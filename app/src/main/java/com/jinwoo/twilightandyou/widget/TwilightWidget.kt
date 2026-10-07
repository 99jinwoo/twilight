package com.jinwoo.twilightandyou.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.clickable
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.jinwoo.twilightandyou.MainActivity
import com.jinwoo.twilightandyou.R
import com.jinwoo.twilightandyou.data.SettingsStore
import com.jinwoo.twilightandyou.data.LiveRepository
import kotlinx.coroutines.CancellationException
import com.jinwoo.twilightandyou.model.*

class TwilightWidget(
    private val previewSettings: WidgetSettings? = null,
    private val previewTime: java.time.ZonedDateTime? = null,
    private val previewData: LiveSnapshot? = null
) : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun onDelete(context: Context, glanceId: GlanceId) {
        SettingsStore(context).delete(GlanceAppWidgetManager(context).getAppWidgetId(glanceId))
        WidgetSchedule.reconcile(context)
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val widgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val settings = previewSettings ?: SettingsStore(context).read(widgetId)
        val now = previewTime ?: java.time.ZonedDateTime.now(SEOUL)
        val base = WidgetPresentation.from(settings, now)
        val model = if (settings.showSample) base else try {
            base.withLiveData(previewData ?: LiveRepository(context).cached(settings, now), settings, now)
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { base }
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            .setData(android.net.Uri.parse("twilight://widget/$widgetId"))
        provideContent {
            val size = LocalSize.current
            val shape = WidgetShape.forSize(size.width.value, size.height.value)
            val tint = Color(settings.palette.background).copy(alpha = settings.opacity / 100f)
            // Keep the launcher's cell; make only the compact card visibly smaller.
            val cardWidth = if (shape == WidgetShape.COMPACT) minOf(size.width.value, 88f) else size.width.value
            val cardHeight = if (shape == WidgetShape.COMPACT) minOf(size.height.value, 90f) else size.height.value
            Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = GlanceModifier.size(cardWidth.dp, cardHeight.dp)
                        .background(ImageProvider(R.drawable.widget_background), colorFilter = ColorFilter.tint(ColorProvider(tint)))
                        .appWidgetBackground()
                        .let { if (previewSettings == null) it.clickable(actionStartActivity(intent)) else it }
                        .padding(if (shape == WidgetShape.COMPACT) 4.dp else if (size.height.value < 96f) 2.dp else 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when (shape) {
                        WidgetShape.COMPACT -> CurrentContent(model,
                            settings.copy(fontScale = minOf(settings.fontScale, (cardWidth - 8f) / 80f)), false)
                        WidgetShape.WIDE -> WideContent(model, settings, size.width.value, size.height.value)
                        WidgetShape.TALL -> TallContent(model, settings, size.width.value)
                    }
                }
            }
        }
    }
}

@Composable
private fun Label(text: String, settings: WidgetSettings, size: Int = 11, muted: Boolean = false, bold: Boolean = false) {
    Text(
        text = text,
        style = TextStyle(
            color = ColorProvider(Color(if (muted) settings.palette.muted else settings.palette.foreground)),
            fontSize = (size * settings.fontScale).sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
        ),
        maxLines = 1
    )
}

@Composable
private fun Dust(label: String, value: Int?, grade: DustGrade, settings: WidgetSettings) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Label(label, settings, 10)
        Spacer(GlanceModifier.width(2.dp))
        Image(
            provider = ImageProvider(R.drawable.dust_dot),
            contentDescription = "${if (label == "미") "미세먼지" else "초미세먼지"} ${grade.title}, ${value?.let { "$it 마이크로그램 매 세제곱미터" } ?: "자료 없음"}",
            modifier = GlanceModifier.size((8 * settings.fontScale).dp),
            colorFilter = ColorFilter.tint(ColorProvider(Color(grade.argb)))
        )
    }
}

@Composable
private fun CurrentContent(model: WidgetPresentation, settings: WidgetSettings, showRegion: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
        if (showRegion) Label(settings.region.take(8), settings, 9, muted = true)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label(model.temperature, settings, 23, bold = true)
            Spacer(GlanceModifier.width(2.dp))
            Label(model.weather, settings, 19)
            if (model.weatherIsForecast) Label("예", settings, 6, muted = true)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dust("미", model.pm10, model.pm10Grade, settings)
            Spacer(GlanceModifier.width(5.dp))
            Dust("초미", model.pm25, model.pm25Grade, settings)
        }
        Spacer(GlanceModifier.height(3.dp))
        Label(model.nextLabel, settings, 9, bold = true)
        Spacer(GlanceModifier.height(1.dp))
        Label(model.previousLabel, settings, 8, muted = true)
    }
}

@Composable
private fun WideContent(model: WidgetPresentation, settings: WidgetSettings, width: Float, height: Float) {
    val short = height < 96f
    val narrow = width < 210f
    val leftWidth = if (narrow) 59f else 84f
    val currentSettings = settings.copy(fontScale = minOf(settings.fontScale, if (narrow || short) 0.70f else 1f))
    val forecastSettings = settings.copy(fontScale = minOf(settings.fontScale, if (narrow || short) 0.8f else 1f))
    Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Box(GlanceModifier.width(leftWidth.dp), contentAlignment = Alignment.Center) {
            CurrentContent(model, currentSettings, !short)
        }
        Spacer(GlanceModifier.width(5.dp))
        Box(GlanceModifier.width(1.dp).height(52.dp).background(ColorProvider(Color(settings.palette.muted).copy(alpha = 0.25f)))) {}
        Spacer(GlanceModifier.width(5.dp))
        Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Label("시간별 예보", forecastSettings, 8, muted = true)
            Spacer(GlanceModifier.height(4.dp))
            if (model.hourly.isEmpty()) {
                Label("미연결", forecastSettings, 10, muted = true)
            } else Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                model.hourly.take(if (width >= 210f) 4 else if (width >= 170f) 3 else 2).forEach { hour ->
                    Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Label(relativeDay(hour.at.toLocalDate(), model.today), forecastSettings, 7, muted = true)
                        Label(hour.hour, forecastSettings, 9, muted = true)
                        Label(hour.weather, forecastSettings, 17)
                        Label(hour.temperature, forecastSettings, 11, bold = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun TallContent(model: WidgetPresentation, settings: WidgetSettings, width: Float) {
    val fit = minOf(settings.fontScale, ((width - 12f) / 80f).coerceAtMost(1f))
    val fitted = settings.copy(fontScale = fit)
    Column(GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
        CurrentContent(model, fitted, true)
        Spacer(GlanceModifier.height(5.dp))
        Box(GlanceModifier.fillMaxWidth().height(1.dp).background(ColorProvider(Color(settings.palette.muted).copy(alpha = 0.25f)))) {}
        Spacer(GlanceModifier.height(5.dp))
        Label("시간별 예보", fitted, 8, muted = true)
        if (model.hourly.isEmpty()) {
            Label("미연결", fitted, 10, muted = true)
        } else Column(GlanceModifier.fillMaxWidth()) {
            model.hourly.take(4).forEach { hour ->
                Row(GlanceModifier.fillMaxWidth().height((22f * fit.coerceAtLeast(0.7f)).dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(GlanceModifier.defaultWeight()) {
                        if (width < 90f) {
                            Label(relativeDay(hour.at.toLocalDate(), model.today), fitted, 7, muted = true)
                            Label(hour.hour, fitted, 9, muted = true)
                        } else Label("${relativeDay(hour.at.toLocalDate(), model.today)} ${hour.hour}", fitted, 9, muted = true)
                    }
                    Label(hour.weather, fitted, 16)
                    Spacer(GlanceModifier.width(if (width < 90f) 3.dp else 5.dp))
                    Label(hour.temperature, fitted, 11, bold = true)
                }
            }
        }
    }
}

abstract class BaseWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TwilightWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetSchedule.reconcile(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetSchedule.reconcile(context)
    }
}

class CompactWidgetReceiver : BaseWidgetReceiver()
class WideWidgetReceiver : BaseWidgetReceiver()
class TallWidgetReceiver : BaseWidgetReceiver()
