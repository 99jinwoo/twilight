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
import com.jinwoo.twilightandyou.model.*

class TwilightWidget(private val previewSettings: WidgetSettings? = null) : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun onDelete(context: Context, glanceId: GlanceId) {
        SettingsStore(context).delete(GlanceAppWidgetManager(context).getAppWidgetId(glanceId))
        WidgetSchedule.reconcile(context)
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val widgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val settings = previewSettings ?: SettingsStore(context).read(widgetId)
        val model = WidgetPresentation.from(settings)
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            .setData(android.net.Uri.parse("twilight://widget/$widgetId"))
        provideContent {
            val size = LocalSize.current
            val shape = WidgetShape.forSize(size.width.value, size.height.value)
            val tint = Color(settings.palette.background).copy(alpha = settings.opacity / 100f)
            Box(
                modifier = GlanceModifier.fillMaxSize()
                    .background(ImageProvider(R.drawable.widget_background), colorFilter = ColorFilter.tint(ColorProvider(tint)))
                    .appWidgetBackground()
                    .let { if (previewSettings == null) it.clickable(actionStartActivity(intent)) else it }
                    .padding(6.dp),
                contentAlignment = Alignment.Center
            ) {
                when (shape) {
                    WidgetShape.COMPACT -> {
                        val fit = minOf(size.width.value / 96f, size.height.value / 112f).coerceIn(0.55f, 1f)
                        CompactContent(model, settings.copy(fontScale = minOf(settings.fontScale, fit * 1.15f)))
                    }
                    WidgetShape.WIDE -> WideContent(model, settings.copy(fontScale = minOf(settings.fontScale, size.width.value / 224f * 1.15f)), size.width.value >= 200f)
                    WidgetShape.TALL -> TallContent(model, settings.copy(fontScale = minOf(settings.fontScale, size.width.value / 112f * 1.15f)))
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
private fun Dust(label: String, value: Int?, grade: DustGrade, settings: WidgetSettings, expanded: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Label(label, settings, 10)
        Spacer(GlanceModifier.width(3.dp))
        Image(
            provider = ImageProvider(R.drawable.dust_dot),
            contentDescription = "$label ${grade.title}, ${value?.let { "$it 마이크로그램 매 세제곱미터" } ?: "자료 없음"}",
            modifier = GlanceModifier.size(9.dp),
            colorFilter = ColorFilter.tint(ColorProvider(Color(grade.argb)))
        )
        if (expanded) {
            Spacer(GlanceModifier.width(3.dp))
            Label(value?.toString() ?: "—", settings, 11)
        }
    }
}

@Composable
private fun DustRow(model: WidgetPresentation, settings: WidgetSettings, expanded: Boolean = false, spacing: Int = 8) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Dust("미", model.pm10, model.pm10Grade, settings, expanded)
        Spacer(GlanceModifier.width(spacing.dp))
        Dust("초", model.pm25, model.pm25Grade, settings, expanded)
    }
}

@Composable
private fun CompactContent(model: WidgetPresentation, settings: WidgetSettings) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label(model.temperature, settings, 23, bold = true)
            Spacer(GlanceModifier.width(2.dp))
            Label(model.weather, settings, 19)
        }
        Spacer(GlanceModifier.height(2.dp))
        DustRow(model, settings, spacing = 4)
        Spacer(GlanceModifier.height(3.dp))
        val shortName = when (model.eventName) {
            "시민 아침" -> "시민↑"; "시민 저녁" -> "시민↓"
            "천문 아침" -> "천문↑"; "천문 저녁" -> "천문↓"
            else -> model.eventName
        }
        Label("$shortName ${model.eventTime}", settings, 10, bold = true)
        Spacer(GlanceModifier.height(2.dp))
        Label(if (model.isSample) "샘플 17시" else "미연결", settings, 8, muted = true)
    }
}

@Composable
private fun WideContent(model: WidgetPresentation, settings: WidgetSettings, showConcentrations: Boolean) {
    Column(verticalAlignment = Alignment.CenterVertically) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Label(settings.region, settings, 9, muted = true)
                Label("${model.temperature} ${model.weather}", settings, 24, bold = true)
            }
            Column(horizontalAlignment = Alignment.End) {
                DustRow(model, settings, expanded = showConcentrations)
                Spacer(GlanceModifier.height(5.dp))
                Label(model.eventName, settings, 9, muted = true)
                Label(model.eventTime, settings, 16, bold = true)
            }
        }
        Spacer(GlanceModifier.height(3.dp))
        Label("${model.status} · 박명 ${model.eventDate}", settings, 8, muted = true)
    }
}

@Composable
private fun TallContent(model: WidgetPresentation, settings: WidgetSettings) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
        Label(settings.region, settings, 10, muted = true)
        Label("${model.temperature} ${model.weather}", settings, 25, bold = true)
        Spacer(GlanceModifier.height(8.dp))
        Dust("미", model.pm10, model.pm10Grade, settings, expanded = true)
        Spacer(GlanceModifier.height(4.dp))
        Dust("초", model.pm25, model.pm25Grade, settings, expanded = true)
        Spacer(GlanceModifier.height(10.dp))
        Label(model.eventName, settings, 11, muted = true)
        Label(model.eventTime, settings, 21, bold = true)
        Label("박명 ${model.eventDate}", settings, 9, muted = true)
        Spacer(GlanceModifier.height(8.dp))
        Label(model.status, settings, 9, muted = true)
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
