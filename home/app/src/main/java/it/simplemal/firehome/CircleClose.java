package it.simplemal.firehome;

import android.animation.Animator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.animation.DecelerateInterpolator;

/**
 * The closing after an app opened from the wheel: the app's circle, small in its place, on its own
 * background as wide as the screen. Android's circular reveal then closes that background onto the
 * circle. The reveal runs on the drawing thread, so it stays smooth while the Home, on the main
 * thread, is busy coming back.
 */
final class CircleClose extends View {
    /** Simple, and slowing down at the end, as the user asked: the circle settles onto the icon. */
    static final long MS = 500;
    private static final float SETTLE = 1.8f;
    /** The background starts just past the farthest corner. */
    private static final float FROM = 1.05f;

    private final int background;
    private final Bitmap art;
    private final float x;
    private final float y;
    private final float radius;
    private final Paint picture = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Path round = new Path();
    private final RectF place = new RectF();

    CircleClose(Context context, int background, Bitmap art, float x, float y, float radius) {
        super(context);
        this.background = background;
        this.art = art;
        this.x = x;
        this.y = y;
        this.radius = radius;
    }

    @Override protected void onDraw(Canvas canvas) {
        canvas.drawColor(background);
        if (art == null) return;
        place.set(x - radius, y - radius, x + radius, y + radius);
        round.rewind();
        round.addCircle(x, y, radius, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(round);
        canvas.drawBitmap(art, null, place, picture);
        canvas.restore();
    }

    /** Laid out first, then closed onto the circle; `done` follows at its end. */
    void close(Runnable done) {
        post(() -> {
            if (!isAttachedToWindow()) return;
            float far = (float) Math.hypot(Math.max(x, getWidth() - x), Math.max(y, getHeight() - y));
            Animator shrink = ViewAnimationUtils.createCircularReveal(this, Math.round(x), Math.round(y), far * FROM, radius);
            shrink.setDuration(MS);
            shrink.setInterpolator(new DecelerateInterpolator(SETTLE));
            shrink.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator animation) { done.run(); }
            });
            shrink.start();
        });
    }
}
