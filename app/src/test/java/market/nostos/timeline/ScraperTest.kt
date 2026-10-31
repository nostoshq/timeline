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

class ScraperTest {
    private fun game(system: String, file: String) = Game(
        system = system, file = file, path = "/storage/emulated/0/ROMs/$system/$file",
        title = Titles.pretty(Titles.stem(file)), sort = Titles.sortKey(Titles.pretty(Titles.stem(file))),
        discs = emptyList(), region = Titles.region(Titles.stem(file)), fanTranslation = false, pal = false,
   )

    // ---- thumbnails -------------------------------------------------------------------------

    @Test fun thumbnailAddressMatchesTheServersNaming() {
        assertEquals(
            "https://thumbnails.libretro.com/Nintendo%20-%20Super%20Nintendo%20Entertainment%20System/Named_Boxarts/Candlewick%20Rising%20(USA).png",
            Thumbnails.url("snes", "covers", "Candlewick Rising (USA)"))
        assertEquals(
            "https://thumbnails.libretro.com/Sony%20-%20PlayStation/Named_Snaps/Lantern%20Road%20(USA).png",
            Thumbnails.url("psx", "screenshots", "Lantern Road (USA)"))
        assertNull(Thumbnails.url("arcade", "covers", "ptiger2f"))
        assertNull(Thumbnails.url("snes", "videos", "x"))
    }

    @Test fun awkwardCharactersBecomeUnderscores() {
        assertEquals("Wren _ Badger + Wren The Wanderer 3 (USA)", Thumbnails.clean("Wren & Badger + Wren The Wanderer 3 (USA)"))
        assertEquals("Harlan Voss's Knockdown!! (Japan, USA) (En) (Rev 1)", Thumbnails.clean("Harlan Voss's Knockdown!! (Japan, USA) (En) (Rev 1)"))
    }

    @Test fun candidatesFallBackFromTranslationsDiscsAndNewerTags() {
        val t = Thumbnails.candidates("Gaslight Manor (Japan) [T-En by Lamplight Works v1.01]")
        assertEquals("Gaslight Manor (Japan) [T-En by Lamplight Works v1.01]", t.first())
        assertTrue("Gaslight Manor (Japan)" in t)
        val d = Thumbnails.candidates("Tideglass (USA, Canada)")
        assertTrue("Tideglass (USA) (Disc 1)" in d)
        assertTrue(d.indexOf("Tideglass (USA, Canada)") < d.indexOf("Tideglass (USA)"))
        val r = Thumbnails.candidates("Mirefen (USA) (Rev 1)")
        assertTrue("Mirefen (USA)" in r)
        assertEquals(r.size, r.toSet().size)
    }

    @Test fun thumbnailsFetchTheChosenImageThenAScreenshot() {
        assertEquals(listOf("covers", "screenshots"), Thumbnails.wanted(ScrapeOptions(image = "Box 2D")))
        assertEquals(listOf("screenshots"), Thumbnails.wanted(ScrapeOptions(image = "Screenshot")))
        assertEquals(listOf("titlescreens", "screenshots"), Thumbnails.wanted(ScrapeOptions(image = "Title screen")))
        assertEquals("the server has no 3D boxes, so it falls back to flat ones",
            listOf("covers", "screenshots"), Thumbnails.wanted(ScrapeOptions(image = "Box 3D")))
    }

    // ---- Arcade Database, against the shape of its answers --------------------------------

    private val arcadeAnswer = """{"release":6,"result":[{"index":1,"url":"https://adb.arcadeitalia.net/?mame=ptiger2f",
        "game_name":"ptiger2f","title":"Paper Tiger II': Final Round (World 920101)","cloneof":"ptiger2",
        "manufacturer":"Brightwave","url_image_ingame":"https://adb.arcadeitalia.net/?mame=ptiger2f&type=ingame&resize=0",
        "url_image_title":"https://adb.arcadeitalia.net/?mame=ptiger2f&type=title&resize=0",
        "url_image_marquee":"https://adb.arcadeitalia.net/?mame=ptiger2f&type=marquee&resize=0",
        "url_image_cabinet":"https://adb.arcadeitalia.net/?mame=ptiger2f&type=cabinet&resize=0","url_image_flyer":"",
        "genre":"Fighter / Versus","players":2,"year":"1992","status":"GOOD","short_title":"Paper Tiger II': Final Round",
        "rate":100,"url_video_shortplay_hd":"https://adb.arcadeitalia.net/download_file.php?tipo=mame_current&codice=ptiger2f"}]}"""

    @Test fun arcadeDatabaseAnswerBecomesFactsAndPictures() {
        val f = ArcadeDb.parse(arcadeAnswer, ScrapeOptions())!!
        val m = f.meta!!
        assertEquals("Paper Tiger II': Final Round", m.name)
        assertEquals("1992", m.year)
        assertEquals("Brightwave", m.developer)
        assertEquals("Fighter / Versus", m.genre)
        assertEquals("2", m.players)
        assertEquals(1.0f, m.rating, 0.001f)
        val folders = f.media.map { it.first }
        assertEquals(listOf("screenshots", "titlescreens", "marquees"), folders)   // empty flyer skipped, video off
        assertTrue(ArcadeDb.parse(arcadeAnswer, ScrapeOptions(videos = true))!!.media.any { it.first == "videos" })
        assertFalse(ArcadeDb.parse(arcadeAnswer, ScrapeOptions(logo = "None"))!!.media.any { it.first == "marquees" })
        assertEquals(-1f, ArcadeDb.parse(arcadeAnswer, ScrapeOptions(ratings = false))!!.meta!!.rating, 0f)
    }

    @Test fun arcadeDatabaseEmptyOrBrokenAnswersAreNotFound() {
        assertNull(ArcadeDb.parse("""{"release":6,"result":[]}""", ScrapeOptions()))
        assertNull(ArcadeDb.parse("<html>busy</html>", ScrapeOptions()))
        assertTrue(ArcadeDb.url("ptiger2f").startsWith("https://adb.arcadeitalia.net/service_scraper.php?ajax=query_mame&game_name=ptiger2f"))
    }

    @Test fun everyCatalogSystemHasAScrapeSource() {
        for (s in Catalog.systems) {
            val covered = s.id == "arcade" || s.id in Thumbnails.folders
            assertTrue("${s.id} has no art source", covered)
        }
    }

    // ---- gamelist.xml ---------------------------------------------------------------------

    @Test fun gamelistIsWrittenInTheSharedFormatWithEscaping() {
        val dir = Files.createTempDirectory("gl").toFile()
        val file = File(dir, "gamelists/snes/gamelist.xml")
        GameList.write(file, mapOf(
            "Wren & Badger <3>.sfc" to Meta(name = "W & B", desc = "a < b", rating = 0.875f, date = "19941018T000000", developer = "Brightwave", genre = "Platform", players = "1-2"),
            "Blank.sfc" to Meta(),
       ))
        val x = file.readText()
        assertTrue(x.startsWith("<?xml version=\"1.0\"?>\n<gameList>"))
        assertTrue(x.contains("<path>./Wren &amp; Badger &lt;3&gt;.sfc</path>"))
        assertTrue(x.contains("<name>W &amp; B</name>"))
        assertTrue(x.contains("<desc>a &lt; b</desc>"))
        assertTrue(x.contains("<rating>0.87</rating>"))
        assertTrue(x.contains("<releasedate>19941018T000000</releasedate>"))
        assertTrue(x.contains("<path>./Blank.sfc</path>"))
        assertFalse("empty fields are left out", x.contains("<publisher>"))
        assertFalse(File(file.path + ".part").exists())
        dir.deleteRecursively()
    }

    @Test fun newFactsFillGapsAndNeverBlankWhatIsKnown() {
        val old = Meta(name = "Old", desc = "Kept", rating = 0.8f, developer = "Dev")
        val merged = GameList.merge(old, Meta(name = "New", publisher = "Pub"))
        assertEquals("New", merged.name)
        assertEquals("Kept", merged.desc)
        assertEquals(0.8f, merged.rating, 0f)
        assertEquals("Dev", merged.developer)
        assertEquals("Pub", merged.publisher)
        assertEquals(Meta(name = "x"), GameList.merge(null, Meta(name = "x")))
    }

    // ---- search -----------------------------------------------------------------------------

    @Test fun searchRanksExactThenPrefixThenWordThenAnywhere() {
        val games = listOf(
            game("snes", "Super Swiftmoor (Japan, USA) (En,Ja).sfc"), game("nes", "Swiftmoor (USA).nes"),
            game("gba", "Swiftmoor - Deep Seam (USA).gba"), game("gba", "Swiftmoor Ember (USA).gba"),
            game("snes", "Candlewick Rising (USA).sfc"), game("nes", "Seventh Lighthouse, The (USA) (Rev 1).nes"),
       )
        assertEquals(listOf("Swiftmoor", "Swiftmoor Ember", "Swiftmoor: Deep Seam", "Super Swiftmoor"), Search.find("swiftmoor", games).map { it.title })
        assertEquals(listOf("The Seventh Lighthouse"), Search.find("lighthouse", games).map { it.title })
        assertEquals(listOf("The Seventh Lighthouse"), Search.find("seventh", games).map { it.title })
        assertEquals(listOf("Swiftmoor: Deep Seam"), Search.find("seam swiftmoor", games).map { it.title })
        assertEquals(listOf("Candlewick Rising"), Search.find("  RISI ", games).map { it.title })
        assertTrue(Search.find("", games).isEmpty())
        assertTrue(Search.find("xyzzy", games).isEmpty())
    }
}
