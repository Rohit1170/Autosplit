package com.example.splitwiseauto

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class PendingNotificationsViewModel(application: Application) : AndroidViewModel(application) {

    private companion object {
        const val RECENT_PAYMENTS = 5
    }

    private val db = AppDatabase.getDatabase(application)

    private val _pending = MutableStateFlow<List<PendingExpenseNotification>>(emptyList())
    val pending: StateFlow<List<PendingExpenseNotification>> = _pending

    // Most recent Scan & Pay payments, for the status list in the dialog.
    private val _payments = MutableStateFlow<List<PendingPayment>>(emptyList())
    val payments: StateFlow<List<PendingPayment>> = _payments

    fun refresh() {
        viewModelScope.launch {
            _pending.value = db.pendingExpenseNotificationDao().getAll()
            _payments.value = db.pendingPaymentDao().getRecent(RECENT_PAYMENTS)
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
