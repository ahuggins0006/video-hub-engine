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
;;
;;
;;(def simple-routing "VIDEO OUTPUT ROUTING:\n 0 3\n\n")
;;(def simple-routing "VIDEO OUTPUT ROUTING:\n 0 3 \n 2 5\n\n")
;;(def req-output-routing "VIDEO OUTPUT ROUTING:\n\n")
;;(def req-output-labels "OUTPUT LABELS:\n\n")
;;(def ping "PING:\n\n")
;;(s/put! c1 simple-routing)
;;(s/put! c1 req-output-routing)
;;(s/put! c1 req-output-labels)
;;(s/put! c1 ping)
;;
;;(s/put! c1 lo/multi-layout-req)
;;;; network connections
;;
;;(s/consume #(prn 'message! %) c1)
;;
;;;;test periodically
;;(def status-period (s/periodically 2000 #(s/try-put! c1 req-output-labels 1000)))
;;(s/consume #(prn 'message! %) status-period)
;;
;;(s/consume #(println %) status-period)
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


