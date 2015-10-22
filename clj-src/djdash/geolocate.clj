(ns djdash.geolocate
  (:require [clj-http.client :as client]
            [clojure.core.async :as async]
            [com.stuartsierra.component :as component]
            [djdash.stats :as stats]
            [djdash.utils :as utils]
            [taoensso.timbre :as log]
            [utilza.core :as utilza]
            [utilza.memdb.utils :as memutils]
            [utilza.misc :as umisc]))

(def geo-keymap {:cityName :city
                 :countryName :country
                 :longitude :lng
                 :ipAddress :ip
                 :latitude  :lat
                 :regionName :region
                 :countryCode :country})


(def keys-triggering-broadcast [:ip :city :lat :lng])

(def convert-types
  (partial umisc/munge-columns {:cityName umisc/capitalize-words
                                :regionName umisc/capitalize-words
                                :latitude #(Float/parseFloat %)
                                :longitude #(Float/parseFloat %)
                                :countryName umisc/capitalize-words}))


(defn munge-geo
  [m]
  (-> m
      convert-types ;; damn well better do this before changing the key names!
      (utilza/select-and-rename geo-keymap)))




(defn fetch-geo
  [ip url api-key]
  (log/trace "fetching geo from api:" ip)
  (try
    (->> (client/get url {:query-params {:ip ip,
                                         :key api-key
                                         :format "json"}
                          :headers {"Accept" "application/json"}
                          :as :json})
         :body
         munge-geo)
    (catch Exception e
      (log/error e)
      nil)))

(defn merge-and-keyify-geo
  [{:keys [id] :as conn-item-map} geo-data]
  {id (-> conn-item-map
          (merge geo-data)
          (dissoc :user-agent) ;;; XXX hack, just in case it's the problem
          (dissoc :id))})

(defn broadcast-new-geo
  [sente conns]
  (log/trace "sending out geos to the world" conns)
  (try
    (utils/broadcast sente :djdash/new-geos conns)
    (catch Exception e
      (log/error e))))



(defn get-found-deets
  [deets new-conn-ids  db-agent lookup-ch]
  (->> (for [{:keys [id ip] :as new-conn} deets
             :when (contains? new-conn-ids id)
             :let [found-geo (get @db-agent ip)]]
         (if (map? found-geo)
           (merge-and-keyify-geo new-conn found-geo)
           (async/>!! lookup-ch new-conn)))
       (filter map?) ;; again, agents return true, don't want those. yet.
       (apply merge)))



(defn update-geos
  "NOTE: executes inside a send-off or send agent state"
  [prev-conns new-deets db-agent lookup-ch]
  (let [new-conn-ids (apply disj (->> new-deets (map :id) set)  (keys prev-conns))
        dead-conn-ids (apply disj (-> prev-conns keys set) (->> new-deets (map :id)))]
    (-> (apply dissoc prev-conns dead-conn-ids)
        (merge (get-found-deets new-deets new-conn-ids db-agent lookup-ch)))))



(defn watch-geo-fn
  [sente]
  (fn [k r o n]
    (log/trace k "geo atom watch updated")
    (when (apply not= [o n])
      (do
        (log/debug k "geo changed, broadcasting " o " -> " n)
        ;; just send em all, that's the norm anyway
        (broadcast-new-geo sente n)))))


(defn start-request-loop
  "Must start after lookup loop.
   Takes a Geo record. Starts a channel/loop that accepts combined stats from icecast."
  [{:keys [db-agent conn-agent lookup-ch sente] :as this}]
  {:pre [ (every? (comp not nil?) [db-agent lookup-ch sente conn-agent])]}
  (let [request-ch (async/chan (async/sliding-buffer 5000))]
    (future (try
              (loop []
                (let [combined (async/<!! request-ch)
                      deets (stats/listener-details combined)]
                  (log/trace "got request" combined)
                  (when-not (= (some-> combined :cmd) :quit)
                    (log/trace "updating geos" (doall deets))
                    (send-off conn-agent update-geos deets db-agent lookup-ch)
                    (recur))))
              (catch Exception e
                (log/error e)))
            (log/debug "exiting request loop"))
    (-> this
        (assoc  :request-ch request-ch))))




(defn start-lookup-loop
  "Takes a Geo record.
   Starts a channel/loop that waits for requests to look up a geo from the API service.
   Requests are a connection map that have an :ip address. When it gets one,
   looks it up via the API, then when it gets a result, updates the db cache,
   and broadcasts the new connection record with its geo info included, to all cljs clients.
   Throttles requests via :ratelimit-delay-ms in settings.
   If the lookup request has {:cmd :quit}, shuts down the loop.
   Assocs the :lookup-ch into the Geo record."
  [{:keys [db-agent settings sente conn-agent] :as this}]
  {:pre [ (every? (comp not nil?) [db-agent conn-agent settings sente])]}
  (let [{:keys [ratelimit-delay-ms url api-key]} settings
        lookup-ch (async/chan (async/sliding-buffer 5000))]
    (future (try
              (loop []
                (let [{:keys [ip cmd id] :as m} (async/<!! lookup-ch)]
                  (when-not (= cmd :quit)
                    (log/trace "fetching" m)
                    (let [g (fetch-geo ip url api-key)]
                      (log/trace "lookup loop, fetch returnd " g)
                      (send-off db-agent (memutils/update-record ip (fn [_] g)))
                      (send-off conn-agent merge (merge-and-keyify-geo m g)))
                    (Thread/sleep ratelimit-delay-ms)
                    (recur))))
              (catch Exception e
                (log/error e)))
            (log/debug "exiting lookup loop"))
    (-> this
        (assoc  :lookup-ch lookup-ch))))



(defn start-sente-loop
  "Takes a Geo component record.
   Starts a channel/loop that waits for welcome messages from clients,
   and sends them the state of the agent (all connections currently active).
   Assocs a :quit-ch, into the Geo record, when that channel gets a message, shuts dow the loop"
  [{:keys [conn-agent sente] :as this}]
  {:pre [(= clojure.lang.Agent (type conn-agent))
         (every? (comp not nil?) [sente conn-agent])]}
  (let [{:keys [chsk-send! recv-pub]} sente
        sente-ch (async/chan (async/sliding-buffer 1000))
        quit-ch (async/chan)]
    (async/sub recv-pub  :djdash/geo sente-ch)
    (future (try
              (log/debug "Starting sub for geo channel")
              (loop []
                (let [[{:keys [id client-id ?data]} ch] (async/alts!! [quit-ch sente-ch])]
                  (when (= ch sente-ch)
                    (log/debug "sub sending reply to" client-id ?data)
                    (chsk-send! client-id [:djdash/new-geos @conn-agent])
                    (recur))))
              (catch Exception e
                (log/error e)))
            (log/debug "exiting sub for geo")
            ;; TODO: should probably close the channels i created too here
            (async/unsub recv-pub :djdash/geo sente-ch))
    (assoc this :quit-ch quit-ch)))




;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn start-geo
  [{:keys [db-agent settings sente] :as this}]
  {:pre [ (every? (comp not nil?) [db-agent settings])]}
  (let [conn-agent (agent  {}
                           :error-handler #(log/error %))]
    (set-validator! conn-agent map?)
    (add-watch conn-agent :djdash/update (watch-geo-fn sente))
    (-> this
        (assoc :conn-agent conn-agent)
        start-lookup-loop
        start-request-loop
        start-sente-loop)))


(defn stop-geo
  [{:keys [conn-agent request-ch lookup-ch] :as this}]
  ;; TODO save data first
  (log/info "stopping geo")
  (async/>!! request-ch {:cmd :quit})
  (async/>!! lookup-ch {:cmd :quit})
  (-> this
      (assoc  :sente nil)
      (assoc  :request-ch nil)
      (assoc  :lookup-ch nil)
      (assoc  :db-agent nil)))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord Geo [settings conn-agent sente lookup-ch request-ch db-agent]
  component/Lifecycle
  (start
    [this]
    (log/info "starting geo " (:settings this))
    (if (and conn-agent lookup-ch request-ch db-agent)
      this 
      (try
        (-> this
            (assoc :sente (-> this :web-server :sente))
            (assoc :db-agent (-> this :db :db-agent)) ;; need local ref
            start-geo )
        (catch Exception e
          (log/error e)
          (log/error (.getCause e))
          this))))
  (stop
    [this]
    (log/info "stopping geo " (:settings this))
    (if-not  (or conn-agent lookup-ch request-ch db-agent)
      this
      (do
        (log/debug "branch hit, stopping" this)
        (try 
          (stop-geo this)
          (catch Exception e
            (log/error e)
            (log/error (.getCause e))
            this))))))



(defn create-geo
  [settings]
  (log/info "geo " settings)
  ;; TODO: verify all the settings are there and correct
  (component/using
   (map->Geo {:settings settings})
   [:log :db :web-server]))





;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(comment

  (do
    (require '[djdash.core :as sys])
    (require '[utilza.repl :as urepl])
    )


  (do
    (swap! sys/system component/stop-system [:geo])
    (swap! sys/system component/start-system [:geo])
    )
  
  (->> @sys/system :geo :conn-agent deref)

  (->> @sys/system :db :db-agent deref)

  
  (async/>!! (->> @sys/system :db :cmd-ch) {:cmd :save})

  (log/merge-config! {:ns-whitelist ["djdash.geolocate"]})

  ;; TODO: stick a watch function on conn-agent, see who or what is nuking what?
  



  )