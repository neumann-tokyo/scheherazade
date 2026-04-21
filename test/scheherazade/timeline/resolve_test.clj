(ns scheherazade.timeline.resolve-test
  (:require [clojure.test :refer [deftest is]]
            [scheherazade.timeline.resolve :as r]))

(def ctx
  {:path->duration-ms {"a.png" 999999
                       "v.mp4" 10000
                       "s.wav" 3000}})

(deftest audio-drives-when-both
  (let [scenario {:timeline [{:id "000001"
                               :videos ["v.mp4"]
                               :audios ["s.wav"]}]}
        [row] (r/resolve-scenario scenario ctx)]
    (is (= 3000 (:duration-ms row)))))

(deftest image-only-default
  (let [scenario {:timeline [{:id "000001" :videos ["a.png"]}]}
        [row] (r/resolve-scenario scenario ctx)]
    (is (= r/default-image-only-ms (:duration-ms row)))))

(deftest video-clip-ms
  (let [scenario {:timeline [{:id "000001" :videos ["v.mp4"]}]}
        [row] (r/resolve-scenario scenario ctx)]
    (is (= 10000 (:duration-ms row)))
    (is (= 1 (count (:video-clips row))))))
