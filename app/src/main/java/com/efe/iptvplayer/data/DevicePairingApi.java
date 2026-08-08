package com.efe.iptvplayer.data;

import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * FPro sunucusundaki cihaz eşleştirme uçlarına (deviceRoutes.js) bağlanır.
 * Sunucu adresini kendi FPro domain'inle değiştir.
 */
public class DevicePairingApi {

    // TODO: Gerekirse kendi FPro alan adınla / portunla güncelle.
    private static final String BASE_URL = "https://fpro.com.tr/api/device";

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();

    public static class RegisterResult {
        public String code;
        public JSONObject config; // null ise henüz atanmamış
    }

    /** Cihazı sunucuya kaydeder, eşleştirme kodu (ör. 8X4K2P) döner. */
    public RegisterResult register(String deviceId) throws Exception {
        JSONObject body = new JSONObject();
        body.put("deviceId", deviceId);

        Request req = new Request.Builder()
                .url(BASE_URL + "/register")
                .post(RequestBody.create(body.toString(), MediaType.parse("application/json")))
                .build();

        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful() || resp.body() == null) {
                throw new Exception("Sunucu hatası: " + resp.code());
            }
            JSONObject json = new JSONObject(resp.body().string());
            RegisterResult result = new RegisterResult();
            result.code = json.optString("code", "");
            result.config = json.optJSONObject("config");
            return result;
        }
    }

    /** Config atanmış mı diye sorgular (polling). null = henüz atanmadı. */
    public JSONObject pollConfig(String deviceId) throws Exception {
        Request req = new Request.Builder()
                .url(BASE_URL + "/" + deviceId + "/config")
                .get()
                .build();

        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful() || resp.body() == null) {
                throw new Exception("Sunucu hatası: " + resp.code());
            }
            JSONObject json = new JSONObject(resp.body().string());
            if (!json.optBoolean("assigned", false)) return null;
            return json.optJSONObject("config");
        }
    }
}
