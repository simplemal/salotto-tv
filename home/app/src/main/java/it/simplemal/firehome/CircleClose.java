package it.simplemal.firehome;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

/**
 * The closing after an app opened from the wheel, as the user asked on 2026-11-09: the screen black
 * with only the app's circle, small in its place. Then a ring of the app's own colour comes in from
 * past the corners and closes onto the circle, thinner and thinner until it is gone under it, and
 * outside the ring the Home is already there.
 *
 * Two layers, each closed by Android's circular reveal: under, the ring's colour; over, the black
 * with the circle, always a little inside it. The reveal runs on the drawing thread, so it stays
 * smooth while the Home, on the main thread, is busy coming back.
 */
final class CircleClose extends FrameLayout {
    /** Simple, and slowing down at the end, as the user asked: the ring settles onto the icon. */
    static final long MS = 600;
    private static final float SETTLE = 1.8f;
    /** The black starts just past the farthest corner, the ring this much farther out. */
    private static final float FROM = 1.05f;
    private static final int RING_DP = 14;

    private final float x;
    private final float y;
    private final float radius;
    private final View ring;
    private final View black;

    CircleClose(Context context, int ringColour, Bitmap art, float x, float y, float radius) {
        super(context);
        this.x = x;
        this.y = y;
        this.radius = radius;
        ring = new View(context);
        ring.setBackgroundColor(Color.rgb(Color.red(ringColour), Color.green(ringColour), Color.blue(ringColour)));
        addView(ring, new LayoutParams(-1, -1));
        black = new Black(context, art, x, y, radius);
        addView(black, new LayoutParams(-1, -1));
    }

    /** The black, and on it the app's circle. */
    private static final class Black extends View {
        private final Bitmap art;
        private final float x;
        private final float y;
        private final float radius;
        private final Paint picture = new Paint(Paint.FILTER_BITMAP_FLAG);
        private final Path round = new Path();
        private final RectF place = new RectF();

        Black(Context context, Bitmap art, float x, float y, float radius) {
            super(context);
            this.art = art;
            this.x = x;
            this.y = y;
            this.radius = radius;
        }

        @Override protected void onDraw(Canvas canvas) {
            canvas.drawColor(Color.BLACK);
            if (art == null) return;
            place.set(x - radius, y - radius, x + radius, y + radius);
            round.rewind();
            round.addCircle(x, y, radius, Path.Direction.CW);
            canvas.save();
            canvas.clipPath(round);
            canvas.drawBitmap(art, null, place, picture);
            canvas.restore();
        }
    }

    /** Laid out first, then closed onto the circle; `done` follows at its end. */
    void close(Runnable done) {
        post(() -> {
            if (!isAttachedToWindow()) return;
            float far = (float) Math.hypot(Math.max(x, getWidth() - x), Math.max(y, getHeight() - y)) * FROM;
            float width = RING_DP * getResources().getDisplayMetrics().density;
            // Both end on the circle at the same moment: the ring between them thins to nothing.
            Animator outer = reveal(ring, far + width);
            Animator inner = reveal(black, far);
            inner.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator animation) { done.run(); }
            });
            outer.start();
            inner.start();
        });
    }

    private Animator reveal(View layer, float from) {
        Animator shrink = ViewAnimationUtils.createCircularReveal(layer, Math.round(x), Math.round(y), from, radius);
        shrink.setDuration(MS);
        shrink.setInterpolator(new DecelerateInterpolator(SETTLE));
        return shrink;
    }
}
