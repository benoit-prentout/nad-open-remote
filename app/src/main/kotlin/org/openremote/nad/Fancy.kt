package org.openremote.nad

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Springy press feedback for any button that exposes its interactionSource. */
@Composable
fun Modifier.bounce(src: MutableInteractionSource): Modifier {
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.92f else 1f, spring(dampingRatio = 0.5f, stiffness = 600f), label = "bounce")
    return this.scale(s)
}

private val PANEL = Color(0xFF050607)
private val GLOW = Color(0xFFE4F0FF)
private val DIM = Color(0xFF7F8A99)

/** Front-panel style display: source on the left, volume on the right, digits roll when the value changes. */
@Composable
fun VfdDisplay(sourceLabel: String, on: Boolean, muted: Boolean, volume: Float) {
    val alpha by animateFloatAsState(if (on) 1f else 0.25f, tween(900), label = "warmup")
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(PANEL).padding(horizontal = 18.dp, vertical = 14.dp).alpha(alpha),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            val mono = TextStyle(fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
            Text(if (on) sourceLabel.uppercase() else stringResource(R.string.standby).uppercase(), style = mono, fontSize = 15.sp, color = GLOW, maxLines = 1)
            Text(if (muted) stringResource(R.string.muted).uppercase() else "VOL", style = mono, fontSize = 12.sp, color = if (muted) MaterialTheme.colorScheme.tertiary else DIM)
        }
        AnimatedContent(
            targetState = volume,
            transitionSpec = {
                if (targetState > initialState) (slideInVertically { -it / 2 } + fadeIn()) togetherWith (slideOutVertically { it / 2 } + fadeOut())
                else (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut())
            }, label = "digits",
        ) { v ->
            Text("%.1f".format(java.util.Locale.US, v), color = if (muted) DIM else GLOW, fontSize = 44.sp, fontWeight = FontWeight.Light,
                style = TextStyle(fontFamily = FontFamily.Monospace))
        }
    }
}

/** Rotary volume knob: drag in circles (relative, no jumps); one haptic tick per dB. 270 degree sweep. */
@Composable
fun VolumeKnob(value: Float, min: Float, max: Float, onChange: (Float) -> Unit, onDone: () -> Unit, onTick: () -> Unit) {
    val trackC = MaterialTheme.colorScheme.surfaceContainerHighest
    val activeC = MaterialTheme.colorScheme.primary
    val bodyC = MaterialTheme.colorScheme.surfaceVariant
    val markC = MaterialTheme.colorScheme.onSurface
    val ringC = MaterialTheme.colorScheme.outlineVariant
    val frac = ((value - min) / (max - min)).coerceIn(0f, 1f)
    val current by rememberUpdatedState(value)
    Canvas(
        Modifier.size(220.dp).pointerInput(min, max) {
            var last = 0f; var v = 0f; var lastDb = 0
            fun ang(o: Offset) = Math.toDegrees(atan2((o.y - size.height / 2f).toDouble(), (o.x - size.width / 2f).toDouble())).toFloat()
            detectDragGestures(
                onDragStart = { o -> last = ang(o); v = current; lastDb = v.roundToInt() },
                onDragEnd = onDone, onDragCancel = onDone,
            ) { change, _ ->
                change.consume()
                val a = ang(change.position)
                var d = a - last; if (d > 180) d -= 360; if (d < -180) d += 360
                last = a
                v = (v + d / 270f * (max - min) * volumeGain(v, max)).coerceIn(min, max)
                val db = v.roundToInt()
                if (db != lastDb) { lastDb = db; onTick() }
                onChange(db.toFloat())
            }
        },
    ) {
        val stroke = 14.dp.toPx()
        val r = size.minDimension / 2 - stroke
        val c = Offset(size.width / 2, size.height / 2)
        val tl = Offset(c.x - r, c.y - r); val sz = androidx.compose.ui.geometry.Size(2 * r, 2 * r)
        drawArc(trackC, 135f, 270f, false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
        if (frac > 0f) drawArc(activeC, 135f, 270f * frac, false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
        val br = r * 0.72f
        drawCircle(bodyC, br, c)
        drawCircle(ringC, br, c, style = Stroke(2.dp.toPx()))
        val th = (135f + 270f * frac) * PI.toFloat() / 180f
        drawLine(markC, Offset(c.x + cos(th) * br * 0.45f, c.y + sin(th) * br * 0.45f), Offset(c.x + cos(th) * br * 0.85f, c.y + sin(th) * br * 0.85f), 5.dp.toPx(), StrokeCap.Round)
    }
}
