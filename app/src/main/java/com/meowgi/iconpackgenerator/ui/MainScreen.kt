package com.meowgi.iconpackgenerator.ui

import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meowgi.iconpackgenerator.domain.ConversionSettings
import com.meowgi.iconpackgenerator.domain.GenerationProgress
import com.meowgi.iconpackgenerator.domain.IconPackStyle
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: GeneratorViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    var showColorPicker by remember { mutableStateOf(false) }
    var selectedStyle by remember { mutableStateOf<IconPackStyle?>(null) }
    var customColor by remember { mutableIntStateOf(AndroidColor.RED) }
    var showLogs by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var showPreviewViewer by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var convSettings by remember { mutableStateOf(ConversionSettings()) }
    val context = LocalContext.current
    val hasSelection = selectedStyle != null

    // Load saved settings on first composition
    androidx.compose.runtime.LaunchedEffect(Unit) {
        convSettings = ConversionSettings.load(context)
    }

    // Dry-run preview files
    val result = state.lastResult
    val previewFiles by remember(result?.previewDir) {
        derivedStateOf {
            val dir = result?.previewDir?.let { File(it) }
            if (dir != null && dir.exists()) {
                dir.listFiles { f -> f.extension == "png" }
                    ?.sortedBy { it.name }
                    ?: emptyList()
            } else emptyList()
        }
    }

    // Full-screen preview viewer
    if (showPreviewViewer && previewFiles.isNotEmpty()) {
        PreviewViewerDialog(
            files = previewFiles,
            onDismiss = { showPreviewViewer = false }
        )
    }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("Permission Required") },
            text = {
                Text("To install icon packs, you need to allow this app to install unknown apps. Tap \"Open Settings\" to grant the permission, then come back and tap Install again.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionDialog = false
                    context.startActivity(viewModel.getInstallPermissionIntent())
                }) { Text("Open Settings") }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showColorPicker) {
        ColorPickerDialog(
            initialColor = customColor,
            onDismiss = { showColorPicker = false },
            onColorSelected = { color ->
                customColor = color
                selectedStyle = IconPackStyle.CUSTOM
                showColorPicker = false
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (showSettings) "Settings" else "Icon Pack Generator",
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = { showSettings = !showSettings }) {
                        Icon(
                            if (showSettings) Icons.Default.Close else Icons.Default.Tune,
                            contentDescription = if (showSettings) "Close" else "Settings"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        if (showSettings) {
            Column(modifier = Modifier.padding(padding)) {
                SettingsScreen(
                    settings = convSettings,
                    onSave = { newSettings ->
                        convSettings = newSettings
                        ConversionSettings.save(context, newSettings)
                        showSettings = false
                    },
                    onReset = {
                        convSettings = ConversionSettings()
                        ConversionSettings.save(context, convSettings)
                    }
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // Description
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Generate Monochrome Icon Packs",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Scans your installed apps, extracts their icons, converts them to monochrome silhouettes, and builds an installable icon pack APK.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Action buttons
            item {
                Text(
                    "Choose a style",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val whiteSelected = selectedStyle == IconPackStyle.WHITE
                    val blackSelected = selectedStyle == IconPackStyle.BLACK
                    val customSelected = selectedStyle == IconPackStyle.CUSTOM

                    OutlinedButton(
                        onClick = { selectedStyle = IconPackStyle.WHITE },
                        enabled = !state.isGenerating,
                        modifier = Modifier.weight(1f),
                        colors = if (whiteSelected) ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ) else ButtonDefaults.outlinedButtonColors()
                    ) {
                        Text("White")
                    }

                    OutlinedButton(
                        onClick = { selectedStyle = IconPackStyle.BLACK },
                        enabled = !state.isGenerating,
                        modifier = Modifier.weight(1f),
                        colors = if (blackSelected) ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.inverseSurface,
                            contentColor = MaterialTheme.colorScheme.inverseOnSurface
                        ) else ButtonDefaults.outlinedButtonColors()
                    ) {
                        Text("Black")
                    }

                    OutlinedButton(
                        onClick = { showColorPicker = true },
                        enabled = !state.isGenerating,
                        modifier = Modifier.weight(1f),
                        colors = if (customSelected) ButtonDefaults.buttonColors(
                            containerColor = Color(customColor),
                            contentColor = if (luminance(customColor) > 128) Color.Black else Color.White
                        ) else ButtonDefaults.outlinedButtonColors()
                    ) {
                        Icon(Icons.Default.Palette, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (customSelected) String.format("#%06X", customColor and 0xFFFFFF) else "Custom")
                    }
                }
            }

            // Dry Run + Build APK buttons
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val style = selectedStyle ?: return@OutlinedButton
                            val color = if (style == IconPackStyle.CUSTOM) customColor
                                else if (style == IconPackStyle.BLACK) AndroidColor.BLACK
                                else AndroidColor.WHITE
                            viewModel.generate(style, color, dryRun = true)
                        },
                        enabled = hasSelection && !state.isGenerating,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.GridView, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Dry Run")
                    }

                    Button(
                        onClick = {
                            val style = selectedStyle ?: return@Button
                            val color = if (style == IconPackStyle.CUSTOM) customColor
                                else if (style == IconPackStyle.BLACK) AndroidColor.BLACK
                                else AndroidColor.WHITE
                            viewModel.generate(style, color)
                        },
                        enabled = hasSelection && !state.isGenerating,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Build, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Build APK")
                    }
                }
            }

            // Cancel button
            if (state.isGenerating) {
                item {
                    OutlinedButton(
                        onClick = { viewModel.cancelGeneration() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Cancel, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Cancel")
                    }
                }
            }

            // Progress
            item {
                AnimatedVisibility(
                    visible = state.isGenerating,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    ProgressCard(state.progress)
                }
            }

            // Result
            if (result != null) {
                item {
                    ResultCard(
                        result = result,
                        onInstall = {
                            if (viewModel.checkInstallPermission()) {
                                viewModel.installLastApk()
                            } else {
                                showPermissionDialog = true
                            }
                        },
                        onViewPreview = if (result.isDryRun && previewFiles.isNotEmpty()) {
                            { showPreviewViewer = true }
                        } else null
                    )
                }
            }

            // Logs toggle
            if (state.logs.isNotEmpty()) {
                item {
                    TextButton(onClick = { showLogs = !showLogs }) {
                        Text(if (showLogs) "Hide Logs" else "Show Logs (${state.logs.size})")
                    }
                }
                if (showLogs) {
                    items(state.logs) { log ->
                        Text(
                            text = log,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun ProgressCard(progress: GenerationProgress) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                progress.message,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            if (progress.total > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress.fraction },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "${progress.current} / ${progress.total}",
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ResultCard(
    result: ResultInfo,
    onInstall: () -> Unit,
    onViewPreview: (() -> Unit)?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (result.success)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (result.success) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = null,
                    tint = if (result.success) Color(0xFF4CAF50)
                    else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = when {
                        !result.success -> "Generation failed"
                        result.isDryRun -> "Dry run complete"
                        else -> "Icon pack ready!"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(8.dp))

            if (result.success) {
                Text("Icons: ${result.iconCount}", style = MaterialTheme.typography.bodyMedium)
                if (result.failedCount > 0) {
                    Text(
                        "Failed: ${result.failedCount}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (result.packageName.isNotBlank()) {
                    Text(
                        "Package: ${result.packageName}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(Modifier.height(12.dp))

                if (onViewPreview != null) {
                    Button(
                        onClick = onViewPreview,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.GridView, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("View Generated Icons")
                    }
                }

                if (result.apkPath.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onInstall,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.InstallMobile, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Install Icon Pack")
                    }
                }
            } else {
                Text(
                    result.errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * Full-screen dialog showing all generated icons in a scrollable grid.
 * Tap an icon to see it enlarged.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreviewViewerDialog(files: List<File>, onDismiss: () -> Unit) {
    var selectedFile by remember { mutableStateOf<File?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Preview (${files.size} icons)") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, "Close")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            containerColor = MaterialTheme.colorScheme.surface
        ) { padding ->
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 80.dp),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                items(files) { file ->
                    val bitmap = remember(file.absolutePath) {
                        try {
                            BitmapFactory.decodeFile(file.absolutePath)
                        } catch (_: Exception) { null }
                    }
                    if (bitmap != null) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectedFile = file }
                                .padding(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = file.nameWithoutExtension,
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                            Text(
                                text = file.nameWithoutExtension
                                    .replace("__", "/")
                                    .substringAfterLast("/")
                                    .take(12),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        // Enlarged icon overlay
        if (selectedFile != null) {
            val enlargedBitmap = remember(selectedFile?.absolutePath) {
                try {
                    BitmapFactory.decodeFile(selectedFile!!.absolutePath)
                } catch (_: Exception) { null }
            }
            if (enlargedBitmap != null) {
                Dialog(onDismissRequest = { selectedFile = null }) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(192.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = enlargedBitmap.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.size(160.dp)
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = selectedFile!!.nameWithoutExtension
                                    .replace("__", " / ")
                                    .replace("_", "."),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(12.dp))
                            TextButton(onClick = { selectedFile = null }) {
                                Text("Close")
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun luminance(color: Int): Int {
    return (0.299 * AndroidColor.red(color) + 0.587 * AndroidColor.green(color) + 0.114 * AndroidColor.blue(color)).toInt()
}
