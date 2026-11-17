// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal.runtime

enum class ExecutionMode(val identity: String) {
    Android("Android · App UID"), Shizuku("Android · Shizuku"), Root("Android · Device root"),
    PRoot("Linux · PRoot · simulated root"), Chroot("Linux · Root-chroot · Device root");
    val linux get() = this == PRoot || this == Chroot
}
