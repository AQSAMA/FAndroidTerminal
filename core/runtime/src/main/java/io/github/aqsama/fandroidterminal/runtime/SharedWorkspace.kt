// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal.runtime

import android.content.Context
import android.os.Build
import android.os.Environment
import android.Manifest
import android.content.pm.PackageManager
import java.io.File

data class WorkspaceGrant(val enabled: Boolean = false, val start: Boolean = false)
/** Integration preferences are per persistent recipient, never per terminal session. */
class SharedWorkspace(context: Context) {
    private val context = context.applicationContext
    private val store = context.getSharedPreferences("shared-workspace", Context.MODE_PRIVATE)
    fun grant(environmentId: String?) = WorkspaceGrant(store.getBoolean("${environmentId ?: "android"}.enabled", false), store.getBoolean("${environmentId ?: "android"}.start", false))
    fun save(environmentId: String?, enabled: Boolean, start: Boolean) {
        check(store.edit().putBoolean("${environmentId ?: "android"}.enabled", enabled)
            .putBoolean("${environmentId ?: "android"}.start", enabled && start).commit())
    }
    fun permitted() = if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    fun create() { check(permitted()) { "Allow shared storage access first" }; check(folder.isDirectory || folder.mkdirs()) { "Cannot create shared workspace" } }
    fun folderFor(environmentId: String?): File? {
        if (!grant(environmentId).enabled) return null
        check(permitted() && folder.isDirectory && folder.canRead() && folder.canWrite()) { "Shared storage access was revoked or the folder is missing. Restore access in Working mode or disable sharing." }
        return folder
    }
    companion object { val folder = File("/storage/emulated/0/FAndroidTerminal/shared") }
}
