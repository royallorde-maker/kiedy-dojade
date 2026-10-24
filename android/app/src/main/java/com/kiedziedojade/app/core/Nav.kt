package com.kiedziedojade.app.core

import java.time.Instant
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
    /** dokładność kierunku w stopniach (null = system jej nie podał) */
    val bearingAccuracy: Float? = null,
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

/** GPS o gorszej dokładności nie może przesuwać prowadzenia ani zaliczać kroków. */
const val MAX_FIX_ACCURACY_M = 120f

/**
 * Fix farther than this from every meaningful point of the itinerary is not
 * the passenger we planned for (emulator/default location, mock GPS). Without
 * this guard the walk card happily showed 11,419 km and 146,399 minutes.
 */
const val MAX_NAV_FIX_DISTANCE_M = 50_000.0

/** True when [fix] is close enough to the planned journey to drive navigation. */
fun navFixPlausible(itinerary: Itinerary, fix: Fix): Boolean {
    val anchors = buildList {
        itinerary.firstRide?.boardStop?.let { add(it.lat to it.lng) }
        itinerary.lastRide?.alightStop?.let { add(it.lat to it.lng) }
        (itinerary.legs.firstOrNull() as? WalkLeg)?.fromPt?.let { add(it) }
        (itinerary.legs.lastOrNull() as? WalkLeg)?.toPt?.let { add(it) }
    }
    if (anchors.isEmpty()) return true
    return anchors.any {
        haversine(fix.lat, fix.lng, it.first, it.second) <= MAX_NAV_FIX_DISTANCE_M
    }
}

/** Po tylu minutach od planowego przyjazdu porzucone śledzenie gasimy. */
const val TRACKING_GRACE_MIN = 180

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

/**
 * Indeks następnego przystanku kursu po najbliższym, wg planu. Używany jako
 * zapas, gdy miasto nie podaje jeszcze wykonania kursu na żywo.
 */
fun rideNextStopIndex(step: NavStep.Ride, fix: Fix?): Int? {
    val left = rideStopsLeft(step, fix) ?: return null
    if (left <= 0) return null
    return (step.stops.size - left).takeIf { it in step.stops.indices }
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
            // Nie przeskakuj jazdy tylko dlatego, że autobus mija cel
            // późniejszego marszu. Na przystanku wysiadki i tak zaliczy ją
            // odległość do samego przystanku.
            (steps[index] !is NavStep.Ride && next != null && next <= NAV_ARRIVE_M)
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
        navDistance(leftRide, fix)?.let { it <= NAV_ARRIVE_M } == true

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
 * Czy porzucone śledzenie powinno się już zakończyć?
 *
 * [arriveMin] to godzina zegarowa przyjazdu, więc bierzemy jej najbliższe
 * wystąpienie po starcie. Dzięki temu podróż przez północ i późny przyjazd
 * (np. 22:50, sprawdzany o 02:00) wygasają po trzech godzinach, a nie po
 * osiemnastu.
 */
fun trackingExpired(
    startedAtMs: Long,
    arriveMin: Int,
    nowMs: Long = System.currentTimeMillis(),
): Boolean {
    if (startedAtMs <= 0L) return false
    val start = Instant.ofEpochMilli(startedAtMs).atZone(WARSAW)
    val startMin = start.hour * 60 + start.minute
    val minutesToArrival = ((arriveMin - startMin) % 1440 + 1440) % 1440
    val deadline = startedAtMs + (minutesToArrival + TRACKING_GRACE_MIN) * 60_000L
    return nowMs > deadline
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

/** Normalizuje kierunek do zakresu 0..360. */
fun normalizeHeading(deg: Float): Float {
    val wrapped = deg % 360f
    return if (wrapped < 0f) wrapped + 360f else wrapped
}

/**
 * Najkrótsza różnica kątów od [from] do [to] w zakresie -180..180.
 * Używana przy wygładzaniu strzałki, żeby nie kręciła się przez 359°.
 */
fun headingDelta(from: Float, to: Float): Float {
    val d = (to - from) % 360f
    return when {
        d > 180f -> d - 360f
        d <= -180f -> d + 360f
        else -> d
    }
}

/**
 * Kierunek jazdy z GPS: bearing urządzenia, gdy naprawdę się poruszamy,
 * a bez niego różnica dwóch pozycji. W miejscu zwraca null, żeby szum GPS
 * nie obracał znacznika — wtedy ekran używa kompasu.
 */
fun courseHeading(fix: Fix?, previous: Fix?): Float? {
    val f = fix ?: return null
    val speed = f.speed
    if (speed != null && speed < 0.8f) return null

    val p = previous
    val meters = if (p == null) 0.0 else {
        haversine(p.lat, p.lng, f.lat, f.lng)
    }
    // bearing jest wiarygodny dopiero w ruchu; przy braku prędkości
    // wymagamy wyraźnego przemieszczenia między dwiema ramkami
    val canUseBearing = (speed != null && speed >= 0.8f) ||
        (speed == null && meters >= 8.0)
    val b = f.bearing
    val bAcc = f.bearingAccuracy
    if (canUseBearing && b != null && (bAcc == null || bAcc <= 45f)) {
        return normalizeHeading(b)
    }

    if (p == null || meters < 3.0) return null
    if (speed == null && meters < 8.0) return null
    val dt = f.timeMs - p.timeMs
    if (dt > 60_000L) return null
    return headingDeg(p.lat to p.lng, f.lat to f.lng)
}

/**
 * Kierunek znacznika: w ruchu kurs GPS, w miejscu kompas telefonu, a gdy
 * kompas chwilowo nie ma danych — ostatni znany kurs.
 */
fun markerHeading(course: Float?, compass: Float?, lastCourse: Float?): Float? = when {
    course != null -> normalizeHeading(course)
    compass != null -> normalizeHeading(compass)
    lastCourse != null -> normalizeHeading(lastCourse)
    else -> null
}

/**
 * Kierunek góry ekranu z azymutu czujnika. [displayRotation] to stała
 * Surface.ROTATION_*; w landscape trzeba obrócić azymut o 90/180/270°.
 */
fun screenCompassHeading(azimuthDeg: Float, displayRotation: Int): Float =
    normalizeHeading(azimuthDeg + displayRotation.coerceIn(0, 3) * 90f)
