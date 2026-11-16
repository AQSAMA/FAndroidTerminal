// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal.sessions

import android.content.Context
import io.github.aqsama.fandroidterminal.runtime.linux.PackageMetadata

/** Shared workspace preferences; explicit distro override replaces the global package plan. */
class GuestDefaults(context: Context) {
    private val store = context.getSharedPreferences("workspace", Context.MODE_PRIVATE)
    fun profile(distribution: String): String = store.getString("profile.$distribution", null) ?: store.getString("profile.global", "")!!
    fun rawProfile(distribution: String): String? = store.getString("profile.$distribution", null)
    fun rawShell(distribution: String): String? = store.getString("shell.$distribution", null)
    fun rawDirectory(distribution: String): String? = store.getString("directory.$distribution", null)
    fun shell(distribution: String): String? = store.getString("shell.$distribution", null) ?: store.getString("shell.global", null)
    fun directory(distribution: String): String = store.getString("directory.$distribution", null) ?: store.getString("directory.global", "/root")!!
    fun save(distribution: String, profile: String?, shell: String?, directory: String?) {
        require(distribution in listOf("global", "debian", "ubuntu", "alpine"))
        profile?.let { PackageMetadata.names(it) }
        listOfNotNull(shell, directory).forEach { require(it.startsWith('/') && it.length <= 4096 && it.none(Char::isISOControl)) }
        check(store.edit().putString("profile.$distribution", profile).putString("shell.$distribution", shell).putString("directory.$distribution", directory).commit())
    }
}
