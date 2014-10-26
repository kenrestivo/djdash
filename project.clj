(defproject djdash "0.1.0-SNAPSHOT"
  :description ""
  :url ""

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-2371"] ;; 2322 for om? or ok with 2371?
                 [weasel "0.4.1"]
                 [cljs-http "0.1.18"]
                 [com.taoensso/sente "1.2.0"]
                 [ankha "0.1.4"]
                 [prismatic/om-tools "0.3.6"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [com.cemerick/piggieback "0.1.3"]
                 [om "0.8.0-alpha1"]]


  :source-paths ["clj-src"]

  :profiles {:dev {:plugins [[lein-cljsbuild "1.0.3"]]
                   :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}}}

  
  :cljsbuild {:builds [{:id "dev" 
                        :source-paths ["cljs-src"]
                        :compiler {:output-to "djdash.js"
                                   :output-dir "out"
                                   :optimizations :none
                                   :source-map  true}}
                       {:id "release"
                        :source-paths ["cljs-src"]
                        :compiler {
                                   :output-to "djdash-min.js"
                                   ;; :output-wrapper true ;; don't know why this would be necessary?
                                   :optimizations :advanced
                                   :pretty-print false
                                   :source-map  "djdash.js.map"
                                   :preamble ["react/react.min.js"]
                                   :externs ["react/externs/react.js"]}}]})
