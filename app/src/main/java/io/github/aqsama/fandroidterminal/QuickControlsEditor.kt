// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aqsama.fandroidterminal.runtime.QuickKeyLayout

/** Two persistent rows; removing a key only returns it to the available palette. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun QuickControlsEditor(keys: List<String>, change: (List<String>) -> Unit) {
    val layout = QuickKeyLayout.parse(keys.joinToString(" "))
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    var destination by rememberSaveable { mutableIntStateOf(0) }
    val bounds = remember { mutableMapOf<String, Rect>() }
    val latest by rememberUpdatedState(layout)
    val changed by rememberUpdatedState(change)
    LaunchedEffect(layout) { if (picked !in layout.always + layout.expanded) picked = null }
    fun put(name: String, row: Int, before: String? = null) {
        val rows = listOf(latest.always.toMutableList(), latest.expanded.toMutableList())
        rows.forEach { it.remove(name) }
        rows[row].add(before?.let { rows[row].indexOf(it).takeIf { i -> i >= 0 } } ?: rows[row].size, name)
        changed(QuickKeyLayout(rows[0], rows[1]).tokens)
    }
    Text(stringResource(R.string.quick_rows_help), style = MaterialTheme.typography.bodyMedium)
    listOf(layout.always, layout.expanded).forEachIndexed { index, row ->
        Text(stringResource(if (index == 0) R.string.keys_always else R.string.keys_expanded), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.fillMaxWidth().padding(8.dp)) {
                CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEach { name -> key(name) {
                            FilterChip(picked == name, {
                                val source = picked
                                if (source != null && source != name && source in latest.always + latest.expanded) { put(source, index, name); picked = null }
                                else picked = if (source == name) null else name
                            }, modifier = Modifier.onGloballyPositioned { bounds[name] = it.boundsInRoot() }.pointerInput(name) {
                                var position = Offset.Zero
                                detectDragGesturesAfterLongPress(onDragStart = { position = (bounds[name]?.topLeft ?: Offset.Zero) + it; picked = name }, onDragEnd = { picked = null }, onDragCancel = { picked = null }) { event, delta ->
                                    event.consume(); position += delta
                                    bounds.entries.firstOrNull { it.key != name && it.key in latest.always + latest.expanded && it.value.contains(position) }?.let {
                                        put(name, if (it.key in latest.always) 0 else 1, it.key)
                                    }
                                }
                            }.widthIn(min = 56.dp), label = { Text(name) })
                        } }
                        if (row.isEmpty()) Text(stringResource(R.string.settings_no_keys), Modifier.padding(12.dp))
                    }
                }
                TextButton({ if (picked != null && picked in latest.always + latest.expanded) { put(picked!!, index); picked = null }; destination = index }) {
                    Text(stringResource(if (picked == null) R.string.keys_add_here else R.string.keys_move_here))
                }
            }
        }
    }
    picked?.takeIf { it in layout.always + layout.expanded }?.let { name ->
        val rowIndex = if (name in layout.always) 0 else 1
        val row = if (rowIndex == 0) layout.always else layout.expanded
        val index = row.indexOf(name)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = index > 0, onClick = { put(name, rowIndex, row[index - 1]) }) { Text(stringResource(R.string.key_previous)) }
            OutlinedButton(enabled = index >= 0 && index < row.lastIndex, onClick = {
                val reordered = row.toMutableList().apply { add(index + 1, removeAt(index)) }
                change(if (rowIndex == 0) QuickKeyLayout(reordered, layout.expanded).tokens else QuickKeyLayout(layout.always, reordered).tokens)
            }) { Text(stringResource(R.string.key_next)) }
            TextButton({ change(QuickKeyLayout(layout.always - name, layout.expanded - name).tokens); picked = null }) { Text(stringResource(R.string.remove_key)) }
        }
    }
    Text(stringResource(R.string.keys_available_for, stringResource(if (destination == 0) R.string.keys_always else R.string.keys_expanded)), style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Preferences.supportedKeys.filterNot { it in layout.always + layout.expanded }.forEach { name ->
            SuggestionChip(enabled = layout.always.size + layout.expanded.size < 16, onClick = { put(name, destination) }, label = { Text(name) })
        }
    }
    TextButton({ change(QuickKeyLayout.parse(QuickKeyLayout.DEFAULT).tokens); picked = null }) { Text(stringResource(R.string.settings_reset_keys)) }
}
