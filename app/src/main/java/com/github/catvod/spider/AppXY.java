package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;
import android.util.Base64;
import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import org.json.JSONArray;
import org.json.JSONObject;

public class AppXY extends Spider {

    private static final String SITE_URL = "https://app.whjzjx.cn";
    private static final String LOGIN_URL = "https://u.shytkjgs.com/user/v3/account/login";
    private static final String CONFIG_URL = "https://fs-im-kefu.7moor-fs1.com/ly/4d2c3f00-7d4c-11e5-af15-41bf63ae4ea0/1732707176882/jiduo.txt";
    private static final String AES_KEY = "B@ecf920Od8A4df7";
    private static final String UA = "Linux; Android 12; Pixel 3 XL) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/98.0.4758.101 Mobile Safari/537.36";
    private static final String LOGIN_UA = "Mozilla/5.0 (Linux; Android 9; V1938T Build/PQ3A.190705.08211809; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/91.0.4472.114 Safari/537.36";

    private String mUrl;
    private String mToken;
    private String mExtra;

    public AppXY() {
        mUrl = SITE_URL;
        mToken = "";
        mExtra = "";
    }

    private Map<String, String> getHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("platform", "1");
        headers.put("version_name", "3.8.3.1");
        headers.put("User-Agent", UA);
        headers.put("authorization", mToken);
        return headers;
    }

    private String aesEcbEncrypt(String input) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(AES_KEY.getBytes(StandardCharsets.UTF_8), "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            byte[] encrypted = cipher.doFinal(input.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(encrypted, Base64.DEFAULT);
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    @Override
    public void init(Context context, String str) {
        try {
            String config = OkHttp.string(CONFIG_URL);
            Pattern pattern2 = Pattern.compile("s2='([^']*)'");
            Matcher matcher2 = pattern2.matcher(config);
            if (matcher2.find()) {
                mExtra = matcher2.group(1);
            }
            long timestamp = System.currentTimeMillis();
            JSONObject loginBody = new JSONObject();
            loginBody.put("device", "2a50580e69d38388c94c93605241fb306");
            loginBody.put("package_name", "com.jz.xydj");
            loginBody.put("android_id", "ec1280db12795506");
            loginBody.put("install_first_open", true);
            loginBody.put("first_install_time", timestamp);
            loginBody.put("last_update_time", timestamp);
            loginBody.put("timestamp", timestamp);
            loginBody.put("report_link_url", "");
            loginBody.put("authorization", "");
            String encryptedBody = aesEcbEncrypt(loginBody.toString());
            Map<String, String> loginHeaders = new HashMap<>();
            loginHeaders.put("platform", "1");
            loginHeaders.put("user_agent", LOGIN_UA);
            loginHeaders.put("content-type", "application/json; charset=utf-8");
            String response = OkHttp.post(LOGIN_URL, encryptedBody, loginHeaders).getBody();
            JSONObject resultJson = new JSONObject(response);
            JSONObject data = resultJson.optJSONObject("data");
            if (data != null) {
                mToken = data.optString("token");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public String homeContent(boolean z) {
        ArrayList<Class> classes = new ArrayList<>();
        ArrayList<Vod> list = new ArrayList<>();
        classes.add(new Class("2", "热播"));
        classes.add(new Class("5", "阳光"));
        classes.add(new Class("1", "剧场"));
        classes.add(new Class("7", "星选"));
        classes.add(new Class("3", "新剧"));
        try {
            String url = SITE_URL + "/v1/theater/home_page?theater_class_id=1&class2_id=4&page_num=1&page_size=24";
            JSONObject response = new JSONObject(OkHttp.string(url, getHeaders()));
            JSONObject data = response.optJSONObject("data");
            if (data == null) return Result.string(classes, list);
            JSONArray jsonArray = data.optJSONArray("list");
            if (jsonArray != null) {
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject item = jsonArray.optJSONObject(i);
                    if (item == null) continue;
                    JSONObject theater = item.optJSONObject("theater");
                    if (theater == null) continue;
                    list.add(new Vod(
                        theater.optString("id"),
                        theater.optString("title"),
                        theater.optString("cover_url"),
                        theater.optString("play_amount_str")
                    ));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Result.string(classes, list);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean z, HashMap<String, String> extend) {
        ArrayList<Vod> list = new ArrayList<>();
        try {
            String url = SITE_URL + "/v1/theater/home_page?theater_class_id=" + tid + "&page_num=" + pg + "&page_size=24";
            JSONObject response = new JSONObject(OkHttp.string(url, getHeaders()));
            JSONObject data = response.optJSONObject("data");
            if (data == null) return Result.string(list);
            JSONArray jsonArray = data.optJSONArray("list");
            if (jsonArray != null) {
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject item = jsonArray.optJSONObject(i);
                    if (item == null) continue;
                    JSONObject theater = item.optJSONObject("theater");
                    if (theater == null) continue;
                    list.add(new Vod(
                        theater.optString("id"),
                        theater.optString("title"),
                        theater.optString("cover_url"),
                        theater.optString("theme")
                    ));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Result.string(list);
    }

    @Override
    public String detailContent(List<String> ids) {
        try {
            String id = ids.get(0);
            String url = SITE_URL + "/v2/theater_parent/detail?theater_parent_id=" + id;
            JSONObject response = new JSONObject(OkHttp.string(url, getHeaders()));
            JSONObject data = response.optJSONObject("data");
            if (data == null) return Result.error("详情数据为空");
            Vod vod = new Vod();
            vod.setVodId(id);
            vod.setVodName(data.optString("title"));
            vod.setVodContent("剧情：" + data.optString("introduction"));
            vod.setVodRemarks(data.optString("filing"));
            String playFrom = "星芽";
            String playUrl = "";
            String videoUrl = data.optString("video_url", "");
            if (!TextUtils.isEmpty(videoUrl)) {
                playUrl = "1$" + videoUrl;
            } else if (!TextUtils.isEmpty(mExtra)) {
                playFrom = "1";
                playUrl = mExtra;
            }
            JSONArray theaters = data.optJSONArray("theaters");
            JSONArray descTags = data.optJSONArray("desc_tags");
            if (descTags != null && theaters != null && theaters.length() > 0) {
                ArrayList<String> episodes = new ArrayList<>();
                for (int i = 0; i < theaters.length(); i++) {
                    JSONObject theater = theaters.optJSONObject(i);
                    if (theater == null) continue;
                    String num = theater.optString("num");
                    String sonUrl = theater.optString("son_video_url");
                    episodes.add(num + "$" + sonUrl);
                }
                playFrom = "星芽";
                playUrl = TextUtils.join("#", episodes);
            }
            vod.setVodPlayFrom(playFrom);
            vod.setVodPlayUrl(playUrl);
            if (descTags != null && descTags.length() > 0) {
                vod.setVodArea(descTags.optString(0));
            }
            return Result.string(vod);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("详情获取失败");
        }
    }

    @Override
    public String searchContent(String key, boolean z) {
        ArrayList<Vod> list = new ArrayList<>();
        try {
            JSONObject body = new JSONObject();
            body.put("text", key);
            String url = SITE_URL + "/v3/search";
            String response = OkHttp.post(url, body.toString(), getHeaders()).getBody();
            JSONObject result = new JSONObject(response);
            JSONObject data = result.optJSONObject("data");
            if (data == null) return Result.string(list);
            JSONObject theaterObj = data.optJSONObject("theater");
            if (theaterObj == null) return Result.string(list);
            JSONArray searchResults = theaterObj.optJSONArray("search_data");
            if (searchResults != null) {
                for (int i = 0; i < searchResults.length(); i++) {
                    JSONObject item = searchResults.optJSONObject(i);
                    if (item == null) continue;
                    list.add(new Vod(
                        item.optString("id"),
                        item.optString("title"),
                        item.optString("cover_url"),
                        item.optString("score_str")
                    ));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Result.string(list);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        return Result.get()
            .url(id)
            .header(getHeaders())
            .string();
    }
}
