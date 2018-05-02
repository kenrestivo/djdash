(ns djdash.nowplaying.parse-test
  (:require [clojure.test :refer :all]
            [utilza.file :as file]
            [utilza.log :as ulog]
            [utilza.repl :as urepl]
            [clj-http.client :as client]
            [clojure.string :as str]
            [cheshire.core :as json]
            [clojure.edn :as edn]
            [taoensso.timbre :as log]
            [djdash.nowplaying.parse :refer :all]
            [schema.core :as s]))

;; rather crude regression test
(deftest all-parsing
  (let [ms (->> "test-data/lines-without-live.json"
                slurp
                (#(json/decode % true))
                (map parse))
        expected (-> "test-data/lines-without-live-parsed.edn"
                     slurp
                     edn/read-string)]
    (is (= ms expected))))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(comment

  (ulog/catcher
   (run-tests))

  (log/set-level! :trace)
  
  
  ;; cheat
  (ulog/catcher
   (log/set-level! :info)
   (let [ms (->> "test-data/lines-without-live.json"
                 slurp
                 (#(json/decode % true))
                 (map parse))]
     (urepl/massive-spew "test-data/lines-without-live-parsed.edn" ms)))



  (ulog/spewer
   (->> "test-data/lines-without-live.json"
        slurp
        (#(json/decode % true))
        (map parse)
        (map :playing)))



  )


(comment
  ;;; post test
  (do
    (require '[djdash.core :as sys])
    )

  (log/trace :wtf)

  ;; STFU
  (log/merge-config! {:ns-blacklist ["djdash.matrix" "djdash.geolocate"]})

  ;; run a  test post!
  (ulog/catcher
   (let [p (->> "test-data/lines-without-live.json"
                slurp
                json/decode
                (rand-nth))]
     (log/trace p)
     (client/post "http://localhost:8080/now-playing"
                  {:form-params p
                   :content-type :json
                   :throw-exceptions true})))






  (->> @sys/system
       :nowplaying
       :nowplaying-internal
       :nowplaying
       deref
       )

  (empty? " ")

  (str/trim " " )
  
  )


(comment

  ;; TODO: make into unit tests

  
  (make-download "/usr/share/airtime/public/archives/olde/2014-11-10-.ogg")
  (make-download "/usr/share/airtime/public/archives/imported/15/MADARO/unknown/unknown-E_MERGE_N_C  -320kbps.mp3")
  (make-download "/usr/share/airtime/public/archives/imports/2018-02-07-Sound_Dimensions_Radio.ogg") 

  ;; failign test case
  (ulog/spewer
   (-> "test-data/broken-download.json"
       slurp
       (json/decode true) 
       parse
       ))


  (ulog/spewer
   (-> "test-data/broken-artist-title.json"
       slurp
       (json/decode true) 
       parse
       ))


  (ulog/spewer
   (-> "test-data/broken-artist-title.json"
       slurp
       (json/decode true) 
       mangle
       ))
  

  (ulog/spewer
   (-> "/usr/share/airtime/public/archives/imports/2018-02-07-Sound_Dimensions_Radio.ogg"
       mangle-from-filename
       ))


  
  )
