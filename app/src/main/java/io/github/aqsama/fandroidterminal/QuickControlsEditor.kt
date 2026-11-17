// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import androidx.compose.foundation.layout.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
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
    listOf(layout.always, layout.expanded).forEachIndexed { index, row ->
        val active = destination == index
        val color by animateColorAsState(if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)
        Surface(onClick = { destination = index; picked = null }, shape = MaterialTheme.shapes.large,
            color = color, border = if (active) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
            shadowElevation = if (active) 2.dp else 0.dp, modifier = Modifier.fillMaxWidth().animateContentSize().semantics { selected = active; role = Role.RadioButton }) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(if (index == 0) R.drawable.ic_keyboard else R.drawable.ic_chevron_up), null, Modifier.size(20.dp))
                    Text(stringResource(if (index == 0) R.string.keys_always else R.string.keys_expanded), Modifier.weight(1f).padding(horizontal = 8.dp), style = MaterialTheme.typography.titleSmall)
                    if (active) Icon(painterResource(R.drawable.ic_check), null, Modifier.size(20.dp))
                }
                CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { name -> key(name) {
                            FilterChip(picked == name, { picked = if (picked == name) null else name; destination = index },
                                modifier = Modifier.heightIn(min = 48.dp), shape = MaterialTheme.shapes.medium,
                                colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.surface,
                                    selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = MaterialTheme.colorScheme.onPrimary),
                                label = { Text(name, maxLines = 1, softWrap = false) })
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
            Column(Modifier.fillMaxWidth().animateContentSize().padding(16.dp)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Preferences.supportedKeys.filterNot { it in layout.always + layout.expanded }.forEach { name ->
            SuggestionChip(modifier = Modifier.heightIn(min = 48.dp), enabled = layout.always.size + layout.expanded.size < 16, onClick = { put(name, destination) }, label = { Text(name) })
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedButton({ change(QuickKeyLayout.parse("Esc / - Home Up End PgUp :: Tab Ctrl Alt Left Down Right PgDn").tokens); picked = null }) { Text(stringResource(R.string.keys_termux_preset)) }
        TextButton({ change(QuickKeyLayout.parse(QuickKeyLayout.DEFAULT).tokens); picked = null }) { Text(stringResource(R.string.settings_reset_keys)) }
    }
}
