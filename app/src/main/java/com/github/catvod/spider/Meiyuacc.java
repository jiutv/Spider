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
 * TVBox Spider for meiyuacct.com (美影视)
 * 基于 lushunming/AndroidCatVodSpider 的 Spider 基类
 * 基于 MacCMS (苹果CMS) 标准模板结构开发
 *
 * URL 模式：
 *   分类列表: /vodshow/{type_id}--------{page}---.html
 *   详情页:   /vod/{vod_id}.html
 *   播放页:   /p/{vod_id}-{flag}-{episode}.html
 *   搜索:    /vodsearch/{keyword}----------{page}---.html
 *
 * 分类ID: 1=电影 2=电视剧 3=综艺 4=动漫 26=短剧
 */
public class Meiyuacc extends Spider {

    private static final String SITE_URL = "https://www.meiyuacct.com";
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final int TIMEOUT = 15000;

    // ======================== 初始化 ========================

    @Override
    public void init(Context context) {
    }

    // ======================== 首页内容 ========================

    @Override
    public String homeContent(boolean filter) {
        // 分类列表
        String[][] categories = {
            {"1", "电影"},
            {"2", "电视剧"},
            {"3", "综艺"},
            {"4", "动漫"},
            {"26", "短剧"}
        };

        StringBuilder classJson = new StringBuilder("[");
        for (int i = 0; i < categories.length; i++) {
            if (i > 0) classJson.append(",");
            classJson.append("{\"type_id\":\"").append(categories[i][0]).append("\"")
                    .append(",\"type_name\":\"").append(categories[i][1]).append("\"}");
        }
        classJson.append("]");

        // 首页推荐列表 (爬取电影分类第一页)
        String listJson = "[]";
        try {
            listJson = fetchVideoListJson(SITE_URL + "/vodshow/1--------1---.html");
        } catch (Exception ignored) {
        }

        // 组装结果
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

    // ======================== 筛选定义 ========================

    private String buildFiltersJson() {
        StringBuilder sb = new StringBuilder("{");

        sb.append("\"1\":[");
        sb.append(buildFilterItem("class", "类型", new String[][]{
            {"全部", ""}, {"动作", "动作"}, {"喜剧", "喜剧"}, {"爱情", "爱情"},
            {"科幻", "科幻"}, {"剧情", "剧情"}, {"悬疑", "悬疑"}, {"惊悚", "惊悚"},
            {"恐怖", "恐怖"}, {"犯罪", "犯罪"}, {"冒险", "冒险"}, {"奇幻", "奇幻"},
            {"战争", "战争"}, {"历史", "历史"}, {"音乐", "音乐"}, {"纪录片", "纪录片"}
        })).append(",");
        sb.append(buildFilterItem("area", "地区", new String[][]{
            {"全部", ""}, {"中国", "中国"}, {"中国香港", "中国香港"}, {"中国台湾", "中国台湾"},
            {"美国", "美国"}, {"日本", "日本"}, {"韩国", "韩国"}, {"法国", "法国"},
            {"英国", "英国"}, {"德国", "德国"}, {"印度", "印度"}, {"其他", "其他"}
        })).append(",");
        sb.append(buildFilterItem("year", "年份", new String[][]{
            {"全部", ""}, {"2026", "2026"}, {"2025", "2025"}, {"2024", "2024"},
            {"2023", "2023"}, {"2022", "2022"}, {"2021", "2021"}, {"2020", "2020"},
            {"2019", "2019"}, {"2018", "2018"}, {"2017", "2017"}, {"更早", "更早"}
        })).append(",");
        sb.append(buildFilterItem("by", "排序", new String[][]{
            {"按时间", "time"}, {"按热度", "hits"}, {"按评分", "score"}
        }));
        sb.append("]");

        sb.append(",\"2\":[");
        sb.append(buildFilterItem("class", "类型", new String[][]{
            {"全部", ""}, {"剧情", "剧情"}, {"悬疑", "悬疑"}, {"犯罪", "犯罪"},
            {"动作", "动作"}, {"爱情", "爱情"}, {"喜剧", "喜剧"}, {"科幻", "科幻"},
            {"奇幻", "奇幻"}, {"历史", "历史"}, {"战争", "战争"}, {"都市", "都市"},
            {"家庭", "家庭"}, {"古装", "古装"}, {"武侠", "武侠"}
        })).append(",");
        sb.append(buildFilterItem("area", "地区", new String[][]{
            {"全部", ""}, {"中国", "中国"}, {"中国香港", "中国香港"}, {"中国台湾", "中国台湾"},
            {"美国", "美国"}, {"日本", "日本"}, {"韩国", "韩国"}, {"英国", "英国"},
            {"泰国", "泰国"}, {"其他", "其他"}
        })).append(",");
        sb.append(buildFilterItem("year", "年份", new String[][]{
            {"全部", ""}, {"2026", "2026"}, {"2025", "2025"}, {"2024", "2024"},
            {"2023", "2023"}, {"2022", "2022"}, {"2021", "2021"}, {"2020", "2020"},
            {"2019", "2019"}, {"2018", "2018"}, {"更早", "更早"}
        })).append(",");
        sb.append(buildFilterItem("by", "排序", new String[][]{
            {"按时间", "time"}, {"按热度", "hits"}, {"按评分", "score"}
        }));
        sb.append("]");

        sb.append(",\"3\":[");
        sb.append(buildFilterItem("class", "类型", new String[][]{
            {"全部", ""}, {"选秀", "选秀"}, {"真人秀", "真人秀"}, {"音乐", "音乐"},
            {"搞笑", "搞笑"}, {"访谈", "访谈"}, {"美食", "美食"}, {"旅游", "旅游"},
            {"竞技", "竞技"}, {"纪实", "纪实"}
        })).append(",");
        sb.append(buildFilterItem("area", "地区", new String[][]{
            {"全部", ""}, {"中国", "中国"}, {"中国香港", "中国香港"}, {"中国台湾", "中国台湾"},
            {"日本", "日本"}, {"韩国", "韩国"}, {"美国", "美国"}, {"其他", "其他"}
        })).append(",");
        sb.append(buildFilterItem("year", "年份", new String[][]{
            {"全部", ""}, {"2026", "2026"}, {"2025", "2025"}, {"2024", "2024"},
            {"2023", "2023"}, {"2022", "2022"}, {"2021", "2021"}, {"更早", "更早"}
        })).append(",");
        sb.append(buildFilterItem("by", "排序", new String[][]{
            {"按时间", "time"}, {"按热度", "hits"}, {"按评分", "score"}
        }));
        sb.append("]");

        sb.append(",\"4\":[");
        sb.append(buildFilterItem("class", "类型", new String[][]{
            {"全部", ""}, {"热血", "热血"}, {"冒险", "冒险"}, {"搞笑", "搞笑"},
            {"科幻", "科幻"}, {"奇幻", "奇幻"}, {"恋爱", "恋爱"}, {"校园", "校园"},
            {"机战", "机战"}, {"推理", "推理"}, {"治愈", "治愈"}, {"运动", "运动"}
        })).append(",");
        sb.append(buildFilterItem("area", "地区", new String[][]{
            {"全部", ""}, {"日本", "日本"}, {"中国", "中国"}, {"美国", "美国"},
            {"韩国", "韩国"}, {"其他", "其他"}
        })).append(",");
        sb.append(buildFilterItem("year", "年份", new String[][]{
            {"全部", ""}, {"2026", "2026"}, {"2025", "2025"}, {"2024", "2024"},
            {"2023", "2023"}, {"2022", "2022"}, {"2021", "2021"}, {"2020", "2020"},
            {"更早", "更早"}
        })).append(",");
        sb.append(buildFilterItem("by", "排序", new String[][]{
            {"按时间", "time"}, {"按热度", "hits"}, {"按评分", "score"}
        }));
        sb.append("]");

        sb.append(",\"26\":[");
        sb.append(buildFilterItem("class", "类型", new String[][]{
            {"全部", ""}, {"都市", "都市"}, {"甜宠", "甜宠"}, {"复仇", "复仇"},
            {"穿越", "穿越"}, {"重生", "重生"}, {"逆袭", "逆袭"}, {"虐恋", "虐恋"}
        })).append(",");
        sb.append(buildFilterItem("area", "地区", new String[][]{
            {"全部", ""}, {"中国", "中国"}, {"其他", "其他"}
        })).append(",");
        sb.append(buildFilterItem("year", "年份", new String[][]{
            {"全部", ""}, {"2026", "2026"}, {"2025", "2025"}, {"2024", "2024"},
            {"2023", "2023"}, {"更早", "更早"}
        })).append(",");
        sb.append(buildFilterItem("by", "排序", new String[][]{
            {"按时间", "time"}, {"按热度", "hits"}, {"按评分", "score"}
        }));
        sb.append("]");

        sb.append("}");
        return sb.toString();
    }

    private String buildFilterItem(String key, String name, String[][] options) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"key\":\"").append(key).append("\",");
        sb.append("\"name\":\"").append(name).append("\",");
        sb.append("\"value\":[");
        for (int i = 0; i < options.length; i++) {
            if (i > 0) sb.append(",");
            sb.append("{\"n\":\"").append(escapeJson(options[i][0])).append("\",");
            sb.append("\"v\":\"").append(escapeJson(options[i][1])).append("\"}");
        }
        sb.append("]}");
        return sb.toString();
    }

    // ======================== 分类列表 ========================

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        try {
            int page = parseInt(pg, 1);

            String classFilter = "";
            String areaFilter = "";
            String langFilter = "";
            String yearFilter = "";
            String byFilter = "";

            if (extend != null) {
                if (extend.containsKey("class")) classFilter = extend.get("class");
                if (extend.containsKey("area")) areaFilter = extend.get("area");
                if (extend.containsKey("lang")) langFilter = extend.get("lang");
                if (extend.containsKey("year")) yearFilter = extend.get("year");
                if (extend.containsKey("by")) byFilter = extend.get("by");
            }

            String url = SITE_URL + "/vodshow/" + tid
                    + "-" + classFilter
                    + "-" + areaFilter
                    + "-" + langFilter
                    + "-" + yearFilter
                    + "-" + byFilter
                    + "-" + "-" + page + "---.html";

            Document doc = fetchDoc(url);
            String listJson = parseVideoListJson(doc);
            int pageCount = parsePageCount(doc);
            int limit = 12;

            StringBuilder result = new StringBuilder();
            result.append("{");
            result.append("\"page\":").append(page).append(",");
            result.append("\"pagecount\":").append(pageCount).append(",");
            result.append("\"limit\":").append(limit).append(",");
            result.append("\"total\":").append(pageCount * limit).append(",");
            result.append("\"list\":").append(listJson);
            result.append("}");

            return result.toString();

        } catch (Exception e) {
            return errorPageResult(pg);
        }
    }

    // ======================== 详情页 ========================

    @Override
    public String detailContent(List<String> ids) {
        try {
            String vodId = ids.get(0);
            String url = SITE_URL + "/vod/" + vodId + ".html";
            Document doc = fetchDoc(url);

            // 提取标题 - 从 h1 或 title 标签
            String title = "";
            Element h1 = doc.selectFirst("h1");
            if (h1 != null) {
                title = h1.text().trim();
            }
            if (title.isEmpty()) {
                title = doc.title();
            }

            // 提取封面图片 - 搜索所有 img，找最像封面的
            String pic = findDetailPic(doc);

            // 提取详细信息 - 用全文 regex
            HashMap<String, String> info = parseDetailInfo(doc);

            // 提取播放线路和剧集
            String[] playData = parsePlayList(doc, vodId);

            StringBuilder vod = new StringBuilder();
            vod.append("{");
            vod.append("\"vod_id\":\"").append(escapeJson(vodId)).append("\",");
            vod.append("\"vod_name\":\"").append(escapeJson(title)).append("\",");
            vod.append("\"vod_pic\":\"").append(escapeJson(pic)).append("\",");
            vod.append("\"vod_remarks\":\"").append(escapeJson(info.get("remarks"))).append("\",");
            vod.append("\"type_name\":\"").append(escapeJson(info.get("type"))).append("\",");
            vod.append("\"vod_area\":\"").append(escapeJson(info.get("area"))).append("\",");
            vod.append("\"vod_year\":\"").append(escapeJson(info.get("year"))).append("\",");
            vod.append("\"vod_director\":\"").append(escapeJson(info.get("director"))).append("\",");
            vod.append("\"vod_actor\":\"").append(escapeJson(info.get("actor"))).append("\",");
            vod.append("\"vod_content\":\"").append(escapeJson(info.get("content"))).append("\",");
            vod.append("\"vod_play_from\":\"").append(escapeJson(playData[0])).append("\",");
            vod.append("\"vod_play_url\":\"").append(escapeJson(playData[1])).append("\"");
            vod.append("}");

            return "{\"list\":[" + vod.toString() + "]}";

        } catch (Exception e) {
            return "{\"list\":[]}";
        }
    }

    // ======================== 播放解析 ========================

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        try {
            String playUrl;
            if (id.startsWith("http")) {
                playUrl = id;
            } else {
                playUrl = SITE_URL + (id.startsWith("/") ? id : "/" + id);
            }

            Document doc = fetchDoc(playUrl);
            String html = doc.html();

            // 解析 MacCMS 标准的 player_aaaa 变量
            String playerJson = extractPlayerVar(html, "player_aaaa");
            if (playerJson == null || playerJson.isEmpty()) {
                playerJson = extractPlayerVar(html, "player_data");
            }

            StringBuilder result = new StringBuilder();
            result.append("{");

            if (playerJson != null && !playerJson.isEmpty()) {
                String videoUrl = extractJsonValue(playerJson, "url");
                String encryptStr = extractJsonValue(playerJson, "encrypt");
                int encrypt = parseInt(encryptStr, 0);

                videoUrl = decodeVideoUrl(videoUrl, encrypt);

                int parse = isDirectVideo(videoUrl) ? 0 : 1;

                result.append("\"parse\":").append(parse).append(",");
                result.append("\"header\":\"{\\\"User-Agent\\\":\\\"").append(escapeJson(UA)).append("\\\",\\\"Referer\\\":\\\"").append(escapeJson(SITE_URL)).append("\\\"}\",");
                result.append("\"playUrl\":\"\",");
                result.append("\"url\":\"").append(escapeJson(videoUrl)).append("\"");

            } else {
                // 尝试直接从页面中提取视频URL
                String directUrl = extractDirectVideoUrl(html);
                if (!directUrl.isEmpty()) {
                    result.append("\"parse\":0,");
                    result.append("\"header\":\"\",");
                    result.append("\"playUrl\":\"\",");
                    result.append("\"url\":\"").append(escapeJson(directUrl)).append("\"");
                } else {
                    // 尝试查找 iframe
                    Element iframe = doc.selectFirst("iframe[src]");
                    String iframeSrc = iframe != null ? iframe.attr("src") : playUrl;
                    result.append("\"parse\":1,");
                    result.append("\"header\":\"\",");
                    result.append("\"playUrl\":\"\",");
                    result.append("\"url\":\"").append(escapeJson(iframeSrc)).append("\"");
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
            String url = SITE_URL + "/vodsearch/" + encodedKey + "----------1---.html";

            Document doc = fetchDoc(url);
            String listJson = parseVideoListJson(doc);

            return "{\"list\":" + listJson + "}";

        } catch (Exception e) {
            return "{\"list\":[]}";
        }
    }

    // ======================== 辅助方法 ========================

    @Override
    public boolean isVideoFormat(String url) {
        if (url == null || url.isEmpty()) return false;
        String lower = url.toLowerCase();
        return lower.contains(".m3u8") || lower.contains(".mp4") || lower.contains(".flv")
                || lower.contains(".mkv") || lower.contains(".avi") || lower.contains(".ts")
                || lower.contains(".mov") || lower.contains(".wmv") || lower.contains(".m4a");
    }

    /**
     * 获取HTTP文档
     */
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

    /**
     * 从 URL 获取视频列表 JSON
     */
    private String fetchVideoListJson(String url) throws Exception {
        Document doc = fetchDoc(url);
        return parseVideoListJson(doc);
    }

    // ======================== 核心解析方法 (重写) ========================

    /**
     * 解析视频列表 - 基于链接的通用解析方法
     * 不依赖特定 CSS 类名，通过 a[href*=/vod/] 链接定位
     */
    private String parseVideoListJson(Document doc) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");

        // 1. 找到所有 /vod/{id}.html 链接
        Elements vodLinks = doc.select("a[href*=/vod/]");
        
        // 2. 按 vod_id 去重，保留信息最丰富的链接
        Map<String, Element> vodIdToLink = new LinkedHashMap<>();
        Map<String, String> vodIdToTitle = new LinkedHashMap<>();
        
        for (Element link : vodLinks) {
            String href = link.attr("abs:href");
            if (href.isEmpty()) href = link.attr("href");
            
            Matcher m = Pattern.compile("/vod/(\\d+)\\.html").matcher(href);
            if (!m.find()) continue;
            
            String vodId = m.group(1);
            
            // 获取标题: 优先 title 属性，其次文本
            String title = link.attr("title");
            if (title == null || title.isEmpty()) {
                title = link.text().trim();
            }
            
            // 如果之前没记录过，或者新链接有 title 属性而旧的没有
            if (!vodIdToLink.containsKey(vodId)) {
                vodIdToLink.put(vodId, link);
                vodIdToTitle.put(vodId, title);
            } else {
                // 保留 title 属性更丰富的链接
                String oldTitle = vodIdToTitle.get(vodId);
                if ((oldTitle == null || oldTitle.isEmpty()) && !title.isEmpty()) {
                    vodIdToLink.put(vodId, link);
                    vodIdToTitle.put(vodId, title);
                }
            }
        }

        boolean first = true;
        for (Map.Entry<String, Element> entry : vodIdToLink.entrySet()) {
            String vodId = entry.getKey();
            Element link = entry.getValue();
            String title = vodIdToTitle.get(vodId);
            if (title == null) title = "";

            // 3. 搜索图片: 从链接向上找祖先节点中的 img
            String pic = findImgFromAncestors(link);

            // 4. 搜索备注: 从链接及附近元素找状态文本
            String remarks = findRemarksFromAncestors(link);
            
            // 5. 如果标题还是空的，从附近的 h 标签获取
            if (title.isEmpty()) {
                title = findTitleFromAncestors(link);
            }

            if (!title.isEmpty() || !vodId.isEmpty()) {
                if (!first) sb.append(",");
                first = false;
                sb.append("{");
                sb.append("\"vod_id\":\"").append(escapeJson(vodId)).append("\",");
                sb.append("\"vod_name\":\"").append(escapeJson(title)).append("\",");
                sb.append("\"vod_pic\":\"").append(escapeJson(pic)).append("\",");
                sb.append("\"vod_remarks\":\"").append(escapeJson(remarks)).append("\"");
                sb.append("}");
            }
        }

        sb.append("]");
        return sb.toString();
    }

    /**
     * 从链接向上搜索祖先节点，找到第一个包含 img 的节点，返回图片URL
     */
    private String findImgFromAncestors(Element link) {
        // 先检查链接自身内部
        Element img = link.selectFirst("img");
        if (img != null) {
            String src = extractImgSrc(img);
            if (!src.isEmpty()) return src;
        }

        // 向上搜索祖先节点 (最多10层)
        Element current = link.parent();
        for (int i = 0; i < 10 && current != null; i++) {
            // 检查当前节点下的 img
            Elements imgs = current.select("img");
            for (Element imgEl : imgs) {
                String src = extractImgSrc(imgEl);
                if (!src.isEmpty()) return src;
            }
            
            // 检查兄弟节点中的 img
            for (Element sibling : current.children()) {
                if (sibling.equals(link)) continue;
                Elements siblingImgs = sibling.select("img");
                for (Element imgEl : siblingImgs) {
                    String src = extractImgSrc(imgEl);
                    if (!src.isEmpty()) return src;
                }
            }
            
            current = current.parent();
        }

        return "";
    }

    /**
     * 从 img 元素提取 src (支持各种懒加载属性)
     */
    private String extractImgSrc(Element img) {
        if (img == null) return "";
        
        // 按优先级尝试各种 src 属性
        String[] attrs = {"data-src", "data-original", "data-lazy-src", "data-img", 
                          "data-bg", "lay-src", "lazysrc", "src"};
        
        for (String attr : attrs) {
            String src = img.attr(attr);
            if (src != null && !src.isEmpty() && !src.endsWith("loading.gif") 
                    && !src.endsWith("placeholder") && !src.contains("blank.gif")) {
                return normalizeUrl(src);
            }
        }
        
        // 检查 style 属性中的 background-image
        String style = img.attr("style");
        if (style != null && style.contains("url(")) {
            Matcher m = Pattern.compile("url\\(['\"]?([^'\"\\)]+)['\"]?\\)").matcher(style);
            if (m.find()) {
                return normalizeUrl(m.group(1));
            }
        }
        
        return "";
    }

    /**
     * 规范化 URL (处理相对路径)
     */
    private String normalizeUrl(String url) {
        if (url == null || url.isEmpty()) return "";
        url = url.trim();
        if (url.startsWith("//")) {
            return "https:" + url;
        } else if (url.startsWith("/")) {
            return SITE_URL + url;
        } else if (!url.startsWith("http")) {
            return SITE_URL + "/" + url;
        }
        return url;
    }

    /**
     * 从链接及附近元素搜索备注/状态文本
     * 如 "HD中字" "更新至TC" "已完结" 等
     */
    private String findRemarksFromAncestors(Element link) {
        // 检查链接自身的文本（可能包含状态信息）
        String linkText = link.text().trim();
        String remarks = extractRemarksFromText(linkText);
        if (!remarks.isEmpty()) return remarks;

        // 检查链接内的子元素文本
        for (Element child : link.children()) {
            String text = child.text().trim();
            remarks = extractRemarksFromText(text);
            if (!remarks.isEmpty()) return remarks;
        }

        // 向上搜索祖先节点中的状态文本
        Element current = link.parent();
        for (int i = 0; i < 5 && current != null; i++) {
            // 检查当前节点的直接子元素
            for (Element child : current.children()) {
                if (child.equals(link)) continue;
                String text = child.text().trim();
                remarks = extractRemarksFromText(text);
                if (!remarks.isEmpty()) return remarks;
                
                // 也检查子元素内的 span, em, label 等标签
                Elements tags = child.select("span, em, label, .tag, .badge, i, b, strong, p");
                for (Element tag : tags) {
                    String tagText = tag.text().trim();
                    remarks = extractRemarksFromText(tagText);
                    if (!remarks.isEmpty()) return remarks;
                }
            }
            current = current.parent();
        }

        return "";
    }

    /**
     * 从文本中提取备注信息
     */
    private String extractRemarksFromText(String text) {
        if (text == null || text.isEmpty()) return "";
        text = text.trim();
        
        // 匹配常见的状态标记
        if (text.matches(".*((HD|hd|TC|tc|BD|bd|DVD|dvd|VOD|vod)[中无]?(字|语)?).*")) {
            Matcher m = Pattern.compile("(更新至[\\d]+集|更新至[\\d]+期|已完结|完结|HD[中无]?字?|TC[中无]?字?|BD[中无]?字?|更新至TC|更新至HD|更新至BD|更新至全\\d+集|TC国语|TC中字|HD国语|HD无字)").matcher(text);
            if (m.find()) return m.group(1);
        }
        
        // 匹配 "更新至XX集" 格式
        Matcher m = Pattern.compile("更新至[\\d]+集").matcher(text);
        if (m.find()) return m.group();
        
        // 匹配 "已完结" "完结"
        if (text.contains("已完结") || text.contains("完结")) return "已完结";
        
        // 匹配 "HD中字" "TC中字" 等
        m = Pattern.compile("[A-Z]{2,}[中无]?[字语]?").matcher(text);
        if (m.find() && text.length() < 20) {
            return text;
        }
        
        return "";
    }

    /**
     * 从附近元素搜索标题 (h2-h6 标签)
     */
    private String findTitleFromAncestors(Element link) {
        Element current = link.parent();
        for (int i = 0; i < 5 && current != null; i++) {
            for (Element child : current.children()) {
                String tagName = child.tagName();
                if (tagName.equals("h2") || tagName.equals("h3") || tagName.equals("h4") 
                    || tagName.equals("h5") || tagName.equals("h6")) {
                    String text = child.text().trim();
                    if (!text.isEmpty()) return text;
                }
            }
            current = current.parent();
        }
        return "";
    }

    /**
     * 在详情页找到封面图片
     */
    private String findDetailPic(Document doc) {
        // 尝试多种 CSS 选择器
        String[] selectors = {
            ".module-info-pic img", ".stui-content__thumb img", 
            ".video-pic img", ".module-info-poster img",
            ".detail-pic img", ".vod-n img", ".content-thumb img",
            ".thumbnail img", ".poster img"
        };
        
        for (String sel : selectors) {
            Element img = doc.selectFirst(sel);
            if (img != null) {
                String src = extractImgSrc(img);
                if (!src.isEmpty()) return src;
            }
        }

        // 通用方式: 找所有 img，返回第一个有实际 src 的
        Elements imgs = doc.select("img");
        for (Element img : imgs) {
            String src = extractImgSrc(img);
            if (!src.isEmpty() && (src.contains("upload") || src.contains("pic") 
                    || src.contains("image") || src.contains("cover") || src.contains("poster"))) {
                return src;
            }
        }

        return "";
    }

    /**
     * 解析详情页信息 - 基于全文 regex 的通用方法
     */
    private HashMap<String, String> parseDetailInfo(Document doc) {
        HashMap<String, String> info = new HashMap<>();
        String fullText = doc.text();

        // 类型: "类型：剧情片" 或 "类型：剧情片 / 地区：印度"
        info.put("type", extractField(fullText, "类型"));
        
        // 地区
        info.put("area", extractField(fullText, "地区"));
        
        // 年份
        String year = extractField(fullText, "年份");
        if (year.isEmpty()) year = extractField(fullText, "年代");
        info.put("year", year);
        
        // 状态/备注
        String state = extractField(fullText, "状态");
        if (state.isEmpty()) state = extractField(fullText, "更新");
        info.put("remarks", state);
        
        // 语言
        info.put("lang", extractField(fullText, "语言"));
        
        // 导演
        info.put("director", extractField(fullText, "导演"));
        
        // 主演
        String actor = extractField(fullText, "主演");
        if (actor.isEmpty()) actor = extractField(fullText, "演员");
        info.put("actor", actor);
        
        // 简介
        String content = extractField(fullText, "简介");
        if (content.isEmpty()) content = extractField(fullText, "剧情");
        if (content.isEmpty()) content = extractField(fullText, "介绍");
        info.put("content", content);

        return info;
    }

    /**
     * 从全文中提取指定字段值
     * 处理格式: "标签：值" 或 "标签：值 / 标签2：值2"
     */
    private String extractField(String fullText, String label) {
        // 方式1: "标签：值" 后面跟换行或其他标签
        // 匹配到下一个 "标签：" 或行尾
        Pattern p = Pattern.compile(label + "[：:]\\s*([^类型地区年份状态语言导演主演简介剧情介绍更新年代]*?)(?:\\s*/\\s*|$)");
        Matcher m = p.matcher(fullText);
        if (m.find()) {
            String value = m.group(1).trim();
            if (!value.isEmpty()) return value;
        }
        
        // 方式2: "标签：值" 值到行尾或句号
        p = Pattern.compile(label + "[：:]\\s*([^\\n。]+)");
        m = p.matcher(fullText);
        if (m.find()) {
            String value = m.group(1).trim();
            // 如果值太长，可能匹配到了其他字段，截取到下一个标签
            if (value.length() > 200) {
                // 尝试截取到下一个 "标签："
                Matcher nextLabel = Pattern.compile("\\s+(类型|地区|年份|年代|状态|语言|导演|主演|简介|剧情|介绍|更新)[：:]").matcher(value);
                if (nextLabel.find()) {
                    value = value.substring(0, nextLabel.start()).trim();
                }
            }
            if (!value.isEmpty()) return value;
        }
        
        // 方式3: 简单匹配 "标签：值" 值为非空白字符
        p = Pattern.compile(label + "[：:]\\s*(\\S+)");
        m = p.matcher(fullText);
        if (m.find()) {
            return m.group(1).trim();
        }
        
        return "";
    }

    /**
     * 解析播放列表 - 通用方法
     */
    private String[] parsePlayList(Document doc, String vodId) {
        String playFrom = "";
        String playUrl = "";

        // 方式1: 找所有 /p/{vodId}-{flag}-{episode}.html 链接
        Pattern playPattern = Pattern.compile("/p/" + vodId + "-(\\d+)-(\\d+)\\.html");
        Elements playLinks = doc.select("a[href*=/p/" + vodId + "-]");

        // 按 flag 分组
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

                if (!routeMap.containsKey(flag)) {
                    routeMap.put(flag, new ArrayList<String[]>());
                }
                routeMap.get(flag).add(new String[]{epName, href});
            }
        }

        if (!routeMap.isEmpty()) {
            StringBuilder fromSb = new StringBuilder();
            StringBuilder urlSb = new StringBuilder();
            boolean first = true;

            for (Map.Entry<String, List<String[]>> entry : routeMap.entrySet()) {
                String flag = entry.getKey();
                List<String[]> episodes = entry.getValue();

                // 尝试找到线路名称
                String routeName = findRouteName(doc, flag);
                if (routeName.isEmpty()) routeName = "播放" + flag;

                if (!first) {
                    fromSb.append("$$$");
                    urlSb.append("$$$");
                }
                first = false;

                fromSb.append(routeName);

                StringBuilder epList = new StringBuilder();
                for (int i = 0; i < episodes.size(); i++) {
                    if (i > 0) epList.append("#");
                    epList.append(episodes.get(i)[0]).append("$").append(episodes.get(i)[1]);
                }
                urlSb.append(epList);
            }

            playFrom = fromSb.toString();
            playUrl = urlSb.toString();
            return new String[]{playFrom, playUrl};
        }

        // 方式2: 按 MacCMS 标准播放列表容器解析
        // 尝试多种容器选择器
        String[] containerSelectors = {
            ".module-player-list-content", ".stui-content__playlist", 
            ".playlist-content", ".module-play-list",
            "[id^=playlist]", ".play-list", ".playlist"
        };

        for (String sel : containerSelectors) {
            Elements containers = doc.select(sel);
            if (containers.isEmpty()) continue;

            StringBuilder fromSb = new StringBuilder();
            StringBuilder urlSb = new StringBuilder();
            boolean first = true;

            for (Element container : containers) {
                String routeName = "";
                // 尝试找到线路标题
                Element titleEl = container.selectFirst("h2, h3, h4, h5, .title, .module-player-list-title");
                if (titleEl != null) {
                    routeName = titleEl.text().trim();
                }
                if (routeName.isEmpty()) {
                    String containerId = container.attr("id");
                    if (containerId.startsWith("playlist")) {
                        routeName = "播放" + containerId.replace("playlist", "");
                    }
                }
                if (routeName.isEmpty()) routeName = "默认线路";

                Elements episodes = container.select("a");
                if (episodes.isEmpty()) continue;

                if (!first) {
                    fromSb.append("$$$");
                    urlSb.append("$$$");
                }
                first = false;

                fromSb.append(routeName);

                StringBuilder epList = new StringBuilder();
                for (int i = 0; i < episodes.size(); i++) {
                    Element ep = episodes.get(i);
                    String epUrl = ep.attr("abs:href");
                    if (epUrl.isEmpty()) epUrl = ep.attr("href");
                    String epName = ep.text().trim();
                    if (epName.isEmpty()) epName = "第" + (i + 1) + "集";

                    if (i > 0) epList.append("#");
                    epList.append(epName).append("$").append(epUrl);
                }
                urlSb.append(epList);
            }

            if (fromSb.length() > 0) {
                playFrom = fromSb.toString();
                playUrl = urlSb.toString();
                return new String[]{playFrom, playUrl};
            }
        }

        // 方式3: 查找所有 a[href*=/p/] 链接
        if (playFrom.isEmpty()) {
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
                playFrom = "默认线路";
                playUrl = epList.toString();
            }
        }

        return new String[]{playFrom, playUrl};
    }

    /**
     * 尝试从文档中找到播放线路名称
     */
    private String findRouteName(Document doc, String flag) {
        // 查找包含 playlist{flag} 的链接
        Elements links = doc.select("a[href*=playlist" + flag + "], a[href*=#playlist" + flag + "]");
        for (Element link : links) {
            String text = link.text().trim();
            if (!text.isEmpty() && !text.equals("播放") && text.length() < 20) {
                return text;
            }
        }
        
        // 查找 id=playlist{flag} 的容器标题
        Element container = doc.selectFirst("#playlist" + flag);
        if (container != null) {
            Element title = container.selectFirst("h2, h3, h4, .title, .module-player-list-title");
            if (title != null) {
                return title.text().trim();
            }
        }
        
        return "";
    }

    /**
     * 从HTML中提取 player_aaaa JavaScript变量
     * 支持嵌套 JSON 对象
     */
    private String extractPlayerVar(String html, String varName) {
        // 查找 var player_aaaa = {...} 或 player_aaaa = {...}
        // 使用平衡括号匹配，支持嵌套
        int idx = -1;
        
        // 先找 "var player_aaaa ="
        int varIdx = html.indexOf("var " + varName);
        if (varIdx >= 0) {
            idx = html.indexOf("{", varIdx);
        }
        
        // 再找 "player_aaaa ="
        if (idx < 0) {
            int plainIdx = html.indexOf(varName + " ");
            if (plainIdx >= 0) {
                idx = html.indexOf("{", plainIdx);
            }
        }
        
        // 再找 "player_aaaa="
        if (idx < 0) {
            int plainIdx = html.indexOf(varName + "=");
            if (plainIdx >= 0) {
                idx = html.indexOf("{", plainIdx);
            }
        }
        
        if (idx < 0) return null;

        // 平衡括号匹配
        int depth = 0;
        int start = idx;
        int end = -1;
        boolean inString = false;
        char stringChar = 0;

        for (int i = idx; i < html.length(); i++) {
            char c = html.charAt(i);
            
            if (inString) {
                if (c == '\\') {
                    i++; // 跳过转义字符
                } else if (c == stringChar) {
                    inString = false;
                }
            } else {
                if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        end = i;
                        break;
                    }
                } else if (c == '"' || c == '\'') {
                    inString = true;
                    stringChar = c;
                }
            }
        }

        if (end > start) {
            return html.substring(start, end + 1);
        }

        return null;
    }

    /**
     * 从 JSON 字符串中提取指定 key 的值
     */
    private String extractJsonValue(String json, String key) {
        // 先尝试字符串值
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
        Matcher m = p.matcher(json);
        if (m.find()) {
            return m.group(1);
        }
        // 尝试数字值
        p = Pattern.compile("\"" + key + "\"\\s*:\\s*(\\d+)");
        m = p.matcher(json);
        if (m.find()) {
            return m.group(1);
        }
        return "";
    }

    /**
     * 根据 encrypt 字段解码视频URL
     */
    private String decodeVideoUrl(String url, int encrypt) {
        if (url == null || url.isEmpty()) return "";
        try {
            switch (encrypt) {
                case 0:
                    return url.trim();
                case 1:
                    return URLDecoder.decode(url, StandardCharsets.UTF_8.name()).trim();
                case 2:
                    byte[] decoded = Base64.getDecoder().decode(url.trim());
                    return new String(decoded, StandardCharsets.UTF_8).trim();
                default:
                    return url.trim();
            }
        } catch (Exception e) {
            return url.trim();
        }
    }

    /**
     * 从HTML中直接提取视频URL
     */
    private String extractDirectVideoUrl(String html) {
        Pattern m3u8 = Pattern.compile("https?://[^\"'\\s<>]+\\.m3u8[^\"'\\s<>]*", Pattern.CASE_INSENSITIVE);
        Matcher m = m3u8.matcher(html);
        if (m.find()) return m.group();

        Pattern mp4 = Pattern.compile("https?://[^\"'\\s<>]+\\.mp4[^\"'\\s<>]*", Pattern.CASE_INSENSITIVE);
        m = mp4.matcher(html);
        if (m.find()) return m.group();

        return "";
    }

    /**
     * 判断是否为直接视频链接
     */
    private boolean isDirectVideo(String url) {
        if (url == null || url.isEmpty()) return false;
        String lower = url.toLowerCase();
        return lower.contains(".m3u8") || lower.contains(".mp4")
                || lower.contains(".flv") || lower.contains(".ts")
                || lower.contains(".mkv") || lower.contains(".avi");
    }

    /**
     * 解析分页总数 - 通用方法
     */
    private int parsePageCount(Document doc) {
        // 方式1: 从 "1/5756" 格式提取
        String fullText = doc.text();
        Matcher m = Pattern.compile("(\\d+)\\s*/\\s*(\\d+)").matcher(fullText);
        if (m.find()) {
            int total = parseInt(m.group(2), 0);
            if (total > 0) return total;
        }

        // 方式2: 从尾页链接中提取
        Elements lastLinks = doc.select("a:contains(尾页), a:contains(末页), a:contains(last), a:last-child");
        for (Element link : lastLinks) {
            String href = link.attr("abs:href");
            if (href.isEmpty()) href = link.attr("href");
            Matcher pageM = Pattern.compile("(\\d+)---\\.html").matcher(href);
            if (pageM.find()) {
                return parseInt(pageM.group(1), 1);
            }
        }

        // 方式3: 从所有分页链接中找最大页码
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

    // ======================== 通用工具方法 ========================

    private int parseInt(String str, int defaultValue) {
        try {
            if (str == null || str.isEmpty()) return defaultValue;
            return Integer.parseInt(str.trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private String escapeJson(String str) {
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
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    private String errorPageResult(String pg) {
        int page = parseInt(pg, 1);
        return "{\"page\":" + page + ",\"pagecount\":1,\"limit\":12,\"total\":0,\"list\":[]}";
    }
}
