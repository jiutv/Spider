package com.github.catvod.spider;

import android.content.Context;
import android.os.Build;
import android.text.TextUtils;
import android.util.Base64;
import com.github.catvod.crawler.Spider;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class ShortWeiguan extends Spider {
    private static final String BASE_URL = "https://api.drama.9ddm.com";
    private String deviceName = "";
    private String deviceFirm = "";
    private String clientInfo = "";

    private static String httpGet(String url, Map<String, String> headers) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);
        connection.setRequestMethod("GET");
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    connection.setRequestProperty(entry.getKey(), entry.getValue());
                }
            }
        }
        InputStream stream = connection.getResponseCode() >= 200 && connection.getResponseCode() < 300
                ? connection.getInputStream()
                : connection.getErrorStream();
        if (stream == null) {
            return "";
        }
        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        }
        return response.toString();
    }

    private static String httpPostJson(String url, JSONObject body, Map<String, String> headers) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setDoInput(true);
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    connection.setRequestProperty(entry.getKey(), entry.getValue());
                }
            }
        }
        if (body != null) {
            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            connection.setRequestProperty("Content-Length", String.valueOf(bytes.length));
            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(bytes);
            }
        }
        InputStream stream = connection.getResponseCode() >= 200 && connection.getResponseCode() < 300
                ? connection.getInputStream()
                : connection.getErrorStream();
        if (stream == null) {
            return "";
        }
        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        }
        return response.toString();
    }

    private Map<String, String> defaultHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "okhttp/5.1.0");
        return headers;
    }

    private String buildQuerySuffix() {
        return "?version_code=1600&version_name=1.6.0"
                + "&device_name=" + urlEncode(deviceName)
                + "&device_type=phone"
                + "&is_first_day=true"
                + "&is_first_24h=true"
                + "&app_launch_way=icon"
                + "&default_homepage=homepage_interaction"
                + "&device_owning_firm=" + urlEncode(deviceFirm)
                + "&font_scale=default"
                + "&os_type=1"
                + "&clientInfo=" + urlEncode(clientInfo);
    }

    private static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return value == null ? "" : value;
        }
    }

    private static String md5(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : digest) {
                String hex = Integer.toHexString(b & 0xFF);
                if (hex.length() == 1) {
                    builder.append('0');
                }
                builder.append(hex);
            }
            return builder.toString();
        } catch (Exception e) {
            return input;
        }
    }

    private static JSONArray toJSONArray(List<JSONObject> list) {
        JSONArray array = new JSONArray();
        for (JSONObject item : list) {
            if (item != null) {
                array.put(item);
            }
        }
        return array;
    }

    private ArrayList<JSONObject> parseList(JSONArray items) throws JSONException {
        ArrayList<JSONObject> result = new ArrayList<>();
        if (items == null) {
            return result;
        }
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            JSONObject entry = new JSONObject();
            entry.put("vod_id", item.optString("oneId"));
            entry.put("vod_name", item.optString("title"));
            entry.put("vod_pic", item.optString("horzPoster"));
            entry.put("vod_remarks", item.optString("episodeCount"));
            result.add(entry);
        }
        return result;
    }

    private String searchUrl(String path) {
        return BASE_URL + path + buildQuerySuffix();
    }

    public String categoryContent(String str, String str2, boolean z, HashMap<String, String> map) throws JSONException {
        if (map != null && map.containsKey("cateId")) {
            str = map.get("cateId");
        }
        JSONObject body = new JSONObject();
        body.put("audience", "全部");
        body.put("order", "最新");
        body.put("page", Integer.parseInt(str2));
        body.put("pageSize", 30);
        body.put("searchWord", "");
        body.put("subject", str);
        String response = "";
        try {
            response = httpPostJson(searchUrl("/drama/home/search"), body, defaultHeaders());
        } catch (Exception ignored) {
            response = "";
        }
        JSONArray data = new JSONObject(response).optJSONArray("data");
        ArrayList<JSONObject> list = parseList(data);

        int page = Integer.parseInt(str2);
        JSONObject result = new JSONObject();
        result.put("page", page);
        result.put("pagecount", list.size() < 30 ? page : page + 1);
        result.put("limit", 30);
        result.put("total", 0);
        result.put("list", toJSONArray(list));
        return result.toString();
    }

    public String detailContent(List<String> list) throws JSONException {
        String oneId = list.get(0);
        String url = searchUrl("/drama/home/shortVideoDetail") + "&oneId=" + oneId + "&page=1&pageSize=1000&userId=0&queryAll=true";
        JSONObject data = new JSONObject();
        try {
            data = new JSONObject(httpGet(url, defaultHeaders()));
        } catch (Exception ignored) {
            data = new JSONObject();
        }

        JSONObject item = new JSONObject();
        item.put("vod_id", oneId);
        item.put("vod_name", data.optString("title"));
        item.put("vod_pic", data.optString("vertPoster"));
        item.put("vod_remarks", "短剧");
        item.put("vod_content", data.optString("description"));

        ArrayList<String> playList = new ArrayList<>();
        JSONArray entries = data.optJSONArray("data");
        if (entries != null) {
            for (int i = 0; i < entries.length(); i++) {
                JSONObject entry = entries.getJSONObject(i);
                JSONArray clarity = entry.optJSONArray("videoClarityList");
                if (clarity == null) {
                    continue;
                }
                String encoded = Base64.encodeToString(clarity.toString().getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
                playList.add(entry.optString("playOrder") + "$" + encoded);
            }
        }
        item.put("vod_play_from", "短剧");
        item.put("vod_play_url", TextUtils.join("#", playList));

        JSONArray arr = new JSONArray();
        arr.put(item);
        JSONObject result = new JSONObject();
        result.put("list", arr);
        return result.toString();
    }

    public String homeContent(boolean z) throws JSONException {
        String response = "";
        try {
            response = httpGet(searchUrl("/drama/home/shortVideoTags"), defaultHeaders());
        } catch (Exception ignored) {
            response = "{}";
        }
        JSONArray tags = new JSONObject(response).optJSONArray("tags");
        JSONArray classes = new JSONArray();
        if (tags != null) {
            for (int i = 0; i < tags.length(); i++) {
                String tag = tags.getString(i);
                JSONObject entry = new JSONObject();
                entry.put("type_id", tag);
                entry.put("type_name", tag);
                classes.put(entry);
            }
        }
        JSONObject result = new JSONObject();
        result.put("class", classes);
        return result.toString();
    }

    public void init(Context context, String str) {
        super.init(context, str);
        this.deviceName = Build.MODEL;
        this.deviceFirm = Build.BRAND;
        Random random = new Random();
        StringBuilder sb = new StringBuilder();
        String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        for (int i = 0; i < 10; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        this.clientInfo = md5(sb.toString());
    }

    public String playerContent(String str, String str2, List<String> list) throws JSONException {
        JSONObject result = new JSONObject();
        result.put("parse", 0);
        try {
            JSONArray array = new JSONArray(new String(Base64.decode(str2, Base64.NO_WRAP), StandardCharsets.UTF_8));
            if (array.length() > 0) {
                result.put("url", array.getJSONObject(0).optString("url"));
            } else {
                result.put("url", "");
            }
        } catch (Exception e) {
            result.put("url", "");
        }
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "okhttp/5.1.0");
        result.put("header", new JSONObject(headers));
        return result.toString();
    }

    public String searchContent(String str, boolean z) throws JSONException {
        JSONObject body = new JSONObject();
        body.put("audience", "");
        body.put("order", "");
        body.put("page", 1);
        body.put("pageSize", 30);
        body.put("searchWord", str == null ? "" : str.trim());
        body.put("subject", "");
        String response = "";
        try {
            response = httpPostJson(searchUrl("/drama/home/search"), body, defaultHeaders());
        } catch (Exception ignored) {
            response = "{}";
        }
        JSONArray data = new JSONObject(response).optJSONArray("data");
        if (data == null) {
            data = new JSONArray();
        }
        ArrayList<JSONObject> list = parseList(data);
        JSONObject result = new JSONObject();
        result.put("list", toJSONArray(list));
        return result.toString();
    }
}
