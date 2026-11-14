# Charging schedule protocol — 0.3.0, 12 November 2026

This independently written implementation uses interface facts established by local interoperability inspection of the user-supplied original APK and the captured command below. No proprietary source or resources are included.

## Layout

Register 04 contains five four-byte slots (20 bytes, no prefix). Write register 19 uses the same 20 bytes plus one shared setting byte (21 bytes). Each slot contains:

- Byte 0 bit 0: full-charge extension. Bits 1–7: Sunday through Saturday repeat-day mask.
- Byte 1 bits 0–2: start minute / 10; bits 3–7: start hour.
- Byte 2 bits 0–2: end minute / 10; bits 3–7: end hour.
- Byte 3 bits 0–1: enabled 1, disabled 2; upper bits preserved.
- Empty slots use 31/7 hour/minute sentinel fields. Those are not clock times.

The shared setting is read from register 1b's low nibble. Original schedule-screen logic preserves values 2/3 (location restriction off/on), and when a usable schedule is created with an inactive value below 2 it uses remembered 2/3 or defaults to 2. Open PHEV preserves a reported 2/3; when no remembered active value is available, it explicitly reviews the default 2. It does not offer new location configuration. Unknown values are rejected.

Local original-app inspection: the charge slot accessor uses offsets [0,0,1,1,2,2,3], shifts [0,1,0,3,0,3,0] and widths [1,7,3,5,3,5,2]. The schedule packet builder places five four-byte slots followed by the shared setting at register 25 (0x19). The original help describes full-charge extension as continuing after the selected period when the battery is not yet full. No original implementation code or assets were reused.

Captured public reference, https://github.com/buxtronix/phev2mqtt/blob/master/protocol/README.md:

- Disabled slot 3: 7d38b00183bd00017c70380200ffff0300ffff0302
- Enabled slot 3: 7d38b00183bd00017c70380100ffff0300ffff0302

The 21-byte command fixture is reproduced exactly by the encoder test. This establishes byte compatibility for this capture, not vehicle validation for all settings.

## Confirmation and limitations

ChargeSession keeps notifications for register 04 and 1b within one TCP session, separately from the UI. Save rejects missing/malformed snapshots or concurrent reported changes. It writes once, then requires new matching notifications for fields changed by that write. Unchanged fields may use current-session observations. All slot bytes and the shared setting must match. ACK alone, timeout, malformed data, encoding rejection or disconnect cannot confirm saving. There is no automatic replay.

The editor offers explicit start/end times and repeat days, full-charge extension, enabled state and an overnight label. Other slots and unknown bits are preserved. It does not offer deletion, overlapping-schedule priority rules, full-day equal-time interpretation, saved-location management or vehicle-clock changes. Those behaviours are not newly inferred by this implementation.

Actual schedule writing, persistence, start-day behaviour across midnight and charging start/stop still need validation on the owner's vehicle. Test before relying on an unattended schedule. The app uses vehicle-stored schedules, not phone alarms.
