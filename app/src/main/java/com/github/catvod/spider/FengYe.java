package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;

import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FengYe extends Spider {
    private static final String siteUrl = "http://www.tjtcdl.com";
    private static final String siteHost = "www.tjtcdl.com";

    private JSONObject playerConfig;
    private JSONObject filterConfig;

    // 旧正则失效，页面链接统一为 /chabeihu/数字.html
    private Pattern regexCategory = Pattern.compile("/(\\w+)-\\d+\\.html");
    private Pattern regexVid = Pattern.compile("/chabeihu/(\\d+)\\.html");
    private Pattern regexPlay = Pattern.compile("\\S+/play/(\\w+)-(\\d+)-(\\d+)\\.html");
    private Pattern regexPage = Pattern.compile("/(cupfox-list/\\S+)-\\d+\\.html");

    @Override
    public void init(Context context) throws Exception {
        super.init(context);
        try {
            playerConfig = new JSONObject("{\"dplayer\":{\"sh\":\"dplayer\",\"pu\":\"\",\"sn\":0,\"or\":999},\"videojs\":{\"sh\":\"videojs-H5播放器\",\"pu\":\"\",\"sn\":0,\"or\":999},\"iva\":{\"sh\":\"iva-H5播放器\",\"pu\":\"\",\"sn\":0,\"or\":999},\"iframe\":{\"sh\":\"iframe外链数据\",\"pu\":\"\",\"sn\":0,\"or\":999},\"link\":{\"sh\":\"外链数据\",\"pu\":\"\",\"sn\":0,\"or\":999},\"flv\":{\"sh\":\"Flv文件\",\"pu\":\"\",\"sn\":0,\"or\":999},\"ckm3u8\":{\"sh\":\"爱迪影视\",\"pu\":\"\",\"sn\":0,\"or\":999},\"dbm3u8\":{\"sh\":\"720P线路\",\"pu\":\"\",\"sn\":0,\"or\":999},\"yjm3u8\":{\"sh\":\"备用线路\",\"pu\":\"\",\"sn\":0,\"or\":999}}");

            filterConfig = new JSONObject("{\"type1\":[{\"key\":0,\"name\":\"分类\",\"value\":[{\"n\":\"全部\",\"v\":\"type1\"},{\"n\":\"电影\",\"v\":\"type1\"}]},{\"key\":2,\"name\":\"排序\",\"value\":[{\"n\":\"时间\",\"v\":\"time\"},{\"n\":\"人气\",\"v\":\"hits\"},{\"n\":\"评分\",\"v\":\"score\"}]}],\"type2\":[{\"key\":0,\"name\":\"分类\",\"value\":[{\"n\":\"全部\",\"v\":\"type2\"},{\"n\":\"电视剧\",\"v\":\"type2\"}]},{\"key\":2,\"name\":\"排序\",\"value\":[{\"n\":\"时间\",\"v\":\"time\"},{\"n\":\"人气\",\"v\":\"hits\"},{\"n\":\"评分\",\"v\":\"score\"}]}],\"type4\":[{\"key\":0,\"name\":\"分类\",\"value\":[{\"n\":\"全部\",\"v\":\"type4\"},{\"n\":\"动漫\",\"v\":\"type4\"}]},{\"key\":2,\"name\":\"排序\",\"value\":[{\"n\":\"时间\",\"v\":\"time\"},{\"n\":\"人气\",\"v\":\"hits\"},{\"n\":\"评分\",\"v\":\"score\"}]}],\"type3\":[{\"key\":0,\"name\":\"分类\",\"value\":[{\"n\":\"全部\",\"v\":\"type3\"},{\"n\":\"综艺\",\"v\":\"type3\"}]},{\"key\":2,\"name\":\"排序\",\"value\":[{\"n\":\"时间\",\"v\":\"time\"},{\"n\":\"人气\",\"v\":\"hits\"},{\"n\":\"评分\",\"v\":\"score\"}]}],\"type5\":[{\"key\":0,\"name\":\"分类\",\"value\":[{\"n\":\"全部\",\"v\":\"type5\"},{\"n\":\"短剧\",\"v\":\"type5\"}]},{\"key\":2,\"name\":\"排序\",\"value\":[{\"n\":\"时间\",\"v\":\"time\"},{\"n\":\"人气\",\"v\":\"hits\"},{\"n\":\"评分\",\"v\":\"score\"}]}]}");
        } catch (JSONException e) {
            SpiderDebug.log("init异常：" + e.getMessage());
            SpiderDebug.log(e);
        }
    }

    protected HashMap<String, String> getHeaders(String url) {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("method", "GET");
        headers.put("Host", siteHost);
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36");
        headers.put("Referer", siteUrl + "/");
        headers.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
        headers.put("Accept-Language", "zh-CN,zh;q=0.9");
        return headers;
    }

    @Override
    public String homeContent(boolean filter) {
        try {
            String html = OkHttp.string(siteUrl, getHeaders(siteUrl));
            SpiderDebug.log("首页返回html长度：" + html.length());
            Document doc = Jsoup.parse(html);

            JSONArray classes = new JSONArray();
            classes.put(getClassMap("qq", "腾讯VIP精选"));
            classes.put(getClassMap("yk", "优酷VIP精选"));
            classes.put(getClassMap("bli", "B站VIP精选"));
            classes.put(getClassMap("type2", "电视剧"));
            classes.put(getClassMap("cupfox-list/13-----------", "国产剧"));
            classes.put(getClassMap("cupfox-list/15-----------", "日韩剧"));
            classes.put(getClassMap("cupfox-list/16-----------", "海外剧"));
            classes.put(getClassMap("type1", "电影"));
            classes.put(getClassMap("type4", "动漫"));
            classes.put(getClassMap("type3", "综艺"));
            classes.put(getClassMap("type5", "短剧"));

            JSONObject result = new JSONObject();
            result.put("class", classes);

            if (filter) {
                result.put("filters", filterConfig);
            }

            JSONArray videos = new JSONArray();
            Elements items = doc.select(".public-list-box");
            SpiderDebug.log("首页匹配到卡片数量：" + items.size());

            for (Element item : items) {
                Element a = item.selectFirst("a.public-list-exp");
                Element img = item.selectFirst("img.gen-movie-img");
                if (a == null) continue;

                String href = a.attr("href");
                String title = a.attr("title");
                String cover = img != null ? img.attr("data-src") : "";
                String update = item.selectFirst(".public-list-prb") != null ? item.selectFirst(".public-list-prb").text() : "";
                String actor = item.selectFirst(".public-list-subtitle") != null ? item.selectFirst(".public-list-subtitle").text() : "";
                String remark = (update + " " + actor).trim();

                // 提取影片ID
                String id = "";
                Matcher matcher = regexVid.matcher(href);
                if (matcher.find()) {
                    id = matcher.group(1);
                }
                if (TextUtils.isEmpty(id)) continue;

                JSONObject v = new JSONObject();
                v.put("vod_id", id);
                v.put("vod_name", title);
                v.put("vod_pic", cover);
                v.put("vod_remarks", remark);
                videos.put(v);
            }
            SpiderDebug.log("首页最终有效影片数：" + videos.length());
            result.put("list", videos);
            return result.toString();
        } catch (Exception e) {
            SpiderDebug.log("homeContent异常：" + e.getMessage());
            SpiderDebug.log(e);
        }
        return "";
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        try {
            String url;
            // 【修复】腾讯/优酷/B站标签分类，拼接页码 /label/qq-pg.html
            if ("qq".equals(tid) || "yk".equals(tid) || "bli".equals(tid)) {
                url = siteUrl + "/label/" + tid + "-" + pg + ".html";
            }
            // 国产剧/日韩剧等cupfox长ID分页
            else if (tid.startsWith("cupfox-list/")) {
                url = siteUrl + "/" + tid + "-" + pg + ".html";
            }
            // 电影/电视剧/动漫/综艺/短剧 常规type分类
            else {
                url = siteUrl + "/" + tid + "-" + pg + ".html";
            }
            SpiderDebug.log("分类访问地址：" + url);
            String html = OkHttp.string(url, getHeaders(url));
            Document doc = Jsoup.parse(html);

            JSONObject result = new JSONObject();
            int pageCount = 1;
            int page = Integer.parseInt(pg);

            // 读取最大页码
            Elements pageLinks = doc.select(".page-link");
            for (Element p : pageLinks) {
                String num = p.text().trim();
                if (num.matches("\\d+")) {
                    int numInt = Integer.parseInt(num);
                    if (numInt > pageCount) {
                        pageCount = numInt;
                    }
                }
            }

            JSONArray videos = new JSONArray();
            if (!html.contains("没有找到您想要的结果哦")) {
                Elements items = doc.select(".public-list-box");
                SpiderDebug.log("分类页卡片数量：" + items.size());
                for (Element item : items) {
                    Element a = item.selectFirst("a.public-list-exp");
                    Element img = item.selectFirst("img.gen-movie-img");
                    if (a == null) continue;

                    String href = a.attr("href");
                    String title = a.attr("title");
                    String cover = img != null ? img.attr("data-src") : "";
                    String remark = item.selectFirst(".public-list-prb") != null ? item.selectFirst(".public-list-prb").text() : "";

                    String id = "";
                    Matcher matcher = regexVid.matcher(href);
                    if (matcher.find()) {
                        id = matcher.group(1);
                    }
                    if (TextUtils.isEmpty(id)) continue;

                    JSONObject v = new JSONObject();
                    v.put("vod_id", id);
                    v.put("vod_name", title);
                    v.put("vod_pic", cover);
                    v.put("vod_remarks", remark);
                    videos.put(v);
                }
            }

            result.put("page", page);
            result.put("pagecount", pageCount);
            result.put("limit", 60);
            result.put("total", pageCount <= 1 ? videos.length() : pageCount * 60);
            result.put("list", videos);
            return result.toString();
        } catch (Exception e) {
            SpiderDebug.log("categoryContent异常：" + e.getMessage());
            SpiderDebug.log(e);
        }
        return "";
    }

    @Override
    public String detailContent(List<String> ids) {
        try {
            String vodId = ids.get(0);
            // 修复详情页路径：原只传数字，拼接完整 /chabeihu/数字.html
            String url = siteUrl + "/chabeihu/" + vodId + ".html";
            Document doc = Jsoup.parse(OkHttp.string(url, getHeaders(url)));

            JSONObject result = new JSONObject();
            JSONObject vodList = new JSONObject();

            String pic = "";
            Element picBox = doc.selectFirst(".this-pic-bj");
            if (picBox != null) {
                String style = picBox.attr("style");
                if (style.contains("url(")) {
                    int start = style.indexOf("url(") + 4;
                    int end = style.indexOf(")", start);
                    if (start > 0 && end > start) {
                        pic = style.substring(start, end).replace("'", "").replace("\"", "").trim();
                    }
                }
            }

            String name = doc.selectFirst(".this-desc-title") != null ? doc.selectFirst(".this-desc-title").text().trim() : "未知影片";
            String score = doc.selectFirst(".fraction") != null ? doc.selectFirst(".fraction").text().trim() : "0.0";
            String infoText = doc.selectFirst(".this-desc-info") != null ? doc.selectFirst(".this-desc-info").text().trim() : "";
            String remark = score + "分 " + infoText;

            String director = "";
            String actor = "";
            Elements infoItems = doc.select(".this-info");
            for (Element item : infoItems) {
                String text = item.text().trim();
                if (text.startsWith("导演:")) {
                    director = text.replace("导演:", "").trim();
                }
                if (text.startsWith("演员:")) {
                    actor = text.replace("演员:", "").trim();
                }
            }

            Element descEl = doc.selectFirst("#height_limit.text");
            String desc = descEl != null ? descEl.text().replace("简介:", "").trim() : "暂无简介";

            vodList.put("vod_id", vodId);
            vodList.put("vod_name", name);
            vodList.put("vod_pic", pic);
            vodList.put("vod_remarks", remark);
            vodList.put("vod_actor", actor);
            vodList.put("vod_director", director);
            vodList.put("vod_content", desc);

            Map<String, String> vod_play = new TreeMap<>();
            Elements playItems = doc.select(".anthology-list-play li a");
            if (playItems.size() > 0) {
                List<String> vodItems = new ArrayList<>();
                for (Element a : playItems) {
                    String epName = a.text().trim();
                    String epPath = a.attr("href").trim();
                    if (!epName.isEmpty() && !epPath.isEmpty()) {
                        vodItems.add(epName + "$" + epPath);
                    }
                }
                if (vodItems.size() > 0) {
                    vod_play.put("自营t", TextUtils.join("#", vodItems));
                }
            }

            if (vod_play.size() > 0) {
                String vod_play_from = TextUtils.join("$$$", vod_play.keySet());
                String vod_play_url = TextUtils.join("$$$", vod_play.values());
                vodList.put("vod_play_from", vod_play_from);
                vodList.put("vod_play_url", vod_play_url);
            }

            JSONArray list = new JSONArray();
            list.put(vodList);
            result.put("list", list);
            return result.toString();
        } catch (Exception e) {
            SpiderDebug.log("detailContent异常：" + e.getMessage());
            SpiderDebug.log(e);
        }
        return "";
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        try {
            String url = siteUrl + "/play/" + id + ".html";
            Document doc = Jsoup.parse(OkHttp.string(url, getHeaders(url)));

            JSONObject result = new JSONObject();
            Elements allScript = doc.select("script");
            for (Element script : allScript) {
                String scContent = script.html().trim();
                if (scContent.startsWith("var player_")) {
                    int start = scContent.indexOf('{');
                    int end = scContent.lastIndexOf('}') + 1;
                    String json = scContent.substring(start, end);
                    JSONObject player = new JSONObject(json);
                    String videoUrl = player.getString("url");
                    result.put("parse", 0);
                    result.put("playUrl", "");
                    result.put("url", videoUrl);
                    result.put("header", "Referer:" + siteUrl + "/");
                    break;
                }
            }
            return result.toString();
        } catch (Exception e) {
            SpiderDebug.log("playerContent异常：" + e.getMessage());
            SpiderDebug.log(e);
        }
        return "";
    }

    @Override
    public String searchContent(String key, boolean quick) {
        try {
            if (quick) return "";
            String encodeKey = URLEncoder.encode(key, "UTF-8");
            String url = siteUrl + "/cupfox-search/-------------.html?wd=" + encodeKey + "&submit=";
            Document doc = Jsoup.parse(OkHttp.string(url, getHeaders(url)));

            JSONObject result = new JSONObject();
            JSONArray videos = new JSONArray();
            Elements items = doc.select(".public-list-box");
            SpiderDebug.log("搜索匹配卡片数：" + items.size());

            for (Element item : items) {
                Element a = item.selectFirst("a.public-list-exp");
                Element img = item.selectFirst("img.gen-movie-img");
                if (a == null) continue;

                String href = a.attr("href");
                String title = a.attr("title");
                String cover = img != null ? img.attr("data-src") : "";

                String id = "";
                Matcher matcher = regexVid.matcher(href);
                if (matcher.find()) {
                    id = matcher.group(1);
                }
                if (TextUtils.isEmpty(id)) continue;

                JSONObject v = new JSONObject();
                v.put("vod_id", id);
                v.put("vod_name", title);
                v.put("vod_pic", cover);
                videos.put(v);
            }
            result.put("list", videos);
            return result.toString();
        } catch (Exception e) {
            SpiderDebug.log("searchContent异常：" + e.getMessage());
            SpiderDebug.log(e);
        }
        return "";
    }

    private JSONObject getClassMap(String tid, String name) {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("type_id", tid);
            jsonObject.put("type_name", name);
        } catch (JSONException e) {
            SpiderDebug.log("getClassMap异常：" + e.getMessage());
        }
        return jsonObject;
    }
}
