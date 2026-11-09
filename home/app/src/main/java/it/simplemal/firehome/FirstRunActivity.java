package it.simplemal.firehome;

import android.graphics.drawable.Drawable;
import android.content.res.ColorStateList;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.text.format.Formatter;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.json.JSONException;

/**
 * The first time after the installation, instead of the Home: Salotto, the Stick's name, its network,
 * the city and the keys held down. The Home starts only at the end, or its first animation would go
 * by unseen under these pages. Its words are in the Stick's language or the one chosen on the first
 * page, and only here: the rest of Salotto is in Italian.
 */
public final class FirstRunActivity extends CardActivity {
    private static final String FILE = "first_run";
    private static final String DONE = "done";
    /**
     * Which pages were seen: raised only when the pages themselves change, so that after an update
     * they come back once, and only then (the user's rule of 2026-11-09). 1: the pages up to the
     * 1.0.24; 2: the choice of the Home, in the 1.1.0.
     */
    private static final String SEEN = "seen";
    private static final int PAGES_VERSION = 2;
    /** What the guide that came before left behind: a Stick that has seen it needs no introduction. */
    private static final String OLD_FILE = "welcome";
    private static final String OLD_DONE = "greeted";
    private static final String RIGHT_TO_LEFT = "ar";
    private static final String NO_NAME = "<unknown ssid>";
    private static final int PAGES = 7;
    private static final int NETWORK_PAGE = 2;
    private static final int LOGO = 92;
    private static final int NAME_SP = 30;
    private static final int VERSION_SP = 9;
    private static final int WELCOME_SP = 34;
    private static final int TITLE_SP = 30;
    private static final int TEXT_SP = 17;
    private static final int EXPLAINED_SP = 15;
    private static final int MARK_SIZE = 20;
    private static final int VALUE_SP = 26;
    private static final int BUTTON_SP = 17;
    private static final int TEXT_WIDTH = 720;
    private static final int WIDE = 300;
    private static final int LANGUAGE_WIDTH = 200;
    private static final int LANGUAGE_ROWS = 5;
    private static final int ROW_HEIGHT = 44;
    private static final int ROW_GAP = 8;
    private static final int RESULT_WIDTH = 480;
    private static final int RESULTS = 5;
    private static final int LIST_ROOM = 8;
    private static final int NETWORK_ICON = 56;
    private static final int NOTE_SP = 12;
    /** The two columns of the backdrop page, as in the Settings: the list runs the whole height. */
    private static final int SIDE_WEIGHT = 46;
    private static final int LIST_WEIGHT = 54;
    /** Together they centre the words on the middle column of the pictures. */
    private static final int SIDE_START = 56;
    private static final int SIDE_END = 20;
    private static final int LIST_END = 40;
    private static final int LIST_TOP = 26;
    private static final float SIDE_CENTER = 0.24f;
    private static final int PREVIEW_SP = 12;
    private static final int PREVIEW_MARGIN = 24;
    private static final int GUIDE_WIDTH = 680;
    private static final int GUIDE_HEIGHT = 220;
    /** The same above and below the drawing. */
    private static final int GUIDE_GAP = 20;
    /** The three Homes, as the Settings list them: Clean on the left, the user's choice of 2026-11-09. */
    private static final int[] HOME_STYLES = {Look.HOME_CLEAN, Look.HOME_CIRCLE, Look.HOME_GRID};
    private static final int[] HOME_PICTURES = {R.drawable.first_home_clean, R.drawable.first_home_circle, R.drawable.first_home_glass};
    private static final int[] HOME_NAMES = {R.string.settings_home_clean, R.string.settings_home_circle, R.string.settings_home_grid};
    private static final int HOME_CARD_WIDTH = 240;
    private static final int HOME_CARD_HEIGHT = 135;
    private static final int HOME_CARD_GAP = 28;
    private static final int HOME_CARD_RADIUS = 14;
    private static final int HOME_NAME_SP = 17;
    private static final int DOT = 7;
    private static final int DOT_GAP = 9;
    private static final int DOTS_BOTTOM = 26;
    /** How often the bars of the collections coming in move. */
    private static final long PROGRESS_MS = 500;
    private static final int DOT_OFF = Color.argb(70, 255, 255, 255);

    /** A backdrop to choose: with a folder it shows its pictures, without it the page itself turns into it. */
    private static final class Choice {
        final String key;
        final int name;
        final int text;
        final String folder;
        final int mark;

        Choice(String key, int name, int text, String folder, int mark) {
            this.key = key;
            this.name = name;
            this.text = text;
            this.folder = folder;
            this.mark = mark;
        }
    }

    private static final Choice[] WEATHER_CHOICES = {
        new Choice(Look.PICTURES, R.string.first_backdrop_pictures, R.string.first_pictures_text, Look.PICTURES_FOLDER, R.drawable.ic_cloud),
        new Choice(Look.PHOTOS, R.string.first_backdrop_photos, R.string.first_photos_text, Look.PHOTOS_FOLDER, R.drawable.ic_cloud),
        new Choice(Look.AERIAL, R.string.first_backdrop_aerial, R.string.first_aerial_text, Look.AERIAL_FOLDER, R.drawable.ic_cloud)};
    private static final Choice[] OTHER_CHOICES = {
        new Choice(Look.ABSTRACT, 0, R.string.first_abstract_text, Look.ABSTRACT_FOLDER, R.drawable.ic_restart),
        new Choice(Look.ABSTRACT_3D, 0, R.string.first_abstract3d_text, Look.ABSTRACT_3D_FOLDER, R.drawable.ic_restart),
        new Choice(Look.NOX, 0, R.string.first_nox_text, Look.NOX_FOLDER, R.drawable.ic_restart),
        new Choice(Look.NATURE, 0, R.string.first_nature_text, Look.NATURE_FOLDER, R.drawable.ic_restart),
        new Choice(Look.PRESET_NAMES[0], R.string.first_backdrop_night, R.string.first_plain_text, null, 0),
        new Choice(Look.PRESET_NAMES[1], R.string.first_backdrop_ocean, R.string.first_plain_text, null, 0),
        new Choice(Look.PRESET_NAMES[2], R.string.first_backdrop_forest, R.string.first_plain_text, null, 0),
        new Choice(Look.PRESET_NAMES[3], R.string.first_backdrop_sunset, R.string.first_plain_text, null, 0),
        new Choice(Look.PRESET_NAMES[4], R.string.first_backdrop_graphite, R.string.first_plain_text, null, 0)};

    private final Runnable[] pages = {this::showWelcome, this::showName, this::showNetwork, this::showCity,
        this::showBackdrop, this::showHomes, this::showRemote};
    private FrameLayout root;
    private PictureMosaic mosaic;
    private TextView previewNote;
    private LinearLayout page;
    private LinearLayout dots;
    private TextView note;
    private Context words;
    private int at;
    /** Tells a late answer that its page is gone. */
    private int visit;
    private Runnable back = () -> { };
    /** The question about the collections is asked once per visit. */
    private boolean packsAsked;
    /** The backdrop under the focus, kept when the page is drawn again as collections arrive. */
    private String focusedChoice;

    static boolean due(Context context) {
        boolean done = prefs(context).getBoolean(DONE, false)
            || context.getSharedPreferences(OLD_FILE, Context.MODE_PRIVATE).getBoolean(OLD_DONE, false);
        // Seen before this number existed: the first pages.
        return !done || prefs(context).getInt(SEEN, 1) < PAGES_VERSION;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        root = new FrameLayout(this);
        root.setBackground(Look.plainBackdrop());
        mosaic = new PictureMosaic(this);
        root.addView(mosaic, new FrameLayout.LayoutParams(-1, -1));
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(page, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
        dots = new LinearLayout(this);
        FrameLayout.LayoutParams place = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        place.bottomMargin = dp(DOTS_BOTTOM);
        root.addView(dots, place);
        previewNote = label("", PREVIEW_SP);
        previewNote.setAlpha(0.7f);
        previewNote.setVisibility(View.GONE);
        FrameLayout.LayoutParams corner = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
        corner.bottomMargin = dp(PREVIEW_MARGIN);
        corner.setMarginStart(dp(PREVIEW_MARGIN));
        root.addView(previewNote, corner);
        setContentView(root);
        speak();
        go(0);
    }

    @Override protected void onDestroy() {
        mosaic.show(null);
        super.onDestroy();
    }

    /** Back from the Settings the network may be another. */
    @Override protected void onRestart() {
        super.onRestart();
        if (at == NETWORK_PAGE) showNetwork();
    }

    @Override public void onBackPressed() { back.run(); }

    private void speak() {
        words = Language.apply(getBaseContext());
        getWindow().getDecorView().setLayoutDirection(RIGHT_TO_LEFT.equals(Language.code(this))
            ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
    }

    private String words(int id) { return words.getString(id); }

    private void go(int index) {
        at = index;
        visit++;
        back = index > 0 ? () -> go(index - 1) : due(this) ? () -> { } : this::finish;
        root.setBackground(Look.plainBackdrop());
        mosaic.show(null);
        previewNote.setVisibility(View.GONE);
        page.setLayoutParams(new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
        dots.setTranslationX(0);
        pages[index].run();
        dots.removeAllViews();
        for (int dot = 0; dot < PAGES; dot++) {
            View mark = new View(this);
            GradientDrawable fill = new GradientDrawable();
            fill.setShape(GradientDrawable.OVAL);
            fill.setColor(dot == index ? Color.WHITE : DOT_OFF);
            mark.setBackground(fill);
            LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(dp(DOT), dp(DOT));
            if (dot > 0) size.setMarginStart(dp(DOT_GAP));
            dots.addView(mark, size);
        }
    }

    private void next() {
        if (at < PAGES - 1) {
            go(at + 1);
            return;
        }
        prefs(this).edit().putBoolean(DONE, true).putInt(SEEN, PAGES_VERSION).apply();
        // Fresh, because seen from the Settings the language may have changed on the way.
        startActivity(new Intent(this, MainActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        finish();
    }

    private void showWelcome() {
        page.removeAllViews();
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_salotto);
        page.addView(logo, new LinearLayout.LayoutParams(dp(LOGO), dp(LOGO)));
        TextView name = label(Brand.NAME, NAME_SP);
        name.setGravity(Gravity.CENTER);
        page.addView(name);
        TextView version = label(BuildVersion.name(this), VERSION_SP);
        version.setGravity(Gravity.CENTER);
        version.setAlpha(0.5f);
        page.addView(version);
        TextView welcome = label(words(R.string.first_welcome), WELCOME_SP);
        welcome.setGravity(Gravity.CENTER);
        welcome.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams line = new LinearLayout.LayoutParams(dp(TEXT_WIDTH), -2);
        line.topMargin = dp(26);
        page.addView(welcome, line);
        page.addView(button(Language.name(this), BUTTON_SP, this::showLanguages), place(WIDE, 26));
        View next = button(words(R.string.first_next), BUTTON_SP, this::next);
        page.addView(next, place(WIDE, 10));
        next.requestFocus();
    }

    private void showLanguages() {
        heading(words(R.string.first_language), null);
        back = () -> go(0);
        String chosen = Language.code(this);
        LinearLayout columns = new LinearLayout(this);
        columns.setGravity(Gravity.CENTER);
        columns.setClipChildren(false);
        View selected = null;
        for (int column = 0; column < Language.CODES.length / LANGUAGE_ROWS; column++) {
            LinearLayout rows = new LinearLayout(this);
            rows.setOrientation(LinearLayout.VERTICAL);
            rows.setClipChildren(false);
            for (int row = 0; row < LANGUAGE_ROWS; row++) {
                int index = column * LANGUAGE_ROWS + row;
                String language = Language.CODES[index];
                View choice = button(Language.NAMES[index], BUTTON_SP, () -> {
                    Language.set(this, language);
                    speak();
                    go(0);
                });
                rows.addView(choice, place(LANGUAGE_WIDTH, row == 0 ? 0 : ROW_GAP));
                if (language.equals(chosen)) selected = choice;
            }
            LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(-2, -2);
            if (column > 0) gap.setMarginStart(dp(ROW_GAP * 2));
            columns.addView(rows, gap);
        }
        LinearLayout.LayoutParams under = new LinearLayout.LayoutParams(-2, -2);
        under.topMargin = dp(24);
        page.addView(columns, under);
        selected.requestFocus();
    }

    private void showName() {
        String name = DeviceInfo.name(this);
        heading(words(R.string.first_name_title), words(R.string.first_name_text));
        page.addView(value(name));
        View next = button(words(R.string.first_next), BUTTON_SP, this::next);
        page.addView(next, place(WIDE, 26));
        page.addView(button(words(R.string.first_name_change), BUTTON_SP, () -> changeName(name)), place(WIDE, 10));
        next.requestFocus();
    }

    private void changeName(String name) {
        heading(words(R.string.first_name_title), words(R.string.first_name_text));
        back = () -> go(1);
        Runnable[] save = new Runnable[1];
        EditText field = field(words(R.string.first_name_hint), () -> save[0].run());
        field.setText(name);
        field.setSelection(name.length());
        save[0] = () -> {
            String chosen = field.getText().toString().trim();
            if (chosen.isEmpty()) return;
            if (!DeviceInfo.setName(this, chosen)) sayLong(words(R.string.first_name_refused));
            go(1);
        };
        page.addView(field, place(WIDE, 26));
        page.addView(button(words(R.string.first_save), BUTTON_SP, () -> save[0].run()), place(WIDE, 22));
        field.requestFocus();
    }

    private void showNetwork() {
        ConnectivityManager network = getSystemService(ConnectivityManager.class);
        NetworkCapabilities link = network.getNetworkCapabilities(network.getActiveNetwork());
        boolean wired = link != null && link.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET);
        heading(words(R.string.first_network_title), words(link != null ? R.string.first_network_on : R.string.first_network_off));
        ImageView icon = new ImageView(this);
        icon.setImageResource(wired ? R.drawable.ic_ethernet : R.drawable.ic_wifi);
        icon.setColorFilter(link != null ? ControlPanel.LINKED : Color.WHITE);
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(dp(NETWORK_ICON), dp(NETWORK_ICON));
        size.topMargin = dp(24);
        page.addView(icon, size);
        page.addView(value(link == null ? words(R.string.first_network_none) : wired ? "Ethernet" : wifiName()));
        View next = button(words(R.string.first_next), BUTTON_SP, this::next);
        page.addView(next, place(WIDE, 26));
        page.addView(button(words(R.string.first_network_change), BUTTON_SP, () -> open(new Intent(this, SettingsActivity.class)
            .putExtra(SettingsActivity.SECTION, SettingsActivity.NETWORK))), place(WIDE, 10));
        next.requestFocus();
    }

    private String wifiName() {
        String name = getSystemService(WifiManager.class).getConnectionInfo().getSSID();
        return name == null || name.equals(NO_NAME) ? "Wi-Fi" : name.replace("\"", "");
    }

    private void showCity() {
        String place = Weather.place(this);
        if (place == null) {
            askCity();
            return;
        }
        heading(words(R.string.first_city_title), words(R.string.first_city_text));
        page.addView(value(place));
        View next = button(words(R.string.first_next), BUTTON_SP, this::next);
        page.addView(next, place(WIDE, 26));
        page.addView(button(words(R.string.first_city_change), BUTTON_SP, this::askCity), place(WIDE, 10));
        next.requestFocus();
    }

    private void askCity() {
        heading(words(R.string.first_city_title), words(R.string.first_city_text));
        visit++;
        back = () -> go(Weather.place(this) != null ? at : at - 1);
        Runnable[] find = new Runnable[1];
        EditText name = field(words(R.string.first_city_hint), () -> find[0].run());
        find[0] = () -> search(name.getText().toString().trim());
        page.addView(name, place(WIDE, 26));
        page.addView(button(words(R.string.first_search), BUTTON_SP, () -> find[0].run()), place(WIDE, 22));
        page.addView(button(words(R.string.first_not_now), BUTTON_SP, this::next), place(WIDE, 10));
        name.requestFocus();
    }

    private void search(String name) {
        if (name.isEmpty()) return;
        note.setText(words(R.string.first_searching));
        int asked = visit;
        Background.run(() -> {
            try {
                List<Weather.Place> places = Weather.search(this, name);
                runOnUiThread(() -> {
                    if (asked != visit) return;
                    if (places.isEmpty()) note.setText(words(R.string.first_no_place));
                    else showPlaces(places);
                });
            } catch (IOException | JSONException e) {
                runOnUiThread(() -> {
                    if (asked == visit) note.setText(words(R.string.first_no_connection));
                });
            }
        });
    }

    private void showPlaces(List<Weather.Place> places) {
        heading(words(R.string.first_place_title), words(R.string.first_place_text));
        back = this::askCity;
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        // Room for the selected row to grow without losing its rounded ends.
        rows.setPadding(dp(LIST_ROOM), dp(LIST_ROOM), dp(LIST_ROOM), dp(LIST_ROOM));
        rows.setClipChildren(false);
        rows.setClipToPadding(false);
        View first = null;
        for (Weather.Place place : places) {
            View row = button(place.name + "  ·  " + place.detail, BUTTON_SP, () -> {
                Weather.setPlace(this, place);
                next();
            });
            LinearLayout.LayoutParams wide = new LinearLayout.LayoutParams(-1, dp(ROW_HEIGHT));
            if (first != null) wide.topMargin = dp(ROW_GAP);
            rows.addView(row, wide);
            if (first == null) first = row;
        }
        ScrollView list = new ScrollView(this);
        list.setVerticalScrollBarEnabled(false);
        list.addView(rows);
        int shown = Math.min(RESULTS, places.size());
        LinearLayout.LayoutParams place = new LinearLayout.LayoutParams(dp(RESULT_WIDTH),
            dp(shown * ROW_HEIGHT + (shown - 1) * ROW_GAP + 2 * LIST_ROOM));
        place.topMargin = dp(24);
        page.addView(list, place);
        first.requestFocus();
    }

    /**
     * After the city: with collections chosen and not yet here, and a network, the user is asked
     * first whether to fetch them now, the user's design of 2026-11-05. Asked once per visit.
     */
    private void showBackdrop() {
        if (!packsAsked && !Packs.fetching() && online() && !Packs.missing(this).isEmpty()) askPacks();
        else showChoices();
    }

    private boolean online() {
        return getSystemService(ConnectivityManager.class).getActiveNetwork() != null;
    }

    /** The question on a line of its own, the weight and the time smaller under it, once they are known. */
    private void askPacks() {
        heading(words(R.string.first_packs_question), "");
        int asked = visit;
        Background.run(() -> {
            long bytes = Packs.missingBytes(this);
            runOnUiThread(() -> {
                if (asked == visit && bytes > 0) {
                    note.setText(words.getString(R.string.first_packs_size, size(bytes)));
                }
            });
        });
        View now = button(words(R.string.first_packs_now), BUTTON_SP, () -> answered(true));
        page.addView(now, place(WIDE, 26));
        page.addView(button(words(R.string.first_packs_later), BUTTON_SP, () -> answered(false)), place(WIDE, 10));
        now.requestFocus();
    }

    /** Back from the choices goes to the question; once collections are coming it is answered, and Back goes to the city. */
    private void answered(boolean now) {
        packsAsked = true;
        if (now) Packs.fetchNow(this);
        int page = at;
        back = () -> {
            if (Packs.fetching()) {
                go(page - 1);
                return;
            }
            packsAsked = false;
            go(page);
        };
        showChoices();
    }

    /**
     * The backdrops to choose: only the collections here, and the plain ones. While collections are
     * coming, each has a bar instead, and becomes a choice once whole; going on leaves them coming.
     */
    private void showChoices() {
        visit++;
        int shownVisit = visit;
        String focusedKey = focusedChoice;
        page.removeAllViews();
        page.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        float toSide = getResources().getDisplayMetrics().widthPixels * (0.5f - SIDE_CENTER);
        dots.setTranslationX(RIGHT_TO_LEFT.equals(Language.code(this)) ? toSide : -toSide);
        List<Choice> choices = new ArrayList<>();
        if (Weather.place(this) != null) choices.addAll(Arrays.asList(WEATHER_CHOICES));
        choices.addAll(Arrays.asList(OTHER_CHOICES));
        List<String> coming = Packs.fetching() ? Packs.queue() : new ArrayList<>();
        List<String> whole = new ArrayList<>();
        List<Object[]> bars = new ArrayList<>();
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPaddingRelative(dp(LIST_ROOM), dp(LIST_TOP), dp(LIST_ROOM), dp(LIST_TOP));
        rows.setClipChildren(false);
        rows.setClipToPadding(false);
        LinearLayout side = new LinearLayout(this);
        side.setOrientation(LinearLayout.VERTICAL);
        side.setGravity(Gravity.CENTER);
        side.setPaddingRelative(dp(SIDE_START), 0, dp(SIDE_END), 0);
        boolean arriving = false;
        for (Choice choice : choices) {
            if (choice.folder != null && !Packs.listed(this, choice.folder) && coming.contains(choice.folder)) arriving = true;
        }
        TextView title = label(words(arriving ? R.string.first_packs_title : R.string.first_backdrop_title), TITLE_SP);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        TextView explained = label("", EXPLAINED_SP);
        explained.setGravity(Gravity.CENTER);
        explained.setAlpha(0.85f);
        View first = null;
        View kept = null;
        boolean dynamic = false;
        boolean statics = false;
        for (Choice choice : choices) {
            boolean here = choice.folder == null || Packs.listed(this, choice.folder);
            // A collection not here is not listed, the user's rule of 2026-11-05; one coming shows its bar.
            if (!here && !coming.contains(choice.folder)) continue;
            if (choice.folder != null && !dynamic) {
                dynamic = true;
                rows.addView(groupLabel(R.string.first_backdrop_dynamic));
            }
            if (choice.folder == null && !statics) {
                statics = true;
                rows.addView(groupLabel(R.string.first_backdrop_static));
            }
            String name = choice.name == 0 ? choice.key : words(choice.name);
            LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-1, dp(ROW_HEIGHT));
            size.bottomMargin = dp(ROW_GAP);
            if (!here) {
                TextView bar = arrivingRow(name, choice.mark);
                rows.addView(bar, size);
                bars.add(new Object[]{bar, choice.folder, name});
                continue;
            }
            if (choice.folder != null) whole.add(choice.folder);
            TextView row = button(name, BUTTON_SP, () -> {
                Look.setBackdrop(this, choice.key);
                next();
            });
            View.OnFocusChangeListener style = row.getOnFocusChangeListener();
            row.setOnFocusChangeListener((view, focused) -> {
                style.onFocusChange(view, focused);
                row.setCompoundDrawableTintList(ColorStateList.valueOf(focused ? Fire.INK : Color.WHITE));
                if (focused) {
                    focusedChoice = choice.key;
                    preview(choice, explained);
                }
            });
            if (choice.mark != 0) {
                Drawable mark = getDrawable(choice.mark).mutate();
                mark.setBounds(0, 0, dp(MARK_SIZE), dp(MARK_SIZE));
                row.setCompoundDrawablesRelative(mark, null, null, null);
                row.setCompoundDrawableTintList(ColorStateList.valueOf(Color.WHITE));
            }
            rows.addView(row, size);
            if (first == null || choice.key.equals(Look.backdrop(this))) first = row;
            if (choice.key.equals(focusedKey)) kept = row;
        }
        ScrollView list = new ScrollView(this);
        list.setVerticalScrollBarEnabled(false);
        list.setClipChildren(false);
        list.addView(rows);
        LinearLayout columns = new LinearLayout(this);
        side.addView(title);
        LinearLayout.LayoutParams textPlace = new LinearLayout.LayoutParams(-1, -2);
        textPlace.topMargin = dp(10);
        side.addView(explained, textPlace);
        String why = stopReason();
        if (why != null) {
            TextView later = label(why, EXPLAINED_SP);
            later.setGravity(Gravity.CENTER);
            later.setAlpha(0.6f);
            LinearLayout.LayoutParams under = new LinearLayout.LayoutParams(-1, -2);
            under.topMargin = dp(16);
            side.addView(later, under);
        }
        // Words first and rows after, as in the Settings: the pictures fade out before the rows.
        columns.addView(side, new LinearLayout.LayoutParams(0, -1, SIDE_WEIGHT));
        LinearLayout.LayoutParams listPlace = new LinearLayout.LayoutParams(0, -1, LIST_WEIGHT);
        listPlace.setMarginEnd(dp(LIST_END));
        columns.addView(list, listPlace);
        page.addView(columns, new LinearLayout.LayoutParams(-1, -1));
        (kept != null ? kept : first).requestFocus();
        if (!bars.isEmpty()) root.postDelayed(() -> follow(shownVisit, whole, bars), PROGRESS_MS);
    }

    /** A weight in the page's language, its number and unit never split over two lines. */
    private String size(long bytes) {
        return Formatter.formatShortFileSize(words, bytes).replace(' ', '\u00a0');
    }

    /** Why the collections stopped coming, said plainly: only the room is the user's to solve. Null if nothing stopped. */
    private String stopReason() {
        if (Packs.fetching() || Packs.missing(this).isEmpty()) return null;
        switch (Packs.stop()) {
            case ROOM:
                return words.getString(R.string.first_packs_room, size(Packs.roomNeeded()), size(Packs.roomFree()));
            case NETWORK: return words(R.string.first_packs_network);
            case SERVER: return words(R.string.first_packs_server);
            case DAMAGED: return words(R.string.first_packs_damaged);
            default: return null;
        }
    }

    /** Twice a second while collections come: the bars move, and a collection whole becomes a choice. */
    private void follow(int shownVisit, List<String> whole, List<Object[]> bars) {
        if (shownVisit != visit || isFinishing()) return;
        boolean changed = !Packs.fetching();
        for (Object[] bar : bars) if (Packs.listed(this, (String) bar[1])) changed = true;
        for (String pack : whole) if (!Packs.listed(this, pack)) changed = true;
        if (changed) {
            showChoices();
            return;
        }
        for (Object[] bar : bars) showShare((TextView) bar[0], (String) bar[2], Packs.percent((String) bar[1]));
        root.postDelayed(() -> follow(shownVisit, whole, bars), PROGRESS_MS);
    }

    /** A row that cannot be chosen yet: its name, and behind it a bar of how much has come. */
    private TextView arrivingRow(String name, int markId) {
        TextView row = label(name, BUTTON_SP);
        if (markId != 0) {
            Drawable mark = getDrawable(markId).mutate();
            mark.setBounds(0, 0, dp(MARK_SIZE), dp(MARK_SIZE));
            row.setCompoundDrawablesRelative(mark, null, null, null);
            row.setCompoundDrawableTintList(ColorStateList.valueOf(Color.WHITE));
        }
        row.setGravity(Gravity.CENTER);
        row.setSingleLine(true);
        row.setFocusable(false);
        row.setPadding(dp(20), 0, dp(20), 0);
        GradientDrawable done = new GradientDrawable();
        done.setColor(Color.argb(90, 255, 255, 255));
        done.setCornerRadius(dp(12));
        ClipDrawable share = new ClipDrawable(done, Gravity.START, ClipDrawable.HORIZONTAL);
        row.setBackground(new LayerDrawable(new Drawable[]{softGlass(dp(12)), share}));
        showShare(row, name, -1);
        return row;
    }

    /** Waiting, it is only dimmed; coming, it shows how much. */
    private void showShare(TextView row, String name, int percent) {
        Drawable share = ((LayerDrawable) row.getBackground()).getDrawable(1);
        share.setLevel(Math.max(0, percent) * 100);
        row.setAlpha(percent < 0 ? 0.45f : 0.8f);
        row.setText(percent < 0 ? name : String.format(java.util.Locale.getDefault(), "%s  ·  %d%%", name, percent));
    }

    private TextView groupLabel(int name) {
        TextView label = label(words(name), TEXT_SP - 2);
        label.setAlpha(0.7f);
        label.setPaddingRelative(dp(ROW_GAP), dp(ROW_GAP), 0, dp(ROW_GAP));
        return label;
    }

    private void preview(Choice choice, TextView explained) {
        explained.setText(words(choice.text));
        mosaic.show(choice.folder);
        if (choice.folder == null) root.setBackground(Look.preset(choice.key));
        previewNote.setText(words(R.string.first_preview));
        previewNote.setVisibility(choice.folder != null ? View.GONE : View.VISIBLE);
    }

    /**
     * The Home to start with, one of three pictures: no Next, the user's rule of 2026-11-09, a
     * picture has to be chosen to go on. The one chosen before, or Clean, has the focus.
     */
    private void showHomes() {
        heading(words(R.string.settings_cat_home), words(R.string.first_home_text));
        LinearLayout row = new LinearLayout(this);
        // Clean on the left in every language.
        row.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        row.setPadding(0, dp(14), 0, dp(14));
        page.setClipChildren(false);
        int chosen = Look.homeChosen(this) ? Look.homeStyle(this) : Look.HOME_CLEAN;
        View focus = null;
        for (int index = 0; index < HOME_STYLES.length; index++) {
            int style = HOME_STYLES[index];
            View card = homeCard(HOME_PICTURES[index], words(HOME_NAMES[index]), () -> {
                Look.setHomeStyle(this, style);
                next();
            });
            LinearLayout.LayoutParams place = new LinearLayout.LayoutParams(-2, -2);
            if (index > 0) place.leftMargin = dp(HOME_CARD_GAP);
            row.addView(card, place);
            if (style == chosen) focus = card;
        }
        LinearLayout.LayoutParams under = new LinearLayout.LayoutParams(-2, -2);
        under.topMargin = dp(16);
        page.addView(row, under);
        focus.requestFocus();
    }

    /** A picture with rounded corners and its name under it; the focus rings it and lifts it a little. */
    private View homeCard(int picture, String name, Runnable chosen) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setFocusable(true);
        card.setClickable(true);
        card.setDefaultFocusHighlightEnabled(false);
        FrameLayout frame = new FrameLayout(this);
        ImageView image = new ImageView(this);
        image.setImageResource(picture);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(HOME_CARD_RADIUS));
            }
        });
        image.setClipToOutline(true);
        frame.addView(image, new FrameLayout.LayoutParams(-1, -1));
        GradientDrawable rest = new GradientDrawable();
        rest.setCornerRadius(dp(HOME_CARD_RADIUS));
        rest.setStroke(dp(1), Color.argb(70, 255, 255, 255));
        GradientDrawable ring = new GradientDrawable();
        ring.setCornerRadius(dp(HOME_CARD_RADIUS));
        // Thinner than at first: 3 dp was too heavy round a small picture (the user, 2026-11-09).
        ring.setStroke(dp(2), Color.WHITE);
        frame.setForeground(rest);
        card.addView(frame, new LinearLayout.LayoutParams(dp(HOME_CARD_WIDTH), dp(HOME_CARD_HEIGHT)));
        TextView label = label(name, HOME_NAME_SP);
        label.setGravity(Gravity.CENTER);
        label.setAlpha(0.7f);
        LinearLayout.LayoutParams under = new LinearLayout.LayoutParams(-2, -2);
        under.topMargin = dp(12);
        card.addView(label, under);
        card.setOnFocusChangeListener((view, focused) -> {
            frame.setForeground(focused ? ring : rest);
            label.setAlpha(focused ? 1f : 0.7f);
            label.setTypeface(Typeface.create(focused ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
            float scale = focused ? 1.06f : 1f;
            view.animate().scaleX(scale).scaleY(scale).setDuration(120).start();
        });
        card.setOnClickListener(view -> chosen.run());
        return card;
    }

    private void showRemote() {
        heading(words(R.string.first_remote_title), words(R.string.first_remote_text));
        LinearLayout.LayoutParams place = new LinearLayout.LayoutParams(dp(GUIDE_WIDTH), dp(GUIDE_HEIGHT));
        place.topMargin = dp(GUIDE_GAP);
        page.addView(new Guide(this), place);
        TextView quiet = label(words(R.string.first_remote_note), NOTE_SP);
        quiet.setGravity(Gravity.CENTER);
        quiet.setAlpha(0.55f);
        LinearLayout.LayoutParams below = new LinearLayout.LayoutParams(dp(TEXT_WIDTH), -2);
        below.topMargin = dp(GUIDE_GAP);
        page.addView(quiet, below);
        View start = button(words(R.string.first_start), BUTTON_SP, this::next);
        page.addView(start, place(WIDE, 14));
        start.requestFocus();
    }

    private void heading(String title, String text) {
        page.removeAllViews();
        TextView name = label(title, TITLE_SP);
        name.setGravity(Gravity.CENTER);
        name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        page.addView(name);
        if (text == null) return;
        note = label(text, TEXT_SP);
        note.setGravity(Gravity.CENTER);
        note.setAlpha(0.8f);
        note.setPadding(0, dp(8), 0, 0);
        page.addView(note, new LinearLayout.LayoutParams(dp(TEXT_WIDTH), -2));
    }

    private TextView value(String text) {
        TextView view = label(text, VALUE_SP);
        view.setGravity(Gravity.CENTER);
        view.setPadding(0, dp(18), 0, 0);
        return view;
    }

    private LinearLayout.LayoutParams place(int width, int top) {
        LinearLayout.LayoutParams place = new LinearLayout.LayoutParams(dp(width), -2);
        place.topMargin = dp(top);
        return place;
    }

    private EditText field(String hint, Runnable done) {
        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setImeOptions(EditorInfo.IME_ACTION_DONE);
        field.setGravity(Gravity.CENTER);
        field.setTextColor(Color.WHITE);
        field.setTextSize(BUTTON_SP);
        field.setHint(hint);
        field.setHintTextColor(Color.argb(130, 255, 255, 255));
        field.setPadding(dp(12), dp(10), dp(12), dp(10));
        field.setDefaultFocusHighlightEnabled(false);
        Runnable paint = () -> {
            GradientDrawable fill = new GradientDrawable();
            fill.setCornerRadius(dp(12));
            fill.setColor(Color.argb(42, 255, 255, 255));
            fill.setStroke(dp(2), field.isFocused() ? Color.WHITE : Color.TRANSPARENT);
            field.setBackground(fill);
        };
        paint.run();
        field.setOnFocusChangeListener((changed, focused) -> paint.run());
        field.setOnEditorActionListener((view, action, event) -> {
            done.run();
            return true;
        });
        return field;
    }

    /** The remote in outline, with a line from each of the three keys to what it does. */
    private final class Guide extends View {
        private static final float BODY_HALF = 56;
        private static final float BODY_BOTTOM = 176;
        private static final float RING_Y = 68;
        private static final float KEYS_Y = 134;
        private static final float KEYS_APART = 31;
        private static final float KEY = 13;
        private static final float REACH = 170;
        private static final float HOME_LINE_END = 192;
        private static final float HOME_NAME_Y = 214;

        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint key = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint name = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint action = new Paint(Paint.ANTI_ALIAS_FLAG);

        Guide(Context context) {
            super(context);
            for (Paint paint : new Paint[]{line, key}) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setStrokeJoin(Paint.Join.ROUND);
            }
            line.setStrokeWidth(px(1.5f));
            line.setColor(Color.WHITE);
            key.setStrokeWidth(px(1.5f));
            key.setColor(ControlPanel.LINKED);
            name.setColor(Color.WHITE);
            name.setTextSize(px(18));
            name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            action.setColor(ControlPanel.LINKED);
            action.setTextSize(px(15));
        }

        private float px(float value) { return value * getResources().getDisplayMetrics().density; }

        @Override protected void onDraw(Canvas canvas) {
            float mid = getWidth() / 2f;
            float corner = px(28);
            float half = px(BODY_HALF);
            canvas.drawRoundRect(new RectF(mid - half, px(1), mid + half, px(BODY_BOTTOM)), corner, corner, line);
            canvas.drawCircle(mid, px(17), px(4), line);
            canvas.drawCircle(mid, px(RING_Y), px(36), line);
            canvas.drawCircle(mid, px(RING_Y), px(12), line);
            float y = px(KEYS_Y);
            float left = mid - px(KEYS_APART);
            float right = mid + px(KEYS_APART);
            for (float x : new float[]{left, mid, right}) canvas.drawCircle(x, y, px(KEY), key);
            canvas.drawPath(backIcon(left, y), line);
            canvas.drawPath(homeIcon(mid, y), line);
            canvas.drawPath(menuIcon(right, y), line);

            canvas.drawLine(left - px(KEY), y, mid - px(REACH), y, key);
            canvas.drawLine(right + px(KEY), y, mid + px(REACH), y, key);
            canvas.drawLine(mid, y + px(KEY), mid, px(HOME_LINE_END), key);

            float gap = px(REACH + 12);
            write(canvas, words(R.string.first_key_back), words(R.string.first_does_tasks), mid - gap, y, Paint.Align.RIGHT);
            write(canvas, words(R.string.first_key_menu), words(R.string.first_does_panel), mid + gap, y, Paint.Align.LEFT);
            name.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(words(R.string.first_key_home), mid, px(HOME_NAME_Y), name);
        }

        private void write(Canvas canvas, String title, String does, float x, float y, Paint.Align align) {
            name.setTextAlign(align);
            action.setTextAlign(align);
            canvas.drawText(title, x, y - px(3), name);
            canvas.drawText(does, x, y + px(18), action);
        }

        private Path backIcon(float x, float y) {
            Path path = new Path();
            path.moveTo(x - px(2), y - px(6));
            path.lineTo(x - px(6), y - px(2));
            path.lineTo(x - px(2), y + px(2));
            path.moveTo(x - px(6), y - px(2));
            path.lineTo(x + px(2), y - px(2));
            path.quadTo(x + px(6), y - px(2), x + px(6), y + px(1.5f));
            path.quadTo(x + px(6), y + px(5), x + px(2), y + px(5));
            path.lineTo(x - px(2), y + px(5));
            return path;
        }

        private Path homeIcon(float x, float y) {
            Path path = new Path();
            path.moveTo(x - px(7), y);
            path.lineTo(x, y - px(6));
            path.lineTo(x + px(7), y);
            path.moveTo(x - px(4.5f), y - px(2));
            path.lineTo(x - px(4.5f), y + px(6));
            path.lineTo(x + px(4.5f), y + px(6));
            path.lineTo(x + px(4.5f), y - px(2));
            return path;
        }

        private Path menuIcon(float x, float y) {
            Path path = new Path();
            for (int row = -1; row <= 1; row++) {
                path.moveTo(x - px(6), y + row * px(4));
                path.lineTo(x + px(6), y + row * px(4));
            }
            return path;
        }
    }
}
