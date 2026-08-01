package com.github.catvod.spider;

import android.text.TextUtils;
import android.util.Base64;
import com.github.catvod.bean.Class;
import com.github.catvod.bean.Filter;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class Gz360 extends Spider {

    public static final String[] API_URLS = {"https://apinew.uozvr.com", "https://api.w32z7vtd.com", "https://api.6a7nnf7.com", "https://api.umygrx3.com", "https://api.rmedphk.com"};

    public final HashMap<String, String> subMap = new HashMap<>();
    public int urlIndex = 0;
    public String currentUrl = API_URLS[0];
    public String deviceId = "";
    public String newKey = "";
    public String token = "";
    public String tokenId = "";
    public boolean signedUp = false;

    private static final String AES_KEY = "OITxa5OqAYjhswxx";
    private static final String AES_IV = "rCMNwZASNBKZ8mXV";
    private static final String RSA_PUBLIC_KEY = "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDUM5+/y8sPsWkd1/RQS64X259EUwxFXFE5HlA65MqrxnPs0JqoSRojSDy5QhwvROlaD6TwRQHKMY2OAZ6SnQeUJsChTEFIR9qUkwrs3/MVUMxjsv6JS6Oe/juclyJGTgVmDhB55EafXsD0SQYVj/QXXsxR6ewR5E2kL52yAAD4yQIDAQAB";
    private static final String RSA_PRIVATE_KEY = "MIICdgIBADANBgkqhkiG9w0BAQEFAASCAmAwggJcAgEAAoGAe6hKrWLi1zQmjTT1ozbE4QdFeJGNxubxld6GrFGximxfMsMB6BpJhpcTouAqywAFppiKetUBBbXwYsYU1wNr648XVmPmCMCy4rY8vdliFnbMUj086DU6Z+/oXBdWU3/b1G0DN3E9wULRSwcKZT3wj/cCI1vsCm3gj2R5SqkA9Y0CAwEAAQKBgAJH+4CxV0/zBVcLiBCHvSANm0l7HetybTh/j2p0Y1sTXro4ALwAaCTUeqdBjWiLSo9lNwDHFyq8zX90+gNxa7c5EqcWV9FmlVXr8VhfBzcZo1nXeNdXFT7tQ2yah/odtdcx+vRMSGJd1t/5k5bDd9wAvYdIDblMAg+wiKKZ5KcdAkEA1cCakEN4NexkF5tHPRrR6XOY/XHfkqXxEhMqmNbB9U34saTJnLWIHC8IXys6Qmzz30TtzCjuOqKRRy+FMM4TdwJBAJQZFPjsGC+RqcG5UvVMiMPhnwe/bXEehShK86yJK/g/UiKrO87h3aEu5gcJqBygTq3BBBoH2md3pr/W+hUMWBsCQQChfhTIrdDinKi6lRxrdBnn0Ohjg2cwuqK5zzU9p/N+S9x7Ck8wUI53DKm8jUJE8WAG7WLj/oCOWEh+ic6NIwTdAkEAj0X8nhx6AXsgCYRql1klbqtVmL8+95KZK7PnLWG/IfjQUy3pPGoSaZ7fdquG8bq8oyf5+dzjE/oTXcByS+6XRQJAP/5ciy1bL3NhUhsaOVy55MHXnPjdcTX0FaLi+ybXZIfIQ2P4rb19mVq1feMbCXhz+L1rG8oat5lYKfpe8k83ZA==";

    public static ArrayList<Filter> getBaseFilters() {
        ArrayList<Filter> filters = new ArrayList<>();
        filters.add(new Filter("area", "地区", Arrays.asList(
            new Filter.Value("全部", "0"),
            new Filter.Value("大陆", "大陆"),
            new Filter.Value("香港", "香港"),
            new Filter.Value("台湾", "台湾"),
            new Filter.Value("日本", "日本"),
            new Filter.Value("韩国", "韩国")
        )));
        filters.add(new Filter("year", "年份", Arrays.asList(
            new Filter.Value("全部", "0"),
            new Filter.Value("2026", "2026"),
            new Filter.Value("2025", "2025"),
            new Filter.Value("2024", "2024"),
            new Filter.Value("2023", "2023")
        )));
        filters.add(new Filter("sort", "排序", Arrays.asList(
            new Filter.Value("综合", "d_id"),
            new Filter.Value("最新", "d_addtime"),
            new Filter.Value("最热", "d_score"),
            new Filter.Value("高分", "d_score")
        )));
        return filters;
    }

    public static String getStr(JsonObject jsonObject, String key) {
        if (jsonObject == null || !jsonObject.has(key) || jsonObject.get(key).isJsonNull()) {
            return "";
        }
        try {
            return jsonObject.get(key).getAsString();
        } catch (Exception e) {
            return String.valueOf(jsonObject.get(key));
        }
    }

    public final JsonObject callApiWithRetry(JsonObject params, String path) {
        JsonObject result = null;
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 5; j++) {
                this.currentUrl = API_URLS[this.urlIndex];
                try {
                    result = requestData(params, path, false);
                } catch (Exception e) {
                }
                if (result != null) {
                    return result;
                }
                this.urlIndex = (this.urlIndex + 1) % 5;
            }
            if (i < 2) {
                try {
                    this.signedUp = false;
                    signUp();
                    refreshToken();
                } catch (Exception e) {
                }
                this.urlIndex = 0;
            }
        }
        return null;
    }

    public String categoryContent(String str, String str2, boolean z, HashMap<String, String> map) {
        JsonObject params = new JsonObject();
        params.addProperty("tid", str);
        params.addProperty("page", str2);
        params.addProperty("sort", (map == null || !map.containsKey("sort")) ? "d_id" : map.get("sort"));
        String area = "0";
        params.addProperty("area", (map == null || !map.containsKey("area")) ? area : map.get("area"));
        String sub = this.subMap.containsKey(str) ? this.subMap.get(str) : area;
        if (map != null && map.containsKey("sub")) {
            sub = map.get("sub");
        }
        params.addProperty("sub", sub);
        String year = "0";
        if (map != null && map.containsKey("year")) {
            year = map.get("year");
        }
        params.addProperty("year", year);
        params.addProperty("pageSize", "30");
        JsonObject response = callApiWithRetry(params, "/App/IndexList/indexList");
        ArrayList<Vod> list = new ArrayList<>();
        if (response != null && response.has("list")) {
            Iterator<JsonElement> it = response.getAsJsonArray("list").iterator();
            while (it.hasNext()) {
                list.add(toVod(it.next().getAsJsonObject()));
            }
        }
        return Result.get().vod(list).page(Integer.parseInt(str2), 9999, 30, 999999).string();
    }

    public String detailContent(List<String> list) {
        String vodId = list.get(0);
        JsonObject params = new JsonObject();
        params.addProperty("token_id", this.tokenId);
        params.addProperty("vod_id", vodId);
        params.addProperty("mobile_time", String.valueOf(System.currentTimeMillis() / 1000));
        params.addProperty("token", this.token);
        JsonObject response = callApiWithRetry(params, "/App/IndexPlay/playInfo");
        if (response != null && response.has("vodInfo")) {
            JsonObject vodInfo = response.getAsJsonObject("vodInfo");
            JsonObject urlParams = new JsonObject();
            urlParams.addProperty("vurl_cloud_id", "2");
            urlParams.addProperty("vod_d_id", vodId);
            JsonObject urlResponse = callApiWithRetry(urlParams, "/App/Resource/Vurl/show");
            LinkedHashMap<String, List<String>> playMap = new LinkedHashMap<>();
            if (urlResponse != null && urlResponse.has("list")) {
                Iterator<JsonElement> it = urlResponse.getAsJsonArray("list").iterator();
                while (it.hasNext()) {
                    JsonObject item = it.next().getAsJsonObject();
                    String title = getStr(item, "title");
                    if (item.has("play")) {
                        JsonObject play = item.getAsJsonObject("play");
                        for (String key : play.keySet()) {
                            JsonObject playItem = play.getAsJsonObject(key);
                            if (!"2".equals(getStr(playItem, "show_type"))) {
                                String param = getStr(playItem, "param");
                                if (!TextUtils.isEmpty(param)) {
                                    if (!playMap.containsKey(key)) {
                                        playMap.put(key, new ArrayList<>());
                                    }
                                    playMap.get(key).add(title + "$" + param);
                                }
                            }
                        }
                    }
                }
            }
            ArrayList<String> playFromList = new ArrayList<>();
            ArrayList<String> playUrlList = new ArrayList<>();
            for (String key : playMap.keySet()) {
                playFromList.add(key);
                playUrlList.add(TextUtils.join("#", playMap.get(key)));
            }
            Vod vod = new Vod();
            vod.setVodId(vodId);
            vod.setVodName(getStr(vodInfo, "vod_name"));
            vod.setVodPic(getStr(vodInfo, "vod_pic"));
            vod.setVodContent(getStr(vodInfo, "vod_use_content"));
            vod.setVodActor(getStr(vodInfo, "vod_actor"));
            vod.setVodDirector(getStr(vodInfo, "vod_director"));
            vod.setVodArea(getStr(vodInfo, "vod_area"));
            vod.setVodYear(getStr(vodInfo, "vod_year"));
            vod.setVodRemarks(getStr(vodInfo, "vod_scroe"));
            vod.setVodPlayFrom(TextUtils.join("$$$", playFromList));
            vod.setVodPlayUrl(TextUtils.join("$$$", playUrlList));
            return Result.string(vod);
        }
        return Result.error("详情获取失败");
    }

    public String homeContent(boolean z) {
        ArrayList<Class> classes = new ArrayList<>();
        classes.add(new Class("1", "电影"));
        classes.add(new Class("2", "电视剧"));
        classes.add(new Class("3", "动漫"));
        classes.add(new Class("4", "综艺"));
        classes.add(new Class("64", "短剧"));
        classes.add(new Class("72", "音乐"));
        classes.add(new Class("74", "AI漫剧"));
        classes.add(new Class("73", "电影解说"));
        classes.add(new Class("71", "体育解说"));
        classes.add(new Class("70", "电竞解说"));
        LinkedHashMap<String, List<Filter>> filters = new LinkedHashMap<>();

        ArrayList<Filter> f1 = getBaseFilters();
        f1.add(2, new Filter("sub", "类型", Arrays.asList(
            new Filter.Value("全部", "5"), new Filter.Value("动作片", "5"),
            new Filter.Value("喜剧片", "6"), new Filter.Value("爱情片", "7"),
            new Filter.Value("科幻片", "8"), new Filter.Value("恐怖片", "9"),
            new Filter.Value("剧情片", "10")
        )));
        filters.put("1", f1);

        ArrayList<Filter> f2 = getBaseFilters();
        f2.add(2, new Filter("sub", "类型", Arrays.asList(
            new Filter.Value("全部", "12"), new Filter.Value("国产剧", "12"),
            new Filter.Value("香港剧", "13"), new Filter.Value("台湾剧", "14"),
            new Filter.Value("欧美剧", "15"), new Filter.Value("日本剧", "16"),
            new Filter.Value("韩国剧", "17")
        )));
        filters.put("2", f2);

        ArrayList<Filter> f3 = getBaseFilters();
        f3.add(2, new Filter("sub", "类型", Arrays.asList(
            new Filter.Value("全部", "30"), new Filter.Value("中国动漫", "30"),
            new Filter.Value("日本动漫", "31"), new Filter.Value("欧美动漫", "33")
        )));
        filters.put("3", f3);

        ArrayList<Filter> f4 = getBaseFilters();
        f4.add(2, new Filter("sub", "类型", Arrays.asList(
            new Filter.Value("全部", "22"), new Filter.Value("大陆综艺", "22"),
            new Filter.Value("港台综艺", "23"), new Filter.Value("日韩综艺", "24"),
            new Filter.Value("欧美综艺", "25")
        )));
        filters.put("4", f4);

        List<Filter> sortOnly = Arrays.asList(new Filter("sort", "排序", Arrays.asList(
            new Filter.Value("综合", "d_id"),
            new Filter.Value("最新", "d_addtime"),
            new Filter.Value("最热", "d_score"),
            new Filter.Value("高分", "d_score")
        )));
        filters.put("64", sortOnly);
        filters.put("70", sortOnly);
        filters.put("71", sortOnly);
        filters.put("72", sortOnly);
        filters.put("73", sortOnly);
        filters.put("74", sortOnly);

        return Result.string(classes, filters);
    }

    public void init(android.content.Context context, String str) {
        this.subMap.put("1", "5");
        this.subMap.put("2", "12");
        this.subMap.put("3", "30");
        this.subMap.put("4", "22");
        this.subMap.put("64", "");
        this.subMap.put("70", "");
        this.subMap.put("71", "");
        this.subMap.put("72", "");
        this.subMap.put("73", "");
        this.subMap.put("74", "");
        Random random = new Random();
        this.deviceId = String.valueOf(((long) random.nextInt(10000)) + 864150060000000L);
        StringBuilder sb = new StringBuilder(40);
        for (int i = 0; i < 40; i++) {
            sb.append("0123456789ABCDEF".charAt(random.nextInt(16)));
        }
        this.newKey = sb.toString();
        try {
            signUp();
            refreshToken();
        } catch (Exception e) {
        }
    }

    public final void parseAuth(JsonObject jsonObject) throws Exception {
        if (jsonObject == null) {
            throw new Exception("认证响应为空");
        }
        String token = getStr(jsonObject, "token");
        if (TextUtils.isEmpty(token)) {
            throw new Exception("认证失败，无 token");
        }
        this.token = token;
        String tokenId = getStr(jsonObject, "app_user_id");
        if (TextUtils.isEmpty(tokenId)) {
            return;
        }
        this.tokenId = tokenId;
    }

    public String playerContent(String str, String str2, List<String> list) {
        JsonObject params = new JsonObject();
        for (String part : str2.split("&")) {
            int idx = part.indexOf('=');
            if (idx > 0) {
                params.addProperty(part.substring(0, idx), part.substring(idx + 1));
            }
        }
        JsonObject response = callApiWithRetry(params, "/App/Resource/VurlDetail/showOne");
        String url = response != null ? getStr(response, "url") : "";
        if (TextUtils.isEmpty(url)) {
            return Result.error("播放链接解析失败");
        }
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Lavf/57.83.100");
        headers.put("Referer", "http://WJiZxLXA2.com/");
        return Result.get().url(url).parse(0).header(headers).string();
    }

    public final void refreshToken() {
        try {
            parseAuth(requestData(new JsonObject(), "/App/Authentication/Authenticator/refresh", true));
        } catch (Exception e) {
        }
    }

    public final JsonObject requestData(JsonObject params, String path, boolean isAuth) throws Exception {
        if (!isAuth && (TextUtils.isEmpty(this.token) || TextUtils.isEmpty(this.tokenId))) {
            if (this.signedUp) {
                JsonObject signInParams = new JsonObject();
                signInParams.addProperty("new_key", this.newKey);
                signInParams.addProperty("old_key", "aLFBMWpxBrIDAD1Si/KVvm41");
                parseAuth(requestData(signInParams, "/App/Authentication/Device/signIn", true));
            } else {
                signUp();
            }
            refreshToken();
        }

        String paramString = params.toString();
        String charset = "UTF-8";

        Cipher aesCipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        SecretKeySpec aesKey = new SecretKeySpec(AES_KEY.getBytes(charset), "AES");
        aesCipher.init(Cipher.ENCRYPT_MODE, aesKey, new IvParameterSpec(AES_IV.getBytes(charset)));
        byte[] encrypted = aesCipher.doFinal(paramString.getBytes(charset));
        StringBuilder hexBuilder = new StringBuilder();
        for (byte b : encrypted) {
            hexBuilder.append(String.format("%02X", b));
        }
        String requestKey = hexBuilder.toString();

        JsonObject keyIv = new JsonObject();
        keyIv.addProperty("iv", AES_IV);
        keyIv.addProperty("key", AES_KEY);
        String keyIvStr = keyIv.toString();

        PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.decode(RSA_PUBLIC_KEY, 0)));
        Cipher rsaCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        rsaCipher.init(Cipher.ENCRYPT_MODE, publicKey);
        String encryptedKeys = Base64.encodeToString(rsaCipher.doFinal(keyIvStr.getBytes(charset)), 2);

        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String currentToken = this.token == null ? "" : this.token;

        String signSource = "token_id=,token=" + currentToken + ",phone_type=1,request_key=" + requestKey + ",app_id=1,time=" + timestamp + ",keys=" + encryptedKeys + "*&zvdvdvddbfikkkumtmdwqppp?|4Y!s!2br";
        byte[] md5Bytes = MessageDigest.getInstance("MD5").digest(signSource.getBytes(charset));
        StringBuilder md5Builder = new StringBuilder();
        for (byte b : md5Bytes) {
            md5Builder.append(String.format("%02x", b));
        }
        String signature = md5Builder.toString().toUpperCase(Locale.ROOT);

        LinkedHashMap<String, String> formParams = new LinkedHashMap<>();
        formParams.put("token", currentToken);
        formParams.put("token_id", "");
        formParams.put("phone_type", "1");
        formParams.put("time", timestamp);
        formParams.put("phone_model", "xiaomi-25031");
        formParams.put("keys", encryptedKeys);
        formParams.put("request_key", requestKey);
        formParams.put("signature", signature);
        formParams.put("app_id", "1");
        formParams.put("ad_version", "1");

        String url = this.currentUrl + path;
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Lavf/57.83.100");
        headers.put("code", "GZ0369");
        headers.put("deviceId", this.deviceId);
        headers.put("lang", "zh_cn");
        headers.put("Cache-Control", "no-cache");
        headers.put("Content-Type", "application/x-www-form-urlencoded");
        headers.put("Version", "2604028");
        headers.put("PackageName", "com.ae06aebdbb.y286327f5a.ofe849883320260517");
        headers.put("Ver", "3.0.3.2");
        headers.put("api-ver", "3.0.3.2");
        headers.put("Referer", this.currentUrl);

        String responseBody = OkHttp.post(url, formParams, headers).getBody();
        if (TextUtils.isEmpty(responseBody)) {
            throw new Exception("空响应");
        }

        JsonObject response = JsonParser.parseString(responseBody).getAsJsonObject();
        if (!response.has("code") || response.get("code").getAsInt() != 200) {
            throw new Exception("业务错误 code=" + response.get("code"));
        }
        if (!response.has("data")) {
            throw new Exception("无 data");
        }

        JsonObject data = response.getAsJsonObject("data");
        if (!data.has("keys") || !data.has("response_key")) {
            throw new Exception("缺加密封装");
        }

        byte[] decodedKeys = Base64.decode(data.get("keys").getAsString(), 0);
        PrivateKey privateKey = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.decode(RSA_PRIVATE_KEY, 0)));
        Cipher rsaDecipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        rsaDecipher.init(Cipher.DECRYPT_MODE, privateKey);
        StringBuilder keySb = new StringBuilder();
        for (int i = 0; i < decodedKeys.length; i += 128) {
            int end = Math.min(i + 128, decodedKeys.length);
            keySb.append(new String(rsaDecipher.doFinal(Arrays.copyOfRange(decodedKeys, i, end)), charset));
        }
        JsonObject keyObj = JsonParser.parseString(keySb.toString().trim()).getAsJsonObject();
        String respKey = keyObj.get("key").getAsString();
        String respIv = keyObj.get("iv").getAsString();

        String responseKey = data.get("response_key").getAsString();
        int len = responseKey.length() / 2;
        byte[] respData = new byte[len];
        for (int i = 0; i < len; i++) {
            int pos = i * 2;
            respData[i] = (byte) Integer.parseInt(responseKey.substring(pos, pos + 2), 16);
        }

        Cipher aesDecipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        aesDecipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(respKey.getBytes(charset), "AES"), new IvParameterSpec(respIv.getBytes(charset)));
        String decrypted = new String(aesDecipher.doFinal(respData), charset);
        if (TextUtils.isEmpty(decrypted)) {
            throw new Exception("解密为空");
        }
        return JsonParser.parseString(decrypted).getAsJsonObject();
    }

    public String searchContent(String str, boolean z) {
        JsonObject params = new JsonObject();
        params.addProperty("keywords", str);
        params.addProperty("order_val", "1");
        JsonObject response = callApiWithRetry(params, "/App/Index/findMoreVod");
        ArrayList<Vod> list = new ArrayList<>();
        if (response != null && response.has("list")) {
            Iterator<JsonElement> it = response.getAsJsonArray("list").iterator();
            while (it.hasNext()) {
                list.add(toVod(it.next().getAsJsonObject()));
            }
        }
        return Result.string(list);
    }

    public final void signUp() {
        JsonObject params = new JsonObject();
        params.addProperty("new_key", this.newKey);
        params.addProperty("old_key", "aLFBMWpxBrIDAD1Si/KVvm41");
        params.addProperty("phone_type", 1);
        params.addProperty("code", "");
        try {
            parseAuth(requestData(params, "/App/Authentication/Device/signUp", true));
            this.signedUp = true;
        } catch (Exception e) {
        }
    }

    public final Vod toVod(JsonObject jsonObject) {
        Vod vod = new Vod();
        vod.setVodId(getStr(jsonObject, "vod_id"));
        vod.setVodName(getStr(jsonObject, "vod_name"));
        vod.setVodPic(getStr(jsonObject, "vod_pic"));
        vod.setVodYear(getStr(jsonObject, "vod_year"));
        String continu = getStr(jsonObject, "vod_continu");
        String score = getStr(jsonObject, "vod_scroe");
        if (!TextUtils.isEmpty(continu) && !"0".equals(continu)) {
            score = "更新至" + continu + "集";
        } else if (TextUtils.isEmpty(score)) {
            score = "暂无备注";
        }
        vod.setVodRemarks(score);
        return vod;
    }
}
