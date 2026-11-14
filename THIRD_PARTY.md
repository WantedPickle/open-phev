# Third-party notices

The Java packet/key implementation and protocol tests adapt the algorithms, behaviour and test vectors in **phev2mqtt**, copyright 2021 Ben Buxton <bbuxton@gmail.com> and its contributors, licensed under GNU GPL version 3 or (at your option) any later version.

Source: https://github.com/buxtronix/phev2mqtt

Files consulted: `protocol/raw.go`, `protocol/message.go`, `protocol/raw_test.go`, `protocol/README.md`, `client/client.go`, `cmd/register.go`, `cmd/mqtt.go`. Retrieved 4 November 2026. The upstream protocol research also credits the phev-remote project and @zivillian for the rolling XOR algorithm.

This project is distributed under GPL-3.0-or-later; the full text is in `LICENSE`. Modifications include the Java implementation, bounded streaming decoder, Android network/UI integration, lifecycle handling, schedule preservation, and host tests.

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

Android SDK/JDK build tools are external prerequisites and are not redistributed with this source archive. No Mitsubishi executable, source, images or other assets are included.

The 0.1.2 session-start exchange and startup test vectors are based on `phev_core_startMessageEncoded`, `phev_pipe_start`, and `test_start_encoded_message` in https://github.com/phev-remote/phevcore (retrieved 4 November 2026). Copyright (c) 2020 Jamie Nuttall. MIT licensed; its complete notice is included in `LICENSE-phevcore.txt`. That pre-security startup exchange and its fixture were removed in 0.1.5 because they did not establish correct ordering for this MY18 vehicle. The MIT notice is retained for attribution to earlier work. The current receive-first security/keepalive behavior follows the phev2mqtt reference client, rechecked on 8 November 2026. No original proprietary APK code is copied.

0.3.0's charging timer tests also use the captured enabled/disabled register-19 fixture in the upstream protocol README. Additional field meanings were established by local interoperability inspection; no proprietary source or assets are distributed.

The 0.4.0 SUV launcher artwork was generated specifically for this project, not extracted from a manufacturer app. See ICON.md for provenance and the generation prompt.
