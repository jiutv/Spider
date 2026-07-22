package com.github.catvod.spider;

import android.content.Context;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.bean.jianpian.Data;
import com.github.catvod.bean.jianpian.Detail;
import com.github.catvod.bean.jianpian.Resp;
import com.github.catvod.bean.jianpian.Search;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 新版荐片 完整修复版
 * 有效主域名：japi.zxfmj.com
 * 新版包名：com.kgvteb.zfnjdk
 */
public class Jianpian extends Spider {

    private final String siteUrl = "https://japi.zxfmj.com";
    private String imgDomain;
    private String extend;
    private final Gson gson = new Gson();
    private static final Pattern CR_TAG = Pattern.compile("\\[a=cr:[^\\]]+\\]|\\[/a\\]");

    private Map<String, String> getHeader() {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Linux; Android 9; V2196A Build/PQ3A.190705.08211809; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/91.0.4472.114 Mobile Safari/537.36;webank/h5face;webank/1.0;netType:NETWORK_WIFI;appVersion:425;packageName:com.kgvteb.zfnjdk");
        headers.put("Referer", siteUrl);
        headers.put("Accept", "application/json");
        return headers;
    }

    @Override
    public void init(Context context, String extend) throws Exception {
        this.extend = extend;
        String json = OkHttp.string(siteUrl + "/api/appAuthConfig", getHeader());
        if (json.isEmpty()) throw new Exception("荐片主接口初始化失败");
        JsonObject root = gson.fromJson(json, JsonObject.class);
        imgDomain = root.getAsJsonObject("data").get("imgDomain").getAsString();
        SpiderDebug.log("荐片初始化成功，图片域名：" + imgDomain);
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        List<String> typeIds = Arrays.asList("1", "2", "3", "4", "50", "99");
        List<String> typeNames = Arrays.asList("電影", "電視劇", "動漫", "綜藝", "紀錄片", "Netflix");
        for (int i = 0; i < typeIds.size(); i++) {
            classes.add(new Class(typeIds.get(i), typeNames.get(i)));
        }
        String extendJson = (extend == null || extend.isEmpty()) ? "{}" : extend;
        return Result.string(classes, JsonParser.parseString(extendJson));
    }

    @Override
    public String homeVideoContent() {
        List<Vod> list = new ArrayList<>();
        try {
            String url = siteUrl + "/api/slide/list?pos_id=88";
            Resp resp = Resp.objectFrom(OkHttp.string(url, getHeader()));
            for (Data data : resp.getData()) {
                list.add(data.homeVod(imgDomain));
            }
        } catch (Exception e) {
            SpiderDebug.log("首页轮播加载异常：" + e.getMessage());
        }
        return Result.string(list);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        if (tid.endsWith("/{pg}")) return searchContent(tid.split("/")[0], pg);
        List<Vod> list = new ArrayList<>();
        if (tid.equals("50") || tid.equals("99") || tid.equals("111")) {
            String url = siteUrl + String.format("/api/dyTag/list?category_id=%s&page=%s", tid, pg);
            Resp resp = Resp.objectFrom(OkHttp.string(url, getHeader()));
            for (Data data : resp.getData()) {
                if (data.getDataList() != null) {
                    for (Data dataList : data.getDataList()) {
                        list.add(dataList.vod(imgDomain));
                    }
                }
            }
        } else {
            HashMap<String, String> ext = new HashMap<>();
            if (extend != null) ext.putAll(extend);
            String area = ext.getOrDefault("area", "0");
            String year = ext.getOrDefault("year", "0");
            String by = ext.getOrDefault("by", "updata");
            String url = siteUrl + String.format("/api/crumb/list?fcate_pid=%s&area=%s&year=%s&type=0&sort=%s&page=%s&category_id=", tid, area, year, by, pg);
            Resp resp = Resp.objectFrom(OkHttp.string(url, getHeader()));
            for (Data data : resp.getData()) {
                list.add(data.vod(imgDomain));
            }
        }
        return Result.get().vod(list).string();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String url = siteUrl + "/api/video/detailv2?id=" + ids.get(0);
        Detail detail = Detail.objectFrom(OkHttp.string(url, getHeader()));
        Data data = detail.getData();
        Vod vod = data.vod(imgDomain);

        vod.setVodPlayFrom(data.getVodFrom());
        vod.setVodYear(data.getYear());
        vod.setVodArea(data.getArea());
        vod.setTypeName(data.getTypes());
        vod.setVodPlayUrl(data.getVodUrl());
        vod.setVodDirector(data.getDirectors());
        vod.setVodContent(data.getDescription());

        String actors = CR_TAG.matcher(data.getActors()).replaceAll("");
        vod.setVodActor(actors);

        return Result.string(vod);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        //修复：小写url，删除不兼容的.parse(false)
        return Result.get().url(id).header(getHeader()).string();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return searchContent(key, "1");
    }

    public String searchContent(String key, String pg) throws Exception {
        List<Vod> list = new ArrayList<>();
        String encodeKey = URLEncoder.encode(key, StandardCharsets.UTF_8.name());
        String url = siteUrl + String.format("/api/v2/search/videoV2?key=%s&category_id=88&page=%s&pageSize=20", encodeKey, pg);
        Search search = Search.objectFrom(OkHttp.string(url, getHeader()));
        for (Search data : search.getData()) {
            list.add(data.vod(imgDomain));
        }
        return Result.get().vod(list).string();
    }
}
