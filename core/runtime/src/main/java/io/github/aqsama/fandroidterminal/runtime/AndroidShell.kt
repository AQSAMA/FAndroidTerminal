// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal.runtime

import java.io.File

object AndroidShell {
    fun specification(home: File, cache: File): ShellSpec {
        require(home.isDirectory && cache.isDirectory) { "Shell directories must exist" }
        return ShellSpec(
            executable = "/system/bin/sh",
            arguments = listOf("/system/bin/sh", "-i"),
            cwd = home.absolutePath,
            environment = mapOf(
                "HOME" to home.absolutePath,
                "TMPDIR" to cache.absolutePath,
                "PATH" to "/system/bin:/system/xbin:/vendor/bin",
                "TERM" to "xterm-256color",
                "COLORTERM" to "truecolor",
                "LANG" to "C.UTF-8",
                "SHELL" to "/system/bin/sh",
                "PS1" to "android\$ ",
                "ANDROID_ROOT" to "/system",
                "ANDROID_DATA" to "/data",
                "ANDROID_RUNTIME_ROOT" to "/apex/com.android.runtime",
                "ANDROID_TZDATA_ROOT" to "/apex/com.android.tzdata",
                "ANDROID_I18N_ROOT" to "/apex/com.android.i18n",
            ),
        )
    }
}
