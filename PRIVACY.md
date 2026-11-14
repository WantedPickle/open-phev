# Privacy

Open PHEV operates locally over the vehicle's Wi-Fi. It has no analytics, advertising, cloud service or account. It opens a TCP connection to the vehicle at 192.168.8.46:8080. The app does not store the Wi-Fi password.

Android preferences store whether the tutorial has been shown, its current page/return route, the optional vehicle network name and a bounded diagnostic log (approximately 96–120 KB). Vehicle status and schedule snapshots stay in memory and are cleared when the connection ends. Android backup is disabled. There are no background phone alarms or uploads.

Diagnostics omit VIN, network names, MAC addresses, security-handshake payloads and passwords. They include timestamps, Android/app versions, network capabilities, command payloads (including schedules) and selected vehicle status values. Review a copied log before sharing it publicly.

Permissions: INTERNET permits the local TCP connection; network/Wi-Fi state and change-network permissions select the car network. Nearby Wi-Fi and location permissions support optional network-name checking on Android. The app does not request GPS location updates. Its primary Connect flow uses the already joined vehicle network.

Uninstalling the app removes its local preferences and diagnostics. Vehicle registration and timers are stored in the car and are not deleted by uninstalling the app.
