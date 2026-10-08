package com.jinwoo.twilightandyou.model

import com.jinwoo.twilightandyou.astronomy.Coordinates
import com.jinwoo.twilightandyou.astronomy.SolarCalculator
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")

enum class TwilightKind(val title: String, val morning: String, val evening: String) {
    CIVIL("시민 −6°", "시민 아침", "시민 저녁"),
    NAUTICAL("항해 −12°", "BMNT", "EENT"),
    ASTRONOMICAL("천문 −18°", "천문 아침", "천문 저녁")
}

enum class EventMode(val title: String) {
    NEXT("다음 박명"), MORNING("아침 고정"), EVENING("저녁 고정")
}

enum class WidgetPalette(val title: String, val background: Long, val foreground: Long, val muted: Long) {
    DUSK("저녁", 0xFF242434, 0xFFF8F1EA, 0xFFBDB8CA),
    DAWN("아침", 0xFFF5EDE4, 0xFF29232F, 0xFF64596E),
    MIDNIGHT("밤", 0xFF111822, 0xFFE5EFF8, 0xFFA5B6C9)
}

enum class DustGrade(val title: String, val argb: Long) {
    GOOD("좋음", 0xFF60B9FF), NORMAL("보통", 0xFF69CEAA),
    BAD("나쁨", 0xFFFFAD66), VERY_BAD("매우 나쁨", 0xFFFF6F82),
    MISSING("자료 없음", 0xFF888694);

    companion object {
        fun fromConcentration(value: Int?, fine: Boolean): DustGrade {
            if (value == null || value < 0) return MISSING
            val bounds = if (fine) listOf(15, 35, 75) else listOf(30, 80, 150)
            return when {
                value <= bounds[0] -> GOOD
                value <= bounds[1] -> NORMAL
                value <= bounds[2] -> BAD
                else -> VERY_BAD
            }
        }
    }
}

data class WidgetSettings(
    val region: String = "서울",
    val twilight: TwilightKind = TwilightKind.NAUTICAL,
    val eventMode: EventMode = EventMode.NEXT,
    val palette: WidgetPalette = WidgetPalette.DUSK,
    val opacity: Int = 100,
    val fontScale: Float = 1f,
    val showSample: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val airArea: String? = null,
    val station: String = "",
    val autoLocation: Boolean = false
) {
    val point: Coordinates? get() {
        val lat = latitude
        val lon = longitude
        return if (lat != null && lon != null) runCatching { Coordinates(lat, lon) }.getOrNull()
            else Regions.find(region)?.point
    }
    val forecastArea: String get() = airArea ?: Regions.find(region)?.airArea.orEmpty()
}

enum class WidgetShape(val title: String, val width: Int, val height: Int) {
    COMPACT("1×1", 100, 112), WIDE("가로 2×1", 224, 112), TALL("세로 1×2", 112, 228);

    companion object {
        fun forSize(width: Float, height: Float): WidgetShape = when {
            width >= 130f && width >= height * 1.25f -> WIDE
            height >= 150f -> TALL
            else -> COMPACT
        }
    }
}

data class TwilightEvent(val name: String, val at: ZonedDateTime) {
    fun labelOn(today: LocalDate): String {
        val local = at.withZoneSameInstant(SEOUL).plusSeconds(30).truncatedTo(ChronoUnit.MINUTES)
        return "${relativeDay(local.toLocalDate(), today)} $name ${local.format(DateTimeFormatter.ofPattern("HH:mm"))}"
    }
}

data class TwilightPair(val previous: TwilightEvent?, val next: TwilightEvent?) {
    companion object {
        fun around(events: List<TwilightEvent>, now: ZonedDateTime) = TwilightPair(
            previous = events.filter { !it.at.isAfter(now) }.maxByOrNull { it.at.toInstant() },
            next = events.filter { it.at.isAfter(now) }.minByOrNull { it.at.toInstant() }
        )
    }
}

fun relativeDay(date: LocalDate, today: LocalDate): String = when (ChronoUnit.DAYS.between(today, date)) {
    -1L -> "어제"
    0L -> "오늘"
    1L -> "내일"
    else -> date.format(DateTimeFormatter.ofPattern("MM/dd"))
}

data class HourlyWeather(val at: ZonedDateTime, val weather: String, val temperature: String) {
    val hour: String get() = at.format(DateTimeFormatter.ofPattern("HH시"))
}

data class WidgetPresentation(
    val temperature: String,
    val weather: String,
    val pm10: Int?,
    val pm25: Int?,
    val today: LocalDate,
    val twilight: TwilightPair,
    val hourly: List<HourlyWeather>,
    val isSample: Boolean,
    val weatherIsForecast: Boolean = false
) {
    val pm10Grade get() = DustGrade.fromConcentration(pm10, false)
    val pm25Grade get() = DustGrade.fromConcentration(pm25, true)
    val nextLabel get() = twilight.next?.labelOn(today) ?: "다음 박명 —:—"
    val previousLabel get() = twilight.previous?.labelOn(today) ?: "직전 박명 —:—"

    companion object {
        fun from(settings: WidgetSettings, now: ZonedDateTime = ZonedDateTime.now(SEOUL)): WidgetPresentation {
            val localNow = now.withZoneSameInstant(SEOUL)
            val today = localNow.toLocalDate()
            if (!settings.showSample) return WidgetPresentation(
                "—°", "❔", null, null, today, settings.point?.let { SolarCalculator.pair(localNow, it, settings.twilight) } ?: TwilightPair(null, null), emptyList(), false
            )
            // Layout fixtures only: these times are not astronomical calculations.
            val (morning, evening) = when (settings.twilight) {
                TwilightKind.CIVIL -> "06:08" to "18:24"
                TwilightKind.NAUTICAL -> "05:38" to "18:54"
                TwilightKind.ASTRONOMICAL -> "05:08" to "19:24"
            }
            val morningName = when (settings.twilight) {
                TwilightKind.CIVIL -> "시민↑"
                TwilightKind.NAUTICAL -> "BMNT"
                TwilightKind.ASTRONOMICAL -> "천문↑"
            }
            val eveningName = when (settings.twilight) {
                TwilightKind.CIVIL -> "시민↓"
                TwilightKind.NAUTICAL -> "EENT"
                TwilightKind.ASTRONOMICAL -> "천문↓"
            }
            val events = (-1L..1L).flatMap { offset ->
                val day = today.plusDays(offset)
                listOf(
                    TwilightEvent(morningName, day.atTime(LocalTime.parse(morning)).atZone(SEOUL)),
                    TwilightEvent(eveningName, day.atTime(LocalTime.parse(evening)).atZone(SEOUL))
                )
            }
            val firstHour = localNow.truncatedTo(ChronoUnit.HOURS).plusHours(1)
            val weather = listOf("🌤️", "☁️", "☁️", "🌧️")
            val temperatures = listOf("21°", "20°", "19°", "18°")
            val hourly = (0..3).map { HourlyWeather(firstHour.plusHours(it.toLong()), weather[it], temperatures[it]) }
            return WidgetPresentation("22°", "☀️", 42, 12, today, TwilightPair.around(events, localNow), hourly, true)
        }
    }
}
