(ns video-hub.gui
  (:require
   [cljfx.api :as fx]
   [clojure.pprint :as pprint]
   [clojure.edn :as edn]
   [video-hub.layout :as lo]
   )
  (:import [javafx.stage FileChooser]
           [javafx.event ActionEvent]
           [javafx.scene Node])
  )

(def *state
  (atom {:file nil
         :layout nil}))

(defmulti handle ::event)
(sort-by first (into [](vals (:layout (edn/read-string (slurp "/data/fcs/projects/video-hub-engine/resources/layouts.edn"))))))
(defmethod handle ::open-file [{:keys [^ActionEvent fx/event]}]
  (let [window (.getWindow (.getScene ^Node (.getTarget event)))
        chooser (doto (FileChooser.)
                  (.setTitle "Open File"))]
    (when-let [file (.showOpenDialog chooser window)]
      {:state {:file file :layout (edn/read-string (slurp file))}})))

(defmethod handle ::exit [{:keys [^ActionEvent fx/event]}]
  (System/exit 0))

(defn root-view [{:keys [file layout]}]
  {:fx/type :stage
   :title "Textual file viewer"
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
