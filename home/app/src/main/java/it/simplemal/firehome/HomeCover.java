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
    private static final long PAIR_MS = 2000;
    /** Without Fixer no black has to end: only the Home's first frame is waited for. */
    private static final long OWN_AFTER_HOME_MS = 150;
    private static final int AMAZON_TRIES = 10;
    private static final long AMAZON_TRY_MS = 100;

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
    private long lastHeard;
    private int unmatched;
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

    /** The log tells of each press twice, once from each side, in either order. */
    void heard(int side) {
        long now = SystemClock.elapsedRealtime();
        if (now - lastHeard > PAIR_MS) unmatched = 0;
        lastHeard = now;
        boolean first = unmatched * side >= 0;
        unmatched += side;
        if (first) pressed();
    }

    void pressed() {
        long now = SystemClock.elapsedRealtime();
        boolean again = now - lastPress < DOUBLE_PRESS_MS;
        lastPress = now;
        main.removeCallbacks(bringBack);
        twice = again;
        leftOnAmazon = again && doubleHome;
        returning = !leftOnAmazon && (again || !Updater.fixerActive(service));
        if (returning) main.postDelayed(bringBack, BRING_BACK_MS);
        if (leftOnAmazon) hide();
        // Taking a picture now is too late: Fixer's black is already up, half a second before it.
        else if (picture == null && MainActivity.showing && home != null) take(home, this::show);
        else show();
    }

    /** Fixer ignores a second press: it can take two seconds to notice, or leave Amazon's Home up. */
    private void bringBack() {
        returning = false;
        service.startActivity(new Intent(service, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    /** Another app came up, so a double press no longer keeps Amazon's Home. */
    void appOpened() { leftOnAmazon = false; }

    /**
     * Without Fixer: Amazon's Home has a page of its own on screen, at power-on, after a press too
     * quick for the Home brought back, or under an app that ended. It tells of that from behind
     * other apps as well, and a moment before it is the active window, so that is looked at.
     */
    void amazonShown() { amazonShown(AMAZON_TRIES); }

    private void amazonShown(int tries) {
        if (leftOnAmazon || returning || holding) return;
        AccessibilityNodeInfo active = service.getRootInActiveWindow();
        if (active != null && Fire.AMAZON_HOME.contentEquals(active.getPackageName())) {
            show();
            bringBack();
        } else if (tries > 0) {
            main.postDelayed(() -> amazonShown(tries - 1), AMAZON_TRY_MS);
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
        main.postDelayed(hide, LIMIT_MS);
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
        windows.removeView(view);
    }
}
