(ns djdash.mqtt
  (:require [clojure.core.async :as async]
            [clojurewerkz.machine-head.client :as mh]
            [robert.bruce :as bruce]
            [schema.core :as s]
            [djdash.utils :as utils]
            [clojurewerkz.machine-head.conversion :as cnv]
            [cheshire.core :as json]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]))



(def Subscription
  {(s/required-key :async-chan) s/Any ;;; XXX hack
   ;; satisifed? ReadPort or WritePort etc.
   (s/required-key :topic) s/Str
   (s/required-key :qos) s/Int})

(def buf-siz 1000)




(defn chanback-fn
  [chan]
  (fn [t i ^bytes payload]
    (log/trace t i payload (try
                             (utils/parse-json-payload payload)
                             (catch Throwable e
                               "Message is not json")))
    (async/>!! chan payload)))



(defn send-msg [{:keys [conn]} chan msg qos]
  (mh/publish conn chan  (json/encode msg) qos))


(s/defn subscribe*
  [conn 
   {:keys [async-chan topic qos] :as sub} :- Subscription]
  (log/debug "subscribing to" sub)
  (mh/subscribe conn {topic qos} (chanback-fn async-chan)))

(s/defn subscribe
  [conn ;; mqttconn
   subscriptions ;; atom
   {:keys [async-chan topic qos] :as sub} :- Subscription]
  (try
    (log/info "subscribing to" sub)
    (s/validate Subscription sub)
    (swap! subscriptions assoc topic sub)
    (subscribe* conn sub)
    (catch Exception e
      (log/error e))))


(s/defn subscribe-all
  [conn ;; mqttconn
   subscriptions ;; atom
   ]
  (doseq [sub (-> subscriptions deref vals)]
    (log/info "resubscribing to" sub)
    (subscribe* conn sub)))




;; TODO: maybe this kicks off and controls the connect loop!
(defn start-cmd-loop
  [{:keys [settings conn cmd-ch] :as this}]
  (let [{:keys [timeout]} settings
        subscriptions (atom {})]
    ;; TODO: validator on atom
    (async/thread
     (loop []
       ;; XXX test first that there's a connection! don't even try if there isn't!
       ;;(when (and conn (mh/connected? conn))
       (let [{:keys [cmd msg]} (async/<!! cmd-ch)]
         (try
           (case cmd
             :subscribe-all (subscribe-all conn subscriptions)
             ;; disconnected, possibly?
             :subscribe (subscribe conn subscriptions msg)
             ;;:unsubscribe (unsubscribe subscriptions msg)
             :quit (log/info "got quit message")
             (log/error "no such command" cmd))
           (catch Exception e
             (log/error e)))
         (if (= :quit cmd)
           (log/info "quitting command loop")
           (recur)))))
    (assoc this 
      :subscriptions subscriptions)))


(defn disconnect
  [{:keys [conn settings] :as this}]
  (try
    (when (and conn (mh/connected? conn)
               (log/info "disconnecting from mqtt" settings)
               (mh/disconnect-and-close conn)))
    (catch Exception e
      (log/error e))))


(defn connect
  [{:keys [settings cmd-ch] :as this}]
  (let [{:keys [port host timeout retry tries keep-alive]} settings
        cmd-ch (async/chan buf-siz)
        id   (mh/generate-id)
        recon (async/chan 10)
        conn (mh/prepare (format "tcp://%s:%d" host port) id)]
    (log/debug "connection prepared" conn)
    ;; TODO: coroutine this from command loop maybe?
    (async/>!! recon {:cmd :start}) ;; kick the thing off before any timeout. hack.
    (log/debug "starting thread")
    (async/thread
     (loop []
       (let [[{:keys [cmd]} c] (async/alts!! [(async/timeout (* 1000 timeout)) recon])]
         (log/trace "checking mqtt")
         (when-not (and conn (mh/connected? conn))
           (log/info "not connected. connecting to mqtt" settings)
           (try
             (.connect conn  (cnv/->connect-options {:connection-timeout timeout
                                                     :keep-alive-interval keep-alive}))
             ;; TODO: could check conn status and set callbak
             (when (mh/connected? conn)
               (log/info "connected to mqtt" settings)
               (async/>!! cmd-ch {:cmd :subscribe-all}))
             (catch Exception e
               (log/error e))))
         (when-not (= :quit cmd)
           (Thread/sleep (* 1000 timeout))
           (recur))))
     (log/info "exiting mqtt connection check thread"))
    (assoc this 
      :conn conn
      :cmd-ch cmd-ch
      :recon recon)))



(defn clj->net-loop
  [{:keys [conn settings chans] :as this}]
  (let [{:keys [timeout]} settings
        clj->net (async/chan buf-siz)]
    (async/thread
     (loop []
       (if (and conn (mh/connected? conn))
         (let [{:keys [chan msg qos] :as m} (async/<!! clj->net)]
           (log/trace "sending" chan msg)
           (mh/publish conn chan msg qos)
           (when-not (= (some-> m :cmd) :quit)
             (recur)))
         (do
           (log/warn "mqtt not connected")
           (Thread/sleep (* 1000 timeout))
           (recur))))
     (log/info "exiting clj->net loop"))
    (assoc this :clj->net clj->net)))


(defn stop-mqtt
  [{:keys [conn cmd-ch clj->net clj<-net recon] :as this}]
  ;;(async/>!! clj->net {:cmd :quit})
  (async/>!! cmd-ch {:cmd :quit})
  (async/>!! recon {:cmd :quit})
  (disconnect this)
  (assoc this
    :conn nil
    :cmd-ch nil
    :recon nil
    :in-ch nil
    :out-ch nil))




(defrecord Mqtt [settings conn recon subscriptions cmd-ch clj->net]
  component/Lifecycle
  (start
    [this]
    (if conn
      this
      (try
        (log/info "starting mqtt " (:settings this))
        (-> this
            connect
            start-cmd-loop
            ;;clj->net-loop
            )
        (catch Exception e
          (log/error e "<- explosion in mqtt start")
          (log/error (.getCause e) "<- was cause of explosion" )
          this))))
  (stop
    [this]
    (log/info "stopping mqtt " (:settings this))
    (if-not (:conn this)
      this
      (do
        (log/info "mqtt running, stopping it..")
        (stop-mqtt this)))))





(defn create-mqtt
  [settings]
  (log/info "mqtt " settings)
  (try
    (component/using
     (map->Mqtt {:settings settings})
     [:log]) 
    (catch Exception e
      (println e))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(comment

  (do
    (require '[djdash.core :as sys])
    (require '[utilza.repl :as urepl])
    )


  (do
    (swap! sys/system component/stop-system [:mqtt])
    (swap! sys/system component/start-system [:mqtt])
    )  

  (log/error (.getCause *e))

  (log/merge-config! {:ns-whitelist ["djdash.mqtt"]})


  (let [{:keys [conn]} (-> @sys/system :mqtt)]
    (mh/subscribe conn {"spazradio" 2} logback))


  (send-msg (-> @sys/system :mqtt) 
            "spazradio"
            {:message (format "hello %s" (.toString (java.util.Date.)))
             :user "fooooobar"}
            2)





  (-> @sys/system :mqtt :conn mh/connected?)

  (-> @sys/system :mqtt :conn .getClientId)


  (def cb (#'clojurewerkz.machine-head.client/reify-mqtt-callback nil nil nil))
  

  (def foo (async/chan))
  

  (isa? clojure.core.async.impl.protocols.ReadPort (class foo))

  clojure.core.async.impl.channels.ManyToManyChannel

  (urepl/hjall foo)

  (def read-ch (async/chan 1000))


  (-> @sys/system :mqtt :subscriptions deref)

  ;; it woiks!!
  (-> read-ch async/poll! utils/parse-json-payload)

  (async/>!! (-> @sys/system :mqtt :cmd-ch) {:cmd :subscribe
                                             :msg {:topic "spazradio"
                                                   :async-chan read-ch
                                                   :qos 2}})
  

  (async/>!! (-> @sys/system :mqtt :cmd-ch) {:cmd :subscribe-all})


  )

