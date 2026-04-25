(require '[scheherazade.scenario.schema :as schema])
(require '[malli.json-schema :as json-schema])
(require '[cheshire.core :as json])
(require '[clojure.walk :as walk])

(defn patterns->strings [x]
  (walk/postwalk
    (fn [v] (if (instance? java.util.regex.Pattern v) (str v) v))
    x))

(spit "scenario-schema.json"
      (json/generate-string
        (patterns->strings (json-schema/transform schema/scenario-schema))
        {:pretty true}))

(println "Generated scenario-schema.json")
