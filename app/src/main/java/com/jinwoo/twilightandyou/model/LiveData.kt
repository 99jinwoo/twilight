package com.jinwoo.twilightandyou.model

import com.jinwoo.twilightandyou.astronomy.Coordinates
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlin.math.roundToInt

data class WeatherObservation(val at: ZonedDateTime, val temperature: Double?, val precipitation: Int?)
data class WeatherForecast(val at: ZonedDateTime, val temperature: Double?, val sky: Int?, val precipitation: Int?)
data class AirObservation(val at: ZonedDateTime, val pm10: Int?, val pm25: Int?, val pm10Flag: String, val pm25Flag: String,
    val reportedGrade10: String, val reportedGrade25: String)
data class AirForecast(val date: LocalDate, val issuedAt: ZonedDateTime, val pollutant: String, val grades: Map<String, String>, val summary: String) {
    fun grade(area: String): String? = grades[when (area) { "강원영동" -> "영동"; "강원영서" -> "영서"; else -> area }]
}
data class AirStation(val name: String, val code: String, val point: Coordinates, val address: String, val items: String)

enum class DataProblem(val title: String) {
    MISSING_KEY("인증키 미설정"), STATION_REQUIRED("측정소를 선택하세요"), AUTH("키 또는 서비스 승인 상태를 확인하세요"),
    QUOTA("호출 한도 초과 · 나중에 다시 시도하세요"), NETWORK("연결 실패 · 저장 자료를 표시합니다"),
    NO_DATA("아직 발표되지 않았거나 자료가 없습니다"), FORMAT("응답 형식을 확인할 수 없습니다")
}

data class SourceStatus(val name: String, val receivedAt: ZonedDateTime? = null, val issue: DataProblem? = null)
data class LiveSnapshot(
    val weather: WeatherObservation? = null,
    val forecast: List<WeatherForecast> = emptyList(),
    val forecastIssuedAt: ZonedDateTime? = null,
    val air: AirObservation? = null,
    val airForecasts: List<AirForecast> = emptyList(),
    val statuses: List<SourceStatus> = emptyList()
)

fun weatherEmoji(precipitation: Int?, sky: Int?, night: Boolean): String = when (precipitation) {
    1, 4, 5 -> "🌧️"
    2, 3, 6, 7 -> "🌨️"
    0 -> when (sky) { 1 -> if (night) "🌙" else "☀️"; 3 -> "🌤️"; 4 -> "☁️"; else -> "❔" }
    else -> "❔"
}

fun temperatureLabel(value: Double?) = value?.roundToInt()?.let { "$it°" } ?: "—°"
fun isStale(at: ZonedDateTime?, now: ZonedDateTime): Boolean = at == null || at.isAfter(now.plusMinutes(10)) || Duration.between(at, now).toMinutes() > 120

fun LiveSnapshot.currentSkyForecast(now: ZonedDateTime): WeatherForecast? {
    val issued = forecastIssuedAt ?: return null
    if (issued.isAfter(now) || Duration.between(issued, now).toHours() >= 24) return null
    return forecast.filter { kotlin.math.abs(Duration.between(now, it.at).seconds) <= 3600 }
        .minByOrNull { kotlin.math.abs(Duration.between(now, it.at).seconds) }
}

fun WidgetPresentation.withLiveData(snapshot: LiveSnapshot, settings: WidgetSettings, now: ZonedDateTime): WidgetPresentation {
    if (isSample) return this
    val observed = snapshot.weather
    val usableWeather = observed?.takeUnless { isStale(it.at, now) }
    val usableAir = snapshot.air?.takeUnless { isStale(it.at, now) }
    val forecastFresh = snapshot.forecastIssuedAt?.let { !it.isAfter(now) && Duration.between(it, now).toHours() < 24 } == true
    val forecast = if (forecastFresh) snapshot.forecast else emptyList()
    val sky = snapshot.currentSkyForecast(now)
    val night = settings.point?.let { com.jinwoo.twilightandyou.astronomy.SolarCalculator.solarAltitude(now.toInstant(), it) < -0.8333 } ?: false
    val icon = weatherEmoji(usableWeather?.precipitation, sky?.sky, night)
    // A forecast-supplemented sky is visibly tagged; precipitation remains observational.
    val forecastSky = usableWeather?.precipitation == 0 && sky?.sky != null && icon != "❔"
    return copy(
        temperature = temperatureLabel(usableWeather?.temperature),
        weather = icon,
        weatherIsForecast = forecastSky,
        pm10 = usableAir?.pm10,
        pm25 = usableAir?.pm25,
        hourly = forecast.filter { it.at.isAfter(now) }.take(4).map {
            val dark = settings.point?.let { point -> com.jinwoo.twilightandyou.astronomy.SolarCalculator.solarAltitude(it.at.toInstant(), point) < -0.8333 } ?: false
            HourlyWeather(it.at, weatherEmoji(it.precipitation, it.sky, dark), temperatureLabel(it.temperature))
        }
    )
}
