(ns djdash.repl)

(comment
  ;; This whole NS will need a place to live,
  ;; but if this piggieback and weasel are only loaded for dev,
  ;; then it'll cause compilation errors if this is here.
  ;; somehow this ns will need to be condidionally compiled only when in dev mode

  (require '[cemerick.piggieback])
  (require '[weasel.repl.websocket])

  (cemerick.piggieback/cljs-repl
   (weasel.repl.websocket/repl-env :ip "0.0.0.0" :port 9001))
  
  )
