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

(comment

  (def foo (sente/start-chsk-router! ch-chsk   (fn [{:keys [?data event]}] (println (js->clj event)))))
  
  (def foo (sente/start-chsk-router! ch-chsk #(js/console.log  (-> % :event second))))

  (def foo (sente/start-chsk-router! ch-chsk println))
  
  (foo)

  (swap! app-state update-in [:buffer :data] #())

  (swap! app-state assoc-in [:buffer :data] [[[1425340269416,0] [1425340369416,1] [1425340469416,2]]])

  (swap! app-state assoc-in [:buffer :data]  [[[1415340269416,0] [1415340369416,4] [1415340469416,1]]])


  (swap! app-state assoc-in [:buffer :data]  [[[1415340269416,0] [1415340369416,4] [1415340469416,1]]])

  ;; THIS:
  (swap! app-state update-in [:buffer :data 0] conj  [1415340569416,9])
  
  (-> (js/Date.) .getTime)

  (def foo   [ [ [1415340269416,0] [1415340369416,4] [1415340469416,1] ] ])

  (def bar   [ [ [1415340269416,0] [1415340369416,4] [1415340469416,1] ]  [[5 :second] [6 :series]] ])

  (update-in foo [0] conj  [1415340569416,9])

  (update-in bar [0] conj  [1415340569416,9])

  )