// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Command input owns a window that resizes around the IME, with context outside scrolling content. */
@Composable
internal fun CommandSurface(title: String, identity: String, dismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding(), contentAlignment = Alignment.TopCenter) {
            val showHeading = maxHeight >= 320.dp
            Surface(Modifier.widthIn(max = 640.dp).fillMaxSize().semantics { paneTitle = title }, color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Row(Modifier.heightIn(min = if (showHeading) 56.dp else 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            if (showHeading) Text(title, style = MaterialTheme.typography.titleMedium)
                            Text(identity, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = dismiss) { Icon(painterResource(R.drawable.ic_close), stringResource(R.string.dismiss_command_surface)) }
                    }
                    content()
                }
            }
        }
    }
}
