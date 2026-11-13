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
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.layout.onSizeChanged
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
import androidx.compose.ui.res.stringResource
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
    var sessionScopeId by rememberSaveable { mutableStateOf<String?>(null) }
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
    val previousSession = sessions.firstOrNull { it.id == selectedId }
    val selected = activeSessions.firstOrNull { it.id == selectedId } ?: activeSessions.lastOrNull { it.environmentId == previousSession?.environmentId } ?: activeSessions.lastOrNull()
    val actionSession = if (actionId == null) selected else sessions.firstOrNull { it.id == actionId }
    val selectedStatus = selected?.status?.collectAsStateWithLifecycle()?.value
    val acceptsInput = selectedStatus == SessionStatus.Running
    val selectedEnvironment = environments.firstOrNull { it.id == (environmentId ?: selected?.environmentId) }
    LaunchedEffect(service, selected?.id) {
        // Preserve the saved selection while Activity recreation reconnects to the service.
        if (service != null) { selectedId = selected?.id; if (selected != null && page == "Terminal") environmentId = selected.environmentId }
        selection = null
    }
    fun environmentName(session: TerminalSession): String = environments.firstOrNull { it.id == session.environmentId }?.name ?: if (session.environmentId == null) context.getString(R.string.android_shell) else "Linux"
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
                val wasSelected = selected?.id == session.id
                service?.sessions?.archive(session.id, archived)
                if (overlay != "Sessions") overlay = null
                if (archived) appScope.launch {
                    val identity = context.getString(R.string.session_identity, session.title, environmentName(session))
                    val message = context.getString(if (session.status.value == SessionStatus.Running) R.string.archive_running_notice else R.string.archive_notice, identity)
                    val result = snackbar.showSnackbar(message, context.getString(R.string.undo_label), duration = SnackbarDuration.Short)
                    val manager = service?.sessions
                    if (result == SnackbarResult.ActionPerformed && manager != null && manager.sessions.value.any { it.id == session.id }) {
                        manager.archive(session.id, false)
                        if (wasSelected) { selectedId = session.id; if (page == "Terminal") environmentId = session.environmentId }
                    }
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
                { sessionScopeId = null; showArchived = true; overlay = "Sessions"; closeDrawer() },
                { open(selected?.environmentId ?: environmentId); closeDrawer() })
        }
        val content: @Composable () -> Unit = {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!wide) IconButton(onClick = { scope.launch { drawer.open() } }) { Icon(painterResource(R.drawable.ic_menu), stringResource(R.string.open_sidebar)) }
                    if (page == "Terminal") {
                        val switchLabel = stringResource(R.string.switch_session)
                        Row(Modifier.weight(1f).semantics { contentDescription = switchLabel }.clickable { sessionScopeId = null; showArchived = false; overlay = "Sessions" }.padding(horizontal = 8.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (selected == null) stringResource(R.string.terminal_label) else environmentName(selected), Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Icon(painterResource(R.drawable.ic_chevron_down), null, Modifier.padding(start = 4.dp).size(18.dp))
                        }
                    } else Text("FAndroidTerminal", Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    TextButton(onClick = { overlay = "Actions" }) { Icon(painterResource(R.drawable.ic_tools), null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.tools_label)) }
                    if (page == "Terminal") IconButton(onClick = { selected?.let { sessionAction(it, "Menu") } }, enabled = selected != null) { Icon(painterResource(R.drawable.ic_more), stringResource(R.string.session_actions)) }
                }
                val tabs = activeSessions.filter { it.environmentId == selected?.environmentId }
                if (page == "Terminal" && tabs.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        tabs.forEach { session ->
                            key(session.id) {
                            val fullName = stringResource(R.string.session_tab_a11y, session.title, environmentName(session))
                            val requester = remember { BringIntoViewRequester() }
                            var measuredWidth by remember { mutableIntStateOf(0) }
                            val isSelected = session.id == selected?.id
                            LaunchedEffect(isSelected, measuredWidth) { if (isSelected && measuredWidth > 0) requester.bringIntoView() }
                            FilterChip(isSelected, { selectSession(session) }, modifier = Modifier.widthIn(max = 180.dp).bringIntoViewRequester(requester).onSizeChanged { measuredWidth = it.width }.semantics { contentDescription = fullName },
                                label = { Text(session.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }, leadingIcon = { Icon(painterResource(R.drawable.ic_terminal), null, Modifier.size(16.dp)) })
                            }
                        }
                    }
                    IconButton(onClick = { open(selected?.environmentId) }, enabled = sessions.size < 12) { Icon(painterResource(R.drawable.ic_add), stringResource(R.string.new_session)) }
                }
                if (page == "Terminal" && sessions.size >= 12) Text(stringResource(R.string.session_limit_hint, sessions.count { it.archived }), Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (progress.isNotEmpty()) Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Row(verticalAlignment = Alignment.CenterVertically) { Text(progress.values.first(), Modifier.weight(1f), maxLines = 2, style = MaterialTheme.typography.bodySmall); TextButton(onClick = { service?.environments?.cancel(progress.keys.first()) }) { Text(stringResource(R.string.cancel_label)) } }
                    }
                }
                (error ?: operationError)?.let { message -> Surface(color = MaterialTheme.colorScheme.errorContainer) { Text(message, Modifier.fillMaxWidth().padding(12.dp), style = MaterialTheme.typography.bodySmall) } }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (service == null) CircularProgressIndicator(Modifier.align(Alignment.Center))
                    else when (page) {
                        "Environments" -> EnvironmentsPage(service, { id, dir -> open(id, dir) }, { environmentId = it; page = "Files" }, { environmentId = it; page = "Guest settings" })
                        "Settings" -> SettingsPage(preferences)
                        "Guest settings" -> environmentId?.let { id -> GuestSettingsPage(service, id) { sessionScopeId = id; overlay = "Sessions" } }
                        "Files" -> if (selectedEnvironment != null) FilesPage(service, selectedEnvironment.id, preferences, workspace.draft(selectedEnvironment.id), { sessionScopeId = selectedEnvironment.id; overlay = "Sessions" }) { id, dir -> open(id, dir) } else Page("Files", "Choose a Linux environment to browse its files.") { Button(onClick = { page = "Environments" }) { Text("Choose environment") } }
                        else -> if (selected != null) SessionBody(selected, preferences, { paste() }, ::intercept, { selection = it }, { open(selected.environmentId) }) else Page(stringResource(R.string.terminal_label), stringResource(if (sessions.isEmpty()) R.string.open_terminal_hint else R.string.archived_terminal_empty)) {
                            Button(onClick = { open(null) }, enabled = sessions.size < 12) { Icon(painterResource(R.drawable.ic_add), null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.new_android_shell)) }
                            if (sessions.isNotEmpty()) OutlinedButton(onClick = { sessionScopeId = null; showArchived = true; overlay = "Sessions" }) { Icon(painterResource(R.drawable.ic_archive), null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.view_archived_sessions)) }
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
            item { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SheetHeader(if (sessionScopeId == null) stringResource(R.string.sessions_label) else stringResource(R.string.owned_sessions_title, environments.firstOrNull { it.id == sessionScopeId }?.name ?: "Linux"), { overlay = null })
                if (sessionScopeId == null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(!showArchived, { showArchived = false }, label = { Text(stringResource(R.string.visible_count, activeSessions.size)) })
                    FilterChip(showArchived, { showArchived = true }, label = { Text(stringResource(R.string.archived_count, sessions.size - activeSessions.size)) }, leadingIcon = { Icon(painterResource(R.drawable.ic_archive), null, Modifier.size(18.dp)) })
                }
                if (sessionScopeId != null) Text(stringResource(R.string.owned_sessions_help), style = MaterialTheme.typography.bodySmall)
                else if (showArchived) Text(stringResource(R.string.archive_explanation), style = MaterialTheme.typography.bodySmall)
                else TextButton(onClick = { open(selected?.environmentId ?: environmentId) }, enabled = service != null && sessions.size < 12) { Icon(painterResource(R.drawable.ic_add), null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.new_session)) }
                if (sessions.size >= 12) Text(stringResource(R.string.session_limit_hint, sessions.count { it.archived }), style = MaterialTheme.typography.bodySmall)
            } }
            val listed = sessions.filter { if (sessionScopeId != null) it.environmentId == sessionScopeId else it.archived == showArchived }
            if (listed.isEmpty()) item { Text(stringResource(if (sessionScopeId != null) R.string.no_owned_sessions else if (showArchived) R.string.no_archived_sessions else R.string.no_visible_sessions), Modifier.padding(16.dp)) }
            (listOf(null to context.getString(R.string.android_shell)) + environments.map { it.id to it.name }).forEach { (id, name) ->
                val group = listed.filter { it.environmentId == id }
                if (group.isNotEmpty()) {
                    item { Text(name, Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(group, key = { it.id }) { session -> SessionRow(session, name, session.id == selected?.id, { selectSession(session) }) { sessionAction(session, it) } }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        }
    }
    if (overlay == "Session actions" && actionSession != null) ModalBottomSheet(onDismissRequest = { overlay = null }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            SheetHeader(stringResource(R.string.session_identity, actionSession.title, environmentName(actionSession)), { overlay = null }, Modifier.padding(horizontal = 24.dp))
            Text(stringResource(if (actionSession.environmentId == null) R.string.shell_permissions else R.string.linux_permissions), Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            fun perform(command: String) = sessionAction(actionSession, command)
            SheetAction(stringResource(R.string.new_session), R.drawable.ic_add, enabled = sessions.size < 12, description = if (sessions.size >= 12) stringResource(R.string.session_limit_hint, sessions.count { it.archived }) else null) { open(actionSession.environmentId) }
            SheetAction(stringResource(R.string.copy_selected), R.drawable.ic_copy, enabled = actionSession.id == selected?.id && selection != null) {
                selection?.copySelection()?.let { context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Terminal selection", it)) }; overlay = null
            }
            SheetAction(stringResource(R.string.paste_label), R.drawable.ic_paste, enabled = actionSession.id == selected?.id && acceptsInput) { overlay = null; paste() }
            SheetAction(stringResource(R.string.rename_label), R.drawable.ic_edit) { perform("Rename") }
            SheetAction(stringResource(R.string.environment_files), R.drawable.ic_files, enabled = actionSession.environmentId != null) { environmentId = actionSession.environmentId; page = "Files"; overlay = null }
            SheetAction(stringResource(if (actionSession.archived) R.string.restore_session else R.string.archive_session), if (actionSession.archived) R.drawable.ic_restore else R.drawable.ic_archive,
                description = stringResource(if (actionSession.archived) R.string.restore_action_help else R.string.archive_action_help)) { perform(if (actionSession.archived) "Restore" else "Archive") }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SheetAction(stringResource(R.string.close_session_menu), R.drawable.ic_delete, destructive = true, description = stringResource(R.string.close_action_help)) { perform("Close") }
        }
    }
    if (overlay == "Close session" && actionSession != null) Prompt(stringResource(R.string.close_named_session, actionSession.title, environmentName(actionSession)), label = "", message = stringResource(R.string.close_named_details, actionSession.title, environmentName(actionSession)), confirm = stringResource(R.string.close_session), destructive = true, dismiss = { overlay = null }) { service?.sessions?.remove(actionSession.id) }
    if (overlay == "Rename session" && actionSession != null) Prompt(stringResource(R.string.rename_label), initial = actionSession.title, dismiss = { overlay = null }) { name -> actionSession.title = io.github.aqsama.fandroidterminal.runtime.linux.EnvironmentFiles.validateName(name) }
    if (overlay == "Actions") {
        var query by remember { mutableStateOf("") }
        var category by remember { mutableStateOf("All") }
        val actions = listOf(
            WorkspaceAction(stringResource(R.string.new_session), R.drawable.ic_add, "Sessions", "Ctrl+Shift+N", service != null && sessions.size < 12) { open(selected?.environmentId) },
            WorkspaceAction(stringResource(R.string.switch_session), R.drawable.ic_terminal, "Sessions", "Ctrl+Shift+Tab", activeSessions.isNotEmpty()) { sessionScopeId = null; showArchived = false; overlay = "Sessions" },
            WorkspaceAction(stringResource(R.string.archived_sessions), R.drawable.ic_archive, "Sessions") { sessionScopeId = null; showArchived = true; overlay = "Sessions" },
            WorkspaceAction(stringResource(R.string.paste_label), R.drawable.ic_paste, "Clipboard", "Ctrl+Shift+V", acceptsInput) { overlay = null; paste() },
            WorkspaceAction(stringResource(R.string.select_visible_text), R.drawable.ic_terminal, "Clipboard", enabled = selection != null && page == "Terminal") { selection?.selectAllVisible(); overlay = null },
            WorkspaceAction(stringResource(R.string.copy_selection), R.drawable.ic_copy, "Clipboard", enabled = selection != null && page == "Terminal") { selection?.copySelection()?.let { context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Terminal selection", it)) }; overlay = null },
            WorkspaceAction(stringResource(R.string.environments_label), R.drawable.ic_environments) { page = "Environments"; overlay = null },
            WorkspaceAction(stringResource(R.string.files_label), R.drawable.ic_files) { page = "Files"; overlay = null },
            WorkspaceAction(stringResource(R.string.settings_label), R.drawable.ic_settings) { page = "Settings"; overlay = null },
            WorkspaceAction(stringResource(R.string.licenses_label), R.drawable.ic_info) { overlay = "Licenses" },
        ) + preferences.snippets.lines().filter { it.isNotBlank() }.map { command -> WorkspaceAction(command, R.drawable.ic_commands, "Saved commands", enabled = acceptsInput) { targetSession = selected; pendingText = "Run saved command?" to "$command\n"; overlay = null } }
        val results = actions.filter { (category == "All" || category == it.category) && it.label.contains(query, true) }
        ModalBottomSheet(onDismissRequest = { overlay = null }) {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 640.dp).imePadding().padding(horizontal = 16.dp), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SheetHeader(stringResource(R.string.tools_label), { overlay = null })
                if (selected != null) Text("${context.getString(R.string.session_identity, selected.title, environmentName(selected))} · ${statusLabel(selectedStatus ?: SessionStatus.Starting)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(query, { query = it }, label = { Text(stringResource(R.string.tools_find)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("All", "Sessions", "Clipboard", "Saved commands").forEach { name -> FilterChip(category == name, { category = name }, label = { Text(stringResource(when (name) { "All" -> R.string.all_label; "Sessions" -> R.string.sessions_label; "Clipboard" -> R.string.clipboard_label; else -> R.string.saved_commands_label })) }) } }
                if (category == "Saved commands") Text(if (!acceptsInput) stringResource(R.string.snippets_running_required) else stringResource(R.string.snippets_confirm_context, context.getString(R.string.session_identity, selected!!.title, environmentName(selected))), style = MaterialTheme.typography.bodySmall)
                if (sessions.size >= 12) Text(stringResource(R.string.session_limit_hint, sessions.count { it.archived }), style = MaterialTheme.typography.bodySmall)
                } }
                    if (results.isEmpty()) item { Text(stringResource(if (category == "Saved commands" && query.isBlank()) R.string.snippets_add_hint else R.string.no_matching_actions), Modifier.padding(12.dp)) }
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

    if (overlay == "Licenses") {
        val notices = remember { context.assets.open("open-source-notices.txt").bufferedReader().use { it.readText() } }
        AlertDialog(onDismissRequest = { overlay = null }, title = { Text(stringResource(R.string.licenses_label)) }, text = { Text(notices, Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) }, confirmButton = { TextButton(onClick = { overlay = null }) { Text(stringResource(R.string.close_label)) } })
    }
    pendingText?.let { (kind, text) ->
        val runningCommand = kind.startsWith("Run")
        val identity = targetSession?.let { stringResource(R.string.session_identity, it.title, environmentName(it)) } ?: stringResource(R.string.no_selected_session)
        AlertDialog(onDismissRequest = { pendingText = null },
            title = { Text(stringResource(if (runningCommand) R.string.run_saved_command else R.string.paste_multiple_lines)) },
            text = { Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(identity)
                Text(text.take(1000), fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, style = LocalTextStyle.current.copy(textDirection = androidx.compose.ui.text.style.TextDirection.Ltr))
                Text(stringResource(R.string.paste_newline_warning))
            } },
            confirmButton = { TextButton(onClick = { attempt { val destination = sessions.firstOrNull { it.id == targetSession?.id } ?: error("Session closed. Select another session."); check(destination.status.value == SessionStatus.Running) { "This session has finished. Open a replacement before sending input." }; destination.emulator.pasteText(text) }; pendingText = null }) { Text(stringResource(if (runningCommand) R.string.run_label else R.string.paste_label)) } },
            dismissButton = { TextButton(onClick = { pendingText = null }) { Text(stringResource(R.string.cancel_label)) } })
    }
}

@Composable
internal fun statusLabel(status: SessionStatus) = when (status) {
    SessionStatus.Starting -> stringResource(R.string.status_starting)
    SessionStatus.Running -> stringResource(R.string.status_running)
    is SessionStatus.Exited -> stringResource(R.string.status_exited, status.code)
    is SessionStatus.Failed -> stringResource(R.string.status_failed, status.message)
}

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
            IconButton(onClick = { paste() }, enabled = status == SessionStatus.Running) { Icon(painterResource(R.drawable.ic_paste), stringResource(R.string.paste_label)) }
            IconButton(onClick = { keyboard?.hide() }) { Icon(painterResource(R.drawable.ic_keyboard_hide), stringResource(R.string.hide_keyboard)) }
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

@Composable
internal fun SheetHeader(title: String, dismiss: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
        TextButton(onClick = dismiss) { Icon(painterResource(R.drawable.ic_close), null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.close_label)) }
    }
}
