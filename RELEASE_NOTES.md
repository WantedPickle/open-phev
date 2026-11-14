# Open PHEV 0.4.1-alpha

## New first-run tutorial

New installations open an offline setup guide once. Settings → Tutorial reopens it at any time, with an option to restart. Existing users upgrading are not forced to register again.

The guide covers disabling VPN, manually joining vehicle Wi-Fi, selecting Phone MAC, entering accessory/pairing mode, understanding entry beeps, connecting/registering and checking a fresh connection with the vehicle OFF. Wi-Fi and VPN buttons open Android settings; Connect and Register use the existing confirmation flows.

A separate recovery section covers full/old registrations. It explains the reset's loss of saved phones/timers and restoration of Wi-Fi-card credentials. Two acknowledgements are required before reset instructions are displayed; the app never sends a reset command. A missing-network route returns to Wi-Fi setup after pairing help.

The 0.4.0 dashboard, upcoming schedules, timer cards/switches, light/dark appearance and SUV icon remain included.

## Update

Install `open-phev-0.4.1-alpha.apk` over the existing app. Application ID and signing certificate are unchanged. No vehicle reset or re-registration is needed to update. Existing users can review the new guide in Settings → Tutorial.

## Verification and limits

551 host checks passed, including tutorial routing, restoration guards and fresh-session verification, alongside the existing fake-vehicle and timer tests. Android API 36 compilation and APK signature verification pass. Native UI and settings-return behaviour still need a phone check; no local emulator/device was available. The guide's car-OFF checkbox relies on the user checking the real vehicle.

Earlier vehicle tests confirmed controls, ordinary reconnection and climate/charging schedule saving. Scheduled execution remains a separate check. Intermittent session-encoding rejection remains unresolved; no automatic write retries or protocol changes are introduced. Electrical windscreen-element control is not implemented or implied by climate demist status.

Source, GPL licence, icon resources, tests and build instructions accompany the APK. See TUTORIAL.md for source references and acceptance checks, and GITHUB_PUBLISHING.md for the release process.
