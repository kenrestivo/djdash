(ns djdash.geo-test
  (:require [clojure.test :refer :all]
            [utilza.file :as file]
            [taoensso.timbre :as log]
            [djdash.geolocate :as geo :refer :all]
            [schema.core :as s]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(comment

  (let [{:keys[url api-key retry-wait max-retries]} (->> @sys/system :geo  :settings)
        {:keys [ip] :as m} {:ip "66.172.10.10", 
                            :user-agent "Wget/1.15 (linux-gnu)", 
                            :connected 674, :id 1546150}
        {:keys [conn-agent dbc]} (->> @sys/system :geo)]
    (when-let [g (fetch-geo ip url api-key retry-wait max-retries)]
                        (log/debug "lookup loop, fetch returned " g)
                        (insert-geo dbc g)
                        (send-off conn-agent merge (merge-and-keyify-geo m g))))


)
