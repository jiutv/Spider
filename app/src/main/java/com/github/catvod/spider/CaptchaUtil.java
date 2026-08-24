package com.github.catvod.spider;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;

import com.googlecode.tesseract.android.TessBaseAPI;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 本地 Tesseract OCR 验证码识别
 * 完全免费，无需联网
 */
class CaptchaUtil {

    private static boolean tessInitialized = false;

    static void initTessData(Context context) {
        if (tessInitialized) return;
        try {
            File tessDir = new File(context.getFilesDir(), "tessdata");
            if (!tessDir.exists()) tessDir.mkdirs();

            File trainedData = new File(tessDir, "eng.traineddata");
            if (!trainedData.exists()) {
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

    static String recognize(byte[] imgBytes, Context context) {
        if (imgBytes == null || imgBytes.length == 0 || context == null) {
            System.out.println("=== OCR: imgBytes is null or empty");
            return "";
        }

        initTessData(context);

        TessBaseAPI tess = new TessBaseAPI();
        try {
            String dataPath = context.getFilesDir().getAbsolutePath();
            if (!tess.init(dataPath, "eng")) {
                System.out.println("=== OCR: TessBaseAPI init failed");
                return "";
            }

            // 只识别数字
            tess.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, "0123456789");
            // 单行文本模式
            tess.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SINGLE_LINE);

            Bitmap bitmap = preprocess(imgBytes);
            if (bitmap == null) {
                System.out.println("=== OCR: preprocess failed");
                return "";
            }

            tess.setImage(bitmap);
            String result = tess.getUTF8Text();
            tess.end();

            result = result.replaceAll("[^0-9]", "").trim();

            System.out.println("=== OCR result: [" + result + "]");

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

    private static Bitmap preprocess(byte[] imgBytes) {
        try {
            Bitmap src = BitmapFactory.decodeByteArray(imgBytes, 0, imgBytes.length);
            if (src == null) return null;

            int width = src.getWidth();
            int height = src.getHeight();
            System.out.println("=== OCR: image size " + width + "x" + height);

            // 放大 3 倍
            float scale = 3.0f;
            Matrix matrix = new Matrix();
            matrix.postScale(scale, scale);
            Bitmap scaled = Bitmap.createBitmap(src, 0, 0, width, height, matrix, true);

            int newWidth = scaled.getWidth();
            int newHeight = scaled.getHeight();

            // RGB_565 减少内存
            Bitmap out = Bitmap.createBitmap(newWidth, newHeight, Bitmap.Config.RGB_565);
            Canvas canvas = new Canvas(out);
            Paint paint = new Paint();

            // 灰度 + 高对比度二值化
            ColorMatrix cm = new ColorMatrix();
            cm.setSaturation(0); // 灰度

            ColorMatrix contrast = new ColorMatrix(new float[] {
                3.0f, 0, 0, 0, -280,
                0, 3.0f, 0, 0, -280,
                0, 0, 3.0f, 0, -280,
                0, 0, 0, 1, 0
            });
            cm.postConcat(contrast);

            paint.setColorFilter(new ColorMatrixColorFilter(cm));
            canvas.drawBitmap(scaled, 0, 0, paint);

            return out;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
