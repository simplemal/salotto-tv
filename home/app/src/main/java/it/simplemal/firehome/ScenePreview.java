package it.simplemal.firehome;

import android.content.Context;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.view.View;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The dynamic weather in a small frame, in random order: each scene comes in as it does on the Home,
 * clouds and rain moving, then the next one takes over.
 */
final class ScenePreview extends View {
    private static final String FOLDER = "meteo";
    private static final String DRAWING = ".svg";
    private static final double SHOWN_S = WeatherScene.ENTRY_S + 0.6;
    private static final double FADE_S = 0.4;
    /** Then it stays still, in case nobody is looking any more. */
    private static final long RUNS_FOR_MS = 300000;

    private final Random chance = new Random();
    private List<String> scenes;
    private WeatherScene scene;
    private long since;
    private long started;
    private int turn;
    private boolean loading;

    ScenePreview(Context context) {
        super(context);
    }

    void start() {
        if (getVisibility() == VISIBLE) return;
        setVisibility(VISIBLE);
        started = SystemClock.uptimeMillis();
        next();
    }

    void stop() {
        if (getVisibility() != VISIBLE) return;
        setVisibility(INVISIBLE);
        turn++;
        loading = false;
        replace(null);
    }

    /** Not at once: the last frame may still be on its way to the screen. */
    private void replace(WeatherScene coming) {
        WeatherScene gone = scene;
        scene = coming;
        if (gone != null) post(gone::release);
    }

    private List<String> scenes() {
        if (scenes != null) return scenes;
        List<String> found = new ArrayList<>();
        Context context = getContext();
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
        if (loading || all.isEmpty() || SystemClock.uptimeMillis() - started > RUNS_FOR_MS) return;
        loading = true;
        String name = all.get(chance.nextInt(all.size()));
        int asked = turn;
        int width = getWidth();
        int height = getHeight();
        Context context = getContext().getApplicationContext();
        Background.draw(() -> {
            WeatherScene loaded = WeatherScene.load(context, name, width, height);
            post(() -> {
                if (asked != turn) {
                    if (loaded != null) loaded.release();
                    return;
                }
                loading = false;
                if (loaded == null) {
                    next();
                    return;
                }
                replace(loaded);
                since = SystemClock.uptimeMillis();
                invalidate();
            });
        });
    }

    @Override protected void onDraw(Canvas canvas) {
        if (scene == null) return;
        double time = (SystemClock.uptimeMillis() - since) / 1000.0;
        scene.draw(canvas, time, 0, (float) Math.min(1, time / FADE_S));
        if (time < SHOWN_S) postInvalidateOnAnimation();
        else next();
    }
}
