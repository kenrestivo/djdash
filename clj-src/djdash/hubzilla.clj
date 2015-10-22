(ns djdash.hubzilla
  (:require  [taoensso.timbre :as log]
             [com.stuartsierra.component :as component]
             [cheshire.generate :as jgen]
             [net.cgrand.enlive-html :as enlive]
             [clojure.java.io :as io]
             [djdash.stats :as stats]
             [clojure.string :as str]
             [utilza.enlive :as unlive]
             [utilza.file :as ufile]
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
  (:import (java.net Socket)
           (java.io PrintWriter InputStreamReader BufferedReader)
           java.text.SimpleDateFormat
           java.util.Locale
           java.util.TimeZone))


(defn  post-to-hubzilla
  [{:keys [url login pw channel listen]} playing]
  (when (-> playing empty? not)
    (log/trace "sending to hubzilla" playing)
    (let [{:keys [body headers]}
          (client/post url
                       {:basic-auth [login pw]
                        :throw-entire-message? true
                        :as :json
                        :form-params {:title "Now Playing"
                                      :status (format "%s \nListen here: %s"
                                                      playing listen)}})]
      (log/trace "sent to hubzilla" body  " --> " headers))))


;; I hate this function with every fiber of my being. This is an unreadable mess.
(defn start-request-loop
  [{:keys [settings] :as this}]
  {:pre [ (every? (comp not nil?) [settings])]}
  (let [{:keys [timeout-ms max-timeout-ms bump-factor]} settings
        request-ch (async/chan (async/sliding-buffer 1))]
    (future (try
              (log/info "starting request loop")
              (loop [malfidato? false
                     timeout timeout-ms
                     prev-playing nil]
                (let [[playing ch] (async/alts!! [request-ch (async/timeout timeout)])]
                  (when-not (= playing :quit) ;; end here
                    (if (= ch request-ch)
                      ;; request. first we trust it then we don't.
                      (if-not malfidato? 
                        (do ;; first request, no changes ina  while, cool, do it.
                          (post-to-hubzilla settings playing)
                          ;; don't need to keep prev-playing
                          (recur true (min (* bump-factor timeout) max-timeout-ms) nil))
                        (do ;; malfidato! 
                          (log/trace "bumping timeout, there should be no changes for a while" timeout)
                          (recur true (min (* bump-factor timeout) max-timeout-ms)  playing)))
                      ;; it's a timeout
                      (do 
                        (when prev-playing (post-to-hubzilla settings prev-playing))
                        (log/trace "it's a timeout, we've waited, no changes, resetting"
                                   prev-playing timeout)
                        (recur false timeout-ms nil))))))
              (catch Exception e
                (log/error e)))
            (log/info "exiting request loop"))
    (-> this
        (assoc  :request-ch request-ch))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn start-hubzilla
  [{:keys [settings] :as this}]
  {:pre [ (every? (comp not nil?) [settings])]}
  (start-request-loop this))



(defn stop-hubzilla
  [{:keys [request-ch] :as this}]
  ;; TODO save data first
  (log/info "stopping hubzilla")
  (async/>!! request-ch :quit)
  (assoc this :request-ch nil))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord Hubzilla [settings request-ch]
  component/Lifecycle
  (start
    [this]
    (log/info "starting hubzilla " (:settings this))
    (if (and request-ch )
      this 
      (try
        (start-hubzilla this)
        (catch Exception e
          (log/error e)
          (log/error (.getCause e))
          this))))
  (stop
    [this]
    (log/info "stopping hubzilla " (:settings this))
    (if-not  (or request-ch)
      this
      (do
        (log/debug "branch hit, stopping" this)
        (try 
          (stop-hubzilla this)
          (catch Exception e
            (log/error e)
            (log/error (.getCause e))
            this))))))



(defn create-hubzilla
  [settings]
  (log/info "hubzilla " settings)
  ;; TODO: verify all the settings are there and correct
  (component/using
   (map->Hubzilla {:settings settings})
   [:log :db :web-server]))




;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(comment
  
  (do
    (require '[djdash.core :as sys])
    (require '[utilza.repl :as urepl])
    )

  (do
    (swap! sys/system component/stop-system [:hubzilla])
    (swap! sys/system component/start-system [:hubzilla])
    )
  
  (log/set-level! :info)

  (log/merge-config! {:ns-whitelist ["djdash.hubzilla"]})
  
  (->> @sys/system :hubzilla :request-ch)
  
  (log/error (.getCause *e))
  
  (log/set-level! :trace)

  

  (post-to-hubzilla*  (->> @sys/system :nowplaying :hubzilla)
                      (->> @sys/system
                           :nowplaying
                           :nowplaying-internal
                           :nowplaying
                           deref
                           :playing))

  ;;; for testing
  (async/>!!  (->> @sys/system :hubzilla :request-ch) (str (java.util.Date.)))

  
  )
