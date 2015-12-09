(ns djdash.hubzilla
  (:require [clj-http.client :as client]
            [clojure.core.async :as async]
            [djdash.utils :as utils]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]))


(defn  post-to-hubzilla!
  [{:keys [url login pw channel listen]} playing]
  (when (-> playing empty? not)
    (log/trace "sending to hubzilla" playing)
    (try
      (let [{:keys [body headers]}
            (client/post url
                         {:basic-auth [login pw]
                          :throw-entire-message? true
                          :as :json
                          :retry-handler utils/retry
                          :form-params {:title "Now Playing"
                                        :status (format "%s \nListen here: %s"
                                                        playing listen)}})]
        (log/trace "sent to hubzilla" body  " --> " headers))
      (catch Throwable e
        (log/error e)))))



(defn start-request-loop
  [{:keys [settings] :as this}]
  {:pre [ (every? (comp not nil?) [settings])]}
  (let [{:keys [timeout-ms max-timeout-ms bump-factor]} settings
        request-ch (async/chan (async/sliding-buffer 1))
        bump (fn [timeout] (min (* bump-factor timeout) max-timeout-ms))]
    (future (try
              (log/info "starting request loop")
              (loop [thrash? true ;; always assume first report is garbage (liquidsoap glitch)
                     timeout timeout-ms
                     prev-playing nil]
                (let [[playing ch] (async/alts!! [request-ch (async/timeout timeout)])
                      req? (= ch request-ch)]
                  (cond (= playing :quit)  nil;; end here
                        (and req? (not thrash?)) (do ;; first request always assumes thrash, this'll be 2nd
                                                   (post-to-hubzilla! settings playing)
                                                   ;; trust no further requests after this, until timeout
                                                   ;; and bump the timeout
                                                   (recur true 
                                                          (bump timeout)
                                                          ;; don't need to keep prev-playing
                                                          nil))
                        ;; thrash! this might be someone thrashing! store it, don't send it to hubzilla yet.
                        (and req? thrash?) (do (log/trace "thrash. bumping timeout " timeout)
                                               (recur true 
                                                      (bump timeout)  
                                                      playing))
                        ;; it's a timeout. 
                        (not req?) (do (when prev-playing 
                                         ;; if something was stored, it's safe to send.
                                         (post-to-hubzilla! settings prev-playing))
                                       (log/trace "we've waited, no changes, resetting timeout"
                                                  prev-playing timeout)
                                       ;; resetting everything back to defaults for next round
                                       (recur true timeout-ms nil)))))
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
   [:log :web-server]))




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

  

  (post-to-hubzilla!  (->> @sys/system :nowplaying :hubzilla)
                      (->> @sys/system
                           :nowplaying
                           :nowplaying-internal
                           :nowplaying
                           deref
                           :playing))

  ;;; for testing
  (async/>!!  (->> @sys/system :hubzilla :request-ch) (str (java.util.Date.)))

  
  )
