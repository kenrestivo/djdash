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
(defn login
  []
  (let [u (js/prompt "who are you, dj person?")]
    (when (not (empty? u))
      (swap! state/app-state (fn [o]
                               (.set state/storage :user u)
                               (assoc-in o [:chat :user] u ))))))


(defn next-up
  [{:keys [name start_timestamp end_timestamp]}]
  [:li {:class "upnext"
        :key start_timestamp}
   (utils/format-schedule-item name start_timestamp end_timestamp)])

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
  (let [{:keys [users]} (-> @state/app-state :chat)]
    [:div  {:id "user-section"}
     [:ul  {:class "list-inline"}
      (cons [:li  {:class "text-label" :key "in-chat-now"} "In Chat Now:"]
            (for [u (utils/hack-users-list users)]
              [:li  {:class "label label-default paddy"
                     :key u ;;; XXX baaaad, not unique?
                     ;; TODO: why am i setting the html anyway?
                     :dangerouslySetInnerHTML   {:__html u}}]))]]))


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
                      :placeholder title
                      :id "chatinput"
                      :on-change #(reset! val (-> % .-target .-value))
                      :on-key-down #(case (.-which %)
                                      13 (save)
                                      27 (stop)
                                      nil)})])))


(defn send-chat!
  [v]
  (swap! state/app-state assoc-in [:chat :message] v))

(defn chat-view
  []
  (reagent/create-class 
   {:display-name "chat-view"
    :reagent-render (fn []
                      (let [{:keys [user messages]} (-> @state/app-state :chat)]
                        [:div {:id "chat"}
                         (if (empty? user)
                           [:button  {:onClick (fn [_] (login))} "Log In"]
                           [:div 
                            [:label {:htmlFor "chatinput"
                                     :class "handle"}
                             (str user ": ")]
                            [chat-input {:title "Say something here." 
                                         :on-save send-chat!}]
                            ])
                         [:div  {:id "messages"
                                 :dangerouslySetInnerHTML   {:__html (utils/hack-list-group messages)}}]]))
    
    :component-will-mount (fn [this]
                            (go (while true
                                  ;; TODO: the message to send!
                                  (trace "updating chat")
                                  (comms/update-chat!)
                                  (<! (async/timeout (-> @state/app-state :chat :timeout))))))
    :component-did-mount (fn [this]
                           (swap! state/app-state assoc-in [:chat :user]  (.get state/storage :user))
                           (when (-> @state/app-state :chat :user empty?)
                             (login)))}))



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
     {:display-name "geo-map"
      :reagent-render (fn []
                        [:div
                         [:div {:class "text-label"} "Listeners Map"]
                         [:div {:key node-name
                                :ref node-name       
                                :id node-name}]])
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
     [geo-map]
     [scheduled-now-view]
     [schedule-view]
     [chat-users]
     [chat-view]
     ]]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(comment


  (ns djdash.views)

  (send-chat "nothing" "hey yoou")


  
  )
