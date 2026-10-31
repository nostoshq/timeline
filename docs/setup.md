# First-time setup, step by step

Timeline's setup opens by itself the first time and stays at the top of **Settings** until everything is done; after
that it is in **Settings, Emulators, First-time setup**. Each step says what it needs, and pressing A does it. You can
leave at any point and come back.

You'll need a handheld running Android 10 or newer with a 64-bit ARM processor, a Wi-Fi connection, your own games,
and about fifteen minutes.

## First: files and the home screen

**Access to your files.** Timeline asks once for access to all files, so it can read your games folder and keep
RetroArch's settings beside it. Press A and Android opens Timeline's page: switch on **Allow access to manage all
files**, then come back.

**Your buttons.** On that first screen, A opens the page. If your handheld sends its A and B buttons the other way
around, press the button you use to say yes twice, and Timeline swaps them. **Settings, Controls, Swap confirm and
back** changes it later.

**Home screen.** Setup's first step offers to make Timeline the home screen, so the Home button and every restart
land on it. You can switch back any time in Android's settings: Apps, Default apps, Home app.

## Your games

Timeline looks for a folder named `ROMs` on the SD card, then in the device's own storage. If there isn't one, press
A and it makes one, on the card if there is a card, with a folder inside for every console and the `bios` folders
for console files. Each new folder holds a short `what goes here.txt`. Copy your games into the console folders
([which folder is which](folders.md)) and Timeline finds them by itself, whenever the card changes.

## Emulators

Setup lists only the emulators your games need, and installs them from their developers:

| Emulator | For | From | About |
|---|---|---|---|
| RetroArch | Everything up to the Dreamcast, Game Boy Advance and DS | buildbot.libretro.com, the newest stable release | 184 MB |
| Dolphin | GameCube and Wii | dl.dolphin-emu.org | 22 MB |
| ARMSX2 | PlayStation 2 | github.com/ARMSX2/ARMSX2, the build for your Android version | 78 MB |
| melonDS | Nintendo DS on two screens, optional | github.com/rafaelvcaetano/melonDS-android | 21 MB |

The Emulators step installs RetroArch, Dolphin and ARMSX2 as your games need them. melonDS is only for playing DS
games across two screens (with a dual-screen attachment or a second display); its own step installs it when you press
A there.

Each download is checked against its developers' signing certificate before Android sees it. A download signed by
anyone else is refused and deleted. Android then asks you to confirm each install. The first time, it also asks
whether Timeline may install apps at all: switch on **Allow from this source** and come back.

If you'd rather install them yourself, from their sites or an app store, setup notices and moves on.

## RetroArch

RetroArch opens once to ask for access to your files: choose **OK**, then **Allow**, then press Home to come back.
From then on Timeline hands RetroArch its settings, shaders and console files each time a game starts. You never
need to open its menus.

## RetroArch's cores

RetroArch runs each console through a "core", and it downloads its own. Setup names exactly which ones your games
need. Press A, then in RetroArch: **Main Menu, Online Updater, Core Downloader**, and pick each one in the list. Press
Home when you're done.

If a game ever says its core is missing, **Settings, Emulators** shows each console's core by name (for example
RetroArch · Snes9x); download that one the same way.

## Shader collection

The CRT and handheld looks are built on RetroArch's shader collection. Press A and Timeline fetches it from libretro
(about 55 MB) and unpacks it where the looks expect it. Once fetched, it is fetched again by itself only if it goes
missing, as when RetroArch is reinstalled.

## Console files

The PlayStation, Saturn, Sega CD, TurboGrafx-CD and PlayStation 2 need a file from the console itself before their
games start. Setup lists the exact file names for the consoles you have games for, and checks each one you add
against known good copies. A PlayStation, Saturn, Sega CD or TurboGrafx-CD game started without its file opens this
list instead of starting.
[What each needs, and how to copy them from consoles you own](console-files.md).

## Each emulator's first run

Dolphin, ARMSX2 and melonDS each ask a question or two the first time they open. Setup opens each in turn and tells
you exactly what to pick, such as the ROMs/gc and ROMs/wii folders for Dolphin, or ROMs/bios/ps2 for ARMSX2's BIOS.

**Dolphin's settings.** One more step lets Timeline into Dolphin's settings folder (Android shows the folder; choose
**Use this folder**, then **Allow**). Timeline then maps your handheld's controls, sets up the picture and keeps it
that way.

## PlayStation 2

In ARMSX2's own settings, switch on its save on exit and load on boot, so a game picks up where you left it. On
Retroid devices, **Settings, Device, Set up ARMSX2 for me** does this for you before each PlayStation 2 game, and keeps
Timeline's 480-line look in ARMSX2's shaders folder. It is off until you switch it on.

## Cover art

Optional. Timeline fetches covers and screenshots from libretro's thumbnail collection, and for arcade games their
year, maker and players from the Arcade Database. It runs in the background, and everything it finds is kept in
`ROMs/_timeline` beside your games, in the layout ES-DE and RetroBat use. Release dates and descriptions for other
consoles come from gamelists already in your library, such as ES-DE's.

## Extra buttons

Optional. Teach Timeline two buttons you don't otherwise use (M1 and M2 on Retroid devices). They become rewind and
fast-forward on the cartridge consoles, and the RetroArch menu and fast-forward elsewhere.

## Done

When nothing is left, the last step reads **All set**. Settings, Emulators keeps the list, so you can always see what
each step did, or come back to one.
