package com.dispatcher.companion.ui

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dispatcher.companion.ServiceLocator
import com.dispatcher.companion.calculator.LaneResult
import com.dispatcher.companion.export.CallArchive
import com.dispatcher.companion.export.Exporter
import com.dispatcher.companion.model.FieldKey
import com.dispatcher.companion.model.Speaker
import com.dispatcher.companion.service.DispatchForegroundService
import com.dispatcher.companion.wizard.CheckState
import com.dispatcher.companion.wizard.SetupChecklist

private enum class Tab(val label: String) {
    HOME("Home"), LIVE("Live"), BROKERS("Brokers"), CALC("Calc"), SETUP("Setup")
}

class MainActivity : ComponentActivity() {

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionLauncher.launch(
            arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
        )
        setContent { DispatcherTheme { AppScaffold() } }
    }
}

@Composable
private fun AppScaffold() {
    var tab by remember { mutableStateOf(Tab.HOME) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = {},
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            when (tab) {
                Tab.HOME -> HomeScreen(onGoLive = { tab = Tab.LIVE })
                Tab.LIVE -> LiveScreen()
                Tab.BROKERS -> BrokersScreen()
                Tab.CALC -> CalculatorScreen()
                Tab.SETUP -> SetupScreen()
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) = Text(
    text, fontWeight = FontWeight.Bold, fontSize = 13.sp,
    color = MaterialTheme.colorScheme.secondary,
    modifier = Modifier.padding(vertical = 6.dp),
)

@Composable
private fun HomeScreen(onGoLive: () -> Unit) {
    val context = LocalContext.current
    val active by ServiceLocator.session.active.collectAsStateWithLifecycle()
    val quality by ServiceLocator.session.quality.collectAsStateWithLifecycle()

    Text("DISPATCHER COMPANION", fontWeight = FontWeight.Black, fontSize = 20.sp)
    Text(
        if (active) "Dispatch mode running — audio: ${quality ?: "?"}" else "Idle",
        color = if (active) Green700 else MaterialTheme.colorScheme.secondary,
    )
    Spacer(Modifier.height(24.dp))
    Button(
        onClick = {
            if (active) DispatchForegroundService.stop(context)
            else { DispatchForegroundService.start(context); onGoLive() }
        },
        colors = ButtonDefaults.buttonColors(containerColor = if (active) Red700 else Orange500),
        modifier = Modifier.fillMaxWidth().height(96.dp),
    ) {
        Text(if (active) "STOP DISPATCH MODE" else "START DISPATCH MODE", fontSize = 18.sp,
            fontWeight = FontWeight.Bold)
    }
    Spacer(Modifier.height(16.dp))
    Text(
        "Tip: put RingCentral on speakerphone so both sides are transcribed. " +
            "Calls auto-start dispatch mode once notification access is granted (Setup tab).",
        fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary,
    )
}

@Composable
private fun LiveScreen() {
    val context = LocalContext.current
    val fields by ServiceLocator.session.fields.collectAsStateWithLifecycle()
    val transcript by ServiceLocator.session.transcript.collectAsStateWithLifecycle()
    val liveLine by ServiceLocator.session.liveLine.collectAsStateWithLifecycle()
    val advice by ServiceLocator.session.advice.collectAsStateWithLifecycle()
    val summary by ServiceLocator.session.summary.collectAsStateWithLifecycle()

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(14.dp)) {
                    SectionTitle("PDWCR — LIVE EXTRACTION")
                    for (key in FieldKey.entries.filter { it.isPdwcr }) {
                        Row {
                            Text(
                                key.name, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                color = Orange500, modifier = Modifier.width(110.dp),
                            )
                            Text(fields[key]?.text ?: "—", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(14.dp)) {
                    SectionTitle("NEGOTIATION COPILOT")
                    val a = advice
                    if (a == null) Text("Waiting for the broker to name a number…", fontSize = 13.sp)
                    else {
                        Text(
                            "$%,.0f".format(a.counterUsd), fontSize = 34.sp,
                            fontWeight = FontWeight.Black, color = Orange500,
                            fontFamily = FontFamily.Monospace,
                        )
                        Text(
                            "floor $%,.0f   ceiling $%,.0f   accept %d%%".format(
                                a.likelyFloorUsd, a.likelyCeilingUsd,
                                (a.acceptanceProbability * 100).toInt(),
                            ),
                            fontSize = 13.sp, fontFamily = FontFamily.Monospace,
                        )
                        Text("“${a.suggestedReply}”", fontSize = 13.sp,
                            modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(14.dp)) {
                    SectionTitle("LIVE TRANSCRIPT")
                    transcript.takeLast(8).forEach { s ->
                        Text(
                            "${if (s.speaker == Speaker.BROKER) "Broker" else "Dispatcher"}: ${s.text}",
                            fontSize = 13.sp,
                            color = if (s.speaker == Speaker.BROKER) Red700 else Blue700,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                    liveLine?.let { Text("… $it", fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.secondary) }
                }
            }
        }
        summary?.let { s ->
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(14.dp)) {
                        SectionTitle("CALL SUMMARY")
                        Text(s.summaryBullets, fontSize = 13.sp)
                        Text(
                            "Saved automatically to Documents/DispatcherCompanion " +
                                "(transcript + info as separate files).",
                            fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { Exporter.toClipboard(context, s) }) { Text("COPY") }
                            Button(onClick = { Exporter.share(context, s) }) { Text("SUMMARY") }
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                Exporter.shareText(
                                    context, "Full transcript — ${s.lane}",
                                    CallArchive.transcriptText(ServiceLocator.session.transcript.value),
                                )
                            }) { Text("TRANSCRIPT") }
                            Button(onClick = {
                                Exporter.shareText(
                                    context, "Load info — ${s.lane}",
                                    CallArchive.infoText(
                                        ServiceLocator.session.fields.value,
                                        ServiceLocator.session.rateEventsSnapshot(),
                                    ),
                                )
                            }) { Text("INFO") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BrokersScreen() {
    var query by remember { mutableStateOf("") }
    val results = remember(query) {
        if (query.length >= 2) ServiceLocator.db.searchBrokers(query) else emptyList()
    }
    SectionTitle("BROKER MEMORY")
    OutlinedTextField(
        value = query, onValueChange = { query = it },
        label = { Text("Search name or company") },
        modifier = Modifier.fillMaxWidth(),
    )
    LazyColumn {
        items(results) { (_, name, company) ->
            Text("$name — $company", modifier = Modifier.padding(vertical = 8.dp))
        }
    }
}

@Composable
private fun CalculatorScreen() {
    var origin by remember { mutableStateOf("") }
    var destination by remember { mutableStateOf("") }
    var rate by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<LaneResult?>(null) }

    SectionTitle("FREIGHT CALCULATOR")
    OutlinedTextField(origin, { origin = it }, label = { Text("Origin (city, ST or ZIP)") },
        modifier = Modifier.fillMaxWidth())
    OutlinedTextField(destination, { destination = it }, label = { Text("Destination") },
        modifier = Modifier.fillMaxWidth())
    OutlinedTextField(rate, { rate = it }, label = { Text("Rate \$") },
        modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(8.dp))
    Button(onClick = {
        result = rate.toDoubleOrNull()?.let { ServiceLocator.calculator.lane(origin, destination, it) }
    }) { Text("CALCULATE") }
    result?.let { r ->
        Card(modifier = Modifier.padding(top = 10.dp)) {
            Column(Modifier.padding(14.dp)) {
                Text("Loaded miles: ${r.loadedMiles}", fontFamily = FontFamily.Monospace)
                Text("RPM: $%.2f".format(r.ratePerMile), fontFamily = FontFamily.Monospace)
                Text("Fuel: $%.0f".format(r.fuelCostUsd), fontFamily = FontFamily.Monospace)
                Text("Profit: $%.0f ($%.2f/mi)".format(r.profitUsd, r.profitPerMile),
                    fontFamily = FontFamily.Monospace)
                Text("Profitability: ${r.profitabilityScore}/100", fontWeight = FontWeight.Bold,
                    color = if (r.profitabilityScore >= 50) Green700 else Amber600)
            }
        }
    }
}

@Composable
private fun SetupScreen() {
    val context = LocalContext.current
    var refresh by remember { mutableStateOf(0) }
    val items = remember(refresh) { SetupChecklist.build(context) }

    SectionTitle("XIAOMI / HYPEROS SETUP WIZARD")
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(items) { item ->
            Card {
                Row(Modifier.padding(12.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(item.title, fontWeight = FontWeight.Bold)
                        Text(item.why, fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.secondary)
                    }
                    Button(
                        onClick = { runCatching { context.startActivity(item.fix) }; refresh++ },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = when (item.state) {
                                CheckState.GRANTED -> Green700
                                CheckState.DENIED -> Red700
                                CheckState.MANUAL -> Amber600
                            }
                        ),
                    ) {
                        Text(when (item.state) {
                            CheckState.GRANTED -> "OK"
                            CheckState.DENIED -> "FIX"
                            CheckState.MANUAL -> "OPEN"
                        })
                    }
                }
            }
        }
    }
}
