package it.simplemal.firehome;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * One of Salotto's own backdrops: three colours down a slant and a soft light in the top corner.
 * Dark colours spread over a whole screen have few steps between them, so both are dithered:
 * without that they show as bands.
 */
final class Shade extends Drawable {
    /** How far across the slant ends when it reaches the bottom. */
    private static final float SLANT = 0.45f;
    private static final float LIGHT_X = 0.8f;
    private static final float LIGHT_Y = 0.12f;
    private static final float LIGHT_SIZE = 0.8f;

    private final Paint base = new Paint();
    private final Paint light = new Paint();
    private final int[] colors;
    private final int glow;

    /** Three opaque colours, then the light with its strength as alpha. */
    Shade(int[] colors) {
        this.colors = new int[]{colors[0], colors[1], colors[2]};
        glow = colors[3];
        base.setDither(true);
        light.setDither(true);
    }

    @Override protected void onBoundsChange(Rect bounds) {
        float width = bounds.width();
        float height = bounds.height();
        if (width <= 0 || height <= 0) return;
        base.setShader(new LinearGradient(bounds.left, bounds.top, bounds.left + width * SLANT, bounds.bottom,
            colors, null, Shader.TileMode.CLAMP));
        light.setShader(new RadialGradient(bounds.left + width * LIGHT_X, bounds.top + height * LIGHT_Y, width * LIGHT_SIZE,
            glow, glow & Color.WHITE, Shader.TileMode.CLAMP));
    }

    @Override public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        canvas.drawRect(bounds, base);
        canvas.drawRect(bounds, light);
    }

    @Override public void setAlpha(int alpha) {
        base.setAlpha(alpha);
        light.setAlpha(alpha);
    }

    @Override public void setColorFilter(ColorFilter filter) {
        base.setColorFilter(filter);
        light.setColorFilter(filter);
    }

    @Override public int getOpacity() { return PixelFormat.OPAQUE; }
}
