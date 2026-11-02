// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 NOSTOS, LLC

package market.nostos.timeline

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.toArgb
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.BatteryManager
import android.os.Environment
import android.os.SystemClock
import android.provider.DocumentsContract
import android.provider.Settings
import android.util.Xml
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Size
import coil.transform.Transformation
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withTimeoutOrNull
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("launcher", Context.MODE_PRIVATE)
    fun str(k: String, d: String = ""): String = sp.getString(k, d) ?: d
    fun bool(k: String, d: Boolean): Boolean = sp.getBoolean(k, d)
    fun int(k: String, d: Int): Int = sp.getInt(k, d)
    fun long(k: String, d: Long): Long = runCatching { sp.getLong(k, d) }.getOrDefault(d)
    fun put(k: String, v: Long) = sp.edit().putLong(k, v).apply()
    fun put(k: String, v: String) = sp.edit().putString(k, v).apply()

    /** Written before returning, for the one value that must not be lost if the app stops a moment later. */
    fun putNow(k: String, v: String) { sp.edit().putString(k, v).commit() }
    fun putNow(k: String, v: Boolean) { sp.edit().putBoolean(k, v).commit() }
    fun removeNow(keys: Collection<String>) { sp.edit().apply { keys.forEach { remove(it) } }.commit() }
    fun put(k: String, v: Boolean) = sp.edit().putBoolean(k, v).apply()
    fun put(k: String, v: Int) = sp.edit().putInt(k, v).apply()
    fun strings(k: String): Set<String> = sp.getStringSet(k, emptySet())?.toSet() ?: emptySet()
    fun putStrings(k: String, v: Set<String>) = sp.edit().putStringSet(k, v).apply()
}

object Storage {
    fun hasAccess(): Boolean = Environment.isExternalStorageManager()

    /** Storage roots: internal first, then any card. */
    fun volumes(ctx: Context): List<File> =
        ctx.getExternalFilesDirs(null).filterNotNull().mapNotNull { f ->
            f.absolutePath.substringBefore("/Android/data/", "").takeIf { it.isNotEmpty() }?.let(::File)
        }

    fun romCandidates(ctx: Context): List<File> {
        val vols = volumes(ctx)
        // A card is the usual home for games, so look there before internal storage.
        // One folder per volume: on a card that ignores case, "ROMs" and "roms" are the same folder.
        return (vols.drop(1) + vols.take(1)).mapNotNull { v -> listOf("ROMs", "roms", "Roms").map { File(v, it) }.firstOrNull { it.isDirectory } }
    }

    fun findRoms(ctx: Context, prefs: Prefs): File? {
        prefs.str("romsRoot").takeIf { it.isNotEmpty() }?.let(::File)?.takeIf { it.isDirectory }?.let { return it }
        return romCandidates(ctx).firstOrNull()
    }

    /**
     * Where scraped text and artwork live: a "_timeline" folder inside the games folder, so everything travels with the
     * games. This is the folder the scraper writes to.
     */
    fun metaDir(ctx: Context, roms: File?): File = File(roms ?: File(internal(ctx), "ROMs"), "_timeline")

    /** Internal shared storage. Named outright when Android has no volume to offer yet, as can happen just after a restart. */
    fun internal(ctx: Context): File = volumes(ctx).firstOrNull() ?: File("/storage/emulated/0")

    /** ES-DE's own data folder, if there is one. Read as a second source, never written. */
    fun esdeDir(ctx: Context, roms: File?): File? =
        (listOfNotNull(roms?.parentFile) + volumes(ctx)).map { File(it, "ES-DE") }
            .firstOrNull { File(it, "gamelists").isDirectory || File(it, "downloaded_media").isDirectory }
}

data class Meta(
    val name: String = "", val desc: String = "", val rating: Float = -1f, val date: String = "",
    val developer: String = "", val publisher: String = "", val genre: String = "", val players: String = "",
) {
    val year: String get() = date.take(4).takeIf { it.length == 4 && it.all(Char::isDigit) } ?: ""
    val released: String
        get() = if (date.length >= 8 && date.take(8).all(Char::isDigit)) {
            val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
            val m = date.substring(4, 6).toInt().coerceIn(1, 12)
            val day = date.substring(6, 8).toInt()
            when {
                day <= 1 && m == 1 -> year                       // only the year is known
                day <= 1 -> "${months[m - 1]} ${date.take(4)}"   // the month is known, the day is not
                else -> "${months[m - 1]} $day, ${date.take(4)}"
            }
        } else year
}

/**
 * Reads the gamelist.xml files and media folders that ES-DE, RetroBat and Skraper all write:
 *   <dir>/gamelists/<system>/gamelist.xml
 *   <dir>/downloaded_media/<system>/{covers,screenshots,miximages,3dboxes,titlescreens,marquees,fanart,videos}/<rom name>.<ext>
 */
class MetaStore(val dir: File, alsoAt: () -> File? = { null }) {
    private val also: File? by lazy(alsoAt)
    private val cache = HashMap<String, MutableMap<String, Meta>>()
    private val listings = ConcurrentHashMap<String, Set<String>>()

    /** The names in one picture folder, read once: a single listing instead of a question to the card per file tried. */
    private fun names(d: File): Set<String> = listings.getOrPut(d.path) { d.list()?.toHashSet() ?: emptySet() }

    /** Pictures were added or replaced: look at the folders again. */
    fun forgetPictures() = listings.clear()
    private val stamps = HashMap<String, Long>()
    private val dirty = HashSet<String>()

    private fun stamp(id: String): Long =
        File(dir, "gamelists/$id/gamelist.xml").lastModified() + (also?.let { File(it, "gamelists/$id/gamelist.xml").lastModified() } ?: 0L)

    /** Forgets any system whose gamelist changed on disk since it was read. True if something was forgotten. */
    @Synchronized
    fun refresh(): Boolean {
        val stale = cache.keys.filter { it !in dirty && stamps[it] != stamp(it) }
        stale.forEach { cache.remove(it) }
        return stale.isNotEmpty()
    }

    @Synchronized
    fun system(id: String): Map<String, Meta> = cache.getOrPut(id) {
        stamps[id] = stamp(id)
        val merged = HashMap<String, Meta>()
        also?.let { merged.putAll(parse(File(it, "gamelists/$id/gamelist.xml"))) }
        for ((k, v) in parse(File(dir, "gamelists/$id/gamelist.xml"))) merged[k] = GameList.merge(merged[k], v)
        merged
    }

    fun meta(g: Game): Meta? = system(g.system)[g.file]

    @Synchronized
    fun put(g: Game, m: Meta) {
        system(g.system)
        val map = cache.getValue(g.system)
        map[g.file] = GameList.merge(map[g.file], m)
        dirty += g.system
    }

    /** Writes any changed systems back to gamelist.xml. */
    @Synchronized
    fun flush() {
        for (id in dirty) {
            runCatching { GameList.write(File(dir, "gamelists/$id/gamelist.xml"), cache.getValue(id)) }
            stamps[id] = stamp(id)
        }
        dirty.clear()
    }

    private val exts = listOf("png", "jpg", "jpeg", "webp")

    private fun media(g: Game, kind: String): File? {
        for (root in listOfNotNull(dir, also)) {
            val d = File(root, "downloaded_media/${g.system}/$kind")
            val have = names(d)
            if (have.isEmpty()) continue
            for (ext in exts) if ("${g.stem}.$ext" in have) return File(d, "${g.stem}.$ext")
        }
        return null
    }

    /** The picture shown for a game: the preferred kind if it exists, then whatever else there is. */
    fun cover(g: Game, prefer: String = "covers"): File? =
        (listOf(prefer) + listOf("covers", "miximages", "3dboxes", "screenshots", "titlescreens")).distinct()
            .firstNotNullOfOrNull { media(g, it) }

    fun screenshot(g: Game): File? = media(g, "screenshots") ?: media(g, "titlescreens") ?: media(g, "fanart")

    fun hasArt(g: Game): Boolean = cover(g) != null

    /** Everything a page shows about one game, gathered in a single trip to the card. */
    fun shown(g: Game, prefer: String): Shown {
        val cover = cover(g, prefer)
        val shot = screenshot(g)?.takeIf { it != cover }
        val files = when {
            g.discs.size > 1 -> g.discs.map(::File)
            g.path.endsWith(".m3u", true) -> File(g.path).parentFile?.listFiles()?.toList() ?: emptyList()
            else -> listOf(File(g.path))
        }
        return Shown(
            meta = meta(g), cover = cover, coverRatio = Pictures.ratio(cover), shot = shot, shotRatio = Pictures.ratio(shot),
            bytes = files.sumOf { it.length() }, loaded = true,
       )
    }

    private fun parse(file: File): Map<String, Meta> {
        if (!file.isFile) return emptyMap()
        val out = HashMap<String, Meta>()
        runCatching {
            file.inputStream().use { input ->
                val p = Xml.newPullParser()
                p.setInput(input, null)
                var fields: HashMap<String, String>? = null
                var tag = ""
                var event = p.eventType
                while (event != XmlPullParser.END_DOCUMENT) {
                    when (event) {
                        XmlPullParser.START_TAG -> if (p.name == "game") fields = HashMap() else tag = p.name
                        XmlPullParser.TEXT -> fields?.let { f -> if (tag.isNotEmpty()) f[tag] = (f[tag] ?: "") + p.text }
                        XmlPullParser.END_TAG -> {
                            if (p.name == "game") {
                                fields?.let { f ->
                                    val key = (f["path"] ?: "").trim().removePrefix("./").substringAfterLast('/')
                                    if (key.isNotEmpty()) out[key] = Meta(
                                        name = f["name"].orEmpty().trim(), desc = f["desc"].orEmpty().trim(),
                                        rating = f["rating"]?.trim()?.toFloatOrNull() ?: -1f, date = f["releasedate"].orEmpty().trim(),
                                        developer = f["developer"].orEmpty().trim(), publisher = f["publisher"].orEmpty().trim(),
                                        genre = f["genre"].orEmpty().trim(), players = f["players"].orEmpty().trim(),
                                   )
                                }
                                fields = null
                            }
                            tag = ""
                        }
                    }
                    event = p.next()
                }
            }
        }
        return out
    }
}

data class Shown(
    val meta: Meta? = null, val cover: File? = null, val coverRatio: Float = 1f,
    val shot: File? = null, val shotRatio: Float = 4f / 3f, val bytes: Long = 0L, val loaded: Boolean = false,
)

object Pictures {
    /** Width over height, read from the file header only. */
    fun ratio(f: File?): Float {
        if (f == null) return 1f
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { BitmapFactory.decodeFile(f.path, o) }
        return if (o.outWidth > 0 && o.outHeight > 0) o.outWidth.toFloat() / o.outHeight else 1f
    }

    fun request(ctx: Context, file: File, px: Int, pixelArt: Boolean = false): ImageRequest =
        ImageRequest.Builder(ctx).data(file).size(px)
            // Named outright, so the picture is found in memory without first asking the card when the file last changed.
            // The memory is emptied whenever scraped pictures change (AppState.metaChanged).
            .memoryCacheKey(file.path + "@" + px + if (pixelArt) "#whole" else "")
            .apply { if (pixelArt) transformations(WholeNumberUpscale(px)) }.build()

    /** Decodes a picture ahead of need, so it is already in memory when the highlight reaches it. */
    fun warm(ctx: Context, file: File, px: Int) { ctx.imageLoader.enqueue(request(ctx, file, px)) }
}

/**
 * A small screenshot is enlarged by a whole number before it is fitted to the page, so its pixels
 * stay square and sharp instead of being smeared by the usual smoothing.
 */
class WholeNumberUpscale(private val target: Int) : Transformation {
    override val cacheKey: String = "whole-number-$target"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val longest = maxOf(input.width, input.height, 1)
        val n = ((target + longest - 1) / longest).coerceAtMost(8)
        return if (n <= 1) input else Bitmap.createScaledBitmap(input, input.width * n, input.height * n, false)
    }
}

/** Shelves the player makes. One text file each in "_timeline/shelves", a game per line, easy to edit by hand. */
data class UserShelf(val name: String, val keys: List<String>)

object UserShelves {
    private fun dir(meta: File) = File(meta, "shelves")

    fun clean(raw: String): String = raw.replace(Regex("[\\/:*?\"<>|\n\t]"), " ").replace(Regex("\\s+"), " ").trim().take(20).trim()

    fun load(meta: File): List<UserShelf> =
        (dir(meta).listFiles() ?: emptyArray()).filter { it.isFile && it.name.endsWith(".txt") && !it.name.startsWith(".") }
            .map { f -> UserShelf(f.name.removeSuffix(".txt"), runCatching { f.readLines() }.getOrDefault(emptyList()).map { it.trim() }.filter { it.contains('/') }.distinct()) }
            .sortedBy { it.name.lowercase() }

    fun save(meta: File, s: UserShelf) {
        dir(meta).mkdirs()
        File(dir(meta), s.name + ".txt").writeText(s.keys.joinToString("") { it + "\n" })
    }

    fun delete(meta: File, name: String) { File(dir(meta), "$name.txt").delete() }
}

data class AppEntry(val label: String, val pkg: String, val cls: String)

/** The folder picker's request number for Dolphin's settings folder. */
const val TREE_DOLPHIN = 42

/** Reading and writing inside a folder another app has shared through Android's folder picker. */
object Docs {
    /** Android's folder picker, opened on the folder an app offers under [authority]. */
    fun pick(authority: String, root: String): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            // As a root, not a document: the picker opens straight on the app's folder that way (tried on Android 13).
            .putExtra(DocumentsContract.EXTRA_INITIAL_URI, DocumentsContract.buildRootUri(authority, root))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)

    /** The folder is still shared with this app, for reading and writing. */
    fun held(ctx: Context, tree: Uri): Boolean = runCatching {
        ctx.contentResolver.persistedUriPermissions.any { it.uri == tree && it.isReadPermission && it.isWritePermission }
    }.getOrDefault(false)

    private fun child(ctx: Context, tree: Uri, parent: String, name: String): String? {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent)
        ctx.contentResolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            while (c.moveToNext()) if (c.getString(1) == name) return c.getString(0)
        }
        return null
    }

    /** The document at a path below the shared folder; with [create], made (folders and all) when it is not there. */
    private fun locate(ctx: Context, tree: Uri, path: String, create: Boolean): Uri? {
        var id = DocumentsContract.getTreeDocumentId(tree)
        val parts = path.split('/').filter { it.isNotEmpty() }
        for ((i, name) in parts.withIndex()) {
            id = child(ctx, tree, id, name) ?: run {
                if (!create) return null
                val made = DocumentsContract.createDocument(ctx.contentResolver, DocumentsContract.buildDocumentUriUsingTree(tree, id),
                    if (i == parts.size - 1) "application/octet-stream" else DocumentsContract.Document.MIME_TYPE_DIR, name) ?: return null
                DocumentsContract.getDocumentId(made)
            }
        }
        return DocumentsContract.buildDocumentUriUsingTree(tree, id)
    }

    /** The file's text, or null when there is no such file. Throws when the file is there but cannot be read. */
    fun read(ctx: Context, tree: Uri, path: String): String? {
        val u = locate(ctx, tree, path, false) ?: return null
        return ctx.contentResolver.openInputStream(u)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: throw java.io.IOException("could not open $path")
    }

    fun write(ctx: Context, tree: Uri, path: String, text: String): Boolean = runCatching {
        val u = locate(ctx, tree, path, true) ?: return false
        ctx.contentResolver.openOutputStream(u, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) } != null
    }.getOrDefault(false)

    private val listing = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED)

    /** The shared folder answers and has something in it. An emulator that is not ready yet answers with an empty folder, not an error. */
    fun alive(ctx: Context, tree: Uri): Boolean = runCatching {
        ctx.contentResolver.query(DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree)),
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)?.use { it.count > 0 } ?: false
    }.getOrDefault(false)

    /**
     * Every file below a folder of the shared one, with its size and when it was written. Empty when the folder is not
     * there; null when it could not be read, which callers must not take for "nothing there".
     */
    fun walk(ctx: Context, tree: Uri, top: String): List<SaveEntry>? = runCatching {
        val start = locate(ctx, tree, top, false) ?: return emptyList()
        val out = ArrayList<SaveEntry>()
        var whole = true
        fun into(id: String, path: String, depth: Int) {
            if (depth > 12) return
            val folders = ArrayList<Pair<String, String>>()
            val c = ctx.contentResolver.query(DocumentsContract.buildChildDocumentsUriUsingTree(tree, id), listing, null, null, null)
            if (c == null) { whole = false; return }
            c.use {
                while (c.moveToNext()) {
                    val name = c.getString(1) ?: continue
                    if (c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) folders.add(c.getString(0) to "$path/$name")
                    else out.add(SaveEntry("$path/$name", c.getLong(3), c.getLong(4)))
                }
            }
            for ((child, at) in folders) into(child, at, depth + 1)
        }
        into(DocumentsContract.getDocumentId(start), top.trim('/'), 0)
        if (whole) out else null
    }.getOrNull()

    /** One file as it is now: its size and when it was written. Null when it is not there. */
    fun stat(ctx: Context, tree: Uri, path: String): SaveEntry? = runCatching {
        val u = locate(ctx, tree, path, false) ?: return null
        ctx.contentResolver.query(u, listing, null, null, null)?.use { c ->
            if (c.moveToFirst() && c.getString(2) != DocumentsContract.Document.MIME_TYPE_DIR) SaveEntry(path, c.getLong(3), c.getLong(4)) else null
        }
    }.getOrNull()

    fun open(ctx: Context, tree: Uri, path: String): java.io.InputStream? = runCatching {
        locate(ctx, tree, path, false)?.let { ctx.contentResolver.openInputStream(it) }
    }.getOrNull()

    private fun pour(ctx: Context, from: File, into: Uri): Boolean =
        ctx.contentResolver.openOutputStream(into, "wt")?.use { o -> from.inputStream().use { it.copyTo(o) } } == from.length()

    /** What is at a path is, byte for byte, [from]. An emulator's word that a file was written or renamed is not taken for it. */
    private fun holds(ctx: Context, tree: Uri, path: String, from: File): Boolean = runCatching {
        if (stat(ctx, tree, path)?.size != from.length()) return false
        open(ctx, tree, path)?.use { there -> from.inputStream().use { mine ->
            val a = ByteArray(1 shl 16); val b = ByteArray(1 shl 16)
            while (true) {
                var x = 0; while (x < a.size) { val g = there.read(a, x, a.size - x); if (g < 0) break; x += g }
                var y = 0; while (y < b.size) { val g = mine.read(b, y, b.size - y); if (g < 0) break; y += g }
                if (x != y || !a.contentEquals(b)) return false
                if (x < a.size) return true
            }
            @Suppress("UNREACHABLE_CODE") true
        } } ?: false
    }.getOrDefault(false)

    /**
     * Replaces the file at a path below the shared folder with a copy of [from], making it and its folders when they
     * are not there. The copy is written whole under another name first and only then takes the file's place, so a
     * write that is cut short never leaves half a save where the emulator will look for it. Where the emulator will
     * not rename a file, it is written in place and its length checked.
     */
    fun put(ctx: Context, tree: Uri, path: String, from: File): Boolean = runCatching {
        if (!from.isFile) return false                       // looked at before anything on the other side is touched
        val r = ctx.contentResolver
        val name = path.substringAfterLast('/')
        val beside = path.substringBeforeLast('/', "") + "/" + name + ".part"
        locate(ctx, tree, beside, false)?.let { runCatching { DocumentsContract.deleteDocument(r, it) } }
        val part = locate(ctx, tree, beside, true) ?: return false
        if (!pour(ctx, from, part) || !holds(ctx, tree, beside, from)) { runCatching { DocumentsContract.deleteDocument(r, part) }; return false }
        val old = locate(ctx, tree, path, false)
        var moved: Uri? = null
        val swapped = runCatching {
            if (old != null && !DocumentsContract.deleteDocument(r, old)) false
            else { moved = DocumentsContract.renameDocument(r, part, name); moved != null }
        }.getOrDefault(false)
        if (swapped && holds(ctx, tree, path, from)) return true
        // No renaming here (or it did not do what it said): straight into place, then. Whatever became of the copy
        // written beside is cleared away first, under either name, so no second file of the save is left lying there.
        runCatching {
            val inPlace = locate(ctx, tree, path, false)?.let { DocumentsContract.getDocumentId(it) }
            moved?.let { m -> if (inPlace != null && DocumentsContract.getDocumentId(m) != inPlace) DocumentsContract.deleteDocument(r, m) }
        }
        runCatching { locate(ctx, tree, beside, false)?.let { DocumentsContract.deleteDocument(r, it) } }
        val u = locate(ctx, tree, path, true) ?: return false
        pour(ctx, from, u) && holds(ctx, tree, path, from)
    }.getOrDefault(false)
}

/**
 * A last word before the launcher goes down: what went wrong, written where it can be read afterwards (the device's
 * own storage, Timeline/crash). The launcher is the home screen, so a fault nobody can read would be hard to chase
 * on a device with no cable attached. The newest five are kept. Android then deals with the fault as it always does.
 */
object CrashNote {
    private var installed = false

    fun dir(ctx: Context) = File(Storage.internal(ctx), "Timeline/crash")

    fun install(ctx: Context) {
        if (installed) return
        installed = true
        val before = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, fault ->
            runCatching {
                val d = dir(ctx)
                d.mkdirs()
                File(d, "crash-" + Saves.stamp(System.currentTimeMillis()) + ".txt").writeText(
                    "Timeline ${Runner.version(ctx)}, Android ${android.os.Build.VERSION.RELEASE}, ${android.os.Build.MODEL}\nThread: ${thread.name}\n\n" +
                        android.util.Log.getStackTraceString(fault))
                d.listFiles()?.sortedByDescending { it.name }?.drop(5)?.forEach { it.delete() }
            }
            before?.uncaughtException(thread, fault)
        }
    }

    /** The newest note, if it was written since the launcher last said so. */
    fun unseen(ctx: Context, prefs: Prefs): File? = runCatching {
        val newest = dir(ctx).listFiles()?.filter { it.name.startsWith("crash-") }?.maxByOrNull { it.name } ?: return null
        if (newest.name == prefs.str("crash.seen")) null else newest.also { prefs.put("crash.seen", it.name) }
    }.getOrNull()
}

object Runner {
    private val have = ConcurrentHashMap<String, Boolean>()

    /** Remembered until the launcher next comes back to the front, so a menu does not ask Android on every key press. */
    fun installed(ctx: Context, pkg: String): Boolean = have.getOrPut(pkg) {
        try { ctx.packageManager.getPackageInfo(pkg, 0); true } catch (e: PackageManager.NameNotFoundException) { false }
    }

    private val libDirs = ConcurrentHashMap<String, String>()

    /** Forget what was learned about installed apps. Called when the launcher comes back to the front. */
    fun forget() { libDirs.clear(); have.clear() }


    private fun libDir(ctx: Context, pkg: String): String = libDirs.getOrPut(pkg) {
        runCatching { ctx.packageManager.getApplicationInfo(pkg, 0).nativeLibraryDir }.getOrNull() ?: ""
    }

    /**
     * A core that came inside the RetroArch install. The installer on the card carries its cores as native
     * libraries, which Android unpacks beside RetroArch's own; Android only unpacks names that begin with "lib".
     */
    fun coreInside(ctx: Context, pkg: String, core: String): File? =
        libDir(ctx, pkg).takeIf { it.isNotEmpty() }?.let { File(it, "lib${core}_libretro_android.so") }?.takeIf { it.isFile }

    fun plan(ctx: Context, emulatorId: String, romPath: String): LaunchPlan? {
        val plan = Launch.plan(
            emulatorId, romPath,
            installed = { installed(ctx, it) },
            fileText = { runCatching { File(it).readText().trim() }.getOrDefault("") },
            providerUri = { FileProvider.getUriForFile(ctx, "market.nostos.timeline.files", File(it)).toString() },
       ) ?: return null
        if (!emulatorId.startsWith("ra:") || !plan.installed) return plan
        val inside = coreInside(ctx, plan.pkg, emulatorId.removePrefix("ra:")) ?: return plan
        return plan.copy(extras = plan.extras + ("LIBRETRO" to inside.absolutePath))
    }

    /**
     * True when this emulator can be expected to start a game: installed, and for a RetroArch that came with
     * cores inside, the core is one of them. A plain RetroArch keeps its cores where no other app can look,
     * so there the core is taken on trust.
     */
    fun ready(ctx: Context, emulatorId: String): Boolean {
        val plan = plan(ctx, emulatorId, "/x") ?: return false
        if (!plan.installed) return false
        if (!emulatorId.startsWith("ra:")) return true
        if (coreInside(ctx, plan.pkg, emulatorId.removePrefix("ra:")) != null) return true
        return coreInside(ctx, plan.pkg, "snes9x") == null && coreInside(ctx, plan.pkg, "fbneo") == null
    }

    /** Starts the emulator. Returns null on success, or a sentence to show the player. */
    fun start(ctx: Context, plan: LaunchPlan): String? {
        if (!plan.installed) return "${plan.emulator} is not installed."
        val i = Intent()
        i.component = ComponentName(plan.pkg, plan.cls)
        plan.action?.let { i.action = it }
        plan.category?.let { i.addCategory(it) }
        var flags = Intent.FLAG_ACTIVITY_NEW_TASK
        if (plan.clear) flags = flags or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        if (plan.grantRead) flags = flags or Intent.FLAG_GRANT_READ_URI_PERMISSION
        i.flags = flags
        plan.data?.let { i.data = Uri.parse(it) }
        plan.extras.forEach { (k, v) -> i.putExtra(k, v) }
        plan.bools.forEach { (k, v) -> i.putExtra(k, v) }
        plan.arrays.forEach { (k, v) -> i.putExtra(k, v.toTypedArray()) }
        return try {
            ctx.startActivity(i, plainStart())
            null
        } catch (e: Exception) {
            "Could not start ${plan.emulator}."
        }
    }

    /**
     * Asks Android to open an emulator on a plain dark screen instead of its icon or logo (Android 13 shows the app's
     * splash, Dolphin's dolphin, ARMSX2's crystal, hakuX's bird, as an app opens afresh). The game's own start is untouched.
     */
    fun plainStart(): android.os.Bundle? = if (android.os.Build.VERSION.SDK_INT >= 33) runCatching {
        android.app.ActivityOptions.makeBasic().apply { setSplashScreenStyle(android.window.SplashScreen.SPLASH_SCREEN_STYLE_SOLID_COLOR) }.toBundle()
    }.getOrNull() else null

    fun apps(ctx: Context): List<AppEntry> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { AppEntry(it.loadLabel(pm).toString(), it.activityInfo.packageName, it.activityInfo.name) }
            .filter { it.pkg != ctx.packageName }
            .sortedBy { it.label.lowercase() }
    }

    fun open(ctx: Context, app: AppEntry): Boolean = try {
        ctx.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(ComponentName(app.pkg, app.cls)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) { false }

    fun openSettings(ctx: Context, action: String, data: String? = null): Boolean = try {
        ctx.startActivity(Intent(action).apply { if (data != null) this.data = Uri.parse(data) }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    } catch (e: Exception) { false }

    /** One of Android's own settings pages by name, the first of [components] this device has. */
    fun openSettingsPage(ctx: Context, vararg components: String): Boolean = components.any { spec ->
        runCatching {
            ctx.startActivity(Intent(Intent.ACTION_MAIN).setComponent(ComponentName.unflattenFromString(spec)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
        }.getOrDefault(false)
    }

    /** Android's navigation mode: 0 three buttons, 1 two, 2 gestures (null if this Android does not say). */
    fun navigationMode(ctx: Context): Int? = runCatching { Settings.Secure.getInt(ctx.contentResolver, "navigation_mode") }.getOrNull()

    /** Whether Android asks for a PIN, pattern or password when the device wakes. */
    fun lockHasCode(ctx: Context): Boolean = runCatching {
        ctx.getSystemService(android.app.KeyguardManager::class.java)?.isDeviceSecure == true
    }.getOrDefault(false)

    /** Where the launcher keeps the RetroArch setup it writes. Internal shared storage, which RetroArch can always read. */
    fun retroPaths(ctx: Context, pkg: String): RetroPack.Paths? = runCatching {
        val info = ctx.packageManager.getApplicationInfo(pkg, 0)
        RetroPack.Paths(File(Storage.internal(ctx), "Timeline/retroarch").absolutePath, pkg, info.dataDir, info.sourceDir)
    }.getOrNull()

    /**
     * Writes the setup: owned keys are merged into config and option files so RetroArch's own changes
     * survive, presets are replaced, and presets from an earlier choice that no longer apply are removed.
     */
    /**
     * Copies BIOS files from "bios" beside the games to RetroArch's system folder. A file of the same name already there
     * is never replaced: it may be one the player put there for RetroArch on purpose.
     * The ps2 folder is left out: ARMSX2 is pointed at it from its own settings. Returns how many are in place.
     */
    fun copyBios(roms: File?): Int = runCatching {
        val from = File(roms ?: return 0, "bios")
        if (!from.isDirectory) return 0
        val to = File("/storage/emulated/0/RetroArch/system")
        var count = 0
        // The PlayStation 2 and Xbox folders are for their own emulators, which are pointed at them from their own setup.
        from.walkTopDown().filter { it.isFile && !it.name.startsWith(".") && !it.relativeTo(from).path.startsWith("ps2") && !it.relativeTo(from).path.startsWith("xbox") }.forEach { f ->
            val dst = File(to, f.relativeTo(from).path)
            if (!dst.exists()) {
                dst.parentFile?.mkdirs()
                val part = File(dst.path + ".part")         // whole or not at all: a copy cut short is never taken for the file
                f.copyTo(part, overwrite = true)
                if (!part.renameTo(dst)) part.delete()
            }
            count++
        }
        count
    }.getOrDefault(0)

    /**
     * Unpacks RetroArch's shader collection (libretro's own shaders_slang.zip, fetched by the setup) to where the
     * generated presets point. Nothing is written outside that folder. Returns how many files were written.
     */
    fun unpackShaders(zip: File, p: RetroPack.Paths): Int = runCatching {
        if (!zip.isFile) return 0
        val to = File(p.slang)
        val inside = to.canonicalPath + File.separator
        var n = 0
        java.util.zip.ZipFile(zip).use { z ->
            for (e in z.entries()) {
                val out = File(to, e.name)
                if (!out.canonicalPath.startsWith(inside)) continue
                if (e.isDirectory) { out.mkdirs(); continue }
                out.parentFile?.mkdirs()
                z.getInputStream(e).use { i -> out.outputStream().use { o -> i.copyTo(o) } }
                n++
            }
        }
        if (n > 0) File(to, UNPACKED).writeText(zip.length().toString())     // written last: its presence means every file made it
        n
    }.getOrDefault(0)

    const val UNPACKED = ".unpacked"

    /** Whether Android will take an installer from this app without first sending the player to a settings page. */
    fun canInstall(ctx: Context): Boolean = runCatching { ctx.packageManager.canRequestPackageInstalls() }.getOrDefault(false)

    fun retroPackage(ctx: Context): String? = listOf("com.retroarch.aarch64", "com.retroarch", "com.retroarch.ra32").firstOrNull { installed(ctx, it) }

    /** RetroArch asks for this itself, from its own front page, the first time it is opened. */
    fun retroCanRead(ctx: Context, pkg: String): Boolean {
        // A RetroArch built for Android 11 or later does not use this permission at all; there is nothing to wait for.
        val modern = runCatching { ctx.packageManager.getApplicationInfo(pkg, 0).targetSdkVersion > 29 }.getOrDefault(false)
        return modern || ctx.packageManager.checkPermission("android.permission.READ_EXTERNAL_STORAGE", pkg) == PackageManager.PERMISSION_GRANTED
    }

    fun writeRetroSetup(look: Look, p: RetroPack.Paths, pal: List<Pair<String, String>>, arcade: List<String> = emptyList()): Boolean = runCatching {
        val home = File(p.home)
        val config = File(home, "config")
        config.mkdirs()
        File(home, "shaders").mkdirs()
        File(p.logs).mkdirs()
        // Written only where the file differs from what it should hold.
        fun put(f: File, text: String) { if (!f.isFile || f.readText() != text) { f.parentFile?.mkdirs(); f.writeText(text) } }
        val main = File(p.config)
        put(main, RetroPack.merge(if (main.isFile) main.readText() else "", RetroPack.base(look, p)))
        // The 3D consoles' CRT: the GLSL ones are the launcher's own; the Vulkan one is made from the collection's GDV,
        // and without it (collection not unpacked yet, or a different one) those consoles are drawn plain for now.
        val dir = File(home, Crt3d.DIR)
        put(File(dir, Crt3d.GLSL), Crt3d.glsl)
        put(File(dir, Crt3d.STOCK_GLSL), Crt3d.stockGlsl)
        put(File(dir, Signal.SLANG), Signal.slang)
        put(File(dir, SteadyBeam.SLANG), SteadyBeam.slang)
        put(File(dir, Signal.GLSL), Signal.glsl)
        put(File(dir, Colour.SLANG), Colour.slang)
        put(File(dir, Colour.GLSL), Colour.glsl)
        put(File(dir, GenesisDac.SLANG), GenesisDac.slang)
        val gdv = File(p.slang, "crt/shaders/crt-gdv-mini-ultra.slang")
        val made = if (gdv.isFile) runCatching { Crt3d.slang(gdv.readText()) }.getOrNull() else null
        if (made != null) put(File(dir, Crt3d.SLANG), made)
        val files = RetroPack.files(look.copy(crt3dSlang = made != null), p, pal, arcade)
        val listed = File(home, "written.txt")
        val before = if (listed.isFile) listed.readLines().toSet() else emptySet()
        // Only ever a file inside the launcher's own settings folder, whatever the list has come to hold.
        val inside = config.canonicalPath + File.separator
        for (gone in before - files.keys) File(config, gone).takeIf { !gone.contains("..") && it.canonicalPath.startsWith(inside) && it.isFile }?.delete()
        for ((path, text) in files) {
            val f = File(config, path)
            val keys = text.lines().filter { it.contains('=') }.associate { it.substringBefore('=').trim() to it.substringAfter('=').trim().trim('"') }
            // A game's own options file is read by RetroArch instead of the core's, so a new one starts from the core's
            // (every option RetroArch knows, as set there) and only then takes the game's own.
            val dirName = path.substringBefore('/')
            val core = File(config, "$dirName/$dirName.opt")
            val seed = if (path.endsWith(".opt") && !f.isFile && f.name != core.name && core.isFile) core.readText() else null
            when {
                path.endsWith(".slangp") || path.endsWith(".glslp") -> put(f, text)
                seed != null -> put(f, RetroPack.merge(seed, keys))
                !f.isFile -> put(f, text)
                else -> put(f, RetroPack.merge(f.readText(), keys))
            }
        }
        // Remembered so they can be removed later: presets, and files named after a game or a folder rather than a core.
        put(listed, files.keys.filter { it.endsWith(".slangp") || it.endsWith(".glslp") || it.substringAfter('/').substringBeforeLast('.') != it.substringBefore('/') }.joinToString("\n"))
        true
    }.getOrDefault(false)

    const val RETRO = RetroPack.SHARED


    fun openPackage(ctx: Context, pkg: String): Boolean = try {
        ctx.startActivity(ctx.packageManager.getLaunchIntentForPackage(pkg)!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    } catch (e: Exception) { false }

    /**
     * Closes a RetroArch that is running and well: handed a game it does not hold, it exits by itself (read from its
     * own code, and tried on Android 13). A RetroArch frozen by a failed start does not answer this either.
     */
    fun closeRetro(ctx: Context, pkg: String): Boolean = try {
        ctx.startActivity(Intent().setComponent(ComponentName(pkg, "com.retroarch.browser.retroactivity.RetroActivityFuture"))
            .putExtra("ROM", "/timeline/none").putExtra("LIBRETRO", "/timeline/none").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) { false }

    /** Force-stopped in Android's settings and not started since: nothing of it is running. */
    fun stopped(ctx: Context, pkg: String): Boolean = runCatching {
        ctx.packageManager.getApplicationInfo(pkg, 0).flags and android.content.pm.ApplicationInfo.FLAG_STOPPED != 0
    }.getOrDefault(false)

    fun isHome(ctx: Context): Boolean =
        ctx.packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName == ctx.packageName

    /** Android's own record of the player's choice of home app (the role), which can outlive the Home button's link to it. */
    fun holdsHomeRole(ctx: Context): Boolean = runCatching {
        ctx.getSystemService(android.app.role.RoleManager::class.java)?.isRoleHeld(android.app.role.RoleManager.ROLE_HOME) == true
    }.getOrDefault(false)

    fun version(ctx: Context): String = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: ""

    fun battery(ctx: Context): Int =
        (ctx.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager)?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
}

// ---------------------------------------------------------------------------------------------

/** One place in the stack: its depth and the screen there. */
data class Entry(val depth: Int, val screen: Screen)

sealed interface Screen {
    data object Timeline : Screen
    data class Games(val system: String? = null, val shelf: String? = null) : Screen
    data class Detail(val key: String) : Screen
    data object ShelfList : Screen
    data class Menu(val id: String) : Screen
    data object Apps : Screen
    data object Idle : Screen
    data object Search : Screen
    data object Scraping : Screen
    data object Accent : Screen
    data class Naming(val title: String) : Screen
    data object Nostos : Screen
}

class AppState(val ctx: Context, private val scope: CoroutineScope) {
    val prefs = Prefs(ctx)

    var hasAccess by mutableStateOf(Storage.hasAccess())
    var romsRoot by mutableStateOf<File?>(null)
    /** Everything the card holds, hidden games included: what the card reading, saves and emulator setup go by. */
    var cardLibrary by mutableStateOf<Map<String, List<Game>>>(emptyMap())
    /** Games marked finished, oldest mark first; and games hidden from every list. Both are files on the card. */
    var finished by mutableStateOf<List<String>>(emptyList())
    var hidden by mutableStateOf<Set<String>>(emptySet())
    private val visibleLibrary by derivedStateOf { Marks.visible(cardLibrary, hidden) }
    /** What the lists, shelves and search show: the card's games less any that are hidden. */
    val library: Map<String, List<Game>> get() = visibleLibrary
    /** On this day: the games released within a few days of today's date, years ago, with their dates. */
    var history by mutableStateOf<List<Pair<Game, String>>>(emptyList())
    var meta by mutableStateOf(MetaStore(File("/nowhere")))
    var metaTick by mutableStateOf(0)        // bumps when scraped data changes, so pages reload it
    var scanned by mutableStateOf(false)
    var query by mutableStateOf("")
    val scrape = ScrapeJob(this, scope)

    var favorites by mutableStateOf(prefs.strings("favorites"))
    var recent by mutableStateOf(prefs.str("recent").split('\n').filter { it.isNotEmpty() })
    var plays by mutableStateOf<Map<String, Played>>(emptyMap())
    var userShelves by mutableStateOf<List<UserShelf>>(emptyList())
    private var playsFrom: String? = null    // the folder the play history was read from
    private var historyRead = false          // the card has been read once since the launcher started, history and all
    private var settleLater: String? = null    // a finished sitting, waiting for the history to be read

    var trueBlack by mutableStateOf(prefs.bool("trueBlack", true))
    var scanlines by mutableStateOf(prefs.bool("scanlines", true))
    var clock24 by mutableStateOf(prefs.bool("clock24", false))
    var swapConfirm by mutableStateOf(prefs.bool("swapConfirm", false))
    /** The key code behind the button press being handled now, for the one page that needs to know which face button it was. */
    var lastPad = 0
    var homeCovers by mutableStateOf(prefs.bool("homeCovers", true))
    var idleMinutes by mutableStateOf(prefs.int("idleMinutes", 3))
    var sortMode by mutableStateOf(prefs.str("sort", Sorting.modes.first()).takeIf { it in Sorting.modes } ?: Sorting.modes.first())
    var refreshRate by mutableStateOf(60f)

    /** The header's battery line, kept up to date by Android's battery broadcast while the launcher is on screen. */
    var batteryText by mutableStateOf("")
    var batteryLevel by mutableStateOf(-1)

    var charging by mutableStateOf(false)
    /** Android's battery saver is on: it slows the processor, and games can stutter. */
    var powerSave by mutableStateOf(false)

    /** The volume bar: the level and the number of steps, and a count that restarts its fade each press. */
    var volumeLevel by mutableStateOf(0)
    var volumeId by mutableStateOf(0)
    var volumeShown by mutableStateOf(false)
    fun showVolume(level: Int) { volumeLevel = level; volumeShown = true; volumeId++ }

    // ---- battery care on the charger (ChargeHold, ChargeCare) ------------------------------------------

    /** Retroid's 80% limit as the setting last read (whoever set it), for the header's "resting". */
    private var chargeHeld = false
    private var chargeLook = 0L

    /** A button or a game: the Nova is in someone's hands, so a held charge carries on to 100%. */
    fun used() {
        ChargeCare.used(ctx)
        val now = SystemClock.uptimeMillis()
        // Retroid's setting itself is looked at, not only what was last decided: a press moments after the hold went on
        // waited for the next minute's look before (seen on a Retroid Pocket Nova).
        if (now - chargeLook > 2000 && (chargeHeld || ChargeCare.held(ctx))) { chargeLook = now; applyCharge() }
    }

    /** Sets Retroid's limit to what the moment calls for: on only while resting on the charger, in Smart. */
    fun applyCharge() {
        ChargeCare.applyLater(ctx) { held ->
            if (held != null) scope.launch(Dispatchers.Main) {
                if (held == chargeHeld) return@launch
                chargeHeld = held
                if (batteryLevel >= 0) batteryText = BatteryEstimate.text(batteryLevel, charging,
                    BatteryEstimate.hoursLeft(BatteryEstimate.decode(prefs.str("battery.samples"))), chargeHeld)
            }
        }
    }

    fun batteryChanged(level: Int, charging: Boolean) {
        if (level < 0) return
        this.charging = charging
        val boot = boots().toLong()
        val now = BatteryEstimate.Sample(boot, android.os.SystemClock.uptimeMillis(), level)
        val kept = BatteryEstimate.add(BatteryEstimate.decode(prefs.str("battery.samples")), now, charging)
        prefs.put("battery.samples", BatteryEstimate.encode(kept))
        batteryLevel = level
        batteryText = BatteryEstimate.text(level, charging, BatteryEstimate.hoursLeft(kept), chargeHeld)
        applyCharge()                                      // unplugged: the limit comes off at once
    }
    var settingsTick by mutableStateOf(0)   // bumps when a stored choice changes, so menus redraw

    /** What this handheld offers: found by reading, so it is safe on any device. */
    var device by mutableStateOf(Device.Found("", false, null, null, null))

    val stack = mutableStateListOf<Screen>(Screen.Timeline)
    var toast by mutableStateOf<String?>(null)
    var toastId by mutableStateOf(0)
    var textEntry by mutableStateOf<TextEntry?>(null)
    val focus = HashMap<String, Int>()      // remembered cursor position per screen

    /** The list a game page was opened from, so L1 and R1 can step through it. */
    var browse: List<String> = emptyList()
    var browseFrom: List<String> = listOf("Timeline")

    /** Text being typed on the naming page, and what to do with it when saved. */
    var draft by mutableStateOf("")
    var naming: ((String) -> Unit)? = null

    val shownCache = ConcurrentHashMap<String, Shown>()
    val homeCache = ConcurrentHashMap<String, List<Pair<File, Float>>>()
    var lastSurprise: String? = null          // the game Surprise me gave last, so the next press gives another

    val current: Screen get() = stack.last()

    /** Today, as a number that changes at midnight; looked at again each time the launcher comes to the front. */
    var day by mutableStateOf(today())
    private fun today(): Int = java.util.Calendar.getInstance().let { it.get(java.util.Calendar.YEAR) * 1000 + it.get(java.util.Calendar.DAY_OF_YEAR) }
    fun lookAtTheDate() { today().let { if (it != day) day = it } }

    private suspend fun historyFor(lib: Map<String, List<Game>>, store: MetaStore): List<Pair<Game, String>> = withContext(Dispatchers.IO) {
        val cal = java.util.Calendar.getInstance()
        val dated = lib.flatMap { (system, games) ->
            val m = runCatching { store.system(system) }.getOrDefault(emptyMap())
            games.mapNotNull { g -> m[g.file]?.date?.takeIf { it.length >= 8 }?.let { g to it } }
        }
        History.week(dated, cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.DAY_OF_MONTH))
    }

    /** The year now, for "31 years ago". */
    val thisYear: Int get() = day / 1000

    /** A mark file's games; empty when there is no file yet; null when it is there but could not be read. */
    private fun readMark(dir: File, name: String): List<String>? {
        val f = File(dir, name)
        if (!f.exists()) return emptyList()
        return runCatching { Marks.parse(f.readText()) }.getOrNull()
    }

    /** The scraped-data folder the marks were last read from in full; marks are only written back to that one. */
    private var marksFrom: String? = null

    private fun marksReady(): Boolean {
        if (marksFrom == meta.dir.path) return true
        say("The card is still being read. Try again in a moment.")
        return false
    }

    /** One writer at a time, so two quick marks are written in the order they were made. */
    private val markWriter = Dispatchers.IO.limitedParallelism(1)
    /** Written beside, then put in place, so a battery that runs out mid-write leaves the old list rather than none. */
    private fun writeMark(name: String, keys: List<String>) {
        val dir = meta.dir
        scope.launch(markWriter) {
            val ok = runCatching {
                dir.mkdirs()
                val part = File(dir, "$name.part")
                part.writeText(Marks.format(keys))
                if (!part.renameTo(File(dir, name))) { File(dir, name).delete(); check(part.renameTo(File(dir, name))) }
            }.isSuccess
            if (!ok) withContext(Dispatchers.Main) { say("That could not be written to the card. Is it in?") }
        }
    }

    fun toggleFinished(g: Game) {
        if (!marksReady()) return
        finished = if (g.key in finished) finished - g.key else finished + g.key
        writeMark(Marks.FINISHED, finished)
    }

    fun hide(g: Game): Boolean {
        if (!marksReady()) return false
        hidden = hidden + g.key
        homeCache.clear()
        writeMark(Marks.HIDDEN, hidden.toList())
        return true
    }

    fun unhide(key: String) {
        if (!marksReady()) return
        hidden = hidden - key
        homeCache.clear()
        writeMark(Marks.HIDDEN, hidden.toList())
    }

    /** The hidden games on this card, for the page that brings them back. */
    fun hiddenGames(): List<Game> = hidden.mapNotNull(::find).sortedBy { it.sort }
    val total: Int get() = library.values.sumOf { it.size }

    private val cacheFile = File(ctx.filesDir, "library.tsv")

    init {
        Ink.Accent = Color(prefs.int("accent", Ink.GoldArgb))
        // The speakers were measured ([SpeakerChain.measured]). A tone saved before then was set on top of the old
        // guess, so the shape goes back to Neutral, once; anything chosen after stays.
        if (prefs.str("eq.measured") != SpeakerChain.MEASURED) {
            prefs.putNow("eq.curve", Eq.format(Eq.presets.getValue("Neutral")))
            prefs.putNow("eq.measured", SpeakerChain.MEASURED)
        }
        // The true picture (2026-11-02): the picture settings chosen before it go back to its defaults, once; anything
        // chosen after stays.
        if (prefs.str("picture.true") != RetroPack.TRUE_PICTURE) {
            prefs.removeNow(listOf("ra.sharp", "ra.saturation", "ra.handheld", "ra.white") + RetroPack.signals.keys.map { "ra.signal.$it" })
            prefs.putNow("picture.true", RetroPack.TRUE_PICTURE)
        }
        SoundService.sync(ctx)
        forgetEarlyGlFallbacks()
        Installs.onResult = ::installAnswered
        // Asking the handheld what it offers is not needed for the first picture: do it off to one side.
        scope.launch {
            device = withContext(Dispatchers.IO) { Device.probe(ctx) }
            watchTv()
            watchPads()
            panel = withContext(Dispatchers.IO) { runCatching { Panel.state() }.getOrNull() }
            // The launcher was chosen as the home app, but after an update Android can forget which screen the Home
            // button opens and ask "Select a Home app" each time (seen on a Retroid Pocket Nova). Put the player's choice back.
            if (Runner.holdsHomeRole(ctx) && !Runner.isHome(ctx) && Bridge.present) withContext(Dispatchers.IO) {
                Bridge.run("cmd package set-home-activity ${ctx.packageName}/.MainActivity")
            }
            // Color calibration is never applied by itself: it is offered on Settings, Display, on the one device and
            // firmware it was measured for.
            if (panel?.calibrated == true) withContext(Dispatchers.IO) { runCatching { Panel.keepTintOff() } }
            // A device that shut down mid-game keeps that game's power and fan: put the resting state back.
            if (!awayForGame && prefs.str("playing").isEmpty()) deviceHome()
            // Battery care: a look once a minute while awake (an alarm covers sleep). The maker's service can be slow to
            // appear after a restart: looked for again each minute until it is there.
            ChargeCare.used(ctx)
            while (true) {
                if (!device.bridge) device = withContext(Dispatchers.IO) { Device.probe(ctx) }
                applyCharge()
                delay(60_000)
            }
        }
        // This week in history: worked out away from the screen whenever the games, their data or the day change.
        scope.launch {
            snapshotFlow { Triple(library, meta to metaTick, day) }.collect { (lib, m, _) -> history = historyFor(lib, m.first) }
        }
        // Show the library as it was last time straight away; the card is read again in the background.
        if (hasAccess) runCatching {
            val root = prefs.str("lastRoot").takeIf { it.isNotEmpty() }?.let(::File)
            val lib = if (root != null && cacheFile.isFile) LibraryCache.parse(cacheFile.readText()) else emptyMap()
            if (root != null && lib.isNotEmpty()) {
                romsRoot = root
                cardLibrary = lib
                meta = MetaStore(Storage.metaDir(ctx, root)) { Storage.esdeDir(ctx, root) }
                scanned = true
                when (prefs.str("startOn", "Timeline")) {
                    "Last system" -> prefs.str("lastSystem").takeIf { it in lib }?.let { stack.add(Screen.Games(system = it)) }
                    "Shelves" -> stack.add(Screen.ShelfList)
                }
            }
        }
    }

    fun push(s: Screen) { stack.add(s) }
    fun pop(): Boolean = if (stack.size > 1) { stack.removeAt(stack.size - 1); true } else false
    fun replace(s: Screen) { stack[stack.size - 1] = s }
    fun home() { while (stack.size > 1) stack.removeAt(stack.size - 1) }
    fun say(msg: String) { toast = msg; toastId++ }
    fun touchSettings() { settingsTick++; refreshSetup() }
    fun metaChanged() {
        shownCache.clear(); homeCache.clear()
        meta.forgetPictures()
        runCatching { ctx.imageLoader.memoryCache?.clear() }       // a picture may have been replaced under the same name
        metaTick++
    }

    /** Where the player is in the stack: how deep, and which screen. Keys and transitions both go by this. */
    val entry: Entry get() = Entry(stack.size, stack.last())

    /** Looks up every game of a list ahead of the highlight, so each one's picture and facts are there when it arrives. */
    fun warmShown(games: List<Game>) {
        val store = meta
        val kind = coverKind
        scope.launch(Dispatchers.IO) {
            for (g in games) {
                if (!isActive || meta !== store) break
                if (shownCache[g.key] == null) shownCache[g.key] = store.shown(g, kind)
            }
        }
    }

    fun setAccent(argb: Int) {
        Ink.Accent = Color(argb)
        prefs.put("accent", argb)
    }

    // ---- RetroArch, set up ahead of time ---------------------------------------------------

    fun look(): Look = Look(
        crt = prefs.str("ra.crt", "GDV Mini Ultra").takeIf { it in RetroPack.crtChoices } ?: "GDV Mini Ultra",
        motion = prefs.str("ra.motion", "Beam simulator").takeIf { it in RetroPack.motionChoices } ?: "Beam simulator",
        motion3d = prefs.str("ra.motion3d", "Beam simulator").takeIf { it in RetroPack.motionChoices } ?: "Beam simulator",
        handheld = prefs.str("ra.handheld", RetroPack.TRUE_LCD).takeIf { it in RetroPack.handheldChoices } ?: RetroPack.TRUE_LCD,
        transparency = prefs.bool("ra.transparency", true),
        integer = prefs.bool("ra.integer", true), sharp = prefs.bool("ra.sharp", false), saturation = prefs.int("ra.saturation", 100),
        white = prefs.str("ra.white", RetroPack.whiteChoices.first()).takeIf { it in RetroPack.whiteChoices } ?: RetroPack.whiteChoices.first(),
        crt3d = prefs.bool("ra.crt3d", true), reconnectByName = padNamesDiffer(),
        signal = RetroPack.signals.keys.mapNotNull { g -> prefs.str("ra.signal.$g").takeIf { it.isNotEmpty() }?.let { g to it } }.toMap(),
        compatible = prefs.str("ra.draw", "Vulkan") == "Compatible", dolphinGl = prefs.str("dol.draw", "Vulkan") == "OpenGL",
        dolphinPad = prefs.bool("dol.pad", true),
        resume = prefs.bool("ra.resume", true), sramSeconds = prefs.int("ra.sram", 10),
        rewind = prefs.bool("ra.rewind", false),
        lag = prefs.str("ra.lag", if (prefs.bool("ra.runahead", false)) "Run-ahead" else "Preemptive frames").takeIf { it in RetroPack.lagChoices } ?: "Preemptive frames",
        hz = gameHz(),
        tv = tvDisplays().isNotEmpty(),
        tvLines = tvDisplays().maxOfOrNull { it.mode.physicalHeight } ?: 0,
        panel = panelSize(),
        upright = prefs.str("ra.upright", "Right side down").takeIf { it in RetroPack.uprightChoices } ?: "Right side down",
        speakers = if (prefs.bool("eq.retroarch", false)) Eq.parse(prefs.str("eq.curve")) else null,
        achievements = if (prefs.bool("ra.cheevos", false) && prefs.str("ra.cheevosUser").isNotBlank())
            Achievements(prefs.str("ra.cheevosUser").trim(), prefs.str("ra.cheevosPass"), prefs.bool("ra.hardcore", false), prefs.bool("ra.cheevosFresh", false)) else null,
        m1 = prefs.int("m1.code", 0), m2 = prefs.int("m2.code", 0),
        extras = Catalog.systems.mapNotNull { s ->
            val usual = Hotkeys.usual(s.id)
            val mine = (prefs.str("m1." + s.id).takeIf { it in Hotkeys.actions } ?: usual.first) to (prefs.str("m2." + s.id).takeIf { it in Hotkeys.actions } ?: usual.second)
            if (mine == usual) null else s.id to mine
        }.toMap(),
        lighter = prefs.strings("light.ever").filter { !it.startsWith("gc/") && !it.startsWith("wii/") }
            .associate { key -> (key.substringBefore('/') + "/" + Titles.stem(key.substringAfter('/'))) to prefs.bool("light:$key", false) },
        // Dolphin: a lighter game's line is set; one switched off is taken out once ("light.undo"), and after that the
        // file is left alone, so a resolution chosen inside Dolphin for that game later is never undone.
        lighterCodes = prefs.strings("light.ever").filter { it.startsWith("gc/") || it.startsWith("wii/") }
            .filter { prefs.bool("light:$it", false) || it in prefs.strings("light.undo") }
            .mapNotNull { key -> find(key)?.let(::codeOf)?.let { it to prefs.bool("light:$key", false) } }.toMap(),
        dolphinGlCodes = prefs.strings("dolgl.ever").filter { drawsWithGl(it) || it in prefs.strings("dolgl.undo") }
            .mapNotNull { key -> find(key)?.let(::codeOf)?.let { it to drawsWithGl(key) } }
            .groupBy({ it.first }, { it.second }).mapValues { (_, v) -> v.any { it } },
   )

    /** True unless two of the controllers connected now have the same name (RetroArch would mix them up on reconnecting). */
    private fun padNamesDiffer(): Boolean = runCatching {
        val im = ctx.getSystemService(android.hardware.input.InputManager::class.java) ?: return@runCatching true
        val names = im.inputDeviceIds.toList().mapNotNull { id -> im.getInputDevice(id) }
            .filter { d -> !d.isVirtual && (d.sources and android.view.InputDevice.SOURCE_GAMEPAD) == android.view.InputDevice.SOURCE_GAMEPAD }
            .distinctBy { d -> d.descriptor }
            .map { d -> d.name }
        names.size == names.toSet().size
    }.getOrDefault(true)

    /** Settings, Display: Android's wallpaper (home and lock screen), which shows behind Android's own screens, made plain black. */
    fun blackWallpaper() {
        val ok = runCatching {
            val black = android.graphics.Bitmap.createBitmap(64, 64, android.graphics.Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.BLACK) }
            android.app.WallpaperManager.getInstance(ctx).setBitmap(black, null, false,
                android.app.WallpaperManager.FLAG_SYSTEM or android.app.WallpaperManager.FLAG_LOCK)
        }.isSuccess
        say(if (ok) "The wallpaper is plain black now, at home and on the lock screen." else "Android did not take the wallpaper.")
    }

    // ---- GameCube and Wii games that will not start with Vulkan ---------------------------------
    // "trying": switched to OpenGL after closing at once with Vulkan, not yet seen to start; "kept": it did, and stays.

    /**
     * Once: games moved to OpenGL by the fallback before the cause of the Vulkan crash was found (GPU texture decoding,
     * now always off) go back to Vulkan, which runs them. Their OpenGL line is taken out of Dolphin's settings next time.
     */
    private fun forgetEarlyGlFallbacks() {
        if (prefs.bool("dolgl.reset1", false)) return
        // Written straight to the saved choices: this runs while the launcher is being built, before the settings exist.
        for (key in prefs.strings("dolgl.ever")) if (drawsWithGl(key)) {
            prefs.put("dolgl:$key", "")
            prefs.putStrings("dolgl.undo", prefs.strings("dolgl.undo") + key)
        }
        prefs.put("dolgl.reset1", true)
    }

    private fun glState(key: String): String = prefs.str("dolgl:$key")
    fun drawsWithGl(key: String): Boolean = glState(key) == "trying" || glState(key) == "kept"

    private fun setGl(key: String, state: String) {
        prefs.put("dolgl:$key", state)
        prefs.putStrings("dolgl.ever", prefs.strings("dolgl.ever") + key)
        prefs.putStrings("dolgl.undo", if (state.isEmpty()) prefs.strings("dolgl.undo") + key else prefs.strings("dolgl.undo") - key)
        touchSettings()
    }

    /** From a game's options: this one game with OpenGL, or back to the system's way. */
    fun toggleGl(g: Game) = setGl(g.key, if (drawsWithGl(g.key)) "" else "kept")

    /**
     * Back from a GameCube or Wii game. Within moments of starting, without the Home button, it did not start: with
     * Vulkan, that game is drawn with OpenGL from its next start; already trying OpenGL, it goes back. Running longer
     * with OpenGL, it stays so. True when the player has been told, so nothing else need be said.
     */
    private fun dolphinOutcome(playing: String, viaHome: Boolean): Boolean {
        val parts = playing.split('\n')
        val key = parts[0]
        if (key.substringBefore('/') !in setOf("gc", "wii")) return false
        if (look().dolphinGl) return false                  // everything is drawn with OpenGL already: nothing to switch
        val ran = SystemClock.uptimeMillis() - (parts.getOrNull(2)?.toLongOrNull() ?: return false)
        val failed = !viaHome && ran in 0..Launch.DOLPHIN_FAILED_MS && !prefs.bool("playing.back", false)
        val gl = prefs.str("playing.gl") == "1"
        val g = find(key) ?: return false
        return when {
            failed && !gl && dolphinGranted() && codeOf(g) != null -> {
                setGl(key, "trying")
                takeBack(key)
                say("${g.title} closed as soon as it opened. It starts with OpenGL instead of Vulkan from now on, and stays that way once it runs.")
                true
            }
            failed && gl && glState(key) == "trying" -> {
                setGl(key, "")
                takeBack(key)
                say("${g.title} closed at once with OpenGL too, so it is back on Vulkan. Try it once more, or Run lighter from its options.")
                true
            }
            // Kept only once it has really run: back after longer than that, however it was left.
            ran > Launch.DOLPHIN_FAILED_MS && gl && glState(key) == "trying" -> { setGl(key, "kept"); false }
            else -> false
        }
    }

    fun runsLighter(g: Game): Boolean = prefs.bool("light:" + g.key, false)

    /** Whether Run lighter can do anything for this game where it opens now: RetroArch with the launcher's setup, or
     *  Dolphin with its folder shared and the disc's ID known. */
    fun canRunLighter(g: Game): Boolean {
        if (g.system !in RetroPack.canRunLighter) return false
        val emu = emulatorFor(g)
        return if (emu == "dolphin") dolphinGranted() && codeOf(g) != null else emu.startsWith("ra:") && retroManaged
    }

    /** One game at its console's own resolution, for a game that stutters. Written into the emulator's settings before its next start. */
    fun toggleLighter(g: Game) {
        val on = !runsLighter(g)
        prefs.put("light:" + g.key, on)
        prefs.putStrings("light.ever", prefs.strings("light.ever") + g.key)
        prefs.putStrings("light.undo", if (on) prefs.strings("light.undo") - g.key else prefs.strings("light.undo") + g.key)
        touchSettings()
    }

    /** What M1 and M2 do on a system: the player's choice if there is one, else the usual. */
    fun extraButtons(system: String): Pair<String, String> = look().extras[system] ?: Hotkeys.usual(system)

    /** Set while the Controls page is waiting for M1 or M2 to be pressed; the next button press is recorded as that button. */
    var learning by mutableStateOf<String?>(null)

    fun learned(code: Int) {
        val which = learning ?: return
        learning = null
        prefs.put("$which.code", code)
        say("${which.uppercase()} recorded as button $code.")
        touchSettings()
    }

    // ---- saves: a copy on the card, earlier versions, and each game's own page ----------------

    private var backingUp = false
    private var backedUpAtStart = false

    /** Saves are being put in place where an emulator keeps them: no game is started until that is done, or it could write over them. */
    @Volatile private var restoring = false

    private fun saveCard(): File? = romsRoot?.takeIf { it.isDirectory }?.let { File(it, "_timeline/saves") }      // none while the card is away

    // ---- Nintendo DS on two screens ---------------------------------------------------------------
    // With a second display connected (the dual-screen attachment), melonDS on its own puts the top screen there and
    // the touch screen on the device's panel, five times its size. RetroArch draws on one display only.

    /**
     * A second display is connected and on: the dual-screen attachment, or a television. Only one that is showing
     * something counts, and not one an app made for itself.
     */
    fun secondScreen(): Boolean = runCatching {
        val dm = ctx.getSystemService(android.hardware.display.DisplayManager::class.java) ?: return@runCatching false
        dm.getDisplays(android.hardware.display.DisplayManager.DISPLAY_CATEGORY_PRESENTATION).any {
            it.state == android.view.Display.STATE_ON && (it.flags and android.view.Display.FLAG_PRIVATE) == 0
        }
    }.getOrDefault(false)

    /** A DS game opens in melonDS, on both screens: a second display is there, melonDS is installed, and RetroArch is not holding this game. */
    fun twoScreens(g: Game): Boolean = g.system == "nds" && prefs.bool("nds.two", true) && stillOpen != g.key &&
        "melonds" in emulatorChoices(g) && Runner.ready(ctx, "melonds") && secondScreen()

    /**
     * The emulator a DS game is open in, if it is. The game goes back there whatever else has changed (the second
     * screen taken off, a choice made since): started in the other emulator as well, each would write its own save.
     */
    private fun dsHolder(g: Game, options: List<String>): String? {
        if (g.system != "nds") return null
        // An emulator of its own is recorded for certain when it is left holding a game, so it is asked first.
        heldOpen.entries.firstOrNull { it.value.key == g.key }?.key?.let { pkg ->
            options.firstOrNull { id -> Emulators.get(id)?.components?.any { it.substringBefore('/') == pkg } == true }?.let { return it }
        }
        if (stillOpen == g.key) {
            val last = prefs.str("ra.last").split('\n')
            return last.getOrNull(1)?.takeIf { last[0] == g.key && it in options } ?: options.firstOrNull { it.startsWith("ra:") }
        }
        return null
    }

    private fun melonHome(): SaveHome? = romsRoot?.let { FileHome("melonDS", it, listOf("nds")) { name -> name.endsWith(".sav") } }

    /** A save as it stands: its length and checksum, or "" when there is none (or it is empty, as melonDS leaves one at first). */
    private fun saveMark(f: File): String = if (!f.isFile || f.length() == 0L) "" else {
        val crc = java.util.zip.CRC32()
        var blank = true                    // every byte 0xFF or every byte 0x00: a cartridge with nothing saved on it yet
        var first = -1
        f.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf); if (n < 0) break
                crc.update(buf, 0, n)
                if (blank) for (i in 0 until n) { val b = buf[i].toInt() and 0xFF; if (first < 0) first = b; if (b != first || (b != 0 && b != 0xFF)) { blank = false; break } }
            }
        }
        if (blank) "" else f.length().toString() + ":" + java.lang.Long.toHexString(crc.value)
    }

    /**
     * A DS game's save is the same bytes in both emulators: RetroArch's melonDS DS keeps it as "<game>.srm" in its saves
     * folder, melonDS on its own as "<game>.sav" beside the game on the card. Just before the game starts in either,
     * the save the other one has played on is put where this one reads it.
     *
     * Which one has played on is told by what the two held the last time they were the same, not by the files' times:
     * a clock set back, or a copy that did not keep times, would otherwise make the older save look newer. Only when
     * both have changed since (or they have never been compared) do the times decide. What a copy replaces is first
     * filed on the card as an earlier version; if that cannot be done nothing is written. Nothing is touched while
     * either emulator is holding this game. Returns something to tell the player, or null.
     */
    /** What a DS save sync did: something to tell the player, and whether RetroArch's save was replaced. */
    private class DsSync(val message: String? = null, val intoRetro: Boolean = false)

    private fun syncDsSave(g: Game, toMelon: Boolean): DsSync {
        val none = DsSync()
        if (isOpen(g.key)) return none
        val card = saveCard() ?: return none
        val root = romsRoot ?: return none
        val stem = Titles.stem(g.file)
        val retroRel = "saves/melonDS DS/$stem.srm"
        val ra = File(Runner.RETRO, retroRel)
        val sav = File(File(g.path).parentFile ?: return none, "$stem.sav")
        val savRel = sav.relativeToOrNull(root)?.invariantSeparatorsPath?.takeIf { it.startsWith("nds/") } ?: return none
        val raMark = saveMark(ra)
        val savMark = saveMark(sav)
        val seen = "nds.sync:" + g.key
        if (raMark == savMark) { if (raMark.isNotEmpty() && prefs.str(seen) != raMark) prefs.put(seen, raMark); return none }
        val from = if (toMelon) ra else sav
        val to = if (toMelon) sav else ra
        val fromMark = if (toMelon) raMark else savMark
        val toMark = if (toMelon) savMark else raMark
        if (fromMark.isEmpty()) return none                         // the other has nothing to bring across
        val last = prefs.str(seen)
        // Two different saves never compared before are as much a surprise as two that have both moved on: told either way.
        val both = DsSaves.bothMoved(fromMark, toMark, last) || (last.isEmpty() && toMark.isNotEmpty())
        if (!DsSaves.bring(fromMark, toMark, last, from.lastModified(), to.lastModified())) return none
        val here = if (toMelon) "melonDS" else "RetroArch"
        val there = if (toMelon) "RetroArch" else "melonDS"
        if (to.isFile && !SaveSync.keep(to, card, here, if (toMelon) savRel else retroRel))
            return DsSync("The card is full, so ${g.title} starts on the save $here already had, not the newer one from $there.")
        val home = if (toMelon) melonHome() ?: return none else FileHome("RetroArch", File(Runner.RETRO), listOf("saves"))
        if (!home.put(if (toMelon) savRel else retroRel, from, from.lastModified()))
            return DsSync("${g.title}'s newer save from $there could not be put in place, so it starts on the save $here already had.")
        prefs.put(seen, fromMark)
        // A place taken back by hand in RetroArch is from before this progress, and loading it would undo it.
        if (!toMelon) prefs.put("place.keep:" + g.key, false)
        val told = if (both) "${g.title} had different saves in RetroArch and melonDS. The newer one is used; the other is kept on the card, in _timeline/saves/earlier." else null
        return DsSync(told, intoRetro = !toMelon)
    }

    private fun retroHome(states: Boolean = true): SaveHome =
        FileHome("RetroArch", File(Runner.RETRO), if (states) listOf("saves", "states") else listOf("saves"))

    /** Dolphin's saves, when the launcher has been let into its folder. */
    private fun dolphinHome(): SaveHome? = if (!dolphinGranted()) null else
        DocHome("Dolphin", ctx, Uri.parse(dolphinTree), listOf("GC", "Wii/title", "Wii/shared2/menu/FaceLib"), Saves::dolphinSave)

    /** When saves were last copied to the card, and how that went. Shown on the Saves page. */
    val savedAt: Long get() = prefs.long("saves.at", 0L)

    /**
     * Copies anything new to the card: RetroArch's saves and places, Dolphin's memory cards and Wii saves, and any
     * backup file ARMSX2 has been asked to make. One at a time; a second request while one runs is dropped.
     */
    fun backUpSaves(announce: Boolean = false, then: () -> Unit = {}) {
        val card = saveCard() ?: run { if (announce) say("No card with a games folder was found."); return }
        if (backingUp) { if (announce) say("Already copying."); return }       // two at once would write the same half-made files
        forgetStopped()
        backingUp = true
        // Places and save states are far larger than saves. With the card nearly full they wait, so the saves themselves always fit.
        val room = card.usableSpace > Saves.ROOM_FOR_PLACES
        val states = prefs.bool("saves.states", true) && room
        // Saves are only ever brought back into an emulator that has no game open: an open game would write its own over them.
        val retroFree = stillOpen.isEmpty()
        val dolphinFree = heldOpen.keys.none { it.contains("dolphin") }
        // A new or wiped device with this card in it: nothing here can be newer than the card's copy, so the saves
        // simply come back, before a game makes a fresh one in their place. Looked for once each time the launcher
        // starts (and once Dolphin's folder has been shared), and no game is started while it happens.
        val tryRetro = !triedRetro && retroFree
        triedRetro = true
        val tryDolphin = !triedDolphin && dolphinFree && dolphinGranted()
        if (tryDolphin) triedDolphin = true
        // Held only for the moments it takes to look into RetroArch's own folders and copy into them. Dolphin answers
        // through another app, which can be slow or silent, so it has a hold of its own (Dolphin games only) with a limit.
        if (tryRetro) restoring = true
        if (tryDolphin) restoringDolphin = true
        scope.launch {
            var back = 0
            val n = try {
                withContext(Dispatchers.IO) {
                    SaveSync.failed = 0
                    SaveSync.unread = 0
                    val retro = retroHome(states)
                    // "None at all" has to be known, not guessed: a folder that could not be read is not an empty one.
                    if (tryRetro && retroHome(false).list()?.none { Saves.kind(it.name) == Saves.Kind.SAVE } == true) back += SaveSync.restore(retroHome(), card)
                    restoring = false
                    learnCodes()
                    // RetroArch's copy first: nothing of Dolphin's can hold it up.
                    var copied = SaveSync.backUp(retro, card) + sweepPs2(card) + (melonHome()?.let { SaveSync.backUp(it, card) } ?: 0)
                    // The PS2 memory cards and the Xbox hard disk, out of their emulators' own folders, while each is closed.
                    // The hard disk is hundreds of MB: like places, it waits when the card is nearly full.
                    if (device.bridge || Bridge.present) for (src in listOf(RootMirror.ps2, RootMirror.xbox)) {
                        if (src == RootMirror.xbox && !room) continue
                        if (Proc.running(ctx, src.pkg) != false || !RootMirror.sync(ctx, src)) continue
                        copied += SaveSync.backUp(FileHome(src.home, RootMirror.dir(ctx, src), listOf(src.top)), card)
                    }
                    val dolphin = dolphinHome()
                    // Whether Dolphin was up before its folder was asked for: asking wakes it.
                    val dolphinUp = if (dolphin != null) Proc.running(ctx, DolphinPack.PACKAGE) else null
                    if (dolphin != null && dolphinIdle.compareAndSet(true, false)) {
                        // Dolphin is asked what it holds, and given eight seconds to say. With no answer nothing of
                        // Dolphin's is touched this time. With one, the work that follows is seen through to its end.
                        val asked = scope.async(Dispatchers.IO) { runCatching { dolphin.list() }.getOrNull() }
                        val holds = withTimeoutOrNull(8000) { asked.await() }
                        if (holds == null) {
                            SaveSync.unread++
                            scope.launch(Dispatchers.IO) { asked.join(); dolphinIdle.set(true) }
                        } else try {
                            val playing = prefs.str("playing.in").substringBefore('\n').contains("dolphin")
                            if (tryDolphin && holds.isEmpty() && !playing) back += SaveSync.restore(dolphin, card)
                            restoringDolphin = false
                            copied += SaveSync.backUp(dolphin, card)
                        } finally { dolphinIdle.set(true) }
                        lullDolphin(dolphinUp)
                    }
                    restoringDolphin = false
                    copied
                }
            } finally { backingUp = false; restoring = false; restoringDolphin = false }
            val failed = SaveSync.failed
            prefs.put("saves.at", System.currentTimeMillis())
            prefs.put("saves.failed", failed)
            prefs.put("saves.room", room)
            when {
                back > 0 -> say(if (back == 1) "1 save brought back from the card." else "$back saves brought back from the card.")
                failed > 0 -> say(if (failed == 1) "1 save could not be copied to the card. Is the card full?" else "$failed saves could not be copied to the card. Is the card full?")
                announce && SaveSync.unread > 0 -> say("Some saves could not be read just now" + (if (n > 0) " ($n others were copied)" else "") + ". Try again in a moment.")
                announce -> say(if (n == 0) "The card already has every save." else if (n == 1) "1 save copied to the card." else "$n saves copied to the card.")
            }
            refreshSaveIndex()
            then()
        }
    }

    private var triedRetro = false
    private var triedDolphin = false
    @Volatile private var restoringDolphin = false
    private val dolphinIdle = java.util.concurrent.atomic.AtomicBoolean(true)

    fun restoreSaves() {
        val card = saveCard() ?: return
        if (backingUp) { say("Still copying. Try again in a moment."); return }
        forgetStopped()
        // Each emulator on its own: a game open in one is no reason to leave the other's saves on the card.
        val inRetro = stillOpen.takeIf { it.isNotEmpty() }?.let { find(it)?.title ?: "A game" }
        val inDolphin = heldOpen.entries.firstOrNull { it.key.contains("dolphin") }?.let { find(it.value.key)?.title ?: "A game" }
        val dolphin = dolphinHome()
        if (inRetro != null && (dolphin == null || inDolphin != null)) {
            say("$inRetro is still open. Close it first, so it does not write over what is brought back.")
            return
        }
        backingUp = true
        restoring = true
        scope.launch {
            val n = try {
                withContext(Dispatchers.IO) {
                    SaveSync.failed = 0
                    SaveSync.unread = 0
                    (if (inRetro == null) SaveSync.restore(retroHome(), card) else 0) + (if (inDolphin == null && dolphin != null) SaveSync.restore(dolphin, card) else 0)
                }
            } finally { backingUp = false; restoring = false }
            val left = when {
                inRetro != null -> " $inRetro is still open, so RetroArch's saves were left alone."
                inDolphin != null && dolphin != null -> " $inDolphin is still open in Dolphin, so GameCube and Wii saves were left alone."
                else -> ""
            }
            val failed = SaveSync.failed.let { if (it == 0) "" else if (it == 1) " 1 could not be written." else " $it could not be written." } +
                if (SaveSync.unread > 0) " Some saves could not be read just now: try again in a moment." else ""
            say((if (n == 0) "Nothing on the card is newer than what is here." else if (n == 1) "1 save brought back from the card." else "$n saves brought back from the card.") + failed + left)
            refreshSaveIndex()
        }
    }

    /**
     * ARMSX2 keeps PlayStation 2 memory cards in its own storage, where no other app can read them, but it can write
     * a backup file wherever it is told. The newest two left in Downloads or Documents are copied to the card.
     */
    private fun sweepPs2(card: File): Int = runCatching {
        val to = File(card, "ARMSX2")
        fun ours(f: File) = f.isFile && f.name.startsWith("ARMSX2-backup", ignoreCase = true) && !f.name.endsWith(".part")
        val newest = listOf("Download", "Documents").flatMap { File(Storage.internal(ctx), it).listFiles()?.filter(::ours).orEmpty() }
            .sortedByDescending { it.lastModified() }.take(2)
        var n = 0
        for (f in newest) {
            val dst = File(to, f.name)
            if (dst.isFile && dst.length() == f.length() && kotlin.math.abs(dst.lastModified() - f.lastModified()) <= 2500) continue
            to.mkdirs()
            val part = File(dst.path + ".part")
            f.copyTo(part, overwrite = true)
            if (part.length() == f.length() && (part.renameTo(dst) || (dst.delete() && part.renameTo(dst)))) { dst.setLastModified(f.lastModified()); n++ } else part.delete()
        }
        // Only ever copies this launcher made: the newest two stay.
        if (newest.isNotEmpty()) to.listFiles()?.filter(::ours)?.sortedByDescending { it.lastModified() }?.drop(2)?.forEach { it.delete() }
        n
    }.getOrDefault(0)

    /** When the newest ARMSX2 backup file on the card was made; 0 when there is none. Looked up away from the screen. */
    var ps2BackedUp by mutableStateOf(0L)
        private set

    // GameCube and Wii saves are filed by the game's code, which is read once from each disc and remembered.
    // A Dreamcast game's memory card is named by its disc's product number, learned from RetroArch's log.
    private val codes = ConcurrentHashMap<String, String>()

    fun codeOf(g: Game): String? = codes[g.key] ?: prefs.str("code:" + g.key).takeIf { it.isNotEmpty() }?.also { codes[g.key] = it }

    private fun learnCodes() {
        for (system in listOf("gc", "wii")) for (g in cardLibrary[system].orEmpty()) {
            if (codeOf(g) != null) continue
            val disc = File(g.discs.firstOrNull() ?: g.path)
            val at = Saves.codeOffset(disc.name) ?: continue
            val code = runCatching { java.io.RandomAccessFile(disc, "r").use { f -> f.seek(at); ByteArray(6).also { f.readFully(it) } } }.getOrNull()?.let(Saves::code) ?: continue
            codes[g.key] = code
            prefs.put("code:" + g.key, code)
        }
    }

    /** What the launcher knows of one game's saves. [where] is "RetroArch", "Dolphin", or empty when they are out of reach. */
    class GameSaves(
        val where: String,
        val own: List<SaveEntry> = emptyList(),      // the game's own save and, where it has one, its clock
        val clock: Boolean = false,
        val place: SaveEntry? = null,
        val aside: SaveEntry? = null,
        val slots: Int = 0,
        val onCard: Long = 0L,
        val moments: List<Long> = emptyList(),       // when each earlier version was written, newest first
   ) {
        val newest: Long get() = maxOf(own.maxOfOrNull { it.modified } ?: 0L, place?.modified ?: 0L)
    }

    private fun own(kind: Saves.Kind) = kind == Saves.Kind.SAVE || kind == Saves.Kind.CLOCK

    /** Picks out a RetroArch game's files, by name or by a path ending in the name: those named after the game's file, and a Dreamcast game's memory card. */
    private fun owner(g: Game, emulatorId: String): (String) -> Boolean {
        val stem = saveStem(g, emulatorId)
        val disc = if (g.system == "dreamcast") codeOf(g) else null
        return { rel -> rel.substringAfterLast('/').let { name -> Saves.belongs(name, stem) || (disc != null && Saves.belongs(name, disc)) } }
    }

    /** The name RetroArch files a game's saves under: the name of the file it is handed, without its ending. */
    private fun saveStem(g: Game, emulatorId: String): String = File(contentFor(g, emulatorId, null)).nameWithoutExtension

    /** Reads folders on the device and on the card: never from the screen's own thread. */
    private fun savesOf(g: Game): GameSaves {
        // A DS game's save is kept the same in RetroArch and melonDS, so its page shows RetroArch's, wherever it opens.
        val emulatorId = emulatorFor(g).let { if (g.system == "nds" && it == "melonds") "ra:melondsds" else it }
        val card = saveCard()
        val core = Saves.coreFolder(emulatorId)
        if (core != null) {
            val owns = owner(g, emulatorId)
            fun here(top: String) = File("${Runner.RETRO}/$top/$core").let { dir ->
                if (!dir.isDirectory) emptyList() else dir.walkTopDown().maxDepth(4).filter { it.isFile && owns(it.name) }
                    .map { SaveEntry(top + "/" + core + "/" + it.relativeTo(dir).invariantSeparatorsPath, it.length(), it.lastModified()) }.toList()
            }
            val saves = here("saves").filter { own(Saves.kind(it.name)) }
            val states = here("states")
            val versions = card?.let { SaveSync.versions(it, "RetroArch", "saves/$core", owns) }.orEmpty()
            val copies = card?.let { SaveSync.onCard(it, "RetroArch", "saves/$core", owns) + SaveSync.onCard(it, "RetroArch", "states/$core", owns) }.orEmpty()
            return GameSaves("RetroArch", saves, saves.any { Saves.kind(it.name) == Saves.Kind.CLOCK },
                states.firstOrNull { Saves.kind(it.name) == Saves.Kind.PLACE }, states.firstOrNull { Saves.kind(it.name) == Saves.Kind.ASIDE },
                states.count { Saves.kind(it.name) == Saves.Kind.SLOT }, copies.maxOfOrNull { it.modified } ?: 0L,
                versions.filter { own(Saves.kind(it.rel.substringAfterLast('/'))) }.map { it.at }.distinct().sortedDescending())
        }
        if (emulatorId == "dolphin") {
            val code = codeOf(g) ?: return GameSaves("Dolphin")
            val owns = { rel: String -> Saves.dolphinOwns(rel, code) }
            // Read from the card's copy, made after every game: asking Dolphin itself is slow.
            val copies = card?.let { SaveSync.onCard(it, "Dolphin", "", owns) }.orEmpty()
            val versions = card?.let { SaveSync.versions(it, "Dolphin", "", owns) }.orEmpty()
            return GameSaves("Dolphin", copies, onCard = copies.maxOfOrNull { it.modified } ?: 0L, moments = versions.map { it.at }.distinct().sortedDescending())
        }
        return GameSaves("")
    }

    /** What was last found for each game whose Saves page has been opened. The page shows this and asks for a fresh look. */
    var saveDetails by mutableStateOf<Map<String, GameSaves>>(emptyMap())
        private set

    /** Looks at one game's saves away from the screen, then lets its page know. */
    fun lookAtSaves(g: Game) {
        scope.launch {
            // If the folders cannot be read just now the page still gets an answer, rather than saying it is looking for ever.
            val found = withContext(Dispatchers.IO) { runCatching { savesOf(g) }.getOrNull() }
                ?: GameSaves(when { Saves.coreFolder(emulatorFor(g)) != null -> "RetroArch"; emulatorFor(g) == "dolphin" -> "Dolphin"; else -> "" })
            saveDetails = saveDetails + (g.key to found)
            touchSettings()
        }
    }

    /** Games that have saves, newest first, for the Saves page. Put together away from the screen, since it reads folders. */
    var saveIndex by mutableStateOf<List<Pair<String, Long>>>(emptyList())
        private set

    fun refreshSaveIndex() {
        scope.launch {
            val (index, ps2) = withContext(Dispatchers.IO) {
                val index = runCatching {
                    // One look into each core's folders, then every game is matched against what was found there.
                    val seen = HashMap<String, List<Pair<String, Long>>>()
                    fun files(core: String) = seen.getOrPut(core) {
                        listOf("saves", "states").flatMap { top ->
                            File("${Runner.RETRO}/$top/$core").walkTopDown().maxDepth(4).filter { it.isFile }.map { it.name to it.lastModified() }.toList()
                        }
                    }
                    val dolphin = saveCard()?.let { SaveSync.onCard(it, "Dolphin", "") { true } }.orEmpty()
                    cardLibrary.values.flatten().mapNotNull { g ->
                        val emulatorId = emulatorFor(g)
                        val core = Saves.coreFolder(emulatorId)
                        val newest = when {
                            core != null -> owner(g, emulatorId).let { owns ->
                                files(core).filter { (name, _) -> owns(name) && (own(Saves.kind(name)) || Saves.kind(name) == Saves.Kind.PLACE) }.maxOfOrNull { it.second }
                            }
                            emulatorId == "dolphin" -> codeOf(g)?.let { code -> dolphin.filter { Saves.dolphinOwns(it.rel, code) }.maxOfOrNull { it.modified } }
                            else -> null
                        }
                        newest?.takeIf { it > 0L }?.let { g.key to it }
                    }.sortedByDescending { it.second }
                }.getOrDefault(emptyList())
                val ps2 = runCatching {
                    saveCard()?.let { File(it, "ARMSX2").listFiles() }?.filter { it.isFile && it.name.startsWith("ARMSX2-backup", ignoreCase = true) }?.maxOfOrNull { it.lastModified() } ?: 0L
                }.getOrDefault(0L)
                index to ps2
            }
            saveIndex = index
            ps2BackedUp = ps2
            // Any game page that is being looked at is brought up to date as well.
            (current as? Screen.Menu)?.id?.takeIf { it.startsWith("saves:") }?.let { find(it.removePrefix("saves:")) }?.let { lookAtSaves(it) }
            touchSettings()
        }
    }

    /** Whether a game's saves may be changed now: not while the emulator still has it, or anything, in memory. */
    private fun savesFree(g: Game, where: String): Boolean = forgetStopped().let { free(g, where) }

    private fun free(g: Game, where: String): Boolean = when {
        isOpen(g.key) -> { say("${g.title} is still open. Go back to it and leave with START and SELECT first, or it will write its own save over this."); false }
        where == "Dolphin" && heldOpen.keys.any { it.contains("dolphin") } -> { say("Dolphin still has a game open. Close that first."); false }
        backingUp -> { say("Still copying. Try again in a moment."); false }
        else -> true
    }

    /** The place a RetroArch game was left at, as a file. */
    private fun placeOf(g: Game, emulatorId: String = emulatorFor(g)): File? {
        val core = Saves.coreFolder(emulatorId) ?: return null
        return File("${Runner.RETRO}/states/$core/${saveStem(g, emulatorId)}.state.auto")
    }

    /**
     * Sets a place aside: renamed, never deleted, so the game next starts from its own save. A place that was already
     * set aside is not written over: it is filed on the card with the earlier versions first, or kept beside the
     * other under its own date when there is no card. The card's newest copy of the place is filed too, so that
     * "Bring saves back" does not put a place the player set aside back in use.
     */
    private fun setAside(place: File): Boolean = runCatching {
        if (!place.isFile) return false
        val aside = File(place.path + ".earlier")
        val card = saveCard()
        val rel = place.relativeTo(File(Runner.RETRO)).invariantSeparatorsPath
        if (aside.isFile) {
            val filed = card != null && SaveSync.keep(aside, card, "RetroArch", "$rel.earlier")
            if (filed) aside.delete() else if (!aside.renameTo(File(aside.path + "." + Saves.stamp(aside.lastModified())))) return false
        }
        if (!place.renameTo(aside)) return false
        card?.let { SaveSync.retire(it, "RetroArch", rel) }
        true
    }.getOrDefault(false)

    /**
     * RetroArch saves your place when a game is closed properly, and loads it the next time. If the game was last
     * closed any other way (another game was started over it, the battery ran out), the place on file is from an
     * earlier sitting. When the game's own save has moved on since, loading that place would put an old moment over
     * newer progress (for Dreamcast it would wind the memory card itself back), so the place is set aside and the
     * game starts from its own save instead. Not from the screen's thread.
     */
    private fun setAsideIfStale(g: Game, emulatorId: String) {
        runCatching {
            val core = Saves.coreFolder(emulatorId) ?: return
            val place = placeOf(g, emulatorId)?.takeIf { it.isFile } ?: return
            val owns = owner(g, emulatorId)
            val own = File("${Runner.RETRO}/saves/$core").walkTopDown().maxDepth(4)
                .filter { it.isFile && owns(it.name) && Saves.kind(it.name) == Saves.Kind.SAVE }.map { it.lastModified() }.toList()
            if (Saves.placeIsStale(place.lastModified(), plays[g.key]?.last ?: 0L, own)) setAside(place)
        }
    }

    /** Sets the place aside, so the game next starts from its own save. Nothing is lost: "Go back to the place set aside" undoes it. */
    fun startFromOwnSave(g: Game) {
        if (!savesFree(g, "RetroArch")) return
        val place = placeOf(g)?.takeIf { it.isFile } ?: run { say("There is no place kept for it."); return }
        scope.launch {
            val ok = withContext(Dispatchers.IO) { setAside(place) }
            prefs.put("place.keep:" + g.key, false)
            say(if (ok) "Next time it starts from its own save. The place is kept aside, not lost." else "The place could not be moved.")
            lookAtSaves(g)
        }
    }

    /**
     * Puts [aside] in use as the place, and the place that was in use (if any) aside in its stead. Each keeps the time
     * it was written, which is how a place is known to belong with a save. The card's newest copy of the place is
     * filed with the earlier versions, so the card takes the one now in use at the next copy even though it is older.
     */
    private fun swapPlaces(place: File, aside: File): Boolean = runCatching {
        val ok = if (!place.isFile) aside.renameTo(place) else {
            val hold = File(place.path + ".swap")
            place.renameTo(hold) && aside.renameTo(place) && hold.renameTo(aside)
        }
        if (ok) saveCard()?.let { card ->
            val rel = place.relativeTo(File(Runner.RETRO)).invariantSeparatorsPath
            SaveSync.retire(card, "RetroArch", rel)
            SaveSync.retire(card, "RetroArch", "$rel.earlier")
        }
        ok
    }.getOrDefault(false)

    /** Takes back a place that was set aside. The place in use, if there is one, is set aside in its stead. */
    fun useAsidePlace(g: Game) {
        if (!savesFree(g, "RetroArch")) return
        val place = placeOf(g) ?: return
        val aside = File(place.path + ".earlier").takeIf { it.isFile } ?: run { say("No place has been set aside for it."); return }
        scope.launch {
            val ok = withContext(Dispatchers.IO) { swapPlaces(place, aside) }
            // Chosen by hand: the next start loads it even though it is older than the game's own save.
            if (ok) prefs.put("place.keep:" + g.key, true)
            say(if (ok) "Next time it picks up from that place." else "The place could not be moved.")
            lookAtSaves(g)
        }
    }

    /**
     * After a save has been put back as it stood at [moment]: the place to go with it. A place written at that same
     * leaving (the one in use, the one set aside, or one on the card) is the game exactly as it was then, and is put
     * in use. Failing that the place in use is from some other time and is set aside, so the game starts from the
     * save. Returns true when a place from that moment is in use.
     */
    private fun matchPlace(g: Game, emulatorId: String, moment: Long, card: File): Boolean = runCatching {
        val core = Saves.coreFolder(emulatorId) ?: return false
        val place = placeOf(g, emulatorId) ?: return false
        val aside = File(place.path + ".earlier")
        fun near(time: Long) = kotlin.math.abs(time - moment) <= 15_000
        if (place.isFile && near(place.lastModified())) return true
        if (aside.isFile && near(aside.lastModified())) return swapPlaces(place, aside)
        val kept = SaveSync.versions(card, "RetroArch", "states/$core") { rel -> rel.substringAfterLast('/') == place.name }.firstOrNull { near(it.at) && it.file.isFile }
        // Brought across whole before anything else moves: setting the place in use aside files it on the card, and
        // the keeping rule for places (two) could let go of the very one wanted here.
        val part = File(place.path + ".part")
        val ready = kept != null && runCatching { kept.file.copyTo(part, overwrite = true); part.length() == kept.file.length() }.getOrDefault(false)
        if (!ready) part.delete()
        if (place.isFile && !setAside(place)) { part.delete(); return false }
        if (!ready || kept == null) return false
        if (!part.renameTo(place)) { part.delete(); return false }
        place.setLastModified(kept.at)
        SaveSync.retire(card, "RetroArch", place.relativeTo(File(Runner.RETRO)).invariantSeparatorsPath)
        true
    }.getOrDefault(false)

    /**
     * Puts a game's own save back as it stood at [moment]. The save in use is kept as an earlier version first, so
     * this can be undone from the same list. The place the game was left at goes with it: the one from that moment
     * when there is one, otherwise the place in use is set aside and the game starts from the save.
     */
    fun putBackSave(g: Game, moment: Long, done: () -> Unit = {}) {
        val card = saveCard() ?: run { say("No card with a games folder was found."); return }
        val emulatorId = emulatorFor(g)
        val core = Saves.coreFolder(emulatorId)
        val where = if (core != null) "RetroArch" else if (emulatorId == "dolphin") "Dolphin" else ""
        if (where.isEmpty() || !savesFree(g, where)) return
        backingUp = true
        restoring = true
        scope.launch {
            var short = 0
            var placed = false
            val n = try {
                withContext(Dispatchers.IO) {
                    val home = (if (where == "Dolphin") dolphinHome() else retroHome()) ?: return@withContext -1
                    val code = codeOf(g).orEmpty()
                    val mine = owner(g, emulatorId)
                    val under = if (core != null) "saves/$core" else ""
                    val owns = { rel: String ->
                        if (core != null) rel.startsWith("saves/$core/") && mine(rel) && own(Saves.kind(rel.substringAfterLast('/')))
                        else Saves.dolphinOwns(rel, code)
                    }
                    // What is on record is read before anything is filed, and nothing is thinned until the end: the
                    // version that was chosen must still be there when it is wanted.
                    val versions = SaveSync.versions(card, home.name, under, owns)
                    val current = (home.list() ?: return@withContext -2).filter { owns(it.rel) }.associate { it.rel to it.modified }
                    // The save that was chosen from the list must be on record this moment (a copy running just before
                    // may have thinned it): otherwise nothing is done, rather than some other save put back in its name.
                    if (versions.none { kotlin.math.abs(it.at - moment) <= 2500 && it.file.isFile }) return@withContext -3
                    val plan = Saves.asOf(moment, versions.groupBy({ it.rel }, { it.at to it.until }), current)
                    var put = 0
                    for ((rel, at) in plan) {
                        val v = versions.firstOrNull { it.rel == rel && it.at == at } ?: continue
                        if (SaveSync.putBack(home, card, v, inUse = current.containsKey(rel))) put++ else short++
                    }
                    if (put > 0 && core != null) placed = matchPlace(g, emulatorId, moment, card)
                    SaveSync.backUp(home, card, owns)
                    put
                }
            } finally { backingUp = false; restoring = false }
            if (n > 0) prefs.put("place.keep:" + g.key, placed)          // a place put back with its save is loaded, whatever its age
            val day = Saves.day(moment, System.currentTimeMillis())
            say(when {
                n == -1 -> "The launcher has not been let into Dolphin's folder yet (First-time setup, Dolphin's settings)."
                n == -2 -> "The saves could not be read just now. Nothing was changed."
                n == -3 -> "That save is no longer on record. The list is up to date now."
                n == 0 && short > 0 -> "That save could not be put back. If the game's save looks wrong now, the one that was in use is in the list."
                n == 0 -> "Nothing was changed: the save in use is already that one."
                short > 0 -> "Only part of the save from $day could be put back. The files it replaced are in the list: put the newest of them back to undo this."
                placed -> "Put back: the save from $day, with the place you left off then. The save it replaced is in the list."
                else -> "Put back: the save from $day. The one it replaced is in the list."
            })
            refreshSaveIndex()
            lookAtSaves(g)
            done()
        }
    }

    val retroManaged: Boolean get() = prefs.bool("ra.managed", true)

    /** True when RetroArch's shader collection is where the generated presets point. */
    fun shadersReady(p: RetroPack.Paths?): Boolean = p != null && File(p.slang, Runner.UNPACKED).isFile

    private var shaderJob by mutableStateOf(false)

    /** Fetches RetroArch's shader collection from libretro and puts it in place, if it is not there yet. In the background. */
    fun ensureShaders(announce: Boolean = false) {
        val pkg = Runner.retroPackage(ctx)
        val p = pkg?.let { Runner.retroPaths(ctx, it) }
        when {
            p == null -> if (announce) say("RetroArch is not installed yet.")
            shadersReady(p) -> if (announce) say("The shader collection is already in place.")
            shaderJob -> if (announce) say("Still fetching the shader collection.")
            // Fetched only once the player has asked for it (setup's step, or Settings). After it has been in place once,
            // it is fetched again by itself only if it goes missing, as when RetroArch is reinstalled.
            !announce && !prefs.bool("shaders.asked", false) -> {}
            else -> {
                shaderJob = true
                if (announce) say("Fetching the shader collection from libretro. A minute or two.")
                scope.launch {
                    val n = withContext(Dispatchers.IO) {
                        retroSetup(p)
                        val zip = File(ctx.cacheDir, "shaders_slang.zip")
                        var shown = -1
                        val got = Downloads.fetch(Sources.SHADERS, zip, progress = { f ->
                            val pct = (f * 100).toInt() / 5 * 5
                            if (pct != shown) { shown = pct; shaderProgress = pct; scope.launch(Dispatchers.Main) { touchSettings() } }
                        })
                        val n = if (got) Runner.unpackShaders(zip, p) else 0
                        zip.delete()
                        n
                    }
                    shaderJob = false
                    shaderProgress = -1
                    if (n > 0) prefs.put("shaders.asked", true)
                    touchSettings()
                    // Said only when the player asked this time; a quiet fetch that failed stays quiet.
                    if (announce) say(if (n > 0) "Shader collection in place." else "Could not fetch the shader collection. Check the Wi-Fi, then press A to try again.")
                }
            }
        }
    }

    @Volatile private var shaderProgress = -1

    private fun palGames(): List<Pair<String, String>> = cardLibrary.values.flatten().filter { it.pal }.map { it.system to it.stem }

    /**
     * Puts the setup in place. Every file is compared with what it should hold and written only where it differs, so
     * this is cheap to do before every game, and it has to be done every time: a RetroArch that was still open when a
     * setting changed writes its own, older core options back over the file when it closes.
     */
    private fun retroSetup(p: RetroPack.Paths): Boolean {
        // The shader collection carries some 400 pictures (textures, backgrounds): without this marker Android's gallery
        // and Google Photos would list them among the photos. Only the shader folder: screenshots stay visible.
        runCatching { File("${p.home}/shaders", ".nomedia").let { if (!it.exists()) { it.parentFile?.mkdirs(); it.createNewFile() } } }
        // RetroArch has signed in to RetroAchievements and keeps a login token: the password is not needed any more, so it
        // is forgotten here and written out of RetroArch's settings file, where other apps with file access could read it.
        val user = prefs.str("ra.cheevosUser").trim()
        if (prefs.str("ra.cheevosPass").isNotEmpty() && !prefs.bool("ra.cheevosFresh", false) &&
            Cheevos.signedIn(runCatching { File(p.config).readText() }.getOrDefault(""), user)) {
            prefs.put("ra.cheevosPass", ""); prefs.put("ra.cheevosSigned", user)
        }
        val look = look()
        val ok = Runner.writeRetroSetup(look, p, palGames(), cardLibrary["arcade"]?.map { it.stem } ?: emptyList())
        if (ok && look.achievements?.fresh == true) prefs.put("ra.cheevosFresh", false)    // handed over; RetroArch signs in with it next
        return ok
    }

    fun writeRetroSetupNow(): String {
        val pkg = listOf("com.retroarch.aarch64", "com.retroarch", "com.retroarch.ra32").firstOrNull { Runner.installed(ctx, it) }
            ?: return "RetroArch is not installed yet."
        val p = Runner.retroPaths(ctx, pkg) ?: return "Could not find RetroArch's folders."
        return if (retroSetup(p)) "Setup written to ${p.home}." else "Could not write to ${p.home}."
    }

    // ---- first run: the few steps that need a person, as one list ------------------------------

    /** Asks Android for the Home role. The screen that owns the window replaces this with the system's own dialog. */
    var requestHome: () -> Unit = { Runner.openSettings(ctx, Settings.ACTION_HOME_SETTINGS) }

    /** The emulator being handed to Android right now, for the list to show. */
    var installing by mutableStateOf("")
    private val installQueue = ArrayDeque<Source>()
    private var setupReturn = false      // a step sent the player elsewhere: show the list again when they come back
    private var openedForPermission = false
    private var installSince = 0L
    private var setupOffered = false

    /** The emulator each system in the library opens in, before anything is chosen by hand. */
    private fun usedEmulators(): List<Pair<SystemDef, String>> = systems().map { s ->
        s to (prefs.str("emu." + s.id).takeIf { it in s.emulators } ?: s.emulators.firstOrNull { Runner.ready(ctx, it) } ?: s.emulators.first())
    }

    fun setupFacts(): SetupFacts {
        val used = usedEmulators()
        val coreIds = used.map { it.second }.filter { it.startsWith("ra:") }.map { it.removePrefix("ra:") }.distinct()
        val retro = Runner.retroPackage(ctx)
        val paths = retro?.let { Runner.retroPaths(ctx, it) }
        val others = used.filter { !it.second.startsWith("ra:") }.groupBy({ it.second }, { it.first.name }).mapNotNull { (id, names) ->
            val def = Emulators.get(id) ?: return@mapNotNull null
            SetupEmu(id, def.label, names, Runner.plan(ctx, id, "/x")?.installed == true, Sources.of(id) != null,
                prefs.bool("setup.opened.$id", false), def.setup)
        } + listOfNotNull(Emulators.get("melonds")?.takeIf {
            // The DS on two screens (a dual-screen attachment) is played in melonDS: offered, never required.
            library["nds"]?.isNotEmpty() == true && prefs.bool("nds.two", true) && used.none { u -> u.second == "melonds" }
        }?.let { def ->
            val installed = Runner.plan(ctx, "melonds", "/x")?.installed == true
            SetupEmu("melonds", def.label + " (DS on two screens)", listOf("Nintendo DS"), installed, true,
                prefs.bool("setup.opened.melonds", false) || (installed && prefs.bool("nds.melonReady", false)), def.setup, optional = true)
        })
        val consoles = cardLibrary.filter { it.value.isNotEmpty() }.keys
        return SetupFacts(
            home = Runner.isHome(ctx),
            gamesFolder = romsRoot?.absolutePath ?: "", games = cardLibrary.values.sumOf { it.size }, consoles = consoles.size,
            canInstall = Runner.canInstall(ctx),
            retroUsed = coreIds.isNotEmpty(), retroInstalled = retro != null,
            retroStorage = retro != null && Runner.retroCanRead(ctx, retro),
            cores = coreIds.map { Emulators.coreName(it) },
            coresInside = if (retro == null) 0 else coreIds.count { Runner.coreInside(ctx, retro, it) != null },
            coresVisited = prefs.bool("setup.cores", false),
            shaders = shadersReady(paths), shadersBusy = shaderJob, shadersProgress = shaderProgress,
            files = ConsoleFiles.check(romsRoot, consoles),
            others = others, installing = installing,
            dolphin = if (others.any { it.id == "dolphin" && it.installed }) dolphinGranted() else null,
            buttonsTaught = prefs.int("m1.code", 0) != 0 && prefs.int("m2.code", 0) != 0,
            art = prefs.bool("setup.art", false),
       )
    }

    private var biosCount by mutableStateOf(0)

    /** The facts as last gathered. Gathering asks Android and the card several things, so it is done off the screen's thread. */
    var setupSeen by mutableStateOf<SetupFacts?>(null)
        private set
    private var gathering = false
    private var gatherAgain = false

    private val afterGathering = ArrayList<() -> Unit>()

    fun refreshSetup(then: (() -> Unit)? = null) {
        // What is to happen afterwards is kept even when a gathering is already under way. It used to be dropped
        // then, and the first-time list sometimes failed to open by itself because of it.
        then?.let { afterGathering += it }
        if (gathering) { gatherAgain = true; return }
        gathering = true
        // Queued behind whatever the screen's thread is doing now, never run on the spot: asked for while the launcher is
        // still being built (it was, once, at the first start after an update), the gathering read a value not yet made
        // and the launcher closed.
        scope.launch(Dispatchers.Main) {
            setupSeen = withContext(Dispatchers.IO) { setupFacts() }
            gathering = false
            if (gatherAgain) { gatherAgain = false; refreshSetup(); return@launch }      // the facts moved meanwhile: act on the fresh ones
            val run = afterGathering.toList()
            afterGathering.clear()
            run.forEach { it() }
        }
    }

    fun setupSteps(): List<SetupStep> = FirstRun.steps(setupSeen ?: setupFacts())

    fun setupAct(id: String) {
        val facts = setupFacts()
        when (id) {
            "home" -> if (facts.home) say("Timeline is the home screen.") else { setupReturn = true; requestHome() }
            "games" -> makeGamesFolder()
            "install" -> when {
                installing.isNotEmpty() -> if (SystemClock.uptimeMillis() - installSince > 20_000) {
                    // Android's question was dismissed without an answer, so none will come: put the installer down and start over.
                    Installs.abandon(ctx)
                    installQueue.clear()
                    installing = ""
                    touchSettings()
                    say("Stopped. Press A to start again.")
                } else say("Still installing $installing.")
                !facts.canInstall && FirstRun.steps(facts).first { it.id == "install" }.done.not() -> {
                    if (Runner.openSettings(ctx, Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:" + ctx.packageName)) setupReturn = true
                    else say("Could not open Android's install setting.")
                }
                else -> startInstalls()
            }
            "retro" -> {
                val pkg = Runner.retroPackage(ctx)
                when {
                    pkg == null -> say("The Emulators step above installs it.")
                    !facts.retroStorage -> if (Runner.openPackage(ctx, pkg)) { setupReturn = true; openedForPermission = true } else say("Could not open RetroArch.")
                    else -> say("RetroArch is ready.")
                }
            }
            "cores" -> {
                val pkg = Runner.retroPackage(ctx)
                if (pkg == null) say("The Emulators step above installs RetroArch first.")
                else if (Runner.openPackage(ctx, pkg)) { prefs.put("setup.cores", true); setupReturn = true; touchSettings() }
                else say("Could not open RetroArch.")
            }
            "shaders" -> { ensureShaders(announce = true); readyBios() }
            "files" -> { readyBios(); push(Screen.Menu("files")) }
            "art" -> {
                val games = all()
                if (games.isEmpty()) say("Cover art comes once there are games to look up.")
                else {
                    prefs.put("setup.art", true)
                    scrape.start(games, scrapeOptions())
                    push(Screen.Scraping)
                }
            }
            "buttons" -> push(Screen.Menu("controls"))
            "dolphinset" -> if (dolphinGranted()) scope.launch {
                val ok = withContext(Dispatchers.IO) { writeDolphin() }
                say(if (ok) "Dolphin's settings are in place." else "Could not write Dolphin's settings.")
            } else pickFolder(Docs.pick(DolphinPack.AUTHORITY, DolphinPack.ROOT), TREE_DOLPHIN)
            "finish" -> {
                if (FirstRun.left(FirstRun.steps(facts)) > 0) prefs.put("setup.quiet", true)
                home()
            }
            else -> {
                val emu = facts.others.firstOrNull { it.id == id } ?: return
                val pkg = Runner.plan(ctx, id, "/x")?.takeIf { it.installed }?.pkg
                when {
                    // An emulator wanted only for something extra (melonDS, for the DS on two screens) is installed
                    // from its own step, when the player asks for it.
                    pkg == null && emu.fetchable && emu.optional -> Sources.of(id)?.let { source ->
                        if (installing.isNotEmpty()) say("One install at a time. This one can follow.")
                        else { installQueue.clear(); installQueue.add(source); installNext() }
                    }
                    pkg == null -> say(if (emu.fetchable) "The Emulators step above installs it." else "${emu.label} is not installed.")
                    Runner.openPackage(ctx, pkg) -> { prefs.put("setup.opened.$id", true); setupReturn = true; touchSettings() }
                    else -> say("Could not open ${emu.label}.")
                }
            }
        }
    }

    // ---- Dolphin, set up by the launcher ----------------------------------------------------------

    /** Opens Android's folder picker and reports back through [folderPicked]. Set by the activity. */
    var pickFolder: (Intent, Int) -> Unit = { _, _ -> }
    private var dolphinTree = prefs.str("dolphin.tree")

    fun dolphinGranted(): Boolean = dolphinTree.isNotEmpty() && Docs.held(ctx, Uri.parse(dolphinTree))

    fun folderPicked(request: Int, uri: Uri?) {
        if (request != TREE_DOLPHIN) return
        when {
            uri == null -> say("Nothing was chosen. Dolphin stays as it is.")
            // Dolphin names its top folder "root/" (seen on Android 13); anything deeper is some folder inside it.
            uri.authority != DolphinPack.AUTHORITY || DocumentsContract.getTreeDocumentId(uri).trimEnd('/') != DolphinPack.ROOT ->
                say("That was not Dolphin's own folder. Press A again and choose Use this folder straight away, without opening anything.")
            else -> {
                runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
                dolphinTree = uri.toString()
                prefs.putNow("dolphin.tree", dolphinTree)
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { writeDolphin() }
                    say(if (ok) "Dolphin is set up." else "Could not write Dolphin's settings.")
                    touchSettings()
                }
            }
        }
    }

    /** The controllers connected now, as Android lists them, with whether each is wired (Players.buses). */
    fun pads(): List<Players.Pad> = runCatching {
        val wired = runCatching { Players.buses(java.io.File("/proc/bus/input/devices").readText()) }.getOrDefault(emptyMap())
        android.view.InputDevice.getDeviceIds().toList().mapNotNull { id -> android.view.InputDevice.getDevice(id) }
            .filter { !it.isVirtual && it.sources and android.view.InputDevice.SOURCE_GAMEPAD == android.view.InputDevice.SOURCE_GAMEPAD }
            .distinctBy { it.descriptor }
            .map { Players.Pad(it.name, it.controllerNumber, it.isExternal, it.vendorId, it.productId, wired[it.name] ?: true) }
    }.getOrDefault(emptyList())

    /**
     * The refresh rate a game will get: the panel's own, or 60 when docked to a television. The dock shows a copy of the
     * device's screen and games are then held to the television's rate; set up for 120 (two passes of the motion-clarity
     * shader to each picture) on a 60 Hz television they would run at half speed. A television is always taken as 60,
     * the rate every one of them shows. Only a television counts: the dual-screen attachment is a screen of its own, and
     * the games on the device's own screen keep its rate.
     */
    fun gameHz(): Int = if (tvDisplays().isNotEmpty()) 60 else Math.round(refreshRate).coerceAtLeast(60)   // 119.88 is 120

    // ---- a television ----------------------------------------------------------------------------

    /**
     * A television showing another rate is asked for 60 ("60"), or by default ("auto") left at its own choice. A "120"
     * kept from an earlier version, which asked for 120, now asks for 60, so the television comes back to it.
     */
    var tvHz by mutableStateOf(prefs.str("tv.hz", "auto").let { if (it == "120") "60" else it })
    /** What the television shows now, for the Display page ("1920 x 1080, 120 Hz"); empty with none connected. */
    var tvNow by mutableStateOf("")
    private val tvAsked = HashSet<Int>()          // displays already asked, while they stay connected
    private val tvCleared = HashSet<Int>()        // displays handed back to their own choice, while they stay connected
    private var tvJob: Job? = null

    /** The built-in screen's size in pixels, the long side first, for layouts made of whole screen pixels. */
    private fun panelSize(): Pair<Int, Int> = runCatching {
        val dm = ctx.getSystemService(android.hardware.display.DisplayManager::class.java)
        val mode = dm.getDisplay(android.view.Display.DEFAULT_DISPLAY).mode
        maxOf(mode.physicalWidth, mode.physicalHeight) to minOf(mode.physicalWidth, mode.physicalHeight)
    }.getOrDefault(1280 to 960)

    private fun tvDisplays(): List<android.view.Display> = runCatching {
        val dm = ctx.getSystemService(android.hardware.display.DisplayManager::class.java) ?: return@runCatching emptyList()
        dm.getDisplays(android.hardware.display.DisplayManager.DISPLAY_CATEGORY_PRESENTATION).filter {
            it.state == android.view.Display.STATE_ON && (it.flags and android.view.Display.FLAG_PRIVATE) == 0 &&
                Players.isTv(it.mode.physicalWidth, it.mode.physicalHeight)
        }
    }.getOrDefault(emptyList())

    /** Looks again whenever a display comes, goes or changes mode, a moment after it settles. */
    private fun watchTv() {
        val dm = ctx.getSystemService(android.hardware.display.DisplayManager::class.java) ?: return
        dm.registerDisplayListener(object : android.hardware.display.DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = tvSoon()
            override fun onDisplayChanged(displayId: Int) = tvSoon()
            override fun onDisplayRemoved(displayId: Int) { tvAsked.remove(displayId); tvCleared.remove(displayId); tvSoon() }
        }, android.os.Handler(android.os.Looper.getMainLooper()))
        tvSoon()
    }

    /** A controller coming or going: Dolphin's ports are written again for the controllers there now. */
    private fun watchPads() {
        val im = ctx.getSystemService(android.hardware.input.InputManager::class.java) ?: return
        im.registerInputDeviceListener(object : android.hardware.input.InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(deviceId: Int) = writeDolphinSoon()
            override fun onInputDeviceRemoved(deviceId: Int) = writeDolphinSoon()
            override fun onInputDeviceChanged(deviceId: Int) {}
        }, android.os.Handler(android.os.Looper.getMainLooper()))
        writeDolphinSoon()
    }

    private fun tvSoon() { tvJob?.cancel(); tvJob = scope.launch { delay(1500); tvMode() } }

    fun setTv(hz: String) { tvHz = hz; prefs.put("tv.hz", hz); tvAsked.clear(); tvCleared.clear(); tvSoon() }

    /**
     * At "60", a television docked at another rate is put to 60, once per connection, through the maker's service since
     * the setting is Android's own. One showing 120, as an earlier version asked of it and Android then kept for that
     * television, is first handed back to its own choice; one still at another rate after that (120 of its own, or the
     * 30 a dock can bring a 4K set up at) is asked for 60, at a size the dock already carries (TvMode.pick).
     */
    private suspend fun tvMode() {
        val tvs = tvDisplays()
        tvNow = tvs.firstOrNull()?.mode?.let { "${it.physicalWidth} x ${it.physicalHeight}, ${Math.round(it.refreshRate)} Hz" }.orEmpty()
        if (!device.bridge || tvHz == "auto") return
        var again = false
        for (d in tvs) {
            if (d.displayId in tvAsked) continue
            val now = TvMode.Mode(d.mode.physicalWidth, d.mode.physicalHeight, d.mode.refreshRate)
            val modes = d.supportedModes.map { TvMode.Mode(it.physicalWidth, it.physicalHeight, it.refreshRate) }
            val command = if (now.hz >= 119f && d.displayId !in tvCleared) {
                // Its own choice first, looked at again once it has settled.
                tvCleared += d.displayId; again = true
                "cmd display clear-user-preferred-display-mode ${d.displayId}"
            } else {
                tvAsked += d.displayId
                TvMode.pick(modes, now, 60)?.let { "cmd display set-user-preferred-display-mode ${it.width} ${it.height} ${it.hz} ${d.displayId}" }
            }
            if (command != null) withContext(Dispatchers.IO) { Bridge.run(command) }
        }
        if (again) tvSoon()
    }

    /** Dolphin's ports and the controllers that drive each (Players.dolphinPorts). */
    fun dolphinPorts(): List<List<String>> = Players.dolphinPorts(pads())

    /**
     * Puts the launcher's files into Dolphin's folder, writing only what differs. True when everything is in place.
     * Done when the folder is shared and again before every GameCube or Wii game, which costs a few short reads.
     */
    fun writeDolphin(stop: java.util.concurrent.atomic.AtomicBoolean? = null): Boolean = synchronized(dolphinLock) { writeDolphinNow(stop) }

    private val dolphinLock = Any()
    private var dolphinSoonJob: Job? = null

    /**
     * Dolphin's files written ahead of a game, away from the screen: as the launcher starts and whenever a controller
     * comes or goes. A game's start then finds them in place and has only to read them; it allows itself four seconds,
     * which the first write after an update, with Dolphin itself still starting, did not always fit into.
     */
    private fun writeDolphinSoon() {
        dolphinSoonJob?.cancel()
        dolphinSoonJob = scope.launch { delay(2000); withContext(Dispatchers.IO) { runCatching {
            if (dolphinGranted()) { val up = Proc.running(ctx, DolphinPack.PACKAGE); writeDolphin(); lullDolphin(up) }
        } } }
    }

    /**
     * The sitting's frame record (Stutter), kept for the game's page as "stutter:" and its key, then switched off. A 50 Hz
     * game in RetroArch is not judged: on a 120 Hz screen its pictures come unevenly by nature.
     */
    private fun readStutter(playing: String, inside: String) {
        if (!(device.bridge || Bridge.present)) return
        val key = playing.substringBefore('\n')
        val g = find(key)
        val hz = gameHz()
        val drawnOnly = inside.isNotEmpty() && emulatorOf(inside) != "armsx2"
        scope.launch(Dispatchers.IO) {
            // One command for the service, so a script; the whole record is too long to come back through it.
            val file = File(ctx.filesDir, "frames.sh")
            val dump = if (Scripts.write(file, Stutter.READ)) Bridge.read("timeout 9 sh ${file.absolutePath}") else null
            if (g == null || (g.pal && inside.isEmpty())) return@launch
            val s = dump?.let { Stutter.of(Stutter.histogram(it), hz, drawnOnly) } ?: return@launch
            prefs.put("stutter:$key", Stutter.format(s, System.currentTimeMillis()))
            withContext(Dispatchers.Main) { settingsTick++ }
        }
    }

    /** How late the game's last measured sitting was, in hundredths of its pictures; null when none was measured. */
    fun stutterOf(g: Game): Double? = Stutter.percentOf(prefs.str("stutter:" + g.key))

    /**
     * Back in the launcher: a Dolphin left running with no game in it is closed, once the last GameCube or Wii sitting has
     * been put away, so it takes no memory or speed from what is played next. It is read from its own threads
     * (DolphinWatch); one holding a game is left for the player to go back to. Before every start the same check runs
     * again (survey).
     */
    private var tidyJob: Job? = null
    private fun tidySoon() {
        tidyJob?.cancel()
        val seq = startSeq
        tidyJob = scope.launch {
            delay(DOLPHIN_CLOSE_MS + 1500)
            collecting?.join()
            if (starting || !inFront || seq != startSeq || !(device.bridge || Bridge.present)) return@launch
            val closed = withContext(Dispatchers.IO) {
                emulatorPackages().filter { emulatorOf(it) == "dolphin" }.mapNotNull { pkg ->
                    if (pkg in heldOpen || Proc.running(ctx, pkg) != true) return@mapNotNull null
                    if (DolphinWatch.state(ctx, pkg) !in setOf("none", "idle")) return@mapNotNull null
                    if (starting || seq != startSeq) return@mapNotNull null
                    pkg.takeIf { Vendor.forceStop(it)?.let(Bridge::run) == true }
                }
            }
            if (closed.isNotEmpty()) forgetOpenIn(closed)
        }
    }

    /** Dolphin woken only to read or write its folder is put back to sleep, if it holds no game and nothing is starting. */
    private fun lullDolphin(wasUp: Boolean?) {
        if (wasUp != false || starting || !(device.bridge || Bridge.present)) return
        if (heldOpen.containsKey(DolphinPack.PACKAGE) || prefs.str("playing.in").substringBefore('\n').contains("dolphin")) return
        if (DolphinWatch.state(ctx, DolphinPack.PACKAGE) == "idle") Vendor.forceStop(DolphinPack.PACKAGE)?.let(Bridge::run)
    }

    private fun writeDolphinNow(stop: java.util.concurrent.atomic.AtomicBoolean?): Boolean {
        if (!dolphinGranted()) return false
        val tree = Uri.parse(dolphinTree)
        var ok = true
        for (d in DolphinPack.forPorts(look(), dolphinPorts())) {
            // The game was started without waiting any longer: no file is rewritten while Dolphin reads them.
            if (stop?.get() == true) { ok = false; break }
            // The profiles come first. If one could not be put in place, no game file is written that names it:
            // Dolphin stops with an error over a profile that is not there.
            if (!ok && !d.path.startsWith("Config/Profiles")) break
            // There but unreadable: left exactly as it is, rather than built again from nothing over whatever it holds.
            val before = try { Docs.read(ctx, tree, d.path) } catch (e: Exception) { ok = false; continue }
            if (before == null && d.text == null && d.set.isEmpty()) continue        // nothing to take out of a file that is not there
            val after = d.text ?: Ini.merge(before ?: "", d.set, remove = d.remove)
            if (after != before) ok = Docs.write(ctx, tree, d.path, after) && ok
        }
        if (ok) prefs.putStrings("light.undo", prefs.strings("light.undo").filter { !it.startsWith("gc/") && !it.startsWith("wii/") }.toSet())
        if (ok) prefs.putStrings("dolgl.undo", emptySet())
        return ok
    }

    /** Fetches every emulator the games need and not yet installed, from its own project, and hands each to Android in turn. */
    private fun startInstalls() {
        installQueue.clear()
        if (Runner.retroPackage(ctx) == null && usedEmulators().any { it.second.startsWith("ra:") }) installQueue.add(Sources.retroarch)
        for (e in setupFacts().others) if (!e.installed && !e.optional) Sources.of(e.id)?.let { if (it !in installQueue) installQueue.add(it) }
        if (installQueue.isEmpty()) say("Nothing left to install.") else installNext()
    }

    private fun installNext() {
        val source = installQueue.removeFirstOrNull() ?: run { installing = ""; touchSettings(); return }
        installing = "Finding ${source.label}"
        installSince = SystemClock.uptimeMillis()
        touchSettings()
        scope.launch {
            val apk = File(ctx.cacheDir, "installers/${source.id}.apk")
            val problem = withContext(Dispatchers.IO) {
                val url = Downloads.installerUrl(source) ?: return@withContext "its download could not be found. Its own page has it: ${source.page}"
                var shown = -1
                val got = Downloads.fetch(url, apk, progress = { f ->
                    val pct = (f * 100).toInt() / 5 * 5
                    if (pct != shown) {
                        shown = pct
                        installSince = SystemClock.uptimeMillis()
                        scope.launch(Dispatchers.Main) { installing = "Downloading ${source.label}  ·  $pct%"; touchSettings() }
                    }
                })
                if (!got) return@withContext "the download did not finish. Check the Wi-Fi and press A to try again"
                Downloads.refusal(ctx, apk, source)?.let { apk.delete(); return@withContext it }
                null
            }
            if (problem != null) { installAnswered(false, problem, source.label); return@launch }
            installing = "Installing ${source.label}"
            installSince = SystemClock.uptimeMillis()
            touchSettings()
            val ok = withContext(Dispatchers.IO) { Installs.start(ctx, apk) }
            if (!ok) installAnswered(false, "Android could not read the download", source.label)
        }
    }

    /** Android's answer for the installer handed over last. */
    private fun installAnswered(ok: Boolean, message: String, name: String? = null) {
        val label = name ?: installing.substringAfter(' ')
        File(ctx.cacheDir, "installers").listFiles()?.forEach { it.delete() }      // nothing left taking up room
        Runner.forget()
        if (ok) {
            if (retroManaged) ensureShaders()
            readyBios()
            installNext()
        } else {
            installQueue.clear()
            installing = ""
            if (label.isNotEmpty()) say(if (message.isEmpty()) "$label was not installed. Press A to carry on." else "$label was not installed: $message")
        }
        touchSettings()
    }

    /** Copies BIOS files to where RetroArch looks, away from the screen's thread, and counts them for the list. */
    private fun readyBios() {
        if (Runner.retroPackage(ctx) == null) return
        scope.launch {
            biosCount = withContext(Dispatchers.IO) { Runner.copyBios(romsRoot) }
            touchSettings()
        }
    }

    /**
     * Makes the games folder, ROMs, on the SD card when there is one, with a folder for every console and the folders
     * console files go in. Folders already there are left as they are; nothing is moved or renamed.
     */
    fun makeGamesFolder() {
        scope.launch {
            val made = withContext(Dispatchers.IO) {
                runCatching {
                    val root = romsRoot ?: Storage.volumes(ctx).let { v -> (v.drop(1) + v.take(1)).firstOrNull() }?.let { File(it, "ROMs") }
                        ?: return@runCatching null
                    root.mkdirs()
                    if (!root.isDirectory) return@runCatching null
                    // A console whose folder is already there under another name (megadrive for genesis, say) gets no second one.
                    val consoles = Catalog.systems.filter { Library.folders(root, it).isEmpty() }.map { it.id }
                    val n = (consoles + listOf("bios", "bios/ps2", "bios/dc")).count { File(root, it).mkdirs() }
                    // A note in each new folder saying what goes in it. Never written over one already there.
                    fun note(dir: File, text: String) = File(dir, FolderNotes.NAME).takeIf { dir.isDirectory && !it.exists() }?.writeText(text)
                    consoles.forEach { id -> Catalog.system(id)?.let { note(File(root, id), FolderNotes.console(it)) } }
                    note(File(root, "bios"), FolderNotes.bios())
                    root to n
                }.getOrNull()
            }
            if (made == null) { say("Could not make the folder. Check that the SD card is in and has room."); return@launch }
            prefs.put("romsRoot", made.first.absolutePath)
            say(if (made.second == 0) "Every console's folder is already there, in ${made.first.absolutePath}."
                else "Made ${made.second} folders in ${made.first.absolutePath}. Copy your games into them.")
            rescan()
            touchSettings()
        }
    }

    /** At start: shows the list when something still needs the player and they have not put it off. */
    private fun offerSetup() {
        if (setupOffered) return
        setupOffered = true
        if (prefs.bool("setup.quiet", false) || current != Screen.Timeline) return
        if (FirstRun.left(setupSteps()) > 0) push(Screen.Menu("setup"))
    }

    // ---- the handheld: stick lights, power and fan. All of it is off until switched on in Settings, Device.

    fun powerFor(system: String): Power = Power.named(prefs.str("power.$system")) ?: Power.default(system)

    /** Power and fan by system switched on or off: the modes found before are remembered, then put back. */
    fun powerSwitched(on: Boolean) {
        if (on) { prefs.put("power.wasFan", device.fan.orEmpty()); prefs.put("power.wasPerf", device.performance.orEmpty()); deviceHome() }
        else {
            if (GuardService.asked) { GuardService.asked = false; GuardService.stop(ctx) }
            Device.restorePower(ctx, prefs.str("power.wasFan").ifEmpty { null }, prefs.str("power.wasPerf").ifEmpty { null })
        }
    }

    /** The state for sitting in the launcher: lights as chosen, Standard power with the Smart fan, no heat guard. */
    fun deviceHome() {
        Device.newEpoch()                                  // anything the heat guard still has queued is now out of date
        if (GuardService.asked) { GuardService.asked = false; GuardService.stop(ctx) }
        if (device.canLight) when (prefs.str("dev.lights", "Leave alone")) {
            "Match the accent" -> Device.lights(ctx, Ink.Accent.toArgb(), prefs.int("dev.lightLevel", 60))
            "Off" -> Device.lights(ctx, null, 0)
        }
        if (device.canPower && prefs.bool("dev.power", false)) Device.power(ctx, Power.resting)
    }

    // ---- the display: calibrated or as shipped ----

    var panel by mutableStateOf<Panel.State?>(null)
        private set
    /** A color change waits for a restart: set in this boot, so it shows until the device starts again. */
    val panelPending: Boolean get() = prefs.int("display.changedBoot", -1) == boots()

    fun readPanel() { scope.launch { panel = withContext(Dispatchers.IO) { runCatching { Panel.state() }.getOrNull() } } }

    private val panelWork = kotlinx.coroutines.sync.Mutex()

    fun setDisplayColor(calibrated: Boolean) {
        scope.launch {
            if (!panelWork.tryLock()) return@launch                 // one change at a time
            try {
                val problem = withContext(Dispatchers.IO) {
                    runCatching {
                        if (calibrated) {
                            // What the colors were before, read now, kept only once the calibration is in place.
                            val mode = Settings.System.getInt(ctx.contentResolver, "display_color_mode", 1)
                            val sat = Panel.saturation().takeIf { it == "1.0" || it == "1.1" } ?: "1.1"
                            val tint = Panel.tint() ?: DisplayCalibration.FACTORY_TINT
                            Panel.calibrate(ctx) ?: run {
                                if (!prefs.bool("display.ours", false) && Panel.state().calibrated) {
                                    prefs.put("display.ours", true)
                                    if (prefs.str("display.was").isEmpty()) prefs.put("display.was", "${mode.coerceIn(0, 3)},$sat,$tint")
                                }
                                Panel.color(0, "1.0"); Panel.setTint(DisplayCalibration.TINT_OFF); null
                            }
                        } else {
                            // Ours when Timeline put it there, or when a note of the colors before it was kept.
                            Panel.stock(prefs.bool("display.ours", false) || prefs.str("display.was").isNotEmpty()) ?: run {
                                val was = prefs.str("display.was").split(',')
                                val mode = was.getOrNull(0)?.toIntOrNull()?.takeIf { it in 0..3 } ?: 1
                                val sat = was.getOrNull(1)?.takeIf { it == "1.0" || it == "1.1" } ?: "1.1"
                                Panel.color(mode, sat)
                                // Recorded since 1.1.1; before, the tint was never changed from the factory's.
                                Panel.setTint(was.getOrNull(2)?.toIntOrNull() ?: DisplayCalibration.FACTORY_TINT)
                                prefs.put("display.was", ""); prefs.put("display.ours", false)
                                null
                            }
                        }
                    }.getOrElse { "The display could not be changed." }
                }
                if (problem == null) {
                    prefs.put("display.changedBoot", boots())
                    say(if (calibrated) "Calibrated color is set. Restart the Nova to see it." else "Stock color is set. Restart the Nova to see it.")
                } else say(problem)
                panel = withContext(Dispatchers.IO) { runCatching { Panel.state() }.getOrNull() }
                touchSettings()
            } finally { panelWork.unlock() }
        }
    }

    private var restartAskedAt = 0L

    /** Restarts the device, after a second press if a game is still open in an emulator or saves are being copied. */
    fun restartDevice() {
        val now = SystemClock.uptimeMillis()
        val busy = stillOpen.isNotEmpty() || heldOpen.isNotEmpty() || backingUp
        if (busy && now - restartAskedAt > 6_000) {
            restartAskedAt = now
            say(if (backingUp) "Saves are being copied to the card. Press A again to restart anyway."
                else "A game is still open and would close without saving. Press A again to restart anyway.")
            return
        }
        scope.launch(Dispatchers.IO) { Bridge.run(DisplayCalibration.RESTART) }
    }


    // ---- the rest screen, kept lit; brightness; closing what is open in the background ----

    /** When the rest screen stays lit at a low light instead of the screen going dark. */
    val restModes = listOf("Always", "While charging", "Off")
    /** Leaves the rest screen; the activity also takes Android's lock screen away first, when it is up. */
    var wake: () -> Unit = { if (current == Screen.Idle) pop() }
    /** Set by the activity: the rest screen's light and lock-screen behavior (resting, kept lit). */
    var applyRest: (Boolean, Boolean) -> Unit = { _, _ -> }
    var restAlways by mutableStateOf(prefs.str("rest.always", "Always").takeIf { it in restModes } ?: "Always")
    val keepLit: Boolean get() = current == Screen.Idle && (restAlways == "Always" || (restAlways == "While charging" && charging)) &&
        (charging || batteryLevel !in 0..15) && !deviceSecure()

    /** A PIN, pattern or password is set: then Android's own timeout is left to lock the device, as it should. */
    private fun deviceSecure(): Boolean = runCatching { ctx.getSystemService(android.app.KeyguardManager::class.java)?.isDeviceSecure == true }.getOrDefault(false)

    /** Android's own brightness, 1 to 255, or -1 when it cannot be read. */
    var brightness by mutableStateOf(readBrightness())
        private set
    fun readBrightness(): Int = runCatching { Settings.System.getInt(ctx.contentResolver, Vendor.BRIGHTNESS) }.getOrDefault(-1)

    /** One step of the brightness, a twentieth of the range, through the maker's service; automatic brightness off. */
    fun stepBrightness(dir: Int) {
        val now = readBrightness().takeIf { it > 0 } ?: 128
        val next = (now + dir * 13).coerceIn(1, 255)
        scope.launch(Dispatchers.IO) {
            Vendor.put(Vendor.BRIGHTNESS_MODE, "0")?.let(Bridge::run)
            Vendor.put(Vendor.BRIGHTNESS, next.toString())?.let(Bridge::run)
            brightness = readBrightness()
            touchSettings()
        }
    }

    /** Every emulator's package that is installed here. */
    private fun emulatorPackages(): List<String> =
        (Emulators.all.mapNotNull { id -> Emulators.get(id)?.components?.map { it.substringBefore('/') } }.flatten() +
            listOf("com.retroarch.aarch64", "com.retroarch", "com.retroarch.ra32")).distinct().filter { Runner.installed(ctx, it) }

    /** Forgets what was open in an emulator that has just been closed. */
    private fun forgetOpenIn(pkgs: Collection<String>) {
        if (pkgs.any { it.startsWith("com.retroarch") }) { stillOpen = ""; prefs.put("ra.open", ""); prefs.put("ra.last", "") }
        heldOpen.keys.filter { it in pkgs }.takeIf { it.isNotEmpty() }?.let { gone -> setHeld(heldOpen - gone.toSet()) }
    }

    private var closeAskedAt = 0L

    /** The reset in Settings: closes every emulator left in the background, after a second press if a game is open in one. */
    fun closeBackground() {
        val open = (listOfNotNull(stillOpen.takeIf { it.isNotEmpty() }) + heldOpen.values.map { it.key }).distinct().mapNotNull(::find)
        val now = SystemClock.uptimeMillis()
        if (open.isNotEmpty() && now - closeAskedAt > 6_000) {
            closeAskedAt = now
            say(open.joinToString(", ") { it.title } + (if (open.size == 1) " is" else " are") + " still open. Press A again to close without saving.")
            return
        }
        closeAskedAt = 0L
        scope.launch {
            val closed = withContext(Dispatchers.IO) {
                emulatorPackages().filter { pkg -> emulatorOf(pkg) !in Memory.NEVER_FORCE && !Runner.stopped(ctx, pkg) && Vendor.forceStop(pkg)?.let(Bridge::run) == true }
            }
            forgetOpenIn(closed)
            say(if (closed.isEmpty()) "Nothing was open in the background." else "Closed ${closed.size} ${if (closed.size == 1) "app" else "apps"} in the background.")
        }
    }

    /** Looks for the maker's service and settings afresh (and reads the temperatures), then applies the choices. */
    fun recheckDevice() {
        scope.launch {
            device = withContext(Dispatchers.IO) { Device.probe(ctx) }
            deviceHome()
            say(if (device.bridge) "Maker's service found." else "Maker's service not found.")
        }
    }

    private fun devicePlay(g: Game) {
        used()
        if (device.canLight && prefs.str("dev.lights", "Leave alone") != "Leave alone") when (prefs.str("dev.lightsPlay", "Same")) {
            "Off" -> Device.lights(ctx, null, 0)
            "The console's color", "The console's colour" -> ConsoleColor.of(g.system)?.let { Device.lights(ctx, it, prefs.int("dev.lightLevel", 60)) }
        }
        if (device.canPower && prefs.bool("dev.power", false)) {
            val p = powerFor(g.system)
            val epoch = Device.newEpoch()
            Device.power(ctx, p)
            if (prefs.bool("dev.guard", false)) runCatching {
                ctx.startForegroundService(Intent(ctx, GuardService::class.java).putExtra("base", p.fan).putExtra("epoch", epoch))
                GuardService.asked = true
            }
        }
    }

    fun systems(): List<SystemDef> = Catalog.systems.filter { library[it.id]?.isNotEmpty() == true }
    /** Any game on the card by its key, hidden or not: an open game, a save or a start in progress still needs it. */
    fun find(key: String): Game? = cardLibrary[key.substringBefore('/')]?.firstOrNull { it.key == key }
    fun all(): List<Game> = library.values.flatten()

    private class Scan(
        val root: File?, val lib: Map<String, List<Game>>, val store: MetaStore, val changed: Boolean,
        val log: Map<String, Played>?, val mine: List<UserShelf>,
        val finished: List<String>?, val hidden: List<String>?,      // null: the file is there but could not be read
   )

    fun rescan() {
        hasAccess = Storage.hasAccess()
        if (!hasAccess) return
        scope.launch {
            val current = meta
            val before = cardLibrary
            val logFrom = playsFrom
            val r = withContext(Dispatchers.IO) {
                val root = rootNow()
                // No games folder anywhere, though there was one: the card is not mounted yet (just after a restart) or is out.
                // Keep what is remembered and look again shortly, rather than showing an empty shelf.
                // A folder never found before (a fresh install) is not waited for: there is no card on its way back.
                if (root == null && (before.isNotEmpty() || (!scanned && prefs.str("lastRoot").isNotEmpty()))) return@withContext null
                val lib = root?.let(Library::scan) ?: emptyMap()
                // A card on its way out can still be named while it can no longer be listed: that is "away", not "empty".
                if (root != null && lib.isEmpty() && before.isNotEmpty() && root.list() == null) return@withContext null
                val dir = Storage.metaDir(ctx, root)
                val store = if (current.dir == dir) current else MetaStore(dir) { Storage.esdeDir(ctx, root) }
                val changed = store === current && store.refresh()
                store.forgetPictures()
                lib.keys.forEach { store.system(it) }      // read every gamelist now, away from the screen's thread
                val history = File(dir, "played.tsv")
                // A history that exists but could not be read is left alone, never replaced by an empty one.
                val log = if (logFrom == dir.path) null else if (!history.exists()) emptyMap() else runCatching { PlayLog.parse(history.readText()) }.getOrNull()
                if (lib != before) runCatching { cacheFile.writeText(LibraryCache.format(lib)) }
                Scan(root, lib, store, changed, log, UserShelves.load(dir), readMark(dir, Marks.FINISHED), readMark(dir, Marks.HIDDEN))
            }
            if (r == null) {
                cardAway = true
                waitForCard()
                if (!scanned && before.isEmpty()) scanned = waited      // the empty page says "Looking" until the wait is over
                return@launch
            }
            // Anything looked up while the card was away (pulled out, or not yet mounted in the first seconds after the
            // device starts) was remembered as "no picture". Forget all of it now that the card can answer.
            val wasAway = cardAway
            cardAway = false
            waited = false                 // the next time the folder goes missing, it is waited for afresh
            // Saves are copied, and brought back once, against the card. If that was done against another folder (the
            // device's own, before the card was in), it is owed again for this one.
            if (r.root?.absolutePath != romsRoot?.absolutePath) { backedUpAtStart = false; triedRetro = false; triedDolphin = false }
            romsRoot = r.root
            prefs.put("lastRoot", r.root?.absolutePath ?: "")
            cardLibrary = r.lib
            if (r.store !== meta) {
                if (!scrape.running) { meta = r.store; metaChanged() }   // a run in progress keeps the store it started with
            } else if (r.changed || wasAway) metaChanged()
            if (r.log != null && playsFrom != r.store.dir.path) {       // not twice: a second reading would undo what has been added since the first
                plays = r.log
                playsFrom = r.store.dir.path
                settleLater?.let { settleLater = null; settlePlay(it) }
            }
            userShelves = r.mine
            // Finished and hidden, read with everything else from this card. A file that could not be read keeps what
            // was known and is not written over until it can be read: a mark made now would replace every earlier one.
            r.finished?.let { finished = it }
            r.hidden?.let { hidden = it.toSet() }
            marksFrom = if (r.finished != null && r.hidden != null) r.store.dir.path else null
            homeCache.clear()
            scanned = true
            historyRead = true
            if (retroManaged) ensureShaders()
            // Once each time the launcher starts: a sitting that ended with the device switching off never came back here to be copied.
            if (!backedUpAtStart && wanted == null && !starting && prefs.bool("saves.backup", true) && prefs.str("playing.in").isEmpty() && prefs.str("playing").isEmpty()) { backedUpAtStart = true; backUpSaves() }
            wanted?.let { (key, disc) ->
                wanted = null
                // Asked for while the card was still being read. If that was long ago (the card took its time, or a
                // press was made and forgotten), nothing starts by itself now.
                if (wantedAt != 0L && SystemClock.uptimeMillis() - wantedAt > 20_000) say("The card is ready now.")
                else find(key)?.let { play(it, disc) } ?: say("That game is not on the card.")
            } ?: refreshSetup { offerSetup() }
        }
    }

    /** True while the games folder cannot be found although it was there before: the card is out, or not mounted yet. */
    var cardAway by mutableStateOf(false)
    private var waiting = false
    private var waited = false

    /** Looks for the card every two seconds for a minute and a half. Android also says when one is mounted; this covers the rest. */
    /**
     * The games folder to use now, or null while the one used last time may still be on its way. The games live on a
     * card, and in the first seconds after the device starts (or with the card pulled) that folder is not there. If the
     * device's own storage also has a ROMs folder, as a maker's set-up may leave behind, it must not be taken in the
     * card's place: the library, the pictures and the play history would all switch to it and back again. Only the
     * device's own folder is held back like this (a different card is taken at once), only until the wait for the card
     * is over, and an empty one is never accepted while a library is remembered.
     */
    private fun rootNow(): File? {
        val last = prefs.str("lastRoot")
        val found = Storage.findRoms(ctx, prefs) ?: return null
        if (last.isEmpty() || found.absolutePath == last) return found
        if (!found.startsWith(Storage.internal(ctx))) return found           // another card, or this one formatted anew: nothing to wait for
        if (File(last).isDirectory) return Storage.findRoms(ctx, prefs)      // last time's folder is there after all: look once more
        // Last time's folder is missing and the device's own is all there is. Not before the wait is over; and even then,
        // an empty folder never takes the place of a library that is remembered.
        return found.takeIf { waited && (cardLibrary.isEmpty() || Library.scan(it).isNotEmpty()) }
    }

    private fun waitForCard() {
        if (waiting) return
        waiting = true
        scope.launch {
            var found = false
            for (i in 0 until 45) {
                delay(2000)
                if (withContext(Dispatchers.IO) { rootNow() } != null) { found = true; break }
            }
            waiting = false
            waited = true
            // Found, or not found but there is another games folder to fall back on now that the wait is over.
            if (found || withContext(Dispatchers.IO) { rootNow() } != null) rescan() else if (cardLibrary.isEmpty()) scanned = true
        }
    }

    fun toggleFavorite(g: Game) {
        favorites = if (g.key in favorites) favorites - g.key else favorites + g.key
        prefs.putStrings("favorites", favorites)
    }

    fun emulatorChoices(g: Game): List<String> = Catalog.system(g.system)?.emulators ?: emptyList()

    fun emulatorFor(g: Game): String {
        val options = emulatorChoices(g)
        dsHolder(g, options)?.let { return it }
        prefs.str("emu:" + g.key).takeIf { it in options }?.let { return it }     // a choice made for this one game comes first
        if (twoScreens(g)) return "melonds"
        prefs.str("emu." + g.system).takeIf { it in options }?.let { return it }
        return options.firstOrNull { Runner.ready(ctx, it) } ?: options.firstOrNull().orEmpty()
    }

    fun cycleEmulator(g: Game, dir: Int = 1) {
        val options = emulatorChoices(g)
        if (options.size < 2) return
        // From the choice kept for this game, if there is one: what it opens in for the moment (an emulator still
        // holding it) is not a choice.
        val from = prefs.str("emu:" + g.key).takeIf { it in options } ?: emulatorFor(g)
        val next = options[(options.indexOf(from) + dir).mod(options.size)]
        prefs.put("emu:" + g.key, next)
        touchSettings()
    }

    /**
     * What is handed to the emulator for a game. A multi-disc game goes to RetroArch as its playlist, always: that way
     * there is one save for the whole game however it was started, and discs are changed from RetroArch's own menu.
     * Standalone emulators are handed the disc itself, since not all of them can follow a playlist on Android.
     */
    fun contentFor(g: Game, emulatorId: String, disc: String?): String = when {
        g.discs.size < 2 -> disc ?: g.path
        emulatorId.startsWith("ra:") -> if (g.path.endsWith(".m3u", true)) g.path else playlistFor(g).absolutePath
        else -> disc ?: g.discs.first()
    }

    /** Where Timeline keeps the playlist of a game whose discs came without one: with its own files, not among the games. */
    private fun playlistFor(g: Game) = File(meta.dir, "playlists/${g.system}/${File(Discs.shared(g.file)).nameWithoutExtension}.m3u")

    private var wanted: Pair<String, String?>? = null
    private var wantedAt = 0L
    private var playSoon: Job? = null
    private var unsure: String? = null

    /** Starts a game named by its key as soon as the library has been read. */
    fun playWhenReady(key: String) {
        val g = find(key)
        if (g != null) play(g) else { wanted = key to null; wantedAt = 0L }    // no timer of an earlier press applies to this one
    }

    private var starting = false
    /** Changes whenever the launcher goes behind or comes back: a start that is still checking drops itself if it moved. */
    private var startSeq = 0
    /** The emulator whose game the player has just closed (or said is closed): not asked about again for the next start. */
    private var skipOnce: String? = null
    private var cameBackAt = 0L
    /** A RetroArch start that RetroArch may answer by closing itself: the game, its disc, when, and whether the launcher has left the front since. */
    private class Again(val key: String, val disc: String?, val at: Long) { var left = false }
    private var again: Again? = null

    private var retry: Job? = null

    /** How many times the device has been started. Anything remembered as open belongs to one start of it. */
    private fun boots(): Int = runCatching { Settings.Global.getInt(ctx.contentResolver, Settings.Global.BOOT_COUNT) }.getOrDefault(0)

    private var inFront = false          // the launcher is the app in front
    private var cameByHome = false       // and it got there by the Home button

    /** The launcher has gone behind another app. */
    fun paused() {
        startSeq++                                // a start still looking at what else is open is dropped
        playSoon?.cancel(); playSoon = null       // nothing starts by itself once the launcher is no longer in front
        wanted = null                             // nor a press that was waiting for the card: not over another app, not after a sleep
        inFront = false
        again?.left = true
        retry?.cancel()          // something has come to the front by itself: do not start a second copy over it
        retry = null
    }

    /** What the history said about a game before the start now under way. */
    private class Undo(val key: String, val before: Played?, val recent: List<String>)
    private var undo: Undo? = null

    /**
     * Takes a start back out of the history, as if it had not been made. A start that never ran must not stand as the
     * game's last sitting: the next start would judge the saved place out of date against it and set it aside.
     * What to go back to is kept from the first of the two starts a hand-over makes, and on file, so that it survives
     * the second start and the launcher being restarted.
     */
    private fun takeBack(key: String) {
        val u = undo?.takeIf { it.key == key } ?: prefs.str("playing.undo").split('\n').takeIf { it.size == 2 && it[0] == key }?.let { p ->
            val f = p[1].split('\t')
            Undo(key, if (f.size == 3) Played(f[0].toIntOrNull() ?: 0, f[1].toLongOrNull() ?: 0L, f[2].toLongOrNull() ?: 0L) else null, recent.filter { it != key })
        } ?: return
        undo = null
        prefs.put("playing.undo", "")
        plays = if (u.before == null) plays - key else plays + (key to u.before)
        savePlays()
        recent = u.recent
        prefs.put("recent", recent.joinToString("\n"))
    }

    /**
     * An app was opened by hand from the Android apps page. If it is RetroArch, it now holds whatever was done in it,
     * not the game the launcher last gave it, so the next game goes through the hand-over.
     */
    fun openedApp(pkg: String) { if (pkg.startsWith("com.retroarch")) prefs.put("ra.last", "") }

    /** A start that cannot go ahead: says why, and drops anything that was waiting on it. */
    private fun refuse(why: String) { after = null; wanted = null; Feel.declined(); say(why) }      // and any earlier press still waiting is let go with it

    /**
     * Starts made by test tools over adb (an intent with "test" set) are kept out of Continue and "last system". The
     * tools put the saves and the play history back when they finish; this list lives inside the launcher, where they
     * cannot reach. It covers only the games the tools asked for, lapses ten minutes after the last such start, and ends when the launcher restarts.
     */
    private var testingUntil = 0L
    private val testKeys = HashSet<String>()
    fun testStart(key: String) {
        if (SystemClock.elapsedRealtime() >= testingUntil) testKeys.clear()
        testKeys += key
        testingUntil = SystemClock.elapsedRealtime() + 10 * 60_000
    }
    /** Only the games the tools themselves asked for, and only for ten minutes: a game started by hand is always written down. */
    private fun testStartOf(key: String) = key in testKeys && SystemClock.elapsedRealtime() < testingUntil


    fun play(g: Game, disc: String? = null, second: Boolean = false) {     // second: the automatic second start after RetroArch closed itself
        // "Play it now" on the Still open page was for this one press: used up here, whichever way this start goes.
        val knowing = knowingly
        knowingly = false
        // A press while a start is being acted on, or in the moment before an automatic one is made.
        if (starting || (retry != null && !second)) return
        // A start that was waiting for saves to be put in place gives way to this one: only the last thing asked for starts.
        playSoon?.cancel()
        playSoon = null
        val emulatorId = emulatorFor(g)
        // Dolphin closes a game left with Home in about a second; a start asked for before then waits for it, rather
        // than reaching the game screen still closing (it would look like a failed start).
        val closing = DOLPHIN_CLOSE_MS - (SystemClock.uptimeMillis() - leftDolphinAt)
        if (emulatorId == "dolphin" && leftDolphinAt > 0 && closing > 0) {
            playSoon = scope.launch { delay(closing); playSoon = null; knowingly = knowing; play(g, disc, second) }
            return
        }
        val def = Emulators.get(emulatorId)
        val content = contentFor(g, emulatorId, disc)
        // Discs that came without a playlist get one, written now so it always names the discs as they are.
        if (emulatorId.startsWith("ra:") && g.discs.size > 1 && content != g.path) runCatching {
            val list = File(content)
            val text = Discs.playlist(g.discs)
            list.parentFile?.mkdirs()
            if (!list.isFile || list.readText() != text) list.writeText(text)
        }
        // Dolphin is handed every disc of a multi-disc game, the chosen one first, and changes discs by itself.
        val plan = Runner.plan(ctx, emulatorId, content)?.let { p ->
            if (emulatorId == "dolphin" && g.discs.size > 1) p.copy(arrays = p.arrays + ("AutoStartFiles" to Launch.discSet(content, g.discs).map(Launch::romSaf))) else p
        }
        if (plan == null) { refuse("No emulator is set for this system."); return }
        if (!plan.installed) { refuse("${plan.emulator} is not installed."); return }
        // Remembered, said, and not held for ever: a press that waited too long is let go rather than start a game by surprise later.
        fun holdUntilRead(message: String) {
            wanted = g.key to disc
            wantedAt = SystemClock.uptimeMillis()
            say(message)
            val asked = wantedAt
            scope.launch { delay(20_000); if (wantedAt == asked && wanted != null && !historyRead) { wanted = null; say("The card has not been read yet. Try again in a moment.") } }
        }
        if (!File(content).exists()) {
            // In the first seconds after the device starts, the card may not have joined yet. The list is on screen all
            // the same (remembered from last time), so a press is likely: the game waits for the card, it is not refused.
            // (Once the search for a card has ended without one, a press is told so plainly instead of being promised a start.)
            if (!historyRead && !second && !waited) { holdUntilRead("Waiting for the card. ${g.title} starts in a moment."); rescan(); return }
            refuse(if (cardAway) "The card is not ready yet." else "That file is no longer on the card."); rescan(); return
        }
        // A file with nothing in it (a copy that was cut short) would leave the emulator on a black screen.
        if ((listOf(content) + g.discs).any { File(it).let { f -> f.isFile && f.length() == 0L } }) {
            refuse("${g.title} is empty on the card. Copy that file across again."); return
        }
        // A console that cannot start without a file from the console itself is told so here, with the page that
        // says which file and where, rather than left to fail on RetroArch's screen. One added since the last copy is
        // copied across now.
        if (emulatorId.startsWith("ra:")) when (ConsoleFiles.ready(g.system, romsRoot, File(Runner.RETRO, "system"))) {
            ConsoleFiles.Ready.YES -> {}
            ConsoleFiles.Ready.COPY -> Runner.copyBios(romsRoot)
            ConsoleFiles.Ready.MISSING -> {
                refuse("${Catalog.system(g.system)?.name ?: "This console"} games need a file from the console first.")
                readyBios(); push(Screen.Menu("files")); return
            }
        }
        // Saves are being brought back from the card this moment (a second or so, once after the launcher starts or
        // Dolphin's folder is shared). The game is not turned away: it starts as soon as that is done.
        if (restoring || (restoringDolphin && emulatorId == "dolphin")) {
            playSoon?.cancel()
            playSoon = scope.launch {
                var said = false
                repeat(48) {
                    delay(250)
                    if (!restoring && !(restoringDolphin && emulatorId == "dolphin")) { playSoon = null; knowingly = knowing; play(g, disc, second); return@launch }
                    if (!said && it >= 3) { said = true; say("Putting saves in place. ${g.title} starts in a moment.") }
                }
                playSoon = null
                refuse("Saves are still being put in place. Try again in a moment.")
            }
            return
        }
        // The launcher has only just started and has not read the play history from the card yet: the game starts
        // the moment that is done. Started sooner, its entry in the history would be written over, and its saved
        // place would be judged without knowing when the game was last played. (The list of games is on screen
        // from the first moment, remembered from last time, so "the library is showing" is not the test.)
        if (!historyRead && !second) { holdUntilRead("Reading the card. ${g.title} starts in a moment."); return }
        val retro = plan.pkg.startsWith("com.retroarch")
        // An emulator of its own that still holds a different game (or another disc of this one) would only show that again.
        if (!retro && def?.clear != true) heldOpen[plan.pkg]?.let { held ->
            val same = held.key == g.key && (g.discs.size < 2 || held.content.isEmpty() || held.content == content)
            if (!same && find(held.key) != null) {
                after = null
                focus.remove("menu:held"); holding = Holding(plan.pkg, plan.emulator, def?.leave.orEmpty(), held.key, g.key, disc)
                if (current != Screen.Menu("held")) push(Screen.Menu("held"))
                return
            }
        }
        // One big emulator at a time (Memory): looked at just before every start, away from the screen's thread. Not for a
        // start the player chose over the question already, nor for the test tools' starts.
        // (Nor a return to the game this emulator already holds; nor, once, the emulator whose game the player has just closed.)
        val returning = !retro && heldOpen[plan.pkg]?.key == g.key
        val skip = skipOnce
        skipOnce = null
        val big = !second && !knowing && !testStartOf(g.key) && !returning
        val heldNow = heldOpen.mapValues { it.value.key }
        val leftAt = leftDolphinAt
        val seq = ++startSeq
        // A RetroArch that failed to load a game is still there, and anything sent to it now would only freeze.
        if (retro && stuck.isNotEmpty()) {
            if (Runner.stopped(ctx, plan.pkg) || prefs.int("ra.stuckBoot", -1) != boots()) { prefs.put("ra.maybe", ""); unstick() }
            else { after = null; if (current != Screen.Menu("stuck")) push(Screen.Menu("stuck")); return }
        }
        // RetroArch only asks for storage access from its own front page. Without it, it cannot read the game or the setup.
        if (retro && !Runner.retroCanRead(ctx, plan.pkg)) {
            refuse("RetroArch needs to read your storage first. Choose Allow, then press Home.")
            if (Runner.openPackage(ctx, plan.pkg)) prefs.put("ra.last", "")       // it is open now, holding no game
            return
        }
        // RetroArch still has another game open, left with the Home button. Handing it this one closes that one without
        // keeping the place in it, and nothing can ask RetroArch to keep it first: its Android build has no command
        // channel (looked for: no listener, and the commands are not in the program). So the player is
        // asked rather than have a sitting go quietly. Not when places are not kept at all: then there is nothing to lose.
        if (retro && !second && !knowing && prefs.bool("ra.resume", true) && stillOpen.isNotEmpty() && stillOpen != g.key && find(stillOpen) != null) {
            after = null
            focus.remove("menu:held"); holding = Holding(plan.pkg, "RetroArch", "", stillOpen, g.key, disc)
            if (current != Screen.Menu("held")) push(Screen.Menu("held"))
            return
        }
        // RetroArch may still be alive behind the launcher, holding some other game. No app is allowed to end another
        // app's process (tried on Android 13 and 15: the request is refused), so RetroArch is simply handed the new game.
        // If it was holding another, or the same one under another core, it closes itself and the launcher lands back in
        // front; returned() then starts the game once more, into a RetroArch that is no longer there, and that start is
        // clean. What RetroArch last held is remembered on its own, since a game in another emulator may have been played
        // in between.
        val holds = g.key + "\n" + emulatorId
        val other = retro && prefs.str("ra.last") != holds
        // A game that is already open and is only being returned to: nothing new is started.
        val back = !second && (if (retro) !other && stillOpen == g.key else heldOpen[plan.pkg]?.key == g.key)
        val suspect = if (retro) prefs.str("ra.maybe") else ""
        starting = true
        if (!second) Feel.started()
        scope.launch {
            if (big) {
                val note = launch { delay(700); say("Checking what else is open…") }
                val (step, gone) = withContext(Dispatchers.IO) { survey(plan.pkg, emulatorId, heldNow, leftAt, skip) }
                note.cancel()
                // The player left, or started something else, while this was being looked at: this start is dropped.
                if (seq != startSeq || !inFront) { if (seq == startSeq) starting = false; return@launch }
                if (gone.isNotEmpty()) setHeld(heldOpen - gone.toSet())
                when (step) {
                    is Memory.Step.Ask -> {
                        starting = false
                        after = null
                        val asked = Emulators.get(step.id)
                        focus.remove("menu:held"); holding = Holding(step.pkg, asked?.label ?: "Another emulator", asked?.leave.orEmpty(), step.held.orEmpty(), g.key, disc,
                            memory = true, canClose = step.id !in Memory.NEVER_FORCE && device.bridge)
                        if (current != Screen.Menu("held")) push(Screen.Menu("held"))
                        return@launch
                    }
                    is Memory.Step.Close -> {
                        // Not before the last GameCube or Wii sitting has been put away: that is read through Dolphin itself.
                        if (step.pkgs.any { emulatorOf(it) == "dolphin" }) collecting?.join()
                        withContext(Dispatchers.IO) { step.pkgs.forEach { Vendor.forceStop(it)?.let(Bridge::run) } }
                        forgetOpenIn(step.pkgs)
                    }
                    Memory.Step.Go -> {}
                }
            }
            // With the launcher's own setup switched on, RetroArch is pointed at it. If it cannot be written, RetroArch starts as usual.
            if (retro) withContext(Dispatchers.IO) { Runner.copyBios(romsRoot) }     // BIOS files are put where RetroArch looks, every time, cheaply
            val ready = if (retro && retroManaged) withContext(Dispatchers.IO) {
                Runner.retroPaths(ctx, plan.pkg)?.takeIf { retroSetup(it) }
            } else null
            // Never over a cleared task: a second RetroArch window opened inside a RetroArch that is still alive hangs on its logo.
            again = if (other) Again(g.key, disc, SystemClock.uptimeMillis()) else null
            val launch = if (ready != null) plan.copy(extras = plan.extras + RetroPack.extras(look(), ready)) else plan
            // Before RetroArch looks for it: a saved place older than the game's last sitting must not be loaded.
            // Nintendo DS: the newer of its two saves goes where the emulator about to run reads it.
            // (Only between the two melonDS: DraStic keeps a save of its own.)
            var intoRetro = false
            if (g.system == "nds" && (emulatorId == "melonds" || emulatorId == "ra:melondsds")) {
                val ds = withContext(Dispatchers.IO) { runCatching { syncDsSave(g, toMelon = emulatorId == "melonds") }.getOrNull() }
                ds?.message?.let { say(it) }
                intoRetro = ds?.intoRetro == true
            }
            if (retro && (!second || intoRetro)) {
                // A place the player took back by hand is loaded once, whatever its age.
                if (prefs.bool("place.keep:" + g.key, false)) prefs.put("place.keep:" + g.key, false)
                else withContext(Dispatchers.IO) { setAsideIfStale(g, emulatorId) }
            }
            // Asked of Android itself when the launcher has only just started and has not yet looked for the maker's service.
            val root = device.bridge || withContext(Dispatchers.IO) { Bridge.present }
            // A Dolphin frozen behind the launcher would take this start and show nothing: it is closed first. Its game
            // had already ended (a Dolphin build that ends games when left does so itself), so nothing is lost.
            if (emulatorId == "dolphin" && root && endsOnLeave(plan.pkg)) {
                if (withContext(Dispatchers.IO) { DolphinWatch.state(ctx, plan.pkg) } == "frozen") {
                    withContext(Dispatchers.IO) { Vendor.forceStop(plan.pkg)?.let(Bridge::run) }
                    say("Dolphin had stopped responding, so it was closed first.")
                    delay(800)
                }
            }
            // Where this game was left (or nothing at all, so no other game's state can be loaded into it). Not for a
            // return to the game Dolphin is still holding: Dolphin reads the file only as a game boots.
            if (emulatorId == "dolphin" && root && endsOnLeave(plan.pkg) && !back) {
                collecting?.join()
                // The player left, or started something else, while the last sitting was being put away: dropped.
                if (seq != startSeq || !inFront) { if (seq == startSeq) starting = false; return@launch }
                withContext(Dispatchers.IO) { prepareDolphin(g) }
            }
            // PlayStation 2's television look, kept in ARMSX2's own folder; and its autosave set to the place-keeping
            // choice, while ARMSX2 is closed (it keeps its preferences in memory while it runs).
            // Only when the player has asked for it: it edits ARMSX2's own files.
            if (emulatorId == "armsx2" && root && prefs.bool("ps2.manage", false)) withContext(Dispatchers.IO) {
                Ps2Look.sync(ctx, plan.pkg, RetroPack.tvLook(look(), "ps2").let { t -> Tv480.preset(t.lines, t.signal, t.white, t.mask, t.stripe) })
                if (Proc.running(ctx, plan.pkg) == false) Ps2Resume.apply(ctx, plan.pkg, look().resume)
            }
            // Dolphin's files are reached through Dolphin itself. If it does not answer, the game is started all the same.
            var dolphinWritten = false
            if (emulatorId == "dolphin") {
                val stop = java.util.concurrent.atomic.AtomicBoolean(false)
                val w = scope.async(Dispatchers.IO) { writeDolphin(stop) }
                dolphinWritten = withTimeoutOrNull(4000) { w.await() } == true
                if (!dolphinWritten) stop.set(true)
            }
            if (!second) {
                undo = Undo(g.key, plays[g.key], recent)
                prefs.put("playing.undo", g.key + "\n" + (plays[g.key]?.let { "${it.count}\t${it.last}\t${it.seconds}" } ?: "none"))
            }
            awayForGame = true
            val problem = Runner.start(ctx, launch)
            starting = false
            if (problem != null) { awayForGame = false; again = null; after = null; say(problem); return@launch }
            // This sitting's frame record, read when the launcher is back (readStutter).
            if (device.bridge) scope.launch(Dispatchers.IO) { Bridge.run("dumpsys SurfaceFlinger --timestats -enable -clear") }
            val now = System.currentTimeMillis()
            if (retro && ready != null) watch(g, disc, ready, now, plan.pkg, suspect)
            if (retro) {
                prefs.put("ra.last", holds)
                if (stillOpen != g.key) { stillOpen = ""; prefs.put("ra.open", "") }
            }
            if (!testStartOf(g.key)) {
                recent = (listOf(g.key) + recent.filter { it != g.key }).take(30)
                prefs.put("recent", recent.joinToString("\n"))
                prefs.put("lastSystem", g.system)
            }
            prefs.put("playing", g.key + "\n" + now + "\n" + SystemClock.uptimeMillis())
            prefs.put("playing.in", if (retro) "" else plan.pkg + "\n" + plan.emulator + "\n" + content)
            // Drawn with OpenGL only if that was really written for it this time (or everything is).
            prefs.put("playing.gl", if (emulatorId == "dolphin" && (look().dolphinGl || (drawsWithGl(g.key) && dolphinWritten))) "1" else "0")
            prefs.put("playing.back", back)
            prefs.put("playing.boot", boots())
            if (!second) {
                plays = PlayLog.started(plays, g.key, now)
                savePlays()
            }
            devicePlay(g)
        }
    }

    // ---- a game still open in an emulator of its own ---------------------------------------------

    /** A game an emulator of its own is holding: its key, and the file it was handed (which disc, for a game of several). */
    class Held(val key: String, val content: String)

    /**
     * Games left with the Home button in an emulator other than RetroArch, by that emulator's package. Such an
     * emulator keeps its game until the game is closed from inside it: asked to start another, Dolphin simply comes
     * forward with the one it has (seen on Android 13). So a different game is not sent into it; the player is told.
     */
    var heldOpen by mutableStateOf(readHeld())
        private set

    private fun readHeld(): Map<String, Held> {
        if (prefs.int("open.boot", -1) != boots()) return emptyMap()        // the device has restarted since: nothing is open
        return prefs.str("open.held").split('\n').mapNotNull { l ->
            l.split('\t').takeIf { it.size >= 2 && it[0].isNotEmpty() }?.let { it[0] to Held(it[1], it.getOrElse(2) { "" }) }
        }.toMap()
    }

    private fun setHeld(m: Map<String, Held>) {
        heldOpen = m
        prefs.put("open.held", m.entries.joinToString("\n") { it.key + "\t" + it.value.key + "\t" + it.value.content })
        prefs.put("open.boot", boots())
    }

    fun isOpen(key: String): Boolean = key.isNotEmpty() && (stillOpen == key || heldOpen.values.any { it.key == key })

    /**
     * An emulator that has been force-stopped in Android's settings holds no game, whatever was open in it. Android
     * says so for certain (it is the same mark the Did not open page waits for), so what the launcher remembered as
     * still open there is forgotten.
     */
    private fun forgetStopped() {
        if (stillOpen.isNotEmpty() && Runner.retroPackage(ctx)?.let { Runner.stopped(ctx, it) } == true) {
            stillOpen = ""; prefs.put("ra.open", ""); prefs.put("ra.last", "")
        }
        heldOpen.keys.filter { Runner.stopped(ctx, it) }.takeIf { it.isNotEmpty() }?.let { gone -> setHeld(heldOpen - gone.toSet()) }
    }

    /** The question on the "Still open" page: an emulator is holding one game and another has been asked for. */
    /** [memory]: asked because the wanted game needs the memory another emulator is holding, not because it is the same emulator. */
    /** [open] may be empty: a big emulator is running and which game is in it, if any, is not known. [canClose]: the page may offer to force-stop it. */
    class Holding(val pkg: String, val emulator: String, val leave: String, val open: String, val wanted: String, val disc: String?,
        val memory: Boolean = false, val canClose: Boolean = false)

    /**
     * Run away from the screen's thread just before a big start: every other big emulator, whether it is running, the
     * game the launcher knows is open in it, and whether it is known to hold nothing (only a Dolphin build that ends games when left, once its
     * game thread is gone; after being left it is given its moment to close first). Also returns the remembered games
     * whose emulator is gone for certain, to be forgotten.
     */
    private fun survey(target: String, targetId: String, held: Map<String, String>, leftAt: Long, skip: String?): Pair<Memory.Step, List<String>> {
        val gone = held.keys.filter { Proc.gone(ctx, it) }
        val others = emulatorPackages().filter { it != target && it != skip && !it.startsWith("com.retroarch") }.mapNotNull { pkg ->
            val id = emulatorOf(pkg)?.takeIf { it in Memory.BIG } ?: return@mapNotNull null
            val up = Proc.running(ctx, pkg)
            var idle = false
            if (up == true && id == "dolphin" && endsOnLeave(pkg)) {
                val wait = DOLPHIN_CLOSE_MS - (SystemClock.uptimeMillis() - leftAt)
                if (leftAt > 0 && wait > 0) Thread.sleep(wait)
                idle = DolphinWatch.state(ctx, pkg) in setOf("none", "idle", "frozen")
            }
            Memory.Other(pkg, id, up, held[pkg]?.takeIf { pkg !in gone }, idle)
        }
        return Memory.decide(targetId, others) to gone
    }

    /** The emulator a package belongs to, by the launcher's own list. */
    private fun emulatorOf(pkg: String): String? = Emulators.all.firstOrNull { id -> Emulators.get(id)?.components?.any { it.substringBefore('/') == pkg } == true }

    /** The memory question's last answer: the other emulator is closed as it is, and the wanted game starts. Anything not saved inside it is lost; the page says so. */
    fun closeHeldAndPlay() {
        val h = holding ?: return
        if (current == Screen.Menu("held")) pop()
        holding = null
        after = null
        scope.launch {
            // Never for hakuX or ARMSX2 (the page does not offer them), and only counted done once Android says the app is stopped.
            val ok = h.canClose && device.bridge && withContext(Dispatchers.IO) {
                Vendor.forceStop(h.pkg)?.let(Bridge::run) == true && run { Thread.sleep(400); Runner.stopped(ctx, h.pkg) }
            }
            if (!ok) { say("${h.emulator} could not be closed from here. Close its game from its own menu."); return@launch }
            forgetOpenIn(listOf(h.pkg))
            find(h.wanted)?.let { play(it, h.disc) }
        }
    }
    var holding by mutableStateOf<Holding?>(null)
        private set
    /** [closing] is the game that has to be closed first; [at] and [wall] are when this was set, by the device's running time and by the clock. */
    private class After(val pkg: String, val key: String, val disc: String?, val at: Long, val closing: String = "", val wall: Long = 0L)
    private var after: After? = null

    /** Starts the game an emulator is holding, with the disc it was holding, which brings it forward as it is. */
    private fun bringForward(pkg: String, g: Game) = play(g, heldOpen[pkg]?.content?.takeIf { it.isNotEmpty() && it in g.discs })

    /** Brings the open game forward so it can be closed from inside; the wanted one starts when the launcher is back. */
    fun closeThenPlay() {
        val h = holding ?: return
        val open = find(h.open)
        if (current == Screen.Menu("held")) pop()
        holding = null
        if (open == null) { after = null; setHeld(heldOpen - h.pkg); find(h.wanted)?.let { play(it, h.disc) }; return }
        after = After(h.pkg, h.wanted, h.disc, SystemClock.elapsedRealtime(), h.open, System.currentTimeMillis())
        bringForward(h.pkg, open)
    }

    /**
     * The next disc of a multi-disc game open in RetroArch, as a console's lid and disc swap: back to the game, then the
     * tray opened, the next disc chosen and the tray closed, a moment apart so the core sees each (RetroArch's own disc
     * keys, pressed on a keyboard that exists for the moment: DiscKeys). The game carries on with the next disc in.
     */
    private var changingDisc = false

    fun nextDisc(g: Game) {
        if (!device.bridge) { say("This needs the maker's service, which was not found."); return }
        if (g.discs.size < 2 || !emulatorFor(g).startsWith("ra:") || stillOpen != g.key) { say("${g.title} is not open in RetroArch."); return }
        while (current != Screen.Timeline && current !is Screen.Games && current !is Screen.Detail && pop()) { }
        if (changingDisc) return                               // one change at a time: a second press would skip a disc
        changingDisc = true
        play(g)                                                // only a return to it: nothing new starts
        scope.launch {
            try {
                // RetroArch in front first: the launcher has gone behind it. If it never does, no key is pressed.
                val until = SystemClock.uptimeMillis() + 6000
                while (inFront && SystemClock.uptimeMillis() < until) delay(100)
                if (inFront) { say("RetroArch did not come forward, so the disc was left as it was."); return@launch }
                delay(1200)
                val ok = withContext(Dispatchers.IO) { DiscKeys.nextDisc(ctx) }
                if (!ok) say("The disc could not be changed. RetroArch's own menu has it: hold START, then Disc Control.")
            } finally { changingDisc = false }
        }
    }

    fun backToHeld() {
        val h = holding ?: return
        if (current == Screen.Menu("held")) pop()
        holding = null
        after = null
        val open = find(h.open)
        if (open != null) bringForward(h.pkg, open) else Runner.openPackage(ctx, h.pkg)     // which game is in it is not known: the emulator itself
    }

    private var knowingly = false

    /**
     * Starts the wanted game anyway, chosen on the page in so many words. For RetroArch the open game closes without its
     * place kept; for the memory question the other emulator is left as it is.
     */
    fun playAnyway() {
        val h = holding ?: return
        if (current == Screen.Menu("held")) pop()
        holding = null
        after = null
        find(h.wanted)?.let { knowingly = true; play(it, h.disc) }
    }

    fun heldIsClosed() {
        val h = holding ?: return
        skipOnce = h.pkg                        // the player's word for it: not asked about again for this start
        setHeld(heldOpen - h.pkg)
        if (current == Screen.Menu("held")) pop()
        holding = null
        after = null
        find(h.wanted)?.let { play(it, h.disc) }
    }

    // ---- a game that never opened --------------------------------------------------------------

    /** The game RetroArch failed to load and why, on two lines; empty when all is well. */
    var stuck by mutableStateOf(prefs.str("ra.stuck"))
        private set

    /** The game whose disc image its core could not read, so that only the console's own screen came up; empty when none. */
    var unread by mutableStateOf("")
        private set
    private var watching: Job? = null

    /** A start that came to nothing: nothing is playing, RetroArch holds nothing worth returning to, and the start leaves the history. */
    private fun gaveUp(g: Game) {
        watching = null
        again = null; retry?.cancel(); retry = null
        prefs.putNow("playing", "")
        prefs.put("playing.in", "")
        prefs.put("ra.last", ""); stillOpen = ""; prefs.put("ra.open", "")
        takeBack(g.key)
    }

    private fun comeForward() { runCatching { ctx.startActivity(Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }

    /**
     * For the first half minute after a start, reads what RetroArch writes about it.
     * - A load that fails leaves RetroArch in front on a black screen, deaf to everything. The launcher steps back in
     *   front (Android allows the home app this; anywhere else the page waits for Home) and says what happened.
     * - A disc image the core cannot read is not a failure to RetroArch: several cores start the console's own screen
     *   with no disc in it. RetroArch is well then, so it is closed, and the launcher says the file could not be read.
     * - [suspect]: the last start froze RetroArch and Android's notice was answered, with Close app or not. A RetroArch
     *   that really starts writes a new log within a moment; none after five seconds means it is still the frozen one.
     */
    private fun watch(g: Game, disc: String?, p: RetroPack.Paths, startedAt: Long, pkg: String, suspect: String) {
        watching?.cancel()
        watching = scope.launch {
            val log = File(p.log)
            repeat(60) { round ->
                delay(500)
                val text = withContext(Dispatchers.IO) {
                    runCatching { if (log.isFile && log.lastModified() >= startedAt - 2000 && log.length() < 4_000_000) log.readText() else null }.getOrNull()
                }
                if (text == null) {
                    if (suspect.isNotEmpty() && round == 10) {
                        gaveUp(g)
                        if (inFront && !cameByHome) {
                            // The launcher is back in front without Home having been pressed: Android's notice came up
                            // over the frozen RetroArch and was answered with Close app. It is gone, so start the game.
                            prefs.put("ra.maybe", "")
                            play(g, disc)
                        } else {
                            stuck = suspect
                            prefs.putNow("ra.stuck", stuck)
                            prefs.put("ra.stuckBoot", boots())
                            if (current != Screen.Menu("stuck")) push(Screen.Menu("stuck"))
                            comeForward()
                        }
                        return@launch
                    }
                    return@repeat
                }
                if (suspect.isNotEmpty()) prefs.put("ra.maybe", "")           // a new log: this is a new RetroArch
                if (g.system == "dreamcast") Saves.discId(text, File(contentFor(g, emulatorFor(g), disc)).name)?.let { id ->
                    if (codeOf(g) != id) { codes[g.key] = id; prefs.put("code:" + g.key, id) }
                }
                val why = RetroLog.failure(text)
                if (why != null) {
                    gaveUp(g)
                    // Vulkan could not be started for one of the two cores that have another way to draw: use that way from
                    // now on, so the next start works without anything having to be found in Settings.
                    val switch = why.contains("graphics") && g.system in setOf("psx", "dreamcast") && prefs.str("ra.draw", "Vulkan") != "Compatible"
                    if (switch) prefs.put("ra.draw", "Compatible")
                    stuck = g.title + "\n" + why + "\n" + g.system + "\n" + if (switch) "switched" else ""
                    prefs.putNow("ra.stuck", stuck)
                    prefs.put("ra.stuckBoot", boots())
                    if (current != Screen.Menu("stuck")) push(Screen.Menu("stuck"))
                    comeForward()
                    return@launch
                }
                if (RetroLog.noDisc(text)) {
                    gaveUp(g)
                    unread = g.title
                    if (current != Screen.Menu("unread")) push(Screen.Menu("unread"))
                    // Handed a game it does not hold, a RetroArch that is well closes itself, and the launcher is in front again.
                    if (!Runner.closeRetro(ctx, pkg)) comeForward()
                    return@launch
                }
            }
            watching = null
        }
    }

    fun stuckPackage(): String = listOf("com.retroarch.aarch64", "com.retroarch", "com.retroarch.ra32").firstOrNull { Runner.installed(ctx, it) } ?: "com.retroarch.aarch64"

    /** RetroArch has been closed (or the device restarted): games can be started again. */
    fun unstick(announce: Boolean = false) {
        if (stuck.isEmpty()) return
        val switched = stuck.split('\n').getOrElse(3) { "" } == "switched"
        stuck = ""; prefs.putNow("ra.stuck", "")
        if (current == Screen.Menu("stuck")) pop()
        if (announce) say(if (switched) "RetroArch is closed. Start the game again: it will be drawn the compatible way." else "RetroArch is closed. Everything else is as it was.")
    }

    /**
     * RetroArch is probably closed, on someone's word rather than on evidence: Android's notice over the launcher was
     * answered (Close app ends the frozen RetroArch, Wait does not, and pulling down the shade looks the same from
     * here), or the page's "It is closed already" was chosen. The page goes, and the next start checks (see [watch]).
     */
    fun probablyClosed(announce: Boolean) {
        if (stuck.isEmpty()) return
        prefs.put("ra.maybe", stuck)
        unstick(announce)
    }

    fun noticeAnswered() = probablyClosed(announce = true)

    /**
     * The RetroArch game that was left with the Home button and so is still open behind the launcher, exactly where
     * it was. Starting it again returns to it; starting a different RetroArch game closes it.
     */
    var stillOpen by mutableStateOf(if (prefs.int("playing.boot", -1) == boots()) prefs.str("ra.open") else "")
        private set
    private var homePressed = false

    /** The Home button was pressed while this launcher is the home app. Runs just before returned(). */
    /** When a game in a Dolphin build that ends games when left was last left with Home: Dolphin takes a moment to close it. */
    private var leftDolphinAt = 0L
    /** The GameCube or Wii game last left with Home: its state is collected once Dolphin has ended it. */
    private var leftDolphinGame = ""
    /** That collection, which a Dolphin start waits for. */
    private var collecting: kotlinx.coroutines.Job? = null
    private val DOLPHIN_CLOSE_MS = 2500L

    /**
     * Just before a GameCube or Wii game starts in a Dolphin build that ends games when left: a state left from the last sitting and not yet
     * taken in (the launcher was closed before it could be) is taken in first; then this game's own state is put where
     * Dolphin loads it, or nothing is. Not for a test tool's start, a game on more than one disc (Dolphin's state does not
     * say which disc was in), or once after a start from a state failed at once.
     */
    private fun prepareDolphin(g: Game) {
        val pending = prefs.str("dolphin.pending")
        var keepTemp = false
        if (pending.isNotEmpty()) when (DolphinResume.collect(ctx, pending)) {
            "ok", "none", "link" -> prefs.put("dolphin.pending", "")
            else -> keepTemp = pending == g.key && find(pending)?.discs.orEmpty().size <= 1   // could not be moved: left where Dolphin loads it
        }
        val fresh = prefs.str("dolphin.fresh") == g.key
        if (fresh) prefs.put("dolphin.fresh", "")
        val placed = !keepTemp && look().resume && !testStartOf(g.key) && !fresh && g.discs.size <= 1 &&
            DolphinResume.file(ctx, g.key).isFile && DolphinResume.place(ctx, g.key)
        if (!placed && !keepTemp) { DolphinResume.clear(ctx); prefs.put("dolphin.pending", "") }
        prefs.put("dolphin.placed", if (placed || keepTemp) g.key else "")
    }

    /**
     * A GameCube or Wii sitting in a Dolphin build that ends games when left has ended, or is ending: once Dolphin has finished with the game,
     * the state it saved as it was left goes into the launcher's keeping. Away from the screen's thread.
     */
    private suspend fun settleDolphin(key: String, viaHome: Boolean, ran: Long, content: String) {
        if (!Bridge.present) return                                   // written down: taken in before the next start
        val until = SystemClock.uptimeMillis() + 15_000
        var state = DolphinWatch.state(ctx, DolphinPack.PACKAGE)
        while (state == "game" && SystemClock.uptimeMillis() < until) { delay(1000); state = DolphinWatch.state(ctx, DolphinPack.PACKAGE) }
        if (state == null) return
        if (state == "game") {
            // Still playing: left with Dolphin's own menu open, which keeps the game (or very slow to end). It counts as
            // open there again; anything it saves later is taken in before the next start.
            withContext(Dispatchers.Main) { setHeld(heldOpen + (DolphinPack.PACKAGE to Held(key, content))) }
            return
        }
        if (find(key)?.discs.orEmpty().size > 1) { DolphinResume.clear(ctx); prefs.put("dolphin.pending", ""); return }
        when (DolphinResume.collect(ctx, key)) {
            "ok", "link" -> prefs.put("dolphin.pending", "")
            "none" -> {
                prefs.put("dolphin.pending", "")
                // Nothing saved, so it was closed from Dolphin's own menu, or Dolphin failed. Closed from the menu (Dolphin
                // still running, the game had been going a while): next time it starts fresh. A crash keeps the place;
                // a start from a place that failed at once starts fresh once, in case the place is what failed.
                if (!viaHome && state == "idle" && ran > Launch.DOLPHIN_FAILED_MS) DolphinResume.setAside(ctx, key)
                else if (!viaHome && ran in 0..Launch.DOLPHIN_FAILED_MS && prefs.str("dolphin.placed") == key) prefs.put("dolphin.fresh", key)
            }
            else -> {}                                                 // busy, full or failed: tried again before the next start
        }
    }

    /** The emulator closes its game by itself when the player leaves it (a Dolphin build signed with this launcher's own key). */
    private fun endsOnLeave(pkg: String): Boolean = pkg == DolphinPack.PACKAGE && runCatching {
        ctx.packageManager.checkSignatures(ctx.packageName, pkg) == android.content.pm.PackageManager.SIGNATURE_MATCH
    }.getOrDefault(false)

    fun leftWithHome() {
        homePressed = true
        val key = prefs.str("playing").substringBefore('\n')
        val inside = prefs.str("playing.in").split('\n')
        // Only a RetroArch sitting leaves RetroArch holding the game: "ra.last" stays as it was through a sitting in
        // an emulator of its own, and the same game may be open there.
        val inRetro = inside.firstOrNull().isNullOrEmpty()
        if (inRetro && key.isNotEmpty() && key == prefs.str("ra.last").substringBefore('\n')) { stillOpen = key; prefs.put("ra.open", key) }
        // A Dolphin build signed with this launcher's key (one changed to end a game when left) ends a game itself when it is left with Home,
        // as its menu's Exit Emulation does: nothing stays open there. A Dolphin from elsewhere keeps the game open.
        if (key.isNotEmpty() && inside.size >= 2 && endsOnLeave(inside[0])) {
            if (heldOpen.containsKey(inside[0])) setHeld(heldOpen - inside[0])
            leftDolphinAt = SystemClock.uptimeMillis()
            if (!testStartOf(key)) leftDolphinGame = key       // a test tool's start leaves no place behind
            return
        }
        if (key.isNotEmpty() && inside.size >= 2 && inside[0].isNotEmpty()) {
            setHeld(heldOpen + (inside[0] to Held(key, inside.getOrElse(2) { "" })))
            after = null                                   // the open game was not closed after all
            val g = find(key)
            if (g != null && prefs.int("hint.held", 0) < 4) {
                say(if (g.system == "ps2") "${g.title} is still open in ${inside[1]}. Close it from its own menu before you put the device down: left open, it uses battery while asleep."
                    else "${g.title} is still open in ${inside[1]}. Starting it again returns to it.")
                prefs.put("hint.held", prefs.int("hint.held", 0) + 1)
            }
        }
    }

    /**
     * The launcher's own process was started afresh by the Home button while a game was in front (Android had cleared
     * the launcher away behind it). That is a Home press like any other, only it arrives differently.
     */
    fun startedByHome() {
        val playing = prefs.str("playing")
        if (playing.isEmpty() || prefs.int("playing.boot", -1) != boots()) return
        // A GameCube or Wii game that brought the launcher back within moments of starting closed by itself (Dolphin's
        // graphics failing), with the launcher rebuilt behind it: not a Home press.
        val parts = playing.split('\n')
        val ran = SystemClock.uptimeMillis() - (parts.getOrNull(2)?.toLongOrNull() ?: -1L)
        if (parts[0].substringBefore('/') in setOf("gc", "wii") && ran in 0..Launch.DOLPHIN_FAILED_MS) return
        // Unless it was RetroArch and its own log says it has shut down since that game started: then the game was
        // left properly while the launcher was away, and it is not open behind anything.
        val since = playing.split('\n').getOrNull(1)?.toLongOrNull() ?: 0L
        if (prefs.str("playing.in").isEmpty() && since > 0L && retroClosedProperly(since)) return
        leftWithHome()
    }

    /** RetroArch's own log, written since [since], says it shut down in good order. False when there is no such log to go by. */
    private fun retroClosedProperly(since: Long): Boolean = retroSaysClosed(since) == true

    /** The same question with a third answer: null when there is no log from that sitting to go by (the launcher's RetroArch setup is switched off, say). */
    private fun retroSaysClosed(since: Long): Boolean? = runCatching {
        if (!retroManaged) return null
        val log = Runner.retroPackage(ctx)?.let { Runner.retroPaths(ctx, it) }?.log?.let(::File) ?: return null
        if (!log.isFile || log.lastModified() < since - 2000 || log.length() >= 4_000_000) return null
        RetroLog.closed(log.readText())
    }.getOrNull()

    /** Called when the launcher comes back to the front: adds the time spent in the game just left. */
    /** A game has just been handed to its emulator: a Home press from here on returns to where it was started from. */
    var awayForGame = false

    fun returned() {
        used()
        Runner.forget()
        awayForGame = false
        cameBackAt = SystemClock.uptimeMillis()
        startSeq++
        starting = false
        if (stuck.isNotEmpty() && (Runner.stopped(ctx, stuckPackage()) || prefs.int("ra.stuckBoot", -1) != boots())) { prefs.put("ra.maybe", ""); unstick(announce = true) }
        val viaHome = homePressed
        homePressed = false
        inFront = true
        cameByHome = viaHome
        forgetStopped()
        again?.let { a ->
            // Gone behind RetroArch and back in front within moments of starting a game: RetroArch was still holding
            // another one and closed itself. It is gone now, so the same game starts properly this time. Coming to the
            // front without having left (Android does that for its own reasons) is not that, and starts nothing. Nor is
            // coming back by the Home button: then the player has left the game, however quickly.
            if (cameBackAt - a.at > 2500) again = null
            else if (a.left) {
                again = null
                if (!viaHome) find(a.key)?.let { g -> retry = scope.launch { delay(600); retry = null; play(g, a.disc, second = true) }; return }
            }
        }
        val playing = prefs.str("playing")
        val inside = prefs.str("playing.in").substringBefore('\n')
        // Back from RetroArch without the Home button: it closed, so it holds nothing now. (Back from anywhere else,
        // Android's settings or another emulator, says nothing about RetroArch.)
        var next: After? = null
        // Home in the middle of "close this, then play that" is a change of mind: nothing starts by itself later.
        if (viaHome) after = null
        if (!viaHome && playing.isNotEmpty() && inside.isEmpty() && again == null) {
            val a = after?.takeIf { it.pkg.startsWith("com.retroarch") }
            if (a != null) after = null
            // Asked to close a game so that another could start: the place in it must really have been written since,
            // or it was not closed with START and SELECT at all (the launcher can come to the front in other ways),
            // and starting the other game now would lose exactly the place that was to be kept.
            // (RetroArch's log saying it shut down in good order counts too: some cores keep no place at all.)
            val closed = a == null || a.closing.isEmpty() || ((find(a.closing)?.let { placeOf(it) }?.lastModified() ?: 0L) >= a.wall - 5000) || retroSaysClosed(a.wall) == true
            if (closed) {
                if (stillOpen.isNotEmpty()) { stillOpen = ""; prefs.put("ra.open", "") }
                next = a?.takeIf { SystemClock.elapsedRealtime() - it.at < 15 * 60_000 }
            } else {
                // Nothing shows it was closed. The other game is not started over it; the player is told, and can ask again.
                val open = find(a!!.closing)?.title ?: "The game"
                val other = find(a.key)?.title ?: "the other game"
                unsure = "$open does not look closed. Leave it with START and SELECT, or ask for $other again and choose to play it now."
            }
        }
        // Back from an emulator of its own without Home: its game was closed from inside, so it holds nothing now. If
        // that was done to make way for another game, that game is started below, once this sitting has been put away.
        if (!viaHome && inside.isNotEmpty()) {
            if (heldOpen.containsKey(inside)) setHeld(heldOpen - inside)
            next = after?.takeIf { it.pkg == inside && SystemClock.elapsedRealtime() - it.at < 15 * 60_000 }
            after = null
        }
        // GameCube and Wii, however the sitting ended: Dolphin saves where the game was whenever it is left for anything
        // else (Home, or another app over it), and nothing when it is closed from its own menu. Written down first, so a
        // launcher closed meanwhile still takes the state in before the next start (settleDolphin, prepareDolphin).
        // (Not device.bridge: when Home has started the launcher afresh, it has not looked for the maker's service yet.)
        val dolphinGame = leftDolphinGame.ifEmpty {
            playing.substringBefore('\n').takeIf { !viaHome && inside == DolphinPack.PACKAGE && it.isNotEmpty() && !testStartOf(it) && endsOnLeave(inside) }.orEmpty()
        }
        leftDolphinGame = ""
        if (dolphinGame.isNotEmpty() && prefs.bool("ra.resume", true)) {
            val ran = SystemClock.uptimeMillis() - (playing.split('\n').getOrNull(2)?.toLongOrNull() ?: 0L)
            val content = prefs.str("playing.in").split('\n').getOrElse(2) { "" }
            prefs.put("dolphin.pending", dolphinGame)
            collecting = scope.launch(Dispatchers.IO) { settleDolphin(dolphinGame, viaHome, ran, content) }
        }
        prefs.put("playing.in", "")
        if (heldOpen.isNotEmpty()) {
            val keys = heldOpen.keys.toList()
            scope.launch {
                val gone = withContext(Dispatchers.IO) { if (!Bridge.present) emptyList() else keys.filter { Proc.gone(ctx, it) } }
                if (gone.isNotEmpty()) setHeld(heldOpen - gone.toSet())
            }
        }
        if (setupReturn) {
            setupReturn = false
            // RetroArch was only opened to ask for its permission; do not leave it running behind the list.
            if (openedForPermission) {
                openedForPermission = false
                prefs.put("ra.last", "")          // it is still open behind the list, holding no game: the first game goes through the hand-over above
            }
            if (current != Screen.Menu("setup")) push(Screen.Menu("setup"))
        }
        readyBios()
        touchSettings()
        deviceHome()
        if (playing.isNotEmpty()) readStutter(playing, inside)
        if (next == null) tidySoon()
        if (playing.isNotEmpty()) {
            if (prefs.bool("saves.backup", true)) backUpSaves()
            val told = dolphinOutcome(playing, viaHome)
            if (!viaHome && again == null && next == null && !told) closedAtOnce(playing)
            if (playsFrom == null) settleLater = playing else settlePlay(playing)
        }
        unsure?.let { unsure = null; say(it) }
        next?.let { a -> find(a.key)?.let { g -> retry = scope.launch { delay(500); retry = null; skipOnce = a.pkg; play(g, a.disc) } } }
    }

    /**
     * A game that is back within moments never really opened. Its start leaves the history, and it is said so rather
     * than return to the list in silence. Not for a game that was already open and has simply been closed.
     */
    private fun closedAtOnce(playing: String) {
        val parts = playing.split('\n')
        val ran = SystemClock.uptimeMillis() - (parts.getOrNull(2)?.toLongOrNull() ?: return)
        if (ran !in 0..Launch.CLOSED_AT_ONCE_MS || prefs.bool("playing.back", false)) return
        takeBack(parts[0])
        val g = find(parts[0]) ?: return
        say("${g.title} closed as soon as it opened. Try it once more, or give this system its other emulator in Settings, Emulators.")
    }

    /** Puts a finished sitting, as it was written down when it began, into the history. */
    private fun settlePlay(p: String) {
        if (p.isEmpty()) return
        if (prefs.str("playing") == p) prefs.putNow("playing", "")           // at once: a session must never be counted twice
        val parts = p.split('\n')
        val since = parts.getOrNull(1)?.toLongOrNull() ?: return
        val now = System.currentTimeMillis()
        // Time asleep with a game paused is not time played: count the time the device was awake. After a restart
        // that clock starts over, and the session is not counted.
        val awake = parts.getOrNull(2)?.toLongOrNull()?.let { SystemClock.uptimeMillis() - it }
        if (awake != null && awake < 0) return
        val next = PlayLog.finished(plays, parts[0], if (awake != null) now - minOf(awake, now - since) else since, now)
        if (next !== plays) {
            // Back from a game: how long that was, and in all, unless something more important is being said.
            val before = plays[parts[0]]?.seconds ?: 0L
            val after = next[parts[0]]
            plays = next
            savePlays()
            val g = find(parts[0])
            if (g != null && after != null && toast == null) PlayLog.session(after.seconds - before, after.seconds)?.let { say("${g.title}: $it") }
        }
    }

    private fun savePlays() {
        if (playsFrom == null) return          // never write over a history that has not been read yet
        val dir = meta.dir
        scope.launch(Dispatchers.IO) {
            // One writer at a time, each writing the history as it stands when its turn comes: the last one in is the latest.
            synchronized(playsLock) {
                runCatching {
                    dir.mkdirs()
                    val tmp = File(dir, "played.tsv.tmp")
                    tmp.writeText(PlayLog.format(plays))
                    tmp.renameTo(File(dir, "played.tsv"))
                }
            }
        }
    }

    private val playsLock = Any()

    // ---- scraping ------------------------------------------------------------------------

    fun scrapeOptions(): ScrapeOptions = ScrapeOptions(
        image = prefs.str("scrape.image", "Box 2D"), logo = prefs.str("scrape.logo", "Marquee"),
        ratings = prefs.bool("scrape.ratings", true), videos = prefs.bool("scrape.videos", false),
   )

    val coverKind: String get() = ScrapeOptions.folder(prefs.str("scrape.image", "Box 2D"))

    /** The games a run would cover, after the two filters in the scraper menu. */
    fun scrapeList(): List<Game> {
        val only = prefs.str("scrape.systems", "All systems")
        val missingOnly = prefs.str("scrape.filter", "All games") != "All games"
        val store = meta
        return systems().filter { only == "All systems" || it.name == only }
            .flatMap { library[it.id] ?: emptyList() }
            .filter { g -> !missingOnly || !store.hasArt(g) }
    }

    // ---- shelves -------------------------------------------------------------------------

    data class Shelf(
        val id: String, val name: String, val group: String, val blurb: String, val games: List<Game>,
        val notes: Map<String, String> = emptyMap(), val ordered: Boolean = false, val user: Boolean = false,
   )

    private val index by derivedStateOf { library.mapValues { (_, games) -> games.associateBy { it.base } } }

    private val shelfList by derivedStateOf {
        val all = all()
        val byKey = all.associateBy { it.key }
        val yours = listOf(
            Shelf("favorites", "Favorites", Shelves.YOURS, "Anything marked with Y, anywhere in the library.",
                all.filter { it.key in favorites }.sortedBy { it.sort }),
            Shelf("recent", "Continue", Shelves.YOURS, "The last thirty things played, newest first.",
                recent.mapNotNull { byKey[it] }, ordered = true),
            Shelf("mostplayed", "Most played", Shelves.YOURS, "Ranked by time on the clock.",
                plays.entries.filter { it.value.seconds >= 60 }.sortedByDescending { it.value.seconds }.take(30).mapNotNull { byKey[it.key] }, ordered = true),
            Shelf("history", "This week in history", Shelves.YOURS, "Games that came out this week, years ago. Oldest first.",
                history.map { it.first }.filter { it.key in byKey }, history.associate { (g, date) -> g.key to History.line(date, thisYear) }, ordered = true),
            Shelf("finished", "Finished", Shelves.YOURS, "Games you have seen to the end, the latest first. Mark one from its options.",
                finished.reversed().mapNotNull { byKey[it] }, ordered = true),
       ) + userShelves.map { u ->
            Shelf("user:" + u.name, u.name, Shelves.YOURS, "A shelf of your own. Add to it from any game's options.",
                u.keys.mapNotNull { byKey[it] }, ordered = true, user = true)
        }
        val housekeeping = listOf(
            Shelf("epics", "Multi-disc games", Shelves.LIBRARY, "Anything that came on more than one disc.",
                all.filter { it.discs.size > 1 }.sortedBy { it.sort }),
       )
        (yours + housekeeping).filter { it.games.isNotEmpty() || it.user }
            .sortedBy { Shelves.groups.indexOf(it.group) }      // stable: order within a group is as written
    }

    fun shelves(): List<Shelf> = shelfList
    fun shelf(id: String): Shelf? = shelfList.firstOrNull { it.id == id }

    /** The hand-made shelves a game sits on, for its page. */
    fun shelvesWith(g: Game): List<Shelf> =
        shelfList.filter { it.id != "recent" && it.id != "mostplayed" && it.games.any { x -> x.key == g.key } }

    fun gamesFor(s: Screen.Games): List<Game> = when {
        s.system != null -> {
            val games = library[s.system] ?: emptyList()
            if (sortMode == "Title") games else {
                val metas = meta.system(s.system)
                Sorting.apply(sortMode, games, { metas[it.file]?.date.orEmpty() }, { plays[it.key] }, { it.key in favorites })
            }
        }
        s.shelf != null -> shelf(s.shelf)?.games ?: emptyList()
        else -> emptyList()
    }

    /** Games worth showing on a system's poster: what has been played and loved first, then the best rated. */
    fun spotlight(system: String): List<Game> {
        val games = library[system] ?: return emptyList()
        val played = recent.mapNotNull(::find).filter { it.system == system && it.key !in hidden }
        val rated = games.map { it to (meta.meta(it)?.rating ?: -1f) }.filter { it.second > 0f }.sortedByDescending { it.second }.map { it.first }
        return (played + games.filter { it.key in favorites } + rated + games).distinctBy { it.key }.take(16)
    }

    fun onShelf(name: String, g: Game): Boolean = userShelves.firstOrNull { it.name == name }?.keys?.contains(g.key) == true

    private fun store(s: UserShelf) {
        val dir = meta.dir
        scope.launch(Dispatchers.IO) { runCatching { UserShelves.save(dir, s) } }
    }

    fun toggleShelf(name: String, g: Game) {
        val s = userShelves.firstOrNull { it.name == name } ?: return
        val next = s.copy(keys = if (g.key in s.keys) s.keys - g.key else s.keys + g.key)
        userShelves = userShelves.map { if (it.name == name) next else it }
        store(next)
    }

    /** Makes a shelf (or finds the one already called that) and puts the game on it. Returns its name. */
    fun createShelf(raw: String, g: Game?): String? {
        val name = UserShelves.clean(raw)
        if (name.isEmpty()) return null
        val existing = userShelves.firstOrNull { it.name.equals(name, ignoreCase = true) }
        if (existing != null) {
            if (g != null && g.key !in existing.keys) toggleShelf(existing.name, g)
            return existing.name
        }
        val s = UserShelf(name, listOfNotNull(g?.key))
        userShelves = (userShelves + s).sortedBy { it.name.lowercase() }
        store(s)
        return name
    }

    fun deleteShelf(name: String) {
        userShelves = userShelves.filter { it.name != name }
        val dir = meta.dir
        scope.launch(Dispatchers.IO) { runCatching { UserShelves.delete(dir, name) } }
    }
}

data class TextEntry(val title: String, val key: String, val secret: Boolean)

/** One scraping run. Progress is plain state the scraping page reads. */
class ScrapeJob(private val app: AppState, private val scope: CoroutineScope) {
    var running by mutableStateOf(false)
    var total by mutableStateOf(0)
    var done by mutableStateOf(0)
    var found by mutableStateOf(0)
    var missed by mutableStateOf(0)
    var current by mutableStateOf("")
    var stopped by mutableStateOf<String?>(null)
    var source by mutableStateOf("")
    private var job: Job? = null

    fun start(games: List<Game>, o: ScrapeOptions) {
        if (running) return
        running = true
        total = games.size; done = 0; found = 0; missed = 0; current = ""; stopped = null; source = o.source
        val store = app.meta
        val mediaRoot = File(store.dir, "downloaded_media")
        job = scope.launch(Dispatchers.IO) {
            try {
                for (g in games) {
                    if (!isActive) break
                    withContext(Dispatchers.Main) { current = g.title }
                    val ok = try {
                        val f = Scraper.lookup(g, o)
                        if (f == null) false else {
                            val pictures = Scraper.fetch(g, f, mediaRoot)
                            f.meta?.let { store.put(g, it) }
                            f.meta != null || pictures > 0
                        }
                    } catch (e: Exception) {
                        false
                    }
                    val flush = (done + 1) % 10 == 0
                    if (flush) store.flush()
                    withContext(Dispatchers.Main) {
                        if (ok) found++ else missed++
                        done++
                        if (flush) app.metaChanged()
                    }
                    delay(120)   // be a polite guest on other people's servers
                }
            } finally {
                store.flush()
                withContext(NonCancellable + Dispatchers.Main) {
                    app.metaChanged()
                    running = false
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
    }
}
