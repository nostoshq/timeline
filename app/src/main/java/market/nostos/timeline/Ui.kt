// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 NOSTOS, LLC

package market.nostos.timeline

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

object Ink {
    const val GoldArgb: Int = 0xFFFEBD1F.toInt()
    val Gold = Color(GoldArgb)

    /** The one colour the player can change. Everything that was gold reads this. */
    var Accent by mutableStateOf(Gold)

    /** Text set on top of the accent: dark on a light accent, light on a dark one. */
    val OnAccent: Color get() = if (Accent.luminance() > 0.26f) Soft else Cream

    val Cream = Color(0xFFF2EFE9)
    val Line = Color(0xFF1F1F1F)
    val Muted = Color(0xFF888888)
    val Black = Color(0xFF000000)
    val Soft = Color(0xFF0D0D0D)
}

val Grotesk = FontFamily(
    Font(R.font.space_grotesk_regular, FontWeight.Normal),
    Font(R.font.space_grotesk_medium, FontWeight.Medium),
    Font(R.font.space_grotesk_bold, FontWeight.Bold),
)

/**
 * Every measurement in the interface is written in "design pixels" on a stage 960 tall: 1280 x 960 on a
 * 4:3 screen such as the Retroid Pocket Nova's, wider on a 16:9 one. The stage is scaled to the screen,
 * so one design pixel is one real pixel on a 1280 x 960 panel and the layout holds on anything else.
 */
class Stage(val k: Float, val width: Float = 1280f)       // width: the stage's width in design pixels, 1280 on 4:3

val LocalStage = compositionLocalOf { Stage(0.5f) }
val LocalApp = compositionLocalOf<AppState> { error("no app state") }

@Composable
@ReadOnlyComposable
fun Number.u(): Dp = (this.toFloat() * LocalStage.current.k).dp

@Composable
@ReadOnlyComposable
fun Number.t(): TextUnit = with(LocalDensity.current) { (this@t.toFloat() * LocalStage.current.k).dp.toSp() }

@Composable
fun T(
    text: String,
    size: Number,
    modifier: Modifier = Modifier,
    color: Color = Ink.Cream,
    weight: FontWeight = FontWeight.Medium,
    tracking: Number = 0,
    upper: Boolean = false,
    lineHeight: Float = 0f,
    maxLines: Int = Int.MAX_VALUE,
    align: TextAlign = TextAlign.Start,
) {
    BasicText(
        text = if (upper) text.uppercase() else text,
        modifier = modifier,
        style = textStyle(size, color, weight, tracking, lineHeight, align),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
   )
}

/**
 * The size text is drawn at, from the size it is written at. The Nova's panel is 4.5 inches at 1280 x 960, so a design
 * pixel is 0.07 mm and a 13 px label was under a millimetre tall. Written sizes under 24 are drawn larger, the
 * smallest most (11 to 13 become 15 to 17, a 17 px description 21), up to 24, where text is drawn as written. Every
 * measurement of text goes through here as well, so what is measured is what is drawn.
 */
fun reading(size: Float): Float = if (size >= 24f) size else size + 4f - maxOf(0f, size - 17f) * 0.5f

@Composable
@ReadOnlyComposable
fun textStyle(size: Number, color: Color, weight: FontWeight, tracking: Number, lineHeight: Float, align: TextAlign = TextAlign.Start): TextStyle {
    val drawn = reading(size.toFloat())
    return TextStyle(
        color = color, fontFamily = Grotesk, fontWeight = weight, fontSize = drawn.t(),
        letterSpacing = tracking.t(), textAlign = align,
        lineHeight = if (lineHeight > 0f) (drawn * lineHeight).t() else TextUnit.Unspecified,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
   )
}

/** The largest of [sizes] at which [text] fits in [lines] lines [widthPx] wide, so a long name steps down rather than
 *  losing its last words; the smallest if none fits. Most names fit the first size and are measured once. */
@Composable
fun fittingSize(text: String, sizes: IntArray, widthPx: Int, weight: FontWeight, tracking: (Int) -> Float, lineHeight: Float, lines: Int): Int {
    val measurer = rememberTextMeasurer(cacheSize = 4)
    val styles = sizes.map { textStyle(it, Ink.Cream, weight, tracking(it), lineHeight) }
    return remember(text, widthPx) {
        if (widthPx <= 0) return@remember sizes.first()
        sizes.indices.firstOrNull { i ->
            !measurer.measure(text, styles[i], overflow = TextOverflow.Ellipsis, maxLines = lines,
                constraints = Constraints(maxWidth = widthPx)).hasVisualOverflow
        }?.let { sizes[it] } ?: sizes.last()
    }
}

/** How many of [facts] fit in a row of labels [widthPx] wide with all of [badges] after them, [gap] design pixels apart. */
@Composable
fun labelsThatFit(facts: List<String>, badges: List<String>, widthPx: Int, gap: Int, size: Number = 13): Int {
    val measurer = rememberTextMeasurer(cacheSize = 16)
    val style = textStyle(size, Ink.Muted, FontWeight.Bold, 3, 0f)
    val gapPx = with(LocalDensity.current) { gap.u().roundToPx() }
    return remember(facts, badges, widthPx) {
        if (widthPx <= 0) return@remember facts.size
        fun w(s: String) = measurer.measure(s.uppercase(), style, maxLines = 1, softWrap = false).size.width
        val badgeWidth = badges.sumOf { w(it) }
        var n = facts.size
        while (n > 0 && facts.take(n).sumOf { w(it) } + badgeWidth + gapPx * (n + badges.size - 1) > widthPx) n--
        n
    }
}

/**
 * How many of [extras] (small labels after a name) fit in a row [widthPx] wide that starts with the buttons [keys] and
 * the labels [lead], [gap] design pixels apart, with the name ([name], 20 px bold) shown whole: the extras give way
 * from the end, so the name is the last thing to be cut.
 */
@Composable
fun extrasThatFit(keys: List<String>, lead: List<String>, name: String, extras: List<String>, widthPx: Int, gap: Int): Int {
    val measurer = rememberTextMeasurer(cacheSize = 16)
    val label = textStyle(12, Ink.Muted, FontWeight.Bold, 3, 0f)
    val cap = textStyle(12, Ink.Cream, FontWeight.Bold, 0, 0f)
    val title = textStyle(20, Ink.Cream, FontWeight.Bold, -0.3f, 0f)
    val d = LocalDensity.current
    val gapPx = with(d) { gap.u().roundToPx() }
    val capPad = with(d) { 12.u().roundToPx() }
    val capMin = with(d) { 26.u().roundToPx() }
    return remember(keys, lead, name, extras, widthPx) {
        if (widthPx <= 0) return@remember extras.size
        fun w(t: String, st: TextStyle) = measurer.measure(t, st, maxLines = 1, softWrap = false).size.width
        val fixed = keys.sumOf { maxOf(capMin, w(it, cap) + capPad) } + lead.sumOf { w(it.uppercase(), label) } + w(name, title)
        var n = extras.size
        while (n > 0 && fixed + extras.take(n).sumOf { w(it.uppercase(), label) } + gapPx * (keys.size + lead.size + n) > widthPx) n--
        n
    }
}

/** The small tracked capitals used for every label. */
@Composable
fun Label(text: String, modifier: Modifier = Modifier, color: Color = Ink.Muted, size: Number = 13) =
    T(text, size, modifier, color = color, weight = FontWeight.Bold, tracking = 3, upper = true, maxLines = 1)

fun Modifier.tap(onClick: () -> Unit): Modifier =
    this.clickable(interactionSource = null, indication = null, onClick = onClick)

/** Dark scanlines drawn only across lit content, so black stays unlit. */
@Composable
fun Modifier.scanlines(on: Boolean): Modifier {
    if (!on) return this
    val pitch = with(LocalDensity.current) { 4.u().toPx() }
    val thick = with(LocalDensity.current) { 1.u().toPx() }
    return this.drawWithContent {
        drawContent()
        var y = pitch - thick
        while (y < size.height) {
            drawRect(Color.Black.copy(alpha = 0.42f), topLeft = Offset(0f, y), size = Size(size.width, thick))
            y += pitch
        }
    }
}

// ---- controller input ---------------------------------------------------------------------------

enum class Key { Up, Down, Left, Right, A, B, X, Y, L1, R1, Start, Select }

/**
 * What each button does, kept per screen. A press goes to the screen that is on top of the stack at that
 * instant, so nothing is lost or sent to the page being left while one screen slides over another.
 */
object Keys {
    val handlers = HashMap<Any, (Key) -> Boolean>()
}

/** The place in the stack a screen is being drawn for. */
val LocalEntry = compositionLocalOf<Any> { Unit }

/** A screen says what each button does while it is the one on top. */
@Composable
fun OnKey(block: (Key) -> Boolean) {
    val current by rememberUpdatedState(block)
    val owner = LocalEntry.current
    DisposableEffect(owner) {
        val h: (Key) -> Boolean = { current(it) }
        Keys.handlers[owner] = h
        onDispose { if (Keys.handlers[owner] === h) Keys.handlers.remove(owner) }
    }
}

// ---- building blocks --------------------------------------------------------------------------

@Composable
fun Keycap(text: String) {
    Box(
        Modifier.height(26.u()).widthIn(min = 26.u()).border(1.u(), Ink.Muted).padding(horizontal = 6.u()),
        contentAlignment = Alignment.Center,
   ) { T(text, 12, color = Ink.Cream, weight = FontWeight.Bold) }
}

@Composable
fun Hint(key: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.u())) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.u())) { key.split(' ').forEach { Keycap(it) } }
        Label(label, size = 12)
    }
}

/**
 * Where the highlight is in a list. Button presses read and write the live value, never a copy
 * taken when the screen was last drawn, so fast presses cannot be lost or land on the wrong row.
 */
class Cursor(private val state: MutableIntState, private val count: State<Int>, private val save: (Int) -> Unit) {
    val index: Int get() = state.intValue.coerceIn(0, (count.value - 1).coerceAtLeast(0))
    /** [quiet]: the page put the highlight there itself (a new search, a page opening on its first row), not a press. */
    fun set(i: Int, quiet: Boolean = false) {
        val v = i.coerceIn(0, (count.value - 1).coerceAtLeast(0))
        if (!quiet) when {
            v != state.intValue -> Feel.moved(v - state.intValue)
            i != v -> Feel.edge()                   // asked to go past either end: a knock, not a tick
        }
        state.intValue = v
        save(v)
    }
    fun move(d: Int) = set(index + d)
}

@Composable
fun rememberCursor(memory: String, count: Int, initial: Int = 0): Cursor {
    val app = LocalApp.current
    val n = rememberUpdatedState(count)
    return remember(memory) { Cursor(mutableIntStateOf(app.focus[memory] ?: initial), n) { app.focus[memory] = it } }
}

@Composable
fun clockText(twentyFour: Boolean): String {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(60_000L - (System.currentTimeMillis() % 60_000L) + 50L)
        }
    }
    return SimpleDateFormat(if (twentyFour) "HH:mm" else "h:mm", Locale.US).format(Date(now))
}

@Composable
fun BatteryGlyph(level: Int) {
    Box(Modifier.size(width = 26.u(), height = 14.u()).drawWithContent {
        val w = size.width
        val h = size.height
        val s = h / 14f
        drawRect(Ink.Muted, topLeft = Offset(0f, 0f), size = Size(w - 4f * s, 1.5f * s))
        drawRect(Ink.Muted, topLeft = Offset(0f, h - 1.5f * s), size = Size(w - 4f * s, 1.5f * s))
        drawRect(Ink.Muted, topLeft = Offset(0f, 0f), size = Size(1.5f * s, h))
        drawRect(Ink.Muted, topLeft = Offset(w - 5.5f * s, 0f), size = Size(1.5f * s, h))
        drawRect(Ink.Muted, topLeft = Offset(w - 3f * s, 4f * s), size = Size(2f * s, 6f * s))
        val fill = (w - 11f * s) * (level.coerceIn(0, 100) / 100f)
        drawRect(Ink.Muted, topLeft = Offset(3.5f * s, 3.5f * s), size = Size(fill, h - 7f * s))
    })
}

/** The shared page: header, body, footer of button hints. */
@Composable
fun Frame(
    header: @Composable () -> Unit,
    hints: List<Pair<String, String>>,
    footer: String = "",
    headerRight: (@Composable () -> Unit)? = null,
    hintsSlot: (@Composable () -> Unit)? = null,       // button hints that follow the highlight, drawn by the page itself
    footerSlot: (@Composable () -> Unit)? = null,
    body: @Composable () -> Unit,
) {
    val app = LocalApp.current
    Column(Modifier.fillMaxSize().padding(horizontal = 64.u(), vertical = 56.u())) {
        Row(Modifier.fillMaxWidth().height(32.u()), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { header() }
            if (headerRight != null) headerRight() else Row(
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.u()),
           ) {
                if (app.powerSave) T("Battery saver on", 13, color = Ink.Gold, weight = FontWeight.Medium, tracking = 2)
                T(clockText(app.clock24), 13, color = Ink.Muted, weight = FontWeight.Medium, tracking = 2)
                val level = app.batteryLevel.takeIf { it >= 0 } ?: remember { Runner.battery(app.ctx) }
                if (app.batteryText.isNotEmpty()) T(app.batteryText, 13, color = Ink.Muted, weight = FontWeight.Medium, tracking = 2)
                if (level >= 0) BatteryGlyph(level)
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.CenterStart) { body() }
        Row(Modifier.fillMaxWidth().height(32.u()), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(30.u())) {
                if (hintsSlot != null) hintsSlot() else hints.forEach { (k, l) -> Hint(k, l) }
            }
            if (footerSlot != null) footerSlot() else if (footer.isNotEmpty()) Label(footer, size = 12)
        }
    }
}

/** Breadcrumb used in headers: muted trail, bright current page, gold cursor. */
@Composable
fun Crumbs(vararg parts: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.u())) {
        parts.dropLast(1).forEach {
            Label(it)
            Label("/")
        }
        Row {
            Label(parts.last(), color = Ink.Cream)
            Label("_", color = Ink.Accent)
        }
    }
}

@Composable
fun Button(text: String, primary: Boolean, modifier: Modifier = Modifier, min: Int = 200, onClick: () -> Unit) {
    Box(
        modifier.height(56.u()).widthIn(min = min.u())
            .then(if (primary) Modifier.background(Ink.Accent) else Modifier.border(1.u(), Ink.Muted))
            .tap(onClick).padding(horizontal = 24.u()),
        contentAlignment = Alignment.Center,
   ) { Label(text, color = if (primary) Ink.OnAccent else Ink.Cream) }
}

/**
 * A list where the highlighted row rests in one place and the rows glide under it. At either end the
 * list stops and the highlight travels instead, so a row of the panel is never left empty.
 * Only the handful of rows on screen exist at any moment, so it costs the same for 10 games or 10,000.
 */
@Composable
fun FocusList(
    count: Int,
    cursor: Cursor,
    rowHeight: Int,
    rows: Int,
    modifier: Modifier = Modifier,
    anchor: Float = ((rows - 1) / 2).toFloat(),
    row: @Composable (index: Int, focused: Boolean) -> Unit,
) {
    val shown = minOf(rows, count).coerceAtLeast(1)
    val reach = (count - shown).coerceAtLeast(0).toFloat()
    // Where the list should sit for the highlighted row. Read here without subscribing: a step of the highlight
    // moves the list by animation and redraws two rows, and nothing else on the page is put together again.
    val pos = remember { Animatable(Snapshot.withoutReadObservation { (cursor.index - anchor).coerceIn(0f, reach) }) }
    LaunchedEffect(cursor, reach, anchor) {
        snapshotFlow { (cursor.index - anchor).coerceIn(0f, reach) }.collect { target ->
            // Each new target takes over from the one in flight and inherits its speed, so a held direction is one
            // continuous glide instead of a string of starts and stops.
            launch { pos.animateTo(target, spring(dampingRatio = 1f, stiffness = 900f)) }
        }
    }
    val rowPx = with(LocalDensity.current) { rowHeight.u().toPx() }
    val first by remember(count, shown) { derivedStateOf { floor(pos.value - 1f).toInt().coerceAtLeast(0) } }
    val last by remember(count, shown) { derivedStateOf { ceil(pos.value + shown).toInt().coerceAtMost(count - 1) } }
    Box(modifier.height((rowHeight * shown).u()).clipToBounds()) {
        for (i in first..last) {
            key(i) {
                Box(Modifier.fillMaxWidth().height(rowHeight.u()).graphicsLayer { translationY = (i - pos.value) * rowPx }) {
                    FocusRow(i, cursor, row)
                }
            }
        }
    }
}

/** One row, which only hears about the highlight when it gains or loses it. */
@Composable
private fun FocusRow(index: Int, cursor: Cursor, row: @Composable (index: Int, focused: Boolean) -> Unit) {
    val on by remember(index, cursor) { derivedStateOf { cursor.index == index } }
    row(index, on)
}

/** A text row that grows and brightens when it has focus, without shifting its neighbours. */
@Composable
fun GlideText(text: String, focused: Boolean, size: Number, rest: Float, modifier: Modifier = Modifier, upper: Boolean = false, tracking: Number = -0.5f) {
    val glide = animateFloatAsState(if (focused) 1f else rest, spring(dampingRatio = 1f, stiffness = 900f), label = "scale")
    val scale by glide
    // A row at rest is drawn smaller, so it is given the room that smaller size leaves and a long name shows more of
    // itself. The room is given only once the row has finished shrinking, and taken back the moment it gains the
    // highlight, so the text never spills past the column. Rows recompose only when this flips, not every frame.
    val settled by remember(rest) { derivedStateOf { glide.value <= rest + 0.002f } }
    val roomy = !focused && settled
    T(
        text, size, modifier.layout { m, c ->
            val w = if (roomy && c.hasBoundedWidth) (c.maxWidth / rest).toInt() else c.maxWidth
            val p = m.measure(c.copy(maxWidth = w))
            layout(minOf(p.width, c.maxWidth), p.height) { p.place(0, 0) }
        }.graphicsLayer {
            scaleX = scale
            scaleY = scale
            transformOrigin = TransformOrigin(0f, 0.5f)
        },
        color = if (focused) Ink.Accent else Ink.Muted, weight = FontWeight.Bold, tracking = tracking, upper = upper, maxLines = 1,
   )
}

@Composable
fun Hairline() = Box(Modifier.fillMaxWidth().height(1.u()).background(Ink.Line))

@Composable
fun picture(file: File, px: Int, pixelArt: Boolean = false): Any {
    val ctx = LocalContext.current
    return remember(file, px, pixelArt) { Pictures.request(ctx, file, px, pixelArt) }
}

/**
 * A square slot for cover art. The art keeps its own shape and sits in the bottom-left corner, so the
 * text beneath it never moves. With nothing scraped yet, a quiet labelled frame holds the place.
 */
@Composable
fun Cover(file: File?, size: Int, modifier: Modifier = Modifier, align: Alignment = Alignment.BottomStart, known: Boolean = true, decode: Int = size) {
    // Decoded at one size wherever the same cover is shown, so a page that follows a list finds it already in memory.
    val px = with(LocalDensity.current) { decode.u().roundToPx() }
    val none = file == null && known        // looked for and not there, as opposed to not looked for yet
    Box(modifier.size(size.u()).then(if (none) Modifier.border(1.u(), Ink.Line) else Modifier), contentAlignment = Alignment.Center) {
        if (file != null) AsyncImage(model = picture(file, px), contentDescription = null, contentScale = ContentScale.Fit, alignment = align, modifier = Modifier.fillMaxSize())
        else if (none) Label("No art yet", size = 12)
    }
}

/** A picture drawn at its own shape, as large as fits in the given box. */
@Composable
fun Art(file: File, ratio: Float, maxW: Int, maxH: Int, modifier: Modifier = Modifier, pixelArt: Boolean = false) {
    val r = ratio.coerceIn(0.4f, 3f)
    val w = if (maxW / r <= maxH) maxW.toFloat() else maxH * r
    val h = w / r
    val px = with(LocalDensity.current) { maxOf(w, h).u().roundToPx() }
    AsyncImage(model = picture(file, px, pixelArt), contentDescription = null, contentScale = ContentScale.Fit, modifier = modifier.size(w.u(), h.u()))
}

/**
 * Long text in a fixed window that shows whole lines only. `line` is the line at the top; the page
 * that owns it moves it and is told how many lines there are and how many fit.
 */
@Composable
fun Reader(text: String, line: Int, size: Int, lineHeight: Float, modifier: Modifier = Modifier, onMeasured: (total: Int, visible: Int) -> Unit) {
    val density = LocalDensity.current
    val drawn = reading(size.toFloat())
    // The distance between lines as the text was really laid out (Android rounds each line to whole pixels), so a
    // window of whole lines stays whole however far it is scrolled; worked out until the first layout says.
    var pitch by remember(text) { mutableFloatStateOf(0f) }
    val linePx = if (pitch > 0f) pitch else with(density) { kotlin.math.ceil((drawn * lineHeight).u().toPx()) }
    val shift by animateFloatAsState(line * linePx, spring(dampingRatio = 1f, stiffness = 700f), label = "read")
    var total by remember(text) { mutableIntStateOf(0) }
    var boxPx by remember { mutableIntStateOf(0) }
    // Whole lines only: with no room for one, nothing rather than a sliced line.
    val visible = if (boxPx > 0) (boxPx / linePx).toInt() else 0
    LaunchedEffect(total, visible) { if (total > 0 && visible > 0) onMeasured(total, visible) }
    Box(modifier.onSizeChanged { boxPx = it.height }) {
        if (visible > 0) Box(Modifier.fillMaxWidth().height(with(density) { (visible * linePx).toDp() }).clipToBounds()) {
            BasicText(
                text = text,
                modifier = Modifier.fillMaxWidth().wrapContentHeight(Alignment.Top, unbounded = true).graphicsLayer { translationY = -shift },
                style = TextStyle(
                    color = Ink.Muted, fontFamily = Grotesk, fontWeight = FontWeight.Normal, fontSize = drawn.t(),
                    lineHeight = (drawn * lineHeight).t(), platformStyle = PlatformTextStyle(includeFontPadding = false),
                    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
               ),
                onTextLayout = { total = it.lineCount; if (it.lineCount > 1) pitch = it.getLineTop(1) - it.getLineTop(0) },
           )
        }
    }
}

@Composable
fun Gap(h: Number = 0, w: Number = 0) = Spacer(Modifier.height(h.u()).width(w.u()))
