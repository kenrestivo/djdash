(ns djdash.core
  (:require [djdash.log :as dlog]
            [environ.core :as env]
            [clojure.tools.namespace.repl :as trepl]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [djdash.server :as srv]
            [djdash.tail :as tail]
            [djdash.schedule :as schedule]
            [clojure.tools.trace :as trace])
  (:gen-class))


(defonce system (atom nil))


(defn make-system
  [{:keys [timbre tailer web-server scheduler] :as options}]
  {:pre  [(every? identity (map map? [timbre tailer web-server]))]} ;; TODO: hack! just use schema
  (component/system-map
   :tailer (tail/create-tailer tailer)
   :scheduler (schedule/create-scheduler scheduler)
   :log (dlog/start-log timbre)
   :web-server (srv/server web-server)))


(defn init
  ;; TODO: shouldn't this take params??
  [options]
  (reset! system (make-system options)))

(defn start
  []
  (swap! system component/start))


(defn stop
  []
  (swap! system #(when % (component/stop %) nil)))


(defn go
  [options]
  (init options)
  (start))

(defn reset []
  (stop)
  (trepl/refresh))

(defn reload [e]
  (reset)
  (go e))

;; TODO: pass params!
(defn -main
  []
  (future (go env/env))
  @(promise))




(comment
  (init env/env)
  (start)
  (stop)
  (reset)


  
  (reload env/env)

  (reload (assoc-in env/env [:web-server :mode] :release))

  (init (assoc-in env/env [:web-server :mode] :release))
  (start)
  
  (keys env/env)
  (:timbre env/env)
  (go env/env)
  
  )


