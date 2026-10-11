package com.example.splitwiseauto

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.animateFloat
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.abs

private enum class SettleChoice { FULL, HALF, CUSTOM }

private data class SettleDone(val settled: Double, val total: Double, val settlementId: Int?) {
    val remaining: Double get() = (total - settled).coerceAtLeast(0.0)
    val isFull: Boolean get() = remaining < 0.01
}

/**
 * Swipe-to-settle sheet (full or partial).
 *
 * Input: Full / Half / Custom amount, then swipe. Result: a ring that fills
 * by the share settled (closing into a check + confetti when it's the whole
 * balance), "₹500 settled · ₹750 left" for partial ones, and an Undo link
 * for 5 seconds. Partial amounts are applied to the oldest splits first
 * (SettleStatus), so expenses update to SETTLED / PART PAID by themselves.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettleUpSheet(
    friend: Friend,
    balance: FriendBalance,
    onSettle: (amount: Double, onSaved: (Int) -> Unit) -> Unit,
    onUndo: (settlementId: Int) -> Unit,
    onDismiss: () -> Unit
) {
    // Captured when the sheet opens, so the numbers don't shift under the
    // user once the settlement refreshes the balances.
    val total = remember(friend.id) { abs(balance.netAmount) }
    val theyOweMe = remember(friend.id) { balance.netAmount > 0 }

    var choice by remember(friend.id) { mutableStateOf(SettleChoice.FULL) }
    var customText by remember(friend.id) { mutableStateOf("") }
    var done by remember(friend.id) { mutableStateOf<SettleDone?>(null) }

    val amount: Double? = when (choice) {
        SettleChoice.FULL -> total
        SettleChoice.HALF -> kotlin.math.round(total / 2.0 * 100) / 100.0
        SettleChoice.CUSTOM -> customText.replace(",", "").toDoubleOrNull()
    }
    val valid = amount != null && amount > 0.009 && amount <= total + 0.001

    AppSheet(
        title = if (done == null) "Settle up with ${friend.name}" else friend.name,
        onDismiss = onDismiss,
        trailing = { TextButton(onClick = onDismiss) { Text(if (done == null) "Close" else "Done") } }
    ) {
        Crossfade(targetState = done, animationSpec = tween(260)) { result ->
            if (result == null) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (theyOweMe) "${friend.name} owes you" else "You owe ${friend.name}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatRupeeIndian(total),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettleChip("Full ${formatRupeeIndian(total)}", choice == SettleChoice.FULL) { choice = SettleChoice.FULL }
                        SettleChip("Half", choice == SettleChoice.HALF) { choice = SettleChoice.HALF }
                        SettleChip("Custom", choice == SettleChoice.CUSTOM) { choice = SettleChoice.CUSTOM }
                    }
                    if (choice == SettleChoice.CUSTOM) {
                        Spacer(Modifier.height(12.dp))
                        WiseAmountField(
                            value = customText,
                            onValueChange = { customText = it },
                            hint = "Up to ${formatRupeeIndian(total)}",
                            isError = customText.isNotBlank() && !valid,
                            autoFocus = true
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                    val a = amount ?: 0.0
                    val isFull = valid && abs(a - total) < 0.01
                    // Re-created when the amount changes so the knob resets.
                    key(a, valid) {
                        SwipeToConfirm(
                            text = when {
                                !valid -> "Enter an amount"
                                isFull -> "Swipe to settle ${formatRupeeIndian(total)}"
                                else -> "Swipe to settle ${formatRupeeIndian(a)} of ${formatRupeeIndian(total)}"
                            },
                            enabled = valid,
                            onConfirmed = {
                                val settledAmount = a
                                onSettle(settledAmount) { id ->
                                    done = SettleDone(settledAmount, total, id)
                                }
                            }
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = if (choice == SettleChoice.CUSTOM && !valid) "Enter how much was paid, then swipe."
                        else if (isFull || !valid) "Clears the whole balance." else
                            "Partial: the rest (${formatRupeeIndian(total - a)}) stays outstanding. " +
                                "It's applied to the oldest splits first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                var showUndo by remember(result) { mutableStateOf(result.settlementId != null) }
                LaunchedEffect(result) {
                    delay(5_000)
                    showUndo = false
                }
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    SuccessBurst(
                        fraction = if (result.isFull) 1f else (result.settled / result.total).toFloat(),
                        celebrate = result.isFull,
                        diameter = 170.dp
                    )
                    Text(
                        text = if (result.isFull) "All settled with ${friend.name} 🎉"
                        else "${formatRupeeIndian(result.settled)} settled",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (result.isFull) "${formatRupeeIndian(result.settled)} · balance ₹0"
                        else "${formatRupeeIndian(result.remaining)} left of ${formatRupeeIndian(result.total)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    if (showUndo) {
                        TextButton(onClick = {
                            result.settlementId?.let(onUndo)
                            done = null
                        }) { Text("Undo") }
                    } else {
                        Spacer(Modifier.height(48.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettleChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}


