#!/usr/bin/env bb

(require '[clojure.test :as t]
         '[babashka.classpath :as cp])

(cp/add-classpath "src:test")

(require 'scheherazade.scenario.jsonc-test
         'scheherazade.scenario.schema-test
         'scheherazade.timeline.resolve-test)

(def results
  (t/run-tests 'scheherazade.scenario.jsonc-test
               'scheherazade.scenario.schema-test
               'scheherazade.timeline.resolve-test))

(let [{:keys [fail error]} results]
  (when (pos? (+ fail error))
    (System/exit 1)))
