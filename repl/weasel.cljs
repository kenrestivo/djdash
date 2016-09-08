(ns djdash.weasel
  (:require [weasel.repl :as repl]))


(defn start-weasel
  []
  (when-not (repl/alive?)
    (repl/connect "ws://localhost:9001")))
