package com.example.splitwiseauto

import android.content.Context

/**
 * Remembers which trip (if any) is currently "active" on this device, so the
 * Home/Insights/Map screens and new-expense saving all agree on the current
 * mode without needing to be wired through a shared ViewModel.
 *
 * null id == "Main" mode (no trip filter, everything, exactly as before).
 */
object ActiveTripStore {
    private const val PREFS_NAME = "active_trip_prefs"
    private const val KEY_TRIP_ID = "active_trip_id"
    private const val KEY_TRIP_NAME = "active_trip_name"

    fun getActiveTripId(context: Context): Int? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val id = prefs.getInt(KEY_TRIP_ID, -1)
        return if (id == -1) null else id
    }

    fun getActiveTripName(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_TRIP_NAME, null)
    }

    fun setActiveTrip(context: Context, id: Int?, name: String?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        if (id == null) {
            editor.putInt(KEY_TRIP_ID, -1)
            editor.remove(KEY_TRIP_NAME)
        } else {
            editor.putInt(KEY_TRIP_ID, id)
            editor.putString(KEY_TRIP_NAME, name)
        }
        editor.apply()
    }
}
