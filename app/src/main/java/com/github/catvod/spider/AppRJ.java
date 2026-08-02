package com.github.catvod.spider;

import android.content.Context;

import com.github.catvod.crawler.Spider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class RemoteCatalogSpider extends Spider {

    private final OkHttpClient httpClient = new OkHttpClient();
    private String apiBase = "https://example.com/api";

    public RemoteCatalogSpider() {
    }

    public RemoteCatalogSpider(String apiBase) {
        this.apiBase = apiBase;
    }

    @Override
    public void init(Context context, String extend) throws Exception {
        if (extend != null && !extend.trim().isEmpty()) {
            this.apiBase = extend.trim();
        }
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        JSONObject root = getJson(apiBase + "/home");
        JSONObject payload = new JSONObject();
        payload.put("class", buildCategoryList(root.optJSONArray("categories")));
        payload.put("list", buildMediaList(root.optJSONArray("items")));
        payload.put("filters", new JSONObject());
        return payload.toString();
    }

    @Override
    public String categoryContent(String tid, String page, boolean filter, HashMap<String, String> extend) throws Exception {
        String url = apiBase + "/category?tid=" + encode(tid) + "&page=" + encode(page);
        JSONObject root = getJson(url);
        JSONObject payload = new JSONObject();
        payload.put("list", buildMediaList(root.optJSONArray("items")));
        payload.put("page", root.optInt("page", 1));
        payload.put("pagecount", root.optInt("pageCount", 1));
        payload.put("limit", root.optInt("limit", 20));
        payload.put("total", root.optInt("total", 0));
        return payload.toString();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        if (ids == null || ids.isEmpty()) {
            return "{\"list\":[]}";
        }

        String id = ids.get(0);
        JSONObject root = getJson(apiBase + "/detail?id=" + encode(id));
        JSONObject payload = new JSONObject();
        payload.put("list", new JSONArray().put(buildDetailItem(root)));
        return payload.toString();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String page) throws Exception {
        String url = apiBase + "/search?q=" + encode(key) + "&page=" + encode(page);
        JSONObject root = getJson(url);
        JSONObject payload = new JSONObject();
        payload.put("list", buildMediaList(root.optJSONArray("items")));
        payload.put("page", root.optInt("page", 1));
        payload.put("pagecount", root.optInt("pageCount", 1));
        payload.put("limit", root.optInt("limit", 20));
        payload.put("total", root.optInt("total", 0));
        return payload.toString();
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        String url = apiBase + "/play?flag=" + encode(flag) + "&id=" + encode(id);
        JSONObject root = getJson(url);
        JSONObject payload = new JSONObject();
        payload.put("parse", 0);
        payload.put("playUrl", "");
        payload.put("url", root.optString("url", id));
        payload.put("header", new JSONObject());
        return payload.toString();
    }

    private JSONArray buildCategoryList(JSONArray source) throws Exception {
        JSONArray result = new JSONArray();
        if (source == null) {
            return result;
        }
        for (int i = 0; i < source.length(); i++) {
            JSONObject item = source.optJSONObject(i);
            if (item == null) {
                continue;
            }
            JSONObject entry = new JSONObject();
            entry.put("type_id", item.optString("type_id", ""));
            entry.put("type_name", item.optString("type_name", ""));
            result.put(entry);
        }
        return result;
    }

    private JSONArray buildMediaList(JSONArray source) throws Exception {
        JSONArray result = new JSONArray();
        if (source == null) {
            return result;
        }
        for (int i = 0; i < source.length(); i++) {
            JSONObject item = source.optJSONObject(i);
            if (item == null) {
                continue;
            }
            result.put(buildMediaItem(item));
        }
        return result;
    }

    private JSONObject buildMediaItem(JSONObject item) throws Exception {
        JSONObject out = new JSONObject();
        out.put("vod_id", item.optString("id", ""));
        out.put("vod_name", item.optString("title", ""));
        out.put("vod_pic", item.optString("cover", ""));
        out.put("vod_remarks", item.optString("remark", ""));
        out.put("vod_content", item.optString("desc", ""));
        out.put("vod_year", item.optString("year", ""));
        out.put("vod_area", item.optString("area", ""));
        out.put("vod_actor", item.optString("actor", ""));
        out.put("vod_director", item.optString("director", ""));
        out.put("vod_play_from", "default");
        out.put("vod_play_url", item.optString("play", ""));
        return out;
    }

    private JSONObject buildDetailItem(JSONObject item) throws Exception {
        return buildMediaItem(item);
    }

    private JSONObject getJson(String url) throws Exception {
        Request request = new Request.Builder().url(url).build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                return new JSONObject();
            }
            String body = response.body() == null ? "{}" : response.body().string();
            if (body == null || body.trim().isEmpty()) {
                return new JSONObject();
            }
            return new JSONObject(body);
        } catch (IOException e) {
            return new JSONObject();
        }
    }

    private String encode(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8.toString());
        } catch (Exception e) {
            return "";
        }
    }
}
