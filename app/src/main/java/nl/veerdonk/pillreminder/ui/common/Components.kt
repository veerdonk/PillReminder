package nl.veerdonk.pillreminder.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import nl.veerdonk.pillreminder.R

/*
 * Shared building blocks for the screens, so every card, tag and icon looks the same
 * (8-point spacing, soft tinted shadows, accent used sparingly).
 */

/** Inner padding of every card. */
val CardPadding: Dp = 20.dp

/** Gap between the cards on a screen. */
val ScreenGap: Dp = 16.dp

@Composable
private fun isDark(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

/**
 * A white card on the light grey background (a raised surface in dark mode), with a soft shadow
 * tinted with the accent instead of plain grey.
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    containerColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val dark = isDark()
    val shape = MaterialTheme.shapes.large
    val tint = colors.primary
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (dark || containerColor != null) {
                    Modifier
                } else {
                    Modifier.shadow(12.dp, shape, ambientColor = tint.copy(alpha = 0.06f), spotColor = tint.copy(alpha = 0.10f))
                },
            ),
        shape = shape,
        color = containerColor ?: if (dark) colors.surfaceContainer else colors.surfaceContainerLowest,
        border = if (containerColor == null && !dark) BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.6f)) else null,
    ) {
        Column(
            modifier = Modifier.padding(CardPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

/** A small heading above a card's content. */
@Composable
fun CardLabel(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = color, modifier = modifier)
}

/** An icon centred in a tinted circle. */
@Composable
fun IconBadge(
    @DrawableRes icon: Int,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    iconSize: Dp = size / 2,
    contentDescription: String? = null,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = contentDescription, tint = contentColor, modifier = Modifier.size(iconSize))
    }
}

/** A small rounded label, e.g. "Today" or a dose status in History. */
@Composable
fun Tag(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(containerColor)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) Icon(painterResource(icon), contentDescription = null, tint = contentColor, modifier = Modifier.size(16.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = contentColor)
    }
}

/** The top bar's back arrow. */
@Composable
fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.setup_navigate_back))
    }
}
