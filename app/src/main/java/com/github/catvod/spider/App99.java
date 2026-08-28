package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Filter;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class App99 extends Spider {

    private static final String TAG = "App99";
    private static final MediaType JSON_MEDIA = MediaType.parse("application/json; charset=utf-8");
    private OkHttpClient client;

    private String host;
    private String token;
    private String uuid;
    private String appKey;
    private String buildSignature;
    private String userAgent;
    private String versionName;
    private String packageName;

    private JSONObject cache = new JSONObject();

    @Override
    public void init(Context context, String extend) {
        if (TextUtils.isEmpty(extend)) return;
        try {
            JSONObject config = new JSONObject(extend);
            host = config.optString("host");
            appKey = config.optString("appkey");
            buildSignature = config.optString("buildSignature");
            versionName = config.optString("versionName", "1.2.0");
            packageName = config.optString("package");
            userAgent = config.optString("userAgent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            uuid = config.optString("uuid", UUID.randomUUID().toString().replace("-", ""));

            client = new OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .build();

            doLogin();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void doLogin() {
        try {
            JSONObject params = new JSONObject();
            params.put("appKey", appKey);
            params.put("version", versionName);
            params.put("package", packageName);

            JSONObject result = apiRequest("/login", params);
            if (result.has("data")) {
                JSONObject data = result.getJSONObject("data");
                token = data.optString("token");
                if (data.has("classify")) {
                    cache.put("classify", data.getJSONArray("classify"));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String getDefaultCategoryId() {
        try {
            if (cache.has("classify")) {
                JSONArray classify = cache.getJSONArray("classify");
                for (int i = 0; i < classify.length(); i++) {
                    JSONObject c = classify.getJSONObject(i);
                    String name = c.optString("name");
                    if (!TextUtils.isEmpty(name) && (name.contains("电影") || name.contains("电视剧"))) {
                        return c.optString("id");
                    }
                }
                return classify.getJSONObject(0).optString("id");
            }
        } catch (Exception e) { /* ignore */ }
        return "1";
    }

    @Override
    public String homeContent(boolean z) {
        try {
            JSONObject req = new JSONObject();
            req.put("page", 1);
            req.put("limit", 21);
            req.put("class", getDefaultCategoryId());
            req.put("sort", "time");
            req.put("order", 1);
            if (!TextUtils.isEmpty(token)) req.put("token", token);

            JSONObject result = apiRequest("/category", req);
            List<Vod> vodList = new ArrayList<>();
            if (result.has("data")) {
                JSONArray data = result.getJSONArray("data");
                for (int i = 0; i < data.length(); i++) {
                    JSONObject item = data.getJSONObject(i);
                    Vod vod = new Vod(
                            item.optString("id"),
                            item.optString("name"),
                            item.optString("pic"),
                            item.optString("remarks")
                    );
                    vod.setVodYear(item.optString("year"));
                    vod.setVodArea(item.optString("area"));
                    vodList.add(vod);
                }
            }

            List<Class> classList = new ArrayList<>();
            if (cache.has("classify")) {
                JSONArray classify = cache.getJSONArray("classify");
                for (int i = 0; i < classify.length(); i++) {
                    JSONObject c = classify.getJSONObject(i);
                    classList.add(new Class(c.optString("id"), c.optString("name")));
                }
            }

            return Result.string(classList, vodList);
        } catch (Exception e) {
            Log.e(TAG, "homeContent error", e);
            return Result.string(new ArrayList<>(), new ArrayList<>());
        }
    }

    @Override
    public String categoryContent(String tid, String pg, boolean z, HashMap<String, String> filter) {
        try {
            JSONObject req = new JSONObject();
            req.put("page", pg);
            req.put("limit", 21);
            req.put("class", tid);
            req.put("sort", "time");
            req.put("order", 1);
            if (filter != null) {
                for (Map.Entry<String, String> entry : filter.entrySet()) {
                    req.put(entry.getKey(), entry.getValue());
                }
            }
            if (!TextUtils.isEmpty(token)) req.put("token", token);

            JSONObject result = apiRequest("/category", req);
            List<Vod> vodList = new ArrayList<>();
            int total = 0;
            if (result.has("data")) {
                JSONArray data = result.getJSONArray("data");
                total = result.optInt("total", 0);
                for (int i = 0; i < data.length(); i++) {
                    JSONObject item = data.getJSONObject(i);
                    Vod vod = new Vod(
                            item.optString("id"),
                            item.optString("name"),
                            item.optString("pic"),
                            item.optString("remarks")
                    );
                    vodList.add(vod);
                }
            }
            int page = Integer.parseInt(pg);
            int pageCount = (total + 20) / 21;
            return Result.get().page(page, pageCount, 0, 0).vod(vodList).string();
        } catch (Exception e) {
            Log.e(TAG, "categoryContent error", e);
            return Result.string(new ArrayList<>());
        }
    }

    @Override
    public String detailContent(List<String> ids) {
        try {
            String id = ids.get(0);
            JSONObject req = new JSONObject();
            req.put("id", id);
            if (!TextUtils.isEmpty(token)) req.put("token", token);

            JSONObject result = apiRequest("/detail", req);
            if (!result.has("data")) return Result.string(new Vod());

            JSONObject data = result.getJSONObject("data");
            Vod vod = new Vod();
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

            String playFrom = data.optString("playFrom");
            String playUrl = data.optString("playUrl");
            if (TextUtils.isEmpty(playFrom) && data.has("playList")) {
                JSONArray playList = data.getJSONArray("playList");
                StringBuilder fromSb = new StringBuilder();
                StringBuilder urlSb = new StringBuilder();
                for (int i = 0; i < playList.length(); i++) {
                    JSONObject source = playList.getJSONObject(i);
                    if (i > 0) {
                        fromSb.append("$$$");
                        urlSb.append("$$$");
                    }
                    fromSb.append(source.optString("name", "源" + (i + 1)));
                    urlSb.append(source.optString("url"));
                }
                playFrom = fromSb.toString();
                playUrl = urlSb.toString();
            }
            vod.setVodPlayFrom(playFrom);
            vod.setVodPlayUrl(playUrl);

            return Result.string(vod);
        } catch (Exception e) {
            Log.e(TAG, "detailContent error", e);
            return Result.string(new Vod());
        }
    }

    @Override
    public String searchContent(String keywords, boolean z) {
        try {
            JSONObject req = new JSONObject();
            req.put("keyword", keywords);
            req.put("page", 1);
            req.put("limit", 21);
            req.put("sort", "relevance");
            if (!TextUtils.isEmpty(token)) req.put("token", token);

            JSONObject result = apiRequest("/search", req);
            List<Vod> vodList = new ArrayList<>();
            if (result.has("data")) {
                JSONArray data = result.getJSONArray("data");
                for (int i = 0; i < data.length(); i++) {
                    JSONObject item = data.getJSONObject(i);
                    vodList.add(new Vod(
                            item.optString("id"),
                            item.optString("name"),
                            item.optString("pic"),
                            item.optString("remarks")
                    ));
                }
            }
            return Result.string(vodList);
        } catch (Exception e) {
            Log.e(TAG, "searchContent error", e);
            return Result.string(new ArrayList<>());
        }
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipIds) {
        try {
            if (id.startsWith("http://") || id.startsWith("https://")) {
                return buildPlayResult(id, flag);
            }

            JSONObject req = new JSONObject();
            req.put("flag", flag);
            req.put("id", id);
            if (!TextUtils.isEmpty(token)) req.put("token", token);
            JSONObject result = apiRequest("/play", req);
            if (result.has("url")) {
                return buildPlayResult(result.getString("url"), flag);
            }
            return Result.error("播放地址获取失败");
        } catch (Exception e) {
            Log.e(TAG, "playerContent error", e);
            return Result.error(e.getMessage());
        }
    }

    private String buildPlayResult(String url, String flag) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("type", 0);
            obj.put("parse", 0);
            obj.put("url", url);
            if (url.toLowerCase().contains(".m3u8")) {
                obj.put("contentType", "application/x-mpegURL");
            }
            return obj.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    // ---------- 核心 API 请求（含加解密） ----------
    private JSONObject apiRequest(String path, JSONObject params) throws Exception {
        String apiUrl = host + path;
        String nonce = UUID.randomUUID().toString().replace("-", "");
        String timestamp = String.valueOf(System.currentTimeMillis());

        // 1. 加密请求体
        String plainText = params.toString();
        String encrypted = encrypt(plainText, uuid);

        // 2. 签名（使用加密后的数据参与签名？原代码中 m344a 使用明文？但原 m344a 传入的可能是加密后的字符串？）
        // 原 m344a 中：String strM4624i0 = ... 加密后的数据，然后 sign = md5(nonce+timestamp+encrypted+...)
        // 我们按照原逻辑：签名使用加密后的密文
        String sign = md5(nonce + timestamp + encrypted + appKey + buildSignature + versionName);

        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", userAgent);
        headers.put("Content-Type", "application/json");
        headers.put("uuid", uuid);
        headers.put("timestamp", timestamp);
        headers.put("nonce", nonce);
        headers.put("sign", sign);
        headers.put("appKey", appKey);
        if (!TextUtils.isEmpty(token)) headers.put("token", token);

        Log.d(TAG, "Request URL: " + apiUrl);
        Log.d(TAG, "Encrypted body: " + encrypted);
        Log.d(TAG, "Headers: " + headers);

        Request.Builder builder = new Request.Builder()
                .url(apiUrl)
                .post(RequestBody.create(JSON_MEDIA, encrypted));  // 发送加密后的字符串

        for (Map.Entry<String, String> entry : headers.entrySet()) {
            builder.addHeader(entry.getKey(), entry.getValue());
        }

        try (Response response = client.newCall(builder.build()).execute()) {
            String encryptedResponse = response.body() != null ? response.body().string() : "";
            Log.d(TAG, "Response code: " + response.code());
            Log.d(TAG, "Encrypted response: " + encryptedResponse);

            if (!response.isSuccessful()) {
                throw new Exception("HTTP " + response.code() + " - " + encryptedResponse);
            }
            if (TextUtils.isEmpty(encryptedResponse)) return new JSONObject();

            // 3. 解密响应
            String decrypted = decrypt(encryptedResponse, uuid);
            Log.d(TAG, "Decrypted response: " + decrypted);

            if (TextUtils.isEmpty(decrypted)) return new JSONObject();
            JSONObject result = new JSONObject(decrypted);
            int code = result.optInt("code", -1);
            if (code != 0 && code != 200) {
                throw new Exception(result.optString("msg", "请求失败"));
            }
            return result;
        }
    }

    // ---------- 加解密实现（参考原 C1376q2） ----------
    private String encrypt(String plainText, String key) throws Exception {
        // AES/CBC/PKCS5Padding，密钥取 key 的 MD5? 原代码使用 key.replace("-","") 作为密钥
        // 但原 m4624i0 中传入的密钥是 uuid（去掉横线），我们直接使用 uuid
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        // 如果长度不是16/24/32，则填充或截断，这里简单取前16字节
        if (keyBytes.length < 16) {
            byte[] tmp = new byte[16];
            System.arraycopy(keyBytes, 0, tmp, 0, keyBytes.length);
            keyBytes = tmp;
        } else if (keyBytes.length > 16) {
            byte[] tmp = new byte[16];
            System.arraycopy(keyBytes, 0, tmp, 0, 16);
            keyBytes = tmp;
        }
        SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        byte[] iv = cipher.getIV();
        byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
        // 组合 IV + 密文
        byte[] combined = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
        return Base64.encodeToString(combined, Base64.DEFAULT);
    }

    private String decrypt(String encryptedBase64, String key) throws Exception {
        byte[] combined = Base64.decode(encryptedBase64, Base64.DEFAULT);
        if (combined.length < 16) throw new Exception("Invalid encrypted data");
        byte[] iv = new byte[16];
        System.arraycopy(combined, 0, iv, 0, 16);
        byte[] cipherText = new byte[combined.length - 16];
        System.arraycopy(combined, 16, cipherText, 0, cipherText.length);

        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 16) {
            byte[] tmp = new byte[16];
            System.arraycopy(keyBytes, 0, tmp, 0, keyBytes.length);
            keyBytes = tmp;
        } else if (keyBytes.length > 16) {
            byte[] tmp = new byte[16];
            System.arraycopy(keyBytes, 0, tmp, 0, 16);
            keyBytes = tmp;
        }
        SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(iv));
        byte[] decrypted = cipher.doFinal(cipherText);
        // 检查是否 gzip 压缩（原代码中可能解压）
        // 原 m4537V 中如果第一个字节是 0x1F 0x8B 则解压，我们尝试检测
        if (decrypted.length >= 2 && decrypted[0] == (byte) 0x1F && decrypted[1] == (byte) 0x8B) {
            // GZIP 解压
            ByteArrayInputStream bais = new ByteArrayInputStream(decrypted);
            GZIPInputStream gzip = new GZIPInputStream(bais);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            int len;
            while ((len = gzip.read(buf)) > 0) {
                baos.write(buf, 0, len);
            }
            return baos.toString(StandardCharsets.UTF_8.name());
        }
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
