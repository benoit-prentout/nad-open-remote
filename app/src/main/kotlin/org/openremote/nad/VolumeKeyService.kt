package org.openremote.nad

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.VolumeProvider
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import java.util.Locale

/**
 * Optional: makes the phone's volume buttons control the amp when the app is closed or the screen is off.
 * A MediaSession with remote volume is the standard Android mechanism for this; it needs a foreground
 * service (hence the notification) and keeps one connection to the amp. Off by default, switch in Settings.
 */
class VolumeKeyService : Service() {
    private var client: NadClient? = null
    private var session: MediaSession? = null
    private val prefs by lazy { getSharedPreferences("p", MODE_PRIVATE) }
    @Volatile private var vol = Float.NaN
    @Volatile private var silent = false
    private var track: AudioTrack? = null

    private val provider = object : VolumeProvider(VOLUME_CONTROL_ABSOLUTE, 92, 0) {
        override fun onSetVolumeTo(v: Int) = send(v - 80f)
        override fun onAdjustVolume(direction: Int) {
            if (direction == 0) return
            if (vol.isNaN()) ConnLog.add(this@VolumeKeyService, "[background] volume key ignored: amp volume not known yet") else send(vol + direction)
        }
    }

    private fun send(db: Float) {
        val v = db.coerceIn(-80f, prefs.getFloat("maxVol", 12f))
        vol = v
        ConnLog.add(this, "[background] volume key -> %.1f dB".format(Locale.US, v))
        client?.set("Volume", "%.1f".format(Locale.US, v))
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, flags: Int, startId: Int): Int {
        if (i?.action == ACTION_STOP || !prefs.getBoolean("bgKeys", false)) {
            prefs.edit().putBoolean("bgKeys", false).apply()
            stopSelf(); return START_NOT_STICKY
        }
        startInForeground()
        val host = prefs.getString("ip", "") ?: ""
        if (host.isBlank()) { stopSelf(); return START_NOT_STICKY }
        client?.stop()
        client = NadClient(host, prefs.getInt("port", 30001),
            onState = { k, v -> if (k == "Volume") v.toFloatOrNull()?.let { vol = it; provider.currentVolume = (it + 80f).toInt().coerceIn(0, 92) } },
            onEvent = { ConnLog.add(this, "[background] $it") }).also { it.start() }
        if (session == null) session = MediaSession(this, "NadRemoteVolume").apply {
            setCallback(object : MediaSession.Callback() {}) // required: without a callback Android drops the volume events
            setPlaybackToRemote(provider)
            setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 0f).build())
            isActive = true
        }
        startSilence()
        return START_STICKY
    }

    /**
     * Android only routes the volume keys to a media session whose app is really playing audio (and, with the
     * screen off, only while music is active). So we play an inaudible stream of zeros. No audio focus is taken,
     * other apps keep playing normally.
     */
    private fun startSilence() {
        if (silent) return
        val rate = 8000
        val buf = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val t = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(buf).setTransferMode(AudioTrack.MODE_STREAM).build()
        track = t; silent = true
        t.play()
        Thread({ val zeros = ByteArray(buf); while (silent) { if (t.write(zeros, 0, zeros.size) < 0) break } }, "nad-silence").also { it.isDaemon = true }.start()
    }

    private fun startInForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.vol_keys), NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, VolumeKeyService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_power).setContentTitle(getString(R.string.bg_notif_title)).setContentText(getString(R.string.bg_notif_text))
            .setContentIntent(open).setOngoing(true)
            .addAction(Notification.Action.Builder(null, getString(R.string.bg_notif_stop), stop).build()).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK) else startForeground(ID, n)
    }

    override fun onDestroy() {
        silent = false; runCatching { track?.stop(); track?.release() }; track = null
        client?.stop(); session?.release(); session = null
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "volume_keys"
        private const val ID = 1
        const val ACTION_STOP = "org.openremote.nad.STOP"
        fun start(ctx: Context) { ctx.startForegroundService(Intent(ctx, VolumeKeyService::class.java)) }
        fun stop(ctx: Context) { ctx.stopService(Intent(ctx, VolumeKeyService::class.java)) }
    }
}
