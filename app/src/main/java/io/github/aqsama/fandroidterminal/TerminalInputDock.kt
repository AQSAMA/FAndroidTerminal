// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.aqsama.fandroidterminal.runtime.QuickKeyLayout
import io.github.aqsama.fandroidterminal.sessions.SessionStatus
import org.connectbot.terminal.ModifierManager
import org.connectbot.terminal.SelectionController
import org.connectbot.terminal.VTermKey

/** Ctrl/Alt from the dock: one tap arms the next key, a second quick tap locks it until tapped again. */
internal class TerminalModifiers : ModifierManager {
    var ctrl by mutableStateOf(KeyState.Off); var alt by mutableStateOf(KeyState.Off)
    enum class KeyState { Off, Armed, Locked }
    private var lastTap = 0L to ""
    fun tap(name: String) {
        val now = System.currentTimeMillis()
        val quick = lastTap.second == name && now - lastTap.first < 400
        lastTap = now to name
        fun next(current: KeyState) = when (current) { KeyState.Off -> KeyState.Armed; KeyState.Armed -> if (quick) KeyState.Locked else KeyState.Off; KeyState.Locked -> KeyState.Off }
        if (name == "Ctrl") ctrl = next(ctrl) else alt = next(alt)
    }
    override fun isCtrlActive() = ctrl != KeyState.Off
    override fun isAltActive() = alt != KeyState.Off
    override fun isShiftActive() = false
    override fun clearTransients() { if (ctrl == KeyState.Armed) ctrl = KeyState.Off; if (alt == KeyState.Armed) alt = KeyState.Off }
    fun mask() = (if (isCtrlActive()) 4 else 0) or (if (isAltActive()) 2 else 0)
}

@Composable
internal fun SessionStatusMark(status: SessionStatus, modifier: Modifier = Modifier) {
    val color = when (status) {
        SessionStatus.Running -> MaterialTheme.colorScheme.primary
        is SessionStatus.Failed -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outline
    }
    Box(modifier.size(8.dp).background(color, CircleShape))
}

/** Actions the live dock offers next to the keys. The settings preview passes none. */
internal class DockActions(
    val paste: () -> Unit,
    val commands: () -> Unit,
    val compose: () -> Unit,
    val customize: () -> Unit,
    val textSize: () -> Unit,
    val toggleKeyboard: (() -> Unit)? = null,
    val imeVisible: Boolean = false,
)

internal fun quickKeyCode(label: String): Int? = when (label) {
    "Esc" -> VTermKey.ESCAPE; "Tab" -> VTermKey.TAB
    "Up" -> VTermKey.UP; "Down" -> VTermKey.DOWN; "Left" -> VTermKey.LEFT; "Right" -> VTermKey.RIGHT
    "Home" -> VTermKey.HOME; "End" -> VTermKey.END; "PgUp" -> VTermKey.PAGEUP; "PgDn" -> VTermKey.PAGEDOWN
    "Insert" -> VTermKey.INS; "Delete" -> VTermKey.DEL
    else -> null
}

/**
 * Termux-style quick keys: the first row is always visible; the chevron reveals the second row
 * together with Paste, Commands and the dock menu. While text is selected the first row becomes
 * a selection bar, so Copy is one tap away.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TerminalKeyControls(
    layout: QuickKeyLayout, enabled: Boolean, modifiers: TerminalModifiers,
    selection: SelectionController? = null, actions: DockActions? = null, haptics: Boolean = true,
    initiallyExpanded: Boolean = false, send: (String) -> Unit = {},
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    val haptic = LocalHapticFeedback.current
    val press: (String) -> Unit = { label ->
        if (haptics) haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
        if (label == "Ctrl" || label == "Alt") modifiers.tap(label) else send(label)
    }
    val selecting = selection?.isSelectionActive == true
    // The dock is a physical keyboard strip: same order in every locale.
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 2.dp) { CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                AnimatedContent(selecting, Modifier.weight(1f), label = "dock row",
                    transitionSpec = { fadeIn() togetherWith fadeOut() }) { selectionBar ->
                    if (selectionBar && selection != null) SelectionBar(selection, actions)
                    else KeyRow(layout.always, enabled, modifiers, press)
                }
                VerticalDivider(Modifier.height(24.dp))
                actions?.toggleKeyboard?.let { toggle ->
                    IconButton(toggle, Modifier.size(44.dp), enabled = enabled) {
                        Icon(
                            painterResource(if (actions.imeVisible) R.drawable.ic_keyboard_hide else R.drawable.ic_keyboard),
                            stringResource(if (actions.imeVisible) R.string.hide_keyboard else R.string.show_keyboard),
                            Modifier.size(20.dp),
                            tint = if (actions.imeVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                val state = stringResource(if (expanded) R.string.keys_row_expanded_state else R.string.keys_row_collapsed_state)
                IconButton({ expanded = !expanded }, Modifier.size(44.dp).semantics { stateDescription = state }) {
                    Icon(painterResource(if (expanded) R.drawable.ic_chevron_down else R.drawable.ic_chevron_up),
                        stringResource(if (expanded) R.string.hide_extra_keys else R.string.show_extra_keys))
                }
            }
            AnimatedVisibility(expanded, enter = expandVertically(), exit = shrinkVertically()) {
                Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (actions != null) {
                        DockIcon(R.drawable.ic_paste, stringResource(R.string.paste_label), enabled, actions.paste)
                        DockIcon(R.drawable.ic_commands, stringResource(R.string.commands_title), true, actions.commands)
                        VerticalDivider(Modifier.height(24.dp).padding(horizontal = 2.dp))
                    }
                    KeyRow(layout.expanded, enabled, modifiers, press, Modifier.weight(1f))
                    if (actions != null) {
                        VerticalDivider(Modifier.height(24.dp))
                        DockMenu(enabled, selection, actions)
                    }
                }
            }
        }
    } }
}

@Composable
private fun KeyRow(row: List<String>, enabled: Boolean, modifiers: TerminalModifiers, press: (String) -> Unit, modifier: Modifier = Modifier) {
    // Key order is physical, not text: it never mirrors in RTL.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(modifier.fillMaxHeight().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            row.forEach { label -> QuickKey(label, enabled, modifiers, press) }
        }
    }
}

@Composable
private fun QuickKey(label: String, enabled: Boolean, modifiers: TerminalModifiers, press: (String) -> Unit) {
    val state = when (label) { "Ctrl" -> modifiers.ctrl; "Alt" -> modifiers.alt; else -> null }
    val container by animateColorAsState(when (state) {
        TerminalModifiers.KeyState.Armed -> MaterialTheme.colorScheme.secondaryContainer
        TerminalModifiers.KeyState.Locked -> MaterialTheme.colorScheme.primary
        else -> androidx.compose.ui.graphics.Color.Transparent
    }, label = "modifier")
    val content = if (state == TerminalModifiers.KeyState.Locked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val stateText = state?.let { stringResource(when (it) { TerminalModifiers.KeyState.Off -> R.string.modifier_idle; TerminalModifiers.KeyState.Armed -> R.string.modifier_armed; TerminalModifiers.KeyState.Locked -> R.string.modifier_locked }) }
    val arrow = when (label) { "Up" -> R.drawable.ic_key_up; "Down" -> R.drawable.ic_key_down; "Left" -> R.drawable.ic_key_left; "Right" -> R.drawable.ic_key_right; else -> null }
    val description = when (label) { "Up" -> R.string.key_arrow_up; "Down" -> R.string.key_arrow_down; "Left" -> R.string.key_arrow_left; "Right" -> R.string.key_arrow_right; else -> null }?.let { stringResource(it) } ?: label
    Box(Modifier.padding(horizontal = 1.dp).widthIn(min = 38.dp).height(40.dp).background(container, MaterialTheme.shapes.small)
        .combinedClickable(enabled = enabled, onClick = { press(label) })
        .semantics { contentDescription = description; role = Role.Button; stateText?.let { stateDescription = it } }
        .padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
        if (arrow != null) Icon(painterResource(arrow), null, Modifier.size(20.dp), tint = content.copy(alpha = if (enabled) 1f else .38f))
        else Text(label, color = content.copy(alpha = if (enabled) 1f else .38f), maxLines = 1, softWrap = false,
            fontFamily = FontFamily.Monospace, fontWeight = if (state != null) FontWeight.SemiBold else FontWeight.Medium,
            style = MaterialTheme.typography.labelLarge.copy(textDirection = TextDirection.Ltr))
    }
}

@Composable
private fun SelectionBar(selection: SelectionController, actions: DockActions?) {
    Row(Modifier.fillMaxHeight().padding(start = 8.dp).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        FilledTonalButton({ selection.copySelection(); selection.clearSelection() }, contentPadding = PaddingValues(horizontal = 14.dp)) {
            Icon(painterResource(R.drawable.ic_copy), null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.copy_label))
        }
        TextButton({ selection.selectAllVisible() }) { Text(stringResource(R.string.select_visible_short)) }
        if (actions != null) TextButton({ selection.clearSelection(); actions.paste() }) { Text(stringResource(R.string.paste_label)) }
        IconButton({ selection.clearSelection() }) { Icon(painterResource(R.drawable.ic_close), stringResource(R.string.close_selection)) }
    }
}

@Composable
private fun DockIcon(icon: Int, label: String, enabled: Boolean, click: () -> Unit) {
    IconButton(click, enabled = enabled) { Icon(painterResource(icon), label, Modifier.size(20.dp)) }
}

@Composable
private fun DockMenu(enabled: Boolean, selection: SelectionController?, actions: DockActions) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton({ open = true }) { Icon(painterResource(R.drawable.ic_more), stringResource(R.string.terminal_input_menu)) }
        DropdownMenu(open, { open = false }) {
            ActionMenuItem(stringResource(R.string.select_visible_text), R.drawable.ic_copy, selection != null) { open = false; selection?.selectAllVisible() }
            ActionMenuItem(stringResource(R.string.write_command), R.drawable.ic_edit, enabled) { open = false; actions.compose() }
            ActionMenuItem(stringResource(R.string.terminal_text_size), R.drawable.ic_text_size) { open = false; actions.textSize() }
            ActionMenuItem(stringResource(R.string.customize_quick_keys), R.drawable.ic_keyboard) { open = false; actions.customize() }
        }
    }
}

@Composable
internal fun TextSizeDialog(size: Int, change: (Int) -> Unit, dismiss: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, icon = { Icon(painterResource(R.drawable.ic_text_size), null) }, title = { Text(stringResource(R.string.terminal_text_size)) },
        text = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                FilledTonalIconButton({ change(size - 1) }, enabled = size > 8) { Icon(painterResource(R.drawable.ic_minus), stringResource(R.string.smaller_terminal_text)) }
                Text("$size sp", style = MaterialTheme.typography.headlineSmall)
                FilledTonalIconButton({ change(size + 1) }, enabled = size < 28) { Icon(painterResource(R.drawable.ic_add), stringResource(R.string.larger_terminal_text)) }
            }
        }, confirmButton = { TextButton(dismiss) { Text(stringResource(R.string.close_label)) } })
}

/** Deliberate command composition: multi-line text, reviewed before it is sent. */
@Composable
internal fun CommandDraftSheet(draft: CommandDraft, identity: String, enabled: Boolean, dismiss: () -> Unit, send: (String) -> Unit) {
    var limitError by remember(draft) { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    CommandSurface(stringResource(R.string.write_command), identity, dismiss) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
