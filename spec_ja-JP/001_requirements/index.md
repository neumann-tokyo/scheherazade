# 要件

scenario.jsonc を入力として動画を生成するシステムを構築してください。

## 技能要件

- 言語: Python
- 実行環境: uv を使うこと
- ライブラリ: 
  - ffmpeg コマンドを subprocess で呼び出すこと

## コマンドラインツールの作成

`uv run scheherazade.py` コマンドを作って欲しい。

### 動画生成

下記のようなコマンドを作りたい

```
uv run scheherazade.py scenario.jsonc -o youtube.mp4
```

これは scenario.jsonc の timeline から動画を生成するコマンド。

scenario.jsonc の仕様については @spec_ja-JP/001_requirements/scenario-jsonc.md を参照すること

### 音声生成

```
uv run scheherazade.py --generate scenario.jsonc
```

scenario.jsonc 中の `generation` がある項目だけに作用して、音声データなどの生成を行う

## scenario.jsonc と動画の関係

- title
  - YouTube用のタイトル。投稿する際に人間がコピペする
- eescription
  - YouTube用の説明文。投稿する際に人間がコピペする
- screen
  - 作成する動画のサイズ
- fps
  - 作成する動画のfps
- timeline
  - Timeline Object に基づいて動画を作成する
  - Timeline 配列を順番に動画にする
- Timeline Object の Children の扱い
  - timeline, videos, audios, texts の配列の要素については要素数0から最後まで順番に再生する
  - children のものはマルチトラックにして重ねる
  
### videos < audios < children の優先度について

動画生成には次のルールを適用する

- videos のみの場合
  - 画像であれば静止画を表示し続ける
  - 動画であれば動画の終了まで表示し続ける
- videos と audios がある場合
  - videos の長さは最長で audios の長さとする
- videos と audios と children がある場合
  - 親の videos と audios の最長は、children の Timeline が終了する長さとする
  - 逆にいれば、完成動画では children の videos や audios を再生中、常に親 Timeline Object の videos と audios も再生する
  - 映像の表示順としては親 Timeline Object の videos の上に子 Timeline Object の映像を重ねる順番とする
    - 映像内の alpha チャンネルを反映すること
  - audios に関しては、使い方の想定としては、親 Timeline で bgm を再生して、子 Timeline で TTS 音声を再生するような想定
  