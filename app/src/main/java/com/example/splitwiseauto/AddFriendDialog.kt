package com.example.splitwiseauto

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * Add a friend by typing a name, or pick them from contacts (fills the name
 * and number). The number is optional and only used for reminders.
 * Callers that store the number pass [onAddFriendWithPhone]; [onAddFriend]
 * is kept for callers that only need the name.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFriendDialog(
    onDismiss: () -> Unit,
    onAddFriend: (String) -> Unit = {},
    onAddFriendWithPhone: ((String, String?) -> Unit)? = null
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    val pickContact = rememberContactPicker { pickedName, pickedPhone ->
        if (!pickedName.isNullOrBlank()) name = pickedName.trim()
        if (!pickedPhone.isNullOrBlank()) phone = pickedPhone.trim()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Friend") },
        text = {
            Column {
                ModernTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "Friend name",
                    placeholder = "e.g. Rahul",
                    autoFocus = true
                )
                Spacer(Modifier.height(10.dp))
                ModernTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "Phone (optional)",
                    placeholder = "For WhatsApp / SMS reminders",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = pickContact) {
                    Icon(Icons.Filled.Contacts, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Pick from contacts")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val clean = name.trim()
                    if (clean.isNotEmpty()) {
                        if (onAddFriendWithPhone != null) {
                            onAddFriendWithPhone(clean, PhoneNumbers.normalize(phone))
                        } else {
                            onAddFriend(clean)
                        }
                    }
                }
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/** Edit a friend's name and number (number optional; pick from contacts or type it). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RenameFriendDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    currentPhone: String? = null,
    onSave: ((String, String?) -> Unit)? = null
) {
    var name by remember { mutableStateOf(currentName) }
    var phone by remember { mutableStateOf(PhoneNumbers.display(currentPhone).orEmpty()) }
    val pickContact = rememberContactPicker { _, pickedPhone ->
        if (!pickedPhone.isNullOrBlank()) phone = pickedPhone.trim()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (onSave != null) "Edit friend" else "Rename Friend") },
        text = {
            Column {
                ModernTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "Friend name"
                )
                if (onSave != null) {
                    Spacer(Modifier.height(10.dp))
                    ModernTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = "Phone (optional)",
                        placeholder = "For WhatsApp / SMS reminders",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                    )
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = pickContact) {
                        Icon(Icons.Filled.Contacts, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Pick from contacts")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val clean = name.trim()
                    if (clean.isNotEmpty()) {
                        if (onSave != null) onSave(clean, phone.takeIf { it.isNotBlank() })
                        else onRename(clean)
                    }
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
