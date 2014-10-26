(ns djdash.utils
  (:require 
   [clojure.walk :as walk])
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


(defn reverse-split
  [s]
  (->> s
       (#(str % "<br>")) ;; hack, last message doesn't have a br, due to interpose on server
       (#(.split % "\n"))
       js->clj
       (remove (partial = "<br>"))
       (remove (partial = ""))
       reverse))


(comment
  (def s "foo\nbar\nbaz")

  )