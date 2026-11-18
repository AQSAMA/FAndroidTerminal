// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.aqsama.fandroidterminal.sessions.TerminalSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Only opens on request. Terminal-controlled URLs remain text until the user copies one. */
@Composable
internal fun OutputLinks(session: TerminalSession, identity: String, dismiss: () -> Unit) {
    var revision by remember(session.id) { mutableIntStateOf(0) }
    var query by rememberSaveable(session.id) { mutableStateOf("") }
    var copied by remember(session.id) { mutableStateOf<String?>(null) }
    val clipboard = LocalContext.current.getSystemService(ClipboardManager::class.java)
    val links by produceState<List<String>?>(null, session.id, revision) {
        value = null
        value = withContext(Dispatchers.Default) {
            try {
                session.emulator.getUrls().asReversed().asSequence().map { it.url }
                    .filter { it.length <= 2048 && it.none(Char::isISOControl) }.distinct().take(200).toList()
            } catch (_: IllegalStateException) {
                // The service may dispose this session while a refresh is in flight.
                emptyList()
            }
        }
    }
    CommandSurface(stringResource(R.string.studio_output_links), identity, dismiss) {
        Row(Modifier.fillMaxWidth()) {
            OutlinedTextField(query, { query = it }, Modifier.weight(1f), label = { Text(stringResource(R.string.studio_find_link)) }, singleLine = true)
            IconButton({ revision++ }, enabled = links != null) {
                Icon(painterResource(R.drawable.ic_refresh), stringResource(R.string.refresh_label))
            }
        }
        if (links == null) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp))
        else {
            val results = links.orEmpty().filter { it.contains(query, ignoreCase = true) }
            if (results.isEmpty()) Text(stringResource(if (query.isBlank()) R.string.studio_no_links else R.string.studio_no_link_match),
                Modifier.padding(vertical = 18.dp), style = MaterialTheme.typography.bodyMedium)
            LazyColumn(Modifier.weight(1f)) {
                items(results, key = { it }) { url ->
                    ListItem(
                        headlineContent = {
                            Text(url, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                style = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr))
                        },
                        trailingContent = {
                            IconButton({
                                clipboard.setPrimaryClip(ClipData.newPlainText(null, url))
                                copied = url
                            }) {
                                Icon(painterResource(if (copied == url) R.drawable.ic_check else R.drawable.ic_copy),
                                    stringResource(R.string.copy_link))
                            }
                        },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
