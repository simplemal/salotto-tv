package it.simplemal.firehome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Random;

/**
 * Salotto's screensaver: the pictures of one of the timed sets in turn, with only the time and the
 * date. Choosing it is what turns Fire OS's off, since only one runs at a time. The clock moves to
 * another corner with each picture, so that nothing stays in one place on the screen for hours.
 */
public final class SaverService extends DreamService {
    private static final long FADE_MS = 1500;
    private static final long MINUTE_MS = 60 * 1000L;
    private static final int TIME_SP = 72;
    private static final int DATE_SP = 22;
    private static final int MARGIN_DP = 48;
    private static final float SHADOW = 12;
    private static final int SHADOW_COLOR = Color.argb(170, 0, 0, 0);
    private static final int[] CORNERS = {Gravity.BOTTOM | Gravity.LEFT, Gravity.BOTTOM | Gravity.RIGHT,
        Gravity.TOP | Gravity.LEFT, Gravity.TOP | Gravity.RIGHT};

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable next = this::next;
    private final Random chance = new Random();
    private ImageView below;
    private ImageView above;
    private LinearLayout clock;
    private String shown;

    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(Language.apply(base));
    }

    @Override public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setFullscreen(true);
        setInteractive(false);
        setScreenBright(true);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        below = picture();
        above = picture();
        root.addView(below, new FrameLayout.LayoutParams(-1, -1));
        root.addView(above, new FrameLayout.LayoutParams(-1, -1));
        clock = new LinearLayout(this);
        clock.setOrientation(LinearLayout.VERTICAL);
        TextClock time = clock(TIME_SP, Look.timePattern(this));
        time.setTypeface(Typeface.create(Look.FONT_FAMILIES[Look.font(this)], Typeface.NORMAL));
        clock.addView(time);
        clock.addView(clock(DATE_SP, DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM")));
        root.addView(clock, corner(CORNERS[0]));
        setContentView(root);
    }

    private ImageView picture() {
        ImageView view = new ImageView(this);
        view.setScaleType(ImageView.ScaleType.CENTER_CROP);
        return view;
    }

    private TextClock clock(int sp, String pattern) {
        TextClock view = new TextClock(this);
        view.setFormat24Hour(pattern);
        view.setFormat12Hour(pattern);
        view.setTextSize(sp);
        view.setTextColor(Color.WHITE);
        view.setShadowLayer(SHADOW, 0, 0, SHADOW_COLOR);
        return view;
    }

    private FrameLayout.LayoutParams corner(int gravity) {
        FrameLayout.LayoutParams place = new FrameLayout.LayoutParams(-2, -2, gravity);
        int margin = Math.round(MARGIN_DP * getResources().getDisplayMetrics().density);
        place.setMargins(margin, margin, margin, margin);
        return place;
    }

    @Override public void onDreamingStarted() {
        super.onDreamingStarted();
        next();
    }

    @Override public void onDreamingStopped() {
        super.onDreamingStopped();
        main.removeCallbacks(next);
    }

    private void next() {
        String file = Look.anyPicture(this, Look.saverFolder(this), shown);
        if (file == null) return;
        Context context = getApplicationContext();
        Background.draw(() -> {
            Bitmap picture = decode(context, file);
            main.post(() -> {
                if (picture != null && clock != null) arrive(file, picture);
                main.postDelayed(next, Look.abstractMinutes(this) * MINUTE_MS);
            });
        });
    }

    private static Bitmap decode(Context context, String file) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        try (InputStream input = context.getAssets().open(file)) {
            return BitmapFactory.decodeStream(input, null, options);
        } catch (IOException e) {
            return null;
        }
    }

    private void arrive(String file, Bitmap picture) {
        shown = file;
        clock.setLayoutParams(corner(CORNERS[chance.nextInt(CORNERS.length)]));
        above.setImageBitmap(picture);
        above.setAlpha(0f);
        above.setVisibility(View.VISIBLE);
        above.animate().alpha(1f).setDuration(FADE_MS).withEndAction(() -> {
            below.setImageBitmap(picture);
            above.setVisibility(View.GONE);
        }).start();
    }
}
