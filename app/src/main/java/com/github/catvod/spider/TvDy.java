package com.github.catvod.spider;

import com.github.catvod.bean.Class;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import android.util.Base64;

public class TvDy extends Spider {

    private static final String siteUrl = "https://www.tvdy.xyz";

    private HashMap<String, String> getHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", Util.CHROME);
        return headers;
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Vod> list = new ArrayList<>();
        List<Class> classes = new ArrayList<>();

        String html = OkHttp.string(siteUrl, getHeaders());
        Document doc = Jsoup.parse(html);

        // =========动态解析导航分类，不再硬编码tid=========
        Elements navLinks = doc.select("nav a[href*=search.php?tid]");
        for (Element a : navLinks) {
            String href = a.attr("href");
            String name = a.text().trim();
            if(name.isEmpty()) continue;
            Matcher m = Pattern.compile("tid=(\\d+)").matcher(href);
            if(m.find()){
                String tid = m.group(1);
                classes.add(new Class(tid, name));
            }
        }

        // 首页推荐列表选择器（新版页面）
        Elements vodItems = doc.select(".vod-item");
        for (Element item : vodItems) {
            try {
                Element a = item.selectFirst("a");
                if(a == null) continue;
                String pic = a.selectFirst("img").attr("data-original");
                String url = a.attr("href");
                String name = a.attr("title");
                if (!pic.startsWith("http")) pic = siteUrl + pic;
                // 截取id：/movie/12345.html
                Matcher idMat = Pattern.compile("/movie/(\\d+)").matcher(url);
                if(!idMat.find()) continue;
                String id = idMat.group(1);
                list.add(new Vod(id, name, pic));
            } catch (Exception ignored) {}
        }
        return Result.string(classes, list);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        List<Vod> list = new ArrayList<>();
        // 新版分类请求地址
        String target = siteUrl + "/search.php?tid=" + tid + "&page=" + pg;
        String html = OkHttp.string(target, getHeaders());
        Document doc = Jsoup.parse(html);

        Elements vodItems = doc.select(".vod-item");
        for (Element item : vodItems) {
            try {
                Element a = item.selectFirst("a");
                if(a == null) continue;
                String pic = a.selectFirst("img").attr("data-original");
                String url = a.attr("href");
                String name = a.attr("title");
                if (!pic.startsWith("http")) pic = siteUrl + pic;
                Matcher idMat = Pattern.compile("/movie/(\\d+)").matcher(url);
                if(!idMat.find()) continue;
                String id = idMat.group(1);
                list.add(new Vod(id, name, pic));
            } catch (Exception ignored) {}
        }
        int page = Integer.parseInt(pg);
        return Result.string(page, page+1, 20, page*20, list);
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String detailUrl = siteUrl + "/movie/" + ids.get(0) + ".html";
        String html = OkHttp.string(detailUrl, getHeaders());
        Document doc = Jsoup.parse(html);

        Element titleEl = doc.selectFirst(".info-title h1");
        String name = titleEl != null ? titleEl.text().trim() : "";

        Element imgEl = doc.selectFirst(".pic-box img");
        String pic = imgEl != null ? imgEl.attr("data-original") : "";
        if (!pic.startsWith("http")) pic = siteUrl + pic;

        String year = "";
        Elements meta = doc.select(".info-item");
        for(Element el : meta){
            String text = el.text();
            if(text.contains("年份")){
                year = text.replace("年份：","").trim();
                break;
            }
        }

        Element descEl = doc.selectFirst(".desc-content");
        String desc = descEl != null ? descEl.text().trim() : "";

        //解析播放源与集数
        Elements sourceTabs = doc.select(".play-tab-item");
        Elements playLists = doc.select(".play-ep-list");

        StringBuilder playFrom = new StringBuilder();
        StringBuilder playUrl = new StringBuilder();

        for(int i=0;i<sourceTabs.size() && i<playLists.size();i++){
            String sourceName = sourceTabs.get(i).text().trim();
            if(playFrom.length()>0) playFrom.append("$$$");
            playFrom.append(sourceName);

            StringBuilder epSb = new StringBuilder();
            Elements eps = playLists.get(i).select("a");
            for(Element ep : eps){
                String epName = ep.text().trim();
                String epHref = ep.attr("href");
                // /play/xxxx.html 提取播放id
                Matcher m = Pattern.compile("/play/(\\d+)").matcher(epHref);
                if(!m.find()) continue;
                String playId = m.group(1);
                if(epSb.length()>0) epSb.append("#");
                epSb.append(epName).append("$").append(playId);
            }
            if(playUrl.length()>0) playUrl.append("$$$");
            playUrl.append(epSb);
        }

        Vod vod = new Vod();
        vod.setVodId(ids.get(0));
        vod.setVodPic(pic);
        vod.setVodYear(year);
        vod.setVodName(name);
        vod.setVodContent(desc);
        vod.setVodPlayFrom(playFrom.toString());
        vod.setVodPlayUrl(playUrl.toString());
        return Result.string(vod);
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        List<Vod> list = new ArrayList<>();
        String searchUrl = siteUrl + "/search.php?searchword=" + URLEncoder.encode(key,"UTF-8");
        String html = OkHttp.string(searchUrl, getHeaders());
        Document doc = Jsoup.parse(html);

        Elements vodItems = doc.select(".vod-item");
        for (Element item : vodItems) {
            try {
                Element a = item.selectFirst("a");
                if(a == null) continue;
                String pic = a.selectFirst("img").attr("data-original");
                String url = a.attr("href");
                String name = a.attr("title");
                if (!pic.startsWith("http")) pic = siteUrl + pic;
                Matcher idMat = Pattern.compile("/movie/(\\d+)").matcher(url);
                if(!idMat.find()) continue;
                String id = idMat.group(1);
                list.add(new Vod(id, name, pic));
            } catch (Exception ignored) {}
        }
        return Result.string(list);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        String playHtml = OkHttp.string(siteUrl + "/play/" + id + ".html", getHeaders());
        Pattern pattern = Pattern.compile("var now=base64decode\\((.*?)\\);var");
        Matcher matcher = pattern.matcher(playHtml);
        String playUrl = "";
        if (matcher.find()) {
            String raw = matcher.group(1).replace("(\"","").replace("\")","");
            playUrl = new String(Base64.decode(raw, Base64.DEFAULT));
        }
        return Result.get().url(playUrl).header(getHeaders()).string();
    }
}
