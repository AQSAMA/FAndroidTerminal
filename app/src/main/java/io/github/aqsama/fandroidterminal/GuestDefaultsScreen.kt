// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import io.github.aqsama.fandroidterminal.designsystem.ConnectedGroup
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aqsama.fandroidterminal.sessions.GuestDefaults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GuestDefaultsScreen(defaults: GuestDefaults, packages: () -> Unit = {}) {
    var target by rememberSaveable { mutableStateOf("global") }
    val drafts = rememberSaveableStateHolder()
    drafts.SaveableStateProvider(target) {
    var profile by rememberSaveable(target) { mutableStateOf(defaults.rawProfile(target).orEmpty()) }
    var inherit by rememberSaveable(target) { mutableStateOf(target != "global" && defaults.rawProfile(target) == null) }
    var shell by rememberSaveable(target) { mutableStateOf(defaults.rawShell(target).orEmpty()) }
    var directory by rememberSaveable(target) { mutableStateOf(defaults.rawDirectory(target).orEmpty()) }
    var result by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

        Text(stringResource(R.string.new_environment_defaults), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        FilledTonalButton(packages) { Icon(painterResource(R.drawable.ic_download), null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.manage_installed_packages)) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("global", "debian", "ubuntu", "alpine").forEach {
                FilterChip(it == target, { target = it; result = null }, enabled = !saving, label = { Text(if (it == "global") stringResource(R.string.all_environments) else it.replaceFirstChar(Char::titlecase)) }, leadingIcon = { if (it != "global") ContextBadge(it, small = true) })
            }
        }
        if (target != "global") Row {
            Checkbox(inherit, { inherit = it }); Text(stringResource(R.string.inherit_profile))
        }
        OutlinedTextField(profile, { if (it.length <= 16384) profile = it }, enabled = !inherit, label = { Text(stringResource(R.string.profile_packages)) }, modifier = Modifier.fillMaxWidth())
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton({ profile = "git curl ca-certificates"; inherit = false }) { Text(stringResource(R.string.profile_essentials)) }
            FilledTonalButton({ profile = if (target == "alpine") "git curl python3 py3-pip build-base" else "git curl python3 python3-venv build-essential"; inherit = false }) { Text(stringResource(R.string.profile_development)) }
        }
        Text(stringResource(R.string.defaults_scope_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(shell, { shell = it }, placeholder = { Text(defaults.shell(target) ?: if (target == "alpine") "/bin/sh" else "/bin/bash") }, supportingText = { Text(stringResource(R.string.inherit_empty_help)) }, label = { Text(stringResource(R.string.default_guest_shell)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(directory, { directory = it }, placeholder = { Text(defaults.directory(target)) }, supportingText = { Text(stringResource(R.string.inherit_empty_help)) }, label = { Text(stringResource(R.string.default_guest_directory)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)

        result?.let { Text(it) }
        val savedMessage = stringResource(R.string.changes_saved)
        Button(modifier = Modifier.fillMaxWidth(), enabled = !saving, onClick = {
            val destination = target; val packageNames = if (inherit) null else profile
            val preferredShell = shell.ifBlank { null }; val startingDirectory = directory.ifBlank { null }
            saving = true
            scope.launch {
                try { withContext(Dispatchers.IO) { defaults.save(destination, packageNames, preferredShell, startingDirectory) }; result = savedMessage }
                catch (e: Exception) { result = e.message }
                finally { saving = false }
            }
        }) { Icon(painterResource(R.drawable.ic_check), null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.save_label)) }
    }
    }
}
