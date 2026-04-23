(ns scheherazade.generation
  "Strategy-style TTS backends via HTTP."
  (:require [babashka.fs :as fs]
            [babashka.http-client :as http]
            [cheshire.core :as json]
            [clojure.string :as str]))

(defn- slurp-json-file
  [path]
  (json/parse-string (slurp path) true))

(defn- normalize-strategy
  [strategy-name]
  (-> (clojure.core/name (keyword (str (or strategy-name ""))))
      str/lower-case
      keyword))

(defn- ensure-parent-dir!
  [out-path]
  (when-let [dir (some-> out-path fs/file fs/parent)]
    (fs/create-dirs dir)))

(defn- normalize-dictionary-entry
  [entry]
  {:word (str (or (:word entry) ""))
   :ruby (str (or (:ruby entry) ""))})

(defn load-dictionary
  [dictionary-path]
  (let [p (some-> dictionary-path str)]
    (if (and p (not (str/blank? p)) (fs/exists? p))
      (->> (slurp-json-file p)
           (filter map?)
           (map normalize-dictionary-entry)
           (remove #(or (str/blank? (:word %))
                        (str/blank? (:ruby %))))
           vec)
      [])))

(defn- apply-dictionary
  [text dictionary]
  (reduce (fn [acc {:keys [word ruby]}]
            (str/replace acc word ruby))
          (str (or text ""))
          dictionary))

(defn- apply-common-props
  [props dictionary]
  (if (contains? props :text)
    (assoc props :text (apply-dictionary (:text props) dictionary))
    props))

(defn- join-url
  [base path]
  (str (str/replace (str base) #"/+$" "")
       "/"
       (str/replace (str path) #"^/+" "")))

(defn- write-audio!
  [out-path body]
  (ensure-parent-dir! out-path)
  (with-open [out (java.io.FileOutputStream. (str out-path))]
    (.write out ^bytes body)))

(defn- post-json
  [url body {:keys [headers] :as opts}]
  (http/post url (assoc opts
                        :headers (merge {"content-type" "application/json"} headers)
                        :body (json/generate-string body))))

(defmulti generation
  (fn [params _out-path _opts]
    (normalize-strategy (:name params))))

(defmethod generation :voicevox
  [{:keys [props]} out-path _opts]
  (let [base-url (or (System/getenv "SCHE_VOICEVOX_URL")
                     (throw (ex-info "SCHE_VOICEVOX_URL is required for voicevox generation" {})))
        speaker-id (str (or (:speaker_id props) "1"))
        text (str (or (:text props) ""))
        query-url (join-url base-url "audio_query")
        synth-url (join-url base-url "synthesis")
        query-resp (http/post query-url
                              {:query-params {"speaker" speaker-id
                                              "text" text}
                               :headers {:accept "application/json"}})
        _ (when-not (= 200 (:status query-resp))
            (throw (ex-info "VOICEVOX audio_query failed"
                            {:status (:status query-resp) :body (:body query-resp)})))
        query-json (json/parse-string (:body query-resp) true)
        query-json (if (some? (:speed props))
                     (assoc query-json :speedScale (double (:speed props)))
                     query-json)
        synth-resp (post-json synth-url query-json
                              {:headers {"accept" "audio/wav"}
                               :query-params {"speaker" speaker-id}})]
    (when-not (= 200 (:status synth-resp))
      (throw (ex-info "VOICEVOX synthesis failed"
                      {:status (:status synth-resp) :body (:body synth-resp)})))
    (write-audio! out-path (:body synth-resp))))

(defmethod generation :elevenlabs
  [{:keys [props]} out-path _opts]
  (let [base-url (or (System/getenv "SCHE_ELEVENLABS_URL")
                     (throw (ex-info "SCHE_ELEVENLABS_URL is required for elevenlabs generation" {})))
        api-key (or (System/getenv "SCHE_ELEVENLABS_API_KEY")
                    (throw (ex-info "SCHE_ELEVENLABS_API_KEY is required for elevenlabs generation" {})))
        speaker-id (or (:speaker_id props)
                       (throw (ex-info "generation.props.speaker_id is required for elevenlabs" {})))
        model (or (:model props) "eleven_turbo_v2_5")
        speed (double (or (:speed props) 1.0))
        url (join-url base-url (str "text-to-speech/" speaker-id))
        body {:text (str (or (:text props) ""))
              :model_id model
              :voice_settings {:speed speed}}
        resp (post-json url body
                        {:headers {"xi-api-key" api-key
                                   "accept" "audio/mpeg"}})]
    (when-not (= 200 (:status resp))
      (throw (ex-info "ElevenLabs synthesis failed"
                      {:status (:status resp) :body (:body resp)})))
    (write-audio! out-path (:body resp))))

(defmethod generation :gemini
  [{:keys [props]} out-path _opts]
  (let [api-key (or (System/getenv "GEMINI_API_KEY")
                    (System/getenv "GOOGLE_API_KEY")
                    (throw (ex-info "GEMINI_API_KEY or GOOGLE_API_KEY is required for gemini generation" {})))
        model (or (:model props) "gemini-2.5-flash-preview-tts")
        speaker-id (or (:speaker_id props) "Kore")
        prompt (or (:prompt props) "")
        text (str (or (:text props) ""))
        url (str "https://generativelanguage.googleapis.com/v1beta/models/"
                 model
                 ":generateContent?key="
                 api-key)
        body {:contents [{:parts [{:text text}]}]
              :generationConfig {:responseModalities ["AUDIO"]
                                 :speechConfig {:voiceConfig {:prebuiltVoiceConfig {:voiceName speaker-id}}}}
              :systemInstruction (when-not (str/blank? prompt)
                                   {:parts [{:text prompt}]})}
        clean-body (cond-> body
                     (nil? (:systemInstruction body)) (dissoc :systemInstruction))
        resp (post-json url clean-body {:headers {"accept" "application/json"}})]
    (when-not (= 200 (:status resp))
      (throw (ex-info "Gemini TTS generation failed"
                      {:status (:status resp) :body (:body resp)})))
    (let [json-body (json/parse-string (:body resp) true)
          b64 (or (get-in json-body [:candidates 0 :content :parts 0 :inlineData :data])
                  (throw (ex-info "Gemini response does not include audio data" {:response json-body})))
          audio-bytes (.decode (java.util.Base64/getDecoder) ^String b64)]
      (write-audio! out-path audio-bytes))))

(defmethod generation :default
  [{:keys [name]} _out-path _opts]
  (throw (ex-info "Unsupported generation strategy" {:name name})))

(defn generate-audio-file!
  [{:keys [name props] :as generation-params} out-path opts]
  (let [dictionary (:dictionary opts [])
        with-common (assoc generation-params
                           :name (normalize-strategy name)
                           :props (apply-common-props (or props {}) dictionary))]
    (generation with-common out-path opts)))

(defn ensure-audio-paths!
  "Walk scenario timeline; when audio has generation and path missing, generate then continue."
  ([scenario]
   (ensure-audio-paths! scenario {}))
  ([scenario {:keys [dictionary-path] :as opts}]
   (let [dictionary (load-dictionary dictionary-path)
         run-opts (assoc opts :dictionary dictionary)]
     (letfn [(audio-node [a]
               (if (string? a) {:path a} a))
             (walk-audios [audios]
               (doseq [a audios]
                 (let [n (audio-node a)
                       p (:path n)
                       g (:generation n)]
                   (when (and g (not (fs/exists? p)))
                     (generate-audio-file! g p run-opts)))))
             (walk-timeline [t]
               (walk-audios (or (:audios t) []))
               (doseq [c (or (:children t) [])]
                 (walk-timeline c)))]
       (doseq [t (:timeline scenario)]
         (walk-timeline t))
       scenario))))
