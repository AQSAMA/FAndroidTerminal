// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aqsama.fandroidterminal.runtime.QuickKeyLayout

/** Explicit selection and destinations keep editing usable without precise drag targets. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun QuickControlsEditor(keys: List<String>, change: (List<String>) -> Unit) {
    val layout = QuickKeyLayout.parse(keys.joinToString(" "))
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    var destination by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(layout) { if (picked !in layout.always + layout.expanded) picked = null }
    fun put(name: String, row: Int, before: String? = null) {
        val rows = listOf(layout.always.toMutableList(), layout.expanded.toMutableList())
        rows.forEach { it.remove(name) }
        rows[row].add(before?.let { rows[row].indexOf(it).takeIf { i -> i >= 0 } } ?: rows[row].size, name)
        change(QuickKeyLayout(rows[0], rows[1]).tokens)
    }
    Text(stringResource(R.string.quick_rows_help), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        listOf(R.string.keys_always, R.string.keys_expanded).forEachIndexed { index, label ->
            SegmentedButton(selected = destination == index, onClick = { destination = index }, shape = SegmentedButtonDefaults.itemShape(index, 2)) { Text(stringResource(label)) }
        }
    }
    listOf(layout.always, layout.expanded).forEachIndexed { index, row ->
        Surface(shape = MaterialTheme.shapes.large, color = if (destination == index) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(if (index == 0) R.string.keys_always else R.string.keys_expanded), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { name -> key(name) {
                            FilterChip(picked == name, { picked = if (picked == name) null else name; destination = index }, modifier = Modifier.heightIn(min = 48.dp), label = { Text(name) })
                        } }
                        if (row.isEmpty()) Text(stringResource(R.string.settings_no_keys), Modifier.padding(12.dp))
                    }
                }
            }
        }
    }
    picked?.takeIf { it in layout.always + layout.expanded }?.let { name ->
        val rowIndex = if (name in layout.always) 0 else 1
        val row = if (rowIndex == 0) layout.always else layout.expanded
        val index = row.indexOf(name)
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer) {
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled = index > 0, onClick = { put(name, rowIndex, row[index - 1]) }) { Text(stringResource(R.string.key_previous)) }
                    OutlinedButton(enabled = index < row.lastIndex, onClick = {
                        val reordered = row.toMutableList().apply { add(index + 1, removeAt(index)) }
                        change(if (rowIndex == 0) QuickKeyLayout(reordered, layout.expanded).tokens else QuickKeyLayout(layout.always, reordered).tokens)
                    }) { Text(stringResource(R.string.key_next)) }
                    TextButton({ put(name, 1 - rowIndex); destination = 1 - rowIndex }) { Text(stringResource(R.string.keys_move_other_row)) }
                    TextButton({ change(QuickKeyLayout(layout.always - name, layout.expanded - name).tokens); picked = null }) { Text(stringResource(R.string.remove_key)) }
                }
            }
        }
    }
    Text(stringResource(R.string.keys_available_for, stringResource(if (destination == 0) R.string.keys_always else R.string.keys_expanded)), style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Preferences.supportedKeys.filterNot { it in layout.always + layout.expanded }.forEach { name ->
            SuggestionChip(modifier = Modifier.heightIn(min = 48.dp), enabled = layout.always.size + layout.expanded.size < 16, onClick = { put(name, destination) }, label = { Text(name) })
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton({ change(QuickKeyLayout.parse("Esc / - Home Up End PgUp :: Tab Ctrl Alt Left Down Right PgDn").tokens); picked = null }) { Text(stringResource(R.string.keys_termux_preset)) }
        TextButton({ change(QuickKeyLayout.parse(QuickKeyLayout.DEFAULT).tokens); picked = null }) { Text(stringResource(R.string.settings_reset_keys)) }
    }
}
