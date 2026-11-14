// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aqsama.fandroidterminal.sessions.TerminalSession
import io.github.aqsama.fandroidterminal.sessions.SessionStatus

/** Immutable display state; native sessions remain owned by the service. */
internal data class SessionListEntry(val id: String, val title: String, val environmentId: String?, val archived: Boolean, val status: SessionStatus)

@Composable
internal fun TerminalSession.listEntry(): SessionListEntry {
    val state by status.collectAsStateWithLifecycle()
    return SessionListEntry(id, title, environmentId, archived, state)
}

/** Row actions are also reachable by long press/menu; swiping never terminates a process. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SessionRow(session: SessionListEntry, environmentName: String, selected: Boolean, select: () -> Unit, directClose: (() -> Unit)? = null, action: (String) -> Unit) {
    val isSelected = selected
    val openLabel = stringResource(if (session.archived) R.string.session_restore_a11y else R.string.session_open_a11y, session.title, environmentName)
    val menuLabel = stringResource(R.string.session_options)
    val actionLabel = stringResource(R.string.session_actions_a11y, session.title, environmentName)
    var menu by remember { mutableStateOf(false) }
    val currentAction by rememberUpdatedState(action)
    val currentArchived by rememberUpdatedState(session.archived)
    val state = rememberSwipeToDismissBoxState(confirmValueChange = { direction ->
        when (direction) {
            SwipeToDismissBoxValue.StartToEnd -> currentAction(if (currentArchived) "Restore" else "Archive")
            SwipeToDismissBoxValue.EndToStart -> currentAction("Close")
            SwipeToDismissBoxValue.Settled -> Unit
        }
        // Keep the row until the caller archives it or the user confirms Close.
        false
    })
    val status = session.status
    val shape = RoundedCornerShape(12.dp)
    SwipeToDismissBox(state = state, modifier = Modifier.clip(shape), backgroundContent = {
        val closing = state.dismissDirection == SwipeToDismissBoxValue.EndToStart
        Row(Modifier.fillMaxSize().background(if (closing) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = if (closing) Arrangement.End else Arrangement.Start) {
            Icon(painterResource(if (closing) R.drawable.ic_delete else if (session.archived) R.drawable.ic_restore else R.drawable.ic_archive), null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(if (closing) R.string.close_label else if (session.archived) R.string.restore_label else R.string.archive_label), style = MaterialTheme.typography.labelLarge)
        }
    }) {
        Surface(color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow, shape = shape) {
            Row(Modifier.fillMaxWidth().combinedClickable(role = Role.Tab, onClickLabel = openLabel, onLongClickLabel = menuLabel, onClick = select, onLongClick = { menu = true }).semantics { contentDescription = openLabel; this.selected = isSelected }.heightIn(min = 56.dp).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_terminal), null, Modifier.size(20.dp), tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text(session.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (session.archived) stringResource(R.string.archived_status, statusLabel(status)) else statusLabel(status), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (directClose != null) IconButton(onClick = directClose) {
                    Icon(painterResource(R.drawable.ic_close), stringResource(R.string.close_named_session, session.title, environmentName), tint = MaterialTheme.colorScheme.error)
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(painterResource(R.drawable.ic_more), actionLabel) }
                    SessionActionMenu(menu, { menu = false }, session.archived) { command -> menu = false; action(command) }
                }
            }
        }
    }
}

@Composable
internal fun ActionMenuItem(label: String, icon: Int, enabled: Boolean = true, destructive: Boolean = false, click: () -> Unit) {
    DropdownMenuItem(text = { Text(label) }, leadingIcon = { Icon(painterResource(icon), null) }, enabled = enabled, onClick = click,
        colors = if (destructive) MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.error, leadingIconColor = MaterialTheme.colorScheme.error) else MenuDefaults.itemColors())
}

@Composable
internal fun SessionActionMenu(expanded: Boolean, dismiss: () -> Unit, archived: Boolean, focus: (() -> Unit)? = null, files: (() -> Unit)? = null, action: (String) -> Unit) {
    DropdownMenu(expanded, dismiss) { SessionMenuItems(archived, focus, files, action) }
}

@Composable
internal fun ColumnScope.SessionMenuItems(archived: Boolean, focus: (() -> Unit)? = null, files: (() -> Unit)? = null, action: (String) -> Unit) {
    ActionMenuItem(stringResource(R.string.rename_label), R.drawable.ic_edit) { action("Rename") }
    ActionMenuItem(stringResource(if (archived) R.string.restore_session else R.string.archive_session), if (archived) R.drawable.ic_restore else R.drawable.ic_archive) { action(if (archived) "Restore" else "Archive") }
    if (files != null) ActionMenuItem(stringResource(R.string.environment_files), R.drawable.ic_files) { files() }
    if (focus != null) ActionMenuItem(stringResource(R.string.focus_mode), R.drawable.ic_focus) { focus() }
    HorizontalDivider()
    ActionMenuItem(stringResource(R.string.close_session_menu), R.drawable.ic_delete, destructive = true) { action("Close") }
}

/** Scoped recovery includes archived sessions because they can still own live processes. */
@Composable
internal fun SessionRecoveryList(environmentName: String, sessions: List<SessionListEntry>, selectedId: String?,
    dismiss: () -> Unit, select: (String) -> Unit, action: (String, String) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(modifier.padding(horizontal = 16.dp), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SheetHeader(stringResource(R.string.owned_sessions_title, environmentName), dismiss)
            Text(stringResource(R.string.owned_sessions_help), style = MaterialTheme.typography.bodyMedium)
        } }
        if (sessions.isEmpty()) item { Text(stringResource(R.string.no_owned_sessions)); TextButton(onClick = dismiss) { Text(stringResource(R.string.continue_label)) } }
        items(sessions, key = { it.id }) { session ->
            SessionRow(session, environmentName, session.id == selectedId, { select(session.id) }, directClose = { action(session.id, "Close") }) { action(session.id, it) }
        }
    }
}
