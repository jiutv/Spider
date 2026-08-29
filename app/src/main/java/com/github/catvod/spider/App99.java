package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Filter;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.jnet.OkHttp;
import com.github.catvod.utils.AesCbc;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

/**
 * App99 影视爬虫 —— 从 classes.dex 反混淆恢复
 *
 * <p>通信协议：请求体经 AES/CBC/PKCS5Padding 加密（key = uuid 去横线，IV 随机并前置），
 * 响应体同样加密；外层通过 SHA-256 签名校验。</p>
 *
 * <p>init 接收的 JSON 配置字段：</p>
 * <ul>
 *   <li>host         —— API 基址（必填）</li>
 *   <li>appkey       —— 签名密钥（必填）</li>
 *   <li>name         —— 渠道名（必填）</li>
 *   <li>buildSignature —— 构建签名（必填）</li>
 *   <li>buildNumber  —— 构建号（必填）</li>
 *   <li>versionName  —— 版本名（必填）</li>
 *   <li>package      —— 包名（必填）</li>
 *   <li>uuid         —— 设备 UUID（可选，默认随机）</li>
 *   <li>ua           —— User-Agent（可选）</li>
 *   <li>version      —— 接口版本（可选，默认 0b4328287a5d953e）</li>
 *   <li>LoginPath    —— 登录路径（可选，默认 /app/userInfo）</li>
 * </ul>
 */
public class App99 extends Spider {

    private static final String DEFAULT_VERSION = "0b4328287a5d953e";
    private static final String DEFAULT_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
          + "(KHTML, like Gecko) Chrome/122.0.6299.95 Safari/537.36";
    private static final int PAGE_SIZE = 21;

    // ---- 实例字段（原 f116a ~ f122f）----
    private String host = "";          // f116a  API 基址
    private final JSONObject config = new JSONObject(); // f117a  服务端下发配置缓存
    private String token = "";         // f118b  用户令牌
    private String uuid = "";          // f119c  设备 UUID（同时作为 AES 密钥）
    private String appKey = "";        // f120d  签名密钥
    private String userAgent = "";     // f121e  HTTP User-Agent
    private String version = DEFAULT_VERSION; // f122f  接口版本

    // =====================================================================
    //  Spider 生命周期
    // =====================================================================

    @Override
    public void init(Context context, String extend) {
        if (TextUtils.isEmpty(extend)) return;
        try {
           JSONObject cfg = new JSONObject(extend);
            host = cfg.optString("host");
            appKey = cfg.optString("appkey");
            String name = cfg.optString("name");
            String buildSignature = cfg.optString("buildSignature");
            String buildNumber = cfg.optString("buildNumber");
            String versionName = cfg.optString("versionName");
            String pkg = cfg.optString("package");

            if (TextUtils.isEmpty(host) || TextUtils.isEmpty(appKey)
                    || TextUtils.isEmpty(name) || TextUtils.isEmpty(buildSignature)
                    || TextUtils.isEmpty(buildNumber) || TextUtils.isEmpty(versionName)
                    || TextUtils.isEmpty(pkg)) {
                return;
            }

            uuid = cfg.optString("uuid", UUID.randomUUID().toString());
            userAgent = cfg.optString("ua", DEFAULT_UA);
            String v = cfg.optString("version", "").trim();
            if (!TextUtils.isEmpty(v)) version = v;

            String loginPath = cfg.optString("LoginPath", "/app/userInfo");

            // 先尝试 systemInit
            systemInit(versionName, name, buildSignature);
            // 失败则走 userInfo / log 兜底
            if (TextUtils.isEmpty(token)) {
                loginWithFallback(loginPath, versionName, name, pkg, buildNumber, buildSignature);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // =====================================================================
    //  首页内容
    // =====================================================================

    @Override
    public String homeContent(boolean refresh) {
        ArrayList<Class> classes = new ArrayList<>();
        LinkedHashMap<String, List<Filter>> filters = new LinkedHashMap<>();
        JSONArray categories = config.optJSONArray("categories");

        try {
            if (categories != null) {
                for (int i = 0; i < categories.length(); i++) {
                    JSONObject cat = categories.getJSONObject(i);
                    String id = cat.optString("id");
                    String name = cat.optString("name");
                    if (isNoticeCategory(name)) continue;

                    classes.add(new Class(id, name));

                    JSONObject typeExtend = cat.optJSONObject("type_extend");
                    if (typeExtend != null) {
                        ArrayList<Filter> fl = new ArrayList<>();
                        fl.add(new Filter("class", "类型", buildFilterValues(typeExtend.optJSONArray("class"))));
                        fl.add(new Filter("area", "地区", buildFilterValues(typeExtend.optJSONArray("areas"))));
                        fl.add(new Filter("lang", "语言", buildFilterValues(typeExtend.optJSONArray("lang"))));
                        fl.add(new Filter("year", "年份", buildFilterValues(typeExtend.optJSONArray("years"))));
                        filters.put(id, fl);
                    }
                }
            }

            // 取首个非公告分类作为首页默认 pid
            String firstId = "1";
            if (categories != null) {
                for (int i = 0; i < categories.length(); i++) {
                    JSONObject cat = categories.getJSONObject(i);
                    if (isNoticeCategory(cat.optString("name"))) {
                        String id = cat.optString("id");
                        if (!TextUtils.isEmpty(id)) { firstId = id; break; }
                    }
                }
            }

            JSONObject body = new JSONObject();
            body.put("kw", "");
            body.put("page", firstId);
            body.put("limit", PAGE_SIZE);
            body.put("pid", firstId);
            body.put("orderBy", "time");
            body.put("isCategory", 1);
            body.put("token", token);

            JSONObject resp = apiCall("/vod/search", token, body);
            if (resp.has("data")) {
                return Result.string(classes, parseVodList(resp.getJSONArray("data")), filters);
            }
        } catch (Exception ignored) {
        }
        return Result.string(classes, new ArrayList<>(), filters);
    }

    // =====================================================================
    //  分类页
    // =====================================================================

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        try {
            JSONObject body = new JSONObject();
            body.put("kw", "");
            body.put("page", pg);
            body.put("limit", PAGE_SIZE);
            body.put("pid", tid);
            body.put("orderBy", "time");
            body.put("isCategory", 1);
            body.put("token", token);

            JSONObject resp = apiCall("/vod/search", token, body);
            if (resp.has("data")) {
                int pageCount = resp.optInt("page_count", 1);
                return Result.get()
                        .page(Integer.parseInt(pg), pageCount, 0, 0)
                        .vod(parseVodList(resp.getJSONArray("data")))
                        .string();
            }
        } catch (Exception ignored) {
        }
        return Result.get().page(1, 1, 0, 0).vod(new ArrayList<>()).string();
    }

    // =====================================================================
    //  详情页
    // =====================================================================

    @Override
    public String detailContent(List<String> ids) {
        Vod vod = new Vod();
        try {
            // 播放器 code -> name 映射
            HashMap<String, String> playerMap = new HashMap<>();
            if (config.has("player")) {
                JSONObject players = config.getJSONObject("player");
                Iterator<String> keys = players.keys();
                while (keys.hasNext()) {
                    JSONObject p = players.getJSONObject(keys.next());
                    playerMap.put(p.optString("code").trim(), p.optString("name").trim());
                }
            }

            JSONObject body = new JSONObject();
            body.put("id", ids.get(0));
            body.put("eps", "1");
            body.put("v", "2.0.0");
            body.put("pl", 1);
            body.put("token", token);

            JSONObject resp = apiCall("/vod/detail", token, body);
            if (!resp.has("data")) return Result.string(vod);

            JSONObject data = resp.getJSONObject("data");
            vod.setVodId(data.optString("id"));
            vod.setVodName(data.optString("name"));
            vod.setVodPic(data.optString("pic"));
            vod.setVodRemarks(data.optString("remarks"));
            vod.setVodYear(data.optString("year"));
            vod.setVodArea(data.optString("area"));
            vod.setVodActor(data.optString("actor"));
            vod.setVodDirector(data.optString("director"));
            vod.setVodContent(data.optString("content"));
            vod.setTypeName(data.optString("class"));

            String vodName = data.optString("name");
            final String[] playFromArr = data.optString("play_from").split("\\$\\$\\$");
            final String[] playUrlArr = data.optString("play_url").split("\\$\\$\\$");
            int len = playUrlArr.length;

            // 按播放源优先级排序
            Integer[] indices = new Integer[len];
            for (int i = 0; i < len; i++) indices[i] = i;
            Arrays.sort(indices, new Comparator<Integer>() {
                @Override
                public int compare(Integer a, Integer b) {
                    int ra = comparePlayUrl(a, playUrlArr[a], playFromArr);
                    int rb = comparePlayUrl(b, playUrlArr[b], playFromArr);
                    return Integer.compare(ra, rb);
                }
            });

            StringBuilder fromSb = new StringBuilder();
            StringBuilder urlSb = new StringBuilder();
            for (int idx = 0; idx < len; idx++) {
                int i = indices[idx];
                String code = i < playFromArr.length ? playFromArr[i] : "";
                String name = playerMap.get(code);
                if (fromSb.length() > 0) fromSb.append("$$$");
                fromSb.append(TextUtils.isEmpty(name) ? code : name);

                String[] episodes = playUrlArr[i].split("#");
                StringBuilder epSb = new StringBuilder();
                for (String ep : episodes) {
                    String[] parts = ep.split("\\$");
                    if (parts.length < 2) continue;
                    String epNum = parts[0];
                    String epUrl = parts[1];
                    String epLabel = epNum.replaceAll("\\D+", "");
                    if (TextUtils.isEmpty(epLabel)) epLabel = "1";
                    // 格式: 集数$URL@播放源code@剧名@集标签
                    String line = epNum + "$" + epUrl + "@" + code + "@" + vodName + "@" + epLabel;
                    if (epSb.length() > 0) epSb.append("#");
                    epSb.append(line);
                }
                if (urlSb.length() > 0 && epSb.length() > 0) urlSb.append("$$$");
                urlSb.append(epSb);
            }

            vod.setVodPlayFrom(fromSb.toString());
            vod.setVodPlayUrl(urlSb.toString());
            return Result.string(vod);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Result.string(vod);
    }

    // =====================================================================
    //  播放解析
    // =====================================================================

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        final String PARSE_FAIL = "播放链接解析失败,请更换其他源播放";
        try {
            String[] parts = id.split("@");
            if (parts.length < 2) return Result.error("播放参数无效");

            String url = parts[0];
            String playerCode = parts[1];
            String epName = parts.length > 2 ? parts[2].trim() : "";
            String epIndex = parts.length > 3 ? parts[3].trim() : "1";

            // 直链直接返回
            if (isDirectVideoUrl(url)) {
                return buildPlayerResult(url, epName, epIndex, url);
            }

            if (!config.has("player")) return Result.error("播放器配置缺失");
            JSONObject players = config.getJSONObject("player");
            JSONObject player = players.optJSONObject(playerCode);

            // 按名称兜底查找
            if (player == null && !TextUtils.isEmpty(flag)) {
                Iterator<String> keys = players.keys();
                while (keys.hasNext()) {
                    JSONObject p = players.optJSONObject(keys.next());
                    if (p != null && flag.equals(p.optString("name"))) {
                        player = p;
                        break;
                    }
                }
            }
            if (player == null) return Result.error("播放器配置缺失: " + playerCode);

            String resolved;
            if (player.optInt("type") != 0 || isMajorVideoSite(url)) {
                resolved = resolveParseUrl(url, player);
                if (TextUtils.isEmpty(resolved)) {
                    if (!isMajorVideoSite(url)) return Result.error(PARSE_FAIL);
                    // 大站走应用内解析
                    JSONObject j = new JSONObject();
                    j.put("parse", 1);
                    j.put("jx", 1);
                    j.put("url", url);
                    String danmaku = Danmaku.buildUrl(epName, epIndex, url);
                    if (!TextUtils.isEmpty(danmaku)) j.put("danmaku", danmaku);
                    return j.toString();
                }
            } else {
                resolved = url;
            }
            return buildPlayerResult(resolved, epName, epIndex, url);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error(PARSE_FAIL);
        }
    }

    // =====================================================================
    //  搜索
    // =====================================================================

    @Override
    public String searchContent(String keyword, boolean quick) {
        try {
            JSONObject body = new JSONObject();
            body.put("kw", keyword);
            body.put("page", 1);
            body.put("limit", PAGE_SIZE);
            body.put("orderBy", "vod_hits_month");
            body.put("sort", "desc");
            body.put("token", token);

            JSONObject resp = apiCall("/vod/search", token, body);
            if (resp.has("data")) {
                return Result.string(parseVodList(resp.getJSONArray("data")));
            }
        } catch (Exception ignored) {
        }
        return Result.string(new ArrayList<>());
    }

    // =====================================================================
    //  初始化辅助：systemInit
    // =====================================================================

    private void systemInit(String versionName, String name, String buildSignature) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("v", versionName);
        payload.put("n", name);
        payload.put("s", buildSignature);
        payload.put("pl", "1");
        payload.put("apiVersion", "v2");
        payload.put("token", "");

        String resp = postEncrypted("/app/systemInit", payload, "");
        if (TextUtils.isEmpty(resp)) return;

        JSONObject json = new JSONObject(resp);
        if (json.has("player")) {
            config.put("player", json.getJSONObject("player"));
        }
        if (json.has("parser_api")) {
            config.put("parses", json.getJSONArray("parser_api"));
        }
        if (json.has("categorys")) {
            JSONObject cats = json.getJSONObject("categorys");
            if (cats.has("data")) {
                config.put("categories", cats.getJSONArray("data"));
            }
        }
    }

    // =====================================================================
    //  初始化辅助：userInfo / log 兜底登录
    // =====================================================================

    private void userInfo(String path, String versionName, String name,
                          String pkg, String buildNumber, String buildSignature) throws Exception {
        long now = System.currentTimeMillis();
        String did = UUID.randomUUID().toString();

        JSONObject appInfo = new JSONObject();
        appInfo.put("version", versionName);
        appInfo.put("name", name);
        appInfo.put("package", pkg);
        appInfo.put("buildNumber", buildNumber);
        appInfo.put("buildSignature", buildSignature);
        appInfo.put("install", now);
        appInfo.put("update", now);

        JSONObject device = new JSONObject();
        device.put("os", "android");
        device.put("name", "xiaomi");
        device.put("version", "15");
        device.put("sdkInt", 32);
        device.put("device", "xiaomi");
        device.put("brand", "xiaomi");
        device.put("manufacturer", "xiaomi");
        device.put("product", "b0q");
        device.put("hardware", "xiaomi");
        device.put("isPhysicalDevice", true);
        device.put("androidId", "V417IR");
        device.put("bootloader", "unknown");
        device.put("display", "V417IR release-keys");
        device.put("host", "a11-gz01-test");
        device.put("tags", "release-keys");
        device.put("type", "user");
        device.put("finger", "xiaomi/b0q/b0q:15/V619IR/613:user/release-keys");
        device.put("app", appInfo);
        device.put("did", did);
        device.put("apiVersion", "v2");
        device.put("channel", "");
        device.put("token", "");

        String resp = postEncrypted(path, device, "");
        if (TextUtils.isEmpty(resp)) return;

        JSONObject json = new JSONObject(resp);
        if (json.has("userInfo")) {
            JSONObject ui = json.getJSONObject("userInfo");
            if (ui.has("user_token")) {
                token = ui.optString("user_token");
            }
        }
    }

    private void loginWithFallback(String loginPath, String versionName, String name,
                                   String pkg, String buildNumber, String buildSignature) {
        ArrayList<String> paths = new ArrayList<>();
        if (!TextUtils.isEmpty(loginPath)) paths.add(loginPath);
        if (!paths.contains("/app/userInfo")) paths.add("/app/userInfo");
        if (!paths.contains("/app/log")) paths.add("/app/log");

        for (String p : paths) {
            try {
                userInfo(p, versionName, name, pkg, buildNumber, buildSignature);
                if (!TextUtils.isEmpty(token)) return;
            } catch (Exception ignored) {
            }
        }
    }

    // =====================================================================
    //  加密通信核心
    // =====================================================================

    /**
     * 构建带签名的请求头。
     * sign = SHA-256(encryptedBody : timestamp : nonce : token : appKey)
     */
    private HashMap<String, String> buildHeaders(String nonce, String timestamp,
                                                 String encryptedBody, String tok) {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", userAgent);
        headers.put("Accept", "application/json");
        headers.put("Content-Type", "application/json");
        headers.put("client_type", "android");
        headers.put("uuid", uuid);
        headers.put("timestamp", timestamp);

        String sign = "";
        try {
            String raw = encryptedBody + ":" + timestamp + ":" + nonce + ":" + tok + ":" + appKey;
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                String hex = Integer.toHexString(b & 0xFF);
                if (hex.length() == 1) sb.append('0');
                sb.append(hex);
            }
            sign = sb.toString();
        } catch (Exception ignored) {
        }

        headers.put("sign", sign);
        headers.put("nonce", nonce);
        headers.put("appkey", appKey);
        headers.put("version", version);
        headers.put("api_version", "v1");
        return headers;
    }

    /**
     * 加密 → POST → 解密 的统一请求入口。
     */
    private JSONObject apiCall(String path, String tok, JSONObject body) throws Exception {
        String result = postEncrypted(path, body, tok);
        return TextUtils.isEmpty(result) ? new JSONObject() : new JSONObject(result);
    }

    /**
     * 执行加密 POST 并返回解密后的明文。
     */
    private String postEncrypted(String path, JSONObject body, String tok) throws Exception {
        String nonce = AesCbc.randomNonce();
        String timestamp = String.valueOf(System.currentTimeMillis());

        body.put("timestamp", timestamp);
        body.put("nonce", nonce);
        if (!body.has("token")) body.put("token", tok);

        String encrypted = AesCbc.encrypt(body.toString(), uuid);
        String url = host + path;
        HashMap<String, String> headers = buildHeaders(nonce, timestamp, encrypted, tok);

        String resp = OkHttp.post(url, encrypted, headers).getBody();
        if (TextUtils.isEmpty(resp)) return "";
        return AesCbc.decrypt(resp, uuid);
    }

    // =====================================================================
    //  播放解析
    // =====================================================================

    /**
     * 通过 parser_api 解析视频直链。
     */
    private String resolveParseUrl(String videoUrl, JSONObject player) {
        JSONArray parses = config.optJSONArray("parses");
        if (parses == null) return "";

        String[] parseIds = player.optString("parseUrl", "").split(",");
        List<String> idList = (parseIds.length == 0 || TextUtils.isEmpty(parseIds[0]))
                ? null : Arrays.asList(parseIds);

        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", userAgent);
        headers.put("Accept", "application/json");

        for (int i = 0; i < parses.length(); i++) {
            JSONObject p = parses.optJSONObject(i);
            if (p == null) continue;
            String id = String.valueOf(p.optInt("id"));
            if (idList != null && !idList.contains(id)) continue;

            String apiUrl = p.optString("api_url");
            if (TextUtils.isEmpty(apiUrl)) continue;

            try {
                String fullUrl = apiUrl + videoUrl;
                if (!fullUrl.startsWith("http")) continue;

                String resp = OkHttp.string(fullUrl, null, headers);
                if (TextUtils.isEmpty(resp)) continue;

                String trimmed = resp.trim();
                // 解析接口直接返回 302/直链
                if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                    return trimmed;
                }
                // 解析接口返回 JSON
                JSONObject json = new JSONObject(trimmed);
                String direct = json.optString("url", "");
                if (TextUtils.isEmpty(direct) && json.has("data")) {
                    JSONObject data = json.optJSONObject("data");
                    if (data != null) {
                        direct = data.optString("url", data.optString("play_url", ""));
                    }
                }
                if (TextUtils.isEmpty(direct) && json.optInt("code", 0) == 200) {
                    direct = json.optString("play_url", json.optString("video", ""));
                }
                if (!TextUtils.isEmpty(direct)) return direct;
            } catch (Exception ignored) {
            }
        }
        return "";
    }

    /**
     * 构建播放器返回 JSON。
     */
    private static String buildPlayerResult(String url, String epName, String epIndex, String originalUrl)
            throws Exception {
        String resultUrl;
        JSONObject result = new JSONObject();
        result.put("parse", 0);
        result.put("jx", 0);

        if (TextUtils.isEmpty(url)) {
            resultUrl = url;
        } else {
            String lower = url.toLowerCase();
            if ((lower.contains("127.0.0.1") || lower.contains("localhost")) && !lower.contains("/proxy")) {
                resultUrl = Proxy.getUrl() + "?do=app99&url=" + URLEncoder.encode(url, "UTF-8");
            } else {
                resultUrl = url;
            }
        }
        result.put("url", resultUrl);

        if (resultUrl.toLowerCase().contains(".m3u8")) {
            result.put("format", "application/x-mpegURL");
        }

        String danmakuUrl = originalUrl;
        if (TextUtils.isEmpty(danmakuUrl)) danmakuUrl = url;
        if (!isDirectVideoUrl(url)) danmakuUrl = originalUrl;

        String danmaku = Danmaku.buildUrl(epName, epIndex, danmakuUrl);
        if (!TextUtils.isEmpty(danmaku)) result.put("danmaku", danmaku);

        return result.toString();
    }

    // =====================================================================
    //  静态工具方法
    // =====================================================================

    /** 将 JSONArray 转为筛选项列表 */
    private static ArrayList<Filter.Value> buildFilterValues(JSONArray arr) {
        ArrayList<Filter.Value> list = new ArrayList<>();
        if (arr == null) return list;
        for (int i = 0; i < arr.length(); i++) {
            String v = arr.optString(i);
            if (!TextUtils.isEmpty(v)) list.add(new Filter.Value(v, v));
        }
        return list;
    }

    /** 判断是否为公告/通知分类（需要跳过） */
    private static boolean isNoticeCategory(String name) {
        if (TextUtils.isEmpty(name)) return false;
        String t = name.trim();
        if ("公告".equals(t) || "通知".equals(t) || "notice".equalsIgnoreCase(t)) return true;
        return t.endsWith("公告") || t.endsWith("通知");
    }

    /** 判断是否为主流视频站点（需要解析） */
    private static boolean isMajorVideoSite(String url) {
        if (TextUtils.isEmpty(url)) return false;
        String lower = url.toLowerCase();
        return lower.contains("v.qq.com") || lower.contains("youku.com")
                || lower.contains("bilibili.com") || lower.contains("iqiyi.com")
                || lower.contains("mgtv.com");
    }

    /** 判断是否为直链视频地址 */
    private static boolean isDirectVideoUrl(String url) {
        if (TextUtils.isEmpty(url)) return false;
        if (!url.startsWith("http://") && !url.startsWith("https://")) return false;
        String lower = url.toLowerCase();
        return lower.contains(".m3u8") || lower.contains(".mp4") || lower.contains(".flv");
    }

    /** 将 JSONArray 转为 Vod 列表 */
    private ArrayList<Vod> parseVodList(JSONArray arr) throws JSONException {
        ArrayList<Vod> list = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject item = arr.getJSONObject(i);
            Vod vod = new Vod(
                    item.optString("id"),
                    item.optString("name"),
                    item.optString("pic"),
                    item.optString("remarks"));
            vod.setVodYear(item.optString("year"));
            vod.setVodArea(item.optString("area"));
            vod.setVodActor(item.optString("actor"));
            vod.setVodDirector(item.optString("director"));
            vod.setVodContent(item.optString("blurb"));
            vod.setTypeName(item.optString("class"));
            list.add(vod);
        }
        return list;
    }

    /**
     * 播放源排序权重：直链优先；其次根据播放器 type 和大站规则排序。
     */
    private int comparePlayUrl(int index, String playUrlBlock, String[] playFromArr) {
        boolean isDirect;
        if (TextUtils.isEmpty(playUrlBlock)) {
            isDirect = false;
        } else {
            String[] parts = playUrlBlock.split("#")[0].split("\\$");
            isDirect = parts.length >= 2 && isDirectVideoUrl(parts[1]);
        }
        if (isDirect) return 0;

        String code = index < playFromArr.length ? playFromArr[index] : "";
        if (!config.has("player") || TextUtils.isEmpty(code)) return 1;
        JSONObject player = config.optJSONObject("player").optJSONObject(code);
        if (player == null || player.optInt("type", 1) != 0) return 1;

        String[] parts = playUrlBlock.split("#")[0].split("\\$");
        if (parts.length < 2 || !isMajorVideoSite(parts[1])) return 0;
        return 1;
    }
}
