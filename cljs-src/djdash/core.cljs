(ns djdash.core
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
             [djdash.comms :as comms]
             [djdash.weasel :as weasel]
             [djdash.views :as views]
             [djdash.state :as state]
             [goog.string :as gstring]
             [cljs-time.coerce :as coerce]
             [cljs-time.format :as tformat]
             [clojure.walk :as walk]
             [cljs.core.async :as async :refer [put! chan <!]])
  (:import [goog.net Jsonp]
           [goog Uri]))


(.initializeTouchEvents js/React true)


(defn mount-root 
  []
  (reagent/render [views/home-page] (.getElementById js/document "content"))
  (.addEventListener js/window "resize" views/on-window-resize))


(defn init!
  []
  (js/console.log "loading" (.toString (js/Date.)))
  (log/set-level! (or (-> js/settings utils/un-json :log-level keyword) :info))
  (swap! state/app-state assoc-in [:mode] (-> js/settings utils/un-json :mode)) ;; right place for this?
  (when (= "dev" (some-> @state/app-state :mode))
    ;; clear out for use with figwheel
    ;; kthis is what breaks everything at load time. don't do this in prod, only in dev!
    (swap! state/app-state #(-> % 
                                (assoc-in [:geo :connections] {})
                                (assoc-in [:geo :markers] {}))))
  (log/trace @state/app-state)
  (comms/start-sente)
  (mount-root)
  (when (= "dev" (some-> @state/app-state :mode))
    (log/info "starting weasel")
    (weasel/start-weasel)))



(init!) ;; TODO: move to dev env profile

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(comment

  (ns djdash.core)


  (log/set-level! :debug)

  (-> @state/app-state :schedule keys)

  (-> @state/app-state :chat :messages)


  (-> @state/app-state :geo :geo-map)

  (-> @state/app-state :playing :flot)

  (-> @state/app-state :geo :connections)

  (-> @state/app-state :playing)

  (update-chat!)

  (do
    (swap! state/app-state assoc-in  [:chat :timeout] 120000)
    (swap! state/app-state assoc-in  [:playing :timeout] 60000)
    )

  (swap! state/app-state update-map!)

  
  (merge (apply dissoc old-markers changed-keys)
         new-markers)
  
  (let [changed-keys #{}
        new-markers {29392 "bar"}
        old-markers {99293 "foo"}]
    (assoc-in @state/app-state [:geo :markers] (merge (apply dissoc old-markers changed-keys)
                                                      new-markers)))

  (:geo *1)

  (info "foo")




  )
