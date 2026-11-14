// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal.runtime.linux

/** Pinned ARM64 userlands. Identity is persisted separately from user-supplied names. */
data class LinuxImage(val distribution: String, val name: String, val version: String, val imageId: String,
    val url: String, val sha256: String, val bytes: Long, val format: ArchiveFormat, val shell: String,
    val packageManager: String, val minimumFreeMiB: Int)

object LinuxImages {
    val all = listOf(
        LinuxImage("debian", "Debian", "13.7", DebianImage.id, DebianImage.url, DebianImage.sha256,
            DebianImage.bytes, ArchiveFormat.TAR_XZ, "/bin/bash", "apt", 800),
        LinuxImage("ubuntu", "Ubuntu", "24.04.5", "ubuntu-base-24.04.5-arm64",
            "https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/ubuntu-base-24.04.5-base-arm64.tar.gz",
            "a91d5a93010193712d346d761372b7c9db6dfcf093893161c64ca107f05914f2", 29936675L,
            ArchiveFormat.TAR_GZ, "/bin/bash", "apt", 800),
        LinuxImage("alpine", "Alpine", "3.22.8", "alpine-minirootfs-3.22.8-aarch64",
            "https://dl-cdn.alpinelinux.org/alpine/v3.22/releases/aarch64/alpine-minirootfs-3.22.8-aarch64.tar.gz",
            "36091efacd083c1cf5a8c837272530bff4fc72db1fbfdef559f52de0d890bca4", 3971704L,
            ArchiveFormat.TAR_GZ, "/bin/sh", "apk", 128),
    )
    fun get(id: String): LinuxImage = all.firstOrNull { it.distribution == id }
        ?: throw IllegalArgumentException("Unsupported Linux distribution: $id")
}
