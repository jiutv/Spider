package com.github.catvod.spider;

import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Filter;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 宅男影视 (m.kptv.us) 短剧爬虫
 * 接口: https://bf.xoxowin86cisyap.com/api.php/provide/vod/  (PHP vod 标准格式)
 * 短剧分类 (来自 /api/web 的 short 字段):
 *   反转爽文,68 | 穿越年代,66 | 现代言情,67 | 古装仙侠,72 | 都市脑洞,71
 */
public class ZhaiNanYS extends Spider {

    /** 短剧 PHP vod API 基地址 */
    private static final String API_BASE = "https://bf.xoxowin86cisyap.com/api.php/provide/vod/";

    /** 短剧分类定义: type_id -> type_name */
    private static final LinkedHashMap<String, String> SHORT_CLASSES = new LinkedHashMap<>();

    static {
        // 首页推荐用的反转爽文放第一个, CatVod 会自动取第一个当首页分类
        SHORT_CLASSES.put("68", "反转爽文");
        SHORT_CLASSES.put("66", "穿越年代");
        SHORT_CLASSES.put("67", "现代言情");
        SHORT_CLASSES.put("72", "古装仙侠");
        SHORT_CLASSES.put("71", "都市脑洞");
    }

    /** 默认每个分类第一页数量 */
    private static final int PAGE_SIZE = 20;

    // ---------- 首页分类 ----------

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        for (Map.Entry<String, String> e : SHORT_CLASSES.entrySet()) {
            classes.add(new Class(e.getKey(), e.getValue()));
        }
        LinkedHashMap<String, List<Filter>> filters = new LinkedHashMap<>();
        return Result.string(classes, filters);
    }

    // ---------- 首页推荐视频 (homeVideoContent) ----------

    @Override
    public String homeVideoContent() throws Exception {
        // 反转爽文 前 12 条作为首页推荐
        String url = API_BASE + "?ac=list&t=68&page=1&limit=12";
        JSONObject root = fetchJson(url);
        List<Vod> vods = parseVodList(root);
        return Result.string(vods);
    }

    // ---------- 分类列表 ----------

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        int page = parsePage(pg);
        String url = API_BASE + "?ac=list&t=" + tid + "&page=" + page + "&limit=" + PAGE_SIZE;
        JSONObject root = fetchJson(url);
        List<Vod> vods = parseVodList(root);

        int total = root.optInt("total", 0);
        int pagecount = root.optInt("pagecount", 0);
        if (pagecount == 0 && total > 0) {
            pagecount = (int) Math.ceil((double) total / PAGE_SIZE);
        }
        return Result.string(page, pagecount, PAGE_SIZE, total, vods);
    }

    // ---------- 详情 ----------

    @Override
    public String detailContent(List<String> ids) throws Exception {
        if (ids == null || ids.isEmpty()) return Result.string(new Vod());
        String id = ids.get(0);
        String url = API_BASE + "?ac=detail&ids=" + id;
        JSONObject root = fetchJson(url);
        JSONArray list = root.optJSONArray("list");
        if (list == null || list.length() == 0) return Result.string(new Vod());

        JSONObject item = list.getJSONObject(0);
        Vod vod = new Vod();
        vod.setVodId(String.valueOf(item.optInt("vod_id", 0)));
        vod.setVodName(item.optString("vod_name"));
        vod.setVodPic(item.optString("vod_pic"));
        vod.setVodRemarks(item.optString("vod_remarks"));
        vod.setVodClass(item.optString("vod_class"));
        vod.setVodActor(item.optString("vod_actor"));
        vod.setVodDirector(item.optString("vod_director"));
        vod.setVodYear(item.optString("vod_year"));
        vod.setVodArea(item.optString("vod_area"));
        vod.setVodContent(item.optString("vod_content"));

        // 播放源 (PHP vod 标准字段)
        String playFrom = item.optString("vod_play_from");
        String playUrl = item.optString("vod_play_url");
        // PHP vod 的 play_from 里可能多个源用 $ 分隔, 取第一个; play_url 用 # 分隔各源
        if (!TextUtils.isEmpty(playFrom) && !TextUtils.isEmpty(playUrl)) {
            String[] fromArr = playFrom.split("\\$");
            String[] urlArr = playUrl.split("#");
            // 只取第一个播放源, CatVod 可以直接播
            String firstFrom = fromArr.length > 0 ? fromArr[0] : "";
            String firstUrl = urlArr.length > 0 ? urlArr[0] : "";
            vod.setVodPlayFrom(firstFrom);
            vod.setVodPlayUrl(firstUrl);
        }

        return Result.string(vod);
    }

    // ---------- 播放 ----------

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        // PHP vod 返回的 id 格式就是 "剧集名$m3u8直链", 直接返回直链
        String url = id;
        if (!TextUtils.isEmpty(url) && url.contains("$")) {
            url = url.substring(url.lastIndexOf('$') + 1);
        }
        return Result.get().url(url).string();
    }

    // ---------- 搜索 ----------

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        int page = parsePage(pg);
        String url = API_BASE + "?ac=list&wd=" + URLEncoder.encode(key, "UTF-8") + "&page=" + page + "&limit=" + PAGE_SIZE;
        JSONObject root = fetchJson(url);
        List<Vod> vods = parseVodList(root);
        int total = root.optInt("total", 0);
        int pagecount = root.optInt("pagecount", 0);
        if (pagecount == 0 && total > 0) {
            pagecount = (int) Math.ceil((double) total / PAGE_SIZE);
        }
        return Result.string(page, pagecount, PAGE_SIZE, total, vods);
    }

    // ---------- 工具方法 ----------

    /** HTTP GET 拿 JSON */
    private JSONObject fetchJson(String url) {
        try {
            Map<String, String> header = getHeader();
            String text = OkHttp.string(url, header);
            if (TextUtils.isEmpty(text)) return new JSONObject();
            return new JSONObject(text);
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    /** 解析 PHP vod list 为 CatVod Vod */
    private List<Vod> parseVodList(JSONObject root) {
        List<Vod> vods = new ArrayList<>();
        if (root == null) return vods;
        JSONArray arr = root.optJSONArray("list");
        if (arr == null) return vods;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject it = arr.optJSONObject(i);
            if (it == null) continue;
            String id = String.valueOf(it.optInt("vod_id", 0));
            String name = it.optString("vod_name");
            String pic = it.optString("vod_pic");
            if (TextUtils.isEmpty(pic)) pic = it.optString("vod_pic_thumb");
            String remarks = it.optString("vod_remarks");
            if (TextUtils.isEmpty(id) || "0".equals(id)) continue;
            Vod v = new Vod(id, name, pic, remarks);
            v.setVodYear(it.optString("vod_year"));
            v.setVodArea(it.optString("vod_area"));
            vods.add(v);
        }
        return vods;
    }

    /** 解析页码 */
    private int parsePage(String pg) {
        try {
            int p = Integer.parseInt(pg);
            return p > 0 ? p : 1;
        } catch (Exception e) {
            return 1;
        }
    }

    /** 请求头 (无特殊鉴权, 给个 UA + Referer 就行) */
    private Map<String, String> getHeader() {
        Map<String, String> h = new HashMap<>();
        h.put("User-Agent", "Mozilla/5.0 (Linux; Android 11; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.91 Mobile Safari/537.36");
        h.put("Referer", "https://m.kptv.us/");
        return h;
    }
}
