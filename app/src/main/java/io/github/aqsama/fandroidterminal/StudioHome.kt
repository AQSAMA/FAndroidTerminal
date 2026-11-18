// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aqsama.fandroidterminal.runtime.linux.LinuxEnvironment
import io.github.aqsama.fandroidterminal.sessions.TerminalSession

@Composable
internal fun StudioNavigation(page: String, open: (String) -> Unit) {
    NavigationBar(windowInsets = WindowInsets(0, 0, 0, 0), containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        listOf(
            Triple("Home", R.string.studio_home, R.drawable.ic_workspace),
            Triple("Environments", R.string.studio_linux, R.drawable.ic_environments),
            Triple("Recipes", R.string.studio_recipes, R.drawable.ic_commands),
            Triple("Settings", R.string.settings_label, R.drawable.ic_settings),
        ).forEach { (destination, label, icon) ->
            NavigationBarItem(selected = page == destination, onClick = { open(destination) },
                icon = { Icon(painterResource(icon), null, Modifier.size(22.dp)) },
                label = { Text(stringResource(label), maxLines = 1, overflow = TextOverflow.Ellipsis) })
        }
    }
}

@Composable
internal fun StudioHeading(title: String, modifier: Modifier = Modifier) {
    Text(title, modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge)
}

@Composable
internal fun StudioHome(
    sessions: List<TerminalSession>,
    environments: List<LinuxEnvironment>,
    selected: TerminalSession?,
    canLaunch: Boolean,
    linuxSupported: Boolean,
    revealArchives: Int,
    openSession: (TerminalSession) -> Unit,
    androidShell: () -> Unit,
    chooseSession: () -> Unit,
    linux: () -> Unit,
    recipes: () -> Unit,
    sessionAction: (TerminalSession, String) -> Unit,
) {
    var archived by rememberSaveable(revealArchives) { mutableStateOf(revealArchives > 0) }
    val visible = sessions.filter { it.archived == archived }
    val archivedCount = sessions.count { it.archived }
    val linuxCount = environments.count { it.ready }
    LazyColumn(
        Modifier.widthIn(max = 760.dp).fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                StudioHeading(stringResource(R.string.studio_home), Modifier.weight(1f))
                IconButton(chooseSession, enabled = canLaunch) {
                    Icon(painterResource(R.drawable.ic_add), stringResource(R.string.new_session))
                }
            }
        }
        item {
            Surface(
                onClick = { if (selected == null) androidShell() else openSession(selected) },
                enabled = selected != null || canLaunch,
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.inverseSurface,
                        contentColor = MaterialTheme.colorScheme.inverseOnSurface) {
                        Box(Modifier.size(60.dp), contentAlignment = Alignment.Center) {
                            Text(">_", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(if (selected == null) R.string.new_android_shell else R.string.studio_resume),
                            style = MaterialTheme.typography.titleLarge)
                        if (selected == null) Text(stringResource(R.string.workspace_app_permissions), style = MaterialTheme.typography.bodySmall)
                        else {
                            val environment = environments.firstOrNull { it.id == selected.environmentId }
                            Text(stringResource(R.string.workspace_session_context, selected.title,
                                environment?.name ?: stringResource(R.string.android_shell)), style = MaterialTheme.typography.bodySmall,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Icon(painterResource(R.drawable.ic_chevron), null, Modifier.size(20.dp))
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StudioShortcut(
                    title = stringResource(if (linuxSupported && linuxCount == 0) R.string.install_linux else R.string.studio_linux),
                    icon = R.drawable.ic_environments, action = linux, modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                StudioShortcut(stringResource(R.string.studio_recipes), R.drawable.ic_commands, recipes, Modifier.weight(1f).fillMaxHeight())
            }
        }
        if (!canLaunch && sessions.size >= 12) item {
            Text(stringResource(R.string.session_limit_hint, archivedCount), style = MaterialTheme.typography.bodySmall)
        }
        if (sessions.isNotEmpty()) item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(if (archived) R.string.archived_sessions else R.string.sessions_label),
                    Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleLarge)
                if (archived || archivedCount > 0) TextButton({ archived = !archived }) {
                    Text(if (archived) stringResource(R.string.active_sessions) else stringResource(R.string.archived_count, archivedCount))
                }
            }
        }
        items(visible, key = { it.id }) { session ->
            val env = environments.firstOrNull { it.id == session.environmentId }
            StudioSessionEntry(session, env, isCurrent = session.id == selected?.id, open = { openSession(session) }) {
                sessionAction(session, it)
            }
        }
    }
}

@Composable
private fun StudioShortcut(title: String, icon: Int, action: () -> Unit, modifier: Modifier) {
    Surface(onClick = action, modifier = modifier, shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Icon(painterResource(icon), null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun StudioSessionEntry(session: TerminalSession, environment: LinuxEnvironment?, isCurrent: Boolean,
    open: () -> Unit, action: (String) -> Unit) {
    val status by session.status.collectAsStateWithLifecycle()
    val contextName = environment?.name ?: stringResource(R.string.android_shell)
    val label = stringResource(if (session.archived) R.string.session_restore_a11y else R.string.session_open_a11y, session.title, contextName)
    var menu by remember { mutableStateOf(false) }
    Surface(shape = MaterialTheme.shapes.medium,
        color = if (isCurrent) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).heightIn(min = 64.dp).clickable(onClick = open)
                .semantics { contentDescription = label; selected = isCurrent }.padding(start = 12.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ContextBadge(environment?.distribution, small = true)
                Column(Modifier.weight(1f)) {
                    Text(session.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.workspace_session_context, contextName, modeLabel(session.mode)),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                SessionStatusMark(status)
            }
            Box {
                IconButton({ menu = true }) {
                    Icon(painterResource(R.drawable.ic_more), stringResource(R.string.session_actions_a11y, session.title, contextName))
                }
                SessionActionMenu(menu, { menu = false }, session.archived) { menu = false; action(it) }
            }
        }
    }
}
