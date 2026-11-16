// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aqsama.fandroidterminal.designsystem.ConnectedGroup
import io.github.aqsama.fandroidterminal.runtime.linux.*
import io.github.aqsama.fandroidterminal.sessions.TerminalService
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@Composable
internal fun Page(title: String, subtitle: String = "", showTitle: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.widthIn(max = 840.dp).fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (showTitle) Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge.copy(textDirection = if (title.startsWith('/')) TextDirection.Ltr else TextDirection.Content))
        if (subtitle.isNotEmpty()) Text(subtitle, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

@Composable
internal fun Prompt(title: String, initial: String = "", label: String = stringResource(R.string.name_label), message: String = "", confirm: String = stringResource(R.string.save_label), destructive: Boolean = false, codeInput: Boolean = false, dismiss: () -> Unit, action: (String) -> Unit) {
    var value by rememberSaveable(title) { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (message.isNotEmpty()) Text(message)
            if (label.isNotEmpty()) OutlinedTextField(value, { value = it }, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), textStyle = LocalTextStyle.current.copy(textDirection = if (codeInput) TextDirection.Ltr else TextDirection.Content))
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(colors = ButtonDefaults.textButtonColors(contentColor = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary), onClick = { try { action(value); dismiss() } catch (e: Exception) { error = e.message } }) { Text(confirm) } }, dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.cancel_label)) } })
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun FilesPage(service: TerminalService, id: String, environmentName: String, preferences: Preferences, draft: EditorDraft, manageSessions: () -> Unit, returnToTerminal: () -> Unit, terminal: (String, String?) -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val progress by service.environments.progress.collectAsStateWithLifecycle()
    var path by draft::directory
    var loadedDirectory by remember { mutableStateOf<String?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    var entries by remember { mutableStateOf<List<GuestFiles.Entry>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<GuestFiles.Entry?>(null) }
    var linkTarget by remember { mutableStateOf(LinkTarget.Unavailable) }
    var prompt by remember { mutableStateOf<String?>(null) }
    var editPath by draft::path
    var text by draft::text
    var baseline by draft::baseline
    var expandedPath by rememberSaveable(id) { mutableStateOf<String?>(null) }
    var editorLimitError by remember(editPath) { mutableStateOf(false) }
    var fileQuery by rememberSaveable(id) { mutableStateOf("") }
    var showHidden by rememberSaveable(id) { mutableStateOf(false) }
    var sortNewest by rememberSaveable(id) { mutableStateOf(false) }
    val visibleEntries = remember(entries, showHidden, sortNewest, fileQuery) {
        entries.filter { (showHidden || !it.name.startsWith('.')) && it.name.contains(fileQuery, true) }.let { rows ->
            if (sortNewest) rows.sortedWith(compareBy<GuestFiles.Entry> { !it.directory }.thenByDescending { it.modified }) else rows
        }
    }
    val externalScope = rememberCoroutineScope()
    val externalBrowser = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.data?.let { uri ->
            externalScope.launch {
                try {
                    val mime = result.data?.type ?: withContext(Dispatchers.IO) { context.contentResolver.getType(uri) } ?: "application/octet-stream"
                    context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION))
                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: Exception) { error = resources.getString(R.string.external_browser_unavailable) }
            }
        }
    }
    fun launchExternal(canonical: String) {
        val authority = context.packageName + ".documents"
        val document = "$id:$canonical"
        val uri = android.provider.DocumentsContract.buildDocumentUri(authority, document)
        val tree = android.provider.DocumentsContract.buildTreeDocumentUri(authority, document)
        val browsable = android.provider.DocumentsContract.buildDocumentUriUsingTree(tree, document)
        try {
            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW).setDataAndType(browsable, android.provider.DocumentsContract.Document.MIME_TYPE_DIR)
                .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_PREFIX_URI_PERMISSION))
        } catch (_: android.content.ActivityNotFoundException) {
            runCatching { externalBrowser.launch(android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(android.content.Intent.CATEGORY_OPENABLE).putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI, uri)) }
                .onFailure { error = resources.getString(R.string.external_browser_unavailable) }
        } catch (_: SecurityException) { error = resources.getString(R.string.external_browser_unavailable) }
    }
    fun openExternal() {
        val requestedPath = path
        var canonical: String? = null
        runCatching {
            service.environments.fileOperation(id, service.sessions, "Resolving external folder…", { failure ->
                if (failure != null) error = failure.message else canonical?.let(::launchExternal)
            }) { guest ->
                check(java.nio.file.Files.isDirectory(guest.resolve(requestedPath))) { "Choose an existing folder" }
                canonical = guest.canonicalPath(requestedPath)
            }
        }.onFailure { error = it.message }
    }
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
    val available = !occupied && progress.isEmpty() && loadedDirectory == path
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
                    else if (failure == null) { loadedDirectory = requestedPath; entries = result; error = null; preferences.visit(requestedPath) }
                    else { loadedDirectory = requestedPath; entries = emptyList(); error = failure.message }
                }) { result = it.list(requestedPath) }
            }.onFailure { error = it.message }
        } else if (occupied) { entries = emptyList(); error = "Close this environment’s sessions before using Files. This prevents concurrent path changes; no sessions are closed automatically." }
    }
    BackHandler(editPath == null && prompt == null && !occupied && path != "/") {
        path = path.trimEnd('/').substringBeforeLast('/', "").ifEmpty { "/" }
    }
    fun openEntry(target: String) {
        var directory = false
        var contents = ""
        work("Opening file…", { failure -> if (failure == null) {
            if (directory) path = target else { text = contents; baseline = contents; editPath = target }
        } }) { guest ->
            directory = java.nio.file.Files.isDirectory(guest.resolve(target))
            if (!directory) contents = guest.read(target)
        }
    }
    fun showFileActions(entry: GuestFiles.Entry) {
        selected = entry
        prompt = "File actions"
        linkTarget = if (entry.link) LinkTarget.Checking else LinkTarget.Unavailable
        if (entry.link) {
            val requestedDirectory = path
            val targetPath = child(entry.name)
            var result = LinkTarget.Unavailable
            work("Inspecting link…", { failure ->
                if (selected?.name == entry.name && path == requestedDirectory) linkTarget = if (failure == null) result else LinkTarget.Unavailable
            }) { guest ->
                result = runCatching {
                    val target = guest.resolve(targetPath)
                    when {
                        java.nio.file.Files.isRegularFile(target) -> LinkTarget.File
                        java.nio.file.Files.isDirectory(target) -> LinkTarget.Directory
                        else -> LinkTarget.Unavailable
                    }
                }.getOrDefault(LinkTarget.Unavailable)
            }
        }
    }
    BackHandler(editPath != null) { if (draft.dirty) prompt = "Discard edits" else editPath = null }
    // Keep the provider URI separate from the selected entry; never interpret it as a host path.
    var importUri by rememberSaveable { mutableStateOf<String?>(null) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) { importUri = uri.toString(); prompt = "Import file" } }
    var fileMenu by remember { mutableStateOf(false) }
    var createMenu by remember { mutableStateOf(false) }
    var exportPath by rememberSaveable { mutableStateOf<String?>(null) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) { val target = exportPath ?: return@rememberLauncherForActivityResult; work("Exporting file…") { guest -> context.contentResolver.openOutputStream(uri, "wt")!!.use { guest.export(target, it) } } }
    }
    if (editPath != null) {
        Page(editPath!!, environmentName + " · " + stringResource(R.string.editor_subtitle)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row { Button(enabled = available, onClick = { val target = editPath!!; val content = text; val original = baseline; work("Saving text…", { failure -> if (failure == null) { baseline = content; if (text == content) editPath = null } }) { guest -> check(guest.read(target) == original) { "File changed on disk. Your draft is retained; export or copy your edits before reloading" }; guest.write(target, content.byteInputStream(), true, 1048576) } }) { Text(stringResource(R.string.save_label)) }; TextButton(onClick = { if (draft.dirty) prompt = "Discard edits" else editPath = null }) { Text(stringResource(R.string.close_label)) }; IconButton(onClick = { expandedPath = editPath }) { Icon(painterResource(R.drawable.ic_info), stringResource(R.string.full_path)) } }
            if (occupied) {
                Text(stringResource(R.string.files_terminal_help), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = returnToTerminal) { Text(stringResource(R.string.return_to_terminal)) }
                Text(stringResource(R.string.files_blocked), style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = manageSessions) { Icon(painterResource(R.drawable.ic_shell), null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.manage_owned_sessions)) }
            } else error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (editorLimitError) Text(stringResource(R.string.editor_size_limit), color = MaterialTheme.colorScheme.error)
            OutlinedTextField(text, { editorLimitError = it.toByteArray(Charsets.UTF_8).size > 1048576; if (!editorLimitError) text = it }, isError = editorLimitError, label = { Text(stringResource(R.string.file_contents)) }, modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp, max = 480.dp), textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, textDirection = TextDirection.Ltr))
            }
        }
    } else Page(stringResource(R.string.files_label), showTitle = false) {
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            item { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(environmentName, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    if (loadedDirectory == path && error == null && !occupied) Text(stringResource(R.string.folder_item_count, visibleEntries.size), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (occupied) {
                    Text(stringResource(R.string.files_terminal_help), style = MaterialTheme.typography.bodySmall)
                    Row {
                        TextButton(returnToTerminal) { Text(stringResource(R.string.return_to_terminal)) }
                        TextButton(manageSessions) { Text(stringResource(R.string.manage_owned_sessions)) }
                    }
                } else {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                            TextButton(enabled = available, onClick = { path = "/" }) { Text("/") }
                            var crumb = ""
                            path.split('/').filter { it.isNotEmpty() }.forEach { part ->
                                crumb += "/$part"; val destination = crumb
                                Text("/", Modifier.padding(top = 14.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                TextButton(enabled = available, onClick = { path = destination }) { Text(part) }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box {
                            FilledTonalIconButton(enabled = available, onClick = { createMenu = true }) { Icon(painterResource(R.drawable.ic_add), stringResource(R.string.new_import)) }
                            DropdownMenu(createMenu, { createMenu = false }) {
                                ActionMenuItem(stringResource(R.string.new_folder), R.drawable.ic_folder_add) { createMenu = false; prompt = "New folder" }
                                ActionMenuItem(stringResource(R.string.new_text_file), R.drawable.ic_file) { createMenu = false; prompt = "New text file" }
                                ActionMenuItem(stringResource(R.string.import_file), R.drawable.ic_download) { createMenu = false; importer.launch(arrayOf("*/*")) }
                            }
                        }
                        TextButton(enabled = id !in progress && sessions.size < 12, onClick = { terminal(id, path) }, modifier = Modifier.weight(1f)) { Icon(painterResource(R.drawable.ic_shell), null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.new_terminal_here)) }
                        Box {
                            IconButton(enabled = !occupied, onClick = { fileMenu = true }) { Icon(painterResource(R.drawable.ic_more), stringResource(R.string.directory_options)) }
                            DropdownMenu(fileMenu, { fileMenu = false }) {
                                ActionMenuItem(stringResource(R.string.open_external_browser), R.drawable.ic_files, available) { fileMenu = false; openExternal() }
                                ActionMenuItem(stringResource(if (showHidden) R.string.hide_hidden_files else R.string.show_hidden_files), R.drawable.ic_file) { fileMenu = false; showHidden = !showHidden }
                                ActionMenuItem(stringResource(if (sortNewest) R.string.sort_file_name else R.string.sort_file_date), R.drawable.ic_sort) { fileMenu = false; sortNewest = !sortNewest }
                                ActionMenuItem(stringResource(R.string.go_to_folder), R.drawable.ic_files, available) { fileMenu = false; prompt = "Go to directory" }
                                ActionMenuItem(stringResource(R.string.storage_usage), R.drawable.ic_storage, available) {
                                    fileMenu = false; var bytes = 0L; var free = 0L
                                    work("Measuring storage…", { failure -> if (failure == null) usage = "${bytes / 1048576} MiB · ${free / 1048576} MiB" }) { bytes = it.usage(); free = service.filesDir.usableSpace }
                                }
                                ActionMenuItem(stringResource(R.string.refresh_label), R.drawable.ic_refresh, progress.isEmpty()) { fileMenu = false; revision++ }
                            }
                        }
                    }
                    OutlinedTextField(fileQuery, { fileQuery = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text(stringResource(R.string.search_files)) }, leadingIcon = { Icon(painterResource(R.drawable.ic_search), null) },
                        trailingIcon = if (fileQuery.isNotEmpty()) ({ IconButton({ fileQuery = "" }) { Icon(painterResource(R.drawable.ic_close), stringResource(R.string.clear_search)) } }) else null)
                    if (id in progress || loadedDirectory != path && error == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (showHidden || sortNewest) Text(stringResource(if (sortNewest) R.string.sort_file_date else R.string.show_hidden_files), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } }
            if (!occupied && loadedDirectory == path && visibleEntries.isEmpty() && progress.isEmpty() && error == null) item {
                FeatureEmptyState(R.drawable.ic_files, stringResource(if (fileQuery.isNotBlank() || entries.isNotEmpty()) R.string.files_no_matches else R.string.empty_directory), "")
            }
            items(if (loadedDirectory == path && !occupied) visibleEntries else emptyList(), key = { it.name }) { entry ->
                ListItem(leadingContent = { Icon(painterResource(if (entry.directory) R.drawable.ic_files else R.drawable.ic_file), null) }, headlineContent = { Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }, supportingContent = { Text("${if (entry.link) resources.getString(R.string.file_type_link) else if (entry.directory) resources.getString(R.string.file_type_directory) else android.text.format.Formatter.formatShortFileSize(context, entry.bytes)} · ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(entry.modified))}") },
                    trailingContent = { IconButton(onClick = { showFileActions(entry) }) { Icon(painterResource(R.drawable.ic_more), stringResource(R.string.file_actions_a11y, entry.name)) } },
                    modifier = Modifier.animateItem().combinedClickable(enabled = available, onClick = { openEntry(child(entry.name)) }, onLongClick = { showFileActions(entry) }))
            }
        }
    }
    usage?.let { value -> AlertDialog(onDismissRequest = { usage = null }, title = { Text(stringResource(R.string.storage_usage)) },
        text = { Text(stringResource(R.string.storage_usage_result, value.substringBefore(" · "), value.substringAfter(" · "))) },
        confirmButton = { TextButton({ usage = null }) { Text(stringResource(R.string.close_label)) } }) }
    expandedPath?.let { full -> AlertDialog(onDismissRequest = { expandedPath = null }, title = { Text(stringResource(R.string.full_path)) },
        text = { SelectionContainer { Text(full, Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()), style = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, textDirection = TextDirection.Ltr)) } },
        confirmButton = { TextButton(onClick = { expandedPath = null }) { Text(stringResource(R.string.close_label)) } }) }
    if (prompt == "File actions") ModalBottomSheet(onDismissRequest = { prompt = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            SheetHeader(selected!!.name, { prompt = null }, Modifier.padding(horizontal = 24.dp))
            if (selected!!.link) {
                Text(stringResource(R.string.link_actions_help), Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = MaterialTheme.typography.bodyMedium)
                if (linkTarget != LinkTarget.File) Text(stringResource(when (linkTarget) { LinkTarget.Checking -> R.string.link_checking; LinkTarget.Directory -> R.string.link_export_folder; else -> R.string.link_unavailable }), Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
            }
            SheetAction(if (selected!!.link) stringResource(R.string.follow_link) else stringResource(if (selected!!.directory) R.string.open_folder else R.string.edit_text), if (selected!!.directory) R.drawable.ic_files else R.drawable.ic_edit,
                enabled = available && (!selected!!.link || linkTarget in listOf(LinkTarget.File, LinkTarget.Directory))) {
                openEntry(child(selected!!.name)); prompt = null
            }
            SheetAction(if (selected!!.link) stringResource(R.string.move_link) else stringResource(R.string.rename_move), R.drawable.ic_edit, enabled = available) { prompt = "Rename / move" }
            SheetAction(if (selected!!.link) stringResource(R.string.copy_link) else stringResource(R.string.copy_label), R.drawable.ic_copy, enabled = available) { prompt = "Copy" }
            SheetAction(if (selected!!.link) stringResource(R.string.export_target_file) else stringResource(R.string.export_file), R.drawable.ic_upload, enabled = available && !selected!!.directory && (!selected!!.link || linkTarget == LinkTarget.File)) { exportPath = child(selected!!.name); exporter.launch(selected!!.name); prompt = null }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SheetAction(if (selected!!.link) stringResource(R.string.delete_link) else stringResource(R.string.delete_menu), R.drawable.ic_delete, enabled = available, destructive = true) { prompt = "Delete" }
        }
    }
    else prompt?.let { action ->
        val title = stringResource(when (action) {
            "Go to directory" -> R.string.go_to_folder
            "Rename / move" -> if (selected?.link == true) R.string.move_link else R.string.rename_move
            "Copy" -> if (selected?.link == true) R.string.copy_link else R.string.copy_label
            "Delete" -> if (selected?.link == true) R.string.delete_link else R.string.delete_label
            "New folder" -> R.string.new_folder
            "New text file" -> R.string.new_text_file
            "Import file" -> R.string.import_file
            else -> R.string.discard_edits
        })
        Prompt(title, initial = if (action == "Go to directory") path else if (action == "Rename / move") child(selected!!.name) else "", label = if (action in listOf("Delete", "Discard edits")) "" else if (action in listOf("Rename / move", "Copy", "Go to directory")) stringResource(R.string.absolute_guest_path) else stringResource(R.string.name_label),
            message = when (action) { "Delete" -> if (selected?.link == true) resources.getString(R.string.delete_link_details, selected!!.name) else resources.getString(R.string.file_delete_details, selected?.name); "Import file" -> resources.getString(R.string.import_replace_details); "Discard edits" -> resources.getString(R.string.discard_edits_details); else -> "" },
            confirm = stringResource(if (action == "Delete") R.string.delete_permanently else if (action == "Import file") R.string.import_replace else R.string.continue_label), destructive = action == "Delete", codeInput = action in listOf("Rename / move", "Copy", "Go to directory"), dismiss = { prompt = null }) { value ->
            when (action) {
                "Go to directory" -> { require(value.startsWith('/')) { resources.getString(R.string.absolute_guest_path_required) }; path = value }
                "New folder", "New text file", "Import file" -> {
                    require(value.isNotBlank() && '/' !in value && value !in listOf(".", "..")) { resources.getString(R.string.single_file_name_required) }
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

private enum class LinkTarget { Checking, File, Directory, Unavailable }
