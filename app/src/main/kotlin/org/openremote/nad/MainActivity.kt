package org.openremote.nad

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val SOURCES = listOf(
    "Stream" to "Streaming", "Wireless" to "Bluetooth", "TV" to "TV", "Phono" to "Phono",
    "Coax1" to "Coax 1", "Coax2" to "Coax 2", "Opt1" to "Optical 1", "Opt2" to "Optical 2",
)

// ponytail: all state lives in this one holder; split into a ViewModel only if screens multiply.
private class Amp(val onSend: (String, String) -> Unit) {
    val s = mutableStateMapOf<String, String>()
    var connected by mutableStateOf(false)
    var scanning by mutableStateOf(false)
    var message by mutableStateOf("")
    val on get() = s["Power"] == "On"
    val volume get() = s["Volume"]?.toFloatOrNull()
    fun flag(k: String) = s[k] == "On"
}

class MainActivity : ComponentActivity() {
    private var client: NadClient? = null
    private lateinit var amp: Amp

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        enableEdgeToEdge()
        val prefs = getSharedPreferences("p", MODE_PRIVATE)
        amp = Amp { k, v -> client?.set(k, v) }
        var host by mutableStateOf(prefs.getString("ip", "") ?: "")

        fun connect(h: String, rescan: Boolean = false) {
            host = h; prefs.edit().putString("ip", h).apply(); client?.stop()
            var ever = false
            client = NadClient(h, onState = { k, v -> runOnUiThread { amp.s[k] = v } }, onConnected = { ok ->
                runOnUiThread {
                    if (ok) ever = true
                    amp.connected = ok
                    if (!ok && !ever && rescan) { client?.stop(); amp.message = "Saved address unreachable, searching…"; find(::connectNoRescan) }
                }
            }).also { it.start() }
        }
        connectRef = { h -> connect(h) }
        fun find() = find(::connectNoRescan)

        if (host.isNotBlank()) connect(host, rescan = true) else find()

        setContent {
            AmpTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Screen(amp, host,
                        onHost = { host = it }, onConnect = { connect(host.trim()) }, onFind = { find() })
                }
            }
        }
    }

    private var connectRef: (String) -> Unit = {}
    private fun connectNoRescan(h: String) = connectRef(h)

    private fun find(onFound: (String) -> Unit) {
        amp.scanning = true; amp.message = "Searching your network…"
        Thread {
            val hit = scan(localSubnetHosts()).keys.firstOrNull()
            runOnUiThread {
                amp.scanning = false
                if (hit == null) amp.message = "No amp found. Enter its address below." else { amp.message = ""; onFound(hit) }
            }
        }.start()
    }

    override fun onDestroy() { client?.stop(); super.onDestroy() }
}

@Composable
private fun AmpTheme(content: @Composable () -> Unit) {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
private fun Screen(amp: Amp, host: String, onHost: (String) -> Unit, onConnect: () -> Unit, onFind: () -> Unit) {
    Column(
        Modifier.verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Header(amp)
        if (!amp.connected) ConnectCard(amp, host, onHost, onConnect, onFind)
        else {
            PowerButton(amp)
            VolumeCard(amp)
            SourceCard(amp)
            SoundCard(amp)
            InfoCard(amp)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Header(amp: Amp) {
    Column {
        Text("Open Remote", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            if (amp.connected) "${amp.s["Model"] ?: "Amp"} · connected" else "Not connected",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ConnectCard(amp: Amp, host: String, onHost: (String) -> Unit, onConnect: () -> Unit, onFind: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Find your amp", style = MaterialTheme.typography.titleLarge)
            if (amp.scanning) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (amp.message.isNotEmpty()) Text(amp.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onFind, Modifier.fillMaxWidth(), enabled = !amp.scanning) { Text("Search my network") }
            OutlinedTextField(host, onHost, Modifier.fillMaxWidth(), label = { Text("Or enter the address") },
                placeholder = { Text("192.168.1.30") }, singleLine = true)
            FilledTonalButton(onConnect, Modifier.fillMaxWidth(), enabled = host.isNotBlank()) { Text("Connect") }
        }
    }
}

@Composable
private fun PowerButton(amp: Amp) {
    val haptic = LocalHapticFeedback.current
    val bg by animateColorAsState(if (amp.on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, tween(400))
    val fg by animateColorAsState(if (amp.on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, tween(400))
    val pulse by rememberInfiniteTransition().animateFloat(1f, 1.08f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse))
    Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
        if (amp.on) Box(Modifier.size(150.dp).scale(pulse).alpha(0.25f).background(bg, CircleShape))
        Surface(
            onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); amp.onSend("Power", if (amp.on) "Off" else "On") },
            shape = CircleShape, color = bg, modifier = Modifier.size(120.dp), shadowElevation = 6.dp,
        ) {
            Canvas(Modifier.padding(36.dp)) {
                val w = 5.dp.toPx(); val st = Stroke(w, cap = StrokeCap.Round)
                drawArc(fg, -60f, 300f, false, Offset(w, w), Size(size.width - 2 * w, size.height - 2 * w), style = st)
                drawLine(fg, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height / 2.2f), w, StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun VolumeCard(amp: Amp) {
    val haptic = LocalHapticFeedback.current
    val vol = amp.volume ?: -80f
    var drag by remember { mutableStateOf<Float?>(null) }
    val shown = drag ?: vol
    fun set(v: Float) = amp.onSend("Volume", "%.1f".format(v.coerceIn(-80f, 12f)))
    val muted = amp.flag("Mute")
    ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp)) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("%.1f".format(shown), fontSize = 56.sp, fontWeight = FontWeight.Light,
                color = if (muted) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface)
            Text(if (muted) "dB · muted" else "dB", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(shown, { drag = it }, Modifier.fillMaxWidth(), valueRange = -80f..12f,
                onValueChangeFinished = { drag?.let { set(it) }; drag = null })
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton({ haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); set(vol - 1) }, Modifier.size(56.dp)) { Text("−", fontSize = 24.sp) }
                FilterChip(muted, { amp.onSend("Mute", if (muted) "Off" else "On") }, { Text("Mute") })
                FilledTonalIconButton({ haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); set(vol + 1) }, Modifier.size(56.dp)) { Text("+", fontSize = 24.sp) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SourceCard(amp: Amp) = Section("Source") {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SOURCES.forEach { (key, label) -> FilterChip(amp.s["Source"] == key, { amp.onSend("Source", key) }, { Text(label) }) }
    }
}

@Composable
private fun SoundCard(amp: Amp) = Section("Sound & settings") {
    @Composable fun row(label: String, sub: String, key: String) = Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(label, style = MaterialTheme.typography.bodyLarge); Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Switch(amp.flag(key), { amp.onSend(key, if (it) "On" else "Off") })
    }
    row("Bass EQ", "+6 dB boost", "Bass")
    row("Auto-sense", "Switch to the input that plays", "AutoSense")
    row("Auto standby", "Sleep after 20 min idle", "AutoStandby")
    row("Network control in standby", "Keep the amp reachable", "ControlStandby")
    Spacer(Modifier.height(8.dp))
    Text("Display brightness", style = MaterialTheme.typography.bodyLarge)
    val b = amp.s["Brightness"]?.toIntOrNull()
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        listOf("Off", "Low", "Mid", "High").forEachIndexed { i, l ->
            SegmentedButton(b == i, { amp.onSend("Brightness", "$i") }, SegmentedButtonDefaults.itemShape(i, 4)) { Text(l) }
        }
    }
}

@Composable
private fun InfoCard(amp: Amp) = Section("About this amp") {
    listOf("Model" to amp.s["Model"], "Firmware" to amp.s["Version"], "Analog gain (read-only)" to amp.s["AnalogGain"]).forEach { (k, v) ->
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            Text(k, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant); Text(v ?: "–")
        }
    }
    Text("Unofficial. Not affiliated with NAD Electronics.", style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) =
    ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp)) {
        Column(Modifier.padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
            content()
        }
    }
