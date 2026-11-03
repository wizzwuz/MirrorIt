# MirrorIt 📱⚡📱

> **Ultra-low latency Android screen mirroring and remote control app**, powered by **Shizuku API v23**, hardware-accelerated **H.264/AVC encoding**, and zero-config **Wi-Fi / Mobile Hotspot discovery**.

---

## ✨ Features

- **🚀 Ultra-Low Latency Screen Mirroring:**
  Hardware-accelerated screen capture via Android `MediaProjection` and low-latency `MediaCodec` AVC/H.264 streaming directly to client over TCP.
- **🛡️ Advanced Control with Shizuku API v23:**
  Full remote control without requiring root! Utilizes Shizuku's elevated Binder IPC (`uid=2000`) and a persistent 0-fork interactive shell process to execute remote taps, swipes, navigation keys, and keyboard text typing with sub-10ms response times.
- **🛜 Flexible Connectivity (Wi-Fi & Hotspot):**
  - **Same Wi-Fi Network:** Automatic zero-config device discovery via UDP broadcast beacons on port `45555`.
  - **Host as Hotspot:** Host device turns on Mobile Hotspot; Controller connects to Wi-Fi and connects directly (preset gateway `192.168.43.1`).
  - **Controller as Hotspot:** Controller turns on Mobile Hotspot; Host connects to it and is automatically detected.
  - **Zero Router / Offline:** Works anywhere in the world without an internet connection or external Wi-Fi router.
- **🎮 Ergonomic Floating Control Overlay:**
  - Modern collapsible HUD pill with live FPS and Latency metrics.
  - Android system navigation buttons: **Back**, **Home**, **Recents**, **Power**, **Volume Up**, **Volume Down**.
  - **Remote Keyboard:** Pop up a text modal to send and type strings on the remote device seamlessly.
- **♿ Accessibility Service Fallback:**
  If Shizuku is not running or not installed on the host device, MirrorIt gracefully falls back to its built-in Accessibility Service to inject taps, swipes, and system navigation.
- **🎨 Modern Material 3 UI:**
  Built with Jetpack Compose featuring a sleek dark theme, glowing cyberpunk accents (Cyan, Violet, Emerald), and intuitive card controls.

---

## 🏗️ Architecture

```
┌─────────────────────────────────┐           ┌─────────────────────────────────┐
│     Host Device (Screen Cast)   │           │   Client Device (Controller)    │
│                                 │           │                                 │
│  ┌───────────────────────────┐  │           │  ┌───────────────────────────┐  │
│  │   MediaProjection API     │  │           │  │   SurfaceView Display     │  │
│  └─────────────┬─────────────┘  │           │  └─────────────▲─────────────┘  │
│                ▼                │           │                │                │
│  ┌───────────────────────────┐  │   Video   │  ┌─────────────┴─────────────┐  │
│  │ MediaCodec (H.264 Encode) ├─┼───────────┼─►│ MediaCodec (H.264 Decode) │  │
│  └─────────────┬─────────────┘  │   (TCP)   │  └───────────────────────────┘  │
│                ▼                │           │                ▲                │
│  ┌───────────────────────────┐  │           │  ┌─────────────┴─────────────┐  │
│  │     HostServer (45556)    │  │  Control  │  │   Multi-touch Gestures    │  │
│  │                           │◄─┼───────────┼──┤   & Navigation Bar        │  │
│  └─────────────┬─────────────┘  │   (TCP)   │  └───────────────────────────┘  │
│                ▼                │           │                                 │
│  ┌───────────────────────────┐  │           │  ┌───────────────────────────┐  │
│  │   Shizuku API v23 Shell   │  │  Beacon   │  │ Discovery Listener        │  │
│  │ (input tap/swipe/key/text)│  │◄─────────┼──┤ (Auto-detects hosts 45555)│  │
│  └───────────────────────────┘  │   (UDP)   │  └───────────────────────────┘  │
└─────────────────────────────────┘           └─────────────────────────────────┘
```

---

## 📱 How to Use

### Mode 1: Host (Device Being Mirrored & Controlled)
1. Open **MirrorIt** and go to the **"Share Screen"** tab.
2. If Shizuku is running, tap **"Grant Permission"** to enable elevated input control. (If Shizuku is not installed, enable the MirrorIt Accessibility Service in Android Settings).
3. Select your desired stream quality (720p HD, 1080p FHD, or 480p SD).
4. Tap **"Start Screen Sharing"** and grant the Android screen capture prompt.
5. Your device will now broadcast its availability on the local network.

### Mode 2: Client (Controller Device)
1. Open **MirrorIt** and go to the **"Control Device"** tab.
2. The host device will appear in **"Discovered Devices"**. Tap **"Control"**.
3. Or manually enter the Host IP address and tap **"Connect & Control"**.
4. Use the full-screen touch gestures to navigate the remote device, or use the floating ergonomic toolbar at the bottom for Back, Home, Recents, Volume, Power, and Keyboard input!

---

## 🛜 Hotspot Direct Connection Guide

No Wi-Fi router? No mobile data? You can connect two Android phones directly:

| Setup | Host Action | Controller Action |
|---|---|---|
| **Host is Hotspot** | Turn on Mobile Hotspot, then start screen sharing in MirrorIt. | Connect to Host's Wi-Fi. In MirrorIt, tap `Host Hotspot (192.168.43.1)` and connect. |
| **Controller is Hotspot** | Connect to Controller's Wi-Fi. Start screen sharing in MirrorIt. | Turn on Mobile Hotspot. Open MirrorIt; the Host will appear in Discovered Devices automatically. |

---

## 🛠️ Tech Stack & Dependencies

- **Language:** Kotlin 2.0+
- **UI Framework:** Jetpack Compose with Material 3 & Navigation Compose
- **Permissions & Elevated IPC:**
  - `dev.rikka.shizuku:api:13.1.5` (API v23)
  - `dev.rikka.shizuku:provider:13.1.5`
- **Video Pipeline:** Android `MediaProjection`, `VirtualDisplay`, `MediaCodec` (H.264/AVC Baseline & Low Latency Mode)
- **Networking:** Java NIO / TCP Sockets for sub-frame latency, UDP Broadcast for auto-discovery
- **Build System:** Gradle 8.10+ (AGP 8.7+)

---

## 📄 License

This project is licensed under the Apache 2.0 License - see the [LICENSE](LICENSE) file for details.
