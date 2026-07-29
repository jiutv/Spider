package com.github.catvod.spider;

import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 酷狗音乐 Spider，可用于 TVBox / CatVod Spider 工程。
 *
 * 说明：
 * 1. 这是从混淆版 KuGou 还原整理的可读版。
 * 2. 原 Str.m4286u(...) 字符串已全部替换为明文。
 * 3. 依赖项目内常见的 com.github.catvod.*、OkHttp、Result、Vod、Spider。
 * 4. HTML 解析使用 jsoup；如果你的工程没有 jsoup，需要替换为项目内已有的 HTML 解析封装。
 */
public class KuGou extends Spider {

    private static final Pattern PLAY_ID_PATTERN = Pattern.compile("play_id\\\\u0022:\\\\u0022([^\\\\]+)");

    private static final String MOBILE_UA = "KuGou2012-9108-Expand133ManagerReview-117000-Android2010-9108-AddPlatinumDeviceExpand1-0";
    private static final String WEB_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";

    private static final String DEFAULT_COVER = "https://p1.music.126.net/5KJI2mq0G0OQHQaAfAJfwg==/109951173289563385.jpg?param=300y300";

    private static final String KUGOU_RANK_LIST = "http://mobilecdnbj.kugou.com/api/v3/rank/list?version=9108&plat=0&showtype=2&parentid=0&apiver=6&area_code=1&withsong=0&with_res_tag=0";
    private static final String KUGOU_RANK_SONG = "http://mobilecdnbj.kugou.com/api/v3/rank/song?version=9108&ranktype=0&plat=0&pagesize=200&area_code=1&page=1&volid=35050&rankid=%s&with_res_tag=0";
    private static final String KUGOU_SONG_INFO = "https://m.kugou.com/app/i/getSongInfo.php?cmd=playInfo&hash=%s";
    private static final String KUGOU_MV_INFO = "https://m.kugou.com/app/i/mv.php?cmd=100&hash=%s&ismp3=1&ext=mp4";
    private static final String KUGOU_SEARCH = "http://mobilecdn.kugou.com/api/v3/search/song?format=json&keyword=%s&page=%s&pagesize=30&showtype=1";

    private static final String SQ0527_BASE = "https://www.sq0527.cn/";
    private static final String SQ0527_SEARCH = "https://www.sq0527.cn/search?ac=";

    private static final String GEQUBAO_BASE = "https://www.gequbao.com";
    private static final String GEQUBAO_HOME = "https://www.gequbao.com/";
    private static final String GEQUBAO_SEARCH = "https://www.gequbao.com/s/";
    private static final String GEQUBAO_COMMON_PLAY_URL = "https://www.gequbao.com/member/common-play-url";

    private static String empty() {
        return Result.string(new ArrayList<Vod>());
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String formatCoverUrl(String url) {
        if (TextUtils.isEmpty(url)) return DEFAULT_COVER;
        return url.replace("{size}", "400");
    }

    private static String extractSongTitle(JSONObject item) {
        String title = item.optString("filename");
        if (TextUtils.isEmpty(title)) title = item.optString("songname");
        if (TextUtils.isEmpty(title)) {
            title = item.optString("singername") + " - " + item.optString("songname");
        }
        return title.trim();
    }

    private static String buildMp3VodId(JSONObject item) {
        String hash = item.optString("sqhash");
        if (TextUtils.isEmpty(hash)) hash = item.optString("hash");
        if (TextUtils.isEmpty(hash)) return "";
        return "kugou-mp3_" + hash + "_" + item.optString("album_id") + "_" + item.optString("album_audio_id");
    }

    private static String extractCover(JSONObject item) {
        JSONObject transParam;
        String cover = item.optString("album_sizable_cover");
        if (TextUtils.isEmpty(cover)) cover = item.optString("imgurl");
        if (TextUtils.isEmpty(cover) && (transParam = item.optJSONObject("trans_param")) != null) {
            cover = transParam.optString("union_cover");
        }
        return formatCoverUrl(cover);
    }

    private static void addPlay(ArrayList<String> plays, String name, String split, String id) {
        if (!TextUtils.isEmpty(id)) plays.add(name + split + id);
    }

    private JSONObject requestJson(String url) {
        try {
            return new JSONObject(OkHttp.string(url, null, buildMobileHeaders()));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private String requestHtml(String url, String referer) {
        try {
            return OkHttp.string(url, null, buildWebHeaders(referer));
        } catch (Exception e) {
            return "";
        }
    }

    private Map<String, String> buildMobileHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", MOBILE_UA);
        return headers;
    }

    private Map<String, String> buildWebHeaders(String referer) {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", WEB_UA);
        if (!TextUtils.isEmpty(referer)) headers.put("Referer", referer);
        return headers;
    }

    public String homeContent(boolean filter) {
        ArrayList<Class> classes = new ArrayList<>();
        classes.add(new Class("hot", "热门榜"));
        classes.add(new Class("special", "特色榜"));
        classes.add(new Class("global", "全球榜"));
        return Result.string(classes, new ArrayList<>());
    }

    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        if (!"1".equals(pg)) return empty();

        ArrayList<Vod> list = new ArrayList<>();
        JSONObject root = requestJson(KUGOU_RANK_LIST);
        JSONObject data = root.optJSONObject("data");
        JSONArray info = data == null ? null : data.optJSONArray("info");

        if (info != null) {
            for (int i = 0; i < info.length(); i++) {
                JSONObject item = info.optJSONObject(i);
                if (item == null) continue;

                String classify = item.optString("classify");
                boolean keep;
                if ("hot".equals(tid)) {
                    keep = !("1".equals(classify) || "2".equals(classify));
                } else if ("special".equals(tid)) {
                    keep = "2".equals(classify) || "5".equals(classify);
                } else if ("global".equals(tid)) {
                    keep = "4".equals(classify) || "2".equals(classify);
                } else {
                    keep = false;
                }

                if (keep) {
                    list.add(new Vod(
                            "kugou_" + item.optString("rankid"),
                            item.optString("rankname"),
                            formatCoverUrl(item.optString("imgurl")),
                            "榜单"
                    ));
                }
            }
        }

        return Result.string(list);
    }

    public String detailContent(List<String> ids) {
        if (ids == null || ids.isEmpty()) return empty();

        String id = ids.get(0);
        String split = "$";
        String defaultCover = DEFAULT_COVER;

        try {
            if (id.startsWith("kugou-mp3_")) {
                String[] parts = id.substring("kugou-mp3_".length()).split("_");
                String title = "酷狗单曲";
                String cover = defaultCover;

                if (parts.length > 0 && !TextUtils.isEmpty(parts[0])) {
                    JSONObject json = requestJson(String.format(KUGOU_SONG_INFO, parts[0]));
                    if (!TextUtils.isEmpty(json.optString("fileName"))) title = json.optString("fileName");
                    if (!TextUtils.isEmpty(json.optString("album_img"))) cover = formatCoverUrl(json.optString("album_img"));
                }

                Vod vod = new Vod(id, title, cover, "酷狗单曲");
                vod.setVodPlayFrom("MP3");
                vod.setVodPlayUrl(title + split + id);
                vod.setVodContent("酷狗音乐 · 酷狗单曲");
                return Result.string(vod);
            }

            if (id.startsWith("kugou-mv_")) {
                String[] parts = id.substring("kugou-mv_".length()).split("_");
                String title = "酷狗单曲";
                String cover = defaultCover;

                if (parts.length > 0 && !TextUtils.isEmpty(parts[0])) {
                    JSONObject json = requestJson(String.format(KUGOU_SONG_INFO, parts[0]));
                    if (!TextUtils.isEmpty(json.optString("fileName"))) title = json.optString("fileName");
                    if (!TextUtils.isEmpty(json.optString("album_img"))) cover = formatCoverUrl(json.optString("album_img"));
                }

                Vod vod = new Vod(id, title, cover, "酷狗单曲");
                vod.setVodPlayFrom("MV");
                vod.setVodPlayUrl(title + split + id);
                vod.setVodContent("酷狗音乐 · 酷狗单曲");
                return Result.string(vod);
            }

            if (id.startsWith("kugou_")) {
                String rankId = id.substring("kugou_".length());
                JSONObject root = requestJson(String.format(KUGOU_RANK_SONG, rankId));
                JSONObject data = root.optJSONObject("data");
                JSONArray info = data == null ? null : data.optJSONArray("info");

                ArrayList<String> mp3Plays = new ArrayList<>();
                ArrayList<String> mvPlays = new ArrayList<>();
                String cover = defaultCover;

                if (info != null) {
                    for (int i = 0; i < info.length(); i++) {
                        JSONObject item = info.optJSONObject(i);
                        if (item == null) continue;

                        String title = extractSongTitle(item);
                        String mp3Id = buildMp3VodId(item);
                        addPlay(mp3Plays, title, split, mp3Id);

                        String mvHash = item.optString("mvhash");
                        if (!TextUtils.isEmpty(mvHash)) {
                            addPlay(mvPlays, title, split, "kugou-mv_" + mvHash);
                        }

                        if (DEFAULT_COVER.equals(cover)) cover = extractCover(item);
                    }
                }

                Vod vod = new Vod(id, "酷狗榜单", cover, mp3Plays.size() + "首");
                vod.setVodPlayFrom("MP3$$$MV");
                vod.setVodPlayUrl(TextUtils.join("#", mp3Plays) + "$$$" + TextUtils.join("#", mvPlays));
                vod.setVodContent("酷狗音乐 · 榜单");
                return Result.string(vod);
            }
        } catch (Exception ignored) {
        }

        return empty();
    }

    public String playerContent(String flag, String id, List<String> vipFlags) {
        if (TextUtils.isEmpty(id)) return emptyPlayer("");

        if (id.contains("-")) {
            String[] split = id.split("-", 2);
            if (split.length == 2) id = split[1];
        }

        String playUrl = "";

        try {
            if (id.startsWith("mp3_")) {
                playUrl = id.substring("mp3_".length());
            } else if (id.startsWith("kugou-mp3_")) {
                playUrl = parseMp3(id);
            } else if (id.startsWith("kugou-mv_")) {
                playUrl = parseMv(id);
            }
        } catch (Exception ignored) {
            playUrl = "";
        }

        Map<String, String> headers = buildMobileHeaders();
        if (playUrl.contains("gequbao") || playUrl.contains("kugou") || playUrl.contains("sq0527")) {
            headers = buildWebHeaders(GEQUBAO_HOME);
        }

        return Result.get().url(playUrl).parse(0).header(headers).string();
    }

    private String emptyPlayer(String url) {
        return Result.get().url(url).parse(0).header(buildMobileHeaders()).string();
    }

    private String parseMp3(String id) {
        String[] parts = id.split("_");
        if (parts.length < 2) return "";

        String hash = parts[1];
        JSONObject json = requestJson(String.format(KUGOU_SONG_INFO, hash));

        String playUrl = json.optString("url");
        if (TextUtils.isEmpty(playUrl)) {
            Object backup = json.opt("backup_url");
            if (backup instanceof JSONArray) {
                JSONArray array = (JSONArray) backup;
                if (array.length() > 0) playUrl = array.optString(0);
            } else if (backup instanceof String) {
                playUrl = (String) backup;
            }
        }

        String error = json.optString("error");
        if (!TextUtils.isEmpty(playUrl) && !safe(error).contains("付费")) {
            return playUrl;
        }

        String songName = json.optString("songName");
        String singerName = json.optString("author_name");
        if (TextUtils.isEmpty(singerName)) singerName = json.optString("singerName");

        String bySq0527 = parseBySq0527(songName, singerName);
        if (!TextUtils.isEmpty(bySq0527)) return bySq0527;

        return parseByGequbao(songName, singerName);
    }

    private String parseBySq0527(String songName, String singerName) {
        if (TextUtils.isEmpty(songName)) return "";

        try {
            String searchUrl = SQ0527_SEARCH + URLEncoder.encode(songName, StandardCharsets.UTF_8.name());
            Document doc = Jsoup.parse(requestHtml(searchUrl, SQ0527_BASE));
            Elements items = doc.select("ul.mul li a");

            for (Element item : items) {
                String text = item.text();
                if (!text.contains(songName)) continue;
                if (!TextUtils.isEmpty(singerName) && !text.contains(singerName)) continue;

                String href = item.attr("href");
                if (TextUtils.isEmpty(href)) continue;
                if (!href.startsWith("http")) href = SQ0527_BASE + href;

                Document detail = Jsoup.parse(requestHtml(href, searchUrl));
                Element download = detail.selectFirst("#btn-download-mp3");
                if (download == null) continue;

                String mp3 = download.attr("href");
                if (TextUtils.isEmpty(mp3)) continue;
                if (!mp3.startsWith("http")) mp3 = SQ0527_BASE + mp3;

                return mp3;
            }
        } catch (Exception ignored) {
        }

        return "";
    }

    private String parseByGequbao(String songName, String singerName) {
        if (TextUtils.isEmpty(songName)) return "";

        try {
            String searchUrl = GEQUBAO_SEARCH + URLEncoder.encode(songName, StandardCharsets.UTF_8.name());
            Document doc = Jsoup.parse(requestHtml(searchUrl, GEQUBAO_HOME));
            Elements items = doc.select("a[href^=/music/]");

            for (Element item : items) {
                String title = item.attr("title");
                if (TextUtils.isEmpty(title)) title = item.text();
                if (!title.contains(songName)) continue;
                if (!TextUtils.isEmpty(singerName) && !title.contains(singerName)) continue;

                String href = item.attr("href");
                if (TextUtils.isEmpty(href)) continue;

                String detailUrl = GEQUBAO_BASE + href;
                String detailHtml = requestHtml(detailUrl, searchUrl);
                Matcher matcher = PLAY_ID_PATTERN.matcher(detailHtml);
                if (!matcher.find()) continue;

                String postBody = "id=" + URLEncoder.encode(matcher.group(1), StandardCharsets.UTF_8.name());
                HashMap<String, String> headers = new HashMap<>(buildWebHeaders(detailUrl));
                headers.put("Content-Type", "application/x-www-form-urlencoded");

                String body = OkHttp.post(GEQUBAO_COMMON_PLAY_URL, postBody, headers).getBody();
                JSONObject result = new JSONObject(body);
                JSONObject data = result.optJSONObject("data");
                if (result.optInt("code") == 1 && data != null) {
                    String url = data.optString("url");
                    if (!TextUtils.isEmpty(url)) return url;
                }
            }
        } catch (Exception ignored) {
        }

        return "";
    }

    private String parseMv(String id) {
        String[] parts = id.split("_");
        if (parts.length < 2) return "";

        JSONObject root = requestJson(String.format(KUGOU_MV_INFO, parts[1]));
        JSONObject mvdata = root.optJSONObject("mvdata");
        if (mvdata == null) return "";

        String quality = mvdata.has("sq") ? "sq" : "le";
        JSONObject item = mvdata.optJSONObject(quality);
        return item == null ? "" : item.optString("downurl");
    }

    public String searchContent(String key, boolean quick) {
        return searchContent(key, quick, "1");
    }

    public String searchContent(String key, boolean quick, String pg) {
        if (TextUtils.isEmpty(key)) return empty();
        if (TextUtils.isEmpty(pg)) pg = "1";

        ArrayList<Vod> list = new ArrayList<>();

        try {
            String url = String.format(KUGOU_SEARCH, URLEncoder.encode(key.trim(), StandardCharsets.UTF_8.name()), pg);
            JSONObject root = requestJson(url);
            JSONObject data = root.optJSONObject("data");
            JSONArray info = data == null ? null : data.optJSONArray("info");

            if (info != null) {
                for (int i = 0; i < info.length(); i++) {
                    JSONObject item = info.optJSONObject(i);
                    if (item == null) continue;

                    String vodId = buildMp3VodId(item);
                    if (TextUtils.isEmpty(vodId)) continue;

                    list.add(new Vod(
                            vodId,
                            extractSongTitle(item),
                            extractCover(item),
                            item.optString("singername")
                    ));
                }
            }
        } catch (Exception ignored) {
        }

        return Result.string(list);
    }
}
