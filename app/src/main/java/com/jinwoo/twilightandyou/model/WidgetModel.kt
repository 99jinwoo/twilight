package com.jinwoo.twilightandyou.model

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
    val showSample: Boolean = false
)

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

data class WidgetPresentation(
    val temperature: String,
    val weather: String,
    val pm10: Int?,
    val pm25: Int?,
    val eventName: String,
    val eventTime: String,
    val eventDate: String,
    val morningTime: String,
    val eveningTime: String,
    val status: String,
    val forecast: String,
    val isSample: Boolean
) {
    val pm10Grade get() = DustGrade.fromConcentration(pm10, false)
    val pm25Grade get() = DustGrade.fromConcentration(pm25, true)

    companion object {
        fun from(settings: WidgetSettings): WidgetPresentation {
            val name = if (settings.eventMode == EventMode.MORNING) settings.twilight.morning
                else settings.twilight.evening
            if (!settings.showSample) return WidgetPresentation(
                "—°", "❔", null, null, name, "—:—", "날짜 —",
                "—:—", "—:—", "미연결", "예보 미연결", false
            )
            val (morning, evening) = when (settings.twilight) {
                TwilightKind.CIVIL -> "06:08" to "18:24"
                TwilightKind.NAUTICAL -> "05:38" to "18:54"
                TwilightKind.ASTRONOMICAL -> "05:08" to "19:24"
            }
            return WidgetPresentation(
                "22°", "☀️", 42, 12, name,
                if (settings.eventMode == EventMode.MORNING) morning else evening,
                "10/07", morning, evening, "샘플 · 17:00", "내일 예보 미 보통 · 초 좋음", true
            )
        }
    }
}
