package it.simplemal.firehome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.text.TextUtils;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** The saved, ordered list of Home apps and the installed apps to choose from. */
final class HomeApps {
    static final class App {
        final String pkg;
        final String label;
        App(String pkg, String label) { this.pkg = pkg; this.label = label; }
    }

    /** Salotto's own settings card, kept in the list so it can be moved like an app. */
    static final String SETTINGS = "salotto:settings";
    /** Taken off the Home by the user: the gear beside the apps still opens the settings. */
    private static final String SETTINGS_OFF = "settings_off";

    private static final String FILE = "home";
    private static final String KEY = "apps";
    private static final String[] DEFAULTS = {
        "com.apple.atve.amazon.appletv",
        "com.disney.disneyplus",
        "com.netflix.ninja",
        "com.nowtv.it",
        "com.plexapp.android",
        "tv.pluto.android",
        "com.amazon.firebat",
        "it.rainet.androidtv",
        "com.amazon.firetv.youtube",
        "com.aurora.store"
    };
    /** Fire OS entries that are listed as launchable but are utilities, or refuse to open from here. */
    private static final List<String> HIDDEN = Arrays.asList(
        "com.amazon.tv.launcher",
        "com.amazon.ftv.profilepicker",
        "com.amazon.ftv.screensaver",
        "com.amazon.firetv.troubleshooting",
        "com.amazon.ssm",
        "com.amazon.tv.earlyaccess");
    private static final Map<String, String> LABELS = new ConcurrentHashMap<>();

    private static boolean watching;
    private static int changes;

    private HomeApps() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /** Once per process, for as long as it lives: only a running app can hear these broadcasts. */
    static void watch(Context context) {
        if (watching) return;
        watching = true;
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package");
        context.getApplicationContext().registerReceiver(new BroadcastReceiver() {
            @Override public void onReceive(Context changed, Intent intent) { changes++; }
        }, filter);
    }

    /** Moves on each time an app is installed, updated or removed. */
    static int changes() { return changes; }

    /** Uninstalled apps stay in the list, so they return to their place if reinstalled. */
    static List<String> load(Context context) {
        String saved = prefs(context).getString(KEY, null);
        List<String> apps = new ArrayList<>();
        if (saved == null) apps.addAll(Arrays.asList(DEFAULTS));
        else if (!saved.isEmpty()) apps.addAll(Arrays.asList(saved.split(",")));
        // Lists from before the card existed get it; one the user took off stays off.
        if (!apps.contains(SETTINGS) && !prefs(context).getBoolean(SETTINGS_OFF, false)) apps.add(SETTINGS);
        return apps;
    }

    static void setSettingsOff(Context context, boolean off) {
        prefs(context).edit().putBoolean(SETTINGS_OFF, off).apply();
    }

    static void save(Context context, List<String> apps) {
        prefs(context).edit().putString(KEY, TextUtils.join(",", apps)).apply();
    }

    /** Alphabetical order for the Home, with Settings kept at the end. */
    static void sort(Context context, List<String> apps) {
        Collator order = Collator.getInstance();
        Collections.sort(apps, (a, b) -> {
            if (SETTINGS.equals(a)) return 1;
            if (SETTINGS.equals(b)) return -1;
            return order.compare(label(context, a), label(context, b));
        });
        save(context, apps);
    }

    /** An app the user opens, as opposed to a utility or a part of the system. */
    static boolean isUserApp(Context context, String pkg) {
        if (isHome(context, pkg)) return false;
        PackageManager manager = context.getPackageManager();
        return manager.getLeanbackLaunchIntentForPackage(pkg) != null
            || manager.getLaunchIntentForPackage(pkg) != null;
    }

    /** Salotto, or the Fire OS screens that take over from an app. */
    static boolean isHome(Context context, String pkg) {
        return pkg.equals(context.getPackageName()) || HIDDEN.contains(pkg);
    }

    static List<App> installed(Context context) {
        PackageManager manager = context.getPackageManager();
        List<App> apps = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        seen.add(context.getPackageName());
        seen.addAll(HIDDEN);
        for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(category);
            for (ResolveInfo info : manager.queryIntentActivities(intent, 0)) {
                String pkg = info.activityInfo.packageName;
                if (seen.add(pkg)) {
                    apps.add(new App(pkg, info.activityInfo.applicationInfo.loadLabel(manager).toString()));
                }
            }
        }
        Collator order = Collator.getInstance();
        Collections.sort(apps, (a, b) -> order.compare(a.label, b.label));
        return apps;
    }

    /** Reading a name opens the app's resources, so it is done once for each version of the app. */
    static String label(Context context, String pkg) {
        PackageManager manager = context.getPackageManager();
        try {
            PackageInfo info = manager.getPackageInfo(pkg, 0);
            return LABELS.computeIfAbsent(pkg + "/" + info.lastUpdateTime,
                key -> info.applicationInfo.loadLabel(manager).toString());
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }
}
