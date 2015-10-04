(defproject djdash "0.1.1"
  :description "Dashboard for SPAZ Radio"
  :url "http://spaz.org/radio"

  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/clojurescript "1.7.122"] 
                 [weasel "0.7.0" :exclusions [org.clojure/clojurescript]]
                 [cljs-http "0.1.37"]
                 [com.taoensso/sente "1.6.0" :exclusions [com.taoensso/encore]]
                 [com.stuartsierra/component "0.3.0"]
                 [compojure "1.4.0"] 
                 ;; [ankha "0.1.4"] ;; breaks everything :-(g
                 [ring "1.4.0"]
                 [utilza "0.1.66"]
                 [me.raynes/conch "0.8.0"]
                 [org.clojure/java.jdbc "0.4.2"]
                 [postgresql "9.1-901-1.jdbc4"]
                 [environ "1.0.1"]
                 [http-kit "2.1.19"]
                 [utilza "0.1.61"]
                 [clj-ical "1.1" :exclusions [clj-time]]
                 [org.clojure/data.xml "0.0.8"]
                 [clj-time "0.11.0"]
                 [com.taoensso/timbre "4.1.2"]
                 [cheshire "5.5.0"]
                 [stencil "0.5.0"]
                 [clj-http "2.0.0"]
                 [com.andrewmcveigh/cljs-time "0.3.13" :exclusions [com.cemerick/austin]]
                 [org.clojure/tools.trace "0.7.8"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [com.cemerick/piggieback "0.2.1"]
                 [org.omcljs/om "0.9.0"]
                 ]


  :source-paths ["clj-src"]
  :plugins [[lein-environ "1.0.0"]
            [lein-pdo "0.1.1"]
            [lein-cljsbuild "1.1.0"]]
  ;;:hooks [leiningen.cljsbuild]
  :main djdash.core
  :clean-targets ^{:protect false} [:target-path :compile-path "resources/public/js/dev/"
                                    "resources/public/js/djdash.js"
                                    "resources/public/js/djdash-min.js"
                                    "resources/public/js/release/"]
  :profiles {:dev {:repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}}
             :repl {:injections [(do
                                   (require 'djdash.core)
                                   (djdash.core/-main))
                                 ]}}

  :cljsbuild {:builds {"dev" {:source-paths ["cljs-src"]
                              :compiler {:output-to "resources/public/js/djdash.js"
                                         :output-dir "resources/public/js/dev"
                                         :preamble ["cljsjs/development/react.inc.js"]
                                         :optimizations :none
                                         :source-map  true}}
                       "release"{:source-paths ["cljs-src"]
                                 :jar true
                                 :compiler {:output-dir "resources/public/js/release"
                                            :output-to "resources/public/js/djdash-min.js"
                                            :preamble ["cljsjs/production/react.min.inc.js"]
                                            ;;:output-wrapper true ;; don't know why this would be necessary?
                                            :optimizations :advanced
                                            :pretty-print false
                                            :closure-warnings {:externs-validation :off
                                                               :non-standard-jsdoc :off}
                                            :source-map  "resources/public/js/djdash.js.map"
                                            :externs ["react/externs/react.js"
                                                      "resources/public/js/jquery.min.js"
                                                      "resources/public/js/jquery.flot.time.min.js"
                                                      "resources/public/js/jquery.flot.min.js"]}}}}
  :aliases {"tr" ["with-profile" "+user,+dev,+server"
                  "pdo" "cljsbuild" "once" "dev," "trampoline" "repl" ":headless"]
            "devbuild" ["cljsbuild" "auto" "dev"]})


