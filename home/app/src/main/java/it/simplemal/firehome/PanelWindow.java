package it.simplemal.firehome;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.function.Consumer;

/**
 * The control panel over an app: a window of TaskService and not a page, because a page would
 * pause whatever is playing. Only as big as the panel, so the video underneath keeps its own path
 * to the screen. Whoever opens it has to give it the windows of that service.
 */
final class PanelWindow extends ContextWrapper implements ControlPanel.Host {
    private final WindowManager windows;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable closed;
    private FrameLayout root;
    private ControlPanel panel;

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
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT);
        place.gravity = Gravity.TOP | Gravity.END;
        windows.addView(root, place);
        panel.openEmpty(null);
        panel.refresh();
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
        if (TaskService.ready()) TaskService.shellAsked(command, done);
        else done.accept(null);
    }

    @Override public void say(String text) { Toast.makeText(this, text, Toast.LENGTH_SHORT).show(); }

    @Override public void sayLong(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }

    @Override public void runOnUiThread(Runnable run) { main.post(run); }

    @Override public void panelDone() { close(); }
}
