package com.adesh.devjarvis.tools.impl

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.adesh.devjarvis.core.LocationFinder
import com.adesh.devjarvis.safety.Sos
import com.adesh.devjarvis.settings.SettingsRepository
import com.adesh.devjarvis.tools.ContactRepository
import com.adesh.devjarvis.tools.ContactRepository.Lookup
import com.adesh.devjarvis.tools.JarvisTool
import com.adesh.devjarvis.tools.SimpleTool
import com.adesh.devjarvis.tools.ToolArgs
import com.adesh.devjarvis.tools.ToolContext
import com.adesh.devjarvis.tools.ToolResult
import com.adesh.devjarvis.tools.params
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Emergency SOS: Sir ke chune hue logon ko location ke saath SMS. Ek baar confirm, phir turant. */
@Singleton
class SafetyTools @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val contacts: ContactRepository,
    private val location: LocationFinder,
) {
    private fun has(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

    private val sos = object : SimpleTool(
        "emergency_sos",
        "EMERGENCY only: text Sir's saved emergency contacts that he needs help, with his current location link. " +
            "Use when Sir says SOS / emergency / \"madad bhejo\". The app asks him to confirm once.",
        params { string("note", "Optional few words about what happened, in Sir's words") },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            val wanted = Sos.contacts(settings.current.emergencyContacts)
            if (wanted.isEmpty()) {
                return ToolResult.fail("no emergency contacts saved — Sir must add them in JARVIS Settings > Emergency SOS. For a real emergency he can call 112.")
            }
            if (!has(Manifest.permission.SEND_SMS)) {
                return ToolResult.fail("SMS permission missing — Sir can allow it in JARVIS Setup. For a real emergency he can call 112.")
            }
            val targets = wanted.mapNotNull { w -> (contacts.resolve(w) as? Lookup.Found)?.let { it.contact.name to it.number } }
            if (targets.isEmpty()) return ToolResult.fail("could not find a phone number for: ${wanted.joinToString()} — Sir should save numbers in Settings > Emergency SOS")
            val names = targets.joinToString(", ") { it.first }
            if (!ctx.confirm("Sir, emergency SMS aapki location ke saath $names ko bhej doon?")) {
                return ToolResult.ok("Sir said no — nothing sent")
            }
            val fix = location.current()
            val text = Sos.message(
                fix?.latitude, fix?.longitude, fix?.accuracy,
                LocalTime.now().format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)),
                args.string("note"),
            )
            @Suppress("DEPRECATION")
            val sms: SmsManager = (if (Build.VERSION.SDK_INT >= 31) context.getSystemService(SmsManager::class.java) else SmsManager.getDefault())
                ?: return ToolResult.fail("SMS not available on this phone")
            val sent = targets.filter { (_, number) ->
                runCatching { sms.sendMultipartTextMessage(number, null, sms.divideMessage(text), null, null) }.isSuccess
            }
            if (sent.isEmpty()) return ToolResult.fail("the SMS could not be sent (no SIM / network?) — tell Sir to call 112")
            val where = when {
                fix != null -> "with his location"
                !location.hasPermission() -> "WITHOUT location (location permission not allowed)"
                else -> "WITHOUT location (location is off or unavailable)"
            }
            return ToolResult.ok("emergency SMS handed to the phone for sending to ${sent.joinToString(", ") { it.first }} $where. Delivery is not confirmed.")
        }
    }

    val tools: List<JarvisTool> = listOf(sos)
}
