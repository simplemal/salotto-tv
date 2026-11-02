package it.simplemal.firehome;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Color;
import java.util.Arrays;

/** What several screens share about the Stick and the apps around Salotto. */
final class Fire {
    /** The tools read this line too: pubblica-aggiornamento.sh and genera-errori.py. */
    static final String UPDATES_REPO = "simplemal/salotto-tv";
    /** Took the Home key to Salotto on Sticks set up before Salotto did that itself. */
    static final String OLD_FIXER = "io.github.hatim.firestickfixer";
    static final String AIRPLAY = "io.github.jqssun.airplay";
    static final String AMAZON_HOME = "com.amazon.tv.launcher";
    /** Its package also holds the recent apps page: only this one is the Home itself. */
    static final String AMAZON_HOME_PAGE = "com.amazon.tv.launcher.ui.HomeActivity_vNext";
    static final String APPSTORE = "com.amazon.venezia";
    /** The store itself. Its launcher entry only asks Amazon's Home for a tab, and Salotto has replaced that Home. */
    static final String APPSTORE_PAGE = "com.amazon.venezia.ade.FEATURED_LAUNCH";
    static final String QUICK_MENU = "com.amazon.tv.quicksettings.ui";
    static final String SETTINGS = "com.amazon.tv.settings.v2";
    static final String UNINSTALL_PROGRESS = ".television.UninstallAppProgress";
    static final String INSTALLER = "com.android.packageinstaller";
    static final String INSTALL_BUTTON = "android:id/button1";
    static final String DEVICE_PAGE = "com.amazon.device.settings.action.DEVICE";
    static final String[] FORCE_STOP = {"Forza arresto", "Force stop"};
    static final String[] RESTART = {"Riavvia", "Restart"};
    private static final String FORCE_STOP_WORDS = "force_stop";
    private static final String RESTART_WORDS = "restart_settings_title";
    /** What Fire OS opens on a remote's Settings key. */
    static final String SETTINGS_PAGE = "com.amazon.tv.launcher.ui.MainSettingsActivity";
    static final String RECENTS_ACTION = "com.amazon.tv.action.NAVIGATE_TO_RECENTS";
    /** What Fire OS opens on the recent apps key. */
    static final String RECENTS_PAGE = "com.amazon.tv.launcher.deeplink.activity.RecentDeepLinkActivityDI";
    static final String CMD_SLEEP = "input keyevent SLEEP";
    static final long MINUTE_MS = 60 * 1000L;
    static final long DAY_MS = 24 * 60 * MINUTE_MS;
    /** Text and symbols on a white surface. */
    static final int INK = Color.rgb(24, 26, 30);

    private static final String REMOTE_NAME = "Remote";

    private Fire() {}

    /** Bonded only says it was paired once. Android has the answer, but keeps the call to itself. */
    static boolean connected(BluetoothDevice device) {
        try {
            return (boolean) BluetoothDevice.class.getMethod("isConnected").invoke(device);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    /** Always connected while someone is holding it, so it says nothing to them. */
    @SuppressLint("MissingPermission")
    static boolean remote(BluetoothDevice device) {
        BluetoothClass type = device.getBluetoothClass();
        String name = device.getName();
        return type != null && type.getMajorDeviceClass() == BluetoothClass.Device.Major.PERIPHERAL
            || name != null && name.contains(REMOTE_NAME);
    }

    static final String AMAZON_SAVER = "com.amazon.ftv.screensaver/.app.services.ScreensaverService";

    static String[] forceStop(Context context) { return labels(context, FORCE_STOP_WORDS, FORCE_STOP); }

    static String[] restart(Context context) { return labels(context, RESTART_WORDS, RESTART); }

    /** Fire OS's pages speak its own language, not Salotto's: the words come from its app, these only if that fails. */
    private static String[] labels(Context context, String words, String[] known) {
        try {
            Resources fire = context.getPackageManager().getResourcesForApplication(SETTINGS);
            int id = fire.getIdentifier(words, "string", SETTINGS);
            if (id == 0) return known;
            String[] all = Arrays.copyOf(known, known.length + 1);
            all[known.length] = fire.getString(id);
            return all;
        } catch (PackageManager.NameNotFoundException e) {
            return known;
        }
    }

    /** Fire OS's list of notifications. They never show over the Home: this page is where they end up. */
    static Intent notifications() { return new Intent("com.amazon.tv.action.NOTIFICATION_CENTER"); }

    /** Fire OS's own menu of its screensavers. */
    static Intent saverMenu() { return new Intent("com.amazon.device.settings.action.SCREENSAVERS"); }

    /** Not a settings page: Android's own way to start the chosen screensaver. */
    static Intent screensaver() {
        return new Intent().setClassName("com.android.systemui", "com.android.systemui.Somnambulator");
    }
}
