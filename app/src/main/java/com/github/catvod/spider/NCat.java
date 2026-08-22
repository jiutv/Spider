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
 * 网飞猫 / 可可影视 修复版 (2026-08-22)
 * 
 * 基于 ncat.it.com 网页版结构重写
 * 原 ncat3.app 已失效
 */
public class NCat extends Spider {

    // 主站（内容展示）
    private static final String siteUrl = "https://ncat.it.com";
    // 播放站（跳转解析）
    private static final String playSite = "https://dyrsvip.cc";
    // 图片 CDN
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
        headers.put("User-Agent", Util.CHROME);
        headers.put("Referer", playSite + "/");
        headers.put("Origin", playSite);
        return headers;
    }

    /**
     * 从 HTML 中提取 JSON-LD 视频列表
     */
    private List<Vod> parseJsonLd(String html) {
        List<Vod> list = new ArrayList<>();
        try {
            // 提取 <script type="application/ld+json"> 中的 VideoObject
            Pattern pattern = Pattern.compile("<script[^>]*type=\"application/ld\\+json\"[^>]*>(.*?)</script>", Pattern.DOTALL);
            Matcher matcher = pattern.matcher(html);
            
            while (matcher.find()) {
                String jsonStr = matcher.group(1).trim();
                if (!jsonStr.contains("VideoObject")) continue;
                
                JSONObject json = new JSONObject(jsonStr);
                String type = json.optString("@type", "");
                
                if ("ItemList".equals(type)) {
                    JSONArray items = json.optJSONArray("itemListElement");
                    if (items == null) continue;
                    
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject item = items.optJSONObject(i);
                        if (item == null) continue;
                        
                        JSONObject video = item.optJSONObject("item");
                        if (video == null) video = item; // 有些结构直接是 VideoObject
                        
                        String name = video.optString("name", "");
                        String url = video.optString("url", "");
                        String thumb = video.optString("thumbnailUrl", "");
                        
                        // 清理标题中的站点后缀
                        name = name.replace("_高清完整版视频在线观看_网飞猫", "")
                                   .replace("_电影", "")
                                   .replace("_电视剧", "")
                                   .replace("_动漫", "")
                                   .replace("_综艺", "")
                                   .replace("_短剧", "");
                        
                        // 提取 ID：ncat-it.com/ncat-12345-hash.html → ncat-12345-hash
                        String id = url.replaceAll(".*/(ncat-[^/]+)\\.html.*", "$1");
                        if (id.equals(url)) id = url;
                        
                        if (!name.isEmpty() && !id.isEmpty()) {
                            list.add(new Vod(id, name, thumb));
                        }
                    }
                } else if ("VideoObject".equals(type) || "Movie".equals(type) || "TVSeries".equals(type)) {
                    // 单视频详情页
                    String name = json.optString("name", "");
                    String url = json.optString("url", "");
                    String thumb = json.optString("thumbnailUrl", "");
                    
                    name = name.replace("_高清完整版视频在线观看_网飞猫", "").trim();
                    String id = url.replaceAll(".*/(ncat-[^/]+)\\.html.*", "$1");
                    
                    if (!name.isEmpty()) {
                        list.add(new Vod(id, name, thumb));
                    }
                }
            }
        } catch (Exception e) {
            // JSON-LD 解析失败时回退到 HTML 解析
        }
        return list;
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        
        // 新版分类（直接映射到 HTML 页面）
        classes.add(new Class("dianying", "电影"));
        classes.add(new Class("dianshiju", "电视剧"));
        classes.add(new Class("dongman", "动漫"));
        classes.add(new Class("duanju", "短剧"));
        classes.add(new Class("zongyi", "综艺"));

        String html = OkHttp.string(siteUrl + "/", getHeaders());
        List<Vod> list = parseJsonLd(html);
        
        // 如果 JSON-LD 没解析到，回退到 HTML
        if (list.isEmpty()) {
            Document doc = Jsoup.parse(html);
            Elements items = doc.select("a[href*=/ncat-]");
            for (Element a : items) {
                String href = a.attr("href");
                String name = a.text();
                if (href.contains("/ncat-") && !name.isEmpty()) {
                    String id = href.replaceAll(".*/(ncat-[^/]+)\\.html.*", "$1");
                    list.add(new Vod(id, name, ""));
                }
            }
        }
        
        return Result.string(classes, list);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        List<Vod> list = new ArrayList<>();
        
        // 新版分类页：/dianying.html, /dianshiju.html 等
        // 如果有分页可能是 /dianying.html?page=2
        String target = siteUrl + "/" + tid + ".html";
        if (!"1".equals(pg)) {
            target = target + "?page=" + pg;
        }
        
        String html = OkHttp.string(target, getHeaders());
        list = parseJsonLd(html);
        
        // 回退
        if (list.isEmpty()) {
            Document doc = Jsoup.parse(html);
            Elements items = doc.select("a[href*=/ncat-]");
            for (Element a : items) {
                String href = a.attr("href");
                String name = a.text();
                if (href.contains("/ncat-") && !name.isEmpty()) {
                    String id = href.replaceAll(".*/(ncat-[^/]+)\\.html.*", "$1");
                    list.add(new Vod(id, name, ""));
                }
            }
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
        if (name.isEmpty()) name = doc.title().replace("_网飞猫", "");
        
        // 提取图片
        String pic = doc.select("img[alt=\"" + name + "\"], .detail-pic img, .poster img").attr("src");
        if (pic.isEmpty()) {
            // 尝试从 JSON-LD 提取
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
        if (desc.isEmpty()) desc = doc.select(".summary, .detail-desc, .video-info-content").text();
        
        // 提取年份/标签
        String year = "";
        Elements tags = doc.select("a[href*=/tag/], .tag, .detail-tags-item");
        if (!tags.isEmpty()) year = tags.first().text();
        
        // 新版网飞猫详情页只有一个"立即播放"按钮，跳转到 dyrsvip.cc
        // 构造单线路单集播放
        String playFrom = "网飞猫";
        // 播放页 ID 格式：wzzy-ID-hash.html
        // vodId 格式：ncat-ID-hash
        String playId = vodId.replace("ncat-", "wzzy-");
        String playUrl = playSite + "/" + playId + ".html";
        
        // 注意：TVBox 的 playerContent 会处理这个 URL
        // 这里存储的是播放页地址，playerContent 再去提取真实 m3u8
        
        Vod vod = new Vod();
        vod.setVodId(vodId);
        vod.setVodPic(pic);
        vod.setVodYear(year);
        vod.setVodName(name);
        vod.setVodContent(desc);
        vod.setVodPlayFrom(playFrom);
        vod.setVodPlayUrl("立即播放$" + playUrl);
        return Result.string(vod);
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        List<Vod> list = new ArrayList<>();
        String target = siteUrl + "/search?q=" + URLEncoder.encode(key, "UTF-8");
        String html = OkHttp.string(target, getHeaders());
        list = parseJsonLd(html);
        
        // 回退
        if (list.isEmpty()) {
            Document doc = Jsoup.parse(html);
            Elements items = doc.select("a[href*=/ncat-]");
            for (Element a : items) {
                String href = a.attr("href");
                String name = a.text();
                if (href.contains("/ncat-") && !name.isEmpty()) {
                    String id = href.replaceAll(".*/(ncat-[^/]+)\\.html.*", "$1");
                    list.add(new Vod(id, name, ""));
                }
            }
        }
        return Result.string(list);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        // id 是播放页 URL，如 https://dyrsvip.cc/wzzy-91851-6a8919cc509fa44202d5b923.html
        String playPageUrl = id;
        
        // 1. 访问播放页
        String html = OkHttp.string(playPageUrl, getPlayHeaders());
        
        // 2. 提取 /api/m3u8?origin=...&url=... 接口
        Pattern apiPattern = Pattern.compile("/api/m3u8\\?[^\\s\"'<>]+");
        Matcher apiMatcher = apiPattern.matcher(html);
        
        if (apiMatcher.find()) {
            String apiPath = apiMatcher.group(0);
            String m3u8Api = playSite + apiPath;
            
            // 3. 请求 m3u8 API 获取真实播放地址
            // 这个接口返回的可能是 302 跳转，也可能是直接返回 m3u8 URL
            String m3u8Result = OkHttp.string(m3u8Api, getPlayHeaders());
            
            // 尝试从返回中提取 m3u8 URL
            String realUrl = m3u8Result.trim();
            if (realUrl.startsWith("http") && realUrl.contains(".m3u8")) {
                return Result.get().url(realUrl).header(getPlayHeaders()).string();
            }
            
            // 如果返回的是 JSON，解析 url 字段
            try {
                JSONObject json = new JSONObject(m3u8Result);
                String url = json.optString("url", "");
                if (!url.isEmpty() && url.startsWith("http")) {
                    return Result.get().url(url).header(getPlayHeaders()).string();
                }
            } catch (Exception ignored) {}
            
            // 如果上面都没拿到，直接返回 API 地址让播放器试试
            return Result.get().url(m3u8Api).header(getPlayHeaders()).string();
        }
        
        // 备用：直接搜索页面里的 m3u8 直链
        Pattern directPattern = Pattern.compile("https?://[^\\s\"'<>]+\\.m3u8[^\\s\"'<>]*");
        Matcher directMatcher = directPattern.matcher(html);
        if (directMatcher.find()) {
            return Result.get().url(directMatcher.group(0)).header(getPlayHeaders()).string();
        }
        
        // 最终备用：返回播放页本身（某些播放器框架能处理）
        return Result.get().url(playPageUrl).header(getPlayHeaders()).string();
    }
}
