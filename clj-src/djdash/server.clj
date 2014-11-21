(ns djdash.server
  (:require [taoensso.timbre :as log]
            [environ.core :as env]
            [djdash.web :as web]
            [taoensso.sente :as sente]
            [com.stuartsierra.component :as component]
            [org.httpkit.server :as kit]))





(defn setup-sente
  []
  (let [{:keys [ch-recv send-fn ajax-post-fn ajax-get-or-ws-handshake-fn
                connected-uids]}
        (sente/make-channel-socket! {})] ;; supply userid fn here, or just use :uid in ring
    {:ring-ajax-post                ajax-post-fn
     :ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn
     :ch-chsk                       ch-recv ; ChannelSocket's receive channel
     :chsk-send!                    send-fn ; ChannelSocket's send API fn
     :connected-uids                connected-uids ; Watchable, read-only atom
     }
    ))


(defrecord Server [settings srv sente]
  component/Lifecycle
  (start
    [this]
    (log/info "starting webserver " (:settings this))
    (if srv
      this
      ;; TODO: there are many more params to httpsrv/run-server fyi. expose some?
      (let [sente (setup-sente)]
        (-> this
            (assoc :sente sente)
            (assoc  :srv (-> this
                             :settings
                             (web/make-handler sente)
                             (kit/run-server  {:port (-> this :settings :port)})))))))
  (stop
    [this]
    (log/info "stopping webserver " (:settings this))
    (if-not (:srv this)
      this
      (do
        (web/reload-templates)
        ;; TODO: shut down sente channels
        ((:srv this))
        ;; (srv) shuts it down, be sure to return the component either way!
        (-> this
            (assoc  :srv nil)
            (assoc :sente nil))))))




(defn server
  [settings]
  (log/info "server " settings)
  (component/using
   (map->Server {:settings settings})
   [:log]))


