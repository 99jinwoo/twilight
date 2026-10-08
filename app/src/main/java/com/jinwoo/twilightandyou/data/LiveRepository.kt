package com.jinwoo.twilightandyou.data

import android.content.Context
import android.util.AtomicFile
import com.jinwoo.twilightandyou.data.remote.*
import com.jinwoo.twilightandyou.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private data class CachedSource(
    val items: List<JSONObject> = emptyList(), val received: Long = 0, val attempted: Long = 0,
    val token: String = "", val issued: String = "", val issue: DataProblem? = null,
    val httpStatus: Int? = null, val providerCode: String? = null
)
private data class SourcePlan(val id: String, val title: String, val path: String, val parameters: Map<String,String>,
    val token: String, val issued: ZonedDateTime? = null, val key: String, val fallback: Map<String,String>? = null)

class LiveRepository(context: Context, private val api: PublicWeatherApi = PublicWeatherApi(),
    private val clock: () -> ZonedDateTime = { ZonedDateTime.now(SEOUL) }) {
    private val context = context.applicationContext
    private val directory = File(context.noBackupFilesDir, "live-data").apply { mkdirs() }

    suspend fun cached(settings: WidgetSettings, now: ZonedDateTime = clock()): LiveSnapshot = withContext(Dispatchers.IO) {
        val keys = ApiKeyStore(context).read()
        val sources = plans(settings, now, keys)
        val records = sources.associate { it.title to read(it.id) }
        fun values(name: String) = records[name]?.items.orEmpty()
        val forecastRecord = records["기상청 시간별 예보"]
        LiveSnapshot(
            weather = ApiParsers.observation(values("기상청 실황")),
            forecast = ApiParsers.forecast(values("기상청 시간별 예보")),
            forecastIssuedAt = forecastRecord?.issued?.let { runCatching { ZonedDateTime.parse(it) }.getOrNull() },
            air = ApiParsers.air(values("에어코리아 실측"), now),
            airForecasts = ApiParsers.airForecasts(values("미세먼지 예보") + values("초미세먼지 예보"), now),
            statuses = sources.map { plan ->
                val record = records[plan.title]
                SourceStatus(plan.title, record?.received?.takeIf { it > 0 }?.let { Instant.ofEpochMilli(it).atZone(SEOUL) },
                    if (plan.key.isBlank()) DataProblem.MISSING_KEY
                    else if (plan.path == PublicWeatherApi.AIR && settings.station.isBlank()) DataProblem.STATION_REQUIRED
                    else record?.issue,
                    attemptedAt = record?.attempted?.takeIf { it > 0 }?.let { Instant.ofEpochMilli(it).atZone(SEOUL) },
                    httpStatus = record?.httpStatus, providerCode = record?.providerCode)
            }
        )
    }

    suspend fun refresh(settings: WidgetSettings, manual: Boolean = false): LiveSnapshot {
        val now = clock()
        refreshLock.withLock {
            val keys = ApiKeyStore(context).read()
            coroutineScope {
                plans(settings, now, keys).filter { it.key.isNotBlank() && (it.path != PublicWeatherApi.AIR || settings.station.isNotBlank()) }
                    .map { plan -> async(Dispatchers.IO) { refreshSource(plan, now, manual) } }.awaitAll()
            }
        }
        return cached(settings, now)
    }

    suspend fun nearestStations(settings: WidgetSettings): List<AirStation> {
        val point = settings.point ?: return emptyList()
        val now = clock()
        refreshLock.withLock {
            val key = ApiKeyStore(context).read().air
            if (key.isBlank()) throw ApiFailure(DataProblem.MISSING_KEY)
            withContext(Dispatchers.IO) {
                val plan = SourcePlan("stations-v1.1", "측정소", PublicWeatherApi.STATIONS,
                    mapOf("returnType" to "json", "ver" to "1.1"), now.toLocalDate().toString(), key = key)
                refreshSource(plan, now, manual = false)
                val record = read(plan.id) ?: throw ApiFailure(DataProblem.NO_DATA)
                if (record.items.isEmpty()) throw ApiFailure(record.issue ?: DataProblem.NO_DATA, record.httpStatus, record.providerCode)
            }
        }
        return withContext(Dispatchers.IO) { ApiParsers.stations(read("stations-v1.1")?.items.orEmpty()).sortedBy { point.distanceKm(it.point) }.take(5) }
    }

    suspend fun clearCache() = withContext(Dispatchers.IO) {
        refreshLock.withLock { directory.listFiles()?.filter { it.isFile }?.forEach { it.delete() }; Unit }
    }

    private suspend fun refreshSource(plan: SourcePlan, now: ZonedDateTime, manual: Boolean) {
        val old = read(plan.id) ?: CachedSource()
        val nowMillis = now.toInstant().toEpochMilli()
        val sinceAttempt = nowMillis - old.attempted
        // Share requests across widgets, and keep manual taps from exhausting provider quotas.
        if (sinceAttempt >= 0 && sinceAttempt < if (manual) 60_000 else 15 * 60_000) return
        if (!manual && old.issue == null && old.token == plan.token && old.items.isNotEmpty()) return
        try {
            var used = plan.parameters
            var items: List<JSONObject>
            try {
                items = api.fetch(plan.path, plan.key, used)
                if (items.isEmpty()) throw ApiFailure(DataProblem.NO_DATA)
            } catch (e: ApiFailure) {
                if (e.problem != DataProblem.NO_DATA || plan.fallback == null) throw e
                used = plan.fallback
                items = api.fetch(plan.path, plan.key, used)
                if (items.isEmpty()) throw ApiFailure(DataProblem.NO_DATA)
            }
            val issued = when (plan.path) {
                PublicWeatherApi.NCST -> ApiParsers.observation(items)?.at ?: throw ApiFailure(DataProblem.FORMAT)
                PublicWeatherApi.FCST -> items.mapNotNull { ApiParsers.dateTime(it.text("baseDate"), it.text("baseTime")) }.maxOrNull()
                    ?: throw ApiFailure(DataProblem.FORMAT)
                PublicWeatherApi.AIR -> ApiParsers.air(items, now)?.at ?: throw ApiFailure(DataProblem.NO_DATA)
                PublicWeatherApi.AIR_FORECAST -> ApiParsers.airForecasts(items, now).maxOfOrNull { it.issuedAt } ?: throw ApiFailure(DataProblem.NO_DATA)
                else -> plan.issued
            }
            val previousIssued = runCatching { ZonedDateTime.parse(old.issued) }.getOrNull()
            if (issued != null && previousIssued != null && issued.isBefore(previousIssued)) {
                write(plan.id, old.copy(attempted = nowMillis, issue = DataProblem.NO_DATA, httpStatus = null, providerCode = null))
                return
            }
            // Retry a fallback or a delayed publication at the next allowed refresh.
            val pending = if (used != plan.parameters || (issued != null && plan.issued != null && issued.isBefore(plan.issued))) DataProblem.NO_DATA else null
            write(plan.id, CachedSource(items, nowMillis, nowMillis, plan.token, issued?.toString().orEmpty(), pending))
        } catch (e: CancellationException) { throw e }
        catch (e: ApiFailure) { write(plan.id, old.copy(attempted = nowMillis, issue = e.problem, httpStatus = e.httpStatus, providerCode = e.providerCode)) }
        catch (_: Exception) { write(plan.id, old.copy(attempted = nowMillis, issue = DataProblem.NETWORK, httpStatus = null, providerCode = null)) }
    }

    private fun plans(settings: WidgetSettings, now: ZonedDateTime, keys: ApiKeys): List<SourcePlan> {
        val result = mutableListOf<SourcePlan>()
        settings.point?.let { point ->
            val grid = point.weatherGrid()
            for ((path, title, stamp) in listOf(
                Triple(PublicWeatherApi.NCST, "기상청 실황", observationBase(now)),
                Triple(PublicWeatherApi.FCST, "기상청 시간별 예보", forecastBase(now)))) {
                val common = mapOf("dataType" to "JSON", "nx" to "${grid.x}", "ny" to "${grid.y}")
                fun params(at: ZonedDateTime) = common + mapOf("base_date" to at.toLocalDate().format(DateTimeFormatter.BASIC_ISO_DATE), "base_time" to at.format(DateTimeFormatter.ofPattern("HHmm")))
                val previous = stamp.minusHours(if (path == PublicWeatherApi.NCST) 1 else 3)
                result += SourcePlan("$path/${grid.x}/${grid.y}", title, path, params(stamp), stamp.toString(), stamp, keys.kma, params(previous))
            }
        }
        result += SourcePlan("air/${settings.station.trim()}", "에어코리아 실측", PublicWeatherApi.AIR,
            mapOf("returnType" to "json", "stationName" to settings.station.trim(), "dataTerm" to "DAILY", "ver" to "1.5"),
            observationBase(now).toString(), key = keys.air)
        for (code in listOf("PM10", "PM25")) {
            val base = airForecastBase(now)
            val params = mapOf("returnType" to "json", "informCode" to code, "searchDate" to base.toLocalDate().toString(), "ver" to "1.1")
            result += SourcePlan("forecast-air/$code", if (code == "PM10") "미세먼지 예보" else "초미세먼지 예보", PublicWeatherApi.AIR_FORECAST,
                params, base.toString(), base, keys.air, params + ("searchDate" to base.minusDays(1).toLocalDate().toString()))
        }
        return result
    }

    private fun file(id: String): AtomicFile {
        val hash = MessageDigest.getInstance("SHA-256").digest(id.toByteArray()).joinToString("") { "%02x".format(it) }
        return AtomicFile(File(directory, "$hash.json"))
    }
    private fun read(id: String): CachedSource? = synchronized(fileLock) {
        try {
            val json = JSONObject(file(id).openRead().bufferedReader().use { it.readText() })
            val items = json.getJSONArray("items")
            CachedSource((0 until items.length()).map { items.getJSONObject(it) }, json.optLong("received"), json.optLong("attempted"),
                json.optString("token"), json.optString("issued"), DataProblem.entries.firstOrNull { it.name == json.optString("issue") },
                json.optInt("httpStatus").takeIf { it in 100..599 },
                json.optString("providerCode").takeIf { it.matches(Regex("[0-9]{1,4}")) })
        } catch (_: Exception) { null }
    }
    private fun write(id: String, value: CachedSource) = synchronized(fileLock) {
        val json = JSONObject().put("items", JSONArray(value.items)).put("received", value.received).put("attempted", value.attempted)
            .put("token", value.token).put("issued", value.issued).put("issue", value.issue?.name.orEmpty())
            .put("httpStatus", value.httpStatus).put("providerCode", value.providerCode)
        val target = file(id)
        val stream = target.startWrite()
        try { stream.write(json.toString().toByteArray()); target.finishWrite(stream) }
        catch (e: Exception) { target.failWrite(stream); throw e }
    }
    private companion object { val refreshLock = Mutex(); val fileLock = Any() }
}
