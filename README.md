# NetShift

**Stop losing your 4G data to invisible 5G drops.**

If you're on a plan with unlimited 5G but a strict daily cap on 4G (common with several Indian carriers), you've probably run into this: you're indoors, your phone quietly falls back from 5G to 4G without telling you, and by the time you notice, your entire day's 4G quota is gone. Android doesn't automatically try to reconnect to 5G on its own, so this can happen over and over throughout the day without you realizing it.

NetShift watches your phone's network connection in the background. The moment it detects a silent 5G → 4G fallback, it shows you a clear, always-visible status notification (green for 5G, red for 4G), and — if enabled — automatically blocks cellular data until 5G comes back, so you don't burn through your limited 4G data without knowing it.

This is a personal project built to solve a real, everyday annoyance — not a polished commercial app, but something that actually works and keeps improving.

---

## Features

- **Live network status** — persistent notification showing whether you're currently on 5G or 4G, updating in real time
- **Automatic data protection** — a lightweight local "kill switch" blocks cellular data the moment you fall back to 4G, and releases it instantly when 5G returns
- **No root required** — works on stock, unrooted Android using only public system APIs
- **Minimal battery impact** — no polling loops, no background scanning; everything is event-driven off Android's own network callbacks
- **Boot persistence** — the watcher restarts automatically after a phone reboot
- **OEM battery-optimization aware** — includes a one-tap flow to exempt the app from aggressive background killing (especially relevant on Vivo/iQOO, Xiaomi, Oppo devices)

## Installation

1. Go to the [Releases](../../releases) page of this repo
2. Download the latest `.apk` file
3. On your phone, enable **Install from unknown sources** for your file manager or browser
4. Open the downloaded APK and install
5. Launch the app, tap **Start watching**, and grant the requested permissions

## Requirements

- Android 12 (API 31) or higher
- A SIM with an active data connection (works with both NSA and SA 5G networks)

---

## How it works (technical)

### The core problem

Non-standalone (NSA) 5G — the most common type of 5G deployed by carriers today — doesn't operate independently. It rides on top of an existing 4G LTE connection, which acts as an "anchor." The 5G radio is essentially a speed boost layered over that anchor, not a replacement for it.

Indoors, the higher-frequency signals used by 5G are attenuated by walls and structures far more than 4G signals are. When that happens, the phone silently drops the 5G component and continues on the 4G anchor alone — with no user-facing warning. This is a physical/RF coverage limitation inherent to NSA 5G, not a bug in any particular phone or carrier.

### Detection

NetShift runs a foreground `Service` (`NetworkWatchService`) that registers a `TelephonyCallback.DisplayInfoListener`. Android calls this back whenever the *displayed* network type changes — which is a more accurate signal of what the user is actually experiencing than the raw `NetworkType`, since it reflects override types like `OVERRIDE_NETWORK_TYPE_NR_NSA`.

The service classifies each callback as 5G or non-5G:

```kotlin
val isFiveG = when (info.overrideNetworkType) {
    TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA,
    TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED -> true
    else -> info.networkType == TelephonyManager.NETWORK_TYPE_NR
}
```

This handles both NSA 5G (most carriers) and standalone (SA) 5G deployments (e.g. Jio's network in India), where the raw `networkType` itself reports `NETWORK_TYPE_NR`.

### Live status notification

Rather than firing a one-off alert, the service maintains a single persistent, ongoing notification that reflects current state at all times — color-coded green (5G) or red (4G fallback) — updated only when the state actually changes (`setOnlyAlertOnce`), so it never spams.

### Data protection (kill switch)

To actually prevent data loss rather than just warn about it, NetShift implements a lightweight local kill switch using Android's public `VpnService` API — the same public API "true" VPN apps use, but with no tunneling, no remote server, and no packet inspection.

When a 5G → 4G fallback is detected (and only if the active network transport is cellular, never WiFi), the service establishes a local VPN interface and simply holds it open without forwarding any packets. This creates a black hole for cellular traffic with near-zero CPU/battery cost, since there's no active processing loop. The moment 5G returns, the interface is torn down and normal data flow resumes instantly.

This requires a one-time system consent dialog (standard for any `VpnService`-based app), granted once and remembered by Android thereafter.

### Boot persistence

A `BroadcastReceiver` (`BootReceiver`) listens for `ACTION_BOOT_COMPLETED` and restarts `NetworkWatchService`, so protection resumes automatically after a reboot without needing to manually reopen the app.

### Battery optimization handling

Several Android OEMs (notably Vivo/iQOO, Xiaomi, Oppo) apply aggressive background-process killing on top of stock Android, which can silently stop the watcher service. NetShift requests exemption via the standard `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` intent, which covers stock Android's battery management on all devices. For OEMs with additional proprietary autostart/background lists, manual whitelisting in device settings is still recommended and documented in-app.

## Tech stack

- Kotlin
- Android `TelephonyCallback` / `TelephonyDisplayInfo` APIs (API 31+)
- `VpnService` for the local kill switch
- Foreground `Service` + `BroadcastReceiver` for persistence
- `SharedPreferences` for user settings

## Known limitations

- A few seconds of data can pass through during the detection window before the kill switch engages
- Rapid signal flickering (fast ping-pong between 5G/4G) can cause the kill switch to toggle quickly rather than staying steadily blocked
- Aggressive OEM background management can still occasionally kill the service despite battery-optimization exemption
- No root available means true forced reconnection to 5G isn't possible — this app focuses on protecting data usage, not forcing the radio back to 5G

## Contributing

This is a personal-use project shared publicly. Issues and pull requests are welcome if you run into bugs or have ideas for improvement.

## License

No license specified yet — all rights reserved by default. Contact the repo owner before reusing code commercially.
