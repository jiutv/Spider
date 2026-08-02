package com.github.catvod.spider;

import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class KuKuMusic extends Spider {

    private static final Pattern tagPattern = Pattern.compile("<[^>]+>");
    private static final Pattern sepPattern = Pattern.compile("[$#]");

    private String buildVod(String id, String name, String pic, String remarks, String actor, String content, String playUrl) {
        Vod vod = new Vod();
        vod.setVodId(id);
        vod.setVodName(name);
        if (!TextUtils.isEmpty(pic)) vod.setVodPic(pic);
        if (!TextUtils.isEmpty(remarks)) vod.setVodRemarks(remarks);
        if (!TextUtils.isEmpty(actor)) vod.setVodActor(actor);
        if (!TextUtils.isEmpty(content)) vod.setVodContent(content);
        vod.setVodPlayFrom("酷我音乐");
        vod.setVodPlayUrl(playUrl);
        return Result.string(vod);
    }

    private JSONArray fetchArtistMusic(String artistId) {
        JSONArray result = new JSONArray();
        for (int i = 1; i <= 10; i++) {
            try {
                JSONObject data = fetchData("http://wapi.kuwo.cn/api/www/artist/artistMusic?artistid=" + artistId + "&pn=" + i + "&rn=30");
                JSONArray list = data != null ? data.optJSONArray("list") : null;
                if (list == null || list.length() == 0) break;
                for (int j = 0; j < list.length(); j++) {
                    JSONObject item = list.getJSONObject(j);
                    if (!TextUtils.isEmpty(item.optString("name", "").trim())) {
                        result.put(item);
                        if (result.length() >= 300) return result;
                    }
                }
            } catch (Exception unused) {
            }
        }
        return result;
    }

    private String buildPlayUrl(JSONArray array) {
        ArrayList<String> list = new ArrayList<>();
        int limit = Math.min(array.length(), 300);
        for (int i = 0; i < limit; i++) {
            JSONObject item = array.optJSONObject(i);
            if (item != null) {
                String name = item.optString("name");
                String nameTrim = TextUtils.isEmpty(name) ? "" : sepPattern.matcher(name).replaceAll("").trim();
                String rid = item.optString("rid");
                if (!TextUtils.isEmpty(nameTrim) && !TextUtils.isEmpty(rid)) {
                    String album = item.optString("album");
                    if (TextUtils.isEmpty(album)) {
                        list.add(nameTrim + "$" + rid);
                    } else {
                        list.add(nameTrim + " - " + album + "$" + rid);
                    }
                }
            }
        }
        return TextUtils.join("#", list);
    }

    private HashMap<String, String> getHeader() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        headers.put("Referer", "http://www.kuwo.cn/");
        return headers;
    }

    private JSONObject fetchData(String url) {
        try {
            return new JSONObject(OkHttp.string(url, getHeader())).optJSONObject("data");
        } catch (Exception e) {
            return null;
        }
    }

    private String fixScheme(String str) {
        if (TextUtils.isEmpty(str)) return "";
        if (str.startsWith("//")) return "https:" + str;
        if (!str.startsWith("http://")) return str;
        return "https://" + str.substring(7);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        int page = 1;
        try {
            page = Math.max(1, Integer.parseInt(pg));
        } catch (Exception unused) {
        }
        try {
            JSONObject data = fetchData("http://wapi.kuwo.cn/api/www/artist/artistInfo?category=" + tid + "&prefix=&pn=" + page + "&rn=30");
            JSONArray artistList = data != null ? data.optJSONArray("artistList") : null;
            ArrayList<Vod> list = new ArrayList<>();
            if (artistList != null) {
                for (int i = 0; i < artistList.length(); i++) {
                    JSONObject item = artistList.getJSONObject(i);
                    String id = item.optString("id");
                    String name = item.optString("name");
                    if (!TextUtils.isEmpty(id) && !TextUtils.isEmpty(name)) {
                        String pic = item.optString("pic300");
                        if (TextUtils.isEmpty(pic)) pic = item.optString("pic");
                        if (TextUtils.isEmpty(pic)) pic = item.optString("pic120");
                        Vod vod = new Vod(id, name, fixScheme(pic), "");
                        vod.setStyle(Vod.Style.oval());
                        list.add(vod);
                    }
                }
            }
            return Result.get().page(page, 9999, 30, 999999).vod(list).string();
        } catch (Exception unused) {
            return Result.string(new ArrayList<>());
        }
    }

    @Override
    public String detailContent(List<String> ids) {
        if (ids == null || ids.isEmpty()) return "{}";
        String id = ids.get(0);
        try {
            JSONObject data = fetchData("http://wapi.kuwo.cn/api/www/artist/artist?artistid=" + id);
            if (data == null) {
                return buildVod(id, "加载失败", "", "加载失败", "未知", "加载歌手信息失败", "");
            }
            String name = data.optString("name");
            String pic = fixScheme(data.optString("pic300", data.optString("pic")));
            String info = data.optString("info");
            String content;
            if (TextUtils.isEmpty(info)) {
                content = "暂无歌手简介";
            } else {
                String cleaned = tagPattern.matcher(info).replaceAll("").replace("&nbsp;", " ").replace("\r\n", "\n").replace("\r", "\n").trim();
                content = TextUtils.isEmpty(cleaned) ? "暂无歌手简介" : cleaned;
            }
            JSONArray songs = fetchArtistMusic(id);
            return buildVod(id, name, pic, "歌曲 :   " + Math.min(songs.length(), 300) + "首", name, content, buildPlayUrl(songs));
        } catch (Exception e) {
            return buildVod(id, "加载失败", "", "加载失败", "未知", "错误: " + e.getMessage(), "");
        }
    }

    @Override
    public String homeContent(boolean filter) {
        ArrayList<Class> classes = new ArrayList<>();
        classes.add(new Class("1", "华语男"));
        classes.add(new Class("2", "华语女"));
        classes.add(new Class("3", "华语组合"));
        classes.add(new Class("4", "日韩男"));
        classes.add(new Class("5", "日韩女"));
        classes.add(new Class("6", "日韩组合"));
        classes.add(new Class("7", "欧美男"));
        classes.add(new Class("8", "欧美女"));
        classes.add(new Class("9", "欧美组合"));
        classes.add(new Class("0", "其他"));
        return Result.string(classes, new ArrayList<>());
    }

    @Override
    public String homeVideoContent() {
        return categoryContent("1", "1", false, new HashMap<>());
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        try {
            String rid = id;
            if (rid.contains("$")) rid = rid.substring(rid.lastIndexOf('$') + 1);
            if (rid.startsWith("MUSIC_")) rid = rid.substring(6);
            rid = rid.trim();
            if (TextUtils.isEmpty(rid)) {
                return Result.get().parse(0).url("").string();
            }
            String api = "https://nmobi.kuwo.cn/mobi.s?f=web&user=0&source=kwplayer_ar_4.4.2.7_B_nuoweida_vh.apk&type=convert_url_with_sign&rid=" + rid + "&bitrate=128&format=mp3";
            HashMap<String, String> headers = new HashMap<>();
            headers.put("User-Agent", "Mozilla/5.0 (Linux; Android 10)");
            headers.put("Referer", "https://www.kuwo.cn/");
            String url = "";
            try {
                JSONObject json = new JSONObject(OkHttp.string(api, headers));
                if (json.optInt("code", 0) == 200) {
                    JSONObject data = json.optJSONObject("data");
                    if (data != null) url = data.optString("url");
                }
            } catch (Exception unused) {
            }
            if (!TextUtils.isEmpty(url) && url.startsWith("http")) {
                HashMap<String, String> playHeaders = new HashMap<>();
                playHeaders.put("User-Agent", "Mozilla/5.0 (Linux; Android 10)");
                playHeaders.put("Accept", "*/*");
                return Result.get().parse(0).url(url).octet().header(playHeaders).string();
            }
            return Result.get().parse(0).url("").string();
        } catch (Exception unused) {
            return Result.get().parse(0).url("").string();
        }
    }

    @Override
    public String searchContent(String key, boolean quick) {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) {
        if (TextUtils.isEmpty(key)) return "{}";
        int page = 1;
        try {
            page = Math.max(1, Integer.parseInt(pg));
        } catch (Exception unused) {
        }
        try {
            JSONObject json = new JSONObject(OkHttp.string("https://search.kuwo.cn/r.s?client=kt&pn=" + ((page - 1) * 30) + "&rn=30&all=" + URLEncoder.encode(key.trim(), "UTF-8") + "&vipver=1&ft=artist&encoding=utf8&rformat=json&mobi=1", getHeader()));
            JSONArray abslist = json.optJSONArray("abslist");
            ArrayList<Vod> list = new ArrayList<>();
            if (abslist != null) {
                String basePic = json.optString("BASEPICPATH", "http://img1.kuwo.cn/star/starheads/");
                for (int i = 0; i < abslist.length(); i++) {
                    JSONObject item = abslist.getJSONObject(i);
                    String artistId = item.optString("ARTISTID", item.optString("DC_TARGETID"));
                    if (!TextUtils.isEmpty(artistId)) {
                        String pic = item.optString("hts_PICPATH");
                        if (TextUtils.isEmpty(pic) && !TextUtils.isEmpty(item.optString("PICPATH"))) {
                            pic = basePic + item.optString("PICPATH");
                        }
                        String name = item.optString("ARTIST");
                        Vod vod = new Vod(artistId, name, fixScheme(pic), "歌曲 :  " + item.optString("SONGNUM", "0") + "首");
                        vod.setStyle(Vod.Style.oval());
                        list.add(vod);
                    }
                }
            }
            return Result.get().page(page, 9999, 30, 999999).vod(list).string();
        } catch (Exception unused) {
            return "{}";
        }
    }
}
