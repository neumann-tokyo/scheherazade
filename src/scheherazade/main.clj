(ns scheherazade.main
  (:require [babashka.fs :as fs]
            [scheherazade.ffmpeg :as ff]
            [scheherazade.generation :as gen]
            [scheherazade.scenario.jsonc :as jsonc]
            [scheherazade.scenario.schema :as schema]
            [scheherazade.timeline.resolve :as resolve]
            [clojure.string :as str]))

(defn- parse-cli
  [args]
  (let [mode (if (some #{"--generate"} args) :generate :render)
        args (vec (remove #{"--generate"} args))
        [out rem] (if-let [i (.indexOf args "-o")]
                    [(get args (inc i))
                     (vec (concat (subvec args 0 i) (subvec args (+ i 2))))]
                    [nil args])
        scenario (first rem)]
    {:mode mode :out out :scenario scenario}))

(defn- normalize-scenario-keys
  [raw]
  (-> raw
      (dissoc :eescription)
      (assoc :description (or (:description raw) (:eescription raw) ""))))

(defn- ctx-from-scenario
  [_scenario]
  {:ffprobe-fn (fn [p] (ff/ffprobe-duration-sec p))})

(defn- render-file!
  [scenario-path out-path]
  (let [raw (jsonc/parse-file scenario-path)
        data (normalize-scenario-keys raw)
        err (schema/validate data)
        _ (when err (throw (ex-info "Invalid scenario" err)))
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
    (let [{:keys [mode out scenario]} (parse-cli args)]
      (when (or (str/blank? scenario) (not (fs/exists? scenario)))
        (binding [*out* *err*]
          (println "Usage: sche [--generate] <scenario.jsonc> [-o out.mp4]"))
        (System/exit 2))
      (case mode
        :generate
        (let [raw (jsonc/parse-file scenario)
              data (normalize-scenario-keys raw)
              err (schema/validate data)]
          (when err (throw (ex-info "Invalid scenario" err)))
          (gen/ensure-audio-paths! data)
          (println "Generation pass complete."))
        :render
        (let [out (or out "out.mp4")]
          (render-file! scenario out)
          (println "Wrote" out))))
    (catch Throwable e
      (binding [*out* *err*]
        (println (.getMessage e))
        (when-let [d (ex-data e)]
          (when (:human d) (prn (:human d)))
          (when (:explain d) (prn (:explain d)))))
      (System/exit 1))))
