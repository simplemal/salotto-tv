package it.simplemal.firehome;

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
import android.os.Bundle;
import android.os.Process;
import android.os.storage.StorageManager;
import android.provider.Settings;
import android.service.dreams.DreamService;
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
    /** As wide as a preview of the screen needs: an icon stays square in the middle. */
    private static final int PICTURE_WIDTH = 213;
    private static final int LOCK_SIZE = 16;
    private static final int WIFI_5GHZ = 5000;
    /** Its middle sits just above the middle of the 540-tall screen. */
    private static final int PICTURE_TOP = 170;
    private static final int EXPLANATION_TOP = PICTURE_TOP + PICTURE_SIZE + 22;
    private static final int EXPLANATION_SP = 15;
    private static final String RESOLUTION = "pq_video_resolution";
    private static final String[] RESOLUTIONS = {"-1", "97", "96", "16", "31", "4", "19"};
    private static final String[] RESOLUTION_NAMES = {"Automatica", "4K a 60 Hz", "4K a 50 Hz", "1080p a 60 Hz",
        "1080p a 50 Hz", "720p a 60 Hz", "720p a 50 Hz"};
    private static final int RESOLUTION_WAIT_S = 15;
    private static final String RESOLUTION_TEXT = "Se l’immagine sparisce, entro " + RESOLUTION_WAIT_S
        + " secondi Salotto torna da sola alla precedente.";
    private static final String RANGE = "pq_dynamic_range";
    private static final String[] RANGES = {"adaptive", "forced", "forcedsdr"};
    private static final String[] RANGE_NAMES = {"Adattivo", "Sempre HDR", "Sempre SDR"};
    private static final String COLOR_FORMAT = "pq_color_format";
    private static final String[] COLOR_FORMATS = {"auto", "rgb", "ycbcr"};
    private static final String[] COLOR_FORMAT_NAMES = {"Automatico", "RGB", "YCbCr"};
    private static final String COLOR_DEPTH = "pq_color_depth";
    private static final String[] COLOR_DEPTHS = {"8", "10", "12"};
    private static final String[] COLOR_DEPTH_NAMES = {"8 bit", "10 bit", "12 bit"};
    private static final String SURROUND = "aq_surround_sound";
    private static final String[] SURROUNDS = {"BEST_AVAILABLE", "STEREO", "DD_PLUS_ALWAYS", "DD_ALWAYS"};
    private static final String[] SURROUND_NAMES = {"Migliore disponibile", "Stereo", "Dolby Digital Plus", "Dolby Digital"};
    /** In the order of encoded_surround_output: 0 to 3. */
    private static final String SURROUND_MODE = "encoded_surround_output";
    private static final String LEVELER = "aq_volume_leveler";
    private static final String DIALOGUE = "aq_dialogue_enhancer";
    private boolean doubleHome;
    private static final String VIDEO_QUALITY = "com.amazon.tv.settings.VIDEO_QUALITY";
    private static final String[] VIDEO_QUALITY_NAMES = {"Buona", "Migliore", "Ottima"};
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
    private static final float QUICK_SCALE = 0.5f;
    private static final String LOCATION = Manifest.permission.ACCESS_FINE_LOCATION;
    private static final String[] WIFI_STRENGTHS = {"Debole", "Debole", "Media", "Buona", "Forte"};
    private static final long WIFI_SCAN_MS = 3000;
    private static final long WIFI_JOIN_MS = 6000;
    private static final int ROW_ROOM = 14;
    private static final String STANDARDS = "Standard audio e video";
    private static final String[] TABLE_COLUMNS = {"Stick", "TV", "In uso"};
    private static final int[] TABLE_WIDTHS = {96, 96, 112};
    private static final int TABLE_ROW = 34;
    private static final int TABLE_PADDING = 14;

    /** One of Salotto's own screensavers, in the list after Fire OS's. Without a service it is still to come. */
    private static final class Saver {
        final String name;
        final String text;
        final int preview;
        final Class<? extends DreamService> service;

        Saver(String name, String text, int preview, Class<? extends DreamService> service) {
            this.name = name;
            this.text = text;
            this.preview = preview;
            this.service = service;
        }
    }

    private static final Saver[] SAVERS = {new Saver("Salvaschermo di Salotto", "Spegne quello di Fire OS.", R.drawable.ic_photo, null)};

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
        Level(Runnable show) { this.show = show; }
    }

    /** Opens directly on a section, for the control panel. */
    static final String SECTION = "section";
    static final String NETWORK = "network";
    static final String REMOTES = "remotes";
    static final String APP = "app";
    static final String PACKAGE = "package";

    private final Deque<Level> levels = new ArrayDeque<>();
    private LinearLayout root;
    private TextView heading;
    private ImageView picture;
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
        fire = new FireSettings(this);
        DeviceInfo.prepare();
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        Look.show(this, root::setBackground);
        root.setPadding(dp(MARGIN), 0, dp(MARGIN), 0);

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
        hideFromFixer(scroll);
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
        for (TextView name : addColumns(tableHead, TABLE_COLUMNS, 12)) name.setAlpha(0.6f);
        list.addView(tableHead, new LinearLayout.LayoutParams(-1, -2));
        list.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        panes.addView(list, new LinearLayout.LayoutParams(0, -1, 55));
        setContentView(root);
        // A mouse click puts the window in touch mode, which drops the selection: take it back.
        root.getViewTreeObserver().addOnTouchModeChangeListener(touching -> {
            if (touching) rows.post(this::selectCurrent);
        });
        // From the control panel or an app card, Back returns there and not to the settings.
        String section = getIntent().getStringExtra(SECTION);
        if (NETWORK.equals(section)) enter(this::showNetwork);
        else if (REMOTES.equals(section)) enter(this::showRemotes);
        else if (APP.equals(section)) enter(() -> showApp(getIntent().getStringExtra(PACKAGE)));
        else enter(this::showRoot);
    }

    /** Coming back from an app or a page elsewhere, what is listed here may have changed. The first time it is fresh. */
    @Override protected void onResume() {
        super.onResume();
        if (resumedOnce) refresh();
        resumedOnce = true;
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
        if (levels.isEmpty()) finish();
        else refresh();
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
        explanation.setTextSize(table ? 16 : 21);
        rows.removeAllViews();
        for (Entry entry : entries) {
            rows.addView(table ? tableRow(entry) : row(entry), table ? tableRowSize(entry) : rowSize());
        }
        if (note != null) {
            TextView line = label(note, 16);
            line.setAlpha(0.75f);
            line.setPadding(dp(20), dp(8), dp(16), 0);
            rows.addView(line, new LinearLayout.LayoutParams(-1, -2));
        }
        int focus = levels.peek().focus;
        if (focus == 0) scroll.scrollTo(0, 0);
        selectRow(focus);
    }

    /** A plain requestFocus fails after a touch, leaving no row selected and the old text on the left. */
    private void selectRow(int index) {
        rows.getChildAt(Math.min(index, rows.getChildCount() - 1)).requestFocusFromTouch();
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
                explanation.setText(Brand.mark(this, entry.text));
            }
        });
    }

    private View row(Entry entry) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(20), 0, dp(16), 0);
        TextView title = label(entry.title, 20);
        if (entry.family != null) title.setTypeface(Typeface.create(entry.family, Typeface.NORMAL));
        title.setSingleLine(true);
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
        show("Impostazioni", Arrays.asList(
            new Entry("Aspetto", Look.backdrop(this), getDrawable(R.drawable.ic_s_palette),
                "Sfondo, griglia e nomi delle app sulla Home.", true, () -> enter(this::showLook)),
            new Entry("Orologio e data", clockValue(), getDrawable(R.drawable.ic_clock),
                "Carattere, dimensione e formato dell’ora, e la data sotto l’orologio.",
                true, () -> enter(this::showClock)),
            new Entry("Meteo", weatherValue(), getDrawable(R.drawable.ic_cloud),
                "Temperatura e tempo sulla Home, per il luogo che scegli. Salotto usa Internet solo per questo e per gli aggiornamenti.",
                true, () -> enter(this::showWeather)),
            new Entry("App", null, getDrawable(R.drawable.ic_apps),
                "Tutte le app installate, l’ordine della Home e la gestione di Fire TV.",
                true, () -> enter(this::showApps)),
            new Entry("Schermo e audio", saverValue(), getDrawable(R.drawable.ic_s_tv),
                "Salvaschermo, spegnimento automatico, collegamento con la TV, video e audio.",
                true, () -> enter(this::showScreen)),
            new Entry("Rete", null, getDrawable(R.drawable.ic_s_language),
                "Stato della connessione e impostazioni della rete.", true, () -> enter(this::showNetwork)),
            new Entry("Telecomandi e Bluetooth", null, getDrawable(R.drawable.ic_s_settings_remote),
                "Telecomandi, controller e dispositivi Bluetooth.",
                true, () -> enter(this::showRemotes)),
            new Entry("Dispositivo", null, getDrawable(R.drawable.ic_s_developer_board),
                "Modello, sistema, memoria e spazio disponibile.", true, () -> enter(this::showDevice)),
            new Entry("Privacy", null, getDrawable(R.drawable.ic_s_privacy),
                "Tracciamento pubblicitario e dati di utilizzo.", true, () -> enter(this::showPrivacy)),
            new Entry("Backup", null, getDrawable(R.drawable.ic_s_backup),
                "Salva e ripristina le scelte di Salotto.", true, () -> enter(this::showBackup)),
            new Entry("Strumenti", null, getDrawable(R.drawable.ic_s_build),
                "AirPlay e FireStick Fixer.", true, () -> enter(this::showTools)),
            new Entry("Aggiornamento", BuildVersion.name(this), getDrawable(R.drawable.ic_s_update),
                "Cerca una nuova versione di Salotto. Controlla tutto prima di toccare qualcosa.",
                false, () -> open(new Intent(this, UpdateActivity.class))),
            new Entry("Informazioni su Salotto", BuildVersion.name(this), getDrawable(R.drawable.ic_s_info),
                "Il progetto, la licenza e il software di altri che usa.", true,
                () -> open(new Intent(this, AboutActivity.class))),
            new Entry("Impostazioni Fire TV", null, getDrawable(R.drawable.ic_s_open),
                "Ciò che Salotto non può fare: account Amazon, controllo genitori, nuovo telecomando Alexa, opzioni del salvaschermo Amazon.",
                false, () -> {
                    TaskService.wantFireSettings();
                    open(fireOsSettings());
                })));
    }

    private String clockValue() {
        return Look.clock(this)
            ? Look.FONT_NAMES[Look.font(this)] + ", " + Look.SIZE_NAMES[Look.size(this)].toLowerCase() : "Nascosto";
    }

    private void showLook() {
        show("Aspetto", Arrays.asList(
            new Entry("Sfondo", Look.backdrop(this), getDrawable(R.drawable.ic_s_wallpaper),
                "Lo sfondo della Home. Resta quello che scegli tu; solo gli sfondi meteo cambiano da soli, con il tempo e l’ora.",
                true, () -> enter(this::showBackdrops)),
            new Entry("Griglia", Look.columns(this) + " app per fila", getDrawable(R.drawable.ic_s_grid),
                "Quante app stanno in ogni fila. La prima fila è il dock.",
                true, () -> enter(this::showColumns)),
            new Entry("Nomi delle app", Look.NAME_MODES[Look.names(this)], getDrawable(R.drawable.ic_s_label),
                "Quando mostrare il nome sotto le card. Nel dock non compare mai.",
                true, () -> enter(this::showNames))));
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

    private static String minutes(int count) { return count + (count == 1 ? " minuto" : " minuti"); }

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

    private void showNames() {
        show("Nomi delle app", choices(Look.NAME_MODES, Look.names(this), R.drawable.ic_apps,
            "Quando mostrare il nome sotto le card. Nel dock non compare mai.", index -> {
                Look.setNames(this, index);
                refresh();
            }));
    }

    private void showApps() {
        show("App", Arrays.asList(
            new Entry("Tutte le app", null, getDrawable(R.drawable.ic_apps),
                "Tenendo premuto OK su un’app puoi aggiungerla alla Home, vederne i dettagli o disinstallarla.",
                false, () -> open(new Intent(this, AppsActivity.class))),
            new Entry("Task manager", null, getDrawable(R.drawable.ic_tasks),
                "Le app usate da quando hai acceso la Stick, da riaprire o chiudere.",
                false, () -> open(new Intent(this, TasksActivity.class))),
            new Entry("Tasti tenuti premuti", TaskService.keysOn(this) ? YES : NO, getDrawable(R.drawable.ic_s_menu),
                "Indietro apre il task manager, Home il centro di controllo, Menu le Impostazioni. "
                    + "Per Home e per chiudere le app con un clic serve anche una conferma una tantum sulla TV.",
                false, () -> changeSystem(() -> TaskService.setKeys(this, !TaskService.keysOn(this)))),
            new Entry("Ordina la Home dalla A alla Z", null, getDrawable(R.drawable.ic_s_sort),
                "Rimette le app della Home in ordine alfabetico.", false, () -> {
                    HomeApps.sort(this, HomeApps.load(this));
                    say("Home in ordine alfabetico");
                }),
            new Entry("Gestione app", null, getDrawable(R.drawable.ic_s_widgets),
                "Spazio occupato, chiusura e disinstallazione di ogni app.",
                true, () -> enter(this::showManageApps))));
    }

    private void showManageApps() {
        List<Entry> entries = new ArrayList<>();
        for (HomeApps.App app : HomeApps.installed(this)) {
            Entry entry = new Entry(app.label, null, null, "Spazio occupato, chiusura e disinstallazione.",
                true, () -> enter(() -> showApp(app.pkg)));
            entry.pkg = app.pkg;
            entries.add(entry);
        }
        show("Gestione app", entries);
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
        String text = "Le informazioni di questa app.";
        entries.add(new Entry("Versione", appVersion(pkg), picture, text, false, () -> { }));
        entries.add(new Entry("App", sizeText(size, StorageStats::getAppBytes), picture, text, false, () -> { }));
        entries.add(new Entry("Dati", sizeText(size, StorageStats::getDataBytes), picture, text, false, () -> { }));
        entries.add(new Entry("Cache", sizeText(size, StorageStats::getCacheBytes), picture, text, false, () -> { }));
        if (launchIntent(pkg) != null) {
            entries.add(new Entry("Apri", null, picture, "Apre l’app.", false, () -> openPackage(pkg)));
        }
        entries.add(new Entry("Chiudi", null, picture, "Ferma l’app. Si riapre normalmente quando la lanci.", false,
            () -> TaskService.stop(pkg, stopped -> say(stopped ? "App chiusa" : "Non è stato possibile chiuderla"))));
        entries.add(new Entry("Cancella i dati", null, picture, "Riporta l’app come appena installata: accessi e scelte si perdono.",
            false, () -> confirm("Cancellare i dati di " + HomeApps.label(this, pkg) + "?",
                "Accessi e scelte di questa app si perdono.", "Cancella",
                () -> shell("pm clear " + pkg, output -> {
                    sayLong(output != null && output.contains("Success") ? "Dati cancellati"
                        : "Fire OS non permette di cancellarli");
                    refreshIf(level);
                }))));
        entries.add(new Entry("Disinstalla", null, picture, "Toglie l’app dalla Stick.", false, () -> {
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
        show("Schermo e audio", Arrays.asList(
            new Entry("Salvaschermo", saverValue(), getDrawable(R.drawable.ic_image),
                "Se attivarlo e dopo quanto tempo.", true, () -> enter(this::showSaver)),
            new Entry("Spegnimento automatico", sleepMinutes() + " minuti", getDrawable(R.drawable.ic_timer),
                "Dopo quanto la Stick va in standby se non la usi.", true, () -> enter(this::showSleepTimes)),
            new Entry("Controllo della TV (HDMI-CEC)", cec ? YES : NO, getDrawable(R.drawable.ic_s_hdmi),
                "Permette alla Stick e alla TV di comandarsi a vicenda attraverso il cavo HDMI.", false,
                () -> changeSystem(() -> Settings.Global.putInt(getContentResolver(), CEC_ENABLED, cec ? 0 : 1))),
            new Entry("Spegni la TV con la Stick", tvOff ? YES : NO, getDrawable(R.drawable.ic_power),
                "Quando la Stick va in standby, spegne anche la TV. Funziona se la TV supporta HDMI-CEC.", false,
                () -> changeSystem(() -> Settings.Global.putInt(getContentResolver(), CEC_TV_OFF, tvOff ? 0 : 1))),
            new Entry("Suoni dell’interfaccia", clicks ? YES : NO, getDrawable(R.drawable.ic_s_music),
                "Il suono dei tasti mentre ti muovi nei menu.", false,
                () -> changeSystem(() -> Settings.System.putInt(getContentResolver(),
                    Settings.System.SOUND_EFFECTS_ENABLED, clicks ? 0 : 1))),
            new Entry("Animazioni veloci", quick ? YES : NO, getDrawable(R.drawable.ic_s_speed),
                "Dimezza la durata delle transizioni tra una schermata e l’altra.", false,
                () -> changeSystem(() -> {
                    float scale = quick ? 1 : QUICK_SCALE;
                    Settings.Global.putFloat(getContentResolver(), Settings.Global.WINDOW_ANIMATION_SCALE, scale);
                    Settings.Global.putFloat(getContentResolver(), Settings.Global.TRANSITION_ANIMATION_SCALE, scale);
                })),
            new Entry("Video", null, getDrawable(R.drawable.ic_s_movie), "Risoluzione, HDR, colore e qualità dei video.", true, () -> enter(this::showVideo)),
            new Entry("Audio", null, getDrawable(R.drawable.ic_s_volume), "Audio surround e volume uniforme.", true, () -> enter(this::showAudio)),
            new Entry("Standard audio e video", null, getDrawable(R.drawable.ic_s_standards), "Cosa sanno fare la Stick e la TV, e cosa è in uso adesso.",
                true, () -> enter(this::showStandards))));
    }

    /** Read again at each visit, keeping the row: what it shows changes with the TV and the choices. */
    private void showStandards() {
        Level level = levels.peek();
        int focus = level.focus;
        showWaiting(STANDARDS);
        Background.run(() -> {
            AvStandards.Chosen chosen = new AvStandards.Chosen();
            chosen.resolution = nameOf(RESOLUTIONS, RESOLUTION_NAMES, fire.read(FireSettings.PICTURE, RESOLUTION));
            chosen.range = nameOf(RANGES, RANGE_NAMES, fire.read(FireSettings.PICTURE, RANGE));
            chosen.colorFormat = nameOf(COLOR_FORMATS, COLOR_FORMAT_NAMES, fire.read(FireSettings.PICTURE, COLOR_FORMAT));
            chosen.colorDepth = nameOf(COLOR_DEPTHS, COLOR_DEPTH_NAMES, fire.read(FireSettings.PICTURE, COLOR_DEPTH));
            chosen.surround = nameOf(SURROUNDS, SURROUND_NAMES, surroundNow());
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
                show(STANDARDS, entries);
            });
        });
    }

    private String nameOf(String[] actions, String[] names, String value) {
        int index = Arrays.asList(actions).indexOf(value);
        return index >= 0 ? names[index] : "—";
    }

    /** The only row of this page takes the focus: the place in the real page has to survive it. */
    private void showWaiting(String title) {
        int focus = levels.peek().focus;
        show(title, Arrays.asList(new Entry("Leggo le impostazioni…", null, getDrawable(R.drawable.ic_image),
            "Serve un attimo.", false, () -> { })));
        levels.peek().focus = focus;
    }

    private void showVideo() {
        Level level = levels.peek();
        showWaiting("Video");
        Background.run(() -> {
            String resolution = fire.read(FireSettings.PICTURE, RESOLUTION);
            String range = fire.read(FireSettings.PICTURE, RANGE);
            String format = fire.read(FireSettings.PICTURE, COLOR_FORMAT);
            String depth = fire.read(FireSettings.PICTURE, COLOR_DEPTH);
            runOnUiThread(() -> {
                if (levels.peek() != level) return;
                Drawable picture = getDrawable(R.drawable.ic_image);
                List<Entry> entries = new ArrayList<>();
                entries.add(new Entry("Risoluzione", nameOf(RESOLUTIONS, RESOLUTION_NAMES, resolution), getDrawable(R.drawable.ic_s_aspect),
                    RESOLUTION_TEXT, true, () -> enter(() -> showResolutions(resolution))));
                entries.add(new Entry("HDR", nameOf(RANGES, RANGE_NAMES, range), getDrawable(R.drawable.ic_s_hdr),
                    "Quando usare l’alta gamma dinamica.", true,
                    () -> enter(() -> showChoices("HDR", FireSettings.PICTURE, RANGE, RANGES, RANGE_NAMES, range))));
                entries.add(new Entry("Formato colore", nameOf(COLOR_FORMATS, COLOR_FORMAT_NAMES, format), getDrawable(R.drawable.ic_s_palette),
                    "Lascia Automatico, se la TV non mostra colori sbagliati.", true,
                    () -> enter(() -> showChoices("Formato colore", FireSettings.PICTURE, COLOR_FORMAT, COLOR_FORMATS, COLOR_FORMAT_NAMES, format))));
                if (depth != null && !depth.isEmpty()) {
                    entries.add(new Entry("Profondità colore", nameOf(COLOR_DEPTHS, COLOR_DEPTH_NAMES, depth), getDrawable(R.drawable.ic_s_gradient),
                        "Più bit, sfumature più morbide, se la TV li supporta.", true,
                        () -> enter(() -> showChoices("Profondità colore", FireSettings.PICTURE, COLOR_DEPTH, COLOR_DEPTHS, COLOR_DEPTH_NAMES, depth))));
                }
                entries.add(new Entry("Qualità video", VIDEO_QUALITY_NAMES[videoQuality()], getDrawable(R.drawable.ic_s_quality),
                    "La qualità dei video di Amazon: più è alta, più dati consuma.", true, () -> enter(this::showVideoQuality)));
                show("Video", entries);
            });
        });
    }

    private void showAudio() {
        Level level = levels.peek();
        showWaiting("Audio");
        Background.run(() -> {
            String leveler = fire.read(FireSettings.AUDIO, LEVELER);
            String dialogue = fire.read(FireSettings.AUDIO, DIALOGUE);
            runOnUiThread(() -> {
                if (levels.peek() != level) return;
                Drawable picture = getDrawable(R.drawable.ic_image);
                boolean even = "true".equals(leveler);
                boolean clear = "true".equals(dialogue);
                String surround = surroundNow();
                show("Audio", Arrays.asList(
                    new Entry("Audio surround", nameOf(SURROUNDS, SURROUND_NAMES, surround), getDrawable(R.drawable.ic_s_surround),
                        "Migliore disponibile va bene quasi sempre. Stereo, se soundbar o TV non hanno audio.", true,
                        () -> enter(() -> showChoices("Audio surround", FireSettings.AUDIO, SURROUND, SURROUNDS, SURROUND_NAMES, surround))),
                    new Entry("Dialoghi più chiari", clear ? YES : NO, getDrawable(R.drawable.ic_s_voice),
                        "Fa risaltare le voci su musica ed effetti. È già il filtro di Fire OS, non un secondo effetto; se TV o soundbar ne hanno uno, tienilo spento.", false,
                        () -> writeFire(FireSettings.AUDIO, DIALOGUE, clear ? "false" : "true", () -> refreshIf(level))),
                    new Entry("Volume uniforme", even ? YES : NO, getDrawable(R.drawable.ic_s_equalizer),
                        "Riduce i rumori forti e gli sbalzi di volume tra programmi e pubblicità.", false,
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
                if (!written) sayLong("Fire OS non ha accettato la scelta");
                done.run();
            });
        });
    }

    private void showChoices(String title, String provider, String id, String[] actions, String[] names, String current) {
        Level level = levels.peek();
        show(title, choices(names, Arrays.asList(actions).indexOf(current), R.drawable.ic_image,
            "Se la TV non lo supporta, Fire OS lo rifiuta.",
            index -> writeFire(provider, id, actions[index], () -> backFrom(level))));
    }

    private void showResolutions(String current) {
        show("Risoluzione", choices(RESOLUTION_NAMES, Arrays.asList(RESOLUTIONS).indexOf(current), R.drawable.ic_image,
            RESOLUTION_TEXT, index -> {
                if (!RESOLUTIONS[index].equals(current)) changeResolution(current, RESOLUTIONS[index]);
            }));
    }

    /** Fire OS does not go back by itself, and with no picture nobody could reach the setting. */
    private void changeResolution(String old, String next) {
        String previous = old != null ? old : RESOLUTIONS[0];
        writeFire(FireSettings.PICTURE, RESOLUTION, next, () -> {
            int[] left = {RESOLUTION_WAIT_S};
            AlertDialog dialog = dialog()
                .setTitle("Si vede bene?").setMessage("Torno alla risoluzione precedente tra " + left[0] + " secondi.")
                .setCancelable(false)
                .setNegativeButton("Torna indietro", null)
                .setPositiveButton("Mantieni", null)
                .create();
            Runnable[] count = new Runnable[1];
            Runnable goBack = () -> {
                rows.removeCallbacks(count[0]);
                dialog.dismiss();
                writeFire(FireSettings.PICTURE, RESOLUTION, previous, this::onBackPressed);
            };
            count[0] = () -> {
                if (--left[0] <= 0) {
                    goBack.run();
                    return;
                }
                dialog.setMessage("Torno alla risoluzione precedente tra " + left[0] + " secondi.");
                rows.postDelayed(count[0], 1000);
            };
            dialog.setOnShowListener(shown -> {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
                    rows.removeCallbacks(count[0]);
                    dialog.dismiss();
                    onBackPressed();
                });
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(button -> goBack.run());
            });
            dialog.show();
            rows.postDelayed(count[0], 1000);
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
        for (int index = 0; index <= last; index++) names[index] = VIDEO_QUALITY_NAMES[last - index];
        show("Qualità video", choices(names, last - videoQuality(), R.drawable.ic_image,
            "La qualità dei video di Amazon: più è alta, più dati consuma.",
            index -> changeSystem(() -> Settings.Global.putInt(getContentResolver(), VIDEO_QUALITY, last - index))));
    }

    @SuppressLint("MissingPermission")
    private void showRemotes() {
        Drawable picture = getDrawable(R.drawable.ic_bluetooth);
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        List<Entry> entries = new ArrayList<>();
        if (adapter == null || !adapter.isEnabled()) {
            entries.add(new Entry("Bluetooth spento", null, picture, "Il Bluetooth non è attivo.", false, () -> { }));
            show("Bluetooth", entries);
            return;
        }
        entries.add(new Entry("Cerca dispositivi", null, picture,
            "Metti il dispositivo in associazione e aspetta che compaia. Per un nuovo telecomando Alexa serve la pagina Amazon.",
            true, () -> enter(this::showBluetoothSearch)));
        for (BluetoothDevice device : adapter.getBondedDevices()) {
            String name = device.getName() != null ? device.getName() : device.getAddress();
            entries.add(new Entry(name, Fire.connected(device) ? "Collegato" : "Associato", picture, "Premi OK per dimenticarlo.", false,
                () -> forgetBluetooth(device, name)));
        }
        show("Bluetooth", entries);
    }

    /** Hidden in the SDK, though open to apps. The remote in use may be the one being removed. */
    private void forgetBluetooth(BluetoothDevice device, String name) {
        confirm("Dimenticare " + name + "?", "Se è il telecomando che stai usando, smetterà di funzionare finché non lo associ di nuovo.",
            "Dimentica", () -> {
                try {
                    device.getClass().getMethod("removeBond").invoke(device);
                } catch (ReflectiveOperationException e) {
                    sayLong("Fire OS non lo permette");
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
                entries.add(new Entry(name, null, picture, "Premi OK per associarlo.", false, () -> {
                    BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
                    sayLong(device.createBond() ? "Associo " + name + "…" : "Associazione non riuscita");
                }));
            }
            if (entries.isEmpty()) {
                entries.add(new Entry("Cerco…", null, picture, "Metti il dispositivo in associazione.", false, () -> { }));
            }
            show("Cerca dispositivi", entries);
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
                sayLong("Fire OS non ha accettato la scelta");
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
        show("Privacy", Arrays.asList(
            new Entry("Numero delle notifiche", counted ? YES : NO, getDrawable(R.drawable.ic_s_notify),
                "Mostra nel centro di controllo quante notifiche ci sono. Salotto le conta soltanto, senza leggerne il testo.", false,
                () -> changeCounter(counted)),
            new Entry("Limita il tracciamento pubblicitario", adsLimited ? YES : NO, getDrawable(R.drawable.ic_s_ads),
                "Chiede alle app di non usare la tua attività per la pubblicità.", false,
                () -> changeSystem(() -> Settings.Secure.putInt(getContentResolver(), ADS_LIMITED, adsLimited ? 0 : 1))),
            new Entry("Invia dati di utilizzo", metrics ? YES : NO, getDrawable(R.drawable.ic_s_chart),
                "Le statistiche d’uso che Fire OS manda ad Amazon.", false,
                () -> changeSystem(() -> Settings.Secure.putInt(getContentResolver(), USAGE_METRICS, metrics ? 0 : 1)))));
    }

    private int sleepMinutes() {
        return Settings.Secure.getInt(getContentResolver(), SLEEP_TIMEOUT, SLEEP_DEFAULT) / MINUTE;
    }

    private void showSleepTimes() {
        show("Spegnimento automatico", choices(minuteNames(SLEEP_MINUTES), indexOf(SLEEP_MINUTES, sleepMinutes()),
            R.drawable.ic_image, "Dopo quanto la Stick va in standby se non la usi.",
            index -> changeSystem(() -> Settings.Secure.putInt(getContentResolver(), SLEEP_TIMEOUT,
                SLEEP_MINUTES[index] * MINUTE))));
    }

    private List<Entry> infoRows(List<String[]> rows, Drawable picture, String text) {
        List<Entry> entries = new ArrayList<>();
        for (String[] row : rows) entries.add(new Entry(row[0], row[1], picture, text, false, () -> { }));
        return entries;
    }

    private void showNetwork() {
        Drawable picture = getDrawable(R.drawable.ic_wifi);
        boolean on = getSystemService(WifiManager.class).isWifiEnabled();
        List<Entry> entries = new ArrayList<>();
        entries.add(new Entry("Wi-Fi", on ? YES : NO, picture, "Senza Wi-Fi la Stick non ha Internet.", false,
            () -> setWifi(!on)));
        if (on) {
            entries.add(new Entry("Reti Wi-Fi", null, picture, "Scegli una rete e collegati. Le password restano sulla Stick.",
                true, () -> enter(this::showWifi)));
            entries.addAll(infoRows(DeviceInfo.network(this), picture, "Stato attuale della connessione."));
        }
        show("Rete", entries);
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
            sayLong("Riattivo il Wi-Fi…");
            refreshLater(WIFI_JOIN_MS);
            return;
        }
        String path = ask.getPath();
        String script = "rm -f " + path + "; cmd wifi set-wifi-enabled disabled; sleep 2; settings put global wifi_on 1; "
            + "while [ ! -e " + path + " ]; do sleep 1; done; rm -f " + path + "; cmd wifi set-wifi-enabled enabled";
        confirm("Disattivare il Wi-Fi?", "La Stick resta senza Internet finché non lo riattivi da qui o non la riavvii.",
            "Disattiva", () -> {
                shell("setsid nohup sh -c " + quoted(script) + " > /dev/null 2>&1 &", output -> { });
                refreshLater(WIFI_SCAN_MS * 2);
            });
    }

    private void showWifi() {
        Level level = levels.peek();
        show("Reti Wi-Fi", Arrays.asList(new Entry("Cerco le reti…", null, getDrawable(R.drawable.ic_wifi),
            "Serve qualche secondo.", false, () -> { })));
        withLocation(() -> {
            getSystemService(WifiManager.class).startScan();
            rows.postDelayed(() -> {
                if (levels.peek() == level) showWifiResults();
            }, WIFI_SCAN_MS);
        });
    }

    private String wifiStrength(int rssi) {
        int level = WifiManager.calculateSignalLevel(rssi, WIFI_STRENGTHS.length);
        return WIFI_STRENGTHS[level];
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
            Entry entry = new Entry(name, connected ? "Collegata" : wifiStrength(network.level), picture,
                (connected ? "Premi OK per dimenticare questa rete." : "Premi OK per collegarti.")
                    + "\n\n" + wifiDetails(network, connected), false,
                () -> {
                    if (connected) forgetWifi(name);
                    else joinWifi(name, security);
                });
            entry.locked = !security.equals("open");
            entries.add(entry);
        }
        if (entries.isEmpty()) {
            entries.add(new Entry("Nessuna rete trovata", null, picture,
                "Se l’elenco resta vuoto, Fire OS non ha dato a Salotto il permesso di leggerlo.", false, () -> { }));
        }
        show("Reti Wi-Fi", entries);
    }

    /** Under the icon of the row with the focus. */
    private String wifiDetails(ScanResult network, boolean connected) {
        String security = wifiSecurity(network);
        String details = "Qualità " + wifiStrength(network.level).toLowerCase(Locale.ITALIAN) + " · "
            + (network.frequency >= WIFI_5GHZ ? "5 GHz" : "2,4 GHz") + " · "
            + (security.equals("open") ? "aperta" : security.toUpperCase(Locale.ROOT));
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
            .setTitle("Password di " + name).setView(password)
            .setNegativeButton("Annulla", null)
            .setPositiveButton("Collegati", (d, button) -> connectWifi(name, security, password.getText().toString()))
            .create();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        dialog.show();
        centerButton(dialog);
    }

    private void connectWifi(String name, String security, String password) {
        sayLong("Mi collego a " + name + "…");
        String secret = password.isEmpty() ? "" : " " + quoted(password);
        Level level = levels.peek();
        shell("cmd wifi connect-network " + quoted(name) + " " + security + secret,
            output -> rows.postDelayed(() -> refreshIf(level), WIFI_JOIN_MS));
    }

    private void forgetWifi(String name) {
        Level level = levels.peek();
        confirm("Dimenticare " + name + "?", "La Stick si scollega e per tornare serve di nuovo la password.", "Dimentica",
            () -> shell("cmd wifi list-networks", output -> {
                Matcher match = Pattern.compile("(?m)^(\\d+)\\s+" + Pattern.quote(name) + "\\s").matcher(output == null ? "" : output);
                if (match.find()) shell("cmd wifi forget-network " + match.group(1), done -> refreshIf(level));
                else say("Rete non trovata");
            }));
    }

    private void showDevice() {
        Drawable picture = getDrawable(R.drawable.ic_settings);
        List<Entry> entries = infoRows(DeviceInfo.system(this), picture, "Informazioni su questo dispositivo.");
        entries.add(new Entry("Archiviazione USB", null, getDrawable(R.drawable.ic_s_usb), "Dischi e chiavette collegati alla Stick.",
            true, () -> enter(this::showUsb)));
        entries.add(new Entry("Standby", null, getDrawable(R.drawable.ic_power), "Mette subito la Stick in standby.", false,
            TaskService::standby));
        entries.add(new Entry("Riavvia", null, getDrawable(R.drawable.ic_restart), "Riavvia la Stick. Ci vuole circa un minuto.", false,
            () -> confirm("Riavviare la Stick?", "Le app aperte si chiudono.", "Riavvia",
                () -> {
                    TaskService.restart();
                })));
        show("Dispositivo", entries);
    }

    private void showBackup() {
        Drawable picture = getDrawable(R.drawable.ic_apps);
        show("Backup", Arrays.asList(
            new Entry("Salva le scelte di Salotto", null, getDrawable(R.drawable.ic_s_save),
                "App della Home, aspetto, orologio e meteo finiscono in un file sulla Stick.", false, () -> {
                    try {
                        Backup.save(this);
                        say("Scelte salvate");
                        refresh();
                    } catch (IOException | JSONException e) {
                        sayLong("Salvataggio non riuscito");
                    }
                }),
            new Entry("Ripristina le scelte", Backup.exists(this) ? null : "Nessun backup", getDrawable(R.drawable.ic_s_restore),
                "Riporta Salotto all’ultimo salvataggio.", false, () -> {
                    try {
                        Backup.restore(this);
                        say("Scelte ripristinate");
                        Look.show(this, root::setBackground);
                        refresh();
                    } catch (IOException | JSONException e) {
                        sayLong("Nessun backup da ripristinare");
                    }
                })));
    }

    /** The switch is Fixer's: only the shell may read it and turn it. */
    private void showTools() {
        Level level = levels.peek();
        if (TaskService.ready()) {
            TaskService.shell(Fire.FIXER_COMMAND + "state", output -> {
                boolean on = output != null && output.contains(Fire.DOUBLE_HOME_ON);
                if (on == doubleHome) return;
                doubleHome = on;
                refreshIf(level);
            });
        }
        show("Strumenti", Arrays.asList(
            new Entry("Presentazione iniziale", null, getDrawable(R.drawable.ic_salotto),
                "Rivedi le pagine del primo avvio, con la spiegazione del telecomando.", false,
                () -> open(new Intent(this, FirstRunActivity.class))),
            new Entry("AirPlay", null, appIcon(Fire.AIRPLAY),
                "Ricevi video da iPhone, iPad e Mac. Dopo ogni riavvio va aperto una volta.",
                false, () -> openPackage(Fire.AIRPLAY)),
            new Entry("FireStick Fixer", null, appIcon(Fire.FIXER),
                "Porta il tasto Home e l’accensione della Stick su Salotto.",
                false, () -> openPackage(Fire.FIXER)),
            new Entry("Home Fire TV con doppio Home", doubleHome ? YES : NO, getDrawable(R.drawable.ic_s_home),
                "Premendo Home due volte si apre la Home di Fire TV, per esempio per cambiare profilo Amazon.",
                false, () -> shell(Fire.FIXER_COMMAND + "set doubleTap " + !doubleHome, output -> {
                    doubleHome = !doubleHome;
                    TaskService.rememberDoubleHome(this, doubleHome);
                    refreshIf(level);
                }))));
    }

    /** "public:8,1 mounted 1234-ABCD": one line for each disk. */
    private void showUsb() {
        Level level = levels.peek();
        Drawable picture = getDrawable(R.drawable.ic_settings);
        show("Archiviazione USB", Arrays.asList(new Entry("Controllo…", null, picture, "Cerco dischi e chiavette.", false, () -> { })));
        shell("sm list-volumes public", output -> {
            if (levels.peek() != level) return;
            List<Entry> entries = new ArrayList<>();
            for (String line : (output == null ? "" : output).split("\n")) {
                String[] parts = line.trim().split(" ");
                if (parts.length < 2 || !parts[0].startsWith("public:")) continue;
                boolean mounted = parts[1].equals("mounted");
                String name = parts.length > 2 && !parts[2].equals("null") ? parts[2] : parts[0];
                entries.add(new Entry(name, mounted ? "Collegato" : "Non collegato", picture,
                    mounted ? "Premi OK per scollegarlo in sicurezza prima di toglierlo." : "Si può togliere.", false, () -> {
                        if (mounted) shell("sm unmount " + parts[0], done -> refreshIf(level));
                    }));
            }
            if (entries.isEmpty()) entries.add(new Entry("Nessun disco collegato", null, picture, "Collega un disco o una chiavetta.", false, () -> { }));
            show("Archiviazione USB", entries);
        });
    }

    /** ACTION_SETTINGS passes through Amazon's home activity, which Fixer redirects back here. */
    private Intent fireOsSettings() {
        return new Intent().setClassName(Fire.AMAZON_HOME, Fire.SETTINGS_PAGE);
    }

    private Entry weatherBackdrop(String name, int picture, String text) {
        boolean ready = Weather.active(this);
        return new Entry(name, name.equals(Look.backdrop(this)) ? CHECK : ready ? null : "Scegli il luogo",
            getDrawable(picture), text + " Si attiva solo con un luogo impostato nel Meteo.", false, () -> {
                if (!ready) {
                    sayLong("Scegli prima il luogo nel Meteo");
                    return;
                }
                Look.setBackdrop(this, name);
                refresh();
            });
    }

    private void showBackdrops() {
        List<String> names = new ArrayList<>(Arrays.asList(Look.PRESET_NAMES));
        int presets = names.size();
        for (File image : Look.images(this)) names.add(image.getName());
        String chosen = Look.backdrop(this);
        List<Entry> entries = new ArrayList<>();
        entries.add(weatherBackdrop(Look.WEATHER, R.drawable.ic_cloud,
            "Cielo, nuvole, pioggia e neve seguono l’ora e il meteo del luogo scelto, con un’animazione leggera."));
        entries.add(weatherBackdrop(Look.PICTURES, R.drawable.ic_image,
            "Un’illustrazione diversa per ogni ora del giorno e per ogni meteo del luogo scelto, sempre ferma."));
        entries.add(weatherBackdrop(Look.PHOTOS, R.drawable.ic_photo,
            "Una fotografia diversa per ogni momento del giorno e per ogni meteo del luogo scelto, sempre ferma."));
        for (int index = 0; index < names.size(); index++) {
            String name = names.get(index);
            String text = index < presets ? "Sfumatura di Salotto." : "Immagine personale, adattata allo schermo.";
            entries.add(new Entry(name, name.equals(chosen) ? CHECK : null, getDrawable(R.drawable.ic_image), text, false, () -> {
                Look.setBackdrop(this, name);
                Look.show(this, root::setBackground);
                refresh();
            }));
        }
        show("Sfondo", entries);
    }

    private void showClock() {
        boolean visible = Look.clock(this);
        show("Orologio", Arrays.asList(
            new Entry("Mostra sulla Home", visible ? YES : NO, getDrawable(R.drawable.ic_clock),
                "L’orario resta comunque visibile nella pillola in alto a destra.", false, () -> {
                    Look.setClock(this, !visible);
                    refresh();
                }),
            new Entry("Carattere", Look.FONT_NAMES[Look.font(this)], getDrawable(R.drawable.ic_clock),
                "Lo stile delle cifre dell’orologio.", true, () -> enter(this::showFonts)),
            new Entry("Dimensione", Look.SIZE_NAMES[Look.size(this)], getDrawable(R.drawable.ic_clock),
                "Quanto spazio occupa l’orologio sopra il dock.", true, () -> enter(this::showSizes)),
            new Entry("Formato", Look.hours24(this) ? "24 ore" : "12 ore", getDrawable(R.drawable.ic_clock),
                "Vale anche per l’ora nella pillola.", false, () -> {
                    Look.setHours24(this, !Look.hours24(this));
                    refresh();
                }),
            new Entry("Secondi", Look.seconds(this) ? YES : NO, getDrawable(R.drawable.ic_clock),
                "Mostra anche i secondi.", false, () -> {
                    Look.setSeconds(this, !Look.seconds(this));
                    refresh();
                }),
            new Entry("Data", Look.date(this) ? YES : NO, getDrawable(R.drawable.ic_clock),
                "Mostra la data sotto l’orologio.", false, () -> {
                    Look.setDate(this, !Look.date(this));
                    refresh();
                })));
    }

    private String weatherValue() {
        String place = Weather.place(this);
        return !Weather.enabled(this) ? "Spento" : place != null ? place : "Scegli il luogo";
    }

    private void showWeather() {
        boolean enabled = Weather.enabled(this);
        String place = Weather.place(this);
        show("Meteo", Arrays.asList(
            new Entry("Mostra sulla Home", enabled ? YES : NO, getDrawable(R.drawable.ic_cloud),
                "Quando è spento Salotto non chiede il meteo a Internet.", false, () -> {
                    Weather.setEnabled(this, !enabled);
                    refresh();
                }),
            new Entry("Luogo", place != null ? place : "Da scegliere", getDrawable(R.drawable.ic_cloud),
                "Scrivi il nome di una città. A Open-Meteo arrivano solo il nome cercato e le sue coordinate.",
                true, this::askPlace),
            new Entry("Unità", Weather.fahrenheit(this) ? "°F" : "°C", getDrawable(R.drawable.ic_cloud),
                "Gradi Celsius o Fahrenheit.", false, () -> {
                    Weather.setFahrenheit(this, !Weather.fahrenheit(this));
                    refresh();
                })));
    }

    private void askPlace() {
        EditText name = new EditText(this);
        name.setSingleLine(true);
        name.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        AlertDialog dialog = dialog()
            .setTitle("Nome della città").setView(name)
            .setPositiveButton("Cerca", (d, button) -> searchPlace(name.getText().toString().trim()))
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
                List<Weather.Place> places = Weather.search(name);
                runOnUiThread(() -> {
                    if (places.isEmpty()) say("Nessun luogo trovato");
                    else enter(() -> showPlaces(places));
                });
            } catch (IOException | JSONException e) {
                runOnUiThread(() -> say("Connessione non riuscita"));
            }
        });
    }

    private void showPlaces(List<Weather.Place> places) {
        List<Entry> entries = new ArrayList<>();
        for (Weather.Place place : places) {
            entries.add(new Entry(place.name, place.detail, getDrawable(R.drawable.ic_cloud),
                "Scegli questo luogo per il meteo sulla Home.", false, () -> {
                    Weather.setPlace(this, place);
                    onBackPressed();
                }));
        }
        show("Scegli il luogo", entries);
    }

    private boolean saverOn() {
        return Settings.Secure.getInt(getContentResolver(), SAVER_ENABLED, 1) == 1;
    }

    private int saverMinutes() {
        return Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, SAVER_DEFAULT) / MINUTE;
    }

    private String saverValue() {
        return saverOn() ? minutes(saverMinutes()) : "Spento";
    }

    /** Needs WRITE_SECURE_SETTINGS, which only adb can grant: without it the values stay readable. */
    private void changeSystem(Runnable change) {
        try {
            change.run();
            refresh();
        } catch (SecurityException e) {
            sayLong("Manca il permesso di sistema per Salotto");
        }
    }

    private boolean ownSaver() {
        String chosen = Settings.Secure.getString(getContentResolver(), SAVER_CHOSEN);
        return chosen != null && chosen.startsWith(getPackageName() + "/");
    }

    private void showSaver() {
        boolean on = saverOn();
        boolean own = ownSaver();
        Drawable picture = getDrawable(R.drawable.ic_image);
        show("Salvaschermo", Arrays.asList(
            new Entry("Attivo", on ? YES : NO, picture,
                "Il salvaschermo parte quando la Stick resta ferma.", false,
                () -> changeSystem(() -> Settings.Secure.putInt(getContentResolver(), SAVER_ENABLED, on ? 0 : 1))),
            new Entry("Tipo", own ? "Salotto" : "Fire OS", getDrawable(R.drawable.ic_photo),
                "Quale salvaschermo usare.", true, () -> enter(this::showSaverKinds)),
            new Entry("Si avvia dopo", minutes(saverMinutes()), picture, saverWait(), true, () -> enter(this::showSaverTimes)),
            new Entry("Avvia adesso", null, picture, "Fa partire subito il salvaschermo. Si chiude con Indietro.", false,
                () -> open(Fire.screensaver()))));
    }

    /** Only one screensaver runs at a time: choosing ours is what turns off Fire OS's. */
    private void showSaverKinds() {
        boolean own = ownSaver();
        List<Entry> entries = new ArrayList<>();
        entries.add(new Entry("Deciso da Fire OS", own ? null : CHECK, getDrawable(R.drawable.ic_image),
            "Il salvaschermo di Amazon, con le sue immagini.", false, () -> chooseSaver(Fire.AMAZON_SAVER)));
        if (!own) {
            entries.add(new Entry("Scegli", null, getDrawable(R.drawable.ic_s_open),
                "Apre il menu di Fire OS per scegliere tra i suoi salvaschermi: foto, stile, velocità. Il tempo di avvio che conta è quello scelto qui in Salotto.", true, () -> open(Fire.saverMenu())));
        }
        for (Saver saver : SAVERS) entries.add(saverEntry(saver));
        show("Tipo", entries);
    }

    private Entry saverEntry(Saver saver) {
        if (saver.service == null) {
            return new Entry(saver.name, "In arrivo", getDrawable(saver.preview), saver.text, false,
                () -> say("Non è ancora pronto"));
        }
        String name = new ComponentName(this, saver.service).flattenToShortString();
        String chosen = Settings.Secure.getString(getContentResolver(), SAVER_CHOSEN);
        return new Entry(saver.name, name.equals(chosen) ? CHECK : null, getDrawable(saver.preview), saver.text, false,
            () -> chooseSaver(name));
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
        if (saverInTime(saverMinutes())) return "Quanto aspetta la Stick prima di far partire il salvaschermo.";
        return "La Stick si spegne dopo " + minutes(sleepMinutes()) + ": il salvaschermo non farà in tempo a partire. Scegli meno, o allunga lo Spegnimento automatico.";
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
        show("Si avvia dopo", entries, limited
            ? "L’attuale Spegnimento automatico, " + minutes(sleepMinutes()) + ", limita le opzioni del salvaschermo." : null);
    }

    private void showFonts() {
        List<Entry> entries = choices(Look.FONT_NAMES, Look.font(this), R.drawable.ic_clock,
            "Lo stile delle cifre dell’orologio.", index -> {
                Look.setFont(this, index);
                refresh();
            });
        for (int index = 0; index < entries.size(); index++) entries.get(index).family = Look.FONT_FAMILIES[index];
        show("Carattere", entries);
    }

    private void showSizes() {
        show("Dimensione", choices(Look.SIZE_NAMES, Look.size(this), R.drawable.ic_clock,
            "Quanto spazio occupa l’orologio sopra il dock.", index -> {
                Look.setSize(this, index);
                refresh();
            }));
    }

    private void showColumns() {
        int[] columns = Look.COLUMN_CHOICES;
        String[] names = new String[columns.length];
        for (int index = 0; index < columns.length; index++) names[index] = columns[index] + " app per fila";
        show("Griglia", choices(names, indexOf(columns, Look.columns(this)), R.drawable.ic_apps,
            index -> columns[index] == 6 ? "Come su Apple TV: più app in vista, card un po’ più piccole."
                : "Card più grandi, meno app in ogni fila.", index -> {
                Look.setColumns(this, columns[index]);
                refresh();
            }));
    }
}
