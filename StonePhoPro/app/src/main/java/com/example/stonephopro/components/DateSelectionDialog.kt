package com.example.stonephopro.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun DateSelectionDialog(
    title: String,
    selectedDay: String,
    selectedMonth: String,
    selectedYear: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit
) {
    val dayOptions = (1..31).map { it.toString().padStart(2, '0') }
    val monthOptions = (1..12).map { it.toString().padStart(2, '0') }

    var day by remember { mutableStateOf(selectedDay) }
    var month by remember { mutableStateOf(selectedMonth) }
    var year by remember { mutableStateOf(selectedYear) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Dropdown ngày
                    var expandedDay by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(onClick = { expandedDay = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(day)
                        }
                        DropdownMenu(expanded = expandedDay, onDismissRequest = { expandedDay = false }) {
                            dayOptions.forEach {
                                DropdownMenuItem(text = { Text(it) }, onClick = {
                                    day = it
                                    expandedDay = false
                                })
                            }
                        }
                    }

                    // Dropdown tháng
                    var expandedMonth by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(onClick = { expandedMonth = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(month)
                        }
                        DropdownMenu(expanded = expandedMonth, onDismissRequest = { expandedMonth = false }) {
                            monthOptions.forEach {
                                DropdownMenuItem(text = { Text(it) }, onClick = {
                                    month = it
                                    expandedMonth = false
                                })
                            }
                        }
                    }
                }

                // Nhập năm
                OutlinedTextField(
                    value = year,
                    onValueChange = {
                        if (it.length <= 4 && it.all { ch -> ch.isDigit() }) year = it
                    },
                    label = { Text("Năm") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(day, month, year) }) {
                Text("✔ Xác nhận")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("❌ Hủy")
            }
        }
    )
}
