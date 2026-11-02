package it.simplemal.firehome;

import android.content.Context;
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
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Every picture of one set, or Salotto's own backdrops with no set named, four across and down to
 * the edge of the screen. OK shows one as the whole screen: left and right go through the
 * others there, Confirm keeps the one on show and Back returns to the grid.
 */
public final class PictureGridActivity extends CardActivity {
    static final String FOLDER = "folder";
    static final String TITLE = "title";

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
    /** A quarter of each side: the grid shows sixty of these at once in very little memory. */
    private static final int THUMB_SAMPLE = 4;
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

    /** A row cut by the edge of the screen is what says there are more, as on the Home. */
    private static final class GridScroll extends ScrollView {
        private final Rect wider = new Rect();
        private final int nextRow;

        GridScroll(Context context, int nextRow) {
            super(context);
            this.nextRow = nextRow;
        }

        @Override protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect) {
            wider.set(rect.left, rect.top - nextRow, rect.right, rect.bottom + nextRow);
            return super.computeScrollDeltaToGetChildRectOnScreen(wider);
        }
    }

    private final List<ImageView> cells = new ArrayList<>();
    private List<String> pictures = new ArrayList<>();
    private boolean files;
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
        pictures = folder == null ? Arrays.asList(Look.PRESET_NAMES) : pictures(folder);
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

        LinearLayout rows = new LinearLayout(this);
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
            LinearLayout.LayoutParams place = new LinearLayout.LayoutParams(dp(CELL_WIDTH), dp(CELL_HEIGHT));
            place.setMarginStart(index % COLUMNS == 0 ? 0 : dp(GAP));
            row.addView(cell, place);
        }
        scroll = new GridScroll(this, dp(NEXT_ROW));
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.addView(rows);
        page.addView(scroll, new LinearLayout.LayoutParams(-2, 0, 1));
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));
        preview = previewPage();
        root.addView(preview, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);

        int chosen = pictures.indexOf(Look.backdrop(this));
        if (!cells.isEmpty()) cells.get(Math.max(0, chosen)).requestFocus();
        if (files) loadThumbnails();
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
        if (!files) cell.setImageDrawable(Look.preset(pictures.get(index)));
        cell.setOnFocusChangeListener((view, focused) -> {
            view.setForeground(focused ? edge() : null);
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

    private void loadThumbnails() {
        List<String> names = new ArrayList<>(pictures);
        Background.draw(() -> {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = THUMB_SAMPLE;
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            for (int index = 0; index < names.size() && !isDestroyed(); index++) {
                Bitmap thumbnail = decode(names.get(index), options);
                int cell = index;
                if (thumbnail != null) runOnUiThread(() -> cells.get(cell).setImageBitmap(thumbnail));
            }
        });
    }

    private Bitmap decode(String asset, BitmapFactory.Options options) {
        try (InputStream input = getAssets().open(asset)) {
            return BitmapFactory.decodeStream(input, null, options);
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
        page.addView(buttons, bottom);
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
        Drawable quick = files ? cell.getDrawable() : Look.preset(asset);
        if (quick != null) full.setImageDrawable(quick);
        preview.setVisibility(View.VISIBLE);
        scroll.setVisibility(View.INVISIBLE);
        confirmButton.requestFocus();
        if (!files) return;
        Background.draw(() -> {
            // Going through them quickly leaves the ones already passed behind.
            if (shown != previews) return;
            Bitmap picture = decode(asset, new BitmapFactory.Options());
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
        if (opened != null) opened.requestFocus();
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
