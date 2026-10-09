package com.jinwoo.twilightandyou

import android.content.Context
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import com.jinwoo.twilightandyou.data.*
import com.jinwoo.twilightandyou.model.*
import com.jinwoo.twilightandyou.ui.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class AutomaticLocationFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun openingRefreshingAndReturningUseLocationButFixedModeDoesNot(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val calls = AtomicInteger()
        val displayed = AtomicReference<WidgetSettings>()
        ApiKeyStore(context).clear()
        val repository = LiveRepository(context)
        repository.clearCache()
        try {
            compose.activityRule.scenario.onActivity { activity ->
                activity.setContent {
                    var settings by remember { mutableStateOf(WidgetSettings(autoLocation = true, autoStation = false)) }
                    val current = settings
                    SideEffect { displayed.set(current) }
                    TwilightTheme {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            androidx.compose.material3.Button(onClick = { settings = settings.copy(autoLocation = false) }) { androidx.compose.material3.Text("시험 고정 지역") }
                            LiveDataPanel(settings, { settings = it }, {}, repository, automaticLocator = { previous ->
                                val count = calls.incrementAndGet()
                                LocationUpdate(previous.copy(region = "시험 위치 $count", latitude = 35.1796, longitude = 129.0756,
                                    airArea = "부산", station = "시험 측정소"), "위치 확인 완료", true)
                            })
                        }
                    }
                }
            }
            compose.waitUntil(10_000) { displayed.get()?.region == "시험 위치 1" }
            compose.waitUntil(10_000) { compose.onAllNodes(hasText("새로고침")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("새로고침").performScrollTo().performClick()
            compose.waitUntil(10_000) { displayed.get()?.region == "시험 위치 2" }
            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            val stoppedCalls = calls.get()
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            compose.waitUntil(10_000) { calls.get() > stoppedCalls }
            compose.onNodeWithText("시험 고정 지역").performScrollTo().performClick()
            val fixedCalls = calls.get()
            compose.onNodeWithText("새로고침").performScrollTo().performClick()
            compose.waitForIdle()
            assertEquals(fixedCalls, calls.get())
            assertEquals("부산", displayed.get().forecastArea)
        } catch (failure: Throwable) {
            throw AssertionError("Location test: calls=${calls.get()}, displayed=${displayed.get()}, lifecycle=${compose.activity.lifecycle.currentState}\n${compose.onRoot().printToString()}", failure)
        } finally { repository.clearCache() }
    }
}
