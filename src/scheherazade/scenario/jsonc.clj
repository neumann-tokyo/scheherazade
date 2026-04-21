(ns scheherazade.scenario.jsonc
  "Strip JSONC comments (// and /* */ outside JSON strings) then parse with Cheshire.
  String contents are preserved; ambiguous cases are out of scope per project plan."
  (:require [cheshire.core :as json]))

(defn strip-jsonc-comments
  [^String s]
  (let [sb (StringBuilder.)
        n (.length s)]
    (loop [i 0 mode :data string-delim nil escape? false]
      (if (>= i n)
        (.toString sb)
        (let [c (.charAt s i)]
          (case mode
            :data
            (cond
              (= c \") (do (.append sb c) (recur (inc i) :in-string c false))
              (and (= c \/) (< (inc i) n) (= \/ (.charAt s (inc i))))
              (recur (+ i 2) :line-comment nil false)
              (and (= c \/) (< (inc i) n) (= \* (.charAt s (inc i))))
              (recur (+ i 2) :block-comment nil false)
              :else (do (.append sb c) (recur (inc i) :data nil false)))
            :in-string
            (cond
              escape? (do (.append sb c) (recur (inc i) :in-string string-delim false))
              (= c \\) (do (.append sb c) (recur (inc i) :in-string string-delim true))
              (= c string-delim) (do (.append sb c) (recur (inc i) :data nil false))
              :else (do (.append sb c) (recur (inc i) :in-string string-delim false)))
            :line-comment
            (if (or (= c \newline) (= c \return))
              (recur (inc i) :data nil false)
              (recur (inc i) :line-comment nil false))
            :block-comment
            (if (and (= c \*) (< (inc i) n) (= \/ (.charAt s (inc i))))
              (recur (+ i 2) :data nil false)
              (recur (inc i) :block-comment nil false))))))))

(defn parse-string
  [s]
  (-> s strip-jsonc-comments (json/parse-string true)))

(defn parse-file
  [path]
  (parse-string (slurp path)))
