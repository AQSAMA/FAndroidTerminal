// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import io.github.aqsama.fandroidterminal.runtime.linux.GuestPackage
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aqsama.fandroidterminal.sessions.TerminalService
import io.github.aqsama.fandroidterminal.runtime.linux.PackageMetadata

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PackageScreen(service: TerminalService, initialTarget: String? = null, initialQuery: String = "", environmentsPage: () -> Unit, manageSessions: (String) -> Unit) {
    val sessions by service.sessions.sessions.collectAsStateWithLifecycle()
    val environments by service.environments.environments.collectAsStateWithLifecycle()
    val snapshots by service.environments.packages.collectAsStateWithLifecycle()
    val progress by service.environments.progress.collectAsStateWithLifecycle()
    val batchBusy by service.environments.packageBatchRunning.collectAsStateWithLifecycle()
    val batchOutcomes by service.environments.packageBatchOutcomes.collectAsStateWithLifecycle()
    var batchOperation by remember { mutableStateOf("Install") }
    var batchReview by remember { mutableStateOf<Map<String, List<String>>?>(null) }
    val reviewed by service.environments.packageReview.collectAsStateWithLifecycle()
    val outputs by service.environments.packageOutput.collectAsStateWithLifecycle()
    var target by rememberSaveable { mutableStateOf(initialTarget) }
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    var selected by rememberSaveable { mutableStateOf(listOf<String>()) }
    var filter by rememberSaveable { mutableStateOf("All") }
    var sort by rememberSaveable { mutableStateOf("Relevance") }
    var compact by rememberSaveable { mutableStateOf(false) }
    var details by rememberSaveable { mutableStateOf<String?>(null) }
    var limit by rememberSaveable { mutableIntStateOf(100) }
    var importing by rememberSaveable { mutableStateOf(false) }
    var editingProfile by rememberSaveable { mutableStateOf<String?>(null) }
    var imported by rememberSaveable { mutableStateOf("") }
    var review by remember { mutableStateOf<Pair<String, List<String>>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val resources = androidx.compose.ui.platform.LocalResources.current
    val targets = environments.filter { it.ready && (target == null || it.id == target) }
    fun action(id: String, operation: String, names: List<String> = emptyList()) {
        try { service.environments.packageAction(id, service.sessions, operation, names); error = null }
        catch (e: Exception) { error = e.message }
    }
    fun selectionText(): String = selected.joinToString("\n") { key ->
        val id = key.substringBefore('|'); val name = key.substringAfter('|')
        val env = environments.firstOrNull { it.id == id }
        val row = snapshots[id]?.packages?.firstOrNull { it.name == name.substringBefore(':') && (':' !in name || it.architecture == name.substringAfter(':')) }
        "$name\t${row?.installed ?: "—"} → ${row?.candidate ?: "—"}\t${row?.architecture.orEmpty()}\t${env?.name.orEmpty()} ${env?.distribution.orEmpty()} ${env?.release.orEmpty()}\t${row?.repository.orEmpty()}"
    }
    val catalogs by produceState<Map<String, List<GuestPackage>>>(emptyMap(), snapshots, query, filter, sort, target, environments) {
        if (query.isNotBlank()) kotlinx.coroutines.delay(120)
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { targets.associate { environment ->
            environment.id to io.github.aqsama.fandroidterminal.runtime.linux.PackageSearch.results(
                snapshots[environment.id]?.packages.orEmpty(), query, filter, sort)
        } }
    }
    val busy = progress.isNotEmpty() || batchBusy
    val attempted = remember { mutableStateListOf<String>() }
    LaunchedEffect(target, busy, snapshots.size, sessions.size, environments) {
        if (!busy) targets.firstOrNull { it.id !in snapshots && it.id !in attempted && sessions.none { session -> session.environmentId == it.id } }?.let {
            attempted += it.id; action(it.id, "Read")
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                var choosing by remember { mutableStateOf(false) }
                TextButton({ choosing = true }) {
                    Icon(painterResource(R.drawable.ic_environments), null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
                    Text(environments.firstOrNull { it.id == target }?.name ?: stringResource(R.string.all_environments), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Icon(painterResource(R.drawable.ic_chevron_down), null, Modifier.size(18.dp))
                }
                DropdownMenu(choosing, { choosing = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.all_environments)) }, onClick = { target = null; limit = 100; choosing = false })
                    environments.filter { it.ready }.forEach { env -> DropdownMenuItem(text = { Text(env.name) }, leadingIcon = { ContextBadge(env.distribution, small = true) }, onClick = { target = env.id; limit = 100; choosing = false }) }
                }
            }
            Box {
                var sorting by remember { mutableStateOf(false) }
                IconButton({ sorting = true }) { Icon(painterResource(R.drawable.ic_sort), stringResource(R.string.package_sort)) }
                DropdownMenu(sorting, { sorting = false }) {
                    listOf("Relevance" to R.string.package_sort_relevance, "AZ" to R.string.package_sort_az, "ZA" to R.string.package_sort_za, "Size" to R.string.package_sort_size).forEach { (value, label) ->
                        DropdownMenuItem(text = { Text(stringResource(label)) }, trailingIcon = if (sort == value) ({ Icon(painterResource(R.drawable.ic_check), null) }) else null, onClick = { sort = value; limit = 100; sorting = false })
                    }
                }
            }
            IconToggleButton(compact, { compact = it }) { Icon(painterResource(R.drawable.ic_list), stringResource(if (compact) R.string.package_compact else R.string.package_comfortable)) }
            Box {
                var menu by remember { mutableStateOf(false) }
                IconButton({ menu = true }) { Icon(painterResource(R.drawable.ic_more), stringResource(R.string.package_actions)) }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.import_package_names)) }, onClick = { editingProfile = null; importing = true; menu = false })
                    DropdownMenuItem(text = { Text(stringResource(R.string.package_batch_upgrade)) }, enabled = targets.isNotEmpty() && !busy, onClick = {
                        val plans = targets.associate { it.id to emptyList<String>() }
                        try { service.environments.batchPackages(plans, service.sessions, "Review upgrades"); batchOperation = "Upgrade"; batchReview = plans } catch (e: Exception) { error = e.message }
                        menu = false
                    })
                }
            }
        }
        OutlinedTextField(query, { query = it; sort = "Relevance"; limit = 100 }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            shape = MaterialTheme.shapes.large, label = { Text(stringResource(R.string.search_packages)) }, placeholder = { Text(stringResource(R.string.package_search_hint)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_search), null) }, trailingIcon = if (query.isNotEmpty()) ({ IconButton({ query = ""; limit = 100 }) { Icon(painterResource(R.drawable.ic_close), stringResource(R.string.clear_search)) } }) else null)
        if (query.isBlank()) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("fish", "zsh", "bash", "git", "curl", "python3").forEach { name ->
                SuggestionChip(onClick = { query = name; sort = "Relevance"; limit = 100 }, label = { Text(name) })
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("All" to R.string.package_filter_all, "Installed" to R.string.package_filter_installed, "Updates" to R.string.package_filter_updates).forEach { (value, label) ->
                FilterChip(filter == value, { filter = value; limit = 100 }, label = { Text(stringResource(label)) }, leadingIcon = if (filter == value) ({ Icon(painterResource(R.drawable.ic_check), null, Modifier.size(16.dp)) }) else null)
            }
        }
        error?.let { Text(operationLabel(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            if (targets.isEmpty()) item {
                FeatureEmptyState(R.drawable.ic_download, stringResource(R.string.package_no_environment), "")
                Button(environmentsPage, Modifier.fillMaxWidth()) { Text(stringResource(R.string.manage_environments)) }
            }
            targets.forEach { environment ->
                val snapshot = snapshots[environment.id]
                val matching = catalogs[environment.id].orEmpty()
                item(key = environment.id) {
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                ContextBadge(environment.distribution)
                                Column(Modifier.weight(1f)) {
                                    Text(environment.name, style = MaterialTheme.typography.titleSmall)
                                    Text(if (snapshot == null) stringResource(R.string.package_not_loaded) else stringResource(R.string.package_catalog_count, matching.size), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                FilledTonalIconButton(enabled = !busy, onClick = { action(environment.id, if (snapshot == null) "Read" else "Refresh") }) {
                                    Icon(painterResource(if (snapshot == null) R.drawable.ic_download else R.drawable.ic_refresh), stringResource(if (snapshot == null) R.string.package_load else R.string.package_refresh))
                                }
                                Box {
                                    var menu by remember { mutableStateOf(false) }
                                    IconButton({ menu = true }) { Icon(painterResource(R.drawable.ic_more), stringResource(R.string.package_actions)) }
                                    DropdownMenu(menu, { menu = false }) {
                                        listOf("Read" to R.string.package_read, "Refresh" to R.string.package_refresh, "Repair" to R.string.package_repair).forEach { (operation, label) ->
                                            DropdownMenuItem(text = { Text(stringResource(label)) }, enabled = !busy, onClick = { action(environment.id, operation); menu = false })
                                        }
                                        DropdownMenuItem(text = { Text(stringResource(R.string.package_upgrade)) }, enabled = !busy && snapshot != null, onClick = { review = environment.id to emptyList(); action(environment.id, "Review upgrades"); menu = false })
                                    }
                                }
                            }
                            val sessionCount = sessions.count { it.environmentId == environment.id }
                            if (sessionCount > 0) {
                                TextButton({ manageSessions(environment.id) }) { Icon(painterResource(R.drawable.ic_shell), null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.package_sessions_lock, sessionCount)) }
                            }
                            Text(stringResource(R.string.package_catalog_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            snapshot?.let { Text(stringResource(R.string.package_loaded_at, java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(it.readAt))), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            batchOutcomes[environment.id]?.let { Text(operationLabel(it), style = MaterialTheme.typography.bodySmall) }
                            if (environment.provisioning.isNotBlank()) {
                                Text(stringResource(R.string.provisioning_status, operationLabel(environment.provisioning), environment.packagePlan), style = MaterialTheme.typography.bodySmall)
                                FlowRow {
                                    TextButton(enabled = !busy, onClick = { imported = environment.packagePlan; target = environment.id; editingProfile = environment.id; importing = true }) { Text(stringResource(R.string.edit_label)) }
                                    TextButton(enabled = !busy, onClick = { try { service.environments.retryProvision(environment.id, service.sessions, environment.packagePlan) } catch (e: Exception) { error = e.message } }) { Text(stringResource(R.string.retry_label)) }
                                    TextButton(enabled = !busy, onClick = { try { service.environments.retryProvision(environment.id, service.sessions, null) } catch (e: Exception) { error = e.message } }) { Text(stringResource(R.string.profile_skip)) }
                                }
                            }
                            if (environment.id in progress) {
                                LinearProgressIndicator(Modifier.fillMaxWidth())
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(operationLabel(progress[environment.id].orEmpty()), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    TextButton({ service.environments.cancel(environment.id) }) { Text(stringResource(R.string.cancel_label)) }
                                }
                            }
                            if (!outputs[environment.id].isNullOrEmpty()) {
                                var showOutput by rememberSaveable(environment.id) { mutableStateOf(false) }
                                TextButton({ showOutput = !showOutput }) { Icon(painterResource(R.drawable.ic_terminal), null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.package_output)) }
                                AnimatedVisibility(showOutput) { SelectionContainer { Text(outputs[environment.id].orEmpty().takeLast(12000), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) } }
                            }
                        }
                    }
                    if (snapshot != null && matching.isEmpty()) FeatureEmptyState(R.drawable.ic_search, stringResource(R.string.package_empty), query)
                }
                items(matching.take(limit), key = { "${environment.id}|${it.name}|${it.architecture}" }) { row ->
                    val packageName = row.name + if (environment.distribution != "alpine" && row.architecture != "all") ":${row.architecture}" else ""
                    val key = "${environment.id}|$packageName"
                    Box(Modifier.animateItem()) { PackageCard(row, key in selected, compact, details == key, !busy,
                        select = { checked -> selected = if (!checked) selected - key else if (selected.size < 100) selected + key else selected },
                        expand = { details = if (details == key) null else key },
                        loadDetails = if (environment.distribution != "alpine" && row.candidateInstalledBytes == null && sessions.none { it.environmentId == environment.id }) ({ action(environment.id, "Details", listOf(packageName)) }) else null) }
                }
                if (matching.size > limit) item { FilledTonalButton({ limit += 100 }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.package_more, matching.size - limit)) } }
            }
        }
        AnimatedVisibility(selected.isNotEmpty()) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer) {
                Column(Modifier.padding(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton({ selected = emptyList() }) { Icon(painterResource(R.drawable.ic_close), stringResource(R.string.clear_selection)) }
                        Text(stringResource(R.string.package_selected_count, selected.size), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                        IconButton({
                            context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Packages", selectionText()))
                        }) { Icon(painterResource(R.drawable.ic_copy), stringResource(R.string.copy_label)) }
                        IconButton({ context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, selectionText()), null)) }) { Icon(painterResource(R.drawable.ic_share), stringResource(R.string.share_label)) }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Upgrade" to R.string.package_filter_updates, "Remove" to R.string.delete_menu).forEach { (operation, label) ->
                            val eligible = selected.filter { key ->
                                val name = key.substringAfter('|')
                                snapshots[key.substringBefore('|')]?.packages?.any { row -> row.name == name.substringBefore(':') && (':' !in name || row.architecture == name.substringAfter(':')) && row.installed != null && (operation != "Upgrade" || row.updateAvailable) } == true
                            }
                            OutlinedButton(enabled = !busy && eligible.isNotEmpty(), onClick = {
                                val plans = eligible.groupBy({ it.substringBefore('|') }, { it.substringAfter('|') })
                                try {
                                    service.environments.batchPackages(plans, service.sessions, if (operation == "Remove") "Review removal" else "Review upgrades")
                                    batchOperation = operation; batchReview = plans
                                } catch (e: Exception) { error = e.message }
                            }) { Text(stringResource(label)) }
                        }
                    }
                    Button(enabled = !busy, onClick = {
                        val plans = selected.groupBy({ it.substringBefore('|') }, { it.substringAfter('|') })
                        try { service.environments.batchPackages(plans, service.sessions, "Review"); batchOperation = "Install"; batchReview = plans } catch (e: Exception) { error = e.message }
                    }, modifier = Modifier.fillMaxWidth()) { Icon(painterResource(R.drawable.ic_download), null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.package_batch_review)) }
                }
            }
        }
    }
    if (importing) AlertDialog(onDismissRequest = { importing = false }, title = { Text(stringResource(R.string.import_package_names)) },
        text = { Column { Text(stringResource(R.string.package_import_help)); OutlinedTextField(imported, { if (it.length <= 16384) imported = it }, label = { Text(stringResource(R.string.import_package_names)) }) } },
        confirmButton = { TextButton(onClick = {
            try {
                val id = target ?: error(resources.getString(R.string.package_choose_target))
                val names = PackageMetadata.importNames(imported)
                if (editingProfile == id) service.environments.retryProvision(id, service.sessions, names.joinToString(" "))
                else selected = names.map { "$id|$it" }
                importing = false; error = null; editingProfile = null
            } catch (e: Exception) { error = e.message; importing = false }
        }) { Text(stringResource(R.string.save_label)) } }, dismissButton = { TextButton({ importing = false }) { Text(stringResource(R.string.cancel_label)) } })
    review?.let { (id, names) ->
        AlertDialog(onDismissRequest = { review = null }, title = { Text(stringResource(R.string.package_review_title, environments.firstOrNull { it.id == id }?.name ?: id)) },
            text = { LazyColumn { item { Text(stringResource(R.string.package_operation_warning)); SelectionContainer { Text(outputs[id].orEmpty(), fontFamily = FontFamily.Monospace) } } } },
            confirmButton = { TextButton(enabled = progress.isEmpty() && !batchBusy && reviewed[id] == names, onClick = {
                action(id, if (names.isEmpty()) "Upgrade" else "Install", names); review = null
            }) { Text(stringResource(R.string.run_label)) } }, dismissButton = { TextButton({ review = null }) { Text(stringResource(R.string.cancel_label)) } })
    }
    batchReview?.let { plans ->
        AlertDialog(onDismissRequest = { batchReview = null }, title = { Text(stringResource(when (batchOperation) { "Remove" -> R.string.package_remove_review; "Upgrade" -> R.string.package_batch_upgrade; else -> R.string.package_batch_review })) },
            text = { LazyColumn {
                item { Text(stringResource(R.string.package_operation_warning)) }
                plans.forEach { (id, names) -> item {
                    Text(environments.firstOrNull { it.id == id }?.name ?: id, style = MaterialTheme.typography.titleMedium)
                    Text(names.joinToString(" "))
                    Text(operationLabel(batchOutcomes[id].orEmpty()))
                    SelectionContainer { Text(outputs[id].orEmpty(), fontFamily = FontFamily.Monospace) }
                } }
            } },
            confirmButton = { TextButton(enabled = !batchBusy && progress.isEmpty() && plans.all { (id, names) -> reviewed[id] == names }, onClick = {
                try { service.environments.batchPackages(plans, service.sessions, batchOperation); batchReview = null }
                catch (e: Exception) { error = e.message }
            }) { Text(stringResource(R.string.run_label)) } },
            dismissButton = { TextButton({ batchReview = null }) { Text(stringResource(R.string.cancel_label)) } })
    }

}

@Composable
internal fun PackageCard(row: GuestPackage, selected: Boolean, compact: Boolean, expanded: Boolean, canLoad: Boolean,
    select: (Boolean) -> Unit, expand: () -> Unit, loadDetails: (() -> Unit)? = null) {
    val status = when { row.updateAvailable -> R.string.package_update_available; row.installed != null -> R.string.package_filter_installed; else -> R.string.package_available }
    val tint = when { row.updateAvailable -> MaterialTheme.colorScheme.tertiary; row.installed != null -> MaterialTheme.colorScheme.primary; else -> MaterialTheme.colorScheme.onSurfaceVariant }
    Surface(shape = MaterialTheme.shapes.medium, color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.animateContentSize()) {
            Row(Modifier.fillMaxWidth().clickable(onClick = expand).padding(end = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(selected, select, modifier = Modifier.semantics { contentDescription = row.name })
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(row.name, style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Monospace, textDirection = TextDirection.Ltr), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (!compact) Text(row.description.ifBlank { row.candidate ?: row.installed ?: "—" }.substringBefore('\n'), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(if (row.updateAvailable) R.drawable.ic_refresh else if (row.installed != null) R.drawable.ic_check else R.drawable.ic_download), null, Modifier.size(14.dp), tint = tint)
                        Text(stringResource(status), color = tint, style = MaterialTheme.typography.labelSmall)
                        if (!compact) Text(row.architecture, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                row.installedBytes?.let { Text(android.text.format.Formatter.formatShortFileSize(LocalContext.current, it), Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
                Icon(painterResource(if (expanded) R.drawable.ic_chevron_up else R.drawable.ic_chevron_down), null, Modifier.padding(start = 6.dp).size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AnimatedVisibility(expanded) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HorizontalDivider()
                    loadDetails?.let { load -> TextButton(enabled = canLoad, onClick = load) { Icon(painterResource(R.drawable.ic_download), null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.package_load_details)) } }
                    SelectionContainer { Text(row.description.ifBlank { stringResource(R.string.package_description_missing) }, style = MaterialTheme.typography.bodyMedium) }
                    Text(stringResource(R.string.package_versions, row.installed ?: "—", row.candidate ?: "—"), style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr))
                    Text("${row.architecture} · ${row.repository}", style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.package_install_size) + ": " + (row.installedBytes?.let { android.text.format.Formatter.formatShortFileSize(LocalContext.current, it) } ?: stringResource(R.string.package_size_unknown)), style = MaterialTheme.typography.labelMedium)
                    row.candidateInstalledBytes?.let { Text(stringResource(R.string.package_candidate_size) + ": " + android.text.format.Formatter.formatShortFileSize(LocalContext.current, it), style = MaterialTheme.typography.labelMedium) }
                    row.downloadBytes?.let { Text(stringResource(R.string.package_download_size) + ": " + android.text.format.Formatter.formatShortFileSize(LocalContext.current, it), style = MaterialTheme.typography.labelMedium) }
                }
            }
        }
    }
}
