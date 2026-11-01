package it.simplemal.firehome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.View;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;

/**
 * Pictures of one set going slowly by, to show what its style is like. Runs only while seen. Weather
 * pictures open with a good and a bad day and a good and a bad night, the contrast that tells the most;
 * after that chance picks a moment of the day and a kind of weather, or it would be mostly drizzle.
 */
final class PictureStrip extends View {
    private static final float DP_PER_SECOND = 24;
    private static final int GAP_DP = 8;
    private static final int RADIUS_DP = 10;
    /** The files are 1920 wide: a quarter is plenty here, and four of them weigh what one would. */
    private static final int SAMPLE = 4;
    private static final int FAIR_UP_TO = 2;
    /** Rain and worse. In between, overcast and fog are neither good nor bad, so they are left out. */
    private static final int BAD_FROM = 51;
    private static final String GOOD = "/good";
    private static final String BAD = "/bad";
    private static final String DAY = "day";
    private static final String NIGHT = "night";

    private static final class Slide {
        final Bitmap picture;
        float x;

        Slide(Bitmap picture, float x) {
            this.picture = picture;
            this.x = x;
        }
    }

    private final Deque<Slide> slides = new ArrayDeque<>();
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Path corners = new Path();
    private final RectF place = new RectF();
    private final float gap;
    private final float radius;
    private final float speed;
    private final Map<String, List<String>> moments = new HashMap<>();
    private final List<String> opening = new ArrayList<>();
    private final Random chance = new Random();
    private List<String> files = new ArrayList<>();
    private String folder;
    private int next;
    private long lastFrame;
    private boolean loading;

    PictureStrip(Context context) {
        super(context);
        float density = getResources().getDisplayMetrics().density;
        gap = GAP_DP * density;
        radius = RADIUS_DP * density;
        speed = DP_PER_SECOND * density;
    }

    /** Null stops it and lets the pictures go. */
    void show(String wanted) {
        if (wanted == null ? folder == null : wanted.equals(folder)) return;
        folder = wanted;
        slides.clear();
        files = new ArrayList<>();
        moments.clear();
        opening.clear();
        next = 0;
        if (wanted == null) return;
        sortMoments(Weather.pictures(getContext(), wanted + "/"), wanted);
        if (moments.isEmpty()) {
            try {
                collect(wanted);
            } catch (IOException e) {
                return;
            }
            Collections.shuffle(files);
        }
        lastFrame = 0;
        invalidate();
    }

    private void sortMoments(Properties table, String wanted) {
        for (String key : table.stringPropertyNames()) {
            String[] parts = key.split("/");
            // The same picture is listed under the weather's name too.
            if (!TextUtils.isDigitsOnly(parts[1])) continue;
            int code = Integer.parseInt(parts[1]);
            if (code > FAIR_UP_TO && code < BAD_FROM) continue;
            List<String> moment = moments.computeIfAbsent(parts[0] + (code <= FAIR_UP_TO ? GOOD : BAD), name -> new ArrayList<>());
            for (String file : table.getProperty(key).split(",")) moment.add(wanted + "/" + file);
        }
        if (moments.isEmpty()) return;
        for (String moment : new String[]{DAY + GOOD, DAY + BAD, NIGHT + GOOD, NIGHT + BAD}) opening.add(pick(moment));
    }

    private String pick(String moment) {
        List<String> choices = moments.get(moment);
        return choices.get(chance.nextInt(choices.size()));
    }

    private String nextFile() {
        if (!opening.isEmpty()) return opening.remove(0);
        if (!moments.isEmpty()) {
            List<String> names = new ArrayList<>(moments.keySet());
            return pick(names.get(chance.nextInt(names.size())));
        }
        String file = files.get(next);
        next = (next + 1) % files.size();
        return file;
    }

    /** The illustrations sit in a folder for each moment of the day. */
    private void collect(String path) throws IOException {
        for (String name : getContext().getAssets().list(path)) {
            String file = path + "/" + name;
            if (name.endsWith(Weather.PICTURE_FILE)) files.add(file);
            else if (name.indexOf('.') < 0) collect(file);
        }
    }

    private float slideWidth() { return getHeight() * 16f / 9f; }

    private void loadNext() {
        if (loading || files.isEmpty() && moments.isEmpty()) return;
        loading = true;
        String file = nextFile();
        String asked = folder;
        Context context = getContext().getApplicationContext();
        Background.draw(() -> {
            Bitmap picture = decode(context, file);
            post(() -> {
                loading = false;
                if (picture == null || !asked.equals(folder)) return;
                Slide last = slides.peekLast();
                slides.addLast(new Slide(picture, last == null ? 0 : last.x + slideWidth() + gap));
                invalidate();
            });
        });
    }

    private static Bitmap decode(Context context, String file) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = SAMPLE;
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        try (InputStream input = context.getAssets().open(file)) {
            return BitmapFactory.decodeStream(input, null, options);
        } catch (IOException e) {
            return null;
        }
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        show(null);
    }

    @Override protected void onDraw(Canvas canvas) {
        if (folder == null || getHeight() == 0) return;
        long now = SystemClock.uptimeMillis();
        float moved = lastFrame == 0 ? 0 : (now - lastFrame) / 1000f * speed;
        lastFrame = now;
        float width = slideWidth();
        Slide first = slides.peekFirst();
        if (first != null && first.x + width < 0) slides.removeFirst();
        for (Slide slide : slides) {
            slide.x -= moved;
            place.set(slide.x, 0, slide.x + width, getHeight());
            corners.reset();
            corners.addRoundRect(place, radius, radius, Path.Direction.CW);
            canvas.save();
            canvas.clipPath(corners);
            canvas.drawBitmap(slide.picture, null, place, paint);
            canvas.restore();
        }
        Slide last = slides.peekLast();
        if (last == null || last.x + width * 2 + gap < getWidth() + width) loadNext();
        postInvalidateOnAnimation();
    }
}
