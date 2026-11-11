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

TODO (verify on the amp before implementing): set syntax (`Main.Power=On`), toggles (`Main.Power+`), push notifications on state change, multi-connection behavior.
