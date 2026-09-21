package com.example.splitwiseauto

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class PendingNotificationsViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)

    private val _pending = MutableStateFlow<List<PendingExpenseNotification>>(emptyList())
    val pending: StateFlow<List<PendingExpenseNotification>> = _pending

    fun refresh() {
        viewModelScope.launch {
            _pending.value = db.pendingExpenseNotificationDao().getAll()
        }
    }

    /** Discards a pending expense without opening/saving it. */
    fun dismiss(id: Int) {
        viewModelScope.launch {
            db.pendingExpenseNotificationDao().deleteById(id)
            refresh()
        }
    }
}
