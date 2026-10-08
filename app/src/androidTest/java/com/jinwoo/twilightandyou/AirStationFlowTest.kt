package com.jinwoo.twilightandyou

import android.graphics.Bitmap
import java.io.File
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import android.content.Context
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.jinwoo.twilightandyou.data.*
import com.jinwoo.twilightandyou.data.remote.*
import com.jinwoo.twilightandyou.model.*
import com.jinwoo.twilightandyou.ui.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.runner.RunWith
import java.time.ZonedDateTime
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class AirStationFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun selectingNearestStationFetchesObservationWithoutAnotherRefreshTap() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val requestedStation = AtomicReference<String?>(null)
        val now = ZonedDateTime.parse("2026-10-08T12:30:00+09:00")
        val api = PublicWeatherApi { path, _, params ->
            val items = when (path) {
                PublicWeatherApi.STATIONS -> """[{"stationName":"종로구","dmX":"127.005028","dmY":"37.572025","item":"PM10, PM2.5"}]"""
                PublicWeatherApi.AIR -> {
                    requestedStation.set(params.getValue("stationName"))
                    """[{"dataTime":"2026-10-08 12:00","pm10Value":"42","pm25Value":"12"}]"""
                }
                PublicWeatherApi.AIR_FORECAST -> "[]"
                else -> error("Unexpected public API call")
            }
            """{"response":{"header":{"resultCode":"00"},"body":{"items":$items}}}"""
        }
        val repository = LiveRepository(context, api) { now }
        try {
            repository.clearCache()
            ApiKeyStore(context).save(ApiKeys(air = "synthetic-flow-key"))
            compose.activityRule.scenario.onActivity { activity ->
                activity.setContent {
                    var settings by remember { mutableStateOf(WidgetSettings()) }
                    TwilightTheme {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            LiveDataPanel(settings, { settings = it }, onUpdated = {}, dataRepository = repository)
                        }
                    }
                }
            }
            compose.onNodeWithText("가까운 측정소 찾기").performScrollTo().performClick()
            compose.waitUntil(10_000) { compose.onAllNodes(androidx.compose.ui.test.hasText("종로구 ·", substring = true)).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("종로구 ·", substring = true).performScrollTo().performClick()
            val deadline = SystemClock.uptimeMillis() + 10_000
            while (requestedStation.get() == null && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(100)
            assertEquals("Selecting a station must immediately query that station", "종로구", requestedStation.get())
            var saved: LiveSnapshot
            do {
                saved = repository.cached(WidgetSettings(station = "종로구"))
                if (saved.air != null) break
                SystemClock.sleep(100)
            } while (SystemClock.uptimeMillis() < deadline)
            assertEquals(42, saved.air!!.pm10)
            assertEquals(12, saved.air!!.pm25)
            compose.waitUntil(10_000) { compose.onAllNodes(androidx.compose.ui.test.hasText("42 μg/m³", substring = true)).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("42 μg/m³", substring = true).performScrollTo().assertIsDisplayed()
        } catch (failure: Throwable) {
            println(compose.onRoot().printToString())
            throw failure
        } finally {
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { bitmap ->
                val directory = File(context.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
                File(directory, "air-station-flow.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
            ApiKeyStore(context).clear(); repository.clearCache()
        }
    }

}
