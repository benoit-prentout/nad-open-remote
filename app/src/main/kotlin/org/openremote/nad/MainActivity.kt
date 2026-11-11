package org.openremote.nad

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.widget.*

// ponytail: programmatic views, no Compose/XML. Move to Compose if the UI grows.
class MainActivity : Activity() {
    private var client: NadClient? = null
    private val state = HashMap<String, String>()
    private lateinit var status: TextView
    private lateinit var info: TextView

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val prefs = getSharedPreferences("p", MODE_PRIVATE)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 60, 40, 40) }
        val ip = EditText(this).apply { hint = "Amp IP (e.g. 192.168.1.30)"; setText(prefs.getString("ip", "")); inputType = InputType.TYPE_CLASS_PHONE }
        status = TextView(this); info = TextView(this).apply { textSize = 18f }
        fun btn(label: String, f: () -> Unit) = Button(this).apply { text = label; setOnClickListener { f() } }
        fun send(k: String, v: String) { client?.set(k, v) }
        fun volume(delta: Double) { state["Volume"]?.toDoubleOrNull()?.let { send("Volume", "%.1f".format((it + delta).coerceIn(-80.0, 12.0))) } }

        col.addView(ip)
        col.addView(btn("Connect") {
            val host = ip.text.toString().trim()
            prefs.edit().putString("ip", host).apply()
            client?.stop()
            client = NadClient(host, onState = { k, v -> runOnUiThread { state[k] = v; render() } },
                onConnected = { ok -> runOnUiThread { status.text = if (ok) "Connected" else "Disconnected, retrying…" } }).also { it.start() }
        })
        col.addView(status); col.addView(info)
        col.addView(btn("Power") { send("Power", if (state["Power"] == "On") "Off" else "On") })
        col.addView(btn("Volume +") { volume(1.0) })
        col.addView(btn("Volume −") { volume(-1.0) })
        col.addView(btn("Mute") { send("Mute", if (state["Mute"] == "On") "Off" else "On") })
        col.addView(btn("Bass EQ") { send("Bass", if (state["Bass"] == "On") "Off" else "On") })
        for (s in listOf("Stream", "Wireless", "TV", "Phono", "Coax1", "Coax2", "Opt1", "Opt2")) col.addView(btn(s) { send("Source", s) })
        setContentView(ScrollView(this).apply { addView(col) })
    }

    private fun render() {
        info.text = "Power ${state["Power"]}   Volume ${state["Volume"]} dB${if (state["Mute"] == "On") " (mute)" else ""}\nSource ${state["Source"]}   Bass EQ ${state["Bass"]}"
    }

    override fun onDestroy() { client?.stop(); super.onDestroy() }
}
