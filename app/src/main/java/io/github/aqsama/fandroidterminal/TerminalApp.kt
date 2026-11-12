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
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
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
    var actionId by rememberSaveable { mutableStateOf<String?>(null) }
    var showArchived by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val appScope = rememberCoroutineScope()
    var selection by remember { mutableStateOf<SelectionController?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val sessions = service?.sessions?.sessions?.collectAsStateWithLifecycle()?.value.orEmpty()
    val environments = service?.environments?.environments?.collectAsStateWithLifecycle()?.value.orEmpty()
    val progress = service?.environments?.progress?.collectAsStateWithLifecycle()?.value.orEmpty()
    val operationError = service?.environments?.error?.collectAsStateWithLifecycle()?.value
    val activeSessions = sessions.filterNot { it.archived }
    val selected = activeSessions.firstOrNull { it.id == selectedId } ?: activeSessions.lastOrNull()
    val actionSession = if (actionId == null) selected else sessions.firstOrNull { it.id == actionId }
    val selectedStatus = selected?.status?.collectAsStateWithLifecycle()?.value
    val acceptsInput = selectedStatus == SessionStatus.Running
    val selectedEnvironment = environments.firstOrNull { it.id == (environmentId ?: selected?.environmentId) }
    LaunchedEffect(selected?.id) { selectedId = selected?.id; if (selected != null) environmentId = selected.environmentId; selection = null }
    fun attempt(action: () -> Unit) { try { action(); error = null } catch (e: Exception) { error = e.message } }
    fun open(id: String?, directory: String? = null) { attempt {
        check(service != null) { "Connecting to terminal service" }
        TerminalService.ensureStarted(context)
        val session = if (id == null) service.sessions.create() else service.sessions.create(id, "Terminal", service.environments.specification(id, directory))
        selectedId = session.id; environmentId = id; page = "Terminal"; overlay = null
    } }
    fun selectSession(session: TerminalSession) {
        service?.sessions?.archive(session.id, false)
        selectedId = session.id; environmentId = session.environmentId; page = "Terminal"; overlay = null
    }
    fun sessionAction(session: TerminalSession, command: String) {
        actionId = session.id
        when (command) {
            "Archive", "Restore" -> {
                val archived = command == "Archive"
                service?.sessions?.archive(session.id, archived)
                if (overlay != "Sessions") overlay = null
                if (archived) appScope.launch {
                    val message = if (session.status.value == SessionStatus.Running) "${session.title} archived · still running" else "${session.title} archived"
                    if (snackbar.showSnackbar(message, "Undo", duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) service?.sessions?.archive(session.id, false)
                }
            }
            "Close" -> overlay = "Close session"
            "Rename" -> overlay = "Rename session"
            else -> overlay = "Session actions"
        }
    }
    fun paste() {
        val text = context.getSystemService(ClipboardManager::class.java).primaryClip?.let { if (it.itemCount > 0) it.getItemAt(0).coerceToText(context).toString() else null } ?: return
        attempt {
            val destination = selected ?: error("Open a session before pasting")
            check(destination.status.value == SessionStatus.Running) { "This session has finished. Open a replacement before pasting." }
            require(text.length <= 1_048_576) { "Paste up to 1 MiB at a time" }
            if ('\n' in text || '\r' in text) { targetSession = destination; pendingText = "Paste multiple lines?" to text }
            else destination.emulator.pasteText(text)
        }
    }
    fun intercept(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown || !event.isCtrlPressed || !event.isShiftPressed) return false
        return when (event.key) {
            Key.P -> { overlay = "Actions"; true }
            Key.N -> { open(selected?.environmentId); true }
            Key.V -> { paste(); true }
            Key.Tab -> { if (activeSessions.isNotEmpty()) selectedId = activeSessions[(activeSessions.indexOf(selected) - 1 + activeSessions.size) % activeSessions.size].id; true }
            else -> false
        }
    }
    BackHandler(page != "Terminal" || overlay != null) { if (overlay != null) overlay = null else page = if (page == "Guest settings") "Environments" else "Terminal" }
    Surface(Modifier.fillMaxSize()) {
    BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
        val wide = maxWidth >= 840.dp
        val drawer = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        fun closeDrawer() { scope.launch { drawer.close() } }
        val sidebar: @Composable () -> Unit = {
            WorkspaceSidebar(page, sessions, environments, selected?.id,
                { page = it; closeDrawer() },
                { selectSession(it); closeDrawer() },
                { session, command -> sessionAction(session, command) },
                { showArchived = true; overlay = "Sessions"; closeDrawer() },
                { open(selected?.environmentId ?: environmentId); closeDrawer() })
        }
        val content: @Composable () -> Unit = {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!wide) IconButton(onClick = { scope.launch { drawer.open() } }) { Icon(painterResource(R.drawable.ic_menu), "Open sidebar") }
                    if (page == "Terminal") {
                        Row(Modifier.weight(1f).semantics { contentDescription = "Switch session" }.clickable { showArchived = false; overlay = "Sessions" }.padding(horizontal = 8.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (selected == null) "Terminal" else environments.firstOrNull { it.id == selected.environmentId }?.name ?: "Android shell", Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Icon(painterResource(R.drawable.ic_chevron_down), null, Modifier.padding(start = 4.dp).size(18.dp))
                        }
                    } else Text("FAndroidTerminal", Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    TextButton(onClick = { overlay = "Actions" }) { Icon(painterResource(R.drawable.ic_tools), null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Tools") }
                    if (page == "Terminal") IconButton(onClick = { selected?.let { sessionAction(it, "Menu") } }, enabled = selected != null) { Icon(painterResource(R.drawable.ic_more), "Session actions") }
                }
                val tabs = activeSessions.filter { it.environmentId == selected?.environmentId }
                if (page == "Terminal" && tabs.size > 1) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    tabs.forEach { session -> FilterChip(session.id == selected?.id, { selectSession(session) }, label = { Text(session.title, maxLines = 1) }, leadingIcon = { Icon(painterResource(R.drawable.ic_terminal), null, Modifier.size(16.dp)) }) }
                    IconButton(onClick = { open(selected?.environmentId) }, enabled = sessions.size < 12) { Icon(painterResource(R.drawable.ic_add), "New session") }
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
                        "Files" -> if (selectedEnvironment != null) FilesPage(service, selectedEnvironment.id, preferences, workspace.draft(selectedEnvironment.id), { showArchived = false; overlay = "Sessions" }) { id, dir -> open(id, dir) } else Page("Files", "Choose a Linux environment to browse its files.") { Button(onClick = { page = "Environments" }) { Text("Choose environment") } }
                        else -> if (selected != null) SessionBody(selected, preferences, { paste() }, ::intercept, { selection = it }, { open(selected.environmentId) }) else Page("Terminal", if (sessions.isEmpty()) "Open a session to start working." else "Your sessions are archived. Their processes may still be running.") {
                            Button(onClick = { open(null) }) { Icon(painterResource(R.drawable.ic_add), null); Spacer(Modifier.width(8.dp)); Text("New Android shell") }
                            if (sessions.isNotEmpty()) OutlinedButton(onClick = { showArchived = true; overlay = "Sessions" }) { Icon(painterResource(R.drawable.ic_archive), null); Spacer(Modifier.width(8.dp)); Text("View archived sessions") }
                        }
                    }
                }
            }
        }
        Box(Modifier.fillMaxSize()) {
        if (wide) PermanentNavigationDrawer(drawerContent = { PermanentDrawerSheet { sidebar() } }, content = content)
        else ModalNavigationDrawer(drawerState = drawer, gesturesEnabled = !drawer.isOpen && page != "Terminal", drawerContent = { ModalDrawerSheet { sidebar() } }, content = content)
        if (overlay != "Sessions") SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(12.dp))
        }
    }
    }
    if (overlay == "Sessions") ModalBottomSheet(onDismissRequest = { overlay = null }) {
        Column(Modifier.fillMaxWidth().heightIn(max = 640.dp)) {
        LazyColumn(Modifier.weight(1f, fill = false).fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text("Sessions", style = MaterialTheme.typography.headlineSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(!showArchived, { showArchived = false }, label = { Text("Active (${activeSessions.size})") })
                    FilterChip(showArchived, { showArchived = true }, label = { Text("Archived (${sessions.size - activeSessions.size})") }, leadingIcon = { Icon(painterResource(R.drawable.ic_archive), null, Modifier.size(18.dp)) })
                }
                if (showArchived) Text("Archiving hides a session without stopping its process. Restore to continue. Sessions and terminal history end if Android stops the app.", style = MaterialTheme.typography.bodySmall)
                else TextButton(onClick = { open(selected?.environmentId ?: environmentId) }, enabled = service != null && sessions.size < 12) { Icon(painterResource(R.drawable.ic_add), null); Spacer(Modifier.width(8.dp)); Text("New session") }
            }
            val listed = sessions.filter { it.archived == showArchived }
            if (listed.isEmpty()) item { Text(if (showArchived) "No archived sessions" else "No active sessions", Modifier.padding(16.dp)) }
            (listOf(null to "Android shell") + environments.map { it.id to it.name }).forEach { (id, name) ->
                val group = listed.filter { it.environmentId == id }
                if (group.isNotEmpty()) {
                    item { Text(name, Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(group, key = { it.id }) { session -> SessionRow(session, session.id == selected?.id, { selectSession(session) }) { sessionAction(session, it) } }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        }
    }
    if (overlay == "Session actions" && actionSession != null) ModalBottomSheet(onDismissRequest = { overlay = null }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Text(actionSession.title, Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = MaterialTheme.typography.titleLarge)
            Text(if (actionSession.environmentId == null) "Android shell · app permissions" else "Linux · PRoot · app permissions", Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            fun perform(command: String) = sessionAction(actionSession, command)
            SheetAction("New session", R.drawable.ic_add, enabled = sessions.size < 12) { open(actionSession.environmentId) }
            SheetAction("Copy selected text", R.drawable.ic_copy, enabled = actionSession.id == selected?.id && selection != null) {
                selection?.copySelection()?.let { context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Terminal selection", it)) }; overlay = null
            }
            SheetAction("Paste", R.drawable.ic_paste, enabled = actionSession.id == selected?.id && acceptsInput) { overlay = null; paste() }
            SheetAction("Rename", R.drawable.ic_edit) { perform("Rename") }
            SheetAction("Environment files", R.drawable.ic_files, enabled = actionSession.environmentId != null) { environmentId = actionSession.environmentId; page = "Files"; overlay = null }
            SheetAction(if (actionSession.archived) "Restore session" else "Archive session", if (actionSession.archived) R.drawable.ic_restore else R.drawable.ic_archive,
                description = if (actionSession.archived) "Return to the active list" else "Hide from the active list. Keep the process running.") { perform(if (actionSession.archived) "Restore" else "Archive") }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SheetAction("Close session…", R.drawable.ic_delete, destructive = true, description = "Stops the process. Environment files remain.") { perform("Close") }
        }
    }
    if (overlay == "Close session" && actionSession != null) Prompt("Close ${actionSession.title}?", label = "", message = "This stops this session’s process and removes its terminal history. Environment files remain. This cannot be undone.", confirm = "Close session", destructive = true, dismiss = { overlay = null }) { service?.sessions?.remove(actionSession.id) }
    if (overlay == "Rename session" && actionSession != null) Prompt("Rename session", initial = actionSession.title, dismiss = { overlay = null }) { name -> actionSession.title = io.github.aqsama.fandroidterminal.runtime.linux.EnvironmentFiles.validateName(name) }
    if (overlay == "Actions") {
        var query by remember { mutableStateOf("") }
        var category by remember { mutableStateOf("All") }
        val actions = listOf(
            WorkspaceAction("New session", R.drawable.ic_add, "Sessions", "Ctrl+Shift+N", service != null && sessions.size < 12) { open(selected?.environmentId) },
            WorkspaceAction("Switch session", R.drawable.ic_terminal, "Sessions", "Ctrl+Shift+Tab", activeSessions.isNotEmpty()) { showArchived = false; overlay = "Sessions" },
            WorkspaceAction("Archived sessions", R.drawable.ic_archive, "Sessions") { showArchived = true; overlay = "Sessions" },
            WorkspaceAction("Paste", R.drawable.ic_paste, "Clipboard", "Ctrl+Shift+V", acceptsInput) { overlay = null; paste() },
            WorkspaceAction("Select visible text", R.drawable.ic_terminal, "Clipboard", enabled = selection != null && page == "Terminal") { selection?.selectAllVisible(); overlay = null },
            WorkspaceAction("Copy selection", R.drawable.ic_paste, "Clipboard", enabled = selection != null && page == "Terminal") { selection?.copySelection()?.let { context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Terminal selection", it)) }; overlay = null },
            WorkspaceAction("Environments", R.drawable.ic_environments) { page = "Environments"; overlay = null },
            WorkspaceAction("Files", R.drawable.ic_files) { page = "Files"; overlay = null },
            WorkspaceAction("Settings", R.drawable.ic_settings) { page = "Settings"; overlay = null },
            WorkspaceAction("Open source licenses", R.drawable.ic_info) { overlay = "Licenses" },
        ) + preferences.snippets.lines().filter { it.isNotBlank() }.map { command -> WorkspaceAction(command, R.drawable.ic_commands, "Saved commands", enabled = acceptsInput) { targetSession = selected; pendingText = "Run saved command?" to "$command\n"; overlay = null } }
        val results = actions.filter { (category == "All" || category == it.category) && it.label.contains(query, true) }
        ModalBottomSheet(onDismissRequest = { overlay = null }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Tools", style = MaterialTheme.typography.headlineSmall)
                if (selected != null) Text("${selected.title} · ${statusLabel(selectedStatus ?: SessionStatus.Starting)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(query, { query = it }, label = { Text("Find an action or command") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("All", "Sessions", "Clipboard", "Saved commands").forEach { name -> FilterChip(category == name, { category = name }, label = { Text(name) }) } }
                if (category == "Saved commands") Text(if (!acceptsInput) "Open a running session to use saved commands." else "Runs in ${selected.title} after confirmation.", style = MaterialTheme.typography.bodySmall)
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    if (results.isEmpty()) item { Text(if (category == "Saved commands" && query.isBlank()) "Add commands in Settings → Saved commands." else "No matching actions.", Modifier.padding(12.dp)) }
                    items(results) { action -> ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable(enabled = action.enabled) { attempt(action.invoke) },
                        headlineContent = { Text(action.label, color = if (action.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)) },
                        leadingContent = { Icon(painterResource(action.icon), null, tint = if (action.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)) },
                        supportingContent = { action.shortcut?.let { Text(it, style = MaterialTheme.typography.labelMedium) } },
                    ) }
                }
            }
        }
    }

    if (overlay == "Licenses") {
        val notices = remember { context.assets.open("open-source-notices.txt").bufferedReader().use { it.readText() } }
        AlertDialog(onDismissRequest = { overlay = null }, title = { Text("Open source licenses") }, text = { Text(notices, Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) }, confirmButton = { TextButton(onClick = { overlay = null }) { Text("Close") } })
    }
    pendingText?.let { (title, text) -> AlertDialog(onDismissRequest = { pendingText = null }, title = { Text(title) }, text = { Text("${targetSession?.title ?: "No selected session"}\n${text.take(1000)}\n\nPasted newlines may execute commands.", Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) }, confirmButton = { TextButton(onClick = { attempt { val destination = sessions.firstOrNull { it.id == targetSession?.id } ?: error("Session closed. Select another session."); check(destination.status.value == SessionStatus.Running) { "This session has finished. Open a replacement before sending input." }; destination.emulator.pasteText(text) }; pendingText = null }) { Text(if (title.startsWith("Run")) "Run" else "Paste") } }, dismissButton = { TextButton(onClick = { pendingText = null }) { Text("Cancel") } }) }
}

internal fun statusLabel(status: SessionStatus) = when (status) { SessionStatus.Starting -> "Starting…"; SessionStatus.Running -> "Running"; is SessionStatus.Exited -> "Exited · ${status.code}"; is SessionStatus.Failed -> "Failed · ${status.message}" }

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
                else TextButton(onClick = { val code = when (label) { "Esc" -> VTermKey.ESCAPE; "Tab" -> VTermKey.TAB; "Up" -> VTermKey.UP; "Down" -> VTermKey.DOWN; "Left" -> VTermKey.LEFT; "Right" -> VTermKey.RIGHT; "Home" -> VTermKey.HOME; "End" -> VTermKey.END; "PgUp" -> VTermKey.PAGEUP; "PgDn" -> VTermKey.PAGEDOWN; "Insert" -> VTermKey.INS; else -> VTermKey.DEL }; session.emulator.dispatchKey(modifiers.mask(), code); modifiers.clearTransients() }) { Text(when (label) { "Up" -> "↑"; "Down" -> "↓"; "Left" -> "←"; "Right" -> "→"; else -> label }, modifier = Modifier.semantics { contentDescription = label }) }
            }
            IconButton(onClick = { paste() }, enabled = status == SessionStatus.Running) { Icon(painterResource(R.drawable.ic_paste), "Paste") }
            IconButton(onClick = { keyboard?.hide() }) { Icon(painterResource(R.drawable.ic_keyboard_hide), "Hide keyboard") }
        }
    }
}

private data class WorkspaceAction(val label: String, val icon: Int, val category: String = "Actions", val shortcut: String? = null, val enabled: Boolean = true, val invoke: () -> Unit)

@Composable
internal fun SheetAction(label: String, icon: Int, enabled: Boolean = true, destructive: Boolean = false, description: String? = null, click: () -> Unit) {
    val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    ListItem(colors = ListItemDefaults.colors(containerColor = Color.Transparent), modifier = Modifier.clickable(enabled = enabled, onClick = click), headlineContent = { Text(label, color = color.copy(alpha = if (enabled) 1f else .38f)) },
        leadingContent = { Icon(painterResource(icon), null, tint = color.copy(alpha = if (enabled) 1f else .38f)) },
        supportingContent = { description?.let { Text(it) } })
}
