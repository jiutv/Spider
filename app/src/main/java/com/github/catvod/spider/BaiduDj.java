package com.github.catvod.spider;

import android.content.Context;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Json;
import com.github.catvod.utils.Util;
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
 * 百度短剧爬虫 —— 移植自百度短剧 JS 规则
 * 基类: Spider
 */
public class BaiduDj extends Spider {

    private static final String UA = "Mozilla/5.0 (Linux; Android 11; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.91 Mobile Safari/537.36";

    private static final String HOST = "https://mbd.baidu.com";
    private static final String DETAIL_HOST = "https://sv.baidu.com";
    private static final String LIST_URL = "/feedapi/v1/videoserver/playlets/list?service=bdbox";
    private static final String SEARCH_URL = "/feedapi/v1/videoserver/playlets/search?service=bdbox";
    private static final String DETAIL_URL = "/haokan/ui-video/playlet/rec/detail?log=vhk&tn=1020970b&ctn=1008350n&blur=1";
    private static final String PLAY_URL = "/appui/api?cmd=video/relate&log=vhk&tn=1020970b&ctn=1008350n&blur=1";

    private static final Map<String, Integer> CLARITY_ORDER = new LinkedHashMap<>();

    static {
        CLARITY_ORDER.put("蓝光", 1);
        CLARITY_ORDER.put("超清", 2);
        CLARITY_ORDER.put("标清", 3);
    }

    /** 综合分类 (全部能搜到有效结果) */
    private static final List<String> HE = Arrays.asList("全部", "热播", "新剧");

    /**
     * 题材分类 (全部用 search API 验证过有结果, 按 total 从大到小排序)
     */
    private static final List<String> TICAI_LIST = Arrays.asList(
            "重生", "总裁", "逆袭", "闪婚", "萌宝", "复仇",
            "穿越", "神医", "战神", "赘婿", "都市", "年代",
            "恋爱", "职场", "替嫁", "霸总", "王妃", "家族",
            "神豪", "异能", "先婚后爱", "民国", "甜宠", "种田",
            "鉴宝", "商战", "玄幻", "虐恋", "热血", "奇幻",
            "真假千金", "冒险", "科幻", "悬疑"
    );

    private HashMap<String, String> getHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", UA);
        headers.put("Content-Type", "application/x-www-form-urlencoded");
        headers.put("Accept", "application/json");
        return headers;
    }

    private HashMap<String, String> getPlayHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", UA);
        return headers;
    }

    /**
     * 封装 POST form 请求: body 仅含一个 "data" 字段，值为 JSON 字符串
     */
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

    /**
     * 封装一般 POST form 请求: 多个 form 字段
     */
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

    @Override
    public void init(Context context) throws Exception {
    }

    @Override
    public void init(Context context, String extend) throws Exception {
        init(context);
    }

    /**
     * 返回分类列表 (homeContent 对应 JS 的 home)
     */
    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        for (String name : HE) {
            classes.add(new Class(name, name));
        }
        for (String name : TICAI_LIST) {
            String typeId = "全部".equals(name) ? "全部题材" : name;
            classes.add(new Class(typeId, name));
        }
        LinkedHashMap<String, List<com.github.catvod.bean.Filter>> filters = new LinkedHashMap<>();
        return Result.get().classes(classes).filters(filters).string();
    }

    /**
     * 首页推荐视频 (homeVideoContent 对应 JS 的 homeVod)
     * JS 里 homeVod 调用 category("新剧",1,{},{}) 再取前 12 条
     */
    @Override
    public String homeVideoContent() throws Exception {
        String categoryJson = categoryContent("新剧", "1", false, new HashMap<>());
        if (categoryJson == null || categoryJson.isEmpty()) return Result.string(new ArrayList<>());
        JsonObject root = JsonParser.parseString(categoryJson).getAsJsonObject();
        JsonArray arr = root.has("list") ? root.getAsJsonArray("list") : new JsonArray();
        int size = Math.min(arr.size(), 12);
        List<Vod> vods = new ArrayList<>();
        com.google.gson.Gson gson = new com.google.gson.Gson();
        for (int i = 0; i < size; i++) {
            Vod v = gson.fromJson(arr.get(i), Vod.class);
            if (v != null) vods.add(v);
        }
        return Result.string(vods);
    }

    /**
     * 分类列表
     * ⚠️ feedapi/v1/videoserver/playlets/list 接口需要 version 签名校验, 无法破解
     * 改而委托 search 接口 (不需要 version, 翻页完全有效, 零重叠验证通过)
     *
     * 综合分类 (HE) → keyword="短剧" (全部热门短剧)
     * 题材分类 (TICAI) → keyword=题材名 (如 "战神"/"神医"/"都市" 等)
     */
    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        // 所有分类的 tid 本身就是有效的 search keyword (已全部验证过)
        // 综合分类: 全部(27)/热播(14)/新剧(1) → 直接用名字搜
        // 题材分类: 重生(4640)/总裁(3795)/... → 直接用题材名搜
        // 如果 tid 是 "全部题材" (homeContent 里 TICAI 的全部映射), 用 "全部"
        String keyword;
        if ("全部题材".equals(tid)) {
            keyword = "全部";
        } else {
            keyword = tid;
        }
        return searchContent(keyword, false, pg);
    }

    /**
     * 视频详情 (对应 JS 的 detail)
     */
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
        if (vids.size() == 0) {
            return Result.string(new ArrayList<>());
        }

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

    /**
     * 播放解析 (对应 JS 的 play)
     */
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

        if (video == null || !video.has("clarityUrl")) {
            return Result.error("获取播放链接失败");
        }

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
        for (LinkItem it : links) {
            flat.add(it.title);
            flat.add(it.url);
        }

        // 返回 JSON: parse=0, url=[title,url,...], header={User-Agent, Referer}
        com.google.gson.JsonObject headerObj = new com.google.gson.JsonObject();
        headerObj.addProperty("User-Agent", UA);
        headerObj.addProperty("Referer", HOST);
        String headerJson = new com.google.gson.Gson().toJson(headerObj);

        // 构造 Result
        com.google.gson.JsonObject result = new com.google.gson.JsonObject();
        result.addProperty("parse", 0);
        result.add("url", new com.google.gson.Gson().toJsonTree(flat));
        result.addProperty("header", headerJson);
        return result.toString();
    }

    /**
     * 搜索 (对应 JS 的 search)
     * 注意: CatVod 引擎会先调用 2 参数版本, 必须委托给 3 参数版本!
     */
    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        int page = 0;
        try { page = Integer.parseInt(pg); } catch (Exception e) { page = 1; }
        if (page <= 0) page = 1;

        JsonArray attribute = new JsonArray();
        attribute.add("title");

        JsonObject extra = new JsonObject();
        extra.addProperty("tab_id", "216");
        extra.addProperty("flow_tabid", "13");
        extra.addProperty("shortplay_source", "feed");
        extra.addProperty("from", "feed");
        extra.addProperty("tab_type", "搜索");
        extra.addProperty("sub_template", "playlet_search_result");

        JsonObject inner = new JsonObject();
        inner.addProperty("query", key);
        inner.addProperty("page", page);
        inner.add("attribute", attribute);
        inner.addProperty("fe_page_type", "search");
        inner.add("extra", extra);

        // 注意: JS search 里没有 timestamp/version (只有 category 才有)
        // 不要加, 否则可能被 API 拒绝

        String url = HOST + SEARCH_URL;
        JsonObject res = requestListOrSearch(url, inner.toString());

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

        // search 接口有 totalCount, 可以精确算 pagecount
        JsonObject dataObj = res.has("data") ? res.getAsJsonObject("data") : new JsonObject();
        int total = dataObj.has("totalCount") ? dataObj.get("totalCount").getAsInt() : vods.size();
        int limit = 20;
        int pagecount = total > 0 ? (int) Math.ceil((double) total / limit) : page;
        return Result.string(page, pagecount, limit, total, vods);
    }

    // ---------- 工具方法 ----------

    private Vod parseVodFromItem(JsonObject it) {
        String vodId = it.has("collId") ? it.get("collId").getAsString() : "";
        String vodName = it.has("title") ? it.get("title").getAsString() : "未知标题";
        String vodPic = it.has("img") ? it.get("img").getAsString() : "";
        String vodRemarks = it.has("updateStatus") ? it.get("updateStatus").getAsString() : "";
        Vod v = new Vod(vodId, vodName, vodPic, vodRemarks);
        String vodContent = it.has("description") ? it.get("description").getAsString() : "";
        v.setVodContent(vodContent);
        return v;
    }

    private static class LinkItem {
        String title;
        String url;
        int order;

        LinkItem(String title, String url, int order) {
            this.title = title;
            this.url = url;
            this.order = order;
        }
    }
}
