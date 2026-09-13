package com.github.catvod.net;

import android.text.TextUtils;
import android.util.Log;

import com.github.catvod.crawler.Spider;
import com.github.catvod.utils.Notify;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import okhttp3.Dns;
import okhttp3.Headers;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;

public class OkHttp {

    public static final String POST = "POST";
    public static final String GET = "GET";

    private OkHttpClient client;


    private static class Loader {
        static volatile OkHttp INSTANCE = new OkHttp();
    }

    private static OkHttp get() {
        return Loader.INSTANCE;
    }

    public static Response newCall(Request request) throws IOException {
        return client().newCall(request).execute();
    }

    public static Response newCall(String url) throws IOException {
        return client().newCall(new Request.Builder().url(url).build()).execute();
    }

    public static Response newCall(String url, Map<String, String> header) throws IOException {
        return client().newCall(new Request.Builder().url(url).headers(Headers.of(header)).build()).execute();
    }

    public static String string(String url) {
        return string(url, null);
    }

    public static String string(String url, Map<String, String> header) {
        return string(url, null, header);
    }

    public static String string(String url, Map<String, String> params, Map<String, String> header) {
        return url.startsWith("http") ? new OkRequest(GET, url, params, header).execute(client()).getBody() : "";
    }

    public static OkResult get(String url, Map<String, String> params, Map<String, String> header) {
        return new OkRequest(GET, url, params, header).execute(client());
    }

    public static String post(String url, Map<String, String> params) {
        return post(url, params, null).getBody();
    }

    public static OkResult post(String url, Map<String, String> params, Map<String, String> header) {
        return new OkRequest(POST, url, params, header).execute(client());
    }

    public static String post(String url, String json) {
        return post(url, json, null).getBody();
    }

    public static OkResult post(String url, String json, Map<String, String> header) {
        return new OkRequest(POST, url, json, header).execute(client());
    }

    public static String getLocation(String url, Map<String, String> header) throws IOException {
        return getLocation(client().newBuilder().followRedirects(false).followSslRedirects(false).build().newCall(new Request.Builder().url(url).headers(Headers.of(header)).build()).execute().headers().toMultimap());
    }
    public static Map<String, List<String>>  getLocationHeader(String url, Map<String, String> header) throws IOException {
        return client().newBuilder().followRedirects(false).followSslRedirects(false).build().newCall(new Request.Builder().url(url).headers(Headers.of(header)).build()).execute().headers().toMultimap();
    }

    public static String getLocation(Map<String, List<String>> headers) {
        if (headers == null) return null;
        if (headers.containsKey("location")) return headers.get("location").get(0);
        if (headers.containsKey("Location")) return headers.get("Location").get(0);
        return null;
    }

    private static OkHttpClient build() {
        if (get().client != null) return get().client;
        return get().client = getBuilder().build();
    }

    private static OkHttpClient.Builder getBuilder() {
        return new OkHttpClient.Builder().dns(safeDns()).connectTimeout(30, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).writeTimeout(30, TimeUnit.SECONDS).hostnameVerifier((hostname, session) -> true).sslSocketFactory(new SSLCompat(), SSLCompat.TM).followRedirects(false).followSslRedirects(false).addInterceptor(new RedirectBlocker());
    }

    private static OkHttpClient client() {
        try {
            return Objects.requireNonNull(Spider.client());
        } catch (Throwable e) {
            return build();
        }
    }

    private static Dns safeDns() {
        try {
            return Objects.requireNonNull(Spider.safeDns());
        } catch (Throwable e) {
            return Dns.SYSTEM;
        }
    }

    // ==================== 恶意跳转拦截 (追加, 原有代码不动) ====================

    /** 日志 TAG */
    private static final String TAG_CHECK = "SOURCE_CHECK";
    private static final String TAG_WARN = "SOURCE_CHECK_WARN";

    /** 恶意域名黑名单 —— 子域名也命中 */
    private static final Set<String> BLOCKED_HOSTS = new HashSet<>(Arrays.asList(
            "7moor-fs1.com", "moor-fs1.com", "qunar-f1.com", "qunar-f2.com", "fs-im-kefu.com", "mqnar.com"
    ));

    private static String extractHost(String url) {
        if (TextUtils.isEmpty(url)) return "";
        try { return new URI(url).getHost(); } catch (URISyntaxException e) { return ""; }
    }

    private static boolean isBlocked(String host) {
        if (TextUtils.isEmpty(host)) return false;
        String h = host.toLowerCase();
        for (String blocked : BLOCKED_HOSTS) {
            if (h.equals(blocked) || h.endsWith("." + blocked)) return true;
        }
        return false;
    }

    /** 重定向拦截器 —— 内部类, 所有请求自动走这里 */
    private static class RedirectBlocker implements Interceptor {
        @Override
        public Response intercept(Chain chain) throws IOException {
            Request request = chain.request();
            String originalUrl = request.url().toString();
            String sourceName = extractHost(originalUrl);

            int redirectCount = 0;
            Request current = request;

            while (true) {
                Response response = chain.proceed(current);
                int code = response.code();

                // 非重定向 → 返回
                if (code != 301 && code != 302 && code != 307 && code != 308) return response;

                String location = response.header("Location");
                if (TextUtils.isEmpty(location)) return response;

                // 补全相对 URL
                java.net.URL baseUrl = new java.net.URL(current.url().toString());
                java.net.URL targetUrl = new java.net.URL(baseUrl, location);
                String targetStr = targetUrl.toString();
                String targetHost = targetUrl.getHost();

                // 跳前日志
                Log.i(TAG_CHECK, "源名=" + sourceName + ",原请求url=" + current.url().toString() + ",即将跳转至=" + targetStr);

                // 黑名单命中 → 终止跳转, 不发新请求
                if (isBlocked(targetHost)) {
                    Log.w(TAG_WARN, "拦截恶意跳转，源名=" + sourceName + "，跳转目标host=" + targetHost);
                    // Toast 显示触发源 (Notify 内部自己跑 UI 线程, 不阻塞网络流程)
                    try { Notify.show("⚠️已拦截恶意跳转，触发源：【" + sourceName + "】"); } catch (Throwable ignored) {}
                    try { response.close(); } catch (Throwable ignored) {}
                    return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                            .code(403).message("SOURCE_CHECK_BLOCKED: " + targetHost).build();
                }

                // 防跳转环
                if (++redirectCount > 5) {
                    Log.w(TAG_WARN, "跳转链过长已终止 (>" + 5 + " 跳), 初始url=" + originalUrl);
                    return response;
                }

                Request next = current.newBuilder().url(targetUrl).build();
                response.close();
                current = next;
            }
        }
    }
}
