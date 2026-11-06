package it.simplemal.firehome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ReplacementSpan;
import java.util.HashMap;
import java.util.Map;

/**
 * The weather's symbols, Lucide's line icons instead of emoji (the user's choice, 2026-11-06): Fire
 * OS's emoji font drew fog as a grey square. In the text a symbol is a private character, and `in`
 * puts the icon in its place, drawn with the text's own colour and shadow.
 */
final class WeatherIcon {
    /** As wide as an emoji was beside the same text. */
    private static final float SCALE = 1.2f;
    private static final char FIRST = '';
    private static final int[] ICONS = {
        R.drawable.weather_sun, R.drawable.weather_cloud_sun, R.drawable.weather_cloudy, R.drawable.weather_cloud_fog,
        R.drawable.weather_cloud_drizzle, R.drawable.weather_cloud_rain, R.drawable.weather_cloud_snow,
        R.drawable.weather_cloud_sun_rain, R.drawable.weather_cloud_lightning, R.drawable.weather_cloud_hail,
    };
    /** Each icon as a mask, at each size it was asked for: the paint gives it colour and shadow. */
    private static final Map<Long, Bitmap> MASKS = new HashMap<>();

    private WeatherIcon() {}

    /** Open-Meteo's weather code as its symbol: the same groups the emoji had, hail apart from thunder. */
    static String of(int code) {
        int icon;
        if (code == 0) icon = 0;
        else if (code <= 2) icon = 1;
        else if (code == 3) icon = 2;
        else if (code <= 48) icon = 3;
        else if (code <= 57) icon = 4;
        else if (code <= 67) icon = 5;
        else if (code <= 77) icon = 6;
        else if (code <= 82) icon = 7;
        else if (code <= 86) icon = 6;
        else if (code == 96 || code == 99) icon = 9;
        else icon = 8;
        return String.valueOf((char) (FIRST + icon));
    }

    /** The text with each symbol drawn as its icon. */
    static CharSequence in(Context context, CharSequence text) {
        if (text == null) return null;
        SpannableString shown = null;
        for (int index = 0; index < text.length(); index++) {
            int icon = text.charAt(index) - FIRST;
            if (icon < 0 || icon >= ICONS.length) continue;
            if (shown == null) shown = new SpannableString(text);
            shown.setSpan(new Icon(context.getApplicationContext(), ICONS[icon]), index, index + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return shown != null ? shown : text;
    }

    private static Bitmap mask(Context context, int icon, int size) {
        long key = (long) icon << 16 | size;
        synchronized (MASKS) {
            Bitmap known = MASKS.get(key);
            if (known != null) return known;
        }
        Bitmap drawn = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Drawable picture = context.getDrawable(icon);
        picture.setBounds(0, 0, size, size);
        picture.draw(new Canvas(drawn));
        Bitmap alpha = drawn.extractAlpha();
        drawn.recycle();
        synchronized (MASKS) { MASKS.put(key, alpha); }
        return alpha;
    }

    private static final class Icon extends ReplacementSpan {
        private final Context context;
        private final int icon;

        Icon(Context context, int icon) {
            this.context = context;
            this.icon = icon;
        }

        private static int size(Paint paint) { return Math.round(paint.getTextSize() * SCALE); }

        @Override public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt metrics) {
            if (metrics != null) paint.getFontMetricsInt(metrics);
            return size(paint);
        }

        /** Centred on the text's own middle, in its colour and with its shadow: a mask takes the paint's colour. */
        @Override public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, Paint paint) {
            int size = size(paint);
            float middle = y + (paint.ascent() + paint.descent()) / 2f;
            canvas.drawBitmap(mask(context, icon, size), x, middle - size / 2f, paint);
        }
    }
}
