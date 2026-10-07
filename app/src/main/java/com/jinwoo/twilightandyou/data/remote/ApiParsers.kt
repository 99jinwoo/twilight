package com.jinwoo.twilightandyou.data.remote

import com.jinwoo.twilightandyou.astronomy.Coordinates
import com.jinwoo.twilightandyou.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class ApiFailure(val problem: DataProblem) : Exception(problem.title)
data class ApiPage(val items: List<JSONObject>, val total: Int)

object ApiParsers {
    fun page(body: String): ApiPage {
        // Gateways may return XML even for JSON requests. Never surface the raw body or key.
        if (body.trimStart().startsWith("<")) {
            val code = Regex("<(?:returnReasonCode|resultCode)>([^<]+)</").find(body)?.groupValues?.get(1)
            throw ApiFailure(problem(code))
        }
        try {
            val root = JSONObject(body).getJSONObject("response")
            val code = root.getJSONObject("header").getString("resultCode")
            if (code !in setOf("00", "0", "0000")) throw ApiFailure(problem(code))
            val data = root.getJSONObject("body")
            val container = data.opt("items")
            val items = if (container is JSONObject && container.has("item")) container.opt("item") else container
            val list = when (items) {
                is JSONArray -> (0 until items.length()).map { items.getJSONObject(it) }
                is JSONObject -> listOf(items)
                null, JSONObject.NULL, "" -> emptyList()
                else -> throw ApiFailure(DataProblem.FORMAT)
            }
            return ApiPage(list, data.optInt("totalCount", list.size))
        } catch (e: ApiFailure) { throw e }
        catch (_: Exception) { throw ApiFailure(DataProblem.FORMAT) }
    }

    private fun problem(code: String?) = when (code?.trim()?.trimStart('0')) {
        "20", "30", "31", "SERVICE_KEY_IS_NOT_REGISTERED_ERROR" -> DataProblem.AUTH
        "22", "23" -> DataProblem.QUOTA
        "3" -> DataProblem.NO_DATA
        else -> DataProblem.FORMAT
    }

    fun observation(items: List<JSONObject>): WeatherObservation? {
        val dated = items.mapNotNull { row -> dateTime(row.text("baseDate"), row.text("baseTime"))?.let { it to row } }
        val time = dated.maxOfOrNull { it.first } ?: return null
        val values = dated.filter { it.first == time }.associate { it.second.text("category") to number(it.second.text("obsrValue")) }
        return WeatherObservation(time, values["T1H"], values["PTY"]?.toInt()?.takeIf { it in 0..7 })
    }

    fun forecast(items: List<JSONObject>): List<WeatherForecast> = items.mapNotNull { row ->
        dateTime(row.text("fcstDate"), row.text("fcstTime"))?.let { it to row }
    }.groupBy({ it.first }, { it.second }).map { (at, rows) ->
        val values = rows.associate { it.text("category") to number(it.text("fcstValue")) }
        WeatherForecast(at, values["TMP"] ?: values["T1H"], values["SKY"]?.toInt(), values["PTY"]?.toInt())
    }.sortedBy { it.at }

    fun air(items: List<JSONObject>, now: ZonedDateTime): AirObservation? = items.mapNotNull { row ->
        val at = runCatching { LocalDateTime.parse(row.text("dataTime"), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")).atZone(SEOUL) }.getOrNull()
            ?: return@mapNotNull null
        if (at.isAfter(now.plusMinutes(10))) return@mapNotNull null
        val flag10 = row.text("pm10Flag")
        val flag25 = row.text("pm25Flag")
        AirObservation(at,
            if (flag10.isBlank()) concentration(row.text("pm10Value")) else null,
            if (flag25.isBlank()) concentration(row.text("pm25Value")) else null,
            flag10, flag25, row.text("pm10Grade1h"), row.text("pm25Grade1h"))
    }.maxByOrNull { it.at }

    fun airForecasts(items: List<JSONObject>, now: ZonedDateTime): List<AirForecast> = items.mapNotNull { row ->
        val pollutant = row.text("informCode")
        if (pollutant !in setOf("PM10", "PM25")) return@mapNotNull null
        val date = runCatching { LocalDate.parse(row.text("informData")) }.getOrNull() ?: return@mapNotNull null
        val issuedText = Regex("(\\d{4}-\\d{2}-\\d{2})\\s+(\\d{1,2})시").find(row.text("dataTime")) ?: return@mapNotNull null
        val issued = runCatching { LocalDate.parse(issuedText.groupValues[1]).atTime(issuedText.groupValues[2].toInt(), 0).atZone(SEOUL) }.getOrNull() ?: return@mapNotNull null
        if (issued.isAfter(now) || date.isBefore(now.withZoneSameInstant(SEOUL).toLocalDate())) return@mapNotNull null
        val grades = row.text("informGrade").split(',').mapNotNull {
            val fields = it.split(':', limit = 2).map(String::trim)
            if (fields.size == 2 && fields[1] in setOf("좋음", "보통", "나쁨", "매우나쁨", "매우 나쁨")) fields[0] to fields[1] else null
        }.toMap()
        AirForecast(date, issued, pollutant, grades, row.text("informOverall"))
    }.groupBy { it.date to it.pollutant }.mapNotNull { (_, records) -> records.maxByOrNull { it.issuedAt } }.sortedBy { it.date }

    fun stations(items: List<JSONObject>): List<AirStation> = items.mapNotNull { row ->
        // Request ver=1.1: dmX is longitude, dmY is latitude. Earlier versions swap them.
        val lat = row.text("dmY").toDoubleOrNull() ?: return@mapNotNull null
        val lon = row.text("dmX").toDoubleOrNull() ?: return@mapNotNull null
        if (lat !in 32.0..40.0 || lon !in 123.0..133.0) return@mapNotNull null
        val name = row.text("stationName").takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val itemsText = row.text("item")
        if (!itemsText.contains("PM10") || !(itemsText.contains("PM2.5") || itemsText.contains("PM25"))) return@mapNotNull null
        AirStation(name, row.text("stationCode"), Coordinates(lat, lon), row.text("addr"), itemsText)
    }.distinctBy { it.code.ifBlank { it.name } }

    fun dateTime(date: String, time: String): ZonedDateTime? = runCatching {
        val day = LocalDate.parse(date, DateTimeFormatter.BASIC_ISO_DATE)
        if (time == "2400") day.plusDays(1).atStartOfDay(SEOUL)
        else day.atTime(LocalTime.parse(time, DateTimeFormatter.ofPattern("HHmm"))).atZone(SEOUL)
    }.getOrNull()

    private fun number(value: String) = value.toDoubleOrNull()?.takeIf { it.isFinite() && it > -900 && it < 900 }
    private fun concentration(value: String) = value.toIntOrNull()?.takeIf { it >= 0 }
}

internal fun JSONObject.text(name: String): String = if (isNull(name)) "" else optString(name, "").trim()
