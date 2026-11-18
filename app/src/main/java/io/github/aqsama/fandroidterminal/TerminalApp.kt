// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import android.content.ClipboardManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aqsama.fandroidterminal.sessions.*
import io.github.aqsama.fandroidterminal.runtime.ExecutionMode
import io.github.aqsama.fandroidterminal.runtime.SharedWorkspace
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
fun TerminalApp(service: TerminalService?, workspace: WorkspaceState, preferences: Preferences) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val view = androidx.compose.ui.platform.LocalView.current
    val pageState = rememberSaveableStateHolder()
    var packageTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var packageQuery by rememberSaveable { mutableStateOf("") }
    var installRequested by rememberSaveable { mutableStateOf(false) }
    var settingsRequest by rememberSaveable { mutableStateOf<String?>(null) }
    var page by rememberSaveable { mutableStateOf(if (preferences.startup in listOf("Environments", "Sessions")) "Environments" else "Terminal") }
    var history by rememberSaveable { mutableStateOf(listOf<String>()) }
    var startupGeneration by rememberSaveable { mutableStateOf<String?>(null) }
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
    var pendingDraftId by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val appScope = rememberCoroutineScope()
    var selection by remember { mutableStateOf<SelectionController?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var launching by remember(service) { mutableStateOf(false) }
    fun navigate(destination: String, guest: String? = environmentId) {
        if (destination == "Terminal") { page = destination; history = emptyList(); return }
        if (destination != page || guest != environmentId) {
            val route = org.json.JSONArray().put(page).put(environmentId ?: org.json.JSONObject.NULL)
                .put(packageTarget ?: org.json.JSONObject.NULL).put(packageQuery).toString()
            history = (history + route).takeLast(24)
            page = destination; environmentId = guest
        }
    }
    fun browseLinux(install: Boolean) {
        overlay = null
        installRequested = install
        navigate("Environments")
    }
    fun goBack() {
        if (overlay != null) overlay = null
        else if (history.isNotEmpty()) {
            val route = org.json.JSONArray(history.last()); history = history.dropLast(1)
            page = route.getString(0); environmentId = if (route.isNull(1)) null else route.getString(1)
            packageTarget = if (route.isNull(2)) null else route.getString(2); packageQuery = route.getString(3)
        }
        else if (page != "Terminal") page = "Terminal"
        else { selection?.takeIf { it.isSelectionActive }?.clearSelection() }
    }
    val sessions = service?.sessions?.sessions?.collectAsStateWithLifecycle()?.value.orEmpty()
    val environments = service?.environments?.environments?.collectAsStateWithLifecycle()?.value.orEmpty()
    val progress = service?.environments?.progress?.collectAsStateWithLifecycle()?.value.orEmpty()
    val operationError = service?.environments?.error?.collectAsStateWithLifecycle()?.value
    val activeSessions = sessions.filterNot { it.archived }
    LaunchedEffect(service, sessions.map { it.id }) { if (service != null) workspace.retainSessions(sessions.map { it.id }.toSet()) }
    fun navigateSessions(archived: Boolean = false) { navigatorArchived = archived; overlay = null; navigatorRequest++ }
    val previousSession = sessions.firstOrNull { it.id == selectedId }
    val selected = activeSessions.firstOrNull { it.id == selectedId } ?: activeSessions.lastOrNull { it.environmentId == previousSession?.environmentId } ?: activeSessions.lastOrNull()
    androidx.lifecycle.compose.LifecycleResumeEffect(view, page, preferences.keepAwake, selected?.id) {
        view.keepScreenOn = page == "Terminal" && selected != null && preferences.keepAwake
        onPauseOrDispose { view.keepScreenOn = false }
    }
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
    fun open(id: String?, directory: String? = null, mode: ExecutionMode = selected?.takeIf { it.environmentId == id }?.mode ?: if (id == null) ExecutionMode.Android else ExecutionMode.PRoot) {
        val owner = service
        if (owner == null) { error = resources.getString(R.string.status_starting); return }
        if (launching || owner.sessions.sessions.value.size >= 12) return
        launching = true
        appScope.launch {
            try {
                val access = ExecutionAccess(context)
                val sharing = SharedWorkspace(context)
                check(owner.sessions.sessions.value.none { it.environmentId == id && it.mode == ExecutionMode.Chroot && mode != ExecutionMode.Chroot } && (mode != ExecutionMode.Chroot || owner.sessions.sessions.value.none { it.environmentId == id && it.mode != ExecutionMode.Chroot })) { "Close this environment’s sessions before switching into or out of root-chroot" }
                val spec = if (id != null) owner.environments.specification(id, directory, mode == ExecutionMode.Chroot)
                    else if (mode != ExecutionMode.Shizuku) access.androidSpec(mode, sharing) else null
                if (mode == ExecutionMode.Shizuku) access.authorizeShizuku()
                check(id == null || id !in owner.environments.progress.value && "workspace-backup" !in owner.environments.progress.value) { "Environment is busy" }
                check(owner.sessions.sessions.value.none { it.environmentId == id && it.mode == ExecutionMode.Chroot && mode != ExecutionMode.Chroot } && (mode != ExecutionMode.Chroot || owner.sessions.sessions.value.none { it.environmentId == id && it.mode != ExecutionMode.Chroot })) { "Close this environment’s sessions before switching root-chroot mode" }
                TerminalService.ensureStarted(context)
                val session = if (mode == ExecutionMode.Shizuku) {
                    val shared = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { sharing.folderFor(null) }
                    owner.sessions.create(mode = mode, transport = {
                        val (transport, _) = access.shizukuTransport(if (shared != null && sharing.grant(null).start) shared.path else null)
                        transport
                    }).also { it.executionIdentity = "Android · Shizuku · UID ${rikka.shizuku.Shizuku.getUid()}" }
                } else owner.sessions.create(id, if (id == null) "Shell" else "Terminal", spec!!, mode)
                selectedId = session.id; environmentId = id; page = "Terminal"; history = emptyList(); overlay = null
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { error = e.message }
            finally { launching = false }
        }
    }
    LaunchedEffect(service, environments, service?.environments?.isLoaded) {
        if (service != null && service.environments.isLoaded && startupGeneration != service.sessions.generation) {
            startupGeneration = service.sessions.generation
            when (preferences.startup) {
                "Environments" -> page = "Environments"
                "Sessions" -> { page = "Terminal"; navigateSessions() }
                "Android" -> if (sessions.isEmpty()) service.initializeSession(null)
                else -> if (sessions.isEmpty()) {
                    if (environments.any { it.id == preferences.startup && it.ready }) service.initializeSession(preferences.startup)
                    else { navigate("Environments"); error = resources.getString(R.string.startup_unavailable) }
                }
            }
        }
    }
    fun launchTool(launcher: io.github.aqsama.fandroidterminal.runtime.linux.CliLauncher) {
        val owner = service ?: return
        appScope.launch {
            try {
                check(owner.sessions.sessions.value.none { it.environmentId == launcher.environmentId && it.mode == ExecutionMode.Chroot }) { "Close root-chroot first" }
                val spec = owner.environments.launcherSpecification(launcher)
                check(launcher.environmentId !in owner.environments.progress.value && "workspace-backup" !in owner.environments.progress.value) { "Environment is busy" }
                TerminalService.ensureStarted(context)
                val session = owner.sessions.create(launcher.environmentId, launcher.name, spec)
                selectedId = session.id; environmentId = launcher.environmentId; page = "Terminal"; history = emptyList(); overlay = null
            } catch (e: Exception) { error = e.message }
        }
    }
    fun selectSession(session: TerminalSession) {
        service?.sessions?.archive(session.id, false)
        selectedId = session.id; environmentId = session.environmentId; page = "Terminal"; history = emptyList(); navigatorArchived = false; overlay = null
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
            Key.N -> { open(selected?.environmentId); true }
            Key.V -> { paste(); true }
            Key.Tab -> { if (activeSessions.isNotEmpty()) selectedId = activeSessions[(activeSessions.indexOf(selected) - 1 + activeSessions.size) % activeSessions.size].id; true }
            else -> false
        }
    }
    BackHandler { goBack() }
    Surface(Modifier.fillMaxSize()) {
    BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
        val wide = maxWidth >= 840.dp
        val drawer = remember { SessionNavigatorState() }
        val scope = rememberCoroutineScope()
        fun closeDrawer() { scope.launch { drawer.close() } }
        LaunchedEffect(navigatorRequest) { if (navigatorRequest > 0 && !wide) drawer.open() }
        val keyboard = LocalSoftwareKeyboardController.current
        val sidebar: @Composable () -> Unit = {
            WorkspaceSidebar(page, progress.keys, sessions, environments, selected?.id, navigatorArchived, { navigatorArchived = it },
                { destination ->
                    if (destination == "Packages") { packageTarget = null; packageQuery = "" }
                    navigate(destination)
                    keyboard?.hide()
                    closeDrawer()
                },
                { selectSession(it); closeDrawer() },
                { session, command -> sessionAction(session, command) },
                { id -> open(id); closeDrawer() }, preferences.launchers, { launchTool(it); closeDrawer() })
        }
        val content: @Composable () -> Unit = {
            Column(Modifier.fillMaxSize()) {
                if (page == "Terminal") {
                    val guest = environments.firstOrNull { it.id == selected?.environmentId }
                    TerminalHeader(
                        session = selected,
                        status = selectedStatus,
                        environment = guest,
                        sessions = activeSessions,
                        environmentName = ::environmentName,
                        selectSession = ::selectSession,
                        canCreate = service != null && !launching && sessions.size < 12,
                        openSidebar = if (wide) null else ({ navigateSessions() }),
                        chooseSession = { overlay = "New session" },
                        newHere = { open(selected?.environmentId) },
                        openSettings = {
                            if (guest == null) navigate("Settings") else navigate("Guest settings", guest.id)
                            keyboard?.hide()
                        },
                        openFiles = { guest?.let { navigate("Files", it.id) }; keyboard?.hide() },
                        openPackages = {
                            packageTarget = guest?.id; packageQuery = ""; navigate("Packages")
                            keyboard?.hide()
                        },
                        sessionAction = { action -> selected?.let { sessionAction(it, action) } },
                    )
                } else if (page != "Settings") Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { backDispatcher?.onBackPressed() }) {
                        Icon(painterResource(R.drawable.ic_back), stringResource(R.string.back_label))
                    }
                    Icon(painterResource(destinationIcon(page)), null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(destinationLabel(page)), Modifier.weight(1f).padding(horizontal = 10.dp),
                        style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (page == "Terminal" && sessions.size >= 12) Text(stringResource(R.string.session_limit_hint, sessions.count { it.archived }), Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (progress.isNotEmpty() && page in listOf("Terminal", "Files")) Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Row(Modifier.fillMaxWidth().clickable { navigate("Environments"); keyboard?.hide() }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
                        "Backup" -> BackupScreen(service, preferences, workspace)
                        "Launchers" -> LauncherScreen(preferences, environments, ::launchTool, { launcher ->
                            try { service.environments.launcherSpecification(launcher); true } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { false }
                        }, installer = { OpenCodeInstaller(service, preferences) }) { launcher -> packageTarget = launcher?.environmentId; packageQuery = launcher?.arguments?.firstOrNull()?.substringAfterLast('/').orEmpty(); navigate("Packages") }
                        "Packages" -> pageState.SaveableStateProvider("Packages:${packageTarget.orEmpty()}:$packageQuery") { PackageScreen(service, packageTarget, packageQuery, environmentsPage = { navigate("Environments") }, manageSessions = { sessionScopeId = it; overlay = "Sessions" }) }
                        "Guest defaults" -> pageState.SaveableStateProvider("Guest defaults") { GuestDefaultsScreen(service.environments.defaults) { packageTarget = null; packageQuery = ""; navigate("Packages") } }
                        "Environments" -> EnvironmentsPage(
                            service, { id, dir -> if (sessions.size >= 12) navigateSessions() else open(id, dir) },
                            { navigate("Files", it) }, { navigate("Guest settings", it) },
                            { sessionScopeId = it; overlay = "Sessions" },
                            installRequested = installRequested, installRequestHandled = { installRequested = false },
                        )
                        "Settings" -> pageState.SaveableStateProvider("Settings") { SettingsPage(preferences, settingsRequest, { settingsRequest = null }, { navigate("Environments") }, { overlay = "Licenses" }, { goBack() }, installed = environments, defaults = { navigate("Guest defaults") }, backup = { navigate("Backup") }, packages = { packageTarget = null; packageQuery = ""; navigate("Packages") }, files = { id, path -> environmentId = id; workspace.draft(id).directory = path; navigate("Files") }, startingDirectory = { id, done -> service.environments.readStartingDirectory(id, service.sessions, done) }) }
                        "Guest settings" -> environmentId?.let { id -> pageState.SaveableStateProvider("Guest settings:$id") { GuestSettingsPage(service, id, packages = { packageTarget = id; packageQuery = ""; navigate("Packages") }) { sessionScopeId = id; overlay = "Sessions" } } }
                        "Files" -> if (selectedEnvironment != null) FilesPage(service, selectedEnvironment.id, selectedEnvironment.name, preferences, workspace.draft(selectedEnvironment.id), { sessionScopeId = selectedEnvironment.id; overlay = "Sessions" }, { val existing = sessions.firstOrNull { it.id == selectedId && it.environmentId == selectedEnvironment.id } ?: sessions.firstOrNull { it.environmentId == selectedEnvironment.id && !it.archived } ?: sessions.firstOrNull { it.environmentId == selectedEnvironment.id }; if (existing != null) selectSession(existing) else page = "Terminal" }) { id, dir -> open(id, dir) } else Page(stringResource(R.string.files_label), stringResource(R.string.choose_environment_help), showTitle = false) { Button(onClick = { navigate("Environments") }) { Text(stringResource(R.string.choose_environment)) } }
                        else -> if (selected != null) {
                            SessionBody(
                                session = selected, preferences = preferences, paste = { paste() }, intercept = ::intercept,
                                selection = { selection = it }, reopen = { open(selected.environmentId) },
                                commandDraft = { overlay = "Command draft" },
                                canOpenReplacement = sessions.size < 12 && !launching,
                                manageSessions = { navigateSessions() }, commands = { overlay = "Actions" },
                                customizeKeys = { settingsRequest = "Extra keyboard keys"; navigate("Settings"); keyboard?.hide() },
                                inputEnabled = wide || !drawer.inputBlocked,
                                showSessions = if (wide) null else ({ navigateSessions() }),
                                terminalBounds = { drawer.openingBounds = it },
                            )
                        } else {
                            val supported = service.environments.supported
                            WorkspaceStart(
                                environments, progress.keys, supported, sessions.size < 12 && !launching, sessions.isNotEmpty(),
                                { id, mode -> open(id, mode = mode) },
                                { browseLinux(supported && environments.isEmpty()) }, { navigateSessions(true) },
                            )
                        }
                    }
                }
            }
        }
        Box(Modifier.fillMaxSize()) {
        if (wide) PermanentNavigationDrawer(drawerContent = { PermanentDrawerSheet { sidebar() } }, content = content)
        else SessionNavigator(drawer, gesturesEnabled = page == "Terminal" && overlay == null && selection?.isSelectionActive != true, sidebar = { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerLow) { sidebar() } }, content = content)
        if (overlay != "Sessions") SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(12.dp))
        }
    }
    }
    if (overlay == "New session" && service != null) {
        val supported = service.environments.supported
        NewSessionSheet(
            environments, progress.keys, supported, sessions.size < 12 && !launching,
            dismiss = { overlay = null },
            browseLinux = { browseLinux(supported && environments.isEmpty()) },
            open = { id, mode -> overlay = null; open(id, mode = mode) },
        )
    }
    // Used only to resolve an owned-files/settings lock; global switching lives in the sidebar.
    if (overlay == "Sessions") ModalBottomSheet(onDismissRequest = { overlay = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().heightIn(max = 640.dp)) {
            val owned = sessions.filter { it.environmentId == sessionScopeId }.map { session -> key(session.id) { session.listEntry() } }
            SessionRecoveryList(environments.firstOrNull { it.id == sessionScopeId }?.name ?: "Linux", owned, selected?.id,
                { overlay = null }, { id -> sessions.firstOrNull { it.id == id }?.let(::selectSession) },
                { id, command -> sessions.firstOrNull { it.id == id }?.let { sessionAction(it, command) } }, Modifier.weight(1f, fill = false))
            SnackbarHost(snackbar, Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        }
    }
    if (overlay == "Close session" && actionSession != null) Prompt(stringResource(R.string.close_named_session, actionSession.title, environmentName(actionSession)), label = "", message = stringResource(R.string.close_named_details, actionSession.title, environmentName(actionSession)), confirm = stringResource(R.string.close_session), destructive = true, dismiss = { overlay = sessionReturnOverlay; sessionReturnOverlay = null }) { service?.sessions?.remove(actionSession.id) }
    if (overlay == "Rename session" && actionSession != null) Prompt(stringResource(R.string.rename_label), initial = actionSession.title, dismiss = { overlay = sessionReturnOverlay; sessionReturnOverlay = null }) { name -> actionSession.title = io.github.aqsama.fandroidterminal.runtime.linux.EnvironmentFiles.validateName(name) }
    if (overlay == "Actions") CommandBrowser(
        selected?.let { resources.getString(R.string.session_identity, it.title, environmentName(it)) } ?: resources.getString(R.string.no_selected_session),
        preferences.snippets.lines().filter { it.isNotBlank() }, acceptsInput, { overlay = null },
        { overlay = "Command draft" }, { settingsRequest = "Saved commands"; navigate("Settings"); overlay = null },
        { command, saved ->
            targetSession = selected
            pendingDraftId = null
            pendingText = (if (saved) "Run saved command?" else "Run command?") to command
            overlay = null
        })

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
            title = { Text(stringResource(if (runningCommand) if (kind == "Run saved command?") R.string.run_saved_command else R.string.run_command else R.string.paste_multiple_lines)) },
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SessionBody(session: TerminalSession, preferences: Preferences, paste: () -> Unit, intercept: (KeyEvent) -> Boolean, selection: (SelectionController) -> Unit, reopen: () -> Unit, commandDraft: () -> Unit, canOpenReplacement: Boolean, manageSessions: () -> Unit, commands: () -> Unit, customizeKeys: () -> Unit, inputEnabled: Boolean, showSessions: (() -> Unit)?, terminalBounds: (Rect) -> Unit) {
    val status by session.status.collectAsStateWithLifecycle()
    val modifiers = remember(session.id) { TerminalModifiers() }
    val focus = remember(session.id) { FocusRequester() }
    // Input intent belongs to this work surface, so switching sessions keeps the IME open.
    var showKeyboard by remember { mutableStateOf(false) }
    val imeVisible = WindowInsets.isImeVisible
    var keyboardRequest by remember { mutableIntStateOf(0) }
    LaunchedEffect(inputEnabled) { if (!inputEnabled) showKeyboard = false }
    var selectedText by remember(session.id) { mutableStateOf<SelectionController?>(null) }
    LaunchedEffect(preferences.palette, session.id) {
        val colors = io.github.aqsama.fandroidterminal.terminal.terminalColors(preferences.palette)
        session.emulator.applyColorScheme(io.github.aqsama.fandroidterminal.terminal.terminalAnsiColors(preferences.palette), colors.first, colors.second)
    }
    Column(Modifier.fillMaxSize()) {
        if (status !is SessionStatus.Running) Row(verticalAlignment = Alignment.CenterVertically) {
            Text(statusLabel(status), Modifier.weight(1f).padding(8.dp), style = MaterialTheme.typography.bodySmall)
            if (status is SessionStatus.Exited || status is SessionStatus.Failed) TextButton(onClick = if (canOpenReplacement) reopen else manageSessions) { Text(stringResource(if (canOpenReplacement) R.string.open_replacement else R.string.close_session_first)) }
        }
        Box(Modifier.weight(1f).fillMaxWidth().background(Color(io.github.aqsama.fandroidterminal.terminal.terminalColors(preferences.palette).second))) {
            key(session.id) { TerminalSurface(session.emulator, modifiers, Modifier.fillMaxSize().testTag("terminal-viewport").onGloballyPositioned { terminalBounds(it.boundsInRoot()) }, paste, preferences.font, preferences.palette, intercept, { selectedText = it; selection(it) }, focus,
                showKeyboard = showKeyboard, onTap = { showKeyboard = true }, onFontSizeChanged = preferences::setTerminalFont, inputEnabled = inputEnabled, keyboardRequest = keyboardRequest) }
        }
        TerminalInputDock(
            preferences = preferences, modifiers = modifiers, session = session, selection = selectedText,
            focus = focus, imeVisible = imeVisible, inputEnabled = inputEnabled,
            toggleKeyboard = {
                showKeyboard = !imeVisible
                if (showKeyboard) keyboardRequest++
            },
            showSessions = showSessions, paste = paste, command = commandDraft, commands = commands,
            customizeKeys = customizeKeys,
        )

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
