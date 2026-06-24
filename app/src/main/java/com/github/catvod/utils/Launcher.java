package com.github.catvod.utils;

import android.content.Context;
import android.os.Build;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import okhttp3.Response;

import java.io.*;
import java.nio.file.Files;
import java.util.*;

public class Launcher {

    private static int port = -1;
    private static Process serverProcess = null; // 用于缓存当前运行的进程实例

    /**
     * Android 端判断进程是否存活
     */
    public static boolean isProcessRunning() {
        if (serverProcess == null) return false;
        try {
            // 如果进程已经结束，exitValue() 会正常返回，否则抛出 IllegalThreadStateException
            serverProcess.exitValue();
            return false;
        } catch (IllegalThreadStateException e) {
            return true; // 进程仍在运行
        }
    }

    private static String getServerName() {
        // Android 核心一般为 ARM 架构，区分 64 位和 32 位
        String[] abis = new String[]{};
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            abis = Build.SUPPORTED_ABIS;
        }
        for (String abi : abis) {
            if (abi.contains("arm64")) {
                return "lu-proxy-server-android-arm64";
            }
        }
        return "lu-proxy-server-android-arm";
    }

    private static String getServerPath(Context context) {
        // 使用 Android 的 App 私有内部存储路径 (/data/user/0/包名/files/)
        return context.getFilesDir().getAbsolutePath()+ File.separator + getServerName();
    }

    public static Process launch(Context context, String... args) throws Exception {
        String binaryPath = getServerPath(context);
        File binaryFile = new File(binaryPath);

        // Android 下通过 Runtime 执行 chmod 赋予权限 (755)
        if (binaryFile.exists() && !binaryFile.canExecute()) {
            SpiderDebug.log("正在为 Android 环境下的二进制文件添加执行权限...");
            try {
                Process chmod = Runtime.getRuntime().exec("chmod 755 " + binaryPath);
                chmod.waitFor();
                SpiderDebug.log("权限设置完成");
            } catch (Exception e) {
                SpiderDebug.log("权限设置失败: " + e.getMessage());
            }
        }

        // 构建命令列表
        List<String> command = new ArrayList<>();
       
        command.add(binaryPath);
        Collections.addAll(command, args);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        serverProcess = pb.start();
        Notify.show("启动go server成功");
        return serverProcess;
    }

    /**
     * 启动服务（注意：Android 端必须在子线程/异步任务中调用此方法！）
     */
    public static void startServer(Context context) {
        // 1. 检测本地文件是否存在，没有就下载文件
        loadServerFiles(context);

        // 2. 检测服务是否启动，没有启动就启动服务
        if (!isProcessRunning()) {
            SpiderDebug.log("服务未启动,正在启动代理服务...");
            try {
                launch(context);
                // 关键修正：给底层服务 500ms 的启动初始化时间，避免立即扫描端口导致失败
                Thread.sleep(500);
            } catch (Exception e) {
                SpiderDebug.log("启动代理服务失败: " + e.getMessage());
            }
        }

        SpiderDebug.log("服务已启动");
        // 3. 检测服务端口
        adjustPort();
    }

    private static void loadServerFiles(Context context) {
        String binaryPath = getServerPath(context);
        File file = new File(binaryPath);
        if (!file.exists()) {
            try {
                SpiderDebug.log("正在下载 Android 代理二进制文件...");
                String downloadUrl = "https://android.lushunming.qzz.io/json/server-android-arm";
                String[] abis = new String[]{};
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    abis = Build.SUPPORTED_ABIS;
                }
                for (String abi : abis) {
                    if (abi.contains("arm64")) {
                        downloadUrl = "https://android.lushunming.qzz.io/json/server-android-arm64"; // 假设服务器有对应的包
                        break;
                    }
                }

                Response result = OkHttp.newCall(downloadUrl, new HashMap<>());
                if (result != null && result.body() != null) {
                    // 适配 Android 低版本，使用传统的流写入，或者 API 26+ 的 Files.write

                        // 兼容老版本 Android
                        try (InputStream is = result.body().byteStream(); OutputStream os = new FileOutputStream(file)) {
                            byte[] buffer = new byte[4096];
                            int length;
                            while ((length = is.read(buffer)) > 0) {
                                os.write(buffer, 0, length);
                            }
                        }

                    SpiderDebug.log("下载server完成");
                }
            } catch (IOException e) {
                SpiderDebug.log("下载代理服务失败");
                throw new RuntimeException(e);
            }
        }
    }

    static void adjustPort() {
        if (port > 0) return;
        int pt = 12345;
        while (pt < 12360) {
            try {
                String resp = OkHttp.string("http://127.0.0.1:" + pt, null);
                if (resp != null && resp.equals("ser200")) {
                    SpiderDebug.log("Found local server port " + pt);
                    port = pt;
                    break;
                }
                pt++;
            } catch (Exception e) {
                SpiderDebug.log("请求端口 异常，正在重试下一个... " + e.getMessage());
                // 每次请求失败稍微等待，防止 CPU 轮询空转
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ignored) {
                }
                pt++;
            }
        }
    }

    public static String getHostPort() {
        adjustPort();
        return "http://127.0.0.1:" + port;
    }

    public static String getProxyUrl() {
        return getHostPort() + "/proxy";
    }

    public static String buildProxyUrl(String url, Map<String, String> headers, int threads) {
        String key = Util.MD5(url);
        Map<String, Object> params = new HashMap<>();
        params.put("url", url);
        params.put("headers", headers);
        params.put("key", key);

        OkHttp.post(getHostPort() + "/buildUrl", Json.toJson(params), new HashMap<>());
        return getProxyUrl() + "?key=" + key + "&threads=" + threads;
    }

    public static String buildProxyUrl(String url, Map<String, String> headers) {
        return buildProxyUrl(url, headers, Runtime.getRuntime().availableProcessors() * 2);
    }
}