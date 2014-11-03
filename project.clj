(defproject djdash "0.1.0-SNAPSHOT"
  :description ""
  :url ""

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-2371"] ;; 2322 for om? or ok with 2371?
                 [weasel "0.4.2"]
                 [cljs-http "0.1.18"]
                 [com.taoensso/sente "1.2.0"]
                 [com.stuartsierra/component "0.2.2"]
                 [compojure "1.2.1"]
                 [ring "1.3.1"]
                 [environ "1.0.0"]
                 [http-kit "2.1.19"]
                 [stencil "0.3.5"]
                 [clj-http "1.0.0"]
                 [com.andrewmcveigh/cljs-time "0.2.3" :exclusions [com.cemerick/austin]]
                 [org.clojure/tools.trace "0.7.8"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [com.cemerick/piggieback "0.1.3"]
                 [om "0.8.0-alpha1"]]


  :source-paths ["clj-src"]
  :plugins [[lein-environ "1.0.0"]]
  :profiles {:dev {:plugins [[lein-cljsbuild "1.0.3"]]
                   :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}
                   :env {:web-server {:mode :dev
                                      :playing-url "http://lamp/playing/playing.php"
                                      :chat-url "http://lamp/spaz/radio/chatster/doUpdate.php"}}}
             
             :repl {:injections [(do (require 'djdash.core)
                                     (djdash.core/-main)
                                     )]}}

  
  :cljsbuild {:builds [{:id "dev" 
                        :source-paths ["cljs-src"]
                        :compiler {:output-to "resources/public/js/djdash.js"
                                   :output-dir "resources/public/js/out"
                                   :optimizations :none
                                   :source-map  true}}
                       {:id "release"
                        :source-paths ["cljs-src"]
                        :compiler {
                                   :output-dir "resources/public/js/release"
                                   :output-to "resources/public/js/djdash-min.js"
                                   ;;:output-wrapper true ;; don't know why this would be necessary?
                                   :optimizations :advanced
                                   :pretty-print false
                                   :source-map  "resources/public/js/djdash.js.map"
                                   :preamble ["react/react.min.js"]
                                   :externs ["react/externs/react.js"
                                             "dyraph-externs.js"]}}]}
  :env  {:web-server {:port 8080
                      :playing-url "http://spazradio.bamfic.com/playing.php"
                      :chat-url "http://spaz.org/radio/chatster/doUpdate.php"
                      :mode :release}
         :timbre-config {:appenders {:spit {:enabled? true
                                            :fmt-output-opts {:nofonts? true}}
                                     :standard-out {:enabled? true
                                                    :fmt-output-opts {:nofonts? true}}}
                         :shared-appender-config {:spit-filename "/tmp/web.log"}}})

