// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aqsama.fandroidterminal.sessions.GuestDefaults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GuestDefaultsScreen(defaults: GuestDefaults) {
    var target by rememberSaveable { mutableStateOf("global") }
    var profile by rememberSaveable(target) { mutableStateOf(defaults.rawProfile(target).orEmpty()) }
    var inherit by rememberSaveable(target) { mutableStateOf(target != "global" && defaults.rawProfile(target) == null) }
    var shell by rememberSaveable(target) { mutableStateOf(defaults.shell(target).orEmpty()) }
    var directory by rememberSaveable(target) { mutableStateOf(defaults.directory(target)) }
    var result by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.guest_defaults_help))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("global", "debian", "ubuntu", "alpine").forEach {
                FilterChip(it == target, { target = it; result = null }, label = { Text(it) })
            }
        }
        if (target != "global") Row {
            Checkbox(inherit, { inherit = it }); Text(stringResource(R.string.inherit_profile))
        }
        OutlinedTextField(profile, { if (it.length <= 16384) profile = it }, enabled = !inherit, label = { Text(stringResource(R.string.profile_packages)) }, modifier = Modifier.fillMaxWidth())
        FlowRow {
            TextButton({ profile = "git curl ca-certificates"; inherit = false }) { Text(stringResource(R.string.profile_essentials)) }
            TextButton({ profile = if (target == "alpine") "git curl python3 py3-pip build-base" else "git curl python3 python3-venv build-essential"; inherit = false }) { Text(stringResource(R.string.profile_development)) }
        }
        OutlinedTextField(shell, { shell = it }, label = { Text(stringResource(R.string.default_guest_shell)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(directory, { directory = it }, label = { Text(stringResource(R.string.default_guest_directory)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Text(stringResource(R.string.default_guest_help), style = MaterialTheme.typography.bodySmall)
        result?.let { Text(it) }
        val savedMessage = stringResource(R.string.changes_saved)
        Button(enabled = !saving, onClick = {
            saving = true
            scope.launch {
                try { withContext(Dispatchers.IO) { defaults.save(target, if (inherit) null else profile, shell.ifBlank { null }, directory.ifBlank { null }) }; result = savedMessage }
                catch (e: Exception) { result = e.message }
                finally { saving = false }
            }
        }) { Text(stringResource(R.string.save_label)) }
    }
}
