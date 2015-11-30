(ns djdash.nowplaying
  (:require [cheshire.core :as json]
            [clojure.core.async :as async]
            [clojure.string :as str]
            [com.stuartsierra.component :as component]
            [djdash.stats :as stats]
            [djdash.utils :as utils]
            [net.cgrand.enlive-html :as enlive]
            [taoensso.timbre :as log]
            [utilza.file :as ufile])
  (:import (java.io BufferedReader InputStreamReader PrintWriter)
           (java.net Socket)))


(def keys-triggering-broadcast [:playing :listeners])
(def archives-host "localhost")
(def archives-port 1234)


;;;;;;;



(defn munge-archives*
  [s]
  (-> s
      (str/replace #"unknown-" "")
      (str/replace #".ogg" "")
      (str/replace #".mp3" "")
      (str/replace #"-\d+kbps" "")))


;; the archive thing, transliterated from python
(defn munge-archives
  [s]
  (->> s
       str/split-lines
       (filter #(.contains % "[playing]"))
       first
       (#(str/split % #" "))
       rest
       first
       (ufile/path-sep "/")
       last
       munge-archives*))

(defn check-archives
  [archives-host archives-port]
  (let [socket (Socket. archives-host archives-port)
        in (BufferedReader. (InputStreamReader. (.getInputStream socket)))
        out (PrintWriter. (.getOutputStream socket))]
    (doto out
      (.println "archives.next\nquit\n")
      .flush)
    (slurp in)))


(defn get-archives
  [archives-host archives-port]
  (try
    (munge-archives (check-archives archives-host archives-port))
    (catch Exception e
      (log/error e archives-host archives-port))))


;; TODO: deal with the entire returned result being "Unknown", it can happen, it should null out
(defn remove-unknown
  [s]
  (some->  s
           (str/replace #" - Unknown" "")
           (str/replace #" - \(null\)" "")
           (str/replace #" - <NULL>" "")))


(defn get-icecast-playing
  ([url]
     (->> url
          java.net.URL.
          enlive/html-resource
          (#(enlive/select % [:td]))
          (map (comp first :content))
          (partition 2 1)
          (filter #(= "Current Song:" (first %)))
          first
          second
          remove-unknown))
  ([host ^Integer port mount]
     (get-icecast-playing (format "http://%s:%d/status.xsl?mount=/%s" host port mount))))




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


(defn post-to-hubzilla
  [h o n]
  (log/trace "hubzilla checking if now playing changed" o n)
  (try 
    (when (and (apply not= (map :playing [o n]))
               (not= "Checking..." (:playing o))
               (-> o :playing empty? not))
      (log/trace "looks like it did change" o n)
      (when h (async/>!! h (:playing n))))
    (catch Exception e
      (log/error e))))


(defn watch-nowplaying-fn
  [sente fake-json-file fake-jsonp-file hubzilla]
  (fn [k r o n]
    (log/trace k "nowplaying atom watch updated")
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
          (post-to-hubzilla hubzilla o n)
          (catch Exception e
            (log/error e)))))))




(defn update-playing-fn
  [{:keys [host port adminuser adminpass song-mount] :as settings}]
  (fn [olde]
    (log/trace "checking playing" host port adminuser adminpass song-mount)
    (try
      (assoc olde :playing (or (get-icecast-playing host port song-mount)
                               (get-archives archives-host archives-port)
                               "IT'S A MYSTERY! Listen and guess."))
      (catch Exception e
        (log/error e)
        olde))))



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


(defn start-checker
  [nowplaying-agent sente request-ch {:keys [check-delay] :as settings}]
  (future (while true
            (try
              (doto nowplaying-agent
                (send-off (update-playing-fn settings))
                (send-off update-listeners settings request-ch))
              (catch Exception e
                (log/error e)))
            (Thread/sleep check-delay))))



(defn start-nowplaying
  [{:keys [check-delay host port fake-json-file fake-jsonp-file] :as settings} sente request-ch hubzilla]
  (let [nowplaying-agent (agent  {:playing "Checking..."
                                  :listeners 0}
                                 :error-handler #(log/error %))]
    (add-watch nowplaying-agent :djdash/update
               (watch-nowplaying-fn sente fake-json-file fake-jsonp-file hubzilla))
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

(defrecord Nowplaying [settings hubzilla nowplaying-internal]
  component/Lifecycle
  (start
    [this]
    (log/info "starting nowplaying " (:settings this))
    (if nowplaying-internal
      this 
      (let [nowplaying-internal (start-nowplaying (:settings this)
                                                  (-> this :web-server :sente)
                                                  (-> this :geo :request-ch)
                                                  (-> this :hubzilla :request-ch))
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
  ;; TODO: verify all the settings are there and correct
  (log/info "nowplaying " settings)
  (component/using
   (map->Nowplaying {:settings settings})
   [:log :geo :web-server :hubzilla]))


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

  (->> @sys/system :nowplaying :nowplaying-internal :nowplaying deref :playing)
  
  (log/error (.getCause *e))
  
  (log/set-level! :trace)

  (send (->> @sys/system :nowplaying :nowplaying-internal :nowplaying) assoc :playing "[LIVE!] not really")


  (-> @sys/system :nowplaying :nowplaying-internal :nowplaying deref :-combined)

  
  
  (-> @sys/system :nowplaying :nowplaying-internal :nowplaying deref :connections
      (#(urepl/massive-spew "/tmp/foo.edn" %)))


  (apply disj (set [:foo :bar]) [:foo])

  
  (apply dissoc {:foo 1 :bar 2 :baz 3} [:foo :baz])

  
  )
