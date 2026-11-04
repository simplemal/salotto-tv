package it.simplemal.firehome;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

/**
 * Opening an app from the wheel, over every window for a moment: the Home's picture, and a disc of
 * the app's colour that widens from under its circle, which stays on top, until it covers the screen.
 * The app starts underneath, and straight away a hole with nothing drawn in it opens from the middle
 * of the circle, through its logo, so the app shows through a ring that runs off the screen.
 * Only Salotto's accessibility service can put a window over another app, so it lives there.
 */
final class PortalWindow extends View {
    private static final long COVER_MS = 380;
    private static final long OPEN_MS = 480;
    /** Should a frame never come, the window still goes. */
    private static final long SPARE_MS = 2000;
    /** The disc ends past the farthest corner, and the hole goes almost as far: its ring is off the screen. */
    private static final float RING_END = 1.25f;
    private static final float HOLE_END = 1.2f;

    /** The one on screen: a second opening replaces it. */
    private static PortalWindow current;

    private final WindowManager windows;
    private final Bitmap home;
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint picture = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Path outside = new Path();
    private final Path band = new Path();
    private final Path round = new Path();
    private final Path gap = new Path();
    private final android.graphics.RectF place = new android.graphics.RectF();
    /** The app's circle as the wheel shows it, or null. */
    private final Bitmap art;
    private final Rect whole = new Rect();
    private final float x;
    private final float y;
    private final float start;
    private Runnable shown;
    private final Runnable opened;
    private float progress;
    private float hole;
    private boolean holeOpening;
    private boolean gone;


    private PortalWindow(Context context, Bitmap home, Bitmap art, int colour, float x, float y, float start, Runnable shown, Runnable opened) {
        super(context);
        this.art = art;
        windows = context.getSystemService(WindowManager.class);
        this.home = home.copy(Bitmap.Config.ARGB_8888, false);
        this.x = x;
        this.y = y;
        this.start = start;
        this.shown = shown;
        this.opened = opened;
        ring.setColor(colour);
    }

    /** `shown` runs once the window covers the screen, `opened` when it has gone. */
    static void open(Context service, Bitmap home, Bitmap art, int colour, float x, float y, float radius, Runnable shown, Runnable opened) {
        if (current != null) current.close();
        PortalWindow view = new PortalWindow(service, home, art, colour, x, y, radius, shown, opened);
        current = view;
        view.windows.addView(view, new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT));
        view.postDelayed(view::close, COVER_MS + OPEN_MS + SPARE_MS);
    }

    @Override protected void onDraw(Canvas canvas) {
        int width = getWidth();
        int height = getHeight();
        float far = (float) Math.hypot(Math.max(x, width - x), Math.max(y, height - y));
        // What a frame does not draw keeps the last one's pixels: the hole must be cleared each time.
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        float outer = start + (far * RING_END - start) * progress;
        float inner = Math.min(outer, far * HOLE_END * hole);

        outside.rewind();
        outside.addCircle(x, y, outer, Path.Direction.CW);
        canvas.save();
        canvas.clipOutPath(outside);
        whole.set(0, 0, width, height);
        canvas.drawBitmap(home, null, whole, picture);
        canvas.restore();

        band.rewind();
        // Rewinding also puts the fill back to winding, which would paint the hole too.
        band.setFillType(Path.FillType.EVEN_ODD);
        band.addCircle(x, y, outer, Path.Direction.CW);
        if (inner > 0) band.addCircle(x, y, inner, Path.Direction.CW);
        canvas.drawPath(band, ring);

        if (art != null && inner < start) {
            // The circle stays on top, in its place, and the hole eats it from the middle.
            place.set(x - start, y - start, x + start, y + start);
            round.rewind();
            round.addCircle(x, y, start, Path.Direction.CW);
            canvas.save();
            canvas.clipPath(round);
            if (inner > 0) {
                gap.rewind();
                gap.addCircle(x, y, inner, Path.Direction.CW);
                canvas.clipOutPath(gap);
            }
            canvas.drawBitmap(art, null, place, picture);
            canvas.restore();
        }

        if (shown != null) {
            // This frame now covers the Home exactly as it was: the app can start under it.
            Runnable now = shown;
            shown = null;
            post(() -> {
                now.run();
                cover();
            });
        }
    }

    /** The disc widens over the Home while the app starts under it. */
    private void cover() {
        ValueAnimator grow = ValueAnimator.ofFloat(0f, 1f);
        grow.setDuration(COVER_MS);
        grow.setInterpolator(new AccelerateInterpolator(1.2f));
        grow.addUpdateListener(step -> {
            progress = (float) step.getAnimatedValue();
            invalidate();
        });
        grow.addListener(new AnimatorListenerAdapter() {
            // Straight on, whatever is under it yet: waiting for the app made the opening falter.
            @Override public void onAnimationEnd(Animator animation) { openHole(); }
        });
        grow.start();
    }

    /** Nothing drawn inside: the app is seen through it as it widens past the corners. */
    private void openHole() {
        if (gone || holeOpening) return;
        holeOpening = true;
        ValueAnimator open = ValueAnimator.ofFloat(0f, 1f);
        open.setDuration(OPEN_MS);
        open.setInterpolator(new DecelerateInterpolator(1.4f));
        open.addUpdateListener(step -> {
            hole = (float) step.getAnimatedValue();
            invalidate();
        });
        open.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) { close(); }
        });
        open.start();
    }

    private void close() {
        if (gone) return;
        gone = true;
        if (current == this) current = null;
        try {
            windows.removeView(this);
        } catch (IllegalArgumentException e) {
            // Already gone with the service.
        }
        // The picture is not recycled: the window may still draw one last frame as it goes.
        opened.run();
    }
}
