// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel

/** Drafts survive screen changes and Activity recreation, without putting large text in Bundles. */
class WorkspaceState : ViewModel() {
    private val commands = mutableMapOf<String, CommandDraft>()
    fun command(id: String) = commands.getOrPut(id) { CommandDraft() }
    fun retainSessions(ids: Set<String>) { commands.keys.retainAll(ids) }
    private val drafts = mutableMapOf<String, EditorDraft>()
    fun draft(id: String) = drafts.getOrPut(id) { EditorDraft() }
}
class CommandDraft { var text by mutableStateOf("") }
class EditorDraft {
    var directory by mutableStateOf("/root")
    var path by mutableStateOf<String?>(null)
    var text by mutableStateOf("")
    var baseline by mutableStateOf("")
    val dirty get() = path != null && text != baseline
}
