package com.swooby.alfred2017.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.swooby.alfred2017.AlfredApp

@Composable
fun SettingsScreen(app: AlfredApp) {
    val vm = settingsViewModel(app)
    val rules by vm.rules.collectAsState()

    var quietStart by remember { mutableStateOf(rules.quietHours?.start?.toString() ?: "") }
    var quietEnd   by remember { mutableStateOf(rules.quietHours?.end?.toString() ?: "") }
    var disabledAppsCsv by remember { mutableStateOf(rules.disabledApps.joinToString(",")) }
    var enabledTypesCsv by remember { mutableStateOf(rules.enabledTypes.joinToString(",")) }
    var speakScreenOff by remember { mutableStateOf(rules.speakWhenScreenOffOnly) }

    Scaffold(topBar = { TopAppBar(title = { Text("Alfred Settings") }) }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Quiet Hours (HH:mm)", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = quietStart, onValueChange = { quietStart = it }, label = { Text("Start 22:00") }, modifier = Modifier.weight(1f))
                OutlinedTextField(value = quietEnd, onValueChange = { quietEnd = it }, label = { Text("End 07:00") }, modifier = Modifier.weight(1f))
                Button(onClick = { vm.updateQuietHours(quietStart.ifBlank { null }, quietEnd.ifBlank { null }) }) { Text("Save") }
            }
            Row {
                Checkbox(checked = speakScreenOff, onCheckedChange = { speakScreenOff = it; vm.updateSpeakWhenScreenOff(it) })
                Text("Speak only when screen is OFF", modifier = Modifier.padding(start = 8.dp))
            }
            Text("Disabled apps (CSV of package names)", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(value = disabledAppsCsv, onValueChange = { disabledAppsCsv = it }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { vm.updateDisabledAppsCsv(disabledAppsCsv) }) { Text("Save") }
            Text("Enabled event types (CSV)", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(value = enabledTypesCsv, onValueChange = { enabledTypesCsv = it }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { vm.updateEnabledTypesCsv(enabledTypesCsv) }) { Text("Save") }
        }
    }
}