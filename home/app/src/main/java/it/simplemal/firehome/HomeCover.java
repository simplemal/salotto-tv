package it.simplemal.firehome;

import android.accessibilityservice.AccessibilityService;
import android.app.AlarmManager;
import android.app.PendingIntent;
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
import android.widget.FrameLayout;
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
    private static final int AMAZON_TRIES = 30;
    private static final long AMAZON_TRY_MS = 50;
    /** The Home just started needs this long to take over from Amazon's. */
    private static final long AMAZON_SETTLE_MS = Fire.ANDROID_9 ? 1500 : 600;
    private static final long CLOSING_SETTLE_MS = 2000;
    /** A frame or two after the cover is up, before the circle starts to close. */
    private static final long FADE_SETTLE_MS = 50;
    /** The circle waits for the Home; should nothing say it has come, it closes all the same after this. */
    private static final long CLOSE_LIMIT_MS = 2500;

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
    /** The window: the picture, and over it, after an app from the wheel, its closing circle. */
    private final FrameLayout frame;
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
        frame = new FrameLayout(service);
        frame.addView(view, new FrameLayout.LayoutParams(-1, -1));
        frame.setVisibility(View.GONE);
        windows.addView(frame, new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT));
    }

    static boolean ready() { return picture != null; }

    static Bitmap lastPicture() { return picture; }


    static void homeShown(Window window) { home = window; }

    /** The app opened from the wheel: its circle, the colour of its ring, and where it sits. */
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

    private CircleClose layer;
    private boolean shrinking;
    /** When the Home drew itself under the cover; zero while it has not. */
    private volatile long homeBackAt;
    private Runnable whenGone;
    private final Runnable shrink = this::shrink;
    private final Runnable quiet = this::whenQuiet;
    private boolean shrunk;
    /** When the fade in ends. */
    private long fadedAt;
    /** While the circle is closing the remote's arrows would move the wheel under it: the Home ignores them. */
    static volatile boolean busy;

    /**
     * The Home key after an app from the wheel, at once over everything: black with the app's circle,
     * small in its place, then a ring of its colour closing onto it over the Home's picture.
     */
    private void startClose(Closing where) {
        busy = true;
        view.setImageBitmap(picture);
        if (picture == null) view.setImageDrawable(new ColorDrawable(Color.BLACK));
        // The whole screen black at once, the app's circle small in its place.
        // It stays still until the Home is there and past Fire OS's animation, so nothing else is
        // being drawn while it closes: started earlier, it stuttered and three dots showed after it.
        layer = new CircleClose(service, where.background, where.art, where.x, where.y, where.radius);
        frame.addView(layer, new FrameLayout.LayoutParams(-1, -1));
        // At once, the user's wish of 2026-11-09: the app's circle must not fade in.
        frame.animate().cancel();
        frame.setAlpha(1f);
        frame.setVisibility(View.VISIBLE);
        fadedAt = SystemClock.uptimeMillis();
        shrinking = true;
        main.removeCallbacks(shrink);
        shrunk = false;
        if (homeBackAt > 0) main.postDelayed(quiet, AFTER_HOME_MS);
        main.postDelayed(shrink, CLOSE_LIMIT_MS);
    }

    /** Once the Home's thread has nothing left to do: then nothing else is drawn while the circle closes. */
    private void whenQuiet() {
        Looper.myQueue().addIdleHandler(() -> {
            main.post(shrink);
            return false;
        });
    }

    private void shrink() {
        // Once only: a second start, from the limit and from the Home both, made it bounce.
        if (layer == null || shrunk) return;
        // Never while it is still fading in: the two together stuttered, as the user saw.
        long fading = fadedAt + FADE_SETTLE_MS - SystemClock.uptimeMillis();
        if (fading > 0) {
            main.removeCallbacks(shrink);
            main.postDelayed(shrink, fading);
            return;
        }
        shrunk = true;
        main.removeCallbacks(shrink);
        main.removeCallbacks(hide);
        main.removeCallbacks(quiet);
        // Around the closing ring, the Home itself once it is there: its last picture, taken as an
        // app opened, has no clock nor buttons, and they would come back after the ring.
        if (homeBackAt > 0) view.setImageDrawable(null);
        layer.close(this::hide);
        // Should the circle never get to its end, the cover goes all the same.
        main.postDelayed(hide, CircleClose.MS + AFTER_HOME_MS);
    }

    /**
     * Back from an app opened from the wheel: the same closing as the Home key's, over everything,
     * so Fire OS's fade from the app never shows. False without an app to close onto.
     */
    boolean closeOverAll() {
        if (layer != null) return true;
        Closing where = closing;
        if (where == null || holding) return false;
        homeBackAt = 0;
        startClose(where);
        return true;
    }

    /** The Home key's closing is being drawn here: the Home leaves it to the cover. */
    boolean closingHere() { return layer != null; }

    /** `then` runs once the cover is gone, at once if it is not up. */
    void whenGone(Runnable then) {
        if (frame.getVisibility() != View.VISIBLE) then.run();
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
        if (!stay) {
            homeBackAt = 0;
            startHome();
        }
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
        // While the circle closes the Home takes longer to come: starting it again resumed it twice under the circle.
        boolean settling = SystemClock.elapsedRealtime() - startedAt < (layer != null ? CLOSING_SETTLE_MS : AMAZON_SETTLE_MS);
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
        frame.animate().cancel();
        frame.setAlpha(1f);
        frame.setVisibility(View.VISIBLE);
        main.removeCallbacks(hide);
        main.postDelayed(hide, HOLD_LIMIT_MS);
    }

    void letGo() {
        if (holding) hide();
    }

    private void show() {
        Closing where = closing;
        // After an app opened from the wheel its circle closes; a second look at Amazon's Home lets it run.
        if (where != null && !holding) {
            if (layer == null) startClose(where);
            return;
        }
        if (picture != null) view.setImageBitmap(picture);
        else view.setImageDrawable(new ColorDrawable(Color.BLACK));
        view.invalidate();
        frame.setVisibility(View.VISIBLE);
        main.removeCallbacks(hide);
        main.postDelayed(hide, SHOW_LIMIT_MS);
    }

    /** The Home has drawn itself. */
    void homeBack() {
        homeBackAt = SystemClock.uptimeMillis();
        // The closing circle starts once the Home is there, and takes the cover away itself.
        if (shrinking) {
            if (shrunk) return;
            main.removeCallbacks(quiet);
            main.postDelayed(quiet, AFTER_HOME_MS);
            return;
        }
        if (frame.getVisibility() != View.VISIBLE) return;
        main.removeCallbacks(hide);
        main.postDelayed(hide, AFTER_HOME_MS);
    }

    private void hide() {
        holding = false;
        held = null;
        main.removeCallbacks(hide);
        main.removeCallbacks(shrink);
        main.removeCallbacks(quiet);
        frame.animate().cancel();
        frame.setAlpha(1f);
        frame.setVisibility(View.GONE);
        if (layer != null) {
            frame.removeView(layer);
            layer = null;
            shrinking = false;
            closing = null;
        }
        busy = false;
        Runnable then = whenGone;
        whenGone = null;
        if (then != null) then.run();
    }

    /** Gone now: the Home under it shows the same colour. A hold over a Fire OS page is not this cover's to drop. */
    void drop() {
        if (!holding && frame.getVisibility() == View.VISIBLE) hide();
    }

    void close() {
        main.removeCallbacks(hide);
        main.removeCallbacks(shrink);
        busy = false;
        main.removeCallbacks(lookForAmazon);
        lastLook++;
        windows.removeView(frame);
    }
}
