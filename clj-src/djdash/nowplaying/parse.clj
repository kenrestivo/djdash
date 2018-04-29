(ns djdash.nowplaying.parse
  (:require [cheshire.core :as json]
            [clojure.core.async :as async]
            [clojure.string :as str]
            [com.stuartsierra.component :as component]
            [djdash.stats :as stats]
            [utilza.log :as ulog]
            [djdash.utils :as utils]
            [taoensso.timbre :as log]
            [utilza.file :as ufile]))

;; these are the keys that this module owns (if i were doing records...)
(def playing-keys [:artist :title :description :url :live])

(defn un-null
  [s]
  (-> s
      (str/replace #"\(null\)" "")))

(defn munge-archives
  [s]
  (-> (ufile/path-sep "/" s)
      last
      (str/replace #"unknown-" "")
      (str/replace #".ogg" "")
      (str/replace #".mp3" "")
      (str/replace #".mp4" "")
      (str/replace #"-\d+kbps" "")))

(defn mangle-from-live
  "OK, what liquidsoap and icy calls an artist, we call a title."
  [{:keys [artist_clean artist description] :as m}]
  (-> m
      (assoc :title artist)
      (dissoc :artist)
      (dissoc :artist_clean)))



(defn filter-keys
  [m]
  (select-keys m playing-keys))

(defn mangle-from-filename
  [filename]
  (let [[artist title] (-> filename
                           munge-archives
                           (str/split  #" - " ))]
    ;; if there's only one, make it a title
    (if (and (not (empty? artist))
             (not (empty? title)))
      {:artist artist
       :title title}
      {:title (or artist title)
       :artist ""})))

(defn mangle
  "We know what we got ain't good. Make it good now. 
   Good luck, and goddess bless"
  [{:keys [filename source live] :as m}]
  (cond (not (empty? live)) (mangle-from-live m)
        (not (empty? filename)) (-> m
                                    (merge (mangle-from-filename filename))) 
        true m))


(defn bad?
  [{:keys [title artist live]}]
  (or (empty? artist)
      (empty? title)))

(defn conditional-mangle
  "Always mangle live shows, and anything with no artist or title"
  [{:keys [live] :as m}]
  (if (or (-> live empty? not)
          (bad? m))  
    (mangle m)
    m))


(defn legacy-playing
  [{:keys [title artist description live url] :as m}]
  (assoc m :playing  (cond->> (cond-> title
                                (not (empty? artist)) (str " - " artist)
                                (not (empty? description)) (str " - " description))
                       live  (str "[LIVE!] "))  ))


(defn assure-playing-keys
  "Takes a map.
  Returns a map with all the playing-keys present and empty, unless overridden by values in map."
  [m]
  (-> playing-keys
      (zipmap (repeat nil))
      (merge m)))

(defn the-mystery
  [{:keys [title] :as m}]
  (cond-> m
    (empty? title) (assoc :title "????")))

(def parse (comp assure-playing-keys the-mystery legacy-playing  filter-keys conditional-mangle))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(comment

  (log/info :wtf)


  ;; generate a report on the frequencies of these things
  (ulog/spewer
   (let [ms (-> "test-data/lines-without-live.json"
                slurp
                (json/decode  true))]
     (->> ms
          utils/val-freqs)))


  
  


  (ulog/spewer
   (let [ms (-> "test-data/lines-without-live.json"
                slurp
                (json/decode  true))]
     (->> ms
          (filter :artist_clean)
          )))


  
  
  
  )
