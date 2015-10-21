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
            [cljs-time.core :as time]
            [djdash.utils :as utils]
            [om.dom :as dom]
            [goog.string :as gstring]
            [cljs-time.coerce :as coerce]
            [cljs-time.format :as tformat]
            [clojure.walk :as walk]
            [clojure.browser.dom :as ddom]
            [cljs.core.async :as async :refer [put! chan <!]])
  (:import [goog.net Jsonp]
           
           [goog Uri]))


(taoensso.timbre/set-level! :info)


(comment
  ;; don't want this with nrepl, i guess?
  (enable-console-print!)
  )

(def ^:export jq js/jQuery)

(def ^:export L js/L)


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
                                :live? false
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
                      :geo {:node-name "listener-map"
                            :url "http://otile{s}.mqcdn.com/tiles/1.0.0/osm/{z}/{x}/{y}.png"
                            :options {:subdomains "1234",
                                      :attribution "&copy; <a href='http://www.openstreetmap.org/'>OpenStreetMap</a> <a href='http://www.openstreetmap.org/copyright' title='ODbL'>open license</a>. <a href='http://www.mapquest.com/'>MapQuest</a> <img src='http://developer.mapquest.com/content/osm/mq_logo.png'>"}
                            :connections {}}
                      :schedule {:data {}
                                 :timeout 30000
                                 :now "Checking..."}
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



(defn live?
  [playing-text]
  (->> playing-text
       (re-find  #"^\[LIVE\!\].*?")
       boolean))

(defn on-air-div
  [live? playing-text]
  (dom/div #js {:id "on_air"
                :className (if live? "label label-danger paddy" "hidden")}
           "LIVE!"))


(defn listeners-view
  [{:keys [listeners timeout] :as state} owner]
  (reify
    om/IDidMount
    (did-mount [_]
      (info timeout)
      (go (while true
            (debug "updating listeners")
            (swap! app-state update-listeners)
            (<! (async/timeout timeout)))))
    om/IRenderState
    (render-state
      [_ s]
      (dom/div #js {:id "listeners"}
               (dom/span #js {:className "text-label"}
                         "Listeners:")
               (dom/span nil listeners)))))


(defn format-schedule-item
  [name start_timestamp end_timestamp]
  (str (short-weekday start_timestamp) " "
       (format-time start_timestamp) " - " (format-time end_timestamp) "   " name ))

(defn next-up
  [{:keys [name start_timestamp end_timestamp]} owner]
  (reify
    om/IRenderState
    (render-state [this _]
      (dom/li #js {:className "upnext"}
              (format-schedule-item name start_timestamp end_timestamp)))))


(defn get-currently-scheduled
  [{:keys [name start_timestamp end_timestamp]}]
  (let [now  (cljs-time.core/now)]
    (if (and start_timestamp end_timestamp
             (cljs-time.core/after?   now start_timestamp)
             (cljs-time.core/before?  now  end_timestamp))
      (format-schedule-item name start_timestamp end_timestamp)
      "Nobody (Random Archives)")))



(defn update-scheduled-now
  [old-app-state]
  (assoc-in old-app-state [:schedule :now]
            (-> old-app-state :schedule :data :current last get-currently-scheduled)))


(defn scheduled-now-view
  [{:keys [data timeout now]}  owner]
  (reify
    om/IWillMount
    (will-mount
      [_]
      (go (while true
            (swap! app-state update-scheduled-now)
            (<! (async/timeout timeout)))))
    om/IRenderState
    (render-state
      [_ s]
      (dom/ul #js {:className "upnext"}
              (dom/li #js {:className "upnext"}
                      now)))))


(defn schedule-view
  [{{:keys [future]} :data} owner]
  (reify
    om/IRenderState
    (render-state
      [_ s]
      (dom/div #js {:className "upnext"}
               (if (< 0 (count future))
                 (apply dom/ul nil
                        (om/build-all next-up (take 3 future)))
                 "Checking...")))))


(defn playing-view
  [{:keys [playing listeners live?  timeout]} owner]
  (reify
    om/IRenderState
    (render-state
      [_ s]
      (dom/div #js {:id "playing"}
               (dom/span #js {:className "text-label"}
                         "Now Playing:")
               (dom/span  nil
                          (on-air-div live? playing)
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
      (go (while true
            ;; TODO: the message to send!
            (update-chat! "")
            (<! (async/timeout timeout)))))
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




(defn geo-map
  "node-name is the name for the DOM node of the map"
  [{:keys [node-name connections options url]} owner]
  (reify
    om/IInitState
    (init-state [_]
      {:geo-map nil
       :markers {}})
    om/IDidMount
    (did-mount [this]
      (let [m (-> L
                  (.map node-name)
                  (.setView (clj->js [30, 0]) 1))]
        (-> L
            (.tileLayer  url (clj->js options))
            (.addTo m))
        (om/set-state! owner :geo-map m)))
    om/IDidUpdate
    (did-update [this prev-props {:keys [geo-map markers] :as prev-state}]
      (let [old-conns (:connections prev-props)]
        (when (not=  old-conns connections)
          ;;(println "changed" old-conns "--->" connections)
          (let [new-connections (into {} (select-keys connections (apply disj (-> connections keys set)
                                                                         (keys old-conns))))
                new-markers (into {}
                                  (for [[k {:keys [lat lng city region country]}] new-connections]
                                    [k (-> L
                                           (.marker  (L.latLng lat lng))
                                           (.addTo geo-map)
                                           (.bindPopup (goog.string.format "<b>%s</b><br />%s, %s"
                                                                           city region country)))]))
                dead-marker-keys (apply disj (-> old-conns keys set) (keys connections))]
            ;;(println "new markers" new-markers)
            ;;(js/console.log new-markers)
            ;; nuke the old ones
            ;;(println "dead conns" dead-marker-keys)
            (doseq [k dead-marker-keys]
              (.removeLayer geo-map (get markers k)))
            (om/update-state! owner :markers (fn [oms]
                                               (merge (apply dissoc oms dead-marker-keys)
                                                      new-markers)))))))
    om/IRender
    (render [this]
      (dom/div #js {:react-key node-name
                    :ref node-name       
                    :id node-name}))))



(defn main-view
  [{:keys [playing buffer chat schedule geo]} owner]
  (reify
    om/IWillMount
    (will-mount [_]
      (let [rfn #(do
                   (debug "sending chsk from anon fn")
                   (chsk-send! [:djdash/now-playing {:cmd :refresh}])
                   (chsk-send! [:djdash/geo {:cmd :refresh}])
                   (chsk-send! [:djdash/schedule {:cmd :refresh}]))]
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
                                 "DJ Connection Quality")
                        (dom/div #js {:className "col-md-8"}
                                 (om/build flot buffer)))
               (dom/div #js {:className "row"}
                        (dom/div #js {:className "col-md-2 text-label"}
                                 "Listeners Map")
                        (dom/div #js {:className "col-md-8"}
                                 (om/build geo-map geo)))
               (dom/div #js {:className "row"}
                        (dom/div #js {:className "col-md-2"}
                                 (dom/div #js {:className "text-label"}
                                          "Who's scheduled now?"))
                        (dom/div #js {:className "col-md-8"}
                                 (om/build scheduled-now-view schedule)))
               (dom/div #js {:className "row"}
                        (dom/div #js {:className "col-md-2"}
                                 (dom/div #js {:className "text-label"}
                                          "Who's up?")
                                 (dom/div nil
                                          "("(dom/a #js {:href "http://radio.spaz.org/spazradio.ics"}
                                                    "Weekly Calendar")")"))
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
    :djdash/new-geos (swap! app-state (fn [o] (assoc-in o [:geo :connections] msg)))
    :djdash/now-playing (swap! app-state (fn [o]
                                           (let [{:keys [listeners playing] :as new-data} msg]
                                             (-> o
                                                 (update-in [:playing] merge
                                                            (select-keys new-data [:listeners :playing]))
                                                 (assoc-in  [:playing :live?] (live? playing))
                                                 (update-in  [:playing :data 0] conj [(js/Date.now)
                                                                                      listeners])))))
    :djdash/next-shows (swap! app-state (fn [o]
                                          (-> o
                                              (assoc-in [:schedule :data] msg)
                                              update-scheduled-now)))
    (error "unknown message type")))



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


  (chsk-send! [:djdash/schedule {:cmd :refresh}])
  
  (chsk-send! [:djdash/now-playing {:cmd :refresh}])

  (println @chsk-state)

  
  ;; do this on a timer, with a go loop, every minute maybe?
  ;; create a view for it.

  (-> @app-state :schedule :data :current last get-currently-scheduled)

  (-> @app-state :schedule :now)
  

  (println @app-state)
  (->> @app-state :playing :playing)

  (dispatch-message  [:djdash/now-playing {:playing "[LIVE!] some test"
                                           :listeners 1}])


  (-> @app-state :geo :connections)

  (-> @app-state :schedule :data :current last get-currently-scheduled)

  (swap! app-state update-scheduled-now)

  (-> @app-state :schedule :now)


  (println @app-state)
  
  )

