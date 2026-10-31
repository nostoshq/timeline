# Console files

A few consoles won't start their games without a file from the console itself: the BIOS, the small program the console
runs when you switch it on. These files are copyrighted by the companies that made the consoles. Timeline never
includes them, never downloads them, and never points to anywhere that offers them. The right copy is the one from a
console you own.

## What each console needs

Put each file in the folder named below, inside your ROMs folder, named exactly as shown. Setup checks each one
against the checksums libretro publishes for its cores: **Checked** means it matches a known good copy, **Found** means
it is there but isn't one Timeline knows (it may well work), and **Missing** means it isn't there yet. Sega CD,
PlayStation 2 and Neo Geo files are looked for but not checked, so they show as Found.

| Console | File | Folder | Known good MD5 |
|---|---|---|---|
| PlayStation | `scph5501.bin` (US) | `bios` | 490f666e1afb15b7362b406ed1cea246 |
| | `scph1001.bin` (US) | `bios` | 924e392ed05558ffdb115408c263dccf |
| | `scph7001.bin` (US) | `bios` | 1e68c231d0896b7eadcad1d7d8e76129 |
| | `scph5500.bin` (Japan) | `bios` | 8dd7d5296a650fac7319bce665a6a53c |
| | `scph5502.bin` (Europe) | `bios` | 32736f17079d0b2b7024407c39bd3050 |
| Saturn | `mpr-17933.bin` (US, Europe) | `bios` | 3240872c70984b6cbfda1586cab68dbe |
| | `sega_101.bin` (Japan) | `bios` | 85ec9ca47d8f6807718151cbcca8b964 |
| Sega CD | `bios_CD_U.bin`, `bios_CD_E.bin`, `bios_CD_J.bin` | `bios` | (several revisions work) |
| TurboGrafx-CD | `syscard3.pce` | `bios` | 38179df8f4ac870017db21ebcbf53114 |
| PlayStation 2 | any PS2 BIOS (`.bin`) | `bios/ps2` | (ARMSX2 checks it) |
| Dreamcast (optional) | `dc_boot.bin` | `bios/dc` | e10c53c2f8b90bab96ead2d368858623 |
| Neo Geo games only | `neogeo.zip` | `arcade` | (a Neo Geo BIOS from your own MVS or AES hardware, packaged as FinalBurn Neo expects) |

Any one PlayStation BIOS is enough, and so is one Saturn BIOS. The Dreamcast runs without its BIOS on Flycast's own
replacement; a few games run better with the real one. The Game Boy Advance and Nintendo DS have built-in replacements
and need nothing.

## Copying them from your own consoles

How depends on the console. These guides, from the emulator projects themselves, show the ways that work:

- **PlayStation 2**: [PCSX2's guide to dumping the BIOS](https://pcsx2.net/docs/setup/bios/).
- **PlayStation**: [libretro's Beetle PSX page](https://docs.libretro.com/library/beetle_psx_hw/) lists the files and regions.
- **Saturn**: [libretro's Beetle Saturn page](https://docs.libretro.com/library/beetle_saturn/).
- **Sega CD**: [libretro's Genesis Plus GX page](https://docs.libretro.com/library/genesis_plus_gx/).
- **TurboGrafx-CD**: [libretro's Beetle PCE FAST page](https://docs.libretro.com/library/beetle_pce_fast/), which lists
  `syscard3.pce`. The System Card is a HuCard, read with a cartridge reader like any other.
- **Dreamcast**: [libretro's Flycast page](https://docs.libretro.com/library/flycast/).
- **Everything libretro runs**: [libretro's BIOS guide](https://docs.libretro.com/guides/bios/).

Once a file is in place, setup's **Console files** step turns from missing to checked or found the next time you look.
A PlayStation, Saturn, Sega CD or TurboGrafx-CD game started while its file is still missing opens that same list
instead of starting, so it never fails on a black screen. A file added later is picked up the next time a game starts.
