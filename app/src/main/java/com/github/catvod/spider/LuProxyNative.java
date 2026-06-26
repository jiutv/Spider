package com.github.catvod.spider;

import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.utils.Launcher;

public class LuProxyNative {


    static {
        try {
            System.load(Launcher.getServerPath(Init.context())); // 全路径加载
        } catch (UnsatisfiedLinkError e) {
            SpiderDebug.log("Failed to load native library: " + e.getMessage());
        }
    }

    // 对应 Go 的 StartServer()
    public native void StartServer();
}

