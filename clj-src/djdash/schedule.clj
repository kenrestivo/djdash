(ns djdash.schedule
  (:require [cheshire.core :as json]
            [cheshire.generate :as jgen]
            [utilza.log :as ulog]
            [clj-time.coerce :as coerce]
            [djdash.schedule.public :as pub]
            [djdash.schedule.ical :as ical]
            [clj-time.core :as time]
            [clj-time.format :as fmt]
            [clojure.core.async :as async]
            [com.stuartsierra.component :as component]
            [djdash.utils :as utils]
            [taoensso.timbre :as log]
            [utilza.misc :as umisc])
  (:import (java.text SimpleDateFormat)
           (java.util Locale TimeZone)))


(jgen/add-encoder  java.util.Date
                   (fn [c jg]
                     (-> c
                         org.joda.time.DateTime.
                         coerce/to-long
                         (jgen/encode-long jg))))


(jgen/add-encoder  org.joda.time.DateTime
                   (fn [c jg]
                     (-> c
                         coerce/to-long
                         (jgen/encode-long jg))))



;; the server supplying the json with the schedule
;; is assumed to always at least pretend to be on the west coast of the usa.
(def read-df
  (doto (SimpleDateFormat. "yyyy-MM-dd HH:mm", Locale/US)
    (.setTimeZone (TimeZone/getTimeZone "America/Los_Angeles"))))


(defn datefix
  [^java.lang.String s]
  (.parse read-df s))

(def colfixes {:start_timestamp datefix
               :end_timestamp datefix})


(defn fix-record
  "Takes a single map for a show,
    returns a map of the properly formatted data"
  [m]
  (umisc/munge-columns colfixes
                       (select-keys m [:start_timestamp :end_timestamp :name :url])))



(defn parse-weekly
  "Takes vector of vectors of maps, outputs formatted, sorted schedule as seq of maps"
  [xs]
  (->> xs
       vals
       (filter vector?) ;; elimnate the api version, which is a string and in the way
       (apply concat) ;; squash all the days together
       (map fix-record)
       (sort-by :start_timestamp)))


(defn split-by-current
  "Takes a current #inst, and a map of the schedule atom.
   Returns the updated schedule atom with the current and future updated for that time provided."
  [^java.util.Date d m]
  (->> m
       (group-by #(some->> % :start_timestamp (.after d)))
       vals
       reverse ;; because sometimes there are no current, everything is future!
       (zipmap [:future :current])))


(defn fake-jsonp
  [s]
  (str "update_schedule_meta(\n" s "\n);"))

(defn jsonify-date
  [i]
  (.getTime i))


(def jsonify-dates {:start_timestamp jsonify-date
                    :end_timestamp jsonify-date})


(defn fetch-schedule
  [^java.lang.String url]
  (try
    (some-> url
            slurp
            (json/decode true)
            parse-weekly)
    (catch Exception e
      (log/error e)
      (str "Error Connecting" "..."))))



(defn update-schedule-fn
  "Takes a URL and a current #inst, and returns a function
   which takes the old schedule atom and updates the future/current and last-started,
   and goes out and fetches a new schedule."
  [^java.lang.String url ^java.util.Date d]
  (fn [{:keys [current future] :as old}]
    (try
      (let [{:keys [current future] :as new-sched} (->> (concat current future) ;; rejoining for resplitting
                                                        (split-by-current d))]
        (-> (or (some->> url fetch-schedule  (split-by-current d))
                new-sched)
            ;; don't need to keep all the old currents!
            (update-in [:current] #(-> % last vector))))
      (catch Exception e
        (log/error e)
        (log/error (.getCause e))
        old))))



(defn update-schedule
  ([schedule ^java.lang.String url ^java.util.Date date]
   (send-off schedule (update-schedule-fn url date)))
  ([schedule ^java.lang.String url]
   (update-schedule schedule url (java.util.Date.))))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn start-checker
  [schedule sente url check-delay]
  (log/info "starting schedule checker thread")
  (future (while true
            (try
              (log/debug "checking" url)
              (update-schedule schedule url)
              (catch Exception e
                (log/error e)))
            (Thread/sleep check-delay))))


(defn stop-schedule
  [{{:keys [schedule check-thread]} :scheduler-internal}]
  (log/trace "stopping schedule. atom:" schedule)
  (try
    (remove-watch schedule :djdash/update)
    (some-> check-thread future-cancel)
    (catch Exception e
      (log/error e))))




(defn schedule-listen-loop
  [{:keys [chsk-send! recv-pub]} {:keys [schedule]}]
  {:pre [(= clojure.lang.Agent (type schedule))
         (every? (comp not nil?) [chsk-send! recv-pub])]}
  (let [sente-ch (async/chan (async/sliding-buffer 1000))
        quit-ch (async/chan (async/sliding-buffer 1000))]
    (future (try
              (async/sub recv-pub  :djdash/schedule sente-ch)
              (log/info "starting sub loop for schedule channel")
              (loop []
                (let [[{:keys [id client-id ?data]} ch] (async/alts!! [quit-ch sente-ch])]
                  (when (= ch sente-ch)
                    (log/debug "sub sending reply to" client-id ?data)
                    (chsk-send! client-id [:djdash/next-shows
                                           @schedule])
                    (recur))))
              (catch Exception e
                (log/error e)))
            (log/info "exiting sub loop for schedule")
            (async/unsub recv-pub :djdash/schedule sente-ch))
    quit-ch))


(defn watch-schedule-fn
  [sente {:keys [ical-file next-up-file json-current-file json-schedule-file]}]
  (fn [k r o n]
    (let [old-future (-> o :future)
          new-future (-> n :future)]
      (log/trace k "schedule atom watch updated")
      (when (not= old-future new-future)
        (log/debug k "schedule changed " o " -> " n)
        (future
          (ulog/catcher
           (->> new-future
                json/encode
                (spit json-schedule-file)))
          (ulog/catcher
           (->> new-future
                first
                json/encode
                fake-jsonp
                (spit next-up-file)))
          (ulog/catcher
           (->> n
                :current
                json/encode
                (spit json-current-file)))
          (ulog/catcher
           (log/info "dumping schedule to" ical-file)
           (->> n
                ical/->ical
                (spit ical-file))))
        (utils/broadcast sente :djdash/next-shows n)))))




(defn start-scheduler
  [{:keys [url check-delay] :as settings} sente]
  ;; TODO: make this an agent not an atom, and send-off it
  (let [schedule (agent  {:current []
                          :future []}
                         :error-mode :continue
                         :error-handler #(log/error %))]
    (set-validator! schedule map?)
    (add-watch schedule :djdash/update (watch-schedule-fn sente settings))
    (log/debug "start-scheduler called")
    {:check-thread (start-checker schedule sente url check-delay)
     :schedule schedule}))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord Schedule [settings scheduler-internal]
  component/Lifecycle
  (start
    [this]
    (log/info "starting scheduler " (:settings this))
    (if scheduler-internal
      this 
      (let [scheduler-internal (start-scheduler (:settings this) (-> this :sente :sente))
            listen-loop (schedule-listen-loop (-> this :sente :sente) scheduler-internal)]
        (log/debug "start-scheduler and schedule-listen-loop returned")
        (assoc this :scheduler-internal (merge scheduler-internal
                                               {:quit-chan listen-loop})))))
  (stop
    [this]
    (log/info "stopping scheduler " (:settings this))
    (if-not scheduler-internal
      this
      (do
        (log/debug "branch hit, stopping" this)
        (async/put! (-> this :scheduler-internal :quit-chan) :quit)
        (stop-schedule this)
        (assoc this :scheduler-internal nil)))))


(defn create-scheduler
  [settings]
  (log/info "schedule " settings)
  (component/using
   (map->Schedule {:settings settings})
   [:log :sente]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(comment

  (do
    (require '[djdash.core :as sys])
    (require '[utilza.repl :as urepl])
    )

  (do
    (swap! sys/system component/stop-system [:scheduler])
    (swap! sys/system component/start-system [:scheduler])
    )

  (log/error (.getCause *e))
  
  (log/set-level! :trace)

  (log/set-level! :debug)
  
  (log/set-level! :info)
  
  ;; nil???
  (try
    (->> sys/system deref :scheduler :scheduler-internal)
    (catch Throwable e
      (log/error e)))


  ;; only shows this week not next week. but works
  (->> (fetch-schedule "http://radio.spaz.org/api/week-info/")
       (urepl/massive-spew "/tmp/foo.edn"))

  

  
  
  )
