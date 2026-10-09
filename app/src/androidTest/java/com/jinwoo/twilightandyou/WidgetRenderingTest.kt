package com.jinwoo.twilightandyou

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.text.Spanned
import android.text.TextPaint
import android.text.style.CharacterStyle
import android.view.accessibility.AccessibilityNodeInfo
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.*
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jinwoo.twilightandyou.data.SettingsStore
import com.jinwoo.twilightandyou.model.*
import com.jinwoo.twilightandyou.widget.TwilightWidget
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.ZonedDateTime

@RunWith(AndroidJUnit4::class)
class WidgetRenderingTest {
    @Test fun actualRemoteViewsKeepBothTwilightsAndForecastsWithoutClipping() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val sizes = listOf(56 to 72, 100 to 112, 130 to 72, 140 to 72, 170 to 96, 224 to 112, 224 to 72, 112 to 228, 56 to 160)
        val failures = mutableListOf<String>()
        for ((width, height) in sizes) {
            val remoteViews = withTimeout(30_000) {
                TwilightWidget(WidgetSettings(showSample = true), ZonedDateTime.parse("2026-10-07T17:00:00+09:00")).compose(context, size = DpSize(width.dp, height.dp))
            }
                instrumentation.runOnMainSync {
                    val parent = FrameLayout(context)
                    val view = remoteViews.apply(context, parent)
                    val density = context.resources.displayMetrics.density
                    val w = (width * density).toInt()
                    val h = (height * density).toInt()
                    view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
                    view.layout(0, 0, w, h)
                    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    view.draw(Canvas(bitmap))
                    val directory = File(context.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
                    File(directory, "widget-${width}x${height}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                    try {
                    val texts = descendants(view).filterIsInstance<TextView>()
                    val combined = texts.joinToString(" ") { it.text.toString() }
                    assertTrue(combined, combined.contains("22°") && combined.contains("☀"))
                    assertTrue(combined, combined.contains("미") && combined.contains("초미"))
                    assertTrue(combined, combined.contains("EENT") && combined.contains("18:54"))
                    assertTrue(combined, combined.contains("오늘 EENT 18:54") && combined.contains("오늘 BMNT 05:38"))
                    assertFalse(combined, combined.contains("샘플") || combined.contains("17시"))
                    val upcoming = texts.first { it.text.toString() == "오늘 EENT 18:54" }
                    val previous = texts.first { it.text.toString() == "오늘 BMNT 05:38" }
                    val upcomingBounds = Rect().also { upcoming.getDrawingRect(it) }
                    val previousBounds = Rect().also { previous.getDrawingRect(it) }
                    (view as ViewGroup).offsetDescendantRectToMyCoords(upcoming, upcomingBounds)
                    view.offsetDescendantRectToMyCoords(previous, previousBounds)
                    assertTrue("Next twilight must be above previous", upcomingBounds.bottom <= previousBounds.top)
                    assertTrue("Next twilight should be bold", renderedPaint(upcoming).typeface.isBold)
                    assertNotEquals(upcoming.currentTextColor, previous.currentTextColor)
                    val shape = WidgetShape.forSize(width.toFloat(), height.toFloat())
                    if (shape != WidgetShape.COMPACT) {
                        assertTrue(combined, combined.contains("시간별 예보"))
                        assertTrue(combined, combined.contains("18시") && combined.contains("21°"))
                        assertTrue(combined, combined.contains("21시") && combined.contains("18°"))
                    }
                    for (text in texts.filter { it.text.isNotEmpty() }) {
                        val bounds = Rect().also { text.getDrawingRect(it) }
                        (view as ViewGroup).offsetDescendantRectToMyCoords(text, bounds)
                        assertTrue("Text outside widget: ${text.text}", Rect(0, 0, w, h).contains(bounds))
                        val layout = text.layout
                        assertNotNull("No text layout: ${text.text}", layout)
                        for (line in 0 until layout.lineCount) {
                            assertEquals("Ellipsized at ${width}×${height}: ${text.text}", 0, layout.getEllipsisCount(line))
                        }
                        assertTrue("Vertical clipping at ${width}×${height}: ${text.text}, layout=${layout.height}, view=${text.height}, padding=${text.compoundPaddingTop + text.compoundPaddingBottom}", layout.height <= text.height - text.compoundPaddingTop - text.compoundPaddingBottom)
                    }
                    } catch (error: AssertionError) {
                        failures += "${width}×${height}: ${error.message}"
                    }
                }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    private fun renderedPaint(view: TextView): TextPaint = TextPaint(view.paint).also { paint ->
        val text = view.text
        if (text is Spanned) text.getSpans(0, text.length, CharacterStyle::class.java)
            .forEach { it.updateDrawState(paint) }
    }

    @Test fun liveForecastBadgeAndCalculatedTwilightFitSmallWidgets() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val now = ZonedDateTime.parse("2026-10-08T23:00:00+09:00")
        val snapshot = LiveSnapshot(weather = WeatherObservation(now, 22.0, 0),
            air = AirObservation(now, 42, 12, "", "", "2", "1"),
            forecast = (0L..4L).map { WeatherForecast(now.plusHours(it), -9.0 - it, 1, 0) }, forecastIssuedAt = now.minusHours(3))
        for ((width, height) in listOf(56 to 72, 100 to 112, 130 to 72, 140 to 72, 170 to 96, 224 to 112, 112 to 228)) {
            val remoteViews = withTimeout(30_000) {
                TwilightWidget(WidgetSettings(), now, snapshot).compose(context, size = DpSize(width.dp, height.dp))
            }
            instrumentation.runOnMainSync {
                val parent = FrameLayout(context)
                val view = remoteViews.apply(context, parent)
                val density = context.resources.displayMetrics.density
                val w = (width * density).toInt(); val h = (height * density).toInt()
                view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
                view.layout(0, 0, w, h)
                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                val directory = File(context.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
                File(directory, "live-widget-${width}x${height}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
                val texts = descendants(view).filterIsInstance<TextView>()
                assertTrue(texts.any { it.text.toString() == "예" })
                assertTrue(texts.any { it.text.contains("오늘 EENT") })
                if (WidgetShape.forSize(width.toFloat(), height.toFloat()) == WidgetShape.WIDE) {
                    assertTrue("All four future hours must remain visible", texts.any { it.text.contains("03시") })
                    assertTrue(texts.any { it.text.contains("내일") })
                    val hours = listOf("00시", "01시", "02시", "03시").map { hour -> texts.single { it.text.toString() == hour } }
                    val positions = hours.map { text -> Rect().also { text.getDrawingRect(it); (view as ViewGroup).offsetDescendantRectToMyCoords(text, it) } }
                    assertEquals("All forecast times must share one horizontal row", 1, positions.map { it.top }.distinct().size)
                    assertTrue("Forecast hours must run left to right", positions.zipWithNext().all { (a,b) -> a.right <= b.left })
                }
                for (text in texts.filter { it.text.isNotEmpty() }) {
                    val bounds = Rect().also { text.getDrawingRect(it) }
                    (view as ViewGroup).offsetDescendantRectToMyCoords(text, bounds)
                    assertTrue("Live text outside widget: ${text.text}", Rect(0, 0, w, h).contains(bounds))
                    val layout = text.layout
                    assertNotNull(layout)
                    for (line in 0 until layout.lineCount) assertEquals("Live ellipsis ${width}x${height}: ${text.text}", 0, layout.getEllipsisCount(line))
                    assertTrue("Live clipping ${width}x${height}: ${text.text}", layout.height <= text.height - text.compoundPaddingTop - text.compoundPaddingBottom)
                }
            }
        }
    }

    @Test fun mainScreenLaunchesAndSavesScreenshot() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch(MainActivity::class.java).use {
            instrumentation.waitForIdleSync()
            val deadline = SystemClock.uptimeMillis() + 15_000
            while (!containsText(instrumentation.uiAutomation.rootInActiveWindow, "BMNT") && SystemClock.uptimeMillis() < deadline) {
                SystemClock.sleep(100)
            }
            assertTrue("Real widget preview did not load", containsText(instrumentation.uiAutomation.rootInActiveWindow, "BMNT"))
            val context = instrumentation.targetContext
            val directory = File(context.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
            instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                File(directory, "app.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        }
    }

    private fun containsText(node: AccessibilityNodeInfo?, value: String): Boolean {
        if (node == null) return false
        if (node.text?.contains(value) == true) return true
        return (0 until node.childCount).any { containsText(node.getChild(it), value) }
    }

    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
}
