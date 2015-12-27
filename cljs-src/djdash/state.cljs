(ns djdash.state
  (:require  [taoensso.timbre :as log
              :refer-macros (log  trace  debug  info  warn  error  fatal  report
                                  logf tracef debugf infof warnf errorf fatalf reportf
                                  spy get-env log-env)]
             [taoensso.sente  :as sente :refer (cb-success?)]
             [goog.style :as gstyle]
             [goog.storage.mechanism.mechanismfactory]
             [reagent.core :as reagent :refer [atom]]
             [reagent.session :as session]
             [cljs-http.client :as http]
             [cljs-time.core :as time]
             [djdash.utils :as utils]
             [goog.string :as gstring]
             [cljs-time.coerce :as coerce]
             [cljs-time.format :as tformat]
             [clojure.walk :as walk]
             [cljs.core.async :as async :refer [put! chan <!]])
  (:import [goog.net Jsonp]
           [goog Uri]))



(def storage (goog.storage.mechanism.mechanismfactory.create))

(def strs {:checking "Checking..."})

(defonce app-state (atom {:playing {:playing (:checking strs)
                                    :live? false
                                    :listeners (:checking strs)
                                    :data [[]] ;; important to have that empty first series
                                    :timeout 60000
                                    :flot nil
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
                          :sente {:event-router nil
                                  :chsk nil
                                  :ch-chsk nil
                                  :chsk-send! nil
                                  :chsk-state nil}
                          :geo {:node-name "listener-map"
                                :url "http://otile{s}.mqcdn.com/tiles/1.0.0/osm/{z}/{x}/{y}.png"
                                :options {:subdomains "1234",
                                          :attribution "&copy; <a href='http://www.openstreetmap.org/'>OpenStreetMap</a> <a href='http://www.openstreetmap.org/copyright' title='ODbL'>open license</a>. <a href='http://www.mapquest.com/'>MapQuest</a> <img src='http://developer.mapquest.com/content/osm/mq_logo.png'>"}
                                :connections {}
                                :text-label "Listeners Map"
                                :geo-map nil
                                :markers {}}
                          :schedule {:data {}
                                     :timeout 30000
                                     :now (:checking strs)}
                          :buffer {:node-name "buffer-chart"
                                   :data [[]] ;; important to have that empty first series
                                   :flot nil
                                   :chart-options {:xaxis {:mode "time"
                                                           :ticks 6
                                                           :minTickSize [2, "minute"]
                                                           :timezone "browser"
                                                           :timeformat "%I:%M%p"}
                                                   :colors ["rgb(19,6,203)"]
                                                   :grid {:markings [{:yaxis {:from 0
                                                                              :to 1}
                                                                      :color "#C11B17"}
                                                                     {:yaxis {:from 1
                                                                              :to 20000}
                                                                      :color "#F0F710"}
                                                                     {:yaxis {:from 20000
                                                                              :to 150000}
                                                                      :color "#077D13"}
                                                                     ]}
                                                   :yaxis {:min 0
                                                           :color "rgba(79, 79, 84, 0.5)"
                                                           :max 150000
                                                           :tickFormatter utils/buffer-tick}}}
                          :chat {:user ""
                                 :users [(:checking strs)]
                                 :messages []
                                 :connected? false
                                 }}))
