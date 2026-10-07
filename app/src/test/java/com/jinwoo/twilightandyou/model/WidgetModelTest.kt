package com.jinwoo.twilightandyou.model

import org.junit.Assert.*
import org.junit.Test
import java.time.ZonedDateTime

class WidgetModelTest {
    @Test fun coarseDustBoundaryValues() {
        val cases = mapOf(0 to DustGrade.GOOD, 30 to DustGrade.GOOD,
            31 to DustGrade.NORMAL, 80 to DustGrade.NORMAL, 81 to DustGrade.BAD,
            150 to DustGrade.BAD, 151 to DustGrade.VERY_BAD)
        cases.forEach { (value, expected) -> assertEquals("PM10 $value", expected, DustGrade.fromConcentration(value, false)) }
    }
    @Test fun fineDustBoundaryValues() {
        val cases = mapOf(0 to DustGrade.GOOD, 15 to DustGrade.GOOD,
            16 to DustGrade.NORMAL, 35 to DustGrade.NORMAL, 36 to DustGrade.BAD,
            75 to DustGrade.BAD, 76 to DustGrade.VERY_BAD)
        cases.forEach { (value, expected) -> assertEquals("PM2.5 $value", expected, DustGrade.fromConcentration(value, true)) }
    }
    @Test fun missingOrNegativeIsNeverGoodAir() {
        listOf(false, true).forEach { fine ->
            assertEquals(DustGrade.MISSING, DustGrade.fromConcentration(null, fine))
            assertEquals(DustGrade.MISSING, DustGrade.fromConcentration(-1, fine))
        }
    }
    @Test fun unconnectedWeatherStaysMissingWhileTwilightIsCalculated() {
        for (kind in TwilightKind.entries) for (mode in EventMode.entries) {
            val model = WidgetPresentation.from(WidgetSettings(twilight = kind, eventMode = mode))
            assertFalse(model.isSample)
            assertEquals("—°", model.temperature)
            assertNull(model.pm10)
            assertNull(model.pm25)
            assertEquals(DustGrade.MISSING, model.pm10Grade)
            assertNotNull(model.twilight.next)
            assertNotNull(model.twilight.previous)
            assertTrue(model.hourly.isEmpty())
        }
    }
    @Test fun sampleOptInKeepsDustGradesIndependent() {
        val model = WidgetPresentation.from(WidgetSettings(showSample = true))
        assertTrue(model.isSample)
        assertEquals(DustGrade.NORMAL, model.pm10Grade)
        assertEquals(DustGrade.GOOD, model.pm25Grade)
    }
    private fun sample(at: String, kind: TwilightKind = TwilightKind.NAUTICAL) = WidgetPresentation.from(
        WidgetSettings(twilight = kind, showSample = true), ZonedDateTime.parse(at)
    )

    @Test fun beforeDawnShowsYesterdayEveningAndTodayMorning() {
        val model = sample("2026-10-07T01:00:00+09:00")
        assertEquals("어제 EENT 18:54", model.previousLabel)
        assertEquals("오늘 BMNT 05:38", model.nextLabel)
    }

    @Test fun daytimeShowsTodayMorningAndEvening() {
        val model = sample("2026-10-07T17:00:00+09:00")
        assertEquals("오늘 BMNT 05:38", model.previousLabel)
        assertEquals("오늘 EENT 18:54", model.nextLabel)
    }

    @Test fun exactEventBecomesPreviousAndNextCrossesYear() {
        val model = sample("2026-12-31T18:54:00+09:00")
        assertEquals("오늘 EENT 18:54", model.previousLabel)
        assertEquals("내일 BMNT 05:38", model.nextLabel)
        assertEquals("2027-01-01", model.twilight.next!!.at.toLocalDate().toString())
        val before = sample("2026-12-31T18:53:59+09:00")
        assertEquals("오늘 EENT 18:54", before.nextLabel)
    }

    @Test fun midnightUsesRegionTimezoneEvenWhenDeviceClockIsUtc() {
        val model = sample("2026-10-06T15:00:00Z")
        assertEquals("2026-10-07", model.today.toString())
        assertEquals("어제 EENT 18:54", model.previousLabel)
        assertEquals("오늘 BMNT 05:38", model.nextLabel)
    }

    @Test fun allTwilightKindsSelectBothSidesWithoutInventingMissingEvents() {
        for (kind in TwilightKind.entries) {
            val model = sample("2028-02-29T12:00:00+09:00", kind)
            assertNotNull(model.twilight.previous)
            assertNotNull(model.twilight.next)
            assertTrue(model.twilight.previous!!.at.isBefore(model.twilight.next!!.at))
        }
        val now = ZonedDateTime.parse("2026-10-07T17:00:00+09:00")
        assertEquals(TwilightPair(null, null), TwilightPair.around(emptyList(), now))
        val previous = TwilightEvent("BMNT", now.minusHours(1))
        assertEquals(TwilightPair(previous, null), TwilightPair.around(listOf(previous), now))
        val next = TwilightEvent("EENT", now.plusHours(1))
        assertEquals(TwilightPair(previous, next), TwilightPair.around(listOf(next, previous), now))
    }

    @Test fun hourlyForecastStartsInFutureAndRetainsNextDayDate() {
        val model = sample("2026-12-31T22:45:00+09:00")
        assertEquals(listOf("23시", "00시", "01시", "02시"), model.hourly.map { it.hour })
        assertEquals(listOf("오늘", "내일", "내일", "내일"), model.hourly.map { relativeDay(it.at.toLocalDate(), model.today) })
        assertTrue(model.hourly.all { it.temperature.endsWith("°") && it.weather.isNotBlank() })
    }

    @Test fun resizingChoosesTheLayoutThatFits() {
        assertEquals(WidgetShape.COMPACT, WidgetShape.forSize(56f, 72f))
        assertEquals(WidgetShape.COMPACT, WidgetShape.forSize(100f, 112f))
        assertEquals(WidgetShape.WIDE, WidgetShape.forSize(224f, 112f))
        assertEquals(WidgetShape.TALL, WidgetShape.forSize(112f, 228f))
        assertEquals(WidgetShape.TALL, WidgetShape.forSize(180f, 240f))
    }
}
