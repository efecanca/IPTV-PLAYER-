package com.efe.iptvplayer.data;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import com.efe.iptvplayer.model.Category;
import com.efe.iptvplayer.model.MediaItem;

/**
 * Xtream Codes panel API'sinden canlı/film/dizi listelerini çekip
 * uygulamanın ortak MediaItem modeline dönüştürür.
 * OkHttp + org.json kullanılıyor çünkü Xtream panelleri arasında
 * JSON tipleri (string/int) tutarsız olabiliyor; manuel parse daha güvenli.
 */
public class XtreamRepository {

    private final String host;   // örn: http://panel.example.com:8080
    private final String username;
    private final String password;

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS) // tüm liste tek istekte geliyor, büyük panellerde payload büyük olabilir
            .retryOnConnectionFailure(true)
            .build();

    public XtreamRepository(String host, String username, String password) {
        this.host = host.endsWith("/") ? host.substring(0, host.length() - 1) : host;
        this.username = username;
        this.password = password;
    }

    private String api(String action, String extra) {
        return host + "/player_api.php?username=" + username
                + "&password=" + password
                + (action != null ? "&action=" + action : "")
                + (extra != null ? extra : "");
    }

    private JSONArray fetchArray(String url) throws Exception {
        Request req = new Request.Builder().url(url).build();
        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful() || resp.body() == null) {
                throw new Exception("Sunucu hatası: " + resp.code());
            }
            String body = resp.body().string();
            return new JSONArray(body);
        }
    }

    /** Kullanıcı adı/şifre/host doğru mu diye login kontrolü yapar. */
    public boolean testLogin() {
        try {
            Request req = new Request.Builder().url(api(null, null)).build();
            try (Response resp = client.newCall(req).execute()) {
                if (!resp.isSuccessful() || resp.body() == null) return false;
                JSONObject obj = new JSONObject(resp.body().string());
                JSONObject userInfo = obj.optJSONObject("user_info");
                if (userInfo == null) return false;
                return "1".equals(userInfo.optString("auth", "0"));
            }
        } catch (Exception e) {
            return false;
        }
    }

    public List<Category> getLiveCategories() throws Exception {
        JSONArray catsJson = fetchArray(api("get_live_categories", null));

        // Kategori başına ayrı istek atmak yerine (N+1 sorunu - onlarca
        // kategoride dakikalar sürebiliyordu) TÜM canlı yayınları TEK istekte
        // çekip client tarafında kategoriye göre grupluyoruz.
        Map<String, Category> categoriesById = new LinkedHashMap<>();
        for (int i = 0; i < catsJson.length(); i++) {
            JSONObject c = catsJson.getJSONObject(i);
            String catId = c.optString("category_id");
            categoriesById.put(catId, new Category(catId, c.optString("category_name", "Kategori")));
        }

        JSONArray streams = fetchArray(api("get_live_streams", null)); // category_id verilmezse tümünü döner
        for (int j = 0; j < streams.length(); j++) {
            JSONObject s = streams.getJSONObject(j);
            String catId = s.optString("category_id");
            Category cat = categoriesById.get(catId);
            if (cat == null) continue; // bilinmeyen/boş kategori

            String streamId = s.optString("stream_id");
            MediaItem item = new MediaItem();
            item.setId(streamId);
            item.setName(s.optString("name", "Kanal"));
            item.setPosterUrl(s.optString("stream_icon", ""));
            item.setCategoryId(catId);
            item.setCategoryName(cat.getName());
            item.setType(MediaItem.Type.LIVE);
            item.setStreamUrl(host + "/live/" + username + "/" + password + "/" + streamId + ".m3u8");
            item.setAddedAtEpochMs(parseAddedTimestamp(s.optString("added", "0")));
            cat.addItem(item);
        }
        return new ArrayList<>(categoriesById.values());
    }

    public List<Category> getVodCategories() throws Exception {
        JSONArray catsJson = fetchArray(api("get_vod_categories", null));

        Map<String, Category> categoriesById = new LinkedHashMap<>();
        for (int i = 0; i < catsJson.length(); i++) {
            JSONObject c = catsJson.getJSONObject(i);
            String catId = c.optString("category_id");
            categoriesById.put(catId, new Category(catId, c.optString("category_name", "Kategori")));
        }

        // Aynı N+1 düzeltmesi: tüm VOD'ları tek istekte çekiyoruz.
        JSONArray vods = fetchArray(api("get_vod_streams", null));
        for (int j = 0; j < vods.length(); j++) {
            JSONObject s = vods.getJSONObject(j);
            String catId = s.optString("category_id");
            Category cat = categoriesById.get(catId);
            if (cat == null) continue;

            String streamId = s.optString("stream_id");
            String ext = s.optString("container_extension", "mp4");
            MediaItem item = new MediaItem();
            item.setId(streamId);
            item.setName(s.optString("name", "Film"));
            item.setPosterUrl(s.optString("stream_icon", ""));
            item.setCategoryId(catId);
            item.setCategoryName(cat.getName());
            item.setType(MediaItem.Type.MOVIE);
            item.setStreamUrl(host + "/movie/" + username + "/" + password + "/" + streamId + "." + ext);
            item.setAddedAtEpochMs(parseAddedTimestamp(s.optString("added", "0")));
            cat.addItem(item);
        }
        return new ArrayList<>(categoriesById.values());
    }

    private long parseAddedTimestamp(String raw) {
        try {
            return Long.parseLong(raw) * 1000L;
        } catch (Exception e) {
            return 0L;
        }
    }
}
