package it.simplemal.firehome;

import android.graphics.Bitmap;
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
        // On the digits as drawn: tracking and side bearings would push a whole line off centre.
        // Across, on where their ink weighs: a 1 centred on its box had its stem to the right of the
        // middle, the box taking in its flag (2026-11-09).
        canvas.drawText(number, bounds.exactCenterX() - weightX(),
            bounds.top + bounds.height() * center - ink.exactCenterY(), text);
        canvas.restoreToCount(layer);
    }

    private float weighedSize = -1;
    private float weighedX;

    /** Where the ink of the number weighs across, from where it is drawn: worked out once per size. */
    private float weightX() {
        // Several digits balance each other: their box is centre enough, as the timer's always was.
        if (number.length() > 1) return ink.exactCenterX();
        float size = text.getTextSize() * text.getTextScaleX();
        if (size == weighedSize) return weighedX;
        weighedSize = size;
        int width = Math.max(1, ink.width());
        int height = Math.max(1, ink.height());
        Bitmap mark = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8);
        Paint plain = new Paint(text);
        plain.setXfermode(null);
        new Canvas(mark).drawText(number, -ink.left, -ink.top, plain);
        byte[] alpha = new byte[width * height];
        mark.copyPixelsToBuffer(java.nio.ByteBuffer.wrap(alpha));
        mark.recycle();
        double sum = 0;
        double weight = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int a = alpha[y * width + x] & 0xff;
                sum += a * (x + 0.5);
                weight += a;
            }
        }
        weighedX = ink.left + (weight > 0 ? (float) (sum / weight) : width / 2f);
        return weighedX;
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
