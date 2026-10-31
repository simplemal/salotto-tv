package it.simplemal.firehome;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * The delicate line of light on liquid glass: bright along the top, fainter along the bottom,
 * almost gone on the sides. It follows the corners, fading as they turn away from the light.
 */
final class GlassRim extends Drawable {
    private static final float SIDE_SHARE = 0.12f;
    private static final float BOTTOM_SHARE = 0.48f;
    private static final float MAX_FADE = 0.45f;
    private static final float CORNER_REACH = 1.3f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF edge = new RectF();
    private final float radius;
    private final int light;

    GlassRim(int radius, int thickness, int light) {
        this.radius = radius;
        this.light = light;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(thickness);
    }

    private int white(float share) { return Color.argb(Math.round(light * share), 255, 255, 255); }

    @Override protected void onBoundsChange(Rect bounds) {
        float half = paint.getStrokeWidth() / 2;
        edge.set(bounds);
        edge.inset(half, half);
        float fade = bounds.height() == 0 ? MAX_FADE : Math.min(MAX_FADE, radius * CORNER_REACH / bounds.height());
        paint.setShader(new LinearGradient(0, bounds.top, 0, bounds.bottom,
            new int[]{white(1), white(SIDE_SHARE), white(SIDE_SHARE), white(BOTTOM_SHARE)},
            new float[]{0, fade, 1 - fade, 1}, Shader.TileMode.CLAMP));
    }

    @Override public void draw(Canvas canvas) {
        canvas.drawRoundRect(edge, radius, radius, paint);
    }

    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }

    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); }

    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
