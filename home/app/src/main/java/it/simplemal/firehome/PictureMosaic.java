package it.simplemal.firehome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
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
 * seconds one tile turns round on its vertical axis and another dissolves. Each tile is a view of its
 * own: this GPU cannot repaint the whole screen sixty times a second, but it can repaint two tiles.
 * Between changes nothing is drawn. Weather pictures open with a good and a bad day and a good and a
 * bad night, on the tiles most in view; after that chance picks a moment of the day and a kind of
 * weather, or it would be mostly drizzle.
 */
final class PictureMosaic extends FrameLayout {
    private static final int COLUMNS = 3;
    private static final int ROWS = 5;
    private static final float TILE_SHARE = 0.25f;
    private static final float GAP_SHARE = 0.024f;
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
    private static final float CAMERA_DISTANCE_DP = 2400;
    private static final float QUARTER_TURN = 90;
    /** How much of the page lies over the pictures on the left: white words stay readable on any of them. */
    private static final int VEIL_ALPHA = 120;
    private static final int VEIL_SCALE = 2;
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
    private static final String ARABIC = "ar";

    private final class Tile {
        final FrameLayout frame = new FrameLayout(getContext());
        final ImageView shown = picture();
        final ImageView coming = picture();
        final float centerX;
        final float centerY;
        boolean filled;
        boolean busy;

        Tile(float left, float top, int width, int height, float radius) {
            centerX = left + width / 2f;
            centerY = top + height / 2f;
            frame.addView(shown);
            frame.addView(coming);
            coming.setVisibility(GONE);
            frame.setTranslationX(left);
            frame.setTranslationY(top);
            frame.setCameraDistance(CAMERA_DISTANCE_DP * getResources().getDisplayMetrics().density);
            frame.setClipToOutline(true);
            frame.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
                }
            });
            grid.addView(frame, new LayoutParams(width, height));
        }

        private ImageView picture() {
            ImageView view = new ImageView(getContext());
            view.setScaleType(ImageView.ScaleType.CENTER_CROP);
            return view;
        }

        void dissolve(Bitmap picture) {
            coming.setImageBitmap(picture);
            coming.setAlpha(0f);
            coming.setVisibility(VISIBLE);
            coming.animate().alpha(1f).setDuration(DISSOLVE_MS).withEndAction(() -> {
                shown.setImageBitmap(picture);
                coming.setVisibility(GONE);
                filled = true;
                busy = false;
            }).start();
        }

        /** Edge-on is the moment to change the picture: it never reads mirrored. */
        void turn(Bitmap picture) {
            AccelerateDecelerateInterpolator ease = new AccelerateDecelerateInterpolator();
            frame.animate().rotationY(QUARTER_TURN).setDuration(TURN_MS / 2).setInterpolator(ease).withEndAction(() -> {
                shown.setImageBitmap(picture);
                frame.setRotationY(-QUARTER_TURN);
                frame.animate().rotationY(0).setDuration(TURN_MS / 2).setInterpolator(ease)
                    .withEndAction(() -> busy = false).start();
            }).start();
        }

        void clear() {
            coming.animate().cancel();
            frame.animate().cancel();
            frame.setRotationY(0);
            shown.setImageDrawable(null);
            coming.setImageDrawable(null);
            coming.setVisibility(GONE);
            filled = false;
            busy = false;
        }
    }

    private final Runnable change = this::change;
    private final List<Tile> tiles = new ArrayList<>();
    private final Map<String, List<String>> moments = new HashMap<>();
    private final List<String> opening = new ArrayList<>();
    private final Random chance = new Random();
    private final Drawable base = Look.plainBackdrop();
    private final FrameLayout grid;
    private List<String> files = new ArrayList<>();
    private String folder;
    private int next;
    private long started;

    PictureMosaic(Context context) {
        super(context);
        setBackground(Look.plainBackdrop());
        setVisibility(GONE);
        DisplayMetrics screen = getResources().getDisplayMetrics();
        int shown = Math.round(screen.widthPixels * FADE_TO);
        grid = new FrameLayout(context);
        addView(grid, new LayoutParams(shown, -1, Gravity.TOP | Gravity.LEFT));
        placeTiles(screen);
        ImageView veil = new ImageView(context);
        veil.setScaleType(ImageView.ScaleType.FIT_XY);
        veil.setImageBitmap(veil(screen, shown));
        grid.addView(veil, new LayoutParams(-1, -1));
    }

    /** The page itself, more and more opaque towards the rows: it is what the pictures fade into. */
    private Bitmap veil(DisplayMetrics screen, int shown) {
        int width = shown / VEIL_SCALE;
        int height = screen.heightPixels / VEIL_SCALE;
        Bitmap made = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(made);
        base.setBounds(0, 0, width, height);
        base.draw(canvas);
        Paint mask = new Paint();
        mask.setShader(new LinearGradient(screen.widthPixels * FADE_FROM / VEIL_SCALE, 0, width, 0,
            Color.argb(VEIL_ALPHA, 0, 0, 0), Color.BLACK, Shader.TileMode.CLAMP));
        mask.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        canvas.drawRect(0, 0, width, height, mask);
        return made;
    }

    /** Always the whole screen. The tiles most in view come first, for the weather's opening. */
    private void placeTiles(DisplayMetrics screen) {
        int width = Math.round(screen.widthPixels * TILE_SHARE);
        int height = width * 9 / 16;
        float gap = screen.widthPixels * GAP_SHARE;
        float radius = screen.widthPixels * RADIUS_SHARE;
        float middleX = screen.widthPixels * MIDDLE_COLUMN;
        float middleY = screen.heightPixels / 2f;
        for (int index = 0; index < COLUMNS * ROWS; index++) {
            int column = index / ROWS;
            float left = middleX - width / 2f + (column - 1) * (width + gap);
            float top = height * (START_Y + (column % 2) * COLUMN_STEP_Y) + index % ROWS * (height + gap);
            tiles.add(new Tile(left, top, width, height, radius));
        }
        tiles.sort(Comparator.comparingDouble(tile -> Math.abs(tile.centerX - middleX) + Math.abs(tile.centerY - middleY)));
    }

    /** Null stops it and lets the pictures go. */
    void show(String wanted) {
        if (wanted == null ? folder == null : wanted.equals(folder)) return;
        folder = wanted;
        removeCallbacks(change);
        files = new ArrayList<>();
        moments.clear();
        opening.clear();
        next = 0;
        for (Tile tile : tiles) tile.clear();
        setVisibility(wanted == null ? GONE : VISIBLE);
        if (wanted == null) return;
        // The rows change sides for Arabic, so the pictures do as well.
        setScaleX(ARABIC.equals(Language.code(getContext())) ? -1 : 1);
        sortMoments(Weather.pictures(getContext(), wanted + "/"), wanted);
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
        postDelayed(change, EVERY_MS);
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
        for (String name : getContext().getAssets().list(path)) {
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
            if (tile.filled && !tile.busy) free.add(tile);
        }
        Collections.shuffle(free);
        if (free.size() > 0) load(free.get(0), true);
        if (free.size() > 1) load(free.get(1), false);
        postDelayed(change, EVERY_MS);
    }

    private void load(Tile tile, boolean turns) {
        tile.busy = true;
        String file = nextFile();
        String asked = folder;
        Context context = getContext().getApplicationContext();
        Background.draw(() -> {
            Bitmap picture = decode(context, file);
            post(() -> {
                if (!asked.equals(folder)) return;
                if (picture == null) {
                    tile.busy = false;
                } else if (turns) {
                    tile.turn(picture);
                } else {
                    tile.dissolve(picture);
                }
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
}
