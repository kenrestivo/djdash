(ns djdash.server
  (:require [taoensso.timbre :as log]
            [environ.core :as env]
            [djdash.web :as web]
            [com.stuartsierra.component :as component]
            [org.httpkit.server :as kit]))





(defrecord Server [settings srv]
  component/Lifecycle
  (start
    [this]
    (log/info "starting webserver " (:settings this))
    (if srv
      this
      ;; TODO: there are many more params to httpsrv/run-server fyi. expose some?
      (assoc this :srv (-> this
                           :settings
                           web/make-handler
                           (kit/run-server  {:port (-> this :settings :port)})))))
  (stop
    [this]
    (log/info "stopping webserver " (:settings this))
    (if-not srv
      this
      (do
        (web/reload-templates)
        (srv)
        ;; (srv) shuts it down, be sure to return the component either way!
        (dissoc this :srv)))))




(defn server
  [settings]
  (log/info "server " settings)
  ;; TODO: (components/using [:log db]) , etc
  (component/using
   (map->Server {:settings settings})
   [:log]))


