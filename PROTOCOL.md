# NAD C 338 network control protocol (observed)

Clean-room notes: observed on the author's own C 338 (firmware 1.76), read-only.
No code or resources from any third-party app were used.

- Transport: TCP port 30001, plain text, lines ending `\r\n`.
- **Do not start a command with a newline** (the amp then stays silent).
- `Main?` returns the full state, one `Main.Key=Value` per line:

| Key | Example | Notes |
|---|---|---|
| Power | On/Off | |
| Volume | -56.0 | dB, range about -80..+12 |
| Mute | On/Off | |
| Source | Wireless | Stream, Wireless, TV, Phono, Coax1/2, Opt1/2 |
| Bass | On/Off | fixed +6 dB |
| Brightness | 2 | display |
| AutoSense, AutoStandby, ControlStandby | On/Off | ControlStandby keeps network control alive in standby |
| AnalogGain | 0 | effect unknown |
| Model, Version | NADC338, 1.76 | read-only |

## Verified on the amp (no-op writes only)

- Query: `Main.Key?` -> `Main.Key=Value`.
- Set: `Main.Key=Value` -> echoes `Main.Key=Value`; related changes may follow (e.g. `Main.Volume=` lines).
- Unknown key: no reply at all (use a read timeout, never wait for an error).
- Updates are pushed on the open connection (also from the front panel / remote), per earlier two-connection test.

TODO: toggle syntax (`Main.Power+`), volume step commands, exact Source names, behavior on power-off with ControlStandby.
