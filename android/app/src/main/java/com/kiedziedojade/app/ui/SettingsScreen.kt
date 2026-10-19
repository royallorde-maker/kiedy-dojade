package com.kiedziedojade.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kiedziedojade.app.BuildConfig
import com.kiedziedojade.app.core.I18n
import com.kiedziedojade.app.core.I18n.t
import com.kiedziedojade.app.core.Lang
import com.kiedziedojade.app.core.MapStyle
import com.kiedziedojade.app.core.Prefs
import com.kiedziedojade.app.core.World
import com.kiedziedojade.app.core.fmtTimestamp
import com.kiedziedojade.app.ui.theme.AppTheme

@Composable
fun SettingsScreen(
    world: World,
    prefs: Prefs,
    appTheme: AppTheme,
    onThemeChange: (String) -> Unit,
    mapStyle: MapStyle,
    onMapStyleChange: (MapStyle) -> Unit,
    dataNote: String?,
    refreshing: Boolean,
    onRefreshData: () -> Unit,
    onOpenChanges: () -> Unit,
) {
    val uri = LocalUriHandler.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Appear(0) {
                Column {
                    Text(
                        t("Ustawienia", "Settings"),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        t("Motyw, mapa, język i dane", "Theme, map, language and data"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            Appear(1) {
                KdCard(onClick = onOpenChanges) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.NewReleases,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                t("Aktualizacje i zmiany", "Updates and changes"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                t(
                                    "Wersja " + BuildConfig.VERSION_NAME + " · sprawdź lub zobacz historię",
                                    "Version " + BuildConfig.VERSION_NAME + " · check or view history",
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item {
            Appear(1) {
                KdCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Language, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            t("Język", "Language"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TimeChip("Polski", null, I18n.pl) {
                            prefs.lang = "pl"
                            I18n.lang = Lang.PL
                        }
                        TimeChip("English", null, !I18n.pl) {
                            prefs.lang = "en"
                            I18n.lang = Lang.EN
                        }
                    }
                }
            }
        }

        item {
            Appear(2) {
                KdCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            t("Motyw", "Theme"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    // cztery na wiersz — kafelki o równych szerokościach
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppTheme.entries.chunked(4).forEach { rowThemes ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                rowThemes.forEach { th ->
                                    TimeChip(
                                        label = t(themeLabelPl(th), themeLabelEn(th)),
                                        icon = null,
                                        selected = appTheme == th,
                                        onClick = { onThemeChange(th.name) },
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        t(
                            "„Systemowy” idzie za jasnym/ciemnym ustawieniem telefonu.",
                            "\u201ESystem\u201D follows the phone's light/dark setting.",
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            Appear(3) {
                KdCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Map, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                t("Mapa", "Map"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                t("Kafelki OpenStreetMap", "OpenStreetMap tiles"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MapStyle.entries.forEach { style ->
                            TimeChip(
                                label = t(style.labelPl, style.labelEn),
                                icon = null,
                                selected = mapStyle == style,
                                onClick = { onMapStyleChange(style) },
                            )
                        }
                    }
                }
            }
        }

        item {
            Appear(4) {
                KdCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                t("Dane rozkładowe", "Schedule data"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                t("z KiedyPrzyjedzie.pl · KM Szczecinek", "from KiedyPrzyjedzie.pl · Szczecinek"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    DataLine(t("Wygenerowano", "Generated"), fmtTimestamp(world.meta.generatedAt, I18n.pl))
                    DataLine(
                        t("Zawartość", "Contents"),
                        "${world.meta.stopsCount} ${I18n.stops(world.meta.stopsCount)} · " +
                            "${world.meta.tripsCount} ${I18n.trips(world.meta.tripsCount)} · " +
                            "${world.meta.patterns.size} ${I18n.plural(world.meta.patterns.size, "rozkład dnia", "rozkłady dnia", "rozkładów dnia")}",
                    )
                    DataLine(
                        t("Kopia lokalna", "Local copy"),
                        if (world.origin == "cache") {
                            t("zaktualizowana z GitHub", "updated from GitHub")
                        } else {
                            t("wbudowana w aplikację", "bundled with the app")
                        },
                    )
                    dataNote?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onRefreshData, enabled = !refreshing) {
                            if (refreshing) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(t("Odśwież dane teraz", "Refresh data now"))
                        }
                    }
                }
            }
        }

        item {
            Appear(5) {
                KdCard {
                    LinkRow(
                        label = "KiedyPrzyjedzie.pl — Szczecinek",
                        onClick = { uri.openUri("https://szczecinek.kiedyprzyjedzie.pl") },
                    )
                    LinkRow(
                        label = t("Kod aplikacji na GitHubie", "App source on GitHub"),
                        onClick = { uri.openUri(BuildConfig.REPO_URL) },
                    )
                    LinkRow(
                        label = t("Zgłoś problem lub pomysł", "Report an issue or idea"),
                        onClick = { uri.openUri(BuildConfig.REPO_URL + "/issues") },
                    )
                }
            }
        }

        item {
            Text(
                t(
                    "KiedyDojadę " + BuildConfig.VERSION_NAME + " · aplikacja nieoficjalna, tworzona hobbystycznie.",
                    "KiedyDojadę " + BuildConfig.VERSION_NAME + " · unofficial, hobby-built app.",
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun DataLine(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(128.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun LinkRow(label: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        TextButton(onClick = onClick, modifier = Modifier.weight(1f)) {
            Text(label, modifier = Modifier.weight(1f))
        }
        Icon(
            Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
    }
}

private fun themeLabelPl(t: AppTheme): String = when (t) {
    AppTheme.SYSTEM -> "Systemowy"
    AppTheme.MONO -> "Mono"
    AppTheme.GRAFIT -> "Grafit"
    AppTheme.MLEKO -> "Mleko"
    AppTheme.CANDY -> "Cukierek"
    AppTheme.BURSZTYN -> "Bursztyn"
    AppTheme.LAS -> "Las"
    AppTheme.LAWENDA -> "Lawenda"
}

private fun themeLabelEn(t: AppTheme): String = when (t) {
    AppTheme.SYSTEM -> "System"
    AppTheme.MONO -> "Mono"
    AppTheme.GRAFIT -> "Graphite"
    AppTheme.MLEKO -> "Milk"
    AppTheme.CANDY -> "Candy"
    AppTheme.BURSZTYN -> "Amber"
    AppTheme.LAS -> "Forest"
    AppTheme.LAWENDA -> "Lavender"
}
