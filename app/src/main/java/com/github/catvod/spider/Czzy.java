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
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 厂长资源 (4kcz.com) TVBox 爬虫
 * 
 * 支持两种模式：
 * 1. 苹果CMS API 模式（优先）- 通过 /api.php/provide/vod 接口获取数据
 * 2. HTML 解析模式（备选）- 如果API关闭，解析网页HTML获取数据
 * 
 * 配置示例：
 * {
 *   "key": "csp_Czzy",
 *   "name": "厂长资源",
 *   "type": 3,
 *   "api": "csp_Czzy",
 *   "searchable": 1,
 *   "quickSearch": 1,
 *   "filterable": 1,
 *   "ext": "https://www.4kcz.com"
 * }
 */
public class Czzy extends Spider {

    private String siteUrl = "https://www.4kcz.com";
    private boolean useApi = true;

    private static final String API_PATH = "/api.php/provide/vod";

    private HashMap<String, String> getHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        headers.put("Referer", siteUrl + "/");
        headers.put("Accept", "application/json, text/javascript, */*; q=0.01");
        headers.put("Accept-Language", "zh-CN,zh;q=0.9");
        return headers;
    }

    private HashMap<String, String> getHtmlHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        headers.put("Referer", siteUrl + "/");
        headers.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
        headers.put("Accept-Language", "zh-CN,zh;q=0.9");
        return headers;
    }

    @Override
    public void init(Context context, String ext) {
        super.init(context, ext);
        if (!TextUtils.isEmpty(ext)) {
            siteUrl = ext.trim();
            if (siteUrl.endsWith("/")) {
                siteUrl = siteUrl.substring(0, siteUrl.length() - 1);
            }
        }
        testApi();
    }

    private void testApi() {
        try {
            String url = siteUrl + API_PATH + "?ac=list";
            String json = OkHttp.string(url, getHeaders());
            JSONObject data = new JSONObject(json);
            useApi = data.has("code") && data.getInt("code") == 1;
        } catch (Exception e) {
            useApi = false;
        }
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        return useApi ? homeContentApi(filter) : homeContentHtml(filter);
    }

    private String homeContentApi(boolean filter) throws Exception {
        String url = siteUrl + API_PATH + "?ac=list";
        String json = OkHttp.string(url, getHeaders());
        JSONObject data = new JSONObject(json);

        JSONObject result = new JSONObject();
        JSONArray classes = new JSONArray();
        JSONObject filters = new JSONObject();

        if (data.has("class") && !data.isNull("class")) {
            JSONArray classArray = data.getJSONArray("class");
            for (int i = 0; i < classArray.length(); i++) {
                JSONObject item = classArray.getJSONObject(i);
                JSONObject cls = new JSONObject();
                String typeId = item.optString("type_id", "");
                String typeName = item.optString("type_name", "");
                cls.put("type_id", typeId);
                cls.put("type_name", typeName);
                classes.put(cls);

                if (filter) {
                    filters.put(typeId, buildFilters());
                }
            }
        } else {
            classes = getDefaultClasses();
            if (filter) {
                for (int i = 0; i < classes.length(); i++) {
                    JSONObject cls = classes.getJSONObject(i);
                    filters.put(cls.getString("type_id"), buildFilters());
                }
            }
        }

        result.put("class", classes);
        if (filter) {
            result.put("filters", filters);
        }

        String homeUrl = siteUrl + API_PATH + "?ac=detail&pg=1&pagesize=30";
        String homeJson = OkHttp.string(homeUrl, getHeaders());
        JSONObject homeData = new JSONObject(homeJson);
        if (homeData.has("list")) {
            result.put("list", homeData.getJSONArray("list"));
        }

        return result.toString();
    }

    private JSONArray buildFilters() {
        JSONArray filterArr = new JSONArray();

        // 年份
        JSONObject yearFilter = new JSONObject();
        yearFilter.put("key", "year");
        yearFilter.put("name", "年份");
        JSONArray yearValues = new JSONArray();
        String[] years = {"", "2025", "2024", "2023", "2022", "2021", "2020", "2019", "2018", "2017", "2016", "2015", "2014", "2013", "2012", "2011", "2010", "2009", "2008"};
        String[] yearNames = {"全部", "2025", "2024", "2023", "2022", "2021", "2020", "2019", "2018", "2017", "2016", "2015", "2014", "2013", "2012", "2011", "2010", "2009", "2008"};
        for (int i = 0; i < years.length; i++) {
            JSONObject y = new JSONObject();
            y.put("n", yearNames[i]);
            y.put("v", years[i]);
            yearValues.put(y);
        }
        yearFilter.put("value", yearValues);
        filterArr.put(yearFilter);

        // 地区
        JSONObject areaFilter = new JSONObject();
        areaFilter.put("key", "area");
        areaFilter.put("name", "地区");
        JSONArray areaValues = new JSONArray();
        String[][] areas = {{"全部", ""}, {"大陆", "大陆"}, {"香港", "香港"}, {"台湾", "台湾"}, {"美国", "美国"}, {"韩国", "韩国"}, {"日本", "日本"}, {"泰国", "泰国"}, {"英国", "英国"}, {"法国", "法国"}, {"德国", "德国"}, {"印度", "印度"}, {"其他", "其他"}};
        for (String[] area : areas) {
            JSONObject a = new JSONObject();
            a.put("n", area[0]);
            a.put("v", area[1]);
            areaValues.put(a);
        }
        areaFilter.put("value", areaValues);
        filterArr.put(areaFilter);

        // 语言
        JSONObject langFilter = new JSONObject();
        langFilter.put("key", "lang");
        langFilter.put("name", "语言");
        JSONArray langValues = new JSONArray();
        String[][] langs = {{"全部", ""}, {"国语", "国语"}, {"英语", "英语"}, {"粤语", "粤语"}, {"韩语", "韩语"}, {"日语", "日语"}, {"法语", "法语"}, {"德语", "德语"}, {"泰语", "泰语"}, {"其他", "其他"}};
        for (String[] lang : langs) {
            JSONObject l = new JSONObject();
            l.put("n", lang[0]);
            l.put("v", lang[1]);
            langValues.put(l);
        }
        langFilter.put("value", langValues);
        filterArr.put(langFilter);

        // 字母
        JSONObject letterFilter = new JSONObject();
        letterFilter.put("key", "letter");
        letterFilter.put("name", "字母");
        JSONArray letterValues = new JSONArray();
        String letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        JSONObject allLetter = new JSONObject();
        allLetter.put("n", "全部");
        allLetter.put("v", "");
        letterValues.put(allLetter);
        for (int i = 0; i < letters.length(); i++) {
            JSONObject l = new JSONObject();
            l.put("n", String.valueOf(letters.charAt(i)));
            l.put("v", String.valueOf(letters.charAt(i)));
            letterValues.put(l);
        }
        letterFilter.put("value", letterValues);
        filterArr.put(letterFilter);

        return filterArr;
    }

    private JSONArray getDefaultClasses() {
        JSONArray classes = new JSONArray();
        String[][] defaultTypes = {
            {"1", "电影"},
            {"2", "电视剧"},
            {"3", "综艺"},
            {"4", "动漫"},
            {"20", "4K电影"},
            {"21", "4K剧集"}
        };
        for (String[] type : defaultTypes) {
            JSONObject cls = new JSONObject();
            cls.put("type_id", type[0]);
            cls.put("type_name", type[1]);
            classes.put(cls);
        }
        return classes;
    }

    @Override
    public String homeVideoContent() throws Exception {
        return useApi ? homeVideoContentApi() : homeVideoContentHtml();
    }

    private String homeVideoContentApi() throws Exception {
        String url = siteUrl + API_PATH + "?ac=detail&pg=1&pagesize=30";
        String json = OkHttp.string(url, getHeaders());
        JSONObject data = new JSONObject(json);

        JSONObject result = new JSONObject();
        if (data.has("list")) {
            result.put("list", data.getJSONArray("list"));
        }
        return result.toString();
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        return useApi ? categoryContentApi(tid, pg, filter, extend) : categoryContentHtml(tid, pg, filter, extend);
    }

    private String categoryContentApi(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        StringBuilder urlBuilder = new StringBuilder(siteUrl + API_PATH + "?ac=list");
        urlBuilder.append("&t=").append(tid);
        urlBuilder.append("&pg=").append(pg);

        if (extend != null && !extend.isEmpty()) {
            Iterator<String> keys = extend.keySet().iterator();
            while (keys.hasNext()) {
                String key = keys.next();
                String value = extend.get(key);
                if (!TextUtils.isEmpty(value)) {
                    urlBuilder.append("&").append(key).append("=").append(URLEncoder.encode(value, "UTF-8"));
                }
            }
        }

        String url = urlBuilder.toString();
        String json = OkHttp.string(url, getHeaders());
        JSONObject data = new JSONObject(json);

        JSONObject result = new JSONObject();
        if (data.has("page")) result.put("page", data.getInt("page"));
        if (data.has("pagecount")) result.put("pagecount", data.getInt("pagecount"));
        if (data.has("limit")) result.put("limit", data.getInt("limit"));
        if (data.has("total")) result.put("total", data.getInt("total"));
        if (data.has("list")) result.put("list", data.getJSONArray("list"));

        return result.toString();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        if (ids == null || ids.isEmpty()) return "";
        return useApi ? detailContentApi(ids) : detailContentHtml(ids);
    }

    private String detailContentApi(List<String> ids) throws Exception {
        String id = ids.get(0);
        String url = siteUrl + API_PATH + "?ac=detail&ids=" + id;
        String json = OkHttp.string(url, getHeaders());
        JSONObject data = new JSONObject(json);

        JSONObject result = new JSONObject();
        JSONArray list = new JSONArray();

        if (data.has("list") && data.getJSONArray("list").length() > 0) {
            JSONArray videos = data.getJSONArray("list");
            for (int i = 0; i < videos.length(); i++) {
                JSONObject vod = videos.getJSONObject(i);
                JSONObject item = parseVodDetail(vod);
                list.put(item);
            }
        }

        result.put("list", list);
        return result.toString();
    }

    private JSONObject parseVodDetail(JSONObject vod) throws JSONException {
        JSONObject item = new JSONObject();

        item.put("vod_id", vod.optString("vod_id", ""));
        item.put("vod_name", vod.optString("vod_name", ""));
        item.put("vod_pic", vod.optString("vod_pic", ""));
        item.put("type_name", vod.optString("type_name", ""));
        item.put("vod_year", vod.optString("vod_year", ""));
        item.put("vod_area", vod.optString("vod_area", ""));
        item.put("vod_remarks", vod.optString("vod_remarks", ""));
        item.put("vod_actor", vod.optString("vod_actor", ""));
        item.put("vod_director", vod.optString("vod_director", ""));
        item.put("vod_content", vod.optString("vod_content", vod.optString("vod_blurb", "")));
        item.put("vod_lang", vod.optString("vod_lang", ""));
        item.put("vod_time", vod.optString("vod_time", ""));
        item.put("vod_douban_score", vod.optString("vod_douban_score", ""));

        String playFrom = vod.optString("vod_play_from", "");
        String playUrl = vod.optString("vod_play_url", "");

        if (!TextUtils.isEmpty(playFrom) && !TextUtils.isEmpty(playUrl)) {
            String[] froms = playFrom.split("\$\$\$");
            String[] urls = playUrl.split("\$\$\$");

            JSONArray playFlags = new JSONArray();
            JSONArray playUrls = new JSONArray();

            for (int j = 0; j < froms.length && j < urls.length; j++) {
                playFlags.put(froms[j].trim());

                StringBuilder urlBuilder = new StringBuilder();
                String[] episodes = urls[j].split("#");
                for (int k = 0; k < episodes.length; k++) {
                    String[] ep = episodes[k].split("\$");
                    if (ep.length >= 2) {
                        if (k > 0) urlBuilder.append("#");
                        urlBuilder.append(ep[0].trim()).append("$").append(ep[1].trim());
                    } else if (ep.length == 1 && !ep[0].trim().isEmpty()) {
                        if (k > 0) urlBuilder.append("#");
                        urlBuilder.append("第").append(k + 1).append("集").append("$").append(ep[0].trim());
                    }
                }
                playUrls.put(urlBuilder.toString());
            }

            item.put("vod_play_from", playFlags);
            item.put("vod_play_url", playUrls);
        }

        return item;
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return useApi ? searchContentApi(key, quick) : searchContentHtml(key, quick);
    }

    private String searchContentApi(String key, boolean quick) throws Exception {
        String url = siteUrl + API_PATH + "?ac=detail&wd=" + URLEncoder.encode(key, "UTF-8") + "&pg=1";
        String json = OkHttp.string(url, getHeaders());
        JSONObject data = new JSONObject(json);

        JSONObject result = new JSONObject();
        if (data.has("list")) {
            result.put("list", data.getJSONArray("list"));
        }
        return result.toString();
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        JSONObject result = new JSONObject();

        if (id.startsWith("http") || id.startsWith("https")) {
            result.put("parse", 0);
            result.put("playUrl", "");
            result.put("url", id);
            result.put("header", new JSONObject().put("User-Agent", "Mozilla/5.0").toString());
        } else {
            result.put("parse", 1);
            result.put("playUrl", "");
            result.put("url", id);
            result.put("header", "");
        }

        return result.toString();
    }

    // ==================== HTML 解析模式 ====================

    private String homeContentHtml(boolean filter) throws Exception {
        String html = OkHttp.string(siteUrl, getHtmlHeaders());
        Document doc = Jsoup.parse(html);

        JSONObject result = new JSONObject();
        JSONArray classes = new JSONArray();

        Elements navItems = doc.select("ul.nav-menu li a, .nav li a, .menu li a, .navbar-nav li a");
        for (Element nav : navItems) {
            String href = nav.attr("href");
            String text = nav.text().trim();
            if (href.contains("/vodtype/") || href.contains("/vod/show/") || href.contains("/vod/type/")) {
                String typeId = extractTypeId(href);
                if (!typeId.isEmpty() && !text.isEmpty()) {
                    JSONObject cls = new JSONObject();
                    cls.put("type_id", typeId);
                    cls.put("type_name", text);
                    classes.put(cls);
                }
            }
        }

        if (classes.length() == 0) {
            classes = getDefaultClasses();
        }

        result.put("class", classes);

        JSONArray list = new JSONArray();
        Elements videoItems = doc.select(".video-item, .movie-item, .vod-item, .stui-vodlist__box, .module-item");
        for (Element item : videoItems) {
            JSONObject vod = parseHtmlVideoItem(item);
            if (vod != null) list.put(vod);
        }
        result.put("list", list);

        return result.toString();
    }

    private String homeVideoContentHtml() throws Exception {
        return homeContentHtml(false);
    }

    private String categoryContentHtml(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        String url = siteUrl + "/vodtype/" + tid + "-" + pg + ".html";
        String html = OkHttp.string(url, getHtmlHeaders());
        Document doc = Jsoup.parse(html);

        JSONObject result = new JSONObject();
        JSONArray list = new JSONArray();

        Elements videoItems = doc.select(".video-item, .movie-item, .vod-item, .stui-vodlist__box, .module-item");
        for (Element item : videoItems) {
            JSONObject vod = parseHtmlVideoItem(item);
            if (vod != null) list.put(vod);
        }

        int page = Integer.parseInt(pg);
        result.put("page", page);
        result.put("pagecount", page + 1);
        result.put("limit", videoItems.size());
        result.put("total", page * videoItems.size());
        result.put("list", list);

        return result.toString();
    }

    private String detailContentHtml(List<String> ids) throws Exception {
        String id = ids.get(0);
        String url = siteUrl + "/voddetail/" + id + ".html";
        String html = OkHttp.string(url, getHtmlHeaders());
        Document doc = Jsoup.parse(html);

        JSONObject result = new JSONObject();
        JSONArray list = new JSONArray();
        JSONObject item = new JSONObject();

        item.put("vod_id", id);
        Element titleEl = doc.select("h1.title, .vod-title, .movie-title").first();
        item.put("vod_name", titleEl != null ? titleEl.text() : "");
        Element picEl = doc.select(".vod-pic img, .movie-pic img, .thumb img").first();
        item.put("vod_pic", picEl != null ? picEl.attr("src") : "");
        Element descEl = doc.select(".vod-content, .movie-desc, .desc").first();
        item.put("vod_content", descEl != null ? descEl.text() : "");

        Elements playTabs = doc.select(".play-tab, .nav-tabs li, .source-item");
        Elements playLists = doc.select(".play-list, .tab-pane, .playlist");

        JSONArray playFlags = new JSONArray();
        JSONArray playUrls = new JSONArray();

        for (int i = 0; i < playTabs.size() && i < playLists.size(); i++) {
            playFlags.put(playTabs.get(i).text().trim());

            StringBuilder urlBuilder = new StringBuilder();
            Elements episodes = playLists.get(i).select("a");
            for (int j = 0; j < episodes.size(); j++) {
                Element ep = episodes.get(j);
                if (j > 0) urlBuilder.append("#");
                String epName = ep.text().trim();
                String epUrl = ep.attr("href");
                if (!epUrl.startsWith("http")) {
                    epUrl = siteUrl + epUrl;
                }
                urlBuilder.append(epName).append("$").append(epUrl);
            }
            playUrls.put(urlBuilder.toString());
        }

        item.put("vod_play_from", playFlags);
        item.put("vod_play_url", playUrls);
        list.put(item);
        result.put("list", list);

        return result.toString();
    }

    private String searchContentHtml(String key, boolean quick) throws Exception {
        String url = siteUrl + "/vodsearch/" + URLEncoder.encode(key, "UTF-8") + "-------------.html";
        String html = OkHttp.string(url, getHtmlHeaders());
        Document doc = Jsoup.parse(html);

        JSONObject result = new JSONObject();
        JSONArray list = new JSONArray();

        Elements videoItems = doc.select(".video-item, .movie-item, .vod-item, .stui-vodlist__box, .module-item");
        for (Element item : videoItems) {
            JSONObject vod = parseHtmlVideoItem(item);
            if (vod != null) list.put(vod);
        }

        result.put("list", list);
        return result.toString();
    }

    private JSONObject parseHtmlVideoItem(Element item) throws JSONException {
        JSONObject vod = new JSONObject();

        Element link = item.select("a").first();
        if (link == null) return null;

        String href = link.attr("href");
        String title = link.attr("title");
        if (title.isEmpty()) title = link.text().trim();

        String id = extractId(href);
        if (id.isEmpty()) return null;

        vod.put("vod_id", id);
        vod.put("vod_name", title);

        Element img = item.select("img").first();
        if (img != null) {
            String pic = img.attr("data-original");
            if (pic.isEmpty()) pic = img.attr("src");
            vod.put("vod_pic", pic);
        }

        Element remark = item.select(".remark, .pic-text, .update, .status").first();
        if (remark != null) {
            vod.put("vod_remarks", remark.text().trim());
        }

        return vod;
    }

    private String extractTypeId(String href) {
        Pattern pattern = Pattern.compile("/vodtype/(\d+)");
        Matcher matcher = pattern.matcher(href);
        if (matcher.find()) return matcher.group(1);

        pattern = Pattern.compile("/vod/show/id/(\d+)");
        matcher = pattern.matcher(href);
        if (matcher.find()) return matcher.group(1);

        pattern = Pattern.compile("type/id/(\d+)");
        matcher = pattern.matcher(href);
        if (matcher.find()) return matcher.group(1);

        return "";
    }

    private String extractId(String href) {
        Pattern pattern = Pattern.compile("/voddetail/(\d+)");
        Matcher matcher = pattern.matcher(href);
        if (matcher.find()) return matcher.group(1);

        pattern = Pattern.compile("/vod/detail/id/(\d+)");
        matcher = pattern.matcher(href);
        if (matcher.find()) return matcher.group(1);

        return "";
    }
}
