(ns user
  (:require [figwheel-sidecar.repl-api :as ra]
            [utilza.log :as ulog]
            [clj-http.client :as client]))

(defn start [] (ra/start-figwheel!))

(defn stop [] (ra/stop-figwheel!))

(defn cljs [] (ra/cljs-repl "dev"))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(comment

  (start)

  (stop)

  (ulog/spewer
   (client/post "http://localhost:8080/now-playing"
                {:form-params {:foo "bar"}
                 :content-type :json
                 :as :json}))

  

  )
