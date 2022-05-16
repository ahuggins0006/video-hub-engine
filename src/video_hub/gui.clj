(ns video-hub.gui
  (:require
   [cljfx.api :as fx]
   [manifold.stream :as s]
   [clojure.string :as str]
   [clojure.pprint :as pprint]
   [clojure.edn :as edn]
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
          :connected false
          :items []
          :client nil
          }))

(defmulti handle ::event)

(defmethod handle ::save-file [{:keys [^ActionEvent fx/event]}]
  (let [window (.getWindow (.getScene ^Node (.getTarget event)))
        chooser (doto (FileChooser.)
                  (.setTitle "Save Current Layout"))]
    (when-let [file (.showSaveDialog chooser window)]

      (spit file (with-out-str (clojure.pprint/write (:layout @*state))
                                                     :dispatch clojure.pprint/code-dispatch)))))

(defmethod handle ::open-file [{:keys [^ActionEvent fx/event]}]
  (let [window (.getWindow (.getScene ^Node (.getTarget event)))
        chooser (doto (FileChooser.)
                  (.setTitle "Open File"))]
    (when-let [file (.showOpenDialog chooser window)]
      (let [data (edn/read-string (slurp file))
            state {:state {:file file
                           :layout data
                           :output ""
                           :input ""
                           :connection (:connection data)
                           :connected? false
                           :items (map inc (sort (mapv :out (vals (:layout data)))))
                           :client nil}}]
       (reset! *state state) 
       state
        ))))

(defmethod handle ::update-route [{:keys [^ActionEvent fx/event]}]
  (s/try-put! (:client @*state) (str "VIDEO OUTPUT ROUTING:\n" (:output @*state) " " (:input @*state) "\n\n") 1000)
  (println (str "sending " (:output @*state) " " (:input @*state))))


(defn update-status! [status]
  (println (str @status))
  (comment (let [layout-status (rest (str/split status #"\n"))
                 layout (lo/status->layout layout-status)]
             (swap! *state assoc :layout layout)
             ))
  )

(defn connect! [_]
  (swap! *state assoc :client (cli/try-client (:ip (:connection @*state)) (:port (:connection @*state))))
  (swap! *state assoc :connected? (str/includes? (str (s/try-put! (:client @*state) "HELLO CLOJURE!" 1000 )) "Success"))
  (if (:connected? @*state)
    (let [status-req "OUTPUT LABELS:\n\n"
          status-period (s/periodically 2000 #(s/try-put! (:client @*state) status-req 1000))]
      (s/consume #(update-status! %) status-period)
      ))
  )

(defn set-output! [x] (swap! *state assoc :output x))

(defn set-input! [x] (swap! *state assoc :input x))

(defn root-view [{:keys [file layout items output input connection connected? client]}]
  {:fx/type :stage
   :title "Video Hub Layout Control"
   :showing true
   :width 800
   :height 600
   :scene {:fx/type :scene
           :root {:fx/type :v-box
                  :padding 30
                  :spacing 15
                  :children [{:fx/type :h-box
                              :spacing 15
                              :alignment :center-left
                              :children [{:fx/type :button
                                          :text "Open file..."
                                          :on-action {::event ::open-file}}
                                         {:fx/type :label
                                          :text (str file)}
                                         {:fx/type :button
                                          :text "Connect"
                                          :on-action connect!
                                          }
                                         {:fx/type :label
                                          :text (str connection)}
                                         {:fx/type :label
                                          :text (str "Connected? " connected?)}
                                         {:fx/type :h-box
                                          :spacing 10
                                          :children [ {:fx/type :v-box
                                                       :children [{:fx/type :label
                                                                   :text "outputs"}
                                                                  {:fx/type :combo-box
                                                                   :value output
                                                                   :on-value-changed set-output!
                                                                   :items items}]}
                                                      {:fx/type :v-box
                                                       :children [{:fx/type :label
                                                                  :text "inputs"}
                                                                 {:fx/type :combo-box
                                                                  :value input
                                                                  :on-value-changed set-input!
                                                                  :items items}]}
                                                     ]}
                                         {:fx/type :button
                                          :text "Update route..."
                                          :on-action {::event ::update-route}
                                          }
                                         {:fx/type :button
                                          :text "Save current layout"
                                          :on-action {::event ::save-file}
                                          }
                                         {:fx/type :button
                                          :text "EXIT"
                                          :on-action (fn [_] (System/exit 0))
                                          }
                                         ]}
                             {:fx/type :text-area
                              :v-box/vgrow :always
                              :editable false
                              :text (with-out-str (pprint/print-table (sort-by first (into [] (vals (:layout layout))))))}]}}})

(def renderer
  (fx/create-renderer
    :middleware (fx/wrap-map-desc #(root-view %))
    :opts {:fx.opt/map-event-handler
           (-> handle
               (fx/wrap-co-effects {:state (fx/make-deref-co-effect *state)})
               (fx/wrap-effects {:state (fx/make-reset-effect *state)
                                 :dispatch fx/dispatch-effect}))}))

(comment (fx/mount-renderer *state renderer))



