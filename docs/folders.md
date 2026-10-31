# Folders

Timeline reads one folder, `ROMs`, with a folder inside for each console. Setup makes all of them for you. If your
library is already laid out for ES-DE, RetroBat or a handheld's own front end, Timeline reads it as it is: the other
names below are recognized, in any mix of upper and lower case.

| Console | Folder | Also recognized | File types |
|---|---|---|---|
| Atari 2600 | `atari2600` | `2600`, `atari` | a26, bin, zip, 7z |
| Arcade | `arcade` | `fbneo`, `mame`, `fba` | zip, 7z |
| NES | `nes` | `famicom`, `fc` | nes, fds, unf, zip, 7z |
| Master System | `mastersystem` | `sms`, `segamastersystem` | sms, zip, 7z |
| TurboGrafx-16 | `tg16` | `pcengine`, `pce`, `turbografx16`, `turbografx` | pce, sgx, zip, 7z |
| Genesis | `genesis` | `megadrive`, `md` | md, gen, smd, zip, 7z |
| Game Boy | `gb` | `gameboy` | gb, zip, 7z |
| TurboGrafx-CD | `tg-cd` | `pcenginecd`, `tgcd`, `turbografxcd` | chd, cue, m3u |
| Super Nintendo | `snes` | `sfc`, `superfamicom`, `supernintendo` | sfc, smc, zip, 7z |
| Game Gear | `gamegear` | `gg` | gg, zip, 7z |
| Sega CD | `segacd` | `megacd`, `scd` | chd, cue, m3u |
| PlayStation | `psx` | `ps1`, `playstation` | chd, cue, m3u, pbp, iso |
| Saturn | `saturn` | `segasaturn` | chd, cue, m3u |
| Nintendo 64 | `n64` | `nintendo64` | z64, n64, v64, zip, 7z |
| Game Boy Color | `gbc` | `gameboycolor` | gbc, gb, zip, 7z |
| Dreamcast | `dreamcast` | `dc` | chd, gdi, cue, cdi, m3u |
| PlayStation 2 | `ps2` | `playstation2` | chd, iso, cso, m3u |
| Game Boy Advance | `gba` | `gameboyadvance` | gba, zip, 7z |
| GameCube | `gc` | `gamecube`, `ngc` | rvz, iso, gcz, ciso, gcm, wia, m3u |
| Nintendo DS | `nds` | `ds`, `nintendods` | nds, zip, 7z |
| Wii | `wii` | | rvz, iso, wbfs, wad, gcz, ciso, wia, m3u |
| PSP | `psp` | | chd, iso, cso, pbp |
| Xbox | `xbox` | | iso, xiso |
| Nintendo 3DS | `n3ds` | `3ds` | 3ds, cci, cxi, zcci |
| Wii U | `wiiu` | | wua, wud, wux, rpx |
| PS Vita | `psvita` | `vita` | psvita |
| ScummVM | `scummvm` | | scummvm, svm |
| DOOM | `doom` | | wad, iwad, pwad |
| Quake | `quake` | | pak |

Console files go in `ROMs/bios` (`ROMs/bios/ps2` for the PlayStation 2, `ROMs/bios/dc` for the Dreamcast). See
[console files](console-files.md).

## Names

Name files with the No-Intro and Redump naming conventions (`Title (USA).sfc`, `Title (Japan) (En).chd`). Then cover
art is found by name, titles read cleanly, and region tags show as small labels. Timeline reads the region from
the name, and games made for European 50 Hz televisions are set up for that.

## Games on more than one disc

A game on several discs shows once, laid out any of these ways:

- **Discs side by side**, named the Redump way: `Big Adventure (USA) (Disc 1).chd`, `Big Adventure (USA) (Disc 2).chd`.
  Timeline puts them together and writes RetroArch the playlist it needs, in `ROMs/_timeline/playlists`, so there is
  nothing to write yourself.
- **A playlist beside the discs**, `Big Adventure (USA).m3u`, naming each disc file on its own line. The discs it
  names aren't listed again.
- **A folder named like the game with an `.m3u` ending**, holding the discs and the playlist:

```
psx/
  Big Adventure (USA).m3u/
    Big Adventure (USA).m3u
    Big Adventure (USA) (Disc 1).chd
    Big Adventure (USA) (Disc 2).chd
```

When the game asks for the next disc: in RetroArch, hold START for its menu, then Disc Control. On Retroid handhelds,
while the game is open in RetroArch, its Options page in Timeline has **Next disc**, which does it for you. Dolphin changes GameCube discs by itself.

Each console folder that setup makes holds a short `what goes here.txt` naming its file types and emulator. It is
safe to delete.

## What Timeline adds

Everything Timeline keeps about your games lives in `ROMs/_timeline`, so it travels with the card: covers and
screenshots, gamelists, your play history, your own shelves, finished and hidden games, copies of your saves with
their earlier versions, and the playlists it writes for games on several discs. Delete it and your games are
untouched; you lose only what Timeline added. Favorites and settings stay in the app.
