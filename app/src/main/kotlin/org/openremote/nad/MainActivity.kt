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
    var volKeys by mutableStateOf(true)
    var bgKeys by mutableStateOf(false)
    var host by mutableStateOf("")
    var theme by mutableStateOf(ThemeChoice.System)
    var lang by mutableStateOf("system")
    val on get() = s["Power"] == "On"
    val volume get() = s["Volume"]?.toFloatOrNull()
    fun flag(k: String) = s[k] == "On"
}

class Actions(
    val send: (String, String) -> Unit, val raw: (String) -> Unit, val find: () -> Unit, val connect: (String, Int) -> Unit,
    val forget: () -> Unit, val setTheme: (ThemeChoice) -> Unit, val setLang: (String) -> Unit, val setMaxVolume: (Float) -> Unit, val setVolKeys: (Boolean) -> Unit, val setBgKeys: (Boolean) -> Unit,
)

class MainActivity : ComponentActivity() {
    private var client: NadClient? = null
    private val amp = Amp()
    private lateinit var prefs: android.content.SharedPreferences
    private val ui = android.os.Handler(android.os.Looper.getMainLooper())
    private val markDown = Runnable { amp.connected = false }
    private var keyVol = Float.NaN
    private var keyAt = 0L

    private val notifPerm = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) {}

    private fun setBg(on: Boolean) {
        amp.bgKeys = on; prefs.edit().putBoolean("bgKeys", on).apply()
        if (!on) { VolumeKeyService.stop(this); return }
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            notifPerm.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        if (!prefs.getString("ip", "").isNullOrBlank()) VolumeKeyService.start(this)
    }

    // Nothing found (amp still booting, in standby, Wi-Fi slow): keep looking every 15 s while this screen is open.
    private val rescan = Runnable { if (!amp.connected && amp.s.isEmpty() && !amp.scanning) find(R.string.searching) }
    private fun scheduleRescan() { ui.removeCallbacks(rescan); ui.postDelayed(rescan, 15000) }

    override fun onStart() { super.onStart(); client?.reconnectNow() }
    override fun onStop() { ui.removeCallbacks(rescan); super.onStop() }

    // Phone volume buttons drive the amp while the app is open. ponytail: foreground only; background needs a MediaSession service.
    private fun volumeKey(code: Int): Boolean {
        val up = code == android.view.KeyEvent.KEYCODE_VOLUME_UP
        if (!amp.volKeys || !amp.connected || (!up && code != android.view.KeyEvent.KEYCODE_VOLUME_DOWN)) return false
        val now = System.currentTimeMillis()
        val base = if (now - keyAt < 700 && !keyVol.isNaN()) keyVol else amp.volume ?: return false
        keyVol = (base + if (up) 1 else -1).coerceIn(-80f, amp.maxVolume); keyAt = now
        client?.set("Volume", "%.1f".format(Locale.US, keyVol))
        return true
    }
    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent): Boolean = volumeKey(keyCode) || super.onKeyDown(keyCode, event)
    override fun onKeyUp(keyCode: Int, event: android.view.KeyEvent): Boolean =
        (amp.volKeys && amp.connected && (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP || keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN)) || super.onKeyUp(keyCode, event)

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
        ConnLog.load(this)
        amp.maxVolume = prefs.getFloat("maxVol", 12f)
        amp.volKeys = prefs.getBoolean("volKeys", true)
        amp.bgKeys = prefs.getBoolean("bgKeys", false)
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
            forget = {
                client?.stop(); VolumeKeyService.stop(this); amp.connected = false; amp.s.clear(); amp.host = ""; amp.found = emptyMap(); amp.message = null
                prefs.edit().remove("ip").remove("port").apply()
            },
            setTheme = { amp.theme = it; prefs.edit().putString("theme", it.name).apply() },
            setLang = { prefs.edit().putString("lang", it).apply(); recreate() },
            setMaxVolume = { amp.maxVolume = it; prefs.edit().putFloat("maxVol", it).apply() },
            setVolKeys = { amp.volKeys = it; prefs.edit().putBoolean("volKeys", it).apply(); if (!it) setBg(false) },
            setBgKeys = ::setBg,
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
        if (amp.bgKeys) VolumeKeyService.start(this)
        client?.stop()
        var ever = false
        client = NadClient(h, port, onState = { k, v -> runOnUiThread { amp.s[k] = v } }, onEvent = { ConnLog.add(this, it) }, onConnected = { ok ->
            runOnUiThread {
                if (ok) ever = true
                ui.removeCallbacks(markDown)
                if (ok) amp.connected = true else ui.postDelayed(markDown, 2500) // brief drops stay invisible
                if (!ok && !ever && rescanIfUnreachable && !amp.scanning) { ui.removeCallbacks(markDown); amp.connected = false; find(R.string.unreachable) } // the saved client keeps retrying meanwhile
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
                    0 -> { amp.message = R.string.none_found; scheduleRescan() }
                    1 -> connect(hits.keys.first(), 30001)
                    else -> { amp.message = null; amp.found = hits }
                }
            }
        }.start()
    }

    override fun onDestroy() { client?.stop(); super.onDestroy() }
}
