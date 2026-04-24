# children 対応

Timeline の children を実装してください。

`ffmpeg` の `-i` オプションを複数渡すことで、親Timelineのvideosとaudioに子Timelineのvideosとaudioを重ねてください。
videosのpng画像またはVP9の透明色も反映してください。
子Timelineのvideosとaudiosの終了まで、親Timelineのvideosとaudiosをループしてください。

例えば、次の scenario.jsonc がある場合、

```json
"timeline": [
  {
    "videos": ["01.mp4", "02.mp4"],
    "audios": ["bgm.mp3"],
    "children": [
      {
        "videos": ["child-01.mp4", "child-02.mp4",],
        "audios": ["serifu1.mp3"]
      },
      {
        "videos": ["child-03.mp4", "child-04.mp4",],
        "audios": ["serifu2.mp3"]
      }
    ]
  }
]
```

完成動画は次のイメージ：

- children video track: [== child-01.mp4 ==][== child-02.mp4 ==][== child-01.mp4 ==][child-02.mp4][child-03.mp4][child-04.mp3]
- children audio track: [======================= serifu1.mp3 ====================================][====== serifu2.mp3 =======]
-   parent video track: [01.mp4][02.mp4][01.mp4][02.mp4][01.mp4][02.mp4][01.mp4][02.mp4][01.mp4][02.mp4][01.mp4][02.mp4][01  ]
-   parent audio track: [================= bgm.mp3 ==================][================= bgm.mp3 ==================][=bgm.mp3]

parent timeline の上に children timeline を重ねる。
videos配列の要素を先頭から順番に同じtimeline object のaudiosが終わるまでループする。
timeline object にchildrenがある場合、children timeline のすべての videos と audios が終わるまで、videosとaudioをループ
