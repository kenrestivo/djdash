(ns djdash.import
  (:require [clojure.java.jdbc :as jdbc]
            [honeysql.core :as sql]
            [honeysql.helpers :as h]
            [djdash.db :as db]
            [clojure.edn :as edn]
            [djdash.core :as sys]
            [utilza.postgres :as pg]
            [taoensso.timbre :as log]))


(defn import-edn-db
  [edn-db-path]
  (jdbc/execute! (-> @sys/system :db :conn) 
                 (-> (h/insert-into :geocode)
                     (h/values  (-> edn-db-path
                                    slurp
                                    edn/read-string
                                    vals))
                     sql/format)))


(comment
  




  (import-edn-db "/mnt/sdcard/tmp/dash.db")


  )
