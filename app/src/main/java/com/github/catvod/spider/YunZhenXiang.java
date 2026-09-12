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
 * 云帧享 / 秒播影视 (baiyunvideo)
 *
 * 后端限制 (实测):
 *   只有一个可用 API: /vc/api/search/{keyword}/{page}.json
 *   不是结构化分类 API, 是关键词搜索
 *   但搜索结果自带 typeName / class / region / year / score 元数据
 *
 *   原始 ZJAPI 分类缓存 /cache/zhaopian/... 全部 404 (服务端下线了)
 *
 * 策略:
 *   7 大类 (首页/剧集/电影/综艺/动漫/少儿/纪录片)
 *   每大类下 4 组 Filter (题材/地区/年份/排序)
 *   用多关键词合并搜索 → 按 typeName 过滤大类 → 按 class/region/year 过滤 → 排序 → 本地分页
 */
public class YunZhenXiang extends Spider {

    /** AES-256 key, 从 APK libkeys.so 硬提取 */
    private static final String AES_KEY = "qvn1u7FCfu8uaolp980i8uVHVS8Dxih7";
    private static final String INDEX_URL = "https://ss.trgfd.cn/cache/index/com.baiyunvideo.app.json";

    /**
     * 7 大类 → 搜索关键词池 (每个关键词翻 MAX_SEARCH_PAGES 页)
     * 合并后按 typeName 精确过滤大类
     */
    private static final String[][][] CATEGORY_CONFIG = {
            // {tid, 显示名, typeName匹配值, 搜索关键词池...}
            {{"首页", "首页", ""}, {"全部", "热门", "推荐"}},
            {{"剧集", "剧集", "剧集"}, {"剧集", "电视剧", "大陆剧", "韩剧", "日剧", "美剧", "港剧", "台剧", "泰剧", "英剧"}},
            {{"电影", "电影", "电影"}, {"电影", "新片", "大片"}},
            {{"综艺", "综艺", "综艺"}, {"综艺", "真人秀", "脱口秀", "选秀"}},
            {{"动漫", "动漫", "动漫"}, {"动漫", "动画", "国漫", "日漫"}},
            {{"少儿", "少儿", "少儿"}, {"少儿", "儿童", "动画电影"}},
            {{"纪录片", "纪录片", "纪录片"}, {"纪录片", "纪实"}}
    };

    private static final int MAX_SEARCH_PAGES = 3; // 每个关键词翻 3 页, 每页 50 条

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
        } catch (Exception e) {
            return "";
        }
    }

    // ============ HTTP ============

    private String fetch(String url) {
        try {
            Map<String, String> h = new HashMap<>(headers);
            String result = OkHttp.string(url, h);
            return TextUtils.isEmpty(result) ? null : result;
        } catch (Exception e) {
            return null;
        }
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
     * 多关键词搜索合并 + 按 typeName 过滤
     * 返回原始 JSONArray list (每条带 typeName/class/region/year/score)
     */
    private List<JSONObject> fetchAndFilter(String[] keywords, String typeNameFilter) {
        List<JSONObject> merged = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        for (String kw : keywords) {
            for (int pg = 1; pg <= MAX_SEARCH_PAGES; pg++) {
                try {
                    String url = textURL + "/vc/api/search/" + kw + "/" + pg + ".json";
                    String json = fetch(url);
                    if (json == null) break;
                    JSONArray arr = new JSONArray(json);
                    if (arr.length() == 0) break;
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject item = arr.getJSONObject(i);
                        String id = String.valueOf(item.optInt("videoId", 0));
                        if (!seenIds.add(id)) continue;
                        // 按 typeName 过滤 (空 = 不过滤, 用于首页)
                        if (!TextUtils.isEmpty(typeNameFilter)) {
                            String tn = item.optString("typeName", "");
                            if (!typeNameFilter.equals(tn)) continue;
                        }
                        merged.add(item);
                    }
                } catch (Exception e) {
                    break;
                }
            }
        }
        return merged;
    }

    /** 根据筛选条件再过滤一次 */
    private List<JSONObject> applyFilters(List<JSONObject> items,
                                           String classFilter, String regionFilter,
                                           String yearFilter, String sort) {
        List<JSONObject> result = new ArrayList<>();
        for (JSONObject it : items) {
            // 题材过滤: class 字段是逗号分隔, 包含即匹配
            if (!TextUtils.isEmpty(classFilter) && !"全部".equals(classFilter)) {
                String cls = it.optString("class", "");
                boolean matched = false;
                for (String c : cls.split(",")) {
                    if (c.trim().equals(classFilter)) { matched = true; break; }
                }
                if (!matched) continue;
            }
            // 地区过滤
            if (!TextUtils.isEmpty(regionFilter) && !"全部".equals(regionFilter)) {
                String reg = it.optString("region", "");
                if (!reg.contains(regionFilter)) continue;
            }
            // 年份过滤
            if (!TextUtils.isEmpty(yearFilter) && !"全部".equals(yearFilter)) {
                String yr = it.optString("year", "");
                if (!yr.equals(yearFilter)) continue;
            }
            result.add(it);
        }

        // 排序
        if ("最热".equals(sort)) {
            Collections.sort(result, new Comparator<JSONObject>() {
                @Override
                public int compare(JSONObject a, JSONObject b) {
                    double sa = a.optDouble("score", 0);
                    double sb = b.optDouble("score", 0);
                    return Double.compare(sb, sa);
                }
            });
        } else {
            // 最新: 按 year 降序
            Collections.sort(result, new Comparator<JSONObject>() {
                @Override
                public int compare(JSONObject a, JSONObject b) {
                    String ya = a.optString("year", "0");
                    String yb = b.optString("year", "0");
                    return yb.compareTo(ya);
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
     * 首页分类 —— 7 大类 + 每大类 4 组 Filter
     *
     * Filter 结构 (和截图里的 UI 一致):
     *   题材: 全部 / 爱情 / 古装 / 战争 / 喜剧 / 家庭 / 犯罪 / 武侠 / 冒险 / 动作 / 恐怖 / 悬疑
     *   地区: 全部 / 大陆 / 香港 / 台湾 / 日本 / 韩国 / 美国 / 泰国 / 其他
     *   年份: 全部 / 2026 / 2025 / 2024 / ... / 2019
     *   排序: 最热 / 最新
     */
    @Override
    public String homeContent(boolean filter) {
        ensureInit();
        try {
            ArrayList<Class> classes = new ArrayList<>();
            for (String[][] cfg : CATEGORY_CONFIG) {
                String tid = cfg[0][0];
                String name = cfg[0][1];
                // "首页" 不放 CatVod 分类栏 (首页由 homeVideoContent 处理)
                if (!"首页".equals(tid)) {
                    classes.add(new Class(tid, name));
                }
            }

            // Filter 选项 (从搜索结果统计而来)
            ArrayList<Filter.Value> classValues = new ArrayList<>();
            classValues.add(new Filter.Value("全部", ""));
            for (String s : new String[]{"爱情", "古装", "战争", "喜剧", "家庭", "犯罪", "武侠", "冒险",
                    "动作", "恐怖", "悬疑", "剧情", "奇幻", "科幻", "动画", "真人秀", "惊悚", "同性", "搞笑"}) {
                classValues.add(new Filter.Value(s, s));
            }

            ArrayList<Filter.Value> regionValues = new ArrayList<>();
            regionValues.add(new Filter.Value("全部", ""));
            for (String s : new String[]{"大陆", "香港", "台湾", "日本", "韩国", "美国", "泰国",
                    "英国", "法国", "德国", "意大利", "西班牙", "印度", "其他"}) {
                regionValues.add(new Filter.Value(s, s));
            }

            ArrayList<Filter.Value> yearValues = new ArrayList<>();
            yearValues.add(new Filter.Value("全部", ""));
            for (int y = 2026; y >= 2015; y--) {
                yearValues.add(new Filter.Value(String.valueOf(y), String.valueOf(y)));
            }

            ArrayList<Filter.Value> sortValues = new ArrayList<>();
            sortValues.add(new Filter.Value("最热", "最热"));
            sortValues.add(new Filter.Value("最新", "最新"));

            Filter classFilter = new Filter("class", "题材", classValues);
            Filter regionFilter = new Filter("region", "地区", regionValues);
            Filter yearFilter = new Filter("year", "年份", yearValues);
            Filter sortFilter = new Filter("sort", "排序", sortValues);

            LinkedHashMap<String, List<Filter>> filters = new LinkedHashMap<>();
            for (String[][] cfg : CATEGORY_CONFIG) {
                String tid = cfg[0][0];
                if (!"首页".equals(tid)) {
                    filters.put(tid, Arrays.asList(classFilter, regionFilter, yearFilter, sortFilter));
                }
            }

            return Result.get().classes(classes).filters(filters).string();
        } catch (Exception e) {
            return "{}";
        }
    }

    /** 首页推荐 —— 用 "全部" 关键词混取, 不分 typeName */
    @Override
    public String homeVideoContent() {
        ensureInit();
        try {
            List<JSONObject> items = fetchAndFilter(new String[]{"热门", "推荐", "全部"}, null);
            // 首页取前 20 条, 混合展示
            Collections.shuffle(items, new Random()); // 打乱让不同类型都出现
            List<Vod> vods = itemsToVods(items.subList(0, Math.min(20, items.size())));
            return Result.string(vods);
        } catch (Exception e) {
            return Result.string(new ArrayList<>());
        }
    }

    /** 分类列表 —— 多关键词合并 + typeName + class/region/year 过滤 + 排序 + 本地分页 */
    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        ensureInit();
        try {
            // 找到这个 tid 对应的配置
            String[][] cfg = null;
            for (String[][] c : CATEGORY_CONFIG) {
                if (c[0][0].equals(tid)) { cfg = c; break; }
            }
            if (cfg == null) return Result.string(new ArrayList<>());

            String typeNameFilter = cfg[0][2]; // 精确 typeName 值
            String[] keywords = cfg[1]; // 搜索关键词池

            // 1. 多关键词合并 + 按 typeName 过滤
            List<JSONObject> all = fetchAndFilter(keywords, typeNameFilter);

            // 2. 按 Filter 条件再过滤
            String classFilter = extend != null ? extend.get("class") : null;
            String regionFilter = extend != null ? extend.get("region") : null;
            String yearFilter = extend != null ? extend.get("year") : null;
            String sort = extend != null ? extend.get("sort") : "最热";

            List<JSONObject> filtered = applyFilters(all, classFilter, regionFilter, yearFilter, sort);

            // 3. 本地分页
            int page;
            try { page = Integer.parseInt(pg); } catch (Exception e) { page = 1; }
            if (page <= 0) page = 1;
            int limit = 20;
            int pagecount = Math.max(1, (int) Math.ceil((double) filtered.size() / limit));
            int start = (page - 1) * limit;
            int end = Math.min(start + limit, filtered.size());

            List<JSONObject> pageItems;
            if (start >= filtered.size()) {
                pageItems = new ArrayList<>();
            } else {
                pageItems = filtered.subList(start, end);
            }

            List<Vod> vods = itemsToVods(pageItems);
            return Result.string(page, pagecount, limit, filtered.size(), vods);
        } catch (Exception e) {
            return Result.string(new ArrayList<>());
        }
    }

    /** JSON item → Vod */
    private List<Vod> itemsToVods(List<JSONObject> items) {
        List<Vod> vods = new ArrayList<>();
        for (JSONObject it : items) {
            String id = String.valueOf(it.optInt("videoId", 0));
            String name = it.optString("videoName");
            String pic = it.optString("fengmiantu");
            if (!pic.startsWith("http")) pic = resourceURL + pic;
            String remarks = it.optString("serialDesc");
            double score = it.optDouble("score", 0);
            if (score > 0) {
                remarks = (TextUtils.isEmpty(remarks) ? "" : remarks + " ") + "评分 " + score;
            }
            Vod v = new Vod(id, name, pic, remarks);
            vods.add(v);
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
                    + "?version=" + version
                    + "&baoming=com.baiyunvideo.app"
                    + "&channel=fenxiang";

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
                    data.optString("actor", "未知"),
                    data.optString("region", ""),
                    data.optString("blurb", "")));
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
        } catch (Exception e) {
            return Result.string(new ArrayList<>());
        }
    }

    /** 播放 */
    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        ensureInit();
        try {
            String[] parts = id.split("@@");
            if (parts.length < 3) return Result.get().url("").parse(0).string();

            String sid = parts[0];
            String ji = parts[1];
            String jiIndex = parts[2];

            StringBuilder sb = new StringBuilder();
            Random random = new Random();
            for (int i = 0; i < 16; i++) {
                sb.append("abcdefghijklmnopqrstuvwxyz0123456789".charAt(random.nextInt(36)));
            }
            String androidId = sb.toString();
            String vuk = md5(sid + AES_KEY);

            String url = textURL + "/vc/api/video/playurl"
                    + "?sid=" + sid + "&ji=" + ji + "&jiIndex=" + jiIndex
                    + "&t=0&y=0&isjiid=1&androidId=" + androidId
                    + "&version=" + version
                    + "&baoming=com.baiyunvideo.app&channel=fenxiang";

            Map<String, String> h = new HashMap<>(headers);
            h.put("vuk", vuk);
            String resp = OkHttp.string(url, h);
            if (TextUtils.isEmpty(resp)) return Result.get().url("").parse(0).string();

            JSONObject root = new JSONObject(resp);
            JSONObject data = root.optJSONObject("data");
            String playUrl = "";
            if (data != null) {
                playUrl = data.optString("url", "");
            }

            Map<String, String> header = new HashMap<>();
            header.put("User-Agent", "baiyunvideo-android " + version);
            return Result.get().url(playUrl).parse(0).header(header).string();
        } catch (Exception e) {
            return Result.get().url("").parse(0).string();
        }
    }

    /** 搜索 —— 用户主动搜某个关键词, 直接返回 */
    @Override
    public String searchContent(String key, boolean quick) {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) {
        ensureInit();
        try {
            int page;
            try { page = Integer.parseInt(pg); } catch (Exception e) { page = 1; }
            if (page <= 0) page = 1;

            String json = fetch(textURL + "/vc/api/search/" + key + "/" + page + ".json");
            if (json == null) return Result.string(new ArrayList<>());

            JSONArray arr = new JSONArray(json);
            List<Vod> vods = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject it = arr.getJSONObject(i);
                String id = String.valueOf(it.optInt("videoId", 0));
                String name = it.optString("videoName");
                String pic = it.optString("fengmiantu");
                if (!pic.startsWith("http")) pic = resourceURL + pic;
                Vod v = new Vod(id, name, pic, it.optString("class"));
                vods.add(v);
            }
            return Result.string(page, page + 1, 50, 9999, vods);
        } catch (Exception e) {
            return Result.string(new ArrayList<>());
        }
    }
}
