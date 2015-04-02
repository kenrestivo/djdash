(ns djdash.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [om.core :as om :include-macros true]
            [weasel.repl :as ws-repl]
            [taoensso.sente  :as sente :refer (cb-success?)]
            [goog.style :as gstyle]
            [goog.storage.mechanism.mechanismfactory]
            ;; [ankha.core :as ankha] ;; breaks everything :-(
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

(def ^:export jq js/jQuery)

(def storage (goog.storage.mechanism.mechanismfactory.create))

(def strs {:checking "Checking..."})




(defn buffer-tick
  [n axis]
  (str (-> n int (/ 1000)) "k"))


(def app-state (atom {
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
                      }))



(let [{:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket! "/ch" ; Note the same path as before
                                  {:type :auto ; e/o #{:auto :ajax :ws}
                                   })]
  (def chsk       chsk)
  (def ch-chsk    ch-recv) ; ChannelSocket's receive channel
  (def chsk-send! send-fn) ; ChannelSocket's send API fn
  (def chsk-state state))   ; Watchable, read-only atom









(defn flot
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
  [{:keys [buffer]} owner]
  (reify
    om/IRenderState
    (render-state [_ s]
      (dom/div #js {:id "annoying-placeholder"} ;; annoying               
               
               (dom/div #js {:className "row"}
                        (dom/div #js {:className "col-md-2"}
                                 "Buffer Status")
                        (dom/div #js {:className "col-md-8"}
                                 (om/build flot buffer)))
               
               ))))



(om/root
 main-view
 app-state
 {:target (. js/document (getElementById "content"))})


(comment
  ;; breaks everything :-(
  ;; a hacky conditional run, if not compile
  (when-let [target (js/document.getElementById "inspect")]
    (om/root
     ankha/inspector
     app-state
     {:target target}))
  )




;; TODO: auto-reconnect
;; TODO: conditionally compile this only if in dev mode
(defn ^:export connect
  []
  (ws-repl/connect "ws://localhost:9001" :verbose false))


(defn format-buffer
  [{:keys [min max avg date]}]
  [date min])

(defn dispatch-message
  [[id msg]]
  (when (= :djdash/buffer id)
    (swap! app-state update-in [:buffer :data 0] conj  (format-buffer msg))))


(defn dispatch-event
  [{[ev-type something] :event}]
  (when (= :chsk/recv ev-type)
    (dispatch-message something)))




(def foo (sente/start-chsk-router! ch-chsk dispatch-event))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(comment

  (do
    (swap! app-state assoc-in  [:chat :timeout] 120000)
    (swap! app-state assoc-in  [:playing :timeout] 1000))
  
  (-> @app-state :playing :listener-history utils/mangle-dygraph*)

  (vec '(1 2 3))

  (js/console.log (clj->js {:file  (-> @app-state :playing :listener-history utils/mangle-dygraph*)}))
  )

