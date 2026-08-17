package com.github.catvod.spider;

import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SaoHuo - TVBox/猫影视爬虫 (骚火电影)
 *
 * 支持域名: shdy5.us (主), shdy2.com (备)
 * 播放器: hhjx.hhplayer.com
 *
 * 关键修复 (2026-08-17):
 *   1. 多域名自动切换: 主域名不可用时自动尝试备用域名
 *   2. 移除 http→https 强制转换，保持API原始协议
 *   3. 实时获取 bootstrap (t/key)，确保签名不过期
 *   4. 增强 iframe/bootstrap 提取的健壮性（多种正则回退）
 *   5. POST api/parse 双保险: Jsoup + HttpURLConnection
 *   6. 自动嗅探降级: 任一环节失败自动回退 parse=1
 *   7. 正确处理 522/Cloudflare 等错误码
 *   8. 返回header优化: Referer跟随实际播放器域名
 */
public class SaoHuo extends Spider {

    // 域名列表，按优先级排列
    private static final List<String> SITE_URLS = Arrays.asList(
        "https://shdy5.us",
        "https://shdy2.com"
    );
    private String currentSiteUrl = SITE_URLS.get(0);

    private static final String UA = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/116.0.0.0 Mobile Safari/537.36";
    private static final int TIMEOUT = 15000;
    private static final int MAX_RETRY = 3;

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

            String html = fetchWithRetry(currentSiteUrl, MAX_RETRY);
            if (html.isEmpty()) {
                // 尝试备用域名
                for (int i = 1; i < SITE_URLS.size(); i++) {
                    currentSiteUrl = SITE_URLS.get(i);
                    html = fetchWithRetry(currentSiteUrl, MAX_RETRY);
                    if (!html.isEmpty()) break;
                }
            }
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

            if (extend != null && extend.containsKey("class") && !extend.get("class").isEmpty()) {
                Map<String, String> subMap = new HashMap<>();
                subMap.put("喜剧", "6"); subMap.put("爱情", "7"); subMap.put("恐怖", "8");
                subMap.put("动作", "9"); subMap.put("科幻", "10"); subMap.put("战争", "11");
                subMap.put("犯罪", "12"); subMap.put("动画", "13"); subMap.put("奇幻", "14");
                subMap.put("剧情", "15"); subMap.put("冒险", "16"); subMap.put("悬疑", "17");
                subMap.put("惊悚", "18"); subMap.put("其它", "19");
                subMap.put("脱口秀", "28"); subMap.put("真人秀", "29"); subMap.put("选秀", "30");
                subMap.put("美食", "31"); subMap.put("旅游", "32"); subMap.put("汽车", "33");
                subMap.put("访谈", "34"); subMap.put("纪实", "35"); subMap.put("搞笑", "36");
                subMap.put("其他综艺", "37");
                subMap.put("国产剧", "20"); subMap.put("港台剧", "21"); subMap.put("日韩剧", "22");
                subMap.put("欧美剧", "23"); subMap.put("海外剧", "24");
                String cls = extend.get("class");
                if (subMap.containsKey(cls)) actualTid = subMap.get(cls);
            }

            String url = currentSiteUrl + "/list/" + actualTid + "-" + page + ".html";
            String html = fetchWithRetry(url, MAX_RETRY);
            if (html.isEmpty()) {
                for (int i = 0; i < SITE_URLS.size(); i++) {
                    if (SITE_URLS.get(i).equals(currentSiteUrl)) continue;
                    String tryUrl = SITE_URLS.get(i) + "/list/" + actualTid + "-" + page + ".html";
                    html = fetchWithRetry(tryUrl, MAX_RETRY);
                    if (!html.isEmpty()) {
                        currentSiteUrl = SITE_URLS.get(i);
                        break;
                    }
                }
            }
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
            String url = currentSiteUrl + "/s----------.html?wd=" + URLEncoder.encode(key, "UTF-8");
            String html = fetchWithRetry(url, MAX_RETRY);
            if (html.isEmpty()) {
                for (int i = 0; i < SITE_URLS.size(); i++) {
                    if (SITE_URLS.get(i).equals(currentSiteUrl)) continue;
                    String tryUrl = SITE_URLS.get(i) + "/s----------.html?wd=" + URLEncoder.encode(key, "UTF-8");
                    html = fetchWithRetry(tryUrl, MAX_RETRY);
                    if (!html.isEmpty()) {
                        currentSiteUrl = SITE_URLS.get(i);
                        break;
                    }
                }
            }
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
            String url = id.startsWith("http") ? id : currentSiteUrl + id;
            String html = fetchWithRetry(url, MAX_RETRY);
            if (html.isEmpty()) {
                for (int i = 0; i < SITE_URLS.size(); i++) {
                    if (SITE_URLS.get(i).equals(currentSiteUrl)) continue;
                    String tryUrl = id.startsWith("http") ? id : SITE_URLS.get(i) + id;
                    html = fetchWithRetry(tryUrl, MAX_RETRY);
                    if (!html.isEmpty()) {
                        currentSiteUrl = SITE_URLS.get(i);
                        break;
                    }
                }
            }

            Document doc = Jsoup.parse(html);
            doc.setBaseUri(currentSiteUrl);

            JSONObject vod = new JSONObject();
            vod.put("vod_id", id);

            Element titleEl = doc.selectFirst("h1.v_title a");
            if (titleEl == null) titleEl = doc.selectFirst("h1 a");
            if (titleEl == null) titleEl = doc.selectFirst("h1");
            if (titleEl != null) vod.put("vod_name", titleEl.text().trim());

            Element bgEl = doc.selectFirst(".m_background");
            if (bgEl != null) {
                String style = bgEl.attr("style");
                Matcher m = Pattern.compile("url\(([^)]+)\)").matcher(style);
                if (m.find()) vod.put("vod_pic", m.group(1).replace("'", "").replace("\"", ""));
            }

            Element infoEl = doc.selectFirst(".v_info_box p");
            if (infoEl != null) {
                String infoText = infoEl.text().replace("剧情介绍", "").trim();
                String[] parts = infoText.split("/");
                if (parts.length >= 1) vod.put("vod_area", parts[0].trim());
                if (parts.length >= 2) {
                    String year = parts[1].trim();
                    Matcher yearM = Pattern.compile("(\d{4})").matcher(year);
                    if (yearM.find()) vod.put("vod_year", yearM.group(1));
                }
                if (parts.length >= 3) vod.put("vod_class", parts[2].trim());
                for (String part : parts) {
                    part = part.trim();
                    if (part.startsWith("导演:")) vod.put("vod_director", part.substring(3).trim());
                    if (part.startsWith("主演:")) vod.put("vod_actor", part.substring(3).trim());
                }
            }

            Element contentEl = doc.selectFirst("p.p_txt");
            if (contentEl != null) {
                String content = contentEl.text();
                int cutIdx = content.indexOf("手机在线观看");
                if (cutIdx > 0) content = content.substring(0, cutIdx);
                vod.put("vod_content", content.trim());
            }

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
    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        JSONObject result = new JSONObject();
        String playUrl = id.startsWith("http") ? id : currentSiteUrl + id;

        try {
            // Step 1: 获取播放页HTML（带域名切换）
            String html = fetchWithRetryMultiDomain(playUrl, id);
            if (html.isEmpty()) {
                SpiderDebug.log("SaoHuo: 所有域名均无法获取播放页");
                return fallbackSniff(result, playUrl);
            }

            // Step 2: 提取播放器iframe URL（多种方式尝试）
            String playerIframeUrl = extractPlayerUrl(html);
            if (playerIframeUrl == null || playerIframeUrl.isEmpty()) {
                playerIframeUrl = extractPlayerUrlBackup(html);
            }
            if (playerIframeUrl == null || playerIframeUrl.isEmpty()) {
                SpiderDebug.log("SaoHuo: 未找到播放器iframe，降级到嗅探");
                return fallbackSniff(result, playUrl);
            }

            // 补全协议
            if (playerIframeUrl.startsWith("//")) {
                playerIframeUrl = "https:" + playerIframeUrl;
            }

            // Step 3: 获取播放器页面，提取 __HHJX_BOOTSTRAP__
            String playerHtml = fetchWithRetry(playerIframeUrl, MAX_RETRY);
            if (playerHtml.isEmpty()) {
                SpiderDebug.log("SaoHuo: 播放器页面获取失败，降级到嗅探");
                return fallbackSniff(result, playUrl);
            }

            String encryptedUrl = null;
            String t = null;
            String key = null;
            String tsKey = null;

            Matcher bootM = Pattern.compile("__HHJX_BOOTSTRAP__\s*=\s*(\{.*?\});", Pattern.DOTALL).matcher(playerHtml);
            if (bootM.find()) {
                String bootJson = bootM.group(1);
                try {
                    JSONObject boot = new JSONObject(bootJson);
                    encryptedUrl = boot.optString("url", "");
                    t = boot.optString("t", "");
                    key = boot.optString("key", "");
                    tsKey = boot.optString("ts_key", "");
                } catch (Exception jsonEx) {
                    SpiderDebug.log("SaoHuo: bootstrap JSON解析失败，使用正则回退");
                    encryptedUrl = extractJsonField(bootJson, "url");
                    t = extractJsonField(bootJson, "t");
                    key = extractJsonField(bootJson, "key");
                    tsKey = extractJsonField(bootJson, "ts_key");
                }
            }

            // 备用：从iframe的url参数提取encryptedUrl
            if (encryptedUrl == null || encryptedUrl.isEmpty()) {
                encryptedUrl = extractParam(playerIframeUrl, "url");
            }

            if (encryptedUrl == null || encryptedUrl.isEmpty() || t == null || t.isEmpty() || key == null || key.isEmpty()) {
                SpiderDebug.log("SaoHuo: 缺少必要的解析参数，降级到嗅探");
                return fallbackSniff(result, playUrl);
            }

            // Step 4: 调用 /api/parse 获取真实m3u8地址
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

            String apiResponse = postJsonRobust(apiUrl, postData.toString(), playerIframeUrl, playerDomain);
            if (apiResponse.isEmpty()) {
                SpiderDebug.log("SaoHuo: API请求无响应，降级到嗅探");
                return fallbackSniff(result, playUrl);
            }

            // Step 5: 解析API响应
            try {
                JSONObject resp = new JSONObject(apiResponse);
                int code = resp.optInt("code", -1);
                if (code == 200) {
                    String m3u8Url = resp.optString("url", "");
                    if (!m3u8Url.isEmpty()) {
                        // 【关键修复】不再强制 http→https
                        // 保持API返回的原始协议，避免证书/速度问题

                        result.put("parse", 0);
                        result.put("playUrl", "");
                        result.put("url", m3u8Url);

                        JSONObject headers = new JSONObject();
                        headers.put("User-Agent", UA);
                        // Referer使用播放器域名，确保切片能正常下载
                        headers.put("Referer", playerDomain + "/");
                        headers.put("Origin", playerDomain);
                        headers.put("Accept", "*/*");
                        headers.put("Accept-Language", "zh-CN,zh;q=0.9");
                        result.put("header", headers.toString());
                        return result.toString();
                    }
                } else {
                    SpiderDebug.log("SaoHuo: API返回错误 code=" + code + " msg=" + resp.optString("msg", ""));
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
            headers.put("Referer", currentSiteUrl);
            result.put("header", headers.toString());
            return result.toString();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    // ==================== 工具方法 ====================

    private JSONArray parseVideoList(String html) {
        JSONArray list = new JSONArray();
        try {
            Document doc = Jsoup.parse(html);
            doc.setBaseUri(currentSiteUrl);

            Elements items = doc.select("ul.v_list li");
            for (Element item : items) {
                Element linkEl = item.selectFirst(".v_img a[href*=/movie/]");
                if (linkEl == null) linkEl = item.selectFirst("a[href*=/movie/]");
                if (linkEl == null) continue;

                String href = linkEl.attr("href");
                if (href.isEmpty()) continue;

                JSONObject vod = new JSONObject();
                vod.put("vod_id", href);

                String title = linkEl.attr("title");
                if (title.isEmpty()) {
                    Element titleEl = item.selectFirst(".v_title a");
                    if (titleEl != null) title = titleEl.text();
                }
                vod.put("vod_name", title.trim());

                Element imgEl = item.selectFirst("img[data-original]");
                if (imgEl == null) imgEl = item.selectFirst("img[src]");
                if (imgEl != null) {
                    String pic = imgEl.attr("data-original");
                    if (pic.isEmpty()) pic = imgEl.attr("src");
                    vod.put("vod_pic", fixUrl(pic));
                }

                Element noteEl = item.selectFirst(".v_note");
                if (noteEl != null) vod.put("vod_remarks", noteEl.text().trim());

                list.put(vod);
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return list;
    }

    private int parsePageCount(String html, int currentPage) {
        try {
            Document doc = Jsoup.parse(html);
            Elements pageLinks = doc.select("a[href*=list/]");
            int maxPage = currentPage;
            for (Element link : pageLinks) {
                String href = link.attr("href");
                Matcher m = Pattern.compile("list/\d+-(\d+)\.html").matcher(href);
                if (m.find()) {
                    int p = Integer.parseInt(m.group(1));
                    if (p > maxPage) maxPage = p;
                }
            }
            Elements nextLinks = doc.select("a:contains(下一页), a:contains(尾页)");
            if (!nextLinks.isEmpty() && maxPage <= currentPage) maxPage = currentPage + 1;
            return maxPage;
        } catch (Exception e) {
            return currentPage + 1;
        }
    }

    private String extractPlayerUrl(String html) {
        if (html == null) return null;
        Matcher m = Pattern.compile("<iframe[^>]+src="(https?://[^"]+)"", Pattern.CASE_INSENSITIVE).matcher(html);
        if (m.find()) return m.group(1);
        m = Pattern.compile("<iframe[^>]+src='(https?://[^']+)'", Pattern.CASE_INSENSITIVE).matcher(html);
        if (m.find()) return m.group(1);
        m = Pattern.compile("<iframe[^>]+src=(https?://[^\s>]+)", Pattern.CASE_INSENSITIVE).matcher(html);
        if (m.find()) return m.group(1);
        return null;
    }

    private String extractPlayerUrlBackup(String html) {
        if (html == null) return null;
        Matcher m = Pattern.compile("(https?://[^"'\s>]*hhplayer[^"'\s>]*)").matcher(html);
        if (m.find()) return m.group(1);
        m = Pattern.compile("data-url="([^"]+)"").matcher(html);
        if (m.find()) return m.group(1);
        m = Pattern.compile("player[^=]*=\s*['"]([^'"]+)['"]").matcher(html);
        if (m.find()) return m.group(1);
        return null;
    }

    private String extractParam(String url, String param) {
        if (url == null || param == null) return null;
        try {
            int idx = url.indexOf(param + "=");
            if (idx < 0) return null;
            int start = idx + param.length() + 1;
            int end = url.indexOf("&", start);
            if (end < 0) end = url.length();
            String value = url.substring(start, end);
            return java.net.URLDecoder.decode(value, "UTF-8");
        } catch (Exception e) {
            return null;
        }
    }

    private String extractJsonField(String json, String field) {
        if (json == null || field == null) return "";
        try {
            Pattern p = Pattern.compile("[" + Pattern.quote(""") + "']" + Pattern.quote(field) + "[" + Pattern.quote(""") + "']\s*:\s*[" + Pattern.quote(""") + "']([^" + Pattern.quote(""") + "']+)[" + Pattern.quote(""") + "']");
            Matcher m = p.matcher(json);
            if (m.find()) return m.group(1);
            p = Pattern.compile("[" + Pattern.quote(""") + "']" + Pattern.quote(field) + "[" + Pattern.quote(""") + "']\s*:\s*([^,}\s]+)");
            m = p.matcher(json);
            if (m.find()) return m.group(1).trim();
            return "";
        } catch (Exception e) {
            return "";
        }
    }

    private String extractDomain(String url) {
        if (url == null) return "";
        try {
            Matcher m = Pattern.compile("(https?://[^/]+)").matcher(url);
            if (m.find()) return m.group(1);
            return "";
        } catch (Exception e) {
            return "";
        }
    }

    private String fixUrl(String url) {
        if (url == null || url.isEmpty()) return "";
        if (url.startsWith("http")) return url;
        if (url.startsWith("//")) return "https:" + url;
        if (url.startsWith("/")) return currentSiteUrl + url;
        return currentSiteUrl + "/" + url;
    }

    /**
     * 带多域名切换的重试请求
     */
    private String fetchWithRetryMultiDomain(String url, String id) {
        // 先尝试当前域名
        String html = fetchWithRetry(url, MAX_RETRY);
        if (!html.isEmpty()) return html;

        // 尝试其他域名
        for (String site : SITE_URLS) {
            if (url.startsWith(site)) continue;
            String tryUrl;
            if (id.startsWith("http")) {
                tryUrl = id; // 已经是完整URL
            } else {
                tryUrl = site + id;
            }
            html = fetchWithRetry(tryUrl, MAX_RETRY);
            if (!html.isEmpty()) {
                currentSiteUrl = site;
                return html;
            }
        }
        return "";
    }

    private String fetch(String url) {
        try {
            return Jsoup.connect(url)
                    .userAgent(UA)
                    .timeout(TIMEOUT)
                    .header("Referer", currentSiteUrl)
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

    private String fetchWithRetry(String url, int maxRetries) {
        for (int i = 0; i < maxRetries; i++) {
            String html = fetch(url);
            if (!html.isEmpty()
                && !html.contains("Connection timed out")
                && !html.contains("error code: 522")
                && !html.contains("error code: 521")
                && !html.contains("Cloudflare")
                && !html.contains("Checking your browser")
                && !html.contains("403 Forbidden")
                && !html.contains("Access denied")) {
                return html;
            }
            try { Thread.sleep(1500L * (i + 1)); } catch (InterruptedException ignored) {}
        }
        return "";
    }

    private String postJsonRobust(String url, String jsonBody, String referer, String origin) {
        String result = postJsonJsoup(url, jsonBody, referer, origin);
        if (!result.isEmpty()) return result;
        SpiderDebug.log("SaoHuo: Jsoup POST失败，尝试HttpURLConnection...");
        result = postJsonHttp(url, jsonBody, referer, origin);
        return result;
    }

    private String postJsonJsoup(String url, String jsonBody, String referer, String origin) {
        try {
            return Jsoup.connect(url)
                    .userAgent(UA)
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .header("Referer", referer)
                    .header("Origin", origin)
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
            SpiderDebug.log("SaoHuo: Jsoup POST error: " + e.getMessage());
            return "";
        }
    }

    private String postJsonHttp(String urlStr, String jsonBody, String referer, String origin) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(TIMEOUT);
            conn.setReadTimeout(TIMEOUT);
            conn.setDoOutput(true);
            conn.setRequestProperty("User-Agent", UA);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Referer", referer);
            conn.setRequestProperty("Origin", origin);
            conn.setRequestProperty("Accept", "application/json, text/plain, */*");
            conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9");
            conn.setRequestProperty("X-Requested-With", "XMLHttpRequest");

            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                java.io.InputStream is = conn.getInputStream();
                java.util.Scanner scanner = new java.util.Scanner(is, "UTF-8").useDelimiter("\A");
                return scanner.hasNext() ? scanner.next() : "";
            } else {
                SpiderDebug.log("SaoHuo: HttpURLConnection POST code=" + responseCode);
                return "";
            }
        } catch (Exception e) {
            SpiderDebug.log("SaoHuo: HttpURLConnection POST error: " + e.getMessage());
            return "";
        } finally {
            if (conn != null) conn.disconnect();
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
