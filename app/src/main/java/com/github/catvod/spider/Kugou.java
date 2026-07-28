package com.github.catvod.spider;

import android.text.TextUtils;
import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Util;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class Kugou extends Spider {
    // Meting备用音源配置 kw=酷我 kg=酷狗
    private static final String AUDIO_SERVER = "kw";
    private static final String[] METING_API = {
            "https://api.xygeng.cn/meting/api",
            "https://meting.qjqq.cn/api"
    };

    public static final Pattern URL_PATTERN = Pattern.compile("https?://.+\\.(m4a|mp3)");

    /**
     * 清理无效字符
     */
    public static String filterEmptyStr(String str) {
        if (TextUtils.isEmpty(str)) return "";
        return str.replace("\r", "").replace("\n", "").trim();
    }

    /**
     * 组装单曲ID songHASH|AlbumID|AudioId
     */
    public static String buildVodId(JSONObject obj) {
        String hash = obj.optString("Hash");
        if (TextUtils.isEmpty(hash)) hash = obj.optString("hash");
        if (TextUtils.isEmpty(hash)) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("song").append(hash).append("|");
        sb.append(obj.optString("AlbumID")).append("|");
        sb.append(obj.optString("AudioId"));
        return sb.toString();
    }

    /**
     * 获取歌曲封面
     */
    public static String getVodPic(JSONObject obj) {
        String pic = obj.optString("Image");
        if (TextUtils.isEmpty(pic)) pic = obj.optString("pic");
        if (TextUtils.isEmpty(pic) && obj.optJSONObject("albuminfo") != null) {
            pic = obj.optJSONObject("albuminfo").optString("image");
        }
        return filterEmptyStr(pic);
    }

    /**
     * 获取歌曲名称+歌手
     */
    public static String getVodName(JSONObject obj) {
        String name = obj.optString("SongName");
        String singer = obj.optString("SingerName");
        if (TextUtils.isEmpty(name)) {
            name = obj.optString("songname");
            singer = obj.optString("singername");
        }
        if (TextUtils.isEmpty(name)) {
            name = obj.optString("name");
        }
        if (!TextUtils.isEmpty(singer)) {
            name = name + " - " + singer;
        }
        return name.trim();
    }

    /**
     * 通用请求头
     */
    private Map<String, String> getHeader() {
        Map<String, String> header = new HashMap<>();
        header.put("User-Agent", Util.CHROME);
        header.put("Accept-Language", "zh-CN,zh;q=0.9");
        return header;
    }

    /**
     * GET 请求返回JSON
     */
    public JSONObject getJson(String url) {
        try {
            String resp = OkHttp.string(url, getHeader());
            return new JSONObject(resp);
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    /**
     * Meting接口搜索获取备用音频链接
     */
    private String getBackupAudio(String keyword) {
        try {
            String encodeKey = URLEncoder.encode(keyword, StandardCharsets.UTF_8.name());
            for (String api : METING_API) {
                try {
                    String url = String.format("%s?server=%s&type=search&keywords=%s", api, AUDIO_SERVER, encodeKey);
                    JSONObject json = new JSONObject(OkHttp.string(url, getHeader()));
                    JSONArray data = json.getJSONArray("data");
                    if (data.length() == 0) continue;
                    String audioUrl = data.getJSONObject(0).optString("url");
                    if (!TextUtils.isEmpty(audioUrl)) return audioUrl;
                } catch (Exception ignore) {
                }
            }
        } catch (Exception e) {
        }
        return "";
    }

    @Override
    public String homeContent(boolean filter) {
        List<Class> classes = new ArrayList<>();
        classes.add(new Class("hot", "🔥热搜榜单"));
        classes.add(new Class("playlist", "📃精选歌单"));
        classes.add(new Class("recommend", "💿热门推荐"));
        return Result.string(classes, new ArrayList<>());
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        if (!"1".equals(pg)) return Result.string(new ArrayList<>());
        List<Vod> vodList = new ArrayList<>();
        JSONObject resp = getJson("https://mobilecdn.kugou.com/api/v3/rank/list?format=json");
        JSONArray dataArr = null;
        if (resp.optJSONObject("data") != null) {
            dataArr = resp.optJSONObject("data").optJSONArray("list");
        }
        if (dataArr != null) {
            for (int i = 0; i < dataArr.length(); i++) {
                JSONObject item = dataArr.optJSONObject(i);
                if (item == null) continue;
                String type = item.optString("type");
                boolean match;
                if ("hot".equals(tid)) {
                    match = "1".equals(type) || "2".equals(type);
                } else if ("playlist".equals(tid)) {
                    match = "6".equals(type) || "0".equals(type);
                } else if ("recommend".equals(tid)) {
                    match = "3".equals(type) || "7".equals(type);
                } else {
                    match = false;
                }
                if (match) {
                    String listId = item.optString("id");
                    String vid = "list" + listId;
                    Vod vod = new Vod(vid, getVodName(item), getVodPic(item), "");
                    vodList.add(vod);
                }
            }
        }
        return Result.string(vodList);
    }

    @Override
    public String detailContent(List<String> ids) {
        if (ids == null || ids.isEmpty()) return Result.string(new ArrayList<>());
        String vid = ids.get(0);
        Vod vod = null;

        // 单曲
        if (vid.startsWith("song")) {
            String[] split = vid.substring(4).split("\\|");
            String hash = split[0];
            JSONObject res = getJson("https://www.kugou.com/yy/index.php?r=play/getdata&hash=" + hash);
            JSONObject data = res.optJSONObject("data");
            String playUrl = "";
            String lyric = "";
            if (data != null) {
                playUrl = data.optString("play_url");
                lyric = filterEmptyStr(data.optString("lyrics"));
            }
            vod = new Vod(vid, getVodName(res), getVodPic(res), lyric);
            vod.setVodPlayFrom("酷狗");
            vod.setVodPlayUrl(getVodName(res) + "$" + vid);
        }
        // 榜单歌单
        else if (vid.startsWith("list")) {
            String listId = vid.substring(4);
            JSONObject res = getJson("https://mobilecdn.kugou.com/api/v3/rank/song?rankid=" + listId + "&page=1&pagesize=100");
            JSONArray songArray = null;
            if (res.optJSONObject("data") != null) {
                songArray = res.optJSONObject("data").optJSONArray("songs");
            }
            List<String> playItems = new ArrayList<>();
            String coverImg = "";
            if (songArray != null) {
                for (int i = 0; i < songArray.length(); i++) {
                    JSONObject song = songArray.optJSONObject(i);
                    String sid = buildVodId(song);
                    if (!TextUtils.isEmpty(sid)) {
                        playItems.add(getVodName(song) + "$" + sid);
                    }
                    if (TextUtils.isEmpty(coverImg)) coverImg = getVodPic(song);
                }
            }
            vod = new Vod(vid, "酷狗榜单", coverImg, playItems.size() + "首歌曲");
            vod.setVodPlayFrom("酷狗合集");
            vod.setVodPlayUrl(String.join("#", playItems));
        }
        return Result.string(vod);
    }

    @Override
    public String playerContent(String flag, String data, List<String> vipFlags) {
        if (TextUtils.isEmpty(data)) return Result.get().string();
        String realUrl = "";
        String songName = "";

        String[] arr = data.split("\\$");
        if (arr.length >= 2) {
            songName = arr[0];
            String id = arr[1];
            if (id.startsWith("song")) {
                String[] sp = id.substring(4).split("\\|");
                String hash = sp[0];
                JSONObject info = getJson("https://www.kugou.com/yy/index.php?r=play/getdata&hash=" + hash);
                JSONObject dataObj = info.optJSONObject("data");
                if (dataObj != null) {
                    realUrl = dataObj.optString("play_url");
                }
            }
        }

        // 原生地址为空，启用Meting备用音源
        if (TextUtils.isEmpty(realUrl)) {
            realUrl = getBackupAudio(songName);
        }

        if (TextUtils.isEmpty(realUrl)) {
            return Result.get().url("").parse(0).string();
        } else {
            return Result.get()
                    .url(realUrl)
                    .parse(0)
                    .header(getHeader())
                    .string();
        }
    }

    @Override
    public String searchContent(String key, boolean quick) {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String page) {
        if (TextUtils.isEmpty(key)) return Result.string(new ArrayList<>());
        List<Vod> list = new ArrayList<>();
        try {
            String encodeKey = URLEncoder.encode(key, StandardCharsets.UTF_8.name());
            String url = String.format("https://mobilecdn.kugou.com/api/v3/search/song?format=json&keyword=%s&page=%s&pagesize=20", encodeKey, page);
            JSONObject resp = getJson(url);
            JSONArray songArr = null;
            if (resp.optJSONObject("data") != null) {
                songArr = resp.optJSONObject("data").optJSONArray("lists");
            }
            if (songArr != null) {
                for (int i = 0; i < songArr.length(); i++) {
                    JSONObject item = songArr.optJSONObject(i);
                    String vid = buildVodId(item);
                    if (!TextUtils.isEmpty(vid)) {
                        String duration = item.optString("duration");
                        list.add(new Vod(vid, getVodName(item), getVodPic(item), "时长:" + duration + "秒"));
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return Result.string(list);
    }
}
