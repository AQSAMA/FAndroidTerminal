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
    fun task(work: suspend () -> String) { scope.launch { busy = true; try { message = work(); revision++ } catch (e: CancellationException) { throw e } catch (e: Exception) { message = e.message } finally { busy = false } } }
    Text(stringResource(R.string.working_mode), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.android_bridge_help))
    Text("/android/system/bin/env -i PATH=/system/bin:/vendor/bin ANDROID_ROOT=/system ANDROID_DATA=/data /android/system/bin/sh", style = MaterialTheme.typography.bodySmall)
    Text(stringResource(R.string.execution_boundary), style = MaterialTheme.typography.bodyMedium)
    Text(root ?: stringResource(if (access.rootBinary() == null) R.string.root_unavailable else R.string.root_not_authorized))
    OutlinedButton(enabled = !busy && access.rootBinary() != null, onClick = { task { access.authorizeRoot(); root = resources.getString(R.string.root_authorized); root!! } }) { Text(stringResource(R.string.root_check)) }
    Text(key(revision) { access.shizukuStatus() })
    OutlinedButton(enabled = !busy, onClick = { task { access.authorizeShizuku(); access.shizukuStatus() } }) { Text(stringResource(R.string.shizuku_connect)) }
    installed.forEach { environment ->
        OutlinedButton(enabled = !busy, onClick = { task { access.repairOwnership(environment) } }) { Text(stringResource(R.string.root_recover, environment.name)) }
    }
    HorizontalDivider()
    Text(stringResource(R.string.shared_workspace), style = MaterialTheme.typography.titleMedium)
    Text(SharedWorkspace.folder.path, style = MaterialTheme.typography.bodySmall)
    Text(stringResource(R.string.shared_boundary))
    val inspecting = androidx.compose.ui.platform.LocalInspectionMode.current
    // Layoutlib has no Android storage service. Preview the ungranted state safely.
    val permitted = key(revision, inspecting) { !inspecting && workspace.permitted() }
    if (!permitted) OutlinedButton(onClick = {
        if (Build.VERSION.SDK_INT >= 30) {
            runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))) }
                .onFailure { message = it.message }
        } else permission.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }) { Text(stringResource(R.string.shared_permission)) }
    Button(enabled = permitted && !busy, onClick = { task { withContext(Dispatchers.IO) { workspace.create() }; resources.getString(R.string.shared_ready) } }) { Text(stringResource(R.string.shared_create)) }
    (listOf(null to stringResource(R.string.android_shell)) + installed.map { it.id to it.name }).forEach { (id, name) ->
        val grant = key(revision, id) { workspace.grant(id) }
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text(name, style = MaterialTheme.typography.titleSmall)
            Row { Text(stringResource(R.string.shared_enable), Modifier.weight(1f)); Switch(grant.enabled, enabled = !busy, onCheckedChange = { enabled -> task { withContext(Dispatchers.IO) { workspace.save(id, enabled, grant.start) }; resources.getString(R.string.shared_next_sessions) } }) }
            if (grant.enabled) Row { Text(stringResource(R.string.shared_start), Modifier.weight(1f)); Switch(grant.start, enabled = !busy, onCheckedChange = { start -> task { withContext(Dispatchers.IO) { workspace.save(id, true, start) }; resources.getString(R.string.shared_next_sessions) } }) }
        }
    }
    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
}
