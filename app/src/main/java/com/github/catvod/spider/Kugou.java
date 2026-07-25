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
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Kugou extends Spider {

    private Map<String, String> getHeader() {
        Map<String, String> header = new HashMap<>();
        header.put("User-Agent", Util.CHROME);
        return header;
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        List<Vod> list = new ArrayList<>();
        List<String> typeIds = Arrays.asList("6666|0", "33162|1", "4681|2");
        List<String> typeNames = Arrays.asList("热门榜单", "特色音乐榜", "全球榜");
        for (int i = 0; i < typeIds.size(); i++) {
            classes.add(new Class(typeIds.get(i), typeNames.get(i)));
        }
        return Result.string(classes, list);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        HashMap<String, String> ext = new HashMap<>();
        if (extend != null && extend.size() > 0) ext.putAll(extend);
        String[] item = tid.split("\\|");
        String id = item[0];
        String digit = item[1];
        int digitValue = Integer.parseInt(digit);
        String cateId = ext.get("cateId") == null ? id : ext.get("cateId");
        String cateUrl = String.format("https://www.kugou.com/yy/rank/home/1-%s.html?from=rank", cateId);
        Document doc = Jsoup.parse(OkHttp.string(cateUrl, getHeader()));
        Elements lis = doc.select(".pc_rank_sidebar").eq(digitValue).select("ul li a");
        JSONArray videos = new JSONArray();
        for (Element li : lis) {
            String rankUrl = li.attr("href");
            String rankTitle = li.attr("title");
            JSONObject vod = new JSONObject()
                    .put("vod_id", rankUrl)
                    .put("vod_name", rankTitle);
            videos.put(vod);
        }
        JSONObject result = new JSONObject()
                .put("total", lis.size())
                .put("pagecount", 1)
                .put("list", videos);
        return result.toString();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String rankUrl = ids.get(0);
        Document doc = Jsoup.parse(OkHttp.string(rankUrl, getHeader()));
        Elements playlist = doc.select(".pc_temp_songlist ul li");
        List<String> vodItems = new ArrayList<>();
        for (Element item : playlist) {
            String songName = item.select("a.pc_temp_songname").text().trim();
            if (!TextUtils.isEmpty(songName)) {
                // 选集格式：歌名$歌名，播放时把名称传给player
                vodItems.add(songName + "$" + songName);
            }
        }
        String title = doc.select(".pc_temp_title h3").text().trim();
        String remark = doc.select(".rank_update").text().trim();

        Vod vod = new Vod();
        vod.setVodId(rankUrl);
        vod.setVodName(title);
        vod.setVodRemarks(remark);
        vod.setVodPlayFrom("酷狗榜单");
        vod.setVodPlayUrl(TextUtils.join("#", vodItems));
        return Result.string(vod);
    }

    @Override
    public String playerContent(String flag, String songName, List<String> vipFlags) throws Exception {
        if (TextUtils.isEmpty(songName)) throw new Exception("歌曲名称为空");
        String keyword = URLEncoder.encode(songName, "UTF-8");
        // meting api 酷狗音源搜索
        String searchApi = String.format("https://api.injahow.cn/meting/api?server=kg&type=search&keywords=%s", keyword);
        String response = OkHttp.string(searchApi, getHeader());
        JSONObject json = new JSONObject(response);
        JSONArray data = json.getJSONArray("data");
        if (data.length() == 0) throw new Exception("未找到音源");

        JSONObject track = data.getJSONObject(0);
        String audioUrl = track.optString("url");
        if (TextUtils.isEmpty(audioUrl)) throw new Exception("暂无试听资源");

        // 返回真实音频直链，TVBox播放器直接解码播放
        return Result.get()
                .url(audioUrl)
                .header(getHeader())
                .string();
    }
}
