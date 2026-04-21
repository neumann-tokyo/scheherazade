(ns scheherazade.scenario.jsonc-test
  (:require [clojure.test :refer [deftest is]]
            [scheherazade.scenario.jsonc :as jsonc]))

(deftest strips-line-comments
  (is (= {:a 1} (jsonc/parse-string "// hi\n{\"a\": 1}"))))

(deftest preserves-slash-in-string
  (is (= {:u "a//b"} (jsonc/parse-string "{\"u\": \"a//b\"}"))))
