package it.simplemal.firehome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import java.io.IOException;
import java.io.InputStream;

/** One finished illustration: sun, rain and lightning are in the picture, so nothing here moves. */
final class RasterScene implements WeatherView.Scene {
    private static final int OPAQUE = 255;

    private final Bitmap picture;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Rect screen = new Rect();

    private RasterScene(Bitmap picture) { this.picture = picture; }

    /** Null if the file cannot be read: the scene on screen stays. */
    static RasterScene load(Context context, String asset) {
        try (InputStream input = context.getAssets().open(asset)) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inMutable = true;
            Bitmap picture = BitmapFactory.decodeStream(input, null, options);
            if (picture == null) return null;
            BottomBlur.apply(picture);
            return new RasterScene(picture);
        } catch (IOException e) {
            return null;
        }
    }

    @Override public void draw(Canvas canvas, double time, float scroll, float alpha) {
        screen.set(0, 0, canvas.getWidth(), canvas.getHeight());
        paint.setAlpha(Math.round(alpha * OPAQUE));
        canvas.drawBitmap(picture, null, screen, paint);
    }

    @Override public boolean moves() { return false; }

    @Override public void release() { picture.recycle(); }
}
