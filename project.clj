(defproject djdash "0.1.0-SNAPSHOT"
  :description ""
  :url ""

  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/clojurescript "0.0-2156"]
                 [weasel "0.1.0"]
                 [org.clojure/core.async "0.1.267.0-0d7780-alpha"]
                 [com.cemerick/piggieback "0.1.3"]
                 [ankha "0.1.1"]
                 [om "0.5.0"]]


  :source-paths ["clj-src"]

  :profiles {:dev {:plugins [[lein-cljsbuild "1.0.1"]]
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
