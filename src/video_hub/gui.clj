(ns video-hub.gui
  (:require
   [cljfx.api :as fx]
   [manifold.stream :as s]
   [clojure.string :as str]
   [clojure.pprint :as pprint]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
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
          :connected? false   ;; establised connection with video hub
          :output nil         ;; placeholder for output combo value
          :input  nil         ;; placeholder for output combo value
          :connection nil     ;; connection info
          :items []           ;; individual combo items
          :scenes {}          ;; user-defined scenes
          :client nil         ;; current client used for connection with video hub
          :layout nil         ;; the layout includes a default layout, connection, and scenes
          :layout-status nil  ;; the current layout status as given by video hub
          }))

;; the current stack of layouts for undos
(def *undo-stack (atom ()))

;; add watch to update *undo-stack on state change
(add-watch *state :app-state-watcher
           (fn [key atom old-state new-state]
             (debug (str "snapshot: " (str old-state)))
             (swap! *undo-stack conj (:layout-status old-state)))
           )

(defn load-scene!
  "Loads a scene-file on respective button press."
  [scene-file]
  (let [data (edn/read-string (slurp scene-file))]
    (swap! *state assoc :layout (assoc {} :layout (:layout data)
                                       :connection (:connection @*state)
                                       :scenes (:scenes @*state))                        :file scene-file)))

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
        p (s/periodically 500 #(s/try-put! c req-output-routing 1000))]
    (s/consume #(if (and (str/includes? % "\n\n") (str/includes? % "ROUTING")) (update-layout-status! %)) c)
    (s/consume #(deref %) p)))

(defn connect!
  "Establishes a connection at specified ip and port given from config file. Also triggers update-status!"
  [_]
  (let [client (cli/try-client (:ip (:connection @*state)) (:port (:connection @*state)))]
    (swap! *state assoc :connected? (str/includes? (str (s/try-put! client "PING:\n\n" 1000)) "Success"))
    (if (:connected? @*state)
      (do
        (swap! *state assoc :client client)
        (update-status!)
        (debug (str "connection established: " (:connection @*state)))
        )
      (swap! *state assoc :connected? false))))

(defn undo! []
  ;; pop @*undo-stack
  ;; update
  (when (and (not (empty? @*undo-stack))
             (:connected? @*state))
    (connect! "")
    (s/try-put! (:client @*state) (lo/layout->routes-reqs {:layout (pop @*undo-stack)}) 1000))
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
    (s/try-put! (:client @*state) (str "VIDEO OUTPUT ROUTING:\n" (dec (:output @*state)) " " (dec (:input @*state)) "\n\n") 1000)))

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
      (let [name (first (str/split (last (str/split (str file) #"/")) #"\."))]
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
                           :items (range 1 (inc (count (vals (:layout data)))))
                           :scenes (:scenes data)
                           }}]
       (debug (str "user attempted to open: " (str (get-in state [:state :file]))))
       (when (:connected? @*state) (connect! "")) ;;refresh connection in opening another file
       (reset! *state state)
       state
        ))))



(defn root-view [{:keys [file layout layout-status items output input connection connected? client scenes]}]
  {:fx/type :stage
   :title "Video Hub Layout Control"
   :showing true
   :on-close-request (fn [& _] (System/exit 0))
   :width  900
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
                                          :text (str "Connected? " connected?)}
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
                                                      :padding 18
                                                      :spacing 3
                                                      :children [{:fx/type :button
                                                                  :text "UPDATE route..."
                                                                  :on-action update-route!}]}
                                                     ]}
                                         ]}
                             {:fx/type :h-box
                              :spacing 30
                              :children [{:fx/type :v-box
                                          :padding 15
                                          :spacing 5
                                          :children [{:fx/type :label
                                                      :pref-width 200
                                                      :wrap-text true
                                                      :text (str (last (str/split (str file) #"/")))}
                                                     {:fx/type :text-area
                                                      :editable false
                                                      :pref-height 400
                                                      :pref-width 200
                                                      :text (with-out-str
                                                              (pprint/print-table
                                                               (sort-by first
                                                                        (into [] (vals (:layout layout))))))}
                                                     {:fx/type :button
                                                      :text "SEND layout"
                                                      :on-action change-layout!}
                                                     ]}
                                         {:fx/type :v-box
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
                                                               (sort-by first
                                                                        (into [] (map lo/inc-route-pair (vals (:layout layout-status)))))))}
                                                     {:fx/type :button
                                                      :text "SAVE current to scene"
                                                      :on-action {::event ::save-scene}}
                                                     ]}
                                         {:fx/type :v-box
                                          :padding 15
                                          :spacing 10
                                          :children (concat [{:fx/type :label
                                                            :text "SAVED SCENES"}] (scenes->buttons scenes))}

                                         ]}

                             {:fx/type :button
                              :text "SAVE configuration"
                              :on-action {::event ::save-file}}
                             {:fx/type :button
                              :text "EXIT"
                              :on-action (fn [_] (System/exit 0))}

                             ;; TODO temporary test button to toggle solo mode on a very specific multi-4
                             {:fx/type   :button
                              :text      "TOGGLE Solo mode"
                              :on-action (fn [_]((let [c             (cli/try-client "192.168.50.150" 9990)
                                                       solo-enabled? (Boolean/parseBoolean (last (str/split ((str/split-lines @(s/take! c)) 47) #" ")))]

                                                   (do (s/try-put! c (str "CONFIGURATION:\n Solo enabled: "
                                                                          (not solo-enabled?)
                                                                          "\n\n") 1000)
                                                       (.close c)
                                                       ))
                                                 ))
                              }
                             ]}}})

(def renderer
  (fx/create-renderer
    :middleware (fx/wrap-map-desc #(root-view %))
    :opts {:fx.opt/map-event-handler
           (-> handle
               (fx/wrap-co-effects {:state (fx/make-deref-co-effect *state)})
               (fx/wrap-effects {:state (fx/make-reset-effect *state)
                                 :dispatch fx/dispatch-effect}))}))

(comment (fx/mount-renderer *state renderer))
