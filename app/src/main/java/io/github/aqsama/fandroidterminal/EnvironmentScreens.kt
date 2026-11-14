// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aqsama.fandroidterminal.runtime.linux.LinuxEnvironment
import io.github.aqsama.fandroidterminal.runtime.linux.DebianImage
import io.github.aqsama.fandroidterminal.sessions.TerminalService

/** Persistent instances and their operations live here; the terminal only links to active work. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EnvironmentsPage(service: TerminalService, open: (String?, String?) -> Unit, files: (String) -> Unit,
    settings: (String) -> Unit, manageSessions: (String) -> Unit) {
    val environments by service.environments.environments.collectAsStateWithLifecycle()
    val sessions by service.sessions.sessions.collectAsStateWithLifecycle()
    val progress by service.environments.progress.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val list = rememberLazyListState()
    var install by rememberSaveable { mutableStateOf(false) }
    var prompt by remember { mutableStateOf<Pair<String, LinuxEnvironment?>?>(null) }
    var backupId by rememberSaveable { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var restoreDetails by remember { mutableStateOf(false) }
    var blockedId by remember { mutableStateOf<String?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val id = backupId
        if (uri != null && id != null) runCatching {
            service.environments.snapshot(id, service.sessions) { context.contentResolver.openOutputStream(uri, "wt") ?: error("Cannot open destination") }
        }.onFailure { error = it.message }
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching { service.environments.restore { context.contentResolver.openInputStream(uri) ?: error("Cannot read backup") } }.onFailure { error = it.message }
    }
    LaunchedEffect(progress.keys) { restoreDetails = false }
    LaunchedEffect(progress.keys, error) { if (progress.isNotEmpty() || error != null) list.animateScrollToItem(0) }
    LaunchedEffect(progress.isNotEmpty(), sessions.size) {
        if (progress.isNotEmpty() || blockedId?.let { id -> sessions.none { it.environmentId == id } } == true) { error = null; blockedId = null }
    }
    fun action(environment: LinuxEnvironment, command: String) {
        error = null; blockedId = null
        when (command) {
            "Backup" -> if (sessions.any { it.environmentId == environment.id }) { blockedId = environment.id; error = resources.getString(R.string.env_backup_lock) }
                else { backupId = environment.id; export.launch("${environment.name}.fatbackup") }
            else -> prompt = command to environment
        }
    }
    val active = environments.firstOrNull { it.id in progress }
    Page(stringResource(R.string.environments_label), showTitle = false) {
        LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            error?.let { message -> item {
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.errorContainer) {
                    Column(Modifier.padding(16.dp)) {
                        Text(message)
                        Row {
                            TextButton(onClick = { error = null }) { Text(stringResource(R.string.dismiss_error)) }
                            blockedId?.let { id -> TextButton(onClick = { manageSessions(id) }) { Text(stringResource(R.string.review_sessions)) } }
                        }
                    }
                }
            } }
            if (active != null) stickyHeader(key = "active-operation") {
                EnvironmentCard(active, sessions.count { it.environmentId == active.id }, progress[active.id], true,
                    { open(active.id, null) }, { files(active.id) }, { settings(active.id) }, { action(active, it) }, { service.environments.cancel(active.id) }, reviewSessions = { manageSessions(active.id) })
            }
            if (progress.isNotEmpty() && active == null) stickyHeader(key = "restore-operation") {
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.env_restore), style = MaterialTheme.typography.titleMedium)
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        OperationSummary(progress.values.first(), { restoreDetails = true }) { service.environments.cancel(progress.keys.first()) }
                    }
                }
            }
            item(key = "device-shell") {
                ListItem(colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    headlineContent = { Text(stringResource(R.string.android_shell)) },
                    supportingContent = { Text(stringResource(R.string.device_shell_summary)) },
                    leadingContent = { Icon(painterResource(R.drawable.ic_terminal), null) },
                    trailingContent = { IconButton(onClick = { open(null, null) }, enabled = sessions.size < 12) { Icon(painterResource(R.drawable.ic_add), stringResource(R.string.new_android_shell)) } },
                    modifier = Modifier.fillMaxWidth())
            }
            if (environments.isEmpty() && progress.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(top = 32.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(painterResource(R.drawable.ic_environments), null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.environments_welcome), style = MaterialTheme.typography.headlineSmall)
                    Text(stringResource(R.string.environments_welcome_help), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(enabled = service.environments.supported, onClick = { install = true }) { Icon(painterResource(R.drawable.ic_add), null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.install_debian)) }
                    TextButton(onClick = { run { prompt = "Restore info" to null } }) { Icon(painterResource(R.drawable.ic_restore), null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.env_restore)) }
                    Text(stringResource(R.string.shell_permissions), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.linux_environments), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    TextButton(enabled = service.environments.supported && progress.isEmpty(), onClick = { install = true }) { Icon(painterResource(R.drawable.ic_add), null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.install_debian)) }
                    IconButton(enabled = progress.isEmpty(), onClick = { run { prompt = "Restore info" to null } }) { Icon(painterResource(R.drawable.ic_restore), stringResource(R.string.env_restore)) }
                }
            }
            if (!service.environments.supported) item { Text(stringResource(R.string.env_arm64), style = MaterialTheme.typography.bodySmall) }
            if (sessions.size >= 12) item { Text(stringResource(R.string.session_limit_hint, sessions.count { it.archived }), style = MaterialTheme.typography.bodySmall) }
            items(environments.filter { it.id != active?.id }, key = { it.id }) { environment ->
                EnvironmentCard(environment, sessions.count { it.environmentId == environment.id }, null, progress.isNotEmpty(),
                    { open(environment.id, null) }, { files(environment.id) }, { settings(environment.id) },
                    { command -> if (command == "Retry") runCatching { service.environments.install(environment.id) }.onFailure { error = it.message } else action(environment, command) }, {}, atSessionLimit = sessions.size >= 12, reviewSessions = { manageSessions(environment.id) })
            }
        }
    }
    if (restoreDetails && progress.isNotEmpty()) AlertDialog(onDismissRequest = { restoreDetails = false },
        title = { Text(stringResource(R.string.operation_details)) },
        text = { Text(operationLabel(progress.values.first()), Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = { restoreDetails = false }) { Text(stringResource(R.string.close_label)) } })
    if (install) InstallEnvironmentDialog("Debian ${environments.size + 1}", { install = false }) { service.environments.create(it) }
    prompt?.let { (command, environment) ->
        val label = stringResource(when (command) { "Rename" -> R.string.rename_label; "Clone" -> R.string.env_clone; "Restore info" -> R.string.env_restore; else -> R.string.env_delete })
        Prompt(label, environment?.name ?: "", label = if (command in listOf("Clone", "Delete", "Restore info")) "" else stringResource(R.string.env_name),
            message = when (command) { "Delete" -> stringResource(R.string.env_delete_confirm, environment?.name.orEmpty()); "Clone" -> stringResource(R.string.env_clone_confirm, environment?.name.orEmpty()); "Restore info" -> stringResource(R.string.restore_backup_help); else -> "" },
            confirm = if (command == "Restore info") stringResource(R.string.choose_backup) else label, destructive = command == "Delete", dismiss = { prompt = null }) { value -> when (command) {
                "Rename" -> service.environments.rename(environment!!.id, value)
                "Clone" -> service.environments.snapshot(environment!!.id, service.sessions)
                "Delete" -> service.environments.delete(environment!!.id, service.sessions)
                "Restore info" -> restore.launch(arrayOf("*/*"))
            } }
    }
}

@Composable
internal fun EnvironmentCard(environment: LinuxEnvironment, count: Int, stage: String?, managementBusy: Boolean,
    open: () -> Unit, files: () -> Unit, settings: () -> Unit, action: (String) -> Unit, cancel: () -> Unit, atSessionLimit: Boolean = false, reviewSessions: () -> Unit = files) {
    var menu by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf(false) }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_environments), null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(environment.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(if (environment.ready) stringResource(R.string.env_session_count, count) else "Debian", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box {
                    IconButton(onClick = { menu = true }, enabled = !managementBusy) { Icon(painterResource(R.drawable.ic_more), stringResource(R.string.env_manage, environment.name)) }
                    DropdownMenu(menu, { menu = false }) {
                        ActionMenuItem(stringResource(R.string.rename_label), R.drawable.ic_edit) { menu = false; action("Rename") }
                        ActionMenuItem(stringResource(R.string.env_clone), R.drawable.ic_copy, enabled = environment.ready) { menu = false; action("Clone") }
                        ActionMenuItem(stringResource(R.string.env_export), R.drawable.ic_upload, enabled = environment.ready) { menu = false; action("Backup") }
                        HorizontalDivider()
                        ActionMenuItem(stringResource(R.string.env_delete), R.drawable.ic_delete, destructive = true) { menu = false; action("Delete") }
                    }
                }
            }
            when {
                stage != null -> {
                    val fraction = stage.takeIf { it.startsWith("Downloading Debian: ") }?.substringAfter(": ")?.removeSuffix("%")?.toFloatOrNull()?.div(100f)
                    if (fraction == null) LinearProgressIndicator(Modifier.fillMaxWidth()) else LinearProgressIndicator(progress = { fraction.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    OperationSummary(stage, { details = true }, cancel)
                }
                environment.ready -> {
                    Button(onClick = open) { Icon(painterResource(R.drawable.ic_terminal), null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(if (atSessionLimit) R.string.close_session_first else if (count == 0) R.string.open_terminal else R.string.new_session)) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = if (count > 0) reviewSessions else files) { Icon(painterResource(if (count > 0) R.drawable.ic_terminal else R.drawable.ic_files), null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(if (count > 0) R.string.review_sessions else R.string.files_label)) }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = settings) { Icon(painterResource(R.drawable.ic_settings), null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.settings_label)) }
                    }
                    if (count > 0) Text(stringResource(R.string.env_card_lock), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> {
                    Text(operationLabel(environment.state), maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                    Row {
                        TextButton(onClick = { action(if (environment.state.startsWith("Delet")) "Delete" else "Retry") }, enabled = !managementBusy) { Icon(painterResource(R.drawable.ic_refresh), null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(if (environment.state.startsWith("Delet")) R.string.retry_delete else R.string.env_retry)) }
                        TextButton(onClick = { details = true }) { Text(stringResource(R.string.operation_details)) }
                    }
                }
            }
        }
    }
    if (details) AlertDialog(onDismissRequest = { details = false }, title = { Text(environment.name) }, text = { Text(operationLabel(stage ?: environment.state), Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) }, confirmButton = { TextButton(onClick = { details = false }) { Text(stringResource(R.string.close_label)) } })
}

/** Bounded stage text cannot push cancellation off the pinned operation surface. */
@Composable
private fun OperationSummary(stage: String, details: () -> Unit, cancel: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(operationLabel(stage), Modifier.weight(1f).padding(top = 12.dp), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
        IconButton(onClick = details) { Icon(painterResource(R.drawable.ic_info), stringResource(R.string.operation_details), Modifier.size(20.dp)) }
        TextButton(onClick = cancel) { Text(stringResource(R.string.cancel_label)) }
    }
}

/** The installed catalog has one pinned image; disclose it without a pretend distro picker. */
@Composable
internal fun InstallEnvironmentDialog(initialName: String, dismiss: () -> Unit, install: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = dismiss,
        title = { Text(stringResource(R.string.install_debian)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.debian_image_summary), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.env_image_download, (DebianImage.bytes + 1_048_575) / 1_048_576), style = MaterialTheme.typography.bodyMedium)
                }
                OutlinedTextField(name, { name = it; error = null }, singleLine = true, label = { Text(stringResource(R.string.env_name)) },
                    isError = error != null, supportingText = if (error != null) ({ Text(error.orEmpty()) }) else null, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.env_install_context), style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = { Button(onClick = {
            try { install(name); dismiss() } catch (e: Exception) { error = e.message }
        }, enabled = name.isNotBlank()) { Text(stringResource(R.string.install_debian)) } },
        dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.cancel_label)) } })
}
