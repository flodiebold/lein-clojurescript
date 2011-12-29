(ns leiningen.clojurescript.util
  (:import java.io.File))

(defn- clojurescript-file? [filename]
  (.endsWith (.toLowerCase filename) ".cljs"))

(def getName (memfn getName))

(defn cljs-files [dir]
  (seq (filter (comp clojurescript-file? getName)
               (file-seq (File. dir)))))
