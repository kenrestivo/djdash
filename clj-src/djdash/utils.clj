(ns djdash.utils
  (:require [utilza.java :as ujava]
            [cheshire.core :as json]
            [taoensso.timbre :as log]))


;;; XXX quick hack 
;;; TODO:  thread the settings through and use make-retry-fn
(def max-retries 5)
(def retry-wait 5000)


(defn broadcast
  "sends a broadcast to everyone"
  [{:keys [connected-uids chsk-send!]} k data]
  (doseq [u (some-> connected-uids deref :any)]
    (chsk-send! u [k data])))


(defn revision-info
  "Utility for determing the program's revision."
  []
  (let [{:keys [version revision]} (ujava/get-project-properties "djdash" "djdash")]
    (format "Version: %s, Revision %s" version revision)))


(defn make-retry-fn
  "Retries, with backoff. Logs non-fatal errors as wern, fatal as error"
  ([retry-wait max-retries expand-backoff?]
     (fn retry
       [ex try-count http-context]
       (log/warn ex http-context)
       (Thread/sleep (if expand-backoff? (* try-count retry-wait) retry-wait))
       (if (> try-count max-retries) 
         false
         (log/error ex try-count http-context))))
  ([retry-wait max-retries]
     (make-retry-fn retry-wait make-retry-fn true)))

;;; XXX hack, don't use, use make-retry-fn and thread settings through
(defn retry
  [ex try-count http-context]
  (log/warn ex http-context)
  (Thread/sleep (* try-count retry-wait))
  ;; TODO: might want to try smaller chunks too!
  (if (> try-count max-retries) 
    false
    (log/error ex try-count http-context)))


(defmacro catcher 
  [body]
  `(try
     ~@body
     (catch Exception e#
       (log/error e#))))

(defn parse-json-payload
  [^bytes payload]
  (try
    (-> payload  
        (String.  "UTF-8") 
        (json/decode true))
    (catch Exception e
      (log/error e))))

(defn logback
  [_ _ ^bytes payload]
  (-> payload parse-json-payload log/info))


(defn map-vals
  "Takes a map and applies f to all vals of it"
  [f m]
  (into {} (for [[k v] m] [k (f v)])))



(defn dissoc-vector
  "Given vector v and a seq of positions to dissoc from it,
     returns a vector with those positions removed"
  [v ks]
  (->>  v
        (map-indexed vector)
        (into (sorted-map))
        (#(apply dissoc % ks))
        vals
        vec))


(defn wrap
  [handler k thing]
  (fn [req]
    (handler (assoc req k thing))))


;; TODO: move to utilza
(defn val-freqs
  "Debug function to show the frequencies of all values for all keys"
  [ms]
  (into {}
        (for [k  (->> ms
                      (map keys)
                      (apply concat)
                      set)]
          [k (->> ms
                  (map k)
                  frequencies)])))
