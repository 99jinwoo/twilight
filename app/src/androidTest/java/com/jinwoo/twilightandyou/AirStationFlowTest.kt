package com.jinwoo.twilightandyou

import android.content.Context
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jinwoo.twilightandyou.data.*
import com.jinwoo.twilightandyou.data.remote.*
import com.jinwoo.twilightandyou.model.*
import com.jinwoo.twilightandyou.ui.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZonedDateTime
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class AirStationFlowTest {
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
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    activity.setContent {
                        var settings by remember { mutableStateOf(WidgetSettings()) }
                        TwilightTheme {
                            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                                LiveDataPanel(settings, { settings = it }, onUpdated = {}, dataRepository = repository)
                            }
                        }
                    }
                }
                clickText("가까운 측정소 찾기")
                clickText("종로구 ·")
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
            }
        } finally { ApiKeyStore(context).clear(); repository.clearCache() }
    }

    private fun clickText(text: String) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < deadline) {
            val root = automation.rootInActiveWindow
            var node = find(root) { it.isVisibleToUser && it.text?.contains(text) == true }
            if (node != null) {
                while (node != null && !node.isClickable) node = node.parent
                if (node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) return
            }
            find(root) { it.isScrollable }?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            SystemClock.sleep(200)
        }
        fail("Could not click $text")
    }

    private fun find(node: AccessibilityNodeInfo?, match: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (node == null) return null
        if (match(node)) return node
        for (i in 0 until node.childCount) find(node.getChild(i), match)?.let { return it }
        return null
    }
}
