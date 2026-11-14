// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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

/** Modifiers stay visible while navigation keys scroll; one input menu owns text operations. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun TerminalInputDock(preferences: Preferences, modifiers: TerminalModifiers, session: TerminalSession,
    selection: SelectionController?, focus: FocusRequester, showKeyboard: (Boolean) -> Unit, paste: () -> Unit, command: () -> Unit, commands: () -> Unit) {
    val status by session.status.collectAsStateWithLifecycle()
    val enabled = status == SessionStatus.Running
    val ime = WindowInsets.isImeVisible
    val keys = preferences.keys.split(' ').filter { it.isNotBlank() }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selection?.isSelectionActive == true) {
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { selection.copySelection(); focus.requestFocus() }) { Icon(painterResource(R.drawable.ic_copy), null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.copy_selection)) }
                    TextButton(onClick = { selection.selectAllVisible() }) { Text(stringResource(R.string.select_visible_text)) }
                }
                IconButton(onClick = { selection.clearSelection(); focus.requestFocus() }) { Icon(painterResource(R.drawable.ic_close), stringResource(R.string.close_selection)) }
            } else {
                listOf("Ctrl", "Alt").filter { it in keys }.forEach { label ->
                    FilterChip(if (label == "Ctrl") modifiers.ctrl else modifiers.alt,
                        { if (label == "Ctrl") modifiers.ctrl = !modifiers.ctrl else modifiers.alt = !modifiers.alt; focus.requestFocus() },
                        enabled = enabled, modifier = Modifier.heightIn(min = 48.dp).widthIn(min = 48.dp), label = { Text(label) })
                }
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                    keys.filter { it !in listOf("Ctrl", "Alt") }.forEach { label ->
                        TextButton(modifier = Modifier.widthIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 8.dp), enabled = enabled, onClick = {
                            val code = when (label) {
                                "Esc" -> VTermKey.ESCAPE; "Tab" -> VTermKey.TAB; "Up" -> VTermKey.UP; "Down" -> VTermKey.DOWN
                                "Left" -> VTermKey.LEFT; "Right" -> VTermKey.RIGHT; "Home" -> VTermKey.HOME; "End" -> VTermKey.END
                                "PgUp" -> VTermKey.PAGEUP; "PgDn" -> VTermKey.PAGEDOWN; "Insert" -> VTermKey.INS; else -> VTermKey.DEL
                            }
                            session.emulator.dispatchKey(modifiers.mask(), code); modifiers.clearTransients(); focus.requestFocus()
                        }) { Text(when (label) { "Up" -> "↑"; "Down" -> "↓"; "Left" -> "←"; "Right" -> "→"; else -> label }, modifier = Modifier.semantics { contentDescription = label }, style = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr)) }
                    }
                }
                Box {
                    var menu by remember { mutableStateOf(false) }
                    TooltipBox(positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(), tooltip = { PlainTooltip { Text(stringResource(R.string.terminal_input_menu)) } }, state = rememberTooltipState()) {
                        IconButton(onClick = { menu = true }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(painterResource(R.drawable.ic_paste), stringResource(R.string.terminal_input_menu), Modifier.size(20.dp))
                                Icon(painterResource(R.drawable.ic_chevron_down), null, Modifier.size(12.dp))
                            }
                        }
                    }
                    DropdownMenu(menu, { menu = false }) {
                        ActionMenuItem(stringResource(R.string.paste_label), R.drawable.ic_paste, enabled = enabled) { menu = false; focus.requestFocus(); paste() }
                        ActionMenuItem(stringResource(R.string.select_visible_text), R.drawable.ic_copy, enabled = selection != null) { menu = false; selection?.selectAllVisible() }
                        HorizontalDivider()
                        ActionMenuItem(stringResource(R.string.write_command), R.drawable.ic_edit, enabled = enabled) { menu = false; command() }
                        ActionMenuItem(stringResource(R.string.commands_title), R.drawable.ic_commands) { menu = false; commands() }
                    }
                }
            }
            IconButton(onClick = { if (!ime) focus.requestFocus(); showKeyboard(!ime) }, enabled = enabled) { Icon(painterResource(if (ime) R.drawable.ic_keyboard_hide else R.drawable.ic_keyboard), stringResource(if (ime) R.string.hide_keyboard else R.string.show_keyboard)) }
        }
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
