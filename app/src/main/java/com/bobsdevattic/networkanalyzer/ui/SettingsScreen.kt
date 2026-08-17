package com.bobsdevattic.networkanalyzer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bobsdevattic.networkanalyzer.data.ThemeMode

/** Settings tab. Theme selection (System / Light / Dark), persisted. */
@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onSetThemeMode: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)

        Card(
            Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Theme", style = MaterialTheme.typography.titleMedium)
                ThemeMode.entries.forEach { mode ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = themeMode == mode,
                                onClick = { onSetThemeMode(mode) },
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RadioButton(
                            selected = themeMode == mode,
                            onClick = { onSetThemeMode(mode) },
                        )
                        Text(mode.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}
