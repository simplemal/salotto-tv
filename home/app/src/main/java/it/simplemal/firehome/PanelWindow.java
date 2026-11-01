package it.simplemal.firehome;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.function.Consumer;

/**
 * The control panel over an app: a window of TaskService and not a page, because a page would
 * pause whatever is playing. It never takes the focus of the screen either: Netflix pauses as soon
 * as it loses that. So TaskService hands it the arrows, OK and Back from the remote, and volume and
 * play stay with the app. Whoever opens it has to give it the windows of that service.
 */
final class PanelWindow extends ContextWrapper implements ControlPanel.Host {
    private final WindowManager windows;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable closed;
    private FrameLayout root;
    private ControlPanel panel;
    private boolean pressed;

    PanelWindow(Context words, WindowManager windows, Runnable closed) {
        super(words);
        this.windows = windows;
        this.closed = closed;
    }

    void open() {
        root = new FrameLayout(this);
        panel = ControlPanel.addTo(this, root, null, false);
        WindowManager.LayoutParams place = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT);
        place.gravity = Gravity.TOP | Gravity.END;
        windows.addView(root, place);
        selectable(panel);
        panel.refresh();
    }

    /** A window without the focus of the screen counts as touched, and its views could not be selected. */
    private static void selectable(View view) {
        if (view.isFocusable()) view.setFocusableInTouchMode(true);
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int index = 0; index < group.getChildCount(); index++) selectable(group.getChildAt(index));
    }

    /** True if the key was the panel's. Nothing is selected until the first arrow. */
    boolean key(KeyEvent event) {
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        int code = event.getKeyCode();
        int direction = direction(code);
        if (direction != 0) {
            if (down) move(direction);
            return true;
        }
        boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER;
        if (!ok && code != KeyEvent.KEYCODE_BACK) return false;
        if (down) {
            pressed = event.getRepeatCount() == 0 || pressed;
        } else if (pressed) {
            pressed = false;
            View selected = root.findFocus();
            if (!ok) close();
            else if (selected != null) selected.performClick();
        }
        return true;
    }

    private static int direction(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT: return View.FOCUS_LEFT;
            case KeyEvent.KEYCODE_DPAD_RIGHT: return View.FOCUS_RIGHT;
            case KeyEvent.KEYCODE_DPAD_UP: return View.FOCUS_UP;
            case KeyEvent.KEYCODE_DPAD_DOWN: return View.FOCUS_DOWN;
            default: return 0;
        }
    }

    private void move(int direction) {
        View selected = root.findFocus();
        View next = selected == null ? panel.first() : selected.focusSearch(direction);
        if (next != null) next.requestFocus(direction);
    }

    void close() {
        if (root == null) return;
        panel.closed();
        windows.removeView(root);
        root = null;
        closed.run();
    }

    @Override public Context context() { return this; }

    @Override public TextView label(String text, int sp) {
        TextView view = new TextView(this);
        view.setText(Brand.mark(this, text));
        view.setTextSize(sp);
        view.setTextColor(0xffffffff);
        return view;
    }

    @Override public int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    /** Whatever opens from here takes over, so the panel goes first. */
    @Override public void open(Intent intent) {
        close();
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException | SecurityException e) {
            say(getString(R.string.card_cannot_open));
        }
    }

    @Override public void shell(String command, Consumer<String> done) {
        TaskService.shellAsked(command, done);
    }

    @Override public void say(String text) { Toast.makeText(this, text, Toast.LENGTH_SHORT).show(); }

    @Override public void sayLong(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }

    @Override public void runOnUiThread(Runnable run) { main.post(run); }

    @Override public void panelDone() { close(); }
}
