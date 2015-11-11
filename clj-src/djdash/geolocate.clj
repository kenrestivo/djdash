(ns djdash.geolocate
  (:require [clj-http.client :as client]
            [clojure.core.async :as async]
            [com.stuartsierra.component :as component]
            [honeysql.helpers :as h]
            [djdash.stats :as stats]
            [honeysql.core :as sql]
            [clojure.java.jdbc :as jdbc]
            [honeysql.core :as sql]
            [djdash.utils :as utils]
            [taoensso.timbre :as log]
            [utilza.core :as utilza]
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

(defn get-geo
  [conn ip]
  (try
    (->> {:select [:*]
          :from [:geocode]
          :where [:= :ip ip]}
         sql/format
         (jdbc/query conn)
         first)
    (catch Exception e
      (log/error e))))



(defn insert-geo
  [conn geo]
  (try
    (jdbc/execute! conn
                   (-> (h/insert-into :geocode)
                       (h/values  [geo])
                       sql/format))
    (catch Exception e
      (log/error e (.getCause *e)))))


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
  [deets new-conn-ids  dbc lookup-ch]
  (->> (for [{:keys [id ip] :as new-conn} deets
             :when (contains? new-conn-ids id)
             :let [found-geo (get-geo dbc ip)]] 
         (if (map? found-geo)
           (merge-and-keyify-geo new-conn found-geo)
           (async/>!! lookup-ch new-conn)))
       (filter map?) ;; again, agents return true, don't want those. yet.
       (apply merge)))



(defn update-geos
  "NOTE: executes inside a send-off or send agent state"
  [prev-conns new-deets dbc lookup-ch]
  (let [new-conn-ids (apply disj (->> new-deets (map :id) set)  (keys prev-conns))
        dead-conn-ids (apply disj (-> prev-conns keys set) (->> new-deets (map :id)))]
    (-> (apply dissoc prev-conns dead-conn-ids)
        (merge (get-found-deets new-deets new-conn-ids dbc lookup-ch)))))



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
  [{:keys [dbc conn-agent lookup-ch sente] :as this}]
  {:pre [ (every? (comp not nil?) [dbc lookup-ch sente conn-agent])]}
  (let [request-ch (async/chan (async/sliding-buffer 5000))]
    (future (try
              (log/info "starting request loop")
              (loop []
                (let [combined (async/<!! request-ch)
                      deets (stats/listener-details combined)]
                  (log/trace "got request" combined)
                  (when-not (= (some-> combined :cmd) :quit)
                    (log/trace "updating geos" (doall deets))
                    (send-off conn-agent update-geos deets dbc lookup-ch)
                    (recur))))
              (catch Exception e
                (log/error e)))
            (log/debug "exiting request loop"))
    (log/info "request loop started")
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
  [{:keys [dbc settings sente conn-agent] :as this}]
  {:pre [ (every? (comp not nil?) [dbc conn-agent settings sente])]}
  (let [{:keys [ratelimit-delay-ms url api-key]} settings
        lookup-ch (async/chan (async/sliding-buffer 5000))]
    (future (try
              (log/info "starting lookup loop")
              (loop []
                (let [{:keys [ip cmd id] :as m} (async/<!! lookup-ch)]
                  (when-not (= cmd :quit)
                    (log/trace "fetching" m)
                    (when-let [g (fetch-geo ip url api-key)]
                      (log/trace "lookup loop, fetch returnd " g)
                      (insert-geo dbc g)
                      (send-off conn-agent merge (merge-and-keyify-geo m g)))
                    (Thread/sleep ratelimit-delay-ms)
                    (recur))))
              (catch Exception e
                (log/error e)))
            (log/debug "exiting lookup loop"))
    (log/info "lookup loop started")
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
              (log/info "Starting sente sub for geo channel")
              (loop []
                (let [[{:keys [id client-id ?data]} ch] (async/alts!! [quit-ch sente-ch])]
                  (when (= ch sente-ch)
                    (log/debug "sub sending reply to" client-id ?data)
                    (chsk-send! client-id [:djdash/new-geos @conn-agent])
                    (recur))))
              (catch Exception e
                (log/error e)))
            (log/info "exiting sub for geo")
            ;; TODO: should probably close the channels i created too here
            (async/unsub recv-pub :djdash/geo sente-ch))
    (log/info "sente loop started")
    (assoc this :quit-ch quit-ch)))




;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn start-geo
  [{:keys [dbc settings sente] :as this}]
  {:pre [ (every? (comp not nil?) [dbc settings sente])]}
  (let [conn-agent (agent  {}
                           :error-handler #(log/error %))]
    (set-validator! conn-agent map?)
    (add-watch conn-agent :djdash/update (watch-geo-fn sente))
    (log/info "start-geo")
    (-> this
        (assoc :conn-agent conn-agent)
        start-lookup-loop
        start-request-loop
        start-sente-loop)))


(defn stop-geo
  [{:keys [conn-agent request-ch lookup-ch] :as this}]
  (log/info "stopping geo")
  (async/>!! request-ch {:cmd :quit})
  (async/>!! lookup-ch {:cmd :quit})
  (-> this
      (assoc  :sente nil)
      (assoc  :request-ch nil)
      (assoc  :lookup-ch nil)
      (assoc  :dbc nil)))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord Geo [settings conn-agent sente lookup-ch request-ch dbc]
  component/Lifecycle
  (start
    [this]
    (log/info "starting geo " (:settings this))
    (if (and conn-agent lookup-ch request-ch dbc)
      this 
      (try
        (-> this
            (assoc :sente (-> this :web-server :sente))
            (assoc :dbc (-> this :db :conn)) ;; need local ref
            start-geo )
        (catch Exception e
          (log/error e)
          (log/error (.getCause e))
          this))))
  (stop
    [this]
    (log/info "stopping geo " (:settings this))
    (if-not  (or conn-agent lookup-ch request-ch dbc)
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


  (require '[djdash.core :as sys])

  (do
    (require '[djdash.core :as sys])
    (require '[utilza.repl :as urepl])
    )


  (do
    (swap! sys/system component/stop-system [:geo])
    (swap! sys/system component/start-system [:geo])
    )
  
  (->> @sys/system :geo  :conn-agent deref)

  (->> @sys/system :db :conn)

  (->> @sys/system :geo  vals (map type))

  
  (async/>!! (->> @sys/system :db :cmd-ch) {:cmd :save})

  (log/merge-config! {:ns-whitelist ["djdash.geolocate"]})

  ;; TODO: stick a watch function on conn-agent, see who or what is nuking what?
  



  )
