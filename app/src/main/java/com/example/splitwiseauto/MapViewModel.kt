package com.example.splitwiseauto

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MapViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)

    private val _expensesWithLocation = MutableStateFlow<List<Expense>>(emptyList())
    val expensesWithLocation: StateFlow<List<Expense>> = _expensesWithLocation

    /** False until the first load finishes, so the empty state doesn't flash. */
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded

    // Trip mode: only that trip's pins (null = everything, as before).
    private var tripId: Int? = null

    /** Which mode the current list was loaded for (null = Main). Starts unset. */
    private val _loadedFor = MutableStateFlow<Int?>(NOT_LOADED)
    val loadedFor: StateFlow<Int?> = _loadedFor

    companion object { const val NOT_LOADED = Int.MIN_VALUE }

    fun loadExpenses(tripId: Int? = this.tripId) {
        this.tripId = tripId
        viewModelScope.launch {
            // Same shared, Room-observed history Home/Insights/Friends use
            // (ExpenseRepository) instead of this being its own separate
            // full-table query on top of theirs.
            _expensesWithLocation.value = ExpenseRepository.allExpenses(database.expenseDao())
                .map { it.expense }
                .filter { it.latitude != null && it.longitude != null }
                .filter { tripId == null || it.tripId == tripId }
            _loadedFor.value = tripId
            _loaded.value = true
        }
    }
}
