package it.simplemal.firehome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

/**
 * The picture made dimmer, and its white warmer. Dimming is a tinted window over everything, drawn
 * once: TaskService owns it. A window can only add colour, which turns blacks amber, so warmth is
 * the screen's own colour matrix: Fire OS has no night display on this Stick, only the shell may
 * set the matrix, and it lasts until the Stick restarts. This class says how much.
 */
final class Veil {
    private static final String FILE = "veil";
    private static final String DIM = "dim";
    private static final String WARM = "warm";
    private static final float[] DIM_ALPHA = {0, 0.25f, 0.45f};
    private static final int WARM_LEVELS = 2;
    private static final String COLOR_MATRIX = "service call SurfaceFlinger 1015 i32 ";
    /** Red, green and blue scaled by what takes a D65 white to D55: black stays black. */
    static final String WARM_ON = COLOR_MATRIX + "1 f 1 f 0 f 0 f 0 f 0 f 0.952 f 0 f 0 f 0 f 0 f 0.879 f 0 f 0 f 0 f 0 f 1";
    static final String WARM_OFF = COLOR_MATRIX + "0";
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
    static void nextWarm(Context context) { next(context, WARM, WARM_LEVELS); }
    static int dim(Context context) { return level(context, DIM, DIM_ALPHA.length); }
    static int warm(Context context) { return level(context, WARM, WARM_LEVELS); }

    /** What `service call` prints when the matrix was taken. */
    static boolean accepted(String reply) { return reply != null && reply.contains("Parcel") && !reply.contains("Error"); }

    static int color(Context context) {
        return Color.argb(Math.round(DIM_ALPHA[dim(context)] * OPAQUE), 0, 0, 0);
    }
}
