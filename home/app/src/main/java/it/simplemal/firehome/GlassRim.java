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
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;

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
    private final RectF step = new RectF();
    private int alpha = 255;
    private final float radius;
    private final int light;
    private final float sides;
    private final int depth;

    GlassRim(int radius, int thickness, int light) {
        this(radius, thickness, light, SIDE_SHARE, 1);
    }

    /** The whole body of the control panel: glass, a sheen from above and this line. */
    static Drawable panel(int radius, int thickness) {
        GradientDrawable glass = new GradientDrawable();
        glass.setColor(Color.argb(46, 255, 255, 255));
        glass.setCornerRadius(radius);
        GradientDrawable sheen = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{Color.argb(54, 255, 255, 255), Color.TRANSPARENT, Color.TRANSPARENT});
        sheen.setCornerRadius(radius);
        return new LayerDrawable(new Drawable[]{glass, sheen, new GlassRim(radius, thickness, 235)});
    }

    private GlassRim(int radius, int thickness, int light, float sides, int depth) {
        this.radius = radius;
        this.light = light;
        this.sides = sides;
        this.depth = depth;
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
            new int[]{white(1), white(sides), white(sides), white(BOTTOM_SHARE)},
            new float[]{0, fade, 1 - fade, 1}, Shader.TileMode.CLAMP));
    }

    @Override public void draw(Canvas canvas) {
        step.set(edge);
        for (int inward = 0; inward < depth; inward++) {
            float left = 1f - (float) inward / depth;
            paint.setAlpha(Math.round(alpha * left * left));
            canvas.drawRoundRect(step, radius - inward, radius - inward, paint);
            step.inset(paint.getStrokeWidth(), paint.getStrokeWidth());
        }
    }

    @Override public void setAlpha(int alpha) { this.alpha = alpha; }

    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); }

    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
