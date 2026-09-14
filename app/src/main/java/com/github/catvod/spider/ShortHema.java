package com.github.catvod.spider;

import android.content.Context;
import android.os.Build;
import android.text.TextUtils;
import android.util.Base64;
import com.github.catvod.crawler.Spider;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.json.JSONArray;
import org.json.JSONObject;

public class ShortHema extends Spider {
    private static final String BASE_URL = "https://freevideo.zqqds.cn";
    private static final byte[] AES_KEY = "dzkjgfyxgshylgzm".getBytes(StandardCharsets.UTF_8);
    private static final byte[] AES_IV = "apiupdownedcrypt".getBytes(StandardCharsets.UTF_8);
    private static volatile String sessionToken;

    private static void resetSession() {
        sessionToken = null;
    }

    private static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return value == null ? "" : value;
        }
    }

    private static String compactJson(JSONObject jsonObject) throws Exception {
        return jsonObject.toString().replace(", ", ",").replace(": ", ":");
    }

    private static String encryptAesCbc(String plain) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(AES_KEY, "AES"), new IvParameterSpec(AES_IV));
        return Base64.encodeToString(cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP).trim();
    }

    private static String decryptAesCbc(String cipherText) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(AES_KEY, "AES"), new IvParameterSpec(AES_IV));
        byte[] decoded = Base64.decode(cipherText, Base64.NO_WRAP);
        return new String(cipher.doFinal(decoded), StandardCharsets.UTF_8);
    }

    private static String jsonRequest(String url, String body, Map<String, String> headers) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);
        connection.setRequestMethod("POST");
        connection.setDoInput(true);
        connection.setDoOutput(true);
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    connection.setRequestProperty(entry.getKey(), entry.getValue());
                }
            }
        }
        if (body != null && !body.isEmpty()) {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            connection.setRequestProperty("Content-Length", String.valueOf(bytes.length));
            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(bytes);
            }
        }
        int statusCode = connection.getResponseCode();
        InputStream stream = (statusCode >= 200 && statusCode < 300) ? connection.getInputStream() : connection.getErrorStream();
        if (stream == null) {
            return "";
        }
        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        }
        return response.toString();
    }

    private static String buildInitSessionPayload() throws Exception {
        JSONObject json = new JSONObject();
        json.put("version", "3.6.0");
        json.put("pname", "com.dz.hmjc");
        json.put("channelCode", "HMJC1000001");
        json.put("utdidTmp", "A" + new SimpleDateFormat("yyyyMMddHHmmss", Locale.US).format(new Date()) + "TVBX");
        json.put("token", "");
        json.put("utdid", "7d2e96f3ed64e228ef1b337de386cdce");
        json.put("os", "android");
        json.put("osv", 30);
        json.put("brand", "Redmi");
        json.put("model", "M2012K10C");
        json.put("manu", "Xiaomi");
        json.put("userId", "");
        json.put("launch", "shortcut");
        json.put("mchid", "HMJC1000001");
        json.put("nchid", "HMJC1000004");
        String sessionId = UUID.randomUUID().toString();
        json.put("session1", sessionId);
        json.put("session2", sessionId);
        json.put("startScene", "shortcut");
        json.put("recSwitch", true);
        json.put("installTime", System.currentTimeMillis());
        json.put("p", 36);
        return encryptAesCbc(compactJson(json));
    }

    private static synchronized String getSessionToken() throws Exception {
        if (sessionToken == null) {
            sessionToken = buildInitSessionPayload();
        }
        return sessionToken;
    }

    private static JSONObject portalRequest(String path, JSONObject body, boolean retry) throws Exception {
        String encrypted = encryptAesCbc(compactJson(body));
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "okhttp/4.10.0");
        headers.put("Content-Type", "application/json; charset=utf-8");
        headers.put("alg", "HG45LKBS");
        headers.put("datas", getSessionToken());
        headers.put("x-request-id", UUID.randomUUID().toString());

        String response = jsonRequest(BASE_URL + path, encrypted, headers);
        JSONObject root = new JSONObject(response);
        if (root.has("code") && root.optInt("code") != 0 && !root.has("data") && !root.has("datas")) {
            int code = root.optInt("code");
            if (code == 8 && !retry) {
                resetSession();
                return portalRequest(path, body, true);
            }
            throw new Exception("hema api code=" + code + " msg=" + root.optString("msg"));
        }

        String payload = root.optString("data");
        if (TextUtils.isEmpty(payload)) {
            payload = root.optString("datas");
        }
        if (TextUtils.isEmpty(payload)) {
            return root;
        }
        return new JSONObject(decryptAesCbc(payload));
    }

    private static String findHttpUrl(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String) {
            String str = (String) value;
            return str.startsWith("http") ? str : "";
        }
        if (value instanceof JSONArray) {
            JSONArray items = (JSONArray) value;
            for (int i = 0; i < items.length(); i++) {
                String url = findHttpUrl(items.opt(i));
                if (!TextUtils.isEmpty(url)) {
                    return url;
                }
            }
        }
        if (value instanceof JSONObject) {
            JSONObject obj = (JSONObject) value;
            JSONArray names = obj.names();
            if (names != null) {
                for (int i = 0; i < names.length(); i++) {
                    String url = findHttpUrl(obj.opt(names.optString(i)));
                    if (!TextUtils.isEmpty(url)) {
                        return url;
                    }
                }
            }
        }
        return "";
    }

    private static ArrayList<JSONObject> parseVideoList(JSONArray array) throws Exception {
        ArrayList<JSONObject> result = new ArrayList<>();
        if (array == null) {
            return result;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.getJSONObject(i);
            String bookId = item.optString("bookId");
            String bookName = item.optString("bookName");
            String cover = item.optString("coverWap");
            String remark = item.optString("finishStatusCn");
            if (!TextUtils.isEmpty(item.optString("updateNum"))) {
                StringBuilder sb = new StringBuilder();
                if (!TextUtils.isEmpty(remark)) {
                    sb.append(remark);
                    sb.append("/");
                }
                sb.append(item.optString("updateNum"));
                sb.append("集");
                remark = sb.toString();
            }
            String score = item.optString("videoStarsNum");
            JSONObject entry = new JSONObject();
            entry.put("vod_id", bookId);
            entry.put("vod_name", bookName);
            entry.put("vod_pic", cover);
            entry.put("vod_remarks", remark);
            if (!TextUtils.isEmpty(score)) {
                entry.put("vod_score", score + "分");
            }
            result.add(entry);
        }
        return result;
    }

    private static ArrayList<JSONObject> collectList(JSONObject root) throws Exception {
        ArrayList<JSONObject> result = new ArrayList<>();
        JSONArray columnData = root.optJSONArray("columnData");
        if (columnData != null) {
            for (int i = 0; i < columnData.length(); i++) {
                JSONObject column = columnData.optJSONObject(i);
                if (column != null) {
                    JSONArray videoData = column.optJSONArray("videoData");
                    if (videoData != null) {
                        result.addAll(parseVideoList(videoData));
                    }
                }
            }
        }
        if (result.isEmpty()) {
            JSONArray videoData = root.optJSONArray("videoData");
            if (videoData != null) {
                result.addAll(parseVideoList(videoData));
            }
        }
        return result;
    }

    private static String buildEpisodeString(String bookId, String chapterId) {
        return bookId + "@" + chapterId;
    }

    private static JSONArray arrayOf(JSONObject... items) throws Exception {
        JSONArray array = new JSONArray();
        for (JSONObject item : items) {
            if (item != null) {
                array.put(item);
            }
        }
        return array;
    }

    public String categoryContent(String str, String str2, boolean z, HashMap<String, String> map) throws Exception {
        int groupId = Integer.parseInt(str);
        int classId = 0;
        if (map != null && map.containsKey("class")) {
            String value = map.get("class");
            if (!TextUtils.isEmpty(value) && value.contains("@")) {
                classId = Integer.parseInt(value.split("@")[0]);
            }
        }
        int page = Integer.parseInt(str2);

        JSONObject body = new JSONObject();
        body.put("recSwitch", true);
        body.put("pageFlag", page > 1 ? String.valueOf(page - 1) : "");
        body.put("theaterSubscriptSwitch", true);
        body.put("channelGroupId", groupId);
        if (classId > 0) {
            body.put("channelId", classId);
        }

        JSONObject root = portalRequest("/free-video-portal/portal/1125", body, false);
        ArrayList<JSONObject> list = collectList(root);
        boolean hasMore = root.optBoolean("hasMore", list.size() >= 18);
        int nextPage = hasMore ? page + 1 : page;

        JSONObject result = new JSONObject();
        result.put("page", page);
        result.put("pagecount", nextPage);
        result.put("limit", 18);
        result.put("total", nextPage * 18);
        result.put("list", new JSONArray(list));
        return result.toString();
    }

    public String detailContent(List<String> list) throws Exception {
        String bookId = list.get(0);
        JSONObject body = new JSONObject();
        body.put("bookId", bookId);
        body.put("needNextChapter", 0);
        body.put("isNeedAlias", "");
        body.put("bookAlias", "");
        body.put("resolutionRate", "1080P");

        JSONObject root = portalRequest("/free-video-portal/portal/1131", body, false);
        JSONObject info = root.optJSONObject("videoInfo");
        if (info == null) {
            info = root;
        }

        JSONObject item = new JSONObject();
        item.put("vod_id", bookId);
        item.put("vod_name", info.optString("bookName"));
        item.put("vod_pic", info.optString("coverWap"));
        item.put("vod_content", info.optString("introduction"));
        item.put("vod_remarks", info.optString("finishStatusCn"));

        JSONArray chapterList = root.optJSONArray("chapterList");
        if (chapterList == null) {
            chapterList = info.optJSONArray("chapterList");
        }
        if (chapterList != null && chapterList.length() > 0) {
            ArrayList<String> partList = new ArrayList<>();
            for (int i = 0; i < chapterList.length(); i++) {
                JSONObject chapter = chapterList.getJSONObject(i);
                String chapterName = chapter.optString("chapterName");
                String chapterId = chapter.optString("chapterId");
                partList.add(chapterName + "$" + buildEpisodeString(bookId, chapterId));
            }
            item.put("vod_play_from", "河马");
            item.put("vod_play_url", TextUtils.join("#", partList));
        } else {
            item.put("vod_play_from", "河马");
            item.put("vod_play_url", "");
        }

        JSONArray wrapper = new JSONArray();
        wrapper.put(item);
        JSONObject result = new JSONObject();
        result.put("list", wrapper);
        return result.toString();
    }

    public String homeContent(boolean z) throws Exception {
        JSONObject body = new JSONObject();
        body.put("recSwitch", true);
        body.put("pageFlag", "");
        body.put("theaterSubscriptSwitch", true);

        JSONObject root = portalRequest("/free-video-portal/portal/1125", body, false);
        JSONArray groups = root.optJSONArray("channelGroupData");
        ArrayList<JSONObject> categories = new ArrayList<>();
        JSONArray recommendation = new JSONArray();
        if (groups != null) {
            for (int i = 0; i < groups.length(); i++) {
                JSONObject group = groups.getJSONObject(i);
                String groupId = String.valueOf(group.optInt("channelGroupId"));
                String groupName = group.optString("channelGroupName");
                if ("全部".equals(groupName)) {
                    groupName = "推荐";
                }
                JSONObject category = new JSONObject();
                category.put("type_id", groupId);
                category.put("type_name", groupName);
                categories.add(category);

                JSONArray channelData = group.optJSONArray("channelData");
                if (channelData != null && channelData.length() > 0) {
                    JSONArray subList = new JSONArray();
                    for (int j = 0; j < channelData.length(); j++) {
                        JSONObject channel = channelData.getJSONObject(j);
                        String channelId = String.valueOf(channel.optInt("channelId"));
                        String channelName = channel.optString("channelName");
                        JSONObject sub = new JSONObject();
                        sub.put("name", channelName);
                        sub.put("value", channelId + "@" + channelName);
                        subList.put(sub);
                    }
                    JSONObject subGroup = new JSONObject();
                    subGroup.put("type_id", groupId);
                    subGroup.put("type_name", groupName);
                    subGroup.put("list", subList);
                    recommendation.put(subGroup);
                }
            }
        }

        JSONObject result = new JSONObject();
        result.put("class", new JSONArray(categories));
        if (recommendation.length() > 0) {
            result.put("list", recommendation);
        }
        return result.toString();
    }

    public void init(Context context, String str) throws Exception {
        super.init(context, str);
    }

    public String playerContent(String str, String str2, List<String> list) throws Exception {
        int idx = str2.indexOf('@');
        String url = "";
        if (idx > 0 && idx < str2.length() - 1) {
            String bookId = str2.substring(0, idx);
            String chapterId = str2.substring(idx + 1);
            JSONObject body = new JSONObject();
            body.put("bookId", bookId);
            body.put("chapterId", chapterId);
            body.put("unClockType", "load");
            body.put("tierPlaySource", JSONObject.NULL);
            body.put("chapterIds", new JSONArray().put(chapterId));
            JSONObject omap = new JSONObject();
            omap.put("expId", JSONObject.NULL);
            omap.put("logId", JSONObject.NULL);
            omap.put("originName", "bigdata_rec");
            omap.put("recId", JSONObject.NULL);
            omap.put("scene", "dzmf_video_sc_reco");
            omap.put("sceneId", "dzmf_video_sc_reco");
            omap.put("strategyId", "godum7go");
            omap.put("strategyName", "omap");
            body.put("omap", omap);

            JSONObject root = portalRequest("/free-video-portal/portal/1139", body, false);
            JSONArray chapters = root.optJSONArray("chapterInfo");
            JSONObject chapterInfo = (chapters == null || chapters.length() <= 0)
                    ? root.optJSONObject("chapterInfo")
                    : chapters.optJSONObject(0);
            if (chapterInfo != null) {
                url = findHttpUrl(chapterInfo.opt("content"));
            }
            if (TextUtils.isEmpty(url) && root.has("ad")) {
                JSONObject ad = root.optJSONObject("ad");
                if (ad != null) {
                    url = findHttpUrl(ad.opt("content"));
                }
            }
        }

        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "aliplayer(appv=2.7.1&av=7.1.0&av2=7.1.0_46933858&os=android&ov=11&dm=" + Build.MODEL + ")");
        JSONObject result = new JSONObject();
        result.put("parse", 0);
        result.put("url", url);
        result.put("header", new JSONObject(headers));
        return result.toString();
    }

    public String searchContent(String str, boolean z) throws Exception {
        JSONObject body = new JSONObject();
        body.put("keyword", str == null ? "" : str.trim());
        body.put("page", 1);
        body.put("size", 15);
        body.put("hotWordType", 2);

        JSONObject root = portalRequest("/free-video-portal/portal/1803", body, false);
        JSONArray searchItems = root.optJSONArray("searchVos");
        if (searchItems == null) {
            searchItems = root.optJSONArray("content");
        }
        ArrayList<JSONObject> list = parseVideoList(searchItems);
        JSONObject result = new JSONObject();
        result.put("list", new JSONArray(list));
        return result.toString();
    }
}
