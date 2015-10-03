(ns djdash.utils)


(defn broadcast
  "sends a broadcast to everyone"
  [{:keys [connected-uids chsk-send!]} k data]
  (doseq [u (:any @connected-uids)]
    (chsk-send! u [k data])))

(defn broadcast-sys
  "fishes the webserver->sente out of system, and sends a broadcast to everyone"
  [system k data]
  (broadcast (-> system :web-server :sente) k data))
