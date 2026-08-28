package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Filter;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class App99 extends Spider {

    private static final MediaType JSON_MEDIA = MediaType.parse("application/json; charset=utf-8");
    private OkHttpClient client;

    private String host;
    private String token;
    private String uuid;
    private String appId;
    private String userAgent;
    private String version;
    private String deviceId;

    private JSONObject cache = new JSONObject();

    @Override
    public void init(Context context, String extend) {
        if (TextUtils.isEmpty(extend)) return;
        try {
            JSONObject config = new JSONObject(extend);
            host = config.optString("host");
            appId = config.optString("appId");
            userAgent = config.optString("userAgent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            version = config.optString("version", "1.0");
            deviceId = config.optString("deviceId", UUID.randomUUID().toString());
            uuid = config.optString("uuid", UUID.randomUUID().toString());

            client = new OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .build();

            doLogin();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void doLogin() {
        try {
            JSONObject params = new JSONObject();
            params.put("appId", appId);
            params.put("deviceId", deviceId);
            params.put("version", version);

            JSONObject result = apiRequest("/api/login", params);
            if (result.has("data")) {
                JSONObject data = result.getJSONObject("data");
                token = data.optString("token");
                if (data.has("classify")) {
                    cache.put("classify", data.getJSONArray("classify"));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String getDefaultCategoryId() {
        try {
            if (cache.has("classify")) {
                JSONArray classify = cache.getJSONArray("classify");
                for (int i = 0; i < classify.length(); i++) {
                    JSONObject c = classify.getJSONObject(i);
                    String name = c.optString("name");
                    if (!TextUtils.isEmpty(name) && (name.contains("电影") || name.contains("电视剧"))) {
                        return c.optString("id");
                    }
                }
                return classify.getJSONObject(0).optString("id");
            }
        } catch (Exception e) { /* ignore */ }
        return "1";
    }

    @Override
    public String homeContent(boolean z) {
        try {
            JSONObject req = new JSONObject();
            req.put("page", 1);
            req.put("limit", 21);
            req.put("class", getDefaultCategoryId());
            req.put("sort", "time");
            req.put("order", 1);
            req.put("token", token);

            JSONObject result = apiRequest("/api/category", req);
            List<Vod> vodList = new ArrayList<>();
            if (result.has("data")) {
                JSONArray data = result.getJSONArray("data");
                for (int i = 0; i < data.length(); i++) {
                    JSONObject item = data.getJSONObject(i);
                    Vod vod = new Vod(
                            item.optString("id"),
                            item.optString("name"),
                            item.optString("pic"),
                            item.optString("remarks")
                    );
                    vod.setVodYear(item.optString("year"));
                    vod.setVodArea(item.optString("area"));
                    vodList.add(vod);
                }
            }

            // 构建分类列表
            List<com.github.catvod.bean.Class> classList = new ArrayList<>();
            if (cache.has("classify")) {
                JSONArray classify = cache.getJSONArray("classify");
                for (int i = 0; i < classify.length(); i++) {
                    JSONObject c = classify.getJSONObject(i);
                    String id = c.optString("id");
                    String name = c.optString("name");
                    classList.add(new com.github.catvod.bean.Class(id, name));
                }
            }

            // 返回带分类和视频的结果
            return Result.string(classList, vodList);
        } catch (Exception e) {
            return Result.string(new ArrayList<>(), new ArrayList<>());
        }
    }

    @Override
    public String categoryContent(String tid, String pg, boolean z, HashMap<String, String> filter) {
        try {
            JSONObject req = new JSONObject();
            req.put("page", pg);
            req.put("limit", 21);
            req.put("class", tid);
            req.put("sort", "time");
            req.put("order", 1);
            if (filter != null) {
                for (Map.Entry<String, String> entry : filter.entrySet()) {
                    req.put(entry.getKey(), entry.getValue());
                }
            }
            req.put("token", token);

            JSONObject result = apiRequest("/api/category", req);
            List<Vod> vodList = new ArrayList<>();
            int total = 0;
            if (result.has("data")) {
                JSONArray data = result.getJSONArray("data");
                total = result.optInt("total", 0);
                for (int i = 0; i < data.length(); i++) {
                    JSONObject item = data.getJSONObject(i);
                    Vod vod = new Vod(
                            item.optString("id"),
                            item.optString("name"),
                            item.optString("pic"),
                            item.optString("remarks")
                    );
                    vodList.add(vod);
                }
            }
            int page = Integer.parseInt(pg);
            int pageCount = (total + 20) / 21;
            return Result.get().page(page, pageCount, 0, 0).vod(vodList).string();
        } catch (Exception e) {
            return Result.string(new ArrayList<>());
        }
    }

    @Override
    public String detailContent(List<String> ids) {
        try {
            String id = ids.get(0);
            JSONObject req = new JSONObject();
            req.put("id", id);
            req.put("token", token);

            JSONObject result = apiRequest("/api/detail", req);
            if (!result.has("data")) return Result.string(new Vod());

            JSONObject data = result.getJSONObject("data");
            Vod vod = new Vod();
            vod.setVodId(data.optString("id"));
            vod.setVodName(data.optString("name"));
            vod.setVodPic(data.optString("pic"));
            vod.setVodRemarks(data.optString("remarks"));
            vod.setVodYear(data.optString("year"));
            vod.setVodArea(data.optString("area"));
            vod.setVodActor(data.optString("actor"));
            vod.setVodDirector(data.optString("director"));
            vod.setVodContent(data.optString("content"));
            vod.setTypeName(data.optString("class"));

            String playFrom = data.optString("playFrom");
            String playUrl = data.optString("playUrl");
            if (TextUtils.isEmpty(playFrom) && data.has("playList")) {
                JSONArray playList = data.getJSONArray("playList");
                StringBuilder fromSb = new StringBuilder();
                StringBuilder urlSb = new StringBuilder();
                for (int i = 0; i < playList.length(); i++) {
                    JSONObject source = playList.getJSONObject(i);
                    if (i > 0) {
                        fromSb.append("$$$");
                        urlSb.append("$$$");
                    }
                    fromSb.append(source.optString("name", "源" + (i + 1)));
                    urlSb.append(source.optString("url"));
                }
                playFrom = fromSb.toString();
                playUrl = urlSb.toString();
            }
            vod.setVodPlayFrom(playFrom);
            vod.setVodPlayUrl(playUrl);

            return Result.string(vod);
        } catch (Exception e) {
            return Result.string(new Vod());
        }
    }

    @Override
    public String searchContent(String keywords, boolean z) {
        try {
            JSONObject req = new JSONObject();
            req.put("keyword", keywords);
            req.put("page", 1);
            req.put("limit", 21);
            req.put("sort", "relevance");
            req.put("token", token);

            JSONObject result = apiRequest("/api/search", req);
            List<Vod> vodList = new ArrayList<>();
            if (result.has("data")) {
                JSONArray data = result.getJSONArray("data");
                for (int i = 0; i < data.length(); i++) {
                    JSONObject item = data.getJSONObject(i);
                    Vod vod = new Vod(
                            item.optString("id"),
                            item.optString("name"),
                            item.optString("pic"),
                            item.optString("remarks")
                    );
                    vodList.add(vod);
                }
            }
            return Result.string(vodList);
        } catch (Exception e) {
            return Result.string(new ArrayList<>());
        }
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipIds) {
        try {
            if (id.startsWith("http://") || id.startsWith("https://")) {
                return buildPlayResult(id, flag);
            }

            JSONObject req = new JSONObject();
            req.put("flag", flag);
            req.put("id", id);
            req.put("token", token);
            JSONObject result = apiRequest("/api/play", req);
            if (result.has("url")) {
                String url = result.getString("url");
                return buildPlayResult(url, flag);
            }
            return Result.error("播放地址获取失败");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    private String buildPlayResult(String url, String flag) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("type", 0);
            obj.put("parse", 0);
            obj.put("url", url);
            if (url.toLowerCase().contains(".m3u8")) {
                obj.put("contentType", "application/x-mpegURL");
            }
            return obj.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    private JSONObject apiRequest(String path, JSONObject params) throws Exception {
        String apiUrl = host + path;
        String nonce = UUID.randomUUID().toString().replace("-", "");
        String timestamp = String.valueOf(System.currentTimeMillis());

        String sign = md5(nonce + timestamp + appId + version + token);

        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", userAgent);
        headers.put("Content-Type", "application/json");
        headers.put("uuid", uuid);
        headers.put("timestamp", timestamp);
        headers.put("nonce", nonce);
        headers.put("sign", sign);
        if (!TextUtils.isEmpty(token)) {
            headers.put("token", token);
        }

        Request.Builder builder = new Request.Builder()
                .url(apiUrl)
                .post(RequestBody.create(params.toString(), JSON_MEDIA));

        for (Map.Entry<String, String> entry : headers.entrySet()) {
            builder.addHeader(entry.getKey(), entry.getValue());
        }

        try (Response response = client.newCall(builder.build()).execute()) {
            if (!response.isSuccessful()) {
                throw new Exception("HTTP " + response.code());
            }
            String body = response.body() != null ? response.body().string() : "";
            if (TextUtils.isEmpty(body)) return new JSONObject();

            JSONObject result = new JSONObject(body);
            int code = result.optInt("code", -1);
            if (code != 0 && code != 200) {
                throw new Exception(result.optString("msg", "请求失败"));
            }
            return result;
        }
    }

    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
