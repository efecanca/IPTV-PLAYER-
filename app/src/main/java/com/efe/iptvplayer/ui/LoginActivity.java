package com.efe.iptvplayer.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.efe.iptvplayer.IPTVApp;
import com.efe.iptvplayer.R;
import com.efe.iptvplayer.data.M3UParser;
import com.efe.iptvplayer.data.XtreamRepository;
import com.efe.iptvplayer.model.Category;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Kullanıcı Xtream Codes (host/kullanıcı/şifre) ya da düz M3U linki girer.
 * Bilgiler SharedPreferences'a kaydedilir, bir sonraki açılışta otomatik
 * bağlanılır (tekrar tekrar sorulmaz).
 */
public class LoginActivity extends AppCompatActivity {

    private static final String PREF_SOURCE_TYPE = "source_type"; // "xtream" | "m3u"
    private static final String PREF_HOST = "host";
    private static final String PREF_USER = "user";
    private static final String PREF_PASS = "pass";
    private static final String PREF_M3U_URL = "m3u_url";

    private RadioGroup rgSourceType;
    private LinearLayout layoutXtream, layoutM3U;
    private EditText etHost, etUsername, etPassword, etM3UUrl;
    private Button btnConnect;
    private ProgressBar progressBar;
    private TextView tvError;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        rgSourceType = findViewById(R.id.rgSourceType);
        layoutXtream = findViewById(R.id.layoutXtream);
        layoutM3U = findViewById(R.id.layoutM3U);
        etHost = findViewById(R.id.etHost);
        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        etM3UUrl = findViewById(R.id.etM3UUrl);
        btnConnect = findViewById(R.id.btnConnect);
        progressBar = findViewById(R.id.progressBar);
        tvError = findViewById(R.id.tvError);

        Button btnRemotePairing = findViewById(R.id.btnRemotePairing);
        btnRemotePairing.setOnClickListener(v ->
                startActivity(new Intent(this, RemotePairingActivity.class)));

        rgSourceType.setOnCheckedChangeListener((group, checkedId) -> {
            boolean isXtream = checkedId == R.id.rbXtream;
            layoutXtream.setVisibility(isXtream ? View.VISIBLE : View.GONE);
            layoutM3U.setVisibility(isXtream ? View.GONE : View.VISIBLE);
        });

        btnConnect.setOnClickListener(v -> attemptConnect());

        prefillSavedCredentials();
    }

    private void prefillSavedCredentials() {
        SharedPreferences prefs = IPTVApp.get().prefs();
        String type = prefs.getString(PREF_SOURCE_TYPE, "xtream");
        if ("m3u".equals(type)) {
            rgSourceType.check(R.id.rbM3U);
            etM3UUrl.setText(prefs.getString(PREF_M3U_URL, ""));
        } else {
            etHost.setText(prefs.getString(PREF_HOST, ""));
            etUsername.setText(prefs.getString(PREF_USER, ""));
            etPassword.setText(prefs.getString(PREF_PASS, ""));
        }
    }

    private void attemptConnect() {
        tvError.setText("");
        boolean isXtream = rgSourceType.getCheckedRadioButtonId() == R.id.rbXtream;
        setLoading(true);

        if (isXtream) {
            String host = etHost.getText().toString().trim();
            String user = etUsername.getText().toString().trim();
            String pass = etPassword.getText().toString().trim();
            if (host.isEmpty() || user.isEmpty() || pass.isEmpty()) {
                showError("Tüm alanları doldurun.");
                return;
            }
            executor.execute(() -> {
                try {
                    XtreamRepository repo = new XtreamRepository(host, user, pass);
                    List<Category> live = repo.getLiveCategories();
                    List<Category> vod = repo.getVodCategories();
                    if (live.isEmpty() && vod.isEmpty()) {
                        showError("Hiç içerik bulunamadı, bilgileri kontrol edin.");
                        return;
                    }
                    IPTVApp.get().setLiveCategories(live);
                    IPTVApp.get().setVodCategories(vod);

                    IPTVApp.get().prefs().edit()
                            .putString(PREF_SOURCE_TYPE, "xtream")
                            .putString(PREF_HOST, host)
                            .putString(PREF_USER, user)
                            .putString(PREF_PASS, pass)
                            .apply();

                    goHome();
                } catch (Exception e) {
                    showError("Bağlantı hatası: " + e.getMessage());
                }
            });
        } else {
            String url = etM3UUrl.getText().toString().trim();
            if (url.isEmpty()) {
                showError("M3U linki girin.");
                return;
            }
            executor.execute(() -> {
                try {
                    M3UParser parser = new M3UParser();
                    Map<String, Category> parsed = parser.parseFromUrl(url);
                    if (parsed.isEmpty()) {
                        showError("Playlist boş ya da geçersiz.");
                        return;
                    }
                    IPTVApp.get().setLiveCategories(new ArrayList<>(parsed.values()));
                    IPTVApp.get().setVodCategories(new ArrayList<>());

                    IPTVApp.get().prefs().edit()
                            .putString(PREF_SOURCE_TYPE, "m3u")
                            .putString(PREF_M3U_URL, url)
                            .apply();

                    goHome();
                } catch (Exception e) {
                    showError("Playlist indirilemedi: " + e.getMessage());
                }
            });
        }
    }

    private void goHome() {
        runOnUiThread(() -> {
            setLoading(false);
            startActivity(new Intent(this, HomeActivity.class));
            finish();
        });
    }

    private void showError(String msg) {
        runOnUiThread(() -> {
            setLoading(false);
            tvError.setText(msg);
        });
    }

    private void setLoading(boolean loading) {
        runOnUiThread(() -> {
            progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
            btnConnect.setEnabled(!loading);
        });
    }
}
