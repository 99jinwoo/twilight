package com.jinwoo.twilightandyou.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Looper
import android.os.SystemClock
import com.jinwoo.twilightandyou.astronomy.Coordinates
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

/** Called only by the user's one-time location button. No background subscription. */
@SuppressLint("MissingPermission")
suspend fun currentCoordinates(context: Context): Coordinates? = withTimeout(20_000) {
    val manager = context.getSystemService(LocationManager::class.java)
    val fine = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val providers = listOf(LocationManager.NETWORK_PROVIDER) + if (fine) listOf(LocationManager.GPS_PROVIDER) else emptyList()
    val provider = providers.firstOrNull { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        ?: return@withTimeout null
    val location: Location? = suspendCancellableCoroutine { continuation ->
        if (Build.VERSION.SDK_INT >= 30) {
            val signal = CancellationSignal()
            continuation.invokeOnCancellation { signal.cancel() }
            manager.getCurrentLocation(provider, signal, context.mainExecutor) { value ->
                if (continuation.isActive) continuation.resume(value)
            }
        } else {
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    manager.removeUpdates(this)
                    if (continuation.isActive) continuation.resume(location)
                }
                override fun onProviderDisabled(provider: String) {
                    manager.removeUpdates(this)
                    if (continuation.isActive) continuation.resume(null)
                }
                override fun onProviderEnabled(provider: String) = Unit
                @Deprecated("Legacy location listener")
                override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
            }
            manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            continuation.invokeOnCancellation { manager.removeUpdates(listener) }
        }
    }
    location?.takeIf { (SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos) in 0..120_000_000_000L }
        ?.let { Coordinates(it.latitude, it.longitude) }
}
