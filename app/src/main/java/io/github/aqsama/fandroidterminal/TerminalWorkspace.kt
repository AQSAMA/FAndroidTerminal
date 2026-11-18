// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.aqsama.fandroidterminal.runtime.ExecutionMode
import io.github.aqsama.fandroidterminal.runtime.linux.LinuxEnvironment
import io.github.aqsama.fandroidterminal.runtime.linux.LinuxImages
import io.github.aqsama.fandroidterminal.sessions.SessionStatus
import io.github.aqsama.fandroidterminal.sessions.TerminalSession

@Composable
internal fun modeLabel(mode: ExecutionMode) = stringResource(when (mode) {
    ExecutionMode.Android -> R.string.workspace_app_permissions
    ExecutionMode.Shizuku -> R.string.workspace_shizuku
    ExecutionMode.Root -> R.string.device_root
    ExecutionMode.PRoot -> R.string.workspace_proot
    ExecutionMode.Chroot -> R.string.workspace_root_chroot
})

@Composable
internal fun TerminalHeader(
    session: TerminalSession?,
    status: SessionStatus?,
    environment: LinuxEnvironment?,
    sessions: List<TerminalSession>,
    environmentName: (TerminalSession) -> String,
    selectSession: (TerminalSession) -> Unit,
    canCreate: Boolean,
    openSidebar: (() -> Unit)?,
    chooseSession: () -> Unit,
    newHere: () -> Unit,
    openSettings: () -> Unit,
    openFiles: () -> Unit,
    openPackages: () -> Unit,
    sessionAction: (String) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var switching by remember { mutableStateOf(false) }
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (session == null && openSidebar != null) {
                IconButton(onClick = openSidebar) {
                    Icon(painterResource(R.drawable.ic_menu), stringResource(R.string.open_sidebar))
                }
            } else {
                ContextBadge(environment?.distribution, small = true)
            }
            Box(Modifier.weight(1f)) {
                val canSwitch = sessions.size > 1
                val switchAction = if (canSwitch) Modifier.clickable(
                    onClickLabel = stringResource(R.string.switch_session), role = Role.Button,
                ) { switching = true } else Modifier
                Column(Modifier.fillMaxWidth().heightIn(min = 48.dp).then(switchAction).padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalArrangement = Arrangement.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(session?.title ?: stringResource(R.string.terminal_label),
                            Modifier.weight(1f, fill = false).semantics { heading() }, style = MaterialTheme.typography.titleMedium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (canSwitch) Icon(painterResource(R.drawable.ic_chevron_down), null,
                            Modifier.padding(start = 4.dp).size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (session != null && status != null) Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        SessionStatusMark(status)
                        val mode = if (session.mode == ExecutionMode.Shizuku) session.executionIdentity else modeLabel(session.mode)
                        Text(stringResource(R.string.workspace_session_context, environmentName(session), mode),
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (canSwitch) DropdownMenu(switching, { switching = false }, modifier = Modifier.widthIn(max = 300.dp)) {
                    sessions.forEach { candidate ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(candidate.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(stringResource(R.string.workspace_session_context, environmentName(candidate), modeLabel(candidate.mode)),
                                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            },
                            trailingIcon = if (candidate.id == session?.id) ({
                                Icon(painterResource(R.drawable.ic_check), stringResource(R.string.workspace_current_session))
                            }) else null,
                            onClick = { switching = false; selectSession(candidate) },
                        )
                    }
                }
            }

            val newSessionLabel = stringResource(R.string.new_session)
            Box(
                Modifier.size(48.dp).semantics { contentDescription = newSessionLabel }.combinedClickable(
                    enabled = canCreate,
                    role = Role.Button,
                    onLongClickLabel = stringResource(R.string.new_terminal_here),
                    onLongClick = newHere,
                    onClick = chooseSession,
                ),
                contentAlignment = Alignment.Center,
            ) {
                Surface(color = if (canCreate) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                    shape = MaterialTheme.shapes.medium) {
                    Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.ic_add), null,
                            tint = if (canCreate) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(painterResource(R.drawable.ic_more), stringResource(if (session == null) R.string.settings_label else R.string.session_actions))
                }
                DropdownMenu(menu, { menu = false }) {
                    ActionMenuItem(stringResource(if (environment == null) R.string.settings_label else R.string.guest_settings_title),
                        R.drawable.ic_settings) { menu = false; openSettings() }
                    if (environment != null) {
                        ActionMenuItem(stringResource(R.string.packages_title), R.drawable.ic_download) { menu = false; openPackages() }
                    }
                    if (session != null) {
                        HorizontalDivider()
                        SessionMenuItems(session.archived, files = if (environment == null) null else ({ menu = false; openFiles() })) {
                            menu = false
                            sessionAction(it)
                        }
                    }
                }
            }
        }
        HorizontalDivider()
    }
}

@Composable
internal fun WorkspaceStart(
    environments: List<LinuxEnvironment>,
    busy: Set<String>,
    supported: Boolean,
    canCreate: Boolean,
    hasArchived: Boolean,
    open: (String?, ExecutionMode) -> Unit,
    browseLinux: () -> Unit,
    showArchived: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Surface(
                onClick = { open(null, ExecutionMode.Android) }, enabled = canCreate,
                modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth(),
                shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
                        Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                            Text(">_", style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Monospace)
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(R.string.new_android_shell), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.device_shell_summary), style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(painterResource(R.drawable.ic_chevron), null, Modifier.size(20.dp))
                }
            }
        }
        items(environments.filter { it.ready }, key = { it.id }) { guest ->
            Surface(Modifier.widthIn(max = 520.dp).fillMaxWidth(), shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                LaunchDestination(guest.name, guestRelease(guest), contextIcon(guest.distribution), canCreate && guest.id !in busy) {
                    open(guest.id, ExecutionMode.PRoot)
                }
            }
        }
        item {
            Surface(Modifier.widthIn(max = 520.dp).fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                LaunchDestination(
                    stringResource(if (supported && environments.isEmpty()) R.string.install_linux else R.string.environments_label),
                    if (supported && environments.isEmpty()) stringResource(R.string.workspace_linux_choices) else null,
                    R.drawable.ic_environments, true, browseLinux,
                )
            }
        }
        if (hasArchived) item {
            TextButton(onClick = showArchived) {
                Icon(painterResource(R.drawable.ic_archive), null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.view_archived_sessions))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NewSessionSheet(
    environments: List<LinuxEnvironment>,
    busy: Set<String>,
    supported: Boolean,
    canCreate: Boolean,
    dismiss: () -> Unit,
    browseLinux: () -> Unit,
    open: (String?, ExecutionMode) -> Unit,
) {
    var advanced by rememberSaveable { mutableStateOf(false) }
    val installed = environments.filter { it.ready }
    ModalBottomSheet(onDismissRequest = dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            SheetHeader(stringResource(R.string.new_session), dismiss)
            LazyColumn(
                Modifier.weight(1f, fill = false),
                contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item {
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.medium) {
                        LaunchDestination(stringResource(R.string.android_shell), stringResource(R.string.device_shell_summary),
                            R.drawable.ic_android, canCreate) { open(null, ExecutionMode.Android) }
                    }
                }
                items(installed, key = { it.id }) { guest ->
                    LaunchDestination(guest.name, guestRelease(guest), contextIcon(guest.distribution), canCreate && guest.id !in busy) {
                        open(guest.id, ExecutionMode.PRoot)
                    }
                }
                item {
                    LaunchDestination(stringResource(if (supported && environments.isEmpty()) R.string.install_linux else R.string.environments_label),
                        if (supported && environments.isEmpty()) stringResource(R.string.workspace_linux_choices) else null,
                        R.drawable.ic_environments, true, browseLinux)
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    TextButton(onClick = { advanced = !advanced }) {
                        Icon(painterResource(if (advanced) R.drawable.ic_chevron_up else R.drawable.ic_chevron_down), null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(if (advanced) R.string.workspace_hide_modes else R.string.workspace_advanced_modes))
                    }
                }
                if (advanced) {
                    item {
                        LaunchDestination(modeLabel(ExecutionMode.Shizuku), stringResource(R.string.workspace_shizuku_help),
                            R.drawable.ic_android, canCreate) { open(null, ExecutionMode.Shizuku) }
                    }
                    item {
                        LaunchDestination(modeLabel(ExecutionMode.Root), stringResource(R.string.workspace_root_help),
                            R.drawable.ic_shield, canCreate) { open(null, ExecutionMode.Root) }
                    }
                    items(installed, key = { "chroot-${it.id}" }) { guest ->
                        LaunchDestination(guest.name, modeLabel(ExecutionMode.Chroot),
                            R.drawable.ic_shield, canCreate && guest.id !in busy) { open(guest.id, ExecutionMode.Chroot) }
                    }
                }
            }
        }
    }
}

@Composable
private fun guestRelease(guest: LinuxEnvironment) = stringResource(
    R.string.workspace_linux_release, LinuxImages.get(guest.distribution).name, guest.release,
)

@Composable
private fun LaunchDestination(title: String, detail: String?, icon: Int, enabled: Boolean, open: () -> Unit) {
    Surface(onClick = open, enabled = enabled, shape = MaterialTheme.shapes.medium, color = androidx.compose.ui.graphics.Color.Transparent) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            val tint = if (enabled) LocalContentColor.current else LocalContentColor.current.copy(alpha = .38f)
            Icon(painterResource(icon), null, Modifier.size(22.dp), tint = tint)
            Column(Modifier.weight(1f)) {
                Text(title, color = tint, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (detail != null) Text(detail, color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else tint,
                    style = MaterialTheme.typography.bodySmall)
            }
            Icon(painterResource(R.drawable.ic_chevron), null, Modifier.size(18.dp), tint = tint)
        }
    }
}
