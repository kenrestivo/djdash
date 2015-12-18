(ns djdash.server
  (:require [clojure.core.async :as async]
            [com.stuartsierra.component :as component]
            [djdash.web :as web]
            [org.httpkit.server :as kit]
            [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.http-kit :as skit]
            [taoensso.timbre :as log]))





(defrecord Server [settings srv sente dbc schedule-agent]
  component/Lifecycle
  (start
    [this]
    (if srv
      this
      (try
        (log/info "starting webserver " (:settings this))
        ;; TODO: there are many more params to httpsrv/run-server fyi. expose some?
        (let [sente (-> this :sente :sente)
              schedule-agent (-> this :scheduler :scheduler-internal :schedule)
              dbc (-> this :db :conn)]
          (log/info "server not running yet, starting it...")
          (assoc this 
            :sente sente
            :dbc dbc
            :schedule-agent schedule-agent
            :srv (-> this
                     :settings
                     (web/make-handler sente dbc schedule-agent)
                     (kit/run-server  {:port (-> this :settings :port)}))))
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
        (assoc this  
          :srv nil
          :sente nil
          :schedule-agent nil
          :dbc nil)))))




(defn start-server
  [settings]
  (log/info "server " settings)
  (try
    (component/using
     (map->Server {:settings settings})
     [:log :db :sente :scheduler]) 
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

  (->> @sys/system :web-server :schedule-agent deref :future)


  (-> @sys/system :web-server  :scheduler :scheduler-internal :schedule)

  )
