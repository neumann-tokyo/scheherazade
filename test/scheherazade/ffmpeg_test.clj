(ns scheherazade.ffmpeg-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [scheherazade.ffmpeg :as ff]))

(def base-pos {:x 100 :y 200 :w 400 :h 100})

(deftest text-windows-chank-text
  (testing "chank=text produces a single window covering full duration"
    (let [obj {:text "hello" :position base-pos}
          ws (ff/text-windows obj 5.0)]
      (is (= 1 (count ws)))
      (is (= "hello" (:text (first ws))))
      (is (= 0 (:start-sec (first ws))))
      (is (= 5.0 (:end-sec (first ws))))))

  (testing "chank=text with array text joins all strings"
    (let [obj {:text ["he" "llo"] :position base-pos :chank "text"}
          ws (ff/text-windows obj 3.0)]
      (is (= 1 (count ws)))
      (is (= "hello" (:text (first ws)))))))

(deftest text-windows-chank-char
  (testing "chank=char with string creates one window per character prefix"
    (let [obj {:text "abc" :position base-pos :chank "char" :speed 100}
          ws (vec (ff/text-windows obj 10.0))]
      (is (= 3 (count ws)))
      (is (= "a" (:text (nth ws 0))))
      (is (= "ab" (:text (nth ws 1))))
      (is (= "abc" (:text (nth ws 2))))
      (is (= 0.0 (:start-sec (nth ws 0))))
      (is (= 0.1 (:start-sec (nth ws 1))))
      (is (= 0.2 (:start-sec (nth ws 2))))
      ;; last window ends at duration-sec
      (is (= 10.0 (:end-sec (nth ws 2))))))

  (testing "chank=char with vector text joins strings before splitting to chars"
    (let [obj {:text ["ab" "c"] :position base-pos :chank "char" :speed 500}
          ws (vec (ff/text-windows obj 5.0))]
      (is (= 3 (count ws)))
      (is (= "a" (:text (nth ws 0))))
      (is (= "ab" (:text (nth ws 1))))
      (is (= "abc" (:text (nth ws 2))))))

  (testing "chank=char empty text produces no windows"
    (let [obj {:text "" :position base-pos :chank "char" :speed 100}
          ws (ff/text-windows obj 5.0)]
      (is (empty? ws)))))

(deftest text-windows-chank-string
  (testing "chank=string with vector shows each element in its own window"
    (let [obj {:text ["foo" "bar" "baz"] :position base-pos :chank "string" :speed 200}
          ws (vec (ff/text-windows obj 10.0))]
      (is (= 3 (count ws)))
      (is (= "foo" (:text (nth ws 0))))
      (is (= "bar" (:text (nth ws 1))))
      (is (= "baz" (:text (nth ws 2))))
      (is (= 0.0 (:start-sec (nth ws 0))))
      (is (= 0.2 (:start-sec (nth ws 1))))
      (is (= 0.4 (:start-sec (nth ws 2))))
      (is (= 10.0 (:end-sec (nth ws 2))))))

  (testing "chank=string with plain string wraps it as a single element"
    (let [obj {:text "hello" :position base-pos :chank "string" :speed 300}
          ws (vec (ff/text-windows obj 5.0))]
      (is (= 1 (count ws)))
      (is (= "hello" (:text (first ws))))
      (is (= 5.0 (:end-sec (first ws)))))))

(deftest text-windows-default-speed
  (testing "speed defaults to 100 ms when not specified"
    (let [obj {:text "ab" :position base-pos :chank "char"}
          ws (vec (ff/text-windows obj 5.0))]
      (is (= 0.0 (:start-sec (nth ws 0))))
      (is (= 0.1 (:start-sec (nth ws 1)))))))

(deftest text-windows-nil-chank-defaults-to-text
  (testing "missing chank behaves like chank=text"
    (let [obj {:text "hello" :position base-pos}
          ws (ff/text-windows obj 4.0)]
      (is (= 1 (count ws)))
      (is (= "hello" (:text (first ws)))))))

(deftest drawtext-respects-font-family
  (let [drawtext-suffix (deref (resolve 'scheherazade.ffmpeg/drawtext-suffix))
        txt {:text "hello"
             :position base-pos
             :font_family "GenEi Kiwami Gothic"}
        s (drawtext-suffix [txt] 5.0)]
    (is (string? s))
    (is (.contains s ":font='GenEi Kiwami Gothic'"))))

(deftest fit-text-to-box-wraps-and-truncates
  (let [fit-text-to-box (deref (resolve 'scheherazade.ffmpeg/fit-text-to-box))
        text-width-px (deref (resolve 'scheherazade.ffmpeg/text-width-px))
        wrapped (fit-text-to-box "abcdefghijkl" 10 40 30 nil)
        truncated (fit-text-to-box "abcdefghijklmnopqr" 10 40 11 nil)
        wrapped-lines (str/split wrapped #"\n")
        trunc-lines (str/split truncated #"\n")]
    (is (> (count wrapped-lines) 1))
    (is (every? #(<= (text-width-px % nil 10) 40.0) wrapped-lines))
    (is (= 1 (count trunc-lines)))
    (is (.endsWith (first trunc-lines) "..."))
    (is (<= (text-width-px (first trunc-lines) nil 10) 40.0))))

(deftest drawtext-renders-wrapped-lines-as-multiple-filters
  (let [drawtext-suffix (deref (resolve 'scheherazade.ffmpeg/drawtext-suffix))
        txt {:text "abcdefghijkl"
             :position {:x 10 :y 20 :w 40 :h 30}
             :font_size 10}
        s (drawtext-suffix [txt] 5.0)
        ys (set (map second (re-seq #":y=(\d+):" s)))]
    (is (string? s))
    (is (= 2 (dec (count (str/split s #",drawtext=")))))
    (is (.contains s ":y=20:"))
    (is (contains? ys "30"))))
