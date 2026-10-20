package com.kiedziedojade.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kiedziedojade.app.core.I18n.t
import com.kiedziedojade.app.core.nowMinutes

/**
 * Wybór godziny odjazdu. Data znika z interfejsu: godzina, która już minęła,
 * oznacza najbliższe wystąpienie, czyli jutro.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickDialog(
    initial: Int?,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit,
) {
    val start = initial ?: nowMinutes()
    val state = rememberTimePickerState(
        initialHour = start / 60,
        initialMinute = start % 60,
        is24Hour = true,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Godzina odjazdu", "Departure time")) },
        text = {
            Column {
                TimeInput(state = state, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                Text(
                    t(
                        "Jeśli ta godzina już dziś minęła, pokażemy najbliższy kurs jutro.",
                        "If this time has already passed today, we'll show tomorrow's next trip.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(state.hour * 60 + state.minute) }) {
                Text(t("Gotowe", "Done"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(t("Anuluj", "Cancel"))
            }
        },
    )
}
