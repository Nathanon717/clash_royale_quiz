package com.example.helloandroid.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

enum class NumberPadLayout {
    STANDARD, // For 0-9, typically for single elixir cost
    COMBOS    // For 2-16+, typically for combined elixir costs
}

@Composable
fun NumberPad(
    modifier: Modifier = Modifier,
    onNumberClick: (Int) -> Unit,
    enabled: Boolean = true,
    layoutType: NumberPadLayout = NumberPadLayout.STANDARD
) {
    val numbers: List<List<Int>> = when (layoutType) {
        NumberPadLayout.STANDARD -> listOf(
            listOf(1, 2, 3, 4, 5),
            listOf(6, 7, 8, 9, 0)
        )
        NumberPadLayout.COMBOS -> listOf(
            listOf(2, 3, 4, 5, 6),
            listOf(7, 8, 9, 10, 11),
            listOf(12, 13, 14, 15, 16) // Assuming max combo cost won't exceed 16 significantly for now
            // If higher costs are needed, this can be extended or made dynamic
        )
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp) // Spacing between rows
    ) {
        numbers.forEach { rowNumbers ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp) // Spacing between buttons
            ) {
                rowNumbers.forEach { number ->
                    Button(
                        onClick = { onNumberClick(number) },
                        modifier = Modifier.width(60.dp), // Adjust size as needed
                        enabled = enabled
                    ) {
                        Text(text = number.toString())
                    }
                }
            }
        }
    }
}