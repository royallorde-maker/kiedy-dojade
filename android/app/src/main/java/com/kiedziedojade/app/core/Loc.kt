package com.kiedziedojade.app.core

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
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

    /** Systemowa lokalizacja włączona? Sama zgoda to jeszcze nie działający GPS. */
    fun locationEnabled(): Boolean {
        val lm = manager()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { lm.isLocationEnabled }.getOrDefault(false)
        } else {
            runCatching {
                lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            }.getOrDefault(false)
        }
    }

    private fun manager(): LocationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    @SuppressLint("MissingPermission")
    private fun knownLocations(): List<Location> {
        val lm = manager()
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )
        return providers.mapNotNull { p ->
            runCatching { lm.getLastKnownLocation(p) }.getOrNull()
        }
    }

    @SuppressLint("MissingPermission")
    private fun lastKnown(): Location? {
        val all = knownLocations()
        val newest = all.maxByOrNull { it.time } ?: return null
        // GPS jest dokładniejszy niż sieć, więc przy podobnym wieku wybieramy
        // jego ramkę. Inaczej świeższa, ale zgrubna lokalizacja sieciowa
        // potrafi na starcie przesunąć znacznik o setki metrów.
        val gps = all.firstOrNull { it.provider == LocationManager.GPS_PROVIDER }
        return if (gps != null && newest.time - gps.time <= 15_000L) gps else newest
    }

    /** Returns (lat, lng) or null when there is no permission / no fix in time. */
    suspend fun current(): Pair<Double, Double>? {
        if (!hasPermission()) return null
        val fresh = lastKnown()?.takeIf { known ->
            val age = System.currentTimeMillis() - known.time
            age < 60_000L && (!known.hasAccuracy() || known.accuracy <= 100f)
        }
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
            // Rejestrujemy wszystkie dostępne źródła naraz: GPS bywa zajęty
            // dłużej, a pierwsza poprawna ramka sieci pozwala od razu pokazać
            // pozycję zamiast czekać do końca timeoutu.
            for (p in providers) {
                val ok = runCatching {
                    lm.requestLocationUpdates(p, 0L, 0f, listener, Looper.getMainLooper())
                }.isSuccess
                if (ok) registered = true
            }
            if (!registered) cont.resume(null)
        }

    /**
     * Ciągły strumień pozycji. Pierwszą ramką jest ostatnia znana pozycja, żeby
     * mapa nie startowała pusta; potem aktualizacje co [minTimeMs] / [minDistanceM].
     * Nawigacja używa domyślnych 1 s / 2 m, a przegląd mapy rzadszych aktualizacji.
     * Strumień żyje tak długo, jak jego odbiorca — anulowanie wyrejestrowuje nasłuch.
     */
    @SuppressLint("MissingPermission")
    fun stream(minTimeMs: Long = 1_000L, minDistanceM: Float = 2f): Flow<Fix> = callbackFlow {
        if (!hasPermission()) {
            close()
            awaitClose { }
            return@callbackFlow
        }

        var lastGps: Location? = null
        var lastGpsAt = 0L
        var lastEmitted: Fix? = null

        // Odrzuca ramki, które nie mogą być prawdziwe (skok o setki metrów
        // w ułamku sekundy). Dzięki temu znacznik nie przeskakuje po błędnym
        // odczycie GPS ani po podmianie dostawcy lokalizacji.
        fun plausible(f: Fix): Boolean {
            val prev = lastEmitted ?: return true
            val dt = (f.timeMs - prev.timeMs) / 1000.0
            if (dt <= 0.0 || dt > 120.0) return true
            val dist = haversine(prev.lat, prev.lng, f.lat, f.lng)
            val accuracy = ((prev.accuracy ?: 0f) + (f.accuracy ?: 0f)).toDouble()
            return dist <= 70.0 * dt + 25.0 + accuracy
        }

        fun publish(location: Location): Boolean {
            if (location.hasAccuracy() && location.accuracy > 150f) return false
            val age = if (location.time > 0L) {
                System.currentTimeMillis() - location.time
            } else {
                0L
            }
            if (age > 30_000L) return false
            val f = location.toFix()
            if (!plausible(f)) return false
            lastEmitted = f
            trySend(f)
            return true
        }

        // Ostatnia znana pozycja tylko wtedy, gdy jest świeża i dokładna.
        // Stara ramka potrafi być kilkaset metrów od rzeczywistości i to ona
        // odpowiadała za pierwszy, losowy skok znacznika.
        val known = knownLocations()
            .filter { location ->
                val age = if (location.time > 0L) {
                    System.currentTimeMillis() - location.time
                } else {
                    Long.MAX_VALUE
                }
                age <= 10_000L &&
                    (!location.hasAccuracy() || location.accuracy <= 80f)
            }
            .let { fresh ->
                // Jeśli oba źródła są świeże, GPS ma pierwszeństwo.
                fresh.firstOrNull { it.provider == LocationManager.GPS_PROVIDER }
                    ?: fresh.maxByOrNull { it.time }
            }
        if (known != null) {
            val age = if (known.time > 0L) {
                System.currentTimeMillis() - known.time
            } else {
                Long.MAX_VALUE
            }
            val f = known.toFix()
            lastEmitted = f
            if (known.provider == LocationManager.GPS_PROVIDER) {
                lastGps = known
                lastGpsAt = System.currentTimeMillis() - age.coerceAtLeast(0L)
            }
            trySend(f)
        }

        val lm = manager()
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                val now = System.currentTimeMillis()
                if (location.provider == LocationManager.GPS_PROVIDER) {
                    if (publish(location)) {
                        lastGps = location
                        lastGpsAt = now
                    }
                } else {
                    // GPS jest dokładniejszy, więc gdy ma świeżą ramkę, sieć
                    // nie może przesuwać znacznika. Wyjątek: sieć jest wyraźnie
                    // lepsza (np. GPS zgubił zasięg pod drzewami).
                    val gps = lastGps
                    val recentGps = gps != null && now - lastGpsAt < 12_000L
                    val clearlyBetter = gps == null ||
                        !gps.hasAccuracy() || !location.hasAccuracy() ||
                        location.accuracy * 2f < gps.accuracy
                    if (!recentGps || clearlyBetter) publish(location)
                }
            }

            @Deprecated("Required for API < 30")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { p -> runCatching { lm.isProviderEnabled(p) }.getOrDefault(false) }
        var registered = false
        for (p in providers) {
            val ok = runCatching {
                lm.requestLocationUpdates(p, minTimeMs, minDistanceM, listener, Looper.getMainLooper())
            }.isSuccess
            if (ok) registered = true
        }
        if (!registered) {
            close()
            awaitClose { }
            return@callbackFlow
        }
        awaitClose { runCatching { lm.removeUpdates(listener) } }
    }.conflate()

    private fun Location.toFix(): Fix = Fix(
        lat = latitude,
        lng = longitude,
        bearing = if (hasBearing()) bearing else null,
        bearingAccuracy = if (hasBearingAccuracy()) bearingAccuracyDegrees else null,
        accuracy = if (hasAccuracy()) accuracy else null,
        speed = if (hasSpeed()) speed else null,
        timeMs = time,
    )
}
