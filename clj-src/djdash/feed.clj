(ns djdash.feed
  (:require [taoensso.timbre :as log]
            [environ.core :as env]
            [clojure.data.xml :as xml]
            [utilza.misc :as umisc]
            [clj-time.core :as time]
            [clojure.string :as st]
            [utilza.file :as file]
            [clojure.java.io :as jio]
            [clj-time.format :as time-fmt]
            [clj-time.coerce :as ctime]
            [clojure.string :as s]
            [cheshire.core :as json]
            [clojure.java.io :as io])
  (:import [org.joda.DateTime]))


(defn my-zone
  "show a time in a readable format, in my damn time zone
    from utilza"
  [t]
  (time-fmt/unparse
   (time-fmt/formatters :rfc822)
   t))

;; TODO: move to config
(def base-url "http://foo")

;;; XXX BROKEN MUST LOCAL ZONE AND THEN UTC
(defn ymd-to-date [ymd]
  (->> ymd
       (map #(Integer/parseInt %))
       (apply time/local-date-time)))


(defn unogg
  "Strip the .suffix from a string.
   WARNING: Can not handle . in the name!"
  [s]
  (-> s
      (st/split  #"\.ogg")
      first))

(defn process-dir
  [dirpath]
  (for [f (file/file-names dirpath  #".*?\.ogg")
        :let [basename  (->> f (file/path-sep "/") second)
              [y m d _ & ss] (st/split basename #"-")]]
    {:date (ymd-to-date [y m d])
     :show (unogg (last ss))
     :file basename}))

(defn get-files
  [dirname]
  (->>   dirname
         process-dir
         (sort-by :date)
         reverse))

;; TODO: move to utlilza
(defn unogg2
  "Strip the .suffix from a string.
   WARNING: Can not handle . in the name!"
  [s]
  (if s
    (st/join (butlast (st/split s  #"\.")))
    ""))

(defn make-item
  "logentry comes in from the view as :key date, :id guid, :value message.
   Change these to XML item elements for RSS feed."
  [{:keys [file show date]}]
  (let [link (str base-url "/" file)
        fdate (-> date ctime/to-date-time my-zone)
        title (str fdate  " - " show)]
    (xml/element :item {}
                 (xml/element :title {}
                              title)
                 (xml/element :description {}
                              title)
                 (xml/element :content:encoded {}
                              )
                 (xml/element :enclosure:url {:type "audio/ogg"}
                              link)
                 (xml/element :itunes:duration {}
                              ;; TODO: XXX no, this must be th real duration. pull it out
                              "04:00:00" )
                 (xml/element :itunes:subtitle {}
                              )
                 (xml/element :itunes:summary {}
                              )
                 (xml/element :itunes:keywords {}
                              "spaz, music" )
                 (xml/element :itunes:author {}
                              "spaz")
                 (xml/element :itunes:explicit {}
                              "no")
                 (xml/element :itunes:block {}
                              "no"             )
                 (xml/element :pubDate {}
                              fdate)
                 (xml/element :link {}  link)
                 (xml/element :guid {:isPermaLink "false"}
                              link))))





(defn xml-feedify
  [lastdate items]
  (xml/emit-str
   (xml/element
    :rss
    {:version "2.0"
     :xmlns:atom "http://www.w3.org/2005/Atom"
     :xmlns:content "http://purl.org/rss/1.0/modules/content/"
     :xmlns:wfw "http://wellformedweb.org/CommentAPI/"
     :xmlns:dc "http://purl.org/dc/elements/1.1/"
     :xmlns:sy "http://purl.org/rss/1.0/modules/syndication/"
     :xmlns:slash "http://purl.org/rss/1.0/modules/slash/"
     :xmlns:itunes "http://www.itunes.com/dtds/podcast-1.0.dtd"
     :xmlns:media "http://search.yahoo.com/mrss/"
     }
    (apply xml/element :channel {}
           (xml/element :title {} "SPAZ Radio Archives")
           (xml/element :link {} base-url)
           (xml/element :sy:updatePeriod  {}
                        "daily")
           (xml/element :sy:updateFrequency {}
                        1)
           (xml/element :copyright {}
                        "sharenjoy")
           (xml/element :webMaster {}
                        "ken@spaz.org")
           (xml/element :language {}
                        "en")
           (xml/element :generator {}
                        "http://spaz.org")
           (xml/element :ttl {}
                        1440)
           (xml/element :description {} "SPAZ Radio Archives")
           (xml/element :lastBuildDate {} lastdate)
           (xml/element :atom:link
                        {:href base-url
                         :rel "self"
                         :type "application/rss+xml"})
           items))))


(defn make-feed
  [showname dirname]
  (let [all-files (get-files dirname)
        files (if showname (filter #(= (:show %) showname) all-files) all-files)
        items (map make-item files)
        ld (->> files first :date ctime/to-date-time my-zone)]
    (xml-feedify ld items)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(comment

;; TODO: get the files, get list of shows in it, get all the feeds for those shows
  
  (->> "/mnt/sdcard/to-other/manually-fix-plz"
       (make-feed nil)
       (spit "/tmp/foo.xml"))

  (require '[utilza.repl :as urepl])
  
  (urepl/massive-spew "/tmp/foo.edn" *1)
  
  )