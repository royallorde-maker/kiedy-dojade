package com.kiedziedojade.app.ui

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import com.kiedziedojade.app.core.I18n.t
import com.kiedziedojade.app.core.WARSAW
import java.time.Instant
import java.time.ZoneOffset

/** Wybór dnia podróży — zwraca ISO (YYYY-MM-DD) w strefie Szczecinka. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickDialog(
    initial: String?,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    val initialMillis = initial?.let {
        runCatching {
            java.time.LocalDate.parse(it).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }.getOrNull()
    } ?: System.currentTimeMillis()
    val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val ms = state.selectedDateMillis
                if (ms != null) {
                    val iso = Instant.ofEpochMilli(ms).atZone(WARSAW).toLocalDate().toString()
                    onPick(iso)
                } else {
                    onDismiss()
                }
            }) {
                Text(t("Gotowe", "Done"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(t("Anuluj", "Cancel"))
            }
        },
    ) {
        DatePicker(state = state, title = null, headline = null, showModeToggle = false)
    }
}
