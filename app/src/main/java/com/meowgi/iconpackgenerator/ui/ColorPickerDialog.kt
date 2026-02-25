package com.meowgi.iconpackgenerator.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

@Composable
fun ColorPickerDialog(
    initialColor: Int = android.graphics.Color.RED,
    onDismiss: () -> Unit,
    onColorSelected: (Int) -> Unit
) {
    var red by remember { mutableFloatStateOf(android.graphics.Color.red(initialColor) / 255f) }
    var green by remember { mutableFloatStateOf(android.graphics.Color.green(initialColor) / 255f) }
    var blue by remember { mutableFloatStateOf(android.graphics.Color.blue(initialColor) / 255f) }

    val currentColor = Color(red, green, blue)
    val currentArgb = currentColor.toArgb() or 0xFF000000.toInt()

    var hexInput by remember {
        mutableStateOf(String.format("%06X", currentArgb and 0xFFFFFF))
    }

    val focusManager = LocalFocusManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pick a Color") },
        text = {
            Column {
                // Color preview
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(currentColor)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Hex input
                OutlinedTextField(
                    value = "#$hexInput",
                    onValueChange = { value ->
                        val hex = value.removePrefix("#").take(6).uppercase()
                            .filter { it in "0123456789ABCDEF" }
                        hexInput = hex
                        if (hex.length == 6) {
                            try {
                                val parsed = android.graphics.Color.parseColor("#$hex")
                                red = android.graphics.Color.red(parsed) / 255f
                                green = android.graphics.Color.green(parsed) / 255f
                                blue = android.graphics.Color.blue(parsed) / 255f
                            } catch (_: Exception) { }
                        }
                    },
                    label = { Text("Hex Color") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // RGB sliders
                ColorSlider("R", red, Color.Red) { red = it; updateHex(red, green, blue) { hexInput = it } }
                ColorSlider("G", green, Color.Green) { green = it; updateHex(red, green, blue) { hexInput = it } }
                ColorSlider("B", blue, Color.Blue) { blue = it; updateHex(red, green, blue) { hexInput = it } }

                Spacer(modifier = Modifier.height(8.dp))

                // Preset colors
                Text("Presets", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(4.dp))
                PresetColorRow { color ->
                    red = android.graphics.Color.red(color) / 255f
                    green = android.graphics.Color.green(color) / 255f
                    blue = android.graphics.Color.blue(color) / 255f
                    hexInput = String.format("%06X", color and 0xFFFFFF)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onColorSelected(currentArgb) }) {
                Text("Select")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ColorSlider(
    label: String,
    value: Float,
    trackColor: Color,
    onValueChange: (Float) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(end = 8.dp)
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            colors = SliderDefaults.colors(
                thumbColor = trackColor,
                activeTrackColor = trackColor
            ),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "${(value * 255).toInt()}",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
private fun PresetColorRow(onSelect: (Int) -> Unit) {
    val presets = listOf(
        0xFFFF0000.toInt(), 0xFFFF5722.toInt(), 0xFFFF9800.toInt(),
        0xFFFFEB3B.toInt(), 0xFF4CAF50.toInt(), 0xFF2196F3.toInt(),
        0xFF9C27B0.toInt(), 0xFFE91E63.toInt(), 0xFF00BCD4.toInt(),
        0xFF795548.toInt()
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        for (color in presets) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(color))
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    .clickable { onSelect(color) }
            )
        }
    }
}

private fun updateHex(r: Float, g: Float, b: Float, setter: (String) -> Unit) {
    val color = android.graphics.Color.rgb(
        (r * 255).toInt(),
        (g * 255).toInt(),
        (b * 255).toInt()
    )
    setter(String.format("%06X", color and 0xFFFFFF))
}
