(ns djdash.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [om.core :as om :include-macros true]
            [weasel.repl :as ws-repl]
            [taoensso.sente  :as sente :refer (cb-success?)]
            [goog.style :as gstyle]
            [goog.storage.mechanism.mechanismfactory]
            [cljs-http.client :as http]
            [djdash.utils :as utils]
            [om.dom :as dom]
            [clojure.walk :as walk]
            [clojure.browser.dom :as ddom]
            [cljs.core.async :as async :refer [put! chan <!]])
  (:import [goog.net Jsonp]
           [goog Uri]))


(comment
  ;; don't want this with nrepl, i guess?
  (enable-console-print!)
  )

(def listener-node-name "listener-chart")

(def storage (goog.storage.mechanism.mechanismfactory.create))

(def strs {:checking "Checking..."})

(def app-state (atom {:playing {:playing (:checking strs)
                                :listeners (:checking strs)
                                :listener-history []
                                :timeout 30000
                                :url js/playing_url}
                      :chat {:url js/chat_url
                             :count 1413798924 ;; could be zero, but who wants to read all that?
                             :user ""
                             :users (:checking strs)
                             :id (int (rand 999999))
                             :errors ""
                             :message ""
                             :timeout 2000
                             :messages []
                             :lines ""}}))



(let [{:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket! "/ch" ; Note the same path as before
                                  {:type :auto ; e/o #{:auto :ajax :ws}
                                   })]
  (def chsk       chsk)
  (def ch-chsk    ch-recv) ; ChannelSocket's receive channel
  (def chsk-send! send-fn) ; ChannelSocket's send API fn
  (def chsk-state state)   ; Watchable, read-only atom


  
  ;; terrible old js-style action-at-a-distance, but i couldn't get async to work right
  (defn jsonp-playing
    [uri]
    (utils/jsonp-wrap uri (fn [res]
                            (swap! app-state (fn [s]
                                               (let [{:keys [listeners] :as new-data} (utils/un-json res)]
                                                 (-> s
                                                     (update-in  [:playing] merge  new-data)
                                                     (update-in  [:playing :listener-history] conj [(js/Date.now)
                                                                                                    listeners])))))))))

(defn live?
  [playing]
  (->> playing
       (re-find  #"^\[LIVE\!\].*?")
       boolean))

(defn on-air-div
  [playing]
  (dom/div #js {:id "on_air"
                :className (if (live? playing) "label label-danger paddy" "hidden")}
           "LIVE!"))


(defn listeners-view
  [{:keys [listeners]} owner]
  (reify
    om/IRenderState
    (render-state
      [_ s]
      (dom/div #js {:id "listeners"}
               (dom/span #js {:className "text-label"}
                         "Listeners:")
               (dom/span nil listeners)))))



(defn playing-view
  [{:keys [playing listeners url timeout]} owner]
  (reify
    om/IWillMount
    (will-mount
      [_]
      (let [c (chan)]
        (go (while true
              (jsonp-playing url)
              (<! (async/timeout timeout))))))
    om/IRenderState
    (render-state
      [_ s]
      (dom/div #js {:id "playing"}
               (dom/span #js {:className "text-label"}
                         "Now Playing:")
               (dom/span  nil
                          (on-air-div playing)
                          playing )))))



(defn process-chat
  [{:keys [lines users] :as new}]
  (swap! app-state update-in [:chat]
         (fn [{:keys [messages] :as old}]
           (merge old new {:messages (concat (utils/reverse-split lines)
                                             messages)}))))




(defn update-chat!
  [msg]
  (let [{{:keys [url user count id message]}  :chat} @app-state
        u (str url "?" (http/generate-query-string {:user user
                                                    :msg message
                                                    :lineNo count
                                                    :chatId id}))]
    (swap! app-state assoc-in [:chat :message] "")
    (utils/jsonp-wrap  u
                       (if (empty? msg)
                         ;; hack to avoid double-messages
                         #(-> % utils/un-json process-chat)
                         identity))))


(defn login
  []
  (let [u (js/prompt "who are you, dj person?")]
    (when (not (empty? u))
      (swap! app-state (fn [o]
                         (.set storage :user u)
                         (assoc-in o [:chat :user] u ))))))



(defn chat-users
  [{:keys [users] :as curs} owner]
  (reify
    om/IRenderState
    (render-state
      [_ s]
      (dom/div #js {:id "user-section"}
               (apply dom/ul #js {:className "list-inline"}
                      (cons (dom/li #js {:className "text-label"} "In Chat Now:")
                            (for [u (utils/hack-users-list users)]
                              (dom/li #js {:className "label label-default paddy"} u))))))))



(defn chat-view
  [{:keys [users messages user url errors count id timeout] :as curs} owner]
  (reify
    om/IWillMount
    (will-mount
      [_]
      (let [c (chan)]
        (go (while true
              ;; TODO: the message to send!
              (update-chat! "")
              (<! (async/timeout timeout))))))
    om/IDidMount
    (did-mount
      [_]
      (swap! app-state assoc-in [:chat :user]  (.get storage :user))
      (when (-> @app-state :chat :user empty?)
        (login)))
    om/IRenderState
    (render-state
      [_ s]
      (dom/div {:id "chat"}
               (if (empty? user)
                 (dom/button #js {:onClick (fn [_] (login))} "Log In")
                 (dom/div nil
                          (dom/label #js {:htmlFor "chatinput"
                                          :className "handle"}
                                     (str user ": "))
                          (dom/input
                           #js {:id "chatinput"
                                :placeholder "Say something here"
                                ;;:on-change #(js/console.log %)
                                :onKeyDown #(when (= (.-key %) "Enter")
                                              (om/update! curs :message (.. % -target -value))
                                              (ddom/set-value (.. % -target)  "")
                                              )})))
               (dom/div #js {:id "messages"
                             :dangerouslySetInnerHTML  #js {:__html (utils/hack-list-group messages)}})
               ))))

;;




(defn line-graph
  [data]
  (js/Dygraph.
   (js/document.getElementById listener-node-name)
   (utils/mangle-dygraph data)))


(defn line-chart
  [{:keys [listeners listener-history]} owner]
  (reify
    om/IInitState
    (init-state [_]
      {:listener-chart nil})
    om/IDidMount
    (did-mount [this]
      (let [g (line-graph listener-history)]
        (om/set-state! owner :listener-chart g)
        g))
    om/IDidUpdate
    (did-update [this prev-props prev-state]
      (when (not= prev-props listener-history)
        ;;(js/console.log (om/get-state owner :listener-chart)) 
        (.remove (.-firstChild (om/get-node owner listener-node-name)))
        (line-graph listener-history)))
    ;; does not work
    ;; (.updateOptions (om/get-state owner :listener-chart) (clj->js {:file (utils/mangle-dygraph* listener-history)}))))
    om/IRender
    (render [this]
      (dom/div #js {:react-key listener-node-name 
                    :ref listener-node-name       
                    :id listener-node-name}))))




(defn main-view
  [{:keys [playing chat]} owner]
  (reify
    om/IRenderState
    (render-state [_ s]
      (dom/div #js {:id "annoying-placeholder"} ;; annoying               
               (dom/div #js {:className "row"} 
                        (om/build playing-view playing))
               (dom/div #js {:className "row"} 
                        (dom/div #js {:className "col-md-2"}
                                 (om/build listeners-view playing))
                        (dom/div #js {:className "col-md-8"}
                                 (om/build line-chart playing)))
               (dom/div #js {:className "row"}
                        (om/build chat-users chat))
               (dom/div #js {:className "row"}
                        (dom/div  #js {:className "col-md-8"}
                                  (om/build chat-view chat)))
               ))))



(om/root
 main-view
 app-state
 {:target (. js/document (getElementById "content"))})



(comment
  ;; TODO: compile only in dev mode?
  (om/root
   ankha/inspector
   app-state
   {:target (js/document.getElementById "inspect")})
  )




;; TODO: auto-reconnect
;; TODO: conditionally compile this only if in dev mode
(defn ^:export connect
  []
  (ws-repl/connect "ws://localhost:9001" :verbose false))




;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(comment

  (do
    (swap! app-state assoc-in  [:chat :timeout] 120000)
    (swap! app-state assoc-in  [:playing :timeout] 1000))

  
  (-> @app-state :playing :listener-history utils/mangle-dygraph*)

  (vec '(1 2 3))

  (js/console.log (clj->js {:file  (-> @app-state :playing :listener-history utils/mangle-dygraph*)}))
  
  )