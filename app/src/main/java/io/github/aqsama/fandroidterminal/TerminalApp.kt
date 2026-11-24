// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.aqsama.fandroidterminal.sessions.SessionStatus
import io.github.aqsama.fandroidterminal.sessions.TerminalService
import kotlinx.coroutines.launch

/**
 * App shell: drawer, typed back stack, the current destination and the modal overlays.
 * Session logic lives in [WorkspaceController]; the terminal surface lives in [TerminalWorkspace].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalApp(service: TerminalService?, workspace: WorkspaceState, preferences: Preferences) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val navigator = rememberNavigator()
    // Session launches run in the ViewModel scope so rotation does not cancel a half-started session.
    val controller = remember { WorkspaceController(context.applicationContext, workspace.viewModelScope, preferences, navigator, workspace) }
    SideEffect { controller.service = service }
    val pageState = rememberSaveableStateHolder()
    val keyboard = LocalSoftwareKeyboardController.current
    val view = androidx.compose.ui.platform.LocalView.current

    val sessions = service?.sessions?.sessions?.collectAsStateWithLifecycle()?.value.orEmpty()
    val environments = service?.environments?.environments?.collectAsStateWithLifecycle()?.value.orEmpty()
    val operationError = service?.environments?.error?.collectAsStateWithLifecycle()?.value
    val selected = controller.selected(sessions)
    var selection by remember { mutableStateOf<org.connectbot.terminal.SelectionController?>(null) }
    val screen = navigator.current

    // Keep the remembered selection valid; wait for the service so recreation does not forget it.
    LaunchedEffect(service, selected?.id) { if (service != null) controller.selectedId = selected?.id }
    LaunchedEffect(service, sessions.map { it.id }) { if (service != null) workspace.retainSessions(sessions.map { it.id }.toSet()) }
    androidx.lifecycle.compose.LifecycleResumeEffect(view, screen, preferences.keepAwake, selected?.id) {
        view.keepScreenOn = screen == Screen.Terminal && selected != null && preferences.keepAwake
        onPauseOrDispose { view.keepScreenOn = false }
    }
    StartupEffect(service, controller, workspace)

    // Back: overlay → drawer → text selection → previous screen → system Back at terminal root.
    val clearingSelection = screen == Screen.Terminal && selection?.isSelectionActive == true
    BackHandler(controller.overlay == null && (clearingSelection || navigator.canGoBack)) {
        if (clearingSelection) selection?.clearSelection() else navigator.pop()
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            val wide = maxWidth >= 840.dp
            val drawer = remember { SessionNavigatorState() }
            fun closeDrawer() { scope.launch { drawer.close() } }
            LaunchedEffect(controller.drawerRequests) { if (controller.drawerRequests > 0 && !wide) drawer.open() }
            val sidebar: @Composable () -> Unit = {
                WorkspaceSidebar(
                    screen, service?.environments?.progress?.collectAsStateWithLifecycle()?.value?.keys.orEmpty(), sessions, environments, selected?.id,
                    navigate = { navigator.push(it); keyboard?.hide(); closeDrawer() },
                    select = { controller.select(it); closeDrawer() },
                    action = { session, command -> sessionCommand(controller, session, command) },
                    newSession = { id -> controller.open(id); closeDrawer() },
                    launchers = preferences.launchers, launchTool = { controller.launch(it); closeDrawer() },
                )
            }
            val content: @Composable () -> Unit = {
                Column(Modifier.fillMaxSize()) {
                    (controller.error ?: operationError)?.let { message ->
                        ErrorBanner(operationLabel(message)) { if (controller.error != null) controller.error = null else service?.environments?.dismissError() }
                    }
                    AnimatedContent(screen, Modifier.weight(1f), label = "destination", contentKey = { it.encode() },
                        transitionSpec = {
                            (fadeIn(tween(180)) + slideInHorizontally(tween(220)) { it / 12 }) togetherWith fadeOut(tween(90))
                        }) { destination ->
                        if (service == null) Box(Modifier.fillMaxSize()) { CircularProgressIndicator(Modifier.align(Alignment.Center)) }
                        else if (destination == Screen.Terminal) TerminalWorkspace(controller, service, workspace, selected, sessions, environments, wide,
                            openDrawer = { scope.launch { drawer.open() } }, drawerBlocksInput = !wide && drawer.inputBlocked,
                            terminalBounds = { drawer.openingBounds = it }, onSelection = { selection = it })
                        else pageState.SaveableStateProvider(destination.encode()) {
                            Destination(destination, controller, service, workspace, preferences, wide)
                        }
                    }
                }
            }
            if (wide) PermanentNavigationDrawer(drawerContent = { PermanentDrawerSheet(Modifier.width(320.dp)) { sidebar() } }, content = content)
            else SessionNavigator(drawer, gesturesEnabled = screen == Screen.Terminal && controller.overlay == null && selection?.isSelectionActive != true,
                sidebar = { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerLow) { sidebar() } }, content = content)
        }
    }
    Overlays(controller, workspace, sessions, environments, selected)
}

@Composable
private fun StartupEffect(service: TerminalService?, controller: WorkspaceController, workspace: WorkspaceState) {
    val loaded = service?.environments?.isLoaded == true
    val environments = service?.environments?.environments?.collectAsStateWithLifecycle()?.value.orEmpty()
    val resources = androidx.compose.ui.platform.LocalResources.current
    LaunchedEffect(service, loaded, environments) {
        if (service == null || !loaded || workspace.startupHandled == service.sessions.generation) return@LaunchedEffect
        workspace.startupHandled = service.sessions.generation
        val startup = controller.preferences.startup
        val empty = service.sessions.sessions.value.isEmpty()
        when (startup) {
            "Environments" -> controller.navigator.push(Screen.Environments)
            "Sessions" -> controller.drawerRequests++
            "Android" -> if (empty) service.initializeSession(null)
            else -> if (empty) {
                if (environments.any { it.id == startup && it.ready }) service.initializeSession(startup)
                else { controller.navigator.push(Screen.Environments); controller.error = resources.getString(R.string.startup_unavailable) }
            }
        }
    }
}

internal fun sessionCommand(controller: WorkspaceController, session: io.github.aqsama.fandroidterminal.sessions.TerminalSession, command: String) {
    when (command) {
        "Archive" -> controller.archive(session, true)
        "Restore" -> controller.archive(session, false)
        "Close" -> controller.overlay = Overlay.Close(session.id)
        "Rename" -> controller.overlay = Overlay.Rename(session.id)
    }
}

/** Non-terminal destinations share one top bar, so Back and titles behave the same everywhere. */
@Composable
private fun Destination(screen: Screen, controller: WorkspaceController, service: TerminalService, workspace: WorkspaceState, preferences: Preferences, wide: Boolean) {
    val navigator = controller.navigator
    val environments by service.environments.environments.collectAsStateWithLifecycle()
    val environment = (screen as? Screen.Environment)?.id ?: (screen as? Screen.Files)?.id
    val guest = environments.firstOrNull { it.id == environment }
    val title = when (screen) {
        is Screen.Environment -> guest?.name ?: stringResource(R.string.guest_settings_title)
        is Screen.Files -> guest?.name?.let { stringResource(R.string.files_in, it) } ?: stringResource(R.string.files_label)
        else -> stringResource(screen.titleRes)
    }
    Column(Modifier.fillMaxSize()) {
        ScreenBar(title, screen.iconRes, back = { navigator.pop() })
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (screen) {
                Screen.Environments -> EnvironmentsPage(service, { id, dir -> controller.open(id, dir) }, { navigator.push(Screen.Files(it)) },
                    { navigator.push(Screen.Environment(it)) }, controller::resume) { controller.overlay = Overlay.Owners(it) }
                is Screen.Environment -> if (guest != null) GuestSettingsPage(service, guest.id, packages = { navigator.push(Screen.Packages(guest.id)) },
                    files = { navigator.push(Screen.Files(guest.id)) }) { controller.overlay = Overlay.Owners(guest.id) }
                is Screen.Files -> if (guest != null) FilesPage(service, guest.id, guest.name, preferences, workspace.draft(guest.id),
                    { controller.overlay = Overlay.Owners(guest.id) }, { navigator.home() }) { id, dir -> controller.open(id, dir) }
                is Screen.Packages -> PackageScreen(service, screen.environmentId, screen.query, environmentsPage = { navigator.push(Screen.Environments) },
                    manageSessions = { controller.overlay = Overlay.Owners(it) })
                Screen.Launchers -> LauncherScreen(preferences, environments, controller::launch, { launcher ->
                    try { service.environments.launcherSpecification(launcher); true } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { false }
                }, installer = { OpenCodeInstaller(service, preferences) }) { launcher ->
                    navigator.push(Screen.Packages(launcher?.environmentId, launcher?.arguments?.firstOrNull()?.substringAfterLast('/').orEmpty()))
                }
                Screen.Backup -> BackupScreen(service, preferences, workspace)
                Screen.GuestDefaults -> GuestDefaultsScreen(service.environments.defaults) { navigator.push(Screen.Packages()) }
                Screen.Settings -> SettingsHome(preferences, environments.size, navigator::push)
                Screen.Appearance -> AppearanceSettings(preferences)
                Screen.Keyboard -> KeyboardSettings(preferences)
                Screen.TerminalBehavior -> TerminalBehaviorSettings(preferences, environments)
                Screen.Access -> SettingsColumn { WorkingModeSettings(environments) }
                Screen.About -> AboutSettings { controller.overlay = Overlay.Licenses }
                Screen.Terminal -> Unit
            }
        }
    }
}

@Composable
internal fun ScreenBar(title: String, icon: Int?, back: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = back) { Icon(painterResource(R.drawable.ic_back), stringResource(R.string.back_label)) }
        if (icon != null) Icon(painterResource(icon), null, Modifier.padding(start = 4.dp).size(22.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, Modifier.weight(1f).padding(horizontal = 12.dp), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        actions()
    }
}

@Composable
private fun ErrorBanner(message: String, dismiss: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_info), null, Modifier.padding(start = 16.dp).size(18.dp))
            Text(message, Modifier.weight(1f).heightIn(max = 96.dp).verticalScroll(rememberScrollState()).padding(12.dp), style = MaterialTheme.typography.bodySmall)
            IconButton(onClick = dismiss) { Icon(painterResource(R.drawable.ic_close), stringResource(R.string.dismiss_error)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Overlays(controller: WorkspaceController, workspace: WorkspaceState, sessions: List<io.github.aqsama.fandroidterminal.sessions.TerminalSession>,
    environments: List<io.github.aqsama.fandroidterminal.runtime.linux.LinuxEnvironment>, selected: io.github.aqsama.fandroidterminal.sessions.TerminalSession?) {
    val context = LocalContext.current
    val dismiss = { controller.overlay = null }
    when (val overlay = controller.overlay) {
        null -> Unit
        is Overlay.Owners -> ModalBottomSheet(onDismissRequest = dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            val owned = sessions.filter { it.environmentId == overlay.environmentId }
            val name = environments.firstOrNull { it.id == overlay.environmentId }?.name ?: stringResource(R.string.android_shell)
            Column(Modifier.fillMaxWidth().heightIn(max = 640.dp)) {
                SessionRecoveryList(name, owned.map { key(it.id) { it.listEntry() } }, selected?.id, dismiss,
                    { id -> sessions.firstOrNull { it.id == id }?.let(controller::select) },
                    { id, command -> sessions.firstOrNull { it.id == id }?.let { if (command == "Close") controller.close(it) else sessionCommand(controller, it, command) } },
                    Modifier.weight(1f, fill = false))
                if (owned.size > 1) Button(onClick = { controller.closeAll(overlay.environmentId); dismiss() }, Modifier.fillMaxWidth().padding(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)) {
                    Icon(painterResource(R.drawable.ic_close), null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.close_all_sessions, owned.size))
                }
            }
        }
        is Overlay.Close -> controller.session(overlay.sessionId)?.let { session ->
            Prompt(stringResource(R.string.close_named_session, session.title, controller.environmentName(session)), label = "",
                message = stringResource(R.string.close_named_details, session.title, controller.environmentName(session)),
                confirm = stringResource(R.string.close_session), destructive = true, dismiss = dismiss) { controller.close(session) }
        } ?: LaunchedEffect(overlay) { dismiss() }
        is Overlay.Rename -> controller.session(overlay.sessionId)?.let { session ->
            Prompt(stringResource(R.string.rename_label), initial = session.title, dismiss = dismiss) { name ->
                session.title = io.github.aqsama.fandroidterminal.runtime.linux.EnvironmentFiles.validateName(name)
            }
        } ?: LaunchedEffect(overlay) { dismiss() }
        Overlay.Commands -> CommandBrowser(selected?.let(controller::identity) ?: stringResource(R.string.no_selected_session),
            controller.preferences.snippetList, selected?.status?.collectAsStateWithLifecycle()?.value == SessionStatus.Running, dismiss,
            { controller.overlay = Overlay.ComposeCommand }, { controller.overlay = null; controller.navigator.push(Screen.TerminalBehavior) },
            { command -> selected?.let { controller.overlay = Overlay.Send(it.id, command, run = true, fromDraft = false) } })
        Overlay.ComposeCommand -> if (selected != null) CommandDraftSheet(workspace.command(selected.id), controller.identity(selected),
            selected.status.collectAsStateWithLifecycle().value == SessionStatus.Running, dismiss) { command ->
            controller.overlay = Overlay.Send(selected.id, command, run = true, fromDraft = true)
        } else LaunchedEffect(overlay) { dismiss() }
        Overlay.Licenses -> {
            val notices by produceState("") { value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { context.assets.open("open-source-notices.txt").bufferedReader().use { it.readText() } } }
            AlertDialog(onDismissRequest = dismiss, title = { Text(stringResource(R.string.licenses_label)) },
                text = { Text(notices, Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) },
                confirmButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.close_label)) } })
        }
        is Overlay.OpenLink -> {
            val linkSchemeBlocked = stringResource(R.string.link_scheme_blocked)
            AlertDialog(onDismissRequest = dismiss, icon = { Icon(painterResource(R.drawable.ic_share), null) },
                title = { Text(stringResource(R.string.open_link_title)) },
                text = { Text(overlay.url, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr), maxLines = 6) },
                confirmButton = { Button(onClick = {
                    dismiss()
                    val uri = android.net.Uri.parse(overlay.url)
                    if (uri.scheme in listOf("http", "https")) runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri)) }
                        .onFailure { controller.error = it.message }
                    else controller.error = linkSchemeBlocked
                }) { Text(stringResource(R.string.open_label)) } },
                dismissButton = { Row {
                    TextButton(onClick = {
                        context.getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(android.content.ClipData.newPlainText("URL", overlay.url)); dismiss()
                    }) { Text(stringResource(R.string.copy_label)) }
                    TextButton(onClick = dismiss) { Text(stringResource(R.string.cancel_label)) }
                } })
        }
        is Overlay.Send -> {
            val target = controller.session(overlay.sessionId)
            val previewLimit = if (overlay.run) 16384 else 1000
            AlertDialog(onDismissRequest = dismiss,
                title = { Text(stringResource(if (!overlay.run) R.string.paste_multiple_lines else if (overlay.fromDraft) R.string.run_command else R.string.run_saved_command)) },
                text = {
                    Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(target?.let(controller::identity) ?: stringResource(R.string.no_selected_session), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                            Text(overlay.text.take(previewLimit), Modifier.fillMaxWidth().padding(12.dp), fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr))
                        }
                        if (overlay.text.length > previewLimit) Text(stringResource(R.string.paste_preview_truncated, previewLimit, overlay.text.length), style = MaterialTheme.typography.bodySmall)
                    }
                },
                confirmButton = { Button(onClick = { controller.send(overlay, workspace) }) { Text(stringResource(if (overlay.run) R.string.run_label else R.string.paste_label)) } },
                dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.cancel_label)) } })
        }
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
internal fun SheetAction(label: String, icon: Int, enabled: Boolean = true, destructive: Boolean = false, description: String? = null, click: () -> Unit) {
    val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    ListItem(colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        modifier = Modifier.clickable(enabled = enabled, onClick = click),
        headlineContent = { Text(label, color = color.copy(alpha = if (enabled) 1f else .38f)) },
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

internal val Screen.titleRes: Int get() = when (this) {
    Screen.Terminal -> R.string.terminal_label
    Screen.Environments, is Screen.Environment -> R.string.environments_label
    is Screen.Files -> R.string.files_label
    is Screen.Packages -> R.string.packages_title
    Screen.Launchers -> R.string.launchers_title
    Screen.Backup -> R.string.backup_title
    Screen.Settings -> R.string.settings_label
    Screen.Appearance -> R.string.settings_appearance
    Screen.Keyboard -> R.string.settings_extra_keys
    Screen.TerminalBehavior -> R.string.terminal_tools
    Screen.Access -> R.string.working_mode
    Screen.GuestDefaults -> R.string.guest_defaults_title
    Screen.About -> R.string.settings_about
}

internal val Screen.iconRes: Int get() = when (this) {
    Screen.Terminal -> R.drawable.ic_shell
    Screen.Environments, is Screen.Environment, Screen.GuestDefaults -> R.drawable.ic_environments
    is Screen.Files -> R.drawable.ic_files
    is Screen.Packages -> R.drawable.ic_download
    Screen.Launchers -> R.drawable.ic_prompt
    Screen.Backup -> R.drawable.ic_storage
    Screen.Appearance -> R.drawable.ic_palette
    Screen.Keyboard -> R.drawable.ic_keyboard
    Screen.TerminalBehavior -> R.drawable.ic_terminal
    Screen.Access -> R.drawable.ic_shield
    Screen.About -> R.drawable.ic_info
    Screen.Settings -> R.drawable.ic_settings
}
