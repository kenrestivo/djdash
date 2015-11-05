(ns djdash.memdb
  (:require [clojure.core.async :as async]
            [clojure.edn :as edn]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log])
  (:import (java.io File)))



(defn save-data!
  "Takes settings map and db-internal map"
  [{{:keys [filename]} :settings
    db-agent :db-agent}]
  (binding [*print-length* 10000000 *print-level* 10000000]
    (let [tmpfile (str filename ".tmp")]
      (send-off db-agent
                (fn [data]
                  (log/debug "saving db " filename)
                  (spit tmpfile (prn-str data))
                  (.renameTo (File. tmpfile) (File. filename)))))))




(defn start-command-loop
  [this]
  (let [cmd-ch (async/chan (async/sliding-buffer 1000))]
    (future (try
              (loop []
                (let [{:keys [cmd data]} (async/<!! cmd-ch)]
                  (case  cmd
                    :save (do (save-data! this)
                              (recur)))))
              (catch Exception e
                (log/error e)))
            (log/debug "exiting command loop"))
    (assoc this  :cmd-ch cmd-ch)))



(defn start-autosave-loop
  [this]
  (let [quit-ch (async/chan (async/sliding-buffer 1000))
        {:keys [autosave-timeout]} (:settings this)]
    (future (try
              (loop []
                (let [[{:keys [cmd data]} ch] (async/alts!! [quit-ch (async/timeout autosave-timeout)])]
                  (when (not= quit-ch ch)
                    (log/debug "autosaving database...")
                    (save-data! this)
                    (recur))))
              (catch Exception e
                (log/error e)))
            (log/debug "exiting autosave loop"))
    (assoc this  :autosave-quit-ch quit-ch)))



(defn read-data*
  [path]
  ;; TODO: try/catch for missing file, create it if missing, and throw if it can't be created/read/written
    (some-> path slurp edn/read-string))


(defn read-data
  [path]
  (let [d (read-data* path)]
    (if (map? d)
      d
      (do (log/warn d "saved data is corrupt? not a map")
          {}))))


(defn load-agent
  [this filename]
  (log/info "Loading db first." filename)
  (let [ag (agent (read-data filename)
                  :error-mode :continue
                  :error-handler #(log/error %))]
    (set-validator! ag map?)
    (log/info "DB loaded (presumably)")
    (log/trace @ag)
    ;; TODO: try a test save, just to make sure, and error out if not?
    (assoc this :db-agent ag)))


(defn start-memdb
  "Takes this map, returns it with the db agent assoced in"
  [{:keys [settings db-agent cmd-ch] :as this}]
  (let [{:keys [filename]} settings]
    ;; will return nil if agent is empty or nil
    (if (some-> db-agent deref count (< 1))
      (do
        (log/warn "Cowardly refusing to load db, it looks like it's already loaded")
        this)
      (load-agent this filename)
      )))



(defn stop-memdb
  [{:keys [cmd-ch autosave-quit-ch] :as this}]
  (save-data! this)
  (async/>!! autosave-quit-ch {:cmd :quit})
  (async/>!! cmd-ch {:cmd :quit}))





;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord Memdb [settings cmd-ch db-agent]
  component/Lifecycle
  (start
    [this]
    (log/info "starting memdb " (:settings this))
    (if (and cmd-ch db-agent)
      this 
      (try
        (-> this
            start-memdb
            start-command-loop
            start-autosave-loop)
        (catch Exception e
          (log/error e)
          (log/error (.getCause e))
          this))))
  (stop
    [this]
    (log/info "stopping memdb " (:settings this))
    (if-not  (and cmd-ch db-agent)
      this
      (do
        (log/debug "branch hit, stopping" this)
        (-> this
            stop-memdb
            (assoc  :cmd-ch nil)
            (assoc  :autosave-quit-ch nil)
            (assoc  :db-agent nil))))))


(defn create-memdb
  [settings]
  (log/info "memdb " settings)
  ;; TODO: verify all the settings are there and correct
  (component/using
   (map->Memdb {:settings settings})
   [:log]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(comment

  (require '[djdash.core :as sys])


  (do
    (swap! sys/system component/stop-system [:db])
    (swap! sys/system component/start-system [:db])
    )  

  (log/error (.getCause *e))
  
  (log/set-level! :info)

  (save-data! (->> @sys/system :db))

  (async/>!! (->> @sys/system :db :cmd-ch) {:cmd :save})


  (->> @sys/system :db :db-agent deref)


  (->> @sys/system :db)

  (read-data   (->> @sys/system :db :settings :filename))
  
  )
