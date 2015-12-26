(ns djdash.web
  (:require [compojure.core :as compojure]
            [compojure.handler :as handler]
            [cheshire.core :as json]
            [ring.middleware.jsonp :as jsonp]
            [djdash.web.shows :as shows]
            [ring.middleware.file-info :as file-info]
            [djdash.chat.chatlog :as chatlog]
            [compojure.coercions :as coerce]
            [ring.middleware.resource :as res]
            [ring.util.response :as r]
            [stencil.core :as stencil]
            [taoensso.timbre :as log]))


;; TODO: wrap exceptions that logs them? or are they logged anyway with timbre?


(defn cors-ify
  [resp]
  (-> resp
      (r/header "Access-Control-Allow-Origin" "*")
      (r/header "Access-Control-Allow-Methods" "GET, OPTIONS")
      (r/header "Content-Type" "application/json; charset=utf-8")
      (r/header "Access-Control-Allow-Headers" 
                "Content-Type, Content-Range, Content-Disposition, Content-Description, x-requested-with")))



(defn app-routes
  []
  (log/debug "app routes ")
  (compojure/routes 
   (compojure/GET  "/chatlog" req (-> req
                                      :dbc 
                                      chatlog/get-log
                                      r/response
                                      cors-ify))
   (compojure/GET "/sched/:offset" {:keys [schedule-agent params]}
                  (log/debug params)
                  (-> {:content (shows/calendar (-> params :offset coerce/as-int) schedule-agent)}
                      (json/encode true)
                      r/response
                      cors-ify))
   (compojure/GET "/ch" req ((-> req :sente :ring-ajax-get-or-ws-handshake) req))
   (compojure/POST "/ch" req ((-> req :sente :ring-ajax-post) req))
   (compojure/GET "/" req (stencil/render-file "templates/index"
                                               {:js-slug (stencil/render-file
                                                          (if (= :dev (-> req :settings :mode))
                                                            "templates/dev"
                                                            "templates/rel")
                                                          {})
                                                :settings (-> req :settings :cljs json/encode)}))))



(defn make-handler
  []
  (log/debug "make handler")
  (-> (app-routes)
      jsonp/wrap-json-with-padding
      handler/site
      (res/wrap-resource  "public") ;; for css and js
      file-info/wrap-file-info ;; for correct mime types
      ))


(defn reload-templates
  []
  (doseq [e ["templates/index" "templates/dev" "templates/rel"]]
    (stencil.loader/invalidate-cache-entry e)))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(comment

  (reload-templates)
  
  
  )
