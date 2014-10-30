(ns djdash.server
  (:require [taoensso.timbre :as log]
            [environ.core :as env]
            [djdash.web :as web]
            [com.stuartsierra.component :as component]
            [org.httpkit.server :as kit]))





(defrecord Server [port db srv]
  component/Lifecycle
  (start
    [this]
    (log/info "starting webserver on port " port)
    (if srv
      this
      ;; TODO: there are many more params to httpsrv/run-server fyi. expose some?
      (assoc this :srv (-> db
                           web/make-handler
                           (kit/run-server  {:port port})))))
  (stop
    [this]
    (log/info "stopping webserver on port " port)
    (if-not srv
      this
      ;; (srv) shuts it down, be sure to return the component either way!
      (assoc this :srv (srv)))))




(defn server
  [port]
  ;; TODO: (components/using [:log db]) , etc
  (component/using
   (map->Server {:port port})
   [:log]))


