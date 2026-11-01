package it.simplemal.firehome;

import android.content.Context;
import android.service.dreams.DreamService;

/**
 * SaverScreen as Android's screensaver. Choosing it is what turns Fire OS's off, since only one runs
 * at a time, where Fire OS lets it: some models start their own Ambient instead. SaverActivity is
 * the same picture for those, from the Home.
 */
public final class SaverService extends DreamService {
    private SaverScreen screen;

    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(Language.apply(base));
    }

    @Override public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setFullscreen(true);
        setInteractive(false);
        setScreenBright(true);
        screen = new SaverScreen(this);
        setContentView(screen.view());
    }

    @Override public void onDreamingStarted() {
        super.onDreamingStarted();
        screen.start();
    }

    @Override public void onDreamingStopped() {
        super.onDreamingStopped();
        screen.stop();
    }
}
