# QCYbutMOD

<p align="center">
  <img src="assets/logo.svg" width="120" height="120" alt="QCYbutMOD Logo" />
</p>

<p align="center">
  <strong>QCY Kulaklıkları İçin Yeni Nesil, Gizlilik Odaklı & Telemetri Düzeyinde Kontrol Süiti</strong><br>
  <em>Next-Generation, Privacy-First & Telemetry-Grade Control Suite for QCY Earbuds</em><br>
  <sub>Tersine Mühendislikle Sıfırdan Geliştirilmiş • Reklamsız • Tamamen Çevrimdışı • AMOLED Uyumlu</sub>
</p>

<p align="center">
  <a href="https://github.com/MrSpy00/QCYbutMOD/releases"><img src="https://img.shields.io/badge/Release-v1.2.0-00F0FF?style=for-the-badge&logo=android&logoColor=black" alt="Release v1.2.0"></a>
  <a href="https://github.com/MrSpy00/QCYbutMOD/blob/main/LICENSE"><img src="https://img.shields.io/badge/License-MIT-00E676?style=for-the-badge" alt="License MIT"></a>
  <a href="https://developer.android.com/about/versions/16"><img src="https://img.shields.io/badge/Android-8.0_to_16+(API_36)-FF6D00?style=for-the-badge&logo=android" alt="Android API 26-36"></a>
  <a href="https://aegissoft.com.tr"><img src="https://img.shields.io/badge/Geli%C5%9Ftirici-aegisSoft-7C4DFF?style=for-the-badge" alt="aegisSoft"></a>
  <a href="https://github.com/MrSpy00"><img src="https://img.shields.io/badge/Author-mrSpy-181717?style=for-the-badge&logo=github" alt="mrSpy GitHub"></a>
</p>

<p align="center">
  <a href="#-türkçe"><strong>🇹🇷 Türkçe Dokümantasyon</strong></a> • 
  <a href="#-english"><strong>🇬🇧 English Documentation</strong></a>
</p>

---

# 🇹🇷 Türkçe

## ⚡ Genel Bakış

**QCYbutMOD**, resmi üretici uygulamasının getirdiği gereksiz telemetri yükünü, zorunlu hesap girişlerini, arka plan veri toplamasını ve hantal arayüzü ortadan kaldırmak amacıyla **[aegisSoft](https://aegissoft.com.tr)** ve **[mrSpy](https://github.com/MrSpy00)** tarafından sıfırdan **Jetpack Compose (Material 3)** ve adli tersine mühendislik yöntemleriyle geliştirilmiş modern bir Android kontrol süitidir.

Uygulama; **Wuqi SoC** (WQ7033 / WQ7034 çipset mimarisi; QCY MeloBuds Pro, N20, H4 ve `products.json` tabanındaki 199+ model) donanım protokolünü doğrudan Bluetooth Düşük Enerji (BLE GATT) seviyesinde konuşur. Stok uygulamada sunulmayan donanımsal susturma, gerçek ham pil verisi, yapay zekâ destekli canlı çeviri ve tam dokunmatik kontrol özgürlüğü sağlar.

---

## 🚀 Öne Çıkan Özellikler

### 🎧 1. Donanımsal Ses ve Adli BLE Hattı
* **Donanımsal Firmware Ses Susturma (0x1D):** Kulaklığın dahili İngilizce sesli istemlerini donanım DSP seviyesinde (`Tone Volume = 0`) susturarak çift ses konuşma çakışmasını tamamen yok eder.
* **aegis TR Sesli Asistanı:** Android TTS altyapısıyla çalışan, kadın/erkek ses seçimi ve perde ayarlaması sunan, tek dokunuşla Google Türkçe ses paketini kurabilen yerel sesli bildirim motoru.
* **Gerçek Ham Pil Telemetrisi (Ground Truth):** Yuvarlama yapmayan, doğrudan 3 baytlık GATT `0008` karakteristiğini okuyarak Sol (%8), Sağ (%41) ve Kutu seviyelerini birbirinden bağımsız anlık takip eden mimari.
* **ANC Yankı Koruması (AncEchoGuard):** Kulaklıktan dönen eski durum yankılarının ANC modunu (Gürültü Engelleme / Şeffaf / Kapalı) yanlışlıkla geri almasını engelleyen 2.5 saniyelik akıllı filtre.

### 🎛️ 2. Dinamik 10-Bant Parametrik Ekolayzer
* **Canlı Bezier Eğrisi:** DSP bantlarına doğrudan bağlanan pürüzsüz kübik Bezier tepki eğrisi görselleştiricisi.
* **Doğrudan Donanıma Yazma:** Frekans kazançlarını gecikmesiz olarak kulaklığın ses işlemcisine aktarma ve yerel önayar saklama.
* **Modele Göre Yetenek Uyarlaması:** Bağlı kulaklığın desteklediği bant sayısına göre arayüzü dinamik olarak şekillendirme.

### 👆 3. Tam Dokunmatik Jest Eşleme
* **Eksiksiz Olay Yönetimi:** Tek dokunma, çift dokunma, üç dokunma ve 2 saniye uzun basma fonksiyonlarını her iki kulaklık için bağımsız atayabilme.
* **Donanımsal Okuma Doğrulaması:** Yazılan tuş eşlemelerini donanım yanıt tablolarından (`0x2B` / `000D`) geri okuyarak doğrulama.

### 🌐 4. Cihaz İçi Yapay Zekâ Canlı Çeviri
* **Çift Motorlu Mimari:**
  * **Çevrimdışı Motor:** Google ML Kit ile 59 dilde internet gerektirmeyen, tamamen cihaz içi anlık çeviri.
  * **Bulut Motor:** Google Gemini Live AI ile yüksek doğruluklu derin bağlam çevirisi (isteğe bağlı API anahtarı ile).
* **Karşılıklı Konuşma Modu (180° Yüz Yüze):** Masada karşıdaki kişinin rahatça okuyabilmesi için tek dokunuşla 180 derece ters dönen çeviri kartları.
* **Ses Yönlendirme:** Telefon mikrofonu ile kulaklık mikrofonu (Bluetooth SCO) arasında anında geçiş.
* **Kalıcı Çeviri Günlüğü:** Oturumları yerel depolamada saklayan, büyük/küçük harf duyarsız tam metin araması sunan çeviri geçmişi.

### 📡 5. Multipoint & Akıllı Bağlantı
* **Türetilmiş LE Adresi (Auto-Connect):** Eşleşmiş klasik Bluetooth cihazından otomatik LE adresi türeterek uygulama açılışında milisaniyeler içinde GATT bağlantısı kurma.
* **Çift Cihaz (Multipoint) Tanısı:** Aktif ses akışı yapılan cihazı ve ikincil bağlantı durumunu dürüst protokol analiziyle izleme.
* **Kulaklık Konumu Bulucu (Sesli Alarm):** Kulaklıkları kaybetmeye karşı özel frekans darbeleri (Sürekli, İkili Darbe, Üçlü Darbe) ile sesli akustik konumlandırma.

### 📱 6. Modern Sistem Mimarisi
* **AMOLED Siyah & Material You:** Gerçek OLED siyahı arayüz ve duvar kâğıdına göre dinamik renk vurguları.
* **Hızlı Ayar Kutucuğu (Quick Settings Tile):** Bildirim panelinden tek tıkla ANC / Şeffaf mod geçişi.
* **Glance AppWidget:** Jetpack Glance tabanlı, ana ekranda anlık pil durumunu gösteren modern widget.

---

## 📦 Kaynak Koddan Derleme

### Gereksinimler
* JDK 17 (Azul Zulu veya Eclipse Temurin önerilir)
* Android SDK 36 (Build Tools 36.0.0+)
* Gradle Wrapper (repo içinde hazır)

### Derleme Adımları
```powershell
# Depoyu klonlayın
git clone https://github.com/MrSpy00/QCYbutMOD.git
cd QCYbutMOD

# Test paketini çalıştırın (95+ birim testi)
.\gradlew.bat testDebugUnitTest

# Debug APK derleyin
.\gradlew.bat assembleDebug

# Release APK derleyin
.\gradlew.bat assembleRelease
```

Üretilen paketler:
* Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
* Release APK: `app/build/outputs/apk/release/app-release.apk`

---

## 🔒 Gizlilik ve Güvenlik Beyanı

* **Sıfır Telemetri:** Hiçbir analitik servisi, reklam kimliği veya kullanıcı takip kodu içermez.
* **Tamamen Yerel:** Bluetooth haberleşmesi ve ML Kit çevirileri yalnızca telefonunuzda çalışır, hiçbir veri dış sunuculara aktarılmaz.
* **Android 16 Güvenliği:** Bluetooth izinleri (`BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN`) izin verilmemiş açılışlarda dahi çökmeyecek şekilde koruyucu try/catch bloklarıyla zırhlanmıştır.

---

# 🇬🇧 English

## ⚡ Overview

**QCYbutMOD** is an advanced, bloat-free Android application built from scratch with **Jetpack Compose (Material 3)**. It replaces proprietary, telemetry-heavy companion software with an open, highly responsive, and forensic-grade Bluetooth Low Energy (BLE) engine specifically engineered for **Wuqi SoC** architectures (WQ7033 / WQ7034, QCY MeloBuds Pro, N20, H4, and over 199+ models from `products.json`).

Developed by **[aegisSoft](https://aegissoft.com.tr)** and **[mrSpy](https://github.com/MrSpy00)**, QCYbutMOD respects user privacy, operates completely offline without tracking, and unlocks hardware-level customization unavailable in stock vendor software.

---

## 🚀 Key Features

### 🎧 1. Forensic BLE & Audio Hardware Control
* **Hardware Prompt Muting (0x1D):** Suppresses native firmware English voice prompts at the DSP level (`Tone Volume = 0`), eliminating disruptive double-announcements.
* **aegis TR Voice Assistant:** Seamless voice feedback powered by Android TTS with automatic female/male voice resolution, pitch tuning, and one-tap Google TTS voice asset downloader.
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
.\gradlew.bat testDebugUnitTest

# Assemble Debug APK
.\gradlew.bat assembleDebug

# Assemble Release APK (requires keystore.properties or default debug signing)
.\gradlew.bat assembleRelease
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

## 📜 Lisans & Yasal Uyarı / License & Legal

Bu proje **MIT Lisansı** altında lisanslanmıştır — detaylar için [LICENSE](LICENSE) dosyasına göz atabilirsiniz.

### Yasal Uyarı / Disclaimer
*QCYbutMOD, aegisSoft tarafından bağımsız olarak geliştirilmiş açık kaynaklı temiz oda (clean-room) tersine mühendislik projesidir. Dongguan Hele Electronics Co., Ltd. veya QCY markası ile hiçbir kurumsal bağı, ortaklığı veya onayı yoktur.*  
*Tüm ticari markalar, ürün isimleri ve tescilli logolar ilgili hak sahiplerine aittir.*

---

<p align="center">
  Geliştirici / Developed by <strong><a href="https://aegissoft.com.tr">aegisSoft</a></strong> & <strong><a href="https://github.com/MrSpy00">mrSpy</a></strong>
</p>
