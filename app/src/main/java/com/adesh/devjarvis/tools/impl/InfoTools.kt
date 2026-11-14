package com.adesh.devjarvis.tools.impl

import com.adesh.devjarvis.brain.JarvisBrain
import com.adesh.devjarvis.settings.SettingsRepository
import com.adesh.devjarvis.tools.Calculator
import com.adesh.devjarvis.tools.JarvisTool
import com.adesh.devjarvis.tools.SimpleTool
import com.adesh.devjarvis.tools.ToolArgs
import com.adesh.devjarvis.tools.ToolContext
import com.adesh.devjarvis.tools.ToolResult
import com.adesh.devjarvis.tools.WeatherClient
import com.adesh.devjarvis.tools.params
import com.adesh.devjarvis.reminders.ReminderScheduler
import com.adesh.devjarvis.tools.CalendarReader
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/** Weather, web answers, calculator, time. */
@Singleton
class InfoTools @Inject constructor(
    private val weather: WeatherClient,
    private val settings: SettingsRepository,
    private val calendar: CalendarReader,
    private val reminders: ReminderScheduler,
    // Provider breaks the JarvisBrain -> ToolRegistry -> InfoTools -> JarvisBrain cycle.
    private val brainProvider: Provider<JarvisBrain>,
    private val documents: Provider<com.adesh.devjarvis.vision.DocumentReader>,
) {
    private val askFile = object : SimpleTool(
        "ask_file",
        "Answer a question about the last photo / PDF / document Sir gave JARVIS with the + button (\"isme total kitna hai\", " +
            "\"is bill ki date kya hai\", \"ise short mein samjhao\").",
        params { string("question", "Sir's question about the file", required = true) },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            val reader = documents.get()
            if (reader.last == null) return ToolResult.fail("Sir has not given a file yet: ask him to tap + and pick it")
            val answer = reader.askLast(args.requireString("question"))
                ?: return ToolResult.fail("could not read the file now (it may be gone, or vision is not answering)")
            return ToolResult.ok("From ${reader.lastName}: $answer")
        }
    }

    private val webAnswer = object : SimpleTool(
        "web_answer", "Answer a question that needs current/live info from the web (news, scores, prices, 'aaj ka', latest anything).",
        params { string("query", "What Sir wants to know", required = true) },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            val query = args.requireString("query")
            val brain = brainProvider.get()
            brain.webAnswer(query)?.let { return ToolResult.ok(it) }
            // Web search busy (its own limit) or down: what JARVIS already knows still answers most questions.
            val known = brain.complete(com.adesh.devjarvis.brain.SystemPrompt.WEB_FALLBACK, query, json = false)
                ?: return ToolResult.fail("web search and the backup brain are both unreachable right now — tell Sir in one line and suggest a minute later")
            return ToolResult.ok(
                "NOT live-checked (web search busy right now), from your own knowledge: $known\n" +
                    "If this is about today's news, live scores, prices or weather, tell Sir in a few words it may not be the latest.",
            )
        }
    }

    private val schedule = object : SimpleTool(
        "get_schedule", "Sir's calendar events and JARVIS reminders for today or tomorrow.",
        params { string("day", "today | tomorrow", enum = listOf("today", "tomorrow")) },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            val zone = ZoneId.systemDefault()
            val date = LocalDate.now().plusDays(if (args.enum("day", listOf("today", "tomorrow")) == "tomorrow") 1 else 0)
            val from = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val to = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val events = calendar.events(from, to)
            val fmt = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
            val rem = reminders.between(from, to).map { java.time.Instant.ofEpochMilli(it.atMillis).atZone(zone).format(fmt) + " " + it.text }
            val parts = buildList {
                add(if (events.isEmpty()) "no calendar events" else "events: " + events.joinToString("; "))
                add(if (rem.isEmpty()) "no reminders" else "reminders: " + rem.joinToString("; "))
                if (!calendar.hasPermission()) add("(calendar permission is off — Sir can allow it in Setup)")
            }
            return ToolResult.ok(parts.joinToString(". "))
        }
    }

    private val weatherTool = object : SimpleTool(
        "weather", "Current weather and today's forecast. City defaults to Sir's home city from Settings.",
        params { string("city", "City name, e.g. Mumbai") },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            val city = args.string("city") ?: settings.current.homeCity.ifBlank { null }
                ?: return ToolResult.fail("which city? (Sir can set a home city in Settings)")
            return weather.summary(city)?.let { ToolResult.ok(it) } ?: ToolResult.fail("couldn't get weather for $city")
        }
    }

    private val calculate = object : SimpleTool(
        "calculate", "Exact arithmetic: + - * / % ^ brackets sqrt(). Use for any non-trivial math.",
        params { string("expression", "e.g. (1250*18)/100", required = true) },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult = try {
            ToolResult.ok(Calculator.format(Calculator.evaluate(args.requireString("expression"))))
        } catch (e: Calculator.CalcError) {
            ToolResult.fail("can't calculate: ${e.message}")
        }
    }

    private val time = object : SimpleTool("get_time", "Current local date and time.") {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult =
            ToolResult.ok(ZonedDateTime.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy, h:mm a", Locale.ENGLISH)))
    }

    private val setWakeWord = object : SimpleTool(
        "set_wake_word",
        "Change the phrase that wakes JARVIS (\"ab se 'hello shiv' pe jaagna\"). Pass phrase \"default\" to go back to Hello Dev / Jarvis.",
        params { string("phrase", "New wake phrase, 1-3 simple English words, or \"default\"", required = true) },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            val phrase = args.requireString("phrase").lowercase().replace(Regex("[^a-z ]"), " ").trim().replace(Regex("\\s+"), " ")
            if (phrase == "default" || phrase.isEmpty()) {
                settings.update { it.copy(customWakePhrases = "") }
                return ToolResult.ok("wake word back to default: Hello Dev / Jarvis")
            }
            if (phrase.split(' ').size > 3) return ToolResult.fail("wake phrase must be 1-3 words")
            if (!ctx.confirm("Sir, ab se main sirf \"$phrase\" pe jaagun? Hello Dev kaam nahi karega.")) return ToolResult.ok("kept the old wake word")
            settings.update { it.copy(customWakePhrases = phrase) }
            return ToolResult.ok(
                "wake word is now '$phrase'. Tell Sir: the offline engine only knows common English words, so if it " +
                    "doesn't wake on this, he can tap the reactor and say 'wake word default karo'.",
            )
        }
    }

    val tools: List<JarvisTool> = listOf(weatherTool, calculate, time, schedule, webAnswer, askFile, setWakeWord)
}
