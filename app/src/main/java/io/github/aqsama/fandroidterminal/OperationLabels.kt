// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/** Presentation only: persisted state and the installer's stage/progress contract stay unchanged. */
@Composable
internal fun operationLabel(value: String): String = when (value) {
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
        value.startsWith("Downloading Debian: ") -> stringResource(R.string.stage_download_progress, value.removePrefix("Downloading Debian: "))
        value.startsWith("Backing up ") -> stringResource(R.string.stage_backup_file, value.removePrefix("Backing up "))
        value.startsWith("Restoring ") -> stringResource(R.string.stage_restore_file, value.removePrefix("Restoring "))
        value.endsWith(". Check storage and retry.") -> value.removeSuffix(". Check storage and retry.") + "\n" + stringResource(R.string.operation_retry_storage)
        value.endsWith(". Check storage/network and retry.") -> value.removeSuffix(". Check storage/network and retry.") + "\n" + stringResource(R.string.operation_retry_network)
        else -> value // Preserve unexpected or technical diagnostics verbatim.
    }
}
