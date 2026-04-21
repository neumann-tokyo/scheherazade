(ns scheherazade.timeline.resolve
  "Resolve logical timeline duration (ms) and per-clip plans. ffprobe/media lookups are injectable."
  (:require [clojure.string :as str]))

(def default-image-only-ms 5000)

(defn- ext-of [path]
  (-> path str (str/split #"\.") last str/lower-case))

(defn image-path?
  [path]
  (#{"png" "jpg" "jpeg" "gif" "webp" "bmp"} (ext-of path)))

(defn- video-path [v]
  (if (string? v) v (:path v)))

(defn- video-effects [v]
  (when (map? v) (:effects v)))

(defn- video-length [v]
  (when (map? v) (:length v)))

(defn- normalize-audio [a]
  (if (string? a) {:path a} a))

(defn media-duration-ms
  "Returns duration in ms for a media path using ctx.
  ctx: {:path->duration-ms {path ms} :ffprobe-fn (fn [path] seconds-float)}"
  [path ctx]
  (or (get-in ctx [:path->duration-ms path])
      (when-let [f (:ffprobe-fn ctx)]
        (long (* 1000 (double (f path)))))
      (throw (ex-info "No duration for media path (provide :path->duration-ms or :ffprobe-fn)"
                      {:path path}))))

(defn- sum-audio-ms
  [audios ctx]
  (reduce (fn [acc a]
            (let [{:keys [path]} (normalize-audio a)]
              (+ acc (media-duration-ms path ctx))))
          0
          audios))

(defn- clip-natural-ms
  [v ctx]
  (let [p (video-path v)
        explicit (video-length v)]
    (cond
      explicit (long explicit)
      (image-path? p) :infinite
      :else (long (media-duration-ms p ctx)))))

(defn- sum-video-ms-uncapped
  [videos ctx]
  (reduce (fn [acc v]
            (let [n (clip-natural-ms v ctx)]
              (if (= n :infinite)
                :infinite
                (+ acc n))))
          0
          videos))

(declare timeline-node-ms)

(defn- base-duration-ms-no-children
  [{:keys [videos audios]} ctx]
  (let [vids (or videos [])
        aus (or audios [])
        audio-sum (when (seq aus) (sum-audio-ms aus ctx))
        vid-uncapped (when (seq vids) (sum-video-ms-uncapped vids ctx))]
    (cond
      (and (seq aus) (seq vids))
      (long audio-sum)
      (seq aus)
      (long audio-sum)
      (seq vids)
      (cond
        (= vid-uncapped :infinite) (long default-image-only-ms)
        :else (long vid-uncapped))
      :else
      0)))

(defn- children-end-ms
  [children ctx]
  (if (seq children)
    (apply max (map #(timeline-node-ms % ctx) children))
    0))

(defn timeline-node-ms
  "Resolved duration for a single Timeline Object (ms)."
  [{:keys [children] :as node} ctx]
  (let [base (base-duration-ms-no-children node ctx)
        cend (children-end-ms children ctx)]
    (long (max base cend))))

(defn- build-video-clips
  [{:keys [videos]} duration-ms ctx]
  (let [vids (or videos [])]
    (loop [vs vids remaining duration-ms t 0 clips []]
      (if (or (empty? vs) (<= remaining 0))
        clips
        (let [v (first vs)
              p (video-path v)
              img? (image-path? p)
              natural (clip-natural-ms v ctx)
              clip-ms (long
                       (cond
                         (= natural :infinite) remaining
                         img? remaining
                         :else (min natural remaining)))]
          (recur (rest vs) (- remaining clip-ms) (+ t clip-ms)
                 (conj clips {:path p
                              :kind (if img? :image :video)
                              :duration-ms clip-ms
                              :effects (or (video-effects v) [])})))))))

(defn- build-audio-clips
  [{:keys [audios]} duration-ms ctx]
  (let [aus (or audios [])
        clips (vec
               (reduce (fn [acc a]
                         (let [n (normalize-audio a)
                               d (media-duration-ms (:path n) ctx)]
                           (conj acc (assoc n :duration-ms d))))
                       []
                       aus))
        total (reduce + 0 (map :duration-ms clips))]
    {:clips clips :total-ms total :trim-to-ms (min duration-ms total)}))

(defn resolve-timeline-object
  "Returns {:duration-ms ... :video-clips ... :audio ... :texts ... :children-resolved ...}"
  [node ctx]
  (let [d (timeline-node-ms node ctx)
        children (:children node)
        children-resolved (mapv #(resolve-timeline-object % ctx) (or children []))]
    {:duration-ms d
     :video-clips (build-video-clips node d ctx)
     :audio (build-audio-clips node d ctx)
     :texts (vec (or (:texts node) []))
     :children-resolved children-resolved}))

(defn resolve-scenario
  [scenario ctx]
  (mapv #(resolve-timeline-object % ctx) (:timeline scenario)))
