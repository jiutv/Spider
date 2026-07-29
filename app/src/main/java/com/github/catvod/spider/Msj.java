package com.github.catvod.spider;

import android.content.Context;

import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Util;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

/**
 * 美视界 TVBox Spider
 * 按 AppYsV2 模板风格改写，类名固定为 Msj，对应配置 csp_Msj。
 */
public class Msj extends Spider {

    private String baseUrl = "http://66.11.117.11:998";
    private String prefix = "/apptov5";
    private String token = "";
    private String ua = "Dart/2.19 (dart:io)";

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
        try {
            if (extend == null) return;
            extend = extend.trim();
            if (extend.startsWith("{")) {
                JSONObject ext = new JSONObject(extend);
                String extBase = cleanBase(ext.optString("baseUrl", ext.optString("url", "")));
                String extPrefix = cleanPrefix(ext.optString("prefix", ""));
                if (!extBase.isEmpty()) baseUrl = extBase;
                if (!extPrefix.isEmpty()) prefix = extPrefix;
                token = ext.optString("token", token);
                ua = ext.optString("ua", ua);
            } else if (extend.startsWith("http")) {
                baseUrl = cleanBase(extend);
            }
        } catch (Throwable e) {
            SpiderDebug.log(e);
        }
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        JSONObject result = new JSONObject();
        JSONArray classes = defaultClasses();

        try {
            JSONObject home = fetchJson("/v1/home/data");
            appendClasses(classes, home);

            JSONObject config = fetchJson("/v1/config/get");
            appendClasses(classes, config);

            result.put("class", classes);
            if (filter) result.put("filters", buildFilters(classes));

            JSONArray videos = findVodArray(home);
            if (videos.length() == 0) videos = findVodArray(fetchJson("/v1/vod/ranking"));
            result.put("list", toVodList(videos));
            return result.toString();
        } catch (Throwable e) {
            SpiderDebug.log(e);
            result.put("class", classes);
            if (filter) result.put("filters", buildFilters(classes));
            result.put("list", new JSONArray());
            return result.toString();
        }
    }

    @Override
    public String homeVideoContent() throws Exception {
        try {
            JSONObject result = new JSONObject();
            JSONArray list = findVodArray(fetchJson("/v1/vod/ranking"));
            if (list.length() == 0) list = findVodArray(fetchJson("/v1/vod/lists?page=1"));
            result.put("list", toVodList(list));
            return result.toString();
        } catch (Throwable e) {
            SpiderDebug.log(e);
            return emptyList();
        }
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        try {
            int page = safePage(pg);
            JSONObject json;

            if ("ranking".equals(tid)) {
                json = fetchJson("/v1/vod/ranking");
            } else if ("scheduling".equals(tid)) {
                json = fetchJson("/v1/vod/scheduling");
            } else {
                String path = withQuery("/v1/vod/lists",
                        "type_id", tid,
                        "area", "",
                        "lang", "",
                        "year", "",
                        "order", "time",
                        "type_name", "",
                        "page", String.valueOf(page),
                        "pageSize", "21",
                        "__platform", "android");
                path = appendFilterQuery(path, extend);
                json = fetchJson(path);
            }

            JSONArray videos = findVodArray(json);
            JSONObject result = new JSONObject();
            result.put("page", page);
            result.put("pagecount", guessPageCount(json, page));
            result.put("limit", 18);
            result.put("total", guessTotal(json, videos.length(), page));
            result.put("list", toVodList(videos));
            return result.toString();
        } catch (Throwable e) {
            SpiderDebug.log(e);
            return emptyList();
        }
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        try {
            String id = ids == null || ids.isEmpty() ? "" : ids.get(0);
            JSONObject src = loadDetail(id);
            JSONObject vod = toDetailVod(src, id);
            JSONArray list = new JSONArray();
            list.put(vod);
            JSONObject result = new JSONObject();
            result.put("list", list);
            return result.toString();
        } catch (Throwable e) {
            SpiderDebug.log(e);
            return emptyList();
        }
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        try {
            String wd = URLEncoder.encode(key, "UTF-8");
            JSONObject json = fetchJson(withQuery("/v1/search/lists", "keyword", wd, "wd", wd, "text", wd, "page", "1", "pg", "1"));
            JSONObject result = new JSONObject();
            result.put("list", toVodList(findVodArray(json)));
            return result.toString();
        } catch (Throwable e) {
            SpiderDebug.log(e);
            return emptyList();
        }
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        try {
            JSONObject result = new JSONObject();
            result.put("parse", 0);
            result.put("playUrl", "");

            if (isVideo(id)) {
                result.put("url", id);
                result.put("header", new JSONObject(getHeaders(id)).toString());
                return result.toString();
            }

            String play = resolve(id);
            if (!play.isEmpty() && isVideo(play)) {
                result.put("url", play);
                result.put("header", new JSONObject(getHeaders(play)).toString());
                return result.toString();
            }

            result.put("parse", 1);
            result.put("jx", "1");
            result.put("url", id);
            return result.toString();
        } catch (Throwable e) {
            SpiderDebug.log(e);
            return "{}";
        }
    }

    @Override
    public boolean manualVideoCheck() {
        return true;
    }

    @Override
    public boolean isVideoFormat(String url) {
        return isVideo(url);
    }

    private JSONObject fetchJson(String path) throws Exception {
        String url = api(path);
        SpiderDebug.log(url);
        String body = OkHttp.string(url, getHeaders(url));
        if (body == null || body.trim().isEmpty()) return new JSONObject();
        body = body.trim();
        if (body.startsWith("[")) {
            JSONObject wrapper = new JSONObject();
            wrapper.put("data", new JSONArray(body));
            return wrapper;
        }
        return new JSONObject(body);
    }

    private HashMap<String, String> getHeaders(String url) {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", ua);
        headers.put("Accept", "application/json,text/plain,*/*");
        headers.put("appto-local-uuid", "89219358-1cf1-4a45-8420-f684d7db5845");
        if (token != null && !token.isEmpty()) {
            headers.put("token", token);
            headers.put("authorization", token);
        }
        return headers;
    }

    private String api(String path) {
        if (path == null) return baseUrl;
        if (path.startsWith("http")) return path;
        String p = path.startsWith("/") ? path : "/" + path;
        if (p.startsWith("/apptov5")) return baseUrl + p;
        return baseUrl + prefix + p;
    }

    private JSONArray defaultClasses() {
        JSONArray arr = new JSONArray();
        addClass(arr, "home", "推荐");
        addClass(arr, "2", "电视剧");
        addClass(arr, "1", "电影");
        addClass(arr, "3", "综艺");
        addClass(arr, "4", "动漫");
        addClass(arr, "23", "少儿");
        addClass(arr, "36", "短剧");
        addClass(arr, "14", "港台剧");
        addClass(arr, "15", "日韩剧");
        addClass(arr, "16", "欧美剧");
        addClass(arr, "38", "电影解说");
        addClass(arr, "37", "纪录片");
        return arr;
    }

    private void appendClasses(JSONArray classes, Object node) {
        HashSet<String> seen = new HashSet<>();
        try {
            for (int i = 0; i < classes.length(); i++) {
                JSONObject item = classes.optJSONObject(i);
                if (item != null) seen.add(item.optString("type_id") + "|" + item.optString("type_name"));
            }
        } catch (Throwable ignored) {
        }
        collectClasses(node, classes, seen);
    }

    private void collectClasses(Object node, JSONArray out, HashSet<String> seen) {
        try {
            if (node instanceof JSONObject) {
                JSONObject obj = unwrap((JSONObject) node);
                String id = firstString(obj, "type_id", "typeId", "category_id", "categoryId", "class_id", "id");
                String name = firstString(obj, "type_name", "typeName", "category_name", "categoryName", "class_name", "name", "title");
                if (!id.isEmpty() && !name.isEmpty()) {
                    String key = id + "|" + name;
                    if (!seen.contains(key)) {
                        addClass(out, id, name);
                        seen.add(key);
                    }
                }
                Iterator<String> keys = obj.keys();
                while (keys.hasNext()) collectClasses(obj.opt(keys.next()), out, seen);
            } else if (node instanceof JSONArray) {
                JSONArray arr = (JSONArray) node;
                for (int i = 0; i < arr.length(); i++) collectClasses(arr.opt(i), out, seen);
            }
        } catch (Throwable ignored) {
        }
    }

    private JSONObject buildFilters(JSONArray classes) {
        JSONObject filters = new JSONObject();
        try {
            for (int i = 0; i < classes.length(); i++) {
                JSONObject item = classes.optJSONObject(i);
                if (item == null) continue;
                String typeId = item.optString("type_id", "");
                if (!typeId.isEmpty()) filters.put(typeId, filterForType(typeId));
            }
        } catch (Throwable ignored) {
        }
        return filters;
    }

    private JSONArray filterForType(String typeId) throws Exception {
        JSONArray arr = new JSONArray();
        if ("2".equals(typeId)) {
            arr.put(filterItem("class", "类型", "全部=", "古装=古装", "香港=香港", "台湾=台湾", "战争=战争", "青春=青春", "偶像=偶像", "爱情=爱情", "悬疑=悬疑", "犯罪=犯罪"));
            arr.put(filterItem("area", "地区", "全部=", "内地=内地", "大陆=大陆", "中国大陆=中国大陆", "国产=国产", "香港=香港", "台湾=台湾", "美国=美国", "韩国=韩国", "日本=日本", "泰国=泰国"));
            arr.put(filterItem("lang", "语言", "全部=", "国语=国语", "粤语=粤语", "广东话=广东话", "普通话=普通话", "英语=英语", "韩语=韩语", "日语=日语", "泰语=泰语"));
        } else if ("3".equals(typeId)) {
            arr.put(filterItem("class", "类型", "全部=", "选秀=选秀", "情感=情感", "访谈=访谈", "播报=播报", "旅游=旅游", "音乐=音乐", "真人秀=真人秀", "脱口秀=脱口秀"));
            arr.put(filterItem("area", "地区", "全部=", "内地=内地", "大陆=大陆", "香港=香港", "台湾=台湾", "美国=美国", "法国=法国", "韩国=韩国", "日本=日本"));
            arr.put(filterItem("lang", "语言", "全部=", "国语=国语", "粤语=粤语", "广东话=广东话", "普通话=普通话", "英语=英语", "韩语=韩语", "日语=日语"));
        } else if ("4".equals(typeId)) {
            arr.put(filterItem("class", "类型", "全部=", "情感=情感", "科幻=科幻", "热血=热血", "推理=推理", "搞笑=搞笑", "冒险=冒险", "奇幻=奇幻", "校园=校园"));
            arr.put(filterItem("area", "地区", "全部=", "内地=内地", "大陆=大陆", "香港=香港", "台湾=台湾", "美国=美国", "法国=法国", "日本=日本", "韩国=韩国"));
            arr.put(filterItem("lang", "语言", "全部=", "国语=国语", "粤语=粤语", "广东话=广东话", "普通话=普通话", "英语=英语", "日语=日语", "韩语=韩语"));
        } else if ("14".equals(typeId)) {
            arr.put(filterItem("class", "类型", "全部=", "香港=香港", "台湾=台湾", "警匪=警匪", "悬疑=悬疑", "罪案=罪案", "青春=青春", "爱情=爱情"));
            arr.put(filterItem("area", "地区", "全部=", "香港=香港", "台湾=台湾", "中国香港=中国香港", "中国台湾=中国台湾", "其它=其它"));
            arr.put(filterItem("lang", "语言", "全部=", "粤语=粤语", "国语=国语", "广东话=广东话", "普通话=普通话", "英语=英语"));
        } else if ("15".equals(typeId)) {
            arr.put(filterItem("class", "类型", "全部=", "日韩=日韩", "韩国=韩国", "爱情=爱情", "古装=古装", "战争=战争", "青春=青春", "悬疑=悬疑"));
            arr.put(filterItem("area", "地区", "全部=", "日本=日本", "韩国=韩国", "日韩=日韩", "泰国=泰国", "印度=印度"));
            arr.put(filterItem("lang", "语言", "全部=", "国语=国语", "英语=英语", "粤语=粤语", "闽南语=闽南语", "韩语=韩语", "日语=日语"));
        } else if ("16".equals(typeId)) {
            arr.put(filterItem("class", "类型", "全部=", "美剧=美剧", "连续=连续", "情=情", "奇幻=奇幻", "悬疑=悬疑", "欧美=欧美", "动作=动作", "科幻=科幻"));
            arr.put(filterItem("area", "地区", "全部=", "美国=美国", "法国=法国", "英国=英国", "泰国=泰国", "日本=日本", "韩国=韩国"));
            arr.put(filterItem("lang", "语言", "全部=", "国语=国语", "英语=英语", "泰语=泰语", "粤语=粤语", "闽南语=闽南语", "韩语=韩语"));
        } else {
            arr.put(filterItem("class", "类型", "全部=", "喜剧=喜剧", "爱情=爱情", "恐怖=恐怖", "动作=动作", "科幻=科幻", "灾难=灾难", "剧情=剧情", "战争=战争", "犯罪=犯罪", "动画=动画", "悬疑=悬疑", "纪录=纪录"));
            arr.put(filterItem("area", "地区", "全部=", "内地=内地", "大陆=大陆", "香港=香港", "台湾=台湾", "美国=美国", "法国=法国", "英国=英国", "韩国=韩国", "日本=日本", "泰国=泰国", "印度=印度"));
            arr.put(filterItem("lang", "语言", "全部=", "国语=国语", "英语=英语", "粤语=粤语", "闽南语=闽南语", "韩语=韩语", "日语=日语", "泰语=泰语"));
        }
        arr.put(filterItem("year", "年份", "全部=", "2026=2026", "2025=2025", "2024=2024", "2023=2023", "2022=2022", "2021=2021", "2020=2020", "2019=2019", "2018=2018", "2017=2017", "2016=2016"));
        arr.put(filterItem("排序", "排序", "按时间=time", "按人气=hits", "按评分=score"));
        return arr;
    }

    private JSONObject filterItem(String key, String name, String... pairs) throws Exception {
        JSONObject item = new JSONObject();
        JSONArray values = new JSONArray();
        item.put("key", key);
        item.put("name", name);
        for (String pair : pairs) {
            String[] kv = pair.split("=", 2);
            JSONObject value = new JSONObject();
            value.put("n", kv[0]);
            value.put("v", kv.length > 1 ? kv[1] : kv[0]);
            values.put(value);
        }
        item.put("value", values);
        return item;
    }

    private String appendFilterQuery(String path, HashMap<String, String> extend) {
        if (extend == null || extend.isEmpty()) return path;
        StringBuilder sb = new StringBuilder(path);
        appendFilterParam(sb, extend, "class", "type_name", "class", "vod_class", "type");
        appendFilterParam(sb, extend, "area", "area", "vod_area");
        appendFilterParam(sb, extend, "lang", "lang", "vod_lang");
        appendFilterParam(sb, extend, "year", "year", "vod_year", "start");
        appendFilterParam(sb, extend, "排序", "by", "sort", "order");
        return sb.toString();
    }

    private void appendFilterParam(StringBuilder sb, HashMap<String, String> extend, String key, String... requestKeys) {
        String value = extend.get(key);
        if (value == null || value.trim().isEmpty() || "全部".equals(value.trim())) return;
        for (String requestKey : requestKeys) appendQueryParam(sb, requestKey, value.trim());
    }

    private JSONObject loadDetail(String id) {
        String[] paths = new String[]{
                withQuery("/v1/vod/detail", "id", id),
                withQuery("/v1/vod/detail", "vod_id", id),
                withQuery("/v1/vod/lists", "id", id),
                withQuery("/v1/vod/lists", "vod_id", id),
                withQuery("/v1/vod/getRelVodLists", "id", id)
        };
        for (String path : paths) {
            try {
                JSONObject found = findVodObject(fetchJson(path), id);
                if (found.length() > 0) return found;
            } catch (Throwable ignored) {
            }
        }
        JSONObject fallback = new JSONObject();
        try {
            fallback.put("vod_id", id);
            fallback.put("vod_name", id);
            fallback.put("vod_play_from", "默认");
            fallback.put("vod_play_url", "播放$" + id);
        } catch (Throwable ignored) {
        }
        return fallback;
    }

    private String resolve(String url) {
        String[] paths = new String[]{
                withQuery("/v1/parsing/proxy", "url", url),
                withQuery("/v2/parsing/proxy", "url", url),
                withQuery("/v1/parsing/proxy", "play_url", url),
                withQuery("/v2/parsing/proxy", "play_url", url)
        };
        for (String path : paths) {
            try {
                JSONObject json = fetchJson(path);
                String play = firstString(json, "url", "play_url", "playUrl", "video_url", "videoUrl", "vod_url", "vodUrl");
                if (isVideo(play)) return play;
            } catch (Throwable ignored) {
            }
        }
        return "";
    }

    private JSONArray findVodArray(Object node) {
        JSONArray out = new JSONArray();
        collectVodArray(node, out);
        return out;
    }

    private boolean collectVodArray(Object node, JSONArray out) {
        try {
            if (node instanceof JSONArray) {
                JSONArray arr = (JSONArray) node;
                int score = 0;
                for (int i = 0; i < arr.length(); i++) {
                    Object item = arr.opt(i);
                    if (item instanceof JSONObject && looksLikeVod((JSONObject) item)) score++;
                }
                if (score > 0) {
                    for (int i = 0; i < arr.length(); i++) {
                        Object item = arr.opt(i);
                        if (item instanceof JSONObject) out.put(item);
                    }
                    return true;
                }
                for (int i = 0; i < arr.length(); i++) {
                    if (collectVodArray(arr.opt(i), out)) return true;
                }
            } else if (node instanceof JSONObject) {
                JSONObject obj = unwrap((JSONObject) node);
                Iterator<String> keys = obj.keys();
                while (keys.hasNext()) {
                    if (collectVodArray(obj.opt(keys.next()), out)) return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private JSONObject findVodObject(Object node, String id) {
        try {
            if (node instanceof JSONObject) {
                JSONObject obj = unwrap((JSONObject) node);
                if (looksLikeVod(obj)) {
                    String vodId = vodId(obj);
                    if (id == null || id.isEmpty() || id.equals(vodId)) return obj;
                }
                Iterator<String> keys = obj.keys();
                while (keys.hasNext()) {
                    JSONObject found = findVodObject(obj.opt(keys.next()), id);
                    if (found.length() > 0) return found;
                }
            } else if (node instanceof JSONArray) {
                JSONArray arr = (JSONArray) node;
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject found = findVodObject(arr.opt(i), id);
                    if (found.length() > 0) return found;
                }
            }
        } catch (Throwable ignored) {
        }
        return new JSONObject();
    }

    private JSONArray toVodList(JSONArray arr) {
        JSONArray list = new JSONArray();
        for (int i = 0; i < arr.length(); i++) {
            try {
                JSONObject src = arr.optJSONObject(i);
                if (src == null) continue;
                JSONObject vod = new JSONObject();
                vod.put("vod_id", vodId(src));
                vod.put("vod_name", vodName(src));
                vod.put("vod_pic", firstString(src, "vod_pic", "vodPic", "vod_cover", "vodCover", "video_cover", "videoCover", "pic", "cover", "img", "image"));
                vod.put("vod_remarks", firstString(src, "vod_remarks", "vodRemarks", "vod_sub", "vodSub", "remark", "remarks", "state", "score", "vod_score", "vodScore"));
                if (!vod.optString("vod_id").isEmpty() && !vod.optString("vod_name").isEmpty()) list.put(vod);
            } catch (Throwable ignored) {
            }
        }
        return list;
    }

    private JSONObject toDetailVod(JSONObject src, String fallbackId) {
        JSONObject vod = new JSONObject();
        try {
            String id = vodId(src);
            if (id.isEmpty()) id = fallbackId;
            vod.put("vod_id", id);
            vod.put("vod_name", vodName(src).isEmpty() ? id : vodName(src));
            vod.put("vod_pic", firstString(src, "vod_pic", "vodPic", "vod_cover", "vodCover", "video_cover", "videoCover", "pic", "cover", "img", "image"));
            vod.put("type_name", firstString(src, "type_name", "typeName", "vod_type_name", "vodTypeName", "vod_class", "vodClass", "class"));
            vod.put("vod_year", firstString(src, "vod_year", "vodYear", "year"));
            vod.put("vod_area", firstString(src, "vod_area", "vodArea", "area"));
            vod.put("vod_lang", firstString(src, "vod_lang", "vodLang", "lang"));
            vod.put("vod_actor", firstString(src, "vod_actor", "vodActor", "actor"));
            vod.put("vod_director", firstString(src, "vod_director", "vodDirector", "director"));
            vod.put("vod_content", firstString(src, "vod_content", "vodContent", "vod_blurb", "vodBlurb", "content", "desc", "description", "intro"));
            String[] play = buildPlay(src, id);
            vod.put("vod_play_from", play[0].isEmpty() ? "默认" : play[0]);
            vod.put("vod_play_url", play[1].isEmpty() ? "播放$" + id : play[1]);
        } catch (Throwable ignored) {
        }
        return vod;
    }

    private String[] buildPlay(JSONObject src, String fallbackId) {
        ArrayList<String> from = new ArrayList<>();
        ArrayList<String> urls = new ArrayList<>();
        try {
            Object playNode = first(src, "vod_play_list", "vodPlayList", "vod_url_with_player", "vodUrlWithPlayer", "playlist", "playList", "urls", "video_chunks", "videoChunks", "play_url", "playUrl", "vod_url", "vodUrl", "url");
            parsePlayNode(playNode, from, urls);
            if (urls.isEmpty()) urls.add("播放$" + fallbackId);
        } catch (Throwable ignored) {
        }
        return new String[]{join(from, "$$$"), join(urls, "$$$")};
    }

    private void parsePlayNode(Object node, ArrayList<String> from, ArrayList<String> urls) {
        try {
            if (node instanceof JSONArray) {
                JSONArray arr = (JSONArray) node;
                boolean simpleEpisodes = true;
                for (int i = 0; i < arr.length(); i++) {
                    Object item = arr.opt(i);
                    if (!(item instanceof String) && !(item instanceof JSONObject)) simpleEpisodes = false;
                }
                if (simpleEpisodes && arr.length() > 0 && !containsSourceObject(arr)) {
                    from.add("默认");
                    urls.add(episodesToString(arr));
                    return;
                }
                for (int i = 0; i < arr.length(); i++) parsePlayNode(arr.opt(i), from, urls);
            } else if (node instanceof JSONObject) {
                JSONObject obj = (JSONObject) node;
                String source = firstString(obj, "code", "name", "video_set_name", "videoSetName", "from", "source", "player", "show");
                Object child = first(obj, "urls", "list", "playlist", "playList", "video_chunks", "videoChunks", "items");
                if (child != null) {
                    from.add(source.isEmpty() ? "默认" : source);
                    urls.add(episodesToString(child));
                    return;
                }
                String url = firstString(obj, "url", "play_url", "playUrl", "video_set_url", "videoSetUrl", "vod_url", "vodUrl");
                if (!url.isEmpty()) {
                    from.add(source.isEmpty() ? "默认" : source);
                    urls.add(episodeName(obj, 1) + "$" + url);
                }
            } else if (node instanceof String) {
                String s = (String) node;
                if (!s.isEmpty()) {
                    from.add("默认");
                    urls.add(s.contains("$") ? s : "播放$" + s);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private String episodesToString(Object node) {
        ArrayList<String> eps = new ArrayList<>();
        try {
            if (node instanceof JSONArray) {
                JSONArray arr = (JSONArray) node;
                for (int i = 0; i < arr.length(); i++) {
                    Object item = arr.opt(i);
                    if (item instanceof JSONObject) {
                        JSONObject obj = (JSONObject) item;
                        String url = firstString(obj, "url", "play_url", "playUrl", "video_set_url", "videoSetUrl", "vod_url", "vodUrl");
                        if (!url.isEmpty()) eps.add(episodeName(obj, i + 1) + "$" + url);
                    } else if (item instanceof String) {
                        String s = (String) item;
                        eps.add(s.contains("$") ? s : ("第" + (i + 1) + "集$" + s));
                    }
                }
            } else if (node instanceof String) {
                String s = (String) node;
                eps.add(s.contains("$") ? s : "播放$" + s);
            }
        } catch (Throwable ignored) {
        }
        return join(eps, "#");
    }

    private boolean containsSourceObject(JSONArray arr) {
        for (int i = 0; i < arr.length(); i++) {
            Object item = arr.opt(i);
            if (item instanceof JSONObject) {
                JSONObject obj = (JSONObject) item;
                if (obj.has("urls") || obj.has("list") || obj.has("playlist") || obj.has("video_chunks") || obj.has("items")) return true;
            }
        }
        return false;
    }

    private String episodeName(JSONObject obj, int index) {
        String name = firstString(obj, "name", "title", "video_set_name", "videoSetName", "vod_name", "vodName");
        return name.isEmpty() ? "第" + index + "集" : name;
    }

    private boolean looksLikeVod(JSONObject obj) {
        return obj.has("vod_id") || obj.has("vod_name") || obj.has("video_id") || obj.has("video_name")
                || obj.has("vodName") || obj.has("vodPic") || obj.has("nextlink") || obj.has("title")
                || obj.has("vod_play_list") || obj.has("play_url") || obj.has("playUrl");
    }

    private String vodId(JSONObject obj) {
        String id = firstString(obj, "vod_id", "vodId", "video_id", "videoId", "id", "nid", "vod_nid", "vodNid", "nextlink");
        return id.isEmpty() ? firstString(obj, "url", "play_url", "playUrl") : id;
    }

    private String vodName(JSONObject obj) {
        return firstString(obj, "vod_name", "vodName", "video_name", "videoName", "name", "title");
    }

    private JSONObject unwrap(JSONObject obj) {
        try {
            if (obj.has("data") && obj.opt("data") instanceof JSONObject) return obj.optJSONObject("data");
            if (obj.has("result") && obj.opt("result") instanceof JSONObject) return obj.optJSONObject("result");
        } catch (Throwable ignored) {
        }
        return obj;
    }

    private Object first(JSONObject obj, String... keys) {
        for (String key : keys) {
            if (obj.has(key) && !obj.isNull(key)) return obj.opt(key);
        }
        return null;
    }

    private String firstString(JSONObject obj, String... keys) {
        for (String key : keys) {
            String value = obj.optString(key, "");
            if (value != null && !value.trim().isEmpty() && !"null".equalsIgnoreCase(value.trim())) return value.trim();
        }
        return "";
    }

    private int guessPageCount(JSONObject json, int current) {
        try {
            int totalPage = json.optInt("pagecount", json.optInt("page_count", json.optInt("totalPage", json.optInt("totalpage", 0))));
            if (totalPage > 0) return totalPage;
            JSONObject data = json.optJSONObject("data");
            if (data != null) {
                int total = data.optInt("total", 0);
                int limit = data.optInt("limit", 18);
                if (total > 0 && limit > 0) return total % limit == 0 ? total / limit : total / limit + 1;
            }
        } catch (Throwable ignored) {
        }
        return current + 1;
    }

    private int guessTotal(JSONObject json, int size, int page) {
        int total = json.optInt("total", json.optInt("count", 0));
        return total > 0 ? total : page * Math.max(size, 18);
    }

    private String withQuery(String path, String... kv) {
        StringBuilder sb = new StringBuilder(path);
        sb.append(path.contains("?") ? "&" : "?");
        for (int i = 0; i + 1 < kv.length; i += 2) {
            if (i > 0) sb.append("&");
            sb.append(enc(kv[i])).append("=").append(enc(kv[i + 1]));
        }
        return sb.toString();
    }

    private void appendQueryParam(StringBuilder sb, String key, String value) {
        sb.append(sb.indexOf("?") >= 0 ? "&" : "?");
        sb.append(enc(key)).append("=").append(enc(value));
    }

    private String enc(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, "UTF-8");
        } catch (Throwable e) {
            return value == null ? "" : value;
        }
    }

    private int safePage(String pg) {
        try {
            return Math.max(1, Integer.parseInt(pg));
        } catch (Throwable e) {
            return 1;
        }
    }

    private boolean isVideo(String url) {
        if (url == null) return false;
        try {
            if (Util.isVideoFormat(url)) return true;
        } catch (Throwable ignored) {
        }
        String v = url.toLowerCase();
        return v.startsWith("http") && (v.contains(".m3u8") || v.contains(".mp4") || v.contains(".flv") || v.contains(".ts"));
    }

    private String cleanBase(String value) {
        if (value == null) return "";
        value = value.trim();
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
    }

    private String cleanPrefix(String value) {
        if (value == null || value.trim().isEmpty()) return "";
        value = value.trim();
        if (!value.startsWith("/")) value = "/" + value;
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
    }

    private void addClass(JSONArray classes, String id, String name) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("type_id", id);
            obj.put("type_name", name);
            classes.put(obj);
        } catch (Throwable ignored) {
        }
    }

    private String join(ArrayList<String> list, String sep) {
        StringBuilder sb = new StringBuilder();
        for (String item : list) {
            if (item == null || item.isEmpty()) continue;
            if (sb.length() > 0) sb.append(sep);
            sb.append(item);
        }
        return sb.toString();
    }

    private String emptyList() {
        try {
            JSONObject result = new JSONObject();
            result.put("list", new JSONArray());
            return result.toString();
        } catch (Throwable e) {
            return "{\"list\":[]}";
        }
    }
}

