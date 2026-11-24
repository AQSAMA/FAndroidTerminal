// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.aqsama.fandroidterminal.runtime.linux.CliLauncher
import io.github.aqsama.fandroidterminal.runtime.linux.LinuxEnvironment
import io.github.aqsama.fandroidterminal.sessions.TerminalSession

@Composable
internal fun WorkspaceSidebar(
    screen: Screen, busy: Set<String>, sessions: List<TerminalSession>, environments: List<LinuxEnvironment>, selectedId: String?,
    navigate: (Screen) -> Unit, select: (TerminalSession) -> Unit, action: (TerminalSession, String) -> Unit, newSession: (String?) -> Unit,
    launchers: List<CliLauncher> = emptyList(), launchTool: (CliLauncher) -> Unit = {},
) {
    val entries = sessions.map { session -> key(session.id) { session.listEntry() } }
    WorkspaceSidebarContent(
        screen = screen,
        busy = busy,
        sessions = entries,
        environments = environments,
        selectedId = selectedId,
        navigate = navigate,
        select = { id -> sessions.firstOrNull { it.id == id }?.let(select) },
        action = { id, command -> sessions.firstOrNull { it.id == id }?.let { action(it, command) } },
        newSession = newSession,
        launchers = launchers,
        launchTool = launchTool,
    )
}

/**
 * Compact, context-grouped workspace sidebar: active session masthead + always-visible search pill,
 * collapsible context sections (Android shell, then each Linux environment) with inline launchers
 * and busy progress, collapsible archived section at bottom, and a slim 4-tile destination dock.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WorkspaceSidebarContent(
    screen: Screen,
    busy: Set<String>,
    sessions: List<SessionListEntry>,
    environments: List<LinuxEnvironment>,
    selectedId: String?,
    navigate: (Screen) -> Unit,
    select: (String) -> Unit,
    action: (String, String) -> Unit,
    newSession: (String?) -> Unit,
    launchers: List<CliLauncher> = emptyList(),
    launchTool: (CliLauncher) -> Unit = {},
    archivedInitiallyExpanded: Boolean = false,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var collapsedIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var archivedExpanded by rememberSaveable { mutableStateOf(archivedInitiallyExpanded) }

    val androidName = stringResource(R.string.android_shell)
    val envById = remember(environments) { environments.associateBy { it.id } }
    fun contextName(envId: String?): String = envId?.let { envById[it]?.name } ?: androidName

    val activeSessions = remember(sessions) { sessions.filterNot { it.archived } }
    val archivedSessions = remember(sessions) { sessions.filter { it.archived } }
    val selectedSession = remember(sessions, selectedId) { sessions.firstOrNull { it.id == selectedId } }

    val trimmedQuery = query.trim()
    val searching = trimmedQuery.isNotEmpty()
    fun matches(text: String) = text.contains(trimmedQuery, ignoreCase = true)

    val groups: List<Pair<String?, LinuxEnvironment?>> = remember(environments) {
        listOf(null to null) + environments.map { it.id to it }
    }

    val archivedMatches = remember(archivedSessions, trimmedQuery, envById, androidName) {
        if (!searching) archivedSessions
        else archivedSessions.filter { matches(it.title) || matches(contextName(it.environmentId)) }
    }

    Column(Modifier.fillMaxSize()) {
        SidebarHeader(
            selectedSession = selectedSession,
            selectedEnv = selectedSession?.environmentId?.let { envById[it] },
            selectedEnvName = selectedSession?.let { contextName(it.environmentId) },
            totalSessions = sessions.size,
            onSelectCurrent = { selectedSession?.let { select(it.id) } },
        )
        SidebarSearchPill(
            query = query,
            onQueryChange = { query = it },
        )

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            var visibleGroupCount = 0
            groups.forEach { (id, env) ->
                val name = env?.name ?: androidName
                val nameMatched = searching && matches(name)
                val groupSessions = activeSessions.filter { session ->
                    session.environmentId == id && (!searching || nameMatched || matches(session.title))
                }
                val groupLaunchers = if (id == null) emptyList() else launchers.filter { launcher ->
                    launcher.environmentId == id && (!searching || nameMatched || matches(launcher.name))
                }
                val isBusy = id != null && id in busy
                val isAttention = env != null && !env.ready && !isBusy
                val shouldShow = if (!searching) true else (nameMatched || groupSessions.isNotEmpty() || groupLaunchers.isNotEmpty())

                if (shouldShow) {
                    visibleGroupCount++
                    val sectionKey = id ?: "__android__"
                    val isCollapsed = !searching && sectionKey in collapsedIds
                    val canAdd = sessions.size < SESSION_LIMIT && (id == null || (!isBusy && env?.ready == true))

                    item(key = "section-$sectionKey") {
                        ContextSection(
                            name = name,
                            environment = env,
                            sessions = groupSessions,
                            launchers = groupLaunchers,
                            selectedId = selectedId,
                            collapsed = isCollapsed,
                            isBusy = isBusy,
                            isAttention = isAttention,
                            canAdd = canAdd,
                            onToggleCollapse = {
                                collapsedIds = if (sectionKey in collapsedIds) collapsedIds - sectionKey else collapsedIds + sectionKey
                            },
                            onNewSession = { newSession(id) },
                            onSelectSession = select,
                            onSessionAction = action,
                            onLaunchTool = launchTool,
                            onNavigate = navigate,
                        )
                    }
                }
            }

            if (archivedMatches.isNotEmpty()) {
                item(key = "section-archived") {
                    val expanded = searching || archivedExpanded
                    ArchivedSection(
                        sessions = archivedMatches,
                        selectedId = selectedId,
                        expanded = expanded,
                        envNameFor = ::contextName,
                        distributionFor = { envId -> envId?.let { envById[it]?.distribution } },
                        onToggle = { archivedExpanded = !archivedExpanded },
                        onSelect = select,
                        onAction = action,
                    )
                }
            }

            if (searching && visibleGroupCount == 0 && archivedMatches.isEmpty()) {
                item(key = "empty-search") {
                    Text(
                        text = stringResource(R.string.no_matching_sessions),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp),
                    )
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
        SidebarDestinationBar(screen = screen, navigate = navigate)
    }
}

@Composable
private fun SidebarHeader(
    selectedSession: SessionListEntry?,
    selectedEnv: LinuxEnvironment?,
    selectedEnvName: String?,
    totalSessions: Int,
    onSelectCurrent: () -> Unit,
) {
    val atLimit = totalSessions >= SESSION_LIMIT
    val capacityA11y = stringResource(R.string.sidebar_session_capacity, totalSessions, SESSION_LIMIT)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .clickable(enabled = selectedSession != null, onClick = onSelectCurrent)
                .padding(vertical = 4.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (selectedSession != null) {
                val tint = contextColor(selectedEnv?.distribution)
                Icon(
                    painterResource(contextIcon(selectedEnv?.distribution)),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = tint,
                )
                Text(
                    text = selectedSession.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (!selectedEnvName.isNullOrBlank()) {
                    Text(
                        text = "· $selectedEnvName",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                Icon(
                    painterResource(R.drawable.ic_terminal),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.sessions_label),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            }
        }
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = if (atLimit) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.semantics { contentDescription = capacityA11y },
        ) {
            Text(
                text = "$totalSessions/$SESSION_LIMIT",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                color = if (atLimit) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

/** Always-visible search pill. Deliberately never auto-focuses so opening the drawer never pops the IME. */
@Composable
private fun SidebarSearchPill(query: String, onQueryChange: (String) -> Unit) {
    val findLabel = stringResource(R.string.find_session)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 2.dp)
            .heightIn(min = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().height(42.dp),
            shape = RoundedCornerShape(21.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)),
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(start = 14.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painterResource(R.drawable.ic_search),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(R.string.find_sessions_short),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = findLabel },
                    )
                }
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(40.dp)) {
                        Icon(
                            painterResource(R.drawable.ic_close),
                            contentDescription = stringResource(R.string.clear_search),
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContextSection(
    name: String,
    environment: LinuxEnvironment?,
    sessions: List<SessionListEntry>,
    launchers: List<CliLauncher>,
    selectedId: String?,
    collapsed: Boolean,
    isBusy: Boolean,
    isAttention: Boolean,
    canAdd: Boolean,
    onToggleCollapse: () -> Unit,
    onNewSession: () -> Unit,
    onSelectSession: (String) -> Unit,
    onSessionAction: (String, String) -> Unit,
    onLaunchTool: (CliLauncher) -> Unit,
    onNavigate: (Screen) -> Unit,
) {
    val accent = contextColor(environment?.distribution)
    val hasBody = sessions.isNotEmpty() || launchers.isNotEmpty()
    val expanded = !collapsed && hasBody
    var envMenu by remember { mutableStateOf(false) }

    val toggleLabel = stringResource(
        if (expanded) R.string.sidebar_env_collapse else R.string.sidebar_env_expand,
        name,
    )

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(
            width = 0.5.dp,
            color = if (isBusy) accent.copy(alpha = 0.28f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f),
        ),
        modifier = Modifier.fillMaxWidth().animateContentSize(),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .padding(start = 10.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(
                            enabled = hasBody || (environment != null && (isBusy || isAttention)),
                            onClickLabel = if (hasBody) toggleLabel else null,
                        ) {
                            when {
                                hasBody -> onToggleCollapse()
                                environment != null && (isBusy || isAttention) -> onNavigate(Screen.Environment(environment.id))
                            }
                        }
                        .padding(end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = accent.copy(alpha = 0.14f),
                        modifier = Modifier.size(28.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painterResource(contextIcon(environment?.distribution)),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = accent,
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (environment != null && environment.release.isNotBlank()) {
                            Text(
                                text = environment.release,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                                maxLines = 1,
                            )
                        }
                        if (hasBody) {
                            Surface(
                                shape = RoundedCornerShape(7.dp),
                                color = accent.copy(alpha = 0.15f),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    if (sessions.isNotEmpty()) {
                                        Text(
                                            text = sessions.size.toString(),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = accent,
                                        )
                                    }
                                    Icon(
                                        painterResource(if (expanded) R.drawable.ic_chevron_up else R.drawable.ic_chevron_down),
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp),
                                        tint = accent.copy(alpha = 0.85f),
                                    )
                                }
                            }
                        }
                    }
                }

                if (isBusy) {
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            painterResource(R.drawable.ic_download),
                            contentDescription = stringResource(R.string.sidebar_env_busy),
                            modifier = Modifier.size(18.dp),
                            tint = accent,
                        )
                    }
                } else if (environment == null || environment.ready) {
                    IconButton(
                        onClick = onNewSession,
                        enabled = canAdd,
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (canAdd) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent,
                            modifier = Modifier.size(28.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painterResource(R.drawable.ic_add),
                                    contentDescription = stringResource(R.string.new_session_in, name),
                                    modifier = Modifier.size(16.dp),
                                    tint = if (canAdd) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                )
                            }
                        }
                    }
                }

                Box {
                    IconButton(onClick = { envMenu = true }) {
                        Icon(
                            painterResource(R.drawable.ic_more),
                            contentDescription = stringResource(R.string.sidebar_env_options, name),
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DropdownMenu(expanded = envMenu, onDismissRequest = { envMenu = false }) {
                        if (environment != null) {
                            val envReady = environment.ready && !isBusy
                            ActionMenuItem(stringResource(R.string.files_label), R.drawable.ic_files, enabled = envReady) {
                                envMenu = false
                                onNavigate(Screen.Files(environment.id))
                            }
                            ActionMenuItem(stringResource(R.string.packages_title), R.drawable.ic_download, enabled = envReady) {
                                envMenu = false
                                onNavigate(Screen.Packages(environment.id))
                            }
                            HorizontalDivider()
                            ActionMenuItem(stringResource(R.string.sidebar_env_settings), R.drawable.ic_settings) {
                                envMenu = false
                                onNavigate(Screen.Environment(environment.id))
                            }
                        } else {
                            ActionMenuItem(stringResource(R.string.working_mode), R.drawable.ic_shield) {
                                envMenu = false
                                onNavigate(Screen.Access)
                            }
                        }
                    }
                }
            }

            if (environment != null && (isBusy || isAttention)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigate(Screen.Environment(environment.id)) }
                        .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    if (isBusy) {
                        val busyText = environment.state.takeIf { it.isNotBlank() && it != "Ready" }
                            ?: stringResource(R.string.sidebar_env_busy)
                        BusyProgressBar(stateText = busyText, accent = accent)
                        Text(
                            text = busyText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else {
                        Text(
                            text = environment.state.ifBlank { stringResource(R.string.environment_needs_attention) },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(
                    modifier = Modifier.padding(start = 6.dp, end = 6.dp, bottom = 6.dp),
                ) {
                    sessions.forEach { session ->
                        key(session.id) {
                            SessionRow(
                                session = session,
                                environmentName = name,
                                selected = selectedId == session.id,
                                select = { onSelectSession(session.id) },
                                accentColor = accent,
                                action = { onSessionAction(session.id, it) },
                            )
                        }
                    }
                    if (launchers.isNotEmpty()) {
                        val canLaunch = canAdd && environment?.ready == true && !isBusy
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = 4.dp,
                                    end = 4.dp,
                                    top = if (sessions.isNotEmpty()) 4.dp else 0.dp,
                                    bottom = 2.dp,
                                ),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            launchers.forEach { launcher ->
                                key(launcher.id) {
                                    LauncherChip(
                                        launcher = launcher,
                                        environmentName = name,
                                        accent = accent,
                                        enabled = canLaunch,
                                        onClick = { onLaunchTool(launcher) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BusyProgressBar(stateText: String, accent: Color, modifier: Modifier = Modifier) {
    val parsedFraction = remember(stateText) {
        Regex("(\\d{1,3})%").find(stateText)?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(0, 100)?.div(100f)
    }
    if (parsedFraction != null) {
        LinearProgressIndicator(
            progress = { parsedFraction },
            modifier = modifier.fillMaxWidth().height(3.dp).clip(CircleShape),
            color = accent,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            drawStopIndicator = {},
        )
    } else {
        var phase by remember { mutableFloatStateOf(0.45f) }
        LaunchedEffect(Unit) {
            if (!android.animation.ValueAnimator.areAnimatorsEnabled()) return@LaunchedEffect
            while (true) {
                kotlinx.coroutines.delay(16L)
                phase = (phase + 0.014f) % 1.35f
            }
        }
        val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
        androidx.compose.foundation.Canvas(modifier.fillMaxWidth().height(3.dp).clip(CircleShape)) {
            drawRect(trackColor)
            val barWidth = size.width * 0.35f
            val isRtl = layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl
            val startX = (phase - 0.35f) * size.width
            val left = if (isRtl) size.width - startX - barWidth else startX
            drawRoundRect(
                color = accent,
                topLeft = androidx.compose.ui.geometry.Offset(left, 0f),
                size = androidx.compose.ui.geometry.Size(barWidth, size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f),
            )
        }
    }
}

@Composable
private fun LauncherChip(
    launcher: CliLauncher,
    environmentName: String,
    accent: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val label = stringResource(R.string.sidebar_launch_tool, launcher.name, environmentName)
    val shape = RoundedCornerShape(10.dp)
    val alpha = if (enabled) 1f else 0.42f
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier
                .clip(shape)
                .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick)
                .semantics { contentDescription = label }
                .heightIn(min = 38.dp)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                painterResource(R.drawable.ic_prompt),
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = accent.copy(alpha = alpha),
            )
            Text(
                text = launcher.name,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ArchivedSection(
    sessions: List<SessionListEntry>,
    selectedId: String?,
    expanded: Boolean,
    envNameFor: (String?) -> String,
    distributionFor: (String?) -> String?,
    onToggle: () -> Unit,
    onSelect: (String) -> Unit,
    onAction: (String, String) -> Unit,
) {
    val toggleA11y = stringResource(
        if (expanded) R.string.sidebar_archived_collapse else R.string.sidebar_archived_expand,
        sessions.size,
    )
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)),
        modifier = Modifier.fillMaxWidth().animateContentSize(),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(onClickLabel = toggleA11y, onClick = onToggle)
                    .semantics { contentDescription = toggleA11y }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    painterResource(R.drawable.ic_archive),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.sidebar_archived_header),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            text = sessions.size.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Icon(
                            painterResource(if (expanded) R.drawable.ic_chevron_up else R.drawable.ic_chevron_down),
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(
                    modifier = Modifier.padding(start = 6.dp, end = 6.dp, bottom = 6.dp),
                ) {
                    sessions.forEach { session ->
                        key(session.id) {
                            val envName = envNameFor(session.environmentId)
                            SessionRow(
                                session = session,
                                environmentName = envName,
                                selected = selectedId == session.id,
                                select = { onSelect(session.id) },
                                accentColor = contextColor(distributionFor(session.environmentId)),
                                showEnvironmentTag = true,
                                action = { onAction(session.id, it) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SidebarDestinationBar(screen: Screen, navigate: (Screen) -> Unit) {
    val destinations = remember {
        listOf(
            Screen.Environments to R.string.environments_label,
            Screen.Packages() to R.string.packages_title,
            Screen.Launchers to R.string.sidebar_nav_launchers,
            Screen.Settings to R.string.settings_label,
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        destinations.forEach { (destination, shortLabelRes) ->
            val active = when (destination) {
                Screen.Environments -> screen == Screen.Environments || screen is Screen.Environment || screen is Screen.Files
                is Screen.Packages -> screen is Screen.Packages
                Screen.Settings -> screen.isSettings || screen == Screen.Backup
                else -> screen == destination
            }
            val fullTitle = stringResource(destination.titleRes)
            val shortLabel = stringResource(shortLabelRes)
            val shape = RoundedCornerShape(14.dp)
            Surface(
                modifier = Modifier.weight(1f),
                shape = shape,
                color = if (active) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clip(shape)
                        .clickable(role = Role.Tab, onClick = { navigate(destination) })
                        .semantics {
                            contentDescription = fullTitle
                            selected = active
                        }
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        painterResource(destination.iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = shortLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
