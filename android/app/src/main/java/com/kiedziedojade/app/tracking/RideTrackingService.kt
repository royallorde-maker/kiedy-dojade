package com.kiedziedojade.app.tracking

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.kiedziedojade.app.MainActivity
import com.kiedziedojade.app.R
import com.kiedziedojade.app.core.Api
import com.kiedziedojade.app.core.Fix
import com.kiedziedojade.app.core.I18n
import com.kiedziedojade.app.core.I18n.t
import com.kiedziedojade.app.core.Itinerary
import com.kiedziedojade.app.core.ItineraryJson
import com.kiedziedojade.app.core.Lang
import com.kiedziedojade.app.core.Locator
import com.kiedziedojade.app.core.Prefs
import com.kiedziedojade.app.core.RideLeg
import com.kiedziedojade.app.core.TrackPhase
import com.kiedziedojade.app.core.TrackSnapshot
import com.kiedziedojade.app.core.fmtMeters
import com.kiedziedojade.app.core.fmtTime
import com.kiedziedojade.app.core.haversine
import com.kiedziedojade.app.core.matchLiveRide
import com.kiedziedojade.app.core.nowMinutes
import com.kiedziedojade.app.core.trackingSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service, który prowadzi aktywną podróż także po wyjściu z aplikacji.
 *
 * Odpowiada za:
 *  - nasłuch GPS i dopasowanie pozycji do planu,
 *  - kurs na żywo (pozycja autobusu, opóźnienie, przystanki),
 *  - wykrycie, że pasażer rzeczywiście jedzie autobusem,
 *  - stałe powiadomienie z linią, następnym przystankiem i wysiadką.
 *
 * Stan jest zapisany w [Prefs], więc po zabiciu procesu usługa wstaje z tym
 * samym połączeniem, a powrót do aplikacji odtwarza ekran prowadzenia.
 */
class RideTrackingService : Service() {

    private lateinit var prefs: Prefs
    private lateinit var locator: Locator
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var itinerary: Itinerary? = null
    private var fix: Fix? = null
    private var stepIndex = 0
    private var nowMin = nowMinutes()

    private var liveRow: Api.LiveRow? = null
    private var liveExec: Api.TripExec? = null
    private var execId: String? = null
    private var execIndex = 0
    private var execRideIndex: Int? = null
    private var liveFails = 0

    private var started = false
    private var stopping = false
    private var persistedStep = -1
    private var lastTitle: String? = null
    private var lastText: String? = null
    private var lastNotifyAt = 0L

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        locator = Locator(this)
        I18n.lang = if (prefs.lang == "en") Lang.EN else Lang.PL
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                finishTracking(arrived = false)
                return START_NOT_STICKY
            }

            else -> {
                val incoming = intent?.getStringExtra(EXTRA_ITINERARY)
                    ?.let { ItineraryJson.decode(it) }
                    ?: prefs.activeItinerary
                if (incoming == null) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                // bez uprawnienia do lokalizacji nie wolno startować FGS typu location
                if (!locator.hasPermission()) {
                    stopSelf()
                    return START_NOT_STICKY
                }

                if (itinerary?.id != incoming.id) {
                    itinerary = incoming
                    stepIndex = prefs.activeStepIndex
                    persistedStep = stepIndex
                    fix = null
                    resetLive()
                    lastTitle = null
                    lastText = null
                }
                if (itinerary == null) itinerary = incoming

                // startForeground musi nastąpić natychmiast po startForegroundService
                startInForeground(computeSnapshot())
                if (!started) {
                    started = true
                    scope.launch { locationLoop() }
                    scope.launch { liveLoop() }
                    scope.launch { tickerLoop() }
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        if (!stopping) {
            runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        }
        super.onDestroy()
    }

    // ------------------------------------------------------------------ loops

    private suspend fun locationLoop() {
        while (scope.isActive) {
            if (!locator.hasPermission()) {
                publish()
                delay(2_000)
                continue
            }
            locator.stream(minTimeMs = 4_000L, minDistanceM = 8f).collect { f ->
                // zbyt niedokładna ramka nie powinna przesuwać prowadzenia
                if (f.accuracy != null && f.accuracy > 150f) return@collect
                if (f.timeMs > 0 && System.currentTimeMillis() - f.timeMs > 10 * 60_000L) {
                    return@collect
                }
                fix = f
                publish()
            }
            delay(1_500)
        }
    }

    private suspend fun liveLoop() {
        while (scope.isActive) {
            if (stopping) return
            val trip = itinerary
            if (trip == null) {
                delay(2_000)
                continue
            }
            val snap = computeSnapshot()
            if (snap.finished) {
                finishTracking(arrived = true, snap)
                return
            }
            val rideIndex = snap.rideLegIndex
            val ride = rideIndex?.let { trip.legs.getOrNull(it) as? RideLeg }
            if (ride == null) {
                liveRow = null
                liveExec = null
                publish()
                delay(15_000)
                continue
            }
            if (execRideIndex != rideIndex) {
                resetLive()
                execRideIndex = rideIndex
            }

            if (execId == null) {
                val rows = runCatching { Api.stopBoard(ride.boardStop.id) }
                    .getOrDefault(emptyList())
                val row = matchLiveRide(rows, ride)
                if (row != null) {
                    liveRow = row
                    execId = row.tripExecutionId
                    execIndex = row.index ?: 0
                }
            }

            val id = execId
            if (id != null) {
                val ex = runCatching { Api.tripExecution(id, execIndex) }.getOrNull()
                if (ex != null) {
                    liveFails = 0
                    liveExec = ex
                } else if (++liveFails >= 3) {
                    resetLive()
                    execRideIndex = rideIndex
                }
            }
            publish()

            val prox = if (liveExec?.vehicle != null && fix != null) {
                haversine(fix!!.lat, fix!!.lng, liveExec!!.vehicle!!.first, liveExec!!.vehicle!!.second)
            } else {
                null
            }
            val wait = when {
                liveExec?.atStop == true -> 7_000L
                prox != null && prox < 1_200.0 -> 10_000L
                else -> 15_000L
            }
            delay(wait)
        }
    }

    private suspend fun tickerLoop() {
        while (scope.isActive) {
            delay(20_000)
            nowMin = nowMinutes()
            val trip = itinerary
            val active = prefs.activeItinerary
            if (trip == null || active == null || active.id != trip.id) {
                finishTracking(arrived = false)
                return
            }
            // porzucone śledzenie nie ma prawa trzymać GPS przez całą dobę
            if (nowMin > trip.arriveAt + 180) {
                finishTracking(arrived = false)
                return
            }
            publish()
        }
    }

    // --------------------------------------------------------------- snapshot

    private fun computeSnapshot(): TrackSnapshot {
        val trip = itinerary ?: return emptySnapshot()
        return trackingSnapshot(
            itinerary = trip,
            fix = fix,
            nowMin = nowMin,
            fromStep = stepIndex,
            liveExec = liveExec,
            liveRow = liveRow,
        ).also {
            stepIndex = it.stepIndex
            if (it.stepIndex != persistedStep) {
                persistedStep = it.stepIndex
                prefs.activeStepIndex = it.stepIndex
            }
        }
    }

    private fun emptySnapshot(): TrackSnapshot = TrackSnapshot(
        phase = TrackPhase.WALKING,
        stepIndex = 0,
        rideLegIndex = null,
        line = null,
        direction = null,
        boardStop = null,
        alightStop = null,
        nextStop = null,
        walkTo = null,
        walkMeters = null,
        stopsLeft = null,
        stopsToBoard = null,
        boardInMin = null,
        boardClock = null,
        alightClock = null,
        delayMin = 0,
        canceled = false,
        onBus = false,
        waiting = false,
        missed = false,
        live = false,
        busAtBoard = false,
        finished = false,
        etaMin = 0,
    )

    private fun publish() {
        if (stopping) return
        nowMin = nowMinutes()
        val snap = computeSnapshot()
        if (snap.finished) {
            finishTracking(arrived = true, snap = snap)
            return
        }
        startInForeground(snap, updateOnly = started && lastTitle != null)
    }

    private fun resetLive() {
        liveRow = null
        liveExec = null
        execId = null
        execIndex = 0
        liveFails = 0
    }

    // ----------------------------------------------------------- notification

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            t("Śledzenie podróży", "Trip tracking"),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = t(
                "Linia, następny przystanek i wysiadka podczas jazdy.",
                "Line, next stop and get-off point while riding.",
            )
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        nm.createNotificationChannel(channel)
    }

    private fun startInForeground(
        snap: TrackSnapshot,
        updateOnly: Boolean = false,
    ) {
        val notification = buildNotification(snap, ongoing = true)
        if (updateOnly) {
            val title = notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            val text = notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            val now = System.currentTimeMillis()
            if (title == lastTitle && text == lastText && now - lastNotifyAt < 10_000L) return
            lastTitle = title
            lastText = text
            lastNotifyAt = now
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS,
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            runCatching { NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification) }
            return
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
        lastTitle = notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        lastText = notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        lastNotifyAt = System.currentTimeMillis()
    }

    private fun buildNotification(snap: TrackSnapshot, ongoing: Boolean): Notification {
        val (title, text) = notificationText(snap)
        val open = Intent(this, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_TRACKING
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPi = PendingIntent.getActivity(
            this,
            REQ_OPEN,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopPi = PendingIntent.getService(
            this,
            REQ_STOP,
            Intent(this, RideTrackingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_bus)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openPi)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setSilent(true)
            .setOngoing(ongoing)
            .setAutoCancel(!ongoing)

        if (ongoing) {
            builder.addAction(
                R.drawable.ic_notification_stop,
                t("Zakończ", "Finish"),
                stopPi,
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                builder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            }
        }
        return builder.build()
    }

    private fun notificationText(snap: TrackSnapshot): Pair<String, String> {
        val line = snap.line
        if (itinerary != null && !locator.hasPermission()) {
            return t("Śledzenie podróży", "Trip tracking") to
                t("Brak dostępu do lokalizacji — dotknij, aby wrócić", "No location access — tap to return")
        }
        return when (snap.phase) {
            TrackPhase.FINISHED -> t("Jesteś na miejscu", "You have arrived") to
                t("Podróż zakończona", "Trip finished")

            TrackPhase.ON_BUS -> {
                val title = if (line != null) {
                    t("Linia ", "Line ") + line + t(" · jedziesz", " · on board")
                } else {
                    t("Jedziesz autobusem", "On the bus")
                }
                val parts = ArrayList<String>()
                snap.alightStop?.let { parts += t("Wysiadka: ", "Get off: ") + it }
                val left = snap.stopsLeft
                if (left != null) {
                    parts += if (left <= 0) {
                        t("wysiadka teraz", "get off now")
                    } else {
                        t("za ", "in ") + left + " " + I18n.stops(left)
                    }
                } else {
                    snap.nextStop?.let { parts += t("następny: ", "next: ") + it }
                }
                if (snap.delayMin != 0) {
                    parts += if (snap.delayMin > 0) "+${snap.delayMin} min" else "${snap.delayMin} min"
                }
                title to parts.joinToString(" · ")
            }

            TrackPhase.WAITING -> {
                val title = if (line != null) {
                    t("Linia ", "Line ") + line + t(" · czekasz", " · waiting")
                } else {
                    t("Czekasz na autobus", "Waiting for the bus")
                }
                val parts = ArrayList<String>()
                when {
                    snap.canceled -> parts += t("Kurs odwołany — sprawdź tablicę", "Trip canceled — check the board")
                    snap.missed -> parts += t("Autobus odjechał — sprawdź tablicę", "Bus left — check the board")
                    snap.busAtBoard -> {
                        snap.boardStop?.let { parts += it }
                        parts += t("autobus na przystanku", "bus at the stop")
                    }
                    else -> {
                        snap.boardStop?.let { parts += it }
                        snap.boardClock?.let { parts += t("odjazd ", "departs ") + fmtTime(it) }
                        snap.boardInMin?.takeIf { it > 0 }?.let {
                            parts += t("za ", "in ") + it + " " + I18n.minutes(it)
                        }
                        snap.stopsToBoard?.takeIf { it in 1..12 }?.let { n ->
                            parts += t("autobus ", "bus ") + n + " " + I18n.stops(n) + t(" stąd", " away")
                        }
                    }
                }
                if (parts.isEmpty()) parts += t("Ustalam pozycję…", "Finding your position…")
                title to parts.joinToString(" · ")
            }

            TrackPhase.WALKING -> {
                val title = snap.walkTo?.let { t("Pieszo do ", "Walk to ") + it }
                    ?: t("Śledzenie podróży", "Trip tracking")
                val parts = ArrayList<String>()
                snap.walkMeters?.let { parts += fmtMeters(it) }
                if (line != null) parts += t("potem linia ", "then line ") + line
                snap.boardClock?.let { if (line != null) parts += t("odjazd ", "departs ") + fmtTime(it) }
                if (parts.isEmpty()) parts += t("Ustalam pozycję…", "Finding your position…")
                title to parts.joinToString(" · ")
            }
        }
    }

    // ------------------------------------------------------------------ finish

    private fun finishTracking(arrived: Boolean, snap: TrackSnapshot? = null) {
        if (stopping) return
        stopping = true
        scope.cancel()
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        prefs.stopRideTracking()
        if (arrived) {
            val notification = buildNotification(snap ?: computeSnapshot(), ongoing = false)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS,
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                runCatching { NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification) }
            }
        }
        stopSelf()
    }

    companion object {
        const val ACTION_START = "com.kiedziedojade.app.action.START_TRACKING"
        const val ACTION_STOP = "com.kiedziedojade.app.action.STOP_TRACKING"
        private const val EXTRA_ITINERARY = "com.kiedziedojade.app.extra.ITINERARY"
        private const val CHANNEL_ID = "ride_tracking"
        private const val NOTIFICATION_ID = 4101
        private const val REQ_OPEN = 4102
        private const val REQ_STOP = 4103

        /** Uruchamia śledzenie; zapis trasy musi być widoczny także po restarcie procesu. */
        fun start(context: Context, itinerary: Itinerary) {
            val prefs = Prefs(context)
            // nie kasuj postępu, gdy usługa już prowadzi tę samą podróż
            if (prefs.activeItinerary?.id != itinerary.id) {
                prefs.startRideTracking(itinerary)
            }
            // Uprawnienie do lokalizacji może dopiero pojawić się po dialogu.
            // Wtedy App wznawia usługę przy ON_RESUME; bez niego FGS typu
            // location rzuciłby SecurityException.
            if (!Locator(context).hasPermission()) return
            val intent = Intent(context, RideTrackingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_ITINERARY, ItineraryJson.encode(itinerary))
            }
            ContextCompat.startForegroundService(context, intent)
        }

        /** Zatrzymuje śledzenie z ekranu prowadzenia. */
        fun stop(context: Context) {
            Prefs(context).stopRideTracking()
            context.stopService(Intent(context, RideTrackingService::class.java))
        }
    }
}
