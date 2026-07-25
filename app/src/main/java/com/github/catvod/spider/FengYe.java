package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;
import com.github.catvod.crawler.Spider;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 茶杯狐 cd-zj 专用爬虫 修复编译错误版本
 * 站点：https://www.cd-zj.com
 * 分类ID规则：qq/yk/bli=平台精选，1电影 2电视剧 4动漫 3综艺 5短剧
 */
public class FengYe extends Spider {
    // ======================【站点配置 cd-zj】======================
    private static final String DEFAULT_HOST = "https://www.cd-zj.com";
    private String HOST = DEFAULT_HOST; // 移除final，运行时可修改
    // 详情页正则 /detail/数字.html
    private static final Pattern VID_PAT = Pattern.compile("/detail/(\\d+)\\.html");
    // 分页page正则 pg=数字
    private static final Pattern PAGE_PAT = Pattern.compile("pg=(\\d+)");
    // 分集是否倒序
    private static final boolean REVERSE_EPISODE = true;
    // 分隔符
    private static final String SEP = "/";
    // ======================================================================

    private OkHttpClient client;

    @Override
    public void init(Context context, String extend) {
        // ext支持自定义站点域名
        if (!TextUtils.isEmpty(extend) && extend.startsWith("http")) {
            HOST = extend;
        }
        client = new OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .followRedirects(true)
                .followSslRedirects(true)
                .cookieJar(new CookieJar() {
                    private final Map<HttpUrl, List<Cookie>> cookieStore = new HashMap<>();
                    @Override
                    public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
                        cookieStore.put(url, new ArrayList<>(cookies));
                    }
                    @Override
                    public List<Cookie> loadForRequest(HttpUrl url) {
                        return cookieStore.getOrDefault(url, new ArrayList<>());
                    }
                })
                .build();
    }

    // 拼接绝对路径
    private String absUrl(String url) {
        if (TextUtils.isEmpty(url)) return "";
        if (url.startsWith("http")) return url;
        if (url.startsWith("/")) return HOST + url;
        return HOST + "/" + url;
    }

    private JSONObject getClassMap(String cid, String name) throws Exception {
        return new JSONObject().put("type_id", cid).put("type_name", name);
    }

    // 通用请求头
    private Headers getBaseHeader() {
        return new Headers.Builder()
                .add("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1")
                .add("Referer", HOST + "/")
                .add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .add("Accept-Language", "zh-CN,zh;q=0.9")
                .build();
    }

    private String get(String url) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .headers(getBaseHeader())
                .get()
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) return "";
            return response.body().string();
        }
    }

    /** 解析首页/分类影片卡片 .public-list-box */
    private JSONObject parseVodItem(Element item) throws Exception {
        Element link = item.selectFirst("a");
        if (link == null) return null;
        String href = link.attr("href");
        Matcher m = VID_PAT.matcher(href);
        if (!m.find()) return null;
        String vid = m.group(1);
        String title = link.attr("title").trim();

        // 封面图data-src优先
        Element img = link.selectFirst("img");
        String pic = "";
        if (img != null) {
            pic = img.hasAttr("data-src") ? img.attr("data-src") : img.attr("src");
        }
        pic = absUrl(pic);

        // 更新备注（集数）
        Element tip = item.selectFirst(".public-list-prb");
        String remark = tip != null ? tip.text().trim() : "";

        JSONObject data = new JSONObject();
        data.put("vod_id", vid);
        data.put("vod_name", title);
        data.put("vod_pic", pic);
        data.put("vod_remarks", remark);
        return data;
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        JSONArray classes = new JSONArray();
        // 顶部平台精选分类
        classes.put(getClassMap("qq", "腾讯VIP精选"));
        classes.put(getClassMap("yk", "优酷VIP精选"));
        classes.put(getClassMap("bli", "B站VIP精选"));
        // 全部分类
        classes.put(getClassMap("1", "电影"));
        classes.put(getClassMap("2", "电视剧"));
        classes.put(getClassMap("4", "动漫"));
        classes.put(getClassMap("3", "综艺"));
        classes.put(getClassMap("5", "短剧"));

        // 分类筛选面板
        JSONObject filterDict = new JSONObject();
        String[] movieClass = {"动作", "喜剧", "爱情", "科幻", "恐怖", "剧情", "战争", "警匪", "犯罪", "动画", "奇幻", "武侠", "冒险", "枪战", "悬疑", "惊悚", "经典", "青春", "文艺", "微电影", "古装", "历史", "运动", "农村", "儿童", "网络电影"};
        String[] movieArea = {"大陆", "香港", "台湾", "美国", "韩国", "日本", "泰国", "新加坡", "马来西亚", "印度", "英国", "法国", "加拿大", "西班牙", "俄罗斯", "其它"};
        String[] tvClass = {"古装", "战争", "青春偶像", "喜剧", "家庭", "犯罪", "动作", "奇幻", "剧情", "历史", "经典", "乡村", "情景", "商战", "网剧", "其他"};
        String[] tvArea = {"国产剧", "日韩剧", "海外剧"};
        String[] comicClass = {"情感", "科幻", "热血", "推理", "搞笑", "冒险", "萝莉", "校园", "动作", "机战", "运动", "战争", "少年", "少女"};
        String[] comicArea = {"国产动漫", "日韩动漫"};
        String[] showClass = {"选秀", "情感", "访谈", "播报", "旅游", "音乐", "舞蹈"};
        String[] showArea = {"大陆综艺", "日韩综艺"};
        String[] shortClass = {};
        String[] shortArea = {};

        JSONArray years = new JSONArray();
        for (int y = 2026; y >= 2004; y--) years.put(new JSONObject().put("n", y).put("v", y));
        JSONArray orders = new JSONArray();
        orders.put(new JSONObject().put("n", "按最新").put("v", "time"));
        orders.put(new JSONObject().put("n", "按最热").put("v", "hits"));
        orders.put(new JSONObject().put("n", "按评分").put("v", "score"));

        filterDict.put("1", makeFilter(movieClass, movieArea, years, orders));
        filterDict.put("2", makeFilter(tvClass, tvArea, years, orders));
        filterDict.put("4", makeFilter(comicClass, comicArea, years, orders));
        filterDict.put("3", makeFilter(showClass, showArea, years, orders));
        filterDict.put("5", makeFilter(shortClass, shortArea, years, orders));

        JSONObject res = new JSONObject();
        res.put("class", classes);
        res.put("filters", filterDict);
        return res.toString();
    }

    // 生成筛选JSON
    private JSONArray makeFilter(String[] cls, String[] area, JSONArray years, JSONArray orders) throws Exception {
        JSONArray arr = new JSONArray();
        if (cls.length > 0) {
            JSONArray clsVal = new JSONArray();
            clsVal.put(new JSONObject().put("n", "全部").put("v", ""));
            for (String s : cls) clsVal.put(new JSONObject().put("n", s).put("v", s));
            arr.put(new JSONObject().put("key", "class").put("name", "类型").put("value", clsVal));
        }
        if (area.length > 0) {
            JSONArray areaVal = new JSONArray();
            areaVal.put(new JSONObject().put("n", "全部").put("v", ""));
            for (String s : area) areaVal.put(new JSONObject().put("n", s).put("v", s));
            arr.put(new JSONObject().put("key", "area").put("name", "地区").put("value", areaVal));
        }
        arr.put(new JSONObject().put("key", "year").put("name", "年份").put("value", years));
        arr.put(new JSONObject().put("key", "by").put("name", "排序").put("value", orders));
        return arr;
    }

    // 首页推荐数据
    @Override
    public String homeVideoContent() throws Exception {
        JSONArray list = new JSONArray();
        String html = get(HOST + "/");
        Document doc = Jsoup.parse(html);
        Elements allBlocks = doc.select(".tv4");
        for (Element block : allBlocks) {
            Elements items = block.select(".public-list-box");
            for (Element item : items) {
                JSONObject vod = parseVodItem(item);
                if (vod != null) list.put(vod);
            }
        }
        JSONObject ret = new JSONObject();
        ret.put("list", list);
        return ret.toString();
    }

    // 分类列表/平台精选
    @Override
    public String categoryContent(String cid, String pg, boolean filter, HashMap<String, String> ext) throws Exception {
        get(HOST + "/"); // 初始化cookie
        int page = 1;
        try {
            page = Integer.parseInt(pg);
        } catch (Exception ignored) {}

        String targetUrl;
        // qq/yk/bli 平台精选标签页
        if ("qq".equals(cid) || "yk".equals(cid) || "bli".equals(cid)) {
            targetUrl = HOST + "/label/" + cid + "/page/" + page + ".html";
        } else {
            // 电影/剧集/动漫 筛选列表
            String area = ext.getOrDefault("area", "");
            String sort = ext.getOrDefault("by", "");
            String cls = ext.getOrDefault("class", "");
            String year = ext.getOrDefault("year", "");
            // 拼接筛选地址
            targetUrl = String.format("%s/cupfox-list/%s-%s-%s-%s-----%s---.html",
                    HOST, cid, URLEncoder.encode(area, "UTF-8"), URLEncoder.encode(sort, "UTF-8"),
                    URLEncoder.encode(cls, "UTF-8"), URLEncoder.encode(year, "UTF-8"), page);
        }

        JSONArray list = new JSONArray();
        String html = get(targetUrl);
        Document doc = Jsoup.parse(html);
        Elements vodItems = doc.select(".public-list-box");
        for (Element item : vodItems) {
            JSONObject vod = parseVodItem(item);
            if (vod != null) list.put(vod);
        }

        // 判断下一页
        boolean hasNext = false;
        Elements pageBtns = doc.select(".page-info a.page-link:not(.ho)");
        for (Element a : pageBtns) {
            Matcher m = PAGE_PAT.matcher(a.attr("href"));
            if (m.find() && Integer.parseInt(m.group(1)) > page) {
                hasNext = true;
                break;
            }
        }
        JSONObject ret = new JSONObject();
        ret.put("list", list);
        ret.put("page", page);
        ret.put("pagecount", hasNext ? page + 1 : page);
        ret.put("limit", vodItems.size());
        ret.put("total", 9999);
        return ret.toString();
    }

    // 详情页解析
    @Override
    public String detailContent(List<String> ids) throws Exception {
        String vid = ids.get(0);
        // 去除分集后缀
        if (vid.contains("$")) vid = vid.substring(0, vid.indexOf("$"));
        String detailUrl = HOST + "/detail/" + vid + ".html";
        String html = get(detailUrl);
        Document doc = Jsoup.parse(html);

        String vodName = "", vodActor = "", vodDirector = "", vodYear = "", vodArea = "", desc = "";
        Elements infoLi = doc.select(".info-parameter li");
        for (Element li : infoLi) {
            Element em = li.selectFirst("em");
            if (em == null) continue;
            String label = em.text().trim();
            String val = li.text().replace(em.text(), "").trim();
            if (label.contains("片名")) vodName = val;
            else if (label.contains("主演")) vodActor = val;
            else if (label.contains("导演")) vodDirector = val;
            else if (label.contains("年份")) vodYear = val;
            else if (label.contains("地区")) vodArea = val;
            else if (label.contains("简介")) desc = val;
        }

        // 多线路分集
        List<String> playFrom = new ArrayList<>();
        List<String> playUrl = new ArrayList<>();
        Elements tabNames = doc.select(".anthology-tab .swiper-slide");
        Elements boxList = doc.select(".anthology-list-box");
        Pattern epPat = Pattern.compile("data-url=\"(.*?)\"");

        for (int i = 0; i < boxList.size(); i++) {
            Element box = boxList.get(i);
            Elements epsLi = box.select("li a");
            List<String> eps = new ArrayList<>();
            for (Element a : epsLi) {
                Matcher m = epPat.matcher(a.outerHtml());
                if (!m.find()) continue;
                String epName = a.text().trim();
                if (TextUtils.isEmpty(epName)) epName = "全集";
                eps.add(epName + "$" + vid + "&" + m.group(1));
            }
            if (eps.isEmpty()) continue;
            if (REVERSE_EPISODE) Collections.reverse(eps);
            String source = i < tabNames.size() ? tabNames.get(i).text().trim() : "线路" + (i + 1);
            playFrom.add(source);
            playUrl.add(String.join("#", eps));
        }

        JSONObject vod = new JSONObject();
        vod.put("vod_id", vid);
        vod.put("vod_name", vodName);
        vod.put("vod_actor", vodActor);
        vod.put("vod_director", vodDirector);
        vod.put("vod_year", vodYear);
        vod.put("vod_area", vodArea);
        vod.put("vod_content", desc);
        vod.put("vod_play_from", String.join("$$$", playFrom));
        vod.put("vod_play_url", String.join("$$$", playUrl));

        JSONArray list = new JSONArray();
        list.put(vod);
        JSONObject res = new JSONObject();
        res.put("list", list);
        return res.toString();
    }

    // 播放器解析
    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        JSONObject ret = new JSONObject();
        ret.put("parse", 1);
        ret.put("url", id);
        String playHtml = get(absUrl(id));
        // 匹配页面播放JSON
        Pattern playPat = Pattern.compile("var player_aaaa=(.*?);", Pattern.DOTALL);
        Matcher m = playPat.matcher(playHtml);
        if (!m.find()) return ret.toString();
        JSONObject playData = new JSONObject(m.group(1));
        String realUrl = playData.optString("url", "");
        // 直链m3u8/mp4 关闭解析
        if (!TextUtils.isEmpty(realUrl) && (realUrl.contains(".m3u8") || realUrl.contains(".mp4"))) {
            ret.put("parse", 0);
            ret.put("url", realUrl);
        }
        return ret.toString();
    }

    // 搜索接口
    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return searchContent(key, quick, "1");
    }
    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        int page = 1;
        try {
            page = Integer.parseInt(pg);
        } catch (Exception ignored) {}
        String searchUrl = HOST + "/cupfox-search/-------------.html";
        Map<String, String> param = new HashMap<>();
        param.put("wd", key);
        Headers.Builder hd = new Headers.Builder();
        hd.add("X-Requested-With", "XMLHttpRequest");
        String html = post(searchUrl, param, hd);
        Document doc = Jsoup.parse(html);
        JSONArray list = new JSONArray();
        Elements items = doc.select(".public-list-box");
        for (Element item : items) {
            JSONObject vod = parseVodItem(item);
            if (vod != null) list.put(vod);
        }
        JSONObject res = new JSONObject();
        res.put("list", list);
        res.put("page", page);
        res.put("pagecount", 1);
        res.put("limit", list.length());
        res.put("total", list.length());
        return res.toString();
    }

    // POST请求封装
    private String post(String url, Map<String, String> params, Headers.Builder headerExt) throws IOException {
        FormBody.Builder form = new FormBody.Builder(StandardCharsets.UTF_8);
        for (Map.Entry<String, String> entry : params) form.add(entry.getKey(), entry.getValue());
        Headers.Builder hd = new Headers.Builder()
                .add("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1")
                .add("Referer", HOST + "/")
                .add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .add("Accept-Language", "zh-CN,zh;q=0.9");
        if (headerExt != null) hd.addAll(headerExt.build());
        Request req = new Request.Builder()
                .url(url)
                .headers(hd.build())
                .post(form.build())
                .build();
        try (Response resp = client.newCall(req).execute()) {
            return resp.body() == null ? "" : resp.body().string();
        }
    }
}
