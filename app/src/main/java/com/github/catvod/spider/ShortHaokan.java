package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;
import com.github.catvod.crawler.Spider;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class ShortHaokan extends Spider {
    private static final String BASE_URL = "https://sv.baidu.com";
    private static final String USER_AGENT = "Mozilla/5.0 (Linux; Android 11; M2012K10C Build/RP1A.200720.011; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/87.0.4280.141 Mobile Safari/537.36 haokan/7.80.0.18 (Baidu; P1 11)/imoaiX_03_11_C01K2102M/1043677m/5ACDB023CFB9D64743B08E51953F7C76%7CVSAJ32AVA/1/7.80.0.18/780001/1/immersiveMode/modeV4PlusWhite/isFirstInstall/bbqMode/bbqModeV2/blackStyle/isPlaylet Talos/1.8.7";

    private static HashMap<String, String> requestHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", USER_AGENT);
        headers.put("Content-Type", "application/x-www-form-urlencoded; charset=utf-8");
        headers.put("Cookie", "BAIDUCUID=giHCu0azv80G8SfQ0avU8gaaH8jfiv86ju2MugiR2i8-k3a35avAa1_mA");
        headers.put("Talos-Module-Version", "1.0.71.1");
        headers.put("Talos-Module-Name", "shortDrama");
        return headers;
    }

    private static HashMap<String, String> form(String key, String value) {
        HashMap<String, String> form = new HashMap<>();
        form.put(key, value);
        return form;
    }

    private static HashMap<String, String> form(String key1, String value1, String key2, String value2) {
        HashMap<String, String> form = form(key1, value1);
        form.put(key2, value2);
        return form;
    }

    private static String httpPostForm(String url, HashMap<String, String> form, HashMap<String, String> headers) {
        HttpURLConnection connection = null;
        try {
            URL requestUrl = new URL(url);
            connection = (HttpURLConnection) requestUrl.openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setInstanceFollowRedirects(false);
            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    if (entry.getKey() != null && entry.getValue() != null) {
                        connection.setRequestProperty(entry.getKey(), entry.getValue());
                    }
                }
            }
            if (form != null && !form.isEmpty()) {
                StringBuilder body = new StringBuilder();
                boolean first = true;
                for (Map.Entry<String, String> entry : form.entrySet()) {
                    if (!first) {
                        body.append('&');
                    }
                    body.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8.name()));
                    body.append('=');
                    body.append(URLEncoder.encode(entry.getValue() == null ? "" : entry.getValue(), StandardCharsets.UTF_8.name()));
                    first = false;
                }
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                connection.setRequestProperty("Content-Length", String.valueOf(bytes.length));
                try (OutputStream outputStream = connection.getOutputStream()) {
                    outputStream.write(bytes);
                }
            }
            int statusCode = connection.getResponseCode();
            InputStream inputStream = (statusCode >= 200 && statusCode < 300) ? connection.getInputStream() : connection.getErrorStream();
            if (inputStream == null) {
                return "";
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            return response.toString();
        } catch (Exception e) {
            return "";
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String findQualityUrl(JSONArray videos, String quality) throws JSONException {
        for (int i = 0; i < videos.length(); i++) {
            JSONObject video = videos.getJSONObject(i);
            if (quality.equals(video.getString("key"))) {
                return video.getString("url");
            }
        }
        return "";
    }

    private static String extractVideoUrl(JSONArray videos) throws JSONException {
        if (videos == null || videos.length() == 0) {
            return "";
        }
        String url = findQualityUrl(videos, "1080p");
        if (!url.isEmpty()) {
            return url;
        }
        url = findQualityUrl(videos, "sc");
        return !url.isEmpty() ? url : videos.getJSONObject(0).getString("url");
    }

    public String categoryContent(String str, String str2, boolean z, HashMap<String, String> map) throws JSONException {
        if (map != null && map.get("cateId") != null) {
            str = map.get("cateId");
        }
        HashMap<String, String> params = form("tag_id", str, "rn", "9");
        params.put("pn", str2);
        String response = httpPostForm(BASE_URL + "/haokan/ui-feed/playletTagsFeed?osbranch=a0", params, requestHeaders());
        int i = Integer.parseInt(str2);
        int iCeil = (int) Math.ceil(((double) Integer.MAX_VALUE) / ((double) 9));
        JSONArray videoList = new JSONObject(response).getJSONObject("data").getJSONArray("list");
        JSONArray resultList = new JSONArray();
        for (int i2 = 0; i2 < videoList.length(); i2++) {
            JSONObject video = videoList.getJSONObject(i2);
            JSONObject item = new JSONObject();
            item.put("vod_id", video.getString("playlet_id"));
            item.put("vod_name", video.getString("playlet_title"));
            item.put("vod_pic", video.getString("playlet_poster"));
            item.put("vod_remarks", video.getString("episodes_num_text"));
            resultList.put(item);
        }
        JSONObject result = new JSONObject();
        result.put("page", i);
        result.put("pagecount", iCeil);
        result.put("limit", 9);
        result.put("total", Integer.MAX_VALUE);
        result.put("list", resultList);
        return result.toString();
    }

    public String detailContent(List<String> list) throws JSONException {
        String playletId = list.get(0);
        String commonList = String.format(
                "enable_enter_playlet=0&seek_time=0&hotspot=0&auto_show_hot_point_panel=0&type=playlet&commonlist_id=%s&scene=&vid=&enable_atlas=0&mark_pn=&uk=&ctime=0&from=playlet_new&id=%s&rn=10&pn=1&direction=3",
                System.currentTimeMillis(),
                playletId);
        JSONObject commonResponse = new JSONObject(httpPostForm(BASE_URL + "/appui/api?osbranch=a0", form("video/commonlist", commonList), requestHeaders()));
        String videoId = commonResponse.getJSONObject("video/commonlist").getJSONObject("data").getJSONArray("results").getJSONObject(0).getJSONObject("content").getString("vid");
        JSONObject detail = new JSONObject(httpPostForm(BASE_URL + "/haokan/ui-video/playlet/rec/detail?osbranch=a0", form("vid", videoId, "playlet_id", playletId), requestHeaders())).getJSONObject("data");

        JSONObject item = new JSONObject();
        item.put("vod_id", playletId);
        item.put("vod_name", detail.getString("playlet_title"));
        item.put("vod_pic", detail.getString("playlet_poster"));
        item.put("vod_content", detail.getString("description"));

        JSONArray episodes = detail.getJSONArray("vid_list");
        ArrayList<String> playUrls = new ArrayList<>();
        for (int i = 0; i < episodes.length(); i++) {
            playUrls.add("第" + (i + 1) + "集$" + episodes.getString(i) + "|||" + playletId);
        }
        item.put("vod_play_from", "短剧");
        item.put("vod_play_url", TextUtils.join("#", playUrls));

        JSONArray listResult = new JSONArray();
        listResult.put(item);
        JSONObject result = new JSONObject();
        result.put("list", listResult);
        return result.toString();
    }

    public String homeContent(boolean z) throws JSONException {
        String response = httpPostForm(BASE_URL + "/haokan/ui-feed/playletShelfFeed?osbranch=a0", form("from", "feed"), requestHeaders());
        JSONArray panels = new JSONObject(response).getJSONObject("data").getJSONArray("playlet_shelf_filter_panel");
        ArrayList<JSONObject> categories = new ArrayList<>();
        for (int i = 0; i < panels.length(); i++) {
            JSONArray tagList = panels.getJSONObject(i).getJSONArray("tag_list");
            for (int j = 0; j < tagList.length(); j++) {
                JSONObject tag = tagList.getJSONObject(j);
                JSONObject category = new JSONObject();
                category.put("type_id", tag.getString("tag_id"));
                category.put("type_name", tag.getString("name"));
                categories.add(category);
            }
        }
        JSONObject wrapper = new JSONObject();
        wrapper.put("class", new JSONArray(categories));
        return wrapper.toString();
    }

    public void init(Context context, String str) throws Exception {
        super.init(context, str);
    }

    public String playerContent(String str, String str2, List<String> list) throws Exception {
        String[] parts = str2.split("\\|\\|||");
        String relate = "method=post&vid=" + parts[0] + "&immersive_mode=v4_5&tplname=feed_small_video&tag=playlet_talos&tab=detail&external_from=&is_dp_video=0&immersive_square_type=3&video_set_id=" + parts[1] + "&play_screen_type=1&play_volume_type=2&play_external_device_type=1";
        String response = httpPostForm(BASE_URL + "/appui/api?osbranch=a0", form("video/relate", relate), requestHeaders());
        JSONObject data = new JSONObject(response).getJSONObject("video/relate").getJSONObject("data");
        JSONObject result = new JSONObject();
        try {
            String url = extractVideoUrl(data.getJSONObject("cur_video").getJSONArray("clarityUrl"));
            result.put("parse", 0);
            result.put("url", url);
        } catch (Exception e) {
            result.put("parse", 0);
            result.put("url", "");
        }
        result.put("header", new JSONObject(requestHeaders()));
        return result.toString();
    }

    public String searchContent(String str, String str2) throws JSONException {
        ArrayList<JSONObject> resultList = new ArrayList<>();
        String response = httpPostForm(BASE_URL + "/haokan/ui-interact/playlet/search/sugs?osbranch=a0", form("search_word", str), requestHeaders());
        JSONArray results = new JSONObject(response).getJSONArray("data");
        for (int i = 0; i < results.length(); i++) {
            JSONObject result = results.getJSONObject(i);
            JSONObject item = new JSONObject();
            item.put("vod_id", result.getString("id"));
            item.put("vod_name", result.getString("title"));
            item.put("vod_pic", result.getString("cover_url"));
            item.put("vod_remarks", result.getString("tag"));
            resultList.add(item);
        }
        JSONObject wrapper = new JSONObject();
        wrapper.put("list", new JSONArray(resultList));
        return wrapper.toString();
    }

    public String searchContent(String str, boolean z) throws Exception {
        return searchContent(str, "1");
    }

    public String searchContent(String str, boolean z, String str2) throws Exception {
        return searchContent(str, str2);
    }
}