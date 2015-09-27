(ns djdash.schedule
  (:require  [taoensso.timbre :as log]
             [com.stuartsierra.component :as component]
             [cheshire.generate :as jgen]
             [clj-time.coerce :as coerce]             
             [clj-time.core :as ctime]
             [clj-time.format :as fmt]
             [djdash.utils :as utils]
             [cheshire.core :as json]
             [clj-ical.format :as ical]
             [utilza.misc :as umisc]
             [clojure.tools.trace :as trace])
  (:import  java.text.SimpleDateFormat
            java.util.Locale
            java.util.TimeZone))


(jgen/add-encoder  java.util.Date
                   (fn [c jg]
                     (-> c
                         org.joda.time.DateTime.
                         coerce/to-long
                         (jgen/encode-long jg))))


;; the server supplying the json with the schedule
;; is assumed to always at least pretend to be on the west coast of the usa.
(def read-df
  (doto (SimpleDateFormat. "yyyy-MM-dd HH:mm", Locale/US)
    (.setTimeZone (TimeZone/getTimeZone "America/Los_Angeles"))))

(defn ->ical-fix
  [s]
  (with-out-str
    (-> s ical/write-object)))


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
       (group-by #(->> % :start_timestamp (.after d)))
       vals
       (zipmap [:current :future])))



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


;; XXX there is a bug here in last-started, if the program suspends and resumes,
;; the last-started is stale, needs to be force-refreshed, by forcing it nil
;; should do that on the android app.
;; ugly but it works
;; another limitation of this, is taht it does not catch shows added in between
;; now and the next previosuly-scheduled show!
;; rare, so not too worrried about it.
(defn update-schedule-fn
  "Takes a URL and a current #inst, and returns a function
   which takes the old schedule atom and updates the future/current and last-started,
   and goes out and fetches a new schedule if a new show has started since last check."
  [^java.lang.String url ^java.util.Date d]
  (fn [{:keys [last-started current future]}]
    (let [{:keys [current future] :as new-sched} (->> (concat current future) ;; rejoining for resplitting
                                                      (split-by-current d))
          new-last-started (-> current last :start_timestamp)]
      (-> (or (some->> url fetch-schedule  (split-by-current d))
              new-sched)
          (assoc :last-started new-last-started)))))


(defn update-schedule
  ([schedule ^java.lang.String url ^java.util.Date date]
     (send-off schedule (update-schedule-fn url date)))
  ([schedule ^java.lang.String url]
     (update-schedule schedule url (java.util.Date.))))



(defn start-checker
  [schedule sente url check-delay]
  (future (while true
            (try
              ;;(log/debug "checking" url)
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



(defn watch-schedule-fn
  [sente ical-file next-up-file]
  (fn [k r o n]
    (let [old-future (-> o :future first)
          new-future (-> n :future first)]
      (log/trace k "schedule atom watch updated")
      (when (not= old-future new-future)
        (do
          (log/trace k "schedule changed " o " -> " n)
          (try
            (->> n
                 ->ical
                 (spit ical-file))
            (catch Exception e
              (log/error e)))
          (try 
            (->> new-future
                 json/encode
                 (spit next-up-file))
            (catch Exception e
              (log/error e)))
          (utils/broadcast* sente :djdash/next-shows n))))))




(defn start-scheduler
  [{:keys [url check-delay ical-file up-next-file]} sente]
  ;; TODO: make this an agent not an atom, and send-off it
  (let [schedule (agent  {:current []
                          :future []
                          :last-started nil}
                         :error-handler #(log/error %))]
    (add-watch schedule :djdash/update (watch-schedule-fn sente ical-file up-next-file))
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
      (assoc this :scheduler-internal (start-scheduler (:settings this) (-> this :web-server :sente)))))
  (stop
    [this]
    (log/info "stopping scheduler " (:settings this))
    (if-not scheduler-internal
      this
      (do
        (log/trace "branch hit, stopping" this)
        (stop-schedule this)
        (assoc this :scheduler-internal nil)))))


(defn create-scheduler
  [settings]
  (log/info "schedule " settings)
  (component/using
   (map->Schedule {:settings settings})
   [:log :web-server]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(comment

  (require '[djdash.core :as sys])


  (do
    (swap! sys/system component/stop-system [:scheduler])
    (swap! sys/system component/start-system [:scheduler])
    )
  

  (log/error (.getCause *e))
  
  (log/set-level! :trace)

  (log/set-level! :debug)
  
  (log/set-level! :info)
  
  (require '[utilza.repl :as urepl])

  (defonce fake-schedule (atom {:current []
                                :future []
                                :last-started nil}))
  
  (->> "http://localhost/schedule-test/week-info"
       (update-schedule fake-schedule )
       (urepl/massive-spew "/tmp/foo.edn"))

  (->> "http://spazradio.bamfic.com/api/week-info"
       (update-schedule fake-schedule )
       (urepl/massive-spew "/tmp/foo.edn"))


  ;; for testing
  (->> @sys/system
       :scheduler
       :scheduler-internal
       :schedule
       deref
       ->ical
       (spit "/home/www/spazradio.ics"))
  

  (require '[clojure.tools.trace :as trace])

  (trace/trace-vars stop)

  
  )