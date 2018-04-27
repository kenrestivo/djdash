(ns user
  (:require [clojure.test :refer :all]
            [utilza.file :as file]
            [utilza.log :as ulog]
            [utilza.repl :as urepl]
            [clj-http.client :as client]
            [cheshire.core :as json]
            [clojure.edn :as edn]
            [taoensso.timbre :as log]
            [djdash.nowplaying.parse :as p]
            [schema.core :as s]))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(comment

  
  (ulog/catcher
   (let [p (->> "/mnt/sdcard/tmp/whynot.json"
                slurp
                json/decode
                (rand-nth))]
     (log/trace p)
     (client/post "http://radio.spaz.org:8080/now-playing"
                  {:form-params p
                   :content-type :json
                   :throw-exceptions true})))
  )

