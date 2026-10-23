package com.kiedziedojade.app

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import com.kiedziedojade.app.core.DataRepo
import com.kiedziedojade.app.core.I18n
import com.kiedziedojade.app.core.Lang
import com.kiedziedojade.app.core.Prefs
import com.kiedziedojade.app.ui.KdApp
import com.kiedziedojade.app.ui.theme.AppTheme
import com.kiedziedojade.app.ui.theme.KiedyDojadeTheme
import com.kiedziedojade.app.ui.theme.appThemeOf
import com.kiedziedojade.app.update.isOnline

class MainActivity : ComponentActivity() {

    /** Skrót z launchera: 0 = Szukaj, 1 = Tablica, 2 = Mapa. */
    private val tabRequest = mutableStateOf<Int?>(null)
    /** Powiadomienie śledzenia: pokaż ekran prowadzenia, także gdy aktywność już żyje. */
    private val trackingRequest = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = Prefs(this)
        val repo = DataRepo(this)
        I18n.lang = if (prefs.lang == "en") Lang.EN else Lang.PL
        // Odtworzenie po obrocie nie może ponownie wykonać skrótu z launchera
        // ani otworzyć prowadzenia, które użytkownik wcześniej ukrył.
        if (savedInstanceState == null) {
            tabRequest.value = tabFromIntent(intent)
            trackingRequest.value = intent?.action == ACTION_OPEN_TRACKING
        }

        setContent {
            var themeName by rememberSaveable { mutableStateOf(prefs.theme) }
            val theme = appThemeOf(themeName)
            val systemDark = isSystemInDarkTheme()

            // Tło okna + jasne/ciemne ikony pasków — bez białego flesza przy starcie.
            LaunchedEffect(theme, systemDark) {
                val dark = when (theme) {
                    AppTheme.SYSTEM -> systemDark
                    AppTheme.MLEKO -> false
                    else -> true
                }
                val windowTheme = when {
                    theme != AppTheme.SYSTEM -> theme
                    systemDark -> AppTheme.MONO
                    else -> AppTheme.MLEKO
                }
                window.setBackgroundDrawable(ColorDrawable(windowTheme.windowBg.toInt()))
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.isAppearanceLightStatusBars = !dark
                controller.isAppearanceLightNavigationBars = !dark
            }

            KiedyDojadeTheme(theme) {
                KdApp(
                    prefs = prefs,
                    repo = repo,
                    appTheme = theme,
                    onThemeChange = {
                        prefs.theme = it
                        themeName = it
                    },
                    openTab = tabRequest.value,
                    onOpenTabHandled = { tabRequest.value = null },
                    openTracking = trackingRequest.value,
                    onOpenTrackingHandled = { trackingRequest.value = false },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        tabFromIntent(intent)?.let { tabRequest.value = it }
        if (intent.action == ACTION_OPEN_TRACKING) trackingRequest.value = true
    }

    private fun tabFromIntent(intent: Intent?): Int? = when (intent?.action) {
        ACTION_OPEN_SEARCH -> 0
        ACTION_OPEN_BOARD -> 1
        ACTION_OPEN_MAP -> 2
        else -> null
    }

    companion object {
        const val ACTION_OPEN_SEARCH = "com.kiedziedojade.app.action.OPEN_SEARCH"
        const val ACTION_OPEN_BOARD = "com.kiedziedojade.app.action.OPEN_BOARD"
        const val ACTION_OPEN_MAP = "com.kiedziedojade.app.action.OPEN_MAP"
        const val ACTION_OPEN_TRACKING = "com.kiedziedojade.app.action.OPEN_TRACKING"
    }
}
