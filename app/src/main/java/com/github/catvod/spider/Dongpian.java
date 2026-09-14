package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * 懂片帝 (dongpian.ai) 专用爬虫
 *
 * 技术要点：
 * - 前端是 React SPA，数据来自 /v1/* API
 * - /v1/* 请求需要 HMAC-SHA256 签名（前端逆向得到的密钥 Vo）
 * - 签名 header: x-ai-movie-timestamp / x-ai-movie-nonce / x-ai-movie-signature
 * - 客户端 header: x-ai-movie-client-* 系列
 *
 * API 端点：
 * - GET  /v1/feed/home                           首页内容
 * - GET  /v1/browse/catalog?kind=series&page=1   分类列表（kind: series/movie/anime/variety/short_drama）
 * - GET  /v1/catalog/{card_id}                   详情（含 episodes 和 yjm3u8 直链 m3u8）
 *
 * 播放源：episodes[].urls.yjm3u8 是直链 m3u8，episodes[].urls.yjapi 是 API 模式
 */
public class Dongpian extends Spider {

    private static final String SITE = "https://dongpian.ai";

    // 前端逆向得到的签名密钥 (Vo)
    private static final String SIGN_SECRET = "8b9a908a05eac640e1ee06f52acaa741bfe4ba9e004eeffdbeb635e532e06666";

    // 客户端标识 (la() 函数填充的 header)
    private static final Map<String, String> CLIENT_HEADERS = new HashMap<>();
    static {
        CLIENT_HEADERS.put("x-ai-movie-client-name", "movie-search-frontend");
        CLIENT_HEADERS.put("x-ai-movie-client-version", "1.0.0");
        CLIENT_HEADERS.put("x-ai-movie-build-version", "dongpiandi-v2026.09.14.12-8ea8477c2120-web");
        CLIENT_HEADERS.put("x-ai-movie-protocol-version", "2026-07-05.library-v2.playback-v1");
    }

    // 首页展示的分类（kind 映射）
    private static final List<String> KIND_IDS   = Arrays.asList("series", "movie", "anime", "variety", "short_drama");
    private static final List<String> KIND_NAMES = Arrays.asList("电视剧", "电影", "动漫", "综艺", "短剧");

    private final OkHttpClient client = new OkHttpClient();
    private final SecureRandom random = new SecureRandom();

    // ==================== Spider 接口 ====================

    @Override
    public void init(Context context, String extend) {}

    @Override
    public String homeContent(boolean filter) {
        ArrayList<Class> classes = new ArrayList<>();
        for (int i = 0; i < KIND_IDS.size(); i++) {
            classes.add(new Class(KIND_IDS.get(i), KIND_NAMES.get(i)));
        }
        try {
            return Result.string(classes, new JSONObject());
        } catch (Exception e) {
            return Result.string(classes, new ArrayList<Vod>());
        }
    }

    @Override
    public String homeVideoContent() {
        ArrayList<Vod> list = new ArrayList<>();
        try {
            String url = SITE + "/v1/feed/home";
            String resp = get(url);
            JSONObject root = new JSONObject(resp);
            JSONArray sections = root.optJSONArray("sections");
            if (sections == null) return Result.string(list);

            for (int i = 0; i < sections.length(); i++) {
                JSONObject sec = sections.optJSONObject(i);
                JSONArray cards = sec == null ? null : sec.optJSONArray("cards");
                if (cards == null) continue;
                for (int j = 0; j < cards.length(); j++) {
                    JSONObject card = cards.optJSONObject(j);
                    if (card != null) list.add(parseCard(card));
                }
            }
        } catch (Exception ignored) {}
        return Result.string(list);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        ArrayList<Vod> list = new ArrayList<>();
        if (TextUtils.isEmpty(pg)) pg = "1";
        try {
            StringBuilder url = new StringBuilder(SITE);
            url.append("/v1/browse/catalog?page=").append(pg);
            url.append("&limit=20");
            url.append("&sort=updated_desc");
            if (!TextUtils.isEmpty(tid)) url.append("&kind=").append(tid);

            String resp = get(url.toString());
            JSONObject root = new JSONObject(resp);
            JSONArray cards = root.optJSONArray("cards");
            if (cards != null) {
                for (int i = 0; i < cards.length(); i++) {
                    JSONObject card = cards.optJSONObject(i);
                    if (card != null) list.add(parseCard(card));
                }
            }
        } catch (Exception ignored) {}
        return Result.string(list);
    }

    @Override
    public String detailContent(List<String> ids) {
        ArrayList<Vod> empty = new ArrayList<>();
        if (ids == null || ids.isEmpty()) return Result.string(empty);

        String cardId = ids.get(0);
        try {
            String url = SITE + "/v1/catalog/" + cardId;
            String resp = get(url);
            JSONObject d = new JSONObject(resp);

            Vod vod = new Vod();
            vod.setVodId(d.optString("id", cardId));
            vod.setVodName(d.optString("title"));
            vod.setVodPic(d.optString("poster_url"));
            vod.setVodContent(d.optString("description"));
            vod.setTypeName(d.optString("content_kind", ""));
            vod.setVodYear(String.valueOf(d.optInt("year")));
            vod.setVodArea(d.optString("area"));
            vod.setVodRemarks(d.optString("remarks"));

            // 演员 / 导演
            vod.setVodActor(joinJsonArray(d.optJSONArray("actors"), " / "));
            vod.setVodDirector(joinJsonArray(d.optJSONArray("directors"), " / "));

            // 剧集 + 播放源
            JSONArray episodes = d.optJSONArray("episodes");
            if (episodes != null && episodes.length() > 0) {
                // 线路: yjm3u8 (m3u8直链) 和 yjapi (API JSON)
                ArrayList<String> fromList = new ArrayList<>();
                ArrayList<String> urlGroupList = new ArrayList<>();

                fromList.add("M3U8");
                ArrayList<String> m3u8Items = new ArrayList<>();
                fromList.add("API");
                ArrayList<String> apiItems = new ArrayList<>();

                for (int i = 0; i < episodes.length(); i++) {
                    JSONObject ep = episodes.optJSONObject(i);
                    if (ep == null) continue;
                    String title = ep.optString("title");
                    if (TextUtils.isEmpty(title)) title = "第" + (i + 1) + "集";
                    JSONObject urls = ep.optJSONObject("urls");
                    if (urls != null) {
                        String m3u8 = urls.optString("yjm3u8");
                        if (!TextUtils.isEmpty(m3u8)) m3u8Items.add(title + "$" + m3u8);
                        String api = urls.optString("yjapi");
                        if (!TextUtils.isEmpty(api)) apiItems.add(title + "$" + api);
                    }
                }

                urlGroupList.add(join("#", m3u8Items));
                urlGroupList.add(join("#", apiItems));

                vod.setVodPlayFrom(join("$$$", fromList));
                vod.setVodPlayUrl(join("$$$", urlGroupList));
            }

            return Result.string(vod);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.string(empty);
        }
    }

    @Override
    public String searchContent(String key, boolean quick) {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) {
        // 懂片帝没开放普通搜索 API，退化为遍历分类列表过滤标题
        ArrayList<Vod> list = new ArrayList<>();
        if (TextUtils.isEmpty(key)) return Result.string(list);
        if (TextUtils.isEmpty(pg)) pg = "1";

        try {
            // 用 browse catalog 的 updated_desc 排序 + 客户端本地过滤 title
            // 搜不到足够结果时，尝试多个 kind 拼合
            for (String kind : KIND_IDS) {
                StringBuilder url = new StringBuilder(SITE);
                url.append("/v1/browse/catalog?page=").append(pg);
                url.append("&limit=20");
                url.append("&sort=heat_desc");
                url.append("&kind=").append(kind);

                String resp = get(url.toString());
                JSONObject root = new JSONObject(resp);
                JSONArray cards = root.optJSONArray("cards");
                if (cards == null) continue;
                for (int i = 0; i < cards.length(); i++) {
                    JSONObject card = cards.optJSONObject(i);
                    if (card == null) continue;
                    String title = card.optString("title");
                    String normalized = card.optString("normalized_title");
                    if (title.contains(key) || normalized.contains(key)) {
                        list.add(parseCard(card));
                    }
                }
            }
        } catch (Exception ignored) {}
        return Result.string(list);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        try {
            // id 已经是完整的 m3u8 / api URL，直接返回
            Map<String, String> headers = new HashMap<>();
            headers.put("User-Agent", "Mozilla/5.0");
            headers.put("Referer", SITE + "/");
            return Result.get().url(id).header(headers).parse(0).string();
        } catch (Exception e) {
            return Result.get().url(id).parse(0).string();
        }
    }

    // ==================== 网络 + 签名 ====================

    /**
     * 执行 GET /v1/* 请求（自动带签名）
     */
    private String get(String url) throws Exception {
        return request("GET", url, null);
    }

    /**
     * 执行 POST /v1/* 请求（自动带签名 + JSON body）
     */
    private String post(String url, JSONObject body) throws Exception {
        return request("POST", url, body.toString());
    }

    private String request(String method, String urlStr, String jsonBody) throws Exception {
        java.net.URL u = new java.net.URL(urlStr);
        // 签名字符串：METHOD\npathname?query\ntimestamp\nnonce
        String pathWithQuery = u.getPath();
        if (!TextUtils.isEmpty(u.getQuery())) pathWithQuery += "?" + u.getQuery();
        long ts = System.currentTimeMillis();
        String nonce = randomHex(16);
        String payload = method + "\n" + pathWithQuery + "\n" + ts + "\n" + nonce;
        String signature = hmacSha256Hex(SIGN_SECRET, payload);

        Request.Builder builder = new Request.Builder().url(urlStr);
        // 基础 UA
        builder.header("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36");
        builder.header("Accept", "application/json");
        builder.header("Referer", SITE + "/");
        // 客户端标识
        for (Map.Entry<String, String> e : CLIENT_HEADERS.entrySet()) {
            builder.header(e.getKey(), e.getValue());
        }
        // 签名
        builder.header("x-ai-movie-timestamp", String.valueOf(ts));
        builder.header("x-ai-movie-nonce", nonce);
        builder.header("x-ai-movie-signature", signature);

        if ("POST".equals(method)) {
            // OkHttp 3.x 签名: create(MediaType, String) — 注意参数顺序
            RequestBody rb = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), jsonBody == null ? "" : jsonBody);
            builder.post(rb);
        }

        Response resp = client.newCall(builder.build()).execute();
        if (!resp.isSuccessful() && resp.body() != null) {
            // 失败时打印一下便于调试
            String body = resp.body().string();
            throw new Exception("HTTP " + resp.code() + " " + resp.message() + " body=" + body);
        }
        return resp.body() == null ? "" : resp.body().string();
    }

    /** HMAC-SHA256 → hex */
    private static String hmacSha256Hex(String key, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : raw) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private String randomHex(int bytes) {
        byte[] buf = new byte[bytes];
        random.nextBytes(buf);
        StringBuilder sb = new StringBuilder();
        for (byte b : buf) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    // ==================== 解析工具 ====================

    private Vod parseCard(JSONObject c) {
        String id = c.optString("id");
        String title = c.optString("title");
        String pic = c.optString("poster_url");
        String remarks = c.optString("remarks");
        Vod vod = new Vod(id, title, pic, remarks);

        // content_kind 用来映射分类名
        String kind = c.optString("content_kind", "");
        if (!TextUtils.isEmpty(kind)) vod.setTypeName(kind);

        // year / area
        if (c.has("year")) vod.setVodYear(String.valueOf(c.optInt("year")));
        if (c.has("area")) vod.setVodArea(c.optString("area"));
        return vod;
    }

    private String joinJsonArray(JSONArray arr, String sep) {
        if (arr == null || arr.length() == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length(); i++) {
            if (i > 0) sb.append(sep);
            sb.append(arr.optString(i));
        }
        return sb.toString();
    }

    private String join(String sep, List<String> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(sep);
            sb.append(list.get(i));
        }
        return sb.toString();
    }
}
