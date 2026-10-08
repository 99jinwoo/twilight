package com.jinwoo.twilightandyou.data.remote

import com.jinwoo.twilightandyou.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.net.SocketTimeoutException
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLException

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

class HttpsApiTransport(private val connect: (URL) -> HttpsURLConnection = { it.openConnection() as HttpsURLConnection }) : ApiTransport {
    override suspend fun get(path: String, key: String, params: Map<String, String>): String = withContext(Dispatchers.IO) {
        // Only these public providers receive the key; redirects are never followed.
        require(path in PublicWeatherApi.paths)
        val normalizedKey = try { normalizeServiceKey(key) } catch (_: IllegalArgumentException) { throw ApiFailure(DataProblem.AUTH) }
        val query = (params + ("serviceKey" to normalizedKey)).entries.joinToString("&") {
            "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}"
        }
        val connection = connect(URL("https://apis.data.go.kr/$path?$query"))
        try {
            connection.connectTimeout = 12_000
            connection.readTimeout = 15_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/json")
            val code = connection.responseCode
            val input = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = input?.use { stream ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > 4 * 1024 * 1024) throw ApiFailure(DataProblem.FORMAT)
                    output.write(buffer, 0, count)
                }
                output.toString("UTF-8")
            }.orEmpty()
            ApiParsers.failure(body)?.let { throw ApiFailure(it.problem, code, it.providerCode) }
            if (code !in 200..299) throw ApiFailure(when (code) {
                401, 403 -> DataProblem.AUTH
                429 -> DataProblem.QUOTA
                408, 504 -> DataProblem.TIMEOUT
                in 500..599 -> DataProblem.SERVER
                else -> DataProblem.REQUEST
            }, code)
            body
        } catch (e: CancellationException) { throw e }
        catch (e: ApiFailure) { throw e }
        catch (_: SocketTimeoutException) { throw ApiFailure(DataProblem.TIMEOUT) }
        catch (_: SSLException) { throw ApiFailure(DataProblem.SECURE_CONNECTION) }
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
