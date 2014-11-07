(ns djdash.tail
  (:require [taoensso.timbre :as log]
            [clojure.core.async :as async]
            [djdash.utils :as utils]
            [clojure.string :as s]
            [com.stuartsierra.component :as component]
            [clojure.java.io :as jio])
  (:import [org.apache.commons.io.input TailerListenerAdapter Tailer]
           java.util.concurrent.ConcurrentLinkedQueue)
  )


(defn start-tailer
  [fpath queue file-check-delay]
  (let [f (jio/file fpath)
        listener-adapter (proxy [TailerListenerAdapter] []
                           (handle [str]
                             ;;(log/debug " q> " str)
                             (when (not (empty? str))
                               (.add queue str))))
        tailer (Tailer. f listener-adapter file-check-delay true)
        t (Thread. tailer)]
    (doto t
      (.setDaemon true)
      .start)))

(defn get-all
  [queue]
  (loop [sq []]
    (let [s (.poll queue)]
      (if (nil? s)
        sq
        (recur (conj sq s))))))


(defn strs->bufs
  [ls]
  (for [l ls]
    (-> l
        (s/split  #" ")
        second
        Long/parseLong)))

(defn stamp
  []
  (-> (java.util.Date.)
      .getTime))

(defn process-bufs
  [ls]
  (let [bs (strs->bufs ls)]
    {:max (apply max bs)
     :date (stamp)
     :avg (int (/ (apply + bs) (count bs)))
     :min (apply min bs)}))


(defn start-chunker
  [queue sente chunk-delay]
  (future (while true
            (try
              (let [all (get-all queue)]
                (when (not (empty? all))
                  (let [p (process-bufs all)]
                    (log/debug " c> " p)
                    (utils/broadcast* sente :djdash/buffer p))))
              (catch Exception e
                (log/error e)))
            (Thread/sleep chunk-delay))))

(defn stop
  [{:keys [tailer-thread queue chunk-thread] :as old}]
  (when (not (nil? chunk-thread)) (.stop tailer-thread))
  (when (not (nil? tailer-thread)) (.stop tailer-thread)))

(defn start
  [{:keys [fpath bufsiz file-check-delay chunk-delay] :as settings} sente]
  (let [queue (ConcurrentLinkedQueue.)]
    {:queue queue
     :chunk-thread (start-chunker queue sente chunk-delay)
     :tailer-thread (start-tailer fpath queue file-check-delay)}))




;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord Tail [settings tailer]
  component/Lifecycle
  (start
    [this]
    (log/info "starting tailer " (:settings this))
    (if tailer
      this 
      (assoc this :tailer (start (:settings this) (-> this :web-server :sente)))))
  (stop
    [this]
    (log/info "stopping tailer " (:settings this))
    (if-not tailer
      this 
      (do
        (stop tailer)
        (dissoc this :tailer)))))




(defn create-tailer
  [settings]
  (log/info "tail " settings)
  (component/using
   (map->Tail {:settings settings})
   [:log :web-server]))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(comment




  )