// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aqsama.fandroidterminal.runtime.linux.LinuxEnvironment
import io.github.aqsama.fandroidterminal.sessions.TerminalSession

/** Native drawer content shared by modal phone and permanent wide-window navigation. */
@Composable
internal fun WorkspaceSidebar(page: String, sessions: List<TerminalSession>, environments: List<LinuxEnvironment>, selectedId: String?, navigate: (String) -> Unit, select: (TerminalSession) -> Unit, actions: (TerminalSession) -> Unit, newSession: () -> Unit) {
    Column(Modifier.width(288.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.padding(12.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(painterResource(R.drawable.ic_terminal), null, tint = MaterialTheme.colorScheme.primary)
            Text("FAndroidTerminal", style = MaterialTheme.typography.titleMedium)
        }
        listOf("Terminal", "Environments", "Files", "Settings").forEach { destination ->
            NavigationDrawerItem(label = { Text(destination) }, selected = page == destination || (page == "Guest settings" && destination == "Environments"), onClick = { navigate(destination) },
                icon = { Icon(painterResource(destinationIcon(destination)), null) }, badge = if (destination == "Environments") ({ Text(environments.size.toString()) }) else null)
        }
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Sessions", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            IconButton(onClick = newSession, enabled = sessions.size < 12) { Icon(painterResource(R.drawable.ic_add), "New session") }
        }
        if (sessions.isEmpty()) Text("Open a terminal to start working.", Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        (listOf(null to "Android shell") + environments.map { it.id to it.name }).forEach { (id, name) ->
            val group = sessions.filter { it.environmentId == id }
            if (group.isNotEmpty()) {
                Text("$name · ${group.size}", Modifier.padding(start = 12.dp, top = 12.dp, bottom = 4.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                group.forEach { session ->
                    val status = session.status.collectAsStateWithLifecycle().value
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NavigationDrawerItem(label = { Column { Text(session.title, maxLines = 2); Text(statusLabel(status), style = MaterialTheme.typography.bodySmall) } }, selected = page == "Terminal" && selectedId == session.id,
                            onClick = { select(session) }, modifier = Modifier.weight(1f), icon = { Icon(painterResource(R.drawable.ic_terminal), null, Modifier.size(20.dp)) })
                        IconButton(onClick = { actions(session) }) { Icon(painterResource(R.drawable.ic_more), "Actions for ${session.title}") }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = { navigate("Environments") }, modifier = Modifier.fillMaxWidth()) { Icon(painterResource(R.drawable.ic_add), null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Add Linux environment") }
    }
}
internal fun destinationIcon(page: String) = when (page) { "Terminal" -> R.drawable.ic_terminal; "Environments" -> R.drawable.ic_environments; "Files" -> R.drawable.ic_files; else -> R.drawable.ic_settings }
