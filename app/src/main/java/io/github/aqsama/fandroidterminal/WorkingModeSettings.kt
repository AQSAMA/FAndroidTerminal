// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aqsama.fandroidterminal.runtime.SharedWorkspace
import io.github.aqsama.fandroidterminal.runtime.linux.LinuxEnvironment
import io.github.aqsama.fandroidterminal.sessions.ExecutionAccess
import kotlinx.coroutines.*

@Composable
internal fun WorkingModeSettings(installed: List<LinuxEnvironment>) {
    val context = LocalContext.current
    val resources = androidx.compose.ui.platform.LocalResources.current
    val access = remember { ExecutionAccess(context) }
    val workspace = remember { SharedWorkspace(context) }
    val scope = rememberCoroutineScope()
    var revision by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var root by remember { mutableStateOf<String?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { revision++ }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) { revision++; onPauseOrDispose {} }
    var feedbackSection by remember { mutableIntStateOf(0) }
    fun task(section: Int = 0, work: suspend () -> String) { scope.launch { feedbackSection = section; message = null; busy = true; try { message = work(); revision++ } catch (e: CancellationException) { throw e } catch (e: Exception) { message = e.message } finally { busy = false } } }
    @Composable fun Feedback(section: Int) {
        if (feedbackSection == section) Column(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
    SettingsPanel(stringResource(R.string.execution_access), R.drawable.ic_shield) {
        Text(stringResource(R.string.mode_short_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.device_root), style = MaterialTheme.typography.titleSmall)
                Text(root ?: stringResource(if (access.rootBinary() == null) R.string.root_unavailable else R.string.root_not_authorized), style = MaterialTheme.typography.bodySmall)
                FilledTonalButton(enabled = !busy && access.rootBinary() != null, onClick = { task { access.authorizeRoot(); root = resources.getString(R.string.root_authorized); root!! } }) { Text(stringResource(R.string.authorize_root_short)) }
            }
        }
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Shizuku", style = MaterialTheme.typography.titleSmall)
                Text(key(revision) { access.shizukuStatus() }, style = MaterialTheme.typography.bodySmall)
                FilledTonalButton(enabled = !busy, onClick = { task { access.authorizeShizuku(); access.shizukuStatus() } }) { Text(stringResource(R.string.connect_shizuku_short)) }
            }
        }
        SettingsDetails(stringResource(R.string.mode_details)) {
            Text(stringResource(R.string.execution_boundary), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.android_bridge_help), style = MaterialTheme.typography.bodySmall)
            androidx.compose.foundation.text.selection.SelectionContainer {
                Text("/android/system/bin/env -i PATH=/system/bin:/vendor/bin ANDROID_ROOT=/system ANDROID_DATA=/data /android/system/bin/sh", style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, textDirection = androidx.compose.ui.text.style.TextDirection.Ltr))
            }
        }
        if (installed.isNotEmpty()) SettingsDetails(stringResource(R.string.ownership_recovery)) {
            installed.forEach { environment ->
                ListItem(colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    headlineContent = { Text(environment.name, style = MaterialTheme.typography.bodyMedium) },
                    trailingContent = { TextButton(enabled = !busy, onClick = { task { access.repairOwnership(environment) } }) { Text(stringResource(R.string.repair_short)) } })
            }
        }
    }
    Feedback(0)
    SettingsPanel(stringResource(R.string.shared_workspace), R.drawable.ic_files) {
        Text(stringResource(R.string.shared_short_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val inspecting = androidx.compose.ui.platform.LocalInspectionMode.current
        // Layoutlib has no Android storage service. Preview the ungranted state safely.
        val permitted = key(revision, inspecting) { !inspecting && workspace.permitted() }
        if (!permitted) OutlinedButton(onClick = {
            if (Build.VERSION.SDK_INT >= 30) {
                runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))) }
                    .onFailure { feedbackSection = 1; message = it.message }
            } else permission.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }) { Text(stringResource(R.string.shared_permission)) }
        if (permitted) FilledTonalButton(enabled = !busy, onClick = { task(1) { withContext(Dispatchers.IO) { workspace.create() }; resources.getString(R.string.shared_ready) } }) { Text(stringResource(R.string.shared_create)) }
        (listOf(null to stringResource(R.string.android_shell)) + installed.map { it.id to it.name }).forEach { (id, name) ->
            val grant = key(revision, id) { workspace.grant(id) }
            Column {
                ListItem(colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), headlineContent = { Text(name, style = MaterialTheme.typography.titleSmall) },
                    supportingContent = { Text(stringResource(R.string.shared_enable), style = MaterialTheme.typography.bodySmall) },
                    trailingContent = { Switch(grant.enabled, enabled = !busy, onCheckedChange = { enabled -> task(1) { withContext(Dispatchers.IO) { workspace.save(id, enabled, grant.start) }; resources.getString(R.string.shared_next_sessions) } }) })
                androidx.compose.animation.AnimatedVisibility(grant.enabled) {
                    ListItem(colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), headlineContent = { Text(stringResource(R.string.shared_start), style = MaterialTheme.typography.bodyMedium) },
                        trailingContent = { Switch(grant.start, enabled = !busy, onCheckedChange = { start -> task(1) { withContext(Dispatchers.IO) { workspace.save(id, true, start) }; resources.getString(R.string.shared_next_sessions) } }) })
                }
            }
        }
        SettingsDetails(stringResource(R.string.shared_details)) {
            Text(SharedWorkspace.folder.path, style = MaterialTheme.typography.bodySmall.copy(textDirection = androidx.compose.ui.text.style.TextDirection.Ltr))
            Text(stringResource(R.string.shared_boundary), style = MaterialTheme.typography.bodySmall)
        }
    }
    Feedback(1)
}
