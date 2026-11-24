// SPDX-License-Identifier: GPL-3.0-only
// Adapted from DenserMeerkat/June aac069dcc86c903f8984beed2f885d46d1836607.
// ColorSwatch.kt / PaletteCard.kt. Local resources, 48dp targets and selection semantics.
package io.github.aqsama.fandroidterminal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Text
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ColorSwatch(
    color: Int,
    label: String,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val swatchShape = if (isSelected) {
        MaterialShapes.Cookie4Sided.toShape()
    } else {
        MaterialShapes.Cookie6Sided.toShape()
    }
    val swatchSize = if (isSelected) 44.dp else 40.dp

    Box(
        modifier = modifier
            .size(48.dp)
            .semantics { contentDescription = label }
            .selectable(selected = isSelected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(swatchSize)
                .clip(swatchShape)
                .background(Color(color)),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    tint = if (ColorUtils.calculateLuminance(color) > 0.5) Color.Black else Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
