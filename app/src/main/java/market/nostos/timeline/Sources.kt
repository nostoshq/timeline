// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 NOSTOS, LLC

package market.nostos.timeline

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

// ---------------------------------------------------------------------------------------------
// Where the emulators come from. Timeline never carries anyone else's software: the setup fetches
// each emulator from its own project's servers, checks that the download is signed with that
// project's key, and hands it to Android, which asks before installing anything.
// ---------------------------------------------------------------------------------------------

/**
 * One emulator's official source. [certs] are the SHA-256 digests of the certificates its releases are signed with,
 * read from official release builds; anything signed otherwise is refused. [page] is the project's own download page,
 * for anyone who would rather do it by hand, and for when a download cannot be found.
 */
data class Source(
    val id: String,
    val label: String,
    val pkg: String,
    val page: String,
    val certs: Set<String>,
    val approxMb: Int,
    val license: String,
)

object Sources {
    val retroarch = Source("retroarch", "RetroArch", "com.retroarch.aarch64", "https://www.retroarch.com/?page=platforms",
        setOf("d7a5eab30902c9b2775af6fb4eac2d294a64634fb47dd1382bc3ac5bf669982e"), 184, "GPL-3.0")
    val dolphin = Source("dolphin", "Dolphin", "org.dolphinemu.dolphinemu", "https://dolphin-emu.org/download/",
        setOf("8e7556d64ce8f7f15e36c4b3380aab0e326431146f9b26d2adee35326951fcab"), 22, "GPL-2.0-or-later")
    val armsx2 = Source("armsx2", "ARMSX2", "com.armsx2", "https://github.com/ARMSX2/ARMSX2/releases",
        setOf("df91c9ce39d3f53c9bab5f2c1e0820e761800c93f1805305b87e87486d244d5a"), 78, "GPL-3.0")
    val melonds = Source("melonds", "melonDS", "me.magnum.melonds", "https://github.com/rafaelvcaetano/melonDS-android/releases",
        setOf("0493bb8749289f0304cc868489dd68b930c19ccc4dce2da876bfe6b9bdbabebc"), 21, "GPL-3.0")

    private val byId = listOf(retroarch, dolphin, armsx2, melonds).associateBy { it.id }

    /** The official source for an emulator id as the catalog names it ("dolphin", or any "ra:" core for RetroArch). */
    fun of(emulator: String): Source? = if (emulator.startsWith("ra:")) retroarch else byId[emulator]

    /** RetroArch's shader collection, from libretro's own servers (the same file RetroArch's Online Updater fetches). */
    const val SHADERS = "https://buildbot.libretro.com/assets/frontend/shaders_slang.zip"
    const val SHADERS_MB = 55

    const val RETROARCH_LISTING = "https://buildbot.libretro.com/stable/"
    const val RETROARCH_KNOWN = "1.22.2"
    fun retroArchApk(version: String) = "https://buildbot.libretro.com/stable/$version/android/RetroArch_aarch64.apk"

    /** The newest stable RetroArch named in libretro's listing of its stable builds; [RETROARCH_KNOWN] if none can be read. */
    fun newestRetroArch(listing: String?): String {
        val found = Regex("""\b(\d+)\.(\d+)\.(\d+)/""").findAll(listing ?: "").map { m ->
            Triple(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
        }.maxWithOrNull(compareBy({ it.first }, { it.second }, { it.third }))
        return found?.let { "${it.first}.${it.second}.${it.third}" } ?: RETROARCH_KNOWN
    }

    /** Dolphin's release builds are named for their release; this is the one Timeline was last checked against. */
    const val DOLPHIN_APK = "https://dl.dolphin-emu.org/releases/2609a/dolphin-2609a.apk"

    fun githubLatest(repo: String) = "https://api.github.com/repos/$repo/releases/latest"
    const val ARMSX2_REPO = "ARMSX2/ARMSX2"
    const val MELONDS_REPO = "rafaelvcaetano/melonDS-android"

    /**
     * ARMSX2 publishes one build per Android generation. The newest one this device can run; the "legacy" build for
     * Android 9 and for processors older than ARMv8.2.
     */
    fun armsx2Flavor(sdk: Int): String = when {
        sdk >= 35 -> "a15-armv8.2-sdk35"
        sdk >= 33 -> "a13-armv8.2-sdk33"
        sdk >= 30 -> "a11-armv8.2-sdk30"
        else -> "legacy-armv8.0-sdk26"
    }

    /** The download address of the first asset in a GitHub "latest release" answer whose name matches [name]. */
    fun githubAsset(json: String?, name: Regex): String? = runCatching {
        val assets = org.json.JSONObject(json ?: return null).getJSONArray("assets")
        (0 until assets.length()).map { assets.getJSONObject(it) }
            .firstOrNull { name.containsMatchIn(it.getString("name")) && it.getString("name").endsWith(".apk") }
            ?.getString("browser_download_url")
    }.getOrNull()

    fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
    fun sha256(bytes: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(bytes))
}

/** Fetching from the internet: plain HTTPS, nothing else, and only when the player asks for it. */
object Downloads {
    private const val AGENT = "Timeline/1.0 (Android launcher; ${Links.SOURCE})"

    private fun open(url: String): HttpURLConnection {
        require(url.startsWith("https://")) { "only https" }
        return (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", AGENT)
        }
    }

    /** A small text answer (a listing, a release description), at most 2 MB; null on any failure. */
    fun text(url: String): String? = runCatching {
        val c = open(url)
        try {
            if (c.responseCode !in 200..299) return null
            c.inputStream.use { s -> String(s.readNBytes(2 shl 20)) }
        } finally { c.disconnect() }
    }.getOrNull()

    /**
     * Fetches [url] into [to], through a part file renamed only once complete. [progress] hears fractions from 0 to 1
     * when the size is known. [cancelled] is asked between blocks.
     */
    fun fetch(url: String, to: File, progress: (Float) -> Unit = {}, cancelled: () -> Boolean = { false }): Boolean {
        val part = File(to.path + ".part")
        var done = false
        try {
            to.parentFile?.mkdirs()
            val c = open(url)
            try {
                if (c.responseCode !in 200..299) return false
                val total = c.contentLengthLong
                var got = 0L
                c.inputStream.use { input ->
                    part.outputStream().use { out ->
                        val buf = ByteArray(1 shl 16)
                        while (true) {
                            if (cancelled()) return false
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            got += n
                            if (total > 0) progress(got.toFloat() / total)
                        }
                    }
                }
                if (total > 0 && got != total) return false
                done = part.renameTo(to)
                return done
            } finally { c.disconnect() }
        } catch (e: Exception) {
            return false
        } finally {
            if (!done) part.delete()          // a cancelled, short or failed download leaves nothing behind
        }
    }

    /**
     * An installer's package name, the SHA-256 digests of its certificates, and whether every one of them must match:
     * an app signed by several signers is accepted only if all are the developers'; one signer with a rotated key
     * carries its proven history, any of which may match.
     */
    fun inspect(ctx: Context, apk: File): Triple<String, Set<String>, Boolean>? = runCatching {
        val pm = ctx.packageManager
        val info = if (Build.VERSION.SDK_INT >= 33)
            pm.getPackageArchiveInfo(apk.path, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
        else @Suppress("DEPRECATION") pm.getPackageArchiveInfo(apk.path, PackageManager.GET_SIGNING_CERTIFICATES)
        info ?: return null
        val signing = info.signingInfo
        val certs = when {
            signing == null -> @Suppress("DEPRECATION") (info.signatures ?: emptyArray())
            signing.hasMultipleSigners() -> signing.apkContentsSigners
            else -> signing.signingCertificateHistory
        }.map { Sources.sha256(it.toByteArray()) }.toSet()
        Triple(info.packageName, certs, signing?.hasMultipleSigners() == true || signing == null)
    }.getOrNull()

    /** Null when [apk] is [source]'s own app, signed with its key; otherwise why it is refused. */
    fun refusal(ctx: Context, apk: File, source: Source): String? {
        val (pkg, certs, all) = inspect(ctx, apk) ?: return "the download could not be read as an app"
        if (pkg != source.pkg) return "the download is a different app ($pkg)"
        val signed = if (all) certs.isNotEmpty() && certs.all { it in source.certs } else certs.any { it in source.certs }
        if (!signed) return "the download is not signed by ${source.label}'s developers"
        return null
    }

    /** Where an emulator's installer can be fetched from right now, asking the project's servers when that is needed. */
    fun installerUrl(source: Source): String? = when (source.id) {
        "retroarch" -> Sources.retroArchApk(Sources.newestRetroArch(text(Sources.RETROARCH_LISTING)))
        "dolphin" -> Sources.DOLPHIN_APK
        "armsx2" -> Sources.githubAsset(text(Sources.githubLatest(Sources.ARMSX2_REPO)), Regex(Regex.escape(Sources.armsx2Flavor(Build.VERSION.SDK_INT))))
        "melonds" -> Sources.githubAsset(text(Sources.githubLatest(Sources.MELONDS_REPO)), Regex("""\.apk$"""))
        else -> null
    }
}

// ---------------------------------------------------------------------------------------------
// Files from the consoles themselves. Timeline names them, looks for them in the games folder's
// "bios" folder, and checks each against the checksums libretro publishes for its cores. It never
// offers them: they come from the player's own console.
// ---------------------------------------------------------------------------------------------

/** One file a console needs, under any of [names], with the MD5 checksums of known good copies. */
data class ConsoleFile(val names: List<String>, val md5: Set<String>, val note: String = "")

/** What a console needs before its games start. [optional]: games start without it, some run better with it. */
data class ConsoleNeeds(val system: String, val files: List<ConsoleFile>, val folder: String = "bios", val optional: Boolean = false)

object ConsoleFiles {
    val needs: List<ConsoleNeeds> = listOf(
        ConsoleNeeds("psx", listOf(ConsoleFile(listOf("scph5501.bin", "scph1001.bin", "scph7001.bin", "scph5500.bin", "scph5502.bin"),
            setOf("490f666e1afb15b7362b406ed1cea246", "924e392ed05558ffdb115408c263dccf", "8dd7d5296a650fac7319bce665a6a53c",
                "32736f17079d0b2b7024407c39bd3050", "1e68c231d0896b7eadcad1d7d8e76129"),
            "Any one PlayStation BIOS. A US console's is scph1001 or scph5501."))),
        ConsoleNeeds("saturn", listOf(ConsoleFile(listOf("mpr-17933.bin", "sega_101.bin"),
            setOf("3240872c70984b6cbfda1586cab68dbe", "85ec9ca47d8f6807718151cbcca8b964"),
            "mpr-17933.bin from a US or European Saturn, sega_101.bin from a Japanese one."))),
        ConsoleNeeds("segacd", listOf(ConsoleFile(listOf("bios_CD_U.bin", "bios_CD_E.bin", "bios_CD_J.bin"), emptySet(),
            "The Sega CD BIOS for the region of your games: U (US), E (Europe), J (Japan)."))),
        ConsoleNeeds("tg-cd", listOf(ConsoleFile(listOf("syscard3.pce"), setOf("38179df8f4ac870017db21ebcbf53114"),
            "The Super CD-ROM System card, version 3."))),
        ConsoleNeeds("ps2", listOf(ConsoleFile(listOf("*.bin"), emptySet(), "Any PlayStation 2 BIOS, in bios/ps2. ARMSX2 is pointed at that folder.")), folder = "bios/ps2"),
        ConsoleNeeds("dreamcast", listOf(ConsoleFile(listOf("dc_boot.bin"), setOf("e10c53c2f8b90bab96ead2d368858623"),
            "Optional: Flycast has a built-in replacement. A few games run better with the real one.")), folder = "bios/dc", optional = true),
        ConsoleNeeds("arcade", listOf(ConsoleFile(listOf("neogeo.zip"), emptySet(),
            "Needed by Neo Geo games only, in the arcade folder beside them.")), folder = "arcade", optional = true),
   )

    enum class State { CHECKED, FOUND, MISSING }

    /** For a game about to start in RetroArch: its console's file is where RetroArch reads it, only on the card, or nowhere. */
    enum class Ready { YES, COPY, MISSING }

    /**
     * Whether [system] can start. Only consoles that cannot start at all without their file are asked about (not the
     * optional ones, and not PlayStation 2, whose emulator keeps its own folder). [retro] is RetroArch's system folder,
     * where the card's bios folder is copied.
     */
    fun ready(system: String, roms: File?, retro: File): Ready {
        val n = needs.firstOrNull { it.system == system && !it.optional && it.system != "ps2" } ?: return Ready.YES
        fun has(dir: File, f: ConsoleFile): Boolean {
            val names = dir.list()?.toList() ?: return false
            return f.names.any { want -> names.any { it.equals(want, ignoreCase = true) } }
        }
        val card = File(roms ?: File("/nowhere"), n.folder)
        val there = File(retro, n.folder.removePrefix("bios").trimStart('/'))
        return when {
            n.files.all { has(there, it) } -> Ready.YES
            n.files.all { has(card, it) || has(there, it) } -> Ready.COPY
            else -> Ready.MISSING
        }
    }

    data class Finding(val system: String, val file: ConsoleFile, val state: State, val name: String?, val folder: String, val optional: Boolean)

    /** What is in place for each console in [systems] that needs anything. [md5] reads a file's checksum. */
    fun check(roms: File?, systems: Set<String>, md5: (File) -> String? = ::md5): List<Finding> =
        needs.filter { it.system in systems }.flatMap { n ->
            val dir = File(roms ?: File("/nowhere"), n.folder)
            n.files.map { f ->
                val present = if (f.names == listOf("*.bin")) dir.listFiles()?.filter { it.isFile && it.name.endsWith(".bin", true) }.orEmpty()
                else f.names.mapNotNull { name -> dir.listFiles()?.firstOrNull { it.isFile && it.name.equals(name, ignoreCase = true) } }
                val checked = if (f.md5.isEmpty()) null else present.firstOrNull { md5(it) in f.md5 }
                when {
                    checked != null -> Finding(n.system, f, State.CHECKED, checked.name, n.folder, n.optional)
                    present.isNotEmpty() -> Finding(n.system, f, State.FOUND, present.first().name, n.folder, n.optional)
                    else -> Finding(n.system, f, State.MISSING, null, n.folder, n.optional)
                }
            }
        }

    private val known = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** A file's MD5, remembered by path, size and time, since the setup list asks again every time it is drawn. */
    fun md5(f: File): String? {
        val key = "${f.path}|${f.length()}|${f.lastModified()}"
        known[key]?.let { return it }
        return hash(f)?.also { known[key] = it }
    }

    private fun hash(f: File): String? = runCatching {
        if (f.length() > 64L shl 20) return null
        val d = MessageDigest.getInstance("MD5")
        f.inputStream().use { s -> val b = ByteArray(1 shl 16); while (true) { val n = s.read(b); if (n < 0) break; d.update(b, 0, n) } }
        Sources.hex(d.digest())
    }.getOrNull()
}
