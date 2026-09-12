package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Filter;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * csp_AppRJ —— 天天影视 (v.rbotv.cn)
 * ZJAPI 2.0.0 签名体系: sign = md5(SIGN_SALT + timestamp)
 *
 * API 结构:
 *   /v3/home/type_search  → 首页推荐 12 条 (data.list)
 *   /v3/type/tj_vod       → 分类列表, 包含:
 *       cai    (6 条 - 推荐)
 *       loop   (5 条 - 轮播)
 *       type_vod (12 个子分类段, 每段 2-6 条, 跳过 type_id=-1 的广告)
 *   /v3/home/search       → 用户搜索
 *   /v3/home/vod_details  → 详情 + 播放源
 */
public class AppRJ extends Spider {

    private static final String DEFAULT_BASE = "http://v.rbotv.cn";
    private static final String USER_AGENT = "okhttp-okgo/jeasonlzy";
    private static final String SIGN_SALT = "7gp0bnd2sr85ydii2j32pcypscoc4w6c7g5spl";

    /** 11 个大类, 和 API type_id 对应 */
    private static final String[][] CATEGORIES = {
            {"1", "推荐"}, {"2", "内地"}, {"3", "电影"}, {"4", "动漫"},
            {"5", "综艺"}, {"6", "韩剧"}, {"7", "泰剧"}, {"8", "港剧"},
            {"9", "日剧"}, {"10", "美剧"}, {"11", "台剧"}
    };

    /** API type_vod 返回的子分类名, 用作 Filter 选项 (首页/分类页都一样) */
    private static final String[][] FILTER_SECTIONS = {
            {"精彩好剧", "精彩好剧"},
            {"内地", "内地"}, {"电影", "电影"}, {"动漫", "动漫"}, {"综艺", "综艺"},
            {"韩剧", "韩剧"}, {"泰剧", "泰剧"}, {"港剧", "港剧"}, {"美剧", "美剧"}
            // 日剧/台剧 目前返回 0 条, 跳过
    };

    private String baseUrl = DEFAULT_BASE;
    private final OkHttpClient client = new OkHttpClient();

    @Override
    public void init(Context context, String extend) {
        try { super.init(context, extend); } catch (Exception ignored) {}
        if (!TextUtils.isEmpty(extend)) baseUrl = extend.trim();
    }

    /**
     * 首页分类 —— 返回 11 个大类 + 子分类 Filter
     * Filter 来自 type_vod 返回的子分类段
     *
     * filters 的 key 是大类 tid ("1","2","3"...), value 是该大类下可用的 Filter 列表
     * CatVod 引擎会把选中的 Filter value 传给 categoryContent 的 extend map
     */
    @Override
    public String homeContent(boolean filter) {
        ArrayList<Class> classes = new ArrayList<>();
        for (String[] c : CATEGORIES) {
            classes.add(new Class(c[0], c[1]));
        }

        // 构造 Filter —— 每个大类共用同一套子分类选项
        ArrayList<Filter.Value> values = new ArrayList<>();
        values.add(new Filter.Value("全部", ""));
        for (String[] sec : FILTER_SECTIONS) {
            values.add(new Filter.Value(sec[0], sec[1]));
        }
        Filter f = new Filter("filter", "子分类", values);

        LinkedHashMap<String, List<Filter>> filters = new LinkedHashMap<>();
        for (String[] c : CATEGORIES) {
            filters.put(c[0], Arrays.asList(f));
        }

        return Result.get().classes(classes).filters(filters).string();
    }

    /** 首页推荐 —— /v3/home/type_search 返回 12 条 */
    @Override
    public String homeVideoContent() {
        ArrayList<Vod> list = new ArrayList<>();
        try {
            JSONObject root = new JSONObject(m43a("/v3/home/type_search", signedParams()));
            JSONObject data = root.optJSONObject("data");
            JSONArray arr = data != null ? data.optJSONArray("list") : null;
            addVodItems(arr, list, null);
        } catch (Exception ignored) {}
        return Result.string(list);
    }

    /**
     * 分类列表 —— 合并 cai + loop + 全部 type_vod 子分类段
     * 如果有 filter extend, 只返回匹配的子分类内容
     * API 服务端分页基本无效, 这里把所有合并后本地分页
     */
    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        ArrayList<Vod> all = new ArrayList<>();
        try {
            if (TextUtils.isEmpty(pg)) pg = "1";
            HashMap<String, String> params = signedParams();
            params.put("type_id", tid);
            params.put("page", pg);

            JSONObject root = new JSONObject(m43a("/v3/type/tj_vod", params));
            JSONObject data = root.optJSONObject("data");
            if (data == null) return Result.string(all);

            String filterSection = (extend != null && extend.containsKey("filter") && !extend.get("filter").isEmpty())
                    ? extend.get("filter") : null;

            // 1. cai (6 条) - 主推荐区, 不区分 filter
            JSONArray cai = data.optJSONArray("cai");
            addVodItems(cai, all, null);

            // 2. loop (5 条) - 轮播, 不区分 filter
            JSONArray loop = data.optJSONArray("loop");
            addVodItems(loop, all, null);

            // 3. type_vod 子分类段 - 这里有 12 个 section, 每个 section 有子分类名 + 视频列表
            JSONArray typeVod = data.optJSONArray("type_vod");
            if (typeVod != null) {
                Set<String> seenIds = new HashSet<>();
                for (int i = 0; i < all.size(); i++) {
                    String id = all.get(i).getVodId();
                    if (id != null) seenIds.add(id);
                }
                for (int i = 0; i < typeVod.length(); i++) {
                    JSONObject sec = typeVod.optJSONObject(i);
                    if (sec == null) continue;
                    // 跳过广告段 (type_id = -1)
                    int stid = sec.optInt("type_id", -999);
                    if (stid == -1) continue;
                    // 如果指定了 filter, 只取匹配的 section
                    String secName = sec.optString("type_name", "");
                    if (filterSection != null && !filterSection.equals(secName)) continue;
                    JSONArray vods = sec.optJSONArray("vod");
                    addVodItems(vods, all, seenIds);
                }
            }

            // 本地分页
            int page;
            try { page = Integer.parseInt(pg); } catch (Exception e) { page = 1; }
            if (page <= 0) page = 1;
            int limit = 18;
            int pagecount = Math.max(1, (int) Math.ceil((double) all.size() / limit));
            int start = (page - 1) * limit;
            int end = Math.min(start + limit, all.size());
            if (start >= all.size()) {
                // 超出范围, 返回空
                return Result.string(page, pagecount, limit, all.size(), new ArrayList<>());
            }
            ArrayList<Vod> pageList = new ArrayList<>(all.subList(start, end));
            return Result.string(page, pagecount, limit, all.size(), pageList);

        } catch (Exception e) {
            return Result.string(all);
        }
    }

    /** 搜索 —— 和首页同一套 API, 返回列表里自带播放源 */
    @Override
    public String searchContent(String keyword, boolean quick) {
        ArrayList<Vod> list = new ArrayList<>();
        if (TextUtils.isEmpty(keyword)) return Result.string(list);
        try {
            HashMap<String, String> params = signedParams();
            params.put("keyword", keyword);
            JSONObject root = new JSONObject(m43a("/v3/home/search", params));
            JSONObject data = root.optJSONObject("data");
            JSONArray arr = null;
            if (data != null) {
                arr = data.optJSONArray("list");
                if (arr == null) arr = data.optJSONArray("data");
            }
            if (arr == null) arr = root.optJSONArray("data");
            addVodItems(arr, list, null);
        } catch (Exception ignored) {}
        return Result.string(list);
    }

    /** 详情 */
    @Override
    public String detailContent(List<String> ids) {
        ArrayList<Vod> empty = new ArrayList<>();
        if (ids == null || ids.isEmpty()) return Result.string(empty);
        try {
            HashMap<String, String> params = signedParams();
            params.put("vod_id", ids.get(0));
            JSONObject root = new JSONObject(m43a("/v3/home/vod_details", params));
            JSONObject data = root.optJSONObject("data");
            if (data == null) return Result.string(empty);

            Vod vod = new Vod();
            vod.setVodId(ids.get(0));
            vod.setVodName(data.optString("vod_name"));
            vod.setVodPic(firstNonEmpty(data.optString("vod_pic"), data.optString("vod_pic_thumb")));
            vod.setVodRemarks(data.optString("vod_remarks"));
            vod.setVodContent(data.optString("vod_content"));
            vod.setVodYear(data.optString("vod_year"));
            vod.setVodActor(data.optString("vod_actor"));
            vod.setVodDirector(data.optString("vod_director"));
            vod.setTypeName(data.optString("vod_class"));

            buildPlayList(data, vod);
            return Result.string(vod);
        } catch (Exception ignored) {
            return Result.string(empty);
        }
    }

    /** 播放 —— 支持解析 URL 走第三方解析器 */
    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        try {
            if (TextUtils.isEmpty(id)) {
                return Result.get().url("").parse(0).string();
            }
            String playUrl = id;
            HashMap<String, String> headers = new HashMap<>();
            headers.put("User-Agent", USER_AGENT);

            if (id.startsWith("{")) {
                JSONObject obj = new JSONObject(id);
                playUrl = obj.optString("url");
                JSONArray parseUrls = obj.optJSONArray("parse_urls");
                if (!TextUtils.isEmpty(playUrl) && parseUrls != null && parseUrls.length() > 0) {
                    String parser = parseUrls.optString(0);
                    if (!TextUtils.isEmpty(parser)) {
                        // parser 格式: "https://xxx/api/?key=XXX&url="
                        if (playUrl.startsWith("http")) {
                            // 直链, 可能不需要解析
                        } else {
                            String parsed = get(parser + playUrl);
                            try {
                                JSONObject pobj = new JSONObject(parsed);
                                String realUrl = pobj.optString("url");
                                if (!TextUtils.isEmpty(realUrl)) playUrl = realUrl;
                            } catch (Exception ignored) {}
                        }
                    }
                }
            }
            return Result.get().url(playUrl).parse(0).header(headers).string();
        } catch (Exception ignored) {
            return Result.get().url(id).parse(0).string();
        }
    }

    // ============ 辅助 ============

    private void buildPlayList(JSONObject data, Vod vod) {
        JSONArray playList = data.optJSONArray("vod_play_list");
        if (playList == null || playList.length() == 0) {
            vod.setVodPlayFrom("默认");
            vod.setVodPlayUrl("");
            return;
        }
        ArrayList<String> fromList = new ArrayList<>();
        ArrayList<String> urlGroupList = new ArrayList<>();
        for (int i = 0; i < playList.length(); i++) {
            JSONObject group = playList.optJSONObject(i);
            if (group == null) continue;
            String from = group.optString("name");
            if (TextUtils.isEmpty(from)) from = "线路" + (i + 1);
            fromList.add(from);
            JSONArray urls = group.optJSONArray("urls");
            JSONArray parseUrls = group.optJSONArray("parse_urls");
            ArrayList<String> itemList = new ArrayList<>();
            if (urls != null) {
                for (int j = 0; j < urls.length(); j++) {
                    JSONObject item = urls.optJSONObject(j);
                    if (item != null) {
                        String name = item.optString("name");
                        String url = item.optString("url");
                        if (TextUtils.isEmpty(name)) name = "第" + (j + 1) + "集";
                        if (!TextUtils.isEmpty(url)) itemList.add(name + "$" + buildPlayerId(url, parseUrls));
                    } else {
                        String url = urls.optString(j);
                        if (!TextUtils.isEmpty(url)) itemList.add("第" + (j + 1) + "集$" + buildPlayerId(url, parseUrls));
                    }
                }
            }
            if (itemList.size() == 0) {
                String url = group.optString("url");
                if (!TextUtils.isEmpty(url)) itemList.add(from + "$" + buildPlayerId(url, parseUrls));
            }
            urlGroupList.add(join("#", itemList));
        }
        vod.setVodPlayFrom(join("$$$", fromList));
        vod.setVodPlayUrl(join("$$$", urlGroupList));
    }

    private String buildPlayerId(String url, JSONArray parseUrls) {
        if (parseUrls == null || parseUrls.length() == 0) return url;
        try {
            JSONObject obj = new JSONObject();
            obj.put("url", url);
            obj.put("parse_urls", parseUrls);
            return obj.toString();
        } catch (Exception e) {
            return url;
        }
    }

    /**
     * 从 JSONArray 里提取 Vod, 可按 seenIds 去重
     * 注意: loop 里没有 vod_name, 但有 vod_remarks/vod_id, 跳过空 name
     */
    private void addVodItems(JSONArray arr, ArrayList<Vod> list, Set<String> seenIds) {
        if (arr == null) return;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject item = arr.optJSONObject(i);
            if (item == null) continue;
            String id = item.optString("vod_id");
            String name = item.optString("vod_name");
            if (TextUtils.isEmpty(id) || TextUtils.isEmpty(name)) continue;
            if (seenIds != null && !seenIds.add(id)) continue;

            String pic = firstNonEmpty(item.optString("vod_pic"), item.optString("vod_pic_thumb"));
            String remarks = item.optString("vod_remarks");
            Vod v = new Vod(id, name, pic, remarks);
            list.add(v);
        }
    }

    // ============ HTTP ============

    private HashMap<String, String> signedParams() {
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        HashMap<String, String> params = new HashMap<>();
        params.put("timestamp", timestamp);
        params.put("sign", md5(SIGN_SALT + timestamp));
        return params;
    }

    /** POST multipart form */
    private String m43a(String path, Map<String, String> params) throws Exception {
        MultipartBody.Builder form = new MultipartBody.Builder().setType(MultipartBody.FORM);
        for (Map.Entry<String, String> entry : params.entrySet()) {
            form.addFormDataPart(entry.getKey(), entry.getValue());
        }
        Request request = new Request.Builder()
                .url(baseUrl + path)
                .post(form.build())
                .addHeader("User-Agent", USER_AGENT)
                .build();
        try (Response response = client.newCall(request).execute()) {
            return response.body() == null ? "" : response.body().string();
        }
    }

    private String get(String url) throws Exception {
        Request request = new Request.Builder()
                .url(url)
                .addHeader("User-Agent", USER_AGENT)
                .build();
        try (Response response = client.newCall(request).execute()) {
            return response.body() == null ? "" : response.body().string();
        }
    }

    // ============ 工具 ============

    private String md5(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] bytes = md.digest(value.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b & 0xff));
            return sb.toString();
        } catch (Exception e) { return ""; }
    }

    private String firstNonEmpty(String a, String b) {
        return TextUtils.isEmpty(a) ? b : a;
    }

    private String join(String sep, List<String> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(sep);
            sb.append(list.get(i));
        }
        return sb.toString();
    }
}
