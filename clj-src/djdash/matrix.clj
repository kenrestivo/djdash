(ns djdash.matrix
  (:import [java.net URLEncoder])
  (:require [clj-http.client :as client]
            [clojure.core.async :as async]
            [utilza.repl :as urepl]
            [djdash.utils :as utils]
            [utilza.log :as ulog]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]))


(defn send-message-url
  [{:keys [room-id event-type tx-id]}]
  (apply format "/_matrix/client/r0/rooms/%s/send/%s/%s" 
         (map #(URLEncoder/encode %) [room-id event-type (str tx-id)])))


(defn send-message!
  [{:keys [url room-id tx-id message token]}]
  (-> (str url (send-message-url {:room-id room-id
                                  :event-type "m.room.message"
                                  :tx-id (swap! tx-id inc)})) ;; closest to the wire
      (client/put {:as :json
                   :form-params {:msgtype "m.text"
                                 :body message}
                   :query-params {:access_token @token}
                   :content-type :json
                   :insecure? true ;; only because jave don't like letsencrypt, it seems
                   :retry-handler utils/retry
                   :throw-exceptions true})))



(defn  post-to-matrix!
  [{:keys [settings token tx-id]} playing]
  (when (-> playing empty? not)
    (log/trace "sending to matrix" playing)
    (ulog/catcher
     (let [{:keys [url channel listen]} settings
           {:keys [body headers]} (-> {:room-id channel
                                       :url url
                                       :token token
                                       :tx-id tx-id
                                       :message  playing}
                                      send-message!)]
       (log/trace "sent to matrix" body  " --> " headers)))))



(defn start-request-loop
  [{:keys [settings token tx-id] :as this}]
  {:pre [ (every? (comp not nil?) [settings])]}
  (let [{:keys [timeout-ms max-timeout-ms bump-factor]} settings
        request-ch (async/chan (async/sliding-buffer 1))
        bump (fn [timeout] (min (* bump-factor timeout) max-timeout-ms))]
    (future (ulog/catcher
             (log/info "starting request loop")
             (loop [thrash? true ;; always assume first report is garbage (liquidsoap glitch)
                    timeout timeout-ms
                    prev-playing nil]
               (let [[playing ch] (async/alts!! [request-ch (async/timeout timeout)])
                     req? (= ch request-ch)]
                 (cond (= playing :quit)  nil;; end here
                       (and req? (not thrash?)) (do ;; first request always assumes thrash, this'll be 2nd
                                                  (post-to-matrix! this playing)
                                                  ;; trust no further requests after this, until timeout
                                                  ;; and bump the timeout
                                                  (recur true 
                                                         (bump timeout)
                                                         ;; don't need to keep prev-playing
                                                         nil))
                       ;; thrash! this might be someone thrashing! store it, don't send it to matrix yet.
                       (and req? thrash?) (do (log/trace "thrash. bumping timeout " timeout)
                                              (recur true 
                                                     (bump timeout)  
                                                     playing))
                       ;; it's a timeout. 
                       (not req?) (do (when prev-playing 
                                        ;; if something was stored, it's safe to send.
                                        (post-to-matrix! this prev-playing))
                                      (log/trace "we've waited, no changes, resetting timeout"
                                                 prev-playing timeout)
                                      ;; resetting everything back to defaults for next round
                                      (recur true timeout-ms nil))))))
            (log/info "exiting request loop"))
    (-> this
        (assoc  :request-ch request-ch))))


(defn login
  [{:keys [settings] :as this}]
  ;; TODO
  (let [{:keys [url login pw]} settings
        tx-id (atom 1)
        token (atom (some->> (client/post (str url "/_matrix/client/r0/login")
                                          {:as :json
                                           :form-params {:type "m.login.password"
                                                         :password pw,
                                                         :user login
                                                         }
                                           :content-type :json
                                           :insecure? true
                                           :throw-exceptions true})
                             :body
                             :access_token))]
    (-> this
        (assoc :token token)
        (assoc :tx-id tx-id))))


(defn logout!
  [{:keys [settings token tx-id] :as this}]
  (let [{:keys [url]} settings]
    (log/info "logging out of" url @tx-id)
    (ulog/catcher
     (-> (str url "/_matrix/client/r0/logout")
         (client/post {:as :json
                       :form-params {}
                       :query-params {:access_token @token}
                       :content-type :json
                       :insecure? true ;; only because jave don't like letsencrypt, it seems
                       :retry-handler utils/retry
                       :throw-exceptions true})
         :body))
    (-> this
        (assoc :token nil)
        (assoc :tx-id nil))))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn start-matrix
  [{:keys [settings] :as this}]
  {:pre [ (every? (comp not nil?) [settings])]}
  (-> this
      login
      start-request-loop))



(defn stop-matrix
  [{:keys [request-ch settings token] :as this}]
  ;; TODO save data first
  (log/info "stopping matrix")
  (async/>!! request-ch :quit)
  (logout! this)
  (-> this
      (assoc :request-ch nil)
      (assoc :token nil)
      (assoc :tx-id nil)))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord Matrix [settings request-ch token tx-id]
  component/Lifecycle
  (start
    [this]
    (log/info "starting matrix " (:settings this))
    (if (and request-ch )
      this 
      (try
        (start-matrix this)
        (catch Exception e
          (log/error e)
          (log/error (.getCause e))
          this))))
  (stop
    [this]
    (log/info "stopping matrix " (:settings this))
    (if-not  (or request-ch)
      this
      (do
        (log/debug "branch hit, stopping" this)
        (try 
          (stop-matrix this)
          (catch Exception e
            (log/error e)
            (log/error (.getCause e))
            this))))))



(defn create-matrix
  [settings]
  (log/info "matrix " settings)
  ;; TODO: verify all the settings are there and correct
  (component/using
   (map->Matrix {:settings settings})
   [:log :web-server]))




;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(comment
  
  (do
    (require '[djdash.core :as sys])
    (require '[utilza.repl :as urepl])
    )

  (do
    (swap! sys/system component/stop-system [:matrix])
    (swap! sys/system component/start-system [:matrix])
    )
  
  (log/set-level! :info)

  (log/merge-config! {:ns-whitelist ["djdash.matrix"]})
  
  (->> @sys/system :matrix :request-ch)
  
  (log/error (.getCause *e))
  
  (log/set-level! :trace)

  



  ;;; for testing
  (async/>!!  (->> @sys/system :matrix :request-ch) (str (java.util.Date.)))

  (async/>!!  (->> @sys/system :matrix :request-ch) "cheese. life.")

  
  (->> @sys/system :matrix ulog/spewer)



  
  (->> @sys/system :matrix logout!)
  

  (->>  (post-to-matrix!  (->> @sys/system :matrix)
                          (some->> @sys/system
                                   :nowplaying
                                   :nowplaying-internal
                                   :nowplaying
                                   deref
                                   :playing))

        ulog/spewer)




  )
