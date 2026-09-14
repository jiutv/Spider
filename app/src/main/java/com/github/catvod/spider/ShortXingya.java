package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;
import android.util.Base64;
import com.github.catvod.crawler.Spider;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class ShortXingya extends Spider {
    private static final String BASE_URL = "https://app.whjzjx.cn";
    private static final String LOGIN_URL = "https://u.shytkjgs.com/user/v3/account/login";
    private static final String USER_AGENT = "Mozilla/5.0 (Linux; Android 9; V1938T Build/PQ3A.190705.08211809; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/91.0.4472.114 Safari/537.36";
    private static final String AES_KEY = "B@ecf920Od8A4df7";

    private String authToken = "";

    private static String buildDevicePayload() throws JSONException {
        JSONObject payload = new JSONObject();
        payload.put("device", "2a50580e69d38388c94c93605241fb306");
        payload.put("package_name", "com.jz.xydj");
        payload.put("android_id", "ec1280db12795506");
        payload.put("install_first_open", true);
        payload.put("first_install_time", 1752505243345L);
        payload.put("last_update_time", 1752505243345L);
        payload.put("report_link_url", "");
        payload.put("authorization", "");
        payload.put("timestamp", System.currentTimeMillis());
        return payload.toString();
    }

    private static String aesEncryptBase64(String raw) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(AES_KEY.getBytes(StandardCharsets.UTF_8), "AES"));
        byte[] encrypted = cipher.doFinal(raw.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(encrypted, Base64.NO_WRAP);
    }

    private static String httpRequest(String url, String method, String body, Map<String, String> headers) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);
        connection.setRequestMethod(method);
        connection.setDoInput(true);
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    connection.setRequestProperty(entry.getKey(), entry.getValue());
                }
            }
        }
        if (body != null && !body.isEmpty()) {
            connection.setDoOutput(true);
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
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

    private static Map<String, String> buildLoginHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("platform", "1");
        headers.put("user_agent", USER_AGENT);
        headers.put("content-type", "application/json; charset=utf-8");
        return headers;
    }

    private static Map<String, String> buildAuthHeaders(String token) {
        Map<String, String> headers = new HashMap<>();
        headers.put("authorization", token);
        headers.put("platform", "1");
        headers.put("version_name", "3.8.3.1");
        return headers;
    }

    private static ArrayList<JSONObject> parseVodList(JSONArray array, String remarkKey) {
        ArrayList<JSONObject> list = new ArrayList<>();
        if (array == null) {
            return list;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) {
                continue;
            }
            JSONObject theater = item.optJSONObject("theater");
            if (theater == null) {
                continue;
            }
            String id = String.valueOf(theater.optInt("id"));
            String title = theater.optString("title");
            String pic = theater.optString("cover_url");
            String remark = theater.optString(remarkKey);
            if (TextUtils.isEmpty(remark)) {
                remark = theater.optString("play_amount_str");
            }
            JSONObject json = new JSONObject();
            try {
                json.put("vod_id", id);
                json.put("vod_name", title);
                json.put("vod_pic", pic);
                json.put("vod_remarks", remark);
            } catch (JSONException ignored) {
            }
            list.add(json);
        }
        return list;
    }

    private static JSONArray toJsonArray(ArrayList<JSONObject> items) {
        JSONArray array = new JSONArray();
        for (JSONObject item : items) {
            if (item != null) {
                array.put(item);
            }
        }
        return array;
    }

    private void login() throws Exception {
        String payload = buildDevicePayload();
        String encrypted = aesEncryptBase64(payload);
        Map<String, String> headers = buildLoginHeaders();
        String response = httpRequest(LOGIN_URL, "POST", encrypted, headers);
        String token = new JSONObject(response).getJSONObject("data").optString("token");
        if (TextUtils.isEmpty(token)) {
            throw new Exception("星盘登录失败");
        }
        this.authToken = token;
    }

    private Map<String, String> authHeaders() throws Exception {
        if (TextUtils.isEmpty(this.authToken)) {
            login();
        }
        return buildAuthHeaders(this.authToken);
    }

    private JSONObject requestJsonGet(String path) throws Exception {
        String response = httpRequest(BASE_URL + path, "GET", null, authHeaders());
        return new JSONObject(response);
    }

    private JSONObject requestJsonPost(String path, JSONObject body) throws Exception {
        String response = httpRequest(BASE_URL + path, "POST", body == null ? "" : body.toString(), authHeaders());
        return new JSONObject(response);
    }

    private String buildVideoPlayUrl(JSONObject detail) {
        JSONArray theaters = detail.optJSONArray("theaters");
        if (theaters == null || theaters.length() == 0) {
            String directUrl = detail.optString("video_url");
            if (TextUtils.isEmpty(directUrl)) {
                return "无播放源";
            }
            return "1$" + directUrl;
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < theaters.length(); i++) {
            JSONObject theater = theaters.optJSONObject(i);
            if (theater == null) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append("#");
            }
            builder.append(theater.optString("num"));
            builder.append("$");
            builder.append(theater.optString("son_video_url"));
        }
        return builder.toString();
    }

    private JSONObject buildSearchResultItem(JSONObject item) throws JSONException {
        String id = item.optString("id");
        if (TextUtils.isEmpty(id) || "0".equals(id)) {
            long numericId = item.optLong("id", 0L);
            if (numericId > 0) {
                id = String.valueOf(numericId);
            }
        }
        if (TextUtils.isEmpty(id) || "0".equals(id)) {
            return null;
        }
        JSONObject out = new JSONObject();
        out.put("vod_id", id);
        out.put("vod_name", item.optString("title"));
        out.put("vod_pic", item.optString("cover_url"));
        out.put("vod_remarks", item.optString("score_str"));
        return out;
    }

    public String categoryContent(String str, String str2, boolean z, HashMap<String, String> map) throws JSONException {
        int page = 1;
        try {
            page = Math.max(1, Integer.parseInt(str2));
        } catch (Exception ignored) {
        }

        JSONObject response = requestJsonGet("/v1/theater/home_page?theater_class_id=" + str + "&page_num=" + page + "&page_size=24");
        JSONArray list = response.optJSONObject("data").optJSONArray("list");
        ArrayList<JSONObject> items = parseVodList(list, "theme");

        JSONObject result = new JSONObject();
        result.put("page", page);
        result.put("pagecount", page + 1);
        result.put("limit", 24);
        result.put("total", 9999);
        result.put("list", toJsonArray(items));
        return result.toString();
    }

    public String detailContent(List<String> list) throws JSONException {
        String theaterId = list.get(0);
        JSONObject detail = requestJsonGet("/v2/theater_parent/detail?theater_parent_id=" + theaterId).getJSONObject("data");

        String introduction = detail.optString("introduction");
        JSONArray descTags = detail.optJSONArray("desc_tags");
        String descTag = (descTags == null || descTags.length() <= 0) ? "" : descTags.optString(0);
        String filing = detail.optString("filing");
        String playUrl = buildVideoPlayUrl(detail);
        String playSource = TextUtils.isEmpty(detail.optString("video_url")) ? "星盘" : "星盘";

        JSONObject item = new JSONObject();
        item.put("vod_id", theaterId);
        item.put("vod_name", detail.optString("title", theaterId));
        item.put("vod_pic", detail.optString("cover_url", ""));
        item.put("vod_content", introduction);
        item.put("vod_remarks", filing);
        item.put("vod_play_from", playSource);
        item.put("vod_play_url", playUrl);

        JSONArray wrappers = new JSONArray();
        wrappers.put(item);
        JSONObject result = new JSONObject();
        result.put("list", wrappers);
        return result.toString();
    }

    public String homeContent(boolean z) throws JSONException {
        JSONArray categories = new JSONArray();
        categories.put(new JSONObject().put("type_id", "1").put("type_name", "剧场"));
        categories.put(new JSONObject().put("type_id", "3").put("type_name", "新剧"));
        categories.put(new JSONObject().put("type_id", "2").put("type_name", "热播"));
        categories.put(new JSONObject().put("type_id", "7").put("type_name", "星选"));
        categories.put(new JSONObject().put("type_id", "5").put("type_name", "阳光"));

        JSONObject result = new JSONObject();
        result.put("class", categories);
        return result.toString();
    }

    public String homeVideoContent() throws JSONException {
        JSONObject response = requestJsonGet("/v1/theater/home_page?theater_class_id=1&class2_id=4&page_num=1&page_size=24");
        JSONArray list = response.optJSONObject("data").optJSONArray("list");
        ArrayList<JSONObject> items = parseVodList(list, "play_amount_str");
        JSONObject result = new JSONObject();
        result.put("list", toJsonArray(items));
        return result.toString();
    }

    public void init(Context context, String str) {
        super.init(context, str);
    }

    public String playerContent(String str, String str2, List<String> list) {
        JSONObject result = new JSONObject();
        try {
            result.put("parse", 0);
            result.put("url", TextUtils.isEmpty(str2) ? "" : str2);
            Map<String, String> headers = new HashMap<>();
            headers.put("User-Agent", "Linux; Android 12; Pixel 3 XL) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/98.0.4758.101 Mobile Safari/537.36");
            result.put("header", new JSONObject(headers));
        } catch (Exception ignored) {
            result = new JSONObject();
            try {
                result.put("parse", 0);
                result.put("url", "");
            } catch (JSONException ignored2) {
            }
        }
        return result.toString();
    }

    public String searchContent(String str, boolean z) throws JSONException {
        if (TextUtils.isEmpty(str)) {
            JSONObject empty = new JSONObject();
            empty.put("list", new JSONArray());
            return empty.toString();
        }

        JSONObject body = new JSONObject();
        body.put("text", str.trim());
        JSONObject response = requestJsonPost("/v3/search", body);
        JSONArray items = response.optJSONObject("data").optJSONObject("theater").optJSONArray("search_data");
        if (items == null) {
            items = new JSONArray();
        }

        ArrayList<JSONObject> list = new ArrayList<>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) {
                continue;
            }
            JSONObject mapped = buildSearchResultItem(item);
            if (mapped != null) {
                list.add(mapped);
            }
        }

        JSONObject result = new JSONObject();
        result.put("list", new JSONArray(list));
        return result.toString();
    }
}

