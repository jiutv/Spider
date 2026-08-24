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
 * 支持 PNG/JPG/WEBP/GIF 等多种格式
 */
class CaptchaUtil {

    static void initTessData(Context context) {}

    static String recognize(byte[] imgBytes, Context context) {
        if (imgBytes == null || imgBytes.length == 0) {
            System.out.println("=== OCR: imgBytes is null or empty");
            return "";
        }

        System.out.println("=== OCR: imgBytes length=" + imgBytes.length);

        // 打印文件头，判断格式
        if (imgBytes.length > 4) {
            StringBuilder hex = new StringBuilder("=== OCR: header=");
            for (int i = 0; i < Math.min(16, imgBytes.length); i++) {
                hex.append(String.format("%02X ", imgBytes[i] & 0xFF));
            }
            System.out.println(hex.toString());

            // 判断文件类型
            String type = detectType(imgBytes);
            System.out.println("=== OCR: detected type=" + type);
        }

        try {
            // 尝试解码
            Bitmap src = BitmapFactory.decodeByteArray(imgBytes, 0, imgBytes.length);
            if (src == null) {
                System.out.println("=== OCR: decode image failed, trying WebP...");
                // 有些设备不支持 WebP，尝试转换
                src = decodeWebP(imgBytes);
            }
            if (src == null) {
                System.out.println("=== OCR: all decode methods failed");
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
     * 检测文件类型
     */
    private static String detectType(byte[] data) {
        if (data.length < 4) return "unknown";
        // PNG: 89 50 4E 47
        if (data[0] == (byte)0x89 && data[1] == (byte)0x50 && data[2] == (byte)0x4E && data[3] == (byte)0x47)
            return "PNG";
        // JPG: FF D8 FF
        if (data[0] == (byte)0xFF && data[1] == (byte)0xD8 && data[2] == (byte)0xFF)
            return "JPG";
        // GIF: 47 49 46 38
        if (data[0] == (byte)0x47 && data[1] == (byte)0x49 && data[2] == (byte)0x46 && data[3] == (byte)0x38)
            return "GIF";
        // WEBP: 52 49 46 46 ... 57 45 42 50
        if (data[0] == (byte)0x52 && data[1] == (byte)0x49 && data[2] == (byte)0x46 && data[3] == (byte)0x46)
            return "WEBP";
        // BMP: 42 4D
        if (data[0] == (byte)0x42 && data[1] == (byte)0x4D)
            return "BMP";
        return "unknown";
    }

    /**
     * 尝试解码 WebP（某些 Android 版本不支持）
     */
    private static Bitmap decodeWebP(byte[] data) {
        try {
            // 如果系统支持 WebP，decodeByteArray 应该已经成功了
            // 这里返回 null 表示不支持
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private static Bitmap preprocess(Bitmap src) {
        int width = src.getWidth();
        int height = src.getHeight();

        float scale = 3.0f;
        Matrix matrix = new Matrix();
        matrix.postScale(scale, scale);
        Bitmap scaled = Bitmap.createBitmap(src, 0, 0, width, height, matrix, true);

        int newWidth = scaled.getWidth();
        int newHeight = scaled.getHeight();

        Bitmap out = Bitmap.createBitmap(newWidth, newHeight, Bitmap.Config.RGB_565);
        Canvas canvas = new Canvas(out);
        Paint paint = new Paint();

        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0);

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

    private static String recognizeDigits(Bitmap img) {
        int w = img.getWidth();
        int h = img.getHeight();

        int[][] pixels = new int[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int pixel = img.getPixel(x, y);
                int gray = (pixel >> 16) & 0xFF;
                pixels[y][x] = gray < 128 ? 0 : 255;
            }
        }

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

    private static String recognizeSingleDigit(int[][] pixels, int left, int right, int h) {
        int charW = right - left;
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

        double[][] templates = {
            {0,1,1,1,0, 1,0,0,0,1, 1,0,0,0,1, 1,0,0,0,1, 1,0,0,0,1, 1,0,0,0,1, 0,1,1,1,0},
            {0,0,1,0,0, 0,1,1,0,0, 0,0,1,0,0, 0,0,1,0,0, 0,0,1,0,0, 0,0,1,0,0, 0,1,1,1,0},
            {0,1,1,1,0, 1,0,0,0,1, 0,0,0,0,1, 0,0,0,1,0, 0,0,1,0,0, 0,1,0,0,0, 1,1,1,1,1},
            {0,1,1,1,0, 1,0,0,0,1, 0,0,0,0,1, 0,0,1,1,0, 0,0,0,0,1, 1,0,0,0,1, 0,1,1,1,0},
            {0,0,0,1,0, 0,0,1,1,0, 0,1,0,1,0, 1,0,0,1,0, 1,1,1,1,1, 0,0,0,1,0, 0,0,0,1,0},
            {1,1,1,1,1, 1,0,0,0,0, 1,1,1,1,0, 0,0,0,0,1, 0,0,0,0,1, 1,0,0,0,1, 0,1,1,1,0},
            {0,1,1,1,0, 1,0,0,0,1, 1,0,0,0,0, 1,1,1,1,0, 1,0,0,0,1, 1,0,0,0,1, 0,1,1,1,0},
            {1,1,1,1,1, 0,0,0,0,1, 0,0,0,1,0, 0,0,1,0,0, 0,0,1,0,0, 0,0,1,0,0, 0,0,1,0,0},
            {0,1,1,1,0, 1,0,0,0,1, 1,0,0,0,1, 0,1,1,1,0, 1,0,0,0,1, 1,0,0,0,1, 0,1,1,1,0},
            {0,1,1,1,0, 1,0,0,0,1, 1,0,0,0,1, 0,1,1,1,1, 0,0,0,0,1, 1,0,0,0,1, 0,1,1,1,0},
        };

        String[] digits = {"0", "1", "2", "3", "4", "5", "6", "7", "8", "9"};

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
