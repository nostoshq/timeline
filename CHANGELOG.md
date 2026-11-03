# Changelog

## 1.8.1

- **Banjo-Tooie without flickering characters.** Its character models flickered every few seconds with GLideN64's
  frame buffer emulation, which is now off for this game only. The picture keeps its size and place and fills the
  thin border around it.
- **Less slowdown in Jet Force Gemini.** The console's processor runs at four times its speed for it. From the same
  15 seconds of play it went from 26.5 pictures a second to 30, the most the game draws. Turok 2, Conker and
  Banjo-Tooie gained little or nothing, so they keep the console's speed.

## 1.8.0

- **Doom 64 on two sticks.** The left stick walks and strafes and the right stick turns, as a modern shooter plays.
  R2 fires, L2 runs while held, Y uses doors and switches, X opens the map, and A and B change weapons, as in the
  game. It is part of **Settings, Controls, Nintendo 64 shooters: modern controls**; off gives Doom 64 the console's
  own controls.
- **Doom 64's page says where its brightness is.** Doom 64 is a dark game, and its own Brightness slider makes it
  much easier to see on a handheld screen: Options, Display, Brightness.

## 1.7.0

- **GoldenEye 007 and Perfect Dark on two sticks.** With one controller in hand, the left stick walks and strafes,
  the right stick looks, L2 aims and R2 fires, as a modern shooter plays. Both games have a style for two
  controllers, one in each hand (2.4, GoldenEye's Goodhead), and Timeline drives both from the one pad. Choose it once
  in the game, with Look Up/Down set to Upright in GoldenEye or Reverse Pitch unchecked in Perfect Dark, since both
  start with up and down the flight-stick way; each game's page says where. With a second player's controller
  connected, both keep their usual controls.
- **A finer stick for both.** The dead zone is 5% instead of 15%, and the stick reaches further on its diagonals, so
  turning while looking up or down no longer drags.
- **Less slowdown.** The console's processor runs at four times its speed for GoldenEye and twice for Perfect Dark.
  From the same 15 seconds of play, GoldenEye went from 39.5 pictures a second to 57.2 and Perfect Dark from 41.8 to
  45.6, with none late on the screen.
- **GoldenEye's menus without flicker.** Its watch reads the game's picture back for the background behind it, and at
  eight times one picture in seven came late. GoldenEye is drawn at four times, as Pokemon Snap is.
- **Modern controls are a setting.** **Settings, Controls, Nintendo 64 shooters: modern controls** is on to start
  with. Off gives GoldenEye, Perfect Dark, both Turoks and Sin and Punishment the console's own controls.
- **Dreamcast games at 30 a second play evenly.** Jet Grind Radio, MDK2, Shenmue and the others that draw 30 pictures
  a second no longer judder: RetroArch is told their rate, so each picture is held for the same time. Jet Grind Radio
  went from 45% of its pictures late to none.

## 1.6.0

- **The sharper look is the default.** The 3D consoles are drawn at the largest whole multiple each keeps smooth on a
  Retroid Pocket Nova, then brought back to the console's own lines for the CRT: the PlayStation at four times its
  detail, the Nintendo 64 at eight (2560 by 1920, averaged down to 1280 by 960, which smooths every edge), the
  Dreamcast at four (drawn the compatible way, it stays at twice), and GameCube and Wii at three. Pokemon Snap, which
  reads its pictures back to check your photos, is drawn at four. A choice made before under **Settings, Picture, 3D
  consoles: sharper than the console** stays; switch it off for the true picture.
- **Xenoblade Chronicles keeps your place.** 1.5.0 moved it to the Classic Controller, and a place kept on the remote
  and Nunchuk stopped on "Connect the Nunchuk". A place kept with an earlier version now goes on with the remote and
  Nunchuk; once you save in the game and close it from Dolphin's menu, it starts on the Classic Controller.
- **Nintendo 64 blending.** Every game uses GLideN64's accurate blending, which Blast Corps alone had before (the
  faster kind for phones lost its banners and logo). On a Retroid Pocket Nova it costs 1 or 2% of the graphics chip.
- **Rumble for four more Nintendo 64 games.** Perfect Dark, WWF No Mercy, Ken Griffey Jr. and Diddy Kong Racing save
  on the cartridge and used the Controller Pak only for extras, so they now shake. Diddy Kong Racing's page
  says how to keep a time trial ghost. Excitebike 64 keeps its pak, which holds its own tracks.
- **Twin sticks for three shooters.** Turok, Turok 2 and Sin and Punishment, made with walking on the C buttons, move
  on the left stick and aim with the right, with Z on R2 and L2, as a modern shooter plays. GoldenEye and Perfect Dark
  keep their usual controls, which start with walking and turning on one stick.
- **GameCube and Wii rumble.** Dolphin's controls now include each controller's motor, so GameCube games and the Wii
  Remote shake the controller in your hands.

## 1.5.0

- **The Game Boy's three screens.** With **Settings, Picture, Handhelds: screen** at As the console, L1 and R1 change a
  Game Boy game's screen while it plays: the first Game Boy's green dot matrix, the Pocket's black and white one, or
  the game as a Game Boy Color showed it, in the colors the Color's start-up gave that cartridge, on the Color's own
  screen. Each game starts in the screen it was left in. The palettes are SameBoy's, and the Color's screen follows
  Pokefan531's model of its colors.
- **Wii games held their own way.** Xenoblade Chronicles plays on the Classic Controller. In Metroid Prime 3, R2 and
  the right stick push, pull and twist its handles; in Super Mario Galaxy and Super Mario Galaxy 2, R2 stands the
  remote up and the left stick tilts it; in New Super Mario Bros. Wii, the right stick tilts it; in Skyward Sword, the
  right stick swings the sword. A game that takes nothing but the remote is played with it held sideways. Each game's
  page says how.
- **GameCube fixes.** EA's videos in MVP Baseball 2005 and NBA Street Vol. 2 lose their vertical lines; Super
  Mario Strikers' loading picture, Tony Hawk's Pro Skater 3's videos, Resident Evil's map, Resident Evil 4 and Luigi in
  Luigi's Mansion's intro are drawn correctly. Luigi's Mansion starts from its title every time, since loading a kept
  place breaks its memory card: save in the game.
- **Nintendo 64.** 25 games made for the Rumble Pak shake on every port; the core had put a Controller Pak in port 1
  and nothing in the others. The games that save to a Controller Pak keep it, as on the console, and their page
  says so. Banjo-Kazooie's jigsaw transitions, Blast Corps' banners, Majora's Mask's Pictograph Box and Mario Tennis's
  scoreboard are drawn correctly. Killer Instinct Gold's six attacks each have a button: punches on
  Y, X and R1, kicks on B, A and R2.
- **PlayStation.** The racers and the games made for the sticks start with the pad in analog mode, as DuckStation
  starts them: Gran Turismo 2, Ridge Racer Type 4, Crash Team Racing, Metal Gear Solid, Silent Hill and others.
  Discs always read at the console's own speed, which Symphony of the Night, Driver and the films of Final Fantasy
  VIII and Chrono Cross need. Snake no longer walks through walls in Metal Gear Solid. In the sharper look, models hold
  still in Spyro, Tekken 3, Vagrant Story and others, and Beetle PSX's textures no longer warp. Resident Evil 3's discs
  no longer hang, Resident Evil 2 keeps its lines of dialogue and Crash Bandicoot: Warped its intro's sound. Gran Turismo 2's
  Arcade disc races the cars won on its Simulation disc, and Suikoden II finds a Suikoden save in its second card
  slot, as on the console.
- **Saturn and Dreamcast.** NiGHTS, Burning Rangers, Panzer Dragoon Saga and Shining Force III play on the 3D Control
  Pad, its stick and analog triggers included; SELECT switches it between analog and digital, as the pad's own switch
  did. Crazy Taxi's Crazy Box no longer breaks up, Marvel vs. Capcom 2 and Street Fighter III: 3rd Strike no longer
  warp and tear in the sharper look, and Rayman 2 starts, going without achievements.
- **NES, TurboGrafx-16 and arcade.** On the Super Mario Bros., Duck Hunt and World Class Track Meet cartridge, Duck
  Hunt is played by touching the screen, and docked the second pad plays Luigi; before, the second port had a Power
  Pad the core cannot read. L2 no longer switches the TurboGrafx-16 pad to six buttons, which no HuCard was made for.
  Capcom's six-button fighters put heavy punch and kick on R1 and R2, as on a fight pad. Shinobi's text is in English,
  Ghouls'n Ghosts and Strider let a second pad play at the same time, and BurgerTime stands upright.
- **The handhelds.** On the DS, R2 changes the screens again, R3 touches at the pointer and L2 is the microphone,
  after melonDS DS moved them in 2025. The microphone blows, for Phoenix Wright's fingerprints and Bowser's giant
  battles, and the pointer shows for three seconds after it moves. Touch-screen games such as
  Kirby: Canvas Curse and Elite Beat Agents start with the touch screen large, and Sonic Rush, Contra 4 and The World
  Ends with You with both screens. Castlevania: Order of Ecclesia no longer sticks after its intro. The Oracles,
  Shantae and Wendy: Every Witch Way run as on a Game Boy Advance, with the extras they hold for it. The Game Boy
  Advance WarioWare played by turning the handheld reads the handheld's motion. The Pokémon games leave with START
  and SELECT pressed twice, so their own reset, which takes both, still works.
- **Sega CD saves.** The RAM cart is kept for each game, as the console's own memory already was, instead of one cart
  shared by every game. Shining Force CD's saves need it.
- **Notes for single games.** A game's page says what is special about it: Metal Gear Solid's codec frequency and
  Psycho Mantis, StarTropics' Sub-C frequency, Boktai's sun on R3 and L3, Pokémon Stadium's Transfer Pak, the
  GameCube games whose Game Boy Advance extras cannot be used here, and others.

## 1.4.0

- **Brighter televisions and arcade monitors.** The sets had the same light budget the Dreamcast's monitor had before
  1.3.1, so dark rooms in Resident Evil or Silent Hill sank into black. The shadows now come back to the panel's, about
  a third of the raw picture's light in dark grays instead of a fifth, with the lines and the slot mask still plainly in
  view.
- **480 lines on RGB.** A 480-line picture on an RGB cable was drawn as separate lines under the slot mask and came out
  as a checkerboard. Every television cable now blends the two fields as a set did, in either look; only the
  Dreamcast's VGA box keeps its lines.
- **PlayStation gets a DualShock.** Every port now has the analog controller instead of the plain digital pad, so the
  games made for its sticks play with them, and Ape Escape starts. It powers on in digital mode, as the real one did,
  with the left stick working the d-pad; a game that wants the sticks turns them on itself.
- **Link's Awakening saves.** Its only save screen is A, B, START and SELECT together, which also closed the game.
  START and SELECT now leave it when pressed twice; RetroArch says so after the first.
- **Upright arcade games on a television** stand upright in the middle of the screen with their usual controls,
  rather than lying on their side.
- **Nintendo 64 on an Xbox controller.** Player 2's A and B now match the labels, as in Dolphin.
- Pokemon Yellow runs as a Game Boy under the Game Boy's own screen, so its four shades stay apart. DS sound gets the
  48 ms buffer the other systems have. Paper Mario's intro draws correctly. PAL Nintendo 64 games skip black frames.
- **Fewer surprises.**
  - A game you left while it was getting ready no longer opens behind you.
  - Going back to the RetroArch game that is already open no longer asks about other emulators.
  - Dolphin's files are not written while a game is running.
  - Closing an emulator waits for Dolphin to finish putting the last game away.
  - Another game's GameCube or Wii place is renamed aside rather than cleared.
  - A save put back for a GameCube or Wii game sets its place aside.
  - Charge care counts a game left asleep on the charger as not in use.
  - The panel's rate is read as its best, not the 60 it can still show just after undocking.
- **Menus.**
  - The button hints show only what the highlighted row answers to, and every setting's note has room for six lines.
  - Leaving instructions follow Keep your place, and on GameCube and Wii they say Home keeps it only where it does.
  - Run lighter appears only where it changes something.
  - "1 game", long titles cut the same way everywhere, and clearer Signal, motion and sharper-look notes.

## 1.3.2

- **No more asking about an emulator that is closed.** An emulator closed from its own menu, hakuX especially, can
  leave an empty process behind with no screen. Timeline took that for an open emulator and asked about it before
  every game. An emulator now counts as open only while it has a screen.

## 1.3.1

- **Dreamcast, bright again.** Its VGA monitor had sunk dark games such as MDK2 into near black: the gaps between its
  480 lines, the aperture grille, a 9300 K white and the beam each took light, and together they left about a seventh
  of it in the shadows. The lines are now plain in dark and middle tones and bloom shut in bright ones, as a monitor's
  did; the shadows are lifted back to the panel's; the monitor takes your chosen white; and the Dreamcast goes without
  the beam. The grille and the lines are still in view.

## 1.3.0

- **Docked: 60 Hz, and a mask that holds still.** Docked to a television, games are always set for 60 Hz, which every
  television shows; the 120 Hz choice is gone. **Settings, Display, Television**: 60 Hz asks a television showing
  another rate for 60, its own choice leaves it as it is. Under 4K, the phosphor mask is drawn two screen columns to a
  stripe, so the dock's scaling no longer makes it shimmer.
- **Sound sooner on the 3D consoles.** PlayStation, Nintendo 64, Saturn and Dreamcast now get the 48 ms sound buffer
  too, about 80 ms sooner, after a first minute of Banjo-Kazooie, Crash Bandicoot, Panzer Dragoon II and Crazy Taxi
  with no underrun.
- **A stutter log** (Retroid devices). Android's own frame record is switched on as a game starts and read when you come
  back, so nothing runs beside the game. A game whose last sitting had more than 5% of its pictures late says so on
  its page, and X runs it lighter.
- **Emulators left in the background.** The memory check now runs before every game, not only before the big
  emulators' games: a Dolphin left up from a GameCube sitting was enough to drop a Nintendo 64 game's frames and sound
  in its first minutes. A Dolphin holding no game is closed as you come back to Timeline, and is no longer left awake
  after its saves are copied.
- The Nintendo 64 names OpenGL in its settings outright.

## 1.2.0

- **A CRT you can see.** The tube is now drawn as one seen up close, its phosphors in view and not only its lines:
  - The television consoles, from the Atari 2600 to the PlayStation, Nintendo 64 and Saturn, get a slot mask, red,
    green and blue slots in staggered rows, as most American sets had. Arcade monitors get the same, at full
    strength. The Dreamcast's VGA monitor gets the stripes of an aperture grille.
  - A tenth more light and a little glow in bright parts give back what the mask takes, so the picture keeps its
    brightness.
  - GameCube, Wii and PlayStation 2 get a rebuilt 480-line tube: beams that spread with brightness and dark gaps
    between them, an aperture grille, and glow. On component, GameCube and Wii now skip the blending games use
    against flicker on interlaced output, so the picture under the tube is sharp; on S-video and composite they keep it.

## 1.1.1

- **The measured color calibration on the Retroid Pocket Nova, as it was measured.** The Nova's firmware tints the
  whole screen a pale blue after everything else: red to 93%, green to 97%, white near 6800 K. The calibration was
  measured on an untinted screen, so with both in place the white sat cooler than D65, and every console's picture
  with it. Calibrated now turns the tint off and keeps it off; Stock puts it back.

## 1.1.0

- **The true picture.** Every console is now set up to look the way it did on the screen it was played on in an
  American home, from the consoles' own clocks, levels and cables:
  - Composite by default for the consoles whose art leaned on it, from the Atari 2600 to the PlayStation and Saturn,
    with RF offered for those that shipped with it; arcade on RGB; and from the Nintendo 64 on, the best connection
    each had: S-video for the Nintendo 64, VGA for the Dreamcast, component for GameCube, Wii and PlayStation 2.
  - The NTSC filter timed to each console: the NES and SNES's crawling artifacts, the Genesis and Master System's
    still rainbow, SNES hi-res effects blended as a set blended them, and the Atari 2600 through Stella's own filter.
  - The tube without the extra glow, brightness and saturation that clipped white and flattened the brighter half of
    the picture; the tube's own gamma; each scanline centered on its line (it sat half a line high); no mask.
  - An American set's color and white, about 8000 K, with 6500 K and Japanese 9300 K as choices.
  - The screen of each period where it was not a living-room set: an arcade monitor seen up close, its mask showing,
    at 9300 K; and for the Dreamcast's VGA box, a PC monitor of 1999 with fine distinct lines.
  - The Genesis's measured color levels, NES color from an American set's decoder (pinned, so it no longer depends
    on when the core was built), and the TurboGrafx's composite palette.
  - Each console's own pixel shape: the Master System with its border, the SNES at 8:7, the Game Gear's wider pixels.
  - PlayStation and Dreamcast at their own resolution, with the PlayStation's dithering and wobble; Nintendo 64 brought
    back to its own 320 by 240 both ways; 480-line pictures without gaps between their lines, as interlaced pictures
    looked; Saturn's interlaced games drawn field by field. The sharper look stays one switch away.
  - GameCube and Wii at their own resolution and told which cable is in, so on composite the games keep their own
    blending against flicker; GameCube, Wii and PlayStation 2 through a rebuilt 480-line television: on component,
    the HD CRT of the day, with fine distinct scanlines.
  - The handhelds as their screens were: the Game Boy's green dot matrix and smear, the Game Boy Color's washed-out
    color, the Game Boy Advance's darker unlit screen, the Game Gear's slow backlit one, and the DS's color.
  - The beam simulator at half the light each refresh, its own recommendation for 120 Hz.
- **The Retroid Pocket Nova's speakers, measured again**, in the hands and on a desk as well as in front: a more even
  curve from 400 Hz to 6.3 kHz, and the treble, where these speakers fall away steeply, lifted up to 12 dB instead of 6.

## 1.0.0

The first public release.

- **The timeline.** Every console you own games for, in the order it came out, from the Atari 2600 to the Wii, with
  shelves of your own, favorites, finished games, search, Surprise me, and the games that came out on this day.
- **First-time setup** that makes your games folder, installs RetroArch, Dolphin and ARMSX2 from their own developers
  (checked against their signing certificates), offers melonDS for DS games on two screens, names the RetroArch cores
  to download, fetches the shader collection, and checks your console files against known good copies.
- **The picture.** The signal each console reached a television by (composite, S-video, RGB), five CRT looks, Blur
  Busters' beam simulator on 120 Hz screens, a steady version of it for the Dreamcast, clean 3D drawn at higher
  detail and brought back to real scanlines, a 480-line look for GameCube and Wii, and LCD looks for the Game Boy
  family.
- **Less lag.** Preemptive frames on the 8 and 16-bit consoles, a shorter picture queue (23 ms instead of 31 on a
  Retroid Pocket Nova), sound 80 ms sooner on the cartridge consoles, and even black frames on the Nintendo 64.
- **Saves kept.** Saves and places copied to the SD card each time you come back from a game, with earlier versions.
- **16:9 and 4:3 screens**, games set for a television's refresh rate, and controllers that take the next player as
  they connect (in GameCube and Wii games, the first one also plays as player one).
- **Libraries from other front ends** read as they are, with ES-DE and RetroBat folder names and gamelists.
- **Games on several discs show once**, whether the discs come with a playlist or not; RetroArch is given one either
  way, so discs can be changed.
- **Checked before it starts.** A console that needs a file from the console itself, and doesn't have it, opens the
  Console files page instead of failing inside RetroArch. A game file left empty by a copy cut short is caught too,
  with a note to copy it again.
- **Small things:** how long you played, said as you come back from a game; a note in each new console folder saying
  what goes in it; the top of the screen saying when Android's battery saver is on; and handhelds whose face buttons
  arrive the other way around caught on the first screen.
- **Retroid extras**, each off until you switch it on: fan and power by console, stick lights, battery care on the
  charger, and ARMSX2 set up for you. PS2 and Xbox saves are copied with the rest. On the Retroid Pocket Nova, a
  measured speaker correction, and a measured color calibration on the firmware it was made for.
- **About NOSTOS**, from the store that made it.
