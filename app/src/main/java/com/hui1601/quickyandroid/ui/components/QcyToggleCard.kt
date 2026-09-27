@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.hui1601.quickyandroid.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

/**
 * M3 Expressive toggle card for dashboard feature toggles.
 * Shape morphs round (28dp, off) to square (12dp, on); active state gets
 * a tertiary-container tint and an emphasized title.
 */
@Composable
fun QcyToggleCard(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val corner by animateDpAsState(
        targetValue = if (checked) 12.dp else 28.dp,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "toggleShape"
    )
    val container by animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.tertiaryContainer
        else MaterialTheme.colorScheme.surfaceContainer,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "toggleContainer"
    )
    val contentColor = if (checked) MaterialTheme.colorScheme.onTertiaryContainer
    else MaterialTheme.colorScheme.onSurface

    Card(
        onClick = { onCheckedChange(!checked) },
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                role = Role.Switch
                stateDescription = if (checked) "On" else "Off"
            },
        shape = RoundedCornerShape(corner),
        colors = CardDefaults.cardColors(
            containerColor = container,
            contentColor = contentColor,
            disabledContainerColor = if (checked) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.38f)
            else MaterialTheme.colorScheme.surfaceContainerLow,
            disabledContentColor = contentColor
        )
    ) {
        Column(
            modifier = Modifier.alpha(if (enabled) 1f else 0.38f).padding(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = if (checked) MaterialTheme.colorScheme.onTertiaryContainer
                else MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                style = if (checked) MaterialTheme.typography.titleMediumEmphasized
                else MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (checked) MaterialTheme.colorScheme.onTertiaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
