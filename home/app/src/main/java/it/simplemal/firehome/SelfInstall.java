package it.simplemal.firehome;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.util.Log;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Installs an update through Android's own installer, which needs no adb. Android asks first, and
 * TaskService answers. Salotto ends as the new version takes its place, so nothing here hears of
 * the success: TaskService comes back with the new version and takes it from there.
 */
public final class SelfInstall extends BroadcastReceiver {
    private static final String TAG = "Salotto";
    private static final String NAME = "salotto.apk";
    private static final String EXTRA_PACKAGE = "package";

    static void start(Context context, File apk, String pkg) throws IOException {
        PackageInstaller installer = context.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params =
            new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(pkg);
        int id = installer.createSession(params);
        try (PackageInstaller.Session session = installer.openSession(id)) {
            try (InputStream input = new FileInputStream(apk);
                 OutputStream output = session.openWrite(NAME, 0, apk.length())) {
                byte[] chunk = new byte[65536];
                for (int read; (read = input.read(chunk)) > 0; ) output.write(chunk, 0, read);
                session.fsync(output);
            }
            Intent answer = new Intent(context, SelfInstall.class).putExtra(EXTRA_PACKAGE, pkg);
            session.commit(PendingIntent.getBroadcast(context, id, answer, PendingIntent.FLAG_UPDATE_CURRENT).getIntentSender());
        }
    }

    @Override public void onReceive(Context context, Intent intent) {
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent ask = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            context.startActivity(ask.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            TaskService.pressInstall();
        } else if (Cover.PACKAGE.equals(intent.getStringExtra(EXTRA_PACKAGE))) {
            TaskService.installEnded();
        } else if (status != PackageInstaller.STATUS_SUCCESS) {
            // C202 alone cannot tell a press on Cancel from a full Stick: Segnala un problema reads this line.
            Log.w(TAG, "C202: Fire OS answered " + status + ", " + intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE));
            TaskService.installEnded();
            Updater.write(Updater.state(context), "failed C202");
            // Over the cover, which goes when it is covered, and so the error can be read.
            context.startActivity(new Intent(context, UpdateActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
    }
}
