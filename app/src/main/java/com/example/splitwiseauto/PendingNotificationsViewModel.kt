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

    // Scan & Pay payments that still need attention (waiting, or never
    // confirmed by a bank SMS). Confirmed and failed ones are not listed.
    private val _payments = MutableStateFlow<List<PendingPayment>>(emptyList())
    val payments: StateFlow<List<PendingPayment>> = _payments

    fun refresh() {
        viewModelScope.launch {
            _pending.value = db.pendingExpenseNotificationDao().getAll()
            _payments.value = db.pendingPaymentDao().getNeedingAttention()
        }
    }

    /** Removes a never-confirmed Scan & Pay payment from the list. */
    fun dismissPayment(id: String) {
        viewModelScope.launch {
            db.pendingPaymentDao().deleteUnconfirmed(id)
            refresh()
        }
    }

    /** "Just me": saves the pending debit as a personal expense (no split screen). */
    fun justMe(id: Int) {
        viewModelScope.launch {
            QuickExpenseActions.saveJustMe(getApplication<Application>(), id)
            QuickExpenseActions.cancelNotification(getApplication<Application>(), id)
            refresh()
        }
    }

    /** Discards a pending expense without opening/saving it. */
    fun dismiss(id: Int) {
        viewModelScope.launch {
            db.pendingExpenseNotificationDao().deleteById(id)
            QuickExpenseActions.cancelNotification(getApplication<Application>(), id)
            refresh()
        }
    }
}
