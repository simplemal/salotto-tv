package it.simplemal.firehome;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.format.DateFormat;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.transition.ChangeBounds;
import android.transition.Fade;
import android.transition.TransitionManager;
import android.transition.TransitionSet;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.json.JSONException;

/** Native launcher. No telemetry, billing library or Google services; the network is only for the weather and updates. */
public final class MainActivity extends CardActivity {
    private static final String ALL_APPS = "salotto:apps";
    private static final int DOCK_SIDE = 28;
    private static final int DOCK_HEIGHT_PADDING = 24;
    private static final int CAPTION_HEIGHT = 38;
    private static final int WEATHER_GAP = 12;
    /** Where the dock stops once a lower row has the focus, clear of the pill. */
    private static final int DOCK_TOP = 112;
    private static final int SNAP_FRACTION = 3;
    private static final int PILL_MS = 220;
    private static final long WEATHER_CHECK_MS = 60 * 1000L;

    /** The Home decides where to scroll: the first row stays low, like tvOS. */
    private static final class ShelfScroll extends ScrollView {
        private int shelf;
        private int startY;

        ShelfScroll(Context context) { super(context); }

        void setShelf(int position) { shelf = position; }

        @Override protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect) { return 0; }

        /** Between the two dock positions a throw would leave it halfway. */
        @Override public void fling(int velocity) {
            if (getScrollY() >= shelf) super.fling(velocity);
        }

        @Override public boolean dispatchTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) startY = getScrollY();
            boolean handled = super.dispatchTouchEvent(event);
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) settle();
            return handled;
        }

        /** A third of the way is enough to carry on; less goes back. */
        private void settle() {
            int y = getScrollY();
            if (y <= 0 || y >= shelf) return;
            boolean fromLower = startY >= shelf;
            boolean carriedOn = Math.abs(y - Math.min(startY, shelf)) * SNAP_FRACTION >= shelf;
            smoothScrollTo(0, fromLower == carriedOn ? 0 : shelf);
        }
    }

    private FrameLayout root;
    private ScrollView scroll;
    private LinearLayout page;
    private LinearLayout hero;
    private TextClock clock;
    private TextClock date;
    private FrameLayout pillBox;
    private ImageView pillButton;
    private ImageView controlButton;
    private ControlPanel panel;
    private WeatherView weatherView;
    private final Runnable freshPicture = () -> {
        if (showing) HomeCover.take(getWindow());
    };
    private final Runnable saver = () -> startActivity(new Intent(this, SaverActivity.class));
    private View panelShade;
    private boolean panelOpen;
    private TextView footer;
    private boolean reenabledChecked;
    private long timeSeen;
    private boolean stoppedWaiting;
    private View clockShade;
    private View pillShade;
    private static final int CORNER_RADIUS = 460;
    private static final int CORNER_ALPHA = 105;
    /** Long enough for the Home to be drawn, weather scene included. */
    private static final long FIRST_PICTURE_MS = 4000;
    private static final long FRESH_PICTURE_MS = 400;
    private static final int SAVER_FALLBACK_MS = 300000;
    private static final long SAVER_AHEAD_MS = 10000;
    private static final long SAVER_SHORTEST_MS = 30000;
    private static final float JIGGLE_DEGREES = 0.5f;
    private static final long JIGGLE_TURN_MS = 130;
    private static final float JIGGLE_BOB_PX = 1f;
    private static final long JIGGLE_BOB_MS = 170;
    /** For the cover of a Home press: over the Home itself its picture has to be taken at once. */
    static volatile boolean showing;
    static final String OPEN_PANEL = "open_panel";
    private static final long OFFER_START_MS = 3000;
    private static final String CLOCK_FILE = "clock";
    private static final String TIME_SEEN = "seen";
    private static final long TIME_SAVE_MS = 3600000;
    private static final long CLOCK_WAIT_MS = 60000;
    private static final long CLOCK_FADE_MS = 400;
    private static final int FOOTER_SP = 8;
    private static final float FOOTER_SPACING = 0.06f;
    private static final int FOOTER_MARGIN = 10;
    private static final int FOOTER_LOGO = 14;
    private static final int FOOTER_GAP = 24;
    private static final int FOOTER_HEIGHT = 20;
    private static final int FOOTER_RADIUS = 12;
    private static final int FOOTER_INK = Color.rgb(205, 205, 205);
    private boolean bigClock;
    private String weatherSymbol;
    private boolean pillDrawn;
    private boolean pillClock;
    private String pillSymbol;
    private LinearLayout pillRow;
    private TextView pillWeather;
    private TextClock pillDate;
    private TextClock pillTime;
    private LinearLayout weatherRow;
    private TextView weather;
    private TextView forecast;
    private TextView hint;
    private final Handler ticker = new Handler(Looper.getMainLooper());
    private final Runnable weatherCheck = this::checkWeather;
    private List<String> home;
    private List<String> shownApps = new ArrayList<>();
    private String shown;
    private String lookShown;
    private LinearLayout dock;
    private String plainBackdrop;
    private String moving;
    private boolean noUpdateLeft;
    private boolean inFront;
    private final Runnable offerStart = this::offerStart;
    private AnimatorSet jiggle;
    private int columns;
    private int shelf;
    private int rowHeight;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        TaskService.keysByDefault(this);
        TaskService.quickAnimationsByDefault(this);
        TaskService.keepAdbKey(this);
        if (FirstRunActivity.due(this)) {
            startActivity(new Intent(this, FirstRunActivity.class));
            finish();
            return;
        }
        root = new FrameLayout(this);
        weatherView = new WeatherView(this);
        // Once the scene covers the screen, the plain background underneath is wasted drawing.
        weatherView.setOnShown(() -> root.setBackground(null));
        root.addView(weatherView, new FrameLayout.LayoutParams(-1, -1));
        clockShade = cornerShade(0);
        pillShade = cornerShade(1);
        pillShade.setAlpha(0);

        hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        clock = new TextClock(this);
        clock.setFormat24Hour("HH:mm");
        clock.setFormat12Hour("HH:mm");
        clock.setTextColor(Color.WHITE);
        clock.setShadowLayer(dp(6), 0, dp(2), Color.argb(120, 0, 0, 0));
        hero.addView(clock);
        date = new TextClock(this);
        String longDate = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM");
        date.setFormat24Hour(longDate);
        date.setFormat12Hour(longDate);
        date.setTextSize(20);
        date.setTextColor(Color.WHITE);
        // Thin letters over a detailed picture: only a tight, dark shadow keeps them apart from it.
        date.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        date.setShadowLayer(dp(3), 0, dp(1), Color.argb(225, 0, 0, 0));
        hero.addView(date);
        timeSeen = getSharedPreferences(CLOCK_FILE, MODE_PRIVATE).getLong(TIME_SEEN, 0);
        revealClock();
        root.postDelayed(() -> {
            stoppedWaiting = true;
            revealClock();
        }, CLOCK_WAIT_MS);
        for (TextClock line : new TextClock[]{clock, date}) line.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable text) {
                alignInk(line);
                revealClock();
            }
        });
        FrameLayout.LayoutParams heroPlace = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        heroPlace.leftMargin = dp(DOCK_MARGIN + DOCK_SIDE);
        heroPlace.topMargin = dp(40);
        root.addView(hero, heroPlace);
        root.addView(weatherRow(), new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        // Both fade at every step of the scroll: as layers that is one texture, not an offscreen pass.
        hero.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        weatherRow.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        scroll = new ShelfScroll(this);
        scroll.setFillViewport(true);
        scroll.setClipChildren(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOnScrollChangeListener((view, x, y, oldX, oldY) -> {
            float alpha = shelf > 0 ? Math.max(0f, 1f - 2f * y / shelf) : 1f;
            weatherView.setScroll(shelf > 0 ? Math.min(1f, (float) y / shelf) : 0);
            hero.setAlpha(alpha);
            clockShade.setAlpha(alpha);
            weatherRow.setAlpha(alpha);
            showPillClock();
        });
        page = page();
        scroll.addView(page);
        root.addView(scroll);
        // While the backdrop fades the cards cost one picture, instead of all their shadows and glass.
        weatherView.setOnFade(fading -> scroll.setLayerType(fading ? View.LAYER_TYPE_HARDWARE : View.LAYER_TYPE_NONE, null));

        root.addView(pill(), pillPlace());
        footer = footer();
        panelShade = new View(this);
        panelShade.setVisibility(View.GONE);
        panelShade.setOnClickListener(view -> showPanel(false));
        root.addView(panelShade, new FrameLayout.LayoutParams(-1, -1));
        panel = ControlPanel.addTo(this, root, controlButton, true);
        panel.setVisibility(View.GONE);
        panel.leadDownTo(this::firstCard);
        root.getViewTreeObserver().addOnGlobalFocusChangeListener((before, after) -> followFocus(after));
        hint = label(getString(R.string.main_move_hint), 16);
        hint.setVisibility(View.GONE);
        hint.setShadowLayer(dp(4), 0, dp(1), Color.argb(160, 0, 0, 0));
        FrameLayout.LayoutParams top = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        top.topMargin = dp(40);
        root.addView(hint, top);
        setContentView(root);
    }

    /** Today on the left and tomorrow on the right, resting on the dock and aligned to its cards. */
    private View weatherRow() {
        int side = dp(DOCK_MARGIN + DOCK_SIDE);
        weatherRow = new LinearLayout(this);
        weatherRow.setPadding(side, 0, side, 0);
        weather = label("", 17);
        weather.setShadowLayer(dp(5), 0, dp(1), Color.argb(190, 0, 0, 0));
        weatherRow.addView(weather, new LinearLayout.LayoutParams(0, -2, 1));
        forecast = label("", 15);
        forecast.setShadowLayer(dp(5), 0, dp(1), Color.argb(190, 0, 0, 0));
        weatherRow.addView(forecast);
        return weatherRow;
    }

    /** Glass pill: weather, date and time when the clock is out of view, then the settings. */
    private View pill() {
        pillRow = new LinearLayout(this);
        pillRow.setGravity(Gravity.CENTER_VERTICAL);
        // The enlarged button would be cut into a square at the row's padding.
        pillRow.setClipToPadding(false);
        pillRow.setClipChildren(false);
        pillDate = new TextClock(this);
        String shortDate = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEdMMM");
        pillDate.setFormat24Hour(shortDate);
        pillDate.setFormat12Hour(shortDate);
        pillDate.setTextSize(16);
        pillDate.setTextColor(Color.WHITE);
        pillDate.setPadding(0, 0, dp(10), 0);
        pillDate.setVisibility(View.GONE);
        pillWeather = label("", 21);
        pillWeather.setPadding(0, 0, dp(10), 0);
        pillWeather.setVisibility(View.GONE);
        pillRow.addView(pillWeather);
        pillRow.addView(pillDate);
        pillTime = new TextClock(this);
        pillTime.setFormat24Hour("HH:mm");
        pillTime.setFormat12Hour("HH:mm");
        pillTime.setTextSize(16);
        pillTime.setTextColor(Color.WHITE);
        pillTime.setPadding(0, 0, dp(10), 0);
        pillRow.addView(pillTime);
        ImageView settings = pillIcon(R.drawable.ic_settings, getString(R.string.first_does_settings));
        settings.setOnClickListener(v -> open(new Intent(this, SettingsActivity.class)));
        controlButton = pillIcon(R.drawable.ic_control, getString(R.string.first_does_panel));
        // A mouse moves no focus, so here the click is what opens and closes the panel.
        controlButton.setOnClickListener(v -> {
            if (v.isInTouchMode()) showPanel(!panelOpen);
            else panel.first().requestFocus();
        });
        controlButton.setOnKeyListener((view, code, event) -> {
            if (code != KeyEvent.KEYCODE_DPAD_DOWN) return false;
            if (event.getAction() == KeyEvent.ACTION_DOWN) panel.first().requestFocus();
            return true;
        });
        LinearLayout.LayoutParams beside = new LinearLayout.LayoutParams(dp(PILL_ICON), dp(PILL_ICON));
        beside.setMarginEnd(dp(PILL_ICON_GAP));
        pillRow.addView(controlButton, beside);
        pillRow.addView(settings, new LinearLayout.LayoutParams(dp(PILL_ICON), dp(PILL_ICON)));
        pillButton = settings;
        pillBox = pillBox(pillRow);
        return pillBox;
    }

    /** Open while the focus is on its button or inside it. Touching clears the focus: that says nothing. */
    private void followFocus(View focus) {
        if (focus != null) showPanel(focus == controlButton || isInside(focus, panel));
    }

    private boolean isInside(View view, View parent) {
        for (View step = view; step != null; step = step.getParent() instanceof View ? (View) step.getParent() : null) {
            if (step == parent) return true;
        }
        return false;
    }

    private void showPanel(boolean open) {
        if (open == panelOpen) return;
        panelOpen = open;
        panelShade.setVisibility(open ? View.VISIBLE : View.GONE);
        panel.hideHint();
        if (!open) panel.closed();
        panel.animate().cancel();
        if (open) {
            panel.refresh();
            panel.setVisibility(View.VISIBLE);
            panel.setAlpha(0f);
            panel.setTranslationY(-dp(12));
            panel.animate().alpha(1f).translationY(0).setDuration(PILL_MS).start();
        } else {
            panel.animate().alpha(0f).translationY(-dp(12)).setDuration(PILL_MS)
                .withEndAction(() -> panel.setVisibility(View.GONE)).start();
        }
    }

    /** Time and date move to the pill only while the big clock cannot be seen: never both. */
    /** A soft dark corner under the time, so that it stands out on any picture. `x` is 0 or 1. */
    private View cornerShade(float x) {
        GradientDrawable fill = new GradientDrawable();
        fill.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        fill.setGradientCenter(x, 0);
        fill.setGradientRadius(dp(CORNER_RADIUS));
        fill.setColors(new int[]{Color.argb(CORNER_ALPHA, 0, 0, 0), Color.TRANSPARENT});
        View shade = new View(this);
        shade.setBackground(fill);
        root.addView(shade, new FrameLayout.LayoutParams(dp(CORNER_RADIUS), dp(CORNER_RADIUS),
            Gravity.TOP | (x == 0 ? Gravity.START : Gravity.END)));
        return shade;
    }

    /** The last thing on the page, so that no row of cards can ever reach it. The glass comes with the focus. */
    private TextView footer() {
        TextView view = label(getString(R.string.main_footer, BuildVersion.full(this)), FOOTER_SP);
        view.setAllCaps(true);
        view.setLetterSpacing(FOOTER_SPACING);
        view.setTextColor(FOOTER_INK);
        view.setShadowLayer(dp(3), 0, dp(1), Color.argb(200, 0, 0, 0));
        view.setPadding(dp(12), dp(4), dp(12), dp(4));
        view.setFocusable(true);
        view.setClickable(true);
        view.setDefaultFocusHighlightEnabled(false);
        Drawable logo = getDrawable(R.drawable.ic_salotto);
        logo.setBounds(0, 0, dp(FOOTER_LOGO), dp(FOOTER_LOGO));
        view.setCompoundDrawablesRelative(logo, null, null, null);
        view.setCompoundDrawablePadding(dp(5));
        view.setGravity(Gravity.CENTER_VERTICAL);
        int radius = dp(FOOTER_RADIUS);
        view.setOnFocusChangeListener((changed, focused) -> {
            view.setBackground(focused ? new LayerDrawable(new Drawable[]{glass(radius), glassEdge(radius)}) : null);
            view.setTextColor(focused ? Color.WHITE : FOOTER_INK);
            if (focused) scroll.smoothScrollTo(0, page.getHeight() - scroll.getHeight());
        });
        view.setOnClickListener(clicked -> open(new Intent(this, AboutActivity.class)));
        return view;
    }

    private void showPillClock() {
        boolean raised = scroll.getScrollY() > shelf / 2;
        boolean clockInView = bigClock && !raised;
        String symbol = raised ? weatherSymbol : null;
        // Called at every step of a scroll, and almost every step changes nothing.
        if (pillDrawn && clockInView == pillClock && Objects.equals(symbol, pillSymbol)) return;
        pillDrawn = true;
        pillClock = clockInView;
        pillSymbol = symbol;
        animatePill();
        int time = clockInView ? View.GONE : View.VISIBLE;
        clockShade.setVisibility(bigClock ? View.VISIBLE : View.GONE);
        pillShade.animate().alpha(clockInView ? 0 : 1).setDuration(PILL_MS).start();
        pillDate.setVisibility(time);
        pillTime.setVisibility(time);
        pillWeather.setVisibility(symbol != null ? View.VISIBLE : View.GONE);
        if (symbol != null) pillWeather.setText(symbol);
        pillRow.setPadding(clockInView ? dp(6) : dp(16), dp(6), dp(6), dp(6));
    }

    private void animatePill() {
        if (!pillBox.isLaidOut()) return;
        TransitionSet change = new TransitionSet();
        change.setOrdering(TransitionSet.ORDERING_TOGETHER);
        change.addTransition(new Fade()).addTransition(new ChangeBounds());
        change.addTarget(pillBox).addTarget(pillRow).addTarget(pillDate).addTarget(pillTime)
            .addTarget(pillWeather).addTarget(pillButton).addTarget(controlButton);
        change.setDuration(PILL_MS);
        TransitionManager.beginDelayedTransition(root, change);
    }

    /** Menu held on the Home. */
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (!intent.getBooleanExtra(OPEN_PANEL, false)) return;
        View from = getCurrentFocus();
        boolean onCards = from != null && from != controlButton && !isInside(from, panel);
        scroll.smoothScrollTo(0, 0);
        showPanel(true);
        panel.openEmpty(onCards ? from : firstCard());
    }

    /** The cover needs the Home as it is now, and a press leaves no time to take it. */
    @Override public void onUserInteraction() {
        root.removeCallbacks(freshPicture);
        root.postDelayed(freshPicture, FRESH_PICTURE_MS);
        waitForSaver();
    }

    /** A little before Fire OS's own turn: where it would start Ambient, or put the Stick to sleep. */
    private void waitForSaver() {
        root.removeCallbacks(saver);
        if (!showing || !Look.ownSaver(this) || Settings.Secure.getInt(getContentResolver(), "screensaver_enabled", 1) != 1) return;
        int after = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, SAVER_FALLBACK_MS);
        root.postDelayed(saver, Math.max(SAVER_SHORTEST_MS, after - SAVER_AHEAD_MS));
    }

    @Override protected void onResume() {
        super.onResume();
        inFront = true;
        showing = true;
        HomeCover.homeShown(getWindow());
        if (panelOpen) panel.refresh();
        root.postOnAnimation(TaskService::homeBack);
        if (!HomeCover.ready()) root.postDelayed(() -> HomeCover.take(getWindow()), FIRST_PICTURE_MS);
        home = HomeApps.load(this);
        List<String> apps = visible();
        String look = Look.signature(this);
        if (!look.equals(lookShown)) {
            lookShown = look;
            applyLook();
        }
        if (!state(apps).equals(shown)) render(focusedTag(), apps);
        Look.backHome(this);
        waitForSaver();
        ticker.post(weatherCheck);
        weatherView.resume();
        root.postDelayed(offerStart, OFFER_START_MS);
    }

    /** After the backdrop has come in, or the question would open over an empty Home. */
    private void offerStart() {
        if (!reenabledChecked) {
            reenabledChecked = true;
            Background.run(() -> {
                ArrayList<String> found = Reenabled.find(this);
                if (found.isEmpty()) return;
                runOnUiThread(() -> {
                    if (!inFront) return;
                    startActivity(new Intent(this, ReenabledActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .putStringArrayListExtra(ReenabledActivity.REENABLED, found));
                });
            });
        }
    }

    /** With no state file there is no update to look after: only this process could start one. */
    private void offerUpdate() {
        if (!noUpdateLeft) {
            noUpdateLeft = !Updater.state(this).exists();
            if (!noUpdateLeft && Updater.interrupted(this)) {
                startActivity(new Intent(this, UpdateActivity.class).putExtra(UpdateActivity.EXTRA_AFTER, true));
                return;
            }
        }
        if (!Updater.due()) return;
        Background.run(() -> {
            if (Updater.offer(this) != null) runOnUiThread(this::showOffer);
        });
    }

    /** Only over the Home: the check may end just after a video has started. */
    private void showOffer() {
        if (hasWindowFocus()) startActivity(new Intent(this, UpdateActivity.class));
        else Updater.askAgain();
    }

    /** Runs every minute: the same backdrop is not fetched and drawn again. */
    private void showBackdrop() {
        String scene = Look.weatherPictures(this) ? Weather.picture(this)
            : Look.weatherBackdrop(this) || Weather.testing(this) ? Weather.scene(this) : null;
        String plain = scene == null ? Look.backdrop(this) : null;
        String before = plainBackdrop;
        plainBackdrop = plain;
        if (plain != null && !plain.equals(before)) {
            Look.show(this, picture -> {
                if (plain.equals(plainBackdrop)) root.setBackground(picture);
            });
        }
        weatherView.show(scene);
    }

    /** Runs while the Home is in front: a TV can stay on it for hours. */
    private void checkWeather() {
        noteTime();
        root.postDelayed(freshPicture, FRESH_PICTURE_MS);
        showWeather();
        showBackdrop();
        if (Weather.due(this)) {
            Background.run(() -> {
                try {
                    Weather.refresh(getApplicationContext());
                    runOnUiThread(this::showWeather);
                } catch (IOException | JSONException e) {
                    // The last reading stays on screen; the next check tries again.
                }
            });
        }
        offerUpdate();
        ticker.postDelayed(weatherCheck, WEATHER_CHECK_MS);
    }

    private void showWeather() {
        String line = Weather.line(this);
        weather.setVisibility(line != null ? View.VISIBLE : View.GONE);
        if (line != null && !line.contentEquals(weather.getText())) weather.setText(line);
        String tomorrow = Weather.tomorrow(this);
        forecast.setVisibility(tomorrow != null ? View.VISIBLE : View.GONE);
        if (tomorrow != null && !tomorrow.contentEquals(forecast.getText())) forecast.setText(tomorrow);
        weatherSymbol = Weather.symbol(this);
        showPillClock();
    }

    @Override protected void onPause() {
        super.onPause();
        showing = false;
        root.removeCallbacks(offerStart);
        HomeCover.take(getWindow());
        inFront = false;
        Look.leaveHome(this);
        root.removeCallbacks(saver);
        ticker.removeCallbacks(weatherCheck);
        weatherView.pause();
        if (moving != null) stopMoving();
    }

    private String state(List<String> apps) {
        return TextUtils.join(",", apps) + "|" + Look.cardsSignature(this);
    }

    /**
     * After a restart the Stick's clock runs weeks behind until the network answers. It is never
     * earlier than what the Home has already seen, so until then the time stays hidden.
     */
    private void revealClock() {
        if (System.currentTimeMillis() < timeSeen && !stoppedWaiting) {
            clock.setAlpha(0);
            date.setAlpha(0);
        } else if (clock.getAlpha() < 1) {
            clock.animate().alpha(1).setDuration(CLOCK_FADE_MS).start();
            date.animate().alpha(1).setDuration(CLOCK_FADE_MS).start();
        }
    }

    private void noteTime() {
        long now = System.currentTimeMillis();
        if (now - timeSeen < TIME_SAVE_MS) return;
        timeSeen = now;
        getSharedPreferences(CLOCK_FILE, MODE_PRIVATE).edit().putLong(TIME_SEEN, now).apply();
    }

    /** Each digit and letter has its own room on the left, the 1 most of all: the ink lines up. */
    private static void alignInk(TextView line) {
        Rect ink = new Rect();
        String text = line.getText().toString();
        line.getPaint().getTextBounds(text, 0, text.length(), ink);
        line.setTranslationX(-ink.left);
    }

    private void applyLook() {
        showBackdrop();
        if (dock != null) dock.setBackground(dockGlass(dp(26), Look.weatherBackdrop(this)));
        columns = Look.columns(this);
        clock.setTypeface(Typeface.create(Look.FONT_FAMILIES[Look.font(this)], Typeface.NORMAL));
        clock.setTextSize(Look.SIZES[Look.size(this)]);
        alignInk(clock);
        bigClock = Look.clock(this);
        clock.setVisibility(bigClock ? View.VISIBLE : View.GONE);
        date.setVisibility(bigClock && Look.date(this) ? View.VISIBLE : View.GONE);
        String pattern = Look.timePattern(this);
        for (TextClock time : new TextClock[]{clock, pillTime}) {
            time.setFormat24Hour(pattern);
            time.setFormat12Hour(pattern);
        }
        showPillClock();
    }

    private List<String> visible() {
        List<String> apps = new ArrayList<>();
        for (String pkg : home) {
            if (HomeApps.SETTINGS.equals(pkg) || launchIntent(pkg) != null) apps.add(pkg);
        }
        return apps;
    }

    private void render(String focus) { render(focus, visible()); }

    private void render(String focus, List<String> apps) {
        stopJiggle();
        page.removeAllViews();
        startCards();
        shownApps = apps;
        shown = state(apps);
        int inset = dp(DOCK_SIDE);
        int width = cardWidth(columns, dp(DOCK_MARGIN) + inset);
        int imageHeight = width * 9 / 16;
        rowHeight = imageHeight + dp(CAPTION_HEIGHT);

        // The first row rests low: below it only a third of the next row shows.
        int screen = getResources().getDisplayMetrics().heightPixels;
        int dockPadding = dp(DOCK_HEIGHT_PADDING);
        int dockHeight = imageHeight + 2 * dockPadding;
        int rowGap = dp(20);
        int top = screen - dockHeight - rowGap - imageHeight / 3;
        shelf = top - dp(DOCK_TOP);
        ((ShelfScroll) scroll).setShelf(shelf);
        page.setPadding(dp(DOCK_MARGIN), top, dp(DOCK_MARGIN), 0);
        ((FrameLayout.LayoutParams) weatherRow.getLayoutParams()).bottomMargin = screen - top + dp(WEATHER_GAP);
        weatherRow.requestLayout();
        page.setMinimumHeight(shelf + screen);

        View first = null;
        View target = null;
        LinearLayout row = null;
        int count = apps.size() + 1;
        for (int index = 0; index < count; index++) {
            if (index % columns == 0) {
                row = row(page);
                row.setPadding(inset, index == 0 ? dockPadding : 0, inset, index == 0 ? dockPadding : 0);
                if (index == 0) {
                    dock = row;
                    dock.setBackground(dockGlass(dp(26), Look.weatherBackdrop(this)));
                } else if (index == columns) {
                    ((LinearLayout.LayoutParams) row.getLayoutParams()).topMargin = rowGap;
                }
            }
            View tile = index == apps.size() ? allAppsCard(row, width) : homeCard(row, apps.get(index), width);
            if (index < columns) ((LinearLayout) tile).getChildAt(1).setVisibility(View.GONE);
            if (first == null) first = tile;
            if (focus != null && focus.equals(tile.getTag())) target = tile;
        }
        // At the bottom of the screen under the raised shelves; with many rows, past the last one.
        int rows = (count + columns - 1) / columns;
        int cards = top + dockHeight + (rows > 1 ? rowGap + (rows - 1) * rowHeight : 0);
        LinearLayout.LayoutParams foot = new LinearLayout.LayoutParams(-2, -2);
        foot.gravity = Gravity.CENTER_HORIZONTAL;
        foot.bottomMargin = dp(FOOTER_MARGIN);
        foot.topMargin = Math.max(dp(FOOTER_GAP), shelf + screen - cards - dp(FOOTER_HEIGHT) - dp(FOOTER_MARGIN));
        page.addView(footer, foot);
        (target != null ? target : first).requestFocus();
        if (moving != null && target != null) startJiggle(target);
    }

    private View homeCard(LinearLayout row, String pkg, int width) {
        if (HomeApps.SETTINGS.equals(pkg)) {
            return card(row, pkg, getString(R.string.first_does_settings), getDrawable(R.drawable.ic_settings), null, SHADED, width,
                () -> open(new Intent(this, SettingsActivity.class)), () -> showMenu(pkg));
        }
        return appCard(row, HomeApps.label(this, pkg), pkg, width, () -> showMenu(pkg));
    }

    /** Pinned last: it cannot be moved or removed, so every app stays reachable. */
    private View allAppsCard(LinearLayout row, int width) {
        return card(row, ALL_APPS, getString(R.string.main_all_apps), getDrawable(R.drawable.ic_apps), null, GLASS, width,
            () -> open(new Intent(this, AppsActivity.class)), null);
    }

    /** Only one card goes: the whole grid does not have to be built again for it. */
    @Override protected void appRemoved() { render(focusedTag()); }

    /** The dock shows only the artwork, as on tvOS. */
    @Override protected boolean showsCaption(View tile) {
        return tile.getParent() != page.getChildAt(0);
    }

    @Override protected void onCardFocus(View tile) {
        int index = page.indexOfChild((View) tile.getParent());
        int rowsOnScreen = 2;
        int target = index == 0 ? 0 : shelf + Math.max(0, index - rowsOnScreen) * rowHeight;
        scroll.smoothScrollTo(0, target);
    }

    /** Like tvOS: the card being moved sways gently until the move is confirmed. */
    private void startJiggle(View tile) {
        View image = ((LinearLayout) tile).getChildAt(0);
        // Rebuilt on every step: skip the focus animation so the card does not pop.
        image.animate().cancel();
        image.setScaleX(focusScale());
        image.setScaleY(focusScale());
        // The same animation raises the shadow, and it has just been stopped.
        if (tile.getBackground() instanceof CardShadow) ((CardShadow) tile.getBackground()).setLift(1);
        // A layer turns each frame into a cheap texture transform instead of a redraw.
        image.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        jiggle = new AnimatorSet();
        // As on iOS: a tiny quick turn, and a smaller bob on another beat so it never looks mechanical.
        jiggle.playTogether(
            sway(ObjectAnimator.ofFloat(image, View.ROTATION, -JIGGLE_DEGREES, JIGGLE_DEGREES), JIGGLE_TURN_MS),
            sway(ObjectAnimator.ofFloat(image, View.TRANSLATION_Y, -JIGGLE_BOB_PX, JIGGLE_BOB_PX), JIGGLE_BOB_MS));
        jiggle.start();
    }

    private ObjectAnimator sway(ObjectAnimator animator, long duration) {
        animator.setDuration(duration);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.REVERSE);
        return animator;
    }

    private void stopJiggle() {
        if (jiggle != null) jiggle.cancel();
        jiggle = null;
    }

    private void showMenu(String pkg) {
        boolean removable = !HomeApps.SETTINGS.equals(pkg);
        String title = removable ? HomeApps.label(this, pkg) : getString(R.string.first_does_settings);
        String[] choices = removable ? new String[]{getString(R.string.main_move), getString(R.string.card_remove_from_home),
                getString(R.string.card_app_info), getString(R.string.card_uninstall)}
            : new String[]{getString(R.string.main_move)};
        dialog()
            .setTitle(title)
            .setItems(choices, (dialog, choice) -> {
                if (choice == 0) {
                    moving = pkg;
                    hint.setVisibility(View.VISIBLE);
                    render(pkg);
                } else if (choice == 1) {
                    home.remove(pkg);
                    HomeApps.save(this, home);
                    render(null);
                } else if (choice == 2) {
                    appInfo(pkg);
                } else {
                    uninstall(pkg);
                }
            })
            .show();
    }

    private void move(int delta) {
        int from = shownApps.indexOf(moving);
        int to = from + delta;
        if (from < 0 || to < 0 || to >= shownApps.size()) return;
        String other = shownApps.get(to);
        Map<String, int[]> before = new HashMap<>();
        before.put(moving, spot(moving));
        before.put(other, spot(other));
        Collections.swap(shownApps, from, to);
        List<String> order = new ArrayList<>(shownApps);
        for (String pkg : home) if (!order.contains(pkg)) order.add(pkg);
        home = order;
        HomeApps.save(this, home);
        stopJiggle();
        View tile = page.findViewWithTag(moving);
        swap(tile, page.findViewWithTag(other));
        tile.requestFocus();
        startJiggle(tile);
        slide(before);
    }

    /** Only the two cards change place: rebuilding the grid at each press would cost all its artwork. */
    private void swap(View tile, View other) {
        LinearLayout tileRow = (LinearLayout) tile.getParent();
        LinearLayout otherRow = (LinearLayout) other.getParent();
        int tileAt = tileRow.indexOfChild(tile);
        int otherAt = otherRow.indexOfChild(other);
        // Out of the row the card loses the focus, which must not stop on another card and scroll there.
        page.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        tileRow.removeView(tile);
        otherRow.removeView(other);
        if (tileRow == otherRow && otherAt < tileAt) {
            tileRow.addView(tile, otherAt);
            tileRow.addView(other, tileAt);
        } else {
            tileRow.addView(other, tileAt);
            otherRow.addView(tile, otherAt);
        }
        page.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
        fitCaption(tile);
        fitCaption(other);
    }

    /** A card can enter or leave the dock, which has no captions. */
    private void fitCaption(View tile) {
        ((LinearLayout) tile).getChildAt(1).setVisibility(showsCaption(tile) ? restingCaption() : View.GONE);
    }

    private int[] spot(String pkg) {
        int[] place = new int[2];
        page.findViewWithTag(pkg).getLocationOnScreen(place);
        return place;
    }

    /** Each swapped card starts where it was and glides to its new place. */
    private void slide(Map<String, int[]> before) {
        page.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                page.getViewTreeObserver().removeOnPreDrawListener(this);
                for (Map.Entry<String, int[]> card : before.entrySet()) {
                    View tile = page.findViewWithTag(card.getKey());
                    // A quick second press finds the card still gliding from the first.
                    tile.animate().cancel();
                    tile.setTranslationX(0);
                    tile.setTranslationY(0);
                    int[] now = spot(card.getKey());
                    tile.setTranslationZ(card.getKey().equals(moving) ? dp(20) : 0);
                    tile.setTranslationX(card.getValue()[0] - now[0]);
                    tile.setTranslationY(card.getValue()[1] - now[1]);
                    tile.animate().translationX(0).translationY(0).setDuration(280)
                        .setInterpolator(new DecelerateInterpolator()).start();
                }
                return true;
            }
        });
    }

    private void stopMoving() {
        String pkg = moving;
        moving = null;
        hint.setVisibility(View.GONE);
        render(pkg);
    }

    @Override protected float focusScale() { return moving != null ? 1.12f : super.focusScale(); }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (moving == null) return super.dispatchKeyEvent(event);
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_LEFT: if (down) move(-1); return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT: if (down) move(1); return true;
            case KeyEvent.KEYCODE_DPAD_UP: if (down) move(-columns); return true;
            case KeyEvent.KEYCODE_DPAD_DOWN: if (down) move(columns); return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (!down) stopMoving();
                return true;
            default: return super.dispatchKeyEvent(event);
        }
    }

    /** Back leaves a move or returns to the first row; the Home itself has nothing behind it. */
    @Override public void onBackPressed() {
        if (moving != null) stopMoving();
        else if (firstCard() != null) firstCard().requestFocus();
    }

    private View firstCard() {
        return page.getChildCount() > 0 ? ((LinearLayout) page.getChildAt(0)).getChildAt(0) : null;
    }
}
