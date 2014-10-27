(ns djdash.utils
  (:require 
   [cljs-time.format :as time]
   [cljs-time.coerce :as coerce]
   [cljs-time.core :as tcore]
   [clojure.walk :as walk]
   [clojure.string :as s])
  (:import [goog.net Jsonp]
           [goog Uri]))

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

(defn d3-date
  [d]
  (time/unparse (time/formatters :mysql)  (coerce/from-long d)))

(defn mangle-dygraph
  [listeners]
  (apply str
         (concat ["Time,Listeners\n"]
                 (for [[d l] listeners]
                   (str  (d3-date d) "," l "\n")))))



