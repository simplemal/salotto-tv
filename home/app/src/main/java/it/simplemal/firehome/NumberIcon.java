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

/** A picture of the control panel with a number in it: cut out of it, or drawn inside its outline. */
final class NumberIcon extends Drawable {
    /** Thin and tall, so that two digits still fit in a small circle. */
    private static final String FONT = "sans-serif-condensed-light";
    private static final float TEXT_SHARE = 0.6f;
    private static final float LONG_TEXT_SHARE = 0.46f;
    private static final int LONG = 3;

    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect ink = new Rect();
    private final Drawable picture;
    private final String number;
    private final float center;

    NumberIcon(Drawable picture, String number, float center, boolean cut) {
        this.picture = picture;
        this.number = number;
        this.center = center;
        text.setColor(Color.WHITE);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(Typeface.create(FONT, Typeface.NORMAL));
        if (cut) text.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
    }

    @Override public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        int layer = canvas.saveLayer(new RectF(bounds), null);
        picture.setBounds(bounds);
        picture.draw(canvas);
        text.setTextSize(bounds.height() * (number.length() < LONG ? TEXT_SHARE : LONG_TEXT_SHARE));
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
