package com.github.catvod.utils;

import com.github.catvod.crawler.SpiderDebug;

public class LuProxyNative {
    /*static {
        try {
            System.loadLibrary("luserver"); // 无需 lib 前缀
        } catch (UnsatisfiedLinkError e) {
            SpiderDebug.log("Failed to load native library: " + e.getMessage());
        }
    }*/

    // 对应 Go 的 StartServer()
    public  native void StartServer();
}

