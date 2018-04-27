(ns djdash.web
  (:require [compojure.core :as compojure]
            [compojure.handler :as handler]
            [cheshire.core :as json]
            [ring.middleware.jsonp :as jsonp]
            [utilza.log :as ulog]
            [djdash.web.shows :as shows]
            [ring.middleware.file-info :as file-info]
            [djdash.nowplaying.parse :as parse]
            [djdash.chat.chatlog :as chatlog]
            [compojure.coercions :as coerce]
            [ring.middleware.resource :as res]
            [ring.util.response :as r]
            [stencil.core :as stencil]
            [taoensso.timbre :as log]))



(defn wrap-exception
  [handler]
  (fn [request]
    (try
      (handler request)
      (catch Throwable e
        (log/error e request)
        (throw e)))))

(defn cors-ify
  [resp]
  (-> resp
      (r/header "Content-Type" "application/json; charset=utf-8")
      (r/header "Access-Control-Allow-Origin" "*")
      (r/header "Access-Control-Allow-Methods" "GET, OPTIONS")
      (r/header "Access-Control-Allow-Headers" 
                "Content-Type, Content-Range, Content-Disposition, Content-Description, x-requested-with")))


(defn now-playing
  [req]
  #_(log/trace "now playing API req, got:" req )
  (let [res (some-> req
                    :body
                    slurp
                    (json/decode true) 
                    parse/parse)]
    (log/trace "now playing POST, decoded as:" res)
    (ulog/catcher
     (some-> req
             :nowplaying
             (send merge res )))
    (r/response "OK")))

(defn index
  [req]
  (stencil/render-file "templates/index"
                       {:js-slug (stencil/render-file
                                  (if (= :dev (-> req :settings :mode))
                                    "templates/dev"
                                    "templates/rel")
                                  {})
                        :settings (-> req :settings :cljs json/encode)}))


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
   ;; TODO; Send to nowplaying for processing
   (compojure/POST "/now-playing" req (now-playing req))
   (compojure/GET "/ch" req ((-> req :sente :ring-ajax-get-or-ws-handshake) req))
   (compojure/POST "/ch" req ((-> req :sente :ring-ajax-post) req))
   (compojure/GET "/" req (index req))))



(defn make-handler
  []
  (log/debug "make handler")
  (-> (app-routes)
      ;; TODO: json body?
      jsonp/wrap-json-with-padding
      handler/site
      (res/wrap-resource  "public") ;; for css and js
      file-info/wrap-file-info ;; for correct mime types
      wrap-exception
      ))


(defn reload-templates
  []
  (doseq [e ["templates/index" "templates/dev" "templates/rel"]]
    (stencil.loader/invalidate-cache-entry e)))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(comment

  (reload-templates)
  
  
  )
