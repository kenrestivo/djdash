(ns djdash.log
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [clojure.tools.trace :as trace]
            [taoensso.timbre.appenders.core :as appenders]))



(defrecord Log [config altered]
  component/Lifecycle
  (start
    [this]
    (if altered
      this
      (let [{:keys [spit-filename]} config]
        (println "starting logging")
        (log/merge-config! (merge config
                                  {:output-fn (partial log/default-output-fn {:stacktrace-fonts {}})
                                   :appenders {:println (appenders/println-appender {:enabled? false})
                                               :spit (appenders/spit-appender
                                                      {:fname spit-filename})}}))
        ;; TODO: only in dev envs
        (alter-var-root #'clojure.tools.trace/tracer (fn [_]
                                                       (fn [name value]
                                                         (log/debug name value))))
        (log/info "logging started" config)
        (assoc this :altered true))))
  (stop [this]
    this))

(defn start-log
  [config]
  (map->Log {:config config}))


(comment

  (log/error (Exception. "foobar"))
  
  )