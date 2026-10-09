package com.jinwoo.twilightandyou.model

import org.junit.Assert.*
import org.junit.Test
import java.time.ZonedDateTime

class LivePresentationTest {
    private val now = ZonedDateTime.parse("2026-10-08T17:30:00+09:00")
    private val settings = WidgetSettings()
    private fun base() = WidgetPresentation.from(settings, now)

    @Test fun forecastNeverSubstitutesForMissingObservedTemperatureOrDust() {
        val snapshot = LiveSnapshot(forecast = listOf(WeatherForecast(now.plusMinutes(30), 22.0, 1, 0)), forecastIssuedAt = now.minusHours(1))
        val result = base().withLiveData(snapshot, settings, now)
        assertEquals("—°", result.temperature)
        assertEquals("❔", result.weather)
        assertNull(result.pm10)
        assertEquals("22°", result.hourly.single().temperature)
        assertNotNull(result.twilight.next)
    }

    @Test fun oldObservationsBecomeEmptyOnWidgetWhileSnapshotRetainsProvenance() {
        val snapshot = LiveSnapshot(weather = WeatherObservation(now.minusHours(3), 22.0, 0), air = AirObservation(now.minusHours(3), 42, 12,"","","2","1"))
        val result = base().withLiveData(snapshot, settings, now)
        assertEquals("—°", result.temperature)
        assertNull(result.pm10)
        assertEquals(22.0, snapshot.weather!!.temperature)
        assertTrue(isStale(snapshot.weather!!.at, now))
    }

    @Test fun forecastSkyIsTaggedAndObservedRainTakesPrecedence() {
        val snapshot = LiveSnapshot(weather = WeatherObservation(now.minusMinutes(30), 22.0, 0),
            forecast = listOf(WeatherForecast(now.plusMinutes(30), 23.0, 1, 0)), forecastIssuedAt = now.minusHours(1))
        val clear = base().withLiveData(snapshot, settings, now)
        assertTrue(clear.weatherIsForecast)
        val rainy = base().withLiveData(snapshot.copy(weather = snapshot.weather!!.copy(precipitation = 1)), settings, now)
        assertEquals("🌧️", rainy.weather)
        assertFalse(rainy.weatherIsForecast)
        assertEquals("❔", weatherEmoji(0, null, false))
    }

    @Test fun midnightForecastDatesAndStaleForecastsAreNotReused() {
        val future = WeatherForecast(now.plusHours(8), 15.0, 4, 0)
        val snapshot = LiveSnapshot(forecast = listOf(future), forecastIssuedAt = now.minusHours(25))
        assertTrue(base().withLiveData(snapshot, settings, now).hourly.isEmpty())
        val fresh = base().withLiveData(snapshot.copy(forecastIssuedAt = now.minusHours(2)), settings, now).hourly.single()
        assertEquals("내일", relativeDay(fresh.at.toLocalDate(), now.toLocalDate()))
    }

    @Test fun sampleModeRemainsEntirelySampleEvenWhenRealValuesExist() {
        val sample = WidgetPresentation.from(settings.copy(showSample = true), now)
        assertEquals(sample, sample.withLiveData(LiveSnapshot(weather = WeatherObservation(now, 31.0, 1)), settings, now))
    }
}
