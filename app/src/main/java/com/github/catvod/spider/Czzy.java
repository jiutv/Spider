package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;
import android.view.ViewGroup;
import android.webkit.ValueCallback;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.github.catvod.crawler.Spider;
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
import java.util.concurrent.atomic.AtomicReference;

public class Czzy extends Spider {

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
     * 同步获取渲染后的 HTML —— 通过 WebView 执行 JS，绕过 SafeLine WAF 等动态防护。
     * Spider 的方法是同步调用的，但 WebView 必须跑在主线程；用 CountDownLatch 桥接。
     */
    private String webViewString(final String url) throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<String> htmlRef = new AtomicReference<>();
        final AtomicReference<WebView> webViewRef = new AtomicReference<>();
        final Throwable[] error = new Throwable[1];

        Init.run(() -> {
            WebView webView = null;
            try {
                webView = new WebView(Init.context());
                webView.getSettings().setJavaScriptEnabled(true);
                webView.getSettings().setDomStorageEnabled(true);
                webView.getSettings().setDatabaseEnabled(true);
                webView.getSettings().setLoadsImagesAutomatically(false);
                webView.getSettings().setUserAgentString(Util.CHROME);
                webViewRef.set(webView);
                Util.addView(webView, new ViewGroup.LayoutParams(0, 0));

                WebView finalWebView = webView;
                webView.setWebViewClient(new WebViewClient() {
                    @Override
                    public void onPageFinished(WebView view, String finishedUrl) {
                        super.onPageFinished(view, finishedUrl);
                        // 给 WAF 动态解密/渲染一点时间
                        view.postDelayed(() -> view.evaluateJavascript(
                                "JSON.stringify({h: document.documentElement.outerHTML})",
                                (ValueCallback<String>) value -> {
                                    try {
                                        if (value != null && value.length() > 2) {
                                            // value 是 JSON 编码的字符串：{"h":"<!DOCTYPE html>..."}
                                            JSONObject obj = new JSONObject(value);
                                            htmlRef.set(obj.optString("h", ""));
                                        }
                                    } catch (Throwable ignored) {
                                    } finally {
                                        latch.countDown();
                                    }
                                }
                        ), 1000);
                    }

                    @Override
                    public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                        error[0] = new RuntimeException("WebView error: " + description);
                        latch.countDown();
                    }
                });

                HashMap<String, String> headers = getHeaders();
                webView.loadUrl(url, headers);
            } catch (Throwable t) {
                error[0] = t;
                latch.countDown();
            }
        });

        boolean done = latch.await(20, TimeUnit.SECONDS);

        // 清理 WebView
        WebView webView = webViewRef.get();
        if (webView != null) {
            try {
                Util.removeView(webView);
                webView.stopLoading();
                webView.loadUrl("about:blank");
                webView.destroy();
            } catch (Throwable ignored) {
            }
        }

        if (!done) throw new RuntimeException("WebView 加载超时: " + url);
        if (error[0] != null) throw new Exception(error[0]);
        String html = htmlRef.get();
        if (html == null || html.isEmpty()) throw new RuntimeException("WebView 返回空内容: " + url);
        // 双保险：如果 WAF 仍然拦截，返回的是 SafeLine 页面
        if (html.contains("slg-title") && html.contains("SafeLine")) {
            throw new RuntimeException("WAF 拦截仍未解除: " + url);
        }
        return html;
    }

    private Document parse(String url) throws Exception {
        return Jsoup.parse(webViewString(url));
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
        return result.toString();
    }

    @Override
    public String homeVideoContent() throws Exception {
        return homeContent(false);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        String cateId = tid;
        if (!cateId.startsWith("/")) {
            cateId = "/" + cateId;
        }
        if (cateId.endsWith("/")) {
            cateId = cateId.substring(0, cateId.length() - 1);
        }

        String url = siteUrl + cateId + "/";
        if (!"1".equals(pg)) {
            url = url + "page/" + pg + "/";
        }

        Document doc = parse(url);
        JSONArray list = new JSONArray();
        getVods(list, doc);

        int page = Integer.parseInt(pg);
        int pageCount = page;
        Elements nextPage = doc.select("a:contains(下一页)");
        if (nextPage.isEmpty()) {
            nextPage = doc.select("a.next");
        }
        if (nextPage.isEmpty()) {
            Element current = doc.selectFirst(".page-numbers.current");
            if (current != null) {
                Element next = current.nextElementSibling();
                if (next != null && next.tagName().equals("a")) {
                    pageCount = page + 1;
                }
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
        return result.toString();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String id = ids.get(0);
        String url;
        if (id.startsWith("http")) {
            url = id;
        } else {
            url = siteUrl + (id.startsWith("/") ? id : "/" + id);
        }
        Document doc = parse(url);

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
        if (playBtns.isEmpty()) {
            playBtns = doc.select(".play_list a, .playlist a, .stui-content__playlist a");
        }

        StringBuilder playUrl = new StringBuilder();
        for (int i = 0; i < playBtns.size(); i++) {
            Element a = playBtns.get(i);
            String text = a.text().trim();
            String href = a.attr("href");
            if (href.isEmpty()) continue;
            if (href.startsWith("/")) {
                href = siteUrl + href;
            } else if (!href.startsWith("http")) {
                href = siteUrl + "/" + href;
            }
            playUrl.append(text).append("$").append(href);
            if (i < playBtns.size() - 1) {
                playUrl.append("#");
            }
        }

        vod.put("vod_play_from", "厂长资源");
        vod.put("vod_play_url", playUrl.toString());

        JSONArray list = new JSONArray();
        list.put(vod);
        JSONObject result = new JSONObject();
        result.put("list", list);
        return result.toString();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        String url = siteUrl + "/nimasile/?q=" + URLEncoder.encode(key, "UTF-8");
        Document doc = parse(url);
        JSONObject result = new JSONObject();
        JSONArray list = new JSONArray();
        getVods(list, doc);
        result.put("list", list);
        return result.toString();
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        // 播放页同样可能被 WAF 拦截，用 WebView 拿渲染后的 HTML
        Document doc = Jsoup.parse(webViewString(id));
        Element iframe = doc.selectFirst("iframe");

        if (iframe != null) {
            String iframeSrc = iframe.attr("src");
            if (!iframeSrc.isEmpty()) {
                if (iframeSrc.startsWith("/")) {
                    iframeSrc = siteUrl + iframeSrc;
                } else if (!iframeSrc.startsWith("http")) {
                    iframeSrc = siteUrl + "/" + iframeSrc;
                }

                // 让 TVBox 的 WebView 加载 iframe 播放页，自动嗅探视频
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

        // 兜底：让 TVBox WebView 直接加载原始播放页
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
     * 提取 vod 列表卡片：优先匹配真实站点 ul.bt_img.mi_ne_kd，
     * 若站点改版则降级尝试原始"无 class 的 ul"逻辑。
     */
    private void getVods(JSONArray list, Document doc) throws JSONException {
        Elements containers = doc.select("ul.bt_img.mi_ne_kd");
        if (containers.isEmpty()) {
            // 降级：匹配无 class 的 ul，内部至少两个 li，且首 li 有指向 /movie/ 的链接
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
                if (href.startsWith(siteUrl)) {
                    href = href.substring(siteUrl.length());
                }
                String name = "";
                String pic = "";
                String remark = "";

                Element img = li.selectFirst("img");
                if (img != null) {
                    name = img.attr("alt");
                    pic = img.attr("data-original");
                    if (pic.isEmpty()) pic = img.attr("src");
                    // 跳过懒加载占位图
                    if (pic.contains("blank.gif") || pic.contains("loading")) {
                        String real = img.attr("data-original");
                        if (!real.isEmpty()) pic = real;
                    }
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
            // 只取第一个主列表容器，避免把"近期更新""热播榜"等小模块重复纳入
            break;
        }
    }
}
