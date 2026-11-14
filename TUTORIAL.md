# First-run setup guide

Version 0.4.1 adds a nine-page offline guide with Back, Next and Close controls. It opens automatically on a fresh install, once only. Closing counts as having seen it; Settings → Tutorial resumes it, and Restart tutorial returns to the beginning. Existing users upgrading from earlier versions are recognised by their saved app-use record and are not forced through registration again.

The normal route covers preparation, disabling VPN, manually joining vehicle Wi-Fi, selecting Phone MAC, entering ACC/pairing, interpreting entry beeps, connecting/registering, a fresh car-OFF connection test and daily use. A missing-network branch visits pairing help and returns to the Wi-Fi step. The guide preserves its page when visiting Android settings and across activity recreation.

Wi-Fi and VPN buttons open Android settings. The app cannot set the MAC preference, disable a VPN or check the user's physical power-button actions. Connect and Register call the existing, explicit confirmation flows. Reading or advancing the guide sends no vehicle command. The final car-OFF test requires a user checkbox and a connected session different from the one present on entry to that page; a pairing session alone cannot pass it. This is still user-confirmed power state, not an independent measurement by the tutorial.

## Recovery

Connection help distinguishes phone network access problems, possible registration issues and unavailable Wi-Fi. Full-reset instructions are separate from ordinary setup. Showing them requires acknowledgement of having the Wi-Fi card and accepting loss of all saved phones/timers. The app does not execute a reset. Consent is not restored after activity recreation; reopening at that point returns to the warning.

The post-reset route returns to network setup, including rechecking Phone MAC if the network was forgotten. Open PHEV does not have or require the original app's Cancel VIN Registration operation. The guide does not treat the number of entry beeps or a registration ACK as proof that this phone is saved.

## Sources and scope

- [Mitsubishi Outlander PHEV registration and reset guidance](https://www.mitsubishi-motors.com/en/products/outlander_phev/app/remote/jizen.html): pairing/reset sequence, beep interpretation, time windows and reset consequences. Checked 13 November 2026.
- [2018 Outlander PHEV owner's manual](https://mitsubishi-motors.co.uk/wp-content/uploads/2026/02/18MY-Outlander-PHEV-Owners-Manual.pdf), printed pages 7-12 and 7-13 (PDF pages 226–227): accessory-power indication and power-mode sequence with brake released.
- [Samsung MAC address documentation](https://docs.samsungknox.com/admin/knox-configure/kbas/kba-669-how-to-set-mac-address-as-phone-mac/): Phone MAC versus Randomized MAC. Menu labels vary by Android/One UI version; the tutorial describes Samsung's network details and notes alternate labels.
- The Open PHEV connection/register buttons, VPN troubleshooting and car-OFF verification reflect this project's implementation and observed phone/vehicle tests. They are not claims that the original Mitsubishi app has the same interface.

## Phone acceptance checklist

1. Fresh install on a spare test profile/device: guide opens once; close and reopen app, confirm it stays closed. Do not clear the owner's working app data just to test this.
2. Upgrade an existing installation: no automatic guide; Settings → Tutorial opens it.
3. Open Android Wi-Fi/VPN settings and return; guide stays at its current page. Rotate/change appearance and check restoration. Text must remain readable in light/dark mode and at larger font sizes; body scrolls while navigation remains accessible.
4. Missing-network route returns to Wi-Fi after the pairing/beep pages. Restart clears that branch.
5. Connect/Register require taps and their existing confirmations. Next is disabled during a connection/registration request. Acknowledgement text does not claim successful car-OFF registration.
6. Car-OFF completion stays disabled for an unchanged pairing connection, a disconnected session or an unchecked power-OFF box. A new ordinary connection plus the user's confirmation enables it.
7. Reset warning cannot advance with only one checkbox. Back or activity recreation re-requires acknowledgement. Merely visiting the recovery screens must send no reset/registration packet.
8. Finishing returns to Controls. Settings → Tutorial can replay the guide later.

551 host checks pass, including first-run eligibility, recovery routing, restore handling, reset-view guards and the fresh-session condition. Android compilation/signature verification pass. Native guide layout and Samsung settings return paths still require a device check; no local emulator/device was available.
