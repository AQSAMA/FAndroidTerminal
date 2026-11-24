// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aqsama.fandroidterminal.designsystem.WorkspacePaletteStyles
import io.github.aqsama.fandroidterminal.runtime.QuickKeyLayout
import io.github.aqsama.fandroidterminal.runtime.linux.LinuxEnvironment
import io.github.aqsama.fandroidterminal.terminal.TerminalFonts
import io.github.aqsama.fandroidterminal.terminal.TerminalScheme
import io.github.aqsama.fandroidterminal.terminal.TerminalSchemes

@Composable
internal fun SettingsHome(preferences: Preferences, environmentCount: Int, open: (Screen) -> Unit) {
    val scheme = TerminalSchemes.get(preferences.palette)
    val layout = remember(preferences.keys) { QuickKeyLayout.parse(preferences.keys) }
    SettingsColumn {
        TerminalPreview(scheme, preferences.terminalFont, 13, Modifier.fillMaxWidth().height(132.dp).clickable { open(Screen.Appearance) })
        SettingsGroup {
            NavigationRow(R.drawable.ic_palette, stringResource(R.string.settings_appearance), ltr("${scheme.name} · ${TerminalFonts.get(preferences.terminalFont).name} · ${preferences.font} sp")) { open(Screen.Appearance) }
            NavigationRow(R.drawable.ic_keyboard, stringResource(R.string.settings_extra_keys), stringResource(R.string.quick_keys_summary, layout.always.size, layout.expanded.size)) { open(Screen.Keyboard) }
            NavigationRow(R.drawable.ic_terminal, stringResource(R.string.terminal_tools), stringResource(R.string.terminal_tools_summary)) { open(Screen.TerminalBehavior) }
            NavigationRow(R.drawable.ic_shield, stringResource(R.string.working_mode), stringResource(R.string.working_mode_summary)) { open(Screen.Access) }
        }
        SettingsGroup {
            NavigationRow(R.drawable.ic_environments, stringResource(R.string.environments_label), stringResource(R.string.settings_environment_count, environmentCount)) { open(Screen.Environments) }
            NavigationRow(R.drawable.ic_download, stringResource(R.string.guest_defaults_title), stringResource(R.string.guest_defaults_summary)) { open(Screen.GuestDefaults) }
            NavigationRow(R.drawable.ic_storage, stringResource(R.string.backup_title), stringResource(R.string.backup_summary)) { open(Screen.Backup) }
        }
        SettingsGroup {
            NavigationRow(R.drawable.ic_info, stringResource(R.string.settings_about), ltr("${BuildConfig.VERSION_NAME} · ${BuildConfig.BUILD_COMMIT.take(7)}")) { open(Screen.About) }
        }
    }
}

/** App chrome and terminal look, edited live with one combined preview. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AppearanceSettings(preferences: Preferences) {
    val scheme = TerminalSchemes.get(preferences.palette)
    // Live while dragging; persisted when the slider is released.
    var size by remember(preferences.font) { mutableFloatStateOf(preferences.font.toFloat()) }
    SettingsColumn {
        TerminalPreview(scheme, preferences.terminalFont, kotlin.math.round(size).toInt(), Modifier.fillMaxWidth().heightIn(min = 156.dp))
        SettingsGroup(stringResource(R.string.terminal_color_scheme)) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                val schemes = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = (TerminalSchemes.all.indexOf(scheme) - 1).coerceAtLeast(0))
                LaunchedEffect(scheme.id) { schemes.animateScrollToItem((TerminalSchemes.all.indexOf(scheme) - 1).coerceAtLeast(0)) }
                LazyRow(state = schemes, contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(TerminalSchemes.all, key = { it.id }) { option -> SchemeCard(option, option.id == scheme.id, TerminalFonts.get(preferences.terminalFont).composeFamily()) { preferences.setTerminalScheme(option.id) } }
                }
            }
        }
        SettingsGroup(stringResource(R.string.terminal_font_title)) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                FlowRow(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TerminalFonts.all.forEach { font ->
                        FilterChip(font.id == preferences.terminalFont, { preferences.setTerminalTypeface(font.id) },
                            label = { Text(font.name, fontFamily = font.composeFamily()) })
                    }
                }
            }
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.settings_text_size), Modifier.weight(1f))
                        Text("${kotlin.math.round(size).toInt()} sp", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    Slider(size, { size = it }, onValueChangeFinished = { preferences.setTerminalFont(kotlin.math.round(size).toInt()) }, valueRange = 8f..28f)
                    Text(stringResource(R.string.pinch_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        SettingsGroup(stringResource(R.string.app_theme_title)) {
            SegmentedRow(stringResource(R.string.theme_mode), listOf(AppTheme.System to stringResource(R.string.theme_system), AppTheme.Light to stringResource(R.string.theme_light), AppTheme.Dark to stringResource(R.string.theme_dark)),
                preferences.appTheme, preferences::setAppTheme)
            val sources = listOf(AccentSource.Terminal to stringResource(R.string.accent_terminal), AccentSource.Custom to stringResource(R.string.accent_custom)) +
                if (Build.VERSION.SDK_INT >= 31) listOf(AccentSource.Wallpaper to stringResource(R.string.accent_wallpaper)) else emptyList()
            SegmentedRow(stringResource(R.string.accent_source), sources, preferences.accentSource, preferences::setAccentSource)
            if (preferences.accentSource == AccentSource.Custom) Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(168, 200, 222, 250, 275, 305, 335, 12, 32, 50, 95, 135).forEach { hue ->
                            ColorSwatch(Color.hsl(hue.toFloat(), .62f, .62f).toArgbInt(), "$hue°", preferences.accentHue == hue, true, { preferences.setAccent(hue = hue) })
                        }
                    }
                    var hue by remember(preferences.accentHue) { mutableFloatStateOf(preferences.accentHue.toFloat()) }
                    Slider(hue, { hue = it }, onValueChangeFinished = { preferences.setAccent(hue = kotlin.math.round(hue).toInt()) }, valueRange = 0f..359f)
                    Text(stringResource(R.string.theme_palette_style), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        WorkspacePaletteStyles.filter { it in listOf("TonalSpot", "Neutral", "Vibrant", "Expressive", "Monochrome", "Fidelity") }.forEach { style ->
                            FilterChip(preferences.paletteStyle == style, { preferences.setPaletteStyle(style) }, label = { Text(paletteStyleLabel(style)) })
                        }
                    }
                }
            }
            SwitchRow(stringResource(R.string.theme_true_black), stringResource(R.string.theme_true_black_help), preferences.trueBlack, change = preferences::setTrueBlack)
        }
        TextButton(preferences::resetAppearance) {
            Icon(painterResource(R.drawable.ic_restore), null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.theme_reset))
        }
    }
}

private fun Color.toArgbInt(): Int = android.graphics.Color.argb((alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())

@Composable
private fun paletteStyleLabel(style: String) = stringResource(when (style) {
    "TonalSpot" -> R.string.palette_tonal; "Neutral" -> R.string.palette_neutral; "Vibrant" -> R.string.palette_vibrant
    "Expressive" -> R.string.palette_expressive; "Monochrome" -> R.string.palette_mono; else -> R.string.palette_fidelity
})

private val fontFamilyCache = HashMap<String, FontFamily>()

internal fun io.github.aqsama.fandroidterminal.terminal.TerminalFont.composeFamily(): FontFamily = synchronized(fontFamilyCache) {
    fontFamilyCache.getOrPut(id) { resource?.let { FontFamily(Font(it)) } ?: FontFamily.Monospace }
}

@Composable
private fun SchemeCard(scheme: TerminalScheme, selected: Boolean, font: FontFamily, select: () -> Unit) {
    val border = if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    Column(Modifier.width(112.dp).semantics { contentDescription = scheme.name; this.selected = selected }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(onClick = select, shape = RoundedCornerShape(16.dp), color = Color(scheme.background), border = border, modifier = Modifier.fillMaxWidth().height(76.dp)) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("$ ls", color = Color(scheme.foreground), fontFamily = font, fontSize = 12.sp, maxLines = 1)
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    (1..6).forEach { i -> Box(Modifier.size(10.dp).background(Color(scheme.ansi[i]), CircleShape)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    (9..14).forEach { i -> Box(Modifier.size(10.dp).background(Color(scheme.ansi[i]), CircleShape)) }
                }
            }
        }
        Text(scheme.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A static sample session in the chosen scheme and font. Also stands in for the live terminal in JVM screenshots. */
@Composable
internal fun TerminalPreview(scheme: TerminalScheme, fontId: String, size: Int, modifier: Modifier = Modifier, lines: AnnotatedString? = null) {
    val family = TerminalFonts.get(fontId).composeFamily()
    val text = lines ?: sampleSession(scheme)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        val shape = RoundedCornerShape(20.dp)
        Box(modifier.clip(shape).background(Color(scheme.background)).then(if (scheme.dark) Modifier else Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape))
            .horizontalScroll(rememberScrollState()).padding(14.dp)) {
            Text(text, color = Color(scheme.foreground), fontFamily = family, fontSize = size.sp, lineHeight = (size * 1.3f).sp,
                softWrap = false, style = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr))
        }
    }
}

internal fun sampleSession(scheme: TerminalScheme): AnnotatedString = buildAnnotatedString {
    fun c(index: Int, value: String, bold: Boolean = false) = withStyle(SpanStyle(color = Color(scheme.ansi[index]), fontWeight = if (bold) FontWeight.Bold else null)) { append(value) }
    c(2, "root@debian", true); append(":"); c(4, "~/app", true); append("$ ls\n")
    c(4, "src", true); append("  "); c(4, "docs", true); append("  "); c(2, "run.sh", true); append("  "); c(5, "logo.png"); append("\n")
    c(2, "root@debian", true); append(":"); c(4, "~/app", true); append("$ git diff --stat\n")
    append(" main.kt | 4 "); c(2, "++"); c(1, "--"); append("\n")
    c(2, "root@debian", true); append(":"); c(4, "~/app", true); append("$ "); withStyle(SpanStyle(background = Color(scheme.foreground))) { append(" ") }
}

/** Quick keys: what is always visible, what the chevron reveals, and how keys feel. */
@Composable
internal fun KeyboardSettings(preferences: Preferences) {
    val layout = remember(preferences.keys) { QuickKeyLayout.parse(preferences.keys) }
    SettingsColumn {
        QuickControlsEditor(layout.tokens, TerminalSchemes.get(preferences.palette), preferences.terminalFont) { tokens -> runCatching { preferences.setKeys(QuickKeyLayout.parse(tokens.joinToString(" "))) } }
        SettingsGroup {
            SwitchRow(stringResource(R.string.haptic_keys), null, preferences.hapticKeys, R.drawable.ic_keyboard, change = preferences::setHapticKeys)
        }
        SettingsGroup(stringResource(R.string.hardware_shortcuts)) {
            listOf(R.string.commands_title to "Ctrl+Shift+P", R.string.new_session to "Ctrl+Shift+N", R.string.close_session to "Ctrl+Shift+W",
                R.string.switch_session to "Ctrl+(Shift)+Tab", R.string.copy_label to "Ctrl+Shift+C", R.string.paste_label to "Ctrl+Shift+V",
                R.string.terminal_text_size to "Ctrl+Shift+= / −").forEach { (label, keys) ->
                ListItem(colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), headlineContent = { Text(stringResource(label)) },
                    trailingContent = { Text(keys, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) })
            }
        }
    }
}

/** Startup, input safety, bell and saved commands. */
@Composable
internal fun TerminalBehaviorSettings(preferences: Preferences, environments: List<LinuxEnvironment>) {
    val context = LocalContext.current
    SettingsColumn {
        SettingsGroup(stringResource(R.string.startup_title)) {
            val choices = listOf("Android" to stringResource(R.string.android_shell), "Sessions" to stringResource(R.string.sessions_label), "Environments" to stringResource(R.string.environments_label)) +
                environments.filter { it.ready }.map { it.id to it.name }
            ChoiceRow(stringResource(R.string.startup_open), R.drawable.ic_shell, choices, preferences.startup, preferences::setStartup)
            SwitchRow(stringResource(R.string.keep_screen_awake_short), stringResource(R.string.while_terminal_visible), preferences.keepAwake, R.drawable.ic_workspace, change = preferences::setKeepAwake)
        }
        SettingsGroup(stringResource(R.string.input_title)) {
            SegmentedRow(stringResource(R.string.bell_title), listOf(BellMode.Off to stringResource(R.string.bell_off), BellMode.Vibrate to stringResource(R.string.bell_vibrate), BellMode.Flash to stringResource(R.string.bell_flash)),
                preferences.bell, preferences::setBell)
            SwitchRow(stringResource(R.string.confirm_multiline_paste), stringResource(R.string.confirm_multiline_paste_help), preferences.confirmMultilinePaste, R.drawable.ic_paste, change = preferences::setConfirmMultilinePaste)
        }
        SavedCommandsEditor(preferences)
        SettingsGroup(stringResource(R.string.home_directories)) {
            ListItem(colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                leadingContent = { RowIcon(R.drawable.ic_android) },
                headlineContent = { Text(stringResource(R.string.android_shell)) },
                supportingContent = { Text(ltr(context.filesDir.absolutePath), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                trailingContent = { IconButton({ context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("HOME", context.filesDir.absolutePath)) }) { Icon(painterResource(R.drawable.ic_copy), stringResource(R.string.copy_label)) } })
            ListItem(colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                leadingContent = { RowIcon(R.drawable.ic_debian) },
                headlineContent = { Text(stringResource(R.string.linux_home_title)) },
                supportingContent = { Text(stringResource(R.string.guest_home_help)) })
        }
    }
}

@Composable
private fun SavedCommandsEditor(preferences: Preferences) {
    var editing by rememberSaveable { mutableIntStateOf(-2) }
    var draft by rememberSaveable { mutableStateOf("") }
    val commands = preferences.snippetList
    SettingsGroup(stringResource(R.string.settings_saved_commands)) {
        commands.forEachIndexed { index, command ->
            ListItem(colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.clickable { editing = index; draft = command },
                leadingContent = { Icon(painterResource(R.drawable.ic_prompt), null, tint = MaterialTheme.colorScheme.primary) },
                headlineContent = { Text(command, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, textDirection = TextDirection.Ltr)) },
                trailingContent = { IconButton({ preferences.setSnippets(commands.filterIndexed { i, _ -> i != index }.joinToString("\n")) }) { Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.delete_menu)) } })
        }
        ListItem(colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.clickable { editing = -1; draft = "" },
            leadingContent = { Icon(painterResource(R.drawable.ic_add), null) }, headlineContent = { Text(stringResource(R.string.add_saved_command)) })
    }
    if (editing != -2) {
        var error by remember { mutableStateOf<String?>(null) }
        AlertDialog(onDismissRequest = { editing = -2 }, title = { Text(stringResource(R.string.settings_saved_commands)) }, text = {
            OutlinedTextField(draft, { if ('\n' !in it && '\r' !in it) draft = it }, label = { Text(stringResource(R.string.command_label)) }, isError = error != null,
                supportingText = error?.let { { Text(it) } }, modifier = Modifier.fillMaxWidth(), minLines = 2,
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, textDirection = TextDirection.Ltr))
        }, confirmButton = { TextButton(enabled = draft.isNotBlank(), onClick = {
            val updated = commands.toMutableList().apply { if (editing in indices) this[editing] = draft else add(draft) }.joinToString("\n")
            runCatching { preferences.setSnippets(updated) }.onSuccess { editing = -2 }.onFailure { error = it.message }
        }) { Text(stringResource(R.string.save_label)) } }, dismissButton = { TextButton({ editing = -2 }) { Text(stringResource(R.string.cancel_label)) } })
    }
}

@Composable
internal fun AboutSettings(licenses: () -> Unit) {
    SettingsColumn {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primaryContainer) {
            Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(painterResource(R.drawable.ic_terminal), null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Column {
                    Text("FAndroidTerminal", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(ltr("${BuildConfig.VERSION_NAME} · build ${BuildConfig.VERSION_CODE} · ${BuildConfig.BUILD_COMMIT.take(12)}"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
        SettingsGroup {
            ListItem(colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), headlineContent = { Text(stringResource(R.string.settings_capabilities)) },
                supportingContent = { Text(stringResource(R.string.settings_capabilities_help)) })
            NavigationRow(R.drawable.ic_info, stringResource(R.string.licenses_label), "GPL-3.0-only", licenses)
        }
    }
}
