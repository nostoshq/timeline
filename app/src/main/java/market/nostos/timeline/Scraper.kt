// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 NOSTOS, LLC

package market.nostos.timeline

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Stops a whole run: a refused login, a spent daily allowance, a missing key. */

/** What one lookup produced: facts, and pictures to fetch as (media folder, address). */
data class Found(val meta: Meta? = null, val media: List<Pair<String, String>> = emptyList()) {
    val empty: Boolean get() = meta == null && media.isEmpty()
}

data class ScrapeOptions(
    val source: String = AUTO,
    val image: String = "Box 2D",
    val logo: String = "Marquee",
    val ratings: Boolean = true,
    val videos: Boolean = false,
) {
    companion object {
        const val AUTO = "Libretro + Arcade DB"
        val images = listOf("Box 2D", "Box 3D", "Mix", "Screenshot", "Title screen")
        val logos = listOf("Marquee", "None")

        /** The media folder each image choice is stored in, in the layout ES-DE and RetroBat share. */
        fun folder(image: String): String = when (image) {
            "Box 3D" -> "3dboxes"
            "Mix" -> "miximages"
            "Screenshot" -> "screenshots"
            "Title screen" -> "titlescreens"
            else -> "covers"
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Libretro thumbnails: box art, screenshots and title screens by exact game name. No account.
// ---------------------------------------------------------------------------------------------

object Thumbnails {
    const val HOST = "https://thumbnails.libretro.com"

    val folders: Map<String, String> = mapOf(
        "atari2600" to "Atari - 2600", "nes" to "Nintendo - Nintendo Entertainment System",
        "mastersystem" to "Sega - Master System - Mark III", "tg16" to "NEC - PC Engine - TurboGrafx 16",
        "genesis" to "Sega - Mega Drive - Genesis", "gb" to "Nintendo - Game Boy",
        "tg-cd" to "NEC - PC Engine CD - TurboGrafx-CD", "scummvm" to "ScummVM",
        "snes" to "Nintendo - Super Nintendo Entertainment System", "gamegear" to "Sega - Game Gear",
        "segacd" to "Sega - Mega-CD - Sega CD", "doom" to "DOOM", "psx" to "Sony - PlayStation",
        "saturn" to "Sega - Saturn", "n64" to "Nintendo - Nintendo 64", "quake" to "Quake",
        "gbc" to "Nintendo - Game Boy Color", "dreamcast" to "Sega - Dreamcast", "ps2" to "Sony - PlayStation 2",
        "gba" to "Nintendo - Game Boy Advance", "gc" to "Nintendo - GameCube", "xbox" to "Microsoft - Xbox",
        "nds" to "Nintendo - Nintendo DS", "psp" to "Sony - PlayStation Portable", "wii" to "Nintendo - Wii",
        "n3ds" to "Nintendo - Nintendo 3DS", "psvita" to "Sony - PlayStation Vita", "wiiu" to "Nintendo - Wii U",
   )

    private val kinds = mapOf("covers" to "Named_Boxarts", "screenshots" to "Named_Snaps", "titlescreens" to "Named_Titles")

    /** The server swaps these characters for an underscore in file names. */
    fun clean(name: String): String = buildString { for (c in name) append(if (c in "&*/:`<>?\\|\"") '_' else c) }

    /**
     * Names to try, most exact first. Translations and hacks fall back to the game they were made
     * from; a multi-disc folder tries its first disc; newer region tags fall back to the older form.
     */
    fun candidates(stem: String): List<String> {
        val out = LinkedHashSet<String>()
        val plain = stem.replace(Regex("\\s*\\[[^\\]]*\\]"), "").trim()
        val older = plain.replace("(USA, Canada)", "(USA)")
        val noRev = older.replace(Regex("\\s*\\((Rev [0-9A-Z]+|v[0-9][0-9.]*)\\)"), "").trim()
        for (n in listOf(stem, plain, older, noRev)) {
            if (n.isEmpty()) continue
            out += n
            if (!n.contains("(Disc ")) out += "$n (Disc 1)"
        }
        return out.toList()
    }

    fun url(system: String, kind: String, name: String): String? {
        val folder = folders[system] ?: return null
        val dir = kinds[kind] ?: return null
        return "$HOST/${Launch.encode(folder)}/$dir/${Launch.encode(clean(name))}.png"
    }

    /** Folders this source can fill for the chosen image type: the chosen one first, then a screenshot. */
    fun wanted(o: ScrapeOptions): List<String> {
        val first = ScrapeOptions.folder(o.image).takeIf { it in kinds } ?: "covers"
        return listOf(first, "screenshots").distinct()
    }
}

// ---------------------------------------------------------------------------------------------
// Arcade Database (adb.arcadeitalia.net): facts and pictures for arcade sets. No account.
// ---------------------------------------------------------------------------------------------

object ArcadeDb {
    fun url(set: String): String =
        "https://adb.arcadeitalia.net/service_scraper.php?ajax=query_mame&game_name=${Launch.encode(set)}&use_parent=1&lang=en"

    fun parse(body: String, o: ScrapeOptions): Found? {
        val r = runCatching { JSONObject(body).optJSONArray("result")?.optJSONObject(0) }.getOrNull() ?: return null
        val title = r.optString("short_title").ifBlank { r.optString("title") }
        if (title.isBlank()) return null
        val year = r.optString("year").filter(Char::isDigit).take(4)
        val rate = r.optString("rate").toFloatOrNull()
        val meta = Meta(
            name = title, date = if (year.length == 4) year + "0101T000000" else "",
            developer = r.optString("manufacturer"), publisher = r.optString("manufacturer"),
            genre = r.optString("genre"), players = r.optString("players"),
            rating = if (o.ratings && rate != null && rate > 0f) rate / 100f else -1f,
       )
        val media = ArrayList<Pair<String, String>>()
        fun add(folder: String, key: String) = r.optString(key).takeIf { it.startsWith("http") }?.let { media += folder to it }
        add("covers", "url_image_flyer")
        add("screenshots", "url_image_ingame")
        add("titlescreens", "url_image_title")
        if (o.logo != "None") add("marquees", "url_image_marquee")
        if (o.videos) add("videos", "url_video_shortplay_hd")
        return Found(meta, media)
    }
}

// ---------------------------------------------------------------------------------------------

object Http {
    private const val AGENT = "Timeline/1.0 (Android launcher; ${Links.SOURCE})"

    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15000
        readTimeout = 30000
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", AGENT)
    }

    fun get(url: String): Pair<Int, String> {
        val c = open(url)
        return try {
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            code to (stream?.use { it.readBytes().toString(Charsets.UTF_8) } ?: "")
        } finally {
            c.disconnect()
        }
    }

    private fun extension(url: String, type: String?): String {
        val fromUrl = url.substringBefore('?').substringAfterLast('.', "").lowercase()
        if (fromUrl in setOf("png", "jpg", "jpeg", "webp", "mp4", "pdf")) return fromUrl
        return when {
            type == null -> "png"
            "jpeg" in type || "jpg" in type -> "jpg"
            "webp" in type -> "webp"
            "mp4" in type || "video" in type -> "mp4"
            "pdf" in type -> "pdf"
            else -> "png"
        }
    }

    /** Fetches a file to <dir>/<stem>.<ext>. Returns it, or null if the server had nothing. */
    fun download(url: String, dir: File, stem: String): File? {
        val c = open(url)
        try {
            if (c.responseCode != 200) return null
            val type = c.contentType
            if (type != null && type.startsWith("text/")) return null
            dir.mkdirs()
            val target = File(dir, "$stem.${extension(url, type)}")
            val part = File(dir, "$stem.part")
            try {
                c.inputStream.use { input -> part.outputStream().use { input.copyTo(it) } }
                if (part.length() < 64) return null
                if (target.exists()) target.delete()
                return if (part.renameTo(target)) target else null
            } finally {
                if (part.exists()) part.delete()          // a short or failed download leaves nothing behind
            }
        } catch (e: Exception) {
            return null
        } finally {
            c.disconnect()
        }
    }
}

object Scraper {
    /** Looks one game up. Null means "nothing found". */
    fun lookup(g: Game, o: ScrapeOptions): Found? {
        if (g.system == "arcade") {
            val (code, body) = Http.get(ArcadeDb.url(g.stem.lowercase()))
            return if (code == 200) ArcadeDb.parse(body, o) else null
        }
        // Thumbnails are fetched by name, so "found" is decided when the pictures are downloaded.
        val names = Thumbnails.candidates(g.stem)
        val media = Thumbnails.wanted(o).flatMap { folder -> names.mapNotNull { n -> Thumbnails.url(g.system, folder, n)?.let { folder to it } } }
        return if (media.isEmpty()) null else Found(null, media)
    }

    /** Downloads the pictures for one game. Several addresses may be offered per folder; the first that exists wins. */
    fun fetch(g: Game, found: Found, mediaRoot: File): Int {
        var got = 0
        val done = HashSet<String>()
        for ((folder, url) in found.media) {
            if (folder in done) continue
            if (Http.download(url, File(mediaRoot, "${g.system}/$folder"), g.stem) != null) {
                done += folder
                got++
            }
        }
        return got
    }
}

/** Reading and writing gamelist.xml in the form ES-DE and RetroBat use. */
object GameList {
    private fun esc(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    fun write(file: File, games: Map<String, Meta>) {
        val sb = StringBuilder("<?xml version=\"1.0\"?>\n<gameList>\n")
        for ((path, m) in games.toSortedMap(String.CASE_INSENSITIVE_ORDER)) {
            sb.append("  <game>\n    <path>./").append(esc(path)).append("</path>\n")
            fun tag(name: String, value: String) { if (value.isNotBlank()) sb.append("    <").append(name).append('>').append(esc(value)).append("</").append(name).append(">\n") }
            tag("name", m.name)
            tag("desc", m.desc)
            if (m.rating >= 0f) tag("rating", ((m.rating * 100).toInt() / 100f).toString())
            tag("releasedate", m.date)
            tag("developer", m.developer)
            tag("publisher", m.publisher)
            tag("genre", m.genre)
            tag("players", m.players)
            sb.append("  </game>\n")
        }
        sb.append("</gameList>\n")
        file.parentFile?.mkdirs()
        val part = File(file.path + ".part")
        part.writeText(sb.toString())
        if (file.exists()) file.delete()
        part.renameTo(file)
    }

    /** New facts fill gaps and replace what they improve; they never blank out something already known. */
    fun merge(old: Meta?, new: Meta): Meta {
        if (old == null) return new
        fun s(a: String, b: String) = b.ifBlank { a }
        return Meta(
            name = s(old.name, new.name), desc = s(old.desc, new.desc),
            rating = if (new.rating >= 0f) new.rating else old.rating, date = s(old.date, new.date),
            developer = s(old.developer, new.developer), publisher = s(old.publisher, new.publisher),
            genre = s(old.genre, new.genre), players = s(old.players, new.players),
       )
    }
}

/**
 * Finding games by typing, on a keyboard of letters picked one at a time: a title answers to its words with or
 * without the spaces and punctuation ("megaman", "spiderman", "kirbys"), to numbers for its Roman numerals ("final
 * fantasy 6", "street fighter 2"), and to its initials ("ff6", "smb3", "gta", "re4").
 */
object Search {
    private val romans = mapOf("ii" to "2", "iii" to "3", "iv" to "4", "v" to "5", "vi" to "6", "vii" to "7", "viii" to "8",
        "ix" to "9", "x" to "10", "xi" to "11", "xii" to "12", "xiii" to "13")
    private val nonWord = Regex("[^\\p{L}\\p{N}]+")

    private class Keys(val words: List<String>, val joined: String, val numbered: String, val initials: List<String>)

    // Worked out once per title: the search runs again at every letter.
    private val keys = HashMap<String, Keys>()

    private fun plain(text: String) = Titles.fold(text).replace("'", "").replace("’", "")       // "Hawk's" is one word

    private fun keysOf(title: String): Keys = keys.getOrPut(title) {
        val words = plain(title).split(nonWord).filter { it.isNotEmpty() }
        val numbered = words.map { romans[it] ?: it }
        fun initials(ws: List<String>) = ws.joinToString("") { w -> if (w.all(Char::isDigit)) w else w.take(1) }
        // Initials both ways: "ff6" from VI as a number, "ffx" and "mmx" from X as written.
        Keys(words + numbered, words.joinToString(""), numbered.joinToString(""), listOf(initials(numbered), initials(words)).distinct())
    }

    fun find(query: String, games: List<Game>, limit: Int = 60): List<Game> {
        val q = plain(query.trim())
        val words = q.split(nonWord).filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        val typed = words.joinToString("")
        fun rank(g: Game): Int {
            val k = keysOf(g.title)
            // Each word typed is found inside a word of the title, or, from four letters, across words ("megaman").
            val found = words.all { w -> k.words.any { w in it } || (w.length >= 4 && (w in k.joined || w in k.numbered)) }
            val one = words.size == 1 && typed.length >= 2
            return when {
                found && (k.joined == typed || k.numbered == typed) -> 0
                found && (k.joined.startsWith(typed) || k.numbered.startsWith(typed) || g.sort.startsWith(q)) -> 1
                found && k.words.any { it.startsWith(words[0]) } -> 2
                // Initials before letters that merely sit inside a word: "ff" is Final Fantasy before Ken Griffey.
                one && k.initials.any { it.startsWith(typed) } -> 3
                one && typed.length >= 3 && k.initials.any { typed in it } -> 4
                found -> 5
                else -> -1
            }
        }
        return games.mapNotNull { g -> rank(g).takeIf { it >= 0 }?.let { it to g } }
            .sortedWith(compareBy({ it.first }, { it.second.sort }))
            .take(limit).map { it.second }
    }
}
