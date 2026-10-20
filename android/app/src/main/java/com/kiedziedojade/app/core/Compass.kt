package com.kiedziedojade.app.core

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.view.Surface
import android.view.WindowManager
import kotlin.math.abs

/**
 * Kompas telefonu: azymut góry ekranu (0 = północ) z wektorowego czujnika
 * obrotu, a na starszych urządzeniach z akcelerometru i magnetometru.
 *
 * Wynik jest filtrowany, żeby strzałka nie drgała, i uwzględnia obrót
 * ekranu (landscape). Gdy czujnik zgłasza niską wiarygodność (np. magnes
 * w pobliżu telefonu), ostatnia dobra wartość zostaje bez zmian.
 */
class CompassHeading(
    private val context: Context,
    private val onHeading: (Float) -> Unit,
) : SensorEventListener {

    private val manager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor = manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val geomagneticSensor =
        manager.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
    private val accelerometer = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer = manager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)
    private var gravity: FloatArray? = null
    private var geomagnetic: FloatArray? = null

    private var started = false
    private var reliable = true
    private var smoothed: Float? = null
    private var lastPublishedHeading: Float? = null
    private var lastPublishedAt = 0L

    /** Rejestruje najlepszy dostępny zestaw czujników. */
    fun start(): Boolean {
        if (started) return true
        started = true

        val primary = rotationSensor ?: geomagneticSensor
        if (primary != null) {
            val ok = runCatching {
                manager.registerListener(this, primary, SensorManager.SENSOR_DELAY_UI)
            }.getOrDefault(false)
            if (ok) return true
        }

        if (accelerometer != null && magnetometer != null) {
            val a = runCatching {
                manager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
            }.getOrDefault(false)
            val m = runCatching {
                manager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI)
            }.getOrDefault(false)
            return a && m
        }
        return false
    }

    fun stop() {
        if (!started) return
        started = false
        runCatching { manager.unregisterListener(this) }
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR,
            Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR -> {
                // Niektóre starsze urządzenia dokładają piątą wartość i potrafią
                // wywalić getRotationMatrixFromVector; pierwsze cztery wystarczą.
                val vector = if (event.values.size > 4) {
                    event.values.copyOf(4)
                } else {
                    event.values
                }
                SensorManager.getRotationMatrixFromVector(rotationMatrix, vector)
                publish(rotationMatrix)
            }

            Sensor.TYPE_ACCELEROMETER -> {
                gravity = event.values.copyOf()
                publishFallback()
            }

            Sensor.TYPE_MAGNETIC_FIELD -> {
                geomagnetic = event.values.copyOf()
                publishFallback()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (sensor?.type != Sensor.TYPE_ROTATION_VECTOR &&
            sensor?.type != Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR &&
            sensor?.type != Sensor.TYPE_MAGNETIC_FIELD
        ) {
            return
        }
        reliable = accuracy != SensorManager.SENSOR_STATUS_UNRELIABLE
    }

    private fun publishFallback() {
        val a = gravity ?: return
        val m = geomagnetic ?: return
        if (!SensorManager.getRotationMatrix(rotationMatrix, null, a, m)) return
        publish(rotationMatrix)
    }

    private fun publish(matrix: FloatArray) {
        if (!reliable) return
        SensorManager.getOrientation(matrix, orientation)
        val azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
        val raw = screenCompassHeading(azimuth, displayRotation())
        val previous = smoothed
        val next = if (previous == null) {
            raw
        } else {
            normalizeHeading(previous + headingDelta(previous, raw) * 0.28f)
        }
        smoothed = next

        val now = SystemClock.elapsedRealtime()
        val lastSent = lastPublishedHeading
        val change = if (lastSent == null) 180f else abs(headingDelta(lastSent, next))
        // Drobny szum magnetometru nie ma sensu — przy nieruchomym telefonie
        // nie wysyłamy wtedy nowych wartości i nie odświeżamy całego ekranu.
        // Porównujemy z ostatnio wysłanym kierunkiem, a nie z poprzednią
        // ramką, żeby także powolny obrót w końcu przesunął strzałkę.
        if (lastSent == null || (change >= 0.8f && now - lastPublishedAt >= 80L) || change >= 10f) {
            lastPublishedAt = now
            lastPublishedHeading = next
            onHeading(next)
        }
    }

    @Suppress("DEPRECATION")
    private fun displayRotation(): Int = runCatching {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm.defaultDisplay.rotation
    }.getOrDefault(Surface.ROTATION_0)
}
