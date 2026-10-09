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
        assertEquals(fixed, resolveAutomaticLocation(fixed, { error("Must not request location") }, { null }).settings)
        assertEquals(seoul, resolveAutomaticLocation(seoul, { null }, { error("No point") }).settings)
        assertEquals(seoul, resolveAutomaticLocation(seoul, { throw SecurityException() }, { null }).settings)
        assertEquals(seoul, resolveAutomaticLocation(seoul, { Coordinates(0.0, 0.0) }, { null }).settings)
    }
    @Test fun movingClearsOldStationWhileSmallLocationJitterPreservesIt(): Unit = runBlocking {
        val moved = resolveAutomaticLocation(seoul, { busan }, { LocationDescription("부산광역시 연제구", "부산") })
        assertTrue(moved.located)
        assertEquals(busan, moved.settings.point)
        assertEquals("부산", moved.settings.forecastArea)
        assertEquals("", moved.settings.station)
        assertTrue(moved.settings.autoStation)
        val same = resolveAutomaticLocation(seoul, { Coordinates(37.5666, 126.9781) }, { LocationDescription("서울", "서울") })
        assertEquals("종로구", same.settings.station)
    }
    @Test fun missingAddressDoesNotLeakPreviousAreasForecastAndCancellationPropagates(): Unit = runBlocking {
        val result = resolveAutomaticLocation(seoul, { busan }, { throw IllegalStateException() })
        assertEquals(busan, result.settings.point)
        assertEquals("", result.settings.station)
        assertEquals("", result.settings.forecastArea)
        try {
            resolveAutomaticLocation(seoul, { throw CancellationException() }, { null })
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
    @Test fun stationQueriesUseAddressNotStationNameAndSupportProvinceAliases() {
        assertEquals(listOf("서울"), stationSearchQueries(WidgetSettings()))
        assertEquals(listOf("수원시", "경기", "경기도"), stationSearchQueries(WidgetSettings(region = "경기도 수원시", airArea = "경기남부")))
        assertEquals(listOf("전남", "전라남도"), stationSearchQueries(WidgetSettings(region = "집", airArea = "전남", station = "사용자가 고른 이름")))
    }
}
