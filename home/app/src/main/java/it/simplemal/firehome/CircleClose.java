package it.simplemal.firehome;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
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
 * with only the app's circle, small in its place. The black closes quickly onto the circle and the
 * app's own colour is all round it, as wide as the opening left it; then that colour closes onto the
 * circle too, a ring thinner and thinner until it is gone under it, and outside it the Home is there.
 *
 * Two layers, each closed by Android's circular reveal: under, the colour; over, the black with the
 * circle, closing faster. The reveal runs on the drawing thread, so it stays smooth while the Home,
 * on the main thread, is busy coming back.
 */
final class CircleClose extends FrameLayout {
    /** The whole closing, until the colour has settled onto the icon. */
    static final long MS = 560;
    /** The black goes first and fast: the colour is soon all round the icon. */
    private static final long BLACK_MS = 260;
    /** Then the colour closes quickly, slowing down only at the end. */
    private static final long COLOUR_DELAY_MS = 100;
    private static final float SETTLE = 1.8f;
    private static final float BLACK_SETTLE = 2.5f;
    /** Both start just past the farthest corner. */
    private static final float FROM = 1.05f;

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
            // Both last the whole closing and end together: a reveal that ends lets go of its
            // circle, and the black, ended first, came back over the whole Home (2026-11-09).
            Animator inner = reveal(black, far, between(0, BLACK_MS, BLACK_SETTLE));
            Animator outer = reveal(ring, far, between(COLOUR_DELAY_MS, MS, SETTLE));
            outer.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator animation) { done.run(); }
            });
            inner.start();
            outer.start();
        });
    }

    private Animator reveal(View layer, float from, TimeInterpolator pace) {
        Animator shrink = ViewAnimationUtils.createCircularReveal(layer, Math.round(x), Math.round(y), from, radius);
        shrink.setDuration(MS);
        shrink.setInterpolator(pace);
        return shrink;
    }

    /** Still before `startMs`, closing from there, slowing down, and closed from `endMs` to the end. */
    private static TimeInterpolator between(long startMs, long endMs, float settle) {
        DecelerateInterpolator slowing = new DecelerateInterpolator(settle);
        float start = startMs / (float) MS;
        float end = endMs / (float) MS;
        return time -> time <= start ? 0f : time >= end ? 1f : slowing.getInterpolation((time - start) / (end - start));
    }
}
