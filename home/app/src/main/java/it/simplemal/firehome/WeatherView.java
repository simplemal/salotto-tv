package it.simplemal.firehome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.View;
import java.util.function.Consumer;

/**
 * Shows a weather scene behind the Home. It moves at full speed for three seconds each time the
 * Home comes back, then stays still: from there on it costs nothing, on a TV left on for hours.
 */
final class WeatherView extends View {
    interface Scene {
        /** `time` runs from the moment the Home comes back. */
        void draw(Canvas canvas, double time, float scroll, float alpha);
        boolean moves();
        void release();
    }

    /**
     * A change made in one step: the old scene stays until the new one is ready elsewhere too, for
     * example blurred, and then `place` swaps it at once. `paint` draws the new one settled, on any
     * canvas; it is null when the scene could not be loaded, and `place` then does nothing.
     */
    interface Swap {
        void ready(Runnable place, Consumer<Canvas> paint);
    }

    private static final double FADE_S = 2.5;
    private static final float SHADE_FROM = 0.30f;
    private static final float SHADE_FULL = 0.72f;
    private static final int SHADE_ALPHA = 115;
    /** The nearest layer moves about 19 pixels over the whole scroll: a smaller step cannot be seen. */
    private static final float SCROLL_STEP = 0.05f;

    /** A bright sky would swallow the white text and the dock at the bottom of the Home. */
    private final Paint shade = new Paint();
    /** Still, the scene is one texture: every frame of a scroll would otherwise draw all its layers. */
    private final Runnable flatten = () -> setLayerType(LAYER_TYPE_HARDWARE, null);
    private Scene scene;
    private Scene previous;
    private String wanted;
    private boolean running;
    private boolean flat;
    private double enteredAt = -WeatherScene.ENTRY_S;
    private double fadeStart;
    private float scroll;
    /** Hidden behind something opaque, the blur of the wheel: nothing to draw or move. */
    private boolean covered;
    private Runnable onShown;
    private Consumer<Boolean> onFade;

    WeatherView(Context context) {
        super(context);
        setVisibility(GONE);
    }

    void setOnShown(Runnable action) { onShown = action; }

    /** Hears when a picture starts and ends fading over another: two whole screens, the most this GPU can do. */
    void setOnFade(Consumer<Boolean> action) { onFade = action; }

    private static double now() { return SystemClock.uptimeMillis() / 1000.0; }

    private void unflatten() {
        removeCallbacks(flatten);
        flat = false;
        setLayerType(LAYER_TYPE_NONE, null);
    }

    void resume() {
        running = true;
        enteredAt = now();
        // A picture that arrived behind another page still has its whole fade to do.
        if (previous != null) fadeStart = enteredAt;
        unflatten();
        invalidate();
    }

    void pause() {
        running = false;
    }

    /** From the Home's scroll: the layers follow it, and only then does the still scene redraw. */
    void setScroll(float value) {
        if (scene != null && !scene.moves()) return;
        boolean resting = value == 0 || value == 1;
        if (resting ? value == scroll : Math.abs(value - scroll) < SCROLL_STEP) return;
        scroll = value;
        invalidate();
    }

    private static void release(Scene gone) {
        if (gone != null) gone.release();
    }

    void setCovered(boolean value) {
        if (covered == value) return;
        covered = value;
        unflatten();
        invalidate();
    }

    /** A file of the illustrations, or the name of a scene to draw, fading over the one before. */
    void show(String name) { show(name, null); }

    /** With a swap, the new scene replaces the old one in one step, when the swap says. True when a scene is on its way. */
    boolean show(String name, Swap swap) {
        if (name == null) {
            wanted = null;
            release(scene);
            release(previous);
            scene = null;
            previous = null;
            unflatten();
            setVisibility(GONE);
            return false;
        }
        if (name.equals(wanted)) return false;
        wanted = name;
        Context context = getContext().getApplicationContext();
        DisplayMetrics screen = getResources().getDisplayMetrics();
        Background.draw(() -> {
            Scene loaded = Look.picture(name) ? RasterScene.load(context, name)
                : WeatherScene.load(context, name, screen.widthPixels, screen.heightPixels);
            post(() -> {
                if (swap != null) swapIn(loaded, name, swap);
                else arrive(loaded, name);
            });
        });
        return true;
    }

    private void swapIn(Scene loaded, String name, Swap swap) {
        if (loaded == null) {
            if (name.equals(wanted)) wanted = null;
            swap.ready(() -> { }, null);
            return;
        }
        boolean[] placed = {false};
        swap.ready(() -> {
            if (placed[0]) return;
            placed[0] = true;
            if (!name.equals(wanted) || scene == loaded) {
                if (scene != loaded) loaded.release();
                return;
            }
            release(previous);
            release(scene);
            previous = null;
            scene = loaded;
            double time = now();
            fadeStart = time - FADE_S;
            enteredAt = time;
            unflatten();
            setVisibility(VISIBLE);
            invalidate();
            if (onShown != null) onShown.run();
        }, canvas -> paint(canvas, loaded));
    }

    /** The scene as it rests after its entrance, with the shade: for a blurred copy of what is behind the Home. */
    void paintSettled(Canvas canvas) {
        if (scene != null && getVisibility() == VISIBLE) paint(canvas, scene);
    }

    private void paint(Canvas canvas, Scene which) {
        which.draw(canvas, WeatherScene.ENTRY_S, scroll, 1);
        canvas.drawRect(0, canvas.getHeight() * SHADE_FROM, canvas.getWidth(), canvas.getHeight(), shade);
    }

    /** The first scene comes in from outside; a change of weather simply fades over the old one. */
    private void arrive(Scene loaded, String name) {
        if (loaded == null) {
            // Not here yet, while its collection is still coming: the next look tries it again.
            if (name.equals(wanted)) wanted = null;
            return;
        }
        if (!name.equals(wanted)) {
            loaded.release();
            return;
        }
        release(previous);
        previous = scene;
        scene = loaded;
        if (previous != null && onFade != null) onFade.accept(true);
        double time = now();
        // Only the first picture since power-on comes in slowly. Reloaded later, it is simply there.
        boolean quiet = previous == null && !Look.firstSinceBoot(getContext());
        fadeStart = quiet ? time - FADE_S : time;
        if (previous == null) enteredAt = time;
        unflatten();
        setVisibility(VISIBLE);
        invalidate();
        if (onShown != null) onShown.run();
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        shade.setShader(new LinearGradient(0, height * SHADE_FROM, 0, height * SHADE_FULL, Color.TRANSPARENT,
            Color.argb(SHADE_ALPHA, 0, 0, 0), Shader.TileMode.CLAMP));
    }

    @Override protected void onDraw(Canvas canvas) {
        Scene current = scene;
        if (current == null || covered) return;
        double time = now();
        double entered = time - enteredAt;
        float alpha = 1;
        double fade = (time - fadeStart) / FADE_S;
        // A still picture has no entrance of its own: the first one fades in as well.
        boolean fading = fade < 1 && (previous != null || !current.moves());
        if (previous != null) {
            if (fade >= 1) {
                Scene gone = previous;
                previous = null;
                post(gone::release);
                if (onFade != null) post(() -> onFade.accept(false));
            } else {
                previous.draw(canvas, entered, scroll, 1);
            }
        }
        if (fading) alpha = (float) fade;
        current.draw(canvas, entered, scroll, alpha);
        canvas.drawRect(0, getHeight() * SHADE_FROM, getWidth(), getHeight(), shade);
        // Every frame the screen can show, for a short while: smooth, and then nothing at all.
        boolean moving = fading || previous != null || current.moves() && entered < WeatherScene.ENTRY_S;
        if (running && moving) {
            postInvalidateOnAnimation();
        } else if (!moving && !flat && current.moves()) {
            flat = true;
            post(flatten);
        }
    }
}
