(ns djdash.utils
  (:require [utilza.java :as ujava]))


(defn broadcast
  "sends a broadcast to everyone"
  [{:keys [connected-uids chsk-send!]} k data]
  (doseq [u (:any @connected-uids)]
    (chsk-send! u [k data])))

(defn broadcast-sys
  "fishes the webserver->sente out of system, and sends a broadcast to everyone"
  [system k data]
  (broadcast (-> system :web-server :sente) k data))



(defn revision-info
  "Utility for determing the program's revision."
  []
  (let [{:keys [version revision]} (ujava/get-project-properties "djdash" "djdash")]
    (format "Version: %s, Revision %s" version revision)))