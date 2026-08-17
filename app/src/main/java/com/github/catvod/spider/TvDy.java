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
import java.util.HashMap;
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
            HashMap<String, String> extend = new HashMap<>();
            extend.put("class", "");
            extend.put("area", "");
            extend.put("year", "");
            extend.put("lang", "");
            extend.put("by", "");
            return Result.string(classes, list, getFilter());
        }
        return Result.string(classes, list);
    }

    private HashMap<String, String> getFilter() {
        HashMap<String, String> filter = new HashMap<>();
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        // 剧情
        sb.append("{\"key\":\"class\",\"name\":\"剧情\",\"value\":[");
        sb.append("{\"n\":\"全部\",\"v\":\"\"},");
        sb.append("{\"n\":\"动作\",\"v\":\"动作\"},{\"n\":\"爱情\",\"v\":\"爱情\"},{\"n\":\"喜剧\",\"v\":\"喜剧\"},");
        sb.append("{\"n\":\"科幻\",\"v\":\"科幻\"},{\"n\":\"悬疑\",\"v\":\"悬疑\"},{\"n\":\"惊悚\",\"v\":\"惊悚\"},");
        sb.append("{\"n\":\"恐怖\",\"v\":\"恐怖\"},{\"n\":\"犯罪\",\"v\":\"犯罪\"},{\"n\":\"剧情\",\"v\":\"剧情\"},");
        sb.append("{\"n\":\"战争\",\"v\":\"战争\"},{\"n\":\"西部\",\"v\":\"西部\"},{\"n\":\"奇幻\",\"v\":\"奇幻\"},");
        sb.append("{\"n\":\"冒险\",\"v\":\"冒险\"},{\"n\":\"灾难\",\"v\":\"灾难\"},{\"n\":\"武侠\",\"v\":\"武侠\"},");
        sb.append("{\"n\":\"同性\",\"v\":\"同性\"},{\"n\":\"音乐\",\"v\":\"音乐\"},{\"n\":\"歌舞\",\"v\":\"歌舞\"},");
        sb.append("{\"n\":\"传记\",\"v\":\"传记\"},{\"n\":\"历史\",\"v\":\"历史\"}");
        sb.append("]},");
        // 地区
        sb.append("{\"key\":\"area\",\"name\":\"地区\",\"value\":[");
        sb.append("{\"n\":\"全部\",\"v\":\"\"},");
        sb.append("{\"n\":\"大陆\",\"v\":\"大陆\"},{\"n\":\"香港\",\"v\":\"香港\"},{\"n\":\"台湾\",\"v\":\"台湾\"},");
        sb.append("{\"n\":\"美国\",\"v\":\"美国\"},{\"n\":\"法国\",\"v\":\"法国\"},{\"n\":\"英国\",\"v\":\"英国\"},");
        sb.append("{\"n\":\"日本\",\"v\":\"日本\"},{\"n\":\"韩国\",\"v\":\"韩国\"},{\"n\":\"泰国\",\"v\":\"泰国\"},");
        sb.append("{\"n\":\"德国\",\"v\":\"德国\"},{\"n\":\"丹麦\",\"v\":\"丹麦\"},{\"n\":\"印度\",\"v\":\"印度\"},");
        sb.append("{\"n\":\"意大利\",\"v\":\"意大利\"},{\"n\":\"西班牙\",\"v\":\"西班牙\"},{\"n\":\"菲律宾\",\"v\":\"菲律宾\"}");
        sb.append("]},");
        // 年份
        sb.append("{\"key\":\"year\",\"name\":\"年份\",\"value\":[");
        sb.append("{\"n\":\"全部\",\"v\":\"\"},");
        sb.append("{\"n\":\"2026\",\"v\":\"2026\"},{\"n\":\"2025\",\"v\":\"2025\"},{\"n\":\"2024\",\"v\":\"2024\"},");
        sb.append("{\"n\":\"2023\",\"v\":\"2023\"},{\"n\":\"2022\",\"v\":\"2022\"},{\"n\":\"2021\",\"v\":\"2021\"},");
        sb.append("{\"n\":\"2020\",\"v\":\"2020\"},{\"n\":\"2019\",\"v\":\"2019\"},{\"n\":\"2018\",\"v\":\"2018\"},");
        sb.append("{\"n\":\"2017\",\"v\":\"2017\"},{\"n\":\"2016\",\"v\":\"2016\"},{\"n\":\"2015\",\"v\":\"2015\"},");
        sb.append("{\"n\":\"2014\",\"v\":\"2014\"},{\"n\":\"2013\",\"v\":\"2013\"}");
        sb.append("]},");
        // 语言
        sb.append("{\"key\":\"lang\",\"name\":\"语言\",\"value\":[");
        sb.append("{\"n\":\"全部\",\"v\":\"\"},");
        sb.append("{\"n\":\"国语\",\"v\":\"国语\"},{\"n\":\"粤语\",\"v\":\"粤语\"},{\"n\":\"英语\",\"v\":\"英语\"},");
        sb.append("{\"n\":\"韩语\",\"v\":\"韩语\"},{\"n\":\"日语\",\"v\":\"日语\"},{\"n\":\"法语\",\"v\":\"法语\"},");
        sb.append("{\"n\":\"德语\",\"v\":\"德语\"},{\"n\":\"俄语\",\"v\":\"俄语\"},{\"n\":\"泰语\",\"v\":\"泰语\"},");
        sb.append("{\"n\":\"闽南语\",\"v\":\"闽南语\"},{\"n\":\"意大利语\",\"v\":\"意大利语\"},{\"n\":\"西班牙语\",\"v\":\"西班牙语\"},");
        sb.append("{\"n\":\"葡萄牙语\",\"v\":\"葡萄牙语\"},{\"n\":\"菲律宾语\",\"v\":\"菲律宾语\"}");
        sb.append("]},");
        // 排序
        sb.append("{\"key\":\"by\",\"name\":\"排序\",\"value\":[");
        sb.append("{\"n\":\"最新\",\"v\":\"time\"},{\"n\":\"人气\",\"v\":\"hits\"},{\"n\":\"推荐\",\"v\":\"level\"},{\"n\":\"评分\",\"v\":\"score\"}");
        sb.append("]}");
        sb.append("]");
        String filterJson = sb.toString();
        for (String tid : new String[]{"dianying", "dianshiju", "zongyi", "dongman", "tiyu"}) {
            filter.put(tid, filterJson);
        }
        return filter;
    }

    private String buildCateUrl(String tid, String pg, HashMap<String, String> extend) {
        String area = extend.containsKey("area") ? extend.get("area") : "";
        String classType = extend.containsKey("class") ? extend.get("class") : "";
        String lang = extend.containsKey("lang") ? extend.get("lang") : "";
        String year = extend.containsKey("year") ? extend.get("year") : "";
        String order = extend.containsKey("by") ? extend.get("by") : "";
        String page = pg;

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
        if (!"1".equals(page)) {
            sb.append(page).append("---");
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
