package com.github.catvod.spider;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import com.googlecode.tesseract.android.TessBaseAPI;

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

import okhttp3.FormBody;
import okhttp3.Request;
import okhttp3.Response;

public class FengYe extends Spider {

    private String siteUrl = "https://maihaolian.com";
    private Context mContext;

    // ========== 静态Cookie，跨请求共享 ==========
    private static String sessionCookie = "";

    // 模拟真实手机浏览器的完整UA
    private static final String UA = "Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36";

    private static final Pattern PATTERN_DETAIL_ID = Pattern.compile("/detail/(\\d+)\\.html");
    private static final Pattern PATTERN_PLAY_ID = Pattern.compile("/play/(.*?)\\.html");
    private static final Pattern PATTERN_PAGE_TYPE = Pattern.compile("-(\\d+)\\.html");
    private static final Pattern PATTERN_PAGE_LABEL = Pattern.compile("/page/(\\d+)\\.html");
    private static final Pattern PATTERN_PLAYER_AAAA = Pattern.compile("player_aaaa=(\\{.*?\\})</script>", Pattern.DOTALL);
    private static final Pattern PATTERN_SEARCH_COUNT = Pattern.compile("const MY_CONSTANT\\s*=\\s*(\\d+)");

    // ========== 工具方法 ==========
    private String fixPic(String pic) {
        if (TextUtils.isEmpty(pic)) return "";
        if (pic.startsWith("//")) return "https:" + pic;
        if (!pic.contains("://")) return siteUrl + pic;
        return pic;
    }

    /**
     * 构造完整手机浏览器请求头
     */
    private Map<String, String> getHeaders(String referer) {
        Map<String, String> h = new HashMap<>();
        h.put("User-Agent", UA);
        h.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8");
        h.put("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        h.put("Accept-Encoding", "gzip, deflate, br");
        h.put("Connection", "keep-alive");
        h.put("Upgrade-Insecure-Requests", "1");
        h.put("Sec-Fetch-Dest", "document");
        h.put("Sec-Fetch-Mode", "navigate");
        h.put("Sec-Fetch-Site", "same-origin");
        h.put("Sec-Fetch-User", "?1");
        
        // 强制保证 site_entry=1，这是网站反爬的核心校验
        String cookie = sessionCookie;
        if (TextUtils.isEmpty(cookie)) {
            cookie = "site_entry=1";
        } else if (!cookie.contains("site_entry=")) {
            cookie = cookie + "; site_entry=1";
        }
        h.put("Cookie", cookie);
        
        h.put("Referer", TextUtils.isEmpty(referer) ? siteUrl + "/" : referer);
        return h;
    }

    /**
     * 验证码验证专用请求头（XHR）
     */
    private Map<String, String> getVerifyHeaders(String referer) {
        Map<String, String> h = getHeaders(referer);
        h.put("Accept", "application/json, text/javascript, */*; q=0.01");
        h.put("X-Requested-With", "XMLHttpRequest");
        h.put("Origin", siteUrl);
        h.put("Sec-Fetch-Dest", "empty");
        h.put("Sec-Fetch-Mode", "cors");
        h.put("Sec-Fetch-Site", "same-origin");
        h.put("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        return h;
    }

    private okhttp3.Headers mapToHeaders(Map<String, String> map) {
        okhttp3.Headers.Builder builder = new okhttp3.Headers.Builder();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            builder.add(entry.getKey(), entry.getValue());
        }
        return builder.build();
    }

    // ========== Cookie管理 ==========
    private void updateCookie(String setCookie) {
        if (TextUtils.isEmpty(setCookie)) return;
        String[] parts = setCookie.split(",");
        for (String part : parts) {
            String[] kv = part.trim().split(";");
            if (kv.length > 0) {
                String[] nv = kv[0].trim().split("=", 2);
                if (nv.length == 2) {
                    sessionCookie = mergeCookie(sessionCookie, nv[0].trim(), nv[1].trim());
                }
            }
        }
    }

    private String mergeCookie(String old, String name, String value) {
        if (TextUtils.isEmpty(old)) return name + "=" + value;
        StringBuilder sb = new StringBuilder();
        boolean replaced = false;
        for (String c : old.split(";")) {
            c = c.trim();
            if (c.isEmpty()) continue;
            String[] nv = c.split("=", 2);
            if (nv.length == 2 && nv[0].trim().equals(name)) {
                sb.append(name).append("=").append(value).append("; ");
                replaced = true;
            } else {
                sb.append(c).append("; ");
            }
        }
        if (!replaced) sb.append(name).append("=").append(value).append("; ");
        String r = sb.toString();
        return r.endsWith("; ") ? r.substring(0, r.length() - 2) : r;
    }

    private void extractCookies(Response response) {
        if (response == null) return;
        for (String c : response.headers("Set-Cookie")) {
            updateCookie(c);
        }
    }

    // ========== 网络请求（统一入口） ==========
    private String fetch(String url) {
        return fetch(url, siteUrl + "/");
    }

    private String fetch(String url, String referer) {
        try {
            if (!url.startsWith("http")) url = siteUrl + (url.startsWith("/") ? url : "/" + url);
            Request request = new Request.Builder()
                    .url(url)
                    .headers(mapToHeaders(getHeaders(referer)))
                    .build();
            Response response = OkHttp.newCall(request);
            extractCookies(response);
            return response.body() != null ? response.body().string() : "";
        } catch (Exception e) {
            return "";
        }
    }

    private byte[] fetchBytes(String url) {
        return fetchBytes(url, siteUrl + "/");
    }

    private byte[] fetchBytes(String url, String referer) {
        try {
            if (!url.startsWith("http")) url = siteUrl + (url.startsWith("/") ? url : "/" + url);
            Map<String, String> h = getHeaders(referer);
            h.put("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8");
            Request request = new Request.Builder()
                    .url(url)
                    .headers(mapToHeaders(h))
                    .build();
            Response response = OkHttp.newCall(request);
            extractCookies(response);
            return (response.isSuccessful() && response.body() != null) ? response.body().bytes() : null;
        } catch (Exception e) {
            return null;
        }
    }

    // ========== 验证码处理（核心优化区） ==========
    private String resolveCaptcha(String inputUrl) {
        String html = fetch(inputUrl);
        if (!html.contains("系统安全验证") && !html.contains("captcha.php?type=code")) {
            return html;
        }

        for (int i = 0; i < 5; i++) {
            try {
                String captchaUrl = siteUrl + "/captcha.php?type=code&r=" + System.currentTimeMillis();
                byte[] img = fetchBytes(captchaUrl, siteUrl + inputUrl);
                if (img == null || img.length == 0) continue;

                String code = ocr(img);
                if (code.length() != 4) continue;

                FormBody body = new FormBody.Builder().add("check", code).build();
                Request post = new Request.Builder()
                        .url(siteUrl + "/captcha.php?type=verify")
                        .post(body)
                        .headers(mapToHeaders(getVerifyHeaders(siteUrl + inputUrl)))
                        .build();

                Response resp = OkHttp.newCall(post);
                extractCookies(resp);
                String result = resp.body() != null ? resp.body().string() : "";
                
                JSONObject json = new JSONObject(result);
                if (json.optInt("code") == 1) {
                    // 验证成功，强制种下 site_entry
                    sessionCookie = mergeCookie(sessionCookie, "site_entry", "1");
                    // 网站逻辑是验证成功后自动刷新页面，这里重新请求原页面
                    Thread.sleep(600);
                    return fetch(inputUrl);
                }
                // 验证失败，继续下一轮
                Thread.sleep(300);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return "";
    }

    /**
     * OCR识别优化版：针对枫叶影院2色验证码优化
     * 
     * 验证码特征：
     * - 100x40 PNG，只有2种颜色
     * - 背景：纯白 RGB(255,255,255)
     * - 文字：深灰 RGB(50,50,50)
     * - 固定4位数字
     * 
     * 优化策略：
     * 1. 直接固定阈值二值化（不需要算平均灰度）
     * 2. 反色增强对比度后放大3倍
     * 3. 单字模式(PSM_SINGLE_WORD)识别
     */
    private String ocr(byte[] imgBytes) {
        try {
            Bitmap bmp = BitmapFactory.decodeByteArray(imgBytes, 0, imgBytes.length);
            if (bmp == null) return "";

            int w = bmp.getWidth(), h = bmp.getHeight();
            int[] pixels = new int[w * h];
            bmp.getPixels(pixels, 0, w, 0, 0, w, h);

            // 针对2色验证码，用固定阈值180即可完美分离（文字~50，背景~255）
            // 同时尝试反色方案，取最佳4位结果
            int[] thresholds = new int[]{180, 150, 200};
            String best = "";
            
            for (int threshold : thresholds) {
                // 方案A：黑字白底（直接二值化）
                String textA = ocrWithThreshold(bmp, pixels, w, h, threshold, false);
                if (textA.length() == 4) return textA;
                
                // 方案B：白字黑底（反色后二值化）
                String textB = ocrWithThreshold(bmp, pixels, w, h, threshold, true);
                if (textB.length() == 4) return textB;
                
                if (textA.length() > best.length()) best = textA;
                if (textB.length() > best.length()) best = textB;
            }
            return best;
        } catch (Exception e) {
            return "";
        }
    }

    private String ocrWithThreshold(Bitmap bmp, int[] pixels, int w, int h, int threshold, boolean invert) {
        // 放大3倍，使用NEAREST保持边缘锐利（对这种2色图效果最好）
        Bitmap scaled = Bitmap.createBitmap(w * 3, h * 3, Bitmap.Config.ARGB_8888);
        
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = pixels[y * w + x];
                int gray = ((p >> 16) & 0xff) * 299 / 1000 
                         + ((p >> 8) & 0xff) * 587 / 1000 
                         + (p & 0xff) * 114 / 1000;
                
                int color;
                if (invert) {
                    // 反色：灰字变白，白底变黑
                    color = gray < threshold ? Color.WHITE : Color.BLACK;
                } else {
                    // 正常：灰字变黑，白底变白
                    color = gray < threshold ? Color.BLACK : Color.WHITE;
                }
                
                for (int dy = 0; dy < 3; dy++)
                    for (int dx = 0; dx < 3; dx++)
                        scaled.setPixel(x * 3 + dx, y * 3 + dy, color);
            }
        }

        TessBaseAPI tess = new TessBaseAPI();
        tess.init(mContext.getFilesDir().getAbsolutePath(), "eng");
        tess.setVariable("tessedit_char_whitelist", "0123456789");
        // PSM_SINGLE_WORD 适合固定长度的单词/数字串
        tess.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SINGLE_WORD);
        tess.setImage(scaled);
        String text = tess.getUTF8Text();
        tess.end();
        
        // 回收Bitmap防止内存泄漏
        scaled.recycle();
        
        return text != null ? text.replaceAll("[^0-9]", "").trim() : "";
    }

    // ========== 解析列表 ==========
    private ArrayList<Vod> parseList(String html) {
        ArrayList<Vod> list = new ArrayList<>();
        LinkedHashSet<String> idSet = new LinkedHashSet<>();
        Document doc = Jsoup.parse(html);
        for (Element item : doc.select("a.public-list-exp")) {
            Matcher m = PATTERN_DETAIL_ID.matcher(item.attr("href"));
            if (m.find() && idSet.add(m.group(1))) {
                String id = m.group(1);
                Element img = item.selectFirst("img");
                String name = img != null ? img.attr("alt") : "";
                if (TextUtils.isEmpty(name) && img != null) name = img.attr("title");
                String pic = img != null ? fixPic(img.attr("data-src")) : "";
                if (TextUtils.isEmpty(pic) && img != null) pic = fixPic(img.attr("src"));

                Element remarkEl = item.selectFirst(".public-list-prb, .ft2");
                String remark = remarkEl != null ? remarkEl.text() : "";

                Vod vod = new Vod(id, name, pic, remark);
                list.add(vod);
            }
        }
        return list;
    }

    // ========== 首页 ==========
    @Override
    public void init(Context context, String extend) throws Exception {
        this.mContext = context;
        // 注意：TessBaseAPI初始化需要提前准备训练数据文件
        // CaptchaUtil.initTessData(context); 
        // 初始化就种下 site_entry，避免首次请求就被拦截
        sessionCookie = mergeCookie(sessionCookie, "site_entry", "1");
        fetch("/");
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        ArrayList<Class> classes = new ArrayList<>();
        classes.add(new Class("/label/qq.html", "腾讯VIP"));
        classes.add(new Class("/label/youku.html", "优酷VIP"));
        classes.add(new Class("/label/bli.html", "B站VIP"));
        classes.add(new Class("/label/duanju.html", "红果短剧"));
        classes.add(new Class("2", "电视剧"));
        classes.add(new Class("1", "电影"));
        classes.add(new Class("4", "动漫"));
        classes.add(new Class("3", "综艺"));
        classes.add(new Class("5", "短剧"));
        return Result.string(classes, new ArrayList<>());
    }

    @Override
    public String homeVideoContent() throws Exception {
        return Result.string(parseList(resolveCaptcha("/")));
    }

    // ========== 分类 ==========
    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        String url;
        if (tid.startsWith("/label/")) {
            if ("1".equals(pg)) {
                url = tid;
            } else {
                url = tid.replace(".html", "") + "/page/" + pg + ".html";
            }
        } else {
            if ("1".equals(pg)) {
                url = "/type/" + tid + ".html";
            } else {
                url = "/type/" + tid + "-" + pg + ".html";
            }
        }

        String html = resolveCaptcha(url);
        ArrayList<Vod> list = parseList(html);

        int page = Integer.parseInt(pg);
        int totalPage = page;
        Document doc = Jsoup.parse(html);
        for (Element el : doc.select("a.page-link")) {
            if ("尾页".equals(el.text())) {
                String href = el.attr("href");
                Matcher mLabel = PATTERN_PAGE_LABEL.matcher(href);
                Matcher mType = PATTERN_PAGE_TYPE.matcher(href);
                if (mLabel.find()) {
                    totalPage = Integer.parseInt(mLabel.group(1));
                } else if (mType.find()) {
                    totalPage = Integer.parseInt(mType.group(1));
                }
            }
        }
        if (list.isEmpty()) totalPage = page;

        return Result.string(page, totalPage, 36, 9999, list);
    }

    // ========== 详情 ==========
    @Override
    public String detailContent(List<String> ids) throws Exception {
        String id = ids.get(0).split("#")[0].trim();
        String html = fetch("/detail/" + id + ".html");
        Document doc = Jsoup.parse(html);

        Vod vod = new Vod(id, "", "");
        Element title = doc.selectFirst("h3.slide-info-title");
        vod.setVodName(title != null ? title.text().trim() : "");

        Element img = doc.selectFirst("img.lazy");
        vod.setVodPic(img != null ? fixPic(img.attr("data-src")) : "");

        for (Element el : doc.select(".slide-info")) {
            String text = el.text().trim();
            if (text.startsWith("导演：")) vod.setVodDirector(text.replace("导演：", "").trim());
            if (text.startsWith("演员：")) vod.setVodActor(text.replace("演员：", "").trim());
        }

        Element desc = doc.selectFirst("#height_limit");
        vod.setVodContent(desc != null ? desc.text().trim() : "");

        ArrayList<String> sources = new ArrayList<>();
        for (Element tab : doc.select(".anthology-tab a.swiper-slide")) {
            String t = tab.text().trim();
            if (!TextUtils.isEmpty(t)) sources.add(t);
        }

        ArrayList<String> playUrls = new ArrayList<>();
        Elements boxes = doc.select(".anthology-list-box");
        for (int i = 0; i < boxes.size(); i++) {
            ArrayList<String> eps = new ArrayList<>();
            for (Element link : boxes.get(i).select("li a")) {
                Matcher m = PATTERN_PLAY_ID.matcher(link.attr("href"));
                if (m.find()) {
                    String name = link.text().trim();
                    if (TextUtils.isEmpty(name)) name = "正片";
                    eps.add(name + "$" + m.group(1));
                }
            }
            if (!eps.isEmpty() && i < sources.size()) {
                ArrayList<String> rev = new ArrayList<>();
                for (int j = eps.size() - 1; j >= 0; j--) rev.add(eps.get(j));
                playUrls.add(TextUtils.join("#", rev));
            }
        }

        ArrayList<String> valid = new ArrayList<>();
        for (int i = 0; i < sources.size(); i++) {
            if (i < playUrls.size()) valid.add(sources.get(i));
        }

        vod.setVodPlayFrom(TextUtils.join("$$$", valid));
        vod.setVodPlayUrl(TextUtils.join("$$$", playUrls));
        return Result.string(vod);
    }

    // ========== 播放 ==========
    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        String vid = id.trim();
        if (vid.contains("$")) vid = vid.substring(vid.lastIndexOf("$") + 1);
        if (vid.contains("/")) vid = vid.substring(vid.lastIndexOf("/") + 1);

        String playUrl = vid.startsWith("http") ? vid : siteUrl + "/play/" + vid + ".html";
        String html = fetch(playUrl);
        if (TextUtils.isEmpty(html)) {
            return Result.get().url(playUrl).parse(1).header(getHeaders("")).string();
        }

        Matcher m = PATTERN_PLAYER_AAAA.matcher(html);
        if (!m.find()) {
            return Result.get().url(playUrl).parse(1).header(getHeaders("")).string();
        }

        try {
            JSONObject json = new JSONObject(m.group(1));
            String url = json.optString("url");
            if (!TextUtils.isEmpty(url) && url.startsWith("http") && (url.contains(".m3u8") || url.contains(".mp4"))) {
                return Result.get().url(url).parse(0).header(getHeaders("")).string();
            }
        } catch (Exception e) {}
        return Result.get().url(playUrl).parse(1).header(getHeaders("")).string();
    }

    // ========== 搜索 ==========
    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        String keyword = key == null ? "" : key.trim();
        String url = "/cupfox-search/------------" + URLEncoder.encode(keyword, "UTF-8")
                   + "----------" + pg + "---.html";

        String html = resolveCaptcha(url);
        ArrayList<Vod> list = parseList(html);

        int page = Integer.parseInt(pg);
        int totalPage = page;
        Document doc = Jsoup.parse(html);
        for (Element el : doc.select("a.page-link")) {
            if ("尾页".equals(el.text())) {
                Matcher m = PATTERN_PAGE_TYPE.matcher(el.attr("href"));
                if (m.find()) totalPage = Integer.parseInt(m.group(1));
            }
        }
        if (list.isEmpty()) totalPage = page;

        int totalCount = list.size();
        Matcher countM = PATTERN_SEARCH_COUNT.matcher(html);
        if (countM.find()) totalCount = Integer.parseInt(countM.group(1));

        return Result.string(page, totalPage, 36, totalCount, list);
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return searchContent(key, quick, "1");
    }
}
