package it.simplemal.firehome;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.WindowManager;

/**
 * The screensaver as a page of Salotto, opened by the Home when it stays untouched. It needs nothing
 * from Fire OS, which on some models never starts the one chosen. Any key closes it.
 */
public final class SaverActivity extends Activity {
    private static final String SLEEP_TIMEOUT = "sleep_timeout";

    private final Handler main = new Handler(Looper.getMainLooper());
    /** Kept awake it would hold the Stick up all night: after its time, the Stick may sleep as usual. */
    private final Runnable letSleep = () -> getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    private SaverScreen screen;

    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(Language.apply(base));
    }

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        screen = new SaverScreen(this);
        setContentView(screen.view());
    }

    @Override protected void onResume() {
        super.onResume();
        screen.start();
        int sleep = Settings.Secure.getInt(getContentResolver(), SLEEP_TIMEOUT, 0);
        if (sleep > 0) main.postDelayed(letSleep, sleep * 1000L);
    }

    @Override protected void onPause() {
        super.onPause();
        screen.stop();
        main.removeCallbacks(letSleep);
        finish();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_UP) finish();
        return true;
    }
}
