package it.simplemal.firehome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.ImageView;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** The dynamic weather has no pictures to show: its scenes are drawn one at a time, in random order, in a small frame. */
final class ScenePreview {
    private static final String FOLDER = "meteo";
    private static final String DRAWING = ".svg";
    private static final long EVERY_MS = 2000;
    private static final int FADE_MS = 400;
    /** Then it stays still, in case nobody is looking any more. */
    private static final long RUNS_FOR_MS = 300000;

    private final Context context;
    private final ImageView frame;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable next = this::next;
    private final Random chance = new Random();
    private List<String> scenes;
    private long started;
    private int asked;

    ScenePreview(ImageView frame) {
        this.frame = frame;
        context = frame.getContext().getApplicationContext();
    }

    void start() {
        stop();
        started = SystemClock.uptimeMillis();
        next();
    }

    void stop() {
        asked++;
        main.removeCallbacks(next);
    }

    private List<String> scenes() {
        if (scenes != null) return scenes;
        List<String> found = new ArrayList<>();
        try {
            for (String moment : context.getAssets().list(FOLDER)) {
                if (moment.indexOf('.') >= 0) continue;
                for (String file : context.getAssets().list(FOLDER + "/" + moment)) {
                    if (file.endsWith(DRAWING)) found.add(moment + "/" + file.substring(0, file.length() - DRAWING.length()));
                }
            }
        } catch (IOException e) {
            found.clear();
        }
        scenes = found;
        return found;
    }

    private void next() {
        List<String> all = scenes();
        if (all.isEmpty() || SystemClock.uptimeMillis() - started > RUNS_FOR_MS) return;
        String name = all.get(chance.nextInt(all.size()));
        int turn = asked;
        int width = frame.getWidth();
        int height = frame.getHeight();
        Background.draw(() -> {
            Bitmap picture = draw(name, width, height);
            main.post(() -> {
                if (turn != asked) return;
                if (picture != null) show(picture);
                main.postDelayed(next, EVERY_MS);
            });
        });
    }

    /** At the end of its entrance, when everything is in place. */
    private Bitmap draw(String name, int width, int height) {
        if (width == 0 || height == 0) return null;
        WeatherScene scene = WeatherScene.load(context, name, width, height);
        if (scene == null) return null;
        Bitmap picture = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        scene.draw(new Canvas(picture), WeatherScene.ENTRY_S, 0, 1);
        scene.release();
        return picture;
    }

    private void show(Bitmap picture) {
        Drawable coming = new BitmapDrawable(context.getResources(), picture);
        Drawable shown = frame.getDrawable();
        if (shown instanceof TransitionDrawable) shown = ((TransitionDrawable) shown).getDrawable(1);
        TransitionDrawable change = new TransitionDrawable(new Drawable[]{shown, coming});
        change.setCrossFadeEnabled(true);
        frame.setImageDrawable(change);
        change.startTransition(FADE_MS);
    }
}
