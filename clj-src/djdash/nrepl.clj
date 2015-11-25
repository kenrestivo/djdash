(ns djdash.nrepl
  (:require [clojure.core.async :as async]
            [clojure.tools.nrepl.server :as nrepl]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]))




(defrecord Nrepl [settings srv]
  component/Lifecycle
  (start
    [this]
    (if srv
      this
      (try
        (log/info "starting nrepl " (:settings this))
        (if-let [{:keys [port]} settings]
          (-> this
              (assoc  :srv  (apply nrepl/start-server (apply concat settings))))
          this)
        (catch Exception e
          (log/error e "<- explosion in nrepl start")
          (log/error (.getCause e) "<- was cause of explosion" )))))
  (stop
    [this]
    (log/info "stopping nrepl " (:settings this))
    (if-not (:srv this)
      this
      (do
        (log/info "nrepl running, stopping it..")
        (some-> this :srv nrepl/stop-server)
        (-> this
            (assoc  :srv nil))))))





(defn create-nrepl
  [settings]
  (log/info "nrepl " settings)
  (try
    (component/using
     (map->Nrepl {:settings settings})
     [:log]) 
    (catch Exception e
      (println e))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(comment


  


  )
