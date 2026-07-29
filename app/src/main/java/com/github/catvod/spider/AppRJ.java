package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class AppRJ extends Spider {

    private static final String DEFAULT_BASE = "http://v.rbotv.cn";
    private static final String USER_AGENT = "okhttp-okgo/jeasonlzy";
    private static final String SIGN_SALT = "7gp0bnd2sr85ydii2j32pcypscoc4w6c7g5spl";

    private String baseUrl = DEFAULT_BASE;
    private final OkHttpClient client = new OkHttpClient();

    public void init(Context context, String extend) {
        if (!TextUtils.isEmpty(extend)) {
            baseUrl = extend.trim();
        }
    }

    public String homeContent(boolean filter) {
        ArrayList<Class> classes = new ArrayList<>();
        return Result.string(classes, new JSONObject());
    }

    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        return Result.string(new ArrayList<Vod>());
    }

    public String searchContent(String keyword, boolean quick) {
        ArrayList<Vod> list = new ArrayList<>();
        if (TextUtils.isEmpty(keyword)) return Result.string(list);

        try {
            HashMap<String, String> params = signedParams();
            params.put("keyword", keyword);

            JSONObject root = new JSONObject(post("/v3/home/search", params));
            JSONObject data = root.optJSONObject("data");
            JSONArray arr = null;
            if (data != null) {
                arr = data.optJSONArray("list");
                if (arr == null) arr = data.optJSONArray("data");
            }
            if (arr == null) arr = root.optJSONArray("data");
            if (arr == null) return Result.string(list);

            for (int i = 0; i < arr.length(); i++) {
                JSONObject item = arr.optJSONObject(i);
                if (item == null) continue;

                String pic = firstNonEmpty(item.optString("vod_pic"), item.optString("vod_pic_thumb"));
                String id = item.optString("vod_id");
                String name = item.optString("vod_name");
                String remarks = item.optString("vod_remarks");

                if (!TextUtils.isEmpty(id) && !TextUtils.isEmpty(name)) {
                    list.add(new Vod(id, name, pic, remarks));
                }
            }
        } catch (Exception ignored) {
        }
        return Result.string(list);
    }

    public String detailContent(List<String> ids) {
        ArrayList<Vod> empty = new ArrayList<>();
        if (ids == null || ids.isEmpty()) return Result.string(empty);

        try {
            HashMap<String, String> params = signedParams();
            params.put("vod_id", ids.get(0));

            JSONObject root = new JSONObject(post("/v3/home/vod_details", params));
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
                        String parsed = parseVideo(parser + playUrl);
                        if (!TextUtils.isEmpty(parsed)) {
                            playUrl = parsed;
                        } else if (!playUrl.startsWith("http")) {
                            playUrl = parser + playUrl;
                        }
                    }
                }
            }

            return Result.get().url(playUrl).parse(0).header(headers).string();
        } catch (Exception ignored) {
            return Result.get().url(id).parse(0).string();
        }
    }

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
                        if (!TextUtils.isEmpty(url)) {
                            itemList.add(name + "$" + buildPlayerId(url, parseUrls));
                        }
                    } else {
                        String url = urls.optString(j);
                        if (!TextUtils.isEmpty(url)) {
                            itemList.add("第" + (j + 1) + "集$" + buildPlayerId(url, parseUrls));
                        }
                    }
                }
            }

            if (itemList.size() == 0) {
                String url = group.optString("url");
                if (!TextUtils.isEmpty(url)) {
                    itemList.add(from + "$" + buildPlayerId(url, parseUrls));
                }
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

    private String post(String path, Map<String, String> params) throws Exception {
        MultipartBody.Builder form = new MultipartBody.Builder().setType(MultipartBody.FORM);
        for (Map.Entry<String, String> entry : params.entrySet()) {
            form.addFormDataPart(entry.getKey(), entry.getValue());
        }
        RequestBody body = form.build();
        Request request = new Request.Builder()
                .url(baseUrl + path)
                .post(body)
                .addHeader("User-Agent", USER_AGENT)
                .build();
        Response response = client.newCall(request).execute();
        return response.body() == null ? "" : response.body().string();
    }

    private String get(String url) throws Exception {
        Request request = new Request.Builder()
                .url(url)
                .addHeader("User-Agent", USER_AGENT)
                .build();
        Response response = client.newCall(request).execute();
        return response.body() == null ? "" : response.body().string();
    }

    private String parseVideo(String parseUrl) {
        try {
            JSONObject obj = new JSONObject(get(parseUrl));
            return obj.optString("url");
        } catch (Exception e) {
            return "";
        }
    }

    private HashMap<String, String> signedParams() {
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        HashMap<String, String> params = new HashMap<>();
        params.put("timestamp", timestamp);
        params.put("sign", md5(SIGN_SALT + timestamp));
        return params;
    }

    private String md5(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] bytes = md.digest(value.getBytes("UTF-8"));
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

    private String join(String sep, List<String> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(sep);
            sb.append(list.get(i));
        }
        return sb.toString();
    }
}
