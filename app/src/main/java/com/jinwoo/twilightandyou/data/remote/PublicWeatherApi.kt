package com.jinwoo.twilightandyou.data.remote

import com.jinwoo.twilightandyou.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import javax.net.ssl.HttpsURLConnection

fun normalizeServiceKey(raw: String): String {
    val trimmed = raw.trim()
    require(trimmed.length <= 4096)
    return if ('%' in trimmed) URLDecoder.decode(trimmed.replace("+", "%2B"), "UTF-8") else trimmed
}

fun observationBase(now: ZonedDateTime): ZonedDateTime = now.withZoneSameInstant(SEOUL).minusMinutes(10).truncatedTo(ChronoUnit.HOURS)
fun forecastBase(now: ZonedDateTime): ZonedDateTime {
    val ready = now.withZoneSameInstant(SEOUL).minusMinutes(10)
    val hour = listOf(2, 5, 8, 11, 14, 17, 20, 23).lastOrNull { it <= ready.hour }
    return if (hour == null) ready.minusDays(1).withHour(23).truncatedTo(ChronoUnit.HOURS)
    else ready.withHour(hour).truncatedTo(ChronoUnit.HOURS)
}
fun airForecastBase(now: ZonedDateTime): ZonedDateTime {
    val ready = now.withZoneSameInstant(SEOUL).minusMinutes(10)
    val hour = listOf(5, 11, 17, 23).lastOrNull { it <= ready.hour }
    return if (hour == null) ready.minusDays(1).withHour(23).truncatedTo(ChronoUnit.HOURS)
    else ready.withHour(hour).truncatedTo(ChronoUnit.HOURS)
}

fun interface ApiTransport {
    suspend fun get(path: String, key: String, params: Map<String, String>): String
}

class HttpsApiTransport : ApiTransport {
    override suspend fun get(path: String, key: String, params: Map<String, String>): String = withContext(Dispatchers.IO) {
        // Only these public providers receive the key; redirects are never followed.
        require(path in PublicWeatherApi.paths)
        val query = (params + ("serviceKey" to normalizeServiceKey(key))).entries.joinToString("&") {
            "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}"
        }
        val connection = URL("https://apis.data.go.kr/$path?$query").openConnection() as HttpsURLConnection
        try {
            connection.connectTimeout = 12_000
            connection.readTimeout = 15_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/json")
            val code = connection.responseCode
            if (code == 401 || code == 403) throw ApiFailure(DataProblem.AUTH)
            if (code == 429) throw ApiFailure(DataProblem.QUOTA)
            if (code != 200) throw ApiFailure(DataProblem.NETWORK)
            connection.inputStream.use { stream ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > 4 * 1024 * 1024) throw ApiFailure(DataProblem.FORMAT)
                    output.write(buffer, 0, count)
                }
                output.toString("UTF-8")
            }
        } catch (e: ApiFailure) { throw e }
        catch (_: Exception) { throw ApiFailure(DataProblem.NETWORK) }
        finally { connection.disconnect() }
    }
}

class PublicWeatherApi(private val transport: ApiTransport = HttpsApiTransport()) {
    suspend fun fetch(path: String, key: String, parameters: Map<String, String>): List<JSONObject> {
        if (key.isBlank()) throw ApiFailure(DataProblem.MISSING_KEY)
        val items = mutableListOf<JSONObject>()
        for (page in 1..8) {
            val response = ApiParsers.page(transport.get(path, key, parameters + mapOf("pageNo" to "$page", "numOfRows" to "1000")))
            items += response.items.map(::sanitized)
            if (items.size >= response.total || response.total == 0) return items
            if (response.items.isEmpty()) throw ApiFailure(DataProblem.FORMAT)
        }
        throw ApiFailure(DataProblem.FORMAT)
    }

    // Persist only public measurements and their provenance, never request credentials or headers.
    private fun sanitized(row: JSONObject) = JSONObject().also { output ->
        publicFields.forEach { field -> if (row.has(field)) output.put(field, row.opt(field)) }
    }

    companion object {
        const val NCST = "1360000/VilageFcstInfoService_2.0/getUltraSrtNcst"
        const val FCST = "1360000/VilageFcstInfoService_2.0/getVilageFcst"
        const val AIR = "B552584/ArpltnInforInqireSvc/getMsrstnAcctoRltmMesureDnsty"
        const val AIR_FORECAST = "B552584/ArpltnInforInqireSvc/getMinuDustFrcstDspth"
        const val STATIONS = "B552584/MsrstnInfoInqireSvc/getMsrstnList"
        val paths = setOf(NCST, FCST, AIR, AIR_FORECAST, STATIONS)
        private val publicFields = setOf("baseDate", "baseTime", "category", "obsrValue", "fcstDate", "fcstTime", "fcstValue", "nx", "ny",
            "dataTime", "pm10Value", "pm25Value", "pm10Flag", "pm25Flag", "pm10Grade1h", "pm25Grade1h", "informCode", "informData",
            "informGrade", "informOverall", "stationName", "stationCode", "dmX", "dmY", "addr", "item", "mangName")
    }
}
