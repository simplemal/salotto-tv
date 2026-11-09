package it.simplemal.firehome;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;

/**
 * Blurs the lower part of a picture, from nothing at its top to all of it near the bottom. Done
 * once, when the picture is loaded: what the Home then draws is still one plain bitmap.
 */
final class BottomBlur {
    private static final float START = 0.6f;
    /** Where in that part the blur is complete. */
    private static final float FULL = 0.7f;
    /** Each halving doubles the blur. Android 11 has no blur of its own for a view. */
    private static final int HALVINGS = 4;

    /**
     * Off when nothing rests at the bottom: the clean Home with its first row in the middle or at
     * the top, the user's request of 2026-11-09. Set by the Home before it loads a picture.
     */
    static volatile boolean on = true;

    private BottomBlur() {}

    /** `picture` must be mutable. */
    static void apply(Bitmap picture) {
        if (!on) return;
        int width = picture.getWidth();
        int height = picture.getHeight();
        int top = Math.round(height * START);
        Bitmap soft = blurred(Bitmap.createBitmap(picture, 0, top, width, height - top));
        Canvas canvas = new Canvas(picture);
        int layer = canvas.saveLayer(0, top, width, height, null);
        canvas.drawBitmap(soft, 0, top, null);
        Paint fade = new Paint();
        fade.setShader(new LinearGradient(0, top, 0, top + FULL * (height - top), Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP));
        fade.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        canvas.drawRect(0, top, width, height, fade);
        canvas.restoreToCount(layer);
        soft.recycle();
    }

    private static Bitmap blurred(Bitmap strip) {
        int width = strip.getWidth();
        int height = strip.getHeight();
        Bitmap soft = strip;
        for (int step = 1; step <= HALVINGS; step++) {
            soft = resized(soft, Math.max(1, width >> step), Math.max(1, height >> step));
        }
        for (int step = HALVINGS - 1; step >= 0; step--) {
            soft = resized(soft, Math.max(1, width >> step), Math.max(1, height >> step));
        }
        return soft;
    }

    /** Filtered both ways: the smoothing is what makes the blur. */
    private static Bitmap resized(Bitmap from, int width, int height) {
        Bitmap to = Bitmap.createScaledBitmap(from, width, height, true);
        if (to != from) from.recycle();
        return to;
    }
}
