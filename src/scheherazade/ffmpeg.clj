(ns scheherazade.ffmpeg
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [clojure.string :as str]
            [selmer.parser :as selmer]))


(defn parse-screen
  [s]
  (let [screen (or s "1920x1080")
        [w h :as parts] (str/split screen #"x")]
    (when-not (= 2 (count parts))
      (throw (ex-info "Invalid screen format (expected <width>x<height>)"
                      {:screen s})))
    (let [pw (parse-long w)
          ph (parse-long h)]
      (when (or (nil? pw) (nil? ph) (<= pw 0) (<= ph 0))
        (throw (ex-info "Invalid screen size (width/height must be positive integers)"
                        {:screen s})))
      [pw ph])))

(defn parse-fps
  [scenario]
  (let [raw (:fps scenario)]
    (cond
      (nil? raw) 30
      (number? raw)
      (let [fps (long raw)]
        (when (<= fps 0)
          (throw (ex-info "Invalid fps (must be positive)" {:fps raw})))
        fps)
      (string? raw)
      (let [fps (parse-long raw)]
        (when (or (nil? fps) (<= fps 0))
          (throw (ex-info "Invalid fps (must be positive integer string)" {:fps raw})))
        fps)
      :else
      (throw (ex-info "Invalid fps type" {:fps raw :type (type raw)})))))

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
    "opus" ["-c:a" "libopus" "-b:a" "192k"]
    (throw (ex-info "Unsupported audio_codec (use aac or opus)" {:codec codec}))))

(defn- effective-audio-codec
  [codec out-path]
  (let [ext (-> (or (fs/extension (str out-path)) "")
                str/lower-case
                (str/replace #"^\." ""))]
    (cond
      ;; WebM does not support AAC.
      (and (= ext "webm")
           (contains? #{nil "" "aac"} (some-> codec str/lower-case)))
      "opus"

      :else codec)))

(defn- chroma-suffix
  [effects]
  (letfn [(pick [m k]
            (when (map? m)
              (or (get m k)
                  (get m (name k)))))
          (name-text [e]
            (some-> (pick e :name) str str/lower-case))]
    (when-let [ck (some (fn [e]
                          (when (and (name-text e)
                                     (str/includes? (name-text e) "chroma"))
                            e))
                        effects)]
      (let [props (or (pick ck :props) {})
            col (str/replace (str (or (pick props :target_color) "#00ff00")) "#" "0x")
            thr (str (or (pick props :threshold) "0.35"))]
        (str ",format=yuva420p,colorkey=" col ":" thr ":0.1")))))

(defn- chroma-overlay-suffix
  [children-resolved]
  (let [effects (->> children-resolved
                     (mapcat :video-clips)
                     (mapcat :effects)
                     vec)]
    (or (chroma-suffix effects) "")))

(defn- escape-drawtext
  [s]
  (-> s
      (str/replace "\\" "\\\\\\\\")
      (str/replace "'" "'\\\\\\''")
      (str/replace ":" "\\:")
      (str/replace "%" "\\%")))

(defn- text-content
  "Normalize a text value (string or vector of strings) to a single string."
  [raw]
  (if (string? raw) raw (str/join "" raw)))

(defn- char-units
  [ch]
  (let [code (int ch)]
    (if (or (<= 0x1100 code 0x11FF)   ;; Hangul Jamo
            (<= 0x2E80 code 0xA4CF)   ;; CJK + radicals + kana + bopomofo
            (<= 0xAC00 code 0xD7A3)   ;; Hangul syllables
            (<= 0xF900 code 0xFAFF)   ;; CJK compatibility ideographs
            (<= 0xFE10 code 0xFE6F)   ;; vertical/fullwidth punctuation
            (<= 0xFF01 code 0xFF60)   ;; fullwidth ASCII variants
            (<= 0xFFE0 code 0xFFE6))  ;; fullwidth symbols
      2
      1)))

(defn- wrap-line-by-units
  [line max-units]
  (if (<= max-units 0)
    [""]
    (loop [chars (seq line) cur [] cur-units 0 out []]
      (if-let [ch (first chars)]
        (let [u (char-units ch)]
          (if (> (+ cur-units u) max-units)
            (recur chars [] 0 (conj out (apply str cur)))
            (recur (next chars) (conj cur ch) (+ cur-units u) out)))
        (if (seq cur)
          (conj out (apply str cur))
          (if (seq out) out [""]))))))

(defn- text-width-px
  [text font-family font-size]
  (let [size (max 1 (long (or font-size 24)))
        units (reduce + 0 (map char-units (str (or text ""))))]
    ;; Python implementation wraps by measured pixel width each appended char.
    ;; Here we use a calibrated approximation per character unit.
    (* units size 0.5)))

(defn- line-height-px
  [font-family font-size]
  (let [size (max 1 (long (or font-size 24)))]
    ;; Keep close to practical glyph height like PIL's textbbox("あ").
    (* size 1.0)))

(defn- wrap-line-by-width
  [line box-w font-family font-size]
  (if (<= box-w 0)
    [""]
    (loop [chars (seq line) cur "" out []]
      (if-let [ch (first chars)]
        (let [candidate (str cur ch)]
          (if (<= (text-width-px candidate font-family font-size) box-w)
            (recur (next chars) candidate out)
            (if (str/blank? cur)
              (recur (next chars) (str ch) out)
              (recur chars "" (conj out cur)))))
        (if (str/blank? cur)
          (if (seq out) out [""])
          (conj out cur))))))

(defn- fit-text-to-box
  [text font-size box-w box-h font-family]
  (let [safe-fs (max 1 (long (or font-size 24)))
        w (max 0 (long (or box-w 0)))
        h (max 0 (long (or box-h 0)))
        line-height (max 1 (long (Math/ceil (line-height-px font-family safe-fs))))
        max-lines (max 1 (long (Math/floor (/ h line-height))))
        lines (->> (str/split (str (or text "")) #"\r?\n")
                   (mapcat #(wrap-line-by-width % w font-family safe-fs))
                   vec)]
    (if (<= (count lines) max-lines)
      (str/join "\n" lines)
      (let [taken (vec (take max-lines lines))
            last-idx (dec (count taken))
            kept-prefix (subvec taken 0 last-idx)
            last-line (or (nth taken last-idx "") "")
            ellipsis "..."
            trimmed-last (loop [s last-line]
                           (let [candidate (str s ellipsis)]
                             (if (<= (text-width-px candidate font-family safe-fs) w)
                               s
                               (if (empty? s)
                                 ""
                                 (recur (subs s 0 (dec (count s))))))))
            final-last (str trimmed-last ellipsis)]
        (str/join "\n" (conj kept-prefix final-last))))))

(defn text-windows
  "Return a seq of {:text :start-sec :end-sec} display windows for a text object.

  chank values:
    \"text\"   — one window covering the full duration (default)
    \"char\"   — one window per cumulative character prefix, spaced by speed ms
    \"string\" — one window per array element, spaced by speed ms

  speed is in milliseconds (default 100)."
  [text-obj duration-sec]
  (let [raw (:text text-obj)
        chank (or (:chank text-obj) "text")
        speed-ms (double (or (:speed text-obj) 100))
        speed-sec (/ speed-ms 1000.0)]
    (case chank
      "text"
      [{:text (text-content raw) :start-sec 0 :end-sec duration-sec}]

      "char"
      (let [full (text-content raw)
            n (count full)]
        (if (zero? n)
          []
          (map-indexed
           (fn [i _]
             {:text (subs full 0 (inc i))
              :start-sec (* i speed-sec)
              :end-sec (if (= i (dec n)) duration-sec (* (inc i) speed-sec))})
           full)))

      "string"
      (let [arr (if (vector? raw) raw [raw])
            n (count arr)]
        (map-indexed
         (fn [i s]
           {:text s
            :start-sec (* i speed-sec)
            :end-sec (if (= i (dec n)) duration-sec (* (inc i) speed-sec))})
         arr)))))

(defn- drawtext-suffix
  [texts duration-sec]
  (when-let [t0 (first texts)]
    (let [pos (:position t0)
          x (long (:x pos))
          y (long (:y pos))
          w (long (:w pos))
          h (long (:h pos))
          fs (long (or (:font_size t0) 24))
          ff (some-> (:font_family t0) str str/trim)
          fc (or (:font_color t0) "ffffff")
          bc (or (:border_color t0) "000000")
          windows (text-windows t0 duration-sec)
          line-height (max 1 (long (Math/ceil (line-height-px ff fs))))
          font-part (if (str/blank? ff)
                      ""
                      (str ":font='" (escape-drawtext ff) "'"))]
      (str/join ""
                (mapcat (fn [{:keys [text start-sec end-sec]}]
                          (let [lines (str/split (fit-text-to-box text fs w h ff) #"\n" -1)]
                            (map-indexed
                             (fn [i line]
                               (selmer/render
                                ",drawtext=text='{{text}}'{{font_part|safe}}:fontsize={{font_size}}:fontcolor=0x{{font_color}}:borderw=2:bordercolor=0x{{border_color}}:x={{x}}:y={{y}}:enable='between(t,{{start}},{{end}})'"
                                {:text (escape-drawtext line)
                                 :font_part font-part
                                 :font_size fs
                                 :font_color fc
                                 :border_color bc
                                 :x x
                                 :y (+ y (* i line-height))
                                 :start start-sec
                                 :end end-sec}))
                             lines)))
                        windows)))))

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
        vout (selmer/render "[{{vsrc}}]format={{pix_fmt}}{{drawtext|safe}}[vout]"
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
                         (audio-encoder-args (effective-audio-codec (:audio_codec scenario) out-path))
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
          child-chroma (chroma-overlay-suffix children-resolved)
          cmd (vec (concat
                    ["ffmpeg" "-y"
                     "-i" parent-path
                     "-i" children-combined-path
                     "-filter_complex"
                     (str "[1:v]format=yuva420p"
                          child-chroma
                          "[child_v];"
                          "[0:v][child_v]overlay=0:0:format=auto[vout];"
                          "[0:a][1:a]amix=inputs=2:duration=first:dropout_transition=0[aout]")
                     "-map" "[vout]" "-map" "[aout]"
                     "-t" (str dur-sec)]
                    (video-encoder-args (:video_codec scenario))
                    (audio-encoder-args (effective-audio-codec (:audio_codec scenario) out-path))
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
