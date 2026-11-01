package it.simplemal.firehome;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
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
 * Every picture of one set, or Salotto's own backdrops with no set named, four across and four down
 * at a time. OK shows one as the whole screen, as the Home would, to keep it or go back to the grid.
 */
public final class PictureGridActivity extends CardActivity {
    static final String FOLDER = "folder";
    static final String TITLE = "title";

    private static final int COLUMNS = 4;
    private static final int CELL_WIDTH = 172;
    private static final int CELL_HEIGHT = 97;
    private static final int GAP = 12;
    private static final int RADIUS = 10;
    private static final int EDGE = 2;
    private static final int TITLE_SP = 20;
    private static final int TITLE_TOP = 22;
    private static final int TITLE_BOTTOM = 14;
    /** The edge of a selected cell stays inside the scrolling area. */
    private static final int SIDE_ROOM = 4;
    /** A quarter of each side: the grid shows sixty of these at once in very little memory. */
    private static final int THUMB_SAMPLE = 4;
    private static final int NOTE_SP = 14;
    private static final int NOTE_MARGIN = 24;
    private static final int BUTTON_SP = 16;
    private static final int BUTTON_WIDTH = 170;
    private static final int BUTTON_GAP = 14;
    private static final int BUTTON_BOTTOM = 48;

    private final List<ImageView> cells = new ArrayList<>();
    private List<String> pictures = new ArrayList<>();
    private boolean files;
    private String setName;
    private TextView title;
    private TextView confirmButton;
    private String previewed;
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
        rows.setPadding(dp(SIDE_ROOM), dp(SIDE_ROOM), dp(SIDE_ROOM), dp(SIDE_ROOM));
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
        scroll = new ScrollView(this);
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
            if (focused) title.setText(getString(R.string.grid_title, setName, Look.backdropName(this, pictures.get(index))));
        });
        cell.setOnClickListener(view -> showPreview(cell, index));
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
        TextView confirm = button(getString(R.string.card_confirm), BUTTON_SP, this::confirm);
        buttons.addView(confirm, new LinearLayout.LayoutParams(dp(BUTTON_WIDTH), -2));
        LinearLayout.LayoutParams second = new LinearLayout.LayoutParams(dp(BUTTON_WIDTH), -2);
        second.setMarginStart(dp(BUTTON_GAP));
        buttons.addView(button(getString(R.string.first_key_back), BUTTON_SP, this::closePreview), second);
        FrameLayout.LayoutParams bottom = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        bottom.bottomMargin = dp(BUTTON_BOTTOM);
        page.addView(buttons, bottom);
        confirmButton = confirm;
        return page;
    }

    /** The thumbnail at once, the real picture as soon as it is read, finished as on the Home. */
    private void showPreview(ImageView cell, int index) {
        opened = cell;
        previewed = pictures.get(index);
        String asset = previewed;
        int shown = ++previews;
        full.setImageDrawable(files ? cell.getDrawable() : Look.preset(asset));
        preview.setVisibility(View.VISIBLE);
        scroll.setVisibility(View.INVISIBLE);
        confirmButton.requestFocus();
        if (!files) return;
        Background.draw(() -> {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inMutable = true;
            Bitmap picture = decode(asset, options);
            if (picture == null) return;
            BottomBlur.apply(picture);
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

    @Override public void onBackPressed() {
        if (preview.getVisibility() == View.VISIBLE) closePreview();
        else super.onBackPressed();
    }
}
