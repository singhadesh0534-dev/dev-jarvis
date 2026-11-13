package com.adesh.devjarvis.tools.impl

import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore
import android.provider.Settings
import android.view.KeyEvent
import com.adesh.devjarvis.accessibility.JarvisAccessibilityService
import com.adesh.devjarvis.core.DeviceStatus
import com.adesh.devjarvis.reminders.ReminderScheduler
import com.adesh.devjarvis.tools.ActivityLauncher
import com.adesh.devjarvis.tools.AppRepository
import com.adesh.devjarvis.tools.JarvisTool
import com.adesh.devjarvis.tools.SimpleTool
import com.adesh.devjarvis.tools.ToolArgs
import com.adesh.devjarvis.tools.ToolContext
import com.adesh.devjarvis.tools.ToolResult
import com.adesh.devjarvis.tools.params
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/** Apps, alarms, timers, reminders, flashlight, volume, brightness, settings, media, device status. */
@Singleton
class PhoneTools @Inject constructor(
    @ApplicationContext private val context: Context,
    private val launcher: ActivityLauncher,
    private val apps: AppRepository,
    private val device: DeviceStatus,
    private val reminders: ReminderScheduler,
) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val camera = context.getSystemService(CameraManager::class.java)
    @Volatile private var torchOn = false
    private val spokenTime = DateTimeFormatter.ofPattern("EEE d MMM, h:mm a", Locale.ENGLISH)

    init {
        runCatching {
            camera?.registerTorchCallback(object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(cameraId: String, enabled: Boolean) { torchOn = enabled }
            }, null)
        }
    }

    private val openApp = object : SimpleTool(
        "open_app", "Open an installed app by name (e.g. YouTube, insta, WhatsApp, camera).",
        params { string("name", "App name as Sir said it", required = true) },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            val q = args.requireString("name")
            val app = apps.find(q) ?: return ToolResult.fail("no installed app matching '$q'")
            val intent = apps.launchIntent(app) ?: return ToolResult.fail("${app.label} can't be opened")
            return launcher.result(launcher.launch(intent, "Open ${app.label}"), "${app.label} opened", "Open ${app.label}")
        }
    }

    private val uninstallApp = object : SimpleTool(
        "uninstall_app", "Uninstall an installed app. The app asks Sir to confirm, then Android shows its own Uninstall dialog.",
        params { string("name", "App name as Sir said it", required = true) },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            val q = args.requireString("name")
            val app = apps.find(q) ?: return ToolResult.fail("no installed app matching '$q'")
            if (!ctx.confirm("Sir, ${app.label} uninstall kar doon? Iska data bhi chala jayega.")) return ToolResult.ok("kept ${app.label} — Sir said no")
            val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}"))
            return launcher.result(
                launcher.launch(intent, "Uninstall ${app.label}"),
                "Android's uninstall dialog for ${app.label} is open — Sir (or tap 'OK' via the screen tools) confirms it there",
                "Uninstall ${app.label}",
            )
        }
    }

    private val installApp = object : SimpleTool(
        "install_app", "Open the Play Store on an app so it can be installed (then tap Install there, e.g. with the tap tool).",
        params { string("name", "App to install", required = true) },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            val q = args.requireString("name")
            var outcome = launcher.launch(Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=${Uri.encode(q)}&c=apps")), "Install $q")
            if (outcome == ActivityLauncher.Outcome.NOT_FOUND) {
                outcome = launcher.launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=${Uri.encode(q)}&c=apps")), "Install $q")
            }
            return launcher.result(outcome, "Play Store opened on '$q' — pick the app and tap Install (free apps only; never buy anything)", "Install $q")
        }
    }

    private val closeApp = object : SimpleTool(
        "close_current_app", "Leave the current app and go to the home screen (Android doesn't allow force-closing other apps).",
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult =
            if (JarvisAccessibilityService.global(AccessibilityService.GLOBAL_ACTION_HOME)) ToolResult.ok("went to home screen")
            else ToolResult.fail("needs JARVIS Accessibility turned on in Setup")
    }

    private val setAlarm = object : SimpleTool(
        "set_alarm", "Set an alarm in the phone's Clock app. Use 24-hour time; decide AM/PM from context (\"subah 6\" = 6, \"shaam 6\" = 18).",
        params {
            integer("hour", "Hour 0-23", required = true)
            integer("minute", "Minute 0-59")
            string("label", "Optional label")
        },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            val hour = args.int("hour")?.takeIf { it in 0..23 } ?: return ToolResult.fail("hour must be 0-23")
            val minute = (args.int("minute") ?: 0).coerceIn(0, 59)
            val intent = Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, hour)
                .putExtra(AlarmClock.EXTRA_MINUTES, minute)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            args.string("label")?.let { intent.putExtra(AlarmClock.EXTRA_MESSAGE, it) }
            val t = String.format(Locale.ENGLISH, "%02d:%02d", hour, minute)
            return launcher.result(launcher.launch(intent, "Set alarm $t"), "alarm set for $t", "Set alarm $t")
        }
    }

    private val setTimer = object : SimpleTool(
        "set_timer", "Start a countdown timer in the Clock app.",
        params {
            integer("seconds", "Total duration in seconds", required = true)
            string("label", "Optional label")
        },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            val secs = args.durationSeconds("seconds")?.takeIf { it in 1..86_400 } ?: return ToolResult.fail("duration must be 1 s – 24 h")
            val intent = Intent(AlarmClock.ACTION_SET_TIMER)
                .putExtra(AlarmClock.EXTRA_LENGTH, secs)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            args.string("label")?.let { intent.putExtra(AlarmClock.EXTRA_MESSAGE, it) }
            return launcher.result(launcher.launch(intent, "Start timer"), "timer started for ${secs}s", "Start timer")
        }
    }

    private val createReminder = object : SimpleTool(
        "create_reminder", "JARVIS reminds Sir (spoken + notification) at a time. Give either datetime or minutes_from_now.",
        params {
            string("text", "What to remind about", required = true)
            string("datetime", "Local date-time ISO, e.g. 2026-11-01T18:30, or just 18:30")
            integer("minutes_from_now", "Alternative to datetime")
        },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            val text = args.requireString("text")
            val now = LocalDateTime.now()
            val at = args.int("minutes_from_now")?.takeIf { it > 0 }?.let { now.plusMinutes(it.toLong()) }
                ?: args.dateTime("datetime", now)
                ?: return ToolResult.fail("when? give datetime or minutes_from_now")
            if (!at.isAfter(now)) return ToolResult.fail("that time has already passed")
            reminders.schedule(text, at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
            return ToolResult.ok("reminder set for ${at.format(spokenTime)}: $text")
        }
    }

    private val addCalendarEvent = object : SimpleTool(
        "add_calendar_event", "Open the calendar with a new event pre-filled; Sir taps save.",
        params {
            string("title", "Event title", required = true)
            string("start", "Start local date-time ISO, e.g. 2026-11-02T10:00", required = true)
            integer("duration_minutes", "Length, default 60")
            string("location", "Optional place")
        },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            val title = args.requireString("title")
            val start = args.dateTime("start", LocalDateTime.now()) ?: return ToolResult.fail("bad start time")
            val startMs = start.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val intent = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.Events.TITLE, title)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMs)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, startMs + (args.int("duration_minutes") ?: 60) * 60_000L)
            args.string("location")?.let { intent.putExtra(CalendarContract.Events.EVENT_LOCATION, it) }
            return launcher.result(launcher.launch(intent, "Add '$title' to calendar"), "calendar opened with '$title' — Sir taps save", "Add '$title' to calendar")
        }
    }

    private val flashlight = object : SimpleTool(
        "toggle_flashlight", "Turn the torch on/off. Omit 'on' to toggle.",
        params { boolean("on", "true = on, false = off") },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            val cm = camera ?: return ToolResult.fail("no camera")
            val id = cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
                ?: return ToolResult.fail("this phone has no flashlight")
            val target = args.bool("on") ?: !torchOn
            cm.setTorchMode(id, target)
            torchOn = target
            return ToolResult.ok(if (target) "flashlight on" else "flashlight off")
        }
    }

    private val setVolume = object : SimpleTool(
        "set_volume", "Change volume. action: set (with level 0-100), up, down, mute, unmute.",
        params {
            string("action", "set | up | down | mute | unmute", required = true, enum = listOf("set", "up", "down", "mute", "unmute"))
            integer("level", "0-100, for action=set")
            string("stream", "media (default) | ring | alarm | notification", enum = listOf("media", "ring", "alarm", "notification"))
        },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            val am = audio ?: return ToolResult.fail("no audio service")
            val stream = when (args.enum("stream", listOf("media", "ring", "alarm", "notification"))) {
                "ring" -> AudioManager.STREAM_RING
                "alarm" -> AudioManager.STREAM_ALARM
                "notification" -> AudioManager.STREAM_NOTIFICATION
                else -> AudioManager.STREAM_MUSIC
            }
            val max = am.getStreamMaxVolume(stream)
            when (args.enum("action", listOf("set", "up", "down", "mute", "unmute"))) {
                "set" -> {
                    val level = args.int("level")?.coerceIn(0, 100) ?: return ToolResult.fail("level 0-100 needed")
                    am.setStreamVolume(stream, (level * max / 100.0).roundToInt(), AudioManager.FLAG_SHOW_UI)
                }
                "up" -> am.adjustStreamVolume(stream, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                "down" -> am.adjustStreamVolume(stream, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                "mute" -> am.adjustStreamVolume(stream, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
                "unmute" -> am.adjustStreamVolume(stream, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI)
                else -> return ToolResult.fail("unknown action")
            }
            val now = am.getStreamVolume(stream) * 100 / max.coerceAtLeast(1)
            return ToolResult.ok("volume now $now%")
        }
    }

    private val setBrightness = object : SimpleTool(
        "set_brightness", "Set screen brightness 0-100, or 'auto'.",
        params {
            integer("level", "0-100")
            boolean("auto", "true = adaptive/auto brightness")
        },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            if (!Settings.System.canWrite(context)) {
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))
                launcher.launch(intent, "Allow JARVIS to change brightness")
                return ToolResult.fail("needs 'Modify system settings' permission — opened that screen for Sir")
            }
            val cr = context.contentResolver
            if (args.bool("auto") == true) {
                Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC)
                return ToolResult.ok("auto brightness on")
            }
            val level = args.int("level")?.coerceIn(0, 100) ?: return ToolResult.fail("level 0-100 needed")
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, (level * 255 / 100).coerceAtLeast(1))
            return ToolResult.ok("brightness $level%")
        }
    }

    private val settingsTargets = listOf(
        "wifi", "internet", "mobile_data", "bluetooth", "hotspot", "location", "display", "battery", "sound",
        "nfc", "airplane", "dnd", "apps", "storage", "general",
    )

    private val openSettings = object : SimpleTool(
        "open_settings",
        "Open a settings panel/screen. Android doesn't let apps toggle Wi-Fi, mobile data or airplane mode directly, so open the panel and tell Sir.",
        params { string("target", settingsTargets.joinToString(" | "), required = true, enum = settingsTargets) },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            val target = args.enum("target", settingsTargets) ?: return ToolResult.fail("unknown settings target")
            val panel = Build.VERSION.SDK_INT >= 29
            val action = when (target) {
                "wifi" -> if (panel) Settings.Panel.ACTION_WIFI else Settings.ACTION_WIFI_SETTINGS
                "internet", "mobile_data" -> if (panel) Settings.Panel.ACTION_INTERNET_CONNECTIVITY else Settings.ACTION_WIRELESS_SETTINGS
                "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
                "hotspot" -> "android.settings.TETHER_SETTINGS"
                "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
                "display" -> Settings.ACTION_DISPLAY_SETTINGS
                "battery" -> Intent.ACTION_POWER_USAGE_SUMMARY
                "sound" -> if (panel) Settings.Panel.ACTION_VOLUME else Settings.ACTION_SOUND_SETTINGS
                "nfc" -> if (panel) Settings.Panel.ACTION_NFC else Settings.ACTION_NFC_SETTINGS
                "airplane" -> Settings.ACTION_AIRPLANE_MODE_SETTINGS
                "dnd" -> "android.settings.ZEN_MODE_SETTINGS"
                "apps" -> Settings.ACTION_APPLICATION_SETTINGS
                "storage" -> Settings.ACTION_INTERNAL_STORAGE_SETTINGS
                else -> Settings.ACTION_SETTINGS
            }
            var outcome = launcher.launch(Intent(action), "Open $target settings")
            if (outcome == ActivityLauncher.Outcome.NOT_FOUND) {
                outcome = launcher.launch(Intent(Settings.ACTION_WIRELESS_SETTINGS), "Open $target settings")
            }
            return launcher.result(outcome, "$target settings opened", "Open $target settings")
        }
    }

    private val screenshot = object : SimpleTool("take_screenshot", "Take a screenshot of the current screen.") {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult =
            if (JarvisAccessibilityService.global(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)) ToolResult.ok("screenshot taken")
            else ToolResult.fail("needs JARVIS Accessibility turned on in Setup")
    }

    private val lockScreen = object : SimpleTool("lock_screen", "Lock the phone screen now.") {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult =
            if (JarvisAccessibilityService.global(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)) ToolResult.ok("screen locked", endsConversation = true)
            else ToolResult.fail("needs JARVIS Accessibility turned on in Setup")
    }

    private val deviceStatus = object : SimpleTool("device_status", "Battery, charging, storage, RAM, network and uptime.") {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            val d = device.snapshot()
            val stat = StatFs(Environment.getDataDirectory().path)
            val freeGb = stat.availableBytes / 1e9
            val totalGb = stat.totalBytes / 1e9
            val mem = ActivityManager.MemoryInfo().also { context.getSystemService(ActivityManager::class.java)?.getMemoryInfo(it) }
            val uptimeH = SystemClock.elapsedRealtime() / 3_600_000
            return ToolResult.ok(
                "battery ${d.batteryPercent}%${if (d.charging) " charging" else ""}; " +
                    "storage ${"%.1f".format(freeGb)} GB free of ${"%.0f".format(totalGb)} GB; " +
                    "RAM ${"%.1f".format(mem.availMem / 1e9)} GB free of ${"%.1f".format(mem.totalMem / 1e9)} GB; " +
                    "network ${d.network.label.lowercase()}; uptime ${uptimeH}h",
            )
        }
    }

    private val musicApps = mapOf(
        "youtube music" to "com.google.android.apps.youtube.music",
        "spotify" to "com.spotify.music",
        "youtube" to "com.google.android.youtube",
        "jiosaavn" to "com.jio.media.jiobeats",
        "gaana" to "com.gaana",
    )

    private val toggleTargets = listOf(
        "bluetooth", "wifi", "mobile_data", "hotspot", "location", "airplane", "nfc",
        "battery_saver", "dark_mode", "do_not_disturb", "auto_rotate", "auto_brightness", "eye_comfort", "data_saver",
        "vibrate_on_ring", "auto_sync",
    )

    /** Settings page to open and the words of the switch on it (XOS names included). */
    private fun toggleRoute(target: String): Pair<List<String>, List<String>> = when (target) {
        "bluetooth" -> listOf(Settings.ACTION_BLUETOOTH_SETTINGS) to listOf("bluetooth")
        "wifi" -> listOf(Settings.ACTION_WIFI_SETTINGS) to listOf("wi-fi", "wifi", "wlan")
        "mobile_data" -> listOf(Settings.ACTION_DATA_ROAMING_SETTINGS, "android.settings.DATA_USAGE_SETTINGS") to listOf("mobile data", "data")
        "hotspot" -> listOf("android.settings.TETHER_SETTINGS") to listOf("hotspot")
        "location" -> listOf(Settings.ACTION_LOCATION_SOURCE_SETTINGS) to listOf("location")
        "airplane" -> listOf(Settings.ACTION_AIRPLANE_MODE_SETTINGS) to listOf("airplane", "aeroplane", "flight")
        "nfc" -> listOf(Settings.ACTION_NFC_SETTINGS) to listOf("nfc")
        "battery_saver" -> listOf(Settings.ACTION_BATTERY_SAVER_SETTINGS, "android.intent.action.POWER_USAGE_SUMMARY") to
            listOf("battery saver", "power saving", "power saver", "saver", "power marathon", "low power", "ultra power")
        "dark_mode" -> listOf("android.settings.DARK_THEME_SETTINGS", Settings.ACTION_DISPLAY_SETTINGS) to listOf("dark theme", "dark mode", "dark")
        "do_not_disturb" -> listOf("android.settings.ZEN_MODE_SETTINGS", Settings.ACTION_SOUND_SETTINGS) to listOf("do not disturb", "dnd", "disturb")
        "auto_rotate" -> listOf(Settings.ACTION_DISPLAY_SETTINGS) to listOf("auto-rotate", "auto rotate", "rotate", "rotation")
        "auto_brightness" -> listOf(Settings.ACTION_DISPLAY_SETTINGS) to listOf("adaptive brightness", "auto brightness", "automatic brightness", "auto-brightness")
        "eye_comfort" -> listOf("android.settings.NIGHT_DISPLAY_SETTINGS", Settings.ACTION_DISPLAY_SETTINGS) to
            listOf("eye comfort", "night light", "eye care", "reading mode", "blue light")
        "data_saver" -> listOf("android.settings.DATA_SAVER_SETTINGS", "android.settings.DATA_USAGE_SETTINGS") to listOf("data saver", "save data")
        "vibrate_on_ring" -> listOf(Settings.ACTION_SOUND_SETTINGS) to listOf("vibrate for calls", "vibrate when ringing", "vibrate")
        else -> listOf(Settings.ACTION_SYNC_SETTINGS) to listOf("auto-sync", "auto sync", "automatically sync", "sync")
    }

    /** Switches Android lets an app set itself, without going through the settings screen. */
    private fun setDirectly(target: String, on: Boolean): Boolean = runCatching {
        when (target) {
            "do_not_disturb" -> {
                val nm = context.getSystemService(android.app.NotificationManager::class.java)
                if (nm == null || !nm.isNotificationPolicyAccessGranted) return false
                nm.setInterruptionFilter(
                    if (on) android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY else android.app.NotificationManager.INTERRUPTION_FILTER_ALL,
                )
                true
            }
            "auto_rotate" -> Settings.System.canWrite(context) &&
                Settings.System.putInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (on) 1 else 0)
            "auto_brightness" -> Settings.System.canWrite(context) &&
                Settings.System.putInt(
                    context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
                    if (on) Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC else Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
                )
            "auto_sync" -> { android.content.ContentResolver.setMasterSyncAutomatically(on); true }
            else -> false
        }
    }.getOrDefault(false)

    private val setToggle = object : SimpleTool(
        "set_toggle",
        "Really switch a phone setting ON or OFF: Bluetooth, Wi-Fi, mobile data, hotspot, location, airplane mode, NFC, battery " +
            "saver, dark mode, do not disturb, auto-rotate, auto brightness, eye comfort / night light, data saver, vibrate on " +
            "ring, auto-sync. JARVIS sets it directly where Android allows, otherwise opens the right settings page and flips " +
            "the switch himself. Use this, not open_settings, when Sir says on / off / chalu / band karo. For any other " +
            "setting: open_settings, then read_screen and tap.",
        params {
            string("target", toggleTargets.joinToString(" | "), required = true, enum = toggleTargets)
            boolean("on", "true = on, false = off", required = true)
        },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            val target = args.enum("target", toggleTargets) ?: return ToolResult.fail("unknown toggle")
            val on = args.bool("on") ?: return ToolResult.fail("on or off?")
            val state = if (on) "ON" else "OFF"
            if (setDirectly(target, on)) return ToolResult.ok("$target switched $state")
            val (actions, hints) = toggleRoute(target)
            val svc = com.adesh.devjarvis.accessibility.JarvisAccessibilityService.get()
            var outcome = ActivityLauncher.Outcome.NOT_FOUND
            for (a in actions) {
                outcome = launcher.launch(Intent(a), "Open $target settings")
                if (outcome != ActivityLauncher.Outcome.NOT_FOUND) break
            }
            if (outcome == ActivityLauncher.Outcome.NOT_FOUND) outcome = launcher.launch(Intent(Settings.ACTION_SETTINGS), "Open settings")
            if (svc == null || outcome != ActivityLauncher.Outcome.LAUNCHED) {
                return launcher.result(
                    outcome,
                    "$target settings opened, but JARVIS could not flip the switch himself: Accessibility is off (JARVIS Setup). Sir must tap it.",
                    "Open $target settings",
                )
            }
            var result: String? = null
            var openedRow = false
            for (attempt in 0 until 6) {
                kotlinx.coroutines.delay(if (attempt == 0) 1_200 else 600)
                result = runCatching { svc.setSwitch(on, hints) }.getOrNull()
                if (result != null) break
                // The switch may live one level deeper (XOS: Battery > Power saving): open that row once, then look again.
                if (!openedRow && attempt >= 1) openedRow = runCatching { svc.openRow(hints) }.getOrDefault(false)
            }
            return when (result) {
                "changed" -> {
                    kotlinx.coroutines.delay(500)
                    svc.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
                    ToolResult.ok("$target switched $state")
                }
                "already" -> {
                    svc.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
                    ToolResult.ok("$target was already $state")
                }
                else -> ToolResult.fail(
                    "$target settings are open but JARVIS could not find that exact switch. Do it yourself now: read_screen, " +
                        "tap the row or switch for $target (scroll if needed), then confirm the new state to Sir.",
                )
            }
        }
    }

    private fun writeDirect(d: com.adesh.devjarvis.tools.SettingsRouter.Direct, on: Boolean?, value: String?): String? {
        if (!Settings.System.canWrite(context)) return null
        val cr = context.contentResolver
        return runCatching {
            when (d) {
                com.adesh.devjarvis.tools.SettingsRouter.Direct.SCREEN_TIMEOUT -> {
                    val ms = com.adesh.devjarvis.tools.SettingsRouter.timeoutMs(value) ?: return null
                    Settings.System.putInt(cr, Settings.System.SCREEN_OFF_TIMEOUT, ms)
                    "screen timeout set to ${if (ms == Int.MAX_VALUE) "never" else "${ms / 1000} seconds"}"
                }
                com.adesh.devjarvis.tools.SettingsRouter.Direct.FONT_SCALE -> {
                    val scale = com.adesh.devjarvis.tools.SettingsRouter.fontScale(value) ?: return null
                    Settings.System.putFloat(cr, Settings.System.FONT_SCALE, scale)
                    "font size set to ${(scale * 100).roundToInt()}%"
                }
                else -> {
                    val flag = on ?: return null
                    val key = when (d) {
                        com.adesh.devjarvis.tools.SettingsRouter.Direct.TOUCH_SOUNDS -> Settings.System.SOUND_EFFECTS_ENABLED
                        com.adesh.devjarvis.tools.SettingsRouter.Direct.HAPTICS -> Settings.System.HAPTIC_FEEDBACK_ENABLED
                        com.adesh.devjarvis.tools.SettingsRouter.Direct.DIAL_TONES -> Settings.System.DTMF_TONE_WHEN_DIALING
                        else -> "lockscreen_sounds_enabled"
                    }
                    Settings.System.putInt(cr, key, if (flag) 1 else 0)
                    "${d.name.lowercase().replace('_', ' ')} ${if (flag) "ON" else "OFF"}"
                }
            }
        }.getOrNull()
    }

    private val phoneSetting = object : SimpleTool(
        "phone_setting",
        "Change or open ANY phone setting by its name — anything in the Settings app (screen timeout, font size, display " +
            "size, refresh rate, ringtone, keyboard, language, date & time, developer options, battery percentage, app " +
            "permissions, notifications, VPN, SIM / 5G, game mode, one-handed mode, gestures…, also names you have never " +
            "seen). JARVIS sets it directly where Android allows, otherwise opens that setting's page or finds it with the " +
            "Settings search, and flips the switch when you give on. The result shows the screen it reached: if the job is " +
            "not finished, carry on there with tap / type_text / scroll by number. For Wi-Fi, Bluetooth, data, hotspot, " +
            "location, airplane, battery saver, dark mode, DND and auto-rotate use set_toggle (faster).",
        params {
            string("setting", "The setting's name in plain words, e.g. \"screen timeout\", \"font size\", \"game mode\"", required = true)
            boolean("on", "true = turn on, false = turn off (omit when it is not an on / off setting)")
            string("value", "A value when there is one: \"2 minute\", \"bada\", \"120hz\"…")
        },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            val name = args.requireString("setting")
            val on = args.bool("on")
            val value = args.string("value")
            val route = com.adesh.devjarvis.tools.SettingsRouter.route(name)
            if (route.forbidden) return ToolResult.fail("factory reset / erasing is never done by JARVIS. Sir must do that himself in Settings.")
            route.direct?.let { d -> writeDirect(d, on, value)?.let { return ToolResult.ok(it) } }

            val svc = JarvisAccessibilityService.get()
            var outcome = ActivityLauncher.Outcome.NOT_FOUND
            for (a in route.pages) {
                outcome = launcher.launch(Intent(a), "Open $name")
                if (outcome != ActivityLauncher.Outcome.NOT_FOUND) break
            }
            val viaSearch = outcome == ActivityLauncher.Outcome.NOT_FOUND
            if (viaSearch) {
                outcome = launcher.launch(Intent("android.settings.APP_SEARCH_SETTINGS"), "Search settings")
                if (outcome == ActivityLauncher.Outcome.NOT_FOUND) outcome = launcher.launch(Intent(Settings.ACTION_SETTINGS), "Open settings")
            }
            if (svc == null || outcome != ActivityLauncher.Outcome.LAUNCHED) {
                return launcher.result(outcome, "Settings opened for '$name'. JARVIS Accessibility is off, so Sir must finish it himself.", "Open $name")
            }
            kotlinx.coroutines.delay(1_200)
            if (viaSearch) {
                // Settings' own search: write the name, open the best result.
                if (!svc.type(null, name.take(40))) {
                    svc.openRow(listOf("search")); kotlinx.coroutines.delay(800); svc.type(null, name.take(40))
                }
                kotlinx.coroutines.delay(1_500)
                svc.openRow(route.words)
                kotlinx.coroutines.delay(1_000)
            } else if (svc.setSwitch(on ?: true, route.words) == null) {
                // The page is open; the setting may be a row on it.
                if (svc.openRow(route.words)) kotlinx.coroutines.delay(1_000)
            }
            if (on != null) {
                when (runCatching { svc.setSwitch(on, route.words) }.getOrNull()) {
                    "changed" -> return ToolResult.ok("$name switched ${if (on) "ON" else "OFF"}")
                    "already" -> return ToolResult.ok("$name was already ${if (on) "ON" else "OFF"}")
                }
            }
            val screen = runCatching { svc.snapshot() }.getOrNull()
            val text = screen?.let { com.adesh.devjarvis.screen.ScreenFormatter.compact(it, 50) } ?: "(screen not readable)"
            return ToolResult.ok(
                "Reached the settings for '$name' but the change is not made yet. Finish it now on this screen with tap / " +
                    "type_text / scroll by number (choose the value Sir asked for), then tell Sir. Screen:\n$text",
            )
        }
    }

    private val searchApps = listOf("youtube", "youtube_music", "google", "play_store", "maps", "spotify", "amazon", "flipkart")

    private val searchInApp = object : SimpleTool(
        "search_in_app",
        "Open an app straight on its search results for a query: YouTube (\"YouTube pe ... search karo / chalao\"), YouTube Music, " +
            "Google, Play Store, Maps, Spotify, Amazon, Flipkart. One step and instant — always use this instead of tapping through the app.",
        params {
            string("app", searchApps.joinToString(" | "), required = true, enum = searchApps)
            string("query", "What to search for, exactly as Sir said it", required = true)
        },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            val app = args.enum("app", searchApps) ?: return ToolResult.fail("unknown app")
            val q = args.requireString("query")
            val e = Uri.encode(q)
            val intents: List<Intent> = when (app) {
                "youtube" -> listOf(
                    Intent(Intent.ACTION_SEARCH).setPackage("com.google.android.youtube").putExtra("query", q),
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$e")),
                )
                "youtube_music" -> listOf(Intent(Intent.ACTION_VIEW, Uri.parse("https://music.youtube.com/search?q=$e")))
                "google" -> listOf(
                    Intent(Intent.ACTION_WEB_SEARCH).putExtra("query", q),
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$e")),
                )
                "play_store" -> listOf(
                    Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=$e")),
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=$e")),
                )
                "maps" -> listOf(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$e")))
                "spotify" -> listOf(
                    Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:$e")),
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com/search/$e")),
                )
                "amazon" -> listOf(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.amazon.in/s?k=$e")))
                else -> listOf(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.flipkart.com/search?q=$e")))
            }
            var outcome = ActivityLauncher.Outcome.NOT_FOUND
            for (i in intents) {
                outcome = launcher.launch(i, "Search $app")
                if (outcome != ActivityLauncher.Outcome.NOT_FOUND) break
            }
            return launcher.result(outcome, "$app opened on results for '$q'. To play one, read_screen and tap the first result.", "Search $app")
        }
    }

    private val playMusic = object : SimpleTool(
        "play_music", "Play a song/artist/playlist. Optionally in a specific app.",
        params {
            string("query", "Song, artist or playlist", required = true)
            string("app", "Optional: youtube music | spotify | youtube | jiosaavn | gaana")
        },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            val query = args.requireString("query")
            val wanted = args.string("app")?.lowercase()?.let { musicApps[it] }
            val pkg = wanted?.takeIf { apps.isInstalled(it) } ?: musicApps.values.firstOrNull { apps.isInstalled(it) }
            val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
                .putExtra(android.app.SearchManager.QUERY, query)
                .putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
            if (pkg != null) intent.setPackage(pkg)
            var outcome = launcher.launch(intent, "Play $query")
            if (outcome == ActivityLauncher.Outcome.NOT_FOUND) {
                val yt = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(query)}"))
                outcome = launcher.launch(yt, "Play $query")
            }
            return launcher.result(
                outcome,
                "opened the music app for '$query'. It normally starts by itself. Only if Sir says it is not playing: read_screen " +
                    "once and tap the first result in THIS app. Do not open another app and do not search again.",
                "Play $query",
            )
        }
    }

    private val mediaControl = object : SimpleTool(
        "media_control", "Control whatever is playing: play, pause, toggle, next, previous, stop.",
        params { string("action", "play | pause | toggle | next | previous | stop", required = true, enum = listOf("play", "pause", "toggle", "next", "previous", "stop")) },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            val am = audio ?: return ToolResult.fail("no audio service")
            val key = when (args.enum("action", listOf("play", "pause", "toggle", "next", "previous", "stop"))) {
                "play" -> KeyEvent.KEYCODE_MEDIA_PLAY
                "pause" -> KeyEvent.KEYCODE_MEDIA_PAUSE
                "toggle" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
                "previous" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
                "stop" -> KeyEvent.KEYCODE_MEDIA_STOP
                else -> return ToolResult.fail("unknown action")
            }
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, key))
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, key))
            return ToolResult.ok("media ${args.string("action")}")
        }
    }

    val tools: List<JarvisTool> = listOf(
        openApp, closeApp, uninstallApp, installApp, setAlarm, setTimer, createReminder, addCalendarEvent, flashlight, setVolume,
        setBrightness, openSettings, setToggle, searchInApp, screenshot, lockScreen, deviceStatus, playMusic, mediaControl,
        phoneSetting,
    )
}
