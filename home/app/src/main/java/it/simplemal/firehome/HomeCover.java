package it.simplemal.firehome;

import android.accessibilityservice.AccessibilityService;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.animation.DecelerateInterpolator;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.PixelCopy;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.ImageView;


/**
 * Takes the Home key from Fire OS. The key never reaches an app: Fire OS brings up its own Home, and
 * the only way to hear of it is the system log. Salotto starts the moment that line is read, before
 * Amazon's Home has drawn anything, and the last picture of Salotto's Home stays above all of it
 * until the real one is there.
 */
final class HomeCover {
    private static final int WIDTH = 960;
    private static final int HEIGHT = 540;
    /** The Home comes in over Amazon's with an animation: this stays on top until that has ended. */
    private static final long AFTER_HOME_MS = 300;
    /** A line of the log can find the Home already there, and nothing then says it has come back. */
    private static final long SHOW_LIMIT_MS = Fire.ANDROID_9 ? 2000 : 1200;
    private static final long HOLD_LIMIT_MS = 3000;
    private static final long DOUBLE_PRESS_MS = 900;
    /** As quick as the Home's own closing; the background starts just past the farthest corner. */
    private static final long SHRINK_MS = 350;
    private static final float SHRINK_FROM = 1.05f;
    private static final int AMAZON_TRIES = 30;
    private static final long AMAZON_TRY_MS = 50;
    /** The Home just started needs this long to take over from Amazon's. */
    private static final long AMAZON_SETTLE_MS = Fire.ANDROID_9 ? 1500 : 600;

    /** The setting: a double press leaves Amazon's Home on screen. */
    static volatile boolean doubleHome;
    private static Bitmap picture;
    /** The Home while it is on screen: a press there goes through Amazon's Home just the same. */
    private static Window home;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable hide = this::hide;
    private final AccessibilityService service;
    private final WindowManager windows;
    private final ImageView view;
    /** Asked for with a double press: it stays until a press or another app says otherwise. */
    private boolean leftOnAmazon;
    private final Runnable lookForAmazon = this::lookForAmazon;
    private int amazonTries;
    /** Only the answer to the latest question counts. */
    private int lastLook;
    private volatile long startedAt;
    private volatile long lastHeard;
    private boolean holding;
    private Bitmap held;

    /** A window takes long to add and a frame to show, so it is always there, hidden. */
    HomeCover(AccessibilityService service) {
        this.service = service;
        windows = service.getSystemService(WindowManager.class);
        view = new ImageView(service);
        view.setScaleType(ImageView.ScaleType.FIT_XY);
        view.setVisibility(View.GONE);
        windows.addView(view, new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.OPAQUE));
    }

    static boolean ready() { return picture != null; }

    static Bitmap lastPicture() { return picture; }


    static void homeShown(Window window) { home = window; }

    /** The app opened from the wheel: its circle, the colour of its background, and where it sits. */
    private static final class Closing {
        final Bitmap art;
        final int background;
        final float x;
        final float y;
        final float radius;

        Closing(Bitmap art, int background, float x, float y, float radius) {
            this.art = art;
            this.background = background;
            this.x = x;
            this.y = y;
            this.radius = radius;
        }
    }

    private static volatile Closing closing;

    static void armClose(Bitmap art, int background, float x, float y, float radius) {
        closing = new Closing(art, background, x, y, radius);
    }

    static void disarm() { closing = null; }

    /**
     * The Home key after an app from the wheel, drawn at once over everything: the circle's own
     * background, as wide as the screen, shrinks onto the app's circle, which stays small in its
     * place over the Home's picture, until the two are one.
     */
    private final class Shrink extends Drawable {
        private final Closing where;
        private final Bitmap under;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pictures = new Paint(Paint.FILTER_BITMAP_FLAG);
        private final Path round = new Path();
        private final RectF place = new RectF();
        private float progress;

        Shrink(Closing where, Bitmap under) {
            this.where = where;
            this.under = under;
            fill.setColor(where.background);
        }

        void setProgress(float value) {
            progress = value;
            invalidateSelf();
        }

        @Override public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            if (under != null) canvas.drawBitmap(under, null, bounds, pictures);
            else canvas.drawColor(Color.BLACK);
            float far = (float) Math.hypot(Math.max(where.x, bounds.width() - where.x), Math.max(where.y, bounds.height() - where.y));
            float start = far * SHRINK_FROM;
            canvas.drawCircle(where.x, where.y, start + (where.radius - start) * progress, fill);
            place.set(where.x - where.radius, where.y - where.radius, where.x + where.radius, where.y + where.radius);
            round.rewind();
            round.addCircle(where.x, where.y, where.radius, Path.Direction.CW);
            canvas.save();
            canvas.clipPath(round);
            canvas.drawBitmap(where.art, null, place, pictures);
            canvas.restore();
        }

        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(ColorFilter filter) { }
        @Override public int getOpacity() { return PixelFormat.OPAQUE; }
    }

    private Shrink shrink;
    private boolean shrinking;
    /** The Home has drawn itself under it: the cover goes as soon as the circle has closed. */
    private boolean homeIsBack;
    private Runnable whenGone;

    private void startShrink(Closing where) {
        shrink = new Shrink(where, picture);
        view.setImageDrawable(shrink);
        shrinking = true;
        homeIsBack = false;
        ValueAnimator close = ValueAnimator.ofFloat(0f, 1f);
        close.setDuration(SHRINK_MS);
        close.setInterpolator(new DecelerateInterpolator());
        close.addUpdateListener(step -> { if (shrink != null) shrink.setProgress((float) step.getAnimatedValue()); });
        close.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                shrinking = false;
                // The last frame is the Home as it will be: the real one takes over as soon as it is there.
                main.removeCallbacks(hide);
                if (homeIsBack) hide();
                else main.postDelayed(hide, SHOW_LIMIT_MS);
            }
        });
        close.start();
    }

    /** The Home key's closing is being drawn here: the Home leaves it to the cover. */
    boolean closingHere() { return shrink != null; }

    /** `then` runs once the cover is gone, at once if it is not up. */
    void whenGone(Runnable then) {
        if (view.getVisibility() != View.VISIBLE) then.run();
        else whenGone = then;
    }

    /** From the Home, as it leaves the screen: half size, the cover is up for a moment only. */
    static void take(Window window) { take(window, () -> { }); }

    static void take(Window window, Runnable taken) {
        if (picture == null) picture = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        try {
            PixelCopy.request(window, picture, result -> taken.run(), new Handler(Looper.getMainLooper()));
        } catch (IllegalArgumentException e) {
            // The window has no surface any more: the last picture will do.
            taken.run();
        }
    }

    /**
     * From the thread reading the log. The Home starts from here and not from the main thread,
     * which the Home itself may be keeping busy: every moment lets Amazon's reach the screen.
     */
    void heard() {
        long now = SystemClock.elapsedRealtime();
        boolean stay = now - lastHeard < DOUBLE_PRESS_MS && doubleHome;
        lastHeard = now;
        if (!stay) startHome();
        main.post(() -> pressed(stay));
    }

    private void pressed(boolean stay) {
        leftOnAmazon = stay;
        if (stay) hide();
        // A first press with no picture yet: the Home can still give one, Amazon's has not drawn.
        else if (picture == null && MainActivity.showing && home != null) take(home, this::show);
        else show();
        if (!stay) amazonShown();
    }

    private void startHome() {
        startedAt = SystemClock.elapsedRealtime();
        Intent salotto = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            .setClass(service, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (Fire.ANDROID_9) {
            service.getSystemService(AlarmManager.class).setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, startedAt,
                PendingIntent.getActivity(service, 0, salotto, PendingIntent.FLAG_UPDATE_CURRENT));
        } else {
            service.startActivity(salotto);
        }
    }

    /** Another app came up, so a double press no longer keeps Amazon's Home. */
    void appOpened() { leftOnAmazon = false; }

    /**
     * Amazon's Home got the screen after all, at power-on, under an app that ended or ahead of a
     * Home started at once. It tells of that from behind other apps as well, so what counts is
     * whether it is the active window.
     */
    void amazonShown() {
        amazonTries = AMAZON_TRIES;
        main.removeCallbacks(lookForAmazon);
        lookForAmazon();
    }

    private void lookForAmazon() {
        if (leftOnAmazon || holding) return;
        if (!Fire.STICK_2020) {
            answered(amazonActive());
            return;
        }
        // The Stick of 2020 can take seconds to answer, and Salotto's Home has to start on the main thread.
        int look = ++lastLook;
        Background.run(() -> {
            boolean there = amazonActive();
            main.post(() -> { if (look == lastLook) answered(there); });
        });
    }

    private boolean amazonActive() {
        AccessibilityNodeInfo active = service.getRootInActiveWindow();
        return active != null && Fire.AMAZON_HOME.contentEquals(active.getPackageName());
    }

    private void answered(boolean there) {
        if (leftOnAmazon || holding) return;
        boolean settling = SystemClock.elapsedRealtime() - startedAt < AMAZON_SETTLE_MS;
        if (there && !settling) {
            show();
            startHome();
        } else if (amazonTries-- > 0) {
            main.postDelayed(lookForAmazon, AMAZON_TRY_MS);
        }
    }

    /** Stays until letGo: `still` over a Fire OS page on its way out, black if there is nothing to show. */
    void hold(Bitmap still) {
        holding = true;
        held = still;
        if (still != null) view.setImageBitmap(still);
        else view.setImageDrawable(new ColorDrawable(Color.BLACK));
        view.invalidate();
        view.setVisibility(View.VISIBLE);
        main.removeCallbacks(hide);
        main.postDelayed(hide, HOLD_LIMIT_MS);
    }

    void letGo() {
        if (holding) hide();
    }

    private void show() {
        Closing where = closing;
        // After an app opened from the wheel its circle closes at once; a second look at Amazon's Home lets it run.
        if (where != null && !holding) {
            if (shrink == null) startShrink(where);
        } else if (picture != null) view.setImageBitmap(picture);
        else view.setImageDrawable(new ColorDrawable(Color.BLACK));
        view.invalidate();
        view.setVisibility(View.VISIBLE);
        main.removeCallbacks(hide);
        main.postDelayed(hide, SHOW_LIMIT_MS);
    }

    /** The Home has drawn itself. */
    void homeBack() {
        if (shrinking) {
            homeIsBack = true;
            return;
        }
        if (shrink != null && view.getVisibility() == View.VISIBLE) {
            hide();
            return;
        }
        if (view.getVisibility() != View.VISIBLE) return;
        main.removeCallbacks(hide);
        main.postDelayed(hide, AFTER_HOME_MS);
    }

    private void hide() {
        holding = false;
        held = null;
        main.removeCallbacks(hide);
        view.setVisibility(View.GONE);
        if (shrink != null) {
            shrink = null;
            closing = null;
        }
        Runnable then = whenGone;
        whenGone = null;
        if (then != null) then.run();
    }

    /** Gone now: the Home under it shows the same colour. A hold over a Fire OS page is not this cover's to drop. */
    void drop() {
        if (!holding && view.getVisibility() == View.VISIBLE) hide();
    }

    void close() {
        main.removeCallbacks(hide);
        main.removeCallbacks(lookForAmazon);
        lastLook++;
        windows.removeView(view);
    }
}
