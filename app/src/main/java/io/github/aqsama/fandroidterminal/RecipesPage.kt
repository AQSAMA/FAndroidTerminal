// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aqsama.fandroidterminal.runtime.ShellRecipe
import io.github.aqsama.fandroidterminal.sessions.SessionStatus
import io.github.aqsama.fandroidterminal.sessions.TerminalSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
internal fun RecipesPage(
    preferences: Preferences,
    active: List<TerminalSession>,
    selectedId: String?,
    canOpenShell: Boolean,
    identify: (TerminalSession) -> String,
    openDraft: () -> Unit,
    review: (TerminalSession?, String) -> Unit,
) {
    val templates = listOf(
        ShellRecipe("sample-pwd", stringResource(R.string.workspace_command_pwd), "pwd"),
        ShellRecipe("sample-find", stringResource(R.string.recipe_find_files), "find . -type f -name \"\$1\"", listOf(stringResource(R.string.recipe_filename))),
        ShellRecipe("sample-grep", stringResource(R.string.recipe_search_text), "grep -rnF -- \"\$1\" .", listOf(stringResource(R.string.recipe_text))),
        ShellRecipe("sample-size", stringResource(R.string.recipe_directory_size), "du -sh -- \"\$1\"", listOf(stringResource(R.string.recipe_directory))),
    )
    val snippets = preferences.snippets.lines().filter { it.isNotBlank() }
    var query by rememberSaveable { mutableStateOf("") }
    var editId by rememberSaveable { mutableStateOf<String?>(null) }
    var runId by rememberSaveable { mutableStateOf<String?>(null) }
    var removeId by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val matches: (ShellRecipe) -> Boolean = { it.name.contains(query, true) || it.script.contains(query, true) }
    val owned = preferences.recipes.filter(matches)
    val examples = templates.filter(matches)
    val savedCommands = snippets.withIndex().filter { it.value.contains(query, true) }
    val running = active.filter { candidate ->
        key(candidate.id) { candidate.status.collectAsStateWithLifecycle().value == SessionStatus.Running }
    }
    fun save(recipes: List<ShellRecipe>, done: () -> Unit) {
        if (saving) return
        scope.launch {
            saving = true
            saveError = false
            try { preferences.saveRecipes(recipes); done() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { saveError = true }
            finally { saving = false }
        }
    }
    LazyColumn(Modifier.widthIn(max = 760.dp).fillMaxSize(), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                StudioHeading(stringResource(R.string.studio_recipes), Modifier.weight(1f))
                FilledTonalIconButton({ editId = "" }, enabled = !saving && preferences.recipes.size < 40) {
                    Icon(painterResource(R.drawable.ic_add), stringResource(R.string.recipe_new))
                }
            }
        }
        item {
            OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                leadingIcon = { Icon(painterResource(R.drawable.ic_search), null) }, label = { Text(stringResource(R.string.recipe_find)) })
            if (selectedId != null) TextButton(onClick = openDraft) {
                Icon(painterResource(R.drawable.ic_edit), null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.write_command))
            }
        }
        if (owned.isNotEmpty()) {
            item { RecipeHeading(R.string.recipe_yours) }
            items(owned, key = { it.id }) { recipe ->
                RecipeCard(recipe.name, recipe.script, { runId = recipe.id }) { closeMenu ->
                    ActionMenuItem(stringResource(R.string.edit_label), R.drawable.ic_edit) { closeMenu(); saveError = false; editId = recipe.id }
                    ActionMenuItem(stringResource(R.string.delete_menu), R.drawable.ic_delete, destructive = true) { closeMenu(); saveError = false; removeId = recipe.id }
                }
            }
        }
        if (savedCommands.isNotEmpty()) {
            item { RecipeHeading(R.string.saved_commands_label) }
            items(savedCommands, key = { "saved-${it.index}" }) { entry ->
                RecipeCard(entry.value, null, { runId = "saved-${entry.index}" })
            }
        }
        if (examples.isNotEmpty()) {
            item { RecipeHeading(R.string.recipe_templates) }
            items(examples, key = { it.id }) { recipe -> RecipeCard(recipe.name, recipe.script, { runId = recipe.id }) }
        }
        if (owned.isEmpty() && examples.isEmpty() && savedCommands.isEmpty()) item {
            Text(stringResource(R.string.no_matching_actions), Modifier.padding(vertical = 12.dp))
        }
    }
    val editable = editId
    if (editable != null) {
        RecipeEditor(preferences.recipes.firstOrNull { it.id == editable }, saving, saveError,
            { if (!saving) editId = null }) { recipe ->
            val current = preferences.recipes
            save(if (editable.isEmpty()) current + recipe else current.map { if (it.id == recipe.id) recipe else it }) { editId = null }
        }
    }
    preferences.recipes.firstOrNull { it.id == removeId }?.let { recipe ->
        AlertDialog(onDismissRequest = { if (!saving) removeId = null },
            title = { Text(stringResource(R.string.recipe_delete, recipe.name)) },
            text = if (saveError) ({ Text(stringResource(R.string.studio_save_failed)) }) else null,
            confirmButton = { TextButton(enabled = !saving, onClick = {
                save(preferences.recipes.filterNot { it.id == recipe.id }) { removeId = null }
            }) { Text(stringResource(R.string.delete_menu), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { if (!saving) removeId = null }) { Text(stringResource(R.string.cancel_label)) } })
    }
    val savedIndex = runId?.takeIf { it.startsWith("saved-") }?.removePrefix("saved-")?.toIntOrNull()
    val savedCommand = savedIndex?.let { snippets.getOrNull(it) }
    val runnable = (preferences.recipes + templates).firstOrNull { it.id == runId }
    if (runnable != null || savedCommand != null) {
        RecipeRunDialog(runnable, savedCommand, running, selectedId, canOpenShell, identify, { runId = null }) { session, command ->
            runId = null
            review(session, command)
        }
    }
}

@Composable
private fun RecipeHeading(label: Int) = Text(stringResource(label), Modifier.padding(top = 12.dp, bottom = 2.dp),
    style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

@Composable
private fun RecipeCard(name: String, script: String?, open: () -> Unit, actions: (@Composable ColumnScope.(() -> Unit) -> Unit)? = null) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).heightIn(min = 62.dp).clickable(onClick = open).padding(vertical = 10.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Icon(painterResource(R.drawable.ic_shell), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                    if (script != null) RecipeCode(script, 1)
                }
                if (actions == null) Icon(painterResource(R.drawable.ic_chevron), null, Modifier.padding(end = 8.dp).size(18.dp))
            }
            if (actions != null) {
                var expanded by remember { mutableStateOf(false) }
                Box {
                    IconButton({ expanded = true }) {
                        Icon(painterResource(R.drawable.ic_more), stringResource(R.string.recipe_options, name))
                    }
                    DropdownMenu(expanded, { expanded = false }) {
                        actions { expanded = false }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecipeCode(text: String, lines: Int) {
    Text(text, maxLines = lines, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, textDirection = TextDirection.Ltr))
}

@Composable
private fun RecipeEditor(recipe: ShellRecipe?, saving: Boolean, saveError: Boolean, dismiss: () -> Unit, save: (ShellRecipe) -> Unit) {
    val id = rememberSaveable(recipe?.id) { recipe?.id ?: UUID.randomUUID().toString() }
    var name by rememberSaveable(id) { mutableStateOf(recipe?.name.orEmpty()) }
    var script by rememberSaveable(id) { mutableStateOf(recipe?.script.orEmpty()) }
    var inputList by rememberSaveable(id) { mutableStateOf(recipe?.inputs.orEmpty().joinToString(", ")) }
    val fields = inputList.split(',').map(String::trim).filter(String::isNotEmpty)
    val value = runCatching { ShellRecipe(id, name.trim(), script, fields).validated() }.getOrNull()
    AlertDialog(onDismissRequest = dismiss, title = { Text(stringResource(if (recipe == null) R.string.recipe_new else R.string.edit_label)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { if (it.length <= 60) name = it }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text(stringResource(R.string.name_label)) })
                OutlinedTextField(script, { if (it.toByteArray(Charsets.UTF_8).size <= 4096) script = it },
                    modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 7,
                    label = { Text(stringResource(R.string.recipe_script)) },
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, textDirection = TextDirection.Ltr))
                OutlinedTextField(inputList, { if (it.length <= 100) inputList = it }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text(stringResource(R.string.recipe_inputs)) },
                    supportingText = { Text(stringResource(R.string.recipe_input_help)) })
                if (saveError) Text(stringResource(R.string.studio_save_failed), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(enabled = value != null && !saving, onClick = { value?.let(save) }) { Text(stringResource(R.string.save_label)) } },
        dismissButton = { TextButton(enabled = !saving, onClick = dismiss) { Text(stringResource(R.string.cancel_label)) } })
}

@Composable
private fun RecipeRunDialog(recipe: ShellRecipe?, saved: String?, sessions: List<TerminalSession>, selectedId: String?,
    canOpenShell: Boolean, identify: (TerminalSession) -> String, dismiss: () -> Unit, review: (TerminalSession?, String) -> Unit) {
    val labels = recipe?.inputs.orEmpty()
    var values by rememberSaveable(recipe?.id) { mutableStateOf(List(labels.size) { "" }) }
    var target by rememberSaveable { mutableStateOf(sessions.firstOrNull { it.id == selectedId }?.id ?: sessions.firstOrNull()?.id) }
    val session = sessions.firstOrNull { it.id == target }
    val newShell = target == null
    val allowsNewShell = newShell && canOpenShell
    var choosing by remember { mutableStateOf(false) }
    val command = saved ?: recipe?.takeIf { values.all(String::isNotBlank) }?.let { runCatching { it.command(values) }.getOrNull() }
    AlertDialog(onDismissRequest = dismiss, title = { Text(recipe?.name ?: stringResource(R.string.saved_commands_label)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RecipeCode(recipe?.script ?: saved.orEmpty(), 6)
                labels.forEachIndexed { index, label ->
                    OutlinedTextField(values[index], { input ->
                        if (input.length <= 512 && input.none(Char::isISOControl)) values = values.mapIndexed { i, old -> if (i == index) input else old }
                    }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(label) })
                }
                Box {
                    OutlinedButton({ choosing = true }, enabled = sessions.isNotEmpty() || canOpenShell, modifier = Modifier.fillMaxWidth()) {
                        Text(session?.let(identify) ?: stringResource(if (newShell) R.string.new_android_shell else R.string.recipe_choose_session), maxLines = 2)
                        Icon(painterResource(R.drawable.ic_chevron_down), null, Modifier.size(18.dp))
                    }
                    DropdownMenu(choosing, { choosing = false }) {
                        sessions.forEach { item -> DropdownMenuItem(text = { Text(identify(item)) }, onClick = { target = item.id; choosing = false }) }
                        if (canOpenShell) DropdownMenuItem(text = { Text(stringResource(R.string.new_android_shell)) },
                            onClick = { target = null; choosing = false })
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = command != null && (session != null || allowsNewShell),
            onClick = { command?.let { review(session, it) } }) {
            Text(stringResource(if (session == null) R.string.recipe_open_draft else R.string.recipe_review))
        } },
        dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.cancel_label)) } })
}
