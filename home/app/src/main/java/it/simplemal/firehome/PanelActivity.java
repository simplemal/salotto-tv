package it.simplemal.firehome;

import android.os.Bundle;
import android.widget.FrameLayout;

/** The control panel over an app: Home held opens it. The Home has its own. */
public final class PanelActivity extends CardActivity {
    static volatile boolean showing;
    /** Still there under Fire OS's page, which pauses it. See TaskService.openPanel. */
    static volatile boolean open;

    private ControlPanel panel;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        open = true;
        FrameLayout root = new FrameLayout(this);
        root.setOnClickListener(view -> finish());
        panel = ControlPanel.addTo(this, root, null, false);
        setContentView(root);
        panel.openEmpty(null);
    }

    @Override protected void onResume() {
        super.onResume();
        showing = true;
        panel.refresh();
    }

    @Override protected void onPause() {
        super.onPause();
        showing = false;
    }

    /** A page opened from the panel takes over: coming back there must not find it waiting. */
    @Override protected void onStop() {
        super.onStop();
        finish();
    }

    @Override protected void onDestroy() {
        open = false;
        panel.closed();
        super.onDestroy();
    }
}
