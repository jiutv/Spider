package com.github.catvod.spider;

import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SaoHuo - TVBox爬虫 (针对 shdy5.us 骚火电影)
 * 站点: 自建PHP CMS (模板: /template/saohuo/)
 *
 * 使用方法:
 * 1. 将此文件放入 CatVodTVSpider 项目的 com.github.catvod.spider 包下
 * 2. 编译生成 custom_spider.jar
 * 3. TVBox配置中 api 填 csp_SaoHuo
 */
public class SaoHuo extends Spider {

    private static final String SITE_URL = "https://shdy5.us";
    private static final String UA = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/116.0.0.0 Mobile Safari/537.36";
    private static final int TIMEOUT = 15000;

    // ==================== 首页内容 ====================
    @Override
    public String homeContent(boolean filter) {
        try {
            JSONObject result = new JSONObject();
            JSONArray classes = new JSONArray();

            // 主分类
            classes.put(new JSONObject().put("type_id", "1").put("type_name", "电影"));
            classes.put(new JSONObject().put("type_id", "2").put("type_name", "电视剧"));
            classes.put(new JSONObject().put("type_id", "4").put("type_name", "动漫"));
            classes.put(new JSONObject().put("type_id", "3").put("type_name", "综艺"));
            result.put("class", classes);

            if (filter) {
                JSONObject filters = new JSONObject();
                // 电影分类筛选
                JSONArray movieFilters = new JSONArray();

                JSONObject movieTypeFilter = new JSONObject();
                movieTypeFilter.put("key", "class");
                movieTypeFilter.put("name", "类型");
                JSONArray movieTypes = new JSONArray();
                movieTypes.put(new JSONObject().put("n", "全部").put("v", ""));
                String[] types = {"喜剧", "爱情", "恐怖", "动作", "科幻", "战争", "犯罪", "动画", "奇幻", "剧情", "冒险", "悬疑", "惊悚", "其它"};
                for (String t : types) movieTypes.put(new JSONObject().put("n", t).put("v", t));
                movieTypeFilter.put("value", movieTypes);
                movieFilters.put(movieTypeFilter);

                filters.put("1", movieFilters);

                // 电视剧分类筛选
                JSONArray tvFilters = new JSONArray();
                JSONObject tvTypeFilter = new JSONObject();
                tvTypeFilter.put("key", "class");
                tvTypeFilter.put("name", "类型");
                JSONArray tvTypes = new JSONArray();
                tvTypes.put(new JSONObject().put("n", "全部").put("v", ""));
                String[] tvTypesArr = {"国产剧", "港台剧", "日韩剧", "欧美剧", "海外剧"};
                for (String t : tvTypesArr) tvTypes.put(new JSONObject().put("n", t).put("v", t));
                tvTypeFilter.put("value", tvTypes);
                tvFilters.put(tvTypeFilter);

                filters.put("2", tvFilters);

                // 综艺分类筛选
                JSONArray showFilters = new JSONArray();
                JSONObject showTypeFilter = new JSONObject();
                showTypeFilter.put("key", "class");
                showTypeFilter.put("name", "类型");
                JSONArray showTypes = new JSONArray();
                showTypes.put(new JSONObject().put("n", "全部").put("v", ""));
                String[] showTypesArr = {"脱口秀", "真人秀", "选秀", "美食", "旅游", "汽车", "访谈", "纪实", "搞笑", "其他综艺"};
                for (String t : showTypesArr) showTypes.put(new JSONObject().put("n", t).put("v", t));
                showTypeFilter.put("value", showTypes);
                showFilters.put(showTypeFilter);

                filters.put("3", showFilters);
                filters.put("4", new JSONArray()); // 动漫无筛选
                result.put("filters", filters);
            }

            // 抓取首页推荐
            String html = fetch(SITE_URL);
            JSONArray list = parseVideoList(html);
            result.put("list", list);
            return result.toString();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    // ==================== 分类内容 ====================
    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        try {
            int page = pg.isEmpty() ? 1 : Integer.parseInt(pg);

            // 综艺子分类映射
            String actualTid = tid;
            if (extend != null && extend.containsKey("class") && !extend.get("class").isEmpty()) {
                String cls = extend.get("class");
                // 综艺子分类ID映射
                Map<String, String> showMap = new HashMap<>();
                showMap.put("脱口秀", "28");
                showMap.put("真人秀", "29");
                showMap.put("选秀", "30");
                showMap.put("美食", "31");
                showMap.put("旅游", "32");
                showMap.put("汽车", "33");
                showMap.put("访谈", "34");
                showMap.put("纪实", "35");
                showMap.put("搞笑", "36");
                showMap.put("其他综艺", "37");

                // 电影子分类ID映射
                Map<String, String> movieMap = new HashMap<>();
                movieMap.put("喜剧", "6");
                movieMap.put("爱情", "7");
                movieMap.put("恐怖", "8");
                movieMap.put("动作", "9");
                movieMap.put("科幻", "10");
                movieMap.put("战争", "11");
                movieMap.put("犯罪", "12");
                movieMap.put("动画", "13");
                movieMap.put("奇幻", "14");
                movieMap.put("剧情", "15");
                movieMap.put("冒险", "16");
                movieMap.put("悬疑", "17");
                movieMap.put("惊悚", "18");
                movieMap.put("其它", "19");

                if (showMap.containsKey(cls)) {
                    actualTid = showMap.get(cls);
                } else if (movieMap.containsKey(cls)) {
                    actualTid = movieMap.get(cls);
                }
            }

            // URL: /list/{id}-{page}.html
            String url = SITE_URL + "/list/" + actualTid + "-" + page + ".html";
            String html = fetch(url);
            JSONArray list = parseVideoList(html);

            // 解析分页
            int pageCount = parsePageCount(html, page);

            JSONObject result = new JSONObject();
            result.put("page", page);
            result.put("pagecount", pageCount);
            result.put("limit", 24);
            result.put("total", pageCount * 24);
            result.put("list", list);
            return result.toString();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    // ==================== 搜索内容 ====================
    @Override
    public String searchContent(String key, boolean quick) {
        try {
            // 搜索URL: /search.php?searchword=xxx
            String url = SITE_URL + "/search.php?searchword=" + URLEncoder.encode(key, "UTF-8");
            String html = fetch(url);
            JSONArray list = parseVideoList(html);

            JSONObject result = new JSONObject();
            result.put("list", list);
            return result.toString();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) {
        try {
            int page = pg.isEmpty() ? 1 : Integer.parseInt(pg);
            // 搜索URL: /search.php?searchword=xxx
            // 该站搜索结果暂无分页，只返回第一页
            if (page > 1) {
                JSONObject result = new JSONObject();
                result.put("list", new JSONArray());
                return result.toString();
            }
            String url = SITE_URL + "/search.php?searchword=" + URLEncoder.encode(key, "UTF-8");
            String html = fetch(url);
            JSONArray list = parseVideoList(html);

            JSONObject result = new JSONObject();
            result.put("list", list);
            return result.toString();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    // ==================== 详情内容 ====================
    @Override
    public String detailContent(List<String> ids) {
        try {
            String id = ids.get(0);
            String url = id.startsWith("http") ? id : SITE_URL + id;
            String html = fetch(url);

            Document doc = Jsoup.parse(html);
            doc.setBaseUri(SITE_URL);

            JSONObject vod = new JSONObject();
            vod.put("vod_id", id);

            // 标题
            Element titleEl = doc.selectFirst("h1");
            if (titleEl != null) {
                vod.put("vod_name", titleEl.text().trim());
            }

            // 封面图
            Element imgEl = doc.selectFirst(".detail_pic img, .v_img img, .movie-pic img");
            if (imgEl != null) {
                String pic = imgEl.attr("data-original");
                if (pic.isEmpty()) pic = imgEl.attr("src");
                vod.put("vod_pic", fixUrl(pic));
            }

            // 状态/备注
            String fullText = doc.body() != null ? doc.body().text() : "";

            // 导演
            String director = extractBetween(html, "导演：", "主演");
            if (director.isEmpty()) director = extractBetween(html, "导演:", "主演");
            if (director.isEmpty()) director = extractBetween(html, "导演：", "</p>");
            vod.put("vod_director", cleanText(director));

            // 主演
            String actor = extractBetween(html, "主演：", "</p>");
            if (actor.isEmpty()) actor = extractBetween(html, "主演:", "</p>");
            vod.put("vod_actor", cleanText(actor));

            // 简介
            String content = extractBetween(html, "class=\"p_txt show_part\">", "<br");
            if (content.isEmpty()) content = extractBetween(html, "class=\"p_txt\">", "<");
            content = cleanText(content.replaceAll("<[^>]+>", ""));
            vod.put("vod_content", content);

            // 年份/地区/类型 从状态行提取
            // 格式: </h1><p>状态信息 导演：xxx 主演：xxx</p>
            String statusLine = extractBetween(html, "</h1><p>", "导演");
            if (!statusLine.isEmpty()) {
                // 尝试提取年份
                Pattern yearP = Pattern.compile("(\\d{4})");
                Matcher yearM = yearP.matcher(statusLine);
                if (yearM.find()) vod.put("vod_year", yearM.group(1));
                vod.put("vod_remarks", cleanText(statusLine));
            }

            // 播放列表 - 从 id="play_link" 容器提取
            StringBuilder playUrl = new StringBuilder();
            Element playLink = doc.selectFirst("#play_link");
            if (playLink != null) {
                Elements links = playLink.select("a[href]");
                for (Element ep : links) {
                    String epName = ep.text().trim();
                    if (epName.isEmpty()) epName = "播放";
                    String epUrl = ep.attr("href");
                    if (playUrl.length() > 0) playUrl.append("#");
                    playUrl.append(epName).append("$").append(epUrl);
                }
            }

            // 备用: 从整个页面找播放链接
            if (playUrl.length() == 0) {
                Elements allLinks = doc.select("a[href*=/play/]");
                for (Element ep : allLinks) {
                    String epName = ep.text().trim();
                    if (epName.isEmpty()) continue;
                    String epUrl = ep.attr("href");
                    if (playUrl.length() > 0) playUrl.append("#");
                    playUrl.append(epName).append("$").append(epUrl);
                }
            }

            // 再备用: 从 v_play 等常见容器找
            if (playUrl.length() == 0) {
                Elements containers = doc.select(".playlist, .play_list, .play-list, .stui-content__playlist, #playlist, .playlist_box");
                for (Element container : containers) {
                    Elements links = container.select("a[href]");
                    for (Element ep : links) {
                        String epName = ep.text().trim();
                        if (epName.isEmpty()) continue;
                        String epUrl = ep.attr("href");
                        if (playUrl.length() > 0) playUrl.append("#");
                        playUrl.append(epName).append("$").append(epUrl);
                    }
                    if (playUrl.length() > 0) break;
                }
            }

            vod.put("vod_play_from", "骚火电影");
            vod.put("vod_play_url", playUrl.toString());

            JSONArray list = new JSONArray();
            list.put(vod);
            JSONObject result = new JSONObject();
            result.put("list", list);
            return result.toString();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    // ==================== 播放内容 ====================
    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        try {
            JSONObject result = new JSONObject();
            String url = id.startsWith("http") ? id : SITE_URL + id;
            String html = fetch(url);

            String playUrl = null;

            // 方法1: 提取 player_aaaa 变量中的 url
            Pattern playerVar = Pattern.compile("player_aaaa\\s*=\\s*(\\{[^}]+\\})");
            Matcher playerMatch = playerVar.matcher(html);
            if (playerMatch.find()) {
                try {
                    JSONObject playerData = new JSONObject(playerMatch.group(1));
                    playUrl = playerData.optString("url", "");
                } catch (Exception e) {
                    // JSON解析失败，尝试正则提取url字段
                    String urlStr = extractBetween(playerMatch.group(1), "\"url\":\"", "\"");
                    if (!urlStr.isEmpty()) playUrl = urlStr.replace("\\/", "/");
                }
            }

            // 方法2: 查找 m3u8 直链
            if (playUrl == null || playUrl.isEmpty()) {
                Pattern m3u8P = Pattern.compile("(https?://[^\"'\\s<>]+\\.m3u8[^\"'\\s<>]*)");
                Matcher m3u8M = m3u8P.matcher(html);
                if (m3u8M.find()) {
                    playUrl = m3u8M.group(1);
                }
            }

            // 方法3: 查找 mp4 直链
            if (playUrl == null || playUrl.isEmpty()) {
                Pattern mp4P = Pattern.compile("(https?://[^\"'\\s<>]+\\.mp4[^\"'\\s<>]*)");
                Matcher mp4M = mp4P.matcher(html);
                if (mp4M.find()) {
                    playUrl = mp4M.group(1);
                }
            }

            // 方法4: 从 iframe src 提取播放器URL
            if (playUrl == null || playUrl.isEmpty()) {
                Document doc = Jsoup.parse(html);
                Elements iframes = doc.select("iframe[src]");
                for (Element iframe : iframes) {
                    String src = iframe.attr("src");
                    if (src.contains("player") || src.contains("play") || src.contains("m3u8") || src.contains("video")) {
                        // 如果iframe src本身就是视频直链
                        if (src.contains(".m3u8") || src.contains(".mp4")) {
                            playUrl = src;
                            break;
                        }
                        // 尝试从iframe src中提取url参数
                        if (src.contains("url=")) {
                            String encoded = extractBetween(src, "url=", "&");
                            if (encoded.isEmpty()) encoded = src.substring(src.indexOf("url=") + 4);
                            try {
                                playUrl = java.net.URLDecoder.decode(encoded, "UTF-8");
                            } catch (Exception e) {
                                playUrl = encoded;
                            }
                            break;
                        }
                    }
                }
            }

            // 方法5: 从 JavaScript 变量提取
            if (playUrl == null || playUrl.isEmpty()) {
                Pattern jsVarP = Pattern.compile("(?:var\\s+\\w+|const\\s+\\w+|let\\s+\\w+)\\s*=\\s*[\"'](https?://[^\"']+\\.(?:m3u8|mp4|flv)[^\"']*)[\"']");
                Matcher jsVarM = jsVarP.matcher(html);
                if (jsVarM.find()) {
                    playUrl = jsVarM.group(1);
                }
            }

            // 方法6: 从 data-url 或 data-src 属性提取
            if (playUrl == null || playUrl.isEmpty()) {
                Pattern dataUrlP = Pattern.compile("data-(?:url|src|video)\\s*=\\s*[\"'](https?://[^\"']+)[\"']");
                Matcher dataUrlM = dataUrlP.matcher(html);
                if (dataUrlM.find()) {
                    playUrl = dataUrlM.group(1);
                }
            }

            if (playUrl != null && !playUrl.isEmpty()) {
                result.put("parse", 0);
                result.put("playUrl", "");
                result.put("url", playUrl);
                JSONObject headers = new JSONObject();
                headers.put("User-Agent", UA);
                headers.put("Referer", SITE_URL);
                result.put("header", headers.toString());
            } else {
                // 无法提取直链，使用网页解析
                result.put("parse", 1);
                result.put("playUrl", "");
                result.put("url", url);
                JSONObject headers = new JSONObject();
                headers.put("User-Agent", UA);
                headers.put("Referer", SITE_URL);
                result.put("header", headers.toString());
            }
            return result.toString();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 解析视频列表
     * 网站使用 v_img/v_note/v_title 结构
     */
    private JSONArray parseVideoList(String html) {
        JSONArray list = new JSONArray();
        try {
            Document doc = Jsoup.parse(html);
            doc.setBaseUri(SITE_URL);

            // 主选择器: ul.v_list > li 或直接找包含 v_img 的元素
            Map<String, JSONObject> seen = new HashMap<>();

            // 方式1: 标准列表选择
            Elements items = doc.select("ul.v_list li, .v_list li, li:has(.v_img)");
            if (items.isEmpty()) {
                // 方式2: 找所有包含 /movie/ 链接的卡片
                items = doc.select("div.v_img");
                if (items.isEmpty()) {
                    // 方式3: 通用选择
                    items = doc.select("a[href*=/movie/]");
                }
            }

            for (Element item : items) {
                Element linkEl = item.selectFirst("a[href*=/movie/]");
                if (linkEl == null) {
                    if (item.tagName().equals("a") && item.attr("href").contains("/movie/")) {
                        linkEl = item;
                    } else {
                        continue;
                    }
                }

                String href = linkEl.attr("href");
                if (href.isEmpty() || seen.containsKey(href)) continue;

                JSONObject vod = new JSONObject();
                vod.put("vod_id", href);

                // 标题
                String title = linkEl.attr("title");
                if (title.isEmpty()) {
                    Element titleEl = item.selectFirst(".v_title a, .v_title, p.v_title");
                    if (titleEl != null) {
                        title = titleEl.text();
                    } else {
                        title = linkEl.text();
                    }
                }
                if (title.isEmpty()) {
                    Element img = item.selectFirst("img[alt]");
                    if (img != null) title = img.attr("alt");
                }
                vod.put("vod_name", title.trim());

                // 封面图 (懒加载用 data-original)
                Element imgEl = item.selectFirst("img[data-original], img[src]");
                if (imgEl != null) {
                    String pic = imgEl.attr("data-original");
                    if (pic.isEmpty()) pic = imgEl.attr("src");
                    vod.put("vod_pic", fixUrl(pic));
                }

                // 备注
                Element noteEl = item.selectFirst(".v_note");
                if (noteEl != null) {
                    vod.put("vod_remarks", noteEl.text().trim());
                }

                seen.put(href, vod);
                list.put(vod);
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return list;
    }

    /**
     * 解析分页总页数
     */
    private int parsePageCount(String html, int currentPage) {
        try {
            // 查找分页信息，格式如 "1/10" 或 "/page/N"
            Pattern pageP = Pattern.compile("(\\d+)\\s*/\\s*(\\d+)");
            Matcher pageM = pageP.matcher(html);
            if (pageM.find()) {
                int total = Integer.parseInt(pageM.group(2));
                if (total > 0) return total;
            }

            // 查找最大的页码链接
            Document doc = Jsoup.parse(html);
            Elements pageLinks = doc.select("a[href*=-]");
            int maxPage = currentPage;
            for (Element link : pageLinks) {
                String href = link.attr("href");
                Pattern p = Pattern.compile("-(\\d+)\\.html");
                Matcher m = p.matcher(href);
                if (m.find()) {
                    int pNum = Integer.parseInt(m.group(1));
                    if (pNum > maxPage) maxPage = pNum;
                }
            }
            return maxPage;
        } catch (Exception e) {
            return currentPage + 1;
        }
    }

    /**
     * 从文本中提取两个标记之间的内容
     */
    private String extractBetween(String text, String start, String end) {
        try {
            int s = text.indexOf(start);
            if (s < 0) return "";
            s += start.length();
            int e = text.indexOf(end, s);
            if (e < 0) e = text.length();
            return text.substring(s, e).trim();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 清理文本中的HTML标签和多余空白
     */
    private String cleanText(String text) {
        if (text == null) return "";
        return text.replaceAll("<[^>]+>", "")
                   .replaceAll("&nbsp;", " ")
                   .replaceAll("\\s+", " ")
                   .trim();
    }

    /**
     * HTTP请求
     */
    private String fetch(String url) {
        try {
            return Jsoup.connect(url)
                    .userAgent(UA)
                    .timeout(TIMEOUT)
                    .header("Referer", SITE_URL)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "zh-CN,zh;q=0.9")
                    .header("Accept-Encoding", "gzip, deflate")
                    .ignoreContentType(true)
                    .maxBodySize(0)
                    .execute()
                    .body();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    /**
     * 修复URL (补全协议和域名)
     */
    private String fixUrl(String url) {
        if (url == null || url.isEmpty()) return "";
        if (url.startsWith("http")) return url;
        if (url.startsWith("//")) return "https:" + url;
        if (url.startsWith("/")) return SITE_URL + url;
        return SITE_URL + "/" + url;
    }

    @Override
    public boolean isVideoFormat(String url) {
        return url.contains(".m3u8") || url.contains(".mp4") || url.contains(".flv");
    }

    @Override
    public boolean manualVideoCheck() {
        return false;
    }
}
