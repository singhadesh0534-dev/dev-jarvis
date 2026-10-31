package com.adesh.devjarvis.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/** Open-Meteo: free, bina API key ke weather. */
@Singleton
class WeatherClient @Inject constructor(private val http: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun summary(city: String): String? = withContext(Dispatchers.IO) {
        val place = geocode(city) ?: return@withContext null
        val url = "https://api.open-meteo.com/v1/forecast".toHttpUrl().newBuilder()
            .addQueryParameter("latitude", place.lat.toString())
            .addQueryParameter("longitude", place.lon.toString())
            .addQueryParameter("current", "temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m")
            .addQueryParameter("daily", "temperature_2m_max,temperature_2m_min,precipitation_probability_max")
            .addQueryParameter("timezone", "auto")
            .addQueryParameter("forecast_days", "1")
            .build()
        val root = get(url.toString()) ?: return@withContext null
        val cur = root["current"]!!.jsonObject
        val daily = root["daily"]!!.jsonObject
        fun d(o: JsonObject, k: String) = (o[k] as? JsonArray)?.firstOrNull()?.jsonPrimitive?.double
        val code = cur["weather_code"]!!.jsonPrimitive.int
        buildString {
            append("${place.name}: ${describe(code)}, ${cur["temperature_2m"]!!.jsonPrimitive.double.roundToInt()}°C")
            append(" (feels ${cur["apparent_temperature"]!!.jsonPrimitive.double.roundToInt()}°C)")
            append(", humidity ${cur["relative_humidity_2m"]!!.jsonPrimitive.double.roundToInt()}%")
            append(", wind ${cur["wind_speed_10m"]!!.jsonPrimitive.double.roundToInt()} km/h")
            val hi = d(daily, "temperature_2m_max")
            val lo = d(daily, "temperature_2m_min")
            if (hi != null && lo != null) append(". Today ${lo.roundToInt()}–${hi.roundToInt()}°C")
            d(daily, "precipitation_probability_max")?.let { append(", rain chance ${it.roundToInt()}%") }
        }
    }

    private data class Place(val name: String, val lat: Double, val lon: Double)

    private fun geocode(city: String): Place? {
        val url = "https://geocoding-api.open-meteo.com/v1/search".toHttpUrl().newBuilder()
            .addQueryParameter("name", city)
            .addQueryParameter("count", "1")
            .build()
        val first = (get(url.toString())?.get("results") as? JsonArray)?.firstOrNull()?.jsonObject ?: return null
        return Place(
            first["name"]!!.jsonPrimitive.content,
            first["latitude"]!!.jsonPrimitive.double,
            first["longitude"]!!.jsonPrimitive.double,
        )
    }

    private fun get(url: String): JsonObject? =
        http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) null else json.parseToJsonElement(r.body!!.string()).jsonObject
        }

    /** WMO weather codes. */
    private fun describe(code: Int) = when (code) {
        0 -> "clear sky"
        1, 2 -> "partly cloudy"
        3 -> "overcast"
        45, 48 -> "fog"
        in 51..57 -> "drizzle"
        in 61..67 -> "rain"
        in 71..77 -> "snow"
        in 80..82 -> "rain showers"
        in 95..99 -> "thunderstorm"
        else -> "mixed weather"
    }

}
