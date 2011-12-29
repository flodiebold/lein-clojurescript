(ns leiningen.clojurescript.watch
  (:use leiningen.clojurescript.util)
  (:require cljs.closure
            [clojure.repl :as repl])
  (:import java.util.Date))

(defn- newer-than? [time file]
  (let [last-modified (.lastModified file)]
    (> last-modified time)))

(defn watch [source-dir options]
  (let [last-compiled (atom 0)]
    (println "Watching for changes in" source-dir "... ")
    (while true
      (Thread/sleep 100)
      (let [cljsfiles (cljs-files source-dir)
            compile-needed (some (partial newer-than? @last-compiled)
                                 cljsfiles)
            starttime (.getTime (Date.))]
        (when compile-needed
          (print "Compiling updated files... ")
          (try
            (cljs.closure/build source-dir options)
            (println (format "compiled %d files to %s/ and '%s' (took %d ms)"
                             (count cljsfiles) (:output-dir options) (:output-to options)
                           (- (.getTime (Date.)) starttime)))
            (catch Exception e
              (println "failed!")
              (repl/pst e)))
          (reset! last-compiled (System/currentTimeMillis)))))))
