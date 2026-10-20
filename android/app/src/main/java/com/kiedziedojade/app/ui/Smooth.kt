package com.kiedziedojade.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import com.kiedziedojade.app.core.headingDelta
import com.kiedziedojade.app.core.normalizeHeading
import kotlin.math.abs

/** Czas dojazdu znacznika do nowej pozycji GPS. */
internal const val LOCATION_ANIM_MS = 850L

/** Krótkie wygładzenie samej strzałki kierunku (stopnie). */
private const val HEADING_ANIM_MS = 260L

private fun smoothStep(t: Float): Float = t * t * (3f - 2f * t)

/**
 * Płynnie przesuwa znacznik między kolejnymi odczytami GPS. Animacja startuje
 * zawsze z aktualnie widocznego punktu, więc nowa ramka w trakcie ruchu nie
 * powoduje skoku — tylko delikatnie zmienia cel.
 */
@Composable
internal fun rememberSmoothPosition(
    target: Pair<Double, Double>?,
    durationMillis: Long = LOCATION_ANIM_MS,
): Pair<Double, Double>? {
    var shown by remember { mutableStateOf(target) }

    LaunchedEffect(target) {
        val end = target ?: return@LaunchedEffect
        val start = shown
        if (start == null) {
            shown = end
            return@LaunchedEffect
        }
        if (start.first == end.first && start.second == end.second) return@LaunchedEffect

        val startedAt = withFrameNanos { it }
        val durationNs = durationMillis.coerceAtLeast(1L) * 1_000_000.0
        while (true) {
            val frame = withFrameNanos { it }
            val t = ((frame - startedAt) / durationNs).coerceIn(0.0, 1.0)
            val e = smoothStep(t.toFloat()).toDouble()
            shown = (start.first + (end.first - start.first) * e) to
                (start.second + (end.second - start.second) * e)
            if (t >= 1.0) break
        }
    }
    return shown
}

/**
 * Wygładza obrót strzałki po najkrótszej drodze (bez przeskoku 359° → 0°).
 * Gdy źródło kierunku chwilowo zniknie, ostatnia wartość zostaje widoczna.
 */
@Composable
internal fun rememberSmoothHeading(
    target: Float?,
    durationMillis: Long = HEADING_ANIM_MS,
): Float? {
    var shown by remember { mutableStateOf(target) }

    LaunchedEffect(target) {
        val end = target ?: return@LaunchedEffect
        val start = shown
        if (start == null) {
            shown = end
            return@LaunchedEffect
        }
        val delta = headingDelta(start, end)
        if (abs(delta) < 0.05f) {
            shown = end
            return@LaunchedEffect
        }

        val startedAt = withFrameNanos { it }
        val durationNs = durationMillis.coerceAtLeast(1L) * 1_000_000.0
        while (true) {
            val frame = withFrameNanos { it }
            val t = ((frame - startedAt) / durationNs).coerceIn(0.0, 1.0)
            val e = smoothStep(t.toFloat())
            shown = normalizeHeading(start + delta * e)
            if (t >= 1.0) break
        }
    }
    return shown
}
