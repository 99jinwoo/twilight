package com.jinwoo.twilightandyou.model

import com.jinwoo.twilightandyou.astronomy.Coordinates
import kotlin.math.*

data class Region(val name: String, val point: Coordinates, val airArea: String)

object Regions {
    val presets = listOf(
        Region("서울", Coordinates(37.5665, 126.9780), "서울"),
        Region("부산", Coordinates(35.1796, 129.0756), "부산"),
        Region("제주", Coordinates(33.4996, 126.5312), "제주"),
        Region("강릉", Coordinates(37.7519, 128.8761), "강원영동")
    )
    val airAreas = listOf("서울", "부산", "대구", "인천", "광주", "대전", "울산", "세종", "경기북부", "경기남부",
        "강원영서", "강원영동", "충북", "충남", "전북", "전남", "경북", "경남", "제주")
    fun find(name: String) = presets.firstOrNull { it.name == name }
}

data class WeatherGrid(val x: Int, val y: Int)

/** KMA's Lambert conformal grid; east-positive WGS84 longitude. */
fun Coordinates.weatherGrid(): WeatherGrid {
    val rad = Math.PI / 180
    val earth = 6371.00877 / 5.0
    val lat1 = 30 * rad
    val lat2 = 60 * rad
    val lon0 = 126 * rad
    val lat0 = 38 * rad
    val n = ln(cos(lat1) / cos(lat2)) / ln(tan(Math.PI / 4 + lat2 / 2) / tan(Math.PI / 4 + lat1 / 2))
    val f = tan(Math.PI / 4 + lat1 / 2).pow(n) * cos(lat1) / n
    val rho0 = earth * f / tan(Math.PI / 4 + lat0 / 2).pow(n)
    val rho = earth * f / tan(Math.PI / 4 + latitude * rad / 2).pow(n)
    var theta = longitude * rad - lon0
    if (theta > Math.PI) theta -= 2 * Math.PI
    if (theta < -Math.PI) theta += 2 * Math.PI
    theta *= n
    return WeatherGrid(floor(rho * sin(theta) + 43 + 0.5).toInt(), floor(rho0 - rho * cos(theta) + 136 + 0.5).toInt())
}

fun Coordinates.distanceKm(other: Coordinates): Double {
    val dLat = Math.toRadians(other.latitude - latitude)
    val dLon = Math.toRadians(other.longitude - longitude)
    val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(latitude)) * cos(Math.toRadians(other.latitude)) * sin(dLon / 2).pow(2)
    return 6371.0088 * 2 * asin(sqrt(a.coerceIn(0.0, 1.0)))
}
