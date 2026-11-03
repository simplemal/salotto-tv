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

/**
 * Every picture of one set, or Salotto's own backdrops with no set named, four across and down to
 * the edge of the screen. OK shows one as the whole screen: left and right go through the
 * others there, Confirm keeps the one on show and Back returns to the grid. Personale shows only
 * whole rows, to leave room for Carica under them.
 */
public final class PictureGridActivity extends CardActivity {
    static final String FOLDER = "folder";
    static final String TITLE = "title";
    static final String PERSONAL = "personal";
    private static final int UPLOAD = 1;

    private static final int COLUMNS = 4;
    private static final int CELL_WIDTH = 216;
    private static final int CELL_HEIGHT = 122;
    private static final int GAP = 12;
    private static final int RADIUS = 10;
    private static final int EDGE = 2;
    private static final int TITLE_SP = 20;
    private static final int TITLE_TOP = 22;
    private static final int TITLE_BOTTOM = 14;
    /** The edge of a selected cell stays inside the scrolling area. */
    private static final int SIDE_ROOM = 4;
    private static final int BOTTOM_ROOM = 28;
    /** What stays in view past the selected row, with the gap: what the screen's edge cuts at the start. */
    private static final int NEXT_ROW = 72;
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
    private static final int PAGE_WHEEL = 48;
    private static final int CELL_WHEEL = 28;
    private static final long FADE_MS = 140;
    private static final int OPAQUE = 255;
    private static final int UPLOAD_TOP = 14;
    private static final int TRASH_GAP = 14;
    /** The icon inside the circle, as large as the X in its own. */
    private static final int TRASH_INSET = 8;
    private static final int UPLOAD_BOTTOM = 28;
    private static final int EMPTY_SP = 16;
    /** A cell needs only this much of a picture: a phone's photo is many times larger. */
    private static final int CELL_PIXELS = 432;
    private static final int SCREEN_PIXELS = 1920;

    /** A row cut by the edge of the screen is what says there are more, as on the Home. */
    private static final class GridScroll extends ScrollView {
        private final Rect wider = new Rect();
        private final int nextRow;
        /** Personale moves by whole rows itself, from the selected cell. */
        boolean wholeRows;

        GridScroll(Context context, int nextRow) {
            super(context);
            this.nextRow = nextRow;
        }

        @Override protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect) {
            if (wholeRows) return 0;
            wider.set(rect.left, rect.top - nextRow, rect.right, rect.bottom + nextRow);
            return super.computeScrollDeltaToGetChildRectOnScreen(wider);
        }
    }

    private final List<ImageView> cells = new ArrayList<>();
    private final List<FrameLayout> places = new ArrayList<>();
    private List<String> pictures = new ArrayList<>();
    private boolean[] asked;
    private boolean[] sharpened;
    /** One cell at a time is read again from its picture, so that the next can still be the one selected. */
    private boolean sharpening;
    private boolean firstShown;
    private LinearLayout rows;
    private ProgressBar pageWheel;
    private boolean files;
    private boolean personal;
    private int rowsShown;
    /** Hidden under a preview, where Down would otherwise find it. */
    private View under;
    private String setName;
    private TextView title;
    private TextView confirmButton;
    private String previewed;
    private int previewIndex;
    private TextView before;
    private TextView after;
    private ScrollView scroll;
    private FrameLayout preview;
    private ImageView full;
    private FrameLayout root;
    private ImageView opened;
    private GradientDrawable edge;
    private int previews;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        String folder = getIntent().getStringExtra(FOLDER);
        personal = getIntent().getBooleanExtra(PERSONAL, false);
        if (personal) {
            Look.tidyPersonal(this);
            for (File image : Look.images(this)) pictures.add(image.getName());
        } else {
            pictures = folder == null ? Arrays.asList(Look.PRESET_NAMES) : pictures(folder);
        }
        files = folder != null;
        setName = getIntent().getStringExtra(TITLE);
        root = new FrameLayout(this);
        root.setBackground(Look.plainBackdrop());
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        title = label(setName, TITLE_SP);
        title.setPadding(0, dp(TITLE_TOP), 0, dp(TITLE_BOTTOM));
        page.addView(title, new LinearLayout.LayoutParams(-2, -2));

        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding(dp(SIDE_ROOM), dp(SIDE_ROOM), dp(SIDE_ROOM), dp(BOTTOM_ROOM));
        LinearLayout row = null;
        for (int index = 0; index < pictures.size(); index++) {
            if (index % COLUMNS == 0) {
                row = new LinearLayout(this);
                LinearLayout.LayoutParams place = new LinearLayout.LayoutParams(-2, -2);
                place.topMargin = index == 0 ? 0 : dp(GAP);
                rows.addView(row, place);
            }
            ImageView cell = cell(index);
            cells.add(cell);
            FrameLayout holder = new FrameLayout(this);
            holder.addView(cell, new FrameLayout.LayoutParams(-1, -1));
            places.add(holder);
            LinearLayout.LayoutParams place = new LinearLayout.LayoutParams(dp(CELL_WIDTH), dp(CELL_HEIGHT));
            place.setMarginStart(index % COLUMNS == 0 ? 0 : dp(GAP));
            row.addView(holder, place);
        }
        GridScroll grid = new GridScroll(this, dp(NEXT_ROW));
        grid.wholeRows = personal;
        scroll = grid;
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.addView(rows);
        page.addView(scroll, new LinearLayout.LayoutParams(-2, 0, 1));
        if (personal) addUpload(page);
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));
        pageWheel = wheel();
        pageWheel.setVisibility(View.GONE);
        root.addView(pageWheel, new FrameLayout.LayoutParams(dp(PAGE_WHEEL), dp(PAGE_WHEEL), Gravity.CENTER));
        preview = previewPage();
        root.addView(preview, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);

        int chosen = Math.max(0, pictures.indexOf(Look.backdrop(this)));
        if (!cells.isEmpty()) cells.get(chosen).requestFocus();
        if (personal) scroll.post(this::fitWholeRows);
        if (files) {
            asked = new boolean[pictures.size()];
            sharpened = new boolean[pictures.size()];
            rows.setAlpha(0);
            scroll.setOnScrollChangeListener((view, x, y, oldX, oldY) -> {
                loadInView();
                sharpenInView();
            });
            // Only once it is laid out, and scrolled to the selected cell, is it known what is in view.
            scroll.post(this::showFirstScreen);
        }
    }

    /** Carica under the grid, and while there is nothing yet a line that says what it is for. */
    private void addUpload(LinearLayout page) {
        if (pictures.isEmpty()) {
            TextView empty = label(getString(R.string.grid_personal_empty), EMPTY_SP);
            empty.setGravity(Gravity.CENTER);
            empty.setAlpha(ARROW_ALPHA);
            page.addView(empty, new LinearLayout.LayoutParams(-2, 0, 1));
            ((LinearLayout.LayoutParams) scroll.getLayoutParams()).weight = 0;
        }
        TextView upload = button(getString(R.string.grid_upload), BUTTON_SP,
            () -> startActivityForResult(new Intent(this, UploadActivity.class), UPLOAD));
        under = upload;
        LinearLayout.LayoutParams place = new LinearLayout.LayoutParams(dp(BUTTON_WIDTH), -2);
        place.setMargins(0, dp(UPLOAD_TOP), 0, dp(UPLOAD_BOTTOM));
        page.addView(upload, place);
        if (pictures.isEmpty()) upload.requestFocus();
    }

    /** The grid takes as many whole rows as fit above Carica: the room left over goes under it. */
    private void fitWholeRows() {
        if (pictures.isEmpty()) return;
        int step = dp(CELL_HEIGHT) + dp(GAP);
        int room = scroll.getHeight() - 2 * dp(SIDE_ROOM) + dp(GAP);
        rowsShown = Math.max(1, room / step);
        rows.setPadding(dp(SIDE_ROOM), dp(SIDE_ROOM), dp(SIDE_ROOM), dp(SIDE_ROOM));
        LinearLayout.LayoutParams place = (LinearLayout.LayoutParams) scroll.getLayoutParams();
        place.height = 2 * dp(SIDE_ROOM) + rowsShown * step - dp(GAP);
        place.weight = 0;
        scroll.setLayoutParams(place);
        ((LinearLayout) scroll.getParent()).addView(new View(this), new LinearLayout.LayoutParams(0, 0, 1));
        View focused = getCurrentFocus();
        if (focused != null && cells.contains(focused)) showRowOf(cells.indexOf(focused), false);
        loadPersonal();
    }

    /** The selected row stays in view, and the rows on screen are always whole ones. */
    private void showRowOf(int index, boolean smooth) {
        if (rowsShown == 0) return;
        int row = index / COLUMNS;
        int step = dp(CELL_HEIGHT) + dp(GAP);
        int first = scroll.getScrollY() / step;
        if (row < first) first = row;
        else if (row >= first + rowsShown) first = row - rowsShown + 1;
        if (smooth) scroll.smoothScrollTo(0, first * step);
        else scroll.scrollTo(0, first * step);
    }

    /** Few pictures, each read small: one after the other, the selected one's row first. */
    private void loadPersonal() {
        List<File> found = new ArrayList<>();
        for (String name : pictures) found.add(new File(Look.personalFolder(this), name));
        Background.draw(() -> {
            for (int index = 0; index < found.size(); index++) {
                if (isDestroyed()) return;
                Bitmap small = decodeFile(found.get(index), CELL_PIXELS);
                int place = index;
                if (small != null) runOnUiThread(() -> appear(cells.get(place), small));
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
        // Shown again with what came in: a new list of cells is simpler than slotting them in.
        if (request == UPLOAD && result == RESULT_OK) recreate();
    }

    private List<String> pictures(String folder) {
        List<String> found = new ArrayList<>();
        try {
            String[] names = getAssets().list(folder);
            Arrays.sort(names);
            for (String name : names) found.add(folder + "/" + name);
        } catch (IOException e) {
            say(getString(R.string.card_app_unavailable));
        }
        return found;
    }

    private ImageView cell(int index) {
        ImageView cell = new ImageView(this);
        cell.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cell.setBackgroundColor(Color.rgb(28, 30, 36));
        cell.setFocusable(true);
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
            if (focused && files) sharpenInView();
            if (focused && personal) showRowOf(index, true);
            if (focused) title.setText(getString(R.string.grid_title, setName, Look.backdropName(this, pictures.get(index)),
                getString(R.string.grid_position, index + 1, pictures.size())));
        });
        cell.setOnClickListener(view -> showPreview(index));
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

    private ProgressBar wheel() {
        ProgressBar wheel = new ProgressBar(this);
        wheel.setIndeterminateTintList(ColorStateList.valueOf(Color.WHITE));
        return wheel;
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
        for (int index : inView()) {
            if (cells.get(index).getDrawable() == null && !asked[index]) missing.add(index);
        }
        return missing;
    }

    /** The first screen comes all at once, even when nothing has been read yet. */
    private void showFirstScreen() {
        List<Integer> missing = missing();
        if (missing.isEmpty()) {
            rows.setAlpha(1);
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
                if (isDestroyed()) return;
                read.add(Thumbnails.read(this, names.get(index)));
            }
            runOnUiThread(() -> {
                for (int place = 0; place < missing.size(); place++) {
                    if (read.get(place) != null) cells.get(missing.get(place)).setImageBitmap(read.get(place));
                }
                pageWheel.setVisibility(View.GONE);
                rows.animate().alpha(1).setDuration(FADE_MS).start();
                firstShown = true;
                loadInView();
                sharpenInView();
            });
        });
    }

    /** From there on, only what scrolling brings in: each cell on its own, as it is ready. */
    private void loadInView() {
        if (!firstShown) return;
        for (int index : missing()) {
            asked[index] = true;
            load(index);
        }
    }

    private void load(int index) {
        FrameLayout place = places.get(index);
        ProgressBar wheel = wheel();
        place.addView(wheel, new FrameLayout.LayoutParams(dp(CELL_WHEEL), dp(CELL_WHEEL), Gravity.CENTER));
        String name = pictures.get(index);
        Background.draw(() -> {
            if (isDestroyed()) return;
            Bitmap quick = Thumbnails.kept(name) == null ? Thumbnails.tiny(this, name) : null;
            if (quick != null) runOnUiThread(() -> {
                place.removeView(wheel);
                appear(cells.get(index), quick);
            });
            Bitmap thumbnail = Thumbnails.read(this, name);
            runOnUiThread(() -> {
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
        if (!firstShown || sharpening) return;
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
        Background.sharpen(() -> {
            Bitmap sharp = isDestroyed() ? null : Thumbnails.sharp(this, name);
            runOnUiThread(() -> {
                sharpening = false;
                if (isDestroyed()) return;
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
        try (InputStream input = getAssets().open(asset)) {
            return BitmapFactory.decodeStream(input);
        } catch (IOException e) {
            return null;
        }
    }

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
        TextView confirm = button(getString(R.string.card_confirm), BUTTON_SP, this::confirm);
        LinearLayout.LayoutParams middle = new LinearLayout.LayoutParams(dp(BUTTON_WIDTH), -2);
        middle.setMargins(dp(BUTTON_GAP), 0, dp(BUTTON_GAP), 0);
        buttons.addView(confirm, middle);
        after = arrow("›");
        buttons.addView(after);
        FrameLayout.LayoutParams bottom = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        bottom.bottomMargin = dp(BUTTON_BOTTOM);
        if (personal) {
            // Under Confirm, as left and right go through the photos: Down reaches it.
            LinearLayout column = new LinearLayout(this);
            column.setOrientation(LinearLayout.VERTICAL);
            column.setGravity(Gravity.CENTER_HORIZONTAL);
            column.setPadding(0, 0, 0, room);
            column.addView(buttons);
            LinearLayout.LayoutParams trashPlace = new LinearLayout.LayoutParams(dp(TasksActivity.BUTTON_SIZE), dp(TasksActivity.BUTTON_SIZE));
            trashPlace.topMargin = dp(TRASH_GAP);
            column.addView(trash(), trashPlace);
            page.addView(column, bottom);
        } else {
            page.addView(buttons, bottom);
        }
        confirmButton = confirm;
        return page;
    }

    /** Not a button: it only says that the arrows of the remote go on from here. */
    private TextView arrow(String mark) {
        TextView arrow = label(mark, ARROW_SP);
        arrow.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        arrow.setAlpha(ARROW_ALPHA);
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
        Drawable quick = files || personal ? cell.getDrawable() : Look.preset(asset);
        if (quick != null) full.setImageDrawable(quick);
        preview.setVisibility(View.VISIBLE);
        scroll.setVisibility(View.INVISIBLE);
        if (under != null) under.setVisibility(View.INVISIBLE);
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

    private void closePreview() {
        previews++;
        previewed = null;
        full.setImageDrawable(null);
        preview.setVisibility(View.GONE);
        scroll.setVisibility(View.VISIBLE);
        if (under != null) under.setVisibility(View.VISIBLE);
        if (opened != null) opened.requestFocus();
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

    /** A photo can be sent by mistake: it goes, after a question, and the grid shows what is left. */
    private void askDelete() {
        String name = previewed;
        if (name == null) return;
        confirm(getString(R.string.grid_delete_title, Look.backdropName(this, name)), getString(R.string.grid_delete_text),
            getString(R.string.grid_delete), () -> {
                Look.deletePersonal(this, name);
                setResult(RESULT_OK);
                recreate();
            });
    }

    private void confirm() {
        if (previewed != null) Look.setBackdrop(this, previewed);
        finish();
    }

    /** Back is decided here, and the arrows in a preview: its one button has the focus and stays. */
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        boolean previewing = preview.getVisibility() == View.VISIBLE;
        if (code == KeyEvent.KEYCODE_BACK) {
            if (!down && previewing) closePreview();
            else if (!down) finish();
            return true;
        }
        int step = code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : 0;
        if (!previewing || step == 0) return super.dispatchKeyEvent(event);
        int next = previewIndex + step;
        if (down && next >= 0 && next < pictures.size()) showPreview(next);
        return true;
    }
}
