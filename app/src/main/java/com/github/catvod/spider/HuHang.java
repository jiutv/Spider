package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;
import com.github.catvod.bean.Class;
import com.github.catvod.bean.Filter;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        // 修复：判断extend里cate不为空再赋值，避免传入非数字内容
        if (extend != null && extend.containsKey("cate")) {
            String cateVal = extend.get("cate");
            if (!TextUtils.isEmpty(cateVal)) {
                catId = cateVal;
            }
        }
        int page = 1;
        try {
            page = Integer.parseInt(pg);
        } catch (NumberFormatException e) {
            page = 1;
        }
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
            if (m.find()) {
                pic = m.group(1).trim();
                break;
            }
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
        // 解析播放线路和剧集 —— 不依赖任何固定标签结构，直接从 angplay URL 按 sid 分组
        HashMap<String, String> sourceNameMap = extractSourceNameMap(html);
        Pattern allLinkPattern = Pattern.compile(
                "href=\"(/angplay/" + Pattern.quote(vid) + "-(\\d+)-(\\d+)\\.html)\"[^>]*>([^<]+)</a>");
        Matcher linkMatcher = allLinkPattern.matcher(html);
        LinkedHashMap<String, LinkedHashMap<Integer, String>> sidEpisodes = new LinkedHashMap<>();
        LinkedHashMap<String, LinkedHashMap<Integer, String>> sidEpisodeUrls = new LinkedHashMap<>();
        while (linkMatcher.find()) {
            String epUrl = linkMatcher.group(1);
            String sid = linkMatcher.group(2);
            int nid;
            try {
                nid = Integer.parseInt(linkMatcher.group(3));
            } catch (NumberFormatException e) {
                continue;
            }
            String epName = linkMatcher.group(4).trim();
            // 过滤"立即播放"等非剧集链接
            if (epName.contains("立即") || epName.contains("播放")) continue;
            if (epName.length() < 2) continue;
            sidEpisodes.computeIfAbsent(sid, k -> new LinkedHashMap<>()).put(nid, epName);
            sidEpisodeUrls.computeIfAbsent(sid, k -> new LinkedHashMap<>()).put(nid, epUrl);
        }
        // 按 sid 数字排序, 让线路顺序稳定
        LinkedHashMap<String, Integer> sidOrder = new LinkedHashMap<>();
        for (String sid : sidEpisodes.keySet()) {
            try {
                sidOrder.put(sid, Integer.parseInt(sid));
            } catch (NumberFormatException e) {
                sidOrder.put(sid, Integer.MAX_VALUE);
            }
        }
        List<Map.Entry<String, Integer>> sortedSids = new ArrayList<>(sidOrder.entrySet());
        sortedSids.sort(Map.Entry.comparingByValue());
        StringBuilder fromSb = new StringBuilder();
        StringBuilder urlSb = new StringBuilder();
        int lineIdx = 1;
        for (Map.Entry<String, Integer> entry : sortedSids) {
            String sid = entry.getKey();
            LinkedHashMap<Integer, String> epNames = sidEpisodes.get(sid);
            LinkedHashMap<Integer, String> epUrls = sidEpisodeUrls.get(sid);
            if (epNames == null || epNames.isEmpty()) continue;
            String srcName = sourceNameMap.get(sid);
            if (TextUtils.isEmpty(srcName)) srcName = "线路" + lineIdx;
            List<Integer> nids = new ArrayList<>(epNames.keySet());
            nids.sort(Integer::compareTo);
            StringBuilder epSb = new StringBuilder();
            for (int nid : nids) {
                String epName = epNames.get(nid);
                String epUrl = epUrls.get(nid);
                if (TextUtils.isEmpty(epName) || TextUtils.isEmpty(epUrl)) continue;
                if (epSb.length() > 0) epSb.append("#");
                epSb.append(epName).append("$").append(epUrl);
            }
            if (epSb.length() == 0) continue;
            if (fromSb.length() > 0) fromSb.append("$$$");
            fromSb.append(srcName);
            if (urlSb.length() > 0) urlSb.append("$$$");
            urlSb.append(epSb);
            lineIdx++;
        }
        vod.setVodPlayFrom(fromSb.toString());
        vod.setVodPlayUrl(urlSb.toString());
        return Result.string(vod);
    }

    // =====================================================================
    //  播放页 - 多重兜底策略
    // =====================================================================
    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        String playUrl = id.startsWith("/") ? baseUrl + id : id;
        String html = fetch(playUrl);
        if (TextUtils.isEmpty(html)) {
            // 无法获取HTML，兜底：让WebView直接加载播放页嗅探
            return buildParse1Result(playUrl);
        }
        // 策略1: 解析 player_aaaa / player_data JSON 变量 (海洋CMS标准)
        String videoUrl = tryParsePlayerJson(html, "player_aaaa");
        if (TextUtils.isEmpty(videoUrl)) videoUrl = tryParsePlayerJson(html, "player_data");
        // 策略2: 解析 var now / var url / var currentUrl 变量
        if (TextUtils.isEmpty(videoUrl)) {
            videoUrl = matchGroup(html, "var\\s+now\\s*=\\s*['\"]([^'\"]+)['\"]", 1);
        }
        if (TextUtils.isEmpty(videoUrl)) {
            videoUrl = matchGroup(html, "var\\s+url\\s*=\\s*['\"]([^'\"]+)['\"]", 1);
        }
        if (TextUtils.isEmpty(videoUrl)) {
            videoUrl = matchGroup(html, "var\\s+currentUrl\\s*=\\s*['\"]([^'\"]+)['\"]", 1);
        }
        // 策略3: 搜索HTML中直接的 m3u8/mp4 链接
        if (TextUtils.isEmpty(videoUrl)) {
            videoUrl = extractDirectVideoUrl(html);
        }
        // 策略4: 找 iframe src 嵌入的播放器 → 让 WebView 嗅探
        if (TextUtils.isEmpty(videoUrl)) {
            try {
                Document doc = Jsoup.parse(html);
                Element iframe = doc.selectFirst("iframe[src]");
                if (iframe != null) {
                    String iframeSrc = iframe.attr("src");
                    if (!TextUtils.isEmpty(iframeSrc)) {
                        if (iframeSrc.startsWith("/")) iframeSrc = baseUrl + iframeSrc;
                        else if (!iframeSrc.startsWith("http")) iframeSrc = baseUrl + "/" + iframeSrc;
                        return buildParse1Result(iframeSrc);
                    }
                }
            } catch (Exception ignored) {}
        }
        // 策略5: 兜底 - 让 WebView 直接加载播放页嗅探
        if (TextUtils.isEmpty(videoUrl)) {
            return buildParse1Result(playUrl);
        }
        // 成功找到视频地址
        Result result = Result.get().parse(0).url(videoUrl);
        if (videoUrl.toLowerCase().contains(".m3u8")) {
            result.m3u8();
        }
        return result.string();
    }
    /**
     * 解析 player_aaaa / player_data 等 JSON 变量
     * 海洋CMS播放页标准格式: var player_aaaa={"url":"xxx","encrypt":0,...}
     */
    private String tryParsePlayerJson(String html, String varName) {
        String json = extractPlayerVar(html, varName);
        if (TextUtils.isEmpty(json)) return "";
        try {
            String url = extractJsonValue(json, "url");
            if (TextUtils.isEmpty(url)) return "";
            int encrypt = 0;
            try { encrypt = Integer.parseInt(extractJsonValue(json, "encrypt")); } catch (NumberFormatException e) {}
            return decodeVideoUrl(url, encrypt);
        } catch (Exception ignored) {}
        return "";
    }
    /**
     * 提取 JS 中的 JSON 对象变量 (支持嵌套花括号)
     */
    private String extractPlayerVar(String html, String varName) {
        int idx = -1;
        // 尝试 var player_aaaa = {...}
        int varIdx = html.indexOf("var " + varName);
        if (varIdx >= 0) idx = html.indexOf("{", varIdx);
        // 尝试 player_aaaa = {...} (无var)
        if (idx < 0) {
            int plainIdx = html.indexOf(varName + "=");
            if (plainIdx >= 0) idx = html.indexOf("{", plainIdx);
        }
        if (idx < 0) {
            int plainIdx = html.indexOf(varName + " =");
            if (plainIdx >= 0) idx = html.indexOf("{", plainIdx);
        }
        if (idx < 0) return null;
        // 花括号计数，支持嵌套
        int depth = 0, start = idx, end = -1;
        boolean inString = false;
        char stringChar = 0;
        for (int i = idx; i < html.length(); i++) {
            char c = html.charAt(i);
            if (inString) {
                if (c == '\\') { i++; }
                else if (c == stringChar) { inString = false; }
            } else {
                if (c == '{') depth++;
                else if (c == '}') { depth--; if (depth == 0) { end = i; break; } }
                else if (c == '"' || c == '\'') { inString = true; stringChar = c; }
            }
        }
        if (end > start) return html.substring(start, end + 1);
        return null;
    }
    /**
     * 从JSON字符串中提取指定key的值 (简单正则，避免org.json的异常)
     */
    private String extractJsonValue(String json, String key) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
        Matcher m = p.matcher(json);
        if (m.find()) return m.group(1).replace("\\/", "/");
        p = Pattern.compile("\"" + key + "\"\\s*:\\s*(\\d+)");
        m = p.matcher(json);
        if (m.find()) return m.group(1);
        return "";
    }
    /**
     * 按 encrypt 类型解码视频地址
     * 0=明文, 1=URL编码, 2=Base64编码
     */
    private String decodeVideoUrl(String url, int encrypt) {
        if (url == null || url.isEmpty()) return "";
        try {
            switch (encrypt) {
                case 0: return url.trim();
                case 1: return URLDecoder.decode(url.trim(), StandardCharsets.UTF_8.name()).trim();
                case 2: return new String(Base64.getDecoder().decode(url.trim()), StandardCharsets.UTF_8).trim();
                default: return url.trim();
            }
        } catch (Exception e) { return url.trim(); }
    }
    /**
     * 直接从HTML中搜索 m3u8/mp4 视频链接
     */
    private String extractDirectVideoUrl(String html) {
        Matcher m = Pattern.compile("https?://[^\"'\\s<>]+\\.m3u8[^\"'\\s<>]*", Pattern.CASE_INSENSITIVE).matcher(html);
        if (m.find()) return m.group();
        m = Pattern.compile("https?://[^\"'\\s<>]+\\.mp4[^\"'\\s<>]*", Pattern.CASE_INSENSITIVE).matcher(html);
        if (m.find()) return m.group();
        // 相对路径 m3u8
        m = Pattern.compile("(/[^\"'\\s<>]+\\.m3u8[^\"'\\s<>]*)", Pattern.CASE_INSENSITIVE).matcher(html);
        if (m.find()) {
            String path = m.group(1);
            return baseUrl + path;
        }
        return "";
    }
    /**
     * 构建 parse=1 的嗅探结果 (让 TVBox WebView 加载后自动嗅探视频)
     */
    private String buildParse1Result(String url) {
        HashMap<String, String> header = new HashMap<>();
        header.put("User-Agent", MOBILE_UA);
        return Result.get().parse(1).url(url).header(header).string();
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
            try { return Integer.parseInt(m.group(1)); } catch (NumberFormatException e) {}
        }
        p = Pattern.compile("1/(\\d+)");
        m = p.matcher(html);
        if (m.find()) {
            try { return Integer.parseInt(m.group(1)); } catch (NumberFormatException e) {}
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
    /**
     * 从页面提取 sid -> 线路名称的映射
     * 兼容多种 HTML 结构 (海洋CMS常见)
     *   1. <span id="wjm3u8">丹顶云</span>  (span 的 id 即 sid)
     *   2. <a data-sid="3" class="fed-play-item">丹顶云</a>  (data-sid 属性)
     *   3. <li class="fed-play-item" data-sid="3">丹顶云</li>
     */
    private HashMap<String, String> extractSourceNameMap(String html) {
        HashMap<String, String> map = new HashMap<>();
        // 方式1: <span id="xxx">名称</span> (sid 就是 id)
        Pattern p1 = Pattern.compile("<span[^>]*id=\"(\\d+)\"[^>]*>([^<]+)</span>");
        Matcher m1 = p1.matcher(html);
        while (m1.find()) {
            String sid = m1.group(1);
            String name = m1.group(2).trim();
            if (isValidSourceName(name) && !map.containsKey(sid)) {
                map.put(sid, name);
            }
        }
        // 方式2: 带 data-sid 属性的元素
        Pattern p2 = Pattern.compile("data-sid=\"(\\d+)\"[^>]*>([^<]{2,10})<");
        Matcher m2 = p2.matcher(html);
        while (m2.find()) {
            String sid = m2.group(1);
            String name = m2.group(2).trim();
            if (isValidSourceName(name) && !map.containsKey(sid)) {
                map.put(sid, name);
            }
        }
        // 方式3: 尝试用 Jsoup 解析线路 tab 区域
        if (map.isEmpty()) {
            try {
                Document doc = Jsoup.parse(html);
                // 常见线路tab选择器
                String[] selectors = {
                        ".fed-play-item", ".fed-source-item", ".tab-item",
                        ".play-item", ".source-item",
                        "[class*='play-item']", "[class*='source-item']",
                        "[data-sid]"
                };
                for (String sel : selectors) {
                    Elements els = doc.select(sel);
                    for (Element el : els) {
                        String sid = el.attr("data-sid");
                        if (TextUtils.isEmpty(sid)) {
                            // 尝试从 class 里提取数字 (某些网站用 class="wjm3u8")
                            String cls = el.className();
                            Pattern cp = Pattern.compile("(\\d+)");
                            Matcher cm = cp.matcher(cls);
                            if (cm.find()) sid = cm.group(1);
                        }
                        String name = el.text().trim();
                        // 只取第一个线路文字 (避免取到剧集列表里的文字)
                        if (name.contains(" ")) name = name.split("\\s+")[0];
                        if (!TextUtils.isEmpty(sid) && isValidSourceName(name) && !map.containsKey(sid)) {
                            map.put(sid, name);
                        }
                    }
                    if (!map.isEmpty()) break;
                }
            } catch (Exception ignored) {}
        }
        return map;
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
