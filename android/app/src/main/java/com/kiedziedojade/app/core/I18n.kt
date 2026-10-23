package com.kiedziedojade.app.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Two languages, one small helper. `t(pl, en)` keeps both strings at the call
 * site — no key catalogue to drift out of sync.
 */
enum class Lang { PL, EN }

object I18n {
    /** Observable so every composable reading `t(...)` re-renders on switch. */
    var lang by mutableStateOf(Lang.PL)

    val pl: Boolean get() = lang == Lang.PL

    fun t(plText: String, enText: String): String = if (pl) plText else enText

    /** Polish plural: 1 przystanek, 2–4 przystanki, 5+ przystanków. */
    fun plural(n: Int, one: String, few: String, many: String): String {
        if (!pl) return if (n == 1) one else many
        val mod10 = n % 10
        val mod100 = n % 100
        return when {
            n == 1 -> one
            mod10 in 2..4 && mod100 !in 12..14 -> few
            else -> many
        }
    }

    fun stops(n: Int) = if (pl) {
        plural(n, "przystanek", "przystanki", "przystanków")
    } else {
        plural(n, "stop", "stops", "stops")
    }

    fun trips(n: Int) = if (pl) {
        plural(n, "kurs", "kursy", "kursów")
    } else {
        plural(n, "trip", "trips", "trips")
    }

    fun transfers(n: Int) = if (pl) {
        plural(n, "przesiadka", "przesiadki", "przesiadek")
    } else {
        plural(n, "change", "changes", "changes")
    }

    fun stopsShort(n: Int) = if (pl) {
        plural(n, "przystanek", "przystanki", "przystanków")
    } else {
        plural(n, "stop", "stops", "stops")
    }

    fun minutes(n: Int) = if (pl) {
        plural(n, "minuta", "minuty", "minut")
    } else {
        plural(n, "minute", "minutes", "minutes")
    }

    fun rides(n: Int) = if (pl) {
        plural(n, "autobus", "autobusy", "autobusów")
    } else {
        plural(n, "bus", "buses", "buses")
    }
}
