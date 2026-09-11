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
 *
 * 5 个主分类, 每个带子分类筛选 Filter:
 *   电影(21): 电影片20 / 动作片21 / 喜剧片22 / 恐怖片23 / 科幻片24 / 爱情片25 / 剧情片26 / 战争片27 / 纪录片28 / 理论片29
 *   电视剧(31): 连续剧30 / 国产剧31 / 欧美剧32 / 香港剧33 / 韩国剧34 / 台湾剧35 / 日本剧36 / 海外剧37 / 泰国剧38
 *   动漫(41): 动漫片39 / 国产动漫40 / 日韩动漫41 / 欧美动漫42 / 港台动漫43 / 海外动漫44
 *   综艺(46): 综艺片45 / 大陆综艺46 / 港台综艺47 / 日韩综艺48 / 欧美综艺49
 *   短剧(58): 短剧大全58 / 重生民国65 / 穿越年代66 / 现代言情67 / 反转爽文68 / 女恋总裁69 / 闪婚离婚70 / 都市脑洞71 / 古装仙侠72 / AI漫剧74
 */
public class ZhaiNanYS extends Spider {

    private static final String API = "https://bfzyapi.com/api.php/provide/vod/";

    private static final int PAGE_SIZE = 20;
    /** 首页每个板块取多少条 */
    private static final int HOME_TAB_SIZE = 12;

    /** 首页 4 板块 (来自 m.kptv.us /api/web hot_db) */
    private static final String[][] HOME_TABS = {
            {"21", "电影"},
            {"31", "电视剧"},
            {"41", "动漫"},
            {"46", "综艺"}
    };

    /**
     * 主分类 -> 子分类 Filter 映射
     * key = 主分类 tid, 第一个就是默认子分类
     */
    private static final LinkedHashMap<String, String[][]> CATEGORY_MAP = new LinkedHashMap<>();

    static {
        // 电影
        CATEGORY_MAP.put("21", new String[][]{
                {"20", "电影片"}, {"21", "动作片"}, {"22", "喜剧片"}, {"23", "恐怖片"},
                {"24", "科幻片"}, {"25", "爱情片"}, {"26", "剧情片"}, {"27", "战争片"},
                {"28", "纪录片"}, {"29", "理论片"}
        });
        // 电视剧
        CATEGORY_MAP.put("31", new String[][]{
                {"30", "连续剧"}, {"31", "国产剧"}, {"32", "欧美剧"}, {"33", "香港剧"},
                {"34", "韩国剧"}, {"35", "台湾剧"}, {"36", "日本剧"}, {"37", "海外剧"},
                {"38", "泰国剧"}
        });
        // 动漫
        CATEGORY_MAP.put("41", new String[][]{
                {"39", "动漫片"}, {"40", "国产动漫"}, {"41", "日韩动漫"}, {"42", "欧美动漫"},
                {"43", "港台动漫"}, {"44", "海外动漫"}
        });
        // 综艺
        CATEGORY_MAP.put("46", new String[][]{
                {"45", "综艺片"}, {"46", "大陆综艺"}, {"47", "港台综艺"}, {"48", "日韩综艺"},
                {"49", "欧美综艺"}
        });
        // 短剧 (已去掉 "福利"!)
        CATEGORY_MAP.put("58", new String[][]{
                {"58", "短剧大全"}, {"65", "重生民国"}, {"66", "穿越年代"}, {"67", "现代言情"},
                {"68", "反转爽文"}, {"69", "女恋总裁"}, {"70", "闪婚离婚"}, {"71", "都市脑洞"},
                {"72", "古装仙侠"}, {"74", "AI漫剧"}
        });
    }

    // ================================================================
    //  首页分类 (5 个主 tab, 每个带子分类筛选)
    // ================================================================

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        LinkedHashMap<String, List<Filter>> filters = new LinkedHashMap<>();

        for (Map.Entry<String, String[][]> entry : CATEGORY_MAP.entrySet()) {
            String mainTid = entry.getKey();
            String[][] subs = entry.getValue();
            // 主分类名 = 第一个子分类名 (默认展示内容)
            String mainName = subs[0][1];
            classes.add(new Class(mainTid, mainName));

            // 构建子分类 Filter
            List<Filter.Value> values = new ArrayList<>();
            for (String[] sub : subs) {
                values.add(new Filter.Value(sub[1], sub[0]));
            }
            Filter f = new Filter("sub", "子分类", values);
            List<Filter> filterList = new ArrayList<>();
            filterList.add(f);
            filters.put(mainTid, filterList);
        }

        return Result.string(classes, filters);
    }

    // ================================================================
    //  首页推荐 (4 板块各 N 条, 批量 detail 补图)
    // ================================================================

    @Override
    public String homeVideoContent() throws Exception {
        List<Vod> all = new ArrayList<>();
        List<String> allIds = new ArrayList<>();
        for (String[] tab : HOME_TABS) {
            String tid = tab[0];
            String url = API + "?ac=list&t=" + tid + "&pg=1&limit=" + HOME_TAB_SIZE;
            JSONObject root = fetchJson(url);
            all.addAll(parseVodList(root, allIds));
        }
        fillPicsFromDetail(all, allIds);
        return Result.string(all);
    }

    // ================================================================
    //  分类列表 (根据 extend 里的 sub 筛选子分类, 批量 detail 补图)
    // ================================================================

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        int page = parsePage(pg);

        // 如果 extend 里带了 sub (子分类筛选), 就用 sub 的 tid; 否则用主分类 tid (默认)
        String useTid = tid;
        if (extend != null && extend.containsKey("sub") && !TextUtils.isEmpty(extend.get("sub"))) {
            useTid = extend.get("sub");
        }

        String url = API + "?ac=list&t=" + useTid + "&pg=" + page + "&limit=" + PAGE_SIZE;
        JSONObject root = fetchJson(url);
        List<String> ids = new ArrayList<>();
        List<Vod> vods = parseVodList(root, ids);

        int total = root.optInt("total", 0);
        int pagecount = root.optInt("pagecount", 0);
        if (pagecount == 0 && total > 0) {
            pagecount = (int) Math.ceil((double) total / PAGE_SIZE);
        }

        fillPicsFromDetail(vods, ids);

        return Result.string(page, pagecount, PAGE_SIZE, total, vods);
    }

    // ================================================================
    //  详情
    // ================================================================

    @Override
    public String detailContent(List<String> ids) throws Exception {
        if (ids == null || ids.isEmpty()) return Result.string(new Vod());
        String id = ids.get(0);
        String url = API + "?ac=detail&ids=" + id;
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
        vod.setVodPlayFrom(item.optString("vod_play_from"));
        vod.setVodPlayUrl(item.optString("vod_play_url"));

        return Result.string(vod);
    }

    // ================================================================
    //  播放
    // ================================================================

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        // PHP vod detail 返回 vod_play_url 格式: 剧集名$m3u8
        String url = id;
        if (!TextUtils.isEmpty(url) && url.contains("$")) {
            url = url.substring(url.lastIndexOf('$') + 1);
        }
        return Result.get().url(url).string();
    }

    // ================================================================
    //  搜索 (list + 批量 detail 补图)
    // ================================================================

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        int page = parsePage(pg);
        String url = API + "?ac=list&wd=" + URLEncoder.encode(key, "UTF-8") + "&pg=" + page + "&limit=" + PAGE_SIZE;
        JSONObject root = fetchJson(url);
        List<String> ids = new ArrayList<>();
        List<Vod> vods = parseVodList(root, ids);
        int total = root.optInt("total", 0);
        int pagecount = root.optInt("pagecount", 0);
        if (pagecount == 0 && total > 0) {
            pagecount = (int) Math.ceil((double) total / PAGE_SIZE);
        }

        fillPicsFromDetail(vods, ids);
        return Result.string(page, pagecount, PAGE_SIZE, total, vods);
    }

    // ================================================================
    //  工具方法
    // ================================================================

    private JSONObject fetchJson(String url) {
        try {
            String text = OkHttp.string(url, getHeader());
            if (TextUtils.isEmpty(text)) return new JSONObject();
            return new JSONObject(text);
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    /** 解析 list 接口返回, 同时返回 parallel 的 id 列表 (Vod bean 没有 getVodId) */
    private List<Vod> parseVodList(JSONObject root, List<String> outIds) {
        List<Vod> vods = new ArrayList<>();
        if (root == null) return vods;
        JSONArray arr = root.optJSONArray("list");
        if (arr == null) return vods;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject it = arr.optJSONObject(i);
            if (it == null) continue;
            // vod_id 可能是数字也可能是字符串 (搜索接口返回字符串)
            String id = it.optString("vod_id", "0");
            // 如果 optString 拿到空, 再试试 int
            if (TextUtils.isEmpty(id) || "0".equals(id)) {
                id = String.valueOf(it.optInt("vod_id", 0));
            }
            String name = it.optString("vod_name");
            String remarks = it.optString("vod_remarks");
            // list 接口有时自带 vod_pic (如搜索 wd=)
            String pic = it.optString("vod_pic");
            if (TextUtils.isEmpty(id) || "0".equals(id)) continue;
            if (outIds != null) outIds.add(id);
            Vod v = new Vod(id, name, pic, remarks);
            v.setVodYear(it.optString("vod_year"));
            v.setVodArea(it.optString("vod_area"));
            vods.add(v);
        }
        return vods;
    }

    /** 批量 detail 补图 (PHP vod ac=detail&ids= 单次最多 20 条, 需要分批) */
    private void fillPicsFromDetail(List<Vod> vods, List<String> ids) {
        if (vods == null || vods.isEmpty() || ids == null || ids.isEmpty()) return;

        // 分批, 每批 20 个 (实测 API 单次最多返回 20 条)
        HashMap<String, String> picMap = new HashMap<>();
        int BATCH = 20;
        for (int b = 0; b < ids.size(); b += BATCH) {
            int end = Math.min(b + BATCH, ids.size());
            StringBuilder idStr = new StringBuilder();
            for (int j = b; j < end; j++) {
                if (idStr.length() > 0) idStr.append(",");
                idStr.append(ids.get(j));
            }
            String url = API + "?ac=detail&ids=" + idStr.toString();
            JSONObject root = fetchJson(url);
            JSONArray arr = root.optJSONArray("list");
            if (arr == null) continue;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject it = arr.optJSONObject(i);
                if (it == null) continue;
                String id = String.valueOf(it.optInt("vod_id", 0));
                String pic = it.optString("vod_pic");
                if (!TextUtils.isEmpty(pic)) picMap.put(id, pic);
            }
        }

        // 回填覆盖 (detail 的图比 list 的更可靠)
        for (int i = 0; i < vods.size(); i++) {
            String pic = picMap.get(ids.get(i));
            if (!TextUtils.isEmpty(pic)) vods.get(i).setVodPic(pic);
        }
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
