// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 NOSTOS, LLC

package market.nostos.timeline

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.os.PowerManager
import android.provider.Settings
import java.util.concurrent.Executors

/**
 * The maker's own service on the Nova (and its siblings), which runs a command with full rights for
 * any app that asks. Only commands built by [Vendor.put] are ever sent, one at a time.
 */
object Bridge {
    private fun binder(): IBinder? = runCatching {
        Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java).invoke(null, "PServerBinder") as? IBinder
    }.getOrNull()

    /** Looked up afresh each time: at boot the home app can start before the maker's service is there. */
    val present: Boolean get() = binder() != null

    @Synchronized
    fun run(command: String): Boolean {
        val b = binder() ?: return false
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeStringArray(arrayOf(command, "1"))
            b.transact(0, data, reply, 0)
        } catch (e: Exception) {
            false
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    /**
     * Runs one plain command and returns what it printed. The service runs only the first part of a chained command
     * and does not expand wildcards for every command, so each read is a single command with its files named in full.
     */
    @Synchronized
    fun read(command: String): String? {
        val b = binder() ?: return null
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeStringArray(arrayOf(command, "1"))
            if (!b.transact(0, data, reply, 0)) null else runCatching { reply.createByteArray()?.let { String(it) } }.getOrNull()
        } catch (e: Exception) {
            null
        } finally {
            data.recycle()
            reply.recycle()
        }
    }
}

/** The display calibration ([DisplayCalibration]) on this device: what is in place, and putting it in or taking it out. */
object Panel {
    /**
     * [installed] is what the display's override file is, from a clear answer only: "ours" (the calibrated file),
     * "none" (no override, the panel as shipped), "other" (some other file), or "unknown" (no clear answer).
     */
    class State(val compatible: Boolean, val installed: String, val reason: String) {
        val calibrated: Boolean get() = installed == "ours"
        val foreign: Boolean get() = installed == "other"
    }

    private fun installed(): String {
        val answer = Bridge.read(DisplayCalibration.READ_OVERRIDE) ?: return "unknown"
        val fp = DisplayCalibration.fingerprintIn(answer)
        return when {
            fp == DisplayCalibration.CALIBRATED_SHA256 -> "ours"
            fp.isNotEmpty() -> "other"
            answer.contains("No such file") -> "none"
            else -> "unknown"
        }
    }

    /** Not on the screen's thread: it reads a large file and asks the maker's service. */
    fun state(): State {
        if (!Bridge.present) return State(false, "unknown", "The maker's settings service was not found.")
        val inPlace = installed()
        if (Build.DISPLAY != DisplayCalibration.BUILD) return State(false, inPlace, "The calibration was measured on firmware ${DisplayCalibration.BUILD}, and this device runs ${Build.DISPLAY}.")
        val factory = runCatching { java.io.File(DisplayCalibration.FACTORY).readBytes() }.getOrNull()
            ?: return State(false, inPlace, "This display's color file could not be read.")
        if (DisplayCalibration.sha256(factory) != DisplayCalibration.FACTORY_SHA256) return State(false, inPlace, "This display's color file is not the one the calibration was measured against.")
        return State(true, inPlace, "")
    }

    /** Builds the calibration from this device's own file and puts it in place. Null when done, or what went wrong. */
    fun calibrate(ctx: Context): String? {
        val st = state()
        if (!st.compatible) return st.reason
        when (st.installed) {
            "ours" -> return null
            "other" -> return "Another app's display file is already in place, so it is left alone."
            "unknown" -> return "The display's color file could not be checked, so nothing was changed."
        }
        val patch = runCatching { ctx.assets.open(DisplayCalibration.PATCH_ASSET).use { it.readBytes() } }.getOrNull() ?: return "The calibration is missing from the launcher."
        val made = DisplayCalibration.rebuild(java.io.File(DisplayCalibration.FACTORY).readBytes(), patch) ?: return "The calibration could not be rebuilt for this display."
        val src = java.io.File(ctx.cacheDir, "qdcm-calibrated.json")
        return try {
            src.writeBytes(made)
            src.setReadable(true, false)
            val cmds = DisplayCalibration.installCommands(src.absolutePath) ?: return "The calibration could not be put in place."
            for (c in cmds) Bridge.run(c)
            Bridge.run(DisplayCalibration.REMOVE_TMP)
            val owner = Bridge.read(DisplayCalibration.READ_OWNER).orEmpty()
            if (installed() == "ours" && owner.contains(DisplayCalibration.OWNER_OK)) null
            else { Bridge.run(DisplayCalibration.REMOVE); "The calibration did not go in place, so nothing was changed." }
        } finally { src.delete() }
    }

    /** Takes out the calibration this launcher put in ([ours]), whatever the firmware. Null when done, or what went wrong. */
    fun stock(ours: Boolean): String? = when (installed()) {
        "none" -> null
        "unknown" -> "The display's color file could not be checked, so nothing was changed."
        "other" -> "Another app's display file is in place, so it is left alone."
        else -> if (!ours) "The calibration was put in place by another app; take it out there." else {
            Bridge.run(DisplayCalibration.REMOVE)
            Bridge.run(DisplayCalibration.REMOVE_TMP)
            if (installed() == "none") null else "The calibration could not be taken out."
        }
    }

    fun saturation(): String = DisplayCalibration.decimalIn(Bridge.read(DisplayCalibration.READ_SATURATION))
    fun color(mode: Int, saturation: String) { DisplayCalibration.colorCommands(mode, saturation)?.forEach { Bridge.run(it) } }
}

/**
 * The chip's temperature. The sensors are read directly where Android allows it (it does on the Nova's firmware), and
 * otherwise through the maker's service, one command naming every file in full. Sensors are found once by name.
 */
object Heat {
    @Volatile private var zones: List<Int>? = null

    private fun findZones(): List<Int> {
        val dir = java.io.File(Vendor.THERMAL)
        val listing = (dir.listFiles() ?: emptyArray()).filter { it.name.startsWith("thermal_zone") }.mapNotNull { z ->
            runCatching { "${z.path}/type:" + java.io.File(z, "type").readText().trim() }.getOrNull()
        }.joinToString("\n")
        return Vendor.chipSensors(listing)
    }

    /** The hottest processor or graphics sensor in °C, or NaN. Not on the screen's thread. */
    fun chip(): Float {
        val z = zones ?: findZones().also { if (it.isNotEmpty()) zones = it }
        if (z.isEmpty()) return Float.NaN
        val direct = z.mapNotNull { n -> runCatching { java.io.File("${Vendor.THERMAL}/thermal_zone$n/temp").readText().trim() }.getOrNull() }
        if (direct.isNotEmpty()) return Vendor.hottest(direct.joinToString("\n"))
        if (!Bridge.present) return Float.NaN
        val cmd = Vendor.readSensors(z) ?: return Float.NaN
        return Vendor.hottest(Bridge.read(cmd).orEmpty())
    }

    /** The battery's temperature in °C, from the notice Android keeps for every app, or NaN. */
    fun battery(ctx: Context): Float = runCatching {
        val i = ctx.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        i?.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)?.takeIf { it != Int.MIN_VALUE }?.let { it / 10f }
    }.getOrNull() ?: Float.NaN
}

object Device {
    /** Runs device work in order, away from the screen's thread. */
    private val worker = Executors.newSingleThreadExecutor()

    data class Found(val model: String, val bridge: Boolean, val lights: String?, val fan: String?, val performance: String?,
                     val chip: Float = Float.NaN, val battery: Float = Float.NaN) {
        val canLight: Boolean get() = bridge && lights != null
        val canPower: Boolean get() = bridge && fan in Vendor.knownFan && performance != null
        /** Retroid's charge limit reaches the charger here (its driver has the switch). */
        val canHoldCharge: Boolean get() = bridge && java.io.File("/sys/class/qcom-battery/limit_capacity_charge").exists()
    }

    private fun read(ctx: Context, key: String): String? = runCatching { Settings.System.getString(ctx.contentResolver, key) }.getOrNull()

    /** Reading needs no special rights, so this is safe on any device. Not on the screen's thread (the temperatures wait on the maker's service). */
    fun probe(ctx: Context): Found = Found(
        Build.MODEL ?: "", Bridge.present, read(ctx, Vendor.LIGHT_COLOR), read(ctx, Vendor.FAN),
        // The setting says the device has power modes at all; the property says which one is applied.
        read(ctx, Vendor.PERFORMANCE)?.let { setting -> (if (Bridge.present) performanceNow() else null) ?: setting },
        Heat.chip(), Heat.battery(ctx),
   )

    /**
     * Writes one setting, but only if this device already has it, the value has the expected shape,
     * and (for the fan) the device's current value is one the maker is known to use. Checks it took.
     */
    private fun set(ctx: Context, key: String, value: String): Boolean {
        // Two of Retroid's settings have no value until first changed: the charge limit (off) and the link (on).
        val now = read(ctx, key) ?: when (key) { Vendor.CHARGE_LIMIT -> "0"; Vendor.FAN_LINK -> "1"; else -> return false }
        if (key == Vendor.FAN && now !in Vendor.knownFan) return false
        if (now == value) return true
        val command = Vendor.put(key, value) ?: return false
        if (!Bridge.run(command)) return false
        return read(ctx, key) == value
    }

    fun lights(ctx: Context, argb: Int?, percent: Int) = worker.execute {
        if (argb == null) {
            set(ctx, Vendor.LIGHT_ON, "0,0")
        } else {
            set(ctx, Vendor.LIGHT_ON, "1,1")
            set(ctx, Vendor.LIGHT_LEVEL, Vendor.level(percent))
            set(ctx, Vendor.LIGHT_COLOR, Vendor.both(argb))
        }
        // The settings are what the maker's app reads back; the lights themselves follow their drivers, as there.
        drive(if (argb == null) null else Vendor.driverColor(argb, percent))
    }

    /** Switches both sticks' light drivers on with [color], or off when it is null. Only on a device that has them. */
    private fun drive(color: String?) {
        // Not checked from here first: Android may not let an app look at these files at all. The maker's service can.
        if (!Bridge.present) return
        for (node in Vendor.lightNodes) {
            Vendor.write(node, "enable", if (color == null) "0" else "1")?.let(Bridge::run)
            if (color != null) Vendor.write(node, "brightness", color)?.let(Bridge::run)
        }
    }

    fun power(ctx: Context, p: Power) = worker.execute {
        // Only the property the maker's service acts on (it sets the chip's lowest clock speeds from it every ten
        // seconds). The setting behind Retroid's quick-settings tile is left alone: whenever it changes to Standard,
        // Retroid's fan tile sets the fan to its default, off, and on the charger Retroid then shows "Current charging
        // status, turning off the fan may cause the machine to heat up" (seen on a Retroid Pocket Nova, with Retroid's
        // power-to-fan link on or off). The link is switched off too, so a power change made on the tile by hand does
        // not move the fan either.
        set(ctx, Vendor.FAN_LINK, "0")
        val v = p.performance.toString()
        // Only where the property is there to read: a model without it is not sent one.
        val now = performanceNow()
        if (now != null && now != v) Vendor.setprop(v)?.let(Bridge::run)
        set(ctx, Vendor.FAN, p.fan.toString())
    }

    /**
     * Puts back the fan and power modes found before Power and fan by system was switched on, and Retroid's own link
     * from power to fan. Only values the maker's settings use are written back.
     */
    fun restorePower(ctx: Context, fan: String?, performance: String?) = worker.execute {
        set(ctx, Vendor.FAN_LINK, "1")
        val now = performanceNow()
        if (performance != null && performance in setOf("0", "1", "2") && now != null && now != performance) Vendor.setprop(performance)?.let(Bridge::run)
        if (fan != null && fan in Vendor.knownFan) set(ctx, Vendor.FAN, fan)
    }

    /** The power mode the maker's service is applying: its property, read through the service. */
    fun performanceNow(): String? =
        Bridge.read(Vendor.READ_PERFORMANCE)?.let { Regex("(?m)^\\s*([012])\\s*$").find(it)?.groupValues?.get(1) }

    /**
     * Each change of state (a game starting, the launcher coming back) starts a new epoch; the heat guard's fan changes
     * carry the epoch they were decided in and are dropped, in turn, if a newer one has begun.
     */
    private val epoch = java.util.concurrent.atomic.AtomicInteger(0)
    fun newEpoch(): Int = epoch.incrementAndGet()

    fun fan(ctx: Context, mode: Int, at: Int) = worker.execute { if (at == epoch.get()) set(ctx, Vendor.FAN, mode.toString()) }

    /** Retroid's link from power to fan back on, as it ships: for when the launcher stops choosing power and fan. */
    fun fanLink(ctx: Context, on: Boolean) = worker.execute { set(ctx, Vendor.FAN_LINK, if (on) "1" else "0") }

    /** Retroid's 80% charge limit on or off (see ChargeHold), on the caller's thread; true once it reads back as set. */
    fun chargeHoldNow(ctx: Context, on: Boolean): Boolean = set(ctx, Vendor.CHARGE_LIMIT, if (on) "1" else "0")

    /** Whether the limit is on now, as the setting says. */
    fun chargeHeld(ctx: Context): Boolean = read(ctx, Vendor.CHARGE_LIMIT) == "1"
}

/**
 * Runs only while a game is on screen, and only with the heat guard on. Every ten seconds it reads the hottest
 * processor or graphics sensor and the battery, and lets [HeatGuard] decide the fan mode. The reading is a handful
 * of small files through the maker's service, off the screen's thread: a few thousandths of a second.
 */
class GuardService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val reader = Executors.newSingleThreadExecutor()
    @Volatile private var guard: HeatGuard? = null
    @Volatile private var epoch = 0
    private val reading = java.util.concurrent.atomic.AtomicBoolean(false)
    private val tick = object : Runnable {
        override fun run() {
            val g = guard ?: return
            // One reading at a time: if the maker's service is slow to answer, the next tick is skipped rather than
            // queued, so readings never arrive back to back and the fan never steps faster than intended.
            if (reading.compareAndSet(false, true)) runCatching {
                reader.execute {
                    try {
                        val before = g.mode
                        val after = g.next(Heat.chip(), Heat.battery(applicationContext))
                        if (after != before && guard === g) Device.fan(applicationContext, after, epoch)
                    } finally { reading.set(false) }
                }
            }.onFailure { reading.set(false) }
            handler.postDelayed(this, 10_000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Stopped by a command of its own, never by stopService: Android ends the whole launcher if a service started this
        // way is stopped before it has said it is running (seen on the Nova when a start and a return came moments apart).
        // Commands arrive in order, so the start before this one has already said so. A start sent after this one keeps it.
        if (intent?.action == STOP) {
            handler.removeCallbacks(tick)
            guard = null
            stopSelfResult(startId)
            return START_NOT_STICKY
        }
        val base = intent?.getIntExtra("base", Vendor.FAN_SMART) ?: Vendor.FAN_SMART
        epoch = intent?.getIntExtra("epoch", 0) ?: 0
        val started = runCatching {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(NotificationChannel("guard", "Heat guard", NotificationManager.IMPORTANCE_MIN))
            val note = Notification.Builder(this, "guard").setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setContentTitle("Heat guard").setContentText("Watching the temperature while you play").build()
            if (Build.VERSION.SDK_INT >= 34) startForeground(1, note, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            else startForeground(1, note)
        }.isSuccess
        if (!started) {
            stopSelfResult(startId)
            return START_NOT_STICKY
        }
        guard = HeatGuard(base)
        handler.removeCallbacks(tick)
        handler.postDelayed(tick, 10_000)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        guard = null
        reader.shutdown()
        super.onDestroy()
    }

    companion object {
        const val STOP = "market.nostos.timeline.STOP"

        /** A guard was started for a game and not yet stopped. Kept for the whole process, not one screen's state. */
        @Volatile var asked = false

        /** Ends the guard, if it runs, in a way that cannot race its own start. Only asked while the launcher is in front. */
        fun stop(ctx: Context) {
            runCatching { ctx.startService(Intent(ctx, GuardService::class.java).setAction(STOP)) }
        }
    }
}

/**
 * Keeps the speaker tone shape applied to everything the device plays, for as long as the device is on.
 * It holds one system-wide effect (the [SpeakerChain]); Android drops it the moment nothing is holding it, which is
 * why this is a service of its own and why it is started again after a restart.
 */
class SoundService : Service() {
    private var eq: Equalizer? = null
    private val routes = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>?) = apply()
        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>?) = apply()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        runCatching { (getSystemService(Context.AUDIO_SERVICE) as AudioManager).registerAudioDeviceCallback(routes, Handler(Looper.getMainLooper())) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Switched off by a command, for the same reason as the heat guard: see GuardService.
        if (intent?.action == GuardService.STOP) {
            stopSelfResult(startId)
            return START_NOT_STICKY
        }
        runCatching {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(NotificationChannel("sound", "Speaker tuning", NotificationManager.IMPORTANCE_MIN))
            val note = Notification.Builder(this, "sound").setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setContentTitle("Speaker tuning").setContentText("Shaping the sound of the built-in speakers").build()
            if (Build.VERSION.SDK_INT >= 34) startForeground(2, note, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            else startForeground(2, note)
        }
        apply()
        return START_STICKY                                  // if Android stops it, it comes back and applies the saved shape
    }

    /** Headphones, Bluetooth and HDMI have their own sound: the shape is for the built-in speakers. */
    private fun external(): Boolean = runCatching {
        (getSystemService(Context.AUDIO_SERVICE) as AudioManager).getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
            it.type in setOf(AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_LINE_ANALOG, 26, 27)
        }
    }.getOrDefault(false)

    /** The equaliser is set up and changed away from the screen's thread: opening it can take a moment. */
    private val worker = Executors.newSingleThreadExecutor()

    private fun apply() { runCatching { worker.execute { applyNow() } } }

    private var chain: DynamicsProcessing? = null
    private var made = ""                                   // what the chain was built for: tone and compression

    private fun applyNow() {
        val prefs = Prefs(this)
        val curve = Eq.parse(prefs.str("eq.curve"))
        val compress = prefs.bool("eq.compress", false)
        val quiet = prefs.bool("eq.speakersOnly", true) && external()
        // The full chain first (Android's DynamicsProcessing); on a device without it, the five-band equaliser.
        state = runCatching {
            val want = Eq.format(curve) + "|" + compress
            val c = chain?.takeIf { made == want } ?: run {
                runCatching { chain?.release() }
                chain = null
                DynamicsProcessing(0, 0, speakerConfig(curve, compress)).also { chain = it; made = want }   // session 0: everything that plays
            }
            runCatching { eq?.release() }; eq = null
            c.enabled = !quiet
            if (quiet) "Resting: headphones or another output is connected"
            else "Working: high-pass, shaping" + (if (compress) ", compressor" else "") + " and limiter"
        }.getOrElse {
            runCatching { chain?.release() }; chain = null; made = ""
            try {
                val e = eq ?: Equalizer(0, 0).also { eq = it }
                val range = e.bandLevelRange
                for (b in 0 until e.numberOfBands) {
                    val hz = e.getCenterFreq(b.toShort()) / 1000     // reported in thousandths of a hertz
                    val db = SpeakerChain.correction(hz.toDouble()) + Eq.gainAt(hz, curve) / 10.0
                    e.setBandLevel(b.toShort(), (db * 100).toInt().coerceIn(range[0].toInt(), range[1].toInt()).toShort())
                }
                e.enabled = !quiet
                if (quiet) "Resting: headphones or another output is connected" else "Working, ${e.numberOfBands} bands only"
            } catch (e: Throwable) {
                eq = null
                "This device does not offer a system-wide equaliser"
            }
        }
    }

    /** [SpeakerChain] as Android's DynamicsProcessing: both speakers alike, the limiter linking them. */
    private fun speakerConfig(tone: List<Int>, compress: Boolean): DynamicsProcessing.Config {
        val bands = SpeakerChain.bands(tone)
        val comp = SpeakerChain.compressor
        val b = DynamicsProcessing.Config.Builder(DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION, 2,
            true, bands.size, true, comp.size, false, 0, true)
        b.setPreferredFrameDuration(10f)
        b.setInputGainAllChannelsTo(SpeakerChain.INPUT_GAIN)
        val pre = DynamicsProcessing.Eq(true, true, bands.size)
        bands.forEachIndexed { i, (top, gain) -> pre.setBand(i, DynamicsProcessing.EqBand(true, top, gain)) }
        b.setPreEqAllChannelsTo(pre)
        val mbc = DynamicsProcessing.Mbc(true, compress, comp.size)
        comp.forEachIndexed { i, c ->
            mbc.setBand(i, DynamicsProcessing.MbcBand(compress, c.top, c.attack, c.release, c.ratio, c.threshold, c.knee,
                -90f, 1f, 0f, if (compress) c.makeUp else 0f))
        }
        b.setMbcAllChannelsTo(mbc)
        b.setLimiterAllChannelsTo(DynamicsProcessing.Limiter(true, true, 0, SpeakerChain.LIMIT_ATTACK, SpeakerChain.LIMIT_RELEASE,
            SpeakerChain.LIMIT_RATIO, SpeakerChain.LIMIT_THRESHOLD, 0f))
        return b.build()
    }

    override fun onDestroy() {
        runCatching { (getSystemService(Context.AUDIO_SERVICE) as AudioManager).unregisterAudioDeviceCallback(routes) }
        runCatching { worker.execute { runCatching { eq?.release() }; eq = null; runCatching { chain?.release() }; chain = null; made = ""; state = "Off" } }
        worker.shutdown()
        super.onDestroy()
    }

    companion object {
        @Volatile var state: String = "Off"

        /** This device is a Retroid Pocket Nova, whose speakers the tuning was measured on: on by default only there. */
        val nova: Boolean by lazy { NovaCheck.isNova(Build.DISPLAY, Build.MODEL).also { SpeakerChain.onNova = it } }

        /** Starts, updates or stops the service to match the saved choices. */
        fun sync(ctx: Context) {
            val on = Prefs(ctx).bool("eq.on", nova)
            runCatching {
                if (on) ctx.startForegroundService(Intent(ctx, SoundService::class.java))
                else ctx.startService(Intent(ctx, SoundService::class.java).setAction(GuardService.STOP))
            }
        }
    }
}

/** Puts the speaker tuning back after the device restarts. */
/**
 * Battery care on the charger, the doing part of [ChargeHold]. Decides from what is saved (the last use, whether a game
 * or another app is in front, the choice in Settings) and from the charger and the screen right now. Runs from the
 * launcher once a minute while it is awake and from an alarm while the Nova sleeps on the charger, so the half hour is
 * real time, not awake time. Compares with Retroid's setting itself each time, so a failed write is tried again, and
 * only ever switches off a limit it switched on (one set on Retroid's own tile is left alone).
 */
object ChargeCare {
    private const val NODE = "/sys/class/qcom-battery/limit_capacity_charge"

    fun available(): Boolean = Bridge.present && java.io.File(NODE).exists()

    /**
     * Every decision and its write, one at a time on this thread, each write finished before the next decision. With
     * the write queued elsewhere a decision could read the limit as still off and drop the record that the launcher had
     * set it, leaving it on for good (found on a Retroid Pocket Nova).
     */
    private val worker = Executors.newSingleThreadExecutor()

    /** Decides and sets on the care's own thread; [done] gets the outcome there. */
    fun applyLater(ctx: Context, done: (Boolean?) -> Unit = {}) =
        worker.execute { done(runCatching { apply(ctx) }.getOrNull()) }
    fun held(ctx: Context): Boolean = runCatching { Settings.System.getString(ctx.contentResolver, Vendor.CHARGE_LIMIT) == "1" }.getOrDefault(false)

    @Volatile private var lastUse = 0L
    @Volatile private var savedUse = -30_000L        // the first use after a start is saved at once
    /** A button, a game, an app coming and going. Kept exactly in memory; saved (for the alarm) at most twice a minute. */
    fun used(ctx: Context) {
        val now = android.os.SystemClock.elapsedRealtime()
        lastUse = now
        if (now - savedUse < 30_000) return
        savedUse = now
        Prefs(ctx).put("charge.used", now)
    }

    /** Decides and sets, the write finished before it returns. Only from [applyLater]'s thread. Returns whether the limit
     *  is on afterwards (null where there is none to set). */
    private fun apply(ctx: Context): Boolean? {
        if (!available()) return null
        val prefs = Prefs(ctx)
        val battery = runCatching { ctx.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED)) }.getOrNull()
        val plugged = (battery?.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        val screenOn = ctx.getSystemService(PowerManager::class.java)?.isInteractive != false
        // In use: a game, or the screen on with something other than the launcher in front (an app opened from it).
        val inUse = prefs.str("playing").isNotEmpty() || (screenOn && !prefs.bool("ui.front", false))
        // A time saved before a restart counts from that start and can be later than now: it is not a use.
        val used = maxOf(lastUse, prefs.long("charge.used", 0L).takeIf { it <= android.os.SystemClock.elapsedRealtime() } ?: 0L)
        val idle = android.os.SystemClock.elapsedRealtime() - used
        val want = ChargeHold.wanted(prefs.str("dev.charge", ChargeHold.modes.first()), plugged, inUse, idle)
        val actual = held(ctx)
        val ours = prefs.bool("charge.ours", false)
        // The record that the limit is the launcher's is written at once (not later in the background), and after a
        // write from what Retroid's setting then says, so a launcher closed a moment later cannot lose it.
        val after = when {
            want && !actual -> { prefs.putNow("charge.ours", true); Device.chargeHoldNow(ctx, true); held(ctx).also { prefs.putNow("charge.ours", it) } }
            !want && actual && ours -> { Device.chargeHoldNow(ctx, false); held(ctx).also { prefs.putNow("charge.ours", it) } }
            !actual && ours -> { prefs.putNow("charge.ours", false); false }    // switched off elsewhere: no longer ours
            else -> actual
        }
        schedule(ctx, plugged, used)
        return after
    }

    /** The next look while the Nova sleeps: at the half-hour mark, then every half hour on the charger; none off it. */
    private fun schedule(ctx: Context, plugged: Boolean, used: Long) {
        val am = ctx.getSystemService(android.app.AlarmManager::class.java) ?: return
        val pi = PendingIntent.getBroadcast(ctx, 7, Intent(ctx, ChargeCheck::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        if (!plugged) { am.cancel(pi); return }
        val now = android.os.SystemClock.elapsedRealtime()
        val due = used + ChargeHold.REST_MS + 5_000
        runCatching { am.setAndAllowWhileIdle(android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP, if (due > now) due else now + ChargeHold.REST_MS, pi) }
    }
}

/**
 * Writes a script the maker's service is about to run: only when its text changed, and through a file of its own that is
 * then renamed over the old one, so a script already being run (another thread asking the same thing) is never seen
 * half-written.
 */
object Scripts {
    fun write(file: java.io.File, text: String): Boolean = runCatching {
        if (file.isFile && file.readText() == text) return true
        val tmp = java.io.File(file.path + ".new" + Thread.currentThread().id)
        tmp.writeText(text)
        if (!tmp.renameTo(file)) { tmp.delete(); return false }
        true
    }.getOrDefault(false)
}

/**
 * Whether Dolphin is frozen behind the launcher. Left while the device was stalled for memory, Dolphin's main thread can
 * go into a quick save while the game is already ending, and stay there; Android then hands every later GameCube or
 * Wii start to that frozen screen, and nothing opens. Read as root from the kernel's own
 * record of each thread: is Dolphin running, does it still have the thread that runs a game ("NativeEmulation"), and
 * where is its main thread waiting. A healthy one waits for input (do_epoll_wait); a frozen one waits on a lock
 * (futex_wait_queue_me) every time it is looked at, while the process does no work at all (see [script]).
 */
object DolphinWatch {
    private val PACKAGE = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")

    /**
     * Frozen only when, over three seconds, the main thread is found waiting on a lock every time AND the whole process
     * does no work: at most two ticks of processor time across all its threads, and not a byte written. A game that is
     * slow to end (flushing its memory card or shader cache under memory pressure) still works or writes, and is left
     * alone. Shell builtins (read) rather than a program per file, so it stays quick under memory pressure.
     */
    fun script(pkg: String): String = """
        p=${'$'}(pidof $pkg | cut -d' ' -f1)
        [ -n "${'$'}p" ] || { echo none; exit 0; }
        g=""
        for t in /proc/${'$'}p/task/*; do n=""; read -r n < ${'$'}t/comm; [ "${'$'}n" = "NativeEmulation" ] && g=${'$'}t; done
        [ -n "${'$'}g" ] || { echo idle; exit 0; }
        work() {
          l=""; read -r l < /proc/${'$'}p/stat; set -- ${'$'}l; c=${'$'}((${'$'}{14} + ${'$'}{15}))
          w=0; while read -r k v; do [ "${'$'}k" = "wchar:" ] && w=${'$'}v; done < /proc/${'$'}p/io
        }
        work; c0=${'$'}c; w0=${'$'}w
        n=0
        for i in 1 2 3 4 5 6; do
          s=""; read -r s < /proc/${'$'}p/wchan
          case "${'$'}s" in futex*) n=${'$'}((n+1)) ;; esac
          sleep 0.5
        done
        work
        if [ ${'$'}n = 6 ] && [ ${'$'}((c - c0)) -le 2 ] && [ "${'$'}w" = "${'$'}w0" ]; then echo frozen; else echo game; fi
    """.trimIndent() + "\n"

    /** Reads the last word the script printed: "none", "idle", "game" or "frozen"; null when it could not be asked. */
    fun parse(out: String?): String? = out?.trim()?.lines()?.lastOrNull()?.trim()?.takeIf { it in setOf("none", "idle", "game", "frozen") }

    @Synchronized fun state(ctx: Context, pkg: String): String? {
        if (!PACKAGE.matches(pkg)) return null
        val file = java.io.File(ctx.filesDir, "dolphin-state.sh")
        if (!Scripts.write(file, script(pkg))) return null
        return parse(Bridge.read("timeout 9 sh ${file.absolutePath}"))
    }
}

/**
 * Whether an app has any process at all (its main one, or one of its own such as hakuX's ":xemu"), read as root from
 * the process list. "yes" or "no" only; anything else is unknown, never "no", including a list that does not even show
 * Android's own system_server (cut short, or not the whole of it).
 */
object Proc {
    private val PACKAGE = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")

    val script = """
        l=${'$'}(ps -A -o NAME=) || { echo unknown; exit 0; }
        case "
        ${'$'}l
        " in *"
        system_server
        "*) ;; *) echo unknown; exit 0 ;; esac
        case "
        ${'$'}l
        " in *"
        ${'$'}1
        "*|*"
        ${'$'}1:"*) echo yes ;; *) echo no ;; esac
    """.trimIndent() + "\n"

    fun parse(out: String?): Boolean? = when (out?.trim()?.lines()?.lastOrNull()?.trim()) { "yes" -> true; "no" -> false; else -> null }

    fun running(ctx: Context, pkg: String): Boolean? {
        if (!PACKAGE.matches(pkg)) return null
        val file = java.io.File(ctx.filesDir, "running.sh")
        if (!Scripts.write(file, script)) return null
        return parse(Bridge.read("timeout 5 sh ${file.absolutePath} $pkg"))
    }

    /** Gone for certain: "no" twice, a moment apart. */
    fun gone(ctx: Context, pkg: String): Boolean = running(ctx, pkg) == false && run { Thread.sleep(300); running(ctx, pkg) == false }
}

/**
 * Where you left off in a GameCube or Wii game, as RetroArch games keep it. Used only with a Dolphin build signed with
 * the same key as this launcher, changed to save a state to its own temp.sav as it is left with Home and to load
 * temp.sav as a game starts. The launcher keeps one state per game in its own folder: moved out of Dolphin's after the game
 * has ended, and put back only just before that same game starts; otherwise temp.sav is cleared. All through the
 * maker's root service, since both folders are private. Dolphin refuses a state made by a different game.
 */
object DolphinResume {
    const val TEMP = "/data/user/0/org.dolphinemu.dolphinemu/files/temp.sav"
    private val PATH = Regex("/data/[A-Za-z0-9_./-]+")

    /** A game's state file name: the game's key, hashed, so any title is a safe file name. */
    fun name(key: String): String =
        java.security.MessageDigest.getInstance("SHA-1").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }.take(24) + ".sav"

    fun dir(ctx: Context): java.io.File = java.io.File(ctx.filesDir, "places/dolphin")
    fun file(ctx: Context, key: String): java.io.File = java.io.File(dir(ctx), name(key))

    /** Room kept free on the device beyond the file itself, in KB, before the launcher copies anything this large. */
    const val SPARE_KB = 524288

    /**
     * Prints one word: ok (moved), none (Dolphin made no state), busy (Dolphin still has the file open: it is being
     * written), full (not enough room to copy it), link, or failed. Only ok removes Dolphin's file.
     */
    fun collectScript(to: String, uid: Int): String = """
        [ -f $TEMP ] || { echo none; exit 0; }
        [ -L $TEMP ] && { echo link; exit 0; }
        for p in ${'$'}(pidof ${DolphinPack.PACKAGE}); do ls -l /proc/${'$'}p/fd 2>/dev/null | grep -q 'files/temp.sav' && { echo busy; exit 0; }; done
        need=${'$'}(( ${'$'}(stat -c %s $TEMP) / 1024 + $SPARE_KB ))
        free=${'$'}(df -k ${to.substringBeforeLast('/')} | awk 'NR==2 {print ${'$'}4}')
        [ -n "${'$'}free" ] && [ "${'$'}free" -gt "${'$'}need" ] || { echo full; exit 0; }
        rm -f $to.part
        cp $TEMP $to.part && cmp -s $TEMP $to.part || { rm -f $to.part; echo failed; exit 0; }
        chown $uid:$uid $to.part
        chmod 600 $to.part
        mv $to.part $to || { rm -f $to.part; echo failed; exit 0; }
        rm $TEMP
        echo ok
    """.trimIndent() + "\n"

    fun placeScript(from: String, uid: Int): String = """
        [ -L $TEMP ] && { echo link; exit 0; }
        rm -f $TEMP.part
        cp $from $TEMP.part || { rm -f $TEMP.part; echo failed; exit 0; }
        chown $uid:$uid $TEMP.part
        chmod 600 $TEMP.part
        mv $TEMP.part $TEMP || { rm -f $TEMP.part; echo failed; exit 0; }
        echo ok
    """.trimIndent() + "\n"

    val clearScript = "rm -f $TEMP\necho ok\n"

    /** The last word the script printed, "" when it could not be run. One at a time: collect, place and clear never overlap. */
    @Synchronized private fun run(ctx: Context, name: String, script: String): String = runCatching {
        val f = java.io.File(ctx.filesDir, name)
        if (!Scripts.write(f, script)) return ""
        Bridge.read("timeout 30 sh ${f.absolutePath}")?.trim()?.lines()?.lastOrNull()?.trim().orEmpty()
    }.getOrDefault("")

    /**
     * After a game has ended: the state Dolphin saved as it was left, into the launcher's keeping. Returns the script's
     * word ("ok", "none", "busy", "full", "link", "failed"; "" when it could not be asked).
     */
    fun collect(ctx: Context, key: String): String {
        val to = file(ctx, key).also { it.parentFile?.mkdirs() }
        if (!PATH.matches(to.absolutePath)) return ""
        val said = run(ctx, "dolphin-collect.sh", collectScript(to.absolutePath, ctx.applicationInfo.uid))
        if (said == "ok") trim(ctx, key)
        return said
    }

    /** Places kept in all, in bytes: past this, the games left longest ago lose theirs (a Wii state is about 50 MB). */
    const val KEPT_BYTES = 3L * 1024 * 1024 * 1024

    /** Two set-aside states at most, and places within [KEPT_BYTES], newest first; [key]'s own is never the one removed. */
    fun trim(ctx: Context, key: String) = runCatching {
        val all = dir(ctx).listFiles()?.filter { it.isFile } ?: return@runCatching
        all.filter { it.name.endsWith(".old") }.sortedByDescending { it.lastModified() }.drop(2).forEach { it.delete() }
        var total = 0L
        for (f in all.filter { it.name.endsWith(".sav") }.sortedByDescending { it.lastModified() }) {
            total += f.length()
            if (total > KEPT_BYTES && f.name != name(key)) f.delete()
        }
    }

    /** Just before [key] starts: its state where Dolphin loads it. */
    fun place(ctx: Context, key: String): Boolean {
        val from = file(ctx, key)
        if (!from.isFile || !PATH.matches(from.absolutePath)) return false
        val uid = runCatching { ctx.packageManager.getApplicationInfo(DolphinPack.PACKAGE, 0).uid }.getOrNull() ?: return false
        return run(ctx, "dolphin-place.sh", placeScript(from.absolutePath, uid)) == "ok"
    }

    /** Before any other start: nothing waiting in Dolphin's folder. */
    fun clear(ctx: Context): Boolean = run(ctx, "dolphin-clear.sh", clearScript) == "ok"

    /** The game was closed from Dolphin's own menu: next time it starts fresh. The state is kept aside, one deep. */
    fun setAside(ctx: Context, key: String) {
        val f = file(ctx, key)
        if (f.isFile) runCatching { java.io.File(f.path + ".old").delete(); f.renameTo(java.io.File(f.path + ".old")) }
        trim(ctx, key)
    }
}

/**
 * Saves kept where only their own emulator can read them, backed up like any other: ARMSX2's memory cards and hakuX's hard disk image, in their app folders. Copied as root into a folder of
 * the launcher's own, file by file and only when a file has changed (its time and size), and only while that emulator
 * is not running, so nothing half-written is taken. From there the card's save mirror keeps them with their earlier
 * versions like any other save. Never written back into the emulators: bringing one back is done by hand.
 */
object RootMirror {
    private val PATH = Regex("/data/[A-Za-z0-9_./-]+")

    /** One emulator's saves: [from] its folder, [files] a shell pattern there, copied to [into] (a folder under the launcher's own). */
    class Source(val pkg: String, val home: String, val from: String, val files: String, val top: String)

    val ps2 = Source("com.armsx2", "ARMSX2", "/data/media/0/Android/data/com.armsx2/files/memcards", "*.ps2", "memcards")
    val xbox = Source("com.rfandango.haku_x", "hakuX", "/data/media/0/Android/data/com.rfandango.haku_x/files/x1box", "hdd.img", "x1box")

    fun dir(ctx: Context, s: Source): java.io.File = java.io.File(ctx.filesDir, "mirror/${s.home}")

    fun script(s: Source, into: String, uid: Int): String = """
        s=${s.from}
        d=$into
        [ -d ${'$'}s ] || { echo absent; exit 0; }
        [ -L ${'$'}s ] && { echo link; exit 0; }
        mkdir -p ${'$'}d
        for f in ${'$'}s/${s.files}; do
          [ -f "${'$'}f" ] || continue
          [ -L "${'$'}f" ] && continue
          n=${'$'}{f##*/}
          a=${'$'}(stat -c %Y:%s "${'$'}f")
          b=${'$'}(stat -c %Y:%s "${'$'}d/${'$'}n" 2>/dev/null)
          [ "${'$'}a" = "${'$'}b" ] && continue
          need=${'$'}(( ${'$'}(stat -c %s "${'$'}f") / 1024 + ${DolphinResume.SPARE_KB} ))
          free=${'$'}(df -k ${'$'}d | awk 'NR==2 {print ${'$'}4}')
          [ -n "${'$'}free" ] && [ "${'$'}free" -gt "${'$'}need" ] || { echo full; exit 0; }
          rm -f "${'$'}d/${'$'}n.part"
          if cp -p "${'$'}f" "${'$'}d/${'$'}n.part" && [ "${'$'}(stat -c %Y:%s "${'$'}f")" = "${'$'}a" ] && ! ps -A -o NAME= | grep -q -E '^${s.pkg.replace(".", "\\.")}(:|${'$'})'; then
            mv "${'$'}d/${'$'}n.part" "${'$'}d/${'$'}n"
          else
            rm -f "${'$'}d/${'$'}n.part"; echo busy; exit 0
          fi
        done
        chown -R $uid:$uid ${into.substringBeforeLast('/')}
        chown $uid:$uid ${into.substringBeforeLast('/').substringBeforeLast('/')}
        chmod 700 ${'$'}d
        echo ok
    """.trimIndent() + "\n"

    /** Brings the launcher's copy up to date; false when it could not be (not running, no folder, no root). */
    @Synchronized fun sync(ctx: Context, s: Source): Boolean = runCatching {
        val into = dir(ctx, s).resolve(s.top)
        if (!PATH.matches(into.absolutePath)) return false
        into.mkdirs()                                      // made by the launcher, so its own; the script hands back anything root made
        val f = java.io.File(ctx.filesDir, "mirror-${s.home}.sh")
        if (!Scripts.write(f, script(s, into.absolutePath, ctx.applicationInfo.uid))) return false
        Bridge.read("timeout 300 sh ${f.absolutePath}")?.lines()?.any { it.trim() == "ok" } == true
    }.getOrDefault(false)
}

/**
 * PlayStation 2 picks up where it was left , with ARMSX2's own autosave: a state saved when a game is
 * closed from its menu, every five minutes while playing, and loaded when the game next starts. Its switches are
 * Android preferences (autoSaveOnExit, autoLoadOnBoot, autoSaveIntervalMin in shared_prefs/ARMSX2.xml, read from its
 * code), set here as root while ARMSX2 is not running, and kept to the launcher's "Keep your place" setting.
 */
object Ps2Resume {
    const val INTERVAL = 5

    fun script(pkg: String, uid: Int, on: Boolean): String {
        val x = "/data/user/0/$pkg/shared_prefs/ARMSX2.xml"
        fun bool(name: String) = """
            if grep -q 'name="$name"' $x; then sed -i 's|<boolean name="$name" value="[a-z]*" />|<boolean name="$name" value="$on" />|' $x
            else sed -i 's|</map>|    <boolean name="$name" value="$on" />\n</map>|' $x; fi
        """.trimIndent()
        val minutes = if (on) INTERVAL else 0
        // Android reads a preferences file's ".bak", when there is one, in place of the file itself: made first, it means
        // an edit cut short leaves ARMSX2 with its settings as they were. One already there is Android's own: left alone.
        return """
            [ -f $x ] || { echo absent; exit 0; }
            [ -L $x ] && { echo link; exit 0; }
            right() { grep -q '<boolean name="autoSaveOnExit" value="$on" />' $x && grep -q '<boolean name="autoLoadOnBoot" value="$on" />' $x && grep -q '<int name="autoSaveIntervalMin" value="$minutes" />' $x; }
            right && { echo ok; exit 0; }
            [ -e $x.bak ] && { echo pending; exit 0; }
            cp -p $x $x.bak || exit 0
            ${bool("autoSaveOnExit")}
            ${bool("autoLoadOnBoot")}
            if grep -q 'name="autoSaveIntervalMin"' $x; then sed -i 's|<int name="autoSaveIntervalMin" value="[0-9]*" />|<int name="autoSaveIntervalMin" value="$minutes" />|' $x
            else sed -i 's|</map>|    <int name="autoSaveIntervalMin" value="$minutes" />\n</map>|' $x; fi
            chown $uid:$uid $x
            chmod 660 $x
            restorecon $x 2>/dev/null
            sync
            right || { mv $x.bak $x; echo failed; exit 0; }
            rm $x.bak
            echo ok
        """.trimIndent() + "\n"
    }

    @Synchronized fun apply(ctx: Context, pkg: String, on: Boolean): Boolean = runCatching {
        if (pkg != "com.armsx2") return false               // the preferences file is named for this build
        val uid = ctx.packageManager.getApplicationInfo(pkg, 0).uid
        val f = java.io.File(ctx.filesDir, "ps2-resume.sh")
        if (!Scripts.write(f, script(pkg, uid, on))) return false
        Bridge.read("timeout 10 sh ${f.absolutePath}")?.lines()?.any { it.trim() == "ok" } == true
    }.getOrDefault(false)
}

/**
 * PlayStation 2's television look ([Tv480]) kept in ARMSX2's own shaders folder, where its "RetroArch Shaders" setting
 * reads the preset (set up by hand, pointing at shaders/timeline/timeline-crt.slangp). Only root can
 * write there; each file is copied only when it differs, and given to ARMSX2 (its user, the shared-storage group) so
 * it is never left read-only to the app the way an adb push leaves it. Nothing else in ARMSX2's folder is touched.
 */
object Ps2Look {
    private val PACKAGE = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")

    private val PATH = Regex("/data/[A-Za-z0-9_./-]+")

    fun script(pkg: String, uid: Int, from: String): String {
        val base = "/data/media/0/Android/data/$pkg/files"
        return """
            [ -d $base ] || { echo absent; exit 0; }
            d=$base/shaders/timeline
            for x in $base/shaders ${'$'}d ${'$'}d/${Tv480.SLANG} ${'$'}d/${Tv480.PRESET}; do [ -L ${'$'}x ] && { echo link; exit 0; }; done
            mkdir -p ${'$'}d
            for f in ${Tv480.SLANG} ${Tv480.PRESET}; do cmp -s $from/${'$'}f ${'$'}d/${'$'}f || cp $from/${'$'}f ${'$'}d/${'$'}f; done
            chown -h $uid:1078 $base/shaders ${'$'}d ${'$'}d/${Tv480.SLANG} ${'$'}d/${Tv480.PRESET}
            chmod 2770 $base/shaders ${'$'}d
            chmod 660 ${'$'}d/${Tv480.SLANG} ${'$'}d/${Tv480.PRESET}
            cmp -s $from/${Tv480.PRESET} ${'$'}d/${Tv480.PRESET} && cmp -s $from/${Tv480.SLANG} ${'$'}d/${Tv480.SLANG} && echo ok
        """.trimIndent() + "\n"
    }

    /** True once both files in ARMSX2's folder are the launcher's. */
    fun sync(ctx: Context, pkg: String, preset: String): Boolean = runCatching {
        if (!PACKAGE.matches(pkg)) return false
        val uid = ctx.packageManager.getApplicationInfo(pkg, 0).uid
        val from = java.io.File(ctx.filesDir, "armsx2-shaders").apply { mkdirs() }
        if (!PATH.matches(from.absolutePath)) return false
        java.io.File(from, Tv480.SLANG).writeText(Tv480.slang)
        java.io.File(from, Tv480.PRESET).writeText(preset)
        val file = java.io.File(ctx.filesDir, "armsx2-shaders.sh")
        if (!Scripts.write(file, script(pkg, uid, from.absolutePath))) return false
        Bridge.read("timeout 10 sh ${file.absolutePath}")?.lines()?.any { it.trim() == "ok" } == true
    }.getOrDefault(false)
}

/**
 * A keyboard that exists for about four seconds: Android's own uinput tool, run through the maker's service, makes it,
 * presses RetroArch's disc keys on it (F10, the tray; F11, the next disc) a moment apart, and takes it away again.
 * RetroArch takes hotkeys only from a real input device, and the Nova has no keyboard, so nothing else can press them.
 */
object DiscKeys {
    private const val F10 = 68
    private const val F11 = 87

    /** The tool's script: one JSON object a line, in the form Android 13's uinput reads. */
    fun script(): String {
        fun tap(key: Int) = listOf(
            "{\"id\": 1, \"command\": \"inject\", \"events\": [1, $key, 1, 0, 0, 0]}",
            "{\"id\": 1, \"command\": \"delay\", \"duration\": 250}",
            "{\"id\": 1, \"command\": \"inject\", \"events\": [1, $key, 0, 0, 0, 0]}",
            "{\"id\": 1, \"command\": \"delay\", \"duration\": 900}")
        return (listOf(
            "{\"id\": 1, \"command\": \"register\", \"name\": \"Timeline Disc Keys\", \"vid\": 6353, \"pid\": 20199, \"bus\": \"usb\", " +
                "\"configuration\": [{\"type\": 100, \"data\": [1]}, {\"type\": 101, \"data\": [$F10, $F11]}]}",
            "{\"id\": 1, \"command\": \"delay\", \"duration\": 800}",
       ) + tap(F10) + tap(F11) + tap(F10)).joinToString("\n", postfix = "\n")
    }

    /** Tray open, next disc, tray closed. Blocks for about four seconds; not on the screen's thread. */
    fun nextDisc(ctx: Context): Boolean {
        val file = java.io.File(ctx.filesDir, "disc-keys.json")
        if (!Scripts.write(file, script())) return false
        val path = file.absolutePath
        if (!Regex("[A-Za-z0-9/._-]+").matches(path)) return false
        // At most 12 seconds (the script takes about 4): the maker's service waits for it, and every other root write waits behind.
        return Bridge.run("timeout 12 uinput $path")
    }
}

/** The battery as the charger's driver reports it, for Settings, Device, Battery. Reading only. */
object BatteryInfo {
    data class Info(val fullMah: Int?, val designMah: Int?, val cycles: Int?, val tempC: Float?, val volts: Float?, val level: Int?, val plugged: Boolean) {
        /** Capacity now against the rating, in percent (a new battery can read a little over 100). */
        val health: Int? get() = if (fullMah != null && designMah != null && designMah > 0) Math.round(fullMah * 100f / designMah) else null
    }

    private fun num(name: String): Long? =
        runCatching { java.io.File("/sys/class/power_supply/battery/$name").readText().trim().toLong() }.getOrNull()

    fun read(ctx: Context): Info {
        val b = runCatching { ctx.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED)) }.getOrNull()
        val level = b?.let { val l = it.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1); val sc = it.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100); if (l >= 0 && sc > 0) l * 100 / sc else null }
        val temp = b?.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)?.takeIf { it != Int.MIN_VALUE }?.let { it / 10f }
        return Info(num("charge_full")?.let { (it / 1000).toInt() }, num("charge_full_design")?.let { (it / 1000).toInt() },
            num("cycle_count")?.toInt(), temp, num("voltage_now")?.let { it / 1_000_000f }, level,
            (b?.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0)
    }
}

/** The alarm from [ChargeCare]: one look, off the main thread, then done. */
class ChargeCheck : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent?) {
        val done = goAsync()
        ChargeCare.applyLater(ctx.applicationContext) { done.finish() }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) SoundService.sync(ctx)
    }
}

/**
 * Hands a downloaded emulator's installer to Android. Android always asks the player before installing
 * anything; this only saves the trip through the Files app and brings them straight back afterwards.
 */
object Installs {
    /** The screen on show, so Android's question appears over it instead of in a task of its own. */
    var host: java.lang.ref.WeakReference<android.app.Activity>? = null
    var onResult: ((ok: Boolean, message: String) -> Unit)? = null

    /** Copies the installer into an install session and commits it. False if it could not be read or handed over. */
    fun start(ctx: Context, apk: java.io.File): Boolean {
        val installer = ctx.packageManager.packageInstaller
        var id = -1
        return try {
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            params.setSize(apk.length())
            id = installer.createSession(params)
            last = id
            installer.openSession(id).use { session ->
                session.openWrite("base.apk", 0, apk.length()).use { out ->
                    apk.inputStream().use { it.copyTo(out, 1 shl 20) }
                    session.fsync(out)
                }
                val answer = PendingIntent.getBroadcast(ctx, id, Intent(ctx, InstallReceiver::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
                session.commit(answer.intentSender)
            }
            true
        } catch (e: Exception) {
            // Nothing half-copied is left behind taking up room.
            if (id >= 0) runCatching { installer.abandonSession(id) }
            last = -1
            false
        }
    }

    @Volatile private var last = -1

    /** Puts down an installer Android never answered about, so the next try starts clean. */
    fun abandon(ctx: Context) {
        if (last >= 0) runCatching { ctx.packageManager.packageInstaller.abandonSession(last) }
        last = -1
    }
}

class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val ask = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
                val shown = ask != null && runCatching {
                    Installs.host?.get()?.startActivity(ask) ?: ctx.startActivity(ask.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }.isSuccess
                if (!shown) Installs.onResult?.invoke(false, "Android could not show its question")
            }
            PackageInstaller.STATUS_SUCCESS -> Installs.onResult?.invoke(true, "")
            PackageInstaller.STATUS_FAILURE_ABORTED -> Installs.onResult?.invoke(false, "")
            else -> Installs.onResult?.invoke(false, when (status) {
                PackageInstaller.STATUS_FAILURE_STORAGE -> "not enough room on the device"
                PackageInstaller.STATUS_FAILURE_CONFLICT -> "a different build of it is already installed"
                PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "it is not made for this device"
                else -> intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Android refused it"
            })
        }
    }
}

/** Four seconds of soft noise across the whole range, for judging the tone shape by ear. */
object TestSound {
    @Volatile private var busy = false

    fun play() {
        if (busy) return
        busy = true
        Thread {
            runCatching {
                val rate = 44100
                val n = rate * 4
                val pcm = ShortArray(n)
                val rnd = java.util.Random(7)
                var b0 = 0.0; var b1 = 0.0; var b2 = 0.0
                for (i in 0 until n) {                           // white noise tilted toward pink, so no band dominates
                    val white = rnd.nextDouble() * 2 - 1
                    b0 = 0.99765 * b0 + white * 0.0990460
                    b1 = 0.96300 * b1 + white * 0.2965164
                    b2 = 0.57000 * b2 + white * 1.0526913
                    val fade = minOf(1.0, i / 4000.0, (n - i) / 4000.0)
                    pcm[i] = ((b0 + b1 + b2 + white * 0.1848) * 0.11 * fade * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort()
                }
                val track = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(n * 2).build()
                track.write(pcm, 0, n)
                track.play()
                Thread.sleep(4200)
                track.release()
            }
            busy = false
        }.start()
    }
}
