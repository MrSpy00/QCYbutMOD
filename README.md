# QCYbutMOD

<p align="center">
  <img src="app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml" width="108" height="108" alt="QCYbutMOD Logo" />
</p>

<p align="center">
  <strong>Next-Generation, Privacy-First & Telemetry-Grade Control Suite for QCY Earbuds</strong><br>
  <em>Clean-room reverse engineered, bloat-free, AMOLED-ready Android companion application.</em>
</p>

<p align="center">
  <a href="https://github.com/MrSpy00/QCYbutMOD/releases"><img src="https://img.shields.io/badge/Release-v1.2.0-00F0FF?style=for-the-badge&logo=android&logoColor=black" alt="Release v1.2.0"></a>
  <a href="https://github.com/MrSpy00/QCYbutMOD/blob/main/LICENSE"><img src="https://img.shields.io/badge/License-MIT-00E676?style=for-the-badge" alt="License MIT"></a>
  <a href="https://developer.android.com/about/versions/16"><img src="https://img.shields.io/badge/Android-8.0_to_16+(API_36)-FF6D00?style=for-the-badge&logo=android" alt="Android API 26-36"></a>
  <a href="https://aegissoft.com.tr"><img src="https://img.shields.io/badge/Organization-aegisSoft-7C4DFF?style=for-the-badge" alt="aegisSoft"></a>
</p>

---

## ⚡ Overview

**QCYbutMOD** is an advanced, bloat-free Android application built from scratch with **Jetpack Compose (Material 3)**. It replaces proprietary, telemetry-heavy companion software with an open, highly responsive, and forensic-grade Bluetooth Low Energy (BLE) engine specifically engineered for **Wuqi SoC** architectures (WQ7033 / WQ7034, QCY MeloBuds Pro, N20, H4, and over 199+ models).

Developed by **[aegisSoft](https://aegissoft.com.tr)** and **[mrSpy](https://github.com/MrSpy00)**, QCYbutMOD respects user privacy, operates completely offline without tracking, and unlocks hardware-level customization unavailable in stock vendor software.

---

## 🚀 Key Features

### 🎧 1. Forensic BLE & Audio Hardware Control
* **Hardware Prompt Muting (0x1D):** Suppresses native firmware English voice prompts at the DSP level (`Tone Volume = 0`), eliminating disruptive double-announcements.
* **aegis TR Voice Assistant:** Seamless Turkish voice feedback powered by Android TTS with automatic female/male voice resolution, pitch tuning, and one-tap Google TTS voice asset downloader.
* **Ground-Truth Battery Telemetry:** Reads unrounded, authoritative 3-byte GATT `0008` telemetry directly from the earbuds (Left, Right, and Case independent channels).
* **ANC Echo Guard:** Intelligent 2.5s debounce filter that prevents stale firmware state echoes from flipping Active Noise Cancellation modes unexpectedly.

### 🎛️ 2. Dynamic 10-Band Parametric Equalizer
* **Real-time Bezier Curves:** Interactive cubic Bezier response curve visualizer mapped directly to DSP bands.
* **Precise Frequency Response:** Full frequency sculpting with instant hardware byte dispatch and persistent custom presets.
* **Capability Aware:** Automatically adapts band configuration based on connected device capabilities.

### 👆 3. Full Touch Gesture Customization
* **Complete Hardware Event Remapping:** Single-tap, double-tap, triple-tap, and 2-second long-press mapping.
* **Readback Verification:** Validates written key mappings against firmware response tables (`0x2B` / `000D`) with graceful fallbacks.

### 🌐 4. On-Device AI Live Translation
* **Dual-Engine Architecture:**
  * **Offline Engine:** Fast, on-device translation across 59 languages powered by Google ML Kit (zero internet required).
  * **Cloud Engine:** Optional high-accuracy translation powered by Google Gemini Live AI.
* **Rotational Face-to-Face UX:** 180° flippable transcript cards designed for across-the-table conversational translation.
* **Microphone Routing:** Seamless switching between phone microphone and Bluetooth headset SCO (Hands-Free profile).
* **Persistent Journal:** Searchable on-device translation session history with zero cloud synchronization.

### 📡 5. Multipoint & Connection Intelligence
* **Auto-Connect Decision Pipeline:** Derived LE address discovery and rate-limited reconnection watchdog ensuring instant link pickup on app launch.
* **Multipoint Route Diagnostics:** Real-time visibility into active audio streaming devices and secondary link status.
* **Find My Earbuds (Audio Locator):** Multi-pattern acoustic beacon triggers (Continuous, Double Pulse, Triple Pulse) to find misplaced earbuds.

### 📱 6. Modern Android Architecture
* **Edge-to-Edge & AMOLED Ready:** Deep true black backgrounds designed for OLED efficiency with dynamic Material You accent palettes.
* **Quick Settings Tile:** Toggle ANC and Transparency modes directly from the Android System Notification Shade.
* **Glance AppWidgets:** Real-time home screen battery widgets built on Jetpack Glance.

---

## 🛠️ Architecture & Tech Stack

```
com.aegissoft.qcybutmod/
├── core/
│   ├── audio/          # AegisVoiceAssistant, ParametricEq, NoiseMonitor, FitTestManager
│   ├── bluetooth/      # BleConnectionManager, QcyProtocol, BatteryTracker, AncEchoGuard
│   ├── models/         # ProductDatabase, DeviceCapabilities, QcyDeviceModel
│   ├── net/            # Dynamic panel repository & versioned cache
│   ├── service/        # QcyBackgroundService, QcyTileService
│   ├── translate/      # MlKitEngine, GeminiLiveEngine, TranslateJournalStore
│   └── utils/          # AegisLogger, CrashReporter
└── ui/
    ├── components/     # AegisCard, BatteryRing, InteractiveEqGraph, DeviceSelectionSheet
    ├── screens/        # HomeScreen, EqualizerScreen, GestureScreen, LiveTranslateScreen, etc.
    └── theme/          # Color.kt, Theme.kt (AMOLED Dark / Material You)
```

* **Kotlin:** 2.1+ / Coroutines & Flow
* **UI Toolkit:** Jetpack Compose (Material 3)
* **SDK Compatibility:** Min SDK 26 (Android 8.0) | Target SDK 36 (Android 16)
* **Testing:** 95+ Unit Tests across 18 Test Suites (JVM JUnit)

---

## 📦 Building from Source

### Prerequisites
* JDK 17 (Azul Zulu or Eclipse Temurin recommended)
* Android SDK 36 (Build Tools 36.0.0+)
* Gradle Wrapper (included in repository)

### 1. Clone the repository
```bash
git clone https://github.com/MrSpy00/QCYbutMOD.git
cd QCYbutMOD
```

### 2. Configure Release Signing (Optional)
For signing release builds, copy the template and configure your keystore credentials:
```bash
cp keystore.properties.example keystore.properties
```

### 3. Run Unit Tests & Build
```powershell
# Run the complete test suite
.\gradlew testDebugUnitTest

# Assemble Debug APK
.\gradlew assembleDebug

# Assemble Release APK (requires keystore.properties or default debug signing)
.\gradlew assembleRelease
```

Generated APKs will be located at:
* Debug: `app/build/outputs/apk/debug/app-debug.apk`
* Release: `app/build/outputs/apk/release/app-release.apk`

---

## 🔒 Privacy & Security

* **Zero Cloud Telemetry:** No user analytics, no crash uploaders, no advertising identifiers, and no vendor trackers.
* **Local-First Execution:** All BLE communication, speech-to-text, and ML Kit translation operate strictly offline on the device.
* **Strict Android 16 Permissions:** Bluetooth permissions (`BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN`) are granularly checked with complete safety wrappers against runtime SecurityExceptions.

---

## 📜 License & Intellectual Property

This project is licensed under the **MIT License** — see the [LICENSE](LICENSE) file for details.

### Disclaimer
*QCYbutMOD is an independent, open-source clean-room project developed by aegisSoft and is not affiliated, endorsed, or associated with Dongguan Hele Electronics Co., Ltd. or QCY.*  
*All trademarks, product names, and registered logos belong to their respective owners.*

---

<p align="center">
  Developed with precision by <strong><a href="https://aegissoft.com.tr">aegisSoft</a></strong> & <strong><a href="https://github.com/MrSpy00">mrSpy</a></strong>
</p>
