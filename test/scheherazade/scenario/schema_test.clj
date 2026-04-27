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

(deftest font-size-must-be-positive-integer
  (let [base (assoc-in valid [:timeline 0 :texts]
                       [{:text "hello"
                         :position {:x 0 :y 0 :w 100 :h 40}}])]
    (is (nil? (schema/validate (assoc-in base [:timeline 0 :texts 0 :font_size] 24))))
    (is (some? (schema/validate (assoc-in base [:timeline 0 :texts 0 :font_size] 0))))
    (is (some? (schema/validate (assoc-in base [:timeline 0 :texts 0 :font_size] -1))))
    (is (some? (schema/validate (assoc-in base [:timeline 0 :texts 0 :font_size] "24pt"))))))
