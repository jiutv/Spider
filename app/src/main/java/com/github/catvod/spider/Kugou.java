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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Kugou extends Spider {

    // 正则匹配页面内歌曲hash
    private static final Pattern HASH_PATTERN = Pattern.compile("\"hash\":\"([0-9A-F]+)\"");

    // 请求头
    private Map<String, String> getHeader() {
        Map<String, String> header = new HashMap<>();
        header.put("User-Agent", Util.CHROME);
        header.put("Cookie", "kg_mid=8BE5438F72ED7681652BAAFFE72980C4");
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
            Element aTag = item.selectFirst("a.pc_temp_songname");
            if (aTag == null) continue;
            String songName = aTag.text().trim();
            String songPageUrl = aTag.attr("href");
            if (TextUtils.isEmpty(songName) || TextUtils.isEmpty(songPageUrl)) continue;
            // 选集格式：歌名$单曲网页地址
            vodItems.add(songName + "$" + songPageUrl);
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
    public String playerContent(String flag, String data, List<String> vipFlags) throws Exception {
        String[] arr = data.split("\\$");
        if (arr.length < 2) throw new Exception("歌曲地址缺失");
        String songUrl = arr[1];
        // 访问单曲播放页面，提取hash
        String html = OkHttp.string(songUrl, getHeader());
        Matcher matcher = HASH_PATTERN.matcher(html);
        if (!matcher.find()) throw new Exception("无法获取歌曲Hash");
        String hash = matcher.group(1);
        // 调用酷狗网页播放接口
        String apiUrl = String.format("https://wwwapi.kugou.com/yy/index.php?r=play/getdata&hash=%s", hash);
        String apiResp = OkHttp.string(apiUrl, getHeader());
        JSONObject json = new JSONObject(apiResp);
        JSONObject dataObj = json.getJSONObject("data");
        String audioUrl = dataObj.optString("play_url");
        if (TextUtils.isEmpty(audioUrl)) throw new Exception("暂无试听音源");
        // 返回音频直链给TVBox播放器
        return Result.get()
                .url(audioUrl)
                .header(getHeader())
                .string();
    }
}
