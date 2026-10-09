package com.jinwoo.twilightandyou.astronomy

import com.jinwoo.twilightandyou.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

class SolarCalculatorTest {
    @Test fun independentUsnoReferencesStayWithinTwoMinutes() {
        val lines = javaClass.getResourceAsStream("/astronomy/usno.csv")!!.bufferedReader().readLines().drop(1)
        assertEquals(54, lines.size)
        var maxSeconds = 0L
        for (line in lines) {
            val fields = line.split(',')
            val date = LocalDate.parse(fields[0])
            val events = SolarCalculator.events(date, Coordinates(fields[2].toDouble(), fields[3].toDouble()), fields[4].toDouble())
            for ((actual, time) in listOf(events.morning to fields[5], events.evening to fields[6])) {
                assertNotNull(line, actual)
                val expected = date.atTime(LocalTime.parse(time)).atZone(SEOUL)
                val seconds = kotlin.math.abs(Duration.between(expected, actual).seconds)
                maxSeconds = maxOf(maxSeconds, seconds)
                assertTrue("$line actual=$actual difference=${seconds}s", seconds <= 120)
            }
        }
        println("USNO: ${lines.size * 2} times, maximum difference ${maxSeconds}s")
    }

    @Test fun noCrossingDoesNotBecomeMidnight() {
        val result = SolarCalculator.events(LocalDate.of(2026,6,21), Coordinates(78.0,15.0), -12.0)
        assertNull(result.morning)
        assertNull(result.evening)
    }

    @Test fun exactCalculatedEventTransitionsWithoutRoundingFirst() {
        val point = Coordinates(37.5665,126.978)
        val evening = SolarCalculator.events(LocalDate.of(2026,10,8), point, -12.0).evening!!
        assertEquals(evening, SolarCalculator.pair(evening.minusSeconds(1), point, TwilightKind.NAUTICAL).next!!.at)
        val after = SolarCalculator.pair(evening, point, TwilightKind.NAUTICAL)
        assertEquals(evening, after.previous!!.at)
        assertEquals(evening.toLocalDate().plusDays(1), after.next!!.at.toLocalDate())
    }

    @Test fun longitudeAndDateAffectTheComputedTimes() {
        val now = ZonedDateTime.parse("2026-10-08T12:00:00+09:00")
        val seoul = SolarCalculator.pair(now, Regions.find("서울")!!.point, TwilightKind.NAUTICAL)
        val busan = SolarCalculator.pair(now, Regions.find("부산")!!.point, TwilightKind.NAUTICAL)
        assertNotEquals(seoul.next!!.at, busan.next!!.at)
        assertNotEquals(seoul.next!!.at.toLocalTime(), SolarCalculator.pair(now.plusMonths(3), Regions.find("서울")!!.point, TwilightKind.NAUTICAL).next!!.at.toLocalTime())
    }

    @Test fun officialKmaGridExamplesAndCoordinatesAreValidated() {
        assertEquals(WeatherGrid(60,127), Regions.find("서울")!!.point.weatherGrid())
        assertEquals(WeatherGrid(98,76), Regions.find("부산")!!.point.weatherGrid())
        assertThrows(IllegalArgumentException::class.java) { Coordinates(Double.NaN,127.0) }
        assertThrows(IllegalArgumentException::class.java) { Coordinates(127.0,37.0) }
    }
}
