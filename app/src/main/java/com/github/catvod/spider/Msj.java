import android.content.Context;

import com.github.catvod.crawler.Spider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * TVBox Spider for the uploaded Flutter APK.
 *
 * APK analysis result:
 * 1. The app is a Flutter video app related to "appto/apptov5".
 * 2. Business paths found in libapp.so:
 *    /v1/home/data
 *    /v1/config/get
 *    /v1/vod/lists
 *    /v1/vod/ranking
 *    /v1/vod/rankingLists?id=
 *    /v1/vod/getRelVodLists
 *    /v1/vod/scheduling
 *    /v1/vod/schedulingLists?w=
 *    /v1/search/config
 *    /v1/search/lists
 *    /v1/parsing/proxy
 *    /v2/parsing/proxy
 * 3. The APK contains baseUrl cache/update strings, such as "Use cached baseUrl:",
 *    "Save newer baseUrl:" and "$$baseUrl$$", but no clear real business domain.
 * 4. The APK also contains "HttpClient.encryptDecodeInInterceptor", so some server
 *    responses may be encrypted depending on the active server configuration.
 *
 * Usage:
 * Put this Java file into your TVBox spider project and configure extend:
 *
 *   {"baseUrl":"http://66.11.117.11:998","prefix":"/apptov5","token":""}
 *
 * If the real API requires the /apptov5 prefix, use:
 *
 *   {"baseUrl":"https://your-real-domain.com","prefix":"/apptov5","token":""}
 *
 * If extend is only a URL, for example:
 *
 *   https://your-real-domain.com
 *
 * it will be used as baseUrl directly.
 */
public class Msj extends Spider {

    private final OkHttpClient client = new OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build();

    private String baseUrl = "http://66.11.117.11:998";
    private String prefix = "/apptov5";
    private String token = "";
    private String userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/101.0.4951.54 Safari/537.36 Edg/101.0.1210.39";

    @Override
    public void init(Context context, String extend) {
        try {
            if (extend == null) return;
            extend = extend.trim();
            if (extend.startsWith("{")) {
                JSONObject ext = new JSONObject(extend);
                String extBaseUrl = cleanBase(ext.optString("baseUrl", ext.optString("url", "")));
                String extPrefix = cleanPrefix(ext.optString("prefix", ""));
                if (extBaseUrl.length() > 0) baseUrl = extBaseUrl;
                if (extPrefix.length() > 0) prefix = extPrefix;
                token = ext.optString("token", "");
                userAgent = ext.optString("ua", userAgent);
            } else if (extend.startsWith("http")) {
                baseUrl = cleanBase(extend);
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public String homeContent(boolean filter) {
        try {
            JSONObject result = new JSONObject();
            JSONArray classes = new JSONArray();

            JSONObject home = fetchJson("/v1/home/data");
            appendClasses(classes, home);

            if (classes.length() == 0) {
                JSONObject config = fetchJson("/v1/config/get");
                appendClasses(classes, config);
            }

            if (classes.length() == 0) {
                addClass(classes, "ranking", "排行榜");
                addClass(classes, "scheduling", "追剧日程");
                addClass(classes, "latest", "最新");
            }

            result.put("class", classes);
            if (filter) result.put("filters", buildFilters(classes));

            JSONArray videos = findVodArray(home);
            if (videos.length() == 0) videos = findVodArray(fetchJson("/v1/vod/ranking"));
            result.put("list", toVodList(videos));

            return result.toString();
        } catch (Throwable e) {
            return emptyList();
        }
    }

    @Override
    public String homeVideoContent() {
        try {
            JSONObject json = fetchJson("/v1/vod/ranking");
            JSONObject result = new JSONObject();
            result.put("list", toVodList(findVodArray(json)));
            return result.toString();
        } catch (Throwable e) {
            return emptyList();
        }
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        try {
            int page = safePage(pg);
            JSONObject result = new JSONObject();
            JSONObject json;

            if ("ranking".equals(tid)) {
                json = fetchJson("/v1/vod/ranking");
            } else if ("scheduling".equals(tid)) {
                json = fetchJson("/v1/vod/scheduling");
            } else if ("latest".equals(tid)) {
                json = fetchJson(appendFilterQuery(withQuery("/v1/vod/lists", "page", String.valueOf(page)), extend));
            } else {
                String path = appendFilterQuery(withQuery("/v1/vod/lists",
                        "type_id", tid,
                        "category_id", tid,
                        "categoryId", tid,
                        "page", String.valueOf(page)), extend);
                json = fetchJson(path);
            }

            JSONArray videos = findVodArray(json);
            result.put("page", page);
            result.put("pagecount", guessPageCount(json, page));
            result.put("limit", 20);
            result.put("total", guessTotal(json, videos.length(), page));
            result.put("list", toVodList(videos));
            return result.toString();
        } catch (Throwable e) {
            return emptyList();
        }
    }

    @Override
    public String detailContent(List<String> ids) {
        try {
            String id = ids == null || ids.isEmpty() ? "" : ids.get(0);
            JSONObject item = loadDetail(id);
            JSONArray list = new JSONArray();
            list.put(toDetailVod(item, id));
            JSONObject result = new JSONObject();
            result.put("list", list);
            return result.toString();
        } catch (Throwable e) {
            return emptyList();
        }
    }

    @Override
    public String searchContent(String key, boolean quick) {
        try {
            JSONObject json = fetchJson(withQuery("/v1/search/lists", "keyword", key, "page", "1"));
            JSONObject result = new JSONObject();
            result.put("list", toVodList(findVodArray(json)));
            return result.toString();
        } catch (Throwable e) {
            return emptyList();
        }
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        try {
            JSONObject result = new JSONObject();
            result.put("parse", 0);
            result.put("playUrl", "");
            result.put("header", new JSONObject(headers()).toString());

            if (isMediaUrl(id)) {
                result.put("url", id);
                return result.toString();
            }

            String play = tryResolve(id);
            result.put("url", play.length() == 0 ? id : play);
            return result.toString();
        } catch (Throwable e) {
            return "{}";
        }
    }

    private JSONObject loadDetail(String id) {
        List<String> candidates = new ArrayList<>();
        candidates.add(withQuery("/v1/vod/lists", "id", id));
        candidates.add(withQuery("/v1/vod/lists", "vod_id", id));
        candidates.add(withQuery("/v1/vod/lists", "nid", id));
        candidates.add(withQuery("/v1/vod/getRelVodLists", "id", id));

        for (String path : candidates) {
            try {
                JSONObject json = fetchJson(path);
                JSONObject found = findVodObject(json, id);
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

    private String tryResolve(String url) {
        String[] apis = {
                withQuery("/v1/parsing/proxy", "url", url),
                withQuery("/v2/parsing/proxy", "url", url),
                withQuery("/v1/parsing/proxy", "play_url", url),
                withQuery("/v2/parsing/proxy", "play_url", url)
        };

        for (String api : apis) {
            try {
                JSONObject json = fetchJson(api);
                String play = firstString(json,
                        "url", "play_url", "playUrl", "video_url", "videoUrl",
                        "vod_url", "vodUrl", "video_set_url", "videoSetUrl");
                if (isMediaUrl(play)) return play;
            } catch (Throwable ignored) {
            }
        }
        return "";
    }

    private JSONObject fetchJson(String path) throws Exception {
        String body = get(api(path));
        body = decryptIfNeeded(body);
        if (body == null || body.trim().length() == 0) return new JSONObject();
        body = body.trim();
        if (body.startsWith("[")) {
            JSONObject wrapper = new JSONObject();
            wrapper.put("data", new JSONArray(body));
            return wrapper;
        }
        return new JSONObject(body);
    }

    private String get(String url) throws Exception {
        Request.Builder builder = new Request.Builder().url(url).get();
        for (Map.Entry<String, String> entry : headers().entrySet()) {
            builder.addHeader(entry.getKey(), entry.getValue());
        }
        Response response = client.newCall(builder.build()).execute();
        if (response.body() == null) return "";
        return response.body().string();
    }

    /**
     * The APK has "HttpClient.encryptDecodeInInterceptor" and RSA-related strings.
     * If your active server returns encrypted payloads, add the real decryption here.
     */
    private String decryptIfNeeded(String body) {
        return body;
    }

    private Map<String, String> headers() {
        Map<String, String> map = new HashMap<>();
        map.put("User-Agent", userAgent);
        map.put("Accept", "application/json,text/plain,*/*");
        map.put("Content-Type", "application/json");
        map.put("APP token", token);
        map.put("__APPTO", "1");
        map.put("__deviceId", "tvbox");
        if (token != null && token.length() > 0) {
            map.put("token", token);
            map.put("authorization", token);
        }
        return map;
    }

    private String api(String path) {
        if (path.startsWith("http")) return path;
        String p = path.startsWith("/") ? path : "/" + path;
        if (baseUrl.length() == 0) return p;
        if (p.startsWith("/apptov5")) return baseUrl + p;
        return baseUrl + prefix + p;
    }

    private JSONObject buildFilters(JSONArray classes) {
        JSONObject filters = new JSONObject();
        try {
            JSONArray common = new JSONArray();
            common.put(filterItem("class", "类型",
                    "全部:", "动作:动作", "喜剧:喜剧", "爱情:爱情", "科幻:科幻", "恐怖:恐怖", "剧情:剧情", "战争:战争", "悬疑:悬疑", "动画:动画", "综艺:综艺", "纪录:纪录"));
            common.put(filterItem("area", "地区",
                    "全部:", "大陆:大陆", "香港:香港", "台湾:台湾", "美国:美国", "韩国:韩国", "日本:日本", "泰国:泰国", "英国:英国", "法国:法国", "印度:印度", "其他:其他"));
            common.put(filterItem("year", "年份",
                    "全部:", "2026:2026", "2025:2025", "2024:2024", "2023:2023", "2022:2022", "2021:2021", "2020:2020", "2019:2019", "2018:2018", "2017:2017", "2016:2016"));
            common.put(filterItem("lang", "语言",
                    "全部:", "国语:国语", "粤语:粤语", "英语:英语", "韩语:韩语", "日语:日语", "泰语:泰语", "其他:其他"));
            common.put(filterItem("sort", "排序",
                    "默认:", "最新:time", "最热:hits", "评分:score"));

            if (classes == null || classes.length() == 0) {
                filters.put("latest", common);
                return filters;
            }

            for (int i = 0; i < classes.length(); i++) {
                Object item = classes.opt(i);
                if (!(item instanceof JSONObject)) continue;
                String typeId = ((JSONObject) item).optString("type_id", "");
                if (typeId.length() > 0) filters.put(typeId, common);
            }
        } catch (Throwable ignored) {
        }
        return filters;
    }

    private JSONObject filterItem(String key, String name, String... pairs) {
        JSONObject obj = new JSONObject();
        JSONArray values = new JSONArray();
        try {
            obj.put("key", key);
            obj.put("name", name);
            for (String pair : pairs) {
                String[] parts = pair.split(":", 2);
                JSONObject value = new JSONObject();
                value.put("n", parts[0]);
                value.put("v", parts.length > 1 ? parts[1] : parts[0]);
                values.put(value);
            }
            obj.put("value", values);
        } catch (Throwable ignored) {
        }
        return obj;
    }

    private String appendFilterQuery(String path, HashMap<String, String> extend) {
        if (extend == null || extend.isEmpty()) return path;
        StringBuilder sb = new StringBuilder(path);
        appendFilterParam(sb, extend, "class", "class", "vod_class", "vodClass");
        appendFilterParam(sb, extend, "area", "area", "vod_area", "vodArea");
        appendFilterParam(sb, extend, "year", "year", "vod_year", "vodYear");
        appendFilterParam(sb, extend, "lang", "lang", "vod_lang", "vodLang");
        appendFilterParam(sb, extend, "sort", "sort", "order", "by");
        return sb.toString();
    }

    private void appendFilterParam(StringBuilder sb, HashMap<String, String> extend, String key, String... requestKeys) {
        String value = extend.get(key);
        if (value == null || value.trim().length() == 0 || "全部".equals(value.trim())) return;
        for (String requestKey : requestKeys) appendQueryParam(sb, requestKey, value.trim());
    }

    private void appendQueryParam(StringBuilder sb, String key, String value) {
        sb.append(sb.indexOf("?") >= 0 ? "&" : "?");
        sb.append(enc(key)).append("=").append(enc(value));
    }

    private void appendClasses(JSONArray classes, JSONObject json) {
        HashSet<String> seen = new HashSet<>();
        collectClasses(json, classes, seen);
    }

    private void collectClasses(Object node, JSONArray out, HashSet<String> seen) {
        try {
            if (node instanceof JSONObject) {
                JSONObject obj = unwrapData((JSONObject) node);
                String id = firstString(obj, "type_id", "typeId", "category_id", "categoryId", "id", "vod_class");
                String name = firstString(obj, "type_name", "typeName", "category_name", "categoryName", "name", "title", "vod_class");
                if (id.length() > 0 && name.length() > 0 && !seen.contains(id + name)) {
                    addClass(out, id, name);
                    seen.add(id + name);
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

    private void addClass(JSONArray classes, String id, String name) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("type_id", id);
            obj.put("type_name", name);
            classes.put(obj);
        } catch (Throwable ignored) {
        }
    }

    private JSONArray findVodArray(Object node) {
        JSONArray found = new JSONArray();
        collectVodArray(node, found);
        return found;
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
                JSONObject obj = unwrapData((JSONObject) node);
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
                JSONObject obj = unwrapData((JSONObject) node);
                if (looksLikeVod(obj)) {
                    String oid = vodId(obj);
                    if (id == null || id.length() == 0 || id.equals(oid)) return obj;
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
                Object value = arr.opt(i);
                if (!(value instanceof JSONObject)) continue;
                JSONObject src = (JSONObject) value;
                JSONObject vod = new JSONObject();
                vod.put("vod_id", vodId(src));
                vod.put("vod_name", vodName(src));
                vod.put("vod_pic", firstString(src, "vod_pic", "vodPic", "vod_cover", "vodCover", "video_cover", "videoCover", "pic", "cover", "img", "image"));
                vod.put("vod_remarks", firstString(src, "vod_remarks", "vodRemarks", "vod_sub", "vodSub", "remark", "remarks", "score", "vod_score", "vodScore"));
                list.put(vod);
            } catch (Throwable ignored) {
            }
        }
        return list;
    }

    private JSONObject toDetailVod(JSONObject src, String fallbackId) {
        JSONObject vod = new JSONObject();
        try {
            String id = vodId(src);
            if (id.length() == 0) id = fallbackId;

            vod.put("vod_id", id);
            vod.put("vod_name", vodName(src));
            vod.put("vod_pic", firstString(src, "vod_pic", "vodPic", "vod_cover", "vodCover", "video_cover", "videoCover", "pic", "cover", "img", "image"));
            vod.put("type_name", firstString(src, "type_name", "typeName", "vod_type_name", "vodTypeName", "vod_class", "vodClass"));
            vod.put("vod_year", firstString(src, "vod_year", "vodYear", "year"));
            vod.put("vod_area", firstString(src, "vod_area", "vodArea", "area"));
            vod.put("vod_lang", firstString(src, "vod_lang", "vodLang", "lang"));
            vod.put("vod_actor", firstString(src, "vod_actor", "vodActor", "actor"));
            vod.put("vod_director", firstString(src, "vod_director", "vodDirector", "director"));
            vod.put("vod_content", firstString(src, "vod_content", "vodContent", "vod_blurb", "vodBlurb", "content", "desc", "description"));

            String[] play = buildPlay(src);
            vod.put("vod_play_from", play[0].length() == 0 ? "默认" : play[0]);
            vod.put("vod_play_url", play[1].length() == 0 ? "播放$" + id : play[1]);
        } catch (Throwable ignored) {
        }
        return vod;
    }

    private String[] buildPlay(JSONObject src) {
        List<String> from = new ArrayList<>();
        List<String> urls = new ArrayList<>();

        try {
            Object playList = first(src,
                    "vod_play_list", "vodPlayList", "playlist", "playList",
                    "urls", "video_chunks", "videoChunks", "play_url", "playUrl");
            parsePlayNode(playList, from, urls);

            if (urls.isEmpty()) {
                String url = firstString(src, "video_set_url", "videoSetUrl", "play_url", "playUrl", "vod_url", "vodUrl", "url");
                if (url.length() > 0) {
                    from.add("默认");
                    urls.add("播放$" + url);
                }
            }
        } catch (Throwable ignored) {
        }
        return new String[]{join(from, "$$$"), join(urls, "$$$")};
    }

    private void parsePlayNode(Object node, List<String> from, List<String> urls) {
        try {
            if (node instanceof JSONArray) {
                JSONArray arr = (JSONArray) node;
                boolean allEpisodes = true;
                for (int i = 0; i < arr.length(); i++) {
                    Object item = arr.opt(i);
                    if (!(item instanceof String) && !(item instanceof JSONObject)) {
                        allEpisodes = false;
                        break;
                    }
                }

                if (allEpisodes && arr.length() > 0 && !containsSourceObject(arr)) {
                    from.add("默认");
                    urls.add(episodesToString(arr));
                    return;
                }

                for (int i = 0; i < arr.length(); i++) parsePlayNode(arr.opt(i), from, urls);
            } else if (node instanceof JSONObject) {
                JSONObject obj = (JSONObject) node;
                String source = firstString(obj, "video_set_name", "videoSetName", "name", "from", "source", "player");
                Object child = first(obj, "urls", "list", "playlist", "playList", "video_chunks", "videoChunks", "items");
                if (child != null) {
                    from.add(source.length() == 0 ? "默认" : source);
                    urls.add(episodesToString(child));
                    return;
                }
                String url = firstString(obj, "video_set_url", "videoSetUrl", "play_url", "playUrl", "vod_url", "vodUrl", "url");
                if (url.length() > 0) {
                    from.add(source.length() == 0 ? "默认" : source);
                    urls.add(episodeName(obj, 1) + "$" + url);
                }
            } else if (node instanceof String) {
                String s = (String) node;
                if (s.length() > 0) {
                    from.add("默认");
                    urls.add(s.contains("$") ? s : "播放$" + s);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private String episodesToString(Object node) {
        List<String> eps = new ArrayList<>();
        try {
            if (node instanceof JSONArray) {
                JSONArray arr = (JSONArray) node;
                for (int i = 0; i < arr.length(); i++) {
                    Object item = arr.opt(i);
                    if (item instanceof JSONObject) {
                        JSONObject obj = (JSONObject) item;
                        String url = firstString(obj, "url", "play_url", "playUrl", "video_set_url", "videoSetUrl", "vod_url", "vodUrl");
                        if (url.length() > 0) eps.add(episodeName(obj, i + 1) + "$" + url);
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
                if (obj.has("urls") || obj.has("list") || obj.has("playlist") || obj.has("video_chunks")) return true;
            }
        }
        return false;
    }

    private String episodeName(JSONObject obj, int index) {
        String name = firstString(obj, "name", "title", "video_set_name", "videoSetName", "vod_name", "vodName");
        return name.length() == 0 ? "第" + index + "集" : name;
    }

    private boolean looksLikeVod(JSONObject obj) {
        return obj.has("vod_id") || obj.has("vod_name") || obj.has("video_id") || obj.has("video_name")
                || obj.has("vodName") || obj.has("vodPic") || obj.has("vod_play_list") || obj.has("play_url");
    }

    private String vodId(JSONObject obj) {
        return firstString(obj, "vod_id", "vodId", "video_id", "videoId", "id", "nid", "vod_nid", "vodNid");
    }

    private String vodName(JSONObject obj) {
        return firstString(obj, "vod_name", "vodName", "video_name", "videoName", "name", "title");
    }

    private JSONObject unwrapData(JSONObject obj) {
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
            if (value != null && value.trim().length() > 0 && !"null".equalsIgnoreCase(value.trim())) {
                return value.trim();
            }
        }
        return "";
    }

    private String firstString(Object node, String... keys) {
        try {
            if (node instanceof JSONObject) {
                JSONObject obj = (JSONObject) node;
                String direct = firstString(obj, keys);
                if (direct.length() > 0) return direct;
                Iterator<String> it = obj.keys();
                while (it.hasNext()) {
                    String found = firstString(obj.opt(it.next()), keys);
                    if (found.length() > 0) return found;
                }
            } else if (node instanceof JSONArray) {
                JSONArray arr = (JSONArray) node;
                for (int i = 0; i < arr.length(); i++) {
                    String found = firstString(arr.opt(i), keys);
                    if (found.length() > 0) return found;
                }
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    private int guessPageCount(JSONObject json, int current) {
        int totalPage = json.optInt("pagecount", json.optInt("page_count", json.optInt("totalPage", 0)));
        return totalPage > 0 ? totalPage : current + 1;
    }

    private int guessTotal(JSONObject json, int size, int page) {
        int total = json.optInt("total", json.optInt("count", 0));
        return total > 0 ? total : page * Math.max(size, 20);
    }

    private int safePage(String pg) {
        try {
            return Math.max(1, Integer.parseInt(pg));
        } catch (Throwable e) {
            return 1;
        }
    }

    private boolean isMediaUrl(String value) {
        if (value == null) return false;
        String v = value.toLowerCase();
        return v.startsWith("http") && (v.contains(".m3u8") || v.contains(".mp4") || v.contains(".flv") || v.contains(".ts") || v.contains("url="));
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

    private String enc(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, "UTF-8");
        } catch (Throwable e) {
            return value == null ? "" : value;
        }
    }

    private String cleanBase(String value) {
        if (value == null) return "";
        value = value.trim();
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
    }

    private String cleanPrefix(String value) {
        if (value == null || value.trim().length() == 0) return "";
        value = value.trim();
        if (!value.startsWith("/")) value = "/" + value;
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
    }

    private String join(List<String> list, String sep) {
        StringBuilder sb = new StringBuilder();
        for (String item : list) {
            if (item == null || item.length() == 0) continue;
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
