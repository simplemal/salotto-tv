package it.simplemal.firehome;

import android.graphics.drawable.ColorDrawable;
import android.content.res.ColorStateList;
import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.usage.StorageStats;
import android.app.usage.StorageStatsManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.IntentFilter;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Bundle;
import android.os.Process;
import android.os.storage.StorageManager;
import android.net.Uri;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.ToLongFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONException;

/** Settings in two columns: the sections on the left, always there; the page of the one chosen on the right. */
public final class SettingsActivity extends CardActivity {
    private static final String CHECK = "✓";
    private static final int COLUMN_TOP = 44;
    private static final int NO_MARK = -1;
    private static final int LABEL_SP = 15;
    private static final int LABEL_ROOM = 6;
    private static final int MARK_SIZE = 20;
    private static final int MARK_GAP = 12;
    private static final int LOCK_SIZE = 16;
    private static final int WIFI_5GHZ = 5000;
    /** The column of sections, and the room between it and the page. */
    private static final int MENU_WIDTH = 262;
    private static final int MENU_GAP = 28;
    private static final int MENU_ROW = 40;
    private static final int ROWS_TOP = 14;
    private static final int ROW_HEIGHT = 48;
    /** In the window everything is a size smaller. */
    private static final int SMALL_ROW_HEIGHT = 40;
    /** The outer columns of pictures stay whole beside it, see PictureMosaic. */
    private static final int POPUP_WIDTH = 440;
    private static final int POPUP_HEIGHT = 390;
    private static final int SHADE = Color.argb(150, 0, 0, 0);
    private static final long SECTION_PAUSE_MS = 140;
    private static final int APPS_SECTION = 3;
    private static final int SCREEN_SECTION = 4;
    private static final int NETWORK_SECTION = 5;
    private static final int FIRE_SECTION = 8;
    static final String RESOLUTION = "pq_video_resolution";
    private static final String[] RESOLUTIONS = {"-1", "97", "96", "16", "31", "4", "19"};
    private static final int[] RESOLUTION_NAMES = {R.string.settings_resolution_auto, R.string.settings_resolution_4k_60,
        R.string.settings_resolution_4k_50, R.string.settings_resolution_1080_60, R.string.settings_resolution_1080_50,
        R.string.settings_resolution_720_60, R.string.settings_resolution_720_50};
    private static final int RESOLUTION_WAIT_S = 15;
    private static final String RANGE = "pq_dynamic_range";
    private static final String[] RANGES = {"adaptive", "forced", "forcedsdr"};
    private static final int[] RANGE_NAMES = {R.string.settings_range_adaptive, R.string.settings_range_hdr,
        R.string.settings_range_sdr};
    private static final String COLOR_FORMAT = "pq_color_format";
    private static final String[] COLOR_FORMATS = {"auto", "rgb", "ycbcr"};
    private static final String COLOR_DEPTH = "pq_color_depth";
    private static final String[] COLOR_DEPTHS = {"8", "10", "12"};
    private static final int[] COLOR_DEPTH_NAMES = {R.string.settings_color_depth_8, R.string.settings_color_depth_10,
        R.string.settings_color_depth_12};
    private static final String SURROUND = "aq_surround_sound";
    private static final String[] SURROUNDS = {"BEST_AVAILABLE", "STEREO", "DD_PLUS_ALWAYS", "DD_ALWAYS"};
    /** In the order of encoded_surround_output: 0 to 3. */
    private static final String SURROUND_MODE = "encoded_surround_output";
    static final String LEVELER = "aq_volume_leveler";
    static final String DIALOGUE = "aq_dialogue_enhancer";
    private static final String VIDEO_QUALITY = "com.amazon.tv.settings.VIDEO_QUALITY";
    private static final int[] VIDEO_QUALITY_NAMES = {R.string.settings_quality_good,
        R.string.settings_video_quality_better, R.string.settings_video_quality_best};
    private static final String YES = "Sì";
    private static final String NO = "No";
    private static final String WIFI_ON = "wifi-on";
    private static final int MINUTE = (int) Fire.MINUTE_MS;
    private static final String SAVER_ENABLED = "screensaver_enabled";
    private static final String SAVER_CHOSEN = "screensaver_components";
    private static final int[] SAVER_MINUTES = {1, 5, 10, 15, 30};
    private static final int SAVER_DEFAULT = 5 * MINUTE;
    private static final int[] SLEEP_MINUTES = {5, 20, 30, 60};
    private static final int COUNTER_MS = 200;
    private static final int COUNTER_TRIES = 25;
    private static final int SLEEP_DEFAULT = 20 * MINUTE;
    private static final String SLEEP_TIMEOUT = "sleep_timeout";
    private static final String CEC_ENABLED = "hdmi_control_enabled";
    private static final String CEC_TV_OFF = "hdmi_control_auto_device_off_enabled";
    private static final String ADS_LIMITED = "amz_limit_ad_tracking";
    private static final String USAGE_METRICS = "USAGE_METRICS_UPLOAD_ENABLED";
    static final float QUICK_SCALE = 0.5f;
    private static final String LOCATION = Manifest.permission.ACCESS_FINE_LOCATION;
    private static final int[] WIFI_STRENGTHS = {R.string.settings_wifi_weak, R.string.settings_wifi_weak,
        R.string.settings_wifi_medium, R.string.settings_quality_good, R.string.settings_wifi_strong};
    private static final long WIFI_SCAN_MS = 3000;
    private static final long WIFI_JOIN_MS = 6000;
    private static final int ROW_ROOM = 14;
    private static final int[] TABLE_COLUMNS = {R.string.settings_table_stick, R.string.settings_table_tv,
        R.string.settings_table_in_use};
    private static final int[] TABLE_WIDTHS = {78, 78, 92};
    private static final int TABLE_ROW = 34;
    private static final int TABLE_PADDING = 14;

    /** One of Salotto's own screensavers, in the list after Fire OS's: the same service with another set. */
    private static final class Saver {
        final String name;
        final String folder;

        Saver(String name, String folder) {
            this.name = name;
            this.folder = folder;
        }
    }

    private static final Saver[] SAVERS = {new Saver(Look.ABSTRACT, Look.ABSTRACT_FOLDER),
        new Saver(Look.ABSTRACT_3D, Look.ABSTRACT_3D_FOLDER), new Saver(Look.NOX, Look.NOX_FOLDER),
        new Saver(Look.NATURE, Look.NATURE_FOLDER)};

    private static final class Entry {
        final String title;
        final String value;
        final Drawable picture;
        final String text;
        final Runnable action;
        final boolean branch;
        String family;
        /** Its icon is loaded when the row gets the focus: a long list would pay for all of them. */
        String pkg;
        /** The values of a table page, one for each of TABLE_COLUMNS. */
        String[] columns;
        boolean opensGroup;
        /** A lock at the end of the row. */
        boolean locked;
        /** Shown but not to be chosen: the focus skips it. */
        boolean off;
        /** A heading for the rows below it: not a row. */
        boolean label;
        /** Beside the title: what kind of backdrop it is. Zero keeps the room empty, to stay lined up. */
        int mark = NO_MARK;
        /** One of a list of choices: pressing it also closes the window of choices. */
        boolean picks;

        Entry(String title, String value, Drawable picture, String text, boolean branch, Runnable action) {
            this.title = title;
            this.value = value;
            this.picture = picture;
            this.text = text;
            this.branch = branch;
            this.action = action;
        }
    }

    /** One step down the tree: how to redraw it, which row to return to, what to stop on leaving. */
    private static final class Level {
        final Runnable show;
        int focus;
        Runnable leave;
        /** The title of the row that opened the level below this one. */
        String opened;
        /** The real page is on screen: reading it again must not replace it with the waiting one. */
        boolean drawn;
        /** Under the title: what the whole section is for. Only the first page of a section has it. */
        String about;
        String title;
        /** Above the title of a window: where it was opened from, the heading over its row or the page. */
        String context;
        Level(Runnable show) { this.show = show; }
    }

    private static final class Pane {
        TextView heading;
        LinearLayout tableHead;
        ScrollView scroll;
        LinearLayout rows;
    }

    /** One line of the column on the left: a section with its first page. */
    private static final class Section {
        final int name;
        final int icon;
        final int about;
        final Runnable show;
        Section(int name, int icon, int about, Runnable show) {
            this.name = name;
            this.icon = icon;
            this.about = about;
            this.show = show;
        }
    }

    /** Opens directly on a section, for the control panel. */
    static final String SECTION = "section";
    static final String NETWORK = "network";
    static final String REMOTES = "remotes";
    static final String SCREEN = "screen";
    static final String APP = "app";
    static final String PACKAGE = "package";

    private final Deque<Level> levels = new ArrayDeque<>();
    /** The rows on screen, in the order of their views. */
    private List<Entry> shownEntries;

    /** One level of the way to where the user left the settings with Home. */
    private static final class Spot {
        final int focus;
        final String opened;
        Spot(int focus, String opened) {
            this.focus = focus;
            this.opened = opened;
        }
    }

    /**
     * Where the settings were left, from the first level down, while Salotto runs: opened again they
     * go back there, the user's wish of 2026-11-04. Leaving them with Back forgets it.
     */
    private static List<Spot> leftAt;
    private static int leftSection;
    private static boolean leftInMenu = true;
    private LinearLayout root;
    private TextView heading;
    private TextView about;
    private LinearLayout menu;
    private Section[] sections;
    private int section;
    /** The focus is on the column on the left: the page on the right only shows what is there. */
    private boolean inMenu = true;
    /** Moving quickly down the column draws only the page where it stops. */
    private final Runnable showSection = this::openSection;
    /** The section's page, and the window over it for everything a row opens: the same size for all. */
    private Pane page;
    private Pane popup;
    private FrameLayout shade;
    private TextView eyebrow;
    /** The rows being built belong to the window: a size smaller. */
    private boolean small;
    private ImageView backdropView;
    /** A key pressed in the column: its release must not reach the page it opened. */
    private int heldInMenu;
    /** Fire OS answers slowly: the page shows what was read last and reads again when it is stale. */
    private String[] picture;
    private String[] sound;
    private boolean avStale = true;
    private boolean avReading;
    /** The network page opened from the Bluetooth tile starts on the Bluetooth rows. */
    private boolean toRemotes;
    private LinearLayout rows;
    private ScrollView scroll;
    private LinearLayout tableHead;
    private BroadcastReceiver bluetoothListener;
    private FireSettings fire;
    private Level uninstalling;
    private boolean resumedOnce;
    private Drawable focusFill;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_LOCALE);
        fire = new FireSettings(this);
        // Before any row shows a photo's name.
        Look.tidyPersonal(this);
        DeviceInfo.prepare();
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(MARGIN), 0, dp(MARGIN), 0);

        LinearLayout panes = new LinearLayout(this);
        root.addView(panes, new LinearLayout.LayoutParams(-1, 0, 1));

        // On the left the sections, always there; on the right the page of the one chosen.
        sections = new Section[]{
            new Section(R.string.settings_cat_home, R.drawable.ic_s_home, R.string.settings_cat_home_text, this::showHome),
            new Section(R.string.settings_cat_clock, R.drawable.ic_clock, R.string.settings_cat_clock_text, this::showClock),
            new Section(R.string.settings_saver, R.drawable.ic_image, R.string.settings_cat_saver_text, this::showSaver),
            new Section(R.string.settings_apps, R.drawable.ic_apps, R.string.settings_cat_apps_text, this::showApps),
            new Section(R.string.settings_screen, R.drawable.ic_s_tv, R.string.settings_cat_screen_text, this::showScreen),
            new Section(R.string.settings_cat_network, R.drawable.ic_wifi, R.string.settings_cat_network_text, this::showNetwork),
            new Section(R.string.settings_cat_system, R.drawable.ic_settings, R.string.settings_cat_system_text, this::showSystem),
            new Section(0, R.drawable.ic_salotto, R.string.settings_cat_salotto_text, this::showSalotto),
            new Section(R.string.settings_fire, R.drawable.ic_s_open, 0, this::showFire)};
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setPadding(0, dp(COLUMN_TOP - 8), dp(MENU_GAP), 0);
        TextView title = label(getString(R.string.settings_title), 26);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setSingleLine(true);
        title.setPadding(dp(14), 0, dp(8), dp(14));
        left.addView(title);
        menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        for (int index = 0; index < sections.length; index++) {
            if (index == FIRE_SECTION) {
                View line = new View(this);
                line.setBackgroundColor(Color.argb(40, 255, 255, 255));
                LinearLayout.LayoutParams place = new LinearLayout.LayoutParams(-1, Math.max(1, dp(1)));
                place.setMargins(dp(14), dp(6), dp(14), dp(8));
                menu.addView(line, place);
            }
            menu.addView(menuRow(index), menuRowSize());
        }
        left.addView(menu, new LinearLayout.LayoutParams(-1, -2));
        panes.addView(left, new LinearLayout.LayoutParams(dp(MENU_WIDTH + MENU_GAP), -1));

        about = label("", 15);
        about.setAlpha(0.7f);
        about.setMaxLines(2);
        about.setEllipsize(TextUtils.TruncateAt.END);
        about.setPadding(dp(14), dp(4), dp(14), 0);
        page = pane(COLUMN_TOP - 8);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        // Title and description scroll with the rows: no row ever slides under a fixed header.
        LinearLayout pageColumn = new LinearLayout(this);
        pageColumn.setOrientation(LinearLayout.VERTICAL);
        page.scroll.removeView(page.rows);
        pageColumn.addView(page.heading, new LinearLayout.LayoutParams(-1, -2));
        pageColumn.addView(about, new LinearLayout.LayoutParams(-1, -2));
        pageColumn.addView(page.tableHead, new LinearLayout.LayoutParams(-1, -2));
        pageColumn.addView(page.rows, new LinearLayout.LayoutParams(-1, -2));
        page.scroll.addView(pageColumn);
        list.addView(page.scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        panes.addView(list, new LinearLayout.LayoutParams(0, -1, 1));

        // Everything a row opens comes up in the middle, over the page, always the same size.
        popup = pane(18);
        popup.heading.setTextSize(20);
        popup.heading.setPadding(dp(16), 0, dp(16), dp(4));
        eyebrow = label("", 13);
        eyebrow.setAlpha(0.6f);
        eyebrow.setSingleLine(true);
        eyebrow.setEllipsize(TextUtils.TruncateAt.END);
        eyebrow.setPadding(dp(16), dp(16), dp(16), dp(2));
        // The title scrolls with the rows, so that a long list never cuts it.
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        popup.scroll.removeView(popup.rows);
        column.addView(eyebrow, new LinearLayout.LayoutParams(-1, -2));
        column.addView(popup.heading, new LinearLayout.LayoutParams(-1, -2));
        column.addView(popup.tableHead, new LinearLayout.LayoutParams(-1, -2));
        column.addView(popup.rows, new LinearLayout.LayoutParams(-1, -2));
        popup.scroll.addView(column);
        // As tall as what it holds, up to POPUP_HEIGHT, and always in the middle.
        LinearLayout card = new LinearLayout(this) {
            @Override protected void onMeasure(int widthSpec, int heightSpec) {
                super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(dp(POPUP_HEIGHT), MeasureSpec.AT_MOST));
            }
        };
        card.setOrientation(LinearLayout.VERTICAL);
        card.setClickable(true);
        // The rows run to the rounded edge and go under it, not under a band above it.
        card.setPadding(dp(8), 0, dp(8), 0);
        card.setClipToOutline(true);
        GradientDrawable fill = new GradientDrawable();
        fill.setColor(Color.rgb(30, 33, 40));
        fill.setCornerRadius(dp(22));
        fill.setStroke(Math.max(1, dp(1)), Color.argb(46, 255, 255, 255));
        card.setBackground(fill);
        card.setElevation(dp(24));
        card.addView(popup.scroll, new LinearLayout.LayoutParams(-1, -2));
        shade = new FrameLayout(this);
        shade.setClickable(true);
        shade.setOnClickListener(view -> onBackPressed());
        // Clickable makes it focusable on its own: with the focus on it, the window's rows had none.
        shade.setFocusable(false);
        shade.setVisibility(View.GONE);
        shade.addView(card, new FrameLayout.LayoutParams(dp(POPUP_WIDTH), -2, Gravity.CENTER));

        backdropView = new ImageView(this);
        backdropView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backdropView.setBackground(MenuBackdrop.base(this));
        FrameLayout stage = new FrameLayout(this);
        stage.addView(backdropView, new FrameLayout.LayoutParams(-1, -1));
        stage.addView(root, new FrameLayout.LayoutParams(-1, -1));
        stage.addView(shade, new FrameLayout.LayoutParams(-1, -1));
        setContentView(stage);
        usePane(false);
        dressBackdrop();
        // A mouse click puts the window in touch mode, which drops the selection: take it back.
        root.getViewTreeObserver().addOnTouchModeChangeListener(touching -> {
            if (touching) rows.post(this::selectCurrent);
        });
        // From the control panel or an app card, Back returns there and not to the settings.
        String asked = getIntent().getStringExtra(SECTION);
        if (asked != null) {
            inMenu = false;
            toRemotes = REMOTES.equals(asked);
            if (APP.equals(asked)) {
                section = APPS_SECTION;
                String pkg = getIntent().getStringExtra(PACKAGE);
                enter(() -> showApp(pkg));
            } else {
                section = SCREEN.equals(asked) ? SCREEN_SECTION : NETWORK_SECTION;
                openSection();
            }
        } else {
            section = Math.min(leftSection, sections.length - 1);
            inMenu = leftAt == null || leftInMenu;
            openSection();
            if (!inMenu) goBack(leftAt);
        }
        paintMenu();
        if (inMenu) menu.post(this::selectCurrent);
    }

    /** A title, the head of a table and the rows under them, scrolling. */
    private Pane pane(int top) {
        Pane pane = new Pane();
        pane.heading = label("", 26);
        pane.heading.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        pane.heading.setSingleLine(true);
        pane.heading.setEllipsize(TextUtils.TruncateAt.END);
        pane.heading.setPadding(dp(14), dp(top), dp(14), 0);
        pane.scroll = new ScrollView(this) {
            /** Keeps the focused row's rounded ends inside the list. */
            @Override protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect) {
                Rect room = new Rect(rect);
                room.inset(0, -dp(ROW_ROOM));
                return super.computeScrollDeltaToGetChildRectOnScreen(room);
            }
        };
        pane.scroll.setVerticalScrollBarEnabled(false);
        pane.scroll.setClipChildren(false);
        pane.scroll.setFillViewport(true);
        pane.rows = new LinearLayout(this);
        pane.rows.setOrientation(LinearLayout.VERTICAL);
        pane.rows.setClipChildren(false);
        pane.rows.setClipToPadding(false);
        pane.scroll.addView(pane.rows);
        pane.tableHead = new LinearLayout(this);
        pane.tableHead.setPadding(dp(8 + TABLE_PADDING), dp(12), dp(8 + TABLE_PADDING), 0);
        pane.tableHead.addView(new View(this), new LinearLayout.LayoutParams(0, 0, 1));
        for (TextView name : addColumns(pane.tableHead, strings(TABLE_COLUMNS), 12)) name.setAlpha(0.6f);
        pane.tableHead.setVisibility(View.GONE);
        return pane;
    }

    /** The Home's own backdrop, blurred and darkened, usually made beforehand by the Home. Until it is ready, the plain one. */
    private void dressBackdrop() {
        Bitmap ready = MenuBackdrop.ready(this);
        if (ready != null) {
            backdropView.setImageBitmap(ready);
            return;
        }
        MenuBackdrop.make(this, made -> {
            if (!isDestroyed()) backdropView.setImageBitmap(made);
        });
    }

    private String sectionName(Section part) {
        return part.name == 0 ? Brand.NAME : getString(part.name);
    }

    private LinearLayout.LayoutParams menuRowSize() {
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-1, dp(MENU_ROW));
        size.bottomMargin = dp(4);
        return size;
    }

    /** Icon and name: a click chooses it with the mouse, the remote moves through dispatchKeyEvent. */
    private View menuRow(int index) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), 0, dp(12), 0);
        row.setDefaultFocusHighlightEnabled(false);
        row.setClickable(true);
        ImageView icon = new ImageView(this);
        icon.setImageResource(sections[index].icon);
        icon.setImageTintList(ColorStateList.valueOf(Color.WHITE));
        row.addView(icon, new LinearLayout.LayoutParams(dp(22), dp(22)));
        TextView name = label(sectionName(sections[index]), 17);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setPaddingRelative(dp(14), 0, 0, 0);
        row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        row.setTag(new Drawable[]{selectedFill(dp(10)), softGlass(dp(10))});
        row.setOnClickListener(view -> {
            // A window still open goes too: the click is a choice of the column.
            if (index != section || levels.size() > 1) chooseSection(index);
            if (!inMenu) goMenu();
        });
        return row;
    }

    private View menuRowAt(int index) {
        return menu.getChildAt(index < FIRE_SECTION ? index : index + 1);
    }

    /** Lit while the focus is here, only marked while it is on the page. */
    private void paintMenu() {
        for (int index = 0; index < sections.length; index++) {
            LinearLayout row = (LinearLayout) menuRowAt(index);
            Drawable[] fills = (Drawable[]) row.getTag();
            boolean chosen = index == section;
            boolean lit = chosen && inMenu;
            row.setBackground(lit ? fills[0] : chosen ? fills[1] : null);
            int ink = lit ? Fire.INK : Color.WHITE;
            ((ImageView) row.getChildAt(0)).setImageTintList(ColorStateList.valueOf(ink));
            TextView name = (TextView) row.getChildAt(1);
            name.setTextColor(ink);
            name.setTypeface(Typeface.create(chosen ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
            row.setFocusable(inMenu);
        }
    }

    /** Ends every level of the section on screen, with what each stops on leaving. */
    private void clearLevels() {
        while (!levels.isEmpty()) {
            Level gone = levels.pop();
            if (gone.leave != null) gone.leave.run();
        }
    }

    /** The column moves at once; the page follows when the moving stops. */
    private void chooseSection(int index) {
        section = index;
        clearLevels();
        paintMenu();
        Section part = sections[index];
        heading.setText(Brand.mark(this, sectionName(part)));
        about.setText(part.about == 0 ? "" : getString(part.about));
        about.setVisibility(part.about == 0 ? View.GONE : View.VISIBLE);
        usePane(false);
        rows.removeAllViews();
        tableHead.setVisibility(View.GONE);
        rows.removeCallbacks(showSection);
        rows.postDelayed(showSection, SECTION_PAUSE_MS);
    }

    private void openSection() {
        rows.removeCallbacks(showSection);
        clearLevels();
        Section part = sections[section];
        Level level = new Level(part.show);
        level.about = part.about == 0 ? null : getString(part.about);
        levels.push(level);
        part.show.run();
    }

    private void moveSection(int step) {
        int next = Math.max(0, Math.min(sections.length - 1, section + step));
        if (next == section) return;
        chooseSection(next);
        menuRowAt(next).requestFocus();
    }

    private void goMenu() {
        inMenu = true;
        paintMenu();
        menuRowAt(section).requestFocusFromTouch();
    }

    private void goPage() {
        if (levels.isEmpty()) openSection();
        inMenu = false;
        selectCurrent();
        paintMenu();
    }

    /** Up and down move through the column by hand: the focus must never slip onto the page. */
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        boolean rtl = getWindow().getDecorView().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        int towardMenu = rtl ? KeyEvent.KEYCODE_DPAD_RIGHT : KeyEvent.KEYCODE_DPAD_LEFT;
        int towardPage = rtl ? KeyEvent.KEYCODE_DPAD_LEFT : KeyEvent.KEYCODE_DPAD_RIGHT;
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        boolean ours = code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN || code == towardMenu
            || code == towardPage || code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER;
        if (!down && code == heldInMenu) {
            heldInMenu = 0;
            return true;
        }
        if (inMenu && ours) {
            if (!down) return true;
            heldInMenu = code;
            if (code == KeyEvent.KEYCODE_DPAD_UP) moveSection(-1);
            else if (code == KeyEvent.KEYCODE_DPAD_DOWN) moveSection(1);
            else if (code != towardMenu && event.getRepeatCount() == 0) goPage();
            return true;
        }
        if (!inMenu && code == towardMenu && rows.findFocus() != null) {
            if (down && event.getRepeatCount() == 0) onBackPressed();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    /** Down the same rows as last time, as long as they are still there and still open a level. */
    private void goBack(List<Spot> way) {
        if (way == null || way.isEmpty()) return;
        for (int step = 0; step < way.size() - 1; step++) {
            Spot spot = way.get(step);
            if (shownEntries == null || spot.focus >= shownEntries.size()) return;
            Entry entry = shownEntries.get(spot.focus);
            if (!entry.branch || !entry.title.equals(spot.opened)) return;
            int depth = levels.size();
            levels.peek().focus = spot.focus;
            levels.peek().opened = entry.title;
            entry.action.run();
            if (levels.size() != depth + 1) return;
        }
        levels.peek().focus = way.get(way.size() - 1).focus;
        refresh();
    }

    /** Leaving with Home keeps the way here; only the settings opened on their own first page. */
    @Override protected void onPause() {
        super.onPause();
        if (getIntent().getStringExtra(SECTION) != null || levels.isEmpty()) return;
        List<Spot> way = new ArrayList<>();
        java.util.Iterator<Level> down = levels.descendingIterator();
        while (down.hasNext()) {
            Level level = down.next();
            way.add(new Spot(level.focus, level.opened));
        }
        leftAt = way;
        leftSection = section;
        leftInMenu = inMenu;
    }

    /** Coming back from an app or a page elsewhere, what is listed here may have changed. The first time it is fresh. */
    @Override protected void onResume() {
        super.onResume();
        avStale = true;
        if (resumedOnce) {
            dressBackdrop();
            refresh();
        }
        resumedOnce = true;
    }

    private String[] strings(int[] ids) {
        String[] texts = new String[ids.length];
        for (int index = 0; index < ids.length; index++) texts[index] = getString(ids[index]);
        return texts;
    }

    private String[] colorFormatNames() {
        return new String[]{getString(R.string.settings_color_format_auto), "RGB", "YCbCr"};
    }

    private String[] surroundNames() {
        return new String[]{getString(R.string.settings_surround_best), getString(R.string.settings_surround_stereo),
            "Dolby Digital Plus", "Dolby Digital"};
    }

    private void enter(Runnable show) {
        Level level = new Level(show);
        if (!levels.isEmpty()) level.context = contextOf(levels.peek());
        levels.push(level);
        show.run();
    }

    /** «Tipo» alone says little: the heading over its row, or the page it is on, says of what. */
    private String contextOf(Level parent) {
        if (shownEntries != null) {
            for (int index = Math.min(parent.focus, shownEntries.size()) - 1; index >= 0; index--) {
                if (shownEntries.get(index).label) return shownEntries.get(index).title;
            }
        }
        return parent.title;
    }

    /** After a choice its window closes, and the page shows it. */
    private void closeChoices(Level level) {
        if (levels.peek() != level || levels.size() < 2) return;
        levels.pop();
        if (level.leave != null) level.leave.run();
        refresh();
    }

    /** Redraws the level after a choice, staying on the same row. Late answers can find no level left. */
    private void refresh() {
        if (!levels.isEmpty()) levels.peek().show.run();
    }

    /** For answers that arrive late: only if that level is still the one on screen. */
    private void refreshIf(Level level) {
        if (levels.peek() == level) refresh();
    }

    private void refreshLater(long delay) {
        Level level = levels.peek();
        rows.postDelayed(() -> refreshIf(level), delay);
    }

    private void backFrom(Level level) {
        if (levels.peek() == level) onBackPressed();
    }

    /** Back closes the choices, then goes up the levels, then to the column, then out. */
    @Override public void onBackPressed() {
        if (levels.isEmpty()) return;
        boolean asked = getIntent().getStringExtra(SECTION) != null;
        if (inMenu || levels.size() == 1 && asked) {
            leftAt = null;
            leftSection = 0;
            leftInMenu = true;
            finish();
            return;
        }
        if (levels.size() == 1) {
            goMenu();
            return;
        }
        Level left = levels.pop();
        if (left.leave != null) left.leave.run();
        refresh();
    }

    private void show(String title, List<Entry> entries) {
        show(title, entries, null);
    }

    /** The note stays under the rows, for what is true of all of them. */
    private void show(String title, List<Entry> entries, String note) {
        Level level = levels.peek();
        usePane(levels.size() > 1);
        small = levels.size() > 1;
        level.title = title;
        heading.setText(Brand.mark(this, title));
        if (small) {
            eyebrow.setText(level.context != null ? Brand.mark(this, level.context) : "");
            eyebrow.setVisibility(level.context != null ? View.VISIBLE : View.GONE);
        }
        if (levels.size() == 1) {
            about.setText(level.about != null ? level.about : "");
            about.setVisibility(level.about != null ? View.VISIBLE : View.GONE);
        }
        boolean table = entries.get(0).columns != null;
        tableHead.setVisibility(table ? View.VISIBLE : View.GONE);
        rows.setPadding(dp(8), dp(table ? 8 : ROWS_TOP), dp(8), dp(16));
        // The rows of the table must not run under its head.
        scroll.setClipChildren(table);
        rows.removeAllViews();
        for (Entry entry : entries) {
            if (entry.label) rows.addView(label(entry), new LinearLayout.LayoutParams(-1, -2));
            else rows.addView(table ? tableRow(entry) : row(entry), table ? tableRowSize(entry) : rowSize());
        }
        if (note != null) rows.addView(note(note), new LinearLayout.LayoutParams(-1, -2));
        shownEntries = entries;
        // A list of choices opens on the one in use, the user's request of 2026-11-09, not on the first.
        if (!level.drawn) {
            for (int index = 0; index < entries.size(); index++) {
                if (CHECK.equals(entries.get(index).value)) {
                    level.focus = index;
                    break;
                }
            }
        }
        level.drawn = true;
        if (level.focus == 0 || inMenu || firstRow(level.focus)) scroll.scrollTo(0, 0);
        if (!inMenu) selectRow(level.focus);
    }

    /** No row can have the focus above this one: up there are only the title and the headings. */
    private boolean firstRow(int index) {
        for (int row = 0; row < Math.min(index, rows.getChildCount()); row++) {
            if (rows.getChildAt(row).isFocusable()) return false;
        }
        return true;
    }

    /** The page for the first level of a section; the window over it for the levels below. */
    private void usePane(boolean up) {
        Pane target = up ? popup : page;
        heading = target.heading;
        tableHead = target.tableHead;
        scroll = target.scroll;
        rows = target.rows;
        shade.setVisibility(up ? View.VISIBLE : View.GONE);
        shade.setBackgroundColor(SHADE);
        page.rows.setDescendantFocusability(up ? ViewGroup.FOCUS_BLOCK_DESCENDANTS : ViewGroup.FOCUS_AFTER_DESCENDANTS);
    }

    private TextView note(String text) {
        TextView line = label(text, 16);
        line.setAlpha(0.75f);
        line.setPadding(dp(20), dp(8), dp(16), 0);
        return line;
    }

    /** A plain requestFocus fails after a touch, leaving no row selected. */
    private void selectRow(int index) {
        int count = rows.getChildCount();
        if (count == 0) return;
        int row = Math.min(Math.max(0, index), count - 1);
        while (row < count - 1 && !rows.getChildAt(row).isFocusable()) row++;
        rows.getChildAt(row).requestFocusFromTouch();
    }

    private void selectCurrent() {
        if (inMenu) menuRowAt(section).requestFocusFromTouch();
        else if (!levels.isEmpty() && rows.getChildCount() > 0) selectRow(levels.peek().focus);
    }

    /** The window enters touch mode as it gets the focus, which drops what was selected before. */
    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && getCurrentFocus() == null) selectCurrent();
    }

    private LinearLayout.LayoutParams rowSize() {
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-1, dp(small ? SMALL_ROW_HEIGHT : ROW_HEIGHT));
        size.bottomMargin = dp(small ? 6 : 8);
        return size;
    }


    private LinearLayout.LayoutParams tableRowSize(Entry entry) {
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-1, dp(TABLE_ROW));
        size.bottomMargin = dp(4);
        if (entry.opensGroup) size.topMargin = dp(10);
        return size;
    }

    private TextView[] addColumns(LinearLayout row, String[] values, int sp) {
        TextView[] cells = new TextView[values.length];
        for (int index = 0; index < values.length; index++) {
            cells[index] = label(values[index], sp);
            cells[index].setSingleLine(true);
            cells[index].setEllipsize(TextUtils.TruncateAt.END);
            row.addView(cells[index], new LinearLayout.LayoutParams(dp(TABLE_WIDTHS[index]), -2));
        }
        return cells;
    }

    private View tableRow(Entry entry) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(TABLE_PADDING), 0, dp(TABLE_PADDING), 0);
        TextView title = label(entry.title, 14);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView[] cells = addColumns(row, entry.columns, 13);
        for (TextView cell : cells) cell.setAlpha(0.8f);
        selectable(row, entry, focused -> {
            int text = focused ? Fire.INK : Color.WHITE;
            title.setTextColor(text);
            for (TextView cell : cells) cell.setTextColor(text);
        });
        return row;
    }

    /** What every row does on a click and with the focus; `paint` recolours what is inside it. */
    private void selectable(LinearLayout row, Entry entry, Consumer<Boolean> paint) {
        row.setFocusable(true);
        row.setClickable(true);
        row.setDefaultFocusHighlightEnabled(false);
        Drawable rest = softGlass(dp(12));
        row.setBackground(rest);
        row.setOnClickListener(v -> {
            // A click, with a mouse or on a preview of the screen, reaches the page without the
            // keys that would have left the column: Back would then still close everything.
            if (inMenu) {
                inMenu = false;
                paintMenu();
            }
            Level level = levels.peek();
            level.focus = rows.indexOfChild(v);
            level.opened = entry.title;
            entry.action.run();
            if (entry.picks && !isFinishing()) closeChoices(level);
        });
        row.setOnFocusChangeListener((view, focused) -> {
            paint.accept(focused);
            view.setBackground(focused ? focusFill() : rest);
            float scale = focused ? 1.02f : 1f;
            view.animate().scaleX(scale).scaleY(scale).setDuration(120).start();
            if (focused) {
                int index = rows.indexOfChild(view);
                levels.peek().focus = index;
                // The title scrolls with the rows: back on the first row it comes back into view.
                if (firstRow(index)) scroll.smoothScrollTo(0, 0);
            }
        });
    }

    private View label(Entry entry) {
        TextView label = label(entry.title, small ? 13 : LABEL_SP);
        label.setText(Brand.mark(this, entry.title));
        label.setAlpha(0.7f);
        label.setPadding(dp(small ? 16 : 20), dp(LABEL_ROOM), dp(16), dp(small ? 6 : 8));
        return label;
    }

    private View row(Entry entry) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(small ? 16 : 20), 0, dp(small ? 12 : 16), 0);
        TextView title = label(entry.title, small ? 17 : 20);
        if (entry.family != null) title.setTypeface(Typeface.create(entry.family, Typeface.NORMAL));
        title.setSingleLine(true);
        if (entry.mark != NO_MARK) {
            Drawable mark = entry.mark == 0 ? new ColorDrawable(Color.TRANSPARENT) : getDrawable(entry.mark).mutate();
            mark.setBounds(0, 0, dp(MARK_SIZE), dp(MARK_SIZE));
            title.setCompoundDrawablesRelative(mark, null, null, null);
            title.setCompoundDrawablePadding(dp(MARK_GAP));
            title.setCompoundDrawableTintList(ColorStateList.valueOf(Color.WHITE));
        }
        row.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        boolean onOff = YES.equals(entry.value) || NO.equals(entry.value);
        ToggleView toggle = onOff ? new ToggleView(this, YES.equals(entry.value)) : null;
        TextView value = label(onOff || entry.value == null ? "" : entry.value, small ? 15 : 18);
        value.setSingleLine(true);
        value.setEllipsize(TextUtils.TruncateAt.END);
        value.setMaxWidth(dp(small ? 170 : 230));
        value.setAlpha(0.75f);
        row.addView(value);
        ImageView lock = new ImageView(this);
        lock.setImageResource(R.drawable.ic_s_lock);
        lock.setAlpha(0.75f);
        LinearLayout.LayoutParams lockPlace = new LinearLayout.LayoutParams(dp(LOCK_SIZE), dp(LOCK_SIZE));
        lockPlace.setMarginStart(dp(8));
        if (entry.locked) row.addView(lock, lockPlace);
        if (onOff) row.addView(toggle, new LinearLayout.LayoutParams(dp(small ? 34 : 38), dp(small ? 19 : 21)));
        TextView chevron = label(entry.branch ? "›" : "", small ? 20 : 24);
        chevron.setPadding(dp(12), 0, 0, dp(3));
        chevron.setAlpha(0.75f);
        if (!onOff) row.addView(chevron);
        selectable(row, entry, focused -> {
            int text = focused ? Fire.INK : Color.WHITE;
            title.setTextColor(text);
            value.setTextColor(text);
            chevron.setTextColor(text);
            lock.setColorFilter(text);
            if (onOff) toggle.setRowFocused(focused);
            title.setCompoundDrawableTintList(ColorStateList.valueOf(text));
        });
        if (entry.off) {
            row.setFocusable(false);
            row.setClickable(false);
            row.setAlpha(0.4f);
        }
        return row;
    }

    /** Only one row has the focus, so they can all share this. */
    private Drawable focusFill() {
        if (focusFill == null) focusFill = selectedFill(dp(12));
        return focusFill;
    }

    private Drawable appIcon(String pkg) {
        try {
            return getPackageManager().getApplicationIcon(pkg);
        } catch (PackageManager.NameNotFoundException e) {
            return getDrawable(R.drawable.ic_apps);
        }
    }

    /** What Salotto cannot do stays in Fire OS: one row to get there. */
    private void showFire() {
        show(getString(R.string.settings_fire), Collections.singletonList(
            new Entry(getString(R.string.settings_fire_open), null, null, getString(R.string.settings_fire_text), false, () -> {
                TaskService.wantFireSettings();
                open(fireOsSettings());
            })));
    }

    private Entry info(String title, String value, String text) {
        return new Entry(title, value, null, text, false, () -> { });
    }

    private String notificationValue() {
        int count = NotificationCounter.count(this);
        return count > 0 ? String.valueOf(count) : null;
    }

    /** A word in the middle of a sentence: German keeps its capital. */
    private String inSentence(String word) {
        return "de".equals(Language.code(this)) ? word : word.toLowerCase(Locale.getDefault());
    }

    private void showLanguages() {
        show(getString(R.string.settings_language), choices(Language.NAMES, Arrays.asList(Language.CODES).indexOf(Language.code(this)),
            R.drawable.ic_s_language, getString(R.string.settings_language_text), index -> {
                Language.set(this, Language.CODES[index]);
                startActivity(new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            }));
    }



    private static final int[] HOME_NAMES = {R.string.settings_home_grid, R.string.settings_home_circle, R.string.settings_home_clean};
    private static final int[] HOME_SECTIONS = {R.string.settings_section_grid, R.string.settings_section_circle, R.string.settings_section_clean};

    private void showHome() {
        int style = Look.homeStyle(this);
        boolean circular = style == Look.HOME_CIRCLE;
        List<Entry> entries = new ArrayList<>();
        entries.add(new Entry(getString(R.string.settings_home), getString(HOME_NAMES[style]), null, null,
            true, () -> enter(this::showHomes)));
        entries.add(new Entry(getString(R.string.settings_backdrop), Look.backdropName(this, Look.backdrop(this)), null,
            getString(R.string.settings_backdrop_text), true, () -> open(new Intent(this, PictureGridActivity.class))));
        // Only for a collection that changes by itself: with one picture or the weather it means nothing.
        if (Look.timedBackdrop(this)) entries.add(paceEntry(false));
        entries.add(groupLabel(HOME_SECTIONS[style]));
        if (circular) {
            // The wheel has no rows, no dock and no names under the circles.
            entries.add(new Entry(getString(R.string.settings_wheel), wheelName(Look.wheel(this)), null,
                getString(R.string.settings_wheel_text), true, () -> enter(this::showWheel)));
            entries.add(new Entry(getString(R.string.settings_glow), glowNames()[Look.glow(this)], null,
                getString(R.string.settings_glow_text), true, () -> enter(this::showGlow)));
            boolean motion = Look.wheelMotion(this);
            Entry opening = new Entry(getString(R.string.settings_wheel_motion), motion ? YES : NO, null,
                getString(R.string.settings_wheel_motion_text), false, () -> {
                    Look.setWheelMotion(this, !motion);
                    refresh();
                });
            // Where it cannot be drawn smoothly: shown, always off, not to be chosen.
            opening.off = !Features.CIRCLE_ANIMATION;
            entries.add(opening);
            boolean blur = Look.wheelBlur(this);
            entries.add(new Entry(getString(R.string.settings_wheel_blur), blur ? YES : NO, null,
                getString(R.string.settings_wheel_blur_text), false, () -> {
                    Look.setWheelBlur(this, !blur);
                    refresh();
                }));
        } else {
            // The clean Home has twelve apps a row, always.
            if (style == Look.HOME_GRID) entries.add(new Entry(getString(R.string.settings_grid), appsPerRow(Look.columns(this)), null,
                getString(R.string.settings_grid_text), true, () -> enter(this::showColumns)));
            entries.add(new Entry(getString(R.string.settings_app_names), Look.nameModes(this)[Look.names(this)], null,
                getString(R.string.settings_app_names_text), true, () -> enter(this::showNames)));
            entries.add(new Entry(getString(style == Look.HOME_CLEAN ? R.string.settings_first_row : R.string.settings_dock),
                getString(Look.appsOnly(this) ? R.string.settings_dock_top : R.string.settings_dock_bottom), null,
                getString(R.string.settings_dock_text), true, () -> enter(this::showDock)));
            if (style == Look.HOME_CLEAN) {
                // Its own switch, off at first: the wheel's is not touched.
                boolean blur = Look.cleanBlur(this);
                entries.add(new Entry(getString(R.string.settings_wheel_blur), blur ? YES : NO, null, null, false, () -> {
                    Look.setCleanBlur(this, !blur);
                    refresh();
                }));
            }
        }
        boolean credits = Look.credits(this);
        entries.add(new Entry(getString(R.string.settings_credits), credits ? YES : NO, null, null, false, () -> {
            Look.setCredits(this, !credits);
            refresh();
        }));
        show(getString(R.string.settings_cat_home), entries);
    }

    private String wheelName(int count) {
        return getString(count == 5 ? R.string.settings_wheel_five : R.string.settings_wheel_three);
    }

    private void showHomes() {
        String[] names = new String[HOME_NAMES.length];
        for (int index = 0; index < names.length; index++) names[index] = getString(HOME_NAMES[index]);
        show(getString(R.string.settings_home), choices(names, Look.homeStyle(this), R.drawable.ic_s_grid, index -> null, index -> {
            Look.setHomeStyle(this, index);
            refresh();
        }));
    }

    private void showWheel() {
        int[] counts = Look.WHEEL_CHOICES;
        String[] names = new String[counts.length];
        for (int index = 0; index < counts.length; index++) names[index] = wheelName(counts[index]);
        show(getString(R.string.settings_wheel), choices(names, indexOf(counts, Look.wheel(this)), R.drawable.ic_apps,
            getString(R.string.settings_wheel_text), index -> {
                Look.setWheel(this, counts[index]);
                refresh();
            }));
    }

    private String[] glowNames() {
        return new String[]{getString(R.string.settings_glow_off), getString(R.string.settings_glow_normal), getString(R.string.settings_glow_wide)};
    }

    private void showGlow() {
        show(getString(R.string.settings_glow), choices(glowNames(), Look.glow(this), R.drawable.ic_apps,
            getString(R.string.settings_glow_text), index -> {
                Look.setGlow(this, index);
                refresh();
            }));
    }

    /** The rows of a list with one choice marked: each runs `choose` with its own index. */
    private List<Entry> choices(String[] names, int chosen, int picture, IntFunction<String> text, IntConsumer choose) {
        List<Entry> entries = new ArrayList<>();
        for (int index = 0; index < names.length; index++) {
            int choice = index;
            Entry entry = new Entry(names[index], index == chosen ? CHECK : null, getDrawable(picture),
                text.apply(index), false, () -> choose.accept(choice));
            entry.picks = true;
            entries.add(entry);
        }
        return entries;
    }

    private List<Entry> choices(String[] names, int chosen, int picture, String text, IntConsumer choose) {
        return choices(names, chosen, picture, index -> text, choose);
    }

    private String minutes(int count) { return getResources().getQuantityString(R.plurals.settings_minutes, count, count); }

    private String appsPerRow(int count) { return getResources().getQuantityString(R.plurals.settings_apps_per_row, count, count); }

    private String[] minuteNames(int[] minutes) {
        String[] names = new String[minutes.length];
        for (int index = 0; index < minutes.length; index++) names[index] = minutes(minutes[index]);
        return names;
    }

    private int indexOf(int[] values, int value) {
        for (int index = 0; index < values.length; index++) {
            if (values[index] == value) return index;
        }
        return -1;
    }

    private void showDock() {
        // The clean Home has no dock to see: its first row of apps, at the bottom or at the top.
        boolean clean = Look.clean(this);
        String[] names = clean ? new String[]{getString(R.string.settings_dock_bottom), getString(R.string.settings_dock_top)}
            : new String[]{getString(R.string.settings_dock_bottom_choice), getString(R.string.settings_dock_top_choice)};
        show(getString(clean ? R.string.settings_first_row : R.string.settings_dock), choices(names, Look.appsOnly(this) ? 1 : 0, R.drawable.ic_s_grid,
            getString(R.string.settings_dock_text), index -> {
                Look.setAppsOnly(this, index == 1);
                refresh();
            }));
    }

    private void showNames() {
        show(getString(R.string.settings_app_names), choices(Look.nameModes(this), Look.names(this), R.drawable.ic_apps,
            getString(R.string.settings_app_names_text), index -> {
                Look.setNames(this, index);
                refresh();
            }));
    }

    /** Every installed app has its row, with its space and the ways to close or remove it. */
    private void showApps() {
        List<Entry> entries = new ArrayList<>(Arrays.asList(
            new Entry(getString(R.string.settings_all_apps), null, null,
                getString(R.string.settings_all_apps_text),
                false, () -> open(new Intent(this, AppsActivity.class))),
            new Entry(getString(R.string.settings_task_manager), null, null,
                getString(R.string.settings_task_manager_text),
                false, () -> open(new Intent(this, TasksActivity.class))),
            new Entry(getString(R.string.settings_held_keys), TaskService.keysOn(this) ? YES : NO, null,
                getString(R.string.settings_held_keys_text),
                false, () -> changeSystem(() -> TaskService.setKeys(this, !TaskService.keysOn(this))))));
        entries.add(groupLabel(R.string.settings_manage_apps));
        for (HomeApps.App app : HomeApps.installed(this)) {
            Entry entry = new Entry(app.label, null, null, getString(R.string.settings_manage_app_text),
                true, () -> enter(() -> showApp(app.pkg)));
            entry.pkg = app.pkg;
            entries.add(entry);
        }
        show(getString(R.string.settings_apps), entries);
    }

    private String appVersion(String pkg) {
        try {
            String version = getPackageManager().getPackageInfo(pkg, 0).versionName;
            return version != null ? version : "—";
        } catch (PackageManager.NameNotFoundException e) {
            return "—";
        }
    }

    /** Needs the usage permission that the task manager already has. */
    private StorageStats appSize(String pkg) {
        try {
            return getSystemService(StorageStatsManager.class)
                .queryStatsForPackage(StorageManager.UUID_DEFAULT, pkg, Process.myUserHandle());
        } catch (IOException | PackageManager.NameNotFoundException | SecurityException e) {
            return null;
        }
    }

    /** The sizes are slow to read, so the page opens first and they join it. */
    private void showApp(String pkg) {
        Level level = levels.peek();
        listApp(pkg, level, null);
        Background.run(() -> {
            StorageStats size = appSize(pkg);
            runOnUiThread(() -> {
                if (size != null && levels.peek() == level) listApp(pkg, level, size);
            });
        });
    }

    private String sizeText(StorageStats size, ToLongFunction<StorageStats> part) {
        return size != null ? Formatter.formatShortFileSize(this, part.applyAsLong(size)) : "—";
    }

    private void listApp(String pkg, Level level, StorageStats size) {
        Drawable picture = appIcon(pkg);
        List<Entry> entries = new ArrayList<>();
        String text = getString(R.string.settings_app_info_text);
        entries.add(new Entry(getString(R.string.settings_app_version), appVersion(pkg), picture, text, false, () -> { }));
        entries.add(new Entry(getString(R.string.settings_apps), sizeText(size, StorageStats::getAppBytes), picture, text, false, () -> { }));
        entries.add(new Entry(getString(R.string.settings_app_data), sizeText(size, StorageStats::getDataBytes), picture, text, false, () -> { }));
        entries.add(new Entry(getString(R.string.settings_app_cache), sizeText(size, StorageStats::getCacheBytes), picture, text, false, () -> { }));
        if (launchIntent(pkg) != null) {
            entries.add(new Entry(getString(R.string.settings_app_open), null, picture, getString(R.string.settings_app_open_text), false, () -> openPackage(pkg)));
        }
        entries.add(new Entry(getString(R.string.settings_app_close), null, picture, getString(R.string.settings_app_close_text), false,
            () -> TaskService.stop(pkg, stopped -> say(stopped ? getString(R.string.settings_app_closed) : getString(R.string.settings_app_not_closed)))));
        if (!Features.CLEAR_DATA_HERE) entries.add(new Entry(getString(R.string.settings_clear_data), null, picture, getString(R.string.settings_clear_data_text),
            false, () -> open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg)))));
        else entries.add(new Entry(getString(R.string.settings_clear_data), null, picture, getString(R.string.settings_clear_data_text),
            false, () -> confirm(getString(R.string.settings_clear_data_question, HomeApps.label(this, pkg)),
                getString(R.string.settings_clear_data_warning), getString(R.string.settings_clear),
                () -> shell("pm clear " + pkg, output -> {
                    sayLong(output != null && output.contains("Success") ? getString(R.string.settings_data_cleared)
                        : getString(R.string.settings_data_not_cleared));
                    refreshIf(level);
                }))));
        entries.add(new Entry(getString(R.string.settings_uninstall), null, picture, getString(R.string.settings_uninstall_text), false, () -> {
            uninstalling = level;
            uninstall(pkg);
        }));
        show(HomeApps.label(this, pkg), entries);
    }

    @Override protected void appRemoved() { backFrom(uninstalling); }

    /** Picture and sound come from Fire OS, slowly: the page opens at once and they join it. */
    private void showScreen() {
        Level level = levels.peek();
        readAv(level);
        boolean cec = Settings.Global.getInt(getContentResolver(), CEC_ENABLED, 1) == 1;
        boolean tvOff = Settings.Global.getInt(getContentResolver(), CEC_TV_OFF, 0) == 1;
        List<Entry> entries = new ArrayList<>();
        entries.add(groupLabel(R.string.settings_video));
        if (picture == null) entries.add(reading());
        else entries.addAll(videoRows(level));
        entries.add(groupLabel(R.string.settings_audio));
        if (sound == null) entries.add(reading());
        else entries.addAll(audioRows(level));
        entries.add(groupLabel(R.string.settings_section_tv));
        entries.add(new Entry(getString(R.string.settings_cec), cec ? YES : NO, null,
            getString(R.string.settings_cec_text), false,
            () -> changeSystem(() -> Settings.Global.putInt(getContentResolver(), CEC_ENABLED, cec ? 0 : 1))));
        entries.add(new Entry(getString(R.string.settings_tv_off), tvOff ? YES : NO, null,
            getString(R.string.settings_tv_off_text), false,
            () -> changeSystem(() -> Settings.Global.putInt(getContentResolver(), CEC_TV_OFF, tvOff ? 0 : 1))));
        entries.add(new Entry(getString(R.string.settings_sleep), minutes(sleepMinutes()), null,
            getString(R.string.settings_sleep_text), true, () -> enter(this::showSleepTimes)));
        entries.add(new Entry(getString(R.string.settings_standards), null, null, getString(R.string.settings_standards_text),
            true, () -> enter(this::showStandards)));
        show(getString(R.string.settings_screen), entries);
    }

    private Entry reading() {
        return info(getString(R.string.settings_reading), null, getString(R.string.settings_a_moment));
    }

    /** Read again after every change and every return to the page; until then the last values stay. */
    private void readAv(Level level) {
        if (!avStale || avReading) return;
        avReading = true;
        avStale = false;
        Background.run(() -> {
            String[] seen = {fire.read(FireSettings.PICTURE, RESOLUTION), fire.read(FireSettings.PICTURE, RANGE),
                fire.read(FireSettings.PICTURE, COLOR_FORMAT), fire.read(FireSettings.PICTURE, COLOR_DEPTH)};
            String[] heard = {fire.read(FireSettings.AUDIO, LEVELER), fire.read(FireSettings.AUDIO, DIALOGUE)};
            runOnUiThread(() -> {
                avReading = false;
                if (isDestroyed()) return;
                picture = seen;
                sound = heard;
                refreshIf(level);
            });
        });
    }

    /** Read again at each visit, keeping the row: what it shows changes with the TV and the choices. */
    private void showStandards() {
        Level level = levels.peek();
        int focus = level.focus;
        showWaiting(getString(R.string.settings_standards));
        Background.run(() -> {
            AvStandards.Chosen chosen = new AvStandards.Chosen();
            chosen.resolution = nameOf(RESOLUTIONS, strings(RESOLUTION_NAMES), fire.read(FireSettings.PICTURE, RESOLUTION));
            chosen.range = nameOf(RANGES, strings(RANGE_NAMES), fire.read(FireSettings.PICTURE, RANGE));
            chosen.colorFormat = nameOf(COLOR_FORMATS, colorFormatNames(), fire.read(FireSettings.PICTURE, COLOR_FORMAT));
            chosen.colorDepth = nameOf(COLOR_DEPTHS, strings(COLOR_DEPTH_NAMES), fire.read(FireSettings.PICTURE, COLOR_DEPTH));
            chosen.surround = nameOf(SURROUNDS, surroundNames(), surroundNow());
            List<AvStandards.Line> lines = AvStandards.read(this, chosen);
            runOnUiThread(() -> {
                if (levels.peek() != level) return;
                Drawable picture = getDrawable(R.drawable.ic_image);
                List<Entry> entries = new ArrayList<>();
                for (AvStandards.Line line : lines) {
                    Entry entry = new Entry(line.name, null, picture, line.text, false, () -> { });
                    entry.columns = new String[]{line.stick, line.tv, line.now};
                    entry.opensGroup = line.opensGroup;
                    entries.add(entry);
                }
                level.focus = focus;
                show(getString(R.string.settings_standards), entries);
            });
        });
    }

    private String nameOf(String[] actions, String[] names, String value) {
        int index = Arrays.asList(actions).indexOf(value);
        return index >= 0 ? names[index] : "—";
    }

    /** The only row of this page takes the focus: the place in the real page has to survive it. */
    private void showWaiting(String title) {
        Level level = levels.peek();
        if (level.drawn) return;
        int focus = level.focus;
        show(title, Arrays.asList(new Entry(getString(R.string.settings_reading), null, getDrawable(R.drawable.ic_image),
            getString(R.string.settings_a_moment), false, () -> { })));
        level.focus = focus;
        level.drawn = false;
    }

    private List<Entry> videoRows(Level level) {
        String resolution = picture[0];
        String range = picture[1];
        String format = picture[2];
        String depth = picture[3];
        List<Entry> entries = new ArrayList<>();
        // A page of its own: a wrong one can leave the screen black, and the page counts down the way back.
        entries.add(new Entry(getString(R.string.settings_resolution), nameOf(RESOLUTIONS, strings(RESOLUTION_NAMES), resolution), null,
            resolutionText(), true, () -> enter(() -> showResolutions(resolution))));
        entries.add(new Entry("HDR", nameOf(RANGES, strings(RANGE_NAMES), range), null,
            getString(R.string.settings_hdr_text), true,
            () -> enter(() -> showChoices("HDR", FireSettings.PICTURE, RANGE, RANGES, strings(RANGE_NAMES), range))));
        entries.add(new Entry(getString(R.string.settings_color_format), nameOf(COLOR_FORMATS, colorFormatNames(), format), null,
            getString(R.string.settings_color_format_text), true,
            () -> enter(() -> showChoices(getString(R.string.settings_color_format), FireSettings.PICTURE, COLOR_FORMAT, COLOR_FORMATS, colorFormatNames(), format))));
        if (depth != null && !depth.isEmpty()) {
            entries.add(new Entry(getString(R.string.settings_color_depth), nameOf(COLOR_DEPTHS, strings(COLOR_DEPTH_NAMES), depth), null,
                getString(R.string.settings_color_depth_text), true,
                () -> enter(() -> showChoices(getString(R.string.settings_color_depth), FireSettings.PICTURE, COLOR_DEPTH, COLOR_DEPTHS, strings(COLOR_DEPTH_NAMES), depth))));
        }
        entries.add(new Entry(getString(R.string.settings_video_quality), getString(VIDEO_QUALITY_NAMES[videoQuality()]), null,
            getString(R.string.settings_video_quality_text), true, () -> enter(this::showVideoQuality)));
        return entries;
    }

    private List<Entry> audioRows(Level level) {
        boolean even = "true".equals(sound[0]);
        boolean clear = "true".equals(sound[1]);
        String surround = surroundNow();
        return Arrays.asList(
            new Entry(getString(R.string.settings_surround), nameOf(SURROUNDS, surroundNames(), surround), null,
                getString(R.string.settings_surround_text), true,
                () -> enter(() -> showChoices(getString(R.string.settings_surround), FireSettings.AUDIO, SURROUND, SURROUNDS, surroundNames(), surround))),
            new Entry(getString(R.string.settings_dialogue), clear ? YES : NO, null,
                getString(R.string.settings_dialogue_text), false,
                () -> writeFire(FireSettings.AUDIO, DIALOGUE, clear ? "false" : "true", () -> refreshIf(level))),
            new Entry(getString(R.string.settings_leveler), even ? YES : NO, null,
                getString(R.string.settings_leveler_text), false,
                () -> writeFire(FireSettings.AUDIO, LEVELER, even ? "false" : "true", () -> refreshIf(level))));
    }

    /** The service names this value differently when read; the system setting is unambiguous. */
    private String surroundNow() {
        int mode = Settings.Global.getInt(getContentResolver(), SURROUND_MODE, 0);
        return SURROUNDS[Math.max(0, Math.min(SURROUNDS.length - 1, mode))];
    }

    private void writeFire(String provider, String id, String action, Runnable done) {
        Background.run(() -> {
            boolean written = fire.write(provider, id, action);
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                avStale = true;
                if (!written) sayLong(getString(R.string.settings_fire_refused));
                done.run();
            });
        });
    }

    /** The choices close as soon as one is pressed; the page shows it once Fire OS has taken it. */
    private void showChoices(String title, String provider, String id, String[] actions, String[] names, String current) {
        Level level = levels.peek();
        show(title, choices(names, Arrays.asList(actions).indexOf(current), R.drawable.ic_image,
            getString(R.string.settings_fire_may_refuse),
            index -> writeFire(provider, id, actions[index], () -> {
                if (levels.peek() == level) closeChoices(level);
                else refresh();
            })));
    }

    private String resolutionText() {
        return getResources().getQuantityString(R.plurals.settings_resolution_text, RESOLUTION_WAIT_S, RESOLUTION_WAIT_S);
    }

    private String resolutionCountdown(int seconds) {
        return getResources().getQuantityString(R.plurals.settings_resolution_countdown, seconds, seconds);
    }

    private void showResolutions(String current) {
        List<String> values = new ArrayList<>(Arrays.asList(RESOLUTIONS));
        List<String> names = new ArrayList<>(Arrays.asList(strings(RESOLUTION_NAMES)));
        // Without 4K the two 4K lines, after Auto, would only give a black screen.
        if (!Features.RESOLUTION_4K) {
            values.subList(1, 3).clear();
            names.subList(1, 3).clear();
        }
        List<Entry> entries = choices(names.toArray(new String[0]), values.indexOf(current), R.drawable.ic_image,
            resolutionText(), index -> {
                if (!values.get(index).equals(current)) changeResolution(current, values.get(index));
            });
        // The window stays while the question counts down: it closes when the answer comes.
        for (Entry entry : entries) entry.picks = false;
        show(getString(R.string.settings_resolution), entries);
    }

    /** Fire OS does not go back by itself, and with no picture nobody could reach the setting. */
    private void changeResolution(String old, String next) {
        String previous = old != null ? old : RESOLUTIONS[0];
        writeFire(FireSettings.PICTURE, RESOLUTION, next, () -> {
            int[] left = {RESOLUTION_WAIT_S};
            AlertDialog dialog = dialog()
                .setTitle(getString(R.string.settings_resolution_question)).setMessage(resolutionCountdown(left[0]))
                .setCancelable(false)
                .setNegativeButton(getString(R.string.settings_resolution_go_back), null)
                .setPositiveButton(getString(R.string.settings_resolution_keep), null)
                .create();
            // Not on a view of this page: with a black screen the person presses Home, and the
            // way back must still run after the page is gone.
            Handler clock = new Handler(Looper.getMainLooper());
            Runnable[] count = new Runnable[1];
            Runnable goBack = () -> {
                clock.removeCallbacks(count[0]);
                if (!isDestroyed()) dialog.dismiss();
                restoreResolution(previous);
            };
            count[0] = () -> {
                if (--left[0] <= 0) {
                    goBack.run();
                    return;
                }
                if (!isDestroyed()) dialog.setMessage(resolutionCountdown(left[0]));
                clock.postDelayed(count[0], 1000);
            };
            dialog.setOnShowListener(shown -> {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
                    clock.removeCallbacks(count[0]);
                    dialog.dismiss();
                    onBackPressed();
                });
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(button -> goBack.run());
            });
            dialog.show();
            clock.postDelayed(count[0], 1000);
        });
    }

    /** With its own connection: this page's closes when the page goes. */
    private void restoreResolution(String previous) {
        FireSettings own = new FireSettings(this);
        Background.run(() -> {
            own.write(FireSettings.PICTURE, RESOLUTION, previous);
            own.close();
            runOnUiThread(() -> {
                avStale = true;
                if (!isDestroyed()) onBackPressed();
            });
        });
    }

    private int videoQuality() {
        int quality = Settings.Global.getInt(getContentResolver(), VIDEO_QUALITY, 2);
        return quality >= 0 && quality < VIDEO_QUALITY_NAMES.length ? quality : 2;
    }

    /** Best first, which is the opposite of the order of the values. */
    private void showVideoQuality() {
        int last = VIDEO_QUALITY_NAMES.length - 1;
        String[] names = new String[last + 1];
        for (int index = 0; index <= last; index++) names[index] = getString(VIDEO_QUALITY_NAMES[last - index]);
        show(getString(R.string.settings_video_quality), choices(names, last - videoQuality(), R.drawable.ic_image,
            getString(R.string.settings_video_quality_text),
            index -> changeSystem(() -> Settings.Global.putInt(getContentResolver(), VIDEO_QUALITY, last - index))));
    }

    @SuppressLint("MissingPermission")
    private List<Entry> remoteRows() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        List<Entry> entries = new ArrayList<>();
        if (adapter == null || !adapter.isEnabled()) {
            entries.add(info(getString(R.string.settings_bluetooth_off), null, getString(R.string.settings_bluetooth_off_text)));
            return entries;
        }
        for (BluetoothDevice device : adapter.getBondedDevices()) {
            String name = device.getName() != null ? device.getName() : device.getAddress();
            entries.add(new Entry(name, getString(Fire.connected(device) ? R.string.settings_connected : R.string.settings_paired), null,
                getString(R.string.settings_press_to_forget_device), false, () -> forgetBluetooth(device, name)));
        }
        entries.add(new Entry(getString(R.string.settings_find_devices), null, null,
            getString(R.string.settings_find_devices_text),
            true, () -> enter(this::showBluetoothSearch)));
        return entries;
    }

    /** Hidden in the SDK, though open to apps. The remote in use may be the one being removed. */
    private void forgetBluetooth(BluetoothDevice device, String name) {
        confirm(getString(R.string.settings_forget_question, name), getString(R.string.settings_forget_device_warning),
            getString(R.string.settings_forget), () -> {
                try {
                    device.getClass().getMethod("removeBond").invoke(device);
                } catch (ReflectiveOperationException e) {
                    sayLong(getString(R.string.settings_fire_forbids));
                }
                refreshLater(WIFI_SCAN_MS);
            });
    }

    /** Discovery keeps the radio busy: it ends with the page that asked for it. */
    @SuppressLint("MissingPermission")
    private void stopSearch() {
        if (bluetoothListener != null) unregisterReceiver(bluetoothListener);
        bluetoothListener = null;
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter != null) adapter.cancelDiscovery();
    }

    @SuppressLint("MissingPermission")
    private void showBluetoothSearch() {
        Level level = levels.peek();
        Drawable picture = getDrawable(R.drawable.ic_bluetooth);
        Map<String, BluetoothDevice> found = new LinkedHashMap<>();
        Runnable draw = () -> {
            if (levels.peek() != level) return;
            List<Entry> entries = new ArrayList<>();
            for (BluetoothDevice device : found.values()) {
                String name = device.getName();
                entries.add(new Entry(name, null, picture, getString(R.string.settings_press_to_pair), false, () -> {
                    BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
                    sayLong(device.createBond() ? getString(R.string.settings_pairing, name) : getString(R.string.settings_pairing_failed));
                }));
            }
            if (entries.isEmpty()) {
                entries.add(new Entry(getString(R.string.first_searching), null, picture, getString(R.string.settings_pairing_mode), false, () -> { }));
            }
            show(getString(R.string.settings_find_devices), entries);
        };
        draw.run();
        BroadcastReceiver listener = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device == null || device.getName() == null || device.getBondState() == BluetoothDevice.BOND_BONDED) return;
                if (found.put(device.getAddress(), device) == null) draw.run();
            }
        };
        stopSearch();
        level.leave = this::stopSearch;
        bluetoothListener = listener;
        registerReceiver(listener, new IntentFilter(BluetoothDevice.ACTION_FOUND));
        withLocation(() -> {
            if (levels.peek() == level) BluetoothAdapter.getDefaultAdapter().startDiscovery();
        });
    }

    /** Scans need the location permission, which the shell can grant. */
    private void withLocation(Runnable run) {
        if (checkSelfPermission(LOCATION) == PackageManager.PERMISSION_GRANTED) run.run();
        else shell("pm grant " + getPackageName() + " " + LOCATION, output -> run.run());
    }

    @Override protected void onDestroy() {
        stopSearch();
        fire.close();
        super.onDestroy();
    }

    private void changeCounter(boolean on) {
        Level level = levels.peek();
        String component = new ComponentName(this, NotificationCounter.class).flattenToString();
        shell("cmd notification " + (on ? "disallow_listener " : "allow_listener ") + component, output -> {
            if (output == null) {
                sayLong(getString(R.string.settings_fire_refused));
                refreshIf(level);
            } else {
                waitCounter(on, level, COUNTER_TRIES);
            }
        });
    }

    /** Android saves the choice a moment after the command ends. */
    private void waitCounter(boolean before, Level level, int tries) {
        if (tries == 0 || NotificationCounter.allowed(this) != before) refreshIf(level);
        else rows.postDelayed(() -> waitCounter(before, level, tries - 1), COUNTER_MS);
    }

    private List<Entry> privacyRows() {
        boolean adsLimited = Settings.Secure.getInt(getContentResolver(), ADS_LIMITED, 1) == 1;
        boolean metrics = Settings.Secure.getInt(getContentResolver(), USAGE_METRICS, 0) == 1;
        boolean counted = NotificationCounter.allowed(this);
        List<Entry> entries = new ArrayList<>(Arrays.asList(
            new Entry(getString(R.string.settings_limit_ads), adsLimited ? YES : NO, null,
                getString(R.string.settings_limit_ads_text), false,
                () -> changeSystem(() -> Settings.Secure.putInt(getContentResolver(), ADS_LIMITED, adsLimited ? 0 : 1))),
            new Entry(getString(R.string.settings_usage_data), metrics ? YES : NO, null,
                getString(R.string.settings_usage_data_text), false,
                () -> changeSystem(() -> Settings.Secure.putInt(getContentResolver(), USAGE_METRICS, metrics ? 0 : 1)))));
        if (Features.NOTIFICATION_COUNT) entries.add(0, new Entry(getString(R.string.settings_notification_count), counted ? YES : NO, null,
            getString(R.string.settings_notification_count_text), false,
            () -> changeCounter(counted)));
        return entries;
    }

    private void showSystem() {
        boolean clicks = Settings.System.getInt(getContentResolver(), Settings.System.SOUND_EFFECTS_ENABLED, 0) == 1;
        boolean quick = Settings.Global.getFloat(getContentResolver(), Settings.Global.WINDOW_ANIMATION_SCALE, 1) < 1;
        List<Entry> entries = new ArrayList<>(Arrays.asList(
            new Entry(getString(R.string.settings_language), Language.name(this), null,
                getString(R.string.settings_language_text), true, () -> enter(this::showLanguages)),
            new Entry(getString(R.string.panel_notifications), notificationValue(), null,
                getString(R.string.settings_notifications_text), false, () -> {
                    NotificationCounter.seen(this);
                    open(Fire.notifications());
                }),
            new Entry(getString(R.string.settings_sounds), clicks ? YES : NO, null,
                getString(R.string.settings_sounds_text), false,
                () -> changeSystem(() -> Settings.System.putInt(getContentResolver(),
                    Settings.System.SOUND_EFFECTS_ENABLED, clicks ? 0 : 1))),
            new Entry(getString(R.string.settings_quick_animations), quick ? YES : NO, null,
                getString(R.string.settings_quick_animations_text), false,
                () -> changeSystem(() -> {
                    float scale = quick ? 1 : QUICK_SCALE;
                    Settings.Global.putFloat(getContentResolver(), Settings.Global.WINDOW_ANIMATION_SCALE, scale);
                    Settings.Global.putFloat(getContentResolver(), Settings.Global.TRANSITION_ANIMATION_SCALE, scale);
                }))));
        entries.add(groupLabel(R.string.settings_privacy));
        entries.addAll(privacyRows());
        entries.add(groupLabel(R.string.settings_section_device));
        entries.addAll(deviceRows());
        show(getString(R.string.settings_cat_system), entries);
    }

    private int sleepMinutes() {
        return Settings.Secure.getInt(getContentResolver(), SLEEP_TIMEOUT, SLEEP_DEFAULT) / MINUTE;
    }

    private void showSleepTimes() {
        show(getString(R.string.settings_sleep), choices(minuteNames(SLEEP_MINUTES), indexOf(SLEEP_MINUTES, sleepMinutes()),
            R.drawable.ic_image, getString(R.string.settings_sleep_text),
            index -> changeSystem(() -> Settings.Secure.putInt(getContentResolver(), SLEEP_TIMEOUT,
                SLEEP_MINUTES[index] * MINUTE))));
    }

    private List<Entry> infoRows(List<String[]> rows, Drawable picture, String text) {
        List<Entry> entries = new ArrayList<>();
        for (String[] row : rows) entries.add(new Entry(row[0], row[1], picture, text, false, () -> { }));
        return entries;
    }

    /** With a cable Wi-Fi stays on but idle, and neither its switch nor its networks would mean anything. */
    private List<Entry> wiredRows() {
        List<Entry> entries = infoRows(DeviceInfo.network(this), null, getString(R.string.settings_connection_state));
        entries.add(info("Wi-Fi", getString(R.string.settings_wifi_on_cable), getString(R.string.settings_wifi_on_cable_text)));
        return entries;
    }

    private List<Entry> networkRows() {
        if (DeviceInfo.wired(this)) return wiredRows();
        boolean on = getSystemService(WifiManager.class).isWifiEnabled();
        List<Entry> entries = new ArrayList<>();
        if (!Features.WIFI_NETWORKS) {
            entries.add(new Entry(getString(R.string.settings_wifi_networks), null, null, getString(R.string.settings_wifi_on_fire),
                false, () -> open(new Intent(Settings.ACTION_WIFI_SETTINGS))));
        } else {
            entries.add(new Entry("Wi-Fi", on ? YES : NO, null, getString(R.string.settings_wifi_text), false,
                () -> setWifi(!on)));
        }
        if (on) {
            if (Features.WIFI_NETWORKS) entries.add(new Entry(getString(R.string.settings_wifi_networks), null, null, getString(R.string.settings_wifi_networks_text),
                true, () -> enter(this::showWifi)));
            entries.addAll(infoRows(DeviceInfo.network(this), null, getString(R.string.settings_connection_state)));
        }
        return entries;
    }

    private void showNetwork() {
        Level level = levels.peek();
        List<Entry> entries = new ArrayList<>();
        entries.add(groupLabel(R.string.settings_network));
        entries.addAll(networkRows());
        entries.add(groupLabel(R.string.settings_remotes));
        int remotes = entries.size();
        entries.addAll(remoteRows());
        if (toRemotes) {
            toRemotes = false;
            level.focus = remotes;
        }
        show(getString(R.string.settings_cat_network), entries);
    }

    /**
     * Turning Wi-Fi off also ends the connection to the shell, and Android no longer lets an app
     * turn it back on. So a script stays behind to do it when asked, and wifi_on goes back to 1:
     * a restart must never leave the Stick without network.
     */
    private void setWifi(boolean on) {
        File ask = Updater.shared(this, WIFI_ON);
        if (on) {
            Updater.write(ask, "on");
            sayLong(getString(R.string.settings_wifi_turning_on));
            refreshLater(WIFI_JOIN_MS);
            return;
        }
        String path = ask.getPath();
        String script = "rm -f " + path + "; cmd wifi set-wifi-enabled disabled; sleep 2; settings put global wifi_on 1; "
            + "while [ ! -e " + path + " ]; do sleep 1; done; rm -f " + path + "; cmd wifi set-wifi-enabled enabled";
        confirm(getString(R.string.settings_wifi_off_question), getString(R.string.settings_wifi_off_warning),
            getString(R.string.settings_wifi_off), () -> {
                shell("setsid nohup sh -c " + quoted(script) + " > /dev/null 2>&1 &", output -> { });
                refreshLater(WIFI_SCAN_MS * 2);
            });
    }

    private void showWifi() {
        Level level = levels.peek();
        show(getString(R.string.settings_wifi_networks), Arrays.asList(new Entry(getString(R.string.settings_wifi_scanning), null, getDrawable(R.drawable.ic_wifi),
            getString(R.string.settings_wifi_scanning_text), false, () -> { })));
        withLocation(() -> {
            getSystemService(WifiManager.class).startScan();
            rows.postDelayed(() -> {
                if (levels.peek() == level) showWifiResults();
            }, WIFI_SCAN_MS);
        });
    }

    private String wifiStrength(int rssi) {
        int level = WifiManager.calculateSignalLevel(rssi, WIFI_STRENGTHS.length);
        return getString(WIFI_STRENGTHS[level]);
    }

    /** Without the location permission the list simply comes back empty. */
    @SuppressLint("MissingPermission")
    private void showWifiResults() {
        WifiManager wifi = getSystemService(WifiManager.class);
        Map<String, ScanResult> best = new LinkedHashMap<>();
        List<ScanResult> found = new ArrayList<>(wifi.getScanResults());
        Collections.sort(found, (a, b) -> b.level - a.level);
        for (ScanResult network : found) {
            if (!network.SSID.isEmpty() && !best.containsKey(network.SSID)) best.put(network.SSID, network);
        }
        String current = wifi.getConnectionInfo().getSSID().replace("\"", "");
        Drawable picture = getDrawable(R.drawable.ic_wifi);
        List<Entry> entries = new ArrayList<>();
        for (ScanResult network : best.values()) {
            String name = network.SSID;
            boolean connected = name.equals(current);
            String security = wifiSecurity(network);
            Entry entry = new Entry(name, connected ? getString(R.string.settings_wifi_connected) : wifiStrength(network.level), picture,
                (connected ? getString(R.string.settings_wifi_press_to_forget) : getString(R.string.settings_wifi_press_to_join))
                    + "\n\n" + wifiDetails(network, connected), false,
                () -> {
                    if (connected) forgetWifi(name);
                    else joinWifi(name, security);
                });
            entry.locked = !security.equals("open");
            entries.add(entry);
        }
        if (entries.isEmpty()) {
            entries.add(new Entry(getString(R.string.settings_wifi_none), null, picture,
                getString(R.string.settings_wifi_none_text), false, () -> { }));
        }
        show(getString(R.string.settings_wifi_networks), entries);
    }

    /** Under the icon of the row with the focus. */
    private String wifiDetails(ScanResult network, boolean connected) {
        String security = wifiSecurity(network);
        String details = getString(R.string.settings_wifi_details, inSentence(wifiStrength(network.level)),
            getString(network.frequency >= WIFI_5GHZ ? R.string.settings_wifi_5ghz : R.string.settings_wifi_2ghz),
            security.equals("open") ? getString(R.string.settings_wifi_open) : security.toUpperCase(Locale.ROOT));
        return connected ? details + "\n" + DeviceInfo.wifiLine(this) : details;
    }

    private String wifiSecurity(ScanResult network) {
        if (network.capabilities.contains("SAE")) return "wpa3";
        return network.capabilities.contains("PSK") ? "wpa2" : "open";
    }

    /** Quotes a value for the shell: names and passwords can hold any character. */
    private String quoted(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private void joinWifi(String name, String security) {
        if (security.equals("open")) {
            connectWifi(name, security, "");
            return;
        }
        askText(getString(R.string.settings_wifi_password, name), true, getString(R.string.settings_wifi_join),
            password -> connectWifi(name, security, password));
    }

    private void connectWifi(String name, String security, String password) {
        sayLong(getString(R.string.settings_wifi_joining, name));
        String secret = password.isEmpty() ? "" : " " + quoted(password);
        Level level = levels.peek();
        shell("cmd wifi connect-network " + quoted(name) + " " + security + secret,
            output -> {
                if (output == null) sayLong(getString(R.string.settings_fire_refused));
                rows.postDelayed(() -> refreshIf(level), WIFI_JOIN_MS);
            });
    }

    private void forgetWifi(String name) {
        Level level = levels.peek();
        confirm(getString(R.string.settings_forget_question, name), getString(R.string.settings_wifi_forget_warning), getString(R.string.settings_forget),
            () -> shell("cmd wifi list-networks", output -> {
                Matcher match = Pattern.compile("(?m)^(\\d+)\\s+" + Pattern.quote(name) + "\\s").matcher(output == null ? "" : output);
                if (match.find()) shell("cmd wifi forget-network " + match.group(1), done -> refreshIf(level));
                else say(getString(R.string.settings_wifi_not_found));
            }));
    }

    private List<Entry> deviceRows() {
        List<Entry> entries = infoRows(DeviceInfo.system(this), null, getString(R.string.settings_device_info_text));
        if (Features.USB_SETTINGS) entries.add(new Entry(getString(R.string.settings_usb), null, null, getString(R.string.settings_usb_text),
            true, () -> enter(this::showUsb)));
        entries.add(new Entry(getString(R.string.settings_standby), null, null, getString(R.string.settings_standby_text), false,
            TaskService::standby));
        entries.add(new Entry(getString(R.string.settings_restart), null, null, getString(R.string.settings_restart_text), false,
            () -> confirm(getString(R.string.settings_restart_question), getString(R.string.settings_restart_warning), getString(R.string.settings_restart),
                TaskService::restart)));
        return entries;
    }

    private void showSalotto() {
        boolean beta = Updater.beta(this);
        boolean doubleHome = HomeCover.doubleHome;
        List<Entry> entries = new ArrayList<>();
        entries.add(new Entry(getString(R.string.settings_about), null, null,
            getString(R.string.settings_about_text), true, () -> open(new Intent(this, AboutActivity.class))));
        entries.add(groupLabel(R.string.settings_update));
        entries.add(new Entry(getString(R.string.settings_update_check), BuildVersion.name(this), null,
            getString(R.string.settings_update_text), true, () -> open(new Intent(this, UpdateActivity.class))));
        entries.add(new Entry(getString(R.string.settings_beta), beta ? YES : NO, null,
            getString(R.string.settings_beta_text), false, () -> {
                Updater.setBeta(this, !beta);
                refresh();
            }));
        entries.add(groupLabel(R.string.settings_backup));
        entries.add(new Entry(getString(R.string.settings_save_choices), null, null,
            getString(R.string.settings_save_choices_text), false, () -> {
                try {
                    Backup.save(this);
                    say(getString(R.string.settings_choices_saved));
                    refresh();
                } catch (IOException | JSONException e) {
                    sayLong(getString(R.string.settings_save_failed));
                }
            }));
        entries.add(new Entry(getString(R.string.settings_restore_choices), Backup.exists(this) ? null : getString(R.string.settings_no_backup), null,
            getString(R.string.settings_restore_choices_text), false, () -> {
                try {
                    Backup.restore(this);
                    say(getString(R.string.settings_choices_restored));
                    dressBackdrop();
                    refresh();
                } catch (IOException | JSONException e) {
                    sayLong(getString(R.string.settings_nothing_to_restore));
                }
            }));
        entries.add(groupLabel(R.string.settings_tools));
        entries.add(new Entry(getString(R.string.settings_tour), null, null,
            getString(R.string.settings_tour_text), false,
            () -> open(new Intent(this, FirstRunActivity.class))));
        entries.add(new Entry("AirPlay", null, null, getString(R.string.settings_airplay_text),
            false, () -> openPackage(Fire.AIRPLAY)));
        entries.add(new Entry(getString(R.string.settings_double_home), doubleHome ? YES : NO, null,
            getString(R.string.settings_double_home_text), false, () -> {
                TaskService.rememberDoubleHome(this, !doubleHome);
                refresh();
            }));
        show(Brand.NAME, entries);
    }

    /** "public:8,1 mounted 1234-ABCD": one line for each disk. */
    private void showUsb() {
        Level level = levels.peek();
        Drawable picture = getDrawable(R.drawable.ic_settings);
        show(getString(R.string.settings_usb), Arrays.asList(new Entry(getString(R.string.settings_usb_checking), null, picture,
            getString(R.string.settings_usb_checking_text), false, () -> { })));
        shell("sm list-volumes public", output -> {
            if (levels.peek() != level) return;
            List<Entry> entries = new ArrayList<>();
            for (String line : (output == null ? "" : output).split("\n")) {
                String[] parts = line.trim().split(" ");
                if (parts.length < 2 || !parts[0].startsWith("public:")) continue;
                boolean mounted = parts[1].equals("mounted");
                String name = parts.length > 2 && !parts[2].equals("null") ? parts[2] : parts[0];
                entries.add(new Entry(name, mounted ? getString(R.string.settings_connected) : getString(R.string.settings_not_connected), picture,
                    mounted ? getString(R.string.settings_usb_press_to_unmount) : getString(R.string.settings_usb_can_remove), false, () -> {
                        if (mounted) shell("sm unmount " + parts[0], done -> refreshIf(level));
                    }));
            }
            if (entries.isEmpty()) entries.add(new Entry(getString(R.string.settings_usb_none), null, picture, getString(R.string.settings_usb_none_text), false, () -> { }));
            show(getString(R.string.settings_usb), entries);
        });
    }

    /** ACTION_SETTINGS passes through Amazon's home activity, which brings Salotto back here. */
    private Intent fireOsSettings() {
        return Fire.settings();
    }

    private Entry groupLabel(int name) {
        return groupLabel(getString(name));
    }

    private Entry groupLabel(String name) {
        Entry entry = new Entry(name, null, null, "", false, () -> { });
        entry.label = true;
        return entry;
    }

    /** How long each picture stays: the backdrop's and the screensaver's are two different times. */
    private Entry paceEntry(boolean forSaver) {
        int now = forSaver ? Look.saverMinutes(this) : Look.abstractMinutes(this);
        String text = getString(forSaver ? R.string.settings_saver_minutes_text : R.string.settings_abstract_time_home_text);
        return new Entry(getString(R.string.settings_abstract_time), minutes(now), null, text, true,
            () -> enter(() -> show(getString(R.string.settings_abstract_time), choices(minuteNames(Look.ABSTRACT_MINUTES),
                indexOf(Look.ABSTRACT_MINUTES, now), R.drawable.ic_timer, text, index -> {
                    if (forSaver) Look.setSaverMinutes(this, Look.ABSTRACT_MINUTES[index]);
                    else Look.setAbstractMinutes(this, Look.ABSTRACT_MINUTES[index]);
                    refresh();
                }))));
    }

    private void showClock() {
        boolean visible = Look.clock(this);
        boolean enabled = Weather.enabled(this);
        String place = Weather.place(this);
        show(getString(R.string.settings_cat_clock), Arrays.asList(
            groupLabel(R.string.settings_time_group),
            new Entry(getString(R.string.settings_show_on_home), visible ? YES : NO, null,
                getString(R.string.settings_clock_show_text), false, () -> {
                    Look.setClock(this, !visible);
                    refresh();
                }),
            new Entry(getString(R.string.settings_font), Look.fontNames(this)[Look.font(this)], null,
                getString(R.string.settings_font_text), true, () -> enter(this::showFonts)),
            new Entry(getString(R.string.settings_size), Look.sizeNames(this)[Look.size(this)], null,
                getString(R.string.settings_size_text), true, () -> enter(this::showSizes)),
            new Entry(getString(R.string.settings_hours), Look.hours24(this) ? getString(R.string.settings_hours_24) : getString(R.string.settings_hours_12), null,
                getString(R.string.settings_hours_text), false, () -> {
                    Look.setHours24(this, !Look.hours24(this));
                    refresh();
                }),
            new Entry(getString(R.string.settings_seconds), Look.seconds(this) ? YES : NO, null,
                getString(R.string.settings_seconds_text), false, () -> {
                    Look.setSeconds(this, !Look.seconds(this));
                    refresh();
                }),
            groupLabel(R.string.settings_date),
            new Entry(getString(R.string.settings_show_on_home), Look.date(this) ? YES : NO, null,
                getString(R.string.settings_date_show_text), false, () -> {
                    Look.setDate(this, !Look.date(this));
                    refresh();
                }),
            new Entry(getString(R.string.settings_font), Look.fontNames(this)[Look.dateFont(this)], null,
                getString(R.string.settings_date_font_text), true, () -> enter(this::showDateFonts)),
            new Entry(getString(R.string.settings_size), Look.sizeNames(this)[Look.dateSize(this)], null,
                getString(R.string.settings_date_size_text), true, () -> enter(this::showDateSizes)),
            new Entry(getString(R.string.settings_hours), dateFormatName(Look.dateNumeric(this)), null,
                getString(R.string.settings_date_format_text), true, () -> enter(this::showDateFormats)),
            groupLabel(R.string.settings_weather),
            new Entry(getString(R.string.settings_show_on_home), enabled ? YES : NO, null,
                getString(R.string.settings_weather_show_text), false, () -> {
                    Weather.setEnabled(this, !enabled);
                    refresh();
                }),
            new Entry(getString(R.string.settings_place), place != null ? place : getString(R.string.settings_place_unset), null,
                getString(R.string.settings_place_text), true, this::askPlace),
            new Entry(getString(R.string.settings_units), Weather.fahrenheit(this) ? "°F" : "°C", null,
                getString(R.string.settings_units_text), false, () -> {
                    Weather.setFahrenheit(this, !Weather.fahrenheit(this));
                    refresh();
                }),
            new Entry(getString(R.string.settings_font), weatherFontName(), null,
                getString(R.string.settings_weather_font_text), true, () -> enter(this::showWeatherFonts)),
            new Entry(getString(R.string.settings_size), weatherSizeName(), null,
                getString(R.string.settings_weather_size_text), true, () -> enter(this::showWeatherSizes))));
    }

    private String dateFormatName(boolean numeric) {
        return getString(numeric ? R.string.settings_date_numeric : R.string.settings_date_long);
    }

    /** Today, written as the language writes it, long or in numbers. */
    private String today(boolean numeric) {
        return android.text.format.DateFormat.format(android.text.format.DateFormat.getBestDateTimePattern(
            java.util.Locale.getDefault(), numeric ? "ddMMyyyy" : "EEEEdMMMM"), System.currentTimeMillis()).toString();
    }

    private void showDateFonts() {
        List<Entry> entries = choices(Look.fontNames(this), Look.dateFont(this), R.drawable.ic_clock,
            getString(R.string.settings_date_font_text), index -> {
                Look.setDateFont(this, index);
                refresh();
            });
        for (int index = 0; index < entries.size(); index++) entries.get(index).family = Look.FONT_FAMILIES[index];
        show(getString(R.string.settings_font), entries);
    }

    private void showDateSizes() {
        show(getString(R.string.settings_size), choices(Look.sizeNames(this), Look.dateSize(this), R.drawable.ic_clock,
            getString(R.string.settings_date_size_text), index -> {
                Look.setDateSize(this, index);
                refresh();
            }));
    }

    private void showDateFormats() {
        String[] names = {dateFormatName(false), dateFormatName(true)};
        show(getString(R.string.settings_hours), choices(names, Look.dateNumeric(this) ? 1 : 0, R.drawable.ic_clock,
            index -> today(index == 1), index -> {
                Look.setDateNumeric(this, index == 1);
                refresh();
            }));
    }

    /** «Like the date» first, then the same choices as the date's. */
    private String[] likeDate(String[] names) {
        String[] all = new String[names.length + 1];
        all[0] = getString(R.string.settings_like_date);
        System.arraycopy(names, 0, all, 1, names.length);
        return all;
    }

    private String weatherFontName() {
        int own = Look.weatherFont(this);
        return own == Look.LIKE_DATE ? getString(R.string.settings_like_date) : Look.fontNames(this)[own];
    }

    private String weatherSizeName() {
        int own = Look.weatherSize(this);
        return own == Look.LIKE_DATE ? getString(R.string.settings_like_date) : Look.sizeNames(this)[own];
    }

    private void showWeatherFonts() {
        List<Entry> entries = choices(likeDate(Look.fontNames(this)), Look.weatherFont(this) + 1, R.drawable.ic_cloud,
            getString(R.string.settings_weather_font_text), index -> {
                Look.setWeatherFont(this, index - 1);
                refresh();
            });
        entries.get(0).family = Look.FONT_FAMILIES[Look.dateFont(this)];
        for (int index = 1; index < entries.size(); index++) entries.get(index).family = Look.FONT_FAMILIES[index - 1];
        show(getString(R.string.settings_font), entries);
    }

    private void showWeatherSizes() {
        show(getString(R.string.settings_size), choices(likeDate(Look.sizeNames(this)), Look.weatherSize(this) + 1, R.drawable.ic_cloud,
            getString(R.string.settings_weather_size_text), index -> {
                Look.setWeatherSize(this, index - 1);
                refresh();
            }));
    }

    private void askPlace() {
        askText(getString(R.string.first_city_hint), false, getString(R.string.first_search), name -> searchPlace(name.trim()));
    }

    private void searchPlace(String name) {
        if (name.isEmpty()) return;
        Background.run(() -> {
            try {
                List<Weather.Place> places = Weather.search(this, name);
                runOnUiThread(() -> {
                    if (places.isEmpty()) say(getString(R.string.settings_no_place));
                    else enter(() -> showPlaces(places));
                });
            } catch (IOException | JSONException e) {
                runOnUiThread(() -> say(getString(R.string.settings_no_connection)));
            }
        });
    }

    private void showPlaces(List<Weather.Place> places) {
        List<Entry> entries = new ArrayList<>();
        for (Weather.Place place : places) {
            entries.add(new Entry(place.name, place.detail, getDrawable(R.drawable.ic_cloud),
                getString(R.string.settings_choose_place_text), false, () -> {
                    Weather.setPlace(this, place);
                    onBackPressed();
                }));
        }
        show(getString(R.string.first_place_title), entries);
    }

    private boolean saverOn() {
        return Settings.Secure.getInt(getContentResolver(), SAVER_ENABLED, 1) == 1;
    }

    private int saverMinutes() {
        return Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, SAVER_DEFAULT) / MINUTE;
    }

    private String saverValue() {
        return saverOn() ? minutes(saverMinutes()) : getString(R.string.settings_off);
    }

    /** Needs WRITE_SECURE_SETTINGS, which only adb can grant: without it the values stay readable. */
    private void changeSystem(Runnable change) {
        try {
            change.run();
            refresh();
        } catch (SecurityException e) {
            sayLong(getString(R.string.settings_no_permission));
        }
    }

    private boolean ownSaver() { return Look.ownSaver(this); }

    /** Who runs it first: with Salotto its own rows, with Fire OS only the way to Fire OS's menu. */
    private void showSaver() {
        boolean on = saverOn();
        boolean own = ownSaver();
        String picture = own ? Look.saverPicture(this) : null;
        List<Entry> entries = new ArrayList<>(Arrays.asList(
            new Entry(getString(R.string.settings_saver_active), on ? YES : NO, null,
                getString(R.string.settings_saver_active_text), false,
                () -> changeSystem(() -> Settings.Secure.putInt(getContentResolver(), SAVER_ENABLED, on ? 0 : 1))),
            new Entry(getString(R.string.settings_saver_managed), own ? Brand.NAME : "Fire OS", null,
                getString(R.string.settings_saver_managed_text), true, () -> enter(this::showSaverManagers)),
            new Entry(getString(R.string.settings_saver_after), minutes(saverMinutes()), null, saverWait(), true,
                () -> enter(this::showSaverTimes))));
        if (own) {
            entries.add(new Entry(getString(R.string.grid_saver_title),
                picture != null ? Look.backdropName(this, new File(picture).getName()) : ownSaverName(), null,
                getString(R.string.settings_saver_pictures_text), true,
                () -> open(new Intent(this, PictureGridActivity.class).putExtra(PictureGridActivity.SAVER, true))));
            if (picture == null) entries.add(paceEntry(true));
        } else {
            entries.add(new Entry(getString(R.string.settings_saver_choose), null, null,
                getString(R.string.settings_saver_choose_text), true, () -> open(Fire.saverMenu())));
        }
        if (own || Features.FIRE_SAVER_NOW) entries.add(new Entry(getString(R.string.settings_saver_start), null, null, getString(R.string.settings_saver_start_text), false,
            () -> open(own ? new Intent(this, SaverActivity.class) : Fire.screensaver(this))));
        if (own) {
            entries.add(groupLabel(R.string.settings_section_saver_shows));
            entries.add(saverShows(R.string.settings_saver_time, R.drawable.ic_clock, Look.saverTime(this), Look::setSaverTime));
            entries.add(saverShows(R.string.settings_saver_date, R.drawable.ic_clock, Look.saverDate(this), Look::setSaverDate));
            entries.add(saverShows(R.string.settings_saver_weather, R.drawable.ic_cloud, Look.saverWeather(this), Look::setSaverWeather));
            entries.add(new Entry(getString(R.string.settings_saver_place), saverPlaces()[Look.saverPlace(this)], null,
                getString(R.string.settings_saver_place_text), true, () -> enter(this::showSaverPlaces)));
        }
        show(getString(R.string.settings_saver), entries);
    }

    /** Salotto with its own pictures, or the screensaver Fire OS decides. */
    private void showSaverManagers() {
        String[] names = {Brand.NAME, "Fire OS"};
        show(getString(R.string.settings_saver_managed), choices(names, ownSaver() ? 0 : 1, R.drawable.ic_image,
            getString(R.string.settings_saver_managed_text), index -> chooseSaver(index == 0
                ? new ComponentName(this, SaverService.class).flattenToShortString() : Fire.AMAZON_SAVER)));
    }

    private String[] saverPlaces() {
        return getResources().getStringArray(R.array.settings_saver_places);
    }

    private void showSaverPlaces() {
        Level level = levels.peek();
        show(getString(R.string.settings_saver_place), choices(saverPlaces(), Look.saverPlace(this), R.drawable.ic_clock,
            getString(R.string.settings_saver_place_text), index -> {
                Look.setSaverPlace(this, index);
                backFrom(level);
            }));
    }

    /** Only one screensaver runs at a time: choosing ours is what turns off Fire OS's. */
    private Entry saverShows(int name, int icon, boolean on, BiConsumer<Context, Boolean> set) {
        return new Entry(getString(name), on ? YES : NO, getDrawable(icon), getString(R.string.settings_saver_shows_text), false, () -> {
            set.accept(this, !on);
            refresh();
        });
    }

    /** The photos of Personale, by the folder's full path: offered, like among the backdrops, from two photos on. */
    private Saver personalSaver() {
        return new Saver(getString(R.string.settings_backdrop_personal), Look.personalFolder(this).getPath());
    }

    private String ownSaverName() {
        for (Saver saver : SAVERS) {
            if (saver.folder.equals(Look.saverFolder(this))) return saver.name;
        }
        if (Look.saverFolder(this).startsWith("/")) return personalSaver().name;
        return Brand.NAME;
    }

    private void chooseSaver(String name) {
        changeSystem(() -> Settings.Secure.putString(getContentResolver(), SAVER_CHOSEN, name));
    }

    /** The Stick goes to sleep at its own time, whatever the screensaver is doing. */
    private boolean saverInTime(int minutes) {
        int sleep = sleepMinutes();
        return sleep <= 0 || minutes < sleep;
    }

    private String saverWait() {
        if (saverInTime(saverMinutes())) return getString(R.string.settings_saver_wait_text);
        int sleep = sleepMinutes();
        return getResources().getQuantityString(R.plurals.settings_saver_too_late, sleep, sleep);
    }

    private void showSaverTimes() {
        List<Entry> entries = choices(minuteNames(SAVER_MINUTES), indexOf(SAVER_MINUTES, saverMinutes()), R.drawable.ic_image,
            saverWait(),
            index -> changeSystem(() -> Settings.System.putInt(getContentResolver(),
                Settings.System.SCREEN_OFF_TIMEOUT, SAVER_MINUTES[index] * MINUTE)));
        boolean limited = false;
        for (int index = 0; index < entries.size(); index++) {
            if (saverInTime(SAVER_MINUTES[index])) continue;
            entries.get(index).off = true;
            limited = true;
        }
        int sleep = sleepMinutes();
        show(getString(R.string.settings_saver_after), entries, limited
            ? getResources().getQuantityString(R.plurals.settings_saver_limited, sleep, sleep) : null);
    }

    private void showFonts() {
        List<Entry> entries = choices(Look.fontNames(this), Look.font(this), R.drawable.ic_clock,
            getString(R.string.settings_font_text), index -> {
                Look.setFont(this, index);
                refresh();
            });
        for (int index = 0; index < entries.size(); index++) entries.get(index).family = Look.FONT_FAMILIES[index];
        show(getString(R.string.settings_font), entries);
    }

    private void showSizes() {
        show(getString(R.string.settings_size), choices(Look.sizeNames(this), Look.size(this), R.drawable.ic_clock,
            getString(R.string.settings_size_text), index -> {
                Look.setSize(this, index);
                refresh();
            }));
    }

    private void showColumns() {
        int[] columns = Look.COLUMN_CHOICES;
        String[] names = new String[columns.length];
        for (int index = 0; index < columns.length; index++) names[index] = appsPerRow(columns[index]);
        show(getString(R.string.settings_grid), choices(names, indexOf(columns, Look.columns(this)), R.drawable.ic_apps,
            index -> getString(columns[index] == 6 ? R.string.settings_grid_six : R.string.settings_grid_five), index -> {
                Look.setColumns(this, columns[index]);
                refresh();
            }));
    }
}
