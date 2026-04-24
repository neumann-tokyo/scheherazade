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

(deftest video-looping
  (let [ctx {:path->duration-ms {"v1.mp4" 1000
                                 "v2.mp4" 1000
                                 "bgm.wav" 5000}}
        scenario {:timeline [{:id "000001"
                              :videos ["v1.mp4" "v2.mp4"]
                              :audios ["bgm.wav"]}]}
        [row] (r/resolve-scenario scenario ctx)
        clips (:video-clips row)]
    (is (= 5000 (:duration-ms row)))
    (is (= 5 (count clips)))
    (is (= ["v1.mp4" "v2.mp4" "v1.mp4" "v2.mp4" "v1.mp4"]
           (mapv :path clips)))
    (is (= [1000 1000 1000 1000 1000]
           (mapv :duration-ms clips)))))

(deftest video-looping-with-trim
  (let [ctx {:path->duration-ms {"v1.mp4" 3000
                                 "v2.mp4" 3000
                                 "bgm.wav" 7000}}
        scenario {:timeline [{:id "000001"
                              :videos ["v1.mp4" "v2.mp4"]
                              :audios ["bgm.wav"]}]}
        [row] (r/resolve-scenario scenario ctx)
        clips (:video-clips row)]
    (is (= 7000 (:duration-ms row)))
    (is (= 3 (count clips)))
    (is (= ["v1.mp4" "v2.mp4" "v1.mp4"] (mapv :path clips)))
    (is (= [3000 3000 1000] (mapv :duration-ms clips)))))

(deftest children-sequential-duration
  (let [ctx {:path->duration-ms {"bg.png" 999999
                                 "c1.mp4" 2000
                                 "c2.mp4" 3000
                                 "s1.wav" 3000
                                 "s2.wav" 2000}}
        scenario {:timeline [{:id "000001"
                              :videos ["bg.png"]
                              :children [{:id "000002"
                                          :videos ["c1.mp4"]
                                          :audios ["s1.wav"]}
                                         {:id "000003"
                                          :videos ["c2.mp4"]
                                          :audios ["s2.wav"]}]}]}
        [row] (r/resolve-scenario scenario ctx)]
    (is (= 5000 (:duration-ms row)) "children are sequential: 3000 + 2000")))

(deftest audio-looping
  (let [ctx {:path->duration-ms {"bg.png" 999999
                                 "c.mp4" 2000
                                 "bgm.wav" 3000
                                 "s1.wav" 10000}}
        scenario {:timeline [{:id "000001"
                              :videos ["bg.png"]
                              :audios ["bgm.wav"]
                              :children [{:id "000002"
                                          :videos ["c.mp4"]
                                          :audios ["s1.wav"]}]}]}
        [row] (r/resolve-scenario scenario ctx)
        a-clips (get-in row [:audio :clips])]
    (is (= 10000 (:duration-ms row)))
    (is (= 4 (count a-clips)) "3000+3000+3000+1000=10000")
    (is (every? #(= "bgm.wav" (:path %)) a-clips))
    (is (= [3000 3000 3000 1000] (mapv :duration-ms a-clips)))))

(deftest children-resolved-structure
  (let [ctx {:path->duration-ms {"bg.png" 999999
                                 "c.mp4" 2000
                                 "s1.wav" 3000}}
        scenario {:timeline [{:id "000001"
                              :videos ["bg.png"]
                              :children [{:id "000002"
                                          :videos ["c.mp4"]
                                          :audios ["s1.wav"]}]}]}
        [row] (r/resolve-scenario scenario ctx)]
    (is (= 1 (count (:children-resolved row))))
    (let [child (first (:children-resolved row))]
      (is (= 3000 (:duration-ms child)))
      (is (= 2 (count (:video-clips child))) "c.mp4 loops: 2000+1000")
      (is (= ["c.mp4" "c.mp4"] (mapv :path (:video-clips child)))))))
