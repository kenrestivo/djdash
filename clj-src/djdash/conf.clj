(ns djdash.conf
  (:require [schema.core :as s]
            [clojure.edn :as edn]))


(def Db
  {(s/required-key :host)  s/Str
   (s/required-key :db)  s/Str
   (s/required-key :user)  s/Str
   (s/required-key :password)  s/Str
   (s/required-key :port)  s/Int})


(def Mqtt
  {(s/required-key :host)  s/Str
   (s/required-key :timeout)  s/Int
   (s/required-key :retry)  s/Int
   (s/required-key :tries)  (s/either s/Int s/Keyword)
   (s/required-key :keep-alive)  s/Int
   (s/required-key :port)  s/Int})


(def Chat
  {(s/required-key :topic)  s/Str
   (s/required-key :qos) s/Int})


(def Cljs
  {(s/required-key :chat) {(s/required-key :history_url) s/Str
                           (s/required-key :serv) s/Str
                           (s/optional-key :presence_chan) s/Str
                           (s/required-key :port) s/Int
                           (s/optional-key :timeout) s/Int
                           (s/required-key :chan) s/Str}
   (s/optional-key :mode) s/Keyword
   (s/optional-key :log-level) s/Keyword
   })



(def Web
  {(s/required-key :port)  s/Int
   (s/required-key :mode)  s/Keyword
   (s/required-key :cljs) Cljs})



(def Conf
  {(s/required-key :tailer) {(s/required-key :fpath) s/Str
                             (s/required-key :bufsiz) s/Int
                             (s/required-key :file-check-delay) s/Int
                             (s/required-key :chunk-delay) s/Int}
   (s/required-key :hubzilla) {(s/required-key :url) s/Str
                               (s/required-key :channel)  s/Str
                               (s/required-key :login)  s/Str
                               (s/required-key :timeout-ms)  s/Int
                               (s/required-key :max-timeout-ms)  s/Int
                               (s/required-key :bump-factor)  (s/pred float? "float")
                               (s/required-key :listen)  s/Str
                               (s/required-key :pw)  s/Str}
   (s/required-key :now-playing)  {(s/required-key :check-delay)  s/Int
                                   (s/required-key :host)  s/Str
                                   (s/required-key :adminuser)  s/Str
                                   (s/required-key :song-mount)  s/Str
                                   (s/required-key :adminpass)  s/Str
                                   (s/required-key :fake-json-file)  s/Str
                                   (s/required-key :fake-jsonp-file)  s/Str
                                   (s/required-key :port)  s/Int}
   (s/required-key :geo)  {(s/required-key :api-key)  s/Str
                           (s/required-key :ratelimit-delay-ms)  s/Int
                           (s/required-key :max-retries) s/Int
                           (s/required-key :retry-wait) s/Int
                           (s/required-key :url)  s/Str}
   (s/required-key :mqtt)  Mqtt
   (s/required-key :db)  Db
   (s/required-key :chat)  Chat
   (s/optional-key :nrepl) {(s/required-key :port) s/Int}
   (s/required-key :scheduler)  {(s/required-key :url)  s/Str
                                 (s/required-key :ical-file)  s/Str
                                 (s/required-key :json-schedule-file)  s/Str
                                 (s/required-key :up-next-file)  s/Str
                                 (s/required-key :check-delay)  s/Int}
   (s/required-key :web-server)  Web
   (s/required-key :timbre)  {(s/required-key :level)  s/Keyword
                              (s/required-key :spit-filename)  s/Str}})


(defn read-and-validate
  [conf-file]
  (->> conf-file
       slurp
       edn/read-string
       (s/validate Conf)))
