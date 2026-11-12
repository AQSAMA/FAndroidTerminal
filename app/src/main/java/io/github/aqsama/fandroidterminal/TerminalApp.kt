// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aqsama.fandroidterminal.sessions.*
import io.github.aqsama.fandroidterminal.terminal.TerminalSurface
import org.connectbot.terminal.*

private class TerminalModifiers : ModifierManager {
    var ctrl by mutableStateOf(false); var alt by mutableStateOf(false)
    override fun isCtrlActive() = ctrl
    override fun isAltActive() = alt
    override fun isShiftActive() = false
    override fun clearTransients() { ctrl = false; alt = false }
    fun mask() = (if (ctrl) 4 else 0) or (if (alt) 2 else 0)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TerminalApp(service: TerminalService?, workspace: WorkspaceState) {
    val context = LocalContext.current
    val preferences = remember { Preferences(context) }
    var page by rememberSaveable { mutableStateOf("Terminal") }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var environmentId by rememberSaveable { mutableStateOf<String?>(null) }
    var overlay by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingText by remember { mutableStateOf<Pair<String, String>?>(null) }
    var targetSession by remember { mutableStateOf<TerminalSession?>(null) }
    var selection by remember { mutableStateOf<SelectionController?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val sessions = service?.sessions?.sessions?.collectAsStateWithLifecycle()?.value.orEmpty()
    val environments = service?.environments?.environments?.collectAsStateWithLifecycle()?.value.orEmpty()
    val progress = service?.environments?.progress?.collectAsStateWithLifecycle()?.value.orEmpty()
    val operationError = service?.environments?.error?.collectAsStateWithLifecycle()?.value
    val selected = sessions.firstOrNull { it.id == selectedId } ?: sessions.lastOrNull()
    val selectedEnvironment = environments.firstOrNull { it.id == (environmentId ?: selected?.environmentId) }
    LaunchedEffect(selected?.id) { selectedId = selected?.id; selection = null }
    fun attempt(action: () -> Unit) { try { action(); error = null } catch (e: Exception) { error = e.message } }
    fun open(id: String?, directory: String? = null) { attempt {
        check(service != null) { "Connecting to terminal service" }
        TerminalService.ensureStarted(context)
        val session = if (id == null) service.sessions.create() else service.sessions.create(id, environments.first { it.id == id }.name, service.environments.specification(id, directory))
        selectedId = session.id; environmentId = id; page = "Terminal"; overlay = null
    } }
    fun paste() {
        val text = context.getSystemService(ClipboardManager::class.java).primaryClip?.let { if (it.itemCount > 0) it.getItemAt(0).coerceToText(context).toString() else null } ?: return
        attempt {
            require(text.length <= 1_048_576) { "Paste up to 1 MiB at a time" }
            if ('\n' in text || '\r' in text) { targetSession = selected; pendingText = "Paste multiple lines?" to text }
            else selected?.emulator?.pasteText(text)
        }
    }
    fun intercept(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown || !event.isCtrlPressed || !event.isShiftPressed) return false
        return when (event.key) {
            Key.P -> { overlay = "Actions"; true }
            Key.N -> { open(selected?.environmentId); true }
            Key.V -> { paste(); true }
            Key.Tab -> { if (sessions.isNotEmpty()) selectedId = sessions[(sessions.indexOf(selected) - 1 + sessions.size) % sessions.size].id; true }
            else -> false
        }
    }
    BackHandler(page != "Terminal" || overlay != null) { if (overlay != null) overlay = null else page = "Terminal" }
    val destinations = listOf("Terminal", "Environments", "Files", "Settings")
    Surface(Modifier.fillMaxSize()) {
    BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
        val wide = maxWidth >= 720.dp
        Row {
            if (wide) NavigationRail { destinations.forEach { destination -> NavigationRailItem(page == destination, { page = destination }, icon = { Icon(painterResource(iconFor(destination)), destination) }, label = { Text(destination) }) } }
            Column(Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(modifier = Modifier.weight(1f).semantics { contentDescription = "Switch session" }, onClick = { overlay = "Sessions" }) { Text(if (page == "Terminal") selected?.title ?: "Workspace" else "Sessions (${sessions.size})", maxLines = 1) }
                    IconButton(onClick = { overlay = "Actions" }) { Icon(painterResource(R.drawable.ic_actions), "Actions and shortcuts") }
                    IconButton(onClick = { overlay = "Session actions" }, enabled = selected != null) { Icon(painterResource(R.drawable.ic_more), "Session actions") }
                }
                if (progress.isNotEmpty()) Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Row(verticalAlignment = Alignment.CenterVertically) { Text(progress.values.first(), Modifier.weight(1f), maxLines = 2, style = MaterialTheme.typography.bodySmall); TextButton(onClick = { service?.environments?.cancel(progress.keys.first()) }) { Text("Cancel") } }
                    }
                }
                (error ?: operationError)?.let { message -> Surface(color = MaterialTheme.colorScheme.errorContainer) { Text(message, Modifier.fillMaxWidth().padding(12.dp), style = MaterialTheme.typography.bodySmall) } }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (service == null) CircularProgressIndicator(Modifier.align(Alignment.Center))
                    else when (page) {
                        "Environments" -> EnvironmentsPage(service, { id, dir -> open(id, dir) }, { environmentId = it; page = "Files" }, { environmentId = it; page = "Guest settings" })
                        "Settings" -> SettingsPage(preferences)
                        "Guest settings" -> environmentId?.let { GuestSettingsPage(service, it) }
                        "Files" -> if (selectedEnvironment != null) FilesPage(service, selectedEnvironment.id, preferences, workspace.draft(selectedEnvironment.id)) { id, dir -> open(id, dir) } else Page("Files", "Choose a Linux environment to browse its files.") { Button(onClick = { page = "Environments" }) { Text("Choose environment") } }
                        else -> if (selected != null) SessionBody(selected, preferences, { paste() }, ::intercept, { selection = it }, { open(selected.environmentId) }) else Page("Your terminal", "Android shell and independent Linux workspaces, together.") {
                            Button(onClick = { open(null) }) { Text("Open Android shell") }
                            OutlinedButton(onClick = { page = "Environments" }) { Text("Install or open Linux") }
                        }
                    }
                }
                if (!wide && !WindowInsets.isImeVisible) NavigationBar {
                    destinations.forEach { destination -> NavigationBarItem(page == destination, { page = destination }, icon = { Icon(painterResource(iconFor(destination)), destination) }, label = { Text(destination) }) }
                }
            }
        }
    }
    }
    if (overlay == "Sessions") ModalBottomSheet(onDismissRequest = { overlay = null }) {
        LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            item { Text("Sessions", style = MaterialTheme.typography.headlineSmall); TextButton(onClick = { open(null) }) { Text("New Android shell") }; TextButton(onClick = { page = "Environments"; overlay = null }) { Text("Open / manage Linux") } }
            (listOf(null to "Android shell") + environments.map { it.id to it.name }).forEach { (id, name) ->
                val group = sessions.filter { it.environmentId == id }
                if (group.isNotEmpty()) {
                    item { Text("$name · ${group.size}", Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.titleMedium) }
                    items(group, key = { it.id }) { session ->
                        val status by session.status.collectAsStateWithLifecycle()
                        ListItem(modifier = Modifier.semantics { contentDescription = "Select session ${session.title}" }.clickable { selectedId = session.id; environmentId = id; page = "Terminal"; overlay = null }, headlineContent = { Text(session.title) }, supportingContent = { Text(statusLabel(status)) }, leadingContent = { RadioButton(session.id == selected?.id, { selectedId = session.id; environmentId = id; page = "Terminal"; overlay = null }) }, trailingContent = { TextButton(onClick = { selectedId = session.id; overlay = "Session actions" }) { Text("Actions") } })
                    }
                }
            }
        }
    }
    if (overlay == "Session actions" && selected != null) AlertDialog(onDismissRequest = { overlay = null }, title = { Text(selected.title) }, text = {
        Column {
            Text(if (selected.environmentId == null) "Android shell · App UID" else "Linux · PRoot · App UID")
            TextButton(onClick = { open(selected.environmentId) }) { Text("New session in this environment") }
            TextButton(onClick = { overlay = "Rename session" }) { Text("Rename session") }
            TextButton(onClick = { environmentId = selected.environmentId; page = "Files"; overlay = null }, enabled = selected.environmentId != null) { Text("Environment files") }
            TextButton(onClick = { overlay = "Close session" }) { Text("Close session…") }
        }
    }, confirmButton = { TextButton(onClick = { overlay = null }) { Text("Done") } })
    if (overlay == "Close session" && selected != null) Prompt("Close ${selected.title}?", label = "", message = "This terminates this session’s running process. Environment files remain.", confirm = "Close session", dismiss = { overlay = null }) { service?.sessions?.remove(selected.id) }
    if (overlay == "Rename session" && selected != null) Prompt("Rename session", initial = selected.title, dismiss = { overlay = null }) { name -> require(name.isNotBlank() && name.length <= 60); selected.title = name }
    if (overlay == "Actions") {
        var query by remember { mutableStateOf("") }
        val actions = listOf(
            "New session · Ctrl+Shift+N" to { open(selected?.environmentId) },
            "Switch session · Ctrl+Shift+Tab" to { overlay = "Sessions" },
            "Paste · Ctrl+Shift+V" to { overlay = null; paste() },
            "Select visible text" to { selection?.selectAllVisible(); overlay = null },
            "Copy selection" to { selection?.copySelection()?.let { context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Terminal selection", it)) }; overlay = null },
            "Environments" to { page = "Environments"; overlay = null },
            "Files" to { page = "Files"; overlay = null },
            "Settings and shortcut help" to { page = "Settings"; overlay = null },
            "Open source licenses" to { overlay = "Licenses" },
        ) + preferences.snippets.lines().filter { it.isNotBlank() }.map { command -> "Run: $command" to { targetSession = selected; pendingText = "Run saved command?" to "$command\n"; overlay = null } }
        AlertDialog(onDismissRequest = { overlay = null }, title = { Text("Actions") }, text = {
            Column { OutlinedTextField(query, { query = it }, label = { Text("Search actions or saved commands") }, singleLine = true)
                LazyColumn(Modifier.heightIn(max = 360.dp)) { items(actions.filter { it.first.contains(query, true) }) { (label, action) -> TextButton(onClick = { attempt(action) }) { Text(label) } } }
            }
        }, confirmButton = { TextButton(onClick = { overlay = null }) { Text("Close") } })
    }
    if (overlay == "Licenses") {
        val notices = remember { context.assets.open("open-source-notices.txt").bufferedReader().use { it.readText() } }
        AlertDialog(onDismissRequest = { overlay = null }, title = { Text("Open source licenses") }, text = { Text(notices, Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) }, confirmButton = { TextButton(onClick = { overlay = null }) { Text("Close") } })
    }
    pendingText?.let { (title, text) -> AlertDialog(onDismissRequest = { pendingText = null }, title = { Text(title) }, text = { Text("${targetSession?.title ?: "No selected session"}\n${text.take(1000)}\n\nPasted newlines may execute commands.", Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) }, confirmButton = { TextButton(onClick = { attempt { check(sessions.any { it.id == targetSession?.id }) { "Session closed. Select another session." }; targetSession?.emulator?.pasteText(text) }; pendingText = null }) { Text(if (title.startsWith("Run")) "Run" else "Paste") } }, dismissButton = { TextButton(onClick = { pendingText = null }) { Text("Cancel") } }) }
}

private fun iconFor(page: String) = when (page) { "Terminal" -> R.drawable.ic_terminal; "Environments" -> R.drawable.ic_environments; "Files" -> R.drawable.ic_files; else -> R.drawable.ic_settings }
private fun statusLabel(status: SessionStatus) = when (status) { SessionStatus.Starting -> "Starting…"; SessionStatus.Running -> "Running"; is SessionStatus.Exited -> "Exited · ${status.code}"; is SessionStatus.Failed -> "Failed · ${status.message}" }

@Composable
private fun SessionBody(session: TerminalSession, preferences: Preferences, paste: () -> Unit, intercept: (KeyEvent) -> Boolean, selection: (SelectionController) -> Unit, reopen: () -> Unit) {
    val status by session.status.collectAsStateWithLifecycle()
    val modifiers = remember(session.id) { TerminalModifiers() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(preferences.palette, session.id) {
        session.emulator.setDefaultColors(if (preferences.palette == "Paper") 0xFF202124.toInt() else if (preferences.palette == "Solarized") 0xFF93A1A1.toInt() else 0xFFE6E1E5.toInt(), if (preferences.palette == "Paper") 0xFFFAFAFA.toInt() else if (preferences.palette == "Solarized") 0xFF002B36.toInt() else 0xFF141218.toInt())
    }
    Column(Modifier.fillMaxSize()) {
        if (status !is SessionStatus.Running) Row(verticalAlignment = Alignment.CenterVertically) {
            Text(statusLabel(status), Modifier.weight(1f).padding(8.dp), style = MaterialTheme.typography.bodySmall)
            if (status is SessionStatus.Exited || status is SessionStatus.Failed) TextButton(onClick = reopen) { Text("Open replacement") }
        }
        Box(Modifier.weight(1f).fillMaxWidth().background(Color(0xFF141218))) {
            key(session.id, preferences.font) { TerminalSurface(session.emulator, modifiers, Modifier.fillMaxSize(), paste, preferences.font, preferences.palette, intercept, selection) }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            preferences.keys.split(' ').filter { it.isNotBlank() }.forEach { label ->
                if (label == "Ctrl" || label == "Alt") FilterChip(if (label == "Ctrl") modifiers.ctrl else modifiers.alt, { if (label == "Ctrl") modifiers.ctrl = !modifiers.ctrl else modifiers.alt = !modifiers.alt }, label = { Text(label) })
                else TextButton(onClick = { val code = when (label) { "Esc" -> VTermKey.ESCAPE; "Tab" -> VTermKey.TAB; "Up" -> VTermKey.UP; "Down" -> VTermKey.DOWN; "Left" -> VTermKey.LEFT; "Right" -> VTermKey.RIGHT; "Home" -> VTermKey.HOME; "End" -> VTermKey.END; "PgUp" -> VTermKey.PAGEUP; "PgDn" -> VTermKey.PAGEDOWN; "Insert" -> VTermKey.INS; else -> VTermKey.DEL }; session.emulator.dispatchKey(modifiers.mask(), code); modifiers.clearTransients() }) { Text(label) }
            }
            IconButton(onClick = { paste() }) { Icon(painterResource(R.drawable.ic_paste), "Paste") }
            IconButton(onClick = { keyboard?.hide() }) { Icon(painterResource(R.drawable.ic_keyboard_hide), "Hide keyboard") }
        }
    }
}
