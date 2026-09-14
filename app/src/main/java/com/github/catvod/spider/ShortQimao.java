package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;
import android.util.Base64;
import com.github.catvod.crawler.Spider;
import com.google.gson.Gson;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

public class ShortQimao extends Spider {
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
    private static final String SIGN_KEY = "d3dGiJc651gSQ8w1";
    private String storeHost = "https://api-store.qmplaylet.com";
    private String readHost = "https://api-read.qmplaylet.com";

    private static String md5(String source) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(source.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                String hex = Integer.toHexString(b & 0xFF);
                if (hex.length() == 1) {
                    sb.append('0');
                }
                sb.append(hex);
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static String buildQmParams() {
        // 服务端升级后，原来的 18 字段 fingerprint 全部被拒（返回 invalid qm-params header）。
        // 实测空 fingerprint {} 编码后的空串可以正常通过验签。
        return "";
    }

    private static String buildSign(String raw) {
        return md5(raw);
    }

    private static String appendQueryString(String url, Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return url;
        }
        StringBuilder sb = new StringBuilder(url);
        boolean hasQuery = url.contains("?");
        for (Map.Entry<String, String> entry : new TreeMap<>(params).entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue() == null ? "" : entry.getValue();
            if (TextUtils.isEmpty(key)) {
                continue;
            }
            try {
                if (hasQuery) {
                    sb.append('&');
                } else {
                    sb.append('?');
                    hasQuery = true;
                }
                sb.append(URLEncoder.encode(key, StandardCharsets.UTF_8.name()));
                sb.append('=');
                sb.append(URLEncoder.encode(value, StandardCharsets.UTF_8.name()));
            } catch (Exception ignored) {
            }
        }
        return sb.toString();
    }

    private static String httpGet(String url, Map<String, String> headers) {
        HttpURLConnection connection = null;
        try {
            URL requestUrl = new URL(url);
            connection = (HttpURLConnection) requestUrl.openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            connection.setRequestMethod("GET");
            connection.setInstanceFollowRedirects(false);
            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    if (entry.getKey() != null && entry.getValue() != null) {
                        connection.setRequestProperty(entry.getKey(), entry.getValue());
                    }
                }
            }
            int status = connection.getResponseCode();
            InputStream stream = (status >= 200 && status < 300) ? connection.getInputStream() : connection.getErrorStream();
            if (stream == null) {
                return "";
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            StringBuilder buffer = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                buffer.append(line);
            }
            return buffer.toString();
        } catch (Exception e) {
            return "";
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static JSONObject signRequest(String baseUrl, String path, LinkedHashMap<String, String> params) {
        String qmParams = buildQmParams();
        LinkedHashMap<String, String> requestParams = new LinkedHashMap<>();
        if (params != null) {
            requestParams.putAll(params);
        }

        StringBuilder signatureBuilder = new StringBuilder();
        for (Map.Entry<String, String> entry : new TreeMap<>(requestParams).entrySet()) {
            String value = entry.getValue() == null ? "" : entry.getValue();
            signatureBuilder.append(entry.getKey());
            signatureBuilder.append('=');
            signatureBuilder.append(value);
        }
        signatureBuilder.append(SIGN_KEY);
        requestParams.put("sign", buildSign(signatureBuilder.toString()));

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("authorization", "");
        headers.put("reg", "");
        headers.put("is-white", "");
        headers.put("user-agent", "webviewversion/0");
        headers.put("net-env", "1");
        headers.put("channel", "va-vivo_lf");
        headers.put("platform", "android");
        headers.put("application-id", "com.duoduo.read");
        headers.put("app-version", "10001");
        headers.put("qm-params", qmParams);
        headers.put("no-permiss", "3");
        headers.put("sign", md5("AUTHORIZATION=app-version=10001application-id=com.duoduo.readchannel=va-vivo_lfis-white=net-env=1platform=androidqm-params=" + qmParams + "reg=" + SIGN_KEY));

        String response = httpGet(appendQueryString(baseUrl + path, requestParams), headers);
        if (TextUtils.isEmpty(response)) {
            return new JSONObject();
        }
        try {
            return new JSONObject(response);
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private static String stripHtml(String input) {
        if (TextUtils.isEmpty(input)) {
            return "";
        }
        return HTML_TAG.matcher(input).replaceAll("");
    }

    private List<JSONObject> parseList(JSONArray items) throws Exception {
        List<JSONObject> result = new ArrayList<>();
        if (items == null) {
            return result;
        }
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String playletId = item.optString("playlet_id", item.optString("id"));
            if (TextUtils.isEmpty(playletId)) {
                continue;
            }
            String title = stripHtml(item.optString("title"));
            String cover = "";
            String[] coverKeys = {"image_link", "image", "cover", "vertical_cover", "playlet_cover"};
            for (String coverKey : coverKeys) {
                String value = item.optString(coverKey);
                if (!TextUtils.isEmpty(value)) {
                    cover = value;
                    break;
                }
            }
            String remarks = item.optString("total_episode_num");
            if (TextUtils.isEmpty(remarks)) {
                remarks = item.optString("total_num");
            }
            if (TextUtils.isEmpty(remarks)) {
                remarks = item.optString("sub_title");
            }
            JSONObject object = new JSONObject();
            object.put("vod_id", playletId);
            object.put("vod_name", title);
            object.put("vod_pic", cover);
            object.put("vod_remarks", remarks);
            result.add(object);
        }
        return result;
    }

    public String categoryContent(String tagId, String nextId, boolean filter, HashMap<String, String> map) throws Exception {
        LinkedHashMap<String, String> params = new LinkedHashMap<>();
        params.put("tag_id", tagId);
        params.put("next_id", TextUtils.isEmpty(nextId) ? "1" : nextId);
        params.put("playlet_privacy", "0".equals(tagId) ? "0" : "1");

        JSONObject data = signRequest(storeHost, "/api/v1/playlet/index", params).optJSONObject("data");
        JSONArray list = data != null && data.has("list") ? data.optJSONArray("list") : null;
        List<JSONObject> items = parseList(list);

        int page;
        try {
            page = Integer.parseInt(nextId == null ? "1" : nextId);
        } catch (Exception e) {
            page = 1;
        }

        JSONObject result = new JSONObject();
        result.put("page", page);
        result.put("pagecount", items.isEmpty() ? page : page + 1);
        result.put("limit", 20);
        result.put("total", items.size());
        result.put("list", new JSONArray(items));
        return result.toString();
    }

    public String detailContent(List<String> list) throws Exception {
        if (list == null || list.isEmpty()) {
            return "";
        }
        String playletId = list.get(0);
        LinkedHashMap<String, String> params = new LinkedHashMap<>();
        params.put("playlet_id", playletId);
        JSONObject data = signRequest(readHost, "/player/api/v1/playlet/info", params).optJSONObject("data");
        if (data == null) {
            JSONObject fallback = new JSONObject();
            fallback.put("vod_id", playletId);
            fallback.put("vod_name", playletId);
            fallback.put("vod_pic", "");
            fallback.put("vod_remarks", "");
            JSONArray fallbackList = new JSONArray();
            fallbackList.put(fallback);
            JSONObject fallbackResult = new JSONObject();
            fallbackResult.put("list", fallbackList);
            return fallbackResult.toString();
        }

        String title = data.optString("title");
        String cover = "";
        String[] coverKeys = {"image_link", "image", "cover"};
        for (String key : coverKeys) {
            String value = data.optString(key);
            if (!TextUtils.isEmpty(value)) {
                cover = value;
                break;
            }
        }

        JSONObject item = new JSONObject();
        item.put("vod_id", playletId);
        item.put("vod_name", title);
        item.put("vod_pic", cover);
        item.put("vod_content", data.optString("intro"));
        item.put("vod_play_from", "七猫");

        JSONArray playList = data.optJSONArray("play_list");
        ArrayList<String> urls = new ArrayList<>();
        if (playList != null && playList.length() > 0) {
            for (int i = 0; i < playList.length(); i++) {
                JSONObject entry = playList.optJSONObject(i);
                if (entry == null) {
                    continue;
                }
                String sort = entry.optString("sort");
                if (TextUtils.isEmpty(sort)) {
                    sort = String.valueOf(i + 1);
                }
                String url = entry.optString("video_url");
                if (!TextUtils.isEmpty(url)) {
                    urls.add("第" + sort + "集$" + url);
                }
            }
        }
        item.put("vod_play_url", TextUtils.join("#", urls));
        JSONArray listResult = new JSONArray();
        listResult.put(item);
        JSONObject result = new JSONObject();
        result.put("list", listResult);
        return result.toString();
    }

    public String homeContent(boolean filter) throws Exception {
        LinkedHashMap<String, String> params = new LinkedHashMap<>();
        params.put("tag_id", "0");
        params.put("playlet_privacy", "1");
        params.put("operation", "1");

        JSONObject data = signRequest(storeHost, "/api/v1/playlet/index", params).optJSONObject("data");
        JSONArray categories = data != null ? data.optJSONArray("tag_items") : null;
        JSONArray result = new JSONArray();
        if (categories != null) {
            for (int i = 0; i < categories.length(); i++) {
                JSONObject item = categories.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                String tagId = item.optString("tag_id");
                String tagName = item.optString("tag_name");
                if (!TextUtils.isEmpty(tagId)) {
                    JSONObject category = new JSONObject();
                    category.put("type_id", tagId);
                    category.put("type_name", tagName);
                    result.put(category);
                }
            }
        }
        JSONObject wrapper = new JSONObject();
        wrapper.put("class", result);
        return wrapper.toString();
    }

    public void init(Context context, String str) throws Exception {
        super.init(context, str);
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "okhttp/4.10.0");
        String domainJson = httpGet("https://neptune.qmplaylet.com/playlet-domain-android.json", headers);
        if (TextUtils.isEmpty(domainJson)) {
            return;
        }
        try {
            JSONObject data = new JSONObject(domainJson).optJSONObject("data");
            if (data == null) {
                return;
            }
            String bc = data.optString("bc");
            String ks = data.optString("ks");
            if (!TextUtils.isEmpty(bc)) {
                this.storeHost = bc.replaceAll("/$", "");
            }
            if (!TextUtils.isEmpty(ks)) {
                this.readHost = ks.replaceAll("/$", "");
            }
        } catch (Exception ignored) {
        }
    }

    public String playerContent(String flag, String url, List<String> flags) throws Exception {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("User-Agent", "webviewversion/0");
        headers.put("Referer", "Dalvik/2.1.0 (Linux; U; Android 11; M2012K10C Build/RP1A.200720.011)");
        JSONObject result = new JSONObject();
        result.put("parse", 0);
        result.put("url", url == null ? "" : url);
        result.put("header", new JSONObject(headers));
        return result.toString();
    }

    public String searchContent(String keyword, boolean quick) throws Exception {
        if (TextUtils.isEmpty(keyword)) {
            return "{\"list\":[]}";
        }
        LinkedHashMap<String, String> params = new LinkedHashMap<>();
        params.put("extend", "");
        params.put("page", "1");
        params.put("wd", keyword.trim());
        params.put("read_preference", "0");
        params.put("0", "6bcc46919d10d06a" + System.currentTimeMillis());

        JSONObject data = signRequest(storeHost, "/api/v1/playlet/search", params).optJSONObject("data");
        JSONArray list = data != null && data.has("list") ? data.optJSONArray("list") : null;
        List<JSONObject> items = parseList(list);

        JSONObject result = new JSONObject();
        result.put("page", 1);
        result.put("pagecount", 2);
        result.put("limit", 20);
        result.put("total", items.size());
        result.put("list", new JSONArray(items));
        return result.toString();
    }
}
