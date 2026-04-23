(ns scheherazade.generation-test
  (:require [babashka.fs :as fs]
            [clojure.test :refer [deftest is testing]]
            [scheherazade.generation :as gen]))

(deftest load-dictionary-reads-valid-entries
  (let [tmp-dir (fs/create-temp-dir {:prefix "sche-generation-test"})
        dict-path (str (fs/file tmp-dir "dictionary.json"))]
    (spit dict-path "[{\"word\":\"脆弱性\",\"ruby\":\"ゼイジャクセイ\"},{\"word\":\"\",\"ruby\":\"x\"}]")
    (is (= [{:word "脆弱性" :ruby "ゼイジャクセイ"}]
           (gen/load-dictionary dict-path)))))

(deftest load-dictionary-missing-file-is-empty
  (is (= [] (gen/load-dictionary "does-not-exist-dictionary.json"))))

(deftest ensure-audio-paths-applies-dictionary-before-generation
  (let [tmp-dir (fs/create-temp-dir {:prefix "sche-generation-test"})
        dict-path (str (fs/file tmp-dir "dictionary.json"))
        out-path (str (fs/file tmp-dir "voice.wav"))
        captured (atom nil)
        scenario {:timeline [{:audios [{:path out-path
                                        :generation {:name "voicevox"
                                                     :props {:text "脆弱性対応"}}}]}]}]
    (spit dict-path "[{\"word\":\"脆弱性\",\"ruby\":\"ゼイジャクセイ\"}]")
    (with-redefs [gen/generation (fn [params _out-path _opts]
                                   (reset! captured params))]
      (gen/ensure-audio-paths! scenario {:dictionary-path dict-path}))
    (is (= :voicevox (:name @captured)))
    (is (= "ゼイジャクセイ対応" (get-in @captured [:props :text])))))
