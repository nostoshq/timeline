# Input lag

Old games were made for a television that showed a button press as soon as the console drew it. Emulation on a phone
or handheld adds steps: the emulator's frame, the picture's wait in the graphics queue, the screen itself. Timeline
takes out what it can, and every change below was measured before it was kept.

All measurements were made on a Retroid Pocket Nova (Android 13, 1280 x 960 at 120 Hz), from the timestamps
Android's compositor records for every picture shown (`dumpsys SurfaceFlinger --latency`), with games running still and
then with a stand-in controller pressing a button thirty times a second.

## A frame sooner on the 8 and 16-bit consoles

**Preemptive frames** is RetroArch's lighter cousin of run-ahead. It keeps the last frame ready to run again, and
whenever a button changes, it re-runs that frame with the new input, so the game answers one frame (about 17 ms) sooner.
It costs nothing while you're not pressing anything and a little each time you do.

| Core (console) | Processor use, still | With buttons changing | Pictures |
|---|---|---|---|
| Nestopia (NES) | 18% | 22% | 120 a second, none late |
| Snes9x (Super Nintendo) | 21% | 24% | 120 a second, none late |
| Genesis Plus GX (Genesis, Master System, Sega CD) | 18% | 22% | 120 a second, none late |
| Beetle PCE (TurboGrafx) | 24% | 32% | 120 a second, none late |
| Stella (Atari 2600) | 22% | 28% | 120 a second, none late |
| FinalBurn Neo (arcade) | 19% | 24% | 120 a second, none late |
| Gambatte (Game Boy, Game Boy Color) | 13% | 16% | 120 a second, none late |
| mGBA (Game Boy Advance) | 24% | 35% | 120 a second, none late |

Percent of one processor core. It is left off where it can't work or would cost too much: MAME 2003-Plus (its save
states aren't repeatable, which preemptive frames needs), the Nintendo DS (several megabytes to copy every frame), and
the 3D consoles. **Settings, Picture, Lag reduction on 8 and 16-bit** switches between preemptive frames, run-ahead
and off.

## A shorter queue to the screen

A finished picture waits in a queue before the screen shows it. RetroArch asks Android for three buffers by default and
gets four; Timeline asks for two and gets three, and the wait drops by a whole refresh of a 120 Hz screen:

| | Asking for 3 | Asking for 2 (Timeline) |
|---|---|---|
| NES, SNES, Genesis, TurboGrafx, Atari, arcade, Game Boy, GBA, PlayStation, Dreamcast | 31.4 to 32.1 ms, none late | 22.8 to 23.9 ms, none late |
| Saturn (78% of a core) | 28.2 ms, none late | 20.7 ms, none late |

Docked to a television, Timeline keeps RetroArch's usual queue until it has been measured there. The Nintendo 64 is
drawn with OpenGL, where this setting does nothing.

## Sound sooner

RetroArch keeps 128 ms of sound ready by default, to ride out hiccups. The cartridge consoles, handhelds and arcade
rarely stall, so Timeline gives them 48 ms, and their sound arrives about 80 ms sooner. On the Retroid Pocket Nova,
Android's own count of underruns (the gaps you'd hear as crackle) stayed at zero in play. The 3D consoles and the
Nintendo DS keep 128 ms: a Dreamcast can stall for a tenth of a second while it loads, and a short buffer would turn
that into a gap.

## Even pictures on the Nintendo 64

Most Nintendo 64 games draw fewer than 60 pictures a second: Ocarina of Time 20, GoldenEye between 15 and 25. With
black frame insertion on, the emulator has to hand over a picture at every one of the console's 60 refreshes,
repeated if need be, or the black frame stays up for 25 to 40 ms at a time. Timeline has the core repeat its last
picture on those refreshes. In a demanding game the measured gaps went from 205 in ten seconds to none.
