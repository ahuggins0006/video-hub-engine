(ns video-hub.core
  (:require
   [aleph.tcp :as tcp]
   [gloss.io :as io]
   [gloss.core :as gloss]
   [manifold.deferred :as d]
   [manifold.stream :as s]
   [clojure.string :as str]
   )
  (:gen-class))

(def protocol (gloss/compile-frame (gloss/string :ascii)))

(defn wrap-duplex-stream
  [protocol s]
  (let [out (s/stream)]
    (s/connect
     (s/map #(io/encode protocol %) out)
     s)
    (s/splice
     out
     (io/decode-stream s protocol))))

(defn client
  [host port]
  (d/chain (tcp/client {:host host, :port port})
           #(wrap-duplex-stream protocol %)))

(defn try-client [ip port] (try @(client ip port) (catch Exception e (str "caught exception: " (.getMessage e)))))

(def c1 (try-client "10.10.0.30" 9990))
c1

(def simple-routing "VIDEO OUTPUT ROUTING:\n 0 3\n")
(s/put! c1 simple-routing)
(s/take! c1)

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!"))



