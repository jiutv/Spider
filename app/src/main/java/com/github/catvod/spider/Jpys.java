package com.github.catvod.spider;

import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Y2sSpider - TVBox爬虫 (针对 y2s52n7.com)
 * 站点: Next.js SSR架构影视站
 * 生成时间: 2026-08-16
 *
 * 使用方法:
 * 1. 将此文件放入 CatVodTVSpider 项目的 com.github.catvod.spider 包下
 * 2. 编译生成 custom_spider.jar
 * 3. TVBox配置中 api 填 csp_jpys
 */
public class jpys extends Spider {

    private static final String SITE_URL = "https://y2s52n7.com";
    private static final String SEARCH_URL = SITE_URL + "/vod/search/{wd}";
    private static final String CATE_URL = SITE_URL + "/vod/show/id/{cateId}/page/{catePg}";
    private static final String UA = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/116.0.0.0 Mobile Safari/537.36";
    private static final int TIMEOUT = 15000;
    // API签名密钥和设备ID
    private static final String SIGN_KEY = "cb808529bae6b6be45ecfab29a4889bc";
    private static final String DEVICE_ID = java.util.UUID.randomUUID().toString();

    // ==================== 首页内容 ====================
    @Override
    public String homeContent(boolean filter) {
        try {
            JSONObject result = new JSONObject();
            JSONArray classes = new JSONArray();
            classes.put(new JSONObject().put("type_id", "1").put("type_name", "电影"));
            classes.put(new JSONObject().put("type_id", "2").put("type_name", "电视剧"));
            classes.put(new JSONObject().put("type_id", "3").put("type_name", "综艺"));
            classes.put(new JSONObject().put("type_id", "4").put("type_name", "动漫"));
            classes.put(new JSONObject().put("type_id", "88").put("type_name", "短剧"));
            result.put("class", classes);

            if (filter) {
                JSONObject filters = new JSONObject();
                for (int i = 0; i < classes.length(); i++) {
                    String typeId = classes.getJSONObject(i).getString("type_id");
                    filters.put(typeId, buildFilters());
                }
                result.put("filters", filters);
            }

            // 抓取首页
            String html = fetch(SITE_URL);
            JSONArray list = parseVideoList(html);
            result.put("list", list);
            return result.toString();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    // ==================== 分类内容 ====================
    @Override
    public String categoryContent(String tid, String pg, boolean filter, Map<String, String> extend) {
        try {
            // 构建URL，支持筛选
            StringBuilder urlBuilder = new StringBuilder(SITE_URL);
            urlBuilder.append("/vod/show/id/").append(tid);

            // 添加筛选条件
            if (extend != null) {
                if (extend.containsKey("class") && !extend.get("class").isEmpty())
                    urlBuilder.append("/class/").append(URLEncoder.encode(extend.get("class"), "UTF-8"));
                if (extend.containsKey("area") && !extend.get("area").isEmpty())
                    urlBuilder.append("/area/").append(URLEncoder.encode(extend.get("area"), "UTF-8"));
                if (extend.containsKey("year") && !extend.get("year").isEmpty())
                    urlBuilder.append("/year/").append(extend.get("year"));
                if (extend.containsKey("lang") && !extend.get("lang").isEmpty())
                    urlBuilder.append("/lang/").append(URLEncoder.encode(extend.get("lang"), "UTF-8"));
            }

            int page = pg.isEmpty() ? 1 : Integer.parseInt(pg);
            urlBuilder.append("/page/").append(page);

            String html = fetch(urlBuilder.toString());
            JSONArray list = parseVideoList(html);

            JSONObject result = new JSONObject();
            result.put("page", page);
            result.put("pagecount", page + 1);
            result.put("limit", 24);
            result.put("total", (page + 1) * 24);
            result.put("list", list);
            return result.toString();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    // ==================== 搜索内容 ====================
    @Override
    public String searchContent(String key, boolean quick) {
        try {
            // 使用网站API搜索（需签名验证）
            // API: /api/mw-movie/anonymous/video/searchByWord
            // 签名: SHA1(MD5(paramStr + "&key=" + SIGN_KEY + "&t=" + timestamp))
            JSONArray list = searchByApi(key, 1);

            JSONObject result = new JSONObject();
            result.put("list", list);
            return result.toString();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) {
        try {
            int page = pg.isEmpty() ? 1 : Integer.parseInt(pg);
            JSONArray list = searchByApi(key, page);

            JSONObject result = new JSONObject();
            result.put("list", list);
            return result.toString();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    /**
     * 通过API搜索影片
     * 签名算法: SHA1(MD5(sortedParams + "&key=" + signKey + "&t=" + timestamp))
     */
    private JSONArray searchByApi(String keyword, int page) {
        JSONArray list = new JSONArray();
        try {
            String apiUrl = SITE_URL + "/api/mw-movie/anonymous/video/searchByWord";

            // 构建参数（空值不参与签名）
            Map<String, String> params = new HashMap<>();
            params.put("keyword", keyword);
            params.put("pageNum", String.valueOf(page));
            params.put("pageSize", "12");

            // 生成签名
            long timestamp = System.currentTimeMillis();
            List<String> sortedKeys = new ArrayList<>(params.keySet());
            java.util.Collections.sort(sortedKeys);
            StringBuilder paramStr = new StringBuilder();
            for (String k : sortedKeys) {
                if (paramStr.length() > 0) paramStr.append("&");
                paramStr.append(k).append("=").append(params.get(k));
            }
            String signStr = paramStr.toString() + "&key=" + SIGN_KEY + "&t=" + timestamp;

            // SHA1(MD5(signStr))
            String md5 = md5Hex(signStr);
            String sign = sha1Hex(md5);

            // 发送请求
            String url = apiUrl + "?keyword=" + URLEncoder.encode(keyword, "UTF-8")
                    + "&pageNum=" + page + "&pageSize=12";
            String json = fetchWithHeaders(url, sign, String.valueOf(timestamp), DEVICE_ID);

            JSONObject resp = new JSONObject(json);
            if (resp.optInt("code") == 200) {
                JSONObject data = resp.optJSONObject("data");
                if (data != null) {
                    JSONObject resultObj = data.optJSONObject("result");
                    if (resultObj == null) resultObj = data.optJSONObject("typeResult");
                    if (resultObj != null) {
                        JSONArray items = resultObj.optJSONArray("list");
                        if (items != null) {
                            for (int i = 0; i < items.length(); i++) {
                                JSONObject item = items.getJSONObject(i);
                                JSONObject vod = new JSONObject();
                                vod.put("vod_id", "/detail/" + item.optInt("vodId"));
                                vod.put("vod_name", item.optString("vodName", ""));
                                vod.put("vod_pic", item.optString("vodPic", ""));
                                vod.put("vod_remarks", item.optString("vodVersion", ""));
                                list.put(vod);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return list;
    }

    // ==================== 详情内容 ====================
    @Override
    public String detailContent(List<String> ids) {
        try {
            String id = ids.get(0);
            String url = id.startsWith("http") ? id : SITE_URL + id;
            String html = fetch(url);

            // 优先从Next.js SSR数据中提取（playListData字段）
            JSONObject vodData = extractVodFromSSR(html);
            JSONObject vod = new JSONObject();
            vod.put("vod_id", id);

            if (vodData != null) {
                vod.put("vod_name", vodData.optString("vodName", ""));
                vod.put("vod_pic", vodData.optString("vodPic", ""));
                // vodContent含HTML标签，清理一下
                String content = vodData.optString("vodContent", "");
                content = content.replaceAll("<[^>]+>", "").trim();
                vod.put("vod_content", content);
                vod.put("vod_year", vodData.optString("vodYear", ""));
                vod.put("vod_area", vodData.optString("vodArea", ""));
                vod.put("vod_actor", vodData.optString("vodActor", "未知"));
                vod.put("vod_director", vodData.optString("vodDirector", "未知"));
                vod.put("vod_remarks", vodData.optString("vodVersion", ""));
                String score = vodData.optString("vodScore", "");
                if (!score.isEmpty() && !score.equals("0")) {
                    vod.put("vod_score", score);
                }

                // 提取播放列表
                JSONArray episodeList = vodData.optJSONArray("episodeList");
                if (episodeList != null && episodeList.length() > 0) {
                    StringBuilder playUrl = new StringBuilder();
                    String vodId = vodData.optString("vodId", "");
                    if (vodId.isEmpty()) {
                        // 从id参数提取数字
                        Pattern idPat = Pattern.compile("(\\d+)");
                        Matcher idMat = idPat.matcher(id);
                        if (idMat.find()) vodId = idMat.group(1);
                    }
                    for (int i = 0; i < episodeList.length(); i++) {
                        JSONObject ep = episodeList.getJSONObject(i);
                        String epName = ep.optString("name", "第" + (i + 1) + "集");
                        String nid = ep.optString("nid", "");
                        String epUrl = "/vod/play/" + vodId + "/sid/" + nid;
                        if (playUrl.length() > 0) playUrl.append("#");
                        playUrl.append(epName).append("$").append(epUrl);
                    }
                    vod.put("vod_play_from", vodData.optString("typeName", "默认源"));
                    vod.put("vod_play_url", playUrl.toString());
                }
            } else {
                // 降级: 从HTML解析
                Document doc = Jsoup.parse(html);
                doc.setBaseUri(SITE_URL);
                Element titleEl = doc.selectFirst("h1.title");
                vod.put("vod_name", titleEl != null ? titleEl.text() : "");

                // 从卡片提取图片
                Element imgEl = doc.selectFirst(".detail__CardImg img, .detail img");
                if (imgEl != null) {
                    vod.put("vod_pic", imgEl.attr("src"));
                }

                // 播放链接
                Elements playLinks = doc.select("a[href*=/vod/play/]");
                StringBuilder playUrl = new StringBuilder();
                for (Element ep : playLinks) {
                    String epName = ep.text().trim();
                    if (epName.isEmpty()) epName = "播放";
                    String epUrl = ep.attr("href");
                    if (playUrl.length() > 0) playUrl.append("#");
                    playUrl.append(epName).append("$").append(epUrl);
                }
                vod.put("vod_play_from", "默认源");
                vod.put("vod_play_url", playUrl.toString());
            }

            JSONArray list = new JSONArray();
            list.put(vod);
            JSONObject result = new JSONObject();
            result.put("list", list);
            return result.toString();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    // ==================== 播放内容 ====================
    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        try {
            JSONObject result = new JSONObject();

            // 从播放ID中提取vodId和nid
            // id格式: /vod/play/146173/sid/1307714
            int vodId = 0, nid = 0;
            Matcher m = Pattern.compile("/vod/play/(\\d+)/sid/(\\d+)").matcher(id);
            if (m.find()) {
                vodId = Integer.parseInt(m.group(1));
                nid = Integer.parseInt(m.group(2));
            } else {
                // 降级: 如果URL格式不对，用网页解析
                String url = id.startsWith("http") ? id : SITE_URL + id;
                result.put("parse", 1);
                result.put("playUrl", "");
                result.put("url", url);
                JSONObject headers = new JSONObject();
                headers.put("User-Agent", UA);
                headers.put("Referer", SITE_URL);
                result.put("header", headers.toString());
                return result.toString();
            }

            // 调用播放地址API获取m3u8
            // API返回: 蓝光(需登录), 高清(需登录), 标清(免费)
            String playUrl = getPlayUrlByApi(vodId, nid);

            if (playUrl != null && !playUrl.isEmpty()) {
                // 获取到直链m3u8，直接播放（不需要解析器）
                result.put("parse", 0);
                result.put("playUrl", "");
                result.put("url", playUrl);
                JSONObject headers = new JSONObject();
                headers.put("User-Agent", UA);
                headers.put("Referer", SITE_URL);
                result.put("header", headers.toString());
            } else {
                // API获取失败，降级用网页解析
                String url = id.startsWith("http") ? id : SITE_URL + id;
                result.put("parse", 1);
                result.put("playUrl", "");
                result.put("url", url);
                JSONObject headers = new JSONObject();
                headers.put("User-Agent", UA);
                headers.put("Referer", SITE_URL);
                result.put("header", headers.toString());
            }
            return result.toString();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    /**
     * 通过API获取播放地址
     * 优先返回蓝光(1080p) m3u8地址
     * 注意: 虽然API返回needLogin=true，但蓝光URL实际可直接播放（CDN不验证token）
     * CDN有频率限制，短时间内频繁请求会被封IP，但TVBox播放时只请求一次无影响
     */
    private String getPlayUrlByApi(int vodId, int nid) {
        try {
            String apiUrl = SITE_URL + "/api/mw-movie/anonymous/v2/video/episode/url";

            // 构建参数
            Map<String, String> params = new HashMap<>();
            params.put("clientType", "3");
            params.put("id", String.valueOf(vodId));
            params.put("nid", String.valueOf(nid));

            // 生成签名
            long timestamp = System.currentTimeMillis();
            List<String> sortedKeys = new ArrayList<>(params.keySet());
            java.util.Collections.sort(sortedKeys);
            StringBuilder paramStr = new StringBuilder();
            for (String k : sortedKeys) {
                if (paramStr.length() > 0) paramStr.append("&");
                paramStr.append(k).append("=").append(params.get(k));
            }
            String signStr = paramStr.toString() + "&key=" + SIGN_KEY + "&t=" + timestamp;
            String sign = sha1Hex(md5Hex(signStr));

            // 发送请求
            String url = apiUrl + "?clientType=3&id=" + vodId + "&nid=" + nid;
            String json = fetchWithHeaders(url, sign, String.valueOf(timestamp), DEVICE_ID);

            JSONObject resp = new JSONObject(json);
            if (resp.optInt("code") == 200) {
                JSONObject data = resp.optJSONObject("data");
                if (data != null) {
                    JSONArray list = data.optJSONArray("list");
                    if (list != null && list.length() > 0) {
                        // 优先蓝光(1080p)，其次高清(720p)，最后标清(480p)
                        // API按清晰度从高到低返回，直接取第一个
                        JSONObject best = list.getJSONObject(0);
                        String playUrl = best.optString("url", "");
                        if (!playUrl.isEmpty()) {
                            return playUrl;
                        }
                        // 降级: 遍历找一个有URL的
                        for (int i = 0; i < list.length(); i++) {
                            String u = list.getJSONObject(i).optString("url", "");
                            if (!u.isEmpty()) return u;
                        }
                    }
                }
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return null;
    }

    // ==================== 搜索详情（快速搜索） ====================
    @Override
    public String searchContent(String key, boolean quick, String pg) {
        return searchContent(key, quick);
    }

    // ==================== 工具方法 ====================

    /**
     * 从Next.js SSR数据中提取影片信息
     * Next.js将数据嵌入在 self.__next_f.push([1,"..."]) 调用中
     * 影片数据在 playListData.data 字段中
     */
    private JSONObject extractVodFromSSR(String html) {
        try {
            // 找到包含vodName的__next_f数据块（正则需匹配转义引号\"）
            Pattern pattern = Pattern.compile("self\\.__next_f\\.push\\(\\[1,\"((?:[^\"\\\\]|\\\\.)*)\"\\]\\)");
            Matcher matcher = pattern.matcher(html);
            while (matcher.find()) {
                String data = matcher.group(1);
                if (!data.contains("vodName")) continue;

                // 解码Unicode转义
                data = decodeUnicode(data);
                // 反转义引号
                data = data.replace("\\\"", "\"").replace("\\\\", "\\");

                // 找到playListData字段（包含当前影片数据）
                int plIdx = data.indexOf("playListData");
                if (plIdx < 0) continue;

                String afterPl = data.substring(plIdx);

                // 找到playListData后的data对象中的vodId
                int vodIdIdx = afterPl.indexOf("\"vodId\"");
                if (vodIdIdx < 0) continue;

                // 找到vodId所在的JSON对象起点
                int objStart = afterPl.lastIndexOf("{", vodIdIdx);
                if (objStart < 0) continue;

                // 找到匹配的结束大括号
                int braceCount = 0;
                int objEnd = objStart;
                for (int i = objStart; i < afterPl.length(); i++) {
                    char c = afterPl.charAt(i);
                    if (c == '{') braceCount++;
                    else if (c == '}') {
                        braceCount--;
                        if (braceCount == 0) {
                            objEnd = i + 1;
                            break;
                        }
                    }
                }

                String jsonStr = afterPl.substring(objStart, objEnd);
                try {
                    JSONObject vod = new JSONObject(jsonStr);
                    // 验证是否包含必要字段
                    if (vod.has("vodName")) {
                        return vod;
                    }
                } catch (Exception e) {
                    // JSON解析失败，尝试手动提取
                    return parseVodDataManually(afterPl.substring(0, Math.min(afterPl.length(), 3000)));
                }
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return null;
    }

    /**
     * 手动解析影片数据（降级方案）
     */
    private JSONObject parseVodDataManually(String data) {
        try {
            JSONObject vod = new JSONObject();
            vod.put("vodName", extractJsonField(data, "vodName"));
            vod.put("vodPic", extractJsonField(data, "vodPic"));
            vod.put("vodContent", extractJsonField(data, "vodContent"));
            vod.put("vodYear", extractJsonField(data, "vodYear"));
            vod.put("vodArea", extractJsonField(data, "vodArea"));
            vod.put("vodActor", extractJsonField(data, "vodActor"));
            vod.put("vodDirector", extractJsonField(data, "vodDirector"));
            vod.put("vodVersion", extractJsonField(data, "vodVersion"));
            vod.put("vodScore", extractJsonField(data, "vodScore"));
            vod.put("vodId", extractJsonField(data, "vodId"));
            vod.put("typeName", extractJsonField(data, "typeName"));

            // 解析episodeList
            JSONArray episodes = new JSONArray();
            Pattern epPattern = Pattern.compile("\"nid\"\\s*:\\s*(\\d+).*?\"name\"\\s*:\\s*\"([^\"]*)\"");
            Matcher epMatcher = epPattern.matcher(data);
            while (epMatcher.find()) {
                JSONObject ep = new JSONObject();
                ep.put("nid", epMatcher.group(1));
                ep.put("name", epMatcher.group(2));
                episodes.put(ep);
            }
            vod.put("episodeList", episodes);
            return vod;
        } catch (Exception e) {
            return null;
        }
    }

    private String extractJsonField(String data, String field) {
        Pattern p = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher m = p.matcher(data);
        if (m.find()) return m.group(1);
        // 尝试数字
        p = Pattern.compile("\"" + field + "\"\\s*:\\s*(\\d+)");
        m = p.matcher(data);
        if (m.find()) return m.group(1);
        return "";
    }

    /**
     * 解析视频列表（通用方法）
     * 从HTML中提取 /detail/xxx 链接的卡片
     */
    private JSONArray parseVideoList(String html) {
        JSONArray list = new JSONArray();
        try {
            Document doc = Jsoup.parse(html);
            doc.setBaseUri(SITE_URL);

            // 优先尝试从Next.js SSR数据提取（更准确）
            JSONArray ssrList = parseVideoListFromSSR(html);
            if (ssrList.length() > 0) return ssrList;

            // 降级: 从DOM解析
            // 网站使用 content-card 类的a标签包裹视频卡片
            Elements cards = doc.select("a.content-card[href*=/detail/]");
            if (cards.isEmpty()) {
                // 尝试其他选择器
                cards = doc.select("a[href*=/detail/]");
            }

            // 去重
            Map<String, JSONObject> seen = new HashMap<>();
            for (Element card : cards) {
                String href = card.attr("href");
                if (seen.containsKey(href)) continue;

                JSONObject vod = new JSONObject();
                vod.put("vod_id", href);

                // 标题: 从img的alt属性获取
                Element img = card.selectFirst("img[alt]");
                if (img != null) {
                    vod.put("vod_name", img.attr("alt"));
                    String pic = img.attr("src");
                    if (pic.isEmpty()) pic = img.attr("data-src");
                    vod.put("vod_pic", fixUrl(pic));
                } else {
                    // 从文本获取标题
                    Element titleEl = card.selectFirst(".flex1, .type1, .title");
                    if (titleEl != null) {
                        vod.put("vod_name", titleEl.text());
                    } else {
                        String text = card.text().trim();
                        // 清理文本（去除评分/类型等附加信息）
                        vod.put("vod_name", cleanTitle(text));
                    }
                }

                // 备注
                Element scoreEl = card.selectFirst(".score");
                if (scoreEl != null) {
                    vod.put("vod_remarks", scoreEl.text());
                }

                seen.put(href, vod);
                list.put(vod);
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return list;
    }

    /**
     * 从SSR数据中解析视频列表
     * Next.js的__next_f数据中视频列表项含vodId/vodName/vodPic
     */
    private JSONArray parseVideoListFromSSR(String html) {
        JSONArray list = new JSONArray();
        try {
            Pattern pattern = Pattern.compile("self\\.__next_f\\.push\\(\\[1,\"((?:[^\"\\\\]|\\\\.)*)\"\\]\\)");
            Matcher matcher = pattern.matcher(html);
            while (matcher.find()) {
                String data = matcher.group(1);
                if (!data.contains("vodName")) continue;

                // 解码
                data = decodeUnicode(data);
                data = data.replace("\\\"", "\"").replace("\\\\", "\\");

                // 提取所有vodId+vodName+vodPic组合
                // 格式: "vodId":123,"typeId":...,"vodName":"名称",...,"vodPic":"url"
                Pattern videoPattern = Pattern.compile(
                    "\"vodId\"\\s*:\\s*(\\d+)[^}]*?\"vodName\"\\s*:\\s*\"([^\"]*)\"[^}]*?\"vodPic\"\\s*:\\s*\"([^\"]*)\""
                );
                Matcher videoMatcher = videoPattern.matcher(data);
                java.util.Set<String> seen = new java.util.HashSet<>();
                while (videoMatcher.find()) {
                    String vodId = videoMatcher.group(1);
                    if (seen.contains(vodId)) continue;
                    seen.add(vodId);

                    String vodName = videoMatcher.group(2);
                    String vodPic = videoMatcher.group(3);

                    JSONObject vod = new JSONObject();
                    vod.put("vod_id", "/detail/" + vodId);
                    vod.put("vod_name", vodName);
                    vod.put("vod_pic", vodPic);

                    // 尝试提取评分和版本
                    String chunk = data.substring(videoMatcher.start(), Math.min(data.length(), videoMatcher.end() + 200));
                    Pattern scoreP = Pattern.compile("\"vodScore\"\\s*:\\s*([\\d.]+)");
                    Matcher scoreM = scoreP.matcher(chunk);
                    String remarks = "";
                    if (scoreM.find() && !scoreM.group(1).equals("0")) {
                        remarks = scoreM.group(1) + "分";
                    }
                    Pattern verP = Pattern.compile("\"vodVersion\"\\s*:\\s*\"([^\"]*)\"");
                    Matcher verM = verP.matcher(chunk);
                    if (verM.find() && !verM.group(1).isEmpty()) {
                        if (remarks.isEmpty()) {
                            remarks = verM.group(1);
                        }
                    }
                    if (!remarks.isEmpty()) {
                        vod.put("vod_remarks", remarks);
                    }

                    list.put(vod);
                }
                if (list.length() > 0) break;
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return list;
    }

    /**
     * 构建筛选条件
     */
    private JSONArray buildFilters() {
        JSONArray filters = new JSONArray();

        // 类型筛选
        JSONObject typeFilter = new JSONObject();
        typeFilter.put("key", "class");
        typeFilter.put("name", "类型");
        JSONArray typeValues = new JSONArray();
        typeValues.put(new JSONObject().put("n", "全部").put("v", ""));
        String[] types = {"喜剧", "动作", "爱情", "科幻", "悬疑", "奇幻", "恐怖", "剧情", "犯罪", "动画", "惊悚", "战争", "冒险", "灾难", "伦理", "其他"};
        for (String t : types) typeValues.put(new JSONObject().put("n", t).put("v", t));
        typeFilter.put("value", typeValues);
        filters.put(typeFilter);

        // 地区筛选
        JSONObject areaFilter = new JSONObject();
        areaFilter.put("key", "area");
        areaFilter.put("name", "地区");
        JSONArray areaValues = new JSONArray();
        areaValues.put(new JSONObject().put("n", "全部").put("v", ""));
        String[] areas = {"中国大陆", "中国香港", "中国台湾", "美国", "日本", "韩国", "印度", "泰国", "英国", "法国", "其他"};
        for (String a : areas) areaValues.put(new JSONObject().put("n", a).put("v", a));
        areaFilter.put("value", areaValues);
        filters.put(areaFilter);

        // 年份筛选
        JSONObject yearFilter = new JSONObject();
        yearFilter.put("key", "year");
        yearFilter.put("name", "年份");
        JSONArray yearValues = new JSONArray();
        yearValues.put(new JSONObject().put("n", "全部").put("v", ""));
        String[] years = {"2026", "2025", "2024", "2023", "2022", "2021", "2020", "2019", "2018", "2017", "2016", "2015", "2014", "2013", "2012", "2011", "2010", "2009~2000"};
        for (String y : years) yearValues.put(new JSONObject().put("n", y).put("v", y));
        yearFilter.put("value", yearValues);
        filters.put(yearFilter);

        // 语言筛选
        JSONObject langFilter = new JSONObject();
        langFilter.put("key", "lang");
        langFilter.put("name", "语言");
        JSONArray langValues = new JSONArray();
        langValues.put(new JSONObject().put("n", "全部").put("v", ""));
        String[] langs = {"国语", "英语", "粤语", "韩语", "日语", "其他"};
        for (String l : langs) langValues.put(new JSONObject().put("n", l).put("v", l));
        langFilter.put("value", langValues);
        filters.put(langFilter);

        return filters;
    }

    // ==================== 基础工具方法 ====================

    private String fetch(String url) {
        try {
            return Jsoup.connect(url)
                    .userAgent(UA)
                    .timeout(TIMEOUT)
                    .header("Referer", SITE_URL)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "zh-CN,zh;q=0.9")
                    .ignoreContentType(true)
                    .execute()
                    .body();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    /**
     * 带签名头的API请求
     */
    private String fetchWithHeaders(String url, String sign, String timestamp, String deviceId) {
        try {
            return Jsoup.connect(url)
                    .userAgent(UA)
                    .timeout(TIMEOUT)
                    .header("Referer", SITE_URL)
                    .header("Accept", "application/json, text/plain, */*")
                    .header("client-type", "3")
                    .header("sign", sign)
                    .header("t", timestamp)
                    .header("deviceId", deviceId)
                    .header("authorization", "")
                    .ignoreContentType(true)
                    .execute()
                    .body();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    /**
     * MD5哈希
     */
    private String md5Hex(String input) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * SHA1哈希
     */
    private String sha1Hex(String input) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(input.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private String fixUrl(String url) {
        if (url == null || url.isEmpty()) return "";
        if (url.startsWith("http")) return url;
        if (url.startsWith("//")) return "https:" + url;
        if (url.startsWith("/")) return SITE_URL + url;
        return SITE_URL + "/" + url;
    }

    private String cleanTitle(String text) {
        if (text == null || text.isEmpty()) return "未知";
        // 去除开头的"蓝光"/"枪版"等版本标记
        text = text.replaceAll("^(蓝光|枪版|高清|正片|预告)\\s*", "");
        // 去除评分数字
        text = text.replaceAll("^\\d+\\.\\d+", "");
        // 截取到上映时间之前
        int idx = text.indexOf("上映时间");
        if (idx > 0) text = text.substring(0, idx);
        // 截取到类型词之前
        String[] typeWords = {"剧情", "喜剧", "动作", "爱情", "科幻", "悬疑", "奇幻", "恐怖", "犯罪", "动画", "惊悚", "战争", "冒险", "灾难", "伦理", "纪录片"};
        for (String tw : typeWords) {
            int ti = text.indexOf(tw);
            if (ti > 0 && ti < text.length() / 2) {
                text = text.substring(0, ti);
                break;
            }
        }
        return text.trim();
    }

    private String decodeUnicode(String str) {
        Pattern pattern = Pattern.compile("\\\\u([0-9a-fA-F]{4})");
        Matcher matcher = pattern.matcher(str);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            char c = (char) Integer.parseInt(matcher.group(1), 16);
            matcher.appendReplacement(sb, String.valueOf(c));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    @Override
    public boolean isVideoFormat(String url) {
        return url.contains(".m3u8") || url.contains(".mp4") || url.contains(".flv");
    }

    @Override
    public boolean manualVideoCheck() {
        return false;
    }
}

