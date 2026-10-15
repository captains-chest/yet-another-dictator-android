package com.captainduckay.dictator.android

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * The captains-chest.com pieces (ADR 0009): wood boards and planks with an ink outline and a hard
 * drop shadow, brass rivets, gold coins, moss buttons and switches.
 */

internal enum class Wood { Board, Plank }

internal enum class Rivets { None, Sides, Corners }

private val WoodShadow = 5.dp
private val InkShadow = Color(0xFF050807)

/** The night-sea backdrop behind the whole screen. */
internal fun Modifier.sea(): Modifier =
    drawBehind {
        drawRect(Chest.Sea)
        drawRect(
            Brush.radialGradient(
                listOf(Chest.SeaGlow, Color.Transparent),
                center = Offset(size.width * 0.8f, 0f),
                radius = size.width * 1.1f,
            ),
        )
        drawRect(
            Brush.radialGradient(
                listOf(Color(0x66145046), Color.Transparent),
                center = Offset(0f, size.height),
                radius = size.width * 0.9f,
            ),
        )
    }

/** A wooden board (or a darker plank) with an ink outline, a hard shadow and optional rivets. */
internal fun Modifier.wood(wood: Wood = Wood.Board, rivets: Rivets = Rivets.None, corner: Dp = 14.dp): Modifier =
    padding(end = WoodShadow, bottom = WoodShadow).drawBehind {
        val r = CornerRadius(corner.toPx())
        val shadow = WoodShadow.toPx()
        drawRoundRect(InkShadow, topLeft = Offset(shadow, shadow), size = size, cornerRadius = r)
        val outline = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, r)) }
        clipPath(outline) {
            when (wood) {
                Wood.Board -> {
                    drawRect(Brush.verticalGradient(listOf(Chest.BoardTop, Chest.BoardBottom)))
                    grain(step = 28.dp.toPx(), line = 2.dp.toPx(), alpha = 0.2f)
                }
                Wood.Plank -> {
                    drawRect(Brush.verticalGradient(listOf(Chest.PlankTop, Chest.PlankMid, Chest.PlankBottom)))
                    grain(step = 8.dp.toPx(), line = 1.dp.toPx(), alpha = 0.14f)
                }
            }
        }
        val border = 3.dp.toPx()
        val glow = 2.dp.toPx()
        drawRoundRect(
            Chest.Rivet.copy(alpha = 0.22f),
            topLeft = Offset(border + glow / 2, border + glow / 2),
            size = Size(size.width - 2 * border - glow, size.height - 2 * border - glow),
            cornerRadius = CornerRadius(r.x - border),
            style = Stroke(glow),
        )
        drawRoundRect(
            Chest.Ink,
            topLeft = Offset(border / 2, border / 2),
            size = Size(size.width - border, size.height - border),
            cornerRadius = r,
            style = Stroke(border),
        )
        val inset = 13.dp.toPx()
        when (rivets) {
            Rivets.None -> Unit
            Rivets.Sides -> {
                rivet(Offset(inset - 3.dp.toPx(), size.height / 2))
                rivet(Offset(size.width - inset + 3.dp.toPx(), size.height / 2))
            }
            Rivets.Corners -> {
                rivet(Offset(inset, inset))
                rivet(Offset(size.width - inset, inset))
                rivet(Offset(inset, size.height - inset))
                rivet(Offset(size.width - inset, size.height - inset))
            }
        }
    }

private fun DrawScope.grain(step: Float, line: Float, alpha: Float) {
    var y = step - line
    while (y < size.height) {
        drawRect(Color.Black.copy(alpha = alpha), topLeft = Offset(0f, y), size = Size(size.width, line))
        y += step
    }
}

private fun DrawScope.rivet(at: Offset) {
    drawCircle(Color(0xFF5A4020), radius = 4.dp.toPx(), center = at)
    drawCircle(
        Brush.radialGradient(
            listOf(Color(0xFFFFF4D6), Chest.Rivet),
            center = at - Offset(1f, 1f),
            radius = 3.dp.toPx(),
        ),
        radius = 2.8.dp.toPx(),
        center = at,
    )
}

/** The big, faint cookie in the corner of a card. Decoration only. */
internal fun Modifier.blob(): Modifier =
    drawBehind {
        val radius = 65.dp.toPx()
        val path = Cookie.path(size.width + 30.dp.toPx() - radius, radius - 30.dp.toPx(), radius, 7, 0.12f)
        drawPath(path.asComposePath(), Chest.CoinLight.copy(alpha = 0.16f))
    }

/** A small-caps eyebrow label in the site's green. */
@Composable
internal fun Eyebrow(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier,
        style =
            MaterialTheme.typography.labelSmall.copy(
                color = Chest.Eyebrow,
                shadow = Shadow(Color.Black, Offset(0f, 2f), 0f),
            ),
    )
}

/** A gold coin with an ink rim, holding a dark icon. */
@Composable
internal fun Coin(
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
    iconSize: Dp = 18.dp,
    tilt: Float = 0f,
) {
    Box(
        modifier
            .size(size + 2.dp)
            .rotate(tilt)
            .drawBehind {
                val d = this.size.minDimension - 2.dp.toPx()
                val c = Offset(d / 2, d / 2)
                val r = d / 2
                drawCircle(Chest.Ink, radius = r, center = c + Offset(2.dp.toPx(), 2.dp.toPx()))
                drawCircle(
                    Brush.radialGradient(
                        0f to Chest.CoinLight,
                        0.6f to Chest.Coin,
                        1f to Chest.CoinDark,
                        center = Offset(d * 0.35f, d * 0.3f),
                        radius = d * 0.8f,
                    ),
                    radius = r,
                    center = c,
                )
                drawCircle(Color(0x59FFF0C8), radius = r - 3.5.dp.toPx(), center = c, style = Stroke(2.dp.toPx()))
                drawCircle(Chest.Ink, radius = r - 1.dp.toPx(), center = c, style = Stroke(2.dp.toPx()))
            },
    ) {
        Image(
            painterResource(icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(Chest.Brown),
            modifier =
                Modifier
                    .padding(end = 2.dp, bottom = 2.dp)
                    .align(Alignment.Center)
                    .size(iconSize),
        )
    }
}

/** A moss switch when on, a dark slot when off. State only: the row around it takes the tap. */
@Composable
internal fun ChestSwitch(checked: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier.size(54.dp, 32.dp).drawBehind {
            val w = 50.dp.toPx()
            val h = 28.dp.toPx()
            val r = CornerRadius(h / 2)
            val ink = 2.dp.toPx()
            drawRoundRect(Chest.Ink, topLeft = Offset(ink, ink), size = Size(w, h), cornerRadius = r)
            drawRoundRect(
                if (checked) {
                    Brush.verticalGradient(listOf(Chest.MossTop, Chest.MossBottom), endY = h)
                } else {
                    Brush.verticalGradient(listOf(Chest.Slate, Chest.SlateDark), endY = h)
                },
                size = Size(w, h),
                cornerRadius = r,
            )
            drawRoundRect(
                Chest.Ink,
                topLeft = Offset(ink / 2, ink / 2),
                size = Size(w - ink, h - ink),
                cornerRadius = r,
                style = Stroke(ink),
            )
            val knob = 10.dp.toPx()
            val cx = if (checked) w - ink - knob else ink + knob
            val center = Offset(cx, h / 2)
            drawCircle(
                Brush.radialGradient(
                    listOf(Color(0xFFFFF8E6), if (checked) Chest.Rivet else Chest.Muted.copy(alpha = 0.7f)),
                    center = center - Offset(knob / 3, knob / 3),
                    radius = knob,
                ),
                radius = knob - ink / 2,
                center = center,
            )
            drawCircle(Chest.Ink, radius = knob - ink / 2, center = center, style = Stroke(ink))
        },
    )
}

/** A moss (primary) or slate button with an ink outline and a hard shadow. */
@Composable
internal fun ChestButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier
            .padding(end = 3.dp, bottom = 3.dp)
            .drawBehind {
                val r = CornerRadius(10.dp.toPx())
                val s = 3.dp.toPx()
                drawRoundRect(Chest.Ink, topLeft = Offset(s, s), size = size, cornerRadius = r)
                val fill =
                    when {
                        !enabled -> listOf(Color(0xFF2A2620), Color(0xFF1E1B16))
                        primary -> listOf(Chest.MossTop, Chest.MossBottom)
                        else -> listOf(Chest.Slate, Chest.SlateDark)
                    }
                drawRoundRect(Brush.verticalGradient(fill), cornerRadius = r)
                val ink = 2.dp.toPx()
                drawRoundRect(
                    Chest.Ink,
                    topLeft = Offset(ink / 2, ink / 2),
                    size = Size(size.width - ink, size.height - ink),
                    cornerRadius = r,
                    style = Stroke(ink),
                )
            }.clip(shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .defaultMinSize(minHeight = 48.dp)
            .padding(PaddingValues(horizontal = 18.dp, vertical = 12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style =
                TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (enabled) Color(0xFFF6F9F1) else Chest.Muted.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center,
                ),
        )
    }
}

/** A small status tag: moss for "In use", slate for a size. */
@Composable
internal fun Pill(text: String, on: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(end = 2.dp, bottom = 2.dp)
            .drawBehind {
                val r = CornerRadius(8.dp.toPx())
                val s = 2.dp.toPx()
                drawRoundRect(Chest.Ink, topLeft = Offset(s, s), size = size, cornerRadius = r)
                val fill =
                    if (on) listOf(Chest.MossTop, Chest.MossBottom) else listOf(Chest.Slate, Chest.SlateDark)
                drawRoundRect(Brush.verticalGradient(fill), cornerRadius = r)
                drawRoundRect(
                    Chest.Ink,
                    topLeft = Offset(s / 2, s / 2),
                    size = Size(size.width - s, size.height - s),
                    cornerRadius = r,
                    style = Stroke(s),
                )
            }.padding(horizontal = 9.dp, vertical = 5.dp),
    ) {
        Text(
            text,
            maxLines = 1,
            style =
                TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp,
                    color = if (on) Color(0xFFF6F9F1) else Color(0xFFECDFC2),
                ),
        )
    }
}

/** A coin when selected, an empty dark socket otherwise. */
@Composable
internal fun ChestRadio(selected: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier.size(22.dp).drawBehind {
            val r = size.minDimension / 2
            val c = center
            if (selected) {
                drawCircle(
                    Brush.radialGradient(
                        0f to Chest.CoinLight,
                        0.6f to Chest.Coin,
                        1f to Chest.CoinDark,
                        center = Offset(size.width * 0.35f, size.height * 0.3f),
                        radius = size.width * 0.8f,
                    ),
                    radius = r,
                )
                drawCircle(Chest.Brown, radius = 4.dp.toPx(), center = c)
            } else {
                drawCircle(Color(0xFF1A1612), radius = r)
                drawCircle(Chest.Rivet.copy(alpha = 0.3f), radius = r - 3.dp.toPx(), style = Stroke(2.dp.toPx()))
            }
            drawCircle(Chest.Ink, radius = r - 1.dp.toPx(), style = Stroke(2.dp.toPx()))
        },
    )
}

/** One board with an eyebrow title and its content. */
@Composable
internal fun BoardCard(
    title: String,
    modifier: Modifier = Modifier,
    rivets: Rivets = Rivets.None,
    decorated: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
    titleInset: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .wood(Wood.Board, rivets)
            .clip(RoundedCornerShape(14.dp))
            .then(if (decorated) Modifier.blob() else Modifier)
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Eyebrow(title, Modifier.padding(start = titleInset, bottom = 2.dp).semantics { heading() })
        content()
    }
}

/** The header plank: the logo on a coin and the name in serif capitals. */
@Composable
internal fun HeaderPlank(title: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .wood(Wood.Plank, Rivets.Sides, corner = 12.dp)
            .padding(start = 26.dp, end = 26.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Coin(R.drawable.ic_logo, size = 32.dp, iconSize = 21.dp, tilt = -6f)
        Text(
            title.uppercase(),
            style =
                MaterialTheme.typography.titleMedium.copy(
                    color = Color(0xFFFBF3DF),
                    letterSpacing = 2.4.sp,
                    shadow = Shadow(Color.Black, Offset(0f, 4f), 0f),
                ),
        )
    }
}
