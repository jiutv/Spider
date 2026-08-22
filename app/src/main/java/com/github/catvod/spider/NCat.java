package com.github.catvod.spider;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Util;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 网飞猫 / 可可影视 修复版 v2 (2026-08-22)
 * 
 * 修复：
 * 1. 超级线路/王者TV蓝光 播放失败（增加 Referer Header）
 * 2. 首页推荐影片播放失败（统一播放页解析逻辑）
 */
public class NCat extends Spider {

    private static final String siteUrl = "https://ncat.it.com";
    private static final String playSite = "https://dyrsvip.cc";

    private HashMap<String, String> getHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", Util.CHROME);
        headers.put("Referer", siteUrl + "/");
        headers.put("Accept-Language", "zh-CN,zh;q=0.9");
        return headers;
    }

    /**
     * 播放请求头 - 关键！CDN 需要正确的 Referer
     */
    private HashMap<String, String> getPlayHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        headers.put("Referer", playSite + "/");
        headers.put("Origin", playSite);
        headers.put("Accept", "*/*");
        return headers;
    }

    private List<Vod> parseJsonLd(String html) {
        List<Vod> list = new ArrayList<>();
        try {
            Pattern pattern = Pattern.compile("<script[^>]*type=\"application/ld\\+json\"[^>]*>(.*?)</script>", Pattern.DOTALL);
            Matcher matcher = pattern.matcher(html);

            while (matcher.find()) {
                String jsonStr = matcher.group(1).trim();
                if (!jsonStr.contains("VideoObject") && !jsonStr.contains("ItemList")) continue;

                JSONObject json = new JSONObject(jsonStr);
                String type = json.optString("@type", "");

                if ("ItemList".equals(type)) {
                    JSONArray items = json.optJSONArray("itemListElement");
                    if (items == null) continue;

                    for (int i = 0; i < items.length(); i++) {
                        JSONObject item = items.optJSONObject(i);
                        if (item == null) continue;

                        JSONObject video = item.optJSONObject("item");
                        if (video == null) {
                            if (item.has("name") && item.has("url")) {
                                video = item;
                            } else {
                                continue;
                            }
                        }

                        String name = video.optString("name", "");
                        String url = video.optString("url", "");
                        String thumb = video.optString("thumbnailUrl", "");

                        name = name.replace("_高清完整版视频在线观看_网飞猫", "")
                                   .replace("_电影", "").replace("_电视剧", "")
                                   .replace("_动漫", "").replace("_综艺", "")
                                   .replace("_短剧", "").trim();

                        String id = url.replaceAll(".*/(ncat-[^/]+)\\.html.*", "$1");
                        if (id.equals(url)) id = url;

                        if (!name.isEmpty() && !id.isEmpty()) {
                            list.add(new Vod(id, name, thumb));
                        }
                    }
                }
            }
        } catch (Exception e) {}
        return list;
    }

    private List<Vod> parseHtmlList(String html) {
        List<Vod> list = new ArrayList<>();
        Document doc = Jsoup.parse(html);
        Elements links = doc.select("a[href*=/ncat-]");
        for (Element a : links) {
            String href = a.attr("href");
            String name = a.text().trim();
            if (!name.isEmpty() && href.contains("/ncat-")) {
                String id = href.replaceAll(".*/(ncat-[^/]+)\\.html.*", "$1");
                if (id.equals(href)) id = href;
                list.add(new Vod(id, name, ""));
            }
        }
        return list;
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        classes.add(new Class("dianying", "电影"));
        classes.add(new Class("dianshiju", "电视剧"));
        classes.add(new Class("dongman", "动漫"));
        classes.add(new Class("duanju", "短剧"));
        classes.add(new Class("zongyi", "综艺"));

        String html = OkHttp.string(siteUrl + "/", getHeaders());
        List<Vod> list = parseJsonLd(html);
        if (list.isEmpty()) list = parseHtmlList(html);

        return Result.string(classes, list);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        List<Vod> list = new ArrayList<>();
        String target = siteUrl + "/" + tid + ".html";
        if (!"1".equals(pg)) {
            target = target + "?page=" + pg;
        }

        String html = OkHttp.string(target, getHeaders());
        list = parseJsonLd(html);
        if (list.isEmpty()) list = parseHtmlList(html);

        Integer total = (Integer.parseInt(pg) + 1) * 24;
        return Result.string(Integer.parseInt(pg), Integer.parseInt(pg) + 1, 24, total, list);
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String vodId = ids.get(0);
        String detailPage = siteUrl + "/" + vodId + ".html";
        String html = OkHttp.string(detailPage, getHeaders());
        Document doc = Jsoup.parse(html);

        // 提取标题
        String name = doc.select("h1").text();
        if (name.isEmpty()) {
            name = doc.title().replace("_网飞猫", "").replace("_高清完整版视频在线观看", "");
        }

        // 提取图片
        String pic = doc.select("img[alt=\"" + name + "\"]").attr("src");
        if (pic.isEmpty()) {
            pic = doc.select(".detail-pic img, .poster img, main img").attr("src");
        }
        if (pic.isEmpty()) {
            Pattern p = Pattern.compile("<script[^>]*type=\"application/ld\\+json\"[^>]*>(.*?)</script>", Pattern.DOTALL);
            Matcher m = p.matcher(html);
            while (m.find()) {
                try {
                    JSONObject json = new JSONObject(m.group(1).trim());
                    if (json.has("thumbnailUrl")) {
                        pic = json.getString("thumbnailUrl");
                        break;
                    }
                } catch (Exception ignored) {}
            }
        }

        // 提取简介
        String desc = doc.select("meta[name=description]").attr("content");
        if (desc.isEmpty()) {
            desc = doc.select(".summary, .detail-desc, .video-info-content, p.text-gray-400").text();
        }

        // 提取年份
        String year = "";
        Elements tags = doc.select("a[href*=/tag/], .tag, .breadcrumb a");
        for (Element tag : tags) {
            String text = tag.text().trim();
            if (text.matches("\\d{4}") || text.contains("年")) {
                year = text;
                break;
            }
        }

        // 统一播放页解析（首页推荐和分类/搜索共用）
        String playFrom = "网飞猫";
        String playUrlStr = "";

        try {
            // 构造 wzzy 播放页 URL
            String playId = vodId.replace("ncat-", "wzzy-");
            String playUrl = playSite + "/" + playId + ".html";
            String playHtml = OkHttp.string(playUrl, getPlayHeaders());

            // 用 Jsoup 解析 DOM，确保线路和 URL 正确匹配
            Document playDoc = Jsoup.parse(playHtml);

            // 查找所有线路标签（data-origin）
            Elements sourceTabs = playDoc.select("[data-origin]");
            // 查找所有 /movie/ 链接
            Elements movieLinks = playDoc.select("a[href*=/movie/]");

            LinkedHashMap<String, String> lineMap = new LinkedHashMap<>();

            // 方法1：从 data-origin 元素提取线路名，从 href 提取链接
            for (Element tab : sourceTabs) {
                String origin = tab.attr("data-origin");
                String tabText = tab.text().trim();

                // 跳过无效线路
                if (origin.isEmpty()) continue;
                if (tabText.isEmpty()) tabText = origin;

                // 在 movieLinks 里找匹配的 origin
                for (Element link : movieLinks) {
                    String href = link.attr("href");
                    if (href.contains("origin=" + origin)) {
                        String fullUrl = href.startsWith("http") ? href : playSite + href;
                        lineMap.put(tabText, fullUrl);
                        break;
                    }
                }
            }

            // 方法2：如果没匹配到，直接用 href 里的 origin 参数
            if (lineMap.isEmpty()) {
                for (Element link : movieLinks) {
                    String href = link.attr("href");
                    String text = link.text().trim();
                    if (href.contains("origin=") && !text.isEmpty()) {
                        String origin = href.replaceAll(".*origin=([^&]+).*", "$1");
                        String fullUrl = href.startsWith("http") ? href : playSite + href;
                        lineMap.put(origin, fullUrl);
                    }
                }
            }

            // 构建 PlayFrom 和 PlayUrl
            if (!lineMap.isEmpty()) {
                StringBuilder fromSb = new StringBuilder();
                StringBuilder urlSb = new StringBuilder();

                for (String lineName : lineMap.keySet()) {
                    String lineUrl = lineMap.get(lineName);

                    if (fromSb.length() > 0) fromSb.append("$$$");
                    fromSb.append(lineName);

                    if (urlSb.length() > 0) urlSb.append("$$$");
                    urlSb.append("立即播放$").append(lineUrl);
                }

                playFrom = fromSb.toString();
                playUrlStr = urlSb.toString();
            }

        } catch (Exception e) {
            // 解析失败时使用默认
        }

        // 保底：如果上面都没解析到，至少给一个默认播放 URL
        if (playUrlStr.isEmpty()) {
            String playId = vodId.replace("ncat-", "wzzy-");
            playUrlStr = "立即播放$" + playSite + "/" + playId + ".html";
        }

        Vod vod = new Vod();
        vod.setVodId(vodId);
        vod.setVodPic(pic.startsWith("http") ? pic : "");
        vod.setVodYear(year);
        vod.setVodName(name);
        vod.setVodContent(desc);
        vod.setVodPlayFrom(playFrom);
        vod.setVodPlayUrl(playUrlStr);
        return Result.string(vod);
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        List<Vod> list = new ArrayList<>();
        String target = siteUrl + "/search?q=" + URLEncoder.encode(key, "UTF-8");
        String html = OkHttp.string(target, getHeaders());

        list = parseJsonLd(html);
        if (list.isEmpty()) list = parseHtmlList(html);

        return Result.string(list);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        // id 格式：https://dyrsvip.cc/movie/VID-EID.html?origin=线路
        String playPageUrl;
        if (id.startsWith("http")) {
            playPageUrl = id;
        } else {
            playPageUrl = playSite + id;
        }

        // 访问播放线路页，提取 m3u8 直链
        String html = OkHttp.string(playPageUrl, getPlayHeaders());

        // 提取 m3u8 直链
        Pattern m3u8Pattern = Pattern.compile("(https?://[^\\s\"'<>]+\\.m3u8[^\\s\"'<>]*)");
        Matcher m3u8Matcher = m3u8Pattern.matcher(html);

        if (m3u8Matcher.find()) {
            String m3u8Url = m3u8Matcher.group(1);
            // 关键：返回时带上正确的 Referer，否则 CDN 会 403
            return Result.get().url(m3u8Url).header(getPlayHeaders()).string();
        }

        // 如果找不到 m3u8，返回原始 URL
        return Result.get().url(playPageUrl).header(getPlayHeaders()).string();
    }
}
