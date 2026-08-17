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
 *
 * 网站结构:
 *   首页:     https://shdy5.us
 *   分类列表: /list/{id}-{page}.html
 *   详情页:   /movie/{id}.html
 *   播放页:   /play/{id}-{line}-{episode}.html
 *   搜索:     /s----------.html?wd={keyword}
 *
 * 播放流程:
 *   1. 播放页含 iframe: https://hhjx.hhplayer.com/?url={encrypted_id}
 *   2. 播放器页面含 __HHJX_BOOTSTRAP__ = {url, t, key, ...}
 *   3. POST https://hhjx.hhplayer.com/api/parse {url, t, key} -> 返回m3u8直链
 *
 * 使用方法:
 *   api 填 csp_SaoHuo
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
            classes.put(new JSONObject().put("type_id", "1").put("type_name", "电影"));
            classes.put(new JSONObject().put("type_id", "2").put("type_name", "电视剧"));
            classes.put(new JSONObject().put("type_id", "4").put("type_name", "动漫"));
            classes.put(new JSONObject().put("type_id", "3").put("type_name", "综艺"));
            result.put("class", classes);

            if (filter) {
                JSONObject filters = new JSONObject();
                filters.put("1", buildFilter("喜剧", "爱情", "恐怖", "动作", "科幻", "战争", "犯罪", "动画", "奇幻", "剧情", "冒险", "悬疑", "惊悚", "其它"));
                filters.put("2", buildFilter("国产剧", "港台剧", "日韩剧", "欧美剧", "海外剧"));
                filters.put("3", buildFilter("脱口秀", "真人秀", "选秀", "美食", "旅游", "汽车", "访谈", "纪实", "搞笑", "其他综艺"));
                filters.put("4", new JSONArray());
                result.put("filters", filters);
            }

            String html = fetchWithRetry(SITE_URL, 3);
            JSONArray list = parseVideoList(html);
            result.put("list", list);
            return result.toString();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    private JSONArray buildFilter(String... types) {
        JSONArray filter = new JSONArray();
        try {
            JSONObject typeFilter = new JSONObject();
            typeFilter.put("key", "class");
            typeFilter.put("name", "类型");
            JSONArray values = new JSONArray();
            values.put(new JSONObject().put("n", "全部").put("v", ""));
            for (String t : types) values.put(new JSONObject().put("n", t).put("v", t));
            typeFilter.put("value", values);
            filter.put(typeFilter);
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return filter;
    }

    // ==================== 分类内容 ====================
    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        try {
            int page = pg.isEmpty() ? 1 : Integer.parseInt(pg);
            String actualTid = tid;

            // 子分类ID映射
            if (extend != null && extend.containsKey("class") && !extend.get("class").isEmpty()) {
                Map<String, String> subMap = new HashMap<>();
                // 电影子分类
                subMap.put("喜剧", "6"); subMap.put("爱情", "7"); subMap.put("恐怖", "8");
                subMap.put("动作", "9"); subMap.put("科幻", "10"); subMap.put("战争", "11");
                subMap.put("犯罪", "12"); subMap.put("动画", "13"); subMap.put("奇幻", "14");
                subMap.put("剧情", "15"); subMap.put("冒险", "16"); subMap.put("悬疑", "17");
                subMap.put("惊悚", "18"); subMap.put("其它", "19");
                // 综艺子分类
                subMap.put("脱口秀", "28"); subMap.put("真人秀", "29"); subMap.put("选秀", "30");
                subMap.put("美食", "31"); subMap.put("旅游", "32"); subMap.put("汽车", "33");
                subMap.put("访谈", "34"); subMap.put("纪实", "35"); subMap.put("搞笑", "36");
                subMap.put("其他综艺", "37");
                // 电视剧子分类
                subMap.put("国产剧", "20"); subMap.put("港台剧", "21"); subMap.put("日韩剧", "22");
                subMap.put("欧美剧", "23"); subMap.put("海外剧", "24");
                String cls = extend.get("class");
                if (subMap.containsKey(cls)) actualTid = subMap.get(cls);
            }

            String url = SITE_URL + "/list/" + actualTid + "-" + page + ".html";
            String html = fetchWithRetry(url, 3);
            JSONArray list = parseVideoList(html);
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
            JSONArray list = new JSONArray();
            // 搜索URL: /s----------.html?wd=xxx (首页表单action)
            String url = SITE_URL + "/s----------.html?wd=" + URLEncoder.encode(key, "UTF-8");
            String html = fetchWithRetry(url, 3);
            if (!html.isEmpty()) list = parseVideoList(html);

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
            if (page > 1) {
                JSONObject result = new JSONObject();
                result.put("list", new JSONArray());
                return result.toString();
            }
            return searchContent(key, quick);
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
            String html = fetchWithRetry(url, 3);

            Document doc = Jsoup.parse(html);
            doc.setBaseUri(SITE_URL);

            JSONObject vod = new JSONObject();
            vod.put("vod_id", id);

            // 标题: <h1 class="v_title"><a href="/movie/50292.html">九门</a></h1>
            Element titleEl = doc.selectFirst("h1.v_title a");
            if (titleEl == null) titleEl = doc.selectFirst("h1 a");
            if (titleEl == null) titleEl = doc.selectFirst("h1");
            if (titleEl != null) vod.put("vod_name", titleEl.text().trim());

            // 封面图: <div class="m_background" style="background-image:url(...)">
            Element bgEl = doc.selectFirst(".m_background");
            if (bgEl != null) {
                String style = bgEl.attr("style");
                Matcher m = Pattern.compile("url\\(([^)]+)\\)").matcher(style);
                if (m.find()) vod.put("vod_pic", m.group(1).replace("'", "").replace("\"", ""));
            }

            // 信息行: <p>大陆 / 2026 / 奇幻,冒险 / 导演:柏杉 / 主演:陈伟霆,陈瑶,...<a>剧情介绍</a></p>
            Element infoEl = doc.selectFirst(".v_info_box p");
            if (infoEl != null) {
                String infoText = infoEl.text();
                // 去掉末尾的"剧情介绍"
                infoText = infoText.replace("剧情介绍", "").trim();

                // 按斜杠分割: 大陆 / 2026 / 奇幻,冒险 / 导演:柏杉 / 主演:...
                String[] parts = infoText.split("/");
                if (parts.length >= 1) vod.put("vod_area", parts[0].trim());
                if (parts.length >= 2) {
                    String year = parts[1].trim();
                    Matcher yearM = Pattern.compile("(\\d{4})").matcher(year);
                    if (yearM.find()) vod.put("vod_year", yearM.group(1));
                }
                if (parts.length >= 3) vod.put("vod_class", parts[2].trim());

                for (String part : parts) {
                    part = part.trim();
                    if (part.startsWith("导演:")) vod.put("vod_director", part.substring(3).trim());
                    if (part.startsWith("主演:")) vod.put("vod_actor", part.substring(3).trim());
                }
            }

            // 简介: <p class="p_txt show_part">剧情...<br/><br/><a>...</a></p>
            Element contentEl = doc.selectFirst("p.p_txt");
            if (contentEl != null) {
                String content = contentEl.text();
                // 去掉末尾的广告文本
                int cutIdx = content.indexOf("手机在线观看");
                if (cutIdx > 0) content = content.substring(0, cutIdx);
                vod.put("vod_content", content.trim());
            }

            // 播放列表: <ul class="play_list" id="play_link">
            //   <li class="current"><a href="/play/50292-1-30.html">30</a>...<a href="/play/50292-1-1.html">1</a></li>
            //   <li><a href="/play/50292-2-27.html">27</a>...<a href="/play/50292-2-1.html">1</a></li>
            Element playListEl = doc.selectFirst("#play_link");
            StringBuilder playFrom = new StringBuilder();
            StringBuilder playUrl = new StringBuilder();

            if (playListEl != null) {
                Elements lines = playListEl.select("li");
                for (int i = 0; i < lines.size(); i++) {
                    Element line = lines.get(i);
                    String lineName = "线路" + (i + 1);
                    if (playFrom.length() > 0) playFrom.append("$$$");
                    playFrom.append(lineName);

                    StringBuilder epUrls = new StringBuilder();
                    Elements links = line.select("a[href]");
                    for (Element ep : links) {
                        String epName = ep.text().trim();
                        String epUrl = ep.attr("href");
                        if (epName.isEmpty() || epUrl.isEmpty()) continue;
                        if (epUrls.length() > 0) epUrls.append("#");
                        epUrls.append(epName).append("$").append(epUrl);
                    }

                    if (playUrl.length() > 0) playUrl.append("$$$");
                    playUrl.append(epUrls.toString());
                }
            }

            vod.put("vod_play_from", playFrom.toString());
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

    // ==================== 播放内容（核心修复）====================
    // 播放流程:
    //   1. 播放页 /play/{id}-{line}-{ep}.html 含 iframe src="https://hhjx.hhplayer.com/?url={encrypted_id}"
    //   2. 播放器页面 __HHJX_BOOTSTRAP__ = {url, t, key, ts_key}
    //   3. POST https://hhjx.hhplayer.com/api/parse {url, t, key} -> {code:200, url:"m3u8直链"}
    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        JSONObject result = new JSONObject();
        String playUrl = id.startsWith("http") ? id : SITE_URL + id;

        try {
            // Step 1: 获取播放页HTML
            String html = fetchWithRetry(playUrl, 3);
            if (html.isEmpty()) {
                SpiderDebug.log("SaoHuo: 播放页获取失败，降级到嗅探");
                return fallbackSniff(result, playUrl);
            }

            // Step 2: 从iframe提取播放器URL（多种方式尝试）
            String playerIframeUrl = null;
            Document doc = Jsoup.parse(html);
            Element iframe = doc.selectFirst("iframe[src]");
            if (iframe != null) {
                playerIframeUrl = iframe.attr("src");
            }

            // 备用: 正则提取iframe src
            if (playerIframeUrl == null || playerIframeUrl.isEmpty()) {
                Matcher iframeM = Pattern.compile("iframe[^>]+src=[\"']([^\"']+)[\"']").matcher(html);
                if (iframeM.find()) playerIframeUrl = iframeM.group(1);
            }

            // 备用2: 查找任何包含 hhplayer 的URL
            if (playerIframeUrl == null || playerIframeUrl.isEmpty()) {
                Matcher iframeM2 = Pattern.compile("(https?://[^\"'\s>]*hhplayer[^\"'\s>]*)").matcher(html);
                if (iframeM2.find()) playerIframeUrl = iframeM2.group(1);
            }

            if (playerIframeUrl == null || playerIframeUrl.isEmpty()) {
                SpiderDebug.log("SaoHuo: 未找到播放器iframe，降级到嗅探");
                return fallbackSniff(result, playUrl);
            }

            // 补全协议
            if (playerIframeUrl.startsWith("//")) {
                playerIframeUrl = "https:" + playerIframeUrl;
            }

            // Step 3: 从播放器URL提取encrypted url参数
            String encryptedUrl = null;
            if (playerIframeUrl.contains("url=")) {
                encryptedUrl = extractParam(playerIframeUrl, "url");
            }

            // Step 4: 获取播放器页面，提取 t 和 key
            String playerHtml = fetchWithRetry(playerIframeUrl, 3);
            if (playerHtml.isEmpty()) {
                SpiderDebug.log("SaoHuo: 播放器页面获取失败，降级到嗅探");
                return fallbackSniff(result, playUrl);
            }

            String t = null, key = null, tsKey = null;

            // 提取 __HHJX_BOOTSTRAP__ = {...}
            Matcher bootM = Pattern.compile("__HHJX_BOOTSTRAP__\s*=\s*(\{.*?\});", Pattern.DOTALL).matcher(playerHtml);
            if (bootM.find()) {
                try {
                    JSONObject boot = new JSONObject(bootM.group(1));
                    t = boot.optString("t", "");
                    key = boot.optString("key", "");
                    tsKey = boot.optString("ts_key", "");
                    if (encryptedUrl == null || encryptedUrl.isEmpty()) {
                        encryptedUrl = boot.optString("url", "");
                    }
                } catch (Exception e) {
                    // JSON解析失败，用正则提取
                    SpiderDebug.log("SaoHuo: bootstrap JSON解析失败，使用正则回退");
                    t = extractJsonField(bootM.group(1), "t");
                    key = extractJsonField(bootM.group(1), "key");
                    tsKey = extractJsonField(bootM.group(1), "ts_key");
                    if (encryptedUrl == null || encryptedUrl.isEmpty()) {
                        encryptedUrl = extractJsonField(bootM.group(1), "url");
                    }
                }
            }

            if (encryptedUrl == null || encryptedUrl.isEmpty() || t == null || t.isEmpty() || key == null || key.isEmpty()) {
                SpiderDebug.log("SaoHuo: 缺少必要的解析参数，降级到嗅探");
                return fallbackSniff(result, playUrl);
            }

            // Step 5: 调用 /api/parse 获取真实m3u8地址
            String playerDomain = extractDomain(playerIframeUrl);
            if (playerDomain.isEmpty()) {
                playerDomain = "https://hhjx.hhplayer.com";
            }
            String apiUrl = playerDomain + "/api/parse";

            JSONObject postData = new JSONObject();
            postData.put("url", encryptedUrl);
            try {
                postData.put("t", Long.parseLong(t));
            } catch (NumberFormatException nfe) {
                postData.put("t", t);
            }
            postData.put("key", key);
            if (tsKey != null && !tsKey.isEmpty()) {
                postData.put("ts_key", tsKey);
            }

            String apiResponse = postJson(apiUrl, postData.toString(), playerIframeUrl);
            if (apiResponse.isEmpty()) {
                SpiderDebug.log("SaoHuo: API请求无响应，降级到嗅探");
                return fallbackSniff(result, playUrl);
            }

            try {
                JSONObject resp = new JSONObject(apiResponse);
                if (resp.optInt("code") == 200) {
                    String m3u8Url = resp.optString("url", "");
                    if (!m3u8Url.isEmpty()) {
                        // 【关键修复】不再强制 http→https，保持API返回的原始协议
                        result.put("parse", 0);
                        result.put("playUrl", "");
                        result.put("url", m3u8Url);
                        JSONObject headers = new JSONObject();
                        headers.put("User-Agent", UA);
                        // 【修复】Referer使用播放器域名，Origin动态跟随
                        headers.put("Referer", playerDomain + "/");
                        headers.put("Origin", playerDomain);
                        headers.put("Accept", "*/*");
                        headers.put("Accept-Language", "zh-CN,zh;q=0.9");
                        result.put("header", headers.toString());
                        return result.toString();
                    }
                } else {
                    SpiderDebug.log("SaoHuo: API返回错误 code=" + resp.optInt("code") + " msg=" + resp.optString("msg", ""));
                }
            } catch (Exception e) {
                SpiderDebug.log("SaoHuo: API响应解析失败: " + apiResponse);
            }

            // 所有解析路径失败，降级到嗅探
            return fallbackSniff(result, playUrl);

        } catch (Exception e) {
            SpiderDebug.log(e);
            return fallbackSniff(result, playUrl);
        }
    }

    /**
     * 嗅探模式降级（parse=1）
     */
    private String fallbackSniff(JSONObject result, String playUrl) {
        try {
            result.put("parse", 1);
            result.put("playUrl", "");
            result.put("url", playUrl);
            JSONObject headers = new JSONObject();
            headers.put("User-Agent", UA);
            headers.put("Referer", SITE_URL);
            result.put("header", headers.toString());
            return result.toString();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }
    // ==================== 工具方法 ====================

    /**
     * 解析视频列表
     * HTML结构: <ul class="v_list"><li><div class="v_img"><a href="/movie/50298.html" title="花开锦绣"><img data-original="..." /></a><div class="v_note">更新至18集</div></div><p class="v_title"><a href="/movie/50298.html" title="花开锦绣">花开锦绣</a></p></li></ul>
     */
    private JSONArray parseVideoList(String html) {
        JSONArray list = new JSONArray();
        try {
            Document doc = Jsoup.parse(html);
            doc.setBaseUri(SITE_URL);

            Elements items = doc.select("ul.v_list li");
            for (Element item : items) {
                Element linkEl = item.selectFirst(".v_img a[href*=/movie/]");
                if (linkEl == null) linkEl = item.selectFirst("a[href*=/movie/]");
                if (linkEl == null) continue;

                String href = linkEl.attr("href");
                if (href.isEmpty()) continue;

                JSONObject vod = new JSONObject();
                vod.put("vod_id", href);

                // 标题
                String title = linkEl.attr("title");
                if (title.isEmpty()) {
                    Element titleEl = item.selectFirst(".v_title a");
                    if (titleEl != null) title = titleEl.text();
                }
                vod.put("vod_name", title.trim());

                // 封面图 (懒加载: data-original)
                Element imgEl = item.selectFirst("img[data-original]");
                if (imgEl == null) imgEl = item.selectFirst("img[src]");
                if (imgEl != null) {
                    String pic = imgEl.attr("data-original");
                    if (pic.isEmpty()) pic = imgEl.attr("src");
                    vod.put("vod_pic", fixUrl(pic));
                }

                // 备注
                Element noteEl = item.selectFirst(".v_note");
                if (noteEl != null) vod.put("vod_remarks", noteEl.text().trim());

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
            // 查找最大页码
            Document doc = Jsoup.parse(html);
            Elements pageLinks = doc.select("a[href*=list/]");
            int maxPage = currentPage;
            for (Element link : pageLinks) {
                String href = link.attr("href");
                Matcher m = Pattern.compile("list/\\d+-(\\d+)\\.html").matcher(href);
                if (m.find()) {
                    int p = Integer.parseInt(m.group(1));
                    if (p > maxPage) maxPage = p;
                }
            }
            // 查看是否有"下一页"
            Elements nextLinks = doc.select("a:contains(下一页), a:contains(尾页)");
            if (!nextLinks.isEmpty() && maxPage <= currentPage) maxPage = currentPage + 1;
            return maxPage;
        } catch (Exception e) {
            return currentPage + 1;
        }
    }

    /**
     * 从URL中提取参数值
     */
    private String extractParam(String url, String param) {
        try {
            int idx = url.indexOf(param + "=");
            if (idx < 0) return null;
            int start = idx + param.length() + 1;
            int end = url.indexOf("&", start);
            if (end < 0) end = url.length();
            return url.substring(start, end);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从JSON字符串中提取字段值 (正则方式，避免JSON解析失败)
     */
    private String extractJsonField(String json, String field) {
        try {
            Pattern p = Pattern.compile("[\"']" + field + "[\"']\\s*:\\s*[\"']([^\"']+)[\"']");
            Matcher m = p.matcher(json);
            if (m.find()) return m.group(1);
            // 尝试数字类型
            p = Pattern.compile("[\"']" + field + "[\"']\\s*:\\s*(\\d+)");
            m = p.matcher(json);
            if (m.find()) return m.group(1);
            return "";
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 从URL提取域名 (含协议)
     */
    private String extractDomain(String url) {
        try {
            Matcher m = Pattern.compile("(https?://[^/]+)").matcher(url);
            if (m.find()) return m.group(1);
            return "";
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 修复URL
     */
    private String fixUrl(String url) {
        if (url == null || url.isEmpty()) return "";
        if (url.startsWith("http")) return url;
        if (url.startsWith("//")) return "https:" + url;
        if (url.startsWith("/")) return SITE_URL + url;
        return SITE_URL + "/" + url;
    }

    /**
     * HTTP GET请求
     */
    /**
     * HTTP GET请求（增强版）
     */
    private String fetch(String url) {
        try {
            return Jsoup.connect(url)
                    .userAgent(UA)
                    .timeout(TIMEOUT)
                    .header("Referer", SITE_URL)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "zh-CN,zh;q=0.9")
                    .ignoreContentType(true)
                    .maxBodySize(0)
                    .followRedirects(true)
                    .execute()
                    .body();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    /**
     * 带重试的HTTP GET
     */
    /**
     * 带重试的HTTP GET（增强版）
     */
    private String fetchWithRetry(String url, int maxRetries) {
        for (int i = 0; i < maxRetries; i++) {
            String html = fetch(url);
            if (!html.isEmpty()
                && !html.contains("Connection timed out")
                && !html.contains("error code: 522")
                && !html.contains("error code: 521")
                && !html.contains("Cloudflare")
                && !html.contains("Checking your browser")
                && !html.contains("Access denied")) {
                return html;
            }
            try { Thread.sleep(1500L * (i + 1)); } catch (InterruptedException ignored) {}
        }
        return "";
    }

    /**
     * HTTP POST JSON请求
     */
    /**
     * HTTP POST JSON请求（增强版）
     */
    private String postJson(String url, String jsonBody, String referer) {
        try {
            return Jsoup.connect(url)
                    .userAgent(UA)
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .header("Referer", referer)
                    .header("Origin", extractDomain(referer))
                    .header("Accept", "application/json, text/plain, */*")
                    .header("Accept-Language", "zh-CN,zh;q=0.9")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .ignoreContentType(true)
                    .maxBodySize(0)
                    .requestBody(jsonBody)
                    .method(org.jsoup.Connection.Method.POST)
                    .execute()
                    .body();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
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

