package com.example.stonephopro.utils

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.util.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.stonephopro.components.Button3D


@Composable
fun PriceText(price: Double) {
    val formatted = NumberFormat.getCurrencyInstance(Locale.US).format(price) // e.g. $1,000.00
    val parts = formatted.split(".")
    val dollars = parts[0]
    val cents = if (parts.size > 1) parts[1] else "00"

    val text = buildAnnotatedString {
        append(dollars + ".")
        pushStyle(SpanStyle(fontSize = 10.sp))
        append(cents)
        pop()
    }

    Text(text = text, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
}
@Composable
fun PriceInputPad(
    label: String,
    value: Int,
    onChange: (Int) -> Unit
) {
    val formatted = "%.2f".format(value / 100.0)

    Column {
        OutlinedTextField(
            value = formatted,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("←", "0", "AC")
        )

        rows.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { digit ->
                    Button3D(
                        text = digit,
                        onClick = {
                            when (digit) {
                                "←" -> onChange(value / 10)
                                "AC" -> onChange(0)
                                else -> {
                                    val newValue = (value * 10 + digit.toInt()).coerceAtMost(999999)
                                    onChange(newValue)
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(4.dp)
                            .height(50.dp), // ✅ chiều cao phù hợp
                        fontSize = 18.sp, // ✅ chữ to hơn cho dễ đọc
                        gradientColors = listOf(Color(0xFF4FC3F7), Color(0xFF0288D1)) // ✅ Gradient màu xanh dương
                    )

                }
            }
        }
    }
}