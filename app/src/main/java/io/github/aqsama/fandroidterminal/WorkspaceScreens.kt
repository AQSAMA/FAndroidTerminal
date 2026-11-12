// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aqsama.fandroidterminal.designsystem.ConnectedGroup
import io.github.aqsama.fandroidterminal.runtime.linux.*
import io.github.aqsama.fandroidterminal.sessions.TerminalService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@Composable
internal fun Page(title: String, subtitle: String = "", content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().widthIn(max = 840.dp).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        if (subtitle.isNotEmpty()) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

@Composable
internal fun Prompt(title: String, initial: String = "", label: String = "Name", message: String = "", confirm: String = "Save", dismiss: () -> Unit, action: (String) -> Unit) {
    var value by remember(title) { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (message.isNotEmpty()) Text(message)
            if (label.isNotEmpty()) OutlinedTextField(value, { value = it }, label = { Text(label) }, modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(onClick = { try { action(value); dismiss() } catch (e: Exception) { error = e.message } }) { Text(confirm) } }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EnvironmentsPage(service: TerminalService, open: (String, String?) -> Unit, files: (String) -> Unit, settings: (String) -> Unit) {
    val environments by service.environments.environments.collectAsStateWithLifecycle()
    val sessions by service.sessions.sessions.collectAsStateWithLifecycle()
    val progress by service.environments.progress.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var prompt by remember { mutableStateOf<Pair<String, LinuxEnvironment?>?>(null) }
    var backupId by rememberSaveable { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) runCatching { service.environments.snapshot(backupId!!, service.sessions) { context.contentResolver.openOutputStream(uri, "wt") ?: error("Cannot open destination") } }.onFailure { error = it.message }
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching { service.environments.restore { context.contentResolver.openInputStream(uri) ?: error("Cannot read backup") } }.onFailure { error = it.message }
    }
    Page("Environments", "Independent Linux installations. Guest root runs with this app’s Android permissions.") {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Row { Button(onClick = { prompt = "Install Debian" to null }) { Text("Install Debian") }; TextButton(onClick = { restore.launch(arrayOf("*/*")) }) { Text("Restore backup") } } }
            items(environments, key = { it.id }) { environment ->
                val count = sessions.count { it.environmentId == environment.id }
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(environment.name, style = MaterialTheme.typography.titleLarge)
                        Text("Debian · $count sessions", style = MaterialTheme.typography.labelLarge)
                        Text(progress[environment.id] ?: environment.state)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (environment.ready) {
                                Button(onClick = { open(environment.id, null) }, enabled = environment.id !in progress) { Text("Terminal") }
                                OutlinedButton(onClick = { files(environment.id) }) { Text("Files") }
                                TextButton(onClick = { settings(environment.id) }) { Text("Settings") }
                            } else TextButton(onClick = { runCatching { service.environments.install(environment.id) }.onFailure { error = it.message } }, enabled = progress.isEmpty()) { Text("Retry install") }
                            TextButton(onClick = { prompt = "Rename" to environment }) { Text("Rename") }
                            TextButton(onClick = { prompt = "Clone" to environment }, enabled = environment.ready && progress.isEmpty()) { Text("Clone") }
                            TextButton(onClick = { if (count > 0) error = "Close this environment’s sessions before backup for a consistent snapshot." else { backupId = environment.id; export.launch("${environment.name}.fatbackup") } }, enabled = environment.ready && progress.isEmpty()) { Text("Backup") }
                            TextButton(onClick = { prompt = "Delete" to environment }, enabled = progress.isEmpty()) { Text("Delete") }
                        }
                    }
                }
            }
            item { Text("Backups include files, links, executable permissions and guest settings. Close all sessions of the source environment before clone or backup. Restore always creates a new instance.", style = MaterialTheme.typography.bodySmall) }
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        }
    }
    prompt?.let { (action, environment) ->
        Prompt(action, environment?.name ?: "", label = if (action in listOf("Clone", "Delete")) "" else "Environment name",
            message = when (action) { "Delete" -> "Permanently delete all files and packages in ${environment?.name}? Close its sessions first. This cannot be undone."; "Clone" -> "Create an independent copy of ${environment?.name}. Close its sessions first; no sessions will be terminated."; "Install Debian" -> "Verified 17 MiB download. Allow at least 800 MiB free storage."; else -> "" },
            confirm = action, dismiss = { prompt = null }) { value ->
            when (action) {
                "Install Debian" -> service.environments.create(value)
                "Rename" -> service.environments.rename(environment!!.id, value)
                "Clone" -> service.environments.snapshot(environment!!.id, service.sessions)
                "Delete" -> service.environments.delete(environment!!.id, service.sessions)
            }
        }
    }
}

@Composable
internal fun SettingsPage(preferences: Preferences) {
    var font by remember { mutableStateOf(preferences.font.toString()) }
    var keys by remember { mutableStateOf(preferences.keys) }
    var snippets by remember { mutableStateOf(preferences.snippets) }
    var palette by remember { mutableStateOf(preferences.palette) }
    var result by remember { mutableStateOf("") }
    Page("Settings", "Display and input changes preserve running processes.") {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Terminal", style = MaterialTheme.typography.titleLarge)
            ConnectedGroup {
                OutlinedTextField(font, { font = it }, label = { Text("Font size · 8–28 sp") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Midnight", "Paper", "Solarized").forEach { FilterChip(palette == it, { palette = it }, label = { Text(it) }) } }
            }
            Text("Extra keys", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(keys, { keys = it }, label = { Text("Keys separated by spaces") }, supportingText = { Text(Preferences.supportedKeys.joinToString(" ")) }, modifier = Modifier.fillMaxWidth())
            Text("Saved commands", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(snippets, { snippets = it }, label = { Text("One command per line") }, supportingText = { Text("Actions → command → confirm Run. Commands never run automatically.") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
            Button(onClick = { result = runCatching { preferences.save(font.toInt(), palette, keys, snippets); "Saved" }.getOrElse { it.message ?: "Enter a valid font size" } }) { Text("Save settings") }
            Text(result)
            Text("Keyboard and clipboard", style = MaterialTheme.typography.titleLarge)
            Text("Ctrl+Shift+P  Actions\nCtrl+Shift+N  New session\nCtrl+Shift+Tab  Previous session\nCtrl+Shift+V  Paste\nLong press terminal text to select and copy. Actions also offers Select visible and Copy selection. Multiline paste always asks first.")
            Text("Engine options", style = MaterialTheme.typography.titleLarge)
            Text("The bundled engine does not expose a configurable scrollback limit or cursor override through its public API. Scrollback uses the engine’s built-in limit; applications control cursor shape and blinking through terminal sequences.")
            Text("FAndroidTerminal ${BuildConfig.VERSION_NAME} · ${BuildConfig.BUILD_COMMIT.take(12)}\nGPL-3.0-only", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
internal fun GuestSettingsPage(service: TerminalService, id: String) {
    val environments by service.environments.environments.collectAsStateWithLifecycle()
    val environment = environments.firstOrNull { it.id == id } ?: return
    var dns by remember(id) { mutableStateOf(environment.dns) }
    var compatibility by remember(id) { mutableStateOf(environment.compatibility) }
    var directory by remember(id) { mutableStateOf(environment.startDirectory) }
    var shell by remember(id) { mutableStateOf(environment.shell) }
    var variables by remember(id) { mutableStateOf(environment.variables) }
    var message by remember { mutableStateOf("") }
    Page(environment.name, "Guest settings apply to new sessions. Close this environment’s sessions before saving.") {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(dns, { dns = it }, label = { Text("DNS · IPv4") })
            Row { Switch(compatibility, { compatibility = it }); Text("PRoot compatibility mode", Modifier.padding(12.dp)) }
            Text("Compatibility mode disables seccomp acceleration. Keep enabled if your phone restricts tracing.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(directory, { directory = it }, label = { Text("Starting guest directory") })
            OutlinedTextField(shell, { shell = it }, label = { Text("Guest shell") })
            OutlinedTextField(variables, { variables = it }, label = { Text("Additional variables · KEY=value per line") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            Button(onClick = { message = runCatching { service.environments.saveSettings(environment.copy(dns = dns, compatibility = compatibility, startDirectory = directory, shell = shell, variables = variables), service.sessions); "Saving… See operation status." }.getOrElse { it.message ?: "Invalid settings" } }) { Text("Save guest settings") }
            Text(message)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FilesPage(service: TerminalService, id: String, preferences: Preferences, draft: EditorDraft, terminal: (String, String?) -> Unit) {
    val context = LocalContext.current
    val progress by service.environments.progress.collectAsStateWithLifecycle()
    var path by rememberSaveable(id) { mutableStateOf("/root") }
    var revision by remember { mutableIntStateOf(0) }
    var entries by remember { mutableStateOf<List<GuestFiles.Entry>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<GuestFiles.Entry?>(null) }
    var prompt by remember { mutableStateOf<String?>(null) }
    var editPath by draft::path
    var text by draft::text
    var baseline by draft::baseline
    var usage by remember { mutableStateOf<String?>(null) }
    fun child(name: String) = path.trimEnd('/') + "/" + name
    fun work(label: String, completed: (Exception?) -> Unit = {}, action: (GuestFiles) -> Unit) {
        runCatching {
            service.environments.fileOperation(id, service.sessions, label, { failure ->
                if (failure != null) error = failure.message
                else { error = null; revision++ }
                completed(failure)
            }, action)
        }.onFailure { error = it.message; completed(it as? Exception ?: IllegalStateException(it)) }
    }
    val sessions by service.sessions.sessions.collectAsStateWithLifecycle()
    val occupied = sessions.any { it.environmentId == id }
    var pendingRefresh by remember { mutableStateOf(true) }
    LaunchedEffect(id, path, revision, occupied) { pendingRefresh = true }
    LaunchedEffect(id, path, pendingRefresh, occupied, progress.isEmpty()) {
        if (!occupied && pendingRefresh && progress.isEmpty()) {
            // Consume the request before publishing our own progress, avoiding a reload loop.
            pendingRefresh = false
            val requestedPath = path
            var result = emptyList<GuestFiles.Entry>()
            runCatching {
                service.environments.fileOperation(id, service.sessions, "Reading directory…", { failure ->
                    if (path != requestedPath) revision++
                    else if (failure == null) { entries = result; error = null; preferences.visit(requestedPath) }
                    else { entries = emptyList(); error = failure.message }
                }) { result = it.list(requestedPath) }
            }.onFailure { error = it.message }
        } else if (occupied) { entries = emptyList(); error = "Close this environment’s sessions before using Files. This prevents concurrent path changes; no sessions are closed automatically." }
    }
    fun edit(target: String) {
        var result = ""
        work("Reading text…", { failure -> if (failure == null) { text = result; baseline = result; editPath = target } }) { result = it.read(target) }
    }
    BackHandler(editPath != null) { if (draft.dirty) prompt = "Discard edits" else editPath = null }
    // Keep the provider URI separate from the selected entry; never interpret it as a host path.
    var importUri by rememberSaveable { mutableStateOf<String?>(null) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) { importUri = uri.toString(); prompt = "Import file" } }
    var exportPath by rememberSaveable { mutableStateOf<String?>(null) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) { val target = exportPath ?: return@rememberLauncherForActivityResult; work("Exporting file…") { guest -> context.contentResolver.openOutputStream(uri, "wt")!!.use { guest.export(target, it) } } }
    }
    if (editPath != null) {
        Page(editPath!!, "UTF-8 editor · maximum 1 MiB. Save replaces the file atomically.") {
            Row { Button(enabled = id !in progress, onClick = { val target = editPath!!; val content = text; val original = baseline; work("Saving text…", { failure -> if (failure == null) { baseline = content; if (text == content) editPath = null } }) { guest -> check(guest.read(target) == original) { "File changed on disk. Your draft is retained; export or copy your edits before reloading" }; guest.write(target, content.byteInputStream(), true, 1048576) } }) { Text("Save") }; TextButton(onClick = { prompt = "Discard edits" }) { Text("Close") } }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            OutlinedTextField(text, { if (it.toByteArray().size <= 1048576) text = it }, modifier = Modifier.fillMaxWidth().weight(1f), textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace))
        }
    } else Page("Files", "Close this environment’s sessions to browse safely. Links follow guest paths; copy and delete act on the link itself. Drafts are retained when navigating away.") {
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            TextButton(onClick = { path = "/" }) { Text("/") }
            var crumb = ""
            path.split('/').filter { it.isNotEmpty() }.forEach { part -> crumb += "/$part"; val destination = crumb; TextButton(onClick = { path = destination }) { Text(part) } }
        }
        FlowRow {
            TextButton(onClick = { prompt = "Go to directory" }) { Text("Go to…") }
            TextButton(onClick = { prompt = "New folder" }) { Text("New folder") }
            TextButton(onClick = { prompt = "New text file" }) { Text("New file") }
            TextButton(onClick = { importer.launch(arrayOf("*/*")) }) { Text("Import") }
            TextButton(onClick = { terminal(id, path) }) { Text("Terminal here") }
            TextButton(onClick = { work("Measuring storage…") { guest -> val bytes = guest.usage(); android.os.Handler(android.os.Looper.getMainLooper()).post { usage = "${bytes / 1048576} MiB used · ${service.filesDir.usableSpace / 1048576} MiB free" } } }) { Text("Storage") }
            TextButton(onClick = { revision++ }) { Text("Refresh") }
        }
        usage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        Row(Modifier.horizontalScroll(rememberScrollState())) { preferences.recent.lines().filter { it != path }.forEach { recent -> SuggestionChip(onClick = { path = recent }, label = { Text(recent) }) } }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        LazyColumn(Modifier.weight(1f)) {
            if (entries.isEmpty()) item { Text(if (error == null) "This directory is empty" else "Choose another directory or retry.", Modifier.padding(16.dp)) }
            items(entries, key = { it.name }) { entry ->
                ListItem(headlineContent = { Text(entry.name) }, supportingContent = { Text("${if (entry.link) "Link" else if (entry.directory) "Directory" else "${entry.bytes} bytes"} · ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(entry.modified))}") },
                    modifier = Modifier.clickable { selected = entry; prompt = "File actions" })
                HorizontalDivider()
            }
        }
    }
    if (prompt == "File actions") AlertDialog(onDismissRequest = { prompt = null }, title = { Text(selected!!.name) }, text = {
        Column {
            TextButton(onClick = { val target = child(selected!!.name); if (selected!!.directory || selected!!.link) { path = target; prompt = null } else { edit(target); prompt = null } }) { Text(if (selected!!.directory || selected!!.link) "Open / follow link" else "Edit text") }
            if (selected!!.link) TextButton(onClick = { edit(child(selected!!.name)); prompt = null }) { Text("Edit link target as text") }
            listOf("Rename / move", "Copy", "Delete").forEach { action -> TextButton(onClick = { prompt = action }) { Text(action) } }
            TextButton(onClick = { exportPath = child(selected!!.name); exporter.launch(selected!!.name); prompt = null }) { Text("Export file") }
        }
    }, confirmButton = { TextButton(onClick = { prompt = null }) { Text("Close") } })
    else prompt?.let { action ->
        Prompt(action, initial = if (action == "Go to directory") path else if (action == "Rename / move") child(selected!!.name) else "", label = if (action in listOf("Delete", "Discard edits")) "" else if (action in listOf("Rename / move", "Copy", "Go to directory")) "Absolute guest path" else "Name",
            message = when (action) { "Delete" -> "Permanently delete ${selected?.name}? Directory contents are deleted without following links. Cancellation can leave a partially deleted directory."; "Import file" -> "An existing file will be replaced only after confirming this action. Links resolve to their guest target."; "Discard edits" -> "Discard unsaved changes?"; else -> "" },
            confirm = if (action == "Delete") "Delete permanently" else if (action == "Import file") "Import / replace" else "Continue", dismiss = { prompt = null }) { value ->
            when (action) {
                "Go to directory" -> { require(value.startsWith('/')); path = value }
                "New folder", "New text file", "Import file" -> {
                    require(value.isNotBlank() && '/' !in value && value !in listOf(".", "..")) { "Enter a single file name" }
                    val target = child(value)
                    work(action) { guest -> when (action) {
                        "New folder" -> guest.mkdir(target)
                        "New text file" -> guest.write(target, "".byteInputStream())
                        else -> context.contentResolver.openInputStream(android.net.Uri.parse(importUri))!!.use { guest.write(target, it, true) }
                    } }
                }
                "Rename / move", "Copy" -> { val source = child(selected!!.name); work(action) { it.transfer(source, value, action == "Rename / move") } }
                "Delete" -> { val target = child(selected!!.name); work(action) { it.delete(target) } }
                "Discard edits" -> editPath = null
            }
        }
    }
}
