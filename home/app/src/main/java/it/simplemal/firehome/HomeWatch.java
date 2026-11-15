package it.simplemal.firehome;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
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
 */
public final class HomeWatch extends Service {
    private static final String TAG = "Salotto";
    /** logcat first repeats the last line it has: that press is long over. */
    private static final long START_MS = 1000;
    private static final long RETRY_MS = 30000;

    private Handler main;
    private volatile Process log;
    private volatile boolean stopped;

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

    @Override public void onCreate() {
        super.onCreate();
        main = new Handler(Looper.getMainLooper());
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
                if (TaskService.amazonHome(line)) HomeCover.startSalotto(this, SystemClock.elapsedRealtime());
            }
        } catch (IOException e) {
            Log.w(TAG, "No Home log", e);
        }
        log = null;
        if (!stopped) main.postDelayed(this::watch, RETRY_MS);
    }
}
