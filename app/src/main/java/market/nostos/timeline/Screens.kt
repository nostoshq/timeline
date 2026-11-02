// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 NOSTOS, LLC

package market.nostos.timeline

import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar
import java.util.TimeZone

private fun two(n: Int): String = n.toString().padStart(2, '0')

/** Opens a game's page, remembering the list it came from so L1 and R1 can step through it. */
private fun AppState.page(key: String, list: List<String>, from: List<String>) {
    browse = list
    browseFrom = from
    push(Screen.Detail(key))
}

private fun playedLines(p: Played?): List<String> {
    if (p == null || p.count == 0) return emptyList()
    val now = System.currentTimeMillis()
    return listOf(p.lastSeen(now, TimeZone.getDefault().getOffset(now)), p.time).filter { it.isNotEmpty() }
}

/** The panel's three lines of [text], cut by [Excerpt] where the layout says the third line ends. */
@Composable
private fun descExcerpt(text: String, widthPx: Int): String {
    val measurer = rememberTextMeasurer(cacheSize = 8)
    val style = textStyle(17, Ink.Muted, FontWeight.Normal, 0, 1.5f)
    return remember(text, widthPx) {
        if (widthPx <= 0) return@remember text
        val r = measurer.measure(text, style, maxLines = 3, constraints = androidx.compose.ui.unit.Constraints(maxWidth = widthPx))
        if (!r.hasVisualOverflow) text else Excerpt.of(text, r.getLineEnd(r.lineCount - 1, visibleEnd = true))
    }
}

/** "Played", then when and for how long, one to a line, so it fits beside the buttons whatever it says. */
@Composable
private fun PlayedNote(p: Played?, modifier: Modifier = Modifier, always: Boolean = false) {
    val lines = playedLines(p)
    if (lines.isEmpty() && !always) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.u())) {
        Label("Played", size = 11)
        if (lines.isEmpty()) Label("Not yet", size = 12)
        lines.forEach { Label(it, color = Ink.Cream, size = 12) }
    }
}

// ---------------------------------------------------------------------------------- Timeline

@Composable
fun TimelineScreen() {
    val app = LocalApp.current
    val systems = app.systems()
    if (systems.isEmpty()) {
        EmptyLibrary()
        return
    }
    val cursor = rememberCursor("timeline", systems.size, initial = systems.indexOfFirst { it.id == app.prefs.str("lastSystem") })
    val last = app.recent.firstOrNull { it !in app.hidden || app.isOpen(it) }?.let(app::find)
    fun open() = app.push(Screen.Games(system = systems[cursor.index].id))
    // From the console in view, as the button sits under its name beside Open.
    fun surprise() {
        val system = systems[cursor.index]
        Surprise.pick(app.library[system.id].orEmpty(), app.lastSurprise)?.let { g ->
            app.lastSurprise = g.key
            app.page(g.key, emptyList(), listOf("Timeline", system.name))
        }
    }
    fun resume() {
        if (last != null) app.page(last.key, app.recent.filter { it !in app.hidden || app.isOpen(it) }, listOf("Timeline", "Continue"))
    }
    // A game released on this very day, years ago, named under Continue. SELECT opens it, with the week around it.
    val onThisDay = remember(app.history, app.day) {
        val cal = java.util.Calendar.getInstance()
        History.today(app.history, cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.DAY_OF_MONTH))
            ?.takeIf { app.library[it.first.system]?.any { g -> g.key == it.first.key } == true }
    }
    fun thisDay() {
        onThisDay?.let { app.page(it.first.key, app.history.map { h -> h.first.key }, listOf("Timeline", "On this day")) }
    }
    OnKey { k ->
        when (k) {
            Key.Up -> cursor.move(-1)
            Key.Down -> cursor.move(1)
            Key.A -> open()
            Key.X -> surprise()
            Key.Y -> app.push(Screen.Search)
            Key.Select -> if (onThisDay != null) thisDay() else return@OnKey false
            Key.L1 -> resume()
            Key.R1 -> app.push(Screen.ShelfList)
            Key.Start -> app.push(Screen.Menu("settings"))
            else -> return@OnKey false
        }
        true
    }
    Frame(
        header = { Crumbs("Timeline") },
        hints = listOf("A" to "Open", "Y" to "Search", "X" to "Surprise me", "R1" to "Shelves", "START" to "Settings"),
        footer = "${app.total} games",
   ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(72.u())) {
            FocusList(systems.size, cursor, rowHeight = 52, rows = 9, modifier = Modifier.width(300.u())) { i, on ->
                Column(Modifier.fillMaxSize().tap { cursor.set(i) }) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.u())) {
                        T(systems[i].year.toString(), 12, Modifier.width(48.u()), color = Ink.Muted, tracking = 1)
                        T(systems[i].name, 17, color = if (on) Ink.Accent else Ink.Muted,
                            weight = if (on) FontWeight.Bold else FontWeight.Medium, tracking = 2, upper = true, maxLines = 1)
                    }
                    Hairline()
                }
            }
            var rowWidth by remember { mutableIntStateOf(0) }
            val rowPx = rowWidth.takeIf { it > 0 } ?: with(LocalDensity.current) { 780.u().roundToPx() }
            Column(Modifier.weight(1f).onSizeChanged { rowWidth = it.width }) {
                Poster(cursor, systems, onOpen = ::open, onSurprise = ::surprise)
                if (last != null) {
                    Gap(h = 36)
                    Row(Modifier.tap { resume() }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.u())) {
                        val lead = if (app.isOpen(last.key)) "Still open" else "Continue"
                        // The play time, then the console, give way before the name would be cut.
                        val extras = listOfNotNull(Catalog.system(last.system)?.name, app.plays[last.key]?.time?.takeIf { it.isNotEmpty() })
                        val room = extrasThatFit(listOf("L1"), listOf(lead), last.title, extras, rowPx, 12)
                        Keycap("L1")
                        Label(lead, size = 12, color = if (app.isOpen(last.key)) Ink.Accent else Ink.Muted)
                        T(last.title, 20, Modifier.weight(1f, fill = false), color = Ink.Accent, weight = FontWeight.Bold, tracking = -0.3f, maxLines = 1)
                        extras.take(room).forEach { Label(it, size = 12) }
                    }
                }
                if (onThisDay != null) {
                    val (g, date) = onThisDay
                    val ago = app.thisYear - date.take(4).toInt()
                    Gap(h = if (last != null) 14 else 36)
                    Row(Modifier.tap { thisDay() }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.u())) {
                        Keycap("SELECT")
                        // Said once, before the name, so the name keeps the room ("On this day ... 1995, 30 years ago" cut a
                        // fifth of them).
                        val lead = when { ago > 1 -> "$ago years ago today"; ago == 1 -> "A year ago today"; else -> "On this day" }
                        val extras = listOfNotNull(Catalog.system(g.system)?.name)
                        val room = extrasThatFit(listOf("SELECT"), listOf(lead), g.title, extras, rowPx, 12)
                        Label(lead, size = 12)
                        T(g.title, 20, Modifier.weight(1f, fill = false), color = Ink.Cream, weight = FontWeight.Bold, tracking = -0.3f, maxLines = 1)
                        extras.take(room).forEach { Label(it, size = 12) }
                    }
                }
            }
        }
    }
}

/** The big name beside the timeline, with its counts and covers. It is the only part of the home page that follows the highlight. */
@Composable
private fun Poster(cursor: Cursor, systems: List<SystemDef>, onOpen: () -> Unit, onSurprise: () -> Unit) {
    val app = LocalApp.current
    AnimatedContent(
        targetState = cursor.index, label = "poster",
        transitionSpec = { fadeIn(tween(140)) togetherWith fadeOut(tween(90)) },
   ) { idx ->
        val s = systems[idx.coerceIn(0, systems.size - 1)]
        Column {
            // The name sits on the floor of a two-line slot, so everything under it stays put.
            Box(Modifier.fillMaxWidth().height(196.u()), contentAlignment = Alignment.BottomStart) {
                T(s.poster, 104, Modifier.wrapContentHeight(Alignment.Bottom, unbounded = true).scanlines(app.scanlines),
                    weight = FontWeight.Bold, tracking = -3, upper = true, lineHeight = 0.9f)
            }
            Gap(h = 24)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(14.u())) {
                T((app.library[s.id]?.size ?: 0).toString(), 40, weight = FontWeight.Bold, tracking = -1)
                Label("games", Modifier.padding(bottom = 7.u()))
                Gap(w = 14)
                Label(s.year.toString(), Modifier.padding(bottom = 7.u()))
                Gap(w = 14)
                Row(Modifier.padding(bottom = 7.u())) {
                    Label(two(idx + 1), color = Ink.Accent)
                    Label(" / ${systems.size}")
                }
            }
            if (app.homeCovers) {
                Gap(h = 28)
                CoverStrip(s.id)
            }
            Gap(h = 28)
            Row(horizontalArrangement = Arrangement.spacedBy(16.u())) {
                Button("Open", primary = true) { onOpen() }
                Button("Surprise me", primary = false) { onSurprise() }
            }
        }
    }
}

/** A row of covers under a system's name: what has been played and loved first, then the famous ones. */
@Composable
private fun CoverStrip(system: String) {
    val app = LocalApp.current
    val store = app.meta
    val kind = app.coverKind
    val items by produceState(app.homeCache[system] ?: emptyList(), system, store, app.metaTick, kind) {
        if (app.homeCache[system] == null) {
            delay(150)                      // not while the list is still moving
            val found = withContext(Dispatchers.IO) {
                val out = ArrayList<Pair<File, Float>>()
                var width = 0f
                for (g in app.spotlight(system)) {
                    val file = store.cover(g, kind) ?: continue
                    val ratio = Pictures.ratio(file).coerceIn(0.55f, 1.6f)
                    if (width + 120f * ratio > 780f) continue
                    width += 120f * ratio + 12f
                    out += file to ratio
                }
                out
            }
            app.homeCache[system] = found
            value = found
        }
    }
    Row(Modifier.fillMaxWidth().height(120.u()), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.u())) {
        items.forEach { (file, ratio) -> Art(file, ratio, 400, 120) }
    }
}

// ------------------------------------------------------------------------------------- Games

/** What is known about a game: at once if it has been looked up before, otherwise after a short settle. */
@Composable
private fun shown(g: Game, settle: Long): Shown {
    val app = LocalApp.current
    val store = app.meta
    val kind = app.coverKind
    val tick = app.metaTick
    val state = remember(g.key, store, tick, kind) { mutableStateOf(app.shownCache[g.key] ?: Shown()) }
    LaunchedEffect(g.key, store, tick, kind) {
        if (!state.value.loaded) {
            if (settle > 0) delay(settle)   // skip the card while the list is still moving
            val loaded = withContext(Dispatchers.IO) { store.shown(g, kind) }
            app.shownCache[g.key] = loaded
            state.value = loaded
        }
    }
    return state.value
}

private fun players(p: String): String = when {
    p.isBlank() -> ""
    p.trim() == "1" -> "1 player"
    else -> "${p.trim()} players"
}

@Composable
fun GamesScreen(screen: Screen.Games) {
    val app = LocalApp.current
    val bySystem = screen.system != null
    val shelf = screen.shelf?.let(app::shelf)
    val games = remember(screen, app.library, app.sortMode, app.metaTick, app.favorites, app.plays, app.shelves()) { app.gamesFor(screen) }
    val title = if (bySystem) Catalog.system(screen.system!!)?.name ?: "" else shelf?.name ?: ""
    val parent = if (bySystem) "Timeline" else "Shelves"
    if (games.isEmpty()) {
        OnKey { false }
        Frame(header = { Crumbs(parent, title) }, hints = listOf("B" to "Back")) {
            Label(if (shelf?.user == true) "Empty. Add games from any game's options (START)." else "Nothing here yet")
        }
        return
    }
    val memory = "games:" + (screen.system ?: screen.shelf)
    val cursor = rememberCursor(memory, games.size)
    val alphabetical = if (bySystem) app.sortMode == "Title" else shelf?.ordered != true
    // This function never reads where the highlight is. The parts that follow it (the row, the card beside the list,
    // the place counter) each read it themselves, so a step redraws those and nothing else.
    fun now(): Game = games[cursor.index.coerceAtMost(games.size - 1)]
    fun initial(i: Int): Char = games[i].sort.firstOrNull()?.uppercaseChar() ?: '#'
    fun jump(dir: Int) {
        val from = cursor.index
        if (!alphabetical) return cursor.set(from + dir * 9)
        var i = from
        if (dir > 0) {
            while (i < games.size - 1 && initial(i) == initial(from)) i++
        } else {
            while (i > 0 && initial(i) == initial(from)) i--
            while (i > 0 && initial(i - 1) == initial(i)) i--
        }
        cursor.set(i)
    }
    fun details() = app.page(now().key, games.map { it.key }, listOf(parent, title))
    OnKey { k ->
        when (k) {
            Key.Up -> cursor.move(-1)
            Key.Down -> cursor.move(1)
            Key.L1 -> jump(-1)
            Key.R1 -> jump(1)
            Key.A -> app.play(now())
            Key.X -> details()
            Key.Y -> app.toggleFavorite(now())
            Key.Start -> app.push(Screen.Menu("game:" + now().key))
            Key.Select -> {
                if (!bySystem) return@OnKey false
                val next = Sorting.modes[(Sorting.modes.indexOf(app.sortMode) + 1).mod(Sorting.modes.size)]
                app.sortMode = next
                app.prefs.put("sort", next)
                cursor.set(0, quiet = true)
                app.say("Sorted by ${next.lowercase()}.")
            }
            else -> return@OnKey false
        }
        true
    }
    // Every game on this list is looked up once, off to one side, so its card is ready before the highlight arrives.
    val store = app.meta
    val kind = app.coverKind
    LaunchedEffect(games, store, kind, app.metaTick) { app.warmShown(games) }
    val sorted = if (bySystem && app.sortMode != "Title") "  ·  by ${app.sortMode.lowercase()}" else ""
    Frame(
        header = { Crumbs(parent, title) },
        headerRight = { Label("${games.size} games$sorted") },
        hints = emptyList(),
        hintsSlot = {
            val favorite = now().key in app.favorites
            Hint("A", "Play"); Hint("X", "Details"); Hint("Y", if (favorite) "Unfavorite" else "Favorite")
            Hint("L1 R1", if (alphabetical) "Letter" else "Page")
            if (bySystem) Hint("SELECT", "Sort")
            Hint("START", "Options")
        },
        footerSlot = { Label("${cursor.index + 1} / ${games.size}", size = 12) },
   ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(64.u())) {
            Box(Modifier.width(560.u())) {
                // A large quiet mark behind the list: the letter you are in, or your place on an ordered shelf.
                ListMark(cursor, games, alphabetical, Modifier.align(Alignment.TopEnd))
                FocusList(games.size, cursor, rowHeight = 64, rows = 9) { i, on ->
                    Column(Modifier.fillMaxSize().tap { cursor.set(i) }) {
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                            GlideText(games[i].title, on, size = 34, rest = 0.65f)
                        }
                        Hairline()
                    }
                }
            }
            GameCard(cursor, games, shelf, bySystem, Modifier.weight(1f), onDetails = ::details)
        }
    }
}

@Composable
private fun ListMark(cursor: Cursor, games: List<Game>, alphabetical: Boolean, modifier: Modifier) {
    val f = cursor.index
    val mark = if (alphabetical) games[f].sort.firstOrNull()?.uppercase() ?: "" else two(f + 1)
    T(mark, 280, modifier.offset(y = (-40).u()), color = Ink.Line, weight = FontWeight.Bold, tracking = -8, lineHeight = 1f)
}

/** The card beside a list: the highlighted game's box, name, facts and a few lines about it. */
@Composable
private fun GameCard(cursor: Cursor, games: List<Game>, shelf: AppState.Shelf?, bySystem: Boolean, modifier: Modifier, onDetails: () -> Unit) {
    val app = LocalApp.current
    val f = cursor.index
    val g = games[f]
    val info = shown(g, settle = 0)
    // Have the neighbours' pictures decoded before the highlight gets to them, further ahead in the direction of travel.
    val coverPx = with(LocalDensity.current) { 400.u().roundToPx() }
    val store = app.meta
    val kind = app.coverKind
    var before by remember(games) { mutableIntStateOf(f) }
    LaunchedEffect(f, games, store, kind) {
        val dir = if (f < before) -1 else 1
        before = f
        delay(60)
        withContext(Dispatchers.IO) {
            for (d in intArrayOf(dir, 2 * dir, 3 * dir, 4 * dir, -dir, 5 * dir, 6 * dir)) {
                val n = games.getOrNull(f + d) ?: continue
                val s = app.shownCache[n.key] ?: store.shown(n, kind).also { app.shownCache[n.key] = it }
                s.cover?.let { Pictures.warm(app.ctx, it, coverPx) }
            }
        }
    }
    // Every slot here has a fixed height, so nothing shifts as the highlight moves down the list.
    Column(modifier) {
        Cover(info.cover, 360, known = info.loaded, decode = 400)
        Gap(h = 28)
        // The panel's width, measured; until the first measure, the width it has on the 1280 x 960 stage, so the first
        // frame of a list is already right.
        var measuredWidth by remember { mutableIntStateOf(0) }
        val titleWidth = measuredWidth.takeIf { it > 0 } ?: with(LocalDensity.current) { 528.u().roundToPx() }
        Box(Modifier.fillMaxWidth().height(92.u()).onSizeChanged { measuredWidth = it.width }, contentAlignment = Alignment.BottomStart) {
            // The slot is two lines tall; the text measures itself freely so a second line is never dropped for want of a pixel.
            // A name too long for two lines at full size steps down (Desert Demolition Starring Road Runner and Wile E. Coyote).
            val tracking = { s: Int -> -s * 0.027f }
            val title = Titles.forLines(g.title)
            val size = fittingSize(title, intArrayOf(44, 38, 33), titleWidth, FontWeight.Bold, tracking, 1.02f, 2)
            T(title, size, Modifier.wrapContentHeight(Alignment.Bottom, unbounded = true), weight = FontWeight.Bold, tracking = tracking(size), lineHeight = 1.02f, maxLines = 2)
        }
        Gap(h = 16)
        Row(Modifier.fillMaxWidth().height(22.u()).clipToBounds(), horizontalArrangement = Arrangement.spacedBy(24.u())) {
            val m = info.meta
            val facts = listOfNotNull(
                if (!bySystem) Catalog.system(g.system)?.name else null,
                m?.year?.takeIf { it.isNotEmpty() && shelf?.notes?.containsKey(g.key) != true },   // a shelf note carries its own year
                m?.genre?.takeIf { it.isNotEmpty() }?.substringBefore(',')?.trim(),
                m?.let { players(it.players) }?.takeIf { it.isNotEmpty() },
           ).take(3)
            val badges = listOfNotNull(
                if (g.discs.size > 1) "${g.discs.size} discs" to Ink.Accent else null,
                if (g.fanTranslation) "Fan translation" to Ink.Accent else null,
                if (g.pal) "50 Hz" to Ink.Muted else null,
                if (g.key in app.favorites) "Favorite" to Ink.Accent else null,
           )
            // When everything would run past the panel, the facts give way from the end (players, then genre), so a
            // badge such as Fan translation is never the one cut.
            val room = labelsThatFit(facts, badges.map { it.first }, titleWidth, 24)
            facts.take(room).forEach { Label(it) }
            badges.forEach { (text, color) -> Label(text, color = color) }
        }
        Gap(h = 16)
        Box(Modifier.fillMaxWidth().height(96.u())) {       // three lines of the description as drawn (21 px, 1.5 apart)
            val note = shelf?.notes?.get(g.key)
            if (note != null) T(note, 19, color = Ink.Cream, weight = FontWeight.Medium, lineHeight = 1.36f, maxLines = 3)
            else info.meta?.desc?.takeIf { it.isNotEmpty() }?.let {
                // Three lines are shown; there is no call to lay out a whole essay to find them. They end on a whole
                // sentence, or a whole word, rather than part way through one (the game page has the rest).
                val text = if (it.length > 360) it.take(360) else it
                val excerpt = descExcerpt(text, titleWidth)
                T(excerpt, 17, color = Ink.Muted, weight = FontWeight.Normal, lineHeight = 1.5f, maxLines = 3)
            }
        }
        Gap(h = 20)
        // As tall as the Played note at its fullest (three lines), so moving between a played game and an unplayed
        // one does not move the panel.
        Row(Modifier.height(68.u()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.u())) {
            Button("Play", primary = true, min = 150) { app.play(g) }
            Button("Details", primary = false, min = 150) { onDetails() }
            PlayedNote(app.plays[g.key], Modifier.padding(start = 8.u()))
        }
    }
}

// ------------------------------------------------------------------------------------ Detail

@Composable
fun DetailScreen(screen: Screen.Detail) {
    val app = LocalApp.current
    val g = app.find(screen.key)
    if (g == null) {
        LaunchedEffect(Unit) { app.pop() }
        return
    }
    var disc by rememberSaveable(g.key) { mutableIntStateOf(0) }      // kept while Options is open over this page
    var line by rememberSaveable(g.key) { mutableIntStateOf(0) }
    var lines by remember(g.key) { mutableStateOf(0 to 0) }      // lines of description: in all, and on screen
    val tick = app.settingsTick
    val emulatorId = remember(g.key, tick) { app.emulatorFor(g) }
    val plan = remember(emulatorId, g.path) { Runner.plan(app.ctx, emulatorId, g.path) }
    val info = shown(g, settle = 0)
    val system = Catalog.system(g.system)
    val list = app.browse
    val at = list.indexOf(g.key)
    val more = (lines.first - lines.second).coerceAtLeast(0)
    // The last measured sitting stuttered (Stutter): Run lighter is offered on X, until it is on.
    val stutter = remember(g.key, tick) { app.stutterOf(g)?.takeIf { it > Stutter.FLAG } }
    val offerLighter = stutter != null && remember(g.key, tick) { app.canRunLighter(g) && !app.runsLighter(g) }
    // RetroArch is always handed the whole set and changes discs from its own menu; a standalone emulator is handed one disc.
    val pickDisc = g.discs.size > 1 && !emulatorId.startsWith("ra:")
    fun play() = app.play(g, if (pickDisc) g.discs.getOrNull(disc) else null)
    fun step(d: Int) {
        if (at >= 0 && list.size > 1) app.replace(Screen.Detail(list[(at + d).mod(list.size)]))
    }
    OnKey { k ->
        when (k) {
            Key.A -> play()
            Key.Left -> if (pickDisc) disc = (disc - 1).mod(g.discs.size)
            Key.Right -> if (pickDisc) disc = (disc + 1).mod(g.discs.size)
            Key.Up -> line = (line - 3).coerceAtLeast(0)
            Key.Down -> line = (line + 3).coerceAtMost(more)
            Key.L1 -> step(-1)
            Key.R1 -> step(1)
            Key.Y -> app.toggleFavorite(g)
            Key.X -> if (offerLighter) { app.toggleLighter(g); app.say("${g.title} runs lighter from its next fresh start.") } else return@OnKey false
            Key.Start -> app.push(Screen.Menu("game:" + g.key))
            else -> return@OnKey false
        }
        true
    }
    val favorite = g.key in app.favorites
    val hints = buildList {
        add("A" to "Play")
        add("Y" to if (favorite) "Unfavorite" else "Favorite")
        if (offerLighter) add("X" to "Run lighter")
        if (pickDisc) add("◀ ▶" to "Disc")
        if (more > 0) add("▲ ▼" to "Read")
        if (at >= 0 && list.size > 1) add("L1 R1" to "Previous, next")
        add("START" to "Options")
    }
    // The place in the list only: while discs are being chosen, the lit disc button says which one.
    val place = if (at >= 0 && list.size > 1) "${at + 1} / ${list.size}" else ""
    Frame(
        header = { Crumbs(*(app.browseFrom + (if (g.title.length > 34) g.title.take(33).trimEnd() + "…" else g.title)).toTypedArray()) },
        hints = hints,
        footer = place,
   ) {
        Row(Modifier.fillMaxSize().padding(vertical = 28.u()), horizontalArrangement = Arrangement.spacedBy(56.u())) {
            // Left: the box, and under it the game itself.
            Column(Modifier.width(400.u()), verticalArrangement = Arrangement.spacedBy(16.u())) {
                val cover = info.cover
                val coverHeight = if (cover == null) 400f else minOf(400f, 400f / info.coverRatio.coerceIn(0.4f, 3f))
                if (cover != null) Art(cover, info.coverRatio, 400, 400) else Cover(null, 400, known = info.loaded)
                info.shot?.let { Art(it, info.shotRatio, 400, (728f - coverHeight - 16f).toInt().coerceAtLeast(120), pixelArt = true) }
            }
            var columnWidth by remember { mutableIntStateOf(0) }
            val density = LocalDensity.current
            val fullPx = columnWidth.takeIf { it > 0 } ?: with(density) { 696.u().roundToPx() }
            Column(Modifier.weight(1f).fillMaxHeight().onSizeChanged { columnWidth = it.width }) {
                val m = info.meta
                Row(horizontalArrangement = Arrangement.spacedBy(24.u())) {
                    // Genre, then year, give way before Favorite or Finished would be cut.
                    val facts = listOfNotNull(system?.name, m?.year?.takeIf { it.isNotEmpty() }, m?.genre?.takeIf { it.isNotEmpty() }?.substringBefore(',')?.trim())
                    val marks = listOfNotNull(if (favorite) "Favorite" else null, if (g.key in app.finished) "Finished" else null)
                    facts.take(labelsThatFit(facts, marks, fullPx, 24)).forEach { Label(it) }
                    marks.forEach { Label(it, color = Ink.Accent) }
                }
                Gap(h = 18)
                val size = when {
                    g.title.length <= 10 -> 104
                    g.title.length <= 18 -> 76
                    g.title.length <= 30 -> 56
                    else -> 44
                }
                T(Titles.forLines(g.title), size, Modifier.scanlines(app.scanlines), weight = FontWeight.Bold, tracking = -size * 0.028f,
                    upper = true, lineHeight = 0.92f, maxLines = 3)
                Gap(h = 18)
                val format = File(g.discs.firstOrNull() ?: g.path).extension.uppercase()
                val facts = buildList {
                    m?.released?.takeIf { it.isNotEmpty() }?.let { add("Released" to it) }
                    m?.developer?.takeIf { it.isNotEmpty() }?.let { add("Developer" to it) }
                    m?.publisher?.takeIf { it.isNotEmpty() }?.let { add("Publisher" to it) }
                    m?.players?.takeIf { it.isNotEmpty() }?.let { add("Players" to it) }
                    m?.rating?.takeIf { it >= 0f }?.let { add("Rating" to "${(it * 10).toInt() / 1f} / 10".replace(".0 ", " ")) }
                    if (g.region.isNotEmpty()) add("Region" to g.region)
                    if (g.fanTranslation) add("Text" to "Fan translation")
                    if (g.pal) add("Speed" to "50 Hz release")
                    if (g.system == "arcade" && g.stem.lowercase() in RetroPack.uprightSets && app.retroManaged)
                        app.look().upright.takeIf { it != "Keep it level" }?.let { add("Hold it" to it) }
                    val file = listOf(if (g.discs.size > 1) "${g.discs.size} × $format" else format, Sizes.bytes(info.bytes)).filter { it.isNotEmpty() }
                    if (file.isNotEmpty()) add("On the card" to file.joinToString("  ·  "))
                }.take(8)
                // Two facts to a row; one too long for half the width ("Sony Computer Entertainment") takes a row of its
                // own instead of being cut, and the next short fact moves up beside the one left waiting for a partner.
                val measurer = rememberTextMeasurer(cacheSize = 24)
                val keyStyle = textStyle(12, Ink.Muted, FontWeight.Bold, 3, 0f)
                val valueStyle = textStyle(19, Ink.Cream, FontWeight.Medium, 0, 0f)
                val gapPx = with(density) { 40.u().roundToPx() }
                val spacePx = with(density) { 16.u().roundToPx() }
                val rows = remember(facts, fullPx) {
                    fun w(text: String, style: androidx.compose.ui.text.TextStyle) = measurer.measure(text, style, maxLines = 1, softWrap = false).size.width
                    val half = (fullPx - gapPx) / 2
                    val out = ArrayList<Pair<Boolean, MutableList<Pair<String, String>>>>()
                    var waiting: MutableList<Pair<String, String>>? = null
                    for (f in facts) {
                        if (w(f.first.uppercase(), keyStyle) + spacePx + w(f.second, valueStyle) > half) { out += true to mutableListOf(f); continue }
                        val open = waiting
                        if (open != null) { open += f; waiting = null } else mutableListOf(f).also { out += false to it; waiting = it }
                    }
                    out
                }
                Column {
                    rows.forEach { (full, pair) ->
                        Row(horizontalArrangement = Arrangement.spacedBy(40.u())) {
                            pair.forEach { (k, v) ->
                                Column(Modifier.weight(1f)) {
                                    Row(Modifier.fillMaxWidth().height(42.u()), verticalAlignment = Alignment.CenterVertically) {
                                        Label(k, size = 12)
                                        Gap(w = 16)
                                        T(v, 19, Modifier.weight(1f), maxLines = 1, align = TextAlign.End)
                                    }
                                    Hairline()
                                }
                            }
                            if (!full && pair.size == 1) Box(Modifier.weight(1f))
                        }
                    }
                }
                Gap(h = 16)
                val desc = m?.desc.orEmpty()
                if (desc.isNotEmpty()) Reader(desc, line, 18, 1.45f, Modifier.weight(1f).fillMaxWidth()) { total, visible -> lines = total to visible }
                else Spacer(Modifier.weight(1f))
                // Favorites and Finished are left out: the marks at the top already say so.
                val on = remember(g.key, app.shelves()) { app.shelvesWith(g).filter { it.id != "favorites" && it.id != "finished" }.map { it.name } }
                if (on.isNotEmpty()) {
                    Gap(h = 16)
                    Row(horizontalArrangement = Arrangement.spacedBy(16.u())) {
                        T("Shelves", 12, color = Ink.Muted, weight = FontWeight.Bold, tracking = 3, upper = true, lineHeight = 1.6f, maxLines = 1)
                        // Up to two lines: a game can sit on six shelves. Each name stays whole and a dot ends a line
                        // rather than starting one.
                        T(on.joinToString("\u00A0\u00A0·  ") { it.replace(' ', '\u00A0') }, 12, Modifier.weight(1f), color = Ink.Cream,
                            weight = FontWeight.Bold, tracking = 3, upper = true, lineHeight = 1.6f, maxLines = 2)
                    }
                }
                val controls = remember(g.key, emulatorId, tick) {
                    val taught = app.prefs.int("m1.code", 0) > 0 || app.prefs.int("m2.code", 0) > 0
                    ControlsNote.of(g.system, g.base, emulatorId, app.look().dolphinPad,
                        if (taught && app.retroManaged) app.look().extras[g.system] ?: Hotkeys.usual(g.system) else null)
                }
                if (controls.isNotEmpty()) {
                    Gap(h = 14)
                    Row(horizontalArrangement = Arrangement.spacedBy(16.u())) {
                        Label("Controls", Modifier.padding(top = 3.u()), size = 12)
                        T(controls, 15, Modifier.weight(1f), color = Ink.Muted, lineHeight = 1.35f, maxLines = 5)   // the DS's runs to five with M1 and M2 taught
                    }
                }
                if (g.discs.size > 1 && !pickDisc) {
                    Gap(h = 16)
                    Label("${g.discs.size} discs, one save. Change discs in RetroArch's menu, under Disc Control.", size = 12)
                }
                if (pickDisc) {
                    Gap(h = 16)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.u())) {
                        g.discs.indices.forEach { i ->
                            val lit = i == disc
                            Box(
                                Modifier.height(48.u()).widthIn(min = 110.u()).border(1.u(), if (lit) Ink.Accent else Ink.Line)
                                    .tap { disc = i }.padding(horizontal = 18.u()),
                                contentAlignment = Alignment.Center,
                           ) { Label("Disc ${i + 1}", color = if (lit) Ink.Accent else Ink.Muted, size = 12) }
                        }
                    }
                }
                Gap(h = 20)
                Row(Modifier.height(68.u()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.u())) {
                    Button("Play", primary = true) { play() }
                    PlayedNote(app.plays[g.key], always = true)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.u())) {
                        Label("Plays in", size = 11)
                        Label(
                            (plan?.emulator ?: "No emulator") + if (plan?.installed == false) "  ·  not installed" else "",
                            color = if (plan?.installed == true) Ink.Cream else Ink.Muted, size = 12,
                       )
                        if (stutter != null) Label("Stuttered last time: ${Math.round(stutter)}% late", color = Ink.Accent, size = 12)
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------------------------------------- Shelves

@Composable
fun ShelfListScreen() {
    val app = LocalApp.current
    val shelves = app.shelves()
    if (shelves.isEmpty()) {
        OnKey { false }
        Frame(header = { Crumbs("Shelves") }, hints = listOf("B" to "Back")) { Label("Shelves fill in as games arrive") }
        return
    }
    val cursor = rememberCursor("shelves", shelves.size, initial = 0)
    val f = cursor.index
    var armed by remember { mutableStateOf("") }
    LaunchedEffect(f) { armed = "" }
    fun go(i: Int) = cursor.set(i)
    fun here() = shelves[cursor.index.coerceAtMost(shelves.size - 1)]
    fun section(dir: Int) {
        var i = cursor.index
        val group = shelves[i].group
        if (dir > 0) {
            while (i < shelves.size - 1 && shelves[i].group == group) i++
            if (shelves[i].group == group) return
        } else {
            while (i > 0 && shelves[i].group == group) i--
            while (i > 0 && shelves[i - 1].group == shelves[i].group) i--
        }
        cursor.set(i)
    }
    OnKey { k ->
        when (k) {
            Key.Up -> cursor.move(-1)
            Key.Down -> cursor.move(1)
            Key.L1 -> section(-1)
            Key.R1 -> section(1)
            Key.A -> app.push(Screen.Games(shelf = here().id))
            Key.X -> {
                val s = here()
                if (!s.user) return@OnKey false
                if (armed == s.id) {
                    app.deleteShelf(s.name)
                    app.say("Removed. The games are still in the library.")
                } else {
                    armed = s.id
                    app.say("Press X again to remove this shelf.")
                }
            }
            else -> return@OnKey false
        }
        true
    }
    val s = shelves[f]
    Frame(
        header = { Crumbs("Shelves", s.group) },
        hints = buildList {
            add("A" to "Open")
            add("L1 R1" to "Section")
            if (s.user) add("X" to "Remove shelf")
            add("B" to "Back")
        },
        footer = "${f + 1} / ${shelves.size}",
   ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(64.u())) {
            FocusList(shelves.size, cursor, rowHeight = 72, rows = 7, modifier = Modifier.width(540.u())) { i, on ->
                Box(Modifier.fillMaxSize().tap { go(i) }, contentAlignment = Alignment.CenterStart) {
                    GlideText(shelves[i].name, on, size = 40, rest = 0.62f, upper = true, tracking = -1.4f)
                }
            }
            AnimatedContent(
                targetState = f, modifier = Modifier.weight(1f), label = "shelf",
                transitionSpec = { fadeIn(tween(140)) togetherWith fadeOut(tween(90)) },
           ) { idx ->
                val shelf = shelves[idx.coerceIn(0, shelves.size - 1)]
                Column {
                    Box(Modifier.fillMaxWidth().height(96.u()), contentAlignment = Alignment.BottomStart) {      // three lines as drawn
                        T(shelf.blurb, 20, color = Ink.Cream, weight = FontWeight.Medium, lineHeight = 1.4f, maxLines = 3)
                    }
                    Gap(h = 20)
                    Row(horizontalArrangement = Arrangement.spacedBy(24.u())) {
                        Label(if (shelf.games.size == 1) "1 game" else "${shelf.games.size} games", color = Ink.Accent)
                    }
                    Gap(h = 8)
                    shelf.games.take(8).forEachIndexed { n, g ->
                        Row(Modifier.fillMaxWidth().height(50.u()), verticalAlignment = Alignment.CenterVertically) {
                            if (shelf.ordered) T(two(n + 1), 12, Modifier.width(36.u()), color = Ink.Muted, tracking = 1)
                            T(g.title, 21, Modifier.weight(1f).padding(end = 16.u()), tracking = -0.4f, maxLines = 1)
                            Label(Catalog.system(g.system)?.name ?: "", size = 11)
                        }
                        Hairline()
                    }
                    Gap(h = 14)
                    Label(if (shelf.games.size > 8) "and ${shelf.games.size - 8} more" else " ", size = 12)
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------- Menus

class Item(
    val label: String,
    val value: String = "",
    val note: String = "",
    val onA: (() -> Unit)? = null,
    val onStep: ((Int) -> Unit)? = null,
    val mark: Boolean? = null,          // a step in a list of steps: done, or still to do
)

/** [focus] is the row to rest on when the page opens and whenever that row changes; [footer] replaces the row count. */
class MenuDef(
    val crumbs: List<String>, val items: List<Item>, val hints: List<Pair<String, String>>? = null,
    val focus: Int? = null, val footer: String? = null,
)

private fun AppState.cycle(key: String, options: List<String>, dir: Int) {
    val now = prefs.str(key, options.first())
    prefs.put(key, options[(options.indexOf(now).coerceAtLeast(0) + dir).mod(options.size)])
    touchSettings()
}

/**
 * Android's System page, the nearest the Nova's Settings app opens to its navigation choice (Gestures, then System
 * navigation); read on the Nova. Its own "Navigation bar" page only orders the three buttons.
 */
private val NAVIGATION_PAGES = arrayOf("com.android.settings/.Settings\$SystemDashboardActivity")

private fun AppState.choice(label: String, key: String, options: List<String>, note: String = "", apply: () -> Unit = {}) =
    Item(label, prefs.str(key, options.first()), note, onStep = { d -> cycle(key, options, d); apply() })

private fun AppState.toggle(label: String, key: String, default: Boolean, note: String = "", apply: (Boolean) -> Unit = {}) =
    Item(label, if (prefs.bool(key, default)) "On" else "Off", note, onStep = {
        val v = !prefs.bool(key, default)
        prefs.put(key, v)
        apply(v)
        touchSettings()
    })

/** The emulator a system will use: the one chosen, else the first one installed, else the recommended one. */
private fun AppState.chosen(s: SystemDef): String =
    prefs.str("emu." + s.id).takeIf { it in s.emulators }
        ?: s.emulators.firstOrNull { Runner.plan(ctx, it, "/x")?.installed == true } ?: s.emulators.first()

private fun settings(vararg more: String) = listOf("Settings") + more

private fun menu(app: AppState, id: String): MenuDef = when {
    id.startsWith("game:") -> {
        val g = app.find(id.removePrefix("game:"))
        if (g == null) MenuDef(listOf("Options"), emptyList()) else {
            val plan = Runner.plan(app.ctx, app.emulatorFor(g), g.path)
            val on = app.userShelves.count { g.key in it.keys }
            MenuDef(listOf(g.title.take(36), "Options"), listOf(
                Item("Favorite", if (g.key in app.favorites) "Yes" else "No",
                    "Favorites get a shelf of their own at the top of Shelves.", onStep = { app.toggleFavorite(g) }),
                Item("Finished", if (g.key in app.finished) "Yes" else "No",
                    "Seen to the end. Finished games have a shelf of their own.", onStep = { app.toggleFinished(g) }),
                Item("Add to a shelf", if (on == 0) "" else if (on == 1) "On 1 shelf" else "On $on shelves",
                    "Put this game on a shelf of your own, or start a new one.", onA = { app.push(Screen.Menu("shelve:" + g.key)) }),
                *(if (g.discs.size > 1 && app.stillOpen == g.key && app.emulatorFor(g).startsWith("ra:") && app.device.bridge) arrayOf(Item("Next disc", "",
                    "Only when the game asks for its next disc: it comes back with the disc tray opened, the next of its ${g.discs.size} discs " +
                        "put in and the tray closed, as on the console. Changed at any other moment the game stops, as a console would.",
                    onA = { app.nextDisc(g) })) else emptyArray()),
                Item("Saves", app.saveIndex.firstOrNull { it.first == g.key }?.second?.let { Saves.day(it, System.currentTimeMillis()) } ?: "",
                    "This game's own save, the place you left off, earlier versions and the copy on the card.",
                    onA = { app.lookAtSaves(g); app.push(Screen.Menu("saves:" + g.key)) }),
                *(if (app.canRunLighter(g)) arrayOf(Item("Run lighter", if (app.runsLighter(g)) "Yes" else "No",
                    "Draws this one game at its console's own resolution instead of the sharper picture, for a game that stutters. Off brings the sharp picture back. Either way it applies from the game's next fresh start.",
                    onStep = { app.toggleLighter(g) })) else emptyArray()),
                *(if (g.system in setOf("gc", "wii") && app.canRunLighter(g) && !app.look().dolphinGl) arrayOf(Item("Drawn with",
                    if (app.drawsWithGl(g.key)) "OpenGL" else "Vulkan",
                    "Vulkan is the usual way. A game that closes as soon as it opens is switched to OpenGL by itself, and stays there once it runs. Left and right switch it by hand.",
                    onStep = { app.toggleGl(g) })) else emptyArray()),
                Item("Emulator", (plan?.emulator ?: "None") + if (plan?.installed == false) "  ·  not installed" else "",
                    "Left and right change what this one game opens in. The choice for the whole system is under Settings, Emulators.",
                    onStep = { d -> app.cycleEmulator(g, d) }),
                Item("Scrape this game", "", "Fetch its pictures and details again, as set under Settings, Scraper.", onA = {
                    app.scrape.start(listOf(g), app.scrapeOptions())
                    app.push(Screen.Scraping)
                }),
                Item("File", g.file, "Its name on the card, inside the ${g.system} folder."),
                Item("Hide this game", "", "Takes it out of every list, shelf and search. Nothing is deleted: Settings, Library, Hidden games brings it back.",
                    onA = {
                        if (app.hide(g)) {
                            app.pop()                                               // these options
                            if (app.current is Screen.Detail) app.pop()             // and its page, which would show a game no longer listed
                            app.say("${g.title} is hidden. Settings, Library, Hidden games brings it back.")
                        }
                    }),
           ))
        }
    }
    id == "hidden" -> {
        val games = app.hiddenGames()
        MenuDef(settings("Library", "Hidden games"), if (games.isEmpty()) listOf(Item("Nothing is hidden", "", "A game is hidden from its options: START on the game, then Hide this game."))
            else games.map { g ->
                Item(g.title, Catalog.system(g.system)?.name ?: "", "A brings it back to its lists and shelves.",
                    onA = { app.unhide(g.key); app.say("${g.title} is back.") })
            })
    }
    id.startsWith("saves:") -> {
        val g = app.find(id.removePrefix("saves:"))
        // What is shown was read from the device and the card away from the screen (asked for as the page opens, and
        // again after anything on it is used); until the first answer is in, the page says it is looking.
        val s = g?.let { app.saveDetails[it.key] }
        if (g == null) MenuDef(listOf("Saves"), emptyList())
        else if (s == null) MenuDef(listOf(g.title.take(36), "Saves"), listOf(Item("Looking at its saves", "", "")), hints = listOf("B" to "Back"))
        else {
            val now = System.currentTimeMillis()
            fun day(ms: Long) = Saves.day(ms, now)
            val rows = ArrayList<Item>()
            when (s.where) {
                "RetroArch" -> {
                    val own = s.own.filter { Saves.kind(it.name) == Saves.Kind.SAVE }
                    val clock = if (s.clock) " This game has a clock. It runs on the device's clock, and keeps time while the device is off." else ""
                    rows += Item("The game's own save", own.maxOfOrNull { it.modified }?.let(::day) ?: "None yet",
                        (if (own.isEmpty()) "Made when you save inside the game, as its cartridge or memory card did."
                        else "What the game itself saved, as its cartridge or memory card did (${Sizes.bytes(own.sumOf { it.size })}). It reaches storage within seconds of saving.") + clock)
                    rows += Item("Where you left off", s.place?.let { day(it.modified) } ?: "Nothing kept",
                        if (s.place == null) "Leave a game with START and SELECT and your exact place is kept. Starting it again picks up there."
                        else "Your exact place from when you left with START and SELECT. Starting the game picks up there.")
                    if (s.place != null) rows += Item("Start from the game's own save instead", "",
                        "Sets your place aside, so the game next opens at its title screen and loads its own save. The place is kept, and can be taken back from this page.",
                        onA = { app.startFromOwnSave(g) })
                    s.aside?.let { aside ->
                        rows += Item("Go back to the place set aside", day(aside.modified),
                            "A place from an earlier sitting, set aside by you or because the game's own save was newer. Press A and the game next picks up there.",
                            onA = { app.useAsidePlace(g) })
                    }
                    if (s.slots > 0) rows += Item("Save states made in RetroArch's menu", s.slots.toString(),
                        "Made and loaded from RetroArch's own menu (hold START). They are copied to the card with everything else.")
                }
                "Dolphin" -> rows += Item("Saved in Dolphin", if (s.own.isEmpty()) "Nothing on the card yet" else day(s.newest),
                    "GameCube games save to a memory card and Wii games to the console's own storage, both inside Dolphin. " +
                        if (app.dolphinGranted()) "The launcher copies them to the card after every game." + (if (s.own.isEmpty()) "" else " ${Sizes.bytes(s.own.sumOf { it.size })} on the card.")
                        else "The launcher can copy them once it has been let into Dolphin's folder (First-time setup, Dolphin's settings).")
                else -> {
                    val label = Runner.plan(app.ctx, app.emulatorFor(g), g.path)?.emulator ?: "its emulator"
                    rows += Item("Kept inside $label", "", when (app.emulatorFor(g)) {
                        "armsx2" -> "PlayStation 2 memory cards live in ARMSX2's own storage, where no other app can read them. ARMSX2 backs a card up itself each time a game starts. For a copy on the card, see Settings, Saves."
                        "hakux" -> "Xbox saves live on the console's hard disk, which hakuX keeps in its own storage. No other app can read it, so there is no copy on the card."
                        else -> "This emulator keeps its saves in its own storage, where the launcher cannot read them."
                    })
                }
            }
            if (s.where.isNotEmpty()) {
                rows += Item("Copy on the card", if (s.onCard > 0L) day(s.onCard) else "Not yet",
                    "Made after every game, in _timeline/saves on the card. Press A to copy now.", onA = { app.backUpSaves(announce = true) })
                if (s.moments.isEmpty()) rows += Item("Earlier versions", "None yet",
                    "Each time a save changes, the one before it is kept on the card: the last six, then one a day, up to twenty.")
                else {
                    // Two versions written in the same minute are told apart by their seconds.
                    val shown = s.moments.take(Saves.MOST)
                    val twice = shown.groupingBy { day(it) }.eachCount()
                    for (m in shown) rows += Item("Earlier save", if ((twice[day(m)] ?: 0) > 1) Saves.day(m, now, seconds = true) else day(m),
                    "Press A to put this one back. The save in use now is kept first, so it can be undone.", onA = { app.push(Screen.Menu("saveback:$m:${g.key}")) })
                }
            }
            MenuDef(listOf(g.title.take(36), "Saves"), rows, hints = listOf("A" to "Select", "B" to "Back"))
        }
    }
    id.startsWith("saveback:") -> {
        val rest = id.removePrefix("saveback:")
        val moment = rest.substringBefore(':').toLongOrNull() ?: 0L
        val g = app.find(rest.substringAfter(':'))
        if (g == null) MenuDef(listOf("Saves"), emptyList()) else MenuDef(listOf(g.title.take(36), "Saves", "Put back"), listOf(
            Item("Put back the save from ${Saves.day(moment, System.currentTimeMillis())}", "",
                "The save in use now is kept as an earlier version first. The place you left off is set aside, so the game starts from this save. Both can be taken back from the page before this one.",
                onA = { app.pop(); app.putBackSave(g, moment) }),
            Item("Leave it as it is", "", "Nothing changes.", onA = { app.pop() }),
       ), hints = listOf("A" to "Select", "B" to "Back"), focus = 1)
    }
    id == "saves" -> {
        val now = System.currentTimeMillis()
        val n = app.saveIndex.size
        MenuDef(settings("Saves"), listOf(
            Item("Games with saves", if (n == 0) "None yet" else n.toString(),
                "Every game with a save or a place kept, newest first. Open one to see its saves, its earlier versions and the copy on the card.",
                onA = { if (n == 0) app.say("No game has a save yet.") else app.push(Screen.Menu("savelist")) }),
            Item("Copied to the card", (app.savedAt.takeIf { it > 0L }?.let { Saves.day(it, now) } ?: "Not yet") +
                    app.prefs.int("saves.failed", 0).let { if (it == 0) "" else if (it == 1) "  ·  1 could not be copied" else "  ·  $it could not be copied" },
                "Saves are copied to _timeline/saves on the card after every game, and each time the launcher starts. Press A to copy now." +
                    if (app.prefs.int("saves.failed", 0) > 0) " Last time some could not be copied: the card may be full, or was taken out part-way." else "",
                onA = { app.backUpSaves(announce = true) }),
            Item("Bring saves back from the card", "",
                "For a new or wiped device: copies saves from the card where this device has none, or an older one. It never replaces a newer save, and anything it does replace is kept as an earlier version.",
                onA = { app.restoreSaves() }),
            app.toggle("Copy saves to the card", "saves.backup", true,
                "After every game. Each time a save changes, the one before it is kept as well: the last six, then one a day, up to twenty for each game."),
            app.toggle("Include places and save states", "saves.states", true,
                "Where you left off in each game, and save states made in RetroArch's menu. They are far larger, so two earlier versions of each are kept." +
                    if (app.prefs.bool("saves.room", true)) "" else " The card has under 1 GB free, so these are waiting; the games' own saves are still copied."),
            app.toggle("Keep your place when you leave", "ra.resume", true,
                "Leaving a game with START and SELECT keeps your exact place; GameCube and Wii keep it when left with Home. Starting it again picks up there. " +
                    "Closing a GameCube or Wii game from Dolphin's own menu starts it fresh next time; games on more than one disc always start fresh. " +
                    "PlayStation 2 saves where you are every five minutes " +
                    "and when you close a game from its menu, and picks up there."),
            Item("Write the game's own save every", app.prefs.int("ra.sram", 10).let { if (it == 0) "Only on exit" else "$it seconds" },
                "In-game saves are copied to storage while you play, so they survive a flat battery or a closed app.", onStep = { d ->
                    val steps = listOf(0, 10, 30, 60)
                    app.prefs.put("ra.sram", steps[(steps.indexOf(app.prefs.int("ra.sram", 10)).coerceAtLeast(0) + d).mod(steps.size)])
                    app.touchSettings()
                }),
            Item("GameCube and Wii", if (app.dolphinGranted()) "Copied too" else "Not yet",
                if (app.dolphinGranted()) "Dolphin's memory cards, Wii saves and Miis are copied to the card with the rest, with earlier versions kept the same way."
                else "Dolphin keeps its saves in its own folder. The launcher copies them once it has been let in: First-time setup, Dolphin's settings."),
            Item("PlayStation 2", if (app.device.bridge) "Copied too" else app.ps2BackedUp.takeIf { it > 0L }?.let { Saves.day(it, now) } ?: "Kept by ARMSX2",
                if (app.device.bridge) "Both of ARMSX2's memory cards are copied to the card whenever ARMSX2 is closed, with earlier versions kept like any other save. " +
                    "A backup file made in ARMSX2's own settings and left in Downloads is copied too, the newest two."
                else "ARMSX2 keeps memory cards in its own storage, where no other app can read them, and backs a card up itself each time a game starts. " +
                    "For a copy on the card: in ARMSX2's settings choose Back up app data and save the file in Downloads. The launcher copies it to the card by itself and keeps the newest two."),
            Item("Xbox", if (app.device.bridge) "Copied too" else "Kept by hakuX",
                if (app.device.bridge) "Xbox saves live on the console's hard disk, which hakuX keeps in its own storage. The whole disk is copied to the card " +
                    "whenever hakuX is closed and the disk has changed, with two earlier versions kept. It is hundreds of MB, so it waits when the card is nearly full."
                else "Xbox saves live on the console's hard disk, which hakuX keeps in its own storage. No other app can read it, so there is no copy on the card. " +
                    "Clearing hakuX's storage or uninstalling it removes them."),
       ))
    }
    id == "savelist" -> {
        val now = System.currentTimeMillis()
        MenuDef(settings("Saves", "Games with saves"), app.saveIndex.mapNotNull { (key, at) ->
            app.find(key)?.let { g -> Item(g.title, Saves.day(at, now), Catalog.system(g.system)?.name ?: "", onA = { app.lookAtSaves(g); app.push(Screen.Menu("saves:" + g.key)) }) }
        }, hints = listOf("A" to "Open", "B" to "Back"))
    }
    id.startsWith("shelve:") -> {
        val g = app.find(id.removePrefix("shelve:"))
        if (g == null) MenuDef(listOf("Shelves"), emptyList()) else MenuDef(
            listOf(g.title.take(36), "Add to a shelf"),
            listOf(Item("New shelf", "", "Name a shelf and put this game on it.", onA = {
                app.draft = ""
                app.naming = { name -> app.createShelf(name, g)?.let { app.say("Added to $it.") } }
                app.push(Screen.Naming("New shelf"))
            })) + app.userShelves.map { u ->
                Item(u.name, if (g.key in u.keys) "On this shelf" else "",
                    if (u.keys.size == 1) "1 game on it." else "${u.keys.size} games on it.", onStep = { app.toggleShelf(u.name, g) })
            },
            hints = listOf("A" to "Add or remove", "B" to "Back"),
       )
    }
    id == "scraper" -> MenuDef(settings("Scraper"), listOf(
        app.choice("Image source", "scrape.image", ScrapeOptions.images,
            "The picture shown in lists, and the one fetched for each game. Box 3D and Mix show pictures your library already has from ES-DE or RetroBat; a scrape fetches the box, a screenshot or the title screen."),
        app.choice("Arcade marquees", "scrape.logo", ScrapeOptions.logos, "The marquee over each arcade cabinet, from the Arcade Database."),
        app.toggle("Scrape ratings", "scrape.ratings", true, "Arcade games only: the Arcade Database's rating."),
        app.toggle("Scrape videos", "scrape.videos", false, "Arcade games only. Videos are large."),
        app.choice("Games to scrape for", "scrape.filter", listOf("All games", "Only missing media")),
        app.choice("Systems included", "scrape.systems", listOf("All systems") + app.systems().map { it.name }),
        Item("Scrape now", app.scrapeList().size.let { if (it == 1) "1 game" else "$it games" },
            "Results are written to the _timeline folder beside your games, in the layout ES-DE and RetroBat use.", onA = {
                val games = app.scrapeList()
                if (games.isEmpty()) app.say("Nothing to scrape with these filters.")
                else {
                    app.scrape.start(games, app.scrapeOptions())
                    app.push(Screen.Scraping)
                }
            }),
   ))
    id == "emulators" -> MenuDef(settings("Emulators"), listOf(
        Item("First-time setup", "", "What is installed, what is missing, and the one thing each emulator needs on first run.",
            onA = { app.push(Screen.Menu("setup")) }),
        app.toggle("Nintendo DS on two screens", "nds.two", true,
            "With the dual-screen attachment (or any second display) connected, DS games open in melonDS: the top screen there, the touch " +
                "screen on this panel. Each game's save goes with it between melonDS and RetroArch. Needs melonDS, which setup installs."),
   ) + app.systems().map { s ->
        val chosen = app.chosen(s)
        val plan = Runner.plan(app.ctx, chosen, "/x")
        Item(s.name, (plan?.emulator ?: chosen) + if (plan?.installed == true) "" else "  ·  not installed",
            if (s.emulators.size > 1) "Left and right change it. The first choice is the one Timeline sets up for this system." else "The one emulator for this system.",
            onStep = { d ->
                app.prefs.put("emu." + s.id, s.emulators[(s.emulators.indexOf(chosen) + d).mod(s.emulators.size)])
                app.touchSettings()
            })
    })
    id == "files" -> consoleFilesPage(app)
    id == "about" -> aboutPage(app)
    id == "setup" -> {
        val steps = app.setupSteps()
        val left = FirstRun.left(steps)
        MenuDef(settings("First-time setup"),
            steps.map { s -> Item(s.label, s.value, s.note, onA = { app.setupAct(s.id) }, mark = s.done) },
            hints = listOf("A" to "Do this step", "B" to "Back"),
            focus = FirstRun.next(steps),
            footer = (if (app.total == 1) "1 game on the card" else "${app.total} games on the card") + "  ·  " + if (left == 0) "all set" else "$left to go")
    }
    id == "library" -> MenuDef(settings("Library"), listOf(
        Item("Sort games by", app.sortMode, "How each system's list is ordered. SELECT changes it from inside a list too. Shelves keep their own order.", onStep = { d ->
            app.sortMode = Sorting.modes[(Sorting.modes.indexOf(app.sortMode) + d).mod(Sorting.modes.size)]
            app.prefs.put("sort", app.sortMode)
        }),
        app.choice("Open on", "startOn", listOf("Timeline", "Last system", "Shelves"), "Where the launcher lands when the device starts."),
        Item("Games folder", app.romsRoot?.absolutePath ?: "Not found", "Left and right switch between cards, if more than one has a ROMs folder.", onStep = { d ->
            val found = Storage.romCandidates(app.ctx)
            if (found.size > 1) {
                val i = found.indexOfFirst { it.absolutePath == app.romsRoot?.absolutePath }
                app.prefs.put("romsRoot", found[(i + d).mod(found.size)].absolutePath)
                app.rescan()
            }
        }),
        Item("Scraped data folder", app.meta.dir.absolutePath, "Pictures, descriptions, play history and your own shelves live here, so they travel with the card."),
        Item("Games found", app.total.toString()),
        Item("Hidden games", app.hiddenGames().size.let { if (it == 0) "None" else it.toString() },
            "Games taken out of the lists from their options. Nothing is deleted; any of them can come back.", onA = { app.push(Screen.Menu("hidden")) }),
        Item("Space on the card", app.romsRoot?.let { "${Sizes.bytes(it.usableSpace)} free of ${Sizes.bytes(it.totalSpace)}" } ?: ""),
        Item("Look again", "", "Reads the card afresh. This also happens by itself every time the launcher comes to the front.",
            onA = { app.rescan(); app.say("Library refreshed.") }),
   ))
    id == "display" -> MenuDef(settings("Display"), listOf(
        Item("Accent color", Hsv.hex(Ink.Accent.toArgb()), "The one color in the interface. Tune it to taste, or go back to gold.",
            onA = { app.push(Screen.Accent) }),
        app.toggle("True black", "trueBlack", true, "Unlit pixels on an OLED panel. Off uses a very dark gray instead.") { app.trueBlack = it },
        app.toggle("Scanlines on headlines", "scanlines", true, "Fine dark lines across the large type only.") { app.scanlines = it },
        Item("Wallpaper", "", "Press A to make Android's own wallpaper plain black: it shows behind the list of open apps, the lock screen and the moment between apps.",
            onA = { app.blackWallpaper() }),
        app.panel.let { pn ->
            Item("Color", if (pn == null) "Looking" else if (pn.calibrated) "Calibrated" else "Stock",
                if (pn != null && !pn.compatible) (if (pn.calibrated) pn.reason + " Stock takes the calibration out." else pn.reason) else
                    "Calibrated is pippopapera's measurement of the Nova's own panel (sRGB, D65 white, gamma 2.2): an average error " +
                    "of 0.6 instead of 5.8, so reds and greens look as they were made instead of running hot. Stock is the panel as " +
                    "shipped, with Android's 10% extra saturation and Retroid's cool tint, which Calibrated turns off. A change shows " +
                    "after a restart.",
                onStep = { if (pn != null && (pn.compatible || pn.calibrated)) app.setDisplayColor(!pn.calibrated) })
        },
   ) + (if (app.panelPending) listOf(Item("Restart to finish", "", "The new color takes effect when the Nova starts again. Press A to restart it now.",
        onA = { app.restartDevice() })) else emptyList()) + listOf(
        app.toggle("Covers on the home page", "homeCovers", true, "A row of box art under each system's name.") { app.homeCovers = it },
        app.toggle("24-hour clock", "clock24", false) { app.clock24 = it },
        Item("Brightness", if (app.brightness > 0) "${(app.brightness * 100 + 254) / 255}%" else "",
            if (app.device.bridge) "Left and right, a step at a time. Android's automatic brightness is switched off when you use it." else "Not offered by this device.",
            onStep = { d -> if (app.device.bridge) app.stepBrightness(d) }),
        Item("Rest screen stays lit", app.restAlways,
            "The rest screen is the dim drifting clock. Always keeps it lit at a low light instead of the screen going dark, and it is " +
                "also what you see when you wake the Nova from the launcher. Lit, it draws a little battery every hour; " +
                "While charging keeps it lit only on the charger.", onStep = { d ->
                val m = app.restModes
                app.restAlways = m[(m.indexOf(app.restAlways).coerceAtLeast(0) + d).mod(m.size)]
                app.prefs.put("rest.always", app.restAlways)
            }),
        Item("Rest screen after", if (app.idleMinutes == 0) "Never" else "${app.idleMinutes} min",
            "A dim clock that drifts a little each minute, so nothing stays lit in one place.", onStep = { d ->
                val steps = listOf(1, 3, 5, 10, 0)
                app.idleMinutes = steps[(steps.indexOf(app.idleMinutes).coerceAtLeast(0) + d).mod(steps.size)]
                app.prefs.put("idleMinutes", app.idleMinutes)
            }),
        Item("Refresh rate", "${app.refreshRate.toInt()} Hz", "The fastest mode the panel offers is asked for automatically."),
        Item("Television", if (app.tvHz == "60") "60 Hz" else "Its own choice",
            (if (app.tvNow.isEmpty()) "No television connected now. " else "Showing ${app.tvNow}. ") +
                "Docked, games are set for 60 Hz, which every television shows. 60 Hz also asks a television showing " +
                "another rate for 60, at a size the dock already carries (Retroid devices); its own choice leaves it as it " +
                "is. The phosphors are drawn twice as wide on a television under 4K, so they stay even when the dock " +
                "scales the picture up.",
            onStep = { _ -> app.setTv(if (app.tvHz == "60") "auto" else "60") }),
   ))
    id == "sound" -> {
        val curve = Eq.parse(app.prefs.str("eq.curve"))
        fun save(next: List<Int>) {
            app.prefs.put("eq.curve", Eq.format(next))
            SoundService.sync(app.ctx)
            app.touchSettings()
        }
        val names = Eq.presets.keys.toList()
        MenuDef(settings("Sound"), listOf(
            app.toggle("Interface sounds", "feel.sounds", true,
                "A soft tick as the highlight moves, and a short note going into a page or a game and coming back out. They follow the media volume.") { Feel.sounds = it },
            app.toggle("Speaker tuning", "eq.on", SoundService.nova,
                "A tone shape applied to everything the device plays, kept in place by a small service that restarts with the device. " +
                    "Now: ${SoundService.state}.") { SoundService.sync(app.ctx) },
            Item("Shape", Eq.name(curve), (if (SoundService.nova) "Neutral is the correction measured on a Retroid Pocket Nova with a microphone: nothing below what " +
                "the speakers can play, a little more of their lowest notes, the boxy sound around 700 Hz and the sharp one at 5 kHz " +
                "taken down, and the top end lifted where they fall away. No bass boost. "
                else "Neutral leaves the sound as the device makes it. ") +
                "Warmer and Brighter lean one way or the other. Any change below makes it your own.", onStep = { d ->
                save(Eq.presets.getValue(names[(names.indexOf(Eq.name(curve)).coerceAtLeast(if (d > 0) -1 else 0) + d).mod(names.size)]))
            }),
       ) + Eq.bands.indices.map { i ->
            val hz = Eq.bands[i].let { if (it >= 1000) "${it / 1000}.${(it % 1000) / 100} kHz".replace(".0 ", " ") else "$it Hz" }
            Item("${Eq.names[i]}  ·  $hz", Eq.label(curve[i]), "Your own adjustment on top of the tuning. Left and right move it half a decibel at a time. Play the test sound and listen as you go.", onStep = { d ->
                save(curve.toMutableList().also { it[i] = (it[i] + d * 5).coerceIn(Eq.MIN, Eq.MAX) })
            })
        } + listOf(
            Item("Play a test sound", "", "Four seconds of soft noise across the whole range, through whatever is connected.", onA = { TestSound.play() }),
            app.toggle("Even out loud and quiet", "eq.compress", false,
                "A gentle compressor in three ranges: quiet sounds come up a little and loud ones down a little. For noisy places. " +
                    "Off, the game's own dynamics reach the speakers as made; the amplifiers still protect them either way.") { SoundService.sync(app.ctx) },
            app.toggle("Only through the speakers", "eq.speakersOnly", true,
                "Rests the shape while headphones, Bluetooth or HDMI are connected, since it is tuned for the built-in speakers.") { SoundService.sync(app.ctx) },
            app.toggle("Same shape inside RetroArch", "eq.retroarch", false,
                "Only if the system-wide tuning does not seem to reach RetroArch: this writes its shape as a RetroArch audio filter instead (without the compressor or limiter). Needs Set up RetroArch for me. Do not use both, or the shaping doubles."),
       ))
    }
    id == "picture" -> {
        val pkg = listOf("com.retroarch.aarch64", "com.retroarch", "com.retroarch.ra32").firstOrNull { Runner.installed(app.ctx, it) }
        val paths = pkg?.let { Runner.retroPaths(app.ctx, it) }
        val fast = app.refreshRate >= 119f
        MenuDef(settings("Picture"), listOf(
            app.toggle("Set up RetroArch for me", "ra.managed", true,
                "The launcher writes scaling, shaders, motion and saving for every RetroArch system and starts RetroArch with them. " +
                    "Off leaves RetroArch exactly as you set it yourself."),
            app.choice("Television consoles: screen", "ra.crt", RetroPack.crtChoices,
                "The CRT look for NES, SNES, Genesis, TurboGrafx, Atari and arcade. GDV Mini Ultra is the default. All five are light."),
            Item("Signal", "", "How each console reached the television: composite, S-video or RGB, in front of the CRT. Set to what most American homes had.",
                onA = { app.push(Screen.Menu("signal")) }),
            app.choice("Television consoles: motion", "ra.motion", RetroPack.motionChoices,
                (if (fast) "" else "Needs a 120 Hz panel; this one is at ${app.refreshRate.toInt()}, so it is skipped. ") +
                    "Beam simulator lights each frame for half the time, as a tube's phosphors did. " +
                    "Black frames is the plain version. Both are dimmer, and 50 Hz games skip them."),
            app.choice("3D consoles: motion", "ra.motion3d", RetroPack.motionChoices,
                (if (fast) "" else "Needs a 120 Hz panel; this one is at ${app.refreshRate.toInt()}, so it is skipped. ") +
                    "The beam for PlayStation, Saturn and Dreamcast; Nintendo 64 gets black frames, since it draws in a " +
                    "way the beam cannot follow. Games that run at 30 or 20 frames a second show a faint double image in " +
                    "motion, as they did on a tube. 50 Hz games skip it."),
            app.choice("Handhelds: screen", "ra.handheld", RetroPack.handheldChoices,
                "As the console: each screen as it was. The Game Boy's green dots of ink over their reflector, with its smear; the Game Boy Color's " +
                    "washed-out colors and pixel shape; the Game Boy Advance's dark, unlit screen; the Game Gear's slow backlit one at its own wider shape. " +
                    "LCD3x and LCD grid are plain grids over the game's colors."),
            app.toggle("Game Boy: pixel transparency", "ra.transparency", true,
                "With LCD3x or LCD grid: the Game Boy and Game Boy Color had no backlight, so white and pale pixels let the screen show through. " +
                    "This draws them that way, with a faint shadow under the picture. As the console draws it already."),
            app.choice("Upright arcade games", "ra.upright", RetroPack.uprightChoices,
                "Pac-Man, Galaga, 1943, DoDonPachi and the other games made for a screen stood on end fill the panel when you turn the device. " +
                    "The picture, both sticks, the d-pad and the face buttons all turn with it, for those games only. If it comes up upside down, pick the other side."),
            app.toggle("Whole-number scaling", "ra.integer", true,
                "Every game pixel becomes the same number of screen pixels. 240-line consoles fill this panel exactly at four times."),
            app.toggle("3D consoles: sharper than the console", "ra.sharp", false,
                "Off is the true picture: PlayStation and Dreamcast at their own resolution, PlayStation with its dithering and wobble, and 480-line " +
                    "pictures without gaps between their lines, as a television showed them. On draws PlayStation and Dreamcast at 960 lines with " +
                    "clean edges and no wobble, keeps Nintendo 64's extra detail across, and draws lines on 480-line pictures, like a monitor."),
            app.toggle("3D consoles: CRT look", "ra.crt3d", true,
                "PlayStation, Saturn, Nintendo 64, Dreamcast, GameCube and Wii through the same CRT as the television consoles, with scanlines at each " +
                    "console's own number of lines and the extra detail kept in them. Off draws them clean."),
            app.choice("PlayStation and Dreamcast: drawn with", "ra.draw", listOf("Vulkan", "Compatible"),
                "Vulkan is the fast, sharp way. Compatible draws PlayStation in software at the console's own resolution and Dreamcast through OpenGL: " +
                    "slower to look at, but it asks less of the graphics chip. The launcher switches to it by itself if Vulkan fails to start."),
            app.choice("GameCube and Wii: drawn with", "dol.draw", listOf("Vulkan", "OpenGL"),
                if (app.dolphinGranted()) "Which of Dolphin's two renderers is used. Vulkan is the one to keep; GPU texture decoding, which " +
                    "crashes some games with it, is kept off."
                else "Takes effect once the launcher has been let into Dolphin's settings (First-time setup, Dolphin's settings)."),
            app.toggle("GameCube and Wii: controls set by the launcher", "dol.pad", true,
                "This device's buttons and sticks are mapped inside Dolphin before each game: a GameCube pad, and a Wii Remote with Nunchuk (left stick moves, " +
                    "right stick points, R1 shakes). Off leaves the controls to whatever you map in Dolphin yourself."),
            app.choice("Television white", "ra.white", RetroPack.whiteChoices,
                "The white the television showed. American sets were sold bluer than the broadcast standard, about 8000 K on average; " +
                    "Japanese sets, the ones these games were made on, ran at 9300 K. 6500 K is the studio standard. Arcade monitors and the Dreamcast's VGA monitor keep their own 9300 K."),
            Item("Color boost", "${app.prefs.int("ra.saturation", 100)}%", "100 is each console's own color. More lifts it under the GDV screen, past what a television showed.", onStep = { d ->
                app.prefs.put("ra.saturation", (app.prefs.int("ra.saturation", 100) + d * 5).coerceIn(100, 130))
                app.touchSettings()
            }),
            app.toggle("Rewind on 8 and 16-bit", "ra.rewind", false, "Hold the rewind hotkey to run a game backwards. Costs a little speed and memory."),
            app.choice("Lag reduction on 8 and 16-bit", "ra.lag", RetroPack.lagChoices,
                "Preemptive frames has the game answer a button a frame sooner (a sixtieth of a second) by quietly re-running the last frame only when a button changes. " +
                    "Run-ahead does the same on every frame and costs more. For the cartridge consoles and the handhelds."),
            Item("Shader collection", if (app.shadersReady(paths)) "In place" else "Not there yet",
                if (paths == null) "RetroArch is not installed yet."
                else "The screens above need RetroArch's shader collection. Press A to fetch it from libretro (about 55 MB).",
                onA = { app.ensureShaders(announce = true) }),
            Item("Write the setup now", "", "Happens by itself before a game starts. This does it straight away, for example before running the shader update.",
                onA = { app.say(app.writeRetroSetupNow()) }),
       ))
    }
    id == "signal" -> {
        fun signal(group: String, label: String, note: String) =
            app.choice(label, "ra.signal.$group", RetroPack.signals.getValue(group), note)
        MenuDef(settings("Picture", "Signal"), listOf(
            signal("nes", "NES", "The NES came with RF and composite only. Composite: soft color fringes and the shimmer of dot crawl. RF is the switch box in the carton, with its noise."),
            signal("snes", "SNES", "Composite, as most American homes had it: its hi-res see-through effects, as in Kirby's Dream Land 3, blend as they did. S-video needed a cable and a set few had."),
            signal("genesis", "Genesis, Sega CD and Master System", "Composite turns the Genesis's dithered stripes into the see-through water and shadows its artists drew for, with the rainbow in Sonic's waterfalls."),
            signal("tg16", "TurboGrafx-16", "The American TurboGrafx had RF only, offered as it was; composite is the nearest clean version of it."),
            signal("atari2600", "Atari 2600", "RF only on the console; composite is the nearest clean version of it."),
            signal("arcade", "Arcade", "Arcade monitors took RGB straight from the board: sharp and clean, and seen close enough that their mask shows."),
            signal("psx", "PlayStation", "Composite, the cable in the box: its dithering blends into the smooth shading the games were drawn for. S-video and RGB are sharper and show the dither."),
            signal("n64", "Nintendo 64", "S-video, the best an American Nintendo 64 could do (it had no RGB). Composite, the cable in the box, is softer."),
            signal("saturn", "Saturn", "Composite: its mesh see-through effects, made of dots, blend into the transparency they were made for."),
            signal("dreamcast", "Dreamcast", "VGA, the VGA box on a monitor: 480 crisp progressive lines, and the console is told the box is in (a game that refuses it falls back to composite by itself). S-video and composite as on a television."),
            signal("gcwii", "GameCube and Wii", "Component, the best cable they had: 480 sharp progressive lines where the game offers them, on a tube seen up close, its lines and phosphor grille in view. S-video and composite send them interlaced, with the games' own blending against flicker."),
            signal("ps2", "PlayStation 2", "Component, the best cable it had: 480 sharp lines on a tube seen up close, its lines and phosphor grille in view. S-video and composite, the cable in the box, are softer."),
       ))
    }
    id == "battery" -> {
        val b = BatteryInfo.read(app.ctx)
        val held = ChargeCare.held(app.ctx)
        MenuDef(settings("Device", "Battery"), listOf(
            Item("Health", b.health?.let { "$it%" } ?: "",
                if (b.fullMah != null && b.designMah != null) "It holds ${b.fullMah} mAh now, against ${b.designMah} mAh when new. A battery loses a little each year; " +
                    "under 80% it is worn." else "This device does not report it."),
            Item("Charge cycles", b.cycles?.toString() ?: "", "Each full charge's worth used counts as one."),
            Item("Temperature", b.tempC?.let { "${it.toInt()}°C" } ?: "",
                "Charging slows itself when the battery is warm. With Heat guard on (Settings, Device), the fan steps up at 45°C while a game runs."),
            Item("Charging care", when { !b.plugged -> "Not charging"; held -> "Holding at 80%"; else -> "Charging normally" },
                if (held) "Resting on the charger, so Retroid's limit is holding it at 80% (or not topping it up above). A button or a game and it charges on to 100%."
                else "Smart holds the battery at 80% after half an hour resting on the charger, and charges on to 100% once you pick it up."),
            Item("Voltage", b.volts?.let { "%.2f V".format(java.util.Locale.US, it) } ?: "", "Its highest is 4.5 V, when full."),
       ))
    }
    id == "android" -> {
        val nav = Runner.navigationMode(app.ctx)
        MenuDef(settings("Android settings"), listOf(
            Item("Navigation", when (nav) { 0 -> "3-button"; 1 -> "2-button"; 2 -> "Gestures"; else -> "" },
                "Android's own setting, set by you. With 3-button navigation, swipes from the screen's edges do nothing; Retroid keeps " +
                    "the bar itself hidden, so nothing appears. A opens Android's System page: Gestures, System navigation, " +
                    "3-button navigation, then press Home.",
                onA = { if (!Runner.openSettingsPage(app.ctx, *NAVIGATION_PAGES)) Runner.openSettings(app.ctx, Settings.ACTION_SETTINGS) }),
            Item("Screen lock", if (Runner.lockHasCode(app.ctx)) "Has a code" else "No code",
                "Android's own setting, set by you. None: the launcher's rest clock is the sleep screen, and a game woken from sleep " +
                    "comes straight back. A opens Android's page for it.",
                onA = { Runner.openSettings(app.ctx, android.app.admin.DevicePolicyManager.ACTION_SET_NEW_PASSWORD) }),
            Item("All of Android's settings", "", "Wi-Fi, Bluetooth, controllers, storage.", onA = { Runner.openSettings(app.ctx, Settings.ACTION_SETTINGS) }),
       ))
    }
    id == "device" -> {
        val d = app.device
        val none = "Not offered by this device."
        MenuDef(settings("Device"), listOf(
            Item("This device", d.model.ifEmpty { "Unknown" },
                if (d.bridge) "The maker's settings service was found, so the controls below work. Each is off until you switch it on, and each was measured on a Retroid Pocket Nova."
                else "The maker's settings service was not found here, so lights, fan and power stay with Android."),
            app.choice("Stick lights", "dev.lights", listOf("Leave alone", "Off", "Match the accent"),
                if (d.canLight) "Match the accent lights both sticks in the launcher's color." else none) { app.deviceHome() },
            app.choice("Lights while playing", "dev.lightsPlay", listOf("Same", "Off", "The console's color"),
                if (d.canLight) "Off saves a little battery. The console's color gives each system its own: green for Nintendo 64, orange for Dreamcast." else none),
            Item("Light brightness", "${app.prefs.int("dev.lightLevel", 60)}%", if (d.canLight) "" else none, onStep = { step ->
                app.prefs.put("dev.lightLevel", (app.prefs.int("dev.lightLevel", 60) + step * 10).coerceIn(10, 100))
                app.deviceHome()
                app.touchSettings()
            }),
            app.toggle("Power and fan by system", "dev.power", false,
                if (d.canPower) "Sets the maker's power and fan modes to suit each system when a game starts, and Standard power with the Smart fan in the launcher. " +
                    "Fan now: ${Vendor.fanName(d.fan)}. Power now: ${Vendor.performanceName(d.performance)}." +
                    listOfNotNull(d.chip.takeIf { !it.isNaN() }?.let { "chip ${it.toInt()}°C" }, d.battery.takeIf { !it.isNaN() }?.let { "battery ${it.toInt()}°C" })
                        .takeIf { it.isNotEmpty() }?.joinToString(", ", prefix = " Now: ", postfix = ".").orEmpty() else none) { on ->
                // On: what was set before is remembered. Off: it is put back, with Retroid's own link from power to fan.
                if (d.canPower) app.powerSwitched(on)
            },
            Item("Profiles by system", "", "Light, Balanced, Strong or High for each system.", onA = { app.push(Screen.Menu("power")) }),
            app.toggle("Heat guard", "dev.guard", false,
                if (d.canPower) "While a game runs, reads the chip's and the battery's temperature every ten seconds. The fan steps up a mode at 80°C on the chip " +
                    "(it would slow itself at 95°C) or 45°C on the battery, and back down once both have cooled. " +
                    "Only the maker's Quiet, Smart and Sport, never off and never a raw speed. Needs Power and fan by system." else none),
            Item("Battery", "", "Its health, charge cycles and temperature, and what charging care is doing now.", onA = { app.push(Screen.Menu("battery")) }),
            app.choice("Charging", "dev.charge", ChargeHold.modes,
                if (d.canHoldCharge) "Smart: put down on the charger for half an hour, the handheld stops at 80%, or stops topping up if it is already above. " +
                    "Pick it up or start a game and it charges on to 100%. Left alone at full, the charger would keep the battery at its highest voltage for as long as it sits there. " +
                    "Retroid's own 80% limit does the holding; it comes off when the charger does." else none) { app.applyCharge() },
            app.toggle("Set up ARMSX2 for me", "ps2.manage", false,
                if (d.bridge) "Before each PlayStation 2 game, sets ARMSX2's autosave to follow Keep your place and keeps the 480-line look in its shaders folder. " +
                    "Off, ARMSX2 is left exactly as you set it." else none),
            Item("Check again", "", "Looks for the maker's service and settings afresh, reads the temperatures, and applies the choices above.", onA = { app.recheckDevice() }),
       ))
    }
    id == "power" -> MenuDef(settings("Device", "Profiles by system"), app.systems().map { s ->
        val p = app.powerFor(s.id)
        Item(s.name, p.label, p.note, onStep = { step ->
            app.prefs.put("power." + s.id, Power.entries[(p.ordinal + step).mod(Power.entries.size)].label)
            app.touchSettings()
        })
    }, hints = listOf("◀ ▶" to "Change", "B" to "Back"))
    id == "controls" -> {
        fun code(which: String) = app.prefs.int("$which.code", 0).let { if (it > 0) "Button $it" else "Not taught yet" }
        val waiting = app.learning
        MenuDef(settings("Controls"), listOf(
            app.toggle("Vibration on scroll", "feel.haptics", true, "A light tick of the motor each time the highlight moves.") { Feel.haptics = it },
            app.toggle("Swap confirm and back", "swapConfirm", false, "For pads that put confirm on the right-hand face button.") { app.swapConfirm = it },
            Item("Teach M1", if (waiting == "m1") "Press M1 now" else code("m1"),
                "Press A, then press the M1 button on the top edge. The launcher remembers what it sends and gives it a job inside RetroArch. B backs out.",
                onA = { app.learning = "m1" }),
            Item("Teach M2", if (waiting == "m2") "Press M2 now" else code("m2"),
                "Press A, then press the M2 button on the top edge.", onA = { app.learning = "m2" }),
            Item("What they do, by system", "", "Rewind and fast-forward on cartridge systems, the RetroArch menu and fast-forward elsewhere, unless you choose otherwise.",
                onA = { app.push(Screen.Menu("extra")) }),
            Item("Other emulators", "", "ARMSX2, Dolphin, melonDS and Flycast have their own hotkey pages: once taught here, M1 and M2 can be picked there like any other button."),
       ))
    }
    id == "extra" -> MenuDef(settings("Controls", "Extra buttons"), app.systems().filter { s -> s.emulators.any { it.startsWith("ra:") } }.map { s ->
        val (a, b) = app.extraButtons(s.id)
        Item(s.name, "M1 ${a.lowercase()}  ·  M2 ${b.lowercase()}", "Press A to change what the two buttons do on this system.", onA = { app.push(Screen.Menu("extra:" + s.id)) })
    }, hints = listOf("A" to "Change", "B" to "Back"))
    id.startsWith("extra:") -> {
        val system = id.removePrefix("extra:")
        val (a, b) = app.extraButtons(system)
        fun step(which: String, now: String, d: Int) {
            app.prefs.put("$which.$system", Hotkeys.actions[(Hotkeys.actions.indexOf(now) + d).mod(Hotkeys.actions.size)])
            app.touchSettings()
        }
        MenuDef(settings("Controls", Catalog.system(system)?.name ?: system), listOf(
            Item("M1 does", a, "Left and right change it. Rewind switches rewinding on for this system.", onStep = { d -> step("m1", a, d) }),
            Item("M2 does", b, "Left and right change it.", onStep = { d -> step("m2", b, d) }),
       ), hints = listOf("◀ ▶" to "Change", "B" to "Back"))
    }
    id == "achievements" -> MenuDef(settings("RetroAchievements"), listOf(
        app.toggle("RetroAchievements", "ra.cheevos", false,
            "Earn achievements in RetroArch games with a free account from retroachievements.org. Typing the name and password below switches it on. Needs Wi-Fi, and Set up RetroArch for me."),
        Item("Account name", app.prefs.str("ra.cheevosUser").ifEmpty { "Not set" }, "Your RetroAchievements user name.",
            onA = { app.textEntry = TextEntry("RetroAchievements account name", "ra.cheevosUser", secret = false) }),
        Item("Password", when {
                app.prefs.str("ra.cheevosPass").isNotEmpty() -> "Saved"
                app.prefs.str("ra.cheevosSigned").let { it.isNotEmpty() && it.equals(app.prefs.str("ra.cheevosUser").trim(), ignoreCase = true) } -> "Signed in"
                else -> "Not set"
            },
            "Typed here once. RetroArch signs in with it at the next game and keeps a login token; then the password is forgotten, here and in RetroArch's own settings. Type it again only if RetroArch says the login has expired.",
            onA = { app.textEntry = TextEntry("RetroAchievements password", "ra.cheevosPass", secret = true) }),
        app.toggle("Hardcore mode", "ra.hardcore", false,
            "The site's stricter rules: no save states, no rewind, no slow motion. Leave off to keep save-and-resume."),
        Item("Other emulators", "", "Dolphin and ARMSX2 sign in to RetroAchievements from their own settings."),
   ))
    id == "unread" -> MenuDef(listOf("Did not open"), listOf(
        Item("Back to my games", "", "${app.unread} did not open: its disc image could not be read, so only the console's own start screen came up. " +
            "The file is probably damaged, or was cut short when it was copied. Copy it to the card again.", onA = { app.pop() }),
   ))
    id == "held" -> {
        val h = app.holding
        // Named from the file if the game has left the library since (a rescan), so no answer reads "Play  now".
        fun named(key: String) = app.find(key)?.title ?: Titles.pretty(Titles.stem(key.substringAfter('/')))
        val open = h?.open?.takeIf { it.isNotEmpty() }?.let { named(it) } ?: ""
        val wanted = h?.let { named(it.wanted) } ?: ""
        val emu = h?.emulator ?: "The emulator"
        val how = h?.leave?.takeIf { it.isNotEmpty() }?.let { " ($it)" } ?: " (from its own menu)"
        // The open game is named once, in the header, so each answer has room for the game wanted
        // ("Keep my place in X, then play Y" was cut for about two pairs in five).
        val shown = open.ifEmpty { if (h?.memory == true) emu else "" }
        val heading = if (shown.length > 34) shown.take(33).trimEnd() + "…" else shown
        MenuDef(listOfNotNull("Still open", heading.takeIf { it.isNotEmpty() }), if (h == null) emptyList() else if (h.pkg.startsWith("com.retroarch")) listOf(
            Item("Keep my place, then play $wanted", "", "$open comes back as you left it. Press START and SELECT there: your place is kept, and $wanted starts by itself.",
                onA = { app.closeThenPlay() }),
            Item("Play $wanted now", "", "$open closes without keeping your place in it. Whatever was saved inside the game itself is safe.",
                onA = { app.playAnyway() }),
            Item("Go back to $open", "", "It is where you left it.", onA = { app.backToHeld() }),
       ) else if (h.memory && h.open.isNotEmpty()) listOfNotNull(
            Item("Close it, then play $wanted", "", "$wanted needs the memory $emu is holding with $open in it. Press A and $open comes up. " +
                "Save there if you want to, close it$how, and $wanted starts by itself.", onA = { app.closeThenPlay() }),
            if (h.canClose && app.device.bridge) Item("Close it without saving", "", "$emu is closed as it is and $wanted starts. Whatever was saved inside $open is safe; " +
                "anything since is lost.", onA = { app.closeHeldAndPlay() }) else null,
            Item("Play $wanted anyway", "", "$emu is left as it is. With two big emulators in memory the Nova can stall.", onA = { app.playAnyway() }),
            Item("Go back to $open", "", "It is where you left it.", onA = { app.backToHeld() }),
            Item("It is closed already", "", "For when $open was closed some other way. Starts $wanted now.", onA = { app.heldIsClosed() }),
       ) else if (h.memory) listOfNotNull(
            if (h.canClose && app.device.bridge) Item("Close $emu, then play $wanted", "", "$emu is still running, and a game may be open in it. " +
                "It is closed as it is: anything not saved inside it is lost.", onA = { app.closeHeldAndPlay() }) else null,
            Item("Play $wanted anyway", "", "$emu is left as it is. With two big emulators in memory the Nova can stall.", onA = { app.playAnyway() }),
            Item("Go back to $emu", "", "Opens it, to save and close whatever is in it from its own menu.", onA = { app.backToHeld() }),
       ) else listOf(
            Item("Close it, then play $wanted", "", "$emu keeps the game it has until that game is closed from inside it. Press A and $open comes up. " +
                "Close it there$how, and $wanted starts by itself.", onA = { app.closeThenPlay() }),
            Item("Go back to $open", "", "It is where you left it.", onA = { app.backToHeld() }),
            Item("It is closed already", "", "For when $open was closed some other way. Starts $wanted now.", onA = { app.heldIsClosed() }),
       ), hints = listOf("A" to "Select", "B" to "Back"), focus = 0)      // a question: it always opens on its first answer, never where it was left
    }
    id == "stuck" -> {
        val parts = app.stuck.split('\n')
        val title = parts.getOrElse(0) { "" }
        val why = parts.getOrElse(1) { "" }
        // The launcher has already switched these two systems to the other way of drawing: say so.
        val switched = parts.getOrElse(3) { "" } == "switched"
        val also = if (switched) "PlayStation and Dreamcast have been switched to the compatible way of drawing, so it should open next time (Picture changes that back). " else ""
        MenuDef(listOf("Did not open"), listOf(
            Item("Close RetroArch", "", "$title did not open. $why ${also}RetroArch is left on a black screen behind the launcher and no longer answers. " +
                "In a moment Android will say it isn't responding: choose Close app. If no notice comes, press A here, choose Force stop, and come back with B.",
                onA = { Runner.openSettings(app.ctx, Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:" + app.stuckPackage()) }),
            Item("Back to my games", "", "Everything that does not use RetroArch still starts. A RetroArch game will bring this page back until RetroArch has been closed.",
                onA = { app.pop() }),
            Item("It is closed already", "", "For when RetroArch was closed some other way and this page has not noticed. RetroArch games start again.",
                onA = { app.probablyClosed(announce = false) }),
       ), hints = listOf("A" to "Select", "B" to "Back"), focus = 0)
    }
    else -> MenuDef(settings(), (FirstRun.left(app.setupSteps()).takeIf { it > 0 }?.let { left ->
        listOf(Item("First-time setup", if (left == 1) "1 step to go" else "$left steps to go",
            "The few things only you can do: the home screen, the emulators, and one look inside each.", onA = { app.push(Screen.Menu("setup")) }))
    } ?: emptyList()) + listOf(
        Item("Display", "", "Color, brightness, the rest screen, accent color, clock.", onA = { app.readPanel(); app.push(Screen.Menu("display")) }),
        Item("Picture", "", "CRT and LCD screens, motion clarity, scaling and detail, all set up ahead of time.", onA = { app.push(Screen.Menu("picture")) }),
        Item("Saves", "", "Every game's saves, the copies on the card, and earlier versions to go back to.", onA = { app.refreshSaveIndex(); app.push(Screen.Menu("saves")) }),
        Item("Sound", "", "Interface sounds, and a tone shape for the built-in speakers that stays put.", onA = { app.push(Screen.Menu("sound")) }),
        Item("Library", "", "Sorting, where the launcher opens, folders and space.", onA = { app.push(Screen.Menu("library")) }),
        Item("Emulators", "", "Which emulator each system opens in, and the setup checklist.", onA = { app.push(Screen.Menu("emulators")) }),
        Item("Device", "", "Stick lights, power and fan by system, heat guard.", onA = { app.push(Screen.Menu("device")) }),
        Item("Close everything in the background", "", "Closes every emulator still open behind the launcher, for a clean start. If a game is open in one, " +
            "the first press names it and a second press closes it without saving. hakuX is left alone: close its game from its own menu, " +
            "since stopping it mid-write could damage the Xbox hard disk.", onA = { app.closeBackground() }),
        Item("RetroAchievements", "", "Achievements in RetroArch games, with your own account.", onA = { app.push(Screen.Menu("achievements")) }),
        Item("Scraper", "", "Fetch pictures and details for the games on the card.", onA = { app.push(Screen.Menu("scraper")) }),
        Item("Controls", "", "Vibration on scroll, swap confirm and back, and the two extra buttons.", onA = { app.push(Screen.Menu("controls")) }),
        Item("Android apps", "", "Everything else installed on the device.", onA = { app.push(Screen.Apps) }),
        Item("Android settings", "", "Navigation and screen lock, then Wi-Fi, Bluetooth, controllers, storage.", onA = { app.push(Screen.Menu("android")) }),
        Item("Make this the home screen", if (Runner.isHome(app.ctx)) "Done" else "",
            "Choose this app as the home app and the device boots straight to it.", onA = { if (Runner.isHome(app.ctx)) Runner.openSettings(app.ctx, Settings.ACTION_HOME_SETTINGS) else app.requestHome() }),
        Item("About Timeline", Runner.version(app.ctx), "The version, the source code, and the people whose work it is built on.", onA = { app.push(Screen.Menu("about")) }),
        Item("About NOSTOS", "", "The retro game store that made Timeline.", onA = { app.push(Screen.Nostos) }),
   ))
}

/** Settings, First-time setup, Console files: what each console needs from the console itself, and what is in place. */
private fun consoleFilesPage(app: AppState): MenuDef {
    val findings = ConsoleFiles.check(app.romsRoot, app.cardLibrary.filter { it.value.isNotEmpty() }.keys)
    val root = app.romsRoot?.absolutePath ?: "ROMs"
    val rows = findings.map { f ->
        val name = Catalog.system(f.system)?.name ?: f.system
        Item(name + if (f.optional) "  ·  optional" else "",
            when (f.state) {
                ConsoleFiles.State.CHECKED -> "Checked  ·  ${f.name}"
                ConsoleFiles.State.FOUND -> "Found  ·  ${f.name}"
                ConsoleFiles.State.MISSING -> "Missing"
            },
            f.file.note + " " + when (f.state) {
                ConsoleFiles.State.CHECKED -> "This copy matches a known good one."
                ConsoleFiles.State.FOUND -> "Not a copy Timeline knows the checksum of. It may well work; if games don't start, it is the first thing to check."
                ConsoleFiles.State.MISSING -> "Put it in $root/${f.folder}."
            }, mark = f.state != ConsoleFiles.State.MISSING)
    }
    return MenuDef(settings("First-time setup", "Console files"), rows + Item("How to dump your own", "",
        "These files come from the console itself, and Timeline never offers them. The guide shows how to copy them from consoles you own. Press A to open it.",
        onA = { if (!Links.open(app.ctx, Links.CONSOLE_FILES)) app.say("No browser to open the guide in.") }),
        footer = if (rows.isEmpty()) "Nothing needed" else "${findings.count { it.state != ConsoleFiles.State.MISSING }} of ${findings.size} in place")
}

/** Settings, About Timeline: the version, the source, and every piece of other people's work inside it. */
private fun aboutPage(app: AppState): MenuDef = MenuDef(settings("About Timeline"), listOf(
    Item("Timeline", Runner.version(app.ctx), "A home screen for retro handhelds. Copyright (C) 2026 NOSTOS, LLC. Free software under the " +
        "GNU General Public License, version 3 or any later version, offered as is, with no warranty."),
    Item("Source code", Links.SOURCE_SHORT, "Everything Timeline is made of, open to read, build and change. Press A to open it.",
        onA = { if (!Links.open(app.ctx, Links.SOURCE)) app.say("No browser to open it in.") }),
    Item("Space Grotesk", "SIL Open Font License", "The typeface, by Florian Karsten and the Space Grotesk Project Authors."),
    Item("CRT beam simulator", "MIT", "By Mark Rejhon of Blur Busters and Timothy Lottes. Dreamcast's steady version is adapted from it."),
    Item("GDV Mini Ultra", "GPL 2.0 or later", "By guest(r), with work by DariusG, from RetroArch's shader collection. Timeline's GLSL version is a modified port of it."),
    Item("Display calibration", "MIT", "The Retroid Pocket Nova's measured color, by pippopapera. Offered on that device only."),
    Item("ES-DE", "MIT", "How each emulator is handed a game follows ES-DE's Android rules, by Northwestern Software AB and its authors."),
    Item("Kotlin, Compose, Coil, OkHttp", "Apache 2.0", "The language and the libraries Timeline is built with, with Okio and kotlinx.coroutines."),
    Item("Public Suffix List", "MPL 2.0", "Inside OkHttp, unchanged, by the Mozilla Foundation and contributors. Its source is at publicsuffix.org."),
    Item("Cover art", "", "Pictures from the libretro-thumbnails collection. Scraping using Arcade Database by motoschifo, for arcade games."),
    Item("The emulators", "Their own licenses", "RetroArch, Dolphin, ARMSX2 and melonDS are fetched from their own projects and keep their own licenses. Timeline includes none of them."),
    Item("Full license texts", "", "Every license above, in full, travels inside the app, and in the source repository's LICENSE file and LICENSES folder."),
    Item("Privacy", "Nothing collected", "No account, no analytics. Timeline goes online only for what you start: an emulator, the shader collection or cover art."),
    Item("Trademarks", "", "All names and trademarks belong to their owners and are used only to say which games run where. Timeline is not affiliated with or endorsed by any of them."),
))

@Composable
fun MenuScreen(screen: Screen.Menu) {
    val app = LocalApp.current
    // Put together again only when a setting changes, never just because the highlight moved.
    val def by remember(screen.id) { derivedStateOf { app.settingsTick; menu(app, screen.id) } }
    val items = def.items
    val cursor = rememberCursor("menu:" + screen.id, items.size, def.focus ?: 0)
    val f = cursor.index
    fun go(i: Int) = cursor.set(i)
    // A list of steps moves on by itself when the step it was resting on is done.
    // (Also when the page's heading changes: a new question on the same page opens on its first answer again.)
    LaunchedEffect(screen.id, def.focus, def.crumbs) { def.focus?.let { cursor.set(it, quiet = true) } }
    OnKey { k ->
        val it = items.getOrNull(cursor.index)
        when (k) {
            Key.Up -> cursor.move(-1)
            Key.Down -> cursor.move(1)
            Key.L1 -> cursor.move(-8)
            Key.R1 -> cursor.move(8)
            Key.A -> {
                val item = it ?: return@OnKey false
                if (item.onA == null && item.onStep == null) return@OnKey false      // a row that only tells you something
                item.onA?.invoke() ?: item.onStep?.invoke(1)
            }
            Key.Right -> it?.onStep?.let { s -> s(1); Feel.moved(1) }
            Key.Left -> it?.onStep?.let { s -> s(-1); Feel.moved(-1) }
            else -> return@OnKey false
        }
        true
    }
    Frame(
        header = { Crumbs(*def.crumbs.toTypedArray()) },
        hints = def.hints ?: listOf("A" to "Select", "◀ ▶" to "Change", "B" to "Back"),
        footer = def.footer ?: if (items.size > 8) "${f + 1} / ${items.size}" else "",
   ) {
        if (items.isEmpty()) {
            Label("Nothing to set yet")
        } else Column(Modifier.fillMaxWidth()) {
            FocusList(items.size, cursor, rowHeight = 66, rows = 8, modifier = Modifier.fillMaxWidth()) { i, on ->
                Column(Modifier.fillMaxSize().tap { go(i); items[i].onA?.invoke() ?: items[i].onStep?.invoke(1) }) {
                    Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        items[i].mark?.let { done ->
                            // done: a filled square in the accent; still to do: its outline
                            Box(Modifier.size(12.u()).then(if (done) Modifier.background(Ink.Accent) else Modifier.border(1.u(), if (on) Ink.Cream else Ink.Muted)))
                            Gap(w = 20)
                        }
                        Box(Modifier.weight(1f)) { GlideText(items[i].label, on, size = 34, rest = 0.7f) }
                        if (items[i].value.isNotEmpty()) Label(items[i].value, Modifier.widthIn(max = 560.u()), color = if (on) Ink.Cream else Ink.Muted)
                    }
                    Hairline()
                }
            }
            Gap(h = 28)
            // What the highlighted setting does, in a sentence. A page with few choices has the room for more: the
            // Did not open page's steps run to five lines and were cut at three.
            val noteLines = (9 - items.size).coerceIn(3, 6)
            Box(Modifier.fillMaxWidth(0.86f).widthIn(max = 1000.u()).height((32 * noteLines).u())) {      // a line of the note as drawn: 22 px, 1.45 apart
                T(items[f].note, 19, color = Ink.Muted, weight = FontWeight.Normal, lineHeight = 1.45f, maxLines = noteLines)
            }
        }
    }
}

// ---------------------------------------------------------------------------- Accent colour

private val accentPresets: List<Pair<String, Int>> = listOf(
    "Gold" to 0xFFFEBD1F, "Amber" to 0xFFFF8A1F, "Vermilion" to 0xFFFF5A36, "Rose" to 0xFFFF6B9D, "Violet" to 0xFFB79CFF,
    "Ice" to 0xFF7FD4FF, "Mint" to 0xFF5EEAA8, "Phosphor" to 0xFF7CFF4F, "Cream" to 0xFFF2EFE9,
).map { it.first to it.second.toInt() }

@Composable
private fun Slider(name: String, value: String, at: Float, on: Boolean, track: Brush, onTap: () -> Unit) {
    val knob = with(LocalDensity.current) { 6.u().toPx() }
    Column(Modifier.fillMaxWidth().height(104.u()).tap(onTap), verticalArrangement = Arrangement.Center) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { GlideText(name, on, size = 34, rest = 0.7f) }
            Label(value, color = if (on) Ink.Cream else Ink.Muted)
        }
        Gap(h = 14)
        Box(Modifier.fillMaxWidth().height(20.u()).drawBehind {
            val thick = size.height * 0.3f
            drawRect(track, topLeft = Offset(0f, (size.height - thick) / 2f), size = Size(size.width, thick), alpha = if (on) 1f else 0.5f)
            drawRect(if (on) Ink.Cream else Ink.Muted, topLeft = Offset((size.width - knob) * at.coerceIn(0f, 1f), 0f), size = Size(knob, size.height))
        })
    }
}

@Composable
fun AccentScreen() {
    val app = LocalApp.current
    val start = remember { Hsv.of(Ink.Accent.toArgb()) }
    var h by remember { mutableFloatStateOf(start.first) }
    var s by remember { mutableFloatStateOf(start.second) }
    var v by remember { mutableFloatStateOf(start.third.coerceAtLeast(0.5f)) }
    var row by remember { mutableIntStateOf(0) }
    val argb = Ink.Accent.toArgb()
    val preset = accentPresets.indexOfFirst { it.second == argb }
    fun load(c: Int) {
        val t = Hsv.of(c)
        h = t.first; s = t.second; v = t.third
        app.setAccent(c)
    }
    fun nudge(d: Int) {
        when (row) {
            0 -> h = ((h + d * 3f) % 360f + 360f) % 360f
            1 -> s = (s + d * 0.02f).coerceIn(0f, 1f)
            2 -> v = (v + d * 0.02f).coerceIn(0.5f, 1f)
            else -> {
                // read the colour as it is this instant, not as it was when the page was last drawn, so quick presses each count
                val now = accentPresets.indexOfFirst { it.second == Ink.Accent.toArgb() }
                return load(accentPresets[((if (now < 0 && d < 0) 0 else now) + d).mod(accentPresets.size)].second)
            }
        }
        app.setAccent(Hsv.toArgb(h, s, v))
    }
    DisposableEffect(Unit) { onDispose { app.deviceHome() } }      // the sticks take the new colour on the way out
    OnKey { k ->
        when (k) {
            Key.Up -> row = (row - 1).coerceAtLeast(0)
            Key.Down -> row = (row + 1).coerceAtMost(3)
            Key.Left -> nudge(-1)
            Key.Right -> nudge(1)
            Key.X -> load(Ink.GoldArgb)
            Key.A -> app.pop()
            else -> return@OnKey false
        }
        true
    }
    Frame(
        header = { Crumbs("Settings", "Display", "Accent color") },
        hints = listOf("◀ ▶" to "Adjust", "X" to "Back to gold", "A" to "Done"),
        footer = Hsv.hex(argb),
   ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(72.u())) {
            Column(Modifier.width(540.u())) {
                Slider("Hue", "${h.toInt()}°", h / 360f, row == 0,
                    Brush.horizontalGradient((0..12).map { Color(Hsv.toArgb(it * 30f, maxOf(s, 0.4f), v)) })) { row = 0 }
                Slider("Strength", "${(s * 100).toInt()}", s, row == 1,
                    Brush.horizontalGradient(listOf(Color(Hsv.toArgb(h, 0f, v)), Color(Hsv.toArgb(h, 1f, v))))) { row = 1 }
                Slider("Brightness", "${(v * 100).toInt()}", (v - 0.5f) / 0.5f, row == 2,
                    Brush.horizontalGradient(listOf(Color(Hsv.toArgb(h, s, 0.5f)), Color(Hsv.toArgb(h, s, 1f))))) { row = 2 }
                Column(Modifier.fillMaxWidth().height(124.u()).tap { row = 3 }, verticalArrangement = Arrangement.Center) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { GlideText("Presets", row == 3, size = 34, rest = 0.7f) }
                        Label(accentPresets.getOrNull(preset)?.first ?: "Your own", color = if (row == 3) Ink.Cream else Ink.Muted)
                    }
                    Gap(h = 14)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.u())) {
                        accentPresets.forEachIndexed { i, (_, c) ->
                            Box(Modifier.size(48.u()).border(1.u(), if (i == preset) Ink.Cream else Color.Transparent).padding(5.u())
                                .background(Color(c)).tap { row = 3; load(c) })
                        }
                    }
                }
            }
            // A live sample of the places the accent turns up.
            Column(Modifier.weight(1f)) {
                Label("Preview", size = 12)
                Gap(h = 12)
                listOf("Lantern Road" to false, "Quiet Harbor" to true, "The Seventh Lighthouse" to false).forEach { (name, on) ->
                    Box(Modifier.fillMaxWidth().height(64.u()), contentAlignment = Alignment.CenterStart) { GlideText(name, on, size = 34, rest = 0.65f) }
                    Hairline()
                }
                Gap(h = 24)
                Row(horizontalArrangement = Arrangement.spacedBy(24.u())) {
                    Label("PlayStation")
                    Label("1998")
                    Label("2 discs", color = Ink.Accent)
                    Label("Favorite", color = Ink.Accent)
                }
                Gap(h = 24)
                Row(horizontalArrangement = Arrangement.spacedBy(16.u())) {
                    Button("Play", primary = true, min = 150) {}
                    Button("Details", primary = false, min = 150) {}
                }
                Gap(h = 32)
                Row(verticalAlignment = Alignment.Bottom) {
                    T("12:00", 104, Modifier.scanlines(app.scanlines), weight = FontWeight.Bold, tracking = -3, lineHeight = 0.9f)
                    T("_", 104, color = Ink.Accent, weight = FontWeight.Bold, lineHeight = 0.9f)
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------- Apps

@Composable
fun AppsScreen() {
    val app = LocalApp.current
    val apps = remember { Runner.apps(app.ctx) }
    val cursor = rememberCursor("apps", apps.size)
    val f = cursor.index
    fun go(i: Int) = cursor.set(i)
    OnKey { k ->
        when (k) {
            Key.Up -> cursor.move(-1)
            Key.Down -> cursor.move(1)
            Key.L1 -> cursor.move(-9)
            Key.R1 -> cursor.move(9)
            Key.A -> apps.getOrNull(cursor.index)?.let { if (Runner.open(app.ctx, it)) app.openedApp(it.pkg) else app.say("Could not open ${it.label}.") }
            else -> return@OnKey false
        }
        true
    }
    Frame(header = { Crumbs("Settings", "Android apps") }, hints = listOf("A" to "Open", "L1 R1" to "Page", "B" to "Back"), footer = "${apps.size} apps") {
        if (apps.isEmpty()) Label("No apps found")
        else FocusList(apps.size, cursor, rowHeight = 66, rows = 9, modifier = Modifier.fillMaxWidth()) { i, on ->
            Column(Modifier.fillMaxSize().tap { go(i) }) {
                Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { GlideText(apps[i].label, on, size = 34, rest = 0.7f) }
                    Label(apps[i].pkg, size = 11)
                }
                Hairline()
            }
        }
    }
}

// -------------------------------------------------------------------------------------- Rest

@Composable
fun IdleScreen() {
    val app = LocalApp.current
    OnKey {
        app.wake()
        true
    }
    val last = app.recent.firstOrNull { it !in app.hidden || app.isOpen(it) }?.let(app::find)
    val minute by produceState(Calendar.getInstance().get(Calendar.MINUTE)) {
        while (true) {
            delay(20_000)
            value = Calendar.getInstance().get(Calendar.MINUTE)
        }
    }
    // The picture moves a little every minute so nothing rests on the same pixels.
    Box(Modifier.fillMaxSize().tap { app.wake() }.padding(horizontal = 64.u(), vertical = 56.u())) {
        Column(
            // It moves a little every minute; it is made narrower by the most it moves, so it never runs off the screen.
            Modifier.align(Alignment.BottomStart).offset(x = (60 + (minute % 6) * 22).u(), y = (-(40 + (minute % 4) * 26)).u())
                .widthIn(max = (1152 - 60 - 5 * 22).u()),
            verticalArrangement = Arrangement.spacedBy(34.u()),
       ) {
            Row(verticalAlignment = Alignment.Bottom) {
                T(clockText(app.clock24), 220, Modifier.scanlines(app.scanlines), weight = FontWeight.Bold, tracking = -8, lineHeight = 0.86f)
                T("_", 220, color = Ink.Accent, weight = FontWeight.Bold, lineHeight = 0.86f)
            }
            if (last != null) Column(verticalArrangement = Arrangement.spacedBy(8.u())) {
                Label("Continue", size = 12)
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(20.u())) {
                    T(last.title, 34, Modifier.weight(1f, fill = false), color = Ink.Accent, weight = FontWeight.Bold, tracking = -0.8f, lineHeight = 1.1f, maxLines = 2)
                    Label(Catalog.system(last.system)?.name ?: "", Modifier.padding(bottom = 6.u()), size = 12)
                }
            }
            Label("Press any button", size = 12)
        }
    }
}

// ----------------------------------------------------------------------------- First-run gates

@Composable
fun PermissionGate(onGrant: () -> Unit) {
    val app = LocalApp.current
    // The very first press tells whether this handheld's face buttons are the way round Timeline expects. Pressing the
    // other one (a device labeled the Nintendo way, sending the codes the other way round) is answered, not ignored:
    // a second press of it makes that button confirm from then on.
    var other by remember { mutableStateOf(false) }
    OnKey { k ->
        val face = app.lastPad == android.view.KeyEvent.KEYCODE_BUTTON_A || app.lastPad == android.view.KeyEvent.KEYCODE_BUTTON_B
        when {
            k == Key.A -> onGrant()
            k == Key.B && face && other -> {
                app.swapConfirm = !app.swapConfirm
                app.prefs.put("swapConfirm", app.swapConfirm)
                other = false
                onGrant()
            }
            k == Key.B && face -> other = true
        }
        true
    }
    Frame(header = { Crumbs("Welcome") }, hints = listOf("A" to "Open the permission page")) {
        Column(Modifier.fillMaxWidth(0.8f).widthIn(max = 940.u()), verticalArrangement = Arrangement.spacedBy(28.u())) {
            T("One permission", 104, weight = FontWeight.Bold, tracking = -3, upper = true, lineHeight = 0.9f)
            T("To list your games this needs to read the storage card. Android asks you to switch that on by hand: " +
                "on the next page, turn on “Allow access to manage all files”, then come back.",
                22, color = Ink.Muted, weight = FontWeight.Normal, lineHeight = 1.45f)
            Button("Open the permission page", primary = true, onClick = onGrant)
            if (other) T("That button means back to Timeline. If it is the one you use to say yes, press it again and the two " +
                "will swap. Settings, Controls swaps them back.", 22, color = Ink.Gold, weight = FontWeight.Normal, lineHeight = 1.45f)
        }
    }
}

@Composable
fun EmptyLibrary() {
    val app = LocalApp.current
    val setup = { app.push(Screen.Menu("setup")) }
    OnKey { k ->
        when (k) {
            Key.A -> if (app.scanned) setup() else app.rescan()
            Key.X -> app.rescan()
            Key.Start -> app.push(Screen.Menu("settings"))
            else -> return@OnKey false
        }
        true
    }
    val hints = if (app.scanned) listOf("A" to "First-time setup", "X" to "Look again", "START" to "Settings")
        else listOf("A" to "Look again", "START" to "Settings")
    Frame(header = { Crumbs("Timeline") }, hints = hints) {
        Column(Modifier.fillMaxWidth(0.8f).widthIn(max = 940.u()), verticalArrangement = Arrangement.spacedBy(28.u())) {
            T(if (app.scanned) "No games yet" else "Looking", 104, weight = FontWeight.Bold, tracking = -3, upper = true, lineHeight = 0.9f)
            if (app.scanned) {
                T(app.romsRoot?.let { "Looked in ${it.absolutePath}. Each console gets its own folder inside, such as snes or psx." }
                    ?: ("Timeline looks for a folder named ROMs on the storage card, with a folder for each console inside, " +
                        "such as snes or psx. First-time setup can make them for you and get everything else ready."),
                    22, color = Ink.Muted, weight = FontWeight.Normal, lineHeight = 1.45f)
                Button("First-time setup", primary = true, onClick = setup)
            }
        }
    }
}

@Composable
fun Toast() {
    val app = LocalApp.current
    val msg = app.toast ?: return
    LaunchedEffect(app.toastId) {
        delay(2600)
        app.toast = null
    }
    Box(Modifier.fillMaxSize().padding(bottom = 104.u()), contentAlignment = Alignment.BottomCenter) {
        Box(Modifier.background(if (app.trueBlack) Ink.Black else Ink.Soft).border(1.u(), Ink.Accent).padding(horizontal = 28.u(), vertical = 16.u())) {
            T(msg, 20, color = Ink.Cream, maxLines = 2)
        }
    }
}

/**
 * The volume, shown in the launcher's own way instead of Android's slider: a row of steps, one for each of Android's
 * volume steps, the ones that are on in the accent color, with the word and the number above in the header's type.
 * It fades in as the volume keys are pressed and away a moment after the last press.
 */
@Composable
fun VolumeBar() {
    val app = LocalApp.current
    val shown = app.volumeId > 0 && app.volumeShown
    val fade by androidx.compose.animation.core.animateFloatAsState(if (shown) 1f else 0f, tween(if (shown) 120 else 420), label = "volume")
    LaunchedEffect(app.volumeId) {
        if (app.volumeId == 0) return@LaunchedEffect
        delay(1400)
        app.volumeShown = false
    }
    if (fade <= 0.01f) return
    val level = app.volumeLevel.coerceIn(0, 100)
    Box(Modifier.fillMaxSize().padding(bottom = 104.u()).graphicsLayer { alpha = fade }, contentAlignment = Alignment.BottomCenter) {
        Column(Modifier.width(488.u()).background(Ink.Black).border(1.u(), Ink.Line).padding(horizontal = 24.u(), vertical = 18.u()),
            verticalArrangement = Arrangement.spacedBy(12.u())) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                T("VOLUME", 13, Modifier.weight(1f), color = Ink.Muted, weight = FontWeight.Medium, tracking = 2)
                T(if (level == 0) "OFF" else "$level", 13, color = Ink.Cream, weight = FontWeight.Medium, tracking = 2)
            }
            Row(Modifier.fillMaxWidth().height(6.u()), horizontalArrangement = Arrangement.spacedBy(4.u())) {
                for (i in 1..10) Box(Modifier.weight(1f).fillMaxHeight().background(if (level >= i * 10) Ink.Accent else Ink.Line))
            }
        }
    }
}

// ---------------------------------------------------------------------------- On-screen keys

private class KeyDef(val label: String, val col: Int, val span: Int = 1, val type: String = label.lowercase())

private val keyRows: List<List<KeyDef>> =
    listOf("ABCDEFG", "HIJKLMN", "OPQRSTU", "VWXYZ12", "3456789").map { row -> row.mapIndexed { i, c -> KeyDef(c.toString(), i) } } +
        listOf(listOf(KeyDef("0", 0), KeyDef("Space", 1, 3, " "), KeyDef("Delete", 4, 3, "\b")))

/** Where the highlight is on the on-screen keys. */
private class KeyPad {
    var row by mutableIntStateOf(0)
    var idx by mutableIntStateOf(0)
    val key: KeyDef get() = keyRows[row][idx.coerceAtMost(keyRows[row].size - 1)]

    fun vertical(d: Int) {
        val r = (row + d).coerceIn(0, keyRows.size - 1)
        if (r == row) return
        val col = key.col
        idx = keyRows[r].indexOfLast { it.col <= col }.coerceAtLeast(0)
        row = r
    }

    /** Moves sideways. False when already at that edge. */
    fun side(d: Int): Boolean {
        val n = idx.coerceAtMost(keyRows[row].size - 1) + d
        if (n < 0 || n > keyRows[row].size - 1) return false
        idx = n
        return true
    }
}

@Composable
private fun KeyGrid(pad: KeyPad, active: Boolean, onType: (KeyDef) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.u())) {
        keyRows.forEachIndexed { r, keys ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.u())) {
                keys.forEachIndexed { i, k ->
                    val on = active && r == pad.row && i == pad.idx
                    Box(
                        Modifier.height(64.u()).width((k.span * 64 + (k.span - 1) * 8).u())
                            .border(1.u(), if (on) Ink.Accent else Ink.Line)
                            .tap { pad.row = r; pad.idx = i; onType(k) },
                        contentAlignment = Alignment.Center,
                   ) {
                        if (k.span == 1) T(k.label, 24, color = if (on) Ink.Accent else Ink.Cream, weight = FontWeight.Bold)
                        else Label(k.label, color = if (on) Ink.Accent else Ink.Muted, size = 12)
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------ Search

@Composable
fun SearchScreen() {
    val app = LocalApp.current
    val all = remember(app.library) { app.all() }
    val results = remember(app.query, all) { Search.find(app.query, all) }
    var zone by remember { mutableIntStateOf(0) }       // 0 = keyboard, 1 = results
    val pad = remember { KeyPad() }
    val cursor = rememberCursor("search", results.size)
    LaunchedEffect(app.query) {
        // Back to the top for a new search, but not on coming back from a game page with the same one.
        if (app.focus["search:for"] != app.query.hashCode()) { app.focus["search:for"] = app.query.hashCode(); cursor.set(0, quiet = true) }
    }
    LaunchedEffect(results.isEmpty()) { if (results.isEmpty()) zone = 0 }
    fun type(k: KeyDef) {
        if (k.type == "\b") app.query = app.query.dropLast(1)
        else if (app.query.length < 40 && !(k.type == " " && app.query.isEmpty())) app.query += k.type
    }
    fun details(g: Game) = app.page(g.key, results.map { it.key }, listOf("Timeline", "Search"))
    OnKey { k ->
        if (zone == 0) {
            when (k) {
                Key.Up -> pad.vertical(-1)
                Key.Down -> pad.vertical(1)
                Key.Left -> pad.side(-1)
                Key.Right -> if (!pad.side(1) && results.isNotEmpty()) zone = 1
                Key.A -> type(pad.key)
                Key.X -> app.query = app.query.dropLast(1)
                Key.Y -> if (app.query.isNotEmpty() && app.query.length < 40) app.query += " "
                Key.L1 -> app.query = ""
                else -> return@OnKey false
            }
        } else {
            when (k) {
                Key.Up -> cursor.move(-1)
                Key.Down -> cursor.move(1)
                Key.L1 -> cursor.move(-9)
                Key.R1 -> cursor.move(9)
                Key.Left -> zone = 0
                Key.A -> results.getOrNull(cursor.index)?.let { app.play(it) }
                Key.X -> results.getOrNull(cursor.index)?.let { details(it) }
                else -> return@OnKey false
            }
        }
        true
    }
    Frame(
        header = { Crumbs("Timeline", "Search") },
        hints = if (zone == 0) listOf("A" to "Type", "X" to "Delete", "Y" to "Space", "L1" to "Clear", "▶" to "Results")
        else listOf("A" to "Play", "X" to "Details", "◀" to "Keyboard"),
        footer = if (app.query.isBlank()) "" else "${results.size} found",
   ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(64.u())) {
            Column(Modifier.width(496.u()), verticalArrangement = Arrangement.spacedBy(28.u())) {
                Row(verticalAlignment = Alignment.Bottom) {
                    T(app.query.ifEmpty { "Type a title" }, 44, Modifier.weight(1f, fill = false),
                        color = if (app.query.isEmpty()) Ink.Muted else Ink.Cream, weight = FontWeight.Bold, tracking = -1.2f, maxLines = 1)
                    T("_", 44, color = Ink.Accent, weight = FontWeight.Bold)
                }
                KeyGrid(pad, active = zone == 0) { zone = 0; type(it) }
            }
            Box(Modifier.weight(1f)) {
                if (results.isEmpty()) {
                    Label(if (app.query.isBlank()) "Results appear as you type" else "No matches")
                } else FocusList(results.size, cursor, rowHeight = 64, rows = 9, anchor = 1f) { i, on ->
                    Column(Modifier.fillMaxSize().tap { zone = 1; cursor.set(i) }) {
                        Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f).padding(end = 12.u())) { GlideText(results[i].title, on && zone == 1, size = 30, rest = 0.74f) }
                            Label(Catalog.system(results[i].system)?.name ?: "", size = 11)
                        }
                        Hairline()
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------ Naming

@Composable
fun NamingScreen(screen: Screen.Naming) {
    val app = LocalApp.current
    val pad = remember { KeyPad() }
    fun type(k: KeyDef) {
        val fresh = app.draft.isEmpty() || app.draft.endsWith(" ")
        when {
            k.type == "\b" -> app.draft = app.draft.dropLast(1)
            app.draft.length >= 20 || (k.type == " " && fresh) -> Unit
            else -> app.draft += if (fresh) k.type.uppercase() else k.type     // capitals start each word
        }
    }
    fun save() {
        val name = app.draft.trim()
        if (name.isEmpty()) return app.say("Type a name first.")
        val done = app.naming
        app.naming = null
        app.pop()
        done?.invoke(name)
    }
    OnKey { k ->
        when (k) {
            Key.Up -> pad.vertical(-1)
            Key.Down -> pad.vertical(1)
            Key.Left -> pad.side(-1)
            Key.Right -> pad.side(1)
            Key.A -> type(pad.key)
            Key.X -> app.draft = app.draft.dropLast(1)
            Key.Y -> type(keyRows.last()[1])
            Key.Start -> save()
            else -> return@OnKey false
        }
        true
    }
    Frame(
        header = { Crumbs("Shelves", screen.title) },
        hints = listOf("A" to "Type", "X" to "Delete", "Y" to "Space", "START" to "Save", "B" to "Cancel"),
        footer = "${app.draft.length} / 20",
   ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(64.u())) {
            Column(Modifier.width(496.u()), verticalArrangement = Arrangement.spacedBy(28.u())) {
                Row(verticalAlignment = Alignment.Bottom) {
                    T(app.draft.ifEmpty { "Name it" }, 44, Modifier.weight(1f, fill = false),
                        color = if (app.draft.isEmpty()) Ink.Muted else Ink.Cream, weight = FontWeight.Bold, tracking = -1.2f, maxLines = 1)
                    T("_", 44, color = Ink.Accent, weight = FontWeight.Bold)
                }
                KeyGrid(pad, active = true) { type(it) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(24.u())) {
                T("A shelf of your own sits at the top of Shelves and keeps the order you add games in.",
                    22, color = Ink.Muted, weight = FontWeight.Normal, lineHeight = 1.45f)
                Button("Save", primary = true) { save() }
            }
        }
    }
}

// ---------------------------------------------------------------------------------- Scraping

@Composable
fun ScrapingScreen() {
    val app = LocalApp.current
    val job = app.scrape
    val view = LocalView.current
    DisposableEffect(job.running) {
        view.keepScreenOn = job.running      // a long run should not be cut short by the screen sleeping
        onDispose { view.keepScreenOn = false }
    }
    OnKey { k ->
        when {
            k == Key.B && job.running -> job.stop()
            k == Key.B || k == Key.A -> if (!job.running) app.pop()
        }
        true
    }
    val heading = when {
        job.running -> "Scraping"
        job.stopped != null -> "Stopped"
        else -> "Done"
    }
    Frame(
        header = { Crumbs("Settings", "Scraper", heading) },
        hints = if (job.running) listOf("B" to "Stop") else listOf("A" to "Done"),
        footer = job.source,
   ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(28.u())) {
            T(heading, 104, Modifier.scanlines(app.scanlines), weight = FontWeight.Bold, tracking = -3, upper = true, lineHeight = 0.9f)
            T(job.stopped ?: job.current.ifEmpty { " " }, 30, color = if (job.stopped != null) Ink.Accent else Ink.Cream, tracking = -0.6f, maxLines = 2)
            Box(Modifier.fillMaxWidth().height(6.u()).background(Ink.Line)) {
                val part = if (job.total > 0) job.done.toFloat() / job.total else 0f
                Box(Modifier.fillMaxWidth(part.coerceIn(0f, 1f)).height(6.u()).background(Ink.Accent))
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(40.u())) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.u())) {
                    T("${job.done}", 40, weight = FontWeight.Bold, tracking = -1)
                    Label("of ${job.total}", Modifier.padding(bottom = 7.u()))
                }
                Label("${job.found} found", Modifier.padding(bottom = 7.u()), color = Ink.Cream)
                Label("${job.missed} not found", Modifier.padding(bottom = 7.u()))
            }
        }
    }
}
