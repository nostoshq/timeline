# Troubleshooting

**A game doesn't start, or comes straight back.** Timeline reads RetroArch's log and usually says why. The usual
reasons, in order:

- The console's core isn't downloaded yet. **Settings, Emulators** shows each console's core by name (for example
  RetroArch · Snes9x); download it in RetroArch's Core Downloader.
- A console file is missing. **Settings, Emulators, First-time setup, Console files** shows which.
- The file isn't a game Timeline can read for that console. Check the [file types](folders.md).

**The screen stays black after a game starts.** Close RetroArch (Android will offer to after a few seconds) and try
again. If it happens with every PlayStation or Dreamcast game, switch **Settings, Picture, PlayStation and Dreamcast:
drawn with** to Compatible.

**Android asks which home app to use every time Home is pressed.** Choose Timeline and **Always**. If it keeps asking
after an update, **Settings, Make this the home screen**.

**GameCube or Wii games don't answer the buttons.** Do setup's Dolphin's settings step, which lets Timeline map the
controls inside Dolphin.

**PlayStation 2 games say there is no BIOS.** ARMSX2 needs to be pointed at `ROMs/bios/ps2` once, from its own
setup. Setup's ARMSX2 step opens it.

**No cover art.** **Settings, Scraper, Scrape now.** Games named with the No-Intro and Redump naming conventions are
found best.

**An emulator download is refused.** Timeline only installs a download signed by the emulator's own developers. If
one is refused, the download was damaged or wasn't the real thing. Try again on another network, or install it
yourself from the developers' site; setup moves on once it is installed.

**A game stutters, or its sound crackles.** If the top of Timeline's screen says **Battery saver on**, that is the
reason: Android's battery saver holds the processor back. Switch it off in Android's quick settings. Otherwise, close
other apps in the background: **Settings, Close everything in the background**. A game whose last sitting had more
than 5% of its pictures late says so on its page (Retroid devices), and X there runs it lighter.

**A PlayStation, Saturn, Sega CD or TurboGrafx-CD game opens the Console files page instead of starting.** That
console can't start without a file from the console itself, and Timeline didn't find one. The page says which file
and where it goes.

**Pressing A goes back, and B confirms.** Your handheld sends its face buttons the other way around. **Settings,
Controls, Swap confirm and back.** (On the very first screen, pressing the other button twice does the same.)

**Saves from before Timeline don't show up.** Timeline gives RetroArch a settings file of its own, with saves in
RetroArch's `saves` folder, sorted by core. Move older saves there, into the folder named for the game's core.

**The battery stops charging at 80%.** That is Retroid's own charge limit, which **Settings, Device, Charging** can
switch on. Set it back to **Always to 100%**. If Timeline has been uninstalled, switch the limit off in Retroid's own
battery settings.

Timeline is offered as is, and this repository isn't watched for questions. Everything it does is in these pages and
in the source, which you are free to read, change and build.
