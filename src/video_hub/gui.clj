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

(def *state
  (atom  {:file nil
          :connected? false
          :output nil
          :input  nil
          :connection nil
          :items []
          :scenes {}
          :client nil
          :layout nil
          :layout-status nil
          }))

(defn load-scene! [scene-file]
  (let [data (edn/read-string (slurp scene-file))]
    (swap! *state assoc :layout (assoc {} :layout (:layout data)
                                       :connection (:connection @*state)
                                       :scenes (:scenes @*state)))))
(defn update-layout-status! [status]
  (comment (debug (str "received: " status)))
  (when (and (not (or (str/includes? status "LOCKS") (str/includes? status "PRELUDE"))) (str/includes? status "ROUTING"))
    (let [layout-status (rest (str/split status #"\n"))
          layout {:layout (lo/status->layout layout-status)
                  :connection (:connection @*state)}]
      (when (= (count (:layout layout)) (count (:items @*state)))
        (swap! *state assoc :layout-status layout)))))

(defn update-status! []
  (let [c (:client @*state)
        req-output-routing "VIDEO OUTPUT ROUTING:\n\n"
        p (s/periodically 500 #(s/try-put! c req-output-routing 1000))]
    (s/consume #(if (and (str/includes? % "\n\n") (str/includes? % "ROUTING")) (update-layout-status! %)) c)
    (s/consume #(deref %) p)))

(defn connect! [_]
  (let [client (cli/try-client (:ip (:connection @*state)) (:port (:connection @*state)))]
    (swap! *state assoc :connected? (str/includes? (str (s/try-put! client "PING:\n\n" 1000)) "Success"))
    (if (:connected? @*state)
      (do
        (swap! *state assoc :client client)
        (update-status!))
      (swap! *state assoc :connected? false))))

(defn update-route! [_]
  (when (and (:connected? @*state)
             (some? (:output @*state))
             (some? (:input @*state)))
    (println (str "VIDEO OUTPUT ROUTING:\n" (dec (:output @*state)) " " (dec (:input @*state)) "\n\n"))
    (connect! "")
    (s/try-put! (:client @*state) (str "VIDEO OUTPUT ROUTING:\n" (dec (:output @*state)) " " (dec (:input @*state)) "\n\n") 1000)))


(defn set-output! [x] (swap! *state assoc :output x))

(defn set-input! [x] (swap! *state assoc :input x))

(defn name->button [data]
  (let [name (name (key data))
        scene-file (val data)]
    {:fx/type :button
     :text name
     :on-action (fn [_] (load-scene! scene-file) (println @*state))}))

(defn scenes->buttons [scenes] (if (not-empty scenes)
                                 (->> scenes
                                      (mapv name->button))
                                 []))

(defn change-layout! [_]
  (when (:connected? @*state)
    (connect! "")
    (s/try-put! (:client @*state) (lo/layout->routes-reqs {:layout (:layout (:layout @*state))}) 1000)))


(defmulti handle ::event)

(defmethod handle ::save-file [{:keys [^ActionEvent fx/event]}]
  (let [window (.getWindow (.getScene ^Node (.getTarget event)))
        chooser (doto (FileChooser.)
                  (.setTitle "Save Current Configuration"))]
    (when-let [file (.showSaveDialog chooser window)]

      (spit file (with-out-str (clojure.pprint/write {:layout (:layout (:layout @*state))
                                                      :connection (:connection @*state)
                                                      :scenes (:scenes @*state)}
                                                    )
                               :dispatch clojure.pprint/code-dispatch)))))

(defmethod handle ::save-layout [{:keys [^ActionEvent fx/event]}]
  (let [window (.getWindow (.getScene ^Node (.getTarget event)))
        chooser (doto (FileChooser.)
                  (.setTitle "Save Current Layout"))]
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
       (when (:connected? @*state) (connect! "")) ;;refresh connection in opening another file
       (reset! *state state)
       state
        ))))



(defn root-view [{:keys [file layout layout-status items output input connection connected? client scenes]}]
  {:fx/type :stage
   :title "Video Hub Layout Control"
   :showing true
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
                                          :text "Open config..."
                                          :on-action {::event ::open-file}}
                                         {:fx/type :button
                                          :text "Connect"
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
                                                                  :text "Input"}
                                                                 {:fx/type :combo-box
                                                                  :value input
                                                                  :on-value-changed set-input!
                                                                  :items items}]}
                                                     {:fx/type :v-box
                                                      :spacing 3
                                                      :children [{:fx/type :label
                                                                  :text "Outpt"}
                                                                 {:fx/type :combo-box
                                                                  :value output
                                                                  :on-value-changed set-output!
                                                                  :items items}]}]}
                                         {:fx/type :button
                                          :text "Update route..."
                                          :on-action update-route!}
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
                                                      :text "Send layout"
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
                                                      :text "Save current layout"
                                                      :on-action {::event ::save-layout}}
                                                     ]}
                                         
                                         {:fx/type :v-box
                                          :padding 15
                                          :spacing 10
                                          :children (concat [{:fx/type :label
                                                            :text "Saved Layouts"}] (scenes->buttons scenes))}]}
                             {:fx/type :button
                              :text "EXIT"
                              :on-action (fn [_] (System/exit 0))}
                             {:fx/type :button
                              :text "SAVE"
                              :on-action {::event ::save-file}}
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
