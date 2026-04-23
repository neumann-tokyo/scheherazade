(ns scheherazade.main
  (:require [babashka.cli :as cli]
            [babashka.fs :as fs]
            [scheherazade.ffmpeg :as ff]
            [scheherazade.generation :as gen]
            [scheherazade.scenario.jsonc :as jsonc]
            [scheherazade.scenario.schema :as schema]
            [scheherazade.timeline.resolve :as resolve]
            [clojure.string :as str]))

(defn- parse-cli
  [args]
  (let [parsed (cli/parse-opts args {:spec {:generate {:coerce :boolean}
                                            :out {:alias :o}
                                            :dictionary {:alias :d}}
                                     :args->opts [:scenario]
                                     :restrict false})
        mode (if (:generate parsed) :generate :render)
        scenario (:scenario parsed)]
    {:mode mode
     :out (:out parsed)
     :scenario scenario
     :dictionary (:dictionary parsed)}))

(defn- default-dictionary-path
  [scenario-path]
  (str (fs/file (or (some-> scenario-path fs/parent str) ".") "dictionary.json")))

(defn- ctx-from-scenario
  [_scenario]
  {:ffprobe-fn (fn [p] (ff/ffprobe-duration-sec p))})

(defn- render-file!
  [scenario-path out-path dictionary-path]
  (let [raw (jsonc/parse-file scenario-path)
        data raw
        err (schema/validate data)
        _ (when err (throw (ex-info "Invalid scenario" err)))
        _ (gen/ensure-audio-paths! data {:dictionary-path dictionary-path})
        ctx (ctx-from-scenario data)
        resolved (resolve/resolve-scenario data ctx)
        wd (fs/create-dirs (fs/file (System/getProperty "java.io.tmpdir") "scheherazade-seg"))]
    (if (= 1 (count resolved))
      (ff/render-resolved-timeline! (first resolved) data out-path {:work-dir (str wd)})
      (let [segs (map-indexed
                  (fn [i r]
                    (let [p (fs/file wd (str "seg-" i ".mp4"))]
                      (ff/render-resolved-timeline! r data (str p) {:work-dir (str wd)})
                      (str p)))
                  resolved)]
        (ff/concat-segment-files! segs out-path (str wd))))))

(defn -main
  [& args]
  (try
    (let [{:keys [mode out scenario dictionary]} (parse-cli args)
          dictionary-path (or dictionary (default-dictionary-path scenario))]
      (when (or (str/blank? scenario) (not (fs/exists? scenario)))
        (binding [*out* *err*]
          (println "Usage: sche [--generate] <scenario.jsonc> [-o out.mp4] [-d dictionary.json]"))
        (System/exit 2))
      (case mode
        :generate
        (let [raw (jsonc/parse-file scenario)
              data raw
              err (schema/validate data)]
          (when err (throw (ex-info "Invalid scenario" err)))
          (gen/ensure-audio-paths! data {:dictionary-path dictionary-path})
          (println "Generation pass complete."))
        :render
        (let [out (or out "out.mp4")]
          (render-file! scenario out dictionary-path)
          (println "Wrote" out))))
    (catch Throwable e
      (binding [*out* *err*]
        (println (.getMessage e))
        (when-let [d (ex-data e)]
          (when (:human d) (prn (:human d)))
          (when (:explain d) (prn (:explain d)))))
      (System/exit 1))))
