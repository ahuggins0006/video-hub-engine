(ns video-hub.core
  (:require
   [cljfx.api :as fx]
   [manifold.stream :as s]
   [clojure.string :as str]
   [video-hub.layout :as lo]
   [video-hub.client :as cli]
   [video-hub.gui :as gui]
   )
  (:gen-class))


;;(def c1 (cli/try-client "10.10.0.30" 9990))
;;c1
;;
;;
;;(def simple-routing "VIDEO OUTPUT ROUTING:\n 0 3\n\n")
;;(def simple-routing "VIDEO OUTPUT ROUTING:\n 0 3 \n 2 5\n\n")
;;(def req-output-routing "VIDEO OUTPUT ROUTING:\n\n")
;;(def req-output-labels "OUTPUT LABELS:\n\n")
;;(def ping "PING:\n\n")
;;(s/put! c1 ping)
;;(s/put! c1 req-output-routing)
;;(s/take! c1)
;; => << "PROTOCOL PREAMBLE:\nVersion: 2.7\n\nVIDEOHUB DEVICE:\nDevice present: true\nModel name: Blackmagic Smart Videohub 20 x 20\nFriendly name: Smart Videohub 20 x 20\nUnique ID: 7C2E0DA55C76\nVideo inputs: 20\nVideo processing units: 0\nVideo outputs: 20\nVideo monitoring outputs: 0\nSerial ports: 0\n\nINPUT LABELS:\n0 Input 1\n1 Input 2\n2 Input 3\n3 Input 4\n4 Input 5\n5 Input 6\n6 Input 7\n7 Input 8\n8 Input 9\n9 Input 10\n10 Input 11\n11 Input 12\n12 Input 13\n13 Input 14\n14 Input 15\n15 Input 16\n16 Input 17\n17 Input 18\n18 Input 19\n19 Input 20\n\nOUTPUT LABELS:\n0 Output 1\n1 Output 2\n2 Output 3\n3 Output 4\n4 Output 5\n5 Output 6\n6 Output 7\n7 Output 8\n8 Output 9\n9 Output 10\n10 Output 11\n11 Output 12\n12 Output 13\n13 Output 14\n14 Output 15\n15 Output 16\n16 Output 17\n17 Output 18\n18 Output 19\n19 Output 20\n\nVIDEO OUTPUT LOCKS:\n0 U\n1 U\n2 U\n3 U\n4 U\n5 U\n6 U\n7 U\n8 U\n9 U\n10 U\n11 U\n12 U\n13 U\n14 U\n15 U\n16 U\n17 U\n18 U\n19 U\n\nVIDEO OUTPUT ROUTING:\n0 3\n1 4\n2 5\n3 1\n4 1\n5 1\n6 1\n7 1\n8 1\n9 1\n10 1\n11 1\n12 1\n13 1\n14 1\n15 1\n16 17\n17 1\n18 1\n19 0\n\n" >>
;;(s/put! c1 req-output-labels)
;;(s/put! c1 ping)
;;
;;(s/put! c1 lo/multi-layout-req)
;;;; network connections
;;
;;(s/consume #(prn 'message! %) c1)
;;
;;;;test periodically
;;
;;an order
;;(do(s/consume #(println "client: "%) c1)
;;   (def status-period (s/periodically 2000 #(s/try-put! c1 req-output-routing 1000)))
;;   (s/consume #(println %) status-period))
;;
;;(def ping-period (s/periodically 1000 #(cli/ping-something "google.com")))
;;
;;(s/consume #(prn 'message! %) ping-period)
;;
;;
;;(s/close! c1)
;;(s/close! ping-period)
;;(s/close! status-period)

(defn -main
  [& args]
  (fx/mount-renderer gui/*state gui/renderer)
  )
