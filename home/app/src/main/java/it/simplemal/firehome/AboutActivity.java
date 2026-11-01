package it.simplemal.firehome;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.KeyEvent;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Text to read and nothing to press, like the regulatory pages of a device: the arrows scroll it. */
public final class AboutActivity extends CardActivity {
    private static final int WIDTH = 720;
    private static final int LOGO = 44;
    private static final int LOGO_GAP = 12;
    private static final int STEP = 120;
    private static final int BODY_SP = 13;
    private static final int SMALL_SP = 12;
    private static final int SOFT = Color.rgb(190, 190, 190);

    private LinearLayout text;
    private ScrollView scroll;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        FrameLayout root = new FrameLayout(this);
        root.setBackground(Look.plainBackdrop());
        text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setPadding(0, dp(40), 0, dp(48));
        write();
        scroll = new ScrollView(this);
        scroll.setFocusable(true);
        scroll.setDefaultFocusHighlightEnabled(false);
        scroll.setScrollbarFadingEnabled(false);
        scroll.setVerticalFadingEdgeEnabled(true);
        scroll.setFadingEdgeLength(dp(40));
        scroll.addView(text, new FrameLayout.LayoutParams(dp(WIDTH), -2, Gravity.CENTER_HORIZONTAL));
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        scroll.requestFocus();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if (code != KeyEvent.KEYCODE_DPAD_DOWN && code != KeyEvent.KEYCODE_DPAD_UP) return super.dispatchKeyEvent(event);
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            scroll.smoothScrollBy(0, code == KeyEvent.KEYCODE_DPAD_DOWN ? dp(STEP) : -dp(STEP));
        }
        return true;
    }

    private void write() {
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_salotto);
        LinearLayout.LayoutParams logoPlace = new LinearLayout.LayoutParams(dp(LOGO), dp(LOGO));
        logoPlace.setMarginEnd(dp(LOGO_GAP));
        LinearLayout title = new LinearLayout(this);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.addView(logo, logoPlace);
        TextView name = label("Salotto", 26);
        name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.addView(name);
        text.addView(title);
        paragraph(getString(R.string.about_version, BuildVersion.full(this)));
        paragraph(getString(R.string.about_tagline));
        paragraph("https://github.com/" + Fire.UPDATES_REPO);

        section(getString(R.string.about_independent));
        paragraph(getString(R.string.about_independent_text));

        section(getString(R.string.about_goals));
        lead(getString(R.string.about_clean), getString(R.string.about_clean_text));
        lead(getString(R.string.about_private), getString(R.string.about_private_text));
        lead(getString(R.string.about_others), getString(R.string.about_others_text));
        lead(getString(R.string.about_only), getString(R.string.about_only_text));

        section(getString(R.string.about_license));
        paragraph(getString(R.string.about_license_text));

        section(getString(R.string.about_marks));
        paragraph(getString(R.string.about_marks_text));

        section(getString(R.string.about_software));
        credit("Material Icons", "Apache 2.0", getString(R.string.about_use_icons), "github.com/google/material-design-icons");
        credit("Open-Meteo", "CC BY 4.0", getString(R.string.about_use_weather), "open-meteo.com");
        credit("Android Open Source Project", "Apache 2.0", getString(R.string.about_use_platform), "source.android.com");
        credit(getString(R.string.about_gradle_name), "Apache 2.0", getString(R.string.about_use_build), "gradle.org");
        credit("Pillow", "HPND", getString(R.string.about_use_pillow), "python-pillow.org");
        credit("Space Grotesk", "SIL OFL 1.1", getString(R.string.about_use_font), "github.com/floriankarsten/space-grotesk");

        section(getString(R.string.about_not_included));
        paragraph(getString(R.string.about_not_included_text));
        credit("FireStick Fixer", "GPL-3.0", getString(R.string.about_use_fixer), "github.com/Hornet34/firestick-fixer");
        credit("AirPlay Server", "GPL-3.0", getString(R.string.about_use_airplay), "github.com/jqssun/android-airplay-server");
        credit("Aurora Store", "GPL-3.0", getString(R.string.about_use_aurora),
            "f-droid.org/packages/com.aurora.store");
    }

    private void section(String name) {
        TextView title = label(name, 16);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setPadding(0, dp(24), 0, dp(2));
        text.addView(title);
    }

    private void lead(String first, String words) {
        SpannableString whole = new SpannableString(first + " " + words);
        whole.setSpan(new ForegroundColorSpan(Color.WHITE), 0, first.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        paragraph(whole);
    }

    private void paragraph(CharSequence words) {
        TextView view = label("", BODY_SP);
        view.setText(words);
        view.setTextColor(SOFT);
        view.setLineSpacing(0, 1.2f);
        view.setPadding(0, dp(4), 0, 0);
        text.addView(view);
    }

    private void credit(String name, String licence, String use, String address) {
        LinearLayout line = new LinearLayout(this);
        line.setPadding(0, dp(10), 0, 0);
        line.addView(label(name, BODY_SP), new LinearLayout.LayoutParams(0, -2, 1));
        TextView terms = label(licence, BODY_SP);
        terms.setTextColor(SOFT);
        line.addView(terms);
        text.addView(line);
        TextView detail = label(use + "  ·  " + address, SMALL_SP);
        detail.setTextColor(SOFT);
        text.addView(detail);
    }
}
