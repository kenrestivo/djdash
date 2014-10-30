(ns djdash.log
  (:require [taoensso.timbre :as log]
            [com.stuartsierra.component :as component]
            [clojure.tools.trace :as trace]))



(defrecord Log [config altered]
  component/Lifecycle
  (start
    [this]
    (log/merge-config! config)
    (if altered
      this
      (do 
        ;; TODO: only in dev envs
        (alter-var-root #'clojure.tools.trace/tracer (fn [_]
                                                       (fn [name value]
                                                         (log/debug name value))))
        (assoc this :altered true))))
  (stop [this]
    this))

(defn start-log
  [config]
  (map->Log {:config config}))


(comment

  (log/error (Exception. "foobar"))
  
  )