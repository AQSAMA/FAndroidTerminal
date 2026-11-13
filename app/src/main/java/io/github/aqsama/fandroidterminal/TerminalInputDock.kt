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

/** Extra keys scroll independently; paste, command entry and keyboard remain in reach. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TerminalInputDock(preferences: Preferences, modifiers: TerminalModifiers, session: TerminalSession,
    selection: SelectionController?, focus: FocusRequester, showKeyboard: (Boolean) -> Unit, paste: () -> Unit, command: () -> Unit) {
    val status by session.status.collectAsStateWithLifecycle()
    val enabled = status == SessionStatus.Running
    val ime = WindowInsets.isImeVisible
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (selection?.isSelectionActive == true) {
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { selection.copySelection() }) { Icon(painterResource(R.drawable.ic_copy), null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.copy_selection)) }
                TextButton(onClick = { selection.selectAllVisible() }) { Text(stringResource(R.string.select_visible_text)) }
                IconButton(onClick = { selection.clearSelection() }) { Icon(painterResource(R.drawable.ic_close), stringResource(R.string.close_selection)) }
            }
        } else {
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                preferences.keys.split(' ').filter { it.isNotBlank() }.forEach { label ->
                    if (label == "Ctrl" || label == "Alt") FilterChip(if (label == "Ctrl") modifiers.ctrl else modifiers.alt,
                        { if (label == "Ctrl") modifiers.ctrl = !modifiers.ctrl else modifiers.alt = !modifiers.alt }, enabled = enabled, label = { Text(label) })
                    else TextButton(enabled = enabled, onClick = {
                        val code = when (label) {
                            "Esc" -> VTermKey.ESCAPE; "Tab" -> VTermKey.TAB; "Up" -> VTermKey.UP; "Down" -> VTermKey.DOWN
                            "Left" -> VTermKey.LEFT; "Right" -> VTermKey.RIGHT; "Home" -> VTermKey.HOME; "End" -> VTermKey.END
                            "PgUp" -> VTermKey.PAGEUP; "PgDn" -> VTermKey.PAGEDOWN; "Insert" -> VTermKey.INS; else -> VTermKey.DEL
                        }
                        session.emulator.dispatchKey(modifiers.mask(), code); modifiers.clearTransients(); focus.requestFocus()
                    }) { Text(when (label) { "Up" -> "↑"; "Down" -> "↓"; "Left" -> "←"; "Right" -> "→"; else -> label }, modifier = Modifier.semantics { contentDescription = label }, style = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr)) }
                }
            }
        }
            VerticalDivider(Modifier.height(24.dp))
            Box {
                var textMenu by remember { mutableStateOf(false) }
                IconButton(onClick = { textMenu = true }, enabled = selection != null) { Icon(painterResource(R.drawable.ic_edit), stringResource(R.string.text_actions)) }
                DropdownMenu(textMenu, { textMenu = false }) {
                    ActionMenuItem(stringResource(R.string.select_visible_text), R.drawable.ic_copy) { textMenu = false; selection?.selectAllVisible() }
                    ActionMenuItem(stringResource(R.string.write_command), R.drawable.ic_commands, enabled = enabled) { textMenu = false; command() }
                }
            }
            IconButton(onClick = { focus.requestFocus(); paste() }, enabled = enabled) { Icon(painterResource(R.drawable.ic_paste), stringResource(R.string.paste_label)) }
            IconButton(onClick = { if (!ime) focus.requestFocus(); showKeyboard(!ime) }, enabled = enabled) { Icon(painterResource(if (ime) R.drawable.ic_keyboard_hide else R.drawable.ic_keyboard), stringResource(if (ime) R.string.hide_keyboard else R.string.show_keyboard)) }
        }
    }
}

/** Optional editor-like entry, inspired by Terax; direct PTY input remains the default. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CommandDraftSheet(draft: CommandDraft, identity: String, enabled: Boolean, dismiss: () -> Unit, send: (String) -> Unit) {
    var limitError by remember(draft) { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = dismiss) {
        Column(Modifier.fillMaxWidth().heightIn(max = 640.dp).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SheetHeader(stringResource(R.string.write_command), dismiss)
            Text(identity, style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.command_draft_help), style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(draft.text, {
                limitError = it.toByteArray(Charsets.UTF_8).size > 8192
                if (!limitError) draft.text = it
            }, isError = limitError,
                supportingText = if (limitError) ({ Text(stringResource(R.string.command_too_large)) }) else null,
                label = { Text(stringResource(R.string.command_label)) }, modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp), minLines = 2,
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, textDirection = TextDirection.Ltr))
            Button(onClick = { send(draft.text) }, enabled = enabled && draft.text.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Icon(painterResource(R.drawable.ic_terminal), null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.review_and_send)) }
            if (!enabled) Text(stringResource(R.string.snippets_running_required), color = MaterialTheme.colorScheme.error)
            Text(stringResource(R.string.command_draft_retained), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
