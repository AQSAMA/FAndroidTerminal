// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aqsama.fandroidterminal.sessions.TerminalService

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OpenCodeInstaller(service: TerminalService, preferences: Preferences) {
    val environments by service.environments.environments.collectAsStateWithLifecycle()
    val progress by service.environments.progress.collectAsStateWithLifecycle()
    val reviewed by service.environments.packageReview.collectAsStateWithLifecycle()
    val output by service.environments.packageOutput.collectAsStateWithLifecycle()
    val outcomes by service.environments.operationResults.collectAsStateWithLifecycle()
    var choosing by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val prerequisites = listOf("ca-certificates", "curl", "tar")
    TextButton(enabled = progress.isEmpty(), onClick = { choosing = true }) { Text(stringResource(R.string.opencode_install)) }
    target?.let { id ->
        outcomes[id]?.let { Text(operationLabel(it)) }
        if (id in progress) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            TextButton({ service.environments.cancel(id) }) { Text(stringResource(R.string.cancel_label)) }
        }
    }
    if (choosing) AlertDialog(onDismissRequest = { choosing = false }, title = { Text(stringResource(R.string.opencode_install)) },
        text = { LazyColumn {
            item { Text(stringResource(R.string.opencode_install_help)) }
            item { FlowRow { environments.filter { it.ready && it.distribution in listOf("debian", "ubuntu") }.forEach { env ->
                FilterChip(target == env.id, { target = env.id; error = null }, enabled = progress.isEmpty(), label = { Text(env.name) })
            } } }
            item { TextButton(enabled = target != null && progress.isEmpty(), onClick = {
                try { service.environments.packageAction(target!!, service.sessions, "Review", prerequisites) }
                catch (e: Exception) { error = e.message }
            }) { Text(stringResource(R.string.package_review_title, environments.firstOrNull { it.id == target }?.name.orEmpty())) } }
            item { SelectionContainer { Text(target?.let { output[it] }.orEmpty()) } }
            error?.let { item { Text(operationLabel(it), color = MaterialTheme.colorScheme.error) } }
        } },
        confirmButton = { TextButton(enabled = target != null && progress.isEmpty() && target?.let { reviewed[it] } == prerequisites, onClick = {
            try {
                service.environments.installOpenCode(target!!, service.sessions) { launcher ->
                    if (preferences.launchers.none { it.environmentId == launcher.environmentId && it.arguments == launcher.arguments }) preferences.saveLaunchers(preferences.launchers + launcher)
                }
                choosing = false
            } catch (e: Exception) { error = e.message }
        }) { Text(stringResource(R.string.install_label)) } },
        dismissButton = { TextButton({ choosing = false }) { Text(stringResource(R.string.cancel_label)) } })
}
