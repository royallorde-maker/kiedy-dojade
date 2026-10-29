package com.kiedziedojade.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiedziedojade.app.core.I18n
import com.kiedziedojade.app.core.I18n.t
import com.kiedziedojade.app.core.fmtDate
import com.kiedziedojade.app.core.isoToDate
import com.kiedziedojade.app.core.todayStr
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime

/* ------------------------------------------------------------------ */
/*  Język wizualny „KD Glass”: szkło, światło od góry, gradient signature */
/* ------------------------------------------------------------------ */

/** Wejście „karta po karcie” — jak w reszcie apki. */
@Composable
fun Appear(index: Int = 0, content: @Composable () -> Unit) {
    // saveable: po przewinięciu w LazyColumn karta wraca już rozjaśniona,
    // zamiast powtarzać fade-in przy każdym recyklingu
    var shown by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(40L * index)
        shown = true
    }
    val alpha by animateFloatAsState(if (shown) 1f else 0f, tween(260), label = "appear")
    val shift by animateDpAsState(if (shown) 0.dp else 18.dp, tween(300), label = "shift")
    Box(modifier = Modifier.alpha(alpha).offset(y = shift)) { content() }
}

/**
 * Oświetlona krawędź szkła. W ciemnych motywach to biała poświata od góry,
 * która gaśnie przy dole. W jasnych motywach biała krawędź byłaby niewidoczna
 * (szkło zlewałoby się z tłem), więc używamy prawdziwej, ciemniejszej obwódki
 * z palety — karta dostaje kontur zamiast płaskiej plamy.
 */
@Composable
private fun glassBorder(topAlpha: Float = 0.22f): Brush {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f
    return if (dark) {
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = topAlpha),
            0.45f to Color.White.copy(alpha = topAlpha * 0.35f),
            1f to Color.White.copy(alpha = 0.06f),
        )
    } else {
        val a = (topAlpha / 0.22f).coerceIn(0.45f, 1f)
        Brush.verticalGradient(
            0f to scheme.outline.copy(alpha = 0.80f * a),
            0.5f to scheme.outline.copy(alpha = 0.55f * a),
            1f to scheme.outline.copy(alpha = 0.35f * a),
        )
    }
}

/**
 * Karta bazowa całej apki: półprzezroczysty profil + cienka, oświetlona
 * obwódka. Głębia bierze się z obwódki, nie z mgły na treści.
 */
@Composable
fun KdCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    highlighted: Boolean = false,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(26.dp)
    val fill = when {
        highlighted -> scheme.primary.copy(alpha = 0.22f)
        else -> scheme.surfaceContainer.copy(alpha = 0.62f)
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(fill)
            .border(1.dp, glassBorder(), shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable { onClick() }
                } else {
                    Modifier
                },
            ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) { content() }
    }
}

/**
 * Sygnaturowy gradient apki — czysta, chłodna biel „hero” z delikatnym
 * spadkiem do szarości. Ciemny napis [KD_ON_HERO] czyta się na niej jak
 * tusz na papierze, a na ciemnych motywach karta świeci jak papier.
 */
fun kdHeroBrush(): Brush = Brush.linearGradient(
    colorStops = arrayOf(
        0f to Color(0xFFFFFFFF),
        0.55f to Color(0xFFF7F9FC),
        1f to Color(0xFFE4EAF2),
    ),
    start = Offset(0f, 0f),
    end = Offset(720f, 720f),
)

/** Kolor tekstu/ikon na białej karcie hero. */
val KD_ON_HERO = Color(0xFF0B1220)

/** Delikatna krawędź białych elementów — widoczna także na jasnym tle. */
@Composable
fun kdHeroEdge(): Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.30f)

/**
 * Wspólne szkło całej apki: półprzezroczyste wypełnienie + cienka,
 * oświetlona obwódka. Wszystkie panele na tle (karty prowadzenia, paski
 * na mapie) budują się na tym samym szkle.
 */
@Composable
fun GlassPane(
    shape: RoundedCornerShape,
    modifier: Modifier = Modifier,
    fill: Color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.62f),
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(fill)
            .border(1.dp, glassBorder(), shape),
    ) {
        content()
    }
}

/** "Dziś" / "Jutro" / a short date for anything further away. */
fun relativeDay(iso: String, today: String = todayStr()): String {
    val day = runCatching { isoToDate(iso) }.getOrNull() ?: return iso
    val base = runCatching { isoToDate(today) }.getOrNull() ?: LocalDate.now()
    return when (day) {
        base -> t("Dziś", "Today")
        base.plusDays(1) -> t("Jutro", "Tomorrow")
        base.minusDays(1) -> t("Wczoraj", "Yesterday")
        else -> fmtDate(iso, I18n.pl)
    }
}

/**
 * Krótsza nazwa miejsca do wąskich pól: „Sikorskiego/ Dworzec PKP” →
 * „Dworzec PKP”, „DWORCOWA (DW. KOLEJOWY)” → „Dworcowa”. Pełna nazwa
 * zostaje w danych i na tablicy przystanku.
 */
fun compactPlaceName(raw: String): String {
    // Etykiety pozycji są zapisywane razem z trasą, więc po zmianie języka
    // trzeba je przetłumaczyć na bieżąco.
    when (raw) {
        "Moja lokalizacja", "My location" -> return t("Moja lokalizacja", "My location")
        "Punkt na mapie", "Map point" -> return t("Punkt na mapie", "Map point")
    }
    val afterSlash = raw.substringAfterLast('/', "").trim()
    val base = afterSlash.ifEmpty { raw }
    val short = base.substringBefore('(').trim().ifEmpty { base }
    val shouty = short.length > 2 &&
        short.none { it.isLowerCase() } &&
        short.all { it.isLetter() || it.isDigit() || it.isWhitespace() || it == '-' }
    return if (shouty) {
        short.lowercase().replaceFirstChar { it.titlecase() }
    } else {
        short
    }
}

/** "Dzień dobry" / "Cześć" / "Dobry wieczór" — drobiazg, który brzmi jak człowiek. */
fun greetingPrefix(): String {
    val hour = runCatching { LocalTime.now().hour }.getOrDefault(12)
    return when (hour) {
        in 5..11 -> t("Dzień dobry", "Good morning")
        in 12..17 -> t("Cześć", "Hello")
        else -> t("Dobry wieczór", "Good evening")
    }
}

/** Nagłówek sekcji: bursztynowa kreska + rozstrzelany nadtytuł. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        modifier = modifier.padding(horizontal = 4.dp, vertical = 6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = 14.dp, height = 3.dp)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f), RoundedCornerShape(2.dp)),
        )
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            letterSpacing = 1.6.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
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

/** Numer linii jako pełny, kolorowy znaczek — biel na kolorze jak na autobusie. */
@Composable
fun LineChip(line: String, big: Boolean = false) {
    val color = lineColor(line)
    Surface(
        shape = RoundedCornerShape(if (big) 12.dp else 8.dp),
        color = color,
        shadowElevation = if (big) 6.dp else 2.dp,
    ) {
        Text(
            text = line,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = if (big) 20.sp else 13.sp,
            modifier = Modifier.padding(
                horizontal = if (big) 12.dp else 8.dp,
                vertical = if (big) 6.dp else 3.dp,
            ),
        )
    }
}

/** Pulsujący punkt „na żywo” — cicha informacja zamiast kolejnej pigułki. */
@Composable
fun LiveBadge(
    tint: Color = MaterialTheme.colorScheme.primary,
    text: String = t("na żywo", "live"),
) {
    val pulse = rememberInfiniteTransition(label = "live")
    val dot by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(850, easing = LinearEasing)),
        label = "liveDot",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .alpha(dot)
                .background(tint, CircleShape),
        )
        Text(text, style = MaterialTheme.typography.labelSmall, color = tint, fontWeight = FontWeight.Medium)
    }
}

/** Mała pigułka statusu (opóźnienie, NŻ, odwołany, szacowany) — pełne kapsułki. */
@Composable
fun StatusChip(
    text: String,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    background: Color = color.copy(alpha = 0.14f),
    icon: ImageVector? = null,
) {
    Surface(shape = CircleShape, color = background) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
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
            text = t("odwołany", "canceled"),
            color = MaterialTheme.colorScheme.error,
        )
        delayMin != null && delayMin > 0 -> StatusChip("+$delayMin min", MaterialTheme.colorScheme.error)
        delayMin != null && delayMin < 0 -> StatusChip("$delayMin min", MaterialTheme.colorScheme.tertiary)
        estimated -> StatusChip(t("na żywo", "live"), MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, text: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 36.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // szkło z delikatnym, neutralnym kręgiem w tle
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f), CircleShape),
            )
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.7f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

/** Nagłówek z opcjonalnym powrotem — duży, ciasny, plakatowy. */
@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    below: @Composable (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    large: Boolean = false,
) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = t("wstecz", "back"),
                )
            }
            Spacer(Modifier.width(2.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = if (large) {
                    MaterialTheme.typography.headlineMedium
                } else {
                    MaterialTheme.typography.headlineSmall
                },
                fontWeight = FontWeight.Bold,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            below?.invoke()
        }
        trailing?.invoke()
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(width = 4.dp, height = 20.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f), RoundedCornerShape(2.dp)),
                )
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
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
            Icons.AutoMirrored.Filled.DirectionsWalk, contentDescription = null,
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

/* ------------------------------------------------------------------ */
/*  Pływający pasek nawigacji — kapsuła ze szkła z gradientową ramką   */
/* ------------------------------------------------------------------ */

private data class NavTabSpec(val icon: ImageVector, val label: String)

/**
 * Dolna nawigacja apki: pływająca kapsuła ze szkła, aktywna zakładka
 * dostaje bursztynową pigułkę-podświetlenie. Zero Materialowych domyślności.
 */
@Composable
fun KdNavBar(
    selected: Int,
    onSelect: (Int) -> Unit,
    tabs: List<Pair<ImageVector, String>>,
) {
    val scheme = MaterialTheme.colorScheme
    val density = androidx.compose.ui.platform.LocalDensity.current
    val capsuleShape = RoundedCornerShape(30.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(capsuleShape)
            .background(scheme.surfaceContainer.copy(alpha = 0.92f))
            .border(1.dp, glassBorder(0.16f), capsuleShape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { index, (icon, label) ->
                val active = index == selected
                val selAlpha by animateFloatAsState(
                    if (active) 1f else 0f,
                    tween(220),
                    label = "navSel",
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .clickable { onSelect(index) }
                        .padding(vertical = 7.dp),
                ) {
                    val pillW = with(density) { 56.dp.toPx() }
                    val pillH = with(density) { 36.dp.toPx() }
                    val pillR = with(density) { 18.dp.toPx() }
                    val brush = kdHeroBrush()
                    Box(
                        modifier = Modifier
                            .size(width = 56.dp, height = 36.dp)
                            .drawBehind {
                                if (selAlpha > 0.01f) {
                                    drawRoundRect(
                                        brush = brush,
                                        topLeft = Offset.Zero,
                                        size = Size(pillW, pillH),
                                        cornerRadius = CornerRadius(pillR, pillR),
                                        alpha = selAlpha,
                                    )
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            icon,
                            contentDescription = label,
                            tint = if (active) KD_ON_HERO else scheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        color = if (active) scheme.onSurface else scheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
