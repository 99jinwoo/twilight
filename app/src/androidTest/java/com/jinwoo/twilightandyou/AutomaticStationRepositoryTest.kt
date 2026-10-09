package com.jinwoo.twilightandyou

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.jinwoo.twilightandyou.data.*
import com.jinwoo.twilightandyou.data.remote.*
import com.jinwoo.twilightandyou.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.ZonedDateTime

class AutomaticStationRepositoryTest {
    @Test fun stationTimeoutRetriesAfterOneMinuteAndSuccessfulMetadataIsReused(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var now = ZonedDateTime.parse("2026-10-09T09:00:00+09:00")
        var calls = 0
        var failing = true
        val repo = LiveRepository(context, PublicWeatherApi { path, _, params ->
            assertEquals(PublicWeatherApi.STATIONS, path)
            assertEquals("서울", params["addr"])
            assertEquals("100", params["numOfRows"])
            calls++
            if (failing) throw ApiFailure(DataProblem.TIMEOUT)
            """{"response":{"header":{"resultCode":"00"},"body":{"totalCount":3,"items":[
              {"stationName":"먼 측정소","dmX":"127.1","dmY":"37.6","item":"PM10, PM2.5"},
              {"stationName":"가까운 측정소","dmX":"126.979","dmY":"37.566","item":"PM10, PM2.5"},
              {"stationName":"PM10 전용","dmX":"126.978","dmY":"37.5665","item":"PM10"}
            ]}}}"""
        }) { now }
        try {
            repo.clearCache(); ApiKeyStore(context).save(ApiKeys(air = "synthetic-station-key"))
            val original = WidgetSettings(station = "마지막 측정소")
            val failed = repo.automaticStation(original)
            assertEquals(DataProblem.TIMEOUT, failed.issue)
            assertEquals("마지막 측정소", failed.settings.station)
            now = now.plusSeconds(20)
            repo.automaticStation(original)
            assertEquals(1, calls)
            now = now.plusSeconds(45); failing = false
            val recovered = repo.automaticStation(WidgetSettings())
            assertEquals("가까운 측정소", recovered.settings.station)
            assertNull(recovered.issue)
            assertEquals(2, calls)
            now = now.plusHours(1)
            assertEquals(recovered.settings.station, repo.automaticStation(WidgetSettings()).settings.station)
            assertEquals("Successful station metadata should be reused for the day", 2, calls)
            val manual = WidgetSettings(station = "수동 고정", autoStation = false)
            assertEquals(manual, repo.automaticStation(manual).settings)
            assertEquals(2, calls)
        } finally { ApiKeyStore(context).clear(); repo.clearCache() }
    }

    @Test fun failedMunicipalQueryFallsBackToProvinceWithoutDownloadingNationwide(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val queries = mutableListOf<String>()
        val repo = LiveRepository(context, PublicWeatherApi { _, _, params ->
            val query = params.getValue("addr")
            queries += query
            if (query == "수원시") throw ApiFailure(DataProblem.TIMEOUT)
            """{"response":{"header":{"resultCode":"00"},"body":{"items":[{"stationName":"광교동","dmX":"127.0508","dmY":"37.2892","item":"PM10, PM2.5"}]}}}"""
        })
        try {
            repo.clearCache(); ApiKeyStore(context).save(ApiKeys(air = "synthetic-station-key"))
            val selected = repo.automaticStation(WidgetSettings(region = "경기도 수원시", latitude = 37.2636, longitude = 127.0286, airArea = "경기남부"))
            assertEquals(listOf("수원시", "경기"), queries)
            assertEquals("광교동", selected.settings.station)
            assertNull(selected.issue)
        } finally { ApiKeyStore(context).clear(); repo.clearCache() }
    }
}
