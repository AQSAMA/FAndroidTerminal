// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import io.github.aqsama.fandroidterminal.runtime.linux.LinuxImages
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.foundation.lazy.LazyListState
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
    EnvironmentList(environments, sessions.groupingBy { it.environmentId }.eachCount(), sessions.count { it.archived }, progress,
        service.environments.supported, list, error, blockedId, { error = null }, { install = true }, { prompt = "Restore info" to null },
        { restoreDetails = true }, { open(it, null) }, files, settings, manageSessions,
        { environment, command -> if (command == "Retry") runCatching { service.environments.install(environment.id) }.onFailure { error = it.message } else action(environment, command) },
        { service.environments.cancel(it) })
    if (restoreDetails && progress.isNotEmpty()) AlertDialog(onDismissRequest = { restoreDetails = false },
        title = { Text(stringResource(R.string.operation_details)) },
        text = { Text(operationLabel(progress.values.first()), Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = { restoreDetails = false }) { Text(stringResource(R.string.close_label)) } })
    if (install) InstallEnvironmentDialog("Debian ${environments.size + 1}", { install = false }) { name, distribution -> service.environments.create(name, distribution) }
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

/** Same native list for service-backed screens and host inspection; no session objects are created here. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun EnvironmentList(environments: List<LinuxEnvironment>, sessionCounts: Map<String?, Int>, archivedCount: Int,
    progress: Map<String, String>, supported: Boolean, list: LazyListState = rememberLazyListState(),
    error: String? = null, blockedId: String? = null, dismissError: () -> Unit, install: () -> Unit, restore: () -> Unit,
    restoreDetails: () -> Unit, open: (String?) -> Unit, files: (String) -> Unit, settings: (String) -> Unit,
    manageSessions: (String) -> Unit, action: (LinuxEnvironment, String) -> Unit, cancel: (String) -> Unit) {
    val active = environments.firstOrNull { it.id in progress }
    Page(stringResource(R.string.environments_label), showTitle = false) {
        LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            error?.let { message -> item {
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.errorContainer) {
                    Column(Modifier.padding(16.dp)) {
                        Text(message)
                        Row {
                            TextButton(onClick = { dismissError() }) { Text(stringResource(R.string.dismiss_error)) }
                            blockedId?.let { id -> TextButton(onClick = { manageSessions(id) }) { Text(stringResource(R.string.review_sessions)) } }
                        }
                    }
                }
            } }
            if (active != null) stickyHeader(key = "active-operation") {
                EnvironmentCard(active, sessionCounts[active.id] ?: 0, progress[active.id], true,
                    { open(active.id) }, { files(active.id) }, { settings(active.id) }, { action(active, it) }, { cancel(active.id) }, reviewSessions = { manageSessions(active.id) })
            }
            if (progress.isNotEmpty() && active == null) stickyHeader(key = "restore-operation") {
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.env_restore), style = MaterialTheme.typography.titleMedium)
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        OperationSummary(progress.values.first(), { restoreDetails() }) { cancel(progress.keys.first()) }
                    }
                }
            }
            item(key = "device-shell") {
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                ListItem(colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    headlineContent = { Text(stringResource(R.string.android_shell)) },
                    supportingContent = { Text(stringResource(R.string.device_shell_summary)) },
                    leadingContent = { ContextBadge(null) },
                    trailingContent = { IconButton(onClick = { open(null) }, enabled = sessionCounts.values.sum() < 12) { Icon(painterResource(R.drawable.ic_add), stringResource(R.string.new_android_shell)) } },
                    modifier = Modifier.fillMaxWidth())
                }
            }
            if (environments.isEmpty() && progress.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(top = 32.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(painterResource(R.drawable.ic_environments), null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.environments_welcome), style = MaterialTheme.typography.headlineSmall)
                    Text(stringResource(R.string.environments_welcome_help), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(enabled = supported, onClick = { install() }) { Icon(painterResource(R.drawable.ic_add), null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.install_linux)) }
                    TextButton(onClick = { restore() }) { Icon(painterResource(R.drawable.ic_restore), null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.env_restore)) }
                }
            } else item {
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.linux_environments), Modifier.heightIn(min = 48.dp).wrapContentHeight(Alignment.CenterVertically), style = MaterialTheme.typography.titleSmall)
                    FlowRow {
                        TextButton(enabled = supported && progress.isEmpty(), onClick = install) { Icon(painterResource(R.drawable.ic_add), null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.install_linux)) }
                        TextButton(enabled = progress.isEmpty(), onClick = restore) { Icon(painterResource(R.drawable.ic_restore), null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.env_restore)) }
                    }
                }
            }
            if (!supported) item { Text(stringResource(R.string.env_arm64), style = MaterialTheme.typography.bodySmall) }
            if (sessionCounts.values.sum() >= 12) item { Text(stringResource(R.string.session_limit_hint, archivedCount), style = MaterialTheme.typography.bodySmall) }
            items(environments.filter { it.id != active?.id }, key = { it.id }) { environment ->
                EnvironmentCard(environment, sessionCounts[environment.id] ?: 0, null, progress.isNotEmpty(),
                    { open(environment.id) }, { files(environment.id) }, { settings(environment.id) },
                    { command -> action(environment, command) }, {}, atSessionLimit = sessionCounts.values.sum() >= 12, reviewSessions = { manageSessions(environment.id) })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EnvironmentCard(environment: LinuxEnvironment, count: Int, stage: String?, managementBusy: Boolean,
    open: () -> Unit, files: () -> Unit, settings: () -> Unit, action: (String) -> Unit, cancel: () -> Unit, atSessionLimit: Boolean = false, reviewSessions: () -> Unit = files) {
    var menu by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf(false) }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ContextBadge(environment.distribution)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(environment.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${LinuxImages.get(environment.distribution).name} ${environment.release} · ${LinuxImages.get(environment.distribution).packageManager}", style = MaterialTheme.typography.labelMedium, color = contextColor(environment.distribution))
                    Text(stringResource(R.string.session_count_short, count), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box {
                    IconButton(onClick = { menu = true }, enabled = !managementBusy) { Icon(painterResource(R.drawable.ic_more), stringResource(R.string.env_manage, environment.name)) }
                    EnvironmentActionMenu(menu, { menu = false }, environment.ready) { command -> menu = false; action(command) }
                }
            }
            Spacer(Modifier.height(8.dp))
            when {
                stage != null -> {
                    val fraction = stage.takeIf { it.startsWith("Downloading ") && ": " in it && it.endsWith("%") }?.substringAfter(": ")?.removeSuffix("%")?.toFloatOrNull()?.div(100f)
                    if (fraction == null) LinearProgressIndicator(Modifier.fillMaxWidth()) else LinearProgressIndicator(progress = { fraction.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    OperationSummary(stage, { details = true }, cancel)
                }
                environment.ready -> {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = if (count > 0) reviewSessions else open) { Icon(painterResource(R.drawable.ic_shell), null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(if (count > 0) R.string.view_environment_sessions else if (atSessionLimit) R.string.close_session_first else R.string.open_terminal)) }
                        if (count > 0) TextButton(onClick = open, enabled = !atSessionLimit) { Icon(painterResource(R.drawable.ic_add), null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.new_session)) }
                    }
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = files) { Icon(painterResource(R.drawable.ic_files), null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.files_label)) }
                        TextButton(onClick = settings) { Icon(painterResource(R.drawable.ic_settings), null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.settings_label)) }
                    }
                    if (count > 0) Text(stringResource(R.string.env_files_lock, count), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> {
                    Surface(shape = MaterialTheme.shapes.medium, color = if (environment.state.startsWith("Cancelled") || environment.state.startsWith("Interrupted")) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.errorContainer) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(if (environment.state.startsWith("Cancelled") || environment.state.startsWith("Interrupted")) R.string.installation_paused else R.string.environment_needs_attention), style = MaterialTheme.typography.titleSmall)
                            Text(operationLabel(environment.state), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Row {
                        FilledTonalButton(onClick = { action(if (environment.state.startsWith("Delet")) "Delete" else "Retry") }, enabled = !managementBusy) { Icon(painterResource(R.drawable.ic_refresh), null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(if (environment.state.startsWith("Delet")) R.string.retry_delete else R.string.env_retry)) }
                        TextButton(onClick = { details = true }) { Text(stringResource(R.string.operation_details)) }
                    }
                }
            }
        }
    }
    if (details) AlertDialog(onDismissRequest = { details = false }, title = { Text(environment.name) }, text = { Text(operationLabel(stage ?: environment.state), Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) }, confirmButton = { TextButton(onClick = { details = false }) { Text(stringResource(R.string.close_label)) } })
}

@Composable
internal fun EnvironmentActionMenu(expanded: Boolean, dismiss: () -> Unit, ready: Boolean, action: (String) -> Unit) {
    DropdownMenu(expanded, dismiss) { EnvironmentMenuItems(ready, action) }
}

@Composable
internal fun ColumnScope.EnvironmentMenuItems(ready: Boolean, action: (String) -> Unit) {
    ActionMenuItem(stringResource(R.string.rename_label), R.drawable.ic_edit) { action("Rename") }
    ActionMenuItem(stringResource(R.string.env_clone), R.drawable.ic_copy, enabled = ready) { action("Clone") }
    ActionMenuItem(stringResource(R.string.env_export), R.drawable.ic_upload, enabled = ready) { action("Backup") }
    HorizontalDivider()
    ActionMenuItem(stringResource(R.string.env_delete), R.drawable.ic_delete, destructive = true) { action("Delete") }
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

/** Select one verified ARM64 image; every instance retains its distribution identity. */
@Composable
internal fun InstallEnvironmentDialog(initialName: String, dismiss: () -> Unit, install: (String, String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var automaticName by rememberSaveable { mutableStateOf(true) }
    var distribution by rememberSaveable { mutableStateOf("debian") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val image = LinuxImages.get(distribution)
    val context = LocalContext.current
    val defaults = remember { io.github.aqsama.fandroidterminal.sessions.GuestDefaults(context) }
    AlertDialog(onDismissRequest = dismiss,
        title = { Text(stringResource(R.string.install_linux)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LinuxImages.all.forEach { entry ->
                    val selected = distribution == entry.distribution
                    Surface(shape = RoundedCornerShape(20.dp), color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null,
                        modifier = Modifier.fillMaxWidth().clickable {
                            distribution = entry.distribution; error = null
                            if (automaticName) name = "${entry.name} ${initialName.substringAfterLast(' ')}"
                        }) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ContextBadge(entry.distribution, small = true)
                            Column(Modifier.weight(1f)) {
                                Text(entry.name, style = MaterialTheme.typography.titleMedium)
                                Text("${entry.version} · ARM64 · ${entry.packageManager}", style = MaterialTheme.typography.bodySmall)
                                Text(stringResource(R.string.download_mebibytes, (entry.bytes + 1048575) / 1048576), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            RadioButton(selected, onClick = null)
                        }
                    }
                }
                OutlinedTextField(name, { name = it; automaticName = false; error = null }, singleLine = true, shape = RoundedCornerShape(16.dp), label = { Text(stringResource(R.string.env_name)) },
                    isError = error != null, supportingText = if (error != null) ({ Text(error.orEmpty()) }) else null, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.install_storage_required, image.minimumFreeMiB), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.install_package_plan, defaults.profile(distribution).ifBlank { "—" }), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.env_install_context), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { Button(onClick = {
            try { install(name, distribution); dismiss() } catch (e: Exception) { error = e.message }
        }, enabled = name.isNotBlank()) { Text(stringResource(R.string.install_named_distribution, image.name)) } },
        dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.cancel_label)) } })
}
