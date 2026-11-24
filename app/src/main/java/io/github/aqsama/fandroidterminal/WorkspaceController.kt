// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.*
import io.github.aqsama.fandroidterminal.runtime.ExecutionMode
import io.github.aqsama.fandroidterminal.runtime.SharedWorkspace
import io.github.aqsama.fandroidterminal.runtime.linux.CliLauncher
import io.github.aqsama.fandroidterminal.sessions.ExecutionAccess
import io.github.aqsama.fandroidterminal.sessions.SessionStatus
import io.github.aqsama.fandroidterminal.sessions.TerminalService
import io.github.aqsama.fandroidterminal.sessions.TerminalSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Modal surfaces above the workspace. Only one is shown at a time. */
sealed interface Overlay {
    data object Commands : Overlay
    data object ComposeCommand : Overlay
    data class Close(val sessionId: String) : Overlay
    data class Rename(val sessionId: String) : Overlay
    /** Sessions that hold an environment's files or settings lock. */
    data class Owners(val environmentId: String?) : Overlay
    /** Text waiting for confirmation before it reaches a terminal. */
    data class Send(val sessionId: String, val text: String, val run: Boolean, val fromDraft: Boolean) : Overlay
    data object Licenses : Overlay
    /** A link tapped in terminal output; never opened without confirmation. */
    data class OpenLink(val url: String) : Overlay
}

const val SESSION_LIMIT = 12

/**
 * Session operations used by every surface (top bar, tabs, drawer, environments). The service owns
 * the sessions; this controller only chooses which one is shown and asks the service for new ones.
 */
@Stable
class WorkspaceController(
    private val context: Context,
    private val scope: CoroutineScope,
    val preferences: Preferences,
    val navigator: Navigator,
    private val state: WorkspaceState,
) {
    var service by mutableStateOf<TerminalService?>(null)
    private var selection by mutableStateOf(state.selectedSessionId)
    var selectedId: String?
        get() = selection
        set(value) { selection = value; state.selectedSessionId = value }
    var overlay by mutableStateOf<Overlay?>(null)
    var error by mutableStateOf<String?>(null)
    /** Incremented to ask the phone drawer to open (the drawer state lives in the shell). */
    var drawerRequests by mutableIntStateOf(0)

    private val resources get() = context.resources
    private val sessionList get() = service?.sessions?.sessions?.value.orEmpty()

    fun session(id: String?) = sessionList.firstOrNull { it.id == id }

    /** Visible selection: the remembered session, else the newest unarchived one. */
    fun selected(sessions: List<TerminalSession>): TerminalSession? {
        val active = sessions.filterNot { it.archived }
        val previous = sessions.firstOrNull { it.id == selectedId }
        return active.firstOrNull { it.id == selectedId }
            ?: active.lastOrNull { it.environmentId == previous?.environmentId }
            ?: active.lastOrNull()
    }

    fun environmentName(session: TerminalSession): String =
        service?.environments?.environments?.value?.firstOrNull { it.id == session.environmentId }?.name
            ?: if (session.environmentId == null) resources.getString(R.string.android_shell) else "Linux"

    fun identity(session: TerminalSession) = resources.getString(R.string.session_identity, session.title, environmentName(session))

    fun select(session: TerminalSession) {
        service?.sessions?.archive(session.id, false)
        selectedId = session.id
        overlay = null
        navigator.home()
    }

    fun cycle(step: Int) {
        val active = sessionList.filterNot { it.archived }
        if (active.isEmpty()) return
        val index = active.indexOfFirst { it.id == selected(sessionList)?.id }.coerceAtLeast(0)
        selectedId = active[(index + step + active.size) % active.size].id
    }

    private fun busy(id: String?): Boolean {
        val progress = service?.environments?.progress?.value.orEmpty()
        return id != null && (id in progress || "workspace-backup" in progress)
    }

    private fun chrootConflict(id: String?, mode: ExecutionMode) = sessionList.any {
        it.environmentId == id && ((it.mode == ExecutionMode.Chroot) != (mode == ExecutionMode.Chroot))
    }

    /** Opens a session in [environmentId] (null = Android). Defaults to the selected session's mode there. */
    fun open(environmentId: String?, directory: String? = null, mode: ExecutionMode? = null) {
        val owner = service ?: run { error = resources.getString(R.string.status_starting); return }
        if (sessionList.size >= SESSION_LIMIT) { error = resources.getString(R.string.session_limit_reached, SESSION_LIMIT); drawerRequests++; return }
        val current = selected(sessionList)
        val chosen = mode ?: current?.takeIf { it.environmentId == environmentId }?.mode
            ?: if (environmentId == null) ExecutionMode.Android else ExecutionMode.PRoot
        scope.launch {
            try {
                check(!busy(environmentId)) { resources.getString(R.string.environment_busy) }
                check(!chrootConflict(environmentId, chosen)) { resources.getString(R.string.chroot_switch_blocked) }
                val access = ExecutionAccess(context)
                val sharing = SharedWorkspace(context)
                val session = when {
                    chosen == ExecutionMode.Shizuku -> {
                        access.authorizeShizuku()
                        val shared = withContext(Dispatchers.IO) { sharing.folderFor(null) }
                        TerminalService.ensureStarted(context)
                        owner.sessions.create(mode = chosen, transport = {
                            access.shizukuTransport(if (shared != null && sharing.grant(null).start) shared.path else null).first
                        }).also { it.executionIdentity = "Android · Shizuku · UID ${rikka.shizuku.Shizuku.getUid()}" }
                    }
                    environmentId != null -> {
                        val spec = owner.environments.specification(environmentId, directory, chosen == ExecutionMode.Chroot)
                        TerminalService.ensureStarted(context)
                        owner.sessions.create(environmentId, "Terminal", spec, chosen)
                    }
                    else -> {
                        val spec = access.androidSpec(chosen, sharing)
                        TerminalService.ensureStarted(context)
                        owner.sessions.create(null, "Shell", spec, chosen)
                    }
                }
                selectedId = session.id; overlay = null; error = null
                navigator.home()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message }
        }
    }

    /** Shows the newest open session of an environment, or opens one if there is none. */
    fun resume(environmentId: String?) {
        sessionList.lastOrNull { it.environmentId == environmentId && !it.archived }?.let(::select) ?: open(environmentId)
    }

    fun launch(launcher: CliLauncher) {
        val owner = service ?: return
        scope.launch {
            try {
                check(sessionList.size < SESSION_LIMIT) { resources.getString(R.string.session_limit_reached, SESSION_LIMIT) }
                check(sessionList.none { it.environmentId == launcher.environmentId && it.mode == ExecutionMode.Chroot }) { resources.getString(R.string.chroot_switch_blocked) }
                check(!busy(launcher.environmentId)) { resources.getString(R.string.environment_busy) }
                val spec = owner.environments.launcherSpecification(launcher)
                TerminalService.ensureStarted(context)
                val session = owner.sessions.create(launcher.environmentId, launcher.name, spec)
                selectedId = session.id; overlay = null; error = null
                navigator.home()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message }
        }
    }

    fun archive(session: TerminalSession, archived: Boolean) { service?.sessions?.archive(session.id, archived) }
    fun close(session: TerminalSession) { service?.sessions?.remove(session.id) }
    fun closeAll(environmentId: String?) {
        sessionList.filter { it.environmentId == environmentId }.forEach { service?.sessions?.remove(it.id) }
    }

    /** Clipboard paste. Multi-line text asks first unless the user turned that off. */
    fun paste(selection: org.connectbot.terminal.SelectionController? = null) {
        val clip = context.getSystemService(ClipboardManager::class.java).primaryClip
        val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString() ?: return
        val target = selected(sessionList) ?: return
        if (target.status.value != SessionStatus.Running) { error = resources.getString(R.string.session_finished_input); return }
        if (text.length > 1_048_576) { error = resources.getString(R.string.paste_too_large); return }
        if (preferences.confirmMultilinePaste && ('\n' in text || '\r' in text)) overlay = Overlay.Send(target.id, text, run = false, fromDraft = false)
        else { selection?.clearSelection(); target.emulator.pasteText(text) }
    }

    /** Delivers confirmed text; [run] presses Enter after it. */
    fun send(request: Overlay.Send, workspace: WorkspaceState) {
        val destination = session(request.sessionId)
        when {
            destination == null -> error = resources.getString(R.string.session_closed_input)
            destination.status.value != SessionStatus.Running -> error = resources.getString(R.string.session_finished_input)
            else -> {
                destination.emulator.pasteText(request.text)
                if (request.run) destination.emulator.dispatchKey(0, org.connectbot.terminal.VTermKey.ENTER)
                if (request.fromDraft) workspace.command(destination.id).text = ""
            }
        }
        overlay = null
    }
}
