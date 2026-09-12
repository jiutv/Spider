package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;
import android.util.Base64;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Filter;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 云帧享 / 秒播影视 (baiyunvideo)
 *
 * 域名:
 *   index_url  → https://ss.trgfd.cn/cache/index/com.baiyunvideo.app.json
 *   textURL    → https://js.trgfd.cn
 *   resourceURL → https://img.zqykfz.cn
 *
 * 问题排查 & 修复:
 * 1. key_api (tangsan.fun) 已死 (502), AES key 从 APK libkeys.so 提取:
 *      函数: Java_com_qingmanlsland_app_utils_SecureConfig_getApiKey
 *      Key:  qvn1u7FCfu8uaolp980i8uVHVS8Dxih7  (32 bytes, AES-256)
 * 2. 分类缓存 /cache/zhaopian/... 全 404, 改用 /vc/api/search/{keyword}/{page}.json 当分类
 * 3. AES-GCM 解密格式: base64 → nonce(12) + ciphertext + tag(16)
 */
public class YunZhenXiang extends Spider {

    /** 硬编码 AES-256 key, 来自 APK libkeys.so */
    private static final String AES_KEY = "qvn1u7FCfu8uaolp980i8uVHVS8Dxih7";
    private static final String INDEX_URL = "https://ss.trgfd.cn/cache/index/com.baiyunvideo.app.json";

    /**
     * xinRanks 里的 type 选项 (来自 index_url), 用搜索 API 当分类
     * 格式: 显示名 → 搜索关键词 (多数和显示名一致)
     */
    private static final String[][] CATEGORIES = {
            {"大陆剧", "大陆剧"}, {"美剧", "美剧"}, {"韩剧", "韩剧"},
            {"英剧", "英剧"}, {"泰剧", "泰剧"}, {"日剧", "日剧"},
            {"台剧", "台剧"}, {"港剧", "港剧"}, {"电影", "电影"},
            {"国内综艺", "国内综艺"}, {"国外综艺", "国外综艺"},
            {"国漫", "国漫"}, {"日漫", "日漫"}, {"少儿", "少儿"},
            {"纪录片", "纪录片"}
    };

    private static final String[][] SORT_FILTERS = {
            {"最新", "最新"}, {"最热", "最热"}
    };

    private String textURL = "https://js.trgfd.cn";
    private String resourceURL = "https://img.zqykfz.cn";
    private String version = "2.7.0";
    private final Map<String, String> headers = new HashMap<>();
    private volatile boolean initialized = false;
    private String ext = "";

    // ============ 解密 ============

    /**
     * AES-256-GCM 解密
     * base64 → nonce(12) + ciphertext(可变) + authTag(16)
     * Java AES/GCM/NoPadding: 把 ciphertext+tag 一起传给 doFinal() 即可
     */
    private String decrypt(String str) {
        try {
            byte[] data = Base64.decode(str.trim(), 0);
            if (data.length < 28) return ""; // 至少 12 nonce + 1 密文 + 16 tag
            byte[] nonce = Arrays.copyOfRange(data, 0, 12);
            byte[] ciphertextWithTag = Arrays.copyOfRange(data, 12, data.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,
                    new SecretKeySpec(AES_KEY.getBytes("UTF-8"), "AES"),
                    new GCMParameterSpec(128, nonce));
            byte[] plain = cipher.doFinal(ciphertextWithTag);
            return new String(plain, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    // ============ HTTP ============

    private String fetch(String url) {
        if (url == null || url.isEmpty()) return null;
        try {
            Map<String, String> h = new HashMap<>(headers);
            String result = OkHttp.string(url, h);
            return TextUtils.isEmpty(result) ? null : result;
        } catch (Exception e) {
            return null;
        }
    }

    // ============ 初始化 ============

    private synchronized void ensureInit() {
        if (initialized) return;
        initialized = true;
        try {
            // 先拉 index_url 更新 textURL / version
            String indexJson = fetch(INDEX_URL);
            if (indexJson != null) {
                JSONObject index = new JSONObject(indexJson);
                JSONObject app = index.optJSONObject("app");
                if (app != null) {
                    String textUrl = app.optString("textURL", "");
                    if (!TextUtils.isEmpty(textUrl)) textURL = textUrl;
                    String imgUrl = app.optString("resourceURL", "");
                    if (TextUtils.isEmpty(imgUrl)) imgUrl = app.optString("img", "");
                    if (!TextUtils.isEmpty(imgUrl)) resourceURL = imgUrl;
                    version = app.optString("latestVersion", version);
                }
                JSONArray qudao = index.optJSONArray("qudao");
                if (qudao != null && qudao.length() > 0) {
                    String banben = qudao.getJSONObject(0).optString("banben", version);
                    if (!TextUtils.isEmpty(banben)) version = banben;
                }
            }
            // ext 里的 host 优先覆盖
            if (ext != null && ext.trim().startsWith("{")) {
                JSONObject obj = new JSONObject(ext);
                if (obj.has("host") && !TextUtils.isEmpty(obj.optString("host"))) {
                    textURL = obj.optString("host");
                }
            }
        } catch (Exception ignored) {}
    }

    // ============ 工具 ============

    private String md5(String str) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] bytes = md.digest(str.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b & 0xff));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private String firstNonEmpty(String a, String b) {
        return TextUtils.isEmpty(a) ? b : a;
    }

    // ============ Spider 接口 ============

    @Override
    public void init(Context context, String extend) {
        try { super.init(context, extend); } catch (Exception ignored) {}
        this.ext = extend;
        headers.put("User-Agent", "baiyunvideo-android " + version);
        headers.put("Accept-Language", "zh-CN,zh;q=0.9");
    }

    @Override
    public String homeContent(boolean filter) {
        ensureInit();
        try {
            // 分类
            ArrayList<Class> classes = new ArrayList<>();
            for (String[] c : CATEGORIES) {
                classes.add(new Class(c[0], c[0]));
            }

            // Filter (排序)
            LinkedHashMap<String, List<Filter>> filters = new LinkedHashMap<>();
            ArrayList<Filter.Value> sortValues = new ArrayList<>();
            for (String[] s : SORT_FILTERS) {
                sortValues.add(new Filter.Value(s[0], s[1]));
            }
            Filter sortFilter = new Filter("sort", "排序", sortValues);
            for (String[] c : CATEGORIES) {
                filters.put(c[0], Arrays.asList(sortFilter));
            }

            return Result.get().classes(classes).filters(filters).string();
        } catch (Exception e) {
            return "{}";
        }
    }

    /** 首页推荐 —— 用搜索"全部"取前 16 条 */
    @Override
    public String homeVideoContent() {
        ensureInit();
        try {
            return searchCategory("全部", "1");
        } catch (Exception e) {
            return Result.string(new ArrayList<>());
        }
    }

    /**
     * 分类列表 —— 用搜索 API 当分类
     * 因为原来的 /cache/zhaopian/... 全部 404 了
     *
     * sort 不影响搜索 API 的结果顺序 (服务端没有排序参数),
     * 但保留 Filter 以便扩展
     */
    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        ensureInit();
        if (TextUtils.isEmpty(textURL)) return Result.string(new ArrayList<>());

        // tid 是分类名, 同时也是搜索关键词
        String keyword = tid;
        // 如果 filter 传过来的是显示名, 映射一下
        for (String[] c : CATEGORIES) {
            if (c[0].equals(tid)) { keyword = c[1]; break; }
        }

        try {
            return searchCategory(keyword, pg);
        } catch (Exception e) {
            return Result.string(new ArrayList<>());
        }
    }

    /** 用搜索 API 返回 Category 格式的结果 */
    private String searchCategory(String keyword, String pg) {
        List<Vod> list = new ArrayList<>();
        try {
            String json = fetch(textURL + "/vc/api/search/" + keyword + "/" + pg + ".json");
            if (json == null) return Result.string(list);
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject item = arr.getJSONObject(i);
                String id = String.valueOf(item.optInt("videoId", 0));
                String name = item.optString("videoName");
                String pic = item.optString("fengmiantu");
                if (!pic.startsWith("http")) pic = resourceURL + pic;
                String remarks = item.optString("serialDesc");
                Vod v = new Vod(id, name, pic, remarks);
                list.add(v);
            }
        } catch (Exception ignored) {}

        int page;
        try { page = Integer.parseInt(pg); } catch (Exception e) { page = 1; }
        if (page <= 0) page = 1;
        int limit = 20;
        int pagecount = page + 1;
        if (list.size() > 0) pagecount = page + 1;
        return Result.string(page, pagecount, limit, 9999, list);
    }

    /** 详情 —— AES 解密 playUrlList */
    @Override
    public String detailContent(List<String> ids) {
        ensureInit();
        if (ids == null || ids.isEmpty()) return Result.string(new ArrayList<>());
        if (TextUtils.isEmpty(textURL)) return Result.string(new ArrayList<>());

        try {
            String id = ids.get(0);
            int idInt;
            try { idInt = Integer.parseInt(id); } catch (Exception e) { idInt = 0; }
            String url = textURL + "/cache/videos/" + (idInt / 1000) + "/" + id + ".json"
                    + "?version=" + version
                    + "&baoming=com.baiyunvideo.app"
                    + "&channel=fenxiang";

            String raw = fetch(url);
            if (TextUtils.isEmpty(raw)) return Result.string(new ArrayList<>());

            String plain = decrypt(raw.trim());
            if (TextUtils.isEmpty(plain)) return Result.string(new ArrayList<>());

            JSONObject data = new JSONObject(plain);
            Vod vod = new Vod();
            vod.setVodId(id);
            vod.setVodName(data.optString("videoName"));
            String pic = data.optString("fengmiantu");
            if (!pic.startsWith("http")) pic = resourceURL + pic;
            vod.setVodPic(pic);
            vod.setTypeName(data.optString("class"));
            vod.setVodRemarks(data.optString("remarks"));
            vod.setVodContent(String.format("主演：%s\n地区：%s\n简介：%s",
                    data.optString("actor", "未知"),
                    data.optString("region", ""),
                    data.optString("blurb", "")));
            vod.setVodYear(data.optString("year"));
            vod.setVodArea(data.optString("region"));
            vod.setVodActor(data.optString("actor"));
            vod.setVodDirector(data.optString("director"));

            // 播放列表
            JSONArray playUrlList = data.optJSONArray("playUrlList");
            ArrayList<String> episodes = new ArrayList<>();
            if (playUrlList != null) {
                for (int i = 0; i < playUrlList.length(); i++) {
                    JSONObject ep = playUrlList.getJSONObject(i);
                    String name = ep.optString("name", "第" + (i + 1) + "集");
                    String ji = ep.optString("ji", String.valueOf(i + 1));
                    // 格式: 名称$sid@@集@@索引
                    episodes.add(name + "$" + id + "@@" + ji + "@@" + i);
                }
            }
            vod.setVodPlayFrom("云帧享");
            vod.setVodPlayUrl(TextUtils.join("#", episodes));

            return Result.string(vod);
        } catch (Exception e) {
            return Result.string(new ArrayList<>());
        }
    }

    /**
     * 播放 —— 用 vuk=md5(sid + aesKey) 签名请求 playurl API
     * 如果 API 返回的 url 为空 (code=10401), 说明 key 不对或已过期
     */
    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        ensureInit();
        try {
            String[] parts = id.split("@@");
            if (parts.length < 3) return Result.get().url("").parse(0).string();

            String sid = parts[0];
            String ji = parts[1];
            String jiIndex = parts[2];

            // 构造 16 位随机 androidId
            StringBuilder sb = new StringBuilder();
            Random random = new Random();
            for (int i = 0; i < 16; i++) {
                sb.append("abcdefghijklmnopqrstuvwxyz0123456789".charAt(random.nextInt(36)));
            }
            String androidId = sb.toString();

            // vuk = md5(sid + AES_KEY)
            String vuk = md5(sid + AES_KEY);

            String url = textURL + "/vc/api/video/playurl"
                    + "?sid=" + sid
                    + "&ji=" + ji
                    + "&jiIndex=" + jiIndex
                    + "&t=0&y=0&isjiid=1"
                    + "&androidId=" + androidId
                    + "&version=" + version
                    + "&baoming=com.baiyunvideo.app"
                    + "&channel=fenxiang";

            Map<String, String> h = new HashMap<>(headers);
            h.put("vuk", vuk);
            String resp = OkHttp.string(url, h);
            if (TextUtils.isEmpty(resp)) return Result.get().url("").parse(0).string();

            JSONObject root = new JSONObject(resp);
            JSONObject data = root.optJSONObject("data");
            String playUrl = "";
            if (data != null) {
                int code = data.optInt("code", -1);
                playUrl = data.optString("url", "");
                if (code != 0 && !TextUtils.isEmpty(playUrl)) {
                    // 有 url 但有 warning, 仍然试试
                }
            }

            Map<String, String> header = new HashMap<>();
            header.put("User-Agent", "baiyunvideo-android " + version);
            return Result.get().url(playUrl).parse(0).header(header).string();
        } catch (Exception e) {
            return Result.get().url("").parse(0).string();
        }
    }

    /** 搜索 */
    @Override
    public String searchContent(String key, boolean quick) {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) {
        ensureInit();
        try {
            return searchCategory(key, pg);
        } catch (Exception e) {
            return Result.string(new ArrayList<>());
        }
    }
}
