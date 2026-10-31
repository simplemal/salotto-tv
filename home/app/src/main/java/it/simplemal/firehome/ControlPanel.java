package it.simplemal.firehome;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;
import android.provider.Settings;
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
    private static final int HEIGHT = 194;
    /** Lined up with the side of the Home's dock. */
    private static final int EDGE = 28;
    private static final int UNIT = 38;
    private static final int GAP = 8;
    private static final int PADDING = 9;
    private static final int WIDE = 2 * UNIT + GAP;
    private static final int RADIUS = 9;
    private static final int PANEL_RADIUS = 18;
    private static final int BIG_ICON = 40;
    private static final int WIDE_ICON = 18;
    private static final int SMALL_ICON = 20;
    /** Dark enough to carry white on a bright backdrop. */
    private static final int TILE_OFF = Color.argb(72, 0, 0, 0);
    private static final int TILE_ACTIVE = Color.argb(205, 255, 255, 255);
    /** The icon of a connection in use. */
    static final int LINKED = Color.rgb(125, 205, 255);
    private static final int LEVELS = 4;
    private static final int[] TIMER_MINUTES = {30, 60, 90, 120};
    private static final Pattern WIFI_NAME = Pattern.compile("connected to \"(.*)\"");

    private final class Tile {
        final LinearLayout view = new LinearLayout(host);
        final ImageView icon = new ImageView(host);
        final GradientDrawable fill = new GradientDrawable();
        final TextView title;
        final TextView value;
        final TextView number;
        boolean active;
        boolean small;
        boolean linked;

        Tile(int picture, String name) {
            icon.setImageResource(picture);
            title = host.label(name, 10);
            title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            value = host.label("", 8);
            value.setSingleLine(true);
            value.setEllipsize(TextUtils.TruncateAt.END);
            number = host.label("", 11);
            number.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            number.setPadding(host.dp(3), 0, 0, 0);
            number.setVisibility(GONE);
            view.setGravity(Gravity.CENTER);
            view.setFocusable(true);
            view.setClickable(true);
            view.setDefaultFocusHighlightEnabled(false);
            view.setContentDescription(name);
            view.setOnFocusChangeListener((changed, focused) -> paint());
            fill.setCornerRadius(host.dp(RADIUS));
            view.setBackground(fill);
            paint();
        }

        void paint() {
            boolean lit = view.isFocused() || active;
            fill.setColor(view.isFocused() ? Color.WHITE : active ? TILE_ACTIVE : TILE_OFF);
            int ink = lit ? Color.BLACK : Color.WHITE;
            icon.setColorFilter(!lit && linked ? LINKED : ink);
            title.setTextColor(ink);
            value.setTextColor(lit ? Color.BLACK : Color.argb(180, 255, 255, 255));
            number.setTextColor(ink);
        }

        /** Zero is off: only a level in use takes room beside the icon. */
        void level(int level) {
            number.setText(String.valueOf(level));
            number.setVisibility(level > 0 ? VISIBLE : GONE);
            show(level > 0 ? "livello " + level : "spento", level > 0);
        }

        void link(boolean on) {
            linked = on;
            paint();
        }

        void show(String text, boolean on) {
            value.setText(text);
            active = on;
            paint();
            if (small && view.isFocused()) describe(this);
        }
    }

    private final CardActivity host;
    private final View opener;
    private final TextView hint;
    private final List<Runnable> refreshers = new ArrayList<>();
    private View first;
    private Supplier<View> below;
    /** Holds the focus when the panel opens with nothing chosen, so that OK does nothing. */
    private View rest;
    private View backTo;
    private boolean backPressed;
    private String wifiName;

    /** Adds the panel and the line under it to `root`: under the pill if the Home shows one. */
    static ControlPanel addTo(CardActivity host, FrameLayout root, View opener, boolean pill) {
        TextView hint = host.label("", 11);
        hint.setPadding(host.dp(10), host.dp(3), host.dp(10), host.dp(3));
        GradientDrawable fill = new GradientDrawable();
        fill.setColor(Color.argb(128, 0, 0, 0));
        fill.setCornerRadius(host.dp(9));
        hint.setBackground(fill);
        hint.setVisibility(GONE);
        int top = pill ? TOP : TOP_OVER_APP;
        root.addView(hint, place(host, top + HEIGHT + 6));
        ControlPanel panel = new ControlPanel(host, opener, hint);
        root.addView(panel, place(host, top));
        return panel;
    }

    private static FrameLayout.LayoutParams place(CardActivity host, int top) {
        FrameLayout.LayoutParams place = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        place.topMargin = host.dp(top);
        place.rightMargin = host.dp(EDGE);
        return place;
    }

    private ControlPanel(CardActivity host, View opener, TextView hint) {
        super(host);
        this.host = host;
        this.opener = opener;
        this.hint = hint;
        setOrientation(VERTICAL);
        int room = host.dp(PADDING);
        setPadding(room, room, room, room);
        // In the background: a foreground would stop at the padding, inside the tiles' margin.
        int corner = host.dp(PANEL_RADIUS);
        setBackground(new LayerDrawable(new Drawable[]{host.glass(corner), host.glassEdge(corner)}));
        setClickable(true);
        build();
        rest = new View(host);
        rest.setOnFocusChangeListener((view, focused) -> {
            if (!focused) view.setFocusable(false);
        });
        LayoutParams small = new LayoutParams(1, 1);
        small.topMargin = -1;
        addView(rest, small);
    }

    /** The panel floats above the Home: the arrows must not reach the cards underneath. */
    @Override public View focusSearch(View focused, int direction) {
        if (focused == rest) return direction == FOCUS_UP && opener != null ? opener : first;
        View next = FocusFinder.getInstance().findNextFocus(this, focused, direction);
        if (next != null) return next;
        View out = direction == FOCUS_UP ? opener : direction == FOCUS_DOWN && below != null ? below.get() : null;
        return out != null ? out : focused;
    }

    /** Opened with Home held: nothing chosen, and Back closes it, leaving the focus on `from`. */
    void openEmpty(View from) {
        backTo = from;
        rest.setFocusable(true);
        rest.requestFocus();
    }

    void closed() { backTo = null; }

    /** On the Home, the last row leads down to the apps. */
    void leadDownTo(Supplier<View> view) { below = view; }

    View first() { return first; }

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
        LinearLayout upper = new LinearLayout(host);
        addView(upper);
        Tile power = big(R.drawable.ic_power, "Standby", TaskService::standby);
        upper.addView(power.view, size(WIDE, WIDE, 0, 0));
        first = power.view;
        LinearLayout side = new LinearLayout(host);
        side.setOrientation(VERTICAL);
        Tile wifi = wide(R.drawable.ic_wifi, "Wi-Fi", () -> settings(SettingsActivity.NETWORK));
        side.addView(wifi.view, size(WIDE, UNIT, 0, 0));
        Tile bluetooth = wide(R.drawable.ic_bluetooth, "Bluetooth", () -> settings(SettingsActivity.REMOTES));
        side.addView(bluetooth.view, size(WIDE, UNIT, 0, GAP));
        upper.addView(side, new LayoutParams(-2, -2));
        ((LayoutParams) side.getLayoutParams()).setMarginStart(host.dp(GAP));

        LinearLayout modes = row();
        Tile quiet = small(modes, R.drawable.ic_moon, "Notifiche a comparsa", 0, () -> {
            toggleQuiet();
            refresh();
        });
        Tile timer = small(modes, R.drawable.ic_timer, "Timer", GAP, () -> {
            nextTimer();
            refresh();
        });
        Tile dim = small(modes, R.drawable.ic_brightness, "Luminosità", GAP, () -> change(() -> Veil.nextDim(host)));
        Tile warm = small(modes, R.drawable.ic_eye, "Filtro luce blu", GAP, () -> change(() -> Veil.nextWarm(host)));

        LinearLayout tools = row();
        small(tools, R.drawable.ic_image, "Salvaschermo", 0, () -> host.open(Fire.screensaver()));
        small(tools, R.drawable.ic_airplay, "AirPlay", GAP, () -> host.openPackage(Fire.AIRPLAY));
        small(tools, R.drawable.ic_tasks, "Task manager", GAP, () -> host.open(new Intent(host, TasksActivity.class)));
        small(tools, R.drawable.ic_restart, "Riavvia", GAP, TaskService::restart);

        refreshers.add(() -> showNetwork(wifi));
        refreshers.add(() -> showBluetooth(bluetooth));
        refreshers.add(() -> quiet.show(quiet() ? "nascoste" : "mostrate", quiet()));
        refreshers.add(() -> {
            int left = TaskService.sleepMinutesLeft();
            timer.show(left > 0 ? "tra " + left + " min" : "spento", left > 0);
        });
        refreshers.add(() -> dim.level(Veil.dim(host)));
        refreshers.add(() -> warm.level(Veil.warm(host)));
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(host);
        LayoutParams place = new LayoutParams(-2, -2);
        place.topMargin = host.dp(GAP);
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
        LinearLayout text = new LinearLayout(host);
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
        tile.view.addView(tile.number);
        tile.view.setOnFocusChangeListener((changed, focused) -> {
            tile.paint();
            describe(tile);
            hint.setVisibility(focused ? VISIBLE : GONE);
        });
        tile.view.setOnClickListener(view -> action.run());
        row.addView(tile.view, size(UNIT, UNIT, startGap, 0));
        return tile;
    }

    private void describe(Tile tile) {
        CharSequence state = tile.value.getText();
        hint.setText(state.length() == 0 ? tile.title.getText() : tile.title.getText() + ": " + state);
    }

    private void shell(String command) {
        host.shell(command, output -> {
            if (output == null) host.say("Comando non riuscito");
        });
    }

    private void settings(String section) {
        host.open(new Intent(host, SettingsActivity.class).putExtra(SettingsActivity.SECTION, section));
    }

    private boolean quiet() {
        return Settings.Global.getInt(host.getContentResolver(), Fire.HEADS_UP, 1) == 0;
    }

    private void toggleQuiet() {
        try {
            Settings.Global.putInt(host.getContentResolver(), Fire.HEADS_UP, quiet() ? 1 : 0);
        } catch (SecurityException e) {
            host.say("Manca il permesso di sistema per Salotto");
        }
    }

    /** The levels go round, and the last one is off. */
    private void change(Runnable level) {
        if (!TaskService.running()) {
            host.say("Manca il servizio di Salotto");
            return;
        }
        level.run();
        TaskService.veilChanged();
        refresh();
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
        ConnectivityManager network = host.getSystemService(ConnectivityManager.class);
        NetworkCapabilities link = network.getNetworkCapabilities(network.getActiveNetwork());
        return link != null && link.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET);
    }

    private void showNetwork(Tile tile) {
        boolean wired = wired();
        tile.icon.setImageResource(wired ? R.drawable.ic_ethernet : R.drawable.ic_wifi);
        tile.title.setText(wired ? "Ethernet" : "Wi-Fi");
        if (wired) {
            tile.link(true);
            tile.show("Collegato", false);
            return;
        }
        int rssi = host.getSystemService(WifiManager.class).getConnectionInfo().getRssi();
        int level = WifiManager.calculateSignalLevel(rssi, LEVELS + 1);
        tile.link(level > 0);
        tile.show(level == 0 ? "Non collegato" : wifiName != null ? wifiName : "Collegato", false);
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
            tile.show("Spento", false);
            return;
        }
        int devices = 0;
        for (BluetoothDevice device : adapter.getBondedDevices()) {
            if (Fire.connected(device) && !Fire.remote(device)) devices++;
        }
        tile.link(devices > 0);
        tile.show(devices == 0 ? "Nessuno" : devices == 1 ? "1 collegato" : devices + " collegati", false);
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
        else host.finish();
        return true;
    }
}
