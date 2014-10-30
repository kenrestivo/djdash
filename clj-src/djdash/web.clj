(ns djdash.web
  (:require [taoensso.timbre :as log]
            [environ.core :as env]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [compojure.core :as compojure]
            [ring.middleware.resource :as res]
            [ring.middleware.file-info :as file-info]))


;; TODO: wrap exceptions that logs them? or are they logged anyway with timbre?



(compojure/defroutes app-routes
  (compojure/GET "/" [] "This is still an API"))


;; could actually move this to the db namespace, it doesn't really matter, wherever


(defn make-handler
  [db]
  (-> #'app-routes
      handler/site
      (res/wrap-resource  "public") ;; for css and js
      file-info/wrap-file-info ;; for correct mime types
      ))