(ns djdash.schedule.public
  (:require [cheshire.core :as json]
            [cheshire.generate :as jgen]
            [clj-ical.format :as ical]
            [clj-time.coerce :as coerce]
            [clj-time.core :as time]
            [schema.core :as s]
            [clj-time.format :as fmt]
            [clojure.core.async :as async]
            [com.stuartsierra.component :as component]
            [djdash.utils :as utils]
            [taoensso.timbre :as log]
            [utilza.misc :as umisc])
  (:import (java.text SimpleDateFormat)
           (java.util Locale TimeZone)))


(defn tz-shift
  [offset ts]
  (-> ts
      org.joda.time.DateTime.
      (time/to-time-zone
       (time/time-zone-for-offset offset))))



(defn date-adjust
  "Displays schedule in timezone shifted by shift"
  [offset]
  (let [shifter (partial tz-shift offset)]
    {:start_timestamp shifter
     :end_timestamp shifter}))

(defn duration-hours
  "displays a show's duration in hours"
  [{:keys [start_timestamp end_timestamp] :as s}]
  (assoc s :duration (->  (time/interval start_timestamp end_timestamp)
                          time/in-minutes
                          (/ 60.0)
                          Math/ceil
                          int)))



;;; XXX duplicate of clj-time.predicates/same-date? perhaps?
(defn same-day?
  [{:keys [end_timestamp start_timestamp]}]
  (apply = (map time/day [start_timestamp end_timestamp])))



(defn midnight
  "given a date in a tz, return midnight on that day in that tz"
  [d]
  (->
   (apply time/date-time (map #(% d) [time/year time/month time/day]))
   (.withZoneRetainFields (.getZone d))))



(defn split-show
  "given a show, return the show with the end (if start? is true) or start clamped to midnight"
  [{:keys [start_timestamp end_timestamp] :as show} start?]
  (if start?
    ;; force end
    (assoc show :end_timestamp (-> start_timestamp (time/plus (time/days 1)) midnight ))
    ;; force start
    (assoc show :start_timestamp (-> end_timestamp midnight))))


(defn split-if-crosses
  "given a show in local tz, return one show or two split shows, 
   depending on if they cross days in local tz"
  [show]
  (if (same-day? show)
    [show]
    (map (partial split-show show) [true false])))


(defn add-week
  [{:keys [start_timestamp] :as show}]
  (assoc show :weekday (time/day-of-week start_timestamp)))


(defn one-week-only
  "given a current now in the local tz, and a seq of shows, 
  return only the shows within 7 days"
  [relative-now shows]
  (filter #(time/before? (:start_timestamp %) (time/plus relative-now (time/days 7))) shows))



(defn start-hour
  [{:keys [start_timestamp] :as show}]
  (assoc show :start-hour (time/hour start_timestamp)))



(defn hourify
  "hour portion, format for html table with rows of hours and columns of weekdays."
  [shows]
  (into (sorted-map) 
        (reduce (fn [acc {:keys [start-hour weekday] :as show}]  
                  (update-in acc [start-hour] conj  show))
                (zipmap (range 0 24) (repeatedly vector)) 
                shows)))

(defn removers
  [{:keys [start-hour duration]}]
  (range (inc start-hour) (+ start-hour duration)))



(defn calculate-to-remove
  [shows-map]
  (->> shows-map
       flatten
       (reduce (fn [acc {:keys [weekday] :as show}]
                 (update-in acc [weekday] concat (removers show)))
               {})
       (reduce-kv (fn [acc weekday hours]
                    (reduce (fn [acc2 h]
                              (update-in acc2 [h] conj weekday))
                            acc
                            hours))
                  {})))




(defn weekify
  "weekday portion, format for html table with rows of hours and columns of weekdays."
  [shows]
  (->> (reduce (fn [acc {:keys [weekday] :as show}]  
                 (update-in acc [weekday] conj show))
               (zipmap (range 1 8) 
                       (repeatedly vector)) 
               shows)
       (into (sorted-map))))



(defn force-hours
  [shows]
  (vals
   (merge
    (into (sorted-map) (map-indexed vector (repeat 24 [])))
    (into (sorted-map) (map-indexed vector  shows)))))


(defn insert-actual
  [{:keys [start_timestamp end_timestamp] :as s}]
  (assoc s 
    :start start_timestamp
    :end end_timestamp))


(defn remove-unused-tds
  [shows-hours]
  (let [to-remove (calculate-to-remove shows-hours)]
    (log/trace "to remove:" to-remove)
    (for [[h wds] (map-indexed vector shows-hours)]
      ;; NOTE the fencepost: indexes are 0 based, but weekdays are 1 indexed
      (let [rs (->> h (get to-remove) (mapv dec))]
        (log/trace "removing" h rs wds)
        (utils/dissoc-vector wds rs)))))


(defn calendar
  [offset sched]
  (let [relative-now (tz-shift offset nil)]
    (->> sched
         :future
         (map #(umisc/munge-columns (date-adjust offset) %))
         (map insert-actual)
         (one-week-only relative-now)
         (mapcat split-if-crosses)
         (map (comp add-week duration-hours start-hour))
         (filter #(-> % :duration pos?))
         (sort-by  (juxt :weekday :start-hour))
         weekify
         (utils/map-vals hourify)
         vals
         umisc/uncolumnify
         rest
         (apply map vector)
         remove-unused-tds)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(comment

  (do
    (require '[djdash.core :as sys])
    (require '[utilza.repl :as urepl])
    )


  (let [offset -4
        relative-now (tz-shift offset nil)]
    (->> @sys/system 
         :scheduler 
         :scheduler-internal 
         :schedule 
         deref 
         ;;; <--- end here
         (calendar offset)
         ;; <--- begin here
         (urepl/massive-spew "/tmp/foo.edn")))


  (log/merge-config! {:ns-whitelist ["djdash.schedule.public" "djdash.utils"]})

  (log/merge-config! {:ns-whitelist []})

  (log/set-level! :trace)

  (clojure.tools.trace/trace-vars #'djdash.utils/dissoc-vector)
  (clojure.tools.trace/trace-vars #'remove-unused-tds)

  )


