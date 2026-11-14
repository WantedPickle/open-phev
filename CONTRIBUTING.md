# Contributing

Open PHEV welcomes reproducible bug reports, Android UI improvements and documented interoperability work. Start with the README and TESTING.md. It currently targets MY18 vehicle Wi-Fi and Android 12 or later; other generations need evidence, not assumptions.

Use JDK 17 and run `./build.ps1 -TestOnly` in PowerShell. Tests use localhost sockets and never contact a vehicle. For Android changes, run the full SDK build and report the phone/emulator used. The included GitHub workflow runs host tests; it does not sign or publish releases.

Keep observations separate from requests: an acknowledgement means acceptance, not physical activation. Schedule changes must preserve unrelated slots and unknown bits, check the same-session snapshot and wait for matching vehicle notifications. Never replay vehicle writes automatically after disconnects.

For new commands, supply public protocol evidence and meaningful tests for success, errors and malformed data. Do not upload Mitsubishi binaries, decompiled source, credentials, identifiable logs, or signing keys. All source contributions are under GPL-3.0-or-later; retain upstream attribution.

Use the bug-report template for problems. Record app and Android versions, vehicle year/market, reproduction steps and redacted diagnostics. A scheduled run, a successful save and a reconnect that reads the entry back are different test results.
