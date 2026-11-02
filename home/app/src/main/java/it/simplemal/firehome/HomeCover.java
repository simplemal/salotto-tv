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
 * Hides the second Fire OS takes over a Home press. The key never reaches an app: Fire OS brings up
 * its own Home, Fixer covers that in black, and Salotto gets its turn almost a second later. This
 * shows the last picture of Salotto's Home above all of it. TaskService says when: from the system
 * log the moment the key is pressed, or, without adb, when Amazon's Home appears.
 */
final class HomeCover {
    private static final int WIDTH = 960;
    private static final int HEIGHT = 540;
    /** Fixer lifts its black a moment after Salotto appears: this has to stay on top until then. */
    private static final long AFTER_HOME_MS = 800;
    /** If Salotto never comes: a double press, for one, stays on Amazon's Home. */
    private static final long LIMIT_MS = 3000;
    private static final long DOUBLE_PRESS_MS = 900;
    /** Amazon's Home has to be up first, or it would land on top of the Home brought back. */
    private static final long BRING_BACK_MS = 200;
    /** Fixer can keep its black to the limit after a second press, a second and a half and its tail. */
    private static final long FIXER_BLACK_MS = 2000;
    /** Without Fixer no black has to end: only the Home coming in over Amazon's is waited for. */
    private static final long OWN_AFTER_HOME_MS = 300;
    /** A late line of the log finds the Home already there, and nothing then says it has come back. */
    private static final long OWN_LIMIT_MS = 1200;
    private static final int AMAZON_TRIES = 30;
    private static final long AMAZON_TRY_MS = 50;
    /** The Home just brought back needs this long to take over from Amazon's. */
    private static final long AMAZON_SETTLE_MS = 600;

    /** Fixer's double press leaves Amazon's Home on screen, so the cover has to step aside. */
    static volatile boolean doubleHome;
    private static Bitmap picture;
    /** The Home while it is on screen: a press there goes through Amazon's Home just the same. */
    private static Window home;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable hide = this::hide;
    private final AccessibilityService service;
    private final WindowManager windows;
    private final ImageView view;
    private long lastPress;
    private final Runnable bringBack = this::bringBack;
    private boolean returning;
    private boolean twice;
    /** Asked for with a double press: it stays until a press or another app says otherwise. */
    private boolean leftOnAmazon;
    private final Runnable lookForAmazon = this::lookForAmazon;
    private int amazonTries;
    private volatile long broughtBackAt;
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
        boolean again = now - lastHeard < DOUBLE_PRESS_MS;
        lastHeard = now;
        if (!(again && doubleHome) && !Updater.fixerActive(service)) startHome();
        main.post(() -> pressed(again));
    }

    void pressed() { pressed(SystemClock.elapsedRealtime() - lastPress < DOUBLE_PRESS_MS); }

    private void pressed(boolean again) {
        lastPress = SystemClock.elapsedRealtime();
        main.removeCallbacks(bringBack);
        twice = again;
        boolean ours = !Updater.fixerActive(service);
        leftOnAmazon = again && doubleHome;
        returning = !ours && again && !doubleHome;
        if (returning) main.postDelayed(bringBack, BRING_BACK_MS);
        if (leftOnAmazon) hide();
        // Taking a picture now is too late: Fixer's black is already up, half a second before it.
        else if (picture == null && MainActivity.showing && home != null) take(home, this::show);
        else show();
        if (ours && !leftOnAmazon) amazonShown();
    }

    /** Fixer ignores a second press: it can take two seconds to notice, or leave Amazon's Home up. */
    private void bringBack() {
        main.removeCallbacks(bringBack);
        returning = false;
        startHome();
    }

    private void startHome() {
        broughtBackAt = SystemClock.elapsedRealtime();
        service.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            .setClass(service, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    /** Another app came up, so a double press no longer keeps Amazon's Home. */
    void appOpened() { leftOnAmazon = false; }

    /**
     * Without Fixer: Amazon's Home got the screen after all, at power-on, under an app that ended or
     * ahead of a Home started at once. It tells of that from behind other apps as well, so what
     * counts is whether it is the active window.
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
        boolean settling = SystemClock.elapsedRealtime() - broughtBackAt < AMAZON_SETTLE_MS;
        if (there && !settling) {
            show();
            bringBack();
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
        main.postDelayed(hide, LIMIT_MS);
    }

    Bitmap held() { return held; }

    void letGo() {
        if (holding) hide();
    }

    private void show() {
        boolean fixer = Updater.fixerActive(service);
        if (picture == null && fixer) return;
        if (picture != null) view.setImageBitmap(picture);
        else view.setImageDrawable(new ColorDrawable(Color.BLACK));
        view.invalidate();
        view.setVisibility(View.VISIBLE);
        main.removeCallbacks(hide);
        main.postDelayed(hide, fixer ? LIMIT_MS : OWN_LIMIT_MS);
    }

    /** The Home has drawn itself. Before a second press is answered, that is only the first trip ending. */
    void homeBack() {
        if (view.getVisibility() != View.VISIBLE || returning) return;
        long fixer = twice ? FIXER_BLACK_MS - (SystemClock.elapsedRealtime() - lastPress) : 0;
        main.removeCallbacks(hide);
        main.postDelayed(hide, Updater.fixerActive(service) ? Math.max(AFTER_HOME_MS, fixer) : OWN_AFTER_HOME_MS);
    }

    private void hide() {
        holding = false;
        held = null;
        main.removeCallbacks(hide);
        view.setVisibility(View.GONE);
    }

    void close() {
        main.removeCallbacks(hide);
        main.removeCallbacks(bringBack);
        main.removeCallbacks(lookForAmazon);
        windows.removeView(view);
    }
}
