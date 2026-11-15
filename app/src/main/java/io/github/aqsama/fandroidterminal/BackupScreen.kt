// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aqsama.fandroidterminal.runtime.linux.WorkspaceBackup
import io.github.aqsama.fandroidterminal.sessions.TerminalService

@Composable
internal fun BackupScreen(service: TerminalService, preferences: Preferences, workspace: WorkspaceState) {
    val context = LocalContext.current
    val resources = androidx.compose.ui.platform.LocalResources.current
    val environments by service.environments.environments.collectAsStateWithLifecycle()
    val prepared by service.environments.workspaceRestore.collectAsStateWithLifecycle()
    val outcomes by service.environments.operationResults.collectAsStateWithLifecycle()
    val progress by service.environments.progress.collectAsStateWithLifecycle()
    var groups by rememberSaveable { mutableStateOf(PortableSettings.groups.keys.toList()) }
    var selected by rememberSaveable { mutableStateOf(listOf<String>()) }
    var restoreSelection by rememberSaveable { mutableStateOf(listOf<String>()) }
    var restoreGroups by rememberSaveable { mutableStateOf(listOf<String>()) }
    var includeEnvironments by rememberSaveable { mutableStateOf(true) }
    var encrypt by rememberSaveable { mutableStateOf(true) }
    // Deliberately not saveable: passwords do not enter saved-instance state.
    var password by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }
    val completed = stringResource(R.string.backup_completed)
    val portable = remember { PortableSettings(context) }
    // Snapshot chooser intent; later edits cannot silently retarget the reviewed operation.

    fun prepare(): BackupRequest {
        require(!encrypt || password.length in 8..1024) { resources.getString(R.string.backup_password_help) }
        return BackupRequest(groups, if (includeEnvironments) selected else emptyList(), if (encrypt) password else null, includeEnvironments)
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val plan = workspace.pendingBackup; workspace.pendingBackup = null
        if (uri != null && plan != null) try {
            val target = environments.filter { it.id in plan.environments }
            check(target.size == plan.environments.size && target.all { it.ready })
            check(service.sessions.sessions.value.none { it.environmentId in plan.environments }) { resources.getString(R.string.backup_close_sessions) }
            val settings = portable.export(plan.groups.toSet())
            service.environments.operation("workspace-backup", "Exporting workspace backup…", { failure -> result = failure?.message ?: completed }) { files, cancel, report ->
                val secret = plan.password?.toCharArray()
                try { context.contentResolver.openOutputStream(uri, "wt")!!.use { out ->
                    WorkspaceBackup(files, context.cacheDir, cancel, report).export(settings, target, out, secret)
                } } finally { secret?.fill('\u0000') }
            }
        } catch (e: Exception) { result = e.message }
        password = ""
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val plan = workspace.pendingBackup; workspace.pendingBackup = null
        if (uri != null && plan != null) try {
            // Workspace operation blocks new guest launches globally while duplicate trees are promoted.
            service.environments.operation("workspace-backup", "Restoring workspace backup…", { failure ->
                result = failure?.message
            }) { files, cancel, report ->
                val secret = plan.password?.toCharArray()
                try {
                    val staged = context.contentResolver.openInputStream(uri)!!.use { input ->
                        WorkspaceBackup(files, context.cacheDir, cancel, report).prepare(input, secret) { portable.validate(it) }
                    }
                    service.environments.closePreparedRestore()
                    service.environments.workspaceRestore.value = staged
                    restoreSelection = if (plan.restoreEnvironments) staged.environments.map { it.originalId } else emptyList()
                    restoreGroups = plan.groups
                } finally { secret?.fill('\u0000') }
            }
        } catch (e: Exception) { result = e.message }
        password = ""
    }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(stringResource(R.string.backup_scope_help)) }
        item { Text(stringResource(R.string.backup_settings_title), style = MaterialTheme.typography.titleMedium) }
        items(PortableSettings.groups.keys.toList()) { group ->
            val label = when (group) { "appearance" -> R.string.settings_appearance; "input" -> R.string.backup_input; "startup" -> R.string.backup_startup; else -> R.string.guest_defaults_title }
            Row(Modifier.fillMaxWidth().toggleable(group in groups, role = Role.Checkbox) { checked -> groups = if (checked) groups + group else groups - group }) { Checkbox(group in groups, null); Text(stringResource(label)) }
        }
        item { Row(Modifier.fillMaxWidth().toggleable(includeEnvironments, role = Role.Checkbox) { includeEnvironments = it }) { Checkbox(includeEnvironments, null); Text(stringResource(R.string.backup_environments_title)) } }
        if (includeEnvironments) items(environments.filter { it.ready }, key = { it.id }) { environment ->
            Row(Modifier.fillMaxWidth().toggleable(environment.id in selected, role = Role.Checkbox) { checked -> selected = if (checked) selected + environment.id else selected - environment.id }) { Checkbox(environment.id in selected, null); Text(environment.name) }
        }
        item {
            Row(Modifier.fillMaxWidth().toggleable(encrypt, role = Role.Checkbox) { encrypt = it }) { Checkbox(encrypt, null); Text(stringResource(R.string.backup_encrypt)) }
            if (encrypt) OutlinedTextField(password, { if (it.length <= 1024) password = it }, label = { Text(stringResource(R.string.backup_password)) },
                supportingText = { Text(stringResource(R.string.backup_password_help)) }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
            Text(stringResource(R.string.backup_portability_help), style = MaterialTheme.typography.bodySmall)
        }
        item {
            (result ?: outcomes["workspace-backup"])?.let { Text(operationLabel(it)) }
            if ("workspace-backup" in progress) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(operationLabel(progress["workspace-backup"].orEmpty().takeLast(1000)))
                TextButton({ service.environments.cancel("workspace-backup") }) { Text(stringResource(R.string.cancel_label)) }
            }
            Row {
                Button(enabled = progress.isEmpty() && workspace.pendingBackup == null, onClick = {
                    try { workspace.pendingBackup = prepare(); export.launch("FAndroidTerminal-workspace.fatworkspace") } catch (e: Exception) { result = e.message }
                }) { Text(stringResource(R.string.backup_export)) }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(enabled = progress.isEmpty() && workspace.pendingBackup == null, onClick = {
                    try { workspace.pendingBackup = prepare(); restore.launch(arrayOf("application/octet-stream", "*/*")) } catch (e: Exception) { result = e.message }
                }) { Text(stringResource(R.string.backup_restore)) }
            }
        }
    }
    prepared?.let { staged ->
        AlertDialog(onDismissRequest = { if (progress.isEmpty()) service.environments.closePreparedRestore() },
            title = { Text(stringResource(R.string.backup_restore_review)) },
            text = {
                LazyColumn {
                    item { Text(stringResource(R.string.backup_restore_review_help)) }
                    items(staged.environments, key = { it.originalId }) { entry ->
                        Row(Modifier.fillMaxWidth().toggleable(entry.originalId in restoreSelection, role = Role.Checkbox) { checked ->
                            restoreSelection = if (checked) restoreSelection + entry.originalId else restoreSelection - entry.originalId
                        }) {
                            Checkbox(entry.originalId in restoreSelection, null)
                            Text("${entry.name} · ${entry.distribution} ${entry.release}")
                        }
                    }
                }
            },
            confirmButton = { TextButton(enabled = progress.isEmpty(), onClick = {
                val ids = restoreSelection.toSet(); val chosenGroups = restoreGroups.toSet()
                try {
                    service.environments.operation("workspace-backup", "Restoring workspace backup…", { failure ->
                        if (failure == null) preferences.reload()
                        result = failure?.message ?: completed
                    }) { files, cancel, report ->
                        val restored = staged.restore(ids, cancel, report)
                        portable.restore(restored.settings, chosenGroups, restored.identities, files.load().map { it.id }.toSet())
                        service.environments.closePreparedRestore()
                    }
                } catch (e: Exception) { result = e.message }
            }) { Text(stringResource(R.string.backup_restore)) } },
            dismissButton = { TextButton(enabled = progress.isEmpty(), onClick = { service.environments.closePreparedRestore() }) { Text(stringResource(R.string.cancel_label)) } })
    }

}
