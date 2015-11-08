(ns djdash.db
  (:require [clojure.java.jdbc :as jdbc]
            [com.stuartsierra.component :as component]
            [schema.core :as s]
            [hikari-cp.core :as pool]
            [honeysql.core :as sql]
            [djdash.conf :as conf]
            [taoensso.timbre :as log]))



(defn make-spec
  [{:keys [host port db user password] :as settings}]
  {:pre [(s/validate conf/Db settings)]} 
  {:auto-commit        true
   :read-only          false
   :connection-timeout 30000
   :validation-timeout 5000
   :idle-timeout       600000
   :max-lifetime       1800000
   :minimum-idle       10
   :maximum-pool-size  10
   :pool-name          "db-pool"
   :adapter            "postgresql"
   :username           user
   :password           password
   :database-name      db
   :server-name        host
   :port-number        port})




(defn start-db
  "Takes this map, returns it with the db agent assoced in"
  [{:keys [settings] :as this}]
  {:pre [(s/validate conf/Db settings)]}
  (log/info "db starting")
  (-> this
      (assoc-in [:conn :datasource] (-> settings make-spec pool/make-datasource))))



(defn stop-db
  [{:keys [conn] :as this}]
  (-> conn :datasource pool/close-datasource)
  (-> this
      (assoc :conn nil)))




;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord Db [settings conn]
  component/Lifecycle
  (start
    [this]
    (log/info "starting db " (:settings this))
    (if conn
      this 
      (try
        (start-db this)
        (catch Exception e
          (log/error e)
          (log/error (.getCause e))
          this))))
  (stop
    [this]
    (log/info "stopping db " (:settings this))
    (if-not  conn
      this
      (do
        (log/debug "branch hit, stopping" this)
        (stop-db this)))))


(defn create-db
  [settings]
  (log/info "db " settings)
  ;; TODO: verify all the settings are there and correct
  (component/using
   (map->Db {:settings settings})
   [:log]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(comment

  (require '[djdash.core :as sys])


  (do
    (swap! sys/system component/stop-system [:db])
    (swap! sys/system component/start-system [:db])
    )  

  (log/error (.getCause *e))




  (jdbc/with-db-connection [conn (-> @sys/system :db :conn)]
    (doseq [r (jdbc/query conn "SELECT * FROM \"schema_migrations\"" )]
      (log/info r)))


  (jdbc/query (-> @sys/system :db :conn) 
              ["SELECT * FROM \"schema_migrations\""])
  




  (-> @sys/system :db :conn)

  )
