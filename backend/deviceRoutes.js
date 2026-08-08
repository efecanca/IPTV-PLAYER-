// deviceRoutes.js
// server.js içine şu şekilde bağlanır:
//   const deviceRoutes = require('./deviceRoutes');
//   app.use('/api/device', deviceRoutes);
//
// Admin uçları (assign, list) mevcut admin auth middleware'inle korunmalı.
// Aşağıda basit bir `requireAdminToken` örneği var — kendi auth sistemine
// göre bunu mevcut middleware'inle değiştir.

const express = require('express');
const router = express.Router();
const deviceStore = require('./deviceStore');

// TODO: Bunu FPro'nun mevcut admin auth middleware'i ile değiştir.
function requireAdminToken(req, res, next) {
    const token = req.headers['x-admin-token'];
    if (!token || token !== process.env.FPRO_ADMIN_TOKEN) {
        return res.status(401).json({ error: 'Yetkisiz' });
    }
    next();
}

// --- Cihaz tarafı (Android uygulaması çağırır) ---

// Cihaz ilk açılışta çağırır: kısa eşleştirme kodu alır.
router.post('/register', (req, res) => {
    const { deviceId } = req.body;
    if (!deviceId || typeof deviceId !== 'string' || deviceId.length < 8) {
        return res.status(400).json({ error: 'Geçersiz deviceId' });
    }
    const result = deviceStore.registerDevice(deviceId);
    res.json(result); // { code, config }
});

// Cihaz periyodik olarak (örn. 5 saniyede bir) config atanmış mı diye sorar.
router.get('/:deviceId/config', (req, res) => {
    const config = deviceStore.getConfig(req.params.deviceId);
    if (!config) {
        return res.json({ assigned: false });
    }
    res.json({ assigned: true, config });
});

// --- Admin tarafı (FPro web paneline eklenecek) ---

// Bekleyen (henüz liste atanmamış) cihazları listeler.
router.get('/admin/pending', requireAdminToken, (req, res) => {
    res.json(deviceStore.listPendingDevices());
});

// Bir koda playlist config'i atar.
// body: { code: "8X4K2P", config: { type: "xtream", host, username, password } }
//    ya da: { type: "m3u", url }
router.post('/admin/assign', requireAdminToken, (req, res) => {
    const { code, config } = req.body;
    if (!code || !config || !config.type) {
        return res.status(400).json({ error: 'code ve config.type zorunlu' });
    }
    if (config.type === 'xtream' && (!config.host || !config.username || !config.password)) {
        return res.status(400).json({ error: 'Xtream için host/username/password zorunlu' });
    }
    if (config.type === 'm3u' && !config.url) {
        return res.status(400).json({ error: 'M3U için url zorunlu' });
    }
    const result = deviceStore.assignConfigByCode(code, config);
    if (!result.success) {
        return res.status(404).json(result);
    }
    res.json(result);
});

module.exports = router;
