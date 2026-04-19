# Scenario.jsoncの仕様

@scenario-example.jsonc

## Top level 要素

- title
  - Type: string (最大文字数:)
  - 必須
  - YouTube のタイトル
- description
  - Type: string (最大文字数:)
  - 必須
  - YouTube の説明文
- promotion
  - Type: boolean
  - 任意 (デフォルト: false)
  - 宣伝を含むかどうかの設定値
- license
  - Type: string (enum)
  - 任意: (デフォルト: )
  - ライセンス表記
- 他にも色々必要(youtube参照)
- timeline
  - Type: Timeline Object
  - 必須

## Timeline Object

- id
  - Type: uuid
  - 必須
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

- name
  - Type: string (enum)
  - 必須
  - エフェクト名
- props
  - Type: Object
  - 任意
  - 渡せる値は name 毎に変化

## Generation Object

- name
  - Type: string (enum)
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
