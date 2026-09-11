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
 * 宅男影视 (m.kptv.us) 全站爬虫
 * 主站 PHP vod API: https://bfzyapi.com/api.php/provide/vod/
 * 覆盖电影 / 电视剧 / 动漫 / 综艺 / 短剧 共 49 个分类
 */
public class ZhaiNanYS extends Spider {

    /** 主站 PHP vod API (全站 49 个分类) */
    private static final String API_BASE = "https://bfzyapi.com/api.php/provide/vod/";

    /** 首页 4 个板块 (来自 m.kptv.us /api/web 的 hot_db 字段) */
    private static final String[][] HOME_TABS = {
            {"21", "电影"},
            {"31", "电视剧"},
            {"41", "动漫"},
            {"47", "综艺"}
    };

    private static final int PAGE_SIZE = 20;
    /** 首页每个板块取多少条 */
    private static final int HOME_TAB_SIZE = 12;

    // ---------- 首页分类 ----------

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        LinkedHashMap<String, List<Filter>> filters = new LinkedHashMap<>();

        // 从 API 动态拉取分类定义
        JSONObject root = fetchJson(API_BASE + "?ac=list&limit=1");
        JSONArray cls = root.optJSONArray("class");
        if (cls != null) {
            for (int i = 0; i < cls.length(); i++) {
                JSONObject c = cls.getJSONObject(i);
                String tid = String.valueOf(c.optInt("type_id", 0));
                String name = c.optString("type_name");
                if (!TextUtils.isEmpty(name) && !"0".equals(tid)) {
                    classes.add(new Class(tid, name));
                }
            }
        }
        // 兜底: 如果 API 没返回 class, 写死常用子分类 (和首页板块保持一致)
        if (classes.isEmpty()) {
            classes.add(new Class("21", "电影"));
            classes.add(new Class("31", "电视剧"));
            classes.add(new Class("41", "动漫"));
            classes.add(new Class("47", "综艺"));
            classes.add(new Class("58", "短剧"));
            classes.add(new Class("68", "反转爽文"));
        }

        return Result.string(classes, filters);
    }

    // ---------- 首页推荐 (4 个板块各 N 条, 共 4*HOME_TAB_SIZE 条) ----------

    @Override
    public String homeVideoContent() throws Exception {
        List<Vod> all = new ArrayList<>();
        for (String[] tab : HOME_TABS) {
            String tid = tab[0];
            String url = API_BASE + "?ac=list&t=" + tid + "&page=1&limit=" + HOME_TAB_SIZE;
            JSONObject root = fetchJson(url);
            List<Vod> part = parseVodList(root);
            all.addAll(part);
        }
        return Result.string(all);
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
        vod.setVodActor(item.optString("vod_actor"));
        vod.setVodDirector(item.optString("vod_director"));
        vod.setVodYear(item.optString("vod_year"));
        vod.setVodArea(item.optString("vod_area"));
        vod.setVodContent(item.optString("vod_content"));

        // 播放源 (PHP vod 标准: vod_play_from 用 $ 分隔多源, vod_play_url 用 # 分隔)
        String playFrom = item.optString("vod_play_from");
        String playUrl = item.optString("vod_play_url");
        if (!TextUtils.isEmpty(playFrom) && !TextUtils.isEmpty(playUrl)) {
            vod.setVodPlayFrom(playFrom);
            vod.setVodPlayUrl(playUrl);
        }

        return Result.string(vod);
    }

    // ---------- 播放 ----------

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        // PHP vod detail 返回的 vod_play_url 格式: 剧集名$m3u8#剧集名$m3u8
        // CatVod 传进来的 id 已经是 "剧集名$m3u8" 格式, 取 $ 后面的直链
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

    private JSONObject fetchJson(String url) {
        try {
            String text = OkHttp.string(url, getHeader());
            if (TextUtils.isEmpty(text)) return new JSONObject();
            return new JSONObject(text);
        } catch (Exception e) {
            return new JSONObject();
        }
    }

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

    private int parsePage(String pg) {
        try {
            int p = Integer.parseInt(pg);
            return p > 0 ? p : 1;
        } catch (Exception e) {
            return 1;
        }
    }

    private Map<String, String> getHeader() {
        Map<String, String> h = new HashMap<>();
        h.put("User-Agent", "Mozilla/5.0 (Linux; Android 11; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.91 Mobile Safari/537.36");
        h.put("Referer", "https://m.kptv.us/");
        return h;
    }
}
