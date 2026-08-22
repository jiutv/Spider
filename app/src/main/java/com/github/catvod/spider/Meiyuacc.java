package com.github.catvod.spider;

import android.content.Context;
import com.github.catvod.crawler.Spider;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URLDecoder;
import java.net.URLEncoder;
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
 * TVBox Spider for meiyuacct.com (美影视 / 114短剧)
 * 模板: stui (stui_default)
 *
 * HTML 结构:
 *   列表项: ul.stui-vodlist > li > div.stui-vodlist__box
 *     - a.stui-vodlist__thumb[ data-original=图片URL, title=标题, href=/vod/{id}.html ]
 *     - span.pic-text > b (备注)
 *     - div.stui-vodlist__detail > h4.title > a[title=标题, href=/vod/{id}.html]
 *
 * URL 模式:
 *   分类列表: /vodshow/{tid}-{class}-{area}-{lang}-{year}-{by}------{page}---.html  (8个横杠在page前)
 *   详情页:   /vod/{vod_id}.html
 *   播放页:   /p/{vod_id}-{flag}-{episode}.html
 *   搜索:    /vodsearch/{keyword}-------------.html
 */
public class Meiyuacc extends Spider {

    private static final String SITE_URL = "https://www.meiyuacct.com";
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final int TIMEOUT = 15000;

    @Override
    public void init(Context context) {
    }

    // ======================== 首页 ========================

    @Override
    public String homeContent(boolean filter) {
        String[][] categories = {
            {"1", "电影"}, {"2", "电视剧"}, {"3", "综艺"}, {"4", "动漫"}, {"26", "短剧"}
        };

        StringBuilder classJson = new StringBuilder("[");
        for (int i = 0; i < categories.length; i++) {
            if (i > 0) classJson.append(",");
            classJson.append("{\"type_id\":\"").append(categories[i][0]).append("\"")
                    .append(",\"type_name\":\"").append(categories[i][1]).append("\"}");
        }
        classJson.append("]");

        // 首页推荐 - 从首页抓取所有分类内容
        String listJson = "[]";
        try {
            Document doc = fetchDoc(SITE_URL + "/");
            listJson = parseListPage(doc);
        } catch (Exception ignored) {
        }

        StringBuilder result = new StringBuilder();
        result.append("{");
        result.append("\"class\":").append(classJson);
        if (filter) {
            result.append(",\"filters\":").append(buildFiltersJson());
        }
        result.append(",\"list\":").append(listJson);
        result.append("}");
        return result.toString();
    }

    // ======================== 筛选 ========================

    private String buildFiltersJson() {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"1\":[").append(filterItem("class","类型",new String[][]{{"全部",""},{"动作片","动作片"},{"喜剧片","喜剧片"},{"爱情片","爱情片"},{"科幻片","科幻片"},{"恐怖片","恐怖片"},{"剧情片","剧情片"},{"战争片","战争片"},{"纪录片","纪录片"}})).append(",").append(filterItem("area","地区",new String[][]{{"全部",""},{"大陆","大陆"},{"香港","香港"},{"台湾","台湾"},{"美国","美国"},{"日本","日本"},{"韩国","韩国"},{"法国","法国"},{"英国","英国"},{"德国","德国"},{"泰国","泰国"},{"印度","印度"},{"其他","其他"}})).append(",").append(filterItem("year","年份",new String[][]{{"全部",""},{"2026","2026"},{"2025","2025"},{"2024","2024"},{"2023","2023"},{"2022","2022"},{"2021","2021"},{"2020","2020"},{"2019","2019"},{"更早","更早"}})).append(",").append(filterItem("by","排序",new String[][]{{"时间","time"},{"人气","hits"},{"评分","score"}})).append("]");
        sb.append(",\"2\":[").append(filterItem("class","类型",new String[][]{{"全部",""},{"国产剧","国产剧"},{"港剧","港剧"},{"美剧","美剧"},{"韩剧","韩剧"},{"日剧","日剧"},{"泰剧","泰剧"}})).append(",").append(filterItem("area","地区",new String[][]{{"全部",""},{"大陆","大陆"},{"香港","香港"},{"台湾","台湾"},{"美国","美国"},{"日本","日本"},{"韩国","韩国"},{"英国","英国"},{"泰国","泰国"}})).append(",").append(filterItem("year","年份",new String[][]{{"全部",""},{"2026","2026"},{"2025","2025"},{"2024","2024"},{"2023","2023"},{"2022","2022"},{"2021","2021"},{"更早","更早"}})).append(",").append(filterItem("by","排序",new String[][]{{"时间","time"},{"人气","hits"},{"评分","score"}})).append("]");
        sb.append(",\"3\":[").append(filterItem("class","类型",new String[][]{{"全部",""}})).append(",").append(filterItem("area","地区",new String[][]{{"全部",""},{"大陆","大陆"},{"香港","香港"},{"台湾","台湾"},{"日本","日本"},{"韩国","韩国"},{"美国","美国"}})).append(",").append(filterItem("year","年份",new String[][]{{"全部",""},{"2026","2026"},{"2025","2025"},{"2024","2024"},{"2023","2023"},{"更早","更早"}})).append(",").append(filterItem("by","排序",new String[][]{{"时间","time"},{"人气","hits"},{"评分","score"}})).append("]");
        sb.append(",\"4\":[").append(filterItem("class","类型",new String[][]{{"全部",""}})).append(",").append(filterItem("area","地区",new String[][]{{"全部",""},{"日本","日本"},{"大陆","大陆"},{"美国","美国"}})).append(",").append(filterItem("year","年份",new String[][]{{"全部",""},{"2026","2026"},{"2025","2025"},{"2024","2024"},{"2023","2023"},{"更早","更早"}})).append(",").append(filterItem("by","排序",new String[][]{{"时间","time"},{"人气","hits"},{"评分","score"}})).append("]");
        sb.append(",\"26\":[").append(filterItem("class","类型",new String[][]{{"全部",""}})).append(",").append(filterItem("area","地区",new String[][]{{"全部",""},{"大陆","大陆"}})).append(",").append(filterItem("year","年份",new String[][]{{"全部",""},{"2026","2026"},{"2025","2025"},{"2024","2024"},{"更早","更早"}})).append(",").append(filterItem("by","排序",new String[][]{{"时间","time"},{"人气","hits"},{"评分","score"}})).append("]");
        sb.append("}");
        return sb.toString();
    }

    private String filterItem(String key, String name, String[][] options) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"key\":\"").append(key).append("\",\"name\":\"").append(name).append("\",\"value\":[");
        for (int i = 0; i < options.length; i++) {
            if (i > 0) sb.append(",");
            sb.append("{\"n\":\"").append(esc(options[i][0])).append("\",\"v\":\"").append(esc(options[i][1])).append("\"}");
        }
        sb.append("]}");
        return sb.toString();
    }

    // ======================== 分类列表 ========================

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        try {
            int page = parseInt(pg, 1);
            String cf = "", af = "", lf = "", yf = "", bf = "";
            if (extend != null) {
                cf = extend.getOrDefault("class", "");
                af = extend.getOrDefault("area", "");
                lf = extend.getOrDefault("lang", "");
                yf = extend.getOrDefault("year", "");
                bf = extend.getOrDefault("by", "");
            }
            // URL格式: /vodshow/{tid}-{class}-{area}-{lang}-{year}-{by}------{page}---.html
            // 空筛选时: /vodshow/1--------1---.html (8个横杠在page前, 3个在后)
            String url = SITE_URL + "/vodshow/" + tid
                    + "-" + cf    // class
                    + "-" + af    // area
                    + "-" + lf    // lang
                    + "-" + yf    // year
                    + "-" + bf    // by
                    + "-" + ""    // letter
                    + "-" + ""    // extra1
                    + "-" + ""    // extra2
                    + "-" + page  // page
                    + "---.html"; // trailing 3 empty fields

            Document doc = fetchDoc(url);
            String listJson = parseListPage(doc);
            int pageCount = parsePageCount(doc);

            StringBuilder result = new StringBuilder();
            result.append("{");
            result.append("\"page\":").append(page).append(",");
            result.append("\"pagecount\":").append(pageCount).append(",");
            result.append("\"limit\":12,");
            result.append("\"total\":").append(pageCount * 12).append(",");
            result.append("\"list\":").append(listJson);
            result.append("}");
            return result.toString();
        } catch (Exception e) {
            return "{\"page\":" + parseInt(pg,1) + ",\"pagecount\":1,\"limit\":12,\"total\":0,\"list\":[]}";
        }
    }

    // ======================== 详情 ========================

    @Override
    public String detailContent(List<String> ids) {
        try {
            String vodId = ids.get(0);
            Document doc = fetchDoc(SITE_URL + "/vod/" + vodId + ".html");

            // 标题: h1 或 .stui-content__detail .title
            String title = "";
            Element h1 = doc.selectFirst("h1, .stui-content__detail .title, .title h1");
            if (h1 != null) title = h1.text().trim();
            if (title.isEmpty()) title = doc.title();

            // 封面: a.stui-content__thumb[data-original] 或 img
            String pic = "";
            Element thumb = doc.selectFirst(".stui-content__thumb, .stui-vodlist__thumb, .thumb");
            if (thumb != null) {
                pic = thumb.attr("data-original");
                if (pic.isEmpty()) pic = thumb.attr("data-src");
                if (pic.isEmpty()) pic = extractImgSrc(thumb);
            }
            if (pic.isEmpty()) pic = findDetailPic(doc);
            pic = normalizeUrl(pic);

            // 详情信息
            HashMap<String, String> info = parseDetailInfo(doc);

            // 播放列表
            String[] playData = parsePlayList(doc, vodId);

            StringBuilder vod = new StringBuilder();
            vod.append("{");
            vod.append("\"vod_id\":\"").append(esc(vodId)).append("\",");
            vod.append("\"vod_name\":\"").append(esc(title)).append("\",");
            vod.append("\"vod_pic\":\"").append(esc(pic)).append("\",");
            vod.append("\"vod_remarks\":\"").append(esc(info.getOrDefault("remarks",""))).append("\",");
            vod.append("\"type_name\":\"").append(esc(info.getOrDefault("type",""))).append("\",");
            vod.append("\"vod_area\":\"").append(esc(info.getOrDefault("area",""))).append("\",");
            vod.append("\"vod_year\":\"").append(esc(info.getOrDefault("year",""))).append("\",");
            vod.append("\"vod_director\":\"").append(esc(info.getOrDefault("director",""))).append("\",");
            vod.append("\"vod_actor\":\"").append(esc(info.getOrDefault("actor",""))).append("\",");
            vod.append("\"vod_content\":\"").append(esc(info.getOrDefault("content",""))).append("\",");
            vod.append("\"vod_play_from\":\"").append(esc(playData[0])).append("\",");
            vod.append("\"vod_play_url\":\"").append(esc(playData[1])).append("\"");
            vod.append("}");
            return "{\"list\":[" + vod + "]}";
        } catch (Exception e) {
            return "{\"list\":[]}";
        }
    }

    // ======================== 播放 ========================

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        try {
            String playUrl = id.startsWith("http") ? id : SITE_URL + (id.startsWith("/") ? id : "/" + id);
            Document doc = fetchDoc(playUrl);
            String html = doc.html();

            // 解析 player_aaaa 变量 (支持嵌套JSON)
            String playerJson = extractPlayerVar(html, "player_aaaa");
            if (playerJson == null || playerJson.isEmpty()) {
                playerJson = extractPlayerVar(html, "player_data");
            }

            StringBuilder result = new StringBuilder("{");
            if (playerJson != null && !playerJson.isEmpty()) {
                String videoUrl = extractJsonValue(playerJson, "url");
                int encrypt = parseInt(extractJsonValue(playerJson, "encrypt"), 0);
                videoUrl = decodeVideoUrl(videoUrl, encrypt);
                int parse = isDirectVideo(videoUrl) ? 0 : 1;
                result.append("\"parse\":").append(parse).append(",");
                result.append("\"header\":\"{\\\"User-Agent\\\":\\\"").append(esc(UA)).append("\\\",\\\"Referer\\\":\\\"").append(esc(SITE_URL)).append("\\\"}\",");
                result.append("\"playUrl\":\"\",");
                result.append("\"url\":\"").append(esc(videoUrl)).append("\"");
            } else {
                String directUrl = extractDirectVideoUrl(html);
                if (!directUrl.isEmpty()) {
                    result.append("\"parse\":0,\"header\":\"\",\"playUrl\":\"\",\"url\":\"").append(esc(directUrl)).append("\"");
                } else {
                    Element iframe = doc.selectFirst("iframe[src]");
                    String iframeSrc = iframe != null ? iframe.attr("src") : playUrl;
                    result.append("\"parse\":1,\"header\":\"\",\"playUrl\":\"\",\"url\":\"").append(esc(iframeSrc)).append("\"");
                }
            }
            result.append("}");
            return result.toString();
        } catch (Exception e) {
            return "{\"parse\":1,\"header\":\"\",\"playUrl\":\"\",\"url\":\"\"}";
        }
    }

    // ======================== 搜索 ========================

    @Override
    public String searchContent(String key, boolean quick) {
        try {
            String encodedKey = URLEncoder.encode(key, StandardCharsets.UTF_8.name());
            // 搜索URL: /vodsearch/{keyword}-------------.html
            String url = SITE_URL + "/vodsearch/" + encodedKey + "-------------.html";
            Document doc = fetchDoc(url);
            String listJson = parseListPage(doc);
            return "{\"list\":" + listJson + "}";
        } catch (Exception e) {
            return "{\"list\":[]}";
        }
    }

    @Override
    public boolean isVideoFormat(String url) {
        if (url == null || url.isEmpty()) return false;
        String l = url.toLowerCase();
        return l.contains(".m3u8") || l.contains(".mp4") || l.contains(".flv")
                || l.contains(".mkv") || l.contains(".avi") || l.contains(".ts");
    }

    // ======================== 核心解析: 列表页 ========================

    /**
     * 解析列表页 - 基于 stui 模板的实际HTML结构
     * ul.stui-vodlist > li > div.stui-vodlist__box
     *   a.stui-vodlist__thumb[data-original=图片, title=标题, href=/vod/{id}.html]
     *   span.pic-text > b (备注)
     *   div.stui-vodlist__detail > h4.title > a (标题链接)
     */
    private String parseListPage(Document doc) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;

        // 找所有 stui-vodlist__box
        Elements boxes = doc.select(".stui-vodlist__box");
        
        for (Element box : boxes) {
            String vodId = "", title = "", pic = "", remarks = "";

            // 1. 从缩略图链接获取: data-original(图片), title(标题), href(ID)
            Element thumb = box.selectFirst(".stui-vodlist__thumb");
            if (thumb != null) {
                String href = thumb.attr("abs:href");
                if (href.isEmpty()) href = thumb.attr("href");
                Matcher m = Pattern.compile("/vod/(\\d+)\\.html").matcher(href);
                if (m.find()) vodId = m.group(1);
                
                title = thumb.attr("title");
                if (title == null || title.isEmpty()) title = thumb.text().trim();
                
                // 图片在 data-original 属性上 (不是 img 标签!)
                pic = thumb.attr("data-original");
                if (pic == null || pic.isEmpty()) pic = thumb.attr("data-src");
                if (pic == null || pic.isEmpty()) {
                    // 兜底: 找 img 标签
                    Element img = thumb.selectFirst("img");
                    if (img != null) pic = extractImgSrc(img);
                }
                pic = normalizeUrl(pic);

                // 备注: span.pic-text > b
                Element remarksEl = box.selectFirst(".pic-text b, .pic-text");
                if (remarksEl != null) remarks = remarksEl.text().trim();
            }

            // 2. 如果没有缩略图链接，从 detail 区域获取标题和ID
            if (vodId.isEmpty()) {
                Element detailLink = box.selectFirst(".stui-vodlist__detail a[href*=/vod/], h4 a[href*=/vod/], .title a[href*=/vod/]");
                if (detailLink != null) {
                    String href = detailLink.attr("abs:href");
                    if (href.isEmpty()) href = detailLink.attr("href");
                    Matcher m = Pattern.compile("/vod/(\\d+)\\.html").matcher(href);
                    if (m.find()) vodId = m.group(1);
                    title = detailLink.attr("title");
                    if (title == null || title.isEmpty()) title = detailLink.text().trim();
                }
            }

            // 3. 如果标题还是空，再尝试 h4.title a
            if (title.isEmpty()) {
                Element titleEl = box.selectFirst("h4 a, .title a, h4.title a");
                if (titleEl != null) {
                    title = titleEl.attr("title");
                    if (title == null || title.isEmpty()) title = titleEl.text().trim();
                }
            }

            if (!vodId.isEmpty()) {
                if (!first) sb.append(",");
                first = false;
                sb.append("{");
                sb.append("\"vod_id\":\"").append(esc(vodId)).append("\",");
                sb.append("\"vod_name\":\"").append(esc(title)).append("\",");
                sb.append("\"vod_pic\":\"").append(esc(pic)).append("\",");
                sb.append("\"vod_remarks\":\"").append(esc(remarks)).append("\"");
                sb.append("}");
            }
        }

        // 兜底: 如果 stui-vodlist__box 没找到，用通用方法
        if (first) {
            sb.append(parseListGeneric(doc).substring(1));
        }

        sb.append("]");
        return sb.toString();
    }

    /**
     * 通用列表解析 (兜底)
     */
    private String parseListGeneric(Document doc) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;

        Elements vodLinks = doc.select("a[href*=/vod/]");
        Map<String, Element> idToLink = new LinkedHashMap<>();
        Map<String, String> idToTitle = new LinkedHashMap<>();

        for (Element link : vodLinks) {
            String href = link.attr("abs:href");
            if (href.isEmpty()) href = link.attr("href");
            Matcher m = Pattern.compile("/vod/(\\d+)\\.html").matcher(href);
            if (!m.find()) continue;
            String vodId = m.group(1);
            String title = link.attr("title");
            if (title == null || title.isEmpty()) title = link.text().trim();
            if (!idToLink.containsKey(vodId) || (!title.isEmpty() && (idToTitle.get(vodId) == null || idToTitle.get(vodId).isEmpty()))) {
                idToLink.put(vodId, link);
                idToTitle.put(vodId, title);
            }
        }

        for (Map.Entry<String, Element> entry : idToLink.entrySet()) {
            String vodId = entry.getKey();
            Element link = entry.getValue();
            String title = idToTitle.get(vodId);
            if (title == null) title = "";
            String pic = normalizeUrl(link.attr("data-original"));
            if (pic.isEmpty()) pic = findImgFromAncestors(link);
            String remarks = findRemarksFromAncestors(link);
            if (!first) sb.append(",");
            first = false;
            sb.append("{\"vod_id\":\"").append(esc(vodId)).append("\",\"vod_name\":\"").append(esc(title)).append("\",\"vod_pic\":\"").append(esc(pic)).append("\",\"vod_remarks\":\"").append(esc(remarks)).append("\"}");
        }
        return sb.toString();
    }

    // ======================== 核心解析: 详情页 ========================

    private HashMap<String, String> parseDetailInfo(Document doc) {
        HashMap<String, String> info = new HashMap<>();
        String fullText = doc.text();

        // 用正则从全文提取: "类型：xxx / 地区：xxx"
        info.put("type", extractField(fullText, "类型"));
        info.put("area", extractField(fullText, "地区"));
        String year = extractField(fullText, "年份");
        if (year.isEmpty()) year = extractField(fullText, "年代");
        info.put("year", year);
        String state = extractField(fullText, "状态");
        if (state.isEmpty()) state = extractField(fullText, "更新");
        info.put("remarks", state);
        info.put("lang", extractField(fullText, "语言"));
        info.put("director", extractField(fullText, "导演"));
        String actor = extractField(fullText, "主演");
        if (actor.isEmpty()) actor = extractField(fullText, "演员");
        info.put("actor", actor);
        String content = extractField(fullText, "简介");
        if (content.isEmpty()) content = extractField(fullText, "剧情");
        info.put("content", content);
        return info;
    }

    private String extractField(String fullText, String label) {
        // "标签：值" 到下一个标签或行尾
        Pattern p = Pattern.compile(label + "[：:]\\s*([^类型地区年份状态语言导演主演简介剧情介绍更新年代]*?)(?:\\s*/\\s*|$)");
        Matcher m = p.matcher(fullText);
        if (m.find()) {
            String v = m.group(1).trim();
            if (!v.isEmpty()) return v;
        }
        // 更简单: "标签：值" 到换行
        p = Pattern.compile(label + "[：:]\\s*([^\\n。]+)");
        m = p.matcher(fullText);
        if (m.find()) {
            String v = m.group(1).trim();
            if (v.length() > 200) {
                Matcher nextLabel = Pattern.compile("\\s+(类型|地区|年份|状态|语言|导演|主演|简介|剧情|更新)[：:]").matcher(v);
                if (nextLabel.find()) v = v.substring(0, nextLabel.start()).trim();
            }
            if (!v.isEmpty()) return v;
        }
        return "";
    }

    // ======================== 核心解析: 播放列表 ========================

    private String[] parsePlayList(Document doc, String vodId) {
        // 方式1: 找所有 /p/{vodId}-{flag}-{episode}.html 链接, 按 flag 分组
        Pattern playPattern = Pattern.compile("/p/" + vodId + "-(\\d+)-(\\d+)\\.html");
        Elements playLinks = doc.select("a[href*=/p/" + vodId + "-]");
        Map<String, List<String[]>> routeMap = new LinkedHashMap<>();

        for (Element link : playLinks) {
            String href = link.attr("abs:href");
            if (href.isEmpty()) href = link.attr("href");
            Matcher m = playPattern.matcher(href);
            if (m.find()) {
                String flag = m.group(1);
                String episode = m.group(2);
                String epName = link.text().trim();
                if (epName.isEmpty()) epName = "第" + episode + "集";
                if (!routeMap.containsKey(flag)) routeMap.put(flag, new ArrayList<>());
                routeMap.get(flag).add(new String[]{epName, href});
            }
        }

        if (!routeMap.isEmpty()) {
            StringBuilder fromSb = new StringBuilder();
            StringBuilder urlSb = new StringBuilder();
            boolean first = true;
            for (Map.Entry<String, List<String[]>> entry : routeMap.entrySet()) {
                String flag = entry.getKey();
                String routeName = findRouteName(doc, flag);
                if (routeName.isEmpty()) routeName = "播放" + flag;
                if (!first) { fromSb.append("$$$"); urlSb.append("$$$"); }
                first = false;
                fromSb.append(routeName);
                StringBuilder epList = new StringBuilder();
                for (int i = 0; i < entry.getValue().size(); i++) {
                    if (i > 0) epList.append("#");
                    epList.append(entry.getValue().get(i)[0]).append("$").append(entry.getValue().get(i)[1]);
                }
                urlSb.append(epList);
            }
            return new String[]{fromSb.toString(), urlSb.toString()};
        }

        // 方式2: 找所有 a[href*=/p/] 链接
        Elements allPlayLinks = doc.select("a[href*=/p/]");
        if (!allPlayLinks.isEmpty()) {
            StringBuilder epList = new StringBuilder();
            for (int i = 0; i < allPlayLinks.size(); i++) {
                Element ep = allPlayLinks.get(i);
                String epUrl = ep.attr("abs:href");
                if (epUrl.isEmpty()) epUrl = ep.attr("href");
                String epName = ep.text().trim();
                if (epName.isEmpty()) epName = "第" + (i + 1) + "集";
                if (i > 0) epList.append("#");
                epList.append(epName).append("$").append(epUrl);
            }
            return new String[]{"默认线路", epList.toString()};
        }

        return new String[]{"", ""};
    }

    private String findRouteName(Document doc, String flag) {
        // 从 a[href*=#playlist{flag}] 获取线路名称
        Elements links = doc.select("a[href*=#playlist" + flag + "]");
        for (Element link : links) {
            String text = link.text().trim();
            if (!text.isEmpty() && text.length() < 20) return text;
        }
        Element container = doc.selectFirst("#playlist" + flag);
        if (container != null) {
            Element title = container.selectFirst("h2, h3, h4, .title");
            if (title != null) return title.text().trim();
        }
        return "";
    }

    // ======================== 核心解析: player_aaaa ========================

    private String extractPlayerVar(String html, String varName) {
        int idx = -1;
        int varIdx = html.indexOf("var " + varName);
        if (varIdx >= 0) idx = html.indexOf("{", varIdx);
        if (idx < 0) {
            int plainIdx = html.indexOf(varName + " ");
            if (plainIdx >= 0) idx = html.indexOf("{", plainIdx);
        }
        if (idx < 0) {
            int plainIdx = html.indexOf(varName + "=");
            if (plainIdx >= 0) idx = html.indexOf("{", plainIdx);
        }
        if (idx < 0) return null;

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

    private String extractJsonValue(String json, String key) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
        Matcher m = p.matcher(json);
        if (m.find()) return m.group(1);
        p = Pattern.compile("\"" + key + "\"\\s*:\\s*(\\d+)");
        m = p.matcher(json);
        if (m.find()) return m.group(1);
        return "";
    }

    private String decodeVideoUrl(String url, int encrypt) {
        if (url == null || url.isEmpty()) return "";
        try {
            switch (encrypt) {
                case 0: return url.trim();
                case 1: return URLDecoder.decode(url, StandardCharsets.UTF_8.name()).trim();
                case 2:
                    byte[] d = Base64.getDecoder().decode(url.trim());
                    return new String(d, StandardCharsets.UTF_8).trim();
                default: return url.trim();
            }
        } catch (Exception e) { return url.trim(); }
    }

    private String extractDirectVideoUrl(String html) {
        Matcher m = Pattern.compile("https?://[^\"'\\s<>]+\\.m3u8[^\"'\\s<>]*", Pattern.CASE_INSENSITIVE).matcher(html);
        if (m.find()) return m.group();
        m = Pattern.compile("https?://[^\"'\\s<>]+\\.mp4[^\"'\\s<>]*", Pattern.CASE_INSENSITIVE).matcher(html);
        if (m.find()) return m.group();
        return "";
    }

    private boolean isDirectVideo(String url) {
        if (url == null || url.isEmpty()) return false;
        String l = url.toLowerCase();
        return l.contains(".m3u8") || l.contains(".mp4") || l.contains(".flv")
                || l.contains(".ts") || l.contains(".mkv") || l.contains(".avi");
    }

    // ======================== 图片提取 ========================

    /**
     * 从 img 元素提取 src
     */
    private String extractImgSrc(Element img) {
        if (img == null) return "";
        String[] attrs = {"data-original", "data-src", "data-lazy-src", "data-img",
                          "data-lazyload", "data-echo", "_src", "src"};
        for (String attr : attrs) {
            String src = img.attr(attr);
            if (src != null && !src.isEmpty()
                    && !src.contains("loading") && !src.contains("placeholder")
                    && !src.contains("blank") && !src.contains("default")) {
                return normalizeUrl(src);
            }
        }
        return "";
    }

    /**
     * 从链接向上搜索祖先节点找图片
     */
    private String findImgFromAncestors(Element link) {
        // 先检查链接自身的 data-original (stui 模板图片在 a 标签上)
        String src = link.attr("data-original");
        if (!src.isEmpty()) return normalizeUrl(src);
        src = link.attr("data-src");
        if (!src.isEmpty()) return normalizeUrl(src);

        // 检查链接内部 img
        Element img = link.selectFirst("img");
        if (img != null) {
            src = extractImgSrc(img);
            if (!src.isEmpty()) return src;
        }

        // 向上搜索祖先
        Element current = link.parent();
        for (int i = 0; i < 10 && current != null; i++) {
            // 检查祖先自身的 data-original
            String attr = current.attr("data-original");
            if (!attr.isEmpty()) return normalizeUrl(attr);

            Elements imgs = current.select("img");
            for (Element imgEl : imgs) {
                src = extractImgSrc(imgEl);
                if (!src.isEmpty()) return src;
            }
            current = current.parent();
        }
        return "";
    }

    /**
     * 从链接附近搜索备注
     */
    private String findRemarksFromAncestors(Element link) {
        // 检查 .pic-text
        Element picText = link.parent().selectFirst(".pic-text, .pic-text b");
        if (picText != null) return picText.text().trim();

        // 向上搜索
        Element current = link.parent();
        for (int i = 0; i < 5 && current != null; i++) {
            for (Element child : current.children()) {
                if (child.equals(link)) continue;
                Element pt = child.selectFirst(".pic-text, .pic-text b");
                if (pt != null) return pt.text().trim();
            }
            current = current.parent();
        }
        return "";
    }

    /**
     * 详情页封面图
     */
    private String findDetailPic(Document doc) {
        String[] selectors = {
            ".stui-content__thumb", ".stui-vodlist__thumb",
            ".module-info-pic img", ".video-pic img",
            ".detail-pic img", ".thumbnail img"
        };
        for (String sel : selectors) {
            Element el = doc.selectFirst(sel);
            if (el != null) {
                String src = el.attr("data-original");
                if (src.isEmpty()) src = el.attr("data-src");
                if (src.isEmpty()) src = extractImgSrc(el);
                if (!src.isEmpty()) return normalizeUrl(src);
            }
        }
        Elements imgs = doc.select("img");
        for (Element img : imgs) {
            String src = extractImgSrc(img);
            if (!src.isEmpty() && (src.contains("upload") || src.contains("pic"))) return src;
        }
        return "";
    }

    // ======================== 分页 ========================

    private int parsePageCount(Document doc) {
        // "1/5756" 格式
        String fullText = doc.text();
        Matcher m = Pattern.compile("(\\d+)\\s*/\\s*(\\d+)").matcher(fullText);
        if (m.find()) {
            int total = parseInt(m.group(2), 0);
            if (total > 0) return total;
        }
        // 尾页链接
        Elements lastLinks = doc.select("a:contains(尾页)");
        for (Element link : lastLinks) {
            String href = link.attr("abs:href");
            if (href.isEmpty()) href = link.attr("href");
            Matcher pageM = Pattern.compile("(\\d+)---\\.html").matcher(href);
            if (pageM.find()) return parseInt(pageM.group(1), 1);
        }
        // 所有分页链接中最大页码
        Elements pageLinks = doc.select("a[href*=---.html]");
        int maxPage = 1;
        for (Element link : pageLinks) {
            String href = link.attr("abs:href");
            if (href.isEmpty()) href = link.attr("href");
            Matcher pageM = Pattern.compile("(\\d+)---\\.html").matcher(href);
            if (pageM.find()) {
                int p = parseInt(pageM.group(1), 1);
                if (p > maxPage) maxPage = p;
            }
        }
        return maxPage;
    }

    // ======================== 工具方法 ========================

    private Document fetchDoc(String url) throws Exception {
        return Jsoup.connect(url)
                .userAgent(UA)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .header("Referer", SITE_URL)
                .header("Connection", "keep-alive")
                .timeout(TIMEOUT)
                .maxBodySize(0)
                .get();
    }

    private String normalizeUrl(String url) {
        if (url == null || url.isEmpty()) return "";
        url = url.trim();
        if (url.startsWith("//")) return "https:" + url;
        if (url.startsWith("/")) return SITE_URL + url;
        if (!url.startsWith("http")) return SITE_URL + "/" + url;
        return url;
    }

    private int parseInt(String str, int def) {
        try {
            if (str == null || str.isEmpty()) return def;
            return Integer.parseInt(str.trim());
        } catch (Exception e) { return def; }
    }

    private String esc(String str) {
        if (str == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.toString();
    }
}
