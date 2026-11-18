// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aqsama.fandroidterminal.designsystem.ConnectedGroup
import io.github.aqsama.fandroidterminal.sessions.TerminalService
import io.github.aqsama.fandroidterminal.sessions.GuestSettingsFailure
import io.github.aqsama.fandroidterminal.runtime.linux.EnvironmentFiles
import io.github.aqsama.fandroidterminal.runtime.linux.LinuxEnvironment
import kotlinx.coroutines.launch

@Composable
private fun SettingLink(icon: Int, title: String, description: String, action: () -> Unit) {
    ListItem(modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = action), colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        leadingContent = { Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer) { Icon(painterResource(icon), null, Modifier.padding(12.dp).size(24.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer) } },
        headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) }, supportingContent = if (description.isBlank()) null else ({ Text(description, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }), trailingContent = { Icon(painterResource(R.drawable.ic_chevron), null) })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SettingsPage(preferences: Preferences, requestedSection: String? = null, requestHandled: () -> Unit = {}, environments: () -> Unit = {}, licenses: () -> Unit = {}, exit: () -> Unit = {}, installed: List<LinuxEnvironment> = emptyList(), defaults: () -> Unit = {}, backup: () -> Unit = {}, packages: () -> Unit = {}, files: (String, String) -> Unit = { _, _ -> }, startingDirectory: (String, (Result<String>) -> Unit) -> Unit = { _, done -> done(Result.failure(IllegalStateException("Unavailable"))) }, detailOnly: Boolean = false) {
    fun normalized(value: String?) = if (value in listOf("Startup", "Saved commands", "Keyboard & clipboard")) "Terminal & tools" else value
    var section by rememberSaveable { mutableStateOf(normalized(requestedSection)) }
    var handledRequest by remember { mutableStateOf<String?>(null) }
    val shownSection = if (requestedSection != null && requestedSection != handledRequest) normalized(requestedSection) else section
    LaunchedEffect(requestedSection) {
        if (requestedSection != null) { section = normalized(requestedSection); handledRequest = requestedSection; requestHandled() }
        else handledRequest = null
    }
    var theme by rememberSaveable { mutableStateOf(preferences.appTheme) }
    var paletteStyle by rememberSaveable { mutableStateOf(preferences.paletteStyle) }
    var hue by rememberSaveable { mutableIntStateOf(preferences.accentHue) }
    var chroma by rememberSaveable { mutableIntStateOf(preferences.accentChroma) }
    var black by rememberSaveable { mutableStateOf(preferences.trueBlack) }
    var dynamic by rememberSaveable { mutableStateOf(preferences.dynamicColors) }
    var awake by rememberSaveable { mutableStateOf(preferences.keepAwake) }
    var startup by rememberSaveable { mutableStateOf(preferences.startup) }
    val canonicalKeys = io.github.aqsama.fandroidterminal.runtime.QuickKeyLayout.parse(preferences.keys).encode()
    val behaviorDirty = awake != preferences.keepAwake || startup != preferences.startup
    var font by rememberSaveable { mutableIntStateOf(preferences.font) }
    var keys by rememberSaveable { mutableStateOf(io.github.aqsama.fandroidterminal.runtime.QuickKeyLayout.parse(preferences.keys).tokens) }
    var snippetsLimitError by remember { mutableStateOf(false) }
    var snippets by rememberSaveable { mutableStateOf(preferences.snippets) }
    var palette by rememberSaveable { mutableStateOf(preferences.palette) }
    val incomingAppearance = listOf(preferences.font.toString(), preferences.palette, preferences.appTheme, preferences.accentHue.toString(), preferences.accentChroma.toString(), preferences.trueBlack.toString(), preferences.dynamicColors.toString(), preferences.paletteStyle)
    val draftAppearance = listOf(font.toString(), palette, theme, hue.toString(), chroma.toString(), black.toString(), dynamic.toString(), paletteStyle)
    val incomingBehavior = listOf(preferences.keepAwake.toString(), preferences.startup, preferences.snippets)
    var appearanceBase by rememberSaveable { mutableStateOf(incomingAppearance) }
    var behaviorBase by rememberSaveable { mutableStateOf(incomingBehavior) }
    var keysBase by rememberSaveable { mutableStateOf(canonicalKeys) }
    var conflicts by rememberSaveable { mutableStateOf(emptyList<String>()) }
    fun syncAppearance() {
        font = preferences.font; palette = preferences.palette; theme = preferences.appTheme
        hue = preferences.accentHue; chroma = preferences.accentChroma; black = preferences.trueBlack
        dynamic = preferences.dynamicColors; paletteStyle = preferences.paletteStyle
    }
    LaunchedEffect(incomingAppearance, incomingBehavior, preferences.keys, preferences.snippets) {
        fun conflict(name: String, edited: Boolean) { conflicts = if (edited) (conflicts + name).distinct() else conflicts - name }
        if (incomingAppearance != appearanceBase) {
            val edited = draftAppearance != appearanceBase && draftAppearance != incomingAppearance
            if (!edited) syncAppearance()
            conflict("Appearance", edited); appearanceBase = incomingAppearance
        }
        if (incomingBehavior != behaviorBase) {
            val draft = listOf(awake.toString(), startup, snippets)
            val edited = draft != behaviorBase && draft != incomingBehavior
            if (!edited) { awake = preferences.keepAwake; startup = preferences.startup; snippets = preferences.snippets }
            conflict("Terminal & tools", edited); behaviorBase = incomingBehavior
        }
        if (canonicalKeys != keysBase) {
            val edited = keys.joinToString(" ") != keysBase && keys.joinToString(" ") != canonicalKeys
            if (!edited) keys = io.github.aqsama.fandroidterminal.runtime.QuickKeyLayout.parse(preferences.keys).tokens
            conflict("Extra keyboard keys", edited); keysBase = canonicalKeys
        }

    }
    var result by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val appearanceDirty = font != preferences.font || palette != preferences.palette || theme != preferences.appTheme || hue != preferences.accentHue || chroma != preferences.accentChroma || black != preferences.trueBlack || dynamic != preferences.dynamicColors || paletteStyle != preferences.paletteStyle
    val keysDirty = keys.joinToString(" ") != canonicalKeys
    val commandsDirty = snippets != preferences.snippets
    val editable = shownSection in listOf("Appearance", "Extra keyboard keys", "Terminal & tools")
    val dirty = when (shownSection) {
        "Terminal & tools" -> behaviorDirty || commandsDirty
        "Appearance" -> appearanceDirty
        "Extra keyboard keys" -> keysDirty
        "Saved commands" -> commandsDirty
        else -> false
    }
    fun back() { if (shownSection != null && !detailOnly) section = null else exit() }
    BackHandler(shownSection != null) { back() }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = ::back) { Icon(painterResource(R.drawable.ic_back), stringResource(R.string.back_label)) }
            Icon(painterResource(when (shownSection) { "Appearance" -> R.drawable.ic_palette; "Extra keyboard keys" -> R.drawable.ic_keyboard; "Saved commands" -> R.drawable.ic_commands; else -> R.drawable.ic_settings }), null, Modifier.size(26.dp), tint = MaterialTheme.colorScheme.primary)
            Text(when (shownSection) { "Appearance" -> stringResource(R.string.settings_appearance); "Extra keyboard keys" -> stringResource(R.string.settings_extra_keys); "Working mode" -> stringResource(R.string.working_mode); "Terminal & tools" -> stringResource(R.string.terminal_tools); "Keyboard & clipboard" -> stringResource(R.string.settings_keyboard_clipboard); "About this app" -> stringResource(R.string.settings_about); else -> stringResource(R.string.settings_label) },
                Modifier.weight(1f).padding(horizontal = 8.dp), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        Page("", showTitle = false) {
            val currentSection = shownSection
            val entrance = remember { Animatable(0f) }
            val density = androidx.compose.ui.platform.LocalDensity.current
            LaunchedEffect(currentSection) {
                entrance.snapTo(with(density) { 12.dp.toPx() })
                entrance.animateTo(0f, tween(220))
            }
            Box(Modifier.weight(1f)) {
            Column(Modifier.fillMaxSize().graphicsLayer { translationY = entrance.value }.verticalScroll(key(currentSection) { rememberScrollState() }), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (currentSection in conflicts) Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.padding(12.dp)) {
                        Text(stringResource(R.string.settings_restore_conflict), color = MaterialTheme.colorScheme.onTertiaryContainer, style = MaterialTheme.typography.bodyMedium)
                        TextButton({
                            when (currentSection) {
                                "Appearance" -> syncAppearance()
                                "Terminal & tools" -> { awake = preferences.keepAwake; startup = preferences.startup; snippets = preferences.snippets }
                                "Extra keyboard keys" -> keys = io.github.aqsama.fandroidterminal.runtime.QuickKeyLayout.parse(preferences.keys).tokens
                                "Saved commands" -> snippets = preferences.snippets
                            }
                            conflicts = conflicts - currentSection!!
                        }) { Text(stringResource(R.string.settings_use_current)) }
                    }
                }
                if (currentSection == null) {
                    if (appearanceDirty || keysDirty || commandsDirty || behaviorDirty) Text(stringResource(R.string.settings_drafts_kept), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ConnectedGroup {
                        SettingLink(R.drawable.ic_palette, stringResource(R.string.settings_appearance), stringResource(R.string.appearance_summary, preferences.font, preferences.palette)) { section = "Appearance"; result = "" }
                        SettingLink(R.drawable.ic_keyboard, stringResource(R.string.settings_extra_keys), stringResource(R.string.quick_rows_summary)) { section = "Extra keyboard keys"; result = "" }
                        SettingLink(R.drawable.ic_shield, stringResource(R.string.working_mode), stringResource(R.string.working_mode_summary)) { section = "Working mode" }
                        SettingLink(R.drawable.ic_shell, stringResource(R.string.terminal_tools), stringResource(R.string.terminal_tools_summary)) { section = "Terminal & tools"; result = "" }
                    }
                    ConnectedGroup {
                        SettingLink(R.drawable.ic_storage, stringResource(R.string.backup_title), "", backup)
                        SettingLink(R.drawable.ic_environments, stringResource(R.string.guest_settings_title), stringResource(R.string.settings_environment_count, installed.size), environments)
                        SettingLink(R.drawable.ic_info, stringResource(R.string.settings_about), BuildConfig.VERSION_NAME) { section = "About this app" }
                    }
                    Text("${BuildConfig.VERSION_NAME} · ${BuildConfig.BUILD_COMMIT.take(12)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                } else {
                    when (currentSection) {
                        "Working mode" -> WorkingModeSettings(installed)
                        "Terminal & tools" -> TerminalToolsSettings(startup, { startup = it; result = "" }, awake, { awake = it; result = "" }, snippets, { snippets = it; result = "" }, installed, packages, defaults, files, startingDirectory)
                        "Appearance" -> {
                            ThemeEditor(theme, hue, chroma, black, dynamic, { theme = it; result = "" }, { hue = it; result = "" }, { chroma = it; result = "" }, { black = it; result = "" }, { dynamic = it; result = "" }, paletteStyle, { paletteStyle = it; result = "" })
                            AppearanceSettings(font, palette, { font = it; result = "" }, { palette = it; result = "" })
                        }
                        "Extra keyboard keys" -> {
                            QuickControlsEditor(keys) { keys = it; result = "" }
                        }
                        "About this app" -> {
                            Text("FAndroidTerminal", style = MaterialTheme.typography.titleLarge)
                            Text("${BuildConfig.VERSION_NAME}\nBuild ${BuildConfig.VERSION_CODE} · ${BuildConfig.BUILD_COMMIT.take(12)}\nGPL-3.0-only")
                            Text(stringResource(R.string.settings_capabilities), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.settings_capabilities_help))
                            OutlinedButton(onClick = licenses) { Icon(painterResource(R.drawable.ic_info), null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.licenses_label)) }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            }
            if (editable) SettingsSaveArea(dirty, saving, result.takeIf { it.isNotEmpty() && it != "Changes saved" }, result == "Changes saved", stringResource(R.string.save_changes)) {
                val selectedSection = shownSection
                val newFont = if (selectedSection == "Appearance") font else preferences.font
                val newPalette = if (selectedSection == "Appearance") palette else preferences.palette
                val newKeys = if (selectedSection == "Extra keyboard keys") keys.joinToString(" ") else preferences.keys
                val newSnippets = if (selectedSection == "Terminal & tools") snippets else preferences.snippets
                val newTheme = if (selectedSection == "Appearance") theme else preferences.appTheme; val newHue = hue; val newChroma = chroma; val newBlack = black; val newDynamic = dynamic; val newStyle = paletteStyle; val newAwake = awake; val newStartup = startup
                saving = true
                scope.launch { try { if (selectedSection == "Terminal & tools") preferences.saveTools(newAwake, newStartup, newSnippets) else if (selectedSection == "Appearance") preferences.saveAppearance(newFont, newPalette, newTheme, newHue, newChroma, newBlack, newDynamic, newStyle) else preferences.save(newFont, newPalette, newKeys, newSnippets); result = "Changes saved" } catch (e: Exception) { result = e.message ?: "Could not save settings" } finally { saving = false } }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
internal fun GuestSettingsPage(service: TerminalService, id: String, packages: () -> Unit = {}, manageSessions: () -> Unit) {
    val environments by service.environments.environments.collectAsStateWithLifecycle()
    val environment = environments.firstOrNull { it.id == id } ?: return
    val sessions by service.sessions.sessions.collectAsStateWithLifecycle()
    val progress by service.environments.progress.collectAsStateWithLifecycle()
    val count = sessions.count { it.environmentId == id }
    var shells by remember(id) { mutableStateOf(emptyList<String>()) }
    val resources = androidx.compose.ui.platform.LocalResources.current
    var discoveryError by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        FilledTonalButton(onClick = packages, modifier = Modifier.padding(horizontal = 16.dp)) { Icon(painterResource(R.drawable.ic_download), null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.install_shell_packages)) }
        discoveryError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Box(Modifier.weight(1f)) {
            GuestSettingsContent(environment, count, progress, manageSessions, shells, {
                var found = emptyList<String>()
                try {
                    service.environments.fileOperation(id, service.sessions, "Discovering shells…", { failure ->
                        discoveryError = failure?.message
                        if (failure == null) shells = found
                    }) { guest ->
                        found = (runCatching { guest.read("/etc/shells").lines() }.getOrDefault(emptyList()) + listOf("/bin/sh", "/bin/bash", "/bin/zsh", "/usr/bin/fish"))
                            .map { it.trim() }.filter { it.startsWith('/') }.distinct().take(100)
                            .filter { runCatching { java.nio.file.Files.isExecutable(guest.resolve(it)) }.getOrDefault(false) }
                    }
                } catch (e: Exception) { discoveryError = e.message ?: resources.getString(R.string.check_values) }
            }) { value, completed -> service.environments.saveSettings(value, service.sessions, completed) }
        }
    }
}

@Composable
internal fun GuestSettingsContent(environment: LinuxEnvironment, count: Int,
    progress: Map<String, String>, manageSessions: () -> Unit, shells: List<String> = emptyList(), discover: (() -> Unit)? = null, save: (LinuxEnvironment, (Exception?) -> Unit) -> Unit) {
    val id = environment.id
    var dns by rememberSaveable(id) { mutableStateOf(environment.dns) }
    var compatibility by rememberSaveable(id) { mutableStateOf(environment.compatibility) }
    var inheritShell by rememberSaveable(id) { mutableStateOf(environment.inheritShell) }
    var inheritDirectory by rememberSaveable(id) { mutableStateOf(environment.inheritDirectory) }
    var directory by rememberSaveable(id) { mutableStateOf(environment.startDirectory) }
    var shell by rememberSaveable(id) { mutableStateOf(environment.shell) }
    var variables by rememberSaveable(id) { mutableStateOf(environment.variables) }
    var message by remember { mutableStateOf<String?>(null) }
    var dnsError by remember { mutableStateOf<String?>(null) }
    var directoryError by remember { mutableStateOf<String?>(null) }
    var shellError by remember { mutableStateOf<String?>(null) }
    var advanced by rememberSaveable(id) { mutableStateOf(environment.variables.isNotBlank()) }
    val dirty = inheritShell != environment.inheritShell || inheritDirectory != environment.inheritDirectory || dns != environment.dns || compatibility != environment.compatibility || directory != environment.startDirectory || shell != environment.shell || variables != environment.variables
    val saving = progress[id] == "Saving settings…"
    val resources = androidx.compose.ui.platform.LocalResources.current
    Page(environment.name, stringResource(R.string.guest_settings_title)) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).animateContentSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ContextBadge(environment.distribution)
                        Column { Text("${io.github.aqsama.fandroidterminal.runtime.linux.LinuxImages.get(environment.distribution).name} ${environment.release}", style = MaterialTheme.typography.titleMedium); Text(stringResource(R.string.guest_context_badge), style = MaterialTheme.typography.labelMedium, color = contextColor(environment.distribution)) }
                    }
                    Text("${environment.shell}  •  ${environment.startDirectory}", style = MaterialTheme.typography.labelMedium.copy(textDirection = TextDirection.Ltr))
                }
            }
            if (count > 0) ConnectedGroup {
                ListItem(colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    headlineContent = { Text(stringResource(R.string.review_sessions)) },
                    supportingContent = { Text(stringResource(R.string.env_access_lock, count)) },
                    leadingContent = { Icon(painterResource(R.drawable.ic_shell), null) },
                    trailingContent = { Icon(painterResource(R.drawable.ic_chevron), null) },
                    modifier = Modifier.clickable(onClick = manageSessions))
            }
            Text(stringResource(R.string.guest_network), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(dns, { dns = it; message = null; dnsError = null }, label = { Text(stringResource(R.string.guest_dns)) }, isError = dnsError != null, supportingText = dnsError?.let { error -> ({ Text(error) }) }, singleLine = true, modifier = Modifier.fillMaxWidth(), textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr))
            Text(stringResource(R.string.guest_start), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(directory, { directory = it; message = null; directoryError = null }, label = { Text(stringResource(R.string.guest_folder)) }, isError = directoryError != null, supportingText = directoryError?.let { error -> ({ Text(error) }) }, singleLine = true, modifier = Modifier.fillMaxWidth(), textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr))
            Row(Modifier.fillMaxWidth().toggleable(inheritShell, role = Role.Checkbox) { inheritShell = it; message = null }) {
                Checkbox(inheritShell, null); Text(stringResource(R.string.inherit_guest_shell))
            }
            Row(Modifier.fillMaxWidth().toggleable(inheritDirectory, role = Role.Checkbox) { inheritDirectory = it; message = null }) {
                Checkbox(inheritDirectory, null); Text(stringResource(R.string.inherit_guest_directory))
            }
            discover?.let { action -> TextButton(enabled = count == 0 && progress.isEmpty(), onClick = action) { Text(stringResource(R.string.discover_shells)) } }
            shells.forEach { candidate ->
                TextButton(onClick = { shell = candidate; shellError = null; message = null }) { Text(candidate) }
            }
            OutlinedTextField(shell, { shell = it; message = null; shellError = null }, label = { Text(stringResource(R.string.guest_shell)) }, isError = shellError != null, supportingText = shellError?.let { error -> ({ Text(error) }) }, singleLine = true, modifier = Modifier.fillMaxWidth(), textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr))
            Text(stringResource(R.string.guest_compatibility), style = MaterialTheme.typography.titleMedium)
            ConnectedGroup {
            ListItem(colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), headlineContent = { Text(stringResource(R.string.guest_compatibility_mode)) }, supportingContent = { Text(stringResource(R.string.guest_compatibility_help)) }, leadingContent = { Icon(painterResource(R.drawable.ic_shield), null) }, trailingContent = { Switch(compatibility, onCheckedChange = null) }, modifier = Modifier.toggleable(value = compatibility, role = Role.Switch, onValueChange = { compatibility = it; message = null }))
            }
            ConnectedGroup {
                ListItem(colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), headlineContent = { Text(stringResource(R.string.guest_advanced)) },
                    supportingContent = { Text(stringResource(R.string.guest_variables)) },
                    trailingContent = { Icon(painterResource(if (advanced) R.drawable.ic_chevron_up else R.drawable.ic_chevron_down), null) },
                    modifier = Modifier.clickable { advanced = !advanced })
            }
            AnimatedVisibility(advanced) { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SelectionContainer { Text(java.io.File(androidx.compose.ui.platform.LocalContext.current.filesDir, "environments/$id/rootfs").path, style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr)) }
            OutlinedTextField(variables, { variables = it; message = null }, label = { Text(stringResource(R.string.guest_variables)) }, supportingText = { Text(stringResource(R.string.guest_variables_help), style = MaterialTheme.typography.bodyMedium) }, minLines = 3, modifier = Modifier.fillMaxWidth(), textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, textDirection = TextDirection.Ltr))
            } }
            Spacer(Modifier.height(16.dp))
        }
        SettingsSaveArea(dirty, saving, message?.takeIf { it != "Changes saved" }, message == "Changes saved", stringResource(R.string.guest_save),
            enabled = count == 0 && progress.isEmpty(), reviewSessions = if (count > 0) manageSessions else null) {
            message = null
            dnsError = runCatching { EnvironmentFiles.validateDns(dns) }.exceptionOrNull()?.message
            directoryError = if (!directory.startsWith('/') || directory.length > 4096 || directory.any(Char::isISOControl)) resources.getString(R.string.absolute_guest_path_required) else null
            shellError = if (!shell.startsWith('/') || shell.length > 4096 || shell.any(Char::isISOControl)) resources.getString(R.string.absolute_guest_path_required) else null
            val completed: (Exception?) -> Unit = { failure ->
                when ((failure as? GuestSettingsFailure)?.field) {
                    GuestSettingsFailure.Field.StartingFolder -> directoryError = failure?.message
                    GuestSettingsFailure.Field.ShellProgram -> shellError = failure?.message
                    null -> Unit
                }
                message = if (failure == null) "Changes saved" else if (failure is GuestSettingsFailure) resources.getString(R.string.check_values) else failure.message ?: resources.getString(R.string.check_values)
            }
            if (dnsError == null && directoryError == null && shellError == null) {
                runCatching { save(environment.copy(dns = dns, compatibility = compatibility, startDirectory = directory, shell = shell, variables = variables, inheritShell = inheritShell, inheritDirectory = inheritDirectory), completed) }.onFailure { completed(it as? Exception ?: IllegalStateException(it)) }
            } else message = resources.getString(R.string.check_values)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun KeyArrangement(keys: List<String>) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        if (keys.isEmpty()) Text(stringResource(R.string.settings_no_keys), style = MaterialTheme.typography.bodySmall)
        keys.forEach { name ->
            val icon = when (name) { "Up" -> R.drawable.ic_key_up; "Down" -> R.drawable.ic_key_down; "Left" -> R.drawable.ic_key_left; "Right" -> R.drawable.ic_key_right; else -> null }
            val description = when (name) { "Up" -> R.string.key_arrow_up; "Down" -> R.string.key_arrow_down; "Left" -> R.string.key_arrow_left; "Right" -> R.string.key_arrow_right; else -> null }
            if (icon != null && description != null) Icon(painterResource(icon), stringResource(description), Modifier.size(20.dp)) else Text(name, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Shared edit feedback keeps app and environment settings consistent, including blocked saves. */
@Composable
internal fun SettingsSaveArea(dirty: Boolean, saving: Boolean, error: String?, saved: Boolean, label: String,
    enabled: Boolean = true, reviewSessions: (() -> Unit)? = null, save: () -> Unit) {
    if (dirty || saving || error != null) Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(if (saving) R.string.saving_settings else R.string.unsaved_changes), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            Button(enabled = dirty && !saving && enabled, onClick = save, modifier = Modifier.fillMaxWidth()) {
                if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Icon(painterResource(R.drawable.ic_check), null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp)); Text(label)
            }
            reviewSessions?.let { TextButton(onClick = it, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.review_sessions)) } }
        }
    } else if (saved) Text(stringResource(R.string.changes_saved), style = MaterialTheme.typography.bodyMedium)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppearanceSettings(font: Int, palette: String, setFont: (Int) -> Unit, setPalette: (String) -> Unit) {
    val sizing: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.settings_text_size), style = MaterialTheme.typography.titleMedium)
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    FilledTonalIconButton(onClick = { setFont((font - 1).coerceAtLeast(8)) }, enabled = font > 8) { Icon(painterResource(R.drawable.ic_minus), stringResource(R.string.smaller_terminal_text)) }
                    Text("$font sp", style = MaterialTheme.typography.headlineSmall)
                    FilledTonalIconButton(onClick = { setFont((font + 1).coerceAtMost(28)) }, enabled = font < 28) { Icon(painterResource(R.drawable.ic_add), stringResource(R.string.larger_terminal_text)) }
                }
            }
        }
    }
    val colors: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.settings_terminal_colors), style = MaterialTheme.typography.titleMedium)
            FlowRow(maxItemsInEachRow = 2, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Graphite", "Midnight", "Paper", "Solarized").forEach { name ->
                    val sample = io.github.aqsama.fandroidterminal.terminal.terminalColors(name)
                    FilterChip(modifier = Modifier.weight(1f), selected = palette == name, onClick = { setPalette(name) }, label = { Text(name) },
                        leadingIcon = { Surface(shape = RoundedCornerShape(4.dp), color = Color(sample.second), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                            Text(if (palette == name) "✓" else "Aa", Modifier.padding(3.dp), color = Color(sample.first), style = MaterialTheme.typography.labelSmall)
                        } })
                }
            }
            Surface(shape = RoundedCornerShape(20.dp), color = Color(io.github.aqsama.fandroidterminal.terminal.terminalColors(palette).second)) {
                Text("~/projects $ ls\nnotes  src  README.md", Modifier.fillMaxWidth().padding(16.dp), fontFamily = FontFamily.Monospace, fontSize = font.sp, style = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr), color = Color(io.github.aqsama.fandroidterminal.terminal.terminalColors(palette).first))
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 600.dp) Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Box(Modifier.weight(0.4f)) { sizing() }
            Box(Modifier.weight(0.6f)) { colors() }
        } else Column(verticalArrangement = Arrangement.spacedBy(24.dp)) { sizing(); colors() }
    }
}
