(ns video-hub.core
  (:require
   [cljfx.api :as fx]
   [video-hub.gui :as gui]
   )
  (:gen-class))


(defn -main
  [& args]
  (fx/mount-renderer gui/*state gui/renderer)
  )
