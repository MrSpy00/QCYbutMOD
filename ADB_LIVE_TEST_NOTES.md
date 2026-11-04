# QCYbutMOD ADB Canlı Test Kayıtları — 2026-11-02

> Ortak çalışma notu: telefon kullanıcı ile birlikte kullanılıyor (log'da kullanıcının kendi tıklamaları da var).
> Kanıt kaynağı: `adb shell run-as com.aegissoft.qcybutmod.debug cat files/qcy_debug.log` (AegisLogger) + screencap.
> Cihaz: 192.168.0.160:40471 (Honor Magic PTP_N49, Android 16), kurulum: `com.aegissoft.qcybutmod.debug` (applicationId `.debug` suffix), APK 95.7MB, HEAD 87ccd86.
> Gradle kapısı: `:app:testDebugUnitTest` 49/49 + `assembleDebug` BUILD SUCCESSFUL (17:45, UP-TO-DATE).

## Sonuç tablosu

| # | Test | Durum | Kanıt |
|---|------|-------|-------|
| 0 | UI açılışı / kenar boşlukları | ✅ | screencap qcy_home.png: status bar ile çakışma yok, safeDrawingPadding çalışıyor |
| 0b | Ortam gürültü kartı (T10) | ✅ | canlı -39,9/-43,7 dB + Canvas dalga formu işliyor; mikrofon izni verilmiş |
| 1 | Prompt mute (İngilizce asistan susturma) | ✅ | 17:45:33.620 `TX FF 04 1D 02 00 00` "Muted firmware voice prompt to 0 for AEGIS_TR"; 1D/FE cevapları vol=0; tekrar mute tetiklenmedi (policy vol==0 → no-op) |
| 2 | Multipoint burst FE+24/4D/4F (T5) | ✅/⚠️ | 17:45:59.634 üçlü TX + ACK'lar; 0x24 toggle ON → ACK `[01]` OK; **firmware 0x4D/0x4F'e hiç cevap vermiyor** (telefon listesi boş kalır → UI "Eşleşen telefon bulunamadı" davranışı devrede); 0x24 OFF/ON iki kez ACK'lendi |
| 2b | Multipoint ON sonrası disconnect | ⚠️ gözlem | 17:46:00.321 `TX FF 03 24 01 01` ACK → 435ms sonra `GATT Disconnected (status 21)`; uygulama tarafında crash yok, UI "Bağlı Değil" + Hızlı Bağlan; 17:47:18 Hızlı Bağlan → tam bulk reconnect ✓. İkinci açılışta disconnect tekrarlanmadı. Firmware tarafı olası (multipoint enable radio restart) — tekrar üretilecek |
| 3 | Reconnect bulk doğrulama | ✅ | 17:47:18 tüm bean'ler (0x17/0x09/0x2C/0x10/0x16/0x1D/0x22/0x23/0x24/0x2B) ACK + 0008/000D/000B read'leri OK; R=91% |
| 4 | Gesture tablosu canlı okuma | ✅ (dinamik) | 0x2B + 000D read her reconnect'te güncel; NOT: 17:47 tablosu {1=0,2=1,3=1,4=3,5=4,6=5,7=0,8=0} — kullanıcı ekranı değiştiriyor |
| 5 | uzun basma L7/R8 firmware'de mevcut | ✅ (kanıt) | her 0x2B tablosunda `7=0, 8=0` — firmware key 7/8'i tanıyor (atanmamış) |

## Yapılacaklar (sıradaki turlar)
- [x] ANC ↔ Normal ↔ Şeffaf chip tıklamaları — **guard canlı çalıştı**: her TX'te firmware `0x28 raw=0` ACK + **eski durum `0x28 FF 05 17 03 02 00 00` yankıları "stale ANC echo ignored" ile bastırıldı** (17:49:16-20, 17:50:03; 5 kez); UI ANC'de kaldı, Normal'a geri zıplamadı → eski "mod değişince UI güncellenmiyor" bug'ı giderildi ✓. TX frame'leri doğru: ANC `01 05 00`, Şeffaf `03 01 03`, Normal `02 00 00`. **KALAN**: firmware 0x17 state okuması 5 sn boyunca hep `[02 00 00]` döndü (0x28 ACK var) — kulaklar takılı değilken (0x2C inEar=false, sol L=-1 tek mod) mode-switch'i doğrulayamadık → **kulaklık takılıyken yeniden test edilecek** (not: eski oturumda iki kulakla anlık `[01 05 00]` echo vardı)
- [x] MultipointScreen — **✅ 18:08**: switch tap(1077,629) ✓ (1077,548 ıska), Yenile tap(1109,968), seed "HONOR PTP-N49/Bu Cihaz • Aktif Ses", firmware 0x4D'e cevapsız (beklenen davranış)
- [x] GestureScreen — **✅ 18:17-18:19**: "Uzun Basma (2 sn)" satırı 19786'da **gizli (beklenen)**: `hasLongPress=false` çünkü products.json 19786 key_function = Touch/Double/Triple only (final review kararı: gate 19786'da kapalı; yalnız Long press içeren modellerde görünür — PROJECT.md §7-5'te açıklandı). **Atama yolu canlı**: Üç Dokunma→Sonraki Parça = `Writing KeyMapping 000D: keyId=5, funcId=3` ACK + 415ms readback `5=3` ✓; geri alma `funcId=6` → readback `5=6` (orijinal tablo {1=1,2=1,3=2,4=3,5=6,6=5,7=0,8=0} aynen restore ✓).
- [x] Settings Panel JSON satırı — **✅ 17:56/18:07**: char-read hook fix ile `Panel JSON: Ok(file=panel_19786_v2.0.6.json)` (84B), relaunch'ta cache hit, satır Ok
- [x] Settings ses cinsi Kadın/Erkek — **✅ 17:58**: chips tap (Erkek 1059,2672 → Kadın 804,2672), Test Et tap(679,1010), crash yok; ses farkı kullanıcının kulağıyla doğrulanacak (AegisVoiceAssistant log yazmıyor)
- [x] FindDeviceScreen — **✅ 18:20**: chipler görünür (Sürekli [168,1775]/İkili/Üçlü, hasFind=true); "İkili"+Sol Kulaklık → **DOUBLE burst tam zamanlama**: `TX FF 03 05 01 01`+`3F 01 01` @49.037 → stop @49.439 (400ms on ✓) → restart @49.741 (300ms off ✓) → stop @50.144, hepsi ACK. Ekran 2800px'de clip yok → T8 bu cihazda pass.
- [x] FitTest — **✅ 18:24**: `TX FF 05 11 03 01 01 01` start + 3.2s poll `FF 03 FE 01 11` + stop'lar; **firmware hiç result RX göndermedi (kulaklar kutuda) → Completed YOK, false-pass YOK** → kod yolu dürüst Failed("Test sonucu alınamadı") — eski battery-heuristic sahte geçişi yok ✓ (UI'da kullanıcı o an ekrandan çıktı, sonuç log+kod ile doğrulandı).
- [x] LiveTranslate — **✅ 18:27-18:30**: "Metin Yaz"→"Çevir & Oku" canlı ML Kit çevirisi ("Merhaba…Test" → "Hi…Test"); ekrandan **Geri (dispose)** → `files/translate_journal/journal.json` **468B yazıldı** (sessionId UUID + startedAt + item: original/translated/langs/timestamp/isFromHeadsetMic tam şema ✓); yeniden gir → Geçmiş sheet "02 Kas 18:27 · 1 kayıt" + metinler listelendi; **arama**: "DUNYA" büyük harf → eşleşti (case-insensitive ✓), "…xyzabc" → "Sonuç bulunamadı" ✓. Sheet başlığı/aralık/hızlı-arama kutusu render ✓.
- [x] English prompt susturma — **✅** her connect'te `TX FF 04 1D 02 00 00` + vol=0 echo'ları (17:45, 17:47, 18:10, 18:14, **18:24:52**)
- [x] NEGATİF TEST — **✅ 18:12:45**: Settings "Bağlantıyı Kes" (402,1193) → disconnect → **9 sn boyunca TEK satır log** (BatteryTracker reset), auto-reconnect satiri YOK → kullanici istegi link'i koparmak auto-reconnect'i tetiklemiyor (userRequestedDisconnect bayragi). Sonrasi Hizli Baglan (408,1070 → ana ekran basligi altinda) ile manuel 18:14:26 GATT Connected + prompt re-mute ✓.
- [x] ANC mod geçişi (takılı/re-aktif tekrar) — **✅ 18:31:42-53 (kullanıcı canlı kullandı)**: ANC switch → firmware anında `[01 05 00]` state echo (önceki turdaki [02 00 00] sessizliği aşıldı), **transparency level değişimleri `[03 01 03]`→`[03 01 06]` readback ✓**, `0x28 raw=1` ACK + verify query anında cevap; **18:31:46 `stale ANC echo ignored: mode=1,subScene=5`** — guard geçiş anında eski ANC yankısını bastırdı → orijinal "UI güncellenmiyor" senaryosu uçtan uca çözüldü ✓
- [x] Multipoint tekrar AÇ — **✅ 18:24 KULLANICI açtı**: `TX FF 03 24 01 01` → verify `[01]` ✓ → drop #4 (47.917) → **auto-reconnect 1/5 (49.920) → Connected 50.348** → tam bulk + prompt re-mute → 0x24=[01] ON persisted ✓ (kullanıcının orijinal durumu geri geldi).
- [x] Bonus: **notify-heal canlı** — 18:24:43 "No notifications for 80s despite probes → CCCD resubscribe ×4" → 18:24:55'ten itibaren bildirimler geri geldi (battery notify ✓).

## AUTO-RECONNECT SONUÇLARI (18:07-18:10) — ✅ ÇALIŞIYOR
- APK kurulumu ✓, relaunch sonrası otomatik bağlanma 18:07:00, Panel JSON cache hit 18:07:01.
- **0x24 write → link drop 3. kez üretildi (tutarlı):** 18:10:24 OFF toggle (`TX FF 03 24 01 02` ACK `Hardware ACK Multipoint Switch: false`) → **18:10:25.675 GATT status 21** (~0.5s sonra).
- **YENİ:** 18:10:27.677 `Unexpected disconnect - auto-reconnect attempt 1/5` (tam 2.0s delay) → 18:10:27.878 GATT Connected → MTU 220 → CCCD resubscribe ×4 → full bulk re-query → 18:10:30.167 prompt re-mute → tüm bean ACK'lar (0x17 [01 05 00], 0x09, 0x2C, 0x10, 0x16, 0x1D [00 0F], 0x22, 0x23, **0x24 [00] OFF persist**), 0x2B {7=0,8=0} + 000D aynı, 0x54/0x4D/0x4F sorguları gitti. L=99 R=80-82 (şarjda).
- **FE+0x24 sorgusu firmware tarafından cevaplanıyor:** `TX FF 03 FE 01 24` → `RX FF 03 24 01 01` (17:56, 18:01, 18:07, 18:09 poll'lerinde) → multipoint state readback çalışıyor.
- 18:09:01 toggle (muhtemelen KULLANICI): ACK + verify [01] desenkroni turu, DROP YOK (anomali — firmware bu tur link'i tuttu; kullanıcı telefonu aktif kullanıyor).
- NOT: multipoint artık OFF (test kapatmıştı) → test bitiminde ON'a geri dönecek.
- Uygulama navigasyonu: Home tools kartları 2 swipe (`input swipe 640 2400 640 900 400`) sonrası: Ekolayzer y1030, Dokunmatik Jest y1331, Uyum y1587, Bul y1888, **Multipoint y2144→tap(614,2172)**, Çeviri y2400.

## Bilinen firmware gerçekleri (test yorumlarken)
- 0x4D/0x4F telefon listesi sorgusuna bu firmware sessiz (eski log'da da yoktu) → listede "bulunamadı" beklenen davranış.
- Kutu pili: hep -1/0x00 (bilinen limit).
- Logcat Honor tarafından şifreleniyor (HKS) → sadece uygulama logu kullanılabilir.

## 2026-11-04 TURU — Görev 1–11 (release + çökme kök nedeni)
> Build: `:app:testDebugUnitTest` **95/95 (18 suite)** + `assembleDebug` + `assembleRelease` BUILD SUCCESSFUL. HEAD `c0c9b38` (ledger: 4be896d→cbe26e4→49cd5b0→fa936e2→a17e999→2a25b63→b5eadd2→5c0ce56→bc60622→c0c9b38).

### DropBox forensics — MagicOS'ta logcat yerine kaynak (YENİ yöntem)
- Logcat HKS ile şifreli → **`adb shell "dumpsys dropbox --print 2>/dev/null"` = tek güvenilir çökme kaynağı** (her `data_app_crash` entry'sinde tam stack + `Crash-Handler:` satırı görünür; `Process:` entry başına 2× tekrar eder — sayım yaparken entry başlığından say!).
- Örnek (2026-11-04 14:55-14:57, release v200): **6 identical crash** = `SecurityException: Need android.permission.BLUETOOTH_CONNECT … A2dpServiceBinder` → `BleConnectionManager$registerProfileProxyListeners$1.onServiceConnected(BleConnectionManager.kt:235)` (`a2dpProfile?.connectedDevices?.map{it.name}` log satırı, ana iş parçacığında). **Alıcının telefonundaki "hata veriyo" buydu** — taze kurulumda izin verilmemişken her açılışta çökerdi; yerel debug'da izin zaten verili olduğu için hiç üretilmedi.

### Çökme fix'i repro kanıtı (`c0c9b38`)
1. Fix öncesi: release taze kurulum → `am start` her seferinde ~200-300ms içinde process ölümü + launcher'a dönüş; DropBox 6 entry.
2. Fix sonrası (`hasBtConnectPermission()` guard: A2DP geri çağrısı + `systemBtReceiver.onReceive` try/catch + `connect()` giriş çıkışı): `adb uninstall` + taze `adb install app-release.apk` → **izin verilmemişken** 3× `am start -W` (Status ok, COLD) → `ps -A` her seferinde süreç ayakta (pid 9560/10605/10779), sonunda `mCurrentFocus = com.aegissoft.qcybutmod/.ui.MainActivity`, **DropBox entry sayısı 6'da sabit (yeni crash YOK)**.
3. İzin dialogu (`GrantPermissionsActivity`) açılıyor ve uygulama arkasında yaşamaya devam ediyor — eskiden dialog gelmeden çöküyordu.

### Release kurulumu (dağıtım durumu)
- `app\build\outputs\apk\release\app-release.apk` — 86.4MB, `apksigner verify` **v2 OK (CN=aegisSoft)**, badging: `com.aegissoft.qcybutmod` / vc200 / vn1.2.0 / `application-label:'QCYbutMOD'` / minSdk26.
- Cihazda: `adb uninstall com.aegissoft.qcybutmod.debug` → `adb install app-release.apk` → başlatma OK (debug ile yan yana KURULAMAZ, imza/id farklı).
- Kullanıcı senaryosu: alıcıdaki eski hatalı paylaşımlar (95MB v1.1.0 debug) yerine **yalnız release 1.2.0 dağıtılacak**; alıcıda önceden `.debug` varsa önce kaldırılır.

### Bu turda ADB ile doğrulananlar
- ✅ Release açılış stabilitesi izinsiz (yukarıdaki repro, 3/3).
- ✅ Release imza/badging/etiket (QCYbutMOD, 1.2.0).
- ✅ `git grep -i -E "aegis\s*qcy"` → 0 eşleşme (kullanıcıya dönük marka süpürmesi temiz).
- ⚠️ Bekleyen (kullanıcı etkileşimi): Google TTS Türkçe kadın ses paketi indirmesi (Ayarlar → Ses → "Ses paketini indir" kartı), fit test canlı koşusu (kulaklık takılı), multipoint 2. cihaz canlı testi, uzun basma atamasının kulakta işitilir doğrulaması.
