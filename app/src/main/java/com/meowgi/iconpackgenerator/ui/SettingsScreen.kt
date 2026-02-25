package com.meowgi.iconpackgenerator.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meowgi.iconpackgenerator.domain.ConversionSettings
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    settings: ConversionSettings,
    onSave: (ConversionSettings) -> Unit,
    onReset: () -> Unit
) {
    var bgKeep by remember(settings) { mutableFloatStateOf(settings.bgKeepThreshold) }
    var bgCut by remember(settings) { mutableFloatStateOf(settings.bgCutThreshold) }
    var secAlpha by remember(settings) { mutableIntStateOf(settings.secondaryAlpha) }
    var lumGap by remember(settings) { mutableIntStateOf(settings.minLuminanceGap) }
    var padding by remember(settings) { mutableFloatStateOf(settings.iconPadding) }
    var fullIcon by remember(settings) { mutableStateOf(settings.useFullIcon) }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Conversion Settings",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Adjust these to tune icon quality. Use Dry Run to preview changes.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Background Removal section
        SettingsSection("Background Removal (U2-Net)") {
            SliderSetting(
                label = "Keep Threshold",
                value = bgKeep,
                range = 0.05f..0.50f,
                format = { "%.2f".format(it) },
                description = "Saliency above this = fully kept. Lower = keep more icon content.",
                onValueChange = { bgKeep = it }
            )
            SliderSetting(
                label = "Cut Threshold",
                value = bgCut,
                range = 0.01f..0.30f,
                format = { "%.2f".format(it) },
                description = "Saliency below this = fully removed. Lower = remove less background.",
                onValueChange = { bgCut = it }
            )
        }

        // Monochrome section
        SettingsSection("Monochrome Conversion") {
            SliderSetting(
                label = "Detail Opacity",
                value = secAlpha.toFloat(),
                range = 0f..200f,
                format = { "${it.roundToInt()}" },
                description = "Alpha for secondary detail regions (0=invisible, 255=same as primary). Current: $secAlpha",
                onValueChange = { secAlpha = it.roundToInt() }
            )
            SliderSetting(
                label = "Min Luminance Gap",
                value = lumGap.toFloat(),
                range = 5f..100f,
                format = { "${it.roundToInt()}" },
                description = "Minimum difference between two groups to show detail. Lower = more detail splits. Current: $lumGap",
                onValueChange = { lumGap = it.roundToInt() }
            )
        }

        // Icon section
        SettingsSection("Icon Rendering") {
            SliderSetting(
                label = "Padding",
                value = padding,
                range = 0f..0.30f,
                format = { "${(it * 100).roundToInt()}%" },
                description = "Space around the icon. 16% is standard.",
                onValueChange = { padding = it }
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Use Full Icon", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Render full icon (foreground+background) instead of just foreground layer. Better for U2-Net on adaptive icons.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
                Switch(checked = fullIcon, onCheckedChange = { fullIcon = it })
            }
        }

        // Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onReset,
                modifier = Modifier.weight(1f)
            ) { Text("Reset Defaults") }

            Button(
                onClick = {
                    onSave(
                        ConversionSettings(
                            bgKeepThreshold = bgKeep,
                            bgCutThreshold = bgCut.coerceAtMost(bgKeep - 0.01f),
                            secondaryAlpha = secAlpha,
                            minLuminanceGap = lumGap,
                            iconPadding = padding,
                            useFullIcon = fullIcon
                        )
                    )
                },
                modifier = Modifier.weight(1f)
            ) { Text("Save") }
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SliderSetting(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    format: (Float) -> String,
    description: String,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                format(value),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp
        )
        Spacer(Modifier.height(8.dp))
    }
}
