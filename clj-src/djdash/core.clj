(ns djdash.core
  (:require [clojure.tools.namespace.repl :as trepl]
            [com.stuartsierra.component :as component]
            [djdash.geolocate :as geo]
            [djdash.log :as dlog]
            [djdash.nrepl :as n]
            [djdash.sente :as sente]
            [schema.core :as s]
            [djdash.conf :as conf]
            [djdash.db :as db]
            [djdash.mqtt :as mqtt]
            [djdash.chat :as chat]
            [djdash.hubzilla :as hubzilla]
            [djdash.nowplaying :as nowplaying]
            [djdash.schedule :as schedule]
            [djdash.server :as srv]
            [djdash.tail :as tail])
  (:gen-class))


(defonce system (atom nil))


(defn make-system
  [{:keys [timbre tailer web-server chat mqtt nrepl db geo scheduler hubzilla now-playing]}]
  (component/system-map
   :log (dlog/start-log timbre)
   :tailer (tail/create-tailer tailer)
   :db   (db/create-db db)
   :nrepl (n/create-nrepl nrepl)
   :sente (sente/create-sente)
   :geo   (geo/create-geo geo)
   :nowplaying (nowplaying/create-nowplaying now-playing)
   :scheduler (schedule/create-scheduler scheduler)
   :web-server (srv/start-server web-server)
   ))


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


(defn -main
  [& [conf-file-arg & _]]
  (try
    (let [conf-file (or conf-file-arg "config.edn")
          conf (conf/read-and-validate conf-file)]
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

  (do 
    (stop)
    (future (-main "config.edn"))
    )

  (future (go env/env))
  
  (into {} @system)
  
  


  (reload (assoc-in env/env [:web-server :mode] :release))

  (init (assoc-in env/env [:web-server :mode] :release))
  (start)
  
  (stop)
  
  (keys env/env)
  (:timbre env/env)
  (go env/env)

  (conf/read-and-validate "config.edn")

  )


