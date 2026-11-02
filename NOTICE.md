# Notices

Timeline is Copyright (C) 2026 NOSTOS, LLC. It is free software, released under the GNU General Public License,
version 3 or (at your option) any later version. See [LICENSE](LICENSE).

It includes work by others, used under their licenses. The full license texts are in [LICENSE](LICENSE) and
[LICENSES](LICENSES), and travel inside the app (`assets/licenses`); Settings, About Timeline lists them.

| What | By | License | Where it is used |
|---|---|---|---|
| Space Grotesk (Regular, Medium, Bold), cut to fixed weights from the variable font | Florian Karsten and the Space Grotesk Project Authors | SIL Open Font License 1.1 ([text](LICENSES/OFL-1.1-SpaceGrotesk.txt)) | `app/src/main/res/font`: every word on screen |
| CRT beam simulator, adapted as Dreamcast's steady beam | Mark Rejhon (Blur Busters) and Timothy Lottes | MIT ([text](LICENSES/MIT-crt-beam-simulator.txt)) | `SteadyBeam` in `Shaders.kt` |
| GDV Mini Ultra (`CRT - Guest - Nomask w. Curvature`), ported to GLSL and modified | guest(r), with work by DariusG | GPL-2.0-or-later ([text](LICENSES/GPL-2.0.txt)) | `Crt3d` in `Shaders.kt` |
| Retroid Pocket Nova display calibration (`gamma22.npatch`) | pippopapera | MIT ([text](LICENSES/MIT-nova-display-calibration.txt)) | `app/src/main/assets/display`, offered on that device only |
| Emulator launch details (package and activity names, how each emulator is handed a game), following ES-DE's Android rules | Northwestern Software AB, Leon Styhre, Alec Lofquist | MIT ([text](LICENSES/MIT-ES-DE.txt)) | `Emulators` in `Core.kt` |
| Kotlin standard library, Jetpack Compose and AndroidX | The Kotlin and Android Open Source Project authors | Apache-2.0 ([text](LICENSES/Apache-2.0.txt)) | the app's code |
| Coil | Coil Contributors | Apache-2.0 ([text](LICENSES/Apache-2.0.txt)) | loading cover art |
| OkHttp and Okio | Square, Inc. | Apache-2.0 ([text](LICENSES/Apache-2.0.txt)) | inside Coil |
| kotlinx.coroutines | JetBrains s.r.o. and contributors | Apache-2.0 ([text](LICENSES/Apache-2.0.txt)) | the app's code |
| Public Suffix List | Mozilla Foundation and contributors | MPL-2.0 ([text](LICENSES/MPL-2.0.txt)); source at [publicsuffix.org](https://publicsuffix.org) | inside OkHttp, unchanged |

## Not included

Timeline includes no emulator, core, shader collection, game, BIOS or firmware. When you ask it to, setup downloads:

- **RetroArch** (GPL-3.0) from libretro's build server, **Dolphin** (GPL-2.0-or-later) from dl.dolphin-emu.org,
  **ARMSX2** (GPL-3.0) and **melonDS** (GPL-3.0) from their GitHub releases. Each keeps its own license and is checked
  against its developers' signing certificate before it is installed.
- **RetroArch's shader collection** from libretro's build server. Each shader in it carries its own license.
- **RetroArch's cores**, which RetroArch downloads itself, each under its own license. Some (Snes9x, Genesis Plus GX,
  FinalBurn Neo, MAME 2003-Plus) are licensed for non-commercial use only.
- **Cover art**, on request: pictures from the [libretro-thumbnails](https://github.com/libretro-thumbnails) collection,
  and for arcade games their details, scraping using Arcade Database by motoschifo
  ([adb.arcadeitalia.net](http://adb.arcadeitalia.net)). What is fetched stays on your device.

## Trademarks

All product names, game titles, company names and trademarks belong to their owners, and are used only to say which
games run where. That includes Nintendo, Sony, Sega, Atari, NEC, SNK, Microsoft, id Software, Retroid and the names of
their consoles and devices, MAME, and the RetroArch, libretro, Dolphin, ARMSX2 and melonDS projects. Timeline is not
affiliated with, endorsed by or supported by any of them.

## Pictures in this repository

Every game in the screenshots and recordings is made up: titles, studios, covers and in-game pictures were all drawn
for them. No real game, character, logo or box art appears.
