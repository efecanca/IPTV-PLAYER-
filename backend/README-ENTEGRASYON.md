# IPTV Player - Uzaktan Liste Yükleme (Cihaz Kodu) - FPro Backend Entegrasyonu

## Kurulum
1. `deviceStore.js` ve `deviceRoutes.js` dosyalarını FPro sunucu kök dizinine kopyala
   (health.json'un olduğu dizinle aynı yere koyman en tutarlısı).
2. `server.js` içine ekle:
   ```js
   const deviceRoutes = require('./deviceRoutes');
   app.use('/api/device', deviceRoutes);
   ```
3. `.env` dosyana admin panel isteklerini korumak için bir token ekle:
   ```
   FPRO_ADMIN_TOKEN=uzun-rastgele-bir-deger
   ```
   (İstersen bunun yerine `requireAdminToken` middleware'ini FPro'nun zaten
   kullandığı oturum/JWT auth'una bağlayabilirsin — `deviceRoutes.js` içinde
   TODO olarak işaretledim.)
4. PM2 restart: `pm2 restart fpro` (ya da hangi isimle çalıştırıyorsan).

## Akış
1. Kullanıcı IPTV Player'ı açar → "Uzaktan Yükle" seçeneğini seçer.
2. Uygulama `POST /api/device/register` ile kendi UUID'sini gönderir, sunucu
   6 haneli bir kod üretip döner (örn. `8X4K2P`). Uygulama bu kodu ekranda
   büyük puntoyla gösterir ve arka planda 5 saniyede bir
   `GET /api/device/:deviceId/config` ile "bana liste atandı mı?" diye sorar.
3. Sen (admin) FPro panelinden `POST /api/device/admin/assign` çağırısını
   yaparsın (ya da paneline küçük bir form eklersin):
   ```json
   { "code": "8X4K2P", "config": { "type": "xtream", "host": "http://panel.com:8080", "username": "...", "password": "..." } }
   ```
   ya da M3U için:
   ```json
   { "code": "8X4K2P", "config": { "type": "m3u", "url": "http://.../playlist.m3u8" } }
   ```
4. Cihaz bir sonraki polling'de config'i alır, otomatik giriş yapar ve
   Anasayfa'ya geçer. Kod bir daha gösterilmez (cihaz artık config'ini
   yerelde SharedPreferences'a kaydeder, tıpkı manuel girişte olduğu gibi).

## Admin panel için hızlı test (curl)
```bash
# Bekleyen cihazları gör
curl -H "x-admin-token: TOKENIN" https://fpro.com.tr/api/device/admin/pending

# Kod ile liste ata
curl -X POST https://fpro.com.tr/api/device/admin/assign \
  -H "Content-Type: application/json" \
  -H "x-admin-token: TOKENIN" \
  -d '{"code":"8X4K2P","config":{"type":"xtream","host":"http://panel.com:8080","username":"demo","password":"demo123"}}'
```

## Notlar
- Kod, eşleştirme yapılmadan 15 dakika içinde geçersiz olur (`CODE_TTL_MS`).
  Süre dolarsa uygulama yeni bir kod ister.
- Depolama basit JSON dosya (`device_store.json`), health.json ile aynı
  atomic-write pattern'i kullanıyor. Cihaz sayısı çok artarsa (binlerce)
  SQLite'a taşımak daha sağlıklı olur — istersen o migrasyonu da yazarım.
- `admin/pending` ve `admin/assign` uçlarını FPro'nun web panel arayüzüne
  küçük bir sayfa olarak eklemek istersen (kod gir, dropdown'dan müşteri seç,
  ata) o ekranı da hazırlayabilirim.
