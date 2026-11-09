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
    /** The Home as a wheel of circles: ora e meteo in the top corners, the credits at the bottom. */
    private boolean circular;
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
        panel.setListed(circular);
        // On the wheel the date goes above the time, where the eye starts reading the corner.
        hero.removeView(date);
        hero.addView(date, circular ? 0 : hero.indexOfChild(clock) + 1);
        hero.post(this::alignDate);
        // The big digits carry room above them for accents they never have: under the date it reads as a gap.
        clock.setIncludeFontPadding(!circular);
        panelShade.setBackground(circular ? new ColorDrawable(Color.argb(200, 0, 0, 0)) : null);
        heroPlace.leftMargin = dp(circular ? 32 : DOCK_MARGIN + DOCK_SIDE);
        heroPlace.topMargin = dp(circular ? 14 : 40);
        hero.setLayoutParams(heroPlace);
        FrameLayout.LayoutParams pillSpot = (FrameLayout.LayoutParams) pillBox.getLayoutParams();
        pillSpot.gravity = circular ? Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL : Gravity.TOP | Gravity.END;
        pillSpot.topMargin = circular ? 0 : dp(22);
        pillSpot.rightMargin = circular ? 0 : dp(DOCK_MARGIN);
        pillSpot.bottomMargin = circular ? dp(12) : 0;
        pillBox.setLayoutParams(pillSpot);
        pillBox.setBackground(circular ? null : pillGlass);
        pillBox.setForeground(circular ? null : pillEdge);
        int icon = dp(circular ? FREE_ICON : PILL_ICON);
        for (ImageView button : new ImageView[]{controlButton, pillButton}) {
            LinearLayout.LayoutParams size = (LinearLayout.LayoutParams) button.getLayoutParams();
            size.width = icon;
            size.height = icon;
            button.setLayoutParams(size);
            int inset = dp(circular ? 2 : 3);
            button.setPadding(inset, inset, inset, inset);
        }
        // Set again as layout params: a margin end changed in place is not applied once the button is
        // on screen, and switching to the wheel left Tutte le app 3 dp from it instead of 22 (2026-11-06).
        LinearLayout.LayoutParams beside = (LinearLayout.LayoutParams) controlButton.getLayoutParams();
        beside.setMarginEnd(dp(circular ? BESIDE_ALL_APPS : PILL_ICON_GAP));
        controlButton.setLayoutParams(beside);
        FrameLayout.LayoutParams weatherSpot = (FrameLayout.LayoutParams) weatherCorner.getLayoutParams();
        weatherSpot.rightMargin = dp(32);
        weatherSpot.topMargin = dp(WEATHER_TOP);
        weatherCorner.setLayoutParams(weatherSpot);
        footer.setTextSize(circular ? 7 : FOOTER_SP);
        tonePill();
    }

    /** White with a small shadow over a medium or dark picture, black over a very light one. */
    private void tonePill() {
        int ink = circular && lightCorner ? Color.BLACK : Color.WHITE;
        for (TextView text : new TextView[]{pillTime, pillDate, pillWeather}) {
            text.setTextColor(ink);
            if (circular && !lightCorner) text.setShadowLayer(dp(3), 0, dp(1), Color.argb(170, 0, 0, 0));
            else text.setShadowLayer(0, 0, 0, 0);
        }
        lightPillIcon(controlButton, controlButton.hasFocus());
        lightPillIcon(pillButton, pillButton.hasFocus());
    }

    @Override protected void lightPillIcon(ImageView button, boolean lit) {
        if (!pillRings.containsKey(button)) pillRings.put(button, button.getBackground());
        if (!circular || lit) {
            button.setBackground(pillRings.get(button));
            super.lightPillIcon(button, lit);
            return;
        }
        button.setBackground(lightCorner ? null : iconShadow(button));
        button.setColorFilter(lightCorner ? Color.BLACK : Color.WHITE);
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
        blur.setMaskFilter(new BlurMaskFilter(dp(2), BlurMaskFilter.Blur.NORMAL));
        int[] offset = new int[2];
        Bitmap alpha = shape.extractAlpha(blur, offset);
        Bitmap shadow = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Paint dark = new Paint();
        dark.setColor(Color.argb(150, 0, 0, 0));
        new Canvas(shadow).drawBitmap(alpha, offset[0], offset[1] + dp(1), dark);
        shape.recycle();
        alpha.recycle();
        return new BitmapDrawable(getResources(), shadow);
    }

    /** How light the picture is around the buttons at the bottom, read again only when it changes. */
    private void readCorner() {
        String name = Look.currentName(this);
        if (!circular || Objects.equals(name, cornerRead)) return;
        cornerRead = name;
        Look.current(this, picture -> {
            int width = picture.getWidth();
            int height = picture.getHeight();
            double sum = 0;
            int count = 0;
            for (int x = width * 3 / 8; x < width * 5 / 8; x += 4) {
                for (int y = height * 5 / 6; y < height; y += 4) {
                    int pixel = picture.getPixel(x, y);
                    sum += (0.2126 * Color.red(pixel) + 0.7152 * Color.green(pixel) + 0.0722 * Color.blue(pixel)) / 255;
                    count++;
                }
            }
            boolean light = count > 0 && sum / count > LIGHT_CORNER;
            if (light == lightCorner) return;
            lightCorner = light;
            tonePill();
        });
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

    private void alignDate() {
        if (!circular || hero == null || date.getParent() != hero || cornerLine.getVisibility() != View.VISIBLE) {
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
        String line = Weather.line(this);
        cornerLine.setVisibility(line != null ? View.VISIBLE : View.GONE);
        if (line != null && !line.contentEquals(cornerLine.getText())) cornerLine.setText(WeatherIcon.in(this, line));
        cornerDays.removeAllViews();
        for (Weather.Day day : Weather.days(this)) {
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
        pillRow.addView(controlButton, beside);
        pillRow.addView(settings, new LinearLayout.LayoutParams(dp(PILL_ICON), dp(PILL_ICON)));
        pillButton = settings;
        // The wheel covers the whole screen, so the arrows would not find it from the buttons by themselves.
        settings.setOnKeyListener((view, code, event) -> {
            if (outOfRow(code, true)) return backToWheel(code, event);
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
        if (focus != null) showPanel(focus == controlButton && !circular || isInside(focus, panel));
    }

    private boolean isInside(View view, View parent) {
        for (View step = view; step != null; step = step.getParent() instanceof View ? (View) step.getParent() : null) {
            if (step == parent) return true;
        }
        return false;
    }

    /** The panel follows the focus: it goes to where the Home rests, the wheel or the first card. */
    @Override public void leave() {
        if (!panelOpen) return;
        View rest = circular ? wheel : dock != null && dock.getChildCount() > 0 ? dock.getChildAt(0) : null;
        if (rest != null) rest.requestFocus();
        showPanel(false);
    }

    private void showPanel(boolean open) {
        if (open == panelOpen) return;
        panelOpen = open;
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
            panelShade.animate().alpha(0f).setDuration(PILL_MS).withEndAction(() -> panelShade.setVisibility(View.GONE)).start();
        }
        // The tiles drop in from the pill.
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
            if (focused && !circular) scroll.smoothScrollTo(0, page.getHeight() - scroll.getHeight());
        });
        view.setOnClickListener(clicked -> open(new Intent(this, AboutActivity.class)));
        view.setOnKeyListener((changed, code, event) -> {
            if (!circular || code != KeyEvent.KEYCODE_DPAD_DOWN) return false;
            if (event.getAction() == KeyEvent.ACTION_DOWN) wheel.requestFocus();
            return true;
        });
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
        if (symbol != null) pillWeather.setText(WeatherIcon.in(this, symbol));
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
        if (!intent.getBooleanExtra(OPEN_PANEL, false)) {
            if (appsOnly) restRaised();
            return;
        }
        View from = getCurrentFocus();
        boolean onCards = from != null && from != controlButton && !isInside(from, panel);
        // Dock up, the Home stays where it is and the panel opens over it: no slide down and back.
        if (!appsOnly) scroll.smoothScrollTo(0, 0);
        showPanel(true);
        panel.openEmpty(onCards ? from : firstCard());
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
        homeMaybeReady();
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
        if (circular) readCorner();
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
        homeMaybeReady();
    }

    private void blurAndPlace(Consumer<Canvas> paint, Runnable place) {
        swapsComing++;
        blurReady = false;
        blurOf(paint, true, soft -> {
            swapsComing--;
            backdropsComing--;
            place.run();
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

    private void showWeather() {
        String line = Weather.line(this);
        weather.setVisibility(line != null ? View.VISIBLE : View.GONE);
        if (line != null && !line.contentEquals(weather.getText())) weather.setText(WeatherIcon.in(this, line));
        String tomorrow = Weather.tomorrow(this);
        forecast.setVisibility(tomorrow != null ? View.VISIBLE : View.GONE);
        if (tomorrow != null && !tomorrow.contentEquals(forecast.getText())) forecast.setText(WeatherIcon.in(this, tomorrow));
        weatherSymbol = Weather.symbol(this);
        if (circular) showWeatherCorner();
        showPillClock();
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
        boolean wasCircular = circular;
        circular = Look.circular(this);
        wheel.setGlow(Look.glow(this));
        // Gone, not hidden: neither the arrows nor the grid's spacing find it.
        footer.setVisibility(Look.credits(this) ? View.VISIBLE : View.GONE);
        // Also when the Home starts, plain: until then only the window's grey would show, the user's
        // report of 2026-11-07. The loading itself only for a switch, the user's request of 2026-11-09.
        if (!looked || circular != wasCircular) showLoading(looked);
        looked = true;
        boolean blur = circular && Look.wheelBlur(this);
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
        if (dock != null) dock.setBackground(dockGlass(dp(26), Look.weatherBackdrop(this)));
        columns = Look.columns(this);
        clock.setTypeface(Typeface.create(Look.FONT_FAMILIES[Look.font(this)], Typeface.NORMAL));
        clock.setTextSize(Look.SIZES[Look.size(this)]);
        alignInk(clock);
        bigClock = Look.clock(this);
        clock.setVisibility(bigClock ? View.VISIBLE : View.GONE);
        appsOnly = Look.appsOnly(this) && !circular;
        scroll.setVisibility(circular ? View.GONE : View.VISIBLE);
        wheel.setVisibility(circular ? View.VISIBLE : View.GONE);
        weatherRow.setVisibility(circular ? View.GONE : View.VISIBLE);
        weatherCorner.setVisibility(circular ? View.VISIBLE : View.GONE);
        weatherShade.setVisibility(circular ? View.VISIBLE : View.GONE);
        if (circular) {
            scroll.scrollTo(0, 0);
            hero.setAlpha(1);
            clockShade.setAlpha(1);
            showWeatherCorner();
        }
        allAppsCircle.setVisibility(circular ? View.VISIBLE : View.GONE);
        if (circular != wasCircular) {
            placeFooter();
            placeCorners();
            cornerRead = null;
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
        if (circular) {
            renderWheel(focus, apps);
            return;
        }
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
        if (footer.getParent() != null) ((ViewGroup) footer.getParent()).removeView(footer);
        page.addView(footer, foot);
        (target != null ? target : first).requestFocus();
        if (moving != null && target != null) startJiggle(target);
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
        int target = index == 0 ? (appsOnly ? shelf : 0) : shelf + Math.max(0, index - rowsOnScreen) * rowHeight;
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

    @Override protected float focusScale() { return moving != null ? 1.12f : super.focusScale(); }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        // The circle is closing over the Home: the arrows would move the wheel under it.
        if (HomeCover.busy && isArrowOrOk(event.getKeyCode())) return true;
        // Dock up: one Up opens the control panel, as from the dock down. The scroll would take it first.
        if (moving == null && appsOnly && event.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP
                && dock != null && isInside(getCurrentFocus(), dock)) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) controlButton.requestFocus();
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

    /** Back leaves a move or returns to the first row; the Home itself has nothing behind it. */
    @Override public void onBackPressed() {
        if (moving != null) stopMoving();
        else if (circular) {
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
