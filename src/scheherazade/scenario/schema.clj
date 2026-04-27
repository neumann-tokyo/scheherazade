(ns scheherazade.scenario.schema
  (:require [malli.core :as m]
            [malli.error :as me]))

(def hex-6 [:re #"^[0-9a-fA-F]{6}$"])

(def position-schema
  [:map
   [:x [:or :int :double]]
   [:y [:or :int :double]]
   [:w [:or :int :double]]
   [:h [:or :int :double]]])

(def effect-schema
  [:map
   [:name :string]
   [:props {:optional true} :map]])

(def generation-schema
  [:map
   [:name [:enum "voicevox" "gemini" "elevenlabs"]]
   [:props :map]])

(def audio-object-schema
  [:map
   [:path :string]
   [:effects {:optional true} [:vector effect-schema]]
   [:generation {:optional true} generation-schema]])

(def audio-entry
  [:or :string audio-object-schema])

(def video-object-schema
  [:map
   [:path :string]
   [:length {:optional true} :int]
   [:effects {:optional true} [:vector effect-schema]]])

(def video-entry
  [:or :string video-object-schema])

(def text-schema
  [:map
   [:text [:or :string [:vector :string]]]
   [:position position-schema]
   [:font_size {:optional true} [:and :int [:> 0]]]
   [:font_family {:optional true} :string]
   [:font_color {:optional true} hex-6]
   [:border_color {:optional true} hex-6]
   [:chank {:optional true} [:enum "char" "string" "text"]]
   [:speed {:optional true} [:or :int :double]]])

(def scenario-schema
  [:schema
   {:registry
    {::timeline
     [:map
      [:id [:re #"^\d{6}$"]]
      [:videos {:optional true} [:vector video-entry]]
      [:audios {:optional true} [:vector audio-entry]]
      [:texts {:optional true} [:vector text-schema]]
      [:children {:optional true} [:vector [:ref ::timeline]]]]}}
   [:map
    [:title [:string {:max 100}]]
    [:description [:string {:max 2000}]]
    [:screen {:optional true} [:re #"^\d+x\d+$"]]
    [:fps {:optional true} [:or :string :int]]
    [:video_codec {:optional true} :string]
    [:audio_codec {:optional true} :string]
    [:timeline [:vector [:ref ::timeline]]]]])

(defn validate
  [data]
  (when-not (m/validate scenario-schema data)
    (let [expl (m/explain scenario-schema data)]
      {:error :invalid-scenario
       :explain expl
       :human (me/humanize expl)})))

(defn explain-human
  [data]
  (when-let [e (validate data)]
    (:human e)))
