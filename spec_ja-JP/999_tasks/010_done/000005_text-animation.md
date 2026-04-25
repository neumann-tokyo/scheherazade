# Text Animation の実装

simple-ffmpegjs ( @/home/kbaba/repos/neumann-tokyo/simple-ffmpegjs ) の text animation typewriter を参考にして、
Text object の `chank`, `speed` 機能を実装してください。

## chank

`chank` は `char`, `string`, `text` のいずれかの値です。

- `char` の場合、Text Object の text の文字を１文字ずつ表示するアニメーションになります (simple-ffmpegjs の typewriter と同様)
- `string` の場合、Text Object の text 配列の要素ごとにアニメーション表示します
- `text` の場合、Text Object の text のすべての文字を一度に表示します (アニメーション不要)

`chank` が `char` または `text` の場合、 text は string でも配列[string] でも振る舞いに影響はありません。

`speed` はアニメーションするとき、何ミリ秒ごとに次の文字を表示するかを設定した値です。
