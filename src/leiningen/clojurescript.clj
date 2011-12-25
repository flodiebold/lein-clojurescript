(ns ^{:doc "clojurescript leiningen plugin" :author "justin barton"}
  leiningen.clojurescript
  (:require [clojure.string :as string]
            [clojure.java.io :as io]
            [robert.hooke :as hooke]
            leiningen.compile)
  (:import java.util.Date))

(defn- clojurescript-arg? [arg]
  (-> arg str string/trim seq first (= \{)))

(defn- clojurescript-file? [filename]
  (.endsWith (string/lower-case filename) ".cljs"))

(def getName (memfn getName))

(defn- cljsc [project source-dir options]
  (binding [leiningen.compile/*skip-auto-compile* true]
    (leiningen.compile/eval-in-project
     (dissoc project :source-path)
     `(cljsc/build ~source-dir ~options)
     nil nil
     '(require '[cljs.closure :as cljsc]))))

(defn- cljs-files [dir]
  (seq (filter (comp clojurescript-file? getName)
               (file-seq (io/file dir)))))

(defn- newer-than? [time file]
  (let [last-modified (.lastModified file)]
    (> last-modified time)))

(defn clojurescript
  "lein-clojurescript: Compiles clojurescript (.cljs) files in src to google
closure compatible javascript (.js) files.
Can use as a standalone task or can hook into the normal compile task.
Uses project name or group for outputfile. Accepts commandline args.
Run lein clojurescript watch to continually watch for changes to the source
files and recompile them.
examples: lein clojurescript
          lein compile '{:output-dir \"myout\" :output-to \"bla.js\" \\
              :optimizations :advanced}'"
  [project & args]
  (let [outputfile (str (or (:name project) (:group project)) ".js")
        opts (apply merge {:output-to outputfile :output-dir "out"}
                    (map read-string (filter clojurescript-arg? args)))
        sourcedir (or (:clojurescript-src project) (:src-dir opts) "src")]
    (cond
     (= (first args) "watch")
     (let [args (rest args)
           last-compiled (atom 0)]
       (apply clojurescript project args)
       (reset! last-compiled (System/currentTimeMillis))
       (println "Watching for changes in" sourcedir "... ")
       (while true
         (Thread/sleep 1000)
         (let [cljsfiles (cljs-files sourcedir)
               compile-needed (some (partial newer-than? @last-compiled)
                                    cljsfiles)
               starttime (.getTime (Date.))]
           (when compile-needed
             (print "Compiling updated files... ")
             (flush)
             (cljsc project sourcedir opts)
             (println (format "compiled %d files to %s/ and '%s' (took %d ms)"
                            (count cljsfiles) (:output-dir opts) (:output-to opts)
                            (- (.getTime (Date.)) starttime)))
             (reset! last-compiled (System/currentTimeMillis))))))
     :default
     (let [starttime (.getTime (Date.))]
       (print (str "Compiling clojurescript in " sourcedir " ... "))
       (flush)
       (if-let [cljsfiles (cljs-files sourcedir)]
         (do
           (cljsc project sourcedir opts)
           (println (format "compiled %d files to %s/ and '%s' (took %d ms)"
                            (count cljsfiles) (:output-dir opts) (:output-to opts)
                            (- (.getTime (Date.)) starttime))))
         (println "no cljs files found."))))))

(defn compile-clojurescript-hook [task & args]
  (let [project (first args)
        js-args (filter clojurescript-arg? (rest args))
        clj-args (remove clojurescript-arg? (rest args))]
    (apply clojurescript (cons project js-args))
    (when (or (contains? project :aot) (seq clj-args))
      (apply task (cons project clj-args)))))

(hooke/add-hook #'leiningen.compile/compile compile-clojurescript-hook)
