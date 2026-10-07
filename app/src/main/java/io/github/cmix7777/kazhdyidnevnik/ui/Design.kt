package io.github.cmix7777.kazhdyidnevnik.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.cmix7777.kazhdyidnevnik.R

/** Фон всех экранов: почти чёрный, с фиолетовым свечением сверху и тонкими кругами, как в референсе. */
@Composable
fun GlowBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Palette.Background)
            .drawBehind {
                val top = Offset(size.width * 0.5f, -size.width * 0.15f)
                val topRadius = size.width * 1.05f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Palette.Glow.copy(alpha = 0.55f), Palette.Glow.copy(alpha = 0.12f), Color.Transparent),
                        center = top,
                        radius = topRadius,
                    ),
                    radius = topRadius,
                    center = top,
                )
                val low = Offset(-size.width * 0.1f, size.height * 0.78f)
                val lowRadius = size.width * 0.95f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF7E22CE).copy(alpha = 0.22f), Color.Transparent),
                        center = low,
                        radius = lowRadius,
                    ),
                    radius = lowRadius,
                    center = low,
                )
                val rings = Offset(size.width * 0.5f, size.height * 0.42f)
                for (i in 1..5) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.022f),
                        radius = size.width * 0.22f * i,
                        center = rings,
                        style = Stroke(width = 1.dp.toPx()),
                    )
                }
            },
        content = content,
    )
}

enum class CardTone { Normal, Highlight, Dim, Danger }

/** Стеклянная карточка: тёмный полупрозрачный градиент и тонкая светлая рамка. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    tone: CardTone = CardTone.Normal,
    shape: Shape = RoundedCornerShape(22.dp),
    contentPadding: PaddingValues = PaddingValues(16.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(6.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val fill = when (tone) {
        CardTone.Highlight -> Brush.linearGradient(
            listOf(Color(0xFF6D28D9).copy(alpha = 0.78f), Color(0xFF3B0F73).copy(alpha = 0.62f)),
        )
        CardTone.Danger -> Brush.verticalGradient(listOf(Color(0xFF3A1120), Color(0xFF240A14)))
        CardTone.Dim -> Brush.verticalGradient(
            listOf(Palette.CardTop.copy(alpha = 0.5f), Palette.CardBottom.copy(alpha = 0.45f)),
        )
        CardTone.Normal -> Brush.verticalGradient(
            listOf(Palette.CardTop.copy(alpha = 0.92f), Palette.CardBottom.copy(alpha = 0.88f)),
        )
    }
    val border = when (tone) {
        CardTone.Highlight -> Brush.linearGradient(
            listOf(Palette.Violet.copy(alpha = 0.9f), Palette.Violet.copy(alpha = 0.2f)),
        )
        CardTone.Danger -> SolidColor(Palette.Danger.copy(alpha = 0.4f))
        else -> Brush.verticalGradient(listOf(Palette.BorderStrong, Color.White.copy(alpha = 0.03f)))
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(fill)
            .border(1.dp, border, shape)
            .padding(contentPadding),
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

/** Маленькая «таблетка» с подписью, как ярлыки разделов в референсе. */
@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    @DrawableRes icon: Int? = null,
) {
    val textColor = if (accent) Palette.Lavender else Palette.TextMuted
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(if (accent) Palette.Violet.copy(alpha = 0.16f) else Palette.Chip)
            .border(1.dp, if (accent) Palette.Violet.copy(alpha = 0.45f) else Palette.Border, CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(14.dp), tint = textColor)
        } else if (accent) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(Palette.Violet),
            )
        }
        Text(text = text, style = MaterialTheme.typography.labelMedium, color = textColor)
    }
}

/** Главная кнопка: белая таблетка с фиолетовым кружком-стрелкой. */
@Composable
fun PillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Row(
        modifier = modifier
            .shadow(
                elevation = if (enabled) 14.dp else 0.dp,
                shape = CircleShape,
                ambientColor = Palette.Violet,
                spotColor = Palette.Violet,
            )
            .clip(CircleShape)
            .background(if (enabled) Color.White else Color.White.copy(alpha = 0.35f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(start = 18.dp, end = 5.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge, color = Palette.Ink)
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Palette.Violet, Palette.VioletDeep))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_arrow_forward),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = Color.White,
            )
        }
    }
}

/** Второстепенная кнопка: стеклянная таблетка. */
@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    small: Boolean = false,
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Palette.Chip)
            .border(1.dp, Palette.BorderStrong, CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = if (small) 12.dp else 18.dp, vertical = if (small) 7.dp else 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = if (small) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
            color = if (enabled) Palette.Text else Palette.TextFaint,
        )
    }
}

/** Круглая стеклянная кнопка со значком (настройки, листание недель). */
@Composable
fun GlassIconButton(
    @DrawableRes icon: Int,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Boolean = false,
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(
                if (accent) Brush.linearGradient(listOf(Palette.Violet, Palette.VioletDeep)) else SolidColor(Palette.Chip),
            )
            .border(1.dp, if (accent) Color.Transparent else Palette.BorderStrong, CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(icon),
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp),
            tint = if (enabled) Palette.Text else Palette.TextFaint,
        )
    }
}

/** Заголовок в две строки: первая белая, вторая приглушённая. */
@Composable
fun TwoToneTitle(
    first: String,
    second: String?,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.headlineLarge,
) {
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = Palette.Text)) { append(first) }
            if (!second.isNullOrEmpty()) {
                append("\n")
                withStyle(SpanStyle(color = Palette.TextMuted)) { append(second) }
            }
        },
        modifier = modifier,
        style = style,
    )
}

/** Круглая отметка «сделано». */
@Composable
fun CheckCircle(checked: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(44.dp)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() }),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(
                    if (checked) Brush.linearGradient(listOf(Palette.Violet, Palette.VioletDeep)) else SolidColor(Palette.Chip),
                )
                .border(1.5.dp, if (checked) Color.Transparent else Palette.BorderStrong, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(
                    painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    modifier = Modifier.size(17.dp),
                    tint = Color.White,
                )
            }
        }
    }
}

/** Значок в фиолетовом круге, как в карточках референса. */
@Composable
fun IconBadge(@DrawableRes icon: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Palette.Violet.copy(alpha = 0.16f))
            .border(1.dp, Palette.Violet.copy(alpha = 0.4f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(18.dp), tint = Palette.Lavender)
    }
}

/** Тонкая линия-разделитель внутри карточки. */
@Composable
fun GlassDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .height(1.dp)
            .background(Palette.Border),
    )
}
