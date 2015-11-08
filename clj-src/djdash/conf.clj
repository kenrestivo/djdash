(ns djdash.conf
  (:require [schema.core :as s]
            [clojure.edn :as edn]))


(def Db
  {(s/required-key :host)  s/Str
   (s/required-key :db)  s/Str
   (s/required-key :user)  s/Str
   (s/required-key :password)  s/Str
   (s/required-key :port)  s/Int})


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
                           (s/required-key :url)  s/Str}
   (s/required-key :db)  Db
   (s/required-key :scheduler)  {(s/required-key :url)  s/Str
                                 (s/required-key :ical-file)  s/Str
                                 (s/required-key :json-schedule-file)  s/Str
                                 (s/required-key :up-next-file)  s/Str
                                 (s/required-key :check-delay)  s/Int}
   (s/required-key :web-server)  {(s/required-key :port)  s/Int
                                  (s/required-key :chat-url)  s/Str
                                  (s/required-key :mode)  s/Keyword}
   (s/required-key :timbre)  {(s/required-key :level)  s/Keyword
                              (s/required-key :spit-filename)  s/Str}})


(defn read-and-validate
  [conf-file]
  (->> conf-file
       slurp
       edn/read-string
       (s/validate Conf)))
