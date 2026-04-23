# malli で関数の schema 定義をちゃんとやる

https://github.com/metosin/malli/blob/master/docs/function-schemas.md#tldr にあるような関数の schema 定義を全体的に行いたい。

```clj
(ns malli.demo)

(defn plus1
  "Adds one to the number"
  {:malli/schema [:=> [:cat :int] :int]}
  [x] (inc x))

;; instrument, clj-kondo + pretty errors
(require '[malli.dev :as dev])
(require '[malli.dev.pretty :as pretty])
(dev/start! {:report (pretty/reporter)})

(plus1 "123")

(comment
  (dev/stop!))
```

各関数に [function-schema-metadata](https://github.com/metosin/malli/blob/master/docs/function-schemas.md#function-schema-metadata) 形式で
引数と戻り値の schema を定義してください。

テストコードの はじめ(startup)に `(dev/start! {:report (pretty/reporter)})` を実行して、 teardown で `(comment (dev/stop!))` を実行してください。
