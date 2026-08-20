package com.github.catvod.spider;

import android.content.Context;
import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Json;
import com.github.catvod.utils.Util;
import com.google.gson.JsonElement;
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

public class Libvio extends Cloud {

    private static String siteUrl = "https://www.libvio.to/";
    private static final String MOBILE_UA = "Mozilla/5.0 (Linux; Android 11; M2007J3SC Build/RKQ1.200826.002; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/77.0.3865.120 MQQBrowser/6.2 TBS/045714 Mobile Safari/537.36";

    private HashMap<String, String> getHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", MOBILE_UA);
        return headers;
    }

    private HashMap<String, String> getHeaders(String refer) {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", MOBILE_UA);
        headers.put("Referer", refer);
        return headers;
    }

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
        try {
            JsonElement json = Json.parse(extend);
            if (json != null && json.isJsonObject() && json.getAsJsonObject().has("site")) {
                String site = json.getAsJsonObject().get("site").getAsString();
                String html = OkHttp.string(site, getHeaders());
                Document doc = Jsoup.parse(html);
                boolean found = false;
                for (Element element : doc.select("a[href]")) {
                    String text = element.text();
                    String href = element.attr("href");
                    if ((text.contains("可用") || text.contains("直达") || text.contains("入口")
                            || text.contains("最新") || text.contains("进入") || text.contains("NEW"))
                            && href.startsWith("http")) {
                        siteUrl = href;
                        found = true;
                        break;
                    }
                }
                if (!found) siteUrl = site;
            }
        } catch (Exception e) {
            SpiderDebug.log("libvio init error: " + e.getMessage());
        }
        if (!siteUrl.endsWith("/")) siteUrl += "/";
        SpiderDebug.log("libvio当前地址 =====> " + siteUrl);
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Vod> list = new ArrayList<>();
        List<Class> classes = new ArrayList<>();

        Document doc = Jsoup.parse(OkHttp.string(siteUrl, getHeaders()));

        Elements menuItems = doc.select("ul.stui-header__menu > li > a");
        if (menuItems.isEmpty()) {
            menuItems = doc.select(".stui-header__menu a, .navbar-nav a, .nav-menu a, .nav-item a, header li a");
        }
        for (Element element : menuItems) {
            String href = element.attr("href").replace(".html", "");
            String text = element.text().trim();
            if (text.isEmpty()) continue;
            if (text.equals("主页") || text.equals("首页")) continue;
            if (href.contains("type") || href.contains("show") || href.startsWith("/")) {
                classes.add(new Class(href, text));
            }
        }

        Elements items = doc.select("ul.stui-vodlist > li > div > a");
        if (items.isEmpty()) {
            items = doc.select(".stui-vodlist__box a, .stui-vodlist li a[title], .video-item a, .module-item a, .vodlist-item a");
        }
        for (Element element : items) {
            String pic = element.attr("data-original");
            if (pic.isEmpty()) pic = element.attr("src");
            String url = element.attr("href");
            String name = element.attr("title");
            if (name.isEmpty()) name = element.text().trim();
            if (!pic.startsWith("http") && !pic.isEmpty()) {
                pic = siteUrl + pic;
            }
            String id = extractId(url);
            if (!id.isEmpty()) {
                list.add(new Vod(id, name, pic));
            }
        }
        return Result.string(classes, list);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        List<Vod> list = new ArrayList<>();

        String target;
        if (tid.contains("show")) {
            target = siteUrl + tid.replaceFirst("^/", "").replace("-----------", "-" + pg + "-----------");
        } else {
            target = siteUrl + tid.replaceFirst("^/", "") + "-" + pg + ".html";
        }

        Document doc = Jsoup.parse(OkHttp.string(target, getHeaders()));

        Elements items = doc.select("ul.stui-vodlist > li > div > a");
        if (items.isEmpty()) {
            items = doc.select(".stui-vodlist__box a, .stui-vodlist li a[title], .video-item a, .module-item a, .vodlist-item a");
        }
        for (Element element : items) {
            String pic = element.attr("data-original");
            if (pic.isEmpty()) pic = element.attr("src");
            String url = element.attr("href");
            String name = element.attr("title");
            if (name.isEmpty()) name = element.text().trim();
            if (!pic.startsWith("http") && !pic.isEmpty()) {
                pic = siteUrl + pic;
            }
            String id = extractId(url);
            if (!id.isEmpty()) {
                list.add(new Vod(id, name, pic));
            }
        }

        int total = Integer.parseInt(pg) * 20;
        Elements pages = doc.select(".stui-page__item a, .pagination a, .page-link");
        for (Element p : pages) {
            String text = p.text();
            if (text.contains("尾页") || text.contains("末页") || text.contains("Last")) {
                String href = p.attr("href");
                Matcher m = Pattern.compile("-(\\d+)\\.html").matcher(href);
                if (m.find()) {
                    total = Integer.parseInt(m.group(1)) * 20;
                }
                break;
            }
        }

        return Result.string(Integer.parseInt(pg), Integer.parseInt(pg) + 1, 20, total, list);
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String detailUrl = siteUrl + "detail/" + ids.get(0) + ".html";
        Document doc = Jsoup.parse(OkHttp.string(detailUrl, getHeaders()));

        String name = "";
        Elements nameEls = doc.select(".stui-content__detail h1, .detail-info h1, h1.title, .movie-title, .video-title, .detail-title h1");
        if (!nameEls.isEmpty()) name = nameEls.first().text().trim();

        String pic = "";
        Elements picEls = doc.select(".stui-content__thumb img, .detail-pic img, .thumb img, .poster img, .detail-poster img");
        if (!picEls.isEmpty()) {
            pic = picEls.first().attr("data-original");
            if (pic.isEmpty()) pic = picEls.first().attr("src");
        }

        Elements tabs = doc.select(".stui-vodlist__head h3, .play-source h3, .tab-item, .source-title, .playlist-title, .play-tab h3, .tab-nav h3, .nav-tabs li");
        Elements playLists = doc.select(".stui-vodlist__head ul.stui-content__playlist, .play-list ul, .playlist ul, .stui-content__playlist, .tab-content .playlist, .play-box ul");

        Vod.VodPlayBuilder builder = new Vod.VodPlayBuilder();
        List<String> quarkList = new ArrayList<>();

        for (int i = 0; i < tabs.size(); i++) {
            List<Vod.VodPlayBuilder.PlayUrl> playUrls = new ArrayList<>();
            String tabName = tabs.get(i).text().trim();

            Elements li = new Elements();
            if (i < playLists.size()) {
                li = playLists.get(i).select("a");
            }

            for (Element element : li) {
                String href = element.attr("href");
                if (tabName.contains("夸克") || tabName.contains("UC") || tabName.contains("网盘") || tabName.contains("云盘")) {
                    quarkList.add(href);
                } else {
                    Vod.VodPlayBuilder.PlayUrl playUrl = new Vod.VodPlayBuilder.PlayUrl();
                    playUrl.name = element.text().trim();
                    playUrl.url = href.replace("/play/", "").replace(".html", "");
                    playUrls.add(playUrl);
                }
            }
            if (!tabName.contains("夸克") && !tabName.contains("UC") && !tabName.contains("网盘") && !tabName.contains("云盘")) {
                builder.append(tabName, playUrls);
            }
        }

        if (tabs.isEmpty() || builder.build().vodPlayFrom.isEmpty()) {
            Elements allPlaylists = doc.select("ul.stui-content__playlist, .playlist ul, .play-list");
            Elements allTabs = doc.select(".stui-vodlist__head h3, .tab-item, .source-title");
            if (allTabs.isEmpty() && !allPlaylists.isEmpty()) {
                List<Vod.VodPlayBuilder.PlayUrl> playUrls = new ArrayList<>();
                for (Element element : allPlaylists.first().select("a")) {
                    Vod.VodPlayBuilder.PlayUrl playUrl = new Vod.VodPlayBuilder.PlayUrl();
                    playUrl.name = element.text().trim();
                    playUrl.url = element.attr("href").replace("/play/", "").replace(".html", "");
                    playUrls.add(playUrl);
                }
                builder.append("默认", playUrls);
            }
        }

        String quarkNames = "";
        String quarkUrls = "";
        if (!quarkList.isEmpty()) {
            List<String> shareLinks = new ArrayList<>();
            for (String s : quarkList) {
                String pageUrl = siteUrl + s.replaceFirst("^/", "");
                Document detailPageDoc = Jsoup.parse(OkHttp.string(pageUrl, getHeaders()));
                Matcher matcher = Pattern.compile("player_aaaa=(.*?)</script>").matcher(detailPageDoc.html());
                String json = matcher.find() ? matcher.group(1) : "";
                if (json.isEmpty()) {
                    matcher = Pattern.compile("player_(\\w+)=(\\{.*?\\})</script>").matcher(detailPageDoc.html());
                    if (matcher.find()) json = matcher.group(2);
                }
                if (!json.isEmpty()) {
                    JSONObject player = new JSONObject(json);
                    String url = player.optString("url", "");
                    if (!url.isEmpty()) shareLinks.add(url);
                }
            }
            if (!shareLinks.isEmpty()) {
                quarkUrls = super.detailContentVodPlayUrl(shareLinks);
                quarkNames = super.detailContentVodPlayFrom(shareLinks);
            }
        }

        Vod.VodPlayBuilder.BuildResult result = builder.build();

        Vod vod = new Vod();
        vod.setVodId(ids.get(0));
        vod.setVodPic(pic.startsWith("http") ? pic : siteUrl + pic);
        vod.setVodName(name);

        String playFrom = result.vodPlayFrom;
        String playUrl = result.vodPlayUrl;
        if (!quarkNames.isEmpty()) {
            playFrom = playFrom.isEmpty() ? quarkNames : playFrom + "$$$" + quarkNames;
            playUrl = playUrl.isEmpty() ? quarkUrls : playUrl + "$$$" + quarkUrls;
        }
        vod.setVodPlayFrom(playFrom);
        vod.setVodPlayUrl(playUrl);

        return Result.string(vod);
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        List<Vod> list = new ArrayList<>();
        String searchUrl = siteUrl + "search/-------------.html?wd=" + URLEncoder.encode(key);
        Document doc = Jsoup.parse(OkHttp.string(searchUrl, getHeaders()));

        Elements items = doc.select("ul.stui-vodlist > li > div > a");
        if (items.isEmpty()) {
            items = doc.select(".stui-vodlist__box a, .stui-vodlist li a[title], .video-item a, .search-result a, .module-item a, .vodlist-item a");
        }
        for (Element element : items) {
            String pic = element.attr("data-original");
            if (pic.isEmpty()) pic = element.attr("src");
            String url = element.attr("href");
            String name = element.attr("title");
            if (name.isEmpty()) name = element.text().trim();
            if (!pic.startsWith("http") && !pic.isEmpty()) {
                pic = siteUrl + pic;
            }
            String id = extractId(url);
            if (!id.isEmpty()) {
                list.add(new Vod(id, name, pic));
            }
        }
        return Result.string(list);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        if (flag.contains("quark") || flag.contains("夸克") || flag.contains("UC")) {
            return super.playerContent(flag, id, vipFlags);
        }
        String target = siteUrl + "play/" + id;
        Document doc = Jsoup.parse(OkHttp.string(target, getHeaders()));

        Matcher matcher = Pattern.compile("player_aaaa=(.*?)</script>").matcher(doc.html());
        String json = matcher.find() ? matcher.group(1) : "";
        if (json.isEmpty()) {
            matcher = Pattern.compile("player_(\\w+)=(\\{.*?\\})</script>").matcher(doc.html());
            if (matcher.find()) json = matcher.group(2);
        }

        if (json.isEmpty()) {
            return Result.get().url("").string();
        }

        JSONObject player = new JSONObject(json);
        String url = player.optString("url", "");
        String from = player.optString("from", "");
        String next = player.optString("link_next", "");
        String vodid = player.optString("id", "");
        String nid = player.optString("nid", "");

        if (url.isEmpty() || from.isEmpty()) {
            return Result.get().url("").string();
        }

        String paurl = OkHttp.string(siteUrl + "static/player/" + from + ".js");
        Matcher matcher1 = Pattern.compile("src=[\"'](.*?)[\"']").matcher(paurl);
        String src = matcher1.find() ? matcher1.group(1) : "";

        if (src.isEmpty()) {
            matcher1 = Pattern.compile("src=\"(.*?)\'").matcher(paurl);
            src = matcher1.find() ? matcher1.group(1) : "";
        }

        String purl = src + url + "&next=" + next + "&id=" + vodid + "&nid=" + nid;
        if (!purl.startsWith("http")) {
            purl = siteUrl.replace("www.", "") + purl;
        }

        String playUrl = OkHttp.string(purl, getHeaders(target.replace("www.", "")));
        String realUrl = Util.getVar(playUrl, "vid");

        return Result.get().url(realUrl).header(getHeaders()).string();
    }

    private String extractId(String url) {
        if (url == null || url.isEmpty()) return "";
        url = url.replace(".html", "");
        String[] parts = url.split("/");
        if (parts.length > 0) {
            String last = parts[parts.length - 1];
            if (last.contains("?")) {
                last = last.substring(0, last.indexOf("?"));
            }
            if (!last.isEmpty()) return last;
        }
        return "";
    }
}
