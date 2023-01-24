(ns video-hub.gui
  (:require
   [cljfx.api :as fx]
   [manifold.stream :as s]
   [clojure.string :as str]
   [clojure.pprint :as pprint]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.data :as data]
   [taoensso.timbre :as timbre
    :refer [log  trace  debug  info  warn  error  fatal  report
            logf tracef debugf infof warnf errorf fatalf reportf
            spy get-env]]
   [taoensso.timbre.appenders.core :as appenders]
   [video-hub.layout :as lo]
   [video-hub.client :as cli])
  (:import [javafx.stage FileChooser]
           [javafx.event ActionEvent]
           [javafx.scene Node]))

(def log-file-name (apply str (concat "logs/"(str (.getTime (java.util.Date.))) ".log")))

; Set the lowest-level to output as :debug
(timbre/set-level! :debug)

;; Create a "spit to file" appender in timbre v4.0.0:
(timbre/merge-config! {:appenders {:spit (appenders/spit-appender {:fname log-file-name})}})

;;appilcation state
(def *state
  (atom  {:file nil           ;; current file name
          :connected? false   ;; established connection with video hub
          :output nil         ;; placeholder for output combo value
          :input  nil         ;; placeholder for output combo value
          :connection nil     ;; connection info
          :items []           ;; individual combo items
          :scenes {}          ;; user-defined scenes
          :client nil         ;; current client used for connection with video hub
          :layout nil         ;; the layout includes a default layout, connection, and scenes
          :layout-status nil  ;; the current layout status as given by video hub
          :quads {}           ;; multi view 4 devices or quads for short
          }))

;; the current stack of layouts for undos
(def *undo-stack (atom ()))

(comment (def test-atom (atom ()))

         (swap! test-atom conj 2)

         @test-atom

         (swap! test-atom conj 3)

         (swap! test-atom pop)

         @*undo-stack
         @*undo-stack
         (count @*undo-stack)
         (first @*undo-stack)
         (second @*undo-stack)
         (nth @*undo-stack 2)
         {:layout (into {} (for [v (first @*undo-stack)] {(key v) (lo/inc-route-pair (val v))}))}
         (lo/layout->routes-reqs {:layout (into {} (for [v (first @*undo-stack)] {(key v) (lo/inc-route-pair (val v))}))})

         )


;; block of code used to test undo-stack
(comment (def inspect-stack @*undo-stack)
         inspect-stack
         (swap! *state assoc :layout-status (:layout @*state))
         (:layout (:layout @*state)))

(def undo-watcher (fn [key atom old-state new-state]
                    (let [layout-old (:layout (:layout-status old-state))
                          layout-new (:layout (:layout-status new-state))]
                      (when (and (some? layout-old )
                                 (not (= layout-old layout-new)))
                        (do
                          (debug "layouts not equal")
                          (swap! *undo-stack conj layout-old))))))

;; add watch to update *undo-stack on state change
(add-watch *state :app-state-watcher
          undo-watcher
           )

(defn load-scene!
  "Loads a scene-file on respective button press."
  [scene-file]
  (let [data (edn/read-string (slurp scene-file))]
    (swap! *state assoc :layout (assoc {} :layout (:layout data)
                                       :connection (:connection @*state)
                                       :scenes (:scenes @*state))                        :file (io/file scene-file))))

(defn update-layout-status!
  "Helper for update-status! and responsible for the data that is displayed at current status."
  [status]
  (comment (debug (str "received: " status)))
  (when (and (not (or (str/includes? status "LOCKS") (str/includes? status "PRELUDE"))) (str/includes? status "ROUTING"))
    (let [layout-status (rest (str/split status #"\n"))
          layout {:layout (lo/status->layout layout-status)
                  :connection (:connection @*state)}]
      (when (= (count (:layout layout)) (count (:items @*state)))
        (swap! *state assoc :layout-status layout)))))


(defn update-status!
  "Periodically polls layout status from video hub."
  []
  (let [c (:client @*state)
        req-output-routing "VIDEO OUTPUT ROUTING:\n\n"
        p (s/periodically 500 #(s/try-put! c req-output-routing 500))]
    (s/consume #(if (and (str/includes? % "\n\n") (str/includes? % "ROUTING")) (update-layout-status! %)) c)
    (s/consume #(deref %) p)
    ))

(defn connect!
  "Establishes a connection at specified ip and port given from config file. Also triggers update-status!"
  [_]
  (let [client (cli/try-client (:ip (:connection @*state)) (:port (:connection @*state)))]
    (swap! *state assoc :connected? (str/includes? (str (s/try-put! client "PING:\n\n" 500)) "Success"))
    (if (:connected? @*state)
      (do
        (swap! *state assoc :client client)
        (update-status!)
        (debug (str "connection established: " (:connection @*state)))
        )
      (swap! *state assoc :connected? false))))

(defn undo! [_]
  ;; pop @*undo-stack
  ;; update
  (when (and (not (empty? @*undo-stack))
             (:connected? @*state))
    (connect! "")
    (remove-watch *state :app-state-watcher)
    (s/try-put! (:client @*state) (lo/layout->routes-reqs {:layout (into {} (for [v (first @*undo-stack)] {(key v) (lo/inc-route-pair (val v))}))}) 500)
    (swap! *undo-stack pop)
    ;; give pause for state to change
    (Thread/sleep 250)
    (add-watch *state :app-state-watcher
                         undo-watcher
                         )
    )
  )

(defn update-route!
  "Used to update a single specific route in the current layout."
  [_]
  (debug "User pressed UPDATE route")
  (when (and (:connected? @*state)
             (some? (:output @*state))
             (some? (:input @*state)))
    (debug (str "VIDEO OUTPUT ROUTING:\n" (dec (:output @*state)) " " (dec (:input @*state)) "\n\n"))
    (connect! "")
    (s/try-put! (:client @*state) (str "VIDEO OUTPUT ROUTING:\n" (dec (:output @*state)) " " (dec (:input @*state)) "\n\n") 500)))

(defn set-output!
  "Helper for update-route!"
  [x]
  (swap! *state assoc :output x))

(defn set-input!
  "Helper for update-route!"
  [x]
  (swap! *state assoc :input x))

(defn name->button
  "Used to dynamically create user-defined scene buttons."
  [data]
  (let [name (name (key data))
        scene-file (val data)]
    {:fx/type :button
     :text name
     :on-action (fn [_] (load-scene! scene-file))}))

(defn scenes->buttons
  "Transforms seq of scenes to vec of buttons."
  [scenes]
  (if (not-empty scenes)
    (->> scenes
         (mapv name->button))
    []))

(def test-quads {:quads {:q1 {:connection {:ip "", :port 8888}}
                         :q2 {:connection {:ip "", :port 7777}}}})

(into {}
      (for [q (:quads test-quads)]
        {(key q) (merge (val q) {:connected? nil})}
        ))
;; => {:q1 {:connection {:ip "", :port 8888}, :connected? nil}, :q2 {:connection {:ip "", :port 7777}, :connected? nil}}

;; comment block to test change source with quads
(comment

  (defn take-all! [cli-conn]
    (loop [c cli-conn s ""]
      (let [c-data @(s/try-take! c 250)]
        (if (nil? c-data)
          s
          (recur c (str s c-data))
          ))

      )
    )
  (let [c (cli/try-client "192.168.50.150" 9990)]

           (do (s/try-put! c (str "VIDEO OUTPUT ROUTING:\n "
                                  "4 0"
                                  "\n\n") 1000)
               (debug @(s/try-take! c 1000))
               (.close c)))

         (let [c             (cli/try-client "192.168.50.150" 9990)
               response           @(s/take! c)
               solo-enabled? (Boolean/parseBoolean (last (when (> (count response) 47) (str/split (response 47) #" "))))]

           (debug response)
           (do (s/try-put! c (str "CONFIGURATION:\n Solo enabled: "
                                  (not solo-enabled?)
                                  "\n\n") 1000)

               (.close c)))

         (let [c (cli/try-client "192.168.50.150" 9990)]
           (debug @(s/try-take! c 1000))
           (debug @(s/try-take! c 1000))
           (debug @(s/try-take! c 1000))
           (debug @(s/try-take! c 1000))
           (debug @(s/try-take! c 1000))
           (debug @(s/try-take! c 1000))
           (debug @(s/try-take! c 1000))
           (debug @(s/try-take! c 1000))
           (.close c))

         (let [c (cli/try-client "192.168.50.150" 9990)]
           (s/consume #(println %) c)
           (.close c)
           )

         (let [c (cli/try-client "192.168.50.150" 9990)
               d @(s/try-take! c 1000)]
           (while (not (nil? d)) (debug d)
             )
           (.close c)
           )

         (let [c (cli/try-client "192.168.50.150" 9990)]
           (debug (take-all! c))
           (.close c)
           )
;; => nil

         (let [c             (cli/try-client "192.168.50.150" 9990)
               response      (take-all! c)
               solo-enabled? (Boolean/parseBoolean (last (str/split ((str/split-lines response) 47) #" ")))
               ]

            (do (s/try-put! c (str "CONFIGURATION:\n Solo enabled: "
                                            (not solo-enabled?)
                                            "\n\n") 1000)

                         (.close c))

           )
         )


(defn quad-component [quad-config]
  "create quad component from quad configuration data map"
  (let [name       (key quad-config)
        connection (:connection (val quad-config))
        connected? (:connected? (val quad-config))]

    {:fx/type  :v-box
     :padding  -10
     :spacing  0
     :children [{:fx/type fx/ext-let-refs
                 :refs    {::toggle-group {:fx/type :toggle-group}}
                 :desc    {:fx/type  :v-box
                           :padding  20
                           :spacing  10
                           :children [{:fx/type  :h-box
                                       :spacing  10
                                       :children [{:fx/type :label
                                                   :text    (str name)}
                                                  {:fx/type :label
                                                   :text    (str "Connected? " connected?)}]}
                                      {:fx/type  :h-box
                                       :spacing  10
                                       :children [{:fx/type   :toggle-button
                                                   :text      "1"
                                                   :on-action (fn [_] (let [c (cli/try-client (:ip connection) (:port connection))
                                                                            fixed-output 5
                                                                            source       1]

                                                                        (do (s/try-put! c (str "VIDEO OUTPUT ROUTING:\n "
                                                                                               (lo/route-cmd->req source fixed-output)
                                                                                               "\n") 250)
                                                                            (debug (cli/take-all! c))
                                                                            (.close c))))

                                                   :toggle-group {:fx/type fx/ext-get-ref
                                                                  :ref     ::toggle-group}}

                                                  {:fx/type      :toggle-button
                                                   :text         "2"
                                                   :on-action    (fn [_] (let [c            (cli/try-client (:ip connection) (:port connection))
                                                                               fixed-output 5
                                                                               source       2]

                                                                           (do (s/try-put! c (str "VIDEO OUTPUT ROUTING:\n "
                                                                                                  (lo/route-cmd->req source fixed-output)
                                                                                                  "\n") 250)
                                                                               (debug (cli/take-all! c))
                                                                               (.close c))))
                                                   :toggle-group {:fx/type fx/ext-get-ref
                                                                  :ref     ::toggle-group}}]}

                                      {:fx/type  :h-box
                                       :spacing  10
                                       :children [{:fx/type      :toggle-button
                                                   :text         "3"
                                                   :on-action    (fn [_] (let [c            (cli/try-client (:ip connection) (:port connection))
                                                                               fixed-output 5
                                                                               source       3]

                                                                           (do (s/try-put! c (str "VIDEO OUTPUT ROUTING:\n "
                                                                                                  (lo/route-cmd->req source fixed-output)
                                                                                                  "\n") 250)
                                                                               (debug (cli/take-all! c))
                                                                               (.close c))))
                                                   :toggle-group {:fx/type fx/ext-get-ref
                                                                  :ref     ::toggle-group}}

                                                  {:fx/type      :toggle-button
                                                   :text         "4"
                                                   :on-action    (fn [_] (let [c            (cli/try-client (:ip connection) (:port connection))
                                                                               fixed-output 5
                                                                               source       4]

                                                                           (do (s/try-put! c (str "VIDEO OUTPUT ROUTING:\n "
                                                                                                  (lo/route-cmd->req source fixed-output)
                                                                                                  "\n") 250)
                                                                               (debug (cli/take-all! c))
                                                                               (.close c))))
                                                   :toggle-group {:fx/type fx/ext-get-ref
                                                                  :ref     ::toggle-group}}]}]}}

                {:fx/type   :button
                 :text      "TOGGLE Solo mode"
                 :on-action (fn [_] (let [c             (cli/try-client "192.168.50.150" 9990)
                                          response      (cli/take-all! c)
                                          solo-enabled? (Boolean/parseBoolean (last (str/split ((str/split-lines response) 47) #" ")))]

                                      (do (s/try-put! c (str "CONFIGURATION:\n Solo enabled: "
                                                             (not solo-enabled?)
                                                             "\n\n") 1000)

                                          (.close c))))}]}))

(defn quad-data->component [quad-configs]
  (if (not-empty quad-configs)
    (->> quad-configs
         (mapv quad-component)
         (into [])
         )
    [])
  )

(quad-data->component {:q1 {:connection {:ip "", :port 8888}, :connected? nil}, :q2 {:connection {:ip "", :port 7777}, :connected? nil}})

(get-in (val (first {:q1 {:connection {:ip "", :port 8888}, :connected? nil}, :q2 {:connection {:ip "", :port 7777}, :connected? nil}})) [:connection :ip])
(defn change-layout!
  "Sends the loaded scene to the video hub. changes will be reflected in the current layout status box."
  [_]
  (debug "User pressed SEND layout button")
  (when (:connected? @*state)
    (connect! "")
    (s/try-put! (:client @*state) (lo/layout->routes-reqs {:layout (:layout (:layout @*state))}) 1000)))

(defmulti handle ::event)

(defmethod handle ::save-file
  [{:keys [^ActionEvent fx/event]}]
  (debug "User pressed Save Current Configuration")
  (let [window (.getWindow (.getScene ^Node (.getTarget event)))
        chooser (doto (FileChooser.)
                  (.setTitle "Save Current Configuration"))]
    (when-let [file (.showSaveDialog chooser window)]

      (spit file (with-out-str (clojure.pprint/write {:layout (:layout (:layout @*state))
                                                      :connection (:connection @*state)
                                                      :scenes (:scenes @*state)}
                                                    )
                               :dispatch clojure.pprint/code-dispatch)))))

(defmethod handle ::save-scene
  [{:keys [^ActionEvent fx/event]}]
  (debug "User pressed Save Current to Scene")
  (let [window (.getWindow (.getScene ^Node (.getTarget event)))
        chooser (doto (FileChooser.)
                  (.setTitle "Save Current Scene"))]
    (when-let [file (.showSaveDialog chooser window)]
      (let [name (first (str/split (.getName file) #"\."))]
        (swap! *state assoc :scenes (conj (:scenes @*state) {(keyword name) (.getAbsolutePath file)}))
        (spit file (with-out-str (clojure.pprint/write {:name name
                                                        :layout (into {} (for [v (:layout (:layout-status @*state))]
                                                                           {(key v) (lo/inc-route-pair (val v))}))}
                                                       )
                     :dispatch clojure.pprint/code-dispatch))))))

(defmethod handle ::open-file [{:keys [^ActionEvent fx/event]}]
  (let [window (.getWindow (.getScene ^Node (.getTarget event)))
        chooser (doto (FileChooser.)
                  (.setTitle "Open File"))]
    (when-let [file (.showOpenDialog chooser window)]
      (let [data (edn/read-string (slurp file))
            state {:state {:file file
                           :layout data
                           :connection (:connection data)
                           :connected? (:connected? @*state)
                           :client     (:client @*state)
                           :items      (range 1 (inc (count (vals (:layout data)))))
                           :scenes     (:scenes data)
                           :quads      (into
                                        {}
                                        (for
                                         [q (:quads data)]
                                         {(key q) (merge
                                                   (val q)
                                                   {:connected? (cli/ping-something (get-in (val q) [:connection :ip]))})}))}}]

        (debug (str "user attempted to open: " (str (get-in state [:state :file]))))
        (when (:connected? @*state) (connect! "")) ;;refresh connection in opening another file
        (reset! *state state)
        state))))



(defn root-view [{:keys [file layout layout-status items output input connection connected? client scenes quads]}]
  {:fx/type :stage
   :title "Video Hub Layout Control"
   :showing true
   :on-close-request (fn [& _] (System/exit 0))
   :width  1200
   :height 700
   :scene {:fx/type :scene
           :root {:fx/type :v-box
                  :padding 30
                  :spacing 15
                  :children [{:fx/type :h-box
                              :spacing 15
                              :alignment :center-left
                              :children [{:fx/type :button
                                          :text "OPEN config..."
                                          :on-action {::event ::open-file}}
                                         {:fx/type :button
                                          :text "CONNECT"
                                          :on-action connect!}
                                         {:fx/type :label
                                          :text (str connection)}
                                         {:fx/type :label
                                          :text (str "Connected? " connected?)}]}
                             {:fx/type :h-box
                              :padding 5
                              :spacing 10
                              :children [{:fx/type :v-box
                                          :spacing 3
                                          :children [{:fx/type :label
                                                      :text "INPUT"}
                                                     {:fx/type :combo-box
                                                      :value input
                                                      :on-value-changed set-input!
                                                      :items items}]}
                                         {:fx/type :v-box
                                          :spacing 3
                                          :children [{:fx/type :label
                                                      :text "OUTPUT"}
                                                     {:fx/type :combo-box
                                                      :value output
                                                      :on-value-changed set-output!
                                                      :items items}]}
                                         {:fx/type :v-box
                                          :padding 19
                                          :spacing 3
                                          :children [{:fx/type :button
                                                      :text "UPDATE route..."
                                                      :on-action update-route!}]}
                                         {:fx/type  :v-box
                                          :padding  19
                                          :spacing  3
                                          :children [{:fx/type   :button
                                                      :text      "UNDO"
                                                      :on-action undo!}]}]}

                             {:fx/type :h-box
                              :spacing 30
                              :children [{:fx/type :v-box
                                          :spacing 5
                                          :padding 15
                                          :children [{:fx/type :label
                                                      :text "Current layout status"}
                                                     {:fx/type :text-area
                                                      :editable false
                                                      :pref-height 400
                                                      :pref-width 200
                                                      :text (with-out-str
                                                              (pprint/print-table
                                                               (sort-by second
                                                                        (into [] (map lo/inc-route-pair (vals (:layout layout-status)))))))}
                                                     {:fx/type :button
                                                      :text "SAVE current to scene"
                                                      :on-action {::event ::save-scene}}]}
                                         {:fx/type :v-box
                                          :padding 15
                                          :spacing 5
                                          :children [{:fx/type :label
                                                      :pref-width 200
                                                      :wrap-text true
                                                      :text (when (some? file) (.getName file))}
                                                     {:fx/type :text-area
                                                      :editable false
                                                      :pref-height 400
                                                      :pref-width 200
                                                      :text (with-out-str
                                                              (pprint/print-table
                                                               (sort-by second
                                                                        (into [] (vals (:layout layout))))))}
                                                     {:fx/type :button
                                                      :text "SEND layout"
                                                      :on-action change-layout!}]}
                                         {:fx/type :v-box
                                          :padding 15
                                          :spacing 10
                                          :children (concat [{:fx/type :label
                                                              :text "SAVED SCENES"}] (scenes->buttons scenes))}
                                         ;; quads
                                         {:fx/type :v-box
                                          :padding 15
                                          :spacing 10
                                          :children   (quad-data->component (:quads @*state)) }]}

                             {:fx/type :button
                              :text "SAVE configuration"
                              :on-action {::event ::save-file}}
                             {:fx/type :button
                              :text "EXIT"
                              :on-action (fn [_] (System/exit 0))}]}}})

(def renderer
  (fx/create-renderer
    :middleware (fx/wrap-map-desc #(root-view %))
    :opts {:fx.opt/map-event-handler
           (-> handle
               (fx/wrap-co-effects {:state (fx/make-deref-co-effect *state)})
               (fx/wrap-effects {:state (fx/make-reset-effect *state)
                                 :dispatch fx/dispatch-effect}))}))

(comment (fx/mount-renderer *state renderer))
