(ns djdash.utils
  (:require [utilza.java :as ujava]
            [taoensso.timbre :as log]))


;;; XXX quick hack 
;;; TODO:  thread the settings through and use make-retry-fn
(def max-retries 5)
(def retry-wait 5000)


(defn broadcast
  "sends a broadcast to everyone"
  [{:keys [connected-uids chsk-send!]} k data]
  (doseq [u (:any @connected-uids)]
    (chsk-send! u [k data])))

(defn broadcast-sys
  "fishes the webserver->sente out of system, and sends a broadcast to everyone"
  [system k data]
  (broadcast (-> system :web-server :sente) k data))



(defn revision-info
  "Utility for determing the program's revision."
  []
  (let [{:keys [version revision]} (ujava/get-project-properties "djdash" "djdash")]
    (format "Version: %s, Revision %s" version revision)))


(defn make-retry-fn
  "Retries, with backoff. Logs non-fatal errors as wern, fatal as error"
  [retry-wait max-retries]
  (fn retry
    [ex try-count http-context]
    (log/warn ex http-context)
    (Thread/sleep (* try-count retry-wait))
    (if (> try-count max-retries) 
      false
      (log/error ex try-count http-context))))

;;; XXX hack, don't use, use make-retry-fn and thread settings through
(defn retry
  [ex try-count http-context]
  (log/warn ex http-context)
  (Thread/sleep (* try-count retry-wait))
  ;; TODO: might want to try smaller chunks too!
  (if (> try-count max-retries) 
    false
    (log/error ex try-count http-context)))
