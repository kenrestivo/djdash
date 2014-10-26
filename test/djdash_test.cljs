(ns djdash.core
  (:require [djdash.core] :refer :all))

(comment
  (-> @app-state :playing :playing)

  (swap! app-state assoc-in [:playing :playing] "[LIVE!] super live shows!")
  (swap! app-state assoc-in [:playing :playing] "just some other show")
  )