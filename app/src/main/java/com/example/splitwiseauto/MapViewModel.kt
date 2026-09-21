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

    fun loadExpenses() {
        viewModelScope.launch {
            _expensesWithLocation.value = database.expenseDao().getAllExpenses().filter {
                it.latitude != null && it.longitude != null
            }
        }
    }
}
