(ns djdash.chat.chatlog
  (:require [clojure.core.async :as async]
            [honeysql.helpers :as h]
            [djdash.utils :as utils]
            [clojure.java.jdbc :as jdbc]
            [honeysql.core :as sql]
            [cheshire.core :as json]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]))

(def chat-log-limit 100) ;; TODO: put in conf file and thread it all the way down here 

(defn get-from-db
  [conn]
  (try
    (->> {:select [[:username :user :time_received] :message]
          :from [:messages]
          :order-by [[:time_received :desc]]
          :limit chat-log-limit}
         sql/format
         (jdbc/query conn)
         reverse
         (assoc {:status "OK"} :history))
    (catch Exception e
      (log/error (.getCause e)))))


(defn get-log
  [dbc]
  (try
    (-> dbc
        get-from-db
        json/encode)
    (catch Exception e
      (log/error e))))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(comment

  (do
    (require '[clj-http.client :as client])
    (require '[djdash.core :as sys])
    (require '[utilza.repl :as urepl])
    )  



  (->> (client/get "http://localhost:8080/chatlog"
                   {:as :json})
       :body
       (urepl/massive-spew "/tmp/foo.edn"))


 (->> (client/get "http://localhost:8080/chatlog"
                  {:query-params {:callback "foobar"}})
;;       :body
       (urepl/massive-spew "/tmp/foo.edn"))


  (-> @sys/system :db :conn)





  )
