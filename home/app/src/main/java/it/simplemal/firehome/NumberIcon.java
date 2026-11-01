package it.simplemal.firehome;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

/** A solid picture of the control panel with a number cut out of it. */
final class NumberIcon extends Drawable {
    /** Bold and narrow: what stays readable at this size. */
    private static final String FONT = "sans-serif-condensed";
    private static final float TEXT_SHARE = 0.47f;
    private static final float TRACKING = -0.05f;
    /** The room inside the circle: a longer number gets narrower, not shorter. */
    private static final float ROOM_SHARE = 0.56f;

    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect ink = new Rect();
    private final Drawable picture;
    private final String number;
    private final float center;

    NumberIcon(Drawable picture, String number, float center) {
        this.picture = picture;
        this.number = number;
        this.center = center;
        text.setColor(Color.WHITE);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(Typeface.create(FONT, Typeface.BOLD));
        text.setLetterSpacing(TRACKING);
        text.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
    }

    @Override public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        int layer = canvas.saveLayer(new RectF(bounds), null);
        picture.setBounds(bounds);
        picture.draw(canvas);
        text.setTextSize(bounds.height() * TEXT_SHARE);
        text.setTextScaleX(1);
        text.setTextScaleX(Math.min(1, bounds.height() * ROOM_SHARE / text.measureText(number)));
        text.getTextBounds(number, 0, number.length(), ink);
        canvas.drawText(number, bounds.exactCenterX(), bounds.top + bounds.height() * center - ink.exactCenterY(), text);
        canvas.restoreToCount(layer);
    }

    @Override public int getIntrinsicWidth() { return picture.getIntrinsicWidth(); }

    @Override public int getIntrinsicHeight() { return picture.getIntrinsicHeight(); }

    @Override public void setAlpha(int alpha) {
        picture.setAlpha(alpha);
        text.setAlpha(alpha);
    }

    @Override public void setColorFilter(ColorFilter filter) {
        picture.setColorFilter(filter);
        text.setColorFilter(filter);
    }

    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
