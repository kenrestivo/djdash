(ns djdash.core
  (:require [djdash.log :as dlog]
            [environ.core :as env]
            [clojure.tools.namespace.repl :as trepl]
            [com.stuartsierra.component :as component]
            [io.aviso.ansi :as ansi]
            [taoensso.timbre :as log]
            [djdash.server :as srv]
            [djdash.tail :as tail]
            [djdash.schedule :as schedule]
            [djdash.nowplaying :as nowplaying]
            [clojure.tools.trace :as trace])
  (:gen-class))


(defonce system (atom nil))


(defn make-system
  [{:keys [timbre tailer web-server scheduler nowplaying] :as options}]
  {:pre  [(every? identity (map map? [timbre tailer web-server]))]} ;; TODO: hack! just use schema
  (component/system-map
   :log (dlog/start-log timbre)
   :tailer (tail/create-tailer tailer)
   :nowplaying (nowplaying/create-nowplaying nowplaying)
   :scheduler (schedule/create-scheduler scheduler)
   :web-server (srv/start-server web-server)))


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
  (println "Starting dashboard components")
  (go env/env))




;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(comment
  (init env/env)
  (start)
  (stop)
  (reset)


  (future (go env/env))
  
  
  (reload env/env)

  (reload (assoc-in env/env [:web-server :mode] :release))

  (init (assoc-in env/env [:web-server :mode] :release))
  (start)
  
  (keys env/env)
  (:timbre env/env)
  (go env/env)



  )


