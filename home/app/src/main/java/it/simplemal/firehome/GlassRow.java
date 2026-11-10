package it.simplemal.firehome;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;

/**
 * Rows and buttons as the monochrome glass of the apps (GlassIcon), flat: a dark veil fuller along a
 * frame of a few dp, and a thin line of light brighter toward the top left. Chosen by the user on
 * 2026-11-10, without the sheen from above and the fall toward the bottom that the apps have.
 */
final class GlassRow extends Drawable {
    private static final float VEIL_FRAME = 0.55f;
    private static final float VEIL_MIDDLE = 0.24f;
    /** As the apps' glass at rest: made 1.3 times larger, then shown at its size (CLEAN_FOCUS_SCALE). */
    private static final float SHOWN = 1 / 1.3f;
    private static final float FRAME_DP = 5f * SHOWN;
    private static final float LINE_DP = 0.75f * SHOWN;
    private static final float LINE_LIGHT = 0.45f;
    private static final float LIGHT_X = -0.55f;
    private static final float LIGHT_Y = -0.83f;
    private static final float GREY = 0.21f;
    private static final Map<String, Bitmap> MADE = new HashMap<>();

    private final float radius;
    private final float density;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private Bitmap shown;

    GlassRow(float radius, float density) {
        this.radius = radius;
        this.density = density;
    }

    /** Rows of one size share one picture. */
    @Override protected void onBoundsChange(Rect bounds) {
        int width = bounds.width();
        int height = bounds.height();
        if (width <= 0 || height <= 0) return;
        String key = width + "x" + height + "/" + radius;
        synchronized (MADE) {
            shown = MADE.get(key);
            if (shown == null) {
                long start = SystemClock.uptimeMillis();
                shown = make(width, height);
                Log.i("Salotto", "Glass row " + key + " made in " + (SystemClock.uptimeMillis() - start) + " ms");
                MADE.put(key, shown);
            }
        }
    }

    private Bitmap make(int width, int height) {
        float frame = FRAME_DP * density;
        float line = LINE_DP * density;
        int[] made = new int[width * height];
        float[] pixel = new float[4];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float px = x + 0.5f;
                float py = y + 0.5f;
                float d = distance(px, py, width, height);
                float cover = clamp(0.5f - d);
                if (cover <= 0) continue;
                float toMiddle = smoothstep(0, frame, -d);
                pixel[0] = GREY;
                pixel[1] = GREY;
                pixel[2] = GREY;
                pixel[3] = VEIL_FRAME + (VEIL_MIDDLE - VEIL_FRAME) * toMiddle;
                // The distance grows outward: its slope is the way the edge faces.
                float gx = distance(px + 1, py, width, height) - distance(px - 1, py, width, height);
                float gy = distance(px, py + 1, width, height) - distance(px, py - 1, width, height);
                float length = (float) Math.hypot(gx, gy) + 1e-6f;
                float facing = (gx / length) * LIGHT_X + (gy / length) * LIGHT_Y;
                float lit = (float) Math.pow(Math.max(0, facing), 1.6);
                float away = (float) Math.pow(Math.max(0, -facing), 2.2);
                float stroke = clamp(line / 2 + 0.5f - Math.abs(d + line / 2));
                over(pixel, clamp(stroke * LINE_LIGHT * (0.10f + 0.90f * lit + 0.45f * away)));
                float a = pixel[3] * cover;
                made[y * width + x] = Math.round(a * 255) << 24 | Math.round(clamp(pixel[0]) * 255) << 16
                    | Math.round(clamp(pixel[1]) * 255) << 8 | Math.round(clamp(pixel[2]) * 255);
            }
        }
        return Bitmap.createBitmap(made, width, height, Bitmap.Config.ARGB_8888);
    }

    /** Signed distance from the rounded rectangle's edge: negative inside. */
    private float distance(float x, float y, int width, int height) {
        float r = Math.min(radius, Math.min(width, height) / 2f);
        float qx = Math.abs(x - width / 2f) - width / 2f + r;
        float qy = Math.abs(y - height / 2f) - height / 2f + r;
        float outside = (float) Math.hypot(Math.max(qx, 0), Math.max(qy, 0));
        return outside + Math.min(Math.max(qx, qy), 0) - r;
    }

    /** White over the pixel. */
    private static void over(float[] pixel, float alpha) {
        if (alpha <= 0) return;
        float under = pixel[3] * (1 - alpha);
        float total = alpha + under;
        for (int c = 0; c < 3; c++) pixel[c] = (alpha + pixel[c] * under) / total;
        pixel[3] = total;
    }

    private static float clamp(float value) { return Math.max(0, Math.min(1, value)); }

    private static float smoothstep(float from, float to, float value) {
        float t = clamp((value - from) / (to - from));
        return t * t * (3 - 2 * t);
    }

    @Override public void draw(Canvas canvas) {
        if (shown != null) canvas.drawBitmap(shown, null, getBounds(), paint);
    }

    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
