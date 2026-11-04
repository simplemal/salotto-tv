package it.simplemal.firehome;

import android.accessibilityservice.AccessibilityService;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
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
    /** As long as the opening's hole took, backwards: slow at first, then closing quickly. */
    private static final long RING_MS = 420;
    /** The hole starts past the farthest corner, where the opening's ended. */
    private static final float RING_START = 1.2f;
    /** The second half, as long as the Home's own closing; the disc starts just past the corners. */
    private static final long DISC_MS = 650;
    private static final float DISC_START = 1.05f;
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
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.OPAQUE));
    }

    static boolean ready() { return picture != null; }

    static Bitmap lastPicture() { return picture; }


    static void homeShown(Window window) { home = window; }

    /** Where and in what colour the Home key closes over an app opened from the wheel. */
    private static final class Closing {
        final int colour;
        final float x;
        final float y;
        final float radius;

        Closing(int colour, float x, float y, float radius) {
            this.colour = colour;
            this.x = x;
            this.y = y;
            this.radius = radius;
        }
    }

    private static volatile Closing closing;

    static void armClose(int colour, float x, float y, float radius) { closing = new Closing(colour, x, y, radius); }

    static void disarm() { closing = null; }

    /**
     * The opening backwards, in one go whatever is under it: a ring of the app's colour closes from
     * past the corners over the app until the screen is all colour (inside it the app's last
     * picture, or a deeper shade); then, over the Home's picture, the colour closes into the app's circle.
     */
    private final class Ring extends Drawable {
        private final Closing where;
        private final Bitmap app;
        private final Paint colour = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint inside = new Paint(Paint.FILTER_BITMAP_FLAG);
        private final Path band = new Path();
        private final RectF whole = new RectF();
        private float progress;

        Ring(Closing where, Bitmap app) {
            this.where = where;
            this.app = app;
            colour.setColor(where.colour);
            inside.setColor(Color.rgb(Color.red(where.colour) / 3, Color.green(where.colour) / 3, Color.blue(where.colour) / 3));
        }

        void setProgress(float value) {
            progress = value;
            invalidateSelf();
        }

        @Override public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            whole.set(bounds);
            float far = (float) Math.hypot(Math.max(where.x, bounds.width() - where.x), Math.max(where.y, bounds.height() - where.y));
            float ringPart = (float) RING_MS / (RING_MS + DISC_MS);
            if (progress < ringPart) {
                float closed = new AccelerateInterpolator(1.4f).getInterpolation(progress / ringPart);
                if (app != null) canvas.drawBitmap(app, null, bounds, inside);
                else canvas.drawRect(whole, inside);
                float hole = far * RING_START * (1 - closed);
                band.rewind();
                band.setFillType(Path.FillType.EVEN_ODD);
                band.addRect(whole, Path.Direction.CW);
                if (hole > 0) band.addCircle(where.x, where.y, hole, Path.Direction.CW);
                canvas.drawPath(band, colour);
                return;
            }
            float shrunk = new DecelerateInterpolator().getInterpolation((progress - ringPart) / (1 - ringPart));
            Bitmap under = picture;
            if (under != null) canvas.drawBitmap(under, null, bounds, inside);
            else canvas.drawRect(whole, inside);
            canvas.drawCircle(where.x, where.y, far * DISC_START + (where.radius - far * DISC_START) * shrunk, colour);
        }

        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(ColorFilter filter) { }
        @Override public int getOpacity() { return PixelFormat.OPAQUE; }
    }

    private Ring ring;
    private boolean ringRunning;
    /** The Home has drawn itself under the ring: it can go as soon as it ends. */
    private boolean homeIsBack;
    /** What waits for the cover to go: the Home's time and weather coming back. */
    private Runnable whenGone;

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
        if (where != null && !holding) {
            // Shown again while it runs, by a second look at Amazon's Home: it carries on.
            if (!ringRunning) closeRing(where);
        } else if (picture != null) {
            view.setImageBitmap(picture);
        } else {
            view.setImageDrawable(new ColorDrawable(Color.BLACK));
        }
        view.invalidate();
        view.setVisibility(View.VISIBLE);
        main.removeCallbacks(hide);
        main.postDelayed(hide, SHOW_LIMIT_MS + (where != null ? RING_MS : 0));
    }

    private void closeRing(Closing where) {
        ring = new Ring(where, ((TaskService) service).frontPicture());
        view.setImageDrawable(ring);
        ringRunning = true;
        homeIsBack = false;
        ValueAnimator close = ValueAnimator.ofFloat(0f, 1f);
        close.setDuration(RING_MS + DISC_MS);
        close.setInterpolator(null);
        close.addUpdateListener(step -> ring.setProgress((float) step.getAnimatedValue()));
        close.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                ringRunning = false;
                // The last frame is the Home's picture: the real one takes over as soon as it is there.
                main.removeCallbacks(hide);
                if (homeIsBack) hide();
                else main.postDelayed(hide, SHOW_LIMIT_MS);
            }
        });
        close.start();
    }

    /** The Home has drawn itself. */
    void homeBack() {
        // The closing takes the cover away itself when it ends, the Home being there by then.
        if (ringRunning) {
            homeIsBack = true;
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
        if (ring != null) {
            ring = null;
            closing = null;
        }
        Runnable then = whenGone;
        whenGone = null;
        if (then != null) then.run();
    }

    /** True while the Home key's closing runs here: the Home leaves the colour to it. */
    boolean closingHere() { return ringRunning; }

    /** `then` runs once the cover is gone, at once if it is not up. */
    void whenGone(Runnable then) {
        if (view.getVisibility() != View.VISIBLE) then.run();
        else whenGone = then;
    }

    void close() {
        main.removeCallbacks(hide);
        main.removeCallbacks(lookForAmazon);
        lastLook++;
        windows.removeView(view);
    }
}
