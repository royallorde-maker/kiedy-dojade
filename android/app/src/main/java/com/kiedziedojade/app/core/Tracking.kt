package com.kiedziedojade.app.core

/**
 * Stan aktywnej podróży, niezależny od ekranu. Ten sam wynik zasila pasek
 * prowadzenia i powiadomienie foreground service, więc po zamknięciu aplikacji
 * nie trzeba na nowo zgadywać, gdzie jesteśmy.
 */
enum class TrackPhase { WALKING, WAITING, ON_BUS, FINISHED }

data class TrackSnapshot(
    val phase: TrackPhase,
    /** indeks bieżącego kroku prowadzenia (marsz / jazda) */
    val stepIndex: Int,
    /** indeks nogi z autobusem, do której należy aktualny lub najbliższy kurs */
    val rideLegIndex: Int?,
    val line: String?,
    val direction: String?,
    val boardStop: String?,
    val alightStop: String?,
    /** następny przystanek autobusu albo cel marszu */
    val nextStop: String?,
    val walkTo: String?,
    val walkMeters: Double?,
    val stopsLeft: Int?,
    val stopsToBoard: Int?,
    val boardInMin: Int?,
    val boardClock: Int?,
    val alightClock: Int?,
    val delayMin: Int,
    val canceled: Boolean,
    val onBus: Boolean,
    val waiting: Boolean,
    val missed: Boolean,
    val live: Boolean,
    val busAtBoard: Boolean,
    val finished: Boolean,
    /** planowany/przewidywany zegar przyjazdu na koniec podróży */
    val etaMin: Int,
)

/**
 * Wylicza stan podróży z planu, GPS i (opcjonalnie) kursu na żywo.
 *
 * „Jestem w autobusie” potwierdza dopiero ruch względem trasy albo przejazd
 * pojazdu obok pozycji pasażera. Sam autobus stojący na przystanku nie wystarcza
 * — podobnie jak w [navState], które świadomie nie zakłada wsiadania.
 */
fun trackingSnapshot(
    itinerary: Itinerary,
    fix: Fix?,
    nowMin: Int,
    fromStep: Int = 0,
    liveExec: Api.TripExec? = null,
    liveRow: Api.LiveRow? = null,
): TrackSnapshot {
    val steps = navSteps(itinerary)
    val ns = navState(steps, fix, fromStep)
    val step = steps.getOrNull(ns.stepIndex)
    val currentRide = step as? NavStep.Ride
    val upcomingRide = currentRide ?: steps
        .drop(ns.stepIndex + 1)
        .filterIsInstance<NavStep.Ride>()
        .firstOrNull()
    val ride = upcomingRide?.legIndex?.let { itinerary.legs.getOrNull(it) as? RideLeg }
    val rideStep = upcomingRide

    val exec = liveExec?.takeIf { it.times.isNotEmpty() }
    val boardIdx = exec?.indexOfPlace(ride?.boardStop?.id.orEmpty())
    val alightIdx = exec?.indexOfPlace(ride?.alightStop?.id.orEmpty())
    val liveVisible = exec != null && boardIdx != null
    val boardAtStop = liveVisible && exec!!.atStop && exec.nextIndex == boardIdx
    val busPassedBoard = liveVisible && exec!!.passed(boardIdx!!)

    val boardClock = if (liveVisible) exec!!.clockTo(boardIdx!!) else null
    val alightClock = if (liveVisible && alightIdx != null) exec!!.clockTo(alightIdx) else null
    val boardInMin = if (liveVisible) exec!!.minutesTo(boardIdx!!, nowMin) else null
    val liveStopsLeft = if (liveVisible && alightIdx != null && exec!!.hasProgress) {
        exec.stopsTo(alightIdx)
    } else {
        null
    }
    val stopsToBoard = if (liveVisible && exec!!.hasProgress) {
        exec.stopsTo(boardIdx!!)
    } else {
        null
    }

    val liveDelay = if (alightClock != null && ride != null) {
        minuteDiff(alightClock, ride.arrMin).coerceIn(-30, 90)
    } else {
        null
    }
    val delayMin = liveDelay ?: (liveRow?.delayMin ?: 0)
    val canceled = liveRow?.canceled == true || (
        exec != null && (
            (boardIdx != null && boardIdx in exec.canceledStops) ||
                (alightIdx != null && alightIdx in exec.canceledStops)
            )
        )

    val busDist = if (exec?.vehicle != null && fix != null) {
        haversine(fix.lat, fix.lng, exec.vehicle.first, exec.vehicle.second)
    } else {
        null
    }
    val boardDist = if (ride != null && fix != null) {
        haversine(fix.lat, fix.lng, ride.boardStop.lat, ride.boardStop.lng)
    } else {
        null
    }
    val busNear = busDist != null && busDist <= 90.0
    val movedFromBoard = boardDist != null && boardDist > 50.0
    val progressed = currentRide != null && ns.stopsLeft != null &&
        ns.stopsLeft < rideStep!!.stops.size - 1

    // Wsiadanie potwierdza minięcie kolejnego przystanku (GPS) albo kurs
    // na żywo, który odjechał z przystanku i jedzie razem z pasażerem.
    val onBus = !ns.finished && currentRide != null && (
        progressed || (busPassedBoard && busNear && movedFromBoard)
        )
    val missed = currentRide != null && !onBus && busPassedBoard && fix != null && !busNear
    val waiting = currentRide != null && !onBus && !missed && (
        if (liveVisible) !busPassedBoard || busNear
        else nowMin <= rideStep!!.departMin + delayMin
        )

    val phase = when {
        ns.finished -> TrackPhase.FINISHED
        currentRide != null -> if (onBus) TrackPhase.ON_BUS else TrackPhase.WAITING
        step is NavStep.Walk -> TrackPhase.WALKING
        else -> TrackPhase.FINISHED
    }

    val nextStop = when (phase) {
        TrackPhase.ON_BUS -> exec
            ?.takeIf { it.hasProgress }
            ?.times
            ?.getOrNull(exec.nextIndex.coerceIn(0, exec.times.lastIndex))
            ?.name
            ?.takeIf { it.isNotBlank() }
            ?: ride?.alightStop?.name

        TrackPhase.WAITING -> ride?.boardStop?.name
        TrackPhase.WALKING -> (step as? NavStep.Walk)?.toName
        TrackPhase.FINISHED -> null
    }
    val walkMeters = (step as? NavStep.Walk)?.let { w ->
        navDistance(w, fix) ?: w.meters
    }
    val eta = if (ride != null && itinerary.legs.lastOrNull() === ride && alightClock != null) {
        alightClock
    } else {
        itinerary.arriveAt + delayMin
    }

    return TrackSnapshot(
        phase = phase,
        stepIndex = ns.stepIndex,
        rideLegIndex = rideStep?.legIndex,
        line = ride?.line,
        direction = ride?.dir,
        boardStop = ride?.boardStop?.name,
        alightStop = ride?.alightStop?.name,
        nextStop = nextStop,
        walkTo = (step as? NavStep.Walk)?.toName,
        walkMeters = walkMeters,
        stopsLeft = if (onBus) liveStopsLeft ?: ns.stopsLeft else null,
        stopsToBoard = stopsToBoard,
        boardInMin = boardInMin,
        boardClock = boardClock ?: ride?.depMin,
        alightClock = alightClock ?: ride?.arrMin,
        delayMin = delayMin,
        canceled = canceled,
        onBus = onBus,
        waiting = waiting,
        missed = missed,
        live = liveVisible,
        busAtBoard = boardAtStop,
        finished = ns.finished,
        etaMin = eta,
    )
}
