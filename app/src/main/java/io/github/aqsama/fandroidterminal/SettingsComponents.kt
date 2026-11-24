// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.aqsama.fandroidterminal.designsystem.ConnectedGroup

/** Isolates technical text (versions, paths, names) so Arabic layouts do not reorder its parts. */
internal fun ltr(value: String) = "\u2066$value\u2069"

/** Scrollable settings body, width-limited on tablets. */
@Composable
internal fun SettingsColumn(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp), content = content)
    }
}

/** A titled group of connected rows. */
@Composable
internal fun SettingsGroup(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title != null) Text(title, Modifier.padding(start = 12.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        ConnectedGroup(content = content)
    }
}

private val rowColors @Composable get() = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)

@Composable
internal fun RowIcon(icon: Int) {
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
        Icon(painterResource(icon), null, Modifier.padding(8.dp).size(20.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@Composable
internal fun NavigationRow(icon: Int, title: String, summary: String? = null, onClick: () -> Unit) {
    ListItem(colors = rowColors, modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { RowIcon(icon) },
        headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
        supportingContent = summary?.takeIf { it.isNotBlank() }?.let { { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) } },
        trailingContent = { Icon(painterResource(R.drawable.ic_chevron), null) })
}

@Composable
internal fun SwitchRow(title: String, summary: String? = null, checked: Boolean, icon: Int? = null, enabled: Boolean = true, change: (Boolean) -> Unit) {
    ListItem(colors = rowColors, modifier = Modifier.toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = change),
        leadingContent = icon?.let { { RowIcon(it) } },
        headlineContent = { Text(title) },
        supportingContent = summary?.let { { Text(it) } },
        trailingContent = { Switch(checked, onCheckedChange = null, enabled = enabled) })
}

/** A row whose value is picked from a short menu. */
@Composable
internal fun <T> ChoiceRow(title: String, icon: Int?, choices: List<Pair<T, String>>, value: T, change: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        ListItem(colors = rowColors, modifier = Modifier.clickable { open = true },
            leadingContent = icon?.let { { RowIcon(it) } },
            headlineContent = { Text(title) },
            supportingContent = { Text(choices.firstOrNull { it.first == value }?.second.orEmpty(), color = MaterialTheme.colorScheme.primary) },
            trailingContent = { Icon(painterResource(R.drawable.ic_chevron_down), null) })
        DropdownMenu(open, { open = false }) {
            choices.forEach { (choice, label) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { change(choice); open = false },
                    trailingIcon = if (choice == value) ({ Icon(painterResource(R.drawable.ic_check), null) }) else null)
            }
        }
    }
}

/** Segmented single choice inside a group surface. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> SegmentedRow(title: String, choices: List<Pair<T, String>>, value: T, change: (T) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                choices.forEachIndexed { index, (choice, label) ->
                    SegmentedButton(value == choice, { change(choice) }, SegmentedButtonDefaults.itemShape(index, choices.size)) { Text(label, maxLines = 1) }
                }
            }
        }
    }
}

/** One hierarchy for settings sections, with details disclosed at the point of use. */
@Composable
internal fun SettingsPanel(title: String, icon: Int, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(painterResource(icon), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        }
        content()
    }
}

@Composable
internal fun SettingsDetails(title: String, content: @Composable ColumnScope.() -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column {
        val state = stringResource(if (open) R.string.keys_row_expanded_state else R.string.keys_row_collapsed_state)
        TextButton({ open = !open }, Modifier.fillMaxWidth().semantics { stateDescription = state }) {
            Text(title, Modifier.weight(1f))
            Icon(painterResource(if (open) R.drawable.ic_chevron_up else R.drawable.ic_chevron_down), null)
        }
        AnimatedVisibility(open) { Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content) }
    }
}
