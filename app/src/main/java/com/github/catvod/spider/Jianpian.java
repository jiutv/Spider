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
import com.google.gson.JsonArray;
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
 * 荐片 修复版
 * 修复：电视剧/动漫/综艺 请求参数与网页不一致导致无数据
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
        imgDomain = "img.cqbqr.com";
        SpiderDebug.log("荐片初始化成功，图片域名：" + imgDomain);
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        // 导航：Netflix、电影、电视剧、短剧、动漫、综艺、纪录片
        List<String> typeIds = Arrays.asList("99", "1", "2", "67", "3", "4", "50");
        List<String> typeNames = Arrays.asList("Netflix", "电影", "电视剧", "短剧", "动漫", "综艺", "纪录片");
        for (int i = 0; i < typeIds.size(); i++) {
            classes.add(new Class(typeIds.get(i), typeNames.get(i)));
        }

        String extendJson = "{" +
                "\"type\":[{\"k\":\"\",\"v\":\"全部\"},{\"k\":\"1\",\"v\":\"剧情\"},{\"k\":\"2\",\"v\":\"爱情\"},{\"k\":\"3\",\"v\":\"动画\"},{\"k\":\"4\",\"v\":\"喜剧\"},{\"k\":\"5\",\"v\":\"战争\"},{\"k\":\"6\",\"v\":\"歌舞\"},{\"k\":\"7\",\"v\":\"古装\"},{\"k\":\"8\",\"v\":\"奇幻\"},{\"k\":\"9\",\"v\":\"冒险\"},{\"k\":\"10\",\"v\":\"动作\"},{\"k\":\"11\",\"v\":\"科幻\"},{\"k\":\"12\",\"v\":\"悬疑\"},{\"k\":\"13\",\"v\":\"犯罪\"},{\"k\":\"14\",\"v\":\"家庭\"},{\"k\":\"15\",\"v\":\"传记\"},{\"k\":\"16\",\"v\":\"运动\"},{\"k\":\"17\",\"v\":\"同性\"},{\"k\":\"18\",\"v\":\"惊悚\"},{\"k\":\"19\",\"v\":\"情色\"},{\"k\":\"20\",\"v\":\"短片\"},{\"k\":\"21\",\"v\":\"历史\"},{\"k\":\"22\",\"v\":\"音乐\"},{\"k\":\"23\",\"v\":\"西部\"},{\"k\":\"24\",\"v\":\"武侠\"},{\"k\":\"25\",\"v\":\"恐怖\"}]," +
                "\"area\":[{\"k\":\"\",\"v\":\"全部\"},{\"k\":\"1\",\"v\":\"国产\"},{\"k\":\"3\",\"v\":\"中国香港\"},{\"k\":\"6\",\"v\":\"中国台湾\"},{\"k\":\"5\",\"v\":\"美国\"},{\"k\":\"18\",\"v\":\"韩国\"},{\"k\":\"2\",\"v\":\"日本\"}]," +
                "\"year\":[{\"k\":\"\",\"v\":\"全部\"},{\"k\":\"162\",\"v\":\"2026\"},{\"k\":\"107\",\"v\":\"2025\"},{\"k\":\"119\",\"v\":\"2024\"},{\"k\":\"153\",\"v\":\"2023\"},{\"k\":\"101\",\"v\":\"2022\"},{\"k\":\"118\",\"v\":\"2021\"},{\"k\":\"16\",\"v\":\"2020\"},{\"k\":\"7\",\"v\":\"2019\"},{\"k\":\"2\",\"v\":\"2018\"},{\"k\":\"3\",\"v\":\"2017\"},{\"k\":\"22\",\"v\":\"2016\"},{\"k\":\"2015\",\"v\":\"2015以前\"}]," +
                "\"category_id\":[{\"k\":\"\",\"v\":\"全部\"},{\"k\":\"70\",\"v\":\"言情\"},{\"k\":\"71\",\"v\":\"爱情\"},{\"k\":\"72\",\"v\":\"战神\"},{\"k\":\"73\",\"v\":\"古代\"},{\"k\":\"74\",\"v\":\"萌娃\"},{\"k\":\"75\",\"v\":\"神医\"},{\"k\":\"76\",\"v\":\"玄幻\"},{\"k\":\"77\",\"v\":\"重生\"},{\"k\":\"79\",\"v\":\"激情\"},{\"k\":\"82\",\"v\":\"时尚\"},{\"k\":\"83\",\"v\":\"剧情演绎\"},{\"k\":\"84\",\"v\":\"影视\"},{\"k\":\"85\",\"v\":\"人文社科\"},{\"k\":\"86\",\"v\":\"二次元\"},{\"k\":\"87\",\"v\":\"明星八卦\"},{\"k\":\"89\",\"v\":\"个人管理\"},{\"k\":\"90\",\"v\":\"音乐\"},{\"k\":\"91\",\"v\":\"汽车\"},{\"k\":\"92\",\"v\":\"休闲\"},{\"k\":\"93\",\"v\":\"校园教育\"},{\"k\":\"94\",\"v\":\"游戏\"},{\"k\":\"95\",\"v\":\"科普\"},{\"k\":\"96\",\"v\":\"科技\"},{\"k\":\"97\",\"v\":\"时政社会\"},{\"k\":\"98\",\"v\":\"萌宠\"},{\"k\":\"113\",\"v\":\"随拍\"},{\"k\":\"114\",\"v\":\"体育\"},{\"k\":\"80\",\"v\":\"穿越\"},{\"k\":\"112\",\"v\":\"闪婚\"}]," +
                "\"sort\":[{\"k\":\"\",\"v\":\"全部\"},{\"k\":\"update\",\"v\":\"最新\"},{\"k\":\"hot\",\"v\":\"最热\"},{\"k\":\"rating\",\"v\":\"评分\"}]" +
                "}";

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

        // Netflix、纪录片、短剧 dyTag 系列
        if (tid.equals("50") || tid.equals("99") || tid.equals("67")) {
            String cidVal = "";
            String sortVal = "update";
            if (extend != null) {
                cidVal = extend.getOrDefault("category_id", "");
                sortVal = extend.getOrDefault("sort", "update");
            }
            String url;
            if (tid.equals("67")) {
                // 短剧接口
                StringBuilder sb = new StringBuilder();
                sb.append(siteUrl).append("/api/dyTag/hand_data?category_id=").append(tid);
                if (!cidVal.isEmpty()) sb.append("&category_sub_id=").append(cidVal);
                if (!sortVal.isEmpty() && !sortVal.equals("update") && !sortVal.equals("rating")) {
                    sb.append("&sort=").append(sortVal);
                }
                url = sb.toString();
                // 短剧独立解析
                String jsonStr = OkHttp.string(url, getHeader());
                JsonObject root = JsonParser.parseString(jsonStr).getAsJsonObject();
                JsonObject dataRoot = root.getAsJsonObject("data");
                for (String key : dataRoot.keySet()) {
                    JsonArray arr = dataRoot.getAsJsonArray(key);
                    List<Data> dataList = gson.fromJson(arr, com.google.gson.reflect.TypeToken.getParameterized(List.class, Data.class).getType());
                    for (Data data : dataList) {
                        list.add(data.vod(imgDomain));
                    }
                }
            } else {
                // Netflix、纪录片
                url = siteUrl + String.format("/api/dyTag/list?category_id=%s&page=%s", tid, pg);
                Resp resp = Resp.objectFrom(OkHttp.string(url, getHeader()));
                for (Data data : resp.getData()) {
                    if (data.getDataList() != null) {
                        for (Data dataList : data.getDataList()) {
                            list.add(dataList.vod(imgDomain));
                        }
                    }
                }
            }
        } else {
            String typeVal = "";
            String areaVal = "";
            String yearVal = "";
            String cidVal = "";
            String sortVal = "update";
            if (extend != null) {
                typeVal = extend.getOrDefault("type", "");
                areaVal = extend.getOrDefault("area", "");
                yearVal = extend.getOrDefault("year", "");
                cidVal = extend.getOrDefault("category_id", "");
                sortVal = extend.getOrDefault("sort", "update");
            }

            // ==========关键修复：统一所有 crumb/list 参数格式，完全匹配网页抓包==========
            StringBuilder urlSb = new StringBuilder();
            urlSb.append(siteUrl).append("/api/crumb/list?fcate_pid=").append(tid);
            urlSb.append("&category_id=").append(cidVal);
            urlSb.append("&area=").append(areaVal);
            urlSb.append("&year=").append(yearVal);
            urlSb.append("&type=").append(typeVal);
            urlSb.append("&sort=").append(sortVal);
            urlSb.append("&page=").append(pg);
            String url = urlSb.toString();

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
        for (Search item : search.getData()) {
            list.add(item.vod(imgDomain));
        }
        return Result.get().vod(list).string();
    }
}
