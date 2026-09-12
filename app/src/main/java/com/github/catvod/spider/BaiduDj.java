package com.github.catvod.spider;

import android.content.Context;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Filter;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 百度短剧爬虫 —— 精选短剧/好看短剧
 * 分类结构: 大类(综合/题材) → 小类(通过 Filter 下拉实现)
 * 数据来源: search API (免费, 无需 version 签名, 翻页稳定)
 *
 * 注意: search API 只做标题关键词匹配, 不是所有分类名都能搜到结果
 *       搜不到或只有 1 条的分类用 fallback 到相关关键词
 */
public class BaiduDj extends Spider {

    private static final String UA = "Mozilla/5.0 (Linux; Android 11; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.91 Mobile Safari/537.36";

    private static final String HOST = "https://mbd.baidu.com";
    private static final String DETAIL_HOST = "https://sv.baidu.com";
    private static final String SEARCH_URL = "/feedapi/v1/videoserver/playlets/search?service=bdbox";
    private static final String DETAIL_URL = "/haokan/ui-video/playlet/rec/detail?log=vhk&tn=1020970b&ctn=1008350n&blur=1";
    private static final String PLAY_URL = "/appui/api?cmd=video/relate&log=vhk&tn=1020970b&ctn=1008350n&blur=1";

    private static final Map<String, Integer> CLARITY_ORDER = new LinkedHashMap<>();

    static {
        CLARITY_ORDER.put("蓝光", 1);
        CLARITY_ORDER.put("超清", 2);
        CLARITY_ORDER.put("标清", 3);
    }

    // ============ 分类层级定义 ============
    // 大类只有两个: 综合 / 题材 (侧边栏显示)
    // 小类通过 Filter 下拉选择, value.v 是实际传给 search API 的 query 关键词

    /**
     * 综合类小类 —— 7 个
     * 格式: {显示名, 实际搜索关键词}
     * 备注里标注的是 API 实测结果数
     */
    private static final String[][] SUB_ZHONGHE = {
            {"全部", "全部"},           // 27 条
            {"热播", "热播"},           // 14 条
            {"新剧", "新"},             // 搜"新剧"只有 1 条广告 → "新" 2006 条
            {"连续剧", "热播"},         // 搜"连续剧"只有 1 条 → "热播" 14 条
            {"限时免费", "热播"},       // 搜不到 → "热播"
            {"精选", "热播"},           // 搜不到 → "热播"
            {"独播", "热播"}            // 搜不到 → "热播"
    };

    /**
     * 题材类小类 —— 43 个
     * 按搜索结果数从多到少排序 (实测 totalCount)
     */
    private static final String[][] SUB_TICAI = {
            {"重生", "重生"},           // 4640
            {"总裁", "总裁"},           // 3795
            {"逆袭", "逆袭"},           // 2198
            {"闪婚", "闪婚"},           // 1546
            {"萌宝", "萌宝"},           // 1537
            {"复仇", "复仇"},           // 970
            {"穿越", "穿越"},           // 849
            {"神医", "神医"},           // 832
            {"战神", "战神"},           // 818
            {"赘婿", "赘婿"},           // 728
            {"都市", "都市"},           // 722
            {"年代", "年代"},           // 329
            {"替嫁", "替嫁"},           // 504
            {"恋爱", "恋爱"},           // 587
            {"职场", "职场"},           // 390
            {"王妃", "王妃"},           // 417
            {"家族", "家族"},           // 215
            {"鉴宝", "鉴宝"},           // 341
            {"先婚后爱", "先婚后爱"},   // 205
            {"神豪", "神豪"},           // 149
            {"民国", "民国"},           // 128
            {"异能", "异能"},           // 63
            {"校园", "校园"},           // 69  (青春校园 → fallback)
            {"种田", "种田"},           // 49
            {"甜宠", "甜宠"},           // 46
            {"真假千金", "真假千金"},   // 53
            {"商战", "商战"},           // 33
            {"虐恋", "虐恋"},           // 35
            {"热血", "热血"},           // 24
            {"宅斗", "宅斗"},           // 7   (宫斗宅斗 → fallback)
            {"冒险", "冒险"},           // 8   (历史架空 → fallback)
            {"玄幻", "玄幻"},           // 6
            {"古代言情", "王妃"},       // 搜不到 → fallback 王妃
            {"现代言情", "恋爱"},       // 搜不到 → fallback 恋爱
            {"穿越重生", "重生"},       // 搜不到 → fallback 重生
            {"家庭伦理", "家族"},       // 搜不到 → fallback 家族
            {"武侠武打", "热血"},       // 搜不到 → fallback 热血
            {"历史架空", "冒险"},       // 搜不到 → fallback 冒险
            {"军旅战争", "热血"},       // 搜不到 → fallback 热血
            {"霸总", "霸总"},           // 308
            {"科幻", "未来"},           // 搜"科幻"只有 1 条 → "未来" 220 条
            {"悬疑", "悬疑"},           // 2
            {"奇幻", "奇幻"}            // 5
    };

    // ============ 网络请求 ============

    private HashMap<String, String> getHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", UA);
        headers.put("Content-Type", "application/x-www-form-urlencoded");
        headers.put("Accept", "application/json");
        return headers;
    }

    /** 封装 POST form: body 仅含一个 "data" 字段, 值为 JSON 字符串 */
    private JsonObject requestListOrSearch(String url, String innerJson) {
        try {
            HashMap<String, String> params = new HashMap<>();
            params.put("data", innerJson);
            String resp = OkHttp.post(url, params, getHeaders()).getBody();
            if (resp == null || resp.isEmpty()) return new JsonObject();
            JsonElement ele = JsonParser.parseString(resp);
            return ele.isJsonObject() ? ele.getAsJsonObject() : new JsonObject();
        } catch (Exception e) {
            return new JsonObject();
        }
    }

    /** 封装一般 POST form: 多个 form 字段 */
    private JsonObject requestForm(String url, HashMap<String, String> params) {
        try {
            String resp = OkHttp.post(url, params, getHeaders()).getBody();
            if (resp == null || resp.isEmpty()) return new JsonObject();
            JsonElement ele = JsonParser.parseString(resp);
            return ele.isJsonObject() ? ele.getAsJsonObject() : new JsonObject();
        } catch (Exception e) {
            return new JsonObject();
        }
    }

    // ============ Spider 接口实现 ============

    @Override
    public void init(Context context) throws Exception {
    }

    @Override
    public void init(Context context, String extend) throws Exception {
        init(context);
    }

    /**
     * 首页分类 —— 返回大类 + 每个大类对应的小类 Filter
     * 侧边栏显示 "综合" "题材" 两个大类
     * 点进大类后顶部 Filter 下拉显示该大类下所有小类
     */
    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        classes.add(new Class("zh", "综合"));
        classes.add(new Class("tc", "题材"));

        LinkedHashMap<String, List<Filter>> filters = new LinkedHashMap<>();
        filters.put("zh", buildSubFilter(SUB_ZHONGHE));
        filters.put("tc", buildSubFilter(SUB_TICAI));

        return Result.get().classes(classes).filters(filters).string();
    }

    /** 构造 Filter —— 小类下拉选项, key="kw" */
    private List<Filter> buildSubFilter(String[][] subs) {
        List<Filter.Value> values = new ArrayList<>();
        for (String[] sub : subs) {
            values.add(new Filter.Value(sub[0], sub[1]));
        }
        return Arrays.asList(new Filter("kw", "分类", values));
    }

    /**
     * 首页推荐 —— 搜 "新" 取前 40 条 (翻 2 页)
     * 不能用 "新剧" (只有 1 条广告), 也不能走 categoryContent (tid 不是 zh/tc)
     */
    @Override
    public String homeVideoContent() throws Exception {
        List<Vod> vods = new ArrayList<>();
        com.google.gson.Gson gson = new com.google.gson.Gson();
        // 翻 2 页, 每页 API 返回最多 20 条, 合计最多 40 条
        for (int page = 1; page <= 2; page++) {
            String result = searchContent("新", false, String.valueOf(page));
            if (result == null || result.isEmpty()) break;
            JsonObject root = JsonParser.parseString(result).getAsJsonObject();
            JsonArray arr = root.has("list") ? root.getAsJsonArray("list") : new JsonArray();
            for (int i = 0; i < arr.size(); i++) {
                Vod v = gson.fromJson(arr.get(i), Vod.class);
                if (v != null) vods.add(v);
            }
        }
        // 截断到最多 40 条, 避免极端情况
        if (vods.size() > 40) vods = vods.subList(0, 40);
        return Result.string(vods);
    }

    /**
     * 分类列表 —— 从 extend.get("kw") 取 Filter 选中的小类搜索关键词
     * 如果用户没选过小类, 综合类默认 "热播", 题材类默认 "重生"
     */
    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        String keyword;
        if (extend != null && extend.containsKey("kw") && !extend.get("kw").isEmpty()) {
            keyword = extend.get("kw");
        } else {
            keyword = "zh".equals(tid) ? "热播" : "重生";
        }
        return searchContent(keyword, false, pg);
    }

    /** 视频详情 */
    @Override
    public String detailContent(List<String> ids) throws Exception {
        if (ids == null || ids.isEmpty()) return Result.string(new ArrayList<>());
        String id = ids.get(0);

        HashMap<String, String> params = new HashMap<>();
        params.put("playlet_id", id);
        params.put("vid", "undefined");

        String url = DETAIL_HOST + DETAIL_URL;
        JsonObject res = requestForm(url, params);

        JsonObject dthtml = res.has("data") ? res.getAsJsonObject("data") : new JsonObject();
        JsonArray vids = dthtml.has("vid_list") ? dthtml.getAsJsonArray("vid_list") : new JsonArray();
        if (vids.size() == 0) return Result.string(new ArrayList<>());

        List<String> playItems = new ArrayList<>();
        for (int i = 0; i < vids.size(); i++) {
            String vid = vids.get(i).getAsString();
            playItems.add("第" + (i + 1) + "集$" + vid);
        }

        Vod vod = new Vod();
        vod.setVodId(id);
        vod.setVodName(dthtml.has("playlet_title") ? dthtml.get("playlet_title").getAsString() : "未知剧名");
        vod.setVodPic(dthtml.has("playlet_poster") ? dthtml.get("playlet_poster").getAsString() : "");
        vod.setVodContent(dthtml.has("description") ? dthtml.get("description").getAsString() : "");
        String hotVal = dthtml.has("hot_value") ? dthtml.get("hot_value").toString() : "0";
        vod.setVodRemarks("共" + vids.size() + "集 热度值:" + hotVal);
        vod.setVodDirector(dthtml.has("tag_text") ? dthtml.get("tag_text").getAsString() : "");
        vod.setVodYear(dthtml.has("create_time") ? dthtml.get("create_time").getAsString() : "");
        vod.setVodPlayFrom("百度短剧");
        vod.setVodPlayUrl(String.join("#", playItems));
        return Result.string(vod);
    }

    /** 播放解析 */
    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        HashMap<String, String> params = new HashMap<>();
        params.put("method", "post");
        params.put("vid", id);

        String url = DETAIL_HOST + PLAY_URL;
        JsonObject res = requestForm(url, params);

        JsonObject video = res.has("video/relate")
                && res.getAsJsonObject("video/relate").has("data")
                && res.getAsJsonObject("video/relate").getAsJsonObject("data").has("cur_video")
                ? res.getAsJsonObject("video/relate").getAsJsonObject("data").getAsJsonObject("cur_video")
                : null;

        if (video == null || !video.has("clarityUrl")) return Result.error("获取播放链接失败");

        JsonArray clarityUrl = video.getAsJsonArray("clarityUrl");
        List<LinkItem> links = new ArrayList<>();
        for (JsonElement el : clarityUrl) {
            JsonObject item = el.getAsJsonObject();
            if (!item.has("url")) continue;
            String title = item.has("title") ? item.get("title").getAsString() : "";
            String link = item.get("url").getAsString();
            if (link == null || link.isEmpty()) continue;
            int order = CLARITY_ORDER.containsKey(title) ? CLARITY_ORDER.get(title) : 999;
            links.add(new LinkItem(title, link, order));
        }
        if (links.isEmpty()) return Result.error("暂无可用播放地址");
        links.sort((a, b) -> Integer.compare(a.order, b.order));

        List<String> flat = new ArrayList<>();
        for (LinkItem it : links) { flat.add(it.title); flat.add(it.url); }

        com.google.gson.JsonObject headerObj = new com.google.gson.JsonObject();
        headerObj.addProperty("User-Agent", UA);
        headerObj.addProperty("Referer", HOST);
        String headerJson = new com.google.gson.Gson().toJson(headerObj);

        com.google.gson.JsonObject result = new com.google.gson.JsonObject();
        result.addProperty("parse", 0);
        result.add("url", new com.google.gson.Gson().toJsonTree(flat));
        result.addProperty("header", headerJson);
        return result.toString();
    }

    /** 搜索 —— 2 参数委托给 3 参数 */
    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return searchContent(key, quick, "1");
    }

    /**
     * 搜索 —— 核心方法
     * categoryContent 和 homeVideoContent 都调这里
     */
    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        int page;
        try { page = Integer.parseInt(pg); } catch (Exception e) { page = 1; }
        if (page <= 0) page = 1;

        JsonObject inner = new JsonObject();
        inner.addProperty("query", key);
        inner.addProperty("page", page);
        JsonArray attribute = new JsonArray();
        attribute.add("title");
        inner.add("attribute", attribute);
        inner.addProperty("fe_page_type", "search");
        JsonObject extra = new JsonObject();
        extra.addProperty("tab_id", "216");
        extra.addProperty("flow_tabid", "13");
        extra.addProperty("shortplay_source", "feed");
        extra.addProperty("from", "feed");
        extra.addProperty("tab_type", "搜索");
        extra.addProperty("sub_template", "playlet_search_result");
        inner.add("extra", extra);

        JsonObject res = requestListOrSearch(HOST + SEARCH_URL, inner.toString());

        List<Vod> vods = new ArrayList<>();
        JsonArray itemList = res.has("data") && res.getAsJsonObject("data").has("itemList")
                ? res.getAsJsonObject("data").getAsJsonArray("itemList") : new JsonArray();
        for (JsonElement el : itemList) {
            JsonObject it = el.getAsJsonObject();
            String nid = it.has("nid") ? it.get("nid").getAsString() : "";
            String vodId = "";
            if (nid.contains("_")) {
                String[] arr = nid.split("_", 2);
                if (arr.length > 1) vodId = arr[1];
            }
            String vodName = it.has("title") ? it.get("title").getAsString() : "未知标题";
            String vodPic = it.has("img") ? it.get("img").getAsString() : "";
            String collNum = it.has("collNum") ? it.get("collNum").getAsString() : "0";
            String vodContent = it.has("description") ? it.get("description").getAsString() : "";
            Vod v = new Vod(vodId, vodName, vodPic, collNum + "集");
            v.setVodContent(vodContent);
            vods.add(v);
        }

        JsonObject dataObj = res.has("data") ? res.getAsJsonObject("data") : new JsonObject();
        int total = dataObj.has("totalCount") ? dataObj.get("totalCount").getAsInt() : vods.size();
        int limit = 20;
        int pagecount = total > 0 ? (int) Math.ceil((double) total / limit) : page;
        return Result.string(page, pagecount, limit, total, vods);
    }

    // ============ 工具类 ============

    private static class LinkItem {
        String title;
        String url;
        int order;
        LinkItem(String title, String url, int order) {
            this.title = title; this.url = url; this.order = order;
        }
    }
}
