# Scheherazade (Šahrzād)

名前の由来: [Sheherazade](https://en.wikipedia.org/wiki/Scheherazade)

`scenario.jsonc`（仕様は `spec_ja-JP/001_requirements/`）から動画を生成する CLI です。実行環境は **Babashka**、JSON は **Cheshire**、入力検証は **Malli**、ffmpeg/ffprobe 呼び出しは **babashka/process** です。

## 前提

- [Babashka](https://book.babashka.org/)（プロジェクトルートの `bb.edn` を利用）
- `ffmpeg` と `ffprobe` が `PATH` にあること

## インストール（開発時）

Please use [bbin](https://github.com/babashka/bbin)

```
bbin install https://github.com/neumann-tokyo/scheherazade.git
```

## コマンド

```bash
sche path/to/scenario.jsonc -o youtube.webm
```

音声だけ先に生成する（`generation` があり、かつ `path` が未作成の Audio Object 向け）:

```bash
sche --generate path/to/scenario.jsonc
```

辞書ファイル（読みの置換。省略時はシナリオと同じディレクトリの `dictionary.json`）を明示する場合:

```bash
sche path/to/scenario.jsonc -o youtube.webm -d path/to/dictionary.json
```

`generation.name` ごとの環境変数:

- `voicevox`: `SCHE_VOICEVOX_URL`（例: `http://127.0.0.1:50021`）
- `elevenlabs`: `SCHE_ELEVENLABS_URL`, `SCHE_ELEVENLABS_API_KEY`
- `gemini`: `GEMINI_API_KEY` または `GOOGLE_API_KEY`

## テスト

[Babashka: Running tests](https://book.babashka.org/#_running_tests) に沿い、`test_runner.clj` で `clojure.test` を実行します。

```bash
bb test
```

## 実装状況（概要）

- JSONC（`//` / `/* */`、文字列外）→ Cheshire パース
- Malli によるシナリオ検証
- タイムラインの論理尺の resolve（`:path->duration-ms` または `:ffprobe-fn` で差し替え可能）
- `--generate` / レンダリング前処理で、`generation` がある Audio Object の音声ファイルを API 経由で自動生成
- `dictionary.json` による読み置換（`generation.props.text` に適用）
- 単一 Timeline: 画像/動画の scale+pad、任意 chroma 系 `Effect`、`drawtext`（先頭の Text の全表示）、VP9 または H.264 + AAC
- ルート `timeline` が複数ある場合はセグメントごとにエンコードし、`concat` で連結
- `children` の親子 overlay / `amix` / テキストの chank アニメは未実装（該当シナリオはエラーになります）
