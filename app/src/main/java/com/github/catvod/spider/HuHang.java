package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Filter;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Util;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 护航影院 (qdhuhang.com) 爬虫
 *
 * <p>网站结构：海洋CMS + 佐佐1.0主题，自定义路由</p>
 * <ul>
 *   <li>分类页: /qdhyl/{id}.html (首页), /qdhyl/{id}-{page}.html</li>
 *   <li>详情页: /huhzc/{vid}.html</li>
 *   <li>播放页: /angplay/{vid}-{sid}-{nid}.html</li>
 *   <li>搜索: /index.php?m=search&searchword={kw}</li>
 * </ul>
 *
 * <p>init 配置：{@code {"url": "https://www.qdhuhang.com"}}</p>
 */
public class HuHang extends Spider {

    private String baseUrl = "https://www.qdhuhang.com";
    private HashMap<String, String> headers;

    // 主分类 id -> 名称
    private static final String[][] MAIN_CATEGORIES = {
            {"1", "电影"},
            {"2", "电视剧"},
            {"3", "综艺"},
            {"4", "动漫"},
            {"48", "短剧"},
    };

    // 电影子分类
    private static final String[][] MOVIE_FILTERS = {
            {"5", "动作片"}, {"6", "爱情片"}, {"7", "科幻片"}, {"8", "恐怖片"},
            {"9", "战争片"}, {"10", "喜剧片"}, {"11", "纪录片"}, {"12", "剧情片"},
            {"32", "惊悚片"}, {"33", "悬疑片"},
    };

    // 电视剧子分类
    private static final String[][] TV_FILTERS = {
            {"13", "国产剧"}, {"14", "港剧"}, {"15", "美剧"}, {"16", "韩剧"},
            {"25", "日剧"}, {"28", "台剧"}, {"29", "泰剧"}, {"36", "大陆剧"},
            {"37", "海外剧"},
    };

    // 综艺子分类
    private static final String[][] VARIETY_FILTERS = {
            {"26", "精选"}, {"38", "内地"}, {"39", "日韩"}, {"40", "港台"}, {"41", "欧美"},
    };

    // 动漫子分类
    private static final String[][] ANIME_FILTERS = {
            {"27", "樱花"}, {"31", "电影"}, {"42", "国产"}, {"43", "日韩"},
            {"44", "欧美"}, {"45", "港台"}, {"46", "风车"}, {"60", "新番"}, {"61", "热番"},
    };

    // 短剧子分类
    private static final String[][] SHORT_FILTERS = {
            {"49", "女频剧"}, {"50", "反转剧"}, {"51", "穿越剧"}, {"52", "古装剧"},
            {"53", "都市剧"}, {"54", "脑洞剧"}, {"55", "爽文剧"},
    };

    // 移动端 User-Agent（网站拦截PC浏览器，必须用移动端UA）
    private static final String MOBILE_UA = "Mozilla/5.0 (Linux; Android 13; Pixel 7 Build/TQ3A.230901.001; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/116.0.5845.178 Mobile Safari/537.36";

    @Override
    public void init(Context context, String extend) {
        if (!TextUtils.isEmpty(extend)) {
            try {
                JSONObject obj = new JSONObject(extend);
                if (obj.has("url")) baseUrl = obj.getString("url");
            } catch (Exception ignored) {
            }
        }
        if (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        headers = new HashMap<>();
        headers.put("User-Agent", MOBILE_UA);
        headers.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        headers.put("Accept-Language", "zh-CN,zh;q=0.9");
    }

    // =====================================================================
    //  首页
    // =====================================================================

    @Override
    public String homeContent(boolean filter) throws Exception {
        ArrayList<Class> classes = new ArrayList<>();
        LinkedHashMap<String, List<Filter>> filters = new LinkedHashMap<>();

        for (String[] cat : MAIN_CATEGORIES) {
            classes.add(new Class(cat[0], cat[1]));
        }

        // 5大分类都加类型筛选器
        filters.put("1", buildFilters("类型", "1", MOVIE_FILTERS));
        filters.put("2", buildFilters("类型", "2", TV_FILTERS));
        filters.put("3", buildFilters("类型", "3", VARIETY_FILTERS));
        filters.put("4", buildFilters("类型", "4", ANIME_FILTERS));
        filters.put("48", buildFilters("类型", "48", SHORT_FILTERS));

        String html = fetch(baseUrl + "/qdhyl/1.html");
        ArrayList<Vod> list = parseVodList(html);

        return Result.string(classes, list, filters);
    }

    // =====================================================================
    //  分类页
    // =====================================================================

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        String catId = tid;
        if (extend != null && extend.containsKey("cate") && !TextUtils.isEmpty(extend.get("cate"))) {
            catId = extend.get("cate");
        }

        int page = 1;
        try { page = Integer.parseInt(pg); } catch (NumberFormatException ignored) {}

        String url;
        if (page <= 1) {
            url = baseUrl + "/qdhyl/" + catId + ".html";
        } else {
            url = baseUrl + "/qdhyl/" + catId + "-" + page + ".html";
        }

        String html = fetch(url);
        ArrayList<Vod> list = parseVodList(html);
        int pageCount = parsePageCount(html);

        return Result.get().page(page, pageCount, 30, pageCount * 30).vod(list).string();
    }

    // =====================================================================
    //  详情页
    // =====================================================================

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String vid = ids.get(0);
        String url = baseUrl + "/huhzc/" + vid + ".html";
        String html = fetch(url);

        Vod vod = new Vod();
        vod.setVodId(vid);

        // 标题
        Pattern p = Pattern.compile("<h1[^>]*>([^<]+)</h1>");
        Matcher m = p.matcher(html);
        if (m.find()) vod.setVodName(m.group(1).trim());

        // 封面 - 优先 data-original, 再 data-src, 再 src
        String pic = "";
        String[] imgAttrs = {"data-original", "data-src", "src"};
        for (String attr : imgAttrs) {
            p = Pattern.compile("class=\"fed-deta-img[^\"]*\"[^>]*>.*?<img[^>]*" + attr + "=\"([^\"]+)\"", Pattern.DOTALL);
            m = p.matcher(html);
            if (m.find()) { pic = m.group(1).trim(); break; }
        }
        if (!TextUtils.isEmpty(pic) && pic.startsWith("/")) pic = baseUrl + pic;
        vod.setVodPic(pic);

        // 信息项
        String infoBlock = matchGroup(html, "class=\"fed-deta-info[^\"]*\"[^>]*>(.*?)</ul>", 1);
        if (!TextUtils.isEmpty(infoBlock)) {
            vod.setTypeName(extractInfo(infoBlock, "分类"));
            vod.setVodArea(extractInfo(infoBlock, "地区"));
            vod.setVodYear(extractInfo(infoBlock, "年份"));
            vod.setVodActor(extractInfo(infoBlock, "主演"));
            vod.setVodDirector(extractInfo(infoBlock, "导演"));
            vod.setVodRemarks(extractInfo(infoBlock, "更新"));
        }

        // 简介
        String desc = matchGroup(html, "class=\"fed-deta-content[^\"]*\"[^>]*>(.*?)</div>", 1);
        if (!TextUtils.isEmpty(desc)) {
            desc = desc.replaceAll("<[^>]+>", "").replace("&nbsp;", " ").trim();
            vod.setVodContent(desc);
        }

        // 解析播放线路和剧集
        // HTML结构: <span id="wjm3u8">丹顶云</span> ... <dd class="wjm3u8">...<a href="/angplay/vid-sid-nid.html">第01集</a>...</dd>
        LinkedHashMap<String, String> sourceMap = new LinkedHashMap<>();
        Pattern sp = Pattern.compile("<span[^>]*id=\"([^\"]+)\"[^>]*>([^<]+)</span>");
        Matcher sm = sp.matcher(html);
        while (sm.find()) {
            String spId = sm.group(1);
            String spName = sm.group(2).trim();
            if (isValidSourceName(spName)) sourceMap.put(spId, spName);
        }

        StringBuilder fromSb = new StringBuilder();
        StringBuilder urlSb = new StringBuilder();

        // 按 <dd class="xxx"> 块解析每条线路的剧集
        Pattern ddPattern = Pattern.compile("<dd[^>]*class=\"([^\"]+)\"[^>]*>(.*?)</dd>", Pattern.DOTALL);
        Matcher ddMatcher = ddPattern.matcher(html);

        while (ddMatcher.find()) {
            String ddClass = ddMatcher.group(1).trim();
            String ddContent = ddMatcher.group(2);

            // 用 dd 的 class 匹配 span 的 id，得到线路名称
            String srcName = sourceMap.get(ddClass);
            if (TextUtils.isEmpty(srcName)) continue;

            // 提取该剧集列表
            Pattern epPattern = Pattern.compile(
                    "href=\"(/angplay/" + vid + "-\\d+-\\d+\\.html)\"[^>]*>([^<]+)</a>");
            Matcher epMatcher = epPattern.matcher(ddContent);

            StringBuilder epSb = new StringBuilder();
            while (epMatcher.find()) {
                String epUrl = epMatcher.group(1);
                String epName = epMatcher.group(2).trim();
                // 过滤"立即播放"等非剧集链接
                if (epName.contains("立即") || epName.contains("播放")) continue;
                if (epSb.length() > 0) epSb.append("#");
                epSb.append(epName).append("$").append(epUrl);
            }

            if (epSb.length() == 0) continue;

            if (fromSb.length() > 0) fromSb.append("$$$");
            fromSb.append(srcName);
            if (urlSb.length() > 0) urlSb.append("$$$");
            urlSb.append(epSb);
        }

        vod.setVodPlayFrom(fromSb.toString());
        vod.setVodPlayUrl(urlSb.toString());

        return Result.string(vod);
    }

    // =====================================================================
    //  播放页
    // =====================================================================

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        String playUrl = id.startsWith("/") ? baseUrl + id : id;
        String html = fetch(playUrl);

        // 从 now 变量提取m3u8地址
        String videoUrl = matchGroup(html, "var\\s+now\\s*=\\s*['\"]([^'\"]+)['\"]", 1);

        if (TextUtils.isEmpty(videoUrl)) {
            Pattern p = Pattern.compile("(https?://[^\\s\"'<>]+\\.m3u8[^\\s\"'<>]*)");
            Matcher m = p.matcher(html);
            if (m.find()) videoUrl = m.group(1);
        }

        if (TextUtils.isEmpty(videoUrl)) {
            return Result.error("解析播放地址失败");
        }

        Result result = Result.get().parse(0).url(videoUrl);
        if (videoUrl.toLowerCase().contains(".m3u8")) {
            result.m3u8();
        }
        return result.string();
    }

    // =====================================================================
    //  搜索
    // =====================================================================

    @Override
    public String searchContent(String keyword, boolean quick) throws Exception {
        String url = baseUrl + "/index.php?m=search&searchword="
                + java.net.URLEncoder.encode(keyword, "UTF-8");
        String html = fetch(url);
        ArrayList<Vod> list = parseVodList(html);
        return Result.string(list);
    }

    // =====================================================================
    //  工具方法
    // =====================================================================

    private String fetch(String url) {
        return OkHttp.string(url, headers);
    }

    private ArrayList<Vod> parseVodList(String html) {
        ArrayList<Vod> list = new ArrayList<>();
        if (TextUtils.isEmpty(html)) return list;

        Pattern p = Pattern.compile(
                "<li[^>]*class=\"[^\"]*fed-list-item[^\"]*\"[^>]*>(.*?)</li>",
                Pattern.DOTALL
        );
        Matcher m = p.matcher(html);

        while (m.find()) {
            String item = m.group(1);
            try {
                // ID
                String idMatch = matchGroup(item, "href=\"/huhzc/(\\d+)\\.html\"", 1);
                if (TextUtils.isEmpty(idMatch)) continue;

                // 图片 - 优先 data-original (lazyload), 再 data-src, 再 src
                String pic = matchGroup(item, "data-original=\"([^\"]+)\"", 1);
                if (TextUtils.isEmpty(pic)) pic = matchGroup(item, "data-src=\"([^\"]+)\"", 1);
                if (TextUtils.isEmpty(pic)) pic = matchGroup(item, "src=\"([^\"]+)\"", 1);
                if (!TextUtils.isEmpty(pic) && pic.startsWith("/")) pic = baseUrl + pic;

                // 标题 - 从 fed-list-title 提取
                String title = matchGroup(item, "class=\"[^\"]*fed-list-title[^\"]*\"[^>]*>([^<]+)</a>", 1);
                if (TextUtils.isEmpty(title)) {
                    ArrayList<String> allTexts = new ArrayList<>();
                    Pattern ap = Pattern.compile("<a[^>]*>([^<]+)</a>");
                    Matcher am = ap.matcher(item);
                    while (am.find()) {
                        String t = am.group(1).trim();
                        if (t.length() >= 2) allTexts.add(t);
                    }
                    for (String t : allTexts) {
                        if (TextUtils.isEmpty(title) || t.length() > title.length()) title = t;
                    }
                }
                if (TextUtils.isEmpty(title)) continue;
                title = title.trim();

                // 备注/状态
                String remark = matchGroup(item, "class=\"[^\"]*fed-list-remarks[^\"]*\"[^>]*>([^<]+)<", 1);
                if (TextUtils.isEmpty(remark)) remark = "";

                Vod vod = new Vod(idMatch, title, pic, remark.trim());
                list.add(vod);
            } catch (Exception ignored) {
            }
        }

        return list;
    }

    private int parsePageCount(String html) {
        Pattern p = Pattern.compile("/qdhyl/\\d+-(\\d+)\\.html[^>]*>尾页");
        Matcher m = p.matcher(html);
        if (m.find()) {
            try { return Integer.parseInt(m.group(1)); } catch (NumberFormatException ignored) {}
        }
        p = Pattern.compile("1/(\\d+)");
        m = p.matcher(html);
        if (m.find()) {
            try { return Integer.parseInt(m.group(1)); } catch (NumberFormatException ignored) {}
        }
        return 1;
    }

    private String[] parseSourceNames(String html) {
        ArrayList<String> names = new ArrayList<>();

        String tabt = matchGroup(html, "class=\"tabt[^\"]*\"[^>]*>(.*?)</(?:div|ul)>", 1);
        if (!TextUtils.isEmpty(tabt)) {
            Pattern p = Pattern.compile("<span[^>]*>([^<]+)</span>");
            Matcher m = p.matcher(tabt);
            while (m.find()) {
                String name = m.group(1).trim();
                if (isValidSourceName(name)) names.add(name);
            }
        }
        if (names.size() > 0) return names.toArray(new String[0]);

        String playTabs = matchGroup(html, "class=\"[^\"]*fed-play[^\"]*\"[^>]*>(.*?)</div>", 1);
        if (!TextUtils.isEmpty(playTabs)) {
            Pattern p = Pattern.compile(">([^<>]{2,8})<");
            Matcher m = p.matcher(playTabs);
            while (m.find()) {
                String name = m.group(1).trim();
                if (isValidSourceName(name) && !names.contains(name)) names.add(name);
            }
        }
        return names.toArray(new String[0]);
    }

    private boolean isValidSourceName(String name) {
        if (TextUtils.isEmpty(name)) return false;
        if (name.length() < 2 || name.length() > 10) return false;
        if (name.contains("播放") || name.contains("下载") || name.contains("介绍")) return false;
        if (name.contains("排序") || name.contains("筛选") || name.contains("全部")) return false;
        return true;
    }

    private String extractInfo(String block, String label) {
        Pattern p = Pattern.compile(label + "：\\s*([^<]+)");
        Matcher m = p.matcher(block);
        if (m.find()) {
            String val = m.group(1).trim();
            val = val.replace("&nbsp;", " ").replaceAll("\\s+", " ").trim();
            int idx = val.indexOf("：");
            if (idx > 0) val = val.substring(0, idx);
            return val.trim();
        }
        return "";
    }

    private static String matchGroup(String input, String regex, int group) {
        try {
            Pattern p = Pattern.compile(regex, Pattern.DOTALL);
            Matcher m = p.matcher(input);
            if (m.find()) return m.group(group);
        } catch (Exception ignored) {
        }
        return "";
    }

    private ArrayList<Filter> buildFilters(String key, String allId, String[][] items) {
        ArrayList<Filter> list = new ArrayList<>();
        ArrayList<Filter.Value> values = new ArrayList<>();
        values.add(new Filter.Value("全部", allId));
        for (String[] f : items) {
            values.add(new Filter.Value(f[1], f[0]));
        }
        list.add(new Filter(key, key, values));
        return list;
    }
}
