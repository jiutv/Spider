package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;
import android.util.Base64;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Filter;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.HashSet;
import java.util.Set;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 云帧享 / 秒播影视 (baiyunvideo app 真实 API)
 *
 * 后端事实 (反编译 app + 实际测试 2026-09-13):
 *
 *   唯一活 API: GET /vc/api/search/{keyword}/{page}.json
 *     每页 50 条, 纯标题关键词匹配 (不是结构化分类 API)
 *     POST /vc/api/search/ 全部返回 {"code":1,"msg":"请输入搜索关键词"}, 不解析任何 body
 *     /cache/zhaopian/... 分类缓存全 404
 *
 *   index_url: https://ss.trgfd.cn/cache/index/com.baiyunvideo.app.json
 *     channels = [首页,剧集,电影,综艺,动漫,少儿,纪录片]
 *     xinRanks[].types = [全部,大陆剧,美剧,韩剧,英剧,泰剧,日剧,台剧,港剧,电影,
 *                         国内综艺,国外综艺,国漫,日漫,少儿,纪录片]
 *     ranks = [热播榜,飙升榜,热搜榜,新片榜,剧集,电影,综艺,动漫,少儿,纪录片]
 *
 *   策略 (对齐 app 真实分类结构):
 *     7 大类 → 每类映射到几组 xinRanks types 当搜索关键词池
 *     翻 MAX_SEARCH_PAGES 页, 去重合并, 按 score/year 排序, 本地分页
 *     再加 Filter (榜单 + 题材 + 年份 + 排序) 二次过滤
 *
 *   app 真实行为:
 *     搜"大陆剧" → 标题含"大陆剧"的影片 (typeName 混合: 电影/剧集/综艺...)
 *     搜"热播榜" → 标题含"热播榜"的影片 (typeName 混合, 剧集偏多)
 *     搜"纪录片" → 标题含"纪录片"的影片 (2 条真 typeName=纪录片)
 *     → 后端就这么设计的, 不是 bug
 */
public class YunZhenXiang extends Spider {

    /** 硬编码 AES-256 key, 从 APK libkeys.so 提取 */
    private static final String AES_KEY = "qvn1u7FCfu8uaolp980i8uVHVS8Dxih7";
    private static final String INDEX_URL = "https://ss.trgfd.cn/cache/index/com.baiyunvideo.app.json";

    /** 榜单关键词 (首页 + 每个 channel 都可以搜) */
    private static final String[] RANK_KEYWORDS = {"热播榜", "飙升榜", "热搜榜", "新片榜", "高分榜"};

    /**
     * 7 大类 → 搜索关键词池 (对齐 app channels + xinRanks types)
     * 每个关键词翻 MAX_SEARCH_PAGES 页去重合并
     *
     * 注: 不再做 typeName 精滤 —— 后端搜索 API 是标题关键词匹配, 精滤反而把内容过滤没了
     */
    private static final String[][][] CATEGORY_CONFIG = {
            // {tid,    显示名,  搜索关键词池...}
            {{"首页",  "首页",   RANK_KEYWORDS}},          // 首页用所有榜单
            {{"剧集",  "剧集",   new String[]{"大陆剧","美剧","韩剧","英剧","泰剧","日剧","台剧","港剧","热播榜"}}},
            {{"电影",  "电影",   new String[]{"电影","热播榜","高分榜"}}},
            {{"综艺",  "综艺",   new String[]{"国内综艺","国外综艺","热播榜"}}},
            {{"动漫",  "动漫",   new String[]{"国漫","日漫","热播榜"}}},
            {{"少儿",  "少儿",   new String[]{"少儿"}}},
            {{"纪录片","纪录片", new String[]{"纪录片"}}}
    };

    private static final int MAX_SEARCH_PAGES = 3; // 每个关键词翻 3 页 = 150 条, 合并去重

    private String textURL = "https://js.trgfd.cn";
    private String resourceURL = "https://img.zqykfz.cn";
    private String version = "2.7.0";
    private final Map<String, String> headers = new HashMap<>();
    private volatile boolean initialized = false;
    private String ext = "";

    // ============ 解密 ============

    private String decrypt(String str) {
        try {
            byte[] data = Base64.decode(str.trim(), 0);
            if (data.length < 28) return "";
            byte[] nonce = Arrays.copyOfRange(data, 0, 12);
            byte[] ciphertextWithTag = Arrays.copyOfRange(data, 12, data.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,
                    new SecretKeySpec(AES_KEY.getBytes("UTF-8"), "AES"),
                    new GCMParameterSpec(128, nonce));
            return new String(cipher.doFinal(ciphertextWithTag), "UTF-8");
        } catch (Exception e) { return ""; }
    }

    // ============ HTTP ============

    private String fetch(String url) {
        try {
            Map<String, String> h = new HashMap<>(headers);
            String result = OkHttp.string(url, h);
            return TextUtils.isEmpty(result) ? null : result;
        } catch (Exception e) { return null; }
    }

    // ============ 初始化 ============

    private synchronized void ensureInit() {
        if (initialized) return;
        initialized = true;
        try {
            String indexJson = fetch(INDEX_URL);
            if (indexJson != null) {
                JSONObject index = new JSONObject(indexJson);
                JSONObject app = index.optJSONObject("app");
                if (app != null) {
                    String textUrl = app.optString("textURL", "");
                    if (!TextUtils.isEmpty(textUrl)) textURL = textUrl;
                    String imgUrl = app.optString("resourceURL", "");
                    if (TextUtils.isEmpty(imgUrl)) imgUrl = app.optString("img", "");
                    if (!TextUtils.isEmpty(imgUrl)) resourceURL = imgUrl;
                    version = app.optString("latestVersion", version);
                }
                JSONArray qudao = index.optJSONArray("qudao");
                if (qudao != null && qudao.length() > 0) {
                    String banben = qudao.getJSONObject(0).optString("banben", version);
                    if (!TextUtils.isEmpty(banben)) version = banben;
                }
            }
            if (ext != null && ext.trim().startsWith("{")) {
                JSONObject obj = new JSONObject(ext);
                if (obj.has("host") && !TextUtils.isEmpty(obj.optString("host"))) {
                    textURL = obj.optString("host");
                }
            }
        } catch (Exception ignored) {}
    }

    // ============ 工具 ============

    private String md5(String str) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] bytes = md.digest(str.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b & 0xff));
            return sb.toString();
        } catch (Exception e) { return ""; }
    }

    /**
     * 多关键词翻页合并 + 去重
     * 不做 typeName 精滤 —— 后端搜索是标题关键词匹配
     */
    private List<JSONObject> fetchAll(String[] keywords) {
        List<JSONObject> merged = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        for (String kw : keywords) {
            for (int pg = 1; pg <= MAX_SEARCH_PAGES; pg++) {
                try {
                    String encKw = URLEncoder.encode(kw, StandardCharsets.UTF_8.name()).replace("+", "%20");
                    String url = textURL + "/vc/api/search/" + encKw + "/" + pg + ".json";
                    String json = fetch(url);
                    if (json == null) break;
                    JSONArray arr = new JSONArray(json);
                    if (arr.length() == 0) break;
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject item = arr.getJSONObject(i);
                        String id = String.valueOf(item.optInt("videoId", 0));
                        if (!seenIds.add(id)) continue;
                        merged.add(item);
                    }
                } catch (Exception e) { break; }
            }
        }
        return merged;
    }

    /** Filter 二次过滤 + 排序 */
    private List<JSONObject> applyFilters(List<JSONObject> items,
                                           String rankFilter, String classFilter,
                                           String yearFilter, String sort) {
        List<JSONObject> result = new ArrayList<>();
        for (JSONObject it : items) {
            // 榜单过滤: rank 用 rankKeyword 字段, 如果搜索词包含 rank 就保留
            if (!TextUtils.isEmpty(rankFilter) && !"全部".equals(rankFilter)) {
                String rk = it.optString("rankKeyword", "");
                boolean matched = false;
                for (String s : rk.split(",")) {
                    if (s.trim().equals(rankFilter)) { matched = true; break; }
                }
                if (!matched) continue;
            }
            if (!TextUtils.isEmpty(classFilter) && !"全部".equals(classFilter)) {
                String cls = it.optString("class", "");
                boolean matched = false;
                for (String c : cls.split(",")) {
                    if (c.trim().equals(classFilter)) { matched = true; break; }
                }
                if (!matched) continue;
            }
            if (!TextUtils.isEmpty(yearFilter) && !"全部".equals(yearFilter)) {
                if (!yearFilter.equals(it.optString("year", ""))) continue;
            }
            result.add(it);
        }

        // 排序
        if ("最新".equals(sort)) {
            Collections.sort(result, new Comparator<JSONObject>() {
                @Override
                public int compare(JSONObject a, JSONObject b) {
                    return b.optString("year", "0").compareTo(a.optString("year", "0"));
                }
            });
        } else {
            // 最热 (默认)
            Collections.sort(result, new Comparator<JSONObject>() {
                @Override
                public int compare(JSONObject a, JSONObject b) {
                    return Double.compare(b.optDouble("score", 0), a.optDouble("score", 0));
                }
            });
        }
        return result;
    }

    // ============ Spider 接口 ============

    @Override
    public void init(Context context, String extend) {
        try { super.init(context, extend); } catch (Exception ignored) {}
        this.ext = extend;
        headers.put("User-Agent", "baiyunvideo-android " + version);
        headers.put("Accept-Language", "zh-CN,zh;q=0.9");
    }

    /**
     * 7 大类 + 4 组 Filter (榜单/题材/年份/排序)
     * 和 app UI 对齐:
     *   首页 tab = 热播榜/飙升榜/热搜榜/新片榜/高分榜
     *   剧集 tab + 下拉筛选 (榜单/地区/题材/年份/排序)
     */
    @Override
    public String homeContent(boolean filter) {
        ensureInit();
        try {
            ArrayList<Class> classes = new ArrayList<>();
            for (String[][] cfg : CATEGORY_CONFIG) {
                String tid = cfg[0][0];
                String name = cfg[0][1];
                if (!"首页".equals(tid)) classes.add(new Class(tid, name));
            }

            // Filter 选项
            ArrayList<Filter.Value> rankValues = new ArrayList<>();
            rankValues.add(new Filter.Value("全部", ""));
            for (String s : RANK_KEYWORDS) rankValues.add(new Filter.Value(s, s));

            ArrayList<Filter.Value> classValues = new ArrayList<>();
            classValues.add(new Filter.Value("全部", ""));
            for (String s : new String[]{"爱情", "古装", "战争", "喜剧", "家庭", "犯罪", "武侠", "冒险",
                    "动作", "恐怖", "悬疑", "剧情", "奇幻", "科幻", "动画", "真人秀", "惊悚", "搞笑"}) {
                classValues.add(new Filter.Value(s, s));
            }

            ArrayList<Filter.Value> yearValues = new ArrayList<>();
            yearValues.add(new Filter.Value("全部", ""));
            for (int y = 2026; y >= 2015; y--) {
                yearValues.add(new Filter.Value(String.valueOf(y), String.valueOf(y)));
            }

            ArrayList<Filter.Value> sortValues = new ArrayList<>();
            sortValues.add(new Filter.Value("最热", "最热"));
            sortValues.add(new Filter.Value("最新", "最新"));

            Filter rankFilter = new Filter("rank", "榜单", rankValues);
            Filter classFilter = new Filter("class", "题材", classValues);
            Filter yearFilter = new Filter("year", "年份", yearValues);
            Filter sortFilter = new Filter("sort", "排序", sortValues);

            LinkedHashMap<String, List<Filter>> filters = new LinkedHashMap<>();
            for (String[][] cfg : CATEGORY_CONFIG) {
                String tid = cfg[0][0];
                if (!"首页".equals(tid)) {
                    filters.put(tid, Arrays.asList(rankFilter, classFilter, yearFilter, sortFilter));
                }
            }

            return Result.get().classes(classes).filters(filters).string();
        } catch (Exception e) { return "{}"; }
    }

    /** 首页 —— 5 个榜单关键词混合, 取前 20, 打乱 */
    @Override
    public String homeVideoContent() {
        ensureInit();
        try {
            List<JSONObject> items = fetchAll(RANK_KEYWORDS);
            Collections.shuffle(items, new Random());
            List<Vod> vods = itemsToVods(items.subList(0, Math.min(20, items.size())));
            return Result.string(vods);
        } catch (Exception e) {
            return Result.string(new ArrayList<>());
        }
    }

    /** 分类列表 —— 关键词池合并 + Filter 二次过滤 + 本地分页 */
    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        ensureInit();
        try {
            String[][] cfg = null;
            for (String[][] c : CATEGORY_CONFIG) {
                if (c[0][0].equals(tid)) { cfg = c; break; }
            }
            if (cfg == null) return Result.string(new ArrayList<>());

            List<String> kwList = new ArrayList<>();
            for (int i = 1; i < cfg.length; i++) {
                for (String kw : cfg[i]) kwList.add(kw);
            }
            if (kwList.isEmpty()) kwList.add(tid);

            List<JSONObject> all = fetchAll(kwList.toArray(new String[0]));

            String rf = extend != null ? extend.get("rank") : null;
            String cf = extend != null ? extend.get("class") : null;
            String yf = extend != null ? extend.get("year") : null;
            String sort = extend != null ? extend.get("sort") : "最热";

            List<JSONObject> filtered = applyFilters(all, rf, cf, yf, sort);

            int page;
            try { page = Integer.parseInt(pg); } catch (Exception e) { page = 1; }
            if (page <= 0) page = 1;
            int limit = 20;
            int pagecount = Math.max(1, (int) Math.ceil((double) filtered.size() / limit));
            int start = (page - 1) * limit;
            int end = Math.min(start + limit, filtered.size());
            List<JSONObject> pageItems = (start >= filtered.size())
                    ? new ArrayList<>()
                    : filtered.subList(start, end);

            return Result.string(page, pagecount, limit, filtered.size(), itemsToVods(pageItems));
        } catch (Exception e) {
            return Result.string(new ArrayList<>());
        }
    }

    private List<Vod> itemsToVods(List<JSONObject> items) {
        List<Vod> vods = new ArrayList<>();
        for (JSONObject it : items) {
            String id = String.valueOf(it.optInt("videoId", 0));
            String name = it.optString("videoName");
            String pic = it.optString("fengmiantu");
            if (!pic.startsWith("http")) pic = resourceURL + pic;
            String remarks = it.optString("serialDesc");
            double score = it.optDouble("score", 0);
            if (score > 0) remarks = (TextUtils.isEmpty(remarks) ? "" : remarks + " ") + "评分 " + score;
            vods.add(new Vod(id, name, pic, remarks));
        }
        return vods;
    }

    /** 详情 */
    @Override
    public String detailContent(List<String> ids) {
        ensureInit();
        if (ids == null || ids.isEmpty()) return Result.string(new ArrayList<>());
        try {
            String id = ids.get(0);
            int idInt;
            try { idInt = Integer.parseInt(id); } catch (Exception e) { idInt = 0; }
            String url = textURL + "/cache/videos/" + (idInt / 1000) + "/" + id + ".json"
                    + "?version=" + version + "&baoming=com.baiyunvideo.app&channel=fenxiang";

            String raw = fetch(url);
            if (TextUtils.isEmpty(raw)) return Result.string(new ArrayList<>());
            String plain = decrypt(raw.trim());
            if (TextUtils.isEmpty(plain)) return Result.string(new ArrayList<>());

            JSONObject data = new JSONObject(plain);
            Vod vod = new Vod();
            vod.setVodId(id);
            vod.setVodName(data.optString("videoName"));
            String pic = data.optString("fengmiantu");
            if (!pic.startsWith("http")) pic = resourceURL + pic;
            vod.setVodPic(pic);
            vod.setTypeName(data.optString("class"));
            vod.setVodRemarks(data.optString("remarks"));
            vod.setVodContent(String.format("主演：%s\n地区：%s\n简介：%s",
                    data.optString("actor", "未知"), data.optString("region", ""), data.optString("blurb", "")));
            vod.setVodYear(data.optString("year"));
            vod.setVodArea(data.optString("region"));
            vod.setVodActor(data.optString("actor"));
            vod.setVodDirector(data.optString("director"));

            JSONArray playUrlList = data.optJSONArray("playUrlList");
            ArrayList<String> episodes = new ArrayList<>();
            if (playUrlList != null) {
                for (int i = 0; i < playUrlList.length(); i++) {
                    JSONObject ep = playUrlList.getJSONObject(i);
                    String name = ep.optString("name", "第" + (i + 1) + "集");
                    String ji = ep.optString("ji", String.valueOf(i + 1));
                    episodes.add(name + "$" + id + "@@" + ji + "@@" + i);
                }
            }
            vod.setVodPlayFrom("云帧享");
            vod.setVodPlayUrl(TextUtils.join("#", episodes));
            return Result.string(vod);
        } catch (Exception e) { return Result.string(new ArrayList<>()); }
    }

    /** 播放 */
    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        ensureInit();
        try {
            String[] parts = id.split("@@");
            if (parts.length < 3) return Result.get().url("").parse(0).string();
            String sid = parts[0], ji = parts[1], jiIndex = parts[2];

            StringBuilder sb = new StringBuilder();
            Random random = new Random();
            for (int i = 0; i < 16; i++) sb.append("abcdefghijklmnopqrstuvwxyz0123456789".charAt(random.nextInt(36)));
            String androidId = sb.toString();
            String vuk = md5(sid + AES_KEY);

            String url = textURL + "/vc/api/video/playurl"
                    + "?sid=" + sid + "&ji=" + ji + "&jiIndex=" + jiIndex
                    + "&t=0&y=0&isjiid=1&androidId=" + androidId
                    + "&version=" + version + "&baoming=com.baiyunvideo.app&channel=fenxiang";

            Map<String, String> h = new HashMap<>(headers);
            h.put("vuk", vuk);
            String resp = OkHttp.string(url, h);
            if (TextUtils.isEmpty(resp)) return Result.get().url("").parse(0).string();

            JSONObject root = new JSONObject(resp);
            JSONObject data = root.optJSONObject("data");
            String playUrl = data != null ? data.optString("url", "") : "";

            Map<String, String> header = new HashMap<>();
            header.put("User-Agent", "baiyunvideo-android " + version);
            return Result.get().url(playUrl).parse(0).header(header).string();
        } catch (Exception e) { return Result.get().url("").parse(0).string(); }
    }

    @Override
    public String searchContent(String key, boolean quick) {
        return searchContent(key, quick, "1");
    }

    /** 用户主动搜索 —— 直接返回 */
    @Override
    public String searchContent(String key, boolean quick, String pg) {
        ensureInit();
        try {
            int page;
            try { page = Integer.parseInt(pg); } catch (Exception e) { page = 1; }
            if (page <= 0) page = 1;
            String encKey = URLEncoder.encode(key, StandardCharsets.UTF_8.name()).replace("+", "%20");
            String json = fetch(textURL + "/vc/api/search/" + encKey + "/" + page + ".json");
            if (json == null) return Result.string(new ArrayList<>());
            JSONArray arr = new JSONArray(json);
            List<Vod> vods = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject it = arr.getJSONObject(i);
                String id = String.valueOf(it.optInt("videoId", 0));
                vods.add(new Vod(id, it.optString("videoName"), it.optString("fengmiantu"), it.optString("class")));
            }
            return Result.string(page, page + 1, 50, 9999, vods);
        } catch (Exception e) { return Result.string(new ArrayList<>()); }
    }
}
