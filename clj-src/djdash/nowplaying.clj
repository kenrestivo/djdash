(ns djdash.nowplaying
  (:require [cheshire.core :as json]
            [clojure.core.async :as async]
            [clojure.string :as str]
            [com.stuartsierra.component :as component]
            [djdash.stats :as stats]
            [djdash.utils :as utils]
            [taoensso.timbre :as log]
            [utilza.file :as ufile])
  (:import (java.io BufferedReader InputStreamReader PrintWriter)
           (java.net Socket)))


(def keys-triggering-broadcast [:playing :listeners :artist :title :url :live])
(def archives-host "localhost")
(def archives-port 1234)


;;;;;;;





;; TODO: relace this with MQTT. I don't need to run a message server here.
(defn nowplaying-listen-loop
  "This loop/daemon pulls from the sente channel (clients) 
   and pushes the current state of now playing back to the client who requested it"
  [{:keys [chsk-send! recv-pub]} {:keys [nowplaying]}]
  {:pre [(= clojure.lang.Agent (type nowplaying))
         (every? (comp not nil?) [chsk-send! recv-pub])]}
  (let [sente-ch (async/chan (async/sliding-buffer 1000))
        quit-ch (async/chan)]
    (future (try
              (async/sub recv-pub  :djdash/now-playing sente-ch)
              (log/debug "Starting sub for nowplaying channel")
              (loop []
                (let [[{:keys [id client-id ?data]} ch] (async/alts!! [quit-ch sente-ch])]
                  (when (= ch sente-ch)
                    (log/debug "sub sending reply to" client-id ?data)
                    (chsk-send! client-id [:djdash/now-playing
                                           (select-keys @nowplaying keys-triggering-broadcast)])
                    (recur))))
              (catch Exception e
                (log/error e)))
            (log/debug "exiting sub for nowplaying")
            (async/unsub recv-pub :djdash/nowplaying sente-ch))
    quit-ch))


(defn fake-jsonp
  [s]
  (str "update_meta(\n" s "\n);"))


(defn post-to-matrix
  [h o n]
  (log/trace "matrix checking if now playing changed" o n)
  (try 
    (when (and (apply not= (map :playing [o n]))
               (not= "Checking..." (:playing o))
               (-> o :playing empty? not))
      (log/trace "looks like it did change, sending to matrix via async" o n)
      (when h 
        (log/trace "there is an h, so here it goes")
        (async/>!! h (:playing n))))
    (catch Exception e
      (log/error e))))


(defn watch-nowplaying-fn
  [sente fake-json-file fake-jsonp-file matrix]
  (fn [k r o n]
    (log/trace k "nowplaying atom watch called")
    (when (apply not= (map #(select-keys % keys-triggering-broadcast) [o n]))
      (do
        (log/debug k "nowplaying changed, broadcasting " o " -> " n)
        (utils/broadcast sente :djdash/now-playing (select-keys n keys-triggering-broadcast))
        (try 
          (->> n
               json/encode
               (spit fake-json-file))
          (->> n
               json/encode
               fake-jsonp
               (spit fake-jsonp-file))
          (post-to-matrix matrix o n)
          (catch Exception e
            (log/error e)))))))






(defn update-listeners
  "Takes settings and a geocode request channel. Fetches the latest now playing info from the server,
  updates the nowplaying agent, and pushes the update out to the geocode component."
  [olde {:keys [host port adminuser adminpass song-mount] :as settings} request-ch]
  (log/trace "checking listeners" host port adminuser adminpass song-mount)
  (try
    (if-let [combined (stats/get-combined-stats settings)]
      (do ;; send off to geos to deal with
        (async/>!! request-ch combined)
        (-> olde
            (assoc :listeners (stats/total-listener-count combined))))
      olde)
    (catch Exception e
      (log/error e)
      olde)))

;; TODO: ugh, shouldn't this be moments? really, a future? i guess it's ok, but...
(defn start-checker
  [nowplaying-agent sente request-ch {:keys [check-delay] :as settings}]
  (future (while true
            (try
              (doto nowplaying-agent
                (send-off update-listeners settings request-ch))
              (catch Exception e
                (log/error e)))
            (Thread/sleep check-delay))))



(defn start-nowplaying
  [{:keys [check-delay host port fake-json-file fake-jsonp-file] :as settings} sente request-ch matrix]
  (let [nowplaying-agent (agent  {:playing "Checking..."
                                  :listeners 0}
                                 :error-handler #(log/error %))]
    (add-watch nowplaying-agent :djdash/update
               (watch-nowplaying-fn sente fake-json-file fake-jsonp-file matrix))
    (log/debug "start-nowplaying called")
    {:check-thread (start-checker nowplaying-agent sente request-ch settings)
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

(defrecord Nowplaying [settings matrix nowplaying-internal]
  component/Lifecycle
  (start
    [this]
    (log/info "starting nowplaying " (:settings this))
    (if nowplaying-internal
      this 
      (let [nowplaying-internal (start-nowplaying (:settings this)
                                                  (-> this :sente :sente)
                                                  (-> this :geo :request-ch)
                                                  (-> this :matrix :request-ch))
            listen-loop (nowplaying-listen-loop (-> this :sente :sente) nowplaying-internal)]
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
  ;; TODO: verify all the settings are there and correct
  (log/info "nowplaying " settings)
  (component/using
   (map->Nowplaying {:settings settings})
   [:log :geo :sente :matrix]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(comment
  
  (do
    (require '[djdash.core :as sys])
    (require '[utilza.repl :as urepl])
    )

  (do
    (swap! sys/system component/stop-system [:nowplaying])
    (swap! sys/system component/start-system [:nowplaying])
    )
  
  (log/set-level! :info)

  (->> @sys/system :nowplaying :nowplaying-internal :nowplaying deref )
  
  (log/error (.getCause *e))
  
  (log/set-level! :trace)

  (send (->> @sys/system :nowplaying :nowplaying-internal :nowplaying) assoc :playing "[LIVE!] not really")


  (-> @sys/system :nowplaying :nowplaying-internal :nowplaying deref :-combined)

  
  
  (-> @sys/system :nowplaying :nowplaying-internal :nowplaying deref :connections
      (#(urepl/massive-spew "/tmp/foo.edn" %)))


  (apply disj (set [:foo :bar]) [:foo])

  
  (apply dissoc {:foo 1 :bar 2 :baz 3} [:foo :baz])

  (-> @sys/system :nowplaying :matrix)
  
  (log/set-level! :info)

  (log/set-level! :trace)

  ;;; to force it, for debugging porpoises
  (post-to-matrix (-> @sys/system :nowplaying :matrix :request-ch) {:playing "Unknown"}
                  (->> @sys/system :nowplaying :nowplaying-internal :nowplaying deref))
  
  

  )
