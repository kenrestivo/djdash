(ns djdash.schedule.ical
  (:require [cheshire.core :as json]
            [cheshire.generate :as jgen]
            [clj-ical.format :as ical]
            [clj-time.coerce :as coerce]
            [clj-time.core :as time]
            [clj-time.format :as fmt]
            [clojure.core.async :as async]
            [com.stuartsierra.component :as component]
            [djdash.utils :as utils]
            [taoensso.timbre :as log]
            [utilza.misc :as umisc])
  (:import (java.text SimpleDateFormat)
           (java.util Locale TimeZone)))


(defn ->ical-fix
  [s]
  (with-out-str
    (-> s ical/write-object)))




(defn ->ical
  [sched]
  (->ical-fix
   `[:vcalendar
     [:version "2.0"]
     [:method "PUBLISH"]
     [:prodid "-//S.P.A.z. Radio//spazradio//EN"]
     ~@(for [{:keys [url name end_timestamp start_timestamp]} (:future sched)]
         [:vevent
          [:summary name]
          [:uid (-> start_timestamp
                    org.joda.time.DateTime.
                    coerce/to-long)]
          [:url "http://spaz.org/radio"]
          [:dtend   (fmt/unparse (fmt/formatters :basic-date-time-no-ms)
                                 (org.joda.time.DateTime. end_timestamp))]
          [:dtstart   (fmt/unparse (fmt/formatters :basic-date-time-no-ms)
                                   (org.joda.time.DateTime. start_timestamp))]])]))

