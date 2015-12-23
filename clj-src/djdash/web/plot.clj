(ns djdash.web.plot
  ;; XXX UGH USE!!!
  (:use [incanter core stats charts io pdf]))


(defn plot-buffer*
  [name f]
  (with-data  (read-dataset f
                            :delim \space
                            :header false)
    (xy-plot ($ :col0) ($ :col1)
             :title (or name f)
             :x-label "Minutes"
             :y-label "Buffer Size (bytes)")))



(defn plot-data
  [name in out]
  (let [p  (plot-buffer* name in)]
    ;;(save p (str out ".png") :width 1280 :height 768)
    (save-pdf p out :width 1280 :height 768)))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(comment

  (-main "delia 10/22/14" "/mnt/sdcard/tmp/master-buffer.log" "/mnt/sdcard/tmp/delia.pdf")

  (plot-data name in out)


  )  

