package com.jinwoo.twilightandyou.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.jinwoo.twilightandyou.TwilightApplication
import com.jinwoo.twilightandyou.data.SettingsStore
import com.jinwoo.twilightandyou.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Persists the exact draft accepted by the launcher, even if the editor has since changed. */
class PinWidgetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (id !in widgetIds(context)) return
        val settings = WidgetSettings(
            region = intent.getStringExtra("region") ?: "서울",
            twilight = TwilightKind.entries.firstOrNull { it.name == intent.getStringExtra("twilight") } ?: TwilightKind.NAUTICAL,
            eventMode = EventMode.entries.firstOrNull { it.name == intent.getStringExtra("mode") } ?: EventMode.NEXT,
            palette = WidgetPalette.entries.firstOrNull { it.name == intent.getStringExtra("palette") } ?: WidgetPalette.DUSK,
            opacity = intent.getIntExtra("opacity", 100),
            fontScale = intent.getFloatExtra("font", 1f),
            showSample = intent.getBooleanExtra("sample", false),
            latitude = if (intent.hasExtra("latitude")) intent.getDoubleExtra("latitude", Double.NaN) else null,
            longitude = if (intent.hasExtra("longitude")) intent.getDoubleExtra("longitude", Double.NaN) else null,
            airArea = intent.getStringExtra("airArea"),
            station = intent.getStringExtra("station").orEmpty()
        )
        val pending = goAsync()
        (context.applicationContext as TwilightApplication).applicationScope.launch {
            try {
                SettingsStore(context).save(id, settings)
                TwilightWidget().update(context, GlanceAppWidgetManager(context).getGlanceIdBy(id))
                WidgetSchedule.reconcile(context)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { Log.e("TwilightWidget", "Unable to finish pin configuration", e) }
            finally { pending.finish() }
        }
    }

    companion object {
        fun intent(context: Context, settings: WidgetSettings) = Intent(context, PinWidgetReceiver::class.java)
            .putExtra("region", settings.region)
            .putExtra("twilight", settings.twilight.name)
            .putExtra("mode", settings.eventMode.name)
            .putExtra("palette", settings.palette.name)
            .putExtra("opacity", settings.opacity)
            .putExtra("font", settings.fontScale)
            .putExtra("sample", settings.showSample)
            .putExtra("airArea", settings.airArea)
            .putExtra("station", settings.station)
            .apply {
                settings.latitude?.let { putExtra("latitude", it) }
                settings.longitude?.let { putExtra("longitude", it) }
            }
    }
}
