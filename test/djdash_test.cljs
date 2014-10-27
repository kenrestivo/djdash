(ns djdash.core
  ;; HACK since there is no refer-all, which is useful for testing
)

(comment
  (-> @app-state :playing :playing)

  (swap! app-state assoc-in [:playing :playing] "[LIVE!] super live shows!")
  (swap! app-state assoc-in [:playing :playing] "just some other show")

  (re-find  #"^\[LIVE\!\].*?" "[LIVE!] super live shows!")



  
  )

(comment

  (-> @app-state :chat :users utils/hack-users-list)
  )