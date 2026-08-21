package com.github.catvod.spider;

import com.github.catvod.crawler.Spider;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TVBox Spider for meiyuacct.com (美影视)
 * 基于 MacCMS (苹果CMS) 标准模板结构开发
 *
 * URL 模式：
 *   分类列表: /vodshow/{type_id}--------{page}---.html
 *   详情页:   /vod/{vod_id}.html
 *   播放页:   /p/{vod_id}-{flag}-{episode}.html
 *   搜索:    /vodsearch/{keyword}----------{page}---.html
 *
 * 分类ID:
 *   1=电影 2=电视剧 3=综艺 4=动漫 26=短剧
 */
public class Meiyuacc extends Spider {

    private static final String SITE_URL = "https://www.meiyuacct.com";
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final int TIMEOUT = 15000;

    // ======================== 首页内容 ========================

    /**
     * 首页内容: 返回分类列表 + 推荐视频
     * @param filter 是否包含筛选
     */
    @Override
    public String homeContent(boolean filter) {
        JSONObject result = new JSONObject();

        // 分类列表
        JSONArray classes = new JSONArray();
        classes.put(buildClass("1", "电影"));
        classes.put(buildClass("2", "电视剧"));
        classes.put(buildClass("3", "综艺"));
        classes.put(buildClass("4", "动漫"));
        classes.put(buildClass("26", "短剧"));
        result.put("class", classes);

        // 筛选配置
        if (filter) {
            JSONObject filters = new JSONObject();
            filters.put("1", getFilterArray("1"));
            filters.put("2", getFilterArray("2"));
            filters.put("3", getFilterArray("3"));
            filters.put("4", getFilterArray("4"));
            filters.put("26", getFilterArray("26"));
            result.put("filters", filters);
        }
        // 首页推荐列表 (爬取电影分类第一页)
        try {
            JSONArray list = fetchVideoList(SITE_URL + "/vodshow/1--------1---.html");
            result.put("list", list);
        } catch (Exception e) {
            result.put("list", new JSONArray());
        }

        return result.toString();
    }

    // ======================== 筛选条件 ========================

    /**
     * 返回分类筛选条件 (辅助方法，筛选数据同时在 homeContent 中返回)
     * @param tid 分类ID
     */
    private JSONArray getFilter(String tid) {
        return getFilterArray(tid);
    }

    /**
     * 获取分类筛选的 JSONArray
     */
    private JSONArray getFilterArray(String tid) {
        JSONArray filters = new JSONArray();

        switch (tid) {
            case "1": // 电影
                filters.put(buildFilter("class", "类型", new String[][]{
                    {"全部", ""}, {"动作片", "动作片"}, {"喜剧片", "喜剧片"},
                    {"爱情片", "爱情片"}, {"科幻片", "科幻片"}, {"剧情片", "剧情片"},
                    {"恐怖片", "恐怖片"}, {"战争片", "战争片"}, {"纪录片", "纪录片"},
                    {"悬疑片", "悬疑片"}, {"惊悚片", "惊悚片"}
                }));
                filters.put(buildFilter("area", "地区", new String[][]{
                    {"全部", ""}, {"中国", "中国"}, {"美国", "美国"}, {"英国", "英国"},
                    {"日本", "日本"}, {"韩国", "韩国"}, {"法国", "法国"},
                    {"印度", "印度"}, {"泰国", "泰国"}
                }));
                filters.put(buildFilter("year", "年份", new String[][]{
                    {"全部", ""}, {"2026", "2026"}, {"2025", "2025"}, {"2024", "2024"},
                    {"2023", "2023"}, {"2022", "2022"}, {"2021", "2021"},
                    {"2020", "2020"}, {"2019", "2019"}, {"2018", "2018"}
                }));
                filters.put(buildFilter("by", "排序", new String[][]{
                    {"最新", "time"}, {"热门", "hits"}, {"评分", "score"}
                }));
                break;

            case "2": // 电视剧
                filters.put(buildFilter("class", "类型", new String[][]{
                    {"全部", ""}, {"美剧", "美剧"}, {"泰剧", "泰剧"}, {"日剧", "日剧"},
                    {"韩剧", "韩剧"}, {"国产剧", "国产剧"}, {"港剧", "港剧"}, {"英剧", "英剧"}
                }));
                filters.put(buildFilter("area", "地区", new String[][]{
                    {"全部", ""}, {"中国", "中国"}, {"美国", "美国"},
                    {"日本", "日本"}, {"韩国", "韩国"}, {"泰国", "泰国"}, {"英国", "英国"}
                }));
                filters.put(buildFilter("year", "年份", new String[][]{
                    {"全部", ""}, {"2026", "2026"}, {"2025", "2025"}, {"2024", "2024"},
                    {"2023", "2023"}, {"2022", "2022"}, {"2021", "2021"}, {"2020", "2020"}
                }));
                filters.put(buildFilter("by", "排序", new String[][]{
                    {"最新", "time"}, {"热门", "hits"}, {"评分", "score"}
                }));
                break;

            case "3": // 综艺
                filters.put(buildFilter("class", "类型", new String[][]{
                    {"全部", ""}, {"综艺", "综艺"}, {"真人秀", "真人秀"},
                    {"脱口秀", "脱口秀"}, {"选秀", "选秀"}, {"情感", "情感"}
                }));
                filters.put(buildFilter("area", "地区", new String[][]{
                    {"全部", ""}, {"中国", "中国"}, {"美国", "美国"},
                    {"日本", "日本"}, {"韩国", "韩国"}
                }));
                filters.put(buildFilter("year", "年份", new String[][]{
                    {"全部", ""}, {"2026", "2026"}, {"2025", "2025"},
                    {"2024", "2024"}, {"2023", "2023"}
                }));
                filters.put(buildFilter("by", "排序", new String[][]{
                    {"最新", "time"}, {"热门", "hits"}, {"评分", "score"}
                }));
                break;

            case "4": // 动漫
                filters.put(buildFilter("class", "类型", new String[][]{
                    {"全部", ""}, {"动漫", "动漫"}, {"日韩动漫", "日韩动漫"},
                    {"国产动漫", "国产动漫"}, {"欧美动漫", "欧美动漫"}, {"动态漫画", "动态漫画"}
                }));
                filters.put(buildFilter("area", "地区", new String[][]{
                    {"全部", ""}, {"中国", "中国"}, {"日本", "日本"},
                    {"美国", "美国"}, {"韩国", "韩国"}
                }));
                filters.put(buildFilter("year", "年份", new String[][]{
                    {"全部", ""}, {"2026", "2026"}, {"2025", "2025"},
                    {"2024", "2024"}, {"2023", "2023"}
                }));
                filters.put(buildFilter("by", "排序", new String[][]{
                    {"最新", "time"}, {"热门", "hits"}, {"评分", "score"}
                }));
                break;

            case "26": // 短剧
                filters.put(buildFilter("class", "类型", new String[][]{
                    {"全部", ""}, {"短剧", "短剧"}
                }));
                filters.put(buildFilter("year", "年份", new String[][]{
                    {"全部", ""}, {"2026", "2026"}, {"2025", "2025"}, {"2024", "2024"}
                }));
                filters.put(buildFilter("by", "排序", new String[][]{
                    {"最新", "time"}, {"热门", "hits"}
                }));
                break;
        }

        return filters;
    }

    // ======================== 分类列表 ========================

    /**
     * 获取分类内容列表
     * @param tid    分类ID
     * @param pg     页码 (字符串)
     * @param filter 筛选条件 (JSONObject)
     * @param extend 是否扩展
     */
    @Override
    public String categoryContent(String tid, String pg, JSONObject filter, boolean extend) {
        try {
            int page = parseInt(pg, 1);

            // 解析筛选条件
            String classFilter = "";
            String areaFilter = "";
            String langFilter = "";
            String yearFilter = "";
            String byFilter = "";

            if (filter != null) {
                classFilter = filter.optString("class", "");
                areaFilter = filter.optString("area", "");
                langFilter = filter.optString("lang", "");
                yearFilter = filter.optString("year", "");
                byFilter = filter.optString("by", "");
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
            JSONArray list = parseVideoList(doc);

            // 解析分页信息
            int pageCount = parsePageCount(doc);
            int limit = list.length() > 0 ? list.length() : 12;

            JSONObject result = new JSONObject();
            result.put("page", page);
            result.put("pagecount", pageCount);
            result.put("limit", limit);
            result.put("total", pageCount * limit);
            result.put("list", list);

            return result.toString();

        } catch (Exception e) {
            return errorPageResult(pg);
        }
    }

    // ======================== 详情页 ========================

    /**
     * 获取视频详情
     * @param ids 视频ID列表
     */
    @Override
    public String detailContent(List<String> ids) {
        try {
            String vodId = ids.get(0);
            String url = SITE_URL + "/vod/" + vodId + ".html";
            Document doc = fetchDoc(url);

            JSONObject vod = new JSONObject();
            vod.put("vod_id", vodId);

            // 提取标题
            String title = extractText(doc, ".module-info-heading h1, .video-info-title h1, .stui-content__detail .title, h1.title, .module-info-title");
            if (title.isEmpty()) {
                Element titleEl = doc.selectFirst("h1");
                title = titleEl != null ? titleEl.text().trim() : "";
            }
            vod.put("vod_name", title);

            // 提取封面图片
            String pic = extractImg(doc, ".module-info-pic img, .stui-content__thumb img, .video-pic img, .module-info-poster img");
            vod.put("vod_pic", pic);

            // 提取详细信息 (类型/地区/年份/状态/语言/导演/主演/简介)
            parseDetailInfo(doc, vod);

            // 提取播放线路和剧集
            parsePlayList(doc, vod, vodId);

            JSONArray list = new JSONArray();
            list.put(vod);

            JSONObject result = new JSONObject();
            result.put("list", list);
            return result.toString();

        } catch (Exception e) {
            JSONObject result = new JSONObject();
            result.put("list", new JSONArray());
            return result.toString();
        }
    }

    // ======================== 播放解析 ========================

    /**
     * 获取播放地址
     * @param flag     播放线路名称
     * @param id       播放标识 (如 /p/175408-4-1.html)
     * @param vipFlags VIP 线路标志列表
     */
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

            JSONObject result = new JSONObject();

            if (playerJson != null && !playerJson.isEmpty()) {
                JSONObject player = new JSONObject(playerJson);

                // 获取视频URL
                String videoUrl = player.optString("url", "");
                int encrypt = player.optInt("encrypt", 0);

                // 根据 encrypt 字段解码URL
                videoUrl = decodeVideoUrl(videoUrl, encrypt);

                // 判断是否需要解析
                int parse = isDirectVideo(videoUrl) ? 0 : 1;

                result.put("parse", parse);
                result.put("header", getPlayHeaders());
                result.put("playUrl", "");
                result.put("url", videoUrl);

            } else {
                // 尝试直接从页面中提取视频URL
                String directUrl = extractDirectVideoUrl(html);
                if (!directUrl.isEmpty()) {
                    result.put("parse", 0);
                    result.put("header", "");
                    result.put("playUrl", "");
                    result.put("url", directUrl);
                } else {
                    // 尝试查找 iframe
                    Element iframe = doc.selectFirst("iframe[src]");
                    if (iframe != null) {
                        result.put("parse", 1);
                        result.put("header", "");
                        result.put("playUrl", "");
                        result.put("url", iframe.attr("src"));
                    } else {
                        // 默认返回播放页URL，让TVBox自行解析
                        result.put("parse", 1);
                        result.put("header", "");
                        result.put("playUrl", "");
                        result.put("url", playUrl);
                    }
                }
            }

            return result.toString();

        } catch (Exception e) {
            JSONObject result = new JSONObject();
            result.put("parse", 1);
            result.put("header", "");
            result.put("playUrl", "");
            result.put("url", "");
            return result.toString();
        }
    }

    // ======================== 搜索 ========================

    /**
     * 搜索影片
     * @param key   搜索关键词
     * @param quick 是否快速搜索
     */
    @Override
    public String searchContent(String key, boolean quick) {
        try {
            String encodedKey = URLEncoder.encode(key, StandardCharsets.UTF_8.name());
            String url = SITE_URL + "/vodsearch/" + encodedKey + "----------1---.html";

            Document doc = fetchDoc(url);
            JSONArray list = parseVideoList(doc);

            JSONObject result = new JSONObject();
            result.put("list", list);
            return result.toString();

        } catch (Exception e) {
            JSONObject result = new JSONObject();
            result.put("list", new JSONArray());
            return result.toString();
        }
    }

    // ======================== 辅助方法 ========================

    /**
     * 判断URL是否为直接视频格式
     */
    @Override
    public boolean isVideoFormat(String url) {
        if (url == null || url.isEmpty()) return false;
        String lower = url.toLowerCase();
        return lower.contains(".m3u8") || lower.contains(".mp4") || lower.contains(".flv")
                || lower.contains(".mkv") || lower.contains(".avi") || lower.contains(".ts")
                || lower.contains(".mov") || lower.contains(".wmv") || lower.contains(".m4a");
    }

    /**
     * 构建分类项
     */
    private JSONObject buildClass(String id, String name) {
        JSONObject cls = new JSONObject();
        cls.put("type_id", id);
        cls.put("type_name", name);
        return cls;
    }

    /**
     * 构建单个筛选条件项
     */
    private JSONObject buildFilter(String key, String name, String[][] options) {
        JSONObject filter = new JSONObject();
        filter.put("key", key);
        filter.put("name", name);

        JSONArray valueList = new JSONArray();
        for (String[] option : options) {
            JSONObject opt = new JSONObject();
            opt.put("n", option[0]);
            opt.put("v", option[1]);
            valueList.put(opt);
        }
        filter.put("value", valueList);

        return filter;
    }

    /**
     * 从 URL 获取视频列表 JSONArray
     */
    private JSONArray fetchVideoList(String url) throws Exception {
        Document doc = fetchDoc(url);
        return parseVideoList(doc);
    }

    /**
     * 从 Document 解析视频列表
     */
    private JSONArray parseVideoList(Document doc) {
        JSONArray list = new JSONArray();

        // 解析视频列表项 - 兼容多种 MacCMS 模板
        Elements items = doc.select(".module-item, .search-list-item, .stui-vodlist__box, .myui-vodlist__box, .vodlist_item, .module-search-item");

        // 如果没有找到，尝试更通用的方式: 找包含 /vod/ 链接且有图片的元素
        if (items.isEmpty()) {
            items = new Elements();
            Elements links = doc.select("a[href*=/vod/]");
            for (Element link : links) {
                Element parent = link.parent();
                if (parent != null && parent.select("img").size() > 0) {
                    // 避免重复添加
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

        for (Element item : items) {
            JSONObject vod = parseListItem(item);
            if (vod != null && vod.has("vod_id")) {
                list.put(vod);
            }
        }

        return list;
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
     * 解析列表项
     */
    private JSONObject parseListItem(Element item) {
        try {
            JSONObject vod = new JSONObject();

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

            // 提取备注 (HD中字/更新至xx集/已完结等)
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
     * 解析详情页信息
     */
    private void parseDetailInfo(Document doc, JSONObject vod) {
        // 提取类型
        String type = extractByLabel(doc, "类型");
        if (!type.isEmpty()) vod.put("type_name", type);

        // 提取地区
        String area = extractByLabel(doc, "地区");
        if (!area.isEmpty()) vod.put("vod_area", area);

        // 提取年份
        String year = extractByLabel(doc, "年份");
        if (year.isEmpty()) year = extractByLabel(doc, "年代");
        if (!year.isEmpty()) vod.put("vod_year", year);

        // 提取状态
        String state = extractByLabel(doc, "状态");
        if (state.isEmpty()) state = extractByLabel(doc, "更新");
        if (!state.isEmpty()) vod.put("vod_state", state);

        // 提取语言
        String lang = extractByLabel(doc, "语言");
        if (!lang.isEmpty()) vod.put("vod_lang", lang);

        // 提取导演
        String director = extractByLabel(doc, "导演");
        if (!director.isEmpty()) vod.put("vod_director", director);

        // 提取主演
        String actor = extractByLabel(doc, "主演");
        if (actor.isEmpty()) actor = extractByLabel(doc, "演员");
        if (!actor.isEmpty()) vod.put("vod_actor", actor);

        // 提取简介
        String content = extractByLabel(doc, "简介");
        if (content.isEmpty()) content = extractByLabel(doc, "剧情");
        if (content.isEmpty()) {
            Element descEl = doc.selectFirst(".module-info-introduction-content p, .video-info-introduction p, .stui-content__detail .desc, .content .detail-content");
            if (descEl != null) {
                content = descEl.text().trim();
            }
        }
        if (!content.isEmpty()) vod.put("vod_content", content);

        // 提取更新时间
        String updateTime = extractByLabel(doc, "更新");
        if (!updateTime.isEmpty()) vod.put("vod_pubdate", updateTime);
    }

    /**
     * 解析播放列表
     */
    private void parsePlayList(Document doc, JSONObject vod, String vodId) {
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
            StringBuilder playFrom = new StringBuilder();
            StringBuilder playUrl = new StringBuilder();
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
                    playFrom.append("$$$");
                    playUrl.append("$$$");
                }
                first = false;

                playFrom.append(routeName);

                StringBuilder epList = new StringBuilder();
                for (int i = 0; i < episodes.size(); i++) {
                    if (i > 0) epList.append("#");
                    epList.append(episodes.get(i)[0]).append("$").append(episodes.get(i)[1]);
                }
                playUrl.append(epList);
            }

            vod.put("vod_play_from", playFrom.toString());
            vod.put("vod_play_url", playUrl.toString());
            return;
        }

        // 方式2: 按 MacCMS 标准播放列表容器解析
        Elements playlistContainers = doc.select(
                ".module-player-list-content, .stui-content__playlist, .playlist-content, .module-play-list"
        );

        if (!playlistContainers.isEmpty()) {
            StringBuilder playFrom = new StringBuilder();
            StringBuilder playUrl = new StringBuilder();
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
                    playFrom.append("$$$");
                    playUrl.append("$$$");
                }
                first = false;

                playFrom.append(routeName);

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
                playUrl.append(epList);
            }

            if (playFrom.length() > 0) {
                vod.put("vod_play_from", playFrom.toString());
                vod.put("vod_play_url", playUrl.toString());
            }
        }

        // 方式3: 如果以上都失败, 尝试查找所有 a[href*=/p/] 链接
        if (!vod.has("vod_play_from")) {
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
                vod.put("vod_play_from", "默认线路");
                vod.put("vod_play_url", epList.toString());
            }
        }
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

        // 匹配 JSON 格式更复杂的版本 (到第一个 }; 或 };)
        pattern = Pattern.compile(
                "var\\s+" + varName + "\\s*=\\s*(\\{.*?\\})\\s*[;<]",
                Pattern.DOTALL
        );
        matcher = pattern.matcher(html);
        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }

    /**
     * 根据 encrypt 字段解码视频URL
     * @param url 原始URL
     * @param encrypt 0=明文 1=URL编码 2=Base64编码
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
        // 查找 m3u8 URL
        Pattern m3u8Pattern = Pattern.compile(
                "https?://[^\"'\\s]+\\.m3u8[^\"'\\s]*",
                Pattern.CASE_INSENSITIVE
        );
        Matcher m = m3u8Pattern.matcher(html);
        if (m.find()) return m.group();

        // 查找 mp4 URL
        Pattern mp4Pattern = Pattern.compile(
                "https?://[^\"'\\s]+\\.mp4[^\"'\\s]*",
                Pattern.CASE_INSENSITIVE
        );
        m = mp4Pattern.matcher(html);
        if (m.find()) return m.group();

        // 查找 flv URL
        Pattern flvPattern = Pattern.compile(
                "https?://[^\"'\\s]+\\.flv[^\"'\\s]*",
                Pattern.CASE_INSENSITIVE
        );
        m = flvPattern.matcher(html);
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
     * 获取播放请求头
     */
    private String getPlayHeaders() {
        JSONObject headers = new JSONObject();
        headers.put("User-Agent", UA);
        headers.put("Referer", SITE_URL);
        return headers.toString();
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
        // 处理相对路径
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
     * 根据标签名提取信息 (如 "类型：动作片")
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
            return Integer.parseInt(str.trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * 生成错误的分页返回结果
     */
    private String errorPageResult(String pg) {
        JSONObject result = new JSONObject();
        result.put("page", parseInt(pg, 1));
        result.put("pagecount", 1);
        result.put("limit", 12);
        result.put("total", 0);
        result.put("list", new JSONArray());
        return result.toString();
    }
}
