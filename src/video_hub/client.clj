(ns video-hub.client
  (:require
   [aleph.tcp :as tcp]
   [gloss.io :as io]
   [gloss.core :as gloss]
   [manifold.deferred :as d]
   [manifold.stream :as s]
   [clojure.java.shell :as shell]
   [clojure.string :as str]
   ))

;;(def protocol (gloss/compile-frame (gloss/string :ascii :delimeters ["\n\n"])))

(def protocol (gloss/compile-frame (gloss/string :ascii :delimeters [])))

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

(defn ping-something [something]
  (case (.toLowerCase (System/getProperty "os.name"))
    "linux"                     (#(if (= (second %) 0) true false) (first (shell/sh "ping" "-c" "1" "-W" "3" something)))
    ("windows 10"
     "windows 11") (str/includes? (:out (shell/sh "cmd" "/C" "powershell.exe" "Test-Connection" something "-Quiet" "-Count" "1")) "True")))
