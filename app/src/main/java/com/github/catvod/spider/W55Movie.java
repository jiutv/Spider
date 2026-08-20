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

    // ========== 修正：使用实际主域名 ==========
    private static String siteUrl = "https://www.555dy.net";
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
        detailUrl = siteUrl + "/voddetail/";
        playUrl = siteUrl + "/vodplay/";
        searchUrl = siteUrl + "/vodsearch/";
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Vod> list = new ArrayList<>();
        List<Class> classes = new ArrayList<>();

        // ========== 修正：分类 ID 对齐实际网站导航 ==========
        // 实际 HTML 中导航链接：/vodtype/1.html /vodtype/2.html /vodtype/126.html 等
        String[] typeIdList = {"/label/netflix", "/vodtype/1", "/vodtype/2", "/vodtype/126", "/vodtype/4", "/vodtype/3"};
        String[] typeNameList = {"Netflix", "电影", "连续剧", "擦边短剧", "动漫", "综艺纪录"};
        for (int i = 0; i < typeNameList.length; i++) {
            classes.add(new Class(typeIdList[i], typeNameList[i]));
        }

        Document doc = Jsoup.parse(OkHttp.string(siteUrl, getHeaders()));

        // ========== 修正：基于真实 HTML 的选择器 ==========
        // <a href="/voddetail/792246.html" title="末日地堡第三季" class="module-poster-item module-item">
        for (Element element : doc.select("a.module-poster-item")) {
            try {
                // 图片：data-original 属性
                String pic = element.select("img").attr("data-original");
                String url = element.attr("href");
                // 标题优先用 a 的 title 属性
                String name = element.attr("title");
                if (name.isEmpty()) {
                    name = element.select(".module-poster-item-title").text();
                }
                // 备注：.module-item-note
                String remark = element.select(".module-item-note").text();

                if (!pic.startsWith("http")) {
                    pic = siteUrl + pic;
                }
                // ID 提取：/voddetail/792246.html → 792246
                String id = url.replace("/voddetail/", "").replace(".html", "");
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
                if (hex.length() == 1) hexString.append('0');
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

        // ========== 修正：分页 URL 构造 ==========
        if (tid.startsWith("/label/")) {
            // Netflix 分页：/label/netflix/page/2.html
            target = siteUrl + tid + "/page/" + pg + ".html";
        } else if (tid.startsWith("/vodtype/")) {
            // 分类分页：/vodtype/1.html → /vodshow/1--------2---.html
            String typeId = tid.replace("/vodtype/", "").replace(".html", "");
            target = siteUrl + "/vodshow/" + typeId + "--------" + pg + "---.html";
        } else {
            target = siteUrl + tid;
        }

        Document doc = Jsoup.parse(OkHttp.string(target, getHeaders()));

        for (Element element : doc.select("a.module-poster-item")) {
            try {
                String pic = element.select("img").attr("data-original");
                String url = element.attr("href");
                String name = element.attr("title");
                if (name.isEmpty()) {
                    name = element.select(".module-poster-item-title").text();
                }
                String remark = element.select(".module-item-note").text();

                if (!pic.startsWith("http")) {
                    pic = siteUrl + pic;
                }
                String id = url.replace("/voddetail/", "").replace(".html", "");
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
        String name = doc.select("h1").text();
        String pic = doc.select("img.lazyload").attr("data-original");
        if (pic.isEmpty()) pic = doc.select("img.lazy").attr("data-original");

        Elements desc = doc.select("div.module-info-tag-link");
        String year = "", area = "", tags = "";
        if (!desc.isEmpty()) {
            Elements links = desc.first().select("a");
            if (links.size() > 0) year = links.get(0).text();
            if (links.size() > 1) area = links.get(1).text();
            if (links.size() > 2) tags = links.get(2).text();
        }
        String content = doc.select("meta[name=description]").attr("content");

        // 播放源
        Elements tabs = doc.select("div.module-tab-item");
        Elements playList = doc.select("div.module-play-list-content");
        String PlayFrom = "";
        String PlayUrl = "";
        for (int i = 0; i < tabs.size(); i++) {
            String tabName = tabs.get(i).text();
            if (!"".equals(PlayFrom)) {
                PlayFrom = PlayFrom + "$$$" + tabName;
            } else {
                PlayFrom = PlayFrom + tabName;
            }
            Elements li = playList.get(i).select("a");
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

        // 搜索结果也是 module-poster-item
        for (Element element : doc.select("a.module-poster-item")) {
            try {
                String pic = element.select("img").attr("data-original");
                String url = element.attr("href");
                String name = element.attr("title");
                if (name.isEmpty()) {
                    name = element.select(".module-poster-item-title").text();
                }
                String remark = element.select(".module-item-note").text();

                if (!pic.startsWith("http")) {
                    pic = siteUrl + pic;
                }
                String id = url.replace("/voddetail/", "").replace(".html", "");
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
        String regex = "\"url\\\":\\\"(.*?)\\\",\\\"url_next\\\":\\\"(.*?)\\\"";

        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(doc.html());
        String url = "";
        String url_next = "";
        if (matcher.find()) {
            url = matcher.group(1);
            url_next = matcher.group(2);
        }
        String encrytStr = url;
        String encrypt = AESEncryption.encrypt(encrytStr, keyString, ivString, CBC_PKCS_7_PADDING);
        String encodeURI = AESEncryption.encodeURIComponent(encrypt);
        String data = OkHttp.string("https://player.ddzyku.com:3653/get_url_v2?data=" + encodeURI);
        String decrypted = AESEncryption.decrypt(data, keyString, ivString, CBC_PKCS_7_PADDING);
        Gson gson = new Gson();
        JsonObject jsonObject = gson.fromJson(decrypted, JsonObject.class);
        JsonObject dataObject = jsonObject.getAsJsonObject("data");
        String url1 = "";
        if (dataObject != null && dataObject.has("url")) {
            url1 = dataObject.get("url").getAsString();
        }
        return Result.get().url(url1).header(getHeaders()).string();
    }
}
