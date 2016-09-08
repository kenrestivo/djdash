(ns djdash.views
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require  [taoensso.timbre :as log
              :refer-macros (log  trace  debug  info  warn  error  fatal  report
                                  logf tracef debugf infof warnf errorf fatalf reportf
                                  spy get-env log-env)]
             [taoensso.sente  :as sente :refer (cb-success?)]
             [goog.style :as gstyle]
             [reagent.core :as reagent :refer [atom]]
             [reagent.session :as session]
             [cljs-http.client :as http]
             [cljs-time.core :as time]
             [djdash.utils :as utils]
             [djdash.state :as state]
             [djdash.comms :as comms]
             [goog.string :as gstring]
             [cljs-time.coerce :as coerce]
             [cljs-time.format :as tformat]
             [clojure.walk :as walk]
             [cljs.core.async :as async :refer [put! chan <!]])
  (:import [goog.net Jsonp]
           [goog Uri]))

(def ^:export jq js/jQuery)


(def window-width (reagent/atom nil))

;; ugly but works
(defn on-window-resize [ evt ]
  (doseq [f [(-> @state/app-state :playing :flot)
             (-> @state/app-state :buffer :flot)]]
    (.resize f)
    (.setupGrid f)
    (.draw f))
  (reset! window-width (.-innerWidth js/window)))




;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; views

(defn on-air-div
  [live? playing-text]
  [:span {:class (if live? "label label-danger paddy" "hidden")}
   "LIVE!"])

;; TODO: use modal
(defn login-user
  []
  (let [u (js/prompt "who are you, dj person?")]
    (when (empty? u)
      (login-user)) ;; no longer allow anon
    (swap! state/app-state (fn [o]
                             (.set state/storage :user u)
                             ;; TODO: maybe log them in, but have to wait until the connection happens?
                             (assoc-in o [:chat :user] u )))))



(defn next-up
  [{:keys [name start_timestamp end_timestamp]}]
  (when (and start_timestamp end_timestamp name)
    [:li {:class "upnext"
          :key start_timestamp}
     (utils/format-schedule-item name start_timestamp end_timestamp)]))

(defn schedule-view
  []
  (let [{:keys [future]} (-> @state/app-state :schedule :data)]
    [:div
     [:div {:class "text-label"} "Who's up?"]
     [:div {:class "upnext"}
      (if (< 0 (count future))
        [:ul 
         (for [n (take 3 future)]
           (next-up n))]
        "Checking...")]]))


(defn listeners-view 
  []
  (reagent/create-class 
   {:display-name "listeners-view"
    :reagent-render (fn []
                      [:div {:id "listeners"}
                       [:span {:class "text-label"} "Listeners:"]
                       [:span (-> @state/app-state :playing :listeners)]])
    :component-did-mount (fn [this]
                           (go (while true
                                 (debug "updating listeners")
                                 (swap! state/app-state comms/update-listeners)
                                 (<! (async/timeout (-> @state/app-state :playing :timeout))))))}))


(defn scheduled-now-view
  []
  (reagent/create-class 
   {:display-name "scheduled-now-view"
    :reagent-render (fn []
                      [:div
                       [:div {:class "text-label"} "Who's scheduled now?"]
                       [:ul {:class "upnext"}
                        [:li {:class "upnext"}
                         (-> @state/app-state :schedule :now)]]])

    :component-did-mount (fn [this]
                           (go (while true
                                 (debug "updating scheduled now")
                                 (swap! state/app-state comms/update-scheduled-now)
                                 (<! (async/timeout (-> @state/app-state :schedule :timeout))))))}))



(defn playing-view
  []
  (let [{:keys [playing listeners live?  timeout]} (-> @state/app-state :playing)]
    [:div 
     [:span  {:class "text-label"} "Now Playing:"]
     [:span (on-air-div live? playing) playing]]))


(defn chat-users
  []
  (let [{:keys [users connected?]} (-> @state/app-state :chat)]
    [:div  {:id "user-section"}
     [:ul  {:class "list-inline"}
      ;; TODO: turn the text red or put an LED there to indicate chat is connected
      (cons [:li  {:class (when connected? "text-label") 
                   :key "in-chat-now"} 
             "In Chat Now:"]
            (for [u users]
              [:li  {:class "label label-default paddy"
                     :key u ;;; XXX baaaad, not unique?
                     } u]))]]))


;; lifted from todomvc
(defn chat-input [{:keys [title on-save on-stop]}]
  (let [val (reagent/atom "")
        stop #(do (reset! val "")
                  (if on-stop (on-stop)))
        save #(let [v (-> @val str clojure.string/trim)]
                (if-not (empty? v) (on-save v))
                (stop))]
    (fn [props]
      [:input (merge props
                     {:type "text" 
                      :value @val 
                      :disabled (-> @state/app-state :chat :connected? not)
                      :placeholder title
                      :id "chatinput"
                      :on-change #(reset! val (-> % .-target .-value))
                      :on-key-down #(case (.-which %)
                                      13 (save)
                                      27 (stop)
                                      nil)})])))

(defn messages-view
  [msgs]
  (try
    (when (< 0 (count msgs))
      [:div {:id "messages"}
       [:ul {:class "list-group"}
        ;; this contrived index will work for the moment. timestamp, maybe, but that might be tricky
        (for [{:keys [user message idx]}  (map-indexed #(assoc %2 :idx %1) msgs)]
          [:li {:class "list-group-item"
                :key  (str "msg" idx)}
           [:span {:class "handle"} (str user ": ")]
           [:span {:class "message"} message]])]])
    (catch :default e
      (error e))))



(defn chat-view
  []
  (reagent/create-class 
   {:display-name "chat-view"
    ;; TODO: add a component-did-update for messages, and re-enable the send box
    :reagent-render (fn []
                      (let [{:keys [user messages sendMessage]} (-> @state/app-state :chat)]
                        [:div {:id "chat"}
                         (if (empty? user)
                           [:button  {:onClick (fn [_] (login-user))} "Log In"]
                           [:div 
                            [:label {:htmlFor "chatinput"
                                     :class "handle"}
                             (str user ": ")]
                            [chat-input {:title "Say something here." 
                                         ;; TODO: also spinner on the input and disable it.
                                         :on-save sendMessage}]
                            ])
                         [messages-view messages]]))
    :component-did-mount (fn [this]
                           (swap! state/app-state assoc-in [:chat :user]  (.get state/storage :user))
                           (comms/start-chat)
                           (let [{:keys [user login]} (:chat @state/app-state)]
                             (when (empty? user)
                               (login-user)))
                           )}))


;; TODO: move this to :component-did-update
(defn update-map!
  [_ _ old-state new-state]
  (try
    (let [old-markers (-> old-state :geo :markers)
          {:keys [geo-map markers]} (-> new-state :geo)]
      (when (not= old-markers markers)
        (debug old-markers)
        (debug markers)
        (doseq [k (utils/changed-keys old-markers markers)]
          (debug "adding" k)
          (.addTo (get markers k) geo-map))
        (doseq [k (utils/changed-keys markers old-markers)]
          (debug "removing" k)
          (.removeLayer geo-map (get old-markers k)))))
    (catch :default e
      (error e))))



(defn geo-map
  []
  (let [{:keys [node-name options url]} (@state/app-state :geo)]
    (reagent/create-class 
     {:display-name node-name
      :component-did-update (fn [this x y]
                              (log/info "foo"))
      :reagent-render (fn []
                        [:div
                         [:div {:class "text-label"} (-> @state/app-state :geo :text-label)]
                         [:div {:key node-name
                                :ref node-name       
                                :id node-name}]])
      ;; TODO: put the pins on a layer, on will-mount, delete from that with .clearLayers
      :component-did-mount (fn [this]
                             (let [m (-> utils/L
                                         (.map node-name)
                                         (.setView (clj->js [30, 0]) 1))]
                               (-> utils/L
                                   (.tileLayer  url (clj->js options))
                                   (.addTo m))
                               (swap! state/app-state assoc-in [:geo :geo-map] m)
                               (add-watch state/app-state :djdash/update-map update-map!)
                               ))})))

(defn text-map
  [conns]
  (try
    (when (< 0 (count conns))
      [:div {:id "conns"}
       [:ul {:class "list-group"}
        ;; this contrived index will work for the moment. timestamp, maybe, but that might be tricky
        (for [[idx {:keys [city region]}]  conns]
          [:li {:class "list-group-item"
                :key  (str "msg" idx)}
           [:span {:class "message"} (str city ", " region)]])]])
    (catch :default e
      (error e))))


;; TODO: move this to :component-did-update
(defn update-flot-fn
  [k]
  (fn [_ _ prev-state new-state]
    (try
      (let [old-data (-> prev-state k :data)
            new-data(-> new-state k :data)]
        (when (not= new-data old-data)
          ;;(js/console.log (clj->js data))
          (doto (-> new-state k :flot)
            (.setData (clj->js new-data))
            .setupGrid
            .draw)))
      (catch :default  e
        (log/error e)))))

(defn flot
  "node-name is the name for the DOM node of the flot chart
   chart-options is a clojure nested map of options for flot (see flot docs)
   data is the data in flot's format (nested vectors or vector of maps)"
  [{:keys [node-name chart-options data]} k]
  (reagent/create-class 
   {:component-did-mount (fn [this]
                           (let [g (.plot jq  (js/document.getElementById node-name)
                                          (clj->js data)
                                          (clj->js chart-options))]
                             (swap! state/app-state assoc-in [k :flot] g))
                           ;; TODO: namespace the addwatch
                           (add-watch state/app-state k (update-flot-fn k)))
    :reagent-render (fn [this]
                      [:div {:key node-name
                             :ref node-name       
                             :id node-name}])}))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn home-page 
  []
  [:div  {:class "container-fluid"}
   [:div  {:class "row"} 
    ;; left column
    [:div  {:class "col-md-6"} 
     [:div {:class "to-top"}
      [:h2   "SPAZ Radio DJ Dashboard"]]
     [playing-view]
     [:div 
      [listeners-view]
      [flot (:playing @state/app-state) :playing]]
     [:div 
      [:div {:class "text-label"} "DJ Connection Quality"]
      [flot (:buffer @state/app-state) :buffer]]]

    ;; right column
    [:div  {:class "col-md-6"} 
     [:div
      [:div {:class "text-label"} "Listeners Locations"]
      [text-map (-> @state/app-state :geo :connections)]]
     [scheduled-now-view]
     [schedule-view]
     [chat-users]
     [chat-view]
     ]]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(comment


  (ns djdash.views)

  (-> @state/app-state :chat :users)

  (-> @state/app-state :chat :messages)

  (swap! state/app-state assoc-in [:chat :messages] [])

  (-> @state/app-state :chat :connected?)

  ((-> @state/app-state :chat :sendMessage) (str "hey " (-> (js/Date.) .toString)))


  ((-> @state/app-state :chat :login) "nobody")

  (login-user)

  ((-> @state/app-state :chat :login) (-> @state/app-state :chat :user))

  
  (messages-view (-> @state/app-state :chat :messages))

  (->> @state/app-state :chat :messages
       (map-indexed #(assoc %2 :idx %1)))

  (->> @state/app-state :chat :messages first)

  ;; NO, will need to use layergroup
  (some-> @state/app-state :geo :geo-map .-_layers)

  (some-> @state/app-state :geo)

  )
