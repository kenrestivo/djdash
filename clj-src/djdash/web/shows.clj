(ns djdash.web.shows
  (:require [cheshire.core :as json]
            [cheshire.generate :as jgen]
            [clj-time.coerce :as coerce]
            [djdash.schedule.public :as pub]
            [utilza.hiccupy :as hutils]
            [hiccup.core :as h]
            [djdash.schedule.ical :as ical]
            [clj-time.core :as time]
            [clj-time.format :as fmt]
            [clojure.walk :as walk]
            [clojure.core.async :as async]
            [com.stuartsierra.component :as component]
            [djdash.utils :as utils]
            [taoensso.timbre :as log]
            [utilza.misc :as umisc])
  (:import (java.text SimpleDateFormat)
           (java.util Locale TimeZone)))


(def stupid-days
  ;; should really use clj-time for this
  {1 "Monday"
   2 "Tuesday"
   3 "Wednesday"
   4 "Thursday"
   5 "Friday"
   6 "Saturday"
   7 "Sunday"})

(defn jsonify-date
  [d]
  (-> d coerce/to-long str))

(def date-fixes
  {:start jsonify-date
   :end jsonify-date})


(defn divify-show
  [{:keys [start end name url duration weekday] :as show}]
  ;; MUST do the td here because i need to insert the rowspan
  [:td (when (pos? duration) {:rowspan (str duration)})
   [:div {:class "master-show-entry"}
    [:span {:class "show-title"}
     (if (empty? url) 
       name
       [:a {:href url} name])]
    [:span {:class "show-time"
            :weekday (str weekday)
            :start start
            :end end}]]])


(defn divify-shows
  [shows]
  (walk/postwalk (fn [x]
                   (if(map? x)
                     (->> x 
                          (umisc/munge-columns date-fixes)
                          divify-show)
                     x))
                 shows))


(defn table-header
  [content]
  [:article {:class "post-21 page type-page status-publish hentry"}
   [:header {:class "entry-header"}
    [:h1 {:class "entry-title"} "SPAZ Radio Schedule"]]
   [:div {:class "single-entry-content entry-content"}
    [:table  {:id "master-program-schedule"}
     content]]])

(defn tablify-sched
  "Make html table from vector of vectors,
  i.e [[r1-c1 r1-c2 r1-c3] [r2-c1 r2-c2 r2-c3] ...  ]"
  [vv]
  (concat [[:tr {:class "master-program-day-row"}
    (concat [[:th]]  (for [d (range 1 8)]
                       [:th {:weekday (str d)
                             :class "weekday"} 
                        (get stupid-days d)]))]]
   (for [[hh r] (map vector (range) vv)]
     [:tr
      [:th {:class "master-program-hour"
            :hour (str hh)} hh]
      (for [td r]
        (if-not (empty? td)
          (first td)
          [:td ]))])))


(defn calendar
  [offset sched-agent]
  (log/trace offset sched-agent)
  (try
    (->> sched-agent
         deref 
         (pub/calendar offset)
         divify-shows
         tablify-sched
         table-header
         h/html)
    (catch Exception e
      (log/error e offset sched-agent))))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(comment

  (do
    (require '[djdash.core :as sys])
    (require '[utilza.repl :as urepl])
    )



  )
