package com.efe.iptvplayer.ui;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import com.efe.iptvplayer.R;
import com.efe.iptvplayer.data.AppDatabase;
import com.efe.iptvplayer.data.WatchProgress;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.util.VLCVideoLayout;

import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.Executors;

/**
 * Video oynatma ekranı.
 *
 * PİL / PERFORMANS NOTLARI:
 *  - Ekran sadece oynatım sırasında FLAG_KEEP_SCREEN_ON ile açık tutulur; durdurulduğunda kaldırılır.
 *  - Activity arka plana geçtiğinde (onPause) oynatıcı tamamen serbest bırakılır (release),
 *    böylece arkada gereksiz decode/network işi devam etmez.
 *  - ExoPlayer'ın DefaultLoadControl'ü canlı yayın/VOD için dengeli buffer aralıklarıyla
 *    ayarlanır: çok küçük buffer donmaya, çok büyük buffer gereksiz pil/RAM tüketimine yol açar.
 *  - "Donma/kasma" koruması: pozisyon belirli aralıklarla kontrol edilir (watchdog), eğer
 *    oynatım durumunda pozisyon ilerlemiyorsa otomatik olarak stream yeniden hazırlanır (retry).
 *  - Ağ hatalarında üstel geri çekilme (exponential backoff) ile otomatik yeniden deneme yapılır.
 */
@OptIn(markerClass = UnstableApi.class)
public class PlayerActivity extends AppCompatActivity {

    public static final String EXTRA_NAME = "extra_name";
    public static final String EXTRA_URL = "extra_url";
    public static final String EXTRA_POSTER = "extra_poster";

    private static final long SKIP_MS = 10_000L;
    private static final long WATCHDOG_INTERVAL_MS = 4_000L;
    private static final long SAVE_PROGRESS_INTERVAL_MS = 5_000L;
    private static final int MAX_RETRY = 5;

    private PlayerView exoPlayerView;
    private VLCVideoLayout vlcVideoLayout;
    private ProgressBar bufferingSpinner;
    private TextView tvErrorOverlay, tvChannelName, tvPosition, tvDuration;
    private LinearLayout controlsOverlay;
    private ImageButton btnPlayPause, btnRewind, btnForward;
    private Button btnSwitchEngine;
    private SeekBar seekBar;

    private ExoPlayer exoPlayer;
    private LibVLC libVLC;
    private org.videolan.libvlc.MediaPlayer vlcPlayer;
    private boolean usingVlc = false;

    private String channelName, streamUrl, posterUrl;
    private String progressKey;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private long lastKnownPosition = -1;
    private int retryCount = 0;
    private boolean userTouchingSeek = false;

    private final Runnable watchdogRunnable = new Runnable() {
        @Override
        public void run() {
            checkForFreeze();
            handler.postDelayed(this, WATCHDOG_INTERVAL_MS);
        }
    };

    private final Runnable progressSaver = new Runnable() {
        @Override
        public void run() {
            saveProgress();
            handler.postDelayed(this, SAVE_PROGRESS_INTERVAL_MS);
        }
    };

    private final Runnable uiTicker = new Runnable() {
        @Override
        public void run() {
            updateSeekUi();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        channelName = getIntent().getStringExtra(EXTRA_NAME);
        streamUrl = getIntent().getStringExtra(EXTRA_URL);
        posterUrl = getIntent().getStringExtra(EXTRA_POSTER);
        progressKey = streamUrl;

        bindViews();
        setupControls();

        startExoPlayer(getResumePositionMs());

        controlsOverlay.setVisibility(View.VISIBLE);
        handler.postDelayed(() -> controlsOverlay.setVisibility(View.GONE), 4000);
    }

    private void bindViews() {
        exoPlayerView = findViewById(R.id.exoPlayerView);
        vlcVideoLayout = findViewById(R.id.vlcVideoLayout);
        bufferingSpinner = findViewById(R.id.bufferingSpinner);
        tvErrorOverlay = findViewById(R.id.tvErrorOverlay);
        controlsOverlay = findViewById(R.id.controlsOverlay);
        tvChannelName = findViewById(R.id.tvChannelName);
        tvPosition = findViewById(R.id.tvPosition);
        tvDuration = findViewById(R.id.tvDuration);
        btnPlayPause = findViewById(R.id.btnPlayPause);
        btnRewind = findViewById(R.id.btnRewind);
        btnForward = findViewById(R.id.btnForward);
        btnSwitchEngine = findViewById(R.id.btnSwitchEngine);
        seekBar = findViewById(R.id.seekBar);

        tvChannelName.setText(channelName);
    }

    private void setupControls() {
        exoPlayerView.setOnClickListener(v -> toggleControlsOverlay());
        vlcVideoLayout.setOnClickListener(v -> toggleControlsOverlay());

        btnPlayPause.setOnClickListener(v -> togglePlayPause());
        btnRewind.setOnClickListener(v -> seekRelative(-SKIP_MS));
        btnForward.setOnClickListener(v -> seekRelative(SKIP_MS));
        btnSwitchEngine.setOnClickListener(v -> switchEngine());

        // Maksimum deneme sayısı aşıldığında kullanıcı hata mesajına dokunarak
        // deneme sayacını sıfırlayıp tekrar bağlanmayı deneyebilir.
        tvErrorOverlay.setOnClickListener(v -> {
            retryCount = 0;
            retryPlayback("Yeniden deneniyor...");
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {}

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { userTouchingSeek = true; }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                userTouchingSeek = false;
                seekTo(seekBar.getProgress() * 1000L);
            }
        });
    }

    private void toggleControlsOverlay() {
        controlsOverlay.setVisibility(
                controlsOverlay.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
    }

    // ---------------------------------------------------------------
    // ExoPlayer motoru
    // ---------------------------------------------------------------

    private void startExoPlayer(long resumeMs) {
        releaseVlc();
        usingVlc = false;
        btnSwitchEngine.setText("Motor: ExoPlayer");
        exoPlayerView.setVisibility(View.VISIBLE);
        vlcVideoLayout.setVisibility(View.GONE);

        // Canlı yayın/VOD için dengeli buffer: donmayı azaltırken pil/RAM israfını önler.
        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                        15_000,   // min buffer
                        50_000,   // max buffer
                        2_500,    // oynatmaya başlamak için gereken buffer
                        5_000)    // yeniden buffer sonrası oynatmaya devam için gereken süre
                .build();

        exoPlayer = new ExoPlayer.Builder(this)
                .setLoadControl(loadControl)
                .build();
        exoPlayerView.setPlayer(exoPlayer);

        exoPlayer.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                bufferingSpinner.setVisibility(state == Player.STATE_BUFFERING ? View.VISIBLE : View.GONE);
                if (state == Player.STATE_READY) {
                    hideError();
                    retryCount = 0;
                    updateDurationUi();
                }
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                updatePlayPauseIcon(isPlaying);
                getWindow().setFlags(
                        isPlaying ? WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON : 0,
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                handlePlaybackError(error.getMessage());
            }
        });

        MediaItem mediaItem = MediaItem.fromUri(Uri.parse(streamUrl));
        exoPlayer.setMediaItem(mediaItem);
        exoPlayer.prepare();
        if (resumeMs > 0) exoPlayer.seekTo(resumeMs);
        exoPlayer.setPlayWhenReady(true);

        startLoops();
    }

    // ---------------------------------------------------------------
    // VLC motoru (alternatif - ExoPlayer'ın oynatamadığı özel akışlar için)
    // ---------------------------------------------------------------

    private void startVlcPlayer(long resumeMs) {
        releaseExo();
        usingVlc = true;
        btnSwitchEngine.setText("Motor: VLC");
        exoPlayerView.setVisibility(View.GONE);
        vlcVideoLayout.setVisibility(View.VISIBLE);

        ArrayList<String> options = new ArrayList<>();
        options.add("--network-caching=3000"); // pil/veri dengesi için orta seviye ağ önbelleği
        options.add("--no-drop-late-frames");
        options.add("--no-skip-frames");
        libVLC = new LibVLC(this, options);
        vlcPlayer = new org.videolan.libvlc.MediaPlayer(libVLC);
        vlcPlayer.attachViews(vlcVideoLayout, null, false, false);

        Media media = new Media(libVLC, Uri.parse(streamUrl));
        vlcPlayer.setMedia(media);
        media.release();

        vlcPlayer.setEventListener(event -> {
            switch (event.type) {
                case org.videolan.libvlc.MediaPlayer.Event.Playing:
                    bufferingSpinner.setVisibility(View.GONE);
                    hideError();
                    retryCount = 0;
                    updatePlayPauseIcon(true);
                    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    if (resumeMsPending > 0) {
                        vlcPlayer.setTime(resumeMsPending);
                        resumeMsPending = -1;
                    }
                    break;
                case org.videolan.libvlc.MediaPlayer.Event.Paused:
                    updatePlayPauseIcon(false);
                    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    break;
                case org.videolan.libvlc.MediaPlayer.Event.Buffering:
                    bufferingSpinner.setVisibility(View.VISIBLE);
                    break;
                case org.videolan.libvlc.MediaPlayer.Event.EncounteredError:
                    handlePlaybackError("VLC oynatma hatası");
                    break;
            }
        });

        resumeMsPending = resumeMs;
        vlcPlayer.play();
        startLoops();
    }

    private long resumeMsPending = -1;

    private void switchEngine() {
        long currentPos = getCurrentPositionMs();
        if (usingVlc) {
            startExoPlayer(currentPos);
        } else {
            startVlcPlayer(currentPos);
        }
    }

    // ---------------------------------------------------------------
    // Ortak kontroller (play/pause/seek) - hangi motor aktifse ona uygulanır
    // ---------------------------------------------------------------

    private void togglePlayPause() {
        if (usingVlc && vlcPlayer != null) {
            if (vlcPlayer.isPlaying()) vlcPlayer.pause(); else vlcPlayer.play();
        } else if (exoPlayer != null) {
            exoPlayer.setPlayWhenReady(!exoPlayer.getPlayWhenReady());
        }
    }

    private void seekRelative(long deltaMs) {
        seekTo(getCurrentPositionMs() + deltaMs);
    }

    private void seekTo(long positionMs) {
        long clamped = Math.max(0, positionMs);
        if (usingVlc && vlcPlayer != null) {
            vlcPlayer.setTime(clamped);
        } else if (exoPlayer != null) {
            exoPlayer.seekTo(clamped);
        }
    }

    private long getCurrentPositionMs() {
        if (usingVlc && vlcPlayer != null) return vlcPlayer.getTime();
        if (exoPlayer != null) return exoPlayer.getCurrentPosition();
        return 0;
    }

    private long getDurationMs() {
        if (usingVlc && vlcPlayer != null) return vlcPlayer.getLength();
        if (exoPlayer != null) return exoPlayer.getDuration();
        return 0;
    }

    private boolean isPlaying() {
        if (usingVlc && vlcPlayer != null) return vlcPlayer.isPlaying();
        if (exoPlayer != null) return exoPlayer.isPlaying();
        return false;
    }

    private void updatePlayPauseIcon(boolean playing) {
        btnPlayPause.setImageResource(playing
                ? android.R.drawable.ic_media_pause
                : android.R.drawable.ic_media_play);
    }

    // ---------------------------------------------------------------
    // Donma / kasma koruması + otomatik yeniden bağlanma
    // ---------------------------------------------------------------

    private void checkForFreeze() {
        if (!isPlaying()) return;
        long pos = getCurrentPositionMs();
        if (lastKnownPosition >= 0 && pos == lastKnownPosition) {
            // Oynatım "playing" diyor ama pozisyon ilerlemiyor -> donma tespit edildi.
            retryPlayback("Yayın takıldı, yeniden bağlanılıyor...");
        }
        lastKnownPosition = pos;
    }

    private void handlePlaybackError(String message) {
        retryPlayback(message != null ? message : "Oynatma hatası");
    }

    private void retryPlayback(String reason) {
        if (retryCount >= MAX_RETRY) {
            showError("Bağlantı kurulamadı: " + reason + "\nTekrar denemek için dokunun.");
            return;
        }
        retryCount++;
        showError(reason + " (deneme " + retryCount + "/" + MAX_RETRY + ")");
        long backoffMs = Math.min(1000L * retryCount, 6000L);
        long resumePos = getCurrentPositionMs();
        handler.postDelayed(() -> {
            if (usingVlc) startVlcPlayer(resumePos); else startExoPlayer(resumePos);
        }, backoffMs);
    }

    private void showError(String msg) {
        tvErrorOverlay.setText(msg);
        tvErrorOverlay.setVisibility(View.VISIBLE);
    }

    private void hideError() {
        tvErrorOverlay.setVisibility(View.GONE);
    }

    // ---------------------------------------------------------------
    // UI güncellemeleri (seekbar, süre etiketleri)
    // ---------------------------------------------------------------

    @SuppressLint("DefaultLocale")
    private void updateSeekUi() {
        if (userTouchingSeek) return;
        long pos = getCurrentPositionMs();
        long dur = getDurationMs();
        if (dur > 0) {
            seekBar.setMax((int) (dur / 1000));
            seekBar.setProgress((int) (pos / 1000));
        }
        tvPosition.setText(formatTime(pos));
        tvDuration.setText(formatTime(dur));
    }

    private void updateDurationUi() {
        long dur = getDurationMs();
        if (dur > 0) seekBar.setMax((int) (dur / 1000));
    }

    private String formatTime(long ms) {
        if (ms < 0) ms = 0;
        long totalSec = ms / 1000;
        long h = totalSec / 3600;
        long m = (totalSec % 3600) / 60;
        long s = totalSec % 60;
        return h > 0
                ? String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s)
                : String.format(Locale.getDefault(), "%02d:%02d", m, s);
    }

    // ---------------------------------------------------------------
    // Kaldığın yerden devam et (Room DB)
    // ---------------------------------------------------------------

    private long getResumePositionMs() {
        // NOT: DB okuması senkron küçük bir sorgu; ana thread'i UI açılışında minik ölçüde bloklar
        // ama kullanıcı deneyimi için (video anında doğru yerden başlasın) kabul edilebilir.
        WatchProgress wp = AppDatabase.getInstance(this).watchProgressDao().getByKey(progressKey);
        return wp != null ? wp.positionMs : 0L;
    }

    private void saveProgress() {
        long pos = getCurrentPositionMs();
        long dur = getDurationMs();
        if (pos <= 0 || dur <= 0) return;
        Executors.newSingleThreadExecutor().execute(() -> {
            WatchProgress wp = new WatchProgress();
            wp.key = progressKey;
            wp.name = channelName;
            wp.posterUrl = posterUrl;
            wp.streamUrl = streamUrl;
            wp.positionMs = pos;
            wp.durationMs = dur;
            wp.lastWatchedAt = System.currentTimeMillis();
            AppDatabase.getInstance(this).watchProgressDao().upsert(wp);
        });
    }

    // ---------------------------------------------------------------
    // Lifecycle - pil tasarrufu için arka planda tam release
    // ---------------------------------------------------------------

    private void startLoops() {
        handler.removeCallbacks(watchdogRunnable);
        handler.removeCallbacks(progressSaver);
        handler.removeCallbacks(uiTicker);
        handler.postDelayed(watchdogRunnable, WATCHDOG_INTERVAL_MS);
        handler.postDelayed(progressSaver, SAVE_PROGRESS_INTERVAL_MS);
        handler.post(uiTicker);
    }

    private void stopLoops() {
        handler.removeCallbacks(watchdogRunnable);
        handler.removeCallbacks(progressSaver);
        handler.removeCallbacks(uiTicker);
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveProgress();
        stopLoops();
        // Ekran kapansın/arka plana geçilsin diye oynatıcıyı tamamen serbest bırak.
        // Bu, arka planda gereksiz CPU/decode/network kullanımını (ve pil tüketimini) sıfırlar.
        releaseExo();
        releaseVlc();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (exoPlayer == null && vlcPlayer == null) {
            long resumePos = getResumePositionMs();
            if (usingVlc) startVlcPlayer(resumePos); else startExoPlayer(resumePos);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopLoops();
        releaseExo();
        releaseVlc();
    }

    private void releaseExo() {
        if (exoPlayer != null) {
            exoPlayer.release();
            exoPlayer = null;
        }
    }

    private void releaseVlc() {
        if (vlcPlayer != null) {
            vlcPlayer.stop();
            vlcPlayer.detachViews();
            vlcPlayer.release();
            vlcPlayer = null;
        }
        if (libVLC != null) {
            libVLC.release();
            libVLC = null;
        }
    }
}
