// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Alignment
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aqsama.fandroidterminal.runtime.QuickKeyLayout

/** Long-press dragging and explicit actions share the same ordered layout. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun QuickControlsEditor(keys: List<String>, change: (List<String>) -> Unit) {
    val layout = QuickKeyLayout.parse(keys.joinToString(" "))
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    var destination by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(layout) { if (picked !in layout.always + layout.expanded) picked = null }
    val haptic = LocalHapticFeedback.current
    val keyBounds = remember { mutableMapOf<String, Rect>() }
    val rowBounds = remember { mutableMapOf<Int, Rect>() }
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var pointer by remember { mutableStateOf(Offset.Zero) }
    val currentLayout by rememberUpdatedState(layout)
    val currentChange by rememberUpdatedState(change)
    fun put(name: String, row: Int, before: String? = null) {
        currentChange(currentLayout.move(name, row, before).tokens)
    }
    val dropRow = rowBounds.entries.firstOrNull { it.value.contains(pointer) }?.key
    val dropKey = dropRow?.let { row ->
        (if (row == 0) layout.always else layout.expanded).filter { it != dragging }
            .minByOrNull { keyBounds[it]?.center?.minus(pointer)?.getDistance() ?: Float.MAX_VALUE }
    }
    var editorOrigin by remember { mutableStateOf(Offset.Zero) }
    Box(Modifier.fillMaxWidth().onGloballyPositioned { editorOrigin = it.boundsInRoot().topLeft }) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.quick_live_preview), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column {
                    TerminalPreview(io.github.aqsama.fandroidterminal.terminal.TerminalSchemes.default, "JetBrainsMono", 12, Modifier.fillMaxWidth().height(96.dp))
                    TerminalKeyControls(layout, true, remember { TerminalModifiers() })
                }
            }
            Text(stringResource(R.string.quick_drag_help), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            listOf(layout.always, layout.expanded).forEachIndexed { index, row ->
                val active = destination == index
                val color by animateColorAsState(if (dragging != null && dropRow == index) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)
                Surface(onClick = { destination = index; picked = null }, shape = MaterialTheme.shapes.large,
                    color = color, border = if (active) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                    shadowElevation = if (active) 2.dp else 0.dp, modifier = Modifier.fillMaxWidth().onGloballyPositioned { rowBounds[index] = it.boundsInRoot() }.semantics { selected = active; role = Role.RadioButton }) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(painterResource(if (index == 0) R.drawable.ic_keyboard else R.drawable.ic_chevron_up), null, Modifier.size(20.dp))
                            Text(stringResource(if (index == 0) R.string.keys_always else R.string.keys_expanded), Modifier.weight(1f).padding(horizontal = 8.dp), style = MaterialTheme.typography.titleSmall)
                            if (active) Icon(painterResource(R.drawable.ic_check), null, Modifier.size(20.dp))
                        }
                        CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                row.forEach { name -> key(name) {
                                    FilterChip(picked == name, { picked = if (picked == name) null else name; destination = index },
                                        modifier = Modifier.heightIn(min = 48.dp)
                                        .onGloballyPositioned { keyBounds[name] = it.boundsInRoot() }
                                        .graphicsLayer {
                                            alpha = if (dragging == name) 0.35f else 1f
                                        }
                                        .pointerInput(name) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = { position ->
                                                    dragging = name; dragOffset = Offset.Zero
                                                    pointer = (keyBounds[name]?.topLeft ?: Offset.Zero) + position
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                },
                                                onDrag = { event, delta -> event.consume(); dragOffset += delta; pointer += delta },
                                                onDragCancel = { dragging = null; dragOffset = Offset.Zero },
                                                onDragEnd = {
                                                    val targetRow = rowBounds.entries.firstOrNull { it.value.contains(pointer) }?.key
                                                    if (targetRow != null) {
                                                        val targetKeys = (if (targetRow == 0) currentLayout.always else currentLayout.expanded).filter { it != name }
                                                        val nearest = targetKeys.minByOrNull { keyBounds[it]?.center?.minus(pointer)?.getDistance() ?: Float.MAX_VALUE }
                                                        val before = nearest?.let { target ->
                                                            val bounds = keyBounds[target]
                                                            if (bounds != null && pointer.x > bounds.center.x) targetKeys.getOrNull(targetKeys.indexOf(target) + 1) else target
                                                        }
                                                        put(name, targetRow, before); destination = targetRow; picked = name
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    }
                                                    dragging = null; dragOffset = Offset.Zero
                                                })
                                        },
                                    border = if (dragging != null && dropKey == name) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else FilterChipDefaults.filterChipBorder(enabled = true, selected = picked == name),
                                    leadingIcon = { Icon(painterResource(R.drawable.ic_drag_handle), null, Modifier.size(16.dp)) }, shape = MaterialTheme.shapes.medium,
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
                io.github.aqsama.fandroidterminal.runtime.QuickKeyLayout.supported.filterNot { it in layout.always + layout.expanded }.forEach { name ->
                    SuggestionChip(modifier = Modifier.heightIn(min = 48.dp), enabled = layout.always.size + layout.expanded.size < 16, onClick = { put(name, destination) }, label = { Text(name) })
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton({ change(QuickKeyLayout.parse(QuickKeyLayout.TERMUX).tokens); picked = null }) { Text(stringResource(R.string.keys_termux_preset)) }
                TextButton({ change(QuickKeyLayout.parse(QuickKeyLayout.DEFAULT).tokens); picked = null }) { Text(stringResource(R.string.settings_reset_keys)) }
            }
        }
    dragging?.let { name ->
        val origin = (keyBounds[name]?.topLeft ?: pointer) - editorOrigin + dragOffset
        Surface(modifier = Modifier.align(androidx.compose.ui.AbsoluteAlignment.TopLeft).absoluteOffset { IntOffset(origin.x.roundToInt(), origin.y.roundToInt()) },
            shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer,
            shadowElevation = 8.dp) {
            Row(Modifier.heightIn(min = 48.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(painterResource(R.drawable.ic_drag_handle), null, Modifier.size(16.dp))
                Text(name)
            }
        }
    }
    }

}
