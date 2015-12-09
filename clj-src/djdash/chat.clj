(ns djdash.chat
  (:require [clojure.core.async :as async]
            [honeysql.helpers :as h]
            [djdash.utils :as utils]
            [clojure.java.jdbc :as jdbc]
            [honeysql.core :as sql]
            [cheshire.core :as json]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]))

(defn insert-message
  [conn {:keys [user message]}]
  (try
    (jdbc/execute! conn
                   (-> (h/insert-into :messages)
                       (h/values  [{:message message
                                    :username user}])
                       sql/format))
    (catch Exception e
      (log/error e (.getCause *e)))))


(defn start-chat-listen-loop
  [{:keys [dbc settings mqtt-cmd-ch] :as this}]
  (let [{:keys [topic qos]} settings
        chat-ch (async/chan 5000)]
    (async/thread
     (loop []
       (let [msg (async/<!! chat-ch)]
         (try
           (->> msg
                utils/parse-json-payload
                (insert-message dbc))
           (catch Throwable e
             (log/error e)))
         (when-not (= :quit (some-> msg :cmd))
           (recur)))))
    (async/>!! mqtt-cmd-ch {:cmd :subscribe
                            :msg {:topic topic
                                  :async-chan chat-ch
                                  :qos qos}})
    (assoc this :chat-ch chat-ch)))


(defn stop-chat
  [{:keys [mqtt-cmd-ch] :as this}]
  (async/>!! mqtt-cmd-ch {:cmd :quit})
  (assoc this :dbc nil
         :chat-ch nil))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord Chat [settings dbc chat-ch]
  component/Lifecycle
  (start
    [this]
    (if chat-ch
      this
      (-> this
          (assoc :dbc (-> this :db :conn)
                 :mqtt-cmd-ch (-> this :mqtt :cmd-ch))
          (start-chat-listen-loop))))
  (stop
    [this]
    (log/info "stopping chat " (:settings this))
    (if-not chat-ch
      this
      (stop-chat this))))






(defn create-chat
  [settings]
  (log/info "chat " settings)
  (try
    (component/using
     (map->Chat {:settings settings})
     [:log :mqtt :db]) 
    (catch Exception e
      (println e))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(comment


  


  )
