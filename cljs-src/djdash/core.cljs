(ns djdash.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [om.core :as om :include-macros true]
            [weasel.repl :as ws-repl]
            [taoensso.timbre :as log
             :refer-macros (log  trace  debug  info  warn  error  fatal  report
                                 logf tracef debugf infof warnf errorf fatalf reportf
                                 spy get-env log-env)]
            [taoensso.sente  :as sente :refer (cb-success?)]
            [goog.style :as gstyle]
            [goog.storage.mechanism.mechanismfactory]
            ;; [ankha.core :as ankha] ;; breaks everything :-(
            [cljs-http.client :as http]
            [djdash.utils :as utils]
            [om.dom :as dom]
            [cljs-time.coerce :as coerce]
            [cljs-time.format :as tformat]
            [clojure.walk :as walk]
            [clojure.browser.dom :as ddom]
            [cljs.core.async :as async :refer [put! chan <!]])
  (:import [goog.net Jsonp]
           [goog Uri]))



(comment
  ;; don't want this with nrepl, i guess?
  (enable-console-print!)
  )

(def ^:export jq js/jQuery)

(def storage (goog.storage.mechanism.mechanismfactory.create))

(def strs {:checking "Checking..."})

(defn format-time
  [d]
  (cljs-time.format/unparse
   (cljs-time.format/formatter "h:mma")
   (goog.date.DateTime.  d)))


(defn short-weekday
  [d]
  (.toLocaleString d js/window.navigator.language #js {"weekday" "short"}))

(defn min-chat-stamp
  []
  (-> (js/Date.)
      .getTime
      (/ 1000)
      (- (* 60 60 24 30))
      Math/floor))

(defn buffer-tick
  [n axis]
  (str (-> n int (/ 1000)) "k"))


(defn update-listeners
  [state]
  (update-in state [:playing :data 0] conj [(js/Date.now) (-> state :playing :listeners)]))

(def app-state (atom {:playing {:playing (:checking strs)
                                :listeners (:checking strs)
                                :data [[]] ;; important to have that empty first series
                                :timeout 60000
                                :node-name "listener-chart"
                                :chart-options {:xaxis {:mode "time"
                                                        :timezone "browser"
                                                        :ticks 6
                                                        :minTickSize [2, "minute"]
                                                        :timeformat "%I:%M%p"}
                                                :yaxis {:min 0
                                                        :minTickSize 1
                                                        :tickFormatter (comp str int)
                                                        :color 1}}}
                      :schedule {:data []}
                      :buffer {:node-name "buffer-chart"
                               :data [[]] ;; important to have that empty first series
                               :chart-options {:xaxis {:mode "time"
                                                       :ticks 6
                                                       :minTickSize [2, "minute"]
                                                       :timezone "browser"
                                                       :timeformat "%I:%M%p"}
                                               :yaxis {:min 0
                                                       :color 2
                                                       :tickFormatter buffer-tick}}}
                      :chat {:url js/chat_url
                             :count (min-chat-stamp)
                             :user ""
                             :users (:checking strs)
                             :id (int (rand 999999))
                             :errors ""
                             :message ""
                             :timeout 2000
                             :messages []
                             :lines ""}}))



(let [{:keys [chsk ch-recv send-fn state]} (sente/make-channel-socket! "/ch"  {:type :auto })]
  (def chsk       chsk)
  (def ch-chsk    ch-recv) 
  (def chsk-send! send-fn) 
  (def chsk-state state))



;; terrible old js-style action-at-a-distance, but i couldn't get async to work right
(defn jsonp-playing
  [uri]
  (utils/jsonp-wrap uri (fn [res]
                          (swap! app-state (fn [s]
                                             (let [{:keys [listeners] :as new-data} (utils/un-json res)]
                                               (-> s
                                                   (update-in  [:playing] merge  new-data)
                                                   update-listeners)))))))

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
  [{:keys [listeners timeout] :as state} owner]
  (reify
    om/IDidMount
    (did-mount [_]
      (info timeout)
      (go (while true
            (info "updating listeners")
            (swap! app-state update-listeners)
            (<! (async/timeout timeout)))))
    om/IRenderState
    (render-state
      [_ s]
      (dom/div #js {:id "listeners"}
               (dom/span #js {:className "text-label"}
                         "Listeners:")
               (dom/span nil listeners)))))


(defn next-up
  [{:keys [name start_timestamp end_timestamp]} owner]
  (reify
    om/IRenderState
    (render-state [this _]
      (dom/li #js {:className "upnext"}
              (str (short-weekday start_timestamp) " "
                   (format-time start_timestamp) " - " (format-time end_timestamp) "   " name )))))


(defn schedule-view
  [{:keys [data]} owner]
  (reify
    om/IRenderState
    (render-state
      [_ s]
      (dom/div #js {:className "upnext"} 
               (if (< 0 (count data))
                 (apply dom/ul nil
                        (om/build-all next-up (take 3 data)))
                 "Checking...")))))


(defn playing-view
  [{:keys [playing listeners  timeout]} owner]
  (reify
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
                              (dom/li #js {:className "label label-default paddy"
                                           :dangerouslySetInnerHTML  #js {:__html u}}))))))))



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




(defn flot
  "node-name is the name for the DOM node of the flot chart
   chart-options is a clojure nested map of options for flot (see flot docs)
   data is the data in flot's format (nested vectors or vector of maps)"
  [{:keys [node-name chart-options data]} owner]
  (reify
    om/IInitState
    (init-state [_]
      {:flot nil})
    om/IDidMount
    (did-mount [this]
      (let [g (.plot jq  (js/document.getElementById node-name)
                     (clj->js data)
                     (clj->js chart-options))]
        (om/set-state! owner :flot g)))
    om/IDidUpdate
    (did-update [this prev-props {:keys [flot] :as prev-state}]
      (when (not= (:data prev-props) data)
        ;;(js/console.log (clj->js data))
        (doto flot
          (.setData (clj->js data))
          .setupGrid
          .draw)))
    om/IRender
    (render [this]
      (dom/div #js {:react-key node-name
                    :ref node-name       
                    :id node-name}))))



(defn main-view
  [{:keys [playing buffer chat schedule]} owner]
  (reify
    om/IWillMount
    (will-mount [_]
      (let [rfn #(do
                   (debug "sending chsk from anon fn")
                   (debug (chsk-send! [:djdash/now-playing {:cmd :refresh}]))
                   (debug (chsk-send! [:djdash/schedule {:cmd :refresh}])))]
        (when (:open? @chsk-state)
          (debug "sending chsk sched early, at mount")
          (rfn))
        (add-watch chsk-state :djdash/login-updates (fn [_ _ o n]
                                                      (when (and (not (:open? o)) (:open? n))
                                                        (debug "callback chsk sched send")
                                                        (rfn))))))
    om/IRenderState
    (render-state [_ s]
      (dom/div #js {:id "annoying-placeholder"} ;; annoying               
               (dom/div #js {:className "row"} 
                        (om/build playing-view playing))
               (dom/div #js {:className "row"} 
                        (dom/div #js {:className "col-md-2"}
                                 (om/build listeners-view playing))
                        (dom/div #js {:className "col-md-8"}
                                 (om/build flot playing)))
               (dom/div #js {:className "row"}
                        (dom/div #js {:className "col-md-2 text-label"}
                                 "Buffer Status")
                        (dom/div #js {:className "col-md-8"}
                                 (om/build flot buffer)))
               (dom/div #js {:className "row"}
                        (dom/div #js {:className "col-md-2 text-label"}
                                 "Who's up?")
                        (dom/div #js {:className "col-md-8"}
                                 (om/build schedule-view schedule)))
               (dom/div #js {:className "row"}
                        (om/build chat-users chat))
               (dom/div #js {:className "row"}
                        (dom/div  #js {:className "col-md-8"}
                                  (om/build chat-view chat)))
               ))))

;;  

(om.core/root
 main-view
 app-state
 {:target (. js/document (getElementById "content"))})


(defn format-buffer
  [{:keys [min max avg date]}]
  [date min])



(defn dispatch-message
  [[id msg]]
  (case id
    :djdash/buffer (swap! app-state update-in [:buffer :data 0] conj  (format-buffer msg))
    :djdash/now-playing (swap! app-state (fn [o]
                                           (let [{:keys [listeners] :as new-data} msg]
                                             (-> o
                                                 (update-in  [:playing] merge  new-data)
                                                 (update-in  [:playing :data 0] conj [(js/Date.now)
                                                                                      listeners])))))
    :djdash/next-shows (swap! app-state assoc-in [:schedule :data] msg)
    (js/console.log "unknown message type")))



(defn dispatch-event
  [{:keys [?data id]}]
  (when (= :chsk/recv id)
    (dispatch-message ?data)))



;; this actually starts the routing going
(def event-router (sente/start-chsk-router! ch-chsk dispatch-event))


;; TODO: auto-reconnect
;; TODO: conditionally compile this only if in dev mode
(defn replconnect
  []
  (ws-repl/connect "ws://localhost:9001"))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(comment
  ;; breaks everything :-(
  ;; a hacky conditional run, if not compile
  (when-let [target (js/document.getElementById "inspect")]
    (om/root
     ankha/inspector
     app-state
     {:target target}))
  )

(comment

  (do
    (swap! app-state assoc-in  [:chat :timeout] 120000)
    (swap! app-state assoc-in  [:playing :timeout] 60000))

  
  (format-time #inst "2015-09-29T03:00:00.000-00:00")

  (-> @app-state :schedule :data)


  (apply str (for [{:keys [name start_timestamp end_timestamp]} data]
               [name (format-time start_timestamp) (format-time end_timestamp)]))

  (timbre/set-level! :trace)

  (taoensso.timbre/set-level! :debug)
  
  (taoensso.timbre/info "test")

  (chsk-send! [:djdash/testsend {:testkey "testdata"}])

  (chsk-send! [:djdash/schedule {:cmd :refresh}])
  
  (chsk-send! [:djdash/now-playing {:cmd :refresh}])

  (println @chsk-state)
  

  
  )

