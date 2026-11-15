// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/** Presentation only: persisted state and the installer's stage/progress contract stay unchanged. */
@Composable
internal fun operationLabel(value: String): String = when (value) {
    "Pending" -> stringResource(R.string.provision_pending)
    "Installing packages", "Provisioning packages…" -> stringResource(R.string.provision_running)
    "Complete" -> stringResource(R.string.provision_complete)
    "Skipped" -> stringResource(R.string.provision_skipped)
    "Failed; rootfs ready. Retry or edit packages." -> stringResource(R.string.provision_failed)
    "Cancelled" -> stringResource(R.string.stage_cancelled)
    "Cancelled; rootfs ready. Retry or skip packages." -> stringResource(R.string.provision_cancelled)
    "Reading package metadata…" -> stringResource(R.string.package_read)
    "Discovering shells…" -> stringResource(R.string.discover_shells)
    "Exporting workspace backup…" -> stringResource(R.string.stage_backup)
    "Restoring workspace backup…" -> stringResource(R.string.stage_restore)
    "Ready" -> stringResource(R.string.stage_ready)
    "Installing", "Preparing installation…" -> stringResource(R.string.stage_preparing)
    "Deleting", "Deleting environment files…" -> stringResource(R.string.stage_deleting)
    "Extracting Debian…" -> stringResource(R.string.stage_extracting)
    "Configuring DNS, certificates and storage…" -> stringResource(R.string.stage_configuring)
    "Checking the guest runtime…" -> stringResource(R.string.stage_checking)
    "Downloading Debian (17 MiB)…" -> stringResource(R.string.stage_downloading)
    "Saving settings…", "Saving name…" -> stringResource(R.string.saving_settings)
    "Cloning…" -> stringResource(R.string.stage_cloning)
    "Exporting backup…" -> stringResource(R.string.stage_backup)
    "Restoring backup…" -> stringResource(R.string.stage_restore)
    "Interrupted", "Interrupted — retry installation" -> stringResource(R.string.stage_interrupted)
    "Cancelled — retry when ready" -> stringResource(R.string.stage_cancelled)
    "Operation cancelled. Completed deletions cannot be undone; partial exports can be discarded." -> stringResource(R.string.operation_cancelled)
    else -> when {
        value.startsWith("Packages: ") -> stringResource(when (value.removePrefix("Packages: ")) { "Read" -> R.string.package_read; "Refresh" -> R.string.package_refresh; "Repair" -> R.string.package_repair; "Review upgrades", "Upgrade" -> R.string.package_upgrade; else -> R.string.packages_title })
        value.startsWith("Package command exited ") || value.startsWith("Package manager exited ") -> stringResource(R.string.package_failed_help)
        value.startsWith("Close this environment") || value.startsWith("Close selected environments") -> stringResource(R.string.package_close_sessions)
        value.startsWith("Environment is busy") || value.startsWith("Wait for the current operation") -> stringResource(R.string.operation_busy)
        value.startsWith("Tool unavailable") || value.startsWith("Launcher directory unavailable") || value.startsWith("Launcher environment unavailable") -> stringResource(R.string.launcher_repair_help)
        value.startsWith("Downloading ") && ": " in value && value.endsWith("%") -> stringResource(R.string.stage_download_progress, value.substringAfter(": "))
        value.startsWith("Extracting ") -> stringResource(R.string.stage_extracting)
        value.startsWith("Downloading ") -> stringResource(R.string.stage_downloading)
        value.startsWith("Backing up ") -> stringResource(R.string.stage_backup_file, value.removePrefix("Backing up "))
        value.startsWith("Restoring ") -> stringResource(R.string.stage_restore_file, value.removePrefix("Restoring "))
        value.endsWith(". Check storage and retry.") -> value.removeSuffix(". Check storage and retry.") + "\n" + stringResource(R.string.operation_retry_storage)
        value.endsWith(". Check storage/network and retry.") -> value.removeSuffix(". Check storage/network and retry.") + "\n" + stringResource(R.string.operation_retry_network)
        else -> value // Preserve unexpected or technical diagnostics verbatim.
    }
}
