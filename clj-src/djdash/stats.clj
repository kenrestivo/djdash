(ns djdash.stats
  (:require [clojure.xml :as xml]
            [clojure.zip :as zip]
            [utilza.misc :as umisc]
            [utilza.core :as utilza]
            [clojure.string :as str]
            [camel-snake-kebab.core :as convert]
            [clojure.edn :as edn]
            [clj-http.client :as client]
            [taoensso.timbre :as log]
            [clojure.tools.trace :as trace]
            [clojure.data.zip.xml :as zx]
            [clojure.java.io :as io]))


(def listener-map {:connected #(Long/parseLong %)
                   :id #(Long/parseLong %)})



(defn total-listener-count
  [m]
  (->> m
       vals
       (map :count)
       (apply +)))

(defn listener-details
  [m]
  (->> m
       vals
       (map :listeners)
       (mapcat identity)
       (map #(dissoc % :connected))))


(defn parse-xml
  "Takes string, returns zipped xml"
  [s]
  (-> s
      (.getBytes "utf-8")
      io/input-stream
      xml/parse
      zip/xml-zip))


(defn xml-nodes-to-map
  "Convert a seq of annoying XML nodes to a proper clojure map"
  [ns]
  (->> (for [n ns]
         [(-> n :tag convert/->kebab-case) 
          (-> n
              :content
              first)])
       (into {})
       (umisc/munge-columns listener-map)
       ))


(defn listeners
  "Oh gawd what a nightmare"
  [n]
  (->> n
       :content
       xml-nodes-to-map
       ))


(defn source-data
  "Takes xml data as parsed by xml/parse,
   returns a seq of maps of the sources and their data and listeners"
  [zxml]
  (for [z-source (zx/xml-> zxml :source)]
    {:mount (zx/xml1-> z-source (zx/attr :mount))
     :count (Long/parseLong (zx/xml1-> z-source :Listeners zx/text))
     :listeners (-> z-source
                    (zx/xml-> :listener zip/node listeners)
                    )}))




(defn mount-count
  "Takes zipped XML tree, returns seq of maps of :mount path and :count listeners"
  [zipped-xml]
  (for [s (zx/xml-> zipped-xml :source)] 
    {:mount (zx/xml1-> s (zx/attr :mount))
     :count (-> s
                (zx/xml1->  :listeners zx/text)
                Long/parseLong)}))

(defn active-mounts
  "Takes seq of maps of :mount and :count, returns only the mounts for counts non-zero"
  [ms]
  (->> ms
       (filter #(-> % :count pos?))
       (map :mount)))



(defn get-mounts
  [{:keys [host port adminuser adminpass]}]
  (->> (client/get (format "http://%s:%d/admin/stats.xml" host port)
                   {:basic-auth [adminuser adminpass]})
       :body
       parse-xml
       mount-count
       active-mounts))


(defn eliminate-headers
  "Wipes out the initial <?xml>from a string of xml"
  [s]
  (->> (.split s "\n")
       ;; TODO: stupid, just use a regexp
       (remove #(boolean (re-find #"^<\?xml.+>$" %)))))


(defn get-stats
  "Obtain the detailed stats as XML for the mountpoint supplied.
   Remove the XML headers for later concatenation and passing along to server."
  [mount {:keys [host port adminuser adminpass]}]
  (try (->> (client/get (format "http://%s:%d/admin/listclients" host port)
                        {:basic-auth [adminuser adminpass]
                         ;;:throw-exceptions false
                         :query-params {:mount mount}})
            :body
            eliminate-headers
            first
            parse-xml
            source-data)
       (catch Exception e
         (log/error e))))


(defn get-combined-stats
  "Takes seq of strings of mounts. Returns all the stats for the mounts as an xml string"
  ([mounts settings]
     (->> (for [m mounts]
            (get-stats m settings))
          (mapcat identity)
          (utilza/mapify :mount)
          ))
  ([settings]
     (get-combined-stats (get-mounts settings) settings)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(comment

  (require '[utilza.repl :as urepl])
  
  (->> "/home/cust/spaz/src/dash-dev.edn"
       slurp
       edn/read-string
       :now-playing
       get-combined-stats
       (urepl/massive-spew "/tmp/foo.edn"))

  
  )