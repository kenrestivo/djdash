(defproject djdash "0.1.8"
  :description "Dashboard for SPAZ Radio"
  :url "http://spaz.org/radio"

  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/clojurescript "1.7.170"] 
                 [weasel "0.7.0" :exclusions [org.clojure/clojurescript]]
                 [cljs-http "0.1.37"]
                 [com.taoensso/sente "1.6.0" :exclusions [io.aviso/pretty com.taoensso/encore]]
                 [prismatic/schema "1.0.3"]
                 [com.stuartsierra/component "0.3.0"]
                 [org.clojure/data.zip "0.1.1"]
                 [camel-snake-kebab "0.3.2"]
                 [enlive "1.1.6"]
                 [compojure "1.4.0"] 
                 [ring "1.4.0"]
                 [me.raynes/conch "0.8.0"]
                 [hikari-cp "1.3.1"]
                 [org.clojure/java.jdbc "0.4.2"]
                 [org.postgresql/postgresql "9.3-1103-jdbc41"]
                 [migratus "0.8.7"]
                 [honeysql "0.6.2"]
                 [environ "1.0.1"]
                 [http-kit "2.1.19"]
                 [utilza "0.1.77"]
                 [clj-ical "1.1" :exclusions [clj-time]]
                 [org.clojure/data.xml "0.0.8"]
                 [clj-time "0.11.0"]
                 [com.taoensso/timbre "4.1.4"]
                 [org.clojure/tools.reader "1.0.0-alpha1"]
                 [cheshire "5.5.0"]
                 [stencil "0.5.0"]
                 [clj-http "2.0.0"]
                 [com.andrewmcveigh/cljs-time "0.3.14" :exclusions [com.cemerick/austin]]
                 [org.clojure/tools.trace "0.7.9"]
                 [org.clojure/core.async "0.2.371"]
                 [com.cemerick/piggieback "0.2.1"]
                 [org.omcljs/om "0.9.0"]
                 ]


  :source-paths ["clj-src"]
  :plugins [[lein-pdo "0.1.1"]
            [migratus-lein "0.2.0"]
            [lein-cljsbuild "1.1.1"]]
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

  :clean-targets ^{:protect false} [:target-path :compile-path "resources/public/js/dev/"
                                    "resources/public/js/djdash.js"
                                    "resources/public/js/djdash.js.map"
                                    "resources/public/js/djdash-min.js"
                                    "resources/public/js/djdash.js.map"
                                    "resources/public/js/release/"]
  :profiles {:dev {:repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}
                   :dependencies [[org.slf4j/log4j-over-slf4j "1.7.12"]
                                  [org.slf4j/slf4j-simple "1.7.12"]]}
             :uberjar {:prep-tasks [["cljsbuild" "once" "release"] "javac" "compile"]
                       :uberjar-name "djdash.jar"
                       :aot :all}
             :release {:jvm-opts ["-XX:-OmitStackTraceInFastThrow"
                                  "-Xms32m"
                                  "-Xmx512m"
                                  "-XX:MaxMetaspaceSize=56m"]}
             :repl {:timeout 180000
                    :injections [(do
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
                                                      "externs/leaflet-src.js"
                                                      "resources/public/js/jquery.min.js"
                                                      "resources/public/js/jquery.flot.time.min.js"
                                                      "resources/public/js/jquery.flot.min.js"]}}}}
  :aliases {"tr" ["with-profile" "+user,+dev,+server"
                  "pdo" "cljsbuild" "once" "dev," "trampoline" "repl" ":headless"]
            "slamhound" ["run" "-m" "slam.hound" "clj-src/"]
            "devbuild" ["cljsbuild" "auto" "dev"]})


