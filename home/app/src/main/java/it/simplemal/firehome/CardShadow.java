package it.simplemal.firehome;

import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import java.util.HashMap;
import java.util.Map;

/**
 * The shadow of a card, always towards the bottom right. Android's own shadows fall away from a
 * light in the middle of the screen, so they would point a different way on each side. The blur
 * is drawn once for each size of card: a frame only places a small bitmap.
 */
final class CardShadow extends Drawable {
    private static final Map<String, Bitmap> MADE = new HashMap<>();

    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final RectF place = new RectF();
    private final Bitmap rest;
    private final Bitmap raised;
    private final int cardWidth;
    private final int cardHeight;
    private final float restBlur;
    private final float raisedBlur;
    private final float restX;
    private final float restY;
    private final float raisedX;
    private final float raisedY;
    private final float scale;
    private float lift;
    /** How dark, 1 as it was made: the clean Home asks for less. */
    private final float strength;

    CardShadow(int cardWidth, int cardHeight, int radius, float unit, float scale, float strength) {
        this.strength = strength;
        this.cardWidth = cardWidth;
        this.cardHeight = cardHeight;
        this.scale = scale;
        restBlur = 2.5f * unit;
        raisedBlur = 12 * unit;
        restX = 1.5f * unit;
        restY = 2.5f * unit;
        raisedX = 5 * unit;
        raisedY = 13 * unit;
        paint.setColor(Color.BLACK);
        rest = blurred(cardWidth, cardHeight, radius, restBlur);
        raised = blurred(cardWidth, cardHeight, radius, raisedBlur);
    }

    private static Bitmap blurred(int width, int height, int radius, float blur) {
        String key = width + "x" + height + "/" + radius + "/" + blur;
        Bitmap made = MADE.get(key);
        if (made != null) return made;
        int room = Math.round(2 * blur);
        made = Bitmap.createBitmap(width + 2 * room, height + 2 * room, Bitmap.Config.ALPHA_8);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setMaskFilter(new BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL));
        new Canvas(made).drawRoundRect(new RectF(room, room, room + width, room + height), radius, radius, paint);
        MADE.put(key, made);
        return made;
    }

    float lift() { return lift; }

    /** From 0 at rest to 1 on the selected card. */
    void setLift(float value) {
        lift = value;
        invalidateSelf();
    }

    private void drawOne(Canvas canvas, Bitmap bitmap, float blur, float x, float y, float size, float opacity) {
        if (opacity <= 0) return;
        float room = Math.round(2 * blur) * size;
        float width = cardWidth * size;
        float height = cardHeight * size;
        float left = getBounds().left + (cardWidth - width) / 2 + x;
        float top = getBounds().top + (cardHeight - height) / 2 + y;
        place.set(left - room, top - room, left + width + room, top + height + room);
        paint.setAlpha(Math.round(255 * opacity));
        canvas.drawBitmap(bitmap, null, place, paint);
    }

    @Override public void draw(Canvas canvas) {
        drawOne(canvas, rest, restBlur, restX, restY, 1, 0.5f * strength * (1 - lift));
        drawOne(canvas, raised, raisedBlur, raisedX, raisedY, 1 + (scale - 1) * lift, 0.55f * strength * lift);
    }

    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }

    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); }

    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
