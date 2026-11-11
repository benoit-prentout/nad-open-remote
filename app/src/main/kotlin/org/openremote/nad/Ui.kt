package org.openremote.nad

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val SOURCES = listOf(
    "Stream" to R.string.src_stream, "Wireless" to R.string.src_wireless, "TV" to R.string.src_tv, "Phono" to R.string.src_phono,
    "Coax1" to R.string.src_coax1, "Coax2" to R.string.src_coax2, "Opt1" to R.string.src_opt1, "Opt2" to R.string.src_opt2,
)
private val CARD = RoundedCornerShape(28.dp)

@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer, selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
)

@Composable
fun Screen(amp: Amp, a: Actions) {
    var settings by remember { mutableStateOf(false) }
    if (settings) SettingsSheet(amp, a) { settings = false }
    Column(
        Modifier.verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Header(amp) { settings = true }
        if (amp.s.isEmpty()) ConnectCard(amp, a)
        else {
            if (!amp.connected) Banner(stringResource(R.string.reconnecting))
            PowerButton(amp, a)
            VolumeCard(amp, a)
            SourceCard(amp, a)
            SoundCard(amp, a)
            InfoCard(amp)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Header(amp: Amp, onSettings: () -> Unit) = Row(verticalAlignment = Alignment.CenterVertically) {
    Column(Modifier.weight(1f)) {
        Text(stringResource(R.string.title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            if (amp.connected) stringResource(R.string.connected_fmt, amp.s["Model"] ?: "") else stringResource(R.string.not_connected),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    IconButton(onSettings) {
        val c = MaterialTheme.colorScheme.onSurfaceVariant
        val bgc = MaterialTheme.colorScheme.background
        Canvas(Modifier.size(24.dp).semantics { contentDescription = "Settings" }) {
            val w = 2.dp.toPx()
            listOf(0.2f to 0.35f, 0.5f to 0.7f, 0.8f to 0.45f).forEach { (y, x) ->
                drawLine(c, Offset(0f, size.height * y), Offset(size.width, size.height * y), w, StrokeCap.Round)
                drawCircle(bgc, 4.dp.toPx(), Offset(size.width * x, size.height * y))
                drawCircle(c, 4.dp.toPx(), Offset(size.width * x, size.height * y), style = Stroke(w))
            }
        }
    }
}

@Composable
private fun Banner(text: String) = Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape)); Spacer(Modifier.width(10.dp))
        Text(text, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun ConnectCard(amp: Amp, a: Actions) {
    Section(stringResource(R.string.find_title)) {
        if (amp.scanning) LinearProgressIndicator(Modifier.fillMaxWidth().padding(bottom = 8.dp))
        amp.message?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp)) }
        Button(a.find, Modifier.fillMaxWidth(), enabled = !amp.scanning) { Text(stringResource(R.string.search)) }
        if (amp.found.isNotEmpty()) {
            Text(stringResource(R.string.amps_found), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
            amp.found.forEach { (host, model) ->
                ListItem(
                    headlineContent = { Text(model) }, supportingContent = { Text(host) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.padding(vertical = 3.dp).clip(RoundedCornerShape(16.dp)).clickable { a.connect(host, 30001) },
                )
            }
        }
    }
    ManualCard(a)
}

@Composable
private fun ManualCard(a: Actions) = Section(stringResource(R.string.manual_title)) {
    var text by remember { mutableStateOf("") }
    var bad by remember { mutableStateOf(false) }
    OutlinedTextField(text, { text = it; bad = false }, Modifier.fillMaxWidth(), singleLine = true, isError = bad,
        label = { Text(stringResource(R.string.manual_label)) }, placeholder = { Text(stringResource(R.string.manual_hint)) },
        supportingText = { if (bad) Text(stringResource(R.string.manual_invalid)) })
    FilledTonalButton({
        val p = parseAddress(text)
        if (p == null) bad = true else a.connect(p.first, p.second)
    }, Modifier.fillMaxWidth().padding(top = 8.dp), enabled = text.isNotBlank()) { Text(stringResource(R.string.connect)) }
}

@Composable
private fun PowerButton(amp: Amp, a: Actions) {
    val haptic = LocalHapticFeedback.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 0.92f else 1f, spring(dampingRatio = 0.5f, stiffness = 600f))
    val bg by animateColorAsState(if (amp.on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, tween(400))
    val fg by animateColorAsState(if (amp.on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, tween(400))
    val ring by animateColorAsState(if (amp.on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.outlineVariant, tween(400))
    Box(Modifier.fillMaxWidth().height(148.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(136.dp).scale(press).border(3.dp, ring, CircleShape), contentAlignment = Alignment.Center) {
            Surface(
                onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); a.send("Power", if (amp.on) "Off" else "On") },
                shape = CircleShape, color = bg, modifier = Modifier.size(112.dp), interactionSource = src,
            ) {
                Canvas(Modifier.padding(34.dp)) {
                    val w = 5.dp.toPx(); val st = Stroke(w, cap = StrokeCap.Round)
                    drawArc(fg, -60f, 300f, false, Offset(w, w), Size(size.width - 2 * w, size.height - 2 * w), style = st)
                    drawLine(fg, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height / 2.2f), w, StrokeCap.Round)
                }
            }
        }
    }
}

@Composable
private fun VolumeCard(amp: Amp, a: Actions) {
    val haptic = LocalHapticFeedback.current
    val vol = amp.volume ?: -80f
    var drag by remember { mutableStateOf<Float?>(null) }
    val shown = (drag ?: vol).coerceAtMost(amp.maxVolume)
    fun set(v: Float) = a.send("Volume", "%.1f".format(java.util.Locale.US, v.coerceIn(-80f, amp.maxVolume)))
    val muted = amp.flag("Mute")
    ElevatedCard(Modifier.fillMaxWidth(), shape = CARD, colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("%.1f".format(shown), fontSize = 60.sp, fontWeight = FontWeight.Light,
                    style = TextStyle(fontFeatureSettings = "tnum"), color = if (muted) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface)
                Text(" dB", Modifier.padding(bottom = 12.dp), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(if (muted) stringResource(R.string.muted).uppercase() else " ", style = MaterialTheme.typography.labelLarge, letterSpacing = 1.5.sp,
                color = MaterialTheme.colorScheme.tertiary.takeIf { muted } ?: MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(shown, { drag = it }, Modifier.fillMaxWidth(), valueRange = -80f..amp.maxVolume,
                onValueChangeFinished = { drag?.let { set(it) }; drag = null },
                colors = SliderDefaults.colors(inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                val btn = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface)
                FilledTonalIconButton({ haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); set(vol - 1) }, Modifier.size(56.dp), colors = btn) { Text("−", fontSize = 24.sp) }
                FilterChip(muted, { a.send("Mute", if (muted) "Off" else "On") }, { Text(stringResource(R.string.mute)) }, Modifier.height(56.dp),
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer, selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer, ),
                    shape = RoundedCornerShape(28.dp))
                FilledTonalIconButton({ haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); set(vol + 1) }, Modifier.size(56.dp), colors = btn) { Text("+", fontSize = 24.sp) }
            }
        }
    }
}

@Composable
private fun SourceCard(amp: Amp, a: Actions) = Section(stringResource(R.string.source)) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SOURCES.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (key, label) ->
                    val sel = amp.s["Source"] == key
                    Surface(
                        onClick = { a.send("Source", key) }, Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(16.dp),
                        color = if (sel) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (sel) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                    ) { Box(contentAlignment = Alignment.Center) { Text(stringResource(label), fontSize = 13.sp, maxLines = 1, textAlign = TextAlign.Center, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal) } }
                }
            }
        }
    }
}

@Composable
private fun SoundCard(amp: Amp, a: Actions) = Section(stringResource(R.string.sound)) {
    @Composable fun row(label: Int, sub: Int, key: String) = Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(amp.flag(key), { a.send(key, if (it) "On" else "Off") },
            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.secondary, checkedThumbColor = MaterialTheme.colorScheme.onSecondary, checkedBorderColor = MaterialTheme.colorScheme.secondary))
    }
    row(R.string.bass, R.string.bass_sub, "Bass")
    row(R.string.autosense, R.string.autosense_sub, "AutoSense")
    row(R.string.autostandby, R.string.autostandby_sub, "AutoStandby")
    row(R.string.controlstandby, R.string.controlstandby_sub, "ControlStandby")
    Spacer(Modifier.height(8.dp))
    Text(stringResource(R.string.brightness), style = MaterialTheme.typography.bodyLarge)
    val b = amp.s["Brightness"]?.toIntOrNull()
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        listOf(R.string.br_off, R.string.br_low, R.string.br_mid, R.string.br_high).forEachIndexed { i, l ->
            SegmentedButton(b == i, { a.send("Brightness", "$i") }, SegmentedButtonDefaults.itemShape(i, 4),
                icon = {}, colors = SegmentedButtonDefaults.colors(activeContainerColor = MaterialTheme.colorScheme.secondaryContainer, activeContentColor = MaterialTheme.colorScheme.onSecondaryContainer, inactiveBorderColor = MaterialTheme.colorScheme.outlineVariant, activeBorderColor = MaterialTheme.colorScheme.secondaryContainer)) { Text(stringResource(l)) }
        }
    }
}

@Composable
private fun InfoCard(amp: Amp) = Section(stringResource(R.string.about)) {
    listOf(R.string.model to amp.s["Model"], R.string.firmware to amp.s["Version"], R.string.analog_gain to amp.s["AnalogGain"]).forEach { (k, v) ->
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            Text(stringResource(k), Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant); Text(v ?: "–")
        }
    }
    Text(stringResource(R.string.disclaimer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) =
    ElevatedCard(Modifier.fillMaxWidth(), shape = CARD, colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(20.dp)) {
            Text(title.uppercase(), style = MaterialTheme.typography.labelLarge, letterSpacing = 1.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 12.dp))
            content()
        }
    }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SettingsSheet(amp: Amp, a: Actions, onClose: () -> Unit) =
    ModalBottomSheet(onClose, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.appearance), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeChoice.values().forEach { t -> FilterChip(amp.theme == t, { a.setTheme(t) }, { Text(stringResource(t.label)) }, colors = chipColors()) }
            }
            Text(stringResource(R.string.language), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("system" to stringResource(R.string.lang_system), "en" to "English", "fr" to "Français").forEach { (code, name) ->
                    FilterChip(amp.lang == code, { a.setLang(code) }, { Text(name) }, colors = chipColors())
                }
            }
            HorizontalDivider()
            Text(stringResource(R.string.volume_limit_fmt, amp.maxVolume.toInt()), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.volume_limit_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(amp.maxVolume, a.setMaxVolume, valueRange = -40f..12f, steps = 51)
            HorizontalDivider()
            Text(stringResource(R.string.amp_section), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.amp_address_fmt, amp.host))
            OutlinedButton({ onClose(); a.forget() }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.change_amp)) }
            HorizontalDivider()
            Text(stringResource(R.string.advanced), style = MaterialTheme.typography.titleMedium)
            var raw by remember { mutableStateOf("") }
            Text(stringResource(R.string.raw_title), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.raw_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(raw, { raw = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Main.Key=Value") })
            Button({ a.raw(raw.trim()); raw = "" }, enabled = raw.startsWith("Main."), modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.send)) }
        }
    }
