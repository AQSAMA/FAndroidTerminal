// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.animateContentSize
import io.github.aqsama.fandroidterminal.designsystem.ConnectedGroup
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
internal fun LauncherScreen(preferences: Preferences, environments: List<LinuxEnvironment>, launch: (CliLauncher) -> Unit, checkTool: suspend (CliLauncher) -> Boolean, installer: (@Composable () -> Unit)? = null, packages: (CliLauncher?) -> Unit) {
    var editing by remember { mutableStateOf<CliLauncher?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var availableTools by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    LaunchedEffect(preferences.launchers, environments) {
        availableTools = emptyMap()
        preferences.launchers.forEach { item -> availableTools = availableTools + (item.id to checkTool(item)) }
    }
    val saved = stringResource(R.string.changes_saved)
    fun save(rows: List<CliLauncher>, done: () -> Unit = {}) {
        saving = true
        scope.launch {
            try { preferences.saveLaunchers(rows); result = saved; done() }
            catch (e: Exception) { result = e.message }
            finally { saving = false }
        }
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !saving && environments.any { it.ready }, onClick = { adding = true }) { Icon(painterResource(R.drawable.ic_add), null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.launcher_add)) }
                FilledTonalButton({ packages(null) }) { Icon(painterResource(R.drawable.ic_download), null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.packages_title)) }
            }
        }
        item { installer?.invoke() }
        result?.let { item { Text(operationLabel(it), style = MaterialTheme.typography.bodySmall) } }
        if (preferences.launchers.isEmpty()) item { FeatureEmptyState(R.drawable.ic_prompt, stringResource(R.string.launcher_empty), stringResource(R.string.launcher_empty_hint)) }
        itemsIndexed(preferences.launchers, key = { _, it -> it.id }) { index, item ->
            val environment = environments.firstOrNull { it.id == item.environmentId && it.ready }
            val toolReady = availableTools[item.id] == true
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.fillMaxWidth().padding(16.dp).animateContentSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ContextBadge(environment?.distribution)
                        Column(Modifier.weight(1f)) {
                            Text(item.name, style = MaterialTheme.typography.titleMedium)
                            Text(environment?.name ?: stringResource(R.string.launcher_unavailable), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Box {
                            var menu by remember { mutableStateOf(false) }
                            IconButton({ menu = true }) { Icon(painterResource(R.drawable.ic_more), stringResource(R.string.session_actions)) }
                            DropdownMenu(menu, { menu = false }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.edit_label)) }, enabled = !saving, onClick = { editing = item; menu = false })
                                DropdownMenuItem(text = { Text(stringResource(R.string.launcher_install_repair)) }, enabled = !saving, onClick = { if (environment == null) editing = item else packages(item); menu = false })
                                DropdownMenuItem(text = { Text(stringResource(R.string.move_key_up, item.name)) }, enabled = !saving && index > 0, onClick = { save(preferences.launchers.toMutableList().apply { add(index - 1, removeAt(index)) }); menu = false })
                                DropdownMenuItem(text = { Text(stringResource(R.string.move_key_down, item.name)) }, enabled = !saving && index < preferences.launchers.lastIndex, onClick = { save(preferences.launchers.toMutableList().apply { add(index + 1, removeAt(index)) }); menu = false })
                                DropdownMenuItem(text = { Text(stringResource(R.string.launcher_remove), color = MaterialTheme.colorScheme.error) }, enabled = !saving, onClick = { save(preferences.launchers.filterNot { it.id == item.id }); menu = false })
                            }
                        }
                    }
                    Text(item.arguments.joinToString(" "), style = MaterialTheme.typography.bodyMedium.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, textDirection = androidx.compose.ui.text.style.TextDirection.Ltr), maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text(item.directory, style = MaterialTheme.typography.labelSmall.copy(textDirection = androidx.compose.ui.text.style.TextDirection.Ltr), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(if (toolReady) R.string.launcher_ready else R.string.launcher_needs_setup), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = if (toolReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary)
                        FilledTonalButton(enabled = environment != null && !saving && availableTools[item.id] != null, onClick = { if (toolReady) launch(item) else packages(item) }) {
                            Icon(painterResource(if (toolReady) R.drawable.ic_terminal else R.drawable.ic_download), null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(if (toolReady) R.string.run_label else R.string.launcher_install_repair))
                        }
                    }
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
