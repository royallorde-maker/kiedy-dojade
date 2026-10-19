package com.kiedziedojade.app.core

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Logika prowadzenia po wybranym połączeniu (bez Androida, więc testowalna):
 * kroki trasy, postęp względem pozycji GPS i moment wysiadania.
 */

/** Pozycja urządzenia z GPS. */
data class Fix(
    val lat: Double,
    val lng: Double,
    /** kierunek ruchu w stopniach (0 = północ) albo null, gdy nieznany */
    val bearing: Float? = null,
    /** dokładność w metrach (null = system jej nie podał) */
    val accuracy: Float? = null,
    /** prędkość w m/s (null = system jej nie podał) */
    val speed: Float? = null,
    /** czas pomiaru (epoch ms); 0 = nieznany (np. w testach) */
    val timeMs: Long = 0L,
)

/** Krok prowadzenia — jeden odcinek połączenia: marsz albo jazda autobusem. */
sealed interface NavStep {
    val legIndex: Int
    /** punkt, do którego prowadzi krok (przystanek docelowy / wysiadki) */
    val target: Pair<Double, Double>?

    data class Walk(
        override val legIndex: Int,
        val toName: String,
        /** odległość z planu (m) — używana, gdy nie ma jeszcze GPS */
        val meters: Double,
        override val target: Pair<Double, Double>?,
    ) : NavStep

    data class Ride(
        override val legIndex: Int,
        val line: String,
        val direction: String,
        val boardName: String,
        val alightName: String,
        val departMin: Int,
        val arriveMin: Int,
        /** przystanki kursu w kolejności — ostatni to wysiadka */
        val stops: List<Pair<Double, Double>>,
        override val target: Pair<Double, Double>?,
    ) : NavStep
}

/** Zamienia nogi połączenia na kroki prowadzenia (w tej samej kolejności). */
fun navSteps(it: Itinerary): List<NavStep> = it.legs.mapIndexedNotNull { i, leg ->
    when (leg) {
        is WalkLeg -> NavStep.Walk(
            legIndex = i,
            toName = leg.toName,
            meters = leg.meters,
            target = leg.toPt,
        )

        is RideLeg -> NavStep.Ride(
            legIndex = i,
            line = leg.line,
            direction = leg.dir,
            boardName = leg.boardStop.name,
            alightName = leg.alightStop.name,
            departMin = leg.depMin,
            arriveMin = leg.arrMin,
            stops = leg.stops.map { rs -> rs.stop.lat to rs.stop.lng },
            target = leg.alightStop.lat to leg.alightStop.lng,
        )
    }
}

/** Nazwa celu podróży (ostatni odcinek połączenia). */
fun navDestination(it: Itinerary): String = when (val last = it.legs.lastOrNull()) {
    is WalkLeg -> last.toName
    is RideLeg -> last.alightStop.name
    else -> ""
}

/** Promień (m), w którym uznajemy krok za zrobiony. */
const val NAV_ARRIVE_M = 45.0

/** Promień (m) wokół przystanku wysiadki, od którego wołamy „wysiądź teraz”. */
const val NAV_GET_OFF_M = 120.0

/** Promień (m), od którego uprzedzamy „za chwilę Twoja wysiadka”. */
const val NAV_SOON_M = 320.0

/** Odległość w linii prostej od pozycji do celu kroku (null, gdy brak danych). */
fun navDistance(step: NavStep, fix: Fix?): Double? {
    val t = step.target ?: return null
    val f = fix ?: return null
    return haversine(f.lat, f.lng, t.first, t.second)
}

/** Ile przystanków zostało do wysiadki (wg najbliższego przystanku kursu). */
fun rideStopsLeft(step: NavStep.Ride, fix: Fix?): Int? {
    val f = fix ?: return null
    if (step.stops.size < 2) return null
    var bestI = 0
    var bestD = Double.MAX_VALUE
    step.stops.forEachIndexed { i, p ->
        val d = haversine(f.lat, f.lng, p.first, p.second)
        if (d < bestD) {
            bestD = d
            bestI = i
        }
    }
    return (step.stops.size - 1 - bestI).coerceAtLeast(0)
}

/** Stan prowadzenia dla pozycji [fix]: który krok, ile zostało, czy wysiadać. */
data class NavState(
    /** indeks aktywnego kroku */
    val stepIndex: Int,
    /** odległość do celu kroku (m) albo null */
    val distanceToTarget: Double?,
    /** jazda: ile przystanków do wysiadki (0 = teraz) */
    val stopsLeft: Int?,
    /** „wysiądź teraz” — jesteś przy przystanku wysiadki */
    val getOffNow: Boolean,
    /** „za chwilę Twoja wysiadka” — jeden przystanek / kilkaset metrów */
    val getOffSoon: Boolean,
    /** cała podróż za nami */
    val finished: Boolean,
)

/**
 * Kroki, których cel już osiągnięto, są pomijane (dotyczy to także startu —
 * gdy stoisz na przystanku, marsz do niego nie blokuje prowadzenia).
 *
 * [fromIndex] to krok, na którym skończyliśmy poprzednie wyliczenie. Prowadzenie
 * nie cofa się: gdy autobus odjechał z przystanku, marsz do niego nie wraca,
 * mimo że GPS jest już od niego daleko.
 */
fun navState(steps: List<NavStep>, fix: Fix?, fromIndex: Int = 0): NavState {
    if (steps.isEmpty()) return NavState(0, null, null, false, false, true)

    var index = fromIndex.coerceIn(0, steps.lastIndex)
    while (index < steps.lastIndex) {
        val here = navDistance(steps[index], fix)
        val next = navDistance(steps[index + 1], fix)
        val reached = (here != null && here <= NAV_ARRIVE_M) ||
            (next != null && next <= NAV_ARRIVE_M)
        if (reached) index++ else break
    }

    val step = steps[index]
    val target = navDistance(step, fix)
    val last = index == steps.lastIndex
    val arrived = target != null && target <= NAV_ARRIVE_M

    // po wysiadce jesteśmy już na kroku marszu, ale jeszcze przy drzwiach —
    // przez pierwsze metry nadal warto wołać „wysiądź teraz”
    val leftRide = steps.getOrNull(index - 1) as? NavStep.Ride
    val justGotOff = leftRide != null && !arrived &&
        navDistance(leftRide, fix)?.let { it <= NAV_GET_OFF_M } == true

    // jadąc, odległość do wysiadki ma sens dopiero z ostatnich metrów;
    // wcześniej liczy się liczba przystanków, nie kilometry
    val distance = when (step) {
        is NavStep.Walk -> target
        is NavStep.Ride -> target?.takeIf { it <= NAV_SOON_M }
    }
    val stopsLeft = (step as? NavStep.Ride)?.let { rideStopsLeft(it, fix) } ?: if (justGotOff) 0 else null
    val getOffNow = (step is NavStep.Ride && arrived) || justGotOff
    val getOffSoon = !getOffNow && step is NavStep.Ride && stopsLeft != null && stopsLeft <= 1 &&
        (target != null && target <= NAV_SOON_M)

    return NavState(
        stepIndex = index,
        distanceToTarget = distance,
        stopsLeft = stopsLeft,
        getOffNow = getOffNow,
        getOffSoon = getOffSoon,
        finished = last && arrived,
    )
}

/**
 * Kierunek ruchu (0..360) wyliczony z dwóch pozycji — GPS nie na każdym
 * urządzeniu podaje bearing, a strzałka na mapie i tak potrzebuje kursu.
 */
fun headingDeg(from: Pair<Double, Double>, to: Pair<Double, Double>): Float? {
    if (haversine(from.first, from.second, to.first, to.second) < 3.0) return null
    val lat1 = from.first * Math.PI / 180
    val lat2 = to.first * Math.PI / 180
    val dLng = (to.second - from.second) * Math.PI / 180
    val y = sin(dLng) * cos(lat2)
    val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLng)
    return (((Math.toDegrees(atan2(y, x)) + 360.0) % 360.0)).toFloat()
}
