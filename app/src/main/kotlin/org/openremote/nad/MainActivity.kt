package org.openremote.nad

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color as AColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import java.util.Locale

// ponytail: all state lives in this one holder; split into a ViewModel only if screens multiply.
class Amp {
    val s = mutableStateMapOf<String, String>()
    var connected by mutableStateOf(false)
    var scanning by mutableStateOf(false)
    var message by mutableStateOf<Int?>(null)
    var found by mutableStateOf<Map<String, String>>(emptyMap())
    var maxVolume by mutableStateOf(12f)
    var host by mutableStateOf("")
    var theme by mutableStateOf(ThemeChoice.System)
    var lang by mutableStateOf("system")
    val on get() = s["Power"] == "On"
    val volume get() = s["Volume"]?.toFloatOrNull()
    fun flag(k: String) = s[k] == "On"
}

class Actions(
    val send: (String, String) -> Unit, val raw: (String) -> Unit, val find: () -> Unit, val connect: (String, Int) -> Unit,
    val media: (Int) -> Unit, val forget: () -> Unit, val setTheme: (ThemeChoice) -> Unit, val setLang: (String) -> Unit, val setMaxVolume: (Float) -> Unit,
)

class MainActivity : ComponentActivity() {
    private var client: NadClient? = null
    private val amp = Amp()
    private lateinit var prefs: android.content.SharedPreferences

    override fun attachBaseContext(base: Context) {
        val lang = base.getSharedPreferences("p", MODE_PRIVATE).getString("lang", "system") ?: "system"
        super.attachBaseContext(
            if (lang == "system") base
            else base.createConfigurationContext(Configuration(base.resources.configuration).apply { setLocale(Locale(lang)) })
        )
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        prefs = getSharedPreferences("p", MODE_PRIVATE)
        amp.maxVolume = prefs.getFloat("maxVol", 12f)
        amp.theme = runCatching { ThemeChoice.valueOf(prefs.getString("theme", "")!!) }.getOrDefault(ThemeChoice.System)
        amp.lang = prefs.getString("lang", "system") ?: "system"
        // debug builds only: `am start --es theme Dracula` previews a theme without saving it (used for screenshots)
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0)
            intent.getStringExtra("theme")?.let { t -> runCatching { amp.theme = ThemeChoice.valueOf(t) } }
        val savedHost = prefs.getString("ip", "") ?: ""
        val savedPort = prefs.getInt("port", 30001)

        if (savedHost.isNotBlank()) connect(savedHost, savedPort, rescanIfUnreachable = true) else find()

        val actions = Actions(
            send = { k, v -> client?.set(k, v) }, raw = { client?.sendRaw(it) }, find = ::find, connect = { h, p -> connect(h, p) },
            media = { code ->
                val am = getSystemService(android.media.AudioManager::class.java)
                am.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, code))
                am.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, code))
            },
            forget = {
                client?.stop(); amp.connected = false; amp.s.clear(); amp.host = ""; amp.found = emptyMap(); amp.message = null
                prefs.edit().remove("ip").remove("port").apply()
            },
            setTheme = { amp.theme = it; prefs.edit().putString("theme", it.name).apply() },
            setLang = { prefs.edit().putString("lang", it).apply(); recreate() },
            setMaxVolume = { amp.maxVolume = it; prefs.edit().putFloat("maxVol", it).apply() },
        )
        setContent {
            val dark = if (amp.theme == ThemeChoice.System) isSystemInDarkTheme() else amp.theme.isDark
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    if (dark) SystemBarStyle.dark(AColor.TRANSPARENT) else SystemBarStyle.light(AColor.TRANSPARENT, AColor.TRANSPARENT),
                    if (dark) SystemBarStyle.dark(AColor.TRANSPARENT) else SystemBarStyle.light(AColor.TRANSPARENT, AColor.TRANSPARENT),
                )
                onDispose {}
            }
            AmpTheme(amp.theme) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { Screen(amp, actions) }
            }
        }
    }

    private fun connect(h: String, port: Int, rescanIfUnreachable: Boolean = false) {
        amp.host = if (port == 30001) h else "$h:$port"; amp.found = emptyMap(); amp.message = null
        prefs.edit().putString("ip", h).putInt("port", port).apply()
        client?.stop()
        var ever = false
        client = NadClient(h, port, onState = { k, v -> runOnUiThread { amp.s[k] = v } }, onConnected = { ok ->
            runOnUiThread {
                if (ok) ever = true
                amp.connected = ok
                if (!ok && !ever && rescanIfUnreachable) { client?.stop(); find(R.string.unreachable) }
            }
        }).also { it.start() }
    }

    /** Scans the local network. One amp: connect at once. Several: let the user pick. */
    private fun find(intro: Int = R.string.searching) {
        amp.scanning = true; amp.message = intro; amp.found = emptyMap()
        Thread {
            val hits = scan(localSubnetHosts())
            runOnUiThread {
                amp.scanning = false
                when (hits.size) {
                    0 -> amp.message = R.string.none_found
                    1 -> connect(hits.keys.first(), 30001)
                    else -> { amp.message = null; amp.found = hits }
                }
            }
        }.start()
    }

    override fun onDestroy() { client?.stop(); super.onDestroy() }
}
