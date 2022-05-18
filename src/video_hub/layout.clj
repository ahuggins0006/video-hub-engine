(ns video-hub.layout
 (:require
  [clojure.string :as str]
  [clojure.pprint :as pprint]
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
(def louts
  {:layout {1 {:out 1
             :in  4}
        2 {:out 2
             :in 5}
        }})

(defn layout->routes
  [layout]
  (let [routes (vals layout)]
    routes))

(layout->routes (:layout louts))
;; => ({:out 1, :in 4} {:out 2, :in 5})

(map #(route-cmd->req (:out %) (:in %))(layout->routes (:layout louts)))
;; => ("0 3\n" "1 4\n")

(apply str (map #(route-cmd->req (:out %) (:in %))(layout->routes (:layout louts))))
;; => "0 3\n1 4\n"

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
multi-layout-req

(def sample-status "VIDEO OUTPUT ROUTING:\n0 3\n1 1\n2 1\n3 1\n4 1\n5 1\n6 1\n7 1\n8 1\n9 1\n10 1\n11 1\n12 1\n13 1\n14 1\n15 1\n16 1\n17 1\n18 1\n19 0\n\n" )

(def sample (with-out-str (pprint/pprint (str/split sample-status #"\n"))))

(def sample-layout (rest (str/split sample-status #"\n")))

sample-layout
;; => ("0 3" "1 1" "2 1" "3 1" "4 1" "5 1" "6 1" "7 1" "8 1" "9 1" "10 1" "11 1" "12 1" "13 1" "14 1" "15 1" "16 1" "17 1" "18 1" "19 0")

(def one-layout '("0 3"))

(defn two-str->tuple [s] (map #(Integer/parseInt %)(str/split s #" ")))


(defn status->layout [lo-status]
  (let [route-keys (map #(inc (.indexOf lo-status %)) lo-status)
        routes     (map (fn [r] {:out (first (two-str->tuple r))
                                 :in (last (two-str->tuple r))}) lo-status)]
    (into (sorted-map) (zipmap route-keys routes))))
(status->layout sample-layout)
;; => {1 {:out 0, :in 3}, 2 {:out 1, :in 1}, 3 {:out 2, :in 1}, 4 {:out 3, :in 1}, 5 {:out 4, :in 1}, 6 {:out 5, :in 1}, 7 {:out 6, :in 1}, 8 {:out 7, :in 1}, 9 {:out 8, :in 1}, 10 {:out 9, :in 1}, 11 {:out 10, :in 1}, 12 {:out 11, :in 1}, 13 {:out 12, :in 1}, 14 {:out 13, :in 1}, 15 {:out 14, :in 1}, 16 {:out 15, :in 1}, 17 {:out 16, :in 1}, 18 {:out 17, :in 1}, 19 {:out 18, :in 1}, 20 {:out 19, :in 0}}

(def sample-status (with-out-str (pprint/print-table (into [](vals (status->layout sample-layout))))))

(defn status->table
  [status]
  (with-out-str (pprint/print-table (into [](vals (status->layout status)))))
  )

(status->table sample-layout)
;; => "\n| :out | :in |\n|------+-----|\n|    0 |   3 |\n|    1 |   1 |\n|    2 |   1 |\n|    3 |   1 |\n|    4 |   1 |\n|    5 |   1 |\n|    6 |   1 |\n|    7 |   1 |\n|    8 |   1 |\n|    9 |   1 |\n|   10 |   1 |\n|   11 |   1 |\n|   12 |   1 |\n|   13 |   1 |\n|   14 |   1 |\n|   15 |   1 |\n|   16 |   1 |\n|   17 |   1 |\n|   18 |   1 |\n|   19 |   0 |\n"

(defn inc-route-pair [p] {:out (inc (:out p)) :in (inc (:in p))})

(map inc-route-pair (vals (status->layout sample-layout)))
;; => ({:out 1, :in 4} {:out 2, :in 2} {:out 3, :in 2} {:out 4, :in 2} {:out 5, :in 2} {:out 6, :in 2} {:out 7, :in 2} {:out 8, :in 2} {:out 9, :in 2} {:out 10, :in 2} {:out 11, :in 2} {:out 12, :in 2} {:out 13, :in 2} {:out 14, :in 2} {:out 15, :in 2} {:out 16, :in 2} {:out 17, :in 2} {:out 18, :in 2} {:out 19, :in 2} {:out 20, :in 1})





(def save-test {:layout {1 {:out 0, :in 3}, 2 {:out 1, :in 1}, 3 {:out 2, :in 1}, 4 {:out 3, :in 1}, 5 {:out 4, :in 1}, 6 {:out 5, :in 1}, 7 {:out 6, :in 1}, 8 {:out 7, :in 1}, 9 {:out 8, :in 1}, 10 {:out 9, :in 1}, 11 {:out 10, :in 1}, 12 {:out 11, :in 1}, 13 {:out 12, :in 1}, 14 {:out 13, :in 1}, 15 {:out 14, :in 1}, 16 {:out 15, :in 1}, 17 {:out 16, :in 1}, 18 {:out 17, :in 1}, 19 {:out 18, :in 1}, 20 {:out 19, :in 0}}})


(map inc-route-pair (vals (:layout save-test)))

(map #(update-in (:layout save-test) [%] inc-route-pair) save-test)

(assoc save-test :layout (into {} (for [v (:layout save-test)] {(key v) (inc-route-pair (val v))})))
