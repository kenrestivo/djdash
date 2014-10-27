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

(comment

  (swap! app-state assoc-in  [:playing :listener-history] [8 2 0 4 12 3 4])

  (d3-date (js/Date.now))

  (swap! app-state assoc-in  [:chat :timeout] 120000)
  
  (swap! app-state assoc-in  [:playing :timeout] 1000)
  
  (swap! app-state update-in  [:playing :listener-history] conj {:x (js/Date.now) :y (rand 20)})

  (->  @app-state :playing :listener-history)

  (utils/mangle-dygraph (-> @app-state :playing :listener-history))
  
  )


(comment

  (time/show-formatters)

  (time/parse (time/formatters :hour-minute-second) 1001001)

  (coerce/from-long (js/Date.now))

  (time/unparse (time/formatters :hour-minute-second)  (coerce/from-long (js/Date.now)))

  (tcore/local-date )

  (tcore/local-date (coerce/from-long (js/Date.now)))

  (time/unparse (time/formatters :hour-minute-second)   (tcore/local-date (coerce/from-long (js/Date.now))))


  )