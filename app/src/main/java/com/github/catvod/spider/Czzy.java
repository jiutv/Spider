package com.github.catvod.spider;

import android.content.Context;
import android.os.Looper;
import android.text.TextUtils;
import android.webkit.ValueCallback;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.utils.Util;

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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class Czzy extends Spider {

    private static final String TAG = "CZZY";

    private String siteUrl = "https://www.4kcz.com";

    private HashMap<String, String> getHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", Util.CHROME);
        headers.put("Referer", siteUrl + "/");
        headers.put("Accept", Util.ACCEPT);
        headers.put("Accept-Language", "zh-CN,zh;q=0.9");
        return headers;
    }

    /**
     * 同步获取 WebView 渲染后的 HTML。
     * 解决两个关键问题：
     * 1. SafeLine WAF 会返回拦截页 → WebView 执行它的解密 JS → 自动 reload 拿到真实内容
     * 2. onPageFinished 会被触发多次（拦截页一次 + reload 后真实页一次），所以不能在回调里立即 countDown
     *
     * 方案：创建 Windowless WebView（不挂 Activity），启动 loadUrl 后进入主线程轮询：
     *   每 1s evaluateJavascript 拿当前 HTML，检查是否包含真实页面特征
     *   轮询最多 25 次（25s），检测到特征 → countDown；超时或错误 → 失败
     */
    private String webViewString(final String url, final String[] featureKeywords) throws Exception {
        SpiderDebug.log(TAG + " >> webViewString start: " + url);
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<String> htmlRef = new AtomicReference<>();
        final AtomicReference<WebView> webViewRef = new AtomicReference<>();
        final Throwable[] error = new Throwable[1];
        final AtomicBoolean finished = new AtomicBoolean(false);

        Runnable setup = () -> {
            try {
                // 用 Application context 创建 windowless WebView，不依赖 Activity 存在
                WebView webView = new WebView(Init.context());
                webView.getSettings().setJavaScriptEnabled(true);
                webView.getSettings().setDomStorageEnabled(true);
                webView.getSettings().setDatabaseEnabled(true);
                webView.getSettings().setLoadsImagesAutomatically(false);
                webView.getSettings().setBlockNetworkImage(true);
                webView.getSettings().setUserAgentString(Util.CHROME);
                // 某些机型需要 webView.setBackgroundColor(0) 才不会阻塞渲染
                webView.setBackgroundColor(0);
                webViewRef.set(webView);

                webView.setWebViewClient(new WebViewClient() {
                    int pollCount = 0;
                    int maxPoll = 25;  // 25 * 1s = 25s
                    long startMs = System.currentTimeMillis();

                    void tryExtract() {
                        if (finished.get()) return;
                        webView.evaluateJavascript(
                                "JSON.stringify({h:document.documentElement.outerHTML,u:location.href})",
                                (ValueCallback<String>) value -> {
                                    if (finished.get()) return;
                                    if (value == null) {
                                        SpiderDebug.log(TAG + " poll " + pollCount + ": evaluate returned null");
                                        scheduleNext();
                                        return;
                                    }
                                    try {
                                        JSONObject obj = new JSONObject(value);
                                        String html = obj.optString("h", "");
                                        String currentUrl = obj.optString("u", "");
                                        htmlRef.set(html);
                                        SpiderDebug.log(TAG + " poll " + pollCount
                                                + " | url=" + currentUrl
                                                + " | len=" + html.length()
                                                + " | isSafeLine=" + (html.contains("SafeLine")));
                                        if (isRealHtml(html, featureKeywords)) {
                                            SpiderDebug.log(TAG + " >> GOT REAL PAGE after " + pollCount + " polls ("
                                                    + (System.currentTimeMillis() - startMs) + "ms)");
                                            if (finished.compareAndSet(false, true)) {
                                                latch.countDown();
                                            }
                                            return;
                                        }
                                    } catch (Throwable t) {
                                        SpiderDebug.log(TAG + " poll parse error: " + t.getMessage());
                                    }
                                    scheduleNext();
                                }
                        );
                    }

                    void scheduleNext() {
                        if (finished.get()) return;
                        pollCount++;
                        if (pollCount > maxPoll) {
                            SpiderDebug.log(TAG + " >> TIMEOUT after " + maxPoll + " polls");
                            if (finished.compareAndSet(false, true)) {
                                error[0] = new RuntimeException("WebView 轮询超时: " + url);
                                latch.countDown();
                            }
                            return;
                        }
                        webView.postDelayed(this::tryExtract, 1000);
                    }

                    @Override
                    public void onPageFinished(WebView view, String finishedUrl) {
                        SpiderDebug.log(TAG + " onPageFinished: " + finishedUrl);
                        // 等 1s 再开始第一轮，给 WAF JS 跑的时间
                        if (!finished.get()) {
                            view.postDelayed(this::tryExtract, 1000);
                        }
                    }

                    @Override
                    public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                        SpiderDebug.log(TAG + " onReceivedError: " + errorCode + " " + description + " " + failingUrl);
                        if (finished.compareAndSet(false, true)) {
                            error[0] = new RuntimeException("WebView error " + errorCode + ": " + description);
                            latch.countDown();
                        }
                    }
                });

                HashMap<String, String> headers = getHeaders();
                SpiderDebug.log(TAG + " >> loadUrl: " + url);
                webView.loadUrl(url, headers);
            } catch (Throwable t) {
                SpiderDebug.log(TAG + " WebView init error: " + t.getMessage());
                t.printStackTrace();
                error[0] = t;
                if (finished.compareAndSet(false, true)) {
                    latch.countDown();
                }
            }
        };

        // 死锁防护：如果当前就在主线程，直接跑 setup；否则 post 到主线程队列
        // 这样无论调用线程是谁，WebView 都在主线程操作，latch.await 在调用线程等待
        if (Looper.myLooper() == Looper.getMainLooper()) {
            SpiderDebug.log(TAG + " NOTE: called on MAIN thread, running setup directly");
            setup.run();
        } else {
            Init.run(setup);
        }

        // 后台线程等主线程轮询结果
        boolean done = latch.await(30, TimeUnit.SECONDS);
        SpiderDebug.log(TAG + " >> await done=" + done);

        // 清理
        WebView webView = webViewRef.get();
        if (webView != null) {
            try {
                webView.stopLoading();
                webView.clearCache(true);
                webView.loadUrl("about:blank");
                webView.removeAllViews();
                webView.destroy();
            } catch (Throwable ignored) {
            }
        }

        if (!done) throw new RuntimeException("WebView 等待超时: " + url);
        if (error[0] != null) throw new Exception(error[0]);
        String html = htmlRef.get();
        if (html == null || html.isEmpty()) throw new RuntimeException("WebView 返回空 HTML: " + url);
        if (html.contains("slg-title") && html.contains("SafeLine")) {
            throw new RuntimeException("WAF 拦截页未被清除: " + url);
        }
        return html;
    }

    /** 默认 feature keywords（首页/分类页/详情页通用特征） */
    private static final String[] FEATURE_HOME = {"dytit", "navlist", "bt_img"};
    private static final String[] FEATURE_DETAIL = {"dytit", "moviedteail_list", "paly_list_btn"};

    private Document parse(String url) throws Exception {
        return Jsoup.parse(webViewString(url, FEATURE_HOME));
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
        SpiderDebug.log(TAG + " >> init siteUrl=" + siteUrl);
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        SpiderDebug.log(TAG + " >> homeContent");
        Document doc = parse(siteUrl);
        JSONObject result = new JSONObject();
        JSONArray classes = new JSONArray();
        LinkedHashMap<String, String> classMap = new LinkedHashMap<>();

        for (Element a : doc.select("ul.navlist > li > a")) {
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
        SpiderDebug.log(TAG + " >> homeContent classes=" + classes.length() + " vods=" + list.length());
        return result.toString();
    }

    @Override
    public String homeVideoContent() throws Exception {
        return homeContent(false);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        String cateId = tid;
        if (!cateId.startsWith("/")) cateId = "/" + cateId;
        if (cateId.endsWith("/")) cateId = cateId.substring(0, cateId.length() - 1);

        String url = siteUrl + cateId + "/";
        if (!"1".equals(pg)) url = url + "page/" + pg + "/";

        SpiderDebug.log(TAG + " >> categoryContent: " + url);
        Document doc = parse(url);
        JSONArray list = new JSONArray();
        getVods(list, doc);

        int page = Integer.parseInt(pg);
        int pageCount = page;
        Elements nextPage = doc.select("a:contains(下一页)");
        if (nextPage.isEmpty()) nextPage = doc.select("a.next");
        if (nextPage.isEmpty()) {
            Element current = doc.selectFirst(".page-numbers.current");
            if (current != null) {
                Element next = current.nextElementSibling();
                if (next != null && next.tagName().equals("a")) pageCount = page + 1;
            }
        } else {
            pageCount = page + 1;
        }

        JSONObject result = new JSONObject();
        result.put("page", page);
        result.put("pagecount", pageCount);
        result.put("limit", 25);
        result.put("total", pageCount * 25);
        result.put("list", list);
        SpiderDebug.log(TAG + " >> categoryContent vods=" + list.length());
        return result.toString();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String id = ids.get(0);
        String url;
        if (id.startsWith("http")) url = id;
        else url = siteUrl + (id.startsWith("/") ? id : "/" + id);

        SpiderDebug.log(TAG + " >> detailContent: " + url);
        Document doc = Jsoup.parse(webViewString(url, FEATURE_DETAIL));

        JSONObject vod = new JSONObject();
        vod.put("vod_id", id);

        Element h1 = doc.selectFirst("h1");
        vod.put("vod_name", h1 != null ? h1.text().trim() : "");

        Element poster = doc.selectFirst(".dyimg img, div.dyimg.fl img");
        if (poster != null) {
            String pic = poster.attr("data-original");
            if (pic.isEmpty()) pic = poster.attr("src");
            vod.put("vod_pic", pic);
        }

        StringBuilder info = new StringBuilder();
        for (Element li : doc.select("div.moviedteail_list li, .moviedteail_list li")) {
            info.append(li.text().trim()).append("\n");
        }
        vod.put("vod_content", info.toString().trim());

        Elements playBtns = doc.select("div.paly_list_btn > a, .paly_list_btn a");
        if (playBtns.isEmpty()) playBtns = doc.select(".play_list a, .playlist a, .stui-content__playlist a");

        StringBuilder playUrl = new StringBuilder();
        for (int i = 0; i < playBtns.size(); i++) {
            Element a = playBtns.get(i);
            String text = a.text().trim();
            String href = a.attr("href");
            if (href.isEmpty()) continue;
            if (href.startsWith("/")) href = siteUrl + href;
            else if (!href.startsWith("http")) href = siteUrl + "/" + href;
            playUrl.append(text).append("$").append(href);
            if (i < playBtns.size() - 1) playUrl.append("#");
        }

        vod.put("vod_play_from", "厂长资源");
        vod.put("vod_play_url", playUrl.toString());

        JSONArray list = new JSONArray();
        list.put(vod);
        JSONObject result = new JSONObject();
        result.put("list", list);
        SpiderDebug.log(TAG + " >> detailContent ok: " + vod.optString("vod_name", ""));
        return result.toString();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        String url = siteUrl + "/nimasile/?q=" + URLEncoder.encode(key, "UTF-8");
        SpiderDebug.log(TAG + " >> searchContent: " + url);
        Document doc = parse(url);
        JSONObject result = new JSONObject();
        JSONArray list = new JSONArray();
        getVods(list, doc);
        result.put("list", list);
        return result.toString();
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        SpiderDebug.log(TAG + " >> playerContent: " + id);
        // 播放页也走 WebView（同样可能被 WAF 挡）
        Document doc = Jsoup.parse(webViewString(id, null));
        Element iframe = doc.selectFirst("iframe");

        if (iframe != null) {
            String iframeSrc = iframe.attr("src");
            if (!iframeSrc.isEmpty()) {
                if (iframeSrc.startsWith("/")) iframeSrc = siteUrl + iframeSrc;
                else if (!iframeSrc.startsWith("http")) iframeSrc = siteUrl + "/" + iframeSrc;

                JSONObject result = new JSONObject();
                result.put("parse", 1);
                result.put("playUrl", "");
                result.put("url", iframeSrc);
                JSONObject headerJson = new JSONObject();
                headerJson.put("Referer", id);
                headerJson.put("User-Agent", Util.CHROME);
                result.put("header", headerJson.toString());
                return result.toString();
            }
        }

        JSONObject result = new JSONObject();
        result.put("parse", 1);
        result.put("playUrl", "");
        result.put("url", id);
        JSONObject headerJson = new JSONObject();
        headerJson.put("Referer", siteUrl + "/");
        headerJson.put("User-Agent", Util.CHROME);
        result.put("header", headerJson.toString());
        return result.toString();
    }

    /**
     * vod 列表提取：优先匹配真实站点 ul.bt_img.mi_ne_kd，空则降级。
     */
    private void getVods(JSONArray list, Document doc) throws JSONException {
        Elements containers = doc.select("ul.bt_img.mi_ne_kd");
        if (containers.isEmpty()) {
            for (Element ul : doc.select("ul")) {
                if (!ul.className().isEmpty()) continue;
                Elements lis = ul.select("> li");
                if (lis.size() < 2) continue;
                if (lis.first() == null || lis.first().selectFirst("a[href*=/movie/]") == null) continue;
                containers.add(ul);
                break;
            }
        }

        for (Element ul : containers) {
            for (Element li : ul.select("> li")) {
                Element a = li.selectFirst("a[href*=/movie/]");
                if (a == null) continue;
                String href = a.attr("href");
                if (href.startsWith(siteUrl)) href = href.substring(siteUrl.length());
                String name = "";
                String pic = "";
                String remark = "";

                Element img = li.selectFirst("img");
                if (img != null) {
                    name = img.attr("alt");
                    pic = img.attr("data-original");
                    if (pic.isEmpty()) pic = img.attr("src");
                }
                if (name.isEmpty()) {
                    Element dytit = li.selectFirst("h3.dytit a");
                    if (dytit != null) name = dytit.text().trim();
                }
                Element hdinfo = li.selectFirst("div.hdinfo span, .hdinfo");
                if (hdinfo != null) remark = hdinfo.text().trim();

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

    /** 判断 WebView 当前拿到的 HTML 是否是目标页面（不是 WAF 拦截页） */
    private static boolean isRealHtml(String html, String[] featureKeywords) {
        if (html == null || html.isEmpty()) return false;
        if (html.contains("slg-title") && html.contains("SafeLine")) return false;
        if (featureKeywords == null || featureKeywords.length == 0) return true;
        for (String kw : featureKeywords) {
            if (html.contains(kw)) return true;
        }
        return false;
    }
}

