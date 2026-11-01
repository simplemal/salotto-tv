package it.simplemal.firehome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Random;

/**
 * Salotto's screensaver: the pictures of one of the timed sets in turn, with the time, the date and
 * the weather if chosen. The clock moves to another corner with each picture, so that nothing stays
 * in one place on the screen for hours.
 */
final class SaverScreen {
    private static final long FADE_MS = 1500;
    private static final long MINUTE_MS = 60 * 1000L;
    private static final int TIME_SP = 72;
    private static final int DATE_SP = 22;
    private static final int MARGIN_DP = 48;
    private static final float SHADOW = 12;
    private static final int SHADOW_COLOR = Color.argb(170, 0, 0, 0);
    private static final int[] CORNERS = {Gravity.BOTTOM | Gravity.LEFT, Gravity.BOTTOM | Gravity.RIGHT,
        Gravity.TOP | Gravity.LEFT, Gravity.TOP | Gravity.RIGHT};
    /** In the order of the choice in the Settings, after «moves with each picture». */
    private static final int[] PLACES = {0, Gravity.TOP | Gravity.LEFT, Gravity.TOP | Gravity.RIGHT,
        Gravity.BOTTOM | Gravity.LEFT, Gravity.BOTTOM | Gravity.RIGHT, Gravity.CENTER};

    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable next = this::next;
    private final Random chance = new Random();
    private final FrameLayout root;
    private final ImageView below;
    private final ImageView above;
    private final LinearLayout clock;
    private final int fixed;
    private String shown;

    SaverScreen(Context context) {
        this.context = context;
        root = new FrameLayout(context);
        root.setBackgroundColor(Color.BLACK);
        below = picture();
        above = picture();
        root.addView(below, new FrameLayout.LayoutParams(-1, -1));
        root.addView(above, new FrameLayout.LayoutParams(-1, -1));
        clock = new LinearLayout(context);
        clock.setOrientation(LinearLayout.VERTICAL);
        if (Look.saverTime(context)) {
            TextClock time = clock(TIME_SP, Look.timePattern(context));
            time.setTypeface(Typeface.create(Look.FONT_FAMILIES[Look.font(context)], Typeface.NORMAL));
            clock.addView(time);
        }
        if (Look.saverDate(context)) clock.addView(clock(DATE_SP, DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM")));
        String weather = Look.saverWeather(context) ? Weather.line(context) : null;
        if (weather != null) {
            TextView line = new TextView(context);
            line.setText(weather);
            line.setTextSize(DATE_SP);
            line.setTextColor(Color.WHITE);
            line.setShadowLayer(SHADOW, 0, 0, SHADOW_COLOR);
            clock.addView(line);
        }
        int place = Look.saverPlace(context);
        fixed = place > 0 && place < PLACES.length ? PLACES[place] : 0;
        if (fixed == Gravity.CENTER) clock.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(clock, corner(fixed != 0 ? fixed : CORNERS[0]));
    }

    View view() { return root; }

    void start() { next(); }

    void stop() { main.removeCallbacks(next); }

    private ImageView picture() {
        ImageView view = new ImageView(context);
        view.setScaleType(ImageView.ScaleType.CENTER_CROP);
        return view;
    }

    private TextClock clock(int sp, String pattern) {
        TextClock view = new TextClock(context);
        view.setFormat24Hour(pattern);
        view.setFormat12Hour(pattern);
        view.setTextSize(sp);
        view.setTextColor(Color.WHITE);
        view.setShadowLayer(SHADOW, 0, 0, SHADOW_COLOR);
        return view;
    }

    private FrameLayout.LayoutParams corner(int gravity) {
        FrameLayout.LayoutParams place = new FrameLayout.LayoutParams(-2, -2, gravity);
        int margin = Math.round(MARGIN_DP * context.getResources().getDisplayMetrics().density);
        place.setMargins(margin, margin, margin, margin);
        return place;
    }

    private void next() {
        String file = Look.anyPicture(context, Look.saverFolder(context), shown);
        if (file == null) return;
        Context app = context.getApplicationContext();
        Background.draw(() -> {
            Bitmap picture = decode(app, file);
            main.post(() -> {
                if (picture != null) arrive(file, picture);
                main.postDelayed(next, Look.abstractMinutes(context) * MINUTE_MS);
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
        if (fixed == 0) clock.setLayoutParams(corner(CORNERS[chance.nextInt(CORNERS.length)]));
        above.setImageBitmap(picture);
        above.setAlpha(0f);
        above.setVisibility(View.VISIBLE);
        above.animate().alpha(1f).setDuration(FADE_MS).withEndAction(() -> {
            below.setImageBitmap(picture);
            above.setVisibility(View.GONE);
        }).start();
    }
}
