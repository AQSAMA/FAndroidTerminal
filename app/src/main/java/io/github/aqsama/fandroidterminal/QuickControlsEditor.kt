// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Long press to move, or tap a key then its destination. Both preserve the runtime's two rows. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun QuickControlsEditor(keys: List<String>, change: (List<String>) -> Unit) {
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    var dragging by remember { mutableStateOf<String?>(null) }
    val bounds = remember { mutableMapOf<String, Rect>() }
    val latestKeys by rememberUpdatedState(keys)
    val onChange by rememberUpdatedState(change)
    fun move(name: String, destination: String) {
        val current = latestKeys
        if (name == destination || name !in current || destination !in current) return
        // Modifiers occupy the persistent row; navigation/symbols occupy the expanded row.
        if ((name in listOf("Ctrl", "Alt", "Esc")) != (destination in listOf("Ctrl", "Alt", "Esc"))) return
        onChange(current.toMutableList().apply { add(indexOf(destination), removeAt(indexOf(name))) })
    }
    Text(stringResource(R.string.keys_drag_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    listOf(true, false).forEach { pinned ->
        Text(stringResource(if (pinned) R.string.quick_controls else R.string.additional_keys), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            FlowRow(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val row = keys.filter { (it in listOf("Ctrl", "Alt", "Esc")) == pinned }
                if (row.isEmpty()) Text(stringResource(R.string.settings_no_keys), Modifier.padding(12.dp))
                row.forEach { name -> key(name) {
                    val upLabel = stringResource(R.string.move_key_up, name)
                    val downLabel = stringResource(R.string.move_key_down, name)
                    val active = picked == name || dragging == name
                    val color by animateColorAsState(if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh, label = "picked key")
                    Surface(onClick = {
                        val source = picked
                        if (source == null || source == name) picked = if (source == name) null else name
                        else { move(source, name); picked = null }
                    }, color = color, shape = RoundedCornerShape(if (active) 12.dp else 20.dp), modifier = Modifier
                        .onGloballyPositioned { bounds[name] = it.boundsInRoot() }
                        .semantics {
                            customActions = listOf(
                                CustomAccessibilityAction(upLabel) { row.getOrNull(row.indexOf(name) - 1)?.let { move(name, it) }; true },
                                CustomAccessibilityAction(downLabel) { row.getOrNull(row.indexOf(name) + 1)?.let { move(name, it) }; true })
                        }
                        .pointerInput(name) {
                            var position = Offset.Zero
                            detectDragGesturesAfterLongPress(onDragStart = { local -> position = (bounds[name]?.topLeft ?: Offset.Zero) + local; dragging = name; picked = null },
                                onDragEnd = { dragging = null }, onDragCancel = { dragging = null }) { event, delta ->
                                event.consume(); position += delta
                                bounds.entries.firstOrNull { it.key in latestKeys && it.key != name && it.value.contains(position) }?.let { move(name, it.key) }
                            }
                        }) {
                        Text(name, Modifier.heightIn(min = 48.dp).padding(horizontal = 16.dp).wrapContentHeight(), style = MaterialTheme.typography.labelLarge)
                    }
                } }
            }
        }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("${keys.size}/16", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
        TextButton(enabled = picked != null, onClick = { change(keys - picked!!); picked = null }) { Icon(painterResource(R.drawable.ic_minus), null, Modifier.size(18.dp)); Text(stringResource(R.string.remove_key)) }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Preferences.supportedKeys.filterNot { it in keys }.forEach { name ->
            SuggestionChip(onClick = { if (keys.size < 16) change(keys + name) }, enabled = keys.size < 16, label = { Text(name) }, icon = { Icon(painterResource(R.drawable.ic_add), null, Modifier.size(16.dp)) })
        }
    }
    TextButton({ change(listOf("Esc", "Ctrl", "Alt", "Tab", "Up", "Down", "Left", "Right")); picked = null }) { Icon(painterResource(R.drawable.ic_restore), null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.settings_reset_keys)) }
}
