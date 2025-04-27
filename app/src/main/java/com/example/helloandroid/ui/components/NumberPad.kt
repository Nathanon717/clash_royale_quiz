package com.example.helloandroid.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun NumberPad(
    modifier: Modifier = Modifier,
    onNumberClick: (Int) -> Unit,
    enabled: Boolean = true // Add enabled flag
) {
    val numbersRow1 = listOf(1, 2, 3, 4, 5)
    val numbersRow2 = listOf(6, 7, 8, 9, 0)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp) // Spacing between rows
    ) {
        // First row
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp) // Spacing between buttons
        ) {
            numbersRow1.forEach { number ->
                Button(
                    onClick = { onNumberClick(number) },
                    modifier = Modifier.width(60.dp), // Adjust size as needed
                    enabled = enabled // Use the enabled flag
                ) {
                    Text(text = number.toString())
                }
            }
        }
        // Second row
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp) // Spacing between buttons
        ) {
            numbersRow2.forEach { number ->
                Button(
                    onClick = { onNumberClick(number) },
                    modifier = Modifier.width(60.dp), // Adjust size as needed
                    enabled = enabled // Use the enabled flag
                ) {
                    Text(text = number.toString())
                }
            }
        }
    }
}