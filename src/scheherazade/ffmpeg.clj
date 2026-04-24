(ns scheherazade.ffmpeg
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [clojure.string :as str]
            [selmer.parser :as selmer]))

;; TODO chank アニメーションの実装 (これもどうやれば良いのやら simple-ffmpeg のコードを参考にする)

(defn parse-screen
  [s]
  (let [s (or s "1920x1080")
        [w h] (str/split s #"x")]
    [(parse-long w) (parse-long h)]))

(defn parse-fps
  [scenario]
  (long (cond
          (number? (:fps scenario)) (:fps scenario)
          (string? (:fps scenario)) (parse-long (:fps scenario))
          :else 30)))

(defn ffprobe-duration-sec
  [path]
  (-> (p/process {:out :string :err :string}
                 "ffprobe" "-v" "error" "-show_entries" "format=duration"
                 "-of" "default=noprint_wrappers=1:nokey=1" (str path))
      p/check :out str/trim Double/parseDouble))

(defn- video-encoder-args
  [codec]
  (case (str/lower-case (str (or codec "vp9")))
    "vp9" ["-c:v" "libvpx-vp9" "-b:v" "0" "-crf" "32" "-pix_fmt" "yuv420p"]
    "h264" ["-c:v" "libx264" "-crf" "23" "-preset" "medium" "-pix_fmt" "yuv420p"]
    (throw (ex-info "Unsupported video_codec (use vp9 or h264)" {:codec codec}))))

(defn- audio-encoder-args
  [codec]
  (case (str/lower-case (str (or codec "aac")))
    "aac" ["-c:a" "aac" "-b:a" "192k"]
    (throw (ex-info "Unsupported audio_codec (use aac)" {:codec codec}))))

(defn- chroma-suffix
  [effects]
  (when-let [ck (some (fn [e]
                        (when (and (:name e)
                                   (str/includes? (str/lower-case (:name e)) "chroma"))
                          e))
                      effects)]
    (let [props (:props ck)
          col (str/replace (str (or (:target_color props) "#00ff00")) "#" "0x")
          thr (str (or (:threshold props) "0.35"))]
      (str ",format=yuva420p,colorkey=" col ":" thr ":0.1"))))

(defn- escape-drawtext
  [s]
  (-> s
      (str/replace "\\" "\\\\\\\\")
      (str/replace "'" "'\\\\\\''")
      (str/replace ":" "\\:")
      (str/replace "%" "\\%")))

(defn- drawtext-suffix
  [texts duration-sec]
  (when-let [t0 (first texts)]
    (let [raw (:text t0)
          s (if (string? raw) raw (str/join "" raw))
          pos (:position t0)
          x (long (:x pos))
          y (long (:y pos))
          fs (str/replace (or (:font_size t0) "24") #"pt$" "")
          fc (or (:font_color t0) "ffffff")
          bc (or (:border_color t0) "000000")]
      (selmer/render
       ",drawtext=text='{{text}}':fontsize={{font_size}}:fontcolor=0x{{font_color}}:borderw=2:bordercolor=0x{{border_color}}:x={{x}}:y={{y}}:enable='between(t,0,{{duration}})'"
       {:text (escape-drawtext s)
        :font_size fs
        :font_color fc
        :border_color bc
        :x x
        :y y
        :duration duration-sec}))))

(defn- clip-filter
  [idx w h fps dur-sec _kind effects & {:keys [alpha? tag-prefix]
                                        :or {alpha? false tag-prefix "p"}}]
  (let [pad-color (if alpha? ":color=0x00000000" "")
        fmt (if alpha? "format=yuva420p," "")]
    (selmer/render
     "[{{idx}}:v]{{fmt}}fps=fps={{fps}},scale=w={{w}}:h={{h}}:force_original_aspect_ratio=decrease,pad={{w}}:{{h}}:(ow-iw)/2:(oh-ih)/2{{pad_color}},trim=duration={{duration}},setpts=PTS-STARTPTS{{chroma}}[{{prefix}}{{idx}}]"
     {:idx idx
      :fmt fmt
      :fps fps
      :w w
      :h h
      :pad_color pad-color
      :duration dur-sec
      :chroma (or (chroma-suffix effects) "")
      :prefix tag-prefix})))

(defn- video-input-args
  [clip fps]
  (let [{:keys [path kind duration-ms]} clip
        dur-sec (str (/ duration-ms 1000.0))]
    (if (= kind :image)
      ["-loop" "1" "-framerate" (str fps) "-t" dur-sec "-i" path]
      ["-i" path])))

(defn- video-graph
  [v-clips w h fps]
  (let [n (count v-clips)]
    (cond
      (zero? n) {:chains ["[0:v]format=yuv420p[vid]"] :vtag "[vid]"}
      (= n 1) {:chains [(clip-filter 0 w h fps (/ (:duration-ms (first v-clips)) 1000.0)
                                     (:kind (first v-clips)) (:effects (first v-clips)))]
               :vtag "[p0]"}
      :else {:chains (conj (vec (map-indexed
                                 (fn [i c]
                                   (clip-filter i w h fps (/ (:duration-ms c) 1000.0)
                                                (:kind c) (:effects c)))
                                 v-clips))
                           (str (apply str (map #(str "[p" % "]") (range n)))
                                "concat=n=" n ":v=1:a=0[vid]"))
             :vtag "[vid]"})))

(defn- audio-graph
  [na a0 dur-sec a-clips]
  (when (pos? na)
    (if (= na 1)
      [(str "[" a0 ":a]atrim=duration=" dur-sec ",asetpts=PTS-STARTPTS[aud]")]
      (conj (vec (map-indexed
                  (fn [i ac]
                    (str "[" (+ a0 i) ":a]atrim=duration="
                         (/ (:duration-ms ac) 1000.0)
                         ",asetpts=PTS-STARTPTS[ac" i "]"))
                  a-clips))
            (str (apply str (map #(str "[ac" % "]") (range na)))
                 "concat=n=" na ":v=0:a=1[aud]")))))

(declare concat-segment-files!)

(defn- render-leaf-timeline!
  "Render a single timeline object (no children) to a file."
  [{:keys [duration-ms video-clips audio texts]} scenario out-path {:keys [alpha?] :or {alpha? false}}]
  (let [[w h] (parse-screen (:screen scenario))
        fps (parse-fps scenario)
        dur-sec (/ duration-ms 1000.0)
        v-clips (vec video-clips)
        a-clips (vec (:clips audio))
        nv (count v-clips)
        na (count a-clips)
        silent ["-f" "lavfi" "-i" (str "anullsrc=r=48000:cl=stereo,atrim=duration=" dur-sec ",asetpts=PTS-STARTPTS")]
        in-args (cond
                  (zero? nv)
                  (vec (concat ["-f" "lavfi" "-i" (str "color=c=black:s=" w "x" h ":r=" fps ":d=" dur-sec)]
                               (mapcat (fn [ac] ["-i" (:path ac)]) a-clips)
                               (when (zero? na) silent)))
                  :else
                  (vec (concat (mapcat #(video-input-args % fps) v-clips)
                               (mapcat (fn [ac] ["-i" (:path ac)]) a-clips)
                               (when (zero? na) silent))))
        a0 (if (zero? nv) 1 nv)
        anull-idx (when (zero? na) (if (zero? nv) 1 (+ nv na)))
        {:keys [chains vtag]} (if (zero? nv)
                                {:chains ["[0:v]format=yuv420p[vid]"] :vtag "[vid]"}
                                (video-graph v-clips w h fps))
        ach (if (pos? na)
              (audio-graph na a0 dur-sec a-clips)
              [(str "[" anull-idx ":a]asetpts=PTS-STARTPTS[aud]")])
        vsrc (subs vtag 1 (dec (count vtag)))
        pix-fmt (if alpha? "yuva420p" "yuv420p")
        vout (selmer/render "[{{vsrc}}]format={{pix_fmt}}{{drawtext}}[vout]"
                            {:vsrc vsrc
                             :pix_fmt pix-fmt
                             :drawtext (or (drawtext-suffix texts dur-sec) "")})
        fc (str/join ";" (concat chains ach [vout]))
        enc (if alpha?
              ["-c:v" "libvpx-vp9" "-b:v" "0" "-crf" "32" "-pix_fmt" "yuva420p"]
              (video-encoder-args (:video_codec scenario)))
        cmd (vec (concat ["ffmpeg" "-y"] in-args
                         ["-filter_complex" fc "-map" "[vout]" "-map" "[aud]"
                          "-t" (str dur-sec)]
                         enc
                         (audio-encoder-args (:audio_codec scenario))
                         [out-path]))]
    (p/check (apply p/process {:inherit true} cmd))))

(defn render-resolved-timeline!
  [{:keys [duration-ms video-clips audio texts children-resolved] :as resolved} scenario out-path opts]
  (if (empty? children-resolved)
    (render-leaf-timeline! resolved scenario out-path (or opts {}))
    (let [work-dir (str (fs/create-dirs (fs/file (or (:work-dir opts)
                                                     (System/getProperty "java.io.tmpdir"))
                                                 "scheherazade-children")))
          ;; 1. Render parent (no children) as leaf
          parent-path (str (fs/file work-dir "parent.webm"))
          parent-leaf {:duration-ms duration-ms
                       :video-clips video-clips
                       :audio audio
                       :texts texts
                       :children-resolved []}
          _ (render-leaf-timeline! parent-leaf scenario parent-path {:alpha? false})
          ;; 2. Render each child to temp file with alpha
          child-paths (vec (map-indexed
                            (fn [i child]
                              (let [cp (str (fs/file work-dir (str "child-" i ".webm")))]
                                (render-resolved-timeline! child scenario cp
                                                           (assoc (or opts {}) :alpha? true :work-dir work-dir))
                                cp))
                            children-resolved))
          ;; 3. Concat children into one track
          children-combined-path (if (= 1 (count child-paths))
                                   (first child-paths)
                                   (let [cp (str (fs/file work-dir "children-combined.webm"))]
                                     (concat-segment-files! child-paths cp work-dir)
                                     cp))
          ;; 4. Overlay children on parent + mix audio
          [w h] (parse-screen (:screen scenario))
          fps (parse-fps scenario)
          dur-sec (/ duration-ms 1000.0)
          cmd (vec (concat
                    ["ffmpeg" "-y"
                     "-i" parent-path
                     "-i" children-combined-path
                     "-filter_complex"
                     (str "[1:v]format=yuva420p[child_v];"
                          "[0:v][child_v]overlay=0:0:format=auto[vout];"
                          "[0:a][1:a]amix=inputs=2:duration=first:dropout_transition=0[aout]")
                     "-map" "[vout]" "-map" "[aout]"
                     "-t" (str dur-sec)]
                    (video-encoder-args (:video_codec scenario))
                    (audio-encoder-args (:audio_codec scenario))
                    [out-path]))]
      (p/check (apply p/process {:inherit true} cmd)))))

(defn concat-segment-files!
  [segment-paths out-path work-dir]
  (fs/create-dirs work-dir)
  (let [list-f (fs/file work-dir "concat-list.txt")
        lines (map (fn [p] (str "file '" (str/replace (str p) "'" "'\\''") "'")) segment-paths)]
    (spit (str list-f) (str/join "\n" lines))
    (p/check (p/process {:inherit true}
                        "ffmpeg" "-y" "-f" "concat" "-safe" "0" "-i" (str list-f)
                        "-c" "copy" out-path))))
