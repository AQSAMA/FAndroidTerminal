// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aqsama.fandroidterminal.runtime.ExecutionMode
import io.github.aqsama.fandroidterminal.sessions.SessionStatus
import io.github.aqsama.fandroidterminal.sessions.TerminalSession

/** Immutable display state; native sessions remain owned by the service. */
internal data class SessionListEntry(
    val id: String,
    val title: String,
    val environmentId: String?,
    val archived: Boolean,
    val status: SessionStatus,
    val mode: ExecutionMode = if (environmentId == null) ExecutionMode.Android else ExecutionMode.PRoot,
)

@Composable
internal fun TerminalSession.listEntry(): SessionListEntry {
    val state by status.collectAsStateWithLifecycle()
    return SessionListEntry(id, title, environmentId, archived, state, mode)
}

/**
 * Compact one-line session row. Supports swipe-to-archive/restore (start-to-end) and
 * swipe-to-close (end-to-start, routed through the close confirmation dialog), plus long-press
 * and TalkBack custom actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SessionRow(
    session: SessionListEntry,
    environmentName: String,
    selected: Boolean,
    select: () -> Unit,
    directClose: (() -> Unit)? = null,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    showEnvironmentTag: Boolean = false,
    enableSwipe: Boolean = directClose == null,
    action: (String) -> Unit,
) {
    val isSelected = selected
    val haptic = LocalHapticFeedback.current
    val openLabel = stringResource(
        if (session.archived) R.string.session_restore_a11y else R.string.session_open_a11y,
        session.title,
        environmentName,
    )
    val menuLabel = stringResource(R.string.session_options)
    val renameActionLabel = stringResource(R.string.rename_label)
    val archiveActionLabel = stringResource(if (session.archived) R.string.restore_session else R.string.archive_session)
    val closeActionLabel = stringResource(R.string.close_session)
    var menu by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)

    val rowContent: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(shape)
                .combinedClickable(
                    role = Role.Tab,
                    onClickLabel = openLabel,
                    onLongClickLabel = menuLabel,
                    onClick = select,
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        menu = true
                    },
                )
                .semantics {
                    contentDescription = openLabel
                    this.selected = isSelected
                    customActions = listOf(
                        CustomAccessibilityAction(renameActionLabel) { action("Rename"); true },
                        CustomAccessibilityAction(archiveActionLabel) { action(if (session.archived) "Restore" else "Archive"); true },
                        CustomAccessibilityAction(closeActionLabel) { action("Close"); true },
                    )
                }
                .padding(vertical = 2.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            val containerColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.78f) else Color.Transparent,
                label = "sessionRowBg",
            )
            Surface(color = containerColor, shape = shape, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = if (isSelected) 4.dp else 10.dp,
                            end = if (directClose != null) 4.dp else 10.dp,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isSelected) {
                        Box(
                            Modifier
                                .width(3.dp)
                                .height(18.dp)
                                .background(accentColor, CircleShape)
                        )
                        Spacer(Modifier.width(7.dp))
                    }
                    SessionStatusDot(session.status, archived = session.archived)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = session.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = when {
                            isSelected -> MaterialTheme.colorScheme.onSecondaryContainer
                            session.archived -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f)
                            session.status is SessionStatus.Exited -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (showEnvironmentTag) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = environmentName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 88.dp),
                        )
                    }
                    if (session.mode.isNonDefault) {
                        Spacer(Modifier.width(6.dp))
                        ExecutionModePill(session.mode)
                    }
                    SessionStatusChip(session.status)
                    if (directClose != null) {
                        IconButton(onClick = directClose) {
                            Icon(
                                painterResource(R.drawable.ic_delete),
                                stringResource(R.string.close_named_session, session.title, environmentName),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                        IconButton(onClick = { menu = true }) {
                            Icon(
                                painterResource(R.drawable.ic_more),
                                stringResource(R.string.session_actions_a11y, session.title, environmentName),
                            )
                        }
                    }
                }
            }
            SessionActionMenu(menu, { menu = false }, session.archived) { command ->
                menu = false
                action(command)
            }
        }
    }

    if (!enableSwipe) {
        rowContent()
        return
    }

    val swipeState = rememberSwipeToDismissBoxState()
    LaunchedEffect(swipeState.currentValue) {
        when (swipeState.currentValue) {
            SwipeToDismissBoxValue.StartToEnd -> {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                action(if (session.archived) "Restore" else "Archive")
                swipeState.reset()
            }
            SwipeToDismissBoxValue.EndToStart -> {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                action("Close")
                swipeState.reset()
            }
            SwipeToDismissBoxValue.Settled -> Unit
        }
    }

    SwipeToDismissBox(
        state = swipeState,
        modifier = Modifier.clip(shape),
        backgroundContent = {
            val direction = swipeState.dismissDirection
            val isStart = direction == SwipeToDismissBoxValue.StartToEnd
            val isEnd = direction == SwipeToDismissBoxValue.EndToStart
            val bgColor by animateColorAsState(
                targetValue = when {
                    isStart -> MaterialTheme.colorScheme.secondaryContainer
                    isEnd -> MaterialTheme.colorScheme.errorContainer
                    else -> Color.Transparent
                },
                label = "swipeBg",
            )
            val contentColor = when {
                isStart -> MaterialTheme.colorScheme.onSecondaryContainer
                isEnd -> MaterialTheme.colorScheme.onErrorContainer
                else -> Color.Transparent
            }
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bgColor, shape)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (isEnd) Arrangement.End else Arrangement.Start,
            ) {
                if (isStart) {
                    Icon(
                        painterResource(if (session.archived) R.drawable.ic_restore else R.drawable.ic_archive),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = contentColor,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(if (session.archived) R.string.restore_label else R.string.archive_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = contentColor,
                    )
                } else if (isEnd) {
                    Text(
                        stringResource(R.string.close_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = contentColor,
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        painterResource(R.drawable.ic_delete),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = contentColor,
                    )
                }
            }
        },
        content = { rowContent() },
    )
}

private val ExecutionMode.isNonDefault: Boolean
    get() = this == ExecutionMode.Root || this == ExecutionMode.Shizuku || this == ExecutionMode.Chroot

@Composable
private fun SessionStatusDot(status: SessionStatus, archived: Boolean) {
    val isDark = MaterialTheme.colorScheme.surface.let { 0.2126f * it.red + 0.7152f * it.green + 0.0722f * it.blue } < 0.5f
    val runningColor = if (isDark) Color(0xFF5AD28A) else Color(0xFF1A8245)
    val alpha = if (archived) 0.65f else 1f
    when (status) {
        SessionStatus.Running -> Box(
            Modifier
                .size(8.dp)
                .background(runningColor.copy(alpha = alpha), CircleShape)
        )
        SessionStatus.Starting -> Box(
            Modifier
                .size(8.dp)
                .background(MaterialTheme.colorScheme.tertiary.copy(alpha = alpha), CircleShape)
        )
        is SessionStatus.Exited -> Box(
            Modifier
                .size(8.dp)
                .border(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = alpha), CircleShape)
        )
        is SessionStatus.Failed -> Box(
            Modifier
                .size(8.dp)
                .background(MaterialTheme.colorScheme.error.copy(alpha = alpha), CircleShape)
        )
    }
}

@Composable
private fun ExecutionModePill(mode: ExecutionMode) {
    val isPrivileged = mode == ExecutionMode.Root || mode == ExecutionMode.Chroot
    val container = if (isPrivileged) {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.52f)
    }
    val stroke = if (isPrivileged) {
        MaterialTheme.colorScheme.error.copy(alpha = 0.36f)
    } else {
        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.32f)
    }
    val content = if (isPrivileged) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onTertiaryContainer
    }
    val label = stringResource(
        when (mode) {
            ExecutionMode.Root -> R.string.sidebar_mode_root
            ExecutionMode.Shizuku -> R.string.sidebar_mode_shizuku
            ExecutionMode.Chroot -> R.string.sidebar_mode_chroot
            else -> R.string.sidebar_mode_root
        }
    )
    val a11y = stringResource(
        when (mode) {
            ExecutionMode.Root -> R.string.sidebar_mode_root_a11y
            ExecutionMode.Shizuku -> R.string.sidebar_mode_shizuku_a11y
            ExecutionMode.Chroot -> R.string.sidebar_mode_chroot_a11y
            else -> R.string.sidebar_mode_root_a11y
        }
    )
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = container,
        border = BorderStroke(0.5.dp, stroke),
        modifier = Modifier.semantics { contentDescription = a11y },
    ) {
        Row(
            Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Icon(
                painterResource(R.drawable.ic_shield),
                contentDescription = null,
                modifier = Modifier.size(11.dp),
                tint = content,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = if (isPrivileged) FontFamily.Monospace else FontFamily.Default,
                fontWeight = FontWeight.Medium,
                color = content,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SessionStatusChip(status: SessionStatus) {
    when (status) {
        SessionStatus.Running -> Unit
        SessionStatus.Starting -> {
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(R.string.sidebar_starting_short),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        is SessionStatus.Exited -> {
            Spacer(Modifier.width(6.dp))
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
            ) {
                Text(
                    text = stringResource(R.string.sidebar_exit_code, status.code.toString()),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }
        }
        is SessionStatus.Failed -> {
            Spacer(Modifier.width(6.dp))
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Text(
                    text = stringResource(R.string.sidebar_failed_short),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                )
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
internal fun SessionActionMenu(expanded: Boolean, dismiss: () -> Unit, archived: Boolean, files: (() -> Unit)? = null, action: (String) -> Unit) {
    DropdownMenu(expanded, dismiss) { SessionMenuItems(archived, files, action) }
}

@Composable
internal fun ColumnScope.SessionMenuItems(archived: Boolean, files: (() -> Unit)? = null, action: (String) -> Unit) {
    ActionMenuItem(stringResource(R.string.rename_label), R.drawable.ic_edit) { action("Rename") }
    ActionMenuItem(stringResource(if (archived) R.string.restore_session else R.string.archive_session), if (archived) R.drawable.ic_restore else R.drawable.ic_archive) { action(if (archived) "Restore" else "Archive") }
    if (files != null) ActionMenuItem(stringResource(R.string.environment_files), R.drawable.ic_files) { files() }
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
