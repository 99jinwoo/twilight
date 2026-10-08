package com.jinwoo.twilightandyou

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jinwoo.twilightandyou.data.*
import com.jinwoo.twilightandyou.data.remote.*
import com.jinwoo.twilightandyou.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.ZonedDateTime
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class LiveRepositoryTest {
    @Test fun keysRoundTripEncryptedAndCanBeCleared() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = ApiKeyStore(context)
        try {
            store.save(ApiKeys("synthetic-private-kma", "synthetic-private-air"))
            assertEquals("synthetic-private-kma", ApiKeyStore(context).read().kma)
            val bytes = File(context.noBackupFilesDir, "api-keys.enc").readText()
            assertFalse(bytes.contains("synthetic-private"))
            store.clear()
            assertEquals("", store.read().kma)
        } finally { store.clear() }
    }

    @Test fun partialFailureKeepsGoodSourcesAndCachesStayIsolatedByLocation() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var now = ZonedDateTime.parse("2026-10-08T17:30:00+09:00")
        var failAir = false
        var temperature = "22"
        val calls = AtomicInteger()
        val transport = ApiTransport { path, _, params ->
            calls.incrementAndGet()
            fun ok(items: String) = """{"response":{"header":{"resultCode":"00"},"body":{"items":$items,"totalCount":1}}}"""
            when (path) {
                PublicWeatherApi.NCST -> {
                    assertTrue(params.getValue("base_date").matches(Regex("\\d{8}")))
                    ok("""[{"baseDate":"${params.getValue("base_date")}","baseTime":"${params.getValue("base_time")}","category":"T1H","obsrValue":"$temperature"}]""")
                }
                PublicWeatherApi.FCST -> ok("""[{"baseDate":"${params.getValue("base_date")}","baseTime":"${params.getValue("base_time")}","fcstDate":"20261008","fcstTime":"1800","category":"TMP","fcstValue":"21"}]""")
                PublicWeatherApi.AIR -> {
                    if (failAir) throw ApiFailure(DataProblem.AUTH, 500, "20")
                    ok("""[{"dataTime":"2026-10-08 17:00","pm10Value":"42","pm25Value":"12"}]""")
                }
                PublicWeatherApi.AIR_FORECAST -> ok("""[{"informCode":"${params.getValue("informCode")}","informData":"2026-10-09","dataTime":"2026-10-08 17시 발표","informGrade":"서울 : 보통, 부산 : 좋음"}]""")
                else -> error("Unexpected path")
            }
        }
        val repo = LiveRepository(context, PublicWeatherApi(transport)) { now }
        val settings = WidgetSettings(station = "종로구")
        try {
            repo.clearCache()
            ApiKeyStore(context).save(ApiKeys("synthetic-private-kma", "synthetic-private-air"))
            val first = repo.refresh(settings)
            assertEquals(22.0, first.weather!!.temperature)
            assertEquals(42, first.air!!.pm10)
            val count = calls.get()
            repo.refresh(settings)
            assertEquals("Same-location widgets should share requests", count, calls.get())
            val other = repo.cached(WidgetSettings(region = "부산", station = "다른 측정소"))
            assertNull(other.weather)
            assertNull(other.air)
            now = now.plusMinutes(16)
            failAir = true; temperature = "24"
            val second = repo.refresh(settings, manual = true)
            assertEquals(24.0, second.weather!!.temperature)
            assertEquals(42, second.air!!.pm10)
            val failure = second.statuses.single { it.name == "에어코리아 실측" }
            assertEquals(DataProblem.AUTH, failure.issue)
            assertEquals(500, failure.httpStatus)
            assertEquals("20", failure.providerCode)
            assertEquals(now.toInstant(), failure.attemptedAt!!.toInstant())
            assertEquals(failure, repo.cached(settings).statuses.single { it.name == "에어코리아 실측" })
            now = now.plusMinutes(2)
            failAir = false
            val recovered = repo.refresh(settings, manual = true).statuses.single { it.name == "에어코리아 실측" }
            assertNull(recovered.issue)
            assertNull(recovered.httpStatus)
            assertNull(recovered.providerCode)
            File(context.noBackupFilesDir, "live-data").listFiles()!!.forEach { assertFalse(it.readText().contains("synthetic-private")) }
        } finally { ApiKeyStore(context).clear(); repo.clearCache() }
    }
}
