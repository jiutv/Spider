@Override
public String playerContent(String flag, String id, List<String> vipFlags) {
    String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
    String sign = md5(KEY + timestamp);

    String vodName = "";
    String vodUrl = "";
    String vodIndex = "";

    String[] parts = id.split("\\|");
    if (parts.length >= 1) vodName = parts[0];
    if (parts.length >= 2) {
        String[] urlParts = parts[1].split("@");
        vodUrl = urlParts[0];
        if (urlParts.length >= 2) vodIndex = urlParts[1];
    }
    String proxyUrl;
    try {
        proxyUrl = Proxy.getUrl() + "?do=danmu&vodName=" + vodName
                + "&vodUrl=" + vodUrl
                + "&vodIndex=" + vodIndex
                + "&sign=" + sign
                + "&timestamp=" + timestamp;
    } catch (Exception e) {
        // Proxy获取失败时，降级返回原始直链
        return Result.get().url(vodUrl).parse(0).string();
    }
    return Result.get().url(proxyUrl).parse(0).string();
}
