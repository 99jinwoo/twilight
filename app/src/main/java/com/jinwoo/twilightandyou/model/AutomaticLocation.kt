package com.jinwoo.twilightandyou.model

import com.jinwoo.twilightandyou.astronomy.Coordinates
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException

data class LocationDescription(val name: String, val airArea: String)
data class LocationUpdate(val settings: WidgetSettings, val message: String, val located: Boolean = false)

/** Resolve all location-dependent fields together; never reuse another area's observation. */
suspend fun resolveAutomaticLocation(
    previous: WidgetSettings,
    locate: suspend () -> Coordinates?,
    describe: suspend (Coordinates) -> LocationDescription?,
    stations: suspend (WidgetSettings) -> List<AirStation>
): LocationUpdate {
    if (!previous.autoLocation) return LocationUpdate(previous, "고정 지역을 사용합니다.")
    val point = try { locate() }
    catch (_: TimeoutCancellationException) { null }
    catch (e: CancellationException) { throw e }
    catch (_: Exception) { null }
    if (point == null) return LocationUpdate(previous, "위치를 확인하지 못해 마지막 지역을 사용합니다. 위치 권한과 위치 서비스를 확인해주세요.")
    if (point.latitude !in 32.0..40.0 || point.longitude !in 123.0..133.0)
        return LocationUpdate(previous, "현재는 국내 지역을 지원합니다. 마지막 지역을 사용합니다.")
    val description = try { describe(point) }
    catch (_: TimeoutCancellationException) { null }
    catch (e: CancellationException) { throw e }
    catch (_: Exception) { null }
    val target = previous.copy(region = description?.name ?: "현재 위치", latitude = point.latitude,
        longitude = point.longitude, airArea = description?.airArea.orEmpty(), station = "")
    val nearest = try { stations(target).minByOrNull { point.distanceKm(it.point) } }
    catch (e: CancellationException) { throw e }
    catch (_: Exception) { null }
    return LocationUpdate(target.copy(station = nearest?.name.orEmpty()),
        "현재 위치를 적용했습니다." + (if (nearest == null) " 측정소를 찾지 못해 먼지 실측은 비워둡니다." else " 가까운 ${nearest.name} 측정소를 사용합니다.") +
            if (target.forecastArea.isBlank()) " 먼지 예보권역은 지역 설정에서 확인해주세요." else "", true)
}

/** AirKorea FAQ, 2026-01-27. Unknown administrative names remain unassigned. */
fun airAreaForAddress(province: String, municipality: String): String {
    val city = municipality.split(' ').firstOrNull().orEmpty()
    return when {
        province.startsWith("경기") -> when (city) {
            in setOf("가평군", "고양시", "구리시", "김포시", "남양주시", "양주시", "연천군", "동두천시", "의정부시", "파주시", "포천시") -> "경기북부"
            in setOf("과천시", "광명시", "광주시", "군포시", "부천시", "성남시", "수원시", "시흥시", "안산시", "여주시", "안성시", "안양시", "양평군", "오산시", "용인시", "의왕시", "이천시", "평택시", "하남시", "화성시") -> "경기남부"
            else -> ""
        }
        province.startsWith("강원") -> when (city) {
            in setOf("고성군", "속초시", "양양군", "강릉시", "동해시", "삼척시", "태백시") -> "강원영동"
            in setOf("양구군", "인제군", "홍천군", "평창군", "정선군", "철원군", "화천군", "춘천시", "횡성군", "원주시", "영월군") -> "강원영서"
            else -> ""
        }
        else -> mapOf("서울" to "서울", "부산" to "부산", "대구" to "대구", "인천" to "인천", "광주" to "광주", "대전" to "대전", "울산" to "울산", "세종" to "세종", "충청북" to "충북", "충청남" to "충남", "전라북" to "전북", "전북" to "전북", "전라남" to "전남", "경상북" to "경북", "경상남" to "경남", "제주" to "제주")
            .entries.firstOrNull { province.startsWith(it.key) }?.value.orEmpty()
    }
}
