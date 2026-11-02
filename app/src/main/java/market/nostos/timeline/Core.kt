// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 NOSTOS, LLC

package market.nostos.timeline

import java.io.File
import java.util.Locale

// ---------------------------------------------------------------------------------------------
// Consoles, in the order they arrived. The first emulator listed for each is the one Timeline sets
// up and recommends; the others are used when they are installed and chosen in Settings, Emulators.
// ---------------------------------------------------------------------------------------------

data class SystemDef(
    val id: String,
    val name: String,
    val poster: String,
    val year: Int,
    val exts: Set<String>,
    val emulators: List<String>,
)

private fun sys(id: String, name: String, poster: String, year: Int, exts: String, vararg emus: String) =
    SystemDef(id, name, poster, year, exts.split(" ").toSet(), emus.toList())

object Catalog {
    val systems: List<SystemDef> = listOf(
        sys("atari2600", "Atari 2600", "Atari 2600", 1977, "a26 bin zip 7z", "ra:stella"),
        sys("arcade", "Arcade", "Arcade", 1978, "zip 7z", "ra:fbneo", "ra:mame2003_plus"),
        sys("nes", "NES", "NES", 1985, "nes fds unf zip 7z", "ra:nestopia"),
        sys("mastersystem", "Master System", "Master\nSystem", 1986, "sms zip 7z", "ra:genesis_plus_gx"),
        sys("tg16", "TurboGrafx-16", "Turbo\nGrafx-16", 1989, "pce sgx zip 7z", "ra:mednafen_pce"),
        sys("genesis", "Genesis", "Genesis", 1989, "md gen smd zip 7z", "ra:genesis_plus_gx"),
        sys("gb", "Game Boy", "Game Boy", 1989, "gb zip 7z", "ra:gambatte", "ra:mgba"),
        sys("tg-cd", "TurboGrafx-CD", "Turbo\nGrafx-CD", 1989, "chd cue m3u", "ra:mednafen_pce"),
        sys("scummvm", "ScummVM", "ScummVM", 1990, "scummvm svm", "ra:scummvm", "scummvm"),
        sys("snes", "Super Nintendo", "Super\nNintendo", 1991, "sfc smc zip 7z", "ra:snes9x"),
        sys("gamegear", "Game Gear", "Game Gear", 1991, "gg zip 7z", "ra:genesis_plus_gx"),
        sys("segacd", "Sega CD", "Sega CD", 1992, "chd cue m3u", "ra:genesis_plus_gx"),
        sys("doom", "DOOM", "DOOM", 1993, "wad iwad pwad", "ra:prboom"),
        sys("psx", "PlayStation", "PlayStation", 1995, "chd cue m3u pbp iso", "ra:swanstation", "ra:mednafen_psx_hw", "duckstation"),
        sys("saturn", "Saturn", "Saturn", 1995, "chd cue m3u", "ra:mednafen_saturn", "yabasanshiro2", "ra:yabasanshiro"),
        sys("n64", "Nintendo 64", "Nintendo 64", 1996, "z64 n64 v64 zip 7z", "ra:mupen64plus_next_gles3", "m64plusfz"),
        sys("quake", "Quake", "Quake", 1996, "pak", "ra:tyrquake"),
        sys("gbc", "Game Boy Color", "Game Boy\nColor", 1998, "gbc gb zip 7z", "ra:gambatte", "ra:mgba"),
        sys("dreamcast", "Dreamcast", "Dreamcast", 1999, "chd gdi cue cdi m3u", "ra:flycast", "flycast", "redream"),
        sys("ps2", "PlayStation 2", "PlayStation 2", 2000, "chd iso cso m3u", "armsx2"),
        sys("gba", "Game Boy Advance", "Game Boy\nAdvance", 2001, "gba zip 7z", "ra:mgba"),
        sys("gc", "GameCube", "GameCube", 2001, "rvz iso gcz ciso gcm wia m3u", "dolphin"),
        sys("xbox", "Xbox", "Xbox", 2001, "iso xiso", "hakux"),
        sys("nds", "Nintendo DS", "Nintendo DS", 2004, "nds zip 7z", "ra:melondsds", "melonds", "drastic"),
        sys("psp", "PSP", "PSP", 2005, "chd iso cso pbp", "ppsspp"),
        sys("wii", "Wii", "Wii", 2006, "rvz iso wbfs wad gcz ciso wia m3u", "dolphin"),
        sys("n3ds", "Nintendo 3DS", "Nintendo 3DS", 2011, "3ds cci cxi zcci", "azahar"),
        sys("psvita", "PS Vita", "PS Vita", 2011, "psvita", "vita3k"),
        sys("wiiu", "Wii U", "Wii U", 2012, "wua wud wux rpx", "cemu"),
   )

    fun system(id: String): SystemDef? = systems.firstOrNull { it.id == id }
}

// ---------------------------------------------------------------------------------------------
// Emulators and how each one expects to be handed a game. These mirror ES-DE's Android launch
// rules (resources/systems/android in the ES-DE repository), which are the tested reference.
// ---------------------------------------------------------------------------------------------

data class Emu(
    val id: String,
    val label: String,
    val components: List<String>,
    val action: String? = null,
    val category: String? = null,
    val clear: Boolean = false,
    val data: String? = null,
    val extras: Map<String, String> = emptyMap(),
    val bools: Map<String, Boolean> = emptyMap(),
    val arrays: Map<String, List<String>> = emptyMap(),
    val setup: String = "",
    val leave: String = "",            // how a game is closed from inside this emulator, where that is known
)

private const val VIEW = "android.intent.action.VIEW"
private const val MAIN = "android.intent.action.MAIN"
private const val FOLDER = "Inside it: give it access to this console's folder in ROMs."

object Emulators {
    private val retroArchComponents = listOf(
        "com.retroarch.aarch64/com.retroarch.browser.retroactivity.RetroActivityFuture",
        "com.retroarch.ra32/com.retroarch.browser.retroactivity.RetroActivityFuture",
        "com.retroarch/com.retroarch.browser.retroactivity.RetroActivityFuture",
   )

    private val coreNames = mapOf(
        "stella" to "Stella", "fbneo" to "FinalBurn Neo", "mame2003_plus" to "MAME 2003-Plus",
        "nestopia" to "Nestopia", "genesis_plus_gx" to "Genesis Plus GX", "mednafen_pce" to "Beetle PCE",
        "gambatte" to "Gambatte", "mgba" to "mGBA", "scummvm" to "ScummVM", "snes9x" to "Snes9x",
        "prboom" to "PrBoom", "swanstation" to "SwanStation", "mednafen_psx_hw" to "Beetle PSX HW",
        "mednafen_saturn" to "Beetle Saturn", "yabasanshiro" to "YabaSanshiro",
        "mupen64plus_next_gles3" to "Mupen64Plus-Next", "tyrquake" to "TyrQuake", "flycast" to "Flycast",
        "melondsds" to "melonDS DS",
   )

    private val standalone: Map<String, Emu> = listOf(
        Emu("duckstation", "DuckStation", listOf("com.github.stenzek.duckstation/.EmulationActivity"),
            clear = true, extras = mapOf("bootPath" to "%ROMSAF%"), bools = mapOf("resumeState" to false), setup = FOLDER),
        Emu("dolphin", "Dolphin", listOf("org.dolphinemu.dolphinemu/.ui.main.TvMainActivity"),
            action = MAIN, category = "android.intent.category.LEANBACK_LAUNCHER",
            extras = mapOf("AutoStartFile" to "%ROMSAF%"),
            setup = "Inside it: its question about usage statistics is your choice. Add Games and pick ROMs/gc, then Add Games again for ROMs/wii (go back one folder to ROMs first). Then the Home button. Its other settings are the next step.",
            leave = "press Back, then Exit Emulation"),
        Emu("ppsspp", "PPSSPP", listOf("org.ppsspp.ppssppgold/org.ppsspp.ppsspp.PpssppActivity", "org.ppsspp.ppsspp/.PpssppActivity"),
            action = VIEW, category = "android.intent.category.DEFAULT", data = "%ROMSAF%",
            setup = "Inside it: add the psp folder with its Browse button."),
        Emu("melonds", "melonDS", listOf("me.magnum.melonds/.ui.emulator.EmulatorActivity", "me.magnum.melondualds/me.magnum.melonds.ui.emulator.EmulatorActivity"),
            // As an address inside the folder it was given ("uri"). Its plain path ("PATH") finds a game on the device's
            // own storage but not on a memory card ("Could not find ROM"), and an address without the folder in it is
            // refused too. melonDS reads "uri" before "PATH", and "PATH" wins if both are sent, so only one is.
            action = "me.magnum.melonds.LAUNCH_ROM", extras = mapOf("uri" to "%ROMSAF%"),
            setup = "Inside it: Set ROM Directory and pick ROMs/nds. To find the SD card in Android's folder picker, open the menu at its top left."),
        Emu("drastic", "DraStic", listOf("com.dsemu.drastic/.DraSticActivity"), clear = true, data = "%ROMSAF%", setup = FOLDER),
        Emu("armsx2", "ARMSX2", listOf(
            "come.nanodata.armsx2/com.armsx2.MainActivity", "com.armsx2/.MainActivity", "com.armsx2.nightly/com.armsx2.MainActivity",
            "come.nanodata.armsx2/kr.co.iefriends.pcsx2.MainActivity", "come.nanodata.armsx2.debug/kr.co.iefriends.pcsx2.MainActivity"),
            action = VIEW, data = "%ROMSAF%",
            setup = "Inside it: Next, Next, then Select BIOS and pick ROMs/bios/ps2, then Pick ROMs Folder and pick ROMs/ps2, then Let's Go. In its settings, Vulkan and 2x resolution suit most handhelds."),
        Emu("flycast", "Flycast", listOf("com.flycast.emulator/com.flycast.emulator.MainActivity", "com.flycast.emulator/com.reicast.emulator.MainActivity"),
            action = VIEW, data = "%ROMSAF%", setup = FOLDER),
        Emu("redream", "Redream", listOf("io.recompiled.redream/.MainActivity"), action = VIEW, data = "%ROMSAF%", setup = FOLDER),
        Emu("m64plusfz", "M64Plus FZ", listOf(
            "org.mupen64plusae.v3.fzurita.amazon/paulscode.android.mupen64plusae.SplashActivity",
            "org.mupen64plusae.v3.fzurita.pro/paulscode.android.mupen64plusae.SplashActivity",
            "org.mupen64plusae.v3.fzurita/paulscode.android.mupen64plusae.SplashActivity"),
            action = VIEW, data = "%ROMSAF%", setup = FOLDER),
        Emu("yabasanshiro2", "Yaba Sanshiro 2", listOf(
            "org.devmiyax.yabasanshioro2.pro/org.uoyabause.android.Yabause", "org.devmiyax.yabasanshioro2/org.uoyabause.android.Yabause"),
            action = VIEW, clear = true, extras = mapOf("org.uoyabause.android.FileNameUri" to "%ROMSAF%"),
            setup = "Only the Pro version accepts a game from a launcher, and only as CHD."),
        Emu("hakux", "hakuX", listOf("com.rfandango.haku_x/.LauncherActivity"), action = VIEW, data = "%ROMSAF%",
            setup = "Four screens: the MCPX ROM, the Flash ROM and the hard disk image (from your own Xbox), then Games Folder: pick ROMs/xbox. Use this folder, Allow, Finish, Home button."),
        Emu("azahar", "Azahar", listOf(
            "org.azahar_emu.azahar/org.citra.citra_emu.activities.EmulationActivity",
            "io.github.lime3ds.android/org.citra.citra_emu.activities.EmulationActivity"),
            clear = true, data = "%ROMSAF%", setup = FOLDER),
        Emu("cemu", "Cemu", listOf("info.cemu.cemu/info.cemu.cemu.emulation.EmulationActivity", "info.cemu.Cemu/info.cemu.Cemu.emulation.EmulationActivity"),
            data = "%ROMSAF%", setup = FOLDER),
        Emu("vita3k", "Vita3K", listOf("org.vita3k.emulator/.Emulator", "org.vita3k.emulator.ikhoeyZX/org.vita3k.emulator.Emulator"),
            arrays = mapOf("AppStartParameters" to listOf("-r", "%FILETEXT%"))),
        Emu("scummvm", "ScummVM", listOf("org.scummvm.scummvm/org.scummvm.scummvm.SplashActivity"), action = MAIN, data = "%FILETEXT%"),
   ).associateBy { it.id }

    /** A RetroArch core's name as its Core Downloader lists it. */
    fun coreName(core: String): String = coreNames[core] ?: core

    fun get(id: String): Emu? {
        if (id.startsWith("ra:")) {
            val core = id.removePrefix("ra:")
            return Emu(
                id, "RetroArch · " + (coreNames[core] ?: core), retroArchComponents,
                extras = mapOf(
                    "CONFIGFILE" to "/storage/emulated/0/Android/data/%PKG%/files/retroarch.cfg",
                    "LIBRETRO" to "/data/user/0/%PKG%/cores/${core}_libretro_android.so",
                    "ROM" to "%ROM%",
               ),
                setup = "In RetroArch: Main Menu, Online Updater, Core Downloader, then pick ${coreNames[core] ?: core}.",
           )
        }
        return standalone[id]
    }

    val all: List<String> get() = Catalog.systems.flatMap { it.emulators }.distinct()
}

/** Everything needed to start a game, as plain values so it can be checked without a device. */
data class LaunchPlan(
    val emulator: String,
    val pkg: String,
    val cls: String,
    val installed: Boolean,
    val action: String?,
    val category: String?,
    val clear: Boolean,
    val data: String?,
    val extras: Map<String, String>,
    val bools: Map<String, Boolean>,
    val arrays: Map<String, List<String>>,
    val grantRead: Boolean,
) {
    fun describe(): String = buildString {
        append(pkg).append('/').append(cls)
        action?.let { append("\naction ").append(it) }
        category?.let { append("\ncategory ").append(it) }
        data?.let { append("\ndata ").append(it) }
        extras.forEach { (k, v) -> append('\n').append(k).append(" = ").append(v) }
        bools.forEach { (k, v) -> append('\n').append(k).append(" = ").append(v) }
        arrays.forEach { (k, v) -> append('\n').append(k).append(" = ").append(v.joinToString(" ")) }
    }
}

object Launch {
    /** Back in the launcher this soon after a start, and the game never opened. */
    const val CLOSED_AT_ONCE_MS = 7000L
    // Dolphin builds its shaders before a game starts, and the Nova's Vulkan driver failed with GameCube games two to
    // ten seconds in (its crash records): back within this long, a GameCube or Wii game did not start.
    const val DOLPHIN_FAILED_MS = 20_000L

    /** Percent-encodes like android.net.Uri.encode: everything except unreserved characters. */
    fun encode(s: String): String {
        val keep = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_-!.~'()*"
        val out = StringBuilder()
        for (b in s.toByteArray(Charsets.UTF_8)) {
            val c = b.toInt() and 0xFF
            if (c < 128 && keep.indexOf(c.toChar()) >= 0) out.append(c.toChar())
            else out.append('%').append("0123456789ABCDEF"[c shr 4]).append("0123456789ABCDEF"[c and 15])
        }
        return out.toString()
    }

    /**
     * The storage-framework address of a file, in the form an emulator can open once it has been given the system's
     * folder (ROMs/gc, ROMs/ps2 and so on): the folder it was given, then the file inside it.
     *   /storage/1A2B-3C4D/ROMs/gc/Game.rvz  ->  .../tree/1A2B-3C4D%3AROMs%2Fgc/document/1A2B-3C4D%3AROMs%2Fgc%2FGame.rvz
     * The shorter form with no folder in it looks right and is refused: tried in Dolphin on Android 13, which answers
     * "does not exist" to it and starts the game from this one.
     */
    /**
     * The discs of a multi-disc game in the order an emulator should take them: the one being started first, then the
     * ones after it, then those before. Dolphin moves to the next in this list each time the game asks for a disc.
     */
    fun discSet(start: String, discs: List<String>): List<String> {
        val at = discs.indexOf(start)
        return if (at < 0) listOf(start) + discs else discs.drop(at) + discs.take(at)
    }

    fun romSaf(path: String): String {
        val p = path.replace("//", "/")
        val (volume, rel) = when {
            p.startsWith("/storage/emulated/") -> "primary" to p.removePrefix("/storage/emulated/").substringAfter('/', "")
            p.startsWith("/sdcard/") -> "primary" to p.removePrefix("/sdcard/")
            p.startsWith("/storage/") -> p.removePrefix("/storage/").let { it.substringBefore('/') to it.substringAfter('/', "") }
            p.startsWith("/mnt/media_rw/") -> p.removePrefix("/mnt/media_rw/").let { it.substringBefore('/') to it.substringAfter('/', "") }
            else -> "primary" to p.trimStart('/')
        }
        val base = "content://com.android.externalstorage.documents"
        val parts = rel.split('/')
        // The system's folder is the one just inside the games folder; failing a folder by that name, the second level down.
        val games = parts.indexOfFirst { it.equals("ROMs", ignoreCase = true) }
        val depth = if (games >= 0) games + 2 else 2
        if (parts.size <= depth) return "$base/document/" + encode("$volume:$rel")
        return "$base/tree/" + encode("$volume:" + parts.take(depth).joinToString("/")) + "/document/" + encode("$volume:$rel")
    }

    private fun component(spec: String): Pair<String, String> {
        val pkg = spec.substringBefore('/')
        val cls = spec.substringAfter('/')
        return pkg to (if (cls.startsWith(".")) pkg + cls else cls)
    }

    fun plan(
        emulatorId: String,
        romPath: String,
        installed: (String) -> Boolean,
        fileText: (String) -> String = { "" },
        providerUri: (String) -> String = { it },
   ): LaunchPlan? {
        val emu = Emulators.get(emulatorId) ?: return null
        val specs = emu.components.map(::component)
        val hit = specs.firstOrNull { installed(it.first) }
        val (pkg, cls) = hit ?: specs.first()
        var grant = false
        fun fill(v: String): String = when {
            v == "%ROMSAF%" -> romSaf(romPath)
            v == "%ROMPROVIDER%" -> { grant = true; providerUri(romPath) }
            v == "%FILETEXT%" -> fileText(romPath)
            else -> v.replace("%ROM%", romPath).replace("%PKG%", pkg)
        }
        return LaunchPlan(
            emulator = emu.label, pkg = pkg, cls = cls, installed = hit != null,
            action = emu.action, category = emu.category, clear = emu.clear,
            data = emu.data?.let(::fill),
            extras = emu.extras.mapValues { fill(it.value) },
            bools = emu.bools,
            arrays = emu.arrays.mapValues { e -> e.value.map(::fill) },
            grantRead = grant,
       )
    }
}

// ---------------------------------------------------------------------------------------------
// The library: what is actually in the ROMs folder.
// ---------------------------------------------------------------------------------------------

data class Game(
    val system: String,
    val file: String,          // entry name in the system folder, the key for scraped data
    val path: String,          // what gets handed to the emulator
    val title: String,
    val sort: String,
    val discs: List<String>,   // disc file paths when there is more than one
    val region: String,
    val fanTranslation: Boolean,
    val pal: Boolean,
) {
    val key: String get() = "$system/$file"
    val stem: String get() = Titles.stem(file)
    val base: String get() = Titles.base(file)
}

object Titles {
    private val tags = Regex("\\s*[\\(\\[][^\\)\\]]*[\\)\\]]")
    private val article = Regex("^(.*?), (The|A|An)( - (.*))?$")
    private val knownExt = Regex("\\.[A-Za-z0-9]{1,7}$")

    private val doubled = Regex("\\.(xiso|nkit)$", RegexOption.IGNORE_CASE)
    private val mode = Regex("\\(((?:Arcade|Simulation) Mode)\\)")

    fun stem(file: String): String = doubled.replace(knownExt.replace(file, ""), "")

    /** Lower-case title before the first tag, used to match games across lists. */
    fun base(file: String): String = stem(file).substringBefore(" (").substringBefore(" [").trim().lowercase()

    /** "Legend of Zelda, The - A Link to the Past (USA)" -> "The Legend of Zelda: A Link to the Past" */
    fun pretty(stem: String): String {
        var t = tags.replace(stem, "").trim()
        // One game sold as two discs that each start by themselves: say which disc this is.
        val disc = mode.find(stem)?.groupValues?.get(1)
        article.find(t)?.let { m ->
            val rest = m.groupValues[4]
            t = m.groupValues[2] + " " + m.groupValues[1] + if (rest.isNotEmpty()) ": $rest" else ""
        }
        t = t.replace(" - ", ": ")
        t = t.replace(pokemonVersion, "Pokémon $1").replace(pokemon, "Pokémon")
        t = known[t] ?: t
        return if (disc != null) "$t: $disc" else t
    }

    /** "Pokemon: Red Version" is "Pokémon Red Version" on the box. */
    private val pokemonVersion = Regex("^Pokemon: (\\w+ Version)")
    private val pokemon = Regex("\\bPokemon\\b")

    /** Names where the file's spelling is not the one on the box (checked game by game). */
    private val known: Map<String, String> = mapOf(
        "1080 Snowboarding" to "1080° Snowboarding", "Def Jam: Fight For NY" to "Def Jam: Fight for NY",
        "Disney-Pixar A Bug's Life" to "A Bug's Life", "Nickelodeon Rugrats: Search for Reptar" to "Rugrats: Search for Reptar",
        "Einhaender" to "Einhänder",
        "Kirby & The Amazing Mirror" to "Kirby & the Amazing Mirror", "Mega Man ZX: Advent" to "Mega Man ZX Advent",
        "MUSHA: Metallic Uniframe Super Hybrid Armor" to "M.U.S.H.A.",
        "Nights into Dreams..." to "NiGHTS into Dreams...", "Ookami" to "Ōkami",
        "Phoenix Wright: Ace Attorney: Justice for All" to "Phoenix Wright: Ace Attorney – Justice for All",
        "Phoenix Wright: Ace Attorney: Trials and Tribulations" to "Phoenix Wright: Ace Attorney – Trials and Tribulations",
        "Pitfall!: Pitfall Harry's Jungle Adventure" to "Pitfall!",
        "Resident Evil: Code: Veronica" to "Resident Evil Code: Veronica",
        "Sonic & Knuckles + Sonic The Hedgehog 3" to "Sonic 3 & Knuckles", "Sonic The Hedgehog" to "Sonic the Hedgehog",
        "Sonic The Hedgehog 2" to "Sonic the Hedgehog 2", "SoulCalibur II" to "Soulcalibur II",
        "Spider-Man: Venom: Maximum Carnage" to "Spider-Man and Venom: Maximum Carnage",
        "Super Ghouls 'N Ghosts" to "Super Ghouls'n Ghosts",
        "Tsumi to Batsu: Hoshi no Keishousha" to "Sin and Punishment", "WCW-nWo Revenge" to "WCW/nWo Revenge",
   )

    private val marks = Regex("\\p{Mn}+")

    /** Lower case with the accents taken off, so "pokemon" finds "Pokémon" and "Ōkami" files under O. */
    fun fold(s: String): String = marks.replace(java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD), "").lowercase()

    private val numerals = mapOf("i" to 1, "ii" to 2, "iii" to 3, "iv" to 4, "v" to 5, "vi" to 6, "vii" to 7, "viii" to 8,
        "ix" to 9, "x" to 10, "xi" to 11, "xii" to 12, "xiii" to 13)
    private val token = Regex("[a-z0-9]+")
    private val xSeries = Regex("x(\\d{1,3})")

    /**
     * The order titles are listed in. After the first word, a number or Roman numeral sorts by its value, just after
     * the same title without one: Final Fantasy VII, VIII, IX before Tactics; X before XII; Wario Land II before 3;
     * The Final Fantasy Legend DX before II DX. The first word is left as it is ("X-Men", "1943").
     */
    fun sortKey(title: String): String {
        var first = true
        return token.replace(fold(title).removePrefix("the ").removePrefix("a ").trim()) { m ->
            val w = m.value
            val n = if (first) null else numerals[w] ?: w.takeIf { it.length <= 4 && it.all(Char::isDigit) }?.toInt()
            val series = if (first) null else xSeries.matchEntire(w)?.groupValues?.get(1)
            first = false
            when {
                n != null -> "i" + n.toString().padStart(4, '0')
                series != null -> "i0010 i" + series.padStart(4, '0')     // Mega Man X2 right after Mega Man X
                else -> w
            }
        }
    }

    /** A title set large over two or three lines: a dash stays at the end of a line and never starts the next. */
    fun forLines(title: String): String = title.replace(" – ", " – ")

    fun region(stem: String): String {
        val first = Regex("\\(([^)]*)\\)").find(stem)?.groupValues?.get(1) ?: return ""
        return if (listOf("USA", "Europe", "Japan", "World").any { first.contains(it) }) first else ""
    }

    val arcade: Map<String, String> = mapOf(
        "pacman" to "Pac-Man", "mspacman" to "Ms. Pac-Man", "galaga" to "Galaga", "dkong" to "Donkey Kong",
        "invaders" to "Space Invaders", "defender" to "Defender", "robotron" to "Robotron: 2084", "digdug" to "Dig Dug",
        "joust" to "Joust", "asteroid" to "Asteroids", "bublbobl" to "Bubble Bobble", "outrun" to "Out Run",
        "sharrier" to "Space Harrier", "rtype" to "R-Type", "shinobi" to "Shinobi", "1943" to "1943: The Battle of Midway",
        "rampage" to "Rampage", "ffight" to "Final Fight", "strider" to "Strider", "ghouls" to "Ghouls'n Ghosts",
        "sf2hf" to "Street Fighter II': Hyper Fighting", "ssf2t" to "Super Street Fighter II Turbo",
        "sfa3" to "Street Fighter Alpha 3", "vsav" to "Vampire Savior: The Lord of Vampire", "xmvsf" to "X-Men vs. Street Fighter",
        "mvsc" to "Marvel vs. Capcom: Clash of Super Heroes", "spf2t" to "Super Puzzle Fighter II Turbo", "dino" to "Cadillacs and Dinosaurs",
        "punisher" to "The Punisher", "avsp" to "Alien vs. Predator", "ddsom" to "Dungeons & Dragons: Shadow over Mystara",
        "mk2" to "Mortal Kombat II", "umk3" to "Ultimate Mortal Kombat 3", "nbajamte" to "NBA Jam Tournament Edition",
        "tmnt" to "Teenage Mutant Ninja Turtles", "simpsons" to "The Simpsons", "xmen" to "X-Men",
        "tmnt2po" to "Teenage Mutant Ninja Turtles", "simpsons2p" to "The Simpsons", "xmen2pu" to "X-Men", "xmen2pa" to "X-Men",
        "tmnt22pu" to "Teenage Mutant Ninja Turtles: Turtles in Time", "tmnt2pj" to "Teenage Mutant Ninja Turtles: Turtles in Time",
        "ssriders" to "Sunset Riders", "ssridersubc" to "Sunset Riders", "mslug" to "Metal Slug", "mslugx" to "Metal Slug X",
        "mslug3" to "Metal Slug 3", "kof98" to "The King of Fighters '98", "kof2002" to "The King of Fighters 2002",
        "garou" to "Garou: Mark of the Wolves", "samsho2" to "Samurai Shodown II", "lastbld2" to "The Last Blade 2",
        "wjammers" to "Windjammers", "turfmast" to "Neo Turf Masters", "blazstar" to "Blazing Star",
        "shocktro" to "Shock Troopers", "magdrop3" to "Magical Drop III", "pbobblen" to "Bust-A-Move",
        "ddonpach" to "DoDonPachi", "s1945ii" to "Strikers 1945 II", "ckong" to "Crazy Kong", "bubbles" to "Bubbles",
        "1941" to "1941: Counter Attack",
   )
}

object Library {
    private val bios = setOf("neogeo", "pgm", "decocass", "qsound", "skns", "ym2608")
    private val consoles50 = setOf("nes", "snes", "genesis", "mastersystem", "segacd", "psx", "saturn", "n64", "dreamcast", "ps2", "gc", "wii")

    private fun game(system: String, entry: File, launch: File, discs: List<File>): Game {
        val stem = Titles.stem(entry.name)
        val title = if (system == "arcade") Titles.arcade[stem.lowercase()] ?: stem else Titles.pretty(stem)
        val region = Titles.region(stem)
        return Game(
            system = system, file = entry.name, path = launch.absolutePath, title = title,
            sort = Titles.sortKey(title), discs = discs.map { it.absolutePath }, region = region,
            fanTranslation = stem.contains("[T-En"),
            pal = system in consoles50 && region.contains("Europe") && !region.contains("USA"),
       )
    }

    /**
     * Other names a console's folder goes by, so a library laid out for ES-DE, RetroBat or a Retroid's own front end is
     * found as it is. Matched without regard to case; a console's own name comes first.
     */
    val aliases: Map<String, List<String>> = mapOf(
        "atari2600" to listOf("2600", "atari"), "arcade" to listOf("fbneo", "mame", "fba"),
        "nes" to listOf("famicom", "fc"), "mastersystem" to listOf("sms", "segamastersystem"),
        "tg16" to listOf("pcengine", "pce", "turbografx16", "turbografx"), "genesis" to listOf("megadrive", "md"),
        "gb" to listOf("gameboy"), "tg-cd" to listOf("pcenginecd", "tgcd", "turbografxcd"),
        "snes" to listOf("sfc", "superfamicom", "supernintendo"), "gamegear" to listOf("gg"), "segacd" to listOf("megacd", "scd"),
        "psx" to listOf("ps1", "playstation"), "saturn" to listOf("segasaturn"), "n64" to listOf("nintendo64"),
        "gbc" to listOf("gameboycolor"), "dreamcast" to listOf("dc"), "ps2" to listOf("playstation2"),
        "gba" to listOf("gameboyadvance"), "gc" to listOf("gamecube", "ngc"), "nds" to listOf("ds", "nintendods"),
        "n3ds" to listOf("3ds"), "psvita" to listOf("vita"),
    )

    /** The folders holding a console's games: its own name, then any of its other names, as spelled on the card. */
    fun folders(root: File, def: SystemDef): List<File> {
        val names = (listOf(def.id) + aliases[def.id].orEmpty()).map { it.lowercase() }
        val present = root.listFiles()?.filter { it.isDirectory }.orEmpty()
        return names.mapNotNull { n -> present.firstOrNull { it.name.lowercase() == n } }.distinct()
    }

    fun scanSystem(root: File, def: SystemDef): List<Game> {
        val entries = folders(root, def).flatMap { it.listFiles()?.toList().orEmpty() }
        if (entries.isEmpty()) return emptyList()
        val out = ArrayList<Game>()
        val files = ArrayList<File>()
        for (e in entries) {
            if (e.name.startsWith(".")) continue
            val ext = e.name.substringAfterLast('.', "").lowercase()
            if (e.isDirectory) {
                // A folder named like a file ("Xenogears (USA).m3u") is one multi-disc game.
                if (ext !in def.exts) continue
                val inside = e.listFiles()?.filter { !it.name.startsWith(".") && it.isFile } ?: continue
                val discs = inside.filter { it.extension.lowercase().let { x -> x in def.exts && x != "m3u" } }.sortedBy { it.name }
                val list = inside.firstOrNull { it.name == e.name } ?: inside.firstOrNull { it.extension.lowercase() == "m3u" }
                val launch = list ?: discs.firstOrNull() ?: continue
                out += game(def.id, e, launch, if (discs.size > 1) discs else emptyList())
            } else if (ext in def.exts) {
                if (def.id == "arcade" && Titles.stem(e.name).lowercase() in bios) continue
                files += e
            }
        }
        // A playlist beside its discs is the game, and the discs it names are not games of their own.
        val named = HashSet<String>()
        for (m in files.filter { it.extension.equals("m3u", true) }) {
            val discs = runCatching { Discs.listed(m.readText()) }.getOrDefault(emptyList())
                .map { File(it).let { f -> if (f.isAbsolute) f else File(m.parentFile, it) } }
            discs.forEach { named += Discs.same(it) }
            val there = discs.filter { it.isFile }
            out += game(def.id, m, m, if (there.size > 1) there else emptyList())
        }
        // Discs without a playlist ("(Disc 1)", "(Disc 2)") are one game, started from the first.
        val loose = files.filter { !it.extension.equals("m3u", true) && Discs.same(it) !in named }
        val (numbered, single) = loose.partition { Discs.number(it.name) != null }
        single.forEach { out += game(def.id, it, it, emptyList()) }
        numbered.groupBy { Discs.shared(it.name).lowercase() }.values.forEach { set ->
            val discs = set.sortedBy { Discs.number(it.name) ?: 0 }
            out += game(def.id, discs.first(), discs.first(), if (discs.size > 1) discs else emptyList())
        }
        return out.sortedBy { it.sort }
    }

    fun scan(root: File): Map<String, List<Game>> {
        val map = LinkedHashMap<String, List<Game>>()
        for (def in Catalog.systems) {
            val games = scanSystem(root, def)
            if (games.isNotEmpty()) map[def.id] = games
        }
        return map
    }
}

/** The short note setup leaves in each folder it makes, saying what goes there. */
object FolderNotes {
    const val NAME = "what goes here.txt"
    private val discTypes = setOf("chd", "cue", "gdi", "rvz", "iso")

    fun console(def: SystemDef): String = buildString {
        val first = def.emulators.firstOrNull().orEmpty()
        val opens = if (first.startsWith("ra:")) "RetroArch, with its ${Emulators.coreName(first.removePrefix("ra:"))} core"
            else Emulators.get(first)?.label ?: first
        append("${def.name}, ${def.year}\n\n")
        append("Put ${def.name} games here, one file each: ${def.exts.joinToString(" ") { ".$it" }}\n")
        append("They open in $opens.\n")
        if (def.exts.any { it in discTypes })
            append("A game on more than one disc: keep its discs together, named (Disc 1), (Disc 2) and so on, and it shows once.\n")
        ConsoleFiles.needs.firstOrNull { it.system == def.id }?.let { n ->
            val note = n.files.joinToString(" ") { it.note }
            append(if (n.optional) (if (note.startsWith("Optional")) note else "Optional: $note") else "Also needed: $note")
            if (n.folder.startsWith("bios") && def.id != "ps2") append(" It goes in the ${n.folder} folder.")
            append("\n")
        }
        Library.aliases[def.id]?.let { append("Timeline also finds this console's games in folders named ${it.joinToString(", ")}.\n") }
        append("\nWritten by Timeline. Safe to delete.\n")
    }

    fun bios(): String = buildString {
        append("Files from the consoles themselves\n\n")
        append("Timeline never includes these. Copy them from consoles you own, as this guide explains:\n${Links.CONSOLE_FILES}\n")
        append("Setup's Console files step checks each one you add.\n\n")
        for (n in ConsoleFiles.needs) {
            val name = Catalog.system(n.system)?.name ?: n.system
            val files = n.files.joinToString(" ") { f -> f.names.joinToString(" or ") { if (it == "*.bin") "any .bin file" else it } }
            append("$name${if (n.optional) " (optional)" else ""}: $files, in ${n.folder}\n")
            n.files.forEach { append("  ${it.note}\n") }
        }
        append("\nWritten by Timeline. Safe to delete.\n")
    }
}

/** Games on more than one disc, known by their names or by a playlist. */
object Discs {
    private val tag = Regex("\\s*\\((?:Disc|Disk|CD)\\s*([0-9]+)(?:\\s*of\\s*[0-9]+)?\\)", RegexOption.IGNORE_CASE)

    /** The disc's number in a name such as "Game (USA) (Disc 2).chd", or null when it has none. */
    fun number(name: String): Int? = tag.find(name)?.groupValues?.get(1)?.toIntOrNull()

    /** The name the discs of one game have in common: the file name without its disc. */
    fun shared(name: String): String = tag.replace(name, "")

    /** The files an .m3u playlist names, one to a line; blank lines and # comments are skipped. */
    fun listed(text: String): List<String> =
        text.lineSequence().map { it.removePrefix("\uFEFF").trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toList()

    /** A playlist for RetroArch naming each disc by its full path, in order. */
    fun playlist(discs: List<String>): String = discs.joinToString("\n", postfix = "\n")

    /** One spelling for a path, so "./Game (Disc 1).chd" in a playlist matches the file beside it. */
    fun same(f: File): String = f.toPath().normalize().toString()
}

/** The library as last seen, kept in a small file so the first frame after boot is not an empty one. */
object LibraryCache {
    private const val F = "\t"
    private const val D = "\u001F"

    fun format(lib: Map<String, List<Game>>): String = buildString {
        for (g in lib.values.flatten()) {
            append(listOf(g.system, g.file, g.path, g.title, g.sort, g.region, if (g.fanTranslation) "1" else "0",
                if (g.pal) "1" else "0", g.discs.joinToString(D)).joinToString(F))
            append('\n')
        }
    }

    fun parse(text: String): Map<String, List<Game>> {
        val out = LinkedHashMap<String, MutableList<Game>>()
        for (line in text.lineSequence()) {
            val f = line.split(F)
            if (f.size < 9) continue
            out.getOrPut(f[0]) { ArrayList() } += Game(
                system = f[0], file = f[1], path = f[2], title = f[3], sort = Titles.sortKey(f[3]),     // worked out again: the rule may have changed
                discs = if (f[8].isEmpty()) emptyList() else f[8].split(D), region = f[5],
                fanTranslation = f[6] == "1", pal = f[7] == "1",
           )
        }
        for (games in out.values) games.sortBy { it.sort }
        return out
    }
}

// ---------------------------------------------------------------------------------------------
// Play history, sorting and colour maths. Plain functions, so they can be checked off-device.
// ---------------------------------------------------------------------------------------------

data class Played(val count: Int = 0, val last: Long = 0L, val seconds: Long = 0L) {
    /** "34 min", "2 h 05 min"; empty under a minute. */
    val time: String
        get() = when {
            seconds < 60 -> ""
            seconds < 3600 -> "${seconds / 60} min"
            else -> "${seconds / 3600} h ${((seconds % 3600) / 60).toString().padStart(2, '0')} min"
        }

    /** "today", "yesterday", "3 days ago", then a plain date. */
    fun lastSeen(now: Long, offsetMs: Int): String {
        if (last <= 0L) return ""
        val day = 86_400_000L
        val days = (now + offsetMs).floorDiv(day) - (last + offsetMs).floorDiv(day)
        return when {
            days <= 0L -> "today"
            days == 1L -> "yesterday"
            days < 7L -> "$days days ago"
            days < 60L -> "${days / 7} weeks ago".replace("1 weeks", "1 week")
            days < 730L -> "${days / 30} months ago"
            else -> "${days / 365} years ago"
        }
    }
}

/** Settings files of the "[Section]" and "Key = Value" kind, as Dolphin keeps them. */
object Ini {
    private val head = Regex("^\\s*\\[(.+)]\\s*$")

    /**
     * The text with every value in [set] in place, and every value in [ifAbsent] added only where the key is not
     * there yet. Everything else in the file is left exactly as it was.
     */
    fun merge(text: String, set: Map<String, Map<String, String>>, ifAbsent: Map<String, Map<String, String>> = emptyMap(),
              remove: Map<String, Set<String>> = emptyMap()): String {
        val lines = text.replace("\r\n", "\n").split('\n').toMutableList()
        while (lines.isNotEmpty() && lines.last().isBlank()) lines.removeAt(lines.size - 1)
        for ((section, keys) in remove) {
            val start = lines.indexOfFirst { head.find(it)?.groupValues?.get(1) == section }
            if (start < 0) continue
            var i = start + 1
            while (i < lines.size && head.find(lines[i]) == null) {
                if (lines[i].contains('=') && lines[i].substringBefore('=').trim() in keys) lines.removeAt(i) else i++
            }
        }
        fun put(section: String, key: String, value: String, force: Boolean) {
            var start = lines.indexOfFirst { head.find(it)?.groupValues?.get(1) == section }
            if (start < 0) { lines += "[$section]"; start = lines.size - 1 }
            var end = start + 1
            while (end < lines.size && head.find(lines[end]) == null) end++
            val at = (start + 1 until end).firstOrNull { lines[it].substringBefore('=').trim() == key && lines[it].contains('=') }
            when {
                at == null -> lines.add(end, "$key = $value")
                force -> lines[at] = "$key = $value"
            }
        }
        for ((section, keys) in set) for ((k, v) in keys) put(section, k, v, true)
        for ((section, keys) in ifAbsent) for ((k, v) in keys) put(section, k, v, false)
        return lines.joinToString("\n") + "\n"
    }

    fun value(text: String, section: String, key: String): String? {
        var inside = false
        for (line in text.lineSequence()) {
            head.find(line)?.let { inside = it.groupValues[1] == section }
            if (inside && line.contains('=') && line.substringBefore('=').trim() == key) return line.substringAfter('=').trim()
        }
        return null
    }
}

/**
 * What the launcher sets inside Dolphin, once it has been let into Dolphin's settings folder. Dolphin offers that
 * folder to Android's folder picker; no other emulator on the card does, which is why only Dolphin gets this.
 *
 * Everything goes through Dolphin's game settings files, never its main ones. Dolphin reads its main settings once,
 * when its process starts, and writes its own copy back when a game ends, so anything put there from outside is
 * ignored and then erased (seen on Android 13). A file named after the first letter of a game's ID applies to every
 * game whose ID starts with it, and is read afresh each time a game starts. GameCube IDs start with G, D, P or U;
 * Wii IDs with R or S.
 */
object DolphinPack {
    const val PACKAGE = "org.dolphinemu.dolphinemu"
    const val AUTHORITY = "org.dolphinemu.dolphinemu.user"
    const val ROOT = "root"
    const val PROFILE = "Timeline"
    const val SIDEWAYS = "Timeline sideways"
    const val CRT = "Timeline CRT"

    /** A file to put in place whole ([text]), or one of Dolphin's own to add a few values to ([set]). */
    class Doc(val path: String, val text: String? = null, val set: Map<String, Map<String, String>> = emptyMap(), val remove: Map<String, Set<String>> = emptyMap())

    private val gameCube = listOf("G", "D", "P", "U")
    private val wii = listOf("R", "S")

    /** Wii games that are better served by something other than a Wii Remote and Nunchuk, by game ID. */
    private val special: Map<String, Map<String, String>> = mapOf(
        "R7PE01" to mapOf("WiimoteProfile1" to SIDEWAYS),    // Punch-Out!!: the remote held sideways, like an NES pad
   )

    /** Wii games played with the GameCube pad, as they were meant to be, by every player: Brawl and Mario Kart Wii. */
    private val padGames = listOf("RSBE01", "RMCE01")

    /**
     * A GameCube-pad Wii game's own control lines for [ports] controllers: no Wii Remotes, and a pad in each port used.
     * The second map holds the lines to take out, left over from a time with more controllers (or with the controls
     * left to Dolphin, all of them).
     */
    private fun padGame(ports: Int): Pair<Map<String, Map<String, String>>, Map<String, Set<String>>> {
        val set = mapOf(
            "Controls" to (0 until ports).associate { "WiimoteSource$it" to "0" } + (2..ports).associate { "PadProfile$it" to "$PROFILE $it" },
            "Core" to (1..ports).associate { "SIDevice${it - 1}" to "6" },
       ).filterValues { it.isNotEmpty() }
        val from = maxOf(ports, 1) + 1
        val remove = mapOf(
            "Controls" to ((ports until 4).map { "WiimoteSource$it" } + (from..4).map { "PadProfile$it" }).toSet(),
            "Core" to ((ports + 1)..4).map { "SIDevice${it - 1}" }.toSet(),
       ).filterValues { it.isNotEmpty() }
        return set to remove
    }

    /**
     * Fixes a game needs that Dolphin's own game settings do not already make, from the Dolphin wiki's game pages and
     * its bug tracker (disc IDs read from the games themselves). Each is the documented setting:
     * copies kept in memory where a game reads them back (lens flares, credits, transitions), a safe texture cache
     * where emblems or text would be wrong, the GPU thread kept in step, one core where two crash, and the emulated
     * processor sped up for the two Tony Hawk games that slowed down on the console itself.
     */
    private val gameFixes: Map<String, Map<String, Map<String, String>>> = run {
        val noEfbTex = "Video_Hacks" to mapOf("EFBToTextureEnable" to "False")
        mapOf(
            "GHQE7D" to mapOf("Video_Hacks" to mapOf("EFBAccessEnable" to "True")),                                  // The Simpsons: Hit & Run, the sun's flare
            "GSWE64" to mapOf("Core" to mapOf("CPUThread" to "False"),                                                // Rogue Leader
                "Video_Hacks" to mapOf("EFBToTextureEnable" to "False", "FastTextureSampling" to "False")),
            "GRSEAF" to mapOf("Core" to mapOf("SyncGPU" to "True"), "Video_Hacks" to mapOf("EFBToTextureEnable" to "False")),   // SoulCalibur II
            "GFZE01" to mapOf(noEfbTex, "Video_Settings" to mapOf("SafeTextureCacheColorSamples" to "0")),          // F-Zero GX
            "GMSE01" to mapOf("Core" to mapOf("SyncGPU" to "True")),                                                  // Super Mario Sunshine
            "GM8E01" to mapOf(noEfbTex),                                                                              // Metroid Prime
            "GPTE41" to mapOf("Core" to mapOf("CPUThread" to "False"), "Video_Hacks" to mapOf("XFBToTextureEnable" to "False")),  // Prince of Persia
            "GNWE69" to mapOf("Video_Hacks" to mapOf("FastTextureSampling" to "False")),                              // Def Jam: Fight for NY, its videos
            "GGEE41" to mapOf("Video_Hacks" to mapOf("XFBToTextureEnable" to "False")),                               // Beyond Good & Evil
            "GT4E52" to mapOf("Core" to mapOf("OverclockEnable" to "True", "Overclock" to "1.5")),                    // Tony Hawk's Pro Skater 4
            "GTDE52" to mapOf("Core" to mapOf("OverclockEnable" to "True", "Overclock" to "1.15")),                   // Tony Hawk's Underground
            "GM4E01" to mapOf(noEfbTex), "GALE01" to mapOf(noEfbTex), "RMGE01" to mapOf(noEfbTex), "SX4E01" to mapOf(noEfbTex),
            "RSBE01" to mapOf("Video_Hacks" to mapOf("EFBToTextureEnable" to "False", "XFBToTextureEnable" to "False"),
                "Video_Settings" to mapOf("SafeTextureCacheColorSamples" to "0")),                                    // Brawl
            "RMCE01" to mapOf("Video_Hacks" to mapOf("EFBToTextureEnable" to "False", "XFBToTextureEnable" to "False"),
                "Video_Settings" to mapOf("SafeTextureCacheColorSamples" to "0")),                                    // Mario Kart Wii
            "SB4E01" to mapOf("Video_Hacks" to mapOf("XFBToTextureEnable" to "False")),                               // Super Mario Galaxy 2
            "GPIE01" to mapOf("Video_Settings" to mapOf("SafeTextureCacheColorSamples" to "512")),                    // Pikmin
       )
    }

    private fun ini(sections: List<Pair<String, List<Pair<String, String>>>>): String =
        sections.filter { it.second.isNotEmpty() }.joinToString("") { (name, keys) -> "[$name]\n" + keys.joinToString("") { (k, v) -> "$k = $v\n" } }

    /** The settings every game of one family starts with. [pad] is false when no gamepad of the device's own was found. */
    fun family(look: Look, wiiGame: Boolean, pad: Boolean, ports: Int = 1): String = ini(listOf(
        // A game on two discs is handed both (AutoStartFiles, see Launch.discSet): when it asks for the other one,
        // Dolphin changes it by itself, with no menu and no file picker.
        // GameCube ports 2 to 4 have a controller plugged in for each further port (6 is the standard pad).
        // The cable (Settings, Picture, Signal): a composite or S-video cable sends 480 lines interlaced, and the console
        // is told so (Dolphin's ProgressiveScan sets the console's own setting); only component sends them progressive,
        // which under 1% of GameCube owners had (Nintendo's reason for dropping the port).
        "Core" to listOf("GFXBackend" to if (look.dolphinGl) "OGL" else "Vulkan", "AutoDiscChange" to "True",
            "ProgressiveScan" to if (look.signalOf("gcwii") == "Component") "True" else "False") +
            (if (pad && !wiiGame) (2..ports).map { "SIDevice${it - 1}" to "6" } else emptyList()),
        // The console's own detail for the true picture (the sharper look: twice it, 1280 by 1056), and shaders built
        // ahead of time and before a game starts, so the first sight of an effect does not stop the game for a moment.
        // GPU texture decoding off: with it on, the Nova's Vulkan driver crashes as games start (vkUpdateDescriptorSets;
        // Animal Crossing, Beyond Good & Evil and Eternal Darkness every time, and all three run with it off).
        // No MSAA: the picture is already drawn at twice the console's detail, and MSAA breaks several games (Mario Kart
        // Wii, Super Mario Galaxy 2's sky, Spider-Man 2's shadows) and costs speed.
        "Video_Settings" to listOf("InternalResolution" to if (look.sharp) "2" else "1", "ShaderCompilationMode" to "2", "WaitForShadersBeforeStarting" to "True",
            "EnableGPUTextureDecoding" to "False", "MSAA" to "1", "SSAA" to "False"),
        // The tube (Crt3d.dolphin, put in Dolphin's Shaders folder below). On composite or S-video, the game's own
        // filtering as it copies each picture out (the three-line blend games used against 480 interlaced lines'
        // flicker) and its own color depth (the dither a set's signal blended): a set showed both. On component, a
        // sharp 480 progressive picture: games use that blend for interlaced output, and a progressive one never needed
        // it (with it, GameCube looked soft). The sharper look drops both too.
        "Video_Enhancements" to (if (look.crt3d) listOf("PostProcessingShader" to CRT) else emptyList()) +
            (if (look.sharp || look.signalOf("gcwii") == "Component") "True" else "False").let { clean ->
                listOf("DisableCopyFilter" to clean, "ForceTrueColor" to clean) },
        // No on-screen messages: "Compiling shaders" would otherwise show at a game's first start, and a Dolphin that
        // resumes places would say "State not found" at every start without one.
        "Dolphin.Interface" to listOf("OnScreenDisplayMessages" to "False"),
        // The handheld has its own sticks and buttons: no touch buttons drawn over the game.
        "Dolphin.Android" to listOf("ShowInputOverlay" to if (pad || !look.dolphinPad) "False" else "True"),
        // A Wii game is told the television is 4:3, which this panel is, so it fills the screen.
        "Wii" to if (wiiGame) listOf("Widescreen" to "False") else emptyList(),
        // Wii: a Wii Remote for each further controller connected (Source 1 is an emulated remote).
        "Controls" to (if (pad) listOf("PadProfile1" to PROFILE) else emptyList()) +
            (if (pad && wiiGame) listOf("WiimoteProfile1" to PROFILE) + (2..ports).flatMap { listOf("WiimoteSource${it - 1}" to "1", "WiimoteProfile$it" to "$PROFILE $it") } else emptyList()) +
            (if (pad && !wiiGame) (2..ports).map { "PadProfile$it" to "$PROFILE $it" } else emptyList()),
   ))

    /**
     * The handheld's controls as a GameCube pad. The names are Dolphin's own for an Android gamepad, taken from what
     * it wrote when each control was mapped by hand: buttons by name, sticks and triggers by Android's axis number
     * (0 and 1 left stick, 11 and 14 right stick, 15 and 16 d-pad, 17 or 23 left trigger, 18 or 22 right trigger).
     */
    fun gamePad(device: String): String = gamePad(listOf(device))

    /** The same for several controllers driving one port, any one of them: each control named with its controller. */
    fun gamePad(devices: List<String>): String = ini(listOf("Profile" to across(devices, listOf(
        "Device" to devices.first(),
        "Buttons/A" to "`Button A`", "Buttons/B" to "`Button B`", "Buttons/X" to "`Button X`", "Buttons/Y" to "`Button Y`",
        "Buttons/Z" to "`Button R1`", "Buttons/Start" to "Start",
        "Main Stick/Up" to "`Axis 1-`", "Main Stick/Down" to "`Axis 1+`", "Main Stick/Left" to "`Axis 0-`", "Main Stick/Right" to "`Axis 0+`",
        "C-Stick/Up" to "`Axis 14-`", "C-Stick/Down" to "`Axis 14+`", "C-Stick/Left" to "`Axis 11-`", "C-Stick/Right" to "`Axis 11+`",
        "Triggers/L" to "`Button L2`", "Triggers/R" to "`Button R2`",
        "Triggers/L-Analog" to "`Axis 23+` | `Axis 17+`", "Triggers/R-Analog" to "`Axis 22+` | `Axis 18+`",
        "D-Pad/Up" to "`Axis 16-`", "D-Pad/Down" to "`Axis 16+`", "D-Pad/Left" to "`Axis 15-`", "D-Pad/Right" to "`Axis 15+`",
   ))))

    /** Settings in a profile that are not controls, left as they are by [across]. */
    private fun isSetting(key: String) = key == "Device" || key == "Extension" || key.startsWith("Options/") || key == "IR/Auto-Hide" || key == "IR/Relative Input"

    /**
     * With more than one controller to a port, each control is named with its controller ("`Android/2/Xbox Wireless
     * Controller:Button A`") and the controllers' are joined, any one of them counting. Dolphin looks the names up
     * again whenever a controller comes or goes.
     */
    private fun across(devices: List<String>, keys: List<Pair<String, String>>): List<Pair<String, String>> =
        if (devices.size <= 1) keys else keys.map { (k, v) ->
            if (isSetting(k)) k to v
            else k to v.split("|").map { it.trim().trim('`') }.flatMap { c -> devices.map { d -> "`$d:$c`" } }.joinToString(" | ")
        }

    /**
     * The handheld's controls as a Wii Remote. Usual form: remote and Nunchuk, the left stick as the Nunchuk's, the
     * right stick as the pointer, R1 to shake. Sideways form: the remote alone, turned on its side.
     */
    fun wiiRemote(device: String, sideways: Boolean): String = wiiRemote(listOf(device), sideways)

    fun wiiRemote(devices: List<String>, sideways: Boolean): String = ini(listOf("Profile" to across(devices, listOf(
        "Device" to devices.first(),
        "Buttons/A" to "`Button A`", "Buttons/B" to "`Button B`", "Buttons/1" to "`Button X`", "Buttons/2" to "`Button Y`",
        "Buttons/-" to "Select", "Buttons/+" to "Start", "Buttons/Home" to "`Button R3`",
        "D-Pad/Up" to "`Axis 16-`", "D-Pad/Down" to "`Axis 16+`", "D-Pad/Left" to "`Axis 15-`", "D-Pad/Right" to "`Axis 15+`",
        "Shake/X" to "`Button R1`", "Shake/Y" to "`Button R1`", "Shake/Z" to "`Button R1`",
   ) + if (sideways) listOf(
        "Options/Sideways Wiimote" to "True", "Extension" to "None",
   ) else listOf(
        "Options/Sideways Wiimote" to "False", "Extension" to "Nunchuk",
        "IR/Up" to "`Axis 14-`", "IR/Down" to "`Axis 14+`", "IR/Left" to "`Axis 11-`", "IR/Right" to "`Axis 11+`",
        "IR/Auto-Hide" to "True", "IR/Relative Input" to "True",
        "Nunchuk/Stick/Up" to "`Axis 1-`", "Nunchuk/Stick/Down" to "`Axis 1+`", "Nunchuk/Stick/Left" to "`Axis 0-`", "Nunchuk/Stick/Right" to "`Axis 0+`",
        "Nunchuk/Buttons/C" to "`Button L1`", "Nunchuk/Buttons/Z" to "`Button L2` | `Axis 23+` | `Axis 17+`",
        "Nunchuk/Shake/X" to "`Button L3`", "Nunchuk/Shake/Y" to "`Button L3`", "Nunchuk/Shake/Z" to "`Button L3`",
   ))))

    fun docs(look: Look, found: String?): List<Doc> = docs(look, listOfNotNull(found))

    /**
     * Everything to put in Dolphin's folder. [devices] are Dolphin's names for the controllers connected, player 1 first
     * (Players): player 1 is GameCube port 1 and Wii Remote 1; further controllers are GameCube ports 2 to 4 and Wii
     * Remotes 2 to 4.
     */
    fun docs(look: Look, devices: List<String>): List<Doc> = forPorts(look, devices.map { listOf(it) })

    /** The same with each port driven by any one of several controllers (Players.dolphinPorts). */
    fun forPorts(look: Look, controllers: List<List<String>>): List<Doc> {
        val ports = if (look.dolphinPad) controllers.filter { it.isNotEmpty() }.take(4) else emptyList()   // switched off: whatever was mapped inside Dolphin
        val device = ports.firstOrNull()
        val pad = device != null
        val out = ArrayList<Doc>()
        // The profiles first: a game settings file that names a profile which is not there yet stops with an error.
        if (device != null) {
            out += Doc("Config/Profiles/GCPad/$PROFILE.ini", text = gamePad(device))
            out += Doc("Config/Profiles/Wiimote/$PROFILE.ini", text = wiiRemote(device, sideways = false))
            out += Doc("Config/Profiles/Wiimote/$SIDEWAYS.ini", text = wiiRemote(device, sideways = true))
            ports.drop(1).forEachIndexed { i, d ->
                out += Doc("Config/Profiles/GCPad/$PROFILE ${i + 2}.ini", text = gamePad(d))
                out += Doc("Config/Profiles/Wiimote/$PROFILE ${i + 2}.ini", text = wiiRemote(d, sideways = false))
            }
        }
        if (look.crt3d) out += RetroPack.tvLook(look, "gcwii").let { t -> Doc("Shaders/$CRT.glsl", text = Crt3d.dolphin(t.lines, t.signal, t.white, t.mask)) }
        for (letter in gameCube) out += Doc("GameSettings/$letter.ini", text = family(look, wiiGame = false, pad = pad, ports = ports.size))
        for (letter in wii) out += Doc("GameSettings/$letter.ini", text = family(look, wiiGame = true, pad = pad, ports = ports.size))
        // Dolphin keeps a game's own settings in a file named after its full ID: only the control lines are added.
        // With the controls left to Dolphin again, those lines are taken back out.
        for ((id, keys) in special) out += if (pad) Doc("GameSettings/$id.ini", set = mapOf("Controls" to keys)) else Doc("GameSettings/$id.ini", remove = mapOf("Controls" to keys.keys))
        for (id in padGames) padGame(if (pad) ports.size else 0).let { (set, remove) -> out += Doc("GameSettings/$id.ini", set = set, remove = remove) }
        // Games Dolphin itself stops with a warning box at every start, for a fault of Dolphin's that the game then
        // plays straight through. For these the warning is shown as a line on the picture instead of a box to press.
        for (id in warnsAtStart) out += Doc("GameSettings/$id.ini", set = mapOf("Dolphin.Interface" to mapOf("UsePanicHandlers" to "False")))
        for ((id, fix) in gameFixes) out += Doc("GameSettings/$id.ini", set = fix)
        // "Run lighter": that one game at the console's own resolution; switched back off, the line is taken out again.
        for ((id, light) in look.lighterCodes) out += if (light) Doc("GameSettings/$id.ini", set = mapOf("Video_Settings" to mapOf("InternalResolution" to "1")))
            else Doc("GameSettings/$id.ini", remove = mapOf("Video_Settings" to setOf("InternalResolution")))
        // A game that would not start with Vulkan: that one game drawn with OpenGL; switched back, the line is taken out.
        // A game drawn with OpenGL also prepares its shaders the plain way: Dolphin's hybrid ubershaders crash in the
        // Adreno OpenGL driver (Dolphin bug 12791); its shaders are still all made before the game starts.
        for ((id, gl) in look.dolphinGlCodes) out += if (gl) Doc("GameSettings/$id.ini", set = mapOf("Core" to mapOf("GFXBackend" to "OGL"),
                "Video_Settings" to mapOf("ShaderCompilationMode" to "0")))
            else Doc("GameSettings/$id.ini", remove = mapOf("Core" to setOf("GFXBackend"), "Video_Settings" to setOf("ShaderCompilationMode")))
        // One entry per game file: its control lines, its fixes and its drawing choice are written together.
        val merged = LinkedHashMap<String, Doc>()
        for (d in out) {
            val was = merged[d.path]
            merged[d.path] = if (was == null || d.text != null || was.text != null) d else Doc(d.path,
                set = (was.set.keys + d.set.keys).associateWith { sec -> was.set[sec].orEmpty() + d.set[sec].orEmpty() },
                remove = (was.remove.keys + d.remove.keys).associateWith { sec -> was.remove[sec].orEmpty() + d.remove[sec].orEmpty() })
        }
        return merged.values.toList()
    }

    /**
     * SSX Tricky: "GFX FIFO: Unknown Opcode (0xfe ...)" as it starts, in Dolphin on Android and on desktop alike, with
     * the same disc image and the same address every time. It is Dolphin's open issue 12818 (present since 5.0-683),
     * not the file. Tried on Android 13: with the box dismissed, or with this setting, the game goes on into its opening.
     */
    val warnsAtStart: List<String> = listOf("GSTE69")
}

/**
 * What RetroArch's log says about a start that went wrong. When RetroArch cannot load a game on Android it does not
 * close: it stays in front, black, and no longer answers anything, including the request to close.
 */
object RetroLog {
    private val fatal = listOf("Could not read content file", "Failed to load content", "Failed to open libretro core",
        "Cannot open video driver", "Fatal error received in")

    /** A plain sentence saying why the game did not open, or null when the log shows no such thing. */
    fun failure(log: String): String? {
        val lines = log.lineSequence().toList()
        val at = lines.indexOfFirst { l -> fatal.any { l.contains(it) } }
        if (at < 0) return null
        val before = lines.take(at + 1).joinToString("\n")
        // In this order: a log that warns of a BIOS file some other region would want, and then fails to start its
        // graphics, failed because of its graphics.
        return when {
            before.contains("Could not read content file") -> "RetroArch could not read the game file."
            before.contains("Failed to open libretro core") -> "The core for this system could not be loaded."
            before.contains("Cannot open video driver") || before.contains("video_driver_init") -> "RetroArch could not start its graphics for this system."
            Regex("(?i)(firmware|bios)[^\\n]*(missing|not found)").containsMatchIn(before) -> "A BIOS file this system needs was not found."
            else -> "RetroArch reported an error while loading it."
        }
    }

    private val discless = listOf("Failed to open CD", "Failed to load CD image", "Failed to load CHD image", "Booting BIOS with no disc")

    /**
     * The core is running, but without the game: it could not read the disc image, and instead of failing it started
     * the console's own screen (PlayStation's memory card page, Saturn's CD player saying "Drive empty"). RetroArch
     * calls that a success. These are the cores' own words for it, each seen in a log on Android 13.
     */
    fun noDisc(log: String): Boolean = discless.any { log.contains(it) }

    /**
     * RetroArch shut down in good order: the last thing it does on a proper exit is unload the game and then the
     * core, and it says so (seen in its log on Android 13 after START and SELECT). A RetroArch that is still running,
     * was killed, or was handed another game never writes this.
     */
    fun closed(log: String): Boolean {
        // Closing a game from RetroArch's own menu writes the same line while RetroArch stays up, and it may then load
        // another: so the line only counts when nothing was loaded after it.
        val at = log.lastIndexOf("[Core] Unloading core")
        return at >= 0 && log.indexOf("Loading content file", at) < 0 && log.indexOf("[Core] Geometry", at) < 0
    }
}

object PlayLog {
    /** What is said on coming back from a game: "42 min." or "42 min. 6 h 10 min in all."; nothing for under a minute. */
    fun session(seconds: Long, total: Long): String? {
        val now = Played(seconds = seconds).time.ifEmpty { return null }
        return if (total - seconds < 60) "$now." else "$now. ${Played(seconds = total).time} in all."
    }

    /** One game per line: key, times started, last start (ms), seconds played; tab separated. */
    fun parse(text: String): Map<String, Played> {
        val out = LinkedHashMap<String, Played>()
        for (line in text.lineSequence()) {
            val f = line.split('\t')
            if (f.size < 4 || f[0].isBlank()) continue
            out[f[0]] = Played(f[1].toIntOrNull() ?: 0, f[2].toLongOrNull() ?: 0L, f[3].toLongOrNull() ?: 0L)
        }
        return out
    }

    fun format(map: Map<String, Played>): String =
        map.entries.joinToString("") { (k, p) -> "$k\t${p.count}\t${p.last}\t${p.seconds}\n" }

    fun started(map: Map<String, Played>, key: String, now: Long): Map<String, Played> {
        val p = map[key] ?: Played()
        return map + (key to p.copy(count = p.count + 1, last = now))
    }

    /** Adds the time away, ignoring a bounce straight back and anything implausibly long. */
    fun finished(map: Map<String, Played>, key: String, since: Long, now: Long): Map<String, Played> {
        val secs = (now - since) / 1000
        if (secs < 20 || secs > 16 * 3600) return map
        val p = map[key] ?: Played(count = 1, last = since)
        return map + (key to p.copy(seconds = p.seconds + secs))
    }
}

/** Surprise me: a game from the console in view, never the one it gave last time when there is another to give. */
object Surprise {
    fun pick(games: List<Game>, last: String?, random: kotlin.random.Random = kotlin.random.Random.Default): Game? =
        games.filter { it.key != last }.ifEmpty { games }.randomOrNull(random)
}

object Sorting {
    val modes = listOf("Title", "Release date", "Recently played", "Most played", "Favorites first")

    fun apply(
        mode: String, games: List<Game>,
        date: (Game) -> String, played: (Game) -> Played?, favorite: (Game) -> Boolean,
   ): List<Game> = when (mode) {
        "Release date" -> games.sortedWith(compareBy<Game> { date(it).ifEmpty { "9999" } }.thenBy { it.sort })
        "Recently played" -> games.sortedWith(compareByDescending<Game> { played(it)?.last ?: 0L }.thenBy { it.sort })
        "Most played" -> games.sortedWith(
            compareByDescending<Game> { played(it)?.seconds ?: 0L }.thenByDescending { played(it)?.count ?: 0 }.thenBy { it.sort })
        "Favorites first" -> games.sortedWith(compareByDescending<Game> { favorite(it) }.thenBy { it.sort })
        else -> games
    }
}

object Hsv {
    /** h in degrees, s and v from 0 to 1. Returns opaque ARGB. */
    fun toArgb(h: Float, s: Float, v: Float): Int {
        val hh = ((h % 360f) + 360f) % 360f / 60f
        val c = v * s
        val x = c * (1f - kotlin.math.abs(hh % 2f - 1f))
        val (r, g, b) = when (hh.toInt()) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val m = v - c
        fun ch(f: Float) = ((f + m) * 255f + 0.5f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
    }

    fun of(argb: Int): Triple<Float, Float, Float> {
        val r = ((argb shr 16) and 255) / 255f
        val g = ((argb shr 8) and 255) / 255f
        val b = (argb and 255) / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val d = max - min
        val h = when {
            d == 0f -> 0f
            max == r -> 60f * (((g - b) / d) % 6f)
            max == g -> 60f * ((b - r) / d + 2f)
            else -> 60f * ((r - g) / d + 4f)
        }
        return Triple((h + 360f) % 360f, if (max == 0f) 0f else d / max, max)
    }

    fun hex(argb: Int): String = "#" + (argb and 0xFFFFFF).toString(16).uppercase().padStart(6, '0')
}

object Sizes {
    fun bytes(n: Long): String = when {
        n <= 0L -> ""
        n >= 1_000_000_000L -> "${(n / 100_000_000L) / 10.0} GB"
        n >= 1_000_000L -> "${n / 1_000_000L} MB"
        else -> "${maxOf(1L, n / 1_000L)} KB"
    }
}

/**
 * Save files: which ones belong to a game, what each is, and how earlier versions are named and thinned.
 *
 * On the card, "_timeline/saves/<emulator>/..." mirrors the emulator's own folders and always holds the newest copy.
 * "_timeline/saves/earlier/<emulator>/..." holds what each newer copy replaced, under the same name with the moment
 * that version was written added to the end ("Some Game (USA).srm.20300102-030405").
 */
object Saves {
    const val RECENT = 6          // earlier versions of a game's own save that are always kept
    const val MOST = 20           // beyond those, one a day is kept, up to this many in all
    const val PLACES = 2          // earlier versions kept of a place or a save state, which are far larger
    const val ROOM_FOR_PLACES = 1_000_000_000L      // with less than this free on the card, places and save states are not copied

    /**
     * SAVE: the game's own save. CLOCK: its cartridge clock. PLACE: where the game was left. ASIDE: a place set aside.
     * SLOT: a save state made in RetroArch's menu. DISK: a console's whole hard disk (the Xbox's, hundreds of MB), kept
     * like a save state: two earlier versions.
     */
    enum class Kind { SAVE, CLOCK, PLACE, ASIDE, SLOT, DISK, OTHER }

    private val slot = Regex("\\.state\\d*$")
    private val ignored = listOf(".ldci", ".png", ".bak", ".part", ".cht", ".tmp")

    fun kind(name: String): Kind = when {
        name.endsWith(".state.auto") -> Kind.PLACE
        name.endsWith(".state.auto.earlier") -> Kind.ASIDE
        slot.containsMatchIn(name) -> Kind.SLOT
        name.endsWith(".rtc") -> Kind.CLOCK
        name == "hdd.img" || name.endsWith(".qcow2") -> Kind.DISK
        ignored.any { name.endsWith(it) } -> Kind.OTHER
        else -> Kind.SAVE
    }

    // What may follow a game's own name: an ending (.srm, .rtc), a place or state, a Dreamcast memory card (.A1.bin),
    // or Sega CD cartridge memory (_cart.brm).
    private val tail = Regex("^(\\.[A-Za-z0-9]+|\\.state\\.auto(\\.earlier)?|\\.[A-D][12]\\.bin|_cart\\.brm)$")

    private val discId = Regex("\\[BOOT\\] Game ID is \\[([A-Za-z0-9][A-Za-z0-9 ._-]{1,20})\\]")

    /**
     * The Dreamcast core names a game's memory card after the product number on its disc ("MK-51035.A1.bin"), so that
     * the discs of one game share a card. It says the number in RetroArch's log as the game boots.
     */
    fun discId(log: String, contentName: String): String? {
        // Only from the part of the log that follows this game's own file being loaded: for a moment after a hand-over
        // the log on file is still the one the game before left behind, and its number is not this game's.
        val from = log.indexOf(contentName).takeIf { contentName.isNotEmpty() && it >= 0 } ?: return null
        return discId.find(log, from)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
    }

    /** Whether a file in a core's save folder is this game's: RetroArch names saves after the game's file. */
    fun belongs(name: String, stem: String): Boolean =
        stem.isNotEmpty() && name.startsWith(stem) && tail.matches(name.substring(stem.length))

    /** RetroArch's folder for the core an emulator choice names ("ra:gambatte" gives "Gambatte"); null for anything else. */
    fun coreFolder(emulatorId: String): String? =
        if (!emulatorId.startsWith("ra:")) null else Emulators.get(emulatorId)?.label?.substringAfter("· ", "")?.takeIf { it.isNotEmpty() }

    private fun cal(ms: Long, zone: java.util.TimeZone) = java.util.Calendar.getInstance(zone, java.util.Locale.US).apply { timeInMillis = ms }

    /** The moment a version was written, as it appears at the end of its name. In the device's own time, so it reads true in a file browser. */
    fun stamp(ms: Long, zone: java.util.TimeZone = java.util.TimeZone.getDefault()): String = cal(ms, zone).let { c ->
        "%04d%02d%02d-%02d%02d%02d".format(java.util.Locale.US, c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH) + 1,
            c.get(java.util.Calendar.DAY_OF_MONTH), c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE), c.get(java.util.Calendar.SECOND))
    }

    /**
     * An earlier version's name: the save's own name, when its contents were last written, and (after an underscore)
     * until when it was the one in use. Both are in the name so that a copy of the card which loses file times loses nothing.
     */
    fun versionName(name: String, ms: Long, until: Long = 0L, zone: java.util.TimeZone = java.util.TimeZone.getDefault()): String =
        name + "." + stamp(ms, zone) + (if (until > 0L) "_" + stamp(until, zone) else "")

    private val versioned = Regex("^(.+)\\.(\\d{4})(\\d{2})(\\d{2})-(\\d{2})(\\d{2})(\\d{2})(?:_(\\d{4})(\\d{2})(\\d{2})-(\\d{2})(\\d{2})(\\d{2}))?$")

    /** The name of the save an earlier version is a version of, or null when the name is not an earlier version's. */
    fun original(versionName: String): String? = versioned.matchEntire(versionName)?.groupValues?.get(1)

    /** When an earlier version was written, read back from its name. */
    fun writtenAt(versionName: String, zone: java.util.TimeZone = java.util.TimeZone.getDefault()): Long? {
        val m = versioned.matchEntire(versionName)?.groupValues?.subList(2, 8)?.map { it.toInt() } ?: return null
        return java.util.Calendar.getInstance(zone, java.util.Locale.US).apply { clear(); set(m[0], m[1] - 1, m[2], m[3], m[4], m[5]) }.timeInMillis
    }

    /** Until when an earlier version was the one in use, read back from its name; null when the name does not say. */
    fun usedUntil(versionName: String, zone: java.util.TimeZone = java.util.TimeZone.getDefault()): Long? {
        val m = versioned.matchEntire(versionName)?.groupValues?.subList(8, 14)?.takeIf { it[0].isNotEmpty() }?.map { it.toInt() } ?: return null
        return java.util.Calendar.getInstance(zone, java.util.Locale.US).apply { clear(); set(m[0], m[1] - 1, m[2], m[3], m[4], m[5]) }.timeInMillis
    }

    /**
     * Which earlier versions to let go of, given when each was written. The newest [recent] always stay. Of the older
     * ones, the last of each day stays (a day the newest ones already cover needs none), until [most] are kept in all.
     */
    fun drop(times: List<Long>, recent: Int = RECENT, most: Int = MOST, zone: java.util.TimeZone = java.util.TimeZone.getDefault()): List<Long> {
        val sorted = times.distinct().sortedDescending()
        if (sorted.size <= recent) return emptyList()
        fun day(ms: Long) = stamp(ms, zone).substringBefore('-')
        val days = sorted.take(recent).map(::day).toMutableSet()
        var kept = recent
        val gone = ArrayList<Long>()
        for (t in sorted.drop(recent)) {
            if (kept < most && days.add(day(t))) kept++ else gone.add(t)
        }
        return gone
    }

    private val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    /** A moment as a person would say it: "Today 18:42", "Yesterday 09:05", "11 Oct, 21:10", "3 Mar 2025". */
    fun day(ms: Long, now: Long, zone: java.util.TimeZone = java.util.TimeZone.getDefault(), seconds: Boolean = false): String {
        if (ms <= 0L) return ""
        val c = cal(ms, zone); val n = cal(now, zone)
        val time = "%02d:%02d".format(java.util.Locale.US, c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE)) +
            if (seconds) ":%02d".format(java.util.Locale.US, c.get(java.util.Calendar.SECOND)) else ""
        fun same(a: java.util.Calendar, b: java.util.Calendar) =
            a.get(java.util.Calendar.YEAR) == b.get(java.util.Calendar.YEAR) && a.get(java.util.Calendar.DAY_OF_YEAR) == b.get(java.util.Calendar.DAY_OF_YEAR)
        val yesterday = cal(now, zone).apply { add(java.util.Calendar.DAY_OF_YEAR, -1) }
        val date = "${c.get(java.util.Calendar.DAY_OF_MONTH)} ${months[c.get(java.util.Calendar.MONTH)]}"
        return when {
            same(c, n) -> "Today $time"
            same(c, yesterday) -> "Yesterday $time"
            c.get(java.util.Calendar.YEAR) == n.get(java.util.Calendar.YEAR) -> "$date, $time"
            else -> "$date ${c.get(java.util.Calendar.YEAR)}"
        }
    }

    /**
     * To put a game's save back as it stood at [moment]: for each of its files, the earlier version to use (named by
     * when it was last written), or null to leave that file alone.
     *
     * [versions] gives, for each file, every earlier version as two times: when it was last written, and until when it
     * was the one in use. [moment] is the "last written" time of the version the player picked. A file with a version
     * written at that very moment takes it. Any other file takes the version that was in use then: the first one
     * that was still in use after the moment. A file none of whose versions outlived the moment already holds what it
     * held then (its clock, say, has not been set since) and is left alone, as is one with nothing on record at all.
     * [current] is only there so that every file of the game is answered for.
     */
    fun asOf(moment: Long, versions: Map<String, List<Pair<Long, Long>>>, current: Map<String, Long>): Map<String, Long?> =
        (versions.keys + current.keys).associateWith { file ->
            val known = versions[file].orEmpty()
            known.firstOrNull { kotlin.math.abs(it.first - moment) <= 2500 }?.first
                ?: known.filter { it.second > moment + 2500 }.minByOrNull { it.second }?.first
        }

    /**
     * Whether the place a game was left at should be set aside before the game starts. It should when it was not
     * written by the last sitting (that sitting ended some other way than START and SELECT) and the game's own save
     * has moved on since: loading it would put an old moment over newer progress. With no newer save, the place is
     * the newest progress there is, and it stays.
     */
    fun placeIsStale(placeWritten: Long, lastStart: Long, ownSaves: List<Long>): Boolean =
        lastStart > 0L && placeWritten + 2000 < lastStart && ownSaves.any { it > placeWritten + 10_000 }

    // ---- GameCube and Wii, which Dolphin files by the game's code rather than by its file's name

    /** Where a disc image holds its six-character game code, by the kind of file; null for a kind that hides it. */
    fun codeOffset(fileName: String): Long? = when (fileName.substringAfterLast('.', "").lowercase()) {
        "iso", "gcm" -> 0L
        "ciso" -> 0x8000L
        "rvz", "wia" -> 0x58L
        "wbfs" -> 0x200L
        else -> null
    }

    fun code(bytes: ByteArray): String? =
        bytes.takeIf { it.size >= 6 }?.copyOf(6)?.toString(Charsets.ISO_8859_1)?.takeIf { c -> c.all { it in 'A'..'Z' || it in '0'..'9' } }

    /**
     * Whether a file in Dolphin's folders (its path below Dolphin's own folder) is a save of the game with this code.
     * GameCube saves are files on a memory card folder, "GC/USA/Card A/01-GM4E-name.gci"; a Wii game's are everything
     * under "Wii/title/00010000/<the code's first four letters in hexadecimal>/data".
     */
    fun dolphinOwns(path: String, code: String): Boolean {
        if (code.length != 6) return false
        val name = path.substringAfterLast('/')
        if (path.startsWith("GC/")) return name.endsWith(".gci", ignoreCase = true) && name.startsWith(code.substring(4) + "-" + code.substring(0, 4) + "-")
        val hex = code.substring(0, 4).toByteArray(Charsets.ISO_8859_1).joinToString("") { "%02x".format(it) }
        return path.startsWith("Wii/title/00010000/$hex/data/", ignoreCase = true)
    }

    /** Which of Dolphin's files are saves at all: memory cards and their files, Wii save data, and the Miis. */
    fun dolphinSave(path: String): Boolean = when {
        path.startsWith("GC/") -> listOf(".gci", ".raw", ".gcp").any { path.endsWith(it, ignoreCase = true) } && !path.endsWith("SRAM.raw")
        path.startsWith("Wii/title/") -> Regex("^Wii/title/0001000[014]/[0-9a-fA-F]{8}/data/.+").matches(path)
        else -> path == "Wii/shared2/menu/FaceLib/RFL_DB.dat"
    }
}

// ---------------------------------------------------------------------------------------------
// The handheld itself: stick lights, fan and power. The Nova's stock firmware has a maker's service
// that applies settings for any app. Nothing here talks to it; this is only what may be asked of it.
// ---------------------------------------------------------------------------------------------

object Vendor {
    const val LIGHT_COLOR = "joystick_led_light_picker_color"
    const val LIGHT_ON = "joystick_light_enabled"
    const val LIGHT_LEVEL = "led_light_brightness_percent"
    const val FAN = "fan_mode"
    const val PERFORMANCE = "performance_mode"
    const val BRIGHTNESS = "screen_brightness"
    const val BRIGHTNESS_MODE = "screen_brightness_mode"
    /** Retroid's own 80% charge limit: its settings app passes it to the charger (limit_capacity_charge). */
    const val CHARGE_LIMIT = "percent_80_charge_limit"
    /** Retroid's link from the power mode to the fan (SystemUI's fan tile): on, a power change also sets the fan. */
    const val FAN_LINK = "is_quick_set_performance_and_fan_enable"

    const val FAN_QUIET = 1
    const val FAN_SMART = 4
    const val FAN_SPORT = 5

    /**
     * The only settings this app will ever write, and the exact shape each value must have.
     * The fan can be set to the maker's Quiet, Smart or Sport: never off, and never a raw speed.
     */
    private val writable: Map<String, Regex> = mapOf(
        LIGHT_COLOR to Regex("#[0-9A-F]{8},#[0-9A-F]{8}"),
        LIGHT_ON to Regex("[01],[01]"),
        LIGHT_LEVEL to Regex("0\\.\\d{1,2}|1\\.0"),
        FAN to Regex("[145]"),
        PERFORMANCE to Regex("[012]"),
        BRIGHTNESS to Regex("[1-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5]"),
        BRIGHTNESS_MODE to Regex("[01]"),
        CHARGE_LIMIT to Regex("[01]"),
        FAN_LINK to Regex("[01]"),
   )

    /** The fan values the maker is known to use. Anything else on a device means "do not touch". */
    val knownFan = setOf("0", "1", "4", "5", "6")

    fun put(key: String, value: String): String? =
        if (writable[key]?.matches(value) == true) "settings put system $key '$value'" else null

    /** Closes an emulator, by its package name and nothing else. */
    fun forceStop(pkg: String): String? = if (Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+").matches(pkg)) "am force-stop $pkg" else null

    /**
     * The power mode is two things on the Nova. The setting above is what the quick-settings tile shows; the maker's
     * own service (pservice) acts on this property, raising the chip's lowest clock speeds: Standard leaves them to the
     * chip, Performance raises them, High holds the processor, graphics and memory near their top. The tile sets both
     * (read from SystemUI), and so does this app, in only these three values.
     */
    const val PERFORMANCE_PROP = "persist.vendor.debug.mode"
    fun setprop(value: String): String? = if (Regex("[012]").matches(value)) "setprop $PERFORMANCE_PROP $value" else null
    const val READ_PERFORMANCE = "getprop $PERFORMANCE_PROP"

    /**
     * Temperatures, read through the maker's service as its own Smart fan reads them (Android's thermal service is not
     * set up on this firmware: "HAL Ready: false"). Read only. Each sensor's name is read once; then the chip's
     * sensors are read each time by number. Values are in thousandths of a degree.
     */
    const val THERMAL = "/sys/devices/virtual/thermal"
    private val chipSensor = Regex("(cpu|cpuss|gpuss)-[0-9-]+")

    /** The sensor numbers for the processor and graphics cores, from lines of "<sensor folder>/type:<name>". */
    fun chipSensors(listing: String): List<Int> = listing.lines().mapNotNull { line ->
        val m = Regex(".*/thermal_zone(\\d+)/type:(.*)").matchEntire(line.trim()) ?: return@mapNotNull null
        m.groupValues[1].toIntOrNull()?.takeIf { chipSensor.matches(m.groupValues[2].trim()) }
    }.distinct().take(48)

    fun readSensors(zones: List<Int>): String? =
        if (zones.isEmpty() || zones.size > 48) null else "cat " + zones.joinToString(" ") { "$THERMAL/thermal_zone$it/temp" }

    /** The hottest reading in °C, or NaN when there is none that makes sense. */
    fun hottest(output: String): Float = output.lines().mapNotNull { it.trim().toLongOrNull() }
        .map { it / 1000f }.filter { it in 0f..150f }.maxOrNull() ?: Float.NaN

    /**
     * The Nova keeps its stick lights' settings in the three settings above but does not act on them: the maker's own
     * settings app also writes each stick's light driver, and so must this. Each stick has an on switch ("1" or "0")
     * and a colour, written as "1-R:G:B" with every channel already scaled by the brightness (read from
     * RPSettings). These are the only two driver files this app will write, in only these two shapes.
     */
    val lightNodes = listOf("/sys/class/sn3112l/led", "/sys/class/sn3112r/led")
    private val nodeValue = mapOf("enable" to Regex("[01]"), "brightness" to Regex("1-(25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])(:(25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])){2}"))

    fun write(node: String, file: String, value: String): String? =
        if (node in lightNodes && nodeValue[file]?.matches(value) == true) "echo $value > $node/$file" else null

    /** The colour for the driver: each channel times the brightness, 0.05 to 1.0. */
    fun driverColor(argb: Int, percent: Int): String {
        val f = percent.coerceIn(5, 100) / 100f
        fun ch(shift: Int) = (((argb shr shift) and 0xFF) * f).toInt().coerceIn(0, 255)
        return "1-${ch(16)}:${ch(8)}:${ch(0)}"
    }

    fun color(argb: Int): String = "#FF" + (argb and 0xFFFFFF).toString(16).uppercase().padStart(6, '0')
    fun both(argb: Int): String = color(argb) + "," + color(argb)
    fun level(percent: Int): String = if (percent >= 100) "1.0" else "0." + percent.coerceIn(5, 99).toString().padStart(2, '0')

    fun fanName(v: String?): String = when (v) {
        "0" -> "Off"; "1" -> "Quiet"; "4" -> "Smart"; "5" -> "Sport"; "6" -> "Custom"; null -> "Not found"; else -> "Unknown ($v)"
    }

    fun performanceName(v: String?): String = when (v) {
        "0" -> "Standard"; "1" -> "Performance"; "2" -> "High performance"; null -> "Not found"; else -> "Unknown ($v)"
    }
}

/**
 * How hard a system works the chip, and so which of the maker's modes suit it. Smart is the maker's fan curve, which
 * follows the processor's and graphics' temperatures by itself; it is the fan for everything that is not barely
 * warm, and for the launcher. Higher power raises only the lowest clock speeds: the chip still speeds up by itself
 * under load at any setting, so higher costs heat and battery and is kept for the systems whose emulators work in
 * bursts (compiling, translating) that would otherwise wait the moment it takes to speed up.
 */
enum class Power(val label: String, val performance: Int, val fan: Int, val note: String) {
    Light("Light", 0, Vendor.FAN_QUIET, "Standard power, quiet fan. For cartridge-era systems that barely warm the chip. The heat guard lifts the fan if one ever does."),
    Balanced("Balanced", 0, Vendor.FAN_SMART, "Standard power, smart fan. For the first 3D consoles: the chip speeds up by itself the moment a game asks."),
    Strong("Strong", 1, Vendor.FAN_SMART, "Performance power, smart fan. For PlayStation 2, GameCube, Wii and Xbox: higher lowest speeds, so bursts of work never wait for the chip."),
    High("High", 2, Vendor.FAN_SPORT, "High performance power, sport fan. Holds the chip near its top speed. No system here needs it; it costs heat and battery."),
    ;

    companion object {
        private val strong = setOf("ps2", "gc", "wii", "xbox", "n3ds", "psvita", "wiiu")
        private val balanced = setOf("psx", "saturn", "n64", "dreamcast", "nds", "psp")
        fun default(system: String): Power = when (system) {
            in strong -> Strong
            in balanced -> Balanced
            else -> Light
        }
        /** In the launcher, and anywhere no game is running: Standard power and the Smart fan. */
        val resting = Balanced
        fun named(label: String): Power? = entries.firstOrNull { it.label == label }
    }
}

/**
 * Steps the fan up a mode when it is getting hot and back down once it has cooled, between the maker's own modes only
 * and never below the mode the system's profile asked for. It watches two things, every ten seconds while a game runs:
 * the hottest processor or graphics sensor, which is what slows a game down (the Nova's graphics slow themselves at
 * 95°C, its processor at 108°C), and the battery, which is what wears (Retroid's own guard starts at 53°C). Up at
 * 80°C on the chip or 45°C on the battery, two readings running; down once the chip is at or under 68°C and the battery
 * at or under 41°C, six readings running, so the fan does not hunt between modes. (A battery charging while a game runs
 * sits around 40°C; the marks are above that, so charging alone does not hold the fan on Sport.)
 */
class HeatGuard(private val base: Int) {
    private val ladder = listOf(Vendor.FAN_QUIET, Vendor.FAN_SMART, Vendor.FAN_SPORT)
    private var hot = 0
    private var cool = 0
    var mode: Int = base
        private set

    companion object {
        const val CHIP_HOT = 80f
        const val CHIP_COOL = 68f
        const val BATTERY_HOT = 45f
        const val BATTERY_COOL = 41f
    }

    fun next(chip: Float, battery: Float = Float.NaN): Int {
        if (chip.isNaN() && battery.isNaN()) return mode           // no reading this time: change nothing
        val isHot = (!chip.isNaN() && chip >= CHIP_HOT) || (!battery.isNaN() && battery >= BATTERY_HOT)
        val isCool = (chip.isNaN() || chip <= CHIP_COOL) && (battery.isNaN() || battery <= BATTERY_COOL)
        hot = if (isHot) hot + 1 else 0
        cool = if (isCool) cool + 1 else 0
        val at = ladder.indexOf(mode)
        if (hot >= 2 && at < ladder.size - 1) {           // two hot readings in a row: one step up
            mode = ladder[at + 1]; hot = 0; cool = 0
        } else if (cool >= 6 && at > ladder.indexOf(base)) { // six cool readings in a row: one step down
            mode = ladder[at - 1]; hot = 0; cool = 0
        }
        return mode
    }
}

/** A colour for each console, for lighting the sticks while one of its games is running. */
object ConsoleColor {
    private val colors: Map<String, Long> = mapOf(
        "atari2600" to 0xFFE0701E, "arcade" to 0xFFFF3B30, "nes" to 0xFFE60012, "mastersystem" to 0xFFD40000,
        "tg16" to 0xFFFF6A00, "tg-cd" to 0xFFFF6A00, "genesis" to 0xFF1E5AFF, "segacd" to 0xFF1E5AFF,
        "gb" to 0xFF8BAC0F, "gbc" to 0xFF00B2A9, "gamegear" to 0xFF00A0E9, "snes" to 0xFF8A63D2,
        "psx" to 0xFFB8C0D0, "saturn" to 0xFF4B6CB7, "n64" to 0xFF00A650, "dreamcast" to 0xFFFF6A13,
        "ps2" to 0xFF2E6DB4, "gba" to 0xFF5A4FCF, "gc" to 0xFF6A5ACD, "xbox" to 0xFF107C10,
        "nds" to 0xFFC8D0E0, "psp" to 0xFFB8C0D0, "wii" to 0xFF8EE3F5,
   )
    fun of(system: String): Int? = colors[system]?.toInt()
}

// ---------------------------------------------------------------------------------------------
// The speakers. A five-point tone shape, kept as tenths of a decibel at Android's usual five bands.
// ---------------------------------------------------------------------------------------------

/**
 * The player's own tone, in tenths of a decibel at five points, laid on top of [SpeakerChain]'s correction. Zero
 * everywhere is the speakers as the chain corrects them. The points sit where these speakers play: nothing below
 * 300 Hz, since they cannot make it.
 */
/** Telling the Retroid Pocket Nova apart, for what was measured on it: its firmware names itself "RPN_...". */
object NovaCheck {
    fun isNova(display: String?, model: String?): Boolean =
        display.orEmpty().startsWith("RPN_") || model.orEmpty().contains("Pocket Nova", ignoreCase = true)
}

object Eq {
    val bands = listOf(300, 700, 1800, 4500, 12000)
    val names = listOf("Low end", "Warmth", "Middle", "Clarity", "Air")
    const val MIN = -60
    const val MAX = 60

    val presets: Map<String, List<Int>> = linkedMapOf(
        "Neutral" to listOf(0, 0, 0, 0, 0),
        "Warmer" to listOf(10, 15, 0, -5, -10),
        "Brighter" to listOf(-5, 0, 0, 10, 15),
   )

    /** Shapes saved by earlier versions, which boosted bass the speakers cannot play, become the nearest of these. */
    private val earlier = mapOf(
        listOf(20, 0, 15, 30, 50) to "Neutral", listOf(0, 0, 0, 0, 0) to "Neutral",
        listOf(10, 0, 10, 40, 70) to "Brighter", listOf(40, 20, 0, 10, 20) to "Warmer",
   )

    fun parse(text: String): List<Int> {
        val raw = text.split(',').mapNotNull { it.trim().toIntOrNull() }
        earlier[raw]?.let { return presets.getValue(it) }
        return raw.map { it.coerceIn(MIN, MAX) }.takeIf { it.size == bands.size } ?: presets.values.first()
    }

    fun format(curve: List<Int>): String = curve.joinToString(",")
    fun name(curve: List<Int>): String = presets.entries.firstOrNull { it.value == curve }?.key ?: "Your own"

    fun label(tenths: Int): String = (if (tenths > 0) "+" else if (tenths < 0) "-" else "") + "${kotlin.math.abs(tenths) / 10}.${kotlin.math.abs(tenths) % 10} dB"

    /** The gain at any frequency: straight lines between the five points, on a scale where each octave is one step. */
    fun gainAt(hz: Int, curve: List<Int>): Int {
        if (hz <= bands.first()) return curve.first()
        if (hz >= bands.last()) return curve.last()
        val i = bands.indexOfLast { it <= hz }
        val t = (kotlin.math.ln(hz.toDouble()) - kotlin.math.ln(bands[i].toDouble())) / (kotlin.math.ln(bands[i + 1].toDouble()) - kotlin.math.ln(bands[i].toDouble()))
        return Math.round(curve[i] + (curve[i + 1] - curve[i]) * t).toInt()
    }

    /**
     * The same correction and tone as a RetroArch audio filter, for when the system-wide chain does not reach
     * RetroArch. Only the shape: RetroArch's filter has no compressor or limiter.
     */
    fun dsp(curve: List<Int>): String {
        val pts = SpeakerChain.bands(curve)
        return "filters = 1\nfilter0 = eq\neq_frequencies = \"" + pts.joinToString(" ") { it.first.toInt().toString() } +
            "\"\neq_gains = \"" + pts.joinToString(" ") { String.format(java.util.Locale.US, "%.1f", it.second) } + "\"\n"
    }
}

/**
 * One big emulator at a time. An Xbox game left open in hakuX, with other emulators beside it, can take the memory a
 * Wii game then needs; a device can freeze for a minute and more while Android moves memory about, and Dolphin is left
 * stuck. Before a game in a big emulator, every other big emulator that is running is looked at. One is closed
 * without a word only when it is known for certain to hold nothing: today that is only a Dolphin build that ends games when left, with its game
 * thread gone, read from the kernel (DolphinWatch). Any other big emulator that is running is asked about, named game or
 * not, since a game can be open in it that the launcher never saw (started from inside it, or left another way).
 * hakuX and ARMSX2 are never force-stopped: they write the Xbox hard disk image and the PlayStation 2 memory cards as
 * they go, and a stop mid-write could damage them.
 */
object Memory {
    val BIG = setOf("dolphin", "armsx2", "hakux", "cemu", "azahar")
    val NEVER_FORCE = setOf("hakux", "armsx2")      // both write whole memory cards or disks in place

    /** Another emulator as found just before a start: [running] null when it could not be told; [held] the game the launcher knows is open in it. */
    data class Other(val pkg: String, val id: String, val running: Boolean?, val held: String?, val idle: Boolean)

    sealed class Step {
        object Go : Step()
        data class Close(val pkgs: List<String>) : Step()
        data class Ask(val pkg: String, val id: String, val held: String?) : Step()
    }

    fun decide(target: String, others: List<Other>): Step {
        if (target !in BIG) return Step.Go
        // Running, or could not be told and known to hold a game: either way it is asked about.
        val up = others.filter { it.id in BIG && (it.running == true || (it.running == null && it.held != null)) }
        val busy = up.filter { !it.idle || it.held != null }
        (busy.firstOrNull { it.held != null } ?: busy.firstOrNull())?.let { return Step.Ask(it.pkg, it.id, it.held) }
        val idle = up.filter { it.idle && it.id !in NEVER_FORCE }.map { it.pkg }
        return if (idle.isEmpty()) Step.Go else Step.Close(idle)
    }
}

/** The launcher's volume steps: off, then 10 to 100 in tens, each on the nearest of Android's (15 on the Nova). */
object VolumeSteps {
    val levels = (0..100 step 10).toList()

    /** Android's step for a level, out of [max]. */
    fun index(level: Int, max: Int): Int = if (level <= 0) 0 else Math.round(level * max / 100f).coerceIn(1, max)

    /** The level shown for Android's step. */
    fun level(index: Int, max: Int): Int = if (index <= 0) 0 else (Math.round(index * 10f / max) * 10).coerceIn(10, 100)

    fun next(level: Int, dir: Int): Int {
        val i = levels.indexOfFirst { it >= level }.let { if (it < 0) levels.lastIndex else it }
        return levels[(i + dir).coerceIn(0, levels.lastIndex)]
    }
}

/**
 * The Nova's two speakers, each driven by its own Awinic AW88166 smart amplifier (I2C 2-0034 and 2-0035). The
 * amplifiers' firmware (tuned by the maker with Awinic's tools) keeps the speakers safe: it senses
 * each speaker's current and voltage and turns the sound down when a cone nears its travel or the coil its heat.
 * This chain only shapes what reaches them, through Android's DynamicsProcessing:
 *
 * 1. 2 dB of headroom first. The measured correction mostly cuts where the speakers are loudest, so this keeps the
 *    overall loudness close to what it was, and the limiter catches the rare peak the lifts push past full scale.
 * 2. A high-pass at 150 Hz, 24 dB an octave, well below anything these speakers can play. Sound sent below it is not
 *    heard, but it still uses up the cones' travel, and the amplifiers answer by turning everything down. Then the
 *    measured correction ([measured]). No bass boost below 315 Hz, real or synthetic.
 * 3. A three-band compressor, off unless the player wants it (for noisy places): the amplifiers already turn the
 *    sound down to protect the speakers, and two such stages in a row can pump.
 * 4. A limiter just under full scale, both speakers linked.
 *
 * The player's five-point tone ([Eq]) is added on top.
 */
object SpeakerChain {
    const val INPUT_GAIN = -2f
    const val HIGH_PASS = 150.0

    /** When [measured] was measured; a change to it should change this too, so a tone set on the old one is reset once. */
    const val MEASURED = "2026-11-02.2"

    /** The most any band is lifted: [TOP_LIFT] above 6.3 kHz, where these speakers fall away steeply, [LIFT] below. */
    const val LIFT = 6.0
    const val TOP_LIFT = 12.0

    /** Each EQ band's top edge, in Hz: third octaves where the speakers play, wider below and above. */
    val edges = floatArrayOf(
        63f, 90f, 112f, 125f, 140f, 160f, 180f, 200f, 250f, 315f, 400f, 500f, 630f, 800f, 1000f, 1250f, 1600f, 2000f,
        2500f, 3150f, 4000f, 5000f, 6300f, 8000f, 10000f, 12500f, 16000f, 20000f, 24000f,
   )

    /**
     * The correction for each band of [edges], in dB, measured on a Retroid Pocket Nova: sweeps through the game sound
     * path, recorded with a laptop's microphone, each speaker on its own. Fitted to four microphone places at once, so the
     * room's part of each cancels: the device held as it is played (counted twice), standing in front of the microphone
     * (twice), lying on a desk, and to one side (its top end left out, since a speaker's treble is heard from the front).
     * The target is flat to 2 kHz, then falling 1.5 dB an octave: the gentle slope a speaker heard close up sounds even
     * with, and some margin against a laptop microphone's own treble. Checked on the device at 1.3 dB (rms) from that
     * target, 400 Hz to 6.3 kHz, against 2.7 for the bare speakers; the chain did what its bands say within 0.85 dB.
     * Below about 550 Hz the amplifiers give up some 4 dB at full volume to protect the cones, so the low notes are
     * thinner up there whatever this does; above 630 Hz the shape is the same at any volume. The speakers fall away
     * steeply above 6.5 kHz (in the hands, 8 kHz is 20 dB down bare), so the top bands lift up to [TOP_LIFT].
     */
    val measured = floatArrayOf(
        0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f,     // up to 315 Hz: nothing; the speakers barely play here
        2.5f, 3f,                                   // 315 to 500 Hz: a little more of the lowest notes they can play
        -0.9f, -2.1f, -2.2f, -1.8f,                 // 500 Hz to 1.25 kHz: the boxy bump around 1 kHz taken down
        -2f, -1.4f, 0f, 0.9f, 0.8f,                 // 1.25 to 4 kHz: nearly flat (a hand around the device lifts 2.5 kHz)
        -5.9f, -3f,                                 // 4 to 6.3 kHz: the sharp peak at 5 kHz taken down
        7.6f, 12f, 12f, 6f, 3f, 0f,                 // above 6.3 kHz, where they fall away steeply: a lift, eased off above hearing
   )

    /** The correction at a frequency, in dB: the high-pass and the measured band it falls in. */
    /** The measured correction belongs to the Retroid Pocket Nova's speakers; anywhere else the chain shapes nothing itself. */
    @Volatile var onNova = true

    fun correction(hz: Double): Double {
        if (!onNova) return 0.0
        val highPass = -10 * kotlin.math.log10(1 + Math.pow(HIGH_PASS / hz, 8.0))       // fourth-order Butterworth
        val i = edges.indexOfFirst { hz <= it }.let { if (it < 0) edges.lastIndex else it }
        return highPass + measured[i]
    }

    /** Each band's top edge and gain in dB: the correction plus the player's tone, at the band's middle. */
    fun bands(tone: List<Int>): List<Pair<Float, Float>> = edges.mapIndexed { i, top ->
        val bottom = if (i == 0) 40.0 else edges[i - 1].toDouble()
        val mid = kotlin.math.sqrt(bottom * top)
        val g = correction(mid) + Eq.gainAt(mid.toInt(), tone) / 10.0
        top to g.coerceIn(-40.0, if (bottom >= 6300.0) TOP_LIFT else LIFT).toFloat()
    }

    /** One compressor band: its top edge (Hz), attack and release (ms), ratio, threshold and knee (dB), make-up gain (dB). */
    class Comp(val top: Float, val attack: Float, val release: Float, val ratio: Float, val threshold: Float, val knee: Float, val makeUp: Float)
    val compressor = listOf(
        Comp(600f, 5f, 100f, 2.5f, -20f, 6f, 4f),        // the low end: a little firmer, the cones' travel is here
        Comp(5000f, 8f, 120f, 2f, -18f, 8f, 5f),         // voices and most of the music
        Comp(24000f, 3f, 80f, 2f, -22f, 8f, 4.5f),       // the top: quick, so cymbals and hiss do not pump
   )

    /** The limiter: attack and release (ms), ratio, threshold (dB). */
    const val LIMIT_ATTACK = 1f
    const val LIMIT_RELEASE = 60f
    const val LIMIT_RATIO = 10f
    const val LIMIT_THRESHOLD = -1f
}

// ---------------------------------------------------------------------------------------------
// RetroArch, set up ahead of time. The launcher writes a config file, per-core overrides, shader
// presets and core options into a folder of its own and starts RetroArch pointed at them, so
// scaling, the CRT look, motion clarity and saving are right the first time a game starts.
// ---------------------------------------------------------------------------------------------

/**
 * Which controller is which player. The handheld's own controls are player 1 and each controller connected takes the
 * next player, in the order it connected. In Dolphin, wired controllers come before wireless ones, and the first also
 * plays as player 1, so a handheld docked to a television can be played from across the room.
 */
object Players {
    data class Pad(val name: String, val number: Int, val external: Boolean, val vendor: Int, val product: Int, val wired: Boolean)

    /**
     * The handheld's own controls. Not told by Android's "external" mark alone: Retroid's input service hands its own
     * controls to Android as a copy, marked external (seen on a Retroid Pocket Nova).
     */
    fun isOwn(p: Pad): Boolean = !p.external || p.name == "Retroid Pocket Controller"

    /**
     * Dolphin's ports and the controllers that drive each. Port 1: the handheld's own controls and the first controller
     * connected to it, so either plays as player 1; then one port for each further controller, wired ones first, in the
     * order Android numbered them.
     */
    fun dolphinPorts(pads: List<Pad>): List<List<String>> {
        val own = pads.filter(::isOwn).sortedBy { it.number }.map(::dolphinName)
        val others = pads.filter { !isOwn(it) }.sortedWith(compareBy({ !it.wired }, { it.number })).map(::dolphinName)
        return (listOf(own + others.take(1)) + others.drop(1).map { listOf(it) }).take(4)
    }

    /** Dolphin's name for an Android controller, as it writes it ("Android/1/Retroid Pocket Controller"). */
    fun dolphinName(p: Pad): String = "Android/${p.number}/${p.name}"

    /** A television: a connected display at least 1280 wide and 16:10 or wider. The DS attachment is not that shape. */
    fun isTv(width: Int, height: Int): Boolean {
        val w = maxOf(width, height); val h = minOf(width, height)
        return h > 0 && w >= 1280 && w.toFloat() / h >= 1.55f
    }

    /** Wired or not, by the kernel's list of input devices: bus 0003 is USB, 0005 Bluetooth. Name to wired. */
    fun buses(procBusInputDevices: String): Map<String, Boolean> {
        val out = HashMap<String, Boolean>()
        var bus: String? = null
        for (line in procBusInputDevices.lines()) {
            if (line.startsWith("I:")) bus = Regex("Bus=([0-9a-fA-F]{4})").find(line)?.groupValues?.get(1)
            if (line.startsWith("N:")) Regex("Name=\"(.*)\"").find(line)?.groupValues?.get(1)?.let { n -> out[n] = bus.equals("0003", true) }
        }
        return out
    }
}

/** Which mode to ask a television for (AppState.tvMode). */
object TvMode {
    data class Mode(val width: Int, val height: Int, val hz: Float)

    private fun rate(m: Mode): Double = m.width.toDouble() * m.height * m.hz
    private fun near(hz: Float, want: Int) = hz >= want - 1f && hz <= want + 1f

    /**
     * A mode at [want] Hz (120, or 60) for a television showing [now], or null when it is there already or offers
     * nothing suitable. Only the same shape, at least 1080 lines (or the television's own, if fewer), and asking no more
     * of the cable and the dock than [now], which has already shown it can be carried. At 120: 1920 by 1080 when
     * offered, since games are paced as two pictures to each of theirs at 120 exactly (119.88 counts); otherwise, and
     * at 60, the largest that fits.
     */
    fun pick(modes: List<Mode>, now: Mode, want: Int = 120): Mode? {
        if (near(now.hz, want)) return null
        val shape = now.width.toDouble() / now.height
        val fits = modes.filter {
            near(it.hz, want) && kotlin.math.abs(it.width.toDouble() / it.height - shape) < shape * 0.01 &&
                it.height >= minOf(now.height, 1080) && rate(it) <= rate(now) * 1.001
        }
        val full = fits.filter { it.width == 1920 && it.height == 1080 }
        return (if (want == 120 && full.isNotEmpty()) full else fits).maxWithOrNull(compareBy({ it.width.toLong() * it.height }, { it.hz }))
    }
}

data class Look(
    val crt: String = "GDV Mini Ultra",
    val motion: String = "Beam simulator",
    val motion3d: String = "Beam simulator",          // the 3D consoles' motion clarity (RetroPack.beam3d)
    val handheld: String = RetroPack.TRUE_LCD,
    val transparency: Boolean = true,                  // Game Boy and Game Boy Color: Pixel Transparency over the grid
    val integer: Boolean = true,
    val sharp: Boolean = false,                        // the sharper look: 3D consoles drawn past their own detail (off: the true picture)
    val crt3d: Boolean = true,                         // the 3D consoles through the same CRT as the television consoles
    val crt3dSlang: Boolean = true,                    // the collection's GDV could be made into the 3D one (see Crt3d.slang)
    val compatible: Boolean = false,                   // PlayStation and Dreamcast drawn without Vulkan, for a device where their Vulkan path fails
    val dolphinGl: Boolean = false,                    // GameCube and Wii through OpenGL instead of Vulkan
    val dolphinPad: Boolean = true,                    // the launcher maps the device's controls inside Dolphin
    val saturation: Int = 100,
    val white: String = "American TV (8000 K)",          // the television's white (RetroPack.whites)
    val resume: Boolean = true,
    val sramSeconds: Int = 10,
    val rewind: Boolean = false,
    val lag: String = "Preemptive frames",             // 8 and 16-bit consoles and handhelds: RetroPack.lagChoices
    val hz: Int = 120,
    val tv: Boolean = false,                            // docked to a television as RetroArch starts
    val panel: Pair<Int, Int> = 1280 to 960,           // the built-in screen's size, landscape: whole-pixel layouts are worked out from it
    val upright: String = "Right side down",
    val speakers: List<Int>? = null,
    val achievements: Achievements? = null,
    val m1: Int = 0,                                   // the key numbers M1 and M2 send, once taught; 0 means not known
    val m2: Int = 0,
    val extras: Map<String, Pair<String, String>> = emptyMap(),   // per system: what M1 and M2 do, where it differs from the usual
    val lighter: Map<String, Boolean> = emptyMap(),    // "system/stem" of RetroArch games run lighter (true), or once run lighter (false)
    val lighterCodes: Map<String, Boolean> = emptyMap(),   // the same for GameCube and Wii games, by disc ID
    val dolphinGlCodes: Map<String, Boolean> = emptyMap(), // GameCube and Wii games drawn with OpenGL on their own (true), or no longer (false), by disc ID
    val reconnectByName: Boolean = true,               // no two connected controllers share a name (see base)
    val signal: Map<String, String> = emptyMap(),      // per console group (RetroPack.signals): the signal it reached the screen by, where changed
) {
    /** The signal a console group reaches the screen by: the one chosen, else the usual one in an American home. */
    fun signalOf(group: String): String = signal[group]?.takeIf { it in RetroPack.signals[group].orEmpty() } ?: RetroPack.signals[group]?.first() ?: "RGB"
    /** Motion clarity needs two screen refreshes per game frame, so it only applies on a 120 Hz panel. */
    val subframes: Boolean get() = motion == "Beam simulator" && hz in 119..121
    /** The same for PlayStation, Saturn and Dreamcast (beam3d), chosen on its own. */
    val subframes3d: Boolean get() = motion3d == "Beam simulator" && hz in 119..121
    val blackFrames3d: Boolean get() = motion3d == "Black frames" && hz >= 119
    val blackFrames: Boolean get() = motion == "Black frames" && hz >= 119
    val signature: String get() = hashCode().toString()
}

/**
 * A RetroAchievements account, as RetroArch wants it. The password is typed by the player on the device and nowhere
 * else, and is handed over only until RetroArch has signed in: from then on RetroArch uses the login token it keeps,
 * and the launcher forgets the password (empty here). [fresh]: a password or account name was just typed, so RetroArch
 * is made to sign in with it instead of an older token.
 */
data class Achievements(val user: String, val password: String, val hardcore: Boolean, val fresh: Boolean = false)

object Cheevos {
    /** Whether RetroArch's settings text holds a login token for this account: RetroArch has signed in and keeps it. */
    fun signedIn(cfg: String, user: String): Boolean {
        fun value(key: String) = cfg.lineSequence().map { it.trim() }.lastOrNull { it.substringBefore('=').trim() == key }
            ?.substringAfter('=', "")?.trim()?.trim('"').orEmpty()
        return user.isNotBlank() && value("cheevos_token").isNotEmpty() && value("cheevos_username").equals(user.trim(), ignoreCase = true)
    }
}

/** What the two extra buttons on the top edge can do inside RetroArch. */
object Hotkeys {
    private val keys = linkedMapOf(
        "RetroArch menu" to "input_menu_toggle_btn",
        "Fast-forward (hold)" to "input_hold_fast_forward_btn",
        "Fast-forward (switch)" to "input_toggle_fast_forward_btn",
        "Rewind (hold)" to "input_rewind_btn",
        "Save state" to "input_save_state_btn",
        "Load state" to "input_load_state_btn",
        "Screenshot" to "input_screenshot_btn",
   )
    val actions: List<String> = listOf("Nothing") + keys.keys
    private val cartridge = setOf("atari2600", "arcade", "nes", "mastersystem", "tg16", "genesis", "gb", "snes", "gamegear", "gbc", "gba")

    /** Cartridge-era systems can rewind cheaply, so M1 rewinds there; elsewhere it opens the menu. M2 speeds things up. */
    fun usual(system: String): Pair<String, String> =
        (if (system in cartridge) "Rewind (hold)" else "RetroArch menu") to "Fast-forward (hold)"

    /** Every hotkey this app manages, set to the button that should trigger it or cleared. */
    fun binds(m1: Int, m2: Int, does: Pair<String, String>): Map<String, String> {
        if (m1 <= 0 && m2 <= 0) return emptyMap()
        val out = LinkedHashMap<String, String>()
        for (key in keys.values) out[key] = "nul"
        if (m1 > 0) keys[does.first]?.let { out[it] = m1.toString() }
        if (m2 > 0) keys[does.second]?.let { out[it] = m2.toString() }
        if (does.first == "Rewind (hold)" && m1 > 0 || does.second == "Rewind (hold)" && m2 > 0) out["rewind_enable"] = "true"
        return out
    }
}

object RetroPack {
    /** RetroArch's folder on the device's own shared storage: saves, places, BIOS files. */
    const val SHARED = "/storage/emulated/0/RetroArch"

    private class Crt(val file: String, val linear: Boolean, val border: Boolean = false)

    /** First is the default: GDV Mini Ultra. All are single-pass, so they are cheap on a handheld. */
    private val crts: Map<String, Crt> = linkedMapOf(
        "GDV Mini Ultra" to Crt("crt/shaders/crt-gdv-mini-ultra.slang", linear = false, border = true),
        "zFast" to Crt("crt/shaders/zfast_crt/zfast_crt_finemask.slang", linear = true),
        "Easymode" to Crt("crt/shaders/crt-easymode.slang", linear = false),
        "Lottes Fast" to Crt("crt/shaders/crt-lottes-fast.slang", linear = true),
        "CRT-Pi" to Crt("crt/shaders/crt-pi.slang", linear = true),
   )
    val crtChoices: List<String> = crts.keys.toList() + "Off"

    /**
     * The signal each console reached a television by, first the usual one in an American home. The CRT shader is the tube; this is the cable in front of it. The NES,
     * TurboGrafx-16 and Atari came with RF or composite only; the Genesis was seen through composite, which is what turns
     * its dithered stripes (Sonic's waterfalls, shadows) into the transparency its artists drew for; the SNES was often on
     * S-video, which keeps it sharp but softens its colour and blends its hi-res see-through effects; arcade monitors took
     * RGB. The SNES, too, came with an RF switch and was mostly
     * seen through composite; S-video needed a cable sold apart and a set that took it, rare before the mid-1990s. RF is
     * what most of these consoles came with in the box, offered as it was. The PlayStation and Saturn stay on composite
     * too: their art leaned on it, the PlayStation's dither pattern blending into smooth shading and the Saturn's mesh
     * see-through (a checkerboard) into transparency; RGB, which both put out, shows them as grain and dots. From the
     * Nintendo 64 on, each console gets the best connection it had, since nothing in their art needed composite (2026-11-02):
     * S-video for the Nintendo 64, the best an American one could do (it had no RGB, and its own chip already smoothed
     * the picture); the VGA box for the Dreamcast, which tells the console a VGA cable is in; component for GameCube,
     * Wii and PlayStation 2, which then send 480 progressive lines where the game offers them. Composite, the cable in
     * their boxes, stays a choice.
     */
    val signals: Map<String, List<String>> = linkedMapOf(
        "nes" to listOf("Composite", "S-video", "RGB", "RF"),
        "snes" to listOf("Composite", "S-video", "RGB", "RF"),
        "genesis" to listOf("Composite", "S-video", "RGB", "RF"),
        "tg16" to listOf("Composite", "S-video", "RGB", "RF"),
        "atari2600" to listOf("Composite", "S-video", "RGB"),
        "arcade" to listOf("RGB", "S-video", "Composite"),
        "psx" to listOf("Composite", "S-video", "RGB"),
        "n64" to listOf("S-video", "Composite"),
        "saturn" to listOf("Composite", "S-video", "RGB"),
        "dreamcast" to listOf("VGA", "S-video", "Composite"),
        "gcwii" to listOf("Component", "S-video", "Composite"),
        "ps2" to listOf("Component", "S-video", "Composite"),
    )

    /**
     * The 480-line television's settings for a console group (GameCube and Wii in Dolphin, PlayStation 2 in ARMSX2,
     * [Tv480]), from the cable and the set it went to, as those sets really showed them (2026-11-02, research on the
     * displays of each period):
     *  - Component: 480 progressive lines on a tube that drew them (a professional monitor, or an HD CRT that took
     *    480p), seen up close, its phosphors in view: each line a beam with a dark gap in dark parts, and the
     *    phosphor grille.
     *  - S-video and composite: 480 interlaced lines on a home set, whose fields partly filled each other's gaps.
     * The grille at the same strength for all. Then the signal as the shader numbers it (0 component, 1 S-video,
     * 2 composite) and the set's white.
     */
    data class TvLook(val lines: Double, val signal: Int, val white: String, val mask: Double)
    fun tvLook(look: Look, group: String): TvLook {
        val signal = look.signalOf(group)
        val (lines, mask) = if (signal == "Component") 1.0 to 0.5 else 0.6 to 0.5
        return TvLook(lines, when (signal) { "Component" -> 0; "S-video" -> 1; else -> 2 }, whiteValue(look), mask)
    }
    /** Which console group each television core draws for: one signal per core. */
    val signalGroup = mapOf("Nestopia" to "nes", "Snes9x" to "snes", "Genesis Plus GX" to "genesis", "Beetle PCE" to "tg16",
        "Stella" to "atari2600", "FinalBurn Neo" to "arcade", "MAME 2003-Plus" to "arcade")

    /**
     * How each console's colour signal was timed, for guest's filter (2026-11-02, from the consoles' clocks). The NES and
     * SNES lines are 227⅓ colour cycles long, so their artifacts step a third of a cycle a line (3-phase), and a hi-res
     * SNES picture (512 across, as in Kirby's Dream Land 3) is the same signal at twice the pixels (auto_res), which a
     * television blended. Genesis and Master System lines are exactly 228 cycles, so their artifacts stand still and are
     * the same on every line: the rainbow in Sonic's waterfalls (ntsc_rainbow1). The Master System's pixels are the
     * NES's width, which guest's PC Engine timing gives. The TurboGrafx's lines are 227½ cycles (its own timing,
     * already set by pce) and its 512-wide games take auto_res too.
     */
    private fun ntscTiming(group: String, sms: Boolean): List<String> = when {
        sms -> listOf("ntsc_phase = \"5.0\"", "ntsc_rainbow1 = \"1.0\"")
        group == "nes" -> listOf("ntsc_phase = \"3.0\"")
        group == "snes" -> listOf("ntsc_phase = \"3.0\"", "auto_res = \"1.0\"")
        group == "genesis" -> listOf("ntsc_rainbow1 = \"1.0\"")
        group == "tg16" -> listOf("auto_res = \"1.0\"")
        else -> emptyList()
    }

    /**
     * Guest's NTSC filter from the collection (the passes of its ntsc-adaptive preset), as passes [start] to [start]+3:
     * the picture encoded and decoded as a television signal at the console's own width, after the beam, before the tube.
     * Composite keeps its artifacts and fringes (the defaults); S-video has neither. TurboGrafx gets the filter's own
     * PC Engine timing. Returns the pass lines and the parameter lines apart, since parameters go after every pass.
     */
    fun ntscPasses(p: Paths, start: Int, signal: String, pce: Boolean = false, extra: List<String> = emptyList()): Pair<List<String>, List<String>> {
        val dir = "${p.slang}/crt/shaders/guest/advanced/ntsc"
        val a = start; val b = start + 1; val c = start + 2; val d = start + 3
        val passes = listOf(
            "shader$a = \"${p.slang}/stock.slang\"", "alias$a = \"PrePass0\"", "filter_linear$a = \"false\"", "scale_type$a = \"source\"", "scale$a = \"1.0\"",
            "shader$b = \"$dir/ntsc-pass1.slang\"", "alias$b = \"NPass1\"", "filter_linear$b = \"false\"", "float_framebuffer$b = \"true\"",
            "scale_type_x$b = \"source\"", "scale_x$b = \"4.0\"", "scale_type_y$b = \"source\"", "scale_y$b = \"1.0\"",
            "shader$c = \"$dir/ntsc-pass2.slang\"", "filter_linear$c = \"true\"", "float_framebuffer$c = \"true\"",
            "scale_type_x$c = \"source\"", "scale_x$c = \"0.5\"", "scale_type_y$c = \"source\"", "scale_y$c = \"1.0\"",
            "shader$d = \"$dir/ntsc-pass3.slang\"", "filter_linear$d = \"true\"", "scale_type$d = \"source\"", "scale$d = \"1.0\"",
       )
        val af = when (signal) { "RF" -> "2.0"; "Composite" -> "1.0"; else -> "0.0" }   // the filter's own values for each
        val params = listOf("cust_artifacting = \"$af\"", "cust_fringing = \"$af\"") + (if (pce) listOf("ntsc_phase = \"5.0\"") else emptyList()) + extra
        return passes to params
    }
    val motionChoices = listOf("Beam simulator", "Black frames", "Off")
    val lagChoices = listOf("Preemptive frames", "Run-ahead", "Off")
    /** Cores whose save states are not repeatable (their info file says "basic", not "deterministic"): RetroArch cannot
     *  re-run a frame from them, so neither kind of lag reduction is asked of them. */
    val noLag = setOf("MAME 2003-Plus")

    /**
     * RetroArch's own sound buffer for the cartridge consoles, handhelds and arcade: 48 ms instead of its usual 128, so
     * sound comes about 80 ms sooner (Android adds about 120 ms of its own after it). Measured on a Retroid Pocket Nova:
     * no underrun in play on NES, SNES, TurboGrafx, arcade or GBA. The 3D consoles keep 128: they can stall for a tenth
     * of a second (Dreamcast while loading), which a short buffer would hear as a gap.
     */
    private val quickSound = mapOf("audio_latency" to "48")
    private val lagOff = mapOf("run_ahead_enabled" to "false", "preemptive_frames_enable" to "false")
    private val lcds = linkedMapOf("LCD3x" to "handheld/lcd3x.slangp", "LCD grid" to "handheld/lcd-grid-v2.slangp")
    /** The handhelds' default screen: each as its own screen was (the true picture, 2026-11-02). */
    const val TRUE_LCD = "As the console"
    /** When the true picture's defaults came in: a version saved before then is put back to them once (Store). */
    const val TRUE_PICTURE = "2026-11-02"
    private const val CORE = "22"
    private const val CUSTOM = "23"

    /**
     * The original Game Boy's screen: the collection's Game Boy dot-matrix shader, the one that draws it as it was made,
     * dark dots of ink over a green reflector that shows between them, each dot's shadow on the reflector, and the slow
     * screen's smear (response 0.555: an STN panel of its day kept about half of each frame into the next, which also
     * turned the 30 Hz flicker some games used into the see-through it was meant as). It wants the core's plain grey
     * levels and no blending of its own (gambatte). Pixel-perfect dots at this panel's six times.
     */
    private fun gbScreen(p: Paths) = "#reference \"${p.slang}/handheld/gameboy.slangp\"\n" +
        "integer_mode = \"1.000000\"\nresponse_time = \"0.555000\"\npalette = \"0.000000\"\n"

    /**
     * The Game Boy Color's screen: its pixels' measured shape (authentic_gbc, from a photo of the screen's sub-pixels),
     * the unlit screen showing through pale pixels with a neutral backing (Pixel Transparency; the core already did the
     * colour), and a third of each frame kept into the next. The response pass comes first: it compares frames as the
     * core made them.
     */
    private fun gbcScreen(p: Paths) = listOf(
        "shaders = \"4\"",
        "shader0 = \"${p.slang}/motionblur/shaders/response-time.slang\"", "filter_linear0 = \"false\"", "scale_type0 = \"source\"", "scale0 = \"1.0\"",
        "shader1 = \"${p.slang}/handheld/shaders/authentic_gbc/to_lin_fast.slang\"", "filter_linear1 = \"false\"", "scale_type1 = \"source\"", "scale1 = \"1.0\"",
        "srgb_framebuffer1 = \"true\"",
        "shader2 = \"${p.slang}/handheld/shaders/authentic_gbc/authentic_gbc_fast.slang\"", "filter_linear2 = \"false\"", "scale_type2 = \"viewport\"",
        "shader3 = \"${p.slang}/handheld/shaders/pixel_transparency/pixel_transparency.slang\"", "filter_linear3 = \"false\"", "scale_type3 = \"viewport\"",
        "response_time = \"0.333000\"", "PT_PALETTE = \"4.000000\"", "PT_POLARIZER = \"0.000000\"", "PT_HIGHLIGHTS = \"0.000000\"",
    ).joinToString("\n") + "\n"

    /**
     * The Game Boy Advance's screen: the original's unlit reflective screen, with its darker, deeper colour (the
     * collection's GBA colour profile, done in the shader so all 32 grey levels survive; the core's own merged the
     * darkest), under an LCD grid brightened a little, since this panel lights it from behind.
     */
    private fun gbaScreen(p: Paths) = "#reference \"${p.slang}/presets/handheld-plus-color-mod/lcd-grid-v2-gba-color.slangp\"\n" +
        "gain = \"1.5\"\nRSUBPIX_R = \"0.75\"\nGSUBPIX_G = \"0.75\"\nBSUBPIX_B = \"0.75\"\n"

    /** Nintendo DS: the original DS's colour (its tinted grey levels too), then a scaler that keeps pixels even at any size. */
    private fun dsScreen(p: Paths) = listOf(
        "shaders = \"4\"",
        "shader0 = \"${p.slang}/reshade/shaders/LUT/multiLUT.slang\"", "filter_linear0 = \"false\"", "scale_type0 = \"source\"", "scale0 = \"1.0\"",
        "shader1 = \"${p.slang}/handheld/shaders/color/nds-color.slang\"", "filter_linear1 = \"false\"", "scale_type1 = \"source\"", "scale1 = \"1.0\"",
        "shader2 = \"${p.slang}/pixel-art-scaling/shaders/pixel_aa/to_lin.slang\"", "filter_linear2 = \"false\"", "scale_type2 = \"source\"", "scale2 = \"1.0\"",
        "float_framebuffer2 = \"true\"",
        "shader3 = \"${p.slang}/pixel-art-scaling/shaders/pixel_aa/pixel_aa.slang\"", "filter_linear3 = \"true\"", "scale_type3 = \"viewport\"",
        "textures = \"SamplerLUT1;SamplerLUT2\"",
        "SamplerLUT1 = \"${p.slang}/reshade/shaders/LUT/64.png\"", "SamplerLUT1_linear = \"true\"",
        "SamplerLUT2 = \"${p.slang}/handheld/shaders/color/lut/nds-grey.png\"", "SamplerLUT2_linear = \"true\"",
        "LUT_selector_param = \"2.000000\"",
    ).joinToString("\n") + "\n"

    /**
     * Gambatte's options. [gbc] false: the original Game Boy's plain grey levels and no blending, for its dot-matrix
     * screen ([gbScreen]) to colour and smear; with another handheld screen chosen, the core's own "GB - DMG" palette
     * (the earlier "Special 1" was one of its extra palettes, not a Game Boy's) and its blending. [gbc] true: the
     * Color's own colour, corrected the accurate way for its central front light, with a Game Boy game shown in the
     * colours a Color gave it.
     */
    private fun gambatte(look: Look, gbc: Boolean): Map<String, String> {
        val trueLcd = look.handheld == TRUE_LCD
        val base = mapOf("gambatte_gbc_color_correction" to "GBC only", "gambatte_gbc_color_correction_mode" to "accurate",
            "gambatte_gbc_frontlight_position" to "central", "gambatte_dark_filter_level" to "0")
        return base + when {
            gbc -> mapOf("gambatte_gb_colorization" to "GBC", "gambatte_mix_frames" to if (trueLcd) "disabled" else "mix")
            trueLcd -> mapOf("gambatte_gb_colorization" to "disabled", "gambatte_mix_frames" to "disabled")
            else -> mapOf("gambatte_gb_colorization" to "internal", "gambatte_gb_internal_palette" to "GB - DMG", "gambatte_mix_frames" to "mix")
        }
    }

    /**
     * The Game Gear's picture on the built-in screen at its own 6:5 pixel shape, 6 x 5 screen pixels to a pixel or a
     * whole multiple of that: on a 4:3 screen of at least 960 x 720 only. Null elsewhere, and on a television.
     */
    fun gameGearSize(look: Look): Pair<Int, Int>? {
        val (w, h) = look.panel
        if (look.tv || w * 3 != h * 4) return null
        val k = minOf(w / 960, h / 720)
        return if (k >= 1) 960 * k to 720 * k else null
    }

    /** Genesis Plus GX's options: [borders], the 16 lines of border colour a set showed around a 224-line picture. */
    private fun genesisOpt(borders: Boolean) = mapOf("genesis_plus_gx_system_bram" to "per game",
        "genesis_plus_gx_overscan" to if (borders) "top/bottom" else "disabled", "genesis_plus_gx_aspect_ratio" to "auto")
    // Pixel Transparency, from the shader pack on the card: on the unlit Game Boy and Game Boy Color screens, white and
    // pale pixels let the screen show through instead of glowing, with a faint shadow under the picture. Drawn over the
    // grid chosen for the handhelds (the pack has a preset for each), or alone when that is Off.
    private val transparent = mapOf(
        "LCD3x" to "presets/pixel_transparency/pixel_transparency-lcd3x.slangp",
        "LCD grid" to "presets/pixel_transparency/pixel_transparency-lcd-grid-v2.slangp",
        "Off" to "handheld/pixel_transparency.slangp",
   )
    val handheldChoices: List<String> = listOf(TRUE_LCD) + lcds.keys.toList() + "Off"
    val uprightChoices = listOf("Right side down", "Left side down", "Keep it level")

    /**
     * Arcade games made for a screen stood on its side. With the device turned, they fill the panel.
     * FinalBurn Neo turns the picture ("TATE" modes, vertical games only) but not the controls, so each
     * of these games gets a remap that turns the sticks, the d-pad and the four face buttons with it.
     */
    val uprightSets: Set<String> = setOf(
        "pacman", "mspacman", "galaga", "dkong", "dkongjr", "dkong3", "invaders", "digdug", "1941", "1942", "1943", "19xx",
        "ddonpach", "donpachi", "dfeveron", "esprade", "guwange", "s1945", "s1945ii", "s1945iii", "gunbird", "gunbird2",
        "galaxian", "galaga88", "gaplus", "xevious", "frogger", "centiped", "milliped", "qbert", "burgertm", "phoenix",
        "pooyan", "scramble", "gyruss", "timeplt", "ckong", "raiden", "raiden2", "raidendx", "truxton", "truxton2",
        "twincobr", "batsugun", "batrider", "bbakraid", "mahoudai", "shippumd", "varth", "gunnail", "aerofgt", "sonicwi",
        "ladybug", "mrdo", "zaxxon", "tutankhm", "bombjack", "arkanoid",
   )

    /** RetroPad numbers: B 0, Y 1, Up 4, Down 5, Left 6, Right 7, A 8, X 9. */
    private fun turned(rightDown: Boolean): String {
        // Which way each physical direction points once the device is turned, and which face button lands where.
        val dir = if (rightDown) mapOf("left" to 4, "right" to 5, "up" to 7, "down" to 6) else mapOf("right" to 4, "left" to 5, "down" to 7, "up" to 6)
        val face = if (rightDown) mapOf("a" to 0, "x" to 8, "y" to 9, "b" to 1) else mapOf("y" to 0, "b" to 8, "a" to 9, "x" to 1)
        val axis = mapOf("left" to "x-", "right" to "x+", "up" to "y-", "down" to "y+")
        return buildString {
            for ((d, id) in dir) append("input_player1_btn_$d = \"$id\"\n")
            for (stick in listOf("l", "r")) for ((d, id) in dir) append("input_player1_stk_${stick}_${axis.getValue(d)} = \"$id\"\n")
            for ((b, id) in face) append("input_player1_btn_$b = \"$id\"\n")
        }
    }

    /** Folder names are the cores' own names, which is what RetroArch looks for. */
    val tv = mapOf(
        "Nestopia" to listOf("nes"), "Snes9x" to listOf("snes"), "Genesis Plus GX" to listOf("genesis", "mastersystem", "segacd"),
        "Beetle PCE" to listOf("tg16", "tg-cd"), "Stella" to listOf("atari2600"),
        "FinalBurn Neo" to listOf("arcade"), "MAME 2003-Plus" to listOf("arcade"),
   )
    val handhelds = mapOf("Gambatte" to listOf("gb", "gbc"), "mGBA" to listOf("gba"))
    val threeD = mapOf(
        "Mupen64Plus-Next" to listOf("n64"), "Beetle Saturn" to listOf("saturn"), "Flycast" to listOf("dreamcast"),
        "SwanStation" to listOf("psx"), "Beetle PSX HW" to listOf("psx"),
   )

    /** Game Boy Advance games whose cartridge had a sunlight sensor, by file name without its ending. */
    val solar = listOf("Boktai - The Sun Is in Your Hand (USA)", "Boktai 2 - Solar Boy Django (USA)")

    /** Two screens shown on one. */
    val dual = mapOf("melonDS DS" to listOf("nds"))

    class Paths(val home: String, val pkg: String, val dataDir: String, val apk: String) {
        val config: String get() = "$home/retroarch.cfg"
        val logs: String get() = "$home/logs"
        val log: String get() = "$logs/retroarch.log"
        val slang: String get() = "$home/shaders/shaders_slang"
    }

    private fun b(v: Boolean) = if (v) "true" else "false"


    /** The keys in retroarch.cfg that the launcher owns. Everything else in the file is left as RetroArch wrote it. */
    fun base(look: Look, p: Paths): Map<String, String> = linkedMapOf(
        // RetroArch's own account of each start, one file, written over every time. The launcher reads it to find out
        // when a game did not open, which RetroArch has no other way of telling anyone.
        "log_verbosity" to "true", "log_to_file" to "true", "log_to_file_timestamp" to "false", "log_dir" to p.logs,
        "video_driver" to "vulkan",                     // slang shaders need it
        "video_fullscreen" to "true",
        "aspect_ratio_index" to "22",                   // the shape the core reports
        "video_scale_integer" to b(look.integer),
        "video_scale_integer_axis" to "0",
        "video_scale_integer_scaling" to "0",
        "video_smooth" to "false",
        "video_shader_enable" to "true",
        "auto_shaders_enable" to "true",
        "video_shader_subframes" to "1",
        "video_black_frame_insertion" to "0",
        "video_refresh_rate" to "${look.hz}.000000",
        "video_vsync" to "true",
        "video_swap_interval" to "0",                   // automatic: 60 fps games on a 120 Hz panel show each frame twice
        // RetroArch's queue of pictures. Asked for 3, Android hands it 4, and a picture waits about 31 ms to be shown at
        // 120 Hz; asked for 2 it gets 3, and the wait is about 23 ms, a frame of a 120 Hz screen sooner. Measured on the
        // Nova's panel for every Vulkan console: 120 pictures a second, none late, still or with buttons
        // pressed 30 times a second. A television keeps 3 until it has been measured there (its pictures are larger).
        "video_max_swapchain_images" to if (look.tv) "3" else "2",
        "audio_sync" to "true",
        "savestate_auto_save" to b(look.resume),
        "savestate_auto_load" to b(look.resume),
        "autosave_interval" to look.sramSeconds.toString(),
        // A place saved on leaving can be older than the game's own save: loading it must never roll that save back.
        "block_sram_overwrite" to "true",
        "sort_savefiles_enable" to "true",
        "sort_savestates_enable" to "true",
        "sort_savefiles_by_content_enable" to "false",
        "sort_savestates_by_content_enable" to "false",
        "savefiles_in_content_dir" to "false",
        "savestates_in_content_dir" to "false",
        // Said outright rather than left to RetroArch's own choice, which depends on the build and on when it was let
        // into storage: the launcher copies BIOS files to this system folder and backs up these two save folders.
        "savefile_directory" to "$SHARED/saves",
        "savestate_directory" to "$SHARED/states",
        "system_directory" to "$SHARED/system",
        "input_menu_toggle_gamepad_combo" to "7",       // hold Start for two seconds
        "input_quit_gamepad_combo" to "4",              // Start and Select together: leave the game, keeping your place
        "quit_press_twice" to "false",
        // Disc changes from the launcher (Next disc): F10 opens and closes the disc tray, as a console's lid, and F11 puts
        // in the next disc. Keyboard keys only, which the Nova's controls cannot send: nothing pressed in play reaches
        // them. The launcher presses them on a keyboard that exists for a few seconds (DiscKeys). RetroArch takes hotkeys
        // only from a real device: keys and pad buttons pressed with Android's `input` command are ignored (checked on
        // the Nova).
        "input_disk_eject_toggle" to "f10",
        "input_disk_next" to "f11",
        "input_disk_eject_toggle_btn" to "nul",
        "input_disk_next_btn" to "nul",
        "input_overlay_enable" to "false",              // a handheld has its own buttons: never draw touch buttons over the game
        "input_overlay_hide_when_gamepad_connected" to "true",
        // A controller that drops and comes back gets its old player number again, found by its name, instead of the
        // next free one (3, then 4...). RetroArch matches by name alone, so two controllers of the same name would take
        // each other's place: with two such connected, it is left off.
        "android_input_disconnect_workaround" to b(look.reconnectByName),
        // Players in the order controllers connect: the handheld's own first, then each one connected. No controller is
        // held for a player by name, and every game starts from that plain order (RetroArch otherwise keeps the order
        // it had when it last closed).
        *(1..4).flatMap { listOf("input_player${it}_reserved_device" to "", "input_player${it}_device_reservation_type" to "0") }.toTypedArray(),
        *(1..4).map { "input_player${it}_joypad_index" to "${it - 1}" }.toTypedArray(),
        "global_core_options" to "false",
        "game_specific_options" to "true",
        "auto_overrides_enable" to "true",
        "rgui_config_directory" to "${p.home}/config",
        "input_remapping_directory" to "${p.home}/config/remaps",
        "input_remap_binds_enable" to "true",
        "video_shader_dir" to "${p.home}/shaders",
        "audio_dsp_plugin" to if (look.speakers != null) "${p.home}/config/speakers.dsp" else "",
        // Nothing written over the first seconds of a game: no "controller configured", no settings or remap "loaded",
        // no disc "set", no place "loaded", no achievements sign-in or summary. What is kept says something happened
        // because of a press: a disc changed, fast-forward, a picture taken, an achievement unlocked.
        "notification_show_autoconfig" to "false",
        "notification_show_autoconfig_fails" to "false",
        "notification_show_config_override_load" to "false",
        "notification_show_remap_load" to "false",
        "notification_show_set_initial_disk" to "false",
        "notification_show_save_state" to "false",
        "notification_show_patch_applied" to "false",
        "notification_show_cheats_applied" to "false",
        "notification_show_refresh_rate" to "false",
        "notification_show_netplay_extra" to "false",
        "cheevos_visibility_account" to "false",
        "cheevos_visibility_summary" to "2",            // off
        "cheevos_enable" to b(look.achievements != null),
   ) + (look.achievements?.let { a ->
        linkedMapOf(
            "cheevos_username" to a.user,
            "cheevos_hardcore_mode_enable" to b(a.hardcore),
            "cheevos_richpresence_enable" to "true",
            "cheevos_leaderboards_enable" to "true",
       ) + when {
            a.fresh -> mapOf("cheevos_password" to a.password, "cheevos_token" to "")   // sign in with what was just typed, not an older token
            else -> mapOf("cheevos_password" to a.password)       // empty once RetroArch has its token: no password left in the file
        }
    } ?: mapOf("cheevos_password" to ""))                         // achievements off: no password left in the file either

    /** What RetroArch must be handed at launch so it uses the setup above. */
    fun extras(look: Look, p: Paths): Map<String, String> = linkedMapOf(
        "CONFIGFILE" to p.config,
        "DATADIR" to p.dataDir,
        "SDCARD" to "/storage/emulated/0",
        "EXTERNAL" to "/storage/emulated/0/Android/data/${p.pkg}/files",
        "APK" to p.apk,
        "REFRESH" to look.hz.toString(),
   )

    private fun cfg(map: Map<String, String>): String = map.entries.joinToString("") { (k, v) -> "$k = \"$v\"\n" }

    private fun satValue(look: Look) = "${look.saturation / 100}.${(look.saturation % 100).toString().padStart(2, '0')}"

    /**
     * The television's white, for [Colour]'s pass: American sets averaged 8,000 K, far bluer than the broadcast
     * standard's 6,500 K, and Japanese ones ran at 9,300 K (Colour has the sources).
     */
    val whites: Map<String, String> = linkedMapOf("American TV (8000 K)" to "2.0", "Studio (6500 K)" to "0.0", "Japanese TV (9300 K)" to "3.0")
    val whiteChoices: List<String> get() = whites.keys.toList()
    fun whiteValue(look: Look) = whites[look.white] ?: "2.0"

    /**
     * GDV as a tube seen up close, its phosphors in view and not only its lines (2026-11-02): brighter lines widened into the gaps (beam_max 2.5), as a tube's beam bloomed with current;
     * the phosphors as [Display] says, the lit color let through half again and the others half (maskDark 0.5,
     * maskLight 1.5), easing in bright parts (thres 0.3), never GDV's own dark column, which is no phosphor pattern; a
     * tenth more light in bright parts and a tenth of glow, which give back what the mask takes (measured on a bright
     * frame: the same average light as no mask, white brighter); no extra saturation; gamma out 2.3, which on this 2.2
     * panel centers the whole curve on a tube's 2.4; no vignette.
     */
    private fun gdvParams(look: Look, glsl: Boolean, display: Display = Display.TV): List<String> = listOf(
        "sat = \"${satValue(look)}\"", "beam_max = \"2.50\"", "brightboost = \"1.10\"", "glow = \"0.10\"",
        "vignette = \"0.0\"", "scanline = \"${display.scanline}\"",
        "shadowMask = \"${display.mask}\"", "maskDark = \"0.5\"", "maskLight = \"1.5\"", "thres = \"0.3\"",
    ) +
        (if (glsl) listOf("gamma_out = \"2.3\"") else listOf("warpY = \"0.0\"", "gamma_out_red = \"2.3\"", "gamma_out_green = \"2.3\"", "gamma_out_blue = \"2.3\""))

    /** GDV's mask types: a slot mask (red, green and blue slots in staggered rows, most American sets and arcade
     * monitors) and an aperture grille (red, green and blue stripes, a Trinitron's or a PC monitor's). */
    const val SLOT = "1.0"
    const val GRILLE = "2.0"

    /**
     * The screen a console was seen on and its phosphors (2026-11-02, research on the displays
     * of each period). A home set's phosphors were a slot mask on most American sets: red, green and blue slots in
     * staggered rows. An arcade monitor was a television's cousin seen from 0.7 m, the same kind of mask, and like
     * Japanese sets and the Wells-Gardner monitors in American cabinets it ran far bluer, about 9300 K. The Dreamcast's
     * VGA box fed a PC monitor, 15 to 17 inches at 31 kHz, which showed 640 x 480 as fine distinct lines (a slightly
     * sharper beam, scanline 9) through the stripes of an aperture grille, and shipped at 9300 K too.
     */
    enum class Display(val scanline: String, val mask: String, val ownWhite: String?) {
        TV("10.0", SLOT, null), ARCADE("10.0", SLOT, "3.0"), MONITOR("9.0", GRILLE, "3.0")
    }
    private fun whiteFor(look: Look, display: Display) = display.ownWhite ?: whiteValue(look)

    /** [Colour]'s pass as pass [n] of a preset, at the picture's own size. */
    private fun colourPass(p: Paths, n: Int, glsl: Boolean): List<String> = listOf(
        "shader$n = \"${p.home}/${Crt3d.DIR}/${if (glsl) Colour.GLSL else Colour.SLANG}\"", "filter_linear$n = \"false\"",
        "scale_type$n = \"source\"", "scale$n = \"1.0\"")

    /**
     * The 3D consoles' CRT: [halvings] plain passes that each average pairs of rows, which brings a picture drawn at
     * two or four times its detail back to the console's own lines, then GDV drawn over the screen. Flat, with the
     * television consoles' saturation.
     *
     * The true picture (look.sharp off) averages columns as well as rows, so a picture drawn larger comes back to the
     * console's own size both ways (Nintendo 64: 1280 x 960 to 320 x 240, close to what its video chip put out), and a
     * picture of 400 lines or more is drawn without gaps between its lines: those consoles sent 480 interlaced lines,
     * whose two fields fill each other's gaps on a television. Only the Dreamcast's VGA box, a progressive picture on a
     * monitor, keeps them. The sharper look keeps the extra detail across and draws lines at every count, as before.
     */
    fun crt3dPreset(look: Look, p: Paths, halvings: Int, glsl: Boolean, beam: Boolean = false, group: String = "", native: Boolean = false): String {
        val dir = "${p.home}/${Crt3d.DIR}"
        val stock = if (glsl) "$dir/${Crt3d.STOCK_GLSL}" else "${p.slang}/stock.slang"
        val crt = if (glsl) "$dir/${Crt3d.GLSL}" else "$dir/${Crt3d.SLANG}"
        val signal = if (group.isEmpty()) "RGB" else look.signalOf(group)
        val clean = signal == "RGB" || signal == "VGA"
        val lines = ArrayList<String>()
        val params = ArrayList<String>()
        // With the beam (Vulkan only), it comes first, at the picture's own size, as on the television consoles.
        val b = if (beam && !glsl && look.subframes3d) 1 else 0
        // Dreamcast takes Timeline's steady version (SteadyBeam): Flycast hands over pictures unevenly while a game loads.
        val steady = group == "dreamcast"
        if (b == 1) { lines += beamPass(p, steady); lines += "scale_type0 = \"source\""; lines += "scale0 = \"1.0\"" }
        for (j in 0 until halvings) {
            val i = b + j
            lines += "shader$i = \"$stock\""
            lines += "filter_linear$i = \"true\""
            // With linear filtering, half the size samples exactly between texels: each pass averages a 2 x 2 block.
            lines += "scale_type_x$i = \"source\""; lines += "scale_x$i = \"" + (if (look.sharp) "1.0" else "0.5") + "\""
            lines += "scale_type_y$i = \"source\""; lines += "scale_y$i = \"0.5\""
        }
        var c = b + halvings
        // The signal, at the console's own lines. A picture at the console's own size (Saturn) takes the collection's NTSC
        // filter; the others take Timeline's. Its colour and luma softening are set in parts of a television line (tl_width
        // 320 for every console), so each console gets the same television.
        if (!clean) {
            if (native && !glsl) {
                val (passes, ps) = ntscPasses(p, c, signal)
                lines += passes; params += ps; c += 4
                // Saturn's hi-res modes are 640 and 704 wide: the filter then counts its subcarrier in pairs of pixels.
                if (group == "saturn") params += "auto_res = \"1.0\""
            } else {
                lines += "shader$c = \"$dir/${if (glsl) Signal.GLSL else Signal.SLANG}\""
                lines += "filter_linear$c = \"true\""
                lines += "scale_type$c = \"source\""; lines += "scale$c = \"1.0\""
                params += "tl_signal = \"${Signal.value(signal)}\""
                params += "tl_width = \"320.0\""
                c += 1
            }
        }
        // The set's own colour and white, after the signal, as in the set itself.
        val display = if (signal == "VGA") Display.MONITOR else Display.TV
        lines += colourPass(p, c, glsl); params += "tl_white = \"${whiteFor(look, display)}\""; c += 1
        lines += "shader$c = \"$crt\""
        lines += "filter_linear$c = \"false\""
        if (!glsl) lines += "wrap_mode$c = \"clamp_to_border\""
        lines += "scale_type$c = \"viewport\""
        params += gdvParams(look, glsl, display)
        // 480-line pictures. On a television cable (composite, S-video) their interlaced fields blend on a set: GDV
        // averages each pair of lines (gdv_hires 400). Through VGA or RGB, or in the sharper look, each line is drawn as
        // its own beam, one panel row of beam and one of gap (Crt3d.slang's quarter-line phase): fine distinct lines, as a
        // monitor showed them.
        params += "gdv_hires = \"" + (if (look.sharp || clean) "0.0" else "400.0") + "\""
        if (b == 1) { if (steady) params += "GAIN_VS_BLUR = \"0.5\"" else beamSettings(params) }
        return (listOf("shaders = \"${c + 1}\"") + lines + params).joinToString("\n") + "\n"
    }

    /** Blur Busters' CRT beam simulator, as pass 0 of a preset; [steady], Timeline's version that holds still pictures steady. */
    private fun beamPass(p: Paths, steady: Boolean = false) =
        (if (steady) "shader0 = \"${p.home}/${Crt3d.DIR}/${SteadyBeam.SLANG}\"" else "shader0 = \"${p.slang}/subframe-bfi/shaders/crt-beam-simulator.slang\"") +
            "\nfilter_linear0 = \"false\""

    /**
     * The beam at 120 Hz: half the light in each refresh, "~0.5 recommended for 120Hz" in the shader's own notes. At 0.5
     * each picture is drawn whole in the first refresh and black in the second, as a tube lit each line once a sixtieth
     * of a second; at the shader's own 0.7, the brightest pixels spilled into the second refresh and every pass after the
     * beam was handed two different pictures a frame, which bent the tube's tone curve near white (2026-11-02).
     */
    private fun beamSettings(lines: MutableList<String>) {
        lines += "GAIN_VS_BLUR = \"0.5\""
        lines += "LCD_ANTI_RETENTION_TOGGLE = \"0.0\""       // not needed on an OLED panel
        lines += "SCAN_DIRECTION = \"0.0\""
    }

    /**
     * The 3D consoles given the beam. The beam needs RetroArch to draw each picture twice with a shader between, which its Vulkan driver
     * and its newer OpenGL driver ("glcore") do and its older one ("gl") does not (read from RetroArch 1.22.2's
     * source), but RetroArch's Android build has only the older OpenGL one. So Nintendo 64, which stays on OpenGL, goes
     * without the beam (n64Parallel), and so does Dreamcast drawn the compatible way.
     */
    fun beam3d(look: Look): List<String> = listOf("SwanStation", "Beetle PSX HW", "Beetle Saturn") +
        (if (n64Parallel(look)) listOf("Mupen64Plus-Next") else emptyList()) + (if (look.compatible) emptyList() else listOf("Flycast"))

    /**
     * A television console's chain: the beam, the signal (NTSC filter, unless RGB), the set's colour ([Colour]), then
     * the tube. The signal and colour only come with a CRT: with the screen Off the picture is drawn clean. GDV is
     * Timeline's patched copy when it could be made ([Crt3d.slang]), told to average lines from 400 as the original did.
     */
    fun tvPreset(look: Look, p: Paths, group: String = "arcade", sms: Boolean = false): String? {
        val crt = crts[look.crt]
        val gdv = look.crt == "GDV Mini Ultra"
        val ours = gdv && look.crt3dSlang
        val signal = look.signalOf(group)
        val display = if (group == "arcade") Display.ARCADE else Display.TV
        val lines = ArrayList<String>()
        val params = ArrayList<String>()
        var n = 0
        // The Genesis's measured colour levels ([GenesisDac]) first, while the core's exact levels can still be read back.
        if (group == "genesis" && !sms && crt != null) {
            lines += "shader$n = \"${p.home}/${Crt3d.DIR}/${GenesisDac.SLANG}\""; lines += "filter_linear$n = \"false\""
            lines += "scale_type$n = \"source\""; lines += "scale$n = \"1.0\""
            n++
        }
        // The beam first, at the picture's own size. (After the NTSC filter it breaks the picture:
        // doubled, then dark with only its edges; first, as on the 3D consoles, it is right.)
        if (look.subframes) {
            lines += "shader$n = \"${p.slang}/subframe-bfi/shaders/crt-beam-simulator.slang\""; lines += "filter_linear$n = \"false\""
            lines += "scale_type$n = \"" + (if (crt != null) "source" else "viewport") + "\""; if (crt != null) lines += "scale$n = \"1.0\""
            n++
        }
        // The Atari's colour clock is its pixel clock, which no setting of guest's filter has: Stella's own filter,
        // made for it, does its signal instead (stella in files()).
        if (crt != null && signal != "RGB" && group != "atari2600") {
            val (passes, ps) = ntscPasses(p, n, signal, pce = group == "tg16", extra = ntscTiming(group, sms))
            lines += passes; params += ps; n += 4
        }
        if (crt != null) {
            lines += colourPass(p, n, glsl = false); params += "tl_white = \"${whiteFor(look, display)}\""; n++
            lines += "shader$n = \"" + (if (ours) "${p.home}/${Crt3d.DIR}/${Crt3d.SLANG}" else "${p.slang}/${crt.file}") + "\""
            lines += "filter_linear$n = \"${b(crt.linear)}\""
            if (crt.border) lines += "wrap_mode$n = \"clamp_to_border\""
            lines += "scale_type$n = \"viewport\""
            n++
        }
        if (n == 0) return null
        if (look.subframes) beamSettings(params)
        if (gdv) {
            params += gdvParams(look, glsl = false, display)     // flat (warpY 0), and as its screen showed it
            if (ours) params += "gdv_hires = \"400.0\""
        }
        return (listOf("shaders = \"$n\"") + lines + params).joinToString("\n") + "\n"
    }

    /**
     * Every file under the config folder, by path relative to it. `pal` lists 50 Hz games as
     * (system, file name without extension): motion clarity is tuned for 60 Hz, so they skip it.
     */
    /** The systems where a single game can be run lighter (drawn at its console's own resolution). */
    val canRunLighter = setOf("psx", "n64", "dreamcast", "gc", "wii")

    /** PlayStation games four can play, through a multitap in the first port: by file name without its ending. */
    val multitap = setOf("CTR - Crash Team Racing (USA)", "Nickelodeon Rugrats - Search for Reptar (USA)")

    /**
     * PlayStation games that need the geometry correction worked out on the processor too (PGXP "CPU" mode), from
     * DuckStation's game database, which SwanStation does not carry. Without it Spyro's
     * portals tear and Tekken 3's and Vagrant Story's models shake. Spyro the Dragon also needs culling off.
     */
    val psxFixes: Map<String, Map<String, String>> = mapOf(
        "Spyro the Dragon (USA)" to mapOf("swanstation_GPU_PGXPCPU" to "true", "swanstation_GPU_PGXPCulling" to "false"),
        "Spyro 2 - Ripto's Rage! (USA)" to mapOf("swanstation_GPU_PGXPCPU" to "true"),
        "Spyro - Year of the Dragon (USA) (Rev 1)" to mapOf("swanstation_GPU_PGXPCPU" to "true"),
        "Tekken 3 (USA)" to mapOf("swanstation_GPU_PGXPCPU" to "true", "swanstation_GPU_PGXPTolerance" to "3.0"),
        "Vagrant Story (USA)" to mapOf("swanstation_GPU_PGXPCPU" to "true", "swanstation_GPU_PGXPTolerance" to "4.0"),
        "Resident Evil 3 - Nemesis (USA, Canada)" to mapOf("swanstation_GPU_PGXPCPU" to "true"),
   )

    private fun swanstation(look: Look, sharp: Boolean, four: Boolean = false, stem: String? = null) = swanBase(look, sharp, four) + psxFixes[stem].orEmpty()

    /**
     * PlayStation. The true picture is the console's own: 320 or so by 240, 15-bit colour with its 4 x 4 dither,
     * textures that warp and corners that wobble (it kept vertices on whole pixels), all softened by the cable. The
     * sharper look ([sharp]) draws at four times with 24-bit colour and PGXP, which straightens the wobble out.
     * Both cores are told the same, so choosing the other one does not change the picture.
     */
    private fun swanBase(look: Look, sharp: Boolean, four: Boolean) = mapOf(
        "swanstation_ControllerPorts_MultitapMode" to if (four) "Port1Only" else "Disabled",
        "swanstation_GPU_Renderer" to if (look.compatible) "Software" else "Auto",
        "swanstation_GPU_ResolutionScale" to if (sharp) "4" else "1",
        "swanstation_GPU_TrueColor" to sharp.toString(),
        "swanstation_GPU_ScaledDithering" to "false",
        "swanstation_GPU_PGXPEnable" to sharp.toString(),
        "swanstation_GPU_TextureFilter" to "Nearest",
        "swanstation_Display_ShowOSDMessages" to "false")      // the core's own notes ("PGXP culling disabled..."), not wanted either
    private fun beetlePsx(look: Look, sharp: Boolean, four: Boolean = false) = mapOf(
        "beetle_psx_hw_enable_multitap_port1" to if (four) "enabled" else "disabled",
        "beetle_psx_hw_renderer" to if (look.compatible) "software" else "hardware",
        "beetle_psx_hw_internal_resolution" to if (sharp && !look.compatible) "4x" else "1x(native)",
        "beetle_psx_hw_depth" to if (sharp) "32bpp" else "16bpp(native)",
        "beetle_psx_hw_dither_mode" to if (sharp) "disabled" else "1x(native)",
        "beetle_psx_hw_pgxp_mode" to if (sharp) "memory only" else "disabled",
        "beetle_psx_hw_filter" to "nearest",
        "beetle_psx_hw_display_osd" to "disabled")
    // With the face buttons turned by [n64Buttons] (A is the N64's A, B its B), these keep R2's C buttons where they sat
    // before: the right face button C-right, the bottom one C-down, the left one C-left, the top one C-up. The core's
    // C1 to C4 are what it receives as RetroPad A, Y, B and X.
    /**
     * Nintendo 64 stays on GLideN64 under RetroArch's older OpenGL driver, the only OpenGL one in its Android build,
     * which cannot draw a picture twice: no beam. ParaLLEl-RDP (through Vulkan, where the beam
     * runs) was tried on a Retroid Pocket Nova: Perfect Dark had no picture and 45 to 78 frames a second even at the
     * console's own detail. The renderer settings are still written, so a Nova that had ParaLLEl gets GLideN64 back.
     * What the older driver can do is plain black frames (n64Frames).
     */
    fun n64Parallel(@Suppress("UNUSED_PARAMETER") look: Look): Boolean = false

    /** Nintendo 64's motion clarity: black frames between pictures, at 120 Hz, when the 3D consoles have any. */
    fun n64Frames(look: Look): Boolean = look.motion3d != "Off" && look.hz in 119..121

    /**
     * Nintendo 64, drawn at four times ([detail], unless the game is run lighter) in both looks. The console's video chip
     * smoothed every edge and resampled the picture; GLideN64 has none of that, so the true picture averages the four
     * times back to 320 x 240 ([crt3dPreset]), the nearest thing to it. Textures use the console's own three-point
     * filter ("3point is the original system specific way", the core's own note), and nothing the console lacked is added.
     */
    private fun mupen(detail: Boolean, parallel: Boolean = false) = mapOf(
        "mupen64plus-43screensize" to if (detail) "1280x960" else "320x240",
        "mupen64plus-BilinearMode" to "3point",
        "mupen64plus-MultiSampling" to "0",
        "mupen64plus-FXAA" to "0",
        "mupen64plus-rdp-plugin" to if (parallel) "parallel" else "gliden64",
        "mupen64plus-rsp-plugin" to if (parallel) "parallel" else "hle",
        "mupen64plus-r-cbutton" to "C3", "mupen64plus-d-cbutton" to "C2", "mupen64plus-l-cbutton" to "C1", "mupen64plus-u-cbutton" to "C4",
        // A game drawing fewer than 60 pictures a second (most do: Ocarina of Time 20, GoldenEye 15 to 25) still hands one
        // over at every one of the console's 60 refreshes, repeated. Without it RetroArch got nothing on those refreshes,
        // and its black frame stayed up for 25 to 40 ms at a time: an uneven dark flicker (Banjo-Tooie on a Retroid Pocket
        // Nova: 205 late pictures in 10 s without, none with).
        "mupen64plus-FrameDuping" to "True",
   )

    /**
     * Nintendo 64: the core puts the N64's A on the bottom face button and its B on the left one. This puts them on the
     * buttons marked A and B instead (the Nova's, and any Nintendo-style pad's), for all four players. The left face
     * button, which carried B, now does nothing outside R2's C-button mode, as the right one did before.
     */
    fun n64Buttons(): String = buildString {
        for (player in 1..4) {
            append("input_player${player}_btn_a = \"0\"\n")     // A: the core's B, the N64's A
            append("input_player${player}_btn_b = \"1\"\n")     // B: the core's Y, the N64's B
            append("input_player${player}_btn_y = \"8\"\n")     // Y: the core's A, a C button under R2 only
        }
    }
    /**
     * Dreamcast. The true picture is its own 640 x 480 with plain texture filtering, as the console drew it; the sharper
     * look draws at 1280 x 960 with anisotropic filtering at 16, which keeps far textures sharp. The console is told the
     * cable chosen under Signal: VGA as through the VGA box (a game that refuses it falls back to composite by itself,
     * the core's own rule), any other as a television cable.
     */
    private fun flycast(look: Look, sharp: Boolean) = mapOf("reicast_internal_resolution" to if (sharp) "1280x960" else "640x480", "reicast_per_content_vmus" to "VMU A1",
        "reicast_anisotropic_filtering" to if (sharp) "16" else "off",
        "reicast_cable_type" to if (look.signalOf("dreamcast") == "VGA") "VGA" else "TV (Composite)")

    /** Nintendo 64 games that need a frame copy GLideN64's own list gives them upstream but this core's copy does not. */
    val n64Fixes: Map<String, Map<String, String>> = mapOf(
        "Legend of Zelda, The - Majora's Mask (USA)" to mapOf("mupen64plus-EnableCopyAuxToRDRAM" to "True"),   // the Lens of Truth and the pause screen
   )

    fun files(look: Look, p: Paths, pal: List<Pair<String, String>> = emptyList(), arcade: List<String> = emptyList()): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        // Upright arcade games: the core turns the picture for those games only, and each gets its turned controls.
        val turn = look.upright != "Keep it level"
        // MAME 2003-Plus, when chosen for arcade games, opens on its own notices (a copying disclaimer, "this game does not
        // work perfectly") that wait for a key: straight to the game instead.
        out["MAME 2003-Plus/MAME 2003-Plus.opt"] = cfg(mapOf("mame2003-plus_skip_disclaimer" to "enabled", "mame2003-plus_skip_warnings" to "enabled",
            "mame2003-plus_tate_mode" to if (turn) "enabled" else "disabled"))
        out["FinalBurn Neo/FinalBurn Neo.opt"] = cfg(mapOf("fbneo-vertical-mode" to when (look.upright) {
            "Right side down" -> "TATE alternate"
            "Left side down" -> "TATE"
            else -> "disabled"
        }))
        if (turn) for (set in arcade) if (set.lowercase() in uprightSets)
            out["remaps/FinalBurn Neo/$set.rmp"] = turned(look.upright == "Right side down")
        look.speakers?.let { out["speakers.dsp"] = Eq.dsp(it) }
        // Lag reduction, for the cartridge consoles and handhelds only: cheap there, costly on 3D cores.
        // Preemptive frames re-runs the last frame only when a button changes, so the game answers a frame sooner (a
        // sixtieth of a second) at a fraction of run-ahead's cost; a core that cannot do it says so and is left as it was.
        val old = linkedMapOf("rewind_enable" to b(look.rewind), "run_ahead_enabled" to b(look.lag == "Run-ahead"),
            "preemptive_frames_enable" to b(look.lag == "Preemptive frames"), "run_ahead_frames" to "1", "run_ahead_hide_warnings" to "true")
        val motion = linkedMapOf(
            "video_shader_subframes" to if (look.subframes) "2" else "1",
            "video_black_frame_insertion" to if (look.blackFrames) "1" else "0",
        )
        for ((core, systems) in tv) {
            val lagHere = if (core in noLag) old + lagOff else old
            out["$core/$core.cfg"] = cfg(motion + lagHere + quickSound + mapOf("video_smooth" to "false"))
            tvPreset(look, p, signalGroup[core] ?: "arcade")?.let { out["$core/$core.slangp"] = it }
            for ((system, stem) in pal) if (system in systems && (look.subframes || look.blackFrames))
                out["$core/$stem.cfg"] = cfg(mapOf("video_shader_subframes" to "1", "video_black_frame_insertion" to "0"))
        }
        // Arcade boards that do not run near 60 pictures a second get the same treatment as 50 Hz games.
        if (look.subframes || look.blackFrames) for (set in arcade) if (set.lowercase() in offPaceSets)
            out["FinalBurn Neo/$set.cfg"] = cfg(mapOf("video_shader_subframes" to "1", "video_black_frame_insertion" to "0"))
        val lcd = lcds[look.handheld]?.let { "#reference \"${p.slang}/$it\"\n" }
        val trueLcd = look.handheld == TRUE_LCD
        for (core in handhelds.keys) {
            out["$core/$core.cfg"] = cfg(old + quickSound + mapOf("video_smooth" to "false", "video_scale_integer" to "true"))
            val sheet = when {
                trueLcd -> if (core == "mGBA") gbaScreen(p) else gbcScreen(p)
                core == "Gambatte" && look.transparency -> transparent[look.handheld]?.let { "#reference \"${p.slang}/$it\"\n" }
                else -> lcd
            }
            if (sheet != null) out["$core/$core.slangp"] = sheet
        }
        // Game Boy and Game Boy Color in Gambatte, each in a folder file of its own (a folder's file stands in place of the
        // core's, so each carries every key): the original Game Boy's grey levels for the green dot-matrix screen, and the
        // Color's own colour, corrected as its washed-out reflective screen showed it.
        out["Gambatte/Gambatte.opt"] = cfg(gambatte(look, gbc = true))
        out["Gambatte/gbc.opt"] = cfg(gambatte(look, gbc = true))
        out["Gambatte/gb.opt"] = cfg(gambatte(look, gbc = false))
        if (trueLcd) out["Gambatte/gb.slangp"] = gbScreen(p)
        // A Game Boy or Game Boy Color game moved to mGBA (Settings, Emulators) keeps the unlit screen too.
        if (look.transparency && !trueLcd) transparent[look.handheld]?.let { t ->
            for (system in listOf("gb", "gbc")) out["mGBA/$system.slangp"] = "#reference \"${p.slang}/$t\"\n"
        }
        // Per system, in a file named after its folder: what the two extra buttons do there.
        val folder = LinkedHashMap<String, Map<String, String>>()
        for ((core, systems) in tv + handhelds + threeD + dual) for (system in systems) {
            val keys = Hotkeys.binds(look.m1, look.m2, look.extras[system] ?: Hotkeys.usual(system))
            if (keys.isNotEmpty()) folder["$core/$system.cfg"] = keys
        }
        // Game Gear shares a core with the Genesis but is a handheld: its folder gets the handheld treatment. Its screen
        // was a 4:3 window, 65.27 by 48.90 mm in Sega's service manual, so its 160 x 144 pixels were a fifth wider than
        // tall (6:5). On a 4:3 screen of at least 960 x 720 that is exactly 6 x 5 screen pixels each, or a whole multiple
        // of it ([gameGearSize]), centered by RetroArch itself (with whole-number scaling on, it centers a custom size and
        // adds the x and y to that, so they are 0). Square pixels drew it 17% too narrow. Any other screen, and a
        // television, takes the core's shape at whole steps both ways instead (7 x 6 each on 1080 lines, 3% narrow).
        folder["Genesis Plus GX/gamegear.cfg"] = mapOf("video_shader_subframes" to "1", "video_black_frame_insertion" to "0", "video_scale_integer" to "true") +
            (gameGearSize(look)?.let { (w, h) -> mapOf("aspect_ratio_index" to CUSTOM, "video_scale_integer_axis" to "0", "custom_viewport_width" to "$w",
                "custom_viewport_height" to "$h", "custom_viewport_x" to "0", "custom_viewport_y" to "0") }
                ?: mapOf("aspect_ratio_index" to CORE, "video_scale_integer_axis" to "1")) +
            Hotkeys.binds(look.m1, look.m2, look.extras["gamegear"] ?: Hotkeys.usual("gamegear"))
        // Master System: the shape the core reports, its pixels as wide as the NES's (8:7), with the border colour a
        // television showed above and below its 192 lines (genesisOpt): 1172 x 960, four panel rows a line like every
        // other television console. Square pixels, used before, drew it 14% too tall and lost the border. Written out, so
        // the square-pixel setting an earlier version put in this file does not stay behind.
        folder["Genesis Plus GX/mastersystem.cfg"] = mapOf("aspect_ratio_index" to CORE) + (folder["Genesis Plus GX/mastersystem.cfg"] ?: emptyMap())
        for ((path, keys) in folder) out[path] = cfg(keys)
        out["Genesis Plus GX/gamegear.slangp"] = when {
            trueLcd -> "#reference \"${p.slang}/handheld/lcd-grid-v2.slangp\"\n"
            lcd != null -> lcd
            // With the handhelds' grid Off, a plain pass of its own: without one the Game Gear falls back to the Genesis's
            // CRT preset, the core it shares.
            else -> "shaders = 1\nshader0 = \"${p.slang}/stock.slang\"\nfilter_linear0 = false\nscale_type0 = viewport\n"
        }
        // The Master System's own chain: its signal timed as its own (ntscTiming), and none of the Genesis's colour levels.
        tvPreset(look, p, "genesis", sms = true)?.let { out["Genesis Plus GX/mastersystem.slangp"] = it }
        // The first 3D consoles: smoothed when drawn plain, and 240 lines is exactly a quarter of this panel.
        val beamed = beam3d(look)
        fun motion3d(core: String) = linkedMapOf(
            "video_shader_subframes" to if (look.subframes3d && core in beamed) "2" else "1",
            "video_black_frame_insertion" to if (look.blackFrames3d && core in beamed) "1" else "0",
        )
        for (core in threeD.keys) out["$core/$core.cfg"] = cfg(mapOf("video_smooth" to "true") + motion3d(core))
        // 50 Hz games skip it, as on the television consoles.
        if (look.subframes3d || look.blackFrames3d) for ((core, systems) in threeD) if (core in beamed)
            for ((system, stem) in pal) if (system in systems)
                out["$core/$stem.cfg"] = cfg(mapOf("video_shader_subframes" to "1", "video_black_frame_insertion" to "0"))
        // Without the CRT look, the beam alone.
        if (look.subframes3d && !(look.crt3d && look.crt3dSlang)) for (core in beamed)
            out["$core/$core.slangp"] = "shaders = \"1\"\n" + beamPass(p, core == "Flycast") + "\nscale_type0 = \"viewport\"\n" +
                "GAIN_VS_BLUR = \"0.5\"\n" + (if (core == "Flycast") "" else "LCD_ANTI_RETENTION_TOGGLE = \"0.0\"\nSCAN_DIRECTION = \"0.0\"\n")
        // With the CRT look, the same GDV as the television consoles, after the picture is brought back to the console's
        // own lines: four times the detail is four rows to a line (two halvings), Dreamcast's 960 is twice its 480 (one);
        // drawn at the console's own resolution (the true picture), in software or run lighter, there is nothing to bring
        // down (none). Nintendo 64 is drawn at four times in both looks, so it always has two. Nintendo 64, and Dreamcast drawn the
        // compatible way, run under OpenGL and take the GLSL one (RetroArch picks the preset its driver can use).
        if (look.crt3d) {
            val psx = if (look.sharp && !look.compatible) 2 else 0
            val n64 = 2
            val dc = if (look.sharp) 1 else 0
            if (look.crt3dSlang) {
                out["SwanStation/SwanStation.slangp"] = crt3dPreset(look, p, psx, glsl = false, beam = true, group = "psx")
                out["Beetle PSX HW/Beetle PSX HW.slangp"] = crt3dPreset(look, p, psx, glsl = false, beam = true, group = "psx")
                out["Beetle Saturn/Beetle Saturn.slangp"] = crt3dPreset(look, p, 0, glsl = false, beam = true, group = "saturn", native = true)
                out["Flycast/Flycast.slangp"] = crt3dPreset(look, p, dc, glsl = false, beam = "Flycast" in beamed, group = "dreamcast")
                out["Mupen64Plus-Next/Mupen64Plus-Next.slangp"] = crt3dPreset(look, p, n64, glsl = false, beam = n64Parallel(look), group = "n64")
            }
            out["Flycast/Flycast.glslp"] = crt3dPreset(look, p, dc, glsl = true, group = "dreamcast")
            out["Mupen64Plus-Next/Mupen64Plus-Next.glslp"] = crt3dPreset(look, p, n64, glsl = true, group = "n64")
            // A game run lighter is drawn at its console's own lines: its own preset, with nothing to bring down. In the
            // true picture only Nintendo 64 has anything to make lighter.
            for ((key, light) in look.lighter) {
                if (!light) continue
                if (!look.sharp && key.substringBefore('/') != "n64") continue
                val stem = key.substringAfter('/')
                when (key.substringBefore('/')) {
                    "psx" -> if (look.crt3dSlang && psx > 0) {
                        out["SwanStation/$stem.slangp"] = crt3dPreset(look, p, 0, glsl = false, beam = true, group = "psx")
                        out["Beetle PSX HW/$stem.slangp"] = crt3dPreset(look, p, 0, glsl = false, beam = true, group = "psx")
                    }
                    "n64" -> {
                        if (look.crt3dSlang) out["Mupen64Plus-Next/$stem.slangp"] = crt3dPreset(look, p, 0, glsl = false, beam = n64Parallel(look), group = "n64")
                        out["Mupen64Plus-Next/$stem.glslp"] = crt3dPreset(look, p, 0, glsl = true, group = "n64")
                    }
                    "dreamcast" -> {
                        if (look.crt3dSlang) out["Flycast/$stem.slangp"] = crt3dPreset(look, p, 0, glsl = false, beam = "Flycast" in beamed, group = "dreamcast")
                        out["Flycast/$stem.glslp"] = crt3dPreset(look, p, 0, glsl = true, group = "dreamcast")
                    }
                }
            }
        }
        // Dreamcast has two ways to draw. Vulkan is the fast one. OpenGL is the fallback, and RetroArch has to be
        // started in OpenGL for the core to be offered it. Always written, so switching back switches back.
        out["Flycast/Flycast.cfg"] = cfg(mapOf("video_smooth" to "true", "video_driver" to if (look.compatible) "gl" else "vulkan") + motion3d("Flycast"))
        out["Mupen64Plus-Next/Mupen64Plus-Next.cfg"] = cfg(mapOf("video_smooth" to "true", "input_poll_type_behavior" to "0",
            "video_shader_subframes" to "1", "video_black_frame_insertion" to if (n64Frames(look)) "1" else "0"))
        // Drawn at four times their own detail, which is this panel's 960 lines exactly: the same polygons and
        // textures, with clean edges. PlayStation also loses its wobble and its dither pattern. Off puts each back
        // to what the console drew. Both values are always written, so switching back really does switch back.
        // (PlayStation's fallback is its software renderer: the console's own resolution, exact, and light enough. Its
        // OpenGL renderer wants desktop OpenGL, which Android does not have.)
        out["SwanStation/SwanStation.opt"] = cfg(swanstation(look, look.sharp))
        out["Beetle PSX HW/Beetle PSX HW.opt"] = cfg(beetlePsx(look, look.sharp))
        out["Mupen64Plus-Next/Mupen64Plus-Next.opt"] = cfg(mupen(true, n64Parallel(look)))
        out["remaps/Mupen64Plus-Next/Mupen64Plus-Next.rmp"] = n64Buttons()
        // Dreamcast: a memory card of its own for every game, kept with the other saves. The core's usual way is one
        // card shared by all of them, in the system folder where the backup does not look, and 200 blocks fill up fast.
        out["Flycast/Flycast.opt"] = cfg(flycast(look, look.sharp))
        // "Run lighter", per game: the console's own resolution for that one game. A game's own file takes the place of
        // the core's, so it carries every setting the core's does; one that was lighter and no longer is gets the usual
        // settings back in its own file, rather than keeping a lighter one nobody asked for any more.
        for ((key, light) in look.lighter) {
            val system = key.substringBefore('/')
            val stem = key.substringAfter('/')
            val sharp = look.sharp && !light
            when (system) {
                "psx" -> { out["SwanStation/$stem.opt"] = cfg(swanstation(look, sharp, stem in multitap, stem)); out["Beetle PSX HW/$stem.opt"] = cfg(beetlePsx(look, sharp, stem in multitap)) }
                "n64" -> out["Mupen64Plus-Next/$stem.opt"] = cfg(mupen(!light, n64Parallel(look)) + n64Fixes[stem].orEmpty())
                "dreamcast" -> out["Flycast/$stem.opt"] = cfg(flycast(look, sharp))
            }
        }
        // Four players: a game's own file with the multitap in, carrying every other setting the core's does.
        for (stem in multitap) if ("psx/$stem" !in look.lighter) {
            out["SwanStation/$stem.opt"] = cfg(swanstation(look, look.sharp, four = true, stem = stem))
            out["Beetle PSX HW/$stem.opt"] = cfg(beetlePsx(look, look.sharp, four = true))
        }
        for ((stem, fix) in n64Fixes) if ("n64/$stem" !in look.lighter) out["Mupen64Plus-Next/$stem.opt"] = cfg(mupen(true, n64Parallel(look)) + fix)
        // The games that need PGXP on the processor: a file of their own, carrying every other setting the core's does.
        for (stem in psxFixes.keys) if ("psx/$stem" !in look.lighter && stem !in multitap) {
            out["SwanStation/$stem.opt"] = cfg(swanstation(look, look.sharp, stem = stem))
        }
        // Saturn's interlaced hi-res games (Virtua Fighter 2's 704 x 480): each field drawn on its own lines, as a television
        // drew them, instead of the core's usual weave, which shows both at once and combs anything that moves.
        out["Beetle Saturn/Beetle Saturn.opt"] = cfg(mapOf("beetle_saturn_deinterlacer" to "bob_offset"))
        // Sega CD: the console's 8 KB of backup memory, which a few role-playing games fill between them, becomes 8 KB for each game.
        out["Genesis Plus GX/Genesis Plus GX.opt"] = cfg(genesisOpt(borders = false))
        // The television consoles show the border colour a set showed above and below the picture, 240 lines in all, each
        // in a folder file (one core option serves all four, and the Game Gear must not have it).
        for (system in listOf("genesis", "segacd", "mastersystem")) out["Genesis Plus GX/$system.opt"] = cfg(genesisOpt(borders = true))
        // The Game Gear's backlit colour screen was slow: a pixel going dark kept 80% of its light each frame (the core's
        // own model of it), which some games' flicker relied on.
        out["Genesis Plus GX/gamegear.opt"] = cfg(genesisOpt(borders = false) + mapOf("genesis_plus_gx_lcd_filter" to "enabled", "genesis_plus_gx_gg_extra" to "disabled"))
        // Nintendo DS: two screens on a single one. R2 steps through the arrangements, in this order: the top screen
        // large with the touch screen small beside it, the reverse, the top screen alone (exactly five times its size on
        // this panel), the touch screen alone, and both stacked. Whole-number scaling is off so each one fills the
        // panel, with a filter that keeps pixels even and sharp at any size.
        out["melonDS DS/melonDS DS.opt"] = cfg(mapOf(
            "melonds_number_of_screen_layouts" to "5",
            "melonds_screen_layout1" to "largescreen-top", "melonds_screen_layout2" to "largescreen-bottom",
            "melonds_screen_layout3" to "top", "melonds_screen_layout4" to "bottom", "melonds_screen_layout5" to "top-bottom",
            "melonds_hybrid_ratio" to "2",
            // The core's "Layout 1/5" label never leaves the corner, which an OLED panel would remember.
            "melonds_show_current_layout" to "disabled",
            // Nothing said as a game starts: the core's notes about its built-in BIOS and about features it lacks.
            "melonds_show_bios_warnings" to "disabled", "melonds_show_unsupported_features" to "disabled",
            // The console's own name is kept, not guessed from RetroArch: guessing finds none and says so on screen for
            // five seconds at every start ("Failed to get username"), seen on the Nova.
            "melonds_firmware_username" to "existing_username",
            // The DS clock is kept on the device's clock. The core's usual way sets it once when a game starts and lets
            // it run on from there, but a game is resumed here from the place it was left, clock and all: the clock
            // would then fall behind by every hour the device was off, and Pokemon's days would stop passing.
            "melonds_start_time_mode" to "sync"))
        // Not one of the 8 and 16-bit systems lag reduction is for: a DS state is several megabytes to copy every frame.
        out["melonDS DS/melonDS DS.cfg"] = cfg(old + lagOff + mapOf("video_scale_integer" to "false", "video_smooth" to "false"))
        out["melonDS DS/melonDS DS.slangp"] = dsScreen(p)
        // NES: the core's composite-video filter stays off. The CRT shader is the television here; both at once blur
        // the picture twice and widen it to 602 columns. (It was found switched on in a test device's stored options.)
        // Its palette is set too: Sony's CXA2025AS decoder in its American setting, the colour an American television made
        // of the NES's signal. The core's own default changed (to "royaltea", 2026-08-29), so left alone the colours would
        // depend on when the core was built. Its pixels at their own 8:7 shape, and the 8 lines top and bottom a set hid.
        out["Nestopia/Nestopia.opt"] = cfg(mapOf("nestopia_blargg_ntsc_filter" to "disabled", "nestopia_palette" to "cxa2025as", "nestopia_aspect" to "auto"))
        // SNES pixels at their own 8:7 shape: the core's usual 4:3 drew them 2% too wide.
        out["Snes9x/Snes9x.opt"] = cfg(mapOf("snes9x_aspect" to "ntsc"))
        // TurboGrafx: the colours its composite output decoded to, measured for the core; RGB keeps the chip's own.
        out["Beetle PCE/Beetle PCE.opt"] = cfg(mapOf("pce_palette" to if (look.signalOf("tg16") == "RGB") "RGB" else "Composite"))
        // Atari 2600: Stella's own television filter, made for the console, for its signal; and no blending of games that
        // flicker objects at 30 a second, which flickered on a tube too (its phosphors faded in a few milliseconds).
        out["Stella/Stella.opt"] = cfg(mapOf("stella_filter" to when (look.signalOf("atari2600")) { "RGB" -> "disabled"; "S-video" -> "s-video"; else -> "composite" },
            "stella_phosphor" to "off"))
        // The Game Boy Advance's colour is done by its screen's shader (gbaScreen), not the core, so the core's is off.
        out["mGBA/mGBA.opt"] = cfg(mapOf("mgba_interframe_blending" to "mix", "mgba_color_correction" to "OFF"))
        // Boktai's cartridge had a sunlight sensor, and without any sun the game cannot be played. It starts at half
        // sun; R3 and L3 turn it up and down while playing. (A file named after a game holds that game's core options
        // in place of the core's own, so the core's are repeated in it.)
        for (boktai in RetroPack.solar) out["mGBA/$boktai.opt"] = cfg(mapOf("mgba_interframe_blending" to "mix", "mgba_color_correction" to "OFF", "mgba_solar_sensor_level" to "5"))
        return out
    }

    /**
     * Arcade sets whose boards run well away from 60 pictures a second (Midway's at 54.7, Irem's M72 at 55, Rampage
     * at 30), in every known variant. Motion clarity is timed for 60, so they go without it.
     */
    val offPaceSets: Set<String> = setOf("mk", "mk2", "umk3", "nbajam", "nbajamte", "rtype", "rtype2", "rampage")

    /** Sets the owned keys in an existing config text and leaves every other line exactly as it was. */
    fun merge(existing: String, owned: Map<String, String>): String {
        // A file with a zero byte in it is no longer a settings file (a write cut short by a flat battery can leave
        // one). RetroArch stops reading at that byte, so everything after it, these settings included, would count
        // for nothing (tried: a game then ran with RetroArch's own defaults). Such a file is started afresh.
        val text = if (existing.contains('\u0000')) "" else existing
        val left = LinkedHashMap(owned)
        val written = HashSet<String>()
        val lines = text.lines().filter { it.isNotBlank() }.mapNotNull { line ->
            val key = line.substringBefore('=').trim()
            when {
                !line.contains('=') -> line
                key in left -> { written += key; "$key = \"${left.remove(key)}\"" }
                key in written -> null          // a second line for a setting already written: only one value may stand
                else -> line
            }
        }
        return (lines + left.map { (k, v) -> "$k = \"$v\"" }).joinToString("\n") + "\n"
    }
}

// ---------------------------------------------------------------------------------------------
// Shelves: the player's own, and a few the library fills by itself.
// ---------------------------------------------------------------------------------------------

object Shelves {
    const val YOURS = "Yours"
    const val LIBRARY = "Library"
    val groups = listOf(YOURS, LIBRARY)
}

// ---------------------------------------------------------------------------------------------
// First run: the few things only a person holding the device can do, in the order to do them.
// Plain values in, plain lines out, so the order and the wording can be checked without a device.
// ---------------------------------------------------------------------------------------------

/** One standalone emulator as the first-run list sees it. [fetchable]: Timeline can fetch it from its project's own site. */
data class SetupEmu(
    val id: String, val label: String, val systems: List<String>,
    val installed: Boolean, val fetchable: Boolean, val opened: Boolean, val inside: String,
    /** Wanted only for something extra (melonDS: the DS on two screens), so the list is all set without it. */
    val optional: Boolean = false,
)

data class SetupFacts(
    val home: Boolean,
    val gamesFolder: String = "",     // where the games folder is; empty when there is none yet
    val games: Int = 0,
    val consoles: Int = 0,
    val canInstall: Boolean,          // Android lets this app hand it installers
    val retroUsed: Boolean,           // at least one console in the library opens in RetroArch
    val retroInstalled: Boolean,
    val retroStorage: Boolean,        // RetroArch may read the games
    val cores: List<String> = emptyList(),   // the RetroArch cores the library needs, by name
    val coresInside: Int = 0,         // how many of those came inside the RetroArch install (a build with cores bundled)
    val coresVisited: Boolean = false,  // the player has been sent to RetroArch's core downloader from this list
    val shaders: Boolean,
    val shadersBusy: Boolean,
    val shadersProgress: Int = -1,    // percent fetched, while fetching
    val files: List<ConsoleFiles.Finding> = emptyList(),
    val others: List<SetupEmu>,
    val installing: String = "",      // what is being fetched or handed to Android right now, with progress
    val dolphin: Boolean? = null,     // the launcher has been let into Dolphin's settings; null when Dolphin is not in use
    val buttonsTaught: Boolean = false,
    val art: Boolean = false,         // cover art has been fetched, or asked for
)

data class SetupStep(val id: String, val label: String, val value: String, val note: String, val done: Boolean, val optional: Boolean = false)

object FirstRun {
    fun listed(names: List<String>): String = when (names.size) {
        0 -> ""
        1 -> names[0]
        else -> names.dropLast(1).joinToString(", ") + " and " + names.last()
    }

    /** Console names for a list of system ids, in timeline order. */
    private fun consoles(ids: Collection<String>): List<String> = Catalog.systems.filter { it.id in ids }.map { it.name }

    /** The cores to pick in RetroArch's downloader, as many as fit in a note of [room] characters. */
    fun coreList(cores: List<String>, room: Int): String {
        val all = listed(cores)
        if (all.length <= room) return all
        var n = cores.size
        while (n > 1) {
            n--
            val text = cores.take(n).joinToString(", ") + " and ${cores.size - n} more (Settings, Emulators names them all)"
            if (text.length <= room) return text
        }
        return "${cores.size} cores (Settings, Emulators names them)"
    }

    fun steps(f: SetupFacts): List<SetupStep> {
        val out = ArrayList<SetupStep>()
        out += SetupStep("home", "Home screen", if (f.home) "Timeline" else "Press A",
            "Press A and pick Timeline. The Home button and every restart then land here. To switch back later: Android's settings, Apps, Default apps.", f.home)

        out += when {
            f.gamesFolder.isEmpty() -> SetupStep("games", "Your games", "Press A to make the folder",
                "Games go in a folder called ROMs, with one folder inside it per console (nes, snes, psx and so on). Press A and Timeline makes them, on the SD card if there is one. Then copy your games in.", false)
            f.games == 0 -> SetupStep("games", "Your games", "No games yet",
                "Copy your games into ${f.gamesFolder}, one folder per console: nes, snes, genesis, psx and so on. Timeline finds them by itself. Press A to make any console folders that are missing.", false)
            else -> SetupStep("games", "Your games", "${f.games} games  ·  ${f.consoles} consoles",
                "Found in ${f.gamesFolder}. Press A to make folders for the consoles you have no games for yet. Timeline looks again whenever the card changes.", true)
        }

        val wanted = (if (f.retroUsed) listOf(Triple("RetroArch", f.retroInstalled, Sources.retroarch.approxMb)) else emptyList()) +
            f.others.filter { !it.optional && it.fetchable }.map { Triple(it.label, it.installed, Sources.of(it.id)?.approxMb ?: 0) }
        val missing = wanted.filter { !it.second }
        val have = wanted.count { it.second }
        out += SetupStep("install", "Emulators",
            when {
                f.installing.isNotEmpty() -> f.installing
                missing.isEmpty() -> if (have == 0) "None needed yet" else "$have installed"
                !f.canInstall -> "Press A to allow it"
                else -> "Press A to install ${missing.size}"
            },
            when {
                wanted.isEmpty() -> "Once there are games, the emulators they need are listed here."
                missing.isEmpty() -> "Every emulator your games need is installed."
                !f.canInstall -> "Android asks once whether Timeline may install apps. Switch on Allow from this source, come back, and press A again."
                else -> "Downloads ${listed(missing.map { it.first })} from ${if (missing.size == 1) "its" else "their"} developers' own sites (about ${missing.sumOf { it.third }} MB), " +
                    "checks each is signed by them, and hands it to Android, which asks you to confirm."
            }, missing.isEmpty())

        if (f.retroUsed) {
            out += SetupStep("retro", "RetroArch",
                when {
                    !f.retroInstalled -> "Waiting for the install"
                    !f.retroStorage -> "Press A, then OK and Allow"
                    else -> "Ready"
                },
                when {
                    !f.retroInstalled -> "The Emulators step above installs it."
                    !f.retroStorage -> "RetroArch opens and asks to read your storage: choose OK, then Allow, then press Home to come back."
                    else -> "Timeline hands RetroArch its settings, shaders and console files each time a game starts. Nothing to do here."
                }, f.retroInstalled && f.retroStorage)

            val coresDone = f.cores.isEmpty() || f.coresInside >= f.cores.size || f.coresVisited
            val lead = "RetroArch fetches its own cores, one per console. Press A, then in RetroArch: Main Menu, Online Updater, Core Downloader. Pick "
            val tail = ". Then press Home."
            out += SetupStep("cores", "RetroArch's cores",
                when {
                    f.coresInside >= f.cores.size && f.cores.isNotEmpty() -> "All inside"
                    f.coresVisited -> "Done"
                    !f.retroInstalled -> "Waiting for RetroArch"
                    else -> "Press A, then pick ${f.cores.size}"
                },
                if (f.coresVisited && f.coresInside < f.cores.size) "If a game says its core is missing, come back here: the list of cores to pick is the same."
                else lead + coreList(f.cores, 285 - lead.length - tail.length) + tail,
                coresDone)

            out += SetupStep("shaders", "Shader collection",
                when {
                    f.shaders -> "In place"
                    f.shadersBusy -> if (f.shadersProgress >= 0) "Fetching  ·  ${f.shadersProgress}%" else "Fetching"
                    !f.retroInstalled -> "Waiting for RetroArch"
                    else -> "Press A to fetch it"
                },
                "The CRT and handheld screen looks are built on RetroArch's shader collection. Press A to fetch it from libretro (about ${Sources.SHADERS_MB} MB). It unpacks by itself.",
                f.shaders)
        }

        if (f.files.isNotEmpty()) {
            val required = f.files.filter { !it.optional }
            val missing = required.filter { it.state == ConsoleFiles.State.MISSING }
            out += SetupStep("files", "Console files",
                if (missing.isEmpty()) "${f.files.count { it.state != ConsoleFiles.State.MISSING }} in place" else "${missing.size} missing",
                if (missing.isEmpty()) "Every console that needs a file from the console itself has one, checked against known good copies where they are known. Press A to see them."
                else "${listed(consoles(missing.map { it.system }))} need a file from the console itself. Press A for the exact names and where they go. Timeline can't provide them: they come from your own console.",
                missing.isEmpty(), optional = required.isEmpty())
        }

        for (e in f.others) {
            val what = "For ${listed(e.systems)}."
            out += when {
                e.installed -> SetupStep(e.id, e.label, if (e.opened) "Opened" else "Press A to open it", "$what ${e.inside}".trim(), e.opened, optional = e.optional)
                e.fetchable && e.optional -> SetupStep(e.id, e.label, "Press A to install it",
                    "$what Optional. Press A and Timeline fetches it from its developers, checks it, and hands it to Android to install. ${e.inside}".trim(),
                    false, optional = true)
                e.fetchable -> SetupStep(e.id, e.label, "Waiting for the install", "$what ${e.inside}".trim(), false, optional = e.optional)
                else -> SetupStep(e.id, e.label, "Not installed",
                    "$what Timeline doesn't install this one. Install it yourself and these games open in it.", false, optional = true)
            }
        }

        f.dolphin?.let { granted ->
            out += SetupStep("dolphinset", "Dolphin's settings", if (granted) "Written" else "Press A, then Use this folder",
                if (granted) "Timeline keeps Dolphin set up: this device's buttons and sticks mapped, Vulkan, twice the detail, shaders prepared before a game starts, and no touch buttons over the picture."
                else "Android shows Dolphin's own folder. Choose Use this folder, then Allow. Timeline then sets Dolphin up for this device, controls included. Without this step its games do not answer the buttons.",
                granted)
        }

        out += SetupStep("art", "Cover art", if (f.art) "Fetched" else "Optional",
            "Box art and screenshots from libretro's thumbnail collection, and for arcade games their year, maker and players from the Arcade Database. Press A to start; it carries on in the background.",
            f.art, optional = true)

        out += SetupStep("buttons", "Extra buttons", if (f.buttonsTaught) "Taught" else "Optional",
            "Teach Timeline two spare buttons (M1 and M2 on Retroid devices). They become rewind and fast-forward on cartridge consoles, and the RetroArch menu and fast-forward elsewhere.",
            f.buttonsTaught, optional = true)

        val left = left(out)
        out += SetupStep("finish", if (left == 0) "All set" else "Finish later", if (left == 0) "Press A" else "$left to go",
            if (left == 0) "Everything is ready. Press A to go to your games."
            else "Press A to stop this list opening by itself. It stays at the top of Settings until everything is done.",
            left == 0, optional = true)
        return out
    }

    /** How many steps still need the player. */
    fun left(steps: List<SetupStep>): Int = steps.count { !it.done && !it.optional }

    /** The row to rest on: the first step that still needs the player, or the last row when there is none. */
    fun next(steps: List<SetupStep>): Int = steps.indexOfFirst { !it.done && !it.optional }.let { if (it < 0) steps.lastIndex else it }
}

// ---------------------------------------------------------------------------------------------
// On this day: the games that came out on today's date, or this week, years ago.
// ---------------------------------------------------------------------------------------------

/**
 * The Nova's measured display calibration, by pippopapera (github.com/pippopapera/nova-display-calibration, MIT; the
 * notice travels in assets/display). Measured with a colorimeter against sRGB, D65 and gamma 2.2: average error 0.6
 * against 5.8 as shipped, where the panel covers about 145% of sRGB and reds and greens run hot.
 *
 * It lives in Qualcomm's display color file, applied by the display hardware itself: a curve into linear light, a
 * measured 3x3 color matrix, and a curve back out. The display system reads an override of that file at boot, so a
 * change shows after a restart. The launcher carries only the author's patch; the file is rebuilt on the device
 * from the device's own factory copy, and only on the firmware it was measured on, checked by fingerprint before
 * and after (a rebuild matched the author's published fingerprint byte for byte).
 */
object DisplayCalibration {
    const val BUILD = "RPN_V1.0.0.436_20260722_083524_user"
    const val FACTORY = "/vendor/etc/display/qdcm_calib_data_il97680a_amoled_panel_without_DSC.json"
    const val OVERRIDE = "/data/vendor/display/qdcm_calib_data_il97680a_amoled_panel_without_DSC.json"
    const val FACTORY_SHA256 = "26a56de100c2a27ecac2fe7ed4d30ba5bcb935865c7470775dcfda744d0173af"
    const val CALIBRATED_SHA256 = "c6e20079ef58220960e9511d5641bc02f1bcfb0f90c5bb0a842e5b2ed00c9afd"
    const val PATCH_SHA256 = "9a4b0907250cc58d5c2272e72bcbc81e57188b4b5ea259ece2c4144d0beae04e"
    const val PATCH_ASSET = "display/gamma22.npatch"

    fun sha256(b: ByteArray): String = java.security.MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    private fun swapped(b: ByteArray): ByteArray {
        val out = b.copyOf()
        var i = out.size % 2
        while (i < out.size - 1) { val t = out[i]; out[i] = out[i + 1]; out[i + 1] = t; i += 2 }
        return out
    }

    /**
     * The calibrated file from the factory file and the author's patch ("NOVAPCH1": numbered lines, each rebuilt from
     * pieces of the old value and new numbers). Null unless both inputs and the result have the expected fingerprints.
     */
    fun rebuild(factory: ByteArray, patch: ByteArray): ByteArray? = runCatching {
        if (sha256(factory) != FACTORY_SHA256 || sha256(patch) != PATCH_SHA256) return null
        val lines = String(factory, Charsets.US_ASCII).lines().let { if (it.lastOrNull() == "") it.dropLast(1) else it }
            .map { it.removeSuffix("\r") }.toMutableList()
        for (i in lines.indices) {
            val l = lines[i]; val lead = l.length - l.trimStart(' ').length
            lines[i] = " ".repeat(lead / 2) + l.trimStart(' ')
        }
        val buf = java.nio.ByteBuffer.wrap(patch)
        val magic = ByteArray(8).also { buf.get(it) }
        if (String(magic, Charsets.US_ASCII) != "NOVAPCH1") return null
        repeat(buf.int) {
            val index = buf.int; val n = buf.int
            val line = lines[index]
            val head = line.substringBefore(": \""); val rest = line.substringAfter(": \"")
            val hex = rest.substringBeforeLast('"'); val tail = rest.substringAfterLast('"')
            val old = swapped(ByteArray(hex.length / 2) { k -> hex.substring(2 * k, 2 * k + 2).toInt(16).toByte() })
            val out = java.io.ByteArrayOutputStream()
            repeat(n) {
                when (buf.get().toInt()) {
                    0 -> { val start = buf.int; val len = buf.int; out.write(old, start, len) }
                    1 -> { val len = buf.int; val lit = ByteArray(len).also { b -> buf.get(b) }; out.write(lit) }
                    else -> return null
                }
            }
            lines[index] = head + ": \"" + swapped(out.toByteArray()).joinToString("") { "%02X".format(it) } + "\"" + tail
        }
        if (buf.hasRemaining()) return null
        val result = (lines.joinToString("\r\n") + "\r\n").toByteArray(Charsets.US_ASCII)
        result.takeIf { sha256(it) == CALIBRATED_SHA256 }
    }.getOrNull()
    // ---- the only commands this sends to the maker's service, in these shapes only ----

    private val safePath = Regex("/data/(user/0|data)/market\\.nostos\\.timeline/[A-Za-z0-9_./-]+")

    /**
     * Puts a rebuilt file in place as the display system expects it (owner system:graphics, readable by it alone), as
     * single commands: the maker's service runs only the first part of a chained command (seen on the Nova).
     */
    fun installCommands(source: String): List<String>? = if (!safePath.matches(source) || ".." in source) null else {
        val tmp = "$OVERRIDE.timeline-new"
        listOf("cp $source $tmp", "chown 1000:1003 $tmp", "chmod 600 $tmp", "restorecon $tmp", "mv $tmp $OVERRIDE", "restorecon $OVERRIDE")
    }
    const val REMOVE = "rm -f $OVERRIDE"
    const val REMOVE_TMP = "rm -f $OVERRIDE.timeline-new"
    /** Owner, group and mode of the file in place: the display system (1000:1003) must be able to read it. */
    const val READ_OWNER = "stat -c %u:%g:%a $OVERRIDE"
    const val OWNER_OK = "1000:1003:600"
    const val READ_OVERRIDE = "sha256sum $OVERRIDE"
    /** The fingerprint in the maker's service's answer, which may carry more than the command printed. */
    fun fingerprintIn(answer: String?): String = answer?.let { Regex("\\b[0-9a-f]{64}\\b").find(it)?.value }.orEmpty()
    /** A property's value such as "1.1" in the same kind of answer. */
    fun decimalIn(answer: String?): String = answer?.let { Regex("\\b[0-9]\\.[0-9]{1,3}\\b").find(it)?.value }.orEmpty()
    const val READ_SATURATION = "getprop persist.sys.sf.color_saturation"

    /**
     * Retroid's own tint: a picked color whose red, green and blue scale the whole screen after everything else ("-1"
     * is none). This firmware ships it at a pale blue (0xBCE1FF): red to 93%, green to 97%, white near 6800 K instead
     * of D65. The calibration was measured on an untinted screen (its own installer refuses a tinted one), so
     * Calibrated turns the tint off and Stock puts back what was there.
     */
    const val TINT_OFF = -1
    const val FACTORY_TINT = -4398593
    const val READ_TINT = "settings get secure customer_display_color_temperature"
    fun tintIn(answer: String?): Int? =
        answer?.let { Regex("(?m)^\\s*(-?\\d+)\\s*$").findAll(it).lastOrNull()?.groupValues?.get(1)?.toIntOrNull() }
    /** The setting is read when the device starts; the call (IColorDisplayManager 17) applies it now. */
    fun tintCommands(tint: Int): List<String> = listOf("settings put secure customer_display_color_temperature $tint",
        "service call color_display 17 i32 $tint")
    const val RESTART = "svc power reboot"

    /**
     * The color mode around it. The calibration is measured with Android's own saturation at exactly "1.0" (Android 13
     * reads the property as text at start: anything else can bring back the 10% "Boosted" saturation the Nova ships
     * with) and the Natural color mode, with Retroid's tint off ([tintCommands]). Going back restores what was there
     * before, in the same shapes.
     */
    fun colorCommands(mode: Int, saturation: String): List<String>? {
        if (mode !in 0..3 || saturation !in setOf("1.0", "1.1")) return null
        return listOf("settings put system display_color_mode $mode", "setprop persist.sys.sf.color_saturation $saturation",
            "service call SurfaceFlinger 1022 f $saturation")
    }
}

/**
 * A DS game's two saves (RetroArch's and melonDS's): whether the one the other emulator has should be brought to the
 * one about to run. A mark is a save's length and checksum ("" for none); [last] is the mark both had the last time
 * they were the same ("" if never seen so).
 */
object DsSaves {
    fun bothMoved(from: String, to: String, last: String) = last.isNotEmpty() && to.isNotEmpty() && from != last && to != last

    fun bring(from: String, to: String, last: String, fromTime: Long, toTime: Long): Boolean = when {
        from.isEmpty() || from == to -> false        // nothing to bring, or nothing different
        to.isEmpty() -> true                         // nothing here yet
        last.isNotEmpty() && to == last -> true      // only the other has played on since they were the same
        last.isNotEmpty() && from == last -> false   // only this one has: it is the newer
        else -> fromTime > toTime + 2500             // both have, or never compared: the clock decides
    }
}

/**
 * How long the battery has left at the pace it has actually been going down: Android does not share its own estimate
 * with apps. Paced by awake time, so a night asleep does not make it look endless, and only once the battery has
 * been off the charger long enough to say anything. The percentage itself is Android's.
 */
/**
 * Battery care on the charger. Retroid's own 80% limit is switched on while the Nova rests on the charger, and off as
 * soon as it is in use or unplugged. Read on the Nova: the limit only stops charging above 80% (the
 * battery is never run down), and left alone at 100% the charger keeps topping the battery up at its highest
 * voltage, 4.5 V, for as long as it sits there, which is what wears a battery out. So a Nova put down on the charger
 * stops at 80%, or stops topping up if it is already above, and finishes to 100% once it is picked up.
 */
object ChargeHold {
    const val LEVEL = 80
    /** How long without a button or a game before the Nova counts as resting on the charger. */
    const val REST_MS = 30 * 60_000L
    val modes = listOf("Always to 100%", "Smart")

    /** [inUse]: a game, or another app on screen. [idleMs]: real time since the last use. */
    fun wanted(mode: String, plugged: Boolean, inUse: Boolean, idleMs: Long): Boolean =
        mode == "Smart" && plugged && !inUse && idleMs >= REST_MS
}

object BatteryEstimate {
    data class Sample(val boot: Long, val awakeMs: Long, val level: Int)

    const val WINDOW = 90 * 60_000L                 // the pace of the last hour and a half awake
    const val MIN_AWAKE = 12 * 60_000L
    const val MIN_DROP = 2

    /** The samples worth keeping once [now] is read: none on the charger; only this boot and only while going down. */
    fun add(samples: List<Sample>, now: Sample, charging: Boolean): List<Sample> {
        if (charging) return emptyList()
        // A level higher than now means it was charged in between, out of sight: those say nothing about the pace now.
        val same = samples.filter { it.boot == now.boot && it.awakeMs <= now.awakeMs && it.level >= now.level }
            .takeIf { s -> s.isEmpty() || now.level <= s.last().level } ?: emptyList()
        val all = same + now
        val inside = all.filter { now.awakeMs - it.awakeMs <= WINDOW }
        val anchor = all.lastOrNull { now.awakeMs - it.awakeMs > WINDOW }
        // One sample for each level is enough: the first time each was seen.
        return (listOfNotNull(anchor) + inside).distinctBy { it.level }
    }

    /** Hours left at that pace, or null while there is too little to go on. */
    fun hoursLeft(samples: List<Sample>): Float? {
        val first = samples.firstOrNull() ?: return null
        val last = samples.last()
        val awake = last.awakeMs - first.awakeMs
        val drop = first.level - last.level
        if (awake < MIN_AWAKE || drop < MIN_DROP) return null
        return last.level / (drop / (awake / 3_600_000f))
    }

    /** "88%", "88% / 6.5 h", "88% / 40 min", "88% / charging", and "82% / resting" on the charger while [ChargeHold] holds it. */
    fun text(level: Int, charging: Boolean, hours: Float?, held: Boolean = false): String = when {
        level < 0 -> ""
        charging && held && level >= ChargeHold.LEVEL -> "$level% / resting"
        charging -> "$level% / charging"
        hours == null -> "$level%"
        hours < 1f -> "$level% / ${(hours * 60).toInt().coerceAtLeast(1)} min"
        hours < 10f -> "$level% / ${"%.1f".format(Locale.US, hours)} h"
        else -> "$level% / ${hours.toInt()} h"
    }

    fun encode(samples: List<Sample>): String = samples.joinToString(";") { "${it.boot},${it.awakeMs},${it.level}" }
    fun decode(text: String): List<Sample> = text.split(';').mapNotNull { part ->
        val f = part.split(',')
        if (f.size != 3) null else runCatching { Sample(f[0].toLong(), f[1].toLong(), f[2].toInt()) }.getOrNull()
    }
}

object History {
    private val lengths = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)

    /** Day of a plain 365-day year, so the 29th of February sits beside the 28th. */
    private fun dayOfYear(month: Int, day: Int): Int = lengths.take(month - 1).sum() + day.coerceAtMost(lengths[month - 1])

    /**
     * Month and day of a release date "YYYYMMDD" when the day is really known. The dates on the card write a known
     * month with an unknown day as the 1st, and a known year alone as the 1st of January, so neither counts.
     */
    fun known(date: String): Pair<Int, Int>? {
        if (date.length < 8 || !date.take(8).all(Char::isDigit)) return null
        val m = date.substring(4, 6).toInt()
        val d = date.substring(6, 8).toInt()
        return if (m in 1..12 && d in 2..31) m to d else null
    }

    /** How many days [date] falls from today's month and day, either way round the year; null if its day is not known. */
    fun distance(date: String, month: Int, day: Int): Int? {
        val (m, d) = known(date) ?: return null
        val diff = kotlin.math.abs(dayOfYear(m, d) - dayOfYear(month, day))
        return minOf(diff, 365 - diff)
    }

    /** The games released within [days] of today's month and day, oldest first. */
    fun week(dated: List<Pair<Game, String>>, month: Int, day: Int, days: Int = 3): List<Pair<Game, String>> =
        dated.filter { (_, date) -> distance(date, month, day)?.let { it <= days } == true }
            .sortedWith(compareBy({ it.second.take(8) }, { it.first.sort }))

    /** The one to name on the home page: released on this very day if any was (the oldest), otherwise none. */
    fun today(week: List<Pair<Game, String>>, month: Int, day: Int): Pair<Game, String>? =
        week.firstOrNull { (_, date) -> known(date) == (month to day) }     // the very day: "30 years ago today" is said of it

    private val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    /** "Aug 22, 1995, 31 years ago" for a shelf's line. */
    fun line(date: String, year: Int): String {
        val (m, d) = known(date) ?: return ""
        val y = date.take(4).toInt()
        val ago = year - y
        return "${months[m - 1]} $d, $y" + when { ago <= 0 -> ""; ago == 1 -> ", a year ago"; else -> ", $ago years ago" }
    }
}

// ---------------------------------------------------------------------------------------------
// Marks the player puts on games: finished, and hidden. A file each on the card, a game per line.
// ---------------------------------------------------------------------------------------------

object Marks {
    const val FINISHED = "finished.txt"
    const val HIDDEN = "hidden.txt"

    fun parse(text: String): List<String> = text.lines().map { it.trim() }.filter { it.contains('/') }.distinct()

    fun format(keys: List<String>): String = keys.joinToString("") { it + "\n" }

    /** Visible games: the library less what is hidden, with a system left out once nothing in it is showing. */
    fun visible(lib: Map<String, List<Game>>, hidden: Set<String>): Map<String, List<Game>> =
        if (hidden.isEmpty()) lib
        else lib.mapValues { (_, games) -> games.filter { it.key !in hidden } }.filterValues { it.isNotEmpty() }
}

// ---------------------------------------------------------------------------------------------
// A line or two on a game's page about the buttons that are not obvious, in the emulator it opens in.
// From the launcher's own Dolphin layouts and libretro's documented defaults for each core.
// ---------------------------------------------------------------------------------------------

/**
 * The few lines of a description the game panel has room for, ending where they read well: on the last whole sentence
 * when that fills most of the room, otherwise on a whole word with an ellipsis, never part way through a word.
 */
object Excerpt {
    private val abbreviations = setOf("dr", "mr", "mrs", "ms", "st", "vs", "jr", "sr", "no", "vol", "mt", "ft", "lt", "sgt", "capt",
        "prof", "inc", "ltd", "co", "etc", "bros")
    // Not a word to leave hanging before the ellipsis ("leaves the...").
    private val weak = setOf("a", "an", "the", "and", "or", "but", "nor", "to", "of", "in", "on", "at", "by", "for", "with", "from",
        "into", "onto", "as", "its", "his", "her", "their", "your", "our", "that", "which", "who", "whose", "than")

    /** Where sentences end, up to [upTo]: not after an initial ("Wile E. Coyote"), "J.J." or an abbreviation ("Dr. Meat"). */
    fun sentenceEnds(text: String, upTo: Int): List<Int> {
        val out = ArrayList<Int>()
        for (i in text.indices) {
            val c = text[i]
            if (c != '.' && c != '?' && c != '!') continue
            var end = i + 1
            while (end < text.length && text[end] in "\"”’')") end++       // a closing quote or bracket ends with its sentence
            if (end > upTo) break
            if (end < text.length && !text[end].isWhitespace()) continue   // "J.J", "1.5"
            var j = end
            while (j < text.length && text[j].isWhitespace()) j++
            val next = if (j < text.length) text[j] else 'A'
            if (!(next.isUpperCase() || next.isDigit() || next in "\"“‘'(")) continue
            if (c == '.') {
                var s = i
                while (s > 0 && (text[s - 1].isLetterOrDigit() || text[s - 1] in ".'’-")) s--
                val word = text.substring(s, i)
                // "Dragon Quest V." ends a sentence; "Wile E." and "Leon S." do not
                if (!(word.length == 1 && word[0] in "IVX") && (word.length <= 1 || '.' in word || word.lowercase() in abbreviations)) continue
            }
            out += end
        }
        return out
    }

    /** [text] cut for a space that shows its first [visible] characters; [atLeast] is how much of that a sentence must fill. */
    fun of(text: String, visible: Int, atLeast: Float = 0.55f): String {
        if (visible >= text.length) return text
        sentenceEnds(text, visible).lastOrNull()?.takeIf { it >= atLeast * visible }?.let { return text.substring(0, it) }
        val cut = text.lastIndexOfAny(charArrayOf(' ', '\n'), (visible - 3).coerceAtLeast(0))
        if (cut <= 0) return text                 // nothing sensible to cut at: the text's own ellipsis does it
        fun trim(t: String) = t.trimEnd(',', ';', ':', '–', '-', ' ', '\n')
        var head = trim(text.substring(0, cut))
        // Not inside a bracket or a quotation left open: back to just before it opened.
        val open = maxOf(head.lastIndexOf('('), head.lastIndexOf('“'))
        if (open > 0 && open > maxOf(head.lastIndexOf(')'), head.lastIndexOf('”'))) head = trim(head.substring(0, open))
        while (true) {
            val sp = head.lastIndexOf(' ')
            if (sp <= 0 || head.substring(sp + 1).lowercase() !in weak) break
            head = trim(head.substring(0, sp))
        }
        return head.trimEnd('.') + "…"
    }
}

object ControlsNote {
    private val cores = mapOf(
        "ra:stella" to "SELECT is the console's Game Select switch and START its Reset. L1 and R1 set the difficulty switches to A, L2 and R2 to B.",
        "ra:fbneo" to "SELECT puts in a coin and START starts.",
        "ra:mupen64plus_next_gles3" to "A and B are the console's A and B. The right stick is the C buttons and L2 is Z. Hold R2 to use the face buttons as C buttons.",
        "ra:melondsds" to "R2 changes how the two screens sit. Touch the screen, or steer a pointer with the right stick and press it in. L2 is the microphone.",
   )
    private const val CUBE = "The right stick is the C stick, R1 is Z, and L2 and R2 are the triggers."
    private const val REMOTE = "Left stick: the Nunchuk. Right stick: pointing. R1 shakes the remote and L3 the Nunchuk; L1 is C, L2 is Z, X and Y are 1 and 2."
    private val wiiOwn = mapOf(
        "super smash bros. brawl" to "Played on the GameCube layout: the right stick is the C stick and R1 is Z.",
        "mario kart wii" to "Played on the GameCube layout: the right stick is the C stick and R1 is Z.",
        "punch-out!!" to "The remote is held sideways: the d-pad moves, X and Y are 1 and 2.",
   )
    private val doing = mapOf(
        "RetroArch menu" to "opens RetroArch's menu", "Fast-forward (hold)" to "fast-forwards while held",
        "Fast-forward (switch)" to "switches fast-forward on and off", "Rewind (hold)" to "rewinds while held",
        "Save state" to "saves a state", "Load state" to "loads the state", "Screenshot" to "takes a picture",
   )

    /**
     * [emulator] is the id the game opens in; [dolphinLayout] whether the launcher sets Dolphin's controls; [extras]
     * what M1 and M2 do on this system when they have been taught, or null.
     */
    fun of(system: String, base: String, emulator: String, dolphinLayout: Boolean, extras: Pair<String, String>?): String {
        val parts = ArrayList<String>()
        when {
            emulator.startsWith("ra:") -> {
                cores[emulator]?.let(parts::add)
                if (extras != null) {
                    val m = listOfNotNull(doing[extras.first]?.let { "M1 $it" }, doing[extras.second]?.let { "M2 $it" })
                    if (m.isNotEmpty()) parts += m.joinToString(", ").replaceFirstChar { it.uppercase() } + "."
                }
                parts += "START and SELECT together leave and keep your place."
            }
            emulator == "dolphin" -> {
                if (dolphinLayout) parts += if (system == "wii") wiiOwn[base] ?: REMOTE else CUBE
                parts += "To leave: Back, then Exit Emulation."
            }
            emulator.isNotEmpty() -> parts += "To leave, use the emulator's own menu."
        }
        return parts.joinToString(" ")
    }
}
