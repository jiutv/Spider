package com.github.catvod.spider;

import com.github.catvod.crawler.SpiderDebug;

public class LuProxyNative {
    private static String absolutePath;
    public LuProxyNative(String absolutePath) {
        LuProxyNative.absolutePath = absolutePath;
    }

    static {
        try {
            System.load(absolutePath); // 全路径加载
        } catch (UnsatisfiedLinkError e) {
            SpiderDebug.log("Failed to load native library: " + e.getMessage());
        }
    }

    // 对应 Go 的 StartServer()
    public  native void StartServer();
}

