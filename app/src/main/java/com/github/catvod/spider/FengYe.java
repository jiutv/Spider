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

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// JDK原生Base64，无自定义工具类，规避版权
import java.util.Base64;

public class FengYe extends Spider {
    // 站点主域名，换站只改此处
    private final String host = "https://www.tjtcdl.com";
    private OkHttpClient client;
    private Headers headers;

    @Override
    public void init(Context context, String extend) {
        // 初始化OkHttp客户端、请求头
        client = new OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
        headers = new Headers.Builder()
                .add("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1")
                .add("Referer", host + "/")
                .add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
                .add("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .build();
    }

    // GET请求封装
    private String get(String url) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .headers(headers)
                .get()
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (response.body() == null) return "";
            return response.body().string();
        }
    }

    // POST表单请求
    private String post(String url, Map<String, String> params, Headers headerExt) throws IOException {
        FormBody.Builder form = new FormBody.Builder(StandardCharsets.UTF_8);
        for (Map.Entry<String, String> entry : params.entrySet()) {
            form.add(entry.getKey(), entry.getValue());
        }
        Headers.Builder hd = headers.newBuilder();
        if (headerExt != null) hd.addAll(headerExt);
        Request request = new Request.Builder()
                .url(url)
                .headers(hd.build())
                .post(form.build())
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (response.body() == null) return "";
            return response.body().string();
        }
    }

    // 首页分类、筛选
    @Override
    public String homeContent(boolean filter) {
        JSONArray classes = new JSONArray();
        classes.put(new JSONObject().put("type_id", "1").put("type_name", "电影"));
        classes.put(new JSONObject().put("type_id", "2").put("type_name", "电视剧"));
        classes.put(new JSONObject().put("type_id", "4").put("type_name", "动漫"));
        classes.put(new JSONObject().put("type_id", "3").put("type_name", "综艺"));
        classes.put(new JSONObject().put("type_id", "5").put("type_name", "热门短剧"));

        JSONObject filterDict = new JSONObject();
        JSONArray years = new JSONArray();
        years.put(new JSONObject().put("n", "全部").put("v", ""));
        for (int y = 2026; y >= 2004; y--) years.put(new JSONObject().put("n", y).put("v", y));

        JSONArray orders = new JSONArray();
        orders.put(new JSONObject().put("n", "按最新").put("v", "time"));
        orders.put(new JSONObject().put("n", "按最热").put("v", "hits"));
        orders.put(new JSONObject().put("n", "按评分").put("v", "score"));

        String[] movieClass = {"动作", "喜剧", "爱情", "科幻", "恐怖", "剧情", "战争", "惊悚", "悬疑", "犯罪", "奇幻", "冒险", "动画", "武侠"};
        String[] movieArea = {"大陆", "香港", "台湾", "美国", "韩国", "日本", "泰国", "新加坡", "马来西亚", "印度", "英国", "法国", "加拿大", "西班牙", "俄罗斯", "其它"};
        String[] tvClass = {"古装", "战争", "青春偶像", "喜剧", "家庭", "犯罪", "动作", "奇幻", "剧情", "历史", "经典", "乡村", "情景", "商战", "网剧", "其他"};
        String[] tvArea = {"内地", "韩国", "香港", "台湾", "日本", "美国", "泰国", "英国", "新加坡", "其他"};
        String[] comicClass = {"科幻", "热血", "推理", "搞笑", "冒险", "萝莉", "校园", "动作", "机战", "运动", "战争", "少年", "少女"};
        String[] showClass = {"脱口秀", "真人秀", "搞笑", "访谈", "生活", "晚会", "美食", "游戏", "亲子", "旅游", "音乐", "舞蹈"};
        String[] shortClass = {"女频", "男频", "复仇", "甜宠", "穿越", "逆袭", "战神", "脑洞"};
        String[] shortArea = {"内地", "其他"};

        filterDict.put("1", makeFilter(movieClass, movieArea, years, orders));
        filterDict.put("2", makeFilter(tvClass, tvArea, years, orders));
        filterDict.put("4", makeFilter(comicClass, tvArea, years, orders));
        filterDict.put("3", makeFilter(showClass, tvArea, years, orders));
        filterDict.put("5", makeFilter(shortClass, shortArea, years, orders));

        JSONObject res = new JSONObject();
        res.put("class", classes);
        res.put("filters", filterDict);
        return res.toString();
    }

    // 构造筛选JSON
    private JSONArray makeFilter(String[] cls, String[] area, JSONArray years, JSONArray orders) {
        JSONArray arr = new JSONArray();
        JSONArray clsVal = new JSONArray();
        clsVal.put(new JSONObject().put("n", "全部").put("v", ""));
        for (String s : cls) clsVal.put(new JSONObject().put("n", s).put("v", s));
        arr.put(new JSONObject().put("key", "class").put("name", "类型").put("value", clsVal));

        JSONArray areaVal = new JSONArray();
        areaVal.put(new JSONObject().put("n", "全部").put("v", ""));
        for (String s : area) areaVal.put(new JSONObject().put("n", s).put("v", s));
        arr.put(new JSONObject().put("key", "area").put("name", "地区").put("value", areaVal));
        arr.put(new JSONObject().put("key", "year").put("name", "年份").put("value", years));
        arr.put(new JSONObject().put("key", "by").put("name", "排序").put("value", orders));
        return arr;
    }

    @Override
    public String homeVideoContent() {
        JSONObject obj = new JSONObject();
        obj.put("list", new JSONArray());
        return obj.toString();
    }

    // 分类分页列表
    @Override
    public String categoryContent(String cid, String pg, boolean filter, HashMap<String, String> ext) {
        int page = Integer.parseInt(pg);
        String area = ext.getOrDefault("area", "");
        String by = ext.getOrDefault("by", "");
        String cls = ext.getOrDefault("class", "");
        String lang = ext.getOrDefault("lang", "");
        String letter = ext.getOrDefault("letter", "");
        String year = ext.getOrDefault("year", "");

        try {
            area = URLEncoder.encode(area, "UTF-8");
            cls = URLEncoder.encode(cls, "UTF-8");
            lang = URLEncoder.encode(lang, "UTF-8");
            year = URLEncoder.encode(year, "UTF-8");
        } catch (Exception e) {
            e.printStackTrace();
        }
        String url = host + "/cupfox-list/" + cid + "-" + area + "-" + by + "-" + cls + "-" + lang + "-" + letter + "---" + page + "---" + year + ".html";
        JSONArray list = new JSONArray();
        try {
            String html = get(url);
            Document doc = Jsoup.parse(html);
            Elements items = doc.select(".public-list-box");
            Pattern vidPat = Pattern.compile("/chabeihu/(\\d+)\\.html");
            for (Element item : items) {
                Element link = item.selectFirst(".public-list-exp");
                if (link == null) continue;
                String href = link.attr("href");
                Matcher m = vidPat.matcher(href);
                if (!m.find()) continue;
                String vid = m.group(1);
                String name = link.attr("title").trim();
                Element img = link.selectFirst("img");
                String pic = "";
                if (img != null) {
                    pic = img.hasAttr("data-src") ? img.attr("data-src") : img.attr("src");
                }
                Element noteTag = item.selectFirst(".public-list-prb");
                String remark = noteTag != null ? noteTag.text().trim() : "";
                JSONObject data = new JSONObject();
                data.put("vod_id", vid);
                data.put("vod_name", name);
                data.put("vod_pic", pic);
                data.put("vod_remarks", remark);
                list.put(data);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        JSONObject ret = new JSONObject();
        ret.put("list", list);
        ret.put("page", page);
        ret.put("pagecount", 9999);
        ret.put("limit", 90);
        ret.put("total", 999999);
        return ret.toString();
    }

    // 影片详情
    @Override
    public String detailContent(List<String> ids) {
        String did = ids.get(0);
        String url = host + "/chabeihu/" + did + ".html";
        String name = "", state = "", actor = "", director = "", year = "", content = "";
        List<String> playFrom = new ArrayList<>();
        List<String> playUrl = new ArrayList<>();
        try {
            String html = get(url);
            Document doc = Jsoup.parse(html);
            Elements infoLi = doc.select(".info-parameter li");
            for (Element li : infoLi) {
                Element em = li.selectFirst("em");
                if (em == null) continue;
                String emTxt = em.text().trim();
                String val = li.text().replace(emTxt, "").replace("\u00a0", " ").trim();
                if (emTxt.contains("片名")) name = val;
                else if (emTxt.contains("状态")) state = val;
                else if (emTxt.contains("主演")) actor = val;
                else if (emTxt.contains("导演")) director = val;
                else if (emTxt.contains("年份")) year = val;
                else if (emTxt.contains("简介")) content = val;
            }
            if (TextUtils.isEmpty(name)) {
                Element titleTag = doc.selectFirst(".this-desc-title");
                if (titleTag != null) name = titleTag.text().trim();
            }
            Elements sourceTabs = doc.select(".anthology-tab .swiper-slide");
            List<String> sources = new ArrayList<>();
            for (Element s : sourceTabs) {
                Element badge = s.selectFirst(".badge");
                String txt = s.text();
                if (badge != null) txt = txt.replace(badge.text(), "").trim();
                sources.add(txt);
            }
            Elements boxList = doc.select(".anthology-list-box");
            for (int i = 0; i < boxList.size(); i++) {
                Element box = boxList.get(i);
                List<String> eps = new ArrayList<>();
                Elements aList = box.select("li a");
                for (Element a : aList) {
                    String href = a.attr("href");
                    String epName = a.text().trim();
                    if (!href.startsWith("http")) href = host + href;
                    eps.add(epName + "$" + href);
                }
                if (eps.isEmpty()) continue;
                Collections.reverse(eps);
                String sourceName = i < sources.size() ? sources.get(i) : "线路" + (i + 1);
                playFrom.add(sourceName);
                playUrl.add(String.join("#", eps));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        JSONObject vod = new JSONObject();
        vod.put("vod_id", did);
        vod.put("vod_name", name);
        vod.put("vod_actor", actor);
        vod.put("vod_director", director);
        vod.put("vod_content", content);
        vod.put("vod_remarks", state);
        vod.put("vod_year", year);
        vod.put("vod_play_from", String.join("$$$", playFrom));
        vod.put("vod_play_url", String.join("$$$", playUrl));
        JSONArray list = new JSONArray();
        list.put(vod);
        JSONObject res = new JSONObject();
        res.put("list", list);
        return res.toString();
    }

    // 播放解析
    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        JSONObject ret = new JSONObject();
        ret.put("parse", 1);
        ret.put("url", id);
        try {
            String html = get(id);
            Pattern playerPat = Pattern.compile("var player_aaaa=(.*?)</script>");
            Matcher m = playerPat.matcher(html);
            if (!m.find()) return ret.toString();
            JSONObject playerData = new JSONObject(m.group(1));
            String durl = playerData.optString("url", "");
            int encrypt = playerData.optInt("encrypt", 0);
            String fromFlag = playerData.optString("from", "");
            if (encrypt == 1 || encrypt == 2) {
                durl = java.net.URLDecoder.decode(durl, "UTF-8");
                if (encrypt == 2) {
                    // JDK原生Base64解码，无自定义工具类
                    byte[] decodeBytes = Base64.getDecoder().decode(durl);
                    durl = new String(decodeBytes, StandardCharsets.UTF_8);
                    durl = java.net.URLDecoder.decode(durl, "UTF-8");
                }
            }
            if (durl.startsWith("http") && (durl.contains(".m3u8") || durl.contains(".mp4"))) {
                ret.put("parse", 0);
                ret.put("url", durl);
                return ret.toString();
            }
            // 获取解析接口
            String configJs = get(host + "/static/js/playerconfig.js");
            String parseApi = "";
            if (!TextUtils.isEmpty(fromFlag)) {
                Matcher apiMatch = Pattern.compile("\"" + fromFlag + "\":\\{[^}]*\"parse\":\"([^\"]+)\"").matcher(configJs);
                if (apiMatch.find()) parseApi = apiMatch.group(1).replace("\\/", "/");
            }
            if (TextUtils.isEmpty(parseApi)) {
                Matcher apiMatch = Pattern.compile("\"parse\":\"(http[^\"]+)\"").matcher(configJs);
                if (apiMatch.find()) parseApi = apiMatch.group(1).replace("\\/", "/");
            }
            if (TextUtils.isEmpty(parseApi)) parseApi = "https://fgsrg.hzqingshan.com/player/?url=";
            String iframeUrl = parseApi + durl;
            Headers iframeHeader = headers.newBuilder()
                    .set("Referer", id)
                    .build();
            String iframeHtml = getWithHeader(iframeUrl, iframeHeader);
            Document iframeDoc = Jsoup.parse(iframeHtml);
            Element playerDataTag = iframeDoc.selectFirst("#player-data");
            if (playerDataTag == null) {
                ret.put("url", iframeUrl);
                return ret.toString();
            }
            String token = playerDataTag.attr("data-te");
            String bt = playerDataTag.attr("data-bt");
            java.net.URL parseUrlObj = new java.net.URL(parseApi);
            String apiHost = parseUrlObj.getProtocol() + "://" + parseUrlObj.getHost();
            String apiUrl = apiHost + bt + "mplayer.php";
            Map<String, String> postParam = new HashMap<>();
            postParam.put("url", durl);
            postParam.put("token", token);
            Headers.Builder apiHd = headers.newBuilder();
            apiHd.set("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            apiHd.set("X-Requested-With", "XMLHttpRequest");
            apiHd.set("Referer", iframeUrl);
            apiHd.set("Origin", apiHost);
            String apiResStr = post(apiUrl, postParam, apiHd.build());
            JSONObject apiJson = new JSONObject(apiResStr);
            String realUrl = apiJson.optString("url", "");
            String urlMode = apiJson.optString("urlmode", "");
            if (TextUtils.isEmpty(realUrl) && apiJson.has("data")) {
                realUrl = apiJson.getJSONObject("data").optString("url", "");
                urlMode = apiJson.getJSONObject("data").optString("urlmode", "");
            }
            if ("1".equals(urlMode)) realUrl = jsDecrypt1(realUrl);
            else if ("2".equals(urlMode)) realUrl = jsDecrypt2(realUrl);
            else if ("3".equals(urlMode)) realUrl = jsDecrypt3(realUrl);
            for (int i = 0; i < 3; i++) {
                if (realUrl.startsWith("WyJ") && realUrl.contains("/")) realUrl = jsDecrypt3(realUrl);
                else break;
            }
            if (!TextUtils.isEmpty(realUrl)) {
                ret.put("url", realUrl);
                ret.put("parse", realUrl.contains(".m3u8") || realUrl.contains(".mp4") ? 0 : 1);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return ret.toString();
    }

    private String getWithHeader(String url, Headers hd) throws IOException {
        Request req = new Request.Builder()
                .url(url)
                .headers(hd)
                .get()
                .build();
        try (Response resp = client.newCall(req).execute()) {
            return resp.body() == null ? "" : resp.body().string();
        }
    }

    // 解密1：JDK原生Base64，无自定义工具
    private String jsDecrypt1(String data) throws Exception {
        String key = md5("test");
        byte[] decodeBytes = Base64.getDecoder().decode(data);
        byte[] xor = new byte[decodeBytes.length];
        for (int i = 0; i < decodeBytes.length; i++) {
            xor[i] = (byte) (decodeBytes[i] ^ key.charAt(i % key.length()));
        }
        byte[] secondDecode = Base64.getDecoder().decode(new String(xor));
        return new String(secondDecode, StandardCharsets.UTF_8);
    }

    // 解密2
    private String jsDecrypt2(String data) throws Exception {
        String staticChars = "PXhw7UT1B0a9kQDKZsjIASmOezxYG4CHo5Jyfg2b8FLpEvRr3WtVnlqMidu6cN";
        byte[] decodeBytes = Base64.getDecoder().decode(data);
        String decode = new String(decodeBytes, StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < decode.length(); i += 3) {
            char c = decode.charAt(i);
            int idx = staticChars.indexOf(c);
            if (idx == -1) sb.append(c);
            else sb.append(staticChars.charAt((idx + 59) % 62));
        }
        return sb.toString();
    }

    // 解密3：修复JSONArray.indexOf不存在，改用循环遍历
    private String jsDecrypt3(String data) throws Exception {
        data = fixB64(data);
        String[] parts = data.split("/");
        if (parts.length < 3) return data;

        // 原生Base64解码
        byte[] arr1Bytes = Base64.getDecoder().decode(fixB64(parts[0]));
        JSONArray arr1 = new JSONArray(new String(arr1Bytes, StandardCharsets.UTF_8));

        byte[] arr2Bytes = Base64.getDecoder().decode(fixB64(parts[1]));
        JSONArray arr2 = new JSONArray(new String(arr2Bytes, StandardCharsets.UTF_8));

        String cipherRaw = String.join("/", Arrays.copyOfRange(parts, 2, parts.length));
        byte[] cipherBytes = Base64.getDecoder().decode(fixB64(cipherRaw));
        String cipher = new String(cipherBytes, StandardCharsets.UTF_8);

        StringBuilder sb = new StringBuilder();
        for (char c : cipher.toCharArray()) {
            int idx = -1;
            // 替换不存在的indexOf，手动循环匹配，消除编译报错
            for (int k = 0; k < arr2.length(); k++) {
                if (arr2.getString(k).equals(String.valueOf(c))) {
                    idx = k;
                    break;
                }
            }
            sb.append(idx == -1 ? c : arr1.getString(idx));
        }
        return sb.toString();
    }

    // 补全Base64填充等号
    private String fixB64(String s) {
        int mod = s.length() % 4;
        if (mod != 0) s += "====".substring(0, 4 - mod);
        return s;
    }

    // MD5加密，JDK原生无第三方工具
    private String md5(String text) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] bytes = md.digest(text.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b & 0xff));
        return sb.toString();
    }

    // 搜索
    @Override
    public String searchContent(String key, boolean quick) {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) {
        JSONArray list = new JSONArray();
        int page = Integer.parseInt(pg);
        try {
            String searchUrl = host + "/cupfox-search/-------------.html";
            Map<String, String> param = new HashMap<>();
            param.put("wd", key);
            Headers.Builder hd = headers.newBuilder();
            String html = post(searchUrl, param, hd.build());
            Document doc = Jsoup.parse(html);
            Pattern vidPat = Pattern.compile("/chabeihu/(\\d+)\\.html");
            Elements items = doc.select(".public-list-box");
            for (Element item : items) {
                Element link = item.selectFirst(".public-list-exp");
                if (link == null) continue;
                Matcher m = vidPat.matcher(link.attr("href"));
                if (!m.find()) continue;
                String vid = m.group(1);
                String name = link.attr("title").trim();
                Element img = link.selectFirst("img");
                String pic = img != null ? (img.hasAttr("data-src") ? img.attr("data-src") : img.attr("src")) : "";
                Element note = item.selectFirst(".public-list-prb");
                String remark = note != null ? note.text().trim() : "";
                JSONObject obj = new JSONObject();
                obj.put("vod_id", vid);
                obj.put("vod_name", name);
                obj.put("vod_pic", pic);
                obj.put("vod_remarks", remark);
                list.put(obj);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        JSONObject ret = new JSONObject();
        ret.put("list", list);
        ret.put("page", page);
        ret.put("pagecount", 1);
        ret.put("limit", list.length());
        ret.put("total", list.length());
        return ret.toString();
    }
}
