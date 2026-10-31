# The pictures in this repository

Every game in these pictures is made up: titles, studios, covers and in-game pictures were all drawn for them. No
real game, character, logo or box design appears anywhere.

| File | What it is | How it was made |
|---|---|---|
| `wordmark-dark.png`, `wordmark-light.png` | The Timeline wordmark for GitHub's dark and light themes | `python3 tools/make_media.py wordmark` |
| `hero.webp` | Moving along the timeline, opening a console and a game | A screen recording of the release build, as an animated WebP |
| `shot-console.png` | A console's games | A screenshot of the release build |
| `shot-game.png` | A game's page | A screenshot of the release build |
| `signals.png` | One frame through composite, S-video and RGB | RetroArch's image viewer with Timeline's own shader chains, then `make_media.py signals` |
| `shot-wide.png` | The timeline on a 16:9 screen | A screenshot at 1920 x 1080 |
| `setup.webp` | First-time setup | A screen recording of a fresh install |
| `nostos.png` | Settings, About NOSTOS | A screenshot of the release build |
| `social.png` | GitHub's link preview, 1280 x 640 | `make_media.py card` |

Screenshots are taken with `adb exec-out screencap -p`. Recordings are made with `adb shell screenrecord`, cut to
frames with ffmpeg and joined with `img2webp` from libwebp, which keeps full color where a GIF would band.

The type is Space Grotesk, under the SIL Open Font License, from `app/src/main/res/font`.
