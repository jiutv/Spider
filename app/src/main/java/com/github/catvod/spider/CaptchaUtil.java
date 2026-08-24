package com.github.catvod.spider;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;

/**
 * 纯 Java 实现的轻量级 OCR 验证码识别
 * 不依赖 Tesseract、不依赖 so 库、不依赖网络
 * 适用于白底黑字的 4 位数字验证码
 */
class CaptchaUtil {

    static void initTessData(Context context) {
        // 纯 Java OCR 不需要初始化
    }

    static String recognize(byte[] imgBytes, Context context) {
        if (imgBytes == null || imgBytes.length == 0) {
            System.out.println("=== OCR: imgBytes is null or empty");
            return "";
        }

        try {
            Bitmap src = BitmapFactory.decodeByteArray(imgBytes, 0, imgBytes.length);
            if (src == null) {
                System.out.println("=== OCR: decode image failed");
                return "";
            }

            System.out.println("=== OCR: image size " + src.getWidth() + "x" + src.getHeight());

            // 预处理
            Bitmap processed = preprocess(src);
            if (processed == null) {
                System.out.println("=== OCR: preprocess failed");
                return "";
            }

            // 识别
            String result = recognizeDigits(processed);
            System.out.println("=== OCR result: [" + result + "]");

            return result;

        } catch (Exception e) {
            System.out.println("=== OCR exception: " + e.getMessage());
            e.printStackTrace();
            return "";
        }
    }

    /**
     * 图片预处理：灰度化 + 对比度增强 + 二值化 + 放大
     */
    private static Bitmap preprocess(Bitmap src) {
        int width = src.getWidth();
        int height = src.getHeight();

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
    }

    /**
     * 识别 4 位数字验证码
     */
    private static String recognizeDigits(Bitmap img) {
        int w = img.getWidth();
        int h = img.getHeight();

        // 将 Bitmap 转为二维数组（0=黑，255=白）
        int[][] pixels = new int[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int pixel = img.getPixel(x, y);
                int gray = (pixel >> 16) & 0xFF; // 取红色通道（已经是灰度图）
                pixels[y][x] = gray < 128 ? 0 : 255;
            }
        }

        // 分割成 4 个字符
        int charWidth = w / 4;
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < 4; i++) {
            int left = i * charWidth;
            int right = (i + 1) * charWidth;
            String digit = recognizeSingleDigit(pixels, left, right, h);
            result.append(digit);
        }

        return result.toString();
    }

    /**
     * 识别单个数字
     */
    private static String recognizeSingleDigit(int[][] pixels, int left, int right, int h) {
        int charW = right - left;

        // 提取特征：7x5 网格，每个网格的黑色像素比例
        int gridH = h / 7;
        int gridW = charW / 5;
        double[] features = new double[35];

        for (int gy = 0; gy < 7; gy++) {
            for (int gx = 0; gx < 5; gx++) {
                int y1 = gy * gridH;
                int y2 = (gy + 1) * gridH;
                int x1 = left + gx * gridW;
                int x2 = left + (gx + 1) * gridW;

                int blackCount = 0;
                int total = 0;
                for (int y = y1; y < y2 && y < h; y++) {
                    for (int x = x1; x < x2 && x < pixels[0].length; x++) {
                        if (pixels[y][x] == 0) blackCount++;
                        total++;
                    }
                }
                features[gy * 5 + gx] = total > 0 ? (double) blackCount / total : 0;
            }
        }

        // 数字模板（0-9）
        double[][] templates = {
            // 0
            {0,1,1,1,0, 1,0,0,0,1, 1,0,0,0,1, 1,0,0,0,1, 1,0,0,0,1, 1,0,0,0,1, 0,1,1,1,0},
            // 1
            {0,0,1,0,0, 0,1,1,0,0, 0,0,1,0,0, 0,0,1,0,0, 0,0,1,0,0, 0,0,1,0,0, 0,1,1,1,0},
            // 2
            {0,1,1,1,0, 1,0,0,0,1, 0,0,0,0,1, 0,0,0,1,0, 0,0,1,0,0, 0,1,0,0,0, 1,1,1,1,1},
            // 3
            {0,1,1,1,0, 1,0,0,0,1, 0,0,0,0,1, 0,0,1,1,0, 0,0,0,0,1, 1,0,0,0,1, 0,1,1,1,0},
            // 4
            {0,0,0,1,0, 0,0,1,1,0, 0,1,0,1,0, 1,0,0,1,0, 1,1,1,1,1, 0,0,0,1,0, 0,0,0,1,0},
            // 5
            {1,1,1,1,1, 1,0,0,0,0, 1,1,1,1,0, 0,0,0,0,1, 0,0,0,0,1, 1,0,0,0,1, 0,1,1,1,0},
            // 6
            {0,1,1,1,0, 1,0,0,0,1, 1,0,0,0,0, 1,1,1,1,0, 1,0,0,0,1, 1,0,0,0,1, 0,1,1,1,0},
            // 7
            {1,1,1,1,1, 0,0,0,0,1, 0,0,0,1,0, 0,0,1,0,0, 0,0,1,0,0, 0,0,1,0,0, 0,0,1,0,0},
            // 8
            {0,1,1,1,0, 1,0,0,0,1, 1,0,0,0,1, 0,1,1,1,0, 1,0,0,0,1, 1,0,0,0,1, 0,1,1,1,0},
            // 9
            {0,1,1,1,0, 1,0,0,0,1, 1,0,0,0,1, 0,1,1,1,1, 0,0,0,0,1, 1,0,0,0,1, 0,1,1,1,0},
        };

        String[] digits = {"0", "1", "2", "3", "4", "5", "6", "7", "8", "9"};

        // 计算余弦相似度
        String bestMatch = "0";
        double bestScore = -1;

        for (int i = 0; i < templates.length; i++) {
            double score = cosineSimilarity(features, templates[i]);
            if (score > bestScore) {
                bestScore = score;
                bestMatch = digits[i];
            }
        }

        return bestMatch;
    }

    /**
     * 计算余弦相似度
     */
    private static double cosineSimilarity(double[] a, double[] b) {
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB) + 1e-8);
    }
}
