package de.mm20.launcher2.devicepose

import android.Manifest
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import android.util.Log
import androidx.core.content.getSystemService
import androidx.core.location.LocationListenerCompat
import de.mm20.launcher2.ktx.PI
import de.mm20.launcher2.ktx.checkPermission
import de.mm20.launcher2.ktx.declination
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.channelFlow
import de.mm20.launcher2.ktx.isBetterThan
import kotlinx.coroutines.flow.combine
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class DevicePoseProvider internal constructor(
    private val context: Context
) {
    private val lastLocationLock = ReentrantReadWriteLock()
    var lastCachedLocation: Location? = null
        get() { return lastLocationLock.read { field } }
        private set(value) {
            if (value == null) return
            lastLocationLock.write {
                if (value.isBetterThan(field)) {
                    field = value
                }
            }
        }

    /**
     * @param skipCache: when using `getLocation().firstOrNull()`, prefer `skipCache = false`,
     *                   since otherwise, you may only receive an out of date location
     */
    fun getLocation(minTimeMs: Long = 1000, minDistanceM: Float = 1f, skipCache: Boolean = false) = channelFlow {
        // have a local copy to work with
        var localLastLocation = lastCachedLocation

        fun updateLocation(update: Location) {
            if (!update.isBetterThan(localLastLocation)) return
            localLastLocation = update
            trySend(update)
        }

        val locationCallback = LocationListenerCompat {
            updateLocation(it)
        }

        if (!skipCache && localLastLocation != null) {
            trySend(localLastLocation)
        }

        context.getSystemService<LocationManager>()
            ?.runCatching {
                val hasFineAccess =
                    context.checkPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                val hasCoarseAccess =
                    context.checkPermission(Manifest.permission.ACCESS_COARSE_LOCATION)

                // On some devices, only the fused or passive provider has a last known location.
                // Without one, callers (e.g. location search) wait for a live fix that may never
                // come, e.g. indoors.
                val providers = buildList {
                    if (hasFineAccess) add(LocationManager.GPS_PROVIDER)
                    if (hasCoarseAccess) add(LocationManager.NETWORK_PROVIDER)
                    if ((hasFineAccess || hasCoarseAccess) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        add(LocationManager.FUSED_PROVIDER)
                    }
                    if (hasFineAccess) add(LocationManager.PASSIVE_PROVIDER)
                }
                val previousLocation = providers
                    .mapNotNull { runCatching { getLastKnownLocation(it) }.getOrNull() }
                    .reduceOrNull { best, location -> if (location.isBetterThan(best)) location else best }

                if (previousLocation != null) {
                    updateLocation(previousLocation)
                }

                // Pass a looper explicitly, the flow is not necessarily collected on a looper
                // thread (e.g. in WeatherUpdateWorker), and requestLocationUpdates would throw.
                if (hasFineAccess) {
                    requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        minTimeMs,
                        minDistanceM,
                        locationCallback,
                        Looper.getMainLooper(),
                    )
                }
                if (hasCoarseAccess) {
                    requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        minTimeMs,
                        minDistanceM,
                        locationCallback,
                        Looper.getMainLooper(),
                    )
                }
            }?.onFailure {
                Log.e("DevicePoseProvider", "Failed to register location listener", it)
            }

        awaitClose {
            context.getSystemService<LocationManager>()?.removeUpdates(locationCallback)
            lastCachedLocation = localLastLocation
        }
    }

    fun getAzimuthDegrees(samplingPeriodUs: Int = SensorManager.SENSOR_DELAY_UI): Flow<Float> =
        callbackFlow {
            val azimuthCallback = object : SensorEventListener {
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
                override fun onSensorChanged(event: SensorEvent?) {
                    if (event?.sensor?.type != Sensor.TYPE_ROTATION_VECTOR)
                        return

                    val rotationMatrix = FloatArray(9)
                    val orientationAngles = FloatArray(3)

                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    SensorManager.getOrientation(rotationMatrix, orientationAngles)

                    trySend(orientationAngles[0] * 180f / Float.PI + (lastCachedLocation?.declination ?: 0f))
                }
            }

            context
                .getSystemService<SensorManager>()
                ?.runCatching {
                    this@runCatching.registerListener(
                        azimuthCallback,
                        this@runCatching.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
                            ?: return@runCatching,
                        samplingPeriodUs
                    )
                }?.onFailure {
                    Log.e("DevicePoseProvider", "Failed to register ROTATION_VECTOR listener", it)
                }

            awaitClose {
                context.getSystemService<SensorManager>()?.unregisterListener(azimuthCallback)
            }
        }

    fun getHeadingToDegrees(
        headingEastwardDegrees: Float,
        samplingPeriodUs: Int = SensorManager.SENSOR_DELAY_UI
    ): Flow<Float> = combine(
        getAzimuthDegrees(samplingPeriodUs),
        callbackFlow {
            val upsideDownCallback = object : SensorEventListener {
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
                override fun onSensorChanged(event: SensorEvent?) {
                    if (event?.sensor?.type != Sensor.TYPE_GRAVITY)
                        return

                    val (_, _, z) = event.values
                    trySend(z < 0f)
                }
            }
            context
                .getSystemService<SensorManager>()
                ?.runCatching {
                    this@runCatching.registerListener(
                        upsideDownCallback,
                        this@runCatching.getDefaultSensor(Sensor.TYPE_GRAVITY)
                            ?: return@runCatching,
                        samplingPeriodUs
                    )
                }?.onFailure {
                    Log.e("SearchableItemVM", "Failed to register GRAVITY listener", it)
                }

            awaitClose {
                context.getSystemService<SensorManager>()?.unregisterListener(upsideDownCallback)
            }
        }) { azimuthDegrees, isUpsideDown ->

        if (isUpsideDown) {
            azimuthDegrees - headingEastwardDegrees
        } else {
            headingEastwardDegrees - azimuthDegrees
        }
    }
}
