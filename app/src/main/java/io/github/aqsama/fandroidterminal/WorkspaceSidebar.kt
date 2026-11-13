// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aqsama.fandroidterminal.runtime.linux.LinuxEnvironment
import io.github.aqsama.fandroidterminal.sessions.TerminalSession

/** Native drawer content shared by modal phone and permanent wide-window navigation. */
@Composable
internal fun WorkspaceSidebar(page: String, sessions: List<TerminalSession>, environments: List<LinuxEnvironment>, selectedId: String?, navigate: (String) -> Unit, select: (TerminalSession) -> Unit, action: (TerminalSession, String) -> Unit, archived: () -> Unit, newSession: () -> Unit) {
    val active = sessions.filterNot { it.archived }
    Column(Modifier.width(288.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.padding(12.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(painterResource(R.drawable.ic_terminal), null, tint = MaterialTheme.colorScheme.primary)
            Text("FAndroidTerminal", style = MaterialTheme.typography.titleMedium)
        }
        listOf("Terminal", "Environments", "Files", "Settings").forEach { destination ->
            NavigationDrawerItem(label = { Text(stringResource(destinationLabel(destination))) }, selected = page == destination || (page == "Guest settings" && destination == "Environments"), onClick = { navigate(destination) },
                icon = { Icon(painterResource(destinationIcon(destination)), null) }, badge = if (destination == "Environments") ({ Text(environments.size.toString()) }) else null)
        }
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.sessions_label), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            IconButton(onClick = newSession, enabled = sessions.size < 12) { Icon(painterResource(R.drawable.ic_add), stringResource(R.string.new_session)) }
        }
        if (sessions.size >= 12) Text(stringResource(R.string.session_limit_hint, sessions.count { it.archived }), Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (active.isEmpty()) Text(if (sessions.isEmpty()) stringResource(R.string.open_terminal_hint) else stringResource(R.string.no_visible_archived, sessions.count { it.archived }), Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        (listOf(null to stringResource(R.string.android_shell)) + environments.map { it.id to it.name }).forEach { (id, name) ->
            val group = active.filter { it.environmentId == id }
            if (group.isNotEmpty()) {
                Text(stringResource(R.string.session_group_count, name, group.size), Modifier.padding(start = 12.dp, top = 12.dp, bottom = 4.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                group.forEach { session ->
                    key(session.id) { SessionRow(session, name, page == "Terminal" && selectedId == session.id, { select(session) }) { action(session, it) } }
                }
            }
        }
        val archivedCount = sessions.count { it.archived }
        TextButton(onClick = archived, modifier = Modifier.fillMaxWidth()) {
            Icon(painterResource(R.drawable.ic_archive), null, Modifier.size(20.dp)); Spacer(Modifier.width(12.dp)); Text(stringResource(R.string.archived_sessions)); Spacer(Modifier.weight(1f)); Text(archivedCount.toString())
        }
        Text(stringResource(R.string.swipe_session_hint), Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
internal fun destinationIcon(page: String) = when (page) { "Terminal" -> R.drawable.ic_terminal; "Environments" -> R.drawable.ic_environments; "Files" -> R.drawable.ic_files; else -> R.drawable.ic_settings }

internal fun destinationLabel(page: String) = when (page) { "Terminal" -> R.string.terminal_label; "Environments" -> R.string.environments_label; "Files" -> R.string.files_label; else -> R.string.settings_label }
