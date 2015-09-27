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
  (let [bs (strs->bufs ls)
        c (count bs)
        maxn (if (< 0 c) (apply max bs) 0)]
    {:max maxn
     :date (stamp)
     :avg (if (< 0 c)
            (int (/ (apply + bs) c))
            0)
     :min (if (< 0 c) (apply min bs) 0)}))

(defn push
  [sente all]
  (try
    (let [p (process-bufs all)]
      (log/debug " c> " p)
      (utils/broadcast* sente :djdash/buffer p))
    (catch Exception e
      (log/error e))))


(defn start-chunker
  [queue sente chunk-delay]
  (future (loop [prev nil]
            (let [all (try
                        (get-all queue)
                        (catch Exception e
                          (log/error e)))]
              (when (and (empty? prev) (not (empty? all)))
                ;; force rising edge
                (push sente prev))
              (when (not (empty? all))
                ;; the actual data, if present
                (push sente all))
              (when (and (empty? all) (not (empty? prev)))
                ;; force falling edge
                (push sente all))
              (Thread/sleep chunk-delay)
              (recur all)))))


(defn stop
  [{:keys [tailer-thread queue chunk-thread] :as old}]
  (when (not (nil? chunk-thread)) (future-cancel tailer-thread))
  (some-> tailer-thread .stop))

(defn start
  [{:keys [fpath bufsiz file-check-delay chunk-delay] :as settings} sente]
  ;; TODO: could core.async channel instead
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
        (assoc this :tailer nil)))))




(defn create-tailer
  [settings]
  (log/info "tail " settings)
  (component/using
   (map->Tail {:settings settings})
   [:log :web-server]))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(comment




  )