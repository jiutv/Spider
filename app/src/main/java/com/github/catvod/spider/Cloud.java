package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;
import com.github.catvod.api.Pan123Api;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.utils.Json;
import com.github.catvod.utils.Util;
import com.google.gson.JsonObject;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.github.catvod.api.TianyiApi.URL_CONTAIN;

/**
 * @author ColaMint & Adam & FongMi
 */
public class Cloud extends Spider {
    private Quark quark = null;
    /*   private Ali ali = null;*/
    private UC uc = null;
    private TianYi tianYi = null;
    private YiDongYun yiDongYun = null;
    private BaiDuPan baiDuPan = null;
    private Pan123 pan123 = null;

    @Override
    public void init(Context context, String extend) throws Exception {
        JsonObject ext = Json.safeObject(extend);
        quark = new Quark();
        uc = new UC();
        /*  ali = new Ali();*/
        tianYi = new TianYi();
        yiDongYun = new YiDongYun();
        baiDuPan = new BaiDuPan();
        pan123 = new Pan123();
        boolean first = Objects.nonNull(ext);
        quark.init(context, first && ext.has("cookie") ? ext.get("cookie").getAsString() : "");
        uc.init(context, first && ext.has("uccookie") ? ext.get("uccookie").getAsString() : "");
        /*   ali.init(context, first && ext.has("token") ? ext.get("token").getAsString() : "");*/
        tianYi.init(context, first && ext.has("tianyicookie") ? ext.get("tianyicookie").getAsString() : "");
        yiDongYun.init(context, "");
        baiDuPan.init(context, "");
        pan123.init(context, "");

    }

    @Override
    public String detailContent(List<String> shareUrl) throws Exception {
        SpiderDebug.log("cloud detailContent shareUrl：" + Json.toJson(shareUrl));

       /* if (shareUrl.get(0).matches(Util.patternAli)) {
            return ali.detailContent(shareUrl);
        } else */
        if (shareUrl.get(0).matches(Util.patternQuark)) {
            return quark.detailContent(shareUrl);
        } else if (shareUrl.get(0).matches(Util.patternUC)) {
            return uc.detailContent(shareUrl);
        } else if (shareUrl.get(0).contains(URL_CONTAIN)) {
            return tianYi.detailContent(shareUrl);
        } else if (shareUrl.get(0).contains(YiDongYun.URL_START)) {
            return yiDongYun.detailContent(shareUrl);
        } else if (shareUrl.get(0).contains(BaiDuPan.URL_START)) {
            return baiDuPan.detailContent(shareUrl);
        } else if (shareUrl.get(0).matches(Pan123Api.regex)) {
            SpiderDebug.log("Pan123Api shareUrl：" + Json.toJson(shareUrl));
            return pan123.detailContent(shareUrl);
        }
        return null;
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        SpiderDebug.log("cloud playerContent flag：" + flag + " id：" + id);

        if (flag.contains("quark")) {
            return quark.playerContent(flag, id, vipFlags);
        } else if (flag.contains("uc")) {
            return uc.playerContent(flag, id, vipFlags);
        } else if (flag.contains("天意")) {
            return tianYi.playerContent(flag, id, vipFlags);
        } else if (flag.contains("移动")) {
            return yiDongYun.playerContent(flag, id, vipFlags);
        }/* else {
            return ali.playerContent(flag, id, vipFlags);
        }*/ else if (flag.contains("BD")) {
            return baiDuPan.playerContent(flag, id, vipFlags);
        } else if (flag.contains("pan123")) {
            return pan123.playerContent(flag, id, vipFlags);
        }
        return flag;
    }

    protected String detailContentVodPlayFrom(List<String> shareLinks) {

        List<String> from = new ArrayList<>();
        int i = 0;
        for (String shareLink : shareLinks) {
            i++;
            if (shareLink.matches(Util.patternUC)) {
                from.add(uc.detailContentVodPlayFrom(List.of(shareLink), i));
            } else if (shareLink.matches(Util.patternQuark)) {
                from.add(quark.detailContentVodPlayFrom(List.of(shareLink), i));
            } /*else if (shareLink.matches(Util.patternAli)) {
                from.add(ali.detailContentVodPlayFrom(List.of(shareLink), i));
            } */ else if (shareLink.contains(URL_CONTAIN)) {
                from.add(tianYi.detailContentVodPlayFrom(List.of(shareLink), i));
            } else if (shareLink.contains(YiDongYun.URL_START)) {
                from.add(yiDongYun.detailContentVodPlayFrom(List.of(shareLink), i));
            } else if (shareLink.contains(BaiDuPan.URL_START)) {
                from.add(baiDuPan.detailContentVodPlayFrom(List.of(shareLink), i));
            } else if (shareLink.matches(Pan123Api.regex)) {
                from.add(pan123.detailContentVodPlayFrom(List.of(shareLink), i));
            }
        }

        return TextUtils.join("$$$", from);
    }

    protected String detailContentVodPlayUrl(List<String> shareLinks) {

        List<String> urls = new CopyOnWriteArrayList<>();
        ExecutorService service = Executors.newFixedThreadPool(4);
        List<CompletableFuture<String>> futures = new ArrayList<>();
        for (String shareLink : shareLinks) {
            futures.add(CompletableFuture.supplyAsync(() -> {

                String url = "";
                if (shareLink.matches(Util.patternUC)) {
                    url = uc.detailContentVodPlayUrl(List.of(shareLink));
                } else if (shareLink.matches(Util.patternQuark)) {
                    url = quark.detailContentVodPlayUrl(List.of(shareLink));
                }/* else if (shareLink.matches(Util.patternAli)) {
                urls.add(ali.detailContentVodPlayUrl(List.of(shareLink)));
            } */ else if (shareLink.contains(URL_CONTAIN)) {
                    url = tianYi.detailContentVodPlayUrl(List.of(shareLink));
                } else if (shareLink.contains(YiDongYun.URL_START)) {
                    url = yiDongYun.detailContentVodPlayUrl(List.of(shareLink));
                } else if (shareLink.contains(BaiDuPan.URL_START)) {
                    url = baiDuPan.detailContentVodPlayUrl(List.of(shareLink));
                } else if (shareLink.matches(Pan123Api.regex)) {
                    url = pan123.detailContentVodPlayUrl(List.of(shareLink));
                }
                return url;
            }, service));

        }
        try {
            for (CompletableFuture<String> future : futures) {
                urls.add(future.get());
            }
        } catch (Exception e) {
            SpiderDebug.log("获取异步结果出错：" + e);
        }

        SpiderDebug.log("---urls：" + Json.toJson(urls));
        service.shutdown();
        return StringUtils.join(urls, "$$$");
    }
}
