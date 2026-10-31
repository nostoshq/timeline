// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 NOSTOS, LLC

package market.nostos.timeline

import kotlinx.coroutines.launch
import kotlin.math.abs
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.key
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Animatable
import android.app.role.RoleManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import coil.Coil
import coil.ImageLoader
import coil.memory.MemoryCache
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private lateinit var app: AppState
    private val handler = Handler(Looper.getMainLooper())
    private var lastInput = SystemClock.uptimeMillis()
    private var stick: Key? = null
    private var holdFrom = 0L
    private val stickRepeat = object : Runnable {
        override fun run() {
            stick?.let {
                press(it)
                // A held direction quickens: an even pace for the first second, then faster, then faster again, so a
                // long list is crossed without a wait and a short one never notices.
                // Only up and down quicken: left and right step settings, where an even pace is what is wanted.
                val held = SystemClock.uptimeMillis() - holdFrom
                val lists = it == Key.Up || it == Key.Down
                handler.postDelayed(this, if (!lists || held < 1300) 70L else if (held < 2600) 50L else 34L)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Pictures: a generous in-memory shelf and no disk copy, since they are already files on the card.
        Coil.setImageLoader(
            ImageLoader.Builder(applicationContext)
                .memoryCache { MemoryCache.Builder(applicationContext).maxSizePercent(0.22).build() }
                .diskCache(null).crossfade(70).build())
        CrashNote.install(applicationContext)
        app = AppState(applicationContext, lifecycleScope)
        Feel.init(applicationContext, app.prefs)
        if (savedInstanceState == null) CrashNote.unseen(applicationContext, app.prefs)?.let { app.say("Timeline closed unexpectedly last time. What went wrong is written in Timeline/crash on this device.") }
        app.requestHome = ::askForHome
        app.wake = ::wakeFromRest
        app.applyRest = ::applyRest
        // The screen going dark with the launcher in front: the rest screen is what comes back, above the lock screen.
        ContextCompat.registerReceiver(this, screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)
        app.pickFolder = { intent, code -> runCatching { @Suppress("DEPRECATION") startActivityForResult(intent, code) }.onFailure { app.say("Android's folder picker could not be opened.") } }
        Installs.host = java.lang.ref.WeakReference(this)
        hideBars()
        preferFastestRefresh()
        setContent {
            LauncherRoot(app, idleFor = { SystemClock.uptimeMillis() - lastInput }, inFront = { inFront }, grant = ::askForFiles)
        }
        // Started afresh by the Home button while a game was in front: a Home press, only arriving a different way.
        if (savedInstanceState == null && intent?.hasCategory(Intent.CATEGORY_HOME) == true) app.startedByHome()
        if (savedInstanceState == null) asked(intent)      // not again when Android rebuilds the screen from an old intent
    }

    /**
     * In a debug build only, a test over adb may name a game to start: the extra "play" holds its key, "system/file
     * name". A release build ignores it, so no other app can start games through the launcher.
     */
    private fun asked(intent: Intent?) {
        if ((applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) == 0) return
        val key = intent?.getStringExtra("play")?.takeIf { it.isNotEmpty() } ?: return
        intent.removeExtra("play")
        if (intent.getBooleanExtra("test", false)) app.testStart(key)   // asked for by test tools over adb
        // Started once the launcher has finished coming to the front, as a press of A would be. Started on the way in,
        // the arrival looked like a return from that same game: "closed as soon as it opened", and the heat guard
        // stopped as it started, which made Android close the launcher (seen on a Retroid Pocket Nova).
        window.decorView.post { app.playWhenReady(key) }
    }

    // A notice from Android itself (such as "RetroArch isn't responding") takes the buttons away from the launcher
    // without sending it behind anything. When they come back, the notice has been answered.
    private var inFront = false
    private var noticeOver = false
    // Hidden behind something (a game, Android's settings) since the launcher last came to the front. Android pauses the
    // launcher before handing it a Home press even when it is already on screen, so being paused cannot tell the two apart.
    private var wasHidden = false

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) { hideBars(); keepFocus() }
        if (!hasFocus) {
            noticeOver = inFront
            held = 0; stick = null; handler.removeCallbacks(stickRepeat)     // a notice took the stick's release: stop scrolling
        }
        else if (noticeOver) { noticeOver = false; app.noticeAnswered() }
    }

    override fun onPause() {
        super.onPause()
        // Put to sleep from the launcher (the power button): waking shows the rest screen. Decided here, because without
        // Android's lock screen the launcher is paused a moment before the screen-off message arrives, and by then it
        // no longer counts as in front (seen on a Retroid Pocket Nova).
        val asleep = getSystemService(android.os.PowerManager::class.java)?.isInteractive == false
        if (inFront && asleep && app.current != Screen.Idle && app.textEntry == null && app.learning == null) app.push(Screen.Idle)
        inFront = false; noticeOver = false
        app.prefs.put("ui.front", false)
        held = 0; stick = null; handler.removeCallbacks(stickRepeat)      // nothing keeps scrolling behind another app
        app.paused()
    }

    override fun onResume() {
        super.onResume()
        // Only when coming back from a game: nobody is holding START and SELECT after Android's settings or a nap.
        frontSince = if (app.prefs.str("playing").isNotEmpty()) SystemClock.uptimeMillis() else 0L
        inFront = true
        app.prefs.put("ui.front", true)                        // battery care: the launcher, not an app, is on screen
        wasHidden = false
        hideBars()
        app.lookAtTheDate()                                    // "This week in history" follows the calendar
        lastInput = SystemClock.uptimeMillis()
        // The rate the panel is running at right now, which a power saver or a system setting may hold below its best.
        @Suppress("DEPRECATION") windowManager.defaultDisplay?.refreshRate?.takeIf { it >= 30f }?.let { app.refreshRate = it }
        app.returned()
        app.rescan()
    }

    /** A card going in or coming out: read the library again. */
    private val cardWatcher = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { app.rescan() }
    }

    /** Android's battery broadcast: the level as the status bar shows it, and whether a charger is in. */
    private val batteryWatcher = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { batteryFrom(intent) }
    }

    // Android's battery saver holds the processor back, and games stutter under it: the header says when it is on.
    private val saverWatcher = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { readSaver() }
    }

    private fun readSaver() {
        app.powerSave = runCatching { getSystemService(android.os.PowerManager::class.java).isPowerSaveMode }.getOrDefault(false)
    }

    private fun batteryFrom(intent: Intent?) {
        if (intent == null) return
        val level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100)
        val plugged = intent.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0) != 0
        if (level >= 0 && scale > 0) app.batteryChanged(level * 100 / scale, plugged)
    }

    override fun onStart() {
        super.onStart()
        // Registered while on screen; the first answer is the current reading, so the header is right at once.
        batteryFrom(ContextCompat.registerReceiver(this, batteryWatcher, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED))
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED); addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_EJECT); addAction(Intent.ACTION_MEDIA_REMOVED); addAction(Intent.ACTION_MEDIA_BAD_REMOVAL)
            addDataScheme("file")
        }
        ContextCompat.registerReceiver(this, cardWatcher, filter, ContextCompat.RECEIVER_EXPORTED)
        readSaver()
        ContextCompat.registerReceiver(this, saverWatcher, IntentFilter(android.os.PowerManager.ACTION_POWER_SAVE_MODE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenOff) }
        super.onDestroy()
    }

    override fun onStop() {
        super.onStop()
        wasHidden = true
        runCatching { unregisterReceiver(cardWatcher) }
        runCatching { unregisterReceiver(batteryWatcher) }
        runCatching { unregisterReceiver(saverWatcher) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // The Home button, when this is the Home app, lands here. Coming back from a game it returns to the page the game
        // was started from, as it was left; pressed with the launcher already on screen, it goes back to the top.
        if (intent.hasCategory(Intent.CATEGORY_HOME)) {
            app.leftWithHome()
            // On the rest screen above the lock screen, Home goes through the lock screen first, like any button.
            val locked = getSystemService(android.app.KeyguardManager::class.java)?.isKeyguardLocked == true
            if (app.current == Screen.Idle && locked) wakeFromRest() else if (!wasHidden && !app.awayForGame) app.home()
        }
        asked(intent)
    }


    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (inFront && app.current != Screen.Idle && app.textEntry == null && app.learning == null) app.push(Screen.Idle)
        }
    }

    /**
     * The rest screen: dimmed to a low light that can still be read in a lit room (0.04, about 22 nits on the Nova's
     * panel; its lowest, 0.01, was about 6 and hard to see), shown above any lock screen, and kept lit (when the
     * player asked for that) instead of letting Android turn the screen off.
     */
    private fun applyRest(idle: Boolean, lit: Boolean) {
        runCatching { setShowWhenLocked(idle) }
        if (lit) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply {
            screenBrightness = if (idle) 0.04f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
    }

    /** A button on the rest screen: through Android's lock screen without showing it (it has no code), then back to the page. */
    private fun wakeFromRest() {
        val km = getSystemService(android.app.KeyguardManager::class.java)
        if (km != null && km.isKeyguardLocked) {
            // Full light for whatever Android shows on the way through; dimmed again if it is left there.
            window.attributes = window.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
            km.requestDismissKeyguard(this, object : android.app.KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() { if (app.current == Screen.Idle) app.pop() }
                override fun onDismissCancelled() { applyRest(app.current == Screen.Idle, app.keepLit) }
                override fun onDismissError() { applyRest(app.current == Screen.Idle, app.keepLit) }
            })
        } else if (app.current == Screen.Idle) app.pop()
    }

    private fun hideBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    /** Ask the panel for its fastest mode at the current resolution (120 Hz on the Nova). */
    private fun preferFastestRefresh() {
        @Suppress("DEPRECATION") val display = windowManager.defaultDisplay ?: return
        val now = display.mode
        val best = display.supportedModes
            .filter { it.physicalWidth == now.physicalWidth && it.physicalHeight == now.physicalHeight }
            .maxByOrNull { it.refreshRate } ?: now
        window.attributes = window.attributes.apply { preferredDisplayModeId = best.modeId }
        app.refreshRate = best.refreshRate
    }

    /** Android's own "use this as the home app?" question, one tap. The settings page is the fallback. */
    private fun askForHome() {
        val asked = runCatching {
            val roles = getSystemService(RoleManager::class.java)
            if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME) && !roles.isRoleHeld(RoleManager.ROLE_HOME)) {
                askedForHomeAt = SystemClock.uptimeMillis()
                @Suppress("DEPRECATION") startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_HOME), HOME_REQUEST)
                true
            } else false
        }.getOrDefault(false)
        if (!asked) Runner.openSettings(this, Settings.ACTION_HOME_SETTINGS)
    }

    private var askedForHomeAt = 0L

    @Deprecated("The role request has no newer form that works from a plain activity.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION") super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == TREE_DOLPHIN) { app.folderPicked(requestCode, if (resultCode == RESULT_OK) data?.data else null); return }
        // Some devices answer "no" at once without showing the question. Then the settings page is the way in.
        if (requestCode == HOME_REQUEST && !Runner.isHome(this) && SystemClock.uptimeMillis() - askedForHomeAt < 700)
            Runner.openSettings(this, Settings.ACTION_HOME_SETTINGS)
        app.touchSettings()
    }

    private companion object { const val HOME_REQUEST = 41 }

    private fun askForFiles() {
        val direct = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName"))
        try {
            startActivity(direct)
        } catch (e: Exception) {
            runCatching { startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
        }
    }

    // ---- input --------------------------------------------------------------------------------

    private fun map(code: Int): Key? {
        val k = when (code) {
            KeyEvent.KEYCODE_DPAD_UP -> Key.Up
            KeyEvent.KEYCODE_DPAD_DOWN -> Key.Down
            KeyEvent.KEYCODE_DPAD_LEFT -> Key.Left
            KeyEvent.KEYCODE_DPAD_RIGHT -> Key.Right
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> Key.A
            KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> Key.B
            KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_X -> Key.X
            KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_Y -> Key.Y
            KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_L2, KeyEvent.KEYCODE_PAGE_UP -> Key.L1
            KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_BUTTON_R2, KeyEvent.KEYCODE_PAGE_DOWN -> Key.R1
            KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_MENU -> Key.Start
            KeyEvent.KEYCODE_BUTTON_SELECT -> Key.Select
            else -> null
        } ?: return null
        // Some pads put confirm on the right-hand face button; one setting flips the pair.
        val pad = code == KeyEvent.KEYCODE_BUTTON_A || code == KeyEvent.KEYCODE_BUTTON_B
        return if (app.swapConfirm && pad) (if (k == Key.A) Key.B else Key.A) else k
    }

    private fun press(key: Key) {
        lastInput = SystemClock.uptimeMillis()
        app.used()
        // To whichever screen is on top of the stack at this instant, even while it is still sliding in.
        val owner: Any = if (app.hasAccess) app.entry else Unit
        val depth = app.stack.size
        // Any button only wakes the rest screen, and on the typing pages A is a key being typed: no note for either.
        val resting = app.current == Screen.Idle || app.current == Screen.Search || app.current is Screen.Naming
        val at = SystemClock.uptimeMillis()
        val handled = Keys.handlers[owner]?.invoke(key) ?: false
        if (!handled && key == Key.B) app.pop()
        if (!resting && Feel.lastStart < at) when {      // a game that started has had its own chime
            app.stack.size > depth -> Feel.opened()
            app.stack.size < depth -> Feel.closed()
            key == Key.A && handled -> Feel.opened()      // Play, a choice on a page: something was done
        }
    }

    /**
     * After the screen has been touched, Android spends the next d-pad press on choosing something to focus, and the
     * press never arrives (seen on Android 13: touch, then Down, and the highlight stays put). It does not do that
     * while something inside the window already holds focus, so the page is handed it back whenever a touch ends.
     */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        lastInput = SystemClock.uptimeMillis()            // a touch is use too: no rest screen in the middle of it
        val handled = super.dispatchTouchEvent(ev)
        if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) keepFocus()
        return handled
    }

    private fun keepFocus() {
        findViewById<android.view.ViewGroup>(android.R.id.content)?.let { page ->
            if (!page.hasFocus()) { page.isFocusable = true; page.isFocusableInTouchMode = true; page.requestFocus() }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // The volume keys, in the launcher: the volume changes without Android's slider, and the launcher says the level.
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            // Held down, a step every few repeats, so it climbs at a pace that can be followed.
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount % 4 == 0) runCatching {
                // The launcher's own steps (VolumeSteps): off, then tens.
                val am = getSystemService(android.media.AudioManager::class.java)
                val stream = android.media.AudioManager.STREAM_MUSIC
                val max = am.getStreamMaxVolume(stream).coerceAtLeast(1)
                val next = VolumeSteps.next(VolumeSteps.level(am.getStreamVolume(stream), max), if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP) 1 else -1)
                am.setStreamVolume(stream, VolumeSteps.index(next, max), 0)
                app.showVolume(next)
            }
            return true
        }
        if (app.textEntry != null) {
            // Typing: let the keyboard have the keys. Back closes the box, on a press that began while the box was
            // open: with confirm and back swapped the box is opened by the B button, and that same press being let
            // go must not shut it again.
            val padBack = if (app.swapConfirm) KeyEvent.KEYCODE_BUTTON_A else KeyEvent.KEYCODE_BUTTON_B      // whichever face button means "back"
            if (event.keyCode == KeyEvent.KEYCODE_BACK || event.keyCode == padBack) {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) closingBox = true
                else if (event.action == KeyEvent.ACTION_UP && closingBox) { closingBox = false; app.textEntry = null }
                return true
            }
            // The other face button means nothing while typing. Left to Android it would come back as a Back press.
            if (event.keyCode == KeyEvent.KEYCODE_BUTTON_A || event.keyCode == KeyEvent.KEYCODE_BUTTON_B) return true
            return super.dispatchKeyEvent(event)
        }
        closingBox = false
        if (app.learning != null) {
            // The Controls page is waiting to hear what M1 or M2 sends. B backs out; anything else is recorded.
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                if (event.keyCode == KeyEvent.KEYCODE_BUTTON_B || event.keyCode == KeyEvent.KEYCODE_BACK) app.learning = null
                else app.learned(event.keyCode)
            }
            return true
        }
        val key = map(event.keyCode) ?: return super.dispatchKeyEvent(event)
        // A game is left by holding START and SELECT. The fingers are still on them when the launcher comes to the
        // front, and a launcher that Android had cleared away and has just started again is handed those presses as
        // if they were new (seen: it opened Settings; in a list SELECT would have changed the order). Neither button
        // means anything in the launcher's first moment in front.
        if ((key == Key.Start || key == Key.Select) && SystemClock.uptimeMillis() - frontSince < 900) return true
        val moves = key == Key.Up || key == Key.Down || key == Key.Left || key == Key.Right
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            app.lastPad = event.keyCode
            press(key)
            // A held direction keeps scrolling at the launcher's own even pace, the same for the d-pad as for the
            // stick, rather than at whatever repeat rate Android happens to be set to.
            if (moves) { held = event.keyCode; stick = key; holdFrom = SystemClock.uptimeMillis(); handler.removeCallbacks(stickRepeat); handler.postDelayed(stickRepeat, 330) }
        } else if (event.action == KeyEvent.ACTION_UP && moves && event.keyCode == held) {
            held = 0; stick = null; handler.removeCallbacks(stickRepeat)
        }
        return true
    }

    private var held = 0
    private var closingBox = false
    private var frontSince = 0L

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        val joystick = ev.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
        if (!joystick || ev.action != MotionEvent.ACTION_MOVE || app.textEntry != null) return super.dispatchGenericMotionEvent(ev)
        fun axis(a: Int, b: Int): Float = ev.getAxisValue(a).let { if (it != 0f) it else ev.getAxisValue(b) }
        val x = axis(MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_X)
        val y = axis(MotionEvent.AXIS_HAT_Y, MotionEvent.AXIS_Y)
        val dir = when {
            y < -0.55f -> Key.Up
            y > 0.55f -> Key.Down
            x < -0.55f -> Key.Left
            x > 0.55f -> Key.Right
            else -> null
        }
        if (held != 0) return true                     // a d-pad key is being held: it has the floor
        if (dir != stick) {
            stick = dir
            handler.removeCallbacks(stickRepeat)
            if (dir != null) {
                press(dir)
                holdFrom = SystemClock.uptimeMillis()
                handler.postDelayed(stickRepeat, 330)
            }
        }
        return true
    }
}

@Composable
fun LauncherRoot(app: AppState, idleFor: () -> Long, inFront: () -> Boolean, grant: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().background(if (app.trueBlack) Ink.Black else Ink.Soft)) {
        // The stage: 960 design pixels tall, scaled to the screen's height. A 4:3 screen shows exactly 1280 across; a wider
        // one (16:9, or the 2:1 of a phone held sideways) gets a wider stage rather than bars, and the pages use the room.
        // Narrower than 4:3, the stage keeps 1280 across and centers. Worked out from real pixels and the density of this
        // moment, so a display size changed in Android's settings never leaves the stage at its old scale.
        val density = LocalDensity.current.density
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val k = minOf(w / 1280f, h / 960f) / density                      // dp per design pixel
        val stageW = (minOf(w, h * 2f) / density).coerceAtLeast(1280f * k)
        CompositionLocalProvider(LocalStage provides Stage(k, stageW / k), LocalApp provides app) {
            Box(Modifier.size(stageW.dp, (960f * k).dp).align(Alignment.Center)) {
                if (!app.hasAccess) {
                    PermissionGate(grant)
                } else {
                    ScreenHost(app) { screen ->
                        when (screen) {
                            Screen.Timeline -> TimelineScreen()
                            is Screen.Games -> GamesScreen(screen)
                            is Screen.Detail -> DetailScreen(screen)
                            Screen.ShelfList -> ShelfListScreen()
                            is Screen.Menu -> MenuScreen(screen)
                            Screen.Apps -> AppsScreen()
                            Screen.Idle -> IdleScreen()
                            Screen.Search -> SearchScreen()
                            Screen.Scraping -> ScrapingScreen()
                            Screen.Accent -> AccentScreen()
                            is Screen.Naming -> NamingScreen(screen)
                            Screen.Nostos -> NostosScreen()
                        }
                    }
                    // Rest the screen after a quiet spell on any of the launcher's pages, so a list or a game page left
                    // up does not stay lit until Android's own timeout (an OLED panel). Not while a name is being typed or
                    // the scraper is at work; any button brings back the page exactly as it was.
                    LaunchedEffect(app.current == Screen.Idle, app.keepLit) { app.applyRest(app.current == Screen.Idle, app.keepLit) }
                    LaunchedEffect(app.idleMinutes) {
                        while (true) {
                            delay(5_000)
                            val limit = app.idleMinutes * 60_000L
                            val page = app.current
                            // Not over a question that has to stay on top (Did not open, Still open), and not while
                            // M1 or M2 is being taught: the press that woke the screen would be learned as the button.
                            val resting = page == Screen.Idle || page == Screen.Scraping || page is Screen.Naming ||
                                page == Screen.Menu("stuck") || page == Screen.Menu("held") || app.learning != null
                            // Only with the launcher in front: never behind a game, to greet its player on the way back.
                            if (limit > 0 && inFront() && !resting && app.textEntry == null && idleFor() > limit) app.push(Screen.Idle)
                        }
                    }
                }
                Toast()
                VolumeBar()
                app.textEntry?.let { TextEntryBox(it) }
            }
        }
    }
}

/** One screen on its way in, at rest, or on its way out. [x] is 0 at rest, +1 off towards the deeper side, -1 off towards the way back. */
private class Slot(val entry: Entry, at: Float) { val x = Animatable(at) }

/**
 * Shows the screen on top of the stack, and moves between screens with a short slide in the direction of
 * travel: going deeper, the new page arrives from the right as the old one leaves to the left; going back, the
 * reverse. Only position and opacity change, on the drawing layer, so nothing is laid out again during the move.
 * A spring carries its speed through a change of mind, so pressing back mid-slide simply turns it around.
 */
@Composable
fun ScreenHost(app: AppState, content: @Composable (Screen) -> Unit) {
    val slots = remember { mutableStateListOf(Slot(app.entry, 0f)) }
    val saved = rememberSaveableStateHolder()
    val shift = with(LocalDensity.current) { 56.u().toPx() }
    LaunchedEffect(Unit) {
        var from = app.entry.depth
        snapshotFlow { app.entry }.collect { target ->
            if (slots.none { it.entry == target })
                slots.add(Slot(target, when { target.depth > from -> 1f; target.depth < from -> -1f; else -> 0.6f }))
            from = target.depth
            slots.toList().forEach { s ->
                val to = when {
                    s.entry == target -> 0f
                    s.entry.depth > target.depth -> 1f
                    s.entry.depth < target.depth -> -1f
                    else -> -0.6f
                }
                launch {
                    withFrameNanos { }                       // the arriving screen is put together before the clock starts
                    s.x.animateTo(to, spring(dampingRatio = 1f, stiffness = 1600f, visibilityThreshold = 0.004f))
                    if (to != 0f && app.entry != s.entry) {
                        slots.remove(s)
                        if (app.stack.getOrNull(s.entry.depth - 1) != s.entry.screen) saved.removeState(s.entry.toString())
                    }
                }
            }
        }
    }
    slots.forEach { s ->
        key(s.entry) {
            Box(Modifier.fillMaxSize().graphicsLayer {
                val p = s.x.value
                translationX = p * shift
                alpha = (1f - abs(p) / 0.6f).coerceIn(0f, 1f)       // gone by six tenths of the way, so pages barely overlap
            }) {
                CompositionLocalProvider(LocalEntry provides s.entry) {
                    saved.SaveableStateProvider(s.entry.toString()) { content(s.entry.screen) }      // a name Android can keep
                }
            }
        }
    }
}

/** A plain box for the two things that must be typed: the scraper account name and password. */
@Composable
fun TextEntryBox(entry: TextEntry) {
    val app = LocalApp.current
    var text by remember(entry.key) { mutableStateOf(app.prefs.str(entry.key)) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(entry.key) { focus.requestFocus() }
    // In the top third: Android's keyboard covers more than half of a 4:3 screen, and a box in the middle sat behind it.
    Box(Modifier.fillMaxSize().background(Ink.Black.copy(alpha = 0.94f)).padding(horizontal = 160.u()).padding(top = 130.u()), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.u())) {
            Label(entry.title)
            Box(Modifier.fillMaxWidth().border(1.u(), Ink.Accent).padding(horizontal = 24.u(), vertical = 20.u())) {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    textStyle = TextStyle(color = Ink.Cream, fontFamily = Grotesk, fontWeight = FontWeight.Medium, fontSize = 30.t()),
                    cursorBrush = SolidColor(Ink.Accent),
                    visualTransformation = if (entry.secret) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        // A new RetroAchievements password or account name: RetroArch signs in with it afresh at the next game.
                        if (entry.key in setOf("ra.cheevosPass", "ra.cheevosUser") && text.trim() != app.prefs.str(entry.key)) {
                            app.prefs.put("ra.cheevosFresh", true); app.prefs.put("ra.cheevosSigned", "")
                            if (text.isNotBlank()) app.prefs.put("ra.cheevos", true)      // nobody types an account in without wanting it on
                        }
                        app.prefs.put(entry.key, text.trim())
                        app.touchSettings()
                        app.textEntry = null
                    }),
               )
            }
            Label("Done on the keyboard saves. Back cancels.", size = 12)
        }
    }
}
