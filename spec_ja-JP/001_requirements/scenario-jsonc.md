# Scenario.jsoncの仕様

@scenario-example.jsonc

## Top level 要素

- title
  - Type: string (最大文字数: 100)
  - 必須
  - YouTube のタイトル
- description
  - Type: string (最大文字数: 2000)
  - 必須
  - YouTube の説明文
- screen
  - Type: string
  - 任意 (default: "1920x1080")
  - 出力する動画のスクリーンサイズ。`横幅x縦幅` のフォーマット
- fps
  - Type: string
  - 任意 (default: 30)
  - 出力する動画の fps
- video_codec
  - Type: string
  - 任意 (default: vp9)
- audio_codec
  - Type: string
  - 任意 (default: aac)
- timeline
  - Type: 配列[Timeline Object]
  - 必須

## Timeline Object

- id
  - Type: string
  - 必須
  - Timeline Object が一意になるID。scenario.jsonc の上から順番に 6 桁の連番とする。
- videos
  - Type: 配列[動画/画像ファイルのパス、または Video Object]
  - 任意
- audios
  - Type: 配列[音声ファイルのパス、または Audio Object]
  - 任意
- texts
  - Type: 配列[Text Object]
  - 任意
- children
  - Type: 配列[Timeline Object]
  - 任意

## Video Object

- path
  - Type: string
  - 必須
  - 画像または動画ファイルのパス
  - 画像または動画の大きさが `screen` で指定したサイズではない場合、中央揃えで `screen` の大きさに拡大縮小する
- length
  - Type: integer
  - 任意
  - 何ミリ秒この映像を表示するかの設定
  - 入力がない場合、動画であれば動画の最後まで再生がデフォルト
    - ただし audios 配列または children 配列の全ての再生が終わるタイミングのほうが早ければ、そこで動画の再生をとめる
  - 入力がない場合、画像であれば無限ループする
    - ただし audios 配列または children 配列の全ての再生が終わるタイミングのほうが早ければ、そこで動画の再生をとめる
- effects
  - Type: 配列[Effect Object]
  - 任意

## Audio Object

- path
  - Type: string
  - 必須
  - 音声ファイルのパス
- effects
  - Type: 配列[Effect Object]
  - 任意
- generation
  - Type: Generation Object
  - 任意

## Effect Object

音声の effect では音量を調整する Effect を使えるようにしたい。
映像の effect ではクロマキー合成に対応して、親Timelineの映像に子Timelineの映像を重ねることができるようにしたい。

- name
  - Type: string (enum)
  - 必須
  - エフェクト名
- props
  - Type: Object
  - 任意
  - 渡せる値は name 毎に変化

## Generation Object

Audio Object の `path` で指定されたファイルが存在しないときに、音声生成を行う。将来的には音楽生成にも対応したいが、今のところは TTS (Text to Speech) のみを考える。
下記の `name` で指定したロジック（これは内部的には Template method pattern か strategy pattern などで実装する）名の処理を行って、 path の位置に生成した音声を出力する

- name
  - Type: string (enum: voicevox, gemini, elevenlabs)
  - 必須
  - 生成を行うロジックの名前
- props
  - Type: Object
  - 必須
  - 渡せる値は name 毎に変化

例:

```jsonc
{
  "generation": {
    "name": "elevenlabs",
    "props": {
      "text": "こんにちは",
      "speed": 1.2
    }
  }
}
```

具体的なロジックのサンプルコード:

- @/home/kbaba/repos/gitea/Podcasts/eleven_labs_cli.py 
- @/home/kbaba/repos/gitea/Podcasts/voicevox_cli.py
- @/home/kbaba/repos/gitea/Podcasts/gemini_cli.py

## Text Object

- text
  - Type: string または 配列[string]
  - 必須
  - 表示する文字列
- position
  - Type: Position Object
  - 必須
  - 文字を表示する位置を設定
- font_size
  - Type: string
  - 任意: (デフォルト: "15pt")
  - 表示する文字のフォントサイズ
- font_family
  - Type: string
  - 任意: (デフォルト: "sane-serif")
  - 表示する文字のフォント
- font_color
  - Type: 6桁の16進数
  - 任意: (デフォルト: ffffff)
  - 表示する文字の色
- border_color
  - Type: 6桁の16進数
  - 任意: (デフォルト: 000000)
  - 表示する文字の縁の色
- chank
  - Type: enum (char, string, text)
  - 任意: (デフォルト: char)
  - 文字をどのようなチャンクで表示していくかの設定
    - char: 1文字ずつ表示
    - string: 配列の要素ごとに表示
    - text: 全てを１度に表示
- speed
  - Type: number
  - 任意: (デフォルト: 500)
  - 文字を表示する速度。単位は ms

### その他の仕様

- 全ての文字を表示し終えたら videos の終了まで文字を表示し続けること
- videos, audios の終了までに texts を表示し終えない場合は、残り部分は表示せず次の Timeline Object に進む

### Position Object

- x
  - Type: number
  - 必須
  - X座標
- y
  - Type: number
  - 必須
  - Y座標
- w
  - Type: number
  - 必須
  - 横幅(width)
- h
  - Type: number
  - 必須
  - 縦幅(height)
