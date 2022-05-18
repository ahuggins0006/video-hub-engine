(ns video-hub.gui
  (:require
   [cljfx.api :as fx]
   [manifold.stream :as s]
   [clojure.string :as str]
   [clojure.pprint :as pprint]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
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
          :client nil
          :layout nil
          :layout-status nil
          }))

(declare connect!)
(defmulti handle ::event)

(defn change-layout! [_]
  (when (:connected? @*state)
    (println (:layout (:layout @*state)))
    (s/try-put! (:client @*state) (lo/layout->routes-reqs {:layout (:layout (:layout @*state))}) 1000)))

(defmethod handle ::save-file [{:keys [^ActionEvent fx/event]}]
  (let [window (.getWindow (.getScene ^Node (.getTarget event)))
        chooser (doto (FileChooser.)
                  (.setTitle "Save Current Layout"))]
    (when-let [file (.showSaveDialog chooser window)]

      (spit file (with-out-str (clojure.pprint/write (assoc (:layout-status @*state) :layout (into {} (for [v (:layout (:layout-status @*state))]
                                                                                                        {(key v) (lo/inc-route-pair (val v))})))
                                                    )
                               :dispatch clojure.pprint/code-dispatch)))))

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
                           :items (sort (mapv :out (vals (:layout data))))
                           }}]
       (when (:connected? @*state) (connect! "")) ;;refresh connection in opening another file
       (reset! *state state)
       state
        ))))

(defn update-layout-status! [status]
  (when (and (not (or (str/includes? status "LOCKS") (str/includes? status "PRELUDE"))) (str/includes? status "ROUTING"))
    (let [layout-status (rest (str/split status #"\n"))
          layout {:layout (lo/status->layout layout-status)
                  :connection (:connection @*state)}]
      (when (= (count (:layout layout)) (count (:items @*state)))
        (swap! *state assoc :layout-status layout)
        ))))

(defn update-status! []
  (let [c (:client @*state)
        req-output-routing "VIDEO OUTPUT ROUTING:\n\n"
        ]
    (s/consume #(if (str/includes? % "ROUTING") (update-layout-status! %)) c)
    (s/consume #(%) (s/periodically 1000 #(s/try-put! c req-output-routing 1000)))
    )
  )

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
    (s/try-put! (:client @*state) (str "VIDEO OUTPUT ROUTING:\n" (dec (:output @*state)) " " (dec (:input @*state)) "\n\n") 1000)
    (connect! "")
    ))


(defn set-output! [x] (swap! *state assoc :output x))

(defn set-input! [x] (swap! *state assoc :input x))


(defn root-view [{:keys [file layout layout-status items output input connection connected? client]}]
  {:fx/type :stage
   :title "Video Hub Layout Control"
   :showing true
   :width 1600
   :height 1200
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
                                                                  :text "Outputs"}
                                                                 {:fx/type :combo-box
                                                                  :value output
                                                                  :on-value-changed set-output!
                                                                  :items items}]}
                                                     {:fx/type :v-box
                                                      :spacing 3
                                                      :children [{:fx/type :label
                                                                  :text "Inputs"}
                                                                 {:fx/type :combo-box
                                                                  :value input
                                                                  :on-value-changed set-input!
                                                                  :items items}]}]}
                                         {:fx/type :button
                                          :text "Update route..."
                                          :on-action update-route!}
                                         ]}
                             {:fx/type :h-box
                              :spacing 30
                              :children [{:fx/type :v-box
                                          :spacing 5
                                          :children [{:fx/type :label
                                                      :pref-width 200
                                                      :wrap-text true
                                                      :text (str file)}
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
                                                      :on-action {::event ::save-file}}
                                                     ]}]}
                             {:fx/type :button
                              :text "EXIT"
                              :on-action (fn [_] (System/exit 0))}
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
