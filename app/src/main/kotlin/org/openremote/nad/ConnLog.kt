package org.openremote.nad

import android.content.Context
import android.net.wifi.WifiManager
import androidx.compose.runtime.mutableStateListOf
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Connection event log, kept on the phone (filesDir) so a drop can be diagnosed later. Newest line last. */
object ConnLog {
    val lines = mutableStateListOf<String>()
    private const val MAX_LINES = 300
    private var loaded = false

    private fun file(ctx: Context) = File(ctx.applicationContext.filesDir, "connection.log")

    @Synchronized fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        runCatching { lines.addAll(file(ctx).readLines().takeLast(MAX_LINES)) }
    }

    @Synchronized fun add(ctx: Context, msg: String) {
        load(ctx)
        val line = "${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())} · $msg · ${wifi(ctx)}"
        lines.add(line)
        while (lines.size > MAX_LINES) lines.removeAt(0)
        runCatching { file(ctx).writeText(lines.joinToString("\n") + "\n") }
    }

    @Synchronized fun clear(ctx: Context) { lines.clear(); runCatching { file(ctx).delete() } }

    @Suppress("DEPRECATION")
    private fun wifi(ctx: Context): String = runCatching {
        val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val i = wm.connectionInfo
        if (!wm.isWifiEnabled) "Wi-Fi off" else if (i.rssi <= -127) "Wi-Fi ?" else "Wi-Fi ${i.rssi} dBm ${i.linkSpeed} Mbps"
    }.getOrDefault("Wi-Fi ?")
}
