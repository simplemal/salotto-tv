package it.simplemal.firehome;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.util.ArrayList;

/** Over the Home, when Fire OS has turned back on what Salotto had turned off: usually an update. */
public final class ReenabledActivity extends CardActivity {
    static final String REENABLED = "reenabled";
    private static final int MOST_NAMES = 6;
    private static final int WHEEL = 40;
    private static final int DIM = Color.argb(204, 0, 0, 0);
    private static final int WIDE = 260;
    private static final int BUTTON_SP = 15;

    private LinearLayout page;
    private TextView note;
    private Runnable back = this::finish;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        FrameLayout root = new FrameLayout(this);
        if (Fire.ANDROID_9) root.setBackground(Look.plainBackdrop());
        else root.setBackgroundColor(DIM);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(page, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
        setContentView(root);
        ArrayList<String> names = getIntent().getStringArrayListExtra(REENABLED);
        if (names == null) finish();
        else if (Fire.ANDROID_9) showWhatToDo(names);
        else showReenabled(names);
    }

    @Override public void onBackPressed() { back.run(); }

    private void showReenabled(ArrayList<String> names) {
        heading(getString(R.string.reenabled_title), getString(R.string.reenabled_text));
        back = this::finish;
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(18), 0, 0);
        int shown = 0;
        for (String name : Reenabled.labels(this, names)) {
            if (shown == MOST_NAMES) break;
            shown++;
            TextView line = label(name, 15);
            line.setGravity(Gravity.CENTER);
            list.addView(line);
        }
        if (names.size() > shown) {
            int left = names.size() - shown;
            TextView more = label(getResources().getQuantityString(
                shown == 0 ? R.plurals.reenabled_parts : R.plurals.reenabled_more_parts, left, left), 14);
            more.setGravity(Gravity.CENTER);
            more.setAlpha(0.7f);
            list.addView(more);
        }
        page.addView(list);
        TextView off = button(getString(R.string.reenabled_turn_off), BUTTON_SP, () -> warnBeforeTurningOff(names));
        page.addView(off, wide(26));
        page.addView(button(getString(R.string.reenabled_leave_on), BUTTON_SP, () -> leaveOn(names)), wide(10));
        off.requestFocus();
    }

    /** Turning them off takes the shell, which here only a computer has. */
    private void showWhatToDo(ArrayList<String> names) {
        heading(getString(R.string.reenabled_title), getString(R.string.reenabled_text));
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(18), 0, 0);
        for (String group : Reenabled.groups(this, names)) {
            TextView line = label(group, 18);
            line.setGravity(Gravity.CENTER);
            list.addView(line);
        }
        page.addView(list);
        TextView steps = label(getString(R.string.reenabled_steps), 15);
        steps.setGravity(Gravity.CENTER);
        steps.setPadding(0, dp(22), 0, 0);
        page.addView(steps);
        TextView close = button(getString(R.string.card_close), BUTTON_SP, () -> leaveOn(names));
        page.addView(close, wide(26));
        close.requestFocus();
    }

    private void leaveOn(ArrayList<String> names) {
        Reenabled.leaveOn(this, names);
        finish();
    }

    private void warnBeforeTurningOff(ArrayList<String> names) {
        heading(getString(R.string.reenabled_before), getString(R.string.reenabled_before_text));
        back = () -> showReenabled(names);
        TextView go = button(getString(R.string.reenabled_continue), BUTTON_SP, () -> turnOff(names));
        page.addView(go, wide(26));
        page.addView(button(getString(R.string.card_cancel), BUTTON_SP, () -> showReenabled(names)), wide(10));
        go.requestFocus();
    }

    private void turnOff(ArrayList<String> names) {
        heading(getString(R.string.reenabled_working), getString(R.string.reenabled_moment));
        back = () -> { };
        ProgressBar wheel = new ProgressBar(this);
        wheel.setIndeterminateTintList(ColorStateList.valueOf(Color.WHITE));
        LinearLayout.LayoutParams place = new LinearLayout.LayoutParams(dp(WHEEL), dp(WHEEL));
        place.topMargin = dp(24);
        page.addView(wheel, place);
        TaskService.turnOffAgain(names, turnedOff -> {
            if (turnedOff) {
                finish();
                return;
            }
            heading(getString(R.string.reenabled_failed), getString(R.string.reenabled_failed_text));
            back = this::finish;
            TextView close = button(getString(R.string.card_close), BUTTON_SP, this::finish);
            page.addView(close, wide(26));
            close.requestFocus();
        });
    }

    private void heading(String name, String text) {
        page.removeAllViews();
        TextView title = label(name, 26);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        page.addView(title);
        note = label(text, 15);
        note.setGravity(Gravity.CENTER);
        note.setAlpha(0.8f);
        note.setPadding(0, dp(8), 0, 0);
        page.addView(note);
    }

    private LinearLayout.LayoutParams wide(int top) {
        LinearLayout.LayoutParams place = new LinearLayout.LayoutParams(dp(WIDE), -2);
        place.topMargin = dp(top);
        return place;
    }
}
