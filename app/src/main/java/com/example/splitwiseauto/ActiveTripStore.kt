package com.example.splitwiseauto

import android.content.Context

/**
 * Remembers which trip (if any) is currently "active" on this device, so the
 * Home/Insights/Map screens and new-expense saving all agree on the current
 * mode without needing to be wired through a shared ViewModel.
 *
 * null id == "Main" mode (no trip filter, everything, exactly as before).
 *
 * Viewing vs recording: you can look at a CLOSED trip, but new expenses
 * must never join it. [getRecordingTripId] is what every save path uses —
 * it returns null (Main) while a closed trip is being viewed.
 */
object ActiveTripStore {
    private const val PREFS_NAME = "active_trip_prefs"
    private const val KEY_TRIP_ID = "active_trip_id"
    private const val KEY_TRIP_NAME = "active_trip_name"
    private const val KEY_TRIP_CLOSED = "active_trip_closed"

    fun getActiveTripId(context: Context): Int? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val id = prefs.getInt(KEY_TRIP_ID, -1)
        return if (id == -1) null else id
    }

    fun getActiveTripName(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_TRIP_NAME, null)
    }

    /** True when the trip being viewed is closed. */
    fun isActiveTripClosed(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_TRIP_CLOSED, false)

    /** Trip new expenses should be tagged with: null when in Main or viewing a closed trip. */
    fun getRecordingTripId(context: Context): Int? =
        if (isActiveTripClosed(context)) null else getActiveTripId(context)

    /** Name of the trip new expenses go to (null = Main). */
    fun getRecordingTripName(context: Context): String? =
        if (isActiveTripClosed(context)) null else getActiveTripName(context)

    fun setActiveTrip(context: Context, id: Int?, name: String?, closed: Boolean = false) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        if (id == null) {
            editor.putInt(KEY_TRIP_ID, -1)
            editor.remove(KEY_TRIP_NAME)
            editor.putBoolean(KEY_TRIP_CLOSED, false)
        } else {
            editor.putInt(KEY_TRIP_ID, id)
            editor.putString(KEY_TRIP_NAME, name)
            editor.putBoolean(KEY_TRIP_CLOSED, closed)
        }
        editor.apply()
    }

    /** Keep the stored closed flag / name in step after a trip is edited. */
    fun syncWith(context: Context, trip: Trip) {
        if (getActiveTripId(context) == trip.id) setActiveTrip(context, trip.id, trip.name, trip.isClosed)
    }

    /** The viewed trip was deleted: back to Main. */
    fun clearIf(context: Context, tripId: Int) {
        if (getActiveTripId(context) == tripId) setActiveTrip(context, null, null)
    }
}
