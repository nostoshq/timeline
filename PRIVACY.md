# Privacy

Timeline has no account, no analytics, no advertising and no crash reporting. It never sends your name, your
device's identifiers or a list of your games anywhere.

It goes online only for these, each of which you start:

| When | Where | What is sent |
|---|---|---|
| Setup installs an emulator | buildbot.libretro.com, dl.dolphin-emu.org, github.com, api.github.com and GitHub's download servers | an ordinary download request |
| Setup fetches the shader collection | buildbot.libretro.com | an ordinary download request. Once you have fetched it, Timeline fetches it again by itself only if it goes missing, as when RetroArch is reinstalled |
| You fetch cover art | thumbnails.libretro.com | each game's name, as the address of its picture |
| You fetch cover art for arcade games | adb.arcadeitalia.net | each game's set name, such as `sf2` |
| You open the store's site or the source code | nostos.market or github.com, in your browser | what any browser sends |

Every request is made over HTTPS and names Timeline and its source code address, nothing more.

**RetroAchievements.** If you sign in to RetroAchievements in Timeline's settings, your username and password are
written to RetroArch's settings file on your device, so RetroArch can sign in. Once RetroArch has signed in it keeps a
login token instead, and Timeline removes the password from that file. Timeline itself never contacts
RetroAchievements.

Your games, saves, play history and settings stay on your device and SD card. A crash note, if Timeline ever closes
unexpectedly, is written to `Timeline/crash` on the device for you to read and is never sent.
