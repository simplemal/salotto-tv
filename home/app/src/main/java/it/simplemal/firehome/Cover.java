package it.simplemal.firehome;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * The small separate app that keeps the update page on screen while Android replaces Salotto, so
 * Fire OS's Home does not show. It travels inside Salotto, which installs it the first time an update
 * needs it, so every Stick gets it. Without it an update still works, and that Home shows for a few seconds.
 */
final class Cover {
    static final String PACKAGE = "it.simplemal.firehome.cover";
    private static final String ACTIVITY = PACKAGE + ".CoverActivity";
    private static final String APK = "copertura.apk";
    private static final String EXTRA_PAGE = "page";
    private static final int VERSION = 2;
    private static final int BEST = 100;
    private static final long INSTALL_LIMIT_MS = 15000;
    private static final long POLL_MS = 200;

    private static volatile Bitmap page;

    private Cover() {}

    /** On the main thread, with the page drawn: from here on this picture stands in for it. */
    static void keepPage(View view) {
        Bitmap picture = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(picture));
        page = picture;
    }

    static Bitmap page() { return page; }

    private static boolean installed(Context context) {
        try {
            return context.getPackageManager().getPackageInfo(PACKAGE, 0).versionCode >= VERSION;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /** Blocking, as it waits for Android's question to be answered. */
    static void prepare(Context context) {
        if (installed(context)) return;
        File apk = new File(context.getCacheDir(), APK);
        try (InputStream input = context.getAssets().open(APK)) {
            Files.copy(input, apk.toPath(), StandardCopyOption.REPLACE_EXISTING);
            SelfInstall.start(context, apk, PACKAGE);
            long limit = System.currentTimeMillis() + INSTALL_LIMIT_MS;
            while (!installed(context) && System.currentTimeMillis() < limit) Thread.sleep(POLL_MS);
        } catch (IOException | SecurityException e) {
            // The update goes on without it.
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            apk.delete();
        }
    }

    /** The update page is plain, so even as a whole screen it fits in an Intent. */
    static void show(Context context) {
        ByteArrayOutputStream picture = new ByteArrayOutputStream();
        page.compress(Bitmap.CompressFormat.PNG, BEST, picture);
        try {
            context.startActivity(new Intent().setClassName(PACKAGE, ACTIVITY)
                .putExtra(EXTRA_PAGE, picture.toByteArray())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION));
        } catch (ActivityNotFoundException e) {
            // The update goes on without it.
        }
    }
}
