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

class GbLooksTest {
    /** A cartridge header: the 16 title bytes as hex, the old licensee byte, and the new licensee code. */
    private fun header(title: String, old: Int, new: String = "\u0000\u0000"): ByteArray {
        val h = ByteArray(0x150)
        title.chunked(2).forEachIndexed { i, b -> h[0x134 + i] = b.toInt(16).toByte() }
        h[0x14B] = old.toByte()
        h[0x144] = new[0].code.toByte()
        h[0x145] = new[1].code.toByte()
        return h
    }

    private fun hex(vararg c: Int) = c.toList()
    private fun colours(id: Int) = GbLooks.colors(id).toList()

    // Each as a Game Boy Color's start-up gives it (Gambatte's GBC colorization gives the same for the Nintendo titles).
    @Test fun nintendoCartridgesGetTheirOwnPalette() {
        assertEquals(3, GbLooks.paletteId(header("54455452495300000000000000000000", 0x01)))               // Tetris
        assertEquals(hex(0x7FFF, 0x03FF, 0x001F, 0x0000, 0x7FFF, 0x03FF, 0x001F, 0x0000, 0x7FFF, 0x03FF, 0x001F, 0x0000), colours(3))
        assertEquals(13, GbLooks.paletteId(header("504f4b454d4f4e205245440000000000", 0x33, "01")))        // Pokémon Red
        assertEquals(hex(0x7FFF, 0x421F, 0x1CF2, 0x0000, 0x7FFF, 0x1BEF, 0x0200, 0x0000, 0x7FFF, 0x421F, 0x1CF2, 0x0000), colours(13))
        assertEquals(32, GbLooks.paletteId(header("53555045524d4152494f4c414e443300", 0x01)))              // Wario Land
        assertEquals(36, GbLooks.paletteId(header("4b4952425920445245414d204c414e44", 0x01)))              // Kirby's Dream Land
        assertEquals(hex(0x7E74, 0x03FF, 0x0180, 0x0000, 0x299F, 0x001A, 0x000C, 0x0000, 0x7C00, 0x7FFF, 0x3FFF, 0x7E00), colours(36))
    }

    @Test fun titlesSharingASumAreToldApartByTheirFourthLetter() {
        assertEquals(22, GbLooks.paletteId(header("5355504552204d4152494f4c414e4400", 0x01)))              // Super Mario Land: E
        assertEquals(hex(0x7ED6, 0x4BFF, 0x2175, 0x0000, 0x0000, 0x7FFF, 0x421F, 0x1CF2, 0x0000, 0x7FFF, 0x421F, 0x1CF2), colours(22))
        assertEquals(46, GbLooks.paletteId(header("4d4554524f4944320000000000000000", 0x01)))              // Metroid II: R
        assertEquals(hex(0x7FFF, 0x7E8C, 0x7C00, 0x0000, 0x03FF, 0x001F, 0x000C, 0x0000, 0x7FFF, 0x1BEF, 0x0200, 0x0000), colours(46))
        assertEquals(36, GbLooks.paletteId(header("4b495242593200000000000000000000", 0x33, "01")))        // Kirby's Dream Land 2: B
        assertEquals(17, GbLooks.paletteId(header("4d4f475552414e594100000000000000", 0x33, "01")))        // Mole Mania: U
    }

    @Test fun otherPublishersGetTheStartUpsOwnPalette() {
        assertEquals(0, GbLooks.paletteId(header("4241544d414e00000000000000000000", 0xBB)))               // Batman (Sunsoft), whose sum Nintendo's share
        assertEquals(0, GbLooks.paletteId(header("4d4547414d414e350000000000000000", 0x33, "08")))        // Mega Man V (Capcom)
        assertEquals(0, GbLooks.paletteId(ByteArray(0x20)))
        assertEquals(hex(0x7FFF, 0x1BEF, 0x6180, 0x0000, 0x7FFF, 0x421F, 0x1CF2, 0x0000, 0x7FFF, 0x421F, 0x1CF2, 0x0000), colours(0))
        assertEquals(colours(0), colours(99))
    }

    // Gambatte's own results on the device, read from its frame: the Color's screen, front light central.
    @Test fun colourIsCorrectedAsGambatteCorrectsIt() {
        assertEquals(31710, GbLooks.correct(0x7FFF))
        assertEquals(18303, GbLooks.correct(0x03FF))
        assertEquals(14748, GbLooks.correct(0x001F))
        assertEquals(22172, GbLooks.correct(0x421F))
        assertEquals(12658, GbLooks.correct(0x1CF2))
        assertEquals(15189, GbLooks.correct(0x1BEF))
        assertEquals(23076, GbLooks.correct(0x6180))
        assertEquals(0, GbLooks.correct(0))
    }

    @Test fun theCodePaletteSaysShadeInRedAndPaletteInGreen() {
        val pal = GbLooks.codePal
        assertTrue(pal.startsWith("[General]\n"))
        assertTrue("Background0=16252928\n" in pal)          // 248 red: shade 0
        assertTrue("Background3=0\n" in pal)
        assertTrue("Sprite%2010=16285696\n" in pal)          // the first sprites: 128 green
        assertTrue("Sprite%2023=64512\n" in pal)             // the second sprites' darkest: 252 green
        assertEquals(13, pal.trimEnd().lines().size)
    }

    @Test fun theFolderPutsTheNextLookFirstAndTheCurrentOneLast() {
        val gb = GbLooks.cycle(GbLooks.all, GbLooks.GAME_BOY)
        assertEquals(listOf("   Game Boy Pocket.slangp" to GbLooks.POCKET, "  Game Boy Color.slangp" to GbLooks.COLOR, " Game Boy.slangp" to GbLooks.GAME_BOY), gb)
        assertEquals(listOf(GbLooks.GAME_BOY, GbLooks.POCKET, GbLooks.COLOR), GbLooks.cycle(GbLooks.all, GbLooks.COLOR).map { it.second })
        assertEquals(GbLooks.startName(GbLooks.COLOR), GbLooks.cycle(GbLooks.all, GbLooks.COLOR).last().first)
        // RetroArch sorts them, ignoring case: the order holds.
        for (look in GbLooks.all) GbLooks.cycle(GbLooks.all, look).map { it.first }.let { assertEquals(it.sortedWith(String.CASE_INSENSITIVE_ORDER), it) }
        val two = GbLooks.cycle(listOf(GbLooks.GAME_BOY, GbLooks.POCKET), GbLooks.POCKET)
        assertEquals(listOf("  Game Boy.slangp" to GbLooks.GAME_BOY, " Game Boy Pocket.slangp" to GbLooks.POCKET), two)
        assertEquals(GbLooks.GAME_BOY, GbLooks.cycle(GbLooks.all, "Something else").last().second)
    }

    @Test fun aCartridgeInColourOfItsOwnHasTheTwoDotMatrixLooks() {
        assertEquals(listOf(GbLooks.GAME_BOY, GbLooks.POCKET), GbLooks.looks(GbLooks.Cart(29, true, "Yellow")))
        assertEquals(GbLooks.all, GbLooks.looks(GbLooks.Cart(3, false, "Tetris")))
    }

    @Test fun cartridgesAreReadFromTheFileOrTheFirstOneInAZip() {
        val dir = Files.createTempDirectory("gb").toFile()
        try {
            val yellow = header("504f4b454d4f4e2059454c4c4f570080", 0x33, "01").also { it[0x143] = 0x80.toByte() }
            File(dir, "Pokemon Yellow (USA).gb").writeBytes(yellow + ByteArray(64))
            val c = GbLooks.cart(File(dir, "Pokemon Yellow (USA).gb"))
            assertEquals(29, c.palette)
            assertTrue(c.color)
            assertEquals("Pokemon Yellow (USA)", c.name)
            val zip = File(dir, "Tetris.zip")
            java.util.zip.ZipOutputStream(zip.outputStream()).use { z ->
                z.putNextEntry(java.util.zip.ZipEntry("readme.txt")); z.write("x".toByteArray()); z.closeEntry()
                z.putNextEntry(java.util.zip.ZipEntry("Tetris (World) (Rev 1).gb")); z.write(header("54455452495300000000000000000000", 0x01)); z.closeEntry()
            }
            GbLooks.cart(zip).let { assertEquals(3, it.palette); assertFalse(it.color); assertEquals("Tetris (World) (Rev 1)", it.name) }
            GbLooks.cart(File(dir, "missing.gb")).let { assertEquals(0, it.palette); assertEquals("missing", it.name) }
        } finally { dir.deleteRecursively() }
    }

    private val preset = """
        shaders = 2

        shader0 = shaders/gameboy/gb-pass0.slang
        filter_linear0 = false
        scale_type0 = viewport
        alias0 = "PASS0"
        response_time = "0.33"

        shader1 = shaders/gameboy/../gameboy/gb-pass1.slang
        scale_type_x1 = source
        float_framebuffer1 = true

        textures = COLOR_PALETTE;BACKGROUND
        COLOR_PALETTE = shaders/gameboy/resources/palette.png
        COLOR_PALETTE_linear = false
        BACKGROUND = /somewhere/bg.png
    """.trimIndent()

    @Test fun aDotMatrixPresetIsReadAfterTheCodes() {
        val made = GbLooks.dotMatrix(preset, "/s/handheld", "/t", 0.555)
        assertNotNull(made)
        val keys = made!!.lines().filter { '=' in it }.associate { it.substringBefore('=').trim() to it.substringAfter('=').trim().trim('"') }
        assertEquals("3", keys["shaders"])
        assertEquals("/t/gb-response.slang", keys["shader0"])
        assertEquals("true", keys["float_framebuffer0"])
        assertEquals("/s/handheld/shaders/gameboy/gb-pass0.slang", keys["shader1"])
        assertEquals("viewport", keys["scale_type1"])
        assertEquals("PASS0", keys["alias1"])
        assertEquals("/s/handheld/shaders/gameboy/gb-pass1.slang", keys["shader2"])
        assertEquals("source", keys["scale_type_x2"])
        assertEquals("true", keys["float_framebuffer2"])
        assertEquals("/s/handheld/shaders/gameboy/resources/palette.png", keys["COLOR_PALETTE"])
        assertEquals("false", keys["COLOR_PALETTE_linear"])
        assertEquals("/somewhere/bg.png", keys["BACKGROUND"])
        assertEquals("0.000000", keys["response_time"])          // the response is done in the first pass instead
        assertEquals("0.555000", keys["gb_response"])
        assertEquals("0.000000", keys["gb_color"])
        assertEquals("1.000000", keys["integer_mode"])
        assertFalse("shader3" in keys)
        assertNull(GbLooks.dotMatrix("#reference \"other.slangp\"\n", "/s", "/t", 0.5))
        assertNull(GbLooks.dotMatrix("shaders = 2\nshader0 = a.slang\n", "/s", "/t", 0.5))
    }

    @Test fun theColorLookUsesTheGamesPaletteCorrected() {
        val made = GbLooks.colorPreset(GbLooks.Cart(3, false, "Tetris"), "/t", "/s")
        assertTrue("shader0 = \"/t/gb-decode.slang\"\n" in made && "alias0 = \"GbFrame\"\n" in made)
        assertTrue("shader4 = \"/t/pixel_transparency_gb.slang\"\n" in made)
        assertTrue("shader3 = \"/s/handheld/shaders/authentic_gbc/authentic_gbc_fast.slang\"\n" in made)
        assertTrue("gb_color = \"1.000000\"\n" in made)
        assertTrue("gb_c0 = \"31710.000000\"\ngb_c1 = \"18303.000000\"\ngb_c2 = \"14748.000000\"\ngb_c3 = \"0.000000\"\n" in made)
        assertTrue("gb_c11 = \"0.000000\"\n" in made)
    }

    @Test fun pixelTransparencyReadsTheDecodedFrame() {
        val pt = "#version 450\nlayout(set = 0, binding = 3) uniform sampler2D Original;\nfloat a = texture(Original, uv).r;\n" +
            "vec3 b = texture(Original, vTexCoord).rgb;\nvec2 c = vec2(textureSize(Original, 0));\nvec2 d = vec2(textureSize(Original, 0));\n"
        val made = GbLooks.transparency(pt)
        assertNotNull(made)
        assertTrue(made!!.startsWith("#version 450\n// Made by Timeline"))
        assertFalse("Original" in made.substringAfter("GbFrame) instead"))
        assertEquals(5, Regex("GbFrame").findAll(made.substringAfter("core's, which holds code colours.\n")).count())
        assertNull(GbLooks.transparency(pt.replace("vec3 b = texture(Original, vTexCoord).rgb;\n", "")))
        assertNull(GbLooks.transparency("#include \"x.inc\"\n$pt"))
    }

    @Test fun theLookAGameWasLeftInIsReadFromTheLog() {
        val home = "/storage/emulated/0/Timeline/retroarch/shaders/timeline/gb"
        val log = """
            [Shaders] Specific shader preset found at "/storage/emulated/0/Timeline/retroarch/config/Gambatte/Tetris (World) (Rev 1).slangp".
            [Shaders] Applying shader: "$home/Tetris (World) (Rev 1)/   Game Boy Pocket.slangp".
            [Shaders] Applying shader: "$home/Tetris (World) (Rev 1)/  Game Boy Color.slangp".
            [Shaders] Specific shader preset found at "/storage/emulated/0/Timeline/retroarch/config/Gambatte/Dr. Mario (World) (Rev 1).slangp".
            [Shaders] Applying shader: "$home/Dr. Mario (World) (Rev 1)/   Game Boy Pocket.slangp".
            [Shaders] Specific shader preset found at "/storage/emulated/0/Timeline/retroarch/config/Gambatte/Dr. Mario (World) (Rev 1).slangp".
            [Shaders] Applying shader: "/elsewhere/crt.slangp".
        """.trimIndent()
        assertEquals(mapOf("Tetris (World) (Rev 1)" to GbLooks.COLOR), GbLooks.leftIn(log))
        assertTrue(GbLooks.leftIn("").isEmpty())
    }

    /** What the looks are made from, as RetroArch's shader collection has it. */
    private fun collection(dir: File): String = File(dir, "slang").also { s ->
        File(s, "handheld").mkdirs()
        File(s, "handheld/gameboy.slangp").writeText(preset)
        File(s, "handheld/gameboy-pocket.slangp").writeText(preset)
        File(s, "handheld/shaders/pixel_transparency").mkdirs()
        File(s, "handheld/shaders/pixel_transparency/pixel_transparency.slang").writeText("#version 450\nuniform sampler2D Original;\n" +
            "texture(Original, a); texture(Original, b); textureSize(Original, 0); textureSize(Original, 0);\n")
        for (f in GbLooks.colorFiles) File(s, f).also { it.parentFile.mkdirs() }.writeText("x")
    }.path

    @Test fun writingTheLooksPutsAFolderForEachGameAndTidiesUp() {
        val dir = Files.createTempDirectory("looks").toFile()
        try {
            val home = File(dir, "home").path
            val slang = collection(dir)
            val pals = File(dir, "palettes")
            val roms = File(dir, "roms").also { it.mkdirs() }
            File(roms, "Tetris.gb").writeBytes(header("54455452495300000000000000000000", 0x01))
            File(roms, "Yellow.gb").writeBytes(header("504f4b454d4f4e2059454c4c4f570080", 0x33, "01").also { it[0x143] = 0x80.toByte() })
            File(roms, "Mine.gb").writeBytes(header("54455452495300000000000000000000", 0x01))
            pals.mkdirs()
            File(pals, "Mine.pal").writeText("[General]\nBackground0=1\n")       // the player's own: left alone
            fun put(f: File, text: String) { f.parentFile?.mkdirs(); f.writeText(text) }
            val games = listOf(GbLooks.Rom("Tetris", File(roms, "Tetris.gb"), GbLooks.COLOR), GbLooks.Rom("Yellow", File(roms, "Yellow.gb"), GbLooks.COLOR),
                GbLooks.Rom("Mine", File(roms, "Mine.gb"), ""))
            val first = GbLooks.write(home, slang, pals, games, ::put)
            first.finish()
            val setup = first.setup
            assertNotNull(setup)
            val root = File(home, "${Crt3d.DIR}/${GbLooks.DIR}")
            assertEquals(File(root, "Tetris/ Game Boy Color.slangp").path, setup!!.start["Tetris"])
            assertEquals(File(root, "Yellow/ Game Boy.slangp").path, setup.start["Yellow"])      // no Color look for it
            assertFalse("Mine" in setup.start)
            assertEquals(listOf("   Game Boy.slangp", "  Game Boy Pocket.slangp", " Game Boy Color.slangp"), File(root, "Tetris").list()!!.sorted())
            assertEquals(2, File(root, "Yellow").list()!!.size)
            assertEquals(GbLooks.codePal, File(pals, "Tetris.pal").readText())
            assertEquals("[General]\nBackground0=1\n", File(pals, "Mine.pal").readText())
            assertTrue(File(home, "${Crt3d.DIR}/${GbLooks.TRANSPARENCY}").readText().contains("uniform sampler2D GbFrame;"))
            assertEquals(File(root, "Game Boy.slangp").path, setup.folder)
            // A preset the player saved in a game's folder is theirs.
            File(root, "Yellow/my look.slangp").writeText("mine")
            // Left in another look: the folder turns, and the old names go, but only once the rest is written (finish).
            val turned = GbLooks.write(home, slang, pals, listOf(GbLooks.Rom("Tetris", File(roms, "Tetris.gb"), GbLooks.POCKET)), ::put)
            assertTrue(File(root, "Tetris/ Game Boy Color.slangp").exists() && File(pals, "Yellow.pal").exists())
            turned.finish()
            assertEquals(listOf("   Game Boy Color.slangp", "  Game Boy.slangp", " Game Boy Pocket.slangp"), File(root, "Tetris").list()!!.sorted())
            assertEquals(listOf("my look.slangp"), File(root, "Yellow").list()!!.toList())
            assertFalse(File(pals, "Yellow.pal").exists())
            assertEquals("Tetris.pal\nYellow.pal", File(root, GbLooks.LISTED).readText())   // listed until it is gone
            // A player's palette put where a code palette was is never taken away.
            File(pals, "Tetris.pal").writeText("[General]\nBackground0=2\n")
            assertTrue(GbLooks.write(home, slang, pals, listOf(GbLooks.Rom("Mine", File(roms, "Mine.gb"), "")), ::put).also { it.finish() }.setup == null)
            assertEquals("[General]\nBackground0=2\n", File(pals, "Tetris.pal").readText())
            File(pals, "Tetris.pal").delete()
            GbLooks.write(home, slang, pals, games, ::put).finish()
            assertTrue(File(root, "Tetris").isDirectory && File(root, "Game Boy.slangp").isFile)
            // Without the collection's presets, nothing of the looks is left; the player's own palette and preset stay.
            File(slang, "handheld/gameboy.slangp").delete()
            val none = GbLooks.write(home, slang, pals, games, ::put)
            assertNull(none.setup)
            none.finish()
            assertFalse(File(root, "Tetris").exists())
            assertFalse(File(root, "Game Boy.slangp").exists() || File(root, GbLooks.LISTED).exists())
            assertFalse(File(pals, "Tetris.pal").exists())
            assertTrue(File(pals, "Mine.pal").exists())
            assertEquals("mine", File(root, "Yellow/my look.slangp").readText())
        } finally { dir.deleteRecursively() }
    }

    private val p = RetroPack.Paths("/storage/emulated/0/Timeline/retroarch", "com.retroarch.aarch64", "/data/user/0/com.retroarch.aarch64", "/data/app/x/base.apk")

    @Test fun eachGameWithLooksStartsInItsOwnAndChangesWithL1AndR1() {
        val start = "/x/shaders/timeline/gb/Pokemon - Red Version (USA, Europe) (SGB Enhanced)/ Game Boy Pocket.slangp"
        val setup = GbLooks.Setup(mapOf("Pokemon - Red Version (USA, Europe) (SGB Enhanced)" to start), "/x/shaders/timeline/gb/Game Boy.slangp", emptyMap())
        val handheld = listOf("gb" to "Pokemon - Red Version (USA, Europe) (SGB Enhanced)")
        val files = RetroPack.files(Look(), p, handheld = handheld, gb = setup)
        val stem = "Pokemon - Red Version (USA, Europe) (SGB Enhanced)"
        assertEquals("#reference \"$start\"\n", files["Gambatte/$stem.slangp"])
        assertEquals("#reference \"/x/shaders/timeline/gb/Game Boy.slangp\"\n", files["Gambatte/gb.slangp"])
        val cfg = files.getValue("Gambatte/$stem.cfg")
        assertTrue("quit_press_twice = \"true\"\n" in cfg)                 // still: Pokémon resets with START and SELECT
        assertTrue("video_shader_dir = \"/x/shaders/timeline/gb/$stem\"\n" in cfg)
        assertTrue("input_shader_prev_btn = \"102\"\n" in cfg && "input_shader_next_btn = \"103\"\n" in cfg)
        val opt = files.getValue("Gambatte/$stem.opt")
        assertTrue("gambatte_gb_colorization = \"custom\"\n" in opt && "gambatte_gb_hwmode = \"GB\"\n" in opt)
        assertTrue(files.getValue("Gambatte/gb.opt").contains("gambatte_gb_colorization = \"disabled\""))
        // Another screen chosen for the handhelds: none of it.
        val grid = RetroPack.files(Look(handheld = "LCD grid"), p, handheld = handheld)
        assertFalse("Gambatte/$stem.slangp" in grid || "Gambatte/$stem.opt" in grid)
        assertEquals("quit_press_twice = \"true\"\n", grid["Gambatte/$stem.cfg"])
        // Except for a game RetroArch still has open, which is handed over alone and keeps its looks until it closes;
        // the core's own screen is the one chosen.
        val held = RetroPack.files(Look(handheld = "LCD grid"), p, handheld = handheld, gb = setup)
        assertEquals("#reference \"$start\"\n", held["Gambatte/$stem.slangp"])
        val heldOpt = held.getValue("Gambatte/$stem.opt")      // still drawn as a Game Boy in code colours, frames unmixed
        assertTrue("gambatte_gb_hwmode = \"GB\"\n" in heldOpt && "gambatte_mix_frames = \"disabled\"\n" in heldOpt &&
            "gambatte_gb_colorization = \"custom\"\n" in heldOpt)
        assertFalse(held["Gambatte/gb.slangp"].orEmpty().contains("/x/shaders/timeline/gb/"))
        // Without the looks, the original screen as before.
        assertTrue(RetroPack.files(Look(), p).getValue("Gambatte/gb.slangp").contains("handheld/gameboy.slangp"))
    }

    @Test fun aGamePageKnowsItsLooksBeforeAnySetup() {
        val dir = Files.createTempDirectory("expected").toFile()
        try {
            val pals = File(dir, "palettes").also { it.mkdirs() }
            val home = File(dir, "home").path
            val slang = collection(dir)
            File(dir, "Tetris.gb").writeBytes(header("54455452495300000000000000000000", 0x01))
            File(dir, "Yellow.gb").writeBytes(header("504f4b454d4f4e2059454c4c4f570080", 0x33, "01").also { it[0x143] = 0x80.toByte() })
            fun expected(name: String) = GbLooks.expected(File(dir, name), pals, home, slang)
            assertEquals(GbLooks.all, expected("Tetris.gb"))
            assertEquals(listOf(GbLooks.GAME_BOY, GbLooks.POCKET), expected("Yellow.gb"))
            File(pals, "Tetris.pal").writeText(GbLooks.codePal)                  // the launcher's own: still its looks
            assertEquals(GbLooks.all, expected("Tetris.gb"))
            File(pals, "Tetris.pal").writeText("[General]\nBackground0=1\n")    // the player's own: the original screen
            assertNull(expected("Tetris.gb"))
            File(slang, GbLooks.colorFiles.first()).delete()                     // the collection without what they are made from: none
            assertNull(expected("Yellow.gb"))
        } finally { dir.deleteRecursively() }
    }

    @Test fun theControlsNoteSaysWhatL1AndR1Do() {
        val note = ControlsNote.of("gb", "tetris", "ra:gambatte", true, null, screens = GbLooks.all)
        assertTrue(note.startsWith("L1 and R1 change the screen between Game Boy, Game Boy Pocket and Game Boy Color. START and SELECT"))
        assertTrue(ControlsNote.of("gb", "pokemon - yellow version", "ra:gambatte", true, null, screens = listOf(GbLooks.GAME_BOY, GbLooks.POCKET))
            .startsWith("L1 and R1 change the screen between Game Boy and Game Boy Pocket."))
        assertFalse("L1" in ControlsNote.of("gb", "tetris", "ra:gambatte", true, null))
    }
}
