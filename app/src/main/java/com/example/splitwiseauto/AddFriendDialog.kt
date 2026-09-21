package com.example.splitwiseauto

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFriendDialog(
    onDismiss: () -> Unit,
    onAddFriend: (String) -> Unit
) {

    var name by remember {
        mutableStateOf("")
    }

    AlertDialog(
        onDismissRequest = onDismiss,

        title = {
            Text("Add Friend")
        },

        text = {

            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = {
                    Text("Friend name")
                },
                placeholder = {
                    Text("e.g. Rahul")
                }
            )
        },

        confirmButton = {

            TextButton(
                onClick = {

                    if (name.trim().isNotEmpty()) {
                        onAddFriend(name.trim())
                    }
                }
            ) {
                Text("Add")
            }
        },

        dismissButton = {

            TextButton(
                onClick = onDismiss
            ) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RenameFriendDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit
) {

    var name by remember {
        mutableStateOf(currentName)
    }

    AlertDialog(
        onDismissRequest = onDismiss,

        title = {
            Text("Rename Friend")
        },

        text = {

            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = {
                    Text("Friend name")
                }
            )
        },

        confirmButton = {

            TextButton(
                onClick = {

                    if (name.trim().isNotEmpty()) {
                        onRename(name.trim())
                    }
                }
            ) {
                Text("Save")
            }
        },

        dismissButton = {

            TextButton(
                onClick = onDismiss
            ) {
                Text("Cancel")
            }
        }
    )
}
