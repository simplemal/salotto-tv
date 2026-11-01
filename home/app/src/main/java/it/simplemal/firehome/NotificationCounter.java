package it.simplemal.firehome;

import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/**
 * The number on the Notifiche tile: what came since Fire OS's list was last opened from there. Amazon
 * keeps its own count for its own apps, so this one asks Android, which fills that list too. Android's
 * outlives it, since the list's Ignora tutto only empties the page, hence the time. It looks at who
 * posted and how, never at the text. Android allows it once, through the shell: the installer does
 * it, and so does the row under Privacy.
 */
public final class NotificationCounter extends NotificationListenerService {
    /** An app at work, like AirPlay: its one notification would keep a number on the tile for good. */
    private static final int NOT_TO_READ = Notification.FLAG_ONGOING_EVENT | Notification.FLAG_FOREGROUND_SERVICE
        | Notification.FLAG_GROUP_SUMMARY;
    private static final String SYSTEM = "android";
    private static final String ALLOWED = "enabled_notification_listeners";
    private static final String FILE = "notifications";
    private static final String SEEN = "seen";

    private static NotificationCounter running;

    /** Android's own record: the service itself connects a moment later. */
    static boolean allowed(Context context) {
        String list = Settings.Secure.getString(context.getContentResolver(), ALLOWED);
        return list != null && list.contains(new ComponentName(context, NotificationCounter.class).flattenToString());
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static void seen(Context context) {
        prefs(context).edit().putLong(SEEN, System.currentTimeMillis()).apply();
    }

    static int count(Context context) {
        NotificationCounter service = running;
        if (service == null) return 0;
        StatusBarNotification[] list = service.getActiveNotifications();
        if (list == null) return 0;
        long seen = prefs(context).getLong(SEEN, 0);
        int count = 0;
        for (StatusBarNotification item : list) {
            if (item.getPostTime() > seen && !SYSTEM.equals(item.getPackageName())
                && (item.getNotification().flags & NOT_TO_READ) == 0) count++;
        }
        return count;
    }

    @Override public void onListenerConnected() { running = this; }

    @Override public void onListenerDisconnected() { running = null; }
}
