package com.jinwoo.twilightandyou.data

import android.content.Context
import android.location.Geocoder
import android.os.Build
import com.jinwoo.twilightandyou.astronomy.Coordinates
import com.jinwoo.twilightandyou.model.LocationDescription
import com.jinwoo.twilightandyou.model.airAreaForAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.Locale
import kotlin.coroutines.resume

@Suppress("DEPRECATION")
suspend fun describeCoordinates(context: Context, point: Coordinates): LocationDescription? = withTimeout(8_000) {
    if (!Geocoder.isPresent()) return@withTimeout null
    val geocoder = Geocoder(context, Locale.KOREA)
    val addresses = if (Build.VERSION.SDK_INT >= 33) suspendCancellableCoroutine { continuation ->
        geocoder.getFromLocation(point.latitude, point.longitude, 1, object : Geocoder.GeocodeListener {
            override fun onGeocode(addresses: MutableList<android.location.Address>) {
                if (continuation.isActive) continuation.resume(addresses)
            }
            override fun onError(errorMessage: String?) {
                if (continuation.isActive) continuation.resume(emptyList())
            }
        })
    } else withContext(Dispatchers.IO) { geocoder.getFromLocation(point.latitude, point.longitude, 1).orEmpty() }
    val address = addresses.firstOrNull { it.countryCode == "KR" } ?: return@withTimeout null
    val province = address.adminArea.orEmpty()
    val city = address.locality ?: address.subAdminArea.orEmpty()
    LocationDescription(listOf(province, city).filter(String::isNotBlank).joinToString(" ").ifBlank { "현재 위치" },
        airAreaForAddress(province, city))
}
