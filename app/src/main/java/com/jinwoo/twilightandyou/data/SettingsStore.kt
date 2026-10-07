package com.jinwoo.twilightandyou.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.jinwoo.twilightandyou.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.widgetDataStore by preferencesDataStore(name = "widget_settings")

class SettingsStore(context: Context) {
    private val dataStore = context.applicationContext.widgetDataStore

    fun observe(id: Int): Flow<WidgetSettings> = dataStore.data.map { p ->
        val prefix = "widget_${id}_"
        WidgetSettings(
            region = p[stringPreferencesKey(prefix + "region")] ?: "서울",
            twilight = enumValueOrDefault(p[stringPreferencesKey(prefix + "twilight")], TwilightKind.NAUTICAL),
            eventMode = enumValueOrDefault(p[stringPreferencesKey(prefix + "mode")], EventMode.NEXT),
            palette = enumValueOrDefault(p[stringPreferencesKey(prefix + "palette")], WidgetPalette.DUSK),
            opacity = (p[intPreferencesKey(prefix + "opacity")] ?: 100).coerceIn(25, 100),
            fontScale = (p[floatPreferencesKey(prefix + "font")] ?: 1f).coerceIn(0.85f, 1.2f),
            showSample = p[booleanPreferencesKey(prefix + "sample")] ?: false,
            latitude = p[doublePreferencesKey(prefix + "latitude")],
            longitude = p[doublePreferencesKey(prefix + "longitude")],
            airArea = p[stringPreferencesKey(prefix + "airArea")],
            station = p[stringPreferencesKey(prefix + "station")] ?: ""
        )
    }

    suspend fun read(id: Int) = observe(id).first()

    suspend fun isConfigured(id: Int): Boolean =
        dataStore.data.first().contains(stringPreferencesKey("widget_${id}_region"))

    suspend fun save(id: Int, settings: WidgetSettings) {
        val prefix = "widget_${id}_"
        dataStore.edit { p ->
            p[stringPreferencesKey(prefix + "region")] = settings.region
            p[stringPreferencesKey(prefix + "twilight")] = settings.twilight.name
            p[stringPreferencesKey(prefix + "mode")] = settings.eventMode.name
            p[stringPreferencesKey(prefix + "palette")] = settings.palette.name
            p[intPreferencesKey(prefix + "opacity")] = settings.opacity.coerceIn(25, 100)
            p[floatPreferencesKey(prefix + "font")] = settings.fontScale.coerceIn(0.85f, 1.2f)
            p[booleanPreferencesKey(prefix + "sample")] = settings.showSample
            val lat = doublePreferencesKey(prefix + "latitude")
            val lon = doublePreferencesKey(prefix + "longitude")
            val area = stringPreferencesKey(prefix + "airArea")
            if (settings.latitude != null && settings.longitude != null && settings.point != null) {
                p[lat] = settings.latitude; p[lon] = settings.longitude
            } else { p.remove(lat); p.remove(lon) }
            if (settings.airArea == null) p.remove(area) else p[area] = settings.airArea
            p[stringPreferencesKey(prefix + "station")] = settings.station.trim().take(60)
        }
    }

    suspend fun delete(id: Int) {
        val prefix = "widget_${id}_"
        dataStore.edit { p ->
            p.asMap().keys.filter { it.name.startsWith(prefix) }.forEach { p.remove(it) }
        }
    }
}

private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String?, default: T): T =
    enumValues<T>().firstOrNull { it.name == value } ?: default
