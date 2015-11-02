;; (ns djdash.tailer_test
;;   (:require [taoensso.timbre :as log]
;;             [djdash.tail :as tail :refer :all]
;;             [djdash.core :as dj]
;;             [djdash.utils :as utils]
;;             [clojure.core.async :as async]
;;             [clojure.string :as s]
;;             [clojure.java.io :as jio])
;;   (:import [org.apache.commons.io.input TailerListenerAdapter Tailer]
;;            java.util.concurrent.ConcurrentLinkedQueue)
;;   )


;; (comment


;;   (reset! tailer {:tailer-thread nil
;;                   :queue nil
;;                   :chunked-chan nil})

  
;;   (start "/mnt/sdcard/tmp/foo" 5000  2000 10000)

;;   (-> @tailer :chunked-chan)

;;   (-> @tailer :queue)

;;   (-> @tailer :queue .size)

;;   (-> @tailer :queue .poll)

;;   (-> @tailer :queue .poll type)
  
;;   (-> @tailer :queue get-all)
  
;;   (-> @tailer
;;       :chunked-chan
;;       (async/take!  #(log/info "<c " %)))
  
;;   )

;; (comment

;;   (def l "0.054067 152600")

;;   (process-bufs ["0.054067 152600"
;;                  "0.104647 135976"
;;                  "0.047248 106880" ])


;;   (for [l ["0.054067 152600"
;;            "0.104647 135976"
;;            "0.047248 106880" ]]
;;     (-> l
;;         (s/split  #" ")
;;         first
;;         Float/parseFloat
;;         (* 60)))

;;   ;; duh, silly, it's 3-6 seconds each, no need to do all this! just send to cllient anyway

;;   (/ (* 200.0 60 60) 110676)

;;   (/ 110676 (* 200.0 60 60))

;;   (* 200 60 60)

  
;;   )


;; (comment

;;   (-> @dj/system
;;       :tailer
;;       :tailer
;;       :chunked-chan
;;       (async/take!  #(log/info "<c " %)))


;;   (-> @dj/system
;;       :web-server
;;       :sente
;;       :connected-uids
;;       deref
;;       :any)


;;   (-> @dj/system
;;       :tailer
;;       :web-server)


  

;;   ((-> @dj/system
;;        :web-server
;;        :sente
;;        :chsk-send!) nil [:djdash/foobar {:baz "yeah"}])


;;   (let [sente (-> @dj/system :web-server :sente)
;;         {:keys [connected-uids chsk-send! ch-chsk]} sente]
;;     (doseq [u @connected-uids]
;;       (chsk-send! u [:djdash/foobar {:baz "yeah"}])))
  

;;   (utils/broadcast @dj/system :djdash/foobar {:baz "yeah"})
  
  
;;   )