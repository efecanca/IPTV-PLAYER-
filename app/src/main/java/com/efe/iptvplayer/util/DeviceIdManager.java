package com.efe.iptvplayer.util;

import android.content.SharedPreferences;

import com.efe.iptvplayer.IPTVApp;

import java.util.UUID;

/**
 * Her cihaz için kalıcı, tekil bir kimlik üretir ve saklar. Bu ID, FPro
 * sunucusuna kayıt için kullanılır (MAC adresi Android 6+'ta erişilemez
 * olduğu için gerçek cihaz kilitleme burada UUID ile yapılıyor).
 */
public class DeviceIdManager {

    private static final String PREF_DEVICE_ID = "device_uuid";

    public static String getOrCreateDeviceId() {
        SharedPreferences prefs = IPTVApp.get().prefs();
        String existing = prefs.getString(PREF_DEVICE_ID, null);
        if (existing != null) return existing;

        String newId = UUID.randomUUID().toString();
        prefs.edit().putString(PREF_DEVICE_ID, newId).apply();
        return newId;
    }
}
