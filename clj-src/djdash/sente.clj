(ns djdash.sente
  (:require [clojure.core.async :as async]
            [com.stuartsierra.component :as component]
            [djdash.web :as web]
            [org.httpkit.server :as kit]
            [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.http-kit :as skit]
            [taoensso.timbre :as log]))


(defn setup-sente
  []
  (let [{:keys [ch-recv send-fn ajax-post-fn 
                ajax-get-or-ws-handshake-fn connected-uids]}
        (sente/make-channel-socket! 
         skit/http-kit-adapter
         ;; just use the clientid as the user id
         {:user-id-fn :client-id})
        recv-pub (async/pub ch-recv :id (fn [_] (async/sliding-buffer 1000)))
        ]
    {:ring-ajax-post                ajax-post-fn
     :ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn
     :ch-chsk                       ch-recv 
     :chsk-send!                    send-fn
     :recv-pub                      recv-pub
     :connected-uids                connected-uids 
     }
    ))


(defrecord Sente [settings sente]
  component/Lifecycle
  (start
    [this]
    (if sente
      this
      (try
        (log/info "starting sente " (:settings this))
        (assoc this :sente (setup-sente))
        (catch Exception e
          (log/error e "<- explosion in sente start")
          (log/error (.getCause e) "<- was cause of explosion" )))))
  (stop
    [this]
    (log/info "stopping sente " (:settings this))
    (if-not (:sente this)
      this
      (do
        (log/info "sente running, stopping it..")
        ;; TODO, shut it down, close channels?
        (-> this
            (assoc  :sente nil))))))





(defn create-sente
  []
  (log/info "sente " )
  (try
    (component/using
     (map->Sente {:settings nil})
     [:log]) 
    (catch Exception e
      (println e))))

