package it.simplemal.firehome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

/**
 * The picture made dimmer and warmer. Fire OS has no night display on this Stick and nothing here
 * reaches the TV's backlight, so both are a single tinted window over everything, drawn once:
 * TaskService owns it. This class only says how much.
 */
final class Veil {
    private static final String FILE = "veil";
    private static final String DIM = "dim";
    private static final String WARM = "warm";
    private static final float[] DIM_ALPHA = {0, 0.25f, 0.45f};
    private static final float[] WARM_ALPHA = {0, 0.16f, 0.30f};
    /** Dark on purpose: a bright amber would turn the blacks brown. */
    private static final int AMBER_RED = 80;
    private static final int AMBER_GREEN = 40;
    private static final int OPAQUE = 255;

    private Veil() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /** Zero is off. A level saved by a version with more of them comes down to the highest. */
    private static int level(Context context, String key, int levels) {
        return Math.min(prefs(context).getInt(key, 0), levels - 1);
    }

    private static void next(Context context, String key, int levels) {
        prefs(context).edit().putInt(key, (level(context, key, levels) + 1) % levels).apply();
    }

    static void nextDim(Context context) { next(context, DIM, DIM_ALPHA.length); }
    static void nextWarm(Context context) { next(context, WARM, WARM_ALPHA.length); }
    static int dim(Context context) { return level(context, DIM, DIM_ALPHA.length); }
    static int warm(Context context) { return level(context, WARM, WARM_ALPHA.length); }

    /** One colour that does what the warm tint and then the dimming would do, in a single layer. */
    static int color(Context context) {
        float dim = DIM_ALPHA[dim(context)];
        float warm = WARM_ALPHA[warm(context)];
        float alpha = 1 - (1 - dim) * (1 - warm);
        if (alpha == 0) return Color.TRANSPARENT;
        float amber = (1 - dim) * warm / alpha;
        return Color.argb(Math.round(alpha * OPAQUE), Math.round(amber * AMBER_RED), Math.round(amber * AMBER_GREEN), 0);
    }
}
