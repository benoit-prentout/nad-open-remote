# Open Remote for NAD

A free, ad-free, open-source Android remote for the **NAD C 338** integrated amplifier, built on the amp's network control port (TCP 30001).

> **Unofficial.** Not affiliated with, endorsed by or sponsored by NAD Electronics or Lenbrook. "NAD" is their trademark, used here only to say which amplifier the app is for.

Tested only on a **NAD C 338** (firmware 1.76). Other NAD amps that speak the same `Main.*` text protocol on that port may work: use *Add by address* to try a host and port. Reports welcome.

## Features

- Power, volume, mute, source (Streaming, Bluetooth, TV, Phono, Coax 1/2, Optical 1/2), Bass EQ, auto-sense, auto standby, display brightness.
- Rotary volume knob with live control and a haptic tick per dB; finer steps at high volume. Volume limit.
- Front-panel style display, animated power button.
- Finds the amp on your network by itself (subnet scan, no mDNS needed), lists several amps if there are several, or connect by address / URL.
- Stays connected: keep-alive pings, fast reconnect, connection log (with Wi-Fi strength) to diagnose drops.
- Phone volume buttons control the amp (app open), optionally also in the background and with the screen off (opt-in; uses a small foreground service and plays an inaudible silent sound so Android routes the buttons).
- Play / pause / previous / next for the music app playing on the phone (the amp has no playback commands).
- Themes: device (Material You), light, dark, AMOLED, NAD-inspired, Dracula. English and French.
- No ads, no analytics, no accounts. The only network traffic is to your amp.

## Install

Grab the signed APK from the Releases page, or build it yourself:

```bash
./gradlew :protocol:test :app:assembleDebug   # needs JDK 17 and the Android SDK (platform 35)
```

The APK ends up in `app/build/outputs/apk/debug/`.

## How it works

The amp answers a simple text protocol on TCP port 30001, see [PROTOCOL.md](PROTOCOL.md). The `protocol` module is plain Kotlin (no Android dependency) and has tests against a fake amp; the `app` module is Jetpack Compose with Material 3.

This is a clean-room implementation: the protocol was observed on a real C 338 and from the amp's own responses. No code or resources from any other app were used.

## Limits

- The amp has no network playback commands, no Bluetooth visibility control, and ignores writes to `ControlStandby` and `AnalogGain` (shown read-only).
- Background volume buttons cost a little battery; turn them off in Settings if you do not need them.
- Android 8.0 (API 26) or newer.

## License

GPL-3.0-or-later, see [LICENSE](LICENSE).
