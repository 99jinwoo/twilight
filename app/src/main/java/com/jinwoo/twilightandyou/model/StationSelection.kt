package com.jinwoo.twilightandyou.model

data class StationSelection(val settings: WidgetSettings, val issue: DataProblem? = null)

/** Address filters supported by the AirKorea station API, independent of station names. */
fun stationSearchQueries(settings: WidgetSettings): List<String> {
    val aliases = when (settings.forecastArea) {
        "경기북부", "경기남부" -> listOf("경기", "경기도")
        "강원영동", "강원영서" -> listOf("강원", "강원특별자치도")
        "충남" -> listOf("충남", "충청남도")
        "충북" -> listOf("충북", "충청북도")
        "전북" -> listOf("전북", "전북특별자치도")
        "전남" -> listOf("전남", "전라남도")
        "경북" -> listOf("경북", "경상북도")
        "경남" -> listOf("경남", "경상남도")
        in Regions.airAreas -> listOf(settings.forecastArea)
        else -> emptyList()
    }
    val municipality = settings.region.split(' ').lastOrNull()?.takeIf {
        it.matches(Regex("[가-힣]{2,10}[시군구]")) && !it.endsWith("특별시") && !it.endsWith("광역시")
    }
    return (listOfNotNull(municipality) + aliases).distinct().ifEmpty { listOf("") }.take(3)
}
