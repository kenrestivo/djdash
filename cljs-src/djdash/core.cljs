(ns djdash.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [om.core :as om :include-macros true]
            [weasel.repl :as ws-repl]
            [ankha.core :as ankha]
            [goog.style :as gstyle]
            [om.dom :as dom :include-macros true]
            [cljs.core.async :as async :refer [put! chan <!]]))


(comment
  ;; don't want this with nrepl, i guess?
  (enable-console-print!)
  )

(def debug-stuff (atom nil))

(def app-state (atom {:num 0
                      :run? true
                      :bit-count 8
                      :timeout 2000}))

(defn bit-match 
  "bit-test over a list of bit indices"
  [n bits]
  (mapv (partial bit-test n) bits))


(defn convert-binary
  [n nbits]
  (bit-match n (-> nbits range reverse)))



(defn update-bin
  [{:keys [num bit-count] :as state}]
  (assoc state :bits (convert-binary num bit-count)))

(defn increment-state
  [state]
  (-> state
      (update-in [:num] inc)
      update-bin))


(defn get-width
  [owner]
  (some-> owner
          om/get-node
          gstyle/getSize
          .-width))


(defn led-block-width-px
  [width]
  (->  width
       (/ (:bit-count @app-state) 3.2)
       int
       (str "px")))



(defn led
  [cursor owner]
  (reify
    om/IRenderState
    (render-state [_ {:keys [width color]}]
      (dom/div #js {:className (str "led" " "
                                    (if (.valueOf cursor)
                                      (str "led-" color)
                                      ""))
                    :style #js {:width width
                                :height width}}
               nil))))


(defn play-pause
  "a terrible hack, but i don't know the om-ish way to do this yet"
  []
  (if (:run? @app-state)
    "Pause"
    (if (< 0 (:num @app-state))
      "Continue"
      "Start")))


(defn toggle-button
  [_ owner]
  (reify
    om/IRenderState
    (render-state [_ {:keys [pause]}]
      (dom/button
       #js {:className "btn btn-primary btn-lg"
            :onClick (fn [e] (put! pause :toggle))}
       (play-pause)))))


(defn reset-button
  [_ owner]
  (reify
    om/IRenderState
    (render-state [_ {:keys [reset]}]
      (dom/button
       #js {:className "btn btn-primary btn-lg"
            :onClick (fn [e] (put! reset :reset))}
       "Reset"))))


(defn led-block
  [{:keys [bits] :as cursor} owner]
  (reify
    om/IInitState
    (init-state [_]
      {:color "green"})
    om/IDidMount
    (did-mount [this]
      (om/set-state! owner :led-width (-> owner get-width led-block-width-px))
      (js/window.addEventListener "resize"
                                  (fn [_]
                                    (let [resized (-> owner get-width led-block-width-px)]
                                      ;;(js/console.log resized)
                                      (om/set-state! owner :led-width resized)))))
    om/IRenderState
    (render-state [_ {:keys [led-width color]}]
      (apply dom/div #js {:className "leds"
                          :ref "ledblock"}
             (om/build-all led bits
                           {:state {:width led-width
                                    :color color}})))))



(defn bin-app
  [{:keys [bits num] :as cursor} owner]
  (reify
    om/IInitState
    (init-state [_]
      {:pause (chan)
       :reset (chan)})
    om/IWillMount
    (will-mount [_]
      (om/transact! cursor update-bin) ;; start off with something in binary
      (let [pause (om/get-state owner :pause)
            reset (om/get-state owner :reset)]
        (go (while true
              (let [_ (<! pause)] ;; TODO: grab the toggle val from the button?
                (om/transact! cursor :run? not))))
        (go (while true
              (let [_ (<! reset)] 
                (om/update! cursor :num 0)
                (om/transact! cursor update-bin)))))
      ;; TODO: seangrove suggests "pulling it out into a centralized controller"
      (go (while true
            ;; NOTE: MUST dereference cursor to get current state!!!
            (when (:run? @cursor)
              (om/transact! cursor increment-state))
            (<! (cljs.core.async/timeout (:timeout @cursor))))))
    om/IRenderState
    (render-state [_ {:keys [pause reset]}]
      (dom/div #js {:className "container-fluid"}
               (om/build led-block cursor)
               (dom/div #js {:className "clearfix"} nil) ;; bleah
               (dom/div #js {:className "controls"}
                        (om/build reset-button cursor {:init-state {:reset reset}})
                        (om/build toggle-button cursor {:init-state {:pause pause}}))))))





(defn test-app
  "If everythign goes horribly wrong, try this"
  [{:keys [num]} owner]
  (dom/h2 nil (str num)))

;; TODO: auto-reconnect
;; TODO: conditionally compile this only if in dev mode
(defn ^:export connect
  []
  (ws-repl/connect "ws://localhost:9001" :verbose false))


(om/root
 bin-app
 app-state
 {:target (. js/document (getElementById "content"))})


