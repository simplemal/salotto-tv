package it.simplemal.firehome;

import android.Manifest;
import android.accessibilityservice.AccessibilityService;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.PixelCopy;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;

/**
 * Behind the task manager: opens it when Menu is held, keeps a recent picture of the app in
 * front, and owns the adb connection. A short Menu still reaches the app: nothing is taken from
 * it but the release of a press that ended up opening the task manager. Back held opens the
 * control panel in the same way. It also holds the Veil's
 * window, the only one Salotto can keep above other apps without another permission.
 */
public final class TaskService extends AccessibilityService {
    private static final long HOLD_MS = 700;
    private static final long STOP_MASK_MS = 800;
    private static final long INSTALL_POLL_MS = 200;
    private static final int INSTALL_TRIES = 50;
    private static final long COVER_AFTER_MS = 250;
    private static final String MASK_DIM = "dim";
    private static final String MASK_WORKING = "working";
    static final int STOP_FAILED = 0;
    static final int STOP_DONE = 1;
    static final int STOP_UP = 2;
    static final int STOP_BACK = 3;
    private static final String MASK_TITLE = "title";
    private static final String MASK_WHEEL = "wheel";
    private static final long ANSWER_WAIT_MS = 30000;
    private static final long ANSWER_POLL_MS = 2000;
    private static final String DOUBLE_HOME = "double_home";
    private static final long QUICK_MENU_ONCE_MS = 2000;
    private static final long QUICK_MENU_CLOSE_MS = 250;
    private static final long QUICK_MENU_FOCUS_MS = 400;
    private static final String ENABLED_SERVICES = Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES;
    private static final String KEY_LIFETIME = "adb_allowed_connection_time";
    private static final String KEYS_FILE = "keys";
    private static final String KEYS_CHOSEN = "chosen";
    private static final long FIRST_PICTURE_MS = 3000;
    private static final long PICTURE_EVERY_MS = 20000;
    /** Protected video never gives a picture: the capture is asked for less and less while it plays. */
    private static final long PICTURE_RAREST_MS = 5 * Fire.MINUTE_MS;
    private static final long PICTURE_WAIT_MS = 600;
    private static final long RETRY_EVERY_MS = Fire.MINUTE_MS;
    /** Protected video reaches a capture as a black frame: the card art is better than that. */
    private static final int BLACK_LEVEL = 10;
    /** About 16 by 9 pixels: enough to tell a black frame, and far quicker to decode than the whole of it. */
    private static final int SAMPLE_SIZE = 32;
    private static final String NOTHING = "none";
    private static final String TAG = "Salotto";
    /** Fire OS closes adb connections made from the TV itself while this is off, and turns it off at every reboot. */
    private static final String WIRELESS_DEBUGGING = "adb_wifi_enabled";
    private static final int WHEEL_DP = 40;
    /**
     * The key's own line comes first, but can come a second late; Amazon's Home starting never does.
     * Home held shows as Fire OS sending its long press, whether or not its quick menu answers.
     */
    private static final String LOG_FILTER = "HOME_PRESSED|START.u0.*cat=.android.intent.category.HOME.*cmp="
        + Fire.AMAZON_HOME + "|sendLongHomeBroadcast";
    private static final String HOME_LOG = "logcat -v brief -T 1 -e '" + LOG_FILTER + "'";
    private static final String HOME_PRESSED = "HOME_PRESSED";
    private static final String HOME_HELD = "sendLongHomeBroadcast";
    private static final long HOME_LOG_START_MS = 1000;
    private static final long HOME_LOG_RETRY_MS = 30000;
    /** From distro/config, the same lists the installer offers. */

    private static volatile TaskService running;
    private static volatile boolean fireSettingsWanted;
    private static long sleepAt;
    private static volatile long lastRetry;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable picture = this::takePicture;
    private final Runnable holdMenu = this::menuHeld;
    private final Runnable holdBack = this::backHeld;
    private final Runnable sleepNow = this::sleep;
    /** The timer belongs to one evening: going to standby in any other way ends it. */
    private final BroadcastReceiver screenOff = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            setSleepTimer(0);
        }
    };
    private HandlerThread worker;
    private Handler adb;
    private volatile AdbClient client;
    private volatile Runnable homeLog;
    private boolean refused;
    private String wordsLanguage;
    private Resources words;
    private boolean waitsAnswer;
    private boolean keyFired;
    private FireSteps fireSteps;
    private FrameLayout installMask;
    private IntConsumer stopAnswer;
    private volatile boolean stopReady;
    private int stopLeaving;
    private long quickMenuAt;
    private String front;
    private View veil;
    private HomeCover cover;
    private volatile long pictureEvery = PICTURE_EVERY_MS;

    /** Does `work` on the adb thread and answers on the main one; `off` when the service is not on. */
    private static <T> void onAdb(T off, Function<TaskService, T> work, Consumer<T> done) {
        TaskService service = running;
        if (service == null) {
            done.accept(off);
            return;
        }
        service.adb.post(() -> {
            T result = work.apply(service);
            service.main.post(() -> done.accept(result));
        });
    }

    static boolean running() { return running != null; }

    /** Fire OS's Settings stay open until Salotto is in front again. */
    static void wantFireSettings() { fireSettingsWanted = true; }

    static boolean ready() {
        TaskService service = running;
        return service != null && service.client != null;
    }

    /** Runs a command as the shell user. Null without a connection, or if the helper refused. */
    static void shell(String command, Consumer<String> done) {
        onAdb(null, service -> service.run(command), done);
    }

    /** For a press on the remote: the TV may ask about debugging, and the command waits for the answer. */
    static void shellAsked(String command, Consumer<String> done) {
        onAdb(null, service -> {
            service.refused = false;
            service.waitsAnswer = true;
            String output = service.run(command);
            service.waitsAnswer = false;
            return output;
        }, done);
    }

    /**
     * Fire OS's Force stop, under a mask: adb would make the Stick ask about debugging. Answers
     * false if it did not happen.
     */
    static void stop(String pkg, Consumer<Boolean> done) {
        TaskService service = running;
        if (service == null) {
            done.accept(false);
            return;
        }
        String name = HomeApps.label(service, pkg);
        Intent page = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg));
        service.onFirePage(page, new String[][]{Fire.FORCE_STOP}, new String[]{service.getString(R.string.task_press_to_close, name)},
            service.getString(R.string.task_closing, name), done);
    }

    /**
     * The X of the task manager is selected: Fire OS's page for this app opens under a picture of
     * the task manager, with Force stop selected, so that the OK on the X is the real press. Those
     * pages take nothing but the real key.
     */
    static void prepareStop(Activity page, String pkg, IntConsumer answer) {
        TaskService service = running;
        if (service == null) {
            answer.accept(STOP_FAILED);
            return;
        }
        DisplayMetrics screen = page.getResources().getDisplayMetrics();
        Bitmap picture = Bitmap.createBitmap(screen.widthPixels / 2, screen.heightPixels / 2, Bitmap.Config.ARGB_8888);
        PixelCopy.request(page.getWindow(), picture, result -> service.coverForStop(picture, pkg, answer), service.main);
    }

    private void coverForStop(Bitmap picture, String pkg, IntConsumer answer) {
        FrameLayout cover = mask(getString(R.string.task_closing, HomeApps.label(this, pkg)), picture);
        View working = cover.findViewWithTag(MASK_WORKING);
        working.setVisibility(View.GONE);
        cover.findViewWithTag(MASK_DIM).setVisibility(View.GONE);
        stopAnswer = answer;
        stopReady = false;
        stopLeaving = STOP_FAILED;
        FireSteps steps = new FireSteps(this, main);
        fireSteps = steps;
        Intent page = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg));
        steps.run(page, new String[][]{Fire.FORCE_STOP}, step -> stopReady = true, pressed -> {
            fireSteps = null;
            if (pressed) {
                cover.findViewWithTag(MASK_DIM).setVisibility(View.VISIBLE);
                working.setVisibility(View.VISIBLE);
            }
            int result = pressed ? STOP_DONE : stopLeaving;
            main.postDelayed(() -> steps.leave(() -> main.postDelayed(() -> {
                getSystemService(WindowManager.class).removeView(cover);
                stopAnswer = null;
                answer.accept(result);
            }, COVER_AFTER_MS)), pressed ? STOP_MASK_MS : 0);
        });
    }

    /** Only OK reaches the page, and only once Force stop is selected: up and Back leave the X. */
    private boolean keyOnPreparedStop(int key, boolean ok, boolean up) {
        if (ok) {
            if (stopReady && up && fireSteps != null) fireSteps.okPressed();
            return !stopReady;
        }
        boolean leaves = key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_BACK;
        if (leaves && up && fireSteps != null) {
            stopLeaving = key == KeyEvent.KEYCODE_BACK ? STOP_BACK : STOP_UP;
            fireSteps.cancel();
        }
        return true;
    }

    /** Fire OS's own Restart, whose pages stay under the mask until the screen goes off. */
    static void restart() {
        TaskService service = running;
        if (service == null) return;
        service.onFirePage(new Intent(Fire.DEVICE_PAGE), new String[][]{Fire.RESTART, Fire.RESTART},
            new String[]{service.getString(R.string.task_restart_confirm), service.getString(R.string.task_restart_again)},
            service.getString(R.string.task_restarting), null);
    }

    /** `done` null: what comes next is the end of the screen, so the mask stays. Back gives up. */
    private void onFirePage(Intent page, String[][] entries, String[] asks, String working, Consumer<Boolean> done) {
        FrameLayout mask = mask(asks[0], HomeCover.lastPicture());
        TextView title = mask.findViewWithTag(MASK_TITLE);
        View wheel = mask.findViewWithTag(MASK_WHEEL);
        wheel.setVisibility(View.INVISIBLE);
        FireSteps steps = new FireSteps(this, main);
        fireSteps = steps;
        steps.run(page, entries, step -> {
            title.setText(asks[step]);
            wheel.setVisibility(View.INVISIBLE);
        }, pressed -> {
            fireSteps = null;
            if (pressed && done == null) {
                title.setText(working);
                wheel.setVisibility(View.VISIBLE);
                return;
            }
            if (pressed) {
                title.setText(working);
                wheel.setVisibility(View.VISIBLE);
            }
            main.postDelayed(() -> steps.leave(() -> {
                getSystemService(WindowManager.class).removeView(mask);
                if (done != null) done.accept(pressed);
            }), pressed ? STOP_MASK_MS : 0);
        });
    }

    private FrameLayout mask(String words, Bitmap picture) {
        FrameLayout mask = new FrameLayout(this);
        mask.setBackgroundColor(Color.BLACK);
        if (picture != null) {
            ImageView home = new ImageView(this);
            home.setScaleType(ImageView.ScaleType.FIT_XY);
            home.setImageBitmap(picture);
            mask.addView(home, new FrameLayout.LayoutParams(-1, -1));
            View dim = new View(this);
            dim.setTag(MASK_DIM);
            dim.setBackgroundColor(Color.argb(204, 0, 0, 0));
            mask.addView(dim, new FrameLayout.LayoutParams(-1, -1));
        }
        float density = getResources().getDisplayMetrics().density;
        LinearLayout middle = new LinearLayout(this);
        middle.setOrientation(LinearLayout.VERTICAL);
        middle.setTag(MASK_WORKING);
        middle.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView title = new TextView(this);
        title.setText(words);
        title.setTag(MASK_TITLE);
        title.setTextColor(Color.WHITE);
        title.setTextSize(24);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        middle.addView(title, new LinearLayout.LayoutParams(-2, -2));
        ProgressBar wheel = new ProgressBar(this);
        wheel.setIndeterminateTintList(ColorStateList.valueOf(Color.WHITE));
        wheel.setTag(MASK_WHEEL);
        int size = Math.round(WHEEL_DP * density);
        LinearLayout.LayoutParams place = new LinearLayout.LayoutParams(size, size);
        place.topMargin = Math.round(24 * density);
        middle.addView(wheel, place);
        mask.addView(middle, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        addMask(mask);
        return mask;
    }

    /** Over Android's question, the update page as it was: nothing seems to change. */
    private FrameLayout pageMask(Bitmap page) {
        FrameLayout mask = new FrameLayout(this);
        ImageView picture = new ImageView(this);
        picture.setScaleType(ImageView.ScaleType.FIT_XY);
        picture.setImageBitmap(page);
        mask.addView(picture, new FrameLayout.LayoutParams(-1, -1));
        addMask(mask);
        return mask;
    }

    private void addMask(FrameLayout mask) {
        getSystemService(WindowManager.class).addView(mask, new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.OPAQUE));
    }

    /** Rounded up, so a timer still running never reads zero. */
    static int sleepMinutesLeft() {
        long left = sleepAt - System.currentTimeMillis();
        return left <= 0 ? 0 : (int) ((left + Fire.MINUTE_MS - 1) / Fire.MINUTE_MS);
    }

    /** Zero turns the timer off. */
    static void setSleepTimer(int minutes) {
        TaskService service = running;
        if (service == null) return;
        service.main.removeCallbacks(service.sleepNow);
        sleepAt = minutes == 0 ? 0 : System.currentTimeMillis() + minutes * Fire.MINUTE_MS;
        if (minutes > 0) service.main.postDelayed(service.sleepNow, minutes * Fire.MINUTE_MS);
    }

    private void sleep() {
        sleepAt = 0;
        standby();
    }

    /** Without adb, so that it asks nothing on the TV. */
    static void standby() {
        TaskService service = running;
        if (service == null) return;
        if (service.client != null) shell(Fire.CMD_SLEEP, output -> { });
        else service.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
    }

    static void rememberDoubleHome(Context context, boolean on) {
        HomeCover.doubleHome = on;
        context.getSharedPreferences(KEYS_FILE, MODE_PRIVATE).edit().putBoolean(DOUBLE_HOME, on).apply();
    }

    static void homeBack() {
        TaskService service = running;
        if (service != null && service.cover != null) service.cover.homeBack();
    }

    /** Applies what Veil says now. */
    static void veilChanged() {
        TaskService service = running;
        if (service != null) service.showVeil();
    }

    /** One window that never takes the focus or a touch, and is drawn once. */
    private void showVeil() {
        int color = Veil.color(this);
        WindowManager windows = getSystemService(WindowManager.class);
        if (color == Color.TRANSPARENT) {
            if (veil != null) windows.removeView(veil);
            veil = null;
            return;
        }
        if (veil == null) {
            veil = new View(this);
            windows.addView(veil, new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT));
        }
        veil.setBackgroundColor(color);
    }

    /** Hands the way back over to the shell. Salotto ends soon after this answers. */
    static void rollback(String apk, String verdict, Consumer<Boolean> done) {
        onAdb(false, service -> "ok".equals(service.ask("rollback " + apk + " " + verdict)), done);
    }

    /**
     * Android asks before an app updates itself. This answers only for the update just accepted on
     * UpdateActivity, under a mask: the mask goes with Salotto when the new version takes over.
     */
    static void pressInstall() {
        TaskService service = running;
        if (service == null) return;
        service.main.post(() -> {
            service.installMask = service.pageMask(Cover.page());
            service.pressInstall(INSTALL_TRIES);
        });
    }

    private void pressInstall(int tries) {
        if (installMask == null) return;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root != null && Fire.INSTALLER.contentEquals(String.valueOf(root.getPackageName()))) {
            for (AccessibilityNodeInfo button : root.findAccessibilityNodeInfosByViewId(Fire.INSTALL_BUTTON)) {
                if (button.isEnabled() && button.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return;
            }
        }
        if (tries > 0) main.postDelayed(() -> pressInstall(tries - 1), INSTALL_POLL_MS);
        else installEnded();
    }

    /** With the mask gone the person can still answer Android's window. */
    static void installEnded() {
        TaskService service = running;
        if (service == null) return;
        service.main.post(() -> {
            if (service.installMask != null) service.getSystemService(WindowManager.class).removeView(service.installMask);
            service.installMask = null;
        });
    }

    /** What is on screen now, or null without a connection. */
    static void screen(Consumer<Bitmap> done) {
        onAdb(null, service -> service.client == null ? null : decode(service.ask("shot")), done);
    }

    private static Bitmap decode(String reply) {
        if (NOTHING.equals(reply)) return null;
        byte[] jpeg = Base64.decode(reply, Base64.DEFAULT);
        return BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length);
    }

    /** Lets a connection refused on the TV be tried again, now that someone asked: once a minute, a try is costly. */
    static void retry() {
        TaskService service = running;
        if (service == null || service.client != null) return;
        long now = SystemClock.elapsedRealtime();
        if (lastRetry != 0 && now - lastRetry < RETRY_EVERY_MS) return;
        lastRetry = now;
        service.adb.post(() -> {
            service.refused = false;
            service.connected();
        });
    }

    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(Language.apply(base));
    }

    /** A service outlives a change of language: its words are looked up each time. */
    @Override public Resources getResources() {
        String code = Language.code(getBaseContext());
        if (!code.equals(wordsLanguage)) {
            wordsLanguage = code;
            words = Language.apply(getBaseContext()).getResources();
        }
        return words;
    }

    @Override protected void onServiceConnected() {
        worker = new HandlerThread("adb");
        worker.start();
        adb = new Handler(worker.getLooper());
        running = this;
        registerReceiver(screenOff, new IntentFilter(Intent.ACTION_SCREEN_OFF));
        cover = new HomeCover(this);
        showVeil();
        HomeCover.doubleHome = getSharedPreferences(KEYS_FILE, MODE_PRIVATE).getBoolean(DOUBLE_HOME, false);
        startHomeLog();
        if (Updater.installed(this)) {
            startActivity(new Intent(this, UpdateActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION)
                .putExtra(UpdateActivity.EXTRA_AFTER, true));
        }
    }

    @Override public void onDestroy() {
        running = null;
        sleepAt = 0;
        unregisterReceiver(screenOff);
        main.removeCallbacksAndMessages(null);
        if (cover != null) cover.close();
        if (homeLog != null) homeLog.run();
        if (worker != null) {
            adb.post(this::disconnect);
            worker.quitSafely();
        }
        super.onDestroy();
    }

    @Override public void onInterrupt() {}

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        CharSequence pkg = event.getPackageName();
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || pkg == null) return;
        String name = pkg.toString();
        if (name.equals(Fire.AMAZON_HOME) && homeLog == null) cover.pressed();
        if (name.equals(Fire.QUICK_MENU) && client == null) quickMenuShown();
        // It closes by itself once something else comes in front.
        if (Fire.HOME_HELD_PAGE.contentEquals(String.valueOf(event.getClassName()))) openPanel();
        if (name.equals(getPackageName())) fireSettingsWanted = false;
        // A remote's Settings key opens Fire OS's page, from any app: only the link in Salotto's stays.
        if (!fireSettingsWanted && Fire.SETTINGS_PAGE.contentEquals(String.valueOf(event.getClassName()))) openSettings();
        if (name.equals(front)) return;
        if (HomeApps.isUserApp(this, name)) front = name;
        else if (HomeApps.isHome(this, name)) front = null;
        else return;
        main.removeCallbacks(picture);
        pictureEvery = PICTURE_EVERY_MS;
        if (front != null) main.postDelayed(picture, FIRST_PICTURE_MS);
    }

    @Override protected boolean onKeyEvent(KeyEvent event) {
        int key = event.getKeyCode();
        boolean ok = key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER;
        boolean up = event.getAction() == KeyEvent.ACTION_UP;
        if (stopAnswer != null) return keyOnPreparedStop(key, ok, up);
        if (ok && up && fireSteps != null) fireSteps.okPressed();
        if (key != KeyEvent.KEYCODE_MENU && key != KeyEvent.KEYCODE_BACK) return false;
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getRepeatCount() == 0 && !TasksActivity.showing && !PanelActivity.showing) {
                keyFired = false;
                main.postDelayed(key == KeyEvent.KEYCODE_MENU ? holdMenu : holdBack, HOLD_MS);
            }
            return false;
        }
        main.removeCallbacks(holdMenu);
        main.removeCallbacks(holdBack);
        if (key == KeyEvent.KEYCODE_BACK && fireSteps != null) fireSteps.cancel();
        boolean fired = keyFired;
        keyFired = false;
        if (fired && key == KeyEvent.KEYCODE_BACK && TasksActivity.showing) endBack();
        // Back's release always goes on: Android TV reads Back with OK as a shortcut, and a Back
        // that never ends swallows every OK after it.
        return fired && key == KeyEvent.KEYCODE_MENU;
    }

    /** Stopped this early, Fire OS's menu never reaches the screen. It starts again next time. */
    private void homeHeld() {
        if (client != null) adb.post(() -> run("cmd activity force-stop " + Fire.QUICK_MENU));
        else cover.overQuickMenu();
        openPanel();
    }

    /** Without adb the menu can only be closed once it is up: it shows for a moment. */
    private void quickMenuShown() {
        long now = SystemClock.elapsedRealtime();
        if (now - quickMenuAt < QUICK_MENU_ONCE_MS) return;
        quickMenuAt = now;
        // Sooner than this the menu does not have the focus yet, and Back would miss it.
        main.postDelayed(() -> performGlobalAction(GLOBAL_ACTION_BACK), QUICK_MENU_FOCUS_MS);
        if (homeLog == null) main.postDelayed(this::openPanel, QUICK_MENU_FOCUS_MS + QUICK_MENU_CLOSE_MS);
    }

    private void openPanel() {
        // Fire OS's own page may already have paused the Home: `front` still says where this was.
        Intent panel = MainActivity.showing || front == null
            ? new Intent(this, MainActivity.class).putExtra(MainActivity.OPEN_PANEL, true)
            : new Intent(this, PanelActivity.class);
        startActivity(panel.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    static boolean keysOn(Context context) {
        String services = Settings.Secure.getString(context.getContentResolver(), ENABLED_SERVICES);
        return services != null && Arrays.asList(services.split(":")).contains(serviceName(context));
    }

    /** Keeps every other service exactly as it is: Fixer's is what sends the Home key to Salotto. */
    static void setKeys(Context context, boolean on) {
        String current = Settings.Secure.getString(context.getContentResolver(), ENABLED_SERVICES);
        List<String> services = new ArrayList<>();
        if (current != null) {
            for (String service : current.split(":")) {
                if (!service.isEmpty() && !service.equals(serviceName(context))) services.add(service);
            }
        }
        if (on) services.add(serviceName(context));
        Settings.Secure.putString(context.getContentResolver(), ENABLED_SERVICES, TextUtils.join(":", services));
        Settings.Secure.putInt(context.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 1);
    }

    /**
     * Android forgets an adb key after a week, by the clock. After a restart this Stick starts weeks
     * behind and then jumps ahead, which makes the key look expired. Zero means it never expires.
     * The user chose this on 2026-10-31, knowing that it also keeps other approved computers.
     */
    static void keepAdbKey(Context context) {
        try {
            Settings.Global.putLong(context.getContentResolver(), KEY_LIFETIME, 0);
        } catch (SecurityException e) {
            Log.w(TAG, "No permission yet for the adb key", e);
        }
    }

    /** On from the first start. Later the choice is the one in the Settings. */
    static void keysByDefault(Context context) {
        SharedPreferences first = context.getSharedPreferences(KEYS_FILE, Context.MODE_PRIVATE);
        if (first.getBoolean(KEYS_CHOSEN, false)) return;
        try {
            if (!keysOn(context)) setKeys(context, true);
            first.edit().putBoolean(KEYS_CHOSEN, true).apply();
        } catch (SecurityException e) {
            Log.w(TAG, "No permission yet for the keys", e);
        }
    }

    private static String serviceName(Context context) {
        return context.getPackageName() + "/" + TaskService.class.getName();
    }

    private void menuHeld() {
        keyFired = true;
        openSettings();
    }

    private void openSettings() {
        startActivity(new Intent(this, SettingsActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP));
    }

    /**
     * Focus moves while Back is down, so the system never closes that press, and the OK keys on
     * the task manager would count as Back with OK. A whole Back of our own closes it there.
     */
    private void endBack() {
        TasksActivity.ownBack = true;
        performGlobalAction(GLOBAL_ACTION_BACK);
    }

    private void backHeld() {
        keyFired = true;
        String pkg = front;
        if (pkg == null || client == null) {
            openTasks();
            return;
        }
        // The picture must be taken before the task manager covers the app.
        AtomicBoolean opened = new AtomicBoolean();
        Runnable open = () -> {
            if (opened.compareAndSet(false, true)) openTasks();
        };
        main.postDelayed(open, PICTURE_WAIT_MS);
        adb.post(() -> {
            save(pkg);
            main.post(open);
        });
    }

    private void openTasks() {
        startActivity(new Intent(this, TasksActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private void takePicture() {
        String pkg = front;
        if (pkg == null) return;
        if (getSystemService(PowerManager.class).isInteractive()) {
            adb.post(() -> pictureEvery = save(pkg) ? PICTURE_EVERY_MS : Math.min(2 * pictureEvery, PICTURE_RAREST_MS));
        }
        main.postDelayed(picture, pictureEvery);
    }

    // Everything below runs on the adb thread.

    private boolean connected() {
        if (client != null) return true;
        if (refused) return false;
        AdbClient opened = null;
        try {
            boolean asks = waitsAnswer || Settings.Global.getInt(getContentResolver(), WIRELESS_DEBUGGING, 0) == 0;
            Settings.Global.putInt(getContentResolver(), WIRELESS_DEBUGGING, 1);
            opened = open(asks);
            opened.openShell("CLASSPATH=" + getPackageCodePath() + " app_process / " + ShellHelper.class.getName());
            if (!"ready".equals(opened.readLine())) throw new IOException("Helper did not start");
            client = opened;
            String fixer = run(Fire.FIXER_COMMAND + "state");
            rememberDoubleHome(this, fixer != null && fixer.contains(Fire.DOUBLE_HOME_ON));
            startHomeLog();
            return true;
        } catch (IOException | GeneralSecurityException | SecurityException e) {
            Log.w(TAG, "No adb connection", e);
            if (opened != null) opened.close();
            refused = true;
            return false;
        }
    }

    /** Android asks on the TV the first time after each restart: the answer takes a moment. */
    private AdbClient open(boolean asks) throws IOException, GeneralSecurityException {
        long limit = SystemClock.elapsedRealtime() + (asks ? ANSWER_WAIT_MS : 0);
        while (true) {
            try {
                return AdbClient.connect(this);
            } catch (IOException e) {
                boolean refused = Settings.Global.getInt(getContentResolver(), WIRELESS_DEBUGGING, 0) == 0;
                if (refused || SystemClock.elapsedRealtime() >= limit) throw e;
                SystemClock.sleep(ANSWER_POLL_MS);
            }
        }
    }

    /**
     * READ_LOGS, given at installation, needs no adb: nothing is asked on the TV at startup. Without
     * it the log takes an adb connection of its own, once there is one.
     */
    private void startHomeLog() {
        boolean own = checkSelfPermission(Manifest.permission.READ_LOGS) == PackageManager.PERMISSION_GRANTED;
        if (homeLog != null || !own && client == null) return;
        Thread thread = new Thread(own ? this::watchOwnLog : this::watchHomeLog, "home-log");
        thread.setDaemon(true);
        thread.start();
    }

    private void watchOwnLog() {
        try {
            Process log = new ProcessBuilder("logcat", "-v", "brief", "-T", "1", "-e", LOG_FILTER).start();
            homeLog = log::destroy;
            BufferedReader lines = new BufferedReader(new InputStreamReader(log.getInputStream(), StandardCharsets.UTF_8));
            follow(lines::readLine);
        } catch (IOException e) {
            Log.w(TAG, "No Home log", e);
        }
        afterHomeLog();
    }

    private void watchHomeLog() {
        try (AdbClient log = AdbClient.connect(this)) {
            log.openShell(HOME_LOG);
            log.waitForever();
            homeLog = log::close;
            follow(log::readLine);
        } catch (IOException | GeneralSecurityException e) {
            Log.w(TAG, "No Home log", e);
        }
        afterHomeLog();
    }

    private void follow(LogLines lines) throws IOException {
        long started = SystemClock.elapsedRealtime();
        for (String line; (line = lines.next()) != null; ) {
            // logcat first repeats the last line it has: that press is long over.
            if (SystemClock.elapsedRealtime() - started <= HOME_LOG_START_MS) continue;
            if (line.contains(HOME_HELD)) {
                main.post(this::homeHeld);
            } else {
                int side = line.contains(HOME_PRESSED) ? 1 : -1;
                main.post(() -> {
                    if (cover != null) cover.heard(side);
                });
            }
        }
    }

    private void afterHomeLog() {
        homeLog = null;
        if (running == this) main.postDelayed(this::startHomeLog, HOME_LOG_RETRY_MS);
    }

    private interface LogLines {
        String next() throws IOException;
    }

    private String run(String command) {
        String reply = ask("run " + Base64.encodeToString(command.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP));
        return NOTHING.equals(reply) ? null
            : new String(Base64.decode(reply, Base64.DEFAULT), StandardCharsets.UTF_8);
    }

    /** Asked for by the person, who was told that the TV may ask about debugging first. */
    static void turnOffAgain(List<String> names, Consumer<Boolean> done) {
        TaskService service = running;
        if (service == null) {
            done.accept(false);
            return;
        }
        service.adb.post(() -> {
            service.refused = false;
            boolean connected = service.connected();
            if (connected) {
                for (String name : names) service.run("pm disable-user --user 0 " + name);
            }
            service.main.post(() -> done.accept(connected));
        });
    }

    private void disconnect() {
        if (client != null) client.close();
        client = null;
    }

    private String ask(String line) {
        if (!connected()) return NOTHING;
        try {
            client.writeLine(line);
            return client.readLine();
        } catch (IOException e) {
            disconnect();
            return NOTHING;
        }
    }

    /** True when the app gave a picture worth keeping. */
    private boolean save(String pkg) {
        if (client == null) return false;
        String reply = ask("shot");
        if (NOTHING.equals(reply)) return false;
        byte[] jpeg = Base64.decode(reply, Base64.DEFAULT);
        BitmapFactory.Options small = new BitmapFactory.Options();
        small.inSampleSize = SAMPLE_SIZE;
        Bitmap sample = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length, small);
        if (sample == null) return false;
        boolean black = isBlack(sample);
        sample.recycle();
        if (black) return false;
        File file = RunningApps.picture(this, pkg);
        try {
            file.getParentFile().mkdirs();
            Files.write(file.toPath(), jpeg);
        } catch (IOException e) {
            file.delete();
        }
        return true;
    }

    private static boolean isBlack(Bitmap sample) {
        int brightest = 0;
        for (int y = 0; y < sample.getHeight(); y++) {
            for (int x = 0; x < sample.getWidth(); x++) {
                int pixel = sample.getPixel(x, y);
                brightest = Math.max(brightest, Math.max(Color.red(pixel), Math.max(Color.green(pixel), Color.blue(pixel))));
            }
        }
        return brightest < BLACK_LEVEL;
    }
}
