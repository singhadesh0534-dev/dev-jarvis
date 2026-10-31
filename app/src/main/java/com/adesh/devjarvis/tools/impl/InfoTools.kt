package com.adesh.devjarvis.tools.impl

import com.adesh.devjarvis.settings.SettingsRepository
import com.adesh.devjarvis.tools.Calculator
import com.adesh.devjarvis.tools.JarvisTool
import com.adesh.devjarvis.tools.SimpleTool
import com.adesh.devjarvis.tools.ToolArgs
import com.adesh.devjarvis.tools.ToolContext
import com.adesh.devjarvis.tools.ToolResult
import com.adesh.devjarvis.tools.WeatherClient
import com.adesh.devjarvis.tools.params
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Weather, calculator, time. */
@Singleton
class InfoTools @Inject constructor(
    private val weather: WeatherClient,
    private val settings: SettingsRepository,
) {
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

    val tools: List<JarvisTool> = listOf(weatherTool, calculate, time)
}
