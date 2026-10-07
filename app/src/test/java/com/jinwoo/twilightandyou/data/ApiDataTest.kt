package com.jinwoo.twilightandyou.data

import com.jinwoo.twilightandyou.data.remote.*
import com.jinwoo.twilightandyou.model.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.ZonedDateTime

/** Synthetic wire responses based on the official 20260928/20260630 guides. No live API key. */
class ApiDataTest {
    private val now = ZonedDateTime.parse("2026-10-08T17:30:00+09:00")
    private fun row(value: String) = JSONObject(value)

    @Test fun supportsBothProviderEnvelopesAndSingletonItems() {
        val kma = ApiParsers.page("""{"response":{"header":{"resultCode":"00"},"body":{"items":{"item":[{"category":"T1H"}]},"totalCount":1}}}""")
        val air = ApiParsers.page("""{"response":{"header":{"resultCode":"00"},"body":{"items":[{"pm10Value":"42"}],"totalCount":1}}}""")
        assertEquals("T1H", kma.items.single().getString("category"))
        assertEquals("42", air.items.single().getString("pm10Value"))
        assertEquals(1, ApiParsers.page("""{"response":{"header":{"resultCode":"00"},"body":{"items":{"item":{"category":"T1H"}},"totalCount":1}}}""").items.size)
    }

    @Test fun authenticationAndQuotaFailuresNeverBecomeGoodAir() {
        val json = """{"response":{"header":{"resultCode":"22"}}}"""
        assertEquals(DataProblem.QUOTA, assertThrows(ApiFailure::class.java) { ApiParsers.page(json) }.problem)
        val xml = "<OpenAPI_ServiceResponse><returnReasonCode>30</returnReasonCode></OpenAPI_ServiceResponse>"
        assertEquals(DataProblem.AUTH, assertThrows(ApiFailure::class.java) { ApiParsers.page(xml) }.problem)
        assertEquals(DataProblem.FORMAT, assertThrows(ApiFailure::class.java) { ApiParsers.page("not json") }.problem)
    }

    @Test fun missingKmaSentinelsStayMissingAndLatestObservationWins() {
        val result = ApiParsers.observation(listOf(
            row("""{"baseDate":"20261008","baseTime":"1500","category":"T1H","obsrValue":"22"}"""),
            row("""{"baseDate":"20261008","baseTime":"1600","category":"T1H","obsrValue":"-999"}"""),
            row("""{"baseDate":"20261008","baseTime":"1600","category":"PTY","obsrValue":"0"}""")))!!
        assertNull(result.temperature)
        assertEquals(16, result.at.hour)
        assertEquals(0, result.precipitation)
    }

    @Test fun forecastsGroupByTargetTimeAndKeepMissingTemperatureSeparate() {
        val values = ApiParsers.forecast(listOf(
            row("""{"fcstDate":"20261008","fcstTime":"2400","category":"SKY","fcstValue":"4"}"""),
            row("""{"fcstDate":"20261008","fcstTime":"2400","category":"PTY","fcstValue":"1"}"""),
            row("""{"fcstDate":"20261008","fcstTime":"2300","category":"TMP","fcstValue":"19"}""")))
        assertEquals(2, values.size)
        assertEquals(19.0, values.first().temperature)
        assertEquals("2026-10-09", values.last().at.toLocalDate().toString())
        assertNull(values.last().temperature)
    }

    @Test fun airFlagsOverrideNumbersAndFutureRowsAreIgnored() {
        val values = listOf(
            row("""{"dataTime":"2026-10-08 17:00","pm10Value":"42","pm25Value":"12","pm25Flag":"통신장애","pm10Grade1h":"2"}"""),
            row("""{"dataTime":"2026-10-09 17:00","pm10Value":"1","pm25Value":"1"}"""))
        val result = ApiParsers.air(values, now)!!
        assertEquals(42, result.pm10)
        assertNull(result.pm25)
        assertEquals("통신장애", result.pm25Flag)
        assertEquals("2", result.reportedGrade10)
    }

    @Test fun airForecastUsesNewestIssueForEachPollutantAndTargetDay() {
        fun forecast(code: String, date: String, issued: String, grade: String) = row("""{"informCode":"$code","informData":"$date","dataTime":"$issued","informGrade":"서울 : $grade, 영동 : 보통","informOverall":"test"}""")
        val result = ApiParsers.airForecasts(listOf(
            forecast("PM10","2026-10-09","2026-10-08 05시 발표","좋음"),
            forecast("PM10","2026-10-09","2026-10-08 17시 발표","나쁨"),
            forecast("PM25","2026-10-09","2026-10-08 11시 발표","좋음"),
            forecast("PM10","2026-10-07","2026-10-07 17시 발표","좋음"),
            forecast("PM25","2026-10-09","2026-10-08 23시 발표","나쁨")), now)
        assertEquals(2, result.size)
        assertEquals("나쁨", result.single { it.pollutant == "PM10" }.grade("서울"))
        assertEquals("좋음", result.single { it.pollutant == "PM25" }.grade("서울"))
        assertEquals("보통", result.first().grade("강원영동"))
        assertNull(result.first().grade("다른 권역"))
    }

    @Test fun stationVersion11HasLongitudeXAndLatitudeY() {
        val correct = row("""{"stationName":"종로구","stationCode":"111123","dmX":"127.005028","dmY":"37.572025","item":"SO2, PM10, PM2.5"}""")
        val swapped = row("""{"stationName":"wrong","dmX":"37.572025","dmY":"127.005028","item":"PM10, PM2.5"}""")
        val result = ApiParsers.stations(listOf(correct, swapped))
        assertEquals(1, result.size)
        assertEquals(37.572025, result.single().point.latitude, 0.000001)
        assertEquals(127.005028, result.single().point.longitude, 0.000001)
    }

    @Test fun publicationBoundariesAndMidnightUseKoreanDate() {
        assertEquals("2026-10-07T23:00+09:00[Asia/Seoul]", observationBase(ZonedDateTime.parse("2026-10-08T00:09:59+09:00")).toString())
        assertEquals(0, observationBase(ZonedDateTime.parse("2026-10-08T00:10:00+09:00")).hour)
        assertEquals(23, forecastBase(ZonedDateTime.parse("2026-10-08T02:09:59+09:00")).hour)
        assertEquals(2, forecastBase(ZonedDateTime.parse("2026-10-08T02:10:00+09:00")).hour)
        assertEquals("2026-10-07", airForecastBase(ZonedDateTime.parse("2026-10-08T05:09:00+09:00")).toLocalDate().toString())
        assertEquals(5, airForecastBase(ZonedDateTime.parse("2026-10-08T05:10:00+09:00")).hour)
    }

    @Test fun encodedKeysDecodeOnceWithoutConvertingLiteralPlusToSpace() {
        assertEquals("abc+/=", normalizeServiceKey("abc%2B%2F%3D"))
        assertEquals("abc+/=", normalizeServiceKey(" abc+/= "))
    }

    @Test fun paginationIsCompleteAndDropsUnrequestedSecretFields() = runBlocking {
        var requests = 0
        val api = PublicWeatherApi { _, _, params ->
            requests++
            val page = params.getValue("pageNo")
            """{"response":{"header":{"resultCode":"00"},"body":{"items":[{"stationName":"$page","serviceKey":"do-not-persist"}],"totalCount":2}}}"""
        }
        val values = api.fetch(PublicWeatherApi.STATIONS, "synthetic-test-key", emptyMap())
        assertEquals(2, requests)
        assertEquals(listOf("1", "2"), values.map { it.getString("stationName") })
        assertTrue(values.none { it.has("serviceKey") })
    }

    @Test fun emptyIncompletePagesFailInsteadOfSavingPartialResults() = runBlocking {
        val api = PublicWeatherApi { _, _, _ -> """{"response":{"header":{"resultCode":"00"},"body":{"items":[],"totalCount":10}}}""" }
        try { api.fetch(PublicWeatherApi.STATIONS, "test", emptyMap()); fail("Expected format error") }
        catch (e: ApiFailure) { assertEquals(DataProblem.FORMAT, e.problem) }
    }
}
