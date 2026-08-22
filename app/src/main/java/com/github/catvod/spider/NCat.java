
package com.github.catvod.spider;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Json;
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
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 网飞猫 / 可可影视 最终修复版 (2026-08-22)
 * 
 * 基于：
 * - 网页版：ncat.it.com（当前可用，Cloudflare CDN）
 * - 播放解析：dyrsvip.cc（APP 跳转的播放站）
 * - CDN：vodcnd13.ajupf.com 等（m3u8 直链）
 * - APK 分析：c200000-NcatC200000V260822150300-3.5.0.30500_gr.apk
 *   提取接口路径：/thread/home.capi, /vod/playUnits.capi 等
 *   APP UA: com.salmon.film.app.start.App/3.5.0
 */
public class NCat extends Spider {

    // 主站（内容展示）
    private static final String siteUrl = "https://ncat.it.com";
    // 播放站（APP 跳转解析）
    private static final String playSite = "https://dyrsvip.cc";
    // 图片 CDN
    private static final String picUrl = "https://ncat.it.com/img/id/";

    private HashMap<String, String> getHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", Util.CHROME);
        headers.put("Referer", siteUrl + "/");
        headers.put("Accept-Language", "zh-CN,zh;q=0.9");
        headers.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
        return headers;
    }

    private HashMap<String, String> getPlayHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        // 模拟 APP 请求头（从抓包提取）
        headers.put("User-Agent", "com.salmon.film.app.start.App/3.5.0 (Linux;Android 9) AndroidXMedia3/1.9.0");
        headers.put("Referer", playSite + "/");
        headers.put("Accept", "*/*");
        headers.put("Accept-Encoding", "gzip");
        return headers;
    }

    /**
     * 从 HTML 中提取 JSON-LD 视频列表
     * 新版 ncat.it.com 使用 schema.org/VideoObject 结构化数据
     */
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

                        // 有些结构 item 里嵌套 VideoObject
                        JSONObject video = item.optJSONObject("item");
                        if (video == null) {
                            // 有些结构直接是 VideoObject
                            if (item.has("name") && item.has("url")) {
                                video = item;
                            } else {
                                continue;
                            }
                        }

                        String name = video.optString("name", "");
                        String url = video.optString("url", "");
                        String thumb = video.optString("thumbnailUrl", "");

                        // 清理标题
                        name = name.replace("_高清完整版视频在线观看_网飞猫", "")
                                   .replace("_电影", "").replace("_电视剧", "")
                                   .replace("_动漫", "").replace("_综艺", "")
                                   .replace("_短剧", "").trim();

                        // 提取 ID：ncat-it.com/ncat-12345-hash.html → ncat-12345-hash
                        String id = url.replaceAll(".*/(ncat-[^/]+)\\.html.*", "$1");
                        if (id.equals(url)) id = url;

                        if (!name.isEmpty() && !id.isEmpty()) {
                            list.add(new Vod(id, name, thumb));
                        }
                    }
                }
            }
        } catch (Exception e) {
            // JSON-LD 解析失败时返回空列表，由调用方回退到 HTML 解析
        }
        return list;
    }

    /**
     * 回退：从 HTML a 标签解析视频列表
     */
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

        // 新版分类（映射到 HTML 页面）
        classes.add(new Class("dianying", "电影"));
        classes.add(new Class("dianshiju", "电视剧"));
        classes.add(new Class("dongman", "动漫"));
        classes.add(new Class("duanju", "短剧"));
        classes.add(new Class("zongyi", "综艺"));

        String html = OkHttp.string(siteUrl + "/", getHeaders());
        List<Vod> list = parseJsonLd(html);

        // 回退到 HTML 解析
        if (list.isEmpty()) {
            list = parseHtmlList(html);
        }

        return Result.string(classes, list);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        List<Vod> list = new ArrayList<>();

        // 分类页 URL：/dianying.html, /dianshiju.html 等
        String target = siteUrl + "/" + tid + ".html";
        if (!"1".equals(pg)) {
            target = target + "?page=" + pg;
        }

        String html = OkHttp.string(target, getHeaders());
        list = parseJsonLd(html);

        if (list.isEmpty()) {
            list = parseHtmlList(html);
        }

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
        // 回退：从 JSON-LD 提取
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

        // 提取年份/标签
        String year = "";
        Elements tags = doc.select("a[href*=/tag/], .tag, .breadcrumb a");
        for (Element tag : tags) {
            String text = tag.text().trim();
            if (text.matches("\\d{4}") || text.contains("年")) {
                year = text;
                break;
            }
        }

        // 新版网飞猫详情页只有一个"立即播放"按钮，跳转到 dyrsvip.cc
        // 播放页 ID 格式：wzzy-ID-hash.html
        // vodId 格式：ncat-ID-hash
        String playId = vodId.replace("ncat-", "wzzy-");
        String playUrl = playSite + "/" + playId + ".html";

        // 尝试预解析播放页，提取多集信息
        String playFrom = "网飞猫";
        String playUrlStr = "立即播放$" + playUrl;

        try {
            String playHtml = OkHttp.string(playUrl, getPlayHeaders());
            Document playDoc = Jsoup.parse(playHtml);

            // 查找多集播放列表
            Elements episodes = playDoc.select("a[href*=/play/], .episode-list a, .play-list a, a[data-url]");
            if (!episodes.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < episodes.size(); i++) {
                    Element ep = episodes.get(i);
                    String epName = ep.text().trim();
                    String epUrl = ep.attr("href");
                    if (epUrl.isEmpty()) epUrl = ep.attr("data-url");
                    if (epName.isEmpty()) epName = "第" + (i + 1) + "集";

                    if (sb.length() > 0) sb.append("#");
                    sb.append(epName).append("$").append(epUrl.replace("/play/", ""));
                }
                if (sb.length() > 0) {
                    playUrlStr = sb.toString();
                }
            }

            // 查找多线路
            Elements sources = playDoc.select(".source-item, .play-source-tab, .tab-item, [data-source]");
            if (!sources.isEmpty()) {
                StringBuilder fromSb = new StringBuilder();
                StringBuilder urlSb = new StringBuilder();
                for (int i = 0; i < sources.size(); i++) {
                    String sourceName = sources.get(i).text().trim();
                    if (sourceName.isEmpty()) sourceName = "线路" + (i + 1);

                    if (fromSb.length() > 0) fromSb.append("$$$");
                    fromSb.append(sourceName);

                    // 每个线路的剧集列表
                    Elements epList = playDoc.select(".episode-list").eq(i).select("a");
                    StringBuilder lineSb = new StringBuilder();
                    for (int j = 0; j < epList.size(); j++) {
                        Element ep = epList.get(j);
                        String epName = ep.text().trim();
                        String epUrl = ep.attr("href").replace("/play/", "");
                        if (epName.isEmpty()) epName = "第" + (j + 1) + "集";

                        if (lineSb.length() > 0) lineSb.append("#");
                        lineSb.append(epName).append("$").append(epUrl);
                    }
                    if (lineSb.length() == 0) lineSb.append("立即播放$").append(playUrl);

                    if (urlSb.length() > 0) urlSb.append("$$$");
                    urlSb.append(lineSb);
                }
                if (fromSb.length() > 0) {
                    playFrom = fromSb.toString();
                    playUrlStr = urlSb.toString();
                }
            }

        } catch (Exception e) {
            // 播放页解析失败，使用默认单集
        }

        Vod vod = new Vod();
        vod.setVodId(vodId);
        vod.setVodPic(pic.startsWith("http") ? pic : picUrl + pic);
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

        if (list.isEmpty()) {
            list = parseHtmlList(html);
        }

        return Result.string(list);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        // id 可能是：
        // 1. 完整播放页 URL：https://dyrsvip.cc/wzzy-xxx.html
        // 2. 播放页路径：/wzzy-xxx.html
        // 3. 剧集 ID（从多集列表传入）

        String playPageUrl;
        if (id.startsWith("http")) {
            playPageUrl = id;
        } else if (id.startsWith("/")) {
            playPageUrl = playSite + id;
        } else {
            // 可能是剧集 ID，构造播放页
            playPageUrl = playSite + "/play/" + id;
        }

        // 1. 访问播放页
        String html = OkHttp.string(playPageUrl, getPlayHeaders());

        // 2. 提取 /api/m3u8?origin=...&url=... 接口（从 APK 和网页分析确认）
        Pattern apiPattern = Pattern.compile("(/api/m3u8\\?[^\\s\"'<>]+)");
        Matcher apiMatcher = apiPattern.matcher(html);

        if (apiMatcher.find()) {
            String apiPath = apiMatcher.group(1);
            // 确保完整 URL
            String m3u8Api;
            if (apiPath.startsWith("http")) {
                m3u8Api = apiPath;
            } else {
                m3u8Api = playSite + apiPath;
            }

            // 3. 请求 m3u8 API 获取真实播放地址
            // 这个接口可能返回 302 跳转，也可能返回 JSON 或直链
            String m3u8Result = OkHttp.string(m3u8Api, getPlayHeaders());

            // 尝试直接返回 m3u8 URL
            String realUrl = m3u8Result.trim();
            if (realUrl.startsWith("http") && realUrl.contains(".m3u8")) {
                return Result.get().url(realUrl).header(getPlayHeaders()).string();
            }

            // 尝试 JSON 解析
            try {
                JSONObject json = new JSONObject(m3u8Result);
                String url = json.optString("url", "");
                if (!url.isEmpty() && url.startsWith("http")) {
                    return Result.get().url(url).header(getPlayHeaders()).string();
                }
                // 有些返回 data.url 结构
                JSONObject data = json.optJSONObject("data");
                if (data != null) {
                    url = data.optString("url", "");
                    if (!url.isEmpty()) {
                        return Result.get().url(url).header(getPlayHeaders()).string();
                    }
                }
            } catch (Exception ignored) {}

            // 如果上面都没拿到，直接返回 API 地址让播放器处理
            return Result.get().url(m3u8Api).header(getPlayHeaders()).string();
        }

        // 备用：直接搜索页面里的 m3u8 直链
        Pattern directPattern = Pattern.compile("https?://[^\\s\"'<>]+\\.m3u8[^\\s\"'<>]*");
        Matcher directMatcher = directPattern.matcher(html);
        if (directMatcher.find()) {
            return Result.get().url(directMatcher.group(0)).header(getPlayHeaders()).string();
        }

        // 最终备用：返回播放页本身
        return Result.get().url(playPageUrl).header(getPlayHeaders()).string();
    }
}
