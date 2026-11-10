package com.adesh.devjarvis.tools.impl

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.adesh.devjarvis.core.Edition
import com.adesh.devjarvis.tools.ActivityLauncher
import com.adesh.devjarvis.tools.AppRepository
import com.adesh.devjarvis.tools.ContactRepository
import com.adesh.devjarvis.tools.ContactRepository.Lookup
import com.adesh.devjarvis.tools.JarvisTool
import com.adesh.devjarvis.tools.PhoneNumbers
import com.adesh.devjarvis.tools.SimpleTool
import com.adesh.devjarvis.tools.ToolArgs
import com.adesh.devjarvis.tools.ToolContext
import com.adesh.devjarvis.tools.ToolResult
import com.adesh.devjarvis.tools.params
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Call, SMS, WhatsApp. Call aur SMS se pehle hamesha Sir se poochha jaata hai. */
@Singleton
class CommunicationTools @Inject constructor(
    @ApplicationContext private val context: Context,
    private val contacts: ContactRepository,
    private val launcher: ActivityLauncher,
    private val apps: AppRepository,
) {
    private fun has(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

    /** Resolve "Mummy" / "+91..." to a number, or a ToolResult explaining why not. */
    private suspend fun resolve(who: String): Pair<String, String>? = when (val l = contacts.resolve(who)) {
        is Lookup.Found -> l.contact.name to l.number
        else -> null
    }

    private suspend fun lookupFailure(who: String): ToolResult = when (val l = contacts.resolve(who)) {
        is Lookup.Ambiguous -> ToolResult.fail("ambiguous: ${l.options.joinToString { it.name }}. Ask Sir which one.")
        Lookup.NoPermission -> ToolResult.fail(
            if (Edition.declares(context, Manifest.permission.READ_CONTACTS)) "contacts permission missing — Sir can allow it in JARVIS Setup, or say the number"
            else "this Lite edition can't read contacts — ask Sir for the number (the Full update adds contacts)"
        )
        Lookup.NotFound -> {
            val near = contacts.closest(who)
            ToolResult.fail(
                when {
                    near.isNotEmpty() -> "no contact saved exactly as '$who'. Closest saved names: ${near.joinToString("; ")}. " +
                        "Ask Sir politely which of these he means (or under what name the number is saved), then call the tool again with that exact name."
                    contacts.count() == 0 -> "JARVIS can't see any contacts on this phone (0 with a number) — Sir should check the Contacts permission in JARVIS Setup"
                    else -> "no contact like '$who' among ${contacts.count()} contacts. Ask Sir politely under what name the number is saved, or for the number itself."
                },
            )
        }
        is Lookup.Found -> ToolResult.fail("unexpected")
    }

    private fun who(args: ToolArgs) = args.string("contact") ?: args.string("number")

    private val makeCall = object : SimpleTool(
        "make_call",
        "Phone call to a contact (name or nickname like Mummy, Bhai) or a number. The app asks Sir to confirm, " +
            "unless urgent=true. Pass the name only (\"Papa\"), in whatever script you heard it.",
        params {
            string("contact", "Contact name/nickname as Sir said it")
            string("number", "Phone number, if Sir said digits")
            boolean("urgent", "true ONLY when Sir himself says it is an emergency / urgent / \"turant\" / \"bina pooche\": the call is placed at once, no confirmation")
        },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            val target = who(args) ?: return ToolResult.fail("who should I call?")
            val (name, number) = resolve(target) ?: return lookupFailure(target)
            val urgent = args.bool("urgent") == true
            if (!urgent && !ctx.confirm("Sir, $name ko call karun?")) return ToolResult.ok("Sir said no — call cancelled")
            val emergency = number.trimStart('+').let { it == "100" || it == "112" || it == "108" || it == "101" }
            val canCall = has(Manifest.permission.CALL_PHONE) && !emergency
            val intent = Intent(if (canCall) Intent.ACTION_CALL else Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(number)}"))
            val outcome = launcher.launch(intent, "Call $name")
            val done = if (canCall) "calling $name" else "dialer opened for $name — Sir must press call"
            return launcher.result(outcome, done, "Call $name", endsConversation = true)
        }
    }

    private val sendSms = object : SimpleTool(
        "send_sms",
        "Send an SMS text message. The app reads it back and asks Sir to confirm before sending.",
        params {
            string("contact", "Contact name/nickname")
            string("number", "Phone number, if given as digits")
            string("message", "Exact message text to send", required = true)
        },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            val message = args.requireString("message")
            val target = who(args) ?: return ToolResult.fail("whom should I send it to?")
            val (name, number) = resolve(target) ?: return lookupFailure(target)
            if (!has(Manifest.permission.SEND_SMS)) {
                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")).putExtra("sms_body", message)
                return launcher.result(launcher.launch(intent, "SMS to $name"), "SMS app opened with the message — Sir must press send", "SMS to $name")
            }
            if (!ctx.confirm("Sir, $name ko SMS: \"$message\". Bhej doon?")) return ToolResult.ok("Sir said no — SMS not sent")
            @Suppress("DEPRECATION")
            val sms: SmsManager = (if (Build.VERSION.SDK_INT >= 31) context.getSystemService(SmsManager::class.java) else SmsManager.getDefault())
                ?: return ToolResult.fail("SMS not available on this phone")
            val parts = sms.divideMessage(message)
            sms.sendMultipartTextMessage(number, null, parts, null, null)
            return ToolResult.ok("SMS sent to $name")
        }
    }

    private val sendWhatsApp = object : SimpleTool(
        "send_whatsapp",
        "Open WhatsApp chat with a contact with the message pre-filled. Sir taps send himself.",
        params {
            string("contact", "Contact name/nickname")
            string("number", "Phone number, if given as digits")
            string("message", "Message text", required = true)
        },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            val message = args.requireString("message")
            val target = who(args) ?: return ToolResult.fail("whom should I message?")
            val (name, number) = resolve(target) ?: return lookupFailure(target)
            val pkg = listOf("com.whatsapp", "com.whatsapp.w4b").firstOrNull { apps.isInstalled(it) }
                ?: return ToolResult.fail("WhatsApp is not installed")
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=${PhoneNumbers.forWhatsApp(number)}&text=${Uri.encode(message)}"))
                .setPackage(pkg)
            return launcher.result(launcher.launch(intent, "WhatsApp $name"), "WhatsApp opened with the message for $name — Sir taps send", "WhatsApp $name")
        }
    }

    val tools: List<JarvisTool> = listOf(makeCall, sendSms, sendWhatsApp)
}
