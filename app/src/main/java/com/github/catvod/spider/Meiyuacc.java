package com.github.catvod.spider;

import android.content.Context;
import com.github.catvod.bean.Class;
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
        // 分类列表 (用 List<Class> 转 JSON 格式返回)
        List<Class> classes = new ArrayList<>();
        classes.add(new Class("1", "电影"));
        classes.add(new Class("2", "电视剧"));
        classes.add(new Class("3", "综艺"));
        classes.add(new Class("4", "动漫"));
        classes.add(new Class("26", "短剧"));

        StringBuilder classJson = new StringBuilder("[");
        for (int i = 0; i < classes.size(); i++) {
            if (i > 0) classJson.append(",");
            classJson.append("{\"type_id\":\"").append(classes.get(i).getTypeId()).append("\"")
                    .append(",\"type_name\":\"").append(classes.get(i).getTypeName()).append("\"}");
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
        result.append("\"class\":").append(classJson).append(",");
        result.append("\"list\":").append(listJson);
        result.append("}");

        return result.toString();
    }

    // ======================== 分类列表 ========================

    /**
     * @param tid    分类ID
     * @param pg     页码
     * @param filter 是否显示筛选 (true=显示筛选UI)
     * @param extend 筛选参数 HashMap (class/area/year/by 等)
     */
    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        try {
            int page = parseInt(pg, 1);

            // 从 extend HashMap 中获取筛选参数
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

            // MacCMS URL格式: /vodshow/{type_id}-{class}-{area}-{lang}-{year}-{by}-{letter}-{page}---.html
            String url = SITE_URL + "/vodshow/" + tid
                    + "-" + classFilter
                    + "-" + areaFilter
                    + "-" + langFilter
                    + "-" + yearFilter
                    + "-" + byFilter
                    + "-" + "-" + page + "---.html";

            Document doc = fetchDoc(url);
            String listJson = parseVideoListJson(doc);

            // 解析分页信息
            int pageCount = parsePageCount(doc);
            int limit = 12;

            // 组装结果
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

            // 提取标题
            String title = extractText(doc, ".module-info-heading h1, .video-info-title h1, .stui-content__detail .title, h1.title, .module-info-title");
            if (title.isEmpty()) {
                Element titleEl = doc.selectFirst("h1");
                title = titleEl != null ? titleEl.text().trim() : "";
            }

            // 提取封面图片
            String pic = extractImg(doc, ".module-info-pic img, .stui-content__thumb img, .video-pic img, .module-info-poster img");

            // 提取详细信息
            HashMap<String, String> info = parseDetailInfoMap(doc);

            // 提取播放线路和剧集
            String[] playData = parsePlayListData(doc, vodId);

            // 组装 vod JSON
            StringBuilder vod = new StringBuilder();
            vod.append("{");
            vod.append("\"vod_id\":\"").append(escapeJson(vodId)).append("\",");
            vod.append("\"vod_name\":\"").append(escapeJson(title)).append("\",");
            vod.append("\"vod_pic\":\"").append(escapeJson(pic)).append("\",");
            vod.append("\"vod_remarks\":\"").append(escapeJson(info.get("remarks") == null ? "" : info.get("remarks"))).append("\",");
            vod.append("\"type_name\":\"").append(escapeJson(info.get("type") == null ? "" : info.get("type"))).append("\",");
            vod.append("\"vod_area\":\"").append(escapeJson(info.get("area") == null ? "" : info.get("area"))).append("\",");
            vod.append("\"vod_year\":\"").append(escapeJson(info.get("year") == null ? "" : info.get("year"))).append("\",");
            vod.append("\"vod_director\":\"").append(escapeJson(info.get("director") == null ? "" : info.get("director"))).append("\",");
            vod.append("\"vod_actor\":\"").append(escapeJson(info.get("actor") == null ? "" : info.get("actor"))).append("\",");
            vod.append("\"vod_content\":\"").append(escapeJson(info.get("content") == null ? "" : info.get("content"))).append("\",");
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
            // 构建播放页URL
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
                // 简单解析 JSON 提取 url 和 encrypt
                String videoUrl = extractJsonValue(playerJson, "url");
                String encryptStr = extractJsonValue(playerJson, "encrypt");
                int encrypt = parseInt(encryptStr, 0);

                // 根据 encrypt 字段解码URL
                videoUrl = decodeVideoUrl(videoUrl, encrypt);

                // 判断是否需要解析
                int parse = isDirectVideo(videoUrl) ? 0 : 1;

                result.append("\"parse\":").append(parse).append(",");
                result.append("\"header\":\"{\\\"User-Agent\\\":\\\"").append(escapeJson(UA)).append("\\\"\\\",\\\"Referer\\\":\\\"").append(escapeJson(SITE_URL)).append("\\\"}\",");
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
     * 从 URL 获取视频列表 JSON 字符串
     */
    private String fetchVideoListJson(String url) throws Exception {
        Document doc = fetchDoc(url);
        return parseVideoListJson(doc);
    }

    /**
     * 从 Document 解析视频列表，返回 JSON 字符串
     */
    private String parseVideoListJson(Document doc) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");

        // 解析视频列表项 - 兼容多种 MacCMS 模板
        Elements items = doc.select(".module-item, .search-list-item, .stui-vodlist__box, .myui-vodlist__box, .vodlist_item, .module-search-item");

        // 如果没有找到，尝试更通用的方式
        if (items.isEmpty()) {
            items = new Elements();
            Elements links = doc.select("a[href*=/vod/]");
            for (Element link : links) {
                Element parent = link.parent();
                if (parent != null && parent.select("img").size() > 0) {
                    boolean alreadyAdded = false;
                    for (Element existing : items) {
                        if (existing.equals(parent)) { alreadyAdded = true; break; }
                    }
                    if (!alreadyAdded) {
                        items.add(parent);
                    }
                }
            }
        }

        boolean first = true;
        for (Element item : items) {
            HashMap<String, String> vod = parseListItemMap(item);
            if (vod != null && vod.containsKey("vod_id")) {
                if (!first) sb.append(",");
                first = false;
                sb.append("{");
                sb.append("\"vod_id\":\"").append(escapeJson(vod.get("vod_id"))).append("\",");
                sb.append("\"vod_name\":\"").append(escapeJson(vod.get("vod_name"))).append("\",");
                sb.append("\"vod_pic\":\"").append(escapeJson(vod.get("vod_pic"))).append("\",");
                sb.append("\"vod_remarks\":\"").append(escapeJson(vod.get("vod_remarks"))).append("\"");
                sb.append("}");
            }
        }

        sb.append("]");
        return sb.toString();
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
     * 解析列表项，返回 HashMap
     */
    private HashMap<String, String> parseListItemMap(Element item) {
        try {
            HashMap<String, String> vod = new HashMap<>();

            // 提取链接和ID
            Element link = item.selectFirst("a[href*=/vod/]");
            if (link == null) return null;

            String href = link.attr("href");
            Matcher idMatcher = Pattern.compile("/vod/(\\d+)\\.html").matcher(href);
            if (!idMatcher.find()) return null;

            String vodId = idMatcher.group(1);
            String title = link.attr("title");
            if (title == null || title.isEmpty()) {
                title = link.text().trim();
            }
            if (title.isEmpty()) {
                Element titleEl = item.selectFirst(".module-item-title, .title a, h4, h3, .stui-vodlist__detail .title a");
                if (titleEl != null) {
                    title = titleEl.text().trim();
                }
            }

            // 提取封面图
            String pic = extractImg(item, "img");

            // 提取备注
            String remarks = extractText(item, ".module-item-text, .pic-text, .remarks, .module-item-note, .tag");
            if (remarks.isEmpty()) {
                Element tagEl = item.selectFirst(".tag, .badge, .status, .quality, .label");
                if (tagEl != null) {
                    remarks = tagEl.text().trim();
                }
            }

            vod.put("vod_id", vodId);
            vod.put("vod_name", title);
            vod.put("vod_pic", pic);
            vod.put("vod_remarks", remarks);

            return vod;

        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 解析详情页信息，返回 HashMap
     */
    private HashMap<String, String> parseDetailInfoMap(Document doc) {
        HashMap<String, String> info = new HashMap<>();

        info.put("type", extractByLabel(doc, "类型"));
        info.put("area", extractByLabel(doc, "地区"));
        String year = extractByLabel(doc, "年份");
        if (year.isEmpty()) year = extractByLabel(doc, "年代");
        info.put("year", year);
        String state = extractByLabel(doc, "状态");
        if (state.isEmpty()) state = extractByLabel(doc, "更新");
        info.put("remarks", state);
        info.put("lang", extractByLabel(doc, "语言"));
        info.put("director", extractByLabel(doc, "导演"));
        String actor = extractByLabel(doc, "主演");
        if (actor.isEmpty()) actor = extractByLabel(doc, "演员");
        info.put("actor", actor);

        // 提取简介
        String content = extractByLabel(doc, "简介");
        if (content.isEmpty()) content = extractByLabel(doc, "剧情");
        if (content.isEmpty()) {
            Element descEl = doc.selectFirst(".module-info-introduction-content p, .video-info-introduction p, .stui-content__detail .desc, .content .detail-content");
            if (descEl != null) {
                content = descEl.text().trim();
            }
        }
        info.put("content", content);

        return info;
    }

    /**
     * 解析播放列表，返回 [playFrom, playUrl]
     */
    private String[] parsePlayListData(Document doc, String vodId) {
        String playFrom = "";
        String playUrl = "";

        // 查找播放路线选项卡
        Elements tabs = doc.select("a[href*=playlist], .module-player-list-tab a, .tab-list a, .playlist-tab a");

        // 查找所有播放链接 /p/{vodId}-{flag}-{episode}.html
        Pattern playPattern = Pattern.compile("/p/" + vodId + "-(\\d+)-(\\d+)\\.html");
        Elements playLinks = doc.select("a[href*=/p/" + vodId + "-]");

        // 按 flag 分组
        Map<String, List<String[]>> routeMap = new LinkedHashMap<>();

        if (!playLinks.isEmpty()) {
            for (Element link : playLinks) {
                String href = link.attr("href");
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
        }

        // 如果按链接分组成功
        if (!routeMap.isEmpty()) {
            StringBuilder fromSb = new StringBuilder();
            StringBuilder urlSb = new StringBuilder();
            boolean first = true;

            for (Map.Entry<String, List<String[]>> entry : routeMap.entrySet()) {
                String flag = entry.getKey();
                List<String[]> episodes = entry.getValue();

                // 尝试从选项卡中找到线路名称
                String routeName = "路线" + flag;
                for (Element tab : tabs) {
                    String tabHref = tab.attr("href");
                    if (tabHref.contains("playlist" + flag)) {
                        routeName = tab.text().trim();
                        break;
                    }
                }

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
        Elements playlistContainers = doc.select(
                ".module-player-list-content, .stui-content__playlist, .playlist-content, .module-play-list"
        );

        if (!playlistContainers.isEmpty()) {
            StringBuilder fromSb = new StringBuilder();
            StringBuilder urlSb = new StringBuilder();
            boolean first = true;

            for (Element container : playlistContainers) {
                String routeName = "";
                Element titleEl = container.selectFirst(".module-player-list-title, .playlist-title, .stui-content__playlist-title");
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
            }
        }

        // 方式3: 如果以上都失败, 尝试查找所有 a[href*=/p/] 链接
        if (playFrom.isEmpty()) {
            Elements allPlayLinks = doc.select("a[href*=/p/" + vodId + "-]");
            if (!allPlayLinks.isEmpty()) {
                StringBuilder epList = new StringBuilder();
                for (int i = 0; i < allPlayLinks.size(); i++) {
                    Element ep = allPlayLinks.get(i);
                    String epUrl = ep.attr("href");
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
     * 从HTML中提取 player_aaaa JavaScript变量
     */
    private String extractPlayerVar(String html, String varName) {
        // 匹配 var player_aaaa = {...}
        Pattern pattern = Pattern.compile(
                "var\\s+" + varName + "\\s*=\\s*(\\{[^}]*\\})",
                Pattern.DOTALL
        );
        Matcher matcher = pattern.matcher(html);
        if (matcher.find()) {
            return matcher.group(1);
        }

        // 匹配 player_aaaa = {...}
        pattern = Pattern.compile(
                varName + "\\s*=\\s*(\\{[^}]*\\})",
                Pattern.DOTALL
        );
        matcher = pattern.matcher(html);
        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }

    /**
     * 从 JSON 字符串中简单提取指定 key 的值
     */
    private String extractJsonValue(String json, String key) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher m = p.matcher(json);
        if (m.find()) {
            return m.group(1);
        }
        // 尝试数字格式
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
        Pattern m3u8Pattern = Pattern.compile(
                "https?://[^\"'\\s]+\\.m3u8[^\"'\\s]*",
                Pattern.CASE_INSENSITIVE
        );
        Matcher m = m3u8Pattern.matcher(html);
        if (m.find()) return m.group();

        Pattern mp4Pattern = Pattern.compile(
                "https?://[^\"'\\s]+\\.mp4[^\"'\\s]*",
                Pattern.CASE_INSENSITIVE
        );
        m = mp4Pattern.matcher(html);
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
     * 提取图片URL (支持懒加载属性)
     */
    private String extractImg(Element parent, String cssQuery) {
        Element img = parent.selectFirst(cssQuery);
        if (img == null) return "";
        String src = img.attr("data-src");
        if (src.isEmpty()) src = img.attr("data-original");
        if (src.isEmpty()) src = img.attr("data-lazy-src");
        if (src.isEmpty()) src = img.attr("src");
        if (src.startsWith("//")) {
            src = "https:" + src;
        } else if (src.startsWith("/")) {
            src = SITE_URL + src;
        }
        return src;
    }

    /**
     * 提取文本内容
     */
    private String extractText(Element parent, String cssQuery) {
        Element el = parent.selectFirst(cssQuery);
        return el != null ? el.text().trim() : "";
    }

    /**
     * 根据标签名提取信息
     */
    private String extractByLabel(Document doc, String label) {
        // 方式1: 查找包含标签名的元素
        Elements elements = doc.select(".module-info-item, .module-info-tag, .video-info-item, .stui-content__detail .data, .content-detail p, .info-item, dl dt, dl dd");

        for (Element el : elements) {
            String text = el.text().trim();
            if (text.startsWith(label)) {
                String value = text.substring(label.length());
                if (value.startsWith("：")) value = value.substring(1);
                if (value.startsWith(":")) value = value.substring(1);
                return value.trim();
            }
        }

        // 方式2: 在整个文档文本中搜索
        String fullText = doc.text();
        Pattern pattern = Pattern.compile(label + "[：:]\\s*([^（(\\s]+(?:\\s+[^（(\\s]+)*)");
        Matcher m = pattern.matcher(fullText);
        if (m.find()) {
            return m.group(1).trim();
        }

        return "";
    }

    /**
     * 解析分页总数
     */
    private int parsePageCount(Document doc) {
        // 方式1: 从 "1/5754" 格式提取
        Element pageInfo = doc.selectFirst(".page-info, .pagenation .page-info");
        if (pageInfo != null) {
            String info = pageInfo.text();
            Matcher m = Pattern.compile("(\\d+)\\s*/\\s*(\\d+)").matcher(info);
            if (m.find()) {
                return parseInt(m.group(2), 1);
            }
        }

        // 方式2: 从尾页链接中提取
        Element lastLink = doc.selectFirst("a:contains(尾页), a:contains(末页)");
        if (lastLink != null) {
            String href = lastLink.attr("href");
            Matcher m = Pattern.compile("(\\d+)---\\.html").matcher(href);
            if (m.find()) {
                return parseInt(m.group(1), 1);
            }
        }

        // 方式3: 从 "共xxx条结果" 提取
        String docText = doc.text();
        Matcher totalMatcher = Pattern.compile("(\\d+)\\s*条").matcher(docText);
        if (totalMatcher.find()) {
            int total = parseInt(totalMatcher.group(1), 0);
            if (total > 0) {
                return (total + 11) / 12;
            }
        }

        return 1;
    }

    /**
     * 安全的字符串转整数
     */
    private int parseInt(String str, int defaultValue) {
        try {
            if (str == null || str.isEmpty()) return defaultValue;
            return Integer.parseInt(str.trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * JSON 字符串转义
     */
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

    /**
     * 生成错误的分页返回结果
     */
    private String errorPageResult(String pg) {
        int page = parseInt(pg, 1);
        return "{\"page\":" + page + ",\"pagecount\":1,\"limit\":12,\"total\":0,\"list\":[]}";
    }
}
