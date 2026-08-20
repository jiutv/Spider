package com.github.catvod.spider;

import static com.github.catvod.utils.AESEncryption.CBC_PKCS_7_PADDING;

import android.content.Context;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.AESEncryption;
import com.github.catvod.utils.Util;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class W55Movie extends Spider {

    // ========== 更新默认域名为当前可用域名 ==========
    private static String siteUrl = "https://555dy7.com";
    private static String cateUrl = siteUrl + "/index.php/api/vod";
    private static String detailUrl = siteUrl + "/voddetail/";
    private static String playUrl = siteUrl + "/vodplay/";
    private static String searchUrl = siteUrl + "/vodsearch/";

    private HashMap<String, String> getHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", Util.CHROME);
        headers.put("Referer", siteUrl + "/");
        return headers;
    }

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
        if (extend != null && !extend.isEmpty()) {
            siteUrl = extend.replaceAll("/$", "");
        }
        cateUrl = siteUrl + "/index.php/api/vod";
        detailUrl = siteUrl + "/voddetail/";
        playUrl = siteUrl + "/vodplay/";
        searchUrl = siteUrl + "/vodsearch/";
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Vod> list = new ArrayList<>();
        List<Class> classes = new ArrayList<>();
        
        // 分类ID适配新站常见结构
        String[] typeIdList = {"/label/netflix", "/vodshow/1", "/vodshow/2", "/vodshow/3", "/vodshow/4", "/vodshow/20"};
        String[] typeNameList = {"Netflix", "电影", "连续剧", "综艺", "动漫", "短剧"};
        for (int i = 0; i < typeNameList.length; i++) {
            classes.add(new Class(typeIdList[i], typeNameList[i]));
        }
        
        Document doc = Jsoup.parse(OkHttp.string(siteUrl, getHeaders()));
        
        // ========== 更新选择器：兼容多种常见模板 ==========
        Elements items = doc.select("a.module-poster-item, a.vodlist_item, a.stui-vodlist__thumb, a.lazyload");
        if (items.isEmpty()) {
            items = doc.select("a[href^=/voddetail/], a[href^=/voddetail/]");
        }
        
        for (Element element : items) {
            try {
                String pic = element.select("img").attr("data-original");
                if (pic.isEmpty()) pic = element.select("img").attr("data-src");
                if (pic.isEmpty()) pic = element.select("img").attr("src");
                
                String url = element.attr("href");
                String name = element.attr("title");
                if (name.isEmpty()) name = element.select("img").attr("alt");
                
                String remark = element.select(".module-item-note, .pic-text, .stui-vodlist__detail p, .vodlist__detail p").text();
                
                if (!pic.startsWith("http")) {
                    pic = siteUrl + pic;
                }
                String id = url.split("/")[2].replace(".html", "");
                list.add(new Vod(id, name, pic, remark));
            } catch (Exception e) {
            }
        }
        return Result.string(classes, list);
    }

    public String MD5(String string) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hashBytes = md.digest(string.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte hashByte : hashBytes) {
                String hex = Integer.toHexString(0xff & hashByte);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return "";
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        List<Vod> list = new ArrayList<>();
        String target;
        if (tid.startsWith("/label/")) {
            target = siteUrl + tid + "/page/" + pg + ".html";
        } else if (tid.startsWith("/vodshow/")) {
            target = siteUrl + tid + "--------" + pg + "---.html";
        } else {
            target = siteUrl + tid;
        }
        
        Document doc = Jsoup.parse(OkHttp.string(target, getHeaders()));
        
        // ========== 更新选择器 ==========
        Elements items = doc.select("a.module-poster-item, a.vodlist_item, a.stui-vodlist__thumb, a.lazyload");
        if (items.isEmpty()) {
            items = doc.select("a[href^=/voddetail/], a[href^=/voddetail/]");
        }
        
        for (Element element : items) {
            try {
                String pic = element.select("img").attr("data-original");
                if (pic.isEmpty()) pic = element.select("img").attr("data-src");
                if (pic.isEmpty()) pic = element.select("img").attr("src");
                
                String url = element.attr("href");
                String name = element.attr("title");
                if (name.isEmpty()) name = element.select("img").attr("alt");
                
                String remark = element.select(".module-item-note, .pic-text, .stui-vodlist__detail p, .vodlist__detail p").text();
                
                if (!pic.startsWith("http")) {
                    pic = siteUrl + pic;
                }
                String id = url.split("/")[2].replace(".html", "");
                list.add(new Vod(id, name, pic, remark));
            } catch (Exception e) {
            }
        }

        Integer total = (Integer.parseInt(pg) + 1) * 20;
        return Result.string(Integer.parseInt(pg), Integer.parseInt(pg) + 1, 20, total, list);
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        Document doc = Jsoup.parse(OkHttp.string(detailUrl.concat(ids.get(0)).concat(".html"), getHeaders()));
        
        // ========== 更新选择器 ==========
        String name = doc.select("h1, .stui-content__detail h3, .vodlist__detail h3").first() != null 
            ? doc.select("h1, .stui-content__detail h3, .vodlist__detail h3").first().text() : "";
            
        String pic = doc.select("img.ls-is-cached, .stui-content__thumb img, .vodlist__thumb img").attr("data-original");
        if (pic.isEmpty()) pic = doc.select("img.ls-is-cached, .stui-content__thumb img, .vodlist__thumb img").attr("src");
        
        // 标签信息兼容多种模板
        String year = "", area = "", tags = "";
        Elements desc = doc.select("div.module-info-tag-link, .stui-content__detail p, .vodlist__detail p, .data");
        if (!desc.isEmpty()) {
            Elements links = desc.first().select("a");
            if (links.size() > 0) year = links.get(0).text();
            if (links.size() > 1) area = links.get(1).text();
            if (links.size() > 2) tags = links.get(2).text();
        }
        
        String content = doc.select("meta[name=description]").attr("content");

        // 播放源兼容
        Elements tabs = doc.select("div.module-tab-item, .stui-vodlist__head h3, .play_source_tab span, .tab_control span");
        Elements list = doc.select("div.module-play-list-content, .stui-content__playlist, .play_list");
        
        String PlayFrom = "";
        String PlayUrl = "";
        
        if (tabs.isEmpty() && !list.isEmpty()) {
            // 无标签单源情况
            tabs = list;
        }
        
        for (int i = 0; i < tabs.size(); i++) {
            String tabName = tabs.get(i).text();
            if (tabName.isEmpty()) tabName = "线路" + (i + 1);
            
            if (!"".equals(PlayFrom)) {
                PlayFrom = PlayFrom + "$$$" + tabName;
            } else {
                PlayFrom = PlayFrom + tabName;
            }
            
            Elements li;
            if (list.size() > i) {
                li = list.get(i).select("a");
            } else {
                li = list.first() != null ? list.first().select("a") : new Elements();
            }
            
            String liUrl = "";
            for (int i1 = 0; i1 < li.size(); i1++) {
                String epName = li.get(i1).text();
                String epUrl = li.get(i1).attr("href").replace("/vodplay/", "").replace(".html", "");
                if (!"".equals(liUrl)) {
                    liUrl = liUrl + "#" + epName + "$" + epUrl;
                } else {
                    liUrl = liUrl + epName + "$" + epUrl;
                }
            }
            if (!"".equals(PlayUrl)) {
                PlayUrl = PlayUrl + "$$$" + liUrl;
            } else {
                PlayUrl = PlayUrl + liUrl;
            }
        }

        Vod vod = new Vod();
        vod.setVodId(ids.get(0));
        vod.setVodPic(pic);
        vod.setVodYear(year);
        vod.setVodArea(area);
        vod.setVodTag(tags);
        vod.setVodContent(content);
        vod.setVodName(name);
        vod.setVodPlayFrom(PlayFrom);
        vod.setVodPlayUrl(PlayUrl);
        return Result.string(vod);
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        List<Vod> list = new ArrayList<>();
        String target = searchUrl.concat(URLEncoder.encode(key)).concat("-------------.html");
        Document doc = Jsoup.parse(OkHttp.string(target, getHeaders()));
        
        // ========== 更新选择器 ==========
        Elements items = doc.select(".module-card-item, .stui-vodlist__item, .vodlist_item, .searchlist_item");
        if (items.isEmpty()) {
            items = doc.select("a[href^=/voddetail/], a[href^=/voddetail/]");
        }
        
        for (Element element : items) {
            try {
                Element link = element.select("a").first() != null ? element.select("a").first() : element;
                String pic = element.select("img").attr("data-original");
                if (pic.isEmpty()) pic = element.select("img").attr("data-src");
                if (pic.isEmpty()) pic = element.select("img").attr("src");
                
                String url = link.attr("href");
                String name = element.select(".module-card-item-title, .stui-vodlist__title, .vodlist__title, .title").text();
                if (name.isEmpty()) name = link.attr("title");
                if (name.isEmpty()) name = element.select("img").attr("alt");
                
                String remark = element.select(".module-item-note, .pic-text, .stui-vodlist__detail p").text();
                
                if (!pic.startsWith("http")) {
                    pic = siteUrl + pic;
                }
                String id = url.split("/")[2].replace(".html", "");
                list.add(new Vod(id, name, pic, remark));
            } catch (Exception e) {
            }
        }
        return Result.string(list);
    }

    private static final String keyString = "a67e9a3a85049339";
    private static final String ivString = "86ad9b37cc9f5b9501b3cecc7dc6377c";

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        String target = playUrl.concat(id).concat(".html");
        Document doc = Jsoup.parse(OkHttp.string(target, getHeaders()));
        
        // 尝试多种正则匹配播放地址
        String url = "";
        String[] regexes = {
            "\"url\\\":\\\"(.*?)\\\",\\\"url_next\\\":\\\"(.*?)\\\"",
            "\"url\":\"(.*?)\",\"url_next\":\"(.*?)\"",
            "var url = \\\"(.*?)\\\"",
            "var player_aaaa=(\\{.*?\\})",
            "\"video\":\\{\"url\":\"(.*?)\""
        };
        
        for (String regex : regexes) {
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(doc.html());
            if (matcher.find()) {
                url = matcher.group(1);
                break;
            }
        }
        
        // 如果直接拿到m3u8或mp4直链
        if (url.contains(".m3u8") || url.contains(".mp4")) {
            return Result.get().url(url).header(getHeaders()).string();
        }
        
        // 尝试原加密解析逻辑
        if (!url.isEmpty()) {
            try {
                String encrytStr = url;
                String encrypt = AESEncryption.encrypt(encrytStr, keyString, ivString, CBC_PKCS_7_PADDING);
                String encodeURI = AESEncryption.encodeURIComponent(encrypt);
                String data = OkHttp.string("https://player.ddzyku.com:3653/get_url_v2?data=" + encodeURI);
                String decrypted = AESEncryption.decrypt(data, keyString, ivString, CBC_PKCS_7_PADDING);
                Gson gson = new Gson();
                JsonObject jsonObject = gson.fromJson(decrypted, JsonObject.class);
                if (jsonObject != null && jsonObject.has("data")) {
                    JsonObject dataObject = jsonObject.getAsJsonObject("data");
                    if (dataObject != null && dataObject.has("url")) {
                        url = dataObject.get("url").getAsString();
                    }
                }
            } catch (Exception e) {
                // 解析失败则返回原始url
            }
        }
        
        if (url.isEmpty()) {
            return Result.error("播放链接解析失败");
        }
        
        return Result.get().url(url).header(getHeaders()).string();
    }
}
