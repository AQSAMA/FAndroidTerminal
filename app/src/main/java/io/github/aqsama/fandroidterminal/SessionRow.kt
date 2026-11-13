// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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

/** Row actions are also reachable by long press/menu; swiping never terminates a process. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SessionRow(session: TerminalSession, environmentName: String, selected: Boolean, select: () -> Unit, action: (String) -> Unit) {
    val isSelected = selected
    val openLabel = stringResource(if (session.archived) R.string.session_restore_a11y else R.string.session_open_a11y, session.title, environmentName)
    val menuLabel = stringResource(R.string.session_options)
    val actionLabel = stringResource(R.string.session_actions_a11y, session.title, environmentName)
    var menu by remember { mutableStateOf(false) }
    val currentAction by rememberUpdatedState(action)
    val state = rememberSwipeToDismissBoxState(confirmValueChange = { direction ->
        when (direction) {
            SwipeToDismissBoxValue.StartToEnd -> currentAction(if (session.archived) "Restore" else "Archive")
            SwipeToDismissBoxValue.EndToStart -> currentAction("Close")
            SwipeToDismissBoxValue.Settled -> Unit
        }
        // Keep the row until the caller archives it or the user confirms Close.
        false
    })
    val status by session.status.collectAsStateWithLifecycle()
    val shape = RoundedCornerShape(16.dp)
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
            Row(Modifier.fillMaxWidth().combinedClickable(role = Role.Tab, onClickLabel = openLabel, onLongClickLabel = menuLabel, onClick = select, onLongClick = { menu = true }).semantics { contentDescription = openLabel; this.selected = isSelected }.padding(start = 14.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_terminal), null, Modifier.size(20.dp), tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(session.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (session.archived) stringResource(R.string.session_identity, stringResource(R.string.archived_sessions), statusLabel(status)) else statusLabel(status), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(painterResource(R.drawable.ic_more), actionLabel) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        ActionMenuItem(stringResource(R.string.rename_label), R.drawable.ic_edit) { menu = false; action("Rename") }
                        ActionMenuItem(stringResource(if (session.archived) R.string.restore_session else R.string.archive_session), if (session.archived) R.drawable.ic_restore else R.drawable.ic_archive) { menu = false; action(if (session.archived) "Restore" else "Archive") }
                        HorizontalDivider()
                        ActionMenuItem(stringResource(R.string.close_session_menu), R.drawable.ic_delete, destructive = true) { menu = false; action("Close") }
                    }
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
