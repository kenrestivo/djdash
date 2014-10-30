(ns djdash.core
  (:require [djdash.log :as dlog]
            [environ.core :as env]
            [clojure.tools.namespace.repl :as trepl]
            [com.stuartsierra.component :as component]
            [djdash.server :as srv]
            [clojure.tools.trace :as trace])
  (:gen-class))


(defonce system (atom nil))


(defn make-system
  [{:keys [timbre-config web-server] :as options}]
  (component/system-map
   ;; :db ;; datomic-stuff!
   :log (dlog/start-log timbre-config)
   :web-server (srv/server (or (-> web-server :port) 8080))))


(defn init
  ;; TODO: shouldn't this take params??
  [options]
  (reset! system (make-system options)))

(defn start
  []
  (swap! system component/start))


(defn stop
  []
  (swap! system #(if % (component/stop %) nil)))


(defn go
  [options]
  (init options)
  (start))

(defn reset []
  (stop)
  (trepl/refresh :after 'djdash.core/go env/env))


;; TODO: pass params!
(defn -main
  []
  (future (go env/env))
  )




(comment
  (init)
  (start)
  (stop)
  (reset)

  
  (:timbre-config env/env)
  (go env/env)
  
  
  )