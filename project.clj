(defproject djdash "0.1.13"
  :description "Dashboard for SPAZ Radio"
  :url "http://spaz.org/radio"

  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/clojurescript "1.7.170"] 
                 [cljs-http "0.1.37"]
                 [com.taoensso/sente "1.6.0" :exclusions [io.aviso/pretty com.taoensso/encore]]
                 [prismatic/schema "1.0.3"]
                 [hiccup "1.0.5"]
                 [com.stuartsierra/component "0.3.0"]
                 [clojurewerkz/machine_head "1.0.0-beta9"
                  :exclusions [com.google.guava/guava]]
                 [org.clojure/data.zip "0.1.1"]
                 [camel-snake-kebab "0.3.2"]
                 [ring.middleware.jsonp "0.1.6" 
                  :exclusions [ring/ring-core]]
                 [robert/bruce "0.8.0"]
                 [enlive "1.1.6"]
                 [reagent "0.5.1"
                  :exclusions [org.clojure/tools.reader]]
                 [reagent-forms "0.5.13"]
                 [reagent-utils "0.1.5"]
                 [compojure "1.4.0"] 
                 [ring "1.4.0"]
                 [me.raynes/conch "0.8.0"]
                 [incanter/incanter-charts "1.5.6"
                  :exclusions [junit]]
                 [incanter/incanter-pdf "1.5.6"]
                 [hikari-cp "1.3.1"]
                 [org.clojure/java.jdbc "0.4.2"]
                 [org.postgresql/postgresql "9.3-1103-jdbc41"]
                 [migratus "0.8.7"
                  :exclusions [org.clojure/clojure]]
                 [honeysql "0.6.2"]
                 [environ "1.0.1"]
                 [com.taoensso.forks/http-kit "2.1.20"]
                 [utilza "0.1.77"]
                 [org.clojure/tools.nrepl "0.2.11"]
                 [clj-ical "1.1" :exclusions [clj-time]]
                 [org.clojure/data.xml "0.0.8"]
                 [clj-time "0.11.0"]
                 [com.taoensso/timbre "4.1.4"]
                 [org.clojure/tools.reader "1.0.0-alpha1"]
                 [cheshire "5.5.0"]
                 [stencil "0.5.0"
                  :exclusions [org.clojure/core.cache]]
                 [clj-http "2.0.0"]
                 [com.andrewmcveigh/cljs-time "0.3.14" :exclusions [com.cemerick/austin]]
                 [org.clojure/tools.trace "0.7.9"]
                 [org.clojure/core.async "0.2.374"]
                 ]

  ;;:hooks [leiningen.cljsbuild]
  :main djdash.core

  ;; XXX HACK, cough, HACK
  :migratus ~(let [{:keys [host db port user password]} (-> "config.edn" slurp read-string :db)]
               {:store :database
                :migration-dir "migrations/"
                :db {:classname "com.postgresql.Driver"
                     :subprotocol "postgresql"
                     :subname (str "//" host ":" port "/" db)
                     :user user
                     :password password}})

  :clean-targets ^{:protect false} [[:cljsbuild :builds "dev" :compiler :output-dir] 
                                    [:cljsbuild :builds "dev" :compiler :output-to]
                                    [:cljsbuild :builds "release" :compiler :output-dir] 
                                    [:cljsbuild :builds "release" :compiler :output-to]
                                    :target]
  :profiles {:dev {:repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}
                   :source-paths ["clj-src" "cljs-src" "dev"]
                   :plugins [[lein-cljsbuild "1.1.1"]
                             [lein-figwheel "0.5.0-2"
                              :exclusions [org.clojure/tools.reader
                                           org.clojure/clojure
                                           ring/ring-core]]
                             [migratus-lein "0.2.0"
                              :exclusions [org.clojure/clojure]]] 
                   :dependencies [[org.slf4j/log4j-over-slf4j "1.7.12"]
                                  [org.slf4j/slf4j-simple "1.7.12"]
                                  ;; for migratus. can't use timbre-slf4j because
                                  ;; http://yogthos.net/posts/2015-12-26-AOTGotchas.html
                                  [com.cemerick/piggieback "0.2.1"]
                                  [figwheel-sidecar "0.5.0-2"
                                   :exclusions [org.clojure/tools.reader
                                                org.clojure/core.async
                                                http-kit
                                                ring/ring-core]]]}
             :uberjar {:prep-tasks [["cljsbuild" "once" "release"] "javac" "compile"]
                       :uberjar-name "djdash.jar"
                       :source-paths ["clj-src" "cljs-src"]
                       :aot :all}
             :release {:jvm-opts ["-XX:-OmitStackTraceInFastThrow"
                                  "-Xms32m"
                                  "-Xmx512m"
                                  "-XX:MaxMetaspaceSize=56m"]}
             :repl {:timeout 180000
                    :injections [(do ;; implicit?
                                   (require 'user)
                                   (user/start) ;; for figwheel
                                   (require 'djdash.core)
                                   (djdash.core/-main))
                                 ]}}

  :cljsbuild {:builds {"dev" {:source-paths ["cljs-src"]
                              :figwheel true
                              :compiler {:output-to "resources/public/js/djdash.js"
                                         :output-dir "resources/public/js/dev"
                                         :asset-path "js/dev"
                                         :main djdash.core
                                         :optimizations :none
                                         :source-map  true}}
                       "release"{:source-paths ["cljs-src"]
                                 :jar true
                                 :compiler {:output-dir "resources/public/js/release"
                                            :output-to "resources/public/js/djdash-min.js"
                                            ;;:output-wrapper true ;; don't know why this would be necessary?
                                            :optimizations :advanced
                                            :pretty-print false
                                            :closure-warnings {:externs-validation :off
                                                               :non-standard-jsdoc :off}
                                            :source-map  "resources/public/js/djdash.js.map"
                                            :externs ["externs/leaflet-src.js"
                                                      "resources/public/js/jquery.min.js"
                                                      "resources/public/js/jquery.flot.time.min.js"
                                                      "resources/public/js/jquery.flot.min.js"]}}}}
  :aliases {"tr" ["with-profile" "+user,+dev,+server"
                  "pdo" "cljsbuild" "once" "dev," "trampoline" "repl" ":headless"]
            "slamhound" ["run" "-m" "slam.hound" "clj-src/"]
            "devbuild" ["cljsbuild" "auto" "dev"]})


