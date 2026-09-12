package com.github.catvod.spider;

import android.content.Context;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.github.catvod.crawler.Spider;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class DianYingTianTangCaiJi extends Spider {

    private static final OkHttpClient client = new OkHttpClient();
    private static final String API_BASE = "http://caiji.dyttzyapi.com/api.php/provide/vod/from/dyttm3u8/at/json/";

    // 分类名 -> 采集站 type_id，首页时动态建立
    private static final Map<String, String> TYPE_ID = new HashMap<>();

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
    }

    // ========== jar 内写死的分类结构（一级分组 + 子分类名） ==========
    private JSONArray innerGroups() {
        JSONArray arr = new JSONArray();
        arr.add(group("电影片", "动作片", "喜剧片", "科幻片", "恐怖片", "爱情片", "剧情片", "战争片", "记录片", "动画片", "伦理片"));
        arr.add(group("连续剧", "国产剧", "香港剧", "韩国剧", "欧美剧", "台湾剧", "日本剧", "海外剧", "泰国剧"));
        arr.add(group("短剧", "短剧"));
        arr.add(group("动漫片", "国产动漫", "日韩动漫", "欧美动漫", "港台动漫", "动画片"));
        arr.add(group("综艺片", "大陆综艺", "港台综艺", "日韩综艺", "欧美综艺"));
        return arr;
    }

    private JSONObject group(String name, String... subs) {
        JSONObject o = new JSONObject();
        o.put("name", name);
        JSONArray s = new JSONArray();
        for (String sub : subs) s.add(sub);
        o.put("sub", s);
        return o;
    }

    // ========== 首页 ==========
    @Override
    public String homeContent(boolean filter) throws Exception {
        JSONObject result = new JSONObject();

        String resp = getHttp(API_BASE + "?ac=list&pg=1");
        JSONObject apiJson = null;
        try {
            apiJson = JSON.parseObject(resp);
        } catch (Exception ignored) {
        }

        // 建立 分类名 -> type_id 映射
        if (apiJson != null) {
            JSONArray classArr = apiJson.getJSONArray("class");
            if (classArr != null) {
                for (int i = 0; i < classArr.size(); i++) {
                    JSONObject c = classArr.getJSONObject(i);
                    String n = c.getString("type_name");
                    String id = c.getString("type_id");
                    if (n != null && id != null) TYPE_ID.put(n, id);
                }
            }
        }

        // 用 jar 内的分组生成扁平分类（显示用）
        JSONArray groups = innerGroups();
        List<String> flat = new ArrayList<>();
        JSONArray originGroups = new JSONArray();
        for (int i = 0; i < groups.size(); i++) {
            JSONObject g = groups.getJSONObject(i);
            JSONArray subs = g.getJSONArray("sub");
            JSONObject ng = new JSONObject();
            ng.put("name", g.getString("name"));
            ng.put("sub", subs);
            originGroups.add(ng);
            for (int j = 0; j < subs.size(); j++) flat.add(subs.getString(j));
        }
        result.put("categories", flat);
        result.put("originCategories", originGroups); // 给魔改 UI 下拉菜单

        if (apiJson == null) {
            result.put("list", new JSONArray());
            return result.toJSONString();
        }

        JSONArray list = apiJson.getJSONArray("list");
        result.put("list", list == null ? new JSONArray() : list);
        result.put("page", apiJson.getInteger("page"));
        result.put("pagecount", apiJson.getInteger("pagecount"));
        result.put("total", apiJson.getInteger("total"));
        return result.toJSONString();
    }

    // ========== 分类列表 ==========
    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        String t = TYPE_ID.get(tid);
        if (t == null) t = tid; // 兜底
        String url = API_BASE + "?ac=list&pg=" + pg + "&t=" + t;
        return getHttp(url);
    }

    // ========== 详情 ==========
    @Override
    public String detailContent(List<String> ids) throws Exception {
        String url = API_BASE + "?ac=detail&ids=" + ids.get(0);
        return getHttp(url);
    }

    // ========== 搜索 ==========
    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        String url = API_BASE + "?ac=list&pg=" + pg + "&wd=" + key;
        return getHttp(url);
    }

    // ========== 播放解析 ==========
    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        JSONObject res = new JSONObject();
        res.put("parse", 0);
        res.put("playUrl", "");

        String url = API_BASE + "?ac=detail&ids=" + id;
        String resp = getHttp(url);
        JSONObject data = JSON.parseObject(resp);
        if (data == null) {
            res.put("url", "");
            return res.toJSONString();
        }

        JSONArray list = data.getJSONArray("list");
        if (list == null || list.isEmpty()) {
            res.put("url", "");
            return res.toJSONString();
        }

        JSONObject vod = list.getJSONObject(0);
        String playAll = vod.getString("vod_play_url");
        String realM3u8 = "";

        if (playAll != null && !playAll.isEmpty()) {
            String[] items = playAll.split("\\$\\$\\$");
            for (String item : items) {
                String[] nameUrl = item.split("#");
                if (nameUrl.length == 2 && nameUrl[0].equals(flag)) {
                    realM3u8 = nameUrl[1];
                    break;
                }
            }
        }

        res.put("url", realM3u8);
        return res.toJSONString();
    }

    // ========== HTTP ==========
    private String getHttp(String url) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android;TVBox)")
                .get()
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) return "";
            return response.body().string();
        }
    }
}
