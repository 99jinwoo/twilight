package com.jinwoo.twilightandyou.astronomy

import com.jinwoo.twilightandyou.model.SEOUL
import com.jinwoo.twilightandyou.model.TwilightEvent
import com.jinwoo.twilightandyou.model.TwilightKind
import com.jinwoo.twilightandyou.model.TwilightPair
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.*

data class Coordinates(val latitude: Double, val longitude: Double) {
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0)
        require(longitude.isFinite() && longitude in -180.0..180.0)
    }
}

data class SolarEvents(val morning: ZonedDateTime?, val evening: ZonedDateTime?)

/** Geometric solar altitude, using the Meeus equations documented by NOAA.
 * Twilight uses the solar centre at -6/-12/-18 degrees, without atmospheric refraction.
 * Sunrise/set use the conventional -0.8333 degree horizon.
 */
object SolarCalculator {
    fun events(date: LocalDate, point: Coordinates, altitude: Double, zone: ZoneId = SEOUL): SolarEvents {
        require(altitude in -90.0..90.0)
        val start = date.atStartOfDay(zone).toEpochSecond()
        val end = date.plusDays(1).atStartOfDay(zone).toEpochSecond()
        var before = start
        var previous = solarAltitude(Instant.ofEpochSecond(before), point) - altitude
        var morning: ZonedDateTime? = null
        var evening: ZonedDateTime? = null
        // A two-minute bracket, then one-second root search. Absent crossings remain null.
        while (before < end) {
            val after = minOf(before + 120, end)
            val value = solarAltitude(Instant.ofEpochSecond(after), point) - altitude
            val rising = previous <= 0 && value > 0
            val setting = previous >= 0 && value < 0
            if (rising || setting) {
                var low = before
                var high = after
                while (high - low > 1) {
                    val mid = (low + high) / 2
                    val midValue = solarAltitude(Instant.ofEpochSecond(mid), point) - altitude
                    if ((rising && midValue > 0) || (setting && midValue < 0)) high = mid else low = mid
                }
                val event = Instant.ofEpochSecond(high).atZone(zone)
                if (event.toLocalDate() == date) {
                    if (rising) morning = event else evening = event
                }
            }
            before = after
            previous = value
        }
        return SolarEvents(morning, evening)
    }

    fun pair(now: ZonedDateTime, point: Coordinates, kind: TwilightKind): TwilightPair {
        val local = now.withZoneSameInstant(SEOUL)
        val names = when (kind) {
            TwilightKind.CIVIL -> "시민↑" to "시민↓"
            TwilightKind.NAUTICAL -> "BMNT" to "EENT"
            TwilightKind.ASTRONOMICAL -> "천문↑" to "천문↓"
        }
        val altitude = when (kind) {
            TwilightKind.CIVIL -> -6.0
            TwilightKind.NAUTICAL -> -12.0
            TwilightKind.ASTRONOMICAL -> -18.0
        }
        val events = (-1L..1L).flatMap { offset ->
            val day = events(local.toLocalDate().plusDays(offset), point, altitude)
            listOfNotNull(day.morning?.let { TwilightEvent(names.first, it) }, day.evening?.let { TwilightEvent(names.second, it) })
        }
        return TwilightPair.around(events, local)
    }

    fun solarAltitude(instant: Instant, point: Coordinates): Double {
        val julianDay = instant.epochSecond / 86400.0 + instant.nano / 86400e9 + 2440587.5
        val t = (julianDay - 2451545.0) / 36525.0
        val meanLongitude = radians(normalize(280.46646 + t * (36000.76983 + t * 0.0003032)))
        val meanAnomaly = radians(357.52911 + t * (35999.05029 - 0.0001537 * t))
        val eccentricity = 0.016708634 - t * (0.000042037 + 0.0000001267 * t)
        val centre = sin(meanAnomaly) * (1.914602 - t * (0.004817 + 0.000014 * t)) +
            sin(2 * meanAnomaly) * (0.019993 - 0.000101 * t) + sin(3 * meanAnomaly) * 0.000289
        val omega = radians(125.04 - 1934.136 * t)
        val apparentLongitude = meanLongitude + radians(centre - 0.00569 - 0.00478 * sin(omega))
        val seconds = 21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))
        val obliquity = radians(23 + (26 + seconds / 60) / 60 + 0.00256 * cos(omega))
        val declination = asin(sin(obliquity) * sin(apparentLongitude))
        val y = tan(obliquity / 2).pow(2)
        val equation = 4 * degrees(y * sin(2 * meanLongitude) - 2 * eccentricity * sin(meanAnomaly) +
            4 * eccentricity * y * sin(meanAnomaly) * cos(2 * meanLongitude) -
            0.5 * y * y * sin(4 * meanLongitude) - 1.25 * eccentricity * eccentricity * sin(2 * meanAnomaly))
        val utcMinutes = Math.floorMod(instant.epochSecond, 86400).toDouble() / 60 + instant.nano / 60e9
        val solarMinutes = ((utcMinutes + equation + 4 * point.longitude) % 1440 + 1440) % 1440
        val hourAngle = radians(solarMinutes / 4 - 180)
        val latitude = radians(point.latitude)
        return degrees(asin((sin(latitude) * sin(declination) + cos(latitude) * cos(declination) * cos(hourAngle)).coerceIn(-1.0, 1.0)))
    }

    private fun radians(value: Double) = Math.toRadians(value)
    private fun degrees(value: Double) = Math.toDegrees(value)
    private fun normalize(value: Double) = (value % 360 + 360) % 360
}
