(ns djdash.conf-test
  (:require [clojure.test :refer :all]
            [utilza.file :as file]
            [djdash.conf :as conf :refer :all]
            [schema.core :as s]))


(defn edns-with-path
  [path]
  (for [f (file/file-names path  #".*?\.edn")]
    (str path "/" f)))

(deftest example-confs
  (testing "example configs")
  ;; TODO: need java resoruce path
  (is (every? map?  (for [f (edns-with-path "resources")]
                      (do
                        (testing f)
                        (read-and-validate f))))))




;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(comment

  ;;; for dev and production purposes only, not automated
  (deftest dev-confs
    (is (every? map?  (for [f (edns-with-path "/home/cust/spaz/src/dash-configs")]
                        (do 
                          (println f)
                          (testing f)
                          (read-and-validate f))))))


  (run-tests)



  )
