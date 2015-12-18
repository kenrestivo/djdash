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
  [{:keys [mode cljs] :as settings}
   {:keys [ring-ajax-get-or-ws-handshake ring-ajax-post]}
   dbc
   schedule-agent]
  (log/debug "app routes " settings schedule-agent dbc)
  (compojure/routes 
   (compojure/GET  "/chatlog" [] (-> dbc 
                                     chatlog/get-log
                                     r/response
                                     cors-ify))
   (compojure/GET "/sched/:offset" [offset :<< coerce/as-int]
                  (-> {:content (shows/calendar offset schedule-agent)}
                      (json/encode true)
                      r/response
                      cors-ify))
   (compojure/GET "/ch" req (ring-ajax-get-or-ws-handshake req))
   (compojure/POST "/ch" req (ring-ajax-post req))
   (compojure/GET "/" [] (stencil/render-file "templates/index"
                                              {:js-slug (stencil/render-file
                                                         (if (= :dev mode)
                                                           "templates/dev"
                                                           "templates/rel")
                                                         {})
                                               :settings (json/encode cljs)}))))



(defn make-handler
  [settings sente dbc schedule-agent]
  {:pre [(every? (comp not nil?) [settings schedule-agent dbc sente])]}
  (log/debug "make handler" settings schedule-agent dbc sente)
  (-> settings
      (app-routes sente dbc schedule-agent)
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
