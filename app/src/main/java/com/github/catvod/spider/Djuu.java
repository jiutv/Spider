package com.github.catvod.spider;

import android.text.TextUtils;
import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.jnet.OkHttp;
import com.github.catvod.jnet.OkResult;
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

/**
 * Djuu 爬虫 - 还原版
 * 原站点: https://m.djuu.com (DJ呦呦网)
 * 功能: DJ 音乐资源爬取
 */
public class Djuu extends Spider {

    // ========== 常量定义 ==========

    /** 站点根地址 */
    private static final String BASE_URL = "https://m.djuu.com";

    /** 请求头: User-Agent */
    private static final String HEADER_UA = "User-Agent";
    /** 请求头: Referer */
    private static final String HEADER_REFERER = "Referer";

    /** User-Agent 值 */
    private static final String UA_VALUE = "Mozilla/5.0 (Linux; Android 13; V2049A Build/TP1A.220624.014; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/116.0.0.0 Mobile Safari/537.36";

    /** 分类ID */
    private static final String TYPE_DJLIST = "djlist";

    /** 第一页 */
    private static final String PAGE_1 = "1";

    /** 播放源名称 */
    private static final String PLAY_FROM = "DJ呦呦网";

    /** 固定图片 */
    private static final String DEFAULT_PIC = "/static/mobile/images/play/logo.png";

    /** 剧集分隔符 */
    private static final String SEP_EPISODE = "$";
    /** 多集分隔符 */
    private static final String SEP_MULTI = "#";
    /** vod_id 内部分隔符 */
    private static final String SEP_ID = "$$$";

    /** CSS 选择器 */
    private static final String SEL_PAGINATION = "div.fs-4";
    private static final String SEL_LIST_ITEM = "div#djuu-musiclist-djlist div.djuu-list-item";
    private static final String SEL_NAME = "div.me-2 > span";
    private static final String SEL_DATA_ID = "data-bs-id";
    private static final String SEL_FOLDER_ITEM = "div.my-3 div.mb-2";
    private static final String SEL_FOLDER_NAME = "div.fs-5";
    private static final String SEL_ONCLICK = "onclick";
    private static final String SEL_IMG = "img";
    private static final String SEL_SRC = "src";
    private static final String SEL_SEARCH_ITEM = "div#djuu-musiclist-search div.djuu-list-item";

    /** URL 路径 */
    private static final String PATH_DJLIST = "/djlist";
    private static final String PATH_PLAY = "/play/music";
    private static final String PATH_SEARCH = "/search?musicname=";

    /** URL 参数 */
    private static final String PARAM_PAGE = "&page=";

    /** 标签 */
    private static final String TAG_FOLDER = "folder";

    /** 错误信息 */
    private static final String ERR_DETAIL_FAIL = "详情解析失败";
    private static final String ERR_NO_PLAY = "文件夹为空";

    // ========== 静态缓存 ==========

    /** 分类页抓取的播放列表，供详情页使用 */
    private static final ArrayList<String> playListCache = new ArrayList<>();

    // ========== 私有方法 ==========

    /**
     * 构建通用请求头
     */
    private Map<String, String> getHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put(HEADER_UA, UA_VALUE);
        headers.put(HEADER_REFERER, BASE_URL);
        return headers;
    }

    /**
     * 补全图片地址
     * 如果地址不以 http/https 开头，则加上站点前缀
     */
    private String fixPic(String pic) {
        if (pic == null || pic.isEmpty()) {
            return "";
        }
        if (pic.startsWith("http://") || pic.startsWith("https://")) {
            return pic;
        }
        return BASE_URL.concat(pic);
    }

    // ========== Spider 接口实现 ==========

    /**
     * 首页内容
     */
    @Override
    public String homeContent(boolean filter) {
        List<Class> classes = new ArrayList<>();
        classes.add(new Class(TYPE_DJLIST, "曲库", PAGE_1));
        return Result.string(classes, new ArrayList<>());
    }

    /**
     * 分类内容
     *
     * @param tid    分类ID
     * @param pg     页码
     * @param filter 是否过滤
     * @param extend 扩展参数
     */
    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        Result result;
        boolean isDjlist = TYPE_DJLIST.equals(tid);
        ArrayList<String> cache = playListCache;

        if (!isDjlist) {
            // 普通分类页面
            String url = BASE_URL + tid.replace("{pg}", pg);
            String html = OkHttp.string(url, null, getHeaders());
            Document doc = Jsoup.parse(html);

            // 获取分页信息
            String pageInfo = doc.select(SEL_PAGINATION).text();

            cache.clear();
            ArrayList<Vod> list = new ArrayList<>();

            Elements items = doc.select(SEL_LIST_ITEM);
            for (Element item : items) {
                String name = item.select(SEL_NAME).text();
                String id = item.attr(SEL_DATA_ID);
                String pic = fixPic(DEFAULT_PIC);

                // 缓存播放列表项: name$id
                cache.add(name + SEP_EPISODE + id);

                // 构建 vod_id: id$$$name$$$pic
                String vodId = id + SEP_ID + name + SEP_ID + pic;
                list.add(new Vod(vodId, name, pic));
            }

            result = Result.get()
                    .page(Integer.parseInt(pg), Integer.MAX_VALUE, list.size(), Integer.MAX_VALUE)
                    .vod(list);

        } else {
            // DJ列表首页，只取第一页
            if (!PAGE_1.equals(pg)) {
                return Result.string(new ArrayList<>());
            }
            cache.clear();

            String url = BASE_URL + PATH_DJLIST;
            String html = OkHttp.string(url, null, getHeaders());
            Document doc = Jsoup.parse(html);

            ArrayList<Vod> list = new ArrayList<>();
            Elements items = doc.select(SEL_FOLDER_ITEM);

            for (Element item : items) {
                Element nameEl = item.select(SEL_FOLDER_NAME).first();
                if (nameEl == null) continue;

                // 解析 onclick 属性获取ID
                // 格式: onclick="...('1_1.html')..."
                String onclick = nameEl.attr(SEL_ONCLICK);
                String id = onclick.split("\\('")[1].split("'\)")[0]
                        .replace("1_1.html", "1_{pg}.html");

                String name = nameEl.text();
                String pic = fixPic(item.select(SEL_IMG).attr(SEL_SRC));

                Vod vod = new Vod(id, name, pic);
                vod.setVodTag(TAG_FOLDER);
                list.add(vod);
            }

            result = Result.get()
                    .page(Integer.parseInt(pg), Integer.MAX_VALUE, list.size(), Integer.MAX_VALUE)
                    .vod(list);
        }

        return result.string();
    }

    /**
     * 详情内容
     *
     * @param ids 视频ID列表
     */
    @Override
    public String detailContent(List<String> ids) {
        String id = ids.get(0);
        boolean hasSep = id.contains(SEP_ID);

        if (hasSep) {
            // 从分类/搜索进入，vod_id 包含 $$$ 分隔符
            try {
                String[] parts = id.split("\\Q" + SEP_ID + "\\E");
                Vod vod = new Vod();
                vod.setVodId(id);

                ArrayList<String> cache = playListCache;
                // 如果有缓存的播放列表，name 取 parts[3]，否则取 parts[1]
                vod.setVodName(cache.size() >= 1 && parts.length > 3 ? parts[3] : parts[1]);
                vod.setVodPic(parts[2]);
                vod.setVodPlayFrom(PLAY_FROM);

                // 如果没有缓存，构造单集: name$id
                if (cache.isEmpty()) {
                    vod.setVodPlayUrl(parts[1] + SEP_EPISODE + parts[0]);
                } else {
                    vod.setVodPlayUrl(TextUtils.join(SEP_MULTI, cache));
                }

                return Result.string(vod);
            } catch (Exception e) {
                return Result.error(ERR_DETAIL_FAIL);
            }
        }

        // 直接通过 ID 请求详情页面
        String url = BASE_URL + id.replace("{pg}", PAGE_1);
        String html = OkHttp.string(url, null, getHeaders());
        Document doc = Jsoup.parse(html);

        String name = doc.select(SEL_PAGINATION).text();
        ArrayList<String> episodes = new ArrayList<>();
        Elements items = doc.select(SEL_LIST_ITEM);
        String pic = fixPic(DEFAULT_PIC);

        for (Element item : items) {
            String epName = item.select(SEL_NAME).text();
            String epId = item.attr(SEL_DATA_ID);

            if (!epId.isEmpty()) {
                episodes.add(epName + SEP_EPISODE + epId);
            }
        }

        if (episodes.isEmpty()) {
            return Result.error(ERR_NO_PLAY);
        }

        Vod vod = new Vod(id);
        if (!name.isEmpty()) {
            vod.setVodName(name);
        }
        vod.setVodPic(pic);
        vod.setVodPlayFrom(PLAY_FROM);
        vod.setVodPlayUrl(TextUtils.join(SEP_MULTI, episodes));

        return Result.string(vod);
    }

    /**
     * 播放内容
     *
     * @param flag     播放源标识
     * @param id       视频ID
     * @param vipFlags VIP标识列表
     */
    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        // 构建 POST 参数: id=xxx
        Map<String, String> params = new HashMap<>();
        params.put("id", id);

        // 添加 Referer 头
        Map<String, String> headers = getHeaders();
        headers.put(HEADER_REFERER, BASE_URL + "/");

        // 请求播放地址
        String url = BASE_URL + PATH_PLAY;
        OkResult response = OkHttp.post(url, params, headers);
        String jsonStr = response.getBody();

        JSONObject root = new JSONObject(jsonStr);
        String playUrl = root.getJSONObject("data").optString("url");

        // 构建返回结果，带请求头
        return Result.get()
                .url(playUrl)
                .parse(0)
                .header(headers)
                .string();
    }

    /**
     * 搜索内容
     *
     * @param key   搜索关键词
     * @param quick 是否快速搜索
     * @param pg    页码
     */
    @Override
    public String searchContent(String key, boolean quick, String pg) {
        playListCache.clear();

        try {
            // URL 编码搜索词
            String encodedKey = URLEncoder.encode(key, "UTF-8");
            StringBuilder urlBuilder = new StringBuilder(BASE_URL + PATH_SEARCH + encodedKey);

            // 如果不是第一页，追加页码参数
            if (!PAGE_1.equals(pg)) {
                urlBuilder.append(PARAM_PAGE).append(pg);
            }

            String url = urlBuilder.toString();
            String html = OkHttp.string(url, null, getHeaders());
            Document doc = Jsoup.parse(html);

            ArrayList<Vod> list = new ArrayList<>();
            Elements items = doc.select(SEL_SEARCH_ITEM);

            for (Element item : items) {
                String name = item.select(SEL_NAME).text();
                String vid = item.attr(SEL_DATA_ID);
                String pic = fixPic(DEFAULT_PIC);

                // vod_id 格式: id$$$name$$$pic$$$
                String vodId = vid + SEP_ID + name + SEP_ID + pic + SEP_ID;
                list.add(new Vod(vodId, name, pic));
            }

            return Result.string(list);
        } catch (Exception e) {
            return Result.string(new ArrayList<>());
        }
    }
}
