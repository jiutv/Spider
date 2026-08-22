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
 * 网飞猫 / 可可影视 最终修复版 (2026-08-22)
 * 
 * 播放流程：
 * 1. ncat.it.com 详情页 → 提取 wzzy-xxx.html 播放页
 * 2. wzzy-xxx.html → 提取多线路（data-origin）和 /movie/VID-EID.html?origin=线路 链接
 * 3. /movie/VID-EID.html?origin=线路 → 提取 m3u8 直链
 */
public class NCat extends Spider {

    private static final String siteUrl = "https://ncat.it.com";
    private static final String playSite = "https://dyrsvip.cc";
    private static final String picUrl = "https://ncat.it.com/img/id/";

    private HashMap<String, String> getHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", Util.CHROME);
        headers.put("Referer", siteUrl + "/");
        headers.put("Accept-Language", "zh-CN,zh;q=0.9");
        return headers;
    }

    private HashMap<String, String> getPlayHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        headers.put("Referer", playSite + "/");
        headers.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        return headers;
    }

    /**
     * 从 HTML 提取 JSON-LD 视频列表
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

    /**
     * 回退：HTML a 标签解析
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

        // 构造播放页 URL
        String playId = vodId.replace("ncat-", "wzzy-");
        String playUrl = playSite + "/" + playId + ".html";

        // 解析播放页，提取多线路
        String playFrom = "网飞猫";
        String playUrlStr = "立即播放$" + playUrl;

        try {
            String playHtml = OkHttp.string(playUrl, getPlayHeaders());
            
            // 提取所有线路（data-origin）和对应的 /movie/ 链接
            LinkedHashMap<String, String> lines = new LinkedHashMap<>();
            
            // 方法1：从 data-origin 提取
            Pattern originPattern = Pattern.compile("data-origin=\"([^\"]+)\"");
            Matcher originMatcher = originPattern.matcher(playHtml);
            
            // 同时提取 /movie/VID-EID.html?origin=LINE 链接
            Pattern moviePattern = Pattern.compile("href=\"(/movie/[^\"]+\\?origin=[^\"]+)\"");
            Matcher movieMatcher = moviePattern.matcher(playHtml);
            
            List<String> origins = new ArrayList<>();
            while (originMatcher.find()) {
                origins.add(originMatcher.group(1));
            }
            
            List<String> movies = new ArrayList<>();
            while (movieMatcher.find()) {
                movies.add(movieMatcher.group(1));
            }
            
            // 匹配线路和链接
            if (!origins.isEmpty() && !movies.isEmpty()) {
                StringBuilder fromSb = new StringBuilder();
                StringBuilder urlSb = new StringBuilder();
                
                for (int i = 0; i < origins.size() && i < movies.size(); i++) {
                    String lineName = origins.get(i);
                    String lineUrl = playSite + movies.get(i);
                    
                    if (fromSb.length() > 0) fromSb.append("$$$");
                    fromSb.append(lineName);
                    
                    if (urlSb.length() > 0) urlSb.append("$$$");
                    urlSb.append("立即播放$").append(lineUrl);
                }
                
                if (fromSb.length() > 0) {
                    playFrom = fromSb.toString();
                    playUrlStr = urlSb.toString();
                }
            }

        } catch (Exception e) {
            // 播放页解析失败，使用默认
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
            return Result.get().url(m3u8Url).header(getPlayHeaders()).string();
        }
        
        // 如果找不到 m3u8，返回原始 URL（让播放器试试）
        return Result.get().url(playPageUrl).header(getPlayHeaders()).string();
    }
}
