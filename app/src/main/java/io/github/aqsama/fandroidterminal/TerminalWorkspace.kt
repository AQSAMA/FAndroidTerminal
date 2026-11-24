// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.drawBehind
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aqsama.fandroidterminal.runtime.ExecutionMode
import io.github.aqsama.fandroidterminal.runtime.QuickKeyLayout
import io.github.aqsama.fandroidterminal.runtime.linux.LinuxEnvironment
import io.github.aqsama.fandroidterminal.sessions.SessionStatus
import io.github.aqsama.fandroidterminal.sessions.TerminalService
import io.github.aqsama.fandroidterminal.sessions.TerminalSession
import io.github.aqsama.fandroidterminal.terminal.TerminalSchemes
import io.github.aqsama.fandroidterminal.terminal.TerminalSurface

/** Display data for one session tab; the native session stays in the service. */
internal data class WorkspaceTab(val entry: SessionListEntry, val environmentName: String, val distribution: String?, val identity: String)

/** Everything the workspace chrome can ask for. Kept separate so the chrome renders without a live service. */
internal class WorkspaceActions(
    val openDrawer: () -> Unit,
    val newSession: (environmentId: String?, mode: ExecutionMode?) -> Unit,
    val select: (String) -> Unit,
    val sessionCommand: (String, String) -> Unit,
    val navigate: (Screen) -> Unit,
    val dismissHint: () -> Unit,
)

@Composable
internal fun TerminalWorkspace(
    controller: WorkspaceController, service: TerminalService, workspace: WorkspaceState,
    selected: TerminalSession?, sessions: List<TerminalSession>, environments: List<LinuxEnvironment>, wide: Boolean,
    openDrawer: () -> Unit, drawerBlocksInput: Boolean, terminalBounds: (Rect) -> Unit,
    onSelection: (org.connectbot.terminal.SelectionController?) -> Unit,
) {
    val preferences = controller.preferences
    val progress by service.environments.progress.collectAsStateWithLifecycle()
    val tabs = sessions.filterNot { it.archived }.map { session ->
        key(session.id) {
            val environment = environments.firstOrNull { it.id == session.environmentId }
            WorkspaceTab(session.listEntry(), controller.environmentName(session), environment?.distribution, session.executionIdentity)
        }
    }
    val actions = remember(controller) {
        WorkspaceActions(openDrawer = openDrawer,
            newSession = { id, mode -> controller.open(id, mode = mode) },
            select = { id -> controller.session(id)?.let(controller::select) },
            sessionCommand = { id, command -> controller.session(id)?.let { sessionCommand(controller, it, command) } },
            navigate = controller.navigator::push,
            dismissHint = preferences::dismissLinuxHint)
    }
    WorkspaceChrome(
        current = tabs.firstOrNull { it.entry.id == selected?.id }, tabs = tabs, wide = wide,
        canOpen = sessions.size < SESSION_LIMIT,
        readyEnvironments = environments.filter { it.ready && it.id !in progress },
        showLinuxHint = environments.isEmpty() && !preferences.linuxHintDismissed,
        progress = progress.values.firstOrNull()?.let { operationLabel(it) },
        actions = actions,
    ) {
        if (selected != null) SessionBody(selected, controller, workspace, drawerBlocksInput, terminalBounds, onSelection)
        else EmptyWorkspace(sessions.isNotEmpty(), sessions.size < SESSION_LIMIT, actions)
    }
}

/** Top bar, session tabs and banners around the terminal content. */
@Composable
internal fun WorkspaceChrome(
    current: WorkspaceTab?, tabs: List<WorkspaceTab>, wide: Boolean, canOpen: Boolean,
    readyEnvironments: List<LinuxEnvironment>, showLinuxHint: Boolean, progress: String?,
    actions: WorkspaceActions, content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        WorkspaceTopBar(current, wide, canOpen, readyEnvironments, actions)
        if (tabs.size > 1) SessionTabs(tabs, current?.entry?.id, canOpen, actions)
        AnimatedVisibility(showLinuxHint) { LinuxHint(actions) }
        if (progress != null) Surface(color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
            Row(Modifier.fillMaxWidth().clickable { actions.navigate(Screen.Environments) }.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(progress, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
                Icon(painterResource(R.drawable.ic_chevron), stringResource(R.string.view_operation), Modifier.size(18.dp))
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) { content() }
    }
}

@Composable
private fun WorkspaceTopBar(current: WorkspaceTab?, wide: Boolean, canOpen: Boolean, ready: List<LinuxEnvironment>, actions: WorkspaceActions) {
    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (!wide) IconButton(onClick = actions.openDrawer) { Icon(painterResource(R.drawable.ic_menu), stringResource(R.string.open_sidebar)) }
        Row(Modifier.weight(1f).clip(MaterialTheme.shapes.medium).clickable(enabled = !wide, onClick = actions.openDrawer).padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            ContextBadge(current?.distribution, small = true)
            Column(Modifier.padding(start = 10.dp)) {
                if (current != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(current.entry.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (current.entry.status != SessionStatus.Running) {
                            Spacer(Modifier.width(8.dp)); SessionStatusMark(current.entry.status)
                        }
                    }
                    Text(current.identity.ifBlank { current.environmentName }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else Text(stringResource(R.string.terminal_label), style = MaterialTheme.typography.titleMedium)
            }
        }
        val environmentId = current?.entry?.environmentId
        if (environmentId != null) IconButton(onClick = { actions.navigate(Screen.Packages(environmentId)) }) {
            Icon(painterResource(R.drawable.ic_download), stringResource(R.string.packages_title))
        }
        NewSessionButton(environmentId, canOpen, ready, actions)
        Box {
            var menu by remember { mutableStateOf(false) }
            IconButton(onClick = { menu = true }) { Icon(painterResource(R.drawable.ic_more), stringResource(R.string.session_actions)) }
            DropdownMenu(menu, { menu = false }) {
                if (current != null) {
                    SessionMenuItems(current.entry.archived) { command -> menu = false; actions.sessionCommand(current.entry.id, command) }
                    if (environmentId != null) {
                        HorizontalDivider()
                        ActionMenuItem(stringResource(R.string.environment_files), R.drawable.ic_files) { menu = false; actions.navigate(Screen.Files(environmentId)) }
                        ActionMenuItem(stringResource(R.string.guest_settings_title), R.drawable.ic_environments) { menu = false; actions.navigate(Screen.Environment(environmentId)) }
                    }
                    HorizontalDivider()
                }
                ActionMenuItem(stringResource(R.string.settings_label), R.drawable.ic_settings) { menu = false; actions.navigate(Screen.Settings) }
            }
        }
    }
}

/** Tap keeps the current context; long press chooses any authority and environment. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewSessionButton(environmentId: String?, canOpen: Boolean, ready: List<LinuxEnvironment>, actions: WorkspaceActions) {
    var chooser by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    Box {
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 2.dp).size(48.dp).clip(MaterialTheme.shapes.medium)
                .combinedClickable(enabled = canOpen, onClickLabel = stringResource(R.string.new_session), onLongClickLabel = stringResource(R.string.execution_choose),
                    onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); chooser = true }, onClick = { actions.newSession(environmentId, null) })) {
            Box(contentAlignment = Alignment.Center) { Icon(painterResource(R.drawable.ic_add), stringResource(R.string.new_session)) }
        }
        DropdownMenu(chooser, { chooser = false }) {
            Text(stringResource(R.string.android_shell), Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            listOf(ExecutionMode.Android, ExecutionMode.Shizuku, ExecutionMode.Root).forEach { mode ->
                DropdownMenuItem(text = { Text(mode.identity) }, leadingIcon = { Icon(painterResource(R.drawable.ic_android), null, Modifier.size(20.dp)) },
                    onClick = { chooser = false; actions.newSession(null, mode) })
            }
            ready.forEach { guest ->
                HorizontalDivider()
                Text(guest.name, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                listOf(ExecutionMode.PRoot, ExecutionMode.Chroot).forEach { mode ->
                    DropdownMenuItem(text = { Text(mode.identity) }, leadingIcon = { Icon(painterResource(contextIcon(guest.distribution)), null, Modifier.size(20.dp)) },
                        onClick = { chooser = false; actions.newSession(guest.id, mode) })
                }
            }
        }
    }
}

/** All open sessions, across Android and every environment. The active tab can be closed directly. */
@Composable
private fun SessionTabs(tabs: List<WorkspaceTab>, selectedId: String?, canOpen: Boolean, actions: WorkspaceActions) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        tabs.forEach { tab ->
            key(tab.entry.id) {
                val active = tab.entry.id == selectedId
                val requester = remember { BringIntoViewRequester() }
                LaunchedEffect(active) { if (active) requester.bringIntoView() }
                var menu by remember { mutableStateOf(false) }
                val label = stringResource(R.string.session_tab_a11y, tab.entry.title, tab.environmentName)
                Box(Modifier.bringIntoViewRequester(requester)) {
                    Surface(shape = RoundedCornerShape(12.dp), color = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant) {
                        Row(Modifier.heightIn(min = 40.dp).widthIn(max = 200.dp)
                            .combinedClickable(role = Role.Tab, onClick = { actions.select(tab.entry.id) }, onLongClickLabel = stringResource(R.string.session_options), onLongClick = { menu = true })
                            .semantics { contentDescription = label; this.selected = active }.padding(start = 10.dp, end = if (active) 2.dp else 12.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(painterResource(contextIcon(tab.distribution)), null, Modifier.size(16.dp), tint = contextColor(tab.distribution))
                            Text(tab.entry.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f, fill = false))
                            if (tab.entry.status != SessionStatus.Running) SessionStatusMark(tab.entry.status)
                            if (active) IconButton({ actions.sessionCommand(tab.entry.id, "Close") }, Modifier.size(40.dp)) {
                                Icon(painterResource(R.drawable.ic_close), stringResource(R.string.close_named_session, tab.entry.title, tab.environmentName), Modifier.size(16.dp))
                            }
                        }
                    }
                    SessionActionMenu(menu, { menu = false }, tab.entry.archived) { command -> menu = false; actions.sessionCommand(tab.entry.id, command) }
                }
            }
        }
    }
}

@Composable
private fun LinuxHint(actions: WorkspaceActions) {
    Surface(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            ContextBadge("debian", small = true)
            Text(stringResource(R.string.linux_first_use_compact), Modifier.weight(1f).padding(horizontal = 8.dp), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { actions.navigate(Screen.Environments) }) { Text(stringResource(R.string.install_linux)) }
            IconButton(onClick = actions.dismissHint) { Icon(painterResource(R.drawable.ic_close), stringResource(R.string.dismiss_linux_hint)) }
        }
    }
}

@Composable
private fun EmptyWorkspace(hasArchived: Boolean, canOpen: Boolean, actions: WorkspaceActions) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primaryContainer) {
            Icon(painterResource(R.drawable.ic_terminal), null, Modifier.padding(20.dp).size(40.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Text(stringResource(if (hasArchived) R.string.archived_terminal_empty else R.string.open_terminal_hint), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = { actions.newSession(null, null) }, enabled = canOpen) { Icon(painterResource(R.drawable.ic_add), null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.new_android_shell)) }
        OutlinedButton(onClick = { actions.navigate(Screen.Environments) }) { Icon(painterResource(R.drawable.ic_environments), null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.manage_environments)) }
        if (hasArchived) TextButton(onClick = actions.openDrawer) { Text(stringResource(R.string.view_archived_sessions)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SessionBody(session: TerminalSession, controller: WorkspaceController, workspace: WorkspaceState, drawerBlocksInput: Boolean,
    terminalBounds: (Rect) -> Unit, onSelection: (org.connectbot.terminal.SelectionController?) -> Unit) {
    val preferences = controller.preferences
    val status by session.status.collectAsStateWithLifecycle()
    val running = status == SessionStatus.Running
    val inputEnabled = !drawerBlocksInput
    val modifiers = remember(session.id) { TerminalModifiers() }
    val focus = remember(session.id) { FocusRequester() }
    var selection by remember(session.id) { mutableStateOf<org.connectbot.terminal.SelectionController?>(null) }
    // Input intent belongs to the work surface, so switching sessions keeps the IME state.
    var showKeyboard by remember { mutableStateOf(false) }
    var keyboardRequest by remember { mutableIntStateOf(0) }
    var textSize by remember { mutableStateOf(false) }
    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(inputEnabled) { if (!inputEnabled) showKeyboard = false }
    val scheme = TerminalSchemes.get(preferences.palette)
    val flash = remember { Animatable(0f) }
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(session.id, preferences.bell) {
        try {
            session.bell.collectLatest {
                when (preferences.bell) {
                    BellMode.Off -> Unit
                    BellMode.Vibrate -> haptic.performHapticFeedback(HapticFeedbackType.Reject)
                    BellMode.Flash -> { flash.snapTo(.35f); flash.animateTo(0f, tween(220)) }
                }
            }
        } finally { flash.snapTo(0f) }
    }
    fun intercept(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown || !event.isCtrlPressed || !event.isShiftPressed) return false
        return when (event.key) {
            Key.P -> { controller.overlay = Overlay.Commands; true }
            Key.N -> { controller.open(session.environmentId); true }
            Key.V -> { controller.paste(selection); true }
            Key.C -> { selection?.takeIf { it.isSelectionActive }?.let { it.copySelection(); it.clearSelection() }; true }
            Key.W -> { controller.overlay = Overlay.Close(session.id); true }
            Key.Tab -> { controller.cycle(-1); true }
            Key.Equals, Key.Plus -> { preferences.setTerminalFont(preferences.font + 1); true }
            Key.Minus -> { preferences.setTerminalFont(preferences.font - 1); true }
            else -> false
        }
    }
    Column(Modifier.fillMaxSize()) {
        if (status !is SessionStatus.Running && status != SessionStatus.Starting) Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                SessionStatusMark(status)
                Text(statusLabel(status), Modifier.weight(1f).padding(horizontal = 8.dp), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                TextButton(onClick = { controller.open(session.environmentId) }) { Text(stringResource(R.string.open_replacement)) }
            }
        }
        val frame = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 6.dp).clip(frame).background(Color(scheme.background))
            .then(if (scheme.dark) Modifier else Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, frame))) {
            key(session.id) {
                TerminalSurface(session.emulator, modifiers, Modifier.fillMaxSize().testTag("terminal-viewport").onGloballyPositioned { terminalBounds(it.boundsInRoot()) },
                    onPaste = { controller.paste(selection) }, fontSize = preferences.font, scheme = scheme, fontId = preferences.terminalFont,
                    onInterceptKey = ::intercept, onSelection = { selection = it; onSelection(it) }, focusRequester = focus,
                    showKeyboard = showKeyboard, onTap = { showKeyboard = true; keyboardRequest++ }, onFontSizeChanged = preferences::setTerminalFont,
                    inputEnabled = inputEnabled, keyboardRequest = keyboardRequest, onHyperlinkClick = { controller.overlay = Overlay.OpenLink(it) })
            }
            // Read only in the draw phase, so a bell never recomposes the terminal.
            Box(Modifier.matchParentSize().drawBehind { if (flash.value > 0f) drawRect(Color(scheme.foreground).copy(alpha = flash.value)) })
            if (inputEnabled && running) SmallFloatingActionButton(
                onClick = { showKeyboard = !imeVisible; if (showKeyboard) keyboardRequest++ },
                modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp),
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .92f),
            ) { Icon(painterResource(if (imeVisible) R.drawable.ic_keyboard_hide else R.drawable.ic_keyboard), stringResource(if (imeVisible) R.string.hide_keyboard else R.string.show_keyboard)) }
        }
        val layout = remember(preferences.keys) { QuickKeyLayout.parse(preferences.keys) }
        TerminalKeyControls(layout, running, modifiers, selection, DockActions(
            paste = { focus.requestFocus(); controller.paste(selection) },
            commands = { controller.overlay = Overlay.Commands },
            compose = { controller.overlay = Overlay.ComposeCommand },
            customize = { controller.navigator.push(Screen.Keyboard) },
            textSize = { textSize = true },
        ), preferences.hapticKeys) { label ->
            when (label) {
                "/", "-", "|", "~" -> label.forEach { session.emulator.dispatchCharacter(modifiers.mask(), it.code) }
                else -> quickKeyCode(label)?.let { session.emulator.dispatchKey(modifiers.mask(), it) }
            }
            modifiers.clearTransients()
            focus.requestFocus()
        }
    }
    if (textSize) TextSizeDialog(preferences.font, preferences::setTerminalFont) { textSize = false }
}
