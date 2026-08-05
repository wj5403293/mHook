package com.rance.library;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.WorkerThread;

/**
 * 作者：Rance on 2016/11/10 16:41
 * 邮箱：rance935@163.com
 *
 * 纯 Java 实现的高斯近似模糊（三遍 box blur），替代已废弃的 RenderScript。
 */
public class Blur {
    private static final float SCALE = 0.4F;
    private static final int PASSES = 3;

    private float radius;

    private Thread blurThread;
    private Context context;
    private Bitmap inBitmap;
    private Callback callback;

    public Blur() {
        initThread();
    }

    private void initThread() {
        blurThread = new Thread(new Runnable() {
            @Override
            public void run() {
                final Bitmap blurred = getBlurBitmap(context, inBitmap, radius);
                Handler handler = new Handler(Looper.getMainLooper());
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (callback != null) {
                            callback.onBlurred(blurred);
                        }
                    }
                });
            }
        });
    }

    public void setParams(Callback callback, Context context, Bitmap inBitmap, float radius) {
        this.callback = callback;
        this.context = context;
        this.inBitmap = inBitmap;
        this.radius = radius;
    }

    public void execute() {
        blurThread.run();
    }

    @WorkerThread
    private Bitmap getBlurBitmap(Context context, Bitmap inBitmap, float radius) {
        if (context == null || inBitmap == null) {
            throw new IllegalArgumentException("have not called setParams() before call execute()");
        }

        int width = Math.round(inBitmap.getWidth() * SCALE);
        int height = Math.round(inBitmap.getHeight() * SCALE);

        Bitmap in = Bitmap.createScaledBitmap(inBitmap, width, height, false);
        Bitmap out = boxBlur(in, Math.max(1, Math.round(radius)));
        if (in != out && in != inBitmap) {
            in.recycle();
        }
        return out;
    }

    private static Bitmap boxBlur(Bitmap src, int radius) {
        int w = src.getWidth();
        int h = src.getHeight();
        int[] srcPix = new int[w * h];
        int[] tmpPix = new int[w * h];
        src.getPixels(srcPix, 0, w, 0, 0, w, h);

        int[] a = srcPix;
        int[] b = tmpPix;
        for (int i = 0; i < PASSES; i++) {
            blurHorizontal(a, b, w, h, radius);
            blurVertical(b, a, w, h, radius);
        }

        Bitmap out = Bitmap.createBitmap(src);
        out.setPixels(a, 0, w, 0, 0, w, h);
        return out;
    }

    private static void blurHorizontal(int[] src, int[] dst, int w, int h, int r) {
        for (int y = 0; y < h; y++) {
            int row = y * w;
            for (int x = 0; x < w; x++) {
                int sumR = 0, sumG = 0, sumB = 0, count = 0;
                for (int dx = -r; dx <= r; dx++) {
                    int nx = x + dx;
                    if (nx < 0 || nx >= w) {
                        continue;
                    }
                    int c = src[row + nx];
                    sumR += (c >> 16) & 0xFF;
                    sumG += (c >> 8) & 0xFF;
                    sumB += c & 0xFF;
                    count++;
                }
                dst[row + x] = 0xFF000000
                        | ((sumR / count) << 16)
                        | ((sumG / count) << 8)
                        | (sumB / count);
            }
        }
    }

    private static void blurVertical(int[] src, int[] dst, int w, int h, int r) {
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                int sumR = 0, sumG = 0, sumB = 0, count = 0;
                for (int dy = -r; dy <= r; dy++) {
                    int ny = y + dy;
                    if (ny < 0 || ny >= h) {
                        continue;
                    }
                    int c = src[ny * w + x];
                    sumR += (c >> 16) & 0xFF;
                    sumG += (c >> 8) & 0xFF;
                    sumB += c & 0xFF;
                    count++;
                }
                dst[y * w + x] = 0xFF000000
                        | ((sumR / count) << 16)
                        | ((sumG / count) << 8)
                        | (sumB / count);
            }
        }
    }

    public interface Callback {
        void onBlurred(Bitmap blurredBitmap);
    }
}
