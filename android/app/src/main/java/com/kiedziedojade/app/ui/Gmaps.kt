package com.kiedziedojade.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.util.Locale

/**
 * Przekazanie prowadzenia do Google Maps (nawigacja piesza): najpierw próbujemy
 * aplikacji przez schemat `google.navigation`, a gdy jej nie ma — otwieramy
 * trasę w przeglądarce. Przydatne, bo planer zna przystanki, ale nie zna ulic.
 */
fun openGoogleMapsNav(context: Context, lat: Double, lng: Double) {
    val coords = String.format(Locale.ROOT, "%.5f,%.5f", lat, lng)
    val app = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$coords&mode=w"))
        .setPackage("com.google.android.apps.maps")
    val launched = runCatching { context.startActivity(app); true }.getOrDefault(false)
    if (launched) return
    val web = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$coords&travelmode=walking"),
    )
    runCatching { context.startActivity(web) }
}
