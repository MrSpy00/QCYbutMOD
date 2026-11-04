# PROJECT.md — QCYbutMOD

## 1. Proje Kimliği & Standartlar
- **Proje Adı:** QCYbutMOD
- **Paket / Namespace:** `com.aegissoft.qcybutmod`
- **Kurumsal Kimlik:** aegisSoft (`aegissoft.com.tr`)
- **Baş Geliştirici:** mrSpy (`https://github.com/MrSpy00`)
- **Hedef Donanım:** QCY MeloBuds Pro (Wuqi WQ7033 / WQ7034 SoC, VendorId: 19786, ModelId: 21020)
- **Test Cihazı:** Honor Magic (Android 16, API 36) ADB bağlı
- **Mimari:** Kotlin + Jetpack Compose (Material 3), Coroutines, Flow, StateFlow, Glance AppWidget, Forensic BLE Engine

## 2. Mimari Haritası
- `app/src/main/java/com/aegissoft/qcybutmod/core/`
  - `bluetooth/`: BLE GATT bağlantı havuzu, Wuqi SoC paket protokolü (`QcyProtocol`), reklam ayrıştırıcı (`BleAdvertisementParser`), pil doğruluk hattı (`BatteryTracker`).
  - `audio/`: Aegis TTS sesli asistanı (`AegisVoiceAssistant`), seslendirme ayarları (`VoiceSettings`, `VoiceMessages`), ekolayzer parametreleri (`ParametricEq`), kulaklık konumu bulucu (`EarbudAudioLocator`), akustik uyum testi (`FitTestManager`).
  - `translate/`: AI Canlı Çeviri motoru (`TranslationEngine`, `MlKitEngine`, `GeminiLiveEngine`, `LanguageCatalog`, `SpeechInput`, `TranslateSession`).
  - `models/`: Cihaz veritabanı (`ProductDatabase`), durum modelleri (`QcyDeviceModel`, `QcyBatteryInfo`, `GestureFunctions`).
  - `service/`: Kalıcı arka plan telemetri servisi (`QcyBackgroundService`), hızlı ayar kutucuğu (`QcyTileService`).
  - `utils/`: Adli seviye döngüsel bellek ve dosya loglayıcı (`AegisLogger`).
- `app/src/main/java/com/aegissoft/qcybutmod/ui/`
  - `screens/`: `HomeScreen`, `SettingsScreen`, `GestureScreen`, `EqualizerScreen`, `FitTestScreen`, `MultipointScreen`, `FindDeviceScreen`, `LiveTranslateScreen`.
  - `components/`: `AegisCard`, `BatteryRing`, `InteractiveEqCurve`, `DeviceSelectionSheet`.
  - `theme/`: AMOLED uyumlu dinamik renk paletleri (`Color.kt`, `Theme.kt`).

## 3. Derleme & Canlı Yükleme
```powershell
cd "X:\mrspy\Reverse Engineering\Projeler\QCYbutMOD"
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n com.aegissoft.qcybutmod.debug/com.aegissoft.qcybutmod.ui.MainActivity
```

## 4. Tamamlanan ve Canlı Doğrulanan İyileştirmeler (2026-11-01)
1. **Çift Seslendirme Çakışması Çözüldü:**
   - Kulaklığın dahili İngilizce istem sesi `0x1D` (Tone Volume) seviyesi 0'a çekilerek donanımsal olarak susturuldu.
   - Ayarlar ekranına Seslendirme Kaynağı seçici eklendi: `aegis (Türkçe)`, `Kulaklık (İngilizce)` ve `Kapalı`.
   - Mod değişimlerinde yalnızca seçilen Türkçe ses asistanı konuşuyor.
2. **Pil Seviyesi ve Birleştirme Doğruluk Hattı:**
   - `BatteryTracker` geliştirildi; GATT `0008` (3 bayt ham) ve `0x08` sorgusu birincil otorite yapıldı.
   - `0%` değeri "yok/kutuda değil" (`-1`) olarak işlendi, eski bayat pillerin asılı kalması engellendi.
   - Android Sistem HFP tek pil değerinin Sol/Sağ kulaklıkları ezmesi engellendi (Sol %8, Sağ %41 canlı doğrulandı).
   - 8 senaryolu JVM Unit Test paketi (`BatteryTrackerTest.kt`) başarıyla geçti.
3. **UI / UX Sıkışmaları Giderildi:**
   - `HomeScreen` içerisindeki `QuickToggleCard` bileşenine esnek iç boşluk (`contentPadding`) verilerek "Kulak Algılama" başlığının kesilmeden tam sığması sağlandı.
   - Tüm ekranlarda edge-to-edge ve insets koruması (`safeDrawingPadding`, `statusBarsPadding`, `navigationBarsPadding`) garanti altına alındı.
4. **Dokunmatik Jest Eşleme Donanım Uyumu:**
   - Tersine mühendislik çıktısı (`EarphoneKey.java`) incelenerek donanım ID'leri doğrulandı: Sol Uzun Basma = `7`, Sağ Uzun Basma = `8` (hatalı 11/12 ID'leri kaldırıldı).
   - Canlı cihazda yazma (`keyId=7, funcId=3`) ve donanımdan geri okuma (`07 03`) teyit edildi.
5. **AI Canlı Çeviri Temeli:**
   - Cihaz içi / çevrimdışı Google ML Kit Translate (~59 dil) ve Gemini Live Cloud desteği eklendi.
   - Sürekli konuşma algılama (`SpeechInput`), telefon ve kulaklık mikrofonu (SCO) seçimi, RMS ses dalgası görselleştiricisi ve kulaklıktan sesli okuma entegrasyonu tamamlandı.
   - `LiveTranslateScreen` arayüzü ana menüye bağlandı ve cihaz üzerinde canlı doğrulandı.

## 5. Stabilizasyon Turu — Canlı Doğrulama (2026-11-01 gece)
Bu tur tüm düzeltmeleri fiziksel cihaz üzerinde tekrar tekrar doğruladı (test = 14 JVM testi yeşil; `BleAdvertisementParserTest` + `QcyProtocolTest` + `BatteryTrackerTest`).

1. **P1 — Firmware Ses Susturma (0x1D) Nihai Çözüm:** Tek baytlı yazma firmware tarafından yok sayılıyordu (okuma hep `06 0F` kaldı). `QcyProtocol.createToneVolumeCommand` resmi QCY `ToneVolumeManagerActivity` else-branch'i gibi **2 bayta** çevrildi (`FF 04 1D 02 vv 00`). Canlıda 3 kez doğrulandı: TX `FF 04 1D 02 00 00` → RX `FF 04 1D 02 00 0F` (seviye 0 kabul). Artık yalnızca Türkçe TTS konuşur; İngilizce firmware istem sesi suskun (kulağınsal teyit kullanıcıdan).
2. **P2 — Pil 3-Yönlü Ground Truth GEÇTİ:** `publishBattery()` tek yayıcı; cihaz listesi (dedup) + state her yerde aynı. Resmi QCY uygulaması ile karşılaştırma: resmi uygulama **en yakın 10'a yuvarlar** (77→80, 59→60, statik), bizim değer GATT `0008` ham gerçekliği (75→71 pürüzsüz azalan). 5 yüzey (Home halkaları, Cihaz Seçim sayfası, donanım bilgileri, bildirim/servis, sesli okuma) aynı `deviceState` akışından beslenir.
   - Ek düzeltmeler: `onBeacon` taze GATT altındaki kutu seviyesi birleşimi; bilinmeyen vendor-vid beacon junk'ı (X-403BT) filtresi; `connect()` seed sırası (bayat liste girdisi yerine HFP/taze beacon); klasik MAC birleştirmesi (`resolveMergedClassicMac`: canlı/ACL + BLE-önbellek tercihi).
   - HFP çapraz cihaz sızıntısı incelendi: alıcı zaten adres+QCY-adı filtreli, loglarda hiç tetiklenmedi — kod değişikliği gerekmedi.
3. **REDMI Koruma Kalkanı:** `reconnectSelectedDevice` aktif-ses fallback'i ve `connect()` girişinde `isQcyDeviceName` guard'ı — REDMI Buds 8 Lite aktifken hero asla zehirlenmiyor, hedef reddedilip taramaya düşüyor (canlıda loglanarak doğrulandı).
4. **P3 — Satır Yüksekliği Kök Nedeni:** M3 `MaterialTheme` içeriği kendi `LocalTextStyle` (bodyLarge 24sp) ile sağladığı için dıştaki override etkisizdi; provider **içeri alındı**. 84px/satır → 45px/satır (ui dump + ekran görüntüsü). Adaptif ANC açıklaması Switch'ten `padding(end=8.dp)` ile ayrıldı.
5. **Bildirim Uyandırma (Notify-Heal):** Soğuk oturumda bildirimlerin ölmesine karşı kendi kendini iyileştirme: 45s sessizlik → `0xFE` sarmalayıcı ile prob; 75s → CCCD yeniden abonelik. Canlıda prob + resub + ACK=status0 döngüsü doğrulandı; `executeGattOp` timeout/false artık AegisLogger'a yazıyor.
6. **P4 — Canlı Çeviri E2E:** `LanguageCatalog` = ML Kit'in tam 59 dili (HAITIAN_CREOLE + MALTESE eklendi). Ekranda canlı metin akışı doğrulandı (kaynak+çeviri kartları, örn. `naber → whats up`); "Kulaklığı Oku" çipi açık (`translate_tts_headset=true`). Sesli okuma teyidi kullanıcı kulağıyla.
7. **Cihaz Dışı İyileştirmeler:** Cihaz listesinde aynı isim tek girişe düşürülüyor (phantom `DD:06` duplicate kaldırıldı — 5→4 kayıt); GATT + A2DP çift bağlanıyor (protokol satırı "Bağlı (GATT 0000A001 + A2DP)" canlı doğrulandı).

## 6. Round-4 Turu — Kullanıcı Kapsamı (2026-11-02 gece)
Kullanıcı 12 maddelik yeni istek turu; tüm düzeltmeler canlı cihazda doğrulandı (test = 14 JVM testi yeşil).

1. **Çeviri Geçmişi:** tekil silme (`deleteTranscript` + kart üzerinde DeleteOutline butonu), en yeni en üstte sıralama (`asReversed` + otomatik kaydırma item 0), Temizle → tam temizlik.
2. **#10 İki Yönlü Konum Özelleştirme → Yüz Yönlü Mod:** ilk yorum soldan/sağdan hizalama sanıldı; kullanıcı netleştirince **180° döndürme (Ters/karşı taraf)** olarak yeniden yazıldı: `transcriptRotate` tercihi + kart `Modifier.rotate(180f)` + diyaloğa "Yön (karşı taraf için): Normal / Ters (karşı taraf)" çipleri (canlı doğrulandı). Metin sırası çipleri (Kaynak/Çeviri Üstte) korundu, varsayılana döndürüldü.
3. **#3 Firmware Ses Her Yerden Kapalı:** 0x1D handler'ı firmware sesi dirilirse (self-restart sonrası) otomatik yeniden susturuyor (2s debounce); `setPromptVolume` politika guard'ı (yalnız `FIRMWARE_EN`); Ayarlar "Kulaklık Bip Ses Düzeyi" kartı aegis/kapalı kaynakta "Kapalı" + pasif slider (canlı doğrulandı).
4. **#4 Kart Uyarıları:** LDAC + Kulak Algılama kartlarında amber "Uyarı: Kulaklık kendiliğinden kapanıp açılabilir" (canlı doğrulandı).
5. **#9 Çıkış Yolu Seçimi:** tek çip döngüsü Kulaklığa Oku → Telefonda Oku → Ses Kapalı (`VoiceSettings.translate_tts_route`; telefon hoparlörü `setCommunicationDevice` + `MODE_IN_COMMUNICATION` ile zorlanır, bittiğinde geri alınır). Canlı 3 durum da doğrulandı.
6. **#11 Türkçe Ses Seçimi:** `onInit`'te en yüksek `quality`'li Türkçe voice seçilir (`trSelectedVoice`); çeviri TTS'i hedef dil ayarladıktan sonra tr voice'u geri uygular.
7. **#7 Fit Test Bağlantı Kapısı:** `startTest` bağlı değilken önce `reconnectSelectedDevice()` + 6s bağlantı bekleme; kurtarılamazsa aynı "Kapalı değil" mesajı (tek deneme özyinelemeli, sınırlı).
8. **#5 Jest Eşleme Kök Nedeni:** resmi panel JSON'u (`POST api.watch.qcy.com/product/findControlPanels`, `panel_19786.json`) 19786 için **uzun basma olayı YOK** — 7/8 keyId'li satırlar firmware tarafından yok sayılıyordu; kaldırıldı (Tek/Çift/Üç = eventID 1-6 kalır, funcId'ler resmiyle %100 eşleşiyor).
9. **Bağlantı Düzeltmeleri:** (a) watchdog klasik-MAC doğrudan LE zaman aşımında artık `autoConnect=true` taramayı yeniden kuruyor (canlı loglandı); (b) phantom `DD:06` önbellek zehirlenmesine karşı 3 katman (bonded-guard knownBle, bonded-guard cachedBle, `saveBleMacMapping` bonded adresi atlıyor).
10. **Ayarlar Donanım Bilgisi (bağlı değilken):** BLE GATT MAC satırı klasik MAC'e düşmüyorsa `--` gösterir (dup kalktı); firmware bilinmiyorsa `1.0.0` yerine `--` (model varsayılanı boşaltıldı). Canlı doğrulandı.
11. **Canlı Çeviri Dil Kartları Simetrisi:** "Hedef Dil (Kulaklık)" 2 satıra sarılıp bayrakları kaydırıyordu → "Hedef Dil"; iki kart tam hizalı (canlı dump: 512/512).

## 7. Yeni Özellikler & Hata Düzeltme Turu — Task 1–11 (2026-11-02)
Plan: `.superpowers/sdd/qcybutmod-yeni-zellikler-hata--2026-11-02-approved/` (12 görev; bu bölüm Task 1-11 kodunu, §8 ise Task 12 limit/test kaydını belgeler). Doğrulama: **49 JVM testi yeşil (10 suite, 0 hata) + `assembleDebug` BUILD SUCCESSFUL**.

### Yeni Özellikler
1. **Çeviri Günlüğü:** `TranslateJournalStore` kalıcı oturum dosyaları (50 oturum cap, en eskisi düşer) + `LiveTranslateScreen` Geçmiş sheet: büyük/küçük harf duyarsız arama (oturumlar arası), tekil oturum silme, en yeni en üstte.
2. **Gürültü Göstergesi:** `NoiseMonitor` canlı dB + 10 saniyelik geçmiş grafiği. Firmware ANC-mic ham akışını BLE'ye açmaz → ölçüm telefon mikrofonuyla yaklaşık (§8-a).
3. **Bulma Paternleri:** Sürekli / İkili / Üçlü desen seçimi; `CapabilityParser.hasFind` yetenek kapısı ile (`find_earphone` yoksa segment gösterilmiyor).
4. **Firmware Delta → Panel JSON Yenileme:** firmware sürümü değişince (`BleConnectionManager` firmware delta takibi) `PanelRepository` sürüm-lü panel cache'i (`panel_19786_vX.json`) düşer ve `panel_19786.json` otomatik yeniden fetch edilir; Ayarlar'da durum satırı gösterilir.
5. **Yetenek Tespiti (N20 / H4):** `CapabilityParser.parse(vendorId, features, ldacSupported)` — N20 (32884) tam yetenek seti; H4 (16849/16850) sağ-only dokunma + LDAC; `key_function.events`'ten uzun basma, `settings` cmdid 36'dan multipoint, `eq.bands` sayısından EQ segmentleri.
6. **Kadın/Erkek Ses Seçimi + Ayar Kartı Restyle:** `VoiceSettings.voice_gender` (`female` varsayılan / `male`) → `VoiceGenderResolver` (voice pick + pitch); ayarlar kartı görsel yenileme.

### Hata Düzeltmeleri
1. **FE Bulk Prompt-Mute Açığı:** `PromptMutePolicy.shouldRemute` — aegis/kapalı ses kaynağındayken firmware'e giden FE ses-seviye yazması bip sesini diriltirse otomatik yeniden susturur (`firmware_en` modunda asla susturmaz); 2 sn debounce yankı fırtınasını keser (`BleConnectionManager` 0x1D handler'ı ile entegre).
2. **ANC Stale-Echo Guard:** `AncEchoGuard` — gönderilen `0x17` ANC komutunun dup/eski yankısı (stale status) uygulamayı yanlış moda çekmesin; 2.5 sn pencere, eşleşen yankı kabulü ve bekleyen temizliği.
3. **Multipoint Empty-List / Tazeleme:** FE+24 (liste sorgusu) / 4D (yanıt) / 4F (tazeleme) hattı — `QcyProtocol.parsePhoneListResponse`: boş payload durumu korur (state ezmez), `count=0` açık boş yanıtla listeyi temizler; `MultipointScreen` refresh akışı bu yanıtlarla beslenir.
4. **Fit Test Sahte Geçiş Kaldırıldı:** `FitTestManager` eşik aşılmadan "Geçti" vermeyi kesti; artık gerçek ölçüm sonucu döner (uydurma geçiş yok).
5. **Uzun Basma 2 sn Dönüşü:** §6-8'de panel JSON'da event bulunmadığı için kaldırılan L7/R8 satırı, 0x2B canlı kanıtı (`key_function` tablosu 7/8 anahtarları) ve `hasLongPress` yetenek kapısıyla "Uzun Basma (2 sn)" olarak geri alındı (`GestureScreen`, keyId 7/8). 19786'da `hasLongPress` gate kapalı kalır: `products.json`'da Long press yok (`key_function.events` "Long press" içermiyor) → satırlar görünmez. Satırlar yalnız `key_function` olayında "Long press" olan modellerde (ör. 23872-23879) ve `key_function`'ı olmayan modellerde (`events == null`) görünür. **⚠️ GÜNCELLEME (2026-11-04, Görev 8):** kullanıcı talebiyle gate kaldırıldı — "Uzun Basma (2 sn)" satırları artık **her modelde her iki kulakta da görünür**; readback firmware eski değere döndürürse "Bu modelde desteklenmiyor olabilir" toast'u çıkar (bkz. §10-8).

### Round-2 Hata Düzeltme Turu — Task 1–9 (2026-11-02 gece)
Plan: `.superpowers/sdd/qcybutmod-hata-dzeltme-ve-kapa-2026-11-02-approved/` (9 görev; §9 bu turun Task 7 borders kaydıdır). Doğrulama: **70 JVM testi yeşil (12 suite, 0 hata) + `assembleDebug` BUILD SUCCESSFUL (HEAD `fa1d5dd`)**. Commit ledger (dosya:satır çapaları güncel HEAD):

1. **`368c8de` — ANC stale-echo guard & legacy 0x0C drop:** `core/bluetooth/AncEchoGuard.kt:7` (2.5 sn pencere), `:14` (`onSent`), `:26-31` (`shouldApply` bekleyen yankı eşleşmesi); `core/bluetooth/BleConnectionManager.kt:1238` (guard örneği), `:1774` (0x17 uygulama kapısı), `:2021` (gönderim kaydı).
2. **`3456daa` — Pil kutusu keep-last, ham şarj bitleri, systemLevel satırı:** `core/bluetooth/BatteryTracker.kt:49` (0x00 → bilinmiyor/kapalı keep-last kuralı), `:52` (`onGattRaw`), `:97-104` (kutu seviyesi koruma + otoriter log); `ui/screens/SettingsScreen.kt:753-756` (SystemLevel satırı).
3. **`4d23b4e` — 1 baytlık GATT payload kutu seviyesini silmiyor:** `core/bluetooth/BatteryTracker.kt:97-100` (kısa payload'da `caseLevel` dokunulmaz).
4. **`48e975a` — Multipoint resmi gate + baytlar, connect blast azaltma, phone status bilinmeyen cihaz ekleme:** `core/bluetooth/QcyProtocol.kt:67` (`CMD_MULTIPOINT_QUERY 0x4D`), `:231` (`createMultipointCommand`), `:477` (`parsePhoneListResponse`); `core/bluetooth/BleConnectionManager.kt:2111-2121` (`setMultipoint` / `refreshMultipointDevices` FE+0x24 ve FE+0x4D birlikte), `:1831-1832` (0x24 ACK), `:1927` (0x4D liste ACK); `core/bluetooth/PhoneStatusPush.kt:19` (`Bilinmeyen cihaz ($mac)` ekleme).
5. **`b4ceb03` — Fit test resmi telefon-yanalı analizör, wav decode, mikrofon izin kapısı:** `core/audio/FitTestAnalyzer.kt:11` (analizör), `:17` (`analyze` PCM korelasyonu), `:53` (`decodeWavPcm16`); `ui/screens/FitTestScreen.kt:44-45,269-274` (`RECORD_AUDIO` izin kapısı).
6. **`c1fcce5` — Fit test mikrofon yakalama çevresinde Bluetooth SCO:** `core/audio/FitTestManager.kt:171` (SCO kapatma), `:179-184` (`startSco`).
7. **`1efe0d8` — Mute-first connect, debounce recheck, tone probe, sesli asistan jest kapısı:** `core/bluetooth/PromptMutePolicy.kt:11` (`shouldRemute`, 2 sn debounce); `core/bluetooth/BleConnectionManager.kt:1199-1204` (tone probe her 4. tick'te); `ui/screens/GestureScreen.kt:46-50` (`assistantAllowed` gate).
8. **`d5a78df` — Kadın ses envanteri kontrolü + Ayarlar uyarısı, AegisLogger ses log'u:** `core/audio/AegisVoiceAssistant.kt:124` (`selectTurkishVoice`), `:131` (`Turkish voice inventory` log satırı), `:155` (`hasFemaleTurkishVoice`); `core/audio/VoiceGenderResolver.kt:14` (`isFemaleCoded`), `:18` (`pickVoiceName`), `:29` (`pitchFor`); `ui/screens/SettingsScreen.kt:491` (uyarı metni).
9. **`5a72ec0` — Ses kaynak kartı sürekli dış hat, chip kenarlıkları, divider kalınlığı:** `ui/screens/SettingsScreen.kt:322` (kart `BorderStroke(1.dp, DarkBorder)`), `:466-486` (FilterChip kenarlıkları + şeffaf dolgu) — canlı ekran görüntüsü §9 ve §8-ADB listesinde ertelendi.
10. **`7821a0e` — Runtime uzun basma satırları, capability-gate'li ANC/LDAC/EQ, bilinmeyen vendorId=0:** `core/models/DeviceCapabilities.kt:7-11` (hasAnc/hasLdac/hasMultipoint/hasFind/hasLongPress), `:21-30` (features tabanlı türetme); `ui/screens/HomeScreen.kt:519` (ANC kartı gate), `:789` (LDAC gate); `ui/screens/EqualizerScreen.kt:223` (EQ band gate, shortcut notu); `core/bluetooth/BleAdvertisementParser.kt:286` (`else -> 0` bilinmeyen vendorId).
11. **`fa1d5dd` — Home EQ nav kartı yetenek bant sayısını gösteriyor:** `ui/screens/HomeScreen.kt:52` (`eqBands`), `:845` (`"$eqBands-Bant Parametrik Ekolayzer"` başlığı).

## 8. Bilinen Sınırlar / Geri Dönüş Kalemi
- Kutu (case) seviyesi: ham bayt her zaman `0x00` → `--` (firmware kutudayken okumuyor; kullanıcı onayıyla `0%` gösterilebilir).
- TTS duyulabilirlik (Türkçe tek ses + pil okuması) ve bildirim gölgesi görsel teyidi kullanıcı tarafında.
- Bağlı değilken seyrek `0x23` TX denemesi (zararsız, uyarı loglanıyor) — kök henüz izlenmedi.
- İki adet aynı model çifti listede birleşir (isim-bazlı dedup); gerekirse seri-no dedup'a geç.
- **Kulaklık LE-reklamı kapalıyken** (#5 jest e2e, #6 multipoint canlı toggle, #8 otomatik bağlanma, bağlı-yken ekran görüntüleri) uçtan uca test bloke — kullanıcı kulaklığı kutudan çıkarıp uyandırmalı; uygulama tarafı hazır (kalıcı tarama + beacon'da anında bağlanma).
- **(a) Gürültü ölçümü yaklaşık:** firmware ANC-mic ham akışını BLE'ye açmaz; `NoiseMonitor` gösterge telefon mikrofonuyla **yaklaşık** ölçer (yönlü/gürültü göstergesi amaçlı, kalibre cihaz değeri değil).
- **(b) `touchSides` H4 override sabit:** `CapabilityParser.rightTouchOnlyVendorIds = {16849, 16850}` — `products.json` yan (touch side) bilgisi taşımadığı için sabit kodlanmış override (`DeviceCapabilities.kt` shortcut yorumu); panel JSON yan verisi entegre edilene kadar geçerli.
- **(c) Canlı cihaz doğrulaması ADB ile yapıldı (2026-11-02 17:35-18:32):** Tüm canlı kontroller `ADB_LIVE_TEST_NOTES.md` dosyasında kanıt loglarıyla; yalnızca ses cinsinin (Kadın/Erkek) kulakla doğrulanması kullanıcının kulağına kaldı (ANC takılı tekrar testi 18:31'de kullanıcı canlı kullanarak kapattı).

- **(d) Round-2 canlı ADB sahnesi (2026-11-02 23:34-23:52) — kısmen bloke, dürüst durum:** Yeni build kuruldu ve uygulama başlatıldı (`app-debug.apk`, HEAD `fa1d5dd`), `AegisLogger` dosyası (`filesDir/qcy_debug.log`) ADB ile çekildi. Kulaklıklar BLE reklamı YAYINLAMIYORDU: klasik bağlantı `cmd bluetooth_manager Disconnect` ile düşürüldükten sonra 5+ dakika tarama sonucu gelmedi; telefonun aktif ses cihazı REDMI Buds 8 Lite'tı ve `Hızlı Bağlan` log'u doğru şekilde `"Active audio device is not QCY (REDMI Buds 8 Lite) - scanning instead"` diyerek güvenli fallback'e düştü. Ekran o sırada aktif kullanımdaydı — UI tap/enjeksiyonu 2'den fazla güvenilmez denemeden sonra atlandı. Sonuç: bu turda bağlanma bağımlı canlı kontroller çalıştırılamadı.
- **(e) Round-2 ADB bekleyen kontroller (bağlantı + kullanıcı tarafı):** (1) kadın/erkek ses cinsinin kulakla doğrulanması — Google TTS Türkçe ses paketi indirildikten sonra; (2) multipoint OFF baytı `0x00` + `0x4D` liste canlı testi (bağlı kulaklıkla); (3) fit test `RECORD_AUDIO` canlı koşusu (kulaklıklar takılıyken); (4) borders ekran görüntüsü (T7, Ayarlar ekranı); (5) tone-probe log satırı kontrolü (bağlantı gerekli). Bunların hiçbiri sahte kaydedilmedi — yukarıdaki (d) maddesindeki engel nedeniyle bekliyor.
- **(f) Round-2 pasif log kanıtları (önceki oturum, aynı turun build'i):** `Muted firmware voice prompt to 0 for AEGIS_TR` (20:38, T5 mute-first), `Hardware ACK Multipoint Switch: true` (20:38, T3), `GATT Authoritative Battery: L=16% R=-1% Case=-1% (sys=40%)` tekrarları (22:03, T2 keep-last), `Hardware ACK ANC (0x17)` yankıları + `Battery notified from 0008` (22:03, T1/T2). Tone probe TX cadencesi ve taze ANC toggle revert kontrolü bağlantı olmadan doğrulanamadı.
- **(g) Yan etki kaydı:** Canlı sahnede aktif ses cihazı zorlaması için medya sesi toggle'ı (Ayarlar > QCY MeloBuds Pro) ve tüm bağlantıyı düşürme (`cmd bluetooth_manager Disconnect`) denendi; sonrasında aktif cihaz REDMI'ye döndü, profiller yeniden bağlandı. `svc power stayon true` geçici olarak açıldı ve sahne sonunda `false` ile geri alındı.

### Doğrulama Test Listesi (Task 12 kaydı)
**JVM Unit Testleri — 70 test / 12 suite, 0 hata (2026-11-02 round-2 güncelleme, `:app:testDebugUnitTest` yeşil):**
| Suite | Adet | Görev |
| :--- | :--- | :--- |
| `PromptMutePolicyTest` | 9 | FE bulk prompt-mute (Task 2) + mute-first/debounce (round-2) |
| `AncEchoGuardTest` | 5 | ANC stale-echo guard (Task 3 + round-2 368c8de) |
| `QcyProtocolTest` | 8 | `parsePhoneListResponse` FE+24/4D/4F (3 test, Task 5) + ton sesi + multipoint baytları (48e975a) |
| `TranslateJournalStoreTest` | 7 | Çeviri günlüğü roundtrip/arama/silme/cap (Task 9) |
| `CapabilityParserTest` | 8 | N20/H4 yetenek tespiti (Task 4) + round-2 gate testleri (48e975a, 7821a0e) |
| `BatteryTrackerTest` | 13 | Pil doğruluk hattı (§4) + keep-last/1-bayt (3456daa, 4d23b4e) |
| `BleAdvertisementParserTest` | 3 | Beacon ayrıştırma (§5) + bilinmeyen vendorId=0 (7821a0e) |
| `NoiseMonitorTest` | 4 | Gürültü göstergesi (Task 10) |
| `VoiceGenderResolverTest` | 5 | Kadın/erkek ses seçimi (Task 1) + envanter (d5a78df) |
| `PanelCacheNamingTest` | 2 | Panel cache adlandırma (Task 11) |
| `FitTestAnalyzerTest` | 4 | Fit test analizör/wav decode (b4ceb03) |
| `PhoneStatusPushTest` | 2 | Phone status bilinmeyen cihaz ekleme (48e975a) |

**Cihazda (ADB) yapılan canlı kontroller — 2026-11-02, kanıtlar `ADB_LIVE_TEST_NOTES.md`:**
1. **✅** FE+24/4D/4F burst gönderildi; **0x24'e firmware cevap veriyor** (`[01]`/`[00]` okuma çalışır); 0x4D/0x4F'e sessiz → boş-liste koruma + seed/boş-durum UI doğru davrandı.
2. **✅** Uzun basma L7/R8: 19786'da `hasLongPress=false` → satır gizli (beklenen, §7-5); jest atama yolu canlı → `0000000D` write `keyId=5,funcId=3` ACK + 415ms readback `5=3` ✓.
3. **✅** Panel JSON fetch: char-read hook fix'iyle `panel_19786_v2.0.6.json` canlı indirildi, Settings satırı `Ok`.
4. **✅** Fit test: sonuç RX'i gelmedi → sahte geçiş yok, dürüst Failed (eski battery-heuristic yok).
5. **✅** Prompt re-mute: her connect'te `FF 04 1D 02 00 00` (5+ kez canlı).
6. **✅** FindDevice kısa ekran: 1280x2800'de clip yok; İkili patern burst zamanlaması 400/300ms tam.
7. **✅ (bonus, yeni fix'ler canlı doğrulandı):** auto-reconnect (0x24 write → link drop ×4 → 2sn'de 1/5 ile geri dönüş; "Bağlantıyı Kes" negatif testi sessiz), ANC stale-echo guard (5 yankı bastırıldı, UI sabit), çeviri günlüğü persist+arama, notify-heal CCCD resubscribe, kadın/erkek chips crash'siz.

**Kullanıcı katılımını bekleyen:** yalnız (ii) ses cinsinin (Kadın/Erkek) kulakla doğrulanması — ANC/şeffaf tekrar testi 18:31'de kapandı (yukarıdaki 7 numaralı madde).

## 9. Hata Düzeltme Turu — Task 7 (2026-11-02)
Plan: `.superpowers/sdd/qcybutmod-hata-dzeltme-ve-kapa-2026-11-02-approved/`. Doğrulama: **68 JVM testi yeşil (12 suite, 0 hata) + `assembleDebug` BUILD SUCCESSFUL**.
- **"Sesli Bildirim ve Anons Kaynağı" kartı dış hatları:** kaynak konteyner `Surface`'e `border = BorderStroke(1.dp, DarkBorder)`; seçili satır vurgusu `RoundedCornerShape(8.dp)` → `12.dp` (konteyner yarıçapıyla eşleşti); `HorizontalDivider` 0.8.dp → 1.dp (alt-piksel/noktalı çizgi giderildi); Kadın/Erkek `FilterChip`'lerine `DarkBorder` kenarlığı + `containerColor = Color.Transparent` / `selectedContainerColor = theme.primary.copy(alpha = 0.10f)` dolguları.
- Canlı ekran görüntüsü doğrulaması bir sonraki ADB oturumuna ertelendi (kompozisyon derleme ile doğrulandı).

## 10. Kapsamlı Hata Giderme & Yüzey Turu — Görev 1–11 (2026-11-04)
Plan: `C:\Users\mrSpy\.plannotator\plans\qcybutmod-son-hata-giderme-ve--2026-11-04-approved.md` (11 görev). **Doğrulama: 95 JVM testi / 18 suite / 0 hata + `assembleDebug` + `assembleRelease` BUILD SUCCESSFUL + release cihazda canlı (3× soğuk başlatma, çökme yok).**
Commit ledger: `4be896d` → `cbe26e4` → `49cd5b0` → `fa936e2` → `a17e999` → `2a25b63` → `b5eadd2` → `5c0ce56` → `bc60622` → `c0c9b38`.

1. **KÖK NEDEN — Paylaşılan APK'daki açılış çökmesi (kullanıcı isteği #1) ÇÖZÜLDÜ (`c0c9b38`):** DropBox forensics (`adb shell dumpsys dropbox --print`) 6 identical `data_app_crash` üretti: `java.lang.SecurityException: Need android.permission.BLUETOOTH_CONNECT … A2dpServiceBinder` → `BleConnectionManager.registerProfileProxyListeners$1.onServiceConnected (:235)` → ana iş parçacığında yakalanmamış → taze kurulumda (izin henüz verilmemişken) her açılışta çökme. **Yerelde debug nedeniyle hiç üretilmedi** (cihazda izin zaten veriliydi). Düzeltme: `hasBtConnectPermission()` guard + try/catch A2DP geri çağrısında; `systemBtReceiver.onReceive` tüm gövdesi try/catch; `connect()` girişinde izin erken çıkışı. **Kanıt:** izin verilmemiş taze release kurulumu → 3× soğuk başlatma → süreç ayakta, odak MainActivity, DropBox kaydı 6'da sabit (yeni yok). CrashReporter açılışta çalıştı (DropBox `Crash-Handler` satırı) — "Hata kaydını paylaş" butonu bu yoldan besleniyor.
2. **Yeniden adlandırma + sürüm/imza (`cbe26e4`):** kullanıcıya dönük her "aegis QCY" → "QCYbutMOD" (manifest app/tile etiketleri, strings `app_title`, bildirim kanalı/başlıkları, Home/Settings metinleri, PROJECT başlığı); `git grep -i -E "aegis\s*qcy"` **temiz**. versionCode **200** / versionName **1.2.0** (BuildConfig tek kaynak). Release imzası: `release/aegissoft-release.jks` (alias `aegissoft`, CN=aegisSoft) + gitignored `keystore.properties` — **keystore yedeklenmazse gelecekte release yükseltmesi imkânsız**. Release APK 86.4MB, apksigner **v2 doğrulandı**, `application-label:'QCYbutMOD'`.
3. **Bağlantı kök nedeni (`49cd5b0`, ADB kanıtlı):** `refreshBondedDevices(autoConnect)` hardcoded false + BLE MAC cache eksik anahtar (`LAST_BLE_` fallback eklendi) → telefon bağlıyken uygulama hiç bağlanmıyordu; artık açılışta otomatik LE GATT (`C4:AC:60:XX:XX:XX`), 10s rate-limit (`AutoConnectDecision`), "Otomatik bağlantı: …" durum satırı.
4. **FeatureGate + ActionMemory (`fa936e2`):** 20 komut giriş noktası `gateFeature` ile bağlı değilken engellenir (Toast: "Kulaklık bağlı değil — X yapılamaz…"); son 20 hareket `filesDir/action_history.json` + Settings "Son Hareketler" kartı + Home "Son işlem" satırı; bağlantıda `markSynced`.
5. **Fit test (`a17e999`):** `gateFailureFor` ilk satır (bağlı değilse ses/audio başlamadan Failed), hata taksonomisi (NOT_CONNECTED / PERMISSION_DENIED + ayar deeplink / SCO_UNAVAILABLE / LOW_CONFIDENCE / UNKNOWN — başlık+çözüm+"Tekrar dene"), mikrofon izni kartı; `FitTestGateTest`.
6. **Multipoint dürüstlük (`2a25b63`):** 0x4D/0x4F TX/RX hex-dump + WAITING→NO_RESPONSE timeout, "Yanıt bekleniyor / kulaklık yanıt vermedi" kartı + 3 adımlık gerekçe + "Tanı Kaydı" paylaş butonu; yerel eşleşmeler "(yerel)" etiketiyle AYRI kart — liste asla uydurulmaz.
7. **Görev 9 — çoklu model/tablet (`5c0ce56`):** `CapabilityParser.unknownModel` (bilinmeyen model = tümü açık, LDAC kapalı), `ProductDatabase.forcedVendorId` + Settings "Kulaklık Modeli" seçici ("Otomatik" / "Genel QCY Aygıtı" / 199+ ürün), desteklenmeyen ANC/LDAC artık **gizlenmiyor**: görünür not + devre dışı buton; >600dp tablette 780dp ortalanmış 2 sütunlu navigasyon; `ProductsJsonValidationTest`. **Bilinçli sapmalar:** FeatureCapability enum'u atlandı (işlevsel karşılığı yok), `touchSides` H4 sabiti `{16849,16850}` korundu (panel JSON'da yan verisi yok, test davranışı sabitliyor).
8. **Uzun basma (`b5eadd2`):** §7-5 gate'i kaldırıldı — L7/R8 satırları her iki kulakta hep görünür; readback uyuşmazsa uyarlı toast.
9. **Kadın TR ses tek dokunuş (`bc60622`):** `FemaleVoiceInstaller.resolve` sırası = `ACTION_INSTALL_TTS_DATA` → sistem TTS ayarları → `market://details?id=com.google.android.tts` (CHECK_TTS_DATA sonuç-tabanlı olduğundan bilinçli olarak aday değil); Settings Ses kartı indirme kartına dönüştü (3 satır kılavuz + "Ses paketini indir", ON_RESUME'da yeniden sorgu → hazır olunca yeşil "Türkçe kadın sesi hazır"); ses gelene kadar pitch **1.35 simülasyon** + `female_missing_fallback` log; manifest `<queries>` (tts/speechservices/vending). `TtsInstallIntentTest` (6 test).
10. **Manifest sertleştirme (`4be896d`):** `uses-feature bluetooth_le required=false`, `allowBackup=false`, `SYSTEM_ALERT_WINDOW` kaldırıldı, izinler BT-zorunlu / opsiyonel olarak ayrıldı (mikrofon/bildirim reddi BT'yi artık kill etmiyor), FGS start try/catch, Tile servisi coroutine leak fix, CrashReporter + Ayarlar "Hata kaydını paylaş".

### Güncel Bilinen Sınırlar (§8'i tamamlar)
- Kutu pili `0x00` → `--`; multipoint 0x4D/0x4F Honor'da sessiz → artık dürüst NO_RESPONSE kartı + tanı paylaşımı; fit test canlı ADB koşusu bekliyor (kulaklık takılı + mikrofon).
- **Düzeltme turu (2026-11-04):** (a) Taze kurulumda MAC cache yokken klasik-MAC üzerindeki LE denemesi boşa gidiyordu ("bağlantı zaman aşımı") → `AutoConnectDecision.deriveLeAddress` ile türetilmiş LE adresi (XOR 0x40 ilk / 0x01 son bayt; kanıt çifti 84:AC:60:XX:XX:XX ↔ C4:AC:60:XX:XX:XX) bir kez denenir, başarısızsa taramaya düşülür; başarılı bağlantı cache'e yazılır (ilk bağlanış ~10 sn, sonrakiler ~0.07 sn — ADB kanıtlı). (b) Kadın ses paketi yüklüydü ama yeni Google TTS TR kimlikleri (`trtr-x-cfs/efu/mfm`=kadın, `tmc/ama`=erkek, `tr-TR-language`=nötr) eski `trf/trm` kontrolüyle eşleşmiyordu → `VoiceGenderResolver` güncellemesi (crosstales RT-Voice ses tablosuyla doğrulandı); artık `cfs-local` seçiliyor, `female_missing_fallback`/1.35 simülasyon devrede değil. 101 JVM testi yeşil.
- Release `com.aegissoft.qcybutmod` ≠ debug `.debug` → debug kurulumu olan cihazda **önce `adb uninstall com.aegissoft.qcybutmod.debug`** (farklı imza/id). Dağıtım: tanıdık-aile APK; Play Store yok.
- 95 MB `QCYbutMOD_v1.1.0_20261102.apk` (repo kökü, gitignore dışı) eski hatalı paylaşımdır — **yerine `app\build\outputs\apk\release\app-release.apk` (1.2.0) dağıtılacak**.
