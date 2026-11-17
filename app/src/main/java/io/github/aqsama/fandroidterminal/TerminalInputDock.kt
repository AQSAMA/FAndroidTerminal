// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aqsama.fandroidterminal.sessions.SessionStatus
import io.github.aqsama.fandroidterminal.sessions.TerminalSession
import org.connectbot.terminal.SelectionController
import org.connectbot.terminal.VTermKey

@Composable
internal fun SessionStatusMark(status: SessionStatus) {
    val color = when (status) {
        SessionStatus.Running -> MaterialTheme.colorScheme.primary
        is SessionStatus.Failed -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(Modifier.size(7.dp).background(color, CircleShape))
}

/** The saved order fills seven-column rows; modifiers remain armed until the next input. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun TerminalInputDock(preferences: Preferences, modifiers: TerminalModifiers, session: TerminalSession,
    selection: SelectionController?, focus: FocusRequester, showKeyboard: (Boolean) -> Unit, paste: () -> Unit, command: () -> Unit, commands: () -> Unit, customizeKeys: () -> Unit = {}) {
    val status by session.status.collectAsStateWithLifecycle()
    val enabled = status == SessionStatus.Running
    val ime = WindowInsets.isImeVisible
    val keys = preferences.keys.split(' ').filter { it.isNotBlank() }
    TerminalKeyControls(keys, enabled, ime, modifiers, selection, focus, showKeyboard, paste, command, commands, customizeKeys = customizeKeys, fontSize = preferences.font, resizeFont = preferences::setTerminalFont, text = { value ->
        value.forEach { session.emulator.dispatchCharacter(modifiers.mask(), it.code) }; modifiers.clearTransients()
    }) { code ->
        session.emulator.dispatchKey(modifiers.mask(), code)
        modifiers.clearTransients()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun TerminalKeyControls(keys: List<String>, enabled: Boolean, ime: Boolean, modifiers: TerminalModifiers,
    selection: SelectionController?, focus: FocusRequester, showKeyboard: (Boolean) -> Unit, paste: () -> Unit,
    command: () -> Unit, commands: () -> Unit, initiallyExpanded: Boolean = false, customizeKeys: () -> Unit = {}, fontSize: Int = 14, resizeFont: (Int) -> Unit = {}, text: (String) -> Unit = {}, key: (Int) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    val layout = io.github.aqsama.fandroidterminal.runtime.QuickKeyLayout.parse(keys.joinToString(" "))
    var menu by remember { mutableStateOf(false) }
    var sizing by rememberSaveable { mutableStateOf(false) }
    val send: (String) -> Unit = { label ->
        when (label) {
            "Ctrl" -> modifiers.ctrl = !modifiers.ctrl
            "Alt" -> modifiers.alt = !modifiers.alt
            "/", "-", "|", "~" -> text(label)
            else -> key(when (label) {
                "Esc" -> VTermKey.ESCAPE; "Tab" -> VTermKey.TAB
                "Up" -> VTermKey.UP; "Down" -> VTermKey.DOWN; "Left" -> VTermKey.LEFT; "Right" -> VTermKey.RIGHT
                "Home" -> VTermKey.HOME; "End" -> VTermKey.END; "PgUp" -> VTermKey.PAGEUP; "PgDn" -> VTermKey.PAGEDOWN
                "Insert" -> VTermKey.INS; else -> VTermKey.DEL
            })
        }
        focus.requestFocus()
    }
    if (sizing) AlertDialog(onDismissRequest = { sizing = false }, title = { Text(stringResource(R.string.terminal_text_size)) },
        text = { Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton({ resizeFont(fontSize - 1) }, enabled = fontSize > 8) { Text("−") }
            Text("$fontSize sp")
            IconButton({ resizeFont(fontSize + 1) }, enabled = fontSize < 28) { Text("+") }
        } }, confirmButton = { TextButton({ sizing = false }) { Text(stringResource(R.string.close_label)) } })
    @Composable fun KeyRow(row: List<String>, modifier: Modifier = Modifier) {
        CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
            Row(modifier.height(48.dp).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                row.forEach { label ->
                    val active = label == "Ctrl" && modifiers.ctrl || label == "Alt" && modifiers.alt
                    Box(Modifier.width(48.dp), contentAlignment = Alignment.Center) {
                        if (label in listOf("Ctrl", "Alt")) {
                            val hint = stringResource(if (active) R.string.modifier_armed else R.string.modifier_idle)
                            Surface(color = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.small) {
                                Box(Modifier.size(48.dp, 48.dp).toggleable(active, enabled = enabled, role = Role.Checkbox) { send(label) }.semantics { stateDescription = hint }, contentAlignment = Alignment.Center) { Text(label) }
                            }
                        } else TerminalExtraKey(label, enabled) { send(label) }
                    }
                }
            }
        }
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth()) {
            KeyRow(layout.always, Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                val expandedState = stringResource(if (expanded) R.string.keys_row_expanded_state else R.string.keys_row_collapsed_state)
                IconButton({ expanded = !expanded }, modifier = Modifier.semantics { stateDescription = expandedState }) {
                    Icon(painterResource(if (expanded) R.drawable.ic_chevron_down else R.drawable.ic_chevron_up), stringResource(if (expanded) R.string.hide_extra_keys else R.string.show_extra_keys))
                }
                Box {
                    IconButton({ menu = true }) { Icon(painterResource(R.drawable.ic_more), stringResource(R.string.terminal_input_menu)) }
                    DropdownMenu(menu, { menu = false }) {
                        if (selection?.isSelectionActive == true) {
                            ActionMenuItem(stringResource(R.string.copy_selection), R.drawable.ic_copy) { menu = false; selection.copySelection(); focus.requestFocus() }
                            ActionMenuItem(stringResource(R.string.close_selection), R.drawable.ic_close) { menu = false; selection.clearSelection(); focus.requestFocus() }
                            HorizontalDivider()
                        }
                        ActionMenuItem(stringResource(R.string.paste_label), R.drawable.ic_paste, enabled) { menu = false; focus.requestFocus(); paste() }
                        ActionMenuItem(stringResource(R.string.select_visible_text), R.drawable.ic_copy, selection != null) { menu = false; selection?.selectAllVisible() }
                        ActionMenuItem(stringResource(R.string.write_command), R.drawable.ic_edit, enabled) { menu = false; command() }
                        ActionMenuItem(stringResource(R.string.commands_title), R.drawable.ic_commands) { menu = false; commands() }
                        ActionMenuItem(stringResource(R.string.customize_quick_keys), R.drawable.ic_keyboard) { menu = false; customizeKeys() }
                        ActionMenuItem(stringResource(R.string.terminal_text_size), R.drawable.ic_palette) { menu = false; sizing = true }
                    }
                }
            }
            AnimatedVisibility(expanded, enter = expandVertically(), exit = shrinkVertically()) {
                if (layout.expanded.isEmpty()) TextButton(customizeKeys, Modifier.fillMaxWidth().height(48.dp)) { Text(stringResource(R.string.customize_quick_keys)) }
                else KeyRow(layout.expanded, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun TerminalExtraKey(label: String, enabled: Boolean, click: () -> Unit) {
    TextButton(modifier = Modifier.widthIn(min = 48.dp).heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 4.dp), enabled = enabled, onClick = click) {
        val arrow = when (label) { "Up" -> R.drawable.ic_key_up; "Down" -> R.drawable.ic_key_down; "Left" -> R.drawable.ic_key_left; "Right" -> R.drawable.ic_key_right; else -> null }
        val description = when (label) { "Up" -> R.string.key_arrow_up; "Down" -> R.string.key_arrow_down; "Left" -> R.string.key_arrow_left; "Right" -> R.string.key_arrow_right; else -> null }
        if (arrow != null && description != null) Icon(painterResource(arrow), stringResource(description), Modifier.size(20.dp))
        else Text(label, maxLines = 1, softWrap = false, style = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr))
    }
}

/** Deliberate command composition shares the same IME-aware surface as saved commands. */
@Composable
internal fun CommandDraftSheet(draft: CommandDraft, identity: String, enabled: Boolean, dismiss: () -> Unit, send: (String) -> Unit) {
    var limitError by remember(draft) { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    CommandSurface(stringResource(R.string.write_command), identity, dismiss) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.command_compose_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(draft.text, {
                limitError = it.toByteArray(Charsets.UTF_8).size > 8192
                if (!limitError) draft.text = it
            }, isError = limitError, supportingText = if (limitError) ({ Text(stringResource(R.string.command_too_large)) }) else null,
                label = { Text(stringResource(R.string.command_label)) }, modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 320.dp).focusRequester(focus), minLines = 3,
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, textDirection = TextDirection.Ltr))
            LaunchedEffect(Unit) { focus.requestFocus() }
            if (!enabled) Text(stringResource(R.string.snippets_running_required), color = MaterialTheme.colorScheme.error)
        }
        Button(onClick = { send(draft.text) }, enabled = enabled && draft.text.isNotBlank(), modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) { Text(stringResource(R.string.review_and_send)) }
    }
}
