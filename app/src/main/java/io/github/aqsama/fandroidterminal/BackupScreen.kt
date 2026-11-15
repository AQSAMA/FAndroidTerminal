// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import io.github.aqsama.fandroidterminal.designsystem.ConnectedGroup
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
import io.github.aqsama.fandroidterminal.runtime.linux.BackupPasswordRequired
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
    var restoreUri by rememberSaveable { mutableStateOf<String?>(null) }
    var requestedGroups by rememberSaveable { mutableStateOf(listOf<String>()) }
    var requestedEnvironments by rememberSaveable { mutableStateOf(true) }
    var restorePassword by remember { mutableStateOf("") }
    var restoreError by remember { mutableStateOf<String?>(null) }
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
    fun readBackup(uri: String, chosenGroups: List<String>, includeGuests: Boolean, passwordText: String? = null) {
        try {
            service.environments.operation("workspace-backup", "Restoring workspace backup…", { failure ->
                when (failure) {
                    is BackupPasswordRequired -> { restoreUri = uri; requestedGroups = chosenGroups; requestedEnvironments = includeGuests; restoreError = null }
                    null -> { restoreUri = null; restoreError = null }
                    else -> { if (restoreUri != null) restoreError = failure.message else result = failure.message }
                }
            }) { files, cancel, report ->
                val secret = passwordText?.toCharArray()
                try {
                    val staged = context.contentResolver.openInputStream(android.net.Uri.parse(uri))!!.use { input ->
                        WorkspaceBackup(files, context.cacheDir, cancel, report).prepare(input, secret) { portable.validate(it) }
                    }
                    service.environments.closePreparedRestore()
                    service.environments.workspaceRestore.value = staged
                    restoreSelection = if (includeGuests) staged.environments.map { it.originalId } else emptyList()
                    restoreGroups = chosenGroups
                } finally { secret?.fill('\u0000') }
            }
        } catch (e: Exception) { if (restoreUri != null) restoreError = e.message else result = e.message }
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val plan = workspace.pendingBackup; workspace.pendingBackup = null
        if (uri != null && plan != null) readBackup(uri.toString(), plan.groups, plan.restoreEnvironments)
    }
    if (restoreUri != null) AlertDialog(onDismissRequest = { if (progress.isEmpty()) { restoreUri = null; restorePassword = "" } },
        title = { Text(stringResource(R.string.backup_unlock)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(restorePassword, { if (it.length <= 1024) restorePassword = it; restoreError = null },
                label = { Text(stringResource(R.string.backup_password)) }, visualTransformation = PasswordVisualTransformation(), singleLine = true, isError = restoreError != null)
            restoreError?.let { Text(operationLabel(it), color = MaterialTheme.colorScheme.error) }
            if ("workspace-backup" in progress) LinearProgressIndicator(Modifier.fillMaxWidth())
        } },
        confirmButton = { TextButton(enabled = progress.isEmpty() && restorePassword.length in 8..1024, onClick = {
            val secret = restorePassword; restorePassword = ""; restoreError = null
            readBackup(restoreUri!!, requestedGroups, requestedEnvironments, secret)
        }) { Text(stringResource(R.string.backup_unlock)) } },
        dismissButton = { TextButton(onClick = { if ("workspace-backup" in progress) service.environments.cancel("workspace-backup") else { restoreUri = null; restorePassword = "" } }) { Text(stringResource(R.string.cancel_label)) } })
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer) {
                Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_storage), null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    Column { Text(stringResource(R.string.backup_choose_content), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(stringResource(R.string.backup_content_summary, groups.size, if (includeEnvironments) selected.size else 0), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer) }
                }
            }
        }
        item { Text(stringResource(R.string.backup_settings_title), style = MaterialTheme.typography.titleMedium) }
        items(PortableSettings.groups.keys.toList()) { group ->
            val label = when (group) { "appearance" -> R.string.settings_appearance; "input" -> R.string.backup_input; "startup" -> R.string.backup_startup; else -> R.string.guest_defaults_title }
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                ListItem(colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent), modifier = Modifier.toggleable(group in groups, role = Role.Checkbox) { checked -> groups = if (checked) groups + group else groups - group },
                    headlineContent = { Text(stringResource(label)) }, leadingContent = { Icon(painterResource(when (group) { "appearance" -> R.drawable.ic_palette; "input" -> R.drawable.ic_keyboard; "startup" -> R.drawable.ic_shell; else -> R.drawable.ic_download }), null, tint = MaterialTheme.colorScheme.primary) }, trailingContent = { Checkbox(group in groups, null) })
            }
        }
        item { ListItem(colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), headlineContent = { Text(stringResource(R.string.backup_environments_title)) }, leadingContent = { Icon(painterResource(R.drawable.ic_environments), null, tint = MaterialTheme.colorScheme.primary) }, trailingContent = { Switch(includeEnvironments, { includeEnvironments = it }) }) }
        if (includeEnvironments) items(environments.filter { it.ready }, key = { it.id }) { environment ->
            Row(Modifier.fillMaxWidth().toggleable(environment.id in selected, role = Role.Checkbox) { checked -> selected = if (checked) selected + environment.id else selected - environment.id }, verticalAlignment = Alignment.CenterVertically) { Checkbox(environment.id in selected, null); ContextBadge(environment.distribution, small = true); Text(environment.name, Modifier.padding(start = 12.dp)) }
        }
        item {
            ListItem(colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), headlineContent = { Text(stringResource(R.string.backup_encrypt)) }, leadingContent = { Icon(painterResource(R.drawable.ic_shield), null, tint = MaterialTheme.colorScheme.primary) }, trailingContent = { Switch(encrypt, { encrypt = it }) })
            if (encrypt) OutlinedTextField(password, { if (it.length <= 1024) password = it }, label = { Text(stringResource(R.string.backup_password)) },
                supportingText = { Text(stringResource(R.string.backup_password_help)) }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())

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
                }) { Icon(painterResource(R.drawable.ic_upload), null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.backup_export)) }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(enabled = progress.isEmpty() && workspace.pendingBackup == null, onClick = {
                    try { workspace.pendingBackup = BackupRequest(groups, emptyList(), null, includeEnvironments); restore.launch(arrayOf("application/octet-stream", "*/*")) } catch (e: Exception) { result = e.message }
                }) { Icon(painterResource(R.drawable.ic_restore), null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.backup_restore)) }
            }
        }
    }
    prepared?.let { staged ->
        AlertDialog(onDismissRequest = { if (progress.isEmpty()) service.environments.closePreparedRestore() },
            title = { Text(stringResource(R.string.backup_restore_review)) },
            text = {
                LazyColumn {
                    item { Text(stringResource(R.string.backup_restore_review_help)) }
                    if ("workspace-backup" in progress) item {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(operationLabel(progress["workspace-backup"].orEmpty()), style = MaterialTheme.typography.bodySmall)
                    }
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
            }) { Icon(painterResource(R.drawable.ic_restore), null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.backup_restore)) } },
            dismissButton = { TextButton(onClick = { if ("workspace-backup" in progress) service.environments.cancel("workspace-backup") else service.environments.closePreparedRestore() }) { Text(stringResource(R.string.cancel_label)) } })
    }

}
