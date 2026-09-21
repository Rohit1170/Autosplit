package com.example.splitwiseauto

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

data class LocationSnapshot(
    val latitude: Double,
    val longitude: Double
)

object LocationHelper {

    private const val LOCATION_PREFS = "location_prefs"
    private const val LAST_LATITUDE = "last_latitude"
    private const val LAST_LONGITUDE = "last_longitude"

    fun hasLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun getLastKnownLocation(context: Context): LocationSnapshot? {
        val prefs = context.applicationContext.getSharedPreferences(LOCATION_PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains(LAST_LATITUDE) || !prefs.contains(LAST_LONGITUDE)) return null
        return LocationSnapshot(
            latitude = Double.fromBits(prefs.getLong(LAST_LATITUDE, 0L)),
            longitude = Double.fromBits(prefs.getLong(LAST_LONGITUDE, 0L))
        )
    }

    fun saveLastKnownLocation(context: Context, location: LocationSnapshot) {
        context.applicationContext.getSharedPreferences(LOCATION_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(LAST_LATITUDE, location.latitude.toBits())
            .putLong(LAST_LONGITUDE, location.longitude.toBits())
            .apply()
    }

    /**
     * Returns null for a missing permission, disabled provider, or any unavailable location.
     *
     * The permission check happens one line below via hasLocationPermission(context), but
     * Android's lint tool can't trace into a custom helper function to see that, so it flags
     * the calls below as if no check exists at all. @SuppressLint documents that this is a
     * known false positive rather than hiding a real gap — the runCatching below still
     * protects against a permission being revoked mid-call (e.g. the user flips it off in
     * Settings while this coroutine is suspended on the location call).
     */
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(context: Context): LocationSnapshot? {
        if (!hasLocationPermission(context)) return null

        return withTimeoutOrNull(6_000) { runCatching {
            val client = LocationServices.getFusedLocationProviderClient(context.applicationContext)
            val location = client.lastLocation.await()
                ?: client.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    CancellationTokenSource().token
                ).await()
            location?.let { LocationSnapshot(it.latitude, it.longitude) }
        }.getOrNull() }
    }
}
