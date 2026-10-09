package com.jinwoo.twilightandyou.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.*
import java.util.concurrent.TimeUnit
import com.jinwoo.twilightandyou.data.SettingsStore
import com.jinwoo.twilightandyou.data.LiveRepository

fun widgetIds(context: Context): List<Int> {
    val manager = AppWidgetManager.getInstance(context)
    return listOf(CompactWidgetReceiver::class.java, WideWidgetReceiver::class.java, TallWidgetReceiver::class.java)
        .flatMap { manager.getAppWidgetIds(ComponentName(context, it)).toList() }
}

object WidgetSchedule {
    fun reconcile(context: Context) {
        val manager = WorkManager.getInstance(context)
        if (widgetIds(context).isEmpty()) {
            manager.cancelUniqueWork("widget-render")
        } else {
            manager.enqueueUniquePeriodicWork(
                "widget-render", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<WidgetRefreshWorker>(1, TimeUnit.HOURS).build()
            )
        }
    }
}

class WidgetRefreshWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        if (widgetIds(applicationContext).isEmpty()) return Result.success()
        return try {
            TwilightWidget().updateAll(applicationContext)
            val settings = widgetIds(applicationContext).map { SettingsStore(applicationContext).read(it) }
                .filter { !it.showSample }
                .distinctBy { listOf(it.point, it.station, it.forecastArea, it.autoStation) }
            for (value in settings) {
                val repository = LiveRepository(applicationContext)
                val selected = repository.automaticStation(value).settings
                SettingsStore(applicationContext).updateAutomaticStations(widgetIds(applicationContext) + 0, selected)
                repository.refresh(selected)
            }
            TwilightWidget().updateAll(applicationContext)
            Result.success()
        } catch (e: java.util.concurrent.CancellationException) {
            throw e
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
