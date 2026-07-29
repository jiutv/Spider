package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 荐片 TVBox 爬虫
 * 基于荐片APP (jp224_4.2.5) API 实现
 * API: https://japi.zxfmj.com
 */
public class Jianpian extends Spider {

    private static final String SITE_URL = "https://japi.zxfmj.com";
    private static final String IMG_DOMAIN = "img.cqbqr.com";
    private static final Pattern CR_TAG = Pattern.compile("\\[a=cr:[^\\]]+\\]|\\[/a\\]");

    private static final String FILTER_JSON = "{" +
            "\"type\":[{\"k\":\"\",\"v\":\"全部\"},{\"k\":\"1\",\"v\":\"剧情\"},{\"k\":\"2\",\"v\":\"爱情\"},{\"k\":\"3\",\"v\":\"动画\"},{\"k\":\"4\",\"v\":\"喜剧\"},{\"k\":\"5\",\"v\":\"战争\"},{\"k\":\"6\",\"v\":\"歌舞\"},{\"k\":\"7\",\"v\":\"古装\"},{\"k\":\"8\",\"v\":\"奇幻\"},{\"k\":\"9\",\"v\":\"冒险\"},{\"k\":\"10\",\"v\":\"动作\"},{\"k\":\"11\",\"v\":\"科幻\"},{\"k\":\"12\",\"v\":\"悬疑\"},{\"k\":\"13\",\"v\":\"犯罪\"},{\"k\":\"14\",\"v\":\"家庭\"},{\"k\":\"15\",\"v\":\"传记\"},{\"k\":\"16\",\"v\":\"运动\"},{\"k\":\"17\",\"v\":\"同性\"},{\"k\":\"18\",\"v\":\"惊悚\"},{\"k\":\"19\",\"v\":\"情色\"},{\"k\":\"20\",\"v\":\"短片\"},{\"k\":\"21\",\"v\":\"历史\"},{\"k\":\"22\",\"v\":\"音乐\"},{\"k\":\"23\",\"v\":\"西部\"},{\"k\":\"24\",\"v\":\"武侠\"},{\"k\":\"25\",\"v\":\"恐怖\"}]," +
            "\"area\":[{\"k\":\"\",\"v\":\"全部\"},{\"k\":\"1\",\"v\":\"国产\"},{\"k\":\"3\",\"v\":\"中国香港\"},{\"k\":\"6\",\"v\":\"中国台湾\"},{\"k\":\"5\",\"v\":\"美国\"},{\"k\":\"18\",\"v\":\"韩国\"},{\"k\":\"2\",\"v\":\"日本\"}]," +
            "\"year\":[{\"k\":\"\",\"v\":\"全部\"},{\"k\":\"162\",\"v\":\"2026\"},{\"k\":\"107\",\"v\":\"2025\"},{\"k\":\"119\",\"v\":\"2024\"},{\"k\":\"153\",\"v\":\"2023\"},{\"k\":\"101\",\"v\":\"2022\"},{\"k\":\"118\",\"v\":\"2021\"},{\"k\":\"16\",\"v\":\"2020\"},{\"k\":\"7\",\"v\":\"2019\"},{\"k\":\"2\",\"v\":\"2018\"},{\"k\":\"3\",\"v\":\"2017\"},{\"k\":\"22\",\"v\":\"2016\"},{\"k\":\"2015\",\"v\":\"2015以前\"}]," +
            "\"category_id\":[{\"k\":\"\",\"v\":\"全部\"},{\"k\":\"70\",\"v\":\"言情\"},{\"k\":\"71\",\"v\":\"爱情\"},{\"k\":\"72\",\"v\":\"战神\"},{\"k\":\"73\",\"v\":\"古代\"},{\"k\":\"74\",\"v\":\"萌娃\"},{\"k\":\"75\",\"v\":\"神医\"},{\"k\":\"76\",\"v\":\"玄幻\"},{\"k\":\"77\",\"v\":\"重生\"},{\"k\":\"79\",\"v\":\"激情\"},{\"k\":\"82\",\"v\":\"时尚\"},{\"k\":\"83\",\"v\":\"剧情演绎\"},{\"k\":\"84\",\"v\":\"影视\"},{\"k\":\"85\",\"v\":\"人文社科\"},{\"k\":\"86\",\"v\":\"二次元\"},{\"k\":\"87\",\"v\":\"明星八卦\"},{\"k\":\"89\",\"v\":\"个人管理\"},{\"k\":\"90\",\"v\":\"音乐\"},{\"k\":\"91\",\"v\":\"汽车\"},{\"k\":\"92\",\"v\":\"休闲\"},{\"k\":\"93\",\"v\":\"校园教育\"},{\"k\":\"94\",\"v\":\"游戏\"},{\"k\":\"95\",\"v\":\"科普\"},{\"k\":\"96\",\"v\":\"科技\"},{\"k\":\"97\",\"v\":\"时政社会\"},{\"k\":\"98\",\"v\":\"萌宠\"},{\"k\":\"113\",\"v\":\"随拍\"},{\"k\":\"114\",\"v\":\"体育\"},{\"k\":\"80\",\"v\":\"穿越\"},{\"k\":\"112\",\"v\":\"闪婚\"}]," +
            "\"sort\":[{\"k\":\"\",\"v\":\"全部\"},{\"k\":\"update\",\"v\":\"最新\"},{\"k\":\"hot\",\"v\":\"最热\"},{\"k\":\"rating\",\"v\":\"评分\"}]" +
            "}";

    // 首页分类：Netflix(99)、电影(1)、电视剧(2)、短剧(67)、动漫(3)、综艺(4)、纪录片(50)
    private static final List<String> TYPE_IDS = Arrays.asList("99", "1", "2", "67", "3", "4", "50");
    private static final List<String> TYPE_NAMES = Arrays.asList("Netflix", "电影", "电视剧", "短剧", "动漫", "综艺", "纪录片");

    private final OkHttpClient client = new OkHttpClient();
    private String imgDomain = IMG_DOMAIN;

    private Map<String, String> getHeader() {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Linux; Android 9; V2196A Build/PQ3A.190705.08211809; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/91.0.4472.114 Mobile Safari/537.36;webank/h5face;webank/1.0;netType:NETWORK_WIFI;appVersion:425;packageName:com.kgvteb.zfnjdk");
        headers.put("Referer", SITE_URL);
        headers.put("Accept", "application/json");
        return headers;
    }

    @Override
    public void init(Context context, String extend) {
        try {
            String json = get(SITE_URL + "/api/appAuthConfig");
            if (!TextUtils.isEmpty(json)) {
                JSONObject root = new JSONObject(json);
                JSONObject data = root.optJSONObject("data");
                if (data != null && data.has("imgDomain")) {
                    String domain = data.optString("imgDomain");
                    if (!TextUtils.isEmpty(domain)) {
                        imgDomain = domain;
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public String homeContent(boolean filter) {
        ArrayList<Class> classes = new ArrayList<>();
        for (int i = 0; i < TYPE_IDS.size(); i++) {
            classes.add(new Class(TYPE_IDS.get(i), TYPE_NAMES.get(i)));
        }
        try {
            return Result.string(classes, new JSONObject(FILTER_JSON));
        } catch (Exception e) {
            return Result.string(classes);
        }
    }

    @Override
    public String homeVideoContent() {
        ArrayList<Vod> list = new ArrayList<>();
        try {
            String url = SITE_URL + "/api/slide/list?pos_id=88";
            JSONObject root = new JSONObject(get(url));
            JSONArray data = root.optJSONArray("data");
            if (data != null) {
                for (int i = 0; i < data.length(); i++) {
                    JSONObject item = data.optJSONObject(i);
                    if (item != null) {
                        list.add(parseShortVod(item));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Result.string(list);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        ArrayList<Vod> list = new ArrayList<>();
        if (TextUtils.isEmpty(pg)) pg = "1";

        try {
            if (tid.equals("50") || tid.equals("99") || tid.equals("67")) {
                // 短剧/Netflix/纪录片 使用短剧接口
                String cidVal = "";
                String sortVal = "update";
                if (extend != null) {
                    cidVal = extend.getOrDefault("category_id", "");
                    sortVal = extend.getOrDefault("sort", "update");
                }

                if (tid.equals("67") && "1".equals(pg)) {
                    // 短剧首页 hand_data（第一页用这个，返回精选分类）
                    StringBuilder sb = new StringBuilder();
                    sb.append(SITE_URL).append("/api/dyTag/hand_data?category_id=").append(tid);
                    if (!TextUtils.isEmpty(cidVal)) sb.append("&category_sub_id=").append(cidVal);
                    if (!TextUtils.isEmpty(sortVal) && !sortVal.equals("update") && !sortVal.equals("rating")) {
                        sb.append("&sort=").append(sortVal);
                    }
                    String url = sb.toString();
                    JSONObject root = new JSONObject(get(url));
                    JSONObject dataRoot = root.optJSONObject("data");
                    if (dataRoot != null) {
                        JSONArray names = dataRoot.names();
                        if (names != null) {
                            for (int i = 0; i < names.length(); i++) {
                                String key = names.getString(i);
                                JSONArray arr = dataRoot.optJSONArray(key);
                                if (arr != null) {
                                    for (int j = 0; j < arr.length(); j++) {
                                        JSONObject itemObj = arr.optJSONObject(j);
                                        if (itemObj != null) {
                                            list.add(parseShortVod(itemObj));
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // 短剧分页 list
                    String url = SITE_URL + String.format("/api/dyTag/list?category_id=%s&page=%s", tid, pg);
                    JSONObject root = new JSONObject(get(url));
                    JSONArray data = root.optJSONArray("data");
                    if (data != null) {
                        for (int i = 0; i < data.length(); i++) {
                            JSONObject item = data.optJSONObject(i);
                            if (item != null) {
                                JSONArray dataList = item.optJSONArray("data_list");
                                if (dataList != null) {
                                    for (int j = 0; j < dataList.length(); j++) {
                                        JSONObject dataItem = dataList.optJSONObject(j);
                                        if (dataItem != null) {
                                            list.add(parseVod(dataItem));
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // 普通分类（电影/电视剧/动漫/综艺）
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

                StringBuilder urlSb = new StringBuilder();
                urlSb.append(SITE_URL).append("/api/crumb/list?fcate_pid=").append(tid);
                urlSb.append("&category_id=").append(cidVal);
                urlSb.append("&area=").append(areaVal);
                urlSb.append("&year=").append(yearVal);
                urlSb.append("&type=").append(typeVal);
                urlSb.append("&sort=").append(sortVal);
                urlSb.append("&page=").append(pg);

                try {
                    JSONObject root = new JSONObject(get(urlSb.toString()));
                    JSONArray data = root.optJSONArray("data");
                    if (data != null) {
                        for (int i = 0; i < data.length(); i++) {
                            JSONObject item = data.optJSONObject(i);
                            if (item != null) {
                                list.add(parseVod(item));
                            }
                        }
                    }
                } catch (Exception e) {
                    // crumb/list 接口可能失效，回退到搜索
                    return searchContent(TYPE_NAMES.get(TYPE_IDS.indexOf(tid)), pg);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Result.string(list);
    }

    @Override
    public String detailContent(List<String> ids) {
        ArrayList<Vod> empty = new ArrayList<>();
        if (ids == null || ids.isEmpty()) return Result.string(empty);

        try {
            String url = SITE_URL + "/api/video/detailv2?id=" + ids.get(0);
            JSONObject root = new JSONObject(get(url));
            JSONObject data = root.optJSONObject("data");
            if (data == null) return Result.string(empty);

            Vod vod = parseVod(data);
            vod.setVodYear(data.optString("year"));
            vod.setVodArea(data.optString("area"));
            vod.setTypeName(data.optString("types"));
            vod.setVodDirector(data.optString("directors"));
            vod.setVodContent(data.optString("description"));

            String actors = CR_TAG.matcher(data.optString("actors")).replaceAll("");
            vod.setVodActor(actors);

            // 解析播放源
            parsePlaySources(data, vod);

            return Result.string(vod);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.string(empty);
        }
    }

    private void parsePlaySources(JSONObject data, Vod vod) {
        // source_list_source 是主要播放源
        JSONArray sourceList = data.optJSONArray("source_list_source");
        if (sourceList == null) sourceList = new JSONArray();

        JSONArray vipSourceList = data.optJSONArray("vip_source_list_source");
        if (vipSourceList != null) {
            for (int i = 0; i < vipSourceList.length(); i++) {
                JSONObject vipItem = vipSourceList.optJSONObject(i);
                if (vipItem != null) {
                    sourceList.put(vipItem);
                }
            }
        }

        // 兼容旧字段
        JSONArray m3u8List = data.optJSONArray("m3u8_list");
        JSONArray ftpList = data.optJSONArray("ftp_list");

        ArrayList<String> fromList = new ArrayList<>();
        ArrayList<String> urlGroupList = new ArrayList<>();

        // 解析 source_list_source
        if (sourceList.length() > 0) {
            for (int i = 0; i < sourceList.length(); i++) {
                JSONObject group = sourceList.optJSONObject(i);
                if (group == null) continue;

                String from = group.optString("name");
                if (TextUtils.isEmpty(from)) from = "线路" + (i + 1);
                fromList.add(from);

                JSONArray urls = group.optJSONArray("source_list");
                ArrayList<String> itemList = new ArrayList<>();

                if (urls != null) {
                    for (int j = 0; j < urls.length(); j++) {
                        JSONObject item = urls.optJSONObject(j);
                        if (item != null) {
                            String name = item.optString("source_name");
                            if (TextUtils.isEmpty(name)) name = item.optString("weight");
                            if (TextUtils.isEmpty(name)) name = "第" + (j + 1) + "集";
                            String playUrl = item.optString("url");
                            if (!TextUtils.isEmpty(playUrl)) {
                                itemList.add(name + "$" + playUrl);
                            }
                        }
                    }
                }

                urlGroupList.add(join("#", itemList));
            }
        }

        // 解析 m3u8_list 和 ftp_list（旧格式）
        if (fromList.isEmpty()) {
            if (m3u8List != null && m3u8List.length() > 0) {
                fromList.add("M3U8");
                ArrayList<String> itemList = new ArrayList<>();
                for (int j = 0; j < m3u8List.length(); j++) {
                    JSONObject item = m3u8List.optJSONObject(j);
                    if (item != null) {
                        String name = item.optString("title");
                        if (TextUtils.isEmpty(name)) name = "第" + (j + 1) + "集";
                        String playUrl = item.optString("url");
                        if (!TextUtils.isEmpty(playUrl)) {
                            itemList.add(name + "$" + playUrl);
                        }
                    }
                }
                urlGroupList.add(join("#", itemList));
            }

            if (ftpList != null && ftpList.length() > 0) {
                fromList.add("FTP");
                ArrayList<String> itemList = new ArrayList<>();
                for (int j = 0; j < ftpList.length(); j++) {
                    JSONObject item = ftpList.optJSONObject(j);
                    if (item != null) {
                        String name = item.optString("title");
                        if (TextUtils.isEmpty(name)) name = "第" + (j + 1) + "集";
                        String playUrl = item.optString("url");
                        if (!TextUtils.isEmpty(playUrl)) {
                            itemList.add(name + "$" + playUrl);
                        }
                    }
                }
                urlGroupList.add(join("#", itemList));
            }
        }

        if (fromList.size() > 0) {
            vod.setVodPlayFrom(join("$$$", fromList));
            vod.setVodPlayUrl(join("$$$", urlGroupList));
        }
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        try {
            Map<String, String> headers = getHeader();
            return Result.get().url(id).header(headers).parse(0).string();
        } catch (Exception e) {
            return Result.get().url(id).parse(0).string();
        }
    }

    @Override
    public String searchContent(String key, boolean quick) {
        return searchContent(key, "1");
    }

    public String searchContent(String key, String pg) {
        ArrayList<Vod> list = new ArrayList<>();
        if (TextUtils.isEmpty(key)) return Result.string(list);

        try {
            String encodeKey = URLEncoder.encode(key, "UTF-8");
            String url = SITE_URL + String.format("/api/v2/search/videoV2?key=%s&category_id=88&page=%s&pageSize=20", encodeKey, pg);
            JSONObject root = new JSONObject(get(url));
            JSONArray data = root.optJSONArray("data");
            if (data != null) {
                for (int i = 0; i < data.length(); i++) {
                    JSONObject item = data.optJSONObject(i);
                    if (item != null) {
                        list.add(parseVod(item));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Result.string(list);
    }

    private String get(String url) throws Exception {
        Request.Builder builder = new Request.Builder().url(url);
        for (Map.Entry<String, String> entry : getHeader().entrySet()) {
            builder.addHeader(entry.getKey(), entry.getValue());
        }
        Request request = builder.build();
        Response response = client.newCall(request).execute();
        return response.body() == null ? "" : response.body().string();
    }

    private String fixImageUrl(String rawImg) {
        if (TextUtils.isEmpty(rawImg)) return "";
        if (rawImg.startsWith("http")) return rawImg;
        if (rawImg.startsWith("/")) {
            return "https://" + imgDomain + rawImg;
        } else {
            return "https://" + imgDomain + "/" + rawImg;
        }
    }

    private Vod parseVod(JSONObject item) {
        String id = item.optString("id");
        String title = item.optString("title");
        if (TextUtils.isEmpty(title)) title = item.optString("vod_name");
        if (TextUtils.isEmpty(title)) title = item.optString("original_name");

        // 封面图：优先 tvimg，其次 thumbnail，再次 cover_image，最后 img
        String pic = "";
        if (item.has("tvimg") && !item.isNull("tvimg")) {
            pic = item.optString("tvimg");
        } else if (item.has("thumbnail") && !item.isNull("thumbnail")) {
            pic = item.optString("thumbnail");
        } else if (item.has("cover_image") && !item.isNull("cover_image")) {
            pic = item.optString("cover_image");
        } else if (item.has("img")) {
            pic = item.optString("img");
        } else if (item.has("vod_pic")) {
            pic = item.optString("vod_pic");
        }
        pic = fixImageUrl(pic);

        // 简介/备注
        String remarks = "";
        if (item.has("mask")) {
            remarks = item.optString("mask");
        } else if (item.has("remarks")) {
            remarks = item.optString("remarks");
        } else if (item.has("vod_remarks")) {
            remarks = item.optString("vod_remarks");
        } else if (item.has("score")) {
            remarks = item.optString("score") + "分";
        }

        if (TextUtils.isEmpty(id) || TextUtils.isEmpty(title)) {
            return new Vod("", "", "", "");
        }
        return new Vod(id, title, pic, remarks);
    }

    private Vod parseShortVod(JSONObject item) {
        // 短剧专用解析（cover_image 字段）
        String id = "";
        if (item.has("id") && !item.isNull("id")) id = item.optString("id");
        String title = "";
        if (item.has("title") && !item.isNull("title")) title = item.optString("title");
        String mask = "";
        if (item.has("mask") && !item.isNull("mask")) mask = item.optString("mask");

        String rawImg = "";
        if (item.has("cover_image") && !item.isNull("cover_image")) {
            rawImg = item.optString("cover_image");
        } else if (item.has("img")) {
            rawImg = item.optString("img");
        }
        String fullImg = fixImageUrl(rawImg);

        return new Vod(id, title, fullImg, mask);
    }

    private String join(String sep, List<String> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(sep);
            sb.append(list.get(i));
        }
        return sb.toString();
    }
}
