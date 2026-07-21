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
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// JDK原生Base64，无第三方工具类，规避版权问题
import java.util.Base64;

/**
 * 茶杯狐采集爬虫 枫叶模板
 * 站点：https://www.tjtcdl.com
 * 频道CID对应：
 * 1=电影  2=电视剧  3=综艺  4=动漫  5=短剧
 * qq/yk/bli = 腾讯/优酷/B站VIP精选（无筛选，独立分页路径）
 */
public class FengYe extends Spider {
    // ====================== 全局基础配置 ======================
    // 站点主域名，换站只需要修改此处
    private final String host = "https://www.tjtcdl.com";
    // OkHttp请求客户端
    private OkHttpClient client;
    // 全局请求头，所有GET/POST共用
    private Headers headers;

    // 正则：匹配影片详情ID /chabeihu/12345.html 提取数字ID
    private final Pattern vidPat = Pattern.compile("/chabeihu/(\\d+)\\.html");
    // 正则：匹配VIP标签分页 /label/qq/page/3.html 提取页码
    private final Pattern pagePat = Pattern.compile("/page/(\\d+)\\.html");

    // ====================== 初始化 ======================
    /**
     * 爬虫初始化，仅启动时执行一次
     * @param context APP上下文
     * @param extend 扩展参数（本爬虫未使用）
     */
    @Override
    public void init(Context context, String extend) {
        // 构建OkHttp客户端：开启自动重定向，解决分页301跳转空白
        client = new OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS) // 连接超时15秒
                .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)    // 读取超时15秒
                .retryOnConnectionFailure(true) // 连接失败自动重试
                .followRedirects(true)          // 跟随HTTP重定向
                .followSslRedirects(true)      // 跟随HTTPS重定向
                .build();

        // 通用请求头，模拟手机浏览器
        headers = new Headers.Builder()
                .add("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1")
                .add("Referer", host + "/") // 来路域名防拦截
                .add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
                .add("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .build();
    }

    // ====================== 工具方法 ======================
    /**
     * 组装首页分类JSON对象
     * @param cid 频道ID
     * @param name 频道显示名称
     * @return 分类JSON
     */
    private JSONObject getClassMap(String cid, String name) throws Exception {
        return new JSONObject().put("type_id", cid).put("type_name", name);
    }

    /**
     * 通用GET请求封装
     * @param url 请求地址
     * @return 页面HTML源码
     */
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

    /**
     * POST表单提交封装（搜索、解析接口使用）
     * @param url 请求地址
     * @param params 表单键值对
     * @param headerExt 额外追加请求头（可为null）
     * @return 接口返回文本
     */
    private String post(String url, Map<String, String> params, Headers headerExt) throws IOException {
        FormBody.Builder form = new FormBody.Builder(StandardCharsets.UTF_8);
        // 遍历表单参数拼接
        for (Map.Entry<String, String> entry : params.entrySet()) {
            form.add(entry.getKey(), entry.getValue());
        }
        // 复制全局头并追加自定义头
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

    /**
     * 自定义请求头GET（解析iframe专用）
     * @param url 地址
     * @param hd 自定义头
     * @return HTML
     */
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

    // ====================== 首页分类+筛选配置 ======================
    /**
     * 首页频道分类、筛选面板生成
     * @param filter 是否开启筛选（APP固定传true）
     * @return 频道+筛选规则JSON
     */
    @Override
    public String homeContent(boolean filter) throws Exception {
        // 1. 构建左侧频道列表
        JSONArray classes = new JSONArray();
        classes.put(getClassMap("qq", "腾讯VIP精选"));
        classes.put(getClassMap("yk", "优酷VIP精选"));
        classes.put(getClassMap("bli", "B站VIP精选"));
        classes.put(getClassMap("1", "电影"));
        classes.put(getClassMap("2", "电视剧"));
        classes.put(getClassMap("4", "动漫"));
        classes.put(getClassMap("3", "综艺"));
        classes.put(getClassMap("5", "短剧"));

        // 2. 通用年份筛选数组 2026 ~ 2004
        JSONObject filterDict = new JSONObject();
        JSONArray years = new JSONArray();
        years.put(new JSONObject().put("n", "全部").put("v", ""));
        for (int y = 2026; y >= 2004; y--) years.put(new JSONObject().put("n", y).put("v", y));

        // 3. 通用排序方式
        JSONArray orders = new JSONArray();
        orders.put(new JSONObject().put("n", "按最新").put("v", "time"));
        orders.put(new JSONObject().put("n", "按最热").put("v", "hits"));
        orders.put(new JSONObject().put("n", "按评分").put("v", "score"));

        // ====================== 各频道筛选标签（与网站前端完全同步） ======================
        // CID=1 电影 类型+地区
        String[] movieClass = {
                "动作", "喜剧", "爱情", "科幻", "恐怖", "剧情", "战争", "警匪",
                "犯罪", "动画", "奇幻", "武侠", "冒险", "枪战", "悬疑", "惊悚",
                "经典", "青春", "文艺", "微电影", "古装", "历史", "运动", "农村",
                "儿童", "网络电影"
        };
        String[] movieArea = {
                "大陆", "香港", "台湾", "美国", "韩国", "日本", "泰国", "新加坡",
                "马来西亚", "印度", "英国", "法国", "加拿大", "西班牙", "俄罗斯", "其它"
        };

        // CID=2 电视剧 类型+地区
        String[] tvClass = {
                "古装", "战争", "青春偶像", "喜剧", "家庭", "犯罪", "动作", "奇幻",
                "剧情", "历史", "经典", "乡村", "情景", "商战", "网剧", "其他"
        };
        String[] tvArea = {"国产剧", "日韩剧", "海外剧"};

        // CID=4 动漫 类型+地区
        String[] comicClass = {
                "情感", "科幻", "热血", "推理", "搞笑", "冒险", "萝莉", "校园",
                "动作", "机战", "运动", "战争", "少年", "少女", "社会", "原创",
                "亲子", "益智", "励志", "其他"
        };
        String[] comicArea = {"国产动漫", "日韩动漫"};

        // CID=3 综艺 类型+地区
        String[] showClass = {
                "选秀", "情感", "访谈", "播报", "旅游", "音乐", "美食",
                "纪实", "曲艺", "生活", "游戏互动", "财经", "求职"
        };
        String[] showArea = {"大陆综艺", "日韩综艺"};

        // CID=5 短剧：页面无地区、类型筛选，数组置空，仅显示年份+排序
        String[] shortClass = {};
        String[] shortArea = {};

        // 绑定各频道筛选配置
        filterDict.put("1", makeFilter(movieClass, movieArea, years, orders));
        filterDict.put("2", makeFilter(tvClass, tvArea, years, orders));
        filterDict.put("4", makeFilter(comicClass, comicArea, years, orders));
        filterDict.put("3", makeFilter(showClass, showArea, years, orders));
        filterDict.put("5", makeFilter(shortClass, shortArea, years, orders));

        // 组装返回JSON
        JSONObject res = new JSONObject();
        res.put("class", classes);
        res.put("filters", filterDict);
        return res.toString();
    }

    /**
     * 生成单频道筛选面板JSON
     * 自动判断：类型/地区数组为空则不生成对应筛选栏（短剧专用）
     * @param cls 类型数组
     * @param area 地区数组
     * @param years 年份数组
     * @param orders 排序数组
     * @return APP识别的筛选JSON数组
     */
    private JSONArray makeFilter(String[] cls, String[] area, JSONArray years, JSONArray orders) throws Exception {
        JSONArray arr = new JSONArray();

        // 存在类型才添加【类型】筛选栏
        if (cls.length > 0) {
            JSONArray clsVal = new JSONArray();
            clsVal.put(new JSONObject().put("n", "全部").put("v", ""));
            for (String s : cls) clsVal.put(new JSONObject().put("n", s).put("v", s));
            arr.put(new JSONObject().put("key", "class").put("name", "类型").put("value", clsVal));
        }

        // 存在地区才添加【地区】筛选栏
        if (area.length > 0) {
            JSONArray areaVal = new JSONArray();
            areaVal.put(new JSONObject().put("n", "全部").put("v", ""));
            for (String s : area) areaVal.put(new JSONObject().put("n", s).put("v", s));
            arr.put(new JSONObject().put("key", "area").put("name", "地区").put("value", areaVal));
        }

        // 所有频道统一：年份 + 排序
        arr.put(new JSONObject().put("key", "year").put("name", "年份").put("value", years));
        arr.put(new JSONObject().put("key", "by").put("name", "排序").put("value", orders));
        return arr;
    }

    // ====================== 首页推荐列表 ======================
    /**
     * 首页所有推荐区块抓取（页面class .tv4）
     * 核心选择器：.tv4 大区块 / .public-list-box 单部影片卡片
     */
    @Override
    public String homeVideoContent() throws Exception {
        JSONArray list = new JSONArray();
        try {
            String html = get(host + "/");
            Document doc = Jsoup.parse(html);
            // 首页所有推荐区块容器
            Elements allBlocks = doc.select(".tv4");
            for (Element block : allBlocks) {
                // 单影片卡片容器
                Elements items = block.select(".public-list-box");
                for (Element item : items) {
                    // 影片封面+标题链接 a标签
                    Element link = item.selectFirst(".public-list-exp");
                    if (link == null) continue;
                    String href = link.attr("href");
                    Matcher m = vidPat.matcher(href);
                    if (!m.find()) continue;
                    String vid = m.group(1);
                    String name = link.attr("title").trim();

                    // 封面图片 优先取data-src懒加载地址
                    Element img = link.selectFirst("img");
                    String pic = "";
                    if (img != null) {
                        pic = img.hasAttr("data-src") ? img.attr("data-src") : img.attr("src");
                    }

                    // 更新集数/期数标签 .public-list-prb
                    Element noteTag = item.selectFirst(".public-list-prb");
                    String remark = noteTag != null ? noteTag.text().trim() : "";

                    // 组装影片基础信息
                    JSONObject data = new JSONObject();
                    data.put("vod_id", vid);
                    data.put("vod_name", name);
                    data.put("vod_pic", pic);
                    data.put("vod_remarks", remark);
                    list.put(data);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        JSONObject ret = new JSONObject();
        ret.put("list", list);
        return ret.toString();
    }

    // ====================== 分类分页列表（核心路由+选择器） ======================
    /**
     * 分类列表页面抓取
     * 两套路径：
     * 1. VIP精选(qq/yk/bli)：/label/xxx/page/页码.html
     * 2. 普通分类(1/2/3/4/5)：/cupfox-list/cid-area-by-class-lang-letter---page---year.html
     * 列表卡片选择器：.public-list-box
     */
    @Override
    public String categoryContent(String cid, String pg, boolean filter, HashMap<String, String> ext) throws Exception {
        // ========== VIP专区独立逻辑（无筛选参数） ==========
        if ("qq".equals(cid) || "yk".equals(cid) || "bli".equals(cid)) {
            ext.clear(); // 清空筛选参数，页面不支持筛选
            int page = Integer.parseInt(pg);
            String pageUrl = host + "/label/" + cid + "/page/" + page + ".html";
            String html = get(pageUrl);
            Document doc = Jsoup.parse(html);
            JSONArray list = new JSONArray();

            // VIP页面影片容器选择器 .list-vod .public-list-box.public-pic-b
            Elements vodItems = doc.select(".list-vod .public-list-box.public-pic-b");
            for (Element item : vodItems) {
                Element link = item.selectFirst(".public-list-exp");
                if (link == null) continue;
                String href = link.attr("href");
                Matcher m = vidPat.matcher(href);
                if (!m.find()) continue;
                String vid = m.group(1);
                String name = link.attr("title").trim();
                Element img = link.selectFirst("img");
                String pic = img != null ? (img.hasAttr("data-src") ? img.attr("data-src") : img.attr("src")) : "";
                String remark = item.selectFirst(".public-list-prb") != null ? item.selectFirst(".public-list-prb").text().trim() : "";
                JSONObject data = new JSONObject();
                data.put("vod_id", vid);
                data.put("vod_name", name);
                data.put("vod_pic", pic);
                data.put("vod_remarks", remark);
                list.put(data);
            }

            // 分页判断：过滤当前页a标签 class=ho
            boolean hasNext = false;
            Elements pageLinks = doc.select(".page-info a.page-link:not(.ho)");
            for (Element a : pageLinks) {
                String href = a.attr("href");
                Matcher m = pagePat.matcher(href);
                if (m.find()) {
                    int num = Integer.parseInt(m.group(1));
                    if (num > page) {
                        hasNext = true;
                        break;
                    }
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

        // ========== 普通数字分类（电影/电视剧/动漫/综艺/短剧） ==========
        int page = Integer.parseInt(pg);
        // 读取筛选参数
        String area = ext.getOrDefault("area", "");    // 地区
        String by = ext.getOrDefault("by", "");        // 排序 time/hits/score
        String cls = ext.getOrDefault("class", "");    // 类型
        String lang = ext.getOrDefault("lang", "");    // 语言（本站未使用，预留）
        String letter = ext.getOrDefault("letter", "");// 首字母（本站未使用，预留）
        String year = ext.getOrDefault("year", "");    // 年份

        // URL编码筛选参数，防止中文乱码
        try {
            area = URLEncoder.encode(area, "UTF-8");
            cls = URLEncoder.encode(cls, "UTF-8");
            lang = URLEncoder.encode(lang, "UTF-8");
            year = URLEncoder.encode(year, "UTF-8");
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 标准筛选列表路由模板
        // /cupfox-list/频道-地区-排序-类型-语言-字母---页码---年份.html
        String url = String.format("%s/cupfox-list/%s-%s-%s-%s-%s-%s---%s---%s.html",
                host, cid, area, by, cls, lang, letter, page, year);
        JSONArray list = new JSONArray();
        try {
            String html = get(url);
            Document doc = Jsoup.parse(html);
            // 通用影片卡片选择器（全站统一）
            Elements items = doc.select(".public-list-box");
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
        ret.put("pagecount", 9999); // 假分页，无限下拉
        ret.put("limit", 90);
        ret.put("total", 999999);
        return ret.toString();
    }

    // ====================== 影片详情页 ======================
    /**
     * 详情页抓取信息、多线路分集
     * 详情页面地址：/chabeihu/ID.html
     * 信息选择器：.info-parameter li 基础信息
     * 线路tab：.anthology-tab .swiper-slide
     * 分集列表：.anthology-list-box li a
     */
    @Override
    public String detailContent(List<String> ids) throws Exception {
        String did = ids.get(0);
        String url = host + "/chabeihu/" + did + ".html";
        String name = "", state = "", actor = "", director = "", year = "", content = "";
        List<String> playFrom = new ArrayList<>(); // 线路名称数组
        List<String> playUrl = new ArrayList<>();  // 分集数组

        try {
            String html = get(url);
            Document doc = Jsoup.parse(html);
            // 基础信息区块
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
            // 兜底标题选择器
            if (TextUtils.isEmpty(name)) {
                Element titleTag = doc.selectFirst(".this-desc-title");
                if (titleTag != null) name = titleTag.text().trim();
            }

            // 线路Tab标签
            Elements sourceTabs = doc.select(".anthology-tab .swiper-slide");
            List<String> sources = new ArrayList<>();
            for (Element s : sourceTabs) {
                Element badge = s.selectFirst(".badge");
                String txt = s.text();
                if (badge != null) txt = txt.replace(badge.text(), "").trim();
                sources.add(txt);
            }

            // 分集容器
            Elements boxList = doc.select(".anthology-list-box");
            for (int i = 0; i < boxList.size(); i++) {
                Element box = boxList.get(i);
                List<String> eps = new ArrayList<>();
                Elements aList = box.select("li a");
                for (Element a : aList) {
                    String href = a.attr("href");
                    String epName = a.text().trim();
                    if (!href.startsWith("http")) href = host + href;
                    eps.add(epName + "$" + href); // APP格式：集数$播放地址
                }
                if (eps.isEmpty()) continue;
                Collections.reverse(eps);
                String sourceName = i < sources.size() ? sources.get(i) : "线路" + (i + 1);
                playFrom.add(sourceName);
                playUrl.add(String.join("#", eps)); // 多集分隔符#
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 组装详情返回JSON
        JSONObject vod = new JSONObject();
        vod.put("vod_id", did);
        vod.put("vod_name", name);
        vod.put("vod_actor", actor);
        vod.put("vod_director", director);
        vod.put("vod_content", content);
        vod.put("vod_remarks", state);
        vod.put("vod_year", year);
        vod.put("vod_play_from", String.join("$$$", playFrom)); // 多线路分隔符$$$
        vod.put("vod_play_url", String.join("$$$", playUrl));
        JSONArray list = new JSONArray();
        list.put(vod);
        JSONObject res = new JSONObject();
        res.put("list", list);
        return res.toString();
    }

    // ====================== 播放解析核心（解密、跳转解析接口） ======================
    /**
     * 播放器解析函数
     * 1. 提取页面 player_aaaa 播放器JSON
     * 2. 区分1/2加密类型解码
     * 3. 读取全局解析配置js获取解析接口
     * 4. iframe中转页面 #player-data 提取token、bt参数
     * 5. POST请求解析接口获取真实m3u8/mp4
     * 6. 三种JS解密函数解码最终播放地址
     */
    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        JSONObject ret = new JSONObject();
        ret.put("parse", 1); // 默认第三方解析
        ret.put("url", id);
        try {
            String html = get(id);
            // 匹配播放器变量 var player_aaaa={...}
            Pattern playerPat = Pattern.compile("var player_aaaa=(.*?)</script>");
            Matcher m = playerPat.matcher(html);
            if (!m.find()) return ret.toString();
            JSONObject playerData = new JSONObject(m.group(1));
            String durl = playerData.optString("url", ""); // 加密原始播放链接
            int encrypt = playerData.optInt("encrypt", 0); // 加密类型0/1/2
            String fromFlag = playerData.optString("from", ""); // 线路标识

            // 解密类型1、2解码
            if (encrypt == 1 || encrypt == 2) {
                durl = java.net.URLDecoder.decode(durl, "UTF-8");
                if (encrypt == 2) {
                    byte[] decodeBytes = Base64.getDecoder().decode(durl);
                    durl = new String(decodeBytes, StandardCharsets.UTF_8);
                    durl = java.net.URLDecoder.decode(durl, "UTF-8");
                }
            }

            // 未加密直链直接返回，关闭第三方解析
            if (durl.startsWith("http") && (durl.contains(".m3u8") || durl.contains(".mp4"))) {
                ret.put("parse", 0);
                ret.put("url", durl);
                return ret.toString();
            }

            // 读取全局解析配置js /static/js/playerconfig.js
            String configJs = get(host + "/static/js/playerconfig.js");
            String parseApi = "";
            // 优先匹配当前线路对应的解析地址
            if (!TextUtils.isEmpty(fromFlag)) {
                Matcher apiMatch = Pattern.compile("\"" + fromFlag + "\":\\{[^}]*\"parse\":\"([^\"]+)\"").matcher(configJs);
                if (apiMatch.find()) parseApi = apiMatch.group(1).replace("\\/", "/");
            }
            // 兜底全局默认解析地址
            if (TextUtils.isEmpty(parseApi)) {
                Matcher apiMatch = Pattern.compile("\"parse\":\"(http[^\"]+)\"").matcher(configJs);
                if (apiMatch.find()) parseApi = apiMatch.group(1).replace("\\/", "/");
            }
            // 备用解析接口
            if (TextUtils.isEmpty(parseApi)) parseApi = "https://fgsrg.hzqingshan.com/player/?url=";

            // 打开中转iframe页面
            String iframeUrl = parseApi + durl;
            Headers iframeHeader = headers.newBuilder().set("Referer", id).build();
            String iframeHtml = getWithHeader(iframeUrl, iframeHeader);
            Document iframeDoc = Jsoup.parse(iframeHtml);
            // 中转页面参数容器 #player-data
            Element playerDataTag = iframeDoc.selectFirst("#player-data");
            if (playerDataTag == null) {
                ret.put("url", iframeUrl);
                return ret.toString();
            }
            String token = playerDataTag.attr("data-te");
            String bt = playerDataTag.attr("data-bt");

            // 拼接解析接口真实地址
            java.net.URL parseUrlObj = new java.net.URL(parseApi);
            String apiHost = parseUrlObj.getProtocol() + "://" + parseUrlObj.getHost();
            String apiUrl = apiHost + bt + "mplayer.php";

            // 提交POST获取真实播放地址
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

            // 兼容data嵌套结构
            if (TextUtils.isEmpty(realUrl) && apiJson.has("data")) {
                realUrl = apiJson.getJSONObject("data").optString("url", "");
                urlMode = apiJson.getJSONObject("data").optString("urlmode", "");
            }

            // 根据加密模式执行对应解密函数
            if ("1".equals(urlMode)) realUrl = jsDecrypt1(realUrl);
            else if ("2".equals(urlMode)) realUrl = jsDecrypt2(realUrl);
            else if ("3".equals(urlMode)) realUrl = jsDecrypt3(realUrl);

            // 循环兼容多层base64封装
            for (int i = 0; i < 3; i++) {
                if (realUrl.startsWith("WyJ") && realUrl.contains("/")) realUrl = jsDecrypt3(realUrl);
                else break;
            }

            // 最终返回真实播放地址
            if (!TextUtils.isEmpty(realUrl)) {
                ret.put("url", realUrl);
                ret.put("parse", realUrl.contains(".m3u8") || realUrl.contains(".mp4") ? 0 : 1);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return ret.toString();
    }

    // ====================== 三种JS地址解密函数 ======================
    /**
     * 解密模式1：MD5密钥异或 + Base64双重解码
     */
    private String jsDecrypt1(String data) throws Exception {
        String key = md5("test");
        byte[] decodeBytes = Base64.getDecoder().decode(data);
        byte[] xor = new byte[decodeBytes.length];
        for (int i = 0; i < decodeBytes.length; i++) {
            byte keyByte = (byte) key.charAt(i % key.length());
            xor[i] = (byte) (decodeBytes[i] ^ keyByte);
        }
        byte[] secondDecode = Base64.getDecoder().decode(new String(xor));
        return new String(secondDecode, StandardCharsets.UTF_8);
    }

    /**
     * 解密模式2：自定义字符表偏移替换
     */
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

    /**
     * 解密模式3：双数组映射置换解密（最常用）
     * 修复编译错误：JSONArray 使用 .length() 方法获取长度，不能用 .length 属性
     */
    private String jsDecrypt3(String data) throws Exception {
        data = fixB64(data);
        String[] parts = data.split("/");
        if (parts.length < 3) return data;

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
            // 修复点：arr2.length() 替代 arr2.length
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

    /**
     * Base64补全等号，修复截断无法解码问题
     */
    private String fixB64(String s) {
        int mod = s.length() % 4;
        if (mod != 0) s += "====".substring(0, 4 - mod);
        return s;
    }

    /**
     * MD5加密工具，解密1使用
     */
    private String md5(String text) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] bytes = md.digest(text.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b & 0xff));
        return sb.toString();
    }

    // ====================== 搜索功能 ======================
    /**
     * 搜索入口兼容单页搜索
     */
    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return searchContent(key, quick, "1");
    }

    /**
     * 搜索列表抓取【适配网站GET伪静态分页，修复无结果问题】
     * 页面表单method=get，真实搜索分页URL格式：/cupfox-search/URL编码关键词----------页码---.html
     * 影片卡片容器选择器：.public-list-box public-pic-b
     * 封面链接：.public-list-exp
     * 更新集数标签：.public-list-prb
     * @param key 搜索关键词
     * @param quick 快速搜索标记（APP固定传false）
     * @param pg 当前页码字符串
     * @return 搜索结果JSON
     */
    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        JSONArray list = new JSONArray();
        int pageNum = Integer.parseInt(pg);
        // 关键词UTF-8 URL编码，适配网站伪静态路径
        String encodeWord = URLEncoder.encode(key, StandardCharsets.UTF_8.name());
        // 拼接网站标准搜索分页URL模板（和页面分页href完全一致）
        String searchUrl = String.format("%s/cupfox-search/%s----------%s---.html", host, encodeWord, pageNum);

        try {
            // 追加搜索专用请求头，防止网站拦截返回空白页面
            Headers.Builder searchHeader = headers.newBuilder();
            searchHeader.add("X-Requested-With", "XMLHttpRequest");
            searchHeader.add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            // GET请求获取搜索结果页面
            String html = getWithHeader(searchUrl, searchHeader.build());
            Document doc = Jsoup.parse(html);

            // 匹配所有搜索结果影片卡片
            Elements items = doc.select(".public-list-box");
            for (Element item : items) {
                // 封面+标题a链接
                Element link = item.selectFirst(".public-list-exp");
                if (link == null) continue;
                String href = link.attr("href");
                Matcher m = vidPat.matcher(href);
                if (!m.find()) continue;
                String vid = m.group(1);
                String name = link.attr("title").trim();

                // 优先读取懒加载data-src封面图
                Element img = link.selectFirst("img");
                String pic = "";
                if (img != null) {
                    pic = img.hasAttr("data-src") ? img.attr("data-src") : img.attr("src");
                }

                // 更新集数/版本备注
                Element note = item.selectFirst(".public-list-prb");
                String remark = note != null ? note.text().trim() : "";

                // 组装APP识别的影片数据对象
                JSONObject obj = new JSONObject();
                obj.put("vod_id", vid);
                obj.put("vod_name", name);
                obj.put("vod_pic", pic);
                obj.put("vod_remarks", remark);
                list.put(obj);
            }
        } catch (Exception e) {
            // 打印异常日志，方便调试报错
            e.printStackTrace();
        }

        JSONObject ret = new JSONObject();
        ret.put("list", list);
        ret.put("page", pageNum);
        // 支持分页下滑加载，返回下一页页码
        ret.put("pagecount", pageNum + 1);
        ret.put("limit", list.length());
        ret.put("total", 9999);
        return ret.toString();
    }
}
