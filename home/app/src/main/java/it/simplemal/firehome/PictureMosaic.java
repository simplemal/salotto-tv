package it.simplemal.firehome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Camera;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;

/**
 * Large pictures of one set, behind a page: they run off the top, the bottom and the left, and fade
 * into the page's own background before the rows on the right, which must stay readable. Every two
 * seconds one tile turns round on its vertical axis and another dissolves, and
 * between changes nothing is drawn. Weather pictures open with a good and a bad day and a good and
 * a bad night, on the tiles most in view; after that chance picks a moment of the day and a kind of
 * weather, or it would be mostly drizzle.
 */
final class PictureMosaic extends Drawable {
    private static final int COLUMNS = 3;
    private static final int ROWS = 5;
    private static final float TILE_SHARE = 0.25f;
    private static final float GAP_SHARE = 0.008f;
    private static final float RADIUS_SHARE = 0.008f;
    /** The middle column stands under the title of the page, with half a column on either side. */
    private static final float MIDDLE_COLUMN = 0.24f;
    /** Where the grid starts, as a share of a tile: above the screen. */
    private static final float START_Y = -0.6f;
    private static final float COLUMN_STEP_Y = -0.34f;
    /** The files are 1920 wide and a tile a quarter of that. */
    private static final int SAMPLE = 4;
    private static final long EVERY_MS = 2000;
    private static final long TURN_MS = 800;
    private static final long DISSOLVE_MS = 1000;
    /** Then it stays still, in case nobody is looking any more. */
    private static final long RUNS_FOR_MS = 300000;
    private static final float CAMERA_DISTANCE = -40;
    private static final float HALF_TURN = 180;
    /** Enough for white words to stay readable over any picture. */
    private static final int VEIL = Color.argb(120, 0, 0, 0);
    /** As shares of the screen: solid up to here, and gone just before the rows begin. */
    private static final float FADE_FROM = 0.2f;
    private static final float FADE_TO = 0.455f;
    private static final int FAIR_UP_TO = 2;
    /** Rain and worse. In between, overcast and fog are neither good nor bad, so they are left out. */
    private static final int BAD_FROM = 51;
    private static final String GOOD = "/good";
    private static final String BAD = "/bad";
    private static final String DAY = "day";
    private static final String NIGHT = "night";

    private static final class Tile {
        final RectF place = new RectF();
        BitmapShader shown;
        BitmapShader coming;
        boolean turns;
        boolean loading;
        long since;
    }

    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable change = this::change;
    private final List<Tile> tiles = new ArrayList<>();
    private final Map<String, List<String>> moments = new HashMap<>();
    private final List<String> opening = new ArrayList<>();
    private final Random chance = new Random();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint fade = new Paint();
    private final Drawable base = Look.plainBackdrop();
    private final Camera camera = new Camera();
    private final Matrix turn = new Matrix();
    private List<String> files = new ArrayList<>();
    private String folder;
    private int next;
    private long started;
    private float radius;
    private float fadeEnd;

    PictureMosaic(Context context) {
        this.context = context.getApplicationContext();
        camera.setLocation(0, 0, CAMERA_DISTANCE);
        for (int index = 0; index < COLUMNS * ROWS; index++) tiles.add(new Tile());
        placeTiles(this.context.getResources().getDisplayMetrics());
    }

    /** Null stops it and lets the pictures go. */
    void show(String wanted) {
        if (wanted == null ? folder == null : wanted.equals(folder)) return;
        folder = wanted;
        main.removeCallbacks(change);
        files = new ArrayList<>();
        moments.clear();
        opening.clear();
        next = 0;
        for (Tile tile : tiles) {
            tile.shown = null;
            tile.coming = null;
            tile.loading = false;
        }
        if (wanted == null) return;
        sortMoments(Weather.pictures(context, wanted + "/"), wanted);
        if (moments.isEmpty()) {
            try {
                collect(wanted);
            } catch (IOException e) {
                return;
            }
            Collections.shuffle(files);
        }
        started = SystemClock.uptimeMillis();
        for (Tile tile : tiles) load(tile, false);
        main.postDelayed(change, EVERY_MS);
        invalidateSelf();
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
        opening.addAll(Arrays.asList(pick(DAY + GOOD), pick(DAY + BAD), pick(NIGHT + GOOD), pick(NIGHT + BAD)));
    }

    private String pick(String moment) {
        List<String> choices = moments.get(moment);
        return choices.get(chance.nextInt(choices.size()));
    }

    /** The illustrations sit in a folder for each moment of the day. */
    private void collect(String path) throws IOException {
        for (String name : context.getAssets().list(path)) {
            String file = path + "/" + name;
            if (name.endsWith(Weather.PICTURE_FILE)) files.add(file);
            else if (name.indexOf('.') < 0) collect(file);
        }
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

    private void change() {
        if (folder == null || SystemClock.uptimeMillis() - started > RUNS_FOR_MS) return;
        List<Tile> free = new ArrayList<>();
        for (Tile tile : tiles) {
            if (tile.shown != null && tile.coming == null && !tile.loading) free.add(tile);
        }
        Collections.shuffle(free);
        if (free.size() > 0) load(free.get(0), true);
        if (free.size() > 1) load(free.get(1), false);
        main.postDelayed(change, EVERY_MS);
    }

    private void load(Tile tile, boolean turns) {
        tile.loading = true;
        String file = nextFile();
        String asked = folder;
        Background.draw(() -> {
            Bitmap picture = decode(file);
            main.post(() -> {
                if (!asked.equals(folder)) return;
                tile.loading = false;
                if (picture == null) return;
                tile.coming = shader(picture, tile.place);
                tile.turns = turns;
                tile.since = 0;
                invalidateSelf();
            });
        });
    }

    private Bitmap decode(String file) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = SAMPLE;
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        try (InputStream input = context.getAssets().open(file)) {
            return BitmapFactory.decodeStream(input, null, options);
        } catch (IOException e) {
            return null;
        }
    }

    private static BitmapShader shader(Bitmap picture, RectF place) {
        BitmapShader shader = new BitmapShader(picture, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        Matrix fit = new Matrix();
        fit.setScale(place.width() / picture.getWidth(), place.height() / picture.getHeight());
        fit.postTranslate(place.left, place.top);
        shader.setLocalMatrix(fit);
        return shader;
    }

    /** Always the whole screen. The tiles most in view come first, for the weather's opening. */
    private void placeTiles(DisplayMetrics screen) {
        fadeEnd = screen.widthPixels * FADE_TO;
        fade.setShader(new LinearGradient(screen.widthPixels * FADE_FROM, 0, fadeEnd, 0, Color.BLACK, Color.TRANSPARENT,
            Shader.TileMode.CLAMP));
        fade.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        float width = screen.widthPixels * TILE_SHARE;
        float height = width * 9 / 16;
        float gap = screen.widthPixels * GAP_SHARE;
        radius = screen.widthPixels * RADIUS_SHARE;
        for (int index = 0; index < tiles.size(); index++) {
            int column = index / ROWS;
            float left = screen.widthPixels * MIDDLE_COLUMN - width / 2 + (column - 1) * (width + gap);
            float top = height * (START_Y + (column % 2) * COLUMN_STEP_Y) + index % ROWS * (height + gap);
            tiles.get(index).place.set(left, top, left + width, top + height);
        }
        float middle = screen.heightPixels / 2f;
        tiles.sort(Comparator.comparingDouble(tile ->
            Math.abs(tile.place.centerX() - screen.widthPixels * MIDDLE_COLUMN) + Math.abs(tile.place.centerY() - middle)));
    }

    @Override protected void onBoundsChange(Rect bounds) { base.setBounds(bounds); }

    @Override public void draw(Canvas canvas) {
        base.draw(canvas);
        int bottom = getBounds().height();
        int faded = canvas.saveLayer(0, 0, fadeEnd, bottom, null);
        long now = SystemClock.uptimeMillis();
        boolean moving = false;
        for (Tile tile : tiles) {
            if (tile.coming == null) {
                if (tile.shown != null) paint(canvas, tile, tile.shown, 255);
                continue;
            }
            if (tile.since == 0) tile.since = now;
            boolean turning = tile.turns && tile.shown != null;
            float linear = Math.min(1, (now - tile.since) / (float) (turning ? TURN_MS : DISSOLVE_MS));
            float eased = linear * linear * (3 - 2 * linear);
            if (turning) turn(canvas, tile, eased);
            else dissolve(canvas, tile, eased);
            if (linear >= 1) {
                tile.shown = tile.coming;
                tile.coming = null;
            } else {
                moving = true;
            }
        }
        canvas.drawColor(VEIL);
        canvas.drawRect(0, 0, fadeEnd, bottom, fade);
        canvas.restoreToCount(faded);
        if (moving) invalidateSelf();
    }

    private void dissolve(Canvas canvas, Tile tile, float eased) {
        if (tile.shown != null) paint(canvas, tile, tile.shown, 255);
        paint(canvas, tile, tile.coming, Math.round(eased * 255));
    }

    /** Past the edge-on moment the new picture is on the face, and it must not read mirrored. */
    private void turn(Canvas canvas, Tile tile, float eased) {
        float angle = eased * HALF_TURN;
        boolean first = angle < HALF_TURN / 2;
        camera.save();
        camera.rotateY(first ? angle : angle - HALF_TURN);
        camera.getMatrix(turn);
        camera.restore();
        turn.preTranslate(-tile.place.centerX(), -tile.place.centerY());
        turn.postTranslate(tile.place.centerX(), tile.place.centerY());
        canvas.save();
        canvas.concat(turn);
        paint(canvas, tile, first ? tile.shown : tile.coming, 255);
        canvas.restore();
    }

    private void paint(Canvas canvas, Tile tile, BitmapShader picture, int alpha) {
        paint.setShader(picture);
        paint.setAlpha(alpha);
        canvas.drawRoundRect(tile.place, radius, radius, paint);
    }

    @Override public void setAlpha(int alpha) { }

    @Override public void setColorFilter(ColorFilter filter) { }

    @Override public int getOpacity() { return PixelFormat.OPAQUE; }
}
