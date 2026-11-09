package it.simplemal.firehome;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.format.DateFormat;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.LruCache;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewPropertyAnimator;
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
import android.widget.ProgressBar;
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
import java.util.function.Consumer;
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
    /** The clean Home's tiles closing before the first row climbs back to the top. */
    private static final int PANEL_QUICK_MS = 90;
    /**
     * The clean Home's coloured line along each app, in dp, half see-through: one pixel came out
     * ragged along the curves, and 0.75 dp at full strength was too thick (2026-11-09).
     */
    private static final float CLEAN_EDGE = 0.75f;
    private static final int CLEAN_EDGE_ALPHA = 128;
    /**
     * On an icon with a dark background the line is fainter, 70% see-through: the user's choice of
     * 2026-11-09 (read the other way round at first, as 70% strong).
     */
    private static final int CLEAN_EDGE_DARK_ALPHA = 77;
    private static final float CLEAN_DARK = 0.3f;
    /** How far towards white that line goes from the app's colour. */
    private static final float CLEAN_EDGE_LIGHT = 0.45f;
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
        // The ring of an app on its way is not the Home to show again.
        if (showing && !this.wheel.portalOpen()) HomeCover.take(getWindow());
    };
    private final Runnable saver = () -> startActivity(new Intent(this, SaverActivity.class));
    private View panelShade;
    private boolean panelOpen;
    /**
     * Clean, first row at the top: where the Home scrolls while the control centre is open, the
     * first row in the middle, clear of its tiles (2026-11-09). -1 elsewhere.
     */
    private int panelRest = -1;
    private long climbAt;
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
    private boolean appsOnly;
    private String weatherSymbol;
    private boolean weatherBig;
    private boolean pillDrawn;
    private boolean pillClock;
    private boolean pillWithDate;
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
    /** The Home as a wheel of circles: ora e meteo in the top corners, the credits at the bottom. */
    private boolean circular;
    /**
     * The clean Home: rows of squircles like the grid's cards, twelve a row and centred, with the time
     * and the weather in the top corners as on the wheel, the buttons at the top in the middle and
     * the credits at the very bottom.
     */
    private boolean clean;
    private int style = -1;
    private ImageView networkButton;
    private static final int CLEAN_COLUMNS = 12;
    private static final int CLEAN_TILE = 56;
    private static final int CLEAN_GAP = 14;
    /** Where the first row stops once a lower one has the focus: under the line at the top. */
    private static final int CLEAN_DOCK_TOP = 100;
    private static final int CLEAN_PILL_TOP = 18;
    private static final int CLEAN_ICON_GAP = 22;
    private Bitmap squirclePlaceholder;
    /**
     * The clean Home's line at the top: time and date on the left, from where a full row's first app
     * starts, today's weather on the right, where its last one ends. Small, in one chosen font.
     */
    private LinearLayout cleanBar;
    private TextClock cleanTime;
    private TextClock cleanDate;
    private TextView cleanWeather;
    private static final int CLEAN_BAR_SP = 17;
    private static final int CLEAN_BAR_GAP = 14;
    private static final int ALL_APPS_FILL = Color.argb(28, 255, 255, 255);
    private static final int ALL_APPS_EDGE = Color.argb(90, 255, 255, 255);
    private WheelHome wheel;
    private LinearLayout weatherCorner;
    private TextView cornerLine;
    private LinearLayout cornerDays;
    private View weatherShade;
    private ImageView allAppsCircle;
    private FrameLayout.LayoutParams heroPlace;
    private Drawable pillGlass;
    private Drawable pillEdge;
    /** The circular Home's free icons are dark over a light corner of the picture. */
    private boolean lightCorner;
    private String cornerRead;
    private final Map<ImageView, Drawable> pillRings = new HashMap<>();
    private static final int FREE_ICON = 24;
    private static final int ALL_APPS_CIRCLE = 60;
    private static final int BESIDE_ALL_APPS = 22;
    private static final float LIGHT_CORNER = 0.72f;
    /**
     * The clean Home's words are small: past this they read better black than white. On the TV's
     * cloudy sky, 0.72, the line stayed white and hard to read (2026-11-09).
     */
    private static final float LIGHT_LINE = 0.5f;
    /**
     * Over a light picture the free buttons and the clean Home's line turn black, a little
     * see-through: the words hardly, the icons a touch more, as the middle ones are drawn thicker.
     */
    private static final int DARK_ICON_ALPHA = 200;
    /** Over a darker picture one shadow for all of them, words and icons alike. */
    private static final int SHADOW_DP = 3;
    private static final int SHADOW_ALPHA = 170;
    private static final int WEATHER_DAY_WIDTH = 50;
    /** One press opens one app: a second one while the first is starting is ignored. */
    private boolean opening;
    private static final long CORNERS_GONE_MS = 110;
    private ImageView blurred;
    /** A third of the screen: the backdrop only seems a step behind the circles, not lost in a haze. */
    private static final int BLUR_WIDTH = 640;
    private static final int BLUR_HEIGHT = 360;
    private static final int BLUR_RADIUS = 2;
    /** The blur comes in softly when it starts to be shown, the user's request of 2026-11-05. */
    private static final long BLUR_FADE_MS = 500;
    /** Shown just now and still empty or old: it fades in with its first picture. */
    private boolean blurFadeIn;
    /** Each blur asked for: a plain one shows only if no other was asked after it. */
    private int blurAsked;
    private int blursComing;
    /** A new backdrop waiting for its blur, to come in with it in one step: no other blur is taken meanwhile, it would show the old one. */
    private int swapsComing;
    private int backdropsComing;
    /** The blur on screen is the one for this backdrop: false from the moment one is wanted until it comes. */
    private boolean blurReady;
    /**
     * Over the Home switched between grid and wheel, until its circles or cards, backdrop and blur are
     * all there: the user's request of 2026-11-07, instead of watching it being built.
     */
    private FrameLayout loadingMask;
    private View loadingMiddle;
    private boolean loading;
    private boolean looked;
    private static final long LOADING_LIMIT_MS = 10000;
    /**
     * A switch between layouts shows its loading at least this long. Back with the Home key the
     * cover shows the Home of before for a moment, and a quick new one was ready under it: the
     * loading never showed (2026-11-09).
     */
    private static final long LOADING_LEAST_MS = 800;
    private long loadingUntil;
    private static final long MENU_BACKDROP_DELAY_MS = 4000;
    private final Runnable menuBackdrop = () -> {
        if (!isDestroyed()) MenuBackdrop.prepare(this);
    };
    private static final long LOADING_OUT_MS = 200;
    private static final int LOADING_WHEEL = 40;
    private final Runnable endLoading = this::hideLoading;
    private static final long WHEEL_MOVE_MS = 180;
    private long lastMove;
    private static final long CORNERS_BACK_MS = 220;
    private static final long PORTAL_GIVE_UP_MS = 2500;
    /** The Home's first frame on screen, under the cover: Amazon's Home must not show through. */
    private static final long CLOSE_LATEST_MS = 450;
    /** The weather's corner on the wheel: a little lower than the top, the date lined up with it. */
    private static final int WEATHER_TOP = 34;
    private static final int CIRCLE_ART_BYTES = (Features.LARGE_PICTURE_MEMORY ? 12 : 6) * 1024 * 1024;
    private static final LruCache<String, Bitmap> CIRCLE_ART = new LruCache<String, Bitmap>(CIRCLE_ART_BYTES) {
        @Override protected int sizeOf(String key, Bitmap art) { return art.getAllocationByteCount(); }
    };
    private static final Map<String, Integer> CIRCLE_GLOW = new HashMap<>();
    private static final int GLOW_SAMPLE = 24;
    private static final int GLOW_HUES = 12;
    /**
     * White, black and grey never count; among the other colours the most present wins, however
     * small: the user's rule of 2026-11-04, after Plex's orange X (1.3% of its circle) glowed white
     * under a 2% floor. What is left is only against a stray pixel or two.
     */
    private static final float GLOW_LEAST = 0.001f;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        // A backdrop that arrives while the Home is on screen shows at once.
        Packs.arrived = this::backdropArrived;
        TaskService.keysByDefault(this);
        TaskService.quickAnimationsByDefault(this);
        TaskService.alarmsAtOnce(this);
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
        // The backdrop blurred, over it and under everything else: only on the wheel, when chosen.
        blurred = new ImageView(this);
        blurred.setScaleType(ImageView.ScaleType.FIT_XY);
        blurred.setVisibility(View.GONE);
        root.addView(blurred, new FrameLayout.LayoutParams(-1, -1));
        // A blur asked for before the first layout had no screen to draw: it is taken now.
        root.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (oldRight - oldLeft == 0 && right - left > 0 && blurred.getVisibility() == View.VISIBLE) blurBackdrop();
        });
        clockShade = cornerShade(0);
        pillShade = cornerShade(1);
        pillShade.setAlpha(0);
        weatherShade = cornerShade(1);
        weatherShade.setVisibility(View.GONE);

        hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        clock = new TextClock(this);
        clock.setFormat24Hour("HH:mm");
        clock.setFormat12Hour("HH:mm");
        clock.setTextColor(Color.WHITE);
        clock.setShadowLayer(dp(6), 0, dp(2), Color.argb(120, 0, 0, 0));
        hero.addView(clock);
        date = new TextClock(this);
        date.setTextColor(Color.WHITE);
        // Thin letters over a detailed picture: only a tight, dark shadow keeps them apart from it.
        date.setShadowLayer(dp(3), 0, dp(1), Color.argb(225, 0, 0, 0));
        styleDate();
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
        heroPlace = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
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
            // On the clean Home the time and the weather stay: the dock stops under them.
            float alpha = clean || shelf <= 0 ? 1f : Math.max(0f, 1f - 2f * y / shelf);
            weatherView.setScroll(shelf > 0 ? Math.min(1f, (float) y / shelf) : 0);
            hero.setAlpha(alpha);
            clockShade.setAlpha(alpha);
            weatherRow.setAlpha(alpha);
            showPillClock();
        });
        page = page();
        scroll.addView(page);
        root.addView(scroll);
        wheel = new WheelHome(this);
        wheel.setVisibility(View.GONE);
        wheel.setListener(new WheelHome.Listener() {
            @Override public void open(String tag) { openFromWheel(tag); }
            @Override public void hold(String tag) { showMenu(tag); }
            @Override public void up() { footer.requestFocus(); }
            @Override public void down() { allAppsCircle.requestFocus(); }
        });
        root.addView(wheel, new FrameLayout.LayoutParams(-1, -1));
        root.addView(allAppsCircle(), allAppsPlace());
        root.addView(weatherCorner(), weatherCornerPlace());
        root.addView(cleanBar(), cleanBarPlace());
        // While the backdrop fades the cards cost one picture, instead of all their shadows and glass.
        weatherView.setOnFade(fading -> scroll.setLayerType(fading ? View.LAYER_TYPE_HARDWARE : View.LAYER_TYPE_NONE, null));

        root.addView(pill(), pillPlace());
        pillGlass = pillBox.getBackground();
        pillEdge = pillBox.getForeground();
        footer = footer();
        panelShade = new View(this);
        panelShade.setVisibility(View.GONE);
        panelShade.setOnClickListener(view -> showPanel(false));
        root.addView(panelShade, new FrameLayout.LayoutParams(-1, -1));
        panel = ControlPanel.addTo(this, root, controlButton, true);
        panel.setVisibility(View.GONE);
        panel.leadDownTo(this::lastAppView);
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

    /** Tutte le app on the circular Home: a glass circle under the wheel, in the middle. */
    private View allAppsCircle() {
        allAppsCircle = new ImageView(this);
        allAppsCircle.setImageResource(R.drawable.ic_apps);
        int inset = dp(17);
        allAppsCircle.setPadding(inset, inset, inset, inset);
        allAppsCircle.setFocusable(true);
        allAppsCircle.setClickable(true);
        allAppsCircle.setDefaultFocusHighlightEnabled(false);
        allAppsCircle.setContentDescription(getString(R.string.main_all_apps));
        allAppsCircle.setVisibility(View.GONE);
        int radius = dp(ALL_APPS_CIRCLE) / 2;
        Drawable rest = new LayerDrawable(new Drawable[]{glass(radius), glassEdge(radius)});
        Drawable lit = selectedFill(radius);
        allAppsCircle.setBackground(rest);
        allAppsCircle.setColorFilter(Color.WHITE);
        allAppsCircle.setOnFocusChangeListener((view, focused) -> {
            allAppsCircle.setBackground(focused ? lit : rest);
            allAppsCircle.setColorFilter(focused ? Fire.INK : Color.WHITE);
            float scale = focused ? 1.12f : 1f;
            allAppsCircle.animate().scaleX(scale).scaleY(scale).setDuration(PILL_MS).start();
        });
        allAppsCircle.setOnClickListener(view -> open(new Intent(this, AppsActivity.class)));
        allAppsCircle.setOnKeyListener((view, code, event) -> {
            if (code != KeyEvent.KEYCODE_DPAD_UP) return false;
            if (event.getAction() == KeyEvent.ACTION_DOWN) wheel.requestFocus();
            return true;
        });
        return allAppsCircle;
    }

    private FrameLayout.LayoutParams allAppsPlace() {
        FrameLayout.LayoutParams place = new FrameLayout.LayoutParams(dp(ALL_APPS_CIRCLE), dp(ALL_APPS_CIRCLE),
            Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        place.bottomMargin = dp(18);
        return place;
    }

    /**
     * On the wheel the pill has no glass and sits at the bottom in the middle: Tutte le app between
     * the control centre and the settings, as small free icons. The clock and weather go higher and wider.
     */
    private void placeCorners() {
        if (allAppsCircle.getParent() != null) ((ViewGroup) allAppsCircle.getParent()).removeView(allAppsCircle);
        if (circular) {
            LinearLayout.LayoutParams middle = new LinearLayout.LayoutParams(dp(ALL_APPS_CIRCLE), dp(ALL_APPS_CIRCLE));
            middle.setMarginEnd(dp(BESIDE_ALL_APPS));
            pillRow.addView(allAppsCircle, pillRow.indexOfChild(pillButton), middle);
        } else {
            root.addView(allAppsCircle, root.indexOfChild(wheel) + 1, allAppsPlace());
        }
        boolean corners = cornersOnTop();
        // The buttons alone, without the pill's glass, on the wheel at the bottom and on the clean Home at the top.
        // The wheel's is a list over its button; the clean Home's the grid's tiles, flat, under its buttons.
        panel.setLook(circular, clean);
        // The date goes above the time, where the eye starts reading: on the glass Home too, as on
        // the wheel (the user's choice of 2026-11-09).
        hero.removeView(date);
        hero.addView(date, 0);
        hero.post(this::alignDate);
        // The big digits carry room above them for accents they never have: under the date it reads as a gap.
        clock.setIncludeFontPadding(false);
        panelShade.setBackground(circular ? new ColorDrawable(Color.argb(200, 0, 0, 0)) : null);
        heroPlace.leftMargin = dp(corners ? 32 : DOCK_MARGIN + DOCK_SIDE);
        heroPlace.topMargin = dp(corners ? 14 : 40);
        hero.setLayoutParams(heroPlace);
        FrameLayout.LayoutParams pillSpot = (FrameLayout.LayoutParams) pillBox.getLayoutParams();
        pillSpot.gravity = circular ? Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL
            : clean ? Gravity.TOP | Gravity.CENTER_HORIZONTAL : Gravity.TOP | Gravity.END;
        pillSpot.topMargin = circular ? 0 : dp(clean ? CLEAN_PILL_TOP : 22);
        pillSpot.rightMargin = corners ? 0 : dp(DOCK_MARGIN);
        pillSpot.bottomMargin = circular ? dp(12) : 0;
        pillBox.setLayoutParams(pillSpot);
        pillBox.setBackground(corners ? null : pillGlass);
        pillBox.setForeground(corners ? null : pillEdge);
        networkButton.setVisibility(clean ? View.VISIBLE : View.GONE);
        if (clean) networkButton.setImageResource(DeviceInfo.wired(this) ? R.drawable.ic_ethernet : R.drawable.ic_wifi);
        int icon = dp(corners ? FREE_ICON : PILL_ICON);
        for (ImageView button : new ImageView[]{controlButton, networkButton, pillButton}) {
            LinearLayout.LayoutParams size = (LinearLayout.LayoutParams) button.getLayoutParams();
            size.width = icon;
            size.height = icon;
            button.setLayoutParams(size);
            int inset = dp(corners ? 2 : 3);
            button.setPadding(inset, inset, inset, inset);
        }
        // Set again as layout params: a margin end changed in place is not applied once the button is
        // on screen, and switching to the wheel left Tutte le app 3 dp from it instead of 22 (2026-11-06).
        LinearLayout.LayoutParams beside = (LinearLayout.LayoutParams) controlButton.getLayoutParams();
        beside.setMarginEnd(dp(circular ? BESIDE_ALL_APPS : clean ? CLEAN_ICON_GAP : PILL_ICON_GAP));
        controlButton.setLayoutParams(beside);
        LinearLayout.LayoutParams network = (LinearLayout.LayoutParams) networkButton.getLayoutParams();
        network.setMarginEnd(dp(CLEAN_ICON_GAP));
        networkButton.setLayoutParams(network);
        FrameLayout.LayoutParams weatherSpot = (FrameLayout.LayoutParams) weatherCorner.getLayoutParams();
        weatherSpot.rightMargin = dp(32);
        weatherSpot.topMargin = dp(WEATHER_TOP);
        weatherCorner.setLayoutParams(weatherSpot);
        footer.setTextSize(circular ? 7 : FOOTER_SP);
        lightCorner = false;
        tonePill();
    }

    /** Time and weather in the two top corners, the buttons free of glass: the wheel and the clean Home. */
    private boolean cornersOnTop() { return circular || clean; }

    /** White with a small shadow over a medium or dark picture, black over a very light one. */
    private void tonePill() {
        boolean dark = cornersOnTop() && lightCorner;
        for (TextView text : new TextView[]{pillTime, pillDate, pillWeather, cleanTime, cleanDate, cleanWeather}) {
            text.setTextColor(dark ? DARK_TEXT : Color.WHITE);
            if (dark || !cornersOnTop()) text.setShadowLayer(0, 0, 0, 0);
            else text.setShadowLayer(dp(SHADOW_DP), 0, dp(1), Color.argb(SHADOW_ALPHA, 0, 0, 0));
        }
        lightPillIcon(controlButton, controlButton.hasFocus());
        if (networkButton != null) lightPillIcon(networkButton, networkButton.hasFocus());
        lightPillIcon(pillButton, pillButton.hasFocus());
    }

    @Override protected void lightPillIcon(ImageView button, boolean lit) {
        if (!pillRings.containsKey(button)) pillRings.put(button, button.getBackground());
        if (!cornersOnTop() || lit) {
            button.setBackground(pillRings.get(button));
            button.setImageAlpha(255);
            super.lightPillIcon(button, lit);
            return;
        }
        button.setBackground(lightCorner ? null : iconShadow(button));
        button.setColorFilter(lightCorner ? Color.BLACK : Color.WHITE);
        button.setImageAlpha(lightCorner ? DARK_ICON_ALPHA : 255);
    }

    /** A soft dark copy of the icon, a little lower, drawn under it. */
    private Drawable iconShadow(ImageView button) {
        int size = dp(FREE_ICON);
        Bitmap shape = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Drawable icon = button.getDrawable().getConstantState().newDrawable().mutate();
        int inset = dp(2);
        icon.setBounds(inset, inset, size - inset, size - inset);
        icon.draw(new Canvas(shape));
        Paint blur = new Paint();
        blur.setMaskFilter(new BlurMaskFilter(dp(SHADOW_DP), BlurMaskFilter.Blur.NORMAL));
        int[] offset = new int[2];
        Bitmap alpha = shape.extractAlpha(blur, offset);
        Bitmap shadow = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Paint dark = new Paint();
        dark.setColor(Color.argb(SHADOW_ALPHA, 0, 0, 0));
        new Canvas(shadow).drawBitmap(alpha, offset[0], offset[1] + dp(1), dark);
        shape.recycle();
        alpha.recycle();
        return new BitmapDrawable(getResources(), shadow);
    }

    /** How light the picture is around the buttons at the bottom, read again only when it changes. */
    private void readCorner() {
        String name = Look.currentName(this);
        if (!cornersOnTop() || Objects.equals(name, cornerRead)) return;
        cornerRead = name;
        Look.current(this, picture -> {
            int width = picture.getWidth();
            int height = picture.getHeight();
            boolean light;
            boolean below = darkInk;
            if (clean) {
                // The whole line at the top; and under the first row, for the names and the credits.
                light = lightness(picture, width / 16, width * 15 / 16, 0, height / 8) > LIGHT_LINE;
                below = lightness(picture, width / 16, width * 15 / 16, height * 7 / 10, height) > LIGHT_LINE;
            } else {
                // The wheel's buttons, at the bottom in the middle.
                light = lightness(picture, width * 3 / 8, width * 5 / 8, height * 5 / 6, height) > LIGHT_CORNER;
            }
            boolean dark = below;
            pendingTone = () -> {
                if (dark != darkInk) {
                    darkInk = dark;
                    toneFooter();
                    render(focusedTag());
                }
                if (light == lightCorner) return;
                lightCorner = light;
                tonePill();
            };
            // With a new backdrop on its way, together with it: never before or after (2026-11-09).
            if (backdropsComing == 0 && swapsComing == 0) placedTone();
        });
    }

    /** The colours read for the backdrop now on show, waiting for it if it was still coming. */
    private Runnable pendingTone;

    private void placedTone() {
        Runnable tone = pendingTone;
        pendingTone = null;
        if (tone != null) tone.run();
    }

    private static double lightness(Bitmap picture, int left, int right, int top, int bottom) {
        double sum = 0;
        int count = 0;
        for (int x = left; x < right; x += 4) {
            for (int y = top; y < bottom; y += 4) {
                int pixel = picture.getPixel(x, y);
                sum += (0.2126 * Color.red(pixel) + 0.7152 * Color.green(pixel) + 0.0722 * Color.blue(pixel)) / 255;
                count++;
            }
        }
        return count > 0 ? sum / count : 0;
    }

    /** The credits at rest: light grey with a shadow, or black on the clean Home's light picture. */
    private void toneFooter() {
        if (footer.hasFocus()) return;
        footer.setTextColor(clean && darkInk ? DARK_TEXT : FOOTER_INK);
        if (clean && darkInk) footer.setShadowLayer(0, 0, 0, 0);
        else footer.setShadowLayer(dp(3), 0, dp(1), Color.argb(200, 0, 0, 0));
    }

    /** The circular Home's weather: the line of now, then today and the next days in columns. */
    private View weatherCorner() {
        weatherCorner = new LinearLayout(this);
        weatherCorner.setOrientation(LinearLayout.VERTICAL);
        weatherCorner.setGravity(Gravity.END);
        weatherCorner.setVisibility(View.GONE);
        // As the date across the screen, which sits on the same line: same size, letters and shadow.
        cornerLine = label("", Look.DATE_SIZES[1]);
        cornerLine.setShadowLayer(dp(3), 0, dp(1), Color.argb(225, 0, 0, 0));
        cornerLine.setSingleLine(true);
        // As wide as its own words: by default it would be as wide as the days under it, and cut.
        weatherCorner.addView(cornerLine, new LinearLayout.LayoutParams(-2, -2));
        cornerDays = new LinearLayout(this);
        LinearLayout.LayoutParams below = new LinearLayout.LayoutParams(-2, -2);
        below.topMargin = dp(8);
        weatherCorner.addView(cornerDays, below);
        // On the wheel the date sits on the line of the weather's first row, whatever their sizes.
        weatherCorner.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> alignDate());
        return weatherCorner;
    }

    private View cleanBar() {
        cleanBar = new LinearLayout(this);
        cleanBar.setGravity(Gravity.CENTER_VERTICAL);
        cleanBar.setVisibility(View.GONE);
        cleanTime = new TextClock(this);
        cleanDate = new TextClock(this);
        cleanWeather = label("", CLEAN_BAR_SP);
        cleanWeather.setSingleLine(true);
        for (TextView text : new TextView[]{cleanTime, cleanDate, cleanWeather}) {
            text.setTextSize(CLEAN_BAR_SP);
            text.setTextColor(Color.WHITE);
            text.setShadowLayer(dp(SHADOW_DP), 0, dp(1), Color.argb(SHADOW_ALPHA, 0, 0, 0));
        }
        cleanBar.addView(cleanTime);
        LinearLayout.LayoutParams after = new LinearLayout.LayoutParams(-2, -2);
        after.setMarginStart(dp(CLEAN_BAR_GAP));
        cleanBar.addView(cleanDate, after);
        cleanBar.addView(new View(this), new LinearLayout.LayoutParams(0, 0, 1));
        cleanBar.addView(cleanWeather);
        return cleanBar;
    }

    /** As wide as a full row of apps, on the line of the buttons in the middle. */
    private FrameLayout.LayoutParams cleanBarPlace() {
        int row = CLEAN_COLUMNS * (dp(CLEAN_TILE) + dp(CLEAN_GAP));
        int side = Math.max(0, (getResources().getDisplayMetrics().widthPixels - row) / 2 + dp(CLEAN_GAP) / 2);
        FrameLayout.LayoutParams place = new FrameLayout.LayoutParams(-1, dp(FREE_ICON), Gravity.TOP);
        place.topMargin = dp(CLEAN_PILL_TOP) + dp(6);
        place.leftMargin = side;
        place.rightMargin = side;
        return place;
    }

    /** What the line shows, in its font: time, date, both or neither, and today's weather. */
    private void styleCleanBar() {
        Typeface font = Typeface.create(Look.FONT_FAMILIES[Look.cleanFont(this)], Typeface.NORMAL);
        int shows = Look.cleanShows(this);
        String time = Look.timePattern(this);
        String day = Look.datePattern(this);
        cleanTime.setFormat24Hour(time);
        cleanTime.setFormat12Hour(time);
        cleanDate.setFormat24Hour(day);
        cleanDate.setFormat12Hour(day);
        cleanTime.setVisibility(shows == Look.SHOW_BOTH || shows == Look.SHOW_TIME ? View.VISIBLE : View.GONE);
        cleanDate.setVisibility(shows == Look.SHOW_BOTH || shows == Look.SHOW_DATE ? View.VISIBLE : View.GONE);
        // Alone, the date starts where the time would.
        ((LinearLayout.LayoutParams) cleanDate.getLayoutParams()).setMarginStart(shows == Look.SHOW_BOTH ? dp(CLEAN_BAR_GAP) : 0);
        for (TextView text : new TextView[]{cleanTime, cleanDate, cleanWeather}) text.setTypeface(font);
        cleanBar.requestLayout();
    }

    /** Only today, as it is now: the days after it are for the other Homes. */
    private void showCleanWeather() {
        String line = Weather.enabled(this) ? Weather.line(this) : null;
        cleanWeather.setVisibility(line != null ? View.VISIBLE : View.GONE);
        if (line != null && !line.contentEquals(cleanWeather.getText())) cleanWeather.setText(WeatherIcon.in(this, line));
    }

    private void alignDate() {
        if (!cornersOnTop() || hero == null || date.getParent() != hero || cornerLine.getVisibility() != View.VISIBLE) {
            if (hero != null) hero.setTranslationY(0);
            return;
        }
        int weatherLine = weatherCorner.getTop() + cornerLine.getTop() + cornerLine.getBaseline();
        int dateLine = hero.getTop() + date.getTop() + date.getBaseline();
        hero.setTranslationY(weatherLine - dateLine);
    }

    private FrameLayout.LayoutParams weatherCornerPlace() {
        FrameLayout.LayoutParams place = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        place.topMargin = dp(78);
        place.rightMargin = dp(DOCK_MARGIN + DOCK_SIDE);
        return place;
    }

    /** Letters, size and format of the date, and of the weather's first line, as chosen in Settings. */
    private void styleDate() {
        String pattern = Look.datePattern(this);
        date.setFormat24Hour(pattern);
        date.setFormat12Hour(pattern);
        date.setTypeface(Typeface.create(Look.FONT_FAMILIES[Look.dateFont(this)], Typeface.NORMAL));
        date.setTextSize(Look.DATE_SIZES[Look.dateSize(this)]);
        if (cornerLine == null) return;
        cornerLine.setTypeface(Typeface.create(Look.FONT_FAMILIES[Look.weatherFontShown(this)], Typeface.NORMAL));
        cornerLine.setTextSize(Look.DATE_SIZES[Look.weatherSizeShown(this)]);
    }

    private void showWeatherCorner() {
        boolean shown = Weather.enabled(this);
        String line = shown ? Weather.line(this) : null;
        cornerLine.setVisibility(line != null ? View.VISIBLE : View.GONE);
        if (line != null && !line.contentEquals(cornerLine.getText())) cornerLine.setText(WeatherIcon.in(this, line));
        cornerDays.removeAllViews();
        for (Weather.Day day : shown ? Weather.days(this) : new ArrayList<Weather.Day>()) {
            LinearLayout column = new LinearLayout(this);
            column.setOrientation(LinearLayout.VERTICAL);
            column.setGravity(Gravity.CENTER_HORIZONTAL);
            TextView symbol = label("", 20);
            symbol.setText(WeatherIcon.in(this, day.symbol));
            for (TextView part : new TextView[]{label(day.name, 13), symbol,
                    label(day.highest + "°", 15), label(day.lowest + "°", 13)}) {
                part.setGravity(Gravity.CENTER);
                part.setShadowLayer(dp(4), 0, dp(1), Color.argb(190, 0, 0, 0));
                column.addView(part);
            }
            ((TextView) column.getChildAt(2)).setTypeface(Typeface.DEFAULT_BOLD);
            column.getChildAt(3).setAlpha(0.75f);
            cornerDays.addView(column, new LinearLayout.LayoutParams(dp(WEATHER_DAY_WIDTH), -2));
        }
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
            else panel.opening().requestFocus();
        });
        controlButton.setOnKeyListener((view, code, event) -> {
            if (outOfRow(code, false)) return backToWheel(code, event);
            int toPanel = circular ? KeyEvent.KEYCODE_DPAD_UP : KeyEvent.KEYCODE_DPAD_DOWN;
            if (code != toPanel) return false;
            // On the wheel Up goes back to the apps: the panel opens with the button itself.
            if (event.getAction() == KeyEvent.ACTION_DOWN) (circular ? wheel : panel.opening()).requestFocus();
            return true;
        });
        LinearLayout.LayoutParams beside = new LinearLayout.LayoutParams(dp(PILL_ICON), dp(PILL_ICON));
        beside.setMarginEnd(dp(PILL_ICON_GAP));
        // Only on the clean Home: Rete e Bluetooth, first, before the control centre.
        networkButton = pillIcon(R.drawable.ic_wifi, getString(R.string.settings_cat_network));
        networkButton.setVisibility(View.GONE);
        networkButton.setOnClickListener(v -> open(new Intent(this, SettingsActivity.class)
            .putExtra(SettingsActivity.SECTION, SettingsActivity.NETWORK)));
        networkButton.setOnKeyListener((view, code, event) -> downToApps(code, event));
        pillRow.addView(networkButton, new LinearLayout.LayoutParams(dp(PILL_ICON), dp(PILL_ICON)));
        pillRow.addView(controlButton, beside);
        pillRow.addView(settings, new LinearLayout.LayoutParams(dp(PILL_ICON), dp(PILL_ICON)));
        pillButton = settings;
        // The wheel covers the whole screen, so the arrows would not find it from the buttons by themselves.
        settings.setOnKeyListener((view, code, event) -> {
            if (outOfRow(code, true)) return backToWheel(code, event);
            if (clean) return downToApps(code, event);
            if (!circular || code != KeyEvent.KEYCODE_DPAD_UP) return false;
            if (event.getAction() == KeyEvent.ACTION_DOWN) wheel.requestFocus();
            return true;
        });
        pillBox = pillBox(pillRow);
        return pillBox;
    }

    /**
     * On the wheel, the arrow that leads out of the row of buttons at its end: past the settings, or
     * before the control centre. There is nothing more that way on the screen.
     */
    private boolean outOfRow(int code, boolean end) {
        if (!circular) return false;
        boolean rtl = pillRow.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        int toEnd = rtl ? KeyEvent.KEYCODE_DPAD_LEFT : KeyEvent.KEYCODE_DPAD_RIGHT;
        int toStart = rtl ? KeyEvent.KEYCODE_DPAD_RIGHT : KeyEvent.KEYCODE_DPAD_LEFT;
        return code == (end ? toEnd : toStart);
    }

    /** The clean Home's buttons are at the top: Down goes to the app selected last, however far below it is. */
    private boolean downToApps(int code, KeyEvent event) {
        if (!clean || code != KeyEvent.KEYCODE_DPAD_DOWN) return false;
        View first = lastAppView();
        if (first != null && event.getAction() == KeyEvent.ACTION_DOWN) first.requestFocus();
        return true;
    }

    /** Back on the wheel, already on the next app that way. */
    private boolean backToWheel(int code, KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            wheel.requestFocus();
            wheel.turnToward(code);
        }
        return true;
    }

    /** Open while the focus is on its button or inside it. Touching clears the focus: that says nothing. */
    private void followFocus(View focus) {
        // On the wheel the panel covers the middle of the screen: it opens only when the button is pressed.
        if (focus != null) showPanel(focus == controlButton && !panel.listed() || isInside(focus, panel));
    }

    private boolean isInside(View view, View parent) {
        for (View step = view; step != null; step = step.getParent() instanceof View ? (View) step.getParent() : null) {
            if (step == parent) return true;
        }
        return false;
    }

    /** The panel follows the focus: it goes to where the Home rests, the wheel or the app selected last. */
    @Override public void leave() {
        if (!panelOpen) return;
        // The app selected last, as everywhere: the first one showed for a moment before the screensaver.
        View rest = lastAppView();
        if (rest != null) rest.requestFocus();
        showPanel(false);
    }

    private void showPanel(boolean open) {
        if (open == panelOpen) return;
        panelOpen = open;
        // Clean, first row at the top: it steps down to the middle under the tiles, and back up after.
        // Closing, the tiles go quickly first and the row climbs only then: together they met on the
        // way (2026-11-09).
        boolean quick = !open && panelRest >= 0;
        long closing = quick ? PANEL_QUICK_MS : PILL_MS;
        if (panelRest >= 0 && open) scroll.smoothScrollTo(0, panelRest);
        if (quick) {
            climbAt = SystemClock.uptimeMillis() + PANEL_QUICK_MS;
            root.postDelayed(() -> { if (!panelOpen) scroll.smoothScrollTo(0, shelf); }, PANEL_QUICK_MS);
        }
        panelShade.animate().cancel();
        panel.hideHint();
        if (!open) panel.closed();
        panel.animate().cancel();
        boolean list = panel.listed();
        // The list appears and goes at once: an animation over the whole picture could stutter on the Stick.
        if (list) {
            panelShade.setAlpha(1f);
            panelShade.setVisibility(open ? View.VISIBLE : View.GONE);
            if (open) {
                panel.refresh();
                panel.placeOver(controlButton);
            }
            panel.setAlpha(1f);
            panel.setTranslationY(0);
            panel.setScaleY(1f);
            panel.setVisibility(open ? View.VISIBLE : View.GONE);
            return;
        }
        if (open) {
            panelShade.setVisibility(View.VISIBLE);
            panelShade.setAlpha(0f);
            panelShade.animate().alpha(1f).setDuration(PILL_MS).start();
        } else {
            panelShade.animate().alpha(0f).setDuration(closing).withEndAction(() -> panelShade.setVisibility(View.GONE)).start();
        }
        // The tiles drop in from the pill.
        if (open) {
            panel.refresh();
            panel.setVisibility(View.VISIBLE);
            panel.setAlpha(0f);
            panel.setTranslationY(-dp(12));
            panel.animate().alpha(1f).translationY(0).setDuration(PILL_MS).start();
        } else {
            panel.animate().alpha(0f).translationY(-dp(12)).setDuration(closing)
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
            // No glass on the clean Home: the same flat veil as Tutte le app.
            view.setBackground(!focused ? null : clean ? plainFill(radius)
                : new LayerDrawable(new Drawable[]{glass(radius), glassEdge(radius)}));
            view.setTextColor(focused ? Color.WHITE : clean && darkInk ? DARK_TEXT : FOOTER_INK);
            // Only if they are not on the screen already: with the first row at the top they are, and
            // reaching them must not move the Home (the user's rule of 2026-11-09).
            if (focused && !circular && !onScreen(view)) scroll.smoothScrollTo(0, page.getHeight() - scroll.getHeight());
        });
        view.setOnClickListener(clicked -> open(new Intent(this, AboutActivity.class)));
        view.setOnKeyListener((changed, code, event) -> {
            // Up from the credits: back on the app that was selected.
            if (!circular && code == KeyEvent.KEYCODE_DPAD_UP) {
                View back = lastAppView();
                if (back == null) return false;
                if (event.getAction() == KeyEvent.ACTION_DOWN) back.requestFocus();
                return true;
            }
            if (!circular || code != KeyEvent.KEYCODE_DPAD_DOWN) return false;
            if (event.getAction() == KeyEvent.ACTION_DOWN) wheel.requestFocus();
            return true;
        });
        return view;
    }

    /** Wholly inside what the scroll shows now. */
    private boolean onScreen(View view) {
        int top = scroll.getScrollY();
        return view.getParent() == page && view.getTop() >= top && view.getBottom() <= top + scroll.getHeight();
    }

    private Drawable plainFill(int radius) {
        GradientDrawable fill = new GradientDrawable();
        fill.setColor(ALL_APPS_FILL);
        fill.setCornerRadius(radius);
        return fill;
    }

    private void showPillClock() {
        // On the clean Home the big clock is never covered.
        boolean raised = !clean && scroll.getScrollY() > shelf / 2;
        // The clean Home's time is in its line at the top, or not wanted: never in the buttons.
        boolean clockInView = clean || bigClock && !raised;
        String symbol = raised || !weatherBig ? weatherSymbol : null;
        // Time and date not big stay small in the pill (2026-11-09: the switch is called Large), and
        // the date on the left is not repeated there: only once it is out of view. Not on the clean
        // Home, which has its own line at the top.
        boolean withDate = !clean && (raised || date.getVisibility() != View.VISIBLE);
        boolean small = !clockInView || withDate || symbol != null;
        // Called at every step of a scroll, and almost every step changes nothing.
        if (pillDrawn && clockInView == pillClock && withDate == pillWithDate && Objects.equals(symbol, pillSymbol)) return;
        pillDrawn = true;
        pillClock = clockInView;
        pillWithDate = withDate;
        pillSymbol = symbol;
        animatePill();
        int time = clockInView ? View.GONE : View.VISIBLE;
        clockShade.setVisibility(bigClock && !clean ? View.VISIBLE : View.GONE);
        pillShade.animate().alpha(small ? 1 : 0).setDuration(PILL_MS).start();
        pillDate.setVisibility(withDate ? View.VISIBLE : View.GONE);
        pillTime.setVisibility(time);
        pillWeather.setVisibility(symbol != null ? View.VISIBLE : View.GONE);
        if (symbol != null) pillWeather.setText(WeatherIcon.in(this, symbol));
        pillRow.setPadding(small ? dp(16) : dp(6), dp(6), dp(6), dp(6));
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
        if (!intent.getBooleanExtra(OPEN_PANEL, false)) {
            if (appsOnly) restRaised();
            return;
        }
        View from = getCurrentFocus();
        boolean onCards = from != null && from != controlButton && !isInside(from, panel);
        // Dock up, the Home stays where it is and the panel opens over it: no slide down and back.
        if (!appsOnly) scroll.smoothScrollTo(0, 0);
        showPanel(true);
        panel.openEmpty(onCards ? from : lastAppView());
    }

    /** The cover needs the Home as it is now, and a press leaves no time to take it. */
    @Override public void onUserInteraction() {
        // On its way to the first start this page has built nothing.
        if (root == null) return;
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
        opening = false;
        // The update's cover waits underneath until Salotto is back: it can go now.
        Cover.close(this);
        // The collections chosen in the installer and not yet on the TV, quietly.
        Packs.fetchChosen(this);
        // The colour closes from the Home's first frame. The Home key's cover shows the same colour, so it
        // goes at once instead of after its usual wait: waiting for both left the colour still for seconds.
        // Simple and quick, the user's choice: the screen is the app's colour at once (the Home key's
        // cover shows it too), and the colour closes into the circle as soon as Fire OS's own
        // animation has ended, which would hide it; at the latest after a moment.
        if (wheel.portalOpen()) {
            if (TaskService.closeOverAll()) {
                // The Home key or Back: the cover closes the circle over everything.
                wheel.dropPortal();
                // Back at once, under the cover: outside the ring the Home is already whole.
                cornersNow();
                TaskService.afterHomeCover(HomeCover::disarm);
            } else {
                // Back: the app has gone by itself; the same closing, drawn here.
                wheel.portalToCircle();
                root.removeCallbacks(closeNow);
                root.postDelayed(closeNow, CLOSE_LATEST_MS);
            }
        }
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
        // A Home loaded again, under its mask, starts from the first app, in the grid and on the wheel:
        // the user's request of 2026-11-07.
        if (!state(apps).equals(shown)) render(loading && !apps.isEmpty() ? apps.get(0) : focusedTag(), apps);
        // The wheel keeps a button's focus otherwise, the control centre's one after a switch from the grid.
        if (loading && circular) wheel.requestFocus();
        if (appsOnly) restRaised();
        Look.backHome(this);
        waitForSaver();
        ticker.post(weatherCheck);
        weatherView.resume();
        root.postDelayed(offerStart, OFFER_START_MS);
        backToApp();
        homeMaybeReady();
    }

    /**
     * Back on the Home from the settings or the control centre: on the app that was selected, not
     * on the button, in every layout (the user's request of 2026-11-09). On the grid that also
     * takes the dock where it now belongs, if it has just been moved.
     */
    private void backToApp() {
        View focus = getCurrentFocus();
        if (focus != controlButton && focus != pillButton && focus != networkButton) return;
        if (circular) {
            wheel.requestFocus();
            return;
        }
        View tile = lastAppView();
        if (tile != null) tile.requestFocus();
    }

    /**
     * Where the apps are entered again from the buttons, the control centre or the credits: the app
     * selected last, not the first or the one in the middle (the user's request of 2026-11-09).
     */
    private View lastAppView() {
        if (circular) return wheel;
        View tile = lastApp != null ? page.findViewWithTag(lastApp) : null;
        return tile != null ? tile : firstCard();
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

    /**
     * Runs every minute: the same backdrop is not fetched and drawn again. `chosen`: the look has just
     * changed in Settings. Then, and always under the wheel's blur or on the Sticks of 2020, a new
     * backdrop is made ready away from the screen, blurred too when the blur is on, and replaces the
     * old one in one step: the user's request of 2026-11-07, a fade over the blur used to stall.
     */
    private void showBackdrop(boolean chosen) {
        if (cornersOnTop()) readCorner();
        String scene = Look.weatherPictures(this) ? Weather.picture(this)
            : Look.weatherBackdrop(this) || Weather.testing(this) ? Weather.scene(this) : null;
        // Salotto's colours changing by themselves: the one due now, so that a new one is a change.
        String plain = scene == null ? Look.shownBackdrop(this) : null;
        String before = plainBackdrop;
        plainBackdrop = plain;
        boolean oneStep = chosen || blurred.getVisibility() == View.VISIBLE || !Features.BACKDROP_FADE;
        if (plain != null && !plain.equals(before)) {
            backdropsComing++;
            Look.show(this, picture -> {
                Runnable place = () -> {
                    if (!plain.equals(plainBackdrop)) return;
                    root.setBackground(picture);
                    weatherView.show(null);
                };
                if (plain.equals(plainBackdrop) && blurred.getVisibility() == View.VISIBLE) {
                    blurAndPlace(canvas -> {
                        picture.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
                        picture.draw(canvas);
                    }, place);
                    return;
                }
                place.run();
                backdropsComing--;
                placedTone();
                homeMaybeReady();
            });
        } else if (scene == null) {
            weatherView.show(null);
        }
        if (scene == null) return;
        if (!oneStep) {
            weatherView.show(scene);
            return;
        }
        backdropsComing++;
        if (!weatherView.show(scene, this::swapReady)) backdropsComing--;
    }

    /** A scene loaded for a change in one step: blurred first when the blur is on. */
    private void swapReady(Runnable place, Consumer<Canvas> paint) {
        if (paint != null && blurred.getVisibility() == View.VISIBLE) {
            blurAndPlace(paint, place);
            return;
        }
        place.run();
        backdropsComing--;
        placedTone();
        homeMaybeReady();
    }

    private void blurAndPlace(Consumer<Canvas> paint, Runnable place) {
        swapsComing++;
        blurReady = false;
        blurOf(paint, true, soft -> {
            swapsComing--;
            backdropsComing--;
            place.run();
            placedTone();
            if (soft != null) showBlur(soft);
            else blurBackdrop();
            homeMaybeReady();
        });
    }

    /** What is behind the wheel now, blurred: after the blur is turned on, or the screen got its size. */
    private void blurBackdrop() {
        if (blurred.getVisibility() != View.VISIBLE || swapsComing > 0) return;
        blurOf(canvas -> {
            Drawable behind = root.getBackground();
            if (behind != null) behind.draw(canvas);
            weatherView.paintSettled(canvas);
        }, false, soft -> {
            if (soft != null) showBlur(soft);
            homeMaybeReady();
        });
    }

    /**
     * Drawn small and blurred by Blur, the same as behind the task manager: Android 11 cannot blur a
     * view. A moving weather scene is drawn as it rests. `done` gets null with no screen size yet, or
     * for a plain blur that a later one has overtaken.
     */
    private void blurOf(Consumer<Canvas> paint, boolean always, Consumer<Bitmap> done) {
        if (root.getWidth() == 0) {
            done.accept(null);
            return;
        }
        int asked = ++blurAsked;
        // At the screen's own size: a picture scene fills whatever canvas it is given, scaled or not.
        Bitmap whole = Bitmap.createBitmap(root.getWidth(), root.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(whole);
        canvas.drawColor(Color.BLACK);
        paint.accept(canvas);
        Bitmap small = Bitmap.createScaledBitmap(whole, BLUR_WIDTH, BLUR_HEIGHT, true);
        whole.recycle();
        blursComing++;
        Background.draw(() -> {
            Bitmap soft = Blur.light(small, BLUR_WIDTH, BLUR_HEIGHT, BLUR_RADIUS);
            small.recycle();
            runOnUiThread(() -> {
                blursComing--;
                if (always || asked == blurAsked) {
                    done.accept(soft);
                } else {
                    soft.recycle();
                    done.accept(null);
                }
            });
        });
    }

    private void showBlur(Bitmap soft) {
        blurred.setImageBitmap(soft);
        blurReady = true;
        if (!blurFadeIn) {
            coverScene();
            return;
        }
        blurFadeIn = false;
        blurred.animate().alpha(1f).setDuration(BLUR_FADE_MS).withEndAction(this::coverScene).start();
    }

    /** Wholly behind the blur, the scene stops drawing: on the wheel it would move unseen. */
    private void coverScene() {
        weatherView.setCovered(blurred.getVisibility() == View.VISIBLE && blurred.getAlpha() == 1f && blurred.getDrawable() != null);
    }

    /**
     * The mask over a Home being built. With its wheel and words only for a switch between grid and
     * wheel: when the Home starts again by itself, after Fire OS closed it behind an app, it is plain,
     * so that coming back from an app never shows a loading.
     */
    private void showLoading(boolean switching) {
        if (loadingMask == null) {
            loadingMask = new FrameLayout(this);
            loadingMask.setBackground(Look.plainBackdrop());
            LinearLayout middle = new LinearLayout(this);
            middle.setOrientation(LinearLayout.VERTICAL);
            middle.setGravity(Gravity.CENTER_HORIZONTAL);
            ProgressBar spinner = new ProgressBar(this);
            spinner.setIndeterminateTintList(ColorStateList.valueOf(Color.WHITE));
            middle.addView(spinner, new LinearLayout.LayoutParams(dp(LOADING_WHEEL), dp(LOADING_WHEEL)));
            TextView words = new TextView(this);
            words.setText(R.string.main_loading_home);
            words.setTextColor(Color.argb(200, 255, 255, 255));
            words.setTextSize(16);
            LinearLayout.LayoutParams under = new LinearLayout.LayoutParams(-2, -2);
            under.topMargin = dp(16);
            middle.addView(words, under);
            loadingMiddle = middle;
            loadingMask.addView(middle, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
            root.addView(loadingMask, new FrameLayout.LayoutParams(-1, -1));
        }
        loadingMiddle.setVisibility(switching ? View.VISIBLE : View.GONE);
        loadingUntil = switching ? SystemClock.uptimeMillis() + LOADING_LEAST_MS : 0;
        loadingMask.animate().cancel();
        loadingMask.setAlpha(1f);
        loadingMask.setVisibility(View.VISIBLE);
        loadingMask.bringToFront();
        loading = true;
        root.removeCallbacks(endLoading);
        root.postDelayed(endLoading, LOADING_LIMIT_MS);
    }

    /** Everything asked for has arrived: one frame later, so that it is drawn, the mask goes. */
    private void homeMaybeReady() {
        if (homeReady()) {
            // The settings' backdrop is made now, while nothing else is going on, and not when they open.
            root.removeCallbacks(menuBackdrop);
            root.postDelayed(menuBackdrop, MENU_BACKDROP_DELAY_MS);
        }
        if (!loading || !homeReady()) return;
        root.postOnAnimation(() -> {
            if (homeReady()) hideLoading();
        });
    }

    /** A blur still missing counts too: before the first layout there is no screen to take it from. */
    private boolean homeReady() {
        return artComing == 0 && backdropsComing == 0 && blursComing == 0 && swapsComing == 0
            && (blurred.getVisibility() != View.VISIBLE || blurReady);
    }

    private void hideLoading() {
        if (!loading) return;
        long wait = loadingUntil - SystemClock.uptimeMillis();
        if (wait > 0) {
            root.removeCallbacks(endLoading);
            root.postDelayed(endLoading, wait);
            return;
        }
        loading = false;
        root.removeCallbacks(endLoading);
        loadingMask.animate().alpha(0f).setDuration(LOADING_OUT_MS).withEndAction(() -> loadingMask.setVisibility(View.GONE)).start();
    }

    @Override protected void artShown() { homeMaybeReady(); }

    /** Runs while the Home is in front: a TV can stay on it for hours. */
    private void checkWeather() {
        noteTime();
        root.postDelayed(freshPicture, FRESH_PICTURE_MS);
        showWeather();
        showBackdrop(false);
        if (Weather.due(this)) {
            Background.run(() -> {
                try {
                    Weather.refresh(getApplicationContext());
                    runOnUiThread(() -> {
                        showWeather();
                        showBackdrop(false);
                    });
                } catch (IOException | JSONException e) {
                    // The last reading stays on screen; the next check tries again.
                }
            });
        }
        offerUpdate();
        ticker.postDelayed(weatherCheck, WEATHER_CHECK_MS);
    }

    /** The weather written on the Home only if it is wanted there: read it is all the same, for the backdrops. */
    private void showWeather() {
        boolean shown = Weather.enabled(this);
        String line = shown ? Weather.line(this) : null;
        weather.setVisibility(line != null ? View.VISIBLE : View.GONE);
        if (line != null && !line.contentEquals(weather.getText())) weather.setText(WeatherIcon.in(this, line));
        String tomorrow = shown ? Weather.tomorrow(this) : null;
        forecast.setVisibility(tomorrow != null ? View.VISIBLE : View.GONE);
        if (tomorrow != null && !tomorrow.contentEquals(forecast.getText())) forecast.setText(WeatherIcon.in(this, tomorrow));
        // Not large, today's symbol alone stays next to the buttons; the clean Home has its own line.
        weatherSymbol = shown || !clean ? Weather.symbol(this) : null;
        weatherBig = shown;
        if (cornersOnTop()) showWeatherCorner();
        if (clean) showCleanWeather();
        showPillClock();
    }

    /** Out of sight: a control centre left open for the screensaver or the task manager closes now, unseen. */
    @Override protected void onStop() {
        super.onStop();
        leave();
    }

    @Override protected void onPause() {
        super.onPause();
        showing = false;
        root.removeCallbacks(offerStart);
        if (!wheel.portalOpen()) HomeCover.take(getWindow());
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
        // Read before the backdrop, which blurs itself only when the wheel is shown and the blur chosen.
        int wasStyle = style;
        style = Look.homeStyle(this);
        circular = style == Look.HOME_CIRCLE;
        clean = style == Look.HOME_CLEAN;
        // The blur at the bottom of the picture is for a first row resting there.
        boolean bottomBlur = !(clean && (Look.appsOnly(this) || Look.cleanRowMiddle(this)));
        if (bottomBlur != BottomBlur.on) {
            BottomBlur.on = bottomBlur;
            Look.trim();
            weatherView.forget();
            plainBackdrop = null;
        }
        wheel.setGlow(Look.glow(this));
        // Gone, not hidden: neither the arrows nor the grid's spacing find it.
        footer.setVisibility(Look.credits(this) ? View.VISIBLE : View.GONE);
        // Also when the Home starts, plain: until then only the window's grey would show, the user's
        // report of 2026-11-07. The loading itself only for a switch, the user's request of 2026-11-09.
        if (!looked || style != wasStyle) showLoading(looked);
        looked = true;
        // Each its own: the clean Home's blur starts off and does not follow the wheel's.
        boolean blur = circular && Look.wheelBlur(this) || clean && Look.cleanBlur(this);
        boolean blurOn = blur && blurred.getVisibility() != View.VISIBLE;
        if (blurOn) blurReady = false;
        if (blurOn) {
            // Turning the blur on: hidden until its picture is ready, then faded in. Under the mask of
            // a switch to the wheel it is simply there when the mask goes.
            blurred.animate().cancel();
            blurred.setAlpha(loading ? 1f : 0f);
            blurFadeIn = !loading;
        }
        blurred.setVisibility(blur ? View.VISIBLE : View.GONE);
        coverScene();
        showBackdrop(true);
        if (blurOn) blurBackdrop();
        if (dock != null) dock.setBackground(clean ? null : dockGlass(dp(26), Look.weatherBackdrop(this)));
        columns = clean ? CLEAN_COLUMNS : Look.columns(this);
        clock.setTypeface(Typeface.create(Look.FONT_FAMILIES[Look.font(this)], Typeface.NORMAL));
        clock.setTextSize(Look.SIZES[Look.size(this)]);
        alignInk(clock);
        bigClock = Look.clock(this);
        clock.setVisibility(bigClock ? View.VISIBLE : View.GONE);
        appsOnly = Look.appsOnly(this) && !circular;
        scroll.setVisibility(circular ? View.GONE : View.VISIBLE);
        wheel.setVisibility(circular ? View.VISIBLE : View.GONE);
        weatherRow.setVisibility(cornersOnTop() ? View.GONE : View.VISIBLE);
        // The clean Home has its own small line at the top instead of the big clock and the weather's corner.
        weatherCorner.setVisibility(circular ? View.VISIBLE : View.GONE);
        weatherShade.setVisibility(circular ? View.VISIBLE : View.GONE);
        hero.setVisibility(clean ? View.GONE : View.VISIBLE);
        cleanBar.setVisibility(clean ? View.VISIBLE : View.GONE);
        if (clean) {
            styleCleanBar();
            showCleanWeather();
        }
        if (circular) scroll.scrollTo(0, 0);
        if (cornersOnTop()) {
            hero.setAlpha(1);
            clockShade.setAlpha(1);
            showWeatherCorner();
        }
        allAppsCircle.setVisibility(circular ? View.VISIBLE : View.GONE);
        if (style != wasStyle) {
            placeFooter();
            placeCorners();
            cornerRead = null;
            // Dark names belong to the clean Home only: read again for it, white elsewhere.
            if (!clean && darkInk) {
                darkInk = false;
                toneFooter();
            }
        }
        readCorner();
        if (appsOnly) restRaised();
        else if (dock != null && isInside(getCurrentFocus(), dock)) scroll.scrollTo(0, 0);
        date.setVisibility(Look.date(this) ? View.VISIBLE : View.GONE);
        styleDate();
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
        focus = neighbourOf(focus, apps);
        if (circular) {
            renderWheel(focus, apps);
            return;
        }
        page.removeAllViews();
        startCards();
        shownApps = apps;
        shown = state(apps);
        int inset = dp(DOCK_SIDE);
        int width = clean ? dp(CLEAN_TILE) : cardWidth(columns, dp(DOCK_MARGIN) + inset);
        int imageHeight = clean ? width : width * 9 / 16;
        rowHeight = imageHeight + dp(CAPTION_HEIGHT);

        // The first row rests low: below it only a third of the next row shows.
        int screen = getResources().getDisplayMetrics().heightPixels;
        // The clean Home's first row is a row like the others, names included: no dock around it.
        int dockPadding = clean ? 0 : dp(DOCK_HEIGHT_PADDING);
        int dockHeight = clean ? rowHeight : imageHeight + 2 * dockPadding;
        int rowGap = dp(20);
        // The clean Home's first row can also rest in the middle: of the room under the line at the top.
        int lineBottom = dp(CLEAN_PILL_TOP) + dp(6) + dp(FREE_ICON);
        int middle = lineBottom + (screen - lineBottom - dockHeight) / 2;
        int top = clean && Look.cleanRowMiddle(this) && !appsOnly ? middle
            : screen - dockHeight - rowGap - imageHeight / 3;
        panelRest = clean && appsOnly ? top - middle : -1;
        shelf = top - dp(clean ? CLEAN_DOCK_TOP : DOCK_TOP);
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
                // Fewer than twelve in a row sit in the middle, not at the start.
                if (clean) row.setGravity(Gravity.CENTER_HORIZONTAL);
                if (index == 0) {
                    dock = row;
                    // The clean Home's dock is only its row of apps: no glass under it.
                    dock.setBackground(clean ? null : dockGlass(dp(26), Look.weatherBackdrop(this)));
                } else if (index == columns) {
                    ((LinearLayout.LayoutParams) row.getLayoutParams()).topMargin = rowGap;
                }
            }
            View tile = clean ? squircleCard(row, index == apps.size() ? ALL_APPS : apps.get(index), width)
                : index == apps.size() ? allAppsCard(row, width) : homeCard(row, apps.get(index), width);
            if (clean) {
                // The same room on both sides: the row's middle is the screen's.
                LinearLayout.LayoutParams around = (LinearLayout.LayoutParams) tile.getLayoutParams();
                around.leftMargin = dp(CLEAN_GAP) / 2;
                around.rightMargin = dp(CLEAN_GAP) / 2;
            }
            if (index < columns && !clean) ((LinearLayout) tile).getChildAt(1).setVisibility(View.GONE);
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
        if (footer.getParent() != null) ((ViewGroup) footer.getParent()).removeView(footer);
        page.addView(footer, foot);
        (target != null ? target : first).requestFocus();
        if (moving != null && target != null) startJiggle(target);
    }

    /**
     * An app taken off the Home, or uninstalled, leaves the selection to the one before it, or with
     * none before to the one after: the user's request of 2026-11-09, instead of the first app.
     */
    private String neighbourOf(String focus, List<String> apps) {
        if (focus == null || apps.contains(focus) || !shownApps.contains(focus)) return focus;
        int gone = shownApps.indexOf(focus);
        for (int index = gone - 1; index >= 0; index--) {
            if (apps.contains(shownApps.get(index))) return shownApps.get(index);
        }
        for (int index = gone + 1; index < shownApps.size(); index++) {
            if (apps.contains(shownApps.get(index))) return shownApps.get(index);
        }
        return null;
    }

    /** The grid keeps the credits after its last row; the wheel has them small, at the top edge, in the middle. */
    private void placeFooter() {
        if (footer.getParent() != null) ((ViewGroup) footer.getParent()).removeView(footer);
        if (!circular) return;
        FrameLayout.LayoutParams place = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        place.topMargin = dp(4);
        root.addView(footer, root.indexOfChild(pillBox), place);
    }

    /** The same apps as the grid, in the same order: Tutte le app has its own circle underneath. */
    private void renderWheel(String focus, List<String> apps) {
        page.removeAllViews();
        shownApps = apps;
        shown = state(apps);
        placeFooter();
        int size = dp(Look.wheel(this) == 5 ? 200 : 230);
        List<ImageView> circles = new ArrayList<>();
        List<String> tags = new ArrayList<>(apps);
        for (String tag : tags) {
            ImageView circle = new ImageView(this);
            circle.setTag(tag);
            circle.setContentDescription(circleName(tag));
            String key = circleKey(tag, size);
            Bitmap known = CIRCLE_ART.get(key);
            if (known != null) {
                circle.setImageBitmap(known);
            } else {
                circle.setBackgroundColor(Color.rgb(60, 64, 72));
                artComing++;
                Background.draw(() -> {
                    Bitmap made = circleArt(tag, size);
                    int glow = glowOf(made);
                    CIRCLE_ART.put(key, made);
                    synchronized (CIRCLE_GLOW) { CIRCLE_GLOW.put(key, glow); }
                    runOnUiThread(() -> {
                        circle.setBackground(null);
                        circle.setImageBitmap(made);
                        wheel.setGlow(tag, glow);
                        artComing--;
                        artShown();
                    });
                });
            }
            circles.add(circle);
        }
        String litTag = focus != null && tags.contains(focus) ? focus : wheel.litTag() != null && tags.contains(wheel.litTag())
            ? wheel.litTag() : tags.get(0);
        wheel.setCircles(circles, Look.wheel(this), litTag);
        for (String tag : tags) {
            Integer glow;
            synchronized (CIRCLE_GLOW) { glow = CIRCLE_GLOW.get(circleKey(tag, size)); }
            if (glow != null) wheel.setGlow(tag, glow);
        }
        wheel.setMoving(moving != null);
        if (!isInside(getCurrentFocus(), panel) && getCurrentFocus() != controlButton && getCurrentFocus() != pillButton
                && getCurrentFocus() != footer && getCurrentFocus() != allAppsCircle) {
            wheel.requestFocus();
        }
    }

    private String circleName(String tag) {
        if (ALL_APPS.equals(tag)) return getString(R.string.main_all_apps);
        if (HomeApps.SETTINGS.equals(tag)) return getString(R.string.first_does_settings);
        return HomeApps.label(this, tag);
    }

    /** An update can change the artwork, so its date is part of the name. */
    private String circleKey(String tag, int size) {
        try {
            return tag + "/" + getPackageManager().getPackageInfo(tag, 0).lastUpdateTime + "/" + size;
        } catch (PackageManager.NameNotFoundException e) {
            return tag + "/" + size;
        }
    }

    /** Salotto's own two circles are drawn here; an app's comes from its artwork, see CircleArt. */
    private Bitmap circleArt(String tag, int size) {
        Bitmap made = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(made);
        if (ALL_APPS.equals(tag) || HomeApps.SETTINGS.equals(tag)) {
            boolean all = ALL_APPS.equals(tag);
            GradientDrawable fill = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, all
                ? new int[]{Color.rgb(96, 100, 112), Color.rgb(58, 62, 72)} : new int[]{Color.rgb(142, 146, 154), Color.rgb(84, 88, 96)});
            fill.setBounds(0, 0, size, size);
            fill.draw(canvas);
            Drawable icon = getDrawable(all ? R.drawable.ic_apps : R.drawable.ic_settings).mutate();
            icon.setTint(Color.WHITE);
            int inset = size * 3 / 10;
            icon.setBounds(inset, inset, size - inset, size - inset);
            icon.draw(canvas);
            return made;
        }
        Art art = art(tag);
        if (art.image == null) {
            canvas.drawColor(CircleArt.PLAIN);
            return made;
        }
        made.recycle();
        return CircleArt.make(art.image, art.back, art.look == BANNER, size, tag);
    }

    /**
     * The app's own colour: the hue that covers most of the circle among the vivid ones, so the
     * red of Netflix's N wins over its black. A grey or white app glows a soft white.
     */
    private static int glowOf(Bitmap art) {
        Bitmap small = Bitmap.createScaledBitmap(art, GLOW_SAMPLE, GLOW_SAMPLE, true);
        float[] weight = new float[GLOW_HUES];
        float[][] sum = new float[GLOW_HUES][3];
        float[] hsv = new float[3];
        int inside = 0;
        float centre = (GLOW_SAMPLE - 1) / 2f;
        for (int x = 0; x < GLOW_SAMPLE; x++) {
            for (int y = 0; y < GLOW_SAMPLE; y++) {
                if (Math.hypot(x - centre, y - centre) > GLOW_SAMPLE / 2f) continue;
                inside++;
                int pixel = small.getPixel(x, y);
                Color.colorToHSV(pixel, hsv);
                if (hsv[1] < 0.35f || hsv[2] < 0.3f) continue;
                int hue = Math.min(GLOW_HUES - 1, (int) (hsv[0] / 360f * GLOW_HUES));
                float strength = hsv[1] * hsv[2];
                weight[hue] += strength;
                sum[hue][0] += Color.red(pixel) * strength;
                sum[hue][1] += Color.green(pixel) * strength;
                sum[hue][2] += Color.blue(pixel) * strength;
            }
        }
        if (small != art) small.recycle();
        int best = 0;
        for (int hue = 1; hue < GLOW_HUES; hue++) if (weight[hue] > weight[best]) best = hue;
        if (weight[best] < inside * GLOW_LEAST) return WheelHome.REST_GLOW;
        int colour = Color.rgb(Math.round(sum[best][0] / weight[best]), Math.round(sum[best][1] / weight[best]),
            Math.round(sum[best][2] / weight[best]));
        Color.colorToHSV(colour, hsv);
        hsv[1] = Math.min(hsv[1], 0.85f);
        hsv[2] = Math.max(hsv[2], 0.9f);
        return Color.HSVToColor(hsv);
    }

    private void openFromWheel(String tag) {
        if (ALL_APPS.equals(tag)) {
            open(new Intent(this, AppsActivity.class));
        } else if (HomeApps.SETTINGS.equals(tag)) {
            open(new Intent(this, SettingsActivity.class));
        } else {
            if (opening) return;
            Intent intent = launchIntent(tag);
            if (intent == null) {
                say(getString(R.string.card_app_unavailable));
                return;
            }
            // Turned off in Aspetto: then the app opens as from the grid.
            if (!Look.wheelMotion(this)) {
                open(intent);
                return;
            }
            // Without Fire OS's own opening: the ring opens straight on, and the app should not slide in under it.
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            opening = true;
            fadeCorners(false, () -> HomeCover.take(getWindow(), () -> {
                float[] spot = wheel.litSpot();
                int colour = wheel.litColour();
                boolean over = TaskService.portal(HomeCover.lastPicture(), wheel.litArt(), colour, spot[0], spot[1], spot[2], () -> {
                    wheel.cover();
                    open(intent);
                }, () -> {
                    Bitmap art = wheel.litArt();
                    if (art != null) HomeCover.armClose(art, wheel.litColour(), spot[0], spot[1], spot[2]);
                });
                if (!over) wheel.openPortal(() -> open(intent));
                // An app that cannot start leaves the Home in front: the colour goes.
                root.postDelayed(() -> {
                    opening = false;
                    if (inFront) closePortal();
                }, PORTAL_GIVE_UP_MS);
            }));
        }
    }

    private void backdropArrived() {
        if (!showing) return;
        String look = Look.signature(this);
        if (look.equals(lookShown)) {
            // The same backdrop, whose picture for this weather may have just arrived.
            showBackdrop(false);
            return;
        }
        lookShown = look;
        applyLook();
    }

    /** Time, weather and the buttons leave at once when an app opens, and come back with the Home. */
    private void fadeCorners(boolean show, Runnable then) {
        View[] corners = {hero, clockShade, weatherCorner, weatherShade, pillBox, footer};
        for (int index = 0; index < corners.length; index++) {
            ViewPropertyAnimator fade = corners[index].animate().alpha(show ? 1f : 0f).setDuration(show ? CORNERS_BACK_MS : CORNERS_GONE_MS);
            if (index == 0 && then != null) fade.withEndAction(then);
            fade.start();
        }
    }

    private final Runnable closeNow = () -> {
        if (!wheel.portalOpen()) return;
        TaskService.dropHomeCover();
        closePortal();
    };

    @Override public void onEnterAnimationComplete() {
        super.onEnterAnimationComplete();
        // Android 9 calls it even when onCreate has handed over to the welcome and built nothing.
        if (wheel == null || !wheel.portalOpen()) return;
        root.removeCallbacks(closeNow);
        closeNow.run();
    }

    private void closePortal() {
        wheel.closePortal(HomeCover::disarm);
        // Under the closing, which is over the whole Home: nothing comes in after it.
        cornersNow();
    }

    /** Time, weather and the buttons there at once, where something covers the Home. */
    private void cornersNow() {
        for (View corner : new View[]{hero, clockShade, weatherCorner, weatherShade, pillBox, footer}) {
            corner.animate().cancel();
            corner.setAlpha(1f);
        }
    }

    private void scrollWhenLaidOut(int target) {
        page.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override public void onLayoutChange(View view, int left, int top, int right, int bottom,
                    int oldLeft, int oldTop, int oldRight, int oldBottom) {
                page.removeOnLayoutChangeListener(this);
                scroll.post(() -> scroll.smoothScrollTo(0, target));
            }
        });
        // Asked for anyway: a page already laid out would never call back.
        page.requestLayout();
    }

    /** Dock up: every Home starts there, without a slide. A page not laid out yet cannot scroll, so it waits. */
    private void restRaised() {
        if (page.isLaidOut() && !page.isLayoutRequested() && page.getHeight() > shelf) {
            scroll.scrollTo(0, shelf);
            return;
        }
        page.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override public void onLayoutChange(View view, int left, int top, int right, int bottom,
                    int oldLeft, int oldTop, int oldRight, int oldBottom) {
                page.removeOnLayoutChangeListener(this);
                scroll.post(() -> { if (appsOnly) scroll.scrollTo(0, shelf); });
            }
        });
    }

    private View homeCard(LinearLayout row, String pkg, int width) {
        if (HomeApps.SETTINGS.equals(pkg)) {
            return card(row, pkg, getString(R.string.first_does_settings), getDrawable(R.drawable.ic_settings), null, SHADED, width,
                () -> open(new Intent(this, SettingsActivity.class)), () -> showMenu(pkg));
        }
        return appCard(row, HomeApps.label(this, pkg), pkg, width, () -> showMenu(pkg));
    }

    @Override protected boolean squareCards() { return clean; }

    /**
     * An app on the clean Home: its picture as on the wheel, square and cut to a squircle, made away
     * from the main thread the first time. Salotto's settings and Tutte le app are drawn here too.
     */
    private View squircleCard(LinearLayout row, String tag, int size) {
        String key = circleKey(tag, size) + "/squircle";
        Bitmap known = CIRCLE_ART.get(key);
        Runnable action;
        Runnable hold;
        if (ALL_APPS.equals(tag)) {
            action = () -> open(new Intent(this, AppsActivity.class));
            hold = null;
        } else if (HomeApps.SETTINGS.equals(tag)) {
            action = () -> open(new Intent(this, SettingsActivity.class));
            hold = () -> showMenu(tag);
        } else {
            action = () -> openPackage(tag);
            hold = () -> showMenu(tag);
        }
        if (squirclePlaceholder == null || squirclePlaceholder.getWidth() != size) {
            Bitmap plain = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            plain.eraseColor(CircleArt.PLAIN);
            squirclePlaceholder = Squircle.cut(plain);
            plain.recycle();
        }
        // Tutte le app: a light veil and the icon, drawn at once, with no shadow under it.
        if (ALL_APPS.equals(tag) && known == null) {
            known = allAppsSquircle(size);
            CIRCLE_ART.put(key, known);
        }
        Bitmap shown = known != null ? known : squirclePlaceholder;
        View tile = card(row, tag, circleName(tag), new BitmapDrawable(getResources(), shown), null,
            ALL_APPS.equals(tag) ? GLASS : ICON, size, action, hold);
        if (known == null) {
            ImageView image = (ImageView) ((LinearLayout) tile).getChildAt(0);
            artComing++;
            Background.draw(() -> {
                Bitmap square = circleArt(tag, size);
                int colour = glowOf(square);
                // The icon's own background, along its edge, where the line is drawn.
                boolean dark = brightness(CircleArt.rim(square)) < CLEAN_DARK;
                Bitmap made = Squircle.cut(square);
                square.recycle();
                colourEdge(made, colour, dark);
                CIRCLE_ART.put(key, made);
                runOnUiThread(() -> {
                    image.setImageBitmap(made);
                    artComing--;
                    artShown();
                });
            });
        }
        return tile;
    }

    /**
     * A very thin line along the squircle in the app's own colour, the one the wheel glows with:
     * the user's request of 2026-11-09. Drawn into the picture, inside its edge.
     */
    private void colourEdge(Bitmap squircle, int colour, boolean dark) {
        int size = squircle.getWidth();
        float line = getResources().getDisplayMetrics().density * CLEAN_EDGE;
        Path edge = Squircle.path(size - line);
        edge.offset(line / 2, line / 2);
        Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(line);
        // Its own colour exactly vanished on an icon of one colour: a little lighter, the same hue.
        rim.setColor(Color.argb(dark ? CLEAN_EDGE_DARK_ALPHA : CLEAN_EDGE_ALPHA, lighter(Color.red(colour)), lighter(Color.green(colour)), lighter(Color.blue(colour))));
        new Canvas(squircle).drawPath(edge, rim);
    }

    /** 0 for black, 1 for white, as the eye weighs the three colours. */
    private static float brightness(int colour) {
        return (0.299f * Color.red(colour) + 0.587f * Color.green(colour) + 0.114f * Color.blue(colour)) / 255f;
    }

    private static int lighter(int channel) {
        return Math.round(channel + (255 - channel) * CLEAN_EDGE_LIGHT);
    }

    private Bitmap allAppsSquircle(int size) {
        Bitmap made = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(made);
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(ALL_APPS_FILL);
        canvas.drawPath(Squircle.path(size), fill);
        // A light line along its edge: so faint a veil would be lost among the apps' own colours.
        float line = dp(1);
        Path edge = Squircle.path(size - line);
        edge.offset(line / 2, line / 2);
        Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(line);
        rim.setColor(ALL_APPS_EDGE);
        canvas.drawPath(edge, rim);
        Drawable icon = getDrawable(R.drawable.ic_apps).mutate();
        icon.setTint(Color.WHITE);
        int inset = size * 3 / 10;
        icon.setBounds(inset, inset, size - inset, size - inset);
        icon.draw(canvas);
        return made;
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
        return clean || tile.getParent() != page.getChildAt(0);
    }

    /** The app selected last, to come back to after the settings or the control centre. */
    private String lastApp;

    @Override protected void onCardFocus(View tile) {
        if (tile.getTag() instanceof String) lastApp = (String) tile.getTag();
        int index = page.indexOfChild((View) tile.getParent());
        int rowsOnScreen = 2;
        int target = index == 0 ? (appsOnly ? shelf : 0) : shelf + Math.max(0, index - rowsOnScreen) * rowHeight;
        // Back on the first row from the clean Home's control centre: the row climbs only once its
        // tiles are gone, from showPanel. Climbing now, it went up through them (2026-11-09).
        if (index == 0 && panelRest >= 0 && (panelOpen || SystemClock.uptimeMillis() < climbAt)) return;
        // A page not laid out yet has nothing to scroll: a smooth scroll there went back to the bottom,
        // and a first row wanted at the top stayed low as the Home started. It waits for the layout,
        // and is never skipped: back up from the credits the first row stayed at the top (2026-11-09).
        if (!page.isLaidOut() || page.isLayoutRequested()) {
            // At the top as the Home starts: there at once, without a slide.
            if (appsOnly && target == shelf) restRaised();
            else scrollWhenLaidOut(target);
            return;
        }
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
        boolean app = !HomeApps.SETTINGS.equals(pkg);
        String title = app ? HomeApps.label(this, pkg) : getString(R.string.first_does_settings);
        // Salotto's settings can leave the Home too: the gear beside the apps opens them, and Tutte le app puts the card back.
        String[] choices = app ? new String[]{getString(R.string.main_move), getString(R.string.card_remove_from_home),
                getString(R.string.card_app_info), getString(R.string.card_uninstall)}
            : new String[]{getString(R.string.main_move), getString(R.string.card_remove_from_home)};
        dialog()
            .setTitle(title)
            .setItems(choices, (dialog, choice) -> {
                if (choice == 0) {
                    moving = pkg;
                    hint.setVisibility(View.VISIBLE);
                    render(pkg);
                } else if (choice == 1) {
                    home.remove(pkg);
                    if (!app) HomeApps.setSettingsOff(this, true);
                    HomeApps.save(this, home);
                    // Its neighbour takes the selection, not the first app: see neighbourOf.
                    render(pkg);
                } else if (choice == 2) {
                    appInfo(pkg);
                } else {
                    uninstall(pkg);
                }
            })
            .show();
    }

    private void move(int delta) {
        // A held key on the wheel moves one place at a steady pace: a swap that overtook the last one would tangle the circles.
        long now = SystemClock.uptimeMillis();
        if (circular && now - lastMove < WHEEL_MOVE_MS) return;
        lastMove = now;
        int from = shownApps.indexOf(moving);
        // The wheel has no ends: past the last app comes the first.
        int to = circular ? Math.floorMod(from + delta, shownApps.size()) : from + delta;
        if (from < 0 || to < 0 || to >= shownApps.size() || to == from) return;
        String other = shownApps.get(to);
        // The wheel has no cards to glide: it turns to the moved app instead.
        Map<String, int[]> before = new HashMap<>();
        if (!circular) {
            before.put(moving, spot(moving));
            before.put(other, spot(other));
        }
        Collections.swap(shownApps, from, to);
        List<String> order = new ArrayList<>(shownApps);
        for (String pkg : home) if (!order.contains(pkg)) order.add(pkg);
        home = order;
        HomeApps.save(this, home);
        stopJiggle();
        if (circular) {
            renderWheel(moving, shownApps);
            return;
        }
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

    private static boolean isArrowOrOk(int key) {
        return key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT || key == KeyEvent.KEYCODE_DPAD_UP
            || key == KeyEvent.KEYCODE_DPAD_DOWN || key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER;
    }

    /** The clean Home's selected app has no ring: it grows clearly instead, the user's choice of 2026-11-09. */
    private static final float CLEAN_FOCUS_SCALE = 1.3f;

    @Override protected float focusScale() {
        if (clean) return CLEAN_FOCUS_SCALE;
        return moving != null ? 1.12f : super.focusScale();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        // The circle is closing over the Home: the arrows would move the wheel under it.
        if (HomeCover.busy && isArrowOrOk(event.getKeyCode())) return true;
        // Dock up: one Up opens the control panel, as from the dock down. The scroll would take it first.
        if (moving == null && appsOnly && event.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP
                && dock != null && isInside(getCurrentFocus(), dock)) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) controlButton.requestFocus();
            return true;
        }
        // Down from the last row reaches the credits at once: off the screen, a first press only scrolled.
        if (moving == null && !circular && event.getKeyCode() == KeyEvent.KEYCODE_DPAD_DOWN && lastRowHas(getCurrentFocus())
                && footer.getVisibility() == View.VISIBLE && footer.getParent() == page) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) footer.requestFocus();
            return true;
        }
        if (moving == null) return super.dispatchKeyEvent(event);
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        if (circular && (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP || event.getKeyCode() == KeyEvent.KEYCODE_DPAD_DOWN)) {
            return true;
        }
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

    private boolean lastRowHas(View focus) {
        if (focus == null || !(focus.getParent() instanceof View)) return false;
        int row = page.indexOfChild((View) focus.getParent());
        return row >= 0 && row == page.indexOfChild(footer) - 1;
    }

    /** Back leaves a move or returns to the first row; the Home itself has nothing behind it. */
    @Override public void onBackPressed() {
        View focus = getCurrentFocus();
        boolean onButtons = focus == controlButton || focus == pillButton || focus == networkButton || isInside(focus, panel);
        if (moving != null) stopMoving();
        // From the buttons or the control centre: back on the app selected last, not the first (2026-11-09).
        else if (onButtons && lastAppView() != null) {
            lastAppView().requestFocus();
            showPanel(false);
        } else if (circular) {
            wheel.requestFocus();
            wheel.turnToFirst();
        } else if (firstCard() != null) firstCard().requestFocus();
    }

    /** The wheel is one view: what has the focus is the app at its top. */
    @Override protected String focusedTag() {
        if (circular && wheel.hasFocus()) return wheel.litTag();
        return super.focusedTag();
    }

    private View firstCard() {
        if (circular) return wheel;
        return page.getChildCount() > 0 ? ((LinearLayout) page.getChildAt(0)).getChildAt(0) : null;
    }
}
