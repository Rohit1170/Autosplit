package com.example.splitwiseauto

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.splitwiseauto.ui.theme.positiveColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Amber for "needs a second look" states (UNVERIFIED / UNCONFIRMED). The dark
 * value matches Home's warn token; the light one is darkened so it keeps
 * enough contrast as text on light surfaces.
 */
@Composable
fun warnColor(): Color = if (isSystemInDarkTheme()) Color(0xFFF0A63C) else Color(0xFF8A5A00)

/**
 * Status pill for a Scan & Pay payment. Every state has its own icon AND
 * color, so they're distinguishable without relying on color alone.
 */
@Composable
fun PaymentStatusChip(status: PendingPaymentStatus, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val (label, color, icon) = when (status) {
        PendingPaymentStatus.VERIFIED_PENDING -> Triple("Paid · confirming", positiveColor(), Icons.Filled.CheckCircle)
        PendingPaymentStatus.CONFIRMED -> Triple("Bank confirmed", positiveColor(), Icons.Filled.Verified)
        PendingPaymentStatus.PENDING -> Triple("Confirming", scheme.primary, Icons.Filled.HourglassTop)
        PendingPaymentStatus.FAILED -> Triple("Failed", scheme.error, Icons.Filled.Cancel)
        PendingPaymentStatus.UNVERIFIED -> Triple("Not verified", warnColor(), Icons.Filled.Info)
        PendingPaymentStatus.UNCONFIRMED -> Triple("Unconfirmed", warnColor(), Icons.Filled.WarningAmber)
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.16f)
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

/** One Scan & Pay payment: payee, amount, time, and its status chip. */
@Composable
fun PendingPaymentRow(payment: PendingPayment, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = payment.payeeName ?: payment.payeeVpa,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${formatRupeeIndian(payment.amountMinor / 100.0)} · " +
                    SimpleDateFormat("dd MMM, h:mm a", Locale.getDefault()).format(Date(payment.initiatedAt)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        PaymentStatusChip(payment.statusEnum, modifier = Modifier.padding(start = 8.dp))
    }
}
