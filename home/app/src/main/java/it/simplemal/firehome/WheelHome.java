package it.simplemal.firehome;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.TimeAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The circular Home: the apps sit on a wheel whose centre is below the screen. The lit one is at
 * the top, the biggest, with a glow of its own colour around it; Left and Right turn the wheel.
 */
final class WheelHome extends FrameLayout {
    interface Listener {
        void open(String tag);
        void hold(String tag);
        void up();
        void down();
    }

    /**
     * Sizes in dp on the TV's 960 × 540: the lit circle, the wheel's radius, the free space between
     * two neighbouring circles and their sizes. The angles follow from these, so every gap is the same.
     */
    private static final class Shape {
        final int big;
        final int radius;
        final float[] scales;
        /** Angle of each place away from the top, in radians: 0 for the lit one, then one per place. */
        final float[] angles;

        Shape(int big, int radius, int gap, float[] scales) {
            this.big = big;
            this.radius = radius;
            this.scales = scales;
            angles = new float[scales.length];
            for (int place = 1; place < scales.length; place++) {
                float apart = big * (scales[place - 1] + scales[place]) / 2f + gap;
                angles[place] = angles[place - 1] + 2 * (float) Math.asin(Math.min(1f, apart / (2f * radius)));
            }
        }

        /** In between two places the angle goes straight from one to the other, so the turn is smooth. */
        float angle(float place) {
            float away = Math.abs(place);
            int below = Math.min((int) away, angles.length - 2);
            float part = away - below;
            float angle = angles[below] + (angles[below + 1] - angles[below]) * part;
            return place < 0 ? -angle : angle;
        }

        /** Where the lit circle's centre is. */
        int top() { return CENTRE - radius; }
    }

    /** The centre of both wheels, below the screen: with five apps the wheel is the same one, only a little smaller. */
    private static final int CENTRE = 566;
    private static final Shape THREE = new Shape(230, 330, 153, new float[]{1f, 0.62f, 0.5f, 0.45f});
    private static final Shape FIVE = new Shape(200, 300, 34, new float[]{1f, 0.62f, 0.5f, 0.44f, 0.4f});
    /** A spring with no bounce: it keeps its speed from one press to the next, so a held key turns smoothly. */
    private static final float SPRING = 15f;
    private static final float SUBSTEP = 0.008f;
    private static final float REST = 0.0005f;
    /** The glow is the lit app's own: gone within this part of the way down, and lit over the whole way up. */
    private static final float GLOW_OUT = 0.4f;
    private static final float GLOW_IN = 1f;
    private static final float MOVED = 0.0005f;
    private static final long PORTAL_MS = 460;
    /** While moving, a circle this close to the top would touch the lit one: it is not drawn, and comes back by the next place. */
    private static final float CROSS_HIDDEN = 0.6f;
    /** Coming back is a little slower than going: the colour is seen closing into its circle. */
    private static final long CLOSE_MS = 650;
    /** The glow reaches this far from the circle's centre, as a multiple of its radius. */
    private static final float GLOW_REACH = 1.45f;
    private static final int GLOW_ALPHA = 105;
    private static final float[] GLOW_STEPS = {1f, 0.9f, 0.75f, 0.35f, 0f};
    private static final float AWAY_GLOW = 0.5f;
    private static final float AWAY_SCALE = 0.94f;
    private static final float SWAY_DEGREES = 2.5f;
    private static final long SWAY_MS = 150;
    static final int REST_GLOW = Color.rgb(196, 202, 218);

    private final float density;
    private final List<ImageView> circles = new ArrayList<>();
    /** Behind each circle, its own glow; and whether the circle is on its way up to the top. */
    private final List<View> glows = new ArrayList<>();
    private boolean[] rising = new boolean[0];
    private float[] lastAway = new float[0];
    private final Map<String, Integer> colours = new HashMap<>();
    /** Where each app was last, by tag: a new set of circles starts from there and glides. */
    private final Map<String, Float> places = new HashMap<>();
    private final TimeAnimator ticker = new TimeAnimator();
    /** The wheel's turn, counted in apps, and where it is heading. */
    private float turned;
    private float speed;
    private int aim;
    /** Each circle's own way back to its place, after a move or a new set of circles. */
    private float[] apart = new float[0];
    private float[] apartSpeed = new float[0];
    private Shape shape = THREE;
    private int shown = 3;
    private int lit;
    private Listener listener;
    private Portal portal;
    /** An app is being moved: the one it changes place with crosses the top behind it, not over it. */
    private boolean moving;
    private boolean closing;
    private ObjectAnimator sway;

    WheelHome(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        setClipChildren(false);
        setFocusable(true);
        setDefaultFocusHighlightEnabled(false);
        ticker.setTimeListener((animation, total, delta) -> step(Math.min(delta, 50) / 1000f));
        setOnFocusChangeListener((view, focused) -> place());
    }

    void setListener(Listener listener) { this.listener = listener; }

    private int dp(float value) { return Math.round(value * density); }

    /** 3 or 5 circles in view. The circles come in the Home's order, and `litTag` goes to the top. */
    void setCircles(List<ImageView> views, int inView, String litTag) {
        for (ImageView circle : circles) removeView(circle);
        for (View glow : glows) removeView(glow);
        circles.clear();
        glows.clear();
        boolean newShape = inView != shown;
        shown = inView;
        shape = inView == 5 ? FIVE : THREE;
        int size = dp(shape.big);
        lit = 0;
        for (int index = 0; index < views.size(); index++) {
            ImageView circle = views.get(index);
            circle.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View view, Outline outline) {
                    outline.setOval(0, 0, view.getWidth(), view.getHeight());
                }
            });
            circle.setClipToOutline(true);
            circle.setScaleType(ImageView.ScaleType.FIT_XY);
            circles.add(circle);
            View glow = new View(getContext());
            glow.setOutlineProvider(null);
            glow.setBackground(glowFill(colourOf((String) circle.getTag())));
            glows.add(glow);
            int reach = glowReach();
            // Every glow under every circle: added first, they are drawn first.
            addView(glow, glows.size() - 1, new LayoutParams(2 * reach, 2 * reach, Gravity.TOP | Gravity.START));
            addView(circle, new LayoutParams(size, size, Gravity.TOP | Gravity.START));
            if (circle.getTag().equals(litTag)) lit = index;
        }
        int count = circles.size();
        aim = lit;
        turned = lit;
        speed = 0;
        apart = new float[count];
        apartSpeed = new float[count];
        rising = new boolean[count];
        lastAway = new float[count];
        for (int index = 0; index < count; index++) {
            Float before = places.get((String) circles.get(index).getTag());
            if (before != null && !newShape) apart[index] = wrap(before - wrap(index - lit));
        }
        setContentDescription(count > 0 ? circles.get(lit).getContentDescription() : null);
        place();
        ticker.start();
    }

    /** The colour of the glow around an app, found by the Home while it paints the circle. */
    void setGlow(String tag, int colour) {
        colours.put(tag, colour);
        for (int index = 0; index < circles.size(); index++) {
            if (tag.equals(circles.get(index).getTag())) glows.get(index).setBackground(glowFill(colour));
        }
    }

    String litTag() { return circles.isEmpty() ? null : (String) circles.get(lit).getTag(); }

    ImageView litCircle() { return circles.isEmpty() ? null : circles.get(lit); }

    /** Back to the first app, as Back does on the grid: the short way round. */
    void turnToFirst() {
        if (!circles.isEmpty() && lit != 0) turn(Math.round(wrap(-lit)));
    }

    void turn(int delta) {
        if (circles.isEmpty() || delta == 0) return;
        aim += delta;
        lit = Math.floorMod(aim, circles.size());
        setContentDescription(circles.get(lit).getContentDescription());
        if (!ticker.isRunning()) ticker.start();
    }

    private void step(float time) {
        for (float done = 0; done < time; done += SUBSTEP) {
            float slice = Math.min(SUBSTEP, time - done);
            speed += (SPRING * SPRING * (aim - turned) - 2 * SPRING * speed) * slice;
            turned += speed * slice;
            for (int index = 0; index < apart.length; index++) {
                apartSpeed[index] += (-SPRING * SPRING * apart[index] - 2 * SPRING * apartSpeed[index]) * slice;
                apart[index] += apartSpeed[index] * slice;
            }
        }
        boolean still = Math.abs(aim - turned) < REST && Math.abs(speed) < REST;
        for (int index = 0; index < apart.length; index++) {
            still &= Math.abs(apart[index]) < REST && Math.abs(apartSpeed[index]) < REST;
        }
        if (still) {
            // Back to a small number, so the turn stays exact however long the Home is used.
            aim = lit;
            turned = lit;
            speed = 0;
            for (int index = 0; index < apart.length; index++) {
                apart[index] = 0;
                apartSpeed[index] = 0;
            }
            ticker.end();
        }
        place();
    }

    /** Between -n/2 and n/2: the wheel has no ends, the last app is next to the first. */
    private float wrap(float place) {
        int count = Math.max(1, circles.size());
        float half = count / 2f;
        return ((place + half) % count + count) % count - half;
    }

    private void place() {
        int size = dp(shape.big);
        float middle = getWidth() / 2f;
        float radius = dp(shape.radius);
        float top = dp(shape.top());
        // Right to left the wheel is mirrored: Right still brings up the app on the right.
        float side = getLayoutDirection() == LAYOUT_DIRECTION_RTL ? -1 : 1;
        int each = (shown - 1) / 2;
        float half = circles.size() / 2f;
        for (int index = 0; index < circles.size(); index++) {
            ImageView circle = circles.get(index);
            float place = wrap(wrap(index - turned) + apart[index]);
            float away = Math.abs(place);
            places.put((String) circle.getTag(), place);
            double angle = shape.angle(place);
            float x = middle + side * (float) (radius * Math.sin(angle));
            float y = top + (float) (radius * (1 - Math.cos(angle)));
            float scale = scale(away) * (index == lit && !hasFocus() ? AWAY_SCALE : 1f);
            // Past the last one in view the circles fade, and where the wheel closes on itself they are gone.
            float alpha = clamp(each + 1 - away) * (circles.size() > shown ? clamp((half - away) * 2) : 1f);
            boolean crossing = moving && index != lit;
            if (crossing) alpha *= clamp((away - CROSS_HIDDEN) / (1 - CROSS_HIDDEN));
            circle.setTranslationX(x - size / 2f);
            circle.setTranslationY(y - size / 2f);
            circle.setScaleX(scale);
            circle.setScaleY(scale);
            circle.setAlpha(alpha);
            circle.setVisibility(alpha > 0 ? VISIBLE : INVISIBLE);
            circle.setTranslationZ(crossing && away < 1 ? 0 : dp(Math.max(0f, 6 - away)));
            View glow = glows.get(index);
            int reach = glowReach();
            glow.setTranslationX(x - reach);
            glow.setTranslationY(y - reach);
            glow.setScaleX(scale);
            glow.setScaleY(scale);
            float glowing = glowing(index, away) * alpha * (hasFocus() ? 1f : AWAY_GLOW);
            glow.setAlpha(glowing);
            glow.setVisibility(glowing > 0 ? VISIBLE : INVISIBLE);
        }
    }

    /**
     * Full at the top. On the way down it goes at once, faster and faster; on the way up it
     * comes in all along the climb, quickly at first and gently at the end.
     */
    private float glowing(int index, float away) {
        if (Math.abs(away - lastAway[index]) > MOVED) rising[index] = away < lastAway[index];
        lastAway[index] = away;
        if (rising[index]) {
            float left = 1 - clamp(away / GLOW_IN);
            return 1 - (1 - left) * (1 - left);
        }
        float gone = clamp(away / GLOW_OUT);
        return 1 - gone * gone;
    }

    private float scale(float away) {
        float[] scales = shape.scales;
        int below = Math.min((int) away, scales.length - 1);
        int above = Math.min(below + 1, scales.length - 1);
        float part = away - (int) away;
        return scales[below] + (scales[above] - scales[below]) * part;
    }

    private static float clamp(float value) { return Math.max(0f, Math.min(1f, value)); }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        place();
    }

    private int glowReach() { return Math.round(dp(shape.big) / 2f * GLOW_REACH); }

    private int colourOf(String tag) {
        Integer colour = colours.get(tag);
        return colour != null ? colour : REST_GLOW;
    }

    private Drawable glowFill(int colour) {
        GradientDrawable fill = new GradientDrawable();
        fill.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        fill.setGradientRadius(glowReach());
        int[] steps = new int[GLOW_STEPS.length];
        for (int step = 0; step < steps.length; step++) steps[step] = withAlpha(colour, Math.round(GLOW_ALPHA * GLOW_STEPS[step]));
        fill.setColors(steps);
        return fill;
    }

    private static int withAlpha(int colour, int alpha) {
        return Color.argb(alpha, Color.red(colour), Color.green(colour), Color.blue(colour));
    }

    /** While it is being moved the lit circle sways, like a card of the grid. */
    void setMoving(boolean moving) {
        this.moving = moving;
        if (sway != null) sway.cancel();
        sway = null;
        for (ImageView circle : circles) circle.setRotation(0);
        ImageView circle = litCircle();
        if (!moving || circle == null) return;
        sway = ObjectAnimator.ofFloat(circle, View.ROTATION, -SWAY_DEGREES, SWAY_DEGREES);
        sway.setDuration(SWAY_MS);
        sway.setRepeatCount(ValueAnimator.INFINITE);
        sway.setRepeatMode(ValueAnimator.REVERSE);
        sway.start();
    }

    /** Without the service: the lit circle opens into a disc of its colour that fills the screen, then the app starts. */
    void openPortal(Runnable then) {
        ImageView circle = litCircle();
        if (circle == null || portal != null) {
            then.run();
            return;
        }
        portal = new Portal(getContext(), colourOf(litTag()), getWidth() / 2f, dp(shape.top()), dp(shape.big) / 2f);
        addView(portal, new LayoutParams(-1, -1));
        portal.setTranslationZ(dp(20));
        ValueAnimator grow = ValueAnimator.ofFloat(0f, 1f);
        grow.setDuration(PORTAL_MS);
        grow.setInterpolator(new AccelerateInterpolator(1.4f));
        grow.addUpdateListener(step -> {
            if (portal != null) portal.setProgress((float) step.getAnimatedValue());
        });
        grow.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) { then.run(); }
        });
        grow.start();
    }

    boolean portalOpen() { return portal != null; }

    /** Where the lit circle is on the screen, and its colour: the window over the app starts from there. */
    float[] litSpot() {
        int[] at = new int[2];
        getLocationOnScreen(at);
        return new float[]{at[0] + getWidth() / 2f, at[1] + dp(shape.top()), dp(shape.big) / 2f};
    }

    int litColour() { return colourOf(litTag()); }

    /** The Home all in the app's colour at once, under the window that opens on the app. */
    void cover() {
        if (portal == null) {
            portal = new Portal(getContext(), litColour(), getWidth() / 2f, dp(shape.top()), dp(shape.big) / 2f);
            addView(portal, new LayoutParams(-1, -1));
            portal.setTranslationZ(dp(20));
        }
        portal.setProgress(1f);
    }

    /** Back on the Home: the colour closes back into the app's circle, as the opening went but backwards. */
    void closePortal(Runnable done) {
        if (portal == null || closing) {
            if (portal == null) done.run();
            return;
        }
        closing = true;
        Portal leaving = portal;
        ValueAnimator shrink = ValueAnimator.ofFloat(1f, 0f);
        shrink.setDuration(CLOSE_MS);
        shrink.setInterpolator(new DecelerateInterpolator());
        shrink.addUpdateListener(step -> leaving.setProgress((float) step.getAnimatedValue()));
        shrink.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                removeView(leaving);
                if (portal == leaving) portal = null;
                closing = false;
                done.run();
            }
        });
        shrink.start();
    }

    @Override public boolean onKeyDown(int code, KeyEvent event) {
        boolean mirrored = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                turn(mirrored ? 1 : -1);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                turn(mirrored ? -1 : 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (listener != null) listener.up();
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (listener != null) listener.down();
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (event.getRepeatCount() == 0) event.startTracking();
                return true;
            default:
                return super.onKeyDown(code, event);
        }
    }

    @Override public boolean onKeyLongPress(int code, KeyEvent event) {
        if (code != KeyEvent.KEYCODE_DPAD_CENTER && code != KeyEvent.KEYCODE_ENTER) return super.onKeyLongPress(code, event);
        if (listener != null && litTag() != null) listener.hold(litTag());
        return true;
    }

    @Override public boolean onKeyUp(int code, KeyEvent event) {
        if (code != KeyEvent.KEYCODE_DPAD_CENTER && code != KeyEvent.KEYCODE_ENTER) return super.onKeyUp(code, event);
        if (event.isTracking() && !event.isCanceled() && listener != null && litTag() != null) listener.open(litTag());
        return true;
    }

    @Override protected void onDetachedFromWindow() {
        ticker.end();
        super.onDetachedFromWindow();
    }

    /** A disc of the app's colour that widens from its circle to past the corners of the screen. */
    private static final class Portal extends View {
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int colour;
        private final float x;
        private final float y;
        private final float start;
        private float progress;

        Portal(Context context, int colour, float x, float y, float start) {
            super(context);
            this.colour = colour;
            this.x = x;
            this.y = y;
            this.start = start;
            ring.setColor(colour);
        }

        void setProgress(float value) {
            progress = value;
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            float far = (float) Math.hypot(Math.max(x, getWidth() - x), Math.max(y, getHeight() - y));
            float outer = start + (far * 1.05f - start) * progress;
            canvas.drawColor(withAlpha(colour, Math.round(120 * progress)));
            canvas.drawCircle(x, y, outer, ring);
        }
    }
}
