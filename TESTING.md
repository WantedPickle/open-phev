# 0.4.1 tutorial verification

See [TUTORIAL.md](TUTORIAL.md) for fresh-install, upgrade, settings-return, recovery and large-font/light/dark device checks. 551 host checks and the Android build pass; native guide testing on Samsung is still needed. The existing codec, registration command, climate and schedule writes are unchanged.

# 0.4.0 phone checks

Host tests (522 checks), Android compilation and APK signature verification pass. Local native UI/device testing was not available. After installing over the current alpha:

1. Check the SUV launcher icon under Samsung's icon mask. Open the app in light and dark mode; check dialog text, wheel numbers, switches and larger system font sizes. A system theme change may recreate the activity and require reconnecting.
2. Connect normally (no pairing). Compare battery, climate summary and individual indicators. Requests awaiting confirmation must stay amber and must not become a green active state from an ACK alone.
3. On Timers, tap slots 2–5 and verify the editor loads the tapped slot rather than Timer 1. Unset slots should say Not set and have a disabled quick switch; tap to configure them.
4. Toggle an existing valid timer, cancel and check no write occurs. Repeat and save; confirm the switch changes only after reported settings arrive. Check Saved to vehicle appears on the Timers page. Reconnect and verify persistence and all untouched slots.
5. Check Up Next against the vehicle clock and repeat days, including weekends and an overnight charging window. Disabled timers must disappear from the forecast. After clock status ages past two minutes, the forecast should request a refresh. Disconnect must remove live forecasts.
6. Check shared climate mode, charging location restrictions and full-charge extension are preserved by quick toggles. Physically observe one scheduled start/stop; saving alone does not prove execution.

The UI update does not change the working packet codec. Capture diagnostics for any short disconnect, particularly while charging. Do not reset registration just to install this update.

---

## Earlier verification notes

# Validation — 0.3.0-alpha

461 host checks, Android compilation and APK signature verification pass. Host tests cover protocol frames and fake-server sessions, network callback ordering, fresh/expired/disconnected status, ACK-before/after-status ordering, timer mode encoding and read-back mismatches. They do not render the Android interface or prove physical vehicle effects.

## Confirmed on 0.1.5

The owner's 11 November log and report confirm normal connection, heat, windscreen mode, cool, Stop climate, headlights and parking lights. Climate notifications change from running=02 with modes 02 / 03 / 01 to running=00 after Stop. Lamp commands receive acknowledgements and status notifications. Scheduled climate execution is still awaiting a vehicle check. A 06:50 timer was accepted by the previous editor; 06:45 was rejected. Neither proves a scheduled run occurred.

## Check the update on the S23

- Install over 0.1.5; keep registration and the same Samsung MAC setting.
- Check all three tabs, scrolling, system-bar clearance, larger font sizes and accessibility labels.
- Connect normally with the car OFF. Confirm status and command controls work.
- Try Heat, Demist and Cool one at a time. Confirm amber becomes green only after the vehicle reports activation. Stop must clear active indicators even though the mode remains remembered.
- Check headlight and parking indicators against the actual lamps.
- Disconnect: indicators must become neutral. After two minutes without a register update its live indicator becomes unknown. Refresh may return only changed data; reconnect reloads startup settings.

## Timer test

- Reconnect & reload timers and compare the displayed vehicle clock with local time.
- Edit one slot; select the shared mode explicitly. The review must name the mode and warn that all five slots use it.
- Scroll the hour/minute wheels. Minutes offer only 00/10/20/30/40/50. Spend longer than a minute editing: Save should compare current-session settings without requiring a repeated snapshot.
- Save and wait for “Saved · vehicle reported changes”. Confirm the other four slots' times/days remain unchanged. A missing/mismatched mode or schedule must not be reported as saved.
- Disconnect/reconnect and refresh. Confirm schedule and shared mode persist. Avoid changing manual climate mode before the scheduled test, since it can change the shared mode.
- Verify an attended scheduled run with the vehicle OFF and phone disconnected, then disable the test slot.
- If the schedule or mode changes elsewhere while editing, Save must block the stale draft without overwriting those changes.

The owner subsequently reports 0.2.1 working and supplied a screenshot of the saved climate timer/mode. 0.3.0 adds charging editing, which still needs physical validation; vehicle-clock changes remain unimplemented. Historical connection troubleshooting is in CONNECTION_NOTES.md; do not repeat old reset/pairing investigations for this update.

## 12 November regression and connection evidence

0.2.0 repeatedly timed out before a timer write: Refresh returned clock and battery fields but no timer schedule/mode. Local socket tests now reproduce this and validate the corrected save path. The supplied log also contains BB at 08:26:21 after roughly 65 seconds; the following session lasted until manual disconnect at 08:27:45. The owner reports five earlier short disconnects not present in this log. No charge/unplug explanation is established. 0.2.1 adds context logging and retains approximately 96–120 KB. Do not claim the disconnect issue is fixed. If it recurs, copy diagnostics immediately; no further registration/reset is indicated by this evidence.


## 0.3.0 display and charging checks

- Empty climate/charging slots show Not set, not 31:70 or On. Midnight displays 00:00. Opening a page must not change any vehicle settings.
- The charging editor loads all existing choices; verify the selected slot, times, days, full-charge checkbox and location restriction in Review.
- Set one attended charging window using the vehicle clock, keeping full-charge extension off for the first test. Wait for vehicle-confirmed saving, then reconnect and inspect all five slots.
- Verify actual charging starts and stops at the selected times. Verify an overnight interval separately before relying on it; +1 day labels the expected end day. The app does not configure a charger or vehicle location.
- Confirm missing/negative acknowledgements or missing changed-register notifications do not show Saved and do not replay the write.
- Continue to collect immediate diagnostics if the separate intermittent disconnect recurs; it is not claimed fixed by this feature update.

Host tests add an exact captured charging write, preserved slots/unknown bits, full-charge flags, interval validation, locator-setting preservation/default, unset/midnight displays, conflicts, malformed status and socket simulations with partial refresh and either notification order. These tests do not constitute physical charging verification.
