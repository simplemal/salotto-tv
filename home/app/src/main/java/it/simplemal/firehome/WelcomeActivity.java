package it.simplemal.firehome;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONException;

/**
 * Over the Home each time the Stick starts, not back from standby: the keys held down are Salotto's
 * own, so nothing on the remote tells of them. "Chiudi" brings it back at the next start. The first
 * time it goes on to the city, in the same style: the Settings can do that too, but this is the
 * introduction.
 */
public final class WelcomeActivity extends CardActivity {
    private static final String FILE = "welcome";
    private static final String NEVER = "never";
    private static final String BOOT = "boot";
    static final String REENABLED = "reenabled";
    private static final String GREETED = "greeted";
    private static final int MOST_NAMES = 6;
    private static final int WHEEL = 40;
    private static final int GREETING_LOGO = 150;
    private static final int DIM = Color.argb(204, 0, 0, 0);
    private static final int GUIDE_WIDTH = 680;
    private static final int GUIDE_HEIGHT = 246;
    private static final int WIDE = 260;
    private static final int RESULT_WIDTH = 440;
    private static final int RESULTS = 5;
    private static final int RESULT_HEIGHT = 38;
    private static final int RESULT_GAP = 8;

    private LinearLayout page;
    private TextView note;
    private Runnable back = this::finish;

    static boolean due(Context context) {
        SharedPreferences saved = prefs(context);
        return !saved.getBoolean(NEVER, false) && saved.getInt(BOOT, -1) != boot(context);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    private static int boot(Context context) {
        return Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, 0);
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        boolean guide = due(this);
        prefs(this).edit().putInt(BOOT, boot(this)).apply();
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(DIM);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(page, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
        setContentView(root);
        ArrayList<String> reenabled = getIntent().getStringArrayListExtra(REENABLED);
        if (reenabled != null) showReenabled(reenabled);
        else if (!prefs(this).getBoolean(GREETED, false)) showGreeting();
        else if (guide) showGuide();
        else showPlace();
    }

    @Override public void onBackPressed() { back.run(); }

    private void showReenabled(ArrayList<String> names) {
        heading("Fire OS ha riattivato alcune parti",
            "Probabilmente con un aggiornamento. Avevi spento queste funzioni:");
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
            TextView more = label(shown == 0 ? names.size() + " parti di Fire OS" : "e altre " + (names.size() - shown) + " parti di Fire OS", 14);
            more.setGravity(Gravity.CENTER);
            more.setAlpha(0.7f);
            list.addView(more);
        }
        page.addView(list);
        TextView off = button("Spegnili", () -> warnBeforeTurningOff(names));
        page.addView(off, wide(26));
        page.addView(button("Lasciali attivi", () -> leaveOn(names)), wide(10));
        off.requestFocus();
    }

    private void leaveOn(ArrayList<String> names) {
        Reenabled.leaveOn(this, names);
        finish();
    }

    private void warnBeforeTurningOff(ArrayList<String> names) {
        heading("Prima di spegnerli",
            "La TV chiederà «Consenti debug wireless su questa rete?».\nRispondi Consenti: è una domanda di Android.");
        back = () -> showReenabled(names);
        TextView go = button("Continua", () -> turnOff(names));
        page.addView(go, wide(26));
        page.addView(button("Annulla", () -> showReenabled(names)), wide(10));
        go.requestFocus();
    }

    private void turnOff(ArrayList<String> names) {
        heading("Li sto spegnendo", "Un attimo.");
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
            heading("Non è stato possibile", "Se la TV ha chiesto il permesso, serve rispondere Consenti.");
            back = this::finish;
            TextView close = button("Chiudi", this::finish);
            page.addView(close, wide(26));
            close.requestFocus();
        });
    }

    /** Once, the very first time. */
    private void showGreeting() {
        prefs(this).edit().putBoolean(GREETED, true).apply();
        back = this::showGuide;
        page.removeAllViews();
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_salotto);
        page.addView(logo, new LinearLayout.LayoutParams(dp(GREETING_LOGO), dp(GREETING_LOGO)));
        TextView title = label("Benvenuto in Salotto", 26);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        page.addView(title);
        note = label("La tua Fire TV, più pulita e più tua.", 15);
        note.setGravity(Gravity.CENTER);
        note.setAlpha(0.8f);
        note.setPadding(0, dp(8), 0, 0);
        page.addView(note);
        TextView start = button("Inizia", this::showGuide);
        page.addView(start, wide(28));
        start.requestFocus();
    }

    private void showGuide() {
        heading("Tieni premuto un tasto", "Sono scorciatoie di Salotto: sul telecomando Fire TV non le trovi scritte.");
        back = this::afterGuide;
        LinearLayout.LayoutParams place = new LinearLayout.LayoutParams(dp(GUIDE_WIDTH), dp(GUIDE_HEIGHT));
        place.topMargin = dp(28);
        page.addView(new Guide(this), place);
        TextView close = button("Chiudi", this::afterGuide);
        page.addView(close, wide(28));
        page.addView(button("Non mostrare più", () -> {
            prefs(this).edit().putBoolean(NEVER, true).apply();
            afterGuide();
        }), wide(10));
        close.requestFocus();
    }

    private void afterGuide() {
        if (Weather.firstAsk(this)) showPlace();
        else finish();
    }

    private void showPlace() {
        heading("Il meteo della tua città",
            "Puoi usare degli sfondi adeguati al meteo della tua città.\nSe ti va di farlo, scrivila qui sotto.");
        back = this::finish;
        EditText name = new EditText(this);
        name.setSingleLine(true);
        name.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        name.setGravity(Gravity.CENTER);
        name.setTextColor(Color.WHITE);
        name.setTextSize(18);
        name.setHint("Nome della città");
        name.setHintTextColor(Color.argb(130, 255, 255, 255));
        name.setPadding(dp(12), dp(8), dp(12), dp(8));
        name.setDefaultFocusHighlightEnabled(false);
        Runnable paint = () -> {
            GradientDrawable fill = new GradientDrawable();
            fill.setCornerRadius(dp(8));
            fill.setColor(Color.argb(50, 255, 255, 255));
            fill.setStroke(dp(2), name.isFocused() ? Color.WHITE : Color.TRANSPARENT);
            name.setBackground(fill);
        };
        paint.run();
        name.setOnFocusChangeListener((changed, focused) -> paint.run());
        name.setOnEditorActionListener((view, action, event) -> {
            search(name.getText().toString().trim());
            return true;
        });
        page.addView(name, wide(32));
        page.addView(button("Cerca", () -> search(name.getText().toString().trim())), wide(24));
        page.addView(button("Non ora", this::finish), wide(10));
        name.requestFocus();
    }

    private void search(String name) {
        if (name.isEmpty()) return;
        note.setText("Cerco…");
        Background.run(() -> {
            try {
                List<Weather.Place> places = Weather.search(name);
                runOnUiThread(() -> {
                    if (places.isEmpty()) note.setText("Nessun luogo trovato. Prova a scriverla in un altro modo.");
                    else showPlaces(places);
                });
            } catch (IOException | JSONException e) {
                runOnUiThread(() -> note.setText("Connessione non riuscita. Riprova, oppure scegli Non ora."));
            }
        });
    }

    private void showPlaces(List<Weather.Place> places) {
        heading("Scegli il luogo", "Il meteo arriverà da qui.");
        back = this::showPlace;
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        View first = null;
        for (Weather.Place place : places) {
            TextView row = button(place.name + "  ·  " + place.detail, () -> {
                Weather.setPlace(this, place);
                finish();
            });
            LinearLayout.LayoutParams wide = new LinearLayout.LayoutParams(-1, dp(RESULT_HEIGHT));
            if (first != null) wide.topMargin = dp(RESULT_GAP);
            rows.addView(row, wide);
            if (first == null) first = row;
        }
        ScrollView list = new ScrollView(this);
        list.setVerticalScrollBarEnabled(false);
        list.addView(rows);
        int shown = Math.min(RESULTS, places.size());
        LinearLayout.LayoutParams place = new LinearLayout.LayoutParams(dp(RESULT_WIDTH),
            dp(shown * RESULT_HEIGHT + (shown - 1) * RESULT_GAP));
        place.topMargin = dp(28);
        page.addView(list, place);
        first.requestFocus();
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

    private TextView button(String name, Runnable run) {
        TextView view = label(name, 15);
        view.setGravity(Gravity.CENTER);
        view.setSingleLine(true);
        view.setFocusable(true);
        view.setClickable(true);
        view.setPadding(dp(12), dp(8), dp(12), dp(8));
        view.setDefaultFocusHighlightEnabled(false);
        Runnable paint = () -> {
            GradientDrawable fill = new GradientDrawable();
            fill.setCornerRadius(dp(8));
            fill.setColor(view.isFocused() ? Color.WHITE : Color.argb(50, 255, 255, 255));
            view.setBackground(fill);
            view.setTextColor(view.isFocused() ? Fire.INK : Color.WHITE);
        };
        paint.run();
        view.setOnFocusChangeListener((changed, focused) -> paint.run());
        view.setOnClickListener(changed -> run.run());
        return view;
    }

    /** The remote in outline, with a line from each of the three keys to what it does. */
    private final class Guide extends View {
        private static final float BODY_HALF = 56;
        private static final float BODY_BOTTOM = 176;
        private static final float RING_Y = 68;
        private static final float KEYS_Y = 134;
        private static final float KEYS_APART = 31;
        private static final float KEY = 13;
        private static final float REACH = 170;
        private static final float HOME_LINE_END = 198;

        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint key = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint name = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint action = new Paint(Paint.ANTI_ALIAS_FLAG);

        Guide(Context context) {
            super(context);
            for (Paint paint : new Paint[]{line, key}) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setStrokeJoin(Paint.Join.ROUND);
            }
            line.setStrokeWidth(px(1.5f));
            line.setColor(Color.WHITE);
            key.setStrokeWidth(px(1.5f));
            key.setColor(ControlPanel.LINKED);
            name.setColor(Color.WHITE);
            name.setTextSize(px(18));
            name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            action.setColor(ControlPanel.LINKED);
            action.setTextSize(px(15));
        }

        private float px(float value) { return value * getResources().getDisplayMetrics().density; }

        @Override protected void onDraw(Canvas canvas) {
            float mid = getWidth() / 2f;
            float corner = px(28);
            float half = px(BODY_HALF);
            canvas.drawRoundRect(new RectF(mid - half, px(1), mid + half, px(BODY_BOTTOM)), corner, corner, line);
            canvas.drawCircle(mid, px(17), px(4), line);
            canvas.drawCircle(mid, px(RING_Y), px(36), line);
            canvas.drawCircle(mid, px(RING_Y), px(12), line);
            float y = px(KEYS_Y);
            float left = mid - px(KEYS_APART);
            float right = mid + px(KEYS_APART);
            for (float x : new float[]{left, mid, right}) canvas.drawCircle(x, y, px(KEY), key);
            canvas.drawPath(backIcon(left, y), line);
            canvas.drawPath(homeIcon(mid, y), line);
            canvas.drawPath(menuIcon(right, y), line);

            canvas.drawLine(left - px(KEY), y, mid - px(REACH), y, key);
            canvas.drawLine(right + px(KEY), y, mid + px(REACH), y, key);
            canvas.drawLine(mid, y + px(KEY), mid, px(HOME_LINE_END), key);

            float gap = px(REACH + 12);
            write(canvas, "Indietro", "Task manager", mid - gap, y, Paint.Align.RIGHT);
            write(canvas, "Menu", "Impostazioni", mid + gap, y, Paint.Align.LEFT);
            write(canvas, "Home", "Centro di controllo", mid, px(HOME_LINE_END + 22), Paint.Align.CENTER);
        }

        private void write(Canvas canvas, String title, String does, float x, float y, Paint.Align align) {
            name.setTextAlign(align);
            action.setTextAlign(align);
            canvas.drawText(title, x, y - px(3), name);
            canvas.drawText(does, x, y + px(18), action);
        }

        private Path backIcon(float x, float y) {
            Path path = new Path();
            path.moveTo(x - px(2), y - px(6));
            path.lineTo(x - px(6), y - px(2));
            path.lineTo(x - px(2), y + px(2));
            path.moveTo(x - px(6), y - px(2));
            path.lineTo(x + px(2), y - px(2));
            path.quadTo(x + px(6), y - px(2), x + px(6), y + px(1.5f));
            path.quadTo(x + px(6), y + px(5), x + px(2), y + px(5));
            path.lineTo(x - px(2), y + px(5));
            return path;
        }

        private Path homeIcon(float x, float y) {
            Path path = new Path();
            path.moveTo(x - px(7), y);
            path.lineTo(x, y - px(6));
            path.lineTo(x + px(7), y);
            path.moveTo(x - px(4.5f), y - px(2));
            path.lineTo(x - px(4.5f), y + px(6));
            path.lineTo(x + px(4.5f), y + px(6));
            path.lineTo(x + px(4.5f), y - px(2));
            return path;
        }

        private Path menuIcon(float x, float y) {
            Path path = new Path();
            for (int row = -1; row <= 1; row++) {
                path.moveTo(x - px(6), y + row * px(4));
                path.lineTo(x + px(6), y + row * px(4));
            }
            return path;
        }
    }
}
