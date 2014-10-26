(ns djdash.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [om.core :as om :include-macros true]
            [weasel.repl :as ws-repl]
            [goog.style :as gstyle]
            [ankha.core :as ankha]
            [goog.storage.mechanism.mechanismfactory]
            [cljs-http.client :as http]
            [djdash.utils :as utils]
            [clojure.walk :as walk]
            [clojure.browser.dom :as ddom]
            [om-tools.core :refer-macros [defcomponent]]
            [om-tools.dom :as dom :include-macros true]
            [cljs.core.async :as async :refer [put! chan <!]])
  (:import [goog.net Jsonp]
           [goog Uri]))


(comment
  ;; don't want this with nrepl, i guess?
  (enable-console-print!)
  )


(def storage (goog.storage.mechanism.mechanismfactory.create))

(def strs {:checking "Checking..."})

(def app-state (atom {:playing {:playing (:checking strs)
                                :listeners (:checking strs)
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
                             :lines ""}
                      }))


;; terrible old js-style action-at-a-distance, but i couldn't get async to work right
(defn jsonp-playing
  [uri]
  (utils/jsonp-wrap uri (fn [res]
                          (swap! app-state update-in  [:playing] #(merge % (utils/un-json res))))))



(defcomponent playing-view
  [{:keys [playing listeners url timeout]} owner]
  (will-mount
   [_]
   (let [c (chan)]
     (go (while true
           (jsonp-playing url)
           (<! (async/timeout timeout))))))
  (render-state
   [_ s]
   (dom/div
    (dom/div {:id "playing"}
             (dom/div "Now Playing:")
             (dom/div  playing ))
    (dom/div {:id "listeners"}
             (dom/div "Listeners:")
             (dom/div listeners)))))





(defn process-chat
  [{:keys [lines] :as new}]
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

(defcomponent chat-view
  [{:keys [users messages user url errors count id timeout] :as curs} owner]
  (will-mount
   [_]
   (let [c (chan)]
     (go (while true
           ;; TODO: the message to send!
           (update-chat! "")
           (<! (async/timeout timeout))))))
  (did-mount
   [_]
   (swap! app-state assoc-in [:chat :user]  (.get storage :user))
   (when (-> @app-state :chat :user empty?)
     (login)))
  (render-state
   [_ s]
   (dom/div {:id "chat"}
            (dom/div {:id "users"
                      :dangerously-set-inner-HTML  {:__html users}})
            (dom/div {:id "messages"
                      :dangerously-set-inner-HTML  {:__html (apply str (interpose "\n" messages))}})
            (if (empty? user)
              (dom/button {:on-click (fn [_] (login))} "Log In")
              (dom/div
               (str user ": ")
               (dom/input
                {:id "chatinput"
                 ;;:on-change #(js/console.log %)
                 :on-key-down #(when (= (.-key %) "Enter")
                                 (om/update! curs :message (.. % -target -value))
                                 (ddom/set-value (.. % -target)  "")
                                 )})
               )))))

;;

(defn main-view
  [{:keys [playing chat]} owner]
  (reify
    om/IRenderState
    (render-state [_ s]
      (dom/div nil
               (om/build playing-view playing)
               (om/build chat-view chat)
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



  )