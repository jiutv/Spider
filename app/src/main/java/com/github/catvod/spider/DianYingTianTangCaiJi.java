import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import okhttp3.Request;
import okhttp3.Response;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class DianYingTianTangCaiJi extends AbsSource {

    private static final String API_BASE = "http://caiji.dyttzyapi.com/api.php/provide/vod/from/dyttm3u8/at/json/";

    // ========== 内部硬编码分类：短剧升级成一级大类 ==========
    private JSONArray buildInnerCategories() {
        JSONArray categoriesArray = new JSONArray();

        // 电影片
        JSONObject movieObj = new JSONObject();
        movieObj.put("name", "电影片");
        JSONArray movieSub = new JSONArray();
        movieSub.add("动作片");
        movieSub.add("喜剧片");
        movieSub.add("科幻片");
        movieSub.add("恐怖片");
        movieSub.add("爱情片");
        movieSub.add("剧情片");
        movieSub.add("战争片");
        movieSub.add("记录片");
        movieSub.add("动画片");
        movieSub.add("伦理片");
        movieObj.put("sub", movieSub);
        categoriesArray.add(movieObj);

        // 连续剧
        JSONObject seriesObj = new JSONObject();
        seriesObj.put("name", "连续剧");
        JSONArray seriesSub = new JSONArray();
        seriesSub.add("国产剧");
        seriesSub.add("香港剧");
        seriesSub.add("韩国剧");
        seriesSub.add("欧美剧");
        seriesSub.add("台湾剧");
        seriesSub.add("日本剧");
        seriesSub.add("海外剧");
        seriesSub.add("泰国剧");
        seriesObj.put("sub", seriesSub);
        categoriesArray.add(seriesObj);

        // 【新增一级大类：短剧】
        JSONObject shortDramaObj = new JSONObject();
        shortDramaObj.put("name", "短剧");
        JSONArray shortDramaSub = new JSONArray();
        shortDramaSub.add("短剧");
        shortDramaObj.put("sub", shortDramaSub);
        categoriesArray.add(shortDramaObj);

        // 动漫片
        JSONObject animeObj = new JSONObject();
        animeObj.put("name", "动漫片");
        JSONArray animeSub = new JSONArray();
        animeSub.add("国产动漫");
        animeSub.add("日韩动漫");
        animeSub.add("欧美动漫");
        animeSub.add("港台动漫");
        animeSub.add("动画片");
        animeObj.put("sub", animeSub);
        categoriesArray.add(animeObj);

        // 综艺片
        JSONObject varietyObj = new JSONObject();
        varietyObj.put("name", "综艺片");
        JSONArray varietySub = new JSONArray();
        varietySub.add("大陆综艺");
        varietySub.add("港台综艺");
        varietySub.add("日韩综艺");
        varietySub.add("欧美综艺");
        varietyObj.put("sub", varietySub);
        categoriesArray.add(varietyObj);

        return categoriesArray;
    }

    // 摊平嵌套分类，原版TVBox使用
    private List<String> flatCategories(JSONArray nestedCategories) {
        List<String> flatList = new ArrayList<>();
        if (nestedCategories == null) return flatList;
        for (Object obj : nestedCategories) {
            JSONObject cate = (JSONObject) obj;
            JSONArray subArr = cate.getJSONArray("sub");
            if (subArr != null) {
                for (Object sub : subArr) {
                    flatList.add((String) sub);
                }
            }
        }
        return flatList;
    }

    @Override
    public JSONObject homeContent(boolean filter) throws IOException {
        return homeContent(filter, "1");
    }

    // 首页支持翻页
    @Override
    public JSONObject homeContent(boolean filter, String pg) throws IOException {
        JSONObject result = new JSONObject();
        String url = API_BASE + "?ac=list&pg=" + pg;
        String resp = getHttp(url);
        JSONObject apiJson;
        try {
            apiJson = JSON.parseObject(resp);
        } catch (Exception e) {
            result.put("list", new JSONArray());
            return result;
        }

        JSONArray nestedCate = buildInnerCategories();
        List<String> flatCateList = flatCategories(nestedCate);

        result.put("categories", flatCateList);
        result.put("originCategories", nestedCate); // 给魔改UI下拉菜单

        result.put("list", apiJson.getJSONArray("list"));
        result.put("page", apiJson.getInteger("page"));
        result.put("pagecount", apiJson.getInteger("pagecount"));
        result.put("total", apiJson.getInteger("total"));
        return result;
    }

    // 分类栏目翻页
    @Override
    public JSONObject categoryContent(String tid, String pg, boolean filter, JSONObject extend) throws IOException {
        String url = API_BASE + "?ac=list&pg=" + pg + "&t=" + tid;
        String resp = getHttp(url);
        return JSON.parseObject(resp);
    }

    // 详情接口
    @Override
    public JSONObject detailContent(List<String> ids) throws IOException {
        String url = API_BASE + "?ac=detail&ids=" + ids.get(0);
        String resp = getHttp(url);
        return JSON.parseObject(resp);
    }

    // 搜索（带翻页）
    @Override
    public JSONObject searchContent(String key, String pg, boolean filter) throws IOException {
        String url = API_BASE + "?ac=list&pg=" + pg + "&wd=" + key;
        String resp = getHttp(url);
        return JSON.parseObject(resp);
    }

    // 播放解析
    @Override
    public JSONObject playerContent(String flag, String id, String vipFlags) throws IOException {
        String url = API_BASE + "?ac=detail&ids=" + id;
        String resp = getHttp(url);
        JSONObject data = JSON.parseObject(resp);
        JSONArray list = data.getJSONArray("list");
        if (list == null || list.isEmpty()) return new JSONObject();
        JSONObject vod = list.getJSONObject(0);
        JSONObject res = new JSONObject();
        res.put("url", vod.getString("vod_play_url"));
        return res;
    }

    // HTTP请求工具
    private String getHttp(String url) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android;TVBox)")
                .get()
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) return "";
            return response.body().string();
        }
    }
}
