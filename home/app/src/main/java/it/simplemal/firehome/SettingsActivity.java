package it.simplemal.firehome;

import android.graphics.drawable.ColorDrawable;
import android.content.res.ColorStateList;
import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
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
import android.graphics.Color;
import android.graphics.Outline;
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
import android.view.View;
import android.view.ViewOutlineProvider;
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

/** A tree of settings like tvOS: picture and explanation on the left, the current level on the right. */
public final class SettingsActivity extends CardActivity {
    private static final String CHECK = "✓";
    private static final int COLUMN_TOP = 44;
    private static final int PICTURE_SIZE = 120;
    private static final int NO_MARK = -1;
    private static final int LABEL_SP = 15;
    private static final int LABEL_ROOM = 6;
    private static final int MARK_SIZE = 20;
    private static final int MARK_GAP = 12;
    private static final int PREVIEW_SP = 12;
    private static final int PREVIEW_MARGIN = 24;
    /** As wide as a preview of the screen needs: an icon stays square in the middle. */
    private static final int PICTURE_WIDTH = 213;
    private static final int LOCK_SIZE = 16;
    private static final int WIFI_5GHZ = 5000;
    /** Its middle sits just above the middle of the 540-tall screen. */
    private static final int PICTURE_TOP = 170;
    private static final int EXPLANATION_TOP = PICTURE_TOP + PICTURE_SIZE + 22;
    private static final int EXPLANATION_SP = 16;
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
    private static final int[] TABLE_WIDTHS = {96, 96, 112};
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
        /** The backdrop this row would give: the page turns into it while the row has the focus. */
        Drawable preview;
        /** The pictures of this row: they become the page's background while it has the focus. */
        String mosaic;
        /** A heading for the rows below it: not a row. */
        boolean label;
        /** Beside the title: what kind of backdrop it is. Zero keeps the room empty, to stay lined up. */
        int mark = NO_MARK;

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
        Level(Runnable show) { this.show = show; }
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
    private LinearLayout root;
    private TextView heading;
    private ImageView picture;
    private PictureMosaic mosaic;
    private TextView previewNote;
    private boolean previewing;
    private TextView explanation;
    private LinearLayout rows;
    private ScrollView scroll;
    private LinearLayout tableHead;
    private BroadcastReceiver bluetoothListener;
    private FireSettings fire;
    private Level uninstalling;
    private boolean resumedOnce;
    private Drawable focusFill;

    private FrameLayout.LayoutParams placed(int width, int height, int top) {
        FrameLayout.LayoutParams place = new FrameLayout.LayoutParams(width, height,
            Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        place.topMargin = dp(top);
        return place;
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_LOCALE);
        fire = new FireSettings(this);
        // Before any row shows a photo's name.
        Look.tidyPersonal(this);
        DeviceInfo.prepare();
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Look.plainBackdrop());
        root.setPadding(dp(MARGIN), 0, dp(MARGIN), 0);
        previewNote = label(getString(R.string.first_preview), PREVIEW_SP);
        previewNote.setAlpha(0.7f);
        previewNote.setVisibility(View.GONE);

        LinearLayout panes = new LinearLayout(this);
        root.addView(panes, new LinearLayout.LayoutParams(-1, 0, 1));

        // Fixed places: moving from one row to the next changes the words, never the layout.
        FrameLayout left = new FrameLayout(this);
        left.setPadding(dp(16), 0, dp(40), 0);
        heading = label("", 28);
        heading.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        heading.setGravity(Gravity.CENTER);
        heading.setSingleLine(true);
        heading.setEllipsize(TextUtils.TruncateAt.END);
        left.addView(heading, placed(-1, -2, COLUMN_TOP));
        picture = new ImageView(this);
        picture.setScaleType(ImageView.ScaleType.FIT_CENTER);
        picture.setClipToOutline(true);
        picture.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(12));
            }
        });
        left.addView(picture, placed(dp(PICTURE_WIDTH), dp(PICTURE_SIZE), PICTURE_TOP));
        mosaic = new PictureMosaic(this);
        explanation = label("", EXPLANATION_SP);
        explanation.setGravity(Gravity.CENTER_HORIZONTAL);
        explanation.setLineSpacing(dp(3), 1f);
        left.addView(explanation, placed(-1, -2, EXPLANATION_TOP));
        panes.addView(left, new LinearLayout.LayoutParams(0, -1, 45));

        scroll = new ScrollView(this) {
            /** Keeps the focused row's rounded ends inside the list. */
            @Override protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect) {
                Rect room = new Rect(rect);
                room.inset(0, -dp(ROW_ROOM));
                return super.computeScrollDeltaToGetChildRectOnScreen(room);
            }
        };
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipChildren(false);
        scroll.setFillViewport(true);
        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setClipChildren(false);
        rows.setClipToPadding(false);
        rows.setPadding(dp(8), dp(COLUMN_TOP), dp(8), dp(16));
        scroll.addView(rows);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        tableHead = new LinearLayout(this);
        tableHead.setPadding(dp(8 + TABLE_PADDING), dp(COLUMN_TOP - 20), dp(8 + TABLE_PADDING), 0);
        tableHead.addView(new View(this), new LinearLayout.LayoutParams(0, 0, 1));
        for (TextView name : addColumns(tableHead, strings(TABLE_COLUMNS), 12)) name.setAlpha(0.6f);
        list.addView(tableHead, new LinearLayout.LayoutParams(-1, -2));
        list.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        panes.addView(list, new LinearLayout.LayoutParams(0, -1, 55));
        FrameLayout stage = new FrameLayout(this);
        stage.addView(mosaic, new FrameLayout.LayoutParams(-1, -1));
        stage.addView(root, new FrameLayout.LayoutParams(-1, -1));
        setContentView(stage);
        FrameLayout.LayoutParams corner = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
        corner.bottomMargin = dp(PREVIEW_MARGIN);
        corner.setMarginStart(dp(PREVIEW_MARGIN));
        addContentView(previewNote, corner);
        // A mouse click puts the window in touch mode, which drops the selection: take it back.
        root.getViewTreeObserver().addOnTouchModeChangeListener(touching -> {
            if (touching) rows.post(this::selectCurrent);
        });
        // From the control panel or an app card, Back returns there and not to the settings.
        String section = getIntent().getStringExtra(SECTION);
        if (NETWORK.equals(section)) enter(this::showNetwork);
        else if (REMOTES.equals(section)) enter(this::showRemotes);
        else if (SCREEN.equals(section)) enter(this::showScreen);
        else if (APP.equals(section)) enter(() -> showApp(getIntent().getStringExtra(PACKAGE)));
        else {
            enter(this::showRoot);
            goBack(leftAt);
        }
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
    }

    /** Coming back from an app or a page elsewhere, what is listed here may have changed. The first time it is fresh. */
    @Override protected void onResume() {
        super.onResume();
        if (resumedOnce) refresh();
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
        levels.push(new Level(show));
        show.run();
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

    @Override public void onBackPressed() {
        if (levels.isEmpty()) return;
        Level left = levels.pop();
        if (left.leave != null) left.leave.run();
        if (levels.isEmpty()) {
            leftAt = null;
            finish();
        } else refresh();
    }

    private void show(String title, List<Entry> entries) {
        show(title, entries, null);
    }

    /** The note stays under the rows, for what is true of all of them. */
    private void show(String title, List<Entry> entries, String note) {
        heading.setText(Brand.mark(this, title));
        boolean table = entries.get(0).columns != null;
        tableHead.setVisibility(table ? View.VISIBLE : View.GONE);
        rows.setPadding(dp(8), dp(table ? 8 : COLUMN_TOP), dp(8), dp(16));
        // The rows of the table must not run under its head.
        scroll.setClipChildren(table);
        explanation.setTextSize(table ? 14 : EXPLANATION_SP);
        rows.removeAllViews();
        shownEntries = entries;
        for (Entry entry : entries) {
            if (entry.label) rows.addView(label(entry));
            else rows.addView(table ? tableRow(entry) : row(entry), table ? tableRowSize(entry) : rowSize());
        }
        if (note != null) {
            TextView line = label(note, 16);
            line.setAlpha(0.75f);
            line.setPadding(dp(20), dp(8), dp(16), 0);
            rows.addView(line, new LinearLayout.LayoutParams(-1, -2));
        }
        int focus = levels.peek().focus;
        levels.peek().drawn = true;
        if (focus == 0) scroll.scrollTo(0, 0);
        selectRow(focus);
    }

    /** A plain requestFocus fails after a touch, leaving no row selected and the old text on the left. */
    private void selectRow(int index) {
        int row = Math.min(index, rows.getChildCount() - 1);
        while (row < rows.getChildCount() - 1 && !rows.getChildAt(row).isFocusable()) row++;
        rows.getChildAt(row).requestFocusFromTouch();
    }

    private void selectCurrent() {
        if (!levels.isEmpty() && rows.getChildCount() > 0) selectRow(levels.peek().focus);
    }

    /** The window enters touch mode as it gets the focus, which drops what was selected before. */
    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && getCurrentFocus() == null) selectCurrent();
    }

    private LinearLayout.LayoutParams rowSize() {
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-1, dp(48));
        size.bottomMargin = dp(8);
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
            levels.peek().focus = rows.indexOfChild(v);
            levels.peek().opened = entry.title;
            entry.action.run();
        });
        row.setOnFocusChangeListener((view, focused) -> {
            paint.accept(focused);
            view.setBackground(focused ? focusFill() : rest);
            float scale = focused ? 1.03f : 1f;
            view.animate().scaleX(scale).scaleY(scale).setDuration(120).start();
            if (focused) {
                levels.peek().focus = rows.indexOfChild(view);
                picture.setImageDrawable(entry.pkg != null ? appIcon(entry.pkg) : entry.picture);
                showPreview(entry);
                explanation.setText(Brand.mark(this, entry.text));
            }
        });
    }

    private void showPreview(Entry entry) {
        boolean plain = entry.preview != null;
        boolean pictures = entry.mosaic != null;
        mosaic.show(entry.mosaic);
        if (plain) root.setBackground(entry.preview);
        else if (pictures) root.setBackground(null);
        else if (previewing) root.setBackground(Look.plainBackdrop());
        previewing = plain || pictures;
        previewNote.setVisibility(plain ? View.VISIBLE : View.GONE);
        picture.setVisibility(pictures ? View.INVISIBLE : View.VISIBLE);
    }

    private View label(Entry entry) {
        TextView label = label(entry.title, LABEL_SP);
        label.setText(Brand.mark(this, entry.title));
        label.setAlpha(0.7f);
        label.setPadding(dp(20), dp(LABEL_ROOM), dp(16), dp(8));
        return label;
    }

    private View row(Entry entry) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(20), 0, dp(16), 0);
        TextView title = label(entry.title, 20);
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
        TextView value = label(onOff || entry.value == null ? "" : entry.value, 18);
        value.setSingleLine(true);
        value.setEllipsize(TextUtils.TruncateAt.END);
        value.setMaxWidth(dp(230));
        value.setAlpha(0.75f);
        row.addView(value);
        ImageView lock = new ImageView(this);
        lock.setImageResource(R.drawable.ic_s_lock);
        lock.setAlpha(0.75f);
        LinearLayout.LayoutParams lockPlace = new LinearLayout.LayoutParams(dp(LOCK_SIZE), dp(LOCK_SIZE));
        lockPlace.setMarginStart(dp(8));
        if (entry.locked) row.addView(lock, lockPlace);
        if (onOff) row.addView(toggle, new LinearLayout.LayoutParams(dp(38), dp(21)));
        TextView chevron = label(entry.branch ? "›" : "", 24);
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

    private void showRoot() {
        show(getString(R.string.settings_title), Arrays.asList(
            new Entry(getString(R.string.settings_look), Look.backdropName(this, Look.backdrop(this)), getDrawable(R.drawable.ic_s_palette),
                getString(R.string.settings_look_text), true, () -> enter(this::showLook)),
            new Entry(getString(R.string.settings_clock_and_date), clockValue(), getDrawable(R.drawable.ic_clock),
                getString(R.string.settings_clock_and_date_text),
                true, () -> enter(this::showClock)),
            new Entry(getString(R.string.settings_weather), weatherValue(), getDrawable(R.drawable.ic_cloud),
                getString(R.string.settings_weather_text),
                true, () -> enter(this::showWeather)),
            new Entry(getString(R.string.settings_apps), null, getDrawable(R.drawable.ic_apps),
                getString(R.string.settings_apps_text),
                true, () -> enter(this::showApps)),
            new Entry(getString(R.string.settings_screen), null, getDrawable(R.drawable.ic_s_tv),
                getString(R.string.settings_screen_text),
                true, () -> enter(this::showScreen)),
            new Entry(getString(R.string.panel_notifications), notificationValue(), getDrawable(R.drawable.ic_s_notify),
                getString(R.string.settings_notifications_text), false, () -> {
                    NotificationCounter.seen(this);
                    open(Fire.notifications());
                }),
            new Entry(getString(R.string.settings_language), Language.name(this), getDrawable(R.drawable.ic_s_language),
                getString(R.string.settings_language_text), true, () -> enter(this::showLanguages)),
            new Entry(getString(R.string.settings_network), null, getDrawable(R.drawable.ic_s_language),
                getString(R.string.settings_network_text), true, () -> enter(this::showNetwork)),
            new Entry(getString(R.string.settings_remotes), null, getDrawable(R.drawable.ic_s_settings_remote),
                getString(R.string.settings_remotes_text),
                true, () -> enter(this::showRemotes)),
            new Entry(getString(R.string.settings_device), null, getDrawable(R.drawable.ic_s_developer_board),
                getString(R.string.settings_device_text), true, () -> enter(this::showDevice)),
            new Entry(getString(R.string.settings_privacy), null, getDrawable(R.drawable.ic_s_privacy),
                getString(R.string.settings_privacy_text), true, () -> enter(this::showPrivacy)),
            new Entry(getString(R.string.settings_backup), null, getDrawable(R.drawable.ic_s_backup),
                getString(R.string.settings_backup_text), true, () -> enter(this::showBackup)),
            new Entry(getString(R.string.settings_tools), null, getDrawable(R.drawable.ic_s_build),
                getString(R.string.settings_tools_text), true, () -> enter(this::showTools)),
            new Entry(getString(R.string.settings_update), null, getDrawable(R.drawable.ic_s_update),
                getString(R.string.settings_update_text),
                true, () -> enter(this::showUpdate)),
            new Entry(getString(R.string.settings_about), BuildVersion.name(this), getDrawable(R.drawable.ic_s_info),
                getString(R.string.settings_about_text), true,
                () -> open(new Intent(this, AboutActivity.class))),
            new Entry(getString(R.string.settings_fire), null, getDrawable(R.drawable.ic_s_open),
                getString(R.string.settings_fire_text),
                false, () -> {
                    TaskService.wantFireSettings();
                    open(fireOsSettings());
                })));
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

    private String clockValue() {
        if (!Look.clock(this)) return getString(R.string.settings_hidden);
        return getString(R.string.settings_clock_value, Look.fontNames(this)[Look.font(this)],
            inSentence(Look.sizeNames(this)[Look.size(this)]));
    }

    private void showLook() {
        boolean circular = Look.circular(this);
        List<Entry> entries = new ArrayList<>();
        entries.add(new Entry(getString(R.string.settings_backdrop), Look.backdropName(this, Look.backdrop(this)), getDrawable(R.drawable.ic_s_wallpaper),
            getString(R.string.settings_backdrop_text),
            true, () -> enter(this::showBackdrops)));
        entries.add(new Entry(getString(R.string.settings_home),
            getString(circular ? R.string.settings_home_circle : R.string.settings_home_grid), getDrawable(R.drawable.ic_s_grid),
            getString(circular ? R.string.settings_home_circle_text : R.string.settings_home_grid_text),
            true, () -> enter(this::showHomes)));
        if (circular) {
            // The wheel has no rows, no dock and no names under the circles.
            entries.add(new Entry(getString(R.string.settings_wheel), wheelName(Look.wheel(this)), getDrawable(R.drawable.ic_s_grid),
                getString(R.string.settings_wheel_text),
                true, () -> enter(this::showWheel)));
            boolean glow = Look.tintGlow(this);
            entries.add(new Entry(getString(R.string.settings_glow), glow ? YES : NO, getDrawable(R.drawable.ic_s_palette),
                getString(R.string.settings_glow_text), false, () -> {
                    Look.setTintGlow(this, !glow);
                    refresh();
                }));
            boolean motion = Look.wheelMotion(this);
            Entry opening = new Entry(getString(R.string.settings_wheel_motion), motion ? YES : NO, getDrawable(R.drawable.ic_s_open),
                getString(R.string.settings_wheel_motion_text), false, () -> {
                    Look.setWheelMotion(this, !motion);
                    refresh();
                });
            // The Sticks of 2020 cannot draw it smoothly: shown, always off, not to be chosen.
            opening.off = Fire.STICK_2020;
            entries.add(opening);
            boolean blur = Look.wheelBlur(this);
            entries.add(new Entry(getString(R.string.settings_wheel_blur), blur ? YES : NO, getDrawable(R.drawable.ic_s_wallpaper),
                getString(R.string.settings_wheel_blur_text), false, () -> {
                    Look.setWheelBlur(this, !blur);
                    refresh();
                }));
        } else {
            entries.add(new Entry(getString(R.string.settings_grid), appsPerRow(Look.columns(this)), getDrawable(R.drawable.ic_s_grid),
                getString(R.string.settings_grid_text),
                true, () -> enter(this::showColumns)));
            entries.add(new Entry(getString(R.string.settings_app_names), Look.nameModes(this)[Look.names(this)], getDrawable(R.drawable.ic_s_label),
                getString(R.string.settings_app_names_text),
                true, () -> enter(this::showNames)));
            entries.add(new Entry(getString(R.string.settings_dock), getString(Look.appsOnly(this) ? R.string.settings_dock_top : R.string.settings_dock_bottom),
                getDrawable(R.drawable.ic_s_grid), getString(R.string.settings_dock_text), true, () -> enter(this::showDock)));
        }
        entries.add(new Entry(getString(R.string.settings_saver), saverValue(), getDrawable(R.drawable.ic_image),
            getString(R.string.settings_saver_text), true, () -> enter(this::showSaver)));
        show(getString(R.string.settings_look), entries);
    }

    private String wheelName(int count) {
        return getString(count == 5 ? R.string.settings_wheel_five : R.string.settings_wheel_three);
    }

    private void showHomes() {
        String[] names = {getString(R.string.settings_home_grid), getString(R.string.settings_home_circle)};
        show(getString(R.string.settings_home), choices(names, Look.circular(this) ? 1 : 0, R.drawable.ic_s_grid,
            index -> getString(index == 1 ? R.string.settings_home_circle_text : R.string.settings_home_grid_text), index -> {
                Look.setCircular(this, index == 1);
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

    /** The rows of a list with one choice marked: each runs `choose` with its own index. */
    private List<Entry> choices(String[] names, int chosen, int picture, IntFunction<String> text, IntConsumer choose) {
        List<Entry> entries = new ArrayList<>();
        for (int index = 0; index < names.length; index++) {
            int choice = index;
            entries.add(new Entry(names[index], index == chosen ? CHECK : null, getDrawable(picture),
                text.apply(index), false, () -> choose.accept(choice)));
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
        String[] names = {getString(R.string.settings_dock_bottom_choice), getString(R.string.settings_dock_top_choice)};
        show(getString(R.string.settings_dock), choices(names, Look.appsOnly(this) ? 1 : 0, R.drawable.ic_s_grid,
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

    private void showApps() {
        show(getString(R.string.settings_apps), Arrays.asList(
            new Entry(getString(R.string.settings_all_apps), null, getDrawable(R.drawable.ic_apps),
                getString(R.string.settings_all_apps_text),
                false, () -> open(new Intent(this, AppsActivity.class))),
            new Entry(getString(R.string.settings_task_manager), null, getDrawable(R.drawable.ic_tasks),
                getString(R.string.settings_task_manager_text),
                false, () -> open(new Intent(this, TasksActivity.class))),
            new Entry(getString(R.string.settings_held_keys), TaskService.keysOn(this) ? YES : NO, getDrawable(R.drawable.ic_s_menu),
                getString(R.string.settings_held_keys_text),
                false, () -> changeSystem(() -> TaskService.setKeys(this, !TaskService.keysOn(this)))),
            new Entry(getString(R.string.settings_sort_home), null, getDrawable(R.drawable.ic_s_sort),
                getString(R.string.settings_sort_home_text), false, () -> {
                    HomeApps.sort(this, HomeApps.load(this));
                    say(getString(R.string.settings_home_sorted));
                }),
            new Entry(getString(R.string.settings_manage_apps), null, getDrawable(R.drawable.ic_s_widgets),
                getString(R.string.settings_manage_apps_text),
                true, () -> enter(this::showManageApps))));
    }

    private void showManageApps() {
        List<Entry> entries = new ArrayList<>();
        for (HomeApps.App app : HomeApps.installed(this)) {
            Entry entry = new Entry(app.label, null, null, getString(R.string.settings_manage_app_text),
                true, () -> enter(() -> showApp(app.pkg)));
            entry.pkg = app.pkg;
            entries.add(entry);
        }
        show(getString(R.string.settings_manage_apps), entries);
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
        if (Fire.ANDROID_9) entries.add(new Entry(getString(R.string.settings_clear_data), null, picture, getString(R.string.settings_clear_data_text),
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

    private void showScreen() {
        boolean cec = Settings.Global.getInt(getContentResolver(), CEC_ENABLED, 1) == 1;
        boolean tvOff = Settings.Global.getInt(getContentResolver(), CEC_TV_OFF, 0) == 1;
        boolean clicks = Settings.System.getInt(getContentResolver(), Settings.System.SOUND_EFFECTS_ENABLED, 0) == 1;
        boolean quick = Settings.Global.getFloat(getContentResolver(), Settings.Global.WINDOW_ANIMATION_SCALE, 1) < 1;
        Drawable picture = getDrawable(R.drawable.ic_image);
        show(getString(R.string.settings_screen), Arrays.asList(
            new Entry(getString(R.string.settings_sleep), minutes(sleepMinutes()), getDrawable(R.drawable.ic_timer),
                getString(R.string.settings_sleep_text), true, () -> enter(this::showSleepTimes)),
            new Entry(getString(R.string.settings_cec), cec ? YES : NO, getDrawable(R.drawable.ic_s_hdmi),
                getString(R.string.settings_cec_text), false,
                () -> changeSystem(() -> Settings.Global.putInt(getContentResolver(), CEC_ENABLED, cec ? 0 : 1))),
            new Entry(getString(R.string.settings_tv_off), tvOff ? YES : NO, getDrawable(R.drawable.ic_power),
                getString(R.string.settings_tv_off_text), false,
                () -> changeSystem(() -> Settings.Global.putInt(getContentResolver(), CEC_TV_OFF, tvOff ? 0 : 1))),
            new Entry(getString(R.string.settings_sounds), clicks ? YES : NO, getDrawable(R.drawable.ic_s_music),
                getString(R.string.settings_sounds_text), false,
                () -> changeSystem(() -> Settings.System.putInt(getContentResolver(),
                    Settings.System.SOUND_EFFECTS_ENABLED, clicks ? 0 : 1))),
            new Entry(getString(R.string.settings_quick_animations), quick ? YES : NO, getDrawable(R.drawable.ic_s_speed),
                getString(R.string.settings_quick_animations_text), false,
                () -> changeSystem(() -> {
                    float scale = quick ? 1 : QUICK_SCALE;
                    Settings.Global.putFloat(getContentResolver(), Settings.Global.WINDOW_ANIMATION_SCALE, scale);
                    Settings.Global.putFloat(getContentResolver(), Settings.Global.TRANSITION_ANIMATION_SCALE, scale);
                })),
            new Entry(getString(R.string.settings_video), null, getDrawable(R.drawable.ic_s_movie), getString(R.string.settings_video_text), true, () -> enter(this::showVideo)),
            new Entry(getString(R.string.settings_audio), null, getDrawable(R.drawable.ic_s_volume), getString(R.string.settings_audio_text), true, () -> enter(this::showAudio)),
            new Entry(getString(R.string.settings_standards), null, getDrawable(R.drawable.ic_s_standards), getString(R.string.settings_standards_text),
                true, () -> enter(this::showStandards))));
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

    private void showVideo() {
        Level level = levels.peek();
        showWaiting(getString(R.string.settings_video));
        Background.run(() -> {
            String resolution = fire.read(FireSettings.PICTURE, RESOLUTION);
            String range = fire.read(FireSettings.PICTURE, RANGE);
            String format = fire.read(FireSettings.PICTURE, COLOR_FORMAT);
            String depth = fire.read(FireSettings.PICTURE, COLOR_DEPTH);
            runOnUiThread(() -> {
                if (levels.peek() != level) return;
                Drawable picture = getDrawable(R.drawable.ic_image);
                List<Entry> entries = new ArrayList<>();
                entries.add(new Entry(getString(R.string.settings_resolution), nameOf(RESOLUTIONS, strings(RESOLUTION_NAMES), resolution), getDrawable(R.drawable.ic_s_aspect),
                    resolutionText(), true, () -> enter(() -> showResolutions(resolution))));
                entries.add(new Entry("HDR", nameOf(RANGES, strings(RANGE_NAMES), range), getDrawable(R.drawable.ic_s_hdr),
                    getString(R.string.settings_hdr_text), true,
                    () -> enter(() -> showChoices("HDR", FireSettings.PICTURE, RANGE, RANGES, strings(RANGE_NAMES), range))));
                entries.add(new Entry(getString(R.string.settings_color_format), nameOf(COLOR_FORMATS, colorFormatNames(), format), getDrawable(R.drawable.ic_s_palette),
                    getString(R.string.settings_color_format_text), true,
                    () -> enter(() -> showChoices(getString(R.string.settings_color_format), FireSettings.PICTURE, COLOR_FORMAT, COLOR_FORMATS, colorFormatNames(), format))));
                if (depth != null && !depth.isEmpty()) {
                    entries.add(new Entry(getString(R.string.settings_color_depth), nameOf(COLOR_DEPTHS, strings(COLOR_DEPTH_NAMES), depth), getDrawable(R.drawable.ic_s_gradient),
                        getString(R.string.settings_color_depth_text), true,
                        () -> enter(() -> showChoices(getString(R.string.settings_color_depth), FireSettings.PICTURE, COLOR_DEPTH, COLOR_DEPTHS, strings(COLOR_DEPTH_NAMES), depth))));
                }
                entries.add(new Entry(getString(R.string.settings_video_quality), getString(VIDEO_QUALITY_NAMES[videoQuality()]), getDrawable(R.drawable.ic_s_quality),
                    getString(R.string.settings_video_quality_text), true, () -> enter(this::showVideoQuality)));
                show(getString(R.string.settings_video), entries);
            });
        });
    }

    private void showAudio() {
        Level level = levels.peek();
        showWaiting(getString(R.string.settings_audio));
        Background.run(() -> {
            String leveler = fire.read(FireSettings.AUDIO, LEVELER);
            String dialogue = fire.read(FireSettings.AUDIO, DIALOGUE);
            runOnUiThread(() -> {
                if (levels.peek() != level) return;
                Drawable picture = getDrawable(R.drawable.ic_image);
                boolean even = "true".equals(leveler);
                boolean clear = "true".equals(dialogue);
                String surround = surroundNow();
                show(getString(R.string.settings_audio), Arrays.asList(
                    new Entry(getString(R.string.settings_surround), nameOf(SURROUNDS, surroundNames(), surround), getDrawable(R.drawable.ic_s_surround),
                        getString(R.string.settings_surround_text), true,
                        () -> enter(() -> showChoices(getString(R.string.settings_surround), FireSettings.AUDIO, SURROUND, SURROUNDS, surroundNames(), surround))),
                    new Entry(getString(R.string.settings_dialogue), clear ? YES : NO, getDrawable(R.drawable.ic_dialogue),
                        getString(R.string.settings_dialogue_text), false,
                        () -> writeFire(FireSettings.AUDIO, DIALOGUE, clear ? "false" : "true", () -> refreshIf(level))),
                    new Entry(getString(R.string.settings_leveler), even ? YES : NO, getDrawable(R.drawable.ic_leveler),
                        getString(R.string.settings_leveler_text), false,
                        () -> writeFire(FireSettings.AUDIO, LEVELER, even ? "false" : "true", () -> refreshIf(level)))));
            });
        });
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
                if (!written) sayLong(getString(R.string.settings_fire_refused));
                done.run();
            });
        });
    }

    private void showChoices(String title, String provider, String id, String[] actions, String[] names, String current) {
        Level level = levels.peek();
        show(title, choices(names, Arrays.asList(actions).indexOf(current), R.drawable.ic_image,
            getString(R.string.settings_fire_may_refuse),
            index -> writeFire(provider, id, actions[index], () -> backFrom(level))));
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
        // The Stick of 2020 stops at 1080p: the two 4K lines, after Auto, would only give a black screen.
        if (Fire.STICK_2020) {
            values.subList(1, 3).clear();
            names.subList(1, 3).clear();
        }
        show(getString(R.string.settings_resolution), choices(names.toArray(new String[0]), values.indexOf(current), R.drawable.ic_image,
            resolutionText(), index -> {
                if (!values.get(index).equals(current)) changeResolution(current, values.get(index));
            }));
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
    private void showRemotes() {
        Drawable picture = getDrawable(R.drawable.ic_bluetooth);
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        List<Entry> entries = new ArrayList<>();
        if (adapter == null || !adapter.isEnabled()) {
            entries.add(new Entry(getString(R.string.settings_bluetooth_off), null, picture, getString(R.string.settings_bluetooth_off_text), false, () -> { }));
            show("Bluetooth", entries);
            return;
        }
        entries.add(new Entry(getString(R.string.settings_find_devices), null, picture,
            getString(R.string.settings_find_devices_text),
            true, () -> enter(this::showBluetoothSearch)));
        for (BluetoothDevice device : adapter.getBondedDevices()) {
            String name = device.getName() != null ? device.getName() : device.getAddress();
            entries.add(new Entry(name, getString(Fire.connected(device) ? R.string.settings_connected : R.string.settings_paired), picture,
                getString(R.string.settings_press_to_forget_device), false, () -> forgetBluetooth(device, name)));
        }
        show("Bluetooth", entries);
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
        mosaic.show(null);
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

    private void showPrivacy() {
        boolean adsLimited = Settings.Secure.getInt(getContentResolver(), ADS_LIMITED, 1) == 1;
        boolean metrics = Settings.Secure.getInt(getContentResolver(), USAGE_METRICS, 0) == 1;
        boolean counted = NotificationCounter.allowed(this);
        Drawable picture = getDrawable(R.drawable.ic_settings);
        List<Entry> entries = new ArrayList<>(Arrays.asList(
            new Entry(getString(R.string.settings_limit_ads), adsLimited ? YES : NO, getDrawable(R.drawable.ic_s_ads),
                getString(R.string.settings_limit_ads_text), false,
                () -> changeSystem(() -> Settings.Secure.putInt(getContentResolver(), ADS_LIMITED, adsLimited ? 0 : 1))),
            new Entry(getString(R.string.settings_usage_data), metrics ? YES : NO, getDrawable(R.drawable.ic_s_chart),
                getString(R.string.settings_usage_data_text), false,
                () -> changeSystem(() -> Settings.Secure.putInt(getContentResolver(), USAGE_METRICS, metrics ? 0 : 1)))));
        if (!Fire.ANDROID_9) entries.add(0, new Entry(getString(R.string.settings_notification_count), counted ? YES : NO, getDrawable(R.drawable.ic_s_notify),
            getString(R.string.settings_notification_count_text), false,
            () -> changeCounter(counted)));
        show(getString(R.string.settings_privacy), entries);
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
    private void showWiredNetwork() {
        List<Entry> entries = infoRows(DeviceInfo.network(this), getDrawable(R.drawable.ic_ethernet), getString(R.string.settings_connection_state));
        entries.add(new Entry("Wi-Fi", getString(R.string.settings_wifi_on_cable), getDrawable(R.drawable.ic_wifi),
            getString(R.string.settings_wifi_on_cable_text), false, () -> { }));
        show(getString(R.string.settings_network), entries);
    }

    private void showNetwork() {
        if (DeviceInfo.wired(this)) {
            showWiredNetwork();
            return;
        }
        Drawable picture = getDrawable(R.drawable.ic_wifi);
        boolean on = getSystemService(WifiManager.class).isWifiEnabled();
        List<Entry> entries = new ArrayList<>();
        if (Fire.ANDROID_9) {
            entries.add(new Entry(getString(R.string.settings_wifi_networks), null, picture, getString(R.string.settings_wifi_on_fire),
                false, () -> open(new Intent(Settings.ACTION_WIFI_SETTINGS))));
        } else {
            entries.add(new Entry("Wi-Fi", on ? YES : NO, picture, getString(R.string.settings_wifi_text), false,
                () -> setWifi(!on)));
        }
        if (on) {
            if (!Fire.ANDROID_9) entries.add(new Entry(getString(R.string.settings_wifi_networks), null, picture, getString(R.string.settings_wifi_networks_text),
                true, () -> enter(this::showWifi)));
            entries.addAll(infoRows(DeviceInfo.network(this), picture, getString(R.string.settings_connection_state)));
        }
        show(getString(R.string.settings_network), entries);
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
        EditText password = new EditText(this);
        password.setSingleLine(true);
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        AlertDialog dialog = dialog()
            .setTitle(getString(R.string.settings_wifi_password, name)).setView(password)
            .setNegativeButton(getString(R.string.settings_cancel), null)
            .setPositiveButton(getString(R.string.settings_wifi_join), (d, button) -> connectWifi(name, security, password.getText().toString()))
            .create();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        dialog.show();
        centerButton(dialog);
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

    private void showDevice() {
        Drawable picture = getDrawable(R.drawable.ic_settings);
        List<Entry> entries = infoRows(DeviceInfo.system(this), picture, getString(R.string.settings_device_info_text));
        if (!Fire.ANDROID_9) entries.add(new Entry(getString(R.string.settings_usb), null, getDrawable(R.drawable.ic_s_usb), getString(R.string.settings_usb_text),
            true, () -> enter(this::showUsb)));
        entries.add(new Entry(getString(R.string.settings_standby), null, getDrawable(R.drawable.ic_power), getString(R.string.settings_standby_text), false,
            TaskService::standby));
        entries.add(new Entry(getString(R.string.settings_restart), null, getDrawable(R.drawable.ic_restart), getString(R.string.settings_restart_text), false,
            () -> confirm(getString(R.string.settings_restart_question), getString(R.string.settings_restart_warning), getString(R.string.settings_restart),
                () -> {
                    TaskService.restart();
                })));
        show(getString(R.string.settings_device), entries);
    }

    private void showBackup() {
        Drawable picture = getDrawable(R.drawable.ic_apps);
        show(getString(R.string.settings_backup), Arrays.asList(
            new Entry(getString(R.string.settings_save_choices), null, getDrawable(R.drawable.ic_s_save),
                getString(R.string.settings_save_choices_text), false, () -> {
                    try {
                        Backup.save(this);
                        say(getString(R.string.settings_choices_saved));
                        refresh();
                    } catch (IOException | JSONException e) {
                        sayLong(getString(R.string.settings_save_failed));
                    }
                }),
            new Entry(getString(R.string.settings_restore_choices), Backup.exists(this) ? null : getString(R.string.settings_no_backup), getDrawable(R.drawable.ic_s_restore),
                getString(R.string.settings_restore_choices_text), false, () -> {
                    try {
                        Backup.restore(this);
                        say(getString(R.string.settings_choices_restored));
                        root.setBackground(Look.plainBackdrop());
                        refresh();
                    } catch (IOException | JSONException e) {
                        sayLong(getString(R.string.settings_nothing_to_restore));
                    }
                })));
    }

    private void showUpdate() {
        boolean beta = Updater.beta(this);
        show(getString(R.string.settings_update), Arrays.asList(
            new Entry(getString(R.string.settings_update_check), BuildVersion.name(this), getDrawable(R.drawable.ic_s_update),
                getString(R.string.settings_update_text), true, () -> open(new Intent(this, UpdateActivity.class))),
            new Entry(getString(R.string.settings_beta), beta ? YES : NO, getDrawable(R.drawable.ic_s_build),
                getString(R.string.settings_beta_text), false, () -> {
                    Updater.setBeta(this, !beta);
                    refresh();
                })));
    }

    private void showTools() {
        boolean doubleHome = HomeCover.doubleHome;
        show(getString(R.string.settings_tools), Arrays.asList(
            new Entry(getString(R.string.settings_tour), null, getDrawable(R.drawable.ic_salotto),
                getString(R.string.settings_tour_text), false,
                () -> open(new Intent(this, FirstRunActivity.class))),
            new Entry("AirPlay", null, appIcon(Fire.AIRPLAY),
                getString(R.string.settings_airplay_text),
                false, () -> openPackage(Fire.AIRPLAY)),
            new Entry(getString(R.string.settings_double_home), doubleHome ? YES : NO, getDrawable(R.drawable.ic_s_home),
                getString(R.string.settings_double_home_text),
                false, () -> {
                    TaskService.rememberDoubleHome(this, !doubleHome);
                    refresh();
                })));
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

    private Entry weatherBackdrop(String name, int picture, String text) {
        boolean ready = Weather.active(this);
        return new Entry(Look.backdropName(this, name), name.equals(Look.backdrop(this)) ? CHECK : ready ? null : getString(R.string.first_place_title),
            getDrawable(picture), getString(R.string.settings_weather_backdrop_text, text), false, () -> {
                if (!ready) {
                    sayLong(getString(R.string.settings_place_first));
                    return;
                }
                Look.setBackdrop(this, name);
                refresh();
            });
    }

    private Entry plainBackdrop(String name, String text) {
        Entry entry = new Entry(Look.backdropName(this, name), name.equals(Look.backdrop(this)) ? CHECK : null,
            getDrawable(R.drawable.ic_image), text, false, () -> {
                Look.setBackdrop(this, name);
                root.setBackground(Look.plainBackdrop());
                refresh();
            });
        entry.mark = 0;
        return entry;
    }

    private void showBackdrops() {
        List<Entry> weather = new ArrayList<>();
        Entry pictures = weatherBackdrop(Look.PICTURES, R.drawable.ic_image,
            getString(R.string.settings_backdrop_pictures_text));
        pictures.mosaic = Look.PICTURES_FOLDER;
        pictures.mark = R.drawable.ic_cloud;
        if (Packs.listed(this, Look.PICTURES_FOLDER)) weather.add(pictures);
        Entry photos = weatherBackdrop(Look.PHOTOS, R.drawable.ic_photo,
            getString(R.string.settings_backdrop_photos_text));
        photos.mosaic = Look.PHOTOS_FOLDER;
        photos.mark = R.drawable.ic_cloud;
        if (Packs.listed(this, Look.PHOTOS_FOLDER)) weather.add(photos);
        Entry aerial = weatherBackdrop(Look.AERIAL, R.drawable.ic_photo,
            getString(R.string.settings_backdrop_aerial_text));
        aerial.mosaic = Look.AERIAL_FOLDER;
        aerial.mark = R.drawable.ic_cloud;
        if (Packs.listed(this, Look.AERIAL_FOLDER)) weather.add(aerial);
        List<Entry> timed = new ArrayList<>();
        Entry drawn = plainBackdrop(Look.ABSTRACT, getString(R.string.settings_backdrop_abstract_text));
        drawn.mosaic = Look.ABSTRACT_FOLDER;
        drawn.mark = R.drawable.ic_restart;
        if (shown(Look.ABSTRACT_FOLDER)) timed.add(drawn);
        Entry shapes = plainBackdrop(Look.ABSTRACT_3D, getString(R.string.settings_backdrop_abstract3d_text));
        shapes.mosaic = Look.ABSTRACT_3D_FOLDER;
        shapes.mark = R.drawable.ic_restart;
        if (shown(Look.ABSTRACT_3D_FOLDER)) timed.add(shapes);
        Entry city = plainBackdrop(Look.NOX, getString(R.string.settings_backdrop_nox_text));
        city.mosaic = Look.NOX_FOLDER;
        city.mark = R.drawable.ic_restart;
        if (shown(Look.NOX_FOLDER)) timed.add(city);
        Entry nature = plainBackdrop(Look.NATURE, getString(R.string.settings_backdrop_nature_text));
        nature.mosaic = Look.NATURE_FOLDER;
        nature.mark = R.drawable.ic_restart;
        if (shown(Look.NATURE_FOLDER)) timed.add(nature);
        if (Look.images(this).size() >= Look.PERSONAL_TIMED_FROM) {
            Entry mine = plainBackdrop(Look.PERSONAL, getString(R.string.settings_backdrop_personal_timed_text));
            mine.mark = R.drawable.ic_restart;
            timed.add(mine);
        }
        weather.add(0, groupLabel(R.string.settings_backdrop_weather_group));
        timed.add(0, groupLabel(R.string.first_backdrop_dynamic));
        // Under its own heading it is plainly about these sets, so it stays on whatever the backdrop.
        Entry pace = paceEntry();
        pace.mark = 0;
        timed.add(pace);
        boolean weatherFirst = Weather.active(this);
        List<Entry> entries = new ArrayList<>(weatherFirst ? weather : timed);
        entries.addAll(weatherFirst ? timed : weather);
        entries.add(groupLabel(R.string.first_backdrop_static));
        entries.add(standardSet());
        String[][] sets = {{Look.ABSTRACT, Look.ABSTRACT_FOLDER}, {Look.ABSTRACT_3D, Look.ABSTRACT_3D_FOLDER},
            {Look.NOX, Look.NOX_FOLDER}, {Look.NATURE, Look.NATURE_FOLDER}};
        for (String[] set : sets) if (shown(set[1])) entries.add(pictureSet(set[0], set[1]));
        entries.add(personalSet());
        showFromChosen(getString(R.string.settings_backdrop), entries);
    }

    /** A collection has a row only if it is whole on the TV, or inside the app, and was not deleted. */
    private boolean shown(String folder) { return Packs.listed(this, folder) && !Look.setTaken(this, folder); }

    /** Arriving, the selection starts on what is in use. */
    private void showFromChosen(String title, List<Entry> entries) {
        Level level = levels.peek();
        if (level.focus == 0) {
            for (int index = 0; index < entries.size(); index++) {
                if (CHECK.equals(entries.get(index).value)) level.focus = index;
            }
        }
        show(title, entries);
    }

    private Entry standardSet() {
        boolean chosen = Arrays.asList(Look.PRESET_NAMES).contains(Look.backdrop(this));
        Entry entry = new Entry(getString(R.string.settings_backdrop_standard), chosen ? CHECK : null, getDrawable(R.drawable.ic_image),
            getString(R.string.settings_backdrop_standard_text), true,
            () -> open(new Intent(this, PictureGridActivity.class)
                .putExtra(PictureGridActivity.TITLE, getString(R.string.settings_backdrop_standard))));
        entry.preview = Look.preset(Look.backdrop(this));
        entry.mark = 0;
        return entry;
    }

    /** One line for a set: its pictures open as a grid, and the one chosen stays. */
    private Entry pictureSet(String name, String folder) {
        boolean chosen = Look.backdrop(this).startsWith(folder + "/");
        Entry entry = new Entry(name, chosen ? CHECK : null, getDrawable(R.drawable.ic_image),
            getString(R.string.settings_backdrop_pick_text), true,
            () -> open(new Intent(this, PictureGridActivity.class)
                .putExtra(PictureGridActivity.FOLDER, folder).putExtra(PictureGridActivity.TITLE, name)));
        entry.mosaic = folder;
        entry.mark = 0;
        return entry;
    }

    /** The images sent from a phone: a grid like the sets, empty at first, with Carica under it. */
    private Entry personalSet() {
        String name = getString(R.string.settings_backdrop_personal);
        Entry entry = new Entry(name, Look.personal(this, Look.backdrop(this)) ? CHECK : null, getDrawable(R.drawable.ic_photo),
            getString(R.string.settings_backdrop_personal_text), true,
            () -> open(new Intent(this, PictureGridActivity.class)
                .putExtra(PictureGridActivity.PERSONAL, true).putExtra(PictureGridActivity.TITLE, name)));
        entry.mark = 0;
        return entry;
    }

    private Entry groupLabel(int name) {
        return groupLabel(getString(name));
    }

    private Entry groupLabel(String name) {
        Entry entry = new Entry(name, null, null, "", false, () -> { });
        entry.label = true;
        return entry;
    }

    private Entry paceEntry() {
        Entry pace = new Entry(getString(R.string.settings_abstract_time), minutes(Look.abstractMinutes(this)),
            getDrawable(R.drawable.ic_timer), getString(R.string.settings_abstract_time_text), true,
            () -> enter(this::showAbstractTimes));
        return pace;
    }

    private void showAbstractTimes() {
        show(getString(R.string.settings_abstract_time), choices(minuteNames(Look.ABSTRACT_MINUTES),
            indexOf(Look.ABSTRACT_MINUTES, Look.abstractMinutes(this)), R.drawable.ic_timer,
            getString(R.string.settings_abstract_time_text), index -> {
                Look.setAbstractMinutes(this, Look.ABSTRACT_MINUTES[index]);
                refresh();
            }));
    }

    private void showClock() {
        boolean visible = Look.clock(this);
        show(getString(R.string.settings_clock), Arrays.asList(
            groupLabel(R.string.settings_time_group),
            new Entry(getString(R.string.settings_show_on_home), visible ? YES : NO, getDrawable(R.drawable.ic_clock),
                getString(R.string.settings_clock_show_text), false, () -> {
                    Look.setClock(this, !visible);
                    refresh();
                }),
            new Entry(getString(R.string.settings_font), Look.fontNames(this)[Look.font(this)], getDrawable(R.drawable.ic_clock),
                getString(R.string.settings_font_text), true, () -> enter(this::showFonts)),
            new Entry(getString(R.string.settings_size), Look.sizeNames(this)[Look.size(this)], getDrawable(R.drawable.ic_clock),
                getString(R.string.settings_size_text), true, () -> enter(this::showSizes)),
            new Entry(getString(R.string.settings_hours), Look.hours24(this) ? getString(R.string.settings_hours_24) : getString(R.string.settings_hours_12), getDrawable(R.drawable.ic_clock),
                getString(R.string.settings_hours_text), false, () -> {
                    Look.setHours24(this, !Look.hours24(this));
                    refresh();
                }),
            new Entry(getString(R.string.settings_seconds), Look.seconds(this) ? YES : NO, getDrawable(R.drawable.ic_clock),
                getString(R.string.settings_seconds_text), false, () -> {
                    Look.setSeconds(this, !Look.seconds(this));
                    refresh();
                }),
            groupLabel(R.string.settings_date),
            new Entry(getString(R.string.settings_show_on_home), Look.date(this) ? YES : NO, getDrawable(R.drawable.ic_clock),
                getString(R.string.settings_date_show_text), false, () -> {
                    Look.setDate(this, !Look.date(this));
                    refresh();
                }),
            new Entry(getString(R.string.settings_font), Look.fontNames(this)[Look.dateFont(this)], getDrawable(R.drawable.ic_clock),
                getString(R.string.settings_date_font_text), true, () -> enter(this::showDateFonts)),
            new Entry(getString(R.string.settings_size), Look.sizeNames(this)[Look.dateSize(this)], getDrawable(R.drawable.ic_clock),
                getString(R.string.settings_date_size_text), true, () -> enter(this::showDateSizes)),
            new Entry(getString(R.string.settings_hours), dateFormatName(Look.dateNumeric(this)), getDrawable(R.drawable.ic_clock),
                getString(R.string.settings_date_format_text), true, () -> enter(this::showDateFormats))));
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

    private String weatherValue() {
        String place = Weather.place(this);
        return !Weather.enabled(this) ? getString(R.string.settings_off) : place != null ? place : getString(R.string.first_place_title);
    }

    private void showWeather() {
        boolean enabled = Weather.enabled(this);
        String place = Weather.place(this);
        show(getString(R.string.settings_weather), Arrays.asList(
            new Entry(getString(R.string.settings_show_on_home), enabled ? YES : NO, getDrawable(R.drawable.ic_cloud),
                getString(R.string.settings_weather_show_text), false, () -> {
                    Weather.setEnabled(this, !enabled);
                    refresh();
                }),
            new Entry(getString(R.string.settings_place), place != null ? place : getString(R.string.settings_place_unset), getDrawable(R.drawable.ic_cloud),
                getString(R.string.settings_place_text),
                true, this::askPlace),
            new Entry(getString(R.string.settings_units), Weather.fahrenheit(this) ? "°F" : "°C", getDrawable(R.drawable.ic_cloud),
                getString(R.string.settings_units_text), false, () -> {
                    Weather.setFahrenheit(this, !Weather.fahrenheit(this));
                    refresh();
                }),
            new Entry(getString(R.string.settings_font), weatherFontName(), getDrawable(R.drawable.ic_cloud),
                getString(R.string.settings_weather_font_text), true, () -> enter(this::showWeatherFonts)),
            new Entry(getString(R.string.settings_size), weatherSizeName(), getDrawable(R.drawable.ic_cloud),
                getString(R.string.settings_weather_size_text), true, () -> enter(this::showWeatherSizes))));
    }

    private void askPlace() {
        EditText name = new EditText(this);
        name.setSingleLine(true);
        name.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        AlertDialog dialog = dialog()
            .setTitle(getString(R.string.first_city_hint)).setView(name)
            .setPositiveButton(getString(R.string.first_search), (d, button) -> searchPlace(name.getText().toString().trim()))
            .create();
        name.setOnEditorActionListener((view, action, event) -> {
            dialog.dismiss();
            searchPlace(name.getText().toString().trim());
            return true;
        });
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        dialog.show();
        centerButton(dialog);
    }

    /** The dialog's own bar leaves its only button off centre, under a line that is centred. */
    private void centerButton(AlertDialog dialog) {
        View button = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        LinearLayout bar = (LinearLayout) button.getParent();
        for (int index = 0; index < bar.getChildCount(); index++) {
            if (bar.getChildAt(index) != button) bar.getChildAt(index).setVisibility(View.GONE);
        }
        bar.setGravity(Gravity.CENTER_HORIZONTAL);
        bar.setPadding(0, bar.getPaddingTop(), 0, bar.getPaddingBottom());
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

    private void showSaver() {
        boolean on = saverOn();
        boolean own = ownSaver();
        Drawable picture = getDrawable(R.drawable.ic_image);
        List<Entry> entries = new ArrayList<>(Arrays.asList(
            new Entry(getString(R.string.settings_saver_active), on ? YES : NO, picture,
                getString(R.string.settings_saver_active_text), false,
                () -> changeSystem(() -> Settings.Secure.putInt(getContentResolver(), SAVER_ENABLED, on ? 0 : 1))),
            new Entry(getString(R.string.settings_saver_kind), own ? ownSaverName() : "Fire OS", getDrawable(R.drawable.ic_photo),
                getString(R.string.settings_saver_kind_text), true, () -> enter(this::showSaverKinds)),
            new Entry(getString(R.string.settings_saver_after), minutes(saverMinutes()), picture, saverWait(), true, () -> enter(this::showSaverTimes))));
        if (own) {
            entries.add(saverShows(R.string.settings_saver_time, R.drawable.ic_clock, Look.saverTime(this), Look::setSaverTime));
            entries.add(saverShows(R.string.settings_saver_date, R.drawable.ic_clock, Look.saverDate(this), Look::setSaverDate));
            entries.add(saverShows(R.string.settings_saver_weather, R.drawable.ic_cloud, Look.saverWeather(this), Look::setSaverWeather));
            entries.add(new Entry(getString(R.string.settings_saver_place), saverPlaces()[Look.saverPlace(this)], getDrawable(R.drawable.ic_clock),
                getString(R.string.settings_saver_place_text), true, () -> enter(this::showSaverPlaces)));
        }
        if (own || !Fire.ANDROID_9) entries.add(new Entry(getString(R.string.settings_saver_start), null, picture, getString(R.string.settings_saver_start_text), false,
            () -> open(own ? new Intent(this, SaverActivity.class) : Fire.screensaver(this))));
        show(getString(R.string.settings_saver), entries);
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

    /** The rows stay where they are whatever is chosen, so the selection stays on the row pressed. */
    private void showSaverKinds() {
        boolean own = ownSaver();
        List<Entry> entries = new ArrayList<>();
        entries.add(groupLabel(Brand.NAME));
        for (Saver saver : SAVERS) if (shown(saver.folder)) entries.add(saverEntry(saver));
        if (Look.images(this).size() >= Look.PERSONAL_TIMED_FROM) {
            Entry mine = saverEntry(personalSaver());
            mine.mosaic = null;
            entries.add(mine);
        }
        entries.add(paceEntry());
        entries.add(groupLabel(R.string.settings_saver_native));
        entries.add(new Entry(getString(R.string.settings_saver_fire), own ? null : CHECK, getDrawable(R.drawable.ic_image),
            getString(R.string.settings_saver_fire_text), false, () -> chooseSaver(Fire.AMAZON_SAVER)));
        Entry choose = new Entry(getString(R.string.settings_saver_choose), null, getDrawable(R.drawable.ic_s_open),
            getString(R.string.settings_saver_choose_text), true, () -> open(Fire.saverMenu()));
        choose.off = own;
        entries.add(choose);
        show(getString(R.string.settings_saver_kind), entries);
    }

    private Entry saverEntry(Saver saver) {
        boolean chosen = ownSaver() && saver.folder.equals(Look.saverFolder(this));
        Entry entry = new Entry(saver.name, chosen ? CHECK : null, getDrawable(R.drawable.ic_image),
            getString(R.string.settings_saver_set_text), false, () -> {
                Look.setSaverFolder(this, saver.folder);
                chooseSaver(new ComponentName(this, SaverService.class).flattenToShortString());
            });
        entry.mosaic = saver.folder;
        return entry;
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
