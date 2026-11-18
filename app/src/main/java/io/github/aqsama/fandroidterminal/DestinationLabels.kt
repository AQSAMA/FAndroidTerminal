// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

internal fun destinationIcon(page: String) = when (page) { "Backup" -> R.drawable.ic_storage; "Launchers" -> R.drawable.ic_prompt; "Packages" -> R.drawable.ic_download; "Terminal" -> R.drawable.ic_shell; "Environments" -> R.drawable.ic_environments; "Files" -> R.drawable.ic_files; else -> R.drawable.ic_settings }
internal fun destinationLabel(page: String) = when (page) { "Backup" -> R.string.backup_title; "Guest defaults" -> R.string.guest_defaults_title; "Launchers" -> R.string.launchers_title; "Packages" -> R.string.packages_title; "Terminal" -> R.string.terminal_label; "Environments" -> R.string.environments_label; "Files" -> R.string.files_label; else -> R.string.settings_label }
