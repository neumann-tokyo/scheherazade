(ns scheherazade.main-test
  (:require [clojure.test :refer [deftest is]]
            [scheherazade.main :as main]))

(deftest parse-cli-supports-generate-and-dictionary
  (let [parsed (#'main/parse-cli ["--generate" "scenario.jsonc" "-o" "out.mp4" "--dictionary" "dict.json"])]
    (is (= :generate (:mode parsed)))
    (is (= "scenario.jsonc" (:scenario parsed)))
    (is (= "out.mp4" (:out parsed)))
    (is (= "dict.json" (:dictionary parsed)))))

(deftest parse-cli-defaults-render-mode
  (let [parsed (#'main/parse-cli ["scenario.jsonc"])]
    (is (= :render (:mode parsed)))
    (is (= "scenario.jsonc" (:scenario parsed)))))

(deftest default-dictionary-path-follows-scenario-dir
  (is (= "/tmp/a/dictionary.json"
         (#'main/default-dictionary-path "/tmp/a/scenario.jsonc"))))
