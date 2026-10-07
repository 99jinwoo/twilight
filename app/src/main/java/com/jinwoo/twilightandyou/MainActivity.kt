package com.jinwoo.twilightandyou

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.runtime.*
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.jinwoo.twilightandyou.data.SettingsStore
import com.jinwoo.twilightandyou.model.*
import com.jinwoo.twilightandyou.ui.*
import com.jinwoo.twilightandyou.widget.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        val requestedId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0)
        val id = requestedId.takeIf { it in widgetIds(this) } ?: 0
        setContent {
            val scope = rememberCoroutineScope()
            var settings by remember { mutableStateOf<WidgetSettings?>(null) }
            var busy by remember { mutableStateOf(false) }
            var message by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(id) { settings = SettingsStore(this@MainActivity).read(id) }
            TwilightTheme {
                settings?.let { current ->
                    TwilightScreen(
                        settings = current,
                        onChange = { settings = it },
                        onPin = if (id == 0) ({ shape: WidgetShape ->
                            scope.launch {
                                SettingsStore(this@MainActivity).save(0, current)
                                val receiver = when (shape) {
                                    WidgetShape.COMPACT -> CompactWidgetReceiver::class.java
                                    WidgetShape.WIDE -> WideWidgetReceiver::class.java
                                    WidgetShape.TALL -> TallWidgetReceiver::class.java
                                }
                                val manager = AppWidgetManager.getInstance(this@MainActivity)
                                val callback = PendingIntent.getBroadcast(this@MainActivity,
                                    kotlin.random.Random.nextInt(), PinWidgetReceiver.intent(this@MainActivity, current),
                                    PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_MUTABLE)
                                val requested = manager.isRequestPinAppWidgetSupported &&
                                    manager.requestPinAppWidget(ComponentName(this@MainActivity, receiver), null, callback)
                                if (!requested) callback.cancel()
                                message = if (requested) "홈화면 추가 창에서 위치를 정해주세요."
                                    else "홈화면을 길게 눌러 위젯 목록에서 Twilight를 선택해주세요."
                            }
                        }) else null,
                        onSave = if (id != 0) {{
                            scope.launch {
                                busy = true
                                try {
                                    SettingsStore(this@MainActivity).save(id, current)
                                    TwilightWidget().update(this@MainActivity, GlanceAppWidgetManager(this@MainActivity).getGlanceIdBy(id))
                                    message = "위젯 설정을 저장했습니다."
                                } catch (e: CancellationException) { throw e }
                                catch (_: Exception) { message = "저장하지 못했습니다. 다시 시도해주세요." }
                                finally { busy = false }
                            }
                        }} else null,
                        editingWidget = id != 0, busy = busy, message = message
                    )
                }
            }
        }
    }
}

class WidgetConfigurationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        setResult(RESULT_CANCELED)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID || id !in widgetIds(this)) { finish(); return }
        setContent {
            val scope = rememberCoroutineScope()
            var settings by remember { mutableStateOf<WidgetSettings?>(null) }
            var busy by remember { mutableStateOf(false) }
            var message by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(id) {
                val store = SettingsStore(this@WidgetConfigurationActivity)
                settings = if (store.isConfigured(id)) store.read(id) else store.read(0)
            }
            TwilightTheme {
                settings?.let { current ->
                    TwilightScreen(current, { settings = it }, null, {
                        scope.launch {
                            busy = true
                            try {
                                SettingsStore(this@WidgetConfigurationActivity).save(id, current)
                                TwilightWidget().update(this@WidgetConfigurationActivity,
                                    GlanceAppWidgetManager(this@WidgetConfigurationActivity).getGlanceIdBy(id))
                                WidgetSchedule.reconcile(this@WidgetConfigurationActivity)
                                setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                                finish()
                            } catch (e: CancellationException) { throw e }
                                catch (_: Exception) { message = "저장하지 못했습니다. 다시 시도해주세요." }
                            finally { busy = false }
                        }
                    }, true, busy, message)
                }
            }
        }
    }
}
