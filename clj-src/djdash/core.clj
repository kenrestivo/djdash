(ns djdash.core
  (:require [djdash.log :as dlog]
            [environ.core :as env]
            [clojure.tools.namespace.repl :as trepl]
            [com.stuartsierra.component :as component]
            [io.aviso.ansi :as ansi]
            [djdash.geolocate :as geo]
            [clojure.edn :as edn]
            [taoensso.timbre :as log]
            [djdash.memdb :as db]
            [djdash.server :as srv]
            [djdash.tail :as tail]
            [djdash.schedule :as schedule]
            [djdash.nowplaying :as nowplaying]
            [clojure.tools.trace :as trace])
  (:gen-class))


(defonce system (atom nil))


(defn make-system
  [{:keys [timbre tailer web-server db geo scheduler hubzilla now-playing]}]
  ;; TODO: hack! just use schema
  {:pre  [(every? identity (map map? [timbre db tailer hubzilla scheduler now-playing geo web-server]))]} 
  (component/system-map
   :log (dlog/start-log timbre)
   :tailer (tail/create-tailer tailer)
   :db   (db/create-memdb db)
   :geo   (geo/create-geo geo)
   :nowplaying (nowplaying/create-nowplaying now-playing hubzilla)
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


(defn -main
  [& [conf-file-arg & _]]
  (try
    (let [conf-file (or conf-file-arg "config.edn")
          conf (->> conf-file
                    slurp
                    edn/read-string)]
      (println "Starting dashboard components" conf-file conf)
      (go conf))
    (catch Exception e
      (println (.getMessage e))
      (println (.getCause e)))))





;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(comment
  (init env/env)
  (start)
  (stop)
  (reset)

  (-main "config.edn")

  (future (go env/env))
  
  (into {} @system)
  
  
  (reload env/env)

  (reload (assoc-in env/env [:web-server :mode] :release))

  (init (assoc-in env/env [:web-server :mode] :release))
  (start)
  
  (keys env/env)
  (:timbre env/env)
  (go env/env)



  )


