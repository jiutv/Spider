package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;
import android.util.Base64;

import com.github.catvod.bean.Result;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;

import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class YunZhenXiang extends Spider {

    private String textURL = "";
    private String resourceURL = "";
    private String version = "1.0.0";
    private String aesKey = "";
    private final Map<String, String> headers = new HashMap<>();
    private boolean initialized = false;
    private String ext = "";

    private String decrypt(String str) throws Exception {
        if (aesKey == null || aesKey.isEmpty()) {
            throw new Exception("缺少AES密钥");
        }
        byte[] data = Base64.decode(str, 0);
        byte[] iv = Arrays.copyOfRange(data, 0, 12);
        byte[] cipherText = Arrays.copyOfRange(data, 12, data.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey.getBytes("UTF-8"), "AES"), new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(cipherText), "UTF-8");
    }

    private String fetch(String url, Map<String, String> extra) {
        if (url == null || url.isEmpty()) return null;
        Map<String, String> h = new HashMap<>(headers);
        if (extra != null) h.putAll(extra);
        String result = OkHttp.string(url, h);
        return TextUtils.isEmpty(result) ? null : result;
    }

    private synchronized void ensureInit() {
        if (initialized) return;
        try {
            String json = ext;
            if (json != null && !json.trim().isEmpty()) {
                if (json.startsWith("http") && !json.trim().startsWith("{")) {
                    json = fetch(json, null);
                }
                if (json != null && json.trim().startsWith("{")) {
                    JSONObject obj = new JSONObject(json);
                    if (obj.has("index_url")) {
                        String indexJson = fetch(obj.optString("index_url"), null);
                        if (indexJson != null && !indexJson.isEmpty()) {
                            JSONObject index = new JSONObject(indexJson);
                            JSONObject app = index.optJSONObject("app");
                            if (app != null) {
                                textURL = app.optString("textURL", textURL);
                                resourceURL = app.optString("img", resourceURL);
                            }
                            JSONArray qudao = index.optJSONArray("qudao");
                            if (qudao != null && qudao.length() > 0) {
                                version = qudao.getJSONObject(0).optString("banben", version);
                            }
                        }
                    }
                    if (obj.has("host")) textURL = obj.optString("host");
                    if (obj.has("img")) resourceURL = obj.optString("img");
                    if (obj.has("version")) version = obj.optString("version");
                    if (obj.has("key")) {
                        aesKey = obj.optString("key");
                    } else if (obj.has("key_api")) {
                        String keyApi = obj.optString("key_api");
                        long time = System.currentTimeMillis() / 1000;
                        String sign = md5("TvBoxSuperSecret" + time);
                        Map<String, String> signHeaders = new HashMap<>();
                        signHeaders.put("X-Spider-Time", String.valueOf(time));
                        signHeaders.put("X-Spider-Sign", sign);
                        String keyResp = fetch(keyApi, signHeaders);
                        if (keyResp != null && !keyResp.trim().isEmpty()) {
                            String key = keyResp.trim();
                            if (key.startsWith("{")) {
                                key = new JSONObject(key).optString("key", "");
                            }
                            aesKey = key;
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        initialized = true;
    }

    private String buildList(String str, int page) throws JSONException {
        JSONObject result = new JSONObject();
        JSONArray list = new JSONArray();
        if (str != null && !str.isEmpty()) {
            try {
                JSONArray arr = new JSONArray(str);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject item = arr.getJSONObject(i);
                    JSONObject vod = new JSONObject();
                    vod.put("vod_id", item.optString("videoId"));
                    vod.put("vod_name", item.optString("videoName"));
                    String pic = item.optString("fengmiantu");
                    if (!pic.startsWith("http")) pic = resourceURL + pic;
                    vod.put("vod_pic", pic);
                    vod.put("vod_remarks", item.optString("serialDesc"));
                    JSONObject style = new JSONObject();
                    style.put("type", "movie");
                    style.put("ratio", 0.75d);
                    vod.put("style", style);
                    list.put(vod);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        result.put("list", list);
        result.put("page", page);
        int pageCount = page;
        if (list.length() > 0) pageCount = page + 1;
        result.put("pagecount", pageCount);
        result.put("limit", 20);
        result.put("total", 9999);
        return result.toString();
    }

    private String md5(String str) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(str.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                String hex = Integer.toHexString(b & 255);
                if (hex.length() == 1) sb.append("0");
                sb.append(hex);
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        ensureInit();
        if (textURL == null || textURL.isEmpty()) return "{}";
        int page = Integer.parseInt(pg);
        String year = "全部";
        String sort = "最新";
        if (extend != null) {
            if (extend.containsKey("year")) year = extend.get("year");
            if (extend.containsKey("sort")) sort = extend.get("sort");
        }
        String url;
        if ("少儿".equals(tid)) {
            url = String.format("%s/cache/zhaopian/%s/全部/全部/全部/全部/%s/%s/%d.json", textURL, tid, year, sort, page);
        } else {
            url = String.format("%s/cache/zhaopian/%s/全部/全部/%s/%s/%d.json", textURL, tid, year, sort, page);
        }
        try {
            return buildList(fetch(url, null), page);
        } catch (JSONException e) {
            e.printStackTrace();
            return "{}";
        }
    }

    @Override
    public String detailContent(List<String> ids) {
        ensureInit();
        if (textURL == null || textURL.isEmpty()) return "{}";
        String id = ids.get(0);
        if (id == null || id.isEmpty()) return "";
        String url = String.format("%s/cache/videos/%d/%s.json?version=%s&baoming=com.baiyunvideo.app&channel=fenxiang", textURL, Integer.parseInt(id) / 1000, id, version);
        JSONObject result;
        try {
            result = new JSONObject();
            JSONArray list = new JSONArray();
            String resp = fetch(url, null);
            if (resp != null) {
                try {
                    JSONObject data = new JSONObject(decrypt(resp.trim()));
                    JSONObject vod = new JSONObject();
                    vod.put("vod_id", id);
                    vod.put("vod_name", data.optString("videoName"));
                    String pic = data.optString("fengmiantu");
                    if (!pic.startsWith("http")) pic = resourceURL + pic;
                    vod.put("vod_pic", pic);
                    vod.put("type_name", data.optString("class"));
                    vod.put("vod_remarks", data.optString("remarks"));
                    vod.put("vod_content", String.format("主演：%s\n地区：%s\n简介：%s", data.optString("actor", "未知"), data.optString("region", ""), data.optString("blurb", "")));
                    vod.put("vod_play_from", "云帧享");
                    JSONArray playUrlList = data.optJSONArray("playUrlList");
                    List<String> episodes = new java.util.ArrayList<>();
                    if (playUrlList != null) {
                        for (int i = 0; i < playUrlList.length(); i++) {
                            JSONObject ep = playUrlList.getJSONObject(i);
                            StringBuilder sb = new StringBuilder();
                            sb.append("第").append(i + 1).append("集");
                            episodes.add(String.format("%s$%s@@%s@@%d", ep.optString("name", sb.toString()), id, ep.optString("ji"), i));
                        }
                    }
                    vod.put("vod_play_url", TextUtils.join("#", episodes));
                    list.put(vod);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            result.put("list", list);
            return result.toString();
        } catch (JSONException e) {
            e.printStackTrace();
            return "{}";
        }
    }

    @Override
    public String homeContent(boolean filter) {
        ensureInit();
        try {
            JSONObject result = new JSONObject();
            JSONArray classes = new JSONArray();
            JSONObject filters = new JSONObject();
            String[] types = {"剧集", "电影", "综艺", "动漫", "少儿", "纪录片"};

            JSONArray yearFilter = new JSONArray();
            JSONObject yearItem = new JSONObject();
            yearItem.put("n", "全部");
            yearItem.put("v", "全部");
            yearFilter.put(yearItem);
            for (int i = 2026; i >= 2010; i--) {
                JSONObject y = new JSONObject();
                y.put("n", String.valueOf(i));
                y.put("v", String.valueOf(i));
                yearFilter.put(y);
            }
            JSONObject yearObj = new JSONObject();
            yearObj.put("key", "year");
            yearObj.put("name", "年份");
            yearObj.put("value", yearFilter);

            JSONArray sortFilter = new JSONArray();
            JSONObject sortNew = new JSONObject();
            sortNew.put("n", "最新");
            sortNew.put("v", "最新");
            sortFilter.put(sortNew);
            JSONObject sortHot = new JSONObject();
            sortHot.put("n", "最热");
            sortHot.put("v", "最热");
            sortFilter.put(sortHot);
            JSONObject sortObj = new JSONObject();
            sortObj.put("key", "sort");
            sortObj.put("name", "排序");
            sortObj.put("value", sortFilter);

            JSONArray filterArr = new JSONArray();
            filterArr.put(yearObj);
            filterArr.put(sortObj);

            for (String type : types) {
                JSONObject cls = new JSONObject();
                cls.put("type_name", type);
                cls.put("type_id", type);
                classes.put(cls);
                filters.put(type, filterArr);
            }
            result.put("class", classes);
            if (filter) result.put("filters", filters);
            return result.toString();
        } catch (JSONException e) {
            e.printStackTrace();
            return "{}";
        }
    }

    @Override
    public void init(Context context, String extend) {
        try {
            super.init(context, extend);
        } catch (Exception e) {
            e.printStackTrace();
        }
        this.ext = extend;
        headers.put("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/118.0.0.0 Mobile Safari/537.36");
        headers.put("Accept-Language", "zh-CN,zh;q=0.9");
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        ensureInit();
        Map<String, String> header = new HashMap<>();
        header.put("User-Agent", headers.get("User-Agent"));
        if (aesKey != null && !aesKey.isEmpty()) {
            String[] parts = id.split("@@");
            if (parts.length >= 3) {
                String sid = parts[0];
                String ji = parts[1];
                String jiIndex = parts[2];
                StringBuilder sb = new StringBuilder();
                Random random = new Random();
                for (int i = 0; i < 16; i++) {
                    sb.append("abcdefghijklmnopqrstuvwxyz0123456789".charAt(random.nextInt(36)));
                }
                String androidId = sb.toString();
                String vuk = md5(sid + aesKey);
                String url = String.format("%s/vc/api/video/playurl?sid=%s&ji=%s&jiIndex=%s&t=0&y=0&isjiid=1&androidId=%s&version=%s&baoming=com.baiyunvideo.app&channel=fenxiang", textURL, sid, ji, jiIndex, androidId, version);
                Map<String, String> playHeaders = new HashMap<>();
                playHeaders.put("vuk", vuk);
                String resp = fetch(url, playHeaders);
                String playUrl = "";
                if (resp != null && !resp.isEmpty()) {
                    try {
                        JSONObject data = new JSONObject(resp).optJSONObject("data");
                        if (data != null && !TextUtils.isEmpty(data.optString("url"))) {
                            playUrl = data.optString("url");
                        }
                    } catch (Exception e) {
                        playUrl = "";
                    }
                }
                return Result.get().url(playUrl).parse(0).header(header).string();
            }
        }
        return Result.get().url("").parse(0).header(header).string();
    }

    @Override
    public String searchContent(String key, boolean quick) {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) {
        ensureInit();
        if (textURL == null || textURL.isEmpty()) return "{}";
        try {
            return buildList(fetch(String.format("%s/vc/api/search/%s/%s.json", textURL, key, pg), null), Integer.parseInt(pg));
        } catch (JSONException e) {
            e.printStackTrace();
            return "{}";
        }
    }
}
