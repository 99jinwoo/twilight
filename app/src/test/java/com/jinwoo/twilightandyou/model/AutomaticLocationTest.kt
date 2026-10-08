package com.jinwoo.twilightandyou.model

import com.jinwoo.twilightandyou.astronomy.Coordinates
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AutomaticLocationTest {
    private val seoul = WidgetSettings(station = "종로구", autoLocation = true)
    private val busan = Coordinates(35.1796, 129.0756)
    @Test fun fixedAndUnavailableLocationsPreserveAllExistingFields(): Unit = runBlocking {
        val fixed = seoul.copy(autoLocation = false)
        assertEquals(fixed, resolveAutomaticLocation(fixed, { error("Must not request location") }, { null }, { emptyList() }).settings)
        assertEquals(seoul, resolveAutomaticLocation(seoul, { null }, { error("No point") }, { error("No point") }).settings)
        assertEquals(seoul, resolveAutomaticLocation(seoul, { throw SecurityException() }, { null }, { emptyList() }).settings)
        assertEquals(seoul, resolveAutomaticLocation(seoul, { Coordinates(0.0, 0.0) }, { null }, { emptyList() }).settings)
    }
    @Test fun movingUpdatesWeatherTwilightForecastAreaAndNearestStationTogether(): Unit = runBlocking {
        val result = resolveAutomaticLocation(seoul, { busan }, { LocationDescription("부산광역시 연제구", "부산") }, {
            assertEquals(busan, it.point)
            assertEquals("", it.station)
            listOf(AirStation("멀리", "", seoul.point!!, "", "PM10, PM2.5"), AirStation("가까이", "", busan, "", "PM10, PM2.5"))
        })
        assertTrue(result.located)
        assertEquals(busan, result.settings.point)
        assertEquals("부산", result.settings.forecastArea)
        assertEquals("가까이", result.settings.station)
        assertEquals(seoul.twilight, result.settings.twilight)
        assertTrue(result.settings.autoLocation)
    }
    @Test fun partialResolutionDoesNotLeakPreviousAreasDustAndCancellationPropagates(): Unit = runBlocking {
        val result = resolveAutomaticLocation(seoul, { busan }, { throw IllegalStateException() }, { throw IllegalStateException() })
        assertEquals(busan, result.settings.point)
        assertEquals("", result.settings.station)
        assertEquals("", result.settings.forecastArea)
        try {
            resolveAutomaticLocation(seoul, { throw CancellationException() }, { null }, { emptyList() })
            fail("Cancellation must propagate when app leaves foreground")
        } catch (_: CancellationException) { }
    }
    @Test fun forecastAreasFollowOfficialCityListsNotLatitudeOrNameGuessing() {
        assertEquals("경기북부", airAreaForAddress("경기도", "김포시"))
        assertEquals("경기남부", airAreaForAddress("경기도", "광주시"))
        assertEquals("광주", airAreaForAddress("광주광역시", "북구"))
        assertEquals("강원영동", airAreaForAddress("강원특별자치도", "태백시"))
        assertEquals("강원영서", airAreaForAddress("강원도", "인제군"))
        assertEquals("충남", airAreaForAddress("충청남도", "논산시"))
        assertEquals("", airAreaForAddress("경기도", ""))
        assertEquals("", airAreaForAddress("unknown", "서울"))
    }
}
