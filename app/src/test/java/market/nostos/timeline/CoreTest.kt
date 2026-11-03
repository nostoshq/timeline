// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 NOSTOS, LLC

package market.nostos.timeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class CoreTest {
    private val all = { _: String -> true }
    private val none = { _: String -> false }

    // ---- titles ---------------------------------------------------------------------------

    @Test fun prettyTitleMovesTheArticleAndTurnsDashIntoColon() {
        assertEquals("The Seventh Lighthouse: A Lamp in the Fog", Titles.pretty("Seventh Lighthouse, The - A Lamp in the Fog (USA)"))
        assertEquals("The Seventh Lighthouse", Titles.pretty("Seventh Lighthouse, The (USA) (Rev 1)"))
        assertEquals("Gravemoor: Song of the Ferryman", Titles.pretty("Gravemoor - Song of the Ferryman (USA)"))
        assertEquals("The Marrows: Fetch & Carry", Titles.pretty("Marrows, The - Fetch & Carry (USA)"))
        assertEquals("Hollow Bell 3", Titles.pretty("Hollow Bell 3 (Japan) [T-En by Ashgrove & Penwright & Kettle v1.3a] [HQ audio mix by Quillsong]"))
        assertEquals("Pokémon Red Version", Titles.pretty("Pokemon - Red Version (USA, Europe) (SGB Enhanced)"))
        assertEquals("Pokémon Yellow Version: Special Pikachu Edition",
            Titles.pretty("Pokemon - Yellow Version - Special Pikachu Edition (USA, Europe) (CGB+SGB Enhanced)"))
        assertEquals("Pokémon Black Version 2", Titles.pretty("Pokemon - Black Version 2 (USA, Europe) (NDSi Enhanced)"))
        assertEquals("Pokémon Snap", Titles.pretty("Pokemon Snap (USA)"))
        assertEquals("Ōkami", Titles.pretty("Ookami (USA)"))
        assertEquals("Sonic 3 & Knuckles", Titles.pretty("Sonic & Knuckles + Sonic The Hedgehog 3 (USA) (Lock-on Combination)"))
        assertEquals("Ironclad Derby 2: Arcade Mode", Titles.pretty("Ironclad Derby 2 (USA) (Arcade Mode) (Rev 1)"))
        assertEquals("Dr. Bramble", Titles.pretty("Dr. Bramble (World) (Rev 1)"))
        // a handful of database spellings are swapped for the name on the box
        assertEquals("Ironclad Derby 2: Arcade Mode", Titles.pretty("Ironclad Derby 2 (USA) (Arcade Mode) (Rev 1)"))
        assertEquals("Ironclad Derby 2: Simulation Mode", Titles.pretty("Ironclad Derby 2 (USA) (Simulation Mode) (Rev 2)"))
        assertEquals("ironclad derby 2", Titles.base("Ironclad Derby 2 (USA) (Simulation Mode) (Rev 2).chd"))
        assertEquals("Resident Evil Code: Veronica", Titles.pretty("Resident Evil - Code - Veronica (USA)"))
        assertEquals("M.U.S.H.A.", Titles.pretty("MUSHA - Metallic Uniframe Super Hybrid Armor (USA)"))
    }

    @Test fun stemKeepsDotsThatBelongToTheTitle() {
        assertEquals("Dr. Bramble (World) (Rev 1)", Titles.stem("Dr. Bramble (World) (Rev 1).gb"))
        assertEquals("Orison Gate (USA)", Titles.stem("Orison Gate (USA).m3u"))
        assertEquals("R.O.V.E.R. (USA)", Titles.stem("R.O.V.E.R. (USA).a26"))
        assertEquals("Sky Miner Bros. 3 (USA) (Rev 1)", Titles.stem("Sky Miner Bros. 3 (USA) (Rev 1).nes"))
        assertEquals("Farwatch - First Light (USA) (Rev 2)", Titles.stem("Farwatch - First Light (USA) (Rev 2).xiso.iso"))
        assertEquals("Farwatch: First Light", Titles.pretty(Titles.stem("Farwatch - First Light (USA) (Rev 2).xiso.iso")))
    }

    @Test fun baseIsTheMatchKey() {
        assertEquals("seventh lighthouse, the - a lamp in the fog", Titles.base("Seventh Lighthouse, The - A Lamp in the Fog (USA).sfc"))
        assertEquals("foxglove fables vi", Titles.base("Foxglove Fables VI (USA) [T-En by Dr. Pell v1.05] [Relocalized] [n].sfc"))
        assertEquals("ptiger2f", Titles.base("ptiger2f.zip"))
    }

    @Test fun sortIgnoresLeadingArticle() {
        assertEquals("seventh lighthouse", Titles.sortKey("The Seventh Lighthouse"))
    }

    // ---- file addresses ---------------------------------------------------------------------

    @Test fun storageAddressForInternalAndCard() {
        assertEquals(
            "content://com.android.externalstorage.documents/tree/primary%3AROMs%2Fpsx/document/primary%3AROMs%2Fpsx%2FLantern%20Road%20(USA).chd",
            Launch.romSaf("/storage/emulated/0/ROMs/psx/Lantern Road (USA).chd"))
        // the form Dolphin was seen to open on Android 13: the folder it was given, then the file
        assertEquals(
            "content://com.android.externalstorage.documents/tree/1A2B-3C4D%3AROMs%2Fgc/document/1A2B-3C4D%3AROMs%2Fgc%2FFlux-Racer%20GX%20(USA).rvz",
            Launch.romSaf("/storage/1A2B-3C4D/ROMs/gc/Flux-Racer GX (USA).rvz"))
        // a disc inside a multi-disc folder still belongs to the system's folder
        assertEquals(
            "content://com.android.externalstorage.documents/tree/1A2B-3C4D%3AROMs%2Fgc/document/1A2B-3C4D%3AROMs%2Fgc%2FHarrow%20House%20(USA).m3u%2FHarrow%20House%20(USA)%20(Disc%202).iso",
            Launch.romSaf("/storage/1A2B-3C4D/ROMs/gc/Harrow House (USA).m3u/Harrow House (USA) (Disc 2).iso"))
        // a games folder that is not at the top of the card
        assertTrue(Launch.romSaf("/storage/1A2B-3C4D/Games/roms/ps2/Pulsewave (USA).chd").contains("/tree/1A2B-3C4D%3AGames%2Froms%2Fps2/document/"))
    }

    @Test fun addressEncodesAwkwardCharacters() {
        val a = Launch.romSaf("/storage/emulated/0/ROMs/gba/Fizzbang, Inc. - Pocket Panic\$! (USA).gba")
        assertTrue(a.endsWith("primary%3AROMs%2Fgba%2FFizzbang%2C%20Inc.%20-%20Pocket%20Panic%24!%20(USA).gba"))
        assertTrue(Launch.romSaf("/storage/emulated/0/ROMs/snes/Wren & Badger + 3.sfc").contains("Wren%20%26%20Badger%20%2B%203.sfc"))
    }

    // ---- launch recipes, checked against ES-DE's published rules ---------------------------

    @Test fun retroArchCore() {
        val p = Launch.plan("ra:snes9x", "/storage/emulated/0/ROMs/snes/Flux-Racer (USA).sfc", all)!!
        assertEquals("com.retroarch.aarch64", p.pkg)
        assertEquals("com.retroarch.browser.retroactivity.RetroActivityFuture", p.cls)
        assertEquals("/storage/emulated/0/ROMs/snes/Flux-Racer (USA).sfc", p.extras["ROM"])
        assertEquals("/data/user/0/com.retroarch.aarch64/cores/snes9x_libretro_android.so", p.extras["LIBRETRO"])
        assertEquals("/storage/emulated/0/Android/data/com.retroarch.aarch64/files/retroarch.cfg", p.extras["CONFIGFILE"])
        assertFalse(p.clear)
        assertEquals(null, p.data)
    }

    @Test fun retroArchFallsBackToWhicheverBuildIsInstalled() {
        val p = Launch.plan("ra:mgba", "/x.gba", installed = { it == "com.retroarch" })!!
        assertEquals("com.retroarch", p.pkg)
        assertTrue(p.installed)
        assertEquals("/data/user/0/com.retroarch/cores/mgba_libretro_android.so", p.extras["LIBRETRO"])
    }

    @Test fun duckStation() {
        val p = Launch.plan("duckstation", "/storage/emulated/0/ROMs/psx/Brawl Circuit 3 (USA).chd", all)!!
        assertEquals("com.github.stenzek.duckstation", p.pkg)
        assertEquals("com.github.stenzek.duckstation.EmulationActivity", p.cls)
        assertTrue(p.clear)
        assertEquals(false, p.bools["resumeState"])
        assertTrue(p.extras["bootPath"]!!.startsWith("content://com.android.externalstorage.documents/tree/primary%3AROMs%2Fpsx/document/primary%3AROMs%2Fpsx%2F"))
    }

    @Test fun dolphin() {
        val p = Launch.plan("dolphin", "/storage/emulated/0/ROMs/gc/Burrow Brigade (USA).rvz", all)!!
        assertEquals("org.dolphinemu.dolphinemu.ui.main.TvMainActivity", p.cls)
        assertEquals("android.intent.action.MAIN", p.action)
        assertEquals("android.intent.category.LEANBACK_LAUNCHER", p.category)
        assertNotNull(p.extras["AutoStartFile"])
    }

    @Test fun armsx2AndPpssppUseViewWithData() {
        val ps2 = Launch.plan("armsx2", "/storage/emulated/0/ROMs/ps2/Driftwood (USA).chd", all)!!
        assertEquals("come.nanodata.armsx2", ps2.pkg)
        assertEquals("android.intent.action.VIEW", ps2.action)
        assertTrue(ps2.data!!.contains("primary%3AROMs%2Fps2%2FDriftwood"))
        val psp = Launch.plan("ppsspp", "/storage/emulated/0/ROMs/psp/Marten Dash (USA).chd", installed = { it == "org.ppsspp.ppsspp" })!!
        assertEquals("org.ppsspp.ppsspp", psp.pkg)
        assertEquals("org.ppsspp.ppsspp.PpssppActivity", psp.cls)
        assertEquals("android.intent.category.DEFAULT", psp.category)
    }

    @Test fun melonDsUsesItsOwnActionAndAnAddressInsideItsFolder() {
        val p = Launch.plan("melonds", "/storage/1A2B-3C4D/ROMs/nds/Courtroom Rush (USA).nds", all)!!
        assertEquals("me.magnum.melonds.LAUNCH_ROM", p.action)
        // The address form melonDS takes for a game on a removable card.
        assertEquals("content://com.android.externalstorage.documents/tree/1A2B-3C4D%3AROMs%2Fnds/document/" +
            "1A2B-3C4D%3AROMs%2Fnds%2FCourtroom%20Rush%20(USA).nds", p.extras["uri"])
        assertFalse("PATH" in p.extras)          // it would win over the address
    }

    @Test fun noEmulatorIsHandedAFileThroughTheLaunchersOwnProvider() {
        // only a Switch emulator ever needed one, and Timeline does not set up Switch emulation
        for (id in Emulators.all) assertFalse(id, Launch.plan(id, "/storage/emulated/0/ROMs/x/y.bin", all, providerUri = { "content://app/x" })?.grantRead ?: false)
        assertTrue(Catalog.systems.none { it.id == "switch" })
    }

    @Test fun missingEmulatorIsReportedNotGuessed() {
        val p = Launch.plan("duckstation", "/x.chd", none)!!
        assertFalse(p.installed)
        assertEquals(null, Launch.plan("no-such-emulator", "/x", all))
    }

    @Test fun everySystemHasWorkingRecipes() {
        for (s in Catalog.systems) {
            assertTrue(s.id + " has no emulator", s.emulators.isNotEmpty())
            for (e in s.emulators) assertNotNull("${s.id}: $e", Launch.plan(e, "/storage/emulated/0/ROMs/${s.id}/x", all))
        }
    }

    // ---- library ----------------------------------------------------------------------------

    private fun tree(vararg files: String): File {
        val root = Files.createTempDirectory("roms").toFile()
        for (f in files) File(root, f).apply { parentFile!!.mkdirs(); writeText("x") }
        return root
    }

    @Test fun scanFindsGamesAndIgnoresEverythingElse() {
        val root = tree(
            "snes/Candlewick Rising (USA).sfc", "snes/.DS_Store", "snes/notes.txt", "snes/._Candlewick Rising (USA).sfc",
            "gb/Stackwell (World) (Rev 1).gb", "arcade/sf2hf.zip", "arcade/neogeo.zip", "readme.md", "unknown/x.sfc")
        val lib = Library.scan(root)
        assertEquals(setOf("arcade", "gb", "snes"), lib.keys)
        assertEquals(listOf("Candlewick Rising"), lib["snes"]!!.map { it.title })
        assertEquals(listOf("Street Fighter II': Hyper Fighting"), lib["arcade"]!!.map { it.title })
        root.deleteRecursively()
    }

    @Test fun multiDiscFolderIsOneGame() {
        val d = "psx/Orison Gate (USA).m3u"
        val root = tree("$d/Orison Gate (USA) (Disc 1).chd", "$d/Orison Gate (USA) (Disc 2).chd", "$d/Orison Gate (USA).m3u", "psx/Brawl Circuit 3 (USA).chd")
        val games = Library.scan(root)["psx"]!!
        assertEquals(listOf("Brawl Circuit 3", "Orison Gate"), games.map { it.title })
        val x = games[1]
        assertEquals(2, x.discs.size)
        assertTrue(x.path.endsWith("Orison Gate (USA).m3u/Orison Gate (USA).m3u"))
        assertEquals("Orison Gate (USA)", x.stem)
        assertEquals(0, games[0].discs.size)
        root.deleteRecursively()
    }

    @Test fun flagsFanTranslationsAndFiftyHertz() {
        val root = tree("snes/Gaslight Manor (Japan) [T-En by Lamplight Works v1.01].sfc", "snes/Verdant Spire (Europe).sfc", "snes/Flux-Racer (USA).sfc", "gb/Puddle Hop (Europe).gb")
        val lib = Library.scan(root)
        val snes = lib["snes"]!!.associateBy { it.title }
        assertTrue(snes["Gaslight Manor"]!!.fanTranslation)
        assertTrue(snes["Verdant Spire"]!!.pal)
        assertFalse(snes["Flux-Racer"]!!.pal)
        assertFalse("handhelds have no 50 Hz mode", lib["gb"]!![0].pal)
        root.deleteRecursively()
    }

    @Test fun listsSortWithoutTheArticle() {
        val root = tree("nes/Seventh Lighthouse, The (USA) (Rev 1).nes", "nes/Kestrel's Errand (USA).nes", "nes/Swiftmoor (USA).nes")
        assertEquals(listOf("Kestrel's Errand", "The Seventh Lighthouse", "Swiftmoor"), Library.scan(root)["nes"]!!.map { it.title })
        root.deleteRecursively()
    }

}

class ShelfAndHistoryTest {
    private fun g(system: String, file: String) = Game(system, file, "/r/$system/$file", Titles.pretty(Titles.stem(file)),
        Titles.sortKey(Titles.pretty(Titles.stem(file))), emptyList(), "USA", false, false)

    @Test fun aPanelDescriptionEndsOnAWholeSentenceOrAWholeWord() {
        val blurb = "Brightwave's first Pip. Roll into the screen, spin through crates and leap bottomless pits across three islands to stop Baron Grist and rescue Tilly. Harder than its sequels."
        // the last whole sentence that fits, when it fills most of the room
        assertEquals("Brightwave's first Pip. Roll into the screen, spin through crates and leap bottomless pits across three islands to stop Baron Grist and rescue Tilly.",
            Excerpt.of(blurb, 170))
        // a first sentence too short to stand alone: a whole word and an ellipsis instead, never part of one
        assertEquals("Brightwave's first Pip. Roll into the screen, spin through crates…", Excerpt.of(blurb, 75))
        // initials, abbreviations and "Bros." are not the end of a sentence
        val road = "As Marlow Finch, keep moving and leave Tobias J. Crane to his own traps. As Tobias J., gather brass gadgets."
        assertEquals(listOf(72, road.length), Excerpt.sentenceEnds(road, road.length))
        assertEquals(listOf(41), Excerpt.sentenceEnds("Played here in Dr. Pell's relocalization. More.", 45))
        val bros = "Sky Miner Bros. 2 and Sky Miner Bros. 3, together."
        assertEquals(listOf(bros.length), Excerpt.sentenceEnds(bros, 99))
        // the ellipsis never follows a word like "the"
        assertEquals("A well-timed repel turns an attack aside and leaves…",
            Excerpt.of("A well-timed repel turns an attack aside and leaves the attacker open to a counter.", 62))
        assertEquals("Short.", Excerpt.of("Short.", 99))
        // a Roman numeral ends a sentence; a closing quote or a line break after the stop still does
        assertEquals(listOf(22), Excerpt.sentenceEnds("The sequel to Quest V. The hero", 99).take(1))
        assertEquals(listOf(13), Excerpt.sentenceEnds("He said \"Go.\" Then left", 99).take(1))
        assertEquals(listOf(9), Excerpt.sentenceEnds("One line.\nAnother", 99).take(1))
        // never left inside a bracket, and no full stop crowding the ellipsis
        assertEquals("Three sidekicks of one kind…",
            Excerpt.of("Three sidekicks of one kind (Moles, Otters, Tinker Bros. and more) join the captain on the pitch.", 64))
    }

    @Test fun phoenixWrightSequelsTakeTheirOfficialDash() {
        assertEquals("Phoenix Wright: Ace Attorney – Justice for All", Titles.pretty("Phoenix Wright - Ace Attorney - Justice for All (USA)"))
        assertEquals("Phoenix Wright: Ace Attorney – Trials and Tribulations", Titles.pretty("Phoenix Wright - Ace Attorney - Trials and Tribulations (USA)"))
        assertEquals("Phoenix Wright: Ace Attorney", Titles.pretty("Phoenix Wright - Ace Attorney (USA)"))
        assertEquals("Phoenix Wright: Ace Attorney – Justice for All", Titles.forLines("Phoenix Wright: Ace Attorney – Justice for All"))
    }

    @Test fun accentsNeitherSortLastNorHideFromSearch() {
        assertEquals("oshima ferry", Titles.sortKey("Ōshima Ferry"))
        assertEquals("pokemon red version", Titles.sortKey("Pokémon Red Version"))
        val games = listOf(g("ps2", "Oshima Ferry (USA).chd").copy(title = "Ōshima Ferry", sort = Titles.sortKey("Ōshima Ferry")),
            g("gb", "Stackwell (World) (Rev 1).gb"))
        assertEquals(listOf("Ōshima Ferry"), Search.find("oshima", games).map { it.title })
        assertEquals(listOf("Ōshima Ferry"), Search.find("Ōsh", games).map { it.title })
    }

    @Test fun surpriseNeverGivesTheSameGameTwiceRunning() {
        val a = g("snes", "Candlewick Rising (USA).sfc"); val b = g("snes", "Super Swiftmoor (Japan, USA) (En).sfc")
        repeat(50) { i -> assertEquals(b, Surprise.pick(listOf(a, b), a.key, kotlin.random.Random(i))) }
        assertEquals(a, Surprise.pick(listOf(a), a.key))           // the only game there is
        assertNull(Surprise.pick(emptyList(), null))
    }

    @Test fun numberedTitlesListInTheirOrder() {
        fun order(vararg t: String) = t.sortedBy { Titles.sortKey(it) }
        assertEquals(listOf("Foxglove Fables", "Foxglove Fables VII", "Foxglove Fables VIII", "Foxglove Fables IX", "Foxglove Fables Tactics"),
            order("Foxglove Fables IX", "Foxglove Fables Tactics", "Foxglove Fables VII", "Foxglove Fables", "Foxglove Fables VIII"))
        assertEquals(listOf("Foxglove Fables X", "Foxglove Fables XII"), order("Foxglove Fables XII", "Foxglove Fables X"))
        assertEquals(listOf("Copper Coast II", "Copper Coast 3"), order("Copper Coast 3", "Copper Coast II"))
        assertEquals(listOf("The Foxglove Fables Legend DX", "Foxglove Fables Legend II DX", "Foxglove Fables Legend III DX"),
            order("Foxglove Fables Legend III DX", "Foxglove Fables Legend II DX", "The Foxglove Fables Legend DX"))
        assertEquals(listOf("Mecha Mole 7", "Mecha Mole X", "Mecha Mole X2"), order("Mecha Mole X2", "Mecha Mole X", "Mecha Mole 7"))
        // the X series stays together, ahead of titles that only share the name
        assertEquals(listOf("Mecha Mole 7", "Mecha Mole X", "Mecha Mole X2", "Mecha Mole X3", "Mecha Mole Legends"),
            order("Mecha Mole Legends", "Mecha Mole X3", "Mecha Mole X", "Mecha Mole X2", "Mecha Mole 7"))
        // the first word keeps its letter, so the list's letter jumps still work
        assertEquals('x', Titles.sortKey("X-Fin Divers: Coral Run")[0])
        assertEquals('1', Titles.sortKey("1872: The Long Crossing")[0])
        // a library remembered by an older build is put in this order as it is read
        val g1 = g("psx", "Foxglove Fables IX (USA).chd"); val g2 = g("psx", "Foxglove Fables VII (USA).chd")
        val back = LibraryCache.parse(LibraryCache.format(mapOf("psx" to listOf(g1, g2))))
        assertEquals(listOf("Foxglove Fables VII", "Foxglove Fables IX"), back["psx"]!!.map { it.title })
    }

    @Test fun searchTakesNumbersJoinedWordsAndInitials() {
        val games = listOf(g("snes", "Foxglove Fables VI (Japan).sfc"), g("psx", "Foxglove Fables VII (USA).chd"),
            g("nes", "Sky Miner Bros. 3 (USA).nes"), g("n64", "Sky Miner 64 (USA).z64"), g("nes", "Mecha Mole 2 (USA).nes"),
            g("snes", "Mecha Mole X2 (USA).sfc"), g("snes", "Rail-Hopper - Night Line (USA).sfc"),
            g("gb", "Kestrel's Dream Isle 2 (USA).gb"), g("ps2", "Gutter Town Alley - Neon Side (USA).iso"),
            g("snes", "Brass Knuckle II - The Grand Bout (USA).sfc"), g("ps2", "Kite Haven II (USA).iso"),
            g("gc", "Harrow House 4 (USA).iso"), g("ps2", "Foxglove Fables X (USA).iso"), g("snes", "Mecha Mole X (USA).sfc"))
        fun first(q: String) = Search.find(q, games).firstOrNull()?.title
        assertEquals(listOf("Foxglove Fables VI"), Search.find("foxglove fables 6", games).map { it.title })
        assertEquals("Foxglove Fables VI", first("ff6"))
        assertEquals("Brass Knuckle II: The Grand Bout", first("brass knuckle 2"))
        assertEquals("Mecha Mole 2", first("mechamole 2"))
        assertEquals("Rail-Hopper: Night Line", first("railhopper"))
        assertEquals("Kestrel's Dream Isle 2", first("kestrels"))
        assertEquals("Sky Miner Bros. 3", first("smb3"))
        assertEquals("Sky Miner 64", first("sm64"))
        assertEquals("Gutter Town Alley: Neon Side", first("gta"))
        assertEquals("Kite Haven II", first("kh2"))
        assertEquals("Harrow House 4", first("hh4"))
        assertEquals("Foxglove Fables X", first("ffx"))          // initials with X as written
        assertEquals("Mecha Mole X", first("mmx"))
        // a word really in a title comes before initials that happen to match
        assertEquals(listOf("Sky Miner Bros. 3", "Sky Miner 64"), Search.find("miner", games).map { it.title })
        assertTrue(Search.find("zzz", games).isEmpty())
    }

    @Test fun onThisDayCountsOnlyKnownDaysAndWrapsTheYear() {
        assertNull(History.known("19950101"))            // only the year is known
        assertNull(History.known("19950801"))            // only the month
        assertEquals(8 to 22, History.known("19950822T000000"))
        assertEquals(0, History.distance("19950822", 8, 22))
        assertEquals(2, History.distance("19991231", 1, 2))      // across New Year
        assertEquals(1, History.distance("20000229", 3, 1))      // a leap day sits beside the 28th
        val cw = g("snes", "Candlewick Rising (USA).sfc"); val ff = g("snes", "Foxglove Fables VI (USA).sfc"); val far = g("gb", "Stackwell (World) (Rev 1).gb")
        val week = History.week(listOf(far to "19890731", ff to "19941011", cw to "19950822", g("nes", "Mecha Mole 2 (USA).nes") to "19890801"), 8, 22)
        assertEquals(listOf(cw.key), week.map { it.first.key })
        assertEquals(cw.key, History.today(week, 8, 22)?.first?.key)
        assertNull(History.today(week, 8, 24))
        assertEquals("Aug 22, 1995, 31 years ago", History.line("19950822", 2026))
    }

    @Test fun hiddenGamesLeaveTheListsAndAnEmptySystemGoesWithThem() {
        val lib = mapOf("gb" to listOf(g("gb", "Stackwell (World) (Rev 1).gb")), "snes" to listOf(g("snes", "Candlewick Rising (USA).sfc"), g("snes", "Backyard Saga (USA).sfc")))
        assertEquals(lib, Marks.visible(lib, emptySet()))
        val shown = Marks.visible(lib, setOf("gb/Stackwell (World) (Rev 1).gb", "snes/Backyard Saga (USA).sfc"))
        assertEquals(setOf("snes"), shown.keys)
        assertEquals(listOf("Candlewick Rising"), shown.getValue("snes").map { it.title })
        assertEquals(listOf("snes/a.sfc", "gb/b.gb"), Marks.parse("snes/a.sfc\n\nnot a key\ngb/b.gb\nsnes/a.sfc\n"))
        assertEquals("snes/a.sfc\ngb/b.gb\n", Marks.format(listOf("snes/a.sfc", "gb/b.gb")))
    }

    @Test fun theControlsLineFollowsTheEmulator() {
        assertTrue(ControlsNote.of("n64", "sky miner 64", "ra:mupen64plus_next_gles3", true, null).startsWith("A and B are the console's A and B. The right stick is the C buttons"))
        assertTrue(ControlsNote.of("n64", "sky miner 64", "m64plusfz", true, null) == "To leave, use the emulator's own menu.")
        assertTrue(ControlsNote.of("snes", "candlewick rising", "ra:snes9x", true, null) == "START and SELECT together leave and keep your place.")
        assertEquals("M1 rewinds while held, M2 fast-forwards while held. START and SELECT together leave and keep your place.",
            ControlsNote.of("snes", "candlewick rising", "ra:snes9x", true, "Rewind (hold)" to "Fast-forward (hold)"))
        assertTrue(ControlsNote.of("wii", "mario kart wii", "dolphin", true, null).startsWith("Played on the GameCube layout"))
        assertTrue(ControlsNote.of("wii", "comet garden", "dolphin", true, null).startsWith("Left stick: the Nunchuk"))
        assertEquals("To leave: Back, then Exit Emulation.", ControlsNote.of("gc", "burrow brigade", "dolphin", false, null, keeps = false))
        assertEquals("Home leaves and keeps your place. Back, then Exit Emulation, starts it fresh next time.", ControlsNote.of("gc", "burrow brigade", "dolphin", false, null))
        assertEquals("START and SELECT together leave.", ControlsNote.of("snes", "candlewick rising", "ra:snes9x", true, null, keeps = false))
        for (s in listOf("ra:stella", "ra:fbneo", "ra:mupen64plus_next_gles3", "ra:melondsds"))
            assertTrue(s, ControlsNote.of("x", "y", s, true, "Rewind (hold)" to "Fast-forward (hold)").length <= 260)
    }

    @Test fun playHistoryRoundTripsAndCountsTime() {
        var log = PlayLog.started(emptyMap(), "snes/Candlewick Rising (USA).sfc", 1_000_000L)
        log = PlayLog.finished(log, "snes/Candlewick Rising (USA).sfc", 1_000_000L, 1_000_000L + 3_725_000L)
        val p = PlayLog.parse(PlayLog.format(log)).getValue("snes/Candlewick Rising (USA).sfc")
        assertEquals(Played(1, 1_000_000L, 3725L), p)
        assertEquals("1 h 02 min", p.time)
        assertEquals("34 min", Played(seconds = 2050).time)
        assertEquals("", Played(seconds = 40).time)
    }

    @Test fun aBounceStraightBackIsNotPlayTime() {
        val log = PlayLog.started(emptyMap(), "k", 0L)
        assertTrue(PlayLog.finished(log, "k", 0L, 5_000L) === log)
        assertTrue(PlayLog.finished(log, "k", 0L, 17L * 3600 * 1000) === log)
    }

    @Test fun lastPlayedReadsLikeSpeech() {
        val day = 86_400_000L
        val now = 20_000L * day + 3600_000L
        assertEquals("today", Played(1, now - 60_000L).lastSeen(now, 0))
        assertEquals("yesterday", Played(1, now - day).lastSeen(now, 0))
        assertEquals("3 days ago", Played(1, now - 3 * day).lastSeen(now, 0))
        assertEquals("2 weeks ago", Played(1, now - 15 * day).lastSeen(now, 0))
        assertEquals("", Played().lastSeen(now, 0))
    }

    @Test fun sortingKeepsTitleOrderAsTheTieBreak() {
        val a = g("snes", "Amberline (USA).sfc"); val b = g("snes", "Candlewick Rising (USA).sfc"); val c = g("snes", "Flux-Racer (USA).sfc")
        val games = listOf(a, b, c)
        val dates = mapOf(a.key to "19920911T000000", b.key to "19950311T000000", c.key to "19901121T000000")
        val plays = mapOf(b.key to Played(2, 50L, 900L))
        fun sort(mode: String) = Sorting.apply(mode, games, { dates[it.key].orEmpty() }, { plays[it.key] }, { it.key == c.key }).map { it.title }
        assertEquals(listOf("Amberline", "Candlewick Rising", "Flux-Racer"), sort("Title"))
        assertEquals(listOf("Flux-Racer", "Amberline", "Candlewick Rising"), sort("Release date"))
        assertEquals(listOf("Candlewick Rising", "Amberline", "Flux-Racer"), sort("Recently played"))
        assertEquals(listOf("Candlewick Rising", "Amberline", "Flux-Racer"), sort("Most played"))
        assertEquals(listOf("Flux-Racer", "Amberline", "Candlewick Rising"), sort("Favorites first"))
    }

    @Test fun accentColourMathsRoundTrips() {
        val gold = 0xFFFEBD1F.toInt()
        val (h, s, v) = Hsv.of(gold)
        assertEquals(gold, Hsv.toArgb(h, s, v))
        assertEquals("#FEBD1F", Hsv.hex(gold))
        assertEquals(0xFFFF0000.toInt(), Hsv.toArgb(0f, 1f, 1f))
        assertEquals(0xFF00FF00.toInt(), Hsv.toArgb(120f, 1f, 1f))
        assertEquals(0xFF0000FF.toInt(), Hsv.toArgb(240f, 1f, 1f))
        assertEquals(0xFF808080.toInt(), Hsv.toArgb(77f, 0f, 0.5f))
    }

    @Test fun theSavedLibraryComesBackIdentical() {
        val discs = listOf("/r/psx/Orison Gate (USA).m3u/Orison Gate (USA) (Disc 1).chd", "/r/psx/Orison Gate (USA).m3u/Orison Gate (USA) (Disc 2).chd")
        val lib = linkedMapOf(
            "snes" to listOf(g("snes", "Candlewick Rising (USA).sfc")),
            "psx" to listOf(g("psx", "Orison Gate (USA).m3u").copy(discs = discs, fanTranslation = true, pal = true)),
       )
        assertEquals(lib, LibraryCache.parse(LibraryCache.format(lib)))
    }

    @Test fun sizesReadPlainly() {
        assertEquals("1.4 GB", Sizes.bytes(1_420_000_000L))
        assertEquals("512 MB", Sizes.bytes(512_300_000L))
        assertEquals("38 KB", Sizes.bytes(38_400L))
        assertEquals("", Sizes.bytes(0L))
    }
}

class DeviceLogicTest {
    @Test fun onlyKnownSettingsInKnownShapesAreEverWritten() {
        assertEquals("settings put system fan_mode '4'", Vendor.put(Vendor.FAN, "4"))
        assertEquals(null, Vendor.put(Vendor.FAN, "0"))            // the fan is never switched off
        assertEquals(null, Vendor.put(Vendor.FAN, "6"))            // and never put on a raw speed
        assertEquals(null, Vendor.put("fan_speed", "50000"))
        assertEquals(null, Vendor.put(Vendor.FAN, "4; rm -rf /"))
        assertEquals(null, Vendor.put("anything_else", "1"))
        assertEquals("settings put system joystick_led_light_picker_color '#FFFEBD1F,#FFFEBD1F'",
            Vendor.put(Vendor.LIGHT_COLOR, Vendor.both(0xFFFEBD1F.toInt())))
        assertEquals("0.60", Vendor.level(60))
        assertEquals("1.0", Vendor.level(100))
        assertEquals("0.05", Vendor.level(0))
        for (p in listOf(0, 5, 10, 60, 99, 100)) assertTrue(Vendor.put(Vendor.LIGHT_LEVEL, Vendor.level(p)) != null)
    }

    @Test fun everySystemHasAPowerProfileThatUsesAMakersFanMode() {
        for (s in Catalog.systems) assertTrue(Power.default(s.id).fan in setOf(Vendor.FAN_QUIET, Vendor.FAN_SMART, Vendor.FAN_SPORT))
        assertEquals(Power.Light, Power.default("snes"))
        assertEquals(Power.Balanced, Power.default("n64"))
        assertEquals(Power.Strong, Power.default("ps2"))
        for (s in Catalog.systems) assertTrue(Power.default(s.id) != Power.High)        // held near top speed only by choice
        assertEquals(0, Power.resting.performance)                                     // the launcher: Standard, Smart
        assertEquals(Vendor.FAN_SMART, Power.resting.fan)
        for (p in Power.entries) {
            assertTrue(Vendor.put(Vendor.FAN, p.fan.toString()) != null && Vendor.put(Vendor.PERFORMANCE, p.performance.toString()) != null)
            assertEquals("setprop persist.vendor.debug.mode ${p.performance}", Vendor.setprop(p.performance.toString()))
        }
        assertNull(Vendor.setprop("3"))
        assertNull(Vendor.setprop("1; reboot"))
    }

    @Test fun theChipSensorsAreFoundByNameAndTheHottestIsRead() {
        val listing = """
            /sys/devices/virtual/thermal/thermal_zone23/type:sdr_mmw_therm
            /sys/devices/virtual/thermal/thermal_zone31/type:cpuss-0
            /sys/devices/virtual/thermal/thermal_zone45/type:cpu-1-10
            /sys/devices/virtual/thermal/thermal_zone47/type:cpu-0-0
            /sys/devices/virtual/thermal/thermal_zone63/type:gpuss-0
            /sys/devices/virtual/thermal/thermal_zone88/type:usb-therm
            /sys/devices/virtual/thermal/thermal_zone94/type:battery
            /sys/devices/virtual/thermal/thermal_zone99/type:cpu-1-10; rm -rf /
        """.trimIndent()
        val zones = Vendor.chipSensors(listing)
        assertEquals(listOf(31, 45, 47, 63), zones)
        assertEquals("cat /sys/devices/virtual/thermal/thermal_zone31/temp /sys/devices/virtual/thermal/thermal_zone45/temp " +
            "/sys/devices/virtual/thermal/thermal_zone47/temp /sys/devices/virtual/thermal/thermal_zone63/temp", Vendor.readSensors(zones))
        assertNull(Vendor.readSensors(emptyList()))
        // As read on a Retroid Pocket Nova near idle: the prime core is the warmest.
        assertEquals(44.8f, Vendor.hottest("34800\n44800\n33500\n31100\n"), 0.01f)
        assertTrue(Vendor.hottest("cat: Invalid argument\n-40960\n").isNaN())       // a sensor not fitted, and nothing else
        assertTrue(Vendor.hottest("").isNaN())
    }

    @Test fun theHeatGuardStepsUpWhenHotAndNeverBelowItsBase() {
        val g = HeatGuard(Vendor.FAN_QUIET)
        assertEquals(Vendor.FAN_QUIET, g.next(85f, 30f))            // one hot reading is not enough
        assertEquals(Vendor.FAN_SMART, g.next(85f, 30f))            // two in a row: one step up
        assertEquals(Vendor.FAN_SMART, g.next(85f, 30f))
        assertEquals(Vendor.FAN_SPORT, g.next(90f, 30f))
        assertEquals(Vendor.FAN_SPORT, g.next(99f, 30f))            // already at the top
        repeat(5) { assertEquals(Vendor.FAN_SPORT, g.next(60f, 30f)) }
        assertEquals(Vendor.FAN_SMART, g.next(60f, 30f))            // six cool readings: one step down
        repeat(5) { g.next(60f, 30f) }
        assertEquals(Vendor.FAN_QUIET, g.next(60f, 30f))
        repeat(20) { assertEquals(Vendor.FAN_QUIET, g.next(40f, 25f)) } // never below the base

        // The battery alone is enough to step up, and it has to cool too before the fan steps down.
        val b = HeatGuard(Vendor.FAN_SMART)
        b.next(60f, 46f)
        assertEquals(Vendor.FAN_SPORT, b.next(60f, 46f))
        repeat(10) { assertEquals(Vendor.FAN_SPORT, b.next(60f, 43f)) } // chip cool, battery not yet
        repeat(5) { b.next(60f, 40f) }
        assertEquals(Vendor.FAN_SMART, b.next(60f, 40f))
        val charging = HeatGuard(Vendor.FAN_QUIET)
        repeat(30) { assertEquals(Vendor.FAN_QUIET, charging.next(55f, 40f)) } // a battery warm from charging alone does not hold the fan up

        val strong = HeatGuard(Vendor.FAN_SMART)
        repeat(30) { assertEquals(Vendor.FAN_SMART, strong.next(50f, 30f)) }
        assertEquals(Vendor.FAN_SMART, strong.next(Float.NaN, Float.NaN)) // no reading changes nothing
        strong.next(85f)                                                 // the chip alone, no battery reading
        assertEquals(Vendor.FAN_SMART, strong.next(75f))                 // a warm reading between the marks breaks the hot streak
        strong.next(85f)
        assertEquals(Vendor.FAN_SPORT, strong.next(Float.NaN, 46f))      // the battery alone, no chip reading
    }
}

class RetroPackTest {
    private val p = RetroPack.Paths("/storage/emulated/0/Timeline/retroarch", "com.retroarch.aarch64", "/data/user/0/com.retroarch.aarch64", "/data/app/x/base.apk")

    @Test fun aGameRunLighterGetsItsOwnFileAndKeepsEverythingElse() {
        val files = RetroPack.files(Look(lighter = mapOf("n64/Bristle's Rough Night (USA)" to true, "dreamcast/Dockside Days (USA)" to true,
            "psx/Glasswing (USA)" to false)), p)
        assertTrue(files.getValue("Mupen64Plus-Next/Bristle's Rough Night (USA).opt").contains("mupen64plus-43screensize = \"320x240\""))
        val dockside = files.getValue("Flycast/Dockside Days (USA).opt")
        assertTrue(dockside.contains("reicast_internal_resolution = \"640x480\""))
        assertTrue("a game's own memory card must survive", dockside.contains("reicast_per_content_vmus = \"VMU A1\""))
        // switched back off: the game's own file holds the usual, sharp settings again
        assertEquals(files.getValue("SwanStation/SwanStation.opt"), files.getValue("SwanStation/Glasswing (USA).opt"))
        assertTrue(files.getValue("Mupen64Plus-Next/Mupen64Plus-Next.opt").contains("2560x1920"))
    }

    @Test fun theDefaultLookIsTheSharperOne() {
        assertTrue(Look().sharp)
        val sharp = RetroPack.files(Look(), p)
        assertTrue(sharp.getValue("SwanStation/SwanStation.opt").let { it.contains("swanstation_GPU_ResolutionScale = \"4\"") && it.contains("swanstation_GPU_PGXPEnable = \"true\"") })
        assertTrue(sharp.getValue("Mupen64Plus-Next/Mupen64Plus-Next.opt").let { it.contains("mupen64plus-43screensize = \"2560x1920\"") && it.contains("mupen64plus-EnableLegacyBlending = \"False\"") })
    }

    @Test fun theTruePicture() {
        val files = RetroPack.files(Look(sharp = false), p)
        val preset = files.getValue("FinalBurn Neo/FinalBurn Neo.slangp")      // arcade: RGB, so the beam, the set's colour and the tube
        assertTrue(preset.startsWith("shaders = \"3\"\n"))
        assertTrue(preset.contains("shader0 = \"/storage/emulated/0/Timeline/retroarch/shaders/shaders_slang/subframe-bfi/shaders/crt-beam-simulator.slang\""))
        assertTrue(preset.contains("shader1 = \"/storage/emulated/0/Timeline/retroarch/shaders/timeline/timeline-colour.slang\""))
        assertTrue(preset.contains("shader2 = \"/storage/emulated/0/Timeline/retroarch/shaders/timeline/crt-gdv-3d.slang\""))
        for (line in listOf("wrap_mode2 = \"clamp_to_border\"", "scale_type2 = \"viewport\"", "LCD_ANTI_RETENTION_TOGGLE = \"0.0\"", "GAIN_VS_BLUR = \"0.5\"",
            "SCAN_DIRECTION = \"0.0\"", "warpY = \"0.0\"", "sat = \"1.00\"", "glow = \"0.10\"", "brightboost = \"1.10\"", "gdv_hires = \"400.0\"",
            // an arcade monitor, seen up close: its slot mask in plain view, and its 9300 K white
            "shadowMask = \"1.0\"", "maskDark = \"0.5\"", "maskLight = \"1.5\"", "tl_white = \"3.0\"")) assertTrue(line, preset.contains(line))
        assertTrue(files.getValue("Snes9x/Snes9x.cfg").contains("video_shader_subframes = \"2\""))
        // the handhelds as their own screens were
        assertTrue(files.getValue("Gambatte/gb.slangp").let { it.contains("handheld/gameboy.slangp") && it.contains("integer_mode = \"1.000000\"") })
        assertTrue(files.getValue("Gambatte/gb.opt").contains("gambatte_gb_colorization = \"disabled\""))
        assertTrue(files.getValue("Gambatte/Gambatte.slangp").contains("authentic_gbc_fast.slang"))
        assertTrue(files.getValue("Gambatte/gbc.opt").contains("gambatte_gbc_color_correction_mode = \"accurate\""))
        assertTrue(files.getValue("mGBA/mGBA.slangp").contains("lcd-grid-v2-gba-color.slangp") && files.getValue("mGBA/mGBA.opt").contains("mgba_color_correction = \"OFF\""))
        assertTrue(files.getValue("melonDS DS/melonDS DS.slangp").let { it.contains("nds-color.slang") && it.contains("pixel_aa.slang") })
        // the 3D consoles at their own detail, with the console's own dither and wobble
        val swan = files.getValue("SwanStation/SwanStation.opt")
        assertTrue(swan.contains("swanstation_GPU_ResolutionScale = \"1\"") && swan.contains("swanstation_GPU_PGXPEnable = \"false\"") && swan.contains("swanstation_GPU_TrueColor = \"false\""))
        val beetle = files.getValue("Beetle PSX HW/Beetle PSX HW.opt")
        assertTrue(beetle.contains("beetle_psx_hw_internal_resolution = \"1x(native)\"") && beetle.contains("beetle_psx_hw_dither_mode = \"1x(native)\""))
        assertTrue(files.getValue("Flycast/Flycast.opt").let { it.contains("reicast_internal_resolution = \"640x480\"") && it.contains("reicast_anisotropic_filtering = \"off\"") })
        assertTrue(files.getValue("Mupen64Plus-Next/Mupen64Plus-Next.opt").let { it.contains("mupen64plus-43screensize = \"2560x1920\"") && it.contains("mupen64plus-BilinearMode = \"3point\"") })
        assertTrue(files.getValue("Beetle Saturn/Beetle Saturn.opt").contains("beetle_saturn_deinterlacer = \"bob_offset\""))
        // the sharper look: as before
        val sharp = RetroPack.files(Look(sharp = true), p)
        assertTrue(sharp.getValue("SwanStation/SwanStation.opt").let { it.contains("swanstation_GPU_ResolutionScale = \"4\"") && it.contains("swanstation_GPU_PGXPEnable = \"true\"") })
        assertTrue(sharp.getValue("Flycast/Flycast.opt").contains("reicast_internal_resolution = \"2560x1920\""))
        assertTrue(sharp.getValue("Beetle PSX HW/Beetle PSX HW.opt").contains("beetle_psx_hw_internal_resolution = \"4x\""))
        // the television consoles' own settings
        assertTrue(files.getValue("Nestopia/Nestopia.opt").contains("nestopia_palette = \"cxa2025as\""))
        assertTrue(files.getValue("Snes9x/Snes9x.opt").contains("snes9x_aspect = \"ntsc\""))
        assertTrue(files.getValue("Beetle PCE/Beetle PCE.opt").contains("pce_palette = \"Composite\""))
        assertTrue(files.getValue("Genesis Plus GX/genesis.opt").let { it.contains("genesis_plus_gx_overscan = \"top/bottom\"") && it.contains("genesis_plus_gx_system_bram = \"per game\"") })
        assertTrue(files.getValue("Genesis Plus GX/gamegear.opt").let { it.contains("genesis_plus_gx_overscan = \"disabled\"") && it.contains("genesis_plus_gx_lcd_filter = \"enabled\"") })
        assertTrue(files.getValue("MAME 2003-Plus/MAME 2003-Plus.opt").contains("mame2003-plus_tate_mode = \"enabled\""))
        assertTrue(files.getValue("Mupen64Plus-Next/Mupen64Plus-Next.cfg").contains("input_poll_type_behavior = \"0\""))
        val base = RetroPack.base(Look(), p)
        assertEquals("true", base["video_scale_integer"]); assertEquals("22", base["aspect_ratio_index"])
        assertEquals("120.000000", base["video_refresh_rate"]); assertEquals("true", base["savestate_auto_save"])
        assertEquals("10", base["autosave_interval"]); assertEquals("1", base["video_shader_subframes"])
    }

    @Test fun theAchievementsPasswordIsHandedOverOnceAndThenForgotten() {
        // just typed: RetroArch signs in with it, and an older token is cleared so it cannot be used instead
        val fresh = RetroPack.base(Look(achievements = Achievements("someone", "secret", false, fresh = true)), p)
        assertEquals("secret", fresh["cheevos_password"]); assertEquals("", fresh["cheevos_token"]); assertEquals("true", fresh["cheevos_enable"])
        assertEquals("false", fresh["cheevos_hardcore_mode_enable"])
        // not signed in yet: the password keeps being handed over, and RetroArch's own token line is left alone
        val waiting = RetroPack.base(Look(achievements = Achievements("someone", "secret", false)), p)
        assertEquals("secret", waiting["cheevos_password"]); assertFalse(waiting.containsKey("cheevos_token"))
        // signed in: the launcher has forgotten it, and the file is written without it
        val signed = RetroPack.base(Look(achievements = Achievements("someone", "", false)), p)
        assertEquals("", signed["cheevos_password"]); assertFalse(signed.containsKey("cheevos_token"))
        // achievements off: no password left in the file either
        val off = RetroPack.base(Look(), p)
        assertEquals("false", off["cheevos_enable"]); assertEquals("", off["cheevos_password"]); assertFalse(off.containsKey("cheevos_username"))
        // RetroArch's token survives the launcher's own writing of the file
        val cfg = RetroPack.merge("cheevos_token = \"abc123\"\ncheevos_username = \"Someone\"\ncheevos_password = \"secret\"\n", signed)
        assertTrue(cfg.contains("cheevos_token = \"abc123\"")); assertTrue(cfg.contains("cheevos_password = \"\""))
    }

    @Test fun signedInMeansATokenForThisVeryAccount() {
        assertTrue(Cheevos.signedIn("cheevos_username = \"Player\"\ncheevos_token = \"abc\"\n", "player"))
        assertFalse(Cheevos.signedIn("cheevos_username = \"player\"\ncheevos_token = \"\"\n", "player"))
        assertFalse(Cheevos.signedIn("cheevos_username = \"someoneelse\"\ncheevos_token = \"abc\"\n", "player"))
        assertFalse(Cheevos.signedIn("", "player")); assertFalse(Cheevos.signedIn("cheevos_username = \"x\"\ncheevos_token = \"abc\"\n", ""))
        // the last line of a setting is the one that counts, as in RetroArch
        assertFalse(Cheevos.signedIn("cheevos_username = \"a\"\ncheevos_token = \"abc\"\ncheevos_token = \"\"\n", "a"))
    }

    @Test fun motionClarityNeedsAFastPanelAndSkipsFiftyHertzGames() {
        val slow = RetroPack.files(Look(hz = 60), p)
        assertTrue(slow.getValue("Snes9x/Snes9x.cfg").contains("video_shader_subframes = \"1\""))
        assertTrue(slow.getValue("FinalBurn Neo/FinalBurn Neo.slangp").startsWith("shaders = \"2\"\n"))
        assertTrue(slow.getValue("Snes9x/Snes9x.slangp").startsWith("shaders = \"6\"\n") && !slow.getValue("Snes9x/Snes9x.slangp").contains("-beam"))   // composite, the colour, then the tube
        val pal = RetroPack.files(Look(), p, listOf("snes" to "Verdant Spire (Europe)", "gb" to "Some Game (Europe)"))
        assertEquals("video_shader_subframes = \"1\"\nvideo_black_frame_insertion = \"0\"\n", pal["Snes9x/Verdant Spire (Europe).cfg"])
        assertEquals(null, pal["Gambatte/Some Game (Europe).cfg"])
        val black = RetroPack.files(Look(motion = "Black frames"), p)
        assertTrue(black.getValue("Nestopia/Nestopia.cfg").contains("video_black_frame_insertion = \"1\""))
        assertTrue(black.getValue("Nestopia/Nestopia.slangp").startsWith("shaders = \"6\"\n"))
        assertTrue(!black.getValue("Nestopia/Nestopia.slangp").contains("-beam"))
    }

    @Test fun everyChoiceProducesFilesAndOffProducesNoPreset() {
        for (crt in RetroPack.crtChoices) for (motion in RetroPack.motionChoices) for (lcd in RetroPack.handheldChoices) {
            val files = RetroPack.files(Look(crt = crt, motion = motion, handheld = lcd), p)
            assertEquals(crt != "Off" || motion == "Beam simulator", "Snes9x/Snes9x.slangp" in files)
            assertEquals(lcd != "Off", "mGBA/mGBA.slangp" in files)
            for ((path, text) in files) assertTrue(path, text.endsWith("\n") && !text.contains("null"))
        }
        // handheld screens never get the motion pass
        assertTrue(RetroPack.files(Look(), p).getValue("Genesis Plus GX/gamegear.cfg").contains("video_shader_subframes = \"1\""))
        // each console at its own pixel shape: the Game Gear's 6:5 pixels at exactly 6 x 5 screen pixels on a 4:3 screen
        // (or a whole multiple), the core's shape at whole steps elsewhere and on a television; the Master System at the
        // shape its core reports (written out, so an earlier square-pixel setting does not stay)
        val files = RetroPack.files(Look(), p)
        assertTrue(files.getValue("Genesis Plus GX/gamegear.cfg").let { it.contains("aspect_ratio_index = \"23\"") && it.contains("custom_viewport_width = \"960\"") &&
            it.contains("custom_viewport_height = \"720\"") && it.contains("custom_viewport_x = \"0\"") && it.contains("custom_viewport_y = \"0\"") })
        assertEquals(1920 to 1440, RetroPack.gameGearSize(Look(panel = 2048 to 1536)))
        assertNull(RetroPack.gameGearSize(Look(panel = 1920 to 1080)))
        assertNull(RetroPack.gameGearSize(Look(panel = 640 to 480)))
        assertNull(RetroPack.gameGearSize(Look(tv = true)))
        for (look in listOf(Look(tv = true), Look(panel = 1920 to 1080)))
            assertTrue(RetroPack.files(look, p).getValue("Genesis Plus GX/gamegear.cfg").let { it.contains("aspect_ratio_index = \"22\"") && it.contains("video_scale_integer_axis = \"1\"") })
        assertTrue(files.getValue("Genesis Plus GX/mastersystem.cfg").contains("aspect_ratio_index = \"22\""))
        assertFalse(files.getValue("Genesis Plus GX/Genesis Plus GX.cfg").contains("aspect_ratio_index"))     // the Genesis keeps its TV shape
        assertTrue(files.getValue("Nestopia/Nestopia.opt").contains("nestopia_blargg_ntsc_filter = \"disabled\""))   // the CRT shader is the TV
        // with a plain grid chosen: Game Boy and Game Boy Color get Pixel Transparency over it; the Game Boy Advance the grid alone
        val grid = RetroPack.files(Look(handheld = "LCD3x"), p)
        assertTrue(grid.getValue("Gambatte/Gambatte.slangp").contains("presets/pixel_transparency/pixel_transparency-lcd3x.slangp"))
        assertTrue(grid.getValue("mGBA/mGBA.slangp").contains("handheld/lcd3x.slangp"))
        assertTrue(grid.getValue("Gambatte/gb.opt").contains("gambatte_gb_internal_palette = \"GB - DMG\""))   // the original's own palette
        assertFalse("Gambatte/gb.slangp" in grid)
        assertTrue(RetroPack.files(Look(handheld = "Off"), p).getValue("Gambatte/Gambatte.slangp").contains("handheld/pixel_transparency.slangp"))
        assertTrue(RetroPack.files(Look(handheld = "LCD3x", transparency = false), p).getValue("Gambatte/Gambatte.slangp").contains("handheld/lcd3x.slangp"))
        assertTrue(grid.getValue("mGBA/gbc.slangp").contains("pixel_transparency"))            // a Game Boy Color game moved to mGBA
        assertFalse("mGBA/gbc.slangp" in RetroPack.files(Look(handheld = "LCD3x", transparency = false), p))
        assertTrue(files.getValue("Genesis Plus GX/gamegear.slangp").contains("handheld/lcd-grid-v2.slangp"))
        // the grid Off: the Game Gear gets a plain pass, not the Genesis's CRT
        assertTrue(RetroPack.files(Look(handheld = "Off"), p).getValue("Genesis Plus GX/gamegear.slangp").contains("stock.slang"))
    }

    @Test fun mergingKeepsEverythingTheLauncherDoesNotOwn() {
        val before = "input_player1_a_btn = \"96\"\nvideo_scale_integer = \"false\"\nmenu_driver = \"ozone\"\n"
        val after = RetroPack.merge(before, mapOf("video_scale_integer" to "true", "autosave_interval" to "10"))
        assertEquals("input_player1_a_btn = \"96\"\nvideo_scale_integer = \"true\"\nmenu_driver = \"ozone\"\nautosave_interval = \"10\"\n", after)
        assertEquals(after, RetroPack.merge(after, mapOf("video_scale_integer" to "true", "autosave_interval" to "10")))
        assertEquals("a = \"1\"\n", RetroPack.merge("", mapOf("a" to "1")))
    }

    @Test fun retroArchIsHandedItsOwnFoldersAlongWithTheConfig() {
        val e = RetroPack.extras(Look(), p)
        assertEquals("/storage/emulated/0/Timeline/retroarch/retroarch.cfg", e["CONFIGFILE"])
        assertEquals("/data/user/0/com.retroarch.aarch64", e["DATADIR"])
        assertEquals("/storage/emulated/0/Android/data/com.retroarch.aarch64/files", e["EXTERNAL"])
        assertEquals("120", e["REFRESH"])
    }
}

class UprightAndSoundTest {
    private val p = RetroPack.Paths("/storage/emulated/0/Timeline/retroarch", "com.retroarch.aarch64", "/data/user/0/com.retroarch.aarch64", "/data/app/x/base.apk")

    @Test fun uprightArcadeGamesTurnThePictureAndEveryControl() {
        val files = RetroPack.files(Look(), p, arcade = listOf("galaga", "ptiger2f", "1943", "sf2hf"))
        assertTrue(files.getValue("FinalBurn Neo/FinalBurn Neo.opt").startsWith("fbneo-vertical-mode = \"TATE alternate\"\n"))
        assertTrue(files.getValue("FinalBurn Neo/FinalBurn Neo.opt").contains("fbneo-dipswitch-strider-Cabinet = \"Upright 2P\"\n"))
        val turned = files.getValue("remaps/FinalBurn Neo/galaga.rmp")
        // right side down: pushing toward the device's left edge is "up" for the player
        for (line in listOf("input_player1_btn_left = \"4\"", "input_player1_btn_right = \"5\"", "input_player1_btn_up = \"7\"", "input_player1_btn_down = \"6\"",
            "input_player1_stk_r_x- = \"4\"", "input_player1_stk_r_x+ = \"5\"", "input_player1_stk_r_y- = \"7\"", "input_player1_stk_r_y+ = \"6\"",
            "input_player1_stk_l_x- = \"4\"", "input_player1_btn_a = \"0\"", "input_player1_btn_x = \"8\"", "input_player1_btn_y = \"9\"", "input_player1_btn_b = \"1\""))
            assertTrue(line, turned.contains(line + "\n"))
        assertTrue("remaps/FinalBurn Neo/1943.rmp" in files)
        assertEquals(null, files["remaps/FinalBurn Neo/ptiger2f.rmp"])       // a level game is left exactly as it is
        // a six-button fighter has only its Modern layout
        assertEquals("input_libretro_device_p1 = \"517\"\ninput_libretro_device_p2 = \"517\"\n", files["remaps/FinalBurn Neo/sf2hf.rmp"])

        val other = RetroPack.files(Look(upright = "Left side down"), p, arcade = listOf("galaga"))
        assertTrue(other.getValue("FinalBurn Neo/FinalBurn Neo.opt").startsWith("fbneo-vertical-mode = \"TATE\"\n"))
        assertTrue(other.getValue("remaps/FinalBurn Neo/galaga.rmp").contains("input_player1_btn_right = \"4\"\n"))
        assertTrue(other.getValue("remaps/FinalBurn Neo/galaga.rmp").contains("input_player1_btn_y = \"0\"\n"))

        val level = RetroPack.files(Look(upright = "Keep it level"), p, arcade = listOf("galaga"))
        assertTrue(level.getValue("FinalBurn Neo/FinalBurn Neo.opt").startsWith("fbneo-vertical-mode = \"disabled\"\n"))
        assertEquals(null, level["remaps/FinalBurn Neo/galaga.rmp"])
        assertEquals("/storage/emulated/0/Timeline/retroarch/config/remaps", RetroPack.base(Look(), p)["input_remapping_directory"])
    }

    @Test fun everyTurnedControlMapsToADifferentDirectionAndButton() {
        for (side in listOf("Right side down", "Left side down")) {
            val rmp = RetroPack.files(Look(upright = side), p, arcade = listOf("pacman")).getValue("remaps/FinalBurn Neo/pacman.rmp")
            val map = rmp.lines().filter { it.contains('=') }.associate { it.substringBefore(" =") to it.substringAfter("\"").substringBefore("\"") }
            assertEquals(setOf("4", "5", "6", "7"), listOf("up", "down", "left", "right").map { map.getValue("input_player1_btn_$it") }.toSet())
            assertEquals(setOf("0", "1", "8", "9"), listOf("a", "b", "x", "y").map { map.getValue("input_player1_btn_$it") }.toSet())
            for (d in listOf("up", "down", "left", "right")) assertTrue(map.getValue("input_player1_btn_$d") != mapOf("up" to "4", "down" to "5", "left" to "6", "right" to "7").getValue(d))
        }
    }

    @Test fun theSpeakerShapeInterpolatesAndRoundTrips() {
        val nova = Eq.presets.getValue("Neutral")
        assertEquals(listOf(0, 0, 0, 0, 0), nova)                          // the tuning as it is, nothing on top
        assertEquals(nova, Eq.parse(Eq.format(nova)))
        assertEquals(nova, Eq.parse("rubbish"))
        assertEquals(nova, Eq.parse("20,0,15,30,50"))                      // the old preset, with its bass boost, is not kept
        assertEquals(Eq.presets.getValue("Warmer"), Eq.parse("40,20,0,10,20"))
        assertEquals("Neutral", Eq.name(nova)); assertEquals("Your own", Eq.name(listOf(1, 2, 3, 4, 5)))
        val warm = Eq.presets.getValue("Warmer")
        assertEquals(15, Eq.gainAt(700, warm)); assertEquals(10, Eq.gainAt(100, warm)); assertEquals(-10, Eq.gainAt(16000, warm))
        assertEquals("+2.0 dB", Eq.label(20)); assertEquals("-1.5 dB", Eq.label(-15)); assertEquals("0.0 dB", Eq.label(0))
        val dsp = Eq.dsp(nova)
        assertTrue(dsp.startsWith("filters = 1\nfilter0 = eq\neq_frequencies = \"63 90 "))
        assertEquals(SpeakerChain.edges.size, dsp.substringAfter("eq_gains = \"").substringBefore("\"").split(" ").size)
        assertEquals("", RetroPack.base(Look(), p)["audio_dsp_plugin"])
        assertTrue(RetroPack.base(Look(speakers = nova), p).getValue("audio_dsp_plugin").endsWith("/config/speakers.dsp"))
        assertEquals(Eq.dsp(nova), RetroPack.files(Look(speakers = nova), p)["speakers.dsp"])
    }

    @Test fun theVolumeMovesInTens() {
        val max = 15                                            // the Nova's music volume has 15 steps
        assertEquals((0..100 step 10).toList(), VolumeSteps.levels)
        assertEquals(20, VolumeSteps.next(10, 1)); assertEquals(0, VolumeSteps.next(10, -1))
        assertEquals(100, VolumeSteps.next(100, 1)); assertEquals(0, VolumeSteps.next(0, -1))
        // Every level has an Android step of its own, and reads back as set.
        val steps = VolumeSteps.levels.map { VolumeSteps.index(it, max) }
        assertEquals(steps.distinct(), steps); assertEquals(15, VolumeSteps.index(100, max))
        for (l in VolumeSteps.levels) assertEquals(l, VolumeSteps.level(VolumeSteps.index(l, max), max))
        assertEquals(70, VolumeSteps.level(10, max))            // Android's 10 of 15
        assertEquals(70, VolumeSteps.next(VolumeSteps.level(10, max), 0))
    }

    @Test fun theSpeakerChainCutsWhatTheSpeakersCannotPlayAndNeverBoostsBass() {
        // The high-pass: deep below 150 Hz, untouched where the speakers play.
        assertTrue(SpeakerChain.correction(40.0) < -40)
        assertTrue(SpeakerChain.correction(75.0) < -20)
        assertEquals(-3.0, SpeakerChain.correction(150.0), 0.5)
        assertEquals(SpeakerChain.edges.size, SpeakerChain.measured.size)
        // Below 4 kHz, where nearly all of a game's sound is, nothing is lifted more than 1.5 dB past the headroom
        // taken first; the top end's lift stops at 6 dB. The limiter catches the rest.
        for (hz in 20..4000 step 10) assertTrue("at $hz Hz", SpeakerChain.correction(hz.toDouble()) <= -SpeakerChain.INPUT_GAIN + 1.5)
        for (hz in 20..6300 step 10) assertTrue("at $hz Hz", SpeakerChain.correction(hz.toDouble()) <= SpeakerChain.LIFT)
        for (hz in 6301..24000 step 10) assertTrue("at $hz Hz", SpeakerChain.correction(hz.toDouble()) <= SpeakerChain.TOP_LIFT)
        // No bass boost: up to 315 Hz the chain only ever cuts.
        for (hz in 20..315) assertTrue(SpeakerChain.correction(hz.toDouble()) <= 0.0)
        // What the measurement found: a little more of the lowest notes the speakers can play, the boxy bump around
        // 700 Hz and the sharp peak at 5 kHz taken down, and a lift where they fall away at the top.
        assertTrue(SpeakerChain.correction(450.0) in 2.5..3.5)
        assertTrue(SpeakerChain.correction(900.0) in -3.0..-1.5)
        assertTrue(SpeakerChain.correction(5000.0) < -4.0 && SpeakerChain.correction(5500.0) < -2.5)
        assertTrue(SpeakerChain.correction(9000.0) in 11.5..12.0)
        // Bands rise in order, end at 24 kHz, and the player's tone is added on top.
        val flat = SpeakerChain.bands(listOf(0, 0, 0, 0, 0))
        assertEquals(24000f, flat.last().first)
        assertTrue(flat.zipWithNext().all { (a, b) -> a.first < b.first })
        val warmer = SpeakerChain.bands(Eq.presets.getValue("Warmer"))
        val i = SpeakerChain.edges.toList().indexOf(800f)
        assertEquals(flat[i].second + 1.5f, warmer[i].second, 0.3f)
        // The compressor is gentle and the limiter sits just under full scale.
        assertTrue(SpeakerChain.compressor.all { it.ratio <= 2.5f && it.knee >= 6f })
        assertEquals(24000f, SpeakerChain.compressor.last().top)
        assertEquals(-1f, SpeakerChain.LIMIT_THRESHOLD)
    }

    @Test fun playStationOpensInTheOpenSourceCoreFirst() {
        assertEquals("ra:swanstation", Catalog.system("psx")!!.emulators.first())
        assertTrue("duckstation" in Catalog.system("psx")!!.emulators)
    }
}

class ExtrasTest {
    private val p = RetroPack.Paths("/storage/emulated/0/Timeline/retroarch", "com.retroarch.aarch64", "/data/user/0/com.retroarch.aarch64", "/data/app/x/base.apk")

    @Test fun theExtraButtonsDoNothingUntilTheyHaveBeenTaught() {
        val files = RetroPack.files(Look(), p)
        assertEquals(null, files["Snes9x/snes.cfg"])
        assertTrue(!files.getValue("Genesis Plus GX/gamegear.cfg").contains("input_"))
        assertEquals(emptyMap<String, String>(), Hotkeys.binds(0, 0, Hotkeys.usual("snes")))
    }

    @Test fun onceTaughtEachSystemGetsItsOwnJobsForThem() {
        val files = RetroPack.files(Look(m1 = 98, m2 = 101), p)
        val snes = files.getValue("Snes9x/snes.cfg")
        assertTrue(snes.contains("input_rewind_btn = \"98\"\n") && snes.contains("input_hold_fast_forward_btn = \"101\"\n"))
        assertTrue(snes.contains("rewind_enable = \"true\"\n") && snes.contains("input_menu_toggle_btn = \"nul\"\n"))
        val n64 = files.getValue("Mupen64Plus-Next/n64.cfg")
        assertTrue(n64.contains("input_menu_toggle_btn = \"98\"\n") && n64.contains("input_rewind_btn = \"nul\"\n") && !n64.contains("rewind_enable"))
        // one core, three folders, three files
        for (f in listOf("genesis", "mastersystem", "segacd", "gamegear")) assertTrue(f, "Genesis Plus GX/$f.cfg" in files)
        val mine = RetroPack.files(Look(m1 = 98, m2 = 101, extras = mapOf("snes" to ("Save state" to "Load state"))), p).getValue("Snes9x/snes.cfg")
        assertTrue(mine.contains("input_save_state_btn = \"98\"\n") && mine.contains("input_load_state_btn = \"101\"\n") && mine.contains("input_rewind_btn = \"nul\"\n"))
        assertEquals("Rewind (hold)" to "Fast-forward (hold)", Hotkeys.usual("gba"))
        assertEquals("RetroArch menu" to "Fast-forward (hold)", Hotkeys.usual("psx"))
    }

    @Test fun achievementsAreOffUnlessAnAccountIsGiven() {
        assertEquals("false", RetroPack.base(Look(), p)["cheevos_enable"])
        assertEquals(null, RetroPack.base(Look(), p)["cheevos_username"])
        val on = RetroPack.base(Look(achievements = Achievements("player", "secret", hardcore = false)), p)
        assertEquals("true", on["cheevos_enable"]); assertEquals("player", on["cheevos_username"]); assertEquals("secret", on["cheevos_password"])
        assertEquals("false", on["cheevos_hardcore_mode_enable"])
        // signed in, password forgotten: the file's password line is written empty, not left as it was
        assertEquals("", RetroPack.base(Look(achievements = Achievements("player", "", true)), p)["cheevos_password"])
        // the password never ends up in the remembered fingerprint of the setup
        assertTrue(!Look(achievements = Achievements("player", "secret", false)).signature.contains("secret"))
    }

    // ---- first run --------------------------------------------------------------------------

    private val threeOthers = listOf(
        SetupEmu("armsx2", "ARMSX2", listOf("PlayStation 2"), installed = false, fetchable = true, opened = false, inside = "Inside it: pick the BIOS folder."),
        SetupEmu("dolphin", "Dolphin", listOf("GameCube", "Wii"), installed = false, fetchable = true, opened = false, inside = "Inside it: add the folders."),
        SetupEmu("hakux", "hakuX", listOf("Xbox"), installed = false, fetchable = false, opened = false, inside = ""),
   )
    private val someCores = listOf("Nestopia", "Snes9x", "Genesis Plus GX", "SwanStation")
    private val fresh = SetupFacts(
        home = false, gamesFolder = "/storage/0000-0000/ROMs", games = 120, consoles = 6, canInstall = false,
        retroUsed = true, retroInstalled = false, retroStorage = false, cores = someCores,
        shaders = false, shadersBusy = false, others = threeOthers,
   )

    @Test fun aNewDeviceStartsAtTheHomeScreenStepAndListsEverythingInOrder() {
        val steps = FirstRun.steps(fresh)
        assertEquals(listOf("home", "games", "install", "retro", "cores", "shaders", "armsx2", "dolphin", "hakux", "art", "buttons", "finish"), steps.map { it.id })
        assertEquals(0, FirstRun.next(steps))
        // home, the installs, RetroArch's permission, its cores, the shaders, and one look inside each of the two it fetches
        assertEquals(7, FirstRun.left(steps))
        assertEquals("Press A to allow it", steps.first { it.id == "install" }.value)
        assertEquals("Finish later", steps.last().label)
        assertEquals("7 to go", steps.last().value)
    }

    @Test fun withNoGamesFolderTheSetupOffersToMakeOne() {
        val none = fresh.copy(home = true, gamesFolder = "", games = 0, consoles = 0, retroUsed = false, cores = emptyList(), others = emptyList())
        val steps = FirstRun.steps(none)
        val games = steps.first { it.id == "games" }
        assertFalse(games.done)
        assertEquals("Press A to make the folder", games.value)
        assertEquals("games", steps[FirstRun.next(steps)].id)
        // nothing to install until there are games, and no RetroArch steps at all
        assertEquals("None needed yet", steps.first { it.id == "install" }.value)
        assertTrue(steps.none { it.id == "retro" || it.id == "cores" || it.id == "shaders" })
        val empty = FirstRun.steps(none.copy(gamesFolder = "/storage/emulated/0/ROMs")).first { it.id == "games" }
        assertEquals("No games yet", empty.value)
        assertTrue(empty.note.contains("/storage/emulated/0/ROMs"))
    }

    @Test fun everySetupHintFitsTheThreeLinesTheListShows() {
        // The list shows three lines of a step's note and cuts the rest. The three emulators' own instructions are the
        // longest notes there are, and the end of each is the part that matters (which folder to pick).
        val others = listOf("armsx2" to listOf("PlayStation 2"), "dolphin" to listOf("GameCube", "Wii"), "hakux" to listOf("Xbox")).map { (id, systems) ->
            val e = Emulators.get(id)!!
            SetupEmu(id, e.label, systems, installed = true, fetchable = Sources.of(id) != null, opened = false, inside = e.setup)
        }
        val manyCores = Emulators.all.filter { it.startsWith("ra:") }.map { Emulators.coreName(it.removePrefix("ra:")) }
        val files = ConsoleFiles.needs.map { n -> ConsoleFiles.Finding(n.system, n.files[0], ConsoleFiles.State.MISSING, null, n.folder, n.optional) }
        for (granted in listOf(false, true)) for (cores in listOf(someCores, manyCores)) {
            val steps = FirstRun.steps(fresh.copy(home = true, canInstall = true, retroInstalled = true, retroStorage = true, cores = cores,
                shaders = true, files = files, others = others, dolphin = granted))
            for (s in steps) assertTrue("${s.id}: ${s.note.length} characters", s.note.length <= 285)
        }
    }

    @Test fun theInstallStepNamesWhatItFetchesAndFromWhere() {
        val step = FirstRun.steps(fresh.copy(home = true, canInstall = true)).first { it.id == "install" }
        assertEquals("Press A to install 3", step.value)
        assertTrue(step.note.contains("RetroArch, ARMSX2 and Dolphin"))
        assertTrue(step.note.contains("developers' own sites") && step.note.contains("signed by them"))
        assertTrue(step.note.contains("about ${Sources.retroarch.approxMb + Sources.armsx2.approxMb + Sources.dolphin.approxMb} MB"))
        assertFalse(step.note.contains("hakuX"))
        assertEquals("Downloading Dolphin  ·  45%", FirstRun.steps(fresh.copy(canInstall = true, installing = "Downloading Dolphin  ·  45%")).first { it.id == "install" }.value)
        val one = fresh.copy(home = true, canInstall = true, retroInstalled = true, others = threeOthers.map { if (it.id == "armsx2") it.copy(installed = true) else it })
        assertTrue(FirstRun.steps(one).first { it.id == "install" }.note.startsWith("Downloads Dolphin from its developers' own sites"))
    }

    @Test fun anEmulatorTimelineDoesNotFetchNeverHoldsUpTheList() {
        val done = fresh.copy(
            home = true, canInstall = true, retroInstalled = true, retroStorage = true, coresVisited = true, shaders = true,
            others = threeOthers.map { if (it.fetchable) it.copy(installed = true, opened = true) else it },
       )
        val steps = FirstRun.steps(done)
        assertEquals(0, FirstRun.left(steps))
        assertEquals("All set", steps.last().label)
        assertEquals(steps.lastIndex, FirstRun.next(steps))
        assertEquals("Not installed", steps.first { it.id == "hakux" }.value)
        assertTrue(steps.first { it.id == "hakux" }.optional)
    }

    @Test fun theListMovesOnOneStepAtATime() {
        var f = fresh.copy(home = true)
        assertEquals("install", FirstRun.steps(f)[FirstRun.next(FirstRun.steps(f))].id)
        f = f.copy(canInstall = true, retroInstalled = true, others = threeOthers.map { if (it.fetchable) it.copy(installed = true) else it })
        assertEquals("retro", FirstRun.steps(f)[FirstRun.next(FirstRun.steps(f))].id)
        assertEquals("Press A, then OK and Allow", FirstRun.steps(f).first { it.id == "retro" }.value)
        f = f.copy(retroStorage = true)
        assertEquals("cores", FirstRun.steps(f)[FirstRun.next(FirstRun.steps(f))].id)
        assertEquals("Press A, then pick 4", FirstRun.steps(f).first { it.id == "cores" }.value)
        f = f.copy(coresVisited = true, shadersBusy = true, shadersProgress = 35)
        assertEquals("shaders", FirstRun.steps(f)[FirstRun.next(FirstRun.steps(f))].id)
        assertEquals("Fetching  ·  35%", FirstRun.steps(f).first { it.id == "shaders" }.value)
        f = f.copy(shaders = true, shadersBusy = false)
        assertEquals("armsx2", FirstRun.steps(f)[FirstRun.next(FirstRun.steps(f))].id)
        assertEquals("Press A to open it", FirstRun.steps(f).first { it.id == "armsx2" }.value)
    }

    @Test fun theCoresStepNamesExactlyWhatToPick() {
        val step = FirstRun.steps(fresh.copy(home = true, retroInstalled = true, retroStorage = true)).first { it.id == "cores" }
        assertTrue(step.note.contains("Online Updater, Core Downloader"))
        assertTrue(step.note.contains("Nestopia, Snes9x, Genesis Plus GX and SwanStation"))
        // a RetroArch with every core already inside needs nothing
        assertTrue(FirstRun.steps(fresh.copy(retroInstalled = true, coresInside = 4)).first { it.id == "cores" }.done)
        // too many to name in three lines: as many as fit, and where the rest are
        val long = FirstRun.coreList(List(30) { "Core number $it" }, 120)
        assertTrue(long.length <= 120 && long.contains("more"))
    }

    @Test fun consoleFilesAreNamedCheckedAndNeverRequiredWhenOptional() {
        val missing = ConsoleFiles.Finding("psx", ConsoleFiles.needs.first { it.system == "psx" }.files[0], ConsoleFiles.State.MISSING, null, "bios", false)
        val dc = ConsoleFiles.Finding("dreamcast", ConsoleFiles.needs.first { it.system == "dreamcast" }.files[0], ConsoleFiles.State.MISSING, null, "bios/dc", true)
        val step = FirstRun.steps(fresh.copy(files = listOf(missing, dc))).first { it.id == "files" }
        assertEquals("1 missing", step.value)
        assertTrue(step.note.startsWith("PlayStation need"))
        assertFalse(step.done)
        val fine = FirstRun.steps(fresh.copy(files = listOf(missing.copy(state = ConsoleFiles.State.FOUND, name = "scph1001.bin"), dc))).first { it.id == "files" }
        assertTrue(fine.done)
        assertEquals("1 in place", fine.value)
    }

    @Test fun noStepNoteIsLongerThanThreeLinesOnTheScreen() {
        // the note box holds three lines of about a hundred characters
        for (facts in listOf(fresh, fresh.copy(canInstall = true), fresh.copy(retroInstalled = true), fresh.copy(retroInstalled = true, retroStorage = true)))
            for (s in FirstRun.steps(facts)) assertTrue("${s.id}: ${s.note.length}", s.note.length <= 290)
        for (id in listOf("armsx2", "dolphin", "melonds")) {
            val note = "For GameCube and Wii. " + Emulators.get(id)!!.setup
            assertTrue("$id: ${note.length}", note.length <= 300)
        }
    }

    // ---- official sources ------------------------------------------------------------------

    @Test fun emulatorsComeOnlyFromTheirOwnProjectsSignedWithTheirOwnKeys() {
        assertEquals(Sources.retroarch, Sources.of("ra:snes9x"))
        assertEquals("org.dolphinemu.dolphinemu", Sources.of("dolphin")!!.pkg)
        assertNull(Sources.of("hakux"))                                   // not fetched: no source to check it against
        for (s in listOf(Sources.retroarch, Sources.dolphin, Sources.armsx2, Sources.melonds)) {
            assertTrue(s.page.startsWith("https://"))
            assertTrue(s.certs.isNotEmpty() && s.certs.all { Regex("[0-9a-f]{64}").matches(it) })
        }
        assertTrue(Sources.SHADERS.startsWith("https://buildbot.libretro.com/"))
    }

    @Test fun theNewestStableRetroArchIsReadFromLibretrosListing() {
        val listing = """<a href="1.21.0/">1.21.0</a> <a href="1.22.2/">1.22.2</a> <a href="1.9.10/">1.9.10</a> <a href="1.22.10/">x</a>"""
        assertEquals("1.22.10", Sources.newestRetroArch(listing))
        assertEquals(Sources.RETROARCH_KNOWN, Sources.newestRetroArch(null))
        assertEquals("https://buildbot.libretro.com/stable/1.22.2/android/RetroArch_aarch64.apk", Sources.retroArchApk("1.22.2"))
    }

    @Test fun theRightArmsx2BuildIsPickedForTheDevice() {
        assertEquals("a15-armv8.2-sdk35", Sources.armsx2Flavor(35))
        assertEquals("a13-armv8.2-sdk33", Sources.armsx2Flavor(34))
        assertEquals("a11-armv8.2-sdk30", Sources.armsx2Flavor(31))
        assertEquals("legacy-armv8.0-sdk26", Sources.armsx2Flavor(29))
        val json = """{"assets":[{"name":"ARMSX2-2.9.1-Linux-arm64-4K-pages.AppImage","browser_download_url":"https://x/a"},
            {"name":"ARMSX2-2.9.1-a13-armv8.2-sdk33.apk","browser_download_url":"https://github.com/ARMSX2/ARMSX2/releases/download/2.9.1/ARMSX2-2.9.1-a13-armv8.2-sdk33.apk"}]}"""
        assertEquals("https://github.com/ARMSX2/ARMSX2/releases/download/2.9.1/ARMSX2-2.9.1-a13-armv8.2-sdk33.apk",
            Sources.githubAsset(json, Regex(Regex.escape(Sources.armsx2Flavor(33)))))
        assertNull(Sources.githubAsset(json, Regex("sdk35")))
        assertNull(Sources.githubAsset("not json", Regex(".")))
    }

    @Test fun consoleFilesAreCheckedButAnUnknownCopyIsNeverCalledWrong() {
        val root = java.nio.file.Files.createTempDirectory("roms").toFile()
        java.io.File(root, "bios").mkdirs()
        java.io.File(root, "bios/scph1001.bin").writeText("a")
        java.io.File(root, "bios/sega_101.bin").writeText("b")
        val known = mapOf("scph1001.bin" to "924e392ed05558ffdb115408c263dccf")
        val found = ConsoleFiles.check(root, setOf("psx", "saturn", "segacd", "nes")) { f -> known[f.name] ?: "0000" }
        assertEquals(listOf("psx", "saturn", "segacd"), found.map { it.system })
        assertEquals(ConsoleFiles.State.CHECKED, found[0].state)
        assertEquals(ConsoleFiles.State.FOUND, found[1].state)      // present, not a checksum on the list: "found", not "wrong"
        assertEquals(ConsoleFiles.State.MISSING, found[2].state)
        root.deleteRecursively()
    }

    @Test fun aConsoleWithoutItsFileIsStoppedBeforeRetroArch() {
        val roms = java.nio.file.Files.createTempDirectory("roms").toFile()
        val retro = java.nio.file.Files.createTempDirectory("system").toFile()
        assertEquals(ConsoleFiles.Ready.MISSING, ConsoleFiles.ready("psx", roms, retro))
        assertEquals(ConsoleFiles.Ready.YES, ConsoleFiles.ready("snes", roms, retro))          // nothing needed
        assertEquals(ConsoleFiles.Ready.YES, ConsoleFiles.ready("dreamcast", roms, retro))     // optional
        assertEquals(ConsoleFiles.Ready.YES, ConsoleFiles.ready("ps2", roms, retro))           // its own emulator's folder
        java.io.File(roms, "bios").mkdirs(); java.io.File(roms, "bios/SCPH5501.BIN").writeText("a")
        assertEquals(ConsoleFiles.Ready.COPY, ConsoleFiles.ready("psx", roms, retro))
        java.io.File(retro, "scph5501.bin").writeText("a")
        assertEquals(ConsoleFiles.Ready.YES, ConsoleFiles.ready("psx", roms, retro))
        assertEquals(ConsoleFiles.Ready.YES, ConsoleFiles.ready("psx", null, retro))           // kept in RetroArch's own folder
        roms.deleteRecursively(); retro.deleteRecursively()
    }

    @Test fun eachNewFolderSaysWhatGoesInIt() {
        val psx = FolderNotes.console(Catalog.system("psx")!!)
        assertTrue(psx.startsWith("PlayStation, 1995\n"))
        assertTrue(psx.contains(".chd") && psx.contains("SwanStation") && psx.contains("(Disc 1)") && psx.contains("bios folder"))
        assertTrue(psx.contains("ps1, playstation"))
        val snes = FolderNotes.console(Catalog.system("snes")!!)
        assertFalse(snes.contains("Disc") || snes.contains("Also needed"))
        assertTrue(FolderNotes.console(Catalog.system("ps2")!!).contains("ARMSX2"))
        assertTrue(FolderNotes.bios().contains("Saturn: mpr-17933.bin or sega_101.bin, in bios"))
        // Calm house style: no exclamation points, no long dashes.
        (Catalog.systems.map { FolderNotes.console(it) } + FolderNotes.bios()).forEach { assertFalse(it.contains("!") || it.contains("—")) }
        java.io.File(System.getProperty("java.io.tmpdir"), "timeline-note-psx.txt").writeText(psx + "\n----\n" + FolderNotes.bios())
    }

    @Test fun comingBackSaysHowLongThatWas() {
        assertNull(PlayLog.session(40, 40))                                   // under a minute: nothing to say
        assertEquals("42 min.", PlayLog.session(42 * 60, 42 * 60 + 30))       // the first time
        assertEquals("42 min. 6 h 10 min in all.", PlayLog.session(42 * 60, 6 * 3600 + 600))
        assertEquals("1 h 05 min.", PlayLog.session(3900, 3900))
    }

    @Test fun discsOfOneGameAreOneGame() {
        val root = java.nio.file.Files.createTempDirectory("roms").toFile()
        val psx = java.io.File(root, "psx").apply { mkdirs() }
        listOf("Quiet Harbor (USA) (Disc 2).chd", "Quiet Harbor (USA) (Disc 1).chd", "Quiet Harbor (USA) (Disc 3).chd",
            "Lone Disc (USA) (Disc 1).chd", "Single (USA).chd",
            "Long Road (USA) (Disc 1).cue", "Long Road (USA) (Disc 1).bin", "Long Road (USA) (Disc 2).cue", "Long Road (USA) (Disc 2).bin",
            "Listed (USA) (Disc 1).chd", "Listed (USA) (Disc 2).chd")
            .forEach { java.io.File(psx, it).writeText("x") }
        java.io.File(psx, "Listed (USA).m3u").writeText("\uFEFF# discs\n./Listed (USA) (Disc 1).chd\n\nListed (USA) (Disc 2).chd\n")
        val games = Library.scanSystem(root, Catalog.system("psx")!!)
        assertEquals(listOf("Listed", "Lone Disc", "Long Road", "Quiet Harbor", "Single"), games.map { it.title })
        val harbor = games.first { it.title == "Quiet Harbor" }
        assertEquals("Quiet Harbor (USA) (Disc 1).chd", harbor.file)                      // the key stays the first disc
        assertEquals(listOf(1, 2, 3), harbor.discs.map { Discs.number(java.io.File(it).name) })
        assertEquals(2, games.first { it.title == "Long Road" }.discs.size)
        assertTrue(games.first { it.title == "Listed" }.path.endsWith("Listed (USA).m3u"))
        assertEquals(2, games.first { it.title == "Listed" }.discs.size)
        assertTrue(games.first { it.title == "Lone Disc" }.discs.isEmpty())                 // one disc of a set is a game, plainly
        assertEquals("Quiet Harbor (USA).chd", Discs.shared("Quiet Harbor (USA) (Disc 2 of 3).chd"))
        assertNull(Discs.number("Disco Comet (USA).chd"))
        assertEquals("/a/1.chd\n/a/2.chd\n", Discs.playlist(listOf("/a/1.chd", "/a/2.chd")))
        root.deleteRecursively()
    }

    @Test fun retroArchLogSaysWhyAGameDidNotOpen() {
        assertNull(RetroLog.failure("[INFO] [Core] Geometry: 256x224, Aspect: 1.333\n[INFO] [Video] Set video size to: fullscreen.\n"))
        assertEquals("RetroArch could not read the game file.",
            RetroLog.failure("[INFO] [Content] Loading content file: \"/x/Flux-Racer (USA).sfc\".\n[ERROR] [Content] Could not read content file: \"/x/Flux-Racer (USA).sfc\".\n[INFO] [Core] Unloading core...\n"))
        assertEquals("RetroArch could not start its graphics for this system.",
            RetroLog.failure("[INFO] [Core] Geometry: 1024x956\n[ERROR] [Vulkan] Failed to create surface.\n[ERROR] [Video] Cannot open video driver. Exiting...\n[ERROR] Fatal error received in: \"video_driver_init_internal()\"\n"))
        assertEquals("A BIOS file this system needs was not found.",
            RetroLog.failure("[WARN] Firmware missing: sega_101.bin\n[ERROR] [Content] Failed to load content\n"))
        assertEquals("RetroArch reported an error while loading it.", RetroLog.failure("[ERROR] [Content] Failed to load content\n"))
        assertTrue(RetroPack.base(Look(), RetroPack.Paths("/h", "pkg", "/d", "/a")).getValue("log_dir") == "/h/logs")
        // Without this RetroArch writes nothing at all, errors included, and a failed start could not be noticed.
        assertEquals("true", RetroPack.base(Look(), RetroPack.Paths("/h", "pkg", "/d", "/a")).getValue("log_verbosity"))
    }

    @Test fun settingsFilesKeepWhatIsNotTheirs() {
        val before = "[Core]\nCPUThread = True\n[Controls]\nPadType0 = 6\n"
        val after = Ini.merge(before, mapOf("Controls" to mapOf("WiimoteSource0" to "0")))
        assertEquals("0", Ini.value(after, "Controls", "WiimoteSource0"))
        assertEquals("6", Ini.value(after, "Controls", "PadType0"))
        assertEquals("True", Ini.value(after, "Core", "CPUThread"))
        assertEquals(after, Ini.merge(after, mapOf("Controls" to mapOf("WiimoteSource0" to "0"))))       // nothing to write the second time
        val added = Ini.merge("[Settings]\nAspectRatio = 0\n[Enhancements]\nMaxAnisotropy = 2\n", mapOf("Settings" to mapOf("InternalResolution" to "2")))
        assertTrue(added.indexOf("InternalResolution") < added.indexOf("[Enhancements]"))              // added inside its own section
        assertEquals("True", Ini.value(Ini.merge("[A]\nK = False\n", emptyMap(), mapOf("A" to mapOf("K" to "True", "N" to "True"))), "A", "N"))
        assertEquals("False", Ini.value(Ini.merge("[A]\nK = False\n", emptyMap(), mapOf("A" to mapOf("K" to "True"))), "A", "K"))
    }

    @Test fun dolphinIsSetUpThroughItsGameSettingsFiles() {
        val docs = DolphinPack.docs(Look(sharp = false), "Android/1/Retroid Pocket Controller")
        val byPath = docs.associateBy { it.path }
        // the profiles come before the files that name them
        assertTrue(docs.indexOfFirst { it.path == "Config/Profiles/GCPad/Timeline.ini" } < docs.indexOfFirst { it.path == "GameSettings/G.ini" })
        val g = byPath.getValue("GameSettings/G.ini").text!!
        assertEquals("Vulkan", Ini.value(g, "Core", "GFXBackend"))
        assertEquals("1", Ini.value(g, "Video_Settings", "InternalResolution"))
        assertEquals("True", Ini.value(g, "Core", "ProgressiveScan"))           // component, the best cable they had
        assertEquals("False", Ini.value(DolphinPack.docs(Look(signal = mapOf("gcwii" to "Composite")), "Android/1/Retroid Pocket Controller")
            .single { it.path == "GameSettings/G.ini" }.text!!, "Core", "ProgressiveScan"))
        // component: the sharp progressive picture; composite: the game's own blending against flicker, and its dither
        assertEquals("True", Ini.value(g, "Video_Enhancements", "DisableCopyFilter"))
        assertEquals("True", Ini.value(g, "Video_Enhancements", "ForceTrueColor"))
        val gc = DolphinPack.docs(Look(signal = mapOf("gcwii" to "Composite"), sharp = false), "Android/1/Retroid Pocket Controller").single { it.path == "GameSettings/G.ini" }.text!!
        assertEquals("False", Ini.value(gc, "Video_Enhancements", "DisableCopyFilter"))
        assertEquals("False", Ini.value(gc, "Video_Enhancements", "ForceTrueColor"))
        val gs = DolphinPack.docs(Look(sharp = true, signal = mapOf("gcwii" to "Component")), "Android/1/Retroid Pocket Controller").single { it.path == "GameSettings/G.ini" }.text!!
        assertEquals("3", Ini.value(gs, "Video_Settings", "InternalResolution")); assertEquals("True", Ini.value(gs, "Core", "ProgressiveScan"))
        assertEquals("True", Ini.value(gs, "Video_Enhancements", "DisableCopyFilter"))
        assertEquals("2", Ini.value(g, "Video_Settings", "ShaderCompilationMode"))
        assertEquals("True", Ini.value(g, "Video_Settings", "WaitForShadersBeforeStarting"))
        assertEquals("False", Ini.value(g, "Dolphin.Android", "ShowInputOverlay"))
        assertEquals("Timeline", Ini.value(g, "Controls", "PadProfile1"))
        assertNull(Ini.value(g, "Wii", "Widescreen"))
        val r = byPath.getValue("GameSettings/R.ini").text!!
        assertEquals("False", Ini.value(r, "Wii", "Widescreen"))
        assertEquals("Timeline", Ini.value(r, "Controls", "WiimoteProfile1"))
        assertEquals(r, byPath.getValue("GameSettings/S.ini").text)
        val pad = byPath.getValue("Config/Profiles/GCPad/Timeline.ini").text!!
        assertEquals("Android/1/Retroid Pocket Controller", Ini.value(pad, "Profile", "Device"))
        assertEquals("`Button A`", Ini.value(pad, "Profile", "Buttons/A"))
        assertEquals("Start", Ini.value(pad, "Profile", "Buttons/Start"))
        assertEquals("`Axis 14-`", Ini.value(pad, "Profile", "C-Stick/Up"))
        assertEquals("`Axis 23+` | `Axis 17+`", Ini.value(pad, "Profile", "Triggers/L-Analog"))
        val remote = byPath.getValue("Config/Profiles/Wiimote/Timeline.ini").text!!
        assertEquals("Nunchuk", Ini.value(remote, "Profile", "Extension"))
        assertEquals("`Axis 1-`", Ini.value(remote, "Profile", "Nunchuk/Stick/Up"))
        assertEquals("True", Ini.value(byPath.getValue("Config/Profiles/Wiimote/Timeline sideways.ini").text!!, "Profile", "Options/Sideways Wiimote"))
        assertEquals("0", byPath.getValue("GameSettings/RSBE01.ini").set.getValue("Controls").getValue("WiimoteSource0"))
        assertEquals("OGL", Ini.value(DolphinPack.family(Look(dolphinGl = true), wiiGame = false, pad = true), "Core", "GFXBackend"))
        // controls left to Dolphin: no profile is written or named, and the touch buttons still stay off
        val own = DolphinPack.docs(Look(dolphinPad = false), "Android/1/Retroid Pocket Controller")
        assertTrue(own.none { it.path.startsWith("Config/Profiles") })
        assertNull(Ini.value(own.first { it.path == "GameSettings/G.ini" }.text!!, "Controls", "PadProfile1"))
        assertEquals("False", Ini.value(own.first { it.path == "GameSettings/G.ini" }.text!!, "Dolphin.Android", "ShowInputOverlay"))
        // no gamepad of the device's own found: the touch buttons stay, and no profile is named
        val light = DolphinPack.docs(Look(lighterCodes = mapOf("SX4E01" to true, "GM4E01" to false)), "Android/1/Retroid Pocket Controller")
        assertEquals(mapOf("InternalResolution" to "1"), light.first { it.path == "GameSettings/SX4E01.ini" && it.set.isNotEmpty() }.set["Video_Settings"])
        assertEquals(setOf("InternalResolution"), light.first { it.path == "GameSettings/GM4E01.ini" }.remove["Video_Settings"])
        val bare = DolphinPack.docs(Look(), null)
        assertTrue(bare.none { it.path.startsWith("Config/Profiles") })
        val g2 = bare.first { it.path == "GameSettings/G.ini" }.text!!
        assertEquals("True", Ini.value(g2, "Dolphin.Android", "ShowInputOverlay"))
        assertNull(Ini.value(g2, "Controls", "PadProfile1"))
    }

    @Test fun playStationAndDreamcastHaveAnotherWayToDraw() {
        val p = RetroPack.Paths("/h", "pkg", "/d", "/a")
        val usual = RetroPack.files(Look(), p)
        assertTrue(usual.getValue("SwanStation/SwanStation.opt").contains("swanstation_GPU_Renderer = \"Auto\""))
        assertTrue(usual.getValue("Flycast/Flycast.cfg").contains("video_driver = \"vulkan\""))
        assertTrue(usual.getValue("Flycast/Flycast.cfg").contains("video_smooth = \"true\""))
        val other = RetroPack.files(Look(compatible = true), p)
        assertTrue(other.getValue("SwanStation/SwanStation.opt").contains("swanstation_GPU_Renderer = \"Software\""))
        assertTrue(other.getValue("Flycast/Flycast.cfg").contains("video_driver = \"gl\""))
        assertTrue(other.getValue("Beetle PSX HW/Beetle PSX HW.opt").contains("beetle_psx_hw_renderer = \"software\""))
        // nothing else changes: the television consoles keep Vulkan, which their screens need
        assertEquals(usual.getValue("Snes9x/Snes9x.slangp"), other.getValue("Snes9x/Snes9x.slangp"))
        assertEquals("vulkan", RetroPack.base(Look(compatible = true), p).getValue("video_driver"))
    }

    @Test fun dolphinsSettingsAreAStepOfTheirOwn() {
        val dolphin = SetupEmu("dolphin", "Dolphin", listOf("GameCube", "Wii"), installed = true, fetchable = true, opened = true, inside = "x")
        val base = SetupFacts(home = true, gamesFolder = "/x/ROMs", games = 3, consoles = 1, canInstall = true, retroUsed = false,
            retroInstalled = false, retroStorage = false, shaders = false, shadersBusy = false, others = listOf(dolphin))
        assertTrue(FirstRun.steps(base).none { it.id == "dolphinset" })                      // Dolphin not in use: no such step
        val waiting = FirstRun.steps(base.copy(dolphin = false))
        assertEquals("dolphinset", waiting[FirstRun.next(waiting)].id)
        assertEquals(1, FirstRun.left(waiting))
        val done = FirstRun.steps(base.copy(dolphin = true))
        assertEquals(0, FirstRun.left(done))
        assertTrue(done.indexOfFirst { it.id == "dolphinset" } > done.indexOfFirst { it.id == "dolphin" })
    }

    @Test fun aLogNamesTheCauseThatStoppedTheGame() {
        // a BIOS warning for some other region, then the graphics fail: the graphics are the cause
        assertEquals("RetroArch could not start its graphics for this system.",
            RetroLog.failure("[WARN] Firmware missing: scph5500.bin\n[INFO] [Core] Geometry: 1024x956\n[ERROR] [Video] Cannot open video driver. Exiting...\n[ERROR] Fatal error received in: \"video_driver_init_internal()\"\n"))
        assertEquals("The core for this system could not be loaded.", RetroLog.failure("[WARN] Firmware missing: x.bin\n[ERROR] Failed to open libretro core: \"/x.so\"\n"))
        assertEquals("A BIOS file this system needs was not found.", RetroLog.failure("[WARN] Firmware missing: sega_101.bin\n[ERROR] [Content] Failed to load content\n"))
    }

    @Test fun aCoreRunningWithoutItsDiscIsNoticed() {
        // each line as its core wrote it on Android 13 when handed a disc image it could not read
        assertTrue(RetroLog.noDisc("[Boot] Failed to load CD image '/x/Broken Test (USA).chd': invalid data. Booting BIOS with no disc.\n"))
        assertTrue(RetroLog.noDisc("Loading \"/x/Banner Tactics III (USA).chd\"\nFailed to open CD: \"/x/Banner Tactics III (USA).chd\".\n"))
        assertTrue(RetroLog.noDisc("Failed to load CHD image: /x/Broken Test (USA).chd[SRAM] Skipping SRAM load.\n"))
        assertFalse(RetroLog.noDisc("[Content] Loading content file: \"/x/Candlewick Rising (USA).sfc\".\n[Core] Geometry: 256x224\n"))
        assertNull(RetroLog.failure("[Boot] Failed to load CD image '/x/a.chd': invalid data. Booting BIOS with no disc.\n"))      // RetroArch itself is well
    }

    @Test fun settingsCanBeTakenBackOut() {
        val before = "[Core]\nCPUThread = True\n[Controls]\nPadType0 = 6\nWiimoteSource0 = 0\n[Video]\nX = 1\n"
        val after = Ini.merge(before, emptyMap(), remove = mapOf("Controls" to setOf("WiimoteSource0")))
        assertNull(Ini.value(after, "Controls", "WiimoteSource0"))
        assertEquals("6", Ini.value(after, "Controls", "PadType0"))
        assertEquals("1", Ini.value(after, "Video", "X"))
        assertEquals(before, Ini.merge(before, emptyMap(), remove = mapOf("Nowhere" to setOf("X"), "Core" to setOf("Absent"))))
        // with the controls left to Dolphin, the three games given something special are put back as they were
        val own = DolphinPack.docs(Look(dolphinPad = false), "Android/1/Retroid Pocket Controller")
        assertEquals(setOf("WiimoteSource0", "WiimoteSource1", "WiimoteSource2", "WiimoteSource3", "PadProfile2", "PadProfile3", "PadProfile4"),
            own.first { it.path == "GameSettings/RSBE01.ini" }.remove.getValue("Controls"))
        assertEquals(setOf("WiimoteProfile1"), own.first { it.path == "GameSettings/R7PE01.ini" }.remove.getValue("Controls"))
        assertNull(own.first { it.path == "GameSettings/RSBE01.ini" }.set["Controls"])           // only its fixes are set
    }

    @Test fun gamesThatNeedAFixGetItInTheirOwnFileWithEverythingElse() {
        val files = RetroPack.files(Look(), RetroPack.Paths("/h", "pkg", "/d", "/a"))
        val spyro = files.getValue("SwanStation/Spyro the Dragon (USA).opt")
        assertTrue(spyro.contains("swanstation_GPU_PGXPCPU = \"true\"") && spyro.contains("swanstation_GPU_PGXPCulling = \"false\""))
        assertTrue(spyro.contains("swanstation_GPU_ResolutionScale"))                 // and the core's own settings with it
        assertTrue(files.getValue("SwanStation/Tekken 3 (USA).opt").contains("swanstation_GPU_PGXPTolerance = \"3.0\""))
        assertFalse(files.getValue("SwanStation/SwanStation.opt").contains("PGXPCPU = \"true\""))   // not for every game
        val mm = files.getValue("Mupen64Plus-Next/Legend of Zelda, The - Majora's Mask (USA).opt")
        assertTrue(mm.contains("mupen64plus-EnableCopyAuxToRDRAM = \"True\"") && mm.contains("mupen64plus-43screensize"))
        assertTrue(files.getValue("Flycast/Flycast.opt").contains("reicast_anisotropic_filtering = \"16\""))
        // a game run lighter keeps its fix
        val light = RetroPack.files(Look(lighter = mapOf("psx/Tekken 3 (USA)" to true)), RetroPack.Paths("/h", "pkg", "/d", "/a"))
        assertTrue(light.getValue("SwanStation/Tekken 3 (USA).opt").contains("swanstation_GPU_PGXPCPU = \"true\""))
    }

    @Test fun clocksAndSensorsAreSetUp() {
        val files = RetroPack.files(Look(), RetroPack.Paths("/h", "pkg", "/d", "/a"))
        // the DS clock follows the device's clock, so resuming a kept place does not leave it behind
        assertTrue(files.getValue("melonDS DS/melonDS DS.opt").contains("melonds_start_time_mode = \"sync\""))
        // Boktai starts with some sun, and keeps the core's other options
        val boktai = files.getValue("mGBA/Boktai - The Sun Is in Your Hand (USA).opt")
        assertTrue(boktai.contains("mgba_solar_sensor_level = \"5\""))
        assertTrue(boktai.contains("mgba_interframe_blending = \"mix\""))
    }

    private val utc = java.util.TimeZone.getTimeZone("UTC")
    private fun at(y: Int, mo: Int, d: Int, h: Int = 12, mi: Int = 0, sec: Int = 0): Long =
        java.util.Calendar.getInstance(utc).apply { clear(); set(y, mo - 1, d, h, mi, sec) }.timeInMillis

    @Test fun saveFilesAreKnownByKindAndByGame() {
        assertEquals(Saves.Kind.SAVE, Saves.kind("Candlewick Rising (USA).srm"))
        assertEquals(Saves.Kind.CLOCK, Saves.kind("Orchard Days (USA, Europe) (Rev 1).rtc"))
        assertEquals(Saves.Kind.PLACE, Saves.kind("Candlewick Rising (USA).state.auto"))
        assertEquals(Saves.Kind.ASIDE, Saves.kind("Candlewick Rising (USA).state.auto.earlier"))
        assertEquals(Saves.Kind.SLOT, Saves.kind("Candlewick Rising (USA).state"))
        assertEquals(Saves.Kind.SLOT, Saves.kind("Candlewick Rising (USA).state3"))
        assertEquals(Saves.Kind.SAVE, Saves.kind("Fare Rush (USA).A1.bin"))
        assertEquals(Saves.Kind.SAVE, Saves.kind("Duskmere - The Long Voyage (USA).brm"))
        assertEquals(Saves.Kind.OTHER, Saves.kind("Glasswing (USA).ldci"))
        val stem = "Seventh Lighthouse, The - Keeper's Dream DX (USA, Europe) (Rev 2)"
        assertTrue(Saves.belongs("$stem.srm", stem))
        assertTrue(Saves.belongs("$stem.state.auto", stem))
        assertTrue(Saves.belongs("$stem.state.auto.earlier", stem))
        assertTrue(Saves.belongs("Fare Rush (USA).A1.bin", "Fare Rush (USA)"))
        assertTrue(Saves.belongs("Hedgerow CD (USA)_cart.brm", "Hedgerow CD (USA)"))
        assertTrue(Saves.belongs("ptiger2.nv", "ptiger2"))
        assertFalse(Saves.belongs("ptiger2f.nv", "ptiger2"))                                   // a different arcade game
        assertFalse(Saves.belongs("Mecha Mole 2 (USA).srm", "Mecha Mole"))
        assertFalse(Saves.belongs("Candlewick Rising (USA).srm", ""))
        assertEquals("Gambatte", Saves.coreFolder("ra:gambatte"))
        assertEquals("melonDS DS", Saves.coreFolder("ra:melondsds"))
        assertNull(Saves.coreFolder("dolphin"))
    }

    @Test fun earlierVersionsAreNamedByTheirMoment() {
        val t = at(2026, 1, 2, 3, 4, 5)
        val name = Saves.versionName("Candlewick Rising (USA).srm", t, zone = utc)
        assertEquals("Candlewick Rising (USA).srm.20260102-030405", name)
        assertEquals("Candlewick Rising (USA).srm", Saves.original(name))
        assertEquals(t, Saves.writtenAt(name, utc))
        assertNull(Saves.original("Candlewick Rising (USA).srm"))
        assertNull(Saves.original("Candlewick Rising (USA).state1"))
        // until when it was in use is in the name too, so a copy of the card that loses file times loses nothing
        val until = at(2026, 1, 3, 4, 5, 6)
        val full = Saves.versionName("Candlewick Rising (USA).srm", t, until, utc)
        assertEquals("Candlewick Rising (USA).srm.20260102-030405_20260103-040506", full)
        assertEquals("Candlewick Rising (USA).srm", Saves.original(full))
        assertEquals(t, Saves.writtenAt(full, utc))
        assertEquals(until, Saves.usedUntil(full, utc))
        assertNull(Saves.usedUntil(name, utc))
        // a place's earlier version is still known for what it is
        assertEquals(Saves.Kind.PLACE, Saves.kind(Saves.original("Candlewick Rising (USA).state.auto.20260102-030405")!!))
    }

    @Test fun earlierVersionsAreThinnedGently() {
        // fewer than the number always kept: nothing goes
        assertTrue(Saves.drop((1..6).map { at(2030, 1, it) }, zone = utc).isEmpty())
        // ten sittings in one evening after a week of one a day: the newest six stay, then one for each earlier day
        val evening = (0..9).map { at(2030, 1, 13, 18, it * 5) }
        val week = (6..12).map { at(2030, 1, it, 20) }
        val gone = Saves.drop(evening + week, zone = utc)
        assertEquals(evening.sorted().take(4), gone.sorted())                          // the four oldest of the evening: that day is covered
        assertTrue(week.none { it in gone })
        // two saves on an earlier day: the later of the two is the one kept
        val two = listOf(at(2030, 1, 1, 9), at(2030, 1, 1, 21))
        assertEquals(listOf(two[0]), Saves.drop(evening.takeLast(6) + two, zone = utc))
        // never more than twenty in all, and it is the oldest that go
        val many = (1..40).map { at(2026, 1, 1) + it * 86_400_000L }
        val dropped = Saves.drop(many, zone = utc)
        assertEquals(20, dropped.size)
        assertEquals(many.take(20), dropped.sorted())
        // places and save states: two, with no daily ones
        assertEquals(3, Saves.drop((1..5).map { at(2030, 1, it) }, Saves.PLACES, Saves.PLACES, utc).size)
    }

    @Test fun momentsAreSaidPlainly() {
        val now = at(2030, 1, 13, 20, 0)
        assertEquals("Today 03:04", Saves.day(at(2030, 1, 13, 3, 4), now, utc))
        assertEquals("Yesterday 09:05", Saves.day(at(2030, 1, 12, 9, 5), now, utc))
        assertEquals("3 Jan, 21:10", Saves.day(at(2030, 1, 3, 21, 10), now, utc))
        assertEquals("25 Dec 2025", Saves.day(at(2025, 12, 25, 8, 0), now, utc))
        assertEquals("Yesterday 23:59", Saves.day(at(2025, 12, 31, 23, 59), at(2026, 1, 1, 0, 30), utc))
        assertEquals("", Saves.day(0L, now, utc))
    }

    @Test fun aSaveIsPutBackAsItStoodAtAMoment() {
        val srm = "saves/Gambatte/Orchard.srm"; val rtc = "saves/Gambatte/Orchard.rtc"
        val h = 3_600_000L
        // three earlier saves, each last written at the end of a sitting and in use until the next one changed it
        val saves = listOf(1 * h to 2 * h, 2 * h to 3 * h, 3 * h to 4 * h)
        // the save takes the version written at that moment; the clock has never changed, has nothing on record, and is left alone
        var plan = Saves.asOf(2 * h, mapOf(srm to saves), mapOf(srm to 4 * h, rtc to 4 * h))
        assertEquals(2 * h, plan[srm])
        assertNull(plan.getValue(rtc))
        // RetroArch writes the clock file again at every exit, unchanged. The clock was set once, long ago: its one
        // earlier version stopped being in use before the moment asked for, so the clock in use is the right one already.
        // (Taking "the last version written before the moment" here would wind the clock back to before it was set.)
        plan = Saves.asOf(2 * h, mapOf(srm to saves, rtc to listOf(h / 2 to h / 2 + 60_000)), mapOf(srm to 4 * h, rtc to 4 * h))
        assertNull(plan.getValue(rtc))
        // a clock that was set again after the moment goes back with the save: the version in use then is the one that
        // was replaced afterwards, whenever it was last written
        plan = Saves.asOf(2 * h, mapOf(srm to saves, rtc to listOf(1 * h to 3 * h + 1000)), mapOf(srm to 4 * h, rtc to 4 * h))
        assertEquals(1 * h, plan[rtc])
        // of two that outlived the moment, the one that was in use then is the first to have been replaced
        plan = Saves.asOf(1 * h, mapOf(srm to saves, rtc to listOf(1 * h - 5000 to 2 * h, 2 * h to 3 * h)), mapOf(srm to 4 * h, rtc to 4 * h))
        assertEquals(1 * h - 5000, plan[rtc])
        // the save in use is the one asked for: nothing on record matches, nothing outlived it, nothing changes
        assertNull(Saves.asOf(4 * h, mapOf(srm to saves), mapOf(srm to 4 * h)).getValue(srm))
        // GameCube and Wii files are written once each: a second file last written before the moment and replaced after it
        val a = "Wii/title/00010000/53434745/data/a.sav"; val b = "Wii/title/00010000/53434745/data/b.sav"
        plan = Saves.asOf(11 * h, mapOf(a to listOf(10 * h to 12 * h), b to listOf(10 * h to 11 * h, 11 * h to 13 * h)), mapOf(a to 12 * h, b to 13 * h))
        assertEquals(10 * h, plan[a])
        assertEquals(11 * h, plan[b])
    }

    @Test fun aPlaceIsSetAsideOnlyWhenTheGamesOwnSaveIsNewer() {
        val hour = 3_600_000L
        // left properly: the place was written after the last start
        assertFalse(Saves.placeIsStale(placeWritten = 10 * hour, lastStart = 9 * hour, ownSaves = listOf(10 * hour)))
        // the last sitting ended another way, and the game was saved in it: the place is older than that save
        assertTrue(Saves.placeIsStale(placeWritten = 5 * hour, lastStart = 9 * hour, ownSaves = listOf(9 * hour + 600_000)))
        // the last sitting ended another way with nothing saved: the place is still the newest progress there is
        assertFalse(Saves.placeIsStale(placeWritten = 5 * hour, lastStart = 9 * hour, ownSaves = listOf(5 * hour + 1500)))
        assertFalse(Saves.placeIsStale(placeWritten = 5 * hour, lastStart = 9 * hour, ownSaves = emptyList()))
        // never started through the launcher: nothing is known, nothing is touched
        assertFalse(Saves.placeIsStale(placeWritten = 5 * hour, lastStart = 0L, ownSaves = listOf(9 * hour)))
    }

    @Test fun gameCubeAndWiiSavesAreFoundByTheGamesCode() {
        assertEquals(0L, Saves.codeOffset("Hollyhock Village (USA).iso"))
        assertEquals(0L, Saves.codeOffset("Kestrel Air Rally (USA).nkit.iso"))
        assertEquals(0x8000L, Saves.codeOffset("Tandem Karts - Twin Turbo!! (USA).ciso"))
        assertEquals(0x58L, Saves.codeOffset("Sunlit Isles (USA).rvz"))
        assertNull(Saves.codeOffset("Harrow House (USA).m3u"))
        assertEquals("GTKE00", Saves.code("GTKE00\u0000\u0000".toByteArray(Charsets.ISO_8859_1)))
        assertNull(Saves.code(byteArrayOf(0, 1, 2, 3, 4, 5)))
        assertNull(Saves.code("GTK".toByteArray()))
        assertTrue(Saves.dolphinOwns("GC/USA/Card A/00-GTKE-TandemKarts Twin Turbo!!.gci", "GTKE00"))
        assertFalse(Saves.dolphinOwns("GC/USA/Card A/00-GKRE-KestrelAirRally.gci", "GTKE00"))
        assertTrue(Saves.dolphinOwns("Wii/title/00010000/53434745/data/progress.sav", "SCGE00"))
        assertFalse(Saves.dolphinOwns("Wii/title/00010000/53434745/content/title.tmd", "SCGE00"))
        assertTrue(Saves.dolphinSave("GC/USA/Card A/00-GTKE-TandemKarts Twin Turbo!!.gci"))
        assertTrue(Saves.dolphinSave("GC/MemoryCardA.USA.raw"))
        assertFalse(Saves.dolphinSave("GC/SRAM.raw"))                                  // the console's settings, not a save
        assertTrue(Saves.dolphinSave("Wii/title/00010000/53434745/data/progress.sav"))
        assertFalse(Saves.dolphinSave("Wii/title/00000001/00000002/data/setting.txt"))  // the console's own
        assertTrue(Saves.dolphinSave("Wii/shared2/menu/FaceLib/RFL_DB.dat"))
        assertFalse(Saves.dolphinSave("Config/Dolphin.ini"))
    }

    @Test fun savesGoToTheCardWithTheirEarlierVersions() {
        val dir = Files.createTempDirectory("saves").toFile()
        val device = File(dir, "RetroArch"); val card = File(dir, "card")
        val home = FileHome("RetroArch", device, listOf("saves", "states"))
        fun put(rel: String, text: String, time: Long) = File(device, rel).apply { parentFile!!.mkdirs(); writeText(text); setLastModified(time) }
        val day = 86_400_000L; val t0 = at(2030, 1, 1)
        val srm = "saves/Gambatte/Orchard.srm"
        put(srm, "first", t0); put("saves/Gambatte/Orchard.rtc", "clock000", t0); put("states/Gambatte/Orchard.state.auto", "place-one", t0)
        File(device, "saves/Gambatte/Orchard.srm.part").writeText("half")              // a copy that was interrupted is never taken for a save
        assertEquals(3, SaveSync.backUp(home, card))
        assertEquals("first", File(card, "RetroArch/$srm").readText())
        assertEquals(t0, File(card, "RetroArch/$srm").lastModified())
        assertEquals(0, SaveSync.backUp(home, card))                                   // nothing new: nothing copied
        assertFalse(File(card, "earlier").exists())

        // the save changes: the card takes the new one and keeps the one before it
        put(srm, "second", t0 + day)
        assertEquals(1, SaveSync.backUp(home, card))
        assertEquals("second", File(card, "RetroArch/$srm").readText())
        val owns = { rel: String -> Saves.belongs(rel.substringAfterLast('/'), "Orchard") }
        var versions = SaveSync.versions(card, "RetroArch", "saves/Gambatte", owns)
        assertEquals(listOf("first"), versions.map { it.file.readText() })
        assertEquals(srm, versions.single().rel)

        // written again with nothing changed in it: no copy, no new version
        File(device, srm).setLastModified(t0 + day + 60_000)
        assertEquals(0, SaveSync.backUp(home, card))
        assertEquals(1, SaveSync.versions(card, "RetroArch", "saves/Gambatte", owns).size)

        // many more sittings: the keeping rule holds
        for (i in 2..30) { put(srm, "save number $i", t0 + i * day); SaveSync.backUp(home, card) }
        versions = SaveSync.versions(card, "RetroArch", "saves/Gambatte", owns)
        assertEquals(Saves.MOST, versions.size)
        assertEquals("save number 29", versions.maxByOrNull { it.at }!!.file.readText())
        for (i in 2..6) { put("states/Gambatte/Orchard.state.auto", "place $i", t0 + i * day); SaveSync.backUp(home, card) }
        assertEquals(Saves.PLACES, SaveSync.versions(card, "RetroArch", "states/Gambatte", owns).size)

        // an earlier save is put back: the one in use is kept first, and the card follows
        val wanted = versions.first { it.file.readText() == "save number 25" }
        assertTrue(SaveSync.putBack(home, card, wanted))
        assertEquals("save number 25", File(device, srm).readText())
        SaveSync.backUp(home, card)
        assertEquals("save number 25", File(card, "RetroArch/$srm").readText())
        assertTrue(SaveSync.versions(card, "RetroArch", "saves/Gambatte", owns).any { it.file.readText() == "save number 30" })

        // a wiped device: everything comes back, with its times
        device.deleteRecursively()
        assertEquals(3, SaveSync.restore(home, card))
        assertEquals("save number 25", File(device, srm).readText())
        assertEquals(File(card, "RetroArch/$srm").lastModified(), File(device, srm).lastModified())
        assertEquals(0, SaveSync.restore(home, card))
        // a newer save on the device is never replaced by the card's
        put(srm, "newer here", t0 + 60 * day)
        assertEquals(0, SaveSync.restore(home, card))
        assertEquals("newer here", File(device, srm).readText())
        // an older one is, and it is kept as an earlier version first
        put(srm, "older here", t0 - day)
        assertEquals(1, SaveSync.restore(home, card))
        assertEquals("save number 25", File(device, srm).readText())
        assertTrue(SaveSync.versions(card, "RetroArch", "saves/Gambatte", owns).any { it.file.readText() == "older here" })
        dir.deleteRecursively()
    }

    @Test fun aProperExitIsReadFromRetroArchsLog() {
        val running = "[Content] Loading content file: \"/x/a.sfc\".\n[Core] Geometry: 256x224\n"
        assertFalse(RetroLog.closed(running))
        // a game closed from RetroArch's own menu and another loaded: RetroArch is still up
        assertFalse(RetroLog.closed(running + "[Core] Unloading game...\n[Core] Unloading core...\n" + running))
        assertTrue(RetroLog.closed(running + "[State] Auto save state to \"/x/a.state.auto\" succeeded.\n[SRAM] Saved successfully to \"/x/a.srm\".\n[Core] Unloading game...\n[Core] Unloading core...\n[Core] Unloading core symbols...\n"))
    }

    @Test fun aDamagedSettingsFileIsStartedAfresh() {
        // every setting stands once, whatever the file held
        assertEquals("a = \"1\"\nkeep = \"x\"\nb = \"2\"\n", RetroPack.merge("a = \"9\"\nkeep = \"x\"\na = \"7\"\n", linkedMapOf("a" to "1", "b" to "2")))
        // a zero byte: RetroArch would stop reading there, so nothing of that file is kept
        assertEquals("a = \"1\"\n", RetroPack.merge("junk\u0000more = \"x\"\nkeep = \"y\"\n", mapOf("a" to "1")))
        // a file merely cut short is still a settings file
        assertEquals("keep = \"y\"\nhalf = \"\na = \"1\"\n", RetroPack.merge("keep = \"y\"\nhalf = \"", mapOf("a" to "1")))
        assertEquals("Today 03:04:05", Saves.day(at(2030, 1, 13, 3, 4, 5), at(2030, 1, 13, 20, 0), utc, seconds = true))
    }

    @Test fun aGameDolphinWarnsAboutAtEveryStartIsNotStoppedByTheWarning() {
        for (pad in listOf("Android/1/Retroid Pocket Controller", null)) {
            val ssx = DolphinPack.docs(Look(), pad).first { it.path == "GameSettings/GSTE69.ini" }
            assertEquals("False", ssx.set.getValue("Dolphin.Interface").getValue("UsePanicHandlers"))
            assertNull(ssx.text)                                                       // merged into the game's own file, never replacing it
        }
        assertEquals("[Dolphin.Interface]\nUsePanicHandlers = False\n", Ini.merge("", mapOf("Dolphin.Interface" to mapOf("UsePanicHandlers" to "False"))))
    }

    @Test fun retroArchsFoldersAreSaidOutright() {
        val base = RetroPack.base(Look(), RetroPack.Paths("/h", "pkg", "/d", "/a"))
        assertEquals("/storage/emulated/0/RetroArch/saves", base["savefile_directory"])
        assertEquals("/storage/emulated/0/RetroArch/states", base["savestate_directory"])
        assertEquals("/storage/emulated/0/RetroArch/system", base["system_directory"])
    }

    @Test fun puttingASaveBackNeverLosesAVersion() {
        val dir = Files.createTempDirectory("putback").toFile()
        val device = File(dir, "RetroArch"); val card = File(dir, "card")
        val home = FileHome("RetroArch", device, listOf("saves"))
        val srm = "saves/Snes9x/Candlewick Rising (USA).srm"
        fun put(text: String, time: Long) = File(device, srm).apply { parentFile!!.mkdirs(); writeText(text); setLastModified(time) }
        fun onRecord() = SaveSync.versions(card, "RetroArch", "saves/Snes9x") { true }.map { it.file.readText() }.toSet()
        // from eight in the morning by the clock of wherever this runs: the keeping rule counts days as the player sees them
        val t0 = java.util.Calendar.getInstance().apply { clear(); set(2026, 9, 1, 8, 0, 0) }.timeInMillis
        // twenty-five sittings in one day: the six newest earlier saves are what is kept
        for (i in 0..25) { put("save $i", t0 + i * 20 * 60_000L); SaveSync.backUp(home, card) }
        assertEquals((19..24).map { "save $it" }.toSet(), onRecord())
        // the oldest of them is put back. Filing the save in use makes seven, and the keeping rule would let the oldest
        // go at that very moment: the one being put back. It must still be there to be read.
        val oldest = SaveSync.versions(card, "RetroArch", "saves/Snes9x") { true }.minByOrNull { it.at }!!
        assertEquals("save 19", oldest.file.readText())
        assertTrue(SaveSync.putBack(home, card, oldest))
        assertEquals("save 19", File(device, srm).readText())
        assertTrue("save 25" in onRecord())
        SaveSync.backUp(home, card)
        assertEquals("save 19", File(card, "RetroArch/$srm").readText())
        // back and forth between the two a few times: no copies pile up, and the saves in between are not pushed out
        repeat(3) {
            for (want in listOf("save 25", "save 19")) {
                val v = SaveSync.versions(card, "RetroArch", "saves/Snes9x") { true }.first { it.file.readText() == want }
                assertTrue(SaveSync.putBack(home, card, v))
                assertEquals(want, File(device, srm).readText())
                SaveSync.backUp(home, card)
            }
        }
        assertTrue(onRecord().containsAll((20..24).map { "save $it" }))
        assertEquals(6, SaveSync.versions(card, "RetroArch", "saves/Snes9x") { true }.size)
        // a save that was in use again is on record under the last time it was written, not the first time it was kept
        put("save 21", t0 + 40 * 20 * 60_000L); SaveSync.backUp(home, card)          // the game wrote an old save's contents again, later
        put("save 26", t0 + 41 * 20 * 60_000L); SaveSync.backUp(home, card)
        val again = SaveSync.versions(card, "RetroArch", "saves/Snes9x") { true }.filter { it.file.readText() == "save 21" }
        assertEquals(1, again.size)
        assertEquals(t0 + 40 * 20 * 60_000L, again.single().at)
        // a version that has gone missing is not put back, and the save in use is left exactly as it is
        val gone = SaveSync.Version(srm, File(dir, "no such file"), t0, t0)
        val inUse = File(device, srm).readText()
        assertFalse(SaveSync.putBack(home, card, gone))
        assertEquals(inUse, File(device, srm).readText())
        dir.deleteRecursively()
    }

    @Test fun savesAreNeverBroughtBackOverSomethingNotAccountedFor() {
        val dir = Files.createTempDirectory("restore").toFile()
        val device = File(dir, "RetroArch"); val card = File(dir, "card")
        val real = FileHome("RetroArch", device, listOf("saves"))
        val a = "saves/mGBA/A.srm"; val b = "saves/mGBA/B.srm"
        fun put(rel: String, text: String, time: Long) = File(device, rel).apply { parentFile!!.mkdirs(); writeText(text); setLastModified(time) }
        val t0 = at(2030, 1, 1)
        put(a, "a on the card", t0); put(b, "b on the card", t0)
        assertEquals(2, SaveSync.backUp(real, card))
        // the device now holds older saves; one of them does not show in the emulator's listing (it could not all be read)
        put(a, "a here, older", t0 - 86_400_000); put(b, "b here, older", t0 - 86_400_000)
        val partial = object : SaveHome by real { override fun list(): List<SaveEntry>? = real.list()?.filter { it.rel != b } }
        assertEquals(1, SaveSync.restore(partial, card))
        assertEquals("a on the card", File(device, a).readText())
        assertEquals("b here, older", File(device, b).readText())                       // not in the listing: not touched
        // a folder that could not be read at all is not an empty one
        val unread = object : SaveHome by real { override fun list(): List<SaveEntry>? = null }
        assertEquals(0, SaveSync.restore(unread, card))
        assertEquals(0, SaveSync.backUp(unread, card))
        // a file written between the listing and the copy (a game has just saved) is left alone
        val racing = object : SaveHome by real {
            override fun list(): List<SaveEntry>? = real.list().also { put(b, "b saved this second", t0 - 80_000_000) }
        }
        assertEquals(0, SaveSync.restore(racing, card))
        assertEquals("b saved this second", File(device, b).readText())
        // a file that was listed and cannot be looked at again a moment later has not gone: it is out of reach, and is left alone
        put(a, "a here, older again", t0 - 86_400_000)
        val blind = object : SaveHome by real { override fun stat(rel: String): SaveEntry? = null }
        assertEquals(0, SaveSync.restore(blind, card))
        assertEquals("a here, older again", File(device, a).readText())
        val onCard = SaveSync.Version(a, File(card, "RetroArch/$a"), t0, t0)
        assertFalse(SaveSync.putBack(blind, card, onCard, inUse = true))
        assertEquals("a here, older again", File(device, a).readText())
        // a save here that is older than the card's yet different (the clock was put back) goes on record, without replacing the card's
        put(a, "a made with the clock wrong", t0 - 3_600_000)
        assertEquals(0, SaveSync.backUp(real, card))
        assertEquals("a on the card", File(card, "RetroArch/$a").readText())
        assertTrue(SaveSync.versions(card, "RetroArch", "saves/mGBA") { it == a }.any { it.file.readText() == "a made with the clock wrong" })
        dir.deleteRecursively()
    }

    @Test fun whenFileTimesAreLostEachVersionLastsUntilTheNext() {
        val dir = Files.createTempDirectory("until").toFile()
        val earlier = File(dir, "earlier/Dolphin/GC/USA/Card A").apply { mkdirs() }
        val name = "00-GTKE-TandemKarts Twin Turbo!!.gci"
        val times = listOf(at(2030, 1, 1), at(2030, 1, 3), at(2030, 1, 7))
        val copied = at(2026, 11, 1)                                                  // the day the whole card was copied to a new one
        for (t in times) File(earlier, Saves.versionName(name, t)).apply { writeText("v$t"); setLastModified(copied) }
        val seen = SaveSync.versions(dir, "Dolphin", "") { true }.sortedBy { it.at }
        assertEquals(times, seen.map { it.at })
        assertEquals(listOf(times[1], times[2], Long.MAX_VALUE), seen.map { it.until })
        // versions whose names say until when they were in use need no file time at all
        val named = File(dir, "earlier/RetroArch/saves/Gambatte").apply { mkdirs() }
        File(named, Saves.versionName("Orchard.rtc", times[0], times[1])).apply { writeText("clock"); setLastModified(copied) }
        val clock = SaveSync.versions(dir, "RetroArch", "") { true }.single()
        assertEquals(times[0] / 1000, clock.at / 1000)
        assertEquals(times[1] / 1000, clock.until / 1000)
        // with their times intact they are believed
        File(earlier, Saves.versionName(name, times[0])).setLastModified(times[0] + 3_600_000)
        File(earlier, Saves.versionName(name, times[1])).setLastModified(times[1] + 7_200_000)
        File(earlier, Saves.versionName(name, times[2])).setLastModified(times[2] + 60_000)
        assertEquals(listOf(times[0] + 3_600_000, times[1] + 7_200_000, times[2] + 60_000), SaveSync.versions(dir, "Dolphin", "") { true }.sortedBy { it.at }.map { it.until })
        dir.deleteRecursively()
    }

    @Test fun everyGameHasItsOwnMemoryCard() {
        // the Dreamcast core names the card after the disc's product number, which it says as the game boots
        val log = "[Content] Loading content file: \"/x/dreamcast/Fare Rush (USA).chd\".\n[BOOT] Game ID is [MK-00000]\n[BOOT] Game ID is [MK-00000]\n"
        assertEquals("MK-00000", Saves.discId(log, "Fare Rush (USA).chd"))
        assertEquals("T0000N", Saves.discId("Loading content file: \"/x/Harrow House - Code - Nightfall (USA).m3u\".\n[BOOT] Game ID is [T0000N]", "Harrow House - Code - Nightfall (USA).m3u"))
        assertNull(Saves.discId("[Core] Geometry: 640x480", "Fare Rush (USA).chd"))
        // the log left behind by the game before this one says nothing about this one
        assertNull(Saves.discId(log, "Bladecrest (USA).chd"))
        assertNull(Saves.discId("[BOOT] Game ID is [MK-00000]\nLoading content file: \"/x/Bladecrest (USA).chd\".\n", "Bladecrest (USA).chd"))
        assertTrue(Saves.belongs("MK-00000.A1.bin", "MK-00000"))
        val files = RetroPack.files(Look(), RetroPack.Paths("/h", "pkg", "/d", "/a"))
        assertTrue(files.getValue("Flycast/Flycast.opt").contains("reicast_per_content_vmus = \"VMU A1\""))
        assertTrue(files.getValue("Genesis Plus GX/Genesis Plus GX.opt").contains("genesis_plus_gx_system_bram = \"per game\""))
    }

    @Test fun retroidsTintIsReadAndSetPlainly() {
        // The factory value is the pale blue the firmware ships: red 0xBC, green 0xE1, blue 0xFF.
        assertEquals(0xFFBCE1FF.toInt(), DisplayCalibration.FACTORY_TINT)
        assertEquals(-4398593, DisplayCalibration.tintIn("-4398593\n"))
        assertEquals(-1, DisplayCalibration.tintIn("result: 0\n-1\n"))
        assertNull(DisplayCalibration.tintIn("null\n")); assertNull(DisplayCalibration.tintIn(null))
        val off = DisplayCalibration.tintCommands(DisplayCalibration.TINT_OFF)
        assertEquals(listOf("settings put secure customer_display_color_temperature -1", "service call color_display 17 i32 -1"), off)
        // Each is a single plain command: the maker's service runs only the first part of a chain.
        assertTrue((off + DisplayCalibration.tintCommands(DisplayCalibration.FACTORY_TINT)).none { ";" in it || "&&" in it || "|" in it })
    }

    @Test fun theDisplayCalibrationIsRebuiltExactlyOrNotAtAll() {
        val patch = File("src/main/assets/${DisplayCalibration.PATCH_ASSET}").readBytes()
        assertEquals(DisplayCalibration.PATCH_SHA256, DisplayCalibration.sha256(patch))
        // Anything but the Nova's own factory file is refused.
        assertNull(DisplayCalibration.rebuild("{}".toByteArray(), patch))
        // The maker's service may answer with more than the command printed.
        val h = DisplayCalibration.CALIBRATED_SHA256
        assertEquals(h, DisplayCalibration.fingerprintIn("$h  /data/vendor/display/x.json\n"))
        assertEquals(h, DisplayCalibration.fingerprintIn("result: 0\n$h  /data/vendor/display/x.json"))
        assertEquals("", DisplayCalibration.fingerprintIn("sha256sum: /data/vendor/display/x.json: No such file or directory"))
        assertEquals("1.1", DisplayCalibration.decimalIn("1.1\n")); assertEquals("1.0", DisplayCalibration.decimalIn("ok\n1.0"))
        // The colors around it: only Natural or Boosted, and only the two saturations Android uses for them.
        assertNotNull(DisplayCalibration.colorCommands(0, "1.0")); assertNotNull(DisplayCalibration.colorCommands(1, "1.1"))
        assertNull(DisplayCalibration.colorCommands(0, "0.0")); assertNull(DisplayCalibration.colorCommands(5, "1.0"))
        assertNull(DisplayCalibration.installCommands("/sdcard/x.json")); assertNull(DisplayCalibration.installCommands("/data/user/0/market.nostos.timeline/../x"))
        // With the factory file read off a Nova (kept out of the project, it is Retroid's), the result is the author's to the byte.
        val factory = System.getenv("NOVA_QDCM_FACTORY")?.let(::File)?.takeIf { it.isFile } ?: return
        val made = DisplayCalibration.rebuild(factory.readBytes(), patch)
        assertNotNull(made)
        assertEquals(DisplayCalibration.CALIBRATED_SHA256, DisplayCalibration.sha256(made!!))
        assertNull(DisplayCalibration.rebuild(factory.readBytes(), patch.copyOf(patch.size - 1)))   // a damaged patch
    }

    @Test fun aDsSaveGoesWhereItWasPlayedOnNotWhereTheClockSays() {
        val day = 86_400_000L
        // A game played on in melonDS since the two were last the same: brought to RetroArch even with melonDS's file
        // stamped a day earlier (a clock set back).
        assertTrue(DsSaves.bring(from = "524288:b", to = "524288:a", last = "524288:a", fromTime = 0L, toTime = day))
        // Only RetroArch's has moved on: melonDS's older save is not brought over it, even stamped later.
        assertFalse(DsSaves.bring(from = "524288:a", to = "524288:b", last = "524288:a", fromTime = day, toTime = 0L))
        // Nothing there yet: brought. Nothing to bring, or the same bytes: left alone.
        assertTrue(DsSaves.bring("8192:x", "", "", 0L, day))
        assertFalse(DsSaves.bring("", "8192:x", "", day, 0L))
        assertFalse(DsSaves.bring("8192:x", "8192:x", "", day, 0L))
        // Both moved on, or never compared: the newer by the clock, and the player is told when both had moved.
        assertTrue(DsSaves.bring("1:c", "1:b", "1:a", day, 0L))
        assertFalse(DsSaves.bring("1:c", "1:b", "1:a", 0L, day))
        assertTrue(DsSaves.bothMoved("1:c", "1:b", "1:a"))
        assertFalse(DsSaves.bothMoved("1:c", "1:a", "1:a"))
        assertFalse(DsSaves.bring("1:c", "1:b", "", 1000L, 0L))       // within the 2.5 seconds a card's clock can be out by
    }

    @Test fun batteryTimeLeftFollowsThePaceOffTheCharger() {
        val B = BatteryEstimate
        val min = 60_000L
        var kept = emptyList<BatteryEstimate.Sample>()
        // ten minutes, 2 % down: too soon to say
        kept = B.add(kept, BatteryEstimate.Sample(1, 0, 90), false)
        kept = B.add(kept, BatteryEstimate.Sample(1, 10 * min, 88), false)
        assertNull(B.hoursLeft(kept))
        assertEquals("88%", B.text(88, false, B.hoursLeft(kept)))
        // an hour awake, 10 % down: 80 % lasts eight hours at that pace
        kept = B.add(kept, BatteryEstimate.Sample(1, 60 * min, 80), false)
        assertEquals(8f, B.hoursLeft(kept)!!, 0.01f)
        assertEquals("80% / 8.0 h", B.text(80, false, B.hoursLeft(kept)))
        // the charger starts it again, and so does a new boot or a level that went up out of sight
        assertTrue(B.add(kept, BatteryEstimate.Sample(1, 61 * min, 80), true).isEmpty())
        assertEquals(1, B.add(kept, BatteryEstimate.Sample(2, 1 * min, 79), false).size)
        assertEquals(1, B.add(kept, BatteryEstimate.Sample(1, 70 * min, 95), false).size)
        // only the last hour and a half counts, anchored by the sample just before it
        val long = B.add(kept, BatteryEstimate.Sample(1, 200 * min, 40), false)
        assertEquals(60 * min, long.first().awakeMs)
        assertEquals("88% / charging", B.text(88, true, null))
        assertEquals("30% / 45 min", B.text(30, false, 0.75f))
        assertEquals("100% / 12 h", B.text(100, false, 12.4f))
        assertEquals(kept, B.decode(B.encode(kept)))
    }

    @Test fun stickLightsAreWrittenToTheirDriversInTheMakersShape() {
        assertEquals("1-254:189:31", Vendor.driverColor(0xFFFEBD1F.toInt(), 100))
        assertEquals("1-127:94:15", Vendor.driverColor(0xFFFEBD1F.toInt(), 50))
        assertEquals("1-12:9:1", Vendor.driverColor(0xFFFEBD1F.toInt(), 1))           // never below 5 %
        assertEquals("echo 1-254:189:31 > /sys/class/sn3112l/led/brightness", Vendor.write("/sys/class/sn3112l/led", "brightness", "1-254:189:31"))
        assertEquals("echo 0 > /sys/class/sn3112r/led/enable", Vendor.write("/sys/class/sn3112r/led", "enable", "0"))
        // nothing else, anywhere else, in any other shape
        assertNull(Vendor.write("/sys/class/sn3112l/led", "breath", "1"))
        assertNull(Vendor.write("/sys/class/other/led", "enable", "1"))
        assertNull(Vendor.write("/sys/class/sn3112l/led", "enable", "1; reboot"))
        assertNull(Vendor.write("/sys/class/sn3112l/led", "brightness", "1-1:1:1 > /x"))
    }

    @Test fun eachSystemGetsTheControlsAndFixesItsGamesNeed() {
        val p = RetroPack.Paths("/h", "pkg", "/d", "/a")
        val ladx = "Legend of Zelda, The - Link's Awakening DX (USA, Europe) (Rev 2) (SGB Enhanced) (GB Compatible)"
        val files = RetroPack.files(Look(), p, handheld = listOf("gbc" to ladx, "gbc" to "Lantern Keep (USA)", "gbc" to "Pokemon - Crystal Version (USA, Europe) (Rev 1)"))
        // PlayStation: a DualShock in every port, each core by its own number, digital at power-on with the stick on the d-pad
        assertEquals((1..4).joinToString("") { "input_libretro_device_p$it = \"261\"\n" }, files.getValue("remaps/SwanStation/SwanStation.rmp"))
        assertTrue(files.getValue("remaps/Beetle PSX HW/Beetle PSX HW.rmp").contains("input_libretro_device_p1 = \"517\""))
        assertTrue(files.getValue("SwanStation/SwanStation.opt").contains("swanstation_Controller1_AnalogDPadInDigitalMode = \"true\""))
        assertTrue(files.getValue("Beetle PSX HW/Beetle PSX HW.opt").contains("beetle_psx_hw_analog_toggle = \"enabled\""))
        // Link's Awakening: START and SELECT twice, so its save screen (A, B, START and SELECT) opens; the Pokémon games
        // too, which reset with A, B, START and SELECT; no other game
        assertEquals("quit_press_twice = \"true\"\n", files.getValue("Gambatte/$ladx.cfg"))
        assertEquals("quit_press_twice = \"true\"\n", files.getValue("mGBA/$ladx.cfg"))
        assertNull(files["Gambatte/Lantern Keep (USA).cfg"])
        assertEquals("quit_press_twice = \"true\"\n", files["Gambatte/Pokemon - Crystal Version (USA, Europe) (Rev 1).cfg"])
        assertTrue(ControlsNote.of("gbc", Titles.base("$ladx.gbc"), "ra:gambatte", true, null).contains("START and SELECT pressed twice leave and keep your place."))
        assertTrue(ControlsNote.of("gbc", "lantern keep", "ra:gambatte", true, null).contains("START and SELECT together leave"))
        assertTrue(ControlsNote.of("gbc", "pokemon - crystal version", "ra:gambatte", true, null).contains("START and SELECT pressed twice leave"))
        assertTrue(ControlsNote.of("gbc", "pokemon pinball", "ra:gambatte", true, null).contains("START and SELECT together leave"))
        // the original Game Boy's screen runs a Game Boy game as a Game Boy, even one that colors itself on a Color
        val lcd = RetroPack.files(Look(handheld = RetroPack.TRUE_LCD), p)
        assertTrue(lcd.getValue("Gambatte/gb.opt").contains("gambatte_gb_hwmode = \"GB\""))
        assertTrue(lcd.getValue("Gambatte/gbc.opt").contains("gambatte_gb_hwmode = \"Auto\""))
        assertTrue(RetroPack.files(Look(handheld = "LCD3x"), p).getValue("Gambatte/gb.opt").contains("gambatte_gb_hwmode = \"Auto\""))
        // the DS's sound as quick as the other systems'; Paper Mario's intro
        assertTrue(files.getValue("melonDS DS/melonDS DS.cfg").contains("audio_latency = \"48\""))
        assertTrue(files.getValue("Mupen64Plus-Next/Paper Mario (USA).opt").contains("mupen64plus-EnableCopyAuxToRDRAM = \"True\""))
        // upright arcade games stand level on a television, with their usual controls
        val turned = RetroPack.files(Look(), p, arcade = listOf("galaga"))
        assertNotNull(turned["remaps/FinalBurn Neo/galaga.rmp"])
        assertTrue(turned.getValue("FinalBurn Neo/FinalBurn Neo.opt").contains("fbneo-vertical-mode = \"TATE alternate\""))
        val docked = RetroPack.files(Look(tv = true), p, arcade = listOf("galaga"))
        assertNull(docked["remaps/FinalBurn Neo/galaga.rmp"])
        assertTrue(docked.getValue("FinalBurn Neo/FinalBurn Neo.opt").contains("fbneo-vertical-mode = \"disabled\""))
        assertTrue(docked.getValue("MAME 2003-Plus/MAME 2003-Plus.opt").contains("mame2003-plus_tate_mode = \"disabled\""))
    }

    @Test fun nintendo64AAndBAreTheButtonsMarkedAAndB() {
        val files = RetroPack.files(Look(), RetroPack.Paths("/h", "pkg", "/d", "/a"))
        val remap = files.getValue("remaps/Mupen64Plus-Next/Mupen64Plus-Next.rmp")
        // player 2's Xbox controller: the core's own layout, which matches its labels, said outright over an older file
        for (line in listOf("input_player2_btn_a = \"8\"", "input_player2_btn_b = \"0\"", "input_player2_btn_y = \"1\"")) assertTrue(line, remap.contains(line))
        for (p in listOf(1, 3, 4)) {
            assertTrue(remap.contains("input_player${p}_btn_a = \"0\""))    // A sends what the core reads as the N64's A
            assertTrue(remap.contains("input_player${p}_btn_b = \"1\""))    // B, its B
            assertTrue(remap.contains("input_player${p}_btn_y = \"8\""))
        }
        // R2's C buttons stay where they were: right, bottom, left and top face buttons
        val opt = files.getValue("Mupen64Plus-Next/Mupen64Plus-Next.opt")
        for (o in listOf("r-cbutton = \"C3\"", "d-cbutton = \"C2\"", "l-cbutton = \"C1\"", "u-cbutton = \"C4\"")) assertTrue(opt, opt.contains("mupen64plus-$o"))
        // and a game run lighter keeps them too
        val light = RetroPack.files(Look(lighter = mapOf("n64/Breakwater Rally 64 (USA)" to true)), RetroPack.Paths("/h", "pkg", "/d", "/a"))
        assertTrue(light.getValue("Mupen64Plus-Next/Breakwater Rally 64 (USA).opt").contains("mupen64plus-r-cbutton = \"C3\""))
    }

    @Test fun retroArchSaysNothingAsAGameStarts() {
        val base = RetroPack.base(Look(), RetroPack.Paths("/h", "pkg", "/d", "/a"))
        for (k in listOf("notification_show_autoconfig", "notification_show_config_override_load", "notification_show_remap_load",
                "notification_show_set_initial_disk", "notification_show_save_state", "cheevos_visibility_account")) assertEquals(k, "false", base[k])
        assertEquals("2", base["cheevos_visibility_summary"])
        assertNull(base["notification_show_disk_control"])          // a disc change is still said
        assertNull(base["cheevos_visibility_unlock"])               // and an achievement unlocked
    }

    @Test fun theThreeDConsolesGetTheTelevisionConsolesCrtAtTheirOwnLines() {
        // the collection's GDV, as far as the changes go: the struct, the parameter lines, the decision, the centring and the glow
        val gdv = "#version 450\nlayout(push_constant) uniform Push\n{\n\tfloat sat;\n\tfloat thres;\n} params;\n" +
            "// Parameter lines go here:\n#pragma parameter sat \"x\" 1.2 0.0 2.0 0.05\nvoid main(){ vec4 SourceSize = global.SourceSize;\n" +
            "\tvec2 OGL2Pos = pos * SourceSize.xy;\n\tif (OriginalSize.y >= 400.0) {color = (color1 + color2)/2.0;}\n\tcolor += glow0(pC4,color);\n}\n"
        val made = Crt3d.slang(gdv)!!
        assertTrue(made.contains("float gdv_hires;"))
        assertTrue(made.contains("#define gdv_hires params.gdv_hires"))
        assertTrue(made.contains("if (gdv_hires > 0.0 && global.SourceSize.y >= gdv_hires)"))
        assertFalse(made.contains("OriginalSize.y >= 400.0"))
        assertTrue(made.contains("vec2 OGL2Pos = pos * SourceSize.xy - vec2(0.5, global.OutputSize.y * SourceSize.w < 3.0 ? 0.25 : 0.5);") && made.contains("if (glow > 0.0) color += glow0(pC4,color);"))
        assertNull(Crt3d.slang(gdv.replace("OriginalSize.y >= 400.0", "OriginalSize.y > 400.0")))   // not the file it was written for
        assertNull(Crt3d.slang(gdv.replace("vec2 OGL2Pos = pos * SourceSize.xy;", "vec2 OGL2Pos = pos;")))
        val p = RetroPack.Paths("/h", "pkg", "/d", "/a")
        val off = Look(motion3d = "Off", sharp = false)
        val files = RetroPack.files(off, p)
        // the true picture: PlayStation and Dreamcast at their own size, nothing to bring down; composite, the set's
        // colour, then GDV, with 480-line pictures drawn without gaps
        val psx = files.getValue("SwanStation/SwanStation.slangp")
        assertTrue(psx.contains("shaders = \"3\"") && psx.contains("shader0 = \"/h/shaders/timeline/timeline-signal.slang\"") && psx.contains("tl_signal = \"2.0\"") && psx.contains("tl_width = \"320.0\""))
        assertTrue(psx.contains("shader1 = \"/h/shaders/timeline/timeline-colour.slang\"") && psx.contains("shader2 = \"/h/shaders/timeline/crt-gdv-3d.slang\"") && psx.contains("gdv_hires = \"400.0\""))
        // Dreamcast through its VGA box: no television signal, and each of its 480 lines drawn crisply
        val dc = files.getValue("Flycast/Flycast.slangp")
        assertTrue(dc.contains("shaders = \"2\"") && !dc.contains("timeline-signal") && dc.contains("gdv_hires = \"0.0\""))
        // ...on a PC monitor of its day, kept bright: lines plain in dark parts and blooming shut in bright ones, the
        // shadows lifted back to the panel's, and the player's white
        assertTrue(dc.contains("scanline = \"6.0\"") && dc.contains("gamma_out_green = \"2.6\"") && dc.contains("tl_white = \"2.0\""))
        assertTrue(psx.contains("scanline = \"9.0\"") && psx.contains("gamma_out_green = \"2.6\""))      // a television, at its own light
        assertTrue(files.getValue("Flycast/Flycast.opt").contains("reicast_cable_type = \"VGA\""))
        val tvdc = RetroPack.files(off.copy(signal = mapOf("dreamcast" to "Composite")), p)
        assertTrue(tvdc.getValue("Flycast/Flycast.slangp").let { it.contains("shaders = \"3\"") && it.contains("tl_width = \"320.0\"") && it.contains("gdv_hires = \"400.0\"") })
        assertTrue(tvdc.getValue("Flycast/Flycast.opt").contains("reicast_cable_type = \"TV (Composite)\""))
        val saturn = files.getValue("Beetle Saturn/Beetle Saturn.slangp")
        assertTrue(saturn.contains("shaders = \"6\"") && saturn.contains("alias0 = \"PrePass0\"") && saturn.contains("ntsc-pass3.slang") && saturn.contains("cust_artifacting = \"1.0\"") && saturn.contains("auto_res = \"1.0\""))
        // Nintendo 64: eight times its detail in both looks, brought back to 320 x 240 both ways (the GLSL passes, under OpenGL)
        val n64 = files.getValue("Mupen64Plus-Next/Mupen64Plus-Next.glslp")
        assertTrue(n64.contains("shaders = \"6\"") && n64.contains("shader0 = \"/h/shaders/timeline/stock.glsl\"") && n64.contains("scale_x0 = \"0.5\"") && n64.contains("scale_y2 = \"0.5\"") &&
            n64.contains("shader3 = \"/h/shaders/timeline/timeline-signal.glsl\"") && n64.contains("shader4 = \"/h/shaders/timeline/timeline-colour.glsl\"") &&
            n64.contains("shader5 = \"/h/shaders/timeline/crt-gdv-3d.glsl\"") && n64.contains("gamma_out = \"2.6\""))
        // the sharper look: four times, PlayStation brought back to its lines only; 480 lines on a television still blend
        val sharp = RetroPack.files(off.copy(sharp = true), p)
        val spsx = sharp.getValue("SwanStation/SwanStation.slangp")
        assertTrue(spsx.contains("shaders = \"5\"") && spsx.contains("scale_x0 = \"1.0\"") && spsx.contains("scale_y1 = \"0.5\"") && spsx.contains("gdv_hires = \"400.0\""))
        assertTrue(RetroPack.files(off.copy(sharp = true, signal = mapOf("psx" to "RGB")), p).getValue("SwanStation/SwanStation.slangp").contains("gdv_hires = \"400.0\""))
        // Dreamcast at four times: two halvings of the rows (1920 to 480), one of the columns (2560 to 1280)
        assertTrue(sharp.getValue("Flycast/Flycast.slangp").let { it.contains("shaders = \"4\"") && it.contains("scale_x0 = \"0.5\"") && it.contains("scale_x1 = \"1.0\"") && it.contains("scale_y1 = \"0.5\"") })
        // drawn the compatible way it stays at twice, with its one halving
        val compat = RetroPack.files(off.copy(sharp = true, compatible = true), p)
        assertTrue(compat.getValue("Flycast/Flycast.opt").contains("reicast_internal_resolution = \"1280x960\"") && compat.getValue("Flycast/Flycast.glslp").contains("shaders = \"3\""))
        // PlayStation drawn in software: its own lines already
        assertTrue(RetroPack.files(off.copy(compatible = true, sharp = true), p).getValue("SwanStation/SwanStation.slangp").contains("shaders = \"3\""))
        // a game run lighter has its own preset without halvings: Nintendo 64 in either look, PlayStation in the sharper one
        assertTrue(RetroPack.files(off.copy(lighter = mapOf("n64/Bristle's Rough Night (USA)" to true)), p)
            .getValue("Mupen64Plus-Next/Bristle's Rough Night (USA).glslp").contains("shaders = \"3\""))
        val light = RetroPack.files(off.copy(sharp = true, lighter = mapOf("psx/Brawl Circuit 3 (USA)" to true)), p)
        assertTrue(light.getValue("SwanStation/Brawl Circuit 3 (USA).slangp").contains("shaders = \"3\""))
        assertNull(RetroPack.files(off.copy(lighter = mapOf("psx/Brawl Circuit 3 (USA)" to true)), p)["SwanStation/Brawl Circuit 3 (USA).slangp"])
        // RGB, or the Dreamcast's VGA box: no signal pass; VGA keeps a monitor's lines, and the console is told
        val rgb = RetroPack.files(off.copy(signal = mapOf("psx" to "RGB", "saturn" to "RGB", "dreamcast" to "VGA")), p)
        assertTrue(rgb.getValue("SwanStation/SwanStation.slangp").contains("shaders = \"2\"") && !rgb.getValue("SwanStation/SwanStation.slangp").contains("signal"))
        assertTrue(rgb.getValue("Beetle Saturn/Beetle Saturn.slangp").contains("shaders = \"2\""))
        assertTrue(rgb.getValue("Flycast/Flycast.slangp").contains("gdv_hires = \"0.0\"") && rgb.getValue("Flycast/Flycast.opt").contains("reicast_cable_type = \"VGA\""))
        // switched off, or the collection's GDV unusable: no Vulkan presets for these (the GLSL ones are the launcher's own)
        assertNull(RetroPack.files(off.copy(crt3d = false), p)["SwanStation/SwanStation.slangp"])
        assertNull(RetroPack.files(off.copy(crt3d = false), p)["Mupen64Plus-Next/Mupen64Plus-Next.glslp"])
        assertNull(RetroPack.files(off.copy(crt3dSlang = false), p)["SwanStation/SwanStation.slangp"])
        assertNotNull(RetroPack.files(Look(crt3dSlang = false), p)["Mupen64Plus-Next/Mupen64Plus-Next.glslp"])
        // the GLSL version carries the true picture's figures: GDV's beam, without its glow, boost, mask or vignette,
        // brighter lines wider, gamma out 2.3, and the beam centered on its line
        for (k in listOf("scanline 9.0", "beam_min 1.5", "beam_max 2.5", "h_sharp 2.5", "thres 0.4", "brightboost 1.0", "glow 0.0",
            "sat 1.0", "shadowMask -1.0", "vignette 0.0", "gamma_out 2.6")) assertTrue(k, Crt3d.glsl.contains("#define $k\n"))
        assertTrue(Crt3d.glsl.contains("TEX0.xy * TextureSize - vec2(0.5, OutputSize.y / InputSize.y < 3.0 ? 0.25 : 0.5)") && Crt3d.glsl.contains("if (glow > 0.0) color += glow0"))
        assertTrue(Crt3d.dolphin(0.25, 2, "2.0", 0.0).let { it.contains("SetOutput(") && it.contains("[configuration]") })
    }

    @Test fun eachConsoleReachesTheTubeByItsOwnSignal() {
        val p = RetroPack.Paths("/h", "pkg", "/d", "/a")
        val look = Look()
        // composite for the consoles whose art leaned on it, an arcade monitor's RGB, and from the Nintendo 64 on the best
        // connection each had
        for (g in listOf("nes", "snes", "genesis", "tg16", "atari2600", "psx", "saturn")) assertEquals(g, "Composite", look.signalOf(g))
        assertEquals("RGB", look.signalOf("arcade")); assertEquals("S-video", look.signalOf("n64")); assertEquals("VGA", look.signalOf("dreamcast"))
        assertEquals("Component", look.signalOf("gcwii")); assertEquals("Component", look.signalOf("ps2"))
        assertEquals("RGB", look.copy(signal = mapOf("genesis" to "RGB")).signalOf("genesis"))
        assertEquals("Composite", look.copy(signal = mapOf("genesis" to "Nonsense")).signalOf("genesis"))
        assertEquals("VGA", look.copy(signal = mapOf("dreamcast" to "RGB")).signalOf("dreamcast"))     // RGB was never an American Dreamcast's
        val files = RetroPack.files(look, p)
        // Genesis: its measured colour levels, the beam, the NTSC filter at its own width, the set's colour, then the tube
        val md = files.getValue("Genesis Plus GX/Genesis Plus GX.slangp")
        assertTrue(md.startsWith("shaders = \"8\"\n"))
        assertTrue(md.contains("shader0 = \"/h/shaders/timeline/timeline-genesis-dac.slang\""))
        assertTrue(md.contains("shader1 = \"${p.slang}/subframe-bfi/shaders/crt-beam-simulator.slang\""))
        for (line in listOf("shader2 = \"${p.slang}/stock.slang\"", "alias2 = \"PrePass0\"", "alias3 = \"NPass1\"",
            "scale_x3 = \"4.0\"", "scale_x4 = \"0.5\"", "float_framebuffer4 = \"true\"", "cust_artifacting = \"1.0\"", "cust_fringing = \"1.0\"",
            "shader6 = \"/h/shaders/timeline/timeline-colour.slang\"", "tl_white = \"2.0\"",
            "shader7 = \"/h/shaders/timeline/crt-gdv-3d.slang\"", "scale_type7 = \"viewport\"", "gdv_hires = \"400.0\"",
            "GAIN_VS_BLUR = \"0.5\"", "SCAN_DIRECTION = \"0.0\"", "warpY = \"0.0\"", "ntsc_rainbow1 = \"1.0\"",
            "glow = \"0.10\"", "brightboost = \"1.10\"", "sat = \"1.00\"", "beam_max = \"2.50\"", "shadowMask = \"1.0\"", "maskDark = \"0.5\"",
            "maskLight = \"1.5\"", "vignette = \"0.0\"", "gamma_out_red = \"2.6\""))
            assertTrue(line, md.contains(line))
        // parameters come after every pass
        assertTrue(md.indexOf("cust_artifacting") > md.indexOf("shader7"))
        // SNES: composite, its 3-phase timing, hi-res frames counted in its own pixels; TurboGrafx: its own timing too
        val sfc = files.getValue("Snes9x/Snes9x.slangp")
        assertTrue(sfc.contains("cust_artifacting = \"1.0\"") && sfc.contains("ntsc_phase = \"3.0\"") && sfc.contains("auto_res = \"1.0\""))
        assertFalse(sfc.contains("genesis-dac"))
        assertTrue(files.getValue("Beetle PCE/Beetle PCE.slangp").let { it.contains("ntsc_phase = \"5.0\"") && it.contains("auto_res = \"1.0\"") })
        assertTrue(files.getValue("Nestopia/Nestopia.slangp").contains("ntsc_phase = \"3.0\""))
        // Master System: its own chain, its static artifacts at the NES's pixel width, none of the Genesis's levels
        val sms = files.getValue("Genesis Plus GX/mastersystem.slangp")
        assertTrue(sms.contains("ntsc_phase = \"5.0\"") && sms.contains("ntsc_rainbow1 = \"1.0\"") && !sms.contains("genesis-dac"))
        // Atari: Stella's own television filter instead of guest's
        assertFalse(files.getValue("Stella/Stella.slangp").contains("ntsc"))
        assertTrue(files.getValue("Stella/Stella.opt").contains("stella_filter = \"composite\""))
        // RF, as most of these consoles came in the box
        assertTrue(RetroPack.files(look.copy(signal = mapOf("nes" to "RF")), p).getValue("Nestopia/Nestopia.slangp").contains("cust_artifacting = \"2.0\""))
        // arcade: RGB, no filter
        assertFalse(files.getValue("FinalBurn Neo/FinalBurn Neo.slangp").contains("ntsc"))
        // the screen Off: drawn clean, no signal, colour or levels either
        val clean = RetroPack.files(look.copy(crt = "Off"), p)
        assertFalse(clean.getValue("Genesis Plus GX/Genesis Plus GX.slangp").let { it.contains("ntsc") || it.contains("colour") || it.contains("genesis-dac") })
        assertNull(RetroPack.files(look.copy(crt = "Off", motion = "Off"), p)["Genesis Plus GX/Genesis Plus GX.slangp"])
        // without Timeline's own copy of GDV, the collection's, with the same settings
        assertTrue(RetroPack.files(look.copy(crt3dSlang = false), p).getValue("Snes9x/Snes9x.slangp").contains("${p.slang}/crt/shaders/crt-gdv-mini-ultra.slang"))
        // Timeline's own signal shader, in both languages, and its two values
        assertEquals("2.0", Signal.value("Composite")); assertEquals("1.0", Signal.value("S-video"))
        assertTrue(Signal.slang.contains("#pragma parameter tl_signal") && Signal.slang.contains("tl_width"))
        assertTrue(Signal.glsl.contains("#pragma parameter tl_signal") && Signal.glsl.contains("uniform float tl_signal, tl_width"))
        // the television's white: American by default, and each choice's value
        assertEquals("2.0", RetroPack.whiteValue(look)); assertEquals("0.0", RetroPack.whiteValue(look.copy(white = "Studio (6500 K)")))
        assertEquals("3.0", RetroPack.whiteValue(look.copy(white = "Japanese TV (9300 K)")))
        for (src in listOf(Colour.slang, Colour.glsl)) assertTrue(src.contains("W8000") && src.contains("tl_white") && !src.contains("\$"))
    }

    @Test fun dockedTheStripesWidenUnder4k() {
        // The dock scales the Nova's 960 rows to the television: under 1920 lines the phosphors are two columns wide
        assertEquals(1, RetroPack.maskStep(Look()))
        assertEquals(2, RetroPack.maskStep(Look(tv = true, tvLines = 1080)))
        assertEquals(2, RetroPack.maskStep(Look(tv = true, tvLines = 1440)))
        assertEquals(1, RetroPack.maskStep(Look(tv = true, tvLines = 2160)))
        val p = RetroPack.Paths("/h", "pkg", "/d", "/a")
        assertTrue(RetroPack.files(Look(), p).getValue("Snes9x/Snes9x.slangp").contains("masksize = \"1.0\""))
        val docked = RetroPack.files(Look(tv = true, tvLines = 1080, hz = 60), p)
        assertTrue(docked.getValue("Snes9x/Snes9x.slangp").contains("masksize = \"2.0\""))
        assertEquals(2, RetroPack.tvLook(Look(tv = true, tvLines = 1080), "gcwii").stripe)
        assertTrue(Crt3d.dolphin(1.0, 0, "2.0", 0.5, 2).contains("#define OPT_STRIPE 2.0"))
        assertTrue(Tv480.preset(1.0, 0, "2.0", 0.5, 2).contains("tv_stripe = \"2.0\""))
        assertTrue(Tv480.slang.contains("#pragma parameter tv_stripe") && Tv480.slang.contains("#define OPT_STRIPE params.tv_stripe"))
        // and at 60 Hz there is no beam or black frames for the television to run at half speed
        assertTrue(docked.getValue("Snes9x/Snes9x.cfg").contains("video_shader_subframes = \"1\"") && docked.getValue("Snes9x/Snes9x.cfg").contains("video_black_frame_insertion = \"0\""))
    }

    @Test fun aSittingThatStutteredIsToldFromTheScreensRecord() {
        val dump = "SurfaceFlinger TimeStats:\ntotalFrames = 3000\npresentToPresent histogram is as below:\n" +
            "0ms=0 8ms=2900 9ms=20 16ms=60 25ms=15 150ms=5\naverageFrameDuration = 1.2 ms\n"
        val h = Stutter.histogram(dump)
        assertEquals(2900, h[8]); assertEquals(5, h[150])
        val s = Stutter.of(h, 120, drawnOnly = false)!!
        assertEquals(3000, s.frames); assertEquals(80, s.late)                 // 16, 25 and 150 ms at 120 Hz
        assertTrue(s.percent > 2.6 && s.percent < 2.7)
        // Dolphin's 30-a-second game at 120: its own pace is not late, a long stall is
        val dol = Stutter.of(mapOf(33 to 1800, 41 to 100, 66 to 40, 100 to 10), 120, drawnOnly = true)!!
        assertEquals(10, dol.late)
        // too little to go by
        assertNull(Stutter.of(mapOf(8 to 500), 120, false))
        assertEquals(2.7, Stutter.percentOf(Stutter.format(Stutter.Sitting(3000, 80), 1L))!!, 0.001)
        assertNull(Stutter.percentOf(""))
        assertEquals(60, Stutter.histogram("0ms=0 8ms=2900 16ms=60\n")[16])          // the one line the device sends back
    }

    @Test fun gameCubeWiiAndPs2GetATelevisionsFourHundredEightyLines() {
        val dol = Crt3d.dolphin(0.25, 2, "2.0", 0.0)
        // GameCube and Wii send 480 lines: a 480-line raster, its beams and gaps in light, and the phosphor grille on the panel's columns
        assertTrue(dol.contains("RASTER = 480.0") && dol.contains("dFdy(GetCoordinates().y)") && dol.contains("float3 tv(") && dol.contains("whiteOf("))
        assertTrue(dol.contains("mod(floor(frag.x / OPT_STRIPE), 3.0)") && dol.contains("#define OPT_STRIPE 1.0"))
        assertTrue(dol.contains("OptionName = lines\nMinValue = 0.0\nMaxValue = 1.0\nStepAmount = 0.05\nDefaultValue = 0.25\n"))
        assertTrue(dol.contains("OptionName = mask\nMinValue = 0.0\nMaxValue = 0.8\n"))
        assertTrue(dol.contains("OptionName = mask") && dol.contains("OptionName = signal") && !dol.contains("\${"))
        assertFalse(dol.contains("mod(floor(gl_FragCoord.y), 4.0)") || dol.contains("lift"))
        // the same for PlayStation 2, as a slang shader and the preset ARMSX2 reads
        assertTrue(Tv480.slang.contains("params.OutputSize.y / 480.0") && Tv480.slang.contains("float3 tv(") && !Tv480.slang.contains("\$"))
        val pre = Tv480.preset(0.25, 2, "2.0", 0.0)
        for (line in listOf("shader0 = \"timeline-tv.slang\"", "scale_type0 = \"viewport\"", "tv_lines = \"0.25\"", "tv_signal = \"2.0\"", "tv_white = \"2.0\""))
            assertTrue(line, pre.contains(line))
        assertEquals("timeline-crt.slangp", Tv480.PRESET)
        // the settings each takes: component's full line structure, interlaced cables' partly filled gaps; the grille for all; the cable; the white
        assertEquals(RetroPack.TvLook(1.0, 0, "2.0", 0.5), RetroPack.tvLook(Look(), "ps2"))
        assertEquals(RetroPack.TvLook(0.6, 2, "2.0", 0.5), RetroPack.tvLook(Look(signal = mapOf("ps2" to "Composite")), "ps2"))
        assertEquals(RetroPack.TvLook(1.0, 0, "2.0", 0.5), RetroPack.tvLook(Look(), "gcwii"))
        assertEquals(RetroPack.TvLook(0.6, 1, "3.0", 0.5), RetroPack.tvLook(Look(white = "Japanese TV (9300 K)", signal = mapOf("gcwii" to "S-video")), "gcwii"))
    }

    @Test fun aFrozenDolphinIsToldApartFromAnIdleOne() {
        assertEquals("frozen", DolphinWatch.parse("frozen\n")); assertEquals("idle", DolphinWatch.parse("x\nidle"))
        assertNull(DolphinWatch.parse("")); assertNull(DolphinWatch.parse(null)); assertNull(DolphinWatch.parse("maybe"))
        val sh = DolphinWatch.script("org.dolphinemu.dolphinemu")
        assertTrue(sh.contains("pidof org.dolphinemu.dolphinemu") && sh.contains("\"NativeEmulation\"") && sh.contains("futex*") && sh.contains("echo frozen"))
        // frozen needs the whole process idle too (processor time and bytes written), read with the shell's own read
        assertTrue(sh.contains("\${14} + \${15}") && sh.contains("/proc/\$p/io") && sh.contains("wchar:") && sh.contains("-le 2") && sh.contains("read -r n <") && !sh.contains("cat "))
        // whether an emulator is running: its own processes too (hakuX's ":xemu"); only "yes" and "no" count
        assertTrue(Proc.script.contains("ps -A -o NAME=") && Proc.script.contains("\$1:") && Proc.script.contains("echo unknown") && Proc.script.contains("system_server"))
        // capitals in a package name (Cemu's) are allowed
        assertEquals("am force-stop info.cemu.Cemu", Vendor.forceStop("info.cemu.Cemu")); assertNull(Vendor.forceStop("x; rm -rf /"))
        assertEquals(true, Proc.parse("yes\n")); assertEquals(false, Proc.parse("no")); assertNull(Proc.parse("")); assertNull(Proc.parse(null)); assertNull(Proc.parse("unknown"))
        // an emulator counts as open only with a window: a leftover process with none holds no game
        assertTrue(Proc.windowScript.contains("dumpsys activity activities") && Proc.windowScript.contains("Hist +#[0-9]+: ActivityRecord\\{[^ ]+ u[0-9]+ \$1/") && Proc.windowScript.contains("echo unknown"))
        // the PS2 look: only its two files, copied when they differ and given to ARMSX2
        val ps2 = Ps2Look.script("com.armsx2", 10120, "/data/user/0/market.nostos.timeline/files/armsx2-shaders")
        assertTrue(ps2.contains("/data/media/0/Android/data/com.armsx2/files/shaders/timeline") && ps2.contains("chown -h 10120:1078"))
        assertTrue(ps2.contains("cmp -s") && ps2.contains("timeline-tv.slang") && ps2.contains("timeline-crt.slangp"))
        assertFalse(ps2.contains("rm ") || ps2.contains("crt-gdv-mini-ultra"))
        // nothing is made before ARMSX2's own folder is found, links are refused, and chown does not follow one
        assertTrue(ps2.indexOf("[ -d /data/media/0/Android/data/com.armsx2/files ]") < ps2.indexOf("mkdir -p"))
        assertTrue(ps2.contains("[ -L ") && ps2.contains("chown -h ") && ps2.indexOf("[ -L ") < ps2.indexOf("mkdir -p"))
    }

    @Test fun oneBigEmulatorAtATimeClosesOnlyWhatIsSurelyIdle() {
        fun o(id: String, running: Boolean?, held: String? = null, idle: Boolean = false) = Memory.Other("pkg.$id", id, running, held, idle)
        // a small target is looked after too: an idle Dolphin behind an N64 game is closed, a running ARMSX2 asked about
        assertEquals(Memory.Step.Close(listOf("pkg.dolphin")), Memory.decide("ra:mupen64plus_next", listOf(o("dolphin", true, idle = true))))
        assertEquals(Memory.Step.Ask("pkg.armsx2", "armsx2", null), Memory.decide("ra:snes9x", listOf(o("armsx2", true))))
        assertEquals(Memory.Step.Go, Memory.decide("ra:snes9x", listOf(o("dolphin", false), o("melonds", true))))
        // Dolphin with its game thread gone: closed without a word
        assertEquals(Memory.Step.Close(listOf("pkg.dolphin")), Memory.decide("armsx2", listOf(o("dolphin", true, idle = true))))
        // any other big emulator running, game known or not: asked about, the named one first
        assertEquals(Memory.Step.Ask("pkg.armsx2", "armsx2", null), Memory.decide("dolphin", listOf(o("armsx2", true))))
        assertEquals(Memory.Step.Ask("pkg.hakux", "hakux", "xbox/Farwatch.iso"),
            Memory.decide("dolphin", listOf(o("armsx2", true), o("hakux", true, "xbox/Farwatch.iso"))))
        // held counts even where the emulator reads idle
        assertEquals(Memory.Step.Ask("pkg.dolphin", "dolphin", "wii/x.rvz"), Memory.decide("armsx2", listOf(o("dolphin", true, "wii/x.rvz", idle = true))))
        // not running, or could not be told and holding nothing known: left alone; could not be told but holding a game: asked
        assertEquals(Memory.Step.Go, Memory.decide("dolphin", listOf(o("armsx2", false), o("hakux", null))))
        assertEquals(Memory.Step.Ask("pkg.hakux", "hakux", "xbox/Farwatch.iso"), Memory.decide("dolphin", listOf(o("hakux", null, "xbox/Farwatch.iso"))))
        // hakuX is never closed by itself, even idle; an idle Dolphin beside a running ARMSX2 waits for the question
        assertEquals(Memory.Step.Go, Memory.decide("dolphin", listOf(o("hakux", true, idle = true))))
        assertEquals(Memory.Step.Ask("pkg.armsx2", "armsx2", null), Memory.decide("hakux", listOf(o("dolphin", true, idle = true), o("armsx2", true))))
        // the DS emulators are not big
        assertEquals(Memory.Step.Go, Memory.decide("dolphin", listOf(o("melonds", true))))
    }

    @Test fun theCartridgeConsolesAnswerAFrameSooner() {
        val p = RetroPack.Paths("/h", "pkg", "/d", "/a")
        val on = RetroPack.files(Look(), p)
        for (core in listOf("Nestopia", "Snes9x", "Genesis Plus GX", "Beetle PCE", "Stella", "FinalBurn Neo", "Gambatte", "mGBA")) {
            val cfg = on.getValue("$core/$core.cfg")
            assertTrue(core, cfg.contains("preemptive_frames_enable = \"true\"") && cfg.contains("run_ahead_enabled = \"false\"") && cfg.contains("run_ahead_frames = \"1\""))
            assertTrue(core, cfg.contains("audio_latency = \"48\""))              // and their sound sooner
        }
        for (core in listOf("SwanStation", "Beetle PSX HW", "Beetle Saturn", "Flycast", "Mupen64Plus-Next"))   // the 3D consoles too, measured
            assertTrue(core, on.getValue("$core/$core.cfg").contains("audio_latency = \"48\""))
        // not on the 3D consoles, where re-running a frame is costly
        assertFalse(on.getValue("SwanStation/SwanStation.cfg").contains("preemptive"))
        // nor on MAME 2003-Plus, whose save states are not repeatable (its info file: savestate_features = "basic")
        for (lag in RetroPack.lagChoices) {
            for (cfg in listOf("MAME 2003-Plus/MAME 2003-Plus.cfg", "melonDS DS/melonDS DS.cfg")) {     // nor the DS: not 8 or 16-bit
                val text = RetroPack.files(Look(lag = lag), p).getValue(cfg)
                assertTrue("$cfg $lag", text.contains("preemptive_frames_enable = \"false\"") && text.contains("run_ahead_enabled = \"false\""))
            }
        }
        val ahead = RetroPack.files(Look(lag = "Run-ahead"), p).getValue("Snes9x/Snes9x.cfg")
        assertTrue(ahead.contains("run_ahead_enabled = \"true\"") && ahead.contains("preemptive_frames_enable = \"false\""))
        val off = RetroPack.files(Look(lag = "Off"), p).getValue("Snes9x/Snes9x.cfg")
        assertTrue(off.contains("run_ahead_enabled = \"false\"") && off.contains("preemptive_frames_enable = \"false\""))
        assertEquals("Preemptive frames", RetroPack.lagChoices.first())
    }

    @Test fun aLibraryLaidOutForAnotherFrontEndIsFoundAsItIs() {
        val root = java.nio.file.Files.createTempDirectory("roms").toFile()
        java.io.File(root, "MegaDrive").mkdirs(); java.io.File(root, "MegaDrive/Game (USA).md").writeText("x")
        java.io.File(root, "genesis").mkdirs(); java.io.File(root, "genesis/Other (USA).md").writeText("x")
        java.io.File(root, "PS1").mkdirs(); java.io.File(root, "PS1/Disc (USA).chd").writeText("x")
        val lib = Library.scan(root)
        assertEquals(2, lib["genesis"]?.size)                         // both folders, merged
        assertEquals(1, lib["psx"]?.size)                             // any case
        assertEquals(listOf("genesis", "MegaDrive"), Library.folders(root, Catalog.system("genesis")!!).map { it.name })
        root.deleteRecursively()
    }

    @Test fun theNovasMeasuredSpeakerTuningStaysOnTheNova() {
        assertTrue(NovaCheck.isNova("RPN_V1.0.0.436_20260722_083524_user", "kalama"))
        assertTrue(NovaCheck.isNova(null, "Retroid Pocket Nova"))
        assertFalse(NovaCheck.isNova("RP5_V1.0", "Retroid Pocket 5"))
        assertFalse(NovaCheck.isNova(null, null))
        SpeakerChain.onNova = false
        try { assertEquals(0.0, SpeakerChain.correction(700.0), 0.0) } finally { SpeakerChain.onNova = true }
        assertTrue(SpeakerChain.correction(100.0) < -6.0)          // on the Nova: nothing below what its speakers can play
    }

    @Test fun nintendo64HandsOverEveryRefresh() {
        val p = RetroPack.Paths("/h", "pkg", "/d", "/a")
        val files = RetroPack.files(Look(), p)
        assertTrue(files.getValue("Mupen64Plus-Next/Mupen64Plus-Next.opt").contains("mupen64plus-FrameDuping = \"True\""))
        // the per-game files carry it too
        assertTrue(files.getValue("Mupen64Plus-Next/Legend of Zelda, The - Majora's Mask (USA).opt").contains("mupen64plus-FrameDuping = \"True\""))
    }

    @Test fun picturesWaitOneRefreshLessOnTheNovasScreen() {
        val p = RetroPack.Paths("/h", "pkg", "/d", "/a")
        assertEquals("2", RetroPack.base(Look(), p)["video_max_swapchain_images"])
        assertEquals("3", RetroPack.base(Look(tv = true), p)["video_max_swapchain_images"])   // a television keeps Android's usual queue
    }

    @Test fun dreamcastIsDrawnWithoutTheBeam() {
        val p = RetroPack.Paths("/h", "pkg", "/d", "/a")
        val files = RetroPack.files(Look(sharp = false), p)
        val dc = files.getValue("Flycast/Flycast.slangp")
        assertTrue(dc.startsWith("shaders = \"2\"") && !dc.contains("beam.slang") && !dc.contains("beam-simulator") && !dc.contains("GAIN_VS_BLUR"))
        assertTrue(files.getValue("Flycast/Flycast.cfg").contains("video_shader_subframes = \"1\""))
        // every other console keeps Blur Busters' own
        for (core in listOf("SwanStation", "Beetle Saturn", "Nestopia"))
            assertTrue(core, files.getValue("$core/$core.slangp").contains("crt-beam-simulator.slang"))
        // without the CRT look there is nothing to draw over Dreamcast at all
        assertNull(RetroPack.files(Look(crt3d = false), p)["Flycast/Flycast.slangp"])
        // still means unchanged for three pictures (history is copied once per slice: two slices, two steps a picture)
        val s = SteadyBeam.slang
        assertTrue(s.startsWith("#version 450\n"))                       // RetroArch reads the version from the first line only
        assertTrue(s.contains("OriginalHistory2") && s.contains("OriginalHistory4") && s.contains("OriginalHistory6") && s.contains("CurrentSubFrame % n"))
    }

    @Test fun aScriptIsNeverSeenHalfWritten() {
        val d = java.nio.file.Files.createTempDirectory("scripts").toFile()
        val f = java.io.File(d, "a.sh")
        assertTrue(Scripts.write(f, "echo one\n")); assertEquals("echo one\n", f.readText())
        f.setLastModified(1000L)
        assertTrue(Scripts.write(f, "echo one\n")); assertEquals(1000L, f.lastModified())      // unchanged: not written again
        assertTrue(Scripts.write(f, "echo two\n")); assertEquals("echo two\n", f.readText())
        assertEquals(listOf("a.sh"), d.list()!!.toList())                                     // nothing left beside it
        d.deleteRecursively()
    }

    @Test fun gameCubeAndWiiPickUpWhereTheyWereLeft() {
        // no on-screen messages ("State not found" at every fresh start otherwise)
        assertTrue(DolphinPack.family(Look(), wiiGame = true, pad = true).contains("[Dolphin.Interface]\nOnScreenDisplayMessages = False\n"))
        assertTrue(DolphinPack.family(Look(), wiiGame = false, pad = true).contains("OnScreenDisplayMessages = False"))
        // one state per game, by a name safe for any title
        val a = DolphinResume.name("wii/Comet Garden 2 (USA).rvz")
        assertTrue(Regex("[0-9a-f]{24}\\.sav").matches(a)); assertEquals(a, DolphinResume.name("wii/Comet Garden 2 (USA).rvz"))
        assertFalse(a == DolphinResume.name("wii/Comet Garden (USA).rvz"))
        // moved out of Dolphin's folder only if it is a plain file there that Dolphin is no longer writing, there is room,
        // and the copy is whole; given to the launcher; nothing removed but Dolphin's file (after the move) and a part copy
        val to = "/data/user/0/market.nostos.timeline/files/places/dolphin/x.sav"
        val c = DolphinResume.collectScript(to, 10113)
        assertTrue(c.contains("[ -L ${DolphinResume.TEMP} ]") && c.contains("chown 10113:10113") && c.contains("rm ${DolphinResume.TEMP}"))
        assertTrue(c.contains("pidof org.dolphinemu.dolphinemu") && c.contains("echo busy") && c.contains("echo full") && c.contains("cmp -s ${DolphinResume.TEMP} $to.part"))
        assertEquals(setOf("$to.part", DolphinResume.TEMP), Regex("\\brm (-f )?([^\\s;]+)").findAll(c).map { it.groupValues[2] }.toSet())
        assertTrue(c.indexOf("rm ${DolphinResume.TEMP}") > c.indexOf("mv $to.part $to"))
        // put back as Dolphin's own file; clearing touches only temp.sav
        val pl = DolphinResume.placeScript(to, 10119)
        assertTrue(pl.contains("chown 10119:10119 ${DolphinResume.TEMP}.part") && pl.contains("mv ${DolphinResume.TEMP}.part ${DolphinResume.TEMP}"))
        assertEquals(setOf("${DolphinResume.TEMP}.part"), Regex("\\brm (-f )?([^\\s;]+)").findAll(pl).map { it.groupValues[2] }.toSet())
        assertEquals("rm -f /data/user/0/org.dolphinemu.dolphinemu/files/temp.sav\necho ok\n", DolphinResume.clearScript)
    }

    @Test fun ps2AndXboxSavesAreCopiedAndPs2PicksUpWhereItWasLeft() {
        // the Xbox's whole disk is kept like a save state: two earlier versions, not twenty
        assertEquals(Saves.Kind.DISK, Saves.kind("hdd.img")); assertEquals(Saves.Kind.DISK, Saves.kind("xbox_hdd.qcow2"))
        assertEquals(Saves.Kind.SAVE, Saves.kind("mcd001.ps2"))
        // copied from the emulators' own folders, changed files only, never a link, given to the launcher
        val m = RootMirror.script(RootMirror.ps2, "/data/user/0/market.nostos.timeline/files/mirror/ARMSX2/memcards", 10113)
        assertTrue(m.contains("s=/data/media/0/Android/data/com.armsx2/files/memcards") && m.contains("*.ps2") && m.contains("stat -c %Y:%s") && m.contains("chown -R 10113:10113 /data/user/0/market.nostos.timeline/files/mirror/ARMSX2") && m.contains("chown 10113:10113 /data/user/0/market.nostos.timeline/files/mirror\n"))
        assertTrue(m.contains("[ -L "))
        // only its own part copy is ever removed: when the card is nearly full, or the file changed or ARMSX2 started during the copy
        assertTrue(m.lines().filter { Regex("\\brm ").containsMatchIn(it) }.all { it.contains("\$n.part") })
        assertTrue(m.contains("echo full") && m.contains("grep -q -E '^com\\.armsx2(:|\$)'") && m.contains("echo busy"))
        assertTrue(RootMirror.script(RootMirror.xbox, "/x", 1).contains("hdd.img"))
        // ARMSX2's own autosave switched to match "Keep your place", and only its three preferences touched
        val on = Ps2Resume.script("com.armsx2", 10115, true)
        assertTrue(on.contains("/data/user/0/com.armsx2/shared_prefs/ARMSX2.xml") && on.contains("autoSaveOnExit") && on.contains("autoLoadOnBoot") &&
            on.contains("value=\"true\"") && on.contains("autoSaveIntervalMin\" value=\"5\"") && on.contains("chown 10115:10115"))
        // left alone when already right; otherwise edited behind Android's own backup file, which is removed once all three are right
        assertTrue(on.contains("right && { echo ok; exit 0; }") && on.contains("cp -p /data/user/0/com.armsx2/shared_prefs/ARMSX2.xml /data/user/0/com.armsx2/shared_prefs/ARMSX2.xml.bak") &&
            on.contains("[ -e /data/user/0/com.armsx2/shared_prefs/ARMSX2.xml.bak ] && { echo pending"))
        assertTrue(on.indexOf("rm /data/user/0/com.armsx2/shared_prefs/ARMSX2.xml.bak") > on.lastIndexOf("right ||"))
        val off = Ps2Resume.script("com.armsx2", 10115, false)
        assertTrue(off.contains("value=\"false\"") && off.contains("autoSaveIntervalMin\" value=\"0\"") && !off.contains("value=\"true\""))
    }

    @Test fun everyPresetNumbersItsPassesInOrder() {
        val p = RetroPack.Paths("/h", "pkg", "/d", "/a")
        val looks = listOf(Look(), Look(hz = 60), Look(motion = "Black frames"), Look(crt = "zFast"), Look(crt = "Off"), Look(sharp = false),
            Look(compatible = true), Look(crt3d = false), Look(signal = RetroPack.signals.mapValues { it.value.last() }),
            Look(signal = RetroPack.signals.mapValues { it.value[1] }, lighter = mapOf("psx/A (USA)" to true, "n64/B (USA)" to true, "dreamcast/C (USA)" to true)))
        for (look in looks) for ((path, text) in RetroPack.files(look, p)) {
            if (!(path.endsWith(".slangp") || path.endsWith(".glslp")) || !text.contains("shaders = ")) continue
            val n = Regex("shaders = \"?(\\d+)").find(text)!!.groupValues[1].toInt()
            val passes = Regex("(?m)^shader(\\d+) = ").findAll(text).map { it.groupValues[1].toInt() }.toList()
            assertEquals("$path in $look", (0 until n).toList(), passes)
        }
    }

    @Test fun gameCubeAndWiiGetTheCrtAndAGameThatWillNotStartGetsOpenGl() {
        val docs = DolphinPack.docs(Look(dolphinGlCodes = mapOf("GFRE00" to true, "GM4E01" to false)), "Retroid Pocket Controller")
        assertEquals(Crt3d.dolphin(1.0, 0, "2.0", 0.5), docs.single { it.path == "Shaders/Timeline CRT.glsl" }.text)
        assertTrue(docs.single { it.path == "GameSettings/G.ini" }.text!!.contains("[Video_Enhancements]\nPostProcessingShader = Timeline CRT\n"))
        assertEquals(mapOf("Core" to mapOf("GFXBackend" to "OGL"), "Video_Settings" to mapOf("ShaderCompilationMode" to "0")),
            docs.single { it.path == "GameSettings/GFRE00.ini" }.set)
        assertEquals(mapOf("Core" to setOf("GFXBackend"), "Video_Settings" to setOf("ShaderCompilationMode")),
            docs.single { it.path == "GameSettings/GM4E01.ini" && it.remove.isNotEmpty() }.remove)
        // the documented per-game fixes, by disc ID
        fun fix(id: String) = docs.filter { it.path == "GameSettings/$id.ini" }.fold(emptyMap<String, Map<String, String>>()) { a, d -> a + d.set }
        assertEquals("True", fix("GHQE7D")["Video_Hacks"]?.get("EFBAccessEnable"))           // Simpsons Hit & Run
        assertEquals("False", fix("GPTE41")["Core"]?.get("CPUThread"))                      // Prince of Persia
        assertEquals("1.5", fix("GT4E52")["Core"]?.get("Overclock"))                        // THPS4
        assertEquals("0", fix("RMCE01")["Video_Settings"]?.get("SafeTextureCacheColorSamples"))
        assertEquals("0", fix("RSBE01")["Controls"]?.get("WiimoteSource0"))                 // Brawl keeps its GameCube pad too
        // the family still draws with Vulkan
        assertTrue(docs.single { it.path == "GameSettings/G.ini" }.text!!.contains("GFXBackend = Vulkan"))
        // switched off: no shader file, no shader line
        val off = DolphinPack.docs(Look(crt3d = false), "Retroid Pocket Controller")
        assertTrue(off.none { it.path.startsWith("Shaders/") })
        assertFalse(off.single { it.path == "GameSettings/G.ini" }.text!!.contains("PostProcessingShader"))
    }

    @Test fun fourCanPlayThePlayStationGamesMadeForItAndControllersComeBackToTheirOwnNumber() {
        val p = RetroPack.Paths("/h", "pkg", "/d", "/a")
        val files = RetroPack.files(Look(sharp = false), p)
        assertTrue(files.getValue("SwanStation/CTR - Crash Team Racing (USA).opt").contains("swanstation_ControllerPorts_MultitapMode = \"Port1Only\""))
        assertTrue(files.getValue("Beetle PSX HW/Nickelodeon Rugrats - Search for Reptar (USA).opt").contains("beetle_psx_hw_enable_multitap_port1 = \"enabled\""))
        // the game's own file carries the rest of the core's settings too
        assertTrue(files.getValue("SwanStation/CTR - Crash Team Racing (USA).opt").contains("swanstation_GPU_ResolutionScale = \"1\""))
        assertTrue(files.getValue("SwanStation/SwanStation.opt").contains("swanstation_ControllerPorts_MultitapMode = \"Disabled\""))
        // run lighter as well: one file with both
        val light = RetroPack.files(Look(lighter = mapOf("psx/CTR - Crash Team Racing (USA)" to true)), p).getValue("SwanStation/CTR - Crash Team Racing (USA).opt")
        assertTrue(light.contains("Port1Only") && light.contains("swanstation_GPU_ResolutionScale = \"1\""))
        assertEquals("true", RetroPack.base(Look(), p)["android_input_disconnect_workaround"])
        assertEquals("false", RetroPack.base(Look(reconnectByName = false), p)["android_input_disconnect_workaround"])
    }

    // ---- emulator setup details -----------------------------------------------------------------

    @Test fun chargeHoldOnlyWhileRestingOnTheChargerInSmart() {
        val rest = ChargeHold.REST_MS
        assertTrue(ChargeHold.wanted("Smart", plugged = true, inUse = false, idleMs = rest))
        assertFalse(ChargeHold.wanted("Smart", plugged = true, inUse = false, idleMs = rest - 1))       // just put down
        assertFalse(ChargeHold.wanted("Smart", plugged = true, inUse = true, idleMs = rest * 4))      // playing, or another app on screen
        assertFalse(ChargeHold.wanted("Smart", plugged = false, inUse = false, idleMs = rest * 4))    // off the charger: never
        assertFalse(ChargeHold.wanted("Always to 100%", plugged = true, inUse = false, idleMs = rest * 4))
    }

    @Test fun batteryTextSaysRestingOnlyWhenHeldAtOrAboveTheLimit() {
        assertEquals("82% / resting", BatteryEstimate.text(82, charging = true, hours = null, held = true))
        assertEquals("64% / charging", BatteryEstimate.text(64, charging = true, hours = null, held = true))   // still climbing to 80
        assertEquals("82% / charging", BatteryEstimate.text(82, charging = true, hours = null))
        assertEquals("82%", BatteryEstimate.text(82, charging = false, hours = null, held = true))
    }

    @Test fun dolphinIsHandedEveryDiscStartingWithTheChosenOne() {
        val discs = listOf("/r/gc/HH4.m3u/Disc 1.iso", "/r/gc/HH4.m3u/Disc 2.iso")
        assertEquals(discs, Launch.discSet(discs[0], discs))
        assertEquals(discs.reversed(), Launch.discSet(discs[1], discs))
        val three = listOf("a", "b", "c")
        assertEquals(listOf("b", "c", "a"), Launch.discSet("b", three))
        assertEquals(listOf("x", "a", "b", "c"), Launch.discSet("x", three))
    }

    @Test fun dolphinChangesDiscsByItself() {
        for (wii in listOf(false, true)) assertTrue(DolphinPack.family(Look(), wiiGame = wii, pad = true).contains("AutoDiscChange = True"))
    }

    @Test fun noNoticesBeforeDsAndMameGames() {
        val files = RetroPack.files(Look(), p)
        assertTrue(files.getValue("melonDS DS/melonDS DS.opt").contains("melonds_firmware_username = \"existing_username\""))
        val mame = files.getValue("MAME 2003-Plus/MAME 2003-Plus.opt")
        assertTrue(mame.contains("mame2003-plus_skip_disclaimer = \"enabled\"") && mame.contains("mame2003-plus_skip_warnings = \"enabled\""))
    }

    @Test fun retroidsPowerToFanLinkCanOnlyBeSwitchedOnOrOff() {
        assertEquals("settings put system is_quick_set_performance_and_fan_enable '0'", Vendor.put(Vendor.FAN_LINK, "0"))
        assertNull(Vendor.put(Vendor.FAN_LINK, "2"))
        assertNull(Vendor.put(Vendor.FAN, "0"))                    // the fan itself: never off
    }

    @Test fun onlyTheMakersChargeLimitValuesCanBeWritten() {
        assertEquals("settings put system percent_80_charge_limit '1'", Vendor.put(Vendor.CHARGE_LIMIT, "1"))
        assertNull(Vendor.put(Vendor.CHARGE_LIMIT, "80"))
        assertNull(Vendor.put("limit_capacity_charge", "1"))
    }

    // ---- players on a television ------------------------------------------------------------

    private val nova = Players.Pad("Retroid Pocket Controller", 1, external = false, vendor = 0x2022, product = 0x3001, wired = true)
    private val xbox = Players.Pad("Xbox Wireless Controller", 2, external = true, vendor = 0x045e, product = 0x0b13, wired = false)
    private val pro = Players.Pad("Pro Controller", 3, external = true, vendor = 0x057e, product = 0x2009, wired = true)

    @Test fun dolphinPortOneIsTheHandheldAndTheFirstControllerThenOnePortEach() {
        assertEquals(listOf(listOf("Android/1/Retroid Pocket Controller")), Players.dolphinPorts(listOf(nova)))
        val ports = Players.dolphinPorts(listOf(nova, xbox, pro))
        // wired before wireless: the wired controller joins the handheld on port 1, the wireless one takes port 2
        assertEquals(listOf("Android/1/Retroid Pocket Controller", "Android/3/Pro Controller"), ports[0])
        assertEquals(listOf("Android/2/Xbox Wireless Controller"), ports[1])
        // the handheld's own controls as Retroid's service hands them over: marked external, still the handheld's
        val docked = Players.dolphinPorts(listOf(nova.copy(external = true), xbox))
        assertEquals(listOf("Android/1/Retroid Pocket Controller", "Android/2/Xbox Wireless Controller"), docked[0])
        assertEquals(1, docked.size)
        val five = (2..6).map { Players.Pad("Pad $it", it, external = true, vendor = 1, product = 1, wired = true) }
        assertEquals(4, Players.dolphinPorts(listOf(nova) + five).size)                         // GameCube and Wii have four
        // each control names its controller, any one of them counting
        val pad = DolphinPack.gamePad(listOf("Android/1/Retroid Pocket Controller", "Android/2/Pro Controller"))
        assertEquals("`Android/1/Retroid Pocket Controller:Button A` | `Android/2/Pro Controller:Button A`", Ini.value(pad, "Profile", "Buttons/A"))
        assertEquals("Android/1/Retroid Pocket Controller", Ini.value(pad, "Profile", "Device"))
        val remote = DolphinPack.wiiRemote(listOf("Android/3/Xbox Wireless Controller"), sideways = false)
        assertEquals("Nunchuk", Ini.value(remote, "Profile", "Extension")); assertEquals("True", Ini.value(remote, "Profile", "IR/Auto-Hide"))
    }

    @Test fun aTelevisionIsWideTheDsAttachmentIsNot() {
        assertTrue(Players.isTv(1920, 1080)); assertTrue(Players.isTv(3840, 2160)); assertTrue(Players.isTv(1080, 1920))
        assertFalse(Players.isTv(1024, 768)); assertFalse(Players.isTv(1240, 1080)); assertFalse(Players.isTv(960, 1280))
    }

    @Test fun wiredOrBluetoothIsReadFromTheKernelList() {
        val list = "I: Bus=0003 Vendor=057e Product=2009 Version=8111\nN: Name=\"Pro Controller\"\nP: Phys=usb-1\n\n" +
            "I: Bus=0005 Vendor=045e Product=0b13 Version=0509\nN: Name=\"Xbox Wireless Controller\"\n"
        assertEquals(mapOf("Pro Controller" to true, "Xbox Wireless Controller" to false), Players.buses(list))
    }

    @Test fun retroArchTakesPlayersInTheOrderTheyConnect() {
        val base = RetroPack.base(Look(), p)
        for (n in 1..4) {
            assertEquals("", base["input_player${n}_reserved_device"]); assertEquals("0", base["input_player${n}_device_reservation_type"])
            assertEquals("${n - 1}", base["input_player${n}_joypad_index"])
        }
    }

    @Test fun dolphinGivesEachControllerAPortOnGameCube() {
        val docs = DolphinPack.docs(Look(), listOf(Players.dolphinName(pro), Players.dolphinName(xbox), Players.dolphinName(nova)))
        assertTrue(docs.first { it.path == "Config/Profiles/GCPad/Timeline.ini" }.text!!.contains("Device = Android/3/Pro Controller"))
        assertTrue(docs.first { it.path == "Config/Profiles/GCPad/Timeline 2.ini" }.text!!.contains("Device = Android/2/Xbox Wireless Controller"))
        assertTrue(docs.first { it.path == "Config/Profiles/Wiimote/Timeline.ini" }.text!!.contains("Device = Android/3/Pro Controller"))
        val g = docs.first { it.path == "GameSettings/G.ini" }.text!!
        assertTrue(g.contains("PadProfile2 = Timeline 2") && g.contains("PadProfile3 = Timeline 3") && g.contains("SIDevice1 = 6") && g.contains("SIDevice2 = 6"))
        val r = docs.first { it.path == "GameSettings/R.ini" }.text!!
        assertTrue(r.contains("WiimoteProfile1 = Timeline") && !r.contains("PadProfile2") && !r.contains("SIDevice"))
        // Wii: a remote for each further controller
        assertTrue(r.contains("WiimoteSource1 = 1") && r.contains("WiimoteProfile2 = Timeline 2") && r.contains("WiimoteSource2 = 1") && r.contains("WiimoteProfile3 = Timeline 3"))
        assertTrue(docs.first { it.path == "Config/Profiles/Wiimote/Timeline 2.ini" }.text!!.contains("Device = Android/2/Xbox Wireless Controller"))
        // Brawl and Mario Kart Wii: the GameCube pad for everyone, no remotes
        val brawl = docs.filter { it.path == "GameSettings/RSBE01.ini" }
        val set = brawl.fold(emptyMap<String, Map<String, String>>()) { a, d -> a + d.set }
        assertEquals(mapOf("WiimoteSource0" to "0", "WiimoteSource1" to "0", "WiimoteSource2" to "0", "PadProfile2" to "Timeline 2", "PadProfile3" to "Timeline 3"), set["Controls"])
        assertEquals(mapOf("SIDevice0" to "6", "SIDevice1" to "6", "SIDevice2" to "6"), set["Core"])
        assertEquals(setOf("WiimoteSource3", "PadProfile4"), brawl.flatMap { it.remove["Controls"].orEmpty() }.toSet())
        assertEquals(setOf("SIDevice3"), brawl.flatMap { it.remove["Core"].orEmpty() }.toSet())
        assertTrue(Ini.value(DolphinPack.docs(Look(), listOf(Players.dolphinName(nova))).first { it.path == "GameSettings/R.ini" }.text!!, "Controls", "WiimoteSource1") == null)
        val one = DolphinPack.docs(Look(), listOf(Players.dolphinName(nova))).first { it.path == "GameSettings/G.ini" }.text!!
        assertTrue(!one.contains("PadProfile2") && !one.contains("SIDevice"))
    }

    @Test fun at60HzTheMotionClarityDoesNotDrawTwicePerPicture() {
        assertTrue(Look(hz = 120).subframes)
        assertFalse(Look(hz = 60).subframes)
        assertFalse(Look(hz = 144).subframes)                                                   // two pictures to each of a game's would be too fast
        assertEquals("60.000000", RetroPack.base(Look(hz = 60), p)["video_refresh_rate"])
    }

    @Test fun aTelevisionIsAskedFor120HzWithinWhatTheDockAlreadyCarries() {
        fun m(w: Int, h: Int, hz: Float) = TvMode.Mode(w, h, hz)
        val k60 = m(3840, 2160, 60f); val p120 = m(1920, 1080, 120f); val q120 = m(2560, 1440, 120f); val k120 = m(3840, 2160, 120f); val p60 = m(1920, 1080, 60f)
        assertEquals(p120, TvMode.pick(listOf(k60, p60, p120, q120, k120), k60))                // 1080p preferred; 4K at 120 needs more than the dock showed
        assertEquals(q120, TvMode.pick(listOf(k60, q120, k120), k60))
        assertNull(TvMode.pick(listOf(p60, p120), p60))                                         // twice the 1080p60 link: not risked
        assertNull(TvMode.pick(listOf(k60, p60), k60))                                          // a 60 Hz television: left alone
        assertNull(TvMode.pick(listOf(k60, p120), p120))                                        // already there
        assertEquals(m(1920, 1080, 119.88f), TvMode.pick(listOf(k60, m(1920, 1080, 119.88f), m(1920, 1080, 144f)), k60))
        assertNull(TvMode.pick(listOf(p60, m(1280, 720, 120f), m(1024, 768, 120f)), p60))       // never smaller than 1080 lines, never another shape
        // asked for 60: a 4K television the dock brought up at 30 goes to the largest 60 that fits
        assertEquals(p60, TvMode.pick(listOf(m(3840, 2160, 30f), p60, k60), m(3840, 2160, 30f), want = 60))
        assertNull(TvMode.pick(listOf(k60, p60), k60, want = 60))
    }

    @Test fun theBeamRunsOnTheThreeDConsolesDrawnWithVulkan() {
        val p = RetroPack.Paths("/h", "pkg", "/d", "/a")
        val files = RetroPack.files(Look(sharp = false), p)
        val psx = files.getValue("SwanStation/SwanStation.slangp")
        assertTrue(psx.contains("shaders = \"4\"") && psx.contains("shader0 = \"/h/shaders/shaders_slang/subframe-bfi/shaders/crt-beam-simulator.slang\"".replace("/h/shaders/shaders_slang", p.slang)))
        assertTrue(psx.contains("shader1 = \"/h/shaders/timeline/timeline-signal.slang\"") && psx.contains("shader3 = \"/h/shaders/timeline/crt-gdv-3d.slang\"") && psx.contains("SCAN_DIRECTION"))
        assertTrue(psx.contains("GAIN_VS_BLUR = \"0.5\""))     // half the light each refresh: a picture, then black
        assertTrue(files.getValue("Beetle Saturn/Beetle Saturn.slangp").contains("shaders = \"7\""))
        val dc = files.getValue("Flycast/Flycast.slangp")
        assertTrue(dc.contains("shaders = \"2\"") && !dc.contains("beam.slang") && !dc.contains("beam-simulator"))      // Dreamcast keeps its monitor's full light
        for (core in listOf("SwanStation", "Beetle PSX HW", "Beetle Saturn"))
            assertTrue(core, files.getValue("$core/$core.cfg").contains("video_shader_subframes = \"2\""))
        assertTrue(files.getValue("Flycast/Flycast.cfg").contains("video_shader_subframes = \"1\""))
        // Nintendo 64: GLideN64 at four times its detail under the older OpenGL driver, which has no beam: black
        // frames instead, with the same CRT (its GLSL preset)
        val n64 = files.getValue("Mupen64Plus-Next/Mupen64Plus-Next.cfg")
        assertTrue(n64.contains("video_black_frame_insertion = \"1\"") && n64.contains("video_shader_subframes = \"1\"") && n64.contains("video_driver = \"gl\""))
        val opt = files.getValue("Mupen64Plus-Next/Mupen64Plus-Next.opt")
        assertTrue(opt.contains("mupen64plus-rdp-plugin = \"gliden64\"") && opt.contains("mupen64plus-rsp-plugin = \"hle\"") && opt.contains("2560x1920"))
        assertTrue(files.getValue("Mupen64Plus-Next/Mupen64Plus-Next.glslp").contains("crt-gdv-3d.glsl"))
        assertFalse(files.getValue("Mupen64Plus-Next/Mupen64Plus-Next.slangp").contains("-beam"))
        assertTrue(RetroPack.files(Look(motion3d = "Off"), p).getValue("Mupen64Plus-Next/Mupen64Plus-Next.cfg").contains("video_black_frame_insertion = \"0\""))
        assertTrue(RetroPack.files(Look(hz = 60), p).getValue("Mupen64Plus-Next/Mupen64Plus-Next.cfg").contains("video_black_frame_insertion = \"0\""))
        // Dreamcast drawn the compatible way (OpenGL): none either
        val compat = RetroPack.files(Look(compatible = true), p)
        assertTrue(compat.getValue("Flycast/Flycast.cfg").contains("video_shader_subframes = \"1\""))
        assertFalse(compat.getValue("Flycast/Flycast.slangp").contains("-beam"))
        // at 60 Hz, on a television at 60: off everywhere
        assertTrue(RetroPack.files(Look(hz = 60), p).getValue("SwanStation/SwanStation.cfg").contains("video_shader_subframes = \"1\""))
        assertFalse(RetroPack.files(Look(hz = 60), p).getValue("SwanStation/SwanStation.slangp").contains("-beam"))
        // without the CRT look, the beam alone; 50 Hz games skip it
        assertTrue(RetroPack.files(Look(crt3d = false), p).getValue("SwanStation/SwanStation.slangp").contains("crt-beam-simulator"))
        val pal = RetroPack.files(Look(), p, listOf("psx" to "Some Game (Europe)"))
        assertTrue(pal.getValue("SwanStation/Some Game (Europe).cfg").contains("video_shader_subframes = \"1\""))
        // the television consoles' own setting is left alone
        assertTrue(RetroPack.files(Look(motion3d = "Off"), p).getValue("Snes9x/Snes9x.cfg").contains("video_shader_subframes = \"2\""))
    }

    @Test fun wiiGamesHeldTheirOwnWayGetTheirOwnProfile() {
        val nova = "Android/1/Retroid Pocket Controller"
        val docs = DolphinPack.docs(Look(), nova).groupBy { it.path }
        for (hold in DolphinPack.Hold.values()) assertTrue(hold.profile, docs.containsKey("Config/Profiles/Wiimote/${hold.profile}.ini"))
        fun profile(hold: DolphinPack.Hold) = docs.getValue("Config/Profiles/Wiimote/${hold.profile}.ini").single().text!!
        val classic = profile(DolphinPack.Hold.CLASSIC)
        assertEquals("Classic", Ini.value(classic, "Profile", "Extension"))
        assertEquals("`Button L1`", Ini.value(classic, "Profile", "Classic/Triggers/L"))
        assertEquals("`Button R2` | `Axis 22+` | `Axis 18+`", Ini.value(classic, "Profile", "Classic/Buttons/ZR"))
        assertNull(Ini.value(classic, "Profile", "Nunchuk/Stick/Up"))
        val point = profile(DolphinPack.Hold.POINT)
        assertEquals("False", Ini.value(point, "Profile", "IR/Relative Input"))
        assertEquals("`Button R2` & `Axis 14-`", Ini.value(point, "Profile", "Swing/Forward"))
        assertEquals("`Button R2`", Ini.value(profile(DolphinPack.Hold.UPRIGHT), "Profile", "Hotkeys/Upright Hold"))
        assertEquals("`Button R2` & `Axis 0-`", Ini.value(profile(DolphinPack.Hold.UPRIGHT), "Profile", "Tilt/Left"))
        assertEquals("`Axis 11+`", Ini.value(profile(DolphinPack.Hold.TILT), "Profile", "Tilt/Right"))
        assertEquals("`Axis 14-`", Ini.value(profile(DolphinPack.Hold.SWORD), "Profile", "Swing/Up"))
        assertEquals("True", Ini.value(profile(DolphinPack.Hold.SIDEWAYS), "Profile", "Options/Sideways Wiimote"))
        assertEquals("`Button B`", Ini.value(profile(DolphinPack.Hold.SIDEWAYS), "Profile", "Buttons/1"))
        // each game names its profile, and the profiles come before every game file
        fun named(id: String) = docs.getValue("GameSettings/$id.ini").firstNotNullOf { it.set["Controls"]?.get("WiimoteProfile1") }
        assertEquals("Timeline classic", named("SX4E01")); assertEquals("Timeline pointing", named("RM3E01"))
        assertEquals("Timeline upright", named("RMGE01")); assertEquals("Timeline upright", named("SB4E01"))
        assertEquals("Timeline tilt", named("SMNE01")); assertEquals("Timeline sword", named("SOUE01")); assertEquals("Timeline sideways", named("R7PE01"))
        val order = DolphinPack.docs(Look(), nova).map { it.path }
        assertTrue(order.indexOfLast { it.startsWith("Config/Profiles") } < order.indexOfFirst { it.startsWith("GameSettings") })
        // with the controls left to Dolphin, the lines come back out
        val own = DolphinPack.docs(Look(dolphinPad = false), nova)
        assertEquals(setOf("WiimoteProfile1"), own.first { it.path == "GameSettings/RM3E01.ini" && it.remove.isNotEmpty() }.remove["Controls"])
        // two controls held together come from the same controller, either controller counting
        val two = DolphinPack.wiiRemote(listOf("A/1", "B/2"), DolphinPack.Hold.POINT)
        assertEquals("(`A/1:Button R2` & `A/1:Axis 14-`) | (`B/2:Button R2` & `B/2:Axis 14-`)", Ini.value(two, "Profile", "Swing/Forward"))
        assertEquals("False", Ini.value(two, "Profile", "IR/Relative Input"))
        assertEquals("`A/1:Button L2` | `B/2:Button L2` | `A/1:Axis 23+` | `B/2:Axis 23+` | `A/1:Axis 17+` | `B/2:Axis 17+`",
            Ini.value(DolphinPack.wiiRemote(listOf("A/1", "B/2"), DolphinPack.Hold.CLASSIC), "Profile", "Classic/Buttons/ZL"))
    }

    @Test fun aPlaceMadeOnTheNunchukKeepsIt() {
        assertTrue("SX4E01" in DolphinPack.nunchukFirst)
        assertFalse("RMGE01" in DolphinPack.nunchukFirst || "R7PE01" in DolphinPack.nunchukFirst)
        val dev = "Android/1/Retroid Pocket Controller"
        fun controls(look: Look) = DolphinPack.forPorts(look, listOf(listOf(dev))).filter { it.path == "GameSettings/SX4E01.ini" }
            .mapNotNull { it.set["Controls"]?.get("WiimoteProfile1") }.single()
        assertEquals("Timeline classic", controls(Look()))
        assertEquals(DolphinPack.PROFILE, controls(Look(keptOnNunchuk = setOf("SX4E01"))))
    }

    @Test fun dolphinGameFixesAndNotes() {
        val docs = DolphinPack.docs(Look(), "Android/1/Retroid Pocket Controller")
        fun fix(id: String, section: String, key: String) = docs.filter { it.path == "GameSettings/$id.ini" }.firstNotNullOfOrNull { it.set[section]?.get(key) }
        assertEquals("False", fix("GLME01", "Core", "CPUThread"))
        assertEquals("False", fix("GV4E69", "Video_Hacks", "FastTextureSampling"))
        assertEquals("False", fix("GT3E52", "Video_Hacks", "XFBToTextureEnable"))
        assertEquals("0", fix("G4BE08", "Video_Settings", "SafeTextureCacheColorSamples"))
        assertEquals("False", fix("G4BE08", "Video_Settings", "FastDepthCalc"))
        // Xenoblade keeps its fix as well as its controller
        assertEquals("False", fix("SX4E01", "Video_Hacks", "EFBToTextureEnable"))
        assertTrue(DolphinPack.startsFresh("luigi's mansion")); assertFalse(DolphinPack.startsFresh("super mario sunshine"))
        val luigi = ControlsNote.of("gc", "luigi's mansion", "dolphin", true, null, keeps = false)
        assertTrue(luigi.contains("starts from its title every time") && luigi.endsWith("To leave: Back, then Exit Emulation."))
        assertTrue(ControlsNote.of("wii", "xenoblade chronicles", "dolphin", true, null).startsWith("Played on the Classic Controller"))
        assertTrue(ControlsNote.of("wii", "super mario galaxy", "dolphin", true, null).contains("Hold R2 to stand the remote up"))
        assertTrue(ControlsNote.of("gc", "animal crossing", "dolphin", false, null).contains("Game Boy Advance link"))
        assertTrue(ControlsNote.of("wii", "warioware - smooth moves", "dolphin", false, null).startsWith("Its microgames"))
        assertFalse(ControlsNote.of("wii", "metroid prime", "dolphin", true, null).contains("Fusion"))
    }

    @Test fun nintendo64GamesGetTheirRumbleFixesAndButtons() {
        val p = RetroPack.Paths("/storage/emulated/0/Timeline/retroarch", "com.retroarch.aarch64", "/data/user/0/com.retroarch.aarch64", "/data/app/x/base.apk")
        val out = RetroPack.files(Look(), p)
        val star = out.getValue("Mupen64Plus-Next/Star Fox 64 (USA) (Rev 1).opt")
        for (port in 1..4) assertTrue(star.contains("mupen64plus-pak$port = \"rumble\""))
        assertTrue(star.contains("mupen64plus-43screensize = \"2560x1920\""))      // every usual setting comes with it
        // a game that saves to the Controller Pak keeps it
        assertFalse(out.containsKey("Mupen64Plus-Next/Turok 2 - Seeds of Evil (USA) (Rev 1).opt"))
        val majora = out.getValue("Mupen64Plus-Next/Legend of Zelda, The - Majora's Mask (USA).opt")
        assertTrue(majora.contains("mupen64plus-EnableCopyAuxToRDRAM = \"True\"") && majora.contains("mupen64plus-pak1 = \"rumble\""))
        assertTrue(out.getValue("Mupen64Plus-Next/Banjo-Kazooie (USA) (Rev 1).opt").contains("mupen64plus-EnableCopyColorToRDRAM = \"Sync\""))
        val blast = out.getValue("Mupen64Plus-Next/Blast Corps (USA) (Rev 1).opt")
        assertTrue(blast.contains("mupen64plus-EnableLegacyBlending = \"False\"") && !blast.contains("pak1"))
        // run lighter, a game keeps its own settings
        val light = RetroPack.files(Look(lighter = mapOf("n64/Star Fox 64 (USA) (Rev 1)" to true)), p).getValue("Mupen64Plus-Next/Star Fox 64 (USA) (Rev 1).opt")
        assertTrue(light.contains("mupen64plus-43screensize = \"320x240\"") && light.contains("mupen64plus-pak1 = \"rumble\""))
        // Killer Instinct Gold: the other layout, and six attacks on six buttons for every player
        val ki = RetroPack.KI_GOLD
        assertTrue(out.getValue("Mupen64Plus-Next/$ki.opt").contains("mupen64plus-alt-map = \"True\""))
        val rmp = out.getValue("remaps/Mupen64Plus-Next/$ki.rmp")
        for (player in 1..4) for ((button, id) in listOf("y" to 1, "x" to 10, "r" to 9, "b" to 0, "a" to 8, "r2" to 11))
            assertTrue("$player $button", rmp.lines().contains("input_player${player}_btn_$button = \"$id\""))
        assertEquals(24, rmp.lines().count { it.isNotBlank() })
        val note = ControlsNote.of("n64", "killer instinct gold", "ra:mupen64plus_next_gles3", true, null)
        assertTrue(note.startsWith("Punches are Y, X and R1") && note.contains("On an Xbox controller, punches are X, Y and RB") && !note.contains("C buttons"))
        assertTrue(ControlsNote.of("n64", "pokemon snap", "ra:mupen64plus_next_gles3", true, null).contains("Professor Oak"))
        // No rumble only where the Controller Pak takes the slot: never for a game that had no rumble at all
        assertTrue(ControlsNote.of("n64", "turok 2 - seeds of evil", "ra:mupen64plus_next_gles3", true, null).contains("No rumble"))
        assertTrue(ControlsNote.of("n64", "diddy kong racing", "ra:mupen64plus_next_gles3", true, null).contains("time trial ghost"))
        for (base in listOf("doom 64", "turok - dinosaur hunter"))
            assertFalse(base, ControlsNote.of("n64", base, "ra:mupen64plus_next_gles3", true, null).contains("rumble"))
        assertTrue(ControlsNote.of("nds", "mario & luigi - bowser's inside story", "ra:melondsds", true, null).contains("giant battles, hold L2"))
        assertTrue(ControlsNote.of("gbc", "mario tennis", "ra:gambatte", true, null).contains("Transfer Pak"))
        assertFalse(ControlsNote.of("n64", "mario tennis", "ra:mupen64plus_next_gles3", true, null).contains("Transfer Pak"))
        assertTrue(ControlsNote.of("nes", "startropics", "ra:nestopia", true, null).contains("747"))
        assertTrue(ControlsNote.of("arcade", "robotron", "ra:fbneo", true, null).startsWith("SELECT puts in a coin and START starts. Move with the left stick"))
        // no note says it louder than it is
        for (s in listOf("n64", "nes", "psx", "arcade", "saturn", "dreamcast", "gbc", "gba", "nds"))
            assertFalse(ControlsNote.of(s, "x", "ra:x", true, null).contains("!"))
    }
    @Test fun cartridgeSavingGamesTakeTheRumblePak() {
        val out = RetroPack.files(Look(), RetroPack.Paths("/h", "pkg", "/d", "/a"))
        for (stem in listOf("Perfect Dark (USA) (Rev 1)", "WWF No Mercy (USA) (Rev 1)", "Major League Baseball Featuring Ken Griffey Jr. (USA)",
                "Diddy Kong Racing (USA) (En,Fr) (Rev 1)"))
            assertTrue(stem, out.getValue("Mupen64Plus-Next/$stem.opt").contains("mupen64plus-pak1 = \"rumble\""))
        assertNull(out["Mupen64Plus-Next/Excitebike 64 (USA) (Rev 1).opt"])           // its own tracks are on the Controller Pak
        assertTrue(ControlsNote.of("n64", "diddy kong racing", "ra:mupen64plus_next_gles3", true, null).contains("Player 1 Pak, memory"))
    }
    @Test fun dolphinRumblesTheControllersOwnMotor() {
        val docs = DolphinPack.docs(Look(), listOf("Android/1/Retroid Pocket Controller", "Android/2/Xbox Wireless Controller")).associateBy { it.path }
        assertEquals("`Motor 0`", Ini.value(docs.getValue("Config/Profiles/GCPad/Timeline.ini").text!!, "Profile", "Rumble/Motor"))
        assertEquals("`Motor 0`", Ini.value(docs.getValue("Config/Profiles/Wiimote/Timeline classic.ini").text!!, "Profile", "Rumble/Motor"))
        assertEquals("`Motor 0`", Ini.value(docs.getValue("Config/Profiles/GCPad/Timeline 2.ini").text!!, "Profile", "Rumble/Motor"))
        // several controllers to a port: each one's motor
        val both = DolphinPack.gamePad(listOf("Android/1/Retroid Pocket Controller", "Android/2/Xbox Wireless Controller"))
        assertEquals("`Android/1/Retroid Pocket Controller:Motor 0` | `Android/2/Xbox Wireless Controller:Motor 0`", Ini.value(both, "Profile", "Rumble/Motor"))
    }
    @Test fun nintendo64IsDrawnAtEightTimesAndBroughtBackToItsLines() {
        val p = RetroPack.Paths("/h", "pkg", "/d", "/a")
        // the sharper look: three halvings of the rows (1920 to 240), one of the columns (2560 to 1280)
        val sharp = RetroPack.files(Look(), p).getValue("Mupen64Plus-Next/Mupen64Plus-Next.glslp")
        for (line in listOf("scale_x0 = \"0.5\"", "scale_y0 = \"0.5\"", "scale_x1 = \"1.0\"", "scale_y1 = \"0.5\"", "scale_x2 = \"1.0\"", "scale_y2 = \"0.5\""))
            assertTrue(line, sharp.contains(line))
        assertFalse(sharp.contains("scale_x3 = "))
        // the true picture: both ways, to 320 x 240
        val truth = RetroPack.files(Look(sharp = false), p).getValue("Mupen64Plus-Next/Mupen64Plus-Next.glslp")
        for (i in 0..2) assertTrue(truth.contains("scale_x$i = \"0.5\"") && truth.contains("scale_y$i = \"0.5\""))
        // a game run lighter has nothing to bring down
        val light = RetroPack.files(Look(lighter = mapOf("n64/Bristle's Rough Night (USA)" to true)), p).getValue("Mupen64Plus-Next/Bristle's Rough Night (USA).glslp")
        assertFalse(light.contains("scale_y0 = \"0.5\""))
        // Pokemon Snap at four times: its own options and presets with two row halvings
        val snap = "Pokemon Snap (USA)"
        val files = RetroPack.files(Look(), p)
        assertTrue(files.getValue("Mupen64Plus-Next/$snap.opt").contains("mupen64plus-43screensize = \"1280x960\""))
        val four = files.getValue("Mupen64Plus-Next/$snap.glslp")
        for (line in listOf("scale_x0 = \"1.0\"", "scale_y0 = \"0.5\"", "scale_x1 = \"1.0\"", "scale_y1 = \"0.5\"")) assertTrue(line, four.contains(line))
        assertFalse(four.contains("scale_y2 = \"0.5\""))
        val lighter = RetroPack.files(Look(lighter = mapOf("n64/$snap" to true)), p)
        assertTrue(lighter.getValue("Mupen64Plus-Next/$snap.opt").contains("mupen64plus-43screensize = \"320x240\""))
        assertTrue(files.getValue("Mupen64Plus-Next/Mupen64Plus-Next.opt").contains("mupen64plus-43screensize = \"2560x1920\""))
    }

    @Test fun shootersMadeForTheCButtonsGetTwinSticks() {
        val p = RetroPack.Paths("/h", "pkg", "/d", "/a")
        val out = RetroPack.files(Look(), p)
        for (stem in listOf("Turok - Dinosaur Hunter (USA) (Rev 2)", "Turok 2 - Seeds of Evil (USA) (Rev 1)", "Tsumi to Batsu - Hoshi no Keishousha (Japan)")) {
            val rmp = out.getValue("remaps/Mupen64Plus-Next/$stem.rmp")
            for (player in 1..4) for (line in listOf("input_player${player}_stk_l_x+ = \"20\"", "input_player${player}_stk_l_y- = \"23\"",
                    "input_player${player}_stk_r_x- = \"17\"", "input_player${player}_stk_r_y+ = \"18\"", "input_player${player}_btn_r2 = \"12\""))
                assertTrue(line, rmp.contains(line + "\n"))
            assertTrue(rmp.startsWith(RetroPack.n64Buttons()))         // A and B stay where the core's own remap puts them
        }
        assertNull(out["remaps/Mupen64Plus-Next/GoldenEye 007 (USA).rmp"])
        assertTrue(ControlsNote.of("n64", "turok - dinosaur hunter", "ra:mupen64plus_next_gles3", true, null).startsWith("A and B are the console's A and B. Move with the left stick"))
        assertTrue(ControlsNote.of("n64", "turok 2 - seeds of evil", "ra:mupen64plus_next_gles3", true, null).contains("No rumble"))
    }

    @Test fun gamesWithNeedsOfTheirOwnGetTheirFiles() {
        val p = RetroPack.Paths("/storage/emulated/0/Timeline/retroarch", "com.retroarch.aarch64", "/data/user/0/com.retroarch.aarch64", "/data/app/x/base.apk")
        val out = RetroPack.files(Look(), p)
        // Dreamcast fixes carry every usual setting with them
        val taxi = out.getValue("Flycast/Crazy Taxi (USA).opt")
        assertTrue(taxi.contains("reicast_native_depth_interpolation = \"enabled\"") && taxi.contains("reicast_internal_resolution"))
        // Saturn's 3D Control Pad games, NES's three-in-one (a Zapper on the handheld, a pad docked), TurboGrafx's L2
        assertEquals(RetroPack.padsOf(261), out.getValue("remaps/Beetle Saturn/Burning Rangers (USA).rmp"))
        val three = "Super Mario Bros. + Duck Hunt + World Class Track Meet (USA)"
        assertEquals("input_libretro_device_p2 = \"262\"\n", out.getValue("remaps/Nestopia/$three.rmp"))
        assertTrue(out.getValue("Nestopia/$three.opt").contains("nestopia_overscan_v_top = \"0\""))
        val docked = RetroPack.files(Look(tv = true), p)
        assertEquals("input_libretro_device_p2 = \"257\"\n", docked.getValue("remaps/Nestopia/$three.rmp"))
        assertNull(docked["Nestopia/$three.opt"])
        assertTrue(out.getValue("remaps/Beetle PCE/tg16.rmp").contains("input_player1_btn_l2 = \"-1\""))
        // handhelds: the Advance-aware Color games, WarioWare's sensors, the DS microphone and pointer
        assertTrue(out.getValue("Gambatte/Shantae (USA).opt").contains("gambatte_gb_hwmode = \"GBA\""))
        assertEquals("input_sensors_enable = \"true\"\n", out.getValue("mGBA/WarioWare - Twisted! (USA).cfg"))
        val ds = out.getValue("melonDS DS/melonDS DS.opt")
        assertTrue(ds.contains("melonds_mic_input = \"blow\"") && ds.contains("melonds_show_cursor = \"timeout\""))
        assertTrue(out.getValue("melonDS DS/Contra 4 (USA).opt").contains("melonds_screen_layout1 = \"top-bottom\""))
        // PlayStation: analog at power-on for the games made for it, the console's own disc speed for all
        assertTrue(out.getValue("SwanStation/Silent Hill (USA).opt").contains("swanstation_Controller1_ForceAnalog = \"true\""))
        assertTrue(out.getValue("Beetle PSX HW/Silent Hill (USA).opt").contains("beetle_psx_hw_analog_toggle = \"enabled-analog\""))
        assertTrue(out.getValue("SwanStation/SwanStation.opt").contains("swanstation_CDROM_ReadSpeedup = \"1\""))
        assertTrue(out.getValue("SwanStation/Metal Gear Solid (USA) (Rev 1).opt").contains("swanstation_GPU_PGXPCulling = \"false\""))
        // a memory card is only ever replaced by one holding every save it had
        fun card(vararg names: String) = ByteArray(PsxCard.SIZE).also { c ->
            c[0] = 'M'.code.toByte(); c[1] = 'C'.code.toByte()
            names.forEachIndexed { i, n -> val f = (i + 1) * 128; c[f] = 0x51; n.toByteArray().copyInto(c, f + 10) }
        }
        assertEquals(setOf("BASCUS-94455GT2"), PsxCard.saves(card("BASCUS-94455GT2")))
        assertTrue(PsxCard.covers(card("A", "B"), card("A")))
        assertFalse(PsxCard.covers(card("B"), card("A")))
        assertTrue(PsxCard.saves(ByteArray(10)).isEmpty())
        // Sega CD's cartridge memory, kept for each game, belongs to its game
        assertTrue(Saves.belongs("Shining Force CD (USA)_4Mbit_cart.brm", "Shining Force CD (USA)"))
    }
}
