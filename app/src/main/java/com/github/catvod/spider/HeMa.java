package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;
import com.github.catvod.bean.Class;
import com.github.catvod.bean.Filter;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class HeMa extends Spider {

    private String f118a = "3.7.0";
    private String f119b = "com.dz.hmjc";
    private String f120c = "HMJC1000000";
    private String f121d = "A20260716161211528mStog5";
    private String f122e = "45a078a3e0a0c07e9da15cc1bd98041a";
    private String f123f = "2854498494";
    private long f124g = 1784189517540L;
    private String f125h = "BnCMa2+Kbcl9D1ctCsHNdkR0b6tNUb4MWkCbJX2wXsVtqTKVONaSzTI3b+ulP+SEOFULO5vnXXaMNkE6Gcy7K0g==";
    private String f126i = "";

    private String m141a(String str, JSONObject jSONObject) throws Exception {
        String string = jSONObject.toString();
        String url = "https://freevideo.zqqds.cn/free-video-portal/portal/" + str;
        JSONObject jSONObject2 = new JSONObject();
        jSONObject2.put("apiId", str);
        jSONObject2.put("url", url);
        jSONObject2.put("body", string);
        JSONObject jSONObject3 = new JSONObject();
        jSONObject3.put("version", this.f118a);
        jSONObject3.put("pname", this.f119b);
        jSONObject3.put("channelCode", this.f120c);
        jSONObject3.put("utdidTmp", this.f121d);
        jSONObject3.put("utdid", this.f122e);
        jSONObject3.put("userId", this.f123f);
        jSONObject3.put("installTime", this.f124g);
        jSONObject3.put("boxId", this.f125h);
        jSONObject2.put("config", jSONObject3);
        HashMap<String, String> map = new HashMap<>();
        map.put("Content-Type", "application/json; charset=utf-8");
        String result = OkHttp.post(this.f126i, jSONObject2.toString(), map).getBody();
        if (TextUtils.isEmpty(result)) {
            return "";
        }
        JSONObject jSONObject4 = new JSONObject(result);
        String key = "data";
        if (jSONObject4.has(key) && (jSONObject4.opt(key) instanceof JSONObject)) {
            return jSONObject4.getJSONObject(key).toString();
        }
        if (jSONObject4.has(key) && (jSONObject4.opt(key) instanceof JSONArray)) {
            return jSONObject4.getJSONArray(key).toString();
        }
        return (!jSONObject4.has(key) || TextUtils.isEmpty(jSONObject4.optString(key))) ? result : jSONObject4.optString(key);
    }

    private void m142b(String str, Object obj, List<String> list, Map<String, Boolean> map) {
        int i = 0;
        if (obj instanceof JSONArray) {
            JSONArray jSONArray = (JSONArray) obj;
            while (i < jSONArray.length()) {
                m142b(str, jSONArray.opt(i), list, map);
                i++;
            }
            return;
        }
        if (obj instanceof JSONObject) {
            JSONObject jSONObject = (JSONObject) obj;
            String chapterId = m152n(jSONObject, "chapterId", "chapter_id", "id");
            if (!TextUtils.isEmpty(chapterId) && !map.containsKey(chapterId)) {
                boolean zHas = jSONObject.has("chapterId");
                if (zHas || jSONObject.has("chapterName") || jSONObject.has("duration") || jSONObject.has("videoId") || jSONObject.has("playUrl")) {
                    map.put(chapterId, Boolean.TRUE);
                    String chapterName = m152n(jSONObject, "chapterName", "name", "title");
                    if (TextUtils.isEmpty(chapterName)) {
                        chapterName = String.valueOf(list.size() + 1);
                    }
                    String preChapterId = m152n(jSONObject, "preChapterId", "prevChapterId");
                    if (TextUtils.isEmpty(preChapterId)) {
                        preChapterId = chapterId;
                    }
                    StringBuilder sb = new StringBuilder();
                    sb.append(chapterName);
                    sb.append("$");
                    sb.append(str);
                    sb.append("|||");
                    sb.append(chapterId);
                    sb.append("|||");
                    sb.append(preChapterId);
                    list.add(sb.toString());
                }
            }
            JSONArray jSONArrayNames = jSONObject.names();
            if (jSONArrayNames == null) {
                return;
            }
            while (i < jSONArrayNames.length()) {
                m142b(str, jSONObject.opt(jSONArrayNames.optString(i)), list, map);
                i++;
            }
        }
    }

    private void m143c(Object obj, List<Vod> list) {
        int i = 0;
        if (obj instanceof JSONArray) {
            JSONArray jSONArray = (JSONArray) obj;
            while (i < jSONArray.length()) {
                m143c(jSONArray.opt(i), list);
                i++;
            }
            return;
        }
        if (obj instanceof JSONObject) {
            JSONObject jSONObject = (JSONObject) obj;
            String bookId = m152n(jSONObject, "bookId", "id", "book_id", "novelId");
            String bookName = m152n(jSONObject, "bookName", "name", "title", "bookAlias");
            if (!TextUtils.isEmpty(bookId) && !TextUtils.isEmpty(bookName)) {
                list.add(new Vod(bookId, bookName, m150l(jSONObject, "coverWap", "cover", "coverUrl", "image", "pic"), m152n(jSONObject, "chapterInfo", "serialCount", "latestChapterName", "categoryName", "desc")));
                return;
            }
            JSONArray jSONArrayNames = jSONObject.names();
            if (jSONArrayNames == null) {
                return;
            }
            while (i < jSONArrayNames.length()) {
                m143c(jSONObject.opt(jSONArrayNames.optString(i)), list);
                i++;
            }
        }
    }

    private String m144f(JSONObject jSONObject) {
        if (jSONObject == null) {
            return "";
        }
        JSONObject content = jSONObject.optJSONObject("content");
        String url = m151m(m152n(content, "mp4Url", "playUrl", "videoUrl", "url"));
        if (!TextUtils.isEmpty(url)) {
            return url;
        }
        String mp4SwitchUrl = m151m(m149k(content == null ? null : content.optJSONArray("mp4SwitchUrl")));
        if (!TextUtils.isEmpty(mp4SwitchUrl)) {
            return mp4SwitchUrl;
        }
        String url2 = m151m(m152n(jSONObject, "mp4Url", "playUrl", "videoUrl", "url"));
        return !TextUtils.isEmpty(url2) ? url2 : m151m(m149k(jSONObject.optJSONArray("mp4SwitchUrl")));
    }

    private JSONObject m145g(Object obj, String... strArr) {
        int i = 0;
        if (obj instanceof JSONObject) {
            JSONObject jSONObject = (JSONObject) obj;
            for (String str : strArr) {
                if (jSONObject.has(str)) {
                    return jSONObject;
                }
            }
            JSONArray jSONArrayNames = jSONObject.names();
            if (jSONArrayNames == null) {
                return null;
            }
            while (i < jSONArrayNames.length()) {
                JSONObject result = m145g(jSONObject.opt(jSONArrayNames.optString(i)), strArr);
                if (result != null) {
                    return result;
                }
                i++;
            }
        } else if (obj instanceof JSONArray) {
            JSONArray jSONArray = (JSONArray) obj;
            while (i < jSONArray.length()) {
                JSONObject result = m145g(jSONArray.opt(i), strArr);
                if (result != null) {
                    return result;
                }
                i++;
            }
        }
        return null;
    }

    private JSONObject m146h(Object obj, String str, String str2) {
        int i = 0;
        if (obj instanceof JSONObject) {
            JSONObject jSONObject = (JSONObject) obj;
            if (str2.equals(jSONObject.optString(str))) {
                return jSONObject;
            }
            JSONArray jSONArrayNames = jSONObject.names();
            if (jSONArrayNames == null) {
                return null;
            }
            while (i < jSONArrayNames.length()) {
                JSONObject result = m146h(jSONObject.opt(jSONArrayNames.optString(i)), str, str2);
                if (result != null) {
                    return result;
                }
                i++;
            }
        } else if (obj instanceof JSONArray) {
            JSONArray jSONArray = (JSONArray) obj;
            while (i < jSONArray.length()) {
                JSONObject result = m146h(jSONArray.opt(i), str, str2);
                if (result != null) {
                    return result;
                }
                i++;
            }
        }
        return null;
    }

    private String m147i(Object obj) {
        int i = 0;
        if (obj instanceof JSONArray) {
            JSONArray jSONArray = (JSONArray) obj;
            while (i < jSONArray.length()) {
                String result = m147i(jSONArray.opt(i));
                if (!TextUtils.isEmpty(result)) {
                    return result;
                }
                i++;
            }
            return "";
        }
        if (!(obj instanceof JSONObject)) {
            String strValueOf = String.valueOf(obj);
            String url = m151m(strValueOf);
            if (TextUtils.isEmpty(url)) {
                return (strValueOf.startsWith("{") || strValueOf.startsWith("[")) ? m147i(m153o(strValueOf)) : "";
            }
            return url;
        }
        JSONObject jSONObject = (JSONObject) obj;
        JSONArray jSONArrayNames = jSONObject.names();
        if (jSONArrayNames == null) {
            return "";
        }
        while (i < jSONArrayNames.length()) {
            String result = m147i(jSONObject.opt(jSONArrayNames.optString(i)));
            if (!TextUtils.isEmpty(result)) {
                return result;
            }
            i++;
        }
        return "";
    }

    private String m148j(Object obj, String... strArr) {
        int i = 0;
        if (obj instanceof JSONObject) {
            JSONObject jSONObject = (JSONObject) obj;
            for (String str : strArr) {
                String strOptString = jSONObject.optString(str);
                if (!TextUtils.isEmpty(strOptString) && !"null".equals(strOptString)) {
                    return strOptString;
                }
            }
            JSONArray jSONArrayNames = jSONObject.names();
            if (jSONArrayNames == null) {
                return "";
            }
            while (i < jSONArrayNames.length()) {
                String result = m148j(jSONObject.opt(jSONArrayNames.optString(i)), strArr);
                if (!TextUtils.isEmpty(result)) {
                    return result;
                }
                i++;
            }
        } else if (obj instanceof JSONArray) {
            JSONArray jSONArray = (JSONArray) obj;
            while (i < jSONArray.length()) {
                String result = m148j(jSONArray.opt(i), strArr);
                if (!TextUtils.isEmpty(result)) {
                    return result;
                }
                i++;
            }
        }
        return "";
    }

    private String m149k(JSONArray jSONArray) {
        if (jSONArray == null) {
            return "";
        }
        for (int i = 0; i < jSONArray.length(); i++) {
            String strOptString = jSONArray.optString(i);
            if (!TextUtils.isEmpty(strOptString)) {
                return strOptString;
            }
        }
        return "";
    }

    private String m150l(JSONObject jSONObject, String... strArr) {
        for (String str : strArr) {
            String result = m152n(jSONObject, str);
            if (!TextUtils.isEmpty(result) && result.startsWith("http")) {
                return result;
            }
        }
        return "";
    }

    private String m151m(String str) {
        if (!TextUtils.isEmpty(str) && str.startsWith("http")) {
            String lowerCase = str.toLowerCase();
            if (lowerCase.contains(".m3u8") || lowerCase.contains(".mp4")) {
                if (!(lowerCase.contains(".jpg") || lowerCase.contains(".jpeg") || lowerCase.contains(".png") || lowerCase.contains(".webp"))) {
                    return str;
                }
            }
        }
        return "";
    }

    private String m152n(JSONObject jSONObject, String... strArr) {
        if (jSONObject == null) {
            return "";
        }
        for (String str : strArr) {
            String strOptString = jSONObject.optString(str);
            if (!TextUtils.isEmpty(strOptString) && !"null".equals(strOptString)) {
                return strOptString;
            }
        }
        return "";
    }

    private Object m153o(String str) throws Exception {
        if (TextUtils.isEmpty(str)) {
            return new JSONObject();
        }
        String strTrim = str.trim();
        return strTrim.startsWith("[") ? new JSONArray(strTrim) : new JSONObject(strTrim);
    }

    private void m154p(List<Filter.Value> list, String str, String str2) {
        list.add(new Filter.Value(str, str2));
    }

    public String categoryContent(String str, String str2, boolean z, HashMap<String, String> map) throws Exception {
        int i;
        try {
            i = Integer.parseInt(str2);
        } catch (Exception unused) {
            i = 1;
        }
        JSONObject jSONObject = new JSONObject();
        jSONObject.put("recSwitch", true);
        jSONObject.put("kingKongSwitch", false);
        jSONObject.put("storePageId", 10002);
        jSONObject.put("audioBook", 1);
        jSONObject.put("theaterSubscriptSwitch", true);
        jSONObject.put("needPlayLink", 1);
        jSONObject.put("isOldAgeMode", false);
        jSONObject.put("resolutionRate", "720P");
        jSONObject.put("preview", 1);
        if (str == null) {
            str = "drama_all";
        }
        boolean zStartsWith = str.startsWith("rank_");
        if (zStartsWith) {
            jSONObject.put("channelGroupId", "14");
            int channelId;
            if ("rank_rise".equals(str)) {
                channelId = 69;
            } else if ("rank_new".equals(str)) {
                channelId = 70;
            } else if ("rank_search".equals(str)) {
                channelId = 71;
            } else {
                channelId = 68;
            }
            jSONObject.put("channelId", channelId);
            String channelName;
            if ("rank_rise".equals(str)) {
                channelName = "飙升榜";
            } else if ("rank_new".equals(str)) {
                channelName = "新剧榜";
            } else if ("rank_search".equals(str)) {
                channelName = "热搜榜";
            } else {
                channelName = "热播榜";
            }
            jSONObject.put("channelName", channelName);
        } else if ("manju_all".equals(str)) {
            jSONObject.put("channelGroupId", "78");
        } else {
            jSONObject.put("channelGroupId", "157");
            String channelName = "";
            String channelIdStr = map == null ? "" : map.get("channelId");
            if (TextUtils.isEmpty(channelIdStr)) {
                channelIdStr = str.startsWith("drama_") ? str.substring(6) : "";
            }
            if (!TextUtils.isEmpty(channelIdStr) && !"all".equals(channelIdStr)) {
                int channelId = 0;
                try {
                    channelId = Integer.parseInt(channelIdStr);
                } catch (Exception unused2) {
                }
                jSONObject.put("channelId", channelId);
                if ("959".equals(channelIdStr)) {
                    channelName = "精选";
                } else if ("964".equals(channelIdStr)) {
                    channelName = "穿越";
                } else if ("961".equals(channelIdStr)) {
                    channelName = "重生";
                } else if ("960".equals(channelIdStr)) {
                    channelName = "古装";
                } else if ("963".equals(channelIdStr)) {
                    channelName = "言情";
                } else if ("965".equals(channelIdStr)) {
                    channelName = "强者回归";
                } else if ("967".equals(channelIdStr)) {
                    channelName = "萌宝";
                } else if ("962".equals(channelIdStr)) {
                    channelName = "伦理";
                } else if ("969".equals(channelIdStr)) {
                    channelName = "超能";
                } else if ("966".equals(channelIdStr)) {
                    channelName = "后悔流";
                } else if ("968".equals(channelIdStr)) {
                    channelName = "修仙";
                } else if ("971".equals(channelIdStr)) {
                    channelName = "年代";
                } else if ("972".equals(channelIdStr)) {
                    channelName = "悬疑";
                } else if ("970".equals(channelIdStr)) {
                    channelName = "奇幻";
                }
                jSONObject.put("channelName", channelName);
            }
        }
        String strM141a = m141a("1125", jSONObject);
        ArrayList<Vod> arrayList = new ArrayList<>();
        m143c(m153o(strM141a), arrayList);
        return Result.get().vod(arrayList).page(i, Integer.MAX_VALUE, Math.max(arrayList.size(), 1), Integer.MAX_VALUE).string();
    }

    public String detailContent(List<String> list) throws Exception {
        String str = list.get(0);
        JSONObject jSONObject = new JSONObject();
        jSONObject.put("bookId", str);
        jSONObject.put("needNextChapter", 0);
        jSONObject.put("isNeedAlias", "");
        jSONObject.put("bookAlias", "");
        jSONObject.put("playSourceL1", "剧场");
        jSONObject.put("playSourceL2", "剧场-热播");
        jSONObject.put("playSourceL3", "热播-精选");
        jSONObject.put("resolutionRate", "720P");
        jSONObject.put("preview", 1);
        jSONObject.put("commentType", 2);
        jSONObject.put("excludeInfo", 0);
        jSONObject.put("screenClearType", 3);
        jSONObject.put("bottomStyle", 2);
        jSONObject.put("showOriginNovel", 0);
        jSONObject.put("interacPop", 0);
        Object objM153o = m153o(m141a("1131", jSONObject));
        JSONObject jSONObjectM146h = m146h(objM153o, "bookId", str);
        if (jSONObjectM146h == null) {
            jSONObjectM146h = m145g(objM153o, "bookName", "bookId", "chapterList", "chapters");
        }
        if (jSONObjectM146h == null && (objM153o instanceof JSONObject)) {
            jSONObjectM146h = (JSONObject) objM153o;
        }
        Vod vod = new Vod();
        vod.setVodId(str);
        vod.setVodName(m152n(jSONObjectM146h, "bookName", "name", "title", "bookAlias"));
        vod.setVodPic(m150l(jSONObjectM146h, "coverWap", "cover", "coverUrl", "image", "pic"));
        vod.setVodRemarks(m152n(jSONObjectM146h, "chapterInfo", "serialCount", "latestChapterName", "status", "remark"));
        vod.setVodContent(m152n(jSONObjectM146h, "intro", "introduction", "summary", "desc", "description"));
        vod.setVodActor("");
        vod.setVodDirector("");
        vod.setVodPlayFrom("盒马短剧");
        ArrayList<String> arrayList = new ArrayList<>();
        m142b(str, objM153o, arrayList, new HashMap<>());
        if (arrayList.size() <= 1 && jSONObjectM146h != null) {
            int updateNum = jSONObjectM146h.optInt("updateNum", 0);
            int chapterIndex = jSONObjectM146h.optInt("chapterIndex", 1);
            String chapterId = jSONObjectM146h.optString("chapterId");
            if (updateNum > 1 && !TextUtils.isEmpty(chapterId)) {
                try {
                    long j = Long.parseLong(chapterId) - ((long) Math.max(chapterIndex - 1, 0));
                    arrayList.clear();
                    for (int i = 1; i <= updateNum; i++) {
                        String chId = String.valueOf((((long) i) + j) - 1);
                        StringBuilder sb = new StringBuilder();
                        sb.append("第");
                        sb.append(i);
                        sb.append("集$");
                        sb.append(str);
                        sb.append("|||");
                        sb.append(chId);
                        sb.append("|||");
                        sb.append(chId);
                        arrayList.add(sb.toString());
                    }
                } catch (Exception unused) {
                }
            }
        }
        vod.setVodPlayUrl(TextUtils.join("#", arrayList));
        return Result.string(vod);
    }

    public String homeContent(boolean z) {
        ArrayList<Class> arrayList = new ArrayList<>();
        arrayList.add(new Class("drama_all", "短剧"));
        arrayList.add(new Class("manju_all", "漫剧"));
        arrayList.add(new Class("rank_hot", "热播榜"));
        arrayList.add(new Class("rank_rise", "飙升榜"));
        arrayList.add(new Class("rank_new", "新剧榜"));
        arrayList.add(new Class("rank_search", "热搜榜"));
        LinkedHashMap<String, List<Filter>> linkedHashMap = new LinkedHashMap<>();
        ArrayList<Filter> arrayList2 = new ArrayList<>();
        ArrayList<Filter.Value> arrayList3 = new ArrayList<>();
        arrayList3.add(new Filter.Value("全部", ""));
        m154p(arrayList3, "精选", "959");
        m154p(arrayList3, "穿越", "964");
        m154p(arrayList3, "重生", "961");
        m154p(arrayList3, "古装", "960");
        m154p(arrayList3, "言情", "963");
        m154p(arrayList3, "强者回归", "965");
        m154p(arrayList3, "萌宝", "967");
        m154p(arrayList3, "伦理", "962");
        m154p(arrayList3, "超能", "969");
        m154p(arrayList3, "后悔流", "966");
        m154p(arrayList3, "修仙", "968");
        m154p(arrayList3, "年代", "971");
        m154p(arrayList3, "悬疑", "972");
        m154p(arrayList3, "奇幻", "970");
        arrayList2.add(new Filter("channelId", "分类", arrayList3));
        linkedHashMap.put("drama_all", arrayList2);
        linkedHashMap.put("manju_all", new ArrayList<>());
        linkedHashMap.put("rank_hot", new ArrayList<>());
        linkedHashMap.put("rank_rise", new ArrayList<>());
        linkedHashMap.put("rank_new", new ArrayList<>());
        linkedHashMap.put("rank_search", new ArrayList<>());
        return Result.string(arrayList, linkedHashMap);
    }

    public String homeVideoContent() throws Exception {
        return categoryContent("drama_all", "1", false, new HashMap<>());
    }

    @Override
    public void init(Context context, String str) throws Exception {
        super.init(context, str);
        this.f126i = "https://www.tangsan.fun/hema.php";
        if (TextUtils.isEmpty(str)) {
            return;
        }
        JSONObject jSONObject = new JSONObject(str);
        this.f118a = jSONObject.optString("version", this.f118a);
        this.f119b = jSONObject.optString("pname", this.f119b);
        this.f120c = jSONObject.optString("channelCode", this.f120c);
        this.f121d = jSONObject.optString("utdidTmp", this.f121d);
        this.f122e = jSONObject.optString("utdid", this.f122e);
        this.f123f = jSONObject.optString("userId", this.f123f);
        this.f124g = jSONObject.optLong("installTime", this.f124g);
        this.f125h = jSONObject.optString("boxId", this.f125h);
    }

    public String playerContent(String str, String str2, List<String> list) throws Exception {
        String errorMsg;
        String[] strArrSplit = str2.split("\\|\\|\\|");
        String str3 = strArrSplit.length > 0 ? strArrSplit[0] : "";
        String str4 = strArrSplit.length > 1 ? strArrSplit[1] : "";
        String str5 = strArrSplit.length > 2 ? strArrSplit[2] : str4;
        if (TextUtils.isEmpty(str3) || TextUtils.isEmpty(str4)) {
            errorMsg = "bad play id";
        } else {
            JSONObject jSONObject = new JSONObject();
            jSONObject.put("bookId", str3);
            jSONObject.put("chapterIds", new JSONArray().put(str4));
            jSONObject.put("unClockType", "load");
            jSONObject.put("chapterId", str5);
            jSONObject.put("omap", new JSONObject());
            jSONObject.put("tierPlaySource", new JSONObject().put("firstTierPlaySource", "剧场").put("secondTierPlaySource", "剧场-热播").put("thirdTierPlaySource", "热播-精选"));
            jSONObject.put("resolutionRate", "720P");
            jSONObject.put("preview", 1);
            jSONObject.put("playPercentage", 0);
            jSONObject.put("continuousAd", 0);
            jSONObject.put("commentType", 2);
            Object objM153o = m153o(m141a("1139", jSONObject));
            String playUrl = m144f(m146h(objM153o, "chapterId", str4));
            if (TextUtils.isEmpty(playUrl)) {
                JSONObject jSONObjectM145g = m145g(objM153o, "mp4Url", "mp4SwitchUrl");
                String playUrl2 = m144f(jSONObjectM145g);
                playUrl = !TextUtils.isEmpty(playUrl2) ? playUrl2 : m151m(m152n(jSONObjectM145g, "mp4Url", "playUrl", "videoUrl", "url"));
            }
            if (TextUtils.isEmpty(playUrl)) {
                playUrl = m147i(objM153o);
            }
            if (TextUtils.isEmpty(playUrl)) {
                playUrl = m148j(objM153o, "mp4Url", "playUrl", "videoUrl", "url", "chapterUrl");
            }
            if (!TextUtils.isEmpty(playUrl)) {
                HashMap<String, String> map = new HashMap<>();
                map.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/117.0.0.0 Safari/537.36");
                map.put("Referer", "https://freevideo.zqqds.cn/");
                return Result.get().url(playUrl).parse(0).header(map).string();
            }
            errorMsg = "play url not found";
        }
        return Result.error(errorMsg);
    }

    public String searchContent(String str, boolean z) throws Exception {
        return searchContent(str, z, "1");
    }

    public String searchContent(String str, boolean z, String str2) throws Exception {
        int i;
        try {
            i = Integer.parseInt(str2);
        } catch (Exception unused) {
            i = 1;
        }
        JSONObject jSONObject = new JSONObject();
        jSONObject.put("keyword", str);
        jSONObject.put("guessReserveSwitch", 0);
        jSONObject.put("reservationSwitch", true);
        jSONObject.put("guessNewStyleSwitch", 1);
        jSONObject.put("guessStyle", 0);
        String strM141a = m141a("1802", jSONObject);
        ArrayList<Vod> arrayList = new ArrayList<>();
        m143c(m153o(strM141a), arrayList);
        return Result.get().vod(arrayList).page(i, Integer.MAX_VALUE, Math.max(arrayList.size(), 1), Integer.MAX_VALUE).string();
    }
}
