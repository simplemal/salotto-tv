package it.simplemal.firehome;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The Home key on Fire OS 6, the Stick 4K of 2018. There Fire OS turns away every accessibility
 * service not signed by Amazon (AmazonA11yAuthorizerCallback.shouldBlockProvider in its services,
 * read on 2026-11-15), so TaskService never runs. This follows the same log line TaskService
 * follows, with READ_LOGS given at installation, and brings Salotto back over Amazon's Home. It
 * starts with the TV and with Salotto, on Android 7 only.
 *
 * Android 7 holds every app back for 5 s after Home, Salotto too: in the meantime the last picture
 * of Salotto's Home covers Amazon's, in a window over the others (SYSTEM_ALERT_WINDOW, given by the
 * installer), which takes the keys so that nothing moves underneath. A wheel in the middle says
 * that Salotto is on its way (the user, 2026-11-15).
 */
public final class HomeWatch extends Service {
    private static final String TAG = "Salotto";
    /** logcat first repeats the last line it has: that press is long over. */
    private static final long START_MS = 1000;
    private static final long RETRY_MS = 30000;
    /** Should the Home never say it is back, the cover goes all the same. */
    private static final long COVER_LIMIT_MS = 8000;
    /** The Home comes in with an animation: the cover stays over it until that has ended. */
    private static final long AFTER_HOME_MS = 300;
    /** As the wheel of «Caricamento della Home». */
    private static final int WHEEL_DP = 40;

    private static volatile HomeWatch running;

    private Handler main;
    private volatile Process log;
    private volatile boolean stopped;
    private View cover;
    private final Runnable uncover = this::uncover;

    static void start(Context context) {
        if (!Fire.ANDROID_7) return;
        try {
            context.startService(new Intent(context, HomeWatch.class));
        } catch (RuntimeException e) {
            Log.w(TAG, "No Home watch", e);
        }
    }

    /** At power on Amazon's Home comes first, before the watch is there to hear it. */
    public static final class Boot extends BroadcastReceiver {
        @Override public void onReceive(Context context, Intent intent) {
            if (!Fire.ANDROID_7 || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
            start(context);
            HomeCover.startSalotto(context, SystemClock.elapsedRealtime());
        }
    }

    /** Salotto's Home is on screen again. */
    static void homeIsBack() {
        HomeWatch watch = running;
        if (watch != null) watch.main.postDelayed(watch.uncover, AFTER_HOME_MS);
    }

    @Override public void onCreate() {
        super.onCreate();
        main = new Handler(Looper.getMainLooper());
        running = this;
        watch();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override public IBinder onBind(Intent intent) {
        return null;
    }

    @Override public void onDestroy() {
        stopped = true;
        if (running == this) running = null;
        uncover();
        Process process = log;
        if (process != null) process.destroy();
        main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void watch() {
        Thread thread = new Thread(this::follow, "home-watch");
        thread.setDaemon(true);
        thread.start();
    }

    private void follow() {
        try {
            List<String> command = new ArrayList<>(Arrays.asList("logcat"));
            command.addAll(Arrays.asList(TaskService.LOG_OPTIONS));
            command.add("*:S");
            Process process = new ProcessBuilder(command).start();
            log = process;
            BufferedReader lines = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            long started = SystemClock.elapsedRealtime();
            for (String line; (line = lines.readLine()) != null; ) {
                if (SystemClock.elapsedRealtime() - started <= START_MS) continue;
                if (TaskService.amazonHome(line)) {
                    main.post(this::cover);
                    HomeCover.startSalotto(this, SystemClock.elapsedRealtime());
                }
            }
        } catch (IOException e) {
            Log.w(TAG, "No Home log", e);
        }
        log = null;
        if (!stopped) main.postDelayed(this::watch, RETRY_MS);
    }

    private void cover() {
        Bitmap picture = HomeCover.lastPicture();
        if (cover != null || picture == null || !Settings.canDrawOverlays(this)) return;
        FrameLayout view = new FrameLayout(this);
        ImageView still = new ImageView(this);
        still.setScaleType(ImageView.ScaleType.FIT_XY);
        still.setImageBitmap(picture);
        view.addView(still, new FrameLayout.LayoutParams(-1, -1));
        ProgressBar wheel = new ProgressBar(this);
        wheel.setIndeterminateTintList(ColorStateList.valueOf(Color.WHITE));
        int side = Math.round(WHEEL_DP * getResources().getDisplayMetrics().density);
        view.addView(wheel, new FrameLayout.LayoutParams(side, side, Gravity.CENTER));
        view.setFocusable(true);
        view.setOnKeyListener((v, code, event) -> true);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.OPAQUE);
        try {
            getSystemService(WindowManager.class).addView(view, params);
        } catch (RuntimeException e) {
            Log.w(TAG, "No cover over Amazon's Home", e);
            return;
        }
        cover = view;
        view.requestFocus();
        main.postDelayed(uncover, COVER_LIMIT_MS);
    }

    private void uncover() {
        main.removeCallbacks(uncover);
        if (cover == null) return;
        try {
            getSystemService(WindowManager.class).removeView(cover);
        } catch (RuntimeException e) {
            Log.w(TAG, "Cover already gone", e);
        }
        cover = null;
    }
}
