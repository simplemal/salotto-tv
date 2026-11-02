package it.simplemal.firehome;

import android.Manifest;
import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Process;
import android.os.SystemClock;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The apps opened since the Stick was turned on and not force-stopped since. Android does not
 * tell an app what is running, so this is what was used. The usage permission comes from adb.
 * Fire OS silently ignores killBackgroundProcesses from a normal app, even on cached processes.
 */
final class RunningApps {
    /** Stopping AirPlay would stop the receiver, and Fire OS has no Force stop for Ambient. */
    private static final List<String> KEPT = Arrays.asList(Fire.AIRPLAY, Fire.AMBIENT);
    private static final String PICTURES = "anteprime";
    /** Between the darkest and the brightest of a real picture, out of 255. */
    private static final int FLAT_RANGE = 40;
    private static final int BLANK_SAMPLES = 16;

    private RunningApps() {}

    private static boolean stopped(Context context, String pkg) {
        try {
            return (context.getPackageManager().getApplicationInfo(pkg, 0).flags & ApplicationInfo.FLAG_STOPPED) != 0;
        } catch (PackageManager.NameNotFoundException e) {
            return true;
        }
    }

    /** The last picture of an app, kept by TaskService. */
    static File picture(Context context, String pkg) {
        return new File(new File(context.getFilesDir(), PICTURES), pkg + ".jpg");
    }

    /**
     * All one tone, black or not: an app still starting shows Android a plain window before its
     * first frame, or a protected one none at all, and on a card that would look like a missing
     * icon. The app's own artwork says more.
     */
    static boolean blank(Bitmap picture) {
        int stepX = Math.max(1, picture.getWidth() / BLANK_SAMPLES);
        int stepY = Math.max(1, picture.getHeight() / BLANK_SAMPLES);
        int darkest = 255;
        int brightest = 0;
        for (int y = 0; y < picture.getHeight(); y += stepY) {
            for (int x = 0; x < picture.getWidth(); x += stepX) {
                int pixel = picture.getPixel(x, y);
                int level = Math.max(Color.red(pixel), Math.max(Color.green(pixel), Color.blue(pixel)));
                darkest = Math.min(darkest, level);
                brightest = Math.max(brightest, level);
            }
        }
        return brightest - darkest < FLAT_RANGE;
    }

    static boolean allowed(Context context) {
        int mode = context.getSystemService(AppOpsManager.class).checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.getPackageName());
        if (mode != AppOpsManager.MODE_DEFAULT) return mode == AppOpsManager.MODE_ALLOWED;
        return context.checkSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS)
            == PackageManager.PERMISSION_GRANTED;
    }

    /** Most recently used first. Blocking: call it away from the main thread. */
    static List<HomeApps.App> list(Context context) {
        long now = System.currentTimeMillis();
        long start = Math.max(now - SystemClock.elapsedRealtime(), now - Fire.DAY_MS);
        UsageEvents events = context.getSystemService(UsageStatsManager.class).queryEvents(start, now);
        Map<String, Long> used = new HashMap<>();
        UsageEvents.Event event = new UsageEvents.Event();
        while (events.hasNextEvent()) {
            events.getNextEvent(event);
            if (event.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                used.put(event.getPackageName(), event.getTimeStamp());
            }
        }
        List<HomeApps.App> apps = new ArrayList<>();
        for (String pkg : used.keySet()) {
            if (KEPT.contains(pkg) || !HomeApps.isUserApp(context, pkg) || stopped(context, pkg)) continue;
            apps.add(new HomeApps.App(pkg, HomeApps.label(context, pkg)));
        }
        Collections.sort(apps, (a, b) -> Long.compare(used.get(b.pkg), used.get(a.pkg)));
        return apps;
    }
}
