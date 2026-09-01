import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HuHang {
    private static final String BASE_URL = "https://www.qdhuhang.com";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final int TIMEOUT = 15000;

    // -------------------- 列表解析 --------------------
    public List<VideoInfo> parseList(String listUrl) {
        List<VideoInfo> list = new ArrayList<>();
        try {
            Document doc = Jsoup.connect(BASE_URL + listUrl)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT)
                    .get();

            Elements items = doc.select("li.fed-list-item");
            for (Element item : items) {
                Element picLink = item.selectFirst("a.fed-list-pics");
                if (picLink == null) continue;

                String detailUrl = picLink.attr("abs:href");
                String imageUrl = picLink.attr("data-original");
                if (imageUrl.startsWith("//")) imageUrl = "https:" + imageUrl;

                Element remarkElem = item.selectFirst("span.fed-list-remarks");
                String remark = remarkElem != null ? remarkElem.text() : "";

                Element titleLink = item.selectFirst("a.fed-list-title");
                String title = titleLink != null ? titleLink.text() : "";

                Element descElem = item.selectFirst("span.fed-list-desc");
                String actors = descElem != null ? descElem.text() : "";

                VideoInfo video = new VideoInfo(title, detailUrl, imageUrl, remark, actors);
                list.add(video);
            }
        } catch (IOException e) {
            System.err.println("列表页解析失败：" + e.getMessage());
        }
        return list;
    }

    // -------------------- 播放地址提取 --------------------
    public String parsePlayUrl(String detailUrl) {
        try {
            Document detailDoc = Jsoup.connect(detailUrl)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT)
                    .get();

            Element ogVideo = detailDoc.selectFirst("meta[property=og:video]");
            if (ogVideo == null) {
                Element playBtn = detailDoc.selectFirst("a.fed-deta-play");
                if (playBtn == null) return null;
                String playPageUrl = playBtn.attr("abs:href");
                return extractVideoFromPlayPage(playPageUrl);
            }

            String playPageUrl = ogVideo.attr("content");
            if (playPageUrl == null || playPageUrl.isEmpty()) return null;
            return extractVideoFromPlayPage(playPageUrl);

        } catch (IOException e) {
            System.err.println("详情页请求失败：" + e.getMessage());
            return null;
        }
    }

    private String extractVideoFromPlayPage(String playPageUrl) {
        try {
            Document playDoc = Jsoup.connect(playPageUrl)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT)
                    .referrer(BASE_URL)
                    .get();

            String html = playDoc.html();

            // 提取 var now = "视频地址";
            Pattern pattern = Pattern.compile("var\\s+now\\s*=\\s*[\"']([^\"']+)[\"']");
            Matcher matcher = pattern.matcher(html);
            if (matcher.find()) {
                return matcher.group(1);
            }

            // 备用匹配其他变量
            pattern = Pattern.compile("(?:video_url|playUrl|url)\\s*[:=]\\s*[\"']([^\"']+?\\.(?:m3u8|mp4)[^\"']*)[\"']");
            matcher = pattern.matcher(html);
            if (matcher.find()) {
                String url = matcher.group(1);
                if (!url.startsWith("http")) {
                    url = playPageUrl.substring(0, playPageUrl.lastIndexOf('/') + 1) + url;
                }
                return url;
            }

            // 备用 iframe
            Element iframe = playDoc.selectFirst("iframe[src]");
            if (iframe != null) {
                String iframeSrc = iframe.attr("abs:src");
                if (iframeSrc != null && !iframeSrc.isEmpty()) {
                    return extractVideoFromPlayPage(iframeSrc);
                }
            }

            return null;
        } catch (IOException e) {
            System.err.println("播放页请求失败：" + e.getMessage());
            return null;
        }
    }

    // -------------------- 筛选功能 --------------------
    public List<VideoInfo> filterVideos(Integer tid, String area, String year, String order, Integer page) {
        StringBuilder url = new StringBuilder("/search.php?searchtype=5");
        if (tid != null) url.append("&tid=").append(tid);
        if (area != null && !area.isEmpty()) {
            try {
                url.append("&area=").append(URLEncoder.encode(area, "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                url.append("&area=").append(area);
            }
        }
        if (year != null && !year.isEmpty()) url.append("&year=").append(year);
        if (order != null && !order.isEmpty()) url.append("&order=").append(order);
        if (page != null && page > 1) url.append("&page=").append(page);

        System.out.println("筛选URL: " + BASE_URL + url);
        return parseList(url.toString());
    }

    public List<VideoInfo> searchVideos(String keyword, Integer page) {
        try {
            String encoded = URLEncoder.encode(keyword, "UTF-8");
            StringBuilder url = new StringBuilder("/search.php?searchword=" + encoded);
            if (page != null && page > 1) url.append("&page=").append(page);
            return parseList(url.toString());
        } catch (UnsupportedEncodingException e) {
            System.err.println("关键词编码失败：" + e.getMessage());
            return new ArrayList<>();
        }
    }

    public List<VideoInfo> getCategoryPage(String categoryPath, Integer page) {
        String url = categoryPath;
        if (page != null && page > 1) {
            if (url.contains("?")) {
                url += "&page=" + page;
            } else {
                url += "?page=" + page;
            }
        }
        return parseList(url);
    }

    // -------------------- 测试入口 --------------------
    public static void main(String[] args) {
        HuHang crawler = new HuHang();  // 实例化类名已改

        // 示例：筛选 2026年大陆电影
        System.out.println("===== 筛选：2026年大陆电影 =====");
        List<VideoInfo> filtered = crawler.filterVideos(1, "大陆", "2026", "time", 1);
        for (VideoInfo v : filtered) {
            System.out.println(v.getTitle() + " - " + v.getActors());
        }

        // 示例：搜索 "校歌"
        System.out.println("\n===== 搜索：校歌 =====");
        List<VideoInfo> searched = crawler.searchVideos("校歌", 1);
        for (VideoInfo v : searched) {
            System.out.println(v.getTitle());
        }

        // 示例：获取第一个视频的播放地址
        if (!filtered.isEmpty()) {
            String playUrl = crawler.parsePlayUrl(filtered.get(0).getDetailUrl());
            System.out.println("\n第一个视频播放地址：" + playUrl);
        }
    }
}
