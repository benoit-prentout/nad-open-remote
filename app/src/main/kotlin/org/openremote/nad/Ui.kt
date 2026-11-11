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
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Path

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
            MediaCard(a)
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
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            if (amp.connected) stringResource(R.string.connected_fmt, amp.s["Model"] ?: "") else stringResource(R.string.not_connected),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    IconButton(onSettings) {
        val c = MaterialTheme.colorScheme.onSurfaceVariant
        val bgc = MaterialTheme.colorScheme.background
        val cd = stringResource(R.string.settings)
        Canvas(Modifier.size(24.dp).semantics { contentDescription = cd }) {
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
    val powerCd = stringResource(R.string.cd_power); val powerState = stringResource(if (amp.on) R.string.on else R.string.off)
    val boot = remember { Animatable(1f) }
    LaunchedEffect(amp.on) { if (amp.on) { boot.snapTo(0f); boot.animateTo(1f, tween(1100, easing = FastOutSlowInEasing)) } }
    val rippleC = MaterialTheme.colorScheme.primary
    Box(Modifier.fillMaxWidth().height(148.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(148.dp)) {
            if (boot.value < 1f) drawCircle(rippleC.copy(alpha = (1f - boot.value) * 0.6f), size.minDimension / 2 * (0.7f + 0.3f * boot.value), style = Stroke(4.dp.toPx()))
        }
        Box(Modifier.size(136.dp).scale(press).border(3.dp, ring, CircleShape), contentAlignment = Alignment.Center) {
            Surface(
                onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); a.send("Power", if (amp.on) "Off" else "On") },
                shape = CircleShape, color = bg, interactionSource = src,
                modifier = Modifier.size(112.dp).semantics { contentDescription = powerCd; stateDescription = powerState },
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
    var pending by remember { mutableStateOf<Float?>(null) } // last value we sent, shown until the amp echoes it back
    var lastSent by remember { mutableStateOf(0L) }
    LaunchedEffect(pending) { if (pending != null) { kotlinx.coroutines.delay(1200); pending = null } }
    LaunchedEffect(vol) { if (pending != null && kotlin.math.abs(vol - pending!!) < 0.05f) pending = null }
    val shown = (drag ?: pending ?: vol).coerceAtMost(amp.maxVolume)
    fun set(v: Float) { pending = v; a.send("Volume", "%.1f".format(java.util.Locale.US, v.coerceIn(-80f, amp.maxVolume))) }
    val muted = amp.flag("Mute")
    val srcLabel = SOURCES.firstOrNull { it.first == amp.s["Source"] }?.second?.let { stringResource(it) } ?: (amp.s["Source"] ?: "")
    ElevatedCard(Modifier.fillMaxWidth(), shape = CARD, colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            VfdDisplay(srcLabel, amp.on, muted, shown)
            VolumeKnob(shown, -80f, amp.maxVolume, stringResource(R.string.cd_volume),
                { v -> drag = v; val t = System.currentTimeMillis(); if (t - lastSent >= 80) { lastSent = t; set(v) } }, // live: at most one command per 80 ms
                { drag?.let { set(it) }; drag = null }, { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) })
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                val btn = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface)
                val s1 = remember { MutableInteractionSource() }; val s2 = remember { MutableInteractionSource() }
                FilledTonalIconButton({ haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); set(vol - 1) }, Modifier.size(56.dp).bounce(s1), colors = btn, interactionSource = s1) { Text("−", fontSize = 24.sp) }
                FilterChip(muted, { a.send("Mute", if (muted) "Off" else "On") }, { Text(stringResource(R.string.mute)) }, Modifier.height(56.dp),
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer, selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer),
                    shape = RoundedCornerShape(28.dp))
                FilledTonalIconButton({ haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); set(vol + 1) }, Modifier.size(56.dp).bounce(s2), colors = btn, interactionSource = s2) { Text("+", fontSize = 24.sp) }
            }
        }
    }
}

@Composable
private fun MediaCard(a: Actions) = Section(stringResource(R.string.playback)) {
    val haptic = LocalHapticFeedback.current
    val fg = MaterialTheme.colorScheme.onSurface
    @Composable fun key(code: Int, kind: String) { val desc = stringResource(when (kind) { "play" -> R.string.cd_play; "pause" -> R.string.cd_pause; "next" -> R.string.cd_next; else -> R.string.cd_prev }); val src = remember { MutableInteractionSource() }; FilledTonalIconButton(
        { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); a.media(code) }, Modifier.size(64.dp).bounce(src).semantics { contentDescription = desc }, interactionSource = src,
        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = fg),
    ) {
        Canvas(Modifier.size(24.dp)) {
            val w = size.width; val h = size.height
            fun tri(x0: Float, x1: Float) = Path().apply { moveTo(x0, 0f); lineTo(x1, h / 2); lineTo(x0, h); close() }
            fun triL(x0: Float, x1: Float) = Path().apply { moveTo(x1, 0f); lineTo(x0, h / 2); lineTo(x1, h); close() }
            when (kind) {
                "play" -> drawPath(tri(w * 0.2f, w * 0.95f), fg)
                "pause" -> { drawRect(fg, Offset(w * 0.15f, 0f), Size(w * 0.25f, h)); drawRect(fg, Offset(w * 0.6f, 0f), Size(w * 0.25f, h)) }
                "next" -> { drawPath(tri(0f, w * 0.75f), fg); drawRect(fg, Offset(w * 0.8f, 0f), Size(w * 0.18f, h)) }
                else -> { drawPath(triL(w * 0.25f, w), fg); drawRect(fg, Offset(0f, 0f), Size(w * 0.18f, h)) }
            }
        }
    } }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        key(android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS, "prev")
        key(android.view.KeyEvent.KEYCODE_MEDIA_PLAY, "play")
        key(android.view.KeyEvent.KEYCODE_MEDIA_PAUSE, "pause")
        key(android.view.KeyEvent.KEYCODE_MEDIA_NEXT, "next")
    }
    Text(stringResource(R.string.playback_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
}

@Composable
private fun SourceCard(amp: Amp, a: Actions) = Section(stringResource(R.string.source)) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = 8.dp; val cellH = 52.dp; val cellW = (maxWidth - gap * 3) / 4
        val idx = SOURCES.indexOfFirst { it.first == amp.s["Source"] }
        val x by animateDpAsState((cellW + gap) * (idx.coerceAtLeast(0) % 4), spring(0.7f, 450f), label = "px")
        val y by animateDpAsState((cellH + gap) * (idx.coerceAtLeast(0) / 4), spring(0.7f, 450f), label = "py")
        if (idx >= 0) Box(Modifier.offset(x, y).size(cellW, cellH).background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(16.dp)))
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            SOURCES.chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { (key, label) ->
                        val sel = amp.s["Source"] == key
                        val src = remember { MutableInteractionSource() }
                        Surface(
                            onClick = { a.send("Source", key) }, Modifier.weight(1f).height(cellH).bounce(src), shape = RoundedCornerShape(16.dp), interactionSource = src,
                            color = if (sel) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (sel) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                        ) { Box(contentAlignment = Alignment.Center) { Text(stringResource(label), fontSize = 13.sp, maxLines = 1, textAlign = TextAlign.Center, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun SoundCard(amp: Amp, a: Actions) = Section(stringResource(R.string.sound)) {
    @Composable fun row(label: Int, sub: Int, key: String) = Row(Modifier.fillMaxWidth().toggleable(amp.flag(key), role = Role.Switch) { a.send(key, if (it) "On" else "Off") }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(amp.flag(key), null,
            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.secondary, checkedThumbColor = MaterialTheme.colorScheme.onSecondary, checkedBorderColor = MaterialTheme.colorScheme.secondary))
    }
    row(R.string.bass, R.string.bass_sub, "Bass")
    row(R.string.autosense, R.string.autosense_sub, "AutoSense")
    row(R.string.autostandby, R.string.autostandby_sub, "AutoStandby")
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
    listOf(R.string.model to amp.s["Model"], R.string.firmware to amp.s["Version"], R.string.controlstandby to amp.s["ControlStandby"], R.string.analog_gain to amp.s["AnalogGain"]).forEach { (k, v) ->
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
            Slider(amp.maxVolume, { a.setMaxVolume(Math.round(it).toFloat()) }, valueRange = -40f..12f) // whole dB, no tick marks
            Row(Modifier.fillMaxWidth().toggleable(amp.volKeys, role = Role.Switch, onValueChange = a.setVolKeys), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.vol_keys), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.vol_keys_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(amp.volKeys, null, colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.secondary, checkedThumbColor = MaterialTheme.colorScheme.onSecondary, checkedBorderColor = MaterialTheme.colorScheme.secondary))
            }
            if (amp.volKeys) Row(Modifier.fillMaxWidth().toggleable(amp.bgKeys, role = Role.Switch, onValueChange = a.setBgKeys), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.bg_keys), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.bg_keys_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(amp.bgKeys, null, colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.secondary, checkedThumbColor = MaterialTheme.colorScheme.onSecondary, checkedBorderColor = MaterialTheme.colorScheme.secondary))
            }
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
            HorizontalDivider()
            ConnectionLog()
        }
    }

@Composable
private fun ConnectionLog() {
    val ctx = LocalContext.current
    val lines = ConnLog.lines
    Text(stringResource(R.string.conn_log), style = MaterialTheme.typography.bodyLarge)
    Text(stringResource(R.string.conn_log_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    var shown by remember { mutableStateOf(false) } // collapsed by default: the log is long
    if (shown) Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Text(
            if (lines.isEmpty()) stringResource(R.string.log_empty) else lines.takeLast(30).reversed().joinToString("\n"),
            Modifier.padding(12.dp), fontSize = 11.sp, lineHeight = 15.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton({ shown = !shown }) { Text(stringResource(if (shown) R.string.hide_log else R.string.show_log)) }
        OutlinedButton({
            val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
            cm.setPrimaryClip(android.content.ClipData.newPlainText("log", lines.joinToString("\n")))
        }, enabled = lines.isNotEmpty()) { Text(stringResource(R.string.copy)) }
        OutlinedButton({ ConnLog.clear(ctx) }, enabled = lines.isNotEmpty()) { Text(stringResource(R.string.clear)) }
    }
}
