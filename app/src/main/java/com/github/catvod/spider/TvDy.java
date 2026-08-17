package com.github.catvod.spider;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Filter;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URLEncoder;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import android.util.Base64;

public class TvDy extends Spider {

    private static final String siteUrl = "https://www.tvdy.xyz";
    private static final String cateUrl = siteUrl + "/vodshow/";
    private static final String detailUrl = siteUrl + "/voddetail/";
    private static final String searchUrl = siteUrl + "/vodsearch/-------------.html?wd=";
    private static final String playUrl = siteUrl + "/vodplay/";

    private HashMap<String, String> getHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", Util.CHROME);
        headers.put("Referer", siteUrl + "/");
        return headers;
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Vod> list = new ArrayList<>();
        List<Class> classes = new ArrayList<>();
        String[] typeIdList = {"dianying", "dianshiju", "zongyi", "dongman", "tiyu"};
        String[] typeNameList = {"电影", "电视剧", "综艺", "动漫", "体育"};
        for (int i = 0; i < typeNameList.length; i++) {
            classes.add(new Class(typeIdList[i], typeNameList[i]));
        }
        Document doc = Jsoup.parse(OkHttp.string(siteUrl, getHeaders()));
        for (Element element : doc.select("ul.stui-vodlist li a.stui-vodlist__thumb")) {
            try {
                String pic = element.attr("data-original");
                String url = element.attr("href");
                String name = element.attr("title");
                if (!pic.startsWith("http")) {
                    pic = siteUrl + pic;
                }
                String id = url.split("/")[2].replace(".html", "");
                list.add(new Vod(id, name, pic));
            } catch (Exception e) {

            }
        }
        if (filter) {
            return Result.string(classes, list, getFilters());
        }
        return Result.string(classes, list);
    }

    private LinkedHashMap<String, List<Filter>> getFilters() {
        LinkedHashMap<String, List<Filter>> filters = new LinkedHashMap<>();
        List<Filter> items = new ArrayList<>();

        // 剧情
        items.add(new Filter("class", "剧情", Arrays.asList(
            new Filter.Value("全部", ""),
            new Filter.Value("动作", "动作"), new Filter.Value("爱情", "爱情"),
            new Filter.Value("喜剧", "喜剧"), new Filter.Value("科幻", "科幻"),
            new Filter.Value("悬疑", "悬疑"), new Filter.Value("惊悚", "惊悚"),
            new Filter.Value("恐怖", "恐怖"), new Filter.Value("犯罪", "犯罪"),
            new Filter.Value("剧情", "剧情"), new Filter.Value("战争", "战争"),
            new Filter.Value("西部", "西部"), new Filter.Value("奇幻", "奇幻"),
            new Filter.Value("冒险", "冒险"), new Filter.Value("灾难", "灾难"),
            new Filter.Value("武侠", "武侠"), new Filter.Value("同性", "同性"),
            new Filter.Value("音乐", "音乐"), new Filter.Value("歌舞", "歌舞"),
            new Filter.Value("传记", "传记"), new Filter.Value("历史", "历史")
        )));

        // 地区
        items.add(new Filter("area", "地区", Arrays.asList(
            new Filter.Value("全部", ""),
            new Filter.Value("大陆", "大陆"), new Filter.Value("香港", "香港"),
            new Filter.Value("台湾", "台湾"), new Filter.Value("美国", "美国"),
            new Filter.Value("法国", "法国"), new Filter.Value("英国", "英国"),
            new Filter.Value("日本", "日本"), new Filter.Value("韩国", "韩国"),
            new Filter.Value("泰国", "泰国"), new Filter.Value("德国", "德国"),
            new Filter.Value("丹麦", "丹麦"), new Filter.Value("印度", "印度"),
            new Filter.Value("意大利", "意大利"), new Filter.Value("西班牙", "西班牙"),
            new Filter.Value("菲律宾", "菲律宾")
        )));

        // 年份
        items.add(new Filter("year", "年份", Arrays.asList(
            new Filter.Value("全部", ""),
            new Filter.Value("2026", "2026"), new Filter.Value("2025", "2025"),
            new Filter.Value("2024", "2024"), new Filter.Value("2023", "2023"),
            new Filter.Value("2022", "2022"), new Filter.Value("2021", "2021"),
            new Filter.Value("2020", "2020"), new Filter.Value("2019", "2019"),
            new Filter.Value("2018", "2018"), new Filter.Value("2017", "2017"),
            new Filter.Value("2016", "2016"), new Filter.Value("2015", "2015"),
            new Filter.Value("2014", "2014"), new Filter.Value("2013", "2013")
        )));

        // 语言
        items.add(new Filter("lang", "语言", Arrays.asList(
            new Filter.Value("全部", ""),
            new Filter.Value("国语", "国语"), new Filter.Value("粤语", "粤语"),
            new Filter.Value("英语", "英语"), new Filter.Value("韩语", "韩语"),
            new Filter.Value("日语", "日语"), new Filter.Value("法语", "法语"),
            new Filter.Value("德语", "德语"), new Filter.Value("俄语", "俄语"),
            new Filter.Value("泰语", "泰语"), new Filter.Value("闽南语", "闽南语"),
            new Filter.Value("意大利语", "意大利语"), new Filter.Value("西班牙语", "西班牙语"),
            new Filter.Value("葡萄牙语", "葡萄牙语"), new Filter.Value("菲律宾语", "菲律宾语")
        )));

        // 排序
        items.add(new Filter("by", "排序", Arrays.asList(
            new Filter.Value("最新", "time"),
            new Filter.Value("人气", "hits"),
            new Filter.Value("推荐", "level"),
            new Filter.Value("评分", "score")
        )));

        for (String tid : new String[]{"dianying", "dianshiju", "zongyi", "dongman", "tiyu"}) {
            filters.put(tid, items);
        }
        return filters;
    }

    private String buildCateUrl(String tid, String pg, HashMap<String, String> extend) {
        String area = extend.containsKey("area") ? extend.get("area") : "";
        String classType = extend.containsKey("class") ? extend.get("class") : "";
        String lang = extend.containsKey("lang") ? extend.get("lang") : "";
        String year = extend.containsKey("year") ? extend.get("year") : "";
        String order = extend.containsKey("by") ? extend.get("by") : "";

        StringBuilder sb = new StringBuilder();
        sb.append(cateUrl).append(tid);

        // d0 + area
        sb.append("-");
        if (!area.isEmpty()) sb.append(area);

        // d1 + order
        sb.append("-");
        if (!order.isEmpty()) sb.append(order);

        // d2 + class
        sb.append("-");
        if (!classType.isEmpty()) sb.append(classType);

        // d3 + lang
        sb.append("-");
        if (!lang.isEmpty()) sb.append(lang);

        // d4-d7 = 4 dashes
        sb.append("----");

        // d8-d10: page or dashes
        if (!"1".equals(pg)) {
            sb.append(pg).append("---");
        } else {
            sb.append("---");
        }

        // year at the end
        if (!year.isEmpty()) sb.append(year);

        sb.append(".html");
        return sb.toString();
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        List<Vod> list = new ArrayList<>();
        String target = buildCateUrl(tid, pg, extend);
        Document doc = Jsoup.parse(OkHttp.string(target, getHeaders()));
        for (Element element : doc.select("ul.stui-vodlist li a.stui-vodlist__thumb")) {
            try {
                String pic = element.attr("data-original");
                String url = element.attr("href");
                String name = element.attr("title");
                if (!pic.startsWith("http")) {
                    pic = siteUrl + pic;
                }
                String id = url.split("/")[2].replace(".html", "");
                list.add(new Vod(id, name, pic));
            } catch (Exception e) {

            }
        }
        Integer total = (Integer.parseInt(pg) + 1) * 20;
        return Result.string(Integer.parseInt(pg), Integer.parseInt(pg) + 1, 20, total, list);
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        Document doc = Jsoup.parse(OkHttp.string(detailUrl.concat(ids.get(0)).concat(".html"), getHeaders()));
        String name = doc.select("h1.title").text();
        name = name.replaceAll("（\\d{4}）", "").replaceAll("\\(\\d{4}\\)", "").trim();
        String pic = doc.select("a.pic img").attr("data-original");
        
        String year = "";
        Matcher yearMatcher = Pattern.compile("（(\\d{4})）").matcher(doc.select("h1.title").html());
        if (yearMatcher.find()) {
            year = yearMatcher.group(1);
        } else {
            yearMatcher = Pattern.compile("\\((\\d{4})\\)").matcher(doc.select("h1.title").html());
            if (yearMatcher.find()) {
                year = yearMatcher.group(1);
            }
        }
        
        String desc = doc.select("span.detail-content").text();

        // 播放源
        Elements tabs = doc.select("ul.tab-top.play-tab li a");
        Elements playLists = doc.select("div.play-content div.play-item.cont ul.stui-play__list");
        String PlayFrom = "";
        String PlayUrl = "";
        for (int i = 0; i < tabs.size() && i < playLists.size(); i++) {
            String tabName = tabs.get(i).text();
            if (!"".equals(PlayFrom)) {
                PlayFrom = PlayFrom + "$$$" + tabName;
            } else {
                PlayFrom = PlayFrom + tabName;
            }
            Elements li = playLists.get(i).select("li a");
            String liUrl = "";
            for (int i1 = 0; i1 < li.size(); i1++) {
                String episodeUrl = li.get(i1).attr("href").replace("/vodplay/", "").replace(".html", "");
                if (!"".equals(liUrl)) {
                    liUrl = liUrl + "#" + li.get(i1).text() + "$" + episodeUrl;
                } else {
                    liUrl = liUrl + li.get(i1).text() + "$" + episodeUrl;
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
        if (!pic.startsWith("http")) {
            pic = siteUrl + pic;
        }
        vod.setVodPic(pic);
        vod.setVodYear(year);
        vod.setVodName(name);
        vod.setVodContent(desc);
        vod.setVodPlayFrom(PlayFrom);
        vod.setVodPlayUrl(PlayUrl);
        return Result.string(vod);
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        List<Vod> list = new ArrayList<>();
        Document doc = Jsoup.parse(OkHttp.string(searchUrl.concat(URLEncoder.encode(key, "UTF-8")), getHeaders()));
        for (Element element : doc.select("ul.stui-vodlist li a.stui-vodlist__thumb")) {
            try {
                String pic = element.attr("data-original");
                String url = element.attr("href");
                String name = element.attr("title");
                if (!pic.startsWith("http")) {
                    pic = siteUrl + pic;
                }
                String id = url.split("/")[2].replace(".html", "");
                list.add(new Vod(id, name, pic));
            } catch (Exception e) {

            }
        }
        return Result.string(list);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        Document doc = Jsoup.parse(OkHttp.string(playUrl.concat(id).concat(".html"), getHeaders()));
        String regex = "var player_aaaa=.*?\"url\":\"([^\"]+)\"";
        Pattern pattern = Pattern.compile(regex, Pattern.DOTALL);
        Matcher matcher = pattern.matcher(doc.html());
        String url = "";
        if (matcher.find()) {
            String encoded = matcher.group(1);
            url = URLDecoder.decode(new String(Base64.decode(encoded, Base64.DEFAULT)), "UTF-8");
        }
        return Result.get().url(url).header(getHeaders()).string();
    }
}
