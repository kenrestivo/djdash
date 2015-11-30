(ns djdash.utils
  (:require 
   [cljs-time.format :as time]
   [cljs-time.coerce :as coerce]
   [cljs-time.core :as tcore]
   [clojure.walk :as walk]
   [clojure.string :as s])
  (:import [goog.net Jsonp]
           [goog Uri]))


;; this is bad

(def ^:export L js/L)

(defn un-json
  [res]
  (-> res
      js->clj
      walk/keywordize-keys))

(defn jsonp-wrap
  [uri f]
  (-> uri
      Uri.
      Jsonp.
      (.send  nil f)))


(defn hack-list-group
  [msgs]
  (str "<ul class='list-group'>"
       (->> msgs
            (map #(str "<li class='list-group-item'>" % "</li>\n"))
            (apply str))
       "</ul>"))

(defn hack-users-list
  [users-string]
  (s/split users-string  #"<br>"))


(defn reverse-split
  [s]
  (->> s
       (#(str % "<br>")) ;; hack, last message doesn't have a br, due to interpose on server
       (#(.split % "\n"))
       js->clj
       (remove (partial = "<br>"))
       (remove (partial = ""))
       reverse))


(defn buffer-tick
  [n axis]
  (str (-> n int (/ 1000)) "k"))


(defn min-chat-stamp
  []
  (-> (js/Date.)
      .getTime
      (/ 1000)
      (- (* 60 60 24 30))
      Math/floor))





(defn format-time
  [d]
  (cljs-time.format/unparse
   (cljs-time.format/formatter "h:mma")
   (goog.date.DateTime.  d)))




(defn short-weekday
  [d]
  (.toLocaleString d js/window.navigator.language #js {"weekday" "short"}))


(defn live?
  [playing-text]
  (->> playing-text
       (re-find  #"^\[LIVE\!\].*?")
       boolean))


(defn format-schedule-item
  [name start_timestamp end_timestamp]
  (str (short-weekday start_timestamp) " "
       (format-time start_timestamp) " - " (format-time end_timestamp) "   " name ))


(defn changed-keys
  "Returns a set of keys in map bm that are not present in map am"
  [am bm]
  (apply disj (-> bm keys set) (keys am)))
