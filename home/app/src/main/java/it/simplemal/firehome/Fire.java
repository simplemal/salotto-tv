package it.simplemal.firehome;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Region;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableWrapper;
import android.graphics.drawable.InsetDrawable;
import android.os.Build;
import android.os.Handler;
import android.provider.Settings;
import android.view.PixelCopy;
import android.view.View;
import android.view.Window;
import java.util.Arrays;

/** What several screens share about the Stick and the apps around Salotto. */
final class Fire {
    /**
     * The system under Salotto, asked first: Google TV, tried on the branch google-tv since
     * 2026-11-15, or else Amazon's Fire OS, which everything was made for. Where the two differ the
     * code asks this and splits there; Fire OS's way stays as it was. The emulator stands for a Fire TV.
     */
    static final boolean GOOGLE_TV = "Google".equalsIgnoreCase(Build.MANUFACTURER)
        && !Build.MODEL.startsWith("sdk_") && !Build.PRODUCT.startsWith("sdk");
    /**
     * The 4K Max of 2021 and the Sticks of 2020, and the Stick 4K of 2018 on Android 7 too. Their
     * adbd answers only other machines, so Salotto has no shell there, and after Home they hold
     * every start for 5 s, except one made by an alarm.
     */
    static final boolean ANDROID_9 = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q;
    /** The Stick 4K of 2018, on Fire OS 6: Android 7, before adaptive icons, fonts as resources and storage figures. */
    static final boolean ANDROID_7 = Build.VERSION.SDK_INT < Build.VERSION_CODES.O;
    /** The tools read this line too: pubblica-aggiornamento.sh and genera-errori.py. */
    static final String UPDATES_REPO = "simplemal/salotto-tv";
    /** Took the Home key to Salotto on Sticks set up before Salotto did that itself. */
    static final String OLD_FIXER = "io.github.hatim.firestickfixer";
    static final String AIRPLAY = "io.github.jqssun.airplay";
    static final String AMAZON_HOME = "com.amazon.tv.launcher";
    static final String AMBIENT = "com.amazon.tv.ftvambient";
    /** Its package also holds the recent apps page: only this one is the Home itself. */
    static final String AMAZON_HOME_PAGE = "com.amazon.tv.launcher.ui.HomeActivity_vNext";
    /** Google TV's own Home, where its Home key goes. */
    static final String GOOGLE_HOME = "com.google.android.apps.tv.launcherx";
    static final String GOOGLE_HOME_PAGE = "com.google.android.apps.tv.launcherx.home.HomeActivity";
    /** The system's name where a choice is between Salotto and the system, as for the screensaver. */
    static final String SYSTEM_NAME = GOOGLE_TV ? "Google TV" : "Fire OS";
    /** Google TV's page of its screensaver, Ambient mode: Android's own action finds nothing there. */
    static final String GOOGLE_SAVER_PAGE = "com.google.android.tv.settings.ambient";
    static final String GOOGLE_SAVER = "com.google.android.apps.tv.dreamx/.service.Backdrop";
    /** The Home the system itself brings with the Home key, which Salotto takes over from. */
    static final String SYSTEM_HOME = GOOGLE_TV ? GOOGLE_HOME : AMAZON_HOME;
    static final String SYSTEM_HOME_PAGE = GOOGLE_TV ? GOOGLE_HOME_PAGE : AMAZON_HOME_PAGE;
    static final String APPSTORE = "com.amazon.venezia";
    /** The store itself. Its launcher entry only asks Amazon's Home for a tab, and Salotto has replaced that Home. */
    static final String APPSTORE_PAGE = "com.amazon.venezia.ade.FEATURED_LAUNCH";
    static final String QUICK_MENU = "com.amazon.tv.quicksettings.ui";
    static final String SETTINGS = "com.amazon.tv.settings.v2";
    static final String UNINSTALL_PROGRESS = ".television.UninstallAppProgress";
    static final String INSTALLER = "com.android.packageinstaller";
    static final String INSTALL_BUTTON = "android:id/button1";
    /** Android 9 gives its button another name. */
    static final String INSTALL_BUTTON_9 = INSTALLER + ":id/ok_button";
    private static final String[] INSTALL = {"Installa", "Install"};
    private static final String INSTALL_WORDS = "install";
    static final String DEVICE_PAGE = "com.amazon.device.settings.action.DEVICE";
    static final String[] FORCE_STOP = {"Forza arresto", "Force stop"};
    static final String[] RESTART = {"Riavvia", "Restart"};
    private static final String FORCE_STOP_WORDS = "force_stop";
    private static final String RESTART_WORDS = "restart_settings_title";
    /** What Fire OS opens on a remote's Settings key. */
    static final String SETTINGS_PAGE = "com.amazon.tv.launcher.ui.MainSettingsActivity";
    /** What Fire OS opens when Home is held. */
    static final String HOME_HELD_PAGE = "com.amazon.tv.settings.v2.hud.HudActivity";
    static final String RECENTS_ACTION = "com.amazon.tv.action.NAVIGATE_TO_RECENTS";
    /** What Fire OS opens on the recent apps key. */
    static final String RECENTS_PAGE = "com.amazon.tv.launcher.deeplink.activity.RecentDeepLinkActivityDI";
    static final String CMD_SLEEP = "input keyevent SLEEP";
    /** Android 7 allows installing for the whole TV, not app by app. The installer turns it on too. */
    static final String ALLOW_INSTALLS_7 = "settings put secure install_non_market_apps 1";
    static final long MINUTE_MS = 60 * 1000L;
    static final long DAY_MS = 24 * 60 * MINUTE_MS;
    /** Text and symbols on a white surface. */
    static final int INK = Color.rgb(24, 26, 30);

    private static final String REMOTE_NAME = "Remote";

    private Fire() {}

    /** Android 8 lights a focused view by itself, and Salotto draws its own focus. Android 7 has nothing to turn off. */
    static void noHighlight(View view) {
        if (!ANDROID_7) view.setDefaultFocusHighlightEnabled(false);
    }

    /** A margin measured on the size it is drawn at. InsetDrawable learnt fractions with Android 8. */
    static Drawable inset(Drawable drawable, float fraction) {
        if (!ANDROID_7) return new InsetDrawable(drawable, fraction);
        return new DrawableWrapper(drawable) {
            @Override protected void onBoundsChange(Rect bounds) {
                int x = (int) (bounds.width() * fraction), y = (int) (bounds.height() * fraction);
                getDrawable().setBounds(bounds.left + x, bounds.top + y, bounds.right - x, bounds.bottom - y);
            }
        };
    }

    /** Whether Salotto may install what it brings: its updates and its cover. */
    @SuppressWarnings("deprecation")
    static boolean canInstall(Context context) {
        if (!ANDROID_7) return context.getPackageManager().canRequestPackageInstalls();
        ContentResolver resolver = context.getContentResolver();
        return Settings.Secure.getInt(resolver, Settings.Secure.INSTALL_NON_MARKET_APPS, 0) == 1;
    }

    /** Everything but the path. Android 7 has only the older way of saying it. */
    @SuppressWarnings("deprecation")
    static void clipOut(Canvas canvas, Path path) {
        if (ANDROID_7) canvas.clipPath(path, Region.Op.DIFFERENCE);
        else canvas.clipOutPath(path);
    }

    /**
     * The window as it is on screen, scaled into the picture. Android 7 cannot copy a window, so its
     * views draw themselves there instead, at once.
     */
    static void copy(Window window, Bitmap picture, Runnable done, Handler handler) {
        if (!ANDROID_7) {
            PixelCopy.request(window, picture, result -> done.run(), handler);
            return;
        }
        View root = window.getDecorView();
        if (root.getWidth() > 0 && root.getHeight() > 0) {
            // At full size first: a picture backdrop fills the canvas it is given, and drawn into a
            // scaled one it came out a quarter of the screen, grey around it (2026-11-15).
            Bitmap whole = Bitmap.createBitmap(root.getWidth(), root.getHeight(), Bitmap.Config.ARGB_8888);
            root.draw(new Canvas(whole));
            new Canvas(picture).drawBitmap(whole, null, new Rect(0, 0, picture.getWidth(), picture.getHeight()),
                new Paint(Paint.FILTER_BITMAP_FLAG));
            whole.recycle();
        }
        handler.post(done);
    }

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
    /**
     * Salotto's own screensaver. Not on Fire OS 7: there, as Salotto's screensaver ends, Fire OS stops
     * all of Salotto, the service goes with it and the Home key goes to Amazon (issue #4). Only from
     * the Home it would have shown Fire OS's inside the apps: the user chose Fire OS's alone
     * (2026-11-15).
     */
    static final boolean OWN_SAVER = !ANDROID_9;

    static String[] forceStop(Context context) { return labels(context, SETTINGS, FORCE_STOP_WORDS, FORCE_STOP); }

    static String[] restart(Context context) { return labels(context, SETTINGS, RESTART_WORDS, RESTART); }

    static String[] install(Context context) { return labels(context, INSTALLER, INSTALL_WORDS, INSTALL); }

    /** Fire OS's pages speak its own language, not Salotto's: the words come from its app, these only if that fails. */
    private static String[] labels(Context context, String app, String words, String[] known) {
        try {
            Resources fire = context.getPackageManager().getResourcesForApplication(app);
            int id = fire.getIdentifier(words, "string", app);
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

    /** Fire OS's own settings, the page behind a remote's Settings key. */
    static Intent settings() {
        if (GOOGLE_TV) return new Intent(Settings.ACTION_SETTINGS);
        return new Intent().setClassName(AMAZON_HOME, SETTINGS_PAGE);
    }

    /** Fire OS's own menu of its screensavers. */
    static Intent saverMenu() {
        if (GOOGLE_TV) return new Intent(GOOGLE_SAVER_PAGE);
        return new Intent("com.amazon.device.settings.action.SCREENSAVERS");
    }

    /**
     * Not a settings page: Android's own way to start the chosen screensaver. Android 9 has none, so
     * Fire OS's can only be chosen there. Google TV has no Somnambulator: its settings start the
     * screensaver on the voice action «sleep» (DaydreamVoiceAction, tried on the Chromecast 2026-11-18).
     */
    static Intent screensaver(Context context) {
        if (GOOGLE_TV) return new Intent("com.google.android.pano.action.SLEEP");
        if (!ANDROID_9) return new Intent().setClassName("com.android.systemui", "com.android.systemui.Somnambulator");
        return Look.ownSaver(context) ? new Intent(context, SaverActivity.class) : saverMenu();
    }
}
