// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellRecipeTest {
    private val recipe = ShellRecipe("fixture", "Print input", "printf '%s' \"\$1\"", listOf("Input"))

    @Test fun shellTreatsInputAsData() {
        val value = "single' double\" \$(printf injected); `printf wrong` \\ *"
        val process = ProcessBuilder("/bin/sh", "-c", recipe.command(listOf(value))).start()
        assertEquals(value, process.inputStream.bufferedReader().use { it.readText() })
        assertEquals(0, process.waitFor())
    }

    @Test fun controlsAndWrongArityAreRejected() {
        assertTrue(runCatching { recipe.command(listOf("line\nline")) }.isFailure)
        assertTrue(runCatching { recipe.command(emptyList()) }.isFailure)
    }
}
