package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;

import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URLEncoder;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

public class Czzy extends Spider {

    private String siteUrl = "https://www.4kcz.com";

    private HashMap<String, String> getHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        headers.put("Referer", siteUrl + "/");
        headers.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
        headers.put("Accept-Language", "zh-CN,zh;q=0.9");
        return headers;
    }

    @Override
    public void init(Context context, String ext) throws Exception {
        super.init(context, ext);
        if (!TextUtils.isEmpty(ext)) {
            siteUrl = ext.trim();
            if (siteUrl.endsWith("/")) {
                siteUrl = siteUrl.substring(0, siteUrl.length() - 1);
            }
        }
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        String html = OkHttp.string(siteUrl, getHeaders());
        Document doc = Jsoup.parse(html);
        JSONObject result = new JSONObject();
        JSONArray classes = new JSONArray();
        LinkedHashMap<String, String> classMap = new LinkedHashMap<>();

        Elements navItems = doc.select("ul.navlist > li > a");
        for (Element a : navItems) {
            String href = a.attr("href");
            String text = a.text().trim();
            if (href.startsWith("/") && !href.equals("/") && !text.isEmpty()) {
                classMap.put(href, text);
            }
        }

        for (String href : classMap.keySet()) {
            JSONObject cls = new JSONObject();
            cls.put("type_id", href);
            cls.put("type_name", classMap.get(href));
            classes.put(cls);
        }
        result.put("class", classes);

        JSONArray list = new JSONArray();
        getVods(list, doc);
        result.put("list", list);
        return result.toString();
    }

    @Override
    public String homeVideoContent() throws Exception {
        return homeContent(false);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        String url = siteUrl + tid;
        if (!"1".equals(pg)) {
            url = url + "/page/" + pg;
        }
        String html = OkHttp.string(url, getHeaders());
        Document doc = Jsoup.parse(html);
        JSONArray list = new JSONArray();
        getVods(list, doc);

        int page = Integer.parseInt(pg);
        int pageCount = page;
        Elements nextPage = doc.select("a:contains(下一页)");
        if (!nextPage.isEmpty()) {
            pageCount = page + 1;
        }

        JSONObject result = new JSONObject();
        result.put("page", page);
        result.put("pagecount", pageCount);
        result.put("limit", 25);
        result.put("total", pageCount * 25);
        result.put("list", list);
        return result.toString();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String id = ids.get(0);
        String url = siteUrl + id;
        String html = OkHttp.string(url, getHeaders());
        Document doc = Jsoup.parse(html);
        JSONObject result = new JSONObject();
        JSONArray list = new JSONArray();
        JSONObject vod = new JSONObject();

        vod.put("vod_id", id);
        Element h1 = doc.selectFirst("h1");
        vod.put("vod_name", h1 != null ? h1.text().trim() : "");

        // 封面图选择器修正：用 div.dyimg img
        Element poster = doc.selectFirst("div.dyimg img");
        if (poster != null) {
            String src = poster.attr("src");
            if (src.contains("blank.gif")) src = poster.attr("data-original");
            vod.put("vod_pic", src);
        }

        // 信息
        Elements infoLis = doc.select("div.moviedteail_list li, .moviedteail_list li");
        StringBuilder info = new StringBuilder();
        for (Element li : infoLis) {
            info.append(li.text().trim()).append(" ");
        }
        vod.put("vod_content", info.toString().trim());

        // 播放列表 - 修正格式，单源不加$$$
        Elements playBtns = doc.select("div.paly_list_btn > a");
        if (playBtns.isEmpty()) {
            playBtns = doc.select(".paly_list_btn a");
        }

        StringBuilder vodPlayUrl = new StringBuilder();
        String vodPlayFrom = "厂长资源";

        for (int i = 0; i < playBtns.size(); i++) {
            Element a = playBtns.get(i);
            String text = a.text().trim();
            String href = a.attr("href");
            if (href.startsWith("/")) {
                href = siteUrl + href;
            }
            vodPlayUrl.append(text).append("$").append(href);
            if (i < playBtns.size() - 1) {
                vodPlayUrl.append("#");
            }
        }

        vod.put("vod_play_from", vodPlayFrom);
        vod.put("vod_play_url", vodPlayUrl.toString());
        list.put(vod);
        result.put("list", list);
        return result.toString();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        String url = siteUrl + "/nimasile/?q=" + URLEncoder.encode(key, "UTF-8");
        String html = OkHttp.string(url, getHeaders());
        Document doc = Jsoup.parse(html);
        JSONObject result = new JSONObject();
        JSONArray list = new JSONArray();
        getVods(list, doc);
        result.put("list", list);
        return result.toString();
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        String html = OkHttp.string(id, getHeaders());
        Document doc = Jsoup.parse(html);
        Element iframe = doc.selectFirst("iframe");
        if (iframe != null) {
            String src = iframe.attr("src");
            if (!src.isEmpty()) {
                JSONObject result = new JSONObject();
                result.put("parse", 0);
                result.put("playUrl", "");
                result.put("url", src);
                result.put("header", new JSONObject().put("User-Agent", "Mozilla/5.0").toString());
                return result.toString();
            }
        }
        JSONObject result = new JSONObject();
        result.put("parse", 1);
        result.put("playUrl", "");
        result.put("url", id);
        result.put("header", "");
        return result.toString();
    }

    private void getVods(JSONArray list, Document doc) throws JSONException {
        Elements uls = doc.select("ul");
        for (Element ul : uls) {
            if (!ul.className().isEmpty()) continue;
            Elements lis = ul.select("> li");
            if (lis.size() < 2) continue;
            Element firstA = lis.first() != null ? lis.first().selectFirst("a[href*=/movie/]") : null;
            if (firstA == null) continue;

            for (Element li : lis) {
                Element a = li.selectFirst("a[href*=/movie/]");
                if (a == null) continue;
                String href = a.attr("href");
                String name = "";
                String pic = "";
                String remark = "";

                Element img = li.selectFirst("img");
                if (img != null) {
                    name = img.attr("alt");
                    pic = img.attr("data-original");
                    if (pic.isEmpty()) pic = img.attr("src");
                }

                Element dytit = li.selectFirst("h3.dytit a");
                if (dytit != null && name.isEmpty()) {
                    name = dytit.text().trim();
                }

                Element hdinfo = li.selectFirst("div.hdinfo span");
                if (hdinfo != null) {
                    remark = hdinfo.text().trim();
                }

                if (!href.isEmpty() && !name.isEmpty()) {
                    JSONObject vod = new JSONObject();
                    vod.put("vod_id", href);
                    vod.put("vod_name", name);
                    vod.put("vod_pic", pic);
                    vod.put("vod_remarks", remark);
                    list.put(vod);
                }
            }
            break;
        }
    }
}
