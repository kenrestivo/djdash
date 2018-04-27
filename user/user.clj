(ns user
  (:require [clojure.test :refer :all]
            [utilza.file :as file]
            [utilza.log :as ulog]
            [utilza.repl :as urepl]
            [clj-http.client :as client]
            [figwheel-sidecar.repl-api :as ra]
            [cheshire.core :as json]
            [clojure.edn :as edn]
            [taoensso.timbre :as log]
            [djdash.nowplaying.parse :as p]
            [schema.core :as s]))




(defn start [] (ra/start-figwheel!))

(defn stop [] (ra/stop-figwheel!))

(defn cljs [] (ra/cljs-repl "dev"))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(comment


  ;; XXX note live!!
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

  (ulog/spewer
   (client/post "http://localhost:8080/now-playing"
                {:form-params {:foo "bar"}
                 :content-type :json
                 :as :json}))

  
  )



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(comment

  (start)

  (stop)

  

  

  )
