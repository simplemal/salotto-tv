package it.simplemal.firehome;

import android.app.ActivityManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.Formatter;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * The task manager, over whatever is on screen: one row of app pictures. Left and right move
 * along it, OK opens the app, down reaches the close button, up the settings in the pill. Keys are
 * handled here rather than through focus, so that button stays one press below whichever card is
 * selected.
 */
public final class TasksActivity extends CardActivity {
    private static final int SCRIM = Color.argb(225, 8, 10, 14);
    /** Three whole cards and a third of the next on each side show that the row goes on. */
    private static final int CARD_WIDTH = 240;
    private static final int CARD_HEIGHT = 135;
    private static final int CARD_GAP = 16;
    private static final int RADIUS = 12;
    private static final int ART_INSET = 32;
    private static final int BLUR_RADIUS = 6;
    /** The backdrop as this page shows it, kept until it changes: working it out takes a moment. */
    private static String veiledName;
    private static Bitmap veiled;
    /** The round button, shared with the trash of Personale. */
    static final int BUTTON_SIZE = 32;
    static final String CLOSE = "✕";
    static final int CLOSE_SP = 13;
    private static final int BUTTON_GAP = 56;
    static final int BUTTON_RING = 2;
    /** The grey the thin cross looks: over a dark picture the darker fill alone cannot be seen. */
    static final int BUTTON_EDGE = Color.rgb(190, 194, 200);
    static final int BUTTON_REST = Color.rgb(84, 88, 96);
    private static final int ROW_DROP = 48;
    private static final float OTHER_SCALE = 0.86f;
    private static final float OTHER_ALPHA = 0.5f;
    private static final long MOVE_MS = 180;
    private static final long FADE_MS = 160;

    /** What one card shows, read away from the main thread. */
    private static final class Slide {
        HomeApps.App app;
        Bitmap frame;
        Art art;
    }

    /** What the pill shows, read with the cards. */
    private static final class State {
        boolean online;
        boolean wired;
        String memory;
    }

    /** Read by TaskService: Menu held here must not open this again. */
    /** Only the approval of the adb key: opening this page must not make the TV ask anything. */
    static final String CONNECT = "connect";
    static volatile boolean showing;
    /** See TaskService.endBack. */
    static volatile boolean ownBack;
    private static final long PREPARE_MS = 120;

    private final List<View> cards = new ArrayList<>();
    private final GradientDrawable buttonFill = new GradientDrawable();
    private final Handler main = new Handler(Looper.getMainLooper());
    private FrameLayout root;
    private ImageView backdropView;
    private Drawable selection;
    private boolean usageAllowed;
    private int loads;
    private List<HomeApps.App> apps = new ArrayList<>();
    private LinearLayout row;
    private TextView title;
    private TextView button;
    private TextView message;
    private TextView memory;
    private ImageView network;
    private ImageView settings;
    private int index;
    private boolean onButton;
    private boolean onPill;
    private boolean onNetwork;
    private boolean stopping;
    private boolean prepareFailed;
    private final Runnable prepareStop = this::prepareStop;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        root = new FrameLayout(this);
        root.setClipChildren(false);
        backdropView = new ImageView(this);
        backdropView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(backdropView, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        column.setClipChildren(false);
        row = new LinearLayout(this);
        row.setClipChildren(false);
        FrameLayout strip = new FrameLayout(this);
        strip.setClipChildren(false);
        strip.addView(row, new FrameLayout.LayoutParams(-2, dp(CARD_HEIGHT), Gravity.START));
        column.addView(strip, new LinearLayout.LayoutParams(-1, dp(CARD_HEIGHT)));
        title = label("", 19);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(14), 0, dp(12));
        column.addView(title, new LinearLayout.LayoutParams(-1, -2));
        button = label(CLOSE, CLOSE_SP);
        button.setGravity(Gravity.CENTER);
        button.setContentDescription(getString(R.string.card_close));
        button.setOnClickListener(view -> stopSelected());
        buttonFill.setShape(GradientDrawable.OVAL);
        button.setBackground(buttonFill);
        LinearLayout.LayoutParams place = new LinearLayout.LayoutParams(dp(BUTTON_SIZE), dp(BUTTON_SIZE));
        place.topMargin = dp(BUTTON_GAP);
        column.addView(button, place);
        column.setTranslationY(dp(ROW_DROP));
        root.addView(column, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));

        message = label("", 22);
        message.setGravity(Gravity.CENTER);
        message.setPadding(dp(MARGIN), 0, dp(MARGIN), 0);
        root.addView(message, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
        root.addView(pill(), pillPlace());
        setContentView(root);
        root.setAlpha(0);
        dressBackdrop();
    }

    /** The Home's pill, settings in the same place: the network beside it leads to its own section. */
    private View pill() {
        LinearLayout line = new LinearLayout(this);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setClipToPadding(false);
        line.setClipChildren(false);
        line.setPadding(dp(16), dp(6), dp(6), dp(6));
        memory = new TextView(this);
        memory.setTextSize(16);
        memory.setTextColor(Color.WHITE);
        memory.setPadding(0, 0, dp(10), 0);
        line.addView(memory);
        network = pillIcon(R.drawable.ic_wifi, "");
        network.setFocusable(false);
        network.setOnClickListener(view -> openNetwork());
        network.setVisibility(View.GONE);
        LinearLayout.LayoutParams beside = new LinearLayout.LayoutParams(dp(PILL_ICON), dp(PILL_ICON));
        beside.setMarginEnd(dp(PILL_ICON_GAP));
        line.addView(network, beside);
        settings = pillIcon(R.drawable.ic_settings, getString(R.string.first_does_settings));
        settings.setFocusable(false);
        settings.setOnClickListener(view -> openSettings());
        line.addView(settings, new LinearLayout.LayoutParams(dp(PILL_ICON), dp(PILL_ICON)));
        return pillBox(line);
    }

    /** The Home's own backdrop, whatever is on screen: the page stays hidden until it is ready. */
    private void dressBackdrop() {
        String name = Look.currentName(this);
        if (name.equals(veiledName)) {
            backdropView.setImageBitmap(veiled);
            appear();
            return;
        }
        Look.current(this, picture -> Background.draw(() -> {
            Bitmap shown = Blur.veiled(picture, BLUR_RADIUS, SCRIM);
            runOnUiThread(() -> {
                veiledName = name;
                veiled = shown;
                if (isDestroyed()) return;
                backdropView.setImageBitmap(shown);
                appear();
            });
        }));
    }

    private void appear() {
        root.animate().alpha(1).setDuration(FADE_MS).withEndAction(TaskService::tasksShown).start();
    }

    @Override protected void onResume() {
        super.onResume();
        showing = true;
        if (getIntent().getBooleanExtra(CONNECT, false)) TaskService.retry();
        load();
    }

    @Override protected void onPause() {
        super.onPause();
        showing = false;
    }

    /** Usage events, pictures and artwork all take time: this page opens over an app, on a held key. */
    private void load() {
        int load = ++loads;
        Background.run(() -> {
            State state = state();
            boolean allowed = RunningApps.allowed(this);
            List<Slide> slides = new ArrayList<>();
            if (allowed) {
                for (HomeApps.App app : RunningApps.list(this)) {
                    Slide slide = new Slide();
                    slide.app = app;
                    slide.frame = picture(app.pkg);
                    if (slide.frame == null) slide.art = art(app.pkg);
                    slides.add(slide);
                }
            }
            runOnUiThread(() -> {
                if (load == loads && !isDestroyed()) fill(slides, allowed, state);
            });
        });
    }

    /** Free is what Android calls available: it counts what it can take back from apps at rest. */
    private State state() {
        State state = new State();
        state.online = DeviceInfo.online(this);
        state.wired = DeviceInfo.wired(this);
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        getSystemService(ActivityManager.class).getMemoryInfo(info);
        state.memory = getString(R.string.tasks_memory, Formatter.formatShortFileSize(this, info.totalMem),
            Formatter.formatShortFileSize(this, info.availMem));
        return state;
    }

    private void showState(State state) {
        memory.setText(state.memory);
        network.setVisibility(state.online ? View.VISIBLE : View.GONE);
        if (!state.online) onNetwork = false;
        network.setImageResource(state.wired ? R.drawable.ic_ethernet : R.drawable.ic_wifi);
        network.setContentDescription(state.wired ? "Ethernet" : "Wi-Fi");
    }

    private void fill(List<Slide> slides, boolean allowed, State state) {
        showState(state);
        usageAllowed = allowed;
        apps = new ArrayList<>();
        row.removeAllViews();
        cards.clear();
        for (int position = 0; position < slides.size(); position++) {
            Slide slide = slides.get(position);
            apps.add(slide.app);
            View card = card(slide, position);
            cards.add(card);
            row.addView(card, cardSize());
        }
        index = Math.min(index, Math.max(0, apps.size() - 1));
        if (apps.isEmpty()) onButton = false;
        show(false);
    }

    private LinearLayout.LayoutParams cardSize() {
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(dp(CARD_WIDTH), dp(CARD_HEIGHT));
        size.rightMargin = dp(CARD_GAP);
        return size;
    }

    private View card(Slide slide, int position) {
        ImageView image = new ImageView(this);
        image.setBackgroundColor(Color.rgb(28, 30, 36));
        if (slide.frame != null) {
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setImageBitmap(slide.frame);
        } else {
            Art art = slide.art;
            image.setImageDrawable(art.image);
            if (art.back != null) image.setBackground(art.back);
            if (art.look == BANNER) {
                image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            } else {
                int inset = art.back != null ? 0 : dp(ART_INSET);
                image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                image.setPadding(inset, inset, inset, inset);
            }
        }
        image.setClipToOutline(true);
        image.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(RADIUS));
            }
        });
        Drawable glass = cardGlass(dp(RADIUS));
        image.setTag(glass);
        image.setForeground(glass);
        image.setOnClickListener(view -> {
            if (position == index) openSelected();
            else select(position);
        });
        return image;
    }

    private Bitmap picture(String pkg) {
        File file = RunningApps.picture(this, pkg);
        Bitmap frame = file.isFile() ? BitmapFactory.decodeFile(file.getPath()) : null;
        return frame == null || RunningApps.blank(frame) ? null : frame;
    }

    private void select(int position) {
        index = position;
        onButton = false;
        onPill = false;
        onNetwork = false;
        show(true);
    }

    /** Keeps the selected card in the middle of the screen. */
    private void show(boolean animate) {
        lightPillIcon(settings, onPill && !onNetwork);
        lightPillIcon(network, onPill && onNetwork);
        boolean any = !apps.isEmpty();
        message.setVisibility(any ? View.GONE : View.VISIBLE);
        title.setVisibility(any ? View.VISIBLE : View.INVISIBLE);
        button.setVisibility(any ? View.VISIBLE : View.INVISIBLE);
        if (!any) {
            message.setText(usageAllowed ? R.string.tasks_none : R.string.tasks_no_permission);
            return;
        }
        title.setText(apps.get(index).label);
        int screen = getResources().getDisplayMetrics().widthPixels;
        float offset = screen / 2f - dp(CARD_WIDTH) / 2f - index * (dp(CARD_WIDTH) + dp(CARD_GAP));
        long time = animate ? MOVE_MS : 0;
        boolean elsewhere = onButton || onPill;
        row.animate().translationX(offset).setDuration(time).start();
        for (int position = 0; position < cards.size(); position++) {
            boolean chosen = position == index;
            View card = cards.get(position);
            card.animate().scaleX(chosen ? 1 : OTHER_SCALE).scaleY(chosen ? 1 : OTHER_SCALE)
                .alpha(chosen ? 1 : OTHER_ALPHA).setDuration(time).start();
            if (!chosen || elsewhere) card.setForeground((Drawable) card.getTag());
        }
        // After the others have let it go: one card at a time wears the same edge.
        if (!elsewhere) {
            View card = cards.get(index);
            card.setForeground(new LayerDrawable(new Drawable[]{(Drawable) card.getTag(), edge()}));
        }
        buttonFill.setColor(onButton ? Color.WHITE : BUTTON_REST);
        buttonFill.setStroke(dp(BUTTON_RING), onButton ? Color.WHITE : BUTTON_EDGE);
        button.setTextColor(onButton ? Fire.INK : Color.WHITE);
        // While Fire OS's page gets ready a wheel takes this place: no X to press too soon.
        button.setText(onButton && !prepareFailed ? "" : CLOSE);
    }

    private Drawable edge() {
        if (selection == null) {
            GradientDrawable edge = new GradientDrawable();
            edge.setCornerRadius(dp(RADIUS));
            edge.setStroke(dp(2), Color.WHITE);
            selection = edge;
        }
        return selection;
    }

    private void openSelected() {
        if (apps.isEmpty()) return;
        openPackage(apps.get(index).pkg);
        finish();
    }

    private void openSettings() {
        open(new Intent(this, SettingsActivity.class));
        finish();
    }

    private void openNetwork() {
        open(new Intent(this, SettingsActivity.class).putExtra(SettingsActivity.SECTION, SettingsActivity.NETWORK));
        finish();
    }

    /**
     * Closing the app that was on screen leaves nothing to come back to, so the Home follows.
     * If Fire OS's page was not as expected, the app's own page in Salotto opens instead.
     */
    private void stopSelected() {
        if (apps.isEmpty() || stopping || !prepareFailed) return;
        String pkg = apps.get(index).pkg;
        stopping = true;
        TaskService.stop(pkg, stopped -> {
            stopping = false;
            if (isFinishing()) return;
            if (!stopped) {
                sayLong(getString(R.string.tasks_stop_failed));
                appInfo(pkg);
            } else {
                load();
            }
        });
    }

    private boolean ownBackDown;

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        boolean ours = code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT
            || code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN
            || code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
            || code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_MENU;
        if (!ours) return super.dispatchKeyEvent(event);
        if (code == KeyEvent.KEYCODE_BACK && ownBack) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) ownBackDown = true;
            else if (ownBackDown) ownBack = ownBackDown = false;
            return true;
        }
        // Back held opened this page, and the key goes on repeating until it is released.
        boolean repeat = event.getRepeatCount() > 0 && (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_MENU);
        if (event.getAction() == KeyEvent.ACTION_DOWN && !repeat) press(code);
        // Only once the key is up: a key still down would end up on Fire OS's page.
        if (event.getAction() == KeyEvent.ACTION_UP && code == KeyEvent.KEYCODE_DPAD_DOWN && onButton) {
            main.removeCallbacks(prepareStop);
            main.postDelayed(prepareStop, PREPARE_MS);
        }
        return true;
    }

    /** See TaskService.prepareStop. The OK on the X goes to Fire OS, so this page only hears how it went. */
    private void prepareStop() {
        if (!onButton || apps.isEmpty() || stopping) return;
        String pkg = apps.get(index).pkg;
        prepareFailed = false;
        TaskService.prepareStop(this, button, pkg, result -> {
            if (isFinishing()) return;
            if (result == TaskService.STOP_FAILED) {
                prepareFailed = true;
                show(false);
            } else if (result == TaskService.STOP_UP) {
                onButton = false;
                show(false);
            } else if (result == TaskService.STOP_BACK) {
                finish();
            } else {
                onButton = false;
                load();
            }
        });
    }

    private void press(int code) {
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_MENU) {
            finish();
        } else if (onPill) {
            if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) {
                if (onNetwork) openNetwork();
                else openSettings();
            } else if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
                onPill = false;
                onNetwork = false;
                show(false);
            } else if (code == KeyEvent.KEYCODE_DPAD_LEFT && network.getVisibility() == View.VISIBLE) {
                onNetwork = true;
                show(false);
            } else if (code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                onNetwork = false;
                show(false);
            }
        } else if (code == KeyEvent.KEYCODE_DPAD_LEFT && !onButton && index > 0) {
            select(index - 1);
        } else if (code == KeyEvent.KEYCODE_DPAD_RIGHT && !onButton && index < apps.size() - 1) {
            select(index + 1);
        } else if (code == KeyEvent.KEYCODE_DPAD_DOWN && !apps.isEmpty()) {
            onButton = true;
            prepareFailed = false;
            show(false);
        } else if (code == KeyEvent.KEYCODE_DPAD_UP) {
            onPill = !onButton;
            onButton = false;
            main.removeCallbacks(prepareStop);
            show(false);
        } else if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) {
            if (onButton) stopSelected();
            else openSelected();
        }
    }
}
