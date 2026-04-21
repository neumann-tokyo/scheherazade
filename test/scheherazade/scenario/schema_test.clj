(ns scheherazade.scenario.schema-test
  (:require [clojure.test :refer [deftest is]]
            [scheherazade.scenario.schema :as schema]))

(def valid
  {:title "t"
   :description "d"
   :timeline [{:id "000001" :videos ["a.png"]}]})

(deftest accepts-minimal
  (is (nil? (schema/validate valid))))

(deftest rejects-bad-id
  (is (some? (schema/validate (assoc-in valid [:timeline 0 :id] "bad")))))
