(ns scheherazade.generation
  "Strategy-style TTS backends: invoke external commands configured via env vars."
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [clojure.string :as str]))

;; TODO @spec_ja-JP/999_tasks/001_todo/000002_generator_strategy.md を参照して作り変えて
(defn- cmd-for
  [name]
  (or (System/getenv (str "SCHE_GENERATE_" (str/upper-case (str/replace name #"-" "_"))))
      (case name
        "elevenlabs" (System/getenv "SCHE_ELEVENLABS_CMD")
        "voicevox" (System/getenv "SCHE_VOICEVOX_CMD")
        "gemini" (System/getenv "SCHE_GEMINI_CMD")
        nil)))

(defn generate-audio-file!
  "Runs external generator so that `out-path` exists. `generation` is {:name ... :props ...}."
  [{:keys [name props] :as _generation} out-path]
  (let [base (cmd-for name)
        _ (when-not base
            (throw (ex-info (str "No command configured for generation " name
                                 " (set SCHE_GENERATE_" (str/upper-case name) " or SCHE_*_CMD)")
                            {:name name})))
        ;; base is shell string or argv0 — split on space for MVP
        parts (str/split base #"\s+")
        cmd (vec (concat parts [out-path]))]
    (when-let [dir (some-> out-path fs/file fs/parent)]
      (fs/create-dirs dir))
    (p/check (apply p/process {:inherit true} cmd))))

(defn ensure-audio-paths!
  "Walk scenario timeline; when audio has generation and path missing, generate then continue."
  [scenario]
  (letfn [(audio-node [a]
            (if (string? a) {:path a} a))
          (walk-audios [audios]
            (doseq [a audios]
              (let [n (audio-node a)
                    p (:path n)
                    g (:generation n)]
                (when (and g (not (fs/exists? p)))
                  (generate-audio-file! g p)))))
          (walk-timeline [t]
            (walk-audios (or (:audios t) []))
            (doseq [c (or (:children t) [])]
              (walk-timeline c)))]
    (doseq [t (:timeline scenario)]
      (walk-timeline t))
    scenario))
