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

    fun loadExpenses() {
        viewModelScope.launch {
            // Same shared, Room-observed history Home/Insights/Friends use
            // (ExpenseRepository) instead of this being its own separate
            // full-table query on top of theirs.
            _expensesWithLocation.value = ExpenseRepository.allExpenses(database.expenseDao())
                .map { it.expense }
                .filter { it.latitude != null && it.longitude != null }
            _loaded.value = true
        }
    }
}
