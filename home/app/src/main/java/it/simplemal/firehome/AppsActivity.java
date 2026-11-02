package it.simplemal.firehome;

import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.List;

/** Every installed app, opened from the Home's small band; holding OK edits the Home list. */
public final class AppsActivity extends CardActivity {
    private TextView hint;
    private LinearLayout page;
    private List<String> home;
    private String shown;
    private int width;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Look.plainBackdrop());
        hint = label("", 15);
        hint.setGravity(Gravity.CENTER);
        hint.setTextColor(Color.argb(170, 255, 255, 255));
        hint.setPadding(0, dp(24), 0, dp(4));
        root.addView(hint);
        ScrollView scroll = new ScrollView(this);
        scroll.setClipChildren(false);
        page = page();
        scroll.addView(page);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    @Override protected void onResume() {
        super.onResume();
        home = HomeApps.load(this);
        if (!state().equals(shown)) render(focusedTag());
    }

    /** What the page depends on: coming back with all of it unchanged builds nothing. */
    private String state() {
        return HomeApps.changes() + "|" + TextUtils.join(",", home) + "|" + Look.signature(this);
    }

    private void render(String focus) {
        page.removeAllViews();
        startCards();
        shown = state();
        int columns = Look.columns(this);
        width = cardWidth(columns, dp(MARGIN));
        View first = null;
        View target = null;
        LinearLayout row = null;
        List<HomeApps.App> apps = HomeApps.installed(this);
        for (int index = 0; index < apps.size(); index++) {
            if (index % columns == 0) row = row(page);
            HomeApps.App app = apps.get(index);
            View tile = appCard(row, app.label, app.pkg, width, () -> showMenu(app));
            if (first == null) first = tile;
            if (app.pkg.equals(focus)) target = tile;
        }
        if (target != null) target.requestFocus();
        else if (first != null) first.requestFocus();
    }

    /** Takes the old card out last: taken out while it has the focus, the page would jump to the top. */
    private void redrawCard(HomeApps.App app) {
        View old = page.findViewWithTag(app.pkg);
        LinearLayout row = (LinearLayout) old.getParent();
        View tile = appCard(row, app.label, app.pkg, width, () -> showMenu(app));
        row.removeView(tile);
        row.addView(tile, row.indexOfChild(old));
        tile.requestFocus();
        row.removeView(old);
        shown = state();
    }

    @Override protected boolean isMarked(String tag) { return home.contains(tag); }

    @Override protected void appRemoved() { render(focusedTag()); }

    @Override protected void onCardFocus(View tile) {
        hint.setText(home.contains(tile.getTag()) ? R.string.apps_remove_hint : R.string.apps_add_hint);
    }

    private void showMenu(HomeApps.App app) {
        boolean listed = home.contains(app.pkg);
        dialog()
            .setTitle(app.label)
            .setItems(new String[]{getString(listed ? R.string.card_remove_from_home : R.string.card_add_to_home),
                    getString(R.string.card_app_info), getString(R.string.card_uninstall)},
                (dialog, choice) -> {
                    if (choice == 1) {
                        appInfo(app.pkg);
                    } else if (choice == 2) {
                        uninstall(app.pkg);
                    } else {
                        if (listed) home.remove(app.pkg);
                        else home.add(app.pkg);
                        HomeApps.save(this, home);
                        redrawCard(app);
                    }
                })
            .show();
    }
}
