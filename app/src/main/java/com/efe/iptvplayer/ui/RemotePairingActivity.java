package com.efe.iptvplayer.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.efe.iptvplayer.IPTVApp;
import com.efe.iptvplayer.R;
import com.efe.iptvplayer.data.DevicePairingApi;
import com.efe.iptvplayer.data.M3UParser;
import com.efe.iptvplayer.data.XtreamRepository;
import com.efe.iptvplayer.model.Category;
import com.efe.iptvplayer.util.DeviceIdManager;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Ekranda bir eşleştirme kodu gösterir, FPro sunucusunu periyodik olarak
 * yoklar (polling). Admin panelden bu koda bir liste atandığında otomatik
 * olarak indirilip Anasayfa'ya geçilir. MAC adresi yerine kalıcı cihaz
 * UUID'si kullanılıyor (bkz. DeviceIdManager).
 */
public class RemotePairingActivity extends AppCompatActivity {

    private static final long POLL_INTERVAL_MS = 5000L;

    private TextView tvDeviceCode, tvStatus;
    private Button btnManualLogin;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final DevicePairingApi api = new DevicePairingApi();
    private String deviceId;
    private boolean stopped = false;   // liste atandı / kalıcı olarak durduruldu
    private boolean paused = false;    // activity arka planda, geçici olarak durduruldu

    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            pollOnce();
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_remote_pairing);

        tvDeviceCode = findViewById(R.id.tvDeviceCode);
        tvStatus = findViewById(R.id.tvStatus);
        btnManualLogin = findViewById(R.id.btnManualLogin);

        btnManualLogin.setOnClickListener(v -> finish());

        deviceId = DeviceIdManager.getOrCreateDeviceId();
        registerDevice();
    }

    private void registerDevice() {
        setStatus("Sunucuya bağlanılıyor...");
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                DevicePairingApi.RegisterResult result = api.register(deviceId);
                runOnUiThread(() -> {
                    tvDeviceCode.setText(result.code.isEmpty() ? "------" : result.code);
                    setStatus("Bekleniyor...");
                });
                if (result.config != null) {
                    applyConfig(result.config);
                } else {
                    handler.postDelayed(pollRunnable, POLL_INTERVAL_MS);
                }
            } catch (Exception e) {
                setStatus("Bağlantı hatası: " + e.getMessage() + " — yeniden deneniyor...");
                if (!stopped && !paused) {
                    handler.postDelayed(this::registerDevice, 8000);
                }
            }
        });
    }

    private void pollOnce() {
        // Ekran arka plandayken (kullanıcı ana ekrana dönmüş vs.) sorgulamayı
        // tamamen durdur — AGTARAMA'daki "arka planda sürekli tarama" pil
        // sorununun aynısına burada da düşmemek için. onResume tekrar başlatır.
        if (stopped || paused) return;
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                JSONObject config = api.pollConfig(deviceId);
                if (stopped || paused) return; // yanıt gelene kadar arka plana geçmiş olabilir
                if (config != null) {
                    applyConfig(config);
                } else {
                    handler.postDelayed(pollRunnable, POLL_INTERVAL_MS);
                }
            } catch (Exception e) {
                if (stopped || paused) return;
                // Ağ hatası olursa sessizce tekrar dener, kullanıcıyı rahatsız etmesin.
                handler.postDelayed(pollRunnable, POLL_INTERVAL_MS);
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        paused = true;
        handler.removeCallbacks(pollRunnable);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (paused && !stopped) {
            paused = false;
            // Ekrana dönünce yoklamayı kaldığı yerden devam ettir.
            handler.post(pollRunnable);
        }
    }

    private void applyConfig(JSONObject config) {
        setStatus("Liste bulundu, yükleniyor...");
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String type = config.optString("type", "");
                if ("xtream".equals(type)) {
                    String host = config.getString("host");
                    String user = config.getString("username");
                    String pass = config.getString("password");

                    XtreamRepository repo = new XtreamRepository(host, user, pass);
                    List<Category> live = repo.getLiveCategories();
                    List<Category> vod = repo.getVodCategories();
                    IPTVApp.get().setLiveCategories(live);
                    IPTVApp.get().setVodCategories(vod);

                    IPTVApp.get().prefs().edit()
                            .putString("source_type", "xtream")
                            .putString("host", host)
                            .putString("user", user)
                            .putString("pass", pass)
                            .apply();

                } else if ("m3u".equals(type)) {
                    String url = config.getString("url");
                    M3UParser parser = new M3UParser();
                    Map<String, Category> parsed = parser.parseFromUrl(url);
                    IPTVApp.get().setLiveCategories(new ArrayList<>(parsed.values()));
                    IPTVApp.get().setVodCategories(new ArrayList<>());

                    IPTVApp.get().prefs().edit()
                            .putString("source_type", "m3u")
                            .putString("m3u_url", url)
                            .apply();
                } else {
                    setStatus("Sunucudan bilinmeyen liste tipi geldi.");
                    handler.postDelayed(pollRunnable, POLL_INTERVAL_MS);
                    return;
                }

                stopped = true;
                runOnUiThread(() -> {
                    startActivity(new Intent(this, HomeActivity.class));
                    finish();
                });
            } catch (Exception e) {
                setStatus("Liste yüklenemedi: " + e.getMessage() + " — tekrar deneniyor...");
                handler.postDelayed(pollRunnable, POLL_INTERVAL_MS);
            }
        });
    }

    private void setStatus(String text) {
        runOnUiThread(() -> tvStatus.setText(text));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopped = true;
        handler.removeCallbacksAndMessages(null);
    }
}
