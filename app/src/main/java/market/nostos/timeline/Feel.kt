// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 NOSTOS, LLC

package market.nostos.timeline

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * The two small kinds of feedback the interface gives, each with its own switch in Settings: a light tick of the
 * vibration motor each time the highlight moves, and quiet sounds (a tick for a step, a rising note going into
 * something, a falling one coming back out). A step down or right ticks a little higher and leans a little right; a
 * step up or left ticks a minor third lower and leans a little left, so the ear can tell which way the list went.
 * A press that would go past the end of a list knocks once, low, instead; a game starting has a chime of its own.
 * Nothing here may slow a press: the sounds are loaded once, at start, and played from memory, and the vibration is a
 * single short effect the motor's maker tuned.
 */
object Feel {
    @Volatile var haptics = true
    @Volatile var sounds = true

    private var vibrator: Vibrator? = null
    private var tick: VibrationEffect? = null
    private var pool: SoundPool? = null
    private val ids = IntArray(6)
    @Volatile private var loaded = 0                    // a bit for each sound that is ready to play
    private const val MOVE = 0
    private const val OPEN = 1
    private const val BACK = 2
    private const val MOVE_BACK = 3
    private const val EDGE = 4
    private const val START = 5
    private const val QUICKEST = 45L                   // ms: steps closer together than this (a fast held scroll) share one tick
    private var lastStep = 0L
    private var lastEdge = 0L
    /** When the last game-start chime played (or a start was declined), so that press plays no "into a page" note. */
    @Volatile var lastStart = 0L
        private set
    private const val LEAN = 0.7f                      // the far side's volume for a step: a lean, not a jump

    fun init(ctx: Context, prefs: Prefs) {
        haptics = prefs.bool("feel.haptics", true)
        sounds = prefs.bool("feel.sounds", true)
        if (pool != null) return
        val app = ctx.applicationContext
        vibrator = runCatching {
            if (Build.VERSION.SDK_INT >= 31) app.getSystemService(VibratorManager::class.java)?.defaultVibrator
            else @Suppress("DEPRECATION") app.getSystemService(Vibrator::class.java)
        }.getOrNull()?.takeIf { it.hasVibrator() }
        tick = vibrator?.let { v ->
            runCatching {
                // The lightest effect the motor offers: a composed tick where the hardware can play one exactly,
                // otherwise the maker's own "tick".
                if (Build.VERSION.SDK_INT >= 30 && v.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_TICK))
                    VibrationEffect.startComposition().addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.6f).compose()
                else VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
            }.getOrNull()
        }
        // The sounds follow the media volume, which is what the volume buttons change on a handheld.
        pool = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
       ).build().also { p ->
            p.setOnLoadCompleteListener { _, id, status -> if (status == 0) ids.indexOf(id).takeIf { it >= 0 }?.let { loaded = loaded or (1 shl it) } }
            ids[MOVE] = p.load(app, R.raw.ui_move, 1)
            ids[OPEN] = p.load(app, R.raw.ui_open, 1)
            ids[BACK] = p.load(app, R.raw.ui_back, 1)
            ids[MOVE_BACK] = p.load(app, R.raw.ui_move_back, 1)
            ids[EDGE] = p.load(app, R.raw.ui_edge, 1)
            ids[START] = p.load(app, R.raw.ui_start, 1)
        }
    }

    /** The highlight moved, or a setting changed by a step: [way] above zero for down or right, below for up or left. */
    fun moved(way: Int) {
        val now = SystemClock.uptimeMillis()
        if (now - lastStep < QUICKEST) return
        lastStep = now
        if (haptics) buzz()
        if (sounds) {
            if (way >= 0) play(MOVE, LEAN, 1f) else play(MOVE_BACK, 1f, LEAN)
        }
    }

    /** A press at the end of a list. Once per push against it: holding the direction there stays quiet. */
    fun edge() {
        val now = SystemClock.uptimeMillis()
        val fresh = now - lastEdge > 400
        lastEdge = now
        if (!fresh) return
        if (haptics) buzz()
        if (sounds) play(EDGE)
    }

    /** A press was turned down (a start refused, say): the press that asked plays no "into" note. */
    fun declined() { lastStart = SystemClock.uptimeMillis() }

    /** A game is starting. */
    fun started() {
        lastStart = SystemClock.uptimeMillis()
        if (sounds) play(START)
    }

    /** Into something: a page, a game. */
    fun opened() { if (sounds) play(OPEN) }

    /** Back out of it. */
    fun closed() { if (sounds) play(BACK) }

    private fun play(which: Int, left: Float = 1f, right: Float = 1f) {
        val p = pool ?: return
        if (loaded and (1 shl which) == 0) return
        runCatching { p.play(ids[which], left, right, 1, 0, 1f) }
    }

    private fun buzz() {
        val v = vibrator ?: return
        val e = tick ?: return
        runCatching {
            // Marked as feedback for a physical button, which is what it is: Android's setting for touch feedback is
            // about the touchscreen, and a maker may ship it switched off on a handheld. This page's own switch decides.
            if (Build.VERSION.SDK_INT >= 33) v.vibrate(e, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_HARDWARE_FEEDBACK))
            else v.vibrate(e)
        }
    }
}
