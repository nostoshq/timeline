// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 NOSTOS, LLC

package market.nostos.timeline

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.InputStream

/** One save file where an emulator keeps it: its path below the emulator's own folders, its size, and when it was written. */
class SaveEntry(val rel: String, val size: Long, val modified: Long) {
    val name: String get() = rel.substringAfterLast('/')
}

/** Somewhere saves live. RetroArch's are plain files; Dolphin's are reached through the folder Dolphin shares. */
interface SaveHome {
    /** The folder under "_timeline/saves" on the card that mirrors this one. */
    val name: String
    /** Every save file there, or null when the place could not be read, which is not the same as its being empty. */
    fun list(): List<SaveEntry>?
    /** One file as it is this moment, or null when it is not there. */
    fun stat(rel: String): SaveEntry?
    fun open(rel: String): InputStream?
    /** Replaces the file, or makes it. [modified] is kept where the home allows a file's time to be set. */
    fun put(rel: String, from: File, modified: Long): Boolean
}

/** Plain folders: RetroArch's "saves" and "states". */
/** [keep] says which files are saves, where the folder holds other things too (melonDS saves beside the games). */
class FileHome(override val name: String, private val root: File, private val tops: List<String>, private val keep: (String) -> Boolean = { true }) : SaveHome {
    override fun list(): List<SaveEntry>? = runCatching {
        tops.flatMap { top ->
            val dir = File(root, top)
            if (!dir.isDirectory) emptyList() else dir.walkTopDown()
                .filter { it.isFile && !it.name.endsWith(".bak") && !it.name.endsWith(".part") && !it.name.endsWith(".swap") && !it.name.startsWith(".") && keep(it.name) }
                .map { SaveEntry(top + "/" + it.relativeTo(dir).invariantSeparatorsPath, it.length(), it.lastModified()) }.toList()
        }
    }.getOrNull()

    override fun stat(rel: String): SaveEntry? = File(root, rel).takeIf { it.isFile }?.let { SaveEntry(rel, it.length(), it.lastModified()) }

    override fun open(rel: String): InputStream? = File(root, rel).takeIf { it.isFile }?.inputStream()

    // Written beside the save and swapped in whole, so nothing interrupted ever leaves half a save in its place.
    override fun put(rel: String, from: File, modified: Long): Boolean = runCatching {
        if (!from.isFile) return false
        val dst = File(root, rel)
        dst.parentFile?.mkdirs()
        val part = File(dst.path + ".part")
        from.copyTo(part, overwrite = true)
        if (part.length() != from.length()) { part.delete(); return false }
        if (!part.renameTo(dst)) {
            // Some file systems will not rename over a file. The old one is moved aside, not deleted, until the new one is in.
            val aside = File(dst.path + ".swap")
            if (dst.exists() && !dst.renameTo(aside)) { part.delete(); return false }
            if (!part.renameTo(dst)) { aside.renameTo(dst); part.delete(); return false }
            aside.delete()
        }
        dst.setLastModified(modified)
        true
    }.getOrDefault(false)
}

/** An emulator's own folder, shared with the launcher through Android's folder picker. [keep] says which paths are saves. */
class DocHome(
    override val name: String, private val ctx: Context, private val tree: Uri,
    private val tops: List<String>, private val keep: (String) -> Boolean,
) : SaveHome {
    override fun list(): List<SaveEntry>? {
        // An emulator whose folder is not ready yet answers with nothing at all rather than with an error. Its own
        // folders are always there once it is, so an empty top level means "not now", never "no saves".
        if (!Docs.alive(ctx, tree)) return null
        val out = ArrayList<SaveEntry>()
        for (top in tops) out += Docs.walk(ctx, tree, top) ?: return null
        return out.filter { keep(it.rel) }
    }

    override fun stat(rel: String): SaveEntry? = Docs.stat(ctx, tree, rel)

    override fun open(rel: String): InputStream? = Docs.open(ctx, tree, rel)

    override fun put(rel: String, from: File, modified: Long): Boolean = Docs.put(ctx, tree, rel, from)
}

/**
 * Copies saves between where an emulator keeps them and the card, never losing what a copy replaces.
 *
 * An earlier version's name carries when its contents were last written; the version file's own time is when it
 * stopped being the copy in use. The first is what the player is shown, the second is what tells which version of
 * one file was in use while a given version of another was.
 */
object SaveSync {
    private const val SLACK = 2500L        // a card keeps time to two seconds, so anything closer than that is the same moment
    private const val SMALL = 4_000_000L   // a game's own save is far smaller than this; places and states can be far larger

    /** How many copies could not be made since this was last set to nought: a full card, or one pulled out part-way. */
    @Volatile var failed = 0

    /** How many times an emulator's folders could not be read since this was last set to nought. "Nothing to copy" must not be said then. */
    @Volatile var unread = 0

    private fun latest(card: File, home: String) = File(card, home)
    private fun slot(card: File, home: String, rel: String) = File(File(card, "earlier/$home"), rel)

    private fun fill(from: InputStream, into: ByteArray): Int {
        var n = 0
        while (n < into.size) { val got = from.read(into, n, into.size - n); if (got < 0) break; n += got }
        return n
    }

    private fun same(a: InputStream, b: File): Boolean = runCatching {
        b.inputStream().use { other ->
            val x = ByteArray(1 shl 16); val y = ByteArray(1 shl 16)
            while (true) {
                val n = fill(a, x)
                if (n != fill(other, y) || !x.contentEquals(y)) return@runCatching false
                if (n < x.size) return@runCatching true
            }
            @Suppress("UNREACHABLE_CODE") true
        }
    }.getOrDefault(false)

    private fun sameFiles(a: File, b: File): Boolean = a.length() == b.length() && runCatching { a.inputStream().use { same(it, b) } }.getOrDefault(false)

    private fun keeps(name: String): Pair<Int, Int> = when (Saves.kind(name)) {
        Saves.Kind.PLACE, Saves.Kind.ASIDE, Saves.Kind.SLOT, Saves.Kind.DISK -> Saves.PLACES to Saves.PLACES
        else -> Saves.RECENT to Saves.MOST
    }

    private fun versionsOf(slot: File): List<File> =
        slot.parentFile?.listFiles()?.filter { it.isFile && Saves.original(it.name) == slot.name } ?: emptyList()

    /** Lets go of the earlier versions of one save that the keeping rule no longer covers, never one named in [keep]. */
    private fun thin(slot: File, keep: Set<String>) {
        val versions = versionsOf(slot)
        val (recent, most) = keeps(slot.name)
        val gone = Saves.drop(versions.mapNotNull { Saves.writtenAt(it.name) }, recent, most).toSet()
        for (v in versions) if (v.name !in keep && Saves.writtenAt(v.name) in gone) v.delete()
    }

    /**
     * Files [old] among the earlier versions kept at [slot]. [written] names it; [until] is when it stopped being the
     * copy in use. Contents already on record are not filed a second time, so going back and forth between two saves
     * does not push real ones out. With [move] the file itself becomes the version (or is removed when it was on
     * record already). Returns false only when it could not be kept.
     */
    private fun file(old: File, slot: File, written: Long, until: Long, move: Boolean, protect: Set<String>, thinning: Boolean): Boolean = runCatching {
        if (!old.isFile) return true
        if (old.length() == 0L) { if (move) old.delete(); return true }           // an empty file is not a save worth keeping
        slot.parentFile?.mkdirs()
        versionsOf(slot).firstOrNull { sameFiles(it, old) }?.let { known ->
            // On record already. If it has been in use again since, the record takes the later time: that is the
            // moment the player will look for it under ("the save I had this afternoon"), not the first time it was kept.
            if (written > (Saves.writtenAt(known.name) ?: 0L) + 1000 && versionsOf(slot).none { Saves.writtenAt(it.name) == Saves.writtenAt(Saves.versionName(slot.name, written)) }) {
                val renamed = File(slot.parentFile, Saves.versionName(slot.name, written, until))
                if (known.renameTo(renamed)) renamed.setLastModified(until)
            }
            if (move) old.delete()
            return true
        }
        // Two different copies written in the same second are both kept: the second takes the next free second.
        // (A version is known to the pages by when it was written, so that must be its alone.)
        val taken = versionsOf(slot).mapNotNull { Saves.writtenAt(it.name) }.toSet()
        var at = written
        while (Saves.writtenAt(Saves.versionName(slot.name, at)) in taken) at += 1000
        val version = File(slot.parentFile, Saves.versionName(slot.name, at, maxOf(until, at)))
        if (!move || !old.renameTo(version)) {
            val part = File(version.path + ".part")
            old.copyTo(part, overwrite = true)
            if (part.length() != old.length() || !part.renameTo(version)) { part.delete(); return false }
            if (move) old.delete()
        }
        version.setLastModified(until)
        if (thinning) thin(slot, protect + version.name)
        true
    }.getOrDefault(false)

    /** The same, for a file that is still where the emulator keeps it: brought across whole first, then filed. */
    private fun fileFromHome(home: SaveHome, e: SaveEntry, slot: File, until: Long, protect: Set<String>, thinning: Boolean): Boolean = runCatching {
        if (e.size == 0L) return true
        slot.parentFile?.mkdirs()
        val incoming = File(slot.parentFile, slot.name + ".incoming.part")
        val got = home.open(e.rel)?.use { i -> incoming.outputStream().use { o -> i.copyTo(o) } }
        if (got == null || got != e.size) { incoming.delete(); return false }
        file(incoming, slot, e.modified, until, move = true, protect = protect, thinning = thinning).also { if (!it) incoming.delete() }
    }.getOrDefault(false)

    /** Files a file from the device's own storage as an earlier version on the card (a place about to be written over). */
    fun keep(local: File, card: File, home: String, rel: String): Boolean =
        file(local, slot(card, home, rel), local.lastModified(), System.currentTimeMillis(), move = false, protect = emptySet(), thinning = true)

    /**
     * The card's newest copy of a file the device no longer has in use (a place that was set aside) is filed as an
     * earlier version, so that bringing saves back does not put it in use again.
     */
    fun retire(card: File, home: String, rel: String): Boolean {
        val copy = File(latest(card, home), rel)
        return !copy.isFile || file(copy, slot(card, home, rel), copy.lastModified(), System.currentTimeMillis(), move = true, protect = emptySet(), thinning = true)
    }

    /**
     * Copies to the card whatever is newer where the emulator keeps it. Never the other way. What a copy replaces on
     * the card is kept as an earlier version. A save here that is older than the card's and not the same (the device's
     * clock was put back, or the card has been in another device) is put on record too, without becoming the newest.
     * Returns how many files were copied.
     */
    fun backUp(home: SaveHome, card: File, only: (String) -> Boolean = { true }, thinning: Boolean = true): Int = runCatching {
        val listed = home.list() ?: run { unread++; return 0 }
        var n = 0
        for (e in listed) {
            if (!only(e.rel)) continue
            runCatching {
                val dst = File(latest(card, home.name), e.rel)
                val earlier = slot(card, home.name, e.rel)
                if (dst.isFile) {
                    val gap = e.modified - dst.lastModified()
                    val sized = dst.length() == e.size
                    when {
                        gap > SLACK -> if (sized && home.open(e.rel)?.use { same(it, dst) } == true) {
                            dst.setLastModified(e.modified)                    // the same save, only written again later
                            return@runCatching
                        }
                        gap >= -SLACK -> if (sized) return@runCatching         // the same moment and the same size: the same save
                        else -> {
                            val own = Saves.kind(e.name).let { it == Saves.Kind.SAVE || it == Saves.Kind.CLOCK } && e.size <= SMALL
                            if (own && !(sized && home.open(e.rel)?.use { same(it, dst) } == true)) fileFromHome(home, e, earlier, e.modified, emptySet(), thinning)
                            return@runCatching
                        }
                    }
                }
                dst.parentFile?.mkdirs()
                val part = File(dst.path + ".part")
                val got = home.open(e.rel)?.use { i -> part.outputStream().use { o -> i.copyTo(o) } }
                if (got == null || got != e.size) { part.delete(); failed++; return@runCatching }   // being written this moment, or no room
                // The new copy is whole on the card before the old one moves anywhere.
                if (dst.isFile && !file(dst, earlier, dst.lastModified(), e.modified, move = true, protect = emptySet(), thinning = thinning)) { part.delete(); failed++; return@runCatching }
                if (!part.renameTo(dst)) { dst.delete(); if (!part.renameTo(dst)) { part.delete(); failed++; return@runCatching } }
                dst.setLastModified(e.modified)
                // An earlier version that is the very save now in use (it was put back) is no longer an earlier one.
                // Left on record it would count toward the number kept and push a real one out.
                if (e.size <= SMALL) versionsOf(earlier).filter { sameFiles(it, dst) }.forEach { it.delete() }
                n++
            }.onFailure { failed++ }
        }
        n
    }.getOrDefault(0)

    /**
     * For a new or wiped device: brings saves from the card where the emulator has none, or an older one. It never
     * replaces a newer save, what it does replace is kept on the card as an earlier version first, and a file that is
     * not exactly as it was when the emulator's folders were listed (a game has just written it, or it was never in
     * the list at all) is left alone.
     */
    fun restore(home: SaveHome, card: File): Int = runCatching {
        val from = latest(card, home.name)
        if (!from.isDirectory) return 0
        val there = (home.list() ?: run { unread++; return 0 }).associateBy { it.rel }
        var n = 0
        from.walkTopDown().filter { it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".bak") && !it.name.startsWith(".") }.forEach { f ->
            runCatching {
                val rel = f.relativeTo(from).invariantSeparatorsPath
                val listed = there[rel]
                val now = home.stat(rel)
                // Listed a moment ago and not to be found now: it has not vanished, it could not be looked at. Left alone.
                if (listed != null && now == null) { failed++; return@runCatching }
                if (now != null) {
                    if (listed == null || listed.size != now.size || listed.modified != now.modified) return@runCatching
                    val gap = f.lastModified() - now.modified
                    val wanted = gap > SLACK || (gap >= -SLACK && f.length() != now.size)
                    if (!wanted) return@runCatching
                    if (now.size == f.length() && home.open(rel)?.use { same(it, f) } == true) return@runCatching
                    if (!fileFromHome(home, now, slot(card, home.name, rel), System.currentTimeMillis(), emptySet(), thinning = true)) { failed++; return@runCatching }
                }
                if (home.put(rel, f, f.lastModified())) n++ else failed++
            }.onFailure { failed++ }
        }
        n
    }.getOrDefault(0)

    /** One earlier version of a save: the file it is a version of, where the version is, when it was last written, and until when it was in use. */
    class Version(val rel: String, val file: File, val at: Long, val until: Long)

    /** Every earlier version on the card of the files [owns] picks out, looking below [under] (a path inside the emulator's folders). */
    fun versions(card: File, home: String, under: String, owns: (String) -> Boolean): List<Version> = runCatching {
        val top = File(card, "earlier/$home")
        val dir = File(top, under)
        if (!dir.isDirectory) return emptyList()
        val found = dir.walkTopDown().maxDepth(12).filter { it.isFile }.mapNotNull { f ->
            val original = Saves.original(f.name) ?: return@mapNotNull null
            val rel = File(f.parentFile, original).relativeTo(top).invariantSeparatorsPath
            val at = Saves.writtenAt(f.name) ?: return@mapNotNull null
            if (owns(rel)) Version(rel, f, at, Saves.usedUntil(f.name) ?: f.lastModified()) else null
        }.toList()
        // "Until when it was in use" is in the version's name. Versions kept before it was put there have only the
        // file's own time for it, which a copy of the card can lose (every file then carries the day of the copy).
        // Among those, where it cannot be right, each is taken to have been in use until the next was written.
        found.groupBy { it.rel }.values.flatMap { of ->
            val sorted = of.sortedBy { it.at }
            val unnamed = sorted.filter { Saves.usedUntil(it.file.name) == null }
            val wrong = unnamed.any { it.until < it.at - SLACK } || (unnamed.size > 1 && unnamed.map { it.until / 60_000 }.distinct().size == 1)
            if (!wrong) sorted else sorted.mapIndexed { i, v ->
                if (Saves.usedUntil(v.file.name) != null) v else Version(v.rel, v.file, v.at, sorted.getOrNull(i + 1)?.at ?: Long.MAX_VALUE)
            }
        }
    }.getOrDefault(emptyList())

    /** The newest copies on the card of the files [owns] picks out. */
    fun onCard(card: File, home: String, under: String, owns: (String) -> Boolean): List<SaveEntry> = runCatching {
        val top = File(card, home)
        val dir = File(top, under)
        if (!dir.isDirectory) return emptyList()
        dir.walkTopDown().maxDepth(12).filter { it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".bak") }.mapNotNull { f ->
            val rel = f.relativeTo(top).invariantSeparatorsPath
            if (owns(rel)) SaveEntry(rel, f.length(), f.lastModified()) else null
        }.toList()
    }.getOrDefault(emptyList())

    /**
     * Puts one earlier version back in use. The save in use is filed as an earlier version first, so this can be
     * undone the same way, and nothing is thinned while it happens: the version wanted must still be there when it is
     * read. The card's newest copy follows at the next backup.
     */
    fun putBack(home: SaveHome, card: File, version: Version, inUse: Boolean = true): Boolean = runCatching {
        if (!version.file.isFile) return false
        val now = home.stat(version.rel)
        // [inUse]: the emulator's own listing had this file a moment ago. If it cannot be looked at now it is still
        // there, only out of reach, and writing over it would lose it unfiled.
        if (inUse && now == null) return false
        if (now != null && !fileFromHome(home, now, slot(card, home.name, version.rel), System.currentTimeMillis(), setOf(version.file.name), thinning = false)) return false
        if (!version.file.isFile) return false
        // Stamped later than the save it replaces, whatever the device's clock says, so the card takes it as the newer one.
        home.put(version.rel, version.file, maxOf(System.currentTimeMillis(), (now?.modified ?: 0L) + 3000))
    }.getOrDefault(false)
}
