(ns djdash.web
  (:require [compojure.core :as compojure]
            [compojure.handler :as handler]
            [ring.middleware.file-info :as file-info]
            [ring.middleware.resource :as res]
            [stencil.core :as stencil]
            [taoensso.timbre :as log]))


;; TODO: wrap exceptions that logs them? or are they logged anyway with timbre?





(defn app-routes
  [{:keys [mode chat-url] :as settings}
   {:keys [ring-ajax-get-or-ws-handshake ring-ajax-post]}]
  (log/debug "app routes " settings)
  (compojure/routes 
   (compojure/GET  "/ch" req (ring-ajax-get-or-ws-handshake req))
   (compojure/POST "/ch" req (ring-ajax-post req))
   (compojure/GET "/" [] (stencil/render-file "templates/index"
                                              {:js-slug (stencil/render-file
                                                         (if (= :dev mode)
                                                           "templates/dev"
                                                           "templates/rel")
                                                         {})
                                               :chat-url chat-url}))))



(defn make-handler
  [settings sente]
  (log/debug "make handler" settings)
  (-> settings
      (app-routes sente)
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


  
  
  )
