(ns video-hub.core-test
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [video-hub.core :refer :all]
            [video-hub.layout :as lo]))

(deftest route-cmd->req-test
  (testing "Route translation"
    (is (= (lo/route-cmd->req 1 4) "3 0\n"))))

(def louts
  {:layout {1 {:in 1
               :out  4}
            2 {:in 2
               :out 5}
            }})

(deftest layout->routes-test
  (testing "pulling routes from a layout"
    (is (= (lo/layout->routes (:layout louts)) '({:in 1, :out 4} {:in 2, :out 5})))))

(deftest layout->routes-reqs-test
  (testing "building comlete layout update request string")
  (is (= (lo/layout->routes-reqs louts) "VIDEO OUTPUT ROUTING:\n3 0\n4 1\n\n")))

(deftest two-str->tuple-test
  (testing "conversion from two element string to two element tuple")
  (is (= (lo/two-str->tuple "0 3") '(0 3))))

(def sample-status (rest (str/split "VIDEO OUTPUT ROUTING:\n0 3\n1 1\n2 1\n3 1\n4 1\n5 1\n6 1\n7 1\n8 1\n9 1\n10 1\n11 1\n12 1\n13 1\n14 1\n15 1\n16 1\n17 1\n18 1\n19 0\n\n" #"\n")) )

(deftest status->layout-test
  (testing "conversion of layout status string to a layout")
  (is (= (lo/status->layout sample-status) {1 {:out 0, :in 3}, 2 {:out 1, :in 1}, 3 {:out 2, :in 1}, 4 {:out 3, :in 1}, 5 {:out 4, :in 1}, 6 {:out 5, :in 1}, 7 {:out 6, :in 1}, 8 {:out 7, :in 1}, 9 {:out 8, :in 1}, 10 {:out 9, :in 1}, 11 {:out 10, :in 1}, 12 {:out 11, :in 1}, 13 {:out 12, :in 1}, 14 {:out 13, :in 1}, 15 {:out 14, :in 1}, 16 {:out 15, :in 1}, 17 {:out 16, :in 1}, 18 {:out 17, :in 1}, 19 {:out 18, :in 1}, 20 {:out 19, :in 0}})))

(deftest inc-route-pair-test
  (testing "incrementing a in/out pairs for display")
  (is (= (lo/inc-route-pair {:in 0 :out 0}) {:in 1, :out 1})))
