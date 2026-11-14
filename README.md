<p align="center"><img src="app/src/main/res/drawable-nodpi/app_icon.png" width="144" alt="Open PHEV SUV icon" /></p>

# Open PHEV

An independent, open-source Android Wi-Fi remote for Mitsubishi Outlander PHEV vehicles using the **MY18 protocol**. **GPL-3.0-or-later · Android 12+ · Local Wi-Fi only**. Not affiliated with Mitsubishi.

Download the APK from [Releases → Assets](https://github.com/WantedPickle/open-phev/releases). Install `open-phev-0.4.1-alpha.apk`; the source ZIP is for building or contributing, not installation. No cloud account, subscription or original Mitsubishi app is required.

## 0.4.1-alpha

- **First-run tutorial:** a nine-step offline guide to VPN/Wi-Fi settings, Phone MAC, ACC, pairing beeps, registration and a fresh car-OFF connection test. Reopen it in Settings → Tutorial. Existing users are not prompted to register again on upgrade.
- **Recovery help:** separate, acknowledged full-reset instructions for old/full registrations, with the effect on saved phones, timers and Wi-Fi credentials explained before proceeding. See [tutorial details and phone checks](TUTORIAL.md).

- A calmer vehicle dashboard with battery, connection and confirmed climate status together.
- **Up Next** shows planned climate and charging starts from the car's clock, with explicit climate mode.
- Tap a timer card to edit its slot. Enable/disable switches review the change before saving and wait for the vehicle's reported settings.
- **Saved to vehicle** feedback on the Timers page, separate from pending commands.
- Automatic light/dark appearance and a new SUV silhouette launcher icon.

Existing climate, lights and vehicle-stored schedules are retained. Times use a 24-hour clock and ten-minute steps; midnight is **00:00**, and unused slots say **Not set**. The app is not locked to one owner's SSID.

Vehicle evidence comes from a 2018 Outlander PHEV and Galaxy S23 running Android 16 / One UI 8.5. The owner has confirmed controls and timer saving, including charging schedule write/read-back in 0.3.0. Scheduled execution is a separate check. Compatibility with every trim, market and phone is not established.

See [release notes](RELEASE_NOTES.md), [phone test checklist](TESTING.md), [privacy](PRIVACY.md), [contributing](CONTRIBUTING.md), and [GitHub publishing guide](GITHUB_PUBLISHING.md). The source includes a host-test workflow; it does not publish APKs automatically.

## Update and connect

1. Install `open-phev-0.4.1-alpha.apk` over the existing app. It has the same application ID and signing certificate. Keep the existing registration and Samsung Wi-Fi MAC setting; no reset or re-registration is needed for this update.
2. Join the vehicle's Wi-Fi in Android settings and choose to stay connected without internet.
3. On Controls, tap **Connect to vehicle** and confirm that the vehicle network is selected. This uses the working connected-Wi-Fi path even when Android hides the SSID. Network-name verification is an optional connection method in Settings.
4. Use **Refresh status** to request updates; the car may send only changed values. **Reconnect & reload timers** obtains the startup settings again. Settings contains Tutorial, Copy diagnostics, registration help and the phone Wi-Fi access check.

The first registration of a new phone requires the vehicle's documented registration mode. The app never registers automatically. Normal use is within vehicle Wi-Fi range; it is not an internet remote.

## Indicators

- Green **Active / On · confirmed** comes from vehicle status, never from a button press or command acknowledgement alone.
- Amber distinguishes Sending, Received / awaiting status, delayed requests and unconfirmed or failed requests.
- Demist/defrost is a climate mode. It does not confirm electrical windscreen-element heating; no separate electrical glass-heater command is implemented.
- Climate needs both the running flag and a supported mode. A remembered mode after Stop does not mean climate is running.
- Headlight and parking indicators use their separate vehicle status fields.
- Status older than two minutes becomes unknown; disconnect clears it. Refresh status requests updates but does not guarantee every field is repeated; reconnect if an unchanged field remains unknown. There is no automatic status polling.
- A negative or malformed acknowledgement is an error, not acceptance.

## Upcoming times and appearance

Up Next uses the last vehicle date/time, at minute precision, advanced by elapsed time while connected. It never substitutes the phone's clock or timezone. After two minutes without a clock update, it asks for Refresh status. It excludes disabled, empty or unsupported slots and respects repeat start days. A forecast is a planned start, not proof of vehicle execution; it does not resolve overlaps or model future DST changes. Vehicle preconditions, charging location restrictions and full-charge extension still apply. No phone alarm is scheduled.

The palette follows Android's light/dark setting. Changing appearance may recreate the activity; reconnect normally if needed. Native dialogs, wheels and switches follow the same system theme. Status uses text as well as colour.

## Climate schedules

Five vehicle-stored slots, with hour and minute scroll wheels and explicit Cool cabin, Heat cabin or Demist / defrost windscreen selection. **One mode applies to all five slots**, and manual climate commands can also change the shared mode. The editor and final review make this clear.

The verified timer format represents minutes in **10-minute steps** (00, 10, 20, 30, 40, 50). It cannot represent 06:45. The wheel prevents invalid minute entry without rounding a user's time silently. Immediate climate start delays of 5/10 minutes are a different command.

The draft does not expire. 0.2.0 incorrectly required both timer settings and mode to be repeated after every Refresh. The owner's 12 November log proves that refresh returned clock/battery updates without those settings, so Save never reached the timer write. 0.2.1 removes that requirement.

Save compares the editor's snapshot with the latest vehicle notifications from the same connection. It writes once and requires a matching notification for each field that actually changes (schedule and/or mode). An unchanged field may retain its value received in this session. If both already match, it reports Already set and sends nothing. Acknowledgement alone cannot confirm a change. Disconnect clears the session cache; missing or conflicting settings block writing. No automatic write retries or speculative refresh commands are used. This relies on the vehicle reporting changes during the connection, not an atomic compare-and-set on the vehicle. Reconnect to reload settings independently and confirm persistence after saving.

0.2.0 corrected the final timer-write byte: it is the shared climate mode, not the unknown first byte of the schedule-read packet. See TIMER_PROTOCOL.md for interoperability evidence and limits. Four untouched timer slots and unknown bits in the edited slot are preserved. The car's clock is displayed but not changed. Verify a scheduled run before relying on it.

## Charging schedules (experimental)

Use Timers → tap a charging timer card. Five slots have start/end times in ten-minute steps and selected start days. An end earlier than the start is marked **+1 day**. Equal start/end times are rejected rather than interpreted as a full day. Midnight is 00:00.

The **Continue after end time until fully charged** checkbox is explicit: leave it off for a strict charging window. Existing values are loaded without silently changing them. Existing vehicle location restriction is preserved; this app does not configure saved locations. When creating schedules from an inactive shared setting, the write uses the original client's default of location restriction off, shown in the review.

Each save changes one slot and preserves the other four plus unknown bits. It checks current-session settings for conflicts, writes once, and requires matching notifications for each changed field. An ACK alone is insufficient. Reconnect and inspect the stored schedule, then verify an attended charging start/stop before relying on it. No charging command is sent merely by opening the page or moving a wheel. See CHARGING_PROTOCOL.md for format evidence and limits.

## Included and incomplete features

- Heating, cooling, windscreen mode; 10/20/30 minutes, now or after a 5/10-minute delay; Stop climate.
- Headlights and parking lights on/off; live battery, charging, door and lock status.
- Climate schedule editing with explicit shared mode and vehicle-reported save confirmation.
- **Experimental charging schedule editing:** five slots with start/end wheels, repeat days, enabled state and optional full-charge extension. Saving was reported successful on the test vehicle; scheduled start/stop still needs separate verification.
- Lock status is read-only; remote lock/unlock is not implemented.
- No automatic Wi-Fi joining, background phone alarms, MY14/MY24 codec or Play Store release.
- Diagnostics stay locally on the phone, bounded to approximately 96–120 KB. VIN, MAC, security material, SSID and passwords are omitted; Android backup is disabled.

Wi-Fi handling and listen-first MY18 session startup are retained from the working build. A session-encoding rejection occurred at 08:26:21 on 12 November, about 65 seconds after connection. The owner also reported five brief disconnects absent from that excerpt. Their cause and any link to charging remain unresolved. This version records last-command/handshake timing and last-reported charging states, and retains a longer log. It does not claim to fix those disconnects. Historical investigation: CONNECTION_NOTES.md. Current phone test steps: TESTING.md.

## Building from source (Windows)

Install JDK 17 and Android command-line tools. Use `sdkmanager` to install `platforms;android-36` and `build-tools;36.0.0`, accepting their SDK licences. No Gradle or third-party Android runtime libraries are required.

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-17'
$env:ANDROID_HOME = 'C:\path\to\android-sdk'
.\build.ps1
```

The script runs protocol tests, compiles Java, dexes classes, packages the manifest and image/theme resources, aligns and signs an APK in `build/`. Use `-BuildDir` to place intermediate files elsewhere.

Host tests only:

```powershell
.\build.ps1 -TestOnly
```

The integration test uses a loopback TCP socket; allow localhost connections in any test sandbox. It never connects to a vehicle or the internet.

Signing uses a locally generated alpha key in `build/local-alpha.keystore` with a development password. Preserve it privately to install subsequent builds as updates. Do not commit or publish private signing keys. Public releases need securely managed private signing keys (see GITHUB_PUBLISHING.md); another developer's self-built APK will normally have a different signer and may require uninstalling the old app.

## Verification performed

- Compilation with JDK 17 and Android API 36, D8 minimum API 31.
- 551 host-side checks: upstream captured packet vectors, every split point of a sample TCP packet, coalesced frames, invalid checksums, rolling-key initial/end/wrap values, climate encoding, timer preservation, local emulated handshake/registration/command acknowledgement, normal-session startup/reconnect, diagnostic redaction, pre-send/post-ping server-close handling, startup silence/cancellation, callback readiness/order/cancellation gating, vehicle-confirmed indicators, invalid acknowledgements, shared timer mode, interval validation and timer read-back checks.
- APK signature verification with Android `apksigner`.

These checks do not validate Android runtime UI/permission behaviour, Samsung-specific network behaviour, or physical vehicle effects. See `TESTING.md`.

## Project layout

- `Protocol.java`: frame codec and MY18 session key schedule.
- `VehicleClient.java`: TCP lifecycle, handshake, register notification acknowledgements, bounded command waits.
- `ClimateTimers.java` / `ChargeTimers.java`: schedule encoding, validation and human-readable summaries.
- `SchedulePreview.java`: read-only next-start estimates and flag-only timer switching.
- `MainActivity.java` and `PhevUi.java`: native Android interface, wheel time picker and network binding.
- `TimerSession.java` / `ChargeSession.java`: per-connection notifications and confirmation of changed settings.
- `LiveStatus.java`: fresh vehicle observations and command feedback, kept separate.
- `NetworkGate.java`: tested readiness gate for callbacks from the selected network.
- `tests/`: plain-Java host tests and minimal local fake vehicle.
- `build.ps1`: reproducible build steps, with no proprietary app dependency.

## Open-source reuse

You may use, modify and distribute this project under GPL-3.0-or-later. When distributing a compiled app, provide its corresponding source and licence under the GPL's terms. This source directory is ready for a public repository but has not been uploaded to a hosting service. Contributions should include evidence for new register formats and tests for packet handling.

Do not include Mitsubishi's original APK, decompiled source, branding or private credentials in releases. The supplied original APK was inspected locally for interoperability research; it is not needed to build this app and none of its code/assets are included.

## References

- [phev2mqtt](https://github.com/buxtronix/phev2mqtt) and its [MY18 protocol documentation](https://github.com/buxtronix/phev2mqtt/blob/master/protocol/README.md).
- [Mitsubishi registration instructions](https://www.mitsubishi-motors.com/en/products/outlander_phev/app/remote/jizen.html).
- [Mitsubishi UK MY19 operation guide](https://mitsubishi-motors.co.uk/wp-content/uploads/2026/02/Outlander-PHEV-Operation-Guide.pdf).

See `THIRD_PARTY.md` for attribution and `LICENSE` for the complete GPL text.




