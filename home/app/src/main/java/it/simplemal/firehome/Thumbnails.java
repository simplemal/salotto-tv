package it.simplemal.firehome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * The small copies of the backdrop sets. WebP has no smaller view of itself: asking for a quarter
 * still reads the whole picture, a tenth of a second each, so sixty keep a grid empty for seconds.
 * These come ready made from distro/tools/prepara-miniature.py, and the last ones read stay here.
 */
final class Thumbnails {
    private static final String FOLDER = "miniature/";
    /** A smaller copy still, for the first moment: it takes a fraction of the time to read. */
    private static final String TINY_FOLDER = "miniature-piccole/";
    /** Salotto stays in memory under every app: a set and a bit, not all of them. Let go when Android asks. */
    private static final int KEPT_BYTES = 28 * 1024 * 1024;
    /** For a picture with no small copy: a quarter of each side. */
    private static final int FULL_SAMPLE = 4;
    /** Kept as read from the picture itself, not from its small copy. */
    private static final Set<String> SHARP = Collections.synchronizedSet(new HashSet<>());
    private static final LruCache<String, Bitmap> KEPT = new LruCache<String, Bitmap>(KEPT_BYTES) {
        @Override protected int sizeOf(String asset, Bitmap thumbnail) { return thumbnail.getByteCount(); }

        @Override protected void entryRemoved(boolean evicted, String asset, Bitmap old, Bitmap replaced) {
            if (replaced == null) SHARP.remove(asset);
        }
    };

    private Thumbnails() {}

    /** Already in memory, or null: for the main thread. */
    static Bitmap kept(String asset) { return KEPT.get(asset); }

    /** Away from the main thread. Null if this picture has no such copy. */
    static Bitmap tiny(Context context, String asset) { return decode(context, TINY_FOLDER + asset, 1); }

    /** Takes time: call it away from the main thread. */
    static Bitmap read(Context context, String asset) {
        Bitmap thumbnail = KEPT.get(asset);
        if (thumbnail != null) return thumbnail;
        thumbnail = decode(context, FOLDER + asset, 1);
        if (thumbnail == null) thumbnail = decode(context, asset, FULL_SAMPLE);
        if (thumbnail != null) KEPT.put(asset, thumbnail);
        return thumbnail;
    }

    /**
     * The picture itself at a quarter of each side, a tenth of a second or more: for the few on
     * screen. The small copies are lossy, and lossy turns shaded backgrounds into squares at any
     * quality. Null if it cannot be read.
     */
    static Bitmap sharp(Context context, String asset) {
        if (SHARP.contains(asset)) {
            Bitmap kept = KEPT.get(asset);
            if (kept != null) return kept;
        }
        Bitmap picture = decode(context, asset, FULL_SAMPLE);
        if (picture == null) return null;
        KEPT.put(asset, picture);
        SHARP.add(asset);
        return picture;
    }

    private static Bitmap decode(Context context, String asset, int sample) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        // Sixteen bits turn the dark gradients of the sets into steps: full colour, at twice the memory.
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        try (InputStream input = context.getAssets().open(asset)) {
            return BitmapFactory.decodeStream(input, null, options);
        } catch (IOException e) {
            return null;
        }
    }

    static void trim() { KEPT.evictAll(); }
}
