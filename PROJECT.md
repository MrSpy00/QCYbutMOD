# PROJECT.md — QCYbutMOD

## 1. Proje Kimliği & Standartlar
- **Proje Adı:** QCYbutMOD (aegis QCY)
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
5. **Uzun Basma 2 sn Dönüşü:** §6-8'de panel JSON'da event bulunmadığı için kaldırılan L7/R8 satırı, 0x2B canlı kanıtı (`key_function` tablosu 7/8 anahtarları) ve `hasLongPress` yetenek kapısıyla "Uzun Basma (2 sn)" olarak geri alındı (`GestureScreen`, keyId 7/8). 19786'da `hasLongPress` gate kapalı kalır: `products.json`'da Long press yok (`key_function.events` "Long press" içermiyor) → satırlar görünmez. Satırlar yalnız `key_function` olayında "Long press" olan modellerde (ör. 23872-23879) ve `key_function`'ı olmayan modellerde (`events == null`) görünür.

## 8. Bilinen Sınırlar / Geri Dönüş Kalemi
- Kutu (case) seviyesi: ham bayt her zaman `0x00` → `--` (firmware kutudayken okumuyor; kullanıcı onayıyla `0%` gösterilebilir).
- TTS duyulabilirlik (Türkçe tek ses + pil okuması) ve bildirim gölgesi görsel teyidi kullanıcı tarafında.
- Bağlı değilken seyrek `0x23` TX denemesi (zararsız, uyarı loglanıyor) — kök henüz izlenmedi.
- İki adet aynı model çifti listede birleşir (isim-bazlı dedup); gerekirse seri-no dedup'a geç.
- **Kulaklık LE-reklamı kapalıyken** (#5 jest e2e, #6 multipoint canlı toggle, #8 otomatik bağlanma, bağlı-yken ekran görüntüleri) uçtan uca test bloke — kullanıcı kulaklığı kutudan çıkarıp uyandırmalı; uygulama tarafı hazır (kalıcı tarama + beacon'da anında bağlanma).
- **(a) Gürültü ölçümü yaklaşık:** firmware ANC-mic ham akışını BLE'ye açmaz; `NoiseMonitor` gösterge telefon mikrofonuyla **yaklaşık** ölçer (yönlü/gürültü göstergesi amaçlı, kalibre cihaz değeri değil).
- **(b) `touchSides` H4 override sabit:** `CapabilityParser.rightTouchOnlyVendorIds = {16849, 16850}` — `products.json` yan (touch side) bilgisi taşımadığı için sabit kodlanmış override (`DeviceCapabilities.kt` shortcut yorumu); panel JSON yan verisi entegre edilene kadar geçerli.
- **(c) Canlı cihaz doğrulaması ertelendi:** Task 1-11 canlı cihaz kontrolleri bu turda ADB bağlıyken yapılamadı; doğrulama JVM testleri + derleme ile tamamlandı (aşağıdaki canlı kontrol listesi bekliyor).

### Doğrulama Test Listesi (Task 12 kaydı)
**JVM Unit Testleri — 49 test / 10 suite, 0 hata (2026-11-02, `:app:testDebugUnitTest` yeşil):**
| Suite | Adet | Görev |
| :--- | :--- | :--- |
| `PromptMutePolicyTest` | 6 | FE bulk prompt-mute (Task 2) |
| `AncEchoGuardTest` | 4 | ANC stale-echo guard (Task 3) |
| `QcyProtocolTest` | 7 | `parsePhoneListResponse` FE+24/4D/4F (3 test, Task 5) + ton sesi |
| `TranslateJournalStoreTest` | 7 | Çeviri günlüğü roundtrip/arama/silme/cap (Task 9) |
| `CapabilityParserTest` | 5 | N20/H4 yetenek tespiti (Task 4) |
| `BatteryTrackerTest` | 8 | Pil doğruluk hattı (§4) |
| `BleAdvertisementParserTest` | 2 | Beacon ayrıştırma (§5) |
| `NoiseMonitorTest` | 4 | Gürültü göstergesi (Task 10) |
| `VoiceGenderResolverTest` | 4 | Kadın/erkek ses seçimi (Task 1) |
| `PanelCacheNamingTest` | 2 | Panel cache adlandırma (Task 11) |

**Cihazda (ADB) yapılacak canlı kontroller — bekliyor:**
1. FE+24/4D/4F multipoint liste burst (boş-liste koruma + refresh).
2. Uzun basma L7/R8 2 sn yazımı ve firmware'den geri okuma.
3. Panel JSON fetch (firmware delta → otomatik yenileme).
4. Fit test gerçek sonuç (sahte geçiş yok teyidi).
5. Prompt re-mute (FE ses-seviye yazınca firmware bip'inin otomatik susması).
6. FindDeviceScreen kısa ekran taşma kontrolü (chips +44dp, park edilen T8 bulgusu).
