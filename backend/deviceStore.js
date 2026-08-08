// deviceStore.js
// FPro sunucusuna eklenecek modül. Mevcut health.json atomic-write pattern'i
// ile aynı yaklaşım kullanılıyor (tmp dosyaya yaz + rename) — eşzamanlı
// isteklerde veri bozulmasını önler.
//
// Depolanan veri (device_store.json):
// {
//   "devices": {
//     "<deviceId-uuid>": {
//       "code": "8X4K2P",
//       "codeExpiresAt": 1234567890000,   // kod claim edilmeden önceki son geçerlilik
//       "config": null | { "type": "xtream"|"m3u", "host":..., "username":..., "password":..., "url":... },
//       "createdAt": 1234567890000,
//       "lastPolledAt": 1234567890000
//     }
//   },
//   "codeIndex": { "8X4K2P": "<deviceId-uuid>" }
// }

const fs = require('fs');
const path = require('path');

const STORE_PATH = path.join(__dirname, 'device_store.json');
const CODE_TTL_MS = 15 * 60 * 1000; // eşleştirilmemiş kod 15 dakika sonra geçersiz olur
const CODE_CHARS = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'; // karışabilecek karakterler (0/O, 1/I) çıkarıldı

function loadStore() {
    if (!fs.existsSync(STORE_PATH)) {
        return { devices: {}, codeIndex: {} };
    }
    try {
        return JSON.parse(fs.readFileSync(STORE_PATH, 'utf8'));
    } catch (e) {
        console.error('[deviceStore] Bozuk dosya, sıfırdan başlatılıyor:', e.message);
        return { devices: {}, codeIndex: {} };
    }
}

function saveStore(store) {
    const tmpPath = STORE_PATH + '.tmp';
    fs.writeFileSync(tmpPath, JSON.stringify(store, null, 2));
    fs.renameSync(tmpPath, STORE_PATH); // atomic - yarım yazılmış dosya riski yok
}

function generateCode(store) {
    let code;
    do {
        code = Array.from({ length: 6 }, () =>
            CODE_CHARS[Math.floor(Math.random() * CODE_CHARS.length)]
        ).join('');
    } while (store.codeIndex[code]); // çakışma ihtimaline karşı
    return code;
}

/** Cihaz ilk açılışta çağırır. Zaten kayıtlıysa mevcut kaydı/config'i döner. */
function registerDevice(deviceId) {
    const store = loadStore();
    const now = Date.now();

    let device = store.devices[deviceId];
    if (device && device.config) {
        // Zaten bir liste atanmış, kodu tekrar üretmeye gerek yok.
        return { code: device.code, config: device.config };
    }

    if (device && device.codeExpiresAt > now) {
        // Kod hâlâ geçerli, aynısını dön (kullanıcı ekranı tekrar açtıysa kod değişmesin).
        return { code: device.code, config: null };
    }

    const code = generateCode(store);
    device = {
        code,
        codeExpiresAt: now + CODE_TTL_MS,
        config: null,
        createdAt: device ? device.createdAt : now,
        lastPolledAt: now,
    };
    store.devices[deviceId] = device;
    store.codeIndex[code] = deviceId;
    saveStore(store);
    return { code, config: null };
}

/** Cihaz periyodik olarak bunu çağırır (polling). */
function getConfig(deviceId) {
    const store = loadStore();
    const device = store.devices[deviceId];
    if (!device) return null;
    device.lastPolledAt = Date.now();
    saveStore(store);
    return device.config;
}

/** Admin panelinden çağrılır: kısa kodu, girilen playlist config'ine bağlar. */
function assignConfigByCode(code, config) {
    const store = loadStore();
    const deviceId = store.codeIndex[code.toUpperCase()];
    if (!deviceId) {
        return { success: false, reason: 'Kod bulunamadı veya süresi dolmuş' };
    }
    const device = store.devices[deviceId];
    if (!device) {
        return { success: false, reason: 'Cihaz kaydı bulunamadı' };
    }
    device.config = config;
    saveStore(store);
    return { success: true, deviceId };
}

/** Admin panelinde bekleyen (henüz eşleşmemiş) cihazları listeler. */
function listPendingDevices() {
    const store = loadStore();
    const now = Date.now();
    return Object.entries(store.devices)
        .filter(([, d]) => !d.config && d.codeExpiresAt > now)
        .map(([deviceId, d]) => ({ deviceId, code: d.code, createdAt: d.createdAt }));
}

module.exports = { registerDevice, getConfig, assignConfigByCode, listPendingDevices };
