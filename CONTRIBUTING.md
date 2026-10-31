# Building and changing Timeline

Timeline is finished software, offered as is. This repository isn't watched for issues or pull requests, but the
source is yours to read, build, change and share under the GPL. If you make something better from it, that is
exactly what it is for.

## Building

You need JDK 17 and the Android SDK (or Android Studio).

```
./gradlew testDebugUnitTest      # the tests
./gradlew assembleDebug          # a debug APK in app/build/outputs/apk/debug
./gradlew assembleRelease        # an unsigned release APK, or a signed one if keystore.properties is present
```

To sign release builds, put a `keystore.properties` in the project's root folder (it is ignored by git):

```
storeFile=timeline-release.jks
storePassword=...
keyAlias=timeline
keyPassword=...
```

Android won't install a build signed with a different key over the one already there. Uninstall that one first,
which also clears Timeline's settings (your games and everything in `ROMs/_timeline` stay).

## How the code is laid out

| File | What it holds |
|---|---|
| `Core.kt` | The consoles, emulators and how each is launched; RetroArch, Dolphin and ARMSX2 settings; setup steps; pure logic with no Android in it, so it can be tested |
| `Store.kt` | The app's state: the library, play history, saves, starting and returning from games |
| `MainActivity.kt` | The one screen, the buttons, and the stage that scales the interface to 4:3 and 16:9 |
| `Screens.kt`, `Ui.kt`, `Nostos.kt` | Every page, and the pieces they are built from |
| `Shaders.kt` | Timeline's own shaders: the 3D CRT, the signal pass, the television looks and the steady beam |
| `Sources.kt` | Where emulators come from, how downloads are checked, and the console files |
| `Device.kt` | Android and device services: storage, the Retroid service, sound, display |
| `Scraper.kt`, `SaveFiles.kt` | Cover art; save copies and their versions |
| `Feel.kt` | Interface sounds and vibration |
| `tools/make_sounds.py` | The interface sounds, made from scratch |
| `tools/make_media.py` | The wordmark and the pictures in `docs/media` |

## Writing code here

- Explain why, briefly, where the reason isn't obvious. Measurements and the device they were taken on are worth
  keeping in a comment; history isn't.
- Everything that can be tested without a device lives in `Core.kt`, `Sources.kt` or `Scraper.kt` and has a test in
  `CoreTest.kt` or `ScraperTest.kt`.
- On-screen text is calm and specific: no exclamation points, short sentences, and it says what will happen.
- Nothing that talks to the internet runs unless the player started it.
- Anything that changes a device's own settings or hardware modes (fan, power, charging, lights, display) is off until
  the player switches it on.
- Nothing that bundles, downloads or links to games, BIOS files, firmware or keys.
