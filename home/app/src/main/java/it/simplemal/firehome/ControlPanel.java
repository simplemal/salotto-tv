package it.simplemal.firehome;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;
import android.text.TextUtils;
import android.view.FocusFinder;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The quick controls under the pill: standby large, connections beside it, modes below, tools at
 * the bottom. A small tile says its name and state under the panel. Everything acts from here: what a normal app may not do goes through TaskService's
 * shell, and nothing opens a Fire OS page. Wi-Fi and Bluetooth only open their pages in Salotto's
 * Settings, on purpose: turning off either one would cut the network or the remote off the Stick.
 */
final class ControlPanel extends LinearLayout {
    private static final int TOP = 76;
    /** Over an app there is no pill to leave room for: the panel takes its place. */
    private static final int TOP_OVER_APP = 22;
    /** Lined up with the side of the Home's dock. */
    private static final int EDGE = 28;
    private static final int UNIT = 44;
    private static final int GAP = 9;
    private static final int PADDING = 10;
    private static final int WIDE = 2 * UNIT + GAP;
    /** On the Home it ends over the weather, with three times the room the weather has over the dock. */
    private static final int HEIGHT = 2 * PADDING + 4 * UNIT + 3 * GAP;
    private static final int WIDTH = 2 * PADDING + 2 * WIDE + GAP;
    private static final int RADIUS = 10;
    private static final int PANEL_RADIUS = 20;
    private static final int BIG_ICON = 56;
    private static final int WIDE_ICON = 24;
    private static final int SMALL_ICON = 28;
    /** Where the number goes in each picture, as a share of its height. */
    private static final float SUN_CENTER = 0.5f;
    private static final float TIMER_CENTER = 0.545f;
    private static final long TIMER_TICK_MS = 5000;
    /** Dark enough to carry white on a bright backdrop. */
    private static final int TILE_OFF = Color.argb(72, 0, 0, 0);
    private static final int TILE_ACTIVE = Color.argb(205, 255, 255, 255);
    /** The icon of a connection in use. */
    static final int LINKED = Color.rgb(125, 205, 255);
    private static final int LEVELS = 4;
    private static final int[] TIMER_MINUTES = {30, 60, 90, 120};
    /** The list of the circular Home: one row per control, icon then name, the state on the right. */
    private static final int LIST_WIDTH = 270;
    private static final int LIST_ROW = 34;
    private static final int LIST_ICON = 22;
    private static final int LIST_VALUE = 110;
    private static final int LIST_LIFT = 12;
    private static final int LIST_FILL = Color.rgb(28, 29, 32);
    /** As bright as the line of the glass panel. */
    private static final int RIM_LIGHT = 235;
    private static final Pattern WIFI_NAME = Pattern.compile("connected to \"(.*)\"");

    private final class Tile {
        final LinearLayout view = new LinearLayout(getContext());
        final ImageView icon = new ImageView(getContext());
        final GradientDrawable fill = new GradientDrawable();
        final TextView title;
        final TextView value;
        boolean active;
        boolean small;
        boolean linked;
        boolean row;

        Tile(int picture, String name) {
            icon.setImageResource(picture);
            title = host.label(name, 11);
            title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            value = host.label("", 9);
            value.setSingleLine(true);
            value.setEllipsize(TextUtils.TruncateAt.END);
            view.setGravity(Gravity.CENTER);
            view.setFocusable(true);
            view.setClickable(true);
            view.setDefaultFocusHighlightEnabled(false);
            view.setContentDescription(name);
            view.setOnFocusChangeListener((changed, focused) -> {
                if (focused) last = view;
                paint();
            });
            tiles.add(this);
            fill.setCornerRadius(host.dp(RADIUS));
            view.setBackground(fill);
            paint();
        }

        void paint() {
            boolean selected = view.isFocused() || view == chosen;
            if (row) {
                // A row is plain until chosen: what is on shows in its icon and in the words on the right.
                fill.setColor(selected ? Color.WHITE : Color.TRANSPARENT);
                icon.setColorFilter(selected ? Color.BLACK : active || linked ? LINKED : Color.WHITE);
                title.setTextColor(selected ? Color.BLACK : Color.WHITE);
                value.setTextColor(selected ? Color.argb(170, 0, 0, 0) : Color.argb(170, 255, 255, 255));
                return;
            }
            boolean lit = selected || active;
            fill.setColor(selected ? Color.WHITE : active ? TILE_ACTIVE : tileOff);
            int ink = lit ? Color.BLACK : Color.WHITE;
            icon.setColorFilter(!lit && linked ? LINKED : ink);
            title.setTextColor(ink);
            value.setTextColor(lit ? Color.BLACK : Color.argb(180, 255, 255, 255));
        }

        void link(boolean on) {
            linked = on;
            paint();
        }

        void show(String text, boolean on) {
            value.setText(text);
            active = on;
            paint();
            if (small && (view.isFocused() || view == chosen)) describe(this);
        }
    }

    /** What the panel needs from a page of Salotto, or from the window it has over an app. */
    interface Host {
        Context context();
        TextView label(String text, int sp);
        int dp(int value);
        void open(Intent intent);
        void shell(String command, Consumer<String> done);
        void say(String text);
        void sayLong(String text);
        void runOnUiThread(Runnable run);
        /** On the Home nothing happens: the panel stays until the focus leaves it. */
        void panelDone();
        /** A page that takes the screen is opening: coming back, the user finds the Home without the panel. */
        default void leave() { }
    }

    private void saver() { away(Fire.screensaver(getContext())); }

    /** The screensaver and the task manager: coming back from them, the panel would only be in the way. */
    private void away(Intent page) {
        host.leave();
        host.open(page);
    }

    private final Host host;
    private final Runnable timerTick = () -> {
        if (isShown()) showTimer();
    };
    private Tile timerTile;
    private FireSettings fire;
    private final View opener;
    private final TextView hint;
    private final List<Runnable> refreshers = new ArrayList<>();
    private final List<Tile> tiles = new ArrayList<>();
    private View first;
    /** The selection when the panel's window has no focus to keep it in. See PanelWindow. */
    private View chosen;
    private Supplier<View> below;
    /** On the circular Home the panel is a list over its button: the arrows stay inside, Back or Down from the bottom leaves. */
    private boolean listed;
    private View line;
    /** Holds the focus when the panel opens with nothing chosen, so that OK does nothing. */
    private View rest;
    private View backTo;
    private boolean backPressed;
    private String wifiName;

    /** Adds the panel and the line under it to `root`: under the pill if the Home shows one. */
    static ControlPanel addTo(Host host, FrameLayout root, View opener, boolean pill) {
        TextView hint = host.label("", 12);
        hint.setPadding(host.dp(10), host.dp(3), host.dp(10), host.dp(3));
        GradientDrawable fill = new GradientDrawable();
        fill.setColor(Color.argb(128, 0, 0, 0));
        fill.setCornerRadius(host.dp(9));
        hint.setBackground(fill);
        hint.setVisibility(GONE);
        int top = pill ? TOP : TOP_OVER_APP;
        FrameLayout line = new FrameLayout(host.context());
        line.addView(hint, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_HORIZONTAL));
        FrameLayout.LayoutParams under = place(host, top + HEIGHT + 6);
        under.width = host.dp(WIDTH);
        root.addView(line, under);
        ControlPanel panel = new ControlPanel(host, opener, hint);
        panel.line = line;
        root.addView(panel, place(host, top));
        return panel;
    }

    private static FrameLayout.LayoutParams place(Host host, int top) {
        FrameLayout.LayoutParams place = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        place.topMargin = host.dp(top);
        place.rightMargin = host.dp(EDGE);
        return place;
    }

    private ControlPanel(Host host, View opener, TextView hint) {
        super(host.context());
        this.host = host;
        this.opener = opener;
        this.hint = hint;
        setOrientation(VERTICAL);
        int room = host.dp(PADDING);
        setPadding(room, room, room, room);
        // In the background: a foreground would stop at the padding, inside the tiles' margin.
        int corner = host.dp(PANEL_RADIUS);
        setBackground(GlassRim.panel(corner, host.dp(1)));
        setClickable(true);
        build();
        rest = new View(getContext());
        rest.setOnFocusChangeListener((view, focused) -> {
            if (!focused) view.setFocusable(false);
        });
        addRest();
    }

    private void addRest() {
        LayoutParams small = new LayoutParams(1, 1);
        small.topMargin = -1;
        addView(rest, small);
    }

    /** The panel floats above the Home: the arrows must not reach the cards underneath. */
    @Override public View focusSearch(View focused, int direction) {
        if (focused == rest) return direction == FOCUS_UP && opener != null && !listed ? opener : first;
        View next = FocusFinder.getInstance().findNextFocus(this, focused, direction);
        if (next != null) return next;
        if (listed) return direction == FOCUS_DOWN && opener != null ? opener : focused;
        View out = direction == FOCUS_UP ? opener : direction == FOCUS_DOWN && below != null ? below.get() : null;
        return out != null ? out : focused;
    }

    /** Opened with Menu held on the Home: nothing chosen, and Back closes it, leaving the focus on `from`. */
    void openEmpty(View from) {
        backTo = from;
        rest.setFocusable(true);
        rest.requestFocus();
    }

    void closed() {
        backTo = null;
        letGoOfFire();
    }

    /** A list over its button on the circular Home, the tiles under the pill on the grid. Built again only on a change. */
    void setListed(boolean list) { setLook(list, false); }

    /** The tiles' size and the room between them: the clean Home's are a little roomier, and closer. */
    private int unit = UNIT;
    private int gap = GAP;
    private boolean cleanLook;
    private static final int CLEAN_UNIT = 48;
    private static final int CLEAN_GAP = 5;
    /**
     * With no panel under them the clean Home's tiles carry the dark themselves, opaque: over the
     * apps, with the first row in the middle or at the top, nothing see-through could be read.
     */
    private static final int CLEAN_TILE_OFF = Color.rgb(30, 31, 34);
    private int tileOff = TILE_OFF;

    private int wide() { return 2 * unit + gap; }

    /**
     * The list over its button, or the tiles under the pill. `clean`: the clean Home's tiles, the
     * grid's ones under its buttons in the middle, on a flat fill with no glass.
     */
    void setLook(boolean list, boolean clean) {
        if (list != listed || clean != cleanLook) {
            listed = list;
            cleanLook = clean;
            unit = clean ? CLEAN_UNIT : UNIT;
            gap = clean ? CLEAN_GAP : GAP;
            tileOff = clean ? CLEAN_TILE_OFF : TILE_OFF;
            // The clean Home's tiles stand on their own: no panel around them.
            int room = clean ? 0 : host.dp(PADDING);
            setPadding(room, room, room, room);
            removeAllViews();
            tiles.clear();
            last = null;
            refreshers.clear();
            chosen = null;
            hint.setVisibility(GONE);
            if (list) buildList();
            else build();
            addRest();
        }
        // The list is one plain colour inside the glass line: no sheen, nothing to blend with what is behind it.
        setBackground(list ? solid() : clean ? null : GlassRim.panel(host.dp(PANEL_RADIUS), host.dp(1)));
        int across = list ? Gravity.START : clean ? Gravity.CENTER_HORIZONTAL : Gravity.END;
        FrameLayout.LayoutParams spot = (FrameLayout.LayoutParams) getLayoutParams();
        spot.gravity = list ? Gravity.BOTTOM | Gravity.START : Gravity.TOP | across;
        spot.topMargin = list ? 0 : host.dp(TOP);
        spot.rightMargin = list || clean ? 0 : host.dp(EDGE);
        spot.leftMargin = 0;
        spot.bottomMargin = 0;
        setLayoutParams(spot);
        // The line of hints under the tiles goes with them.
        FrameLayout.LayoutParams under = (FrameLayout.LayoutParams) line.getLayoutParams();
        under.gravity = Gravity.TOP | across;
        under.rightMargin = list || clean ? 0 : host.dp(EDGE);
        int padding = clean ? 0 : PADDING;
        under.topMargin = host.dp(TOP + 2 * padding + 4 * unit + 3 * gap + 6);
        under.width = host.dp(2 * padding + 2 * wide() + gap);
        line.setLayoutParams(under);
    }


    /** The list stands on the button, centred on it and a little above it, inside the screen. */
    void placeOver(View button) {
        View parent = (View) getParent();
        int[] at = new int[2];
        int[] base = new int[2];
        button.getLocationInWindow(at);
        parent.getLocationInWindow(base);
        measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        FrameLayout.LayoutParams spot = (FrameLayout.LayoutParams) getLayoutParams();
        int left = at[0] - base[0] + button.getWidth() / 2 - getMeasuredWidth() / 2;
        spot.leftMargin = Math.max(host.dp(16), Math.min(left, parent.getWidth() - getMeasuredWidth() - host.dp(16)));
        int buttonTop = at[1] - base[1];
        // Above a button at the bottom, as on the wheel; under one at the top, as on the clean Home.
        if (buttonTop < parent.getHeight() / 2) {
            spot.gravity = Gravity.TOP | Gravity.START;
            spot.topMargin = buttonTop + button.getHeight() + host.dp(LIST_LIFT);
            spot.bottomMargin = 0;
        } else {
            spot.gravity = Gravity.BOTTOM | Gravity.START;
            spot.topMargin = 0;
            spot.bottomMargin = parent.getHeight() - buttonTop + host.dp(LIST_LIFT);
        }
        setLayoutParams(spot);
    }

    boolean listed() { return listed; }
    private View last;

    /** A plain fill instead of the glass, with the same glass line around it as the grid's panel and the buttons. */
    private Drawable solid() {
        int radius = host.dp(PANEL_RADIUS);
        GradientDrawable fill = new GradientDrawable();
        fill.setColor(LIST_FILL);
        fill.setCornerRadius(radius);
        return new LayerDrawable(new Drawable[]{fill, new GlassRim(radius, host.dp(1), RIM_LIGHT)});
    }

    /** On the Home, the last row leads down to the apps. */
    void leadDownTo(Supplier<View> view) { below = view; }

    View first() { return first; }

    /** Where the panel was left: it opens there again, as long as Salotto stays open. */
    View opening() { return last != null && last.getParent() != null ? last : first; }

    void choose(int direction) {
        View next = chosen == null ? first : FocusFinder.getInstance().findNextFocus(this, chosen, direction);
        if (next == null) return;
        chosen = next;
        hint.setVisibility(GONE);
        for (Tile tile : tiles) {
            tile.paint();
            if (tile.view != next || !tile.small) continue;
            describe(tile);
            hint.setVisibility(VISIBLE);
        }
    }

    void pressChosen() {
        if (chosen != null) chosen.performClick();
    }

    void hideHint() { hint.setVisibility(GONE); }

    void refresh() {
        for (Runnable refresher : refreshers) refresher.run();
    }

    private LayoutParams size(int width, int height, int startGap, int topGap) {
        LayoutParams size = new LayoutParams(host.dp(width), host.dp(height));
        size.setMarginStart(host.dp(startGap));
        size.topMargin = host.dp(topGap);
        return size;
    }

    private void build() {
        LinearLayout upper = new LinearLayout(getContext());
        addView(upper);
        Tile power = big(R.drawable.ic_power, getContext().getString(R.string.panel_standby), TaskService::standby);
        upper.addView(power.view, size(wide(), wide(), 0, 0));
        first = power.view;
        LinearLayout side = new LinearLayout(getContext());
        side.setOrientation(VERTICAL);
        Tile wifi = wide(R.drawable.ic_wifi, "Wi-Fi", () -> settings(SettingsActivity.NETWORK));
        side.addView(wifi.view, size(wide(), unit, 0, 0));
        Tile bluetooth = wide(R.drawable.ic_bluetooth, "Bluetooth", () -> settings(SettingsActivity.REMOTES));
        side.addView(bluetooth.view, size(wide(), unit, 0, gap));
        upper.addView(side, new LayoutParams(-2, -2));
        ((LayoutParams) side.getLayoutParams()).setMarginStart(host.dp(gap));

        LinearLayout modes = row();
        audioSwitch(modes, R.drawable.ic_dialogue, R.string.settings_dialogue, 0, SettingsActivity.DIALOGUE);
        audioSwitch(modes, R.drawable.ic_leveler, R.string.settings_leveler, gap, SettingsActivity.LEVELER);
        Tile dim = small(modes, R.drawable.ic_brightness, getContext().getString(R.string.panel_brightness), gap,
            () -> change(() -> Veil.nextDim(getContext())));
        Tile warm = small(modes, R.drawable.ic_eye, getContext().getString(R.string.panel_blue_filter), gap,
            () -> change(() -> Veil.nextWarm(getContext())));

        LinearLayout tools = row();
        small(tools, R.drawable.ic_image, getContext().getString(R.string.panel_screensaver), 0, () -> saver());
        Tile timer = small(tools, R.drawable.ic_timer, getContext().getString(R.string.panel_timer), gap, () -> {
            nextTimer();
            refresh();
        });
        small(tools, R.drawable.ic_tasks, getContext().getString(R.string.first_does_tasks), gap,
            () -> away(new Intent(getContext(), TasksActivity.class)));
        small(tools, R.drawable.ic_restart, getContext().getString(R.string.panel_restart), gap, () -> {
            host.panelDone();
            TaskService.restart();
        });

        refreshers.add(() -> showNetwork(wifi));
        refreshers.add(() -> showBluetooth(bluetooth));
        timerTile = timer;
        refreshers.add(this::showTimer);
        refreshers.add(() -> showDim(dim));
        refreshers.add(() -> showSwitch(warm, Veil.warm(getContext()) > 0));
    }

    /** The same controls as the tiles, one per row; Standby at the bottom, next to the button. */
    private void buildList() {
        Tile power = listRow(R.drawable.ic_power, getContext().getString(R.string.panel_standby), TaskService::standby);
        Tile wifi = listRow(R.drawable.ic_wifi, "Wi-Fi", () -> settings(SettingsActivity.NETWORK));
        Tile bluetooth = listRow(R.drawable.ic_bluetooth, "Bluetooth", () -> settings(SettingsActivity.REMOTES));
        Tile dialogue = listSwitch(R.drawable.ic_dialogue, R.string.settings_dialogue, SettingsActivity.DIALOGUE);
        Tile leveler = listSwitch(R.drawable.ic_leveler, R.string.settings_leveler, SettingsActivity.LEVELER);
        Tile dim = listRow(R.drawable.ic_brightness, getContext().getString(R.string.panel_brightness),
            () -> change(() -> Veil.nextDim(getContext())));
        Tile warm = listRow(R.drawable.ic_eye, getContext().getString(R.string.panel_blue_filter),
            () -> change(() -> Veil.nextWarm(getContext())));
        Tile saver = listRow(R.drawable.ic_image, getContext().getString(R.string.panel_screensaver),
            () -> saver());
        Tile timer = listRow(R.drawable.ic_timer, getContext().getString(R.string.panel_timer), () -> {
            nextTimer();
            refresh();
        });
        Tile tasks = listRow(R.drawable.ic_tasks, getContext().getString(R.string.first_does_tasks),
            () -> away(new Intent(getContext(), TasksActivity.class)));
        Tile restart = listRow(R.drawable.ic_restart, getContext().getString(R.string.panel_restart), () -> {
            host.panelDone();
            TaskService.restart();
        });
        // From the bottom up, in the order the user asked: what is used most is nearest the button.
        Tile[] all = {power, timer, dim, warm, dialogue, leveler, wifi, bluetooth, saver, tasks, restart};
        for (int index = all.length - 1; index >= 0; index--) {
            addView(all[index].view, new LayoutParams(host.dp(LIST_WIDTH), host.dp(LIST_ROW)));
        }
        first = power.view;
        refreshers.add(() -> showNetwork(wifi));
        refreshers.add(() -> showBluetooth(bluetooth));
        timerTile = timer;
        refreshers.add(this::showTimer);
        refreshers.add(() -> showDim(dim));
        refreshers.add(() -> showSwitch(warm, Veil.warm(getContext()) > 0));
    }

    private Tile listRow(int picture, String name, Runnable action) {
        Tile tile = new Tile(picture, name);
        tile.row = true;
        tile.view.setGravity(Gravity.CENTER_VERTICAL);
        tile.view.setPadding(host.dp(12), 0, host.dp(12), 0);
        tile.view.addView(tile.icon, new LayoutParams(host.dp(LIST_ICON), host.dp(LIST_ICON)));
        tile.title.setTextSize(14);
        tile.title.setSingleLine(true);
        tile.title.setPadding(host.dp(12), 0, host.dp(8), 0);
        tile.view.addView(tile.title, new LayoutParams(0, -2, 1));
        tile.value.setTextSize(12);
        tile.value.setMaxWidth(host.dp(LIST_VALUE));
        tile.view.addView(tile.value, new LayoutParams(-2, -2));
        tile.view.setOnClickListener(view -> action.run());
        tile.paint();
        return tile;
    }

    private Tile listSwitch(int picture, int name, String id) {
        Tile[] tile = new Tile[1];
        tile[0] = listRow(picture, getContext().getString(name), () -> flip(tile[0], id));
        refreshers.add(() -> {
            FireSettings settings = fire();
            Background.run(() -> {
                boolean on = "true".equals(settings.read(FireSettings.AUDIO, id));
                host.runOnUiThread(() -> showSwitch(tile[0], on));
            });
        });
        return tile[0];
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(getContext());
        LayoutParams place = new LayoutParams(-2, -2);
        place.topMargin = host.dp(gap);
        addView(row, place);
        return row;
    }

    private Tile big(int picture, String name, Runnable action) {
        Tile tile = new Tile(picture, name);
        tile.view.setOrientation(VERTICAL);
        tile.view.addView(tile.icon, new LayoutParams(host.dp(BIG_ICON), host.dp(BIG_ICON)));
        tile.title.setPadding(0, host.dp(4), 0, 0);
        tile.view.addView(tile.title, new LayoutParams(-2, -2));
        tile.view.setOnClickListener(view -> action.run());
        return tile;
    }

    private Tile wide(int picture, String name, Runnable action) {
        Tile tile = new Tile(picture, name);
        tile.view.setPadding(host.dp(8), 0, host.dp(5), 0);
        tile.view.addView(tile.icon, new LayoutParams(host.dp(WIDE_ICON), host.dp(WIDE_ICON)));
        LinearLayout text = new LinearLayout(getContext());
        text.setOrientation(VERTICAL);
        text.setPadding(host.dp(6), 0, 0, 0);
        tile.title.setLineSpacing(0, 0.9f);
        text.addView(tile.title);
        text.addView(tile.value);
        tile.view.addView(text, new LayoutParams(0, -2, 1));
        tile.view.setOnClickListener(view -> action.run());
        return tile;
    }

    /** Too small for words: name and state appear under the panel while the tile has the focus. */
    private Tile small(LinearLayout row, int picture, String name, int startGap, Runnable action) {
        Tile tile = new Tile(picture, name);
        tile.small = true;
        tile.view.addView(tile.icon, new LayoutParams(host.dp(SMALL_ICON), host.dp(SMALL_ICON)));
        tile.view.setOnFocusChangeListener((changed, focused) -> {
            tile.paint();
            describe(tile);
            hint.setVisibility(focused ? VISIBLE : GONE);
        });
        tile.view.setOnClickListener(view -> action.run());
        row.addView(tile.view, size(unit, unit, startGap, 0));
        return tile;
    }

    /** Fire OS's setting takes a moment to read or write, so the tile answers the press at once. */
    private void audioSwitch(LinearLayout row, int picture, int name, int startGap, String id) {
        Tile[] tile = new Tile[1];
        tile[0] = small(row, picture, getContext().getString(name), startGap, () -> flip(tile[0], id));
        refreshers.add(() -> {
            FireSettings settings = fire();
            Background.run(() -> {
                boolean on = "true".equals(settings.read(FireSettings.AUDIO, id));
                host.runOnUiThread(() -> showSwitch(tile[0], on));
            });
        });
    }

    private void flip(Tile tile, String id) {
        boolean on = !tile.active;
        showSwitch(tile, on);
        FireSettings settings = fire();
        Background.run(() -> {
            if (settings.write(FireSettings.AUDIO, id, String.valueOf(on))) return;
            host.runOnUiThread(() -> {
                host.sayLong(getContext().getString(R.string.settings_fire_refused));
                showSwitch(tile, !on);
            });
        });
    }

    private void showSwitch(Tile tile, boolean on) {
        tile.show(getContext().getString(on ? R.string.panel_on : R.string.panel_off), on);
    }

    private FireSettings fire() {
        if (fire == null) fire = new FireSettings(getContext());
        return fire;
    }

    private void describe(Tile tile) {
        CharSequence state = tile.value.getText();
        hint.setText(state.length() == 0 ? tile.title.getText()
            : getContext().getString(R.string.panel_tile_state, tile.title.getText(), state));
    }

    private void shell(String command) {
        host.shell(command, output -> {
            if (output == null) host.say(getContext().getString(R.string.panel_command_failed));
        });
    }

    /** A shut panel keeps nothing of Amazon's settings app awake. */
    private void letGoOfFire() {
        if (fire != null) fire.close();
        fire = null;
    }

    private void settings(String section) {
        host.open(new Intent(getContext(), SettingsActivity.class).putExtra(SettingsActivity.SECTION, section));
    }

    /** The levels go round, and the last one is off. */
    private void change(Runnable level) {
        if (!TaskService.running()) {
            host.say(getContext().getString(R.string.panel_no_service));
            return;
        }
        level.run();
        TaskService.veilChanged();
        refresh();
    }

    /** Counts down in front of whoever is looking, and stops once the panel is shut. */
    private void showTimer() {
        int left = TaskService.sleepMinutesLeft();
        timerTile.icon.setImageDrawable(left == 0 ? getContext().getDrawable(R.drawable.ic_timer) : new NumberIcon(
            getContext().getDrawable(R.drawable.ic_timer_full), String.valueOf(left), TIMER_CENTER));
        timerTile.show(left > 0 ? getContext().getString(R.string.panel_timer_left, left) : getContext().getString(R.string.panel_off), left > 0);
        removeCallbacks(timerTick);
        if (left > 0) postDelayed(timerTick, TIMER_TICK_MS);
    }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(timerTick);
        super.onDetachedFromWindow();
    }

    /** Zero is off. A level in use is cut out of the sun. */
    private void showDim(Tile tile) {
        int level = Veil.dim(getContext());
        tile.icon.setImageDrawable(level == 0 ? getContext().getDrawable(R.drawable.ic_brightness) : new NumberIcon(
            getContext().getDrawable(R.drawable.ic_brightness_level), String.valueOf(level), SUN_CENTER));
        tile.show(level > 0 ? getContext().getString(R.string.panel_level, level) : getContext().getString(R.string.panel_off), level > 0);
    }

    private void nextTimer() {
        int left = TaskService.sleepMinutesLeft();
        int next = 0;
        for (int minutes : TIMER_MINUTES) {
            if (minutes > left) {
                next = minutes;
                break;
            }
        }
        TaskService.setSleepTimer(next);
    }

    private boolean wired() {
        ConnectivityManager network = getContext().getSystemService(ConnectivityManager.class);
        NetworkCapabilities link = network.getNetworkCapabilities(network.getActiveNetwork());
        return link != null && link.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET);
    }

    private void showNetwork(Tile tile) {
        boolean wired = wired();
        tile.icon.setImageResource(wired ? R.drawable.ic_ethernet : R.drawable.ic_wifi);
        tile.title.setText(wired ? "Ethernet" : "Wi-Fi");
        if (wired) {
            tile.link(true);
            tile.show(getContext().getString(R.string.panel_connected), false);
            return;
        }
        int rssi = getContext().getSystemService(WifiManager.class).getConnectionInfo().getRssi();
        int level = WifiManager.calculateSignalLevel(rssi, LEVELS + 1);
        tile.link(level > 0);
        tile.show(level == 0 ? getContext().getString(R.string.panel_not_connected)
            : wifiName != null ? wifiName : getContext().getString(R.string.panel_connected), false);
        if (level == 0 || !TaskService.ready()) return;
        TaskService.shell("cmd wifi status", output -> {
            Matcher match = WIFI_NAME.matcher(output == null ? "" : output);
            if (!match.find()) return;
            wifiName = match.group(1);
            tile.show(wifiName, false);
        });
    }

    /** Salotto targets Android 11, where BLUETOOTH is enough: BLUETOOTH_CONNECT came later. */
    @SuppressLint("MissingPermission")
    private void showBluetooth(Tile tile) {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            tile.link(false);
            tile.show(getContext().getString(R.string.panel_bluetooth_off), false);
            return;
        }
        int devices = 0;
        for (BluetoothDevice device : adapter.getBondedDevices()) {
            if (Fire.connected(device) && !Fire.remote(device)) devices++;
        }
        tile.link(devices > 0);
        tile.show(devices == 0 ? getContext().getString(R.string.panel_bluetooth_none)
            : getContext().getResources().getQuantityString(R.plurals.panel_bluetooth_connected, devices, devices), false);
    }

    /** Back leaves the panel; the rest of the keys are the Home's. */
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() != KeyEvent.KEYCODE_BACK) return super.dispatchKeyEvent(event);
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            backPressed |= event.getRepeatCount() == 0;
            return true;
        }
        // A release whose press began somewhere else is not for the panel.
        if (!backPressed) return true;
        backPressed = false;
        View to = backTo != null ? backTo : opener;
        if (to != null) to.requestFocus();
        else host.panelDone();
        return true;
    }
}
