package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;
import com.github.catvod.bean.*;
import com.github.catvod.crawler.Spider;
import com.github.catvod.jnet.OkHttp;
import com.github.catvod.spider.obf.Str;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

public class App99 extends Spider {

    // 配置参数
    private String host;
    private String token;
    private String uuid;
    private String appId;
    private String userAgent;
    private String version;
    private String deviceId;

    // 缓存数据
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

            // 初始化时尝试获取 token（原 m345g / m346h 逻辑简化）
            if (!TextUtils.isEmpty(host) && !TextUtils.isEmpty(appId)) {
                doLogin();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 模拟登录获取 token（原代码中 m345g / m346h / m347i）
     * 实际接口需根据具体后端调整
     */
    private void doLogin() {
        try {
            // 构造登录请求（示例）
            JSONObject params = new JSONObject();
            params.put("appId", appId);
            params.put("deviceId", deviceId);
            params.put("version", version);

            JSONObject result = apiRequest("/api/login", params);
            if (result.has("data")) {
                JSONObject data = result.getJSONObject("data");
                token = data.optString("token");
                // 缓存分类等数据
                if (data.has("classify")) {
                    cache.put("classify", data.getJSONArray("classify"));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public String homeContent(boolean z) {
        try {
            // 获取分类列表
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

            // 构建分类和筛选器（原代码中的分类和筛选逻辑）
            LinkedHashMap<String, List<Filter>> filters = new LinkedHashMap<>();
            // 如果缓存有分类数据，则生成分类列表（原 homeContent 中的 m341d 判断）
            if (cache.has("classify")) {
                JSONArray classify = cache.getJSONArray("classify");
                List<Class> classes = new ArrayList<>();
                for (int i = 0; i < classify.length(); i++) {
                    JSONObject c = classify.getJSONObject(i);
                    String id = c.optString("id");
                    String name = c.optString("name");
                    classes.add(new Class(id, name));
                    // 生成筛选器（原代码中的 filter）
                    JSONObject filterObj = c.optJSONObject("filter");
                    if (filterObj != null) {
                        List<Filter> filterList = new ArrayList<>();
                        JSONArray areaArr = filterObj.optJSONArray("area");
                        if (areaArr != null) {
                            List<Filter.Value> values = new ArrayList<>();
                            for (int j = 0; j < areaArr.length(); j++) {
                                values.add(new Filter.Value(areaArr.getString(j), areaArr.getString(j)));
                            }
                            filterList.add(new Filter("area", "地区", values));
                        }
                        // 其他筛选字段类似...
                        filters.put(id, filterList);
                    }
                }
                return Result.string(classes, vodList, filters);
            }

            return Result.string(vodList, filters);
        } catch (Exception e) {
            return Result.string(new ArrayList<>());
        }
    }

    // 获取默认分类 ID（原逻辑中取第一个有效分类）
    private String getDefaultCategoryId() {
        try {
            if (cache.has("classify")) {
                JSONArray classify = cache.getJSONArray("classify");
                for (int i = 0; i < classify.length(); i++) {
                    JSONObject c = classify.getJSONObject(i);
                    String name = c.optString("name");
                    // 原代码中 m341d 判断是否为 "电影" 等，这里简化
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

            // 处理播放源（原代码中复杂的 parse 逻辑）
            String playFrom = data.optString("playFrom");
            String playUrl = data.optString("playUrl");
            if (TextUtils.isEmpty(playFrom) && data.has("playList")) {
                // 原代码中处理多个播放源的情况
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
            // 如果 id 本身就是 HTTP 地址，直接返回
            if (id.startsWith("http://") || id.startsWith("https://")) {
                return buildPlayResult(id, flag);
            }

            // 否则请求播放地址
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

    /**
     * 构建播放返回结果（原 m339b）
     */
    private String buildPlayResult(String url, String flag) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("type", 0);
            obj.put("parse", 0);
            obj.put("url", url);
            if (url.toLowerCase().contains(".m3u8")) {
                obj.put("contentType", "application/x-mpegURL");
            }
            // 可能还需添加 header（原逻辑中会尝试获取字幕等）
            return obj.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    // ---------- 核心 API 请求封装（包含签名、加密，原 m350l 等） ----------
    private JSONObject apiRequest(String path, JSONObject params) throws Exception {
        String apiUrl = host + path;
        String nonce = UUID.randomUUID().toString().replace("-", "");
        String timestamp = String.valueOf(System.currentTimeMillis());

        // 生成签名（原 m344a 逻辑，这里简化为 MD5）
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

        // 原代码中会对 params 进行 AES 加密（m4624i0），这里简化直接发送明文
        String response = OkHttp.post(apiUrl, params.toString(), headers).getBody();
        if (TextUtils.isEmpty(response)) return new JSONObject();

        // 原响应会解密（m4537V），这里假设返回直接 JSON
        JSONObject result = new JSONObject(response);

        // 处理错误码（原 m350l 中的异常处理）
        int code = result.optInt("code", -1);
        if (code != 0 && code != 200) {
            throw new Exception(result.optString("msg", "请求失败"));
        }
        return result;
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
