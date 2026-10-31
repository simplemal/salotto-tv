package it.simplemal.firehome.cover;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;

/**
 * Android closes Salotto to replace it, and everything Salotto has on screen goes with it. This app
 * is separate so that it survives: it holds a picture of Salotto's update page over Fire OS's Home,
 * so the steps stay on screen. It goes as soon as anything comes over it, which is Salotto coming back.
 */
public final class CoverActivity extends Activity {
    private static final String SALOTTO = "it.simplemal.firehome";
    private static final String EXTRA_PAGE = "page";
    private static final long LIMIT_MS = 60000;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable limit = this::backToSalotto;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ImageView picture = new ImageView(this);
        picture.setScaleType(ImageView.ScaleType.FIT_XY);
        byte[] page = getIntent().getByteArrayExtra(EXTRA_PAGE);
        if (page != null) picture.setImageBitmap(BitmapFactory.decodeByteArray(page, 0, page.length));
        setContentView(picture);
        main.postDelayed(limit, LIMIT_MS);
    }

    /** Android's own question is a small window, and does not stop this page. */
    @Override protected void onStop() {
        super.onStop();
        finish();
    }

    @Override protected void onDestroy() {
        main.removeCallbacks(limit);
        super.onDestroy();
    }

    @Override public void onBackPressed() {}

    /** Salotto never came back by itself: its Home knows how to look after an unfinished update. */
    private void backToSalotto() {
        try {
            startActivity(new Intent().setClassName(SALOTTO, SALOTTO + ".MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException e) {
            // Nothing left to go to but Fire OS's Home.
        }
        finish();
    }
}
