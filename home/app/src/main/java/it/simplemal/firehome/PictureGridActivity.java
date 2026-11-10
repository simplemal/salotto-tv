package it.simplemal.firehome;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.FocusFinder;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.json.JSONException;

/**
 * Backdrops, or the screensaver's pictures, in one page. On the left the collections, as plain
 * words with an icon for their kind: one that changes by itself, one that follows the weather, one
 * that stays. On the right the pictures of the collection with the focus, three across and three
 * rows high, with a line under them that says what it does and the one button it needs.
 *
 * OK on a collection puts it in use. A collection that changes by itself keeps its pictures out of
 * reach until "One picture" is on; then the selection goes to the first and OK shows one as the
 * whole screen, where Confirm keeps it. The weather's pictures are only to look at.
 */
public final class PictureGridActivity extends CardActivity {
    /** Opened for the screensaver's pictures instead of the Home's backdrop. */
    static final String SAVER = "saver";
    private static final int UPLOAD = 1;

    private static final int STANDARD = 0;
    private static final int TIMED = 1;
    private static final int WEATHER = 2;
    private static final int PERSONAL = 3;

    private static final int COLUMNS = 3;
    private static final int ROWS_SHOWN = 3;
    private static final int CELL_WIDTH = 196;
    private static final int CELL_HEIGHT = 110;
    private static final int GAP = 12;
    private static final int RADIUS = 10;
    private static final int EDGE = 2;
    /** The edge of a selected cell stays inside the scrolling area. */
    private static final int SIDE_ROOM = 4;
    private static final int PAGE_START = 34;
    private static final int PAGE_TOP = 24;
    private static final int PAGE_END = 24;
    private static final int LIST_WIDTH = 214;
    private static final int LIST_ROW = 38;
    private static final int LIST_SP = 16;
    private static final int ICON = 18;
    private static final int DIVIDER_ROOM = 14;
    private static final int BAR_HEIGHT = 36;
    private static final int BAR_GAP = 8;
    private static final int INFO_TOP = 10;
    /** Two lines of words, or one button: the same height for every collection. */
    private static final int INFO_HEIGHT = 40;
    private static final int KIND_ROOM = 14;
    private static final int INFO_SP = 14;
    private static final float INFO_ALPHA = 0.72f;
    private static final float LOCKED_ALPHA = 0.5f;
    private static final long SHOW_PAUSE_MS = 150;
    private static final long DOWNLOAD_LOOK_MS = 400;
    private static final int MARK = 22;
    private static final int NOTE_SP = 14;
    private static final int NOTE_MARGIN = 24;
    private static final int BUTTON_SP = 16;
    private static final int BUTTON_WIDTH = 170;
    private static final int BUTTON_GAP = 14;
    private static final int BUTTON_BOTTOM = 48;
    /** A selected button grows a little, and would be cut at the edge of its row. */
    private static final int BUTTON_ROOM = 8;
    private static final int ARROW_SP = 34;
    private static final float ARROW_ALPHA = 0.55f;
    /** The guillemet sits low in its line: raised to the middle of Confirm. */
    private static final int ARROW_RISE = 3;
    private static final float ARROW_SHADOW = 8;
    private static final int ARROW_SHADOW_COLOR = Color.argb(170, 0, 0, 0);
    private static final int PAGE_WHEEL = 48;
    private static final int CELL_WHEEL = 28;
    private static final long FADE_MS = 140;
    private static final int OPAQUE = 255;
    private static final int TRASH_GAP = 14;
    /** The icon inside the circle, as large as the X in its own. */
    private static final int TRASH_INSET = 8;
    private static final int QUIET_SP = 13;
    private static final int QUIET_ICON = 15;
    private static final int QUIET_ICON_GAP = 6;
    private static final int QUIET_SIDE = 12;
    private static final int QUIET_TOP = 5;
    private static final int QUIET_RADIUS = 14;
    private static final float QUIET_ALPHA = 0.45f;
    private static final int QUIET_RIM = Color.argb(60, 255, 255, 255);
    private static final int PILL_SP = 15;
    /** A cell needs only this much of a picture: a phone's photo is many times larger. */
    private static final int CELL_PIXELS = 432;
    private static final int SCREEN_PIXELS = 1920;

    /** One line on the left: a name, the folder of its pictures, and what to save to put it in use. */
    private static final class Collection {
        final String name;
        final String folder;
        final String key;
        final int kind;
        /** Not on the TV: listed with an arrow, and Download brings it. */
        boolean missing;

        Collection(String name, String folder, String key, int kind) {
            this.name = name;
            this.folder = folder;
            this.key = key;
            this.kind = kind;
        }
    }

    /** Moves by whole rows itself, from the selected cell: the three on screen are always whole. */
    private static final class GridScroll extends ScrollView {
        GridScroll(Context context) { super(context); }

        @Override protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect) { return 0; }
    }

    private boolean saver;
    private final List<Collection> collections = new ArrayList<>();
    private final List<LinearLayout> items = new ArrayList<>();
    /** The collection whose pictures are on the right. */
    private int current = -1;
    private final Runnable showFocused = this::showFocused;
    private int focusedItem;
    private int personalCount;
    private boolean singleOn;
    /** Counts the collections shown: work that finishes for one already gone is dropped. */
    private int shownCount;

    private final List<ImageView> cells = new ArrayList<>();
    private final List<FrameLayout> places = new ArrayList<>();
    private final List<View> marks = new ArrayList<>();
    private List<String> pictures = new ArrayList<>();
    private boolean[] asked;
    private boolean[] sharpened;
    /** One cell at a time is read again from its picture, so that the next can still be the one selected. */
    private boolean sharpening;
    private boolean firstShown;
    private boolean files;
    private boolean personal;
    /** The folder of one of the app's sets, whose pictures can be taken away. */
    private String set;

    private FrameLayout root;
    private ImageView backdropView;
    private LinearLayout list;
    private TextView setTitle;
    private TextView kindLabel;
    private TextView deleteSet;
    private LinearLayout single;
    private LinearLayout rows;
    private GridScroll scroll;
    private TextView info;
    private TextView action;
    private Runnable actionRun;
    /** The weather's place, up in the bar where the others have their switch. */
    private TextView placeButton;
    private ProgressBar pageWheel;
    private FrameLayout preview;
    private ImageView full;
    private TextView before;
    private TextView after;
    private TextView confirmButton;
    private ImageView trash;
    private String previewed;
    private int previewIndex;
    private ImageView opened;
    private GradientDrawable edge;
    private int previews;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        saver = getIntent().getBooleanExtra(SAVER, false);
        Look.tidyPersonal(this);
        root = new FrameLayout(this);
        backdropView = new ImageView(this);
        backdropView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backdropView.setBackground(MenuBackdrop.base(this));
        root.addView(backdropView, new FrameLayout.LayoutParams(-1, -1));
        dressBackdrop();

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(PAGE_START), dp(PAGE_TOP), dp(PAGE_END), 0);
        TextView eyebrow = label(getString(saver ? R.string.settings_saver : R.string.settings_cat_home), 13);
        eyebrow.setAlpha(0.6f);
        eyebrow.setPadding(dp(14), 0, 0, 0);
        page.addView(eyebrow);
        // The page's title on the same line as the collection's: the lists under them start together.
        LinearLayout body = new LinearLayout(this);
        page.addView(body, new LinearLayout.LayoutParams(-1, bodyHeight()));
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        TextView title = label(getString(saver ? R.string.grid_saver_title : R.string.settings_backdrop), 26);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setIncludeFontPadding(false);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(dp(14), 0, 0, 0);
        LinearLayout.LayoutParams titlePlace = new LinearLayout.LayoutParams(-1, dp(BAR_HEIGHT));
        titlePlace.bottomMargin = dp(BAR_GAP);
        left.addView(title, titlePlace);
        ScrollView listScroll = new ScrollView(this);
        listScroll.setVerticalScrollBarEnabled(false);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(SIDE_ROOM), 0, 0);
        listScroll.addView(list);
        left.addView(listScroll, new LinearLayout.LayoutParams(-1, 0, 1));
        body.addView(left, new LinearLayout.LayoutParams(dp(LIST_WIDTH), -1));
        View divider = new View(this);
        divider.setBackgroundColor(Color.argb(40, 255, 255, 255));
        LinearLayout.LayoutParams line = new LinearLayout.LayoutParams(Math.max(1, dp(1)), -1);
        line.setMargins(dp(DIVIDER_ROOM), 0, dp(DIVIDER_ROOM), 0);
        body.addView(divider, line);
        body.addView(rightColumn(), new LinearLayout.LayoutParams(gridWidth(), -1));
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));

        pageWheel = new ProgressBar(this);
        pageWheel.setIndeterminateTintList(ColorStateList.valueOf(Color.WHITE));
        pageWheel.setVisibility(View.GONE);
        root.addView(pageWheel, new FrameLayout.LayoutParams(dp(PAGE_WHEEL), dp(PAGE_WHEEL), Gravity.CENTER));
        preview = previewPage();
        root.addView(preview, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);

        collect();
        int start = Math.max(0, inUse());
        buildList();
        showCollection(start);
        items.get(start).requestFocus();
    }

    /** The Home's backdrop blurred, made beforehand by the Home: the same as under the settings. */
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

    // ---------- the collections ----------

    /** Only what is whole on the TV, or inside the app, and was not taken away. */
    private void collect() {
        collections.clear();
        // Counted once here: the list asks at every move of the selection.
        personalCount = Look.images(this).size();
        if (!saver) collections.add(new Collection(getString(R.string.settings_backdrop_standard), null, Look.STANDARD_TIMED, STANDARD));
        String[][] sets = {{Look.ABSTRACT, Look.ABSTRACT_FOLDER}, {Look.ABSTRACT_3D, Look.ABSTRACT_3D_FOLDER},
            {Look.NOX, Look.NOX_FOLDER}, {Look.NATURE, Look.NATURE_FOLDER}};
        for (String[] one : sets) add(one, TIMED);
        collections.add(new Collection(getString(R.string.settings_backdrop_personal), Look.personalFolder(this).getPath(), Look.PERSONAL, PERSONAL));
        if (saver) return;
        String[][] weather = {{Look.PICTURES, Look.PICTURES_FOLDER}, {Look.PHOTOS, Look.PHOTOS_FOLDER}, {Look.AERIAL, Look.AERIAL_FOLDER}};
        for (String[] one : weather) add(one, WEATHER);
    }

    /** Every collection has its line: one not on the TV, or taken away, with the way to bring it. */
    private void add(String[] one, int kind) {
        Collection collection = new Collection(Look.backdropName(this, one[0]), one[1], one[0], kind);
        collection.missing = !Packs.listed(this, one[1]) || Look.setTaken(this, one[1]);
        collections.add(collection);
    }

    /** From two photos on, Personale can change by itself like the app's sets. */
    private boolean personalTimes() { return personalCount >= Look.PERSONAL_TIMED_FROM; }

    private boolean changesItself(Collection collection) {
        if (collection.missing) return false;
        return collection.kind == TIMED || collection.kind == STANDARD || collection.kind == PERSONAL && personalTimes();
    }

    /** Which collection is in use now, or -1. */
    private int inUse() {
        for (int index = 0; index < collections.size(); index++) {
            if (uses(collections.get(index))) return index;
        }
        return -1;
    }

    private boolean uses(Collection collection) {
        if (collection.missing) return false;
        if (saver) {
            String folder = Look.saverFolder(this);
            if (collection.kind == PERSONAL) return folder.startsWith("/");
            return folder.equals(collection.folder);
        }
        String chosen = Look.backdrop(this);
        switch (collection.kind) {
            case STANDARD: return Look.STANDARD_TIMED.equals(chosen) || Arrays.asList(Look.PRESET_NAMES).contains(chosen);
            case TIMED: return chosen.equals(collection.key) || chosen.startsWith(collection.folder + "/");
            case PERSONAL: return Look.PERSONAL.equals(chosen) || Look.personal(this, chosen);
            default: return chosen.equals(collection.key);
        }
    }

    /** The one picture kept, as its cell names it, when the collection in use shows only that. */
    private String chosenPicture(Collection collection) {
        if (!uses(collection)) return null;
        if (saver) {
            String picture = Look.saverPicture(this);
            if (picture == null) return null;
            return collection.kind == PERSONAL ? new File(picture).getName() : picture;
        }
        String chosen = Look.backdrop(this);
        if (collection.kind == STANDARD) return Arrays.asList(Look.PRESET_NAMES).contains(chosen) ? chosen : null;
        if (collection.kind == TIMED && chosen.startsWith(collection.folder + "/")) return chosen;
        if (collection.kind == PERSONAL && Look.personal(this, chosen)) return chosen;
        return null;
    }

    private void buildList() {
        list.removeAllViews();
        items.clear();
        boolean grouped = false;
        for (int index = 0; index < collections.size(); index++) {
            Collection collection = collections.get(index);
            if (collection.kind == WEATHER && !grouped) {
                grouped = true;
                TextView group = label(getString(R.string.grid_weather_group), 12);
                group.setAlpha(0.5f);
                group.setPadding(dp(14), dp(12), 0, dp(4));
                list.addView(group);
            }
            LinearLayout item = item(index);
            items.add(item);
            LinearLayout.LayoutParams place = new LinearLayout.LayoutParams(-1, dp(LIST_ROW));
            place.bottomMargin = dp(2);
            list.addView(item, place);
        }
        paintList();
    }

    /** As the collection is set now: the arrow that turns while it changes by itself, a picture with one picture. */
    private int iconOf(int index) {
        Collection collection = collections.get(index);
        if (collection.missing) return R.drawable.ic_download;
        if (collection.kind == WEATHER) return R.drawable.ic_cloud;
        boolean one = index == current ? singleOn : chosenPicture(collection) != null;
        return changesItself(collection) && !one ? R.drawable.ic_restart : R.drawable.ic_image;
    }

    private LinearLayout item(int index) {
        Collection collection = collections.get(index);
        LinearLayout item = new LinearLayout(this);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(dp(14), 0, dp(12), 0);
        item.setFocusable(true);
        item.setClickable(true);
        item.setDefaultFocusHighlightEnabled(false);
        ImageView icon = new ImageView(this);
        item.addView(icon, new LinearLayout.LayoutParams(dp(ICON), dp(ICON)));
        TextView name = label(collection.name, LIST_SP);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setPaddingRelative(dp(12), 0, dp(6), 0);
        item.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        TextView used = label("✓", LIST_SP);
        item.addView(used);
        item.setTag(new Drawable[]{selectedFill(dp(10)), softGlass(dp(10))});
        item.setOnFocusChangeListener((view, focused) -> {
            if (focused) {
                focusedItem = index;
                // Moving quickly down the list draws only the collection where it stops.
                list.removeCallbacks(showFocused);
                if (index != current) list.postDelayed(showFocused, SHOW_PAUSE_MS);
            }
            paintList();
        });
        item.setOnClickListener(view -> use(index));
        return item;
    }

    /** Only if the focus is still there: passing through on its way elsewhere does not count. */
    private void showFocused() {
        if (focusedItem != current && items.get(focusedItem).hasFocus()) showCollection(focusedItem);
    }

    /** White under the focus, glass on the one shown on the right, a tick on the one in use. */
    private void paintList() {
        int used = inUse();
        for (int index = 0; index < items.size(); index++) {
            LinearLayout item = items.get(index);
            Drawable[] fills = (Drawable[]) item.getTag();
            boolean lit = item.hasFocus();
            item.setBackground(lit ? fills[0] : index == current ? fills[1] : null);
            int ink = lit ? Fire.INK : Color.WHITE;
            ImageView icon = (ImageView) item.getChildAt(0);
            icon.setImageResource(iconOf(index));
            icon.setImageTintList(ColorStateList.valueOf(ink));
            TextView name = (TextView) item.getChildAt(1);
            name.setTextColor(ink);
            name.setTypeface(Typeface.create(index == current ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
            TextView tick = (TextView) item.getChildAt(2);
            tick.setTextColor(ink);
            tick.setVisibility(index == used ? View.VISIBLE : View.INVISIBLE);
        }
    }

    /** OK on a collection: in use, as it is meant to be. One that stays waits for a picture instead. */
    private void use(int index) {
        list.removeCallbacks(showFocused);
        if (index != current) showCollection(index);
        Collection collection = collections.get(index);
        if (collection.missing) {
            action.requestFocus();
            return;
        }
        if (collection.kind == WEATHER) {
            // Only without a place: with the weather off the Home it was asked for at every press.
            if (Weather.place(this) != null) putInUse(collection);
            else askPlace();
            return;
        }
        if (changesItself(collection)) {
            singleOn = false;
            putInUse(collection);
            return;
        }
        // Standard, or Personale with fewer than two photos: a picture has to be picked.
        if (!cells.isEmpty()) focusCell(chosenIndex());
        else if (action.getVisibility() == View.VISIBLE) action.requestFocus();
    }

    /** The whole collection: one that changes by itself, or the weather. */
    private void putInUse(Collection collection) {
        if (saver) {
            Look.setSaverFolder(this, collection.folder);
            Look.setSaverPicture(this, null);
        } else {
            Look.setBackdrop(this, collection.key);
            dressBackdrop();
        }
        refreshState();
    }

    // ---------- the right side ----------

    private View rightColumn() {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(SIDE_ROOM), 0, dp(SIDE_ROOM), 0);
        setTitle = label("", 19);
        setTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        setTitle.setSingleLine(true);
        setTitle.setEllipsize(TextUtils.TruncateAt.END);
        bar.addView(setTitle, new LinearLayout.LayoutParams(0, -2, 1));
        // What the collection does now, next to the switch that changes it.
        kindLabel = label("", 13);
        kindLabel.setAlpha(0.6f);
        kindLabel.setSingleLine(true);
        LinearLayout.LayoutParams kindPlace = new LinearLayout.LayoutParams(-2, -2);
        kindPlace.setMarginStart(dp(KIND_ROOM));
        kindPlace.setMarginEnd(dp(KIND_ROOM));
        bar.addView(kindLabel, kindPlace);
        placeButton = pill(getString(R.string.grid_change_place));
        placeButton.setOnClickListener(view -> askPlace());
        bar.addView(placeButton, new LinearLayout.LayoutParams(-2, dp(BAR_HEIGHT - 2)));
        single = new LinearLayout(this);
        single.setGravity(Gravity.CENTER_VERTICAL);
        single.setPadding(dp(12), 0, dp(14), 0);
        single.setFocusable(true);
        single.setClickable(true);
        single.setDefaultFocusHighlightEnabled(false);
        single.setOnClickListener(view -> toggleSingle());
        single.setOnFocusChangeListener((view, focused) -> paintSingle());
        bar.addView(single, new LinearLayout.LayoutParams(-2, dp(BAR_HEIGHT - 2)));
        LinearLayout.LayoutParams barPlace = new LinearLayout.LayoutParams(-1, dp(BAR_HEIGHT));
        barPlace.bottomMargin = dp(BAR_GAP);
        column.addView(bar, barPlace);

        scroll = new GridScroll(this);
        scroll.setVerticalScrollBarEnabled(false);
        // With its pictures out of reach Down would otherwise stop on the scrolling area itself.
        scroll.setFocusable(false);
        scroll.setClipToPadding(false);
        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding(dp(SIDE_ROOM), dp(SIDE_ROOM), dp(SIDE_ROOM), dp(SIDE_ROOM));
        scroll.addView(rows);
        scroll.setOnScrollChangeListener((view, x, y, oldX, oldY) -> {
            loadInView();
            sharpenInView();
        });
        column.addView(scroll, new LinearLayout.LayoutParams(-1, gridHeight()));

        LinearLayout infoRow = new LinearLayout(this);
        infoRow.setGravity(Gravity.CENTER_VERTICAL);
        infoRow.setPadding(dp(SIDE_ROOM), 0, dp(SIDE_ROOM), 0);
        info = label("", INFO_SP);
        info.setAlpha(INFO_ALPHA);
        info.setMaxLines(2);
        info.setLineSpacing(dp(2), 1f);
        infoRow.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
        action = pill("");
        action.setOnClickListener(view -> {
            if (actionRun != null) actionRun.run();
        });
        LinearLayout.LayoutParams actionPlace = new LinearLayout.LayoutParams(-2, dp(BAR_HEIGHT - 2));
        actionPlace.setMarginStart(dp(16));
        infoRow.addView(action, actionPlace);
        deleteSet = quietButton(getString(R.string.grid_delete_set), this::askDeleteSet);
        LinearLayout.LayoutParams quiet = new LinearLayout.LayoutParams(-2, -2);
        quiet.setMarginStart(dp(16));
        infoRow.addView(deleteSet, quiet);
        LinearLayout.LayoutParams infoPlace = new LinearLayout.LayoutParams(-1, dp(INFO_HEIGHT));
        infoPlace.topMargin = dp(INFO_TOP);
        column.addView(infoRow, infoPlace);
        return column;
    }

    /** A rounded button of glass, white under the focus. */
    private TextView pill(String text) {
        TextView pill = label(text, PILL_SP);
        pill.setGravity(Gravity.CENTER);
        pill.setPadding(dp(18), 0, dp(18), 0);
        pill.setFocusable(true);
        pill.setClickable(true);
        pill.setDefaultFocusHighlightEnabled(false);
        Drawable rest = softGlass(dp(17));
        Drawable lit = selectedFill(dp(17));
        pill.setBackground(rest);
        pill.setOnFocusChangeListener((view, focused) -> {
            view.setBackground(focused ? lit : rest);
            pill.setTextColor(focused ? Fire.INK : Color.WHITE);
        });
        return pill;
    }

    /** Its switch and its words, drawn again as it changes: the switch has no other way to show it. */
    private void paintSingle() {
        boolean lit = single.hasFocus();
        single.removeAllViews();
        ToggleView toggle = new ToggleView(this, singleOn);
        toggle.setRowFocused(lit);
        single.addView(toggle, new LinearLayout.LayoutParams(dp(34), dp(19)));
        TextView words = label(getString(R.string.grid_single), PILL_SP);
        words.setTextColor(lit ? Fire.INK : Color.WHITE);
        words.setPaddingRelative(dp(10), 0, 0, 0);
        single.addView(words);
        single.setBackground(lit ? selectedFill(dp(17)) : softGlass(dp(17)));
    }

    private void toggleSingle() {
        Collection collection = collections.get(current);
        singleOn = !singleOn;
        if (singleOn) {
            applyLock();
            paintSingle();
            paintBar();
            paintList();
            // On: the selection goes into the pictures, to say that one can now be picked.
            focusCell(chosenIndex());
            return;
        }
        putInUse(collection);
        single.requestFocus();
    }

    private boolean singleShown(Collection collection) { return changesItself(collection); }

    /** The pictures can be reached: always for one that stays, for one that changes only with "One picture". */
    private boolean gridOpen() {
        Collection collection = collections.get(current);
        if (collection.kind == WEATHER || collection.missing) return false;
        return !changesItself(collection) || singleOn;
    }

    /**
     * The bar: what the collection does, only while it changes by itself, next to its switch; for
     * the weather its place instead, which the title already says is the weather's.
     */
    private void paintBar() {
        Collection collection = collections.get(current);
        boolean weather = collection.kind == WEATHER && !collection.missing;
        String kind = collection.missing ? getString(R.string.grid_kind_missing)
            : changesItself(collection) && !singleOn ? getString(R.string.grid_kind_timed) : null;
        kindLabel.setText(kind != null ? kind : "");
        kindLabel.setVisibility(kind != null ? View.VISIBLE : View.GONE);
        single.setVisibility(singleShown(collection) ? View.VISIBLE : View.GONE);
        placeButton.setVisibility(weather ? View.VISIBLE : View.GONE);
        if (weather) placeButton.setText(getString(Weather.place(this) != null ? R.string.grid_change_place : R.string.grid_choose_place));
    }

    /** The picture the line names: the one selected, else the one kept, else none. */
    private String namedPicture() {
        int focused = cells.indexOf(getCurrentFocus());
        if (focused >= 0) return pictures.get(focused);
        return chosenPicture(collections.get(current));
    }

    /** What the line under the pictures says, and the button it needs. */
    private void updateInfo() {
        Collection collection = collections.get(current);
        String text;
        actionRun = null;
        String button = null;
        if (collection.missing) {
            text = downloadText(collection);
            button = Packs.fetching() ? null : getString(R.string.grid_download);
            actionRun = () -> download(collection);
        } else if (collection.kind == WEATHER) {
            String place = Weather.place(this);
            text = place != null ? getString(R.string.grid_info_weather, place) : getString(R.string.grid_info_weather_none);
        } else if (collection.kind == PERSONAL && pictures.isEmpty()) {
            text = getString(R.string.grid_personal_empty);
        } else if (changesItself(collection) && !singleOn) {
            text = getString(saver ? R.string.grid_info_timed_saver : R.string.grid_info_timed);
        } else {
            // One picture: its name, the one selected or the one kept.
            String named = namedPicture();
            text = named != null ? Look.backdropName(this, named) : getString(R.string.grid_info_single);
        }
        if (collection.kind == PERSONAL) {
            button = getString(R.string.grid_upload);
            actionRun = () -> startActivityForResult(new Intent(this, UploadActivity.class), UPLOAD);
        }
        info.setText(text);
        action.setText(button != null ? button : "");
        action.setVisibility(button != null ? View.VISIBLE : View.GONE);
    }

    /** After a change: ticks, the pictures that can be reached, and the words under them. */
    private void refreshState() {
        Collection collection = collections.get(current);
        if (!changesItself(collection)) singleOn = false;
        else if (chosenPicture(collection) != null) singleOn = true;
        paintList();
        paintBar();
        paintSingle();
        applyLock();
        markChosen();
        updateInfo();
    }

    private void applyLock() {
        boolean open = gridOpen();
        for (ImageView cell : cells) cell.setFocusable(open);
        if (rows.getAlpha() > 0) rows.setAlpha(open ? 1f : LOCKED_ALPHA);
    }

    private float gridAlpha() { return gridOpen() ? 1f : LOCKED_ALPHA; }

    // ---------- the grid ----------

    private List<String> picturesOf(Collection collection) {
        List<String> found = new ArrayList<>();
        if (collection.missing) return found;
        if (collection.kind == STANDARD) {
            found.addAll(Arrays.asList(Look.PRESET_NAMES));
        } else if (collection.kind == PERSONAL) {
            for (File image : Look.images(this)) found.add(image.getName());
        } else {
            try {
                collectPictures(collection.folder, found);
            } catch (IOException e) {
                say(getString(R.string.card_app_unavailable));
            }
        }
        return found;
    }

    /** The weather's pictures sit in a folder for each moment of the day. */
    private void collectPictures(String folder, List<String> found) throws IOException {
        String[] names = Packs.list(this, folder);
        Arrays.sort(names);
        for (String name : names) {
            String file = folder + "/" + name;
            if (name.endsWith(Weather.PICTURE_FILE)) {
                if (!Look.taken(this, file)) found.add(file);
            } else if (name.indexOf('.') < 0) {
                collectPictures(file, found);
            }
        }
    }

    /** A new collection on the right: the same place and size, the cells filled as they are read. */
    private void showCollection(int index) {
        shownCount++;
        current = index;
        Collection collection = collections.get(index);
        pictures = picturesOf(collection);
        personal = collection.kind == PERSONAL;
        files = !personal && collection.kind != STANDARD;
        set = collection.kind == TIMED && !collection.missing ? collection.folder : null;
        singleOn = changesItself(collection) && chosenPicture(collection) != null;
        cells.clear();
        places.clear();
        marks.clear();
        sharpening = false;
        firstShown = false;
        rows.removeAllViews();
        rows.animate().cancel();
        scroll.scrollTo(0, 0);
        LinearLayout row = null;
        for (int cellIndex = 0; cellIndex < pictures.size(); cellIndex++) {
            if (cellIndex % COLUMNS == 0) {
                row = new LinearLayout(this);
                LinearLayout.LayoutParams place = new LinearLayout.LayoutParams(-2, -2);
                place.topMargin = cellIndex == 0 ? 0 : dp(GAP);
                rows.addView(row, place);
            }
            ImageView cell = cell(cellIndex);
            cells.add(cell);
            FrameLayout holder = new FrameLayout(this);
            holder.addView(cell, new FrameLayout.LayoutParams(-1, -1));
            TextView mark = label("✓", 14);
            mark.setGravity(Gravity.CENTER);
            mark.setTextColor(Fire.INK);
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            dot.setColor(Color.WHITE);
            mark.setBackground(dot);
            mark.setVisibility(View.GONE);
            FrameLayout.LayoutParams corner = new FrameLayout.LayoutParams(dp(MARK), dp(MARK), Gravity.TOP | Gravity.END);
            corner.setMargins(0, dp(6), dp(6), 0);
            holder.addView(mark, corner);
            marks.add(mark);
            places.add(holder);
            LinearLayout.LayoutParams place = new LinearLayout.LayoutParams(dp(CELL_WIDTH), dp(CELL_HEIGHT));
            place.setMarginStart(cellIndex % COLUMNS == 0 ? 0 : dp(GAP));
            row.addView(holder, place);
        }
        // Not on the TV: empty places where its pictures will be.
        if (collection.missing) {
            for (int place = 0; place < COLUMNS * ROWS_SHOWN; place++) {
                if (place % COLUMNS == 0) {
                    row = new LinearLayout(this);
                    LinearLayout.LayoutParams line = new LinearLayout.LayoutParams(-2, -2);
                    line.topMargin = place == 0 ? 0 : dp(GAP);
                    rows.addView(row, line);
                }
                View empty = new View(this);
                GradientDrawable fill = new GradientDrawable();
                fill.setCornerRadius(dp(RADIUS));
                fill.setColor(Color.argb(14, 255, 255, 255));
                fill.setStroke(Math.max(1, dp(1)), Color.argb(26, 255, 255, 255));
                empty.setBackground(fill);
                LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(dp(CELL_WIDTH), dp(CELL_HEIGHT));
                size.setMarginStart(place % COLUMNS == 0 ? 0 : dp(GAP));
                row.addView(empty, size);
            }
            weigh(collection);
        }
        setTitle.setText(collection.name);
        deleteSet.setVisibility(set != null ? View.VISIBLE : View.GONE);
        refreshState();
        int chosen = chosenIndex();
        if (chosen >= 0) showRowOf(chosen, false);
        if (files) {
            asked = new boolean[pictures.size()];
            sharpened = new boolean[pictures.size()];
            rows.setAlpha(0);
            int shown = shownCount;
            // Only once it is laid out, and scrolled to the chosen cell, is it known what is in view.
            scroll.post(() -> {
                if (shown == shownCount) showFirstScreen();
            });
        } else {
            rows.setAlpha(gridAlpha());
            if (personal) loadPersonal();
        }
    }

    private int chosenIndex() {
        String chosen = chosenPicture(collections.get(current));
        return chosen == null ? 0 : Math.max(0, pictures.indexOf(chosen));
    }

    private void markChosen() {
        String chosen = chosenPicture(collections.get(current));
        boolean shows = chosen != null && (!changesItself(collections.get(current)) || singleOn);
        for (int index = 0; index < marks.size(); index++) {
            marks.get(index).setVisibility(shows && pictures.get(index).equals(chosen) ? View.VISIBLE : View.GONE);
        }
    }

    private void focusCell(int index) {
        if (cells.isEmpty() || !gridOpen()) return;
        int place = Math.min(Math.max(0, index), cells.size() - 1);
        showRowOf(place, true);
        cells.get(place).requestFocus();
    }

    private ImageView cell(int index) {
        ImageView cell = new ImageView(this);
        cell.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cell.setBackgroundColor(Color.rgb(28, 30, 36));
        cell.setClickable(true);
        cell.setDefaultFocusHighlightEnabled(false);
        cell.setClipToOutline(true);
        cell.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(RADIUS));
            }
        });
        Bitmap kept = files ? Thumbnails.kept(pictures.get(index)) : null;
        if (kept != null) cell.setImageBitmap(kept);
        else if (!files && !personal) cell.setImageDrawable(Look.preset(pictures.get(index)));
        cell.setOnFocusChangeListener((view, focused) -> {
            view.setForeground(focused ? edge() : null);
            // After the focus has settled: leaving a cell for another names the new one.
            info.post(() -> {
                if (!isDestroyed() && index < cells.size() && cells.get(index) == view) updateInfo();
            });
            if (!focused) return;
            showRowOf(index, true);
            if (files) sharpenInView();
        });
        cell.setOnClickListener(view -> {
            if (gridOpen()) showPreview(index);
        });
        return cell;
    }

    private GradientDrawable edge() {
        if (edge == null) {
            edge = new GradientDrawable();
            edge.setCornerRadius(dp(RADIUS));
            edge.setStroke(dp(EDGE), Color.WHITE);
        }
        return edge;
    }

    /** The selected row stays in view, and the rows on screen are always three whole ones. */
    private void showRowOf(int index, boolean smooth) {
        int row = index / COLUMNS;
        int step = dp(CELL_HEIGHT) + dp(GAP);
        int first = scroll.getScrollY() / step;
        if (row < first) first = row;
        else if (row >= first + ROWS_SHOWN) first = row - ROWS_SHOWN + 1;
        if (smooth) scroll.smoothScrollTo(0, first * step);
        else scroll.scrollTo(0, first * step);
    }

    /** Few pictures, each read small: one after the other. */
    private void loadPersonal() {
        List<File> found = new ArrayList<>();
        for (String name : pictures) found.add(new File(Look.personalFolder(this), name));
        int shown = shownCount;
        Background.draw(() -> {
            for (int index = 0; index < found.size(); index++) {
                if (isDestroyed() || shown != shownCount) return;
                Bitmap small = decodeFile(found.get(index), CELL_PIXELS);
                int place = index;
                if (small != null) runOnUiThread(() -> {
                    if (shown == shownCount) appear(cells.get(place), small);
                });
            }
        });
    }

    /** At least `width` pixels across, and never the whole of a phone's photo. */
    private static Bitmap decodeFile(File file, int width) {
        BitmapFactory.Options size = new BitmapFactory.Options();
        size.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getPath(), size);
        BitmapFactory.Options options = new BitmapFactory.Options();
        int sample = 1;
        while (size.outWidth / (sample * 2) >= width) sample *= 2;
        options.inSampleSize = sample;
        return BitmapFactory.decodeFile(file.getPath(), options);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        // What came in, and Personale may now be able to change by itself: list and grid again.
        if (request == UPLOAD && result == RESULT_OK) reload();
    }

    /** After an upload or something taken away: the same collection if it is still there. */
    private void reload() {
        String was = current >= 0 && current < collections.size() ? collections.get(current).name : null;
        collect();
        buildList();
        int again = 0;
        for (int index = 0; index < collections.size(); index++) {
            if (collections.get(index).name.equals(was)) again = index;
        }
        showCollection(again);
        items.get(again).requestFocus();
    }

    /** Every cell the screen shows now, the ones its edge cuts too. */
    private List<Integer> inView() {
        int top = scroll.getScrollY();
        int bottom = top + scroll.getHeight();
        List<Integer> seen = new ArrayList<>();
        for (int index = 0; index < places.size(); index++) {
            View row = (View) places.get(index).getParent();
            if (row.getBottom() > top && row.getTop() < bottom) seen.add(index);
        }
        return seen;
    }

    private List<Integer> missing() {
        List<Integer> missing = new ArrayList<>();
        if (asked == null) return missing;
        for (int index : inView()) {
            if (index < asked.length && cells.get(index).getDrawable() == null && !asked[index]) missing.add(index);
        }
        return missing;
    }

    /** The first screen comes all at once, even when nothing has been read yet. */
    private void showFirstScreen() {
        List<Integer> missing = missing();
        int shown = shownCount;
        if (missing.isEmpty()) {
            rows.animate().alpha(gridAlpha()).setDuration(FADE_MS).start();
            firstShown = true;
            sharpenInView();
            return;
        }
        pageWheel.setVisibility(View.VISIBLE);
        for (int index : missing) asked[index] = true;
        List<String> names = new ArrayList<>(pictures);
        Background.draw(() -> {
            List<Bitmap> read = new ArrayList<>();
            for (int index : missing) {
                if (isDestroyed() || shown != shownCount) return;
                read.add(Thumbnails.read(this, names.get(index)));
            }
            runOnUiThread(() -> {
                if (shown != shownCount) return;
                for (int place = 0; place < missing.size(); place++) {
                    if (read.get(place) != null) cells.get(missing.get(place)).setImageBitmap(read.get(place));
                }
                pageWheel.setVisibility(View.GONE);
                rows.animate().alpha(gridAlpha()).setDuration(FADE_MS).start();
                firstShown = true;
                loadInView();
                sharpenInView();
            });
        });
    }

    /** From there on, only what scrolling brings in: each cell on its own, as it is ready. */
    private void loadInView() {
        if (!firstShown || !files) return;
        for (int index : missing()) {
            asked[index] = true;
            load(index);
        }
    }

    private void load(int index) {
        FrameLayout place = places.get(index);
        ProgressBar wheel = new ProgressBar(this);
        wheel.setIndeterminateTintList(ColorStateList.valueOf(Color.WHITE));
        place.addView(wheel, new FrameLayout.LayoutParams(dp(CELL_WHEEL), dp(CELL_WHEEL), Gravity.CENTER));
        String name = pictures.get(index);
        int shown = shownCount;
        Background.draw(() -> {
            if (isDestroyed() || shown != shownCount) return;
            Bitmap quick = Thumbnails.kept(name) == null ? Thumbnails.tiny(this, name) : null;
            if (quick != null) runOnUiThread(() -> {
                if (shown != shownCount) return;
                place.removeView(wheel);
                appear(cells.get(index), quick);
            });
            Bitmap thumbnail = Thumbnails.read(this, name);
            runOnUiThread(() -> {
                if (shown != shownCount) return;
                place.removeView(wheel);
                if (thumbnail == null) return;
                if (quick != null) cells.get(index).setImageBitmap(thumbnail);
                else appear(cells.get(index), thumbnail);
                sharpenInView();
            });
        });
    }

    /**
     * Then the cells on screen once more, from the pictures themselves: one at a time, the selected
     * one first, so that where the selection stops is the next to come.
     */
    private void sharpenInView() {
        if (!firstShown || sharpening || !files) return;
        int next = -1;
        View focused = getCurrentFocus();
        int chosen = focused == null ? -1 : cells.indexOf(focused);
        for (int index : inView()) {
            if (sharpened[index] || cells.get(index).getDrawable() == null) continue;
            if (index == chosen) {
                next = index;
                break;
            }
            if (next < 0) next = index;
        }
        if (next < 0) return;
        int index = next;
        sharpened[index] = true;
        sharpening = true;
        String name = pictures.get(index);
        int shown = shownCount;
        Background.sharpen(() -> {
            Bitmap sharp = isDestroyed() ? null : Thumbnails.sharp(this, name);
            runOnUiThread(() -> {
                if (shown != shownCount || isDestroyed()) return;
                sharpening = false;
                if (sharp != null) cells.get(index).setImageBitmap(sharp);
                sharpenInView();
            });
        });
    }

    private void appear(ImageView cell, Bitmap picture) {
        cell.setImageAlpha(0);
        cell.setImageBitmap(picture);
        ObjectAnimator.ofInt(cell, "imageAlpha", 0, OPAQUE).setDuration(FADE_MS).start();
    }

    private Bitmap decode(String asset) {
        try (InputStream input = Packs.open(this, asset)) {
            return BitmapFactory.decodeStream(input);
        } catch (IOException e) {
            return null;
        }
    }

    // ---------- a collection not on the TV ----------

    private long weight = -1;
    private String weighed;

    /** What it weighs comes from the signed list, on the network: the line says it once it is known. */
    private void weigh(Collection collection) {
        if (collection.folder.equals(weighed)) return;
        int shown = shownCount;
        Background.run(() -> {
            long bytes = Packs.bytes(collection.folder);
            runOnUiThread(() -> {
                weighed = collection.folder;
                weight = bytes;
                if (shown == shownCount && !isDestroyed()) updateInfo();
            });
        });
    }

    private String downloadText(Collection collection) {
        if (Packs.fetching()) {
            int percent = Packs.percent(collection.folder);
            return percent >= 0 ? getString(R.string.grid_downloading, collection.name, percent)
                : getString(R.string.grid_downloading_plain, collection.name);
        }
        if (Packs.stop() == Packs.Stop.ROOM) return getString(R.string.grid_download_room,
            android.text.format.Formatter.formatShortFileSize(this, Packs.roomNeeded()),
            android.text.format.Formatter.formatShortFileSize(this, Packs.roomFree()));
        if (Packs.stop() != Packs.Stop.NONE) return getString(R.string.grid_download_failed);
        if (collection.folder.equals(weighed) && weight > 0) {
            return getString(R.string.grid_info_missing, android.text.format.Formatter.formatShortFileSize(this, weight));
        }
        return getString(R.string.grid_info_missing_plain);
    }

    private final Runnable watchDownload = this::watchDownload;

    /** Asked by the user: as on the first start, with the share that has come shown under the grid. */
    private void download(Collection collection) {
        Background.run(() -> Packs.ask(getApplicationContext(), collection.folder));
        list.postDelayed(watchDownload, DOWNLOAD_LOOK_MS);
        updateInfo();
        items.get(current).requestFocus();
    }

    private void watchDownload() {
        if (isDestroyed()) return;
        if (Packs.fetching()) {
            if (collections.get(current).missing) updateInfo();
            list.postDelayed(watchDownload, DOWNLOAD_LOOK_MS);
            return;
        }
        boolean came = false;
        for (Collection collection : collections) {
            if (collection.missing && Packs.listed(this, collection.folder)) came = true;
        }
        if (came) reload();
        else if (collections.get(current).missing) updateInfo();
    }

    // ---------- the weather's place ----------

    /** The same window as in the settings, over this page: the name, then the places found. */
    private void askPlace() {
        askText(getString(R.string.first_city_hint), false, getString(R.string.first_search), name -> searchPlace(name.trim()));
    }

    private void searchPlace(String name) {
        if (name.isEmpty()) return;
        Background.run(() -> {
            try {
                List<Weather.Place> places = Weather.search(this, name);
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    if (places.isEmpty()) {
                        say(getString(R.string.settings_no_place));
                        return;
                    }
                    List<String> names = new ArrayList<>();
                    for (Weather.Place place : places) names.add(place.detail == null ? place.name : place.name + ", " + place.detail);
                    pickText(getString(R.string.first_place_title), names, index -> {
                        Weather.setPlace(this, places.get(index));
                        Collection collection = collections.get(current);
                        if (collection.kind == WEATHER) putInUse(collection);
                        else refreshState();
                    });
                });
            } catch (IOException | JSONException e) {
                runOnUiThread(() -> say(getString(R.string.settings_no_connection)));
            }
        });
    }

    // ---------- one picture, whole ----------

    private FrameLayout previewPage() {
        FrameLayout page = new FrameLayout(this);
        page.setVisibility(View.GONE);
        page.setBackgroundColor(Color.BLACK);
        full = new ImageView(this);
        full.setScaleType(ImageView.ScaleType.CENTER_CROP);
        page.addView(full, new FrameLayout.LayoutParams(-1, -1));
        TextView note = label(getString(R.string.first_preview), NOTE_SP);
        FrameLayout.LayoutParams corner = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        corner.topMargin = dp(NOTE_MARGIN);
        corner.setMarginStart(dp(NOTE_MARGIN));
        page.addView(note, corner);
        LinearLayout buttons = new LinearLayout(this);
        buttons.setGravity(Gravity.CENTER);
        int room = dp(BUTTON_ROOM);
        buttons.setPadding(room, room, room, room);
        before = arrow("‹");
        buttons.addView(before);
        confirmButton = button(getString(R.string.card_confirm), BUTTON_SP, this::confirm);
        LinearLayout.LayoutParams middle = new LinearLayout.LayoutParams(dp(BUTTON_WIDTH), -2);
        middle.setMargins(dp(BUTTON_GAP), 0, dp(BUTTON_GAP), 0);
        buttons.addView(confirmButton, middle);
        after = arrow("›");
        buttons.addView(after);
        // Under Confirm, as left and right go through the pictures: Down reaches it.
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        column.setPadding(0, 0, 0, room);
        column.addView(buttons, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout.LayoutParams trashPlace = new LinearLayout.LayoutParams(dp(TasksActivity.BUTTON_SIZE), dp(TasksActivity.BUTTON_SIZE));
        trashPlace.topMargin = dp(TRASH_GAP);
        trash = trash();
        column.addView(trash, trashPlace);
        FrameLayout.LayoutParams bottom = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        bottom.bottomMargin = dp(BUTTON_BOTTOM);
        page.addView(column, bottom);
        return page;
    }

    /** Not a button: it only says that the arrows of the remote go on from here. */
    private TextView arrow(String mark) {
        TextView arrow = label(mark, ARROW_SP);
        arrow.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        // Full white with a shadow: over a light photo a faded one could not be seen.
        arrow.setShadowLayer(ARROW_SHADOW, 0, 0, ARROW_SHADOW_COLOR);
        arrow.setIncludeFontPadding(false);
        arrow.setTranslationY(-dp(ARROW_RISE));
        return arrow;
    }

    /** The thumbnail at once, the real picture as soon as it is read. */
    private void showPreview(int index) {
        previewIndex = index;
        ImageView cell = cells.get(index);
        opened = cell;
        previewed = pictures.get(index);
        String asset = previewed;
        int shown = ++previews;
        before.setVisibility(index > 0 ? View.VISIBLE : View.INVISIBLE);
        after.setVisibility(index < pictures.size() - 1 ? View.VISIBLE : View.INVISIBLE);
        trash.setVisibility(personal || set != null ? View.VISIBLE : View.GONE);
        Drawable quick = files || personal ? cell.getDrawable() : Look.preset(asset);
        if (quick != null) full.setImageDrawable(quick);
        preview.setVisibility(View.VISIBLE);
        confirmButton.requestFocus();
        if (!files && !personal) return;
        Background.draw(() -> {
            // Going through them quickly leaves the ones already passed behind.
            if (shown != previews) return;
            Bitmap picture = personal ? decodeFile(new File(Look.personalFolder(this), asset), SCREEN_PIXELS) : decode(asset);
            if (picture == null) return;
            runOnUiThread(() -> {
                if (shown == previews && preview.getVisibility() == View.VISIBLE) full.setImageBitmap(picture);
            });
        });
    }

    /** The focus goes back to the picture first: hiding the page with it would hand it to the list. */
    private void closePreview() {
        previews++;
        previewed = null;
        if (opened != null) opened.requestFocus();
        full.setImageDrawable(null);
        preview.setVisibility(View.GONE);
    }

    /** The picture on show is kept: for the Home, or as the screensaver's only one. */
    private void confirm() {
        if (previewed == null) return;
        Collection collection = collections.get(current);
        if (saver) {
            Look.setSaverFolder(this, collection.folder);
            Look.setSaverPicture(this, personal ? new File(Look.personalFolder(this), previewed).getPath() : previewed);
        } else {
            Look.setBackdrop(this, previewed);
            dressBackdrop();
        }
        closePreview();
        refreshState();
    }

    /** A trash in a circle, the size and colours of the task manager's X. */
    private ImageView trash() {
        ImageView trash = new ImageView(this);
        trash.setImageResource(R.drawable.ic_s_delete);
        trash.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int inset = dp(TRASH_INSET);
        trash.setPadding(inset, inset, inset, inset);
        trash.setContentDescription(getString(R.string.grid_delete));
        trash.setFocusable(true);
        trash.setClickable(true);
        trash.setDefaultFocusHighlightEnabled(false);
        GradientDrawable fill = new GradientDrawable();
        fill.setShape(GradientDrawable.OVAL);
        trash.setBackground(fill);
        View.OnFocusChangeListener look = (view, focused) -> {
            fill.setColor(focused ? Color.WHITE : TasksActivity.BUTTON_REST);
            fill.setStroke(dp(TasksActivity.BUTTON_RING), focused ? Color.WHITE : TasksActivity.BUTTON_EDGE);
            trash.setImageTintList(ColorStateList.valueOf(focused ? Fire.INK : Color.WHITE));
        };
        look.onFocusChange(trash, false);
        trash.setOnFocusChangeListener(look);
        trash.setOnClickListener(view -> askDelete());
        return trash;
    }

    /**
     * A photo can be sent by mistake: it goes, after a question, and the grid shows what is left.
     * Gone for good, the user's choice: a picture of the app's sets is deleted from the TV if it was
     * copied there, and if it is inside the app it is never shown again.
     */
    private void askDelete() {
        String name = previewed;
        if (name == null) return;
        confirm(getString(R.string.grid_delete_title, Look.backdropName(this, name)),
            getString(R.string.grid_delete_text), getString(R.string.grid_delete), () -> {
                if (personal) Look.deletePersonal(this, name);
                else Look.take(this, name);
                setResult(RESULT_OK);
                closePreview();
                reload();
            });
    }

    private void askDeleteSet() {
        String name = collections.get(current).name;
        confirm(getString(R.string.grid_delete_set_title, name), getString(R.string.grid_delete_set_text),
            getString(R.string.grid_delete_set_do), () -> {
                Look.takeSet(this, set);
                setResult(RESULT_OK);
                reload();
            });
    }

    /** Small and pale until it has the focus, with a trash: it is there for whoever looks for it. */
    private TextView quietButton(String text, Runnable run) {
        TextView button = label(text, QUIET_SP);
        button.setSingleLine(true);
        button.setFocusable(true);
        button.setClickable(true);
        button.setDefaultFocusHighlightEnabled(false);
        button.setPadding(dp(QUIET_SIDE), dp(QUIET_TOP), dp(QUIET_SIDE), dp(QUIET_TOP));
        // A trash before the words: it says at a glance what the button does.
        Drawable bin = getDrawable(R.drawable.ic_s_delete).mutate();
        bin.setBounds(0, 0, dp(QUIET_ICON), dp(QUIET_ICON));
        button.setCompoundDrawablesRelative(bin, null, null, null);
        button.setCompoundDrawablePadding(dp(QUIET_ICON_GAP));
        button.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable fill = new GradientDrawable();
        fill.setCornerRadius(dp(QUIET_RADIUS));
        button.setBackground(fill);
        View.OnFocusChangeListener look = (view, focused) -> {
            fill.setColor(focused ? Color.WHITE : Color.TRANSPARENT);
            fill.setStroke(dp(1), focused ? Color.WHITE : QUIET_RIM);
            button.setTextColor(focused ? Fire.INK : Color.WHITE);
            button.setCompoundDrawableTintList(ColorStateList.valueOf(focused ? Fire.INK : Color.WHITE));
            button.setAlpha(focused ? 1f : QUIET_ALPHA);
        };
        look.onFocusChange(button, false);
        button.setOnFocusChangeListener(look);
        button.setOnClickListener(view -> run.run());
        return button;
    }

    private int gridWidth() { return COLUMNS * dp(CELL_WIDTH) + (COLUMNS - 1) * dp(GAP) + 2 * dp(SIDE_ROOM); }

    private int gridHeight() { return ROWS_SHOWN * dp(CELL_HEIGHT) + (ROWS_SHOWN - 1) * dp(GAP) + 2 * dp(SIDE_ROOM); }

    /** The bar, the pictures and the line under them: the divider ends with them. */
    private int bodyHeight() { return dp(BAR_HEIGHT) + dp(BAR_GAP) + gridHeight() + dp(INFO_TOP) + dp(INFO_HEIGHT); }

    // ---------- keys ----------

    private boolean inList(View view) { return view != null && view.getParent() == list; }

    /** A picture with no other under it in its column. */
    private boolean lastInColumn(View view) {
        int index = cells.indexOf(view);
        return index >= 0 && index + COLUMNS >= cells.size();
    }

    /** Right from the list: the switch, else the chosen picture, else the button under them. */
    private void enterRight() {
        Collection collection = collections.get(current);
        if (single.getVisibility() == View.VISIBLE) single.requestFocus();
        else if (placeButton.getVisibility() == View.VISIBLE) placeButton.requestFocus();
        else if (gridOpen() && !cells.isEmpty()) focusCell(chosenIndex());
        else if (action.getVisibility() == View.VISIBLE) action.requestFocus();
        else if (collection.kind == TIMED && deleteSet.getVisibility() == View.VISIBLE) deleteSet.requestFocus();
    }

    /** Back is decided here, and the arrows in a preview: its one button has the focus and stays. */
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        boolean previewing = preview.getVisibility() == View.VISIBLE;
        View focused = getCurrentFocus();
        if (code == KeyEvent.KEYCODE_BACK) {
            if (down) return true;
            if (previewing) closePreview();
            else if (focused != null && !inList(focused)) items.get(current).requestFocus();
            else finish();
            return true;
        }
        boolean rtl = getWindow().getDecorView().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        int towardList = rtl ? KeyEvent.KEYCODE_DPAD_RIGHT : KeyEvent.KEYCODE_DPAD_LEFT;
        int towardPictures = rtl ? KeyEvent.KEYCODE_DPAD_LEFT : KeyEvent.KEYCODE_DPAD_RIGHT;
        if (previewing) {
            int step = code == towardList ? -1 : code == towardPictures ? 1 : 0;
            if (step == 0) return super.dispatchKeyEvent(event);
            int next = previewIndex + step;
            if (down && next >= 0 && next < pictures.size()) showPreview(next);
            return true;
        }
        if (inList(focused)) {
            int index = items.indexOf(focused);
            if (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN) {
                // By hand: at the ends of the list the focus must not jump to the pictures.
                int next = index + (code == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1);
                if (down && next >= 0 && next < items.size()) items.get(next).requestFocus();
                return true;
            }
            if (code == towardPictures) {
                if (down && event.getRepeatCount() == 0) {
                    list.removeCallbacks(showFocused);
                    if (index != current) showCollection(index);
                    enterRight();
                }
                return true;
            }
        } else if (focused != null && code == KeyEvent.KEYCODE_DPAD_DOWN && lastInColumn(focused)) {
            // Down from the last picture of a column: only the third reaches the delete button under
            // it, the first two stay. Android found the list for them (the user's report of 2026-11-10).
            boolean third = cells.indexOf(focused) % COLUMNS == COLUMNS - 1;
            if (down && third && deleteSet.getVisibility() == View.VISIBLE) deleteSet.requestFocus();
            return true;
        } else if (focused != null && code == towardList && down) {
            // Toward the list from the edge of the right side: back to the collection shown.
            View next = FocusFinder.getInstance().findNextFocus(root, focused, rtl ? View.FOCUS_RIGHT : View.FOCUS_LEFT);
            if (next == null || inList(next)) {
                items.get(current).requestFocus();
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }
}
