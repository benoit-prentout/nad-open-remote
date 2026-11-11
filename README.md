# Open Remote for NAD C 338 (unofficial)

Free, ad-free Android remote for the **NAD C 338** over its network control port (TCP 30001).
Tested only on the C 338. Other NAD amps that speak the same `Main.*` text protocol on that port may work (use "Add by address" to try a custom host or port); reports welcome.
Not affiliated with NAD Electronics or Lenbrook. "NAD" is their trademark.
License (planned): GPL-3.0-or-later. Status: local draft, nothing published.
Protocol notes: PROTOCOL.md.

## Build

`./gradlew :protocol:test :app:assembleDebug` (needs Android SDK 35, JDK 17). APK: app/build/outputs/apk/debug/.
