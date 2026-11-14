# Latest report and feature update — 12 November 2026

The owner reports 0.2.1 working and shows a stored 06:40 weekday climate timer with shared demist mode. 0.3.0 corrects unset-slot rendering (31:70 becomes Not set; midnight is 00:00) and adds experimental charging schedule editing. Charging operation needs its own first vehicle test. Neither report nor this update establishes that the earlier intermittent encoding rejection is permanently resolved. Connection/security handling is unchanged.

---

# Update — 12 November 2026

0.2.0's timer preflight blocked all observed saves. Register 06 / 03 refresh received ACK 00 plus clock/battery notifications, but no repeat of timer register 05 or mode 1c. Those registers were present on reconnect. No timer write (1a) appears in the supplied failed-save log. 0.2.1 uses current-session notifications, compares the editor snapshot, and confirms only fields changed by a single timer write; see TIMER_PROTOCOL.md.

An unsolicited encoding rejection (BB) is present at 08:26:21, 64.7 seconds into the 08:25:17 session, after a request-status write. This does not prove that write caused the rejection. The next session starts at 08:26:30 and ends by manual disconnect at 08:27:45. Five earlier few-second disconnects reported by the owner are absent from this excerpt. Unplugging and locking/unlocking reportedly helped, but causality is unestablished. No transport/encoding algorithm change or automatic replay is introduced. New diagnostics record handshake/command ages and last-reported charging states without security material. Log retention increases to roughly 96–120 KB.

All earlier next-step instructions below are historical. Keep the successful registration; no reset is required for this update.

---

# Current status — 11 November 2026

The owner confirms normal connection and all tested immediate climate/lamp controls work on 0.1.5. The latest log shows heat, windscreen and cooling status, then climate off after Stop. Scheduled execution is not yet confirmed. 0.2.0 redesigns the UI, adds vehicle-confirmed indicators, scroll wheels and a corrected shared timer mode. See README.md, TESTING.md and TIMER_PROTOCOL.md.

No further reset or re-registration is required for this update. The investigation below is a historical record: its older “next test” instructions and statements that climate is untested are superseded by this update.

---

# Connection investigation — updated 10 November 2026

## Confirmed milestone on 10 November

After the owner-approved reset and fresh registration, the owner confirms headlights on/off worked outside pairing mode. The 07:33 session supports this: security initialization completed, live status arrived, register 1a was 0000000000 (OFF), headlight commands were acknowledged, and no registration command was sent during that session. It ended by user disconnect at 07:33:50 without a logged encoding error. This is the first confirmed ordinary car-OFF connection and control result.

Registration metadata byte 19 changed from 00 before registration to 01 afterward and remained 01 on the ordinary connection. Register 15 byte 0 remained 03 even during the successful ordinary session, so it must NOT be used alone to reject registration or label this connection unauthorized. The earlier original-app interpretation was layout-dependent and insufficient.

A BB frame still occurred immediately after the first registration at 07:31:18; its cause is not resolved. Later sessions succeeded. Do not describe all encoding issues as fixed or claim long-term stability from this short test.

Only headlights outside pairing mode have been physically verified in this test. Heating, cooling, windscreen mode, climate stop and timers remain untested. The owner plans further testing tomorrow. No further reset, registration, new build or immediate test is needed now.

## Pairing-mode evidence supplied 9 November

The owner heard one beep followed by two faster beeps, three sounds total. Mitsubishi's published count maps three beeps to two registered devices, the maximum. This independently supports the count interpretation of metadata byte 19=02 for this observation; it does not identify either saved device or prove the S23 was saved.

The supplied 0.1.5 log establishes:

- At 16:56:31, MY18 security initialization arrived after 1006 ms; the status request was acknowledged with 00.
- Registers 06 and 15 repeatedly arrived with 20-byte payloads. Register 06 metadata was [0]=00, [18]=01, [19]=02; register 15 was [0]=03, [18]=01, [19]=02. Register 2a was 01. No registration write was made in this test.
- At 16:56:39, a decoded BB frame arrived (ack=1, register=1f, payload length=1). The app then closed the session after 9029 ms, with 65 received and 65 transmitted frames.

The [reference protocol](https://github.com/buxtronix/phev2mqtt/blob/master/protocol/README.md) associates BB with an unexpected XOR encoding value. Its payload was omitted by diagnostics, so this log cannot determine the encoding discrepancy or justify a specific recovery/replay. This session ended because Open PHEV handled BB by closing, unlike the earlier car-initiated close before any application bytes. No speculative command retries have been added.

Local original-app inspection maps status byte 0 to an eligibility/error check in one register layout: value 03 causes the app to reject the registration path. The layout selection and complete meaning must not be inferred solely from this log. Full registration slots are now a leading explanation for failure to save a new phone, but not a proven explanation of every failure. The encoding error is a separate unresolved finding.

Next: establish whether an older registered phone with a working original app is available. Its registration may be individually removed if the owner chooses. No reset, deregistration or new registration is currently authorized or performed. A full reset would affect saved devices and timer schedules and is not the next automatic action. No additional identical connection test is needed yet.

## Observed on the owner's S23 and 2018 vehicle

- 0.1.4's phone access check passed: Android supplied the selected Wi-Fi, reported access unblocked, and created a bound socket.
- The ordinary connection reached TCP port 8080. After the immediate plaintext startup messages, the session closed after 46 ms with zero bytes received. That timing alone does not prove the messages caused the close.
- Pairing-mode sessions and headlights worked in earlier releases. Ordinary reconnection has not yet worked, including in 0.1.1 before the plaintext startup messages were added.
- The owner confirmed the entered phone MAC matched Samsung's displayed value, Phone MAC was selected, and the VPN was uninstalled. A typo in that field is not the current explanation.
- At 14:58:59 on 8 November, 0.1.5 reached TCP successfully and the peer closed after 41 ms: received bytes=0, transmitted bytes=0, keepalives=0. The failure therefore precedes any application message; changing command encoding cannot explain this attempt. Saved registration or vehicle connection admission is the next hypothesis, not a confirmed diagnosis.

## Next evidence needed

Use the existing 0.1.5 build. Enter ordinary registration mode with hazard lamps OFF and count the entry beeps. Connect using **Use connected Wi-Fi**, wait about ten seconds if connected, copy diagnostics, disconnect and turn the car OFF. Do not press **Register this phone** or reset registrations. The beep count provides an independent device count; the pairing-mode log can expose registration metadata that is unavailable in the rejected ordinary session. Also establish whether an older registered phone is still available. Neither the beep count alone nor an acknowledgement identifies which phone is saved.

## Why 0.1.5 changes startup

The [phev2mqtt MY18 protocol reference](https://github.com/buxtronix/phev2mqtt/blob/master/protocol/README.md) describes vehicle-led security initialization. Its [client implementation](https://github.com/buxtronix/phev2mqtt/blob/master/client/client.go) starts receiving, sends keepalives, and acknowledges the vehicle's security packet; it does not require a plaintext F2 identity/F6-AA preamble before security.

Local interoperability inspection of the supplied original APK also found an outbound gate that suppresses ordinary packets before security is initialized while allowing a ping. Merely finding a routine that constructs F2 followed by F6-AA did not establish when those bytes actually reach the socket. No decompiled code or assets are distributed here.

The old phevcore byte fixture established packet construction, not correct startup timing for this car. The 0.1.2–0.1.4 fake server requiring that preamble was therefore an inadequate test of the real protocol assumption. Both the preamble and that requirement are removed in 0.1.5. The manual MAC field is no longer used or displayed.

The initial two-second listen period is a diagnostic addition. It lets a prompt remote close be observed before client application traffic. It is not claimed as a required Mitsubishi timing value. A received security packet is acknowledged immediately; otherwise periodic pings start after the window. No registration or control commands are sent automatically before security.

## Interpreting the next log

- Zero transmitted and zero received application bytes: the session ended before any Open PHEV protocol packet was exchanged. This narrows the investigation to connection admission/registration or vehicle behavior; it does not prove a particular cause.
- Transmitted bytes but no received bytes: the car stayed connected long enough for a keepalive but did not send a decoded handshake. The timing and counters distinguish this from the previous case.
- MY18 handshake and live status: ordinary connection has progressed. Only then test attended climate operation with the car OFF.

Do not infer filled registration slots from the old raw metadata label. Mitsubishi's [registration instructions](https://www.mitsubishi-motors.com/en/products/outlander_phev/app/remote/jizen.html) provide an independent count: one/two/three entry beeps mean zero/one/two registered devices respectively. If needed later, that count and fresh pairing-mode metadata can help investigate registration. Entering ordinary registration mode is separate from resetting registrations. A reset can erase saved device registrations and timer schedules, so it is not part of the current test.

## Scope of verification

221 host checks pass for this release. They verify packet fixtures, stream handling, callback gating, startup ordering, cancellation, diagnostics, and local fake-server interactions. Android callback execution on this release, car acceptance, actual heating/cooling, and timer execution remain unverified. Charging-timer editing and remote locking/unlocking are not implemented.



