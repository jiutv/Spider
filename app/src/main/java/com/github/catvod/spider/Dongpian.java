package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import okhttp3.ConnectionPool;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * 懂片帝 (dongpian.ai) 专用爬虫 v2
 *
 * 播放链路（关键）：
 *   dongpian detail API 给的 episodes[].urls.yjm3u8 是 zy.baipiaozhe.com 内部代理，
 *   对外返回 301 循环重定向。正确方式是：
 *   每集的 YJ token → player.baipiaozhe.com/v1/playback/resolve/<token> → 真实 CDN m3u8
 *
 *   优化：resolve 第一集 token 就能拿到全集 tokens 列表，后续集用并发 resolve 加速。
 *   每集 resolve 返回 30 条不同 provider 的线路，取 top-N 组成多播放源（$$$ 分隔）。
 *
 * 搜索：dongpian 无原生搜索 API（/v1/catalog/search 返回 500，后端未实现），
 *   退化为多 kind × 多页 browse catalog 本地过滤，匹配 title / normalized_title。
 */
public class Dongpian extends Spider {

    private static final String SITE = "https://dongpian.ai";
    private static final String PLAYBACK_RESOLVE = "https://player.baipiaozhe.com/v1/playback/resolve/";

    // 前端逆向的签名密钥
    private static final String SIGN_SECRET = "8b9a908a05eac640e1ee06f52acaa741bfe4ba9e004eeffdbeb635e532e06666";

    private static final Map<String, String> CLIENT_HEADERS = new HashMap<>();
    static {
        CLIENT_HEADERS.put("x-ai-movie-client-name", "movie-search-frontend");
        CLIENT_HEADERS.put("x-ai-movie-client-version", "1.0.0");
        CLIENT_HEADERS.put("x-ai-movie-build-version", "dongpiandi-v2026.09.14.12-8ea8477c2120-web");
        CLIENT_HEADERS.put("x-ai-movie-protocol-version", "2026-07-05.library-v2.playback-v1");
    }

    private static final List<String> KIND_IDS   = Arrays.asList("series", "movie", "anime", "variety", "short_drama");
    private static final List<String> KIND_NAMES = Arrays.asList("电视剧", "电影", "动漫", "综艺", "短剧");

    /** 每集选 top-N 不同 provider 作为多播放源 */
    private static final int TOP_PROVIDERS = 5;
    /** 并发 resolve 线程数 */
    private static final int RESOLVE_THREADS = 8;
    /** 并发 resolve 超时（秒） */
    private static final int RESOLVE_TIMEOUT_SEC = 15;
    /** 搜索遍历的分类页数上限（dongpian 无原生搜索 API，靠 browse catalog 本地过滤） */
    private static final int SEARCH_MAX_PAGES = 10;
    /** 单次 browse catalog 返回条数 */
    private static final int SEARCH_PAGE_LIMIT = 50;
    /** 精确匹配达到这个数量后提前停止（性能优化） */
    private static final int SEARCH_EARLY_EXACT_STOP = 20;
    /** 前 N 页优先精确匹配（热门内容基本在前几页，快速返回） */
    private static final int SEARCH_EXACT_FIRST_PAGES = 3;

    /** 带连接池的签名请求客户端 */
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .connectionPool(new ConnectionPool(5, 5, TimeUnit.MINUTES))
            .build();

    /** 无签名的外部请求客户端（baipiaozhe / CDN） */
    private final OkHttpClient plainClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .connectionPool(new ConnectionPool(5, 5, TimeUnit.MINUTES))
            .build();

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
            String resp = get(SITE + "/v1/feed/home");
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

            JSONObject root = new JSONObject(get(url.toString()));
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
            // 1) 拉 dongpian detail API（基础信息 + 第一集 token）
            JSONObject d = new JSONObject(get(SITE + "/v1/catalog/" + cardId));

            Vod vod = new Vod();
            vod.setVodId(d.optString("id", cardId));
            vod.setVodName(d.optString("title"));
            vod.setVodPic(d.optString("poster_url"));
            vod.setVodContent(d.optString("description"));
            vod.setTypeName(d.optString("content_kind", ""));
            vod.setVodYear(String.valueOf(d.optInt("year")));
            vod.setVodArea(d.optString("area"));
            vod.setVodRemarks(d.optString("remarks"));
            vod.setVodActor(joinJsonArray(d.optJSONArray("actors"), " / "));
            vod.setVodDirector(joinJsonArray(d.optJSONArray("directors"), " / "));

            // 2) 拿第一集 token（从 dongpian detail 里取）
            JSONArray dpEps = d.optJSONArray("episodes");
            if (dpEps == null || dpEps.length() == 0) {
                return Result.string(vod);
            }
            String firstToken = dpEps.getJSONObject(0).optString("token");
            if (TextUtils.isEmpty(firstToken)) {
                return Result.string(vod);
            }

            // 3) resolve 第一集 → 拿到全集 tokens + 多 provider 线路
            String firstResolveRaw = getPlain(PLAYBACK_RESOLVE + firstToken);
            JSONObject firstResolve = new JSONObject(firstResolveRaw);

            // 从 line_options 选 top-N 不同 provider（跳过 resolve:// 付费线路）
            JSONArray lineOpts = firstResolve.optJSONArray("line_options");
            ArrayList<Provider> topProviders = pickTopProviders(lineOpts, TOP_PROVIDERS);
            if (topProviders.isEmpty()) {
                // 兜底：只用第一集的默认线路
                String fallbackUrl = firstResolve.optString("url");
                if (!TextUtils.isEmpty(fallbackUrl)) {
                    ArrayList<String> fromList = new ArrayList<>();
                    ArrayList<String> urlGroupList = new ArrayList<>();
                    fromList.add("M3U8");
                    ArrayList<String> items = new ArrayList<>();
                    // 只有第一集有 url，其余集兜底
                    JSONArray allEps = firstResolve.optJSONArray("episodes");
                    if (allEps != null) {
                        for (int i = 0; i < allEps.length(); i++) {
                            JSONObject ep = allEps.getJSONObject(i);
                            String name = ep.optString("display_name");
                            if (TextUtils.isEmpty(name)) name = "第" + (i + 1) + "集";
                            items.add(name + "$" + (i == 0 ? fallbackUrl : ""));
                        }
                    }
                    urlGroupList.add(join("#", items));
                    vod.setVodPlayFrom("M3U8");
                    vod.setVodPlayUrl(join("$$$", urlGroupList));
                }
                return Result.string(vod);
            }

            // 4) 收集全集 tokens（从第一集 resolve 返回的 episodes 数组）
            JSONArray resolveEps = firstResolve.optJSONArray("episodes");
            ArrayList<String> allTokens = new ArrayList<>();
            ArrayList<String> allTitles = new ArrayList<>();
            // 第一集已经 resolve 过了
            allTokens.add(firstToken);
            allTitles.add(firstResolve.optJSONObject("current_episode").optString("display_name", "第1集"));
            if (resolveEps != null) {
                for (int i = 1; i < resolveEps.length(); i++) {
                    JSONObject ep = resolveEps.getJSONObject(i);
                    allTokens.add(ep.optString("token"));
                    String name = ep.optString("display_name");
                    if (TextUtils.isEmpty(name)) name = "第" + (i + 1) + "集";
                    allTitles.add(name);
                }
            }

            // 5) 并发 resolve 剩余集
            int totalEps = allTokens.size();
            // providerUrls[providerId] = [ep1_url, ep2_url, ...]
            Map<String, ArrayList<String>> providerUrls = new LinkedHashMap<>();
            for (Provider p : topProviders) {
                ArrayList<String> urls = new ArrayList<>(totalEps);
                for (int i = 0; i < totalEps; i++) urls.add("");  // 预填充空
                providerUrls.put(p.id, urls);
            }

            // 先填第一集的 url
            fillEpisodeUrls(providerUrls, topProviders, lineOpts, 0);

            // 并发 resolve 剩下集
            if (totalEps > 1) {
                ExecutorService executor = Executors.newFixedThreadPool(RESOLVE_THREADS);
                ArrayList<Future<String>> futures = new ArrayList<>();
                for (int i = 1; i < totalEps; i++) {
                    final String token = allTokens.get(i);
                    final int epIdx = i;
                    futures.add(executor.submit(new Callable<String>() {
                        @Override
                        public String call() {
                            try {
                                return getPlain(PLAYBACK_RESOLVE + token);
                            } catch (Exception e) {
                                return null;
                            }
                        }
                    }));
                }

                for (int i = 0; i < futures.size(); i++) {
                    try {
                        String raw = futures.get(i).get(RESOLVE_TIMEOUT_SEC, TimeUnit.SECONDS);
                        if (raw != null) {
                            JSONObject r = new JSONObject(raw);
                            JSONArray lo = r.optJSONArray("line_options");
                            fillEpisodeUrls(providerUrls, topProviders, lo, i + 1);
                        }
                    } catch (Exception ignored) {}
                }
                executor.shutdown();
            }

            // 6) 组装 VodPlayFrom / VodPlayUrl
            ArrayList<String> fromList = new ArrayList<>();
            ArrayList<String> urlGroupList = new ArrayList<>();
            for (Provider p : topProviders) {
                fromList.add(p.label);
                ArrayList<String> items = new ArrayList<>();
                ArrayList<String> urls = providerUrls.get(p.id);
                for (int i = 0; i < allTitles.size(); i++) {
                    String url = i < urls.size() ? urls.get(i) : "";
                    if (!TextUtils.isEmpty(url)) {
                        items.add(allTitles.get(i) + "$" + url);
                    }
                }
                urlGroupList.add(join("#", items));
            }

            vod.setVodPlayFrom(join("$$$", fromList));
            vod.setVodPlayUrl(join("$$$", urlGroupList));
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
        ArrayList<Vod> list = new ArrayList<>();
        if (TextUtils.isEmpty(key)) return Result.string(list);
        if (TextUtils.isEmpty(pg)) pg = "1";

        try {
            String keyLower = key.toLowerCase();

            // 并发：5 kind × SEARCH_MAX_PAGES 页的 browse catalog 请求
            ExecutorService executor = Executors.newFixedThreadPool(RESOLVE_THREADS);
            ArrayList<Future<String>> futures = new ArrayList<>();

            for (String kind : KIND_IDS) {
                for (int page = 1; page <= SEARCH_MAX_PAGES; page++) {
                    final String url = SITE + "/v1/browse/catalog?page=" + page
                            + "&limit=" + SEARCH_PAGE_LIMIT + "&sort=heat_desc&kind=" + kind;
                    futures.add(executor.submit(new Callable<String>() {
                        @Override
                        public String call() {
                            try {
                                return get(url);
                            } catch (Exception e) {
                                return null;
                            }
                        }
                    }));
                }
            }
            executor.shutdown();

            // 收集结果并过滤
            HashSet<String> seenIds = new HashSet<>();
            ArrayList<Vod> exactMatches = new ArrayList<>();
            ArrayList<Vod> fuzzyMatches = new ArrayList<>();

            for (Future<String> fut : futures) {
                String raw;
                try {
                    raw = fut.get(RESOLVE_TIMEOUT_SEC, TimeUnit.SECONDS);
                } catch (Exception e) {
                    continue;
                }
                if (TextUtils.isEmpty(raw)) continue;

                JSONObject root;
                try {
                    root = new JSONObject(raw);
                } catch (Exception e) {
                    continue;
                }
                JSONArray cards = root.optJSONArray("cards");
                if (cards == null || cards.length() == 0) continue;

                for (int i = 0; i < cards.length(); i++) {
                    JSONObject card = cards.optJSONObject(i);
                    if (card == null) continue;
                    String id = card.optString("id");
                    if (TextUtils.isEmpty(id) || seenIds.contains(id)) continue;

                    String title = card.optString("title", "");
                    String normalized = card.optString("normalized_title", "");
                    String titleLower = title.toLowerCase();
                    String normLower = normalized.toLowerCase();

                    boolean exact = title.equals(key) || normalized.equals(key)
                            || titleLower.equals(keyLower) || normLower.equals(keyLower);
                    boolean fuzzy = title.contains(key) || normalized.contains(key)
                            || titleLower.contains(keyLower) || normLower.contains(keyLower);

                    if (exact) {
                        seenIds.add(id);
                        exactMatches.add(parseCard(card));
                    } else if (fuzzy) {
                        seenIds.add(id);
                        fuzzyMatches.add(parseCard(card));
                    }
                }
            }

            // 精确匹配在前，模糊匹配在后
            list.addAll(exactMatches);
            list.addAll(fuzzyMatches);

            // 分页返回（TVBox searchContent 的 pg 是客户端分页）
            int pageNum = Integer.parseInt(pg);
            int pageSize = 20;
            int fromIdx = (pageNum - 1) * pageSize;
            int toIdx = Math.min(fromIdx + pageSize, list.size());
            if (fromIdx >= list.size()) {
                return Result.string(new ArrayList<>());
            }
            ArrayList<Vod> pageResult = new ArrayList<>(list.subList(fromIdx, toIdx));
            return Result.string(pageResult);

        } catch (Exception e) {
            e.printStackTrace();
            return Result.string(list);
        }
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        try {
            Map<String, String> headers = new HashMap<>();
            headers.put("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36");
            headers.put("Accept", "*/*");
            return Result.get().url(id).header(headers).parse(0).string();
        } catch (Exception e) {
            return Result.get().url(id).parse(0).string();
        }
    }

    // ==================== Provider 选择 + URL 填充 ====================

    /** 从 line_options 里选 top-N 不同 provider（跳过 resolve:// 付费线路） */
    private ArrayList<Provider> pickTopProviders(JSONArray lineOptions, int topN) {
        ArrayList<Provider> result = new ArrayList<>();
        if (lineOptions == null) return result;
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < lineOptions.length(); i++) {
            JSONObject line = lineOptions.optJSONObject(i);
            if (line == null) continue;
            String url = line.optString("url", "");
            // 跳过付费/需二次 resolve 的线路
            if (url.startsWith("resolve://")) continue;
            String pid = line.optString("provider_id");
            if (TextUtils.isEmpty(pid) || seen.contains(pid)) continue;
            seen.add(pid);
            String label = line.optString("display_label");
            if (TextUtils.isEmpty(label)) label = line.optString("label");
            if (TextUtils.isEmpty(label)) label = pid;
            result.add(new Provider(pid, label, line.optString("play_from", ""), line.optInt("score", 0)));
            if (result.size() >= topN) break;
        }
        return result;
    }

    /** 根据一集的 line_options，填充到 providerUrls[providerId][epIdx] */
    private void fillEpisodeUrls(Map<String, ArrayList<String>> providerUrls,
                                  ArrayList<Provider> providers,
                                  JSONArray lineOptions,
                                  int epIdx) {
        if (lineOptions == null) return;
        // 建 pid → Provider 快速查找
        Map<String, Provider> pidMap = new HashMap<>();
        for (Provider p : providers) pidMap.put(p.id, p);

        for (int i = 0; i < lineOptions.length(); i++) {
            JSONObject line = lineOptions.optJSONObject(i);
            if (line == null) continue;
            String pid = line.optString("provider_id");
            Provider p = pidMap.get(pid);
            if (p == null) continue;
            String url = line.optString("url", "");
            if (TextUtils.isEmpty(url) || url.startsWith("resolve://")) continue;

            ArrayList<String> slot = providerUrls.get(pid);
            if (slot != null && epIdx >= 0 && epIdx < slot.size()) {
                if (TextUtils.isEmpty(slot.get(epIdx))) {
                    slot.set(epIdx, url);
                }
            }
        }
    }

    /** Provider 内部数据类 */
    private static class Provider {
        final String id;
        final String label;
        final String playFrom;
        final int score;

        Provider(String id, String label, String playFrom, int score) {
            this.id = id;
            this.label = label;
            this.playFrom = playFrom;
            this.score = score;
        }
    }

    // ==================== 网络 + HMAC 签名 ====================

    private String get(String url) throws Exception {
        return request("GET", url, null);
    }

    /** 无签名 GET（baipiaozhe resolve / CDN） */
    private String getPlain(String url) throws Exception {
        Request req = new Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "application/json")
                .header("Referer", "https://player.baipiaozhe.com/yjplayer.html")
                .build();
        try (Response resp = plainClient.newCall(req).execute()) {
            return resp.body() == null ? "" : resp.body().string();
        }
    }

    private String request(String method, String urlStr, String jsonBody) throws Exception {
        java.net.URL u = new java.net.URL(urlStr);
        String pathWithQuery = u.getPath();
        if (!TextUtils.isEmpty(u.getQuery())) pathWithQuery += "?" + u.getQuery();

        long ts = System.currentTimeMillis();
        String nonce = randomHex(16);
        String payload = method + "\n" + pathWithQuery + "\n" + ts + "\n" + nonce;
        String signature = hmacSha256Hex(SIGN_SECRET, payload);

        Request.Builder builder = new Request.Builder().url(urlStr);
        builder.header("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36");
        builder.header("Accept", "application/json");
        builder.header("Referer", SITE + "/");
        for (Map.Entry<String, String> e : CLIENT_HEADERS.entrySet()) {
            builder.header(e.getKey(), e.getValue());
        }
        builder.header("x-ai-movie-timestamp", String.valueOf(ts));
        builder.header("x-ai-movie-nonce", nonce);
        builder.header("x-ai-movie-signature", signature);

        if ("POST".equals(method)) {
            RequestBody rb = RequestBody.create(
                    MediaType.parse("application/json; charset=utf-8"),
                    jsonBody == null ? "" : jsonBody);
            builder.post(rb);
        }

        try (Response resp = client.newCall(builder.build()).execute()) {
            if (!resp.isSuccessful() && resp.body() != null) {
                String body = resp.body().string();
                throw new Exception("HTTP " + resp.code() + " " + resp.message() + " body=" + body);
            }
            return resp.body() == null ? "" : resp.body().string();
        }
    }

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

        String kind = c.optString("content_kind", "");
        if (!TextUtils.isEmpty(kind)) vod.setTypeName(kind);

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
