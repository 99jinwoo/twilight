package com.jinwoo.twilightandyou.model

import org.junit.Assert.*
import org.junit.Test

class WidgetModelTest {
    @Test fun coarseDustBoundaryValues() {
        val cases = mapOf(0 to DustGrade.GOOD, 30 to DustGrade.GOOD,
            31 to DustGrade.NORMAL, 80 to DustGrade.NORMAL, 81 to DustGrade.BAD,
            150 to DustGrade.BAD, 151 to DustGrade.VERY_BAD)
        cases.forEach { (value, expected) -> assertEquals("PM10 $value", expected, DustGrade.fromConcentration(value, false)) }
    }
    @Test fun fineDustBoundaryValues() {
        val cases = mapOf(0 to DustGrade.GOOD, 15 to DustGrade.GOOD,
            16 to DustGrade.NORMAL, 35 to DustGrade.NORMAL, 36 to DustGrade.BAD,
            75 to DustGrade.BAD, 76 to DustGrade.VERY_BAD)
        cases.forEach { (value, expected) -> assertEquals("PM2.5 $value", expected, DustGrade.fromConcentration(value, true)) }
    }
    @Test fun missingOrNegativeIsNeverGoodAir() {
        listOf(false, true).forEach { fine ->
            assertEquals(DustGrade.MISSING, DustGrade.fromConcentration(null, fine))
            assertEquals(DustGrade.MISSING, DustGrade.fromConcentration(-1, fine))
        }
    }
    @Test fun unconnectedDefaultDoesNotManufactureWeatherOrTwilight() {
        for (kind in TwilightKind.entries) for (mode in EventMode.entries) {
            val model = WidgetPresentation.from(WidgetSettings(twilight = kind, eventMode = mode))
            assertFalse(model.isSample)
            assertEquals("—°", model.temperature)
            assertNull(model.pm10)
            assertNull(model.pm25)
            assertEquals(DustGrade.MISSING, model.pm10Grade)
            assertEquals("—:—", model.eventTime)
        }
    }
    @Test fun sampleOptInKeepsDustGradesIndependent() {
        val model = WidgetPresentation.from(WidgetSettings(showSample = true))
        assertTrue(model.isSample)
        assertTrue(model.status.contains("샘플"))
        assertEquals(DustGrade.NORMAL, model.pm10Grade)
        assertEquals(DustGrade.GOOD, model.pm25Grade)
    }
    @Test fun morningSelectionUsesMorningLabelAndFixture() {
        for (kind in TwilightKind.entries) {
            val model = WidgetPresentation.from(WidgetSettings(twilight = kind, eventMode = EventMode.MORNING, showSample = true))
            assertEquals(kind.morning, model.eventName)
            assertEquals(model.morningTime, model.eventTime)
            assertNotEquals(model.eveningTime, model.eventTime)
        }
    }
    @Test fun resizingChoosesTheLayoutThatFits() {
        assertEquals(WidgetShape.COMPACT, WidgetShape.forSize(56f, 72f))
        assertEquals(WidgetShape.COMPACT, WidgetShape.forSize(100f, 112f))
        assertEquals(WidgetShape.WIDE, WidgetShape.forSize(224f, 112f))
        assertEquals(WidgetShape.TALL, WidgetShape.forSize(112f, 228f))
        assertEquals(WidgetShape.TALL, WidgetShape.forSize(180f, 240f))
    }
}
