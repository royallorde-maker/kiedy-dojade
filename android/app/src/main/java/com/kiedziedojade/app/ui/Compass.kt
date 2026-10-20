package com.kiedziedojade.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.kiedziedojade.app.core.CompassHeading

/**
 * Żywy kierunek telefonu dla znacznika pozycji. Czujnik działa tylko przez
 * czas kompozycji ekranu prowadzenia i jest zwalniany po wyjściu.
 */
@Composable
fun rememberCompassHeading(): Float? {
    val context = LocalContext.current
    var heading by remember { mutableStateOf<Float?>(null) }

    DisposableEffect(context) {
        val compass = CompassHeading(context) { heading = it }
        compass.start()
        onDispose { compass.stop() }
    }
    return heading
}
