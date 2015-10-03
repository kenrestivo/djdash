(ns djdash.nowplaying
  (:require  [taoensso.timbre :as log]
             [com.stuartsierra.component :as component]
             [cheshire.generate :as jgen]
             [clojure.core.async :as async]
             [clj-time.coerce :as coerce]
             [me.raynes.conch :as sh]
             [clj-time.core :as ctime]
             [clj-time.format :as fmt]
             [djdash.utils :as utils]
             [cheshire.core :as json]
             [clj-ical.format :as ical]
             [utilza.misc :as umisc]
             [clojure.tools.trace :as trace])
  (:import  java.text.SimpleDateFormat
            java.util.Locale
            java.util.TimeZone))



(defn nowplaying-listen-loop
  [{:keys [chsk-send! recv-pub]} {:keys [nowplaying]}]
  {:pre [(= clojure.lang.Agent (type nowplaying))
         (every? (comp not nil?) [chsk-send! recv-pub])]}
  (let [sente-ch (async/chan)
        quit-ch (async/chan)]
    (future (try
              (async/sub recv-pub  :djdash/now-playing sente-ch)
              (log/debug "Starting sub for nowplaying channel")
              (loop []
                (let [[{:keys [id client-id ?data]} ch] (async/alts!! [quit-ch sente-ch])]
                  (when (= ch sente-ch)
                    (log/debug "sub sending reply to" client-id ?data)
                    (chsk-send! client-id [:djdash/now-playing @nowplaying])
                    (recur))))
              (catch Exception e
                (log/error e)))
            (log/debug "exiting sub for nowplaying")
            (async/unsub recv-pub :djdash/nowplaying sente-ch))
    quit-ch))


(defn fake-jsonp
  [s]
  (str "update_meta(\n" s "\n);"))


(defn watch-nowplaying-fn
  [sente nowplaying-file nowplaying-fake-json-file]
  (fn [k r o n]
    (log/trace k "nowplaying atom watch updated")
    (when (not= o n)
      (do
        (log/debug k "nowplaying changed " o " -> " n)
        (try 
          (->> n
               json/encode
               (spit nowplaying-file))
          (->> n
               json/encode
               fake-jsonp
               (spit nowplaying-fake-json-file))
          (catch Exception e
            (log/error e)))
        (utils/broadcast sente :djdash/now-playing n)))))


(defn update-nowplaying-fn
  [host port]
  (fn [olde]
    (sh/let-programs [nowplaying "resources/scripts/nowplaying.py"]
                     (->
                      (nowplaying host port)
                      (json/decode true)))))




(defn start-checker
  [nowplaying sente check-delay host port]
  (future (while true
            (try
              ;;(log/debug "checking" url)
              (send-off nowplaying (update-nowplaying-fn host port))
              (catch Exception e
                (log/error e)))
            (Thread/sleep check-delay))))



(defn start-nowplaying
  [{:keys [check-delay host port nowplaying-file nowplaying-fake-json-file]} sente]
  ;; TODO: make this an agent not an atom, and send-off it
  (let [nowplaying-agent (agent  {:playing "Checking..."
                                  :listeners 0}
                                 :error-handler #(log/error %))]
    (add-watch nowplaying-agent :djdash/update
               (watch-nowplaying-fn sente nowplaying-file nowplaying-fake-json-file))
    (log/debug "start-nowplayingr called")
    {:check-thread (start-checker nowplaying-agent sente check-delay host port)
     :nowplaying nowplaying-agent}))


(defn stop-nowplaying
  [{{:keys [nowplaying check-thread]} :nowplaying-internal}]
  (log/trace "stopping nowplaying. agent:" nowplaying)
  (try
    (remove-watch nowplaying :djdash/update)
    (some-> check-thread future-cancel)
    (catch Exception e
      (log/error e))))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord Nowplaying [settings nowplaying-internal]
  component/Lifecycle
  (start
    [this]
    (log/info "starting nowplaying " (:settings this))
    (if nowplaying-internal
      this 
      (let [nowplaying-internal (start-nowplaying (:settings this) (-> this :web-server :sente))
            listen-loop (nowplaying-listen-loop (-> this :web-server :sente) nowplaying-internal)]
        (log/debug "start-nowplaying and nowplaying-listen-loop returned")
        (assoc this :nowplaying-internal (merge nowplaying-internal
                                                {:quit-chan listen-loop})))))
  (stop
    [this]
    (log/info "stopping nowplaying " (:settings this))
    (if-not nowplaying-internal
      this
      (do
        (log/debug "branch hit, stopping" this)
        (async/put! (-> this :nowplaying-internal :quit-chan) :quit)
        (stop-nowplaying this)
        (assoc this :nowplaying-internal nil)))))


(defn create-nowplaying
  [settings]
  (log/info "nowplaying " settings)
  (component/using
   (map->Nowplaying {:settings settings})
   [:log :web-server]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(comment

  (require '[djdash.core :as sys])


  (do
    (swap! sys/system component/stop-system [:nowplaying])
    (swap! sys/system component/start-system [:nowplaying])
    )  


  (->> @sys/system :nowplaying :nowplaying-internal :nowplaying deref)
  
  (log/error (.getCause *e))
  
  (log/set-level! :trace)

  )
