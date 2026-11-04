package it.simplemal.firehome;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.json.JSONException;

/**
 * The whole update, on one screen: the offer with its changelog, the checks that touch nothing,
 * the installation, and the result. While installing, Salotto stops running: update-state carries
 * the story across, and TaskService reopens this page in the new version. Only going back needs adb.
 */
public final class UpdateActivity extends CardActivity {
    static final String EXTRA_AFTER = "after";
    private static final long POLL_MS = 500;
    /** Long enough for a second try after the first one's half minute at the question on the TV. */
    private static final long READY_WAIT_MS = 100000;
    private static final long HEALTH_WAIT_MS = 30000;
    private static final long INSTALL_LIMIT_MS = 120000;
    private static final long END_LIMIT_MS = 20000;
    private static final long ALLOW_WAIT_MS = 5000;
    /** Versions up to 0.12.20 install the next one from a script, which waits for its word. */
    private static final String OLD_SCRIPT = "starting";
    private static final long HOME_AFTER_MS = 2500;
    /** A download that has not started by then will not: the network, or GitHub. */
    private static final long BACKDROP_START_MS = 15000;
    private static final long BACKDROP_LONGEST_MS = 5 * 60 * 1000;
    /** Fixed, so these pages stay readable whatever the Home wallpaper is. */
    static final int BACKGROUND = Color.rgb(24, 26, 32);
    private static final int BOX = Color.rgb(14, 15, 19);
    private static final int TOP = 40;
    private static final int BOTTOM = 22;
    private static final int STEP_SP = 14;
    private static final int CHANGE_SP = 12;
    private static final int ACTION_SP = 15;
    private static final int ERROR_SP = 15;
    private static final int ROOM_WIDTH = 640;
    private static final int BAR_HEIGHT = 5;
    private static final long PAGE_DRAWN_MS = 100;

    private static final int[] STEPS = {
        R.string.update_step_check, R.string.update_step_room, R.string.update_step_download,
        R.string.update_step_verify, R.string.update_step_choices, R.string.update_step_copy,
        R.string.update_step_install, R.string.update_step_health};
    private static final List<String> UNFINISHED = Arrays.asList("installing", OLD_SCRIPT, "rollback");
    private static final int CHECK = 0;
    private static final int ROOM = 1;
    private static final int DOWNLOAD = 2;
    private static final int VERIFY = 3;
    private static final int CHOICES = 4;
    private static final int COPY = 5;
    private static final int INSTALL = 6;
    private static final int HEALTH = 7;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<TextView> stepViews = new ArrayList<>();
    private LinearLayout page;
    private LinearLayout body;
    private TextView title;
    private TextView line;
    private ProgressBar bar;
    private boolean busy;
    private long deadline;
    private Updater.Release release;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.setBackgroundColor(BACKGROUND);
        page.setPadding(dp(MARGIN * 3), dp(TOP), dp(MARGIN * 3), dp(BOTTOM));
        setContentView(page);
        if (getIntent().getBooleanExtra(EXTRA_AFTER, false)) resume();
        else look();
    }

    private TextView text(String value, int size, boolean strong) {
        TextView view = label(value, size);
        view.setGravity(Gravity.CENTER);
        if (strong) view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return view;
    }

    private void clear() {
        page.removeAllViews();
        stepViews.clear();
        title = text("", 28, true);
        title.setPadding(0, 0, 0, dp(14));
        page.addView(title);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setGravity(Gravity.CENTER);
        page.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));
    }

    private View action(String name, Runnable run) {
        return button(name, ACTION_SP, run);
    }

    private void actions(View... views) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        row.setPadding(0, dp(16), 0, 0);
        for (View view : views) {
            LinearLayout.LayoutParams place = new LinearLayout.LayoutParams(-2, -2);
            place.setMarginStart(dp(8));
            place.setMarginEnd(dp(8));
            row.addView(view, place);
        }
        page.addView(row);
        views[0].requestFocusFromTouch();
    }

    private void look() {
        clear();
        title.setText(Brand.mark(this, getString(R.string.update_title)));
        line = text(getString(R.string.update_looking), 19, false);
        body.addView(line);
        background(() -> {
            Updater.Release found = Updater.check(this);
            main.post(() -> {
                if (found == null) showCurrent();
                else showOffer(found);
            });
        });
    }

    private void showCurrent() {
        clear();
        title.setText(Brand.mark(this, getString(R.string.update_current)));
        body.addView(text(getString(R.string.update_current_text), 19, false));
        actions(action(getString(R.string.card_close), this::finish));
    }

    private void showOffer(Updater.Release found) {
        release = found;
        clear();
        title.setText(Brand.mark(this, getString(R.string.update_available, found.versionName)));
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(20), dp(2), dp(15), dp(2));
        for (String change : found.changes(this)) {
            TextView item = label("•  " + change, CHANGE_SP);
            item.setPadding(0, dp(3), 0, dp(3));
            list.addView(item);
        }
        ScrollView box = new ScrollView(this);
        box.setDefaultFocusHighlightEnabled(false);
        box.setScrollbarFadingEnabled(false);
        // The padding keeps the scroll bar off the rounded corners; the text still reaches the edge.
        box.setPadding(0, dp(12), dp(5), dp(12));
        box.setClipToPadding(false);
        box.setClipToOutline(true);
        box.addView(list);
        paintBox(box);
        box.setOnFocusChangeListener((changed, focused) -> paintBox(box));
        // Up reaches the list only when there is more of it to see.
        box.post(() -> box.setFocusable(box.canScrollVertically(1)));
        body.addView(box, new LinearLayout.LayoutParams(-1, -1));
        View later = action(getString(R.string.update_later), this::finish);
        actions(action(getString(R.string.update_start), this::start), later);
        // This page opens by itself: an OK meant for something else must not start an update.
        later.requestFocusFromTouch();
    }

    private void paintBox(View box) {
        GradientDrawable fill = new GradientDrawable();
        fill.setCornerRadius(dp(14));
        fill.setColor(BOX);
        fill.setStroke(dp(1), box.isFocused() ? Color.WHITE : Color.argb(90, 255, 255, 255));
        box.setBackground(fill);
    }

    private void showSteps() {
        clear();
        title.setText(Brand.mark(this, getString(R.string.update_to, release != null ? release.versionName : BuildVersion.name(this))));
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(24), dp(18), dp(24), dp(18));
        paintBox(panel);
        for (int step : STEPS) {
            TextView view = label("○  " + getString(step), STEP_SP);
            view.setAlpha(0.55f);
            view.setPadding(0, dp(3), 0, dp(3));
            stepViews.add(view);
            panel.addView(view);
        }
        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgressDrawable(barDrawable());
        LinearLayout.LayoutParams place = new LinearLayout.LayoutParams(-1, dp(BAR_HEIGHT));
        place.topMargin = dp(16);
        panel.addView(bar, place);
        line = label("", CHANGE_SP);
        line.setPadding(0, dp(10), 0, 0);
        panel.addView(line);
        body.addView(panel, new LinearLayout.LayoutParams(-1, -2));
    }

    private Drawable barDrawable() {
        GradientDrawable track = new GradientDrawable();
        track.setCornerRadius(dp(BAR_HEIGHT));
        track.setColor(Color.argb(40, 255, 255, 255));
        GradientDrawable fill = new GradientDrawable();
        fill.setCornerRadius(dp(BAR_HEIGHT));
        fill.setColor(Color.WHITE);
        LayerDrawable layers = new LayerDrawable(new Drawable[] {
            track, new ClipDrawable(fill, Gravity.START, ClipDrawable.HORIZONTAL)});
        layers.setId(0, android.R.id.background);
        layers.setId(1, android.R.id.progress);
        return layers;
    }

    /** Marks the steps before `step` as done and shows this one as running. */
    private void step(int step, String detail) {
        main.post(() -> {
            for (int index = 0; index < stepViews.size(); index++) {
                TextView view = stepViews.get(index);
                boolean done = index < step;
                view.setText((done ? "✓  " : index == step ? "●  " : "○  ") + getString(STEPS[index]));
                view.setAlpha(index <= step ? 1f : 0.55f);
            }
            bar.setProgress((step * 100) / STEPS.length);
            line.setText(detail);
        });
    }

    private interface Job { void run() throws Updater.Failure; }

    private void background(Job job) {
        Background.run(() -> {
            try {
                job.run();
            } catch (Updater.NoRoom room) {
                main.post(() -> showNoRoom(room));
            } catch (Updater.Failure failure) {
                main.post(() -> showError(failure.error));
            }
        });
    }

    private void start() {
        busy = true;
        showSteps();
        background(() -> {
            // Nobody else would press Install, and from the background the question could not open.
            if (!TaskService.running()) throw new Updater.Failure(UpdateError.F115);
            step(CHECK, getString(R.string.update_detail_check));
            release = Updater.check(this);
            if (release == null) throw new Updater.Failure(UpdateError.F105);
            step(ROOM, getString(R.string.update_detail_room));
            Updater.checkIdle(this);
            Updater.checkRoom(this, release);
            step(DOWNLOAD, getString(R.string.update_detail_download));
            File apk = Updater.download(this, release, done -> main.post(() -> {
                bar.setProgress((DOWNLOAD * 100 + done) / STEPS.length);
                line.setText(getString(R.string.update_detail_percent, done));
            }));
            step(VERIFY, getString(R.string.update_detail_verify));
            Updater.inspect(this, apk, release);
            step(CHOICES, getString(R.string.update_detail_choices));
            saveChoices();
            step(COPY, getString(R.string.update_detail_copy));
            // Android 9 goes back only from the computer: a copy there would only take room the install needs.
            if (!Fire.ANDROID_9) Updater.keepCurrent(this);
            allowInstalling();
            Updater.recheck(apk, release);
            install(apk);
        });
    }

    /** Given at installation: only a Stick set up by an older version comes through here, once. */
    private void allowInstalling() throws Updater.Failure {
        if (getPackageManager().canRequestPackageInstalls()) return;
        step(COPY, getString(R.string.update_detail_allow));
        waitForConnection();
        TaskService.shell("appops set " + getPackageName() + " REQUEST_INSTALL_PACKAGES allow", output -> { });
        long limit = System.currentTimeMillis() + ALLOW_WAIT_MS;
        while (!getPackageManager().canRequestPackageInstalls()) {
            if (System.currentTimeMillis() > limit) throw new Updater.Failure(UpdateError.F115);
            pause();
        }
    }

    private void waitForConnection() throws Updater.Failure {
        long limit = System.currentTimeMillis() + READY_WAIT_MS;
        while (!TaskService.ready()) {
            // Retries are spaced out: one refused now may be allowed before the wait ends.
            TaskService.retry();
            if (System.currentTimeMillis() > limit) throw new Updater.Failure(UpdateError.F115);
            pause();
        }
    }

    private void pause() {
        try {
            Thread.sleep(POLL_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void saveChoices() throws Updater.Failure {
        try {
            Backup.save(this);
        } catch (IOException | JSONException e) {
            throw new Updater.Failure(UpdateError.F116);
        }
    }

    private void install(File apk) throws Updater.Failure {
        Updater.setTarget(this, release.versionCode);
        Updater.write(Updater.state(this), "installing");
        step(INSTALL, getString(R.string.update_detail_install));
        main.postDelayed(() -> {
            Cover.keepPage(page);
            background(() -> replace(apk));
        }, PAGE_DRAWN_MS);
    }

    private void replace(File apk) throws Updater.Failure {
        Cover.prepare(this);
        Cover.show(this);
        try {
            SelfInstall.start(this, apk, getPackageName());
        } catch (IOException | SecurityException e) {
            throw new Updater.Failure(UpdateError.C201);
        }
        deadline = System.currentTimeMillis() + INSTALL_LIMIT_MS;
        main.post(this::watch);
    }

    /** This process ends when the new version is installed. Before that, Android can only refuse. */
    private void watch() {
        String state = Updater.read(Updater.state(this));
        if (!state.equals("installing")) finishFrom(state);
        else if (expired()) showError(UpdateError.R303);
        else main.postDelayed(this::watch, POLL_MS);
    }

    private boolean expired() { return System.currentTimeMillis() > deadline; }

    /** Reopened as the new version to test itself, or by the shell at the end of the way back. */
    private void resume() {
        busy = true;
        showSteps();
        String state = Updater.read(Updater.state(this));
        // With no script left behind the state, waiting for it is pointless.
        deadline = Updater.interrupted(this) ? 0 : System.currentTimeMillis() + INSTALL_LIMIT_MS;
        if (Updater.installed(this) || state.equals(OLD_SCRIPT) && !expired()) confirmHealth();
        else finishFrom(state);
    }

    private void confirmHealth() {
        step(HEALTH, getString(R.string.update_detail_health));
        background(() -> {
            long limit = System.currentTimeMillis() + HEALTH_WAIT_MS;
            String verdict = "good";
            if (Updater.installedCode(this) != Updater.target(this)) verdict = UpdateError.C204.name();
            else {
                while (!TaskService.running() && System.currentTimeMillis() < limit) pause();
                if (!TaskService.running()) verdict = UpdateError.C206.name();
            }
            String word = verdict;
            main.post(() -> judged(word));
        });
    }

    private void judged(String verdict) {
        boolean script = Updater.read(Updater.state(this)).equals(OLD_SCRIPT);
        if (script) {
            Updater.write(Updater.health(this), verdict);
            deadline = System.currentTimeMillis() + END_LIMIT_MS;
            followEnd();
        } else if (verdict.equals("good")) {
            showDone();
        } else {
            goBack(verdict);
        }
    }

    private void goBack(String verdict) {
        if (Fire.ANDROID_9) {
            showError(UpdateError.R304);
            return;
        }
        step(HEALTH, getString(R.string.update_detail_back_ask));
        background(() -> {
            try {
                waitForConnection();
            } catch (Updater.Failure e) {
                throw new Updater.Failure(UpdateError.R302);
            }
            TaskService.rollback(Updater.previous(this).getPath(), verdict, started -> {
                if (!started) {
                    showError(UpdateError.R302);
                    return;
                }
                Cover.keepPage(page);
                Cover.show(this);
                deadline = System.currentTimeMillis() + INSTALL_LIMIT_MS;
                followEnd();
            });
        });
    }

    private void followEnd() {
        String state = Updater.read(Updater.state(this));
        if (expired()) showError(UpdateError.R303);
        else if (state.isEmpty() || state.equals(OLD_SCRIPT) || state.equals("installing")) main.postDelayed(this::followEnd, POLL_MS);
        else finishFrom(state);
    }

    private void finishFrom(String state) {
        List<String> parts = Arrays.asList(TextUtils.split(state, " "));
        String kind = parts.isEmpty() ? "" : parts.get(0);
        String code = parts.size() > 1 ? parts.get(1) : "R303";
        if (kind.equals("done")) {
            showDone();
        } else if (kind.equals("failed")) {
            showError(UpdateError.parse(code));
        } else if (kind.equals("rolled-back")) {
            restoreChoices();
            showError(UpdateError.parse(code));
        } else if (kind.equals("rollback-failed")) {
            showError(UpdateError.R302);
        } else if (expired() || !UNFINISHED.contains(kind)) {
            showError(UpdateError.R303);
        } else {
            if (kind.equals("rollback")) step(HEALTH, getString(R.string.update_detail_back));
            main.postDelayed(() -> finishFrom(Updater.read(Updater.state(this))), POLL_MS);
        }
    }

    private void restoreChoices() {
        try {
            Backup.restore(this);
        } catch (IOException | JSONException e) {
            // The installed version keeps its own data: the copy is only a second safety.
        }
    }

    private void showDone() {
        busy = false;
        clear();
        title.setText(getString(R.string.update_done));
        body.addView(text(getString(R.string.update_done_text, BuildVersion.name(this)), 19, false));
        forget();
        // The backdrops left the app: the Home is shown only once its own has come, never a stand-in.
        String pack = Look.chosenPack(this);
        if (pack != null && Packs.missing(this).contains(pack)) {
            TextView coming = text(getString(R.string.update_backdrops, 0), 16, false);
            body.addView(coming);
            Packs.fetchChosen(this);
            waitBackdrop(pack, coming, SystemClock.uptimeMillis());
        } else {
            main.postDelayed(this::home, HOME_AFTER_MS);
        }
    }

    /** Until the collection is whole, the download has ended without it, or a few minutes have gone. */
    private void waitBackdrop(String pack, TextView coming, long since) {
        int percent = Packs.percent(pack);
        if (percent >= 0) coming.setText(getString(R.string.update_backdrops, percent));
        boolean over = Packs.present(this, pack) || !Packs.fetching() && percent < 0 && SystemClock.uptimeMillis() - since > BACKDROP_START_MS
            || SystemClock.uptimeMillis() - since > BACKDROP_LONGEST_MS;
        if (over) home();
        else main.postDelayed(() -> waitBackdrop(pack, coming, since), POLL_MS);
    }

    private void forget() { Updater.clean(this); }

    private void home() {
        startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        finish();
    }

    /** Checked before anything is downloaded or changed: Salotto stays exactly as it is. */
    private void showNoRoom(Updater.NoRoom room) {
        TaskService.installEnded();
        busy = false;
        forget();
        clear();
        title.setText(getString(R.string.update_room_title));
        TextView numbers = text(getString(R.string.update_room_numbers, Formatter.formatShortFileSize(this, room.needed),
            Formatter.formatShortFileSize(this, Math.max(0, room.free))), ERROR_SP, false);
        numbers.setPadding(0, 0, 0, dp(16));
        body.addView(numbers);
        body.addView(text(getString(R.string.update_room_choices, "github.com/" + Fire.UPDATES_REPO), ERROR_SP, false),
            new LinearLayout.LayoutParams(dp(ROOM_WIDTH), -2));
        boolean alone = getIntent().getBooleanExtra(EXTRA_AFTER, false);
        actions(action(getString(R.string.card_close), alone ? this::home : this::finish));
    }

    private void showError(UpdateError error) {
        TaskService.installEnded();
        busy = false;
        forget();
        clear();
        title.setText(error.outcome(this));
        TextView problem = text(getString(R.string.update_problem_line, error.title(this), error.code()), ERROR_SP, false);
        problem.setPadding(0, 0, 0, dp(16));
        body.addView(problem);
        ImageView code = new ImageView(this);
        code.setImageResource(R.drawable.qr_errori);
        body.addView(code, new LinearLayout.LayoutParams(-2, -2));
        if (error == UpdateError.R304) {
            TextView steps = text(getString(R.string.update_back_computer), CHANGE_SP, false);
            steps.setPadding(0, 0, 0, dp(16));
            body.addView(steps, 1);
        }
        TextView help = text(getString(R.string.update_scan_help), CHANGE_SP, false);
        help.setPadding(0, dp(12), 0, 0);
        body.addView(help);
        // From the computer Salotto is installed another way, which gets past an update that keeps failing here.
        if (error != UpdateError.R304) {
            TextView computer = text(getString(R.string.update_from_computer, "github.com/" + Fire.UPDATES_REPO), CHANGE_SP, false);
            computer.setPadding(0, dp(12), 0, 0);
            body.addView(computer);
        }
        // Reopened by the install script, this page has nothing of Salotto beneath it.
        boolean alone = getIntent().getBooleanExtra(EXTRA_AFTER, false);
        actions(action(getString(R.string.card_close), alone ? this::home : this::finish));
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        boolean locked = busy && (event.getKeyCode() == KeyEvent.KEYCODE_BACK);
        return locked || super.dispatchKeyEvent(event);
    }
}
