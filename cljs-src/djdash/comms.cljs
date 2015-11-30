(ns djdash.comms
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
             [goog.string :as gstring]
             [cljs-time.coerce :as coerce]
             [cljs-time.format :as tformat]
             [clojure.walk :as walk]
             [cljs.core.async :as async :refer [put! chan <!]])
  (:import [goog.net Jsonp]
           [goog Uri]))






(defn update-listeners
  [state]
  ;; TODO: only update if it's not checking...!
  (update-in state [:playing :data 0] conj [(js/Date.now) (-> state :playing :listeners)]))



(let [{:keys [chsk ch-recv send-fn state]} (sente/make-channel-socket! "/ch"  {:type :auto })]
  (def chsk       chsk)
  (def ch-chsk    ch-recv) 
  (def chsk-send! send-fn) 
  (def chsk-state state))




;;; TODO: Move with below?
(defn get-currently-scheduled
  [{:keys [name start_timestamp end_timestamp]}]
  (let [now  (cljs-time.core/now)]
    (if (and start_timestamp end_timestamp
             (cljs-time.core/after?   now start_timestamp)
             (cljs-time.core/before?  now  end_timestamp))
      (utils/format-schedule-item name start_timestamp end_timestamp)
      "Nobody (Random Archives)")))

;;; TODO: move?
(defn update-scheduled-now
  [old-app-state]
  (assoc-in old-app-state [:schedule :now]
            (-> old-app-state :schedule :data :current last get-currently-scheduled)))


(defn process-chat
  [{:keys [lines users] :as new}]
  (swap! state/app-state update-in [:chat]
         (fn [{:keys [messages] :as old}]
           (merge old new {:messages (concat (utils/reverse-split lines)
                                             messages)}))))





(defn update-chat!
  []
  (let [{{:keys [url user count id message]}  :chat} @state/app-state
        u (str url "?" (http/generate-query-string {:user user
                                                    :msg message
                                                    :lineNo count
                                                    :chatId id}))]
    (utils/jsonp-wrap  u
                       (if (empty? message)
                         ;; hack to avoid double-messages
                         #(-> % utils/un-json process-chat)
                         identity))
    (swap! state/app-state assoc-in [:chat :message] "") ;; reset
    nil))


(defn update-buffer
  [state {date :date
          min-val :min}]
  (update-in state [:buffer :data 0] conj 
             [date (min min-val
                        (-> state :buffer :chart-options :yaxis :max))]))


;; TODO: move this to :component-did-update of geo
(defn update-geos
  [{:keys [connections geo-map markers] :as old-state} msg] 
  (if (=  connections msg)
    old-state ;; nothing to see here, move along, move along
    ;; TODO: use changed-keys here 
    (let [new-connections (into {} (select-keys msg (apply disj (-> msg keys set)
                                                           (keys connections))))
          new-markers (into {}
                            (for [[k {:keys [lat lng city region country]}] new-connections]
                              [k (-> utils/L
                                     (.marker  (utils/L.latLng lat lng))
                                     (.bindPopup (goog.string.format "<b>%s</b><br />%s, %s"
                                                                     city region country)))]))
          dead-marker-keys (utils/changed-keys msg connections)]
      (debug "map changed" new-connections new-markers dead-marker-keys)
      (let [debug-new-markers (merge (apply dissoc markers dead-marker-keys)
                                     new-markers)]
        (debug debug-new-markers)
        (-> old-state 
            (assoc :markers debug-new-markers)
            (assoc :connections msg))))))



(defn dispatch-message
  [[id msg]]
  (case id
    :djdash/buffer (swap! state/app-state update-buffer msg)
    :djdash/new-geos (swap! state/app-state update-in [:geo] update-geos msg)
    :djdash/now-playing (swap! state/app-state (fn [o]
                                                 (let [{:keys [listeners playing] :as new-data} msg]
                                                   (-> o
                                                       (update-in [:playing] merge
                                                                  (select-keys new-data [:listeners :playing]))
                                                       (assoc-in  [:playing :live?] (utils/live? playing))
                                                       (update-in  [:playing :data 0] conj [(js/Date.now)
                                                                                            listeners])))))
    :djdash/next-shows (swap! state/app-state (fn [o]
                                                (-> o
                                                    (assoc-in [:schedule :data] msg)
                                                    update-scheduled-now)))
    (error "unknown message type" id msg)))


(defn request-updates
  []
  (chsk-send! [:djdash/schedule {:cmd :refresh}])
  (chsk-send! [:djdash/now-playing {:cmd :refresh}])
  (chsk-send! [:djdash/geo {:cmd :refresh}]))


(defn set-to-checking
  []
  (info "disconnected, setting to checking")
  (swap! state/app-state
         (fn [s]
           (-> s            
               (assoc-in [:playing :playing] (:checking state/strs))
               (assoc-in [:playing :listeners] (:checking state/strs))
               (assoc-in [:schedule :now] (:checking state/strs))))))


(defn dispatch-event
  [{:keys [?data id]}]
  ;; TODO: try/catch here to as to not choke the router
  (case id
    :chsk/recv (dispatch-message ?data)
    :chsk/state (cond
                 (-> ?data :first-open?) (request-updates)
                 ;; TODO: CHECK taht this works
                 (-> ?data :disconnected) (set-to-checking))
    ;; ignore handshakes, etc
    nil))


;; this actually starts the routing going
(def event-router (sente/start-chsk-router! ch-chsk dispatch-event))


