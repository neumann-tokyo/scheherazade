# Scenario.jsonc の JSON Schema を定義する

@src/scheherazade/scenario/schema.clj の `scenario-schema` に対して、
下記のような処理を実行して、JSON Schema を `scenario-schema.json` という名前で保存する
bb tasks を作成してください。

```clj
(require '[malli.json-schema :as json-schema])

(json-schema/transform scenario-schema)
```

### 参考

https://github.com/metosin/malli#json-schema
