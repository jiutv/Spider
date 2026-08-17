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

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Czzy extends Spider {

    private String siteUrl = "https://www.4kcz.com";

    private HashMap<String, String> getHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        headers.put("Referer", siteUrl + "/");
        headers.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
        headers.put("Accept-Language", "zh-CN,zh;q=0.9");
        return headers;
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
        Document doc = Jsoup.parse(OkHttp.string(siteUrl, getHeaders()));
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

        Document doc = Jsoup.parse(OkHttp.string(url, getHeaders()));
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
        Document doc = Jsoup.parse(OkHttp.string(url, getHeaders()));

        JSONObject vod = new JSONObject();
        vod.put("vod_id", id);

        Element h1 = doc.selectFirst("h1");
        vod.put("vod_name", h1 != null ? h1.text().trim() : "");

        Element poster = doc.selectFirst("div.dyimg img");
        if (poster != null) {
            String pic = poster.attr("src");
            if (pic.contains("blank.gif")) pic = poster.attr("data-original");
            vod.put("vod_pic", pic);
        }

        StringBuilder info = new StringBuilder();
        for (Element li : doc.select("div.moviedteail_list li, .moviedteail_list li")) {
            info.append(li.text().trim()).append("\n");
        }
        vod.put("vod_content", info.toString().trim());

        Elements playBtns = doc.select("div.paly_list_btn > a");
        if (playBtns.isEmpty()) {
            playBtns = doc.select(".paly_list_btn a");
        }
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
        Document doc = Jsoup.parse(OkHttp.string(url, getHeaders()));
        JSONObject result = new JSONObject();
        JSONArray list = new JSONArray();
        getVods(list, doc);
        result.put("list", list);
        return result.toString();
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        String playHtml = OkHttp.string(id, getHeaders());
        Document doc = Jsoup.parse(playHtml);
        Element iframe = doc.selectFirst("iframe");

        if (iframe != null) {
            String iframeSrc = iframe.attr("src");
            if (!iframeSrc.isEmpty()) {
                if (iframeSrc.startsWith("/")) {
                    iframeSrc = siteUrl + iframeSrc;
                } else if (!iframeSrc.startsWith("http")) {
                    iframeSrc = siteUrl + "/" + iframeSrc;
                }

                // 厂长资源是免嗅源：m3u8 直链直接写在 iframe src 的 url= 参数里
                String m3u8Url = extractUrlFromIframeSrc(iframeSrc);

                if (m3u8Url != null && !m3u8Url.isEmpty()) {
                    // 【关键修复】对中文路径进行 URL 编码，避免 IJKPlayer 超时
                    m3u8Url = safeUrl(m3u8Url);

                    JSONObject result = new JSONObject();
                    result.put("parse", 0);   // 免嗅
                    result.put("playUrl", "");
                    result.put("url", m3u8Url);
                    JSONObject headerJson = new JSONObject();
                    headerJson.put("Referer", iframeSrc);
                    headerJson.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
                    result.put("header", headerJson.toString());
                    return result.toString();
                }
            }
        }

        // 无 iframe 时尝试直接提取直链
        String directM3u8 = extractDirectM3u8(doc);
        if (directM3u8 != null) {
            directM3u8 = safeUrl(directM3u8);
            JSONObject result = new JSONObject();
            result.put("parse", 0);
            result.put("playUrl", "");
            result.put("url", directM3u8);
            JSONObject headerJson = new JSONObject();
            headerJson.put("Referer", id);
            headerJson.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            result.put("header", headerJson.toString());
            return result.toString();
        }

        // 最终兜底
        JSONObject result = new JSONObject();
        result.put("parse", 1);
        result.put("playUrl", "");
        result.put("url", id);
        result.put("header", "");
        return result.toString();
    }

    /**
     * 从 iframe src 的 url= 参数中提取视频直链
     */
    private String extractUrlFromIframeSrc(String iframeSrc) {
        try {
            Pattern pattern = Pattern.compile("[?&]url=(https?://[^&\\s\"'<>]+)");
            Matcher matcher = pattern.matcher(iframeSrc);
            if (matcher.find()) {
                String url = matcher.group(1);
                // 先解码（处理已编码的情况），后续 safeUrl 会重新编码
                return URLDecoder.decode(url, "UTF-8");
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    /**
     * 对 URL 中的中文/非 ASCII 字符进行编码，保证 IJK/Exo 都能正常解析
     */
    private String safeUrl(String urlStr) {
        try {
            return new URI(urlStr).toASCIIString();
        } catch (Exception e) {
            return urlStr;
        }
    }

    private String extractDirectM3u8(Document doc) {
        Element video = doc.selectFirst("video[src]");
        if (video != null) {
            String src = video.attr("src");
            if (src.contains(".m3u8") || src.contains(".mp4")) {
                if (!src.startsWith("http")) {
                    return siteUrl + (src.startsWith("/") ? src : "/" + src);
                }
                return src;
            }
        }
        Element source = doc.selectFirst("source[src]");
        if (source != null) {
            String src = source.attr("src");
            if (src.contains(".m3u8") || src.contains(".mp4")) {
                if (!src.startsWith("http")) {
                    return siteUrl + (src.startsWith("/") ? src : "/" + src);
                }
                return src;
            }
        }
        Elements scripts = doc.select("script");
        for (Element script : scripts) {
            String html = script.html();
            if (html.contains(".m3u8")) {
                Pattern p = Pattern.compile("(https?://[^\\s\"'<>]+\\.m3u8[^\\s\"'<>]*)");
                Matcher m = p.matcher(html);
                if (m.find()) return m.group(1);
            }
        }
        return null;
    }

    private void getVods(JSONArray list, Document doc) throws JSONException {
        for (Element ul : doc.select("ul")) {
            if (!ul.className().isEmpty()) continue;
            Elements lis = ul.select("> li");
            if (lis.size() < 2) continue;
            if (lis.first() == null || lis.first().selectFirst("a[href*=/movie/]") == null) continue;

            for (Element li : lis) {
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
                }

                if (name.isEmpty()) {
                    Element dytit = li.selectFirst("h3.dytit a");
                    if (dytit != null) name = dytit.text().trim();
                }

                Element hdinfo = li.selectFirst("div.hdinfo span");
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
}
