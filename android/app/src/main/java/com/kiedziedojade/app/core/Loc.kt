package com.kiedziedojade.app.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** One-shot location: last known fix when fresh, otherwise a single update. */
class Locator(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun manager(): LocationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private fun lastKnown(): Location? {
        val lm = manager()
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )
        return providers
            .mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
            .maxByOrNull { it.time }
    }

    /** Returns (lat, lng) or null when there is no permission / no fix in time. */
    suspend fun current(): Pair<Double, Double>? {
        if (!hasPermission()) return null
        val fresh = lastKnown()?.takeIf { System.currentTimeMillis() - it.time < 15 * 60 * 1000 }
        if (fresh != null) return fresh.latitude to fresh.longitude

        val lm = manager()
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        if (providers.isEmpty()) {
            val last = lastKnown() ?: return null
            return last.latitude to last.longitude
        }

        val fix = withTimeoutOrNull(12_000) { requestOne(lm, providers) }
        if (fix != null) return fix.latitude to fix.longitude
        val last = lastKnown() ?: return null
        return last.latitude to last.longitude
    }

    @Suppress("MissingPermission")
    private suspend fun requestOne(lm: LocationManager, providers: List<String>): Location? =
        suspendCancellableCoroutine { cont ->
            var done = false
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (done) return
                    done = true
                    runCatching { lm.removeUpdates(this) }
                    if (cont.isActive) cont.resume(location)
                }

                @Deprecated("Required for API < 30")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }
            cont.invokeOnCancellation { runCatching { lm.removeUpdates(listener) } }
            var registered = false
            for (p in providers) {
                val ok = runCatching {
                    lm.requestLocationUpdates(p, 0L, 0f, listener, Looper.getMainLooper())
                }.isSuccess
                if (ok) {
                    registered = true
                    break
                }
            }
            if (!registered) cont.resume(null)
        }
}
