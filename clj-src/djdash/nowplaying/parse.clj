(ns djdash.nowplaying.parse
  (:require [cheshire.core :as json]
            [clojure.core.async :as async]
            [clojure.string :as str]
            [com.stuartsierra.component :as component]
            [djdash.stats :as stats]
            [utilza.log :as ulog]
            [utilza.core :as utilza]
            [djdash.utils :as utils]
            [taoensso.timbre :as log]
            [utilza.file :as ufile]))

;; these are the keys that this module owns (if i were doing records...)
(def playing-keys [:artist :title :description :url :live :download])

(defn un-null
  "Some streamers put NULL in there, and some have just a space and nothing else. 
   Annoying. Remove."
  [s]
  (-> s
      (str/replace #"\(null\)" "")
      str/trim
      (str/replace #"^\s+$" "")))

(defn munge-archives
  "Archive files have their own special level of annoyingness. Remove garbage"
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
  [{:keys [artist_clean live artist description] :as m}]
  (if live
    (-> m
        (assoc :title artist)
        (dissoc :artist)
        (dissoc :artist_clean))
    m))



(defn filter-keys
  "We just want the playing-related keys"
  ([m keys]
   (select-keys m keys))
  ([m]
   (filter-keys m playing-keys)) )


(defn make-download
  [filename]
  (str/replace-first filename  #"/usr/share/airtime/public/" ""))

(defn artist-title-mangle
  [{:keys [artist title] :as m} ]
  (merge m (if (and (not (empty? artist))
                    (not (empty? title)))
             {:artist artist
              :title title}
             {:title (or artist title)
              :artist nil})) )


(defn add-download
  [{:keys [filename] :as m}]
  (if (not (empty? filename))
    (assoc m :download (make-download filename))
    m))

(defn mangle-from-filename
  "Filenames of archives have to be parsed out into artist and title.
   This is imprecise and often wrong. Optimize for the case of liquidsoap-created archives."
  [{:keys [artist title filename] :as m} ]
  (if (and (not (empty? filename))
           (empty? title)
           (empty? artist)) 
    (let [[artist title] (-> filename
                             munge-archives
                             (str/split  #" - " ))]
      (log/debug "have to guess from filename:" artist title)
      (assoc m
             :artist artist
             :title title))
    m))



(defn bad?
  "If there's no artist or title, we got us a problem."
  [{:keys [title artist live]}]
  (or (empty? artist)
      (empty? title)))



(defn legacy-playing
  "Streaming clients using the old code required a string with the now playing already parsed.
   Appease them here."
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
  "What's going on? Who the hell knows. Document the atrocities"
  [{:keys [title] :as m}]
  (cond-> m
    (empty? title) (assoc :title "????")))


(defn parse
  "The main entry point of the parsing. It's a chain much like ring handlers.
   Moves the map through the chain of parsers, hopefully resulting in something useful."
  [m]
  (->> m
       (utilza/map-vals un-null) 
       mangle-from-live
       artist-title-mangle
       mangle-from-filename
       add-download
       filter-keys
       legacy-playing
       the-mystery
       assure-playing-keys))



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
