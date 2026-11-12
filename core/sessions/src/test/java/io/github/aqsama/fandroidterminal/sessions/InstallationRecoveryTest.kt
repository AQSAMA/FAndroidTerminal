// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal.sessions

import io.github.aqsama.fandroidterminal.runtime.linux.EnvironmentFiles
import io.github.aqsama.fandroidterminal.runtime.linux.LinuxEnvironment
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class InstallationRecoveryTest {
    @get:Rule val temp = TemporaryFolder()
    @Test fun cancellationBeforePromotionPublishesRetryableStatus() = runBlocking {
        val files = EnvironmentFiles(temp.newFolder())
        val environment = LinuxEnvironment(UUID.randomUUID().toString(), "Debian", "Installing")
        files.save(environment)
        var published: LinuxEnvironment? = null
        val worker = launch {
            currentCoroutineContext().cancel()
            recoverInstallation(environment, CancellationException(), files, {
                withContext(Dispatchers.IO) { files.save(it) }
                published = it
            }, { _, error -> throw AssertionError(error) })
        }
        worker.join()
        assertTrue(published!!.state.startsWith("Cancelled"))
        assertEquals(published, files.load().single())
    }
    @Test fun cancellationAfterPromotionPublishesReady() = runBlocking {
        val files = EnvironmentFiles(temp.newFolder())
        val environment = LinuxEnvironment(UUID.randomUUID().toString(), "Debian", "Installing")
        files.save(environment); files.root(environment.id).mkdirs()
        File(files.root(environment.id), ".fandroid-ready").writeText("verified")
        var published: LinuxEnvironment? = null
        val worker = launch {
            currentCoroutineContext().cancel()
            recoverInstallation(environment, CancellationException(), files, {
                withContext(Dispatchers.IO) { files.save(it) }
                published = it
            }, { _, error -> throw AssertionError(error) })
        }
        worker.join()
        assertTrue(published!!.ready)
        assertTrue(files.load().single().ready)
    }
    @Test fun repeatedDiskFailureReportsWithoutEscapingOrCancellingSibling() = runBlocking {
        val files = EnvironmentFiles(temp.newFolder())
        val environment = LinuxEnvironment(UUID.randomUUID().toString(), "Debian", "Installing")
        var reported = false
        val sibling = launch { awaitCancellation() }
        recoverInstallation(environment, IOException("full disk"), files,
            { throw IOException("still full") }, { result, error ->
                assertTrue(result.state.contains("full disk"))
                assertEquals("still full", error.message)
                reported = true
            })
        assertTrue(reported)
        assertTrue(sibling.isActive)
        sibling.cancelAndJoin()
    }
}
