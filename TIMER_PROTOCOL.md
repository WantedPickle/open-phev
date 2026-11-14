# Climate timer interoperability notes — 11 November 2026

No proprietary source or resources are included in this project. The following describes protocol behaviour established by local inspection of the supplied original APK and the open MY18 protocol reference.

## Read and write formats

Register 05: 16 bytes, an unknown header followed by five three-byte timer slots. Slot fields are little-endian: duration bits 0–1; days 2–8; minute index 9–11; hour 12–16; on/off 17–18. Preserve bits 19–23. Minutes index 0–5 means 00/10/20/30/40/50. This is also consistent with the original app's field extraction and validation (minute index less than six). Do not invent five-minute encodings.

Register 1a write: five three-byte slots followed by a shared climate mode. Modes are 1=cool, 2=heat, 3=windscreen. The mode is read back from register 1c's low nibble, separately from the schedule. The unknown header of read register 05 is not the mode byte. Older Open PHEV versions mistakenly copied that header into the write mode field.

Evidence in local original-app inspection: the schedule screen's packet builder uses register 26 (0x1a), copies all five slots, then puts its chosen mode in payload byte 15. Its screen loads the mode from the register-28 (0x1c) storage offset and masks to the low nibble. Selection values 1/2/3 select the original cool/heat/defrost icons respectively. These resource identifiers were inspected solely to establish the mapping; no original icons or code are distributed. All slots use this one mode. Live climate commands in the owner's log also change register 1c, so the mode shown on Timers can change after manual climate use.

Open reference: https://github.com/buxtronix/phev2mqtt/blob/master/protocol/README.md (timer bit fields; read header and final write byte are marked unknown there). The additional mode interpretation above is from local interoperability inspection, not a claim made by that reference.

## Save and confirmation — corrected in 0.2.1

The 12 November vehicle log disproves the assumption that register 06 payload 03 forces register 05 and 1c to be repeated. It returns ACK 00 and clock/battery updates, while both timer registers arrive at connection startup. 0.2.0 therefore blocked before any timer write. It must not keep requesting the same partial refresh.

TimerSession stores only actual register-05 and register-1c notifications from one connection; a write or ACK never populates the cache. Save checks the editor snapshot against that cache, then sends a single write. Changed fields require a newer matching notification; unchanged fields retain their current-session observation. Both resulting values must match. When nothing changes, no write is sent. Negative ACK, disconnect, missing update, malformed status or mismatched values cannot produce a successful save. No command is replayed automatically.

Notifications are handled on the client reader before UI dispatch, so ACK/notification arrival order and UI posting delays cannot cause a false timeout. Settings are cleared on disconnect. A new connection requires new settings. This uses the vehicle's change-notification stream; it cannot detect a vehicle-side change that the vehicle never reports. The reconnect-and-reload button makes that verification available explicitly.

Tests reproduce partial refresh, schedule-only/mode-only/combined changes, notifications before/after ACK, unchanged saves without writes, missing updates, conflicts, malformed values, session invalidation and encoding rejection without retry. Existing byte-field tests remain. Actual timer-write responses and scheduled execution still require the owner's vehicle test.
