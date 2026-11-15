// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aqsama.fandroidterminal.designsystem.ConnectedGroup
import io.github.aqsama.fandroidterminal.designsystem.TerminalTheme
import io.github.aqsama.fandroidterminal.designsystem.workspaceColors

/** June's grouped appearance flow, adapted to terminal chrome and our preference ownership. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ThemeEditor(theme: String, hue: Int, chroma: Int, black: Boolean, dynamic: Boolean,
    setTheme: (String) -> Unit, setHue: (Int) -> Unit, setChroma: (Int) -> Unit, setBlack: (Boolean) -> Unit, setDynamic: (Boolean) -> Unit, paletteStyle: String = "TonalSpot", setStyle: (String) -> Unit = {}) {
    val hueLabel = stringResource(R.string.theme_seed)
    val chromaLabel = stringResource(R.string.theme_chroma, chroma)
    var paletteTab by rememberSaveable { mutableStateOf(false) }
    val dark = when (theme) { "Dark" -> true; "Light" -> false; else -> isSystemInDarkTheme() }
    TerminalTheme(dark, hue, chroma, black, dynamic, paletteStyle) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(painterResource(R.drawable.ic_terminal), null, tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.theme_preview), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.weight(1f)); Icon(painterResource(R.drawable.ic_check), null, tint = MaterialTheme.colorScheme.primary)
                }
                ConnectedGroup {
                    ListItem(colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh), leadingContent = { Icon(painterResource(R.drawable.ic_palette), null, tint = MaterialTheme.colorScheme.tertiary) }, headlineContent = { Text(stringResource(R.string.settings_appearance)) }, trailingContent = { Switch(true, null) })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary, shape = MaterialTheme.shapes.large) { Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(painterResource(R.drawable.ic_add), null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.new_session)) } }
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.large) { Icon(painterResource(R.drawable.ic_files), null, Modifier.padding(12.dp)) }
                }
                Row(Modifier.fillMaxWidth().height(16.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.secondaryContainer).forEach { color -> Box(Modifier.weight(1f).fillMaxHeight().background(color, RoundedCornerShape(4.dp))) }
                }
            }
        }
    }
    ConnectedGroup {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.app_theme_title), style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("System" to R.string.theme_system, "Light" to R.string.theme_light, "Dark" to R.string.theme_dark).forEach { (value, label) ->
                        FilterChip(theme == value, { setTheme(value) }, label = { Text(stringResource(label)) }, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        ListItem(colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), headlineContent = { Text(stringResource(R.string.theme_true_black)) }, leadingContent = { Icon(painterResource(R.drawable.ic_palette), null) }, trailingContent = { Switch(black, setBlack) })
        if (Build.VERSION.SDK_INT >= 31) ListItem(colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), headlineContent = { Text(stringResource(R.string.theme_wallpaper)) }, leadingContent = { Icon(painterResource(R.drawable.ic_palette), null) }, trailingContent = { Switch(dynamic, setDynamic) })
    }
    AnimatedVisibility(!dynamic) {
        Column(Modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!paletteTab, { paletteTab = false }, label = { Text(stringResource(R.string.theme_seed)) }, modifier = Modifier.weight(1f))
                FilterChip(paletteTab, { paletteTab = true }, label = { Text(stringResource(R.string.theme_palette_style)) }, modifier = Modifier.weight(1f))
            }
            if (!paletteTab) Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.theme_seed), style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(168, 190, 215, 245, 275, 305, 335, 12, 35, 55, 90, 135).forEach { value ->
                            ColorSwatch(Color.hsl(value.toFloat(), .65f, .7f).toArgb(), "$value°", hue == value, true, { setHue(value) })
                        }
                    }
                    Text(stringResource(R.string.theme_hue, hue), style = MaterialTheme.typography.labelLarge)
                    Slider(hue.toFloat(), { setHue(it.toInt()) }, modifier = Modifier.semantics { contentDescription = hueLabel }, valueRange = 0f..359f)
                    Text(stringResource(R.string.theme_chroma, chroma), style = MaterialTheme.typography.labelLarge)
                    Slider(chroma.toFloat(), { setChroma(it.toInt()) }, modifier = Modifier.semantics { contentDescription = chromaLabel }, valueRange = 20f..90f)
                }
            }
            if (paletteTab) ConnectedGroup {
                io.github.aqsama.fandroidterminal.designsystem.WorkspacePaletteStyles.forEach { style ->
                    val scheme = workspaceColors(dark, hue, chroma, black, style)
                    val label = when (style) {
                        "TonalSpot" -> R.string.palette_tonal; "Neutral" -> R.string.palette_neutral; "Vibrant" -> R.string.palette_vibrant
                        "Expressive" -> R.string.palette_expressive; "Rainbow" -> R.string.palette_rainbow; "FruitSalad" -> R.string.palette_fruit
                        "Monochrome" -> R.string.palette_mono; "Fidelity" -> R.string.palette_fidelity; else -> R.string.palette_content
                    }
                    PaletteCard(stringResource(label), listOf(scheme.primary, scheme.primaryContainer, scheme.secondary, scheme.secondaryContainer, scheme.tertiary, scheme.tertiaryContainer, scheme.surfaceContainerHighest), paletteStyle == style, { setStyle(style) }, Modifier.fillMaxWidth())
                }
            }
        }
    }
    TextButton({ setTheme("System"); setHue(168); setChroma(65); setBlack(false); setDynamic(false); setStyle("TonalSpot") }) {
        Icon(painterResource(R.drawable.ic_restore), null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.theme_reset))
    }
}
