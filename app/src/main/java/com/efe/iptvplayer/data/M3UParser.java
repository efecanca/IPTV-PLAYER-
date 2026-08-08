package com.efe.iptvplayer.data;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import com.efe.iptvplayer.model.Category;
import com.efe.iptvplayer.model.MediaItem;

/**
 * Standart #EXTM3U / #EXTINF formatındaki playlist'leri okuyup
 * kategori bazlı MediaItem listesine çevirir.
 */
public class M3UParser {

    private static final Pattern ATTR_PATTERN = Pattern.compile("([a-zA-Z0-9-]+)=\"([^\"]*)\"");

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    /** Uzak URL'den M3U indirip parse eder. */
    public Map<String, Category> parseFromUrl(String url) throws Exception {
        Request req = new Request.Builder().url(url).build();
        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful() || resp.body() == null) {
                throw new Exception("Playlist indirilemedi: " + resp.code());
            }
            try (InputStream is = resp.body().byteStream()) {
                return parse(is);
            }
        }
    }

    public Map<String, Category> parse(InputStream inputStream) throws Exception {
        Map<String, Category> categories = new LinkedHashMap<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));

        String line;
        String pendingName = null;
        String pendingLogo = null;
        String pendingGroup = null;
        long index = 0;

        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) continue;

            if (line.startsWith("#EXTINF")) {
                pendingName = extractName(line);
                pendingLogo = extractAttr(line, "tvg-logo");
                pendingGroup = extractAttr(line, "group-title");
                if (pendingGroup == null || pendingGroup.isEmpty()) pendingGroup = "Diğer";
            } else if (!line.startsWith("#")) {
                // Bu satır stream URL'si
                String groupName = pendingGroup != null ? pendingGroup : "Diğer";
                Category cat = categories.get(groupName);
                if (cat == null) {
                    cat = new Category(groupName, groupName);
                    categories.put(groupName, cat);
                }
                MediaItem item = new MediaItem();
                item.setId(String.valueOf(index++));
                item.setName(pendingName != null ? pendingName : "Kanal");
                item.setPosterUrl(pendingLogo != null ? pendingLogo : "");
                item.setStreamUrl(line);
                item.setCategoryId(groupName);
                item.setCategoryName(groupName);
                item.setType(MediaItem.Type.LIVE);
                item.setAddedAtEpochMs(System.currentTimeMillis() - index); // sıra korunsun diye azalan
                cat.addItem(item);

                pendingName = null;
                pendingLogo = null;
                pendingGroup = null;
            }
        }
        return categories;
    }

    private String extractName(String extinfLine) {
        int comma = extinfLine.lastIndexOf(',');
        return comma >= 0 && comma < extinfLine.length() - 1
                ? extinfLine.substring(comma + 1).trim()
                : "Kanal";
    }

    private String extractAttr(String line, String attr) {
        Matcher m = ATTR_PATTERN.matcher(line);
        while (m.find()) {
            if (m.group(1).equalsIgnoreCase(attr)) {
                return m.group(2);
            }
        }
        return null;
    }
}
