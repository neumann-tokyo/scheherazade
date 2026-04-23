# generationで設定できるstrategyの定義について

## generation を defmethod で抽象化する

@src/scheherazade/generation.clj において次のように `defmulti`, `defmethod` を使って
strategy を抽象化したい。 (参考 @https://clojuredocs.org/clojure.core/defmethod )

```clj
(defmulti generation (fn [params] (:name params)))

(defmethod generation :elevenlabs [params]
  ;; (:props params) を使って、 elevenlabs APIで TTS 音声を作成する
)

(defmethod generation :voicevox [params]
  ;; (:props params) を使って、 voicevox APIで TTS 音声を作成する
)

(defmethod generation :gemini [params]
  ;; (:props params) を使って、 gemini APIで TTS 音声を作成する
)
```

`generation` 関数の `params` には scenario.jsonc から取得した generation 以下の部分を入れるようにする。

```json
{
  "generation": {
    "name": "elevenlabs",
    "props": {
      "text": "こんにちは",
      "model": "eleven_turbo_v2_5",
      "speaker_id": "3",
      "speed": 1.2
    }
  }
}
```

`props` 以下に strategy ごとに設定できる任意の項目を入れておく。
複数の strategy でまとめられる項目については、できるだけ同じにする。
例えば `"text"` と `"string"` は同じような意味なので `"text"` に統一するなど。
一方で `"speed"` の指定は可能なサービスと不可能なサービスがあるので、可能なサービスでのみ指定できる

### 共通事項

- HTTP 通信には @https://github.com/babashka/http-client を使う
- HTTP 通信による TTS に対応し、Linuxコマンド呼び出しは不要 (voicevoxもHTTP通信で呼び出すこと)
- 読み上げるワードの中に dictionary.json の `word` が含まれていたら `ruby` の文字に変換してから TTS 処理を行う

#### dictionary.jsonの仕様

```json
[
  {
    "word": "脆弱性",
    "ruby": "ゼイジャクセイ"
  },
  {
    "word": "clojure",
    "ruby": "クロージャー"
  }
]
```

### voicevox storategy

voicevox の REST API は次のサイトを参考にしてください。

https://github.com/VOICEVOX/voicevox_engine#%E9%9F%B3%E5%A3%B0%E3%82%92%E8%AA%BF%E6%95%B4%E3%81%99%E3%82%8B%E3%82%B5%E3%83%B3%E3%83%97%E3%83%AB%E3%82%B3%E3%83%BC%E3%83%89

例：

```bash
echo -n "こんにちは、音声合成の世界へようこそ" >text.txt

curl -s \
    -X POST \
    "127.0.0.1:50021/audio_query?speaker=1" \
    --get --data-urlencode text@text.txt \
    > query.json

# sed を使用して speedScale の値を 1.5 に変更
sed -i -r 's/"speedScale":[0-9.]+/"speedScale":1.5/' query.json

curl -s \
    -H "Content-Type: application/json" \
    -X POST \
    -d @query.json \
    "127.0.0.1:50021/synthesis?speaker=1" \
    > audio_fast.wav
```

`generation.props.speed` が設定されている場合には、上記のような方法で速度を変更してください。

voicevox 用の `generation` 要素の例:

```json
{
  "generation": {
    "name": "voicevox",
    "props": {
      "text": "こんにちは",
      "speaker_id": "3",
      "speed": 1.2
    }
  }
}
```

VOICEVOX の URL は環境変数 `SCHE_VOICEVOX_URL` から取得する。

### elevenlabs storategy

次のページを参考にして ElevenLabs の API について理解してください。

- https://elevenlabs.io/docs/api-reference/authentication
- https://elevenlabs.io/docs/api-reference/text-to-speech/convert
  - speed は voice_settings 以下で設定できます

ElevenLabs の URL は環境変数 `SCHE_ELEVENLABS_URL` から取得してください。
ElevenLabs の API KEY は環境変数 `SCHE_ELEVENLABS_API_KEY` から取得してください。

ElevenLabs 用の `generation` 要素の例:

```json
{
  "generation": {
    "name": "elevenlabs",
    "props": {
      "text": "こんにちは",
      "model": "eleven_turbo_v2_5"
      "speaker_id": "fUjY9K2nAIwlALOwSiwc",
      "speed": 1.2
    }
  }
}
```

### gemini tts strategy

次のページを参考にして Gemini TTS のAPIについて理解してください。

- https://ai.google.dev/gemini-api/docs/speech-generation#rest

`Single-speaker TTS` を使用すること。

Gemini TTS 用の `generation` 要素の例:

```json
{
  "generation": {
    "name": "gemini",
    "props": {
      "text": "こんにちは",
      "model": "gemini-3.1-flash-tts-preview"
      "speaker_id": "kore",
      "prompt": "少し早口で興奮した様子で話す"
    }
  }
}
```


