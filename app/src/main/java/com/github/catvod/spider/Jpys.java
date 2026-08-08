package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

public class Jpys extends Spider {

    private static final String KEY = "cb808529bae6b6be45ecfab29a4889bc";

    private String siteUrl = "";
    private String deviceId = "";

    private static String md5(String str) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(str.getBytes());
            BigInteger bigInt = new BigInteger(1, digest);
            String hash = bigInt.toString(16);
            while (hash.length() < 32) hash = "0" + hash;
            return hash.toLowerCase();
        } catch (Exception e) {
            return "";
        }
    }

    private static String sha1(String str) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(str.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                String hex = Integer.toHexString(b & 0xFF);
                if (hex.length() < 2) sb.append('0');
                sb.append(hex);
            }
            return sb.toString().toLowerCase();
        } catch (Exception e) {
            return "";
        }
    }

    private String sign(TreeMap<String, String> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (sb.length() > 0) sb.append("&");
            sb.append(entry.getKey()).append("=").append(entry.getValue());
        }
        return sha1(md5(sb.toString()));
    }

    private HashMap<String, String> getHeaders(String timestamp, String signValue) {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("sign", signValue);
        headers.put("T", timestamp);
        headers.put("Deviceid", deviceId);
        return headers;
    }

    private String fetch(String url, TreeMap<String, String> params) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        params.put("key", KEY);
        params.put("t", timestamp);
        String signValue = sign(params);
        StringBuilder query = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (query.length() > 0) query.append("&");
            try {
                query.append(entry.getKey()).append("=").append(java.net.URLEncoder.encode(entry.getValue(), "UTF-8"));
            } catch (Exception e) {
                query.append(entry.getKey()).append("=").append(entry.getValue());
            }
        }
        return OkHttp.string(url + "?" + query.toString(), getHeaders(timestamp, signValue));
    }

    @Override
    public void init(Context context, String extend) {
        this.siteUrl = extend == null ? "" : extend.replaceAll("/$", "");
        this.deviceId = UUID.randomUUID().toString();
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        classes.add(new Class("1", "电影"));
        classes.add(new Class("2", "电视剧"));
        classes.add(new Class("3", "综艺"));
        classes.add(new Class("4", "动漫"));
        List<Vod> list = new ArrayList<>();
        try {
            TreeMap<String, String> params = new TreeMap<>();
            String json = fetch(siteUrl + "/api/mw-movie/anonymous/home/hotSearch", params);
            JSONObject obj = new JSONObject(json);
            JSONArray arr = obj.optJSONArray("data");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject item = arr.getJSONObject(i);
                    String vodId = item.optString("vodId");
                    String vodName = item.optString("vodName");
                    String vodPic = item.optString("vodPic");
                    String vodRemarks = item.optString("vodRemarks");
                    if (!TextUtils.isEmpty(vodId)) {
                        list.add(new Vod(vodId, vodName, vodPic, vodRemarks));
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return Result.string(classes, list);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        int page = TextUtils.isEmpty(pg) ? 1 : Integer.parseInt(pg);
        String area = extend != null && extend.containsKey("area") ? extend.get("area") : "";
        String year = extend != null && extend.containsKey("year") ? extend.get("year") : "";
        TreeMap<String, String> params = new TreeMap<>();
        params.put("type1", tid);
        params.put("pageNum", String.valueOf(page));
        params.put("area", area);
        params.put("year", year);
        String json = fetch(siteUrl + "/api/mw-movie/anonymous/video/list", params);
        List<Vod> list = new ArrayList<>();
        int count = 1, limit = 20, total = 0;
        try {
            JSONObject obj = new JSONObject(json);
            JSONObject data = obj.optJSONObject("data");
            if (data != null) {
                total = data.optInt("total");
                count = data.optInt("pagecount", page + 1);
                limit = data.optInt("limit", 20);
                JSONArray arr = data.optJSONArray("list");
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject item = arr.getJSONObject(i);
                        list.add(new Vod(
                            item.optString("vodId"),
                            item.optString("vodName"),
                            item.optString("vodPic"),
                            item.optString("vodRemarks")
                        ));
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return Result.string(page, count, limit, total, list);
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String vodId = ids.get(0);
        TreeMap<String, String> params = new TreeMap<>();
        params.put("id", vodId);
        String json = fetch(siteUrl + "/api/mw-movie/anonymous/video/detail", params);
        Vod vod = new Vod();
        try {
            JSONObject obj = new JSONObject(json);
            JSONObject data = obj.optJSONObject("data");
            if (data != null) {
                vod.setVodId(vodId);
                vod.setVodName(data.optString("vodName"));
                vod.setVodPic(data.optString("vodPic"));
                vod.setVodRemarks(data.optString("vodRemarks"));
                vod.setVodYear(data.optString("vodYear"));
                vod.setVodArea(data.optString("vodArea"));
                vod.setVodActor(data.optString("vodActor"));
                vod.setVodDirector(data.optString("vodDirector"));
                vod.setVodContent(data.optString("vodContent"));
                vod.setTypeName(data.optString("vodClass"));
                // Parse episode list
                JSONArray episodes = data.optJSONArray("episodeList");
                if (episodes != null && episodes.length() > 0) {
                    StringBuilder playUrl = new StringBuilder();
                    for (int i = 0; i < episodes.length(); i++) {
                        JSONObject ep = episodes.getJSONObject(i);
                        String epName = ep.optString("name");
                        String epUrl = ep.optString("nid");
                        if (TextUtils.isEmpty(epUrl)) epUrl = ep.optString("url");
                        if (playUrl.length() > 0) playUrl.append("#");
                        playUrl.append(epName).append("$").append(epUrl);
                    }
                    vod.setVodPlayFrom("在线播放");
                    vod.setVodPlayUrl(playUrl.toString());
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return Result.string(vod);
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        TreeMap<String, String> params = new TreeMap<>();
        params.put("keyword", key);
        params.put("pageNum", "1");
        params.put("pageSize", "8");
        String json = fetch(siteUrl + "/api/mw-movie/anonymous/video/searchByWord", params);
        List<Vod> list = new ArrayList<>();
        try {
            JSONObject obj = new JSONObject(json);
            JSONObject data = obj.optJSONObject("data");
            if (data != null) {
                JSONArray arr = data.optJSONArray("list");
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject item = arr.getJSONObject(i);
                        list.add(new Vod(
                            item.optString("vodId"),
                            item.optString("vodName"),
                            item.optString("vodPic"),
                            item.optString("vodRemarks")
                        ));
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return Result.string(list);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        return Result.get().url(id).header(getHeaders("", "")).string();
    }
}
