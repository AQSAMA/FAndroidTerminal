// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/** Original geometric context marks; no downloaded artwork or external icon dependency. */
internal fun contextIcon(distribution: String?) = when (distribution) {
    "ubuntu" -> R.drawable.ic_ubuntu; "alpine" -> R.drawable.ic_alpine
    "debian" -> R.drawable.ic_debian; else -> R.drawable.ic_android
}
@Composable
internal fun contextColor(distribution: String?): Color = when (distribution) {
    "ubuntu" -> if (MaterialTheme.colorScheme.surface.luminanceValue() < .5f) Color(0xFFD9A17F) else Color(0xFF9E3C0D)
    "alpine" -> MaterialTheme.colorScheme.primary
    "debian" -> if (MaterialTheme.colorScheme.surface.luminanceValue() < .5f) Color(0xFFD7A8BB) else Color(0xFF9D2453)
    else -> if (MaterialTheme.colorScheme.surface.luminanceValue() < .5f) Color(0xFFB0C5AC) else Color(0xFF216447)
}
private fun Color.luminanceValue() = .2126f * red + .7152f * green + .0722f * blue
@Composable
internal fun ContextBadge(distribution: String?, modifier: Modifier = Modifier, small: Boolean = false) {
    val color = contextColor(distribution)
    Box(modifier.size(if (small) 28.dp else 36.dp), contentAlignment = Alignment.Center) {
        Icon(painterResource(contextIcon(distribution)), null, Modifier.size(if (small) 22.dp else 28.dp), tint = color)
    }
}
