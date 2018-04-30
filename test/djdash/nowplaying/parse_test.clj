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

  ;; cheat
  (ulog/catcher
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
