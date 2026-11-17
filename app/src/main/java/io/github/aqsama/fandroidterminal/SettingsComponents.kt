// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/** One hierarchy for settings sections, with details disclosed at the point of use. */
@Composable
internal fun SettingsPanel(title: String, icon: Int, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().animateContentSize()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer) {
                    Icon(painterResource(icon), null, Modifier.padding(10.dp).size(22.dp))
                }
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            }
            content()
        }
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
