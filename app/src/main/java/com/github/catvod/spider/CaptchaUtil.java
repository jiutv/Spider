package com.github.catvod.spider;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;

import com.rmtheis.tess.two.TessBaseAPI;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 本地 Tesseract OCR 验证码识别
 * 完全免费，无需联网
 *
 * 使用步骤：
 * 1. build.gradle 添加：implementation 'com.rmtheis:tess-two:9.1.0'
 * 2. 下载 eng.traineddata（约14MB）：
 *    https://github.com/tesseract-ocr/tessdata_fast/raw/main/eng.traineddata
 * 3. 放入：app/src/main/assets/tessdata/eng.traineddata
 * 4. 编译运行，首次启动自动复制训练文件
 */
class CaptchaUtil {

    private static boolean tessInitialized = false;

    /**
     * 初始化：将 assets 中的训练数据复制到可读写目录
     * 在 FengYe.init() 中调用一次即可
     */
    static void initTessData(Context context) {
        if (tessInitialized) return;
        try {
            File tessDir = new File(context.getFilesDir(), "tessdata");
            if (!tessDir.exists()) tessDir.mkdirs();

            File trainedData = new File(tessDir, "eng.traineddata");
            if (!trainedData.exists()) {
                // 从 assets 复制
                copyAsset(context, "tessdata/eng.traineddata", trainedData);
            }
            tessInitialized = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void copyAsset(Context context, String assetPath, File dest) throws IOException {
        try (InputStream in = context.getAssets().open(assetPath);
             FileOutputStream out = new FileOutputStream(dest)) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();
        }
    }

    /**
     * 识别验证码
     * @param imgBytes 验证码图片（PNG/JPG）
     * @param context  Android Context
     * @return 识别结果（4位数字），失败返回空字符串
     */
    static String recognize(byte[] imgBytes, Context context) {
        if (imgBytes == null || imgBytes.length == 0 || context == null) return "";

        // 确保训练数据已复制
        initTessData(context);

        TessBaseAPI tess = new TessBaseAPI();
        try {
            // 初始化 Tesseract，datapath 指向 files 目录（包含 tessdata 子目录）
            String dataPath = context.getFilesDir().getAbsolutePath();
            if (!tess.init(dataPath, "eng")) {
                return "";
            }

            // 只识别数字（大大提高准确率）
            tess.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, "0123456789");

            // 图片预处理
            Bitmap bitmap = preprocess(imgBytes);
            if (bitmap == null) return "";

            tess.setImage(bitmap);
            String result = tess.getUTF8Text();
            tess.end();

            // 清理结果：只保留数字
            result = result.replaceAll("[^0-9]", "").trim();
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        } finally {
            try {
                tess.end();
            } catch (Exception ignored) {}
        }
    }

    /**
     * 图片预处理：灰度化 + 二值化 + 放大（提高识别率）
     */
    private static Bitmap preprocess(byte[] imgBytes) {
        try {
            Bitmap src = BitmapFactory.decodeByteArray(imgBytes, 0, imgBytes.length);
            if (src == null) return null;

            int width = src.getWidth();
            int height = src.getHeight();

            // 放大 2 倍（小验证码放大后识别率更高）
            float scale = 2.0f;
            Matrix matrix = new Matrix();
            matrix.postScale(scale, scale);
            Bitmap scaled = Bitmap.createBitmap(src, 0, 0, width, height, matrix, true);

            int newWidth = scaled.getWidth();
            int newHeight = scaled.getHeight();
            Bitmap out = Bitmap.createBitmap(newWidth, newHeight, Bitmap.Config.ARGB_8888);

            for (int x = 0; x < newWidth; x++) {
                for (int y = 0; y < newHeight; y++) {
                    int pixel = scaled.getPixel(x, y);
                    // 灰度化
                    int gray = (int) (Color.red(pixel) * 0.299
                                    + Color.green(pixel) * 0.587
                                    + Color.blue(pixel) * 0.114);
                    // 二值化（阈值 160，验证码通常是深色文字浅色背景）
                    int binary = gray < 160 ? 0 : 255;
                    out.setPixel(x, y, Color.rgb(binary, binary, binary));
                }
            }
            return out;
        } catch (Exception e) {
            return null;
        }
    }
}
