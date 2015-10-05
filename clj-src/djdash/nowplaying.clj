(ns djdash.nowplaying
  (:require  [taoensso.timbre :as log]
             [com.stuartsierra.component :as component]
             [cheshire.generate :as jgen]
             [clojure.java.io :as io]
             [clj-http.client :as client]
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


(defn  post-to-hubzilla*
  [{:keys [url login pw channel]} playing]
  (log/trace "sending to hubzilla")
  (let [{:keys [body headers]}
        (client/post url
                     {:basic-auth [login pw]
                      :throw-entire-message? true
                      :as :json
                      :form-params {:title "Now Playing"
                                    :status playing}})]
    (log/trace "sent to hubzilla" body headers)))


(defn post-to-hubzilla
  [h o n]
  (log/trace "checking if now playing changed")
  (when (and (apply not= (map :playing [o n]))
             (not= "Checking..." (:playing o))
             (-> o :playing empty? not))
    (log/trace o n)
    (future (post-to-hubzilla* h (:playing n)))))



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
  [sente nowplaying-file nowplaying-fake-json-file hubzilla]
  (fn [k r o n]
    (log/trace k "nowplaying atom watch updated")
    (when (not= o n)
      (do
        (log/debug k "nowplaying changed " o " -> " n)
        (utils/broadcast sente :djdash/now-playing n)
        (try 
          (->> n
               json/encode
               (spit nowplaying-file))
          (->> n
               json/encode
               fake-jsonp
               (spit nowplaying-fake-json-file))
          (post-to-hubzilla hubzilla o n)
          (catch Exception e
            (log/error e)))))))



(defn update-nowplaying-fn
  [host port]
  (fn [olde]
    (try
      (log/trace "checking" host port)
      (sh/let-programs [nowplaying (.getPath (io/resource "scripts/nowplaying.py"))]
                       (->
                        (nowplaying host port)
                        (json/decode true)))
      (catch Exception e
        (log/error e)
        olde))))


(defn start-checker
  [nowplaying sente check-delay host port]
  (future (while true
            (try
              (send-off nowplaying (update-nowplaying-fn host port))
              (catch Exception e
                (log/error e)))
            (Thread/sleep check-delay))))



(defn start-nowplaying
  [{:keys [check-delay host port nowplaying-file nowplaying-fake-json-file]} sente hubzilla]
  ;; TODO: make this an agent not an atom, and send-off it
  (let [nowplaying-agent (agent  {:playing "Checking..."
                                  :listeners 0}
                                 :error-handler #(log/error %))]
    (add-watch nowplaying-agent :djdash/update
               (watch-nowplaying-fn sente nowplaying-file nowplaying-fake-json-file hubzilla))
    (log/debug "start-nowplaying called")
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

(defrecord Nowplaying [settings hubzilla nowplaying-internal]
  component/Lifecycle
  (start
    [this]
    (log/info "starting nowplaying " (:settings this))
    (if nowplaying-internal
      this 
      (let [nowplaying-internal (start-nowplaying (:settings this) (-> this :web-server :sente) hubzilla)
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
  [settings hubzilla]
  (log/info "nowplaying " settings hubzilla)
  (component/using
   (map->Nowplaying {:settings settings
                     :hubzilla hubzilla})
   [:log :web-server]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(comment

  (require '[djdash.core :as sys])


  (do
    (swap! sys/system component/stop-system [:nowplaying])
    (swap! sys/system component/start-system [:nowplaying])
    )  

  (log/set-level! :trace)

  (->> @sys/system :nowplaying :nowplaying-internal :nowplaying deref :playing)
  
  (log/error (.getCause *e))
  
  (log/set-level! :trace)

  (require '[clojure.java.io])
  (require '[utilza.repl :as urepl])
  
  (.getPath (clojure.java.io/resource "scripts/nowplaying.py"))

  (require '[utilza.repl :as urepl])

  (post-to-hubzilla*  (->> @sys/system :nowplaying :hubzilla)
                      (->> @sys/system
                           :nowplaying
                           :nowplaying-internal
                           :nowplaying
                           deref
                           :playing))





  )
