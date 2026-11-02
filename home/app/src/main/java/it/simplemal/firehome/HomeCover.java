package it.simplemal.firehome;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
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
    private static final long SHOW_LIMIT_MS = 1200;
    private static final long HOLD_LIMIT_MS = 3000;
    private static final long DOUBLE_PRESS_MS = 900;
    private static final int AMAZON_TRIES = 30;
    private static final long AMAZON_TRY_MS = 50;
    /** The Home just started needs this long to take over from Amazon's. */
    private static final long AMAZON_SETTLE_MS = 600;

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

    /** From the Home, as it leaves the screen: half size, the cover is up for a moment only. */
    static void take(Window window) { take(window, () -> { }); }

    private static void take(Window window, Runnable taken) {
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
        service.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            .setClass(service, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
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
        AccessibilityNodeInfo active = service.getRootInActiveWindow();
        boolean there = active != null && Fire.AMAZON_HOME.contentEquals(active.getPackageName());
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
        if (picture != null) view.setImageBitmap(picture);
        else view.setImageDrawable(new ColorDrawable(Color.BLACK));
        view.invalidate();
        view.setVisibility(View.VISIBLE);
        main.removeCallbacks(hide);
        main.postDelayed(hide, SHOW_LIMIT_MS);
    }

    /** The Home has drawn itself. */
    void homeBack() {
        if (view.getVisibility() != View.VISIBLE) return;
        main.removeCallbacks(hide);
        main.postDelayed(hide, AFTER_HOME_MS);
    }

    private void hide() {
        holding = false;
        held = null;
        main.removeCallbacks(hide);
        view.setVisibility(View.GONE);
    }

    void close() {
        main.removeCallbacks(hide);
        main.removeCallbacks(lookForAmazon);
        windows.removeView(view);
    }
}
