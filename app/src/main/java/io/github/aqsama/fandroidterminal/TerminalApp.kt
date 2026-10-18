// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aqsama.fandroidterminal.designsystem.ConnectedGroup
import io.github.aqsama.fandroidterminal.designsystem.FloatingControls
import io.github.aqsama.fandroidterminal.sessions.SessionStatus
import io.github.aqsama.fandroidterminal.sessions.TerminalService
import io.github.aqsama.fandroidterminal.sessions.TerminalSession
import io.github.aqsama.fandroidterminal.terminal.TerminalSurface
import kotlinx.coroutines.launch
import org.connectbot.terminal.ModifierManager
import org.connectbot.terminal.VTermKey

private class TerminalModifiers : ModifierManager {
    var ctrl by mutableStateOf(false)
    var alt by mutableStateOf(false)
    override fun isCtrlActive() = ctrl
    override fun isAltActive() = alt
    override fun isShiftActive() = false
    override fun clearTransients() { ctrl = false; alt = false }
    fun mask() = (if (ctrl) 4 else 0) or (if (alt) 2 else 0)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TerminalApp(service: TerminalService?) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var showInfo by rememberSaveable { mutableStateOf(false) }
    var showLicenses by rememberSaveable { mutableStateOf(false) }
    val sessions = if (service != null) service.sessions.sessions.collectAsStateWithLifecycle().value else emptyList()
    val selected = sessions.firstOrNull { it.id == selectedId } ?: sessions.lastOrNull()
    LaunchedEffect(selected?.id) { selectedId = selected?.id }
    fun paste() {
        val text = context.getSystemService(ClipboardManager::class.java).primaryClip
            ?.let { if (it.itemCount > 0) it.getItemAt(0).text?.toString() else null } ?: return
        if (text.length > 1_048_576) scope.launch { snackbar.showSnackbar("Paste up to 1 MiB at a time") }
        else selected?.emulator?.pasteText(text)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Terminal", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
                    Text("Android shell", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { paste() }) { Icon(painterResource(R.drawable.ic_paste), "Paste") }
                IconButton(onClick = { showInfo = true }) { Icon(painterResource(R.drawable.ic_info), "Session and build information") }
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                sessions.forEach { session ->
                    FilterChip(
                        selected = session.id == selected?.id,
                        onClick = { selectedId = session.id },
                        label = { Text(session.title) },
                        trailingIcon = {
                            IconButton(onClick = { service?.sessions?.remove(session.id) }, modifier = Modifier.size(40.dp)) {
                                Icon(painterResource(R.drawable.ic_close), "Close ${session.title}", Modifier.size(18.dp))
                            }
                        },
                    )
                }
                IconButton(onClick = { TerminalService.newSession(context) }, enabled = sessions.size < 12) {
                    Icon(painterResource(R.drawable.ic_add), "New session")
                }
            }
            if (selected != null) {
                SessionBody(selected, Modifier.weight(1f).padding(horizontal = 8.dp), onPaste = { paste() })
            } else {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (service == null) CircularProgressIndicator()
                    else Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Your next command starts here", style = MaterialTheme.typography.titleMedium)
                        Button(onClick = { TerminalService.newSession(context) }) { Text("Start a shell") }
                    }
                }
            }
        }
    }
    if (showInfo) {
        ModalBottomSheet(onDismissRequest = { showInfo = false }) {
            Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("This session", style = MaterialTheme.typography.headlineSmall)
                ConnectedGroup {
                    InfoRow("Environment", "Android shell")
                    InfoRow("Permissions", "App permissions")
                }
                Text("Commands run with this app's permissions. Other apps' private files and privileged Android controls require a separate authorized connection.", style = MaterialTheme.typography.bodyMedium)
                HorizontalDivider()
                Text("FAndroidTerminal", style = MaterialTheme.typography.titleLarge)
                InfoRow("Version", BuildConfig.VERSION_NAME)
                InfoRow("Build", "${BuildConfig.VERSION_CODE} / ${BuildConfig.BUILD_COMMIT.take(12)}")
                if (BuildConfig.BUILD_PR.isNotEmpty()) InfoRow("Pull request", "#${BuildConfig.BUILD_PR}")
                Text("GPLv3 · ConnectBot termlib · Termux PTY · June components", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { showInfo = false; showLicenses = true }) { Text("Open source licenses") }
            }
        }
    }
    if (showLicenses) {
        val notices = remember { context.assets.open("open-source-notices.txt").bufferedReader().use { it.readText() } }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showLicenses = false },
            title = { Text("Open source licenses") },
            text = { androidx.compose.foundation.text.selection.SelectionContainer {
                androidx.compose.foundation.layout.Box(Modifier.height(420.dp).verticalScroll(rememberScrollState())) {
                    Text(notices, style = MaterialTheme.typography.bodySmall)
                }
            } },
            confirmButton = { TextButton(onClick = { showLicenses = false }) { Text("Close") } },
        )
    }
}

@Composable
private fun InfoRow(title: String, value: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(title, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value)
        }
    }
}

@Composable
private fun SessionBody(session: TerminalSession, modifier: Modifier, onPaste: () -> Unit) {
    val status by session.status.collectAsStateWithLifecycle()
    val modifiers = remember(session.id) { TerminalModifiers() }
    val keyboard = LocalSoftwareKeyboardController.current
    Column(modifier) {
        when (val current = status) {
            SessionStatus.Starting -> Text("Starting shell…", Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall)
            is SessionStatus.Failed -> Text("Shell unavailable: ${current.message}", Modifier.padding(8.dp), color = MaterialTheme.colorScheme.error)
            is SessionStatus.Exited -> Text("Finished with exit code ${current.code}", Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall)
            SessionStatus.Running -> Spacer(Modifier.height(4.dp))
        }
        Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(androidx.compose.ui.graphics.Color(0xFF141218))) {
            androidx.compose.runtime.key(session.id) {
                TerminalSurface(session.emulator, modifiers, Modifier.fillMaxSize().padding(8.dp), onPaste)
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            FloatingControls {
                fun key(code: Int) { session.emulator.dispatchKey(modifiers.mask(), code); modifiers.clearTransients() }
                TextButton(onClick = { key(VTermKey.ESCAPE) }) { Text("Esc") }
                FilterChip(selected = modifiers.ctrl, onClick = { modifiers.ctrl = !modifiers.ctrl }, label = { Text("Ctrl") })
                FilterChip(selected = modifiers.alt, onClick = { modifiers.alt = !modifiers.alt }, label = { Text("Alt") })
                TextButton(onClick = { key(VTermKey.TAB) }) { Text("Tab") }
                listOf("↑" to VTermKey.UP, "↓" to VTermKey.DOWN, "←" to VTermKey.LEFT, "→" to VTermKey.RIGHT).forEach { (label, code) ->
                    TextButton(onClick = { key(code) }) { Text(label, fontSize = 20.sp) }
                }
                IconButton(onClick = { keyboard?.hide() }) { Icon(painterResource(R.drawable.ic_keyboard_hide), "Hide keyboard") }
            }
        }
    }
}
