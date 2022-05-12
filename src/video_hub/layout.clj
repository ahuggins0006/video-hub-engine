(ns video-hub.layout
 (:require
  [clojure.string :as str]
 ))


(defn route-cmd->req
  [output input]
  (let [out (str (dec output))
        in  (str (dec input))]
    (apply str out " " in "\n"))
  )

(route-cmd->req 1 4)
;; => "\n0 3\n"

;; build layout/scene map

(def louts {:layouts
            {:l1 {:r1 {:out 1
                       :in  4}
                  :r2 {:out 2
                       :in 5}
                  }}})

(-> louts :layouts)
;; => {:l1 {:r1 {:out 1, :in 4}, :r2 {:out 2, :in 5}}}


(defn layout->routes
  [layout]
  (let [routes (vals layout)]
    (vals (first routes))
    )
  )

(layout->routes (:layouts louts))
;; => ({:out 1, :in 4} {:out 2, :in 5})

(map #(route-cmd->req (:out %) (:in %))(layout->routes (:layouts louts)))
;; => ("VIDEO OUTPUT ROUTING:\n0 3\n\n" "VIDEO OUTPUT ROUTING:\n1 4\n\n")

(apply str (map #(route-cmd->req (:out %) (:in %))(layout->routes (:layouts louts))))
;; => "VIDEO OUTPUT ROUTING:\n0 3\n\nVIDEO OUTPUT ROUTING:\n1 4\n\n"

(defn layout->routes-reqs
  [layout]
  (let [routes (->> layout
                    vals
                    first
                    layout->routes)
        reqs   (->> routes
                    (map #(route-cmd->req (:out %) (:in %)))
                    (apply str))]
    (apply str "VIDEO OUTPUT ROUTING:\n" reqs "\n")

    ))

(def multi-layout-req (layout->routes-reqs louts))
