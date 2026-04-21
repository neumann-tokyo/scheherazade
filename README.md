# Scheherazade (Šahrzād)

名前の由来: [Sheherazade](https://en.wikipedia.org/wiki/Scheherazade)

`scenario.jsonc`（仕様は `spec_ja-JP/001_requirements/`）から動画を生成する CLI です。実行環境は **Babashka**、JSON は **Cheshire**、入力検証は **Malli**、ffmpeg/ffprobe 呼び出しは **babashka/process** です。

## 前提

- [Babashka](https://book.babashka.org/)（プロジェクトルートの `bb.edn` を利用）
- `ffmpeg` と `ffprobe` が `PATH` にあること

## インストール（開発時）

リポジトリをクローンしたディレクトリで依存解決は `bb` が自動で行います。

## コマンド

```bash
bb run sche -- path/to/scenario.jsonc -o youtube.mp4
```

音声だけ先に生成する（`generation` があり、かつ `path` が未作成の Audio Object 向け）:

```bash
bb run sche -- --generate path/to/scenario.jsonc
```

各 `generation.name`（`voicevox` / `gemini` / `elevenlabs`）用に、**外部コマンド**を環境変数で渡します（仕様で参照される `eleven_labs_cli.py` 等と同様に、`path` に書き出すコマンドを想定）。

- `SCHE_GENERATE_VOICEVOX` / `SCHE_GENERATE_GEMINI` / `SCHE_GENERATE_ELEVENLABS`  
  または `SCHE_VOICEVOX_CMD` / `SCHE_GEMINI_CMD` / `SCHE_ELEVENLABS_CMD`  
  値はスペース区切りの argv 先頭（例: `python3 /path/to/cli.py`）。実行時に出力ファイルパスが末尾引数として付与されます。

## テスト

[Babashka: Running tests](https://book.babashka.org/#_running_tests) に沿い、`test_runner.clj` で `clojure.test` を実行します。

```bash
bb test
```

## 実装状況（概要）

- JSONC（`//` / `/* */`、文字列外）→ Cheshire パース
- Malli によるシナリオ検証（`eescription` は入力時に `description` へ正規化）
- タイムラインの論理尺の resolve（`:path->duration-ms` または `:ffprobe-fn` で差し替え可能）
- 単一 Timeline: 画像/動画の scale+pad、任意 chroma 系 `Effect`、`drawtext`（先頭の Text の全表示）、VP9 または H.264 + AAC
- ルート `timeline` が複数ある場合はセグメントごとにエンコードし、`concat` で連結
- `children` の親子 overlay / `amix` / テキストの chank アニメは未実装（該当シナリオはエラーになります）
