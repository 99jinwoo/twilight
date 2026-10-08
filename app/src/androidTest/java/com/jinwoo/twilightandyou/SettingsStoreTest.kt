package com.jinwoo.twilightandyou

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jinwoo.twilightandyou.data.SettingsStore
import com.jinwoo.twilightandyou.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsStoreTest {
    @Test fun settingsSurviveRepositoryRecreationAndRemainIsolated() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val first = SettingsStore(context)
        val a = WidgetSettings(region = "부산", twilight = TwilightKind.CIVIL, showSample = true, autoLocation = true)
        val b = WidgetSettings(region = "강릉", palette = WidgetPalette.DAWN, eventMode = EventMode.MORNING)
        try {
            first.save(7001, a)
            first.save(70010, b)
            val recreated = SettingsStore(context)
            assertEquals(a, recreated.read(7001))
            assertEquals(b, recreated.read(70010))
            val moved = WidgetSettings(region = "현재 위치", latitude = 37.5665, longitude = 126.9780,
                airArea = "서울", station = "종로구", autoLocation = true)
            recreated.updateAutomaticLocations(listOf(7001, 70010), moved)
            assertEquals(moved.point, recreated.read(7001).point)
            assertEquals("종로구", recreated.read(7001).station)
            assertEquals(a.twilight, recreated.read(7001).twilight)
            assertTrue(recreated.read(7001).showSample)
            assertEquals(b, recreated.read(70010))
            recreated.delete(7001)
            assertFalse(recreated.isConfigured(7001))
            assertEquals(WidgetSettings(), recreated.read(7001))
            assertEquals(b, recreated.read(70010))
        } finally { first.delete(7001); first.delete(70010) }
    }
    @Test fun persistedInvalidStyleValuesAreClamped() = runBlocking {
        val store = SettingsStore(ApplicationProvider.getApplicationContext())
        try {
            store.save(7002, WidgetSettings(opacity = -50, fontScale = 10f))
            assertEquals(25, store.read(7002).opacity)
            assertEquals(1.2f, store.read(7002).fontScale, 0.001f)
        } finally { store.delete(7002) }
    }
}
