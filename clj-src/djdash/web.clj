(ns djdash.web
  (:require [taoensso.timbre :as log]
            [environ.core :as env]
            [stencil.core :as stencil]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [taoensso.sente :as sente]
            [compojure.core :as compojure]
            [ring.middleware.resource :as res]
            [ring.middleware.file-info :as file-info]))


;; TODO: wrap exceptions that logs them? or are they logged anyway with timbre?


(let [{:keys [ch-recv send-fn ajax-post-fn ajax-get-or-ws-handshake-fn
              connected-uids]}
      (sente/make-channel-socket! {})] ;; supply userid fn here, or just use :uid in ring
  (def ring-ajax-post                ajax-post-fn)
  (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
  (def ch-chsk                       ch-recv) ; ChannelSocket's receive channel
  (def chsk-send!                    send-fn) ; ChannelSocket's send API fn
  (def connected-uids                connected-uids) ; Watchable, read-only atom
  )


(defn app-routes
  [{:keys [mode playing-url chat-url] :as settings}]
  (log/info "app routes " settings)
  (compojure/routes 
   (compojure/GET  "/ch" req (ring-ajax-get-or-ws-handshake req))
   (compojure/POST "/ch" req (ring-ajax-post                req))
   (compojure/GET "/" [] (stencil/render-file "templates/index"
                                              {:js-slug (stencil/render-file
                                                         (if (= :dev (:mode settings))
                                                           "templates/dev"
                                                           "templates/rel")
                                                         {})
                                               :playing-url (:playing-url settings)
                                               :chat-url (:chat-url settings)}))))



(defn make-handler
  [settings]
  (log/info "make handler" settings)
  (-> settings
      app-routes
      handler/site
      (res/wrap-resource  "public") ;; for css and js
      file-info/wrap-file-info ;; for correct mime types
      ))


(defn reload-templates
  []
  (doseq [e ["templates/index" "templates/dev" "templates/rel"]]
    (stencil.loader/invalidate-cache-entry e)))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
