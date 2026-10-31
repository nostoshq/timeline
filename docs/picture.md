# The picture

Most of these games were made for a television, and the people who drew them knew it. They drew for a picture that
came down a cable, through the electronics of a set, onto the glass of a tube: soft in some places, sharp in others,
lit line by line. Shown raw on a modern panel, the same art can look harsher and flatter than it ever did. Timeline
puts back as much of that chain as a handheld can run, console by console, and lets you change every part of it.

Everything here is in **Settings, Picture**.

<p align="center">
  <img src="media/signals.png" alt="A detail of one frame through composite, S-video and RGB under the same CRT" width="880">
</p>

## The signal

The first part of the chain is the cable. Timeline sets each console to the cable that suits how it was seen, and you
can switch any of them in **Settings, Picture, Signal**:

| Console | Signal | Why |
|---|---|---|
| Atari 2600 | Composite | It shipped with RF only; composite is the clean version of that picture |
| NES | Composite | RF or composite only, and its palette was chosen for it |
| TurboGrafx-16 | Composite | RF out of the box in the US |
| Genesis | Composite | Its artists dithered stripes for waterfalls and shadows that composite blends into transparency |
| Saturn | Composite | Its mesh transparencies were drawn for composite to blend |
| Super Nintendo | S-video | Common for it, and kind to its colors and hi-res see-through effects |
| PlayStation, Nintendo 64 | S-video | A light softening that suits their 3D |
| Arcade | RGB | Cabinet monitors took RGB directly |
| Dreamcast | RGB | Its VGA cable gave a sharp, computer-monitor picture |

Composite and S-video use the NTSC filter from RetroArch's shader collection at the console's own width. For the
PlayStation and Nintendo 64, which are drawn at four times their detail, Timeline uses a signal pass of its own that
works in fractions of a scanline, the way a television's bandwidth did: color softened over about 1.3 of the
console's pixels, brightness nearly untouched for S-video, and averaged with its neighbors for composite. No dot
crawl.

## The tube

Then the CRT. Five looks from RetroArch's shader collection, each a single pass so it runs on any handheld:

- **GDV Mini Ultra** (the default): scanlines that swell with brightness, a soft glow, no mask to dim the picture.
- **zFast**, **Easymode**, **Lottes Fast**, **CRT-Pi**: lighter or crisper alternatives.
- **Off**: the picture as the console drew it, scaled by whole numbers so every pixel is the same size.

## Motion

A tube lit each part of the picture for a moment and let it fade. Your eye filled in the rest, which is why motion on
a CRT looked sharp. A modern screen holds each picture for the whole frame, and moving things smear.

- **Beam simulator** (on 120 Hz screens): Blur Busters' CRT beam simulator by Mark Rejhon and Timothy Lottes draws
  every console frame twice, putting the light where a tube would have, so scrolling backgrounds and fast sprites
  stay crisp. Bright parts of the picture are spread over both halves so the overall brightness holds.
- **Black frames**: a simpler version for screens or consoles where the beam can't run, such as the Nintendo 64 under
  OpenGL.
- **Off**.

The Dreamcast gets a steady version of the beam, written for Timeline. When a Dreamcast game loads, the emulator hands
over a new picture only every tenth of a second, and an ordinary beam would blink ten times a second all through the
load. Timeline's version flashes moving pixels exactly as the original does and holds still ones steady at the same
brightness, so loading screens and pauses sit quietly.

## The 3D consoles

The PlayStation, Nintendo 64, Saturn and Dreamcast drew low-resolution 3D: 240 lines, give or take, or 480 on the
Dreamcast. Drawn at their own resolution on a modern screen, their polygons shimmer and their edges crawl. Drawn at a
higher resolution, they look clean but lose the scanlines that made them look like themselves.

Timeline does both. The PlayStation and Nintendo 64 are drawn at four times their detail and the Dreamcast at twice,
then every group of lines is averaged back to the console's own count before the CRT, so the edges stay clean and the
scanlines are real. The Saturn is drawn at its own resolution. A game
that struggles can be run at its own resolution from its page: **Options, Run lighter**.

## GameCube, Wii and PlayStation 2

These sent 480 lines to a television. A CRT look made for 240-line consoles draws them wrong, with lines far too far
apart. Timeline writes Dolphin a look of its own: one line and one gap for each of the console's 480, deepest in the
dark parts as a bright line spreads over its neighbor, and an aperture grille of red, green and blue stripes in place
of a dark mask. On a television, the spacing is rounded to whole rows of its screen so the lines never beat against
them. On Retroid devices, PlayStation 2 games get the same look through ARMSX2, with **Settings, Device, Set up
ARMSX2 for me** switched on.

## The handhelds

Handheld screens were their own thing:

- **Game Boy and Game Boy Color**: the grid of the LCD, and the unlit screen showing through the pale pixels with a
  faint shadow underneath, the way it looked in daylight.
- **Game Boy Advance**: the same grid, at whole-number scaling.
- **LCD3x**, **LCD grid** or **Off** in **Settings, Picture, Handhelds: screen**.

## Shape and size

Every console keeps its own shape: 4:3 for the televisions, the handhelds at their own proportions. On a 4:3 screen
like the Retroid Pocket Nova's, a 4:3 console fills it. On a 16:9 screen, it sits in the middle at full height.
**Whole-number scaling** keeps every console pixel the same size where the console's resolution allows it.
