# IPTV Player Pro

Android için gelişmiş IPTV oynatıcı. M3U ve Xtream Codes ikisini de destekler,
ExoPlayer/VLC arasında geçiş yapılabilir, kaldığın yerden devam eder, pil dostu
çalışır.

## Özellikler
- **M3U / M3U8 link** veya **Xtream Codes** (host + kullanıcı adı + şifre) ile giriş
- **Anasayfa**: Kaldığın Yerden Devam Et, Yeni Eklenenler, kategori bazlı poster satırları
- **Kaldığın yerden devam et**: Room veritabanı ile her içeriğin izleme pozisyonu saklanır
- **10 saniye ileri/geri sarma**, durdur/izle, sürükle-bırak seek bar
- **Çift oynatma motoru**: ExoPlayer (varsayılan, pil dostu) ↔ VLC (bozuk/özel codec akışlar için)
- **Donma/kasma koruması**: pozisyon watchdog'u + otomatik yeniden bağlanma (exponential backoff)
- **Pil optimizasyonu**: uygulama arka plana geçtiğinde oynatıcı tamamen serbest bırakılır,
  ekran sadece oynatım sırasında açık tutulur (FLAG_KEEP_SCREEN_ON), dengeli buffer ayarları

## Kurulum (Android Studio)
1. Bu klasörü Android Studio ile açın (`File > Open`).
2. Gradle senkronizasyonunun bitmesini bekleyin (ilk seferde ExoPlayer/VLC/Room bağımlılıkları inecek).
3. `Run` ile bir cihaz veya emülatöre kurun (minSdk 23 / Android 6.0+).

## Proje Yapısı
```
app/src/main/java/com/efe/iptvplayer/
  ui/LoginActivity.java      → M3U / Xtream giriş ekranı
  ui/HomeActivity.java       → Anasayfa (posterler, satırlar, arama)
  ui/PlayerActivity.java     → Video oynatıcı (ExoPlayer + VLC)
  data/XtreamRepository.java → Xtream Codes API istemcisi
  data/M3UParser.java        → M3U/M3U8 ayrıştırıcı
  data/AppDatabase.java      → Room DB (kaldığın yerden devam kayıtları)
  model/                     → MediaItem, Category, HomeRow
  adapter/                   → RecyclerView adaptörleri (poster grid, satırlar)
```

## Notlar / Yapılacaklar
- Uygulama ikonu şu an basit bir placeholder vektör; gerçek logo ile değiştirin
  (`res/drawable/ic_launcher_foreground.xml` ve `ic_launcher_background.xml`).
- Xtream girişinde diziler (`get_series`) için ekstra bir ekran eklenmedi; VOD (film)
  ve canlı yayın tam entegre. İstersen dizi bölüm listesi ekranını da ekleyebilirim.
- EPG (yayın akışı / program rehberi) şu an entegre değil; istenirse `get_short_epg`
  uçlarıyla eklenebilir.
- Gerçek cihazda test ederken ilk açılışta VLC native kütüphaneleri (~30MB) APK boyutuna
  eklenir; APK boyutunu küçültmek istersen `libvlc-all` yerine sadece ihtiyacın olan
  ABI'ları içeren VLC varyantı kullanılabilir.
