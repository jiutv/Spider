package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Filter;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Util;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 护航影院 (qdhuhang.com) 爬虫
 *
 * <p>网站结构：海洋CMS + 佐佐1.0主题，自定义路由</p>
 * <ul>
 *   <li>分类页: /qdhyl/{id}.html (首页), /qdhyl/{id}-{page}.html</li>
 *   <li>详情页: /huhzc/{vid}.html</li>
 *   <li>播放页: /angplay/{vid}-{sid}-{nid}.html</li>
 *   <li>搜索: /search.php?searchword={kw} (可能被限制，走分类兜底)</li>
 * </ul>
 *
 * <p>init 配置：{@code {"url": "https://www.qdhuhang.com"}}</p>
 */
public class HuHang extends Spider {

    private String baseUrl = "https://www.qdhuhang.com";
    private HashMap<String, String> headers;

    // 主分类 id -> 名称
    private static final String[][] MAIN_CATEGORIES = {
            {"1", "电影"},
            {"2", "电视剧"},
            {"3", "综艺"},
            {"4", "动漫"},
            {"48", "短剧"},
    };

    // 子分类筛选: 主分类id -> 筛选列表
    private static final String[][] MOVIE_FILTERS = {
            {"5", "动作片"}, {"6", "爱情片"}, {"7", "科幻片"}, {"8", "恐怖片"},
            {"9", "战争片"}, {"10", "喜剧片"}, {"11", "纪录片"}, {"12", "剧情片"},
            {"32", "惊悚片"}, {"33", "悬疑片"},
    };

    private static final String[][] TV_FILTERS = {
            {"13", "国产剧"}, {"14", "港剧"}, {"15", "美剧"}, {"16", "韩剧"},
            {"25", "日剧"}, {"28", "台剧"}, {"29", "泰剧"}, {"36", "大陆剧"},
            {"37", "海外剧"},
    };

    @Override
    public void init(Context context, String extend) {
        if (!TextUtils.isEmpty(extend)) {
            try {
                JSONObject obj = new JSONObject(extend);
                if (obj.has("url")) baseUrl = obj.getString("url");
            } catch (Exception ignored) {
            }
        }
        // 去掉末尾斜杠
        if (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        headers = new HashMap<>();
        headers.put("User-Agent", Util.CHROME);
        headers.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        headers.put("Accept-Language", "zh-CN,zh;q=0.9");
    }

    // =====================================================================
    //  首页
    // =====================================================================

    @Override
    public String homeContent(boolean filter) throws Exception {
        ArrayList<Class> classes = new ArrayList<>();
        LinkedHashMap<String, List<Filter>> filters = new LinkedHashMap<>();

        // 主分类
        for (String[] cat : MAIN_CATEGORIES) {
            classes.add(new Class(cat[0], cat[1]));
        }

        // 筛选器
        filters.put("1", buildMovieFilters());
        filters.put("2", buildTvFilters());

        // 首页推荐 - 从电影分类第1页取
        String html = fetch(baseUrl + "/qdhyl/1.html");
        ArrayList<Vod> list = parseVodList(html);

        return Result.string(classes, list, filters);
    }

    // =====================================================================
    //  分类页
    // =====================================================================

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        // extend 中可能有分类筛选id，如 {"cate": "5"} 表示动作片
        String catId = tid;
        if (extend != null && extend.containsKey("cate") && !TextUtils.isEmpty(extend.get("cate"))) {
            catId = extend.get("cate");
        }

        String url;
        int page = 1;
        try {
            page = Integer.parseInt(pg);
        } catch (NumberFormatException ignored) {
        }

        if (page <= 1) {
            url = baseUrl + "/qdhyl/" + catId + ".html";
        } else {
            url = baseUrl + "/qdhyl/" + catId + "-" + page + ".html";
        }

        String html = fetch(url);
        ArrayList<Vod> list = parseVodList(html);
        int pageCount = parsePageCount(html);

        return Result.get().page(page, pageCount, 30, pageCount * 30).vod(list).string();
    }

    // =====================================================================
    //  详情页
    // =====================================================================

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String vid = ids.get(0);
        String url = baseUrl + "/huhzc/" + vid + ".html";
        String html = fetch(url);

        Vod vod = new Vod();
        vod.setVodId(vid);

        // 标题
        Pattern p = Pattern.compile("<h1[^>]*>([^<]+)</h1>");
        Matcher m = p.matcher(html);
        if (m.find()) vod.setVodName(m.group(1).trim());

        // 封面 - fed-deta-img
        p = Pattern.compile("class=\"fed-deta-img[^\"]*\"[^>]*>.*?<img[^>]*data-src=\"([^\"]+)\"", Pattern.DOTALL);
        m = p.matcher(html);
        if (m.find()) {
            String pic = m.group(1).trim();
            if (pic.startsWith("/")) pic = baseUrl + pic;
            vod.setVodPic(pic);
        } else {
            p = Pattern.compile("class=\"fed-deta-img[^\"]*\"[^>]*>.*?<img[^>]*src=\"([^\"]+)\"", Pattern.DOTALL);
            m = p.matcher(html);
            if (m.find()) {
                String pic = m.group(1).trim();
                if (pic.startsWith("/")) pic = baseUrl + pic;
                vod.setVodPic(pic);
            }
        }

        // 信息项: 分类、地区、年份、主演、导演、更新、简介
        String infoBlock = matchGroup(html, "class=\"fed-deta-info[^\"]*\"[^>]*>(.*?)</ul>", 1);
        if (!TextUtils.isEmpty(infoBlock)) {
            vod.setTypeName(extractInfo(infoBlock, "分类"));
            vod.setVodArea(extractInfo(infoBlock, "地区"));
            vod.setVodYear(extractInfo(infoBlock, "年份"));
            vod.setVodActor(extractInfo(infoBlock, "主演"));
            vod.setVodDirector(extractInfo(infoBlock, "导演"));
            vod.setVodRemarks(extractInfo(infoBlock, "更新"));
        }

        // 简介
        String desc = matchGroup(html, "class=\"fed-deta-content[^\"]*\"[^>]*>(.*?)</div>", 1);
        if (!TextUtils.isEmpty(desc)) {
            desc = desc.replaceAll("<[^>]+>", "").replace("&nbsp;", " ").trim();
            vod.setVodContent(desc);
        }

        // 播放线路名称
        String[] sourceNames = parseSourceNames(html);
        // 播放线路URL模式: /angplay/{vid}-{sid}-{nid}.html
        // 从剧集链接中解析
        Pattern epPattern = Pattern.compile("href=\"(/angplay/" + vid + "-(\\d+)-(\\d+)\\.html)\"[^>]*>([^<]+)</a>");
        Matcher epMatcher = epPattern.matcher(html);

        // 按 sid 分组
        HashMap<String, ArrayList<String[]>> sources = new LinkedHashMap<>();
        while (epMatcher.find()) {
            String epUrl = epMatcher.group(1);
            String sid = epMatcher.group(2);
            String epName = epMatcher.group(4).trim();
            if (!sources.containsKey(sid)) sources.put(sid, new ArrayList<String[]>());
            sources.get(sid).add(new String[]{epName, epUrl});
        }

        // 构建 play_from 和 play_url
        StringBuilder fromSb = new StringBuilder();
        StringBuilder urlSb = new StringBuilder();
        int srcIdx = 0;
        for (String sid : sources.keySet()) {
            if (fromSb.length() > 0) fromSb.append("$$$");
            String srcName = srcIdx < sourceNames.length ? sourceNames[srcIdx] : ("线路" + (srcIdx + 1));
            fromSb.append(srcName);

            StringBuilder epSb = new StringBuilder();
            for (String[] ep : sources.get(sid)) {
                if (epSb.length() > 0) epSb.append("#");
                // 格式: 集数$URL
                epSb.append(ep[0]).append("$").append(ep[1]);
            }
            if (urlSb.length() > 0) urlSb.append("$$$");
            urlSb.append(epSb);
            srcIdx++;
        }

        vod.setVodPlayFrom(fromSb.toString());
        vod.setVodPlayUrl(urlSb.toString());

        return Result.string(vod);
    }

    // =====================================================================
    //  播放页
    // =====================================================================

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        // id 是播放页相对路径，如 /angplay/361296-0-0.html
        String playUrl = id.startsWith("/") ? baseUrl + id : id;
        String html = fetch(playUrl);

        // 从 now 变量提取m3u8地址
        String videoUrl = matchGroup(html, "var\\s+now\\s*=\\s*['\"]([^'\"]+)['\"]", 1);

        if (TextUtils.isEmpty(videoUrl)) {
            // 兜底：找所有m3u8
            Pattern p = Pattern.compile("(https?://[^\\s\"'<>]+\\.m3u8[^\\s\"'<>]*)");
            Matcher m = p.matcher(html);
            if (m.find()) videoUrl = m.group(1);
        }

        if (TextUtils.isEmpty(videoUrl)) {
            return Result.error("解析播放地址失败");
        }

        Result result = Result.get().parse(0).url(videoUrl);
        if (videoUrl.toLowerCase().contains(".m3u8")) {
            result.m3u8();
        }
        return result.string();
    }

    // =====================================================================
    //  搜索
    // =====================================================================

    @Override
    public String searchContent(String keyword, boolean quick) throws Exception {
        // 搜索功能可能被限制，尝试标准搜索
        String url = baseUrl + "/search.php?searchword=" + java.net.URLEncoder.encode(keyword, "UTF-8");
        String html = fetch(url);

        ArrayList<Vod> list = parseVodList(html);
        if (list.size() > 0) return Result.string(list);

        // 兜底：返回空
        return Result.string(new ArrayList<>());
    }

    // =====================================================================
    //  工具方法
    // =====================================================================

    private String fetch(String url) {
        return OkHttp.string(url, headers);
    }

    private ArrayList<Vod> parseVodList(String html) {
        ArrayList<Vod> list = new ArrayList<>();
        if (TextUtils.isEmpty(html)) return list;

        // 匹配列表项 <li ...> ... <a href="/huhzc/xxx.html" ...> <img ... data-src="..." title="..."> ... </li>
        Pattern p = Pattern.compile(
                "<li[^>]*class=\"[^\"]*fed-list-item[^\"]*\"[^>]*>(.*?)</li>",
                Pattern.DOTALL
        );
        Matcher m = p.matcher(html);

        while (m.find()) {
            String item = m.group(1);
            try {
                // 链接和ID
                String urlMatch = matchGroup(item, "href=\"(/huhzc/(\\d+)\\.html)\"", 0);
                String idMatch = matchGroup(item, "href=\"/huhzc/(\\d+)\\.html\"", 1);
                if (TextUtils.isEmpty(idMatch)) continue;

                // 图片
                String pic = matchGroup(item, "data-src=\"([^\"]+)\"", 1);
                if (TextUtils.isEmpty(pic)) pic = matchGroup(item, "src=\"([^\"]+)\"", 1);
                if (!TextUtils.isEmpty(pic) && pic.startsWith("/")) pic = baseUrl + pic;

                // 标题 - 优先 title 属性
                String title = matchGroup(item, "title=\"([^\"]+)\"", 1);
                if (TextUtils.isEmpty(title)) {
                    title = matchGroup(item, "<a[^>]*>([^<]+)</a>", 1);
                }
                if (TextUtils.isEmpty(title)) continue;
                title = title.trim();

                // 备注/状态
                String remark = matchGroup(item, "class=\"[^\"]*fed-list-remarks[^\"]*\"[^>]*>([^<]+)<", 1);
                if (TextUtils.isEmpty(remark)) remark = "";

                Vod vod = new Vod(idMatch, title.trim(), pic, remark.trim());
                list.add(vod);
            } catch (Exception ignored) {
            }
        }

        return list;
    }

    private int parsePageCount(String html) {
        // 从尾页链接提取最大页码 /qdhyl/1-7739.html
        Pattern p = Pattern.compile("/qdhyl/\\d+-(\\d+)\\.html[^>]*>尾页");
        Matcher m = p.matcher(html);
        if (m.find()) {
            try { return Integer.parseInt(m.group(1)); } catch (NumberFormatException ignored) {}
        }
        // 兜底：从分页信息提取
        p = Pattern.compile("1/(\\d+)");
        m = p.matcher(html);
        if (m.find()) {
            try { return Integer.parseInt(m.group(1)); } catch (NumberFormatException ignored) {}
        }
        return 1;
    }

    private String[] parseSourceNames(String html) {
        // 从播放区域提取线路名称: 丹顶云 狮子云 斑马
        // 结构: $(".tabt span,.tabt2 span") ... 丹顶云 狮子云 斑马
        String playArea = matchGroup(html, "oooTab.*?</script>", 0);
        if (TextUtils.isEmpty(playArea)) {
            playArea = matchGroup(html, "class=\"[^\"]*fed-play[^\"]*\"[^>]*>(.*?)</div>", 1);
        }
        if (TextUtils.isEmpty(playArea)) return new String[0];

        // 提取 tab 中的名称
        ArrayList<String> names = new ArrayList<>();
        Pattern p = Pattern.compile("<span[^>]*>([^<]+)</span>");
        Matcher m = p.matcher(playArea);
        while (m.find()) {
            String name = m.group(1).trim();
            if (!TextUtils.isEmpty(name) && name.length() < 10 && !name.contains("播放")
                    && !name.contains("下载") && !name.contains("介绍")) {
                names.add(name);
            }
        }
        if (names.size() > 0) return names.toArray(new String[0]);

        // 兜底：从 data-sortid 或 tabt 中找
        String tabt = matchGroup(html, "class=\"tabt[^\"]*\"[^>]*>(.*?)</div>", 1);
        if (!TextUtils.isEmpty(tabt)) {
            p = Pattern.compile(">([^<>]{2,8})<");
            m = p.matcher(tabt);
            while (m.find()) {
                String name = m.group(1).trim();
                if (!TextUtils.isEmpty(name) && name.length() >= 2 && name.length() <= 8) {
                    names.add(name);
                }
            }
        }
        return names.toArray(new String[0]);
    }

    private String extractInfo(String block, String label) {
        // block 中格式: 分类：泰剧 地区：泰国 ...
        Pattern p = Pattern.compile(label + "：\\s*([^<]+)");
        Matcher m = p.matcher(block);
        if (m.find()) {
            String val = m.group(1).trim();
            val = val.replace("&nbsp;", " ").replaceAll("\\s+", " ").trim();
            // 去掉下一个标签前的内容
            int idx = val.indexOf("：");
            if (idx > 0) val = val.substring(0, idx);
            return val.trim();
        }
        return "";
    }

    private static String matchGroup(String input, String regex, int group) {
        try {
            Pattern p = Pattern.compile(regex, Pattern.DOTALL);
            Matcher m = p.matcher(input);
            if (m.find()) return m.group(group);
        } catch (Exception ignored) {
        }
        return "";
    }

    private ArrayList<Filter> buildMovieFilters() {
        ArrayList<Filter> list = new ArrayList<>();
        ArrayList<Filter.Value> values = new ArrayList<>();
        values.add(new Filter.Value("全部", "1"));
        for (String[] f : MOVIE_FILTERS) {
            values.add(new Filter.Value(f[1], f[0]));
        }
        list.add(new Filter("cate", "类型", values));
        return list;
    }

    private ArrayList<Filter> buildTvFilters() {
        ArrayList<Filter> list = new ArrayList<>();
        ArrayList<Filter.Value> values = new ArrayList<>();
        values.add(new Filter.Value("全部", "2"));
        for (String[] f : TV_FILTERS) {
            values.add(new Filter.Value(f[1], f[0]));
        }
        list.add(new Filter("cate", "类型", values));
        return list;
    }
}
