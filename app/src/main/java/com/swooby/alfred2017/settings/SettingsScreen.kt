package com.swooby.alfred2017.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.swooby.alfred2017.AlfredApp

@Composable
fun SettingsScreen(app: AlfredApp) {
    val vm = settingsViewModel(app)
    val rules by vm.rules.collectAsState()

    // Local editable copies derived from state
    var quietStart by remember { mutableStateOf(rules.quietHours?.start?.toString() ?: "") }
    var quietEnd by remember { mutableStateOf(rules.quietHours?.end?.toString() ?: "") }
    var disabledAppsCsv by remember { mutableStateOf(rules.disabledApps.joinToString(",")) }
    var enabledTypesCsv by remember { mutableStateOf(rules.enabledTypes.joinToString(",")) }
    var speakScreenOff by remember { mutableStateOf(rules.speakWhenScreenOffOnly) }

    SettingsContent(
        quietStart = quietStart,
        quietEnd = quietEnd,
        disabledAppsCsv = disabledAppsCsv,
        enabledTypesCsv = enabledTypesCsv,
        speakScreenOff = speakScreenOff,
        onSaveQuietHours = { s, e -> vm.updateQuietHours(s, e) },
        onSpeakScreenOffChange = { checked ->
            speakScreenOff = checked
            vm.updateSpeakWhenScreenOff(checked)
        },
        onSaveDisabledApps = { csv -> vm.updateDisabledAppsCsv(csv) },
        onSaveEnabledTypes = { csv -> vm.updateEnabledTypesCsv(csv) },
        onLocalQuietStartChange = { quietStart = it },
        onLocalQuietEndChange = { quietEnd = it },
        onLocalDisabledAppsChange = { disabledAppsCsv = it },
        onLocalEnabledTypesChange = { enabledTypesCsv = it },
    )
}

/** Pure UI: easy to preview & unit-test */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    quietStart: String,
    quietEnd: String,
    disabledAppsCsv: String,
    enabledTypesCsv: String,
    speakScreenOff: Boolean,
    onSaveQuietHours: (String?, String?) -> Unit,
    onSpeakScreenOffChange: (Boolean) -> Unit,
    onSaveDisabledApps: (String) -> Unit,
    onSaveEnabledTypes: (String) -> Unit,
    // Local state mutators for text fields (so caller can mirror edits)
    onLocalQuietStartChange: (String) -> Unit,
    onLocalQuietEndChange: (String) -> Unit,
    onLocalDisabledAppsChange: (String) -> Unit,
    onLocalEnabledTypesChange: (String) -> Unit,
) {
    Scaffold(topBar = { TopAppBar(title = { Text("Alfred Settings") }) }) { pad ->
        Column(
            Modifier
                .padding(pad)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            Text("Quiet Hours (HH:mm)", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = quietStart,
                    onValueChange = onLocalQuietStartChange,
                    label = { Text("Start e.g. 22:00") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = quietEnd,
                    onValueChange = onLocalQuietEndChange,
                    label = { Text("End e.g. 07:00") },
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        onSaveQuietHours(
                            quietStart.ifBlank { null },
                            quietEnd.ifBlank { null }
                        )
                    }
                ) { Text("Save") }
            }

            Row {
                Checkbox(
                    checked = speakScreenOff,
                    onCheckedChange = { onSpeakScreenOffChange(it) }
                )
                Text("Speak only when screen is OFF", modifier = Modifier.padding(start = 8.dp))
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "Disabled apps (CSV of package names)",
                style = MaterialTheme.typography.titleMedium
            )
            OutlinedTextField(
                value = disabledAppsCsv,
                onValueChange = onLocalDisabledAppsChange,
                modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = { onSaveDisabledApps(disabledAppsCsv) }) { Text("Save") }

            Spacer(Modifier.height(8.dp))
            Text("Enabled event types (CSV)", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = enabledTypesCsv,
                onValueChange = onLocalEnabledTypesChange,
                modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = { onSaveEnabledTypes(enabledTypesCsv) }) { Text("Save") }
        }
    }
}

/** Compose Preview — no DataStore or ViewModel required */
@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun SettingsContent_Preview() {
    // Preview uses local state to exercise the UI
    var quietStart by remember { mutableStateOf("22:00") }
    var quietEnd by remember { mutableStateOf("07:00") }
    var disabledAppsCsv by remember { mutableStateOf("com.reddit.frontpage,com.twitter.android") }
    var enabledTypesCsv by remember { mutableStateOf("media.start,media.stop,notif.post,display.on,display.off") }
    var speakScreenOff by remember { mutableStateOf(false) }

    MaterialTheme {
        SettingsContent(
            quietStart = quietStart,
            quietEnd = quietEnd,
            disabledAppsCsv = disabledAppsCsv,
            enabledTypesCsv = enabledTypesCsv,
            speakScreenOff = speakScreenOff,
            onSaveQuietHours = { _, _ -> /* no-op in preview */ },
            onSpeakScreenOffChange = { speakScreenOff = it },
            onSaveDisabledApps = { /* no-op in preview */ },
            onSaveEnabledTypes = { /* no-op in preview */ },
            onLocalQuietStartChange = { quietStart = it },
            onLocalQuietEndChange = { quietEnd = it },
            onLocalDisabledAppsChange = { disabledAppsCsv = it },
            onLocalEnabledTypesChange = { enabledTypesCsv = it },
        )
    }
}
