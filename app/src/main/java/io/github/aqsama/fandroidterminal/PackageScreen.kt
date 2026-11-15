// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.foundation.layout.*
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
internal fun PackageScreen(service: TerminalService, initialTarget: String? = null, initialQuery: String = "") {
    val environments by service.environments.environments.collectAsStateWithLifecycle()
    val snapshots by service.environments.packages.collectAsStateWithLifecycle()
    val progress by service.environments.progress.collectAsStateWithLifecycle()
    val batchBusy by service.environments.packageBatchRunning.collectAsStateWithLifecycle()
    val batchOutcomes by service.environments.packageBatchOutcomes.collectAsStateWithLifecycle()
    var batchReview by remember { mutableStateOf<Map<String, List<String>>?>(null) }
    val reviewed by service.environments.packageReview.collectAsStateWithLifecycle()
    val outputs by service.environments.packageOutput.collectAsStateWithLifecycle()
    var target by rememberSaveable { mutableStateOf(initialTarget) }
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    var selected by rememberSaveable { mutableStateOf(listOf<String>()) }
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
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.package_context_help), style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(target == null, { target = null; limit = 100 }, label = { Text(stringResource(R.string.all_environments)) })
            environments.filter { it.ready }.forEach { environment ->
                FilterChip(target == environment.id, { target = environment.id; limit = 100 }, label = { Text(environment.name) })
            }
        }
        OutlinedTextField(query, { query = it; limit = 100 }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.search_packages)) })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(enabled = selected.isNotEmpty() && progress.isEmpty() && !batchBusy, onClick = {
                val plans = selected.groupBy({ it.substringBefore('|') }, { it.substringAfter('|') })
                try { service.environments.batchPackages(plans, service.sessions, "Review"); batchReview = plans }
                catch (e: Exception) { error = e.message }
            }) { Text(stringResource(R.string.package_batch_review)) }
            TextButton(enabled = targets.isNotEmpty() && progress.isEmpty() && !batchBusy, onClick = {
                val plans = targets.associate { it.id to emptyList<String>() }
                try { service.environments.batchPackages(plans, service.sessions, "Review upgrades"); batchReview = plans }
                catch (e: Exception) { error = e.message }
            }) { Text(stringResource(R.string.package_batch_upgrade)) }
            TextButton({ editingProfile = null; importing = true }) { Text(stringResource(R.string.import_package_names)) }
            TextButton(enabled = selected.isNotEmpty(), onClick = {
                val text = selected.joinToString("\n") { key ->
                    val id = key.substringBefore('|'); val name = key.substringAfter('|')
                    val env = environments.firstOrNull { it.id == id }
                    val row = snapshots[id]?.packages?.firstOrNull { it.name == name.substringBefore(':') }
                    "$name\t${row?.installed ?: "-"} → ${row?.candidate ?: "-"}\t${row?.architecture}\t${env?.name} ${env?.distribution} ${env?.release}\t${row?.repository}"
                }
                context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Packages", text))
            }) { Text(stringResource(R.string.copy_package_selection, selected.size)) }
            TextButton(enabled = selected.isNotEmpty(), onClick = {
                val text = selected.joinToString("\n") { key ->
                    val id = key.substringBefore('|'); val name = key.substringAfter('|')
                    val environment = environments.firstOrNull { it.id == id }
                    val row = snapshots[id]?.packages?.firstOrNull { it.name == name.substringBefore(':') }
                    "$name\t${row?.installed ?: "-"} → ${row?.candidate ?: "-"}\t${row?.architecture}\t${environment?.name} ${environment?.distribution} ${environment?.release}\t${row?.repository}"
                }
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
            }) { Text(stringResource(R.string.share_label)) }
        }
        error?.let { Text(operationLabel(it), color = MaterialTheme.colorScheme.error) }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (targets.isEmpty()) item { Text(stringResource(R.string.package_no_environment)) }
            targets.forEach { environment ->
                val snapshot = snapshots[environment.id]
                val matching = snapshot?.packages.orEmpty().filter { it.name.contains(query, true) }
                item(key = environment.id) {
                    Text("${environment.name} · ${environment.distribution} ${environment.release} · PRoot", style = MaterialTheme.typography.titleMedium)
                    Text(if (snapshot == null) stringResource(R.string.package_not_loaded) else
                        stringResource(R.string.package_freshness, java.util.Date(snapshot.readAt).toString(), snapshot.refreshedAt?.let { java.util.Date(it).toString() } ?: stringResource(R.string.package_refresh_unknown)),
                        style = MaterialTheme.typography.bodySmall)
                    batchOutcomes[environment.id]?.let { Text(operationLabel(it), style = MaterialTheme.typography.bodySmall) }
                    if (environment.provisioning.isNotBlank()) {
                        Text(stringResource(R.string.provisioning_status, operationLabel(environment.provisioning), environment.packagePlan))
                        Row {
                            TextButton(enabled = progress.isEmpty() && !batchBusy, onClick = { imported = environment.packagePlan; target = environment.id; editingProfile = environment.id; importing = true }) { Text(stringResource(R.string.edit_label)) }
                            TextButton(enabled = progress.isEmpty() && !batchBusy, onClick = { try { service.environments.retryProvision(environment.id, service.sessions, environment.packagePlan) } catch (e: Exception) { error = e.message } }) { Text(stringResource(R.string.retry_label)) }
                            TextButton(enabled = progress.isEmpty() && !batchBusy, onClick = { try { service.environments.retryProvision(environment.id, service.sessions, null) } catch (e: Exception) { error = e.message } }) { Text(stringResource(R.string.profile_skip)) }
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Read" to R.string.package_read, "Refresh" to R.string.package_refresh, "Repair" to R.string.package_repair).forEach { (operation, label) ->
                            TextButton(enabled = progress.isEmpty() && !batchBusy, onClick = { action(environment.id, operation) }) { Text(stringResource(label)) }
                        }
                        TextButton(enabled = progress.isEmpty() && !batchBusy && snapshot != null, onClick = { review = environment.id to emptyList(); action(environment.id, "Review upgrades") }) { Text(stringResource(R.string.package_upgrade)) }
                        val names = selected.filter { it.startsWith(environment.id + "|") }.map { it.substringAfter('|') }
                        TextButton(enabled = progress.isEmpty() && !batchBusy && names.isNotEmpty(), onClick = { review = environment.id to names; action(environment.id, "Review", names) }) { Text(stringResource(R.string.package_review_install, names.size)) }
                    }
                    if (environment.id in progress) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        TextButton({ service.environments.cancel(environment.id) }) { Text(stringResource(R.string.cancel_label)) }
                    }
                    var showOutput by rememberSaveable(environment.id) { mutableStateOf(false) }
                    if (!outputs[environment.id].isNullOrEmpty()) {
                        TextButton({ showOutput = !showOutput }) { Text(stringResource(R.string.package_output)) }
                        if (showOutput) SelectionContainer { Text(outputs[environment.id].orEmpty().takeLast(12000), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
                    }
                    if (snapshot != null && matching.isEmpty()) Text(stringResource(R.string.package_empty))
                }
                items(matching.take(limit), key = { "${environment.id}|${it.name}|${it.architecture}" }) { row ->
                    val key = "${environment.id}|${row.name}" + if (environment.distribution != "alpine" && row.architecture != "all") ":${row.architecture}" else ""
                    ListItem(modifier = Modifier.toggleable(key in selected, role = Role.Checkbox, onValueChange = { checked -> selected = if (!checked) selected - key else if (selected.size < 100) selected + key else selected }), headlineContent = { Text(row.name) }, supportingContent = {
                        Text((if (row.updateAvailable) stringResource(R.string.package_update_available) + "\n" else "") + stringResource(R.string.package_versions, row.installed ?: "—", row.candidate ?: "—") + "\n${row.architecture} · ${row.repository}")
                    }, leadingContent = {
                        Checkbox(key in selected, null)
                    })
                }
                if (matching.size > limit) item { TextButton({ limit += 100 }) { Text(stringResource(R.string.package_more, matching.size - limit)) } }
            }
            item { Spacer(Modifier.height(24.dp)) }
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
        AlertDialog(onDismissRequest = { batchReview = null }, title = { Text(stringResource(R.string.package_batch_review)) },
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
                try { service.environments.batchPackages(plans, service.sessions, if (plans.values.all { it.isEmpty() }) "Upgrade" else "Install"); batchReview = null }
                catch (e: Exception) { error = e.message }
            }) { Text(stringResource(R.string.run_label)) } },
            dismissButton = { TextButton({ batchReview = null }) { Text(stringResource(R.string.cancel_label)) } })
    }

}
