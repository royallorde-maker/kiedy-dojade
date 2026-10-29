package com.kiedziedojade.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kiedziedojade.app.BuildConfig
import com.kiedziedojade.app.core.I18n
import com.kiedziedojade.app.core.I18n.t
import com.kiedziedojade.app.core.Lang
import com.kiedziedojade.app.core.MapStyle
import com.kiedziedojade.app.core.Prefs
import com.kiedziedojade.app.core.World
import com.kiedziedojade.app.core.fmtTimestamp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    world: World,
    prefs: Prefs,
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
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Appear(0) {
                ScreenHeader(
                    title = t("Ustawienia", "Settings"),
                    subtitle = t("Mapa, język i dane", "Map, language and data"),
                    large = true,
                )
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
                                    "Wersja " + BuildConfig.VERSION_NAME + " · historia zmian",
                                    "Version " + BuildConfig.VERSION_NAME + " · release history",
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
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
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
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
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
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
            Appear(3) {
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
                            "${world.meta.patterns.size} " + if (I18n.pl) {
                                I18n.plural(
                                    world.meta.patterns.size,
                                    "rozkład dnia",
                                    "rozkłady dnia",
                                    "rozkładów dnia",
                                )
                            } else {
                                I18n.plural(
                                    world.meta.patterns.size,
                                    "day pattern",
                                    "day patterns",
                                    "day patterns",
                                )
                            },
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
            Appear(4) {
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
            modifier = Modifier.width(112.dp),
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
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
    }
}
