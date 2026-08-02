package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;

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

public class AppRJ extends Spider {
    private static final String KEY = "7gp0bnd2sr85ydii2j32pcypscoc4w6c7g5spl";
    private static final String DEFAULT_URL = "http://v.rbotv.cn";
    private static final String UA = "okhttp-okgo/jeasonlzy";
    private String siteUrl = DEFAULT_URL;

    private static String md5(String str) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(str.getBytes("UTF-8"));
            BigInteger bigInt = new BigInteger(1, digest);
            String hash = bigInt.toString(16);
            while (hash.length() < 32) hash = "0" + hash;
            return hash.toLowerCase();
        } catch (Exception e) {
            return "";
        }
    }

    private HashMap<String, String> getHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", UA);
        return headers;
    }

    private String post(String path, Map<String, String> params) {
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String sign = md5(KEY + timestamp);
        params.put("timestamp", timestamp);
        params.put("sign", sign);
        return OkHttp.post(siteUrl + path, params, getHeaders()).getBody();
    }

    @Override
    public void init(Context context, String extend) {
        super.init(context, extend);
        if (!TextUtils.isEmpty(extend)) {
            siteUrl = extend.replaceAll("/$", "");
        }
    }

    @Override
    public String homeContent(boolean filter) {
        return Result.string(new ArrayList<>(), new ArrayList<>());
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        int page = TextUtils.isEmpty(pg) ? 1 : Integer.parseInt(pg);
        HashMap<String, String> params = new HashMap<>();
        params.put("type_id", tid);
        params.put("page", String.valueOf(page));
        params.put("limit", "12");
        if (extend != null) {
            if (extend.containsKey("area")) params.put("area", extend.get("area"));
            if (extend.containsKey("year")) params.put("year", extend.get("year"));
            if (extend.containsKey("class")) params.put("class", extend.get("class"));
            if (extend.containsKey("lang")) params.put("lang", extend.get("lang"));
        }
        List<Vod> list = new ArrayList<>();
        int count = 1, limit = 12, total = 0;
        try {
            String json = post("/v3/home/type_search", params);
            JSONObject obj = new JSONObject(json);
            JSONObject data = obj.optJSONObject("data");
            if (data != null) {
                total = data.optInt("total");
                count = data.optInt("pagecount", page + 1);
                limit = data.optInt("limit", 12);
                JSONArray arr = data.optJSONArray("list");
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject item = arr.getJSONObject(i);
                        String vodPic = item.optString("vod_pic_thumb");
                        if (TextUtils.isEmpty(vodPic)) vodPic = item.optString("vod_pic");
                        list.add(new Vod(
                                item.optString("vod_id"),
                                item.optString("vod_name"),
                                vodPic,
                                item.optString("vod_remarks")
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
    public String detailContent(List<String> ids) {
        String vodId = ids.get(0);
        HashMap<String, String> params = new HashMap<>();
        params.put("vod_id", vodId);
        Vod vod = new Vod();
        try {
            String json = post("/v3/home/vod_details", params);
            JSONObject obj = new JSONObject(json);
            JSONObject data = obj.optJSONObject("data");
            if (data != null) {
                vod.setVodId(vodId);
                vod.setVodName(data.optString("vod_name"));
                String vodPic = data.optString("vod_pic_thumb");
                if (TextUtils.isEmpty(vodPic)) vodPic = data.optString("vod_pic");
                vod.setVodPic(vodPic);
                vod.setVodRemarks(data.optString("vod_remarks"));
                vod.setVodContent(data.optString("vod_content"));
                vod.setVodYear(data.optString("vod_year"));
                vod.setVodActor(data.optString("vod_actor"));
                vod.setVodDirector(data.optString("vod_director"));
                vod.setTypeName(data.optString("vod_class"));

                JSONArray playList = data.optJSONArray("vod_play_list");
                if (playList != null && playList.length() > 0) {
                    List<String> playFromList = new ArrayList<>();
                    List<String> playUrlList = new ArrayList<>();
                    for (int i = 0; i < playList.length(); i++) {
                        JSONObject source = playList.getJSONObject(i);
                        String sourceName = source.optString("name");
                        String urls = source.optString("urls");
                        if (TextUtils.isEmpty(sourceName)) sourceName = "线路" + (i + 1);
                        if (TextUtils.isEmpty(urls)) urls = "";
                        playFromList.add(sourceName);
                        playUrlList.add(urls);
                    }
                    vod.setVodPlayFrom(TextUtils.join("$$$", playFromList));
                    vod.setVodPlayUrl(TextUtils.join("$$$", playUrlList));
                }
            }
        } catch (Exception e) {
            //ignore
        }
        return Result.string(vod);
    }

    @Override
    public String searchContent(String key, boolean quick) {
        HashMap<String, String> params = new HashMap<>();
        params.put("keyword", key);
        params.put("page", "1");
        params.put("limit", "12");
        List<Vod> list = new ArrayList<>();
        try {
            String json = post("/v3/home/search", params);
            JSONObject obj = new JSONObject(json);
            JSONObject data = obj.optJSONObject("data");
            if (data != null) {
                JSONArray arr = data.optJSONArray("list");
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject item = arr.getJSONObject(i);
                        String vodPic = item.optString("vod_pic_thumb");
                        if (TextUtils.isEmpty(vodPic)) vodPic = item.optString("vod_pic");
                        list.add(new Vod(
                                item.optString("vod_id"),
                                item.optString("vod_name"),
                                vodPic,
                                item.optString("vod_remarks")
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
    public String playerContent(String flag, String id, List<String> vipFlags) {
        String vodUrl = "";
        String[] parts = id.split("\\|");
        if (parts.length >= 2) {
            String[] urlParts = parts[1].split("@");
            vodUrl = urlParts[0];
        }
        return Result.get().url(vodUrl).parse(0).string();
    }
}
