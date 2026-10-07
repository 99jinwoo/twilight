package com.jinwoo.twilightandyou

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
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

@RunWith(AndroidJUnit4::class)
class WidgetRenderingTest {
    @Test fun actualRemoteViewsContainAllRequiredFieldsAtThreeSizes() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val sizes = listOf(56 to 72, 100 to 112, 140 to 72, 224 to 112, 112 to 228)
        for ((width, height) in sizes) {
            val remoteViews = withTimeout(30_000) {
                TwilightWidget(WidgetSettings(showSample = true)).compose(context, size = DpSize(width.dp, height.dp))
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
                    val texts = descendants(view).filterIsInstance<TextView>()
                    val combined = texts.joinToString(" ") { it.text.toString() }
                    assertTrue(combined, combined.contains("22°") && combined.contains("☀"))
                    assertTrue(combined, combined.contains("미") && combined.contains("초"))
                    assertTrue(combined, combined.contains("EENT") && combined.contains("18:54"))
                    assertTrue(combined, combined.contains("샘플"))
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
                }
        }
    }

    @Test fun mainScreenLaunchesAndSavesScreenshot() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch(MainActivity::class.java).use {
            instrumentation.waitForIdleSync()
            val deadline = SystemClock.uptimeMillis() + 10_000
            while (!containsText(instrumentation.uiAutomation.rootInActiveWindow, "기온 · 두 가지 먼지") && SystemClock.uptimeMillis() < deadline) {
                SystemClock.sleep(100)
            }
            assertTrue("Settings screen did not load", containsText(instrumentation.uiAutomation.rootInActiveWindow, "기온 · 두 가지 먼지"))
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
