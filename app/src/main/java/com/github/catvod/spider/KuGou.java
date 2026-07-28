package com.github.catvod.spider;

import android.content.Context;
import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import com.github.catvod.net.OkResult;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class KuGou extends Spider {
    private static final String SEARCH_API = "http://mobilecdn.kugou.com/api/v3/search/song?format=json&keyword=%s&page=%d";
    private static final String PLAY_API = "https://m.kugou.com/app/i/getSongInfo.php?cmd=playInfo&hash=%s";

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        return Result.string(classes, new ArrayList<>());
    }

    @Override
    public String homeVideoContent() throws Exception {
        return Result.string(new ArrayList<>());
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        return Result.string(new ArrayList<>());
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return searchVideo(key, 1);
    }

    private String searchVideo(String key, int page) throws Exception {
        String url = String.format(SEARCH_API, URLEncoder.encode(key, "UTF-8"), page);
        OkResult result = OkHttp.get(url, null, null);
        String resp = result.text();
        List<Vod> list = new ArrayList<>();

        try {
            JSONObject obj = new JSONObject(resp);
            JSONObject data = obj.optJSONObject("data");
            if (data == null) return Result.string(list);
            JSONArray songArr = data.optJSONArray("songinfo");
            if (songArr == null) return Result.string(list);

            for (int i = 0; i < songArr.length(); i++) {
                JSONObject song = songArr.optJSONObject(i);
                String hash = song.optString("hash");
                String name = song.optString("songname");
                String singer = song.optString("singername");
                String pic = song.optString("album_img");
                pic = pic.replace("{size}", "400").replace("http://", "https://");

                Vod vod = new Vod();
                vod.setVodId(hash);
                vod.setVodName(name);
                vod.setVodPic(pic);
                vod.setVodActor(singer);
                list.add(vod);
            }
        } catch (Exception ignored) {}
        return Result.string(list);
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String hash = ids.get(0);
        String url = String.format(PLAY_API, hash);
        OkResult result = OkHttp.get(url, null, null);
        String resp = result.text();

        JSONObject info = new JSONObject(resp);
        String songName = info.optString("songName");
        String singer = info.optString("author_name");
        String pic = info.optString("album_img").replace("{size}", "400").replace("http://", "https://");
        String playUrl = info.optString("playUrl");

        Vod vod = new Vod();
        vod.setVodName(songName);
        vod.setVodActor(singer);
        vod.setVodPic(pic);
        vod.setVodPlayFrom("酷狗");
        vod.setVodPlayUrl("播放$" + playUrl);
        return Result.string(vod);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        return Result.get().url(id).parse().string();
    }
}
