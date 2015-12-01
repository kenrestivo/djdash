(ns djdash.schedule-test
  (:require [clojure.test :refer :all]
            [utilza.file :as file]
            [taoensso.timbre :as log]
            [djdash.schedule :as schedule :refer :all]
            [schema.core :as s]))




(comment

;;; TODO;
  (require '[clojure.edn :as edn])

  (let [d (java.util.Date.)
        {:keys [url]} (->> @sys/system :scheduler :settings)
        {:keys [current future] :as old}  (->> "resources/test-data/broken-schedule.edn" 
                                               slurp 
                                               edn/read-string)]
    (let [{:keys [current future] :as new-sched} (->> (concat current future) ;; rejoining for resplitting
                                                      (split-by-current d))]
      (-> (or (some->> url fetch-schedule  (split-by-current d))
              new-sched)
          ;; don't need to keep all the old currents!
          (update-in [:current] #(-> % last vector)))))

  )

