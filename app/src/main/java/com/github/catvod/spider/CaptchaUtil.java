package com.github.catvod.spider;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import java.util.ArrayList;  // ← 添加了这一行

/**
 * 纯 Java 实现的轻量级 OCR 验证码识别 V2
 * 改进：去噪 + 自适应二值化 + 更精细特征
 */
class CaptchaUtil {

    static void initTessData(Context context) {}

    static String recognize(byte[] imgBytes, Context context) {
        if (imgBytes == null || imgBytes.length == 0) {
            System.out.println("=== OCR: imgBytes is null or empty");
            return "";
        }

        System.out.println("=== OCR: imgBytes length=" + imgBytes.length);

        if (imgBytes.length > 4) {
            StringBuilder hex = new StringBuilder("=== OCR: header=");
            for (int i = 0; i < Math.min(16, imgBytes.length); i++) {
                hex.append(String.format("%02X ", imgBytes[i] & 0xFF));
            }
            System.out.println(hex.toString());
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

            // 去噪
            int[][] pixels = bitmapToArray(processed);
            pixels = removeNoise(pixels);
            pixels = removeHorizontalLines(pixels);

            // 识别
            String result = recognizeDigits(pixels);
            System.out.println("=== OCR result: [" + result + "]");

            return result;

        } catch (Exception e) {
            System.out.println("=== OCR exception: " + e.getMessage());
            e.printStackTrace();
            return "";
        }
    }

    private static Bitmap preprocess(Bitmap src) {
        int width = src.getWidth();
        int height = src.getHeight();

        // 放大 4 倍
        float scale = 4.0f;
        Matrix matrix = new Matrix();
        matrix.postScale(scale, scale);
        Bitmap scaled = Bitmap.createBitmap(src, 0, 0, width, height, matrix, true);

        int newWidth = scaled.getWidth();
        int newHeight = scaled.getHeight();

        Bitmap out = Bitmap.createBitmap(newWidth, newHeight, Bitmap.Config.RGB_565);
        Canvas canvas = new Canvas(out);
        Paint paint = new Paint();

        // 灰度 + 对比度
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0);

        ColorMatrix contrast = new ColorMatrix(new float[] {
            4.0f, 0, 0, 0, -350,
            0, 4.0f, 0, 0, -350,
            0, 0, 4.0f, 0, -350,
            0, 0, 0, 1, 0
        });
        cm.postConcat(contrast);

        paint.setColorFilter(new ColorMatrixColorFilter(cm));
        canvas.drawBitmap(scaled, 0, 0, paint);

        return out;
    }

    private static int[][] bitmapToArray(Bitmap img) {
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
        return pixels;
    }

    /**
     * 去除孤立噪点（3x3 邻域统计）
     */
    private static int[][] removeNoise(int[][] pixels) {
        int h = pixels.length;
        int w = pixels[0].length;
        int[][] result = new int[h][w];

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (pixels[y][x] == 255) {
                    result[y][x] = 255;
                    continue;
                }

                // 统计 3x3 邻域黑色像素数
                int blackCount = 0;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int ny = y + dy;
                        int nx = x + dx;
                        if (ny >= 0 && ny < h && nx >= 0 && nx < w && pixels[ny][nx] == 0) {
                            blackCount++;
                        }
                    }
                }

                // 如果周围黑色像素少于 3 个，认为是噪点
                result[y][x] = (blackCount >= 3) ? 0 : 255;
            }
        }
        return result;
    }

    /**
     * 去除水平干扰线
     */
    private static int[][] removeHorizontalLines(int[][] pixels) {
        int h = pixels.length;
        int w = pixels[0].length;
        int[][] result = new int[h][w];

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                result[y][x] = pixels[y][x];
            }
        }

        for (int y = 1; y < h - 1; y++) {
            int blackCount = 0;
            for (int x = 0; x < w; x++) {
                if (pixels[y][x] == 0) blackCount++;
            }
            // 如果一行几乎都是黑色，可能是干扰线
            if (blackCount > w * 0.7) {
                for (int x = 0; x < w; x++) {
                    // 只去除连续的水平线，保留垂直结构
                    if (pixels[y-1][x] == 255 && pixels[y+1][x] == 255) {
                        result[y][x] = 255;
                    }
                }
            }
        }

        return result;
    }

    private static String recognizeDigits(int[][] pixels) {
        int h = pixels.length;
        int w = pixels[0].length;

        // 垂直投影分割字符
        int[] projection = new int[w];
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                if (pixels[y][x] == 0) projection[x]++;
            }
        }

        // 找到 4 个字符的位置
        ArrayList<int[]> charRanges = findCharRanges(projection, h);
        if (charRanges.size() < 4) {
            System.out.println("=== OCR: found only " + charRanges.size() + " chars");
            // 如果分割不到 4 个，用固定宽度
            return recognizeByFixedWidth(pixels);
        }

        StringBuilder result = new StringBuilder();
        for (int[] range : charRanges) {
            String digit = recognizeSingleDigit(pixels, range[0], range[1], h);
            result.append(digit);
        }

        return result.toString();
    }

    private static ArrayList<int[]> findCharRanges(int[] projection, int h) {
        ArrayList<int[]> ranges = new ArrayList<>();
        int w = projection.length;
        int threshold = h / 20; // 最小高度阈值

        int start = -1;
        for (int x = 0; x < w; x++) {
            if (projection[x] > threshold) {
                if (start == -1) start = x;
            } else {
                if (start != -1) {
                    if (x - start > 5) { // 最小宽度
                        ranges.add(new int[]{start, x});
                    }
                    start = -1;
                }
            }
        }
        if (start != -1 && w - start > 5) {
            ranges.add(new int[]{start, w});
        }

        // 如果找到太多，合并相邻的
        while (ranges.size() > 4) {
            // 合并最窄的相邻对
            int minWidth = Integer.MAX_VALUE;
            int mergeIdx = -1;
            for (int i = 0; i < ranges.size() - 1; i++) {
                int gap = ranges.get(i+1)[0] - ranges.get(i)[1];
                if (gap < minWidth) {
                    minWidth = gap;
                    mergeIdx = i;
                }
            }
            if (mergeIdx >= 0) {
                int[] merged = new int[]{ranges.get(mergeIdx)[0], ranges.get(mergeIdx+1)[1]};
                ranges.remove(mergeIdx+1);
                ranges.remove(mergeIdx);
                ranges.add(mergeIdx, merged);
            } else {
                break;
            }
        }

        return ranges;
    }

    private static String recognizeByFixedWidth(int[][] pixels) {
        int h = pixels.length;
        int w = pixels[0].length;
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

        // 10x8 网格特征
        int gridH = h / 8;
        int gridW = charW / 6;
        double[] features = new double[48];

        for (int gy = 0; gy < 8; gy++) {
            for (int gx = 0; gx < 6; gx++) {
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
                features[gy * 6 + gx] = total > 0 ? (double) blackCount / total : 0;
            }
        }

        // 更精细的数字模板（8x6 网格）
        double[][] templates = {
            // 0
            {0,1,1,1,1,0, 1,1,0,0,1,1, 1,0,0,0,0,1, 1,0,0,0,0,1, 1,0,0,0,0,1, 1,0,0,0,0,1, 1,1,0,0,1,1, 0,1,1,1,1,0},
            // 1
            {0,0,0,1,0,0, 0,0,1,1,0,0, 0,1,0,1,0,0, 0,0,0,1,0,0, 0,0,0,1,0,0, 0,0,0,1,0,0, 0,0,0,1,0,0, 0,1,1,1,1,1},
            // 2
            {0,1,1,1,1,0, 1,1,0,0,1,1, 0,0,0,0,1,1, 0,0,0,1,1,0, 0,0,1,1,0,0, 0,1,1,0,0,0, 1,1,0,0,0,0, 1,1,1,1,1,1},
            // 3
            {0,1,1,1,1,0, 1,1,0,0,1,1, 0,0,0,0,1,1, 0,0,1,1,1,0, 0,0,0,0,1,1, 0,0,0,0,1,1, 1,1,0,0,1,1, 0,1,1,1,1,0},
            // 4
            {0,0,0,0,1,0, 0,0,0,1,1,0, 0,0,1,0,1,0, 0,1,0,0,1,0, 1,1,1,1,1,1, 0,0,0,0,1,0, 0,0,0,0,1,0, 0,0,0,0,1,0},
            // 5
            {1,1,1,1,1,1, 1,1,0,0,0,0, 1,1,1,1,1,0, 0,0,0,0,1,1, 0,0,0,0,1,1, 0,0,0,0,1,1, 1,1,0,0,1,1, 0,1,1,1,1,0},
            // 6
            {0,1,1,1,1,0, 1,1,0,0,1,1, 1,1,0,0,0,0, 1,1,1,1,1,0, 1,1,0,0,1,1, 1,1,0,0,1,1, 1,1,0,0,1,1, 0,1,1,1,1,0},
            // 7
            {1,1,1,1,1,1, 0,0,0,0,1,1, 0,0,0,0,1,1, 0,0,0,1,1,0, 0,0,1,1,0,0, 0,0,1,1,0,0, 0,0,1,1,0,0, 0,0,1,1,0,0},
            // 8
            {0,1,1,1,1,0, 1,1,0,0,1,1, 1,1,0,0,1,1, 0,1,1,1,1,0, 1,1,0,0,1,1, 1,1,0,0,1,1, 1,1,0,0,1,1, 0,1,1,1,1,0},
            // 9
            {0,1,1,1,1,0, 1,1,0,0,1,1, 1,1,0,0,1,1, 1,1,0,0,1,1, 0,1,1,1,1,1, 0,0,0,0,1,1, 1,1,0,0,1,1, 0,1,1,1,1,0},
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
