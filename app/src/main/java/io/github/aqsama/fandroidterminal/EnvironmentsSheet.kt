// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aqsama.fandroidterminal.designsystem.ConnectedGroup
import io.github.aqsama.fandroidterminal.runtime.linux.LinuxEnvironment
import io.github.aqsama.fandroidterminal.sessions.TerminalService

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EnvironmentsSheet(service: TerminalService, dismiss: () -> Unit, opened: (String) -> Unit) {
    val manager = service.environments
    val environments by manager.environments.collectAsStateWithLifecycle()
    val progress by manager.progress.collectAsStateWithLifecycle()
    val operationError by manager.error.collectAsStateWithLifecycle()
    val sessions by service.sessions.sessions.collectAsStateWithLifecycle()
    var name by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<LinuxEnvironment?>(null) }
    var deleting by remember { mutableStateOf<LinuxEnvironment?>(null) }
    var renamed by remember { mutableStateOf("") }
    fun perform(action: () -> Unit) { try { action(); error = null } catch (e: Exception) { error = e.message } }
    ModalBottomSheet(onDismissRequest = dismiss) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Environments", style = MaterialTheme.typography.headlineMedium)
            Text("Each Debian environment has its own packages, home and files. PRoot runs as this app's Android UID; guest root does not grant Android root.", style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = { perform { TerminalService.ensureStarted(service); opened(service.sessions.create().id) } }, enabled = sessions.size < 12) { Text("Open Android shell") }
            environments.forEach { environment ->
                val operation = progress[environment.id]
                val count = sessions.count { it.environmentId == environment.id }
                ConnectedGroup {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(environment.name, style = MaterialTheme.typography.titleLarge)
                            Text("Debian 13 · ARM64 · $count open sessions", style = MaterialTheme.typography.bodySmall)
                            Text(operation ?: environment.state, color = if (!environment.ready && operation == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                            if (operation != null) {
                                LinearProgressIndicator(Modifier.fillMaxWidth())
                                if (environment.state == "Installing") TextButton(onClick = { manager.cancel(environment.id) }) { Text("Cancel installation") }
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (environment.ready) Button(onClick = { perform {
                                    TerminalService.ensureStarted(service)
                                    val spec = manager.specification(environment.id)
                                    opened(service.sessions.create(environment.id, environment.name, spec).id)
                                } }, enabled = operation == null && sessions.size < 12) { Text("Open terminal") }
                                else if (operation == null) Button(onClick = { perform { manager.install(environment.id) } }, enabled = progress.isEmpty()) { Text("Retry installation") }
                                TextButton(onClick = { editing = environment; renamed = environment.name }, enabled = operation == null) { Text("Rename") }
                                TextButton(onClick = { deleting = environment }, enabled = progress.isEmpty() && count == 0) { Text("Delete") }
                            }
                            if (count > 0) Text("Close its session tabs before deleting this environment.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            HorizontalDivider()
            Text("New Debian environment", style = MaterialTheme.typography.titleLarge)
            Text("17 MiB download · allow at least 800 MiB free internal storage. Keep the app open during setup; returning to a terminal leaves setup running.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Environment name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
            operationError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = { perform { manager.create(name); name = "" } }, enabled = manager.supported && name.isNotBlank() && progress.isEmpty()) { Text("Install Debian") }
            if (!manager.supported) Text("Linux environments require an ARM64 device. Android shell remains available.")
        }
    }
    editing?.let { environment ->
        AlertDialog(onDismissRequest = { editing = null }, title = { Text("Rename environment") },
            text = { Column { OutlinedTextField(renamed, { renamed = it }, label = { Text("Name") }, singleLine = true); error?.let { Text(it, color = MaterialTheme.colorScheme.error) } } },
            confirmButton = { TextButton(onClick = { perform { manager.rename(environment.id, renamed); editing = null } }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } })
    }
    deleting?.let { environment ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Delete ${environment.name}?") },
            text = { Text("Permanently delete all its packages, home files and projects from this device. This cannot be undone.") },
            confirmButton = { TextButton(onClick = { perform { manager.delete(environment.id, service.sessions); deleting = null } }) { Text("Delete all data") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Keep environment") } })
    }
}
