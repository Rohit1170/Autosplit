package com.example.splitwiseauto

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Full-width bottom sheet used instead of AlertDialog for list content.
 * AlertDialog measures its body with intrinsic sizes, which collapsed rows
 * containing weighted text + buttons (the settlement history showed only a
 * clipped sliver of "Received ₹200"). A plain Column has no such problem.
 */
@Composable
fun AppSheet(
    title: String,
    onDismiss: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    subtitle: String? = null,
    // Splitlee's dark-green glass instead of the plain Material surface.
    glass: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    ),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = if (glass) androidx.compose.ui.graphics.Color.Transparent else MaterialTheme.colorScheme.surface,
                // A transparent Surface can't derive a text colour (it fell back
                // to black on the dark glass), so set it explicitly.
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = if (glass) 0.dp else 3.dp
            ) {
              Box(
                modifier = if (!glass) Modifier else Modifier
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            if (androidx.compose.foundation.isSystemInDarkTheme())
                                listOf(androidx.compose.ui.graphics.Color(0xFF17221E), androidx.compose.ui.graphics.Color(0xFF0D1311))
                            else listOf(androidx.compose.ui.graphics.Color(0xFFFFFFFF), androidx.compose.ui.graphics.Color(0xFFF1F6F3))
                        )
                    )
                    .border(
                        1.dp,
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(
                                androidx.compose.ui.graphics.Color.White.copy(alpha = 0.14f),
                                androidx.compose.ui.graphics.Color.White.copy(alpha = 0.0f)
                            )
                        ),
                        RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                    )
              ) {
                Column(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .imePadding()
                        .heightIn(max = 600.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .padding(top = 10.dp, bottom = 20.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .size(width = 36.dp, height = 4.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50))
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        leading?.let {
                            it()
                            Spacer(modifier = Modifier.size(12.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            subtitle?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        trailing?.invoke()
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    content()
                }
              }
            }
        }
    }
}
