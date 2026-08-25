package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import com.github.catvod.net.OkResult;

import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Request;
import okhttp3.Response;

public class FengYe extends Spider {

    private String siteUrl = "https://www.cd-zj.com";
    private String backupUrl = "https://www.vip1949.com/";
    private static String cachedSiteUrl = "";
    private static long lastCheckTime = 0;
    private Context mContext;

    private static final String UA = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36";
    private static final String ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8";

    private static final String FILTER_JSON = "{\"2\":[{\"key\":\"class\",\"name\":\"类型\",\"value\":[{\"n\":\"全部\",\"v\":\"2\"},{\"n\":\"国产剧\",\"v\":\"13\"},{\"n\":\"日韩剧\",\"v\":\"15\"},{\"n\":\"海外剧\",\"v\":\"16\"}]},{\"key\":\"area\",\"name\":\"地区\",\"value\":[{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"大陆\",\"v\":\"大陆\"},{\"n\":\"香港\",\"v\":\"香港\"},{\"n\":\"台湾\",\"v\":\"台湾\"},{\"n\":\"美国\",\"v\":\"美国\"},{\"n\":\"韩国\",\"v\":\"韩国\"},{\"n\":\"日本\",\"v\":\"日本\"}]},{\"key\":\"year\",\"name\":\"年份\",\"value\":[{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"2026\",\"v\":\"2026\"},{\"n\":\"2025\",\"v\":\"2025\"},{\"n\":\"2024\",\"v\":\"2024\"},{\"n\":\"2023\",\"v\":\"2023\"},{\"n\":\"2022\",\"v\":\"2022\"},{\"n\":\"2021\",\"v\":\"2021\"},{\"n\":\"2020\",\"v\":\"2020\"}]},{\"key\":\"lang\",\"name\":\"语言\",\"value\":[{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"国语\",\"v\":\"国语\"},{\"n\":\"英语\",\"v\":\"英语\"},{\"n\":\"粤语\",\"v\":\"粤语\"}]},{\"key\":\"letter\",\"name\":\"字母\",\"value\":[{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"A\",\"v\":\"A\"},{\"n\":\"B\",\"v\":\"B\"},{\"n\":\"C\",\"v\":\"C\"},{\"n\":\"D\",\"v\":\"D\"}]},{\"key\":\"sort\",\"name\":\"排序\",\"value\":[{\"n\":\"时间\",\"v\":\"time\"},{\"n\":\"人气\",\"v\":\"hits\"},{\"n\":\"评分\",\"v\":\"score\"}]}],\"1\":[{\"key\":\"class\",\"name\":\"类型\",\"value\":[{\"n\":\"全部\",\"v\":\"1\"},{\"n\":\"动作片\",\"v\":\"6\"},{\"n\":\"喜剧片\",\"v\":\"7\"},{\"n\":\"恐怖片\",\"v\":\"8\"},{\"n\":\"科幻片\",\"v\":\"9\"},{\"n\":\"爱情片\",\"v\":\"10\"},{\"n\":\"剧情片\",\"v\":\"11\"},{\"n\":\"战争片\",\"v\":\"12\"}]},{\"key\":\"area\",\"name\":\"地区\",\"value\":[{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"大陆\",\"v\":\"大陆\"},{\"n\":\"香港\",\"v\":\"香港\"},{\"n\":\"台湾\",\"v\":\"台湾\"},{\"n\":\"美国\",\"v\":\"美国\"},{\"n\":\"韩国\",\"v\":\"韩国\"},{\"n\":\"日本\",\"v\":\"日本\"}]},{\"key\":\"year\",\"name\":\"年份\",\"value\":[{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"2026\",\"v\":\"2026\"},{\"n\":\"2025\",\"v\":\"2025\"},{\"n\":\"2024\",\"v\":\"2024\"},{\"n\":\"2023\",\"v\":\"2023\"},{\"n\":\"2022\",\"v\":\"2022\"},{\"n\":\"2021\",\"v\":\"2021\"},{\"n\":\"2020\",\"v\":\"2020\"}]},{\"key\":\"lang\",\"name\":\"语言\",\"value\":[{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"国语\",\"v\":\"国语\"},{\"n\":\"英语\",\"v\":\"英语\"},{\"n\":\"粤语\",\"v\":\"粤语\"}]},{\"key\":\"letter\",\"name\":\"字母\",\"value\":[{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"A\",\"v\":\"A\"},{\"n\":\"B\",\"v\":\"B\"},{\"n\":\"C\",\"v\":\"C\"},{\"n\":\"D\",\"v\":\"D\"}]},{\"key\":\"sort\",\"name\":\"排序\",\"value\":[{\"n\":\"时间\",\"v\":\"time\"},{\"n\":\"人气\",\"v\":\"hits\"},{\"n\":\"评分\",\"v\":\"score\"}]}],\"4\":[{\"key\":\"class\",\"name\":\"类型\",\"value\":[{\"n\":\"全部\",\"v\":\"4\"},{\"n\":\"国产动漫\",\"v\":\"25\"},{\"n\":\"日韩动漫\",\"v\":\"26\"}]},{\"key\":\"year\",\"name\":\"年份\",\"value\":[{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"2026\",\"v\":\"2026\"},{\"n\":\"2025\",\"v\":\"2025\"},{\"n\":\"2024\",\"v\":\"2024\"}]},{\"key\":\"sort\",\"name\":\"排序\",\"value\":[{\"n\":\"时间\",\"v\":\"time\"},{\"n\":\"人气\",\"v\":\"hits\"},{\"n\":\"评分\",\"v\":\"score\"}]}],\"3\":[{\"key\":\"class\",\"name\":\"类型\",\"value\":[{\"n\":\"全部\",\"v\":\"3\"},{\"n\":\"大陆综艺\",\"v\":\"21\"},{\"n\":\"日韩综艺\",\"v\":\"22\"}]},{\"key\":\"year\",\"name\":\"年份\",\"value\":[{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"2026\",\"v\":\"2026\"},{\"n\":\"2025\",\"v\":\"2025\"},{\"n\":\"2024\",\"v\":\"2024\"}]},{\"key\":\"sort\",\"name\":\"排序\",\"value\":[{\"n\":\"时间\",\"v\":\"time\"},{\"n\":\"人气\",\"v\":\"hits\"},{\"n\":\"评分\",\"v\":\"score\"}]}]}";

    private static final Pattern PATTERN_DETAIL_ID = Pattern.compile("/detail/(\\d+)\\.html");
    private static final Pattern PATTERN_PLAY_ID = Pattern.compile("/play/(.*?)\\.html");
    private static final Pattern PATTERN_PAGE = Pattern.compile("---(\\d+)---");
    private static final Pattern PATTERN_PLAYER_AAAA = Pattern.compile("player_aaaa=(.*?)</script>", Pattern.DOTALL);
    private static final Pattern PATTERN_DATA_TE = Pattern.compile("data-te=\"(.*?)\"");

    private String fixPic(String pic) {
        if (TextUtils.isEmpty(pic)) return "";
        if (pic.startsWith("//")) return "https:" + pic;
        if (!pic.contains("://")) return removeTrailingSlash(siteUrl) + pic;
        return pic;
    }

    private String removeTrailingSlash(String str) {
        if (TextUtils.isEmpty(str)) return str;
        while (str.endsWith("/")) {
            str = str.substring(0, str.length() - 1);
        }
        return str;
    }

    private String absUrl(String str) {
        if (TextUtils.isEmpty(str)) return removeTrailingSlash(siteUrl) + "/";
        if (str.startsWith("http")) return str;
        return removeTrailingSlash(siteUrl) + (str.startsWith("/") ? str : "/" + str);
    }

    private Map<String, String> getHeaders() {
        Map<String, String> h = new HashMap<>();
        h.put("User-Agent", UA);
        h.put("Accept", ACCEPT);
        h.put("Accept-Language", "zh-CN,zh;q=0.9");
        h.put("Referer", removeTrailingSlash(siteUrl) + "/");
        h.put("Cache-Control", "no-cache");
        h.put("Pragma", "no-cache");
        return h;
    }

    private Map<String, String> getHeaders(String referer) {
        Map<String, String> h = getHeaders();
        h.put("Referer", removeTrailingSlash(referer) + "/");
        return h;
    }

    private String getOrDefault(HashMap<String, String> map, String key, String defaultValue) {
        if (map == null) return defaultValue;
        String value = map.get(key);
        return TextUtils.isEmpty(value) ? defaultValue : value;
    }

    private String fetchHtml(String url) {
        try {
            if (!url.startsWith("http")) {
                url = removeTrailingSlash(siteUrl) + (url.startsWith("/") ? url : "/" + url);
            }
            return OkHttp.string(url, null, getHeaders(siteUrl));
        } catch (Exception e) {}
        return "";
    }

    private byte[] fetchBytes(String url) {
        try {
            if (!url.startsWith("http")) {
                url = removeTrailingSlash(siteUrl) + (url.startsWith("/") ? url : "/" + url);
            }
            Request request = new Request.Builder()
                    .url(url)
                    .headers(okhttp3.Headers.of(getHeaders(siteUrl)))
                    .build();
            Response response = OkHttp.newCall(request);
            if (response.isSuccessful() && response.body() != null) {
                byte[] bytes = response.body().bytes();
                System.out.println("=== fetchBytes: got " + bytes.length + " bytes");
                return bytes;
            }
        } catch (Exception e) {
            System.out.println("=== fetchBytes exception: " + e.getMessage());
        }
        return null;
    }

    private String captchaOCR(byte[] imgBytes) {
        return CaptchaUtil.recognize(imgBytes, mContext);
    }

    /**
     * 验证码处理 - 使用同一个OkHttpClient保持Cookie/Session
     */
    private String resolveCaptcha(String inputUrl) {
        String url = absUrl(inputUrl);
        
        // 先访问页面，检查是否需要验证码
        String html = fetchHtml(url);
        if (!html.contains("系统安全验证") && !html.contains("mac_verify") && !html.contains("captcha")) {
            System.out.println("=== resolveCaptcha: no captcha needed");
            return html;
        }

        System.out.println("=== resolveCaptcha: captcha detected, solving...");

        // 使用同一个OkHttpClient实例，保持cookies
        okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
                .cookieJar(new okhttp3.JavaNetCookieJar(new java.net.CookieManager()))
                .build();

        for (int i = 0; i < 5; i++) {
            try {
                // 1. 获取验证码图片
                String verifyUrl = absUrl("/captcha.php?type=code&r=" + Math.random());
                System.out.println("=== resolveCaptcha: fetching captcha from " + verifyUrl);
                
                okhttp3.Request imgRequest = new okhttp3.Request.Builder()
                        .url(verifyUrl)
                        .header("User-Agent", UA)
                        .header("Referer", removeTrailingSlash(siteUrl) + "/")
                        .header("Accept", ACCEPT)
                        .build();
                
                okhttp3.Response imgResponse = client.newCall(imgRequest).execute();
                byte[] imgBytes = imgResponse.body() != null ? imgResponse.body().bytes() : null;
                
                if (imgBytes == null || imgBytes.length == 0) {
                    System.out.println("=== resolveCaptcha: imgBytes null or empty");
                    continue;
                }
                
                System.out.println("=== resolveCaptcha: got " + imgBytes.length + " bytes");
                
                // 2. OCR识别验证码
                String code = captchaOCR(imgBytes);
                System.out.println("=== resolveCaptcha: OCR result = [" + code + "]");
                
                if (TextUtils.isEmpty(code) || code.length() != 4) {
                    System.out.println("=== resolveCaptcha: invalid code length: " + (code == null ? "null" : code.length()));
                    continue;
                }

                // 3. 提交验证码 - 使用同一个client保持cookies
                okhttp3.FormBody formBody = new okhttp3.FormBody.Builder()
                        .add("check", code)
                        .build();
                
                okhttp3.Request verifyRequest = new okhttp3.Request.Builder()
                        .url(absUrl("/captcha.php?type=verify"))
                        .post(formBody)
                        .header("User-Agent", UA)
                        .header("Referer", removeTrailingSlash(siteUrl) + "/")
                        .header("Accept", ACCEPT)
                        .build();
                
                okhttp3.Response verifyResponse = client.newCall(verifyRequest).execute();
                String body = verifyResponse.body() != null ? verifyResponse.body().string() : "";
                System.out.println("=== verify response: " + body);
                
                JSONObject json = new JSONObject(body);
                if (json.optInt("code") == 1) {
                    System.out.println("=== verify SUCCESS! (attempt " + (i + 1) + ")");
                    // 4. 验证通过后，用同一个client获取数据
                    okhttp3.Request dataRequest = new okhttp3.Request.Builder()
                            .url(url)
                            .header("User-Agent", UA)
                            .header("Referer", removeTrailingSlash(siteUrl) + "/")
                            .header("Accept", ACCEPT)
                            .build();
                    okhttp3.Response dataResponse = client.newCall(dataRequest).execute();
                    String result = dataResponse.body() != null ? dataResponse.body().string() : "";
                    System.out.println("=== resolveCaptcha: fetched data length=" + result.length());
                    return result;
                } else {
                    System.out.println("=== verify FAILED! (attempt " + (i + 1) + "): " + json.optString("msg"));
                }
            } catch (Exception e) {
                System.out.println("=== resolveCaptcha exception: " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        System.out.println("=== resolveCaptcha: all attempts failed");
        return html;
    }

    private ArrayList<Vod> parseList(String html) {
        ArrayList<Vod> list = new ArrayList<>();
        LinkedHashSet<String> idSet = new LinkedHashSet<>();
        Document doc = Jsoup.parse(html);
        Elements items = doc.select("a.public-list-exp");

        for (Element item : items) {
            Matcher matcher = PATTERN_DETAIL_ID.matcher(item.attr("href"));
            if (matcher.find()) {
                String id = matcher.group(1);
                if (idSet.add(id)) {
                    Element img = item.selectFirst("img");
                    String name = "";
                    if (img != null) {
                        name = img.attr("title");
                        if (TextUtils.isEmpty(name)) name = img.attr("alt");
                    }
                    String pic = img != null ? fixPic(img.attr("data-src")) : "";

                    Element remarkEl = item.selectFirst(".ft2, .public-list-prb");
                    String remark = remarkEl != null ? remarkEl.text() : "";

                    Element typeEl = item.selectFirst("span.public-prt");
                    String type = typeEl != null ? typeEl.text() : "";

                    Vod vod = new Vod(id, name, pic, remark);
                    vod.setVodYear(type);
                    list.add(vod);
                }
            }
        }
        return list;
    }

    private boolean isSiteOnline(String str) {
        String url = removeTrailingSlash(str);
        String[] urls = {url, url + "/cupfox-list/1--------1---.html"};
        for (String test : urls) {
            try {
                OkResult result = OkHttp.get(test, null, getHeaders(url));
                if (result != null) {
                    int code = result.getCode();
                    if (code >= 200 && code < 400) {
                        String body = result.getBody();
                        if (!TextUtils.isEmpty(body) && (body.contains("public-list-exp") || body.contains("影片"))) {
                            return true;
                        }
                    }
                }
            } catch (Exception e) {}
        }
        return false;
    }

    @Override
    public void init(Context context, String extend) throws Exception {
        this.mContext = context;
        CaptchaUtil.initTessData(context);

        this.siteUrl = "https://www.cd-zj.com";
        this.backupUrl = "https://www.vip1949.com/";

        if (!TextUtils.isEmpty(extend)) {
            String ext = extend.trim();
            if (!ext.startsWith("http")) {
                try {
                    JSONObject json = new JSONObject(ext);
                    String url = json.optString("url");
                    if (!TextUtils.isEmpty(url)) ext = url.trim();
                } catch (Exception e) {}
            }
            if (ext.startsWith("http")) {
                this.backupUrl = ext;
            }
        }

        long now = System.currentTimeMillis();
        if (!TextUtils.isEmpty(cachedSiteUrl) && now - lastCheckTime < 300000) {
            this.siteUrl = cachedSiteUrl;
            return;
        }

        String testUrl = this.backupUrl;
        String defaultUrl = "https://www.cd-zj.com";
        try {
            String body = OkHttp.string(testUrl, null, getHeaders(defaultUrl));
            if (!TextUtils.isEmpty(body)) {
                ArrayList<String> candidates = new ArrayList<>();
                Matcher m = Pattern.compile("<a[^>]+href=\"(https?://[^\"]+)\"[^>]*>").matcher(body);
                while (m.find()) {
                    String url = removeTrailingSlash(m.group(1));
                    if (!TextUtils.isEmpty(url) && !candidates.contains(url)) {
                        candidates.add(url);
                    }
                }
                if (!candidates.isEmpty()) {
                    for (String candidate : candidates) {
                        if (isSiteOnline(candidate)) {
                            defaultUrl = candidate;
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {}

        this.siteUrl = defaultUrl;
        cachedSiteUrl = defaultUrl;
        lastCheckTime = now;
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        ArrayList<Class> classes = new ArrayList<>();
        classes.add(new Class("/label/qq", "腾讯VIP精选"));
        classes.add(new Class("/label/bli", "B站VIP精选"));
        classes.add(new Class("/label/youku", "优酷VIP精选"));
        classes.add(new Class("2", "电视剧"));
        classes.add(new Class("1", "电影"));
        classes.add(new Class("4", "动漫"));
        classes.add(new Class("3", "综艺"));
        classes.add(new Class("5", "热门短剧"));

        JSONObject filterJson = new JSONObject(FILTER_JSON);

        if (!filter) return Result.string(classes, new ArrayList<>());
        return Result.string(classes, new ArrayList<>(), filterJson);
    }

    @Override
    public String homeVideoContent() throws Exception {
        return Result.string(parseList(fetchHtml("/")));
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        if (tid.startsWith("/label/")) {
            String url = tid + "/page/" + pg + ".html";
            ArrayList<Vod> list = parseList(fetchHtml(url));
            int page = Integer.parseInt(pg);
            int pageCount = list.size() < 24 ? page : page + 2;
            return Result.string(page, pageCount, 24, pageCount * 24, list);
        }

        String classType = getOrDefault(extend, "class", getOrDefault(extend, "tid", tid));
        String area = getOrDefault(extend, "area", "");
        String genre = getOrDefault(extend, "genre", "");
        String lang = getOrDefault(extend, "lang", "");
        String letter = getOrDefault(extend, "letter", "");
        String sort = getOrDefault(extend, "sort", "");

        if (!area.isEmpty() || !genre.isEmpty() || !lang.isEmpty() || !letter.isEmpty() || !sort.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append("/cupfox-list/").append(classType).append("-")
              .append(area).append("-").append(genre).append("-")
              .append(lang).append("-").append(letter).append("-")
              .append(sort).append("---").append(pg).append(".html");
            return Result.string(1, 1, 36, 9999, parseList(resolveCaptcha(sb.toString())));
        }

        String url = "/cupfox-list/" + classType + "--------" + pg + "---.html";
        String html = resolveCaptcha(url);
        ArrayList<Vod> list = parseList(html);

        int page = Integer.parseInt(pg);
        int totalPage = page;
        Document doc = Jsoup.parse(html);
        Elements pageLinks = doc.select("a.page-link");
        for (Element el : pageLinks) {
            if ("尾页".equals(el.text())) {
                Matcher m = PATTERN_PAGE.matcher(el.attr("href"));
                if (m.find()) totalPage = Integer.parseInt(m.group(1));
            }
        }
        if (list.isEmpty()) totalPage = 0;

        return Result.string(page, totalPage, 36, 9999, list);
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        if (ids == null || ids.isEmpty()) return Result.error("详情参数为空");
        String id = ids.get(0);
        if (id.contains("#")) id = id.substring(0, id.indexOf('#'));
        id = id.trim();
        if (TextUtils.isEmpty(id)) return Result.error("影片ID为空");

        String html = fetchHtml("/detail/" + id + ".html");
        Document doc = Jsoup.parse(html);

        Vod vod = new Vod(id, "", "");
        Element titleEl = doc.selectFirst("h3.slide-info-title");
        vod.setVodName(titleEl != null ? titleEl.text().trim() : "");

        Element imgEl = doc.selectFirst("img.lazy");
        vod.setVodPic(imgEl != null ? fixPic(imgEl.attr("data-src")) : "");

        String director = "", actor = "";
        Elements infoEls = doc.select(".slide-info");
        for (Element el : infoEls) {
            String text = el.text().trim();
            if (text.startsWith("导演：")) director = text.replace("导演：", "").trim();
            if (text.startsWith("演员：")) actor = text.replace("演员：", "").trim();
        }
        vod.setVodDirector(director);
        vod.setVodActor(actor);

        Element descEl = doc.selectFirst("#height_limit");
        vod.setVodContent(descEl != null ? descEl.text().trim() : "");

        Elements sourceTabs = doc.select(".anthology-tab a.swiper-slide");
        ArrayList<String> sources = new ArrayList<>();
        for (Element tab : sourceTabs) {
            String text = tab.text().trim();
            if (!TextUtils.isEmpty(text)) sources.add(text);
        }

        Elements playBoxes = doc.select(".anthology-list-box");
        ArrayList<String> playUrls = new ArrayList<>();

        for (int i = 0; i < playBoxes.size(); i++) {
            ArrayList<String> episodes = new ArrayList<>();
            Elements links = playBoxes.get(i).select("li a");
            for (Element link : links) {
                Matcher m = PATTERN_PLAY_ID.matcher(link.attr("href"));
                if (m.find()) {
                    String epName = link.text().trim();
                    if (TextUtils.isEmpty(epName)) epName = "正片";
                    episodes.add(epName + "$" + id + "/" + m.group(1));
                }
            }
            if (!episodes.isEmpty() && i < sources.size()) {
                ArrayList<String> reversed = new ArrayList<>();
                for (int j = episodes.size() - 1; j >= 0; j--) {
                    reversed.add(episodes.get(j));
                }
                playUrls.add(TextUtils.join("#", reversed));
            }
        }

        ArrayList<String> validSources = new ArrayList<>();
        for (int i = 0; i < sources.size(); i++) {
            if (i < playUrls.size()) validSources.add(sources.get(i));
        }

        vod.setVodPlayFrom(TextUtils.join("$$$", validSources));
        vod.setVodPlayUrl(TextUtils.join("$$$", playUrls));

        return Result.string(vod);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        String ua = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36";

        if (TextUtils.isEmpty(id)) return Result.error("播放ID为空");
        String vid = id.trim();
        if (vid.contains("$")) vid = vid.substring(vid.lastIndexOf("$") + 1);

        if (TextUtils.isEmpty(vid)) return Result.error("播放ID为空");

        String playUrl;
        if (!vid.startsWith("http")) {
            playUrl = siteUrl + "/play/" + vid + ".html";
        } else {
            playUrl = vid;
        }

        String html = fetchHtml(playUrl);
        if (TextUtils.isEmpty(html)) {
            return Result.get().url(playUrl).parse(1).header(getHeaders()).string();
        }

        Matcher m = PATTERN_PLAYER_AAAA.matcher(html);
        if (!m.find()) {
            return Result.get().url(playUrl).parse(1).header(getHeaders()).string();
        }

        try {
            JSONObject json = new JSONObject(m.group(1));
            String url = json.optString("url");
            String from = json.optString("from");

            if (TextUtils.isEmpty(url)) return Result.error("无播放地址");

            if (url.startsWith("http") && (url.contains(".m3u8") || url.contains(".mp4"))) {
                return Result.get().url(url).parse(0).header(getHeaders()).string();
            }

            return Result.get().url(playUrl).parse(1).header(getHeaders()).string();
        } catch (Exception e) {
            return Result.get().url(playUrl).parse(1).header(getHeaders()).string();
        }
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        String keyword = key == null ? "" : key.trim();
        String url = "/cupfox-search/" + URLEncoder.encode(keyword, "UTF-8") + "----------" + pg + ".html";
        String html = resolveCaptcha(url);

        ArrayList<Vod> list = parseList(html);

        int page = Integer.parseInt(pg);
        return Result.string(page, 1, 36, list.size(), list);
    }
}
