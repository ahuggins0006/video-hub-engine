(ns video-hub.gui
  (:require
   [cljfx.api :as fx]
   [manifold.stream :as s]
   [clojure.pprint :as pprint]
   [clojure.string :as str]
   [clojure.edn :as edn]
   [video-hub.layout :as lo]
   [video-hub.client :as cli]
   )
  (:import [javafx.stage FileChooser]
           [javafx.event ActionEvent]
           [javafx.scene Node])
  )


(def *state
  (atom  {:file nil
          :connected? false
          :output nil
          :input  nil
          :connection nil
          :connected false
          :items []
          }))

(:connected? @*state)


(def data (edn/read-string (slurp "/data/fcs/projects/video-hub-engine/resources/layouts.edn")))
(map inc (sort (mapv :out (vals (:layout data)))))

(defmulti handle ::event)

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

(defmethod handle ::exit [{:keys [^ActionEvent fx/event]}]
  (System/exit 0))

(defmethod handle ::update-route [{:keys [^ActionEvent fx/event]}]
  (s/try-put! (:client @*state) (str "VIDEO OUTPUT ROUTING:\n" (:output @*state) " " (:input @*state) "\n\n") 1000)
  (println (str "sending " (:output @*state) " " (:input @*state))))

(defn connect! [_]
  (swap! *state assoc :client (cli/try-client (:ip (:connection @*state)) (:port (:connection @*state))))
  (swap! *state assoc :connected? (str/includes? (s/try-put! (:client @*state) "HELLO CLOJURE!" 1000 ) "Success"))
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

(fx/mount-renderer *state renderer)

(renderer)

