// SPDX-License-Identifier: GPL-3.0-only
// Adapted from DenserMeerkat/June, JuneConnectedList.kt and JuneFloatingActionBar.kt.
// Source revision f7501172211624bdfdc2690a8c6794d2483bfafb. See THIRD_PARTY_NOTICES.md.
package io.github.aqsama.fandroidterminal.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

@Composable
fun ConnectedGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.clip(RoundedCornerShape(24.dp)), verticalArrangement = Arrangement.spacedBy(2.dp), content = content)
}

@Composable
fun FloatingControls(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Surface(modifier, shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), content = content)
    }
}
