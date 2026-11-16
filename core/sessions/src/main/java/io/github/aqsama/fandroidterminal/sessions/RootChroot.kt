// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal.sessions

/** Invoked only through su/app_process, never inside the application process. */
object RootChroot {
    @JvmStatic external fun run(root: String, uid: Int, shared: String, directory: String, shell: String, variables: Array<String>): Int
    @JvmStatic fun main(args: Array<String>) {
        System.load(args[0])
        kotlin.system.exitProcess(run(args[1], args[2].toInt(), args[3], args[4], args[5], args.drop(6).toTypedArray()))
    }
}
