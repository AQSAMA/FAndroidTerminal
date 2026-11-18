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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
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
    val label = statusLabel(status)
    val color = when (status) {
        SessionStatus.Running -> MaterialTheme.colorScheme.primary
        is SessionStatus.Failed -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(Modifier.size(7.dp).background(color, CircleShape).semantics { contentDescription = label })
}

@Composable
internal fun TerminalInputDock(
    preferences: Preferences,
    modifiers: TerminalModifiers,
    session: TerminalSession,
    selection: SelectionController?,
    focus: FocusRequester,
    imeVisible: Boolean,
    toggleKeyboard: () -> Unit,
    openLinks: () -> Unit,
    paste: () -> Unit,
    command: () -> Unit,
    commands: () -> Unit,
    customizeKeys: () -> Unit,
) {
    val status by session.status.collectAsStateWithLifecycle()
    val enabled = status == SessionStatus.Running
    var menu by remember { mutableStateOf(false) }
    var sizing by rememberSaveable { mutableStateOf(false) }
    val linksLabel = stringResource(R.string.studio_output_links)
    if (sizing) AlertDialog(
        onDismissRequest = { sizing = false },
        title = { Text(stringResource(R.string.terminal_text_size)) },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton({ preferences.setTerminalFont(preferences.font - 1) }, enabled = preferences.font > 8) {
                    Icon(painterResource(R.drawable.ic_minus), stringResource(R.string.decrease_text_size))
                }
                Text(stringResource(R.string.font_size_value, preferences.font))
                IconButton({ preferences.setTerminalFont(preferences.font + 1) }, enabled = preferences.font < 28) {
                    Icon(painterResource(R.drawable.ic_add), stringResource(R.string.increase_text_size))
                }
            }
        },
        confirmButton = { TextButton({ sizing = false }) { Text(stringResource(R.string.close_label)) } },
    )
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth()) {
            if (status == SessionStatus.Running) {
                TerminalKeyControls(
                    keys = preferences.keys.split(' '), enabled = enabled, modifiers = modifiers,
                    customizeKeys = customizeKeys, requestInputFocus = { focus.requestFocus() },
                    text = { value ->
                        value.forEach { session.emulator.dispatchCharacter(modifiers.mask(), it.code) }
                        modifiers.clearTransients()
                    },
                ) { code ->
                    session.emulator.dispatchKey(modifiers.mask(), code)
                    modifiers.clearTransients()
                }
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    TextButton(
                        onClick = openLinks,
                        modifier = Modifier.semantics { contentDescription = linksLabel },
                        contentPadding = PaddingValues(horizontal = 6.dp),
                    ) {
                        Icon(painterResource(R.drawable.ic_search), null, Modifier.size(18.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(stringResource(R.string.studio_links_short), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                val commandsLabel = stringResource(R.string.studio_recipes)
                TextButton(commands, modifier = Modifier.weight(1f, fill = false)
                    .semantics { contentDescription = commandsLabel }, contentPadding = PaddingValues(horizontal = 6.dp)) {
                    Icon(painterResource(R.drawable.ic_commands), null, Modifier.size(18.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(commandsLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(toggleKeyboard, enabled = enabled) {
                    Icon(painterResource(if (imeVisible) R.drawable.ic_keyboard_hide else R.drawable.ic_keyboard),
                        stringResource(if (imeVisible) R.string.hide_keyboard else R.string.show_keyboard))
                }
                Box {
                    IconButton({ menu = true }) {
                        Icon(painterResource(R.drawable.ic_more), stringResource(R.string.terminal_input_menu))
                    }
                    DropdownMenu(menu, { menu = false }) {
                        if (selection?.isSelectionActive == true) {
                            ActionMenuItem(stringResource(R.string.copy_selection), R.drawable.ic_copy) {
                                menu = false; selection.copySelection()
                            }
                            ActionMenuItem(stringResource(R.string.close_selection), R.drawable.ic_close) {
                                menu = false; selection.clearSelection()
                            }
                            HorizontalDivider()
                        }
                        ActionMenuItem(stringResource(R.string.paste_label), R.drawable.ic_paste, enabled) {
                            menu = false; focus.requestFocus(); paste()
                        }
                        ActionMenuItem(stringResource(R.string.select_visible_text), R.drawable.ic_copy, selection != null) {
                            menu = false; selection?.selectAllVisible()
                        }
                        ActionMenuItem(stringResource(R.string.write_command), R.drawable.ic_edit, enabled) {
                            menu = false; command()
                        }
                        HorizontalDivider()
                        ActionMenuItem(stringResource(R.string.customize_quick_keys), R.drawable.ic_keyboard) {
                            menu = false; customizeKeys()
                        }
                        ActionMenuItem(stringResource(R.string.terminal_text_size), R.drawable.ic_palette) {
                            menu = false; sizing = true
                        }
                    }
                }
            }
        }
    }
}

/** The saved order fills seven-column rows; modifiers remain armed until the next input. */
@Composable
internal fun TerminalKeyControls(
    keys: List<String>,
    enabled: Boolean,
    modifiers: TerminalModifiers,
    customizeKeys: (() -> Unit)? = null,
    requestInputFocus: () -> Unit = {},
    text: (String) -> Unit = {},
    key: (Int) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val layout = remember(keys) { io.github.aqsama.fandroidterminal.runtime.QuickKeyLayout.parse(keys.joinToString(" ")) }
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
        requestInputFocus()
    }
    @Composable fun KeyRow(row: List<String>, modifier: Modifier = Modifier) {
        CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
            Row(modifier.height(48.dp).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                row.forEach { label ->
                    val active = label == "Ctrl" && modifiers.ctrl || label == "Alt" && modifiers.alt
                    if (label == "Ctrl" || label == "Alt") {
                        val hint = stringResource(if (active) R.string.modifier_armed else R.string.modifier_idle)
                        Box(Modifier.size(48.dp).toggleable(active, enabled = enabled, role = Role.Checkbox) { send(label) }
                            .semantics { stateDescription = hint }, contentAlignment = Alignment.Center) {
                            Surface(
                                Modifier.size(42.dp, 32.dp), shape = MaterialTheme.shapes.small,
                                color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(label, style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                    } else TerminalExtraKey(label, enabled) { send(label) }
                }
            }
        }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            KeyRow(layout.always, Modifier.weight(1f))
            val expandedState = stringResource(if (expanded) R.string.keys_row_expanded_state else R.string.keys_row_collapsed_state)
            IconButton({ expanded = !expanded }, modifier = Modifier.semantics { stateDescription = expandedState }) {
                Icon(painterResource(if (expanded) R.drawable.ic_chevron_up else R.drawable.ic_chevron_down),
                    stringResource(if (expanded) R.string.hide_extra_keys else R.string.show_extra_keys))
            }
        }
        AnimatedVisibility(expanded, enter = expandVertically(), exit = shrinkVertically()) {
            if (layout.expanded.isNotEmpty()) KeyRow(layout.expanded, Modifier.fillMaxWidth())
            else if (customizeKeys != null) TextButton(customizeKeys, Modifier.height(48.dp)) {
                Text(stringResource(R.string.customize_quick_keys))
            } else Box(Modifier.height(48.dp))
        }
    }
}

@Composable
private fun TerminalExtraKey(label: String, enabled: Boolean, click: () -> Unit) {
    TextButton(modifier = Modifier.size(48.dp), contentPadding = PaddingValues(0.dp), enabled = enabled, onClick = click) {
        val arrow = when (label) { "Up" -> R.drawable.ic_key_up; "Down" -> R.drawable.ic_key_down; "Left" -> R.drawable.ic_key_left; "Right" -> R.drawable.ic_key_right; else -> null }
        val description = when (label) { "Up" -> R.string.key_arrow_up; "Down" -> R.string.key_arrow_down; "Left" -> R.string.key_arrow_left; "Right" -> R.string.key_arrow_right; else -> null }
        if (arrow != null && description != null) Icon(painterResource(arrow), stringResource(description), Modifier.size(20.dp))
        else Text(label, maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelMedium.copy(
            fontFamily = FontFamily.Monospace, textDirection = TextDirection.Ltr,
        ))
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
