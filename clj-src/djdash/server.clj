(ns djdash.server
  (:require [clojure.core.async :as async]
            [com.stuartsierra.component :as component]
            [djdash.web :as web]
            [org.httpkit.server :as kit]
            [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.http-kit :as skit]
            [taoensso.timbre :as log]))



(defn setup-sente
  []
  (let [{:keys [ch-recv send-fn ajax-post-fn 
                ajax-get-or-ws-handshake-fn connected-uids]}
        (sente/make-channel-socket! 
         skit/sente-web-server-adapter 
         ;; just use the clientid as the user id
         {:user-id-fn :client-id})
        recv-pub (async/pub ch-recv :id (fn [_] (async/sliding-buffer 1000)))
        ]
    {:ring-ajax-post                ajax-post-fn
     :ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn
     :ch-chsk                       ch-recv 
     :chsk-send!                    send-fn
     :recv-pub                      recv-pub
     :connected-uids                connected-uids 
     }
    ))


(defrecord Server [settings srv sente]
  component/Lifecycle
  (start
    [this]
    (if srv
      this
      (try
        (log/info "starting webserver " (:settings this))
        ;; TODO: there are many more params to httpsrv/run-server fyi. expose some?
        (let [sente (setup-sente)]
          (log/info "server not running yet, starting it...")
          (-> this
              (assoc :sente sente)
              (assoc  :srv (-> this
                               :settings
                               (web/make-handler sente)
                               (kit/run-server  {:port (-> this :settings :port)})))))
        (catch Exception e
          (log/error e "<- explosion in webserver start")
          (log/error (.getCause e) "<- was cause of explosion" )))))
  (stop
    [this]
    (log/info "stopping webserver " (:settings this))
    (if-not (:srv this)
      this
      (do
        (log/info "server running, stopping it..")
        (web/reload-templates)
        ;; TODO: shut down sente channels
        ((:srv this))
        ;; (srv) shuts it down, be sure to return the component either way!
        (-> this
            (assoc  :srv nil)
            (assoc :sente nil))))))




(defn start-server
  [settings]
  (log/info "server " settings)
  (try
    (component/using
     (map->Server {:settings settings})
     [:log]) 
    (catch Exception e
      (println e))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(comment
  
  (require '[djdash.core :as sys])


  (do
    (swap! sys/system component/stop-system [:web-server])
    (swap! sys/system component/start-system [:web-server])
    )  

  (def s (setup-sente))

  (def h (web/make-handler {:mode :dev,
                            :chat-url "http://lamp/spaz/radio/chatster/doUpdate.php"}
                           s))

  (def srv (kit/run-server h {:port 8080}))
  
  (srv)

  
  )
