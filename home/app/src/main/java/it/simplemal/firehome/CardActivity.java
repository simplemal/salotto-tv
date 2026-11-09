package it.simplemal.firehome;

import android.app.Activity;
import android.app.AlertDialog;
import java.util.function.IntConsumer;
import android.widget.EditText;
import android.view.inputmethod.EditorInfo;
import android.view.WindowManager;
import android.view.Window;
import android.text.InputType;
import android.graphics.Typeface;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.LayerDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.LruCache;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.ViewPropertyAnimator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/** The tvOS-like cards and app launching shared by the Salotto screens. */
abstract class CardActivity extends Activity implements ControlPanel.Host {
    @Override public Context context() { return this; }
    @Override public void panelDone() { }

    private String removing;
    protected static final int BANNER = 0;
    protected static final int ICON = 1;
    protected static final int SHADED = 2;
    protected static final int GLASS = 3;
    protected static final int MARGIN = 48;
    protected static final int GAP = 10;
    protected static final int DOCK_MARGIN = 28;
    protected static final int PILL_ICON = 30;
    protected static final int PILL_ICON_GAP = 3;
    private static final int PILL_ICON_OFF = Color.argb(20, 0, 0, 0);
    private static final int PILL_RADIUS = 21;
    private static final int SOFT_LIGHT = 110;
    private static final int SOFT_DEPTH = 3;
    private static final int BUTTON_RADIUS = 12;
    private static final int BUTTON_PADDING = 10;
    /** Quieter than the pill: on artwork the glass must stay a hint. */
    private static final int CARD_REFLECTION = 14;
    private static final int CARD_LIGHT = 100;
    private static final int MIN_CAPTION = 130;
    private static final int EDGE_SAMPLE = 32;
    private static final long FOCUS_MS = 140;
    /** Half on the Stick of 2020, which has half the memory. */
    private static final int CARD_ART_BYTES = (Features.LARGE_PICTURE_MEMORY ? 16 : 8) * 1024 * 1024;
    /** Apps whose TV banner is a small logo: their layered icon fills the card far better. */
    private static final List<String> LAYERED_ICON = Arrays.asList("com.aurora.store");
    /** Each app's artwork at the size of its card: a banner alone can be many times larger. */
    private static final LruCache<String, Bitmap> CARD_ART = new LruCache<String, Bitmap>(CARD_ART_BYTES) {
        @Override protected int sizeOf(String key, Bitmap art) { return art.getAllocationByteCount(); }
    };

    private int cardNames;
    /** Artwork being drawn away from the main thread, for a page that waits until it has all arrived. */
    protected int artComing;
    private Drawable focusRing;

    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(Language.apply(base));
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        // Pages of words turn round for Arabic by themselves; the Home and the panels are placed by hand.
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        HomeApps.watch(this);
    }

    /** The system's usual levels never arrive: the accessibility service keeps this process in use. */
    protected boolean memoryShort(int level) {
        return level == TRIM_MEMORY_RUNNING_CRITICAL;
    }

    @Override public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (memoryShort(level)) {
            CARD_ART.evictAll();
            Look.trim();
            Thumbnails.trim();
        }
    }

    protected LinearLayout page() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setClipChildren(false);
        page.setClipToPadding(false);
        page.setPadding(dp(MARGIN), dp(32), dp(MARGIN), dp(32));
        return page;
    }

    /** Width of a card when each side of the screen keeps `edge` free. */
    protected int cardWidth(int columns, int edge) {
        return (getResources().getDisplayMetrics().widthPixels
            - 2 * edge - (columns - 1) * dp(GAP)) / columns;
    }

    protected LinearLayout row(LinearLayout page) {
        LinearLayout row = new LinearLayout(this);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        page.addView(row);
        return row;
    }

    protected float focusScale() { return 1.08f; }

    protected void onCardFocus(View tile) { }

    protected boolean showsCaption(View tile) { return true; }

    /** Cards with this tag get a small check mark in the corner. */
    protected boolean isMarked(String tag) { return false; }

    private View marked(ImageView image) {
        TextView mark = label("✓", 13);
        mark.setGravity(Gravity.CENTER);
        mark.setTextColor(Fire.INK);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(Color.WHITE);
        mark.setBackground(circle);
        // Above the card, which rises to 32 dp when focused; a shadow here would only add noise.
        mark.setOutlineProvider(null);
        mark.setElevation(dp(40));
        FrameLayout.LayoutParams corner = new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.TOP | Gravity.END);
        corner.setMargins(0, dp(5), dp(5), 0);
        FrameLayout holder = new FrameLayout(this);
        holder.setClipChildren(false);
        holder.addView(image);
        holder.addView(mark, corner);
        return holder;
    }

    /** What an app shows on its card. */
    protected static final class Art {
        Drawable image;
        Drawable back;
        int look = ICON;
    }

    /** A card seen before is ready at once. A new one fills in by itself, away from the main thread. */
    protected View appCard(LinearLayout row, String name, String pkg, int width, Runnable hold) {
        String key = artKey(pkg, width);
        Bitmap known = CARD_ART.get(key);
        Drawable drawn = known != null ? new BitmapDrawable(getResources(), known) : null;
        View tile = card(row, pkg, name, drawn, null, BANNER, width, () -> openPackage(pkg), hold);
        if (known == null) {
            ImageView image = imageOf(tile);
            // Opaque meanwhile: the focus shadow would show through an empty card.
            image.setBackground(cardFill(ICON));
            artComing++;
            Background.draw(() -> {
                Bitmap made = cardArt(key, pkg, width);
                runOnUiThread(() -> {
                    image.setBackground(null);
                    image.setImageBitmap(made);
                    artComing--;
                    artShown();
                });
            });
        }
        return tile;
    }

    /** One more card's artwork is on screen. */
    protected void artShown() { }

    /** An update can change the artwork, so its date is part of the name. */
    private String artKey(String pkg, int width) {
        try {
            return pkg + "/" + getPackageManager().getPackageInfo(pkg, 0).lastUpdateTime + "/" + width;
        } catch (PackageManager.NameNotFoundException e) {
            return pkg + "/" + width;
        }
    }

    /** Paints the card's image once, exactly as the card would show it, into a bitmap of its size. */
    private Bitmap cardArt(String key, String pkg, int width) {
        Bitmap made = CARD_ART.get(key);
        if (made != null) return made;
        int height = width * 9 / 16;
        Art art = art(pkg);
        ImageView image = new ImageView(this);
        dress(image, art.image, art.back, art.look);
        image.layout(0, 0, width, height);
        made = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        image.draw(new Canvas(made));
        CARD_ART.put(key, made);
        return made;
    }

    private ImageView imageOf(View tile) {
        View art = ((LinearLayout) tile).getChildAt(0);
        return (ImageView) (art instanceof ImageView ? art : ((FrameLayout) art).getChildAt(0));
    }

    protected Art art(String pkg) {
        Art art = new Art();
        try {
            PackageManager manager = getPackageManager();
            Drawable banner = manager.getApplicationBanner(pkg);
            // Some apps wrap a full 16:9 banner in an adaptive icon, which zooms and crops it.
            Drawable bannerBack = null;
            if (banner instanceof AdaptiveIconDrawable) {
                bannerBack = ((AdaptiveIconDrawable) banner).getBackground();
                banner = ((AdaptiveIconDrawable) banner).getForeground();
            }
            if (banner != null && isWide(banner) && !LAYERED_ICON.contains(pkg)) {
                art.image = banner;
                art.back = bannerBack;
                art.look = BANNER;
            } else {
                art.image = manager.getApplicationIcon(pkg);
                if (art.image instanceof AdaptiveIconDrawable) {
                    AdaptiveIconDrawable layered = (AdaptiveIconDrawable) art.image;
                    boolean store = LAYERED_ICON.contains(pkg);
                    art.back = store ? storeBack() : layered.getBackground();
                    // Aurora's artwork has a wide margin to zoom past; others must stay whole.
                    art.image = store ? new InsetDrawable(layered.getForeground(), -0.5f) : layered.getForeground();
                } else if (isWide(art.image)) {
                    // Amazon Kids and Photos have no banner, but their icon is already 16:9.
                    art.look = BANNER;
                } else {
                    art.back = edgeFill(art.image);
                }
            }
        } catch (PackageManager.NameNotFoundException ignored) { }
        return art;
    }

    /** The blue of Aurora's own store icon, which the TV build replaces with a flat violet. */
    private Drawable storeBack() {
        return new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{Color.rgb(61, 165, 245), Color.rgb(44, 100, 215)});
    }

    /** An opaque square icon on its own edge colour fills the card instead of floating in grey. */
    private Drawable edgeFill(Drawable icon) {
        Bitmap bitmap = Bitmap.createBitmap(EDGE_SAMPLE, EDGE_SAMPLE, Bitmap.Config.ARGB_8888);
        icon.setBounds(0, 0, EDGE_SAMPLE, EDGE_SAMPLE);
        icon.draw(new Canvas(bitmap));
        int edge = bitmap.getPixel(EDGE_SAMPLE / 2, 1);
        return Color.alpha(edge) == 255 ? new ColorDrawable(edge) : null;
    }

    /** Only true 16:9 artwork can fill a card without being cropped. */
    private boolean isWide(Drawable image) {
        int height = image.getIntrinsicHeight();
        return height > 0 && Math.abs((float) image.getIntrinsicWidth() / height - 16f / 9) < 0.06f;
    }

    private void dress(ImageView image, Drawable drawable, Drawable back, int look) {
        image.setImageDrawable(drawable);
        if (look == BANNER) {
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            if (back != null) image.setBackground(back);
        } else {
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            if (back != null) {
                image.setBackground(back);
            } else {
                if (drawable != null) image.setImageDrawable(new InsetDrawable(drawable, look == GLASS ? 0.3f : 0.2f));
                image.setBackground(cardFill(look));
            }
        }
    }

    /** Read once for a whole page of cards. */
    protected void startCards() { cardNames = Look.names(this); }

    /** The caption of a card without the focus, outside the dock. */
    protected int restingCaption() { return cardNames == Look.NAMES_ALWAYS ? View.VISIBLE : View.INVISIBLE; }

    /** The artwork is the card: no frame, no fixed caption, focus lifts it like tvOS. */
    protected View card(LinearLayout row, String tag, String name, Drawable drawable, Drawable back,
            int look, int width, Runnable action, Runnable hold) {
        int radius = dp(10);
        ImageView image = new ImageView(this);
        dress(image, drawable, back, look);
        Drawable restEdge = glassEdge(radius, CARD_REFLECTION, CARD_LIGHT);
        // Only one card has the focus, so one ring is enough for the whole page.
        if (focusRing == null) focusRing = focusEdge(radius);
        image.setForeground(restEdge);
        image.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
        image.setClipToOutline(true);

        TextView caption = label(name, 14);
        caption.setGravity(Gravity.CENTER);
        caption.setSingleLine(true);
        caption.setEllipsize(TextUtils.TruncateAt.END);
        caption.setPadding(0, dp(8), 0, dp(10));
        caption.setVisibility(restingCaption());

        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setClipChildren(false);
        tile.setFocusable(true);
        tile.setClickable(true);
        tile.setDefaultFocusHighlightEnabled(false);
        // A marked card zooms as one piece, so the check mark stays in its corner.
        View art = isMarked(tag) ? marked(image) : image;
        tile.addView(art, new LinearLayout.LayoutParams(-1, width * 9 / 16));
        // Narrow cards let their caption overflow rather than cut the name short.
        int captionWidth = Math.max(width, dp(MIN_CAPTION));
        LinearLayout.LayoutParams captionSize = new LinearLayout.LayoutParams(captionWidth, -2);
        captionSize.leftMargin = (width - captionWidth) / 2;
        tile.addView(caption, captionSize);
        tile.setTag(tag);
        tile.setContentDescription(name);
        tile.setOnClickListener(v -> action.run());
        if (hold != null) {
            // The key's own long-press flag and the view's timer can both fire: act only once.
            boolean[] held = {false};
            tile.setOnLongClickListener(v -> {
                if (!held[0]) {
                    held[0] = true;
                    hold.run();
                }
                return true;
            });
            tile.setOnKeyListener((v, code, event) -> {
                if (code != KeyEvent.KEYCODE_DPAD_CENTER && code != KeyEvent.KEYCODE_ENTER) return false;
                // A menu opened by holding takes the key-up, so a new press starts clean here.
                if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) held[0] = false;
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    boolean consumed = held[0];
                    held[0] = false;
                    return consumed;
                }
                if (!event.isLongPress() || held[0]) return held[0];
                held[0] = true;
                v.setPressed(false);
                v.cancelLongPress();
                hold.run();
                return true;
            });
        }
        // Glass has no shadow: it would show through as rectangles inside the card.
        CardShadow shadow = look == GLASS ? null
            : new CardShadow(width, width * 9 / 16, radius, dp(1), focusScale());
        tile.setBackground(shadow);
        tile.setOutlineProvider(null);
        tile.setOnFocusChangeListener((view, focused) -> {
            float scale = focused ? focusScale() : 1f;
            ViewPropertyAnimator zoom = art.animate().scaleX(scale).scaleY(scale).setDuration(FOCUS_MS);
            if (shadow != null) {
                // One animator for both: quick presses cannot leave two of them fighting over the shadow.
                float from = shadow.lift();
                float to = focused ? 1 : 0;
                zoom.setUpdateListener(step -> shadow.setLift(from + (to - from) * step.getAnimatedFraction()));
            }
            zoom.start();
            image.setForeground(focused ? focusRing : restEdge);
            if (cardNames == Look.NAMES_ON_FOCUS && showsCaption(view)) {
                caption.setVisibility(focused ? View.VISIBLE : View.INVISIBLE);
            }
            // The card's own row: a move in the Home can take it to another one.
            ((View) view.getParent()).setTranslationZ(focused ? 1 : 0);
            view.setTranslationZ(focused ? 1 : 0);
            if (focused) onCardFocus(view);
        });
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(width, -2);
        size.rightMargin = dp(GAP);
        row.addView(tile, size);
        return tile;
    }

    /** The line of light of liquid glass and a soft reflection from above. */
    protected Drawable glassEdge(int radius) { return glassEdge(radius, 54, 235); }

    /** The glass of a card at rest: on artwork it stays a hint. */
    protected Drawable cardGlass(int radius) { return glassEdge(radius, CARD_REFLECTION, CARD_LIGHT); }

    /** The base of the pill and of the control panel. */
    protected GradientDrawable glass(int radius) {
        GradientDrawable glass = new GradientDrawable();
        glass.setColor(Color.argb(46, 255, 255, 255));
        glass.setCornerRadius(radius);
        return glass;
    }

    /** The row goes inside this rather than holding the glass: a foreground stays inside its view's padding. */
    protected FrameLayout pillBox(View row) {
        FrameLayout pill = new FrameLayout(this);
        pill.setClipChildren(false);
        pill.setBackground(glass(dp(PILL_RADIUS)));
        pill.setForeground(glassEdge(dp(PILL_RADIUS)));
        pill.addView(row);
        return pill;
    }

    protected FrameLayout.LayoutParams pillPlace() {
        FrameLayout.LayoutParams place = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        place.topMargin = dp(22);
        place.rightMargin = dp(DOCK_MARGIN);
        return place;
    }

    protected ImageView pillIcon(int picture, String name) {
        ImageView button = new ImageView(this);
        button.setImageResource(picture);
        int inset = dp(3);
        button.setPadding(inset, inset, inset, inset);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        button.setBackground(circle);
        lightPillIcon(button, false);
        button.setFocusable(true);
        button.setClickable(true);
        button.setDefaultFocusHighlightEnabled(false);
        button.setContentDescription(name);
        button.setOnFocusChangeListener((view, focused) -> lightPillIcon(button, focused));
        return button;
    }

    protected void lightPillIcon(ImageView button, boolean lit) {
        ((GradientDrawable) button.getBackground()).setColor(lit ? Color.WHITE : PILL_ICON_OFF);
        button.setColorFilter(lit ? Color.BLACK : Color.WHITE);
    }

    /** For rows and buttons: flat under the words, the light stays in a few pixels along the edge. */
    protected Drawable softGlass(int radius) {
        GradientDrawable fill = new GradientDrawable();
        fill.setColor(Color.argb(42, 255, 255, 255));
        fill.setCornerRadius(radius);
        return new LayerDrawable(new Drawable[]{fill, GlassRim.soft(radius, SOFT_LIGHT, dp(SOFT_DEPTH))});
    }

    /** What a row or a button becomes under the focus. */
    protected Drawable selectedFill(int radius) {
        GradientDrawable fill = new GradientDrawable();
        fill.setColor(Color.argb(240, 255, 255, 255));
        fill.setCornerRadius(radius);
        return fill;
    }

    /** Every button in Salotto, the same as a row of the Settings. */
    protected TextView button(String name, int sp, Runnable run) {
        TextView view = label(name, sp);
        view.setGravity(Gravity.CENTER);
        view.setSingleLine(true);
        view.setFocusable(true);
        view.setClickable(true);
        view.setPadding(dp(20), dp(BUTTON_PADDING), dp(20), dp(BUTTON_PADDING));
        view.setDefaultFocusHighlightEnabled(false);
        Drawable rest = softGlass(dp(BUTTON_RADIUS));
        Drawable selected = selectedFill(dp(BUTTON_RADIUS));
        view.setBackground(rest);
        view.setOnFocusChangeListener((changed, focused) -> {
            view.setBackground(focused ? selected : rest);
            view.setTextColor(focused ? Fire.INK : Color.WHITE);
            float scale = focused ? 1.03f : 1f;
            view.animate().scaleX(scale).scaleY(scale).setDuration(120).start();
        });
        view.setOnClickListener(changed -> run.run());
        return view;
    }

    /**
     * Plain under the cards: a reflection here would read as a second level of glass. Dark, so it
     * stands out on a bright sky as well as on a night one; light over the shade of the weather
     * scenes, which already darkens that part of the screen.
     */
    protected Drawable dockGlass(int radius, boolean shaded) {
        GradientDrawable fill = new GradientDrawable();
        fill.setColor(shaded ? Color.argb(30, 255, 255, 255) : Color.argb(48, 0, 0, 0));
        fill.setCornerRadius(radius);
        return new LayerDrawable(new Drawable[]{fill, new GlassRim(radius, dp(1), 70)});
    }

    private GradientDrawable sheen(int radius, int reflection) {
        GradientDrawable sheen = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{Color.argb(reflection, 255, 255, 255), Color.TRANSPARENT, Color.TRANSPARENT});
        sheen.setCornerRadius(radius);
        return sheen;
    }

    private Drawable glassEdge(int radius, int reflection, int light) {
        return new LayerDrawable(new Drawable[]{sheen(radius, reflection), new GlassRim(radius, dp(1), light)});
    }

    /** The selection is the one edge meant to be seen all the way round. */
    private Drawable focusEdge(int radius) {
        GradientDrawable ring = new GradientDrawable();
        ring.setColor(Color.TRANSPARENT);
        ring.setStroke(dp(3) / 2, Color.WHITE);
        ring.setCornerRadius(radius);
        return new LayerDrawable(new Drawable[]{sheen(radius, 70), ring});
    }

    /** Opaque where a focus shadow is cast, or it shows through as a rectangle inside the card. */
    private Drawable cardFill(int look) {
        if (look == SHADED) {
            return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.rgb(142, 146, 154), Color.rgb(84, 88, 96)});
        }
        GradientDrawable fill = new GradientDrawable();
        fill.setColor(look == GLASS ? Color.argb(46, 255, 255, 255) : Color.rgb(60, 64, 72));
        fill.setCornerRadius(dp(10));
        return fill;
    }

    @Override public TextView label(String text, int sp) {
        TextView view = new TextView(this);
        view.setText(Brand.mark(this, text));
        view.setTextSize(sp);
        view.setTextColor(Color.WHITE);
        return view;
    }

    protected String focusedTag() {
        View view = getCurrentFocus();
        return view != null && view.getTag() instanceof String ? (String) view.getTag() : null;
    }

    protected void appInfo(String pkg) {
        open(new Intent(this, SettingsActivity.class)
            .putExtra(SettingsActivity.SECTION, SettingsActivity.APP).putExtra(SettingsActivity.PACKAGE, pkg));
    }

    /** A title, one field and one button, all centred, in Salotto's own window: not Android's, with its line under the words. */
    protected void askText(String title, boolean secret, String action, Consumer<String> done) {
        Dialog dialog = new Dialog(this);
        LinearLayout box = windowBox(dialog, title);
        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setTextSize(18);
        field.setTextColor(Color.WHITE);
        field.setGravity(Gravity.CENTER);
        // A rounded field instead of Android's line under the words.
        GradientDrawable well = new GradientDrawable();
        well.setColor(Color.argb(40, 255, 255, 255));
        well.setCornerRadius(dp(12));
        field.setBackground(well);
        field.setPadding(dp(16), dp(10), dp(16), dp(10));
        field.setInputType(secret ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD : InputType.TYPE_CLASS_TEXT);
        field.setImeOptions(secret ? EditorInfo.IME_ACTION_DONE : EditorInfo.IME_ACTION_SEARCH);
        LinearLayout.LayoutParams wide = new LinearLayout.LayoutParams(dp(360), -2);
        wide.topMargin = dp(16);
        box.addView(field, wide);
        TextView button = windowButton(action);
        LinearLayout.LayoutParams under = new LinearLayout.LayoutParams(-2, -2);
        under.topMargin = dp(16);
        box.addView(button, under);
        Runnable send = () -> {
            dialog.dismiss();
            done.accept(field.getText().toString());
        };
        button.setOnClickListener(view -> send.run());
        field.setOnEditorActionListener((view, code, event) -> {
            send.run();
            return true;
        });
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        dialog.show();
    }

    /** The same window with one button for each choice, one under the other: OK picks it. */
    protected void pickText(String title, List<String> names, IntConsumer done) {
        Dialog dialog = new Dialog(this);
        LinearLayout box = windowBox(dialog, title);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        for (int index = 0; index < names.size(); index++) {
            int choice = index;
            TextView button = windowButton(names.get(index));
            button.setOnClickListener(view -> {
                dialog.dismiss();
                done.accept(choice);
            });
            LinearLayout.LayoutParams place = new LinearLayout.LayoutParams(dp(360), -2);
            place.topMargin = dp(10);
            column.addView(button, place);
        }
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(column);
        LinearLayout.LayoutParams under = new LinearLayout.LayoutParams(-2, -2);
        under.topMargin = dp(6);
        box.addView(scroll, under);
        dialog.show();
    }

    /** A rounded dark window with its title centred: what askText and pickText fill. */
    private LinearLayout windowBox(Dialog dialog, String title) {
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(dp(24), dp(20), dp(24), dp(20));
        GradientDrawable fill = new GradientDrawable();
        fill.setColor(Color.rgb(30, 33, 40));
        fill.setCornerRadius(dp(22));
        fill.setStroke(Math.max(1, dp(1)), Color.argb(46, 255, 255, 255));
        box.setBackground(fill);
        TextView heading = label(title, 20);
        heading.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        heading.setGravity(Gravity.CENTER);
        box.addView(heading, new LinearLayout.LayoutParams(-2, -2));
        dialog.setContentView(box);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        return box;
    }

    private TextView windowButton(String text) {
        TextView button = label(text, 18);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(28), dp(10), dp(28), dp(10));
        button.setFocusable(true);
        button.setClickable(true);
        button.setDefaultFocusHighlightEnabled(false);
        Drawable rest = softGlass(dp(20));
        Drawable lit = selectedFill(dp(20));
        button.setBackground(rest);
        button.setOnFocusChangeListener((view, focused) -> {
            view.setBackground(focused ? lit : rest);
            button.setTextColor(focused ? Fire.INK : Color.WHITE);
        });
        return button;
    }

    protected AlertDialog.Builder dialog() {
        return new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert);
    }

    protected void confirm(String title, String message, String action, Runnable run) {
        dialog().setTitle(title).setMessage(message)
            .setNegativeButton(getString(R.string.card_cancel), null)
            .setPositiveButton(action, (dialog, button) -> run.run())
            .show();
    }

    @Override public void say(String text) { Toast.makeText(this, text, Toast.LENGTH_SHORT).show(); }

    @Override public void sayLong(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }

    /** False, with a word on screen, while the connection to the shell is not there. */
    /** What a normal app may not do goes through TaskService. Done runs with the output. */
    @Override public void shell(String command, Consumer<String> done) {
        if (Fire.ANDROID_9) {
            done.accept(null);
            return;
        }
        if (!TaskService.ready() && onCable()) {
            sayLong(getString(R.string.card_needs_wifi));
            done.accept(null);
            return;
        }
        if (!TaskService.ready()) sayLong(getString(R.string.card_tv_may_ask));
        TaskService.shellAsked(command, done);
    }

    /** Android starts wireless debugging only on Wi-Fi, and everything here that needs a shell goes through it. */
    private boolean onCable() {
        ConnectivityManager network = getSystemService(ConnectivityManager.class);
        NetworkCapabilities active = network.getNetworkCapabilities(network.getActiveNetwork());
        return active != null && active.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET);
    }

    /** Android's own dialog asks, and it needs no shell, so it works on a cable too. Salotto itself stays: it is the Home. */
    protected void uninstall(String pkg) {
        if (pkg.equals(getPackageName())) {
            sayLong(getString(R.string.card_home_stays));
            return;
        }
        removing = pkg;
        open(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + pkg)));
    }

    /** Back from Android's dialog: nothing is said if the person chose to keep the app. */
    @Override protected void onResume() {
        super.onResume();
        String pkg = removing;
        removing = null;
        if (pkg == null) return;
        try {
            getPackageManager().getPackageInfo(pkg, 0);
        } catch (PackageManager.NameNotFoundException e) {
            sayLong(getString(R.string.card_app_removed));
            appRemoved();
        }
    }

    /** Lists of apps are built once: they have to be built again. */
    protected void appRemoved() { recreate(); }

    protected Intent launchIntent(String pkg) {
        if (Fire.APPSTORE.equals(pkg)) {
            Intent store = new Intent(Fire.APPSTORE_PAGE).setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (store.resolveActivity(getPackageManager()) != null) return store;
        }
        Intent intent = getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
        return intent != null ? intent : getPackageManager().getLaunchIntentForPackage(pkg);
    }

    protected void openPackage(String pkg) {
        Intent intent = launchIntent(pkg);
        if (intent != null) open(intent);
        else say(getString(R.string.card_app_unavailable));
    }

    @Override public void open(Intent intent) {
        try { startActivity(intent); }
        catch (ActivityNotFoundException | SecurityException e) {
            say(getString(R.string.card_cannot_open));
        }
    }

    @Override public int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
