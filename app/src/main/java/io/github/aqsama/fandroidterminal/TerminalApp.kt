// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import android.content.ClipboardManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aqsama.fandroidterminal.sessions.*
import io.github.aqsama.fandroidterminal.terminal.TerminalSurface
import org.connectbot.terminal.*

internal class TerminalModifiers : ModifierManager {
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
    val resources = LocalResources.current
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val preferences = remember { Preferences(context) }
    val pageState = rememberSaveableStateHolder()
    var settingsRequest by rememberSaveable { mutableStateOf<String?>(null) }
    var page by rememberSaveable { mutableStateOf("Terminal") }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var environmentId by rememberSaveable { mutableStateOf<String?>(null) }
    var overlay by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingText by remember { mutableStateOf<Pair<String, String>?>(null) }
    var targetSession by remember { mutableStateOf<TerminalSession?>(null) }
    var actionId by rememberSaveable { mutableStateOf<String?>(null) }
    var sessionReturnOverlay by rememberSaveable { mutableStateOf<String?>(null) }
    var sessionScopeId by rememberSaveable { mutableStateOf<String?>(null) }
    var navigatorArchived by rememberSaveable { mutableStateOf(false) }
    var navigatorRequest by remember { mutableIntStateOf(0) }
    var focusMode by rememberSaveable { mutableStateOf(false) }
    var pendingDraftId by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val appScope = rememberCoroutineScope()
    var selection by remember { mutableStateOf<SelectionController?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val sessions = service?.sessions?.sessions?.collectAsStateWithLifecycle()?.value.orEmpty()
    val environments = service?.environments?.environments?.collectAsStateWithLifecycle()?.value.orEmpty()
    val progress = service?.environments?.progress?.collectAsStateWithLifecycle()?.value.orEmpty()
    val operationError = service?.environments?.error?.collectAsStateWithLifecycle()?.value
    val activeSessions = sessions.filterNot { it.archived }
    LaunchedEffect(service, sessions.map { it.id }) { if (service != null) workspace.retainSessions(sessions.map { it.id }.toSet()) }
    fun navigateSessions(archived: Boolean = false) { navigatorArchived = archived; focusMode = false; overlay = null; navigatorRequest++ }
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
    fun environmentName(session: TerminalSession): String = environments.firstOrNull { it.id == session.environmentId }?.name ?: if (session.environmentId == null) resources.getString(R.string.android_shell) else "Linux"
    fun attempt(action: () -> Unit) { try { action(); error = null } catch (e: Exception) { error = e.message } }
    fun open(id: String?, directory: String? = null) { attempt {
        check(service != null) { "Connecting to terminal service" }
        TerminalService.ensureStarted(context)
        val session = if (id == null) service.sessions.create() else service.sessions.create(id, "Terminal", service.environments.specification(id, directory))
        selectedId = session.id; environmentId = id; page = "Terminal"; overlay = null
    } }
    fun selectSession(session: TerminalSession) {
        service?.sessions?.archive(session.id, false)
        selectedId = session.id; environmentId = session.environmentId; page = "Terminal"; navigatorArchived = false; overlay = null
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
                    val identity = resources.getString(R.string.session_identity, session.title, environmentName(session))
                    val message = resources.getString(if (session.status.value == SessionStatus.Running) R.string.archive_running_notice else R.string.archive_notice, identity)
                    val result = snackbar.showSnackbar(message, resources.getString(R.string.undo_label), duration = SnackbarDuration.Short)
                    val manager = service?.sessions
                    if (result == SnackbarResult.ActionPerformed && manager != null && manager.sessions.value.any { it.id == session.id }) {
                        manager.archive(session.id, false)
                        if (wasSelected) { selectedId = session.id; if (page == "Terminal") environmentId = session.environmentId }
                    }
                }
            }
            "Close" -> { sessionReturnOverlay = if (overlay == "Sessions") "Sessions" else null; overlay = "Close session" }
            "Rename" -> { sessionReturnOverlay = if (overlay == "Sessions") "Sessions" else null; overlay = "Rename session" }
            "Focus" -> { focusMode = true; overlay = null }
            else -> Unit
        }
    }
    fun paste() {
        val text = context.getSystemService(ClipboardManager::class.java).primaryClip?.let { if (it.itemCount > 0) it.getItemAt(0).coerceToText(context).toString() else null } ?: return
        attempt {
            val destination = selected ?: error("Open a session before pasting")
            check(destination.status.value == SessionStatus.Running) { "This session has finished. Open a replacement before pasting." }
            require(text.length <= 1_048_576) { "Paste up to 1 MiB at a time" }
            if ('\n' in text || '\r' in text) { targetSession = destination; pendingDraftId = null; pendingText = "Paste multiple lines?" to text }
            else { selection?.clearSelection(); destination.emulator.pasteText(text) }
        }
    }
    fun intercept(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown || !event.isCtrlPressed || !event.isShiftPressed) return false
        return when (event.key) {
            Key.P -> { overlay = "Actions"; true }
            Key.F -> { focusMode = !focusMode; true }
            Key.N -> { open(selected?.environmentId); true }
            Key.V -> { paste(); true }
            Key.Tab -> { if (activeSessions.isNotEmpty()) selectedId = activeSessions[(activeSessions.indexOf(selected) - 1 + activeSessions.size) % activeSessions.size].id; true }
            else -> false
        }
    }
    LaunchedEffect(page) { if (page != "Terminal") focusMode = false }
    BackHandler(page != "Terminal" || overlay != null || focusMode) { if (overlay != null) overlay = null else if (focusMode) focusMode = false else page = if (page in listOf("Guest settings", "Files")) "Environments" else "Terminal" }
    Surface(Modifier.fillMaxSize()) {
    BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
        val wide = maxWidth >= 840.dp
        val drawer = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        fun closeDrawer() { scope.launch { drawer.close() } }
        LaunchedEffect(navigatorRequest) { if (navigatorRequest > 0 && !wide) drawer.open() }
        val keyboard = LocalSoftwareKeyboardController.current
        val sidebar: @Composable () -> Unit = {
            WorkspaceSidebar(page, progress.keys, sessions, environments, selected?.id, navigatorArchived, { navigatorArchived = it },
                { page = it; keyboard?.hide(); closeDrawer() },
                { selectSession(it); closeDrawer() },
                { session, command -> sessionAction(session, command) },
                { id -> open(id); closeDrawer() })
        }
        val content: @Composable () -> Unit = {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (page != "Terminal") IconButton(onClick = { backDispatcher?.onBackPressed() }) { Icon(painterResource(R.drawable.ic_back), stringResource(R.string.back_label)) }
                    else if (!focusMode && !wide) IconButton(onClick = { keyboard?.hide(); scope.launch { drawer.open() } }) { Icon(painterResource(R.drawable.ic_menu), stringResource(R.string.open_sidebar)) }
                    Column(Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 4.dp)) {
                        if (page == "Terminal" && selected != null) {
                            Text(environmentName(selected), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SessionStatusMark(selectedStatus ?: SessionStatus.Starting)
                                Text("${selected.title} · ${statusLabel(selectedStatus ?: SessionStatus.Starting)}", Modifier.padding(start = 6.dp), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        } else Text(stringResource(if (page == "Terminal") R.string.terminal_label else destinationLabel(page)), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (page == "Terminal") {
                        if (focusMode) IconButton(onClick = { focusMode = false }) { Icon(painterResource(R.drawable.ic_focus_exit), stringResource(R.string.exit_focus_mode)) }
                        else {
                            IconButton(onClick = { open(selected?.environmentId) }, enabled = service != null && sessions.size < 12) { Icon(painterResource(R.drawable.ic_add), stringResource(R.string.new_session)) }
                            Box {
                                var menu by remember { mutableStateOf(false) }
                                IconButton(onClick = { menu = true }, enabled = selected != null) { Icon(painterResource(R.drawable.ic_more), stringResource(R.string.session_actions)) }
                                SessionActionMenu(menu, { menu = false }, selected?.archived == true, focus = { menu = false; focusMode = true }, files = selected?.takeIf { it.environmentId != null }?.let { session -> { menu = false; environmentId = session.environmentId; page = "Files" } }) { command -> menu = false; selected?.let { sessionAction(it, command) } }
                            }
                        }
                    }
                }
                if (page == "Terminal" && !focusMode && environments.isEmpty() && !preferences.linuxHintDismissed && service != null) Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.linux_first_use_compact), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { page = "Environments"; keyboard?.hide() }) { Text(stringResource(R.string.install_debian)) }
                        IconButton(onClick = preferences::dismissLinuxHint) { Icon(painterResource(R.drawable.ic_close), stringResource(R.string.dismiss_linux_hint)) }
                    }
                }
                val tabs = activeSessions.filter { it.environmentId == selected?.environmentId }
                if (page == "Terminal" && !focusMode && tabs.size > 1) Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        tabs.forEach { session ->
                            key(session.id) {
                            val fullName = stringResource(R.string.session_tab_a11y, session.title, environmentName(session))
                            var tabMenu by remember { mutableStateOf(false) }
                            val requester = remember { BringIntoViewRequester() }
                            var measuredWidth by remember { mutableIntStateOf(0) }
                            val isSelected = session.id == selected?.id
                            LaunchedEffect(isSelected, measuredWidth) { if (isSelected && measuredWidth > 0) requester.bringIntoView() }
                            Box {
                                Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp), color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                                    modifier = Modifier.widthIn(max = 180.dp).bringIntoViewRequester(requester).onSizeChanged { measuredWidth = it.width }) {
                                    val longPressLabel = stringResource(R.string.session_options)
                                    Row(Modifier.heightIn(min = 48.dp).combinedClickable(role = androidx.compose.ui.semantics.Role.Tab, onClick = { selectSession(session) }, onLongClickLabel = longPressLabel, onLongClick = { tabMenu = true }).semantics { contentDescription = fullName; this.selected = isSelected }.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        val tabStatus by session.status.collectAsStateWithLifecycle()
                                        SessionStatusMark(tabStatus)
                                        Text(session.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                                SessionActionMenu(tabMenu, { tabMenu = false }, session.archived) { command -> tabMenu = false; sessionAction(session, command) }
                            }
                            }
                        }
                    }
                }
                if (page == "Terminal" && sessions.size >= 12) Text(stringResource(R.string.session_limit_hint, sessions.count { it.archived }), Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (progress.isNotEmpty() && page in listOf("Terminal", "Files")) Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Row(Modifier.fillMaxWidth().clickable { page = "Environments"; keyboard?.hide() }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(operationLabel(progress.values.first()), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
                        Icon(painterResource(R.drawable.ic_chevron), stringResource(R.string.view_operation), Modifier.size(18.dp))
                    }
                }
                (error ?: operationError)?.let { message -> Surface(color = MaterialTheme.colorScheme.errorContainer) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(operationLabel(message), Modifier.weight(1f).heightIn(max = 96.dp).verticalScroll(rememberScrollState()).padding(12.dp), style = MaterialTheme.typography.bodySmall)
                        IconButton(onClick = { if (error != null) error = null else service?.environments?.dismissError() }) { Icon(painterResource(R.drawable.ic_close), stringResource(R.string.dismiss_error)) }
                    }
                } }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (service == null) CircularProgressIndicator(Modifier.align(Alignment.Center))
                    else when (page) {
                        "Environments" -> EnvironmentsPage(service, { id, dir -> if (sessions.size >= 12) navigateSessions() else open(id, dir) }, { environmentId = it; page = "Files" }, { environmentId = it; page = "Guest settings" }, { sessionScopeId = it; overlay = "Sessions" })
                        "Settings" -> pageState.SaveableStateProvider("Settings") { SettingsPage(preferences, settingsRequest, { settingsRequest = null }, { page = "Environments" }) { overlay = "Licenses" } }
                        "Guest settings" -> environmentId?.let { id -> pageState.SaveableStateProvider("Guest settings:$id") { GuestSettingsPage(service, id) { sessionScopeId = id; overlay = "Sessions" } } }
                        "Files" -> if (selectedEnvironment != null) FilesPage(service, selectedEnvironment.id, selectedEnvironment.name, preferences, workspace.draft(selectedEnvironment.id), { sessionScopeId = selectedEnvironment.id; overlay = "Sessions" }, { val existing = sessions.firstOrNull { it.id == selectedId && it.environmentId == selectedEnvironment.id } ?: sessions.firstOrNull { it.environmentId == selectedEnvironment.id && !it.archived } ?: sessions.firstOrNull { it.environmentId == selectedEnvironment.id }; if (existing != null) selectSession(existing) else page = "Terminal" }) { id, dir -> open(id, dir) } else Page(stringResource(R.string.files_label), stringResource(R.string.choose_environment_help), showTitle = false) { Button(onClick = { page = "Environments" }) { Text(stringResource(R.string.choose_environment)) } }
                        else -> if (selected != null) SessionBody(selected, preferences, { paste() }, ::intercept, { selection = it }, { open(selected.environmentId) }, { overlay = "Command draft" }, sessions.size < 12, { navigateSessions() }, { overlay = "Actions" }) else Page(stringResource(R.string.terminal_label), stringResource(if (sessions.isEmpty()) R.string.open_terminal_hint else R.string.archived_terminal_empty)) {
                            Button(onClick = { open(null) }, enabled = sessions.size < 12) { Icon(painterResource(R.drawable.ic_add), null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.new_android_shell)) }
                            OutlinedButton(onClick = { page = "Environments" }) { Icon(painterResource(R.drawable.ic_environments), null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.manage_environments)) }
                            if (sessions.isNotEmpty()) OutlinedButton(onClick = { navigateSessions(true) }) { Icon(painterResource(R.drawable.ic_archive), null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.view_archived_sessions)) }
                        }
                    }
                }
            }
        }
        Box(Modifier.fillMaxSize()) {
        if (wide && !focusMode) PermanentNavigationDrawer(drawerContent = { PermanentDrawerSheet { sidebar() } }, content = content)
        else ModalNavigationDrawer(drawerState = drawer, gesturesEnabled = drawer.isOpen || page != "Terminal", drawerContent = { ModalDrawerSheet { sidebar() } }, content = content)
        if (overlay != "Sessions") SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(12.dp))
        }
    }
    }
    // Used only to resolve an owned-files/settings lock; global switching lives in the sidebar.
    if (overlay == "Sessions") ModalBottomSheet(onDismissRequest = { overlay = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().heightIn(max = 640.dp)) {
            LazyColumn(Modifier.weight(1f, fill = false).padding(horizontal = 16.dp), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SheetHeader(stringResource(R.string.owned_sessions_title, environments.firstOrNull { it.id == sessionScopeId }?.name ?: "Linux"), { overlay = null })
                    Text(stringResource(R.string.owned_sessions_help), style = MaterialTheme.typography.bodySmall)
                } }
                val owned = sessions.filter { it.environmentId == sessionScopeId }
                if (owned.isEmpty()) item { Text(stringResource(R.string.no_owned_sessions)); TextButton(onClick = { overlay = null }) { Text(stringResource(R.string.continue_label)) } }
                items(owned, key = { it.id }) { session -> SessionRow(session, environmentName(session), session.id == selected?.id, { selectSession(session) }) { sessionAction(session, it) } }
            }
            SnackbarHost(snackbar, Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        }
    }
    if (overlay == "Close session" && actionSession != null) Prompt(stringResource(R.string.close_named_session, actionSession.title, environmentName(actionSession)), label = "", message = stringResource(R.string.close_named_details, actionSession.title, environmentName(actionSession)), confirm = stringResource(R.string.close_session), destructive = true, dismiss = { overlay = sessionReturnOverlay; sessionReturnOverlay = null }) { service?.sessions?.remove(actionSession.id) }
    if (overlay == "Rename session" && actionSession != null) Prompt(stringResource(R.string.rename_label), initial = actionSession.title, dismiss = { overlay = sessionReturnOverlay; sessionReturnOverlay = null }) { name -> actionSession.title = io.github.aqsama.fandroidterminal.runtime.linux.EnvironmentFiles.validateName(name) }
    if (overlay == "Actions") CommandBrowser(
        selected?.let { resources.getString(R.string.session_identity, it.title, environmentName(it)) } ?: resources.getString(R.string.no_selected_session),
        preferences.snippets.lines().filter { it.isNotBlank() }, acceptsInput, { overlay = null },
        { overlay = "Command draft" }, { settingsRequest = "Saved commands"; page = "Settings"; overlay = null },
        { command -> targetSession = selected; pendingDraftId = null; pendingText = "Run saved command?" to command; overlay = null })

    if (overlay == "Command draft" && selected != null) CommandDraftSheet(
        workspace.command(selected.id), resources.getString(R.string.session_identity, selected.title, environmentName(selected)), acceptsInput,
        { overlay = null }, { command ->
            targetSession = selected; pendingDraftId = selected.id
            pendingText = "Run command?" to command; overlay = null
        })
    if (overlay == "Licenses") {
        val notices = remember { context.assets.open("open-source-notices.txt").bufferedReader().use { it.readText() } }
        AlertDialog(onDismissRequest = { overlay = null }, title = { Text(stringResource(R.string.licenses_label)) }, text = { Text(notices, Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) }, confirmButton = { TextButton(onClick = { overlay = null }) { Text(stringResource(R.string.close_label)) } })
    }
    pendingText?.let { (kind, text) ->
        val runningCommand = kind.startsWith("Run")
        val identity = targetSession?.let { stringResource(R.string.session_identity, it.title, environmentName(it)) } ?: stringResource(R.string.no_selected_session)
        AlertDialog(onDismissRequest = { pendingText = null; pendingDraftId = null },
            title = { Text(stringResource(if (runningCommand) if (pendingDraftId != null) R.string.run_command else R.string.run_saved_command else R.string.paste_multiple_lines)) },
            text = { Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(identity)
                val previewLimit = if (runningCommand) 16384 else 1000
                Text(text.take(previewLimit), fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, style = LocalTextStyle.current.copy(textDirection = androidx.compose.ui.text.style.TextDirection.Ltr))
                if (text.length > previewLimit) Text(stringResource(R.string.paste_preview_truncated, previewLimit, text.length), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.paste_newline_warning))
            } },
            confirmButton = { TextButton(onClick = { attempt { val destination = sessions.firstOrNull { it.id == targetSession?.id } ?: error("Session closed. Select another session."); check(destination.status.value == SessionStatus.Running) { "This session has finished. Open a replacement before sending input." }; if (destination.id == selected?.id) selection?.clearSelection(); destination.emulator.pasteText(text)
                if (runningCommand) destination.emulator.dispatchKey(0, VTermKey.ENTER)
                if (pendingDraftId == destination.id) workspace.command(destination.id).text = ""
                }; pendingText = null; pendingDraftId = null }) { Text(stringResource(if (runningCommand) R.string.run_label else R.string.paste_label)) } },
            dismissButton = { TextButton(onClick = { pendingText = null; pendingDraftId = null }) { Text(stringResource(R.string.cancel_label)) } })
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
private fun SessionBody(session: TerminalSession, preferences: Preferences, paste: () -> Unit, intercept: (KeyEvent) -> Boolean, selection: (SelectionController) -> Unit, reopen: () -> Unit, commandDraft: () -> Unit, canOpenReplacement: Boolean, manageSessions: () -> Unit, commands: () -> Unit) {
    val status by session.status.collectAsStateWithLifecycle()
    val modifiers = remember(session.id) { TerminalModifiers() }
    val focus = remember(session.id) { FocusRequester() }
    var showKeyboard by remember(session.id) { mutableStateOf(true) }
    var keyboardSeen by remember(session.id) { mutableStateOf(false) }
    var selectedText by remember(session.id) { mutableStateOf<SelectionController?>(null) }
    LaunchedEffect(preferences.palette, session.id) {
        val colors = io.github.aqsama.fandroidterminal.terminal.terminalColors(preferences.palette)
        session.emulator.setDefaultColors(colors.first, colors.second)
    }
    Column(Modifier.fillMaxSize()) {
        if (status !is SessionStatus.Running) Row(verticalAlignment = Alignment.CenterVertically) {
            Text(statusLabel(status), Modifier.weight(1f).padding(8.dp), style = MaterialTheme.typography.bodySmall)
            if (status is SessionStatus.Exited || status is SessionStatus.Failed) TextButton(onClick = if (canOpenReplacement) reopen else manageSessions) { Text(stringResource(if (canOpenReplacement) R.string.open_replacement else R.string.close_session_first)) }
        }
        Box(Modifier.weight(1f).fillMaxWidth().background(Color(io.github.aqsama.fandroidterminal.terminal.terminalColors(preferences.palette).second))) {
            key(session.id, preferences.font) { TerminalSurface(session.emulator, modifiers, Modifier.fillMaxSize(), paste, preferences.font, preferences.palette, intercept, { selectedText = it; selection(it) }, focus,
                showKeyboard = showKeyboard, onImeVisibility = { visible -> if (visible) keyboardSeen = true; if (keyboardSeen) showKeyboard = visible }, onTap = { showKeyboard = true }) }
        }
        TerminalInputDock(preferences, modifiers, session, selectedText, focus, { showKeyboard = it }, paste, commandDraft, commands)

    }
}

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
        Text(title, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = dismiss) { Icon(painterResource(R.drawable.ic_close), stringResource(R.string.dismiss_panel)) }
    }
}
