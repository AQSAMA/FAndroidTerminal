// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aqsama.fandroidterminal.runtime.linux.CliLauncher
import io.github.aqsama.fandroidterminal.runtime.linux.LinuxEnvironment
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LauncherScreen(preferences: Preferences, environments: List<LinuxEnvironment>, launch: (CliLauncher) -> Unit, packages: () -> Unit) {
    var editing by remember { mutableStateOf<CliLauncher?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val saved = stringResource(R.string.changes_saved)
    fun save(rows: List<CliLauncher>, done: () -> Unit = {}) {
        saving = true
        scope.launch {
            try { preferences.saveLaunchers(rows); result = saved; done() }
            catch (e: Exception) { result = e.message }
            finally { saving = false }
        }
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(stringResource(R.string.launcher_help))
        Row {
            TextButton(enabled = !saving && environments.any { it.ready }, onClick = { adding = true }) { Text(stringResource(R.string.launcher_add)) }
            TextButton(packages) { Text(stringResource(R.string.packages_title)) }
        }
        result?.let { Text(it) }
        LazyColumn {
            itemsIndexed(preferences.launchers, key = { _, it -> it.id }) { index, item ->
                val environment = environments.firstOrNull { it.id == item.environmentId && it.ready }
                ListItem(headlineContent = { Text(item.name) }, supportingContent = { Text("${environment?.name ?: stringResource(R.string.launcher_unavailable)} · ${item.directory}\n${item.arguments.first()}") })
                FlowRow {
                    TextButton(enabled = environment != null && !saving, onClick = { launch(item) }) { Text(stringResource(R.string.run_label)) }
                    TextButton(enabled = !saving, onClick = { editing = item }) { Text(stringResource(R.string.edit_label)) }
                    TextButton(enabled = !saving, onClick = { save(preferences.launchers.filterNot { it.id == item.id }) }) { Text(stringResource(R.string.launcher_remove)) }
                    IconButton(enabled = !saving && index > 0, onClick = { save(preferences.launchers.toMutableList().apply { add(index - 1, removeAt(index)) }) }) { Icon(painterResource(R.drawable.ic_chevron_up), stringResource(R.string.move_key_up, item.name)) }
                    IconButton(enabled = !saving && index < preferences.launchers.lastIndex, onClick = { save(preferences.launchers.toMutableList().apply { add(index + 1, removeAt(index)) }) }) { Icon(painterResource(R.drawable.ic_chevron_down), stringResource(R.string.move_key_down, item.name)) }
                }
            }
        }
    }
    if (adding || editing != null) key(editing?.id) {
        var name by rememberSaveable { mutableStateOf(editing?.name.orEmpty()) }
        var environment by rememberSaveable { mutableStateOf(editing?.environmentId ?: environments.firstOrNull { it.ready }?.id.orEmpty()) }
        var directory by rememberSaveable { mutableStateOf(editing?.directory ?: "/root") }
        var argv by rememberSaveable { mutableStateOf(editing?.arguments?.joinToString("\n") ?: "/usr/local/bin/opencode") }
        var error by remember { mutableStateOf<String?>(null) }
        AlertDialog(onDismissRequest = { if (!saving) { adding = false; editing = null } }, title = { Text(stringResource(R.string.launchers_title)) },
            text = { LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { OutlinedTextField(name, { if (it.length <= 60) name = it }, label = { Text(stringResource(R.string.launcher_name)) }) }
                item { FlowRow { environments.filter { it.ready }.forEach { env -> FilterChip(environment == env.id, { environment = env.id }, label = { Text(env.name) }) } } }
                item { OutlinedTextField(directory, { if (it.length <= 4096) directory = it }, label = { Text(stringResource(R.string.default_guest_directory)) }) }
                item { Text(stringResource(R.string.launcher_argv_help)); OutlinedTextField(argv, { if (it.length <= 16384) argv = it }, minLines = 3) }
                error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            } },
            confirmButton = { TextButton(enabled = !saving, onClick = {
                try {
                    val value = CliLauncher(editing?.id ?: UUID.randomUUID().toString(), name, environment, directory, argv.lines()).validated()
                    save(if (editing == null) preferences.launchers + value else preferences.launchers.map { if (it.id == value.id) value else it }) { editing = null; adding = false }
                } catch (e: Exception) { error = e.message }
            }) { Text(stringResource(R.string.save_label)) } },
            dismissButton = { TextButton(enabled = !saving, onClick = { editing = null; adding = false }) { Text(stringResource(R.string.cancel_label)) } })
    }
}
