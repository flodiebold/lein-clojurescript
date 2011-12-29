(ns ^{:doc "clojurescript leiningen plugin" :author "justin barton"}
  leiningen.clojurescript
  (:use leiningen.clojurescript.util)
  (:require [clojure.string :as string]
            [clojure.java.io :as io]
            [robert.hooke :as hooke]
            leiningen.compile)
  (:import java.util.Date))

(defn- clojurescript-arg? [arg]
  (-> arg str string/trim seq first (= \{)))

(defn- cljsc [project source-dir options]
  (binding [leiningen.compile/*skip-auto-compile* true]
    (leiningen.compile/eval-in-project
     (dissoc project :source-path)
     `(cljsc/build ~source-dir ~options)
     nil nil
     '(require '[cljs.closure :as cljsc]))))

(defn- cljsc-watch [project source-dir options]
  (binding [leiningen.compile/*skip-auto-compile* true]
    (leiningen.compile/eval-in-project
     (dissoc project :source-path)
     `(lein-cljs/watch ~source-dir ~options)
     nil nil
     '(require '[leiningen.clojurescript.watch :as lein-cljs]))))

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
     (= (first args) "watch") (cljsc-watch project sourcedir opts)
     
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
