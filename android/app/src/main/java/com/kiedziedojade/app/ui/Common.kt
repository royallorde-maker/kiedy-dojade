package com.kiedziedojade.app.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Wejście „karta po karcie” — jak w reszcie apki. */
@Composable
fun Appear(index: Int = 0, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(40L * index)
        shown = true
    }
    val alpha by animateFloatAsState(if (shown) 1f else 0f, tween(260), label = "appear")
    val shift by animateDpAsState(if (shown) 0.dp else 18.dp, tween(300), label = "shift")
    Box(modifier = Modifier.alpha(alpha).offset(y = shift)) { content() }
}

/** Karta w stylu apki: duże zaokrąglenie, kolor kontenera, bez obramowań. */
@Composable
fun KdCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    highlighted: Boolean = false,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        shape = RoundedCornerShape(22.dp),
        color = if (highlighted) {
            scheme.primaryContainer
        } else {
            scheme.surfaceContainer
        },
    ) {
        Column(modifier = Modifier.padding(16.dp)) { content() }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        letterSpacing = 1.4.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 4.dp, vertical = 6.dp),
    )
}

private val LINE_COLORS = listOf(
    Color(0xFF2B5FD9), Color(0xFF1E8E5A), Color(0xFFA86A00), Color(0xFFC0325A),
    Color(0xFF6E4BD9), Color(0xFF0E7C8A), Color(0xFFC25412), Color(0xFF5A6B7A),
)

fun lineColor(line: String): Color {
    var h = 0
    for (c in line) h = (h * 31 + c.code) and 0x7fffffff
    return LINE_COLORS[h % LINE_COLORS.size]
}

/** Numer linii jako kolorowy znacznik. */
@Composable
fun LineChip(line: String, big: Boolean = false) {
    val color = lineColor(line)
    Surface(
        shape = RoundedCornerShape(if (big) 12.dp else 9.dp),
        color = color.copy(alpha = 0.20f),
    ) {
        Text(
            text = line,
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = if (big) 20.sp else 13.sp,
            modifier = Modifier.padding(
                horizontal = if (big) 12.dp else 8.dp,
                vertical = if (big) 6.dp else 3.dp,
            ),
        )
    }
}

/** Mała pigułka statusu (opóźnienie, NŻ, odwołany, szacowany). */
@Composable
fun StatusChip(
    text: String,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    background: Color = color.copy(alpha = 0.14f),
    icon: ImageVector? = null,
) {
    Surface(shape = RoundedCornerShape(8.dp), color = background) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
        ) {
            if (icon != null) Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(13.dp))
            Text(text, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun DelayChip(delayMin: Int?, estimated: Boolean, canceled: Boolean) {
    when {
        canceled -> StatusChip(
            text = "odwołany",
            color = MaterialTheme.colorScheme.error,
        )
        delayMin != null && delayMin > 0 -> StatusChip("+$delayMin min", MaterialTheme.colorScheme.error)
        delayMin != null && delayMin < 0 -> StatusChip("$delayMin min", MaterialTheme.colorScheme.tertiary)
        estimated -> StatusChip("na żywo", MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, text: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 36.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            icon, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(44.dp),
        )
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

/** Nagłówek z opcjonalnym powrotem — jak TabHero w pozostałych ekranach. */
@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "wstecz")
            }
            Spacer(Modifier.width(2.dp))
        } else {
            Spacer(Modifier.width(4.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Ikona w kółku (bus/pieszo) do wiersza trasy. */
@Composable
fun LegDot(icon: ImageVector, color: Color) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .background(color.copy(alpha = 0.16f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(17.dp))
    }
}

/** Wiersz karty zmian (InfoCard z bs-companion). */
@Composable
fun InfoCard(title: String, lines: List<String>, index: Int = 0) {
    Appear(index) {
        KdCard(modifier = Modifier.padding(vertical = 4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            lines.forEach { line ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 2.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 7.dp)
                            .size(5.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                    )
                    Text(line, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
fun WalkRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(
            Icons.Filled.DirectionsWalk, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(15.dp),
        )
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun BusIcon(size: Int = 17) {
    Icon(
        Icons.Filled.DirectionsBus, contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(size.dp),
    )
}
